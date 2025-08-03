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

package com.android.devicelockcontroller;

import android.app.Service;
import android.app.admin.DevicePolicyManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.devicelock.ParcelableException;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteCallback;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.devicelockcontroller.policy.DevicePolicyController;
import com.android.devicelockcontroller.policy.DeviceStateController;
import com.android.devicelockcontroller.policy.FinalizationController;
import com.android.devicelockcontroller.policy.PolicyObjectsProvider;
import com.android.devicelockcontroller.stats.StatsLogger;
import com.android.devicelockcontroller.stats.StatsLoggerProvider;
import com.android.devicelockcontroller.storage.GlobalParametersClient;
import com.android.devicelockcontroller.storage.SetupParametersClient;
import com.android.devicelockcontroller.util.LogUtil;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

import java.util.List;
import java.util.Objects;

/**
 * Device Lock Controller Service. This is hosted in an APK and is bound
 * by the Device Lock System Service.
 */
public final class DeviceLockControllerService extends Service {
    private static final String TAG = "DeviceLockControllerService";
    private DeviceStateController mDeviceStateController;
    private DevicePolicyController mPolicyController;
    private FinalizationController mFinalizationController;
    private PackageManager mPackageManager;
    private StatsLogger mStatsLogger;

    private final IDeviceLockControllerService.Stub mBinder =
            new IDeviceLockControllerService.Stub() {
                @Override
                public void lockDevice(RemoteCallback remoteCallback) {
                    logKioskAppRequest();
                    ListenableFuture<Void> lockDeviceFuture = mDeviceStateController.lockDevice();
                    Futures.addCallback(lockDeviceFuture,
                            remoteCallbackWrapper(remoteCallback),
                            MoreExecutors.directExecutor());
                    Futures.addCallback(lockDeviceFuture,
                            logLockUnlockDeviceCallback(/* isLockDevice = */ true),
                            MoreExecutors.directExecutor());
                }

                @Override
                public void unlockDevice(RemoteCallback remoteCallback) {
                    logKioskAppRequest();
                    ListenableFuture<Void> unlockDeviceFuture =
                            mDeviceStateController.unlockDevice();
                    Futures.addCallback(unlockDeviceFuture,
                            remoteCallbackWrapper(remoteCallback),
                            MoreExecutors.directExecutor());
                    Futures.addCallback(unlockDeviceFuture,
                            logLockUnlockDeviceCallback(/* isLockDevice = */ false),
                            MoreExecutors.directExecutor());
                }

                @Override
                public void isDeviceLocked(RemoteCallback remoteCallback) {
                    logKioskAppRequest();
                    Futures.addCallback(mDeviceStateController.isLocked(),
                            remoteCallbackWrapper(remoteCallback, KEY_RESULT),
                            MoreExecutors.directExecutor());
                }

                @Override
                public void getDeviceIdentifier(RemoteCallback remoteCallback) {
                    logKioskAppRequest();
                    Futures.addCallback(
                            GlobalParametersClient.getInstance().getRegisteredDeviceId(),
                            remoteCallbackWrapper(remoteCallback, KEY_RESULT),
                            MoreExecutors.directExecutor());
                }

                @Override
                public void clearDeviceRestrictions(RemoteCallback remoteCallback) {
                    logKioskAppRequest();
                    Futures.addCallback(
                            Futures.transformAsync(mDeviceStateController.clearDevice(),
                                    unused -> mFinalizationController.notifyRestrictionsCleared(),
                                    MoreExecutors.directExecutor()),
                            remoteCallbackWrapper(remoteCallback),
                            MoreExecutors.directExecutor());
                }

                @Override
                public void onUserSwitching(RemoteCallback remoteCallback) {
                    Futures.addCallback(mPolicyController.enforceCurrentPolicies(),
                            remoteCallbackWrapper(remoteCallback),
                            MoreExecutors.directExecutor());
                }

                @Override
                public void onUserUnlocked(RemoteCallback remoteCallback) {
                    DevicePolicyManager dpm = getSystemService(DevicePolicyManager.class);
                    Objects.requireNonNull(dpm).setUserControlDisabledPackages(
                            /* admin= */ null,
                            List.of(getPackageName()));
                    Futures.addCallback(mPolicyController.onUserUnlocked(),
                            remoteCallbackWrapper(remoteCallback),
                            MoreExecutors.directExecutor());
                }

                @Override
                public void onUserSetupCompleted(RemoteCallback remoteCallback) {
                    Futures.addCallback(mPolicyController.onUserSetupCompleted(),
                            remoteCallbackWrapper(remoteCallback),
                            MoreExecutors.directExecutor());
                }

                @Override
                public void onAppCrashed(boolean isKiosk, RemoteCallback remoteCallback) {
                    Futures.addCallback(mPolicyController.onAppCrashed(isKiosk),
                            remoteCallbackWrapper(remoteCallback),
                            MoreExecutors.directExecutor());
                }

                private void logKioskAppRequest() {
                    Futures.addCallback(SetupParametersClient.getInstance().getKioskPackage(),
                            new FutureCallback<>() {
                                @Override
                                public void onSuccess(String result) {
                                    try {
                                        final int uid = mPackageManager.getPackageUid(
                                                result, /* flags= */ 0);
                                        mStatsLogger.logKioskAppRequest(uid);
                                    } catch (PackageManager.NameNotFoundException e) {
                                        LogUtil.e(TAG, "Kiosk App package name not found", e);
                                    }
                                }

                                @Override
                                public void onFailure(Throwable t) {
                                    LogUtil.e(TAG, "Failed to get Kiosk app package name", t);
                                }
                            },
                            MoreExecutors.directExecutor());

                }
            };

    @NonNull
    private static FutureCallback<Object> remoteCallbackWrapper(RemoteCallback remoteCallback,
            @Nullable final String key) {
        return new FutureCallback<>() {
            @Override
            public void onSuccess(Object result) {
                sendResult(key, remoteCallback, result);
            }

            @Override
            public void onFailure(Throwable t) {
                LogUtil.e(TAG, "Failed to perform the request", t);
                sendFailure(t, remoteCallback);
            }
        };
    }

    @NonNull
    private static FutureCallback<Object> remoteCallbackWrapper(RemoteCallback remoteCallback) {
        return remoteCallbackWrapper(remoteCallback, /* key= */ null);
    }

    /**
     * Send result to caller.
     *
     * @param key Key to use in bundle for result. null if no result is needed
     * @param remoteCallback remote callback used to send the result.
     * @param result Value to return in bundle.
     */
    private static void sendResult(@Nullable String key, RemoteCallback remoteCallback,
            Object result) {
        final Bundle bundle = new Bundle();
        if (key != null) {
            if (result instanceof Boolean) {
                bundle.putBoolean(key, (Boolean) result);
            } else if (result instanceof String) {
                bundle.putString(key, (String) result);
            }
        }
        remoteCallback.sendResult(bundle);
    }

    private static void sendFailure(Throwable t, RemoteCallback remoteCallback) {
        final Bundle bundle = new Bundle();
        bundle.putParcelable(IDeviceLockControllerService.KEY_PARCELABLE_EXCEPTION,
                new ParcelableException(t instanceof Exception ? (Exception) t : new Exception(t)));
        remoteCallback.sendResult(bundle);
    }

    private FutureCallback<Void> logLockUnlockDeviceCallback(boolean isLockDevice) {
        return new FutureCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                if (isLockDevice) {
                    mStatsLogger.logSuccessfulLockingDevice();
                } else {
                    mStatsLogger.logSuccessfulUnlockingDevice();
                }
            }

            @Override
            public void onFailure(Throwable t) {
                Futures.addCallback(mDeviceStateController.getDeviceState(),
                        new FutureCallback<Integer>() {
                            @Override
                            public void onSuccess(Integer result) {
                                int deviceStatePostCommand;
                                switch (result) {
                                    case DeviceStateController.DeviceState.UNLOCKED ->
                                            deviceStatePostCommand =
                                                    StatsLogger.DeviceStateStats.UNLOCKED;
                                    case DeviceStateController.DeviceState.LOCKED ->
                                            deviceStatePostCommand =
                                                    StatsLogger.DeviceStateStats.LOCKED;
                                    case DeviceStateController.DeviceState.CLEARED ->
                                            deviceStatePostCommand =
                                                    StatsLogger.DeviceStateStats.CLEARED;
                                    case DeviceStateController.DeviceState.UNDEFINED ->
                                            deviceStatePostCommand =
                                                    StatsLogger.DeviceStateStats.UNDEFINED;
                                    default -> deviceStatePostCommand =
                                            StatsLogger.DeviceStateStats.UNDEFINED;
                                }
                                if (isLockDevice) {
                                    mStatsLogger.logLockDeviceFailure(deviceStatePostCommand);
                                } else {
                                    mStatsLogger.logUnlockDeviceFailure(deviceStatePostCommand);
                                }
                            }

                            // We don't expect this to be reached
                            @Override
                            public void onFailure(Throwable t) {
                                LogUtil.e(TAG, "Failed to get device State", t);
                                throw new RuntimeException(t);
                            }
                        }, MoreExecutors.directExecutor());
            }
        };
    }

    @Override
    public void onCreate() {
        LogUtil.d(TAG, "onCreate");

        final PolicyObjectsProvider policyObjects = (PolicyObjectsProvider) getApplication();
        final StatsLoggerProvider statsLoggerProvider = (StatsLoggerProvider) getApplication();
        mDeviceStateController = policyObjects.getDeviceStateController();
        mPolicyController = policyObjects.getPolicyController();
        mFinalizationController = policyObjects.getFinalizationController();
        mPackageManager = getPackageManager();
        mStatsLogger = statsLoggerProvider.getStatsLogger();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }
}
