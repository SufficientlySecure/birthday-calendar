/*
 * Authenticator.java
 *
 * Copyright (C) 2012 Marten Gajda <marten@dmfs.org>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 */

package org.birthdayadapter.jb.workaround;

import android.accounts.AbstractAccountAuthenticator;
import android.accounts.Account;
import android.accounts.AccountAuthenticatorResponse;
import android.accounts.AccountManager;
import android.accounts.NetworkErrorException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;

/**
 * This is the fake authenticator service.
 * 
 * Adjust to your needs. It should mimic the real authenticator and call its activities, this way we
 * don't need to include them here.
 * 
 * @author Marten Gajda <marten@dmfs.org>
 */
public class Authenticator extends AbstractAccountAuthenticator {
    private Context mContext;

    public Authenticator(Context context) {
        super(context);
        mContext = context;
    }

    /**
     * Get the account type from the resources.
     * 
     * @param context
     *            Our {@link Context}
     * @return the account type of the original sync app
     */
    public static String getAccountType(Context context) {
        return context.getString(R.string.account_type);
    }

    /**
     * Request new account by returning an intent for the AuthenticatorActivity
     * 
     * Add parameters that your authenticator activity needs.
     */
    @Override
    public Bundle addAccount(AccountAuthenticatorResponse response, String accountType,
            String authTokenType, String[] requiredFeatures, Bundle options)
            throws NetworkErrorException {
        try {
            mContext.getPackageManager().getApplicationInfo(
                    mContext.getString(R.string.package_name), 0);
        } catch (PackageManager.NameNotFoundException e) {
            // original sync app is not installed, do nothing
            return null;
        }

        final Intent intent = new Intent(mContext.getString(R.string.authenticator_activity));
        intent.putExtra(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE, response);

        final Bundle bundle = new Bundle();
        bundle.putParcelable(AccountManager.KEY_INTENT, intent);
        return bundle;
    }

    /**
     * Called to check that a user knows the credentials of an account.
     * 
     * Add parameters that your password check activity needs.
     */
    @Override
    public Bundle confirmCredentials(AccountAuthenticatorResponse response, Account account,
            Bundle options) throws NetworkErrorException {
        return null;
    }

    @Override
    public Bundle editProperties(AccountAuthenticatorResponse response, String accountType) {
        throw new UnsupportedOperationException();
    }

    /**
     * Return the authtoken.
     * 
     * You must change this to your needs!
     */
    @Override
    public Bundle getAuthToken(AccountAuthenticatorResponse response, Account account,
            String authTokenType, Bundle options) throws NetworkErrorException {

        return null;
    }

    @Override
    public String getAuthTokenLabel(String authTokenType) {
        return mContext.getString(R.string.label);
    }

    @Override
    public Bundle hasFeatures(AccountAuthenticatorResponse response, Account account,
            String[] features) throws NetworkErrorException {
        return null;
    }

    @Override
    public Bundle updateCredentials(AccountAuthenticatorResponse response, Account account,
            String authTokenType, Bundle options) throws NetworkErrorException {
        return null;
    }

}
