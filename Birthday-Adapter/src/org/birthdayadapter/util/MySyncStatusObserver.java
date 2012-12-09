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

import android.app.Activity;
import android.content.ContentResolver;
import android.content.SyncStatusObserver;

public class MySyncStatusObserver implements SyncStatusObserver {
    Activity activity;

    public MySyncStatusObserver(Activity activity) {
        super();
        this.activity = activity;
    }

    @Override
    public void onStatusChanged(int which) {
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Log.d(Constants.TAG, "SyncStatusObserver, onStatusChanged");

                boolean syncActive = ContentResolver.isSyncActive(Constants.ACCOUNT,
                        Constants.CONTENT_AUTHORITY);
                boolean syncPending = ContentResolver.isSyncPending(Constants.ACCOUNT,
                        Constants.CONTENT_AUTHORITY);

                String syncActiveStr = syncActive ? "true" : "false";
                Log.d(Constants.TAG, "syncActive: " + syncActiveStr);
                String syncPendingStr = syncPending ? "true" : "false";
                Log.d(Constants.TAG, "syncPending: " + syncPendingStr);

                if (syncActive || syncPending) {
                    activity.setProgressBarIndeterminateVisibility(Boolean.TRUE);
                } else {
                    activity.setProgressBarIndeterminateVisibility(Boolean.FALSE);
                }
            }
        });
    }
};