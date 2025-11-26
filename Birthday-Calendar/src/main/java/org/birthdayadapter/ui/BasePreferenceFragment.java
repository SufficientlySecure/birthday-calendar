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
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.CalendarContract;
import android.provider.ContactsContract;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
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

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class BasePreferenceFragment extends PreferenceFragmentCompat {
    private AccountHelper mAccountHelper;

    private SwitchPreferenceCompat mEnabled;

    private ActivityResultLauncher<String[]> requestPermissionLauncher;

    private static final String[] REQUIRED_PERMISSIONS = new String[]{
            Manifest.permission.GET_ACCOUNTS,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.WRITE_CONTACTS,
            Manifest.permission.READ_CALENDAR,
            Manifest.permission.WRITE_CALENDAR
    };

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestPermissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), permissions -> {
            boolean allGranted = true;
            for (Boolean isGranted : permissions.values()) {
                if (!isGranted) {
                    allGranted = false;
                    break;
                }
            }

            if (allGranted) {
                // All permissions granted, now add the account
                mAccountHelper.addAccountAndSync();
            } else {
                // At least one permission denied, disable the feature
                if (mEnabled != null) {
                    mEnabled.setChecked(false);
                }
                Context context = getContext();
                if (context != null) {
                    Toast.makeText(context, R.string.permission_denied, Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    @Override
    public void onCreatePreferences(Bundle bundle, String s) {
        getPreferenceManager().setSharedPreferencesName(Constants.PREFS_NAME);
        addPreferencesFromResource(R.xml.base_preferences);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        BaseActivity mActivity = (BaseActivity) getActivity();
        if (mActivity == null) {
            return;
        }

        mAccountHelper = new AccountHelper(mActivity);

        mEnabled = findPreference(getString(R.string.pref_enabled_key));
        if (mEnabled != null) {
            mEnabled.setOnPreferenceChangeListener((preference, newValue) -> {
                if (newValue instanceof Boolean) {
                    if ((Boolean) newValue) {
                        // This will trigger the permission check if needed
                        checkAndRequestPermissions();
                    } else {
                        mAccountHelper.removeAccount();
                    }
                }
                // We return false because the permission launcher is asynchronous.
                // The switch state will be updated in the launcher's callback.
                return true;
            });
        }

        // On first run, set the switch to true and trigger the sync/permission flow
        if (PreferencesHelper.getFirstRun(mActivity)) {
            PreferencesHelper.setFirstRun(mActivity, false);
            // We directly call checkAndRequestPermissions which contains the permission check.
            // We also manually set the switch to checked state.
            if (mEnabled != null) {
                mEnabled.setChecked(true);
            }
        }

        Preference openContacts = findPreference(getString(R.string.pref_contacts_key));
        if (openContacts != null) {
            openContacts.setOnPreferenceClickListener(preference -> {
                Intent intent = new Intent(Intent.ACTION_VIEW, ContactsContract.Contacts.CONTENT_URI);
                startActivity(intent);
                return true;
            });
        }

        Preference openCalendar = findPreference(getString(R.string.pref_calendar_key));
        if (openCalendar != null) {
            openCalendar.setOnPreferenceClickListener(preference -> {
                Uri.Builder builder = CalendarContract.CONTENT_URI.buildUpon();
                builder.appendPath("time");
                ContentUris.appendId(builder, Calendar.getInstance().getTimeInMillis());
                Intent intent = new Intent(Intent.ACTION_VIEW).setData(builder.build());
                startActivity(intent);
                return true;
            });
        }
    }

    private void checkAndRequestPermissions() {
        Context context = getContext();
        if (context == null) return;

        List<String> permissionsToRequest = new ArrayList<>();
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(permission);
            }
        }

        if (permissionsToRequest.isEmpty()) {
            // All permissions are already granted
            mAccountHelper.addAccountAndSync();
        } else {
            // Request the missing permissions
            requestPermissionLauncher.launch(permissionsToRequest.toArray(new String[0]));
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
