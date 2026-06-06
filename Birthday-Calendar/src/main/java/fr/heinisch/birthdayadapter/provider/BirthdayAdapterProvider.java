/*
 * Copyright (C) 2025-2026 Matthias Heinisch <birthdayadapter@heinisch.fr>
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

package fr.heinisch.birthdayadapter.provider;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteConstraintException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.provider.BaseColumns;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import fr.heinisch.birthdayadapter.util.Constants;
import fr.heinisch.birthdayadapter.util.Log;

public class BirthdayAdapterProvider extends ContentProvider {
    private static final UriMatcher sUriMatcher = buildUriMatcher();

    private static final int ACCOUNT_BLACKLIST = 100;
    private static final int ACCOUNT_BLACKLIST_ID = 101;
    private static final int CONTACT_MAPPING = 200;
    private static final int CONTACT_MAPPING_ID = 201;

    /**
     * Build and return a {@link android.content.UriMatcher} that catches all {@link android.net.Uri} variations supported by
     * this {@link android.content.ContentProvider}.
     */
    private static UriMatcher buildUriMatcher() {
        final UriMatcher matcher = new UriMatcher(UriMatcher.NO_MATCH);
        final String authority = BirthdayAdapterContract.CONTENT_AUTHORITY;

        matcher.addURI(authority, BirthdayAdapterContract.PATH_ACCOUNT_BLACKLIST, ACCOUNT_BLACKLIST);
        matcher.addURI(authority, BirthdayAdapterContract.PATH_ACCOUNT_BLACKLIST + "/#", ACCOUNT_BLACKLIST_ID);
        matcher.addURI(authority, BirthdayAdapterContract.PATH_CONTACT_MAPPING, CONTACT_MAPPING);
        matcher.addURI(authority, BirthdayAdapterContract.PATH_CONTACT_MAPPING + "/#", CONTACT_MAPPING_ID);

        return matcher;
    }

    private BirthdayAdapterDatabase mBirthdayAdapterDatabase;

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean onCreate() {
        final Context context = getContext();
        if (context != null) {
            mBirthdayAdapterDatabase = new BirthdayAdapterDatabase(context);
            return true;
        }
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getType(@NonNull Uri uri) {
        final int match = sUriMatcher.match(uri);
        switch (match) {
            case ACCOUNT_BLACKLIST:
                return BirthdayAdapterContract.AccountBlacklist.CONTENT_TYPE;
            case ACCOUNT_BLACKLIST_ID:
                return BirthdayAdapterContract.AccountBlacklist.CONTENT_ITEM_TYPE;
            case CONTACT_MAPPING:
                return BirthdayAdapterContract.ContactMapping.CONTENT_TYPE;
            case CONTACT_MAPPING_ID:
                return BirthdayAdapterContract.ContactMapping.CONTENT_ITEM_TYPE;
            default:
                throw new UnsupportedOperationException("Unknown uri: " + uri);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
        Log.d(Constants.TAG, "insert(uri=" + uri + ", values=" + values + ")");

        final SQLiteDatabase db = mBirthdayAdapterDatabase.getWritableDatabase();

        Uri rowUri = null;
        long rowId;
        try {
            final int match = sUriMatcher.match(uri);
            if (match == ACCOUNT_BLACKLIST) {
                rowId = db.insertOrThrow(BirthdayAdapterDatabase.TABLE_ACCOUNT_BLACKLIST, null, values);
                rowUri = BirthdayAdapterContract.AccountBlacklist.buildUri(Long.toString(rowId));
            } else if (match == CONTACT_MAPPING) {
                // Use insertWithOnConflict to handle UNIQUE constraint on lookup_key
                rowId = db.insertWithOnConflict(BirthdayAdapterDatabase.TABLE_CONTACT_MAPPING, null, values, SQLiteDatabase.CONFLICT_IGNORE);
                if (rowId == -1) {
                    // Entry already exists, query for its ID
                    String lookupKey = values.getAsString(BirthdayAdapterContract.ContactMappingColumns.LOOKUP_KEY);
                    try (Cursor cursor = db.query(BirthdayAdapterDatabase.TABLE_CONTACT_MAPPING,
                            new String[]{BaseColumns._ID},
                            BirthdayAdapterContract.ContactMappingColumns.LOOKUP_KEY + " = ?",
                            new String[]{lookupKey}, null, null, null)) {
                        if (cursor != null && cursor.moveToFirst()) {
                            rowId = cursor.getLong(0);
                        }
                    }
                }
                rowUri = BirthdayAdapterContract.ContactMapping.buildUri(Long.toString(rowId));
            } else {
                throw new UnsupportedOperationException("Unknown uri: " + uri);
            }
        } catch (SQLiteConstraintException e) {
            Log.e(Constants.TAG, "Constraint exception on insert! Entry already existing?");
        }

        // notify of changes in db
        final Context context = getContext();
        if (context != null && rowUri != null) {
            context.getContentResolver().notifyChange(uri, null);
        }

        return rowUri;
    }

    /**
     * {@inheritDoc}
     */
    @Nullable
    @Override
    public Cursor query(@NonNull Uri uri, @Nullable String[] projection, @Nullable String selection, @Nullable String[] selectionArgs,
                        @Nullable String sortOrder) {

        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        SQLiteDatabase db = mBirthdayAdapterDatabase.getReadableDatabase();

        final int match = sUriMatcher.match(uri);
        switch (match) {
            case ACCOUNT_BLACKLIST:
            case ACCOUNT_BLACKLIST_ID:
                qb.setTables(BirthdayAdapterDatabase.TABLE_ACCOUNT_BLACKLIST);
                if (match == ACCOUNT_BLACKLIST_ID) {
                    qb.appendWhere(BaseColumns._ID + "=" + uri.getLastPathSegment());
                }
                break;
            case CONTACT_MAPPING:
            case CONTACT_MAPPING_ID:
                qb.setTables(BirthdayAdapterDatabase.TABLE_CONTACT_MAPPING);
                if (match == CONTACT_MAPPING_ID) {
                    qb.appendWhere(BaseColumns._ID + "=" + uri.getLastPathSegment());
                }
                break;
            default:
                throw new UnsupportedOperationException("Unknown uri: " + uri);
        }

        Cursor cursor = qb.query(db, projection, selection, selectionArgs, null, null, sortOrder);

        // notify through cursor
        if (cursor != null) {
            final Context context = getContext();
            if (context != null) {
                cursor.setNotificationUri(context.getContentResolver(), uri);
            }
        }
        return cursor;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int update(@NonNull Uri uri, @Nullable ContentValues values, @Nullable String selection, @Nullable String[] selectionArgs) {
        Log.e(Constants.TAG, "Not supported");

        return -1;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int delete(@NonNull Uri uri, @Nullable String selection, @Nullable String[] selectionArgs) {
        Log.v(Constants.TAG, "delete(uri=" + uri + ")");

        final SQLiteDatabase db = mBirthdayAdapterDatabase.getWritableDatabase();

        int count;
        final int match = sUriMatcher.match(uri);
        switch (match) {
            case ACCOUNT_BLACKLIST:
                count = db.delete(BirthdayAdapterDatabase.TABLE_ACCOUNT_BLACKLIST, selection, selectionArgs);
                break;
            case ACCOUNT_BLACKLIST_ID:
                count = db.delete(BirthdayAdapterDatabase.TABLE_ACCOUNT_BLACKLIST, buildDefaultSelection(uri, selection),
                        selectionArgs);
                break;
            case CONTACT_MAPPING:
                count = db.delete(BirthdayAdapterDatabase.TABLE_CONTACT_MAPPING, selection, selectionArgs);
                break;
            case CONTACT_MAPPING_ID:
                count = db.delete(BirthdayAdapterDatabase.TABLE_CONTACT_MAPPING, buildDefaultSelection(uri, selection), selectionArgs);
                break;
            default:
                throw new UnsupportedOperationException("Unknown uri: " + uri);
        }

        // notify of changes in db
        final Context context = getContext();
        if (context != null) {
            context.getContentResolver().notifyChange(uri, null);
        }

        return count;
    }

    /**
     * Build default selection statement. If no extra selection is specified only build where clause
     * with rowId
     *
     * @param uri The uri to build the selection from.
     * @param selection An optional selection.
     * @return The selection string.
     */
    private String buildDefaultSelection(@NonNull Uri uri, @Nullable String selection) {
        String rowId = uri.getLastPathSegment();
        String where = "";
        if (!TextUtils.isEmpty(selection)) {
            where = " AND (" + selection + ")";
        }

        return BaseColumns._ID + "=" + rowId + where;
    }
}
