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

package org.birthdayadapter.ui;

import org.birthdayadapter.service.PreferenceIntentService;
import org.birthdayadapter.util.Constants;
import org.birthdayadapter.util.Log;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Messenger;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;

public class PreferenceImpl {

    public static class ColorOnChange implements OnPreferenceChangeListener {
        Context context;
        Handler handler;

        public ColorOnChange(Context context, Handler handler) {
            super();
            this.context = context;
            this.handler = handler;
        }

        @Override
        public boolean onPreferenceChange(Preference preference, Object newValue) {
            int newColor = Integer.valueOf(String.valueOf(newValue));
            Log.d(Constants.TAG, "color changed to " + newColor);

            // Send all information needed to service to do in other thread
            Intent intent = new Intent(context, PreferenceIntentService.class);

            // Create a new Messenger for the communication back
            Messenger messenger = new Messenger(handler);
            intent.putExtra(PreferenceIntentService.EXTRA_MESSENGER, messenger);
            intent.putExtra(PreferenceIntentService.EXTRA_ACTION,
                    PreferenceIntentService.ACTION_CHANGE_COLOR);

            // fill values for this action
            Bundle data = new Bundle();
            data.putInt(PreferenceIntentService.CHANGE_COLOR_NEW_COLOR, newColor);
            intent.putExtra(PreferenceIntentService.EXTRA_DATA, data);

            // start service with intent
            context.startService(intent);

            return true;
        }
    }

    public static class ReminderOnChange implements OnPreferenceChangeListener {
        Context context;
        Handler handler;
        int reminderNo;

        public ReminderOnChange(Context context, Handler handler, int reminderNo) {
            super();
            this.context = context;
            this.handler = handler;
            this.reminderNo = reminderNo;
        }

        @Override
        public boolean onPreferenceChange(Preference preference, Object newValue) {
            if (newValue instanceof String) {
                String stringValue = (String) newValue;
                final int newMinutes = Integer.valueOf(stringValue);

                // Send all information needed to service to do in other thread
                Intent intent = new Intent(context, PreferenceIntentService.class);

                // Create a new Messenger for the communication back
                Messenger messenger = new Messenger(handler);
                intent.putExtra(PreferenceIntentService.EXTRA_MESSENGER, messenger);
                intent.putExtra(PreferenceIntentService.EXTRA_ACTION,
                        PreferenceIntentService.ACTION_CHANGE_REMINDER);

                // fill values for this action
                Bundle data = new Bundle();
                data.putInt(PreferenceIntentService.CHANGE_REMINDER_NEW_MINUTES, newMinutes);
                data.putInt(PreferenceIntentService.CHANGE_REMINDER_NO, reminderNo);
                intent.putExtra(PreferenceIntentService.EXTRA_DATA, data);

                // start service with intent
                context.startService(intent);
            }

            return true;
        }
    }

}
