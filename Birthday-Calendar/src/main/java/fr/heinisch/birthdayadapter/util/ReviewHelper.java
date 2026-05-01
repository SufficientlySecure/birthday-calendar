package fr.heinisch.birthdayadapter.util;

import android.app.Activity;

/**
 * Interface for managing In-App Review dialogs.
 */
public interface ReviewHelper {
    /**
     * Triggers the In-App Review flow if requirements are met (e.g., full version unlocked).
     *
     * @param activity The activity from which the review flow is initiated.
     */
    void maybeShowReviewDialog(Activity activity);
}
