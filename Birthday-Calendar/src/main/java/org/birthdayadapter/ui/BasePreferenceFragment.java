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
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.ContentUris;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.CalendarContract;
import android.provider.ContactsContract;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreferenceCompat;

import org.birthdayadapter.R;
import org.birthdayadapter.util.AccountHelper;
import org.birthdayadapter.util.Constants;
import org.birthdayadapter.util.PreferencesHelper;

import java.util.Calendar;

public class BasePreferenceFragment extends PreferenceFragmentCompat {
    private AccountHelper mAccountHelper;

    private SwitchPreferenceCompat mEnabled;

    public static final int MY_PERMISSIONS_REQUEST = 42;

    @Override
    public void onCreatePreferences(Bundle bundle, String s) {
        getPreferenceManager().setSharedPreferencesName(Constants.PREFS_NAME);
        addPreferencesFromResource(R.xml.base_preferences);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // All setup logic that requires a context/activity must go here, AFTER the view is created.
        BaseActivity mActivity = (BaseActivity) getActivity();
        if (mActivity == null) {
            // This should not happen, but as a safeguard.
            return;
        }

        mAccountHelper = new AccountHelper(mActivity, mActivity.mBackgroundStatusHandler);

        // if this is the first run, enable and sync birthday adapter!
        if (PreferencesHelper.getFirstRun(mActivity)) {
            PreferencesHelper.setFirstRun(mActivity, false);
            addAccountAndSync();
        }

        mEnabled = findPreference(getString(R.string.pref_enabled_key));
        if (mEnabled != null) {
            mEnabled.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    if (newValue instanceof Boolean) {
                        if ((Boolean) newValue) {
                            addAccountAndSync();
                        } else {
                            mAccountHelper.removeAccount();
                        }
                    }
                    return true;
                }
            });
        }

        Preference openContacts = findPreference(getString(R.string.pref_contacts_key));
        if (openContacts != null) {
            openContacts.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    Intent intent = new Intent(Intent.ACTION_VIEW, ContactsContract.Contacts.CONTENT_URI);
                    startActivity(intent);
                    return true;
                }
            });
        }

        Preference openCalendar = findPreference(getString(R.string.pref_calendar_key));
        if (openCalendar != null) {
            openCalendar.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @SuppressLint("NewApi")
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    Uri.Builder builder = CalendarContract.CONTENT_URI.buildUpon();
                    builder.appendPath("time");
                    ContentUris.appendId(builder, Calendar.getInstance().getTimeInMillis());
                    Intent intent = new Intent(Intent.ACTION_VIEW).setData(builder.build());
                    startActivity(intent);
                    return true;
                }
            });
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == MY_PERMISSIONS_REQUEST) {
            if (grantResults.length > 0) {
                for (int res : grantResults) {
                    if (res != PackageManager.PERMISSION_GRANTED) {
                        if (mEnabled != null) {
                            mEnabled.setChecked(false);
                        }
                        return;
                    }
                }

                // permission was granted
                mAccountHelper.addAccountAndSync();
                if (mEnabled != null) {
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
        if (getActivity() == null) return false;

        int contactsPerm = ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.READ_CONTACTS);
        int calendarPerm = ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.READ_CALENDAR);

        if (contactsPerm == PackageManager.PERMISSION_GRANTED && calendarPerm == PackageManager.PERMISSION_GRANTED) {
            return true;
        } else {
            requestPermissions(
                    new String[]{
                            Manifest.permission.GET_ACCOUNTS,
                            Manifest.permission.READ_CONTACTS,
                            Manifest.permission.WRITE_CONTACTS,
                            Manifest.permission.READ_CALENDAR,
                            Manifest.permission.WRITE_CALENDAR},
                    MY_PERMISSIONS_REQUEST);
            return false;
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mAccountHelper != null && mEnabled != null) {
            mEnabled.setChecked(mAccountHelper.isAccountActivated());
        }
    }
}
