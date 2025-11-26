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

import android.content.Context;
import android.content.SharedPreferences;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import org.birthdayadapter.R;
import org.birthdayadapter.service.BirthdayWorker;

public class MySharedPreferenceChangeListener implements SharedPreferences.OnSharedPreferenceChangeListener {
    private final Context context;

    public MySharedPreferenceChangeListener(Context context) {
        super();
        this.context = context;
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key == null || context == null) {
            return; // Safeguard
        }

        Log.d("BirthdayAdapter", "Preference changed: " + key);

        // --- Special case: Color change is a lightweight action ---
        String colorKey = context.getString(R.string.pref_color_key);
        if (key.equals(colorKey)) {
            startWork();
            return;
        }

        // --- Keys for UI elements or one-off actions that should NOT trigger a sync ---
        String advancedKey = context.getString(R.string.pref_advanced_key);
        String contactsKey = context.getString(R.string.pref_contacts_key);
        String forceSyncKey = context.getString(R.string.pref_force_sync_key);

        if (key.equals(advancedKey) || key.equals(contactsKey) || key.equals(forceSyncKey)) {
            // These are UI toggles or have their own click listeners, so we do nothing here.
            return;
        }

        // --- Catch-all for any other preference change ---
        // Assume it affects event data (reminders, titles, date formats, etc.) and trigger the full resync.
        Log.d("BirthdayAdapter", "Triggering full calendar resync for key: " + key);
        new AccountHelper(context).updateReminders();
    }

    /**
     * Start a worker to perform an action
     */
    private void startWork() {
        if (context == null) return;

        Data inputData = new Data.Builder()
                .putString(BirthdayWorker.ACTION, BirthdayWorker.ACTION_CHANGE_COLOR)
                .build();

        OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(BirthdayWorker.class)
                .setInputData(inputData)
                .build();

        WorkManager.getInstance(context).enqueue(workRequest);
    }

}
