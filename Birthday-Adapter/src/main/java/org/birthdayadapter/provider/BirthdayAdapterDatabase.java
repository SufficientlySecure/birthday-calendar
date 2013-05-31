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

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.provider.BaseColumns;
import org.birthdayadapter.util.Constants;
import org.birthdayadapter.util.Log;

public class BirthdayAdapterDatabase extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "birthdayadapter.db";
    private static final int DATABASE_VERSION = 1;

    public interface Tables {
        String ACCOUNT_BLACKLIST = "account_blacklist";
    }

    private static final String CREATE_ACCOUNT_BLACKLIST = "CREATE TABLE IF NOT EXISTS "
            + Tables.ACCOUNT_BLACKLIST + "(" + BaseColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
            + BirthdayAdapterContract.AccountBlacklistColumns.ACCOUNT_NAME + " TEXT, "
            + BirthdayAdapterContract.AccountBlacklistColumns.ACCOUNT_TYPE + " TEXT)";

    BirthdayAdapterDatabase(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        Log.w(Constants.TAG, "Creating database...");

        db.execSQL(CREATE_ACCOUNT_BLACKLIST);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        Log.w(Constants.TAG, "Upgrading database from version " + oldVersion + " to " + newVersion);

//        if (oldVersion <= 1) {
//
//        } else {
        db.execSQL("DROP TABLE IF EXISTS " + Tables.ACCOUNT_BLACKLIST);
        onCreate(db);
//        }
    }
}