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

package com.android.server.wifi.hal;

import android.annotation.Nullable;

import java.util.List;

/** Abstraction of the root Wifi HAL */
public interface IWifiHal {
    /**
     * Get the chip corresponding to the provided chipId.
     *
     * @param chipId ID of the chip.
     * @return {@link WifiChip} if successful, null otherwise.
     */
    @Nullable
    WifiChip getChip(int chipId);

    /**
     * Retrieves the list of all chip id's on the device.
     * The corresponding |WifiChip| object for any chip can be
     * retrieved using the |getChip| method.
     *
     * @return List of all chip id's on the device, or null if an error occurred.
     */
    @Nullable
    List<Integer> getChipIds();

    /**
     * Register for HAL event callbacks.
     *
     * @param callback Instance of {@link WifiHal.Callback}
     * @return true if successful, false otherwise.
     */
    boolean registerEventCallback(WifiHal.Callback callback);

    /**
     * Initialize the Wi-Fi HAL service. Must initialize before calling {@link #start()}
     *
     * @param deathRecipient Instance of {@link WifiHal.DeathRecipient}
     */
    void initialize(WifiHal.DeathRecipient deathRecipient);

    /**
     * Check if the initialization is complete and the HAL is ready to accept commands.
     *
     * @return true if initialization is complete, false otherwise.
     */
    boolean isInitializationComplete();

    /**
     * Check if the Wi-Fi HAL supported on this device.
     *
     * @return true if supported, false otherwise.
     */
    boolean isSupported();

    /**
     * Start the Wi-Fi HAL.
     *
     * @return {@link WifiHal.WifiStatusCode} indicating the result.
     */
    @WifiHal.WifiStatusCode int start();

    /**
     * Get the current state of the HAL.
     *
     * @return true if started, false otherwise.
     */
    boolean isStarted();

    /**
     * Stop the Wi-Fi HAL.
     *
     * Note: Calling stop() and then start() is a valid way of resetting state in
     * the HAL, driver, and firmware.
     *
     * @return true if successful, false otherwise.
     */
    boolean stop();

    /**
     * Invalidate the Wi-Fi HAL. Call when a significant error occurred external to the root
     * Wi-Fi HAL, for instance in a Wi-Fi chip retrieved from this HAL.
     */
    void invalidate();
}
