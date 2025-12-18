package fr.heinisch.birthdayadapter.util;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import fr.heinisch.birthdayadapter.R;
import fr.heinisch.birthdayadapter.service.BirthdayWorker;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class MySharedPreferenceChangeListener implements SharedPreferences.OnSharedPreferenceChangeListener {
    private final Context mContext;
    private final Set<String> mFullResyncKeys;

    public MySharedPreferenceChangeListener(Context context) {
        super();
        mContext = context.getApplicationContext();

        // Initialize the set of preference keys that trigger a full resync
        mFullResyncKeys = new HashSet<>(Arrays.asList(
                mContext.getString(R.string.pref_title_enable_key),
                mContext.getString(R.string.pref_title_birthday_without_age_key),
                mContext.getString(R.string.pref_title_birthday_with_age_key),
                mContext.getString(R.string.pref_title_anniversary_without_age_key),
                mContext.getString(R.string.pref_title_anniversary_with_age_key),
                mContext.getString(R.string.pref_title_other_without_age_key),
                mContext.getString(R.string.pref_title_other_with_age_key),
                mContext.getString(R.string.pref_title_custom_without_age_key),
                mContext.getString(R.string.pref_title_custom_with_age_key),
                mContext.getString(R.string.pref_jubilee_years_key),
                // mContext.getString(R.string.pref_group_filtering_key),
                mContext.getString(R.string.pref_reminders_key)
        ));
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
            enqueueColorChangeWork();
            return;
        }

        // Keys that should not trigger a sync or are handled elsewhere
        String forceSyncKey = mContext.getString(R.string.pref_force_sync_key);
        String advancedKey = mContext.getString(R.string.pref_advanced_key);

        if (key.equals(forceSyncKey) || key.equals(advancedKey)) {
            return;
        }

        // Keys that trigger a full resync
        if (mFullResyncKeys.contains(key)) {
            Log.d(Constants.TAG, "Triggering full resync for key: " + key);
            new AccountHelper(mContext).triggerFullResync();
            return;
        }

        // For all other changes, trigger a normal manual sync
        Log.d(Constants.TAG, "Triggering differential sync for key: " + key);
        new AccountHelper(mContext).differentialSync();
    }

    /**
     * Enqueues a worker to handle the color change preference.
     */
    private void enqueueColorChangeWork() {
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
