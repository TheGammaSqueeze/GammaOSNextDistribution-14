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

import android.net.MacAddress;

import java.util.List;

/** Abstraction of WifiApIface */
public interface IWifiApIface {
    /**
     * Get the name of this interface.
     *
     * @return Name of this interface, or null on error.
     */
    String getName();

    /**
     * Get the names of the bridged AP instances.
     *
     * @return List containing the names of the bridged AP instances,
     *         or an empty vector for a non-bridged AP. Returns null
     *         if an error occurred.
     */
    List<String> getBridgedInstances();

    /**
     * Gets the factory MAC address of the interface.
     *
     * @return Factory MAC address of the interface, or null on error.
     */
    MacAddress getFactoryMacAddress();

    /**
     * Set the country code for this interface.
     *
     * @param countryCode two-letter country code (as ISO 3166).
     * @return true if successful, false otherwise.
     */
    boolean setCountryCode(byte[] countryCode);

    /**
     * Reset all the AP interfaces' MAC address to the factory MAC address.
     *
     * @return true if successful, false otherwise.
     */
    boolean resetToFactoryMacAddress();

    /**
     * Check whether {@link #setMacAddress(MacAddress)} is supported by this HAL.
     *
     * @return true if supported, false otherwise.
     */
    boolean isSetMacAddressSupported();

    /**
     * Changes the MAC address of the interface to the given MAC address.
     *
     * @param mac MAC address to change to.
     * @return true if successful, false otherwise.
     */
    boolean setMacAddress(MacAddress mac);
}
