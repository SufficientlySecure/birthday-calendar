/*
 * AccountEnableService.java
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

import java.util.Timer;
import java.util.TimerTask;

import android.accounts.AccountManager;
import android.accounts.AuthenticatorDescription;
import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.util.Log;

/**
 * This service listens for account updates. Once the original authenticator has taken over it
 * enables the workaround again.
 * 
 * @author Marten Gajda <marten@dmfs.org>
 */
public class AccountEnableService extends Service {
    private final static String TAG = "Birthday Adapter JB Workaround";
    private final static Timer mTimer = new Timer();

    @Override
    public void onCreate() {
        super.onCreate();
        mTimer.scheduleAtFixedRate(new mCheckerTask(), 5000, 5000);
    }

    @Override
    public IBinder onBind(Intent intent) {
        // no binding allowed
        return null;
    }

    /**
     * The checker task. It checks if the original account authenticator has taken over.
     * 
     * @author Marten Gajda <marten@dmfs.org>
     * 
     */
    private class mCheckerTask extends TimerTask {
        public void run() {
            Log.v(TAG, "checking accounts");

            AccountManager am = AccountManager.get(AccountEnableService.this);
            AuthenticatorDescription[] authenticators = am.getAuthenticatorTypes();
            String package_name = getString(R.string.package_name);
            // check all authenticators for the original package name
            for (AuthenticatorDescription authenticator : authenticators) {
                Log.v(TAG, authenticator.type + "   " + authenticator.packageName);
                if (package_name.equals(authenticator.packageName)) {
                    // enable the workaround now that the original authenticator has taken over
                    Log.v(TAG, "enable workaround authenticator");
                    PackageManager pm = getPackageManager();
                    pm.setComponentEnabledSetting(new ComponentName(AccountEnableService.this,
                            AuthenticationService.class),
                            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                            PackageManager.DONT_KILL_APP);

                    // stop this service, we're done
                    mTimer.cancel();
                    AccountEnableService.this.stopSelf();
                }
            }
        }
    }
}
