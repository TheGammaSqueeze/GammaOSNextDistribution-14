/*
 * Copyright (C) 2016 The Android Open Source Project
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
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;
import com.android.server.wifi.WifiGlobals;
import com.android.server.wifi.WifiInjector;
import com.android.server.wifi.WifiNative;

import java.util.List;
import java.util.Set;

public class SupplicantP2pIfaceHal {
    private static final String TAG = "SupplicantP2pIfaceHal";
    private final Object mLock = new Object();
    private static boolean sVerboseLoggingEnabled = true;
    private static boolean sHalVerboseLoggingEnabled = true;
    private final WifiP2pMonitor mMonitor;
    private final WifiGlobals mWifiGlobals;
    private final WifiInjector mWifiInjector;

    // HAL interface object - might be implemented by HIDL or AIDL
    private ISupplicantP2pIfaceHal mP2pIfaceHal;

    public SupplicantP2pIfaceHal(WifiP2pMonitor monitor, WifiGlobals wifiGlobals,
            WifiInjector wifiInjector) {
        mMonitor = monitor;
        mWifiGlobals = wifiGlobals;
        mWifiInjector = wifiInjector;
        mP2pIfaceHal = createP2pIfaceHalMockable();
        if (mP2pIfaceHal == null) {
            Log.wtf(TAG, "Failed to get internal ISupplicantP2pIfaceHal instance.");
        }
    }

    /**
     * Enable verbose logging for all sub modules.
     */
    public static void enableVerboseLogging(boolean verboseEnabled, boolean halVerboseEnabled) {
        sVerboseLoggingEnabled = verboseEnabled;
        sHalVerboseLoggingEnabled = halVerboseEnabled;
        SupplicantP2pIfaceHalHidlImpl.enableVerboseLogging(verboseEnabled, halVerboseEnabled);
        SupplicantP2pIfaceHalAidlImpl.enableVerboseLogging(verboseEnabled, halVerboseEnabled);
    }

    /**
     * Set the debug log level for wpa_supplicant
     *
     * @param turnOnVerbose Whether to turn on verbose logging or not.
     * @return true if request is sent successfully, false otherwise.
     */
    public boolean setLogLevel(boolean turnOnVerbose) {
        synchronized (mLock) {
            String methodStr = "setLogLevel";
            if (mP2pIfaceHal == null) {
                return handleNullHal(methodStr);
            }
            return mP2pIfaceHal.setLogLevel(turnOnVerbose,
                    mWifiGlobals.getShowKeyVerboseLoggingModeEnabled());
        }
    }

    /**
     * Initialize the P2P Iface HAL. Creates the internal ISupplicantP2pIfaceHal
     * object and calls its initialize method.
     *
     * @return true if the initialization succeeded
     */
    public boolean initialize() {
        synchronized (mLock) {
            if (sVerboseLoggingEnabled) {
                Log.i(TAG, "Initializing SupplicantP2pIfaceHal.");
            }
            if (mP2pIfaceHal == null) {
                Log.wtf(TAG, "Internal ISupplicantP2pIfaceHal instance does not exist.");
                return false;
            }
            if (!mP2pIfaceHal.initialize()) {
                Log.e(TAG, "Failed to init ISupplicantP2pIfaceHal, stopping startup.");
                return false;
            }
            setLogLevel(sHalVerboseLoggingEnabled);
            return true;
        }
    }

    /**
     * Wrapper function to create the ISupplicantP2pIfaceHal object.
     * Created to be mockable in unit tests.
     */
    @VisibleForTesting
    protected ISupplicantP2pIfaceHal createP2pIfaceHalMockable() {
        synchronized (mLock) {
            // Prefer AIDL implementation if service is declared.
            if (SupplicantP2pIfaceHalAidlImpl.serviceDeclared()) {
                Log.i(TAG, "Initializing SupplicantP2pIfaceHal using AIDL implementation.");
                return new SupplicantP2pIfaceHalAidlImpl(mMonitor, mWifiInjector);

            } else if (SupplicantP2pIfaceHalHidlImpl.serviceDeclared()) {
                Log.i(TAG, "Initializing SupplicantP2pIfaceHal using HIDL implementation.");
                return new SupplicantP2pIfaceHalHidlImpl(mMonitor);
            }
            Log.e(TAG, "No HIDL or AIDL service available for SupplicantP2pIfaceHal.");
            return null;
        }
    }

    /**
     * Setup the P2P iface.
     *
     * @param ifaceName Name of the interface.
     * @return true on success, false otherwise.
     */
    public boolean setupIface(@NonNull String ifaceName) {
        synchronized (mLock) {
            String methodStr = "setupIface";
            if (mP2pIfaceHal == null) {
                return handleNullHal(methodStr);
            }
            return mP2pIfaceHal.setupIface(ifaceName);
        }
    }

    /**
     * Teardown the P2P interface.
     *
     * @param ifaceName Name of the interface.
     * @return true on success, false otherwise.
     */
    public boolean teardownIface(@NonNull String ifaceName) {
        synchronized (mLock) {
            String methodStr = "teardownIface";
            if (mP2pIfaceHal == null) {
                return handleNullHal(methodStr);
            }
            return mP2pIfaceHal.teardownIface(ifaceName);
        }
    }

    /**
     * Signals whether initialization started successfully.
     */
    public boolean isInitializationStarted() {
        synchronized (mLock) {
            String methodStr = "isInitializationStarted";
            if (mP2pIfaceHal == null) {
                return handleNullHal(methodStr);
            }
            return mP2pIfaceHal.isInitializationStarted();
        }
    }

    /**
     * Signals whether Initialization completed successfully. Only necessary for testing, is not
     * needed to guard calls etc.
     */
    public boolean isInitializationComplete() {
        synchronized (mLock) {
            String methodStr = "isInitializationComplete";
            if (mP2pIfaceHal == null) {
                return handleNullHal(methodStr);
            }
            return mP2pIfaceHal.isInitializationComplete();
        }
    }

    /**
     * Initiate a P2P service discovery with a (optional) timeout.
     *
     * @param timeout Max time to be spent is performing discovery.
     *        Set to 0 to indefinitely continue discovery until an explicit
     *        |stopFind| is sent.
     * @return boolean value indicating whether operation was successful.
     */
    public boolean find(int timeout) {
        synchronized (mLock) {
            String methodStr = "find";
            if (mP2pIfaceHal == null) {
                return handleNullHal(methodStr);
            }
            return mP2pIfaceHal.find(timeout);
        }
    }

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
    public boolean find(@WifiP2pManager.WifiP2pScanType int type, int freq, int timeout) {
        synchronized (mLock) {
            String methodStr = "find";
            if (mP2pIfaceHal == null) {
                return handleNullHal(methodStr);
            }
            return mP2pIfaceHal.find(type, freq, timeout);
        }
    }

    /**
     * Initiate P2P device discovery with config params.
     *
     * See comments for {@link ISupplicantP2pIfaceHal#findWithParams(WifiP2pDiscoveryConfig, int)}.
     */
    public boolean findWithParams(WifiP2pDiscoveryConfig config, int timeout) {
        synchronized (mLock) {
            String methodStr = "findWithParams";
            if (mP2pIfaceHal == null) {
                return handleNullHal(methodStr);
            }
            return mP2pIfaceHal.findWithParams(config, timeout);
        }
    }

    /**
     * Stop an ongoing P2P service discovery.
     *
     * @return boolean value indicating whether operation was successful.
     */
    public boolean stopFind() {
        synchronized (mLock) {
            String methodStr = "stopFind";
            if (mP2pIfaceHal == null) {
                return handleNullHal(methodStr);
            }
            return mP2pIfaceHal.stopFind();
        }
    }

    /**
     * Flush P2P peer table and state.
     *
     * @return boolean value indicating whether operation was successful.
     */
    public boolean flush() {
        synchronized (mLock) {
            String methodStr = "flush";
            if (mP2pIfaceHal == null) {
                return handleNullHal(methodStr);
            }
            return mP2pIfaceHal.flush();
        }
    }

    /**
     * This command can be used to flush all services from the
     * device.
     *
     * @return boolean value indicating whether operation was successful.
     */
    public boolean serviceFlush() {
        synchronized (mLock) {
            String methodStr = "serviceFlush";
            if (mP2pIfaceHal == null) {
                return handleNullHal(methodStr);
            }
            return mP2pIfaceHal.serviceFlush();
        }
    }

    /**
     * Turn on/off power save mode for the interface.
     *
     * @param groupIfName Group interface name to use.
     * @param enable Indicate if power save is to be turned on/off.
     *
     * @return boolean value indicating whether operation was successful.
     */
    public boolean setPowerSave(String groupIfName, boolean enable) {
        synchronized (mLock) {
            String methodStr = "setPowerSave";
            if (mP2pIfaceHal == null) {
                return handleNullHal(methodStr);
            }
            return mP2pIfaceHal.setPowerSave(groupIfName, enable);
        }
    }

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
    public boolean setGroupIdle(String groupIfName, int timeoutInSec) {
        synchronized (mLock) {
            String methodStr = "setGroupIdle";
            if (mP2pIfaceHal == null) {
                return handleNullHal(methodStr);
            }
            return mP2pIfaceHal.setGroupIdle(groupIfName, timeoutInSec);
        }
    }

    /**
     * Set the postfix to be used for P2P SSID's.
     *
     * @param postfix String to be appended to SSID.
     *
     * @return boolean value indicating whether operation was successful.
     */
    public boolean setSsidPostfix(String postfix) {
        synchronized (mLock) {
            String methodStr = "setSsidPostfix";
            if (mP2pIfaceHal == null) {
                return handleNullHal(methodStr);
            }
            return mP2pIfaceHal.setSsidPostfix(postfix);
        }
    }

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
    public String connect(WifiP2pConfig config, boolean joinExistingGroup) {
        synchronized (mLock) {
            String methodStr = "connect";
            if (mP2pIfaceHal == null) {
                handleNullHal(methodStr);
                return null;
            }
            return mP2pIfaceHal.connect(config, joinExistingGroup);
        }
    }

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
    public boolean cancelConnect() {
        synchronized (mLock) {
            String methodStr = "cancelConnect";
            if (mP2pIfaceHal == null) {
                return handleNullHal(methodStr);
            }
            return mP2pIfaceHal.cancelConnect();
        }
    }

    /**
     * Send P2P provision discovery request to the specified peer. The
     * parameters for this command are the P2P device address of the peer and the
     * desired configuration method.
     *
     * @param config Config class describing peer setup.
     *
     * @return boolean value indicating whether operation was successful.
     */
    public boolean provisionDiscovery(WifiP2pConfig config) {
        synchronized (mLock) {
            String methodStr = "provisionDiscovery";
            if (mP2pIfaceHal == null) {
                return handleNullHal(methodStr);
            }
            return mP2pIfaceHal.provisionDiscovery(config);
        }
    }

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
    public boolean invite(WifiP2pGroup group, String peerAddress) {
        synchronized (mLock) {
            String methodStr = "invite";
            if (mP2pIfaceHal == null) {
                return handleNullHal(methodStr);
            }
            return mP2pIfaceHal.invite(group, peerAddress);
        }
    }

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
    public boolean reject(String peerAddress) {
        synchronized (mLock) {
            String methodStr = "reject";
            if (mP2pIfaceHal == null) {
                return handleNullHal(methodStr);
            }
            return mP2pIfaceHal.reject(peerAddress);
        }
    }

    /**
     * Gets the MAC address of the device.
     *
     * @return MAC address of the device.
     */
    public String getDeviceAddress() {
        synchronized (mLock) {
            String methodStr = "getDeviceAddress";
            if (mP2pIfaceHal == null) {
                handleNullHal(methodStr);
                return null;
            }
            return mP2pIfaceHal.getDeviceAddress();
        }
    }

    /**
     * Gets the operational SSID of the device.
     *
     * @param address MAC address of the peer.
     *
     * @return SSID of the device.
     */
    public String getSsid(String address) {
        synchronized (mLock) {
            String methodStr = "getSsid";
            if (mP2pIfaceHal == null) {
                handleNullHal(methodStr);
                return null;
            }
            return mP2pIfaceHal.getSsid(address);
        }
    }

    /**
     * Reinvoke a device from a persistent group.
     *
     * @param networkId Used to specify the persistent group.
     * @param peerAddress MAC address of the device to reinvoke.
     *
     * @return true, if operation was successful.
     */
    public boolean reinvoke(int networkId, String peerAddress) {
        synchronized (mLock) {
            String methodStr = "reinvoke";
            if (mP2pIfaceHal == null) {
                return handleNullHal(methodStr);
            }
            return mP2pIfaceHal.reinvoke(networkId, peerAddress);
        }
    }

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
    public boolean groupAdd(int networkId, boolean isPersistent) {
        synchronized (mLock) {
            String methodStr = "groupAdd";
            if (mP2pIfaceHal == null) {
                return handleNullHal(methodStr);
            }
            return mP2pIfaceHal.groupAdd(networkId, isPersistent);
        }
    }

    /**
     * Set up a P2P group owner manually.
     * This is a helper method that invokes groupAdd(networkId, isPersistent) internally.
     *
     * @param isPersistent Used to request a persistent group to be formed.
     *
     * @return true, if operation was successful.
     */
    public boolean groupAdd(boolean isPersistent) {
        synchronized (mLock) {
            // Supplicant expects networkId to be -1 if not supplied.
            return groupAdd(-1, isPersistent);
        }
    }

    /**
     * Set up a P2P group as Group Owner or join a group with a configuration.
     *
     * @param networkName SSID of the group to be formed
     * @param passphrase passphrase of the group to be formed
     * @param isPersistent Used to request a persistent group to be formed.
     * @param freq prefered frequencty or band of the group to be formed
     * @param peerAddress peerAddress Group Owner MAC address, only applied for Group Client.
     *        If the MAC is "00:00:00:00:00:00", the device will try to find a peer
     *        whose SSID matches ssid.
     * @param join join a group or create a group
     *
     * @return true, if operation was successful.
     */
    public boolean groupAdd(String networkName, String passphrase,
            boolean isPersistent, int freq, String peerAddress, boolean join) {
        synchronized (mLock) {
            String methodStr = "groupAdd";
            if (mP2pIfaceHal == null) {
                return handleNullHal(methodStr);
            }
            return mP2pIfaceHal.groupAdd(networkName, passphrase,
                    isPersistent, freq, peerAddress, join);
        }
    }

    /**
     * Terminate a P2P group. If a new virtual network interface was used for
     * the group, it must also be removed. The network interface name of the
     * group interface is used as a parameter for this command.
     *
     * @param groupName Group interface name to use.
     *
     * @return true, if operation was successful.
     */
    public boolean groupRemove(String groupName) {
        synchronized (mLock) {
            String methodStr = "groupRemove";
            if (mP2pIfaceHal == null) {
                return handleNullHal(methodStr);
            }
            return mP2pIfaceHal.groupRemove(groupName);
        }
    }

    /**
     * Gets the capability of the group which the device is a
     * member of.
     *
     * @param peerAddress MAC address of the peer.
     *
     * @return combination of |GroupCapabilityMask| values.
     */
    public int getGroupCapability(String peerAddress) {
        synchronized (mLock) {
            String methodStr = "getGroupCapability";
            if (mP2pIfaceHal == null) {
                handleNullHal(methodStr);
                return -1;
            }
            return mP2pIfaceHal.getGroupCapability(peerAddress);
        }
    }

    /**
     * Configure Extended Listen Timing. See comments for
     * {@link ISupplicantP2pIfaceHal#configureExtListen(boolean, int, int, WifiP2pExtListenParams)}
     *
     * @return true, if operation was successful.
     */
    public boolean configureExtListen(boolean enable, int periodInMillis, int intervalInMillis,
            @Nullable WifiP2pExtListenParams extListenParams) {
        synchronized (mLock) {
            String methodStr = "configureExtListen";
            if (mP2pIfaceHal == null) {
                return handleNullHal(methodStr);
            }
            return mP2pIfaceHal.configureExtListen(
                    enable, periodInMillis, intervalInMillis, extListenParams);
        }
    }

    /**
     * Set P2P Listen channel.
     *
     * @param listenChannel Wifi channel. eg, 1, 6, 11.
     *
     * @return true, if operation was successful.
     */
    public boolean setListenChannel(int listenChannel) {
        synchronized (mLock) {
            String methodStr = "setListenChannel";
            if (mP2pIfaceHal == null) {
                return handleNullHal(methodStr);
            }
            return mP2pIfaceHal.setListenChannel(listenChannel);
        }
    }

    /**
     * Set P2P operating channel.
     *
     * @param operatingChannel the desired operating channel.
     * @param unsafeChannels channels which p2p cannot use.
     *
     * @return true, if operation was successful.
     */
    public boolean setOperatingChannel(int operatingChannel,
            @NonNull List<CoexUnsafeChannel> unsafeChannels) {
        synchronized (mLock) {
            String methodStr = "setOperatingChannel";
            if (mP2pIfaceHal == null) {
                return handleNullHal(methodStr);
            }
            return mP2pIfaceHal.setOperatingChannel(operatingChannel, unsafeChannels);
        }
    }

    /**
     * This command can be used to add a upnp/bonjour service.
     *
     * @param servInfo List of service queries.
     *
     * @return true, if operation was successful.
     */
    public boolean serviceAdd(WifiP2pServiceInfo servInfo) {
        synchronized (mLock) {
            String methodStr = "serviceAdd";
            if (mP2pIfaceHal == null) {
                return handleNullHal(methodStr);
            }
            return mP2pIfaceHal.serviceAdd(servInfo);
        }
    }

    /**
     * This command can be used to remove a upnp/bonjour service.
     *
     * @param servInfo List of service queries.
     *
     * @return true, if operation was successful.
     */
    public boolean serviceRemove(WifiP2pServiceInfo servInfo) {
        synchronized (mLock) {
            String methodStr = "serviceRemove";
            if (mP2pIfaceHal == null) {
                return handleNullHal(methodStr);
            }
            return mP2pIfaceHal.serviceRemove(servInfo);
        }
    }

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
    public String requestServiceDiscovery(String peerAddress, String query) {
        synchronized (mLock) {
            String methodStr = "requestServiceDiscovery";
            if (mP2pIfaceHal == null) {
                handleNullHal(methodStr);
                return null;
            }
            return mP2pIfaceHal.requestServiceDiscovery(peerAddress, query);
        }
    }

    /**
     * Cancel a previous service discovery request.
     *
     * @param identifier Identifier for the request to cancel.
     * @return true, if operation was successful.
     */
    public boolean cancelServiceDiscovery(String identifier) {
        synchronized (mLock) {
            String methodStr = "cancelServiceDiscovery";
            if (mP2pIfaceHal == null) {
                return handleNullHal(methodStr);
            }
            return mP2pIfaceHal.cancelServiceDiscovery(identifier);
        }
    }

    /**
     * Send driver command to set Miracast mode.
     *
     * @param mode Mode of Miracast.
     * @return true, if operation was successful.
     */
    public boolean setMiracastMode(int mode) {
        synchronized (mLock) {
            String methodStr = "setMiracastMode";
            if (mP2pIfaceHal == null) {
                return handleNullHal(methodStr);
            }
            return mP2pIfaceHal.setMiracastMode(mode);
        }
    }

    /**
     * Initiate WPS Push Button setup.
     * The PBC operation requires that a button is also pressed at the
     * AP/Registrar at about the same time (2 minute window).
     *
     * @param groupIfName Group interface name to use.
     * @param bssid BSSID of the AP. Use empty bssid to indicate wildcard.
     * @return true, if operation was successful.
     */
    public boolean startWpsPbc(String groupIfName, String bssid) {
        synchronized (mLock) {
            String methodStr = "startWpsPbc";
            if (mP2pIfaceHal == null) {
                return handleNullHal(methodStr);
            }
            return mP2pIfaceHal.startWpsPbc(groupIfName, bssid);
        }
    }

    /**
     * Initiate WPS Pin Keypad setup.
     *
     * @param groupIfName Group interface name to use.
     * @param pin 8 digit pin to be used.
     * @return true, if operation was successful.
     */
    public boolean startWpsPinKeypad(String groupIfName, String pin) {
        synchronized (mLock) {
            String methodStr = "startWpsPinKeypad";
            if (mP2pIfaceHal == null) {
                return handleNullHal(methodStr);
            }
            return mP2pIfaceHal.startWpsPinKeypad(groupIfName, pin);
        }
    }

    /**
     * Initiate WPS Pin Display setup.
     *
     * @param groupIfName Group interface name to use.
     * @param bssid BSSID of the AP. Use empty bssid to indicate wildcard.
     * @return generated pin if operation was successful, null otherwise.
     */
    public String startWpsPinDisplay(String groupIfName, String bssid) {
        synchronized (mLock) {
            String methodStr = "startWpsPinDisplay";
            if (mP2pIfaceHal == null) {
                handleNullHal(methodStr);
                return null;
            }
            return mP2pIfaceHal.startWpsPinDisplay(groupIfName, bssid);
        }
    }

    /**
     * Cancel any ongoing WPS operations.
     *
     * @param groupIfName Group interface name to use.
     * @return true, if operation was successful.
     */
    public boolean cancelWps(String groupIfName) {
        synchronized (mLock) {
            String methodStr = "cancelWps";
            if (mP2pIfaceHal == null) {
                return handleNullHal(methodStr);
            }
            return mP2pIfaceHal.cancelWps(groupIfName);
        }
    }

    /**
     * Enable/Disable Wifi Display.
     *
     * @param enable true to enable, false to disable.
     * @return true, if operation was successful.
     */
    public boolean enableWfd(boolean enable) {
        synchronized (mLock) {
            String methodStr = "enableWfd";
            if (mP2pIfaceHal == null) {
                return handleNullHal(methodStr);
            }
            return mP2pIfaceHal.enableWfd(enable);
        }
    }

    /**
     * Set Wifi Display device info.
     *
     * @param info WFD device info as described in section 5.1.2 of WFD technical
     *        specification v1.0.0.
     * @return true, if operation was successful.
     */
    public boolean setWfdDeviceInfo(String info) {
        synchronized (mLock) {
            String methodStr = "setWfdDeviceInfo";
            if (mP2pIfaceHal == null) {
                return handleNullHal(methodStr);
            }
            return mP2pIfaceHal.setWfdDeviceInfo(info);
        }
    }

    /**
     * Remove network with provided id.
     *
     * @param networkId Id of the network to lookup.
     * @return true, if operation was successful.
     */
    public boolean removeNetwork(int networkId) {
        synchronized (mLock) {
            String methodStr = "removeNetwork";
            if (mP2pIfaceHal == null) {
                return handleNullHal(methodStr);
            }
            return mP2pIfaceHal.removeNetwork(networkId);
        }
    }

    /**
     * Get the persistent group list from wpa_supplicant's p2p mgmt interface
     *
     * @param groups WifiP2pGroupList to store persistent groups in
     * @return true, if list has been modified.
     */
    public boolean loadGroups(WifiP2pGroupList groups) {
        synchronized (mLock) {
            String methodStr = "loadGroups";
            if (mP2pIfaceHal == null) {
                return handleNullHal(methodStr);
            }
            return mP2pIfaceHal.loadGroups(groups);
        }
    }

    /**
     * Set WPS device name.
     *
     * @param name String to be set.
     * @return true if request is sent successfully, false otherwise.
     */
    public boolean setWpsDeviceName(String name) {
        synchronized (mLock) {
            String methodStr = "setWpsDeviceName";
            if (mP2pIfaceHal == null) {
                return handleNullHal(methodStr);
            }
            return mP2pIfaceHal.setWpsDeviceName(name);
        }
    }

    /**
     * Set WPS device type.
     *
     * @param typeStr Type specified as a string. Used format: <categ>-<OUI>-<subcateg>
     * @return true if request is sent successfully, false otherwise.
     */
    public boolean setWpsDeviceType(String typeStr) {
        synchronized (mLock) {
            String methodStr = "setWpsDeviceType";
            if (mP2pIfaceHal == null) {
                return handleNullHal(methodStr);
            }
            return mP2pIfaceHal.setWpsDeviceType(typeStr);
        }
    }

    /**
     * Set WPS config methods
     *
     * @param configMethodsStr List of config methods.
     * @return true if request is sent successfully, false otherwise.
     */
    public boolean setWpsConfigMethods(String configMethodsStr) {
        synchronized (mLock) {
            String methodStr = "setWpsConfigMethods";
            if (mP2pIfaceHal == null) {
                return handleNullHal(methodStr);
            }
            return mP2pIfaceHal.setWpsConfigMethods(configMethodsStr);
        }
    }

    /**
     * Get NFC handover request message.
     *
     * @return select message if created successfully, null otherwise.
     */
    public String getNfcHandoverRequest() {
        synchronized (mLock) {
            String methodStr = "getNfcHandoverRequest";
            if (mP2pIfaceHal == null) {
                handleNullHal(methodStr);
                return null;
            }
            return mP2pIfaceHal.getNfcHandoverRequest();
        }
    }

    /**
     * Get NFC handover select message.
     *
     * @return select message if created successfully, null otherwise.
     */
    public String getNfcHandoverSelect() {
        synchronized (mLock) {
            String methodStr = "getNfcHandoverSelect";
            if (mP2pIfaceHal == null) {
                handleNullHal(methodStr);
                return null;
            }
            return mP2pIfaceHal.getNfcHandoverSelect();
        }
    }

    /**
     * Report NFC handover select message.
     *
     * @return true if reported successfully, false otherwise.
     */
    public boolean initiatorReportNfcHandover(String selectMessage) {
        synchronized (mLock) {
            String methodStr = "initiatorReportNfcHandover";
            if (mP2pIfaceHal == null) {
                return handleNullHal(methodStr);
            }
            return mP2pIfaceHal.initiatorReportNfcHandover(selectMessage);
        }
    }

    /**
     * Report NFC handover request message.
     *
     * @return true if reported successfully, false otherwise.
     */
    public boolean responderReportNfcHandover(String requestMessage) {
        synchronized (mLock) {
            String methodStr = "responderReportNfcHandover";
            if (mP2pIfaceHal == null) {
                return handleNullHal(methodStr);
            }
            return mP2pIfaceHal.responderReportNfcHandover(requestMessage);
        }
    }

    /**
     * Set the client list for the provided network.
     *
     * @param networkId Id of the network.
     * @param clientListStr Space separated list of clients.
     * @return true, if operation was successful.
     */
    public boolean setClientList(int networkId, String clientListStr) {
        synchronized (mLock) {
            String methodStr = "setClientList";
            if (mP2pIfaceHal == null) {
                return handleNullHal(methodStr);
            }
            return mP2pIfaceHal.setClientList(networkId, clientListStr);
        }
    }

    /**
     * Set the client list for the provided network.
     *
     * @param networkId Id of the network.
     * @return Space separated list of clients if successful, null otherwise.
     */
    public String getClientList(int networkId) {
        synchronized (mLock) {
            String methodStr = "getClientList";
            if (mP2pIfaceHal == null) {
                handleNullHal(methodStr);
                return null;
            }
            return mP2pIfaceHal.getClientList(networkId);
        }
    }

    /**
     * Persist the current configurations to disk.
     *
     * @return true, if operation was successful.
     */
    public boolean saveConfig() {
        synchronized (mLock) {
            String methodStr = "saveConfig";
            if (mP2pIfaceHal == null) {
                return handleNullHal(methodStr);
            }
            return mP2pIfaceHal.saveConfig();
        }
    }

    /**
     * Enable/Disable P2P MAC randomization.
     *
     * @param enable true to enable, false to disable.
     * @return true, if operation was successful.
     */
    public boolean setMacRandomization(boolean enable) {
        synchronized (mLock) {
            String methodStr = "setMacRandomization";
            if (mP2pIfaceHal == null) {
                return handleNullHal(methodStr);
            }
            return mP2pIfaceHal.setMacRandomization(enable);
        }
    }

    /**
     * Set Wifi Display R2 device info.
     *
     * @param info WFD R2 device info as described in section 5.1.12 of WFD technical
     *        specification v2.1.
     * @return true, if operation was successful.
     */
    public boolean setWfdR2DeviceInfo(String info) {
        synchronized (mLock) {
            String methodStr = "setWfdR2DeviceInfo";
            if (mP2pIfaceHal == null) {
                return handleNullHal(methodStr);
            }
            return mP2pIfaceHal.setWfdR2DeviceInfo(info);
        }
    }

    /**
     * Remove the client with the MAC address from the group.
     *
     * @param peerAddress Mac address of the client.
     * @param isLegacyClient Indicate if client is a legacy client or not.
     * @return true if success
     */
    public boolean removeClient(String peerAddress, boolean isLegacyClient) {
        synchronized (mLock) {
            String methodStr = "removeClient";
            if (mP2pIfaceHal == null) {
                return handleNullHal(methodStr);
            }
            return mP2pIfaceHal.removeClient(peerAddress, isLegacyClient);
        }
    }

    /**
     * Set vendor-specific information elements to wpa_supplicant.
     *
     * @param vendorElements The list of vendor-specific information elements.
     *
     * @return boolean The value indicating whether operation was successful.
     */
    public boolean setVendorElements(Set<ScanResult.InformationElement> vendorElements) {
        synchronized (mLock) {
            String methodStr = "setVendorElements";
            if (mP2pIfaceHal == null) {
                return handleNullHal(methodStr);
            }
            return mP2pIfaceHal.setVendorElements(vendorElements);
        }
    }

    /**
     * Get the supported features.
     *
     * @return  bitmask defined by WifiP2pManager.FEATURE_*
     */
    public long getSupportedFeatures() {
        if (mP2pIfaceHal instanceof SupplicantP2pIfaceHalHidlImpl) return 0L;
        return ((SupplicantP2pIfaceHalAidlImpl) mP2pIfaceHal).getSupportedFeatures();
    }

    private boolean handleNullHal(String methodStr) {
        Log.e(TAG, "Cannot call " + methodStr + " because HAL object is null.");
        return false;
    }

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
    public boolean configureEapolIpAddressAllocationParams(int ipAddressGo, int ipAddressMask,
            int ipAddressStart, int ipAddressEnd) {
        synchronized (mLock) {
            String methodStr = "configureEapolIpAddressAllocationParams";
            if (mP2pIfaceHal == null) {
                return handleNullHal(methodStr);
            }
            return mP2pIfaceHal.configureEapolIpAddressAllocationParams(ipAddressGo, ipAddressMask,
                    ipAddressStart, ipAddressEnd);
        }
    }

    /**
     * Terminate the supplicant daemon & wait for its death.
     */
    public void terminate() {
        synchronized (mLock) {
            String methodStr = "terminate";
            if (mP2pIfaceHal == null) {
                handleNullHal(methodStr);
                return;
            }
            mP2pIfaceHal.terminate();
        }
    }

    /**
     * Registers a death notification for supplicant.
     * @return Returns true on success.
     */
    public boolean registerDeathHandler(@NonNull WifiNative.SupplicantDeathEventHandler handler) {
        synchronized (mLock) {
            String methodStr = "registerDeathHandler";
            if (mP2pIfaceHal == null) {
                return handleNullHal(methodStr);
            }
            return mP2pIfaceHal.registerDeathHandler(handler);
        }
    }

    /**
     * Deregisters a death notification for supplicant.
     * @return Returns true on success.
     */
    public boolean deregisterDeathHandler() {
        synchronized (mLock) {
            String methodStr = "deregisterDeathHandler";
            if (mP2pIfaceHal == null) {
                return handleNullHal(methodStr);
            }
            return mP2pIfaceHal.deregisterDeathHandler();
        }
    }
}
