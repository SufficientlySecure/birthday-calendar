package org.birthdayadapter.service;

import android.Manifest;
import android.accounts.Account;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.OperationCanceledException;
import android.provider.BaseColumns;
import android.provider.CalendarContract;
import android.provider.ContactsContract;
import android.text.TextUtils;
import android.text.format.DateUtils;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.birthdayadapter.BuildConfig;
import org.birthdayadapter.R;
import org.birthdayadapter.provider.ProviderHelper;
import org.birthdayadapter.util.AccountHelper;
import org.birthdayadapter.util.CalendarHelper;
import org.birthdayadapter.util.Constants;
import org.birthdayadapter.util.Log;
import org.birthdayadapter.util.PreferencesHelper;
import org.birthdayadapter.util.SyncStatusManager;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

public class BirthdayWorker extends Worker {

    public static final String ACTION = "action";
    public static final String ACTION_CHANGE_COLOR = "CHANGE_COLOR";
    public static final String ACTION_SYNC = "SYNC";
    public static final String ACTION_REMINDERS_CHANGED = "REMINDERS_CHANGED";

    private static final Object sSyncLock = new Object();

    private HashSet<Integer> jubileeYears;

    public BirthdayWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        String action = getInputData().getString(ACTION);

        // Default to sync for backward compatibility with periodic requests
        if (action == null) {
            action = ACTION_SYNC;
        }

        // For user-initiated syncs, show the spinner
        if (ACTION_SYNC.equals(action) || ACTION_REMINDERS_CHANGED.equals(action)) {
            SyncStatusManager.getInstance().setSyncing(true);
        }

        try {
            if (!new AccountHelper(getApplicationContext()).isAccountActivated()) {
                Log.d(Constants.TAG, "Account not active, skipping work.");
                return Result.success();
            }

            switch (action) {
                case ACTION_CHANGE_COLOR:
                    updateCalendarColor(getApplicationContext());
                    break;
                case ACTION_REMINDERS_CHANGED:
                    Log.d(Constants.TAG, "Reminders changed, forcing a full resync...");
                    CalendarHelper.deleteCalendar(getApplicationContext());
                    performSync(getApplicationContext());
                    break;
                case ACTION_SYNC:
                    performSync(getApplicationContext());
                    break;
            }

            return Result.success();
        } catch (Exception e) {
            Log.e(Constants.TAG, "Worker failed", e);
            return Result.failure();
        } finally {
            // Always hide the spinner when the work is finished
            SyncStatusManager.getInstance().setSyncing(false);
        }
    }

    /**
     * Updates the color for the birthday calendar.
     */
    private void updateCalendarColor(Context context) {
        Log.d(Constants.TAG, "Updating calendar color via Worker...");

        long calendarId = CalendarHelper.getCalendar(context);
        if (calendarId == -1) {
            Log.e(Constants.TAG, "Cannot update color, calendar not found.");
            return;
        }

        int color = PreferencesHelper.getColor(context);

        ContentValues values = new ContentValues();
        values.put(CalendarContract.Calendars.CALENDAR_COLOR, color);

        Uri calendarUri = ContentUris.withAppendedId(CalendarContract.Calendars.CONTENT_URI, calendarId);

        calendarUri = calendarUri.buildUpon()
                .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
                .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, Constants.getAccountName(context))
                .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, context.getString(R.string.account_type))
                .build();

        int updatedRows = context.getContentResolver().update(calendarUri, values, null, null);
        if (updatedRows > 0) {
            Log.d(Constants.TAG, "Calendar color updated successfully.");
        } else {
            Log.w(Constants.TAG, "Could not update calendar color.");
        }
    }

    private void performSync(Context context) throws OperationCanceledException {
        // Use a static lock to prevent concurrent syncs from interfering with each other,
        // which would cause race conditions and duplicate events.
        synchronized (sSyncLock) {
            Log.d(Constants.TAG, "Starting sync inside lock...");

            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
                Log.e(Constants.TAG, "Sync failed: Missing calendar permissions.");
                return;
            }

            if (Thread.currentThread().isInterrupted()) {
                throw new OperationCanceledException();
            }

            ContentResolver contentResolver = context.getContentResolver();

            if (contentResolver == null) {
                Log.e(Constants.TAG, "Unable to get content resolver!");
                return;
            }

            long calendarId = CalendarHelper.getCalendar(context);
            if (calendarId == -1) {
                Log.e(Constants.TAG, "Unable to create or find calendar");
                return;
            }

            // Get all existing event UIDs
            ArrayList<String> existingEventUids = getExistingEventUids(context, contentResolver, calendarId);
            final int totalEventsBeforeSync = existingEventUids.size();
            int newEventsCount = 0;

            ArrayList<ContentProviderOperation> operationList = new ArrayList<>();
            Map<String, String> firstNameCache = new HashMap<>();

            try (Cursor cursor = getContactsEvents(context, contentResolver)) {
                if (cursor == null) {
                    Log.e(Constants.TAG, "Unable to get events from contacts! Cursor is null!");
                    return;
                }

                int[] reminderMinutes = PreferencesHelper.getAllReminderMinutes(context);
                Log.d(Constants.TAG, "Reminder minutes: " + Arrays.toString(reminderMinutes));
                boolean hasReminders = false;
                for (int minute : reminderMinutes) {
                    if (minute != Constants.DISABLED_REMINDER) {
                        hasReminders = true;
                        break;
                    }
                }

                int eventDateColumn = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Event.START_DATE);
                int displayNameColumn = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME);
                int eventTypeColumn = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Event.TYPE);
                int eventCustomLabelColumn = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Event.LABEL);
                int eventLookupKeyColumn = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Event.LOOKUP_KEY);

                int backRef = 0;

                while (cursor.moveToNext()) {
                    if (Thread.currentThread().isInterrupted()) {
                        throw new OperationCanceledException();
                    }

                    String eventDateString = cursor.getString(eventDateColumn);
                    String displayName = cursor.getString(displayNameColumn);
                    int eventType = cursor.getInt(eventTypeColumn);
                    String eventLookupKey = cursor.getString(eventLookupKeyColumn);
                    String eventCustomLabel = cursor.getString(eventCustomLabelColumn);

                    Date eventDate = parseEventDateString(context, eventDateString, displayName);

                    if (eventDate != null) {
                        Calendar eventCal = Calendar.getInstance();
                        eventCal.setTime(eventDate);
                        int eventYear = eventCal.get(Calendar.YEAR);

                        boolean hasYear = eventYear >= 1800;
                        int currYear = Calendar.getInstance().get(Calendar.YEAR);

                        int startYear = currYear - 3;
                        int endYear = currYear + 5;

                        for (int iteratedYear = startYear; iteratedYear <= endYear; iteratedYear++) {
                            if (Thread.currentThread().isInterrupted()) {
                                throw new OperationCanceledException();
                            }

                            if (hasYear && iteratedYear < eventYear) {
                                continue; // Don't create events for years before the birth year
                            }

                            // Create a stable, unique ID for the event instance based on raw data
                            String uidCore = eventLookupKey + ":" + eventDateString + ":" + eventType + ":" + displayName.hashCode();
                            if (eventType == ContactsContract.CommonDataKinds.Event.TYPE_CUSTOM && eventCustomLabel != null) {
                                uidCore += ":" + eventCustomLabel;
                            }
                            String eventUid = uidCore + ":" + iteratedYear;

                            // If the event already exists, remove it from the list of existing UIDs and continue
                            if (existingEventUids.remove(eventUid)) {
                                continue;
                            }

                            int age = iteratedYear - eventYear;
                            boolean includeAge = hasYear && age >= 0;

                            String title = generateTitle(context, eventType, cursor,
                                    eventCustomLabelColumn, includeAge, displayName, age, eventLookupKey, firstNameCache);

                            if (title != null && !title.trim().isEmpty()) {
                                newEventsCount++;
                                // Calculate the exact start time for this specific instance of the event
                                Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
                                cal.setTime(eventDate);
                                cal.set(Calendar.YEAR, iteratedYear);
                                cal.set(Calendar.HOUR_OF_DAY, 0);
                                cal.set(Calendar.MINUTE, 0);
                                cal.set(Calendar.SECOND, 0);
                                cal.set(Calendar.MILLISECOND, 0);
                                long dtstart = cal.getTimeInMillis();

                                Log.v(Constants.TAG, "Adding event: " + title);
                                operationList.add(insertEvent(context, calendarId, dtstart, title, eventLookupKey, eventUid, hasReminders));

                                if (hasReminders) {
                                    int noOfReminderOperations = 0;
                                    for (int minute : reminderMinutes) {
                                        if (minute != Constants.DISABLED_REMINDER) {
                                            ContentProviderOperation.Builder builder = ContentProviderOperation
                                                    .newInsert(CalendarHelper.getBirthdayAdapterUri(context, CalendarContract.Reminders.CONTENT_URI));

                                            builder.withValueBackReference(CalendarContract.Reminders.EVENT_ID, backRef);
                                            builder.withValue(CalendarContract.Reminders.MINUTES, minute);
                                            builder.withValue(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT);
                                            operationList.add(builder.build());

                                            noOfReminderOperations += 1;
                                        }
                                    }
                                    backRef += 1 + noOfReminderOperations;
                                } else {
                                    backRef += 1;
                                }
                            }

                            if (operationList.size() > 200) {
                                try {
                                    contentResolver.applyBatch(CalendarContract.AUTHORITY,
                                            operationList);
                                    backRef = 0;
                                    operationList.clear();
                                } catch (Exception e) {
                                    Log.e(Constants.TAG, "Applying batch error!", e);
                                }
                            }
                        }
                    }
                }
            }

            if (!operationList.isEmpty()) {
                try {
                    contentResolver.applyBatch(CalendarContract.AUTHORITY, operationList);
                } catch (Exception e) {
                    Log.e(Constants.TAG, "Applying batch error!", e);
                }
            }

            // Delete old events
            int deletedEventsCount = 0;
            if (!existingEventUids.isEmpty()) {
                deletedEventsCount = existingEventUids.size();
                Log.d(Constants.TAG, "Deleting " + deletedEventsCount + " old events.");
                ArrayList<ContentProviderOperation> deleteOperationList = new ArrayList<>();
                for (String uid : existingEventUids) {
                    deleteOperationList.add(ContentProviderOperation.newDelete(CalendarHelper.getBirthdayAdapterUri(context, CalendarContract.Events.CONTENT_URI))
                            .withSelection(CalendarContract.Events.UID_2445 + " = ?", new String[]{uid})
                            .build());
                }
                try {
                    contentResolver.applyBatch(CalendarContract.AUTHORITY, deleteOperationList);
                } catch (Exception e) {
                    Log.e(Constants.TAG, "Error deleting old events", e);
                }
            }

            int checkedEventsCount = totalEventsBeforeSync - deletedEventsCount;
            Log.i(Constants.TAG, "Sync summary: " + checkedEventsCount + " events checked, "
                    + newEventsCount + " new events added, " + deletedEventsCount + " old events removed.");


            // Store the last sync timestamp in a separate file to avoid triggering listeners
            SharedPreferences syncPrefs = context.getSharedPreferences("sync_status_prefs", Context.MODE_PRIVATE);
            syncPrefs.edit().putLong("last_sync_timestamp", System.currentTimeMillis()).apply();
        }
    }

    private ArrayList<String> getExistingEventUids(Context context, ContentResolver contentResolver, long calendarId) {
        ArrayList<String> existingUids = new ArrayList<>();
        Uri uri = CalendarHelper.getBirthdayAdapterUri(context, CalendarContract.Events.CONTENT_URI);

        try (Cursor cursor = contentResolver.query(uri,
                new String[]{CalendarContract.Events.UID_2445},
                CalendarContract.Events.CALENDAR_ID + " = ?",
                new String[]{String.valueOf(calendarId)},
                null)) {

            if (cursor == null) {
                Log.e(Constants.TAG, "Unable to get existing events! Cursor is null!");
                return existingUids;
            }

            int uidColumn = cursor.getColumnIndex(CalendarContract.Events.UID_2445);
            while (cursor.moveToNext()) {
                existingUids.add(cursor.getString(uidColumn));
            }
        }
        return existingUids;
    }

    private Map<String, List<String>> getRawContactGroupTitles(Context context, ContentResolver contentResolver) {
        Map<String, String> groupIdToTitleMap = new HashMap<>();
        final String[] groupProjection = {ContactsContract.Groups._ID, ContactsContract.Groups.TITLE};
        final String groupSelection = ContactsContract.Groups.DELETED + " = 0";

        // Query all groups to create a mapping from group ID to group title
        try (Cursor groupCursor = contentResolver.query(ContactsContract.Groups.CONTENT_URI,
                groupProjection, groupSelection, null, null)) {
            if (groupCursor != null) {
                int idColumn = groupCursor.getColumnIndex(ContactsContract.Groups._ID);
                int titleColumn = groupCursor.getColumnIndex(ContactsContract.Groups.TITLE);
                while (groupCursor.moveToNext()) {
                    String id = groupCursor.getString(idColumn);
                    String title = groupCursor.getString(titleColumn);
                    // We are not interested in system groups
                    if (!TextUtils.isEmpty(title) && !title.startsWith("System Group:")) {
                        groupIdToTitleMap.put(id, title);
                    }
                }
            }
        }

        Map<String, List<String>> rawContactToGroupTitlesMap = new HashMap<>();
        final String[] membershipProjection = {
                ContactsContract.Data.RAW_CONTACT_ID,
                ContactsContract.CommonDataKinds.GroupMembership.GROUP_ROW_ID
        };
        final String membershipSelection = ContactsContract.Data.MIMETYPE + " = ?";
        final String[] membershipSelectionArgs = {ContactsContract.CommonDataKinds.GroupMembership.CONTENT_ITEM_TYPE};

        // Query all group memberships to link contacts to groups
        try (Cursor membershipCursor = contentResolver.query(ContactsContract.Data.CONTENT_URI,
                membershipProjection, membershipSelection, membershipSelectionArgs, null)) {
            if (membershipCursor != null) {
                int rawContactIdColumn = membershipCursor.getColumnIndex(ContactsContract.Data.RAW_CONTACT_ID);
                int groupIdColumn = membershipCursor.getColumnIndex(ContactsContract.CommonDataKinds.GroupMembership.GROUP_ROW_ID);
                while (membershipCursor.moveToNext()) {
                    String rawContactId = membershipCursor.getString(rawContactIdColumn);
                    String groupId = membershipCursor.getString(groupIdColumn);
                    String groupTitle = groupIdToTitleMap.get(groupId);
                    if (groupTitle != null) {
                        rawContactToGroupTitlesMap
                                .computeIfAbsent(rawContactId, k -> new ArrayList<>())
                                .add(groupTitle);
                    }
                }
            }
        }
        return rawContactToGroupTitlesMap;
    }

    private Cursor getContactsEvents(Context context, ContentResolver contentResolver) throws OperationCanceledException {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            Log.e(Constants.TAG, "Missing READ_CONTACTS permission!");
            return null;
        }

        if (Thread.currentThread().isInterrupted()) {
            throw new OperationCanceledException();
        }

        Map<String, List<String>> contactGroupMembership = getRawContactGroupTitles(context, contentResolver);
        HashMap<Account, HashSet<String>> blacklist = ProviderHelper.getAccountBlacklist(context);
        HashSet<String> addedEventsIdentifiers = new HashSet<>();

        // Define the columns we want to fetch in a single query
        String[] projection = new String[]{
                BaseColumns._ID,
                ContactsContract.Data.DISPLAY_NAME,
                ContactsContract.Data.LOOKUP_KEY,
                ContactsContract.CommonDataKinds.Event.START_DATE,
                ContactsContract.CommonDataKinds.Event.TYPE,
                ContactsContract.CommonDataKinds.Event.LABEL,
                ContactsContract.RawContacts.ACCOUNT_TYPE,
                ContactsContract.RawContacts.ACCOUNT_NAME,
                ContactsContract.Data.RAW_CONTACT_ID
        };

        // The query is performed on the Data table, filtering for the Event mimetype
        String selection = ContactsContract.Data.MIMETYPE + " = ?";
        String[] selectionArgs = new String[]{ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE};

        // The resulting cursor to be returned
        MatrixCursor resultCursor = new MatrixCursor(new String[]{
                BaseColumns._ID,
                ContactsContract.Data.DISPLAY_NAME,
                ContactsContract.Data.LOOKUP_KEY,
                ContactsContract.CommonDataKinds.Event.START_DATE,
                ContactsContract.CommonDataKinds.Event.TYPE,
                ContactsContract.CommonDataKinds.Event.LABEL
        });

        try (Cursor dataCursor = contentResolver.query(ContactsContract.Data.CONTENT_URI, projection, selection, selectionArgs, null)) {
            if (dataCursor == null) {
                Log.e(Constants.TAG, "Failed to query contacts data.");
                return resultCursor; // Return an empty cursor
            }

            int accTypeColumn = dataCursor.getColumnIndex(ContactsContract.RawContacts.ACCOUNT_TYPE);
            int accNameColumn = dataCursor.getColumnIndex(ContactsContract.RawContacts.ACCOUNT_NAME);
            int rawContactIdColumn = dataCursor.getColumnIndex(ContactsContract.Data.RAW_CONTACT_ID);
            int lookupKeyColumn = dataCursor.getColumnIndex(ContactsContract.Data.LOOKUP_KEY);
            int typeColumn = dataCursor.getColumnIndex(ContactsContract.CommonDataKinds.Event.TYPE);
            int labelColumn = dataCursor.getColumnIndex(ContactsContract.CommonDataKinds.Event.LABEL);
            int startDateColumn = dataCursor.getColumnIndex(ContactsContract.CommonDataKinds.Event.START_DATE);
            int displayNameColumn = dataCursor.getColumnIndex(ContactsContract.Data.DISPLAY_NAME);

            int idCounter = 0;
            while (dataCursor.moveToNext()) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new OperationCanceledException();
                }

                // Check if the contact's account is in the blacklist
                String accType = dataCursor.getString(accTypeColumn);
                String accName = dataCursor.getString(accNameColumn);

                boolean isBlacklisted = false;
                if (!TextUtils.isEmpty(accType) && !TextUtils.isEmpty(accName)) {
                    Account account = new Account(accName, accType);
                    HashSet<String> blacklistedGroups = blacklist.get(account);

                    if (blacklistedGroups != null) {
                        if (blacklistedGroups.contains(null)) {
                            // Account is fully blacklisted
                            isBlacklisted = true;
                        } else {
                            // Account is partially blacklisted, check groups
                            String rawContactId = dataCursor.getString(rawContactIdColumn);
                            List<String> contactGroups = contactGroupMembership.get(rawContactId);

                            if (contactGroups != null) {
                                for (String groupTitle : contactGroups) {
                                    if (blacklistedGroups.contains(groupTitle)) {
                                        isBlacklisted = true;
                                        break;
                                    }
                                }
                            }
                        }
                    }
                }

                if (!isBlacklisted) {
                    String lookupKey = dataCursor.getString(lookupKeyColumn);
                    int type = dataCursor.getInt(typeColumn);
                    String label = dataCursor.getString(labelColumn);
                    String startDate = dataCursor.getString(startDateColumn);

                    // Prevent adding the same event (birthday, anniversary) for the same contact twice
                    String eventIdentifier = lookupKey + type + label + startDate;
                    if (addedEventsIdentifiers.add(eventIdentifier)) {
                        resultCursor.newRow()
                                .add(idCounter++)
                                .add(dataCursor.getString(displayNameColumn))
                                .add(lookupKey)
                                .add(startDate)
                                .add(type)
                                .add(label);
                    }
                }
            }
        }

        return resultCursor;
    }

    private String generateTitle(Context context, int eventType, Cursor cursor,
                                 int eventCustomLabelColumn, boolean includeAge, String displayName, int age, String lookupKey, Map<String, String> firstNameCache) {
        if (displayName == null) {
            return null;
        }

        int effectiveEventType = eventType;
        String eventCustomLabel = null;
        if (eventType == ContactsContract.CommonDataKinds.Event.TYPE_CUSTOM) {
            eventCustomLabel = cursor.getString(eventCustomLabelColumn);
            if (eventCustomLabel == null) {
                // Fallback to OTHER if custom label is missing
                effectiveEventType = ContactsContract.CommonDataKinds.Event.TYPE_OTHER;
            }
        }

        String title = PreferencesHelper.getLabel(context, effectiveEventType, includeAge);

        // add jubilee icon
        if ((BuildConfig.FULL_VERSION) && (includeAge)) {
            title = addJubileeIcon(context, title, age);
        }

        // Replace placeholders
        if (title.contains("{FIRSTNAME}")) {
            String firstName = getFirstName(context, lookupKey, displayName, firstNameCache);
            title = title.replace("{FIRSTNAME}", firstName);
        }
        title = title.replace("{NAME}", displayName);
        if (includeAge) {
            title = title.replace("{AGE}", String.valueOf(age));
        }
        if (eventCustomLabel != null) {
            title = title.replace("{LABEL}", eventCustomLabel);
        }

        return title;
    }

    private String getFirstName(Context context, String lookupKey, String displayName, Map<String, String> firstNameCache) {
        String firstName = firstNameCache.get(lookupKey);
        if (firstName == null && lookupKey != null) {
            firstName = getFirstNameFromLookupKey(context, lookupKey);
            // Fallback to splitting the display name if structured name is not available
            if (TextUtils.isEmpty(firstName)) {
                firstName = displayName.split("\\s+")[0];
            }
            firstNameCache.put(lookupKey, firstName);
        } else if (firstName == null) {
            // Fallback for when lookupKey is null for some reason
            firstName = displayName.split("\\s+")[0];
        }

        // Final fallback to ensure firstname is not empty if display name is not
        if (TextUtils.isEmpty(firstName) && !TextUtils.isEmpty(displayName)) {
            firstName = displayName;
        }
        return firstName;
    }

    private String getFirstNameFromLookupKey(Context context, String lookupKey) {
        if (lookupKey == null) {
            return null;
        }
        Uri lookupUri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_LOOKUP_URI, lookupKey);
        Uri dataUri = Uri.withAppendedPath(lookupUri, ContactsContract.Contacts.Data.CONTENT_DIRECTORY);

        String[] projection = {ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME};
        String selection = ContactsContract.Data.MIMETYPE + " = ?";
        String[] selectionArgs = {ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE};

        try (Cursor cursor = context.getContentResolver().query(dataUri, projection, selection, selectionArgs, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int givenNameColumnIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME);
                if (givenNameColumnIndex != -1) {
                    String givenName = cursor.getString(givenNameColumnIndex);
                    if (!TextUtils.isEmpty(givenName)) {
                        return givenName;
                    }
                }
            }
        } catch (Exception e) {
            Log.e(Constants.TAG, "Error querying for given name using lookup key: " + lookupKey, e);
        }
        return null;
    }

    private String addJubileeIcon(Context context, String title, int age) {
        if (jubileeYears == null) {
            // Always initialize the set to prevent NullPointerException
            jubileeYears = new HashSet<>();
            String jubileeYearsStr = PreferencesHelper.getJubileeYears(context);
            if (!TextUtils.isEmpty(jubileeYearsStr)) {
                try {
                    Arrays.stream(jubileeYearsStr.split(",")).map(String::trim).map(Integer::parseInt).forEach(jubileeYears::add);
                } catch (NumberFormatException e) {
                    Log.e(Constants.TAG, "Invalid jubilee years format in preferences. No jubilee icons will be shown.", e);
                    // In case of error, clear the set to be safe
                    jubileeYears.clear();
                }
            }
        }

        if (jubileeYears.contains(age)) {
            return "\uD83C\uDF89 " + title;
        }
        return title;
    }

    private ContentProviderOperation insertEvent(Context context, long calendarId,
                                                 long dtstart, String title, String lookupKey, String eventUid, boolean hasReminders)
            throws OperationCanceledException {
        if (Thread.currentThread().isInterrupted()) {
            throw new OperationCanceledException();
        }

        ContentProviderOperation.Builder builder =
                ContentProviderOperation.newInsert(CalendarHelper.getBirthdayAdapterUri(context, CalendarContract.Events.CONTENT_URI));

        long dtend = dtstart + DateUtils.DAY_IN_MILLIS;

        builder.withValue(CalendarContract.Events.CALENDAR_ID, calendarId);
        builder.withValue(CalendarContract.Events.DTSTART, dtstart);
        builder.withValue(CalendarContract.Events.DTEND, dtend);
        builder.withValue(CalendarContract.Events.EVENT_TIMEZONE, "UTC");

        builder.withValue(CalendarContract.Events.ALL_DAY, 1);
        builder.withValue(CalendarContract.Events.TITLE, title);
        builder.withValue(CalendarContract.Events.STATUS, CalendarContract.Events.STATUS_CONFIRMED);
        builder.withValue(CalendarContract.Events.UID_2445, eventUid);

        builder.withValue(CalendarContract.Events.HAS_ALARM, hasReminders ? 1 : 0);

        builder.withValue(CalendarContract.Events.AVAILABILITY, CalendarContract.Events.AVAILABILITY_FREE);

        if (lookupKey != null) {
            builder.withValue(CalendarContract.Events.CUSTOM_APP_PACKAGE, context.getPackageName());
            Uri contactLookupUri = Uri.withAppendedPath(
                    ContactsContract.Contacts.CONTENT_LOOKUP_URI, lookupKey);
            builder.withValue(CalendarContract.Events.CUSTOM_APP_URI, contactLookupUri.toString());
        }

        return builder.build();
    }


    private Date parseEventDateString(Context context, String eventDateString, String displayName)
            throws OperationCanceledException {
        if (Thread.currentThread().isInterrupted()) {
            throw new OperationCanceledException();
        }
        if (TextUtils.isEmpty(eventDateString)) {
            return null;
        }

        String[] formatsToTry;
        if (PreferencesHelper.getPreferddSlashMM(context)) {
            formatsToTry = new String[]{"yyyy-MM-dd", "--MM-dd", "yyyyMMdd", "dd.MM.yyyy", "yyyy.MM.dd", "dd/MM/yyyy", "dd/MM"};
        } else {
            formatsToTry = new String[]{"yyyy-MM-dd", "--MM-dd", "yyyyMMdd", "MM/dd/yyyy", "MM/dd"};
        }

        for (String format : formatsToTry) {
            boolean setYear1700 = format.equals("--MM-dd") || format.equals("dd/MM") || format.equals("MM/dd");
            Date parsedDate = parseStringWithSimpleDateFormat(eventDateString, format);
            if (parsedDate != null) {
                if (setYear1700) {
                    Calendar cal = Calendar.getInstance();
                    cal.setTime(parsedDate);
                    cal.set(Calendar.YEAR, 1700);
                    parsedDate = cal.getTime();
                }
                return parsedDate;
            }
        }

        // If all other formats fail, try to parse as a raw timestamp
        try {
            return new Date(Long.parseLong(eventDateString));
        } catch (NumberFormatException e) {
            Log.e(Constants.TAG, "Could not parse date string: '" + eventDateString + "' for contact: '" + displayName + "'");
            return null;
        }
    }

    private Date parseStringWithSimpleDateFormat(String input, String format) {
        SimpleDateFormat dateFormat = new SimpleDateFormat(format, Locale.US);
        // All-day events should be parsed in UTC to avoid timezone-related shifts.
        dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        try {
            return dateFormat.parse(input);
        } catch (ParseException e) {
            return null;
        }
    }
}
