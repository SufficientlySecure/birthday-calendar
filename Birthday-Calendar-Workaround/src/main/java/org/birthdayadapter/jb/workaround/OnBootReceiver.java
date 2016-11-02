/*
 * OnBootReceiver.java
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

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.util.Log;

/**
 * This receiver disables the workaround authenticator to let the original authenticator take over.
 * Also it starts a service that re-enables the workaround once that has happened.
 * 
 * @author Marten Gajda <marten@dmfs.org>
 */
public class OnBootReceiver extends BroadcastReceiver {
    private final static String TAG = "Birthday Adapter JB Workaround";

    @Override
    public void onReceive(Context context, Intent intent) {
        PackageManager pm = context.getPackageManager();

        // start the service that re-enables the workaround
        Intent serviceIntent = new Intent(context, AccountEnableService.class);
        context.startService(serviceIntent);

        // disable workaround
        Log.v(TAG, "disable authenticator");
        pm.setComponentEnabledSetting(new ComponentName(context, AuthenticationService.class),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
    }

}
