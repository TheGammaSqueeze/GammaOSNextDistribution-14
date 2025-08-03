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

package com.android.server.wifi.p2p;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.net.wifi.CoexUnsafeChannel;
import android.net.wifi.ScanResult;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDiscoveryConfig;
import android.net.wifi.p2p.WifiP2pExtListenParams;
import android.net.wifi.p2p.WifiP2pGroup;
import android.net.wifi.p2p.WifiP2pGroupList;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.nsd.WifiP2pServiceInfo;

import com.android.server.wifi.WifiNative;

import java.util.List;
import java.util.Set;

/** Abstraction of Supplicant P2P Iface HAL interface */
interface ISupplicantP2pIfaceHal {
    /**
     * Begin initializing the ISupplicantP2pIfaceHal object. Specific initialization
     * logic differs between the HIDL and AIDL implementations.
     *
     * @return true if the initialization routine was successful
     */
    boolean initialize();

    /**
     * Set the debug log level for wpa_supplicant
     *
     * @param turnOnVerbose Whether to turn on verbose logging or not.
     * @param globalShowKeys Whether show keys is true in WifiGlobals.
     * @return true if request is sent successfully, false otherwise.
     */
    boolean setLogLevel(boolean turnOnVerbose, boolean globalShowKeys);

    /**
     * Setup the P2P iface.
     *
     * @param ifaceName Name of the interface.
     * @return true on success, false otherwise.
     */
    boolean setupIface(@NonNull String ifaceName);

    /**
     * Teardown the P2P interface.
     *
     * @param ifaceName Name of the interface.
     * @return true on success, false otherwise.
     */
    boolean teardownIface(@NonNull String ifaceName);

    /**
     * Signals whether initialization started successfully.
     */
    boolean isInitializationStarted();

    /**
     * Signals whether Initialization completed successfully. Only necessary for testing, is not
     * needed to guard calls etc.
     */
    boolean isInitializationComplete();

    /**
     * Initiate a P2P service discovery with a (optional) timeout.
     *
     * @param timeout Max time to be spent is performing discovery.
     *        Set to 0 to indefinitely continue discovery until an explicit
     *        |stopFind| is sent.
     * @return boolean value indicating whether operation was successful.
     */
    boolean find(int timeout);

    /**
     * Initiate a P2P device discovery with a scan type, a (optional) frequency, and a (optional)
     * timeout.
     *
     * @param type indicates what channels to scan.
     *        Valid values are {@link WifiP2pManager#WIFI_P2P_SCAN_FULL} for doing full P2P scan,
     *        {@link WifiP2pManager#WIFI_P2P_SCAN_SOCIAL} for scanning social channels,
     *        {@link WifiP2pManager#WIFI_P2P_SCAN_SINGLE_FREQ} for scanning a specified frequency.
     * @param freq is the frequency to be scanned.
     *        The possible values are:
     *        <ul>
     *        <li> A valid frequency for {@link WifiP2pManager#WIFI_P2P_SCAN_SINGLE_FREQ}</li>
     *        <li> {@link WifiP2pManager#WIFI_P2P_SCAN_FREQ_UNSPECIFIED} for
     *          {@link WifiP2pManager#WIFI_P2P_SCAN_FULL} and
     *          {@link WifiP2pManager#WIFI_P2P_SCAN_SOCIAL}</li>
     *        </ul>
     * @param timeout Max time to be spent is performing discovery.
     *        Set to 0 to indefinitely continue discovery until an explicit
     *        |stopFind| is sent.
     * @return boolean value indicating whether operation was successful.
     */
    boolean find(@WifiP2pManager.WifiP2pScanType int type, int freq, int timeout);

    /**
     * Initiate P2P device discovery with config params.
     *
     * @param config The config parameters to initiate P2P discovery.
     * @param timeout The maximum amount of time to be spent in performing discovery.
     *        Set to 0 to indefinitely continue discovery until an explicit
     *        |stopFind| is sent.
     * @return boolean value indicating whether the operation was successful.
     */
    default boolean findWithParams(@NonNull WifiP2pDiscoveryConfig config, int timeout) {
        return false;
    }

    /**
     * Stop an ongoing P2P service discovery.
     *
     * @return boolean value indicating whether operation was successful.
     */
    boolean stopFind();

    /**
     * Flush P2P peer table and state.
     *
     * @return boolean value indicating whether operation was successful.
     */
    boolean flush();

    /**
     * This command can be used to flush all services from the
     * device.
     *
     * @return boolean value indicating whether operation was successful.
     */
    boolean serviceFlush();

    /**
     * Turn on/off power save mode for the interface.
     *
     * @param groupIfName Group interface name to use.
     * @param enable Indicate if power save is to be turned on/off.
     *
     * @return boolean value indicating whether operation was successful.
     */
    boolean setPowerSave(String groupIfName, boolean enable);

    /**
     * Set the Maximum idle time in seconds for P2P groups.
     * This value controls how long a P2P group is maintained after there
     * is no other members in the group. As a group owner, this means no
     * associated stations in the group. As a P2P client, this means no
     * group owner seen in scan results.
     *
     * @param groupIfName Group interface name to use.
     * @param timeoutInSec Timeout value in seconds.
     *
     * @return boolean value indicating whether operation was successful.
     */
    boolean setGroupIdle(String groupIfName, int timeoutInSec);

    /**
     * Set the postfix to be used for P2P SSID's.
     *
     * @param postfix String to be appended to SSID.
     *
     * @return boolean value indicating whether operation was successful.
     */
    boolean setSsidPostfix(String postfix);

    /**
     * Start P2P group formation with a discovered P2P peer. This includes
     * optional group owner negotiation, group interface setup, provisioning,
     * and establishing data connection.
     *
     * @param config Configuration to use to connect to remote device.
     * @param joinExistingGroup Indicates that this is a command to join an
     *        existing group as a client. It skips the group owner negotiation
     *        part. This must send a Provision Discovery Request message to the
     *        target group owner before associating for WPS provisioning.
     *
     * @return String containing generated pin, if selected provision method
     *        uses PIN.
     */
    String connect(WifiP2pConfig config, boolean joinExistingGroup);

    /**
     * Cancel an ongoing P2P group formation and joining-a-group related
     * operation. This operation unauthorizes the specific peer device (if any
     * had been authorized to start group formation), stops P2P find (if in
     * progress), stops pending operations for join-a-group, and removes the
     * P2P group interface (if one was used) that is in the WPS provisioning
     * step. If the WPS provisioning step has been completed, the group is not
     * terminated.
     *
     * @return boolean value indicating whether operation was successful.
     */
    boolean cancelConnect();

    /**
     * Send P2P provision discovery request to the specified peer. The
     * parameters for this command are the P2P device address of the peer and the
     * desired configuration method.
     *
     * @param config Config class describing peer setup.
     *
     * @return boolean value indicating whether operation was successful.
     */
    boolean provisionDiscovery(WifiP2pConfig config);

    /**
     * Invite a device to a persistent group.
     * If the peer device is the group owner of the persistent group, the peer
     * parameter is not needed. Otherwise it is used to specify which
     * device to invite. |goDeviceAddress| parameter may be used to override
     * the group owner device address for Invitation Request should it not be
     * known for some reason (this should not be needed in most cases).
     *
     * @param group Group object to use.
     * @param peerAddress MAC address of the device to invite.
     *
     * @return boolean value indicating whether operation was successful.
     */
    boolean invite(WifiP2pGroup group, String peerAddress);

    /**
     * Reject connection attempt from a peer (specified with a device
     * address). This is a mechanism to reject a pending group owner negotiation
     * with a peer and request to automatically block any further connection or
     * discovery of the peer.
     *
     * @param peerAddress MAC address of the device to reject.
     *
     * @return boolean value indicating whether operation was successful.
     */
    boolean reject(String peerAddress);

    /**
     * Gets the MAC address of the device.
     *
     * @return MAC address of the device.
     */
    String getDeviceAddress();

    /**
     * Gets the operational SSID of the device.
     *
     * @param address MAC address of the peer.
     *
     * @return SSID of the device.
     */
    String getSsid(String address);

    /**
     * Reinvoke a device from a persistent group.
     *
     * @param networkId Used to specify the persistent group.
     * @param peerAddress MAC address of the device to reinvoke.
     *
     * @return true, if operation was successful.
     */
    boolean reinvoke(int networkId, String peerAddress);

    /**
     * Set up a P2P group owner manually (i.e., without group owner
     * negotiation with a specific peer). This is also known as autonomous
     * group owner.
     *
     * @param networkId Used to specify the restart of a persistent group.
     * @param isPersistent Used to request a persistent group to be formed.
     *
     * @return true, if operation was successful.
     */
    boolean groupAdd(int networkId, boolean isPersistent);

    /**
     * Set up a P2P group as Group Owner or join a group with a configuration.
     *
     * @param networkName SSID of the group to be formed
     * @param passphrase passphrase of the group to be formed
     * @param isPersistent Used to request a persistent group to be formed.
     * @param freq preferred frequency or band of the group to be formed
     * @param peerAddress peerAddress Group Owner MAC address, only applied for Group Client.
     *        If the MAC is "00:00:00:00:00:00", the device will try to find a peer
     *        whose SSID matches ssid.
     * @param join join a group or create a group
     *
     * @return true, if operation was successful.
     */
    boolean groupAdd(String networkName, String passphrase,
            boolean isPersistent, int freq, String peerAddress, boolean join);

    /**
     * Terminate a P2P group. If a new virtual network interface was used for
     * the group, it must also be removed. The network interface name of the
     * group interface is used as a parameter for this command.
     *
     * @param groupName Group interface name to use.
     *
     * @return true, if operation was successful.
     */
    boolean groupRemove(String groupName);

    /**
     * Gets the capability of the group which the device is a
     * member of.
     *
     * @param peerAddress MAC address of the peer.
     *
     * @return combination of |GroupCapabilityMask| values.
     */
    int getGroupCapability(String peerAddress);

    /**
     * Configure Extended Listen Timing.
     *
     * If enabled, listen state must be entered every |intervalInMillis| for at
     * least |periodInMillis|. Both values have acceptable range of 1-65535
     * (with interval obviously having to be larger than or equal to duration).
     * If the P2P module is not idle at the time the Extended Listen Timing
     * timeout occurs, the Listen State operation must be skipped.
     *
     * @param enable Enables or disables listening.
     * @param periodInMillis Period in milliseconds.
     * @param intervalInMillis Interval in milliseconds.
     * @param extListenParams Additional parameter struct for this request.
     *
     * @return true, if operation was successful.
     */
    boolean configureExtListen(boolean enable, int periodInMillis, int intervalInMillis,
            @Nullable WifiP2pExtListenParams extListenParams);

    /**
     * Set P2P Listen channel.
     *
     * @param listenChannel Wifi channel. eg, 1, 6, 11.
     *
     * @return true, if operation was successful.
     */
    boolean setListenChannel(int listenChannel);

    /**
     * Set P2P operating channel.
     *
     * @param operatingChannel the desired operating channel.
     * @param unsafeChannels channels which p2p cannot use.
     *
     * @return true, if operation was successful.
     */
    boolean setOperatingChannel(int operatingChannel,
            @NonNull List<CoexUnsafeChannel> unsafeChannels);

    /**
     * This command can be used to add a upnp/bonjour service.
     *
     * @param servInfo List of service queries.
     *
     * @return true, if operation was successful.
     */
    boolean serviceAdd(WifiP2pServiceInfo servInfo);

    /**
     * This command can be used to remove a upnp/bonjour service.
     *
     * @param servInfo List of service queries.
     *
     * @return true, if operation was successful.
     */
    boolean serviceRemove(WifiP2pServiceInfo servInfo);

    /**
     * Schedule a P2P service discovery request. The parameters for this command
     * are the device address of the peer device (or 00:00:00:00:00:00 for
     * wildcard query that is sent to every discovered P2P peer that supports
     * service discovery) and P2P Service Query TLV(s) as hexdump.
     *
     * @param peerAddress MAC address of the device to discover.
     * @param query Hex dump of the query data.
     * @return identifier Identifier for the request. Can be used to cancel the
     *         request.
     */
    String requestServiceDiscovery(String peerAddress, String query);

    /**
     * Cancel a previous service discovery request.
     *
     * @param identifier Identifier for the request to cancel.
     * @return true, if operation was successful.
     */
    boolean cancelServiceDiscovery(String identifier);

    /**
     * Send driver command to set Miracast mode.
     *
     * @param mode Mode of Miracast.
     * @return true, if operation was successful.
     */
    boolean setMiracastMode(int mode);

    /**
     * Initiate WPS Push Button setup.
     * The PBC operation requires that a button is also pressed at the
     * AP/Registrar at about the same time (2 minute window).
     *
     * @param groupIfName Group interface name to use.
     * @param bssid BSSID of the AP. Use empty bssid to indicate wildcard.
     * @return true, if operation was successful.
     */
    boolean startWpsPbc(String groupIfName, String bssid);

    /**
     * Initiate WPS Pin Keypad setup.
     *
     * @param groupIfName Group interface name to use.
     * @param pin 8 digit pin to be used.
     * @return true, if operation was successful.
     */
    boolean startWpsPinKeypad(String groupIfName, String pin);

    /**
     * Initiate WPS Pin Display setup.
     *
     * @param groupIfName Group interface name to use.
     * @param bssid BSSID of the AP. Use empty bssid to indicate wildcard.
     * @return generated pin if operation was successful, null otherwise.
     */
    String startWpsPinDisplay(String groupIfName, String bssid);

    /**
     * Cancel any ongoing WPS operations.
     *
     * @param groupIfName Group interface name to use.
     * @return true, if operation was successful.
     */
    boolean cancelWps(String groupIfName);

    /**
     * Enable/Disable Wifi Display.
     *
     * @param enable true to enable, false to disable.
     * @return true, if operation was successful.
     */
    boolean enableWfd(boolean enable);

    /**
     * Set Wifi Display device info.
     *
     * @param info WFD device info as described in section 5.1.2 of WFD technical
     *        specification v1.0.0.
     * @return true, if operation was successful.
     */
    boolean setWfdDeviceInfo(String info);

    /**
     * Remove network with provided id.
     *
     * @param networkId Id of the network to lookup.
     * @return true, if operation was successful.
     */
    boolean removeNetwork(int networkId);

    /**
     * Get the persistent group list from wpa_supplicant's p2p mgmt interface
     *
     * @param groups WifiP2pGroupList to store persistent groups in
     * @return true, if list has been modified.
     */
    boolean loadGroups(WifiP2pGroupList groups);

    /**
     * Set WPS device name.
     *
     * @param name String to be set.
     * @return true if request is sent successfully, false otherwise.
     */
    boolean setWpsDeviceName(String name);

    /**
     * Set WPS device type.
     *
     * @param typeStr Type specified as a string. Used format: <categ>-<OUI>-<subcateg>
     * @return true if request is sent successfully, false otherwise.
     */
    boolean setWpsDeviceType(String typeStr);

    /**
     * Set WPS config methods
     *
     * @param configMethodsStr List of config methods.
     * @return true if request is sent successfully, false otherwise.
     */
    boolean setWpsConfigMethods(String configMethodsStr);

    /**
     * Get NFC handover request message.
     *
     * @return select message if created successfully, null otherwise.
     */
    String getNfcHandoverRequest();

    /**
     * Get NFC handover select message.
     *
     * @return select message if created successfully, null otherwise.
     */
    String getNfcHandoverSelect();

    /**
     * Report NFC handover select message.
     *
     * @return true if reported successfully, false otherwise.
     */
    boolean initiatorReportNfcHandover(String selectMessage);

    /**
     * Report NFC handover request message.
     *
     * @return true if reported successfully, false otherwise.
     */
    boolean responderReportNfcHandover(String requestMessage);

    /**
     * Set the client list for the provided network.
     *
     * @param networkId Id of the network.
     * @param clientListStr Space separated list of clients.
     * @return true, if operation was successful.
     */
    boolean setClientList(int networkId, String clientListStr);

    /**
     * Set the client list for the provided network.
     *
     * @param networkId Id of the network.
     * @return Space separated list of clients if successful, null otherwise.
     */
    String getClientList(int networkId);

    /**
     * Persist the current configurations to disk.
     *
     * @return true, if operation was successful.
     */
    boolean saveConfig();

    /**
     * Enable/Disable P2P MAC randomization.
     *
     * @param enable true to enable, false to disable.
     * @return true, if operation was successful.
     */
    boolean setMacRandomization(boolean enable);

    /**
     * Set Wifi Display R2 device info.
     *
     * @param info WFD R2 device info as described in section 5.1.12 of WFD technical
     *        specification v2.1.
     * @return true, if operation was successful.
     */
    boolean setWfdR2DeviceInfo(String info);

    /**
     * Remove the client with the MAC address from the group.
     *
     * @param peerAddress Mac address of the client.
     * @param isLegacyClient Indicate if client is a legacy client or not.
     * @return true if success
     */
    boolean removeClient(String peerAddress, boolean isLegacyClient);

    /**
     * Set vendor-specific information elements to wpa_supplicant.
     *
     * @param vendorElements vendor-specific information elements.
     *
     * @return boolean value indicating whether operation was successful.
     */
    boolean setVendorElements(Set<ScanResult.InformationElement> vendorElements);

    /**
     * Configure the IP addresses in supplicant for P2P GO to provide the IP address to
     * client in EAPOL handshake. Refer Wi-Fi P2P Technical Specification v1.7 - Section  4.2.8
     * IP Address Allocation in EAPOL-Key Frames (4-Way Handshake) for more details.
     * The IP addresses are IPV4 addresses and higher-order address bytes are in the
     * lower-order int bytes (e.g. 1.2.3.4 is represented as 0x04030201)
     *
     * @param ipAddressGo The P2P Group Owner IP address.
     * @param ipAddressMask The P2P Group owner subnet mask.
     * @param ipAddressStart The starting address in the IP address pool.
     * @param ipAddressEnd The ending address in the IP address pool.
     * @return boolean value indicating whether operation was successful.
     */
    boolean configureEapolIpAddressAllocationParams(int ipAddressGo, int ipAddressMask,
            int ipAddressStart, int ipAddressEnd);

    /**
     * Terminate the supplicant daemon & wait for its death.
     * Note: Aidl only since it was added from HIDL 1.1
     */
    default void terminate() {};

    /**
     * Registers a death notification for supplicant.
     * @return Returns true on success.
     *
     * Note: Aidl only.
     */
    default boolean registerDeathHandler(@NonNull WifiNative.SupplicantDeathEventHandler handler) {
        return false;
    };

    /**
     * Deregisters a death notification for supplicant.
     * @return Returns true on success.
     *
     * Note: Aidl only.
     */
    default boolean deregisterDeathHandler() {
        return false;
    };
}
