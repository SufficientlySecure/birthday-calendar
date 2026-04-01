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

import android.content.Context;
import android.content.SharedPreferences;
import androidx.preference.PreferenceManager;

import fr.heinisch.birthdayadapter.BuildConfig;

public class VersionHelper {

    public static final String PREF_FULL_VERSION_PURCHASED = "pref_full_version_purchased";

    /**
     * Checks if the full version of the app is unlocked, either by build flavor or by an in-app purchase.
     *
     * @param context The context to access SharedPreferences.
     * @return {@code true} if the full version is unlocked, {@code false} otherwise.
     */
    public static boolean isFullVersionUnlocked(Context context) {
        // The 'full' build flavor is always unlocked.
        if (BuildConfig.FULL_VERSION) {
            return true;
        }

        // For the 'free' flavor, check if the user has purchased the upgrade.
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getBoolean(PREF_FULL_VERSION_PURCHASED, false);
    }

    /**
     * Saves the purchase state of the full version upgrade.
     *
     * @param context   The context to access SharedPreferences.
     * @param purchased Whether the full version has been purchased.
     */
    public static void setFullVersionUnlocked(Context context, boolean purchased) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        prefs.edit().putBoolean(PREF_FULL_VERSION_PURCHASED, purchased).apply();
    }
}
