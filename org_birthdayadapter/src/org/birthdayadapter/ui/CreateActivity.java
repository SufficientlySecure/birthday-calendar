/*
 * Copyright (C) 2010 Sam Steele
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

package org.birthdayadapter.ui;

import org.birthdayadapter.R;
import org.birthdayadapter.util.Constants;

import android.accounts.Account;
import android.accounts.AccountAuthenticatorActivity;
import android.accounts.AccountManager;
import android.accounts.AccountManagerFuture;
import android.app.AlarmManager;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;

public class CreateActivity extends AccountAuthenticatorActivity {
    // Button mCreateButton;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // setContentView(R.layout.main);

        // mCreateButton = (Button) findViewById(R.id.main_create);
        // mCreateButton.setOnClickListener(new OnClickListener() {
        //
        // public void onClick(View v) {
        //
        // CreateTask t = new CreateTask(CreateActivity.this);
        // t.execute();
        // }
        //
        // });

        // create directly, there are no options
        CreateTask t = new CreateTask(this);
        t.execute();
    }

    public static Bundle addAccount(Context context) {
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
        Account account = new Account(Constants.ACCOUNT_NAME, Constants.ACCOUNT_TYPE);
        AccountManager am = AccountManager.get(context);

        // remove account
        AccountManagerFuture<Boolean> future = am.removeAccount(account, null, null);
        if (future.isDone()) {
            try {
                boolean result = future.getResult();

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

    public class CreateTask extends AsyncTask<String, Void, Boolean> {
        Context mContext;
        ProgressDialog mDialog;

        CreateTask(Context c) {
            mContext = c;

            mDialog = ProgressDialog
                    .show(c, "", mContext.getString(R.string.creating), true, false);
            mDialog.setCancelable(false);
        }

        @Override
        public Boolean doInBackground(String... params) {

            // Wait while asynchronous android background operations finish
            try {
                Thread.sleep(5000);
            } catch (Exception e) {
                e.printStackTrace();
            }

            // add account
            Bundle result = addAccount(mContext);

            if (result != null) {
                if (result.containsKey(AccountManager.KEY_ACCOUNT_NAME)) {
                    setAccountAuthenticatorResult(result);
                    return true;
                } else {
                    return false;
                }
            } else {
                return false;
            }
        }

        @Override
        public void onPostExecute(Boolean result) {
            mDialog.dismiss();
            if (result)
                finish();
        }
    }
}
