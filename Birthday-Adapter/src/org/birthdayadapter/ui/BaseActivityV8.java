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

import org.birthdayadapter.R;
import org.birthdayadapter.util.AccountHelper;
import org.birthdayadapter.util.BackgroundStatusHandler;
import org.birthdayadapter.util.Constants;
import org.birthdayadapter.util.PreferencesHelper;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.view.Window;

/**
 * This BaseActivity uses the old Layout for Android < 3
 */
@SuppressWarnings("deprecation")
public class BaseActivityV8 extends PreferenceActivity {
    private Activity mActivity;
    private AccountHelper mAccountHelper;

    public BackgroundStatusHandler mBackgroundStatusHandler = new BackgroundStatusHandler(this);

    private CheckBoxPreference mEnabled;
    private Preference mForceSync;

    private ColorPickerPreference mColor;
    private ListPreference mReminder0;
    private ListPreference mReminder1;
    private ListPreference mReminder2;

    private Preference mHelp;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);

        super.onCreate(savedInstanceState);

        mActivity = this;

        // save prefs here
        getPreferenceManager().setSharedPreferencesName(Constants.PREFS_NAME);
        // load preferences from xml
        addPreferencesFromResource(R.xml.base_preferences_v8);

        // default is disabled:
        mActivity.setProgressBarIndeterminateVisibility(Boolean.FALSE);

        mAccountHelper = new AccountHelper(mActivity, mBackgroundStatusHandler);

        mEnabled = (CheckBoxPreference) findPreference(getString(R.string.pref_enabled_key));
        mForceSync = (Preference) findPreference(getString(R.string.pref_force_sync_key));

        mColor = (ColorPickerPreference) findPreference(getString(R.string.pref_color_key));
        mReminder0 = (ListPreference) findPreference(getString(R.string.pref_reminder_key0));
        mReminder1 = (ListPreference) findPreference(getString(R.string.pref_reminder_key1));
        mReminder2 = (ListPreference) findPreference(getString(R.string.pref_reminder_key2));

        mHelp = (Preference) findPreference(getString(R.string.pref_help_key));

        // if this is the first run, enable and sync birthday adapter!
        if (PreferencesHelper.getFirstRun(mActivity)) {
            PreferencesHelper.setFirstRun(mActivity, false);

            mAccountHelper.addAccountAndSync();
        }

        mEnabled.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                if (newValue instanceof Boolean) {
                    Boolean boolVal = (Boolean) newValue;

                    if (boolVal) {
                        mAccountHelper.addAccountAndSync();
                        mForceSync.setEnabled(true);
                    } else {
                        mAccountHelper.removeAccount();
                        mForceSync.setEnabled(false);
                    }
                }
                return true;
            }
        });

        mForceSync.setOnPreferenceClickListener(new OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                mAccountHelper.manualSync();

                return false;
            }
        });

        /*
         * Functionality is defined in PreferenceImpl
         */
        mColor.setOnPreferenceChangeListener(new PreferenceImpl.ColorOnChange(mActivity,
                mBackgroundStatusHandler));

        mReminder0.setOnPreferenceChangeListener(new PreferenceImpl.ReminderOnChange(mActivity,
                mBackgroundStatusHandler, 0));
        mReminder1.setOnPreferenceChangeListener(new PreferenceImpl.ReminderOnChange(mActivity,
                mBackgroundStatusHandler, 1));
        mReminder2.setOnPreferenceChangeListener(new PreferenceImpl.ReminderOnChange(mActivity,
                mBackgroundStatusHandler, 2));

        mHelp.setOnPreferenceClickListener(new OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                startActivity(new Intent(mActivity, HelpActivityV8.class));

                return false;
            }
        });

    }

    @Override
    public void onResume() {
        super.onResume();

        // If account is activated check the preference
        if (mAccountHelper.isAccountActivated()) {
            mEnabled.setChecked(true);
            mForceSync.setEnabled(true);
        } else {
            mEnabled.setChecked(false);
            mForceSync.setEnabled(false);
        }
    }

}
