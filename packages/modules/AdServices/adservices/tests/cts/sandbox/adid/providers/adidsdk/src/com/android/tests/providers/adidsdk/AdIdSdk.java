/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.tests.providers.adidsdk;

import android.adservices.adid.AdId;
import android.adservices.adid.AdIdManager;
import android.app.sdksandbox.LoadSdkException;
import android.app.sdksandbox.SandboxedSdk;
import android.app.sdksandbox.SandboxedSdkProvider;
import android.content.Context;
import android.os.Binder;
import android.os.Bundle;
import android.os.OutcomeReceiver;
import android.util.Log;
import android.view.View;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class AdIdSdk extends SandboxedSdkProvider {
    private static final String TAG = "AdIdSdk";
    private static final Executor CALLBACK_EXECUTOR = Executors.newCachedThreadPool();

    @Override
    public SandboxedSdk onLoadSdk(Bundle params) throws LoadSdkException {
        try {
            AdIdManager adIdManager = AdIdManager.get(getContext());

            CompletableFuture<AdId> future = new CompletableFuture<>();
            OutcomeReceiver<AdId, Exception> callback =
                    new OutcomeReceiver<AdId, Exception>() {
                        @Override
                        public void onResult(AdId result) {
                            future.complete(result);
                        }

                        @Override
                        public void onError(Exception error) {
                            Log.e(TAG, "SDK Runtime.testAdId onError " + error.getMessage());
                        }
                    };

            adIdManager.getAdId(CALLBACK_EXECUTOR, callback);

            AdId resultAdId = future.get();

            if (resultAdId.getAdId() != null) {
                // Successfully called the getAdId
                Log.d(
                        TAG,
                        "Successfully called the getAdId. resultAdId.getAdId() = "
                                + resultAdId.getAdId());
                return new SandboxedSdk(new Binder());
            } else {
                // Failed to call the getAdId
                Log.e(TAG, "Failed to call the getAdId");
                throw new LoadSdkException(new Exception("AdId failed."), new Bundle());
            }
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
            // Throw an exception to tell the Test App that some errors occurred so
            // that it will fail the test.
            throw new LoadSdkException(e, new Bundle());
        }
    }

    @Override
    public View getView(Context windowContext, Bundle params, int width, int height) {
        return null;
    }
}
