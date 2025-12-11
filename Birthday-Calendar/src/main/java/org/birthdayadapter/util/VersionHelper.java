package org.birthdayadapter.util;

import org.birthdayadapter.BuildConfig;

public class VersionHelper {

    /**
     * Checks if the full version of the app is unlocked, either by build flavor or by an in-app purchase.
     * <p>
     * This method is prepared for a future in-app purchase implementation. Currently, it only checks the build-time flag.
     *
     * @return {@code true} if the full version is unlocked, {@code false} otherwise.
     */
    public static boolean isFullVersionUnlocked() {
        // TODO: Add logic to check for a successful in-app purchase from SharedPreferences.
        // For example: `boolean hasPurchased = a.getBoolean("has_purchased_full_version", false);`
        // return BuildConfig.FULL_VERSION || hasPurchased;

        return BuildConfig.FULL_VERSION;
    }
}
