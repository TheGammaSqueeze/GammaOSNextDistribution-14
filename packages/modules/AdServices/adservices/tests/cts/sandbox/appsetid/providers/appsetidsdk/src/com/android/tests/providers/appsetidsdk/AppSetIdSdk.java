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

package com.android.tests.providers.appsetidsdk;

import android.adservices.appsetid.AppSetId;
import android.adservices.appsetid.AppSetIdManager;
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

public class AppSetIdSdk extends SandboxedSdkProvider {
    private static final String TAG = "AppSetIdSdk";
    private static final Executor CALLBACK_EXECUTOR = Executors.newCachedThreadPool();

    @Override
    public SandboxedSdk onLoadSdk(Bundle params) throws LoadSdkException {
        try {
            AppSetIdManager appSetIdManager = AppSetIdManager.get(getContext());

            CompletableFuture<AppSetId> future = new CompletableFuture<>();
            OutcomeReceiver<AppSetId, Exception> callback =
                    new OutcomeReceiver<AppSetId, Exception>() {
                        @Override
                        public void onResult(AppSetId result) {
                            future.complete(result);
                        }

                        @Override
                        public void onError(Exception error) {
                            Log.e(TAG, "SDK Runtime.testAppSetId onError " + error.getMessage());
                        }
                    };

            appSetIdManager.getAppSetId(CALLBACK_EXECUTOR, callback);

            AppSetId resultAppSetId = future.get();

            if (resultAppSetId.getId() != null) {
                // Successfully called the getAppSetId
                Log.d(
                        TAG,
                        "Successfully called the getAppSetId. resultAppSetId.getId() = "
                                + resultAppSetId.getId());
                return new SandboxedSdk(new Binder());
            } else {
                // Failed to call the getAppSetId
                Log.e(TAG, "Failed to call the getAppSetId");
                throw new LoadSdkException(new Exception("AppSetId failed."), new Bundle());
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
