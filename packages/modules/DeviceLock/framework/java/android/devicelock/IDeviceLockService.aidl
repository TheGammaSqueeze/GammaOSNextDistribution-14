/*
 * Copyright (c) 2022, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.devicelock;

import android.devicelock.IGetKioskAppsCallback;
import android.devicelock.IGetDeviceIdCallback;
import android.devicelock.IIsDeviceLockedCallback;
import android.devicelock.ILockUnlockDeviceCallback;

import android.os.RemoteCallback;

/**
 * Binder interface to communicate with DeviceLockService.
 * {@hide}
 */
oneway interface IDeviceLockService {
    /**
     * Asynchronously lock the device.
     */
    void lockDevice(in ILockUnlockDeviceCallback callback);

    /**
     * Asynchronously unlock the device.
     */
    void unlockDevice(in ILockUnlockDeviceCallback callback);

    /**
     * Asynchronously retrieve the device lock status.
     */
    void isDeviceLocked(in IIsDeviceLockedCallback callback);

    /**
     * Asynchronously retrieve the device identifier.
     */
    void getDeviceId(in IGetDeviceIdCallback callback);

    /**
     * Constant corresponding to a financed device role.
     * Returned by {@link #getKioskApps}.
     */
    const int DEVICE_LOCK_ROLE_FINANCING = 0;

    /**
     * Asynchronously retrieve the Kiosk apps roles and package names.
     */
    void getKioskApps(in IGetKioskAppsCallback callback);

    // The following are for calls initiated by the Controller.

    // Value is a boolean for success (true) or failure (false).
    const String KEY_REMOTE_CALLBACK_RESULT = "KEY_REMOTE_CALLBACK_RESULT";

    /**
     * Add the android.app.role.FINANCED_DEVICE_KIOSK role to the kiosk app.
     */
    void addFinancedDeviceKioskRole(in String packageName, in RemoteCallback remoteCallback);

    /**
     * Remove the android.app.role.FINANCED_DEVICE_KIOSK role from the kiosk app.
     */
    void removeFinancedDeviceKioskRole(in String packageName, in RemoteCallback remoteCallback);

    /**
     * Set the exempt from activity background restrction app op mode for the calling uid.
     */
    void setCallerExemptFromActivityBgStartRestrictionState(in boolean exempt,
     in RemoteCallback remoteCallback);

    /**
     * Set whether the caller should be allowed to send undismissible notifications.
     */
    void setCallerAllowedToSendUndismissibleNotifications(in boolean allowed,
     in RemoteCallback remoteCallback);

    /**
     * Set the exempt from hibernation, battery usage, data usage restrictions state for the
     * given uid.
     */
    void setUidExemptFromRestrictionsState(in int uid, in boolean exempt,
     in RemoteCallback remoteCallback);

    /**
     * Enable kiosk keepalive.
     */
     void enableKioskKeepalive(in String packageName, in RemoteCallback remoteCallback);

    /**
     * Disable kiosk keepalive.
     */
     void disableKioskKeepalive(in RemoteCallback remoteCallback);

    /**
     * Enable controller keepalive.
     */
     void enableControllerKeepalive(in RemoteCallback remoteCallback);

    /**
     * Disable controller keepalive.
     */
     void disableControllerKeepalive(in RemoteCallback remoteCallback);

    /**
     * Set device finalized.
     */
     void setDeviceFinalized(in boolean finalized, in RemoteCallback remoteCallback);

     /**
      * Set/clear controller POST_NOTIFICATIONS FLAG_PERMISSION_SYSTEM_FIXED flag.
      */
     void setPostNotificationsSystemFixed(in boolean systemFixed, in RemoteCallback remoteCallback);
}
