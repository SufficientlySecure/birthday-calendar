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

import net.margaritov.preference.colorpicker.ColorPickerPreference;

import org.birthdayadapter.CalendarSyncAdapterService;
import org.birthdayadapter.R;
import org.birthdayadapter.util.Constants;
import org.birthdayadapter.util.Log;
import org.birthdayadapter.util.PreferencesHelper;

import android.annotation.TargetApi;
import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.Preference.OnPreferenceChangeListener;

@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
public class PreferencesFragment extends PreferenceFragment {
    Activity mActivity;

    private ColorPickerPreference mColor;
    private ListPreference mReminder;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mActivity = getActivity();

        // save prefs here
        getPreferenceManager().setSharedPreferencesName(Constants.PREFS_NAME);
        // load preferences from xml
        addPreferencesFromResource(R.xml.pref_preferences);

        mColor = (ColorPickerPreference) findPreference(getString(R.string.pref_color_key));
        mReminder = (ListPreference) findPreference(getString(R.string.pref_reminder_key));

        mColor.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                int newColor = Integer.valueOf(String.valueOf(newValue));
                Log.d(Constants.TAG, "color changed to " + newColor);

                CalendarSyncAdapterService.updateCalendarColor(mActivity, newColor);

                return true;
            }
        });

        mReminder.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                if (newValue instanceof String) {
                    String stringValue = (String) newValue;
                    int newMinutes = Integer.parseInt(stringValue);
                    int oldMinutes = PreferencesHelper.getReminder(mActivity);

                    Log.d(Constants.TAG, "Setting all reminders to " + newMinutes
                            + ", oldMinutes are " + oldMinutes);

                    // Update all reminders to new minutes, newMinutes=-1 will delete all
                    CalendarSyncAdapterService
                            .updateAllReminders(mActivity, newMinutes, oldMinutes);
                }

                return true;
            }
        });
    }

}
