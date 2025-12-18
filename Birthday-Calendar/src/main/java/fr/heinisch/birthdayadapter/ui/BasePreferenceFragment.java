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

package fr.heinisch.birthdayadapter.ui;

import android.Manifest;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
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

import fr.heinisch.birthdayadapter.R;
import fr.heinisch.birthdayadapter.util.AccountHelper;
import fr.heinisch.birthdayadapter.util.Constants;
import fr.heinisch.birthdayadapter.util.PreferencesHelper;

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
            if (!isAdded()) {
                // Fragment is not attached to an activity, do nothing.
                return;
            }

            boolean essentialPermissionsGranted = true;
            Context context = getContext();
            if (context == null) {
                // Fragment not attached, do nothing.
                return;
            }

            for (String permission : REQUIRED_PERMISSIONS) {
                if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                    essentialPermissionsGranted = false;
                    break;
                }
            }

            if (essentialPermissionsGranted) {
                // All essential permissions granted, now add the account
                mAccountHelper.addAccountAndSync();
                if (mEnabled != null) {
                    mEnabled.setChecked(true);
                }
            } else {
                // At least one essential permission was denied, disable the feature
                if (mEnabled != null) {
                    mEnabled.setChecked(false);
                }
                Toast.makeText(context, R.string.permission_denied, Toast.LENGTH_LONG).show();
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
                        checkAndRequestPermissions();
                        // Defer UI update until permissions are handled
                        return false;
                    } else {
                        mAccountHelper.removeAccount();
                        // Allow UI update immediately for deactivation
                        return true;
                    }
                }
                return true;
            });
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
            if (mEnabled != null) {
                mEnabled.setChecked(true);
            }
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
