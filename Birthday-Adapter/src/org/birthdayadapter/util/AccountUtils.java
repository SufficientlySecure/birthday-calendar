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

package org.birthdayadapter.util;

import org.birthdayadapter.R;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerFuture;
import android.app.AlarmManager;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;

public class AccountUtils {

    public static Bundle addAccount(Context context) {
        Log.d(Constants.TAG, "Adding account...");

        Account account = new Account(Constants.ACCOUNT_NAME, Constants.ACCOUNT_TYPE);

        // enable automatic sync once per day
        ContentResolver.setSyncAutomatically(account, Constants.CONTENT_AUTHORITY, true);
        ContentResolver.setIsSyncable(account, Constants.ACCOUNT_TYPE, 1);

        // add periodic sync interval once per day
        long freq = AlarmManager.INTERVAL_DAY;
        ContentResolver.addPeriodicSync(account, Constants.ACCOUNT_TYPE, new Bundle(), freq);

        AccountManager am = AccountManager.get(context);
        if (am.addAccountExplicitly(account, null, null)) {
            Bundle result = new Bundle();
            result.putString(AccountManager.KEY_ACCOUNT_NAME, account.name);
            result.putString(AccountManager.KEY_ACCOUNT_TYPE, account.type);
            return result;
        } else {
            return null;
        }
    }

    public static boolean removeAccount(Context context) {
        Log.d(Constants.TAG, "Removing account...");

        Account account = new Account(Constants.ACCOUNT_NAME, Constants.ACCOUNT_TYPE);
        AccountManager am = AccountManager.get(context);

        // remove account
        AccountManagerFuture<Boolean> future = am.removeAccount(account, null, null);
        if (future.isDone()) {
            try {
                future.getResult();

                return true;
            } catch (Exception e) {
                Log.e(Constants.TAG, "Problem while removing account: " + e.getMessage());
                e.printStackTrace();
                return false;
            }
        } else {
            return false;
        }
    }

    public static boolean isAccountActivated(Context context) {
        AccountManager am = AccountManager.get(context);

        Account[] availableAccounts = am.getAccountsByType(Constants.ACCOUNT_TYPE);
        for (Account currentAccount : availableAccounts) {
            if (currentAccount.name.equals(Constants.ACCOUNT_NAME)) {
                return true;
            }
        }

        return false;
    }

    public static class SyncTask extends AsyncTask<Void, Void, Void> {
        Context mContext;
        ProgressDialog mDialog;

        public SyncTask(Context context) {
            mContext = context;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();

            mDialog = ProgressDialog.show(mContext, "", mContext.getString(R.string.synchronizing),
                    true, false);
            mDialog.setCancelable(false);
        }

        @Override
        protected Void doInBackground(Void... unused) {

            // force sync now!
            Account account = new Account(Constants.ACCOUNT_NAME, Constants.ACCOUNT_TYPE);

            Bundle extras = new Bundle();
            // force resync!
            extras.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true);
            ContentResolver.requestSync(account, Constants.CONTENT_AUTHORITY, extras);

            // Wait while asynchronous android background operations finish
            try {
                Thread.sleep(5000);
            } catch (Exception e) {
                e.printStackTrace();
            }

            // return nothing as type is Void
            return null;
        }

        @Override
        protected void onPostExecute(Void unused) {
            super.onPostExecute(unused);

            mDialog.dismiss();
        }
    }

    abstract public static class CreateTask extends AsyncTask<String, Void, Boolean> {
        protected Context mContext;
        protected ProgressDialog mDialog;

        protected CreateTask(Context context) {
            mContext = context;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();

            mDialog = ProgressDialog.show(mContext, "", mContext.getString(R.string.creating),
                    true, false);
            mDialog.setCancelable(false);
        }

        @Override
        protected Boolean doInBackground(String... params) {

            // add account
            Bundle result = AccountUtils.addAccount(mContext);

            // Wait while asynchronous android background operations finish
            try {
                Thread.sleep(5000);
            } catch (Exception e) {
                e.printStackTrace();
            }

            if (result != null) {
                if (result.containsKey(AccountManager.KEY_ACCOUNT_NAME)) {
                    return true;
                } else {
                    return false;
                }
            } else {
                return false;
            }
        }

        // @Override
        // protected void onPostExecute(Boolean result) {
        // super.onPostExecute(result);
        //
        // mDialog.dismiss();
        // if (result) {
        // setStatusBasedOnAccount();
        // }
        // }
    }

    abstract public static class RemoveTask extends AsyncTask<String, Void, Boolean> {
        protected Context mContext;
        protected ProgressDialog mDialog;

        protected RemoveTask(Context context) {
            mContext = context;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();

            mDialog = ProgressDialog.show(mContext, "", mContext.getString(R.string.removing),
                    true, false);
            mDialog.setCancelable(false);
        }

        @Override
        protected Boolean doInBackground(String... params) {
            // remove account
            boolean result = AccountUtils.removeAccount(mContext);

            // Wait while asynchronous android background operations finish
            try {
                Thread.sleep(5000);
            } catch (Exception e) {
                e.printStackTrace();
            }

            return result;
        }

        // @Override
        // protected void onPostExecute(Boolean result) {
        // super.onPostExecute(result);
        //
        // mDialog.dismiss();
        // if (result) {
        // setStatusBasedOnAccount();
        // }
        // }
    }
}
