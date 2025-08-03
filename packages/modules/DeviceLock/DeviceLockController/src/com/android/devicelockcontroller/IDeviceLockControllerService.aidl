/**
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

package com.android.devicelockcontroller;

import android.os.RemoteCallback;

/**
 * Binder interface to communicate with DeviceLockController.
 * {@hide}
 */
oneway interface IDeviceLockControllerService {
    /**
     * Key used to store the result (return value) of a call.
     */
    const String KEY_RESULT = "KEY_RESULT";

    /**
     * Key used to store a ParcelableException.
     */
    const String KEY_PARCELABLE_EXCEPTION = "KEY_PARCELABLE_EXCEPTION";

    /**
     * Locks the device.
     */
    void lockDevice(in RemoteCallback callback);

    /**
     * Unlocks the device.
     */
    void unlockDevice(in RemoteCallback callback);

    /**
     * Outputs true result if device is locked.
     */
    void isDeviceLocked(in RemoteCallback callback);

    /**
     * Gets the device identifier.
     */
    void getDeviceIdentifier(in RemoteCallback callback);

    /**
     * Clears all device restrictions which removes the device from further policy management.
     */
    void clearDeviceRestrictions(in RemoteCallback callback);

    /**
     * Called when a user has just been switched to.
     *
     * Unlike the system service equivalent, this is NOT guaranteed to called in order with other
     * lifecycle events (e.g. before onUserUnlocked).
     */
    void onUserSwitching(in RemoteCallback callback);

    /**
     * Called when a user has been unlocked and credential encrypted storage is available.
     *
     * Unlike the system service equivalent, this is NOT guaranteed to called in order with other
     * lifecycle events (e.g. after onUserSwitching).
     */
    void onUserUnlocked(in RemoteCallback callback);

    /**
     * Called when a user has completed set-up wizard.
     */
    void onUserSetupCompleted(in RemoteCallback callback);

    /**
     * Called when the controller or kiosk app has crashed.
     */
    void onAppCrashed(in boolean isKiosk, in RemoteCallback callback);
}
