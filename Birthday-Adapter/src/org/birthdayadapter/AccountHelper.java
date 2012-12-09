/*
 * Copyright (C) 2012 Dominik Schürmann <dominik@dominikschuermann.de>
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

package org.birthdayadapter;

import org.birthdayadapter.util.Constants;
import org.birthdayadapter.util.Log;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerFuture;
import android.app.Activity;
import android.app.AlarmManager;
import android.content.ContentResolver;
import android.os.Bundle;

public class AccountHelper {
    Activity mActivity;
    Object mSyncObserveHandle;

    public AccountHelper(Activity activity) {
        mActivity = activity;
    }

    /**
     * Add account for Birthday Adapter to Android system
     * 
     * @param context
     * @return
     */
    public Bundle addAccount() {
        Log.d(Constants.TAG, "Adding account...");

        // enable automatic sync once per day
        ContentResolver.setSyncAutomatically(Constants.ACCOUNT, Constants.CONTENT_AUTHORITY, true);
        ContentResolver.setIsSyncable(Constants.ACCOUNT, Constants.ACCOUNT_TYPE, 1);

        // add periodic sync interval once per day
        long freq = AlarmManager.INTERVAL_DAY;
        ContentResolver.addPeriodicSync(Constants.ACCOUNT, Constants.ACCOUNT_TYPE, new Bundle(),
                freq);

        AccountManager am = AccountManager.get(mActivity);
        if (am.addAccountExplicitly(Constants.ACCOUNT, null, null)) {
            Bundle result = new Bundle();
            result.putString(AccountManager.KEY_ACCOUNT_NAME, Constants.ACCOUNT.name);
            result.putString(AccountManager.KEY_ACCOUNT_TYPE, Constants.ACCOUNT.type);
            return result;
        } else {
            return null;
        }
    }

    /**
     * Adds account and forces manual sync afterwards if adding was successful
     */
    public void addAccountAndSync() {
        Bundle result = addAccount();

        if (result != null) {
            if (result.containsKey(AccountManager.KEY_ACCOUNT_NAME)) {
                // Force a sync! Even when background sync is disabled, this will force one sync!
                manualSync();
            } else {
                Log.e(Constants.TAG,
                        "Account was not added! result did not contain KEY_ACCOUNT_NAME!");
            }
        } else {
            Log.e(Constants.TAG, "Account was not added! result was null!");
        }
    }

    /**
     * Remove account from Android system
     * 
     * @param context
     * @return
     */
    public boolean removeAccount() {
        Log.d(Constants.TAG, "Removing account...");

        AccountManager am = AccountManager.get(mActivity);

        // remove account
        AccountManagerFuture<Boolean> future = am.removeAccount(Constants.ACCOUNT, null, null);
        if (future.isDone()) {
            try {
                future.getResult();

                return true;
            } catch (Exception e) {
                Log.e(Constants.TAG, "Problem while removing account!", e);
                return false;
            }
        } else {
            return false;
        }
    }

    /**
     * Force a manual sync now!
     */
    public void manualSync() {
        Log.d(Constants.TAG, "Force manual sync...");

        // force resync!
        Bundle extras = new Bundle();
        extras.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true);
        ContentResolver.requestSync(Constants.ACCOUNT, Constants.CONTENT_AUTHORITY, extras);
    }

    /**
     * Checks whether the account is enabled or not
     * 
     * @param context
     * @return
     */
    public boolean isAccountActivated() {
        AccountManager am = AccountManager.get(mActivity);

        Account[] availableAccounts = am.getAccountsByType(Constants.ACCOUNT_TYPE);
        for (Account currentAccount : availableAccounts) {
            if (currentAccount.name.equals(Constants.ACCOUNT_NAME)) {
                return true;
            }
        }

        return false;
    }
}
