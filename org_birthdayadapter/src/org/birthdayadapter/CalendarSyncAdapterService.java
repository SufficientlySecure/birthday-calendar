/*
 * Copyright (C) 2010 Sam Steele
 * Copyright (C) 2012 Dominik Schürmann <dominik@dominikschuermann.de>
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.birthdayadapter;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

import android.accounts.Account;
import android.accounts.OperationCanceledException;
import android.app.Service;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SyncResult;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.BaseColumns;
import android.provider.CalendarContract;
import android.provider.CalendarContract.Calendars;
import android.provider.CalendarContract.Events;
import android.provider.ContactsContract;
import android.util.Log;

/**
 * CalendarSyncAdapter is mainly based on c99koder / lastfm-android SyncAdapter, see
 * https://github.com
 * /c99koder/lastfm-android/blob/master/app/src/fm/last/android/sync/CalendarSyncAdapterService.java
 * 
 */
public class CalendarSyncAdapterService extends Service {
    private static final String TAG = "BirthdayCalendarSyncAdapterService";
    private static SyncAdapterImpl sSyncAdapter = null;
    private static ContentResolver mContentResolver = null;

    public CalendarSyncAdapterService() {
        super();
    }

    private static class SyncAdapterImpl extends AbstractThreadedSyncAdapter {
        private Context mContext;

        public SyncAdapterImpl(Context context) {
            super(context, true);
            mContext = context;
        }

        @Override
        public void onPerformSync(Account account, Bundle extras, String authority,
                ContentProviderClient provider, SyncResult syncResult) {
            try {
                CalendarSyncAdapterService.performSync(mContext, account, extras, authority,
                        provider, syncResult);
            } catch (OperationCanceledException e) {
            }
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        IBinder ret = null;
        ret = getSyncAdapter().getSyncAdapterBinder();
        return ret;
    }

    private SyncAdapterImpl getSyncAdapter() {
        if (sSyncAdapter == null)
            sSyncAdapter = new SyncAdapterImpl(this);
        return sSyncAdapter;
    }

    private static long getCalendar(Context context, Account account) {
        // Find the calendar if we've got one
        Uri calenderUri = Calendars.CONTENT_URI.buildUpon()
                .appendQueryParameter(Calendars.ACCOUNT_NAME, account.name)
                .appendQueryParameter(Calendars.ACCOUNT_TYPE, account.type).build();
        Cursor c1 = mContentResolver.query(calenderUri, new String[] { BaseColumns._ID }, null,
                null, null);
        if (c1.moveToNext()) {
            return c1.getLong(0);
        } else {
            ArrayList<ContentProviderOperation> operationList = new ArrayList<ContentProviderOperation>();

            ContentProviderOperation.Builder builder = ContentProviderOperation
                    .newInsert(getBirthdayAdapterUri(Calendars.CONTENT_URI, account));
            builder.withValue(Calendars.ACCOUNT_NAME, account.name);
            builder.withValue(Calendars.ACCOUNT_TYPE, account.type);
            builder.withValue(Calendars.NAME, "Birthdays");
            builder.withValue(Calendars.CALENDAR_DISPLAY_NAME,
                    context.getString(R.string.calendar_display_name));
            builder.withValue(Calendars.CALENDAR_COLOR, 0xD51007);
            builder.withValue(Calendars.CALENDAR_ACCESS_LEVEL, Calendars.CAL_ACCESS_READ);
            builder.withValue(Calendars.OWNER_ACCOUNT, account.name);
            builder.withValue(Calendars.SYNC_EVENTS, 1);
            operationList.add(builder.build());
            try {
                mContentResolver.applyBatch(CalendarContract.AUTHORITY, operationList);
            } catch (Exception e) {
                Log.e(TAG, "Error: " + e.getMessage());
                e.printStackTrace();
                return -1;
            }
            return getCalendar(context, account);
        }
    }

    // private static void deleteEvent(Context context, Account account, long rawId) {
    // Uri uri = ContentUris.withAppendedId(getBirthdayAdapterUri(Events.CONTENT_URI, account),
    // rawId);
    // ContentProviderClient client = context.getContentResolver().acquireContentProviderClient(
    // CalendarContract.AUTHORITY);
    // try {
    // client.delete(uri, null, null);
    // } catch (RemoteException e) {
    // Log.e(TAG, "Error: " + e.getMessage());
    // e.printStackTrace();
    // }
    // client.release();
    // }

    static Uri getBirthdayAdapterUri(Uri uri, Account account) {
        return uri.buildUpon().appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
                .appendQueryParameter(Calendars.ACCOUNT_NAME, account.name)
                .appendQueryParameter(Calendars.ACCOUNT_TYPE, account.type).build();
    }

    /**
     * raw_id = -1 will insert a new event
     * 
     * @param calendar_id
     * @param account
     * @param birthday
     * @param raw_id
     * @return
     */
    private static ContentProviderOperation updateEvent(Context context, long calendar_id,
            Account account, Date birthday, String name, long raw_id) {
        ContentProviderOperation.Builder builder;
        if (raw_id != -1) {
            builder = ContentProviderOperation.newUpdate(getBirthdayAdapterUri(Events.CONTENT_URI,
                    account));
            builder.withSelection(Events._ID + " = '" + raw_id + "'", null);
        } else {
            builder = ContentProviderOperation.newInsert(getBirthdayAdapterUri(Events.CONTENT_URI,
                    account));
        }
        String title = String.format(context.getString(R.string.event_title), name);

        Calendar cal = Calendar.getInstance();
        cal.setTime(birthday);
        cal.set(Calendar.HOUR, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);

        // allday events have to be set in UTC!
        // without UTC it results in:
        // CalendarProvider2 W insertInTransaction: allDay is true but sec, min, hour were not 0.
        // http://stackoverflow.com/questions/3440172/getting-exception-when-inserting-events-in-android-calendar
        cal.setTimeZone(TimeZone.getTimeZone("UTC"));

        long dtstart = cal.getTimeInMillis();
        long id = raw_id;

        builder.withValue(Events.CALENDAR_ID, calendar_id);
        builder.withValue(Events.DTSTART, dtstart);
        builder.withValue(Events.TITLE, title);

        builder.withValue(Events.ALL_DAY, 1);

        // Duration: 1 hour
        // without:
        // CalendarProvider2 E Repeating event has no duration -- should not happen.
        builder.withValue(Events.DURATION, "PT1H");

        // repeat rule: every year
        builder.withValue(Events.RRULE, "FREQ=YEARLY");

        builder.withValue(Events.STATUS, Events.STATUS_CONFIRMED);
        builder.withValue(Events._SYNC_ID, Long.valueOf(id));
        return builder.build();

    }

    /**
     * method to get name, contact id, and birthday
     * 
     * http://stackoverflow.com/questions/8579883/get-birthday-for-each-contact-in-android-
     * application
     * 
     * @return
     */
    private static Cursor getContactsBirthdays(Context context) {
        Uri uri = ContactsContract.Data.CONTENT_URI;

        String[] projection = new String[] { ContactsContract.Contacts.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Event.CONTACT_ID,
                ContactsContract.CommonDataKinds.Event.START_DATE };

        String where = ContactsContract.Data.MIMETYPE + "= ? AND "
                + ContactsContract.CommonDataKinds.Event.TYPE + "="
                + ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY;
        String[] selectionArgs = new String[] { ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE };
        String sortOrder = null;

        return context.getContentResolver().query(uri, projection, where, selectionArgs, sortOrder);
    }

    private static void performSync(Context context, Account account, Bundle extras,
            String authority, ContentProviderClient provider, SyncResult syncResult)
            throws OperationCanceledException {
        mContentResolver = context.getContentResolver();

        long calendar_id = getCalendar(context, account);
        if (calendar_id == -1) {
            Log.e("CalendarSyncAdapter", "Unable to create calendar");
            return;
        }

        // Okay, now this works as follows:
        // 1. Clear events table for this account completely
        // 2. Get birthdays from contacts
        // 3. Create event for each birthday

        // Known limitations:
        // - This is not nicely done, I am not doing any updating, just delete everything and then
        // readd everything
        // - birtdays may be stored in other ways on some phones
        // see
        // http://stackoverflow.com/questions/8579883/get-birthday-for-each-contact-in-android-application
        // - problems with date format:
        // http://dmfs.org/carddav/?date_format

        // clear table with workaround: "_id != -1"
        int delRows = mContentResolver.delete(getBirthdayAdapterUri(Events.CONTENT_URI, account),
                "_id != -1", null);
        Log.i(TAG, "number of del rows: " + delRows);

        // collection of birthdays that will later be added to the calendar
        ArrayList<ContentProviderOperation> operationList = new ArrayList<ContentProviderOperation>();

        // date format
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        dateFormat.setTimeZone(TimeZone.getDefault());
        Date birthdayDate = null;

        // iterate through all Contact's Birthdays and print in log
        Cursor cursor = getContactsBirthdays(context);
        int birthdayColumn = cursor
                .getColumnIndex(ContactsContract.CommonDataKinds.Event.START_DATE);
        int displayNameColumn = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME);

        while (cursor.moveToNext()) {
            String birthday = cursor.getString(birthdayColumn);
            String displayName = cursor.getString(displayNameColumn);

            // Log.d(TAG, "Birthday: " + bDay);
            try {
                birthdayDate = dateFormat.parse(birthday);
                // Log.d(TAG, "Birthday of " + displayName + " parsed: " +
                // dateFormat.format(birthdayDate));

                // with raw_id -1 it will make a new one
                operationList.add(updateEvent(context, calendar_id, account, birthdayDate,
                        displayName, -1));

            } catch (ParseException e) {
                Log.e(TAG, "Birthday " + birthday + " of " + displayName
                        + " could not be parsed with yyyy-MM-dd!");
                e.printStackTrace();
            }
        }

        /* Create events */
        if (operationList.size() > 0) {
            try {
                mContentResolver.applyBatch(CalendarContract.AUTHORITY, operationList);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
