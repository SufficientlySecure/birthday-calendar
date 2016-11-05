/*
 * Copyright (C) 2012-2016 Dominik Schürmann <dominik@dominikschuermann.de>
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

import android.accounts.AccountAuthenticatorActivity;
import android.os.Bundle;

import org.birthdayadapter.util.AccountHelper;

/**
 * This activity is transparent, i.e., it has no layout
 */
public class AddAccountActivity extends AccountAuthenticatorActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        AccountHelper accHelper = new AccountHelper(this);
        Bundle result = accHelper.addAccountAndSync();
        if (result != null) {
            setAccountAuthenticatorResult(result);
        }

        finish();
    }

}
