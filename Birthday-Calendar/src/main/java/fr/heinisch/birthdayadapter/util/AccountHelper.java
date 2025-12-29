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

package fr.heinisch.birthdayadapter.util;

import static fr.heinisch.birthdayadapter.util.VersionHelper.isFullVersionUnlocked;

import android.Manifest;
import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;

import androidx.core.app.ActivityCompat;
import androidx.work.Data;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.OutOfQuotaPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

import fr.heinisch.birthdayadapter.R;
import fr.heinisch.birthdayadapter.service.BirthdayWorker;

public class AccountHelper {
    private final Context mContext;

    public AccountHelper(Context context) {
        mContext = context;
    }

    /**
     * Ensures the account exists, schedules the periodic sync, and triggers an immediate sync.
     */
    public Bundle addAccountAndSync() {
        Bundle result = null;

        // Clear any old events before creating a new account and calendar
        CalendarHelper.clearAllEvents(mContext);

        if (!isAccountActivated()) {
            Log.d(Constants.TAG, "Account does not exist. Adding account...");

            AccountManager am = AccountManager.get(mContext);
            final Account account = new Account(Constants.getAccountName(mContext), mContext.getString(R.string.account_type));

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

        // Trigger a sync
        differentialSync();

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
        final Account account = new Account(Constants.getAccountName(mContext), mContext.getString(R.string.account_type));

        am.removeAccount(account, null, future -> {
            try {
                if (future.getResult().getBoolean(AccountManager.KEY_BOOLEAN_RESULT)) {
                    Log.i(Constants.TAG, "Account removed successfully.");
                    // Cancel any pending syncs
                    WorkManager.getInstance(mContext).cancelUniqueWork("periodic_sync");
                } else {
                    Log.e(Constants.TAG, "Failed to remove account.");
                }
            } catch (Exception e) {
                Log.e(Constants.TAG, "Error while removing account", e);
            }
        }, null);
    }

    /**
     * Force a manual sync now using WorkManager and reschedule the periodic sync for the full version.
     */
    public void differentialSync() {
        Log.i(Constants.TAG, "Differential sync triggered.");
        // (Re)schedule a periodic sync upon a manual sync
        if (isFullVersionUnlocked(mContext)) {
            Log.d(Constants.TAG, "Enqueuing periodic sync with UPDATE policy.");
            PeriodicWorkRequest periodicSyncRequest = new PeriodicWorkRequest.Builder(BirthdayWorker.class, Constants.SYNC_INTERVAL_DAYS, TimeUnit.DAYS).build();
            WorkManager.getInstance(mContext).enqueueUniquePeriodicWork("periodic_sync", ExistingPeriodicWorkPolicy.UPDATE, periodicSyncRequest);
        }

        // Trigger the immediate sync
        syncWithAction("differential_sync", BirthdayWorker.ACTION_SYNC);
    }

    /**
     * Enqueues a worker to perform a full resync, which involves deleting the calendar and all events.
     */
    public void triggerFullResync() {
        Log.i(Constants.TAG, "Full resync triggered.");
        syncWithAction("full_resync", BirthdayWorker.ACTION_FORCE_RESYNC);
    }

    /**
     * Enqueues a one-time work request with a specific action and unique name.
     *
     * @param uniqueWorkName   A unique name for the work request.
     * @param action           The action to be performed by the worker.
     */
    private void syncWithAction(String uniqueWorkName, String action) {
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
                ExistingWorkPolicy.REPLACE,
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
            if (currentAccount.name.equals(Constants.getAccountName(mContext))) {
                return true;
            }
        }

        return false;
    }
}
