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

package com.android.server.devicelock;

import android.os.OutcomeReceiver;

/**
 * Connector class that acts as the interface between the system service and the DLC service.
 */
public interface DeviceLockControllerConnector {

    /**
     * Unbinds the Device Lock Controller service.
     */
    void unbind();

    /**
     * Locks the device.
     */
    void lockDevice(OutcomeReceiver<Void, Exception> callback);

    /**
     * Unlocks the device.
     */
    void unlockDevice(OutcomeReceiver<Void, Exception> callback);

    /**
     * Returns whether the device is currently locked.
     */
    void isDeviceLocked(OutcomeReceiver<Boolean, Exception> callback);

    /**
     * Gets the device id.
     */
    void getDeviceId(OutcomeReceiver<String, Exception> callback);

    /**
     * Clears the device restrictions
     */
    void clearDeviceRestrictions(OutcomeReceiver<Void, Exception> callback);

    /**
     * Called when the user has switched.
     */
    void onUserSwitching(OutcomeReceiver<Void, Exception> callback);

    /**
     * Called when the user is unlocked.
     */
    void onUserUnlocked(OutcomeReceiver<Void, Exception> callback);

    /**
     * Called when the user has completed setup.
     */
    void onUserSetupCompleted(OutcomeReceiver<Void, Exception> callback);

    /**
     * Called when the controller or kiosk app has crashed.
     */
    void onAppCrashed(boolean isKiosk, OutcomeReceiver<Void, Exception> callback);
}
