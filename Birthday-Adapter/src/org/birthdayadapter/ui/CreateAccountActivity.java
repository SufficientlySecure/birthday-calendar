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

import org.birthdayadapter.util.AccountHelper;
import org.birthdayadapter.util.Constants;
import org.birthdayadapter.util.Log;

import android.accounts.AccountAuthenticatorActivity;
import android.accounts.AccountManager;
import android.os.Bundle;

public class CreateAccountActivity extends AccountAuthenticatorActivity {

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        AccountHelper accHelper = new AccountHelper(this);
        Bundle result = accHelper.addAccount();

        if (result != null) {
            if (result.containsKey(AccountManager.KEY_ACCOUNT_NAME)) {
                // Force a sync! Even when background sync is disabled, this will force one sync!
                accHelper.manualSync();

                setAccountAuthenticatorResult(result);
            } else {
                Log.e(Constants.TAG,
                        "Account was not added! result did not contain KEY_ACCOUNT_NAME!");
            }
        } else {
            Log.e(Constants.TAG, "Account was not added! result was null!");
        }

        finish();
    }

}
