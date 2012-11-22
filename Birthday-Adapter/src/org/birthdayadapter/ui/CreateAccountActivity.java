/*
 * Copyright (C) 2012 Dominik Schürmann <dominik@dominikschuermann.de>
 * Copyright (C) 2010 Sam Steele
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
import org.birthdayadapter.util.AccountUtils;

import android.accounts.AccountAuthenticatorActivity;
import android.accounts.AccountManager;
import android.app.ProgressDialog;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;

public class CreateAccountActivity extends AccountAuthenticatorActivity {
    // Button mCreateButton;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // create directly, there are no options
        CreateTask t = new CreateTask(this);
        t.execute();
    }

    public class CreateTask extends AsyncTask<String, Void, Boolean> {
        Context mContext;
        ProgressDialog mDialog;

        CreateTask(Context context) {
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
        protected void onPostExecute(Boolean result) {
            mDialog.dismiss();

            finish();
        }
    }
}
