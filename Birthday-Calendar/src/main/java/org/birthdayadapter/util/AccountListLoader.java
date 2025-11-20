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
import androidx.loader.content.AsyncTaskLoader;

import org.birthdayadapter.R;
import org.birthdayadapter.provider.ProviderHelper;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
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
     * Perform alphabetical comparison of account entry objects.
     */
    public static final Comparator<AccountListEntry> ALPHA_COMPARATOR = new Comparator<AccountListEntry>() {
        private final Collator sCollator = Collator.getInstance();

        @Override
        public int compare(AccountListEntry object1, AccountListEntry object2) {
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
                    entries.add(new AccountListEntry(getContext(), account, description, enabled));
                    break; // Found descriptor, no need to look further
                }
            }
        }

        // Sort the list.
        Collections.sort(entries, ALPHA_COMPARATOR);

        // Done!
        return entries;
    }

    @Override
    public void deliverResult(List<AccountListEntry> accounts) {
        if (isReset()) {
            if (accounts != null) {
                onReleaseResources(accounts);
            }
        }
        mAccounts = accounts;

        if (isStarted()) {
            super.deliverResult(accounts);
        }

        if (accounts != null) {
            onReleaseResources(accounts);
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
        onReleaseResources(apps);
    }

    @Override
    protected void onReset() {
        super.onReset();
        onStopLoading();

        if (mAccounts != null) {
            onReleaseResources(mAccounts);
            mAccounts = null;
        }
    }

    protected void onReleaseResources(List<AccountListEntry> accounts) {
    }
}
