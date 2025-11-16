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

import android.Manifest;
import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerFuture;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;

import androidx.core.app.ActivityCompat;
import androidx.work.Data;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.OutOfQuotaPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import org.birthdayadapter.R;
import org.birthdayadapter.service.BirthdayWorker;

import java.util.concurrent.TimeUnit;

public class AccountHelper {
    private Context mContext;

    public AccountHelper(Context context, Handler handler) {
        mContext = context;
    }

    public AccountHelper(Context context) {
        mContext = context;
    }

    /**
     * Ensures the account exists, schedules the periodic sync, and triggers an immediate sync.
     */
    public Bundle addAccountAndSync() {
        Bundle result = null;

        if (!isAccountActivated()) {
            Log.d(Constants.TAG, "Account does not exist. Adding account...");

            AccountManager am = AccountManager.get(mContext);
            final Account account = new Account(Constants.ACCOUNT_NAME, mContext.getString(R.string.account_type));

            if (am.addAccountExplicitly(account, null, null)) {
                result = new Bundle();
                result.putString(AccountManager.KEY_ACCOUNT_NAME, account.name);
                result.putString(AccountManager.KEY_ACCOUNT_TYPE, account.type);
            } else {
                Log.e(Constants.TAG, "Failed to add account explicitly.");
                return null; // Return early if account creation failed
            }
        } else {
            Log.d(Constants.TAG, "Account already exists.");
        }

        // Ensure the periodic sync is always scheduled if the account is active.
        // Using UPDATE ensures that if the work already exists, it's updated if needed,
        // and if it doesn't exist, it's created.
        Log.d(Constants.TAG, "Enqueuing periodic sync with UPDATE policy.");
        PeriodicWorkRequest periodicSyncRequest = new PeriodicWorkRequest.Builder(BirthdayWorker.class, Constants.SYNC_INTERVAL_HOURS, TimeUnit.HOURS)
                .build();
        WorkManager.getInstance(mContext).enqueueUniquePeriodicWork("birthday_sync", ExistingPeriodicWorkPolicy.UPDATE, periodicSyncRequest);

        // Force a first/manual sync now.
        manualSync();

        return result;
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

                // Cancel the periodic sync
                WorkManager.getInstance(mContext).cancelUniqueWork("birthday_sync");

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
     * Force a manual sync now using WorkManager.
     * This sync is enqueued as unique work with a REPLACE policy, ensuring
     * that only one manual sync is pending or running at a time. The last one wins.
     */
    public void manualSync() {
        Log.d(Constants.TAG, "Forcing manual sync using WorkManager...");

        // Define the action for the worker to perform a sync
        Data inputData = new Data.Builder()
                .putString(BirthdayWorker.ACTION, BirthdayWorker.ACTION_SYNC)
                .build();

        // Create a one-time work request for the BirthdayWorker
        OneTimeWorkRequest manualSyncRequest = new OneTimeWorkRequest.Builder(BirthdayWorker.class)
                // Mark it as "expedited" so the system attempts to run it as soon as possible.
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setInputData(inputData)
                .build();

        // Enqueue the request as unique work, replacing any existing pending manual sync.
        // This prevents multiple syncs from queuing up if the user taps the button repeatedly.
        WorkManager.getInstance(mContext).enqueueUniqueWork(
                "manual_sync",
                ExistingWorkPolicy.REPLACE,
                manualSyncRequest);
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
