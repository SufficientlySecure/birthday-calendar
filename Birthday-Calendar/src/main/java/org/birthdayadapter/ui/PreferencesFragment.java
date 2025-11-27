package org.birthdayadapter.ui;

import android.content.SharedPreferences;
import android.os.Bundle;

import org.birthdayadapter.util.AccountHelper;
import org.birthdayadapter.R;
import org.birthdayadapter.util.Constants;

import androidx.annotation.NonNull;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;

import org.birthdayadapter.util.PreferencesHelper;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

public class PreferencesFragment extends PreferenceFragmentCompat implements Preference.OnPreferenceClickListener {

    private AccountHelper mAccountHelper;
    private PreferenceCategory remindersCategory;

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getActivity() != null) {
            mAccountHelper = new AccountHelper(getActivity().getApplicationContext());
        }
    }

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        // Use the default shared preferences to avoid conflicts and ensure consistency.
        addPreferencesFromResource(R.xml.pref_preferences);

        remindersCategory = findPreference("pref_reminders_category");
        if (remindersCategory != null) {
            populateReminders();
        }

        Preference mForceSyncPref = findPreference(getString(R.string.pref_force_sync_key));
        if (mForceSyncPref != null) {
            mForceSyncPref.setOnPreferenceClickListener(this);
        }

        Preference colorPref = findPreference(getString(R.string.pref_color_key));
        if (colorPref != null) {
            colorPref.setOnPreferenceClickListener(this);
        }
    }

    private void populateReminders() {
        if (getContext() == null) return;

        remindersCategory.removeAll();

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

    @Override
    public boolean onPreferenceClick(@NonNull Preference preference) {
        if (getActivity() == null) return false;

        if (preference.getKey().equals(getString(R.string.pref_force_sync_key))) {
            if (mAccountHelper != null) {
                mAccountHelper.manualSync();
            }
            return true;
        } else if (preference.getKey().equals(getString(R.string.pref_color_key))) {
            // open color picker
            if (getActivity() instanceof ColorChangedListener) {
                ((ColorChangedListener) getActivity()).showColorPickerDialog(PreferencesHelper.getColor(getActivity()));
            }
        }

        return false;
    }

    public interface ColorChangedListener {
        void showColorPickerDialog(int currentColor);
    }

}
