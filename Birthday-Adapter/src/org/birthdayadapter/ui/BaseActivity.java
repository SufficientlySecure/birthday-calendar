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
import org.birthdayadapter.util.AccountUtils;
import org.birthdayadapter.util.Constants;
import org.birthdayadapter.util.Log;
import org.birthdayadapter.util.PreferencesHelper;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.SwitchPreference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;

public class BaseActivity extends PreferenceActivity {
    private Activity mActivity;

    private Preference mEnabled;
    private ColorPickerPreference mColor;
    private ListPreference mReminder;
    private Preference mForceSync;

    private Preference mHelp;
    private Preference mAbout;

    /**
     * Sets display of status to enabled/disabled based on account
     */
    private void setStatusBasedOnAccount() {
        // If account is activated check the preference
        if (AccountUtils.isAccountActivated(mActivity)) {
            // use check box for android < 4, otherwise the new SwitchPreference
            if (Build.VERSION.SDK_INT < 14) {
                ((CheckBoxPreference) mEnabled).setChecked(true);
            } else {
                ((SwitchPreference) mEnabled).setChecked(true);
            }
        } else {
            if (Build.VERSION.SDK_INT < 14) {
                ((CheckBoxPreference) mEnabled).setChecked(false);
            } else {
                ((SwitchPreference) mEnabled).setChecked(false);
            }
        }
    }

    /**
     * Called when the activity is first created.
     */
    @SuppressWarnings("deprecation")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mActivity = this;

        // save prefs here
        getPreferenceManager().setSharedPreferencesName(Constants.PREFS_NAME);
        // load preferences from xml
        addPreferencesFromResource(R.xml.base_preferences);

        mEnabled = (Preference) findPreference(getString(R.string.pref_enabled_key));
        mColor = (ColorPickerPreference) findPreference(getString(R.string.pref_color_key));
        mReminder = (ListPreference) findPreference(getString(R.string.pref_reminder_key));
        mForceSync = (Preference) findPreference(getString(R.string.pref_force_sync_key));

        mHelp = (Preference) findPreference(getString(R.string.pref_help_key));
        mAbout = (Preference) findPreference(getString(R.string.pref_about_key));

        // if this is the first run enable birthday adapter!
        if (PreferencesHelper.getFirstRun(mActivity)) {
            CreateTask t = new CreateTask(mActivity);
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

        mForceSync.setOnPreferenceClickListener(new OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                // force sync
                SyncTask t = new SyncTask(mActivity);
                t.execute();

                return false;
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

    public class SyncTask extends AsyncTask<Void, Void, Void> {
        Context mContext;
        ProgressDialog mDialog;

        SyncTask(Context context) {
            mContext = context;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();

            mDialog = ProgressDialog.show(mContext, "", mContext.getString(R.string.synchronizing),
                    true, false);
            mDialog.setCancelable(false);
        }

        @Override
        protected Void doInBackground(Void... unused) {

            // force sync now!
            Account account = new Account(Constants.ACCOUNT_NAME, Constants.ACCOUNT_TYPE);

            Bundle extras = new Bundle();
            // force resync!
            extras.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true);
            ContentResolver.requestSync(account, Constants.CONTENT_AUTHORITY, extras);

            // Wait while asynchronous android background operations finish
            try {
                Thread.sleep(5000);
            } catch (Exception e) {
                e.printStackTrace();
            }

            // return nothing as type is Void
            return null;
        }

        @Override
        protected void onPostExecute(Void unused) {
            super.onPostExecute(unused);

            mDialog.dismiss();
        }
    }

    public class CreateTask extends AsyncTask<String, Void, Boolean> {
        Context mContext;
        ProgressDialog mDialog;

        CreateTask(Context context) {
            mContext = context;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();

            mDialog = ProgressDialog.show(mContext, "", mContext.getString(R.string.creating),
                    true, false);
            mDialog.setCancelable(false);
        }

        @Override
        protected Boolean doInBackground(String... params) {

            // add account
            Bundle result = AccountUtils.addAccount(mContext);

            // Wait while asynchronous android background operations finish
            try {
                Thread.sleep(5000);
            } catch (Exception e) {
                e.printStackTrace();
            }

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
        protected void onPostExecute(Boolean result) {
            super.onPostExecute(result);

            mDialog.dismiss();
            if (result) {
                setStatusBasedOnAccount();
            }
        }
    }

    public class RemoveTask extends AsyncTask<String, Void, Boolean> {
        Context mContext;
        ProgressDialog mDialog;

        RemoveTask(Context context) {
            mContext = context;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();

            mDialog = ProgressDialog.show(mContext, "", mContext.getString(R.string.removing),
                    true, false);
            mDialog.setCancelable(false);
        }

        @Override
        protected Boolean doInBackground(String... params) {
            // remove account
            boolean result = AccountUtils.removeAccount(mContext);

            // Wait while asynchronous android background operations finish
            try {
                Thread.sleep(5000);
            } catch (Exception e) {
                e.printStackTrace();
            }

            return result;
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
}