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
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;

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
     * Remove account from Android system and deletes the associated calendar.
     */
    public void removeAccount() {
        Log.d(Constants.TAG, "Removing account and calendar...");

        // First, delete the calendar
        CalendarHelper.deleteCalendar(mContext);

        // Then, remove the account
        AccountManager am = AccountManager.get(mContext);
        final Account account = new Account(Constants.ACCOUNT_NAME, mContext.getString(R.string.account_type));

        am.removeAccount(account, null, future -> {
            try {
                if (future.getResult().getBoolean(AccountManager.KEY_BOOLEAN_RESULT)) {
                    Log.i(Constants.TAG, "Account removed successfully.");
                    // Cancel any pending syncs
                    WorkManager.getInstance(mContext).cancelUniqueWork("birthday_sync");
                } else {
                    Log.e(Constants.TAG, "Failed to remove account.");
                }
            } catch (Exception e) {
                Log.e(Constants.TAG, "Error while removing account", e);
            }
        }, null);
    }

    /**
     * Force a manual sync now using WorkManager.
     */
    public void manualSync() {
        syncWithAction("manual_sync", BirthdayWorker.ACTION_SYNC, ExistingWorkPolicy.APPEND_OR_REPLACE);
    }

    /**
     * Enqueues a worker to update all reminders on existing events.
     */
    public void updateReminders() {
        Log.d(Constants.TAG, "Reminder settings changed, enqueuing worker with ACTION_REMINDERS_CHANGED.");
        syncWithAction("reminder_update", BirthdayWorker.ACTION_REMINDERS_CHANGED, ExistingWorkPolicy.APPEND_OR_REPLACE);
    }

    /**
     * Enqueues a one-time work request with a specific action and unique name.
     *
     * @param uniqueWorkName   A unique name for the work request.
     * @param action           The action to be performed by the worker.
     * @param existingWorkPolicy The policy to apply if work with that name already exists.
     */
    private void syncWithAction(String uniqueWorkName, String action, ExistingWorkPolicy existingWorkPolicy) {
        Log.d(Constants.TAG, "Enqueuing one-time work with name '" + uniqueWorkName + "' and action '" + action + "'");

        Data inputData = new Data.Builder()
                .putString(BirthdayWorker.ACTION, action)
                .build();

        OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(BirthdayWorker.class)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setInputData(inputData)
                .build();

        WorkManager.getInstance(mContext).enqueueUniqueWork(
                uniqueWorkName,
                existingWorkPolicy,
                workRequest);
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
