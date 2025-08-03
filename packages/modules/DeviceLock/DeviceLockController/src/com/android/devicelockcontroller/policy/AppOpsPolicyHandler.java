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
import com.android.devicelockcontroller.util.LogUtil;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

import java.util.concurrent.Executor;

final class AppOpsPolicyHandler implements PolicyHandler {
    private static final String TAG = "AppOpsPolicyHandler";
    private final SystemDeviceLockManager mSystemDeviceLockManager;
    private final Executor mBgExecutor;

    AppOpsPolicyHandler(SystemDeviceLockManager systemDeviceLockManager, Executor bgExecutor) {
        mSystemDeviceLockManager = systemDeviceLockManager;
        mBgExecutor = bgExecutor;
    }

    /**
     * Set the exempt from activity background start restriction state for dlc app.
     */
    private ListenableFuture<Boolean> setDlcExemptFromActivityBgStartRestrictionState(
            boolean exempt) {
        return CallbackToFutureAdapter.getFuture(completer -> {
            mSystemDeviceLockManager.setDlcExemptFromActivityBgStartRestrictionState(exempt,
                    mBgExecutor, new OutcomeReceiver<>() {
                        @Override
                        public void onResult(Void unused) {
                            completer.set(true);
                        }

                        @Override
                        public void onError(Exception error) {
                            LogUtil.e(TAG, "Cannot set background start exemption", error);
                            completer.set(false);
                        }
                    });
            // Used only for debugging.
            return "setDlcExemptFromActivityBgStartRestrictionState";
        });
    }

    /**
     * Set whether the dlc can send undismissible notifications.
     */
    private ListenableFuture<Boolean> setDlcAllowedToSendUndismissibleNotifications(
            boolean allowed) {
        return CallbackToFutureAdapter.getFuture(completer -> {
            mSystemDeviceLockManager.setDlcAllowedToSendUndismissibleNotifications(allowed,
                    mBgExecutor, new OutcomeReceiver<>() {
                        @Override
                        public void onResult(Void unused) {
                            completer.set(true);
                        }

                        @Override
                        public void onError(Exception error) {
                            LogUtil.e(TAG, "Cannot set undismissible notifs", error);
                            // Returns true here to make sure state transition
                            // success.
                            completer.set(true);
                        }
                    });
            // Used only for debugging.
            return "setDlcAllowedToSendUndismissibleNotifications";
        });
    }

    /**
     * Set the exempt from hibernation, battery and data usage restriction state for kiosk app.
     */
    private ListenableFuture<Boolean> setKioskAppExemptionsState(boolean exempt) {
        return Futures.transformAsync(SetupParametersClient.getInstance().getKioskPackage(),
                kioskPackageName -> kioskPackageName == null ? Futures.immediateFuture(true)
                        : CallbackToFutureAdapter.getFuture(completer -> {
                            mSystemDeviceLockManager.setKioskAppExemptFromRestrictionsState(
                                    kioskPackageName, exempt, mBgExecutor, new OutcomeReceiver<>() {
                                        @Override
                                        public void onResult(Void unused) {
                                            completer.set(true);
                                        }

                                        @Override
                                        public void onError(Exception error) {
                                            LogUtil.e(TAG, "Cannot set exempt kiosk app", error);
                                            // Returns true here to make sure state transition
                                            // success.
                                            completer.set(true);
                                        }
                                    });
                            // Used only for debugging.
                            return "setKioskAppExemptionsState";
                        }), MoreExecutors.directExecutor());
    }

    /**
     * Set the exempt state for dlc and kiosk app. Note that the exemptions are different between
     * dlc app and kiosk app.
     */
    private ListenableFuture<Boolean> setDlcAndKioskAppExemptionsState(boolean exempt) {
        final ListenableFuture<Boolean> backgroundFuture =
                setDlcExemptFromActivityBgStartRestrictionState(exempt);
        final ListenableFuture<Boolean> kioskExemptionFuture = setKioskAppExemptionsState(exempt);
        return Futures.whenAllSucceed(backgroundFuture, kioskExemptionFuture).call(
                () -> Futures.getDone(backgroundFuture) && Futures.getDone(kioskExemptionFuture),
                MoreExecutors.directExecutor());
    }

    @Override
    public ListenableFuture<Boolean> onProvisioned() {
        return setDlcAndKioskAppExemptionsState(/* exempt= */ true);
    }

    @Override
    public ListenableFuture<Boolean> onProvisionInProgress() {
        final ListenableFuture<Boolean> notifFuture =
                setDlcAllowedToSendUndismissibleNotifications(/* allowed= */ true);
        final ListenableFuture<Boolean> backgroundFuture =
                setDlcExemptFromActivityBgStartRestrictionState(/* exempt= */ true);
        return Futures.whenAllSucceed(notifFuture, backgroundFuture).call(
                () -> Futures.getDone(notifFuture) && Futures.getDone(backgroundFuture),
                MoreExecutors.directExecutor());
    }

    @Override
    public ListenableFuture<Boolean> onProvisionFailed() {
        return setDlcExemptFromActivityBgStartRestrictionState(/* exempt= */ false);
    }

    @Override
    public ListenableFuture<Boolean> onCleared() {
        final ListenableFuture<Boolean> notifFuture =
                setDlcAllowedToSendUndismissibleNotifications(/* allowed= */ false);
        final ListenableFuture<Boolean> clearExemptions =
                setDlcAndKioskAppExemptionsState(/* exempt= */ false);
        return Futures.whenAllSucceed(notifFuture, clearExemptions).call(
                () -> Futures.getDone(notifFuture) && Futures.getDone(clearExemptions),
                MoreExecutors.directExecutor());
    }

    // Due to some reason, AppOpsManager does not persist exemption after reboot, therefore we
    // need to always set them from our end.
    @Override
    public ListenableFuture<Boolean> onLocked() {
        return setDlcAndKioskAppExemptionsState(/* exempt= */ true);
    }

    // Due to some reason, AppOpsManager does not persist exemption after reboot, therefore we
    // need to always set them from our end.
    @Override
    public ListenableFuture<Boolean> onUnlocked() {
        return setKioskAppExemptionsState(/* exempt= */ true);
    }
}
