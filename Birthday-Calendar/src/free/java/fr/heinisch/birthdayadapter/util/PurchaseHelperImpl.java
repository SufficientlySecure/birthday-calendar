/*
 * Copyright (C) 2025-2026 Matthias Heinisch <birthdayadapter@heinisch.fr>
 *
 * This file is part of Birthday Adapter.
 *
 * Birthday Adapter is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Birthday Adapter is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Birthday Adapter.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package fr.heinisch.birthdayadapter.util;

import static fr.heinisch.birthdayadapter.util.VersionHelper.isFullVersionUnlocked;

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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import fr.heinisch.birthdayadapter.BuildConfig;

public class PurchaseHelperImpl implements IPurchaseHelper {

    private static final String SKU_FULL_VERSION = "full_version";
    private BillingClient billingClient;
    private final AtomicBoolean isBillingClientConnecting = new AtomicBoolean(false);
    private final List<Runnable> pendingTasks = new ArrayList<>();

    private void ensureBillingClient(Context context, Runnable onConnected) {
        synchronized (pendingTasks) {
            if (billingClient != null && billingClient.isReady()) {
                if (onConnected != null) {
                    onConnected.run();
                }
                return;
            }

            if (onConnected != null) {
                pendingTasks.add(onConnected);
            }
        }

        if (isBillingClientConnecting.compareAndSet(false, true)) {
            Context appContext = context.getApplicationContext();

            PurchasesUpdatedListener listener = (billingResult, purchases) -> {
                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK && purchases != null) {
                    for (Purchase purchase : purchases) {
                        handlePurchase(appContext, billingClient, purchase, () -> {});
                    }
                } else if (billingResult.getResponseCode() != BillingClient.BillingResponseCode.USER_CANCELED) {
                    Log.w(Constants.TAG, "Purchases updated with error: " + billingResult.getResponseCode() + " - " + billingResult.getDebugMessage());
                }
            };

            billingClient = BillingClient.newBuilder(appContext)
                    .setListener(listener)
                    .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
                    .build();

            billingClient.startConnection(new BillingClientStateListener() {
                @Override
                public void onBillingSetupFinished(@NonNull BillingResult billingResult) {
                    isBillingClientConnecting.set(false);
                    List<Runnable> tasksToRun;
                    synchronized (pendingTasks) {
                        if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                            Log.d(Constants.TAG, "BillingClient setup finished.");
                            tasksToRun = new ArrayList<>(pendingTasks);
                        } else {
                            Log.w(Constants.TAG, "BillingClient setup failed: " + billingResult.getResponseCode() + " - " + billingResult.getDebugMessage());
                            tasksToRun = Collections.emptyList();
                        }
                        pendingTasks.clear();
                    }
                    for (Runnable task : tasksToRun) {
                        task.run();
                    }
                }

                @Override
                public void onBillingServiceDisconnected() {
                    Log.w(Constants.TAG, "BillingClient disconnected.");
                    isBillingClientConnecting.set(false);
                    synchronized (pendingTasks) {
                        pendingTasks.clear();
                    }
                }
            });
        } else {
            Log.d(Constants.TAG, "BillingClient connection already in progress. Task added to queue.");
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

            billingClient.queryProductDetailsAsync(params, (billingResult, result) -> {
                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    List<ProductDetails> productDetailsList = result.getProductDetailsList();
                    if (!productDetailsList.isEmpty()) {
                        for (ProductDetails productDetails : productDetailsList) {
                            if (productDetails.getProductId().equals(SKU_FULL_VERSION)) {
                                BillingFlowParams.ProductDetailsParams.Builder productDetailsParamsBuilder = BillingFlowParams.ProductDetailsParams.newBuilder()
                                        .setProductDetails(productDetails);
                                ProductDetails.OneTimePurchaseOfferDetails offerDetails = productDetails.getOneTimePurchaseOfferDetails();
                                if (offerDetails != null) {
                                    assert offerDetails.getOfferToken() != null;
                                    productDetailsParamsBuilder.setOfferToken(offerDetails.getOfferToken());
                                }
                                BillingFlowParams flowParams = BillingFlowParams.newBuilder()
                                        .setProductDetailsParamsList(Collections.singletonList(productDetailsParamsBuilder.build()))
                                        .build();
                                activity.runOnUiThread(() -> {
                                    if (!activity.isFinishing() && !activity.isDestroyed()) {
                                        billingClient.launchBillingFlow(activity, flowParams);
                                    }
                                });
                                return;
                            }
                        }
                    }
                }
                Log.e(Constants.TAG, "Product details query failed: " + billingResult.getResponseCode() + " - " + billingResult.getDebugMessage());
            });
        });
    }

    @Override
    public void queryProductDetails(Activity activity, OnPriceFoundCallback callback) {
        Log.d(Constants.TAG, "queryProductDetails called.");
        ensureBillingClient(activity, () -> {
            QueryProductDetailsParams.Product product = QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(SKU_FULL_VERSION)
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build();
            QueryProductDetailsParams params = QueryProductDetailsParams.newBuilder().setProductList(Collections.singletonList(product)).build();

            billingClient.queryProductDetailsAsync(params, (billingResult, result) -> {
                boolean priceFound = false;
                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    List<ProductDetails> productDetailsList = result.getProductDetailsList();
                    if (!productDetailsList.isEmpty()) {
                        for (ProductDetails productDetails : productDetailsList) {
                            if (productDetails.getProductId().equals(SKU_FULL_VERSION)) {
                                ProductDetails.OneTimePurchaseOfferDetails offerDetails = productDetails.getOneTimePurchaseOfferDetails();
                                if (offerDetails != null) {
                                    String price = offerDetails.getFormattedPrice();
                                    priceFound = true;
                                    activity.runOnUiThread(() -> {
                                        if (!activity.isFinishing() && !activity.isDestroyed()) {
                                            callback.onPriceFound(price);
                                        }
                                    });
                                }
                                break;
                            }
                        }
                    }
                }

                if (!priceFound) {
                    Log.e(Constants.TAG, "Product details query for price failed: " + billingResult.getResponseCode() + " - " + billingResult.getDebugMessage());
                    if (activity != null && !activity.isFinishing() && !activity.isDestroyed()) {
                        activity.runOnUiThread(callback::onPriceQueryFailed);
                    }
                }
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
            billingClient.queryPurchasesAsync(params, (billingResult, purchases) -> {
                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    for (Purchase purchase : purchases) {
                        if (purchase.getProducts().contains(SKU_FULL_VERSION)) {
                            handlePurchase(context, billingClient, purchase, () -> {});
                        }
                    }
                } else {
                    Log.w(Constants.TAG, "verifyAndRestorePurchases failed: " + billingResult.getResponseCode() + " - " + billingResult.getDebugMessage());
                }
            });
        });
    }

    private void handlePurchase(Context context, BillingClient billingClient, Purchase purchase, Runnable onFinishedListener) {
        Log.d(Constants.TAG, "handlePurchase: State is " + purchase.getPurchaseState());
        if (purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
            if (!purchase.isAcknowledged()) {
                Log.d(Constants.TAG, "handlePurchase: Purchase is new. Acknowledging...");
                AcknowledgePurchaseParams acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
                        .setPurchaseToken(purchase.getPurchaseToken())
                        .build();
                billingClient.acknowledgePurchase(acknowledgePurchaseParams, billingResult -> {
                    Log.d(Constants.TAG, "handlePurchase: Acknowledge response: " + billingResult.getResponseCode());
                    if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                        Log.i(Constants.TAG, "handlePurchase: Purchase acknowledged successfully. Unlocking full version.");
                        unlockFullVersion(context);
                    } else {
                        Log.e(Constants.TAG, "handlePurchase: Error acknowledging purchase: " + billingResult.getResponseCode() + " - " + billingResult.getDebugMessage());
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
        if (isFullVersionUnlocked(context)) {
            Log.d(Constants.TAG, "unlockFullVersion: Already unlocked, no action taken.");
            return;
        }

        Log.i(Constants.TAG, "unlockFullVersion: Setting full version to purchased.");
        VersionHelper.setFullVersionUnlocked(context, true);

        if (context instanceof Activity) {
            Activity activity = (Activity) context;
            Log.d(Constants.TAG, "unlockFullVersion: Recreating activity to apply changes.");
            activity.runOnUiThread(() -> {
                if (!activity.isFinishing() && !activity.isDestroyed()) {
                    activity.recreate();
                }
            });
        }
    }
}