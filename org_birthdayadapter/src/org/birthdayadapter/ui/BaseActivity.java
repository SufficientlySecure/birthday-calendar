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
import org.birthdayadapter.util.PreferencesHelper;

import android.accounts.AccountManager;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.util.Log;

public class BaseActivity extends PreferenceActivity {
    private Activity mActivity;

    private CheckBoxPreference mEnabled;
    private ColorPickerPreference mColor;

    private Preference mHelp;
    private Preference mAbout;

    /**
     * Called when the activity is first created.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // load preferences from xml
        addPreferencesFromResource(R.xml.base_preference);

        mActivity = this;
        mEnabled = (CheckBoxPreference) findPreference(getString(R.string.pref_enabled_key));
        mColor = (ColorPickerPreference) findPreference(getString(R.string.pref_color_key));

        mHelp = (Preference) findPreference(getString(R.string.pref_help_key));
        mAbout = (Preference) findPreference(getString(R.string.pref_about_key));

        // if this is the first run enable birthday adapter!
        if (PreferencesHelper.getFirstRun(mActivity)) {
            CreateTask t = new CreateTask(mActivity);
            t.execute();
            PreferencesHelper.setFirstRun(mActivity, false);
        }

        // If account is activated check the preference
        if (CreateActivity.isAccountActivated(mActivity)) {
            mEnabled.setChecked(true);
        } else {
            mEnabled.setChecked(false);
        }

        mEnabled.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                if (newValue instanceof Boolean) {
                    Boolean boolVal = (Boolean) newValue;

                    if (boolVal) {
                        // create adapter
                        CreateTask t = new CreateTask(mActivity);
                        t.execute();
                    } else {
                        // remove adapter
                        RemoveTask t = new RemoveTask(mActivity);
                        t.execute();
                    }
                }
                return true;
            }
        });

        mColor.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {

            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                // preference.setSummary(ColorPickerPreference.convertToARGB(Integer.valueOf(String
                // .valueOf(newValue))));

                int newColor = Integer.valueOf(String.valueOf(newValue));
                Log.d(Constants.TAG, "color changed to " + newColor);

                CalendarSyncAdapterService.updateCalendarColor(mActivity, newColor);

                return true;
            }

        });

        mHelp.setOnPreferenceClickListener(new OnPreferenceClickListener() {

            @Override
            public boolean onPreferenceClick(Preference preference) {
                startActivity(new Intent(mActivity, HelpActivity.class));

                return false;
            }

        });

        mAbout.setOnPreferenceClickListener(new OnPreferenceClickListener() {

            @Override
            public boolean onPreferenceClick(Preference preference) {
                startActivity(new Intent(mActivity, AboutActivity.class));

                return false;
            }

        });

    }

    public class CreateTask extends AsyncTask<String, Void, Boolean> {
        Context mContext;
        ProgressDialog mDialog;

        CreateTask(Context context) {
            mContext = context;

            mDialog = ProgressDialog.show(context, "", mContext.getString(R.string.creating), true,
                    false);
            mDialog.setCancelable(false);
        }

        @Override
        public Boolean doInBackground(String... params) {

            // Wait while asynchronous android background operations finish
            try {
                Thread.sleep(5000);
            } catch (Exception e) {
                e.printStackTrace();
            }

            // add account
            Bundle result = CreateActivity.addAccount(mContext);

            if (result != null) {
                if (result.containsKey(AccountManager.KEY_ACCOUNT_NAME)) {
                    return true;
                } else {
                    return false;
                }
            } else {
                return false;
            }
        }

        @Override
        public void onPostExecute(Boolean result) {
            mDialog.dismiss();
            if (result) {
                mEnabled.setEnabled(true);
            }
        }
    }

    public class RemoveTask extends AsyncTask<String, Void, Boolean> {
        Context mContext;
        ProgressDialog mDialog;

        RemoveTask(Context context) {
            mContext = context;

            mDialog = ProgressDialog.show(context, "", mContext.getString(R.string.removing), true,
                    false);
            mDialog.setCancelable(false);
        }

        @Override
        public Boolean doInBackground(String... params) {

            // Wait while asynchronous android background operations finish
            try {
                Thread.sleep(5000);
            } catch (Exception e) {
                e.printStackTrace();
            }

            // remove account
            boolean result = CreateActivity.removeAccount(mContext);

            return result;
        }

        @Override
        public void onPostExecute(Boolean result) {
            mDialog.dismiss();
            if (result) {
                mEnabled.setEnabled(false);
            }
        }
    }
}