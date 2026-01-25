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
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.CalendarContract;
import android.provider.ContactsContract;
import android.text.format.DateUtils;
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
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import fr.heinisch.birthdayadapter.R;
import fr.heinisch.birthdayadapter.util.AccountHelper;
import fr.heinisch.birthdayadapter.util.Constants;
import fr.heinisch.birthdayadapter.util.SyncStatusManager;

public class BasePreferenceFragment extends PreferenceFragmentCompat {
    private AccountHelper mAccountHelper;

    private SwitchPreferenceCompat mEnabled;
    private Preference forceSyncPref;
    private SharedPreferences mSyncStatusPrefs;
    private WorkInfo mBirthdaySyncWorkInfo;

    private final Handler mSyncUpdateHandler = new Handler(Looper.getMainLooper());
    private Runnable mSyncUpdateRunnable;

    private final SharedPreferences.OnSharedPreferenceChangeListener mSyncStatusListener = (sharedPreferences, key) -> {
        if (key != null && key.equals("last_sync_timestamp") && getActivity() != null) {
            getActivity().runOnUiThread(this::updateSyncStatus);
        }
    };

    private ActivityResultLauncher<String[]> requestPermissionLauncher;

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private static final String[] REQUIRED_PERMISSIONS = new String[]{
            Manifest.permission.GET_ACCOUNTS,
            Manifest.permission.READ_CONTACTS,
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
                executorService.execute(() -> {
                    mAccountHelper.addAccountAndSync();
                    handler.post(() -> {
                        if (isAdded() && mEnabled != null) {
                            mEnabled.setChecked(true);
                        }
                    });
                });
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
        mSyncStatusPrefs = mActivity.getSharedPreferences("sync_status_prefs", Context.MODE_PRIVATE);

        mEnabled = findPreference(getString(R.string.pref_enabled_key));
        if (mEnabled != null) {
            mEnabled.setOnPreferenceChangeListener((preference, newValue) -> {
                if (newValue instanceof Boolean) {
                    if ((Boolean) newValue) {
                        checkAndRequestPermissions();
                        // Defer UI update until permissions are handled
                        return false;
                    } else {
                        executorService.execute(() -> mAccountHelper.removeAccount());
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

        forceSyncPref = findPreference(getString(R.string.pref_force_sync_key));
        if (forceSyncPref != null) {
            forceSyncPref.setOnPreferenceClickListener(preference -> {
                SyncStatusManager.getInstance().setSyncing(true);
                mAccountHelper.differentialSync();
                updateSyncStatus();
                return true;
            });
        }

        WorkManager.getInstance(mActivity).getWorkInfosForUniqueWorkLiveData("periodic_sync")
                .observe(getViewLifecycleOwner(), workInfos -> {
                    if (workInfos != null && !workInfos.isEmpty()) {
                        mBirthdaySyncWorkInfo = workInfos.get(0);
                    } else {
                        mBirthdaySyncWorkInfo = null;
                    }
                    updateSyncStatus();
                });
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
            executorService.execute(() -> {
                mAccountHelper.addAccountAndSync();
                handler.post(() -> {
                    if (isAdded() && mEnabled != null) {
                        mEnabled.setChecked(true);
                    }
                });
            });
        } else {
            // Request the missing permissions
            requestPermissionLauncher.launch(permissionsToRequest.toArray(new String[0]));
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executorService.shutdown();
    }

    @Override
    public void onPause() {
        super.onPause();
        mSyncStatusPrefs.unregisterOnSharedPreferenceChangeListener(mSyncStatusListener);
        // Stop the periodic UI updates
        mSyncUpdateHandler.removeCallbacks(mSyncUpdateRunnable);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mAccountHelper != null && mEnabled != null) {
            executorService.execute(() -> {
                final boolean isActivated = mAccountHelper.isAccountActivated();
                handler.post(() -> {
                    if (isAdded() && mEnabled != null) {
                        mEnabled.setChecked(isActivated);
                    }
                });
            });
        }

        mSyncStatusPrefs.registerOnSharedPreferenceChangeListener(mSyncStatusListener);

        mSyncUpdateRunnable = () -> {
            updateSyncStatus();
            // Rerun every minute
            mSyncUpdateHandler.postDelayed(mSyncUpdateRunnable, DateUtils.MINUTE_IN_MILLIS);
        };
        // Immediately run and start the cycle
        mSyncUpdateHandler.post(mSyncUpdateRunnable);
    }

    private void updateSyncStatus() {
        if (!isAdded() || forceSyncPref == null || getActivity() == null) return;

        long lastSync = mSyncStatusPrefs.getLong("last_sync_timestamp", 0);
        String summary;

        if (lastSync == 0) {
            summary = getString(R.string.last_sync_never);
        } else {
            summary = getString(R.string.last_sync, DateUtils.getRelativeTimeSpanString(lastSync, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS));
        }

        if (mAccountHelper != null && mAccountHelper.isAccountActivated()) {
            if (mBirthdaySyncWorkInfo != null) {
                long nextRun = mBirthdaySyncWorkInfo.getNextScheduleTimeMillis();
                long now = System.currentTimeMillis();
                long sanityThreshold = now + TimeUnit.DAYS.toMillis(Constants.SYNC_INTERVAL_DAYS * 2);

                if (nextRun > now && nextRun < sanityThreshold) {
                    summary += "\n" + getString(R.string.next_sync, DateUtils.getRelativeTimeSpanString(nextRun, now, DateUtils.MINUTE_IN_MILLIS));
                }
            }
        }

        forceSyncPref.setSummary(summary);
    }
}
