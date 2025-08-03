/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.server.wifi;

import android.annotation.NonNull;
import android.net.MacAddress;
import android.net.wifi.SoftApConfiguration;

import com.android.server.wifi.WifiNative.HostapdDeathEventHandler;
import com.android.server.wifi.WifiNative.SoftApHalCallback;

import java.io.PrintWriter;

/** Abstraction of HAL interface */
interface IHostapdHal {
    /**
     * Begin initializing the IHostapdHal object. Specific initialization logic differs
     * between the HIDL and AIDL implementations.
     *
     * @return true if the initialization routine was successful
     */
    boolean initialize();

    /**
     * Start hostapd daemon.
     */
    boolean startDaemon();

    /**
     * Enable/Disable verbose logging.
     *
     * @param verboseEnabled true to enable, false to disable.
     * @param halVerboseEnabled true to enable hal verbose logging, false to disable.
     */
    void enableVerboseLogging(boolean verboseEnabled, boolean halVerboseEnabled);

    /**
     * Add and start a new access point.
     *
     * @param ifaceName Name of the interface.
     * @param config Configuration to use for the AP.
     * @param isMetered Indicates the network is metered or not. Ignored in AIDL imp.
     * @param onFailureListener A runnable to be triggered on failure.
     * @return true on success, false otherwise.
     */
    boolean addAccessPoint(@NonNull String ifaceName,
            @NonNull SoftApConfiguration config, boolean isMetered,
            Runnable onFailureListener);

    /**
     * Remove a previously started access point.
     *
     * @param ifaceName Name of the interface.
     * @return true on success, false otherwise.
     */
    boolean removeAccessPoint(@NonNull String ifaceName);

    /**
     * Remove a previously connected client.
     *
     * @param ifaceName Name of the interface.
     * @param client Mac Address of the client.
     * @param reasonCode One of disconnect reason code which defined in {@link WifiManager}.
     * @return true on success, false otherwise.
     */
    boolean forceClientDisconnect(@NonNull String ifaceName,
            @NonNull MacAddress client, int reasonCode);

    /**
     * Register the provided callback handler for SoftAp events.
     * <p>
     * Note that only one callback can be registered at a time - any registration overrides previous
     * registrations.
     *
     * @param ifaceName Name of the interface.
     * @param callback Callback listener for AP events.
     * @return true on success, false on failure.
     */
    boolean registerApCallback(@NonNull String ifaceName,
            @NonNull SoftApHalCallback callback);

    /**
     * Returns whether or not the hostapd supports getting the AP info from the callback.
     */
    boolean isApInfoCallbackSupported();

    /**
     * Registers a death notification for hostapd.
     * @return Returns true on success.
     */
    boolean registerDeathHandler(@NonNull HostapdDeathEventHandler handler);

    /**
     * Deregisters a death notification for hostapd.
     * @return Returns true on success.
     */
    boolean deregisterDeathHandler();

    /**
     * Signals whether Initialization started successfully.
     */
    boolean isInitializationStarted();

    /**
     * Signals whether Initialization completed successfully.
     */
    boolean isInitializationComplete();

    /**
     * Terminate the hostapd daemon & wait for it's death.
     */
    void terminate();

    /**
     * Dump information about the specific implementation.
     */
    void dump(PrintWriter pw);
}
