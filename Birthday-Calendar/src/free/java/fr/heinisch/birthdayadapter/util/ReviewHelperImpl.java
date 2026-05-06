package fr.heinisch.birthdayadapter.util;

import android.app.Activity;

import com.google.android.play.core.review.ReviewInfo;
import com.google.android.play.core.review.ReviewManager;
import com.google.android.play.core.review.ReviewManagerFactory;
import com.google.android.gms.tasks.Task;

/**
 * Google Play implementation of the ReviewHelper.
 */
public class ReviewHelperImpl implements ReviewHelper {

    private boolean wasRequestedInThisSession = false;

    @Override
    public void maybeShowReviewDialog(Activity activity) {
        // Only trigger for users who have the full version unlocked and haven't seen it this session
        if (wasRequestedInThisSession || !VersionHelper.isFullVersionUnlocked(activity)) {
            return;
        }

        ReviewManager manager = ReviewManagerFactory.create(activity);
        Task<ReviewInfo> request = manager.requestReviewFlow();
        request.addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                // ReviewInfo object received successfully
                ReviewInfo reviewInfo = task.getResult();
                Task<Void> flow = manager.launchReviewFlow(activity, reviewInfo);
                flow.addOnCompleteListener(reviewTask -> {
                    // Review flow finished (regardless of whether the user actually left a review)
                    wasRequestedInThisSession = true;
                });
            }
        });
    }
}
