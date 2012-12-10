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

import org.birthdayadapter.AccountHelper;
import org.birthdayadapter.CalendarSyncAdapterService;
import org.birthdayadapter.R;
import org.birthdayadapter.util.Constants;
import org.birthdayadapter.util.Log;
import org.birthdayadapter.util.MySyncStatusObserver;
import org.birthdayadapter.util.PreferencesHelper;

import android.app.Activity;
import android.content.ContentResolver;
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

    private CheckBoxPreference mEnabled;
    private Preference mForceSync;

    private ColorPickerPreference mColor;
    private ListPreference mReminder;

    private Preference mHelp;

    Object mSyncObserveHandle;

    /**
     * Sets display of status to enabled/disabled based on account
     */
    private void setStatusBasedOnAccount() {
        // If account is activated check the preference
        if (mAccountHelper.isAccountActivated()) {
            mEnabled.setChecked(true);
        } else {
            mEnabled.setChecked(false);
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);

        super.onCreate(savedInstanceState);

        mActivity = this;

        mAccountHelper = new AccountHelper(mActivity);

        // save prefs here
        getPreferenceManager().setSharedPreferencesName(Constants.PREFS_NAME);
        // load preferences from xml
        addPreferencesFromResource(R.xml.base_preferences_v8);

        // register observer to know when sync is running
        mSyncObserveHandle = ContentResolver.addStatusChangeListener(
                ContentResolver.SYNC_OBSERVER_TYPE_ACTIVE
                        | ContentResolver.SYNC_OBSERVER_TYPE_PENDING, new MySyncStatusObserver(
                        mActivity));

        mEnabled = (CheckBoxPreference) findPreference(getString(R.string.pref_enabled_key));
        mForceSync = (Preference) findPreference(getString(R.string.pref_force_sync_key));

        mColor = (ColorPickerPreference) findPreference(getString(R.string.pref_color_key));
        mReminder = (ListPreference) findPreference(getString(R.string.pref_reminder_key));

        mHelp = (Preference) findPreference(getString(R.string.pref_help_key));

        // if this is the first run, enable and sync birthday adapter!
        if (PreferencesHelper.getFirstRun(mActivity)) {
            PreferencesHelper.setFirstRun(mActivity, false);

            mAccountHelper.addAccountAndSync();
        }

        // If account is activated check the preference
        setStatusBasedOnAccount();

        mEnabled.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                if (newValue instanceof Boolean) {
                    Boolean boolVal = (Boolean) newValue;

                    if (boolVal) {
                        mAccountHelper.addAccountAndSync();
                    } else {
                        mAccountHelper.removeAccount();
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
                    int newMinutes = Integer.valueOf(stringValue);
                    int oldMinutes = PreferencesHelper.getReminder(mActivity, 0);

                    Log.d(Constants.TAG, "Setting all reminders to " + newMinutes
                            + ", oldMinutes are " + oldMinutes);

                    // Update all reminders to new minutes, newMinutes=-1 will delete all
                    CalendarSyncAdapterService
                            .updateAllReminders(mActivity, newMinutes, oldMinutes);
                }

                return true;
            }
        });

        mHelp.setOnPreferenceClickListener(new OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                startActivity(new Intent(mActivity, HelpActivityV8.class));

                return false;
            }
        });

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // remove observer
        if (mSyncObserveHandle != null) {
            ContentResolver.removeStatusChangeListener(mSyncObserveHandle);
        }
    }

}
