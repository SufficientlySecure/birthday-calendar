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

import org.birthdayadapter.R;
import org.birthdayadapter.service.MainIntentService;

import android.Manifest;
import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerFuture;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Messenger;
import androidx.core.app.ActivityCompat;

import java.util.concurrent.TimeUnit;

public class AccountHelper {
    private Context mContext;
    private Handler mBackgroundStatusHandler;

    public AccountHelper(Context context, Handler handler) {
        mContext = context;
        mBackgroundStatusHandler = handler;
    }

    public AccountHelper(Context context) {
        mContext = context;
    }

    /**
     * Add account for Birthday Adapter to Android system
     */
    public Bundle addAccountAndSync() {
        Log.d(Constants.TAG, "Adding account...");

        AccountManager am = AccountManager.get(mContext);
        final Account account = new Account(Constants.ACCOUNT_NAME, mContext.getString(R.string.account_type));

        if (am.addAccountExplicitly(account, null, null)) {
            // Set the sync adapter to be syncable
            ContentResolver.setIsSyncable(account, Constants.CONTENT_AUTHORITY, 1);
            // Enable automatic sync
            ContentResolver.setSyncAutomatically(account, Constants.CONTENT_AUTHORITY, true);
            // Add a periodic sync every 24 hours
            ContentResolver.addPeriodicSync(account, Constants.CONTENT_AUTHORITY, Bundle.EMPTY, TimeUnit.HOURS.toSeconds(24));

            Bundle result = new Bundle();
            result.putString(AccountManager.KEY_ACCOUNT_NAME, account.name);
            result.putString(AccountManager.KEY_ACCOUNT_TYPE, account.type);

            // Force a sync! Even when background sync is disabled, this will force one sync!
            manualSync();

            return result;
        } else {
            Log.e(Constants.TAG, "Failed to add account explicitly.");
            return null;
        }
    }

    /**
     * Remove account from Android system
     */
    public boolean removeAccount() {
        Log.d(Constants.TAG, "Removing account...");

        AccountManager am = AccountManager.get(mContext);
        final Account account = new Account(Constants.ACCOUNT_NAME, mContext.getString(R.string.account_type));

        // remove account
        AccountManagerFuture<Boolean> future = am.removeAccount(account, null, null);
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

        // Disabled: Force resync in Android OS
        // Bundle extras = new Bundle();
        // extras.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true);
        // final Account account = new Account(Constants.ACCOUNT_NAME, mContext.getString(R.string.account_type));
        // ContentResolver.requestSync(account, Constants.CONTENT_AUTHORITY, extras);

        // Enabled: Force resync in own thread:
        // Send all information needed to service to do in other thread
        Intent intent = new Intent(mContext, MainIntentService.class);

        // Create a new Messenger for the communication back
        if (mBackgroundStatusHandler != null) {
            Messenger messenger = new Messenger(mBackgroundStatusHandler);
            intent.putExtra(MainIntentService.EXTRA_MESSENGER, messenger);
        }
        intent.setAction(MainIntentService.ACTION_MANUAL_COMPLETE_SYNC);

        // start service with intent
        mContext.startService(intent);
    }

    /**
     * Checks whether the account is enabled or not
     */
    public boolean isAccountActivated() {
        AccountManager am = AccountManager.get(mContext);

        if (ActivityCompat.checkSelfPermission(mContext,
                Manifest.permission.GET_ACCOUNTS) != PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        Account[] availableAccounts = am.getAccountsByType(mContext.getString(R.string.account_type));
        for (Account currentAccount : availableAccounts) {
            if (currentAccount.name.equals(Constants.ACCOUNT_NAME)) {
                return true;
            }
        }

        return false;
    }
}
