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
            // sync reminders
            intent.putExtra(MainIntentService.EXTRA_ACTION, MainIntentService.ACTION_CHANGE_COLOR);
        } else if (context.getString(R.string.pref_reminder_enable_key0).equals(key)
                || context.getString(R.string.pref_reminder_enable_key1).equals(key)
                || context.getString(R.string.pref_reminder_enable_key2).equals(key)
                || context.getString(R.string.pref_reminder_time_key0).equals(key)
                || context.getString(R.string.pref_reminder_time_key1).equals(key)
                || context.getString(R.string.pref_reminder_time_key2).equals(key)) {
            // sync reminders
            intent.putExtra(MainIntentService.EXTRA_ACTION,
                    MainIntentService.ACTION_CHANGE_REMINDER_ENABLED);
        } else {
            // resync all events
            intent.putExtra(MainIntentService.EXTRA_ACTION, MainIntentService.ACTION_MANUAL_SYNC);
        }

        // start service with intent
        context.startService(intent);
    }

}
