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

import org.birthdayadapter.R;
import org.birthdayadapter.service.MainIntentService;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Handler;
import android.os.Messenger;

public class MySharedPreferenceChangeListener implements OnSharedPreferenceChangeListener {
    Context context;
    Handler handler;

    public MySharedPreferenceChangeListener(Context context, Handler handler) {
        super();
        this.context = context;
        this.handler = handler;
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        // Send all information needed to service to do in other thread
        Intent intent = new Intent(context, MainIntentService.class);

        // Create a new Messenger for the communication back
        Messenger messenger = new Messenger(handler);
        intent.putExtra(MainIntentService.EXTRA_MESSENGER, messenger);

        if (context.getString(R.string.pref_color_key).equals(key)) {
            // set new color
            intent.setAction(MainIntentService.ACTION_CHANGE_COLOR);
        } else if (context.getString(R.string.pref_reminder_enable_key0).equals(key)
                || context.getString(R.string.pref_reminder_enable_key1).equals(key)
                || context.getString(R.string.pref_reminder_enable_key2).equals(key)
                || context.getString(R.string.pref_reminder_time_key0).equals(key)
                || context.getString(R.string.pref_reminder_time_key1).equals(key)
                || context.getString(R.string.pref_reminder_time_key2).equals(key)) {
            // sync reminders
            intent.setAction(MainIntentService.ACTION_CHANGE_REMINDER);
        } else {
            // resync all events
            intent.setAction(MainIntentService.ACTION_MANUAL_COMPLETE_SYNC);
        }

        // start service with intent
        context.startService(intent);
    }

}
