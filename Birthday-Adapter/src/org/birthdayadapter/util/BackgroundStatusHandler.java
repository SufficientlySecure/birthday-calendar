/*
 * Copyright (C) 2012-2013 Dominik Schürmann <dominik@dominikschuermann.de>
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

import java.lang.ref.WeakReference;

import android.app.Activity;
import android.os.Handler;
import android.os.Message;

public class BackgroundStatusHandler extends Handler {
    public static final int BACKGROUND_STATUS_HANDLER_DISABLE = 0;
    public static final int BACKGROUND_STATUS_HANDLER_ENABLE = 1;

    WeakReference<Activity> mActivity;

    public BackgroundStatusHandler(Activity activity) {
        mActivity = new WeakReference<Activity>(activity);
        noOfRunningBackgroundThreads = 0;
    }

    int noOfRunningBackgroundThreads;

    @Override
    public void handleMessage(Message msg) {
        Activity activity = mActivity.get();
        final int what = msg.what;

        switch (what) {
        case BACKGROUND_STATUS_HANDLER_ENABLE:
            noOfRunningBackgroundThreads++;

            if (activity != null) {
                activity.setProgressBarIndeterminateVisibility(Boolean.TRUE);
            }
            break;

        case BACKGROUND_STATUS_HANDLER_DISABLE:
            noOfRunningBackgroundThreads--;

            if (noOfRunningBackgroundThreads <= 0) {
                if (activity != null) {
                    activity.setProgressBarIndeterminateVisibility(Boolean.FALSE);
                }
            }

            break;

        default:
            break;
        }
    }

}
