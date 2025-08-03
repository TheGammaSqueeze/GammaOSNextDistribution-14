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
import android.net.wifi.MscsParams;
import android.net.wifi.QosPolicyParams;
import android.net.wifi.SecurityParams;
import android.net.wifi.WifiConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Abstraction of Supplicant STA Iface HAL interface */
interface ISupplicantStaIfaceHal {
    /**
     * Enable/Disable verbose logging.
     */
    void enableVerboseLogging(boolean verboseEnabled, boolean halVerboseEnabled);

    /**
     * Begin initializing the ISupplicantStaIfaceHal object. Specific initialization
     * logic differs between the HIDL and AIDL implementations.
     *
     * @return true if the initialization routine was successful
     */
    boolean initialize();

    /**
     * Setup a STA interface for the specified iface name.
     *
     * @param ifaceName Name of the interface.
     * @return true on success, false otherwise.
     */
    boolean setupIface(@NonNull String ifaceName);

    /**
     * Teardown a STA interface for the specified iface name.
     *
     * @param ifaceName Name of the interface.
     * @return true on success, false otherwise.
     */
    boolean teardownIface(@NonNull String ifaceName);

    /**
     * Registers a death notification for supplicant.
     * @return Returns true on success.
     */
    boolean registerDeathHandler(@NonNull WifiNative.SupplicantDeathEventHandler handler);

    /**
     * Deregisters a death notification for supplicant.
     * @return Returns true on success.
     */
    boolean deregisterDeathHandler();

    /**
     * Signals whether initialization started successfully.
     */
    boolean isInitializationStarted();

    /**
     * Signals whether initialization completed successfully.
     */
    boolean isInitializationComplete();

    /**
     * Start the supplicant daemon.
     *
     * @return true on success, false otherwise.
     */
    boolean startDaemon();

    /**
     * Terminate the supplicant daemon & wait for its death.
     */
    void terminate();

    /**
     * Add the provided network configuration to wpa_supplicant and initiate connection to it.
     * This method does the following:
     * 1. If |config| is different to the current supplicant network, removes all supplicant
     * networks and saves |config|.
     * 2. Select the new network in wpa_supplicant.
     *
     * @param ifaceName Name of the interface.
     * @param config WifiConfiguration parameters for the provided network.
     * @return {@code true} if it succeeds, {@code false} otherwise
     */
    boolean connectToNetwork(@NonNull String ifaceName, @NonNull WifiConfiguration config);

    /**
     * Initiates roaming to the already configured network in wpa_supplicant. If the network
     * configuration provided does not match the already configured network, then this triggers
     * a new connection attempt (instead of roam).
     *
     * @param ifaceName Name of the interface.
     * @param config WifiConfiguration parameters for the provided network.
     * @return {@code true} if it succeeds, {@code false} otherwise
     */
    boolean roamToNetwork(@NonNull String ifaceName, WifiConfiguration config);

    /**
     * Clean HAL cached data for |networkId| in the framework.
     *
     * @param networkId Network id of the network to be removed from supplicant.
     */
    void removeNetworkCachedData(int networkId);

    /**
     * Clear HAL cached data if MAC address is changed.
     *
     * @param networkId Network id of the network to be checked.
     * @param curMacAddress Current MAC address
     */
    void removeNetworkCachedDataIfNeeded(int networkId, MacAddress curMacAddress);

    /**
     * Remove all networks from supplicant
     *
     * @param ifaceName Name of the interface.
     */
    boolean removeAllNetworks(@NonNull String ifaceName);

    /**
     * Disable the current network in supplicant
     *
     * @param ifaceName Name of the interface.
     */
    boolean disableCurrentNetwork(@NonNull String ifaceName);

    /**
     * Set the currently configured network's bssid.
     *
     * @param ifaceName Name of the interface.
     * @param bssidStr Bssid to set in the form of "XX:XX:XX:XX:XX:XX"
     * @return true if succeeds, false otherwise.
     */
    boolean setCurrentNetworkBssid(@NonNull String ifaceName, String bssidStr);

    /**
     * Get the currently configured network's WPS NFC token.
     *
     * @param ifaceName Name of the interface.
     * @return Hex string corresponding to the WPS NFC token.
     */
    String getCurrentNetworkWpsNfcConfigurationToken(@NonNull String ifaceName);

    /**
     * Get the eap anonymous identity for the currently configured network.
     *
     * @param ifaceName Name of the interface.
     * @return anonymous identity string if succeeds, null otherwise.
     */
    String getCurrentNetworkEapAnonymousIdentity(@NonNull String ifaceName);

    /**
     * Send the eap identity response for the currently configured network.
     *
     * @param ifaceName Name of the interface.
     * @param identity Identity used for EAP-Identity
     * @param encryptedIdentity Encrypted identity used for EAP-AKA/EAP-SIM
     * @return true if succeeds, false otherwise.
     */
    boolean sendCurrentNetworkEapIdentityResponse(
            @NonNull String ifaceName, @NonNull String identity, String encryptedIdentity);

    /**
     * Send the eap sim gsm auth response for the currently configured network.
     *
     * @param ifaceName Name of the interface.
     * @param paramsStr String to send.
     * @return true if succeeds, false otherwise.
     */
    boolean sendCurrentNetworkEapSimGsmAuthResponse(
            @NonNull String ifaceName, String paramsStr);

    /**
     * Send the eap sim gsm auth failure for the currently configured network.
     *
     * @param ifaceName Name of the interface.
     * @return true if succeeds, false otherwise.
     */
    boolean sendCurrentNetworkEapSimGsmAuthFailure(@NonNull String ifaceName);

    /**
     * Send the eap sim umts auth response for the currently configured network.
     *
     * @param ifaceName Name of the interface.
     * @param paramsStr String to send.
     * @return true if succeeds, false otherwise.
     */
    boolean sendCurrentNetworkEapSimUmtsAuthResponse(
            @NonNull String ifaceName, String paramsStr);

    /**
     * Send the eap sim umts auts response for the currently configured network.
     *
     * @param ifaceName Name of the interface.
     * @param paramsStr String to send.
     * @return true if succeeds, false otherwise.
     */
    boolean sendCurrentNetworkEapSimUmtsAutsResponse(
            @NonNull String ifaceName, String paramsStr);

    /**
     * Send the eap sim umts auth failure for the currently configured network.
     *
     * @param ifaceName Name of the interface.
     * @return true if succeeds, false otherwise.
     */
    boolean sendCurrentNetworkEapSimUmtsAuthFailure(@NonNull String ifaceName);

    /**
     * Set WPS device name.
     *
     * @param ifaceName Name of the interface.
     * @param deviceName String to be set.
     * @return true if request is sent successfully, false otherwise.
     */
    boolean setWpsDeviceName(@NonNull String ifaceName, String deviceName);

    /**
     * Set WPS device type.
     *
     * @param ifaceName Name of the interface.
     * @param typeStr Type specified as a string. Used format: <categ>-<OUI>-<subcateg>
     * @return true if request is sent successfully, false otherwise.
     */
    boolean setWpsDeviceType(@NonNull String ifaceName, String typeStr);

    /**
     * Set WPS manufacturer.
     *
     * @param ifaceName Name of the interface.
     * @param manufacturer String to be set.
     * @return true if request is sent successfully, false otherwise.
     */
    boolean setWpsManufacturer(@NonNull String ifaceName, String manufacturer);

    /**
     * Set WPS model name.
     *
     * @param ifaceName Name of the interface.
     * @param modelName String to be set.
     * @return true if request is sent successfully, false otherwise.
     */
    boolean setWpsModelName(@NonNull String ifaceName, String modelName);

    /**
     * Set WPS model number.
     *
     * @param ifaceName Name of the interface.
     * @param modelNumber String to be set.
     * @return true if request is sent successfully, false otherwise.
     */
    boolean setWpsModelNumber(@NonNull String ifaceName, String modelNumber);

    /**
     * Set WPS serial number.
     *
     * @param ifaceName Name of the interface.
     * @param serialNumber String to be set.
     * @return true if request is sent successfully, false otherwise.
     */
    boolean setWpsSerialNumber(@NonNull String ifaceName, String serialNumber);

    /**
     * Set WPS config methods
     *
     * @param ifaceName Name of the interface.
     * @param configMethodsStr List of config methods.
     * @return true if request is sent successfully, false otherwise.
     */
    boolean setWpsConfigMethods(@NonNull String ifaceName, String configMethodsStr);

    /**
     * Trigger a reassociation even if the iface is currently connected.
     *
     * @param ifaceName Name of the interface.
     * @return true if request is sent successfully, false otherwise.
     */
    boolean reassociate(@NonNull String ifaceName);

    /**
     * Trigger a reconnection if the iface is disconnected.
     *
     * @param ifaceName Name of the interface.
     * @return true if request is sent successfully, false otherwise.
     */
    boolean reconnect(@NonNull String ifaceName);

    /**
     * Trigger a disconnection from the currently connected network.
     *
     * @param ifaceName Name of the interface.
     * @return true if request is sent successfully, false otherwise.
     */
    boolean disconnect(@NonNull String ifaceName);

    /**
     * Enable or disable power save mode.
     *
     * @param ifaceName Name of the interface.
     * @param enable true to enable, false to disable.
     * @return true if request is sent successfully, false otherwise.
     */
    boolean setPowerSave(@NonNull String ifaceName, boolean enable);

    /**
     * Initiate TDLS discover with the specified AP.
     *
     * @param ifaceName Name of the interface.
     * @param macAddress MAC Address of the AP.
     * @return true if request is sent successfully, false otherwise.
     */
    boolean initiateTdlsDiscover(@NonNull String ifaceName, String macAddress);

    /**
     * Initiate TDLS setup with the specified AP.
     *
     * @param ifaceName Name of the interface.
     * @param macAddress MAC Address of the AP.
     * @return true if request is sent successfully, false otherwise.
     */
    boolean initiateTdlsSetup(@NonNull String ifaceName, String macAddress);

    /**
     * Initiate TDLS teardown with the specified AP.
     * @param ifaceName Name of the interface.
     * @param macAddress MAC Address of the AP.
     * @return true if request is sent successfully, false otherwise.
     */
    boolean initiateTdlsTeardown(@NonNull String ifaceName, String macAddress);

    /**
     * Request the specified ANQP elements |elements| from the specified AP |bssid|.
     *
     * @param ifaceName Name of the interface.
     * @param bssid BSSID of the AP
     * @param infoElements ANQP elements to be queried. Refer to ISupplicantStaIface.AnqpInfoId.
     * @param hs20SubTypes HS subtypes to be queried. Refer to ISupplicantStaIface.Hs20AnqpSubTypes.
     * @return true if request is sent successfully, false otherwise.
     */
    boolean initiateAnqpQuery(@NonNull String ifaceName, String bssid,
            ArrayList<Short> infoElements,
            ArrayList<Integer> hs20SubTypes);

    /**
     * Request Venue URL ANQP element from the specified AP |bssid|.
     *
     * @param ifaceName Name of the interface.
     * @param bssid BSSID of the AP
     * @return true if request is sent successfully, false otherwise.
     */
    boolean initiateVenueUrlAnqpQuery(@NonNull String ifaceName, String bssid);

    /**
     * Request the specified ANQP ICON from the specified AP |bssid|.
     *
     * @param ifaceName Name of the interface.
     * @param bssid BSSID of the AP
     * @param fileName Name of the file to request.
     * @return true if request is sent successfully, false otherwise.
     */
    boolean initiateHs20IconQuery(@NonNull String ifaceName, String bssid, String fileName);

    /**
     * Gets MAC address from the supplicant
     *
     * @param ifaceName Name of the interface.
     * @return string containing the MAC address, or null on a failed call
     */
    String getMacAddress(@NonNull String ifaceName);

    /**
     * Start using the added RX filters.
     *
     * @param ifaceName Name of the interface.
     * @return true if request is sent successfully, false otherwise.
     */
    boolean startRxFilter(@NonNull String ifaceName);

    /**
     * Stop using the added RX filters.
     *
     * @param ifaceName Name of the interface.
     * @return true if request is sent successfully, false otherwise.
     */
    boolean stopRxFilter(@NonNull String ifaceName);

    /**
     * Add an RX filter.
     *
     * @param ifaceName Name of the interface.
     * @param type one of {@link WifiNative#RX_FILTER_TYPE_V4_MULTICAST}
     *        {@link WifiNative#RX_FILTER_TYPE_V6_MULTICAST} values.
     * @return true if request is sent successfully, false otherwise.
     */
    boolean addRxFilter(@NonNull String ifaceName, int type);

    /**
     * Remove an RX filter.
     *
     * @param ifaceName Name of the interface.
     * @param type one of {@link WifiNative#RX_FILTER_TYPE_V4_MULTICAST}
     *        {@link WifiNative#RX_FILTER_TYPE_V6_MULTICAST} values.
     * @return true if request is sent successfully, false otherwise.
     */
    boolean removeRxFilter(@NonNull String ifaceName, int type);

    /**
     * Set Bt coexistence mode.
     *
     * @param ifaceName Name of the interface.
     * @param mode one of the above {@link WifiNative#BLUETOOTH_COEXISTENCE_MODE_DISABLED},
     *             {@link WifiNative#BLUETOOTH_COEXISTENCE_MODE_ENABLED} or
     *             {@link WifiNative#BLUETOOTH_COEXISTENCE_MODE_SENSE}.
     * @return true if request is sent successfully, false otherwise.
     */
    boolean setBtCoexistenceMode(@NonNull String ifaceName, int mode);

    /** Enable or disable BT coexistence mode.
     *
     * @param ifaceName Name of the interface.
     * @param enable true to enable, false to disable.
     * @return true if request is sent successfully, false otherwise.
     */
    boolean setBtCoexistenceScanModeEnabled(@NonNull String ifaceName, boolean enable);

    /**
     * Enable or disable suspend mode optimizations.
     *
     * @param ifaceName Name of the interface.
     * @param enable true to enable, false otherwise.
     * @return true if request is sent successfully, false otherwise.
     */
    boolean setSuspendModeEnabled(@NonNull String ifaceName, boolean enable);

    /**
     * Set country code.
     *
     * @param ifaceName Name of the interface.
     * @param codeStr 2 byte ASCII string. For ex: US, CA.
     * @return true if request is sent successfully, false otherwise.
     */
    boolean setCountryCode(@NonNull String ifaceName, String codeStr);

    /**
     * Flush all previously configured HLPs.
     *
     * @param ifaceName Name of the interface.
     * @return true if request is sent successfully, false otherwise.
     */
    boolean flushAllHlp(@NonNull String ifaceName);

    /**
     * Set FILS HLP packet.
     *
     * @param ifaceName Name of the interface.
     * @param dst Destination MAC address.
     * @param hlpPacket Hlp Packet data in hex.
     * @return true if request is sent successfully, false otherwise.
     */
    boolean addHlpReq(@NonNull String ifaceName, byte [] dst, byte [] hlpPacket);

    /**
     * Start WPS pin registrar operation with the specified peer and pin.
     *
     * @param ifaceName Name of the interface.
     * @param bssidStr BSSID of the peer.
     * @param pin Pin to be used.
     * @return true if request is sent successfully, false otherwise.
     */
    boolean startWpsRegistrar(@NonNull String ifaceName, String bssidStr, String pin);

    /**
     * Start WPS pin display operation with the specified peer.
     *
     * @param ifaceName Name of the interface.
     * @param bssidStr BSSID of the peer. Use empty bssid to indicate wildcard.
     * @return true if request is sent successfully, false otherwise.
     */
    boolean startWpsPbc(@NonNull String ifaceName, String bssidStr);

    /**
     * Start WPS pin keypad operation with the specified pin.
     *
     * @param ifaceName Name of the interface.
     * @param pin Pin to be used.
     * @return true if request is sent successfully, false otherwise.
     */
    boolean startWpsPinKeypad(@NonNull String ifaceName, String pin);

    /**
     * Start WPS pin display operation with the specified peer.
     *
     * @param ifaceName Name of the interface.
     * @param bssidStr BSSID of the peer. Use empty bssid to indicate wildcard.
     * @return new pin generated on success, null otherwise.
     */
    String startWpsPinDisplay(@NonNull String ifaceName, String bssidStr);

    /**
     * Cancels any ongoing WPS requests.
     *
     * @param ifaceName Name of the interface.
     * @return true if request is sent successfully, false otherwise.
     */
    boolean cancelWps(@NonNull String ifaceName);

    /**
     * Sets whether to use external sim for SIM/USIM processing.
     *
     * @param ifaceName Name of the interface.
     * @param useExternalSim true to enable, false otherwise.
     * @return true if request is sent successfully, false otherwise.
     */
    boolean setExternalSim(@NonNull String ifaceName, boolean useExternalSim);

    /**
     * Enable/Disable auto reconnect to networks.
     * Use this to prevent wpa_supplicant from trying to connect to networks
     * on its own.
     *
     * @param enable true to enable, false to disable.
     * @return true if no exceptions occurred, false otherwise
     */
    boolean enableAutoReconnect(@NonNull String ifaceName, boolean enable);

    /**
     * Set the debug log level for wpa_supplicant
     *
     * @param turnOnVerbose Whether to turn on verbose logging or not.
     * @return true if request is sent successfully, false otherwise.
     */
    boolean setLogLevel(boolean turnOnVerbose);

    /**
     * Set concurrency priority between P2P & STA operations.
     *
     * @param isStaHigherPriority Set to true to prefer STA over P2P during concurrency operations,
     *                            false otherwise.
     * @return true if request is sent successfully, false otherwise.
     */
    boolean setConcurrencyPriority(boolean isStaHigherPriority);

    /**
     * Returns a bitmask of advanced capabilities: WPA3 SAE/SUITE B and OWE
     * Bitmask used is:
     * - WIFI_FEATURE_WPA3_SAE
     * - WIFI_FEATURE_WPA3_SUITE_B
     * - WIFI_FEATURE_OWE
     *
     *  On error, or if these features are not supported, 0 is returned.
     */
    long getAdvancedCapabilities(@NonNull String ifaceName);

    /**
     * Get the driver supported features through supplicant.
     *
     * @param ifaceName Name of the interface.
     * @return bitmask defined by WifiManager.WIFI_FEATURE_*.
     */
    long getWpaDriverFeatureSet(@NonNull String ifaceName);

    /**
     * Returns connection capabilities of the current network
     *
     * @param ifaceName Name of the interface.
     * @return connection capabilities of the current network
     */
    WifiNative.ConnectionCapabilities getConnectionCapabilities(@NonNull String ifaceName);

    /**
     * Returns signal poll results for all Wi-Fi links of the interface.
     *
     * @param ifaceName Name of the interface.
     * @return Signal poll results.
     */
    WifiSignalPollResults getSignalPollResults(@NonNull String ifaceName);

    /**
     * Returns connection MLO links info
     *
     * @param ifaceName Name of the interface.
     * @return connection MLO links info
     */
    WifiNative.ConnectionMloLinksInfo getConnectionMloLinksInfo(@NonNull String ifaceName);

    /**
     * Adds a DPP peer URI to the URI list.
     *
     * Returns an ID to be used later to refer to this URI (>0).
     * On error, or if these features are not supported, -1 is returned.
     */
    int addDppPeerUri(@NonNull String ifaceName, @NonNull String uri);

    /**
     * Removes a DPP URI to the URI list given an ID.
     *
     * Returns true when operation is successful
     * On error, or if these features are not supported, false is returned.
     */
    boolean removeDppUri(@NonNull String ifaceName, int bootstrapId);

    /**
     * Stops/aborts DPP Initiator request
     *
     * Returns true when operation is successful
     * On error, or if these features are not supported, false is returned.
     */
    boolean stopDppInitiator(@NonNull String ifaceName);

    /**
     * Starts DPP Configurator-Initiator request
     *
     * Returns true when operation is successful
     * On error, or if these features are not supported, false is returned.
     */
    boolean startDppConfiguratorInitiator(@NonNull String ifaceName, int peerBootstrapId,
            int ownBootstrapId, @NonNull String ssid, String password, String psk,
            int netRole, int securityAkm, byte[] privEcKey);

    /**
     * Starts DPP Enrollee-Initiator request
     *
     * Returns true when operation is successful
     * On error, or if these features are not supported, false is returned.
     */
    boolean startDppEnrolleeInitiator(@NonNull String ifaceName, int peerBootstrapId,
            int ownBootstrapId);

    /**
     * Generate a DPP QR code based boot strap info
     *
     * Returns DppResponderBootstrapInfo;
     */
    WifiNative.DppBootstrapQrCodeInfo generateDppBootstrapInfoForResponder(
            @NonNull String ifaceName, String macAddress, @NonNull String deviceInfo,
            int dppCurve);

    /**
     * Starts DPP Enrollee-Responder request
     *
     * Returns true when operation is successful
     * On error, or if these features are not supported, false is returned.
     */
    boolean startDppEnrolleeResponder(@NonNull String ifaceName, int listenChannel);

    /**
     * Stops/aborts DPP Responder request.
     *
     * Returns true when operation is successful
     * On error, or if these features are not supported, false is returned.
     */
    boolean stopDppResponder(@NonNull String ifaceName, int ownBootstrapId);

    /**
     * Register callbacks for DPP events.
     *
     * @param dppCallback DPP callback object.
     */
    void registerDppCallback(WifiNative.DppEventCallback dppCallback);

    /**
     * Set MBO cellular data availability.
     *
     * @param ifaceName Name of the interface.
     * @param available true means cellular data available, false otherwise.
     * Returns true when operation is successful
     */
    boolean setMboCellularDataStatus(@NonNull String ifaceName, boolean available);

    /**
     * Check if we've roamed to a linked network and make the linked network the current network
     * if we have.
     *
     * @param ifaceName Name of the interface.
     * @param newNetworkId Network id of the new network we've roamed to. If fromFramework is
     *                     {@code true}, this will be a framework network id. Otherwise, this will
     *                     be a remote network id.
     * @param fromFramework {@code true} if the network id is a framework network id, {@code false}
                            if the network id is a remote network id.
     * @return true if we've roamed to a linked network, false if not.
     */
    boolean updateOnLinkedNetworkRoaming(@NonNull String ifaceName,
            int newNetworkId, boolean fromFramework);

    /**
     * Updates the linked networks for the current network and sends them to the supplicant.
     *
     * @param ifaceName Name of the interface.
     * @param networkId Network id of the network to link the configurations to.
     * @param linkedConfigurations Map of config profile key to config for linking.
     * @return true if networks were successfully linked, false otherwise.
     */
    boolean updateLinkedNetworks(@NonNull String ifaceName, int networkId,
            Map<String, WifiConfiguration> linkedConfigurations);

    /**
     * Gets the security params of the current network associated with this interface
     *
     * @param ifaceName Name of the interface
     * @return Security params of the current network associated with the interface
     */
    SecurityParams getCurrentNetworkSecurityParams(@NonNull String ifaceName);

    /**
     * Set whether the network-centric QoS policy feature is enabled or not for this interface.
     *
     * @param ifaceName name of the interface.
     * @param isEnabled true if the feature is enabled, false otherwise.
     * @return true if operation is successful, false otherwise.
     */
    default boolean setNetworkCentricQosPolicyFeatureEnabled(
            @NonNull String ifaceName, boolean isEnabled) {
        return false;
    }

    /**
     * Sends a QoS policy response.
     *
     * @param ifaceName Name of the interface.
     * @param qosPolicyRequestId Dialog token to identify the request.
     * @param morePolicies Flag to indicate more QoS policies can be accommodated.
     * @param qosPolicyStatusList List of framework QosPolicyStatus objects.
     * @return true if response is sent successfully, false otherwise.
     */
    default boolean sendQosPolicyResponse(String ifaceName, int qosPolicyRequestId,
            boolean morePolicies,
            @NonNull List<SupplicantStaIfaceHal.QosPolicyStatus> qosPolicyStatusList) {
        return false;
    }

    /**
     * Indicates the removal of all active QoS policies configured by the AP.
     *
     * @param ifaceName Name of the interface.
     */
    default boolean removeAllQosPolicies(String ifaceName) {
        return false;
    }

    /**
     * Send a set of QoS SCS policy add requests to the AP.
     *
     * Immediate response will indicate which policies were sent to the AP, and which were
     * rejected immediately by the supplicant. If any requests were sent to the AP, the AP's
     * response will arrive later in the onQosPolicyResponseForScs callback.
     *
     * @param ifaceName Name of the interface.
     * @param policies List of policies that the caller is requesting to add.
     * @return List of responses for each policy in the request, or null if an error occurred.
     *         Status code will be one of
     *         {@link SupplicantStaIfaceHal.QosPolicyScsRequestStatusCode}.
     */
    default List<SupplicantStaIfaceHal.QosPolicyStatus> addQosPolicyRequestForScs(
            @NonNull String ifaceName, @NonNull List<QosPolicyParams> policies) {
        return null;
    }

    /**
     * Request the removal of specific QoS policies for SCS.
     *
     * Immediate response will indicate which policies were sent to the AP, and which were
     * rejected immediately by the supplicant. If any requests were sent to the AP, the AP's
     * response will arrive later in the onQosPolicyResponseForScs callback.
     *
     * @param ifaceName Name of the interface.
     * @param policyIds List of policy IDs for policies that should be removed.
     * @return List of responses for each policy in the request, or null if an error occurred.
     *         Status code will be one of
     *         {@link SupplicantStaIfaceHal.QosPolicyScsRequestStatusCode}.
     */
    default List<SupplicantStaIfaceHal.QosPolicyStatus> removeQosPolicyForScs(
            @NonNull String ifaceName, @NonNull List<Byte> policyIds) {
        return null;
    }

    /**
     * Register a callback to receive notifications for QoS SCS transactions.
     * Callback should only be registered once.
     *
     * @param callback {@link SupplicantStaIfaceHal.QosScsResponseCallback} to register.
     */
    default void registerQosScsResponseCallback(
            SupplicantStaIfaceHal.QosScsResponseCallback callback) {}

    /**
     * Generate DPP credential for network access
     *
     * @param ifaceName Name of the interface.
     * @param ssid ssid of the network
     * @param privEcKey Private EC Key for DPP Configurator
     * Returns true when operation is successful. On error, false is returned.
     */
    default boolean generateSelfDppConfiguration(@NonNull String ifaceName, @NonNull String ssid,
            byte[] privEcKey) {
        return false;
    }

    /**
     * Set the currently configured network's anonymous identity.
     *
     * @param ifaceName Name of the interface.
     * @param anonymousIdentity the anonymouns identity.
     * @param updateToNativeService write the data to the native service.
     * @return true if succeeds, false otherwise.
     */
    default boolean setEapAnonymousIdentity(@NonNull String ifaceName, String anonymousIdentity,
            boolean updateToNativeService) {
        return false;
    }

    /**
     * Enable Mirrored Stream Classification Service (MSCS) and configure using
     * the provided configuration values.
     *
     * @param mscsParams {@link MscsParams} object containing the configuration parameters.
     * @param ifaceName Name of the interface.
     */
    default void enableMscs(@NonNull MscsParams mscsParams, String ifaceName) {}

    /**
     * Disable Mirrored Stream Classification Service (MSCS).
     *
     * @param ifaceName Name of the interface.
     */
    default void disableMscs(String ifaceName) {}
}
