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

package org.birthdayadapter.service;

import android.app.IntentService;
import android.content.Intent;
import android.os.Bundle;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;

import org.birthdayadapter.util.AccountHelper;
import org.birthdayadapter.util.Constants;
import org.birthdayadapter.util.Log;
import org.birthdayadapter.util.BackgroundStatusHandler;

public class MainIntentService extends IntentService {

    /* extras that can be given by intent */
    public static final String EXTRA_MESSENGER = "messenger";
    public static final String EXTRA_ACTION = "action";
    public static final String EXTRA_DATA = "data";

    /* possible EXTRA_ACTIONs */
    public static final int ACTION_CHANGE_REMINDER = 0;
    public static final int ACTION_CHANGE_COLOR = 1;
    public static final int ACTION_MANUAL_SYNC = 2;

    /* keys for data bundle */

    // change reminder
    public static final String CHANGE_REMINDER_NO = "reminderNo";
    public static final String CHANGE_REMINDER_NEW_MINUTES = "reminderNewMinutes";

    // change color
    public static final String CHANGE_COLOR_NEW_COLOR = "colorNewColor";

    Messenger mMessenger;

    public MainIntentService() {
        super("BirthdayAdapterMainIntentService");
    }

    /**
     * The IntentService calls this method from the default worker thread with the intent that
     * started the service. When this method returns, IntentService stops the service, as
     * appropriate.
     */
    @Override
    protected void onHandleIntent(Intent intent) {
        Bundle extras = intent.getExtras();
        if (extras == null) {
            Log.e(Constants.TAG, "Extras bundle is null!");
            return;
        }

        if (!(extras.containsKey(EXTRA_ACTION))) {
            Log.e(Constants.TAG, "Extra bundle must contain an action!");
            return;
        }

        if (extras.containsKey(EXTRA_MESSENGER)) {
            mMessenger = (Messenger) extras.get(EXTRA_MESSENGER);
        }
        Bundle data = extras.getBundle(EXTRA_DATA);

        int action = extras.getInt(EXTRA_ACTION);

        setProgressCircleWithHandler(true);

        // execute action from extra bundle
        switch (action) {
        case ACTION_CHANGE_COLOR:
            int newColor = data.getInt(CHANGE_COLOR_NEW_COLOR);

            // only if enabled
            if (new AccountHelper(this).isAccountActivated()) {
                // update calendar color
                CalendarSyncAdapterService.updateCalendarColor(this, newColor);
            }

            break;

        case ACTION_CHANGE_REMINDER:
            int newMinutes = data.getInt(CHANGE_REMINDER_NEW_MINUTES);
            int reminderNo = data.getInt(CHANGE_REMINDER_NO);

            // only if enabled
            if (new AccountHelper(this).isAccountActivated()) {
                // Update all reminders to new minutes
                CalendarSyncAdapterService.updateAllReminders(this, reminderNo, newMinutes);
            }

            break;

        case ACTION_MANUAL_SYNC:

            // Force synchronous sync
            CalendarSyncAdapterService.performSync(this);

            break;

        default:
            break;
        }

        setProgressCircleWithHandler(false);
    }

    private void setProgressCircleWithHandler(boolean value) {
        Message msg = Message.obtain();

        if (value) {
            msg.what = BackgroundStatusHandler.BACKGROUND_STATUS_HANDLER_ENABLE;
        } else {
            msg.what = BackgroundStatusHandler.BACKGROUND_STATUS_HANDLER_DISABLE;
        }

        if (mMessenger != null) {
            try {
                mMessenger.send(msg);
            } catch (RemoteException e) {
                Log.w(Constants.TAG, "Exception sending message, Is handler present?", e);
            }
        }
    }

}
