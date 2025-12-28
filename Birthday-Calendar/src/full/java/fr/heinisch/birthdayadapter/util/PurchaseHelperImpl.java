package fr.heinisch.birthdayadapter.util;

import android.app.Activity;
import android.content.Context;

public class PurchaseHelperImpl implements IPurchaseHelper {
    @Override
    public void launchBillingFlow(Activity activity) {
        // Not used in the full version
    }

    @Override
    public void verifyAndRestorePurchases(Context context) {
        // Not used in the full version
    }
}
