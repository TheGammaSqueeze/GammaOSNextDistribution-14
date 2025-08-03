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

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.net.DhcpResultsParcelable;
import android.net.MacAddress;
import android.net.Network;
import android.net.wifi.IWifiConnectedNetworkScorer;
import android.net.wifi.WifiAnnotations;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager.DeviceMobilityState;
import android.net.wifi.hotspot2.IProvisioningCallback;
import android.net.wifi.hotspot2.OsuProvider;
import android.net.wifi.nl80211.DeviceWiphyCapabilities;
import android.net.wifi.nl80211.WifiNl80211Manager;
import android.os.IBinder;
import android.os.Message;
import android.os.WorkSource;

import com.android.server.wifi.WifiNative.RxFateReport;
import com.android.server.wifi.WifiNative.TxFateReport;
import com.android.server.wifi.util.ActionListenerWrapper;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;
import java.util.Set;

/**
 * This interface is used to respond to calls independent of a STA's current mode.
 * If the STA is in scan only mode, ClientMode is implemented using {@link ScanOnlyModeImpl}.
 * If the STA is in client mode, ClientMode is implemented using {@link ClientModeImpl}.
 */
public interface ClientMode {
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(flag = true, prefix = {"POWER_SAVE_CLIENT_"},
            value = {
                    POWER_SAVE_CLIENT_DHCP,
                    POWER_SAVE_CLIENT_WIFI_LOCK})
    @interface PowerSaveClientType {}
    int POWER_SAVE_CLIENT_DHCP = 0x1;
    int POWER_SAVE_CLIENT_WIFI_LOCK = 0x2;

    void dump(FileDescriptor fd, PrintWriter pw, String[] args);

    void enableVerboseLogging(boolean verbose);

    void connectNetwork(NetworkUpdateResult result, ActionListenerWrapper wrapper, int callingUid,
            @NonNull String packageName, @Nullable String attributionTag);

    void saveNetwork(NetworkUpdateResult result, ActionListenerWrapper wrapper, int callingUid,
            @NonNull String packageName);

    void disconnect();

    void reconnect(WorkSource ws);

    void reassociate();

    void startConnectToNetwork(int networkId, int uid, String bssid);

    void startRoamToNetwork(int networkId, String bssid);

    /** When the device mobility changes, update the RSSI polling interval accordingly */
    void onDeviceMobilityStateUpdated(@DeviceMobilityState int newState);

    /**
     * Set the fixed link layer stats polling interval when it is overridden, or set the interval
     * to be handled automatically by the framework
     * @param newIntervalMs new link layer stats polling interval in milliseconds. Use value 0
     *                      for automatic handling
     */
    void setLinkLayerStatsPollingInterval(int newIntervalMs);

    /**
     * See {@link android.net.wifi.WifiManager#setWifiConnectedNetworkScorer(Executor,
     * WifiManager.WifiConnectedNetworkScorer)}
     */
    boolean setWifiConnectedNetworkScorer(IBinder binder, IWifiConnectedNetworkScorer scorer,
            int callerUid);

    void clearWifiConnectedNetworkScorer();

    /**
     * Notify the connected network scorer of the user accepting a network switch.
     */
    void onNetworkSwitchAccepted(int targetNetworkId, String targetBssid);

    /**
     * Notify the connected network scorer of the user rejecting a network switch.
     */
    void onNetworkSwitchRejected(int targetNetworkId, String targetBssid);

    void resetSimAuthNetworks(@ClientModeImpl.ResetSimReason int resetReason);

    /**
     * Notification that the Bluetooth connection state changed. The latest connection state can be
     * fetched from {@link WifiGlobals#isBluetoothConnected()}.
     */
    void onBluetoothConnectionStateChanged();

    /**
     * Get current Wifi connection information
     * @return Wifi info
     */
    WifiInfo getConnectionInfo();

    boolean syncQueryPasspointIcon(long bssid, String fileName);

    /**
     * Get the current Wifi network information
     * @return network
     */
    Network getCurrentNetwork();

    DhcpResultsParcelable syncGetDhcpResultsParcelable();

    /** Get the supported feature set synchronously */
    long getSupportedFeatures();

    boolean syncStartSubscriptionProvisioning(int callingUid, OsuProvider provider,
            IProvisioningCallback callback);

    boolean isWifiStandardSupported(@WifiAnnotations.WifiStandard int standard);

    /** Enable TDLS session with remote MAC address */
    boolean enableTdls(String remoteMacAddress, boolean enable);

    /** Enable TDLS session with remote IP address */
    boolean enableTdlsWithRemoteIpAddress(String remoteIpAddress, boolean enable);

    /** Check if a TDLS session can be established */
    boolean isTdlsOperationCurrentlyAvailable();

    /** The maximum number of TDLS sessions supported by the device */
    int getMaxSupportedConcurrentTdlsSessions();

    /** The number of Peer mac addresses configured in the device for establishing a TDLS session */
    int getNumberOfEnabledTdlsSessions();

    void dumpIpClient(FileDescriptor fd, PrintWriter pw, String[] args);

    void dumpWifiScoreReport(FileDescriptor fd, PrintWriter pw, String[] args);

    String getFactoryMacAddress();

    /**
     * Returns WifiConfiguration object corresponding to the currently connected network, null if
     * not connected.
     */
    @Nullable
    WifiConfiguration getConnectedWifiConfiguration();

    /**
     * Returns WifiConfiguration object corresponding to the currently connecting network, null if
     * not connecting.
     */
    @Nullable WifiConfiguration getConnectingWifiConfiguration();

    /**
     * Returns bssid corresponding to the currently connected network, null if not connected.
     */
    @Nullable String getConnectedBssid();

    /**
     * Returns bssid corresponding to the currently connecting network, null if not connecting.
     */
    @Nullable String getConnectingBssid();

    WifiLinkLayerStats getWifiLinkLayerStats();

    boolean setPowerSave(@PowerSaveClientType int client, boolean ps);
    boolean enablePowerSave();

    boolean setLowLatencyMode(boolean enabled);

    WifiMulticastLockManager.FilterController getMcastLockManagerFilterController();

    boolean isConnected();

    boolean isConnecting();

    boolean isRoaming();

    boolean isDisconnected();

    boolean isSupplicantTransientState();

    void onCellularConnectivityChanged(@WifiDataStall.CellularDataStatusCode int status);

    /** returns whether the current network is labeled as local-only due to ip provision timeout */
    boolean isIpProvisioningTimedOut();

    /** Result callback for {@link #probeLink(LinkProbeCallback, int)} */
    interface LinkProbeCallback extends WifiNl80211Manager.SendMgmtFrameCallback {

        @Retention(RetentionPolicy.SOURCE)
        @IntDef(prefix = {"LINK_PROBE_ERROR_"},
                value = {LINK_PROBE_ERROR_NOT_CONNECTED})
        @interface LinkProbeFailure {}

        /** Attempted to send a link probe when not connected to Wifi. */
        // Note: this is a restriction in the Wifi framework since link probing is defined to be
        // targeted at the currently connected AP. Driver/firmware has no problems with sending
        // management frames to arbitrary APs whether connected or disconnected.
        int LINK_PROBE_ERROR_NOT_CONNECTED = 1000;

        /**
         * Called when the link probe failed.
         * @param reason The error code for the failure. One of
         * {@link WifiNl80211Manager.SendMgmtFrameError} or {@link LinkProbeFailure}.
         */
        void onFailure(int reason);

        static String failureReasonToString(int reason) {
            switch (reason) {
                case WifiNl80211Manager.SEND_MGMT_FRAME_ERROR_UNKNOWN:
                    return "SEND_MGMT_FRAME_ERROR_UNKNOWN";
                case WifiNl80211Manager.SEND_MGMT_FRAME_ERROR_MCS_UNSUPPORTED:
                    return "SEND_MGMT_FRAME_ERROR_MCS_UNSUPPORTED";
                case WifiNl80211Manager.SEND_MGMT_FRAME_ERROR_NO_ACK:
                    return "SEND_MGMT_FRAME_ERROR_NO_ACK";
                case WifiNl80211Manager.SEND_MGMT_FRAME_ERROR_TIMEOUT:
                    return "SEND_MGMT_FRAME_ERROR_TIMEOUT";
                case WifiNl80211Manager.SEND_MGMT_FRAME_ERROR_ALREADY_STARTED:
                    return "SEND_MGMT_FRAME_ERROR_ALREADY_STARTED";
                case LINK_PROBE_ERROR_NOT_CONNECTED:
                    return "LINK_PROBE_ERROR_NOT_CONNECTED";
                default:
                    return "Unrecognized error";
            }
        }
    }

    /** Send a link probe */
    void probeLink(LinkProbeCallback callback, int mcs);

    /** Send a {@link Message} to ClientModeImpl's StateMachine. */
    void sendMessageToClientModeImpl(Message msg);

    /** Unique ID for this ClientMode instance, used for debugging. */
    long getId();

    /**
     * Set MBO cellular data status
     * @param available cellular data status, true means cellular data available, false otherwise.
     */
    void setMboCellularDataStatus(boolean available);

    /**
     * Query the firmware roaming capabilities.
     * @return Roaming Capabilities on success, null on failure.
     */
    @Nullable
    WifiNative.RoamingCapabilities getRoamingCapabilities();

    /** Set firmware roaming configurations. */
    boolean configureRoaming(WifiNative.RoamingConfig config);

    /** Enable/Disable firmware roaming. */
    boolean enableRoaming(boolean enabled);

    /**
     * Set country code.
     *
     * @param countryCode 2 byte ASCII string. For ex: US, CA.
     * @return true if request is sent successfully, false otherwise.
     */
    boolean setCountryCode(String countryCode);

    /**
     * Fetch the most recent TX packet fates from the HAL. Fails unless HAL is started.
     * @return TxFateReport list on success, empty list on failure. Never returns null.
     */
    @NonNull
    List<TxFateReport> getTxPktFates();

    /**
     * Fetch the most recent RX packet fates from the HAL. Fails unless HAL is started.
     * @return RxFateReport list on success, empty list on failure. Never returns null.
     */
    @NonNull
    List<RxFateReport> getRxPktFates();

    /**
     * Get the Wiphy capabilities of a device for a given interface
     * If the interface is not associated with one,
     * it will be read from the device through wificond
     *
     * @return the device capabilities for this interface, or null if not available
     */
    @Nullable
    DeviceWiphyCapabilities getDeviceWiphyCapabilities();

    /**
     * Initiate ANQP query.
     *
     * @param bssid BSSID of the AP to be queried
     * @param anqpIds Set of anqp IDs.
     * @param hs20Subtypes Set of HS20 subtypes.
     * @return true on success, false otherwise.
     */
    boolean requestAnqp(String bssid, Set<Integer> anqpIds, Set<Integer> hs20Subtypes);

    /**
     * Initiate Venue URL ANQP query.
     *
     * @param bssid BSSID of the AP to be queried
     * @return true on success, false otherwise.
     */
    boolean requestVenueUrlAnqp(String bssid);

    /**
     * Request a passpoint icon file |filename| from the specified AP |bssid|.
     *
     * @param bssid BSSID of the AP
     * @param fileName name of the icon file
     * @return true if request is sent successfully, false otherwise
     */
    boolean requestIcon(String  bssid, String fileName);

    /**
     * If set to true, the NetworkAgent score for connections established on this ClientModeManager
     * will be artificially reduced so that ConnectivityService will prefer any other connection.
     */
    void setShouldReduceNetworkScore(boolean shouldReduceNetworkScore);


    /**
     * update the capabilities
     */
    void updateCapabilities();

    /**
     * Check if BSSID belongs to any of the affiliated link BSSID's.
     * @param bssid BSSID of the AP
     * @return true if BSSID matches to one of the affiliated link BSSIDs, false otherwise.
     */
    boolean isAffiliatedLinkBssid(MacAddress bssid);

    /**
     * Check if the connection is MLO (Multi-Link Operation).
     * @return true if connection is MLO, otherwise false.
     */
    boolean isMlo();

    /**
     * Notify changes in PowerManager#isDeviceIdleMode
     */
    void onIdleModeChanged(boolean isIdle);
}
