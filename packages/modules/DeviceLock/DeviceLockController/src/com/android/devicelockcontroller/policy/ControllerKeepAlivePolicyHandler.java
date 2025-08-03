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
import com.android.devicelockcontroller.util.LogUtil;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.concurrent.Executor;

/** Handles device lock controller keep-alive. */
public final class ControllerKeepAlivePolicyHandler implements PolicyHandler {
    private static final String TAG = "ControllerKeepAlivePolicyHandler";

    private final SystemDeviceLockManager mSystemDeviceLockManager;
    private final Executor mBgExecutor;

    ControllerKeepAlivePolicyHandler(SystemDeviceLockManager systemDeviceLockManager,
            Executor bgExecutor) {
        mSystemDeviceLockManager = systemDeviceLockManager;
        mBgExecutor = bgExecutor;
    }

    private ListenableFuture<Boolean> getEnableControllerKeepAliveFuture() {
        return CallbackToFutureAdapter.getFuture(
                completer -> {
                    mSystemDeviceLockManager.enableControllerKeepalive(
                            mBgExecutor,
                            new OutcomeReceiver<>() {
                                @Override
                                public void onResult(Void result) {
                                    completer.set(true);
                                }

                                @Override
                                public void onError(Exception ex) {
                                    LogUtil.e(TAG, "Failed to enable controller keep-alive",
                                            ex);
                                    // Return SUCCESS since we don't want to fail the transition
                                    completer.set(true);
                                }
                            });
                    // Used only for debugging.
                    return "getEnableControllerKeepAliveFuture";
                });
    }

    private ListenableFuture<Boolean> getDisableControllerKeepAliveFuture() {
        return CallbackToFutureAdapter.getFuture(
                completer -> {
                    mSystemDeviceLockManager.disableControllerKeepalive(mBgExecutor,
                            new OutcomeReceiver<>() {
                                @Override
                                public void onResult(Void result) {
                                    completer.set(true);
                                }

                                @Override
                                public void onError(Exception ex) {
                                    LogUtil.e(TAG, "Failed to disable controller keep-alive",
                                            ex);
                                    // Return SUCCESS since we don't want to fail the transition
                                    completer.set(true);
                                }
                            });
                    // Used only for debugging.
                    return "getDisableControllerKeepAliveFuture";
                });
    }

    @Override
    public ListenableFuture<Boolean> onProvisionInProgress() {
        return getEnableControllerKeepAliveFuture();
    }

    @Override
    public ListenableFuture<Boolean> onProvisionPaused() {
        return getDisableControllerKeepAliveFuture();
    }

    @Override
    public ListenableFuture<Boolean> onProvisioned() {
        return getDisableControllerKeepAliveFuture();
    }
}
