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

    private static String CALENDAR_COLUMN_NAME = "birthday_adapter";

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
        Uri calenderUri = getBirthdayAdapterUri(CalendarContract.Calendars.CONTENT_URI);

        // be sure to select the birthday calendar only (additionally to appendQueries in
        // getBirthdayAdapterUri for Android < 4)
        try (Cursor cursor = contentResolver.query(calenderUri, new String[]{BaseColumns._ID},
                CalendarContract.Calendars.ACCOUNT_NAME + " = ? AND " + CalendarContract.Calendars.ACCOUNT_TYPE + " = ?",
                new String[]{Constants.ACCOUNT_NAME, Constants.ACCOUNT_TYPE}, null)) {
            if (cursor != null && cursor.moveToNext()) {
                return cursor.getLong(0);
            } else {
                ArrayList<ContentProviderOperation> operationList = new ArrayList<>();

                ContentProviderOperation.Builder builder = ContentProviderOperation
                        .newInsert(calenderUri);
                builder.withValue(CalendarContract.Calendars.ACCOUNT_NAME, Constants.ACCOUNT_NAME);
                builder.withValue(CalendarContract.Calendars.ACCOUNT_TYPE, Constants.ACCOUNT_TYPE);
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
                    contentResolver.applyBatch(CalendarContract.AUTHORITY, operationList);
                } catch (Exception e) {
                    Log.e(Constants.TAG, "getCalendar() failed", e);
                    return -1;
                }
                return getCalendar(context);
            }
        }
    }

    /**
     * Builds URI for Birthday Adapter based on account. Ensures that only the calendar of Birthday
     * Adapter is chosen.
     */
    public static Uri getBirthdayAdapterUri(Uri uri) {
        return uri.buildUpon().appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
                .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, Constants.ACCOUNT_NAME)
                .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, Constants.ACCOUNT_TYPE).build();
    }

}
