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
import android.hardware.wifi.WifiStatusCode;
import android.net.MacAddress;
import android.net.apf.ApfCapabilities;
import android.net.wifi.WifiManager.RoamingMode;
import android.net.wifi.WifiScanner;
import android.net.wifi.twt.TwtRequest;
import android.os.Bundle;

import com.android.server.wifi.WifiLinkLayerStats;
import com.android.server.wifi.WifiLoggerHal;
import com.android.server.wifi.WifiNative;

import java.util.List;

/** Abstraction of WifiStaIface */
public interface IWifiStaIface {
    /**
     * Register for event callbacks.
     *
     * @param callback Instance of {@link WifiStaIface.Callback}
     * @return true if successful, false otherwise.
     */
    boolean registerFrameworkCallback(WifiStaIface.Callback callback);

    /**
     * Get the name of this interface.
     *
     * @return Name of this interface, or null on error.
     */
    @Nullable
    String getName();

    /**
     * Configure roaming control parameters.
     *
     * @param bssidBlocklist List of BSSIDs that are blocklisted for roaming.
     * @param ssidAllowlist List of SSIDs that are allowlisted for roaming.
     * @return true if successful, false otherwise.
     */
    boolean configureRoaming(List<MacAddress> bssidBlocklist, List<byte[]> ssidAllowlist);

    /**
     * Enable link layer stats collection.
     *
     * Radio statistics (once started) must not stop until disabled.
     * Iface statistics (once started) reset and start afresh after each
     * connection until disabled.
     *
     * @param debug true to enable field debug mode, false to disable. Driver
     *        must collect all statistics regardless of performance impact.
     * @return true if successful, false otherwise.
     */
    boolean enableLinkLayerStatsCollection(boolean debug);

    /**
     * Enable/disable the neighbor discovery offload functionality in the firmware.
     *
     * @param enable true to enable, false to disable.
     * @return true if successful, false otherwise.
     */
    boolean enableNdOffload(boolean enable);

    /**
     * Used to query additional information about the chip's APF capabilities.
     *
     * @return Instance of {@link ApfCapabilities}.
     */
    ApfCapabilities getApfPacketFilterCapabilities();

    /**
     * Used to query additional information about the chip's Background Scan capabilities.
     *
     * @return Instance of {@link WifiNative.ScanCapabilities}, or null on error.
     */
    @Nullable
    WifiNative.ScanCapabilities getBackgroundScanCapabilities();

    /**
     * Get the capabilities supported by this STA iface.
     *
     * @return Bitset of WifiManager.WIFI_FEATURE_* values.
     */
    long getCapabilities();

    /**
     * Retrieve the fates of inbound packets.
     * Reports the inbound frames for the most recent association (space allowing).
     *
     * @return List of RxFateReports up to size {@link WifiLoggerHal#MAX_FATE_LOG_LEN},
     *        or empty list on failure.
     */
    List<WifiNative.RxFateReport> getDebugRxPacketFates();

    /**
     * Retrieve fates of outbound packets.
     * Reports the outbound frames for the most recent association (space allowing).
     *
     * @return List of TxFateReports up to size {@link WifiLoggerHal#MAX_FATE_LOG_LEN},
     *         or empty list on failure.
     */
    List<WifiNative.TxFateReport> getDebugTxPacketFates();

    /**
     * Get the factory MAC address of the STA interface.
     *
     * @return Factory MAC address of the STA interface, or null on error.
     */
    @Nullable
    MacAddress getFactoryMacAddress();

    /**
     * Retrieve the cached scan data.
     *
     * @return Instance of {@link ScanData}, or null on error.
     */
    @Nullable
    WifiScanner.ScanData getCachedScanData();

    /**
     * Retrieve the latest link layer stats.
     * Note: will fail if link layer stats collection has not been explicitly enabled.
     *
     * @return Instance of {@link WifiLinkLayerStats}, or null on error.
     */
    @Nullable
    WifiLinkLayerStats getLinkLayerStats();

    /**
     * Get roaming control capabilities.
     *
     * @return Instance of {@link WifiNative.RoamingCapabilities}, or null on error.
     */
    @Nullable
    WifiNative.RoamingCapabilities getRoamingCapabilities();

    /**
     * Install an APF program on this interface, replacing any existing program.
     *
     * @param program APF Program to be set.
     * @return true if successful, false otherwise.
     */
    boolean installApfPacketFilter(byte[] program);

    /**
     * Read the APF program and data buffer on this interface.
     *
     * @return Buffer returned by the driver, or null if an error occurred.
     */
    @Nullable
    byte[] readApfPacketFilterData();

    /**
     * Changes the MAC address of the interface to the given MAC address.
     *
     * @param mac MAC address to change to.
     * @return true if successful, false otherwise.
     */
    boolean setMacAddress(MacAddress mac);

    /**
     * Enable/disable firmware roaming.
     *
     * @param state State of the roaming control.
     * @return SET_FIRMWARE_ROAMING_SUCCESS, SET_FIRMWARE_ROAMING_FAILURE,
     *         or SET_FIRMWARE_ROAMING_BUSY
     */
    @WifiNative.RoamingEnableStatus int setRoamingState(@WifiNative.RoamingEnableState int state);

    /**
     * Enable/disable scan-only mode for this interface.
     *
     * @param enable True to enable scan only mode, false to disable.
     * @return true if successful, false otherwise.
     */
    boolean setScanMode(boolean enable);

    /**
     * Starts a background scan.
     * Note: any ongoing scan will be stopped first.
     *
     * @param cmdId Command ID to use for this invocation.
     * @param params Background scan parameters.
     * @return true if successful, false otherwise.
     */
    boolean startBackgroundScan(int cmdId, WifiStaIface.StaBackgroundScanParameters params);

    /**
     * Start packet fate monitoring. Once started, monitoring remains active until
     * the HAL is unloaded.
     *
     * @return true if successful, false otherwise.
     */
    boolean startDebugPacketFateMonitoring();

    /**
     * Start RSSI monitoring on the currently connected access point.
     *
     * @param cmdId Command ID to use for this invocation.
     * @param maxRssi Maximum RSSI threshold.
     * @param minRssi Minimum RSSI threshold.
     * @return true if successful, false otherwise.
     */
    boolean startRssiMonitoring(int cmdId, int maxRssi, int minRssi);

    /**
     * Start sending the specified keep-alive packets periodically.
     *
     * @param cmdId Command ID to use for this invocation.
     * @param ipPacketData IP packet contents to be transmitted.
     * @param etherType Ether type to be set in the ethernet frame transmitted.
     * @param srcAddress Source MAC address of the packet.
     * @param dstAddress Destination MAC address of the packet.
     * @param periodInMs Interval at which this packet must be transmitted.
     * @return true if successful, false otherwise.
     */
    boolean startSendingKeepAlivePackets(int cmdId, byte[] ipPacketData, int etherType,
            MacAddress srcAddress, MacAddress dstAddress, int periodInMs);

    /**
     * Stop the current background scan.
     *
     * @param cmdId Command ID corresponding to the request.
     * @return true if successful, false otherwise.
     */
    boolean stopBackgroundScan(int cmdId);

    /**
     * Stop RSSI monitoring.
     *
     * @param cmdId Command ID corresponding to the request.
     * @return true if successful, false otherwise.
     */
    boolean stopRssiMonitoring(int cmdId);

    /**
     * Stop sending the specified keep-alive packets.
     *
     * @param cmdId Command ID corresponding to the request.
     * @return true if successful, false otherwise.
     */
    boolean stopSendingKeepAlivePackets(int cmdId);

    /**
     * Set maximum acceptable DTIM multiplier to hardware driver. Any multiplier larger than the
     * maximum value must not be accepted, it will cause packet loss higher than what the system
     * can accept, which will cause unexpected behavior for apps, and may interrupt the network
     * connection.
     *
     * @param multiplier maximum DTIM multiplier value to set.
     * @return true if successful, false otherwise.
     */
    boolean setDtimMultiplier(int multiplier);

    /**
     * Set the roaming mode.
     *
     * @param roamingMode {@link android.net.wifi.WifiManager.RoamingMode}.
     * @return {@link WifiStatusCode#SUCCESS} if success, otherwise error code.
     */
    @WifiStatusCode int setRoamingMode(@RoamingMode int roamingMode);

    /**
     * Get target wake time (TWT) capabilities.
     *
     * @return TWT capabilities as Bundle
     */
    default Bundle getTwtCapabilities() {
        return null;
    }

    /**
     * Set up a TWT session
     *
     * @param cmdId Command ID to use for this invocation.
     * @param twtRequest TWT request configuration to setup TWT session
     * @return true if successful, false otherwise.
     */
    default boolean setupTwtSession(int cmdId, TwtRequest twtRequest) {
        return false;
    }

    /**
     * Teardown a TWT session.
     *
     * @param cmdId Command ID to use for this invocation.
     * @param sessionId TWT session identifier
     * @return true if successful, false otherwise.
     */
    default boolean tearDownTwtSession(int cmdId, int sessionId) {
        return false;
    }

    /**
     * Get stats for the TWT session.
     *
     * @param cmdId Command ID to use for this invocation.
     * @param sessionId TWT session identifier
     * @return true if successful, false otherwise.
     */
    default boolean getStatsTwtSession(int cmdId, int sessionId) {
        return false;
    }
}
