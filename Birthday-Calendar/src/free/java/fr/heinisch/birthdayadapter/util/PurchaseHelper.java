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
import com.android.billingclient.api.ProductDetailsResponseListener;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesResponseListener;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.QueryProductDetailsParams;
import com.android.billingclient.api.QueryProductDetailsResult;
import com.android.billingclient.api.QueryPurchasesParams;

import fr.heinisch.birthdayadapter.BuildConfig;
import fr.heinisch.birthdayadapter.util.AccountHelper;
import fr.heinisch.birthdayadapter.util.Constants;
import fr.heinisch.birthdayadapter.util.Log;
import fr.heinisch.birthdayadapter.util.VersionHelper;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

// Final fix: Adjust to the exact listener signature as per compiler error
public class PurchaseHelper {

    private static final String SKU_FULL_VERSION = "full_version"; // IMPORTANT: Replace with your real product ID

    public static void launchBillingFlow(Activity activity) {
        Log.i(Constants.TAG, "launchBillingFlow called.");
        if (!BuildConfig.GOOGLE_PLAY_VERSION) {
            Log.w(Constants.TAG, "Billing flow not launched: GOOGLE_PLAY_VERSION is false.");
            return;
        }

        final BillingClient[] billingClientHolder = new BillingClient[1];

        PurchasesUpdatedListener purchasesUpdatedListener = (billingResult, purchases) -> {
            Log.d(Constants.TAG, "PurchasesUpdatedListener invoked.");
            final BillingClient client = billingClientHolder[0];
            if (client == null) {
                Log.e(Constants.TAG, "BillingClient was null in PurchasesUpdatedListener.");
                return;
            }

            if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK && purchases != null && !purchases.isEmpty()) {
                handlePurchase(activity, client, purchases.get(0), client::endConnection);
            } else {
                Log.w(Constants.TAG, "Purchase failed or was cancelled. Response code: " + billingResult.getResponseCode());
                client.endConnection();
            }
        };

        BillingClient billingClient = BillingClient.newBuilder(activity)
                .setListener(purchasesUpdatedListener)
                .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
                .build();
        billingClientHolder[0] = billingClient;

        Log.d(Constants.TAG, "Starting BillingClient connection...");
        billingClient.startConnection(new BillingClientStateListener() {
            @Override
            public void onBillingSetupFinished(@NonNull BillingResult billingResult) {
                Log.d(Constants.TAG, "onBillingSetupFinished, response code: " + billingResult.getResponseCode());
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

                billingClient.queryProductDetailsAsync(params, new ProductDetailsResponseListener() {
                    @Override
                    public void onProductDetailsResponse(BillingResult br, QueryProductDetailsResult productDetailsResult) {
                        Log.d(Constants.TAG, "onProductDetailsResponse, response code: " + br.getResponseCode());
                        List<ProductDetails> productDetailsList = productDetailsResult.getProductDetailsList();
                        boolean flowLaunched = false;

                        if (br.getResponseCode() == BillingClient.BillingResponseCode.OK && productDetailsList != null && !productDetailsList.isEmpty()) {
                            Log.d(Constants.TAG, "Product details query successful. Found " + productDetailsList.size() + " products.");
                            for (ProductDetails productDetails : productDetailsList) {
                                Log.d(Constants.TAG, "Checking product: " + productDetails.getProductId());
                                if (productDetails.getProductId().equals(SKU_FULL_VERSION)) {
                                    Log.i(Constants.TAG, "Product '" + SKU_FULL_VERSION + "' found. Launching billing flow...");
                                    BillingFlowParams.ProductDetailsParams productDetailsParams = BillingFlowParams.ProductDetailsParams.newBuilder()
                                            .setProductDetails(productDetails)
                                            .build();
                                    BillingFlowParams flowParams = BillingFlowParams.newBuilder()
                                            .setProductDetailsParamsList(Collections.singletonList(productDetailsParams))
                                            .build();
                                    billingClient.launchBillingFlow(activity, flowParams);
                                    flowLaunched = true;
                                    break;
                                }
                            }
                        } else {
                            Log.e(Constants.TAG, "Product details query failed or returned no results. Response code: " + br.getResponseCode());
                        }

                        if (!flowLaunched) {
                            Log.w(Constants.TAG, "Did not launch billing flow. Closing connection.");
                            billingClient.endConnection();
                        }
                    }
                });
            }

            @Override
            public void onBillingServiceDisconnected() {
                Log.w(Constants.TAG, "Billing service disconnected. Will try to reconnect on next action.");
            }
        });
    }

    public static void verifyAndRestorePurchases(Context context) {
        if (!BuildConfig.GOOGLE_PLAY_VERSION) return;
        Log.d(Constants.TAG, "Verifying and restoring purchases.");

        final BillingClient billingClient = BillingClient.newBuilder(context)
                .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
                .setListener((result, list) -> {}) // Dummy listener, not used for queries.
                .build();

        billingClient.startConnection(new BillingClientStateListener() {
            @Override
            public void onBillingSetupFinished(@NonNull BillingResult billingResult) {
                if (billingResult.getResponseCode() != BillingClient.BillingResponseCode.OK) {
                    billingClient.endConnection();
                    return;
                }

                QueryPurchasesParams params = QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build();
                billingClient.queryPurchasesAsync(params, new PurchasesResponseListener() {
                    @Override
                    public void onQueryPurchasesResponse(BillingResult br, List<Purchase> purchases) {
                        if (br.getResponseCode() != BillingClient.BillingResponseCode.OK || purchases == null || purchases.isEmpty()) {
                            billingClient.endConnection();
                            return;
                        }

                        final AtomicInteger pendingOperations = new AtomicInteger(purchases.size());
                        Runnable onFinishedListener = () -> {
                            if (pendingOperations.decrementAndGet() == 0) {
                                billingClient.endConnection();
                            }
                        };

                        for (Purchase purchase : purchases) {
                            if (purchase.getProducts().contains(SKU_FULL_VERSION)) {
                                handlePurchase(context, billingClient, purchase, onFinishedListener);
                            } else {
                                onFinishedListener.run(); // Count down for non-relevant purchases too.
                            }
                        }
                    }
                });
            }

            @Override
            public void onBillingServiceDisconnected() {
                 Log.w(Constants.TAG, "Billing service disconnected during purchase verification.");
            }
        });
    }

    private static void handlePurchase(Context context, BillingClient billingClient, Purchase purchase, Runnable onFinishedListener) {
        if (purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
            if (!purchase.isAcknowledged()) {
                Log.d(Constants.TAG, "Acknowledging purchase: " + purchase.getPurchaseToken());
                AcknowledgePurchaseParams acknowledgeParams = AcknowledgePurchaseParams.newBuilder()
                        .setPurchaseToken(purchase.getPurchaseToken())
                        .build();
                if (billingClient != null) {
                    billingClient.acknowledgePurchase(acknowledgeParams, billingResult -> {
                        if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                            Log.i(Constants.TAG, "Purchase acknowledged successfully.");
                            unlockFullVersion(context);
                        } else {
                            Log.e(Constants.TAG, "Error acknowledging purchase. Response code: " + billingResult.getResponseCode());
                        }
                        if (onFinishedListener != null) {
                            onFinishedListener.run();
                        }
                    });
                } else if (onFinishedListener != null) {
                    onFinishedListener.run();
                }
            } else {
                Log.d(Constants.TAG, "Purchase already acknowledged.");
                unlockFullVersion(context);
                if (onFinishedListener != null) {
                    onFinishedListener.run();
                }
            }
        } else {
            Log.d(Constants.TAG, "Purchase not in PURCHASED state: " + purchase.getPurchaseState());
            if (onFinishedListener != null) {
                onFinishedListener.run();
            }
        }
    }

    private static void unlockFullVersion(Context context) {
        if (VersionHelper.isFullVersionUnlocked(context)) return;
        
        VersionHelper.setFullVersionUnlocked(context, true);
        new AccountHelper(context).differentialSync();
        Log.i(Constants.TAG, "Full version unlocked.");

        if (context instanceof Activity) {
            ((Activity) context).runOnUiThread(((Activity) context)::recreate);
        }
    }
}
