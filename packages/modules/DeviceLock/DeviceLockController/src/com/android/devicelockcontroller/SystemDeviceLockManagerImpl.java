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

package com.android.devicelockcontroller;

import android.annotation.CallbackExecutor;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.PackageInfoFlags;
import android.devicelock.DeviceLockManager;
import android.devicelock.IDeviceLockService;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.OutcomeReceiver;
import android.os.RemoteCallback;
import android.os.RemoteException;

import androidx.annotation.NonNull;

import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * Implementation for SystemDeviceLockManager.
 */
public final class SystemDeviceLockManagerImpl implements SystemDeviceLockManager {
    private static final String TAG = "SystemDeviceLockManagerImpl";

    private final IDeviceLockService mIDeviceLockService;
    private final Context mContext;

    private SystemDeviceLockManagerImpl(Context context) {
        mContext = context;
        final DeviceLockManager deviceLockManager = context.getSystemService(
                DeviceLockManager.class);

        if (deviceLockManager != null)
            mIDeviceLockService = deviceLockManager.getService();
        else
            mIDeviceLockService = null;
    }

    private SystemDeviceLockManagerImpl() {
        this(DeviceLockControllerApplication.getAppContext());
    }

    // Initialization-on-demand holder.
    private static final class SystemDeviceLockManagerHolder {
        static final SystemDeviceLockManagerImpl sSystemDeviceLockManager =
                new SystemDeviceLockManagerImpl();
    }

    /**
     * Returns the lazily initialized singleton instance of the SystemDeviceLockManager.
     */
    public static SystemDeviceLockManager getInstance() {
        return SystemDeviceLockManagerHolder.sSystemDeviceLockManager;
    }

    @Override
    public void addFinancedDeviceKioskRole(@NonNull String packageName,
            @CallbackExecutor Executor executor,
            @NonNull OutcomeReceiver<Void, Exception> callback) {
        Objects.requireNonNull(executor);
        Objects.requireNonNull(callback);

        try {
            mIDeviceLockService.addFinancedDeviceKioskRole(packageName,
                    new RemoteCallback(result -> executor.execute(() -> {
                        processResult(result, callback,
                                "Failed to add financed role to: " + packageName);
                    }), new Handler(Looper.getMainLooper())));
        } catch (RemoteException e) {
            executor.execute(() -> callback.onError(new RuntimeException(e)));
        }
    }

    @Override
    public void removeFinancedDeviceKioskRole(@NonNull String packageName,
            @CallbackExecutor Executor executor,
            @NonNull OutcomeReceiver<Void, Exception> callback) {
        Objects.requireNonNull(executor);
        Objects.requireNonNull(callback);

        try {
            mIDeviceLockService.removeFinancedDeviceKioskRole(packageName,
                    new RemoteCallback(result -> executor.execute(() -> {
                        processResult(result, callback,
                                "Failed to remove financed role from: " + packageName);
                    }), new Handler(Looper.getMainLooper())));
        } catch (RemoteException e) {
            executor.execute(() -> callback.onError(new RuntimeException(e)));
        }
    }

    @Override
    public void setDlcExemptFromActivityBgStartRestrictionState(boolean exempt,
            @CallbackExecutor Executor executor,
            @NonNull OutcomeReceiver<Void, Exception> callback) {
        Objects.requireNonNull(executor);
        Objects.requireNonNull(callback);

        try {
            mIDeviceLockService.setCallerExemptFromActivityBgStartRestrictionState(exempt,
                    new RemoteCallback(result -> executor.execute(() -> {
                        processResult(result, callback,
                                "Failed to change exempt from activity background start to: "
                                        + (exempt ? "exempt" : "non exempt"));
                    }), new Handler(Looper.getMainLooper())));
        } catch (RemoteException e) {
            executor.execute(() -> callback.onError(new RuntimeException(e)));
        }
    }

    @Override
    public void setDlcAllowedToSendUndismissibleNotifications(boolean allowed,
            @CallbackExecutor Executor executor,
            @NonNull OutcomeReceiver<Void, Exception> callback) {
        Objects.requireNonNull(executor);
        Objects.requireNonNull(callback);

        try {
            mIDeviceLockService.setCallerAllowedToSendUndismissibleNotifications(allowed,
                    new RemoteCallback(result -> executor.execute(() -> {
                        processResult(result, callback,
                                "Failed to change undismissible notifs allowed to: "
                                        + (allowed ? "allowed" : "not allowed"));
                    }), new Handler(Looper.getMainLooper())));
        } catch (RemoteException e) {
            executor.execute(() -> callback.onError(new RuntimeException(e)));
        }
    }

    @Override
    public void setKioskAppExemptFromRestrictionsState(String packageName, boolean exempt,
            Executor executor, @NonNull OutcomeReceiver<Void, Exception> callback) {
        Objects.requireNonNull(executor);
        Objects.requireNonNull(callback);

        try {
            int kioskUid = mContext.getPackageManager().getPackageUid(packageName,
                    PackageInfoFlags.of(0));
            mIDeviceLockService.setUidExemptFromRestrictionsState(kioskUid, exempt,
                    new RemoteCallback(result -> executor.execute(() -> {
                        processResult(result, callback, "Failed to exempt for UID: " + kioskUid);
                    }), new Handler(Looper.getMainLooper())));
        } catch (RemoteException | PackageManager.NameNotFoundException e) {
            executor.execute(() -> callback.onError(new RuntimeException(e)));
        }
    }

    @Override
    public void enableKioskKeepalive(String packageName, Executor executor,
            @NonNull OutcomeReceiver<Void, Exception> callback) {
        Objects.requireNonNull(packageName);
        Objects.requireNonNull(executor);
        Objects.requireNonNull(callback);

        try {
            mIDeviceLockService.enableKioskKeepalive(packageName,
                    new RemoteCallback(result -> executor.execute(() -> {
                        processResult(result, callback,
                                "Failed to enable kiosk keep-alive for package: "
                                        + packageName);
                    }), new Handler(Looper.getMainLooper())));
        } catch (RemoteException e) {
            executor.execute(() -> callback.onError(new RuntimeException(e)));
        }
    }

    @Override
    public void disableKioskKeepalive(Executor executor,
            @NonNull OutcomeReceiver<Void, Exception> callback) {
        Objects.requireNonNull(executor);
        Objects.requireNonNull(callback);

        try {
            mIDeviceLockService.disableKioskKeepalive(
                    new RemoteCallback(result -> executor.execute(() -> {
                        processResult(result, callback, "Failed to disable kiosk keep-alive");
                    }), new Handler(Looper.getMainLooper())));
        } catch (RemoteException e) {
            executor.execute(() -> callback.onError(new RuntimeException(e)));
        }
    }

    @Override
    public void enableControllerKeepalive(Executor executor,
            @NonNull OutcomeReceiver<Void, Exception> callback) {
        Objects.requireNonNull(executor);
        Objects.requireNonNull(callback);

        try {
            mIDeviceLockService.enableControllerKeepalive(
                    new RemoteCallback(result -> executor.execute(() -> {
                        processResult(result, callback, "Failed to enable controller keep-alive");
                    }), new Handler(Looper.getMainLooper())));
        } catch (RemoteException e) {
            executor.execute(() -> callback.onError(new RuntimeException(e)));
        }
    }

    @Override
    public void disableControllerKeepalive(Executor executor,
            @NonNull OutcomeReceiver<Void, Exception> callback) {
        Objects.requireNonNull(executor);
        Objects.requireNonNull(callback);

        try {
            mIDeviceLockService.disableControllerKeepalive(
                    new RemoteCallback(result -> executor.execute(() -> {
                        processResult(result, callback, "Failed to disable controller keep-alive");
                    }), new Handler(Looper.getMainLooper())));
        } catch (RemoteException e) {
            executor.execute(() -> callback.onError(new RuntimeException(e)));
        }
    }

    @Override
    public void setDeviceFinalized(boolean finalized, Executor executor,
            @NonNull OutcomeReceiver<Void, Exception> callback) {
        Objects.requireNonNull(executor);
        Objects.requireNonNull(callback);

        try {
            mIDeviceLockService.setDeviceFinalized(finalized,
                    new RemoteCallback(result -> executor.execute(() -> {
                        processResult(result, callback, "Failed to set device finalized");
                    }), new Handler(Looper.getMainLooper())));
        } catch (RemoteException e) {
            executor.execute(() -> callback.onError(new RuntimeException(e)));
        }
    }

    @Override
    public void setPostNotificationsSystemFixed(boolean systemFixed, Executor executor,
            @NonNull OutcomeReceiver<Void, Exception> callback) {
        Objects.requireNonNull(executor);
        Objects.requireNonNull(callback);

        try {
            mIDeviceLockService.setPostNotificationsSystemFixed(systemFixed,
                    new RemoteCallback(result -> executor.execute(() -> {
                        processResult(result, callback, "Failed to change POST_NOTIFICATIONS "
                                + "SYSTEM_FIXED flag to: " + systemFixed);
                    }), new Handler(Looper.getMainLooper())));
        } catch (RemoteException e) {
            executor.execute(() -> callback.onError(new RuntimeException(e)));
        }
    }

    private static void processResult(Bundle result,
            @NonNull OutcomeReceiver<Void, Exception> callback, String message) {
        final boolean remoteCallbackResult = result.getBoolean(
                IDeviceLockService.KEY_REMOTE_CALLBACK_RESULT);
        if (remoteCallbackResult) {
            callback.onResult(null /* result */);
        } else {
            callback.onError(new Exception(message));
        }
    }
}
