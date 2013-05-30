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

    /* possible EXTRA_ACTIONs */
    public static final String ACTION_MANUAL_COMPLETE_SYNC = "MANUAL_SYNC";
    public static final String ACTION_CHANGE_COLOR = "CHANGE_COLOR";
    public static final String ACTION_CHANGE_REMINDER = "CHANGE_REMINDER";

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

        if (intent.getAction() == null) {
            Log.e(Constants.TAG, "Intent must contain an action!");
            return;
        }

        if (extras.containsKey(EXTRA_MESSENGER)) {
            mMessenger = (Messenger) extras.get(EXTRA_MESSENGER);
        }

        String action = intent.getAction();

        setProgressCircleWithHandler(true);

        // execute action
        if (ACTION_CHANGE_COLOR.equals(action)) {
            // only if enabled
            if (new AccountHelper(this).isAccountActivated()) {
                // update calendar color
                CalendarSyncAdapterService.updateCalendarColor(this);
            }
        } else if (ACTION_CHANGE_REMINDER.equals(action)) {
            // only if enabled
            if (new AccountHelper(this).isAccountActivated()) {
                // Update all reminders to new minutes
                CalendarSyncAdapterService.updateAllReminders(this);
            }
        } else if (ACTION_MANUAL_COMPLETE_SYNC.equals(action)) {
            // Force synchronous sync
            CalendarSyncAdapterService.performSync(this);
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
