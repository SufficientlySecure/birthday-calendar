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
import java.util.concurrent.atomic.AtomicInteger;

import fr.heinisch.birthdayadapter.BuildConfig;
import fr.heinisch.birthdayadapter.util.Log;

public class PurchaseHelperImpl implements IPurchaseHelper {

    private static final String SKU_FULL_VERSION = "full_version";

    @Override
    public void launchBillingFlow(Activity activity) {
        Log.d(Constants.TAG, "launchBillingFlow called.");
        if (!BuildConfig.GOOGLE_PLAY_VERSION) {
            Log.d(Constants.TAG, "Billing flow aborted: GOOGLE_PLAY_VERSION is false.");
            return;
        }

        final BillingClient[] billingClientHolder = new BillingClient[1];

        PurchasesUpdatedListener purchasesUpdatedListener = (billingResult, purchases) -> {
            Log.d(Constants.TAG, "PurchasesUpdatedListener invoked with response code: " + billingResult.getResponseCode());
            final BillingClient client = billingClientHolder[0];
            if (client == null) {
                Log.e(Constants.TAG, "BillingClient was null in PurchasesUpdatedListener.");
                return;
            }

            if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK && purchases != null && !purchases.isEmpty()) {
                handlePurchase(activity, client, purchases.get(0), client::endConnection);
            } else {
                Log.w(Constants.TAG, "Purchase failed, was cancelled, or is pending. Response code: " + billingResult.getResponseCode());
                client.endConnection();
            }
        };

        BillingClient billingClient = BillingClient.newBuilder(activity)
                .setListener(purchasesUpdatedListener)
                .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
                .build();
        billingClientHolder[0] = billingClient;

        Log.d(Constants.TAG, "Starting BillingClient connection for billing flow...");
        billingClient.startConnection(new BillingClientStateListener() {
            @Override
            public void onBillingSetupFinished(@NonNull BillingResult billingResult) {
                Log.d(Constants.TAG, "Billing flow setup finished with response code: " + billingResult.getResponseCode());
                if (billingResult.getResponseCode() != BillingClient.BillingResponseCode.OK) {
                    Log.e(Constants.TAG, "Billing setup failed. Closing connection.");
                    billingClient.endConnection();
                    return;
                }

                Log.d(Constants.TAG, "Billing setup successful. Querying product details...");
                QueryProductDetailsParams.Product product = QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(SKU_FULL_VERSION)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build();
                QueryProductDetailsParams params = QueryProductDetailsParams.newBuilder().setProductList(Collections.singletonList(product)).build();

                billingClient.queryProductDetailsAsync(params, (br, productDetailsResult) -> {
                    Log.d(Constants.TAG, "Product details query finished with response code: " + br.getResponseCode());
                    boolean flowLaunched = false;
                    List<ProductDetails> productDetailsList = productDetailsResult.getProductDetailsList();
                    if (br.getResponseCode() == BillingClient.BillingResponseCode.OK && productDetailsList != null && !productDetailsList.isEmpty()) {
                        for (ProductDetails productDetails : productDetailsList) {
                            if (productDetails.getProductId().equals(SKU_FULL_VERSION)) {
                                Log.d(Constants.TAG, "Product '" + SKU_FULL_VERSION + "' found. Launching billing flow...");
                                BillingFlowParams.ProductDetailsParams.Builder productDetailsParamsBuilder = BillingFlowParams.ProductDetailsParams.newBuilder()
                                        .setProductDetails(productDetails);
                                ProductDetails.OneTimePurchaseOfferDetails offerDetails = productDetails.getOneTimePurchaseOfferDetails();
                                if (offerDetails != null) {
                                    productDetailsParamsBuilder.setOfferToken(offerDetails.getOfferToken());
                                }

                                BillingFlowParams flowParams = BillingFlowParams.newBuilder()
                                        .setProductDetailsParamsList(Collections.singletonList(productDetailsParamsBuilder.build()))
                                        .build();
                                billingClient.launchBillingFlow(activity, flowParams);
                                flowLaunched = true;
                                break;
                            }
                        }
                    } else {
                        Log.e(Constants.TAG, "Product details query failed or returned no results.");
                    }

                    if (!flowLaunched) {
                        Log.w(Constants.TAG, "Did not launch billing flow. Closing connection.");
                        billingClient.endConnection();
                    }
                });
            }

            @Override
            public void onBillingServiceDisconnected() {
                Log.w(Constants.TAG, "Billing service disconnected during billing flow.");
            }
        });
    }

    @Override
    public void queryProductDetails(Activity activity, PriceCallback callback) {
        Log.d(Constants.TAG, "queryProductDetails called.");
        BillingClient billingClient = BillingClient.newBuilder(activity)
                .setListener((billingResult, purchases) -> {
                    // Purchases can be handled in launchBillingFlow
                })
                .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
                .build();

        billingClient.startConnection(new BillingClientStateListener() {
            @Override
            public void onBillingSetupFinished(@NonNull BillingResult billingResult) {
                if (billingResult.getResponseCode() != BillingClient.BillingResponseCode.OK) {
                    Log.e(Constants.TAG, "Billing setup failed. Closing connection.");
                    billingClient.endConnection();
                    return;
                }

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
                                    callback.onPriceFound(offerDetails.getFormattedPrice());
                                }
                                break;
                            }
                        }
                    } else {
                        Log.e(Constants.TAG, "Product details query failed or returned no results.");
                    }
                    billingClient.endConnection();
                });
            }

            @Override
            public void onBillingServiceDisconnected() {
                Log.w(Constants.TAG, "Billing service disconnected during query.");
            }
        });
    }

    @Override
    public void verifyAndRestorePurchases(Context context) {
        Log.d(Constants.TAG, "verifyAndRestorePurchases: Starting verification.");
        if (!BuildConfig.GOOGLE_PLAY_VERSION) {
            Log.w(Constants.TAG, "verifyAndRestorePurchases: Skipped, GOOGLE_PLAY_VERSION is false.");
            return;
        }

        final BillingClient billingClient = BillingClient.newBuilder(context)
                .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
                .setListener((result, list) -> {
                }) // Dummy listener, not used for queries.
                .build();

        Log.d(Constants.TAG, "verifyAndRestorePurchases: Starting BillingClient connection...");
        billingClient.startConnection(new BillingClientStateListener() {
            @Override
            public void onBillingSetupFinished(@NonNull BillingResult billingResult) {
                Log.d(Constants.TAG, "verifyAndRestorePurchases: onBillingSetupFinished response: " + billingResult.getResponseCode());
                if (billingResult.getResponseCode() != BillingClient.BillingResponseCode.OK) {
                    Log.e(Constants.TAG, "verifyAndRestorePurchases: Billing setup failed. Closing connection.");
                    billingClient.endConnection();
                    return;
                }

                QueryPurchasesParams params = QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build();
                billingClient.queryPurchasesAsync(params, (br, purchases) -> {
                    Log.d(Constants.TAG, "verifyAndRestorePurchases: queryPurchasesAsync response: " + br.getResponseCode());
                    if (br.getResponseCode() != BillingClient.BillingResponseCode.OK || purchases == null || purchases.isEmpty()) {
                        Log.i(Constants.TAG, "verifyAndRestorePurchases: No active purchases found or query failed.");
                        billingClient.endConnection();
                        return;
                    }

                    Log.d(Constants.TAG, "verifyAndRestorePurchases: Found " + purchases.size() + " purchase(s). Processing...");
                    final AtomicInteger pendingOperations = new AtomicInteger(purchases.size());
                    Runnable onFinishedListener = () -> {
                        if (pendingOperations.decrementAndGet() == 0) {
                            Log.d(Constants.TAG, "verifyAndRestorePurchases: All purchase processing finished. Closing connection.");
                            billingClient.endConnection();
                        }
                    };

                    for (Purchase purchase : purchases) {
                        if (purchase.getProducts().contains(SKU_FULL_VERSION)) {
                            Log.i(Constants.TAG, "verifyAndRestorePurchases: Found matching full version purchase.");
                            handlePurchase(context, billingClient, purchase, onFinishedListener);
                        } else {
                            onFinishedListener.run();
                        }
                    }
                });
            }

            @Override
            public void onBillingServiceDisconnected() {
                Log.w(Constants.TAG, "verifyAndRestorePurchases: Billing service disconnected.");
            }
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
