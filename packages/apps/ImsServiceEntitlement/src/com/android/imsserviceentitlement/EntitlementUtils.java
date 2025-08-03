/*
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.imsserviceentitlement;

import static com.android.imsserviceentitlement.utils.Executors.getAsyncExecutor;
import static com.android.imsserviceentitlement.utils.Executors.getDirectExecutor;

import android.util.Log;

import androidx.annotation.MainThread;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.android.imsserviceentitlement.WfcActivationController.EntitlementResultCallback;
import com.android.imsserviceentitlement.entitlement.EntitlementResult;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

/** Handles entitlement check from main thread. */
public final class EntitlementUtils {

    public static final String LOG_TAG = "IMSSE-EntitlementUtils";

    private static ListenableFuture<EntitlementResult> sCheckEntitlementFuture;

    private EntitlementUtils() {}

    /**
     * Performs the entitlement status check, and passes the result via {@link
     * EntitlementResultCallback}.
     */
    @MainThread
    public static void entitlementCheck(
            ImsEntitlementApi activationApi, EntitlementResultCallback callback) {
        sCheckEntitlementFuture =
                Futures.submit(() -> getEntitlementStatus(activationApi), getAsyncExecutor());
        Futures.addCallback(
                sCheckEntitlementFuture,
                new FutureCallback<EntitlementResult>() {
                    @Override
                    public void onSuccess(EntitlementResult result) {
                        callback.onEntitlementResult(result);
                        sCheckEntitlementFuture = null;
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        Log.w(LOG_TAG, "get entitlement status failed.", t);
                        sCheckEntitlementFuture = null;
                    }
                },
                getDirectExecutor());
    }

    /** Cancels the running task of entitlement status check if exist. */
    public static void cancelEntitlementCheck() {
        if (sCheckEntitlementFuture != null) {
            Log.i(LOG_TAG, "cancel entitlement status check.");
            sCheckEntitlementFuture.cancel(true);
        }
    }

    /**
     * Gets entitlement status via carrier-specific entitlement API over network; returns null on
     * network falure or other unexpected failure from entitlement API.
     */
    @WorkerThread
    @Nullable
    private static EntitlementResult getEntitlementStatus(ImsEntitlementApi activationApi) {
        try {
            return activationApi.checkEntitlementStatus();
        } catch (RuntimeException e) {
            Log.e("WfcActivationActivity", "getEntitlementStatus failed.", e);
            return null;
        }
    }
}
