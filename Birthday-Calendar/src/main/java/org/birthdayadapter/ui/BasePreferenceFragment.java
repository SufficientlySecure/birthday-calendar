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

import android.Manifest;
import android.annotation.TargetApi;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceFragmentCompat;
import android.support.v7.preference.SwitchPreferenceCompat;

import org.birthdayadapter.R;
import org.birthdayadapter.util.AccountHelper;
import org.birthdayadapter.util.Constants;
import org.birthdayadapter.util.Log;
import org.birthdayadapter.util.PreferencesHelper;

public class BasePreferenceFragment extends PreferenceFragmentCompat {
    private BaseActivity mActivity;
    private AccountHelper mAccountHelper;

    private SwitchPreferenceCompat mEnabled;
    private Preference mForceSync;

    public static final int MY_PERMISSIONS_REQUEST = 42;

    @Override
    public void onCreatePreferences(Bundle bundle, String s) {
        // save prefs here
        getPreferenceManager().setSharedPreferencesName(Constants.PREFS_NAME);
        addPreferencesFromResource(R.xml.base_preferences);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        mActivity = (BaseActivity) getActivity();
        mAccountHelper = new AccountHelper(mActivity, mActivity.mBackgroundStatusHandler);

        // if this is the first run, enable and sync birthday adapter!
        if (PreferencesHelper.getFirstRun(mActivity)) {
            PreferencesHelper.setFirstRun(mActivity, false);

            addAccountAndSync();
        }

        mEnabled = (SwitchPreferenceCompat) findPreference(getString(R.string.pref_enabled_key));
        mEnabled.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                if (newValue instanceof Boolean) {
                    Boolean boolVal = (Boolean) newValue;

                    if (boolVal) {
                        addAccountAndSync();
                        mForceSync.setEnabled(true);
                    } else {
                        mAccountHelper.removeAccount();
                        mForceSync.setEnabled(false);
                    }
                }
                return true;
            }
        });

        mForceSync = findPreference(getString(R.string.pref_force_sync_key));
        mForceSync.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                mAccountHelper.manualSync();

                return false;
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[],
                                           @NonNull int[] grantResults) {
        Log.d(Constants.TAG, "perm result");
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST: {
                if (grantResults.length > 0) {
                    for (int res : grantResults) {
                        if (res != PackageManager.PERMISSION_GRANTED) {
                            mEnabled.setChecked(false);
                            return;
                        }
                    }

                    // permission was granted
                    mAccountHelper.addAccountAndSync();
                    mEnabled.setChecked(true);
                }
            }
        }
    }

    private void addAccountAndSync() {
        if (hasPermissions()) {
            mAccountHelper.addAccountAndSync();
        }
    }

    @TargetApi(Build.VERSION_CODES.M)
    private boolean hasPermissions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true;
        }

        // check Android 6 permission
        int contactsPerm = ContextCompat.checkSelfPermission(getActivity(),
                Manifest.permission.READ_CONTACTS);
        int calendarPerm = ContextCompat.checkSelfPermission(getActivity(),
                Manifest.permission.READ_CALENDAR);

        if (contactsPerm == PackageManager.PERMISSION_GRANTED
                && calendarPerm == PackageManager.PERMISSION_GRANTED) {
            return true;
        } else {
            requestPermissions(
                    new String[]{
                            Manifest.permission.READ_CONTACTS,
                            Manifest.permission.READ_CALENDAR},
                    MY_PERMISSIONS_REQUEST);
            return false;
        }
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
