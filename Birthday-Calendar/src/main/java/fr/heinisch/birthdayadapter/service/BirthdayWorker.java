/*
 * Copyright (C) 2025-2026 Matthias Heinisch <birthdayadapter@heinisch.fr>
 *
 * This file is part of Birthday Adapter.
 *
 * Birthday Adapter is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Birthday Adapter is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Birthday Adapter.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package fr.heinisch.birthdayadapter.service;

import static fr.heinisch.birthdayadapter.util.CalendarHelper.getAppPackageName;
import static fr.heinisch.birthdayadapter.util.VersionHelper.isFullVersionUnlocked;

import android.Manifest;
import android.accounts.Account;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Build;
import android.os.OperationCanceledException;
import android.provider.BaseColumns;
import android.provider.CalendarContract;
import android.provider.ContactsContract;
import android.text.TextUtils;
import android.text.format.DateUtils;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;
import androidx.work.ForegroundInfo;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
import java.util.Objects;
import java.util.Set;
import java.util.TimeZone;

import fr.heinisch.birthdayadapter.R;
import fr.heinisch.birthdayadapter.provider.ProviderHelper;
import fr.heinisch.birthdayadapter.util.AccountHelper;
import fr.heinisch.birthdayadapter.util.CalendarHelper;
import fr.heinisch.birthdayadapter.util.Constants;
import fr.heinisch.birthdayadapter.util.Installation;
import fr.heinisch.birthdayadapter.util.Log;
import fr.heinisch.birthdayadapter.util.PreferencesHelper;
import fr.heinisch.birthdayadapter.util.SyncStatusManager;

public class BirthdayWorker extends Worker {

    public static final String ACTION = "action";
    public static final String EXTRA_OLD_CALENDAR_ID = "old_calendar_id";
    public static final String ACTION_CHANGE_COLOR = "CHANGE_COLOR";
    public static final String ACTION_SYNC = "SYNC";
    public static final String ACTION_FORCE_RESYNC = "FORCE_RESYNC";

    private static final Object sSyncLock = new Object();

    private static final int NOTIFICATION_ID = 3105;
    private static final String NOTIFICATION_CHANNEL_ID = "birthday_sync_channel";

    private HashSet<Integer> jubileeYears;

    public BirthdayWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        String action = getInputData().getString(ACTION);
        if (action == null) {
            action = ACTION_SYNC; // Default for backward compatibility
        }

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
            setForegroundAsync(getForegroundInfo());
        }

        final boolean shouldShowSpinner = ACTION_SYNC.equals(action) || ACTION_FORCE_RESYNC.equals(action) || ACTION_CHANGE_COLOR.equals(action);

        if (shouldShowSpinner) {
            SyncStatusManager.getInstance().setSyncing(true);
        }

        try {
            Context context = getApplicationContext();

            // On a calendar change, we must always clean the old calendar, even if the adapter is disabled.
            if (ACTION_FORCE_RESYNC.equals(action)) {
                long oldCalendarId = getInputData().getLong(EXTRA_OLD_CALENDAR_ID, -1);
                if (oldCalendarId != -1) {
                    String calendarName = CalendarHelper.getCalendarName(context, oldCalendarId);
                    Log.i(Constants.TAG, "Cleaning up old calendar: " + calendarName);
                    CalendarHelper.clearBirthdayAdapterEvents(context, oldCalendarId);
                }
            }

            AccountHelper accountHelper = new AccountHelper(context);
            if (!accountHelper.isAccountActivated()) {
                Log.d(Constants.TAG, "Account not active, skipping work.");
                // We already cleaned the old calendar, so we can just stop here.
                return Result.success();
            }

            switch (action) {
                case ACTION_CHANGE_COLOR:
                    updateCalendarColor(context);
                    break;
                case ACTION_FORCE_RESYNC:
                    Log.d(Constants.TAG, "Forcing a full resync...");
                    // The old calendar is already cleaned. Now, we just need to clean the new one and sync.
                    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
                    String targetCalendarIdStr = prefs.getString(context.getString(R.string.pref_target_calendar_key), null);
                    if (targetCalendarIdStr != null) {
                        try {
                            long calendarId = Long.parseLong(targetCalendarIdStr);
                            CalendarHelper.clearBirthdayAdapterEvents(context, calendarId);
                        } catch (NumberFormatException e) {
                            Log.e(Constants.TAG, "Invalid target calendar ID during resync, falling back to deleting default calendar.");
                            CalendarHelper.deleteCalendar(context);
                        }
                    } else {
                        CalendarHelper.deleteCalendar(context);
                    }
                    performSync(context);
                    break;
                case ACTION_SYNC:
                    performSync(context);
                    break;
            }

            return Result.success();
        } catch (Exception e) {
            Log.e(Constants.TAG, "Worker failed", e);
            return Result.failure();
        } finally {
            if (shouldShowSpinner) {
                SyncStatusManager.getInstance().setSyncing(false);
            }
        }
    }

    @NonNull
    @Override
    public ForegroundInfo getForegroundInfo() {
        Context context = getApplicationContext();
        String notificationTitle = context.getString(R.string.notification_title);

        createNotificationChannel(context);

        Notification notification = new NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
                .setContentTitle(notificationTitle)
                .setTicker(notificationTitle)
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setOngoing(true)
                .build();

        return new ForegroundInfo(NOTIFICATION_ID, notification);
    }

    private void createNotificationChannel(Context context) {
        NotificationChannel channel = new NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                context.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription(context.getString(R.string.notification_channel_description));
        NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
        if (notificationManager != null) {
            notificationManager.createNotificationChannel(channel);
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

        Account account = CalendarHelper.getAccountForCalendar(context, calendarId);
        calendarUri = CalendarHelper.getBirthdayAdapterUri(calendarUri, account);

        int updatedRows = context.getContentResolver().update(calendarUri, values, null, null);
        String calendarName = CalendarHelper.getCalendarName(context, calendarId);
        if (updatedRows > 0) {
            Log.d(Constants.TAG, "Calendar color updated successfully for calendar: " + calendarName);
        } else {
            Log.w(Constants.TAG, "Could not update calendar color for calendar: " + calendarName);
        }
    }

    private void performSync(Context context) throws OperationCanceledException {
        // Use a static lock to prevent concurrent syncs from interfering with each other,
        // which would cause race conditions and duplicate events.
        synchronized (sSyncLock) {

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

            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            String prefKey = context.getString(R.string.pref_target_calendar_key);
            String targetCalendarIdStr = prefs.getString(prefKey, null);

            long calendarId = -1;
            if (targetCalendarIdStr != null) {
                try {
                    calendarId = Long.parseLong(targetCalendarIdStr);
                } catch (NumberFormatException e) {
                    Log.w(Constants.TAG, "Invalid target calendar ID format in preferences: " + targetCalendarIdStr);
                    // ID is -1, will fall back to default below
                }
            }

            Account account = null;
            if (calendarId != -1) {
                account = CalendarHelper.getAccountForCalendar(context, calendarId);
            }

            // Fallback logic if no valid calendar is found
            if (account == null) {
                if (calendarId != -1) {
                    // This means the previously selected calendar is no longer valid.
                    Log.w(Constants.TAG, "Previously selected calendar (ID: " + calendarId + ") is no longer valid or has been deleted. Falling back to the default app calendar.");
                } else if (targetCalendarIdStr != null) {
                    // ID was invalid format
                    Log.w(Constants.TAG, "Invalid calendar ID in preferences. Falling back to default app calendar.");
                } else {
                    // No calendar was selected yet
                    Log.i(Constants.TAG, "No calendar selected. Using default app calendar.");
                }


                // Get the default calendar, which will create it if necessary.
                calendarId = CalendarHelper.getCalendar(context);

                if (calendarId == -1) {
                    Log.e(Constants.TAG, "Unable to create or find a fallback calendar. Aborting sync.");
                    return;
                }

                // Re-check account for the new/default calendar
                account = CalendarHelper.getAccountForCalendar(context, calendarId);

                if (account == null) {
                    String fallbackCalendarName = CalendarHelper.getCalendarName(context, calendarId);
                    Log.e(Constants.TAG, "Fatal: Failed to get a syncable account even for the default calendar '" + fallbackCalendarName + "'. Aborting sync.");
                    return;
                }

                // Save the valid calendar ID for future syncs.
                prefs.edit().putString(prefKey, String.valueOf(calendarId)).apply();
                Log.i(Constants.TAG, "Switched to default calendar (ID: " + calendarId + ") and saved it to preferences.");
            }

            // At this point, calendarId and account are guaranteed to be valid.
            String calendarName = CalendarHelper.getCalendarName(context, calendarId);
            Log.d(Constants.TAG, "Starting sync for calendar: " + calendarName);

            // Get all existing events
            ExistingEvents existingEvents = getExistingEvents(context, contentResolver, calendarId, account);
            final int totalEventsBeforeSync = existingEvents.uids.size();
            int newEventsCount = 0;

            ArrayList<ContentProviderOperation> operationList = new ArrayList<>();
            Map<String, String> firstNameCache = new HashMap<>();
            Map<String, String> lastNameCache = new HashMap<>();

            try (Cursor cursor = getContactsEvents(context, contentResolver)) {
                if (cursor == null) {
                    Log.e(Constants.TAG, "Unable to get events from contacts! Cursor is null!");
                    return;
                }

                int[] reminderMinutes = PreferencesHelper.getAllReminderMinutes(context);
                Set<String> reminderEventTypes = PreferencesHelper.getReminderEventTypes(context);
                Log.d(Constants.TAG, "Reminder minutes: " + Arrays.toString(reminderMinutes));
                boolean hasReminders = reminderMinutes.length > 0;

                int eventDateColumn = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Event.START_DATE);
                int displayNameColumn = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME);
                int eventTypeColumn = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Event.TYPE);
                int eventCustomLabelColumn = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Event.LABEL);
                int eventLookupKeyColumn = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Event.LOOKUP_KEY);

                int backRef = 0;

                boolean useLastNameFirst = PreferencesHelper.getUseLastNameFirst(context);

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
                        Calendar eventCal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
                        eventCal.setTime(eventDate);
                        int eventYear = eventCal.get(Calendar.YEAR);

                        boolean hasYear = eventYear >= 1800;
                        int currYear = Calendar.getInstance(TimeZone.getTimeZone("UTC")).get(Calendar.YEAR);

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
                            String uidCore = Installation.id(context) + ":" + calendarId + ":" + eventLookupKey + ":" + eventDateString + ":" + eventType + ":" + displayName;
                            if (eventType == ContactsContract.CommonDataKinds.Event.TYPE_CUSTOM && eventCustomLabel != null) {
                                uidCore += ":" + eventCustomLabel;
                            }
                            String eventUid = generateHash(uidCore) + ":" + iteratedYear;

                            // If the event already exists, remove it from the list of existing UIDs and continue
                            if (existingEvents.uids.remove(eventUid)) {
                                continue;
                            }

                            int age = iteratedYear - eventYear;
                            boolean includeAge = hasYear && age >= 0;

                            String title = generateTitle(context, eventType, cursor,
                                    eventCustomLabelColumn, includeAge, displayName, age, eventLookupKey, firstNameCache, lastNameCache, useLastNameFirst);

                            if (title != null && !title.trim().isEmpty()) {

                                // Calculate the exact start time for this specific instance of the event
                                Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
                                cal.setTime(eventDate);
                                cal.set(Calendar.YEAR, iteratedYear);
                                cal.set(Calendar.HOUR_OF_DAY, 0);
                                cal.set(Calendar.MINUTE, 0);
                                cal.set(Calendar.SECOND, 0);
                                cal.set(Calendar.MILLISECOND, 0);
                                long dtstart = cal.getTimeInMillis();

                                // Check for duplicates from other installations
                                if (existingEvents.eventExists(title, dtstart)) {
                                    continue;
                                }

                                newEventsCount++;

                                boolean shouldAddReminder = hasReminders && reminderEventTypes.contains(String.valueOf(eventType));

                                Log.v(Constants.TAG, "Adding event: " + title);
                                operationList.add(insertEvent(context, calendarId, dtstart, title, eventLookupKey, eventUid, shouldAddReminder, account));

                                if (shouldAddReminder) {
                                    for (int minute : reminderMinutes) {
                                        ContentProviderOperation.Builder builder = ContentProviderOperation
                                                .newInsert(CalendarHelper.getBirthdayAdapterUri(CalendarContract.Reminders.CONTENT_URI, account));

                                        builder.withValueBackReference(CalendarContract.Reminders.EVENT_ID, backRef);
                                        builder.withValue(CalendarContract.Reminders.MINUTES, minute);
                                        builder.withValue(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT);
                                        operationList.add(builder.build());
                                    }
                                    backRef += 1 + reminderMinutes.length;
                                } else {
                                    backRef += 1;
                                }
                            }

                            if (operationList.size() > 200) {
                                applyBatchOperations(contentResolver, operationList);
                                backRef = 0;
                                operationList.clear();
                            }
                        }
                    }
                }
            }

            if (!operationList.isEmpty()) {
                applyBatchOperations(contentResolver, operationList);
            }

            // Delete old events from this installation
            int deletedEventsCount = 0;
            if (!existingEvents.uids.isEmpty()) {
                deletedEventsCount = existingEvents.uids.size();
                Log.d(Constants.TAG, "Deleting " + deletedEventsCount + " old events from calendar: " + calendarName);
                ArrayList<ContentProviderOperation> deleteOperationList = new ArrayList<>();
                for (String uid : existingEvents.uids) {
                    deleteOperationList.add(ContentProviderOperation.newDelete(CalendarHelper.getBirthdayAdapterUri(CalendarContract.Events.CONTENT_URI, account))
                            .withSelection(CalendarContract.Events.UID_2445 + " = ?", new String[]{uid})
                            .build());
                }
                applyBatchOperations(contentResolver, deleteOperationList);
            }

            int checkedEventsCount = totalEventsBeforeSync - deletedEventsCount;
            Log.i(Constants.TAG, "Sync summary for calendar \"" + calendarName + "\": " + checkedEventsCount + " events confirmed, "
                    + newEventsCount + " new events added, " + deletedEventsCount + " old events removed.");


            // Store the last sync timestamp in a separate file to avoid triggering listeners
            SharedPreferences syncPrefs = context.getSharedPreferences("sync_status_prefs", Context.MODE_PRIVATE);
            syncPrefs.edit().putLong("last_sync_timestamp", System.currentTimeMillis()).apply();
        }
    }

    private void applyBatchOperations(ContentResolver contentResolver, ArrayList<ContentProviderOperation> operationList) {
        try {
            ContentProviderResult[] results = contentResolver.applyBatch(CalendarContract.AUTHORITY, operationList);
            if (results.length == 0) {
                Log.w(Constants.TAG, "Batch operation returned no results.");
            }
        } catch (Exception e) {
            Log.e(Constants.TAG, "Applying batch error!", e);
        }
    }

    private static class ExistingEventInfo {
        final String title;
        final long dtstart;

        ExistingEventInfo(String title, long dtstart) {
            this.title = title;
            this.dtstart = dtstart;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ExistingEventInfo that = (ExistingEventInfo) o;
            return dtstart == that.dtstart && Objects.equals(title, that.title);
        }

        @Override
        public int hashCode() {
            return Objects.hash(title, dtstart);
        }
    }

    private static class ExistingEvents {
        final ArrayList<String> uids = new ArrayList<>();
        private final HashSet<ExistingEventInfo> infos = new HashSet<>();

        void add(String uid, String title, long dtstart) {
            if (uid != null) {
                uids.add(uid);
            }
            if (title != null) {
                infos.add(new ExistingEventInfo(title, dtstart));
            }
        }

        boolean eventExists(String title, long dtstart) {
            return infos.contains(new ExistingEventInfo(title, dtstart));
        }
    }


    private ExistingEvents getExistingEvents(Context context, ContentResolver contentResolver, long calendarId, Account account) {
        ExistingEvents existingEvents = new ExistingEvents();
        String calendarName = CalendarHelper.getCalendarName(context, calendarId);
        Uri uri = CalendarHelper.getBirthdayAdapterUri(CalendarContract.Events.CONTENT_URI, account);

        // Fetch all events from the target calendar to check for duplicates.
        // We check for events created by this app instance (via SYNC_DATA1) and remove them if they are outdated.
        // We also check for events created by other instances (or manually) by comparing title and start date.
        String selection = CalendarContract.Events.CALENDAR_ID + " = ?";
        String[] selectionArgs = new String[]{String.valueOf(calendarId)};

        try (Cursor cursor = contentResolver.query(uri,
                new String[]{CalendarContract.Events.UID_2445, CalendarContract.Events.TITLE, CalendarContract.Events.DTSTART, CalendarContract.Events.SYNC_DATA1},
                selection,
                selectionArgs,
                null)) {

            if (cursor == null) {
                Log.e(Constants.TAG, "Unable to get existing events for calendar " + calendarName + "! Cursor is null!");
                return existingEvents;
            }

            int uidColumn = cursor.getColumnIndex(CalendarContract.Events.UID_2445);
            int titleColumn = cursor.getColumnIndex(CalendarContract.Events.TITLE);
            int dtstartColumn = cursor.getColumnIndex(CalendarContract.Events.DTSTART);
            int syncData1Column = cursor.getColumnIndex(CalendarContract.Events.SYNC_DATA1);
            String installationId = Installation.id(context);

            while (cursor.moveToNext()) {
                String uid = cursor.getString(uidColumn);
                String title = cursor.getString(titleColumn);
                long dtstart = cursor.getLong(dtstartColumn);
                String syncData1 = cursor.getString(syncData1Column);

                // Only UIDs from the current installation are added to the list for potential deletion.
                if (installationId.equals(syncData1)) {
                    existingEvents.add(uid, title, dtstart);
                } else {
                    existingEvents.add(null, title, dtstart);
                }
            }
        }
        return existingEvents;
    }

    private Map<String, List<String>> getRawContactGroupTitles(ContentResolver contentResolver) {
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

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        boolean groupFilteringEnabled = sharedPreferences.getBoolean(context.getString(R.string.pref_group_filtering_key), context.getResources().getBoolean(R.bool.pref_group_filtering_def));

        Map<String, List<String>> contactGroupMembership = getRawContactGroupTitles(contentResolver);
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
                        // Check for full account blacklist first (applies always)
                        if (blacklistedGroups.contains(null)) {
                            isBlacklisted = true;
                        } else if (groupFilteringEnabled && !blacklistedGroups.isEmpty()) {
                            // If not fully blacklisted, check group-based blacklist (only if feature is enabled)
                            String rawContactId = dataCursor.getString(rawContactIdColumn);
                            List<String> contactGroups = contactGroupMembership.get(rawContactId);

                            if (contactGroups != null && !contactGroups.isEmpty()) {
                                // A contact is only blacklisted if ALL of its groups are blacklisted
                                boolean allGroupsBlacklisted = true;
                                for (String groupTitle : contactGroups) {
                                    if (!blacklistedGroups.contains(groupTitle)) {
                                        allGroupsBlacklisted = false;
                                        break;
                                    }
                                }
                                if (allGroupsBlacklisted) {
                                    isBlacklisted = true;
                                }
                            } else {
                                // Contact has no group, check if "No Group" is blacklisted
                                if (blacklistedGroups.contains(Constants.GROUP_TITLE_NO_GROUP)) {
                                    isBlacklisted = true;
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
                                 int eventCustomLabelColumn, boolean includeAge, String displayName, int age, String lookupKey, Map<String, String> firstNameCache, Map<String, String> lastNameCache, boolean useLastNameFirst) {
        if (TextUtils.isEmpty(displayName)) {
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
        if (isFullVersionUnlocked(context) && (includeAge)) {
            title = addJubileeIcon(context, title, age);
        }

        // Replace placeholders
        String formattedDisplayName = getDisplayName(context, lookupKey, displayName, firstNameCache, lastNameCache, useLastNameFirst);
        if (title.contains("{FIRSTNAME}")) {
            String firstName = getFirstName(context, lookupKey, displayName, firstNameCache);
            title = title.replace("{FIRSTNAME}", firstName);
        }
        title = title.replace("{NAME}", formattedDisplayName);
        if (includeAge) {
            title = title.replace("{AGE}", String.valueOf(age));
        }
        if (eventCustomLabel != null) {
            title = title.replace("{LABEL}", eventCustomLabel);
        }

        return title;
    }

    private String getDisplayName(Context context, String lookupKey, String displayName, Map<String, String> firstNameCache, Map<String, String> lastNameCache, boolean useLastNameFirst) {
        if (!useLastNameFirst) {
            return displayName;
        }

        String firstName = getFirstName(context, lookupKey, displayName, firstNameCache);
        String lastName = getLastName(context, lookupKey, displayName, lastNameCache);

        if (!TextUtils.isEmpty(lastName) && !TextUtils.isEmpty(firstName)) {
            return lastName + ", " + firstName;
        } else if (!TextUtils.isEmpty(lastName)) {
            return lastName;
        } else {
            return displayName; // Fallback to full display name
        }
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

    private String getLastName(Context context, String lookupKey, String displayName, Map<String, String> lastNameCache) {
        String lastName = lastNameCache.get(lookupKey);
        if (lastName == null && lookupKey != null) {
            lastName = getLastNameFromLookupKey(context, lookupKey);
            // Fallback to splitting the display name if structured name is not available
            if (TextUtils.isEmpty(lastName) && displayName.contains(" ")) {
                lastName = displayName.substring(displayName.lastIndexOf(' ') + 1);
            }
            lastNameCache.put(lookupKey, lastName);
        } else if (lastName == null) {
            // Fallback for when lookupKey is null for some reason
            if (displayName.contains(" ")) {
                lastName = displayName.substring(displayName.lastIndexOf(' ') + 1);
            }
        }
        return lastName;
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
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    int givenNameColumnIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME);
                    if (givenNameColumnIndex != -1) {
                        String givenName = cursor.getString(givenNameColumnIndex);
                        if (!TextUtils.isEmpty(givenName)) {
                            return givenName;
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(Constants.TAG, "Error querying for given name using lookup key: " + lookupKey, e);
        }
        return null;
    }

    private String getLastNameFromLookupKey(Context context, String lookupKey) {
        if (lookupKey == null) {
            return null;
        }
        Uri lookupUri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_LOOKUP_URI, lookupKey);
        Uri dataUri = Uri.withAppendedPath(lookupUri, ContactsContract.Contacts.Data.CONTENT_DIRECTORY);

        String[] projection = {ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME};
        String selection = ContactsContract.Data.MIMETYPE + " = ?";
        String[] selectionArgs = {ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE};

        try (Cursor cursor = context.getContentResolver().query(dataUri, projection, selection, selectionArgs, null)) {
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    int familyNameColumnIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME);
                    if (familyNameColumnIndex != -1) {
                        String familyName = cursor.getString(familyNameColumnIndex);
                        if (!TextUtils.isEmpty(familyName)) {
                            return familyName;
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(Constants.TAG, "Error querying for family name using lookup key: " + lookupKey, e);
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
                                                 long dtstart, String title, String lookupKey, String eventUid, boolean hasReminders, Account account)
            throws OperationCanceledException {
        if (Thread.currentThread().isInterrupted()) {
            throw new OperationCanceledException();
        }

        ContentProviderOperation.Builder builder =
                ContentProviderOperation.newInsert(CalendarHelper.getBirthdayAdapterUri(CalendarContract.Events.CONTENT_URI, account));

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
            builder.withValue(CalendarContract.Events.CUSTOM_APP_PACKAGE, getAppPackageName(context, account));
            builder.withValue(CalendarContract.Events.SYNC_DATA1, Installation.id(context));
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
        if (PreferencesHelper.getPreferDDSlashMM(context)) {
            formatsToTry = new String[]{"yyyy-MM-dd", "--MM-dd", "yyyyMMdd", "dd.MM.yyyy", "yyyy.MM.dd", "dd/MM/yyyy", "dd/MM"};
        } else {
            formatsToTry = new String[]{"yyyy-MM-dd", "--MM-dd", "yyyyMMdd", "MM/dd/yyyy", "MM/dd"};
        }

        for (String format : formatsToTry) {
            boolean setYear1700 = format.equals("--MM-dd") || format.equals("dd/MM") || format.equals("MM/dd");
            Date parsedDate = parseStringWithSimpleDateFormat(eventDateString, format);
            if (parsedDate != null) {
                if (setYear1700) {
                    Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
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

    private String generateHash(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes());
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed on all Android platforms, so this should never happen.
            Log.e(Constants.TAG, "Failed to generate hash", e);
            return input; // Fallback to the original string
        }
    }
}
