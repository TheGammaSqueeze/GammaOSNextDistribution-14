/*
 * Copyright (C) 2020 The Android Open Source Project
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
import android.annotation.Nullable;
import android.net.DhcpResultsParcelable;
import android.net.MacAddress;
import android.net.Network;
import android.net.wifi.IWifiConnectedNetworkScorer;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.DeviceMobilityState;
import android.net.wifi.hotspot2.IProvisioningCallback;
import android.net.wifi.hotspot2.OsuProvider;
import android.net.wifi.nl80211.DeviceWiphyCapabilities;
import android.os.IBinder;
import android.os.Message;
import android.os.WorkSource;

import com.android.server.wifi.util.ActionListenerWrapper;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Default implementations for {@link ClientMode} APIs.
 */
public interface ClientModeDefaults extends ClientMode {
    default void dump(FileDescriptor fd, PrintWriter pw, String[] args) { }

    default void connectNetwork(NetworkUpdateResult result, ActionListenerWrapper wrapper,
            int callingUid, @NonNull String packageName, @Nullable String attributionTag) {
        // wifi off, can't connect.
        wrapper.sendFailure(WifiManager.ActionListener.FAILURE_BUSY);
    }

    default void saveNetwork(NetworkUpdateResult result, ActionListenerWrapper wrapper,
            int callingUid, @NonNull String packageName) {
        // wifi off, nothing more to do here.
        wrapper.sendSuccess();
    }

    default void disconnect() { }

    default void reconnect(WorkSource ws) { }

    default void reassociate() { }

    default void startConnectToNetwork(int networkId, int uid, String bssid) { }

    default void startRoamToNetwork(int networkId, String bssid) { }

    default void onDeviceMobilityStateUpdated(@DeviceMobilityState int newState) { }

    default void setLinkLayerStatsPollingInterval(int newIntervalMs) { }

    default boolean setWifiConnectedNetworkScorer(
            IBinder binder, IWifiConnectedNetworkScorer scorer, int callerUid) {
        // don't fail the public API when wifi is off.
        return true;
    }

    default void clearWifiConnectedNetworkScorer() { }

    /**
     * Notify the connected network scorer of the user accepting a network switch.
     */
    default void onNetworkSwitchAccepted(int targetNetworkId, String targetBssid) { }

    /**
     * Notify the connected network scorer of the user rejecting a network switch.
     */
    default void onNetworkSwitchRejected(int targetNetworkId, String targetBssid) { }

    default void resetSimAuthNetworks(@ClientModeImpl.ResetSimReason int resetReason) { }

    default void onBluetoothConnectionStateChanged() { }

    default WifiInfo getConnectionInfo() {
        return new WifiInfo();
    }

    default boolean syncQueryPasspointIcon(long bssid, String fileName) {
        return false;
    }

    default Network getCurrentNetwork() {
        return null;
    }

    default DhcpResultsParcelable syncGetDhcpResultsParcelable() {
        return new DhcpResultsParcelable();
    }

    default boolean syncStartSubscriptionProvisioning(int callingUid, OsuProvider provider,
            IProvisioningCallback callback) {
        return false;
    }

    /** Enable TDLS session with remote MAC address */
    default boolean enableTdls(String remoteMacAddress, boolean enable) {
        return false;
    }

    /** Enable TDLS session with remote IP address */
    default boolean enableTdlsWithRemoteIpAddress(String remoteIpAddress, boolean enable) {
        return false;
    }

    /** Check if a TDLS session can be established */
    default boolean isTdlsOperationCurrentlyAvailable() {
        return false;
    }

    /** The maximum number of TDLS sessions supported by the device */
    default int getMaxSupportedConcurrentTdlsSessions() {
        return -1;
    }

    /** The number of Peer mac addresses configured in the device for establishing a TDLS session */
    default int getNumberOfEnabledTdlsSessions() {
        return 0;
    }

    default void dumpIpClient(FileDescriptor fd, PrintWriter pw, String[] args) { }

    default void dumpWifiScoreReport(FileDescriptor fd, PrintWriter pw, String[] args) { }

    default void enableVerboseLogging(boolean verbose) { }

    default String getFactoryMacAddress() {
        return null;
    }

    default WifiConfiguration getConnectedWifiConfiguration() {
        return null;
    }

    default WifiConfiguration getConnectingWifiConfiguration() {
        return null;
    }

    default String getConnectedBssid() {
        return null;
    }

    default String getConnectingBssid() {
        return null;
    }

    default WifiLinkLayerStats getWifiLinkLayerStats() {
        return null;
    }

    default boolean setPowerSave(@PowerSaveClientType int client, boolean ps) {
        return false;
    }

    default boolean enablePowerSave() {
        return false;
    }

    default boolean setLowLatencyMode(boolean enabled) {
        return false;
    }

    default WifiMulticastLockManager.FilterController getMcastLockManagerFilterController() {
        return new WifiMulticastLockManager.FilterController() {
            @Override
            public void startFilteringMulticastPackets() { }
            @Override
            public void stopFilteringMulticastPackets() { }
        };
    }

    default boolean isConnected() {
        return false;
    }

    default boolean isConnecting() {
        return false;
    }

    default boolean isRoaming() {
        return false;
    }

    default boolean isDisconnected() {
        return true;
    }

    default boolean isIpProvisioningTimedOut() {
        return false;
    }

    default boolean isSupplicantTransientState() {
        return false;
    }

    default void onCellularConnectivityChanged(@WifiDataStall.CellularDataStatusCode int status) {}

    default void probeLink(ClientMode.LinkProbeCallback callback, int mcs) {
        callback.onFailure(ClientMode.LinkProbeCallback.LINK_PROBE_ERROR_NOT_CONNECTED);
    }

    default void sendMessageToClientModeImpl(Message msg) { }

    default void setMboCellularDataStatus(boolean available) { }

    default WifiNative.RoamingCapabilities getRoamingCapabilities() {
        return null;
    }

    default boolean configureRoaming(WifiNative.RoamingConfig config) {
        return false;
    }

    default boolean enableRoaming(boolean enabled) {
        return false;
    }

    default boolean setCountryCode(String countryCode) {
        return false;
    }

    default List<WifiNative.TxFateReport> getTxPktFates() {
        return new ArrayList<>();
    }

    default List<WifiNative.RxFateReport> getRxPktFates() {
        return new ArrayList<>();
    }

    default DeviceWiphyCapabilities getDeviceWiphyCapabilities() {
        return null;
    }

    default boolean requestAnqp(String bssid, Set<Integer> anqpIds, Set<Integer> hs20Subtypes) {
        return false;
    }

    default boolean requestVenueUrlAnqp(String bssid) {
        return false;
    }

    default boolean requestIcon(String bssid, String fileName) {
        return false;
    }

    @Override
    default void setShouldReduceNetworkScore(boolean shouldReduceNetworkScore) { }

    @Override
    default void updateCapabilities() { }

    @Override
    default boolean isAffiliatedLinkBssid(MacAddress bssid) {
        return false;
    }

    @Override
    default boolean isMlo() {
        return false;
    }

    @Override
    default void onIdleModeChanged(boolean isIdle) { }
}
