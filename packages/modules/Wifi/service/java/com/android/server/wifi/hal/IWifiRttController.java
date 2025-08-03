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
import android.net.MacAddress;
import android.net.wifi.rtt.RangingRequest;

import java.io.PrintWriter;
import java.util.List;

interface IWifiRttController {
    /**
     * Set up the IWifiRttController.
     *
     * @return true if successful, false otherwise.
     */
    boolean setup();

    /**
     * Enable/disable verbose logging.
     */
    void enableVerboseLogging(boolean verbose);

    /**
     * Register a callback to receive ranging results.
     *
     * @param callback Callback to register.
     */
    void registerRangingResultsCallback(
            WifiRttController.RttControllerRangingResultsCallback callback);

    /**
     * Check whether the RTT controller is valid.
     *
     * @return true if the RTT controller is valid, false otherwise or if an error occurred.
     */
    boolean validate();

    /**
     * Get the RTT capabilities.
     *
     * @return Capabilities, or null if they could not be retrieved.
     */
    @Nullable
    WifiRttController.Capabilities getRttCapabilities();

    /**
     * Issue a range request to the HAL.
     *
     * @param cmdId Command ID for the request. Will be used in the corresponding response from
     *              {@link HalDeviceManager.InterfaceRttControllerLifecycleCallback}
     *              onRangingResults()
     * @param request Range request.
     * @return true if successful, false otherwise.
     */
    boolean rangeRequest(int cmdId, RangingRequest request);

    /**
     * Cancel an outstanding ranging request.
     *
     * Note: No guarantees of execution. Can ignore any results which are returned for the
     * canceled request.
     *
     * @param cmdId The cmdId issued with the original rangeRequest command.
     * @param macAddresses A list of MAC addresses for which to cancel the operation.
     * @return true for success, false for failure.
     */
    boolean rangeCancel(int cmdId, List<MacAddress> macAddresses);

    /**
     * Dump the internal state of the class.
     */
    void dump(PrintWriter pw);
}
