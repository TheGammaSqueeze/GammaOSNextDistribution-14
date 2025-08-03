/*
 * Copyright (C) 2024 The Android Open Source Project
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

/** Handles setting POST_NOTIFICATIONS as SYSTEM_FIXED. */
public final class NotificationsPolicyHandler implements PolicyHandler {
    private static final String TAG = "NotificationsPolicyHandler";

    private final SystemDeviceLockManager mSystemDeviceLockManager;
    private final Executor mBgExecutor;

    NotificationsPolicyHandler(SystemDeviceLockManager systemDeviceLockManager,
            Executor bgExecutor) {
        mSystemDeviceLockManager = systemDeviceLockManager;
        mBgExecutor = bgExecutor;
    }

    private ListenableFuture<Boolean> setPostNotificationsSystemFixedFuture(boolean systemFixed) {
        return CallbackToFutureAdapter.getFuture(
                completer -> {
                    mSystemDeviceLockManager.setPostNotificationsSystemFixed(
                            systemFixed,
                            mBgExecutor,
                            new OutcomeReceiver<>() {
                                @Override
                                public void onResult(Void result) {
                                    completer.set(true);
                                }

                                @Override
                                public void onError(Exception ex) {
                                    LogUtil.e(TAG, "Failed to set POST_NOTIFICATIONS system fixed "
                                            + "flag to: " + systemFixed, ex);
                                    // Return true since we don't want to fail the transition
                                    completer.set(true);
                                }
                            });
                    // Used only for debugging.
                    return "setPostNotificationsSystemFixedFuture";
                });
    }

    @Override
    public ListenableFuture<Boolean> onProvisionInProgress() {
        return setPostNotificationsSystemFixedFuture(/* systemFixed= */ true);
    }

    @Override
    public ListenableFuture<Boolean> onCleared() {
        return setPostNotificationsSystemFixedFuture(/* systemFixed= */ false);
    }
}
