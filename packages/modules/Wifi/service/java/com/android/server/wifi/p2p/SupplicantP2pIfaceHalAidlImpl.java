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
import android.hardware.wifi.supplicant.DebugLevel;
import android.hardware.wifi.supplicant.FreqRange;
import android.hardware.wifi.supplicant.ISupplicant;
import android.hardware.wifi.supplicant.ISupplicantP2pIface;
import android.hardware.wifi.supplicant.ISupplicantP2pIfaceCallback;
import android.hardware.wifi.supplicant.ISupplicantP2pNetwork;
import android.hardware.wifi.supplicant.IfaceInfo;
import android.hardware.wifi.supplicant.IfaceType;
import android.hardware.wifi.supplicant.MiracastMode;
import android.hardware.wifi.supplicant.P2pConnectInfo;
import android.hardware.wifi.supplicant.P2pDiscoveryInfo;
import android.hardware.wifi.supplicant.P2pExtListenInfo;
import android.hardware.wifi.supplicant.P2pFrameTypeMask;
import android.hardware.wifi.supplicant.P2pScanType;
import android.hardware.wifi.supplicant.WpsConfigMethods;
import android.hardware.wifi.supplicant.WpsProvisionMethod;
import android.net.wifi.CoexUnsafeChannel;
import android.net.wifi.ScanResult;
import android.net.wifi.WpsInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDiscoveryConfig;
import android.net.wifi.p2p.WifiP2pExtListenParams;
import android.net.wifi.p2p.WifiP2pGroup;
import android.net.wifi.p2p.WifiP2pGroupList;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.nsd.WifiP2pServiceInfo;
import android.os.IBinder;
import android.os.IBinder.DeathRecipient;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.ServiceSpecificException;
import android.text.TextUtils;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;
import com.android.modules.utils.build.SdkLevel;
import com.android.server.wifi.WifiInjector;
import com.android.server.wifi.WifiNative;
import com.android.server.wifi.WifiSettingsConfigStore;
import com.android.server.wifi.util.ArrayUtils;
import com.android.server.wifi.util.HalAidlUtil;
import com.android.server.wifi.util.NativeUtil;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Native calls sending requests to the P2P Hals, and callbacks for receiving P2P events
 */
public class SupplicantP2pIfaceHalAidlImpl implements ISupplicantP2pIfaceHal {
    private static final String TAG = "SupplicantP2pIfaceHalAidlImpl";
    @VisibleForTesting
    private static final String HAL_INSTANCE_NAME = ISupplicant.DESCRIPTOR + "/default";
    private static boolean sVerboseLoggingEnabled = true;
    private static boolean sHalVerboseLoggingEnabled = true;
    private boolean mInitializationStarted = false;
    private static final int RESULT_NOT_VALID = -1;
    private static final int DEFAULT_OPERATING_CLASS = 81;
    public static final long WAIT_FOR_DEATH_TIMEOUT_MS = 50L;
    /**
     * Regex pattern for extracting the wps device type bytes.
     * Matches a strings like the following: "<categ>-<OUI>-<subcateg>";
     */
    private static final Pattern WPS_DEVICE_TYPE_PATTERN =
            Pattern.compile("^(\\d{1,2})-([0-9a-fA-F]{8})-(\\d{1,2})$");

    private final Object mLock = new Object();
    private CountDownLatch mWaitForDeathLatch;
    private WifiNative.SupplicantDeathEventHandler mDeathEventHandler;

    // Supplicant HAL AIDL interface objects
    private ISupplicant mISupplicant = null;
    private ISupplicantP2pIface mISupplicantP2pIface = null;
    private final DeathRecipient mSupplicantDeathRecipient =
            () -> {
                Log.d(TAG, "ISupplicant/ISupplicantP2pIface died");
                synchronized (mLock) {
                    if (mWaitForDeathLatch != null) {
                        mWaitForDeathLatch.countDown();
                    }
                    supplicantServiceDiedHandler();
                }
            };
    private final WifiP2pMonitor mMonitor;
    private final WifiInjector mWifiInjector;
    private ISupplicantP2pIfaceCallback mCallback = null;
    private int mServiceVersion = -1;

    public SupplicantP2pIfaceHalAidlImpl(WifiP2pMonitor monitor, WifiInjector wifiInjector) {
        mMonitor = monitor;
        mWifiInjector = wifiInjector;
    }

    /**
     * Enable verbose logging for all sub modules.
     *
     */
    public static void enableVerboseLogging(boolean verboseEnabled, boolean halVerboseEnabled) {
        sVerboseLoggingEnabled = verboseEnabled;
        sHalVerboseLoggingEnabled = halVerboseEnabled;
        SupplicantP2pIfaceCallbackAidlImpl.enableVerboseLogging(verboseEnabled, halVerboseEnabled);
    }

    /**
     * Set the debug log level for wpa_supplicant
     *
     * @param turnOnVerbose Whether to turn on verbose logging or not.
     * @param globalShowKeys Whether show keys is true in WifiGlobals.
     * @return true if request is sent successfully, false otherwise.
     */
    public boolean setLogLevel(boolean turnOnVerbose, boolean globalShowKeys) {
        synchronized (mLock) {
            int logLevel = turnOnVerbose
                    ? DebugLevel.DEBUG
                    : DebugLevel.INFO;
            return setDebugParams(logLevel, false,
                    turnOnVerbose && globalShowKeys);
        }
    }

    /** See ISupplicant.hal for documentation */
    private boolean setDebugParams(int level, boolean showTimestamp, boolean showKeys) {
        synchronized (mLock) {
            String methodStr = "setDebugParams";
            if (!checkSupplicantAndLogFailure(methodStr)) {
                return false;
            }
            try {
                mISupplicant.setDebugParams(level, showTimestamp, showKeys);
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    /**
     * Retrieve the ISupplicant service and link to service death.
     * @return true if successful, false otherwise
     */
    public boolean initialize() {
        synchronized (mLock) {
            final String methodStr = "initialize";
            if (mISupplicant != null) {
                Log.i(TAG, "Service is already initialized.");
                return true;
            }
            mInitializationStarted = true;
            mISupplicantP2pIface = null;
            mISupplicant = getSupplicantMockable();
            if (mISupplicant == null) {
                Log.e(TAG, "Unable to obtain ISupplicant binder.");
                return false;
            }
            Log.i(TAG, "Obtained ISupplicant binder.");

            try {
                IBinder serviceBinder = getServiceBinderMockable();
                if (serviceBinder == null) {
                    return false;
                }
                serviceBinder.linkToDeath(mSupplicantDeathRecipient, /* flags= */  0);
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
                return false;
            }
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
            if (mISupplicantP2pIface != null) {
                // P2P iface already exists
                return false;
            }
            ISupplicantP2pIface iface = addIface(ifaceName);
            if (iface == null) {
                Log.e(TAG, "Unable to add iface " + ifaceName);
                return false;
            }
            mISupplicantP2pIface = iface;

            if (mMonitor != null) {
                ISupplicantP2pIfaceCallback callback =
                        new SupplicantP2pIfaceCallbackAidlImpl(ifaceName, mMonitor,
                                getCachedServiceVersion());
                if (!registerCallback(callback)) {
                    Log.e(TAG, "Unable to register callback for iface " + ifaceName);
                    return false;
                }
                mCallback = callback;
            }
            return true;
        }
    }

    private ISupplicantP2pIface addIface(@NonNull String ifaceName) {
        synchronized (mLock) {
            String methodStr = "addIface";
            if (!checkSupplicantAndLogFailure(methodStr)) {
                return null;
            }
            try {
                return mISupplicant.addP2pInterface(ifaceName);
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return null;
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
            final String methodStr = "teardownIface";
            if (!checkSupplicantAndLogFailure(methodStr)) {
                return false;
            } else if (!checkP2pIfaceAndLogFailure(methodStr)) {
                return false;
            }

            try {
                IfaceInfo ifaceInfo = new IfaceInfo();
                ifaceInfo.name = ifaceName;
                ifaceInfo.type = IfaceType.P2P;
                mISupplicant.removeInterface(ifaceInfo);
                mISupplicantP2pIface = null;
                mCallback = null;
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    private void supplicantServiceDiedHandler() {
        synchronized (mLock) {
            mISupplicant = null;
            mISupplicantP2pIface = null;
            mInitializationStarted = false;
            if (mDeathEventHandler != null) {
                mDeathEventHandler.onDeath();
            }
        }
    }

    /**
     * Signals whether initialization started successfully.
     */
    public boolean isInitializationStarted() {
        synchronized (mLock) {
            return mInitializationStarted;
        }
    }

    /**
     * Signals whether initialization completed successfully.
     */
    public boolean isInitializationComplete() {
        synchronized (mLock) {
            return mISupplicant != null;
        }
    }

    /**
     * Indicates whether the AIDL service is declared
     */
    public static boolean serviceDeclared() {
        // Service Manager API ServiceManager#isDeclared supported after T.
        if (!SdkLevel.isAtLeastT()) {
            return false;
        }
        return ServiceManager.isDeclared(HAL_INSTANCE_NAME);
    }

    /**
     * Wrapper functions to access static HAL methods, created to be mockable in unit tests
     */
    @VisibleForTesting
    protected ISupplicant getSupplicantMockable() {
        synchronized (mLock) {
            try {
                if (SdkLevel.isAtLeastT()) {
                    return ISupplicant.Stub.asInterface(
                            ServiceManager.waitForDeclaredService(HAL_INSTANCE_NAME));
                } else {
                    return null;
                }
            } catch (Exception e) {
                Log.e(TAG, "Unable to get ISupplicant service, " + e);
                return null;
            }
        }
    }

    @VisibleForTesting
    protected IBinder getServiceBinderMockable() {
        synchronized (mLock) {
            if (mISupplicant == null) {
                return null;
            }
            return mISupplicant.asBinder();
        }
    }

    /**
     * Returns false if mISupplicant is null and logs failure message
     */
    private boolean checkSupplicantAndLogFailure(String methodStr) {
        synchronized (mLock) {
            if (mISupplicant == null) {
                Log.e(TAG, "Can't call " + methodStr + ", ISupplicant is null");
                return false;
            }
            return true;
        }
    }

    /**
     * Returns false if SupplicantP2pIface is null, and logs failure to call methodStr
     */
    private boolean checkP2pIfaceAndLogFailure(String methodStr) {
        synchronized (mLock) {
            if (mISupplicantP2pIface == null) {
                Log.e(TAG, "Can't call " + methodStr + ", ISupplicantP2pIface is null");
                return false;
            }
            return true;
        }
    }

    private void handleRemoteException(RemoteException e, String methodStr) {
        synchronized (mLock) {
            supplicantServiceDiedHandler();
            Log.e(TAG,
                    "ISupplicantP2pIface." + methodStr + " failed with remote exception: ", e);
        }
    }

    private void handleServiceSpecificException(ServiceSpecificException e, String methodStr) {
        synchronized (mLock) {
            Log.e(TAG, "ISupplicantP2pIface." + methodStr + " failed with "
                    + "service specific exception: ", e);
        }
    }

    private int wpsInfoToConfigMethod(int info) {
        switch (info) {
            case WpsInfo.PBC:
                return WpsProvisionMethod.PBC;
            case WpsInfo.DISPLAY:
                return WpsProvisionMethod.DISPLAY;
            case WpsInfo.KEYPAD:
            case WpsInfo.LABEL:
                return WpsProvisionMethod.KEYPAD;
            default:
                Log.e(TAG, "Unsupported WPS provision method: " + info);
                return RESULT_NOT_VALID;
        }
    }

    /**
     * Retrieves the name of the network interface.
     *
     * @return name Name of the network interface, e.g., wlan0
     */
    public String getName() {
        synchronized (mLock) {
            String methodStr = "getName";
            if (!checkP2pIfaceAndLogFailure(methodStr)) {
                return null;
            }
            try {
                return mISupplicantP2pIface.getName();
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return null;
        }
    }

    /**
     * Register for callbacks from this interface.
     *
     * These callbacks are invoked for events that are specific to this interface.
     * Registration of multiple callback objects is supported. These objects must
     * be automatically deleted when the corresponding client process is dead or
     * if this interface is removed.
     *
     * @param callback An instance of the |ISupplicantP2pIfaceCallback| AIDL
     *        interface object.
     * @return boolean value indicating whether operation was successful.
     */
    public boolean registerCallback(ISupplicantP2pIfaceCallback callback) {
        synchronized (mLock) {
            String methodStr = "registerCallback";
            if (!checkP2pIfaceAndLogFailure(methodStr)) {
                return false;
            }
            try {
                mISupplicantP2pIface.registerCallback(callback);
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
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
        return find(
                WifiP2pManager.WIFI_P2P_SCAN_FULL,
                WifiP2pManager.WIFI_P2P_SCAN_FREQ_UNSPECIFIED, timeout);
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
            if (!checkP2pIfaceAndLogFailure(methodStr)) {
                return false;
            }
            if (timeout < 0) {
                Log.e(TAG, "Invalid timeout value: " + timeout);
                return false;
            }
            if (freq < 0) {
                Log.e(TAG, "Invalid freq value: " + freq);
                return false;
            }
            if (freq != WifiP2pManager.WIFI_P2P_SCAN_FREQ_UNSPECIFIED
                    && type != WifiP2pManager.WIFI_P2P_SCAN_SINGLE_FREQ) {
                Log.e(TAG, "Specified freq for scan type:" + type);
                return false;
            }
            try {
                switch (type) {
                    case WifiP2pManager.WIFI_P2P_SCAN_FULL:
                        mISupplicantP2pIface.find(timeout);
                        break;
                    case WifiP2pManager.WIFI_P2P_SCAN_SOCIAL:
                        mISupplicantP2pIface.findOnSocialChannels(timeout);
                        break;
                    case WifiP2pManager.WIFI_P2P_SCAN_SINGLE_FREQ:
                        if (freq == WifiP2pManager.WIFI_P2P_SCAN_FREQ_UNSPECIFIED) {
                            Log.e(TAG, "Unspecified freq for WIFI_P2P_SCAN_SINGLE_FREQ");
                            return false;
                        }
                        mISupplicantP2pIface.findOnSpecificFrequency(freq, timeout);
                        break;
                    default:
                        Log.e(TAG, "Invalid scan type: " + type);
                        return false;
                }
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    private static int frameworkToHalScanType(@WifiP2pManager.WifiP2pScanType int scanType) {
        switch (scanType) {
            case WifiP2pManager.WIFI_P2P_SCAN_FULL:
                return P2pScanType.FULL;
            case WifiP2pManager.WIFI_P2P_SCAN_SOCIAL:
                return P2pScanType.SOCIAL;
            case WifiP2pManager.WIFI_P2P_SCAN_SINGLE_FREQ:
                return P2pScanType.SPECIFIC_FREQ;
            default:
                Log.e(TAG, "Invalid discovery scan type: " + scanType);
                return -1;
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
            if (!checkP2pIfaceAndLogFailure(methodStr)) {
                return false;
            }
            if (getCachedServiceVersion() < 3) {
                // HAL does not support findWithParams before V3.
                return find(config.getScanType(), config.getFrequencyMhz(), timeout);
            }

            P2pDiscoveryInfo halInfo = new P2pDiscoveryInfo();
            halInfo.scanType = frameworkToHalScanType(config.getScanType());
            halInfo.timeoutInSec = timeout;
            halInfo.frequencyMhz = config.getFrequencyMhz();
            if (halInfo.scanType == -1) {
                return false;
            }
            if (halInfo.frequencyMhz < 0) {
                Log.e(TAG, "Invalid freq value: " +  halInfo.frequencyMhz);
                return false;
            }

            if (!config.getVendorData().isEmpty()) {
                halInfo.vendorData =
                        HalAidlUtil.frameworkToHalOuiKeyedDataList(config.getVendorData());
            }

            try {
                mISupplicantP2pIface.findWithParams(halInfo);
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
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
            if (!checkP2pIfaceAndLogFailure(methodStr)) {
                return false;
            }
            try {
                mISupplicantP2pIface.stopFind();
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
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
            if (!checkP2pIfaceAndLogFailure(methodStr)) {
                return false;
            }
            try {
                mISupplicantP2pIface.flush();
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
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
            if (!checkP2pIfaceAndLogFailure(methodStr)) {
                return false;
            }
            try {
                mISupplicantP2pIface.flushServices();
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
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
            if (!checkP2pIfaceAndLogFailure(methodStr)) {
                return false;
            }
            try {
                mISupplicantP2pIface.setPowerSave(groupIfName, enable);
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
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
            if (!checkP2pIfaceAndLogFailure(methodStr)) {
                return false;
            }
            // Basic checking here. Leave actual parameter validation to supplicant.
            if (timeoutInSec < 0) {
                Log.e(TAG, "Invalid group timeout value " + timeoutInSec);
                return false;
            }
            if (groupIfName == null) {
                Log.e(TAG, "Group interface name cannot be null.");
                return false;
            }

            try {
                mISupplicantP2pIface.setGroupIdle(groupIfName, timeoutInSec);
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
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
            if (!checkP2pIfaceAndLogFailure(methodStr)) {
                return false;
            }
            // Basic checking here. Leave actual parameter validation to supplicant.
            if (postfix == null) {
                Log.e(TAG, "Invalid SSID postfix value (null).");
                return false;
            }

            try {
                mISupplicantP2pIface.setSsidPostfix(
                        NativeUtil.byteArrayFromArrayList(
                                NativeUtil.decodeSsid("\"" + postfix + "\"")));
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Could not decode SSID.", e);
            }
            return false;
        }
    }

    private String connectWithParams(boolean joinExistingGroup, byte[] peerAddress,
            int provisionMethod, String preSelectedPin, boolean persistent, int groupOwnerIntent,
            WifiP2pConfig config) {
        synchronized (mLock) {
            String methodStr = "connectWithParams";

            // Parameters should be pre-validated.
            P2pConnectInfo info = new P2pConnectInfo();
            info.joinExistingGroup = joinExistingGroup;
            info.peerAddress = peerAddress;
            info.provisionMethod = provisionMethod;
            info.preSelectedPin = preSelectedPin;
            info.persistent = persistent;
            info.goIntent = groupOwnerIntent;

            if (SdkLevel.isAtLeastV() && config.getVendorData() != null
                    && !config.getVendorData().isEmpty()) {
                info.vendorData =
                        HalAidlUtil.frameworkToHalOuiKeyedDataList(config.getVendorData());
            }

            try {
                return mISupplicantP2pIface.connectWithParams(info);
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return null;
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

            if (!checkP2pIfaceAndLogFailure(methodStr)) {
                return null;
            }
            if (config == null) {
                Log.e(TAG, "Could not connect because config is null.");
                return null;
            }
            if (config.deviceAddress == null) {
                Log.e(TAG, "Could not parse null mac address.");
                return null;
            }
            if (config.wps.setup == WpsInfo.PBC && !TextUtils.isEmpty(config.wps.pin)) {
                Log.e(TAG, "Expected empty pin for PBC.");
                return null;
            }

            byte[] peerAddress = null;
            try {
                peerAddress = NativeUtil.macAddressToByteArray(config.deviceAddress);
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Could not parse peer mac address.", e);
                return null;
            }

            int provisionMethod = wpsInfoToConfigMethod(config.wps.setup);
            if (provisionMethod == RESULT_NOT_VALID) {
                Log.e(TAG, "Invalid WPS config method: " + config.wps.setup);
                return null;
            }
            // NOTE: preSelectedPin cannot be null, otherwise hal would crash.
            String preSelectedPin = TextUtils.isEmpty(config.wps.pin) ? "" : config.wps.pin;
            boolean persistent = (config.netId == WifiP2pGroup.NETWORK_ID_PERSISTENT);

            if (config.groupOwnerIntent < 0 || config.groupOwnerIntent > 15) {
                Log.e(TAG, "Invalid group owner intent: " + config.groupOwnerIntent);
                return null;
            }

            if (getCachedServiceVersion() >= 3) {
                return connectWithParams(joinExistingGroup, peerAddress, provisionMethod,
                        preSelectedPin, persistent, config.groupOwnerIntent, config);
            }

            try {
                return mISupplicantP2pIface.connect(
                        peerAddress, provisionMethod, preSelectedPin, joinExistingGroup,
                        persistent, config.groupOwnerIntent);
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return null;
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
            if (!checkP2pIfaceAndLogFailure(methodStr)) {
                return false;
            }
            try {
                mISupplicantP2pIface.cancelConnect();
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
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
            if (!checkP2pIfaceAndLogFailure("provisionDiscovery")) {
                return false;
            }
            if (config == null) {
                return false;
            }

            int targetMethod = wpsInfoToConfigMethod(config.wps.setup);
            if (targetMethod == RESULT_NOT_VALID) {
                Log.e(TAG, "Unrecognized WPS configuration method: " + config.wps.setup);
                return false;
            }
            if (targetMethod == WpsProvisionMethod.DISPLAY) {
                // We are doing display, so provision discovery is keypad.
                targetMethod = WpsProvisionMethod.KEYPAD;
            } else if (targetMethod == WpsProvisionMethod.KEYPAD) {
                // We are doing keypad, so provision discovery is display.
                targetMethod = WpsProvisionMethod.DISPLAY;
            }

            if (config.deviceAddress == null) {
                Log.e(TAG, "Cannot parse null mac address.");
                return false;
            }
            byte[] macAddress = null;
            try {
                macAddress = NativeUtil.macAddressToByteArray(config.deviceAddress);
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Could not parse peer mac address.", e);
                return false;
            }

            try {
                mISupplicantP2pIface.provisionDiscovery(macAddress, targetMethod);
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
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
            if (!checkP2pIfaceAndLogFailure(methodStr)) {
                return false;
            }
            if (TextUtils.isEmpty(peerAddress)) {
                Log.e(TAG, "Peer mac address is empty.");
                return false;
            }
            if (group == null) {
                Log.e(TAG, "Cannot invite to null group.");
                return false;
            }
            if (group.getOwner() == null) {
                Log.e(TAG, "Cannot invite to group with null owner.");
                return false;
            }
            if (group.getOwner().deviceAddress == null) {
                Log.e(TAG, "Group owner has no mac address.");
                return false;
            }

            byte[] ownerMacAddress = null;
            try {
                ownerMacAddress = NativeUtil.macAddressToByteArray(group.getOwner().deviceAddress);
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Group owner mac address parse error.", e);
                return false;
            }

            byte[] peerMacAddress;
            try {
                peerMacAddress = NativeUtil.macAddressToByteArray(peerAddress);
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Peer mac address parse error.", e);
                return false;
            }

            try {
                mISupplicantP2pIface.invite(
                        group.getInterface(), ownerMacAddress, peerMacAddress);
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
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
            if (!checkP2pIfaceAndLogFailure(methodStr)) {
                return false;
            }

            if (peerAddress == null) {
                Log.e(TAG, "Rejected peer's mac address is null.");
                return false;
            }
            byte[] macAddress = null;
            try {
                macAddress = NativeUtil.macAddressToByteArray(peerAddress);
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Could not parse peer mac address.", e);
                return false;
            }

            try {
                mISupplicantP2pIface.reject(macAddress);
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
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
            if (!checkP2pIfaceAndLogFailure(methodStr)) {
                return null;
            }
            try {
                byte[] address = mISupplicantP2pIface.getDeviceAddress();
                return NativeUtil.macAddressFromByteArray(address);
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Received invalid MAC address", e);
            }
            return null;
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
            if (!checkP2pIfaceAndLogFailure(methodStr)) {
                return null;
            }

            if (address == null) {
                Log.e(TAG, "Cannot parse null peer mac address.");
                return null;
            }
            byte[] macAddress = null;
            try {
                macAddress = NativeUtil.macAddressToByteArray(address);
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Could not parse mac address.", e);
                return null;
            }

            try {
                byte[] ssid = mISupplicantP2pIface.getSsid(macAddress);
                if (ssid == null) {
                    return null;
                }
                return NativeUtil.removeEnclosingQuotes(
                        NativeUtil.encodeSsid(
                                NativeUtil.byteArrayToArrayList(ssid)));
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Unable to parse SSID: ", e);
            }
            return null;
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
            if (!checkP2pIfaceAndLogFailure(methodStr)) {
                return false;
            }
            if (TextUtils.isEmpty(peerAddress) || networkId < 0) {
                return false;
            }
            byte[] macAddress = null;
            try {
                macAddress = NativeUtil.macAddressToByteArray(peerAddress);
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Could not parse mac address.", e);
                return false;
            }

            try {
                mISupplicantP2pIface.reinvoke(networkId, macAddress);
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
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
            if (!checkP2pIfaceAndLogFailure(methodStr)) {
                return false;
            }
            try {
                mISupplicantP2pIface.addGroup(isPersistent, networkId);
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

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
    public boolean groupAdd(String networkName, String passphrase,
            boolean isPersistent, int freq, String peerAddress, boolean join) {
        synchronized (mLock) {
            String methodStr = "groupAdd";
            if (!checkP2pIfaceAndLogFailure(methodStr)) {
                return false;
            }

            byte[] macAddress = null;
            try {
                macAddress = NativeUtil.macAddressToByteArray(peerAddress);
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Could not parse mac address.", e);
                return false;
            }

            byte[] ssid = null;
            try {
                ssid = NativeUtil.byteArrayFromArrayList(
                        NativeUtil.decodeSsid("\"" + networkName + "\""));
            } catch (Exception e) {
                Log.e(TAG, "Could not parse ssid.", e);
                return false;
            }

            try {
                mISupplicantP2pIface.addGroupWithConfig(
                        ssid, passphrase, isPersistent, freq, macAddress, join);
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
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
            if (!checkP2pIfaceAndLogFailure(methodStr)) {
                return false;
            }
            if (TextUtils.isEmpty(groupName)) {
                return false;
            }
            try {
                mISupplicantP2pIface.removeGroup(groupName);
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
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
            if (!checkP2pIfaceAndLogFailure(methodStr)) {
                return RESULT_NOT_VALID;
            }
            if (peerAddress == null) {
                Log.e(TAG, "Cannot parse null peer mac address.");
                return RESULT_NOT_VALID;
            }

            byte[] macAddress = null;
            try {
                macAddress = NativeUtil.macAddressToByteArray(peerAddress);
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Could not parse mac address.", e);
                return RESULT_NOT_VALID;
            }

            try {
                return mISupplicantP2pIface.getGroupCapability(macAddress);
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return RESULT_NOT_VALID;
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
            if (!checkP2pIfaceAndLogFailure(methodStr)) {
                return false;
            }
            if (enable && intervalInMillis < periodInMillis) {
                return false;
            }

            // If listening is disabled, wpa supplicant expects zeroes.
            if (!enable) {
                periodInMillis = 0;
                intervalInMillis = 0;
            }

            // Verify that the integers are not negative. Leave actual parameter validation to
            // supplicant.
            if (periodInMillis < 0 || intervalInMillis < 0) {
                Log.e(TAG, "Invalid parameters supplied to configureExtListen: " + periodInMillis
                        + ", " + intervalInMillis);
                return false;
            }

            if (getCachedServiceVersion() >= 3) {
                return configureExtListenWithParams(
                        periodInMillis, intervalInMillis, extListenParams);
            }

            try {
                mISupplicantP2pIface.configureExtListen(periodInMillis, intervalInMillis);
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    private boolean configureExtListenWithParams(int periodInMillis, int intervalInMillis,
            @Nullable WifiP2pExtListenParams extListenParams) {
        String methodStr = "configureExtListenWithParams";

        // Expect that these parameters are already validated.
        P2pExtListenInfo extListenInfo = new P2pExtListenInfo();
        extListenInfo.periodMs = periodInMillis;
        extListenInfo.intervalMs = intervalInMillis;

        if (SdkLevel.isAtLeastV() && extListenParams != null
                && extListenParams.getVendorData() != null) {
            extListenInfo.vendorData =
                    HalAidlUtil.frameworkToHalOuiKeyedDataList(extListenParams.getVendorData());
        }

        try {
            mISupplicantP2pIface.configureExtListenWithParams(extListenInfo);
            return true;
        } catch (RemoteException e) {
            handleRemoteException(e, methodStr);
        } catch (ServiceSpecificException e) {
            handleServiceSpecificException(e, methodStr);
        }
        return false;
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
            if (!checkP2pIfaceAndLogFailure(methodStr)) {
                return false;
            }

            // There is no original channel recorded in supplicant, so just return true.
            if (listenChannel == 0) {
                return true;
            }

            // Using channels other than 1, 6, and 11 would result in a discovery issue.
            if (listenChannel != 1 && listenChannel != 6 && listenChannel != 11) {
                return false;
            }

            try {
                mISupplicantP2pIface.setListenChannel(listenChannel, DEFAULT_OPERATING_CLASS);
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
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
            if (!checkP2pIfaceAndLogFailure(methodStr)) {
                return false;
            }
            if (unsafeChannels == null) {
                return false;
            }

            ArrayList<FreqRange> ranges = new ArrayList<>();
            if (operatingChannel >= 1 && operatingChannel <= 165) {
                int freq = (operatingChannel <= 14 ? 2407 : 5000) + operatingChannel * 5;
                FreqRange range1 =  new FreqRange();
                range1.min = 1000;
                range1.max = freq - 5;
                FreqRange range2 =  new FreqRange();
                range2.min = freq + 5;
                range2.max = 6000;
                ranges.add(range1);
                ranges.add(range2);
            }
            if (SdkLevel.isAtLeastS()) {
                for (CoexUnsafeChannel cuc: unsafeChannels) {
                    int centerFreq = ScanResult.convertChannelToFrequencyMhzIfSupported(
                            cuc.getChannel(), cuc.getBand());
                    FreqRange range = new FreqRange();
                    // The range boundaries are inclusive in native frequency inclusion check.
                    // Subtract one to avoid affecting neighbors.
                    range.min = centerFreq - 5 - 1;
                    range.max = centerFreq + 5 - 1;
                    ranges.add(range);
                }
            }

            FreqRange[] rangeArr = new FreqRange[ranges.size()];
            for (int i = 0; i < ranges.size(); i++) {
                rangeArr[i] = ranges.get(i);
            }

            try {
                mISupplicantP2pIface.setDisallowedFrequencies(rangeArr);
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
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
            if (!checkP2pIfaceAndLogFailure(methodStr)) {
                return false;
            }

            if (servInfo == null) {
                Log.e(TAG, "Null service info passed.");
                return false;
            }

            for (String s : servInfo.getSupplicantQueryList()) {
                if (s == null) {
                    Log.e(TAG, "Invalid service description (null).");
                    return false;
                }

                String[] data = s.split(" ");
                if (data.length < 3) {
                    Log.e(TAG, "Service specification invalid: " + s);
                    return false;
                }

                try {
                    if ("upnp".equals(data[0])) {
                        int version = 0;
                        try {
                            version = Integer.parseInt(data[1], 16);
                        } catch (NumberFormatException e) {
                            Log.e(TAG, "UPnP Service specification invalid: " + s, e);
                            return false;
                        }
                        mISupplicantP2pIface.addUpnpService(version, data[2]);
                    } else if ("bonjour".equals(data[0])) {
                        if (data[1] != null && data[2] != null) {
                            byte[] request = null;
                            byte[] response = null;
                            try {
                                request = NativeUtil.hexStringToByteArray(data[1]);
                                response = NativeUtil.hexStringToByteArray(data[2]);
                            } catch (IllegalArgumentException e) {
                                Log.e(TAG, "Invalid bonjour service description.");
                                return false;
                            }
                            mISupplicantP2pIface.addBonjourService(request, response);
                        }
                    } else {
                        Log.e(TAG, "Unknown / unsupported P2P service requested: " + data[0]);
                        return false;
                    }
                } catch (RemoteException e) {
                    handleRemoteException(e, methodStr);
                    return false;
                } catch (ServiceSpecificException e) {
                    handleServiceSpecificException(e, methodStr);
                    return false;
                }
            }

            return true;
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
            if (!checkP2pIfaceAndLogFailure(methodStr)) {
                return false;
            }

            if (servInfo == null) {
                Log.e(TAG, "Null service info passed.");
                return false;
            }

            for (String s : servInfo.getSupplicantQueryList()) {
                if (s == null) {
                    Log.e(TAG, "Invalid service description (null).");
                    return false;
                }

                String[] data = s.split(" ");
                if (data.length < 3) {
                    Log.e(TAG, "Service specification invalid: " + s);
                    return false;
                }

                try {
                    if ("upnp".equals(data[0])) {
                        int version = 0;
                        try {
                            version = Integer.parseInt(data[1], 16);
                        } catch (NumberFormatException e) {
                            Log.e(TAG, "UPnP Service specification invalid: " + s, e);
                            return false;
                        }
                        mISupplicantP2pIface.removeUpnpService(version, data[2]);
                    } else if ("bonjour".equals(data[0])) {
                        if (data[1] != null) {
                            byte[] request = null;
                            try {
                                request = NativeUtil.hexStringToByteArray(data[1]);
                            } catch (IllegalArgumentException e) {
                                Log.e(TAG, "Invalid bonjour service description.");
                                return false;
                            }
                            mISupplicantP2pIface.removeBonjourService(request);
                        }
                    } else {
                        Log.e(TAG, "Unknown / unsupported P2P service requested: " + data[0]);
                        return false;
                    }
                } catch (RemoteException e) {
                    handleRemoteException(e, methodStr);
                    return false;
                } catch (ServiceSpecificException e) {
                    handleServiceSpecificException(e, methodStr);
                    return false;
                }
            }

            return true;
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
            if (!checkP2pIfaceAndLogFailure(methodStr)) {
                return null;
            }

            if (peerAddress == null) {
                Log.e(TAG, "Cannot parse null peer mac address.");
                return null;
            }

            byte[] macAddress = null;
            try {
                macAddress = NativeUtil.macAddressToByteArray(peerAddress);
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Could not process peer MAC address.", e);
                return null;
            }

            if (query == null) {
                Log.e(TAG, "Cannot parse null service discovery query.");
                return null;
            }
            byte[] binQuery = null;
            try {
                binQuery = NativeUtil.hexStringToByteArray(query);
            } catch (Exception e) {
                Log.e(TAG, "Could not parse service query.", e);
                return null;
            }

            try {
                long result = mISupplicantP2pIface.requestServiceDiscovery(macAddress, binQuery);
                return Long.toString(result);
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return null;
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
            if (!checkP2pIfaceAndLogFailure(methodStr)) {
                return false;
            }
            if (identifier == null) {
                Log.e(TAG, "Received a null service discovery identifier.");
                return false;
            }

            long id = 0;
            try {
                id = Long.parseLong(identifier);
            } catch (NumberFormatException e) {
                Log.e(TAG, "Service discovery identifier invalid: " + identifier, e);
                return false;
            }

            try {
                mISupplicantP2pIface.cancelServiceDiscovery(id);
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
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
            if (!checkP2pIfaceAndLogFailure(methodStr)) {
                return false;
            }

            byte targetMode = MiracastMode.DISABLED;
            switch (mode) {
                case WifiP2pManager.MIRACAST_SOURCE:
                    targetMode = MiracastMode.SOURCE;
                    break;
                case WifiP2pManager.MIRACAST_SINK:
                    targetMode = MiracastMode.SINK;
                    break;
            }

            try {
                mISupplicantP2pIface.setMiracastMode(targetMode);
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
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
            if (!checkP2pIfaceAndLogFailure(methodStr)) {
                return false;
            }
            if (TextUtils.isEmpty(groupIfName)) {
                Log.e(TAG, "Group name required when requesting WPS PBC. Got empty string.");
                return false;
            }

            // Null values should be fine, since bssid can be empty.
            byte[] macAddress = null;
            try {
                macAddress = NativeUtil.macAddressToByteArray(bssid);
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Could not parse BSSID.", e);
                return false;
            }

            try {
                mISupplicantP2pIface.startWpsPbc(groupIfName, macAddress);
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
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
            if (!checkP2pIfaceAndLogFailure(methodStr)) {
                return false;
            }
            if (TextUtils.isEmpty(groupIfName)) {
                Log.e(TAG, "Group name required when requesting WPS KEYPAD.");
                return false;
            }
            if (TextUtils.isEmpty(pin)) {
                Log.e(TAG, "PIN required when requesting WPS KEYPAD.");
                return false;
            }

            try {
                mISupplicantP2pIface.startWpsPinKeypad(groupIfName, pin);
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
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
            if (!checkP2pIfaceAndLogFailure(methodStr)) {
                return null;
            }
            if (TextUtils.isEmpty(groupIfName)) {
                Log.e(TAG, "Group name required when requesting WPS KEYPAD.");
                return null;
            }

            // Null values should be fine, since bssid can be empty.
            byte[] macAddress = null;
            try {
                macAddress = NativeUtil.macAddressToByteArray(bssid);
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Could not parse BSSID.", e);
                return null;
            }

            try {
                return mISupplicantP2pIface.startWpsPinDisplay(groupIfName, macAddress);
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return null;
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
            if (!checkP2pIfaceAndLogFailure(methodStr)) {
                return false;
            }
            if (groupIfName == null) {
                Log.e(TAG, "Group name required when requesting WPS KEYPAD.");
                return false;
            }

            try {
                mISupplicantP2pIface.cancelWps(groupIfName);
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
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
            if (!checkP2pIfaceAndLogFailure(methodStr)) {
                return false;
            }
            try {
                mISupplicantP2pIface.enableWfd(enable);
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
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
            if (!checkP2pIfaceAndLogFailure(methodStr)) {
                return false;
            }
            if (info == null) {
                Log.e(TAG, "Cannot parse null WFD info string.");
                return false;
            }

            byte[] wfdInfo = null;
            try {
                wfdInfo = NativeUtil.hexStringToByteArray(info);
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Could not parse WFD Device Info string.");
                return false;
            }

            try {
                mISupplicantP2pIface.setWfdDeviceInfo(wfdInfo);
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
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
            if (!checkP2pIfaceAndLogFailure(methodStr)) {
                return false;
            }
            try {
                mISupplicantP2pIface.removeNetwork(networkId);
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    /**
     * List the networks saved in wpa_supplicant.
     *
     * @return List of network ids.
     */
    private int[] listNetworks() {
        synchronized (mLock) {
            String methodStr = "listNetworks";
            if (!checkP2pIfaceAndLogFailure(methodStr)) {
                return null;
            }
            try {
                return mISupplicantP2pIface.listNetworks();
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return null;
        }
    }

    /**
     * Get the supplicant P2p network object for the specified network ID.
     *
     * @param networkId Id of the network to lookup.
     * @return ISupplicantP2pNetwork instance on success, null on failure.
     */
    private ISupplicantP2pNetwork getNetwork(int networkId) {
        synchronized (mLock) {
            String methodStr = "getNetwork";
            if (!checkP2pIfaceAndLogFailure(methodStr)) {
                return null;
            }
            try {
                return mISupplicantP2pIface.getNetwork(networkId);
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return null;
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
            if (!checkP2pIfaceAndLogFailure(methodStr)) {
                return false;
            }
            int[] networkIds = listNetworks();
            if (networkIds == null || networkIds.length == 0) {
                return false;
            }
            for (int networkId : networkIds) {
                ISupplicantP2pNetwork network = getNetwork(networkId);
                if (network == null) {
                    Log.e(TAG, "Failed to retrieve network object for " + networkId);
                    continue;
                }

                boolean gotResult = false;
                boolean isCurrent = false;
                try {
                    isCurrent = network.isCurrent();
                    gotResult = true;
                } catch (RemoteException e) {
                    handleRemoteException(e, methodStr);
                } catch (ServiceSpecificException e) {
                    handleServiceSpecificException(e, methodStr);
                }

                /** Skip the current network, if we're somehow getting networks from the p2p GO
                 interface, instead of p2p mgmt interface*/
                if (!gotResult || isCurrent) {
                    Log.i(TAG, "Skipping current network");
                    continue;
                }

                WifiP2pGroup group = new WifiP2pGroup();
                group.setNetworkId(networkId);

                // Now get the ssid, bssid and other flags for this network.
                byte[] ssid = null;
                gotResult = false;
                try {
                    ssid = network.getSsid();
                    gotResult = true;
                } catch (RemoteException e) {
                    handleRemoteException(e, methodStr);
                } catch (ServiceSpecificException e) {
                    handleServiceSpecificException(e, methodStr);
                }
                if (gotResult && !ArrayUtils.isEmpty(ssid)) {
                    group.setNetworkName(NativeUtil.removeEnclosingQuotes(
                            NativeUtil.encodeSsid(
                                    NativeUtil.byteArrayToArrayList(ssid))));
                }

                byte[] bssid = null;
                gotResult = false;
                try {
                    bssid = network.getBssid();
                    gotResult = true;
                } catch (RemoteException e) {
                    handleRemoteException(e, methodStr);
                } catch (ServiceSpecificException e) {
                    handleServiceSpecificException(e, methodStr);
                }
                if (gotResult && !ArrayUtils.isEmpty(bssid)) {
                    WifiP2pDevice device = new WifiP2pDevice();
                    device.deviceAddress = NativeUtil.macAddressFromByteArray(bssid);
                    group.setOwner(device);
                }

                boolean isGroupOwner = false;
                gotResult = false;
                try {
                    isGroupOwner = network.isGroupOwner();
                    gotResult = true;
                } catch (RemoteException e) {
                    handleRemoteException(e, methodStr);
                } catch (ServiceSpecificException e) {
                    handleServiceSpecificException(e, methodStr);
                }
                if (gotResult) {
                    group.setIsGroupOwner(isGroupOwner);
                }
                groups.add(group);
            }
        }
        return true;
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
            if (!checkP2pIfaceAndLogFailure(methodStr)) {
                return false;
            }
            if (name == null) {
                return false;
            }
            try {
                mISupplicantP2pIface.setWpsDeviceName(name);
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    /**
     * Set WPS device type.
     *
     * @param typeStr Type specified as a string. Used format: <categ>-<OUI>-<subcateg>
     * @return true if request is sent successfully, false otherwise.
     */
    public boolean setWpsDeviceType(String typeStr) {
        try {
            Matcher match = WPS_DEVICE_TYPE_PATTERN.matcher(typeStr);
            if (!match.find() || match.groupCount() != 3) {
                Log.e(TAG, "Malformed WPS device type " + typeStr);
                return false;
            }
            short categ = Short.parseShort(match.group(1));
            byte[] oui = NativeUtil.hexStringToByteArray(match.group(2));
            short subCateg = Short.parseShort(match.group(3));

            byte[] bytes = new byte[8];
            ByteBuffer byteBuffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
            byteBuffer.putShort(categ);
            byteBuffer.put(oui);
            byteBuffer.putShort(subCateg);
            synchronized (mLock) {
                String methodStr = "setWpsDeviceType";
                if (!checkP2pIfaceAndLogFailure(methodStr)) {
                    return false;
                }
                try {
                    mISupplicantP2pIface.setWpsDeviceType(bytes);
                    return true;
                } catch (RemoteException e) {
                    handleRemoteException(e, methodStr);
                } catch (ServiceSpecificException e) {
                    handleServiceSpecificException(e, methodStr);
                }
            }
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Illegal argument " + typeStr, e);
        }
        return false;
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
            if (!checkP2pIfaceAndLogFailure(methodStr)) {
                return false;
            }

            short configMethodsMask = 0;
            String[] configMethodsStrArr = configMethodsStr.split("\\s+");
            for (int i = 0; i < configMethodsStrArr.length; i++) {
                configMethodsMask |= stringToWpsConfigMethod(configMethodsStrArr[i]);
            }

            try {
                mISupplicantP2pIface.setWpsConfigMethods(configMethodsMask);
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
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
            if (!checkP2pIfaceAndLogFailure(methodStr)) {
                return null;
            }
            try {
                byte[] message = mISupplicantP2pIface.createNfcHandoverRequestMessage();
                return NativeUtil.hexStringFromByteArray(message);
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Invalid message received ", e);
            }
            return null;
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
            if (!checkP2pIfaceAndLogFailure(methodStr)) {
                return null;
            }
            try {
                byte[] message = mISupplicantP2pIface.createNfcHandoverSelectMessage();
                return NativeUtil.hexStringFromByteArray(message);
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Invalid message received ", e);
            }
            return null;
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
            if (!checkP2pIfaceAndLogFailure(methodStr)) {
                return false;
            }
            if (selectMessage == null) {
                return false;
            }
            try {
                mISupplicantP2pIface.reportNfcHandoverInitiation(
                        NativeUtil.hexStringToByteArray(selectMessage));
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Illegal argument " + selectMessage, e);
            }
            return false;
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
            if (!checkP2pIfaceAndLogFailure(methodStr)) {
                return false;
            }
            if (requestMessage == null) {
                return false;
            }
            try {
                mISupplicantP2pIface.reportNfcHandoverResponse(
                        NativeUtil.hexStringToByteArray(requestMessage));
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Illegal argument " + requestMessage, e);
            }
            return false;
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
            if (!checkP2pIfaceAndLogFailure(methodStr)) {
                return false;
            }
            if (TextUtils.isEmpty(clientListStr)) {
                Log.e(TAG, "Invalid client list");
                return false;
            }
            ISupplicantP2pNetwork network = getNetwork(networkId);
            if (network == null) {
                Log.e(TAG, "Invalid network id ");
                return false;
            }

            try {
                String[] clientListArr = clientListStr.split("\\s+");
                android.hardware.wifi.supplicant.MacAddress[] clients =
                        new android.hardware.wifi.supplicant.MacAddress[clientListArr.length];
                for (int i = 0; i < clientListArr.length; i++) {
                    android.hardware.wifi.supplicant.MacAddress client =
                            new android.hardware.wifi.supplicant.MacAddress();
                    client.data = NativeUtil.macAddressToByteArray(clientListArr[i]);
                    clients[i] = client;
                }
                network.setClientList(clients);
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Illegal argument " + clientListStr, e);
            }
            return false;
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
            if (!checkP2pIfaceAndLogFailure(methodStr)) {
                return null;
            }
            ISupplicantP2pNetwork network = getNetwork(networkId);
            if (network == null) {
                Log.e(TAG, "Invalid network id ");
                return null;
            }
            try {
                android.hardware.wifi.supplicant.MacAddress[] clients = network.getClientList();
                String[] macStrings = new String[clients.length];
                for (int i = 0; i < clients.length; i++) {
                    try {
                        macStrings[i] = NativeUtil.macAddressFromByteArray(clients[i].data);
                    } catch (Exception e) {
                        Log.e(TAG, "Invalid MAC address received ", e);
                        return null;
                    }
                }
                return String.join(" ", macStrings);
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return null;

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
            if (!checkP2pIfaceAndLogFailure(methodStr)) {
                return false;
            }
            try {
                mISupplicantP2pIface.saveConfig();
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
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
            if (!checkP2pIfaceAndLogFailure(methodStr)) {
                return false;
            }
            try {
                mISupplicantP2pIface.setMacRandomization(enable);
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
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
            if (info == null) {
                Log.e(TAG, "Cannot parse null WFD info string.");
                return false;
            }

            byte[] wfdR2Info = null;
            try {
                wfdR2Info = NativeUtil.hexStringToByteArray(info);
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Could not parse WFD R2 Device Info string.");
                return false;
            }

            if (!checkP2pIfaceAndLogFailure(methodStr)) {
                return false;
            }
            try {
                mISupplicantP2pIface.setWfdR2DeviceInfo(wfdR2Info);
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
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

            if (peerAddress == null) {
                Log.e(TAG, "Cannot parse null peer mac address.");
                return false;
            }

            byte[] peerMacAddress;
            try {
                peerMacAddress = NativeUtil.macAddressToByteArray(peerAddress);
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Peer mac address parse error.", e);
                return false;
            }


            if (!checkP2pIfaceAndLogFailure(methodStr)) {
                return false;
            }
            try {
                mISupplicantP2pIface.removeClient(peerMacAddress, isLegacyClient);
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
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
            if (!checkP2pIfaceAndLogFailure(methodStr)) {
                return false;
            }
            if (vendorElements == null) {
                return false;
            }
            byte[] vendorElemBytes = convertInformationElementSetToBytes(
                    vendorElements);
            if (null == vendorElemBytes) {
                Log.w(TAG, "Cannot convert vendor elements to bytes.");
                return false;
            }
            try {
                mISupplicantP2pIface.setVendorElements(
                        P2pFrameTypeMask.P2P_FRAME_PROBE_RESP_P2P, vendorElemBytes);
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    private int getCachedServiceVersion() {
        if (mServiceVersion == -1) {
            mServiceVersion = mWifiInjector.getSettingsConfigStore().get(
                    WifiSettingsConfigStore.SUPPLICANT_HAL_AIDL_SERVICE_VERSION);
        }
        return mServiceVersion;
    }

    /**
     * Get the supported features.
     *
     * @return  bitmask defined by WifiP2pManager.FEATURE_*
     */
    public long getSupportedFeatures() {
        // First AIDL version supports these three features.
        long result = WifiP2pManager.FEATURE_SET_VENDOR_ELEMENTS
                | WifiP2pManager.FEATURE_FLEXIBLE_DISCOVERY
                | WifiP2pManager.FEATURE_GROUP_CLIENT_REMOVAL;
        if (getCachedServiceVersion() >= 2) {
            result |= WifiP2pManager.FEATURE_GROUP_OWNER_IPV6_LINK_LOCAL_ADDRESS_PROVIDED;
        }
        return result;
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
            if (!checkP2pIfaceAndLogFailure(methodStr)) {
                return false;
            }
            try {
                mISupplicantP2pIface.configureEapolIpAddressAllocationParams(ipAddressGo,
                        ipAddressMask, ipAddressStart, ipAddressEnd);
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    private byte[] convertInformationElementSetToBytes(
            Set<ScanResult.InformationElement> ies) {
        try {
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            for (ScanResult.InformationElement ie: ies) {
                os.write((byte) ie.id);
                os.write((byte) (ie.bytes.length));
                os.write(ie.bytes);
            }
            return os.toByteArray();
        } catch (IOException ex) {
            return null;
        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * Converts the Wps config method string to the equivalent enum value.
     */
    private static short stringToWpsConfigMethod(String configMethod) {
        switch (configMethod) {
            case "usba":
                return WpsConfigMethods.USBA;
            case "ethernet":
                return WpsConfigMethods.ETHERNET;
            case "label":
                return WpsConfigMethods.LABEL;
            case "display":
                return WpsConfigMethods.DISPLAY;
            case "int_nfc_token":
                return WpsConfigMethods.INT_NFC_TOKEN;
            case "ext_nfc_token":
                return WpsConfigMethods.EXT_NFC_TOKEN;
            case "nfc_interface":
                return WpsConfigMethods.NFC_INTERFACE;
            case "push_button":
                return WpsConfigMethods.PUSHBUTTON;
            case "keypad":
                return WpsConfigMethods.KEYPAD;
            case "virtual_push_button":
                return WpsConfigMethods.VIRT_PUSHBUTTON;
            case "physical_push_button":
                return WpsConfigMethods.PHY_PUSHBUTTON;
            case "p2ps":
                return WpsConfigMethods.P2PS;
            case "virtual_display":
                return WpsConfigMethods.VIRT_DISPLAY;
            case "physical_display":
                return WpsConfigMethods.PHY_DISPLAY;
            default:
                throw new IllegalArgumentException(
                        "Invalid WPS config method: " + configMethod);
        }
    }

    /**
     * Terminate the supplicant daemon & wait for its death.
     */
    public void terminate() {
        synchronized (mLock) {
            final String methodStr = "terminate";
            if (!checkSupplicantAndLogFailure(methodStr)) {
                return;
            }
            Log.i(TAG, "Terminate supplicant service");
            try {
                mWaitForDeathLatch = new CountDownLatch(1);
                mISupplicant.terminate();
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            }
        }

        // Wait for death recipient to confirm the service death.
        try {
            if (!mWaitForDeathLatch.await(WAIT_FOR_DEATH_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                Log.w(TAG, "Timed out waiting for confirmation of supplicant death");
                supplicantServiceDiedHandler();
            } else {
                Log.d(TAG, "Got service death confirmation");
            }
        } catch (InterruptedException e) {
            Log.w(TAG, "Failed to wait for supplicant death");
        }
    }

    /**
     * Registers a death notification for supplicant.
     * @return Returns true on success.
     */
    public boolean registerDeathHandler(@NonNull WifiNative.SupplicantDeathEventHandler handler) {
        synchronized (mLock) {
            if (mDeathEventHandler != null) {
                Log.e(TAG, "Death handler already present");
            }
            mDeathEventHandler = handler;
            return true;
        }
    }

    /**
     * Deregisters a death notification for supplicant.
     * @return Returns true on success.
     */
    public boolean deregisterDeathHandler() {
        synchronized (mLock) {
            if (mDeathEventHandler == null) {
                Log.e(TAG, "No Death handler present");
            }
            mDeathEventHandler = null;
            return true;
        }
    }

}
