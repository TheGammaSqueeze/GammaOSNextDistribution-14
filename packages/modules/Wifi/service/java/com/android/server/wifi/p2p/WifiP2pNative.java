/*
 * Copyright (C) 2008 The Android Open Source Project
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

import static com.android.server.wifi.HalDeviceManager.HDM_CREATE_IFACE_P2P;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.net.wifi.CoexUnsafeChannel;
import android.net.wifi.ScanResult;
import android.net.wifi.nl80211.WifiNl80211Manager;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDiscoveryConfig;
import android.net.wifi.p2p.WifiP2pExtListenParams;
import android.net.wifi.p2p.WifiP2pGroup;
import android.net.wifi.p2p.WifiP2pGroupList;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.nsd.WifiP2pServiceInfo;
import android.os.Handler;
import android.os.WorkSource;
import android.text.TextUtils;
import android.util.Log;

import com.android.server.wifi.HalDeviceManager;
import com.android.server.wifi.PropertyService;
import com.android.server.wifi.WifiInjector;
import com.android.server.wifi.WifiMetrics;
import com.android.server.wifi.WifiNative;
import com.android.server.wifi.WifiVendorHal;
import com.android.wifi.flags.FeatureFlags;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Native calls for bring up/shut down of the supplicant daemon and for
 * sending requests to the supplicant daemon
 */
public class WifiP2pNative {
    private static final String TAG = "WifiP2pNative";
    private boolean mVerboseLoggingEnabled = false;
    private final SupplicantP2pIfaceHal mSupplicantP2pIfaceHal;
    private final WifiNative mWifiNative;
    private final WifiMetrics mWifiMetrics;
    private final WifiNl80211Manager mWifiNl80211Manager;
    private final HalDeviceManager mHalDeviceManager;
    private final PropertyService mPropertyService;
    private final WifiVendorHal mWifiVendorHal;
    private final WifiInjector mWifiInjector;
    private final FeatureFlags mFeatureFlags;
    private final Object mLock = new Object();
    private WifiNative.Iface mP2pIface;
    private String mP2pIfaceName;
    private InterfaceDestroyedListenerInternal mInterfaceDestroyedListener;

    /**
     * Death handler for the supplicant daemon.
     */
    private class SupplicantDeathHandlerInternal implements WifiNative.SupplicantDeathEventHandler {
        @Override
        public void onDeath() {
            if (mP2pIface != null) {
                Log.i(TAG, "wpa_supplicant died. Cleaning up internal state.");
                mInterfaceDestroyedListener.teardownAndInvalidate(mP2pIface.name);
                mWifiMetrics.incrementNumSupplicantCrashes();
            }
        }
    }

    // Internal callback registered to HalDeviceManager.
    private class InterfaceDestroyedListenerInternal implements
            HalDeviceManager.InterfaceDestroyedListener {
        private final HalDeviceManager.InterfaceDestroyedListener mExternalListener;
        private boolean mValid;

        InterfaceDestroyedListenerInternal(
                HalDeviceManager.InterfaceDestroyedListener externalListener) {
            mExternalListener = externalListener;
            mValid = true;
        }

        public void teardownAndInvalidate(@Nullable String ifaceName) {
            synchronized (mLock) {
                if (mFeatureFlags.d2dWhenInfraStaOff()
                        && !mSupplicantP2pIfaceHal.deregisterDeathHandler()) {
                    Log.i(TAG, "Failed to deregister p2p supplicant death handler");
                }
                if (!TextUtils.isEmpty(ifaceName)) {
                    mSupplicantP2pIfaceHal.teardownIface(ifaceName);
                    if (mP2pIface != null) {
                        mWifiNative.teardownP2pIface(mP2pIface.id);
                    }
                }
                mP2pIfaceName = null;
                mP2pIface = null;
                mValid = false;
                Log.i(TAG, "teardownAndInvalidate is completed");
            }
        }

        @Override
        public void onDestroyed(String ifaceName) {
            synchronized (mLock) {
                Log.d(TAG, "P2P InterfaceDestroyedListener " + ifaceName);
                if (!mValid) {
                    Log.d(TAG, "Ignoring stale interface destroyed listener");
                    return;
                }
                teardownAndInvalidate(ifaceName);
                mExternalListener.onDestroyed(ifaceName);
            }
        }
    }

    public WifiP2pNative(
            WifiNl80211Manager wifiNl80211Manager,
            WifiNative wifiNative,
            WifiMetrics wifiMetrics,
            WifiVendorHal wifiVendorHal,
            SupplicantP2pIfaceHal p2pIfaceHal,
            HalDeviceManager halDeviceManager,
            PropertyService propertyService,
            WifiInjector wifiInjector) {
        mWifiNative = wifiNative;
        mWifiMetrics = wifiMetrics;
        mWifiNl80211Manager = wifiNl80211Manager;
        mWifiVendorHal = wifiVendorHal;
        mSupplicantP2pIfaceHal = p2pIfaceHal;
        mHalDeviceManager = halDeviceManager;
        mPropertyService = propertyService;
        mWifiInjector = wifiInjector;
        mFeatureFlags = wifiInjector.getDeviceConfigFacade().getFeatureFlags();
    }

    /**
     * Enable verbose logging for all sub modules.
     */
    public void enableVerboseLogging(boolean verboseEnabled, boolean halVerboseEnabled) {
        mVerboseLoggingEnabled = verboseEnabled;
        SupplicantP2pIfaceHal.enableVerboseLogging(verboseEnabled, halVerboseEnabled);
    }

    private static final int CONNECT_TO_SUPPLICANT_SAMPLING_INTERVAL_MS = 100;
    private static final int CONNECT_TO_SUPPLICANT_MAX_SAMPLES = 50;
    /**
     * This method is called to wait for establishing connection to wpa_supplicant.
     *
     * @return true if connection is established, false otherwise.
     */
    private boolean waitForSupplicantConnection() {
        // Start initialization if not already started.
        if (!mSupplicantP2pIfaceHal.isInitializationStarted()
                && !mSupplicantP2pIfaceHal.initialize()) {
            return false;
        }
        int connectTries = 0;
        while (connectTries++ < CONNECT_TO_SUPPLICANT_MAX_SAMPLES) {
            // Check if the initialization is complete.
            if (mSupplicantP2pIfaceHal.isInitializationComplete()) {
                return true;
            }
            try {
                Thread.sleep(CONNECT_TO_SUPPLICANT_SAMPLING_INTERVAL_MS);
            } catch (InterruptedException ignore) {
            }
        }
        return false;
    }

    /**
     * Close supplicant connection.
     */
    public void stopP2pSupplicantIfNecessary() {
        if (mFeatureFlags.d2dWhenInfraStaOff()
                && mSupplicantP2pIfaceHal.isInitializationStarted()) {
            mSupplicantP2pIfaceHal.terminate();
        }
    }

    /**
     * Returns whether HAL is supported on this device or not.
     */
    public boolean isHalInterfaceSupported() {
        return mHalDeviceManager.isSupported();
    }

    public static final String P2P_IFACE_NAME = "p2p0";
    public static final String P2P_INTERFACE_PROPERTY = "wifi.direct.interface";

    /**
     * Helper function to handle creation of P2P iface.
     * For devices which do not the support the HAL, this will bypass HalDeviceManager &
     * teardown any existing iface.
     */
    private String createP2pIface(Handler handler, WorkSource requestorWs) {
        if (mHalDeviceManager.isSupported()) {
            mP2pIfaceName = mHalDeviceManager.createP2pIface(
                    mInterfaceDestroyedListener, handler, requestorWs);
            if (mP2pIfaceName == null) {
                Log.e(TAG, "Failed to create P2p iface in HalDeviceManager");
                return null;
            }
            return mP2pIfaceName;
        } else {
            Log.i(TAG, "Vendor Hal is not supported, ignoring createP2pIface.");
            return mPropertyService.getString(P2P_INTERFACE_PROPERTY, P2P_IFACE_NAME);
        }
    }

    /**
     * Setup Interface for P2p mode.
     *
     * @param destroyedListener Listener to be invoked when the interface is destroyed.
     * @param handler Handler to be used for invoking the destroyedListener.
     * @param requestorWs Worksource to attribute the request to.
     */
    public String setupInterface(
            @Nullable HalDeviceManager.InterfaceDestroyedListener destroyedListener,
            @NonNull Handler handler, @NonNull WorkSource requestorWs) {
        synchronized (mLock) {
            Log.d(TAG, "Setup P2P interface");
            if (mP2pIfaceName == null) {
                mInterfaceDestroyedListener = (null == destroyedListener)
                        ? null
                        : new InterfaceDestroyedListenerInternal(destroyedListener);
                if (mFeatureFlags.d2dWhenInfraStaOff()) {
                    mP2pIface = mWifiNative.createP2pIface(mInterfaceDestroyedListener, handler,
                        requestorWs);
                    if (mP2pIface != null) {
                        mP2pIfaceName = mP2pIface.name;
                    }
                } else {
                    mP2pIfaceName = createP2pIface(handler, requestorWs);
                }
                if (mP2pIfaceName == null) {
                    Log.e(TAG, "Failed to create P2p iface");
                    if (mHalDeviceManager.isItPossibleToCreateIface(HDM_CREATE_IFACE_P2P,
                            requestorWs)) {
                        mWifiMetrics.incrementNumSetupP2pInterfaceFailureDueToHal();
                    }
                    return null;
                }
                if (!waitForSupplicantConnection()) {
                    Log.e(TAG, "Failed to connect to supplicant");
                    teardownInterface();
                    mWifiMetrics.incrementNumSetupP2pInterfaceFailureDueToSupplicant();
                    return null;
                }
                if (!mSupplicantP2pIfaceHal.setupIface(mP2pIfaceName)) {
                    Log.e(TAG, "Failed to setup P2p iface in supplicant");
                    teardownInterface();
                    mWifiMetrics.incrementNumSetupP2pInterfaceFailureDueToSupplicant();
                    return null;
                }
                if (mFeatureFlags.d2dWhenInfraStaOff()
                        && !mSupplicantP2pIfaceHal.registerDeathHandler(
                                new SupplicantDeathHandlerInternal())) {
                    Log.e(TAG, "Failed to register supplicant death handler"
                            + "(because hidl supplicant?)");
                    teardownInterface();
                    mWifiMetrics.incrementNumSetupP2pInterfaceFailureDueToSupplicant();
                    return null;
                }
                Log.i(TAG, "P2P interface setup completed");
                return mP2pIfaceName;
            } else {
                Log.i(TAG, "P2P interface already exists");
                return mHalDeviceManager.isSupported()
                    ? mP2pIfaceName
                    : mPropertyService.getString(P2P_INTERFACE_PROPERTY, P2P_IFACE_NAME);
            }
        }
    }

    /**
     * Teardown P2p interface.
     */
    public void teardownInterface() {
        synchronized (mLock) {
            Log.d(TAG, "Teardown P2P interface:" + mP2pIfaceName);
            if (mHalDeviceManager.isSupported()) {
                if (mP2pIfaceName != null) {
                    mHalDeviceManager.removeP2pIface(mP2pIfaceName);
                    Log.i(TAG, "P2P interface teardown completed");
                    if (!mFeatureFlags.d2dWhenInfraStaOff()) {
                        if (null != mInterfaceDestroyedListener) {
                            mInterfaceDestroyedListener.teardownAndInvalidate(mP2pIfaceName);
                        }
                    }
                }
            } else {
                Log.i(TAG, "HAL is not supported. Destroy listener for the interface.");
                String ifaceName = mPropertyService.getString(P2P_INTERFACE_PROPERTY,
                        P2P_IFACE_NAME);
                if (null != mInterfaceDestroyedListener) {
                    mInterfaceDestroyedListener.teardownAndInvalidate(ifaceName);
                }
            }
        }
    }

    /**
     * Replace requestorWs in-place when iface is already enabled.
     */
    public boolean replaceRequestorWs(WorkSource requestorWs) {
        synchronized (mLock) {
            if (mHalDeviceManager.isSupported()) {
                if (mP2pIfaceName == null) return false;
                return mHalDeviceManager.replaceRequestorWsForP2pIface(mP2pIfaceName, requestorWs);
            } else {
                Log.i(TAG, "HAL is not supported. Ignore replace requestorWs");
                return true;
            }
        }
    }

    /**
     * Get the supported features.
     *
     * The features can be retrieved regardless of whether the P2P interface is up.
     *
     * Note that the feature set may be incomplete if Supplicant has not been started
     * on the device yet.
     *
     * @return bitmask defined by WifiP2pManager.FEATURE_*
     */
    public long getSupportedFeatures() {
        return mSupplicantP2pIfaceHal.getSupportedFeatures();
    }

    /**
     * Set WPS device name.
     *
     * @param name String to be set.
     * @return true if request is sent successfully, false otherwise.
     */
    public boolean setDeviceName(String name) {
        return mSupplicantP2pIfaceHal.setWpsDeviceName(name);
    }

    /**
     * Populate list of available networks or update existing list.
     *
     * @return true, if list has been modified.
     */
    public boolean p2pListNetworks(WifiP2pGroupList groups) {
        return mSupplicantP2pIfaceHal.loadGroups(groups);
    }

    /**
     * Initiate WPS Push Button setup.
     * The PBC operation requires that a button is also pressed at the
     * AP/Registrar at about the same time (2 minute window).
     *
     * @param iface Group interface name to use.
     * @param bssid BSSID of the AP. Use zero'ed bssid to indicate wildcard.
     * @return true, if operation was successful.
     */
    public boolean startWpsPbc(String iface, String bssid) {
        return mSupplicantP2pIfaceHal.startWpsPbc(iface, bssid);
    }

    /**
     * Initiate WPS Pin Keypad setup.
     *
     * @param iface Group interface name to use.
     * @param pin 8 digit pin to be used.
     * @return true, if operation was successful.
     */
    public boolean startWpsPinKeypad(String iface, String pin) {
        return mSupplicantP2pIfaceHal.startWpsPinKeypad(iface, pin);
    }

    /**
     * Initiate WPS Pin Display setup.
     *
     * @param iface Group interface name to use.
     * @param bssid BSSID of the AP. Use zero'ed bssid to indicate wildcard.
     * @return generated pin if operation was successful, null otherwise.
     */
    public String startWpsPinDisplay(String iface, String bssid) {
        return mSupplicantP2pIfaceHal.startWpsPinDisplay(iface, bssid);
    }

    /**
     * Remove network with provided id.
     *
     * @param netId Id of the network to lookup.
     * @return true, if operation was successful.
     */
    public boolean removeP2pNetwork(int netId) {
        return mSupplicantP2pIfaceHal.removeNetwork(netId);
    }

    /**
     * Set WPS device type.
     *
     * @param type Type specified as a string. Used format: <categ>-<OUI>-<subcateg>
     * @return true if request is sent successfully, false otherwise.
     */
    public boolean setP2pDeviceType(String type) {
        return mSupplicantP2pIfaceHal.setWpsDeviceType(type);
    }

    /**
     * Set WPS config methods
     *
     * @param cfg List of config methods.
     * @return true if request is sent successfully, false otherwise.
     */
    public boolean setConfigMethods(String cfg) {
        return mSupplicantP2pIfaceHal.setWpsConfigMethods(cfg);
    }

    /**
     * Set the postfix to be used for P2P SSID's.
     *
     * @param postfix String to be appended to SSID.
     *
     * @return boolean value indicating whether operation was successful.
     */
    public boolean setP2pSsidPostfix(String postfix) {
        return mSupplicantP2pIfaceHal.setSsidPostfix(postfix);
    }

    /**
     * Set the Maximum idle time in seconds for P2P groups.
     * This value controls how long a P2P group is maintained after there
     * is no other members in the group. As a group owner, this means no
     * associated stations in the group. As a P2P client, this means no
     * group owner seen in scan results.
     *
     * @param iface Group interface name to use.
     * @param time Timeout value in seconds.
     *
     * @return boolean value indicating whether operation was successful.
     */
    public boolean setP2pGroupIdle(String iface, int time) {
        return mSupplicantP2pIfaceHal.setGroupIdle(iface, time);
    }

    /**
     * Turn on/off power save mode for the interface.
     *
     * @param iface Group interface name to use.
     * @param enabled Indicate if power save is to be turned on/off.
     *
     * @return boolean value indicating whether operation was successful.
     */
    public boolean setP2pPowerSave(String iface, boolean enabled) {
        return mSupplicantP2pIfaceHal.setPowerSave(iface, enabled);
    }

    /**
     * Enable/Disable Wifi Display.
     *
     * @param enable true to enable, false to disable.
     * @return true, if operation was successful.
     */
    public boolean setWfdEnable(boolean enable) {
        return mSupplicantP2pIfaceHal.enableWfd(enable);
    }

    /**
     * Set Wifi Display device info.
     *
     * @param hex WFD device info as described in section 5.1.2 of WFD technical
     *        specification v1.0.0.
     * @return true, if operation was successful.
     */
    public boolean setWfdDeviceInfo(String hex) {
        return mSupplicantP2pIfaceHal.setWfdDeviceInfo(hex);
    }

    /**
     * Initiate a P2P service discovery indefinitely.
     * Will trigger {@link WifiP2pMonitor#P2P_DEVICE_FOUND_EVENT} on finding devices.
     *
     * @return boolean value indicating whether operation was successful.
     */
    public boolean p2pFind() {
        return p2pFind(0);
    }

    /**
     * Initiate a P2P service discovery with a (optional) timeout.
     *
     * @param timeout The maximum amount of time to be spent in performing discovery.
     *        Set to 0 to indefinitely continue discovery until an explicit
     *        |stopFind| is sent.
     * @return boolean value indicating whether operation was successful.
     */
    public boolean p2pFind(int timeout) {
        return mSupplicantP2pIfaceHal.find(timeout);
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
     * @param timeout The maximum amount of time to be spent in performing discovery.
     *        Set to 0 to indefinitely continue discovery until an explicit
     *        |stopFind| is sent.
     * @return boolean value indicating whether operation was successful.
     */
    public boolean p2pFind(@WifiP2pManager.WifiP2pScanType int type, int freq, int timeout) {
        return mSupplicantP2pIfaceHal.find(type, freq, timeout);
    }

    /**
     * Initiate a P2P service discovery with config parameters.
     *
     * @param config The config parameters to initiate P2P discovery.
     * @param timeout The maximum amount of time to be spent in performing discovery.
     *        Set to 0 to indefinitely continue discovery until an explicit
     *        |stopFind| is sent.
     * @return boolean value indicating whether the operation was successful.
     */
    public boolean p2pFindWithParams(@NonNull WifiP2pDiscoveryConfig config, int timeout) {
        return mSupplicantP2pIfaceHal.findWithParams(config, timeout);
    }

    /**
     * Stop an ongoing P2P service discovery.
     *
     * @return boolean value indicating whether operation was successful.
     */
    public boolean p2pStopFind() {
        return mSupplicantP2pIfaceHal.stopFind();
    }

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
     * @param period Period in milliseconds.
     * @param interval Interval in milliseconds.
     * @param extListenParams Additional parameter struct for this request.
     *
     * @return true, if operation was successful.
     */
    public boolean p2pExtListen(boolean enable, int period, int interval,
            @Nullable WifiP2pExtListenParams extListenParams) {
        return mSupplicantP2pIfaceHal.configureExtListen(enable, period, interval, extListenParams);
    }

    /**
     * Set P2P Listen channel.
     *
     * When specifying a social channel on the 2.4 GHz band (1/6/11) there is no
     * need to specify the operating class since it defaults to 81. When
     * specifying a social channel on the 60 GHz band (2), specify the 60 GHz
     * operating class (180).
     *
     * @param lc Wifi channel. eg, 1, 6, 11.
     *
     * @return true, if operation was successful.
     */
    public boolean p2pSetListenChannel(int lc) {
        return mSupplicantP2pIfaceHal.setListenChannel(lc);
    }

    /**
     * Set P2P operating channel.
     *
     * @param oc Wifi channel, eg, 1, 6, 11.
     * @param unsafeChannels channels are not allowed.
     * @return true if operation was successful.
     */
    public boolean p2pSetOperatingChannel(int oc, @NonNull List<CoexUnsafeChannel> unsafeChannels) {
        if (null == unsafeChannels) {
            Log.wtf(TAG, "unsafeChannels is null.");
            return false;
        }
        return mSupplicantP2pIfaceHal.setOperatingChannel(oc, unsafeChannels);
    }

    /**
     * Flush P2P peer table and state.
     *
     * @return boolean value indicating whether operation was successful.
     */
    public boolean p2pFlush() {
        return mSupplicantP2pIfaceHal.flush();
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
    public String p2pConnect(WifiP2pConfig config, boolean joinExistingGroup) {
        return mSupplicantP2pIfaceHal.connect(config, joinExistingGroup);
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
    public boolean p2pCancelConnect() {
        return mSupplicantP2pIfaceHal.cancelConnect();
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
    public boolean p2pProvisionDiscovery(WifiP2pConfig config) {
        return mSupplicantP2pIfaceHal.provisionDiscovery(config);
    }

    /**
     * Set up a P2P group owner manually.
     * This is a helper method that invokes groupAdd(networkId, isPersistent) internally.
     *
     * @param persistent Used to request a persistent group to be formed.
     *
     * @return true, if operation was successful.
     */
    public boolean p2pGroupAdd(boolean persistent) {
        return mSupplicantP2pIfaceHal.groupAdd(persistent);
    }

    /**
     * Set up a P2P group owner manually (i.e., without group owner
     * negotiation with a specific peer). This is also known as autonomous
     * group owner.
     *
     * @param netId Used to specify the restart of a persistent group.
     *
     * @return true, if operation was successful.
     */
    public boolean p2pGroupAdd(int netId) {
        return mSupplicantP2pIfaceHal.groupAdd(netId, true);
    }

    /**
     * Set up a P2P group as Group Owner or join a group with a configuration.
     *
     * @param config Used to specify config for setting up a P2P group
     *
     * @return true, if operation was successful.
     */
    public boolean p2pGroupAdd(WifiP2pConfig config, boolean join) {
        int freq = 0;
        switch (config.groupOwnerBand) {
            case WifiP2pConfig.GROUP_OWNER_BAND_2GHZ:
                freq = 2;
                break;
            case WifiP2pConfig.GROUP_OWNER_BAND_5GHZ:
                freq = 5;
                break;
            // treat it as frequency.
            default:
                freq = config.groupOwnerBand;
        }
        abortWifiRunningScanIfNeeded(join);
        return mSupplicantP2pIfaceHal.groupAdd(
                config.networkName,
                config.passphrase,
                (config.netId == WifiP2pGroup.NETWORK_ID_PERSISTENT),
                freq, config.deviceAddress, join);
    }

    private void abortWifiRunningScanIfNeeded(boolean isJoin) {
        if (!isJoin) return;

        Set<String> wifiClientInterfaces = mWifiNative.getClientInterfaceNames();

        for (String interfaceName: wifiClientInterfaces) {
            mWifiNl80211Manager.abortScan(interfaceName);
        }
    }

    /**
     * Terminate a P2P group. If a new virtual network interface was used for
     * the group, it must also be removed. The network interface name of the
     * group interface is used as a parameter for this command.
     *
     * @param iface Group interface name to use.
     * @return true, if operation was successful.
     */
    public boolean p2pGroupRemove(String iface) {
        return mSupplicantP2pIfaceHal.groupRemove(iface);
    }

    /**
     * Reject connection attempt from a peer (specified with a device
     * address). This is a mechanism to reject a pending group owner negotiation
     * with a peer and request to automatically block any further connection or
     * discovery of the peer.
     *
     * @param deviceAddress MAC address of the device to reject.
     *
     * @return boolean value indicating whether operation was successful.
     */
    public boolean p2pReject(String deviceAddress) {
        return mSupplicantP2pIfaceHal.reject(deviceAddress);
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
     * @param deviceAddress MAC address of the device to invite.
     *
     * @return boolean value indicating whether operation was successful.
     */
    public boolean p2pInvite(WifiP2pGroup group, String deviceAddress) {
        return mSupplicantP2pIfaceHal.invite(group, deviceAddress);
    }

    /**
     * Reinvoke a device from a persistent group.
     *
     * @param netId Used to specify the persistent group.
     * @param deviceAddress MAC address of the device to reinvoke.
     *
     * @return true, if operation was successful.
     */
    public boolean p2pReinvoke(int netId, String deviceAddress) {
        return mSupplicantP2pIfaceHal.reinvoke(netId, deviceAddress);
    }

    /**
     * Gets the operational SSID of the device.
     *
     * @param deviceAddress MAC address of the peer.
     *
     * @return SSID of the device.
     */
    public String p2pGetSsid(String deviceAddress) {
        return mSupplicantP2pIfaceHal.getSsid(deviceAddress);
    }

    /**
     * Gets the MAC address of the device.
     *
     * @return MAC address of the device.
     */
    public String p2pGetDeviceAddress() {
        return mSupplicantP2pIfaceHal.getDeviceAddress();
    }

    /**
     * Gets the capability of the group which the device is a
     * member of.
     *
     * @param deviceAddress MAC address of the peer.
     *
     * @return combination of |GroupCapabilityMask| values.
     */
    public int getGroupCapability(String deviceAddress) {
        return mSupplicantP2pIfaceHal.getGroupCapability(deviceAddress);
    }

    /**
     * This command can be used to add a upnp/bonjour service.
     *
     * @param servInfo List of service queries.
     *
     * @return true, if operation was successful.
     */
    public boolean p2pServiceAdd(WifiP2pServiceInfo servInfo) {
        return mSupplicantP2pIfaceHal.serviceAdd(servInfo);
    }

    /**
     * This command can be used to remove a upnp/bonjour service.
     *
     * @param servInfo List of service queries.
     *
     * @return true, if operation was successful.
     */
    public boolean p2pServiceDel(WifiP2pServiceInfo servInfo) {
        return mSupplicantP2pIfaceHal.serviceRemove(servInfo);
    }

    /**
     * This command can be used to flush all services from the
     * device.
     *
     * @return boolean value indicating whether operation was successful.
     */
    public boolean p2pServiceFlush() {
        return mSupplicantP2pIfaceHal.serviceFlush();
    }

    /**
     * Schedule a P2P service discovery request. The parameters for this command
     * are the device address of the peer device (or 00:00:00:00:00:00 for
     * wildcard query that is sent to every discovered P2P peer that supports
     * service discovery) and P2P Service Query TLV(s) as hexdump.
     *
     * @param addr MAC address of the device to discover.
     * @param query Hex dump of the query data.
     * @return identifier Identifier for the request. Can be used to cancel the
     *         request.
     */
    public String p2pServDiscReq(String addr, String query) {
        return mSupplicantP2pIfaceHal.requestServiceDiscovery(addr, query);
    }

    /**
     * Cancel a previous service discovery request.
     *
     * @param id Identifier for the request to cancel.
     * @return true, if operation was successful.
     */
    public boolean p2pServDiscCancelReq(String id) {
        return mSupplicantP2pIfaceHal.cancelServiceDiscovery(id);
    }

    /**
     * Send driver command to set Miracast mode.
     *
     * @param mode Mode of Miracast.
     *        0 = disabled
     *        1 = operating as source
     *        2 = operating as sink
     */
    public void setMiracastMode(int mode) {
        mSupplicantP2pIfaceHal.setMiracastMode(mode);
    }

    /**
     * Get NFC handover request message.
     *
     * @return select message if created successfully, null otherwise.
     */
    public String getNfcHandoverRequest() {
        return mSupplicantP2pIfaceHal.getNfcHandoverRequest();
    }

    /**
     * Get NFC handover select message.
     *
     * @return select message if created successfully, null otherwise.
     */
    public String getNfcHandoverSelect() {
        return mSupplicantP2pIfaceHal.getNfcHandoverSelect();
    }

    /**
     * Report NFC handover select message.
     *
     * @return true if reported successfully, false otherwise.
     */
    public boolean initiatorReportNfcHandover(String selectMessage) {
        return mSupplicantP2pIfaceHal.initiatorReportNfcHandover(selectMessage);
    }

    /**
     * Report NFC handover request message.
     *
     * @return true if reported successfully, false otherwise.
     */
    public boolean responderReportNfcHandover(String requestMessage) {
        return mSupplicantP2pIfaceHal.responderReportNfcHandover(requestMessage);
    }

    /**
     * Set the client list for the provided network.
     *
     * @param netId Id of the network.
     * @return  Space separated list of clients if successfull, null otherwise.
     */
    public String getP2pClientList(int netId) {
        return mSupplicantP2pIfaceHal.getClientList(netId);
    }

    /**
     * Set the client list for the provided network.
     *
     * @param netId Id of the network.
     * @param list Space separated list of clients.
     * @return true, if operation was successful.
     */
    public boolean setP2pClientList(int netId, String list) {
        return mSupplicantP2pIfaceHal.setClientList(netId, list);
    }

    /**
     * Save the current configuration to p2p_supplicant.conf.
     *
     * @return true on success, false otherwise.
     */
    public boolean saveConfig() {
        return mSupplicantP2pIfaceHal.saveConfig();
    }

    /**
     * Enable/Disable MAC randomization.
     *
     * @param enable true to enable, false to disable.
     * @return true, if operation was successful.
     */
    public boolean setMacRandomization(boolean enable) {
        return mSupplicantP2pIfaceHal.setMacRandomization(enable);
    }

    /**
     * Set Wifi Display R2 device info.
     *
     * @param hex WFD device info as described in section 5.1.12 of WFD technical
     *        specification v2.1.0.
     * @return true, if operation was successful.
     */
    public boolean setWfdR2DeviceInfo(String hex) {
        return mSupplicantP2pIfaceHal.setWfdR2DeviceInfo(hex);
    }

    /**
     * Remove the client with the MAC address from the group.
     *
     * @param peerAddress Mac address of the client.
     * @return true if success
     */
    public boolean removeClient(String peerAddress) {
        // The client is deemed as a P2P client, not a legacy client, hence the false.
        return mSupplicantP2pIfaceHal.removeClient(peerAddress, false);
    }

    /**
     * Set vendor-specific information elements to the native service.
     *
     * @param vendorElements the vendor opaque data.
     * @return true, if operation was successful.
     */
    public boolean setVendorElements(Set<ScanResult.InformationElement> vendorElements) {
        return mSupplicantP2pIfaceHal.setVendorElements(vendorElements);
    }

    /**
     * Remove vendor-specific information elements from the native service.
     */
    public boolean removeVendorElements() {
        return mSupplicantP2pIfaceHal.setVendorElements(
                new HashSet<ScanResult.InformationElement>());
    }

    /** Indicate whether or not 5GHz/6GHz DBS is supported. */
    public boolean is5g6gDbsSupported() {
        synchronized (mLock) {
            if (mP2pIfaceName == null) return false;
            if (!mHalDeviceManager.isSupported()) return false;
            return mHalDeviceManager.is5g6gDbsSupportedOnP2pIface(mP2pIfaceName);
        }
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
        return mSupplicantP2pIfaceHal.configureEapolIpAddressAllocationParams(ipAddressGo,
                ipAddressMask, ipAddressStart, ipAddressEnd);
    }
}
