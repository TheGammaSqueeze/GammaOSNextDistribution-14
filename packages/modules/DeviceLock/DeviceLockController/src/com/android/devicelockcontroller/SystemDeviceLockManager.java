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
import android.annotation.RequiresPermission;
import android.os.OutcomeReceiver;

import androidx.annotation.NonNull;

import java.util.concurrent.Executor;

/**
 * Manager used to interact with the system device lock service from the Device Lock Controller.
 * Stopgap: these should have been SystemApis on DeviceLockManager.
 */
public interface SystemDeviceLockManager {
    String MANAGE_DEVICE_LOCK_SERVICE_FROM_CONTROLLER =
            "com.android.devicelockcontroller.permission."
                    + "MANAGE_DEVICE_LOCK_SERVICE_FROM_CONTROLLER";

    /**
     * Add the FINANCED_DEVICE_KIOSK role to the specified package.
     *
     * @param packageName package for the financed device kiosk app.
     * @param executor    the {@link Executor} on which to invoke the callback.
     * @param callback    this returns either success or an exception.
     */
    @RequiresPermission(MANAGE_DEVICE_LOCK_SERVICE_FROM_CONTROLLER)
    void addFinancedDeviceKioskRole(@NonNull String packageName,
            @CallbackExecutor Executor executor,
            @NonNull OutcomeReceiver<Void, Exception> callback);

    /**
     * Remove the FINANCED_DEVICE_KIOSK role from the specified package.
     *
     * @param packageName package for the financed device kiosk app.
     * @param executor    the {@link Executor} on which to invoke the callback.
     * @param callback    this returns either success or an exception.
     */
    @RequiresPermission(MANAGE_DEVICE_LOCK_SERVICE_FROM_CONTROLLER)
    void removeFinancedDeviceKioskRole(@NonNull String packageName,
            @CallbackExecutor Executor executor,
            @NonNull OutcomeReceiver<Void, Exception> callback);

    /**
     * Set the exemption state for activity background start restriction for dlc.
     *
     * @param exempt if true, dlc will be set to exempt from activity background start
     *               restriction; false, the exemption state will be set to default.
     */
    @RequiresPermission(MANAGE_DEVICE_LOCK_SERVICE_FROM_CONTROLLER)
    void setDlcExemptFromActivityBgStartRestrictionState(boolean exempt,
            @CallbackExecutor Executor executor,
            @NonNull OutcomeReceiver<Void, Exception> callback);

    /**
     * Set whether the dlc should be allowed to send undismissible notifications
     *
     * @param allowed if true, dlc can send undimissible notifications
     */
    @RequiresPermission(MANAGE_DEVICE_LOCK_SERVICE_FROM_CONTROLLER)
    void setDlcAllowedToSendUndismissibleNotifications(boolean allowed,
            @CallbackExecutor Executor executor,
            @NonNull OutcomeReceiver<Void, Exception> callback);

    /**
     * Set the exemption state of app restrictions (e.g. hibernation, battery and data usage) for
     * kiosk app.
     *
     * @param packageName kiosk app package name.
     * @param exempt      if true, the given uid will be set to exempt from app restrictions (e.g.
     *                    hibernation, battery and data usage restriction); false, the exemption
     *                    state will be set to default.
     * @param executor    the {@link Executor} on which to invoke the callback.
     * @param callback    callback this returns either success or an exception.
     */
    @RequiresPermission(MANAGE_DEVICE_LOCK_SERVICE_FROM_CONTROLLER)
    void setKioskAppExemptFromRestrictionsState(String packageName, boolean exempt,
            @CallbackExecutor Executor executor,
            @NonNull OutcomeReceiver<Void, Exception> callback);

    /**
     * Enable kiosk keepalive, making sure the kiosk app is restarted on crash.
     *
     * @param packageName kiosk app package name.
     * @param executor    the {@link Executor} on which to invoke the callback.
     * @param callback    callback this returns either success or an exception.
     */
    @RequiresPermission(MANAGE_DEVICE_LOCK_SERVICE_FROM_CONTROLLER)
    void enableKioskKeepalive(String packageName, @CallbackExecutor Executor executor,
            @NonNull OutcomeReceiver<Void, Exception> callback);

    /**
     * Disable kiosk keepalive.
     *
     * @param executor the {@link Executor} on which to invoke the callback.
     * @param callback callback this returns either success or an exception.
     */
    @RequiresPermission(MANAGE_DEVICE_LOCK_SERVICE_FROM_CONTROLLER)
    void disableKioskKeepalive(@CallbackExecutor Executor executor,
            @NonNull OutcomeReceiver<Void, Exception> callback);

    /**
     * Enable controller keepalive, making sure DLC is restarted on crash.
     *
     * @param executor the {@link Executor} on which to invoke the callback.
     * @param callback callback this returns either success or an exception.
     */
    @RequiresPermission(MANAGE_DEVICE_LOCK_SERVICE_FROM_CONTROLLER)
    void enableControllerKeepalive(@CallbackExecutor Executor executor,
            @NonNull OutcomeReceiver<Void, Exception> callback);

    /**
     * Disable controller keepalive.
     *
     * @param executor the {@link Executor} on which to invoke the callback.
     * @param callback callback this returns either success or an exception.
     */
    @RequiresPermission(MANAGE_DEVICE_LOCK_SERVICE_FROM_CONTROLLER)
    void disableControllerKeepalive(@CallbackExecutor Executor executor,
            @NonNull OutcomeReceiver<Void, Exception> callback);

    /**
     * Set whether device is finalized so that system service knows when to keep the Device Lock
     * Controller enabled.
     *
     * @param finalized true if device is finalized and DLC should not be enabled.
     * @param executor  the {@link Executor} on which to invoke the callback.
     * @param callback  callback this returns either success or an exception.
     */
    @RequiresPermission(MANAGE_DEVICE_LOCK_SERVICE_FROM_CONTROLLER)
    void setDeviceFinalized(boolean finalized, @CallbackExecutor Executor executor,
            @NonNull OutcomeReceiver<Void, Exception> callback);

    /**
     * Set or clear the POST_NOTIFICATIONS permission as SYSTEM_FIXED, so it cannot be revoked
     * in Settings.
     *
     * @param systemFixed true if POST_NOTIFICATIONS should be SYSTEM_FIXED.
     * @param executor    the {@link Executor} on which to invoke the callback.
     * @param callback    callback this returns either success or an exception.
     */
    @RequiresPermission(MANAGE_DEVICE_LOCK_SERVICE_FROM_CONTROLLER)
    void setPostNotificationsSystemFixed(boolean systemFixed, Executor executor,
            @NonNull OutcomeReceiver<Void, Exception> callback);
}
