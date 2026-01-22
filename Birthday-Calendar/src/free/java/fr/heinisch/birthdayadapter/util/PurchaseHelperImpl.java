package fr.heinisch.birthdayadapter.util;

import android.app.Activity;
import android.content.Context;

import androidx.annotation.NonNull;

import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.PendingPurchasesParams;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.QueryProductDetailsParams;
import com.android.billingclient.api.QueryPurchasesParams;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import fr.heinisch.birthdayadapter.BuildConfig;
import fr.heinisch.birthdayadapter.util.Log;

public class PurchaseHelperImpl implements IPurchaseHelper {

    private static final String SKU_FULL_VERSION = "full_version";
    private BillingClient billingClient;
    private final AtomicBoolean isBillingClientConnecting = new AtomicBoolean(false);

    private void ensureBillingClient(Context context, Runnable onConnected) {
        if (billingClient != null && billingClient.isReady()) {
            if (onConnected != null) {
                onConnected.run();
            }
            return;
        }

        if (isBillingClientConnecting.compareAndSet(false, true)) {
            PurchasesUpdatedListener listener = (billingResult, purchases) -> {
                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK && purchases != null) {
                    for (Purchase purchase : purchases) {
                        handlePurchase(context, billingClient, purchase, () -> {});
                    }
                }
            };

            billingClient = BillingClient.newBuilder(context)
                    .setListener(listener)
                    .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
                    .build();

            billingClient.startConnection(new BillingClientStateListener() {
                @Override
                public void onBillingSetupFinished(@NonNull BillingResult billingResult) {
                    isBillingClientConnecting.set(false);
                    if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                        Log.d(Constants.TAG, "BillingClient setup finished.");
                        if (onConnected != null) {
                            onConnected.run();
                        }
                    } else {
                        Log.w(Constants.TAG, "BillingClient setup failed with code: " + billingResult.getResponseCode());
                    }
                }

                @Override
                public void onBillingServiceDisconnected() {
                    Log.w(Constants.TAG, "BillingClient disconnected.");
                    isBillingClientConnecting.set(false);
                    // Optionally, you could try to reconnect here with a backoff strategy.
                }
            });
        } else {
            // You could queue the runnable or simply log that a connection is already in progress.
            Log.d(Constants.TAG, "BillingClient connection already in progress.");
        }
    }


    @Override
    public void launchBillingFlow(Activity activity) {
        Log.d(Constants.TAG, "launchBillingFlow called.");
        if (!BuildConfig.GOOGLE_PLAY_VERSION) {
            Log.d(Constants.TAG, "Billing flow aborted: GOOGLE_PLAY_VERSION is false.");
            return;
        }

        ensureBillingClient(activity, () -> {
            QueryProductDetailsParams.Product product = QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(SKU_FULL_VERSION)
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build();
            QueryProductDetailsParams params = QueryProductDetailsParams.newBuilder().setProductList(Collections.singletonList(product)).build();

            billingClient.queryProductDetailsAsync(params, (br, productDetailsResult) -> {
                List<ProductDetails> productDetailsList = productDetailsResult.getProductDetailsList();
                if (br.getResponseCode() == BillingClient.BillingResponseCode.OK && productDetailsList != null && !productDetailsList.isEmpty()) {
                    for (ProductDetails productDetails : productDetailsList) {
                        if (productDetails.getProductId().equals(SKU_FULL_VERSION)) {
                            BillingFlowParams.ProductDetailsParams.Builder productDetailsParamsBuilder = BillingFlowParams.ProductDetailsParams.newBuilder()
                                    .setProductDetails(productDetails);
                            ProductDetails.OneTimePurchaseOfferDetails offerDetails = productDetails.getOneTimePurchaseOfferDetails();
                            if (offerDetails != null) {
                                productDetailsParamsBuilder.setOfferToken(offerDetails.getOfferToken());
                            }
                            BillingFlowParams flowParams = BillingFlowParams.newBuilder()
                                    .setProductDetailsParamsList(Collections.singletonList(productDetailsParamsBuilder.build()))
                                    .build();
                            activity.runOnUiThread(() -> billingClient.launchBillingFlow(activity, flowParams));
                            return;
                        }
                    }
                }
                Log.e(Constants.TAG, "Product details query failed or returned no results.");
            });
        });
    }

    @Override
    public void queryProductDetails(Activity activity, PriceCallback callback) {
        Log.d(Constants.TAG, "queryProductDetails called.");
        ensureBillingClient(activity, () -> {
            QueryProductDetailsParams.Product product = QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(SKU_FULL_VERSION)
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build();
            QueryProductDetailsParams params = QueryProductDetailsParams.newBuilder().setProductList(Collections.singletonList(product)).build();

            billingClient.queryProductDetailsAsync(params, (br, productDetailsResult) -> {
                List<ProductDetails> productDetailsList = productDetailsResult.getProductDetailsList();
                if (br.getResponseCode() == BillingClient.BillingResponseCode.OK && productDetailsList != null && !productDetailsList.isEmpty()) {
                    for (ProductDetails productDetails : productDetailsList) {
                        if (productDetails.getProductId().equals(SKU_FULL_VERSION)) {
                            ProductDetails.OneTimePurchaseOfferDetails offerDetails = productDetails.getOneTimePurchaseOfferDetails();
                            if (offerDetails != null) {
                                activity.runOnUiThread(() -> callback.onPriceFound(offerDetails.getFormattedPrice()));
                            }
                            return; // Exit after finding the product
                        }
                    }
                }
                Log.e(Constants.TAG, "Product details query failed or returned no results for price query.");
            });
        });
    }


    @Override
    public void verifyAndRestorePurchases(Context context) {
        Log.d(Constants.TAG, "verifyAndRestorePurchases: Starting verification.");
        if (!BuildConfig.GOOGLE_PLAY_VERSION) {
            Log.w(Constants.TAG, "verifyAndRestorePurchases: Skipped, GOOGLE_PLAY_VERSION is false.");
            return;
        }

        ensureBillingClient(context.getApplicationContext(), () -> {
            QueryPurchasesParams params = QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build();
            billingClient.queryPurchasesAsync(params, (br, purchases) -> {
                if (br.getResponseCode() == BillingClient.BillingResponseCode.OK && purchases != null) {
                    for (Purchase purchase : purchases) {
                        if (purchase.getProducts().contains(SKU_FULL_VERSION)) {
                            handlePurchase(context, billingClient, purchase, () -> {});
                        }
                    }
                } else {
                    Log.i(Constants.TAG, "verifyAndRestorePurchases: No active purchases found or query failed.");
                }
            });
        });
    }

    private void handlePurchase(Context context, BillingClient billingClient, Purchase purchase, Runnable onFinishedListener) {
        Log.d(Constants.TAG, "handlePurchase: State is " + purchase.getPurchaseState());
        if (purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
            if (!purchase.isAcknowledged()) {
                Log.d(Constants.TAG, "handlePurchase: Purchase is new. Acknowledging...");
                AcknowledgePurchaseParams acknowledgeParams = AcknowledgePurchaseParams.newBuilder()
                        .setPurchaseToken(purchase.getPurchaseToken())
                        .build();
                billingClient.acknowledgePurchase(acknowledgeParams, billingResult -> {
                    Log.d(Constants.TAG, "handlePurchase: Acknowledge response: " + billingResult.getResponseCode());
                    if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                        Log.i(Constants.TAG, "handlePurchase: Purchase acknowledged successfully. Unlocking full version.");
                        unlockFullVersion(context);
                    } else {
                        Log.e(Constants.TAG, "handlePurchase: Error acknowledging purchase.");
                    }
                    onFinishedListener.run();
                });
            } else {
                Log.i(Constants.TAG, "handlePurchase: Purchase already acknowledged. Unlocking full version.");
                unlockFullVersion(context);
                onFinishedListener.run();
            }
        } else {
            Log.w(Constants.TAG, "handlePurchase: Purchase is not in PURCHASED state (state: " + purchase.getPurchaseState() + ").");
            onFinishedListener.run();
        }
    }

    private void unlockFullVersion(Context context) {
        if (VersionHelper.isFullVersionUnlocked(context)) {
            Log.d(Constants.TAG, "unlockFullVersion: Already unlocked, no action taken.");
            return;
        }

        Log.i(Constants.TAG, "unlockFullVersion: Setting full version to purchased.");
        VersionHelper.setFullVersionUnlocked(context, true);

        if (context instanceof Activity) {
            Log.d(Constants.TAG, "unlockFullVersion: Recreating activity to apply changes.");
            ((Activity) context).runOnUiThread(((Activity) context)::recreate);
        }
    }
}
