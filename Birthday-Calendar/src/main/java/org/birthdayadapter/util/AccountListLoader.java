/*
 * Copyright (C) 2012-2013 Dominik Schürmann <dominik@dominikschuermann.de>
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

package org.birthdayadapter.util;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AuthenticatorDescription;
import android.content.Context;
import android.database.Cursor;
import android.provider.ContactsContract;
import androidx.loader.content.AsyncTaskLoader;

import org.birthdayadapter.provider.ProviderHelper;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

/**
 * A custom Loader that loads all active accounts.
 */
public class AccountListLoader extends AsyncTaskLoader<List<AccountListEntry>> {

    private List<AccountListEntry> mAccounts;

    public AccountListLoader(Context context) {
        super(context);
    }

    /**
     * Perform comparison of account entry objects, sorting by the sum of contacts and dates.
     */
    public static final Comparator<AccountListEntry> TOTAL_COUNT_COMPARATOR = new Comparator<>() {
        private final Collator sCollator = Collator.getInstance();

        @Override
        public int compare(AccountListEntry object1, AccountListEntry object2) {
            int total1 = object1.getContactCount() + object1.getDateCount();
            int total2 = object2.getContactCount() + object2.getDateCount();

            // Sort by total count descending
            int countCompare = Integer.compare(total2, total1);
            if (countCompare != 0) {
                return countCompare;
            }
            // If counts are equal, sort by label ascending
            return sCollator.compare(object1.getLabel(), object2.getLabel());
        }
    };

    @Override
    public List<AccountListEntry> loadInBackground() {
        AccountManager manager = AccountManager.get(getContext());
        AuthenticatorDescription[] descriptions = manager.getAuthenticatorTypes();
        Account[] accounts = manager.getAccounts();
        ArrayList<AccountListEntry> entries = new ArrayList<>();

        HashSet<Account> accountBlacklist = ProviderHelper.getAccountBlacklist(getContext());

        for (Account account : accounts) {
            // Don't show the app's own account in the filter list
            if (account.type.startsWith("org.birthdayadapter")) {
                continue;
            }

            for (AuthenticatorDescription description : descriptions) {
                if (description.type.equals(account.type)) {
                    boolean enabled = !accountBlacklist.contains(account);
                    AccountListEntry entry = new AccountListEntry(getContext(), account, description, enabled);
                    int[] counts = getAccountStats(account);
                    entry.setContactCount(counts[0]);
                    entry.setDateCount(counts[1]);
                    entries.add(entry);
                    break; // Found descriptor, no need to look further
                }
            }
        }

        // Sort the list.
        entries.sort(TOTAL_COUNT_COMPARATOR);

        // Done!
        return entries;
    }

    private int[] getAccountStats(Account account) {
        // Query 1: Get total number of raw contacts for the account
        int totalContacts = 0;
        final String[] rawContactsProjection = {ContactsContract.RawContacts._ID};
        final String rawContactsSelection = ContactsContract.RawContacts.ACCOUNT_TYPE + " = ? AND " + ContactsContract.RawContacts.ACCOUNT_NAME + " = ?";
        final String[] rawContactsSelectionArgs = {account.type, account.name};

        try (Cursor rawContactsCursor = getContext().getContentResolver().query(
                ContactsContract.RawContacts.CONTENT_URI,
                rawContactsProjection,
                rawContactsSelection,
                rawContactsSelectionArgs,
                null
        )) {
            if (rawContactsCursor != null) {
                totalContacts = rawContactsCursor.getCount();
            }
        }

        // Query 2: Get total number of dates (events) for the account
        int totalDates = 0;
        final String[] eventsProjection = {ContactsContract.Data._ID}; // Just need to count
        final String eventsSelection = ContactsContract.Data.MIMETYPE + " = ? AND " +
                ContactsContract.RawContacts.ACCOUNT_TYPE + " = ? AND " +
                ContactsContract.RawContacts.ACCOUNT_NAME + " = ?";
        final String[] eventsSelectionArgs = {
                ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE,
                account.type,
                account.name
        };

        try (Cursor eventsCursor = getContext().getContentResolver().query(ContactsContract.Data.CONTENT_URI, eventsProjection, eventsSelection, eventsSelectionArgs, null)) {
            if (eventsCursor != null) {
                totalDates = eventsCursor.getCount();
            }
        }

        return new int[]{totalContacts, totalDates};
    }


    @Override
    public void deliverResult(List<AccountListEntry> accounts) {
        if (isReset()) {
            if (accounts != null) {
                onReleaseResources();
            }
        }
        mAccounts = accounts;

        if (isStarted()) {
            super.deliverResult(accounts);
        }

        if (accounts != null) {
            onReleaseResources();
        }
    }

    @Override
    protected void onStartLoading() {
        if (mAccounts != null) {
            deliverResult(mAccounts);
        }

        if (takeContentChanged() || mAccounts == null) {
            forceLoad();
        }
    }

    @Override
    protected void onStopLoading() {
        cancelLoad();
    }

    @Override
    public void onCanceled(List<AccountListEntry> apps) {
        super.onCanceled(apps);
        onReleaseResources();
    }

    @Override
    protected void onReset() {
        super.onReset();
        onStopLoading();

        if (mAccounts != null) {
            onReleaseResources();
            mAccounts = null;
        }
    }

    protected void onReleaseResources() {
    }
}
