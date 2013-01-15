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

package org.birthdayadapter.ui;

import net.margaritov.preference.colorpicker.ColorPickerPreference;

import org.birthdayadapter.R;
import org.birthdayadapter.util.Constants;

import android.annotation.TargetApi;
import android.os.Build;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.PreferenceFragment;

@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
public class PreferencesFragment extends PreferenceFragment {
    BaseActivity mActivity;

    private ColorPickerPreference mColor;
    private ListPreference mReminder0;
    private ListPreference mReminder1;
    private ListPreference mReminder2;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mActivity = (BaseActivity) getActivity();

        // save prefs here
        getPreferenceManager().setSharedPreferencesName(Constants.PREFS_NAME);
        // load preferences from xml
        addPreferencesFromResource(R.xml.pref_preferences);

        mColor = (ColorPickerPreference) findPreference(getString(R.string.pref_color_key));
        mReminder0 = (ListPreference) findPreference(getString(R.string.pref_reminder_key0));
        mReminder1 = (ListPreference) findPreference(getString(R.string.pref_reminder_key1));
        mReminder2 = (ListPreference) findPreference(getString(R.string.pref_reminder_key2));

        /*
         * Functionality is defined in PreferenceImpl
         */
        mColor.setOnPreferenceChangeListener(new PreferenceImpl.ColorOnChange(mActivity,
                mActivity.mBackgroundStatusHandler));

        mReminder0.setOnPreferenceChangeListener(new PreferenceImpl.ReminderOnChange(mActivity,
                mActivity.mBackgroundStatusHandler, 0));
        mReminder1.setOnPreferenceChangeListener(new PreferenceImpl.ReminderOnChange(mActivity,
                mActivity.mBackgroundStatusHandler, 1));
        mReminder2.setOnPreferenceChangeListener(new PreferenceImpl.ReminderOnChange(mActivity,
                mActivity.mBackgroundStatusHandler, 2));
    }

}
