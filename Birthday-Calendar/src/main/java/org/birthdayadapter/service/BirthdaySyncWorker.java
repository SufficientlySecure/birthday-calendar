package org.birthdayadapter.service;

import android.Manifest;
import android.accounts.Account;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
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

import org.birthdayadapter.R;
import org.birthdayadapter.provider.ProviderHelper;
import org.birthdayadapter.util.CalendarHelper;
import org.birthdayadapter.util.Constants;
import org.birthdayadapter.util.Log;
import org.birthdayadapter.util.PreferencesHelper;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.TimeZone;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class BirthdaySyncWorker extends Worker {

    private HashSet<Integer> jubileeYears;

    public BirthdaySyncWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            Log.d(Constants.TAG, "BirthdaySyncWorker started sync.");
            performSync(getApplicationContext());
            Log.d(Constants.TAG, "BirthdaySyncWorker finished sync.");
            return Result.success();
        } catch (Exception e) {
            Log.e(Constants.TAG, "BirthdaySyncWorker failed", e);
            return Result.failure();
        }
    }

    private void performSync(Context context) throws OperationCanceledException {
        Log.d(Constants.TAG, "Starting sync...");

        // Check for calendar permissions before proceeding
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
            Log.e("BirthdaySyncWorker", "Unable to create or find calendar");
            return;
        }

        cleanTables(contentResolver, calendarId);

        int[] reminderMinutes = PreferencesHelper.getAllReminderMinutes(context);
        Log.d(Constants.TAG, "Reminder minutes: " + Arrays.toString(reminderMinutes));

        ArrayList<ContentProviderOperation> operationList = new ArrayList<>();

        try (Cursor cursor = getContactsEvents(context, contentResolver)) {
            if (cursor == null) {
                Log.e(Constants.TAG, "Unable to get events from contacts! Cursor is null!");
                return;
            }

            int eventDateColumn = cursor
                    .getColumnIndex(ContactsContract.CommonDataKinds.Event.START_DATE);
            int displayNameColumn = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME);
            int eventTypeColumn = cursor
                    .getColumnIndex(ContactsContract.CommonDataKinds.Event.TYPE);
            int eventCustomLabelColumn = cursor
                    .getColumnIndex(ContactsContract.CommonDataKinds.Event.LABEL);
            int eventLookupKeyColumn = cursor
                    .getColumnIndex(ContactsContract.CommonDataKinds.Event.LOOKUP_KEY);

            int backRef = 0;

            while (cursor.moveToNext()) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new OperationCanceledException();
                }

                String eventDateString = cursor.getString(eventDateColumn);
                String displayName = cursor.getString(displayNameColumn);
                int eventType = cursor.getInt(eventTypeColumn);
                String eventLookupKey = cursor.getString(eventLookupKeyColumn);

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

                        int age = iteratedYear - eventYear;
                        boolean includeAge = hasYear && age >= 0;

                        String title = generateTitle(context, eventType, cursor,
                                eventCustomLabelColumn, includeAge, displayName, age);

                        if (title != null) {
                            Log.d(Constants.TAG, "Adding event: " + title);
                            operationList.add(insertEvent(context, calendarId, eventDate,
                                    iteratedYear, title, eventLookupKey));

                            int noOfReminderOperations = 0;
                            for (int i = 0; i < 3; i++) {
                                if (reminderMinutes[i] != Constants.DISABLED_REMINDER) {
                                    ContentProviderOperation.Builder builder = ContentProviderOperation
                                            .newInsert(CalendarHelper.getBirthdayAdapterUri(CalendarContract.Reminders.CONTENT_URI));

                                    builder.withValueBackReference(CalendarContract.Reminders.EVENT_ID, backRef);
                                    builder.withValue(CalendarContract.Reminders.MINUTES, reminderMinutes[i]);
                                    builder.withValue(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT);
                                    operationList.add(builder.build());

                                    noOfReminderOperations += 1;
                                }
                            }

                            backRef += 1 + noOfReminderOperations;
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

        if (operationList.size() > 0) {
            try {
                contentResolver.applyBatch(CalendarContract.AUTHORITY, operationList);
            } catch (Exception e) {
                Log.e(Constants.TAG, "Applying batch error!", e);
            }
        }
    }


    private Cursor getContactsEvents(Context context, ContentResolver contentResolver)
            throws OperationCanceledException {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            Log.e(Constants.TAG, "Missing READ_CONTACTS permission!");
            return null;
        }

        if (Thread.currentThread().isInterrupted()) {
            throw new OperationCanceledException();
        }

        HashSet<Account> blacklist = ProviderHelper.getAccountBlacklist(context);
        HashSet<String> addedEventsIdentifiers = new HashSet<>();

        Uri rawContactsUri = ContactsContract.RawContacts.CONTENT_URI;
        String[] rawContactsProjection = new String[]{
                ContactsContract.RawContacts._ID,
                ContactsContract.RawContacts.CONTACT_ID,
                ContactsContract.RawContacts.DISPLAY_NAME_PRIMARY,
                ContactsContract.RawContacts.ACCOUNT_NAME,
                ContactsContract.RawContacts.ACCOUNT_TYPE,};

        String[] columns = new String[]{
                BaseColumns._ID,
                ContactsContract.Data.DISPLAY_NAME,
                ContactsContract.Data.LOOKUP_KEY,
                ContactsContract.CommonDataKinds.Event.START_DATE,
                ContactsContract.CommonDataKinds.Event.TYPE,
                ContactsContract.CommonDataKinds.Event.LABEL
        };
        MatrixCursor mc = new MatrixCursor(columns);
        int mcIndex = 0;

        try (Cursor rawContacts = contentResolver.query(rawContactsUri, rawContactsProjection, null, null, null)) {
            if (rawContacts == null) {
                return mc;
            }
            while (rawContacts.moveToNext()) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new OperationCanceledException();
                }

                long rawId = rawContacts.getLong(rawContacts.getColumnIndex(ContactsContract.RawContacts._ID));
                String accType = rawContacts.getString(rawContacts.getColumnIndex(ContactsContract.RawContacts.ACCOUNT_TYPE));
                String accName = rawContacts.getString(rawContacts.getColumnIndex(ContactsContract.RawContacts.ACCOUNT_NAME));

                boolean addEvent = false;
                if (TextUtils.isEmpty(accType) || TextUtils.isEmpty(accName)) {
                    addEvent = true;
                } else {
                    Account acc = new Account(accName, accType);
                    if (!blacklist.contains(acc)) {
                        addEvent = true;
                    }
                }

                if (addEvent) {
                    String displayName = null;
                    String lookupKey = null;
                    String startDate;
                    int type;
                    String label;

                    String[] displayProjection = new String[]{
                            ContactsContract.Data.RAW_CONTACT_ID,
                            ContactsContract.Data.DISPLAY_NAME,
                            ContactsContract.Data.LOOKUP_KEY,
                    };
                    String displayWhere = ContactsContract.Data.RAW_CONTACT_ID + "= ?";
                    String[] displaySelectionArgs = new String[]{
                            String.valueOf(rawId)
                    };
                    try (Cursor displayCursor = contentResolver.query(ContactsContract.Data.CONTENT_URI, displayProjection,
                            displayWhere, displaySelectionArgs, null)) {
                        if (displayCursor != null && displayCursor.moveToFirst()) {
                            displayName = displayCursor.getString(displayCursor.getColumnIndex(ContactsContract.Data.DISPLAY_NAME));
                            lookupKey = displayCursor.getString(displayCursor.getColumnIndex(ContactsContract.Data.LOOKUP_KEY));
                        }
                    }

                    Uri thisRawContactUri = ContentUris.withAppendedId(ContactsContract.RawContacts.CONTENT_URI, rawId);
                    Uri entityUri = Uri.withAppendedPath(thisRawContactUri, ContactsContract.RawContacts.Entity.CONTENT_DIRECTORY);
                    String[] eventsProjection = new String[]{
                            ContactsContract.RawContacts._ID,
                            ContactsContract.RawContacts.Entity.DATA_ID,
                            ContactsContract.CommonDataKinds.Event.START_DATE,
                            ContactsContract.CommonDataKinds.Event.TYPE,
                            ContactsContract.CommonDataKinds.Event.LABEL
                    };
                    String eventsWhere = ContactsContract.RawContacts.Entity.MIMETYPE + "= ? AND "
                            + ContactsContract.RawContacts.Entity.DATA_ID + " IS NOT NULL";
                    String[] eventsSelectionArgs = new String[]{
                            ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE
                    };
                    try (Cursor eventsCursor = contentResolver.query(entityUri, eventsProjection, eventsWhere,
                            eventsSelectionArgs, null)) {
                        if (eventsCursor == null) {
                            continue;
                        }
                        while (eventsCursor.moveToNext()) {
                            if (Thread.currentThread().isInterrupted()) {
                                throw new OperationCanceledException();
                            }

                            startDate = eventsCursor.getString(eventsCursor.getColumnIndex(ContactsContract.CommonDataKinds.Event.START_DATE));
                            type = eventsCursor.getInt(eventsCursor.getColumnIndex(ContactsContract.CommonDataKinds.Event.TYPE));
                            label = eventsCursor.getString(eventsCursor.getColumnIndex(ContactsContract.CommonDataKinds.Event.LABEL));

                            String eventIdentifier = lookupKey + type + label;
                            if (!addedEventsIdentifiers.contains(eventIdentifier)) {
                                addedEventsIdentifiers.add(eventIdentifier);
                                mc.newRow().add(mcIndex).add(displayName).add(lookupKey).add(startDate).add(type).add(label);
                                mcIndex++;
                            }
                        }
                    }
                }
            }
        }

        return mc;
    }

    private String generateTitle(Context context, int eventType, Cursor cursor,
                                 int eventCustomLabelColumn, boolean includeAge, String displayName, int age) {
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
        if (includeAge) {
            title = addJubileeIcon(context, title, age);
        }

        // Replace user-friendly placeholders with String.format specifiers
        if (eventCustomLabel != null) {
            // Order for custom: 1:name, 2:label, 3:age
            title = title.replace("{NAME}", "%1$s");
            title = title.replace("{LABEL}", "%2$s");
            title = title.replace("{AGE}", "%3$d");
            return String.format(title, displayName, eventCustomLabel, age);
        } else {
            // Order for all others: 1:name, 2:age
            title = title.replace("{NAME}", "%1$s");
            title = title.replace("{AGE}", "%2$d");
            return String.format(title, displayName, age);
        }
    }


    private String addJubileeIcon(Context context, String title, int age) {
        if (jubileeYears == null) {
            Resources res = context.getResources();
            int[] years = res.getIntArray(R.array.jubilee_years);
            jubileeYears = (HashSet<Integer>) IntStream.of(years).boxed().collect(Collectors.toSet());
        }
        if (jubileeYears.contains(age)) {
            return "\uD83C\uDF89 " + title;
        }
        return title;
    }

    private void cleanTables(ContentResolver contentResolver, long calendarId) {
        int delEventsRows = contentResolver.delete(CalendarHelper.getBirthdayAdapterUri(CalendarContract.Events.CONTENT_URI),
                CalendarContract.Events.CALENDAR_ID + " = ?", new String[]{String.valueOf(calendarId)});
        Log.i(Constants.TAG, "Events of birthday calendar is now empty, deleted " + delEventsRows
                + " rows!");
    }


    private ContentProviderOperation insertEvent(Context context, long calendarId,
                                                 Date eventDate, int year, String title, String lookupKey)
            throws OperationCanceledException {
        if (Thread.currentThread().isInterrupted()) {
            throw new OperationCanceledException();
        }

        ContentProviderOperation.Builder builder =
                ContentProviderOperation.newInsert(CalendarHelper.getBirthdayAdapterUri(CalendarContract.Events.CONTENT_URI));

        Calendar cal = Calendar.getInstance();
        cal.setTime(eventDate);
        cal.set(Calendar.YEAR, year);
        cal.set(Calendar.HOUR, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);

        cal.setTimeZone(TimeZone.getTimeZone("UTC"));

        long dtstart = cal.getTimeInMillis();
        long dtend = dtstart + DateUtils.DAY_IN_MILLIS;

        builder.withValue(CalendarContract.Events.CALENDAR_ID, calendarId);
        builder.withValue(CalendarContract.Events.DTSTART, dtstart);
        builder.withValue(CalendarContract.Events.DTEND, dtend);
        builder.withValue(CalendarContract.Events.EVENT_TIMEZONE, "UTC");

        builder.withValue(CalendarContract.Events.ALL_DAY, 1);
        builder.withValue(CalendarContract.Events.TITLE, title);
        builder.withValue(CalendarContract.Events.STATUS, CalendarContract.Events.STATUS_CONFIRMED);

        builder.withValue(CalendarContract.Events.HAS_ALARM, 1);

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
        dateFormat.setTimeZone(TimeZone.getDefault());
        try {
            return dateFormat.parse(input);
        } catch (ParseException e) {
            return null;
        }
    }
}
