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

import java.text.Collator;
import java.util.*;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AuthenticatorDescription;
import android.annotation.SuppressLint;
import android.content.AsyncTaskLoader;
import android.content.Context;
import android.database.Cursor;
import android.provider.ContactsContract;
import org.birthdayadapter.provider.ProviderHelper;

/**
 * A custom Loader that loads all active accounts that provide contacts.
 */
@SuppressLint("NewApi")
public class AccountListLoader extends AsyncTaskLoader<List<AccountListEntry>> {

    List<AccountListEntry> mAccounts;

    public AccountListLoader(Context context) {
        super(context);
    }

    /**
     * Perform alphabetical comparison of account entry objects.
     */
    public static final Comparator<AccountListEntry> ALPHA_COMPARATOR = new Comparator<AccountListEntry>() {
        private final Collator sCollator = Collator.getInstance();

        @Override
        public int compare(AccountListEntry object1, AccountListEntry object2) {
            return sCollator.compare(object1.getLabel(), object2.getLabel());
        }
    };

    /**
     * This is where the bulk of our work is done. This function is called in a background thread
     * and should generate a new set of data to be published by the loader.
     */
    @Override
    public List<AccountListEntry> loadInBackground() {
        // Retrieve all accounts that are actively used for contacts
        HashSet<Account> activeContactAccounts = new HashSet<Account>();
        Cursor cursor = null;
        try {
            cursor = getContext().getContentResolver().query(
                    ContactsContract.RawContacts.CONTENT_URI,
                    new String[]{ContactsContract.RawContacts.ACCOUNT_NAME,
                            ContactsContract.RawContacts.ACCOUNT_TYPE}, null, null, null);

            if (cursor != null && cursor.getCount() > 0) {
                while (cursor.moveToNext()) {
                    Account account = new Account(cursor.getString(cursor
                            .getColumnIndex(ContactsContract.RawContacts.ACCOUNT_NAME)),
                            cursor.getString(cursor
                                    .getColumnIndex(ContactsContract.RawContacts.ACCOUNT_TYPE)));
                    activeContactAccounts.add(account);
                }
                cursor.close();
            }
        } catch (Exception e) {
            Log.e(Constants.TAG, "Error retrieving accounts!", e);
        } finally {
            cursor.close();
        }

        // get current blacklist from preferences
        HashSet<Account> accountBlacklist = ProviderHelper.getAccountBlacklist(getContext());

        Log.d(Constants.TAG, "accountBlacklist" + accountBlacklist);
        for (Account account : accountBlacklist) {
            Log.d(Constants.TAG, "accountBlacklist acc type: " + account.type + ", name: " + account.name);
        }

        // Build List<AccountListEntry> by getting AuthenticatorDescription for every Account
        AccountManager manager = AccountManager.get(getContext());
        AuthenticatorDescription[] descriptions = manager.getAuthenticatorTypes();

        ArrayList<AccountListEntry> entries = new ArrayList<AccountListEntry>();
        for (Account account : activeContactAccounts) {
            for (AuthenticatorDescription description : descriptions) {
                if (description.type.equals(account.type)) {
                    // add to entries, disable entry if in blacklist
                    boolean enabled = !accountBlacklist.contains(account);
                    entries.add(new AccountListEntry(getContext(), account, description, enabled));
                }
            }
        }

        // Sort the list.
        Collections.sort(entries, ALPHA_COMPARATOR);

        // Done!
        return entries;
    }

    /**
     * Called when there is new data to deliver to the client. The super class will take care of
     * delivering it; the implementation here just adds a little more logic.
     */
    @Override
    public void deliverResult(List<AccountListEntry> accounts) {
        if (isReset()) {
            // An async query came in while the loader is stopped. We
            // don't need the result.
            if (accounts != null) {
                onReleaseResources(accounts);
            }
        }
        List<AccountListEntry> oldAccounts = accounts;
        mAccounts = accounts;

        if (isStarted()) {
            // If the Loader is currently started, we can immediately
            // deliver its results.
            super.deliverResult(accounts);
        }

        // At this point we can release the resources associated with
        // 'oldAccounts' if needed; now that the new result is delivered we
        // know that it is no longer in use.
        if (oldAccounts != null) {
            onReleaseResources(oldAccounts);
        }
    }

    /**
     * Handles a request to start the Loader.
     */
    @Override
    protected void onStartLoading() {
        if (mAccounts != null) {
            // If we currently have a result available, deliver it
            // immediately.
            deliverResult(mAccounts);
        }

        if (takeContentChanged() || mAccounts == null) {
            // If the data has changed since the last time it was loaded
            // or is not currently available, start a load.
            forceLoad();
        }
    }

    /**
     * Handles a request to stop the Loader.
     */
    @Override
    protected void onStopLoading() {
        // Attempt to cancel the current load task if possible.
        cancelLoad();
    }

    /**
     * Handles a request to cancel a load.
     */
    @Override
    public void onCanceled(List<AccountListEntry> apps) {
        super.onCanceled(apps);

        // At this point we can release the resources associated with 'apps'
        // if needed.
        onReleaseResources(apps);
    }

    /**
     * Handles a request to completely reset the Loader.
     */
    @Override
    protected void onReset() {
        super.onReset();

        // Ensure the loader is stopped
        onStopLoading();

        // At this point we can release the resources associated with 'apps'
        // if needed.
        if (mAccounts != null) {
            onReleaseResources(mAccounts);
            mAccounts = null;
        }
    }

    /**
     * Helper function to take care of releasing resources associated with an actively loaded data
     * set.
     */
    protected void onReleaseResources(List<AccountListEntry> accounts) {
        // For a simple List<> there is nothing to do. For something
        // like a Cursor, we would close it here.
    }
}