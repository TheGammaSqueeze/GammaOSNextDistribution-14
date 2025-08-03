/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.devicelockcontroller.policy;

import android.os.OutcomeReceiver;

import androidx.concurrent.futures.CallbackToFutureAdapter;

import com.android.devicelockcontroller.SystemDeviceLockManager;
import com.android.devicelockcontroller.storage.SetupParametersClient;
import com.android.devicelockcontroller.storage.SetupParametersClientInterface;
import com.android.devicelockcontroller.util.LogUtil;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

import java.util.concurrent.Executor;

/** Handles kiosk app keep-alive. */
public final class KioskKeepAlivePolicyHandler implements PolicyHandler {
    private static final String TAG = "KioskKeepAlivePolicyHandler";

    private final SystemDeviceLockManager mSystemDeviceLockManager;
    private final SetupParametersClientInterface mSetupParametersClient;
    private final Executor mBgExecutor;

    KioskKeepAlivePolicyHandler(SystemDeviceLockManager systemDeviceLockManager,
            Executor bgExecutor) {
        mSystemDeviceLockManager = systemDeviceLockManager;
        mBgExecutor = bgExecutor;
        mSetupParametersClient = SetupParametersClient.getInstance();
    }

    private ListenableFuture<Boolean> getEnableKioskKeepAliveFuture(String packageName) {
        return CallbackToFutureAdapter.getFuture(
                completer -> {
                    mSystemDeviceLockManager.enableKioskKeepalive(packageName,
                            mBgExecutor,
                            new OutcomeReceiver<>() {
                                @Override
                                public void onResult(Void result) {
                                    completer.set(true);
                                }

                                @Override
                                public void onError(Exception ex) {
                                    // Return true since the keep-alive service is optional
                                    LogUtil.d(TAG, "Failed to enable kiosk keep-alive", ex);
                                    completer.set(true);
                                }
                            });
                    // Used only for debugging.
                    return "getEnableKioskKeepAliveFuture";
                });
    }

    private ListenableFuture<Boolean> getEnableKioskKeepAliveFuture() {
        return Futures.transformAsync(mSetupParametersClient.getKioskPackage(),
                kioskPackageName -> kioskPackageName == null
                        ? Futures.immediateFuture(false)
                        : getEnableKioskKeepAliveFuture(kioskPackageName),
                MoreExecutors.directExecutor());
    }

    private ListenableFuture<Boolean> getDisableKioskKeepAliveFuture() {
        return CallbackToFutureAdapter.getFuture(
                completer -> {
                    mSystemDeviceLockManager.disableKioskKeepalive(mBgExecutor,
                            new OutcomeReceiver<>() {
                                @Override
                                public void onResult(Void result) {
                                    completer.set(true);
                                }

                                @Override
                                public void onError(Exception ex) {
                                    // Return true since the keep-alive service is optional
                                    LogUtil.d(TAG, "Failed to disable kiosk keep-alive", ex);
                                    completer.set(true);
                                }
                            });
                    // Used only for debugging.
                    return "getDisableKioskKeepAliveFuture";
                });
    }

    @Override
    public ListenableFuture<Boolean> onProvisioned() {
        return getEnableKioskKeepAliveFuture();
    }

    @Override
    public ListenableFuture<Boolean> onCleared() {
        return getDisableKioskKeepAliveFuture();
    }
}
