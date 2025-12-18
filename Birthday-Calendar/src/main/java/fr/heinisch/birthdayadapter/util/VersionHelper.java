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
