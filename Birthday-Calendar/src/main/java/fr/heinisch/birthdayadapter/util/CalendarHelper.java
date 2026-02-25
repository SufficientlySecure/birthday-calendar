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

package fr.heinisch.birthdayadapter.util;

import android.Manifest;
import android.accounts.Account;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.BaseColumns;
import android.provider.CalendarContract;
import android.text.TextUtils;
import android.util.Log;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

import fr.heinisch.birthdayadapter.BuildConfig;
import fr.heinisch.birthdayadapter.R;

public class CalendarHelper {

    public static class CalendarItem {
        public final long id;
        public final String name;
        public final String accountName;

        public CalendarItem(long id, String name, String accountName) {
            this.id = id;
            this.name = name;
            this.accountName = accountName;
        }
    }

    public static List<CalendarItem> getWritableCalendars(Context context) {
        List<CalendarItem> calendars = new ArrayList<>();

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            Log.e(Constants.TAG, "Missing calendar permissions to get writable calendars!");
            return calendars;
        }

        long birthdayCalendarId = getCalendar(context);

        ContentResolver contentResolver = context.getContentResolver();
        Uri uri = CalendarContract.Calendars.CONTENT_URI;

        String[] projection = new String[]{
                CalendarContract.Calendars._ID,
                CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                CalendarContract.Calendars.ACCOUNT_NAME
        };

        String selection = CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL + " >= ?";
        String[] selectionArgs = new String[]{String.valueOf(CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR)};

        try (Cursor cursor = contentResolver.query(uri, projection, selection, selectionArgs, null)) {
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    long id = cursor.getLong(0);
                    String name = cursor.getString(1);
                    String accountName = cursor.getString(2);

                    if (id != birthdayCalendarId) {
                        calendars.add(new CalendarItem(id, name, accountName));
                    }
                }
            }
        }

        return calendars;
    }

    public static Account getAccountForCalendar(Context context, long calendarId) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            Log.e(Constants.TAG, "Missing calendar permissions to get account for calendar!");
            return null;
        }

        ContentResolver contentResolver = context.getContentResolver();
        Uri uri = ContentUris.withAppendedId(CalendarContract.Calendars.CONTENT_URI, calendarId);

        String[] projection = new String[]{
                CalendarContract.Calendars.ACCOUNT_NAME,
                CalendarContract.Calendars.ACCOUNT_TYPE
        };

        try (Cursor cursor = contentResolver.query(uri, projection, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                String accountName = cursor.getString(0);
                String accountType = cursor.getString(1);
                if (!TextUtils.isEmpty(accountName) && !TextUtils.isEmpty(accountType)) {
                    return new Account(accountName, accountType);
                }
            }
        }
        return null;
    }

    public static String getCalendarName(Context context, long calendarId) {
        if (calendarId == -1) {
            return "unknown calendar";
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            Log.e(Constants.TAG, "Missing calendar permissions to get calendar name!");
            return String.valueOf(calendarId); // fallback to id
        }

        ContentResolver contentResolver = context.getContentResolver();
        Uri uri = ContentUris.withAppendedId(CalendarContract.Calendars.CONTENT_URI, calendarId);

        String[] projection = new String[]{
                CalendarContract.Calendars.CALENDAR_DISPLAY_NAME
        };

        try (Cursor cursor = contentResolver.query(uri, projection, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                String name = cursor.getString(0);
                if (!TextUtils.isEmpty(name)) {
                    return name;
                }
            }
        }
        return String.valueOf(calendarId); // fallback to id
    }

    /**
     * Gets calendar id, when no calendar is present, create one!
     */
    public static long getCalendar(Context context) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            Log.e(Constants.TAG, "Missing calendar permissions to get or create calendar!");
            return -1;
        }

        ContentResolver contentResolver = context.getContentResolver();

        // Find the calendar if we've got one
        Uri calenderUri = getBirthdayAdapterUri(context, CalendarContract.Calendars.CONTENT_URI, true);

        // be sure to select the birthday calendar only (additionally to appendQueries in
        // getBirthdayAdapterUri for Android < 4)
        try (Cursor cursor = contentResolver.query(calenderUri, new String[]{BaseColumns._ID},
                CalendarContract.Calendars.ACCOUNT_NAME + " = ? AND " + CalendarContract.Calendars.ACCOUNT_TYPE + " = ?",
                new String[]{Constants.getAccountName(context), context.getString(R.string.account_type)}, null)) {
            if (cursor != null && cursor.moveToNext()) {
                return cursor.getLong(0);
            } else {
                ArrayList<ContentProviderOperation> operationList = new ArrayList<>();

                ContentProviderOperation.Builder builder = ContentProviderOperation
                        .newInsert(calenderUri);
                builder.withValue(CalendarContract.Calendars.ACCOUNT_NAME, Constants.getAccountName(context));
                builder.withValue(CalendarContract.Calendars.ACCOUNT_TYPE, context.getString(R.string.account_type));
                String CALENDAR_COLUMN_NAME = "birthday_adapter";
                builder.withValue(CalendarContract.Calendars.NAME, CALENDAR_COLUMN_NAME);
                builder.withValue(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                        context.getString(R.string.calendar_display_name));
                builder.withValue(CalendarContract.Calendars.CALENDAR_COLOR, PreferencesHelper.getColor(context));
                if (BuildConfig.DEBUG) {
                    builder.withValue(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL, CalendarContract.Calendars.CAL_ACCESS_EDITOR);
                } else {
                    builder.withValue(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL, CalendarContract.Calendars.CAL_ACCESS_READ);
                }
                builder.withValue(CalendarContract.Calendars.OWNER_ACCOUNT, Constants.getAccountName(context));
                builder.withValue(CalendarContract.Calendars.SYNC_EVENTS, 1);
                builder.withValue(CalendarContract.Calendars.VISIBLE, 1);
                operationList.add(builder.build());
                try {
                    android.content.ContentProviderResult[] results = contentResolver.applyBatch(CalendarContract.AUTHORITY, operationList);
                    if (results.length > 0) {
                        assert results[0].uri != null;
                        return android.content.ContentUris.parseId(results[0].uri);
                    } else {
                        return -1;
                    }
                } catch (Exception e) {
                    Log.e(Constants.TAG, "getCalendar() failed", e);
                    return -1;
                }
            }
        }
    }

    /**
     * Deletes the birthday calendar and all its events.
     */
    public static void deleteCalendar(Context context) {
        Log.d(Constants.TAG, "Deleting birthday calendar...");

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            Log.e(Constants.TAG, "Missing calendar permissions to delete calendar!");
            return;
        }

        ContentResolver contentResolver = context.getContentResolver();
        Uri calendarUri = getBirthdayAdapterUri(context, CalendarContract.Calendars.CONTENT_URI, true);

        int deletedRows = contentResolver.delete(calendarUri, null, null);

        if (deletedRows > 0) {
            Log.i(Constants.TAG, "Successfully deleted birthday calendar.");
        } else {
            Log.w(Constants.TAG, "Birthday calendar not found or could not be deleted.");
        }
    }

    public static void clearBirthdayAdapterEvents(Context context, long calendarId) {
        String calendarName = getCalendarName(context, calendarId);
        Log.d(Constants.TAG, "Safely clearing all events from calendar: " + calendarName);

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            Log.e(Constants.TAG, "Missing calendar permissions to clear events!");
            return;
        }

        ContentResolver contentResolver = context.getContentResolver();
        Account account = getAccountForCalendar(context, calendarId);
        if (account == null) {
            Log.w(Constants.TAG, "Cannot clear events from calendar '" + calendarName + "' because it has no syncable account.");
            return;
        }
        boolean isOwnCalendar = isBirthdayAdapterAccount(context, account);
        Uri eventsUri = getEventsUri(account, isOwnCalendar);

        String selection;
        String[] selectionArgs;
        if (isOwnCalendar) {
            selection = CalendarContract.Events.CALENDAR_ID + " = ? AND " + CalendarContract.Events.CUSTOM_APP_PACKAGE + " = ?";
            selectionArgs = new String[]{String.valueOf(calendarId), getAppPackageName(context, account)};
        } else {
            selection = CalendarContract.Events.CALENDAR_ID + " = ? AND " + CalendarContract.Events.DESCRIPTION + " LIKE ?";
            selectionArgs = new String[]{String.valueOf(calendarId), Constants.MANAGED_BY_BIRTHDAY_ADAPTER + "%"};
        }

        int deletedRows = contentResolver.delete(eventsUri, selection, selectionArgs);

        if (deletedRows > 0) {
            Log.i(Constants.TAG, "Successfully cleared " + deletedRows + " old events from calendar: " + calendarName);
            if (!isOwnCalendar) { requestCalendarSync(context, calendarId, true); }
        } else {
            Log.d(Constants.TAG, "Calendar " + calendarName + " was already empty or had no events from this app. No events to clear.");
        }
    }

    public static void resetAllBirthdayAdapterEventsInCalendar(Context context, long calendarId) {
        String calendarName = getCalendarName(context, calendarId);
        Log.d(Constants.TAG, "Force clearing all events from calendar: " + calendarName);

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            Log.e(Constants.TAG, "Missing calendar permissions to clear events!");
            return;
        }

        ContentResolver contentResolver = context.getContentResolver();
        Account account = getAccountForCalendar(context, calendarId);
        if (account == null) {
            Log.w(Constants.TAG, "Cannot clear events from calendar '" + calendarName + "' because it has no syncable account.");
            return;
        }
        boolean isOwnCalendar = isBirthdayAdapterAccount(context, account);
        Uri eventsUri = getEventsUri(account, isOwnCalendar);

        String selection;
        String[] selectionArgs;

        if (isOwnCalendar) {
            selection = CalendarContract.Events.CALENDAR_ID + " = ?";
            selectionArgs = new String[]{String.valueOf(calendarId)};
        } else {
            selection = CalendarContract.Events.CALENDAR_ID + " = ? AND " + CalendarContract.Events.DESCRIPTION + " LIKE ?";
            selectionArgs = new String[]{String.valueOf(calendarId), Constants.MANAGED_BY_BIRTHDAY_ADAPTER + "%"};
        }

        int deletedRows = contentResolver.delete(eventsUri, selection, selectionArgs);

        if (deletedRows > 0) {
            Log.i(Constants.TAG, "Successfully force cleared " + deletedRows + " events from calendar: " + calendarName);
            if (!isOwnCalendar) { requestCalendarSync(context, calendarId, true); }
        } else {
            Log.d(Constants.TAG, "Calendar " + calendarName + " had no events from this app to clear.");
        }
    }

    public static void requestCalendarSync(Context context, long calendarId, boolean isUrgent) {
        Account account = getAccountForCalendar(context, calendarId);
        if (account == null) {
            Log.w(Constants.TAG, "Cannot request sync for calendar " + calendarId + " because no account was found.");
            return;
        }
        Bundle bundle = new Bundle();
        if (isUrgent) {
            Log.d(Constants.TAG, "Requesting immediate sync for calendar " + calendarId + " with account " + account.name);
            bundle.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true);
            bundle.putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true);
        } else {
            Log.d(Constants.TAG, "Requesting sync for calendar " + calendarId + " with account " + account.name);
        }
        ContentResolver.requestSync(account, CalendarContract.AUTHORITY, bundle);
    }


    /**
     * Deletes all events from the birthday calendar.
     */
    public static void clearAllEvents(Context context) {
        Log.d(Constants.TAG, "Clearing all events from birthday calendar...");

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            Log.e(Constants.TAG, "Missing calendar permissions to clear events!");
            return;
        }

        ContentResolver contentResolver = context.getContentResolver();
        Uri eventsUri = getBirthdayAdapterUri(context, CalendarContract.Events.CONTENT_URI, true);

        // A selection is required when using CALLER_IS_SYNCADAPTER=true
        int deletedRows = contentResolver.delete(eventsUri, "1", null);

        if (deletedRows > 0) {
            Log.i(Constants.TAG, "Successfully cleared " + deletedRows + " old events.");
        } else {
            Log.d(Constants.TAG, "Calendar was already empty. No events to clear.");
        }
    }

    public static Uri getEventsUri(Account account, boolean isOwnCalendar) {
        Uri.Builder builder = CalendarContract.Events.CONTENT_URI.buildUpon();
        if (isOwnCalendar) {
            builder.appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true");
            if (account != null) {
                builder.appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, account.name)
                        .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, account.type);
            }
        }
        return builder.build();
    }

    public static Uri getRemindersUri(Account account, boolean isOwnCalendar) {
        Uri.Builder builder = CalendarContract.Reminders.CONTENT_URI.buildUpon();
        if (isOwnCalendar) {
            builder.appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true");
            if (account != null) {
                builder.appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, account.name)
                        .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, account.type);
            }
        }
        return builder.build();
    }


    /**
     * Builds URI for Birthday Adapter based on account. Ensures that only the calendar of Birthday
     * Adapter is chosen.
     */
    public static Uri getBirthdayAdapterUri(Context context, Uri uri, boolean asSyncAdapter) {
        return getBirthdayAdapterUri(uri, new Account(Constants.getAccountName(context), context.getString(R.string.account_type)), asSyncAdapter);
    }

    /**
     * Builds URI for a sync adapter operation.
     *
     * @param uri The base URI.
     * @param account The account to use for the operation. Can be null.
     * @return The URI with sync adapter parameters.
     */
    public static Uri getBirthdayAdapterUri(Uri uri, Account account, boolean asSyncAdapter) {
        Uri.Builder builder = uri.buildUpon();
        if (asSyncAdapter) {
            builder.appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true");
        }
        if (account != null) {
            builder.appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, account.name)
                    .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, account.type);
        }
        return builder.build();
    }

    public static boolean isBirthdayAdapterAccount(Context context, Account account) {
        if (account == null) {
            return false;
        }
        String expectedAccountName = Constants.getAccountName(context);
        String expectedAccountType = context.getString(R.string.account_type);
        return expectedAccountName.equals(account.name) && expectedAccountType.equals(account.type);
    }

    /**
     * Builds the package name string used to identify events created by this app.
     *
     * @param context The application context.
     * @param account The account associated with the calendar.
     * @return A unique identifier string for the app's events.
     */
    public static String getAppPackageName(Context context, Account account) {
        return context.getPackageName() + "/" + account.name + "/" + account.type;
    }

}
