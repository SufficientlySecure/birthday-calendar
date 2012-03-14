/*
 * Copyright (C) 2010 Sam Steele
 * Copyright (C) 2012 Dominik Schürmann <dominik@dominikschuermann.de>
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.birthdayadapter;

import android.accounts.Account;
import android.accounts.AccountAuthenticatorActivity;
import android.accounts.AccountManager;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;

public class CreateActivity extends AccountAuthenticatorActivity {
    // Button mCreateButton;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
//        setContentView(R.layout.main);

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
        CreateTask t = new CreateTask(CreateActivity.this);
        t.execute();
    }

    public class CreateTask extends AsyncTask<String, Void, Boolean> {
        Context mContext;
        ProgressDialog mDialog;

        CreateTask(Context c) {
            mContext = c;
            // mCreateButton.setEnabled(false);

            mDialog = ProgressDialog.show(c, "", getString(R.string.creating), true, false);
            mDialog.setCancelable(true);
        }

        @Override
        public Boolean doInBackground(String... params) {
            // String user = params[0];
            // String pass = params[1];
            String user = getString(R.string.app_name);
            String pass = "no";

            // Do something internetty
            try {
                Thread.sleep(2000);
            } catch (Exception e) {
                e.printStackTrace();
            }

            Bundle result = null;
            Account account = new Account(user, mContext.getString(R.string.ACCOUNT_TYPE));

            // enable automatically
            ContentResolver.setSyncAutomatically(account, getString(R.string.CONTENT_AUTHORITY),
                    true);
            ContentResolver.setIsSyncable(account, getString(R.string.CONTENT_AUTHORITY), 1);

            AccountManager am = AccountManager.get(mContext);
            if (am.addAccountExplicitly(account, pass, null)) {
                result = new Bundle();
                result.putString(AccountManager.KEY_ACCOUNT_NAME, account.name);
                result.putString(AccountManager.KEY_ACCOUNT_TYPE, account.type);
                setAccountAuthenticatorResult(result);
                return true;
            } else {
                return false;
            }
        }

        @Override
        public void onPostExecute(Boolean result) {
            // mCreateButton.setEnabled(true);
            mDialog.dismiss();
            if (result)
                finish();
        }
    }
}
