/*
 * Copyright (C) 2012-2016 Dominik Schürmann <dominik@dominikschuermann.de>
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

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.OvalShape;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.format.DateUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentActivity;
import androidx.preference.MultiSelectListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;

import org.birthdayadapter.R;
import org.birthdayadapter.util.AccountHelper;
import org.birthdayadapter.util.Constants;
import org.birthdayadapter.util.PreferencesHelper;
import org.birthdayadapter.util.SyncStatusManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

public class ExtendedPreferencesFragment extends PreferenceFragmentCompat {

    BaseActivity mActivity;
    private AccountHelper mAccountHelper;
    private Preference colorPref;
    private Preference forceSyncPref;
    private Preference mJubileeYearsPref;
    private PreferenceCategory remindersCategory;
    private SharedPreferences mSyncStatusPrefs;
    private WorkInfo mBirthdaySyncWorkInfo;

    private final Handler mSyncUpdateHandler = new Handler(Looper.getMainLooper());
    private Runnable mSyncUpdateRunnable;

    private final SharedPreferences.OnSharedPreferenceChangeListener mSyncStatusListener = (sharedPreferences, key) -> {
        if ("last_sync_timestamp".equals(key) && getActivity() != null) {
            getActivity().runOnUiThread(this::updateSyncStatus);
        }
    };

    private final int[] baseColors = new int[]{
            0xfff44336, 0xffe91e63, 0xff9c27b0, 0xff673ab7, 0xff3f51b5, 0xff2196f3, 0xff03a9f4, 0xff00bcd4,
            0xff009688, 0xff4caf50, 0xff8bc34a, 0xffcddc39, 0xffffeb3b, 0xffffc107, 0xffff9800, 0xffff5722,
            0xff795548, 0xff9e9e9e, 0xff607d8b
    };

    @Override
    public void onCreatePreferences(Bundle bundle, String s) {
        // Use the default shared preferences to ensure consistency across the app.
        addPreferencesFromResource(R.xml.pref_preferences);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        FragmentActivity activity = getActivity();
        if (activity instanceof BaseActivity) {
            mActivity = (BaseActivity) activity;
        } else {
            return;
        }

        mAccountHelper = new AccountHelper(mActivity);
        mSyncStatusPrefs = mActivity.getSharedPreferences("sync_status_prefs", Context.MODE_PRIVATE);

        remindersCategory = findPreference("pref_reminders_category");
        if (remindersCategory != null) {
            MultiSelectListPreference reminderTypesPref = findPreference("pref_reminder_event_types");
            if (reminderTypesPref != null) {
                updateReminderEventTypesSummary(reminderTypesPref);
                reminderTypesPref.setOnPreferenceChangeListener((preference, newValue) -> {
                    mAccountHelper.triggerFullResync();
                    // This cast is safe because the preference is a MultiSelectListPreference
                    @SuppressWarnings("unchecked")
                    Set<String> values = (Set<String>) newValue;
                    updateReminderEventTypesSummary(reminderTypesPref, values);
                    return true;
                });
            }
            populateReminders();
        }

        forceSyncPref = findPreference(getString(R.string.pref_force_sync_key));
        if (forceSyncPref != null) {
            forceSyncPref.setOnPreferenceClickListener(preference -> {
                SyncStatusManager.getInstance().setSyncing(true);
                mAccountHelper.differentialSync();
                updateSyncStatus();
                return true;
            });
        }

        colorPref = findPreference(getString(R.string.pref_color_key));
        if (colorPref != null) {
            updateColorPreferenceIcon();
            colorPref.setOnPreferenceClickListener(preference -> {
                showColorPickerDialog();
                return true;
            });
        }

        mJubileeYearsPref = findPreference(getString(R.string.pref_jubilee_years_key));
        if (mJubileeYearsPref != null) {
            updateJubileeYearsSummary();
            mJubileeYearsPref.setOnPreferenceClickListener(preference -> {
                showJubileeYearsInputDialog();
                return true;
            });
        }

        WorkManager.getInstance(mActivity).getWorkInfosForUniqueWorkLiveData("periodic_sync")
                .observe(getViewLifecycleOwner(), workInfos -> {
                    if (workInfos != null && !workInfos.isEmpty()) {
                        mBirthdaySyncWorkInfo = workInfos.get(0);
                    } else {
                        mBirthdaySyncWorkInfo = null;
                    }
                    updateSyncStatus();
                });
    }

    private void updateReminderEventTypesSummary(MultiSelectListPreference preference) {
        updateReminderEventTypesSummary(preference, preference.getValues());
    }

    private void updateReminderEventTypesSummary(MultiSelectListPreference preference, Set<String> values) {
        if (preference == null) return;

        if (values.isEmpty()) {
            preference.setSummary(R.string.no_events_selected);
            return;
        }

        List<String> selectedEntries = new ArrayList<>();
        CharSequence[] entries = preference.getEntries();
        CharSequence[] entryValues = preference.getEntryValues();

        for (String value : values) {
            int index = -1;
            for (int i = 0; i < entryValues.length; i++) {
                if (entryValues[i].equals(value)) {
                    index = i;
                    break;
                }
            }
            if (index != -1) {
                selectedEntries.add(entries[index].toString());
            }
        }

        Collections.sort(selectedEntries);
        preference.setSummary(TextUtils.join(", ", selectedEntries));
    }

    private void populateReminders() {
        if (getContext() == null) return;

        List<Preference> prefsToKeep = new ArrayList<>();
        for (int i = 0; i < remindersCategory.getPreferenceCount(); i++) {
            Preference pref = remindersCategory.getPreference(i);
            if (pref instanceof MultiSelectListPreference) {
                prefsToKeep.add(pref);
            }
        }

        remindersCategory.removeAll();

        for (Preference pref : prefsToKeep) {
            remindersCategory.addPreference(pref);
        }

        int[] reminderMinutes = PreferencesHelper.getAllReminderMinutes(getContext());
        for (int i = 0; i < reminderMinutes.length; i++) {
            addReminderPreference(reminderMinutes[i], i, false);
        }

        Preference addReminderPref = new Preference(getContext());
        addReminderPref.setTitle(R.string.add_reminder);
        addReminderPref.setIcon(R.drawable.ic_add);
        addReminderPref.setOnPreferenceClickListener(preference -> {
            addReminderPreference(getResources().getInteger(R.integer.pref_reminder_time_def), reminderMinutes.length, true);
            return true;
        });
        remindersCategory.addPreference(addReminderPref);
    }

    private void addReminderPreference(int minutes, int index, boolean isNew) {
        if (getContext() == null) return;

        ReminderPreferenceCompat reminderPref = new ReminderPreferenceCompat(getContext(), null);
        reminderPref.setKey("pref_reminder_time_" + index);
        reminderPref.setTitle(getString(R.string.pref_reminder_time) + " " + (index + 1));
        reminderPref.setPersistent(false); // We are handling persistence manually
        reminderPref.setValue(minutes);
        reminderPref.setOnPreferenceChangeListener((preference, newValue) -> {
            saveReminders();
            return true;
        });
        reminderPref.setOnRemoveListener(preference -> {
            remindersCategory.removePreference(preference);
            saveReminders();
        });

        remindersCategory.addPreference(reminderPref);

        if (isNew) {
            reminderPref.performClick(true);
        }
    }

    private void saveReminders() {
        if (getContext() == null) return;

        SharedPreferences prefs = getPreferenceManager().getSharedPreferences();
        if (prefs == null) return;

        Set<String> reminderSet = new HashSet<>();
        for (int i = 0; i < remindersCategory.getPreferenceCount(); i++) {
            Preference pref = remindersCategory.getPreference(i);
            if (pref instanceof ReminderPreferenceCompat) {
                reminderSet.add(String.valueOf(((ReminderPreferenceCompat) pref).getValue()));
            }
        }

        prefs.edit().putStringSet(getString(R.string.pref_reminders_key), reminderSet).apply();
        populateReminders(); // Repopulate to reflect changes
    }

    private void updateJubileeYearsSummary() {
        if (mJubileeYearsPref != null && mActivity != null) {
            String jubileeYears = PreferencesHelper.getJubileeYears(mActivity);
            mJubileeYearsPref.setSummary(jubileeYears);
        }
    }

    private boolean isValidJubileeYears(String value) {
        if (TextUtils.isEmpty(value)) return true; // allow empty
        return Pattern.matches("^[1-9][0-9]*(?:\\s*,\\s*[1-9][0-9]*)*$", value.trim());
    }

    private void showJubileeYearsInputDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_jubilee_input, null);
        final EditText jubileeInput = dialogView.findViewById(R.id.jubileeInput);
        final TextInputLayout jubileeInputLayout = dialogView.findViewById(R.id.jubileeInputLayout);

        String currentJubileeYears = PreferencesHelper.getJubileeYears(mActivity);
        jubileeInput.setText(currentJubileeYears);

        AlertDialog jubileeDialog = new MaterialAlertDialogBuilder(mActivity)
                .setTitle(R.string.pref_jubilee_years_title)
                .setView(dialogView)
                .setPositiveButton(android.R.string.ok, (d, which) -> {
                    String jubileeYears = jubileeInput.getText().toString();
                    saveJubileeYears(jubileeYears);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .create();

        jubileeDialog.show();

        final Button positiveButton = jubileeDialog.getButton(AlertDialog.BUTTON_POSITIVE);
        positiveButton.setEnabled(isValidJubileeYears(currentJubileeYears));

        jubileeInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (isValidJubileeYears(s.toString())) {
                    positiveButton.setEnabled(true);
                    if (jubileeInputLayout != null) {
                        jubileeInputLayout.setError(null);
                    }
                } else {
                    positiveButton.setEnabled(false);
                    if (jubileeInputLayout != null) {
                        jubileeInputLayout.setError("Invalid format");
                    }
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    private void saveJubileeYears(String jubileeYears) {
        SharedPreferences.Editor editor = Objects.requireNonNull(getPreferenceManager().getSharedPreferences()).edit();
        editor.putString(getString(R.string.pref_jubilee_years_key), jubileeYears);
        editor.apply();
        updateJubileeYearsSummary();
    }

    private void updateSyncStatus() {
        if (forceSyncPref == null || mActivity == null) return;

        long lastSync = mSyncStatusPrefs.getLong("last_sync_timestamp", 0);
        String summary;

        if (lastSync == 0) {
            summary = getString(R.string.last_sync_never);
        } else {
            summary = getString(R.string.last_sync, DateUtils.getRelativeTimeSpanString(lastSync, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS));
        }

        if (mAccountHelper != null && mAccountHelper.isAccountActivated()) {
            if (mBirthdaySyncWorkInfo != null) {
                long nextRun = mBirthdaySyncWorkInfo.getNextScheduleTimeMillis();
                long now = System.currentTimeMillis();
                long sanityThreshold = now + TimeUnit.DAYS.toMillis(Constants.SYNC_INTERVAL_DAYS * 2);

                if (nextRun > now && nextRun < sanityThreshold) {
                    summary += "\n" + getString(R.string.next_sync, DateUtils.getRelativeTimeSpanString(nextRun, now, DateUtils.MINUTE_IN_MILLIS));
                }
            }
        }

        forceSyncPref.setSummary(summary);
    }

    private void showColorPickerDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_color_picker, null);
        RecyclerView recyclerView = dialogView.findViewById(R.id.colorPicker);

        AlertDialog colorDialog = new MaterialAlertDialogBuilder(mActivity)
                .setTitle(R.string.pref_color)
                .setView(dialogView)
                .create();

        int[] allColors = Arrays.copyOf(baseColors, baseColors.length + 1);
        allColors[baseColors.length] = ColorPickerAdapter.CUSTOM_COLOR;

        int numColumns = 4;
        ColorPickerAdapter adapter = new ColorPickerAdapter(allColors, numColumns,
                color -> {
                    saveColor(color);
                    colorDialog.dismiss();
                },
                () -> {
                    colorDialog.dismiss();
                    showHexInputDialog();
                }
        );
        recyclerView.setLayoutManager(new GridLayoutManager(mActivity, numColumns));
        recyclerView.setAdapter(adapter);

        colorDialog.show();
    }

    private void showHexInputDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_hex_input, null);
        final EditText hexInput = dialogView.findViewById(R.id.hexInput);
        final TextInputLayout hexInputLayout = dialogView.findViewById(R.id.hexInputLayout);

        int currentColor = PreferencesHelper.getColor(mActivity);
        hexInput.setText(String.format("#%06X", (0xFFFFFF & currentColor)));

        AlertDialog hexDialog = new MaterialAlertDialogBuilder(mActivity)
                .setTitle(R.string.hex_color_title)
                .setView(dialogView)
                .setPositiveButton(android.R.string.ok, (d, which) -> {
                    String hex = hexInput.getText().toString();
                    try {
                        int color = Color.parseColor(hex);
                        saveColor(color);
                    } catch (IllegalArgumentException e) {
                        // Invalid color, do nothing or show error
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .create();

        hexDialog.show();

        final Button positiveButton = hexDialog.getButton(AlertDialog.BUTTON_POSITIVE);
        positiveButton.setEnabled(true);

        hexInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s.toString().isEmpty()) {
                    positiveButton.setEnabled(false);
                    hexInputLayout.setError(null);
                    return;
                }
                try {
                    Color.parseColor(s.toString());
                    positiveButton.setEnabled(true);
                    hexInputLayout.setError(null);
                } catch (IllegalArgumentException e) {
                    positiveButton.setEnabled(false);
                    hexInputLayout.setError("Invalid hex code");
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    private void saveColor(int color) {
        SharedPreferences.Editor editor = Objects.requireNonNull(getPreferenceManager().getSharedPreferences()).edit();
        editor.putInt(getString(R.string.pref_color_key), color);
        editor.apply();
        updateColorPreferenceIcon();
    }

    private void updateColorPreferenceIcon() {
        if (colorPref != null) {
            int color = PreferencesHelper.getColor(mActivity);
            colorPref.setIcon(createColorDrawable(color));
        }
    }

    private Drawable createColorDrawable(int color) {
        ShapeDrawable coloredCircle = new ShapeDrawable(new OvalShape());
        coloredCircle.getPaint().setColor(color);
        coloredCircle.setIntrinsicWidth(72);
        coloredCircle.setIntrinsicHeight(72);
        return coloredCircle;
    }

    @Override
    public void onResume() {
        super.onResume();
        updateJubileeYearsSummary();
        mSyncStatusPrefs.registerOnSharedPreferenceChangeListener(mSyncStatusListener);

        mSyncUpdateRunnable = new Runnable() {
            @Override
            public void run() {
                updateSyncStatus();
                // Rerun every minute
                mSyncUpdateHandler.postDelayed(this, DateUtils.MINUTE_IN_MILLIS);
            }
        };
        // Immediately run and start the cycle
        mSyncUpdateHandler.post(mSyncUpdateRunnable);
    }

    @Override
    public void onPause() {
        super.onPause();
        mSyncStatusPrefs.unregisterOnSharedPreferenceChangeListener(mSyncStatusListener);
        // Stop the periodic UI updates
        mSyncUpdateHandler.removeCallbacks(mSyncUpdateRunnable);
    }
}
