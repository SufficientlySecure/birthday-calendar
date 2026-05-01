package fr.heinisch.birthdayadapter.util;

import android.app.Activity;

/**
 * No-op implementation of ReviewHelper for non-Google Play versions.
 */
public class ReviewHelperImpl implements ReviewHelper {

    @Override
    public void maybeShowReviewDialog(Activity activity) {
        // Do nothing as Google Play Review API is not available/needed here
    }
}
