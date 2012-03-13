/*
 * Copyright (C) 2010 Sam Steele
 * Copyright (C) 2011 Dominik Schürmann <dominik@dominikschuermann.de>
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

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;

import android.accounts.Account;
import android.accounts.OperationCanceledException;
import android.app.Service;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences.Editor;
import android.content.SyncResult;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.provider.BaseColumns;
import android.provider.CalendarContract;
import android.provider.CalendarContract.Calendars;
import android.provider.CalendarContract.Events;
import android.provider.ContactsContract;
import android.provider.ContactsContract.RawContacts;
import android.provider.ContactsContract.RawContacts.Entity;
import android.util.Log;

public class CalendarSyncAdapterService extends Service {
    private static final String TAG = "CalendaryncAdapterService";
    private static SyncAdapterImpl sSyncAdapter = null;
    private static ContentResolver mContentResolver = null;
    private static String UsernameColumn = ContactsContract.RawContacts.SYNC1;
    private static String PhotoTimestampColumn = ContactsContract.RawContacts.SYNC2;

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

    private static long getCalendar(Account account) {
        // Find the Last.fm calendar if we've got one
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
                    .newInsert(Calendars.CONTENT_URI.buildUpon()
                            .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
                            .appendQueryParameter(Calendars.ACCOUNT_NAME, account.name)
                            .appendQueryParameter(Calendars.ACCOUNT_TYPE, account.type).build());
            builder.withValue(Calendars.ACCOUNT_NAME, account.name);
            builder.withValue(Calendars.ACCOUNT_TYPE, account.type);
            builder.withValue(Calendars.NAME, "Last.fm Events");
            builder.withValue(Calendars.CALENDAR_DISPLAY_NAME, "Last.fm Events");
            builder.withValue(Calendars.CALENDAR_COLOR, 0xD51007);
            builder.withValue(Calendars.CALENDAR_ACCESS_LEVEL, Calendars.CAL_ACCESS_READ);
            builder.withValue(Calendars.OWNER_ACCOUNT, account.name);
            builder.withValue(Calendars.SYNC_EVENTS, 1);
            operationList.add(builder.build());
            try {
                mContentResolver.applyBatch(CalendarContract.AUTHORITY, operationList);
            } catch (Exception e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
                return -1;
            }
            return getCalendar(account);
        }
    }

    private static void deleteEvent(Context context, Account account, long rawId) {
        Uri uri = ContentUris.withAppendedId(Events.CONTENT_URI, rawId).buildUpon()
                .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
                .appendQueryParameter(Calendars.ACCOUNT_NAME, account.name)
                .appendQueryParameter(Calendars.ACCOUNT_TYPE, account.type).build();
        ContentProviderClient client = context.getContentResolver().acquireContentProviderClient(
                CalendarContract.AUTHORITY);
        try {
            client.delete(uri, null, null);
        } catch (RemoteException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        client.release();
    }

    private static ContentProviderOperation updateEvent(long calendar_id, Account account,
            Date event, long raw_id) {
        ContentProviderOperation.Builder builder;
        if (raw_id != -1) {
            builder = ContentProviderOperation.newUpdate(Events.CONTENT_URI.buildUpon()
                    .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
                    .appendQueryParameter(Calendars.ACCOUNT_NAME, account.name)
                    .appendQueryParameter(Calendars.ACCOUNT_TYPE, account.type).build());
            builder.withSelection(Events._ID + " = '" + raw_id + "'", null);
        } else {
            builder = ContentProviderOperation.newInsert(Events.CONTENT_URI.buildUpon()
                    .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
                    .appendQueryParameter(Calendars.ACCOUNT_NAME, account.name)
                    .appendQueryParameter(Calendars.ACCOUNT_TYPE, account.type).build());
        }
        // long dtstart = event.getStartDate().getTime();
        // long dtend = dtstart + (1000*60*60);
        // if(event.getEndDate() != null)
        // dtend = event.getEndDate().getTime();
        // builder.withValue(Events.CALENDAR_ID, calendar_id);
        // builder.withValue(Events.DTSTART, dtstart);
        // builder.withValue(Events.DTEND, dtend);
        // builder.withValue(Events.TITLE, event.getTitle());
        //
        // String location = "";
        // if(event.getVenue().getName().length() > 0)
        // location += event.getVenue().getName() + "\n";
        // if(event.getVenue().getLocation().getCity().length() > 0)
        // location += event.getVenue().getLocation().getCity() + "\n";
        // if(event.getVenue().getLocation().getCountry().length() > 0)
        // location += event.getVenue().getLocation().getCountry() + "\n";
        //
        // builder.withValue(Events.EVENT_LOCATION, location);
        //
        // String description = "http://www.last.fm/event/" + event.getId() + "\n\n";
        //
        // if(event.getArtists().length > 0) {
        // description += "LINEUP\n";
        // for(String artist : event.getArtists()) {
        // description += artist + "\n";
        // }
        // description += "\n";
        // }
        //
        // if(event.getDescription() != null && event.getDescription().length() > 0) {
        // description += "MORE DETAILS\n";
        // description += event.getDescription();
        // }
        //
        // builder.withValue(Events.DESCRIPTION, description);
        //
        // if(Integer.valueOf(event.getStatus()) == 1)
        // builder.withValue(Events.STATUS, Events.STATUS_TENTATIVE);
        // else
        // builder.withValue(Events.STATUS, Events.STATUS_CONFIRMED);
        // builder.withValue(Events._SYNC_ID, Long.valueOf(event.getId()));
        // return builder.build();
        return null;
    }

    private static class SyncEntry {
        public Long raw_id = 0L;
    }

    private static void performSync(Context context, Account account, Bundle extras,
            String authority, ContentProviderClient provider, SyncResult syncResult)
            throws OperationCanceledException {
        HashMap<Long, SyncEntry> localEvents = new HashMap<Long, SyncEntry>();
        ArrayList<Long> lastfmEvents = new ArrayList<Long>();
        mContentResolver = context.getContentResolver();

        // If our app has requested a full sync, we're going to delete all our local events and
        // start over
        boolean is_full_sync; // =
                              // PreferenceManager.getDefaultSharedPreferences(LastFMApplication.getInstance()).getBoolean("cal_do_full_sync",
                              // false);

        // If our schema is out-of-date, do a fresh sync
        // if(PreferenceManager.getDefaultSharedPreferences(LastFMApplication.getInstance()).getInt("cal_sync_schema",
        // 0) < syncSchema)
        is_full_sync = true;

        long calendar_id = getCalendar(account);
        if (calendar_id == -1) {
            Log.e("CalendarSyncAdapter", "Unable to create Last.fm event calendar");
            return;
        }

        // // Load the local Last.fm events
        // Uri eventsUri = Events.CONTENT_URI.buildUpon().appendQueryParameter(Events.CALENDAR_ID,
        // String.valueOf(calendar_id)).build();
        // Cursor c1 = mContentResolver.query(eventsUri, new String[] { Events._ID, Events._SYNC_ID
        // }, null, null, null);
        // while (c1 != null && c1.moveToNext()) {
        // if(is_full_sync) {
        // deleteEvent(context, account, c1.getLong(0));
        // } else {
        // SyncEntry entry = new SyncEntry();
        // entry.raw_id = c1.getLong(0);
        // localEvents.put(c1.getLong(1), entry);
        // }
        // }
        // c1.close();
        //
        // Editor editor =
        // PreferenceManager.getDefaultSharedPreferences(LastFMApplication.getInstance()).edit();
        // editor.remove("cal_do_full_sync");
        // editor.putInt("cal_sync_schema", syncSchema);
        // editor.commit();
        //
        // LastFmServer server = AndroidLastFmServerFactory.getServer();
        // try {
        // Event[] events = server.getUserEvents(account.name);
        // ArrayList<ContentProviderOperation> operationList = new
        // ArrayList<ContentProviderOperation>();
        // for (Event event : events) {
        // lastfmEvents.add(Long.valueOf(event.getId()));
        //
        // if (localEvents.containsKey(Long.valueOf(event.getId()))) {
        // SyncEntry entry = localEvents.get(Long.valueOf(event.getId()));
        // operationList.add(updateEvent(calendar_id, account, event, entry.raw_id));
        // } else {
        // operationList.add(updateEvent(calendar_id, account, event, -1));
        // }
        //
        // if(operationList.size() >= 50) {
        // try {
        // mContentResolver.applyBatch(CalendarContract.AUTHORITY, operationList);
        // } catch (Exception e) {
        // e.printStackTrace();
        // }
        // operationList.clear();
        // }
        // }
        //
        // events = server.getPastUserEvents(account.name);
        // for (Event event : events) {
        // lastfmEvents.add(Long.valueOf(event.getId()));
        //
        // if (localEvents.containsKey(Long.valueOf(event.getId()))) {
        // SyncEntry entry = localEvents.get(Long.valueOf(event.getId()));
        // operationList.add(updateEvent(calendar_id, account, event, entry.raw_id));
        // } else {
        // operationList.add(updateEvent(calendar_id, account, event, -1));
        // }
        //
        // if(operationList.size() >= 50) {
        // try {
        // mContentResolver.applyBatch(CalendarContract.AUTHORITY, operationList);
        // } catch (Exception e) {
        // e.printStackTrace();
        // }
        // operationList.clear();
        // }
        // }
        //
        // if(operationList.size() > 0) {
        // try {
        // mContentResolver.applyBatch(CalendarContract.AUTHORITY, operationList);
        // } catch (Exception e) {
        // e.printStackTrace();
        // }
        // }
        // } catch (IOException e1) {
        // // TODO Auto-generated catch block
        // e1.printStackTrace();
        // } catch (WSError e1) {
        // // TODO Auto-generated catch block
        // e1.printStackTrace();
        // }

        Iterator<Long> i = localEvents.keySet().iterator();
        while (i.hasNext()) {
            Long event = i.next();
            if (!lastfmEvents.contains(event))
                deleteEvent(context, account, localEvents.get(event).raw_id);
        }
    }
}
