package fr.heinisch.birthdayadapter.util;

import android.app.Activity;
import android.content.Context;

public interface IPurchaseHelper {

    @FunctionalInterface
    interface PriceCallback {
        void onPriceFound(String price);
    }

    void launchBillingFlow(Activity activity);

    void queryProductDetails(Activity activity, PriceCallback callback);

    void verifyAndRestorePurchases(Context context);
}
