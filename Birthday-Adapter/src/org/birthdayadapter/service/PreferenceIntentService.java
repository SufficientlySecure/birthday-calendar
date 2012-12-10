package org.birthdayadapter.service;

import android.app.IntentService;
import android.content.Intent;
import android.os.Bundle;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;

import org.birthdayadapter.util.Constants;
import org.birthdayadapter.util.Log;
import org.birthdayadapter.util.BackgroundStatusHandler;

public class PreferenceIntentService extends IntentService {

    /* extras that can be given by intent */
    public static final String EXTRA_MESSENGER = "messenger";
    public static final String EXTRA_ACTION = "action";
    public static final String EXTRA_DATA = "data";

    /* possible EXTRA_ACTIONs */
    public static final int ACTION_CHANGE_REMINDER = 0;
    public static final int ACTION_CHANGE_COLOR = 1;

    /* keys for data bundle */

    // change reminder
    public static final String CHANGE_REMINDER_NO = "reminderNo";
    public static final String CHANGE_REMINDER_NEW_MINUTES = "reminderNewMinutes";
    public static final String CHANGE_REMINDER_OLD_MINUTES = "reminderOldMinutes";

    // change color
    public static final String CHANGE_COLOR_NEW_COLOR = "colorNewColor";

    Messenger mMessenger;

    public PreferenceIntentService() {
        super("BirthdayAdapterPreferenceIntentService");
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

        if (!(extras.containsKey(EXTRA_MESSENGER) || extras.containsKey(EXTRA_DATA) || extras
                .containsKey(EXTRA_ACTION))) {
            Log.e(Constants.TAG,
                    "Extra bundle must contain a messenger, a data bundle, and an action!");
            return;
        }

        mMessenger = (Messenger) extras.get(EXTRA_MESSENGER);
        Bundle data = extras.getBundle(EXTRA_DATA);

        int action = extras.getInt(EXTRA_ACTION);

        setCircleWithHandler(true);

        // execute action from extra bundle
        switch (action) {
        case ACTION_CHANGE_COLOR:
            int newColor = data.getInt(CHANGE_COLOR_NEW_COLOR);

            CalendarSyncAdapterService.updateCalendarColor(this, newColor);

            break;

        case ACTION_CHANGE_REMINDER:
            int newMinutes = data.getInt(CHANGE_REMINDER_NEW_MINUTES);
            int reminderNo = data.getInt(CHANGE_REMINDER_NO);

            // Update all reminders to new minutes
            CalendarSyncAdapterService.updateAllReminders(this, reminderNo, newMinutes);
            break;

        default:
            break;
        }

        setCircleWithHandler(false);
    }

    private void setCircleWithHandler(boolean value) {
        Message msg = Message.obtain();

        if (value) {
            msg.what = BackgroundStatusHandler.CIRCLE_HANDLER_ENABLE;
        } else {
            msg.what = BackgroundStatusHandler.CIRCLE_HANDLER_DISABLE;
        }

        try {
            mMessenger.send(msg);
        } catch (RemoteException e) {
            Log.w(Constants.TAG, "Exception sending message, Is handler present?", e);
        } catch (NullPointerException e) {
            Log.w(Constants.TAG, "Messenger is null!", e);
        }
    }

}
