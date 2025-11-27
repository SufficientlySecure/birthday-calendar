package org.birthdayadapter.util;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import org.birthdayadapter.R;
import org.birthdayadapter.service.BirthdayWorker;

public class MySharedPreferenceChangeListener implements SharedPreferences.OnSharedPreferenceChangeListener {
    private final Context mContext;

    public MySharedPreferenceChangeListener(Context context) {
        super();
        mContext = context.getApplicationContext();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key == null || mContext == null) {
            return; // Safeguard
        }

        Log.d(Constants.TAG, "Preference changed: " + key);

        // Special case: Color change is a lightweight action
        String colorKey = mContext.getString(R.string.pref_color_key);
        if (key.equals(colorKey)) {
            startWork();
            return;
        }

        // Keys that should not trigger a sync or are handled elsewhere
        String forceSyncKey = mContext.getString(R.string.pref_force_sync_key);
        String advancedKey = mContext.getString(R.string.pref_advanced_key);

        if (key.equals(forceSyncKey) || key.equals(advancedKey)) {
            return;
        }

        // Reminder or title changes trigger a full resync
        String remindersKey = mContext.getString(R.string.pref_reminders_key);
        if (key.equals(remindersKey) || key.startsWith("pref_title_")) {
            Log.d(Constants.TAG, "Triggering full resync for reminder or title change: " + key);
            new AccountHelper(mContext).triggerFullResync();
            return;
        }

        // For all other changes, trigger a normal manual sync
        Log.d(Constants.TAG, "Triggering manual sync for key: " + key);
        new AccountHelper(mContext).manualSync();
    }

    /**
     * Start a worker to perform an action
     */
    private void startWork() {
        if (mContext == null) return;

        Data inputData = new Data.Builder()
                .putString(BirthdayWorker.ACTION, BirthdayWorker.ACTION_CHANGE_COLOR)
                .build();

        OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(BirthdayWorker.class)
                .setInputData(inputData)
                .build();

        WorkManager.getInstance(mContext).enqueue(workRequest);
    }
}
