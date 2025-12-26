package fr.heinisch.birthdayadapter.util;

import android.app.Activity;
import android.content.Context;

public interface IPurchaseHelper {
    void launchBillingFlow(Activity activity);

    void verifyAndRestorePurchases(Context context);
}
