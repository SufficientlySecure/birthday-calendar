/*
 * Copyright (C) 2025-2026 Matthias Heinisch <birthdayadapter@heinisch.fr>
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

package fr.heinisch.birthdayadapter.util;

import static java.util.concurrent.Executors.newSingleThreadExecutor;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.Log;

import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

import java.util.concurrent.ExecutorService;

import fr.heinisch.birthdayadapter.R;

public class MigrationManager {

    private static final String LAST_SEEN_VERSION_CODE = "last_seen_version_code";

    private static final String[] REQUIRED_PERMISSIONS = new String[]{
            Manifest.permission.GET_ACCOUNTS,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.READ_CALENDAR,
            Manifest.permission.WRITE_CALENDAR
    };

    public static void migrate(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        long lastSeenVersionCode = prefs.getLong(LAST_SEEN_VERSION_CODE, 0);
        long currentVersionCode;

        try {
            PackageInfo pInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            currentVersionCode = pInfo.getLongVersionCode();
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(Constants.TAG, "Could not get package info!", e);
            return; // Can't perform migration without version info
        }

        // Only run migrations if this is an update from a previous version
        if (lastSeenVersionCode > 0 && lastSeenVersionCode < currentVersionCode) {
            Log.i(Constants.TAG, "Starting migration from version " + lastSeenVersionCode + " to " + currentVersionCode);

            // Migration for users from versions before onboarding was introduced
            if (!prefs.getBoolean("has_seen_onboarding", false) && areAllPermissionsGranted(context)) {
                skipOnboarding(context, prefs);
            }

            // Future migrations can be added here...

            Log.i(Constants.TAG, "Migration finished.");
        }

        // After all migrations are done (or if it's a fresh install), update the version code
        prefs.edit().putLong(LAST_SEEN_VERSION_CODE, currentVersionCode).apply();
    }

    private static void skipOnboarding(Context context, SharedPreferences prefs) {
        Log.i(Constants.TAG, "Skipping onboarding for legacy user.");
        // This is an existing user with all permissions. Mark onboarding as seen and enable the adapter.
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean("has_seen_onboarding", true);
        editor.putBoolean(context.getString(R.string.pref_enabled_key), true);
        editor.apply();

        // Activate the account in the background
        AccountHelper accountHelper = new AccountHelper(context);
        ExecutorService executor = newSingleThreadExecutor();
        executor.execute(accountHelper::addAccountAndSync);
        Log.i(Constants.TAG, "Legacy user migration finished.");
    }

    private static boolean areAllPermissionsGranted(Context context) {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }
}
