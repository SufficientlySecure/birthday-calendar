package org.birthdayadapter.util;

import android.Manifest;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.BaseColumns;
import android.provider.CalendarContract;
import androidx.core.content.ContextCompat;

import org.birthdayadapter.BuildConfig;
import org.birthdayadapter.R;

import java.util.ArrayList;

public class CalendarHelper {

    /**
     * Gets calendar id, when no calendar is present, create one!
     */
    public static long getCalendar(Context context) {
        Log.d(Constants.TAG, "getCalendar Method...");

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            Log.e(Constants.TAG, "Missing calendar permissions to get or create calendar!");
            return -1;
        }

        ContentResolver contentResolver = context.getContentResolver();

        // Find the calendar if we've got one
        Uri calenderUri = getBirthdayAdapterUri(context, CalendarContract.Calendars.CONTENT_URI);

        // be sure to select the birthday calendar only (additionally to appendQueries in
        // getBirthdayAdapterUri for Android < 4)
        try (Cursor cursor = contentResolver.query(calenderUri, new String[]{BaseColumns._ID},
                CalendarContract.Calendars.ACCOUNT_NAME + " = ? AND " + CalendarContract.Calendars.ACCOUNT_TYPE + " = ?",
                new String[]{Constants.ACCOUNT_NAME, context.getString(R.string.account_type)}, null)) {
            if (cursor != null && cursor.moveToNext()) {
                return cursor.getLong(0);
            } else {
                ArrayList<ContentProviderOperation> operationList = new ArrayList<>();

                ContentProviderOperation.Builder builder = ContentProviderOperation
                        .newInsert(calenderUri);
                builder.withValue(CalendarContract.Calendars.ACCOUNT_NAME, Constants.ACCOUNT_NAME);
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
                builder.withValue(CalendarContract.Calendars.OWNER_ACCOUNT, Constants.ACCOUNT_NAME);
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
        Uri calendarUri = getBirthdayAdapterUri(context, CalendarContract.Calendars.CONTENT_URI);

        int deletedRows = contentResolver.delete(calendarUri, null, null);

        if (deletedRows > 0) {
            Log.i(Constants.TAG, "Successfully deleted birthday calendar.");
        } else {
            Log.w(Constants.TAG, "Birthday calendar not found or could not be deleted.");
        }
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
        Uri eventsUri = getBirthdayAdapterUri(context, CalendarContract.Events.CONTENT_URI);

        // A selection is required when using CALLER_IS_SYNCADAPTER=true
        int deletedRows = contentResolver.delete(eventsUri, "1", null);

        if (deletedRows > 0) {
            Log.i(Constants.TAG, "Successfully cleared " + deletedRows + " old events.");
        } else {
            Log.d(Constants.TAG, "Calendar was already empty. No events to clear.");
        }
    }

    /**
     * Builds URI for Birthday Adapter based on account. Ensures that only the calendar of Birthday
     * Adapter is chosen.
     */
    public static Uri getBirthdayAdapterUri(Context context, Uri uri) {
        return uri.buildUpon().appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
                .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, Constants.ACCOUNT_NAME)
                .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, context.getString(R.string.account_type)).build();
    }

}
