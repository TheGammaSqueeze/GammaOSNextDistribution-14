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

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.hardware.wifi.WifiStatusCode;
import android.net.wifi.CoexUnsafeChannel;
import android.net.wifi.OuiKeyedData;
import android.net.wifi.WifiAvailableChannel;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiScanner;

import com.android.server.wifi.SarInfo;
import com.android.server.wifi.WifiNative;
import com.android.server.wifi.WlanWakeReasonAndCounts;

import java.util.List;

/** Abstraction of WifiChip */
public interface IWifiChip {
    /**
     * Configure the chip.
     *
     * @param modeId Mode that the chip must switch to, corresponding to the
     *               id property of the target ChipMode.
     * @return true if successful, false otherwise.
     */
    boolean configureChip(int modeId);

    /**
     * Create an AP interface on the chip.
     *
     * @param vendorData List of {@link OuiKeyedData} containing vendor-provided
     *                   configuration data. Empty list indicates no vendor data.
     * @return {@link WifiApIface} object, or null if a failure occurred.
     */
    @Nullable
    WifiApIface createApIface(@NonNull List<OuiKeyedData> vendorData);

    /**
     * Create a bridged AP interface on the chip.
     *
     * @param vendorData List of {@link OuiKeyedData} containing vendor-provided
     *                   configuration data. Empty list indicates no vendor data.
     * @return {@link WifiApIface} object, or null if a failure occurred.
     */
    @Nullable
    WifiApIface createBridgedApIface(@NonNull List<OuiKeyedData> vendorData);

    /**
     * Create a NAN interface on the chip.
     *
     * @return {@link WifiNanIface} object, or null if a failure occurred.
     */
    @Nullable
    WifiNanIface createNanIface();

    /**
     * Create a P2P interface on the chip.
     *
     * @return {@link WifiP2pIface} object, or null if a failure occurred.
     */
    @Nullable
    WifiP2pIface createP2pIface();

    /**
     * Create an RTTController instance. Implementation will decide the iface to use for
     * RTT operations.
     *
     * @return {@link WifiRttController} object, or null if a failure occurred.
     */
    @Nullable
    WifiRttController createRttController();

    /**
     * Create a STA iface on the chip.
     *
     * @return {@link WifiStaIface} object, or null if a failure occurred.
     */
    @Nullable
    WifiStaIface createStaIface();

    /**
     * Enable/disable alert notifications from the chip.
     *
     * @param enable true to enable, false to disable.
     * @return true if successful, false otherwise.
     */
    boolean enableDebugErrorAlerts(boolean enable);

    /**
     * Flush debug ring buffer data to files.
     *
     * @return true if successful, false otherwise.
     */
    boolean flushRingBufferToFile();

    /**
     * Force dump data into the corresponding ring buffer.
     *
     * @param ringName Name of the ring for which data collection should be forced.
     * @return true if successful, false otherwise.
     */
    boolean forceDumpToDebugRingBuffer(String ringName);

    /**
     * Get the AP interface corresponding to the provided ifaceName.
     *
     * @param ifaceName Name of the interface.
     * @return {@link WifiApIface} if the interface exists, null otherwise.
     */
    @Nullable
    WifiApIface getApIface(String ifaceName);

    /**
     * List all the AP iface names configured on the chip.
     * The corresponding |WifiApIface| object for any iface
     * can be retrieved using the |getApIface| method.
     *
     * @return List of all AP interface names on the chip, or null if an error occurred.
     */
    @Nullable
    List<String> getApIfaceNames();

    /**
     * Get the set of operation modes that the chip supports.
     *
     * @return List of modes supported by the device, or null if an error occurred.
     */
    @Nullable
    List<WifiChip.ChipMode> getAvailableModes();

    /**
     * Get the capabilities supported by this chip.
     * Call if no interfaces have been created on this chip.
     *
     * Note: This method can still be called safely after ifaces have been created,
     * but it is recommended to use {@link #getCapabilitiesAfterIfacesExist()} once
     * any ifaces are up.
     *
     * @return {@link WifiChip.Response} where the value is a bitset of
     *         WifiManager.WIFI_FEATURE_* values.
     */
    WifiChip.Response<Long> getCapabilitiesBeforeIfacesExist();

    /**
     * Get the capabilities supported by this chip.
     * Call if interfaces have been created on this chip.
     *
     * @return {@link WifiChip.Response} where the value is a bitset of
     *         WifiManager.WIFI_FEATURE_* values.
     */
    WifiChip.Response<Long> getCapabilitiesAfterIfacesExist();

    /**
     * Retrieve the Wi-Fi wakeup reason stats for debugging.
     *
     * @return {@link WlanWakeReasonAndCounts} object, or null if an error occurred.
     */
    @Nullable
    WlanWakeReasonAndCounts getDebugHostWakeReasonStats();

    /**
     * Get the status of all ring buffers supported by driver.
     *
     * @return List of {@link WifiNative.RingBufferStatus} corresponding to the
     *         status of each ring buffer on the device, or null if an error occurred.
     */
    @Nullable
    List<WifiNative.RingBufferStatus> getDebugRingBuffersStatus();

    /**
     * Get the ID assigned to this chip.
     *
     * @return Chip ID, or -1 if an error occurred.
     */
    int getId();

    /**
     * Get the current mode that the chip is in.
     *
     * @return {@link WifiChip.Response} where the value is the mode that the chip
     *         is currently configured to.
     */
    WifiChip.Response<Integer> getMode();

    /**
     * Get the NAN interface corresponding to the provided ifaceName.
     *
     * @param ifaceName Name of the interface.
     * @return {@link WifiNanIface} if the interface exists, null otherwise.
     */
    @Nullable
    WifiNanIface getNanIface(String ifaceName);

    /**
     * List all the NAN iface names configured on the chip.
     * The corresponding |WifiNanIface| object for any iface
     * can be retrieved using the |getNanIface| method.
     *
     * @return List of all NAN interface names on the chip, or null if an error occurred.
     */
    @Nullable
    List<String> getNanIfaceNames();

    /**
     * Get the P2P interface corresponding to the provided ifaceName.
     *
     * @param ifaceName Name of the interface.
     * @return {@link WifiP2pIface} if the interface exists, null otherwise.
     */
    @Nullable
    WifiP2pIface getP2pIface(String ifaceName);

    /**
     * List all the P2P iface names configured on the chip.
     * The corresponding |WifiP2pIface| object for any iface
     * can be retrieved using the |getP2pIface| method.
     *
     * @return List of all P2P interface names on the chip, or null if an error occurred.
     */
    @Nullable
    List<String> getP2pIfaceNames();

    /**
     * Get the STA interface corresponding to the provided ifaceName.
     *
     * @param ifaceName Name of the interface.
     * @return {@link WifiStaIface} if the interface exists, null otherwise.
     */
    @Nullable
    WifiStaIface getStaIface(String ifaceName);

    /**
     * List all the STA iface names configured on the chip.
     * The corresponding |WifiStaIface| object for any iface
     * can be retrieved using the |getStaIface| method.
     *
     * @return List of all P2P interface names on the chip, or null if an error occurred.
     */
    @Nullable
    List<String> getStaIfaceNames();

    /**
     * Retrieve the list of all the possible radio combinations supported by this chip.
     *
     * @return List of all possible radio combinations, or null if an error occurred.
     */
    @Nullable
    List<WifiChip.WifiRadioCombination> getSupportedRadioCombinations();

    /**
     * Retrieve the chip capabilities.
     *
     * @return |WifiChipCapabilities| representation of wifi chip capabilities or null if
     * an error occurred or not available.
     */
    WifiChip.WifiChipCapabilities getWifiChipCapabilities();

    /**
     * Retrieve a list of usable Wifi channels for the specified band and operational modes.
     *
     * @param band Band for which the list of usable channels is requested.
     * @param mode Bitmask of modes that the caller is interested in.
     * @param filter Bitmask of filters. Specifies whether driver should filter
     *        channels based on additional criteria. If no filter is specified,
     *        then the driver should return usable channels purely based on
     *        regulatory constraints.
     * @return List of channels represented by {@link WifiAvailableChannel},
     *         or null if an error occurred.
     */
    @Nullable
    List<WifiAvailableChannel> getUsableChannels(@WifiScanner.WifiBand int band,
            @WifiAvailableChannel.OpMode int mode, @WifiAvailableChannel.Filter int filter);

    /**
     * Register for chip event callbacks.
     *
     * @param callback Instance of {@link WifiChip.Callback}
     * @return true if successful, false otherwise.
     */
    boolean registerCallback(WifiChip.Callback callback);

    /**
     * Removes the AP interface with the provided ifaceName.
     *
     * @param ifaceName Name of the iface.
     * @return true if successful, false otherwise.
     */
    boolean removeApIface(String ifaceName);

    /**
     * Removes an instance of AP iface with name |ifaceName| from the
     * bridged AP with name |brIfaceName|.
     *
     * Note: Use {@link #removeApIface(String)} with the |brIfaceName| to remove the bridged iface.
     *
     * @param brIfaceName Name of the bridged AP iface.
     * @param ifaceName Name of the AP instance.
     * @return true if successful, false otherwise.
     */
    boolean removeIfaceInstanceFromBridgedApIface(String brIfaceName, String ifaceName);

    /**
     * Removes the NAN interface with the provided ifaceName.
     *
     * @param ifaceName Name of the iface.
     * @return true if successful, false otherwise.
     */
    boolean removeNanIface(String ifaceName);

    /**
     * Removes the P2P interface with the provided ifaceName.
     *
     * @param ifaceName Name of the iface.
     * @return true if successful, false otherwise.
     */
    boolean removeP2pIface(String ifaceName);

    /**
     * Removes the STA interface with the provided ifaceName.
     *
     * @param ifaceName Name of the iface.
     * @return true if successful, false otherwise.
     */
    boolean removeStaIface(String ifaceName);

    /**
     * Request information about the chip.
     *
     * @return Instance of {@link WifiChip.ChipDebugInfo}, or null if an error occurred.
     */
    @Nullable
    WifiChip.ChipDebugInfo requestChipDebugInfo();

    /**
     * Request vendor debug info from the driver.
     *
     * @return Byte array retrieved from the driver, or null if an error occurred.
     */
    @Nullable
    byte[] requestDriverDebugDump();

    /**
     * Request vendor debug info from the firmware.
     *
     * @return Byte array retrieved from the firmware, or null if an error occurred.
     */
    @Nullable
    byte[] requestFirmwareDebugDump();

    /**
     * Select one of the preset TX power scenarios.
     *
     * @param sarInfo SarInfo to select the proper scenario.
     * @return true if successful, false otherwise.
     */
    boolean selectTxPowerScenario(SarInfo sarInfo);

    /**
     * Set the current coex unsafe channels to avoid and their restrictions.
     *
     * @param unsafeChannels List of {@link CoexUnsafeChannel} to avoid.
     * @param restrictions int containing a bitwise-OR combination of
     *                     {@link android.net.wifi.WifiManager.CoexRestriction}.
     * @return true if successful, false otherwise.
     */
    boolean setCoexUnsafeChannels(List<CoexUnsafeChannel> unsafeChannels, int restrictions);

    /**
     * Set the country code for this Wifi chip.
     *
     * @param code 2-byte country code to set (as defined in ISO 3166).
     * @return true if successful, false otherwise.
     */
    boolean setCountryCode(byte[] code);

    /**
     * Enable/disable low-latency mode.
     *
     * @param enable true to enable, false to disable.
     * @return true if successful, false otherwise.
     */
    boolean setLowLatencyMode(boolean enable);

    /**
     * Set primary connection when multiple STA ifaces are active.
     *
     * @param ifaceName Name of the interface.
     * @return true if successful, false otherwise.
     */
    boolean setMultiStaPrimaryConnection(String ifaceName);

    /**
     * Set use-case when multiple STA ifaces are active.
     *
     * @param useCase use case from {@link WifiNative.MultiStaUseCase}.
     * @return true if successful, false otherwise.
     */
    boolean setMultiStaUseCase(@WifiNative.MultiStaUseCase int useCase);

    /**
     * Control debug data collection.
     *
     * @param ringName Name of the ring for which data collection is to start.
     * @param verboseLevel 0 to 3, inclusive. 0 stops logging.
     * @param maxIntervalInSec Maximum interval between reports; ignore if 0.
     * @param minDataSizeInBytes Minimum data size in buffer for report; ignore if 0.
     * @return true if successful, false otherwise.
     */
    boolean startLoggingToDebugRingBuffer(String ringName, int verboseLevel, int maxIntervalInSec,
            int minDataSizeInBytes);

    /**
     * Stop the debug data collection for all ring buffers.
     *
     * @return true if successful, false otherwise.
     */
    boolean stopLoggingToDebugRingBuffer();

    /**
     * Trigger subsystem restart.
     *
     * Use if the framework detects a problem (e.g. connection failure),
     * in order to attempt recovery.
     *
     * @return true if successful, false otherwise.
     */
    boolean triggerSubsystemRestart();

    /**
     * Set MLO mode for the chip. See {@link WifiManager#setMloMode(int, Executor, Consumer)} and
     * {@link android.net.wifi.WifiManager.MloMode}.
     *
     * @param mode MLO mode {@link android.net.wifi.WifiManager.MloMode}
     * @return {@code true} if success, otherwise false.
     */
    @WifiStatusCode int setMloMode(@WifiManager.MloMode int mode);

    /**
     * Enable/disable the feature of allowing current STA-connected channel for WFA GO, SAP and
     * Aware when the regulatory allows.
     *
     * @param enableIndoorChannel enable or disable indoor channel.
     * @param enableDfsChannel    enable or disable DFS channel.
     * @return true if successful, false otherwise.
     */
    boolean enableStaChannelForPeerNetwork(boolean enableIndoorChannel, boolean enableDfsChannel);

    /**
     * Sends the AFC allowed channels and frequencies to the driver.
     *
     * @param afcChannelAllowance the allowed frequencies and channels received from
     * querying the AFC server.
     * @return whether the channel allowance was set successfully.
     */
    boolean setAfcChannelAllowance(WifiChip.AfcChannelAllowance afcChannelAllowance);
}
