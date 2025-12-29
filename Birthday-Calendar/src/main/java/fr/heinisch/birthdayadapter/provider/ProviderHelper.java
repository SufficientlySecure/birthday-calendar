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

package fr.heinisch.birthdayadapter.provider;

import android.accounts.Account;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

public class ProviderHelper {

    public static void setAccountBlacklist(Context context, HashMap<Account, HashSet<String>> blacklist) {
        // clear table
        context.getContentResolver().delete(BirthdayAdapterContract.AccountBlacklist.CONTENT_URI, null, null);

        ArrayList<ContentValues> valuesList = new ArrayList<>();

        for (Map.Entry<Account, HashSet<String>> entry : blacklist.entrySet()) {
            Account acc = entry.getKey();
            HashSet<String> groups = entry.getValue();

            boolean accountIsBlacklisted = groups.contains(null);
            if (accountIsBlacklisted) {
                // account is blacklisted without specific groups
                ContentValues values = new ContentValues();
                values.put(BirthdayAdapterContract.AccountBlacklist.ACCOUNT_NAME, acc.name);
                values.put(BirthdayAdapterContract.AccountBlacklist.ACCOUNT_TYPE, acc.type);
                values.putNull(BirthdayAdapterContract.AccountBlacklist.ACCOUNT_GROUP);
                valuesList.add(values);
            }

            // Always save blacklisted groups, even if the whole account is blacklisted
            for (String group : groups) {
                if (group != null) {
                    ContentValues values = new ContentValues();
                    values.put(BirthdayAdapterContract.AccountBlacklist.ACCOUNT_NAME, acc.name);
                    values.put(BirthdayAdapterContract.AccountBlacklist.ACCOUNT_TYPE, acc.type);
                    values.put(BirthdayAdapterContract.AccountBlacklist.ACCOUNT_GROUP, group);
                    valuesList.add(values);
                }
            }
        }

        ContentValues[] values = new ContentValues[valuesList.size()];
        valuesList.toArray(values);

        // insert as bulk operation
        context.getContentResolver().bulkInsert(BirthdayAdapterContract.AccountBlacklist.CONTENT_URI, values);
    }

    public static HashMap<Account, HashSet<String>> getAccountBlacklist(Context context) {
        HashMap<Account, HashSet<String>> hashMap = new HashMap<>();
        Cursor cursor = getAccountBlacklistCursor(context);

        try {
            while (cursor != null && cursor.moveToNext()) {
                Account acc = new Account(
                        cursor.getString(cursor.getColumnIndexOrThrow(BirthdayAdapterContract.AccountBlacklist.ACCOUNT_NAME)),
                        cursor.getString(cursor.getColumnIndexOrThrow(BirthdayAdapterContract.AccountBlacklist.ACCOUNT_TYPE)));
                String group = cursor.getString(cursor.getColumnIndexOrThrow(BirthdayAdapterContract.AccountBlacklist.ACCOUNT_GROUP));

                HashSet<String> groups = hashMap.computeIfAbsent(acc, k -> new HashSet<>());

                groups.add(group);
            }
        } finally {
            if (cursor != null && !cursor.isClosed())
                cursor.close();
        }

        return hashMap;
    }

    private static Cursor getAccountBlacklistCursor(Context context) {
        return context.getContentResolver().query(
                BirthdayAdapterContract.AccountBlacklist.CONTENT_URI,
                new String[]{BirthdayAdapterContract.AccountBlacklist._ID,
                        BirthdayAdapterContract.AccountBlacklist.ACCOUNT_NAME,
                        BirthdayAdapterContract.AccountBlacklist.ACCOUNT_TYPE,
                        BirthdayAdapterContract.AccountBlacklist.ACCOUNT_GROUP},
                null,
                null,
                BirthdayAdapterContract.AccountBlacklist.DEFAULT_SORT);
    }

}
