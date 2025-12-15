package org.birthdayadapter.util;

import android.app.Activity;
import android.content.Context;
import android.widget.Toast;

import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesResponseListener;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.SkuDetails;
import com.android.billingclient.api.SkuDetailsParams;

import org.birthdayadapter.BuildConfig;
import org.birthdayadapter.util.Constants;
import org.birthdayadapter.util.Log;

import java.util.Collections;
import java.util.List;

public class PurchaseHelper {

    private static final String SKU_FULL_VERSION = "full_version"; // Replace with your actual SKU

    private static BillingClient billingClient;

    public static void launchBillingFlow(Activity activity) {
        if (!BuildConfig.GOOGLE_PLAY_VERSION) {
            return;
        }

        PurchasesUpdatedListener purchasesUpdatedListener = (billingResult, purchases) -> {
            if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK && purchases != null) {
                for (Purchase purchase : purchases) {
                    if (purchase.getSkus().contains(SKU_FULL_VERSION) && purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
                        VersionHelper.setFullVersionUnlocked(activity, true);
                        new AccountHelper(activity).differentialSync();
                    }
                }
            }
        };

        billingClient = BillingClient.newBuilder(activity)
                .setListener(purchasesUpdatedListener)
                .enablePendingPurchases()
                .build();

        billingClient.startConnection(new BillingClientStateListener() {
            @Override
            public void onBillingSetupFinished(BillingResult billingResult) {
                if (billingResult.getResponseCode() ==  BillingClient.BillingResponseCode.OK) {
                    SkuDetailsParams.Builder params = SkuDetailsParams.newBuilder();
                    params.setSkusList(Collections.singletonList(SKU_FULL_VERSION)).setType(BillingClient.SkuType.INAPP);
                    billingClient.querySkuDetailsAsync(params.build(), (billingResult1, skuDetailsList) -> {
                        if (billingResult1.getResponseCode() == BillingClient.BillingResponseCode.OK && skuDetailsList != null) {
                            for (SkuDetails skuDetails : skuDetailsList) {
                                if (skuDetails.getSku().equals(SKU_FULL_VERSION)) {
                                    BillingFlowParams billingFlowParams = BillingFlowParams.newBuilder()
                                            .setSkuDetails(skuDetails)
                                            .build();
                                    billingClient.launchBillingFlow(activity, billingFlowParams);
                                }
                            }
                        }
                    });
                }
            }
            @Override
            public void onBillingServiceDisconnected() {
                // Try to restart the connection on the next operation.
            }
        });
    }

    public static void verifyAndRestorePurchases(Context context) {
        if (!BuildConfig.GOOGLE_PLAY_VERSION) {
            return;
        }

        billingClient = BillingClient.newBuilder(context).enablePendingPurchases().setListener((billingResult, list) -> {}).build();
        billingClient.startConnection(new BillingClientStateListener() {
            @Override
            public void onBillingSetupFinished(BillingResult billingResult) {
                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    billingClient.queryPurchasesAsync(BillingClient.SkuType.INAPP, (billingResult1, purchases) -> {
                        if (billingResult1.getResponseCode() == BillingClient.BillingResponseCode.OK && purchases != null) {
                            for (Purchase purchase : purchases) {
                                if (purchase.getSkus().contains(SKU_FULL_VERSION) && purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
                                    if (!purchase.isAcknowledged()) {
                                        AcknowledgePurchaseParams acknowledgePurchaseParams =
                                                AcknowledgePurchaseParams.newBuilder()
                                                        .setPurchaseToken(purchase.getPurchaseToken())
                                                        .build();
                                        billingClient.acknowledgePurchase(acknowledgePurchaseParams, billingResult2 -> {});
                                    }
                                    VersionHelper.setFullVersionUnlocked(context, true);
                                    new AccountHelper(context).differentialSync();
                                }
                            }
                        }
                    });
                }
            }

            @Override
            public void onBillingServiceDisconnected() {
                // Try to restart the connection on the next operation
            }
        });
    }
}
