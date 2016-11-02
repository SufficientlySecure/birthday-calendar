/*
 * Copyright (C) 2013 Dominik Schürmann <dominik@dominikschuermann.de>
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

package org.birthdayadapter.provider;

import java.util.Arrays;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteConstraintException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.provider.BaseColumns;
import android.text.TextUtils;

import org.birthdayadapter.BuildConfig;
import org.birthdayadapter.util.Constants;
import org.birthdayadapter.util.Log;

public class BirthdayAdapterProvider extends ContentProvider {
    private static final UriMatcher sUriMatcher = buildUriMatcher();

    private static final int ACCOUNT_BLACKLIST = 100;
    private static final int ACCOUNT_BLACKLIST_ID = 101;

    /**
     * Build and return a {@link android.content.UriMatcher} that catches all {@link android.net.Uri} variations supported by
     * this {@link android.content.ContentProvider}.
     */
    private static UriMatcher buildUriMatcher() {
        final UriMatcher matcher = new UriMatcher(UriMatcher.NO_MATCH);
        final String authority = BirthdayAdapterContract.CONTENT_AUTHORITY;

        matcher.addURI(authority, BirthdayAdapterContract.PATH_ACCOUNT_BLACKLIST, ACCOUNT_BLACKLIST);
        matcher.addURI(authority, BirthdayAdapterContract.PATH_ACCOUNT_BLACKLIST + "/#", ACCOUNT_BLACKLIST_ID);

        return matcher;
    }

    private BirthdayAdapterDatabase mBirthdayAdapterDatabase;

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean onCreate() {
        final Context context = getContext();
        mBirthdayAdapterDatabase = new BirthdayAdapterDatabase(context);
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getType(Uri uri) {
        final int match = sUriMatcher.match(uri);
        switch (match) {
            case ACCOUNT_BLACKLIST:
                return BirthdayAdapterContract.AccountBlacklist.CONTENT_TYPE;
            case ACCOUNT_BLACKLIST_ID:
                return BirthdayAdapterContract.AccountBlacklist.CONTENT_ITEM_TYPE;
            default:
                throw new UnsupportedOperationException("Unknown uri: " + uri);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Uri insert(Uri uri, ContentValues values) {
        Log.d(Constants.TAG, "insert(uri=" + uri + ", values=" + values.toString() + ")");

        final SQLiteDatabase db = mBirthdayAdapterDatabase.getWritableDatabase();

        Uri rowUri = null;
        long rowId = -1;
        try {
            final int match = sUriMatcher.match(uri);
            switch (match) {
                case ACCOUNT_BLACKLIST:
                    rowId = db.insertOrThrow(BirthdayAdapterDatabase.Tables.ACCOUNT_BLACKLIST, null, values);
                    rowUri = BirthdayAdapterContract.AccountBlacklist.buildUri(Long.toString(rowId));
                    break;
                default:
                    throw new UnsupportedOperationException("Unknown uri: " + uri);
            }
        } catch (SQLiteConstraintException e) {
            Log.e(Constants.TAG, "Constraint exception on insert! Entry already existing?");
        }

        // notify of changes in db
        getContext().getContentResolver().notifyChange(uri, null);

        return rowUri;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
                        String sortOrder) {
        Log.v(Constants.TAG, "query(uri=" + uri + ", proj=" + Arrays.toString(projection) + ")");

        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        SQLiteDatabase db = mBirthdayAdapterDatabase.getReadableDatabase();

        final int match = sUriMatcher.match(uri);
        switch (match) {
            case ACCOUNT_BLACKLIST:
                qb.setTables(BirthdayAdapterDatabase.Tables.ACCOUNT_BLACKLIST);
                break;
            default:
                throw new UnsupportedOperationException("Unknown uri: " + uri);
        }

        Cursor cursor = qb.query(db, projection, selection, selectionArgs, null, null, sortOrder);

        if (BuildConfig.DEBUG)
            DatabaseUtils.dumpCursor(cursor);
        // notify through cursor
        cursor.setNotificationUri(getContext().getContentResolver(), uri);
        return cursor;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        Log.e(Constants.TAG, "Not supported");

        return -1;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        Log.v(Constants.TAG, "delete(uri=" + uri + ")");

        final SQLiteDatabase db = mBirthdayAdapterDatabase.getWritableDatabase();

        int count;
        final int match = sUriMatcher.match(uri);
        switch (match) {
            case ACCOUNT_BLACKLIST:
                count = db.delete(BirthdayAdapterDatabase.Tables.ACCOUNT_BLACKLIST, selection, selectionArgs);
                break;
            case ACCOUNT_BLACKLIST_ID:
                count = db.delete(BirthdayAdapterDatabase.Tables.ACCOUNT_BLACKLIST, buildDefaultSelection(uri, selection),
                        selectionArgs);
                break;
            default:
                throw new UnsupportedOperationException("Unknown uri: " + uri);
        }

        // notify of changes in db
        getContext().getContentResolver().notifyChange(uri, null);

        return count;
    }

    /**
     * Build default selection statement. If no extra selection is specified only build where clause
     * with rowId
     *
     * @param uri
     * @param selection
     * @return
     */
    private String buildDefaultSelection(Uri uri, String selection) {
        String rowId = uri.getPathSegments().get(1);
        String where = "";
        if (!TextUtils.isEmpty(selection)) {
            where = " AND (" + selection + ")";
        }

        return BaseColumns._ID + "=" + rowId + where;
    }
}