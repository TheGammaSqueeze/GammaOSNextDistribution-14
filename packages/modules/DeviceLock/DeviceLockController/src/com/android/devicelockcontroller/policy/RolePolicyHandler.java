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

/** Handles kiosk app role. */
public final class RolePolicyHandler implements PolicyHandler {
    private static final String TAG = "RolePolicyHandler";

    private final SystemDeviceLockManager mSystemDeviceLockManager;
    private final SetupParametersClientInterface mSetupParametersClient;
    private final Executor mBgExecutor;

    RolePolicyHandler(SystemDeviceLockManager systemDeviceLockManager, Executor bgExecutor) {
        mSystemDeviceLockManager = systemDeviceLockManager;
        mBgExecutor = bgExecutor;
        mSetupParametersClient = SetupParametersClient.getInstance();
    }

    private ListenableFuture<Boolean> getAddFinancedDeviceLockKioskRoleFuture(String packageName) {
        return CallbackToFutureAdapter.getFuture(
                completer -> {
                    mSystemDeviceLockManager.addFinancedDeviceKioskRole(packageName,
                            mBgExecutor,
                            new OutcomeReceiver<>() {
                                @Override
                                public void onResult(Void result) {
                                    completer.set(true);
                                }

                                @Override
                                public void onError(Exception ex) {
                                    LogUtil.e(TAG, "Failed to add financed device kiosk role",
                                            ex);
                                    completer.set(false);
                                }
                            });
                    // Used only for debugging.
                    return "getAddFinancedDeviceLockKioskRoleFuture";
                });
    }

    private ListenableFuture<Boolean> getAddFinancedDeviceLockKioskRoleFuture() {
        return Futures.transformAsync(mSetupParametersClient.getKioskPackage(),
                kioskPackageName -> kioskPackageName == null
                        ? Futures.immediateFuture(false)
                        : getAddFinancedDeviceLockKioskRoleFuture(kioskPackageName),
                MoreExecutors.directExecutor());
    }

    private ListenableFuture<Boolean> getRemoveFinancedDeviceLockKioskRoleFuture(
            String packageName) {
        return CallbackToFutureAdapter.getFuture(
                completer -> {
                    mSystemDeviceLockManager.removeFinancedDeviceKioskRole(packageName,
                            mBgExecutor,
                            new OutcomeReceiver<>() {
                                @Override
                                public void onResult(Void result) {
                                    completer.set(true);
                                }

                                @Override
                                public void onError(Exception ex) {
                                    LogUtil.e(TAG, "Failed to remove financed device kiosk role",
                                            ex);
                                    completer.set(false);
                                }
                            });
                    // Used only for debugging.
                    return "getRemoveFinancedDeviceLockKioskRoleFuture";
                });
    }

    private ListenableFuture<Boolean> getRemoveFinancedDeviceLockKioskRoleFuture() {
        return Futures.transformAsync(mSetupParametersClient.getKioskPackage(),
                kioskPackageName -> kioskPackageName == null
                        ? Futures.immediateFuture(false)
                        : getRemoveFinancedDeviceLockKioskRoleFuture(kioskPackageName),
                MoreExecutors.directExecutor());
    }

    @Override
    public ListenableFuture<Boolean> onProvisioned() {
        return getAddFinancedDeviceLockKioskRoleFuture();
    }

    @Override
    public ListenableFuture<Boolean> onCleared() {
        return getRemoveFinancedDeviceLockKioskRoleFuture();
    }
}
