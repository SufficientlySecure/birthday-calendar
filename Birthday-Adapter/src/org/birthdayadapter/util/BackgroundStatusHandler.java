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
import android.os.Handler;
import android.os.Message;

public class BackgroundStatusHandler extends Handler {
    Activity mActivity;
    Object mSyncObserveHandle;

    SyncStatusObserver mMySyncStatusObserver = new SyncStatusObserver() {

        @Override
        public void onStatusChanged(int which) {
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
                BackgroundStatusHandler.this.sendEmptyMessage(CIRCLE_HANDLER_ENABLE);
            } else {
                BackgroundStatusHandler.this.sendEmptyMessage(CIRCLE_HANDLER_DISABLE);
            }
        }
    };

    public static final int CIRCLE_HANDLER_DISABLE = 0;
    public static final int CIRCLE_HANDLER_ENABLE = 1;

    int noOfRunningBackgroundThreads;

    public BackgroundStatusHandler(Activity activity) {
        super();
        this.mActivity = activity;
        noOfRunningBackgroundThreads = 0;
        // register observer to know when sync is running
        mSyncObserveHandle = ContentResolver.addStatusChangeListener(
                ContentResolver.SYNC_OBSERVER_TYPE_ACTIVE
                        | ContentResolver.SYNC_OBSERVER_TYPE_PENDING, mMySyncStatusObserver);
    }

    public void removeObserver() {
        // remove observer
        if (mSyncObserveHandle != null) {
            ContentResolver.removeStatusChangeListener(mSyncObserveHandle);
        }
    }

    @Override
    public void handleMessage(Message msg) {
        final int what = msg.what;

        switch (what) {
        case CIRCLE_HANDLER_ENABLE:
            noOfRunningBackgroundThreads++;

            mActivity.setProgressBarIndeterminateVisibility(Boolean.TRUE);

            break;

        case CIRCLE_HANDLER_DISABLE:
            noOfRunningBackgroundThreads--;

            if (noOfRunningBackgroundThreads <= 0) {
                mActivity.setProgressBarIndeterminateVisibility(Boolean.FALSE);
            }

            break;

        default:
            break;
        }
    }

}
