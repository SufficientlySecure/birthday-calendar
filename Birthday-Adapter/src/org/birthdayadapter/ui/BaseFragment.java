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

import org.birthdayadapter.R;
import org.birthdayadapter.util.AccountUtils;
import org.birthdayadapter.util.Constants;
import org.birthdayadapter.util.PreferencesHelper;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.SwitchPreference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.Preference.OnPreferenceClickListener;

@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
public class BaseFragment extends PreferenceFragment {
    private Activity mActivity;

    private SwitchPreference mEnabled;
    private Preference mForceSync;

    /**
     * Extend class from AccountUtils to change behavior in onPostExecute
     */
    private class createTask extends AccountUtils.CreateTask {

        createTask(Context context) {
            super(context);
        }

        @Override
        protected void onPostExecute(Boolean result) {
            super.onPostExecute(result);

            mDialog.dismiss();
            if (result) {
                setStatusBasedOnAccount();
            }
        }
    }

    /**
     * Extend class from AccountUtils to change behavior in onPostExecute
     */
    private class removeTask extends AccountUtils.RemoveTask {

        removeTask(Context context) {
            super(context);
        }

        @Override
        protected void onPostExecute(Boolean result) {
            super.onPostExecute(result);

            mDialog.dismiss();
            if (result) {
                setStatusBasedOnAccount();
            }
        }
    }

    /**
     * Sets display of status to enabled/disabled based on account
     */
    @SuppressLint("NewApi")
    private void setStatusBasedOnAccount() {
        // If account is activated check the preference
        if (AccountUtils.isAccountActivated(mActivity)) {
            mEnabled.setChecked(true);
        } else {
            mEnabled.setChecked(false);
        }
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        mActivity = getActivity();

        // save prefs here
        getPreferenceManager().setSharedPreferencesName(Constants.PREFS_NAME);
        // load preferences from xml
        addPreferencesFromResource(R.xml.base_preferences);

        mEnabled = (SwitchPreference) findPreference(getString(R.string.pref_enabled_key));
        mForceSync = (Preference) findPreference(getString(R.string.pref_force_sync_key));

        // if this is the first run enable birthday adapter!
        if (PreferencesHelper.getFirstRun(mActivity)) {
            createTask t = new createTask(mActivity);
            t.execute();
            PreferencesHelper.setFirstRun(mActivity, false);
        }

        // If account is activated check the preference
        setStatusBasedOnAccount();

        mEnabled.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                if (newValue instanceof Boolean) {
                    Boolean boolVal = (Boolean) newValue;

                    if (boolVal) {
                        // create adapter
                        createTask t = new createTask(mActivity);
                        t.execute();
                    } else {
                        // remove adapter
                        removeTask t = new removeTask(mActivity);
                        t.execute();
                    }
                }
                return true;
            }
        });

        mForceSync.setOnPreferenceClickListener(new OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                // force sync
                AccountUtils.SyncTask t = new AccountUtils.SyncTask(mActivity);
                t.execute();

                return false;
            }
        });
    }

}
