/*
 * Copyright (C) 2012-2016 Dominik Schürmann <dominik@dominikschuermann.de>
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

package org.birthdayadapter.service;

import android.accounts.AbstractAccountAuthenticator;
import android.accounts.Account;
import android.accounts.AccountAuthenticatorResponse;
import android.accounts.AccountManager;
import android.accounts.NetworkErrorException;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;

import org.birthdayadapter.ui.CreateAccountActivity;

/**
 * Based on https://developer.android.com/training/sync-adapters/creating-authenticator.html
 */
public class AccountAuthenticatorService extends Service {
    private Authenticator mAuthenticator;

    @Override
    public void onCreate() {
        super.onCreate();
        mAuthenticator = new Authenticator(this);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mAuthenticator.getIBinder();
    }

    private static class Authenticator extends AbstractAccountAuthenticator {
        private Context mContext;

        Authenticator(Context context) {
            super(context);
            mContext = context;
        }

        /*
         * (non-Javadoc)
         * 
         * @see android.accounts.AbstractAccountAuthenticator#addAccount(android.
         * accounts.AccountAuthenticatorResponse, java.lang.String, java.lang.String,
         * java.lang.String[], android.os.Bundle)
         */
        @Override
        public Bundle addAccount(AccountAuthenticatorResponse response, String accountType,
                                 String authTokenType, String[] requiredFeatures, Bundle options)
                throws NetworkErrorException {

            Intent intent = new Intent(mContext, CreateAccountActivity.class);
            intent.putExtra(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE, response);

            Bundle result = new Bundle();
            result.putParcelable(AccountManager.KEY_INTENT, intent);
            return result;
        }

        /*
         * (non-Javadoc)
         * 
         * @see android.accounts.AbstractAccountAuthenticator#confirmCredentials(
         * android.accounts.AccountAuthenticatorResponse, android.accounts.Account,
         * android.os.Bundle)
         */
        @Override
        public Bundle confirmCredentials(AccountAuthenticatorResponse response, Account account,
                                         Bundle options) {
            throw new UnsupportedOperationException();
        }

        /*
         * (non-Javadoc)
         * 
         * @see android.accounts.AbstractAccountAuthenticator#editProperties(android
         * .accounts.AccountAuthenticatorResponse, java.lang.String)
         */
        @Override
        public Bundle editProperties(AccountAuthenticatorResponse response, String accountType) {
            throw new UnsupportedOperationException();
        }

        /*
         * (non-Javadoc)
         * 
         * @see android.accounts.AbstractAccountAuthenticator#getAuthToken(android
         * .accounts.AccountAuthenticatorResponse, android.accounts.Account, java.lang.String,
         * android.os.Bundle)
         */
        @Override
        public Bundle getAuthToken(AccountAuthenticatorResponse response, Account account,
                                   String authTokenType, Bundle options) throws NetworkErrorException {
            throw new UnsupportedOperationException();
        }

        /*
         * (non-Javadoc)
         * 
         * @see android.accounts.AbstractAccountAuthenticator#getAuthTokenLabel(java .lang.String)
         */
        @Override
        public String getAuthTokenLabel(String authTokenType) {
            throw new UnsupportedOperationException();
        }

        /*
         * (non-Javadoc)
         * 
         * @see android.accounts.AbstractAccountAuthenticator#hasFeatures(android
         * .accounts.AccountAuthenticatorResponse, android.accounts.Account, java.lang.String[])
         */
        @Override
        public Bundle hasFeatures(AccountAuthenticatorResponse response, Account account,
                                  String[] features) throws NetworkErrorException {
            throw new UnsupportedOperationException();
        }

        /*
         * (non-Javadoc)
         * 
         * @see android.accounts.AbstractAccountAuthenticator#updateCredentials(android
         * .accounts.AccountAuthenticatorResponse, android.accounts.Account, java.lang.String,
         * android.os.Bundle)
         */
        @Override
        public Bundle updateCredentials(AccountAuthenticatorResponse response, Account account,
                                        String authTokenType, Bundle options) {
            throw new UnsupportedOperationException();
        }
    }
}
