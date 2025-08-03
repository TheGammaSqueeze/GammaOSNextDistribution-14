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

package com.android.server.wifi;

import static android.net.wifi.WifiManager.WIFI_FEATURE_OWE;

import static com.android.server.wifi.HalDeviceManager.HDM_CREATE_IFACE_AP;
import static com.android.server.wifi.HalDeviceManager.HDM_CREATE_IFACE_AP_BRIDGE;
import static com.android.server.wifi.HalDeviceManager.HDM_CREATE_IFACE_STA;
import static com.android.server.wifi.HalDeviceManager.HDM_CREATE_IFACE_P2P;
import static com.android.server.wifi.WifiSettingsConfigStore.WIFI_NATIVE_SUPPORTED_FEATURES;
import static com.android.server.wifi.p2p.WifiP2pNative.P2P_IFACE_NAME;
import static com.android.server.wifi.p2p.WifiP2pNative.P2P_INTERFACE_PROPERTY;

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SuppressLint;
import android.hardware.wifi.WifiStatusCode;
import android.net.MacAddress;
import android.net.TrafficStats;
import android.net.apf.ApfCapabilities;
import android.net.wifi.CoexUnsafeChannel;
import android.net.wifi.MscsParams;
import android.net.wifi.OuiKeyedData;
import android.net.wifi.QosPolicyParams;
import android.net.wifi.ScanResult;
import android.net.wifi.SecurityParams;
import android.net.wifi.SoftApConfiguration;
import android.net.wifi.WifiAnnotations;
import android.net.wifi.WifiAvailableChannel;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiContext;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.RoamingMode;
import android.net.wifi.WifiScanner;
import android.net.wifi.WifiScanner.ScanData;
import android.net.wifi.WifiSsid;
import android.net.wifi.nl80211.DeviceWiphyCapabilities;
import android.net.wifi.nl80211.NativeScanResult;
import android.net.wifi.nl80211.NativeWifiClient;
import android.net.wifi.nl80211.RadioChainInfo;
import android.net.wifi.nl80211.WifiNl80211Manager;
import android.net.wifi.twt.TwtRequest;
import android.net.wifi.twt.TwtSessionCallback;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.SystemClock;
import android.os.WorkSource;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Log;
import android.util.SparseArray;
import android.util.SparseIntArray;

import com.android.internal.annotations.Immutable;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.HexDump;
import com.android.modules.utils.build.SdkLevel;
import com.android.server.wifi.SupplicantStaIfaceHal.QosPolicyStatus;
import com.android.server.wifi.hal.WifiChip;
import com.android.server.wifi.hotspot2.NetworkDetail;
import com.android.server.wifi.mockwifi.MockWifiServiceUtil;
import com.android.server.wifi.proto.WifiStatsLog;
import com.android.server.wifi.util.FrameParser;
import com.android.server.wifi.util.InformationElementUtil;
import com.android.server.wifi.util.NativeUtil;
import com.android.server.wifi.util.NetdWrapper;
import com.android.server.wifi.util.NetdWrapper.NetdEventObserver;
import com.android.wifi.resources.R;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.TimeZone;

/**
 * Native calls for bring up/shut down of the supplicant daemon and for
 * sending requests to the supplicant daemon
 *
 * {@hide}
 */
public class WifiNative {
    private static final String TAG = "WifiNative";

    private final SupplicantStaIfaceHal mSupplicantStaIfaceHal;
    private final HostapdHal mHostapdHal;
    private final WifiVendorHal mWifiVendorHal;
    private final WifiNl80211Manager mWifiCondManager;
    private final WifiMonitor mWifiMonitor;
    private final PropertyService mPropertyService;
    private final WifiMetrics mWifiMetrics;
    private final Handler mHandler;
    private final Random mRandom;
    private final BuildProperties mBuildProperties;
    private final WifiInjector mWifiInjector;
    private final WifiContext mContext;
    private NetdWrapper mNetdWrapper;
    private boolean mVerboseLoggingEnabled = false;
    private boolean mIsEnhancedOpenSupported = false;
    private final List<CoexUnsafeChannel> mCachedCoexUnsafeChannels = new ArrayList<>();
    private int mCachedCoexRestrictions;
    private CountryCodeChangeListenerInternal mCountryCodeChangeListener;
    private boolean mUseFakeScanDetails;
    private final ArrayList<ScanDetail> mFakeScanDetails = new ArrayList<>();
    private long mCachedFeatureSet;
    private boolean mQosPolicyFeatureEnabled = false;
    private final Map<String, String> mWifiCondIfacesForBridgedAp = new ArrayMap<>();
    private MockWifiServiceUtil mMockWifiModem = null;
    private InterfaceObserverInternal mInterfaceObserver;
    private InterfaceEventCallback mInterfaceListener;
    private @WifiManager.MloMode int mCachedMloMode = WifiManager.MLO_MODE_DEFAULT;
    private boolean mIsLocationModeEnabled = false;
    private long mLastLocationModeEnabledTimeMs = 0;
    private Map<String, Bundle> mCachedTwtCapabilities = new ArrayMap<>();
    /**
     * Mapping of unknown AKMs configured in overlay config item
     * config_wifiUnknownAkmToKnownAkmMapping to ScanResult security key management scheme
     * (ScanResult.KEY_MGMT_XX)
     */
    @VisibleForTesting @Nullable SparseIntArray mUnknownAkmMap;

    public WifiNative(WifiVendorHal vendorHal,
                      SupplicantStaIfaceHal staIfaceHal, HostapdHal hostapdHal,
                      WifiNl80211Manager condManager, WifiMonitor wifiMonitor,
                      PropertyService propertyService, WifiMetrics wifiMetrics,
                      Handler handler, Random random, BuildProperties buildProperties,
                      WifiInjector wifiInjector) {
        mWifiVendorHal = vendorHal;
        mSupplicantStaIfaceHal = staIfaceHal;
        mHostapdHal = hostapdHal;
        mWifiCondManager = condManager;
        mWifiMonitor = wifiMonitor;
        mPropertyService = propertyService;
        mWifiMetrics = wifiMetrics;
        mHandler = handler;
        mRandom = random;
        mBuildProperties = buildProperties;
        mWifiInjector = wifiInjector;
        mContext = wifiInjector.getContext();
        initializeUnknownAkmMapping();
    }

    private void initializeUnknownAkmMapping() {
        String[] unknownAkmMapping =
                mContext.getResources()
                        .getStringArray(R.array.config_wifiUnknownAkmToKnownAkmMapping);
        if (unknownAkmMapping == null) {
            return;
        }
        for (String line : unknownAkmMapping) {
            if (line == null) {
                continue;
            }
            String[] items = line.split(",");
            if (items.length != 2) {
                Log.e(
                        TAG,
                        "Failed to parse config_wifiUnknownAkmToKnownAkmMapping line="
                                + line
                                + ". Should contain only two values separated by comma");
                continue;
            }
            try {
                int unknownAkm = Integer.parseInt(items[0].trim());
                int knownAkm = Integer.parseInt(items[1].trim());
                // Convert the OEM configured known AKM suite selector to
                // ScanResult security key management scheme(ScanResult.KEY_MGMT_XX)*/
                int keyMgmtScheme =
                        InformationElementUtil.Capabilities.akmToScanResultKeyManagementScheme(
                                knownAkm);
                if (keyMgmtScheme != ScanResult.KEY_MGMT_UNKNOWN) {
                    if (mUnknownAkmMap == null) {
                        mUnknownAkmMap = new SparseIntArray();
                    }
                    mUnknownAkmMap.put(unknownAkm, keyMgmtScheme);
                    Log.d(
                            TAG,
                            "unknown AKM = "
                                    + unknownAkm
                                    + " - converted keyMgmtScheme: "
                                    + keyMgmtScheme);
                } else {
                    Log.e(
                            TAG,
                            "Known AKM: "
                                    + knownAkm
                                    + " is not defined in the framework."
                                    + " Hence Failed to add AKM: "
                                    + unknownAkm
                                    + " in UnknownAkmMap."
                                    + " Parsed config from overlay: "
                                    + line);
                }
            } catch (Exception e) {
                // failure to parse. Something is wrong with the configuration.
                Log.e(
                        TAG,
                        "Parsing config_wifiUnknownAkmToKnownAkmMapping line="
                                + line
                                + ". Exception occurred:"
                                + e);
            }
        }
    }

    /**
     * Enable verbose logging for all sub modules.
     */
    public void enableVerboseLogging(boolean verboseEnabled, boolean halVerboseEnabled) {
        Log.d(TAG, "enableVerboseLogging " + verboseEnabled + " hal " + halVerboseEnabled);
        mVerboseLoggingEnabled = verboseEnabled;
        mWifiCondManager.enableVerboseLogging(verboseEnabled);
        mSupplicantStaIfaceHal.enableVerboseLogging(verboseEnabled, halVerboseEnabled);
        mHostapdHal.enableVerboseLogging(verboseEnabled, halVerboseEnabled);
        mWifiVendorHal.enableVerboseLogging(verboseEnabled, halVerboseEnabled);
        mIfaceMgr.enableVerboseLogging(verboseEnabled);
    }

    /**
     * Get TWT capabilities for the interface
     */
    public Bundle getTwtCapabilities(String interfaceName) {
        return mCachedTwtCapabilities.get(interfaceName);
    }

    /**
     * Callbacks for SoftAp interface.
     */
    public class SoftApHalCallbackFromWificond implements WifiNl80211Manager.SoftApCallback {
        // placeholder for now - provide a shell so that clients don't use a
        // WifiNl80211Manager-specific API.
        private String mIfaceName;
        private SoftApHalCallback mSoftApHalCallback;

        SoftApHalCallbackFromWificond(String ifaceName,
                SoftApHalCallback softApHalCallback) {
            mIfaceName = ifaceName;
            mSoftApHalCallback = softApHalCallback;
        }

        @Override
        public void onFailure() {
            mSoftApHalCallback.onFailure();
        }

        @Override
        public void onSoftApChannelSwitched(int frequency, int bandwidth) {
            mSoftApHalCallback.onInfoChanged(mIfaceName, frequency, bandwidth,
                    ScanResult.WIFI_STANDARD_UNKNOWN, null, Collections.emptyList());
        }

        @Override
        public void onConnectedClientsChanged(NativeWifiClient client, boolean isConnected) {
            mSoftApHalCallback.onConnectedClientsChanged(mIfaceName,
                    client.getMacAddress(), isConnected);
        }
    }

    @SuppressLint("NewApi")
    private static class CountryCodeChangeListenerInternal implements
            WifiNl80211Manager.CountryCodeChangedListener {
        private WifiCountryCode.ChangeListener mListener;

        public void setChangeListener(@NonNull WifiCountryCode.ChangeListener listener) {
            mListener = listener;
        }

        public void onSetCountryCodeSucceeded(String country) {
            Log.d(TAG, "onSetCountryCodeSucceeded: " + country);
            if (mListener != null) {
                mListener.onSetCountryCodeSucceeded(country);
            }
        }

        @Override
        public void onCountryCodeChanged(String country) {
            Log.d(TAG, "onCountryCodeChanged: " + country);
            if (mListener != null) {
                mListener.onDriverCountryCodeChanged(country);
            }
        }
    }

    /**
     * Callbacks for SoftAp instance.
     */
    public interface SoftApHalCallback {
        /**
         * Invoked when there is a fatal failure and the SoftAp is shutdown.
         */
        void onFailure();

        /**
         * Invoked when there is a fatal happen in specific instance only.
         */
        default void onInstanceFailure(String instanceName) {}

        /**
         * Invoked when a channel switch event happens - i.e. the SoftAp is moved to a different
         * channel. Also called on initial registration.
         *
         * @param apIfaceInstance The identity of the ap instance.
         * @param frequency The new frequency of the SoftAp. A value of 0 is invalid and is an
         *                     indication that the SoftAp is not enabled.
         * @param bandwidth The new bandwidth of the SoftAp.
         * @param generation The new generation of the SoftAp.
         * @param vendorData List of {@link OuiKeyedData} containing vendor-specific configuration
         *                   data, or empty list if not provided.
         */
        void onInfoChanged(String apIfaceInstance, int frequency, int bandwidth,
                int generation, MacAddress apIfaceInstanceMacAddress,
                @NonNull List<OuiKeyedData> vendorData);
        /**
         * Invoked when there is a change in the associated station (STA).
         *
         * @param apIfaceInstance The identity of the ap instance.
         * @param clientAddress Macaddress of the client.
         * @param isConnected Indication as to whether the client is connected (true), or
         *                    disconnected (false).
         */
        void onConnectedClientsChanged(String apIfaceInstance, MacAddress clientAddress,
                boolean isConnected);
    }

    /********************************************************
     * Interface management related methods.
     ********************************************************/
    /**
     * Meta-info about every iface that is active.
     */
    public static class Iface {
        /** Type of ifaces possible */
        public static final int IFACE_TYPE_AP = 0;
        public static final int IFACE_TYPE_STA_FOR_CONNECTIVITY = 1;
        public static final int IFACE_TYPE_STA_FOR_SCAN = 2;
        public static final int IFACE_TYPE_P2P = 3;

        @IntDef({IFACE_TYPE_AP, IFACE_TYPE_STA_FOR_CONNECTIVITY, IFACE_TYPE_STA_FOR_SCAN,
                IFACE_TYPE_P2P})
        @Retention(RetentionPolicy.SOURCE)
        public @interface IfaceType{}

        /** Identifier allocated for the interface */
        public final int id;
        /** Type of the iface: STA (for Connectivity or Scan) or AP */
        public @IfaceType int type;
        /** Name of the interface */
        public String name;
        /** Is the interface up? This is used to mask up/down notifications to external clients. */
        public boolean isUp;
        /** External iface destroyed listener for the iface */
        public InterfaceCallback externalListener;
        /** Network observer registered for this interface */
        public NetworkObserverInternal networkObserver;
        /** Interface feature set / capabilities */
        public long featureSet;
        public int bandsSupported;
        public DeviceWiphyCapabilities phyCapabilities;

        Iface(int id, @Iface.IfaceType int type) {
            this.id = id;
            this.type = type;
        }

        @Override
        public String toString() {
            StringBuffer sb = new StringBuffer();
            String typeString;
            switch(type) {
                case IFACE_TYPE_STA_FOR_CONNECTIVITY:
                    typeString = "STA_CONNECTIVITY";
                    break;
                case IFACE_TYPE_STA_FOR_SCAN:
                    typeString = "STA_SCAN";
                    break;
                case IFACE_TYPE_AP:
                    typeString = "AP";
                    break;
                default:
                    typeString = "<UNKNOWN>";
                    break;
            }
            sb.append("Iface:")
                .append("{")
                .append("Name=").append(name)
                .append(",")
                .append("Id=").append(id)
                .append(",")
                .append("Type=").append(typeString)
                .append("}");
            return sb.toString();
        }
    }

    /**
     * Iface Management entity. This class maintains list of all the active ifaces.
     */
    private static class IfaceManager {
        /** Integer to allocate for the next iface being created */
        private int mNextId;
        /** Map of the id to the iface structure */
        private HashMap<Integer, Iface> mIfaces = new HashMap<>();
        private boolean mVerboseLoggingEnabled = false;

        public void enableVerboseLogging(boolean enable) {
            mVerboseLoggingEnabled = enable;
        }

        /** Allocate a new iface for the given type */
        private Iface allocateIface(@Iface.IfaceType int type) {
            if (mVerboseLoggingEnabled) {
                Log.d(TAG, "IfaceManager#allocateIface: type=" + type + ", pre-map=" + mIfaces);
            }
            Iface iface = new Iface(mNextId, type);
            mIfaces.put(mNextId, iface);
            mNextId++;
            return iface;
        }

        /** Remove the iface using the provided id */
        private Iface removeIface(int id) {
            if (mVerboseLoggingEnabled) {
                Log.d(TAG, "IfaceManager#removeIface: id=" + id + ", pre-map=" + mIfaces);
            }
            return mIfaces.remove(id);
        }

        /** Lookup the iface using the provided id */
        private Iface getIface(int id) {
            return mIfaces.get(id);
        }

        /** Lookup the iface using the provided name */
        private Iface getIface(@NonNull String ifaceName) {
            for (Iface iface : mIfaces.values()) {
                if (TextUtils.equals(iface.name, ifaceName)) {
                    return iface;
                }
            }
            return null;
        }

        /** Iterator to use for deleting all the ifaces while performing teardown on each of them */
        private Iterator<Integer> getIfaceIdIter() {
            return mIfaces.keySet().iterator();
        }

        /** Checks if there are any iface active. */
        private boolean hasAnyIface() {
            return !mIfaces.isEmpty();
        }

        /** Checks if there are any iface of the given type active. */
        private boolean hasAnyIfaceOfType(@Iface.IfaceType int type) {
            for (Iface iface : mIfaces.values()) {
                if (iface.type == type) {
                    return true;
                }
            }
            return false;
        }

        /** Checks if there are any P2P iface active. */
        private boolean hasAnyP2pIface() {
            return hasAnyIfaceOfType(Iface.IFACE_TYPE_P2P);
        }

        /** Checks if there are any STA (for connectivity) iface active. */
        private boolean hasAnyStaIfaceForConnectivity() {
            return hasAnyIfaceOfType(Iface.IFACE_TYPE_STA_FOR_CONNECTIVITY);
        }

        /** Checks if there are any STA (for scan) iface active. */
        private boolean hasAnyStaIfaceForScan() {
            return hasAnyIfaceOfType(Iface.IFACE_TYPE_STA_FOR_SCAN);
        }

        /** Checks if there are any AP iface active. */
        private boolean hasAnyApIface() {
            return hasAnyIfaceOfType(Iface.IFACE_TYPE_AP);
        }

        private @NonNull Set<String> findAllStaIfaceNames() {
            Set<String> ifaceNames = new ArraySet<>();
            for (Iface iface : mIfaces.values()) {
                if (iface.type == Iface.IFACE_TYPE_STA_FOR_CONNECTIVITY
                        || iface.type == Iface.IFACE_TYPE_STA_FOR_SCAN) {
                    ifaceNames.add(iface.name);
                }
            }
            return ifaceNames;
        }

        private @NonNull Set<String> findAllApIfaceNames() {
            Set<String> ifaceNames = new ArraySet<>();
            for (Iface iface : mIfaces.values()) {
                if (iface.type == Iface.IFACE_TYPE_AP) {
                    ifaceNames.add(iface.name);
                }
            }
            return ifaceNames;
        }

        /** Removes the existing iface that does not match the provided id. */
        public Iface removeExistingIface(int newIfaceId) {
            if (mVerboseLoggingEnabled) {
                Log.d(TAG, "IfaceManager#removeExistingIface: newIfaceId=" + newIfaceId
                        + ", pre-map=" + mIfaces);
            }
            Iface removedIface = null;
            // The number of ifaces in the database could be 1 existing & 1 new at the max.
            if (mIfaces.size() > 2) {
                Log.wtf(TAG, "More than 1 existing interface found");
            }
            Iterator<Map.Entry<Integer, Iface>> iter = mIfaces.entrySet().iterator();
            while (iter.hasNext()) {
                Map.Entry<Integer, Iface> entry = iter.next();
                if (entry.getKey() != newIfaceId) {
                    removedIface = entry.getValue();
                    iter.remove();
                }
            }
            return removedIface;
        }

        @Override
        public String toString() {
            return mIfaces.toString();
        }
    }

    private class NormalScanEventCallback implements WifiNl80211Manager.ScanEventCallback {
        private String mIfaceName;

        NormalScanEventCallback(String ifaceName) {
            mIfaceName = ifaceName;
        }

        @Override
        public void onScanResultReady() {
            Log.d(TAG, "Scan result ready event");
            mWifiMonitor.broadcastScanResultEvent(mIfaceName);
        }

        @Override
        public void onScanFailed() {
            Log.d(TAG, "Scan failed event");
            mWifiMonitor.broadcastScanFailedEvent(mIfaceName, WifiScanner.REASON_UNSPECIFIED);
        }

        @Override
        public void onScanFailed(int errorCode) {
            Log.d(TAG, "Scan failed event: errorCode: " + errorCode);
            mWifiMonitor.broadcastScanFailedEvent(mIfaceName, errorCode);
        }
    }

    private class PnoScanEventCallback implements WifiNl80211Manager.ScanEventCallback {
        private String mIfaceName;

        PnoScanEventCallback(String ifaceName) {
            mIfaceName = ifaceName;
        }

        @Override
        public void onScanResultReady() {
            Log.d(TAG, "Pno scan result event");
            mWifiMonitor.broadcastPnoScanResultEvent(mIfaceName);
            mWifiMetrics.incrementPnoFoundNetworkEventCount();
        }

        @Override
        public void onScanFailed() {
            Log.d(TAG, "Pno Scan failed event");
            WifiStatsLog.write(WifiStatsLog.PNO_SCAN_STOPPED,
                    WifiStatsLog.PNO_SCAN_STOPPED__STOP_REASON__SCAN_FAILED,
                    0, false, false, false, false, // default values
                    WifiStatsLog.PNO_SCAN_STOPPED__FAILURE_CODE__WIFICOND_SCAN_FAILURE);
        }
    }

    private final Object mLock = new Object();
    private final IfaceManager mIfaceMgr = new IfaceManager();
    private HashSet<StatusListener> mStatusListeners = new HashSet<>();

    /** Helper method invoked to start supplicant if there were no ifaces */
    private boolean startHal() {
        synchronized (mLock) {
            if (!mIfaceMgr.hasAnyIface()) {
                if (mWifiVendorHal.isVendorHalSupported()) {
                    if (!mWifiVendorHal.startVendorHal()) {
                        Log.e(TAG, "Failed to start vendor HAL");
                        return false;
                    }
                    if (SdkLevel.isAtLeastS()) {
                        mWifiVendorHal.setCoexUnsafeChannels(
                                mCachedCoexUnsafeChannels, mCachedCoexRestrictions);
                    }
                } else {
                    Log.i(TAG, "Vendor Hal not supported, ignoring start.");
                }
            }
            registerWificondListenerIfNecessary();
            return true;
        }
    }

    /** Helper method invoked to stop HAL if there are no more ifaces */
    private void stopHalAndWificondIfNecessary() {
        synchronized (mLock) {
            if (!mIfaceMgr.hasAnyIface()) {
                if (!mWifiCondManager.tearDownInterfaces()) {
                    Log.e(TAG, "Failed to teardown ifaces from wificond");
                }
                if (mWifiVendorHal.isVendorHalSupported()) {
                    mWifiVendorHal.stopVendorHal();
                } else {
                    Log.i(TAG, "Vendor Hal not supported, ignoring stop.");
                }
            }
        }
    }

    /**
     * Helper method invoked to setup wificond related callback/listener.
     */
    private void registerWificondListenerIfNecessary() {
        if (mCountryCodeChangeListener == null && SdkLevel.isAtLeastS()) {
            // The country code listener is a new API in S.
            mCountryCodeChangeListener = new CountryCodeChangeListenerInternal();
            mWifiCondManager.registerCountryCodeChangedListener(Runnable::run,
                    mCountryCodeChangeListener);
        }
    }

    private static final int CONNECT_TO_SUPPLICANT_RETRY_INTERVAL_MS = 100;
    private static final int CONNECT_TO_SUPPLICANT_RETRY_TIMES = 50;
    /**
     * This method is called to wait for establishing connection to wpa_supplicant.
     *
     * @return true if connection is established, false otherwise.
     */
    private boolean startAndWaitForSupplicantConnection() {
        // Start initialization if not already started.
        if (!mSupplicantStaIfaceHal.isInitializationStarted()
                && !mSupplicantStaIfaceHal.initialize()) {
            return false;
        }
        if (!mSupplicantStaIfaceHal.startDaemon()) {
            Log.e(TAG, "Failed to startup supplicant");
            return false;
        }
        boolean connected = false;
        int connectTries = 0;
        while (!connected && connectTries++ < CONNECT_TO_SUPPLICANT_RETRY_TIMES) {
            // Check if the initialization is complete.
            connected = mSupplicantStaIfaceHal.isInitializationComplete();
            if (connected) {
                break;
            }
            try {
                Thread.sleep(CONNECT_TO_SUPPLICANT_RETRY_INTERVAL_MS);
            } catch (InterruptedException ignore) {
            }
        }
        return connected;
    }

    /** Helper method invoked to start supplicant if there were no STA ifaces */
    private boolean startSupplicant() {
        synchronized (mLock) {
            if (!mIfaceMgr.hasAnyStaIfaceForConnectivity()) {
                if (!startAndWaitForSupplicantConnection()) {
                    Log.e(TAG, "Failed to connect to supplicant");
                    return false;
                }
                if (!mSupplicantStaIfaceHal.registerDeathHandler(
                        new SupplicantDeathHandlerInternal())) {
                    Log.e(TAG, "Failed to register supplicant death handler");
                    return false;
                }
            }
            return true;
        }
    }

    /** Helper method invoked to stop supplicant if there are no more STA ifaces */
    private void stopSupplicantIfNecessary() {
        synchronized (mLock) {
            if (!mIfaceMgr.hasAnyStaIfaceForConnectivity()) {
                if (mSupplicantStaIfaceHal.isInitializationStarted()) {
                    if (!mSupplicantStaIfaceHal.deregisterDeathHandler()) {
                        Log.e(TAG, "Failed to deregister supplicant death handler");
                    }

                }
                if (!mIfaceMgr.hasAnyP2pIface()) {
                    if (mSupplicantStaIfaceHal.isInitializationStarted()) {
                        mSupplicantStaIfaceHal.terminate();
                    } else {
                        mWifiInjector.getWifiP2pNative().stopP2pSupplicantIfNecessary();
                    }
                }
            }
        }
    }

    /** Helper method invoked to start hostapd if there were no AP ifaces */
    private boolean startHostapd() {
        synchronized (mLock) {
            if (!mIfaceMgr.hasAnyApIface()) {
                if (!startAndWaitForHostapdConnection()) {
                    Log.e(TAG, "Failed to connect to hostapd");
                    return false;
                }
                if (!mHostapdHal.registerDeathHandler(
                        new HostapdDeathHandlerInternal())) {
                    Log.e(TAG, "Failed to register hostapd death handler");
                    return false;
                }
            }
            return true;
        }
    }

    /** Helper method invoked to stop hostapd if there are no more AP ifaces */
    private void stopHostapdIfNecessary() {
        synchronized (mLock) {
            if (!mIfaceMgr.hasAnyApIface()) {
                if (!mHostapdHal.deregisterDeathHandler()) {
                    Log.e(TAG, "Failed to deregister hostapd death handler");
                }
                mHostapdHal.terminate();
            }
        }
    }

    /**
     * Helper method to register a new {@link InterfaceObserverInternal}, if there is no previous
     * observer in place and {@link WifiGlobals#isWifiInterfaceAddedSelfRecoveryEnabled()} is
     * enabled.
     */
    private void registerInterfaceObserver() {
        if (!mWifiInjector.getWifiGlobals().isWifiInterfaceAddedSelfRecoveryEnabled()) {
            return;
        }
        if (mInterfaceObserver != null) {
            Log.d(TAG, "Interface observer has previously been registered.");
            return;
        }
        mInterfaceObserver = new InterfaceObserverInternal();
        mNetdWrapper.registerObserver(mInterfaceObserver);
        Log.d(TAG, "Registered new interface observer.");
    }

    /** Helper method to register a network observer and return it */
    private boolean registerNetworkObserver(NetworkObserverInternal observer) {
        if (observer == null) return false;
        mNetdWrapper.registerObserver(observer);
        return true;
    }

    /** Helper method to unregister a network observer */
    private boolean unregisterNetworkObserver(NetworkObserverInternal observer) {
        if (observer == null) return false;
        mNetdWrapper.unregisterObserver(observer);
        return true;
    }

    /**
     * Helper method invoked to teardown client iface (for connectivity) and perform
     * necessary cleanup
     */
    private void onClientInterfaceForConnectivityDestroyed(@NonNull Iface iface) {
        synchronized (mLock) {
            mWifiMonitor.stopMonitoring(iface.name);
            if (!unregisterNetworkObserver(iface.networkObserver)) {
                Log.e(TAG, "Failed to unregister network observer on " + iface);
            }
            if (!mSupplicantStaIfaceHal.teardownIface(iface.name)) {
                Log.e(TAG, "Failed to teardown iface in supplicant on " + iface);
            }
            if (!mWifiCondManager.tearDownClientInterface(iface.name)) {
                Log.e(TAG, "Failed to teardown iface in wificond on " + iface);
            }
            stopSupplicantIfNecessary();
            stopHalAndWificondIfNecessary();
        }
    }

    /** Helper method invoked to teardown client iface (for scan) and perform necessary cleanup */
    private void onClientInterfaceForScanDestroyed(@NonNull Iface iface) {
        synchronized (mLock) {
            mWifiMonitor.stopMonitoring(iface.name);
            if (!unregisterNetworkObserver(iface.networkObserver)) {
                Log.e(TAG, "Failed to unregister network observer on " + iface);
            }
            if (!mWifiCondManager.tearDownClientInterface(iface.name)) {
                Log.e(TAG, "Failed to teardown iface in wificond on " + iface);
            }
            stopHalAndWificondIfNecessary();
        }
    }

    /** Helper method invoked to teardown softAp iface and perform necessary cleanup */
    private void onSoftApInterfaceDestroyed(@NonNull Iface iface) {
        synchronized (mLock) {
            if (!unregisterNetworkObserver(iface.networkObserver)) {
                Log.e(TAG, "Failed to unregister network observer on " + iface);
            }
            if (!mHostapdHal.removeAccessPoint(iface.name)) {
                Log.e(TAG, "Failed to remove access point on " + iface);
            }
            String wificondIface = iface.name;
            String bridgedApInstance = mWifiCondIfacesForBridgedAp.remove(iface.name);
            if (bridgedApInstance != null) {
                wificondIface = bridgedApInstance;
            }
            if (!mWifiCondManager.tearDownSoftApInterface(wificondIface)) {
                Log.e(TAG, "Failed to teardown iface in wificond on " + iface);
            }
            stopHostapdIfNecessary();
            stopHalAndWificondIfNecessary();
        }
    }

    /** Helper method invoked to teardown iface and perform necessary cleanup */
    private void onInterfaceDestroyed(@NonNull Iface iface) {
        synchronized (mLock) {
            if (iface.type == Iface.IFACE_TYPE_STA_FOR_CONNECTIVITY) {
                onClientInterfaceForConnectivityDestroyed(iface);
            } else if (iface.type == Iface.IFACE_TYPE_STA_FOR_SCAN) {
                onClientInterfaceForScanDestroyed(iface);
            } else if (iface.type == Iface.IFACE_TYPE_AP) {
                onSoftApInterfaceDestroyed(iface);
            }
            // Invoke the external callback only if the iface was not destroyed because of vendor
            // HAL crash. In case of vendor HAL crash, let the crash recovery destroy the mode
            // managers.
            if (mWifiVendorHal.isVendorHalReady()) {
                iface.externalListener.onDestroyed(iface.name);
            }
        }
    }

    /**
     * Callback to be invoked by HalDeviceManager when an interface is destroyed.
     */
    private class InterfaceDestoyedListenerInternal
            implements HalDeviceManager.InterfaceDestroyedListener {
        /** Identifier allocated for the interface */
        private final int mInterfaceId;

        InterfaceDestoyedListenerInternal(int ifaceId) {
            mInterfaceId = ifaceId;
        }

        @Override
        public void onDestroyed(@NonNull String ifaceName) {
            synchronized (mLock) {
                final Iface iface = mIfaceMgr.removeIface(mInterfaceId);
                if (iface == null) {
                    if (mVerboseLoggingEnabled) {
                        Log.v(TAG, "Received iface destroyed notification on an invalid iface="
                                + ifaceName);
                    }
                    return;
                }
                onInterfaceDestroyed(iface);
                Log.i(TAG, "Successfully torn down " + iface);
            }
        }
    }

    /**
     * Helper method invoked to trigger the status changed callback after one of the native
     * daemon's death.
     */
    private void onNativeDaemonDeath() {
        synchronized (mLock) {
            for (StatusListener listener : mStatusListeners) {
                listener.onStatusChanged(false);
            }
            for (StatusListener listener : mStatusListeners) {
                listener.onStatusChanged(true);
            }
        }
    }

    /**
     * Death handler for the Vendor HAL daemon.
     */
    private class VendorHalDeathHandlerInternal implements VendorHalDeathEventHandler {
        @Override
        public void onDeath() {
            synchronized (mLock) {
                Log.i(TAG, "Vendor HAL died. Cleaning up internal state.");
                onNativeDaemonDeath();
                mWifiMetrics.incrementNumHalCrashes();
            }
        }
    }

    /**
     * Death handler for the wificond daemon.
     */
    private class WificondDeathHandlerInternal implements Runnable {
        @Override
        public void run() {
            synchronized (mLock) {
                Log.i(TAG, "wificond died. Cleaning up internal state.");
                onNativeDaemonDeath();
                mWifiMetrics.incrementNumWificondCrashes();
            }
        }
    }

    /**
     * Death handler for the supplicant daemon.
     */
    private class SupplicantDeathHandlerInternal implements SupplicantDeathEventHandler {
        @Override
        public void onDeath() {
            mHandler.post(() -> {
                synchronized (mLock) {
                    Log.i(TAG, "wpa_supplicant died. Cleaning up internal state.");
                    onNativeDaemonDeath();
                    mWifiMetrics.incrementNumSupplicantCrashes();
                }
            });
        }
    }

    /**
     * Death handler for the hostapd daemon.
     */
    private class HostapdDeathHandlerInternal implements HostapdDeathEventHandler {
        @Override
        public void onDeath() {
            synchronized (mLock) {
                Log.i(TAG, "hostapd died. Cleaning up internal state.");
                onNativeDaemonDeath();
                mWifiMetrics.incrementNumHostapdCrashes();
            }
        }
    }

    /** Helper method invoked to handle interface change. */
    private void onInterfaceStateChanged(Iface iface, boolean isUp) {
        synchronized (mLock) {
            // Mask multiple notifications with the same state.
            if (isUp == iface.isUp) {
                if (mVerboseLoggingEnabled) {
                    Log.v(TAG, "Interface status unchanged on " + iface + " from " + isUp
                            + ", Ignoring...");
                }
                return;
            }
            Log.i(TAG, "Interface state changed on " + iface + ", isUp=" + isUp);
            if (isUp) {
                iface.externalListener.onUp(iface.name);
            } else {
                iface.externalListener.onDown(iface.name);
                if (iface.type == Iface.IFACE_TYPE_STA_FOR_CONNECTIVITY
                        || iface.type == Iface.IFACE_TYPE_STA_FOR_SCAN) {
                    mWifiMetrics.incrementNumClientInterfaceDown();
                } else if (iface.type == Iface.IFACE_TYPE_AP) {
                    mWifiMetrics.incrementNumSoftApInterfaceDown();
                }
            }
            iface.isUp = isUp;
        }
    }

    /**
     * Listener for wifi interface events.
     */
    public interface InterfaceEventCallback {

        /**
         * Interface physical-layer link state has changed.
         *
         * @param ifaceName The interface.
         * @param isLinkUp True if the physical link-layer connection signal is valid.
         */
        void onInterfaceLinkStateChanged(String ifaceName, boolean isLinkUp);

        /**
         * Interface has been added.
         *
         * @param ifaceName Name of the interface.
         */
        void onInterfaceAdded(String ifaceName);
    }

    /**
     * Register a listener for wifi interface events.
     *
     * @param ifaceEventCallback Listener object.
     */
    public void setWifiNativeInterfaceEventCallback(InterfaceEventCallback ifaceEventCallback) {
        mInterfaceListener = ifaceEventCallback;
        Log.d(TAG, "setWifiNativeInterfaceEventCallback");
    }

    private class InterfaceObserverInternal implements NetdEventObserver {
        private static final String TAG = "InterfaceObserverInternal";
        private final String mSelfRecoveryInterfaceName = mContext.getResources().getString(
                R.string.config_wifiSelfRecoveryInterfaceName);

        @Override
        public void interfaceLinkStateChanged(String ifaceName, boolean isLinkUp) {
            if (!ifaceName.equals(mSelfRecoveryInterfaceName)) {
                return;
            }
            Log.d(TAG, "Received interfaceLinkStateChanged, iface=" + ifaceName + " up="
                    + isLinkUp);
            if (mInterfaceListener != null) {
                mInterfaceListener.onInterfaceLinkStateChanged(ifaceName, isLinkUp);
            } else {
                Log.e(TAG, "Received interfaceLinkStateChanged, interfaceListener=null");
            }
        }

        @Override
        public void interfaceStatusChanged(String iface, boolean up) {
            // unused.
        }

        @Override
        public void interfaceAdded(String ifaceName) {
            if (!ifaceName.equals(mSelfRecoveryInterfaceName)) {
                return;
            }
            Log.d(TAG, "Received interfaceAdded, iface=" + ifaceName);
            if (mInterfaceListener != null) {
                mInterfaceListener.onInterfaceAdded(ifaceName);
            } else {
                Log.e(TAG, "Received interfaceAdded, interfaceListener=null");
            }
        }

    }

    /**
     * Network observer to use for all interface up/down notifications.
     */
    private class NetworkObserverInternal implements NetdEventObserver {
        /** Identifier allocated for the interface */
        private final int mInterfaceId;

        NetworkObserverInternal(int id) {
            mInterfaceId = id;
        }

        /**
         * Note: We should ideally listen to
         * {@link NetdEventObserver#interfaceStatusChanged(String, boolean)} here. But, that
         * callback is not working currently (broken in netd). So, instead listen to link state
         * change callbacks as triggers to query the real interface state. We should get rid of
         * this workaround if we get the |interfaceStatusChanged| callback to work in netd.
         * Also, this workaround will not detect an interface up event, if the link state is
         * still down.
         */
        @Override
        public void interfaceLinkStateChanged(String ifaceName, boolean unusedIsLinkUp) {
            // This is invoked from the main system_server thread. Post to our handler.
            mHandler.post(() -> {
                synchronized (mLock) {
                    if (mVerboseLoggingEnabled) {
                        Log.d(TAG, "interfaceLinkStateChanged: ifaceName=" + ifaceName
                                + ", mInterfaceId = " + mInterfaceId
                                + ", mIfaceMgr=" + mIfaceMgr.toString());
                    }
                    final Iface ifaceWithId = mIfaceMgr.getIface(mInterfaceId);
                    if (ifaceWithId == null) {
                        if (mVerboseLoggingEnabled) {
                            Log.v(TAG, "Received iface link up/down notification on an invalid"
                                    + " iface=" + mInterfaceId);
                        }
                        return;
                    }
                    final Iface ifaceWithName = mIfaceMgr.getIface(ifaceName);
                    if (ifaceWithName == null || ifaceWithName != ifaceWithId) {
                        if (mVerboseLoggingEnabled) {
                            Log.v(TAG, "Received iface link up/down notification on an invalid"
                                    + " iface=" + ifaceName);
                        }
                        return;
                    }
                    onInterfaceStateChanged(ifaceWithName, isInterfaceUp(ifaceName));
                }
            });
        }

        @Override
        public void interfaceStatusChanged(String ifaceName, boolean unusedIsLinkUp) {
            // unused currently. Look at note above.
        }

        @Override
        public void interfaceAdded(String iface){
            // unused currently.
        }
    }

    /**
     * Radio mode change handler for the Vendor HAL daemon.
     */
    private class VendorHalRadioModeChangeHandlerInternal
            implements VendorHalRadioModeChangeEventHandler {
        @Override
        public void onMcc(int band) {
            synchronized (mLock) {
                Log.i(TAG, "Device is in MCC mode now");
                mWifiMetrics.incrementNumRadioModeChangeToMcc();
            }
        }
        @Override
        public void onScc(int band) {
            synchronized (mLock) {
                Log.i(TAG, "Device is in SCC mode now");
                mWifiMetrics.incrementNumRadioModeChangeToScc();
            }
        }
        @Override
        public void onSbs(int band) {
            synchronized (mLock) {
                Log.i(TAG, "Device is in SBS mode now");
                mWifiMetrics.incrementNumRadioModeChangeToSbs();
            }
        }
        @Override
        public void onDbs() {
            synchronized (mLock) {
                Log.i(TAG, "Device is in DBS mode now");
                mWifiMetrics.incrementNumRadioModeChangeToDbs();
            }
        }
    }

    // For devices that don't support the vendor HAL, we will not support any concurrency.
    // So simulate the HalDeviceManager behavior by triggering the destroy listener for
    // any active interface.
    private String handleIfaceCreationWhenVendorHalNotSupported(@NonNull Iface newIface) {
        synchronized (mLock) {
            Iface existingIface = mIfaceMgr.removeExistingIface(newIface.id);
            if (existingIface != null) {
                onInterfaceDestroyed(existingIface);
                Log.i(TAG, "Successfully torn down " + existingIface);
            }
            // Return the interface name directly from the system property.
            return mPropertyService.getString("wifi.interface", "wlan0");
        }
    }

    /**
     * Helper function to handle creation of STA iface.
     * For devices which do not the support the HAL, this will bypass HalDeviceManager &
     * teardown any existing iface.
     */
    private String createStaIface(@NonNull Iface iface, @NonNull WorkSource requestorWs,
            @NonNull ConcreteClientModeManager concreteClientModeManager) {
        synchronized (mLock) {
            if (mWifiVendorHal.isVendorHalSupported()) {
                return mWifiVendorHal.createStaIface(
                        new InterfaceDestoyedListenerInternal(iface.id), requestorWs,
                        concreteClientModeManager);
            } else {
                Log.i(TAG, "Vendor Hal not supported, ignoring createStaIface.");
                return handleIfaceCreationWhenVendorHalNotSupported(iface);
            }
        }
    }

    /**
     * Helper function to handle creation of AP iface.
     * For devices which do not the support the HAL, this will bypass HalDeviceManager &
     * teardown any existing iface.
     */
    private String createApIface(@NonNull Iface iface, @NonNull WorkSource requestorWs,
            @SoftApConfiguration.BandType int band, boolean isBridged,
            @NonNull SoftApManager softApManager, @NonNull List<OuiKeyedData> vendorData) {
        synchronized (mLock) {
            if (mWifiVendorHal.isVendorHalSupported()) {
                return mWifiVendorHal.createApIface(
                        new InterfaceDestoyedListenerInternal(iface.id), requestorWs,
                        band, isBridged, softApManager, vendorData);
            } else {
                Log.i(TAG, "Vendor Hal not supported, ignoring createApIface.");
                return handleIfaceCreationWhenVendorHalNotSupported(iface);
            }
        }
    }

    private String createP2pIfaceFromHalOrGetNameFromProperty(
            HalDeviceManager.InterfaceDestroyedListener p2pInterfaceDestroyedListener,
            Handler handler, WorkSource requestorWs) {
        synchronized (mLock) {
            if (mWifiVendorHal.isVendorHalSupported()) {
                return mWifiInjector.getHalDeviceManager().createP2pIface(
                    p2pInterfaceDestroyedListener, handler, requestorWs);
            } else {
                Log.i(TAG, "Vendor Hal not supported, ignoring createStaIface.");
                return mPropertyService.getString(P2P_INTERFACE_PROPERTY, P2P_IFACE_NAME);
            }
        }
    }

    /**
     * Helper function to handle creation of P2P iface.
     * For devices which do not the support the HAL, this will bypass HalDeviceManager &
     * teardown any existing iface.
     */
    public Iface createP2pIface(
            HalDeviceManager.InterfaceDestroyedListener p2pInterfaceDestroyedListener,
            Handler handler, WorkSource requestorWs) {
        synchronized (mLock) {
            // Make sure HAL is started for p2p
            if (!startHal()) {
                Log.e(TAG, "Failed to start Hal");
                mWifiMetrics.incrementNumSetupP2pInterfaceFailureDueToHal();
                return null;
            }
            // maintain iface status in WifiNative
            Iface iface = mIfaceMgr.allocateIface(Iface.IFACE_TYPE_P2P);
            if (iface == null) {
                Log.e(TAG, "Failed to allocate new P2P iface");
                stopHalAndWificondIfNecessary();
                return null;
            }
            iface.name = createP2pIfaceFromHalOrGetNameFromProperty(
                    p2pInterfaceDestroyedListener, handler, requestorWs);
            if (TextUtils.isEmpty(iface.name)) {
                Log.e(TAG, "Failed to create P2p iface in HalDeviceManager");
                mIfaceMgr.removeIface(iface.id);
                mWifiMetrics.incrementNumSetupP2pInterfaceFailureDueToHal();
                stopHalAndWificondIfNecessary();
                return null;
            }
            return iface;
        }
    }

    /**
     * Teardown P2p iface with input interface Id which was returned by createP2pIface.
     *
     * @param interfaceId the interface identify which was gerenated when creating P2p iface.
     */
    public void teardownP2pIface(int interfaceId) {
        synchronized (mLock) {
            mIfaceMgr.removeIface(interfaceId);
            stopHalAndWificondIfNecessary();
            stopSupplicantIfNecessary();
        }
    }

    /**
     * Get list of instance name from this bridged AP iface.
     *
     * @param ifaceName Name of the bridged interface.
     * @return list of instance name when succeed, otherwise null.
     */
    @Nullable
    public List<String> getBridgedApInstances(@NonNull String ifaceName) {
        synchronized (mLock) {
            if (mWifiVendorHal.isVendorHalSupported()) {
                return mWifiVendorHal.getBridgedApInstances(ifaceName);
            } else {
                Log.i(TAG, "Vendor Hal not supported, ignoring getBridgedApInstances.");
                return null;
            }
        }
    }

    // For devices that don't support the vendor HAL, we will not support any concurrency.
    // So simulate the HalDeviceManager behavior by triggering the destroy listener for
    // the interface.
    private boolean handleIfaceRemovalWhenVendorHalNotSupported(@NonNull Iface iface) {
        synchronized (mLock) {
            mIfaceMgr.removeIface(iface.id);
            onInterfaceDestroyed(iface);
            Log.i(TAG, "Successfully torn down " + iface);
            return true;
        }
    }

    /**
     * Helper function to handle removal of STA iface.
     * For devices which do not the support the HAL, this will bypass HalDeviceManager &
     * teardown any existing iface.
     */
    private boolean removeStaIface(@NonNull Iface iface) {
        synchronized (mLock) {
            if (mWifiVendorHal.isVendorHalSupported()) {
                return mWifiVendorHal.removeStaIface(iface.name);
            } else {
                Log.i(TAG, "Vendor Hal not supported, ignoring removeStaIface.");
                return handleIfaceRemovalWhenVendorHalNotSupported(iface);
            }
        }
    }

    /**
     * Helper function to handle removal of STA iface.
     */
    private boolean removeApIface(@NonNull Iface iface) {
        synchronized (mLock) {
            if (mWifiVendorHal.isVendorHalSupported()) {
                return mWifiVendorHal.removeApIface(iface.name);
            } else {
                Log.i(TAG, "Vendor Hal not supported, ignoring removeApIface.");
                return handleIfaceRemovalWhenVendorHalNotSupported(iface);
            }
        }
    }

    /**
     * Helper function to remove specific instance in bridged AP iface.
     *
     * @param ifaceName Name of the iface.
     * @param apIfaceInstance The identity of the ap instance.
     * @return true if the operation succeeded, false if there is an error in Hal.
     */
    public boolean removeIfaceInstanceFromBridgedApIface(@NonNull String ifaceName,
            @NonNull String apIfaceInstance) {
        synchronized (mLock) {
            if (mWifiVendorHal.isVendorHalSupported()) {
                return mWifiVendorHal.removeIfaceInstanceFromBridgedApIface(ifaceName,
                        apIfaceInstance);
            } else {
                return false;
            }
        }
    }

    /**
     * Register listener for subsystem restart event
     *
     * @param listener SubsystemRestartListener listener object.
     */
    public void registerSubsystemRestartListener(
            HalDeviceManager.SubsystemRestartListener listener) {
        if (listener != null) {
            mWifiVendorHal.registerSubsystemRestartListener(listener);
        }
    }

    /**
     * Initialize the native modules.
     *
     * @return true on success, false otherwise.
     */
    public boolean initialize() {
        synchronized (mLock) {
            if (!mWifiVendorHal.initialize(new VendorHalDeathHandlerInternal())) {
                Log.e(TAG, "Failed to initialize vendor HAL");
                return false;
            }
            mWifiCondManager.setOnServiceDeadCallback(new WificondDeathHandlerInternal());
            mWifiCondManager.tearDownInterfaces();
            mWifiVendorHal.registerRadioModeChangeHandler(
                    new VendorHalRadioModeChangeHandlerInternal());
            mNetdWrapper = mWifiInjector.makeNetdWrapper();
            return true;
        }
    }

    /**
     * Callback to notify when the status of one of the native daemons
     * (wificond, wpa_supplicant & vendor HAL) changes.
     */
    public interface StatusListener {
        /**
         * @param allReady Indicates if all the native daemons are ready for operation or not.
         */
        void onStatusChanged(boolean allReady);
    }

    /**
     * Register a StatusListener to get notified about any status changes from the native daemons.
     *
     * It is safe to re-register the same callback object - duplicates are detected and only a
     * single copy kept.
     *
     * @param listener StatusListener listener object.
     */
    public void registerStatusListener(@NonNull StatusListener listener) {
        synchronized (mLock) {
            mStatusListeners.add(listener);
        }
    }

    /**
     * Callback to notify when the associated interface is destroyed, up or down.
     */
    public interface InterfaceCallback {
        /**
         * Interface destroyed by HalDeviceManager.
         *
         * @param ifaceName Name of the iface.
         */
        void onDestroyed(String ifaceName);

        /**
         * Interface is up.
         *
         * @param ifaceName Name of the iface.
         */
        void onUp(String ifaceName);

        /**
         * Interface is down.
         *
         * @param ifaceName Name of the iface.
         */
        void onDown(String ifaceName);
    }

    private void takeBugReportInterfaceFailureIfNeeded(String bugTitle, String bugDetail) {
        if (mWifiInjector.getDeviceConfigFacade().isInterfaceFailureBugreportEnabled()) {
            mWifiInjector.getWifiDiagnostics().takeBugReport(bugTitle, bugDetail);
        }
    }

    /**
     * Setup an interface for client mode (for scan) operations.
     *
     * This method configures an interface in STA mode in the native daemons
     * (wificond, vendor HAL).
     *
     * @param interfaceCallback Associated callback for notifying status changes for the iface.
     * @param requestorWs Requestor worksource.
     * @param concreteClientModeManager ConcreteClientModeManager requesting the interface.
     * @return Returns the name of the allocated interface, will be null on failure.
     */
    public String setupInterfaceForClientInScanMode(
            @NonNull InterfaceCallback interfaceCallback, @NonNull WorkSource requestorWs,
            @NonNull ConcreteClientModeManager concreteClientModeManager) {
        synchronized (mLock) {
            if (!startHal()) {
                Log.e(TAG, "Failed to start Hal");
                mWifiMetrics.incrementNumSetupClientInterfaceFailureDueToHal();
                return null;
            }
            Iface iface = mIfaceMgr.allocateIface(Iface.IFACE_TYPE_STA_FOR_SCAN);
            if (iface == null) {
                Log.e(TAG, "Failed to allocate new STA iface");
                return null;
            }
            iface.externalListener = interfaceCallback;
            iface.name = createStaIface(iface, requestorWs, concreteClientModeManager);
            if (TextUtils.isEmpty(iface.name)) {
                Log.e(TAG, "Failed to create iface in vendor HAL");
                mIfaceMgr.removeIface(iface.id);
                mWifiMetrics.incrementNumSetupClientInterfaceFailureDueToHal();
                return null;
            }
            if (!mWifiCondManager.setupInterfaceForClientMode(iface.name, Runnable::run,
                    new NormalScanEventCallback(iface.name),
                    new PnoScanEventCallback(iface.name))) {
                Log.e(TAG, "Failed to setup iface in wificond=" + iface.name);
                teardownInterface(iface.name);
                mWifiMetrics.incrementNumSetupClientInterfaceFailureDueToWificond();
                return null;
            }
            registerInterfaceObserver();
            iface.networkObserver = new NetworkObserverInternal(iface.id);
            if (!registerNetworkObserver(iface.networkObserver)) {
                Log.e(TAG, "Failed to register network observer for iface=" + iface.name);
                teardownInterface(iface.name);
                return null;
            }
            mWifiMonitor.startMonitoring(iface.name);
            // Just to avoid any race conditions with interface state change callbacks,
            // update the interface state before we exit.
            onInterfaceStateChanged(iface, isInterfaceUp(iface.name));
            mWifiVendorHal.enableLinkLayerStats(iface.name);
            Log.i(TAG, "Successfully setup " + iface);

            iface.featureSet = getSupportedFeatureSetInternal(iface.name);
            updateSupportedBandForStaInternal(iface);

            mWifiVendorHal.enableStaChannelForPeerNetwork(mContext.getResources().getBoolean(
                            R.bool.config_wifiEnableStaIndoorChannelForPeerNetwork),
                    mContext.getResources().getBoolean(
                            R.bool.config_wifiEnableStaDfsChannelForPeerNetwork));
            return iface.name;
        }
    }

    /**
     * Setup an interface for Soft AP mode operations.
     *
     * This method configures an interface in AP mode in all the native daemons
     * (wificond, wpa_supplicant & vendor HAL).
     *
     * @param interfaceCallback Associated callback for notifying status changes for the iface.
     * @param requestorWs Requestor worksource.
     * @param isBridged Whether or not AP interface is a bridge interface.
     * @param softApManager SoftApManager of the request.
     * @param vendorData List of {@link OuiKeyedData} containing vendor-provided
     *                   configuration data. Empty list indicates no vendor data.
     * @return Returns the name of the allocated interface, will be null on failure.
     */
    public String setupInterfaceForSoftApMode(
            @NonNull InterfaceCallback interfaceCallback, @NonNull WorkSource requestorWs,
            @SoftApConfiguration.BandType int band, boolean isBridged,
            @NonNull SoftApManager softApManager, @NonNull List<OuiKeyedData> vendorData) {
        synchronized (mLock) {
            String bugTitle = "Wi-Fi BugReport (softAp interface failure)";
            String errorMsg = "";
            if (!startHal()) {
                errorMsg = "Failed to start softAp Hal";
                Log.e(TAG, errorMsg);
                mWifiMetrics.incrementNumSetupSoftApInterfaceFailureDueToHal();
                takeBugReportInterfaceFailureIfNeeded(bugTitle, errorMsg);
                softApManager.writeSoftApStartedEvent(SoftApManager.START_RESULT_FAILURE_START_HAL);
                return null;
            }
            if (!startHostapd()) {
                errorMsg = "Failed to start softAp hostapd";
                Log.e(TAG, errorMsg);
                mWifiMetrics.incrementNumSetupSoftApInterfaceFailureDueToHostapd();
                takeBugReportInterfaceFailureIfNeeded(bugTitle, errorMsg);
                softApManager.writeSoftApStartedEvent(
                        SoftApManager.START_RESULT_FAILURE_START_HOSTAPD);
                return null;
            }
            Iface iface = mIfaceMgr.allocateIface(Iface.IFACE_TYPE_AP);
            if (iface == null) {
                Log.e(TAG, "Failed to allocate new AP iface");
                return null;
            }
            iface.externalListener = interfaceCallback;
            iface.name = createApIface(iface, requestorWs, band, isBridged, softApManager,
                    vendorData);
            if (TextUtils.isEmpty(iface.name)) {
                errorMsg = "Failed to create softAp iface in vendor HAL";
                Log.e(TAG, errorMsg);
                mIfaceMgr.removeIface(iface.id);
                mWifiMetrics.incrementNumSetupSoftApInterfaceFailureDueToHal();
                takeBugReportInterfaceFailureIfNeeded(bugTitle, errorMsg);
                return null;
            }
            String ifaceInstanceName = iface.name;
            if (isBridged) {
                List<String> instances = getBridgedApInstances(iface.name);
                if (instances == null || instances.size() == 0) {
                    errorMsg = "Failed to get bridged AP instances" + iface.name;
                    Log.e(TAG, errorMsg);
                    teardownInterface(iface.name);
                    mWifiMetrics.incrementNumSetupSoftApInterfaceFailureDueToHal();
                    takeBugReportInterfaceFailureIfNeeded(bugTitle, errorMsg);
                    return null;
                }
                // Always select first instance as wificond interface.
                ifaceInstanceName = instances.get(0);
                mWifiCondIfacesForBridgedAp.put(iface.name, ifaceInstanceName);
            }
            if (!mWifiCondManager.setupInterfaceForSoftApMode(ifaceInstanceName)) {
                errorMsg = "Failed to setup softAp iface in wifiCond manager on " + iface;
                Log.e(TAG, errorMsg);
                teardownInterface(iface.name);
                mWifiMetrics.incrementNumSetupSoftApInterfaceFailureDueToWificond();
                takeBugReportInterfaceFailureIfNeeded(bugTitle, errorMsg);
                return null;
            }
            iface.networkObserver = new NetworkObserverInternal(iface.id);
            if (!registerNetworkObserver(iface.networkObserver)) {
                Log.e(TAG, "Failed to register network observer on " + iface);
                teardownInterface(iface.name);
                return null;
            }
            // Just to avoid any race conditions with interface state change callbacks,
            // update the interface state before we exit.
            onInterfaceStateChanged(iface, isInterfaceUp(iface.name));
            Log.i(TAG, "Successfully setup " + iface);

            iface.featureSet = getSupportedFeatureSetInternal(iface.name);
            updateSupportedBandForStaInternal(iface);
            return iface.name;
        }
    }

    /**
     * Switches an existing Client mode interface from connectivity
     * {@link Iface#IFACE_TYPE_STA_FOR_CONNECTIVITY} to scan mode
     * {@link Iface#IFACE_TYPE_STA_FOR_SCAN}.
     *
     * @param ifaceName Name of the interface.
     * @param requestorWs Requestor worksource.
     * @return true if the operation succeeded, false if there is an error or the iface is already
     * in scan mode.
     */
    public boolean switchClientInterfaceToScanMode(@NonNull String ifaceName,
            @NonNull WorkSource requestorWs) {
        synchronized (mLock) {
            final Iface iface = mIfaceMgr.getIface(ifaceName);
            if (iface == null) {
                Log.e(TAG, "Trying to switch to scan mode on an invalid iface=" + ifaceName);
                return false;
            }
            if (iface.type == Iface.IFACE_TYPE_STA_FOR_SCAN) {
                Log.e(TAG, "Already in scan mode on iface=" + ifaceName);
                return true;
            }
            if (mWifiVendorHal.isVendorHalSupported()
                    && !mWifiVendorHal.replaceStaIfaceRequestorWs(iface.name, requestorWs)) {
                Log.e(TAG, "Failed to replace requestor ws on " + iface);
                teardownInterface(iface.name);
                return false;
            }
            if (!mSupplicantStaIfaceHal.teardownIface(iface.name)) {
                Log.e(TAG, "Failed to teardown iface in supplicant on " + iface);
                teardownInterface(iface.name);
                return false;
            }
            iface.type = Iface.IFACE_TYPE_STA_FOR_SCAN;
            stopSupplicantIfNecessary();
            iface.featureSet = getSupportedFeatureSetInternal(iface.name);
            updateSupportedBandForStaInternal(iface);
            iface.phyCapabilities = null;
            Log.i(TAG, "Successfully switched to scan mode on iface=" + iface);
            return true;
        }
    }

    /**
     * Switches an existing Client mode interface from scan mode
     * {@link Iface#IFACE_TYPE_STA_FOR_SCAN} to connectivity mode
     * {@link Iface#IFACE_TYPE_STA_FOR_CONNECTIVITY}.
     *
     * @param ifaceName Name of the interface.
     * @param requestorWs Requestor worksource.
     * @return true if the operation succeeded, false if there is an error or the iface is already
     * in scan mode.
     */
    public boolean switchClientInterfaceToConnectivityMode(@NonNull String ifaceName,
            @NonNull WorkSource requestorWs) {
        synchronized (mLock) {
            final Iface iface = mIfaceMgr.getIface(ifaceName);
            if (iface == null) {
                Log.e(TAG, "Trying to switch to connectivity mode on an invalid iface="
                        + ifaceName);
                return false;
            }
            if (iface.type == Iface.IFACE_TYPE_STA_FOR_CONNECTIVITY) {
                Log.e(TAG, "Already in connectivity mode on iface=" + ifaceName);
                return true;
            }
            if (mWifiVendorHal.isVendorHalSupported()
                    && !mWifiVendorHal.replaceStaIfaceRequestorWs(iface.name, requestorWs)) {
                Log.e(TAG, "Failed to replace requestor ws on " + iface);
                teardownInterface(iface.name);
                return false;
            }
            if (!startSupplicant()) {
                Log.e(TAG, "Failed to start supplicant");
                teardownInterface(iface.name);
                mWifiMetrics.incrementNumSetupClientInterfaceFailureDueToSupplicant();
                return false;
            }
            if (!mSupplicantStaIfaceHal.setupIface(iface.name)) {
                Log.e(TAG, "Failed to setup iface in supplicant on " + iface);
                teardownInterface(iface.name);
                mWifiMetrics.incrementNumSetupClientInterfaceFailureDueToSupplicant();
                return false;
            }
            if (mContext.getResources().getBoolean(
                    R.bool.config_wifiNetworkCentricQosPolicyFeatureEnabled)
                    && isSupplicantUsingAidlService()) {
                mQosPolicyFeatureEnabled = mSupplicantStaIfaceHal
                        .setNetworkCentricQosPolicyFeatureEnabled(iface.name, true);
                if (!mQosPolicyFeatureEnabled) {
                    Log.e(TAG, "Failed to enable QoS policy feature for iface " + iface.name);
                }
            }
            iface.type = Iface.IFACE_TYPE_STA_FOR_CONNECTIVITY;
            iface.featureSet = getSupportedFeatureSetInternal(iface.name);
            saveCompleteFeatureSetInConfigStoreIfNecessary(iface.featureSet);
            updateSupportedBandForStaInternal(iface);
            mIsEnhancedOpenSupported = (iface.featureSet & WIFI_FEATURE_OWE) != 0;
            Log.i(TAG, "Successfully switched to connectivity mode on iface=" + iface);
            return true;
        }
    }

    /**
     * Change the requestor WorkSource for a given STA iface.
     * @return true if the operation succeeded, false otherwise.
     */
    public boolean replaceStaIfaceRequestorWs(@NonNull String ifaceName, WorkSource newWorkSource) {
        final Iface iface = mIfaceMgr.getIface(ifaceName);
        if (iface == null) {
            Log.e(TAG, "Called replaceStaIfaceRequestorWs() on an invalid iface=" + ifaceName);
            return false;
        }
        if (!mWifiVendorHal.isVendorHalSupported()) {
            // if vendor HAL isn't supported, return true since there's nothing to do.
            return true;
        }
        if (!mWifiVendorHal.replaceStaIfaceRequestorWs(iface.name, newWorkSource)) {
            Log.e(TAG, "Failed to replace requestor ws on " + iface);
            teardownInterface(iface.name);
            return false;
        }
        return true;
    }

    /**
     *
     * Check if the interface is up or down.
     *
     * @param ifaceName Name of the interface.
     * @return true if iface is up, false if it's down or on error.
     */
    public boolean isInterfaceUp(@NonNull String ifaceName) {
        synchronized (mLock) {
            final Iface iface = mIfaceMgr.getIface(ifaceName);
            if (iface == null) {
                Log.e(TAG, "Trying to get iface state on invalid iface=" + ifaceName);
                return false;
            }
            try {
                return mNetdWrapper.isInterfaceUp(ifaceName);
            } catch (IllegalStateException e) {
                Log.e(TAG, "Unable to get interface config", e);
                return false;
            }
        }
    }

    /**
     * Teardown an interface in Client/AP mode.
     *
     * This method tears down the associated interface from all the native daemons
     * (wificond, wpa_supplicant & vendor HAL).
     * Also, brings down the HAL, supplicant or hostapd as necessary.
     *
     * @param ifaceName Name of the interface.
     */
    public void teardownInterface(@NonNull String ifaceName) {
        synchronized (mLock) {
            final Iface iface = mIfaceMgr.getIface(ifaceName);
            if (iface == null) {
                Log.e(TAG, "Trying to teardown an invalid iface=" + ifaceName);
                return;
            }
            // Trigger the iface removal from HAL. The rest of the cleanup will be triggered
            // from the interface destroyed callback.
            if (iface.type == Iface.IFACE_TYPE_STA_FOR_CONNECTIVITY
                    || iface.type == Iface.IFACE_TYPE_STA_FOR_SCAN) {
                if (!removeStaIface(iface)) {
                    Log.e(TAG, "Failed to remove iface in vendor HAL=" + ifaceName);
                    return;
                }
            } else if (iface.type == Iface.IFACE_TYPE_AP) {
                if (!removeApIface(iface)) {
                    Log.e(TAG, "Failed to remove iface in vendor HAL=" + ifaceName);
                    return;
                }
            }
            Log.i(TAG, "Successfully initiated teardown for iface=" + ifaceName);
        }
    }

    /**
     * Teardown all the active interfaces.
     *
     * This method tears down the associated interfaces from all the native daemons
     * (wificond, wpa_supplicant & vendor HAL).
     * Also, brings down the HAL, supplicant or hostapd as necessary.
     */
    public void teardownAllInterfaces() {
        synchronized (mLock) {
            Iterator<Integer> ifaceIdIter = mIfaceMgr.getIfaceIdIter();
            while (ifaceIdIter.hasNext()) {
                Iface iface = mIfaceMgr.getIface(ifaceIdIter.next());
                ifaceIdIter.remove();
                onInterfaceDestroyed(iface);
                Log.i(TAG, "Successfully torn down " + iface);
            }
            Log.i(TAG, "Successfully torn down all ifaces");
        }
    }

    /**
     * Get names of all the client interfaces.
     *
     * @return List of interface name of all active client interfaces.
     */
    public Set<String> getClientInterfaceNames() {
        synchronized (mLock) {
            return mIfaceMgr.findAllStaIfaceNames();
        }
    }

    /**
     * Get names of all the client interfaces.
     *
     * @return List of interface name of all active client interfaces.
     */
    public Set<String> getSoftApInterfaceNames() {
        synchronized (mLock) {
            return mIfaceMgr.findAllApIfaceNames();
        }
    }

    /********************************************************
     * Wificond operations
     ********************************************************/

    /**
     * Query the list of valid frequencies for the provided band.
     * The result depends on the on the country code that has been set.
     *
     * @param band as specified by one of the WifiScanner.WIFI_BAND_* constants.
     * The following bands are supported {@link WifiAnnotations.WifiBandBasic}:
     * WifiScanner.WIFI_BAND_24_GHZ
     * WifiScanner.WIFI_BAND_5_GHZ
     * WifiScanner.WIFI_BAND_5_GHZ_DFS_ONLY
     * WifiScanner.WIFI_BAND_6_GHZ
     * WifiScanner.WIFI_BAND_60_GHZ
     * @return frequencies vector of valid frequencies (MHz), or null for error.
     * @throws IllegalArgumentException if band is not recognized.
     */
    public int [] getChannelsForBand(@WifiAnnotations.WifiBandBasic int band) {
        if (!SdkLevel.isAtLeastS() && band == WifiScanner.WIFI_BAND_60_GHZ) {
            // 60 GHz band is new in Android S, return empty array on older SDK versions
            return new int[0];
        }
        return mWifiCondManager.getChannelsMhzForBand(band);
    }

    /**
     * Start a scan using wificond for the given parameters.
     * @param ifaceName Name of the interface.
     * @param scanType Type of scan to perform. One of {@link WifiScanner#SCAN_TYPE_LOW_LATENCY},
     * {@link WifiScanner#SCAN_TYPE_LOW_POWER} or {@link WifiScanner#SCAN_TYPE_HIGH_ACCURACY}.
     * @param freqs list of frequencies to scan for, if null scan all supported channels.
     * @param hiddenNetworkSSIDs List of hidden networks to be scanned for.
     * @param enable6GhzRnr whether Reduced Neighbor Report should be enabled for 6Ghz scanning.
     * @param vendorIes Byte array of vendor IEs
     * @return Returns true on success.
     */
    public int scan(
            @NonNull String ifaceName, @WifiAnnotations.ScanType int scanType, Set<Integer> freqs,
            List<String> hiddenNetworkSSIDs, boolean enable6GhzRnr, byte[] vendorIes) {
        int scanRequestStatus = WifiScanner.REASON_SUCCEEDED;
        boolean scanStatus = true;
        List<byte[]> hiddenNetworkSsidsArrays = new ArrayList<>();
        for (String hiddenNetworkSsid : hiddenNetworkSSIDs) {
            try {
                hiddenNetworkSsidsArrays.add(
                        NativeUtil.byteArrayFromArrayList(
                                NativeUtil.decodeSsid(hiddenNetworkSsid)));
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Illegal argument " + hiddenNetworkSsid, e);
                continue;
            }
        }
        if (SdkLevel.isAtLeastS()) {
            // enable6GhzRnr is a new parameter first introduced in Android S.
            Bundle extraScanningParams = new Bundle();
            extraScanningParams.putBoolean(WifiNl80211Manager.SCANNING_PARAM_ENABLE_6GHZ_RNR,
                    enable6GhzRnr);
            if (SdkLevel.isAtLeastU()) {
                extraScanningParams.putByteArray(WifiNl80211Manager.EXTRA_SCANNING_PARAM_VENDOR_IES,
                        vendorIes);
                scanRequestStatus = mWifiCondManager.startScan2(ifaceName, scanType, freqs,
                        hiddenNetworkSsidsArrays, extraScanningParams);
            } else {
                scanStatus = mWifiCondManager.startScan(ifaceName, scanType, freqs,
                        hiddenNetworkSsidsArrays,
                        extraScanningParams);
                scanRequestStatus = scanStatus
                        ? WifiScanner.REASON_SUCCEEDED : WifiScanner.REASON_UNSPECIFIED;

            }
        } else {
            scanStatus = mWifiCondManager.startScan(ifaceName, scanType, freqs,
                        hiddenNetworkSsidsArrays);
            scanRequestStatus = scanStatus
                    ? WifiScanner.REASON_SUCCEEDED : WifiScanner.REASON_UNSPECIFIED;
        }

        return scanRequestStatus;
    }

    /**
     * Fetch the latest scan result from kernel via wificond.
     * @param ifaceName Name of the interface.
     * @return Returns an ArrayList of ScanDetail.
     * Returns an empty ArrayList on failure.
     */
    public ArrayList<ScanDetail> getScanResults(@NonNull String ifaceName) {
        if (mUseFakeScanDetails) {
            synchronized (mFakeScanDetails) {
                ArrayList<ScanDetail> copyList = new ArrayList<>();
                for (ScanDetail sd: mFakeScanDetails) {
                    ScanDetail copy = new ScanDetail(sd);
                    copy.getScanResult().ifaceName = ifaceName;
                    // otherwise the fake will be too old
                    copy.getScanResult().timestamp = SystemClock.elapsedRealtime() * 1000;
                    copyList.add(copy);
                }
                return copyList;
            }
        }
        if (mMockWifiModem != null
                && mMockWifiModem.isMethodConfigured(
                MockWifiServiceUtil.MOCK_NL80211_SERVICE, "getScanResults")) {
            Log.i(TAG, "getScanResults was called from mock wificond");
            return convertNativeScanResults(ifaceName, mMockWifiModem.getWifiNl80211Manager()
                   .getScanResults(ifaceName, WifiNl80211Manager.SCAN_TYPE_SINGLE_SCAN));
        }
        return convertNativeScanResults(ifaceName, mWifiCondManager.getScanResults(
                ifaceName, WifiNl80211Manager.SCAN_TYPE_SINGLE_SCAN));
    }

    /**
     * Start faking scan results - using information provided via
     * {@link #addFakeScanDetail(ScanDetail)}. Stop with {@link #stopFakingScanDetails()}.
     */
    public void startFakingScanDetails() {
        if (mBuildProperties.isUserBuild()) {
            Log.wtf(TAG, "Can't fake scan results in a user build!");
            return;
        }
        Log.d(TAG, "Starting faking scan results - " + mFakeScanDetails);
        mUseFakeScanDetails = true;
    }

    /**
     * Add fake scan result. Fakes are not used until activated via
     * {@link #startFakingScanDetails()}.
     * @param fakeScanDetail
     */
    public void addFakeScanDetail(@NonNull ScanDetail fakeScanDetail) {
        synchronized (mFakeScanDetails) {
            mFakeScanDetails.add(fakeScanDetail);
        }
    }

    /**
     * Reset the fake scan result list updated via {@link #addFakeScanDetail(ScanDetail)} .}
     */
    public void resetFakeScanDetails() {
        synchronized (mFakeScanDetails) {
            mFakeScanDetails.clear();
        }
    }

    /**
     * Stop faking scan results. Started with {@link #startFakingScanDetails()}.
     */
    public void stopFakingScanDetails() {
        mUseFakeScanDetails = false;
    }

    /**
     * Fetch the latest scan result from kernel via wificond.
     * @param ifaceName Name of the interface.
     * @return Returns an ArrayList of ScanDetail.
     * Returns an empty ArrayList on failure.
     */
    public ArrayList<ScanDetail> getPnoScanResults(@NonNull String ifaceName) {
        if (mMockWifiModem != null
                && mMockWifiModem.isMethodConfigured(
                    MockWifiServiceUtil.MOCK_NL80211_SERVICE, "getPnoScanResults")) {
            Log.i(TAG, "getPnoScanResults was called from mock wificond");
            return convertNativeScanResults(ifaceName, mMockWifiModem.getWifiNl80211Manager()
                   .getScanResults(ifaceName, WifiNl80211Manager.SCAN_TYPE_PNO_SCAN));
        }
        return convertNativeScanResults(ifaceName, mWifiCondManager.getScanResults(ifaceName,
                WifiNl80211Manager.SCAN_TYPE_PNO_SCAN));
    }

    /**
     * Get the max number of SSIDs that the driver supports per scan.
     * @param ifaceName Name of the interface.
     */
    public int getMaxSsidsPerScan(@NonNull String ifaceName) {
        if (SdkLevel.isAtLeastT()) {
            return mWifiCondManager.getMaxSsidsPerScan(ifaceName);
        } else {
            return -1;
        }
    }

    private ArrayList<ScanDetail> convertNativeScanResults(@NonNull String ifaceName,
            List<NativeScanResult> nativeResults) {
        ArrayList<ScanDetail> results = new ArrayList<>();
        for (NativeScanResult result : nativeResults) {
            if (result.getSsid().length > 32) {
                Log.e(TAG, "Invalid SSID length (> 32 bytes): "
                        + Arrays.toString(result.getSsid()));
                continue;
            }
            WifiSsid originalSsid = WifiSsid.fromBytes(result.getSsid());
            MacAddress bssidMac = result.getBssid();
            if (bssidMac == null) {
                Log.e(TAG, "Invalid MAC (BSSID) for SSID " + originalSsid);
                continue;
            }
            String bssid = bssidMac.toString();
            ScanResult.InformationElement[] ies =
                    InformationElementUtil.parseInformationElements(result.getInformationElements());
            InformationElementUtil.Capabilities capabilities =
                    new InformationElementUtil.Capabilities();
            capabilities.from(
                    ies,
                    result.getCapabilities(),
                    mIsEnhancedOpenSupported,
                    result.getFrequencyMhz(),
                    mUnknownAkmMap);
            String flags = capabilities.generateCapabilitiesString();
            NetworkDetail networkDetail;
            try {
                networkDetail = new NetworkDetail(bssid, ies, null, result.getFrequencyMhz());
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Illegal argument for scan result with bssid: " + bssid, e);
                continue;
            }

            WifiSsid translatedSsid = mWifiInjector.getSsidTranslator()
                    .getTranslatedSsidAndRecordBssidCharset(originalSsid, bssidMac);
            ScanDetail scanDetail = new ScanDetail(networkDetail, translatedSsid, bssid, flags,
                    result.getSignalMbm() / 100, result.getFrequencyMhz(), result.getTsf(), ies,
                    null, result.getInformationElements());
            ScanResult scanResult = scanDetail.getScanResult();
            scanResult.setWifiStandard(wifiModeToWifiStandard(networkDetail.getWifiMode()));
            scanResult.ifaceName = ifaceName;

            // Fill up the radio chain info.
            scanResult.radioChainInfos =
                    new ScanResult.RadioChainInfo[result.getRadioChainInfos().size()];
            int idx = 0;
            for (RadioChainInfo nativeRadioChainInfo : result.getRadioChainInfos()) {
                scanResult.radioChainInfos[idx] = new ScanResult.RadioChainInfo();
                scanResult.radioChainInfos[idx].id = nativeRadioChainInfo.getChainId();
                scanResult.radioChainInfos[idx].level = nativeRadioChainInfo.getLevelDbm();
                idx++;
            }

            // Fill MLO Attributes
            scanResult.setApMldMacAddress(networkDetail.getMldMacAddress());
            scanResult.setApMloLinkId(networkDetail.getMloLinkId());
            scanResult.setAffiliatedMloLinks(networkDetail.getAffiliatedMloLinks());

            results.add(scanDetail);
        }
        if (mVerboseLoggingEnabled) {
            Log.d(TAG, "get " + results.size() + " scan results from wificond");
        }

        return results;
    }

    @WifiAnnotations.WifiStandard
    private static int wifiModeToWifiStandard(int wifiMode) {
        switch (wifiMode) {
            case InformationElementUtil.WifiMode.MODE_11A:
            case InformationElementUtil.WifiMode.MODE_11B:
            case InformationElementUtil.WifiMode.MODE_11G:
                return ScanResult.WIFI_STANDARD_LEGACY;
            case InformationElementUtil.WifiMode.MODE_11N:
                return ScanResult.WIFI_STANDARD_11N;
            case InformationElementUtil.WifiMode.MODE_11AC:
                return ScanResult.WIFI_STANDARD_11AC;
            case InformationElementUtil.WifiMode.MODE_11AX:
                return ScanResult.WIFI_STANDARD_11AX;
            case InformationElementUtil.WifiMode.MODE_11BE:
                return ScanResult.WIFI_STANDARD_11BE;
            case InformationElementUtil.WifiMode.MODE_UNDEFINED:
            default:
                return ScanResult.WIFI_STANDARD_UNKNOWN;
        }
    }

    /**
     * Start PNO scan.
     * @param ifaceName Name of the interface.
     * @param pnoSettings Pno scan configuration.
     * @return true on success.
     */
    public boolean startPnoScan(@NonNull String ifaceName, PnoSettings pnoSettings) {
        if (mMockWifiModem != null
                && mMockWifiModem.isMethodConfigured(
                MockWifiServiceUtil.MOCK_NL80211_SERVICE, "startPnoScan")) {
            Log.i(TAG, "startPnoScan was called from mock wificond");
            return mMockWifiModem.getWifiNl80211Manager()
                    .startPnoScan(ifaceName, pnoSettings.toNativePnoSettings(),
                    Runnable::run,
                        new WifiNl80211Manager.PnoScanRequestCallback() {
                            @Override
                            public void onPnoRequestSucceeded() {
                            }

                            @Override
                            public void onPnoRequestFailed() {
                            }
                        });
        }
        return mWifiCondManager.startPnoScan(ifaceName, pnoSettings.toNativePnoSettings(),
                Runnable::run,
                new WifiNl80211Manager.PnoScanRequestCallback() {
                    @Override
                    public void onPnoRequestSucceeded() {
                        mWifiMetrics.incrementPnoScanStartAttemptCount();
                    }

                    @Override
                    public void onPnoRequestFailed() {
                        WifiStatsLog.write(WifiStatsLog.PNO_SCAN_STOPPED,
                                WifiStatsLog.PNO_SCAN_STOPPED__STOP_REASON__SCAN_FAILED,
                                0, false, false, false, false, // default values
                                WifiStatsLog
                                        .PNO_SCAN_STOPPED__FAILURE_CODE__WIFICOND_REQUEST_FAILURE);
                    }
                });
    }

    /**
     * Stop PNO scan.
     * @param ifaceName Name of the interface.
     * @return true on success.
     */
    public boolean stopPnoScan(@NonNull String ifaceName) {
        return mWifiCondManager.stopPnoScan(ifaceName);
    }

    /**
     * Sends an arbitrary 802.11 management frame on the current channel.
     *
     * @param ifaceName Name of the interface.
     * @param frame Bytes of the 802.11 management frame to be sent, including the header, but not
     *              including the frame check sequence (FCS).
     * @param callback A callback triggered when the transmitted frame is ACKed or the transmission
     *                 fails.
     * @param mcs The MCS index that the frame will be sent at. If mcs < 0, the driver will select
     *            the rate automatically. If the device does not support sending the frame at a
     *            specified MCS rate, the transmission will be aborted and
     *            {@link WifiNl80211Manager.SendMgmtFrameCallback#onFailure(int)} will be called
     *            with reason {@link WifiNl80211Manager#SEND_MGMT_FRAME_ERROR_MCS_UNSUPPORTED}.
     */
    public void sendMgmtFrame(@NonNull String ifaceName, @NonNull byte[] frame,
            @NonNull WifiNl80211Manager.SendMgmtFrameCallback callback, int mcs) {
        mWifiCondManager.sendMgmtFrame(ifaceName, frame, mcs, Runnable::run, callback);
    }

    /**
     * Sends a probe request to the AP and waits for a response in order to determine whether
     * there is connectivity between the device and AP.
     *
     * @param ifaceName Name of the interface.
     * @param receiverMac the MAC address of the AP that the probe request will be sent to.
     * @param callback callback triggered when the probe was ACKed by the AP, or when
     *                an error occurs after the link probe was started.
     * @param mcs The MCS index that this probe will be sent at. If mcs < 0, the driver will select
     *            the rate automatically. If the device does not support sending the frame at a
     *            specified MCS rate, the transmission will be aborted and
     *            {@link WifiNl80211Manager.SendMgmtFrameCallback#onFailure(int)} will be called
     *            with reason {@link WifiNl80211Manager#SEND_MGMT_FRAME_ERROR_MCS_UNSUPPORTED}.
     */
    public void probeLink(@NonNull String ifaceName, @NonNull MacAddress receiverMac,
            @NonNull WifiNl80211Manager.SendMgmtFrameCallback callback, int mcs) {
        if (callback == null) {
            Log.e(TAG, "callback cannot be null!");
            return;
        }

        if (receiverMac == null) {
            Log.e(TAG, "Receiver MAC address cannot be null!");
            callback.onFailure(WifiNl80211Manager.SEND_MGMT_FRAME_ERROR_UNKNOWN);
            return;
        }

        String senderMacStr = getMacAddress(ifaceName);
        if (senderMacStr == null) {
            Log.e(TAG, "Failed to get this device's MAC Address");
            callback.onFailure(WifiNl80211Manager.SEND_MGMT_FRAME_ERROR_UNKNOWN);
            return;
        }

        byte[] frame = buildProbeRequestFrame(
                receiverMac.toByteArray(),
                NativeUtil.macAddressToByteArray(senderMacStr));
        sendMgmtFrame(ifaceName, frame, callback, mcs);
    }

    // header = 24 bytes, minimal body = 2 bytes, no FCS (will be added by driver)
    private static final int BASIC_PROBE_REQUEST_FRAME_SIZE = 24 + 2;

    private byte[] buildProbeRequestFrame(byte[] receiverMac, byte[] transmitterMac) {
        ByteBuffer frame = ByteBuffer.allocate(BASIC_PROBE_REQUEST_FRAME_SIZE);
        // ByteBuffer is big endian by default, switch to little endian
        frame.order(ByteOrder.LITTLE_ENDIAN);

        // Protocol version = 0, Type = management, Subtype = Probe Request
        frame.put((byte) 0x40);

        // no flags set
        frame.put((byte) 0x00);

        // duration = 60 microseconds. Note: this is little endian
        // Note: driver should calculate the duration and replace it before sending, putting a
        // reasonable default value here just in case.
        frame.putShort((short) 0x3c);

        // receiver/destination MAC address byte array
        frame.put(receiverMac);
        // sender MAC address byte array
        frame.put(transmitterMac);
        // BSSID (same as receiver address since we are sending to the AP)
        frame.put(receiverMac);

        // Generate random sequence number, fragment number = 0
        // Note: driver should replace the sequence number with the correct number that is
        // incremented from the last used sequence number. Putting a random sequence number as a
        // default here just in case.
        // bit 0 is least significant bit, bit 15 is most significant bit
        // bits [0, 7] go in byte 0
        // bits [8, 15] go in byte 1
        // bits [0, 3] represent the fragment number (which is 0)
        // bits [4, 15] represent the sequence number (which is random)
        // clear bits [0, 3] to set fragment number = 0
        short sequenceAndFragmentNumber = (short) (mRandom.nextInt() & 0xfff0);
        frame.putShort(sequenceAndFragmentNumber);

        // NL80211 rejects frames with an empty body, so we just need to put a placeholder
        // information element.
        // Tag for SSID
        frame.put((byte) 0x00);
        // Represents broadcast SSID. Not accurate, but works as placeholder.
        frame.put((byte) 0x00);

        return frame.array();
    }

    private static final int CONNECT_TO_HOSTAPD_RETRY_INTERVAL_MS = 100;
    private static final int CONNECT_TO_HOSTAPD_RETRY_TIMES = 50;
    /**
     * This method is called to wait for establishing connection to hostapd.
     *
     * @return true if connection is established, false otherwise.
     */
    private boolean startAndWaitForHostapdConnection() {
        // Start initialization if not already started.
        if (!mHostapdHal.isInitializationStarted()
                && !mHostapdHal.initialize()) {
            return false;
        }
        if (!mHostapdHal.startDaemon()) {
            Log.e(TAG, "Failed to startup hostapd");
            return false;
        }
        boolean connected = false;
        int connectTries = 0;
        while (!connected && connectTries++ < CONNECT_TO_HOSTAPD_RETRY_TIMES) {
            // Check if the initialization is complete.
            connected = mHostapdHal.isInitializationComplete();
            if (connected) {
                break;
            }
            try {
                Thread.sleep(CONNECT_TO_HOSTAPD_RETRY_INTERVAL_MS);
            } catch (InterruptedException ignore) {
            }
        }
        return connected;
    }

    /**
     * Start Soft AP operation using the provided configuration.
     *
     * @param ifaceName Name of the interface.
     * @param config    Configuration to use for the soft ap created.
     * @param isMetered Indicates the network is metered or not.
     * @param callback  Callback for AP events.
     * @return one of {@link SoftApManager.StartResult}
     */
    public @SoftApManager.StartResult int startSoftAp(
            @NonNull String ifaceName, SoftApConfiguration config, boolean isMetered,
            SoftApHalCallback callback) {
        if (mHostapdHal.isApInfoCallbackSupported()) {
            if (!mHostapdHal.registerApCallback(ifaceName, callback)) {
                Log.e(TAG, "Failed to register ap hal event callback");
                return SoftApManager.START_RESULT_FAILURE_REGISTER_AP_CALLBACK_HOSTAPD;
            }
        } else {
            SoftApHalCallbackFromWificond softApHalCallbackFromWificond =
                    new SoftApHalCallbackFromWificond(ifaceName, callback);
            if (!mWifiCondManager.registerApCallback(ifaceName,
                    Runnable::run, softApHalCallbackFromWificond)) {
                Log.e(TAG, "Failed to register ap hal event callback from wificond");
                return SoftApManager.START_RESULT_FAILURE_REGISTER_AP_CALLBACK_WIFICOND;
            }
        }

        if (!mHostapdHal.addAccessPoint(ifaceName, config, isMetered, callback::onFailure)) {
            String errorMsg = "Failed to add softAp";
            Log.e(TAG, errorMsg);
            mWifiMetrics.incrementNumSetupSoftApInterfaceFailureDueToHostapd();
            takeBugReportInterfaceFailureIfNeeded("Wi-Fi BugReport (softap interface failure)",
                    errorMsg);
            return SoftApManager.START_RESULT_FAILURE_ADD_AP_HOSTAPD;
        }

        return SoftApManager.START_RESULT_SUCCESS;
    }

    /**
     * Force a softap client disconnect with specific reason code.
     *
     * @param ifaceName Name of the interface.
     * @param client Mac address to force disconnect in clients of the SoftAp.
     * @param reasonCode One of disconnect reason code which defined in {@link ApConfigUtil}.
     * @return true on success, false otherwise.
     */
    public boolean forceClientDisconnect(@NonNull String ifaceName,
            @NonNull MacAddress client, int reasonCode) {
        return mHostapdHal.forceClientDisconnect(ifaceName, client, reasonCode);
    }

    /**
     * Set MAC address of the given interface
     * @param interfaceName Name of the interface
     * @param mac Mac address to change into
     * @return true on success
     */
    public boolean setStaMacAddress(String interfaceName, MacAddress mac) {
        // TODO(b/72459123): Suppress interface down/up events from this call
        // Trigger an explicit disconnect to avoid losing the disconnect event reason (if currently
        // connected) from supplicant if the interface is brought down for MAC address change.
        disconnect(interfaceName);
        return mWifiVendorHal.setStaMacAddress(interfaceName, mac);
    }

    /**
     * Set MAC address of the given interface
     * @param interfaceName Name of the interface
     * @param mac Mac address to change into
     * @return true on success
     */
    public boolean setApMacAddress(String interfaceName, MacAddress mac) {
        return mWifiVendorHal.setApMacAddress(interfaceName, mac);
    }

    /**
     * Returns true if Hal version supports setMacAddress, otherwise false.
     *
     * @param interfaceName Name of the interface
     */
    public boolean isApSetMacAddressSupported(@NonNull String interfaceName) {
        return mWifiVendorHal.isApSetMacAddressSupported(interfaceName);
    }

    /**
     * Get the factory MAC address of the given interface
     * @param interfaceName Name of the interface.
     * @return factory MAC address, or null on a failed call or if feature is unavailable.
     */
    public MacAddress getStaFactoryMacAddress(@NonNull String interfaceName) {
        return mWifiVendorHal.getStaFactoryMacAddress(interfaceName);
    }

    /**
     * Get the factory MAC address of the given interface
     * @param interfaceName Name of the interface.
     * @return factory MAC address, or null on a failed call or if feature is unavailable.
     */
    public MacAddress getApFactoryMacAddress(@NonNull String interfaceName) {
        return mWifiVendorHal.getApFactoryMacAddress(interfaceName);
    }

    /**
     * Reset MAC address to factory MAC address on the given interface
     *
     * @param interfaceName Name of the interface
     * @return true for success
     */
    public boolean resetApMacToFactoryMacAddress(@NonNull String interfaceName) {
        return mWifiVendorHal.resetApMacToFactoryMacAddress(interfaceName);
    }

    /**
     * Set the unsafe channels and restrictions to avoid for coex.
     * @param unsafeChannels List of {@link CoexUnsafeChannel} to avoid
     * @param restrictions Bitmask of WifiManager.COEX_RESTRICTION_ flags
     */
    public void setCoexUnsafeChannels(
            @NonNull List<CoexUnsafeChannel> unsafeChannels, int restrictions) {
        mCachedCoexUnsafeChannels.clear();
        mCachedCoexUnsafeChannels.addAll(unsafeChannels);
        mCachedCoexRestrictions = restrictions;
        mWifiVendorHal.setCoexUnsafeChannels(mCachedCoexUnsafeChannels, mCachedCoexRestrictions);
    }

    /********************************************************
     * Hostapd operations
     ********************************************************/

    /**
     * Callback to notify hostapd death.
     */
    public interface HostapdDeathEventHandler {
        /**
         * Invoked when the supplicant dies.
         */
        void onDeath();
    }

    /********************************************************
     * Supplicant operations
     ********************************************************/

    /**
     * Callback to notify supplicant death.
     */
    public interface SupplicantDeathEventHandler {
        /**
         * Invoked when the supplicant dies.
         */
        void onDeath();
    }

    /**
     * Set supplicant log level
     *
     * @param turnOnVerbose Whether to turn on verbose logging or not.
     */
    public void setSupplicantLogLevel(boolean turnOnVerbose) {
        mSupplicantStaIfaceHal.setLogLevel(turnOnVerbose);
    }

    /**
     * Trigger a reconnection if the iface is disconnected.
     *
     * @param ifaceName Name of the interface.
     * @return true if request is sent successfully, false otherwise.
     */
    public boolean reconnect(@NonNull String ifaceName) {
        return mSupplicantStaIfaceHal.reconnect(ifaceName);
    }

    /**
     * Trigger a reassociation even if the iface is currently connected.
     *
     * @param ifaceName Name of the interface.
     * @return true if request is sent successfully, false otherwise.
     */
    public boolean reassociate(@NonNull String ifaceName) {
        return mSupplicantStaIfaceHal.reassociate(ifaceName);
    }

    /**
     * Trigger a disconnection from the currently connected network.
     *
     * @param ifaceName Name of the interface.
     * @return true if request is sent successfully, false otherwise.
     */
    public boolean disconnect(@NonNull String ifaceName) {
        return mSupplicantStaIfaceHal.disconnect(ifaceName);
    }

    /**
     * Makes a callback to HIDL to getMacAddress from supplicant
     *
     * @param ifaceName Name of the interface.
     * @return string containing the MAC address, or null on a failed call
     */
    public String getMacAddress(@NonNull String ifaceName) {
        return mSupplicantStaIfaceHal.getMacAddress(ifaceName);
    }

    public static final int RX_FILTER_TYPE_V4_MULTICAST = 0;
    public static final int RX_FILTER_TYPE_V6_MULTICAST = 1;
    /**
     * Start filtering out Multicast V4 packets
     * @param ifaceName Name of the interface.
     * @return {@code true} if the operation succeeded, {@code false} otherwise
     *
     * Multicast filtering rules work as follows:
     *
     * The driver can filter multicast (v4 and/or v6) and broadcast packets when in
     * a power optimized mode (typically when screen goes off).
     *
     * In order to prevent the driver from filtering the multicast/broadcast packets, we have to
     * add a DRIVER RXFILTER-ADD rule followed by DRIVER RXFILTER-START to make the rule effective
     *
     * DRIVER RXFILTER-ADD Num
     *   where Num = 0 - Unicast, 1 - Broadcast, 2 - Mutil4 or 3 - Multi6
     *
     * and DRIVER RXFILTER-START
     * In order to stop the usage of these rules, we do
     *
     * DRIVER RXFILTER-STOP
     * DRIVER RXFILTER-REMOVE Num
     *   where Num is as described for RXFILTER-ADD
     *
     * The  SETSUSPENDOPT driver command overrides the filtering rules
     */
    public boolean startFilteringMulticastV4Packets(@NonNull String ifaceName) {
        return mSupplicantStaIfaceHal.stopRxFilter(ifaceName)
                && mSupplicantStaIfaceHal.removeRxFilter(
                        ifaceName, RX_FILTER_TYPE_V4_MULTICAST)
                && mSupplicantStaIfaceHal.startRxFilter(ifaceName);
    }

    /**
     * Stop filtering out Multicast V4 packets.
     * @param ifaceName Name of the interface.
     * @return {@code true} if the operation succeeded, {@code false} otherwise
     */
    public boolean stopFilteringMulticastV4Packets(@NonNull String ifaceName) {
        return mSupplicantStaIfaceHal.stopRxFilter(ifaceName)
                && mSupplicantStaIfaceHal.addRxFilter(
                        ifaceName, RX_FILTER_TYPE_V4_MULTICAST)
                && mSupplicantStaIfaceHal.startRxFilter(ifaceName);
    }

    /**
     * Start filtering out Multicast V6 packets
     * @param ifaceName Name of the interface.
     * @return {@code true} if the operation succeeded, {@code false} otherwise
     */
    public boolean startFilteringMulticastV6Packets(@NonNull String ifaceName) {
        return mSupplicantStaIfaceHal.stopRxFilter(ifaceName)
                && mSupplicantStaIfaceHal.removeRxFilter(
                        ifaceName, RX_FILTER_TYPE_V6_MULTICAST)
                && mSupplicantStaIfaceHal.startRxFilter(ifaceName);
    }

    /**
     * Stop filtering out Multicast V6 packets.
     * @param ifaceName Name of the interface.
     * @return {@code true} if the operation succeeded, {@code false} otherwise
     */
    public boolean stopFilteringMulticastV6Packets(@NonNull String ifaceName) {
        return mSupplicantStaIfaceHal.stopRxFilter(ifaceName)
                && mSupplicantStaIfaceHal.addRxFilter(
                        ifaceName, RX_FILTER_TYPE_V6_MULTICAST)
                && mSupplicantStaIfaceHal.startRxFilter(ifaceName);
    }

    public static final int BLUETOOTH_COEXISTENCE_MODE_ENABLED  = 0;
    public static final int BLUETOOTH_COEXISTENCE_MODE_DISABLED = 1;
    public static final int BLUETOOTH_COEXISTENCE_MODE_SENSE    = 2;
    /**
     * Sets the bluetooth coexistence mode.
     *
     * @param ifaceName Name of the interface.
     * @param mode One of {@link #BLUETOOTH_COEXISTENCE_MODE_DISABLED},
     *            {@link #BLUETOOTH_COEXISTENCE_MODE_ENABLED}, or
     *            {@link #BLUETOOTH_COEXISTENCE_MODE_SENSE}.
     * @return Whether the mode was successfully set.
     */
    public boolean setBluetoothCoexistenceMode(@NonNull String ifaceName, int mode) {
        return mSupplicantStaIfaceHal.setBtCoexistenceMode(ifaceName, mode);
    }

    /**
     * Enable or disable Bluetooth coexistence scan mode. When this mode is on,
     * some of the low-level scan parameters used by the driver are changed to
     * reduce interference with A2DP streaming.
     *
     * @param ifaceName Name of the interface.
     * @param setCoexScanMode whether to enable or disable this mode
     * @return {@code true} if the command succeeded, {@code false} otherwise.
     */
    public boolean setBluetoothCoexistenceScanMode(
            @NonNull String ifaceName, boolean setCoexScanMode) {
        return mSupplicantStaIfaceHal.setBtCoexistenceScanModeEnabled(
                ifaceName, setCoexScanMode);
    }

    /**
     * Enable or disable suspend mode optimizations.
     *
     * @param ifaceName Name of the interface.
     * @param enabled true to enable, false otherwise.
     * @return true if request is sent successfully, false otherwise.
     */
    public boolean setSuspendOptimizations(@NonNull String ifaceName, boolean enabled) {
        return mSupplicantStaIfaceHal.setSuspendModeEnabled(ifaceName, enabled);
    }

    /**
     * Set country code for STA interface
     *
     * @param ifaceName Name of the STA interface.
     * @param countryCode 2 byte ASCII string. For ex: US, CA.
     * @return true if request is sent successfully, false otherwise.
     */
    public boolean setStaCountryCode(@NonNull String ifaceName, String countryCode) {
        if (mSupplicantStaIfaceHal.setCountryCode(ifaceName, countryCode)) {
            if (mCountryCodeChangeListener != null) {
                mCountryCodeChangeListener.onSetCountryCodeSucceeded(countryCode);
            }
            return true;
        }
        return false;
    }

    /**
     * Flush all previously configured HLPs.
     *
     * @return true if request is sent successfully, false otherwise.
     */
    public boolean flushAllHlp(@NonNull String ifaceName) {
        return mSupplicantStaIfaceHal.flushAllHlp(ifaceName);
    }

    /**
     * Set FILS HLP packet.
     *
     * @param dst Destination MAC address.
     * @param hlpPacket Hlp Packet data in hex.
     * @return true if request is sent successfully, false otherwise.
     */
    public boolean addHlpReq(@NonNull String ifaceName, MacAddress dst, byte [] hlpPacket) {
        return mSupplicantStaIfaceHal.addHlpReq(ifaceName, dst.toByteArray(), hlpPacket);
    }

    /**
     * Initiate TDLS discover and setup or teardown with the specified peer.
     *
     * @param ifaceName Name of the interface.
     * @param macAddr MAC Address of the peer.
     * @param enable true to start discovery and setup, false to teardown.
     * @return true if request is sent successfully, false otherwise.
     */
    public boolean startTdls(@NonNull String ifaceName, String macAddr, boolean enable) {
        boolean ret = true;
        if (enable) {
            mSupplicantStaIfaceHal.initiateTdlsDiscover(ifaceName, macAddr);
            ret = mSupplicantStaIfaceHal.initiateTdlsSetup(ifaceName, macAddr);
        } else {
            ret = mSupplicantStaIfaceHal.initiateTdlsTeardown(ifaceName, macAddr);
        }
        return ret;
    }

    /**
     * Start WPS pin display operation with the specified peer.
     *
     * @param ifaceName Name of the interface.
     * @param bssid BSSID of the peer.
     * @return true if request is sent successfully, false otherwise.
     */
    public boolean startWpsPbc(@NonNull String ifaceName, String bssid) {
        return mSupplicantStaIfaceHal.startWpsPbc(ifaceName, bssid);
    }

    /**
     * Start WPS pin keypad operation with the specified pin.
     *
     * @param ifaceName Name of the interface.
     * @param pin Pin to be used.
     * @return true if request is sent successfully, false otherwise.
     */
    public boolean startWpsPinKeypad(@NonNull String ifaceName, String pin) {
        return mSupplicantStaIfaceHal.startWpsPinKeypad(ifaceName, pin);
    }

    /**
     * Start WPS pin display operation with the specified peer.
     *
     * @param ifaceName Name of the interface.
     * @param bssid BSSID of the peer.
     * @return new pin generated on success, null otherwise.
     */
    public String startWpsPinDisplay(@NonNull String ifaceName, String bssid) {
        return mSupplicantStaIfaceHal.startWpsPinDisplay(ifaceName, bssid);
    }

    /**
     * Sets whether to use external sim for SIM/USIM processing.
     *
     * @param ifaceName Name of the interface.
     * @param external true to enable, false otherwise.
     * @return true if request is sent successfully, false otherwise.
     */
    public boolean setExternalSim(@NonNull String ifaceName, boolean external) {
        return mSupplicantStaIfaceHal.setExternalSim(ifaceName, external);
    }

    /**
     * Sim auth response types.
     */
    public static final String SIM_AUTH_RESP_TYPE_GSM_AUTH = "GSM-AUTH";
    public static final String SIM_AUTH_RESP_TYPE_UMTS_AUTH = "UMTS-AUTH";
    public static final String SIM_AUTH_RESP_TYPE_UMTS_AUTS = "UMTS-AUTS";

    /**
     * EAP-SIM Error Codes
     */
    public static final int EAP_SIM_NOT_SUBSCRIBED = 1031;
    public static final int EAP_SIM_VENDOR_SPECIFIC_CERT_EXPIRED = 16385;

    /**
     * Send the sim auth response for the currently configured network.
     *
     * @param ifaceName Name of the interface.
     * @param type |GSM-AUTH|, |UMTS-AUTH| or |UMTS-AUTS|.
     * @param response Response params.
     * @return true if succeeds, false otherwise.
     */
    public boolean simAuthResponse(
            @NonNull String ifaceName, String type, String response) {
        if (SIM_AUTH_RESP_TYPE_GSM_AUTH.equals(type)) {
            return mSupplicantStaIfaceHal.sendCurrentNetworkEapSimGsmAuthResponse(
                    ifaceName, response);
        } else if (SIM_AUTH_RESP_TYPE_UMTS_AUTH.equals(type)) {
            return mSupplicantStaIfaceHal.sendCurrentNetworkEapSimUmtsAuthResponse(
                    ifaceName, response);
        } else if (SIM_AUTH_RESP_TYPE_UMTS_AUTS.equals(type)) {
            return mSupplicantStaIfaceHal.sendCurrentNetworkEapSimUmtsAutsResponse(
                    ifaceName, response);
        } else {
            return false;
        }
    }

    /**
     * Send the eap sim gsm auth failure for the currently configured network.
     *
     * @param ifaceName Name of the interface.
     * @return true if succeeds, false otherwise.
     */
    public boolean simAuthFailedResponse(@NonNull String ifaceName) {
        return mSupplicantStaIfaceHal.sendCurrentNetworkEapSimGsmAuthFailure(ifaceName);
    }

    /**
     * Send the eap sim umts auth failure for the currently configured network.
     *
     * @param ifaceName Name of the interface.
     * @return true if succeeds, false otherwise.
     */
    public boolean umtsAuthFailedResponse(@NonNull String ifaceName) {
        return mSupplicantStaIfaceHal.sendCurrentNetworkEapSimUmtsAuthFailure(ifaceName);
    }

    /**
     * Send the eap identity response for the currently configured network.
     *
     * @param ifaceName Name of the interface.
     * @param unencryptedResponse String to send.
     * @param encryptedResponse String to send.
     * @return true if succeeds, false otherwise.
     */
    public boolean simIdentityResponse(@NonNull String ifaceName, String unencryptedResponse,
                                       String encryptedResponse) {
        return mSupplicantStaIfaceHal.sendCurrentNetworkEapIdentityResponse(ifaceName,
                unencryptedResponse, encryptedResponse);
    }

    /**
     * This get anonymous identity from supplicant and returns it as a string.
     *
     * @param ifaceName Name of the interface.
     * @return anonymous identity string if succeeds, null otherwise.
     */
    public String getEapAnonymousIdentity(@NonNull String ifaceName) {
        String anonymousIdentity = mSupplicantStaIfaceHal
                .getCurrentNetworkEapAnonymousIdentity(ifaceName);

        if (TextUtils.isEmpty(anonymousIdentity)) {
            return anonymousIdentity;
        }

        int indexOfDecoration = anonymousIdentity.lastIndexOf('!');
        if (indexOfDecoration >= 0) {
            if (anonymousIdentity.substring(indexOfDecoration).length() < 2) {
                // Invalid identity, shouldn't happen
                Log.e(TAG, "Unexpected anonymous identity: " + anonymousIdentity);
                return null;
            }
            // Truncate RFC 7542 decorated prefix, if exists. Keep only the anonymous identity or
            // pseudonym.
            anonymousIdentity = anonymousIdentity.substring(indexOfDecoration + 1);
        }

        return anonymousIdentity;
    }

    /**
     * Start WPS pin registrar operation with the specified peer and pin.
     *
     * @param ifaceName Name of the interface.
     * @param bssid BSSID of the peer.
     * @param pin Pin to be used.
     * @return true if request is sent successfully, false otherwise.
     */
    public boolean startWpsRegistrar(@NonNull String ifaceName, String bssid, String pin) {
        return mSupplicantStaIfaceHal.startWpsRegistrar(ifaceName, bssid, pin);
    }

    /**
     * Cancels any ongoing WPS requests.
     *
     * @param ifaceName Name of the interface.
     * @return true if request is sent successfully, false otherwise.
     */
    public boolean cancelWps(@NonNull String ifaceName) {
        return mSupplicantStaIfaceHal.cancelWps(ifaceName);
    }

    /**
     * Set WPS device name.
     *
     * @param ifaceName Name of the interface.
     * @param name String to be set.
     * @return true if request is sent successfully, false otherwise.
     */
    public boolean setDeviceName(@NonNull String ifaceName, String name) {
        return mSupplicantStaIfaceHal.setWpsDeviceName(ifaceName, name);
    }

    /**
     * Set WPS device type.
     *
     * @param ifaceName Name of the interface.
     * @param type Type specified as a string. Used format: <categ>-<OUI>-<subcateg>
     * @return true if request is sent successfully, false otherwise.
     */
    public boolean setDeviceType(@NonNull String ifaceName, String type) {
        return mSupplicantStaIfaceHal.setWpsDeviceType(ifaceName, type);
    }

    /**
     * Set WPS config methods
     *
     * @param cfg List of config methods.
     * @return true if request is sent successfully, false otherwise.
     */
    public boolean setConfigMethods(@NonNull String ifaceName, String cfg) {
        return mSupplicantStaIfaceHal.setWpsConfigMethods(ifaceName, cfg);
    }

    /**
     * Set WPS manufacturer.
     *
     * @param ifaceName Name of the interface.
     * @param value String to be set.
     * @return true if request is sent successfully, false otherwise.
     */
    public boolean setManufacturer(@NonNull String ifaceName, String value) {
        return mSupplicantStaIfaceHal.setWpsManufacturer(ifaceName, value);
    }

    /**
     * Set WPS model name.
     *
     * @param ifaceName Name of the interface.
     * @param value String to be set.
     * @return true if request is sent successfully, false otherwise.
     */
    public boolean setModelName(@NonNull String ifaceName, String value) {
        return mSupplicantStaIfaceHal.setWpsModelName(ifaceName, value);
    }

    /**
     * Set WPS model number.
     *
     * @param ifaceName Name of the interface.
     * @param value String to be set.
     * @return true if request is sent successfully, false otherwise.
     */
    public boolean setModelNumber(@NonNull String ifaceName, String value) {
        return mSupplicantStaIfaceHal.setWpsModelNumber(ifaceName, value);
    }

    /**
     * Set WPS serial number.
     *
     * @param ifaceName Name of the interface.
     * @param value String to be set.
     * @return true if request is sent successfully, false otherwise.
     */
    public boolean setSerialNumber(@NonNull String ifaceName, String value) {
        return mSupplicantStaIfaceHal.setWpsSerialNumber(ifaceName, value);
    }

    /**
     * Enable or disable power save mode.
     *
     * @param ifaceName Name of the interface.
     * @param enabled true to enable, false to disable.
     */
    public void setPowerSave(@NonNull String ifaceName, boolean enabled) {
        mSupplicantStaIfaceHal.setPowerSave(ifaceName, enabled);
    }

    /**
     * Enable or disable low latency mode.
     *
     * @param enabled true to enable, false to disable.
     * @return true on success, false on failure
     */
    public boolean setLowLatencyMode(boolean enabled) {
        return mWifiVendorHal.setLowLatencyMode(enabled);
    }

    /**
     * Set concurrency priority between P2P & STA operations.
     *
     * @param isStaHigherPriority Set to true to prefer STA over P2P during concurrency operations,
     *                            false otherwise.
     * @return true if request is sent successfully, false otherwise.
     */
    public boolean setConcurrencyPriority(boolean isStaHigherPriority) {
        return mSupplicantStaIfaceHal.setConcurrencyPriority(isStaHigherPriority);
    }

    /**
     * Enable/Disable auto reconnect functionality in wpa_supplicant.
     *
     * @param ifaceName Name of the interface.
     * @param enable true to enable auto reconnecting, false to disable.
     * @return true if request is sent successfully, false otherwise.
     */
    public boolean enableStaAutoReconnect(@NonNull String ifaceName, boolean enable) {
        return mSupplicantStaIfaceHal.enableAutoReconnect(ifaceName, enable);
    }

    /**
     * Add the provided network configuration to wpa_supplicant and initiate connection to it.
     * This method does the following:
     * 1. Abort any ongoing scan to unblock the connection request.
     * 2. Remove any existing network in wpa_supplicant(This implicitly triggers disconnect).
     * 3. Add a new network to wpa_supplicant.
     * 4. Save the provided configuration to wpa_supplicant.
     * 5. Select the new network in wpa_supplicant.
     * 6. Triggers reconnect command to wpa_supplicant.
     *
     * @param ifaceName Name of the interface.
     * @param configuration WifiConfiguration parameters for the provided network.
     * @return {@code true} if it succeeds, {@code false} otherwise
     */
    public boolean connectToNetwork(@NonNull String ifaceName, WifiConfiguration configuration) {
        // Abort ongoing scan before connect() to unblock connection request.
        mWifiCondManager.abortScan(ifaceName);
        return mSupplicantStaIfaceHal.connectToNetwork(ifaceName, configuration);
    }

    /**
     * Initiates roaming to the already configured network in wpa_supplicant. If the network
     * configuration provided does not match the already configured network, then this triggers
     * a new connection attempt (instead of roam).
     * 1. Abort any ongoing scan to unblock the roam request.
     * 2. First check if we're attempting to connect to the same network as we currently have
     * configured.
     * 3. Set the new bssid for the network in wpa_supplicant.
     * 4. Triggers reassociate command to wpa_supplicant.
     *
     * @param ifaceName Name of the interface.
     * @param configuration WifiConfiguration parameters for the provided network.
     * @return {@code true} if it succeeds, {@code false} otherwise
     */
    public boolean roamToNetwork(@NonNull String ifaceName, WifiConfiguration configuration) {
        // Abort ongoing scan before connect() to unblock roaming request.
        mWifiCondManager.abortScan(ifaceName);
        return mSupplicantStaIfaceHal.roamToNetwork(ifaceName, configuration);
    }

    /**
     * Remove all the networks.
     *
     * @param ifaceName Name of the interface.
     * @return {@code true} if it succeeds, {@code false} otherwise
     */
    public boolean removeAllNetworks(@NonNull String ifaceName) {
        return mSupplicantStaIfaceHal.removeAllNetworks(ifaceName);
    }

    /**
     * Disable the currently configured network in supplicant
     *
     * @param ifaceName Name of the interface.
     */
    public boolean disableNetwork(@NonNull String ifaceName) {
        return mSupplicantStaIfaceHal.disableCurrentNetwork(ifaceName);
    }

    /**
     * Set the BSSID for the currently configured network in wpa_supplicant.
     *
     * @param ifaceName Name of the interface.
     * @return true if successful, false otherwise.
     */
    public boolean setNetworkBSSID(@NonNull String ifaceName, String bssid) {
        return mSupplicantStaIfaceHal.setCurrentNetworkBssid(ifaceName, bssid);
    }

    /**
     * Initiate ANQP query.
     *
     * @param ifaceName Name of the interface.
     * @param bssid BSSID of the AP to be queried
     * @param anqpIds Set of anqp IDs.
     * @param hs20Subtypes Set of HS20 subtypes.
     * @return true on success, false otherwise.
     */
    public boolean requestAnqp(
            @NonNull String ifaceName, String bssid, Set<Integer> anqpIds,
            Set<Integer> hs20Subtypes) {
        if (bssid == null || ((anqpIds == null || anqpIds.isEmpty())
                && (hs20Subtypes == null || hs20Subtypes.isEmpty()))) {
            Log.e(TAG, "Invalid arguments for ANQP request.");
            return false;
        }
        ArrayList<Short> anqpIdList = new ArrayList<>();
        for (Integer anqpId : anqpIds) {
            anqpIdList.add(anqpId.shortValue());
        }
        ArrayList<Integer> hs20SubtypeList = new ArrayList<>();
        hs20SubtypeList.addAll(hs20Subtypes);
        return mSupplicantStaIfaceHal.initiateAnqpQuery(
                ifaceName, bssid, anqpIdList, hs20SubtypeList);
    }

    /**
     * Request a passpoint icon file |filename| from the specified AP |bssid|.
     *
     * @param ifaceName Name of the interface.
     * @param bssid BSSID of the AP
     * @param fileName name of the icon file
     * @return true if request is sent successfully, false otherwise
     */
    public boolean requestIcon(@NonNull String ifaceName, String  bssid, String fileName) {
        if (bssid == null || fileName == null) {
            Log.e(TAG, "Invalid arguments for Icon request.");
            return false;
        }
        return mSupplicantStaIfaceHal.initiateHs20IconQuery(ifaceName, bssid, fileName);
    }

    /**
     * Initiate Venue URL ANQP query.
     *
     * @param ifaceName Name of the interface.
     * @param bssid BSSID of the AP to be queried
     * @return true on success, false otherwise.
     */
    public boolean requestVenueUrlAnqp(
            @NonNull String ifaceName, String bssid) {
        if (bssid == null) {
            Log.e(TAG, "Invalid arguments for Venue URL ANQP request.");
            return false;
        }
        return mSupplicantStaIfaceHal.initiateVenueUrlAnqpQuery(ifaceName, bssid);
    }

    /**
     * Get the currently configured network's WPS NFC token.
     *
     * @param ifaceName Name of the interface.
     * @return Hex string corresponding to the WPS NFC token.
     */
    public String getCurrentNetworkWpsNfcConfigurationToken(@NonNull String ifaceName) {
        return mSupplicantStaIfaceHal.getCurrentNetworkWpsNfcConfigurationToken(ifaceName);
    }

    /**
     * Clean HAL cached data for |networkId|.
     *
     * @param networkId network id of the network to be removed from supplicant.
     */
    public void removeNetworkCachedData(int networkId) {
        mSupplicantStaIfaceHal.removeNetworkCachedData(networkId);
    }

    /** Clear HAL cached data for |networkId| if MAC address is changed.
     *
     * @param networkId network id of the network to be checked.
     * @param curMacAddress current MAC address
     */
    public void removeNetworkCachedDataIfNeeded(int networkId, MacAddress curMacAddress) {
        mSupplicantStaIfaceHal.removeNetworkCachedDataIfNeeded(networkId, curMacAddress);
    }

    /*
     * DPP
     */

    /**
     * Adds a DPP peer URI to the URI list.
     *
     * @param ifaceName Interface name
     * @param uri Bootstrap (URI) string (e.g. DPP:....)
     * @return ID, or -1 for failure
     */
    public int addDppPeerUri(@NonNull String ifaceName, @NonNull String uri) {
        return mSupplicantStaIfaceHal.addDppPeerUri(ifaceName, uri);
    }

    /**
     * Removes a DPP URI to the URI list given an ID.
     *
     * @param ifaceName Interface name
     * @param bootstrapId Bootstrap (URI) ID
     * @return true when operation is successful, or false for failure
     */
    public boolean removeDppUri(@NonNull String ifaceName, int bootstrapId)  {
        return mSupplicantStaIfaceHal.removeDppUri(ifaceName, bootstrapId);
    }

    /**
     * Stops/aborts DPP Initiator request
     *
     * @param ifaceName Interface name
     * @return true when operation is successful, or false for failure
     */
    public boolean stopDppInitiator(@NonNull String ifaceName)  {
        return mSupplicantStaIfaceHal.stopDppInitiator(ifaceName);
    }

    /**
     * Starts DPP Configurator-Initiator request
     *
     * @param ifaceName Interface name
     * @param peerBootstrapId Peer's bootstrap (URI) ID
     * @param ownBootstrapId Own bootstrap (URI) ID - Optional, 0 for none
     * @param ssid SSID of the selected network
     * @param password Password of the selected network, or
     * @param psk PSK of the selected network in hexadecimal representation
     * @param netRole The network role of the enrollee (STA or AP)
     * @param securityAkm Security AKM to use: PSK, SAE
     * @return true when operation is successful, or false for failure
     */
    public boolean startDppConfiguratorInitiator(@NonNull String ifaceName, int peerBootstrapId,
            int ownBootstrapId, @NonNull String ssid, String password, String psk,
            int netRole, int securityAkm, byte[] privEcKey)  {
        return mSupplicantStaIfaceHal.startDppConfiguratorInitiator(ifaceName, peerBootstrapId,
                ownBootstrapId, ssid, password, psk, netRole, securityAkm, privEcKey);
    }

    /**
     * Starts DPP Enrollee-Initiator request
     *
     * @param ifaceName Interface name
     * @param peerBootstrapId Peer's bootstrap (URI) ID
     * @param ownBootstrapId Own bootstrap (URI) ID - Optional, 0 for none
     * @return true when operation is successful, or false for failure
     */
    public boolean startDppEnrolleeInitiator(@NonNull String ifaceName, int peerBootstrapId,
            int ownBootstrapId)  {
        return mSupplicantStaIfaceHal.startDppEnrolleeInitiator(ifaceName, peerBootstrapId,
                ownBootstrapId);
    }

    /**
     * Callback to notify about DPP success, failure and progress events.
     */
    public interface DppEventCallback {
        /**
         * Called when local DPP Enrollee successfully receives a new Wi-Fi configuration from the
         * peer DPP configurator.
         *
         * @param newWifiConfiguration New Wi-Fi configuration received from the configurator
         * @param connStatusRequested Flag to indicate that the configurator requested
         *                            connection status
         */
        void onSuccessConfigReceived(WifiConfiguration newWifiConfiguration,
                boolean connStatusRequested);

        /**
         * DPP Success event.
         *
         * @param dppStatusCode Status code of the success event.
         */
        void onSuccess(int dppStatusCode);

        /**
         * DPP Progress event.
         *
         * @param dppStatusCode Status code of the progress event.
         */
        void onProgress(int dppStatusCode);

        /**
         * DPP Failure event.
         *
         * @param dppStatusCode Status code of the failure event.
         * @param ssid SSID of the network the Enrollee tried to connect to.
         * @param channelList List of channels the Enrollee scanned for the network.
         * @param bandList List of bands the Enrollee supports.
         */
        void onFailure(int dppStatusCode, String ssid, String channelList, int[] bandList);

        /**
         * DPP Configurator Private keys update.
         *
         * @param key Configurator's private EC key.
         */
        void onDppConfiguratorKeyUpdate(byte[] key);

        /**
         * Indicates that DPP connection status result frame is sent
         *
         * @param result DPP Status value indicating the result of a connection attempt.
         */
        void onConnectionStatusResultSent(int result);
    }

    /**
     * Class to get generated bootstrap info for DPP responder operation.
     */
    public static class DppBootstrapQrCodeInfo {
        public int bootstrapId;
        public int listenChannel;
        public String uri = new String();
        DppBootstrapQrCodeInfo() {
            bootstrapId = -1;
            listenChannel = -1;
        }
    }

    /**
     * Generate DPP bootstrap Information:Bootstrap ID, DPP URI and the listen channel.
     *
     * @param ifaceName Interface name
     * @param deviceInfo Device specific info to attach in DPP URI.
     * @param dppCurve Elliptic curve cryptography type used to generate DPP
     *                 public/private key pair.
     * @return ID, or -1 for failure
     */
    public DppBootstrapQrCodeInfo generateDppBootstrapInfoForResponder(@NonNull String ifaceName,
            String deviceInfo, int dppCurve) {
        return mSupplicantStaIfaceHal.generateDppBootstrapInfoForResponder(ifaceName,
                getMacAddress(ifaceName), deviceInfo, dppCurve);
    }

    /**
     * start DPP Enrollee responder mode.
     *
     * @param ifaceName Interface name
     * @param listenChannel Listen channel to wait for DPP authentication request.
     * @return ID, or -1 for failure
     */
    public boolean startDppEnrolleeResponder(@NonNull String ifaceName, int listenChannel) {
        return mSupplicantStaIfaceHal.startDppEnrolleeResponder(ifaceName, listenChannel);
    }

    /**
     * Stops/aborts DPP Responder request
     *
     * @param ifaceName Interface name
     * @param ownBootstrapId Bootstrap (URI) ID
     * @return true when operation is successful, or false for failure
     */
    public boolean stopDppResponder(@NonNull String ifaceName, int ownBootstrapId)  {
        return mSupplicantStaIfaceHal.stopDppResponder(ifaceName, ownBootstrapId);
    }


    /**
     * Registers DPP event callbacks.
     *
     * @param dppEventCallback Callback object.
     */
    public void registerDppEventCallback(DppEventCallback dppEventCallback) {
        mSupplicantStaIfaceHal.registerDppCallback(dppEventCallback);
    }

    /**
     * Check whether Supplicant is using the AIDL HAL service.
     *
     * @return true if the Supplicant is using the AIDL service, false otherwise.
     */
    public boolean isSupplicantUsingAidlService() {
        return mSupplicantStaIfaceHal.isAidlService();
    }

    /**
     * Check whether the Supplicant AIDL service is running at least the expected version.
     *
     * @param expectedVersion Version number to check.
     * @return true if the AIDL service is available and >= the expected version, false otherwise.
     */
    public boolean isSupplicantAidlServiceVersionAtLeast(int expectedVersion) {
        return mSupplicantStaIfaceHal.isAidlServiceVersionAtLeast(expectedVersion);
    }

    /********************************************************
     * Vendor HAL operations
     ********************************************************/
    /**
     * Callback to notify vendor HAL death.
     */
    public interface VendorHalDeathEventHandler {
        /**
         * Invoked when the vendor HAL dies.
         */
        void onDeath();
    }

    /**
     * Callback to notify when vendor HAL detects that a change in radio mode.
     */
    public interface VendorHalRadioModeChangeEventHandler {
        /**
         * Invoked when the vendor HAL detects a change to MCC mode.
         * MCC (Multi channel concurrency) = Multiple interfaces are active on the same band,
         * different channels, same radios.
         *
         * @param band Band on which MCC is detected (specified by one of the
         *             WifiScanner.WIFI_BAND_* constants)
         */
        void onMcc(int band);
        /**
         * Invoked when the vendor HAL detects a change to SCC mode.
         * SCC (Single channel concurrency) = Multiple interfaces are active on the same band, same
         * channels, same radios.
         *
         * @param band Band on which SCC is detected (specified by one of the
         *             WifiScanner.WIFI_BAND_* constants)
         */
        void onScc(int band);
        /**
         * Invoked when the vendor HAL detects a change to SBS mode.
         * SBS (Single Band Simultaneous) = Multiple interfaces are active on the same band,
         * different channels, different radios.
         *
         * @param band Band on which SBS is detected (specified by one of the
         *             WifiScanner.WIFI_BAND_* constants)
         */
        void onSbs(int band);
        /**
         * Invoked when the vendor HAL detects a change to DBS mode.
         * DBS (Dual Band Simultaneous) = Multiple interfaces are active on the different bands,
         * different channels, different radios.
         */
        void onDbs();
    }

    /**
     * Tests whether the HAL is running or not
     */
    public boolean isHalStarted() {
        return mWifiVendorHal.isHalStarted();
    }

    /**
     * Tests whether the HAL is supported or not
     */
    public boolean isHalSupported() {
        return mWifiVendorHal.isVendorHalSupported();
    }

    // TODO: Change variable names to camel style.
    public static class ScanCapabilities {
        public int  max_scan_cache_size;
        public int  max_scan_buckets;
        public int  max_ap_cache_per_scan;
        public int  max_rssi_sample_size;
        public int  max_scan_reporting_threshold;
    }

    /**
     * Gets the scan capabilities
     *
     * @param ifaceName Name of the interface.
     * @param capabilities object to be filled in
     * @return true for success. false for failure
     */
    public boolean getBgScanCapabilities(
            @NonNull String ifaceName, ScanCapabilities capabilities) {
        return mWifiVendorHal.getBgScanCapabilities(ifaceName, capabilities);
    }

    public static class ChannelSettings {
        public int frequency;
        public int dwell_time_ms;
        public boolean passive;
    }

    public static class BucketSettings {
        public int bucket;
        public int band;
        public int period_ms;
        public int max_period_ms;
        public int step_count;
        public int report_events;
        public int num_channels;
        public ChannelSettings[] channels;
    }

    /**
     * Network parameters for hidden networks to be scanned for.
     */
    public static class HiddenNetwork {
        public String ssid;

        @Override
        public boolean equals(Object otherObj) {
            if (this == otherObj) {
                return true;
            } else if (otherObj == null || getClass() != otherObj.getClass()) {
                return false;
            }
            HiddenNetwork other = (HiddenNetwork) otherObj;
            return Objects.equals(ssid, other.ssid);
        }

        @Override
        public int hashCode() {
            return Objects.hash(ssid);
        }
    }

    public static class ScanSettings {
        /**
         * Type of scan to perform. One of {@link WifiScanner#SCAN_TYPE_LOW_LATENCY},
         * {@link WifiScanner#SCAN_TYPE_LOW_POWER} or {@link WifiScanner#SCAN_TYPE_HIGH_ACCURACY}.
         */
        @WifiAnnotations.ScanType
        public int scanType;
        public int base_period_ms;
        public int max_ap_per_scan;
        public int report_threshold_percent;
        public int report_threshold_num_scans;
        public int num_buckets;
        public boolean enable6GhzRnr;
        /* Not used for bg scans. Only works for single scans. */
        public HiddenNetwork[] hiddenNetworks;
        public BucketSettings[] buckets;
        public byte[] vendorIes;
    }

    /**
     * Network parameters to start PNO scan.
     */
    public static class PnoNetwork {
        public String ssid;
        public byte flags;
        public byte auth_bit_field;
        public int[] frequencies;

        @Override
        public boolean equals(Object otherObj) {
            if (this == otherObj) {
                return true;
            } else if (otherObj == null || getClass() != otherObj.getClass()) {
                return false;
            }
            PnoNetwork other = (PnoNetwork) otherObj;
            return ((Objects.equals(ssid, other.ssid)) && (flags == other.flags)
                    && (auth_bit_field == other.auth_bit_field))
                    && Arrays.equals(frequencies, other.frequencies);
        }

        @Override
        public int hashCode() {
            return Objects.hash(ssid, flags, auth_bit_field, Arrays.hashCode(frequencies));
        }

        android.net.wifi.nl80211.PnoNetwork toNativePnoNetwork() {
            android.net.wifi.nl80211.PnoNetwork nativePnoNetwork =
                    new android.net.wifi.nl80211.PnoNetwork();
            nativePnoNetwork.setHidden(
                    (flags & WifiScanner.PnoSettings.PnoNetwork.FLAG_DIRECTED_SCAN) != 0);
            try {
                nativePnoNetwork.setSsid(
                        NativeUtil.byteArrayFromArrayList(NativeUtil.decodeSsid(ssid)));
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Illegal argument " + ssid, e);
                return null;
            }
            nativePnoNetwork.setFrequenciesMhz(frequencies);
            return nativePnoNetwork;
        }
    }

    /**
     * Parameters to start PNO scan. This holds the list of networks which are going to used for
     * PNO scan.
     */
    public static class PnoSettings {
        public int min5GHzRssi;
        public int min24GHzRssi;
        public int min6GHzRssi;
        public int periodInMs;
        public int scanIterations;
        public int scanIntervalMultiplier;
        public boolean isConnected;
        public PnoNetwork[] networkList;

        android.net.wifi.nl80211.PnoSettings toNativePnoSettings() {
            android.net.wifi.nl80211.PnoSettings nativePnoSettings =
                    new android.net.wifi.nl80211.PnoSettings();
            nativePnoSettings.setIntervalMillis(periodInMs);
            nativePnoSettings.setMin2gRssiDbm(min24GHzRssi);
            nativePnoSettings.setMin5gRssiDbm(min5GHzRssi);
            nativePnoSettings.setMin6gRssiDbm(min6GHzRssi);
            if (SdkLevel.isAtLeastU()) {
                nativePnoSettings.setScanIterations(scanIterations);
                nativePnoSettings.setScanIntervalMultiplier(scanIntervalMultiplier);
            }

            List<android.net.wifi.nl80211.PnoNetwork> pnoNetworks = new ArrayList<>();
            if (networkList != null) {
                for (PnoNetwork network : networkList) {
                    android.net.wifi.nl80211.PnoNetwork nativeNetwork =
                            network.toNativePnoNetwork();
                    if (nativeNetwork != null) {
                        pnoNetworks.add(nativeNetwork);
                    }
                }
            }
            nativePnoSettings.setPnoNetworks(pnoNetworks);
            return nativePnoSettings;
        }
    }

    public static interface ScanEventHandler {
        /**
         * Called for each AP as it is found with the entire contents of the beacon/probe response.
         * Only called when WifiScanner.REPORT_EVENT_FULL_SCAN_RESULT is specified.
         */
        void onFullScanResult(ScanResult fullScanResult, int bucketsScanned);
        /**
         * Callback on an event during a gscan scan.
         * See WifiNative.WIFI_SCAN_* for possible values.
         */
        void onScanStatus(int event);
        /**
         * Called with the current cached scan results when gscan is paused.
         */
        void onScanPaused(WifiScanner.ScanData[] data);
        /**
         * Called with the current cached scan results when gscan is resumed.
         */
        void onScanRestarted();
        /**
         * Callback to notify when the scan request fails.
         * See WifiScanner.REASON_* for possible values.
         */
        void onScanRequestFailed(int errorCode);
    }

    /**
     * Handler to notify the occurrence of various events during PNO scan.
     */
    public interface PnoEventHandler {
        /**
         * Callback to notify when one of the shortlisted networks is found during PNO scan.
         * @param results List of Scan results received.
         */
        void onPnoNetworkFound(ScanResult[] results);

        /**
         * Callback to notify when the PNO scan schedule fails.
         */
        void onPnoScanFailed();
    }

    public static final int WIFI_SCAN_RESULTS_AVAILABLE = 0;
    public static final int WIFI_SCAN_THRESHOLD_NUM_SCANS = 1;
    public static final int WIFI_SCAN_THRESHOLD_PERCENT = 2;
    public static final int WIFI_SCAN_FAILED = 3;

    /**
     * Starts a background scan.
     * Any ongoing scan will be stopped first
     *
     * @param ifaceName Name of the interface.
     * @param settings     to control the scan
     * @param eventHandler to call with the results
     * @return true for success
     */
    public boolean startBgScan(
            @NonNull String ifaceName, ScanSettings settings, ScanEventHandler eventHandler) {
        return mWifiVendorHal.startBgScan(ifaceName, settings, eventHandler);
    }

    /**
     * Stops any ongoing backgound scan
     * @param ifaceName Name of the interface.
     */
    public void stopBgScan(@NonNull String ifaceName) {
        mWifiVendorHal.stopBgScan(ifaceName);
    }

    /**
     * Pauses an ongoing backgound scan
     * @param ifaceName Name of the interface.
     */
    public void pauseBgScan(@NonNull String ifaceName) {
        mWifiVendorHal.pauseBgScan(ifaceName);
    }

    /**
     * Restarts a paused scan
     * @param ifaceName Name of the interface.
     */
    public void restartBgScan(@NonNull String ifaceName) {
        mWifiVendorHal.restartBgScan(ifaceName);
    }

    /**
     * Gets the latest scan results received.
     * @param ifaceName Name of the interface.
     */
    public WifiScanner.ScanData[] getBgScanResults(@NonNull String ifaceName) {
        return mWifiVendorHal.getBgScanResults(ifaceName);
    }

    /**
     * Sets whether global location mode is enabled.
     */
    public void setLocationModeEnabled(boolean enabled) {
        if (!mIsLocationModeEnabled && enabled) {
            mLastLocationModeEnabledTimeMs = SystemClock.elapsedRealtime();
        }
        Log.d(TAG, "mIsLocationModeEnabled " + enabled
                + " mLastLocationModeEnabledTimeMs " + mLastLocationModeEnabledTimeMs);
        mIsLocationModeEnabled = enabled;
    }

    @NonNull
    private ScanResult[] getCachedScanResultsFilteredByLocationModeEnabled(
            @NonNull ScanResult[] scanResults) {
        List<ScanResult> resultList = new ArrayList<ScanResult>();
        for (ScanResult scanResult : scanResults) {
            if (mIsLocationModeEnabled
                     && scanResult.timestamp >=  mLastLocationModeEnabledTimeMs * 1000) {
                resultList.add(scanResult);
            }
        }
        return resultList.toArray(new ScanResult[0]);
    }

    /**
     * Gets the cached scan data from the given client interface
     */
    @Nullable
    ScanData getCachedScanResults(String ifaceName) {
        ScanData scanData = mWifiVendorHal.getCachedScanData(ifaceName);
        if (scanData == null || scanData.getResults() == null) {
            return null;
        }
        ScanResult[] results = getCachedScanResultsFilteredByLocationModeEnabled(
                scanData.getResults());
        return new ScanData(0, 0, 0, scanData.getScannedBands(), results);
    }

    /**
     * Gets the cached scan data from all client interfaces
     */
    @NonNull
    public ScanData getCachedScanResultsFromAllClientIfaces() {
        ScanData consolidatedScanData = new ScanData();
        Set<String> ifaceNames = getClientInterfaceNames();
        for (String ifaceName : ifaceNames) {
            ScanData scanData = getCachedScanResults(ifaceName);
            if (scanData == null) {
                continue;
            }
            consolidatedScanData.addResults(scanData.getResults());
        }
        return consolidatedScanData;
    }

    /**
     * Gets the latest link layer stats
     * @param ifaceName Name of the interface.
     */
    public WifiLinkLayerStats getWifiLinkLayerStats(@NonNull String ifaceName) {
        WifiLinkLayerStats stats = mWifiVendorHal.getWifiLinkLayerStats(ifaceName);
        if (stats != null) {
            stats.aggregateLinkLayerStats();
        }
        return stats;
    }

    /**
     * Gets the usable channels
     * @param band one of the {@code WifiScanner#WIFI_BAND_*} constants.
     * @param mode bitmask of {@code WifiAvailablechannel#OP_MODE_*} constants.
     * @param filter bitmask of filters (regulatory, coex, concurrency).
     *
     * @return list of channels
     */
    public List<WifiAvailableChannel> getUsableChannels(
            @WifiScanner.WifiBand int band,
            @WifiAvailableChannel.OpMode int mode,
            @WifiAvailableChannel.Filter int filter) {
        return mWifiVendorHal.getUsableChannels(band, mode, filter);
    }
    /**
     * Returns whether the device supports the requested
     * {@link HalDeviceManager.HdmIfaceTypeForCreation} combo.
     */
    public boolean canDeviceSupportCreateTypeCombo(SparseArray<Integer> combo) {
        synchronized (mLock) {
            return mWifiVendorHal.canDeviceSupportCreateTypeCombo(combo);
        }
    }

    /**
     * Returns whether STA + AP concurrency is supported or not.
     */
    public boolean isStaApConcurrencySupported() {
        synchronized (mLock) {
            return mWifiVendorHal.canDeviceSupportCreateTypeCombo(
                    new SparseArray<Integer>() {{
                            put(HDM_CREATE_IFACE_STA, 1);
                            put(HDM_CREATE_IFACE_AP, 1);
                    }});
        }
    }

    /**
     * Returns whether STA + STA concurrency is supported or not.
     */
    public boolean isStaStaConcurrencySupported() {
        synchronized (mLock) {
            return mWifiVendorHal.canDeviceSupportCreateTypeCombo(
                    new SparseArray<Integer>() {{
                            put(HDM_CREATE_IFACE_STA, 2);
                    }});
        }
    }

    /**
     * Returns whether P2p + STA concurrency is supported or not.
     */
    public boolean isP2pStaConcurrencySupported() {
        synchronized (mLock) {
            return mWifiVendorHal.canDeviceSupportCreateTypeCombo(
                    new SparseArray<Integer>() {{
                            put(HDM_CREATE_IFACE_STA, 1);
                            put(HDM_CREATE_IFACE_P2P, 1);
                    }});
        }
    }

    /**
     * Returns whether a new AP iface can be created or not.
     */
    public boolean isItPossibleToCreateApIface(@NonNull WorkSource requestorWs) {
        synchronized (mLock) {
            if (!isHalStarted()) {
                return canDeviceSupportCreateTypeCombo(
                        new SparseArray<Integer>() {{
                            put(HDM_CREATE_IFACE_AP, 1);
                        }});
            }
            return mWifiVendorHal.isItPossibleToCreateApIface(requestorWs);
        }
    }

    /**
     * Returns whether a new AP iface can be created or not.
     */
    public boolean isItPossibleToCreateBridgedApIface(@NonNull WorkSource requestorWs) {
        synchronized (mLock) {
            if (!isHalStarted()) {
                return canDeviceSupportCreateTypeCombo(
                        new SparseArray<Integer>() {{
                            put(HDM_CREATE_IFACE_AP_BRIDGE, 1);
                        }});
            }
            return mWifiVendorHal.isItPossibleToCreateBridgedApIface(requestorWs);
        }
    }

    /**
     * Returns whether creating a single AP does not require destroying an existing iface, but
     * creating a bridged AP does.
     */
    public boolean shouldDowngradeToSingleApForConcurrency(@NonNull WorkSource requestorWs) {
        synchronized (mLock) {
            if (!mWifiVendorHal.isHalStarted()) {
                return false;
            }
            return !mWifiVendorHal.canDeviceSupportAdditionalIface(HDM_CREATE_IFACE_AP_BRIDGE,
                    requestorWs)
                    && mWifiVendorHal.canDeviceSupportAdditionalIface(HDM_CREATE_IFACE_AP,
                    requestorWs);
        }
    }

    /**
     * Returns whether a new STA iface can be created or not.
     */
    public boolean isItPossibleToCreateStaIface(@NonNull WorkSource requestorWs) {
        synchronized (mLock) {
            if (!isHalStarted()) {
                return canDeviceSupportCreateTypeCombo(
                        new SparseArray<Integer>() {{
                            put(HDM_CREATE_IFACE_STA, 1);
                        }});
            }
            return mWifiVendorHal.isItPossibleToCreateStaIface(requestorWs);
        }
    }

    /**
     * Set primary connection when multiple STA ifaces are active.
     *
     * @param ifaceName Name of the interface.
     * @return true for success
     */
    public boolean setMultiStaPrimaryConnection(@NonNull String ifaceName) {
        synchronized (mLock) {
            return mWifiVendorHal.setMultiStaPrimaryConnection(ifaceName);
        }
    }

    /**
     * Multi STA use case flags.
     */
    public static final int DUAL_STA_TRANSIENT_PREFER_PRIMARY = 0;
    public static final int DUAL_STA_NON_TRANSIENT_UNBIASED = 1;

    @IntDef({DUAL_STA_TRANSIENT_PREFER_PRIMARY, DUAL_STA_NON_TRANSIENT_UNBIASED})
    @Retention(RetentionPolicy.SOURCE)
    public @interface MultiStaUseCase{}

    /**
     * Set use-case when multiple STA ifaces are active.
     *
     * @param useCase one of the use cases.
     * @return true for success
     */
    public boolean setMultiStaUseCase(@MultiStaUseCase int useCase) {
        synchronized (mLock) {
            return mWifiVendorHal.setMultiStaUseCase(useCase);
        }
    }

    /**
     * Get the supported features
     *
     * @param ifaceName Name of the interface.
     * @return bitmask defined by WifiManager.WIFI_FEATURE_*
     */
    public long getSupportedFeatureSet(String ifaceName) {
        synchronized (mLock) {
            long featureSet = 0;
            // First get the complete feature set stored in config store when supplicant was
            // started
            featureSet = getCompleteFeatureSetFromConfigStore();
            // Include the feature set saved in interface class. This is to make sure that
            // framework is returning the feature set for SoftAp only products and multi-chip
            // products.
            if (ifaceName != null) {
                Iface iface = mIfaceMgr.getIface(ifaceName);
                if (iface != null) {
                    featureSet |= iface.featureSet;
                }
            }
            return featureSet;
        }
    }

    /**
     * Get the supported bands for STA mode.
     * @return supported bands
     */
    public @WifiScanner.WifiBand int getSupportedBandsForSta(String ifaceName) {
        synchronized (mLock) {
            if (ifaceName != null) {
                Iface iface = mIfaceMgr.getIface(ifaceName);
                if (iface != null) {
                    return iface.bandsSupported;
                }
            }
            return WifiScanner.WIFI_BAND_UNSPECIFIED;
        }
    }

    /**
     * Get the supported features
     *
     * @param ifaceName Name of the interface.
     * @return bitmask defined by WifiManager.WIFI_FEATURE_*
     */
    private long getSupportedFeatureSetInternal(@NonNull String ifaceName) {
        long featureSet = mSupplicantStaIfaceHal.getAdvancedCapabilities(ifaceName)
                | mWifiVendorHal.getSupportedFeatureSet(ifaceName)
                | mSupplicantStaIfaceHal.getWpaDriverFeatureSet(ifaceName);
        if (SdkLevel.isAtLeastT()) {
            if (((featureSet & WifiManager.WIFI_FEATURE_DPP) != 0)
                    && mContext.getResources().getBoolean(R.bool.config_wifiDppAkmSupported)) {
                // Set if DPP is filled by supplicant and DPP AKM is enabled by overlay.
                featureSet |= WifiManager.WIFI_FEATURE_DPP_AKM;
                Log.v(TAG, ": DPP AKM supported");
            }
        }
        Bundle twtCapabilities = mWifiVendorHal.getTwtCapabilities(ifaceName);
        if (twtCapabilities != null) mCachedTwtCapabilities.put(ifaceName, twtCapabilities);
        return featureSet;
    }

    private void updateSupportedBandForStaInternal(Iface iface) {
        List<WifiAvailableChannel> usableChannelList =
                mWifiVendorHal.getUsableChannels(WifiScanner.WIFI_BAND_24_5_WITH_DFS_6_60_GHZ,
                        WifiAvailableChannel.OP_MODE_STA,
                        WifiAvailableChannel.FILTER_REGULATORY);
        int bands = 0;
        if (usableChannelList == null) {
            // If HAL doesn't support getUsableChannels then check wificond
            if (getChannelsForBand(WifiScanner.WIFI_BAND_24_GHZ).length > 0) {
                bands |= WifiScanner.WIFI_BAND_24_GHZ;
            }
            if (getChannelsForBand(WifiScanner.WIFI_BAND_5_GHZ).length > 0) {
                bands |= WifiScanner.WIFI_BAND_5_GHZ;
            }
            if (getChannelsForBand(WifiScanner.WIFI_BAND_6_GHZ).length > 0) {
                bands |= WifiScanner.WIFI_BAND_6_GHZ;
            }
            if (getChannelsForBand(WifiScanner.WIFI_BAND_60_GHZ).length > 0) {
                bands |= WifiScanner.WIFI_BAND_60_GHZ;
            }
        } else {
            for (int i = 0; i < usableChannelList.size(); i++) {
                int frequency = usableChannelList.get(i).getFrequencyMhz();
                if (ScanResult.is24GHz(frequency)) {
                    bands |= WifiScanner.WIFI_BAND_24_GHZ;
                } else if (ScanResult.is5GHz(frequency)) {
                    bands |= WifiScanner.WIFI_BAND_5_GHZ;
                } else if (ScanResult.is6GHz(frequency)) {
                    bands |= WifiScanner.WIFI_BAND_6_GHZ;
                } else if (ScanResult.is60GHz(frequency)) {
                    bands |= WifiScanner.WIFI_BAND_60_GHZ;
                }
            }
        }
        if (mVerboseLoggingEnabled) {
            Log.i(TAG, "updateSupportedBandForStaInternal " + iface.name + " : 0x"
                    + Integer.toHexString(bands));
        }
        iface.bandsSupported = bands;
    }

    /**
     * Class to retrieve connection capability parameters after association
     */
    public static class ConnectionCapabilities {
        public @WifiAnnotations.WifiStandard int wifiStandard;
        public int channelBandwidth;
        public int maxNumberTxSpatialStreams;
        public int maxNumberRxSpatialStreams;
        public boolean is11bMode;
        /** Indicates the AP support for TID-to-link mapping negotiation. */
        public boolean apTidToLinkMapNegotiationSupported;
        public @NonNull List<OuiKeyedData> vendorData;
        ConnectionCapabilities() {
            wifiStandard = ScanResult.WIFI_STANDARD_UNKNOWN;
            channelBandwidth = ScanResult.CHANNEL_WIDTH_20MHZ;
            maxNumberTxSpatialStreams = 1;
            maxNumberRxSpatialStreams = 1;
            is11bMode = false;
            vendorData = Collections.emptyList();
        }
    }

    /**
     * Returns connection capabilities of the current network
     *
     * @param ifaceName Name of the interface.
     * @return connection capabilities of the current network
     */
    public ConnectionCapabilities getConnectionCapabilities(@NonNull String ifaceName) {
        return mSupplicantStaIfaceHal.getConnectionCapabilities(ifaceName);
    }

    /**
     * Request signal polling to supplicant.
     *
     * @param ifaceName Name of the interface.
     * Returns an array of SignalPollResult objects.
     * Returns null on failure.
     */
    @Nullable
    public WifiSignalPollResults signalPoll(@NonNull String ifaceName) {
        if (mMockWifiModem != null
                && mMockWifiModem.isMethodConfigured(
                    MockWifiServiceUtil.MOCK_NL80211_SERVICE, "signalPoll")) {
            Log.i(TAG, "signalPoll was called from mock wificond");
            WifiNl80211Manager.SignalPollResult result =
                    mMockWifiModem.getWifiNl80211Manager().signalPoll(ifaceName);
            if (result != null) {
                // Convert WifiNl80211Manager#SignalPollResult to WifiSignalPollResults.
                // Assume single link and linkId = 0.
                WifiSignalPollResults results = new WifiSignalPollResults();
                results.addEntry(0, result.currentRssiDbm, result.txBitrateMbps,
                        result.rxBitrateMbps, result.associationFrequencyMHz);
                return results;
            }
        }
        // Query supplicant.
        WifiSignalPollResults results = mSupplicantStaIfaceHal.getSignalPollResults(
                ifaceName);
        if (results == null) {
            // Fallback to WifiCond.
            WifiNl80211Manager.SignalPollResult result = mWifiCondManager.signalPoll(ifaceName);
            if (result != null) {
                // Convert WifiNl80211Manager#SignalPollResult to WifiSignalPollResults.
                // Assume single link and linkId = 0.
                results = new WifiSignalPollResults();
                results.addEntry(0, result.currentRssiDbm, result.txBitrateMbps,
                        result.rxBitrateMbps, result.associationFrequencyMHz);
            }
        }
        return results;
    }

    /**
     * Class to represent a connection MLO Link
     */
    public static class ConnectionMloLink {
        private final int mLinkId;
        private final MacAddress mStaMacAddress;
        private final BitSet mTidsUplinkMap;
        private final BitSet mTidsDownlinkMap;
        private final MacAddress mApMacAddress;
        private final int mFrequencyMHz;

        ConnectionMloLink(int id, MacAddress staMacAddress, MacAddress apMacAddress,
                byte tidsUplink, byte tidsDownlink, int frequencyMHz) {
            mLinkId = id;
            mStaMacAddress = staMacAddress;
            mApMacAddress = apMacAddress;
            mTidsDownlinkMap = BitSet.valueOf(new byte[] { tidsDownlink });
            mTidsUplinkMap = BitSet.valueOf(new byte[] { tidsUplink });
            mFrequencyMHz = frequencyMHz;
        };

        /**
         * Check if there is any TID mapped to this link in uplink of downlink direction.
         *
         * @return true if there is any TID mapped to this link, otherwise false.
         */
        public boolean isAnyTidMapped() {
            if (mTidsDownlinkMap.isEmpty() && mTidsUplinkMap.isEmpty()) {
                return false;
            }
            return true;
        }

        /**
         * Check if a TID is mapped to this link in uplink direction.
         *
         * @param tid TID value.
         * @return true if the TID is mapped in uplink direction. Otherwise, false.
         */
        public boolean isTidMappedToUplink(byte tid) {
            if (tid < mTidsUplinkMap.length()) {
                return mTidsUplinkMap.get(tid);
            }
            return false;
        }

        /**
         * Check if a TID is mapped to this link in downlink direction. Otherwise, false.
         *
         * @param tid TID value
         * @return true if the TID is mapped in downlink direction. Otherwise, false.
         */
        public boolean isTidMappedtoDownlink(byte tid) {
            if (tid < mTidsDownlinkMap.length()) {
                return mTidsDownlinkMap.get(tid);
            }
            return false;
        }

        /**
         * Get link id for the link.
         *
         * @return link id.
         */
        public int getLinkId() {
            return mLinkId;
        }

        /**
         * Get link STA MAC address.
         *
         * @return link mac address.
         */
        public MacAddress getStaMacAddress() {
            return mStaMacAddress;
        }

        /**
         * Get link AP MAC address.
         *
         * @return MAC address.
         */
        public MacAddress getApMacAddress() {
            return mApMacAddress;
        }

        /**
         * Get link frequency in MHz.
         *
         * @return frequency in Mhz.
         */
        public int getFrequencyMHz() {
            return mFrequencyMHz;
        }
    }

    /**
     * Class to represent the MLO links info for a connection that is collected after association
     */
    public static class ConnectionMloLinksInfo {
        public ConnectionMloLink[] links;
        public MacAddress apMldMacAddress;
        public int apMloLinkId;
        ConnectionMloLinksInfo() {
            // Nothing for now
        }
    }

    /**
     * Returns connection MLO Links Info.
     *
     * @param ifaceName Name of the interface.
     * @return connection MLO Links Info
     */
    public ConnectionMloLinksInfo getConnectionMloLinksInfo(@NonNull String ifaceName) {
        return mSupplicantStaIfaceHal.getConnectionMloLinksInfo(ifaceName);
    }

    /**
     * Get the APF (Android Packet Filter) capabilities of the device
     * @param ifaceName Name of the interface.
     */
    public ApfCapabilities getApfCapabilities(@NonNull String ifaceName) {
        return mWifiVendorHal.getApfCapabilities(ifaceName);
    }

    /**
     * Installs an APF program on this iface, replacing any existing program.
     *
     * @param ifaceName Name of the interface
     * @param filter is the android packet filter program
     * @return true for success
     */
    public boolean installPacketFilter(@NonNull String ifaceName, byte[] filter) {
        return mWifiVendorHal.installPacketFilter(ifaceName, filter);
    }

    /**
     * Reads the APF program and data buffer for this iface.
     *
     * @param ifaceName Name of the interface
     * @return the buffer returned by the driver, or null in case of an error
     */
    public byte[] readPacketFilter(@NonNull String ifaceName) {
        return mWifiVendorHal.readPacketFilter(ifaceName);
    }

    /**
     * Set country code for this AP iface.
     * @param ifaceName Name of the AP interface.
     * @param countryCode - two-letter country code (as ISO 3166)
     * @return true for success
     */
    public boolean setApCountryCode(@NonNull String ifaceName, String countryCode) {
        if (mWifiVendorHal.setApCountryCode(ifaceName, countryCode)) {
            if (mCountryCodeChangeListener != null) {
                mCountryCodeChangeListener.onSetCountryCodeSucceeded(countryCode);
            }
            return true;
        }
        return false;
    }

    /**
     * Set country code for this chip
     * @param countryCode - two-letter country code (as ISO 3166)
     * @return true for success
     */
    public boolean setChipCountryCode(String countryCode) {
        if (mWifiVendorHal.setChipCountryCode(countryCode)) {
            if (mCountryCodeChangeListener != null) {
                mCountryCodeChangeListener.onSetCountryCodeSucceeded(countryCode);
            }
            return true;
        }
        return false;
    }

    //---------------------------------------------------------------------------------
    /* Wifi Logger commands/events */
    public static interface WifiLoggerEventHandler {
        void onRingBufferData(RingBufferStatus status, byte[] buffer);
        void onWifiAlert(int errorCode, byte[] buffer);
    }

    /**
     * Registers the logger callback and enables alerts.
     * Ring buffer data collection is only triggered when |startLoggingRingBuffer| is invoked.
     *
     * @param handler Callback to be invoked.
     * @return true on success, false otherwise.
     */
    public boolean setLoggingEventHandler(WifiLoggerEventHandler handler) {
        return mWifiVendorHal.setLoggingEventHandler(handler);
    }

    /**
     * Control debug data collection
     *
     * @param verboseLevel 0 to 3, inclusive. 0 stops logging.
     * @param flags        Ignored.
     * @param maxInterval  Maximum interval between reports; ignore if 0.
     * @param minDataSize  Minimum data size in buffer for report; ignore if 0.
     * @param ringName     Name of the ring for which data collection is to start.
     * @return true for success, false otherwise.
     */
    public boolean startLoggingRingBuffer(int verboseLevel, int flags, int maxInterval,
            int minDataSize, String ringName){
        return mWifiVendorHal.startLoggingRingBuffer(
                verboseLevel, flags, maxInterval, minDataSize, ringName);
    }

    /**
     * Logger features exposed.
     * This is a no-op now, will always return -1.
     *
     * @return true on success, false otherwise.
     */
    public int getSupportedLoggerFeatureSet() {
        return mWifiVendorHal.getSupportedLoggerFeatureSet();
    }

    /**
     * Stops all logging and resets the logger callback.
     * This stops both the alerts and ring buffer data collection.
     * @return true on success, false otherwise.
     */
    public boolean resetLogHandler() {
        return mWifiVendorHal.resetLogHandler();
    }

    /**
     * Vendor-provided wifi driver version string
     *
     * @return String returned from the HAL.
     */
    public String getDriverVersion() {
        return mWifiVendorHal.getDriverVersion();
    }

    /**
     * Vendor-provided wifi firmware version string
     *
     * @return String returned from the HAL.
     */
    public String getFirmwareVersion() {
        return mWifiVendorHal.getFirmwareVersion();
    }

    public static class RingBufferStatus{
        public String name;
        public int flag;
        public int ringBufferId;
        public int ringBufferByteSize;
        public int verboseLevel;
        int writtenBytes;
        int readBytes;
        int writtenRecords;

        // Bit masks for interpreting |flag|
        public static final int HAS_BINARY_ENTRIES = (1 << 0);
        public static final int HAS_ASCII_ENTRIES = (1 << 1);
        public static final int HAS_PER_PACKET_ENTRIES = (1 << 2);

        @Override
        public String toString() {
            return "name: " + name + " flag: " + flag + " ringBufferId: " + ringBufferId +
                    " ringBufferByteSize: " +ringBufferByteSize + " verboseLevel: " +verboseLevel +
                    " writtenBytes: " + writtenBytes + " readBytes: " + readBytes +
                    " writtenRecords: " + writtenRecords;
        }
    }

    /**
     * API to get the status of all ring buffers supported by driver
     */
    public RingBufferStatus[] getRingBufferStatus() {
        return mWifiVendorHal.getRingBufferStatus();
    }

    /**
     * Indicates to driver that all the data has to be uploaded urgently
     *
     * @param ringName Name of the ring buffer requested.
     * @return true on success, false otherwise.
     */
    public boolean getRingBufferData(String ringName) {
        return mWifiVendorHal.getRingBufferData(ringName);
    }

    /**
     * Request hal to flush ring buffers to files
     *
     * @return true on success, false otherwise.
     */
    public boolean flushRingBufferData() {
        return mWifiVendorHal.flushRingBufferData();
    }

    /**
     * Request vendor debug info from the firmware
     *
     * @return Raw data obtained from the HAL.
     */
    public byte[] getFwMemoryDump() {
        return mWifiVendorHal.getFwMemoryDump();
    }

    /**
     * Request vendor debug info from the driver
     *
     * @return Raw data obtained from the HAL.
     */
    public byte[] getDriverStateDump() {
        return mWifiVendorHal.getDriverStateDump();
    }

    /**
     * Dump information about the internal state
     *
     * @param pw PrintWriter to write dump to
     */
    protected void dump(PrintWriter pw) {
        pw.println("Dump of " + TAG);
        pw.println("mIsLocationModeEnabled: " + mIsLocationModeEnabled);
        pw.println("mLastLocationModeEnabledTimeMs: " + mLastLocationModeEnabledTimeMs);
        mHostapdHal.dump(pw);
    }

    //---------------------------------------------------------------------------------
    /* Packet fate API */

    @Immutable
    public abstract static class FateReport {
        final static int USEC_PER_MSEC = 1000;
        // The driver timestamp is a 32-bit counter, in microseconds. This field holds the
        // maximal value of a driver timestamp in milliseconds.
        final static int MAX_DRIVER_TIMESTAMP_MSEC = (int) (0xffffffffL / 1000);
        final static SimpleDateFormat dateFormatter = new SimpleDateFormat("HH:mm:ss.SSS");

        public final byte mFate;
        public final long mDriverTimestampUSec;
        public final byte mFrameType;
        public final byte[] mFrameBytes;
        public final long mEstimatedWallclockMSec;

        FateReport(byte fate, long driverTimestampUSec, byte frameType, byte[] frameBytes) {
            mFate = fate;
            mDriverTimestampUSec = driverTimestampUSec;
            mEstimatedWallclockMSec =
                    convertDriverTimestampUSecToWallclockMSec(mDriverTimestampUSec);
            mFrameType = frameType;
            mFrameBytes = frameBytes;
        }

        public String toTableRowString() {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            FrameParser parser = new FrameParser(mFrameType, mFrameBytes);
            dateFormatter.setTimeZone(TimeZone.getDefault());
            pw.format("%-15s  %12s  %-9s  %-32s  %-12s  %-23s  %s\n",
                    mDriverTimestampUSec,
                    dateFormatter.format(new Date(mEstimatedWallclockMSec)),
                    directionToString(), fateToString(), parser.mMostSpecificProtocolString,
                    parser.mTypeString, parser.mResultString);
            return sw.toString();
        }

        public String toVerboseStringWithPiiAllowed() {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            FrameParser parser = new FrameParser(mFrameType, mFrameBytes);
            pw.format("Frame direction: %s\n", directionToString());
            pw.format("Frame timestamp: %d\n", mDriverTimestampUSec);
            pw.format("Frame fate: %s\n", fateToString());
            pw.format("Frame type: %s\n", frameTypeToString(mFrameType));
            pw.format("Frame protocol: %s\n", parser.mMostSpecificProtocolString);
            pw.format("Frame protocol type: %s\n", parser.mTypeString);
            pw.format("Frame length: %d\n", mFrameBytes.length);
            pw.append("Frame bytes");
            pw.append(HexDump.dumpHexString(mFrameBytes));  // potentially contains PII
            pw.append("\n");
            return sw.toString();
        }

        /* Returns a header to match the output of toTableRowString(). */
        public static String getTableHeader() {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            pw.format("\n%-15s  %-12s  %-9s  %-32s  %-12s  %-23s  %s\n",
                    "Time usec", "Walltime", "Direction", "Fate", "Protocol", "Type", "Result");
            pw.format("%-15s  %-12s  %-9s  %-32s  %-12s  %-23s  %s\n",
                    "---------", "--------", "---------", "----", "--------", "----", "------");
            return sw.toString();
        }

        protected abstract String directionToString();

        protected abstract String fateToString();

        private static String frameTypeToString(byte frameType) {
            switch (frameType) {
                case WifiLoggerHal.FRAME_TYPE_UNKNOWN:
                    return "unknown";
                case WifiLoggerHal.FRAME_TYPE_ETHERNET_II:
                    return "data";
                case WifiLoggerHal.FRAME_TYPE_80211_MGMT:
                    return "802.11 management";
                default:
                    return Byte.toString(frameType);
            }
        }

        /**
         * Converts a driver timestamp to a wallclock time, based on the current
         * BOOTTIME to wallclock mapping. The driver timestamp is a 32-bit counter of
         * microseconds, with the same base as BOOTTIME.
         */
        private static long convertDriverTimestampUSecToWallclockMSec(long driverTimestampUSec) {
            final long wallclockMillisNow = System.currentTimeMillis();
            final long boottimeMillisNow = SystemClock.elapsedRealtime();
            final long driverTimestampMillis = driverTimestampUSec / USEC_PER_MSEC;

            long boottimeTimestampMillis = boottimeMillisNow % MAX_DRIVER_TIMESTAMP_MSEC;
            if (boottimeTimestampMillis < driverTimestampMillis) {
                // The 32-bit microsecond count has wrapped between the time that the driver
                // recorded the packet, and the call to this function. Adjust the BOOTTIME
                // timestamp, to compensate.
                //
                // Note that overflow is not a concern here, since the result is less than
                // 2 * MAX_DRIVER_TIMESTAMP_MSEC. (Given the modulus operation above,
                // boottimeTimestampMillis must be less than MAX_DRIVER_TIMESTAMP_MSEC.) And, since
                // MAX_DRIVER_TIMESTAMP_MSEC is an int, 2 * MAX_DRIVER_TIMESTAMP_MSEC must fit
                // within a long.
                boottimeTimestampMillis += MAX_DRIVER_TIMESTAMP_MSEC;
            }

            final long millisSincePacketTimestamp = boottimeTimestampMillis - driverTimestampMillis;
            return wallclockMillisNow - millisSincePacketTimestamp;
        }
    }

    /**
     * Represents the fate information for one outbound packet.
     */
    @Immutable
    public static final class TxFateReport extends FateReport {
        public TxFateReport(byte fate, long driverTimestampUSec, byte frameType,
                byte[] frameBytes) {
            super(fate, driverTimestampUSec, frameType, frameBytes);
        }

        @Override
        protected String directionToString() {
            return "TX";
        }

        @Override
        protected String fateToString() {
            switch (mFate) {
                case WifiLoggerHal.TX_PKT_FATE_ACKED:
                    return "acked";
                case WifiLoggerHal.TX_PKT_FATE_SENT:
                    return "sent";
                case WifiLoggerHal.TX_PKT_FATE_FW_QUEUED:
                    return "firmware queued";
                case WifiLoggerHal.TX_PKT_FATE_FW_DROP_INVALID:
                    return "firmware dropped (invalid frame)";
                case WifiLoggerHal.TX_PKT_FATE_FW_DROP_NOBUFS:
                    return "firmware dropped (no bufs)";
                case WifiLoggerHal.TX_PKT_FATE_FW_DROP_OTHER:
                    return "firmware dropped (other)";
                case WifiLoggerHal.TX_PKT_FATE_DRV_QUEUED:
                    return "driver queued";
                case WifiLoggerHal.TX_PKT_FATE_DRV_DROP_INVALID:
                    return "driver dropped (invalid frame)";
                case WifiLoggerHal.TX_PKT_FATE_DRV_DROP_NOBUFS:
                    return "driver dropped (no bufs)";
                case WifiLoggerHal.TX_PKT_FATE_DRV_DROP_OTHER:
                    return "driver dropped (other)";
                default:
                    return Byte.toString(mFate);
            }
        }
    }

    /**
     * Represents the fate information for one inbound packet.
     */
    @Immutable
    public static final class RxFateReport extends FateReport {
        public RxFateReport(byte fate, long driverTimestampUSec, byte frameType,
                byte[] frameBytes) {
            super(fate, driverTimestampUSec, frameType, frameBytes);
        }

        @Override
        protected String directionToString() {
            return "RX";
        }

        @Override
        protected String fateToString() {
            switch (mFate) {
                case WifiLoggerHal.RX_PKT_FATE_SUCCESS:
                    return "success";
                case WifiLoggerHal.RX_PKT_FATE_FW_QUEUED:
                    return "firmware queued";
                case WifiLoggerHal.RX_PKT_FATE_FW_DROP_FILTER:
                    return "firmware dropped (filter)";
                case WifiLoggerHal.RX_PKT_FATE_FW_DROP_INVALID:
                    return "firmware dropped (invalid frame)";
                case WifiLoggerHal.RX_PKT_FATE_FW_DROP_NOBUFS:
                    return "firmware dropped (no bufs)";
                case WifiLoggerHal.RX_PKT_FATE_FW_DROP_OTHER:
                    return "firmware dropped (other)";
                case WifiLoggerHal.RX_PKT_FATE_DRV_QUEUED:
                    return "driver queued";
                case WifiLoggerHal.RX_PKT_FATE_DRV_DROP_FILTER:
                    return "driver dropped (filter)";
                case WifiLoggerHal.RX_PKT_FATE_DRV_DROP_INVALID:
                    return "driver dropped (invalid frame)";
                case WifiLoggerHal.RX_PKT_FATE_DRV_DROP_NOBUFS:
                    return "driver dropped (no bufs)";
                case WifiLoggerHal.RX_PKT_FATE_DRV_DROP_OTHER:
                    return "driver dropped (other)";
                default:
                    return Byte.toString(mFate);
            }
        }
    }

    /**
     * Ask the HAL to enable packet fate monitoring. Fails unless HAL is started.
     *
     * @param ifaceName Name of the interface.
     * @return true for success, false otherwise.
     */
    public boolean startPktFateMonitoring(@NonNull String ifaceName) {
        return mWifiVendorHal.startPktFateMonitoring(ifaceName);
    }

    /**
     * Fetch the most recent TX packet fates from the HAL. Fails unless HAL is started.
     *
     * @param ifaceName Name of the interface.
     * @return TxFateReport list on success, empty list on failure. Never returns null.
     */
    @NonNull
    public List<TxFateReport> getTxPktFates(@NonNull String ifaceName) {
        return mWifiVendorHal.getTxPktFates(ifaceName);
    }

    /**
     * Fetch the most recent RX packet fates from the HAL. Fails unless HAL is started.
     * @param ifaceName Name of the interface.
     * @return RxFateReport list on success, empty list on failure. Never returns null.
     */
    @NonNull
    public List<RxFateReport> getRxPktFates(@NonNull String ifaceName) {
        return mWifiVendorHal.getRxPktFates(ifaceName);
    }

    /**
     * Get the tx packet counts for the interface.
     *
     * @param ifaceName Name of the interface.
     * @return tx packet counts
     */
    public long getTxPackets(@NonNull String ifaceName) {
        return TrafficStats.getTxPackets(ifaceName);
    }

    /**
     * Get the rx packet counts for the interface.
     *
     * @param ifaceName Name of the interface
     * @return rx packet counts
     */
    public long getRxPackets(@NonNull String ifaceName) {
        return TrafficStats.getRxPackets(ifaceName);
    }

    /**
     * Start sending the specified keep alive packets periodically.
     *
     * @param ifaceName Name of the interface.
     * @param slot Integer used to identify each request.
     * @param dstMac Destination MAC Address
     * @param packet Raw packet contents to send.
     * @param protocol The ethernet protocol type
     * @param period Period to use for sending these packets.
     * @return 0 for success, -1 for error
     */
    public int startSendingOffloadedPacket(@NonNull String ifaceName, int slot,
            byte[] dstMac, byte[] packet, int protocol, int period) {
        byte[] srcMac = NativeUtil.macAddressToByteArray(getMacAddress(ifaceName));
        return mWifiVendorHal.startSendingOffloadedPacket(
                ifaceName, slot, srcMac, dstMac, packet, protocol, period);
    }

    /**
     * Stop sending the specified keep alive packets.
     *
     * @param ifaceName Name of the interface.
     * @param slot id - same as startSendingOffloadedPacket call.
     * @return 0 for success, -1 for error
     */
    public int stopSendingOffloadedPacket(@NonNull String ifaceName, int slot) {
        return mWifiVendorHal.stopSendingOffloadedPacket(ifaceName, slot);
    }

    public static interface WifiRssiEventHandler {
        void onRssiThresholdBreached(byte curRssi);
    }

    /**
     * Start RSSI monitoring on the currently connected access point.
     *
     * @param ifaceName        Name of the interface.
     * @param maxRssi          Maximum RSSI threshold.
     * @param minRssi          Minimum RSSI threshold.
     * @param rssiEventHandler Called when RSSI goes above maxRssi or below minRssi
     * @return 0 for success, -1 for failure
     */
    public int startRssiMonitoring(
            @NonNull String ifaceName, byte maxRssi, byte minRssi,
            WifiRssiEventHandler rssiEventHandler) {
        return mWifiVendorHal.startRssiMonitoring(
                ifaceName, maxRssi, minRssi, rssiEventHandler);
    }

    /**
     * Stop RSSI monitoring on the currently connected access point.
     *
     * @param ifaceName Name of the interface.
     * @return 0 for success, -1 for failure
     */
    public int stopRssiMonitoring(@NonNull String ifaceName) {
        return mWifiVendorHal.stopRssiMonitoring(ifaceName);
    }

    /**
     * Fetch the host wakeup reasons stats from wlan driver.
     *
     * @return the |WlanWakeReasonAndCounts| object retrieved from the wlan driver.
     */
    public WlanWakeReasonAndCounts getWlanWakeReasonCount() {
        return mWifiVendorHal.getWlanWakeReasonCount();
    }

    /**
     * Enable/Disable Neighbour discovery offload functionality in the firmware.
     *
     * @param ifaceName Name of the interface.
     * @param enabled true to enable, false to disable.
     * @return true for success, false otherwise.
     */
    public boolean configureNeighborDiscoveryOffload(@NonNull String ifaceName, boolean enabled) {
        return mWifiVendorHal.configureNeighborDiscoveryOffload(ifaceName, enabled);
    }

    // Firmware roaming control.

    /**
     * Class to retrieve firmware roaming capability parameters.
     */
    public static class RoamingCapabilities {
        public int maxBlocklistSize;
        public int maxAllowlistSize;
    }

    /**
     * Query the firmware roaming capabilities.
     * @param ifaceName Name of the interface.
     * @return capabilities object on success, null otherwise.
     */
    @Nullable
    public RoamingCapabilities getRoamingCapabilities(@NonNull String ifaceName) {
        return mWifiVendorHal.getRoamingCapabilities(ifaceName);
    }

    /**
     * Macros for controlling firmware roaming.
     */
    public static final int DISABLE_FIRMWARE_ROAMING = 0;
    public static final int ENABLE_FIRMWARE_ROAMING = 1;

    @IntDef({ENABLE_FIRMWARE_ROAMING, DISABLE_FIRMWARE_ROAMING})
    @Retention(RetentionPolicy.SOURCE)
    public @interface RoamingEnableState {}

    /**
     * Indicates success for enableFirmwareRoaming
     */
    public static final int SET_FIRMWARE_ROAMING_SUCCESS = 0;

    /**
     * Indicates failure for enableFirmwareRoaming
     */
    public static final int SET_FIRMWARE_ROAMING_FAILURE = 1;

    /**
     * Indicates temporary failure for enableFirmwareRoaming - try again later
     */
    public static final int SET_FIRMWARE_ROAMING_BUSY = 2;

    @IntDef({SET_FIRMWARE_ROAMING_SUCCESS, SET_FIRMWARE_ROAMING_FAILURE, SET_FIRMWARE_ROAMING_BUSY})
    @Retention(RetentionPolicy.SOURCE)
    public @interface RoamingEnableStatus {}

    /**
     * Enable/disable firmware roaming.
     *
     * @param ifaceName Name of the interface.
     * @return SET_FIRMWARE_ROAMING_SUCCESS, SET_FIRMWARE_ROAMING_FAILURE,
     *         or SET_FIRMWARE_ROAMING_BUSY
     */
    public @RoamingEnableStatus int enableFirmwareRoaming(@NonNull String ifaceName,
            @RoamingEnableState int state) {
        return mWifiVendorHal.enableFirmwareRoaming(ifaceName, state);
    }

    /**
     * Class for specifying the roaming configurations.
     */
    public static class RoamingConfig {
        public ArrayList<String> blocklistBssids;
        public ArrayList<String> allowlistSsids;
    }

    /**
     * Set firmware roaming configurations.
     * @param ifaceName Name of the interface.
     */
    public boolean configureRoaming(@NonNull String ifaceName, RoamingConfig config) {
        return mWifiVendorHal.configureRoaming(ifaceName, config);
    }

    /**
     * Reset firmware roaming configuration.
     * @param ifaceName Name of the interface.
     */
    public boolean resetRoamingConfiguration(@NonNull String ifaceName) {
        // Pass in an empty RoamingConfig object which translates to zero size
        // blacklist and whitelist to reset the firmware roaming configuration.
        return mWifiVendorHal.configureRoaming(ifaceName, new RoamingConfig());
    }

    /**
     * Select one of the pre-configured transmit power level scenarios or reset it back to normal.
     * Primarily used for meeting SAR requirements.
     *
     * @param sarInfo The collection of inputs used to select the SAR scenario.
     * @return true for success; false for failure or if the HAL version does not support this API.
     */
    public boolean selectTxPowerScenario(SarInfo sarInfo) {
        return mWifiVendorHal.selectTxPowerScenario(sarInfo);
    }

    /**
     * Set MBO cellular data status
     *
     * @param ifaceName Name of the interface.
     * @param available cellular data status,
     *        true means cellular data available, false otherwise.
     */
    public void setMboCellularDataStatus(@NonNull String ifaceName, boolean available) {
        mSupplicantStaIfaceHal.setMboCellularDataStatus(ifaceName, available);
    }

    /**
     * Query of support of Wi-Fi standard
     *
     * @param ifaceName name of the interface to check support on
     * @param standard the wifi standard to check on
     * @return true if the wifi standard is supported on this interface, false otherwise.
     */
    public boolean isWifiStandardSupported(@NonNull String ifaceName,
            @WifiAnnotations.WifiStandard int standard) {
        synchronized (mLock) {
            Iface iface = mIfaceMgr.getIface(ifaceName);
            if (iface == null || iface.phyCapabilities == null) {
                return false;
            }
            return iface.phyCapabilities.isWifiStandardSupported(standard);
        }
    }

    /**
     * Get the Wiphy capabilities of a device for a given interface
     * If the interface is not associated with one,
     * it will be read from the device through wificond
     *
     * @param ifaceName name of the interface
     * @return the device capabilities for this interface
     */
    public DeviceWiphyCapabilities getDeviceWiphyCapabilities(@NonNull String ifaceName) {
        synchronized (mLock) {
            Iface iface = mIfaceMgr.getIface(ifaceName);
            if (iface == null) {
                Log.e(TAG, "Failed to get device capabilities, interface not found: " + ifaceName);
                return null;
            }
            if (iface.phyCapabilities == null) {
                iface.phyCapabilities = mWifiCondManager.getDeviceWiphyCapabilities(ifaceName);
            }
            if (iface.phyCapabilities != null
                    && iface.phyCapabilities.isWifiStandardSupported(ScanResult.WIFI_STANDARD_11BE)
                    != mWifiInjector.getSettingsConfigStore()
                    .get(WifiSettingsConfigStore.WIFI_WIPHY_11BE_SUPPORTED)) {
                mWifiInjector.getSettingsConfigStore().put(
                        WifiSettingsConfigStore.WIFI_WIPHY_11BE_SUPPORTED,
                        iface.phyCapabilities.isWifiStandardSupported(
                        ScanResult.WIFI_STANDARD_11BE));
            }
            return iface.phyCapabilities;
        }
    }

    /**
     * Set the Wiphy capabilities of a device for a given interface
     *
     * @param ifaceName name of the interface
     * @param capabilities the wiphy capabilities to set for this interface
     */
    public void setDeviceWiphyCapabilities(@NonNull String ifaceName,
            DeviceWiphyCapabilities capabilities) {
        synchronized (mLock) {
            Iface iface = mIfaceMgr.getIface(ifaceName);
            if (iface == null) {
                Log.e(TAG, "Failed to set device capabilities, interface not found: " + ifaceName);
                return;
            }
            iface.phyCapabilities = capabilities;
        }
    }

    /**
     * Notify scan mode state to driver to save power in scan-only mode.
     *
     * @param ifaceName Name of the interface.
     * @param enable whether is in scan-only mode
     * @return true for success
     */
    public boolean setScanMode(String ifaceName, boolean enable) {
        return mWifiVendorHal.setScanMode(ifaceName, enable);
    }

    /** updates linked networks of the |networkId| in supplicant if it's the current network,
     * if the current configured network matches |networkId|.
     *
     * @param ifaceName Name of the interface.
     * @param networkId network id of the network to be updated from supplicant.
     * @param linkedNetworks Map of config profile key and config for linking.
     */
    public boolean updateLinkedNetworks(@NonNull String ifaceName, int networkId,
            Map<String, WifiConfiguration> linkedNetworks) {
        return mSupplicantStaIfaceHal.updateLinkedNetworks(ifaceName, networkId, linkedNetworks);
    }

    /**
     * Start Subsystem Restart
     * @return true on success
     */
    public boolean startSubsystemRestart() {
        return mWifiVendorHal.startSubsystemRestart();
    }

    /**
     * Register the provided listener for country code event.
     *
     * @param listener listener for country code changed events.
     */
    public void registerCountryCodeEventListener(WifiCountryCode.ChangeListener listener) {
        registerWificondListenerIfNecessary();
        if (mCountryCodeChangeListener != null) {
            mCountryCodeChangeListener.setChangeListener(listener);
        }
    }

    /**
     * Gets the security params of the current network associated with this interface
     *
     * @param ifaceName Name of the interface
     * @return Security params of the current network associated with the interface
     */
    public SecurityParams getCurrentNetworkSecurityParams(@NonNull String ifaceName) {
        return mSupplicantStaIfaceHal.getCurrentNetworkSecurityParams(ifaceName);
    }

    /**
     * Check if the network-centric QoS policy feature was successfully enabled.
     */
    public boolean isQosPolicyFeatureEnabled() {
        return mQosPolicyFeatureEnabled;
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
    public boolean sendQosPolicyResponse(String ifaceName, int qosPolicyRequestId,
            boolean morePolicies, @NonNull List<QosPolicyStatus> qosPolicyStatusList) {
        if (!mQosPolicyFeatureEnabled) {
            Log.e(TAG, "Unable to send QoS policy response, feature is not enabled");
            return false;
        }
        return mSupplicantStaIfaceHal.sendQosPolicyResponse(ifaceName, qosPolicyRequestId,
                morePolicies, qosPolicyStatusList);
    }

    /**
     * Indicates the removal of all active QoS policies configured by the AP.
     *
     * @param ifaceName Name of the interface.
     */
    public boolean removeAllQosPolicies(String ifaceName) {
        if (!mQosPolicyFeatureEnabled) {
            Log.e(TAG, "Unable to remove all QoS policies, feature is not enabled");
            return false;
        }
        return mSupplicantStaIfaceHal.removeAllQosPolicies(ifaceName);
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
    List<SupplicantStaIfaceHal.QosPolicyStatus> addQosPolicyRequestForScs(
            @NonNull String ifaceName, @NonNull List<QosPolicyParams> policies) {
        return mSupplicantStaIfaceHal.addQosPolicyRequestForScs(ifaceName, policies);
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
    List<SupplicantStaIfaceHal.QosPolicyStatus> removeQosPolicyForScs(
            @NonNull String ifaceName, @NonNull List<Byte> policyIds) {
        return mSupplicantStaIfaceHal.removeQosPolicyForScs(ifaceName, policyIds);
    }

    /**
     * Register a callback to receive notifications for QoS SCS transactions.
     * Callback should only be registered once.
     *
     * @param callback {@link SupplicantStaIfaceHal.QosScsResponseCallback} to register.
     */
    public void registerQosScsResponseCallback(
            @NonNull SupplicantStaIfaceHal.QosScsResponseCallback callback) {
        mSupplicantStaIfaceHal.registerQosScsResponseCallback(callback);
    }

    /**
     * Generate DPP credential for network access
     *
     * @param ifaceName Name of the interface.
     * @param ssid ssid of the network
     * @param privEcKey Private EC Key for DPP Configurator
     * Returns true when operation is successful. On error, false is returned.
     */
    public boolean generateSelfDppConfiguration(@NonNull String ifaceName, @NonNull String ssid,
            byte[] privEcKey) {
        return mSupplicantStaIfaceHal.generateSelfDppConfiguration(ifaceName, ssid, privEcKey);
    }

    /**
     * This set anonymous identity to supplicant.
     *
     * @param ifaceName Name of the interface.
     * @param anonymousIdentity the anonymouns identity.
     * @param updateToNativeService write the data to the native service.
     * @return true if succeeds, false otherwise.
     */
    public boolean setEapAnonymousIdentity(@NonNull String ifaceName, String anonymousIdentity,
            boolean updateToNativeService) {
        if (null == anonymousIdentity) {
            Log.e(TAG, "Cannot set null anonymous identity.");
            return false;
        }
        return mSupplicantStaIfaceHal.setEapAnonymousIdentity(ifaceName, anonymousIdentity,
                updateToNativeService);
    }

    /**
     * Notify wificond daemon of country code have changed.
     */
    public void countryCodeChanged(String countryCode) {
        if (SdkLevel.isAtLeastT()) {
            try {
                mWifiCondManager.notifyCountryCodeChanged(countryCode);
            } catch (RuntimeException re) {
                Log.e(TAG, "Fail to notify wificond country code changed to " + countryCode
                        + "because exception happened:" + re);
            }
        }
    }

    /**
     *  Return the maximum number of concurrent TDLS sessions supported by the device.
     *  @return -1 if the information is not available on the device
     */
    public int getMaxSupportedConcurrentTdlsSessions(@NonNull String ifaceName) {
        return mWifiVendorHal.getMaxSupportedConcurrentTdlsSessions(ifaceName);
    }

    /**
     * Save the complete list of features retrieved from WiFi HAL and Supplicant HAL in
     * config store.
     */
    private void saveCompleteFeatureSetInConfigStoreIfNecessary(long featureSet) {
        long cachedFeatureSet = getCompleteFeatureSetFromConfigStore();
        if (cachedFeatureSet != featureSet) {
            mCachedFeatureSet = featureSet;
            mWifiInjector.getSettingsConfigStore()
                    .put(WIFI_NATIVE_SUPPORTED_FEATURES, mCachedFeatureSet);
            Log.i(TAG, "Supported features is updated in config store: " + mCachedFeatureSet);
        }
    }

    /**
     * Get the feature set from cache/config store
     */
    private long getCompleteFeatureSetFromConfigStore() {
        if (mCachedFeatureSet == 0) {
            mCachedFeatureSet = mWifiInjector.getSettingsConfigStore()
                    .get(WIFI_NATIVE_SUPPORTED_FEATURES);
        }
        return mCachedFeatureSet;
    }

    /**
     * Returns whether or not the hostapd HAL supports reporting single instance died event.
     */
    public boolean isSoftApInstanceDiedHandlerSupported() {
        return mHostapdHal.isSoftApInstanceDiedHandlerSupported();
    }

    /** Checks if there are any STA (for connectivity) iface active. */
    @VisibleForTesting
    boolean hasAnyStaIfaceForConnectivity() {
        return mIfaceMgr.hasAnyStaIfaceForConnectivity();
    }

    /** Checks if there are any STA (for scan) iface active. */
    @VisibleForTesting
    boolean hasAnyStaIfaceForScan() {
        return mIfaceMgr.hasAnyStaIfaceForScan();
    }

    /** Checks if there are any AP iface active. */
    @VisibleForTesting
    boolean hasAnyApIface() {
        return mIfaceMgr.hasAnyApIface();
    }

    /** Checks if there are any iface active. */
    @VisibleForTesting
    boolean hasAnyIface() {
        return mIfaceMgr.hasAnyIface();
    }

    /** Checks if there are any P2P iface active. */
    @VisibleForTesting
    boolean hasAnyP2pIface() {
        return mIfaceMgr.hasAnyP2pIface();
    }

    /**
     * Sets or clean mock wifi service
     *
     * @param serviceName the service name of mock wifi service. When service name is empty, the
     *                    framework will clean mock wifi service.
     */
    public void setMockWifiService(String serviceName) {
        Log.d(TAG, "set MockWifiModemService to " + serviceName);
        if (TextUtils.isEmpty(serviceName)) {
            mMockWifiModem.unbindMockModemService();
            mMockWifiModem = null;
            mWifiInjector.setMockWifiServiceUtil(null);
            return;
        }
        mMockWifiModem = new MockWifiServiceUtil(mContext, serviceName, mWifiMonitor);
        mWifiInjector.setMockWifiServiceUtil(mMockWifiModem);
        if (mMockWifiModem == null) {
            Log.e(TAG, "MockWifiServiceUtil creation failed.");
            return;
        }

        // mock wifi modem service is set, try to bind all supported mock HAL services
        mMockWifiModem.bindAllMockModemService();
        for (int service = MockWifiServiceUtil.MIN_SERVICE_IDX;
                service < MockWifiServiceUtil.NUM_SERVICES; service++) {
            int retryCount = 0;
            IBinder binder;
            do {
                binder = mMockWifiModem.getServiceBinder(service);
                retryCount++;
                if (binder == null) {
                    Log.d(TAG, "Retry(" + retryCount + ") for "
                            + mMockWifiModem.getModuleName(service));
                    try {
                        Thread.sleep(MockWifiServiceUtil.BINDER_RETRY_MILLIS);
                    } catch (InterruptedException e) {
                    }
                }
            } while ((binder == null) && (retryCount < MockWifiServiceUtil.BINDER_MAX_RETRY));

            if (binder == null) {
                Log.e(TAG, "Mock " + mMockWifiModem.getModuleName(service) + " bind fail");
            }
        }
    }

    /**
     *  Returns mock wifi service name.
     */
    public String getMockWifiServiceName() {
        String serviceName = mMockWifiModem != null ? mMockWifiModem.getServiceName() : null;
        Log.d(TAG, "getMockWifiServiceName - service name is " + serviceName);
        return serviceName;
    }

    /**
     * Sets mocked methods which like to be called.
     *
     * @param methods the methods string with formats HAL name - method name, ...
     */
    public boolean setMockWifiMethods(String methods) {
        if (mMockWifiModem == null || methods == null) {
            return false;
        }
        return mMockWifiModem.setMockedMethods(methods);
    }

    /**
     * Set maximum acceptable DTIM multiplier to hardware driver. Any multiplier larger than the
     * maximum value must not be accepted, it will cause packet loss higher than what the system
     * can accept, which will cause unexpected behavior for apps, and may interrupt the network
     * connection.
     *
     * @param ifaceName Name of the interface.
     * @param multiplier integer maximum DTIM multiplier value to set.
     * @return true for success
     */
    public boolean setDtimMultiplier(String ifaceName, int multiplier) {
        return mWifiVendorHal.setDtimMultiplier(ifaceName, multiplier);
    }

    /**
     * Set Multi-Link Operation mode.
     *
     * @param mode Multi-Link Operation mode {@link android.net.wifi.WifiManager.MloMode}.
     * @return {@link WifiStatusCode#SUCCESS} if success, otherwise error code.
     */
    public @WifiStatusCode int setMloMode(@WifiManager.MloMode int mode) {
        @WifiStatusCode  int errorCode = mWifiVendorHal.setMloMode(mode);
        // If set is success, cache it.
        if (errorCode == WifiStatusCode.SUCCESS) mCachedMloMode = mode;
        return errorCode;
    }

    /**
     * Get Multi-Link Operation mode.
     *
     * @return Current Multi-Link Operation mode {@link android.net.wifi.WifiManager.MloMode}.
     */
    public @WifiManager.MloMode int getMloMode() {
        return mCachedMloMode;
    }

    /**
     * Get the maximum number of links supported by the chip for MLO association.
     *
     * e.g. if the chip supports eMLSR (Enhanced Multi-Link Single Radio) and STR (Simultaneous
     * Transmit and Receive) with following capabilities,
     * - Maximum MLO association link count = 3
     * - Maximum MLO STR link count         = 2 See {@link WifiNative#getMaxMloStrLinkCount(String)}
     * One of the possible configuration is - STR (2.4 , eMLSR(5, 6)), provided the radio
     * combination of the chip supports it.
     *
     * Note: This is an input to MLO aware network scoring logic to predict maximum multi-link
     * throughput.
     *
     * @param ifaceName Name of the interface.
     * @return maximum number of association links or -1 if error or not available.
     */
    public int getMaxMloAssociationLinkCount(@NonNull String ifaceName) {
        return mWifiVendorHal.getMaxMloAssociationLinkCount(ifaceName);
    }

    /**
     * Get the maximum number of STR links used in Multi-Link Operation. The maximum number of STR
     * links used for MLO can be different from the number of radios supported by the chip.
     *
     * e.g. if the chip supports eMLSR (Enhanced Multi-Link Single Radio) and STR (Simultaneous
     * Transmit and Receive) with following capabilities,
     * - Maximum MLO association link count = 3
     *   See {@link WifiNative#getMaxMloAssociationLinkCount(String)}
     * - Maximum MLO STR link count         = 2
     * One of the possible configuration is - STR (2.4, eMLSR(5, 6)), provided the radio
     * combination of the chip supports it.
     *
     * Note: This is an input to MLO aware network scoring logic to predict maximum multi-link
     * throughput.
     *
     * @param ifaceName Name of the interface.
     * @return maximum number of MLO STR links or -1 if error or not available.
     */
    public int getMaxMloStrLinkCount(@NonNull String ifaceName) {
        return mWifiVendorHal.getMaxMloStrLinkCount(ifaceName);
    }

    /**
     * Check the given band combination is supported simultaneously by the Wi-Fi chip.
     *
     * Note: This method is for checking simultaneous band operations and not for multichannel
     * concurrent operation (MCC).
     *
     * @param ifaceName Name of the interface.
     * @param bands A list of bands in the combination. See {@link WifiScanner.WifiBand}
     * for the band enums. List of bands can be in any order.
     * @return true if the provided band combination is supported by the chip, otherwise false.
     */
    public boolean isBandCombinationSupported(@NonNull String ifaceName, List<Integer> bands) {
        return mWifiVendorHal.isBandCombinationSupported(ifaceName, bands);
    }

    /**
     * Get the set of band combinations supported simultaneously by the Wi-Fi Chip.
     *
     * Note: This method returns simultaneous band operation combination and not multichannel
     * concurrent operation (MCC) combination.
     *
     * @param ifaceName Name of the interface.
     * @return An unmodifiable set of supported band combinations.
     */
    public Set<List<Integer>> getSupportedBandCombinations(@NonNull String ifaceName) {
        return mWifiVendorHal.getSupportedBandCombinations(ifaceName);
    }

    /**
     * Sends the AFC allowed channels and frequencies to the driver.
     *
     * @param afcChannelAllowance the allowed frequencies and channels received from
     * querying the AFC server.
     * @return whether the channel allowance was set successfully.
     */
    public boolean setAfcChannelAllowance(WifiChip.AfcChannelAllowance afcChannelAllowance) {
        return mWifiVendorHal.setAfcChannelAllowance(afcChannelAllowance);
    }

    /**
     * Enable Mirrored Stream Classification Service (MSCS) and configure using
     * the provided configuration values.
     *
     * @param mscsParams {@link MscsParams} object containing the configuration parameters.
     * @param ifaceName Name of the interface.
     */
    public void enableMscs(@NonNull MscsParams mscsParams, String ifaceName) {
        mSupplicantStaIfaceHal.enableMscs(mscsParams, ifaceName);
    }

    /**
     * Disable Mirrored Stream Classification Service (MSCS).
     *
     * @param ifaceName Name of the interface.
     */
    public void disableMscs(String ifaceName) {
        mSupplicantStaIfaceHal.disableMscs(ifaceName);
    }

    /**
     * Set the roaming mode value.
     *
     * @param ifaceName   Name of the interface.
     * @param roamingMode {@link android.net.wifi.WifiManager.RoamingMode}.
     * @return {@link WifiStatusCode#SUCCESS} if success, otherwise error code.
     */
    public @WifiStatusCode int setRoamingMode(@NonNull String ifaceName,
                                              @RoamingMode int roamingMode) {
        return mWifiVendorHal.setRoamingMode(ifaceName, roamingMode);
    }

    /*
     * TWT callback events
     */
    public interface WifiTwtEvents {
        /**
         * Called when a TWT operation fails
         *
         * @param cmdId Unique command id.
         * @param twtErrorCode Error code
         */
        void onTwtFailure(int cmdId, @TwtSessionCallback.TwtErrorCode int twtErrorCode);

        /**
         * Called when {@link #setupTwtSession(int, String, TwtRequest)}  succeeds.
         *
         * @param cmdId Unique command id used in {@link #setupTwtSession(int, String, TwtRequest)}
         * @param wakeDurationUs TWT wake duration for the session in microseconds
         * @param wakeIntervalUs TWT wake interval for the session in microseconds
         * @param linkId Multi link operation link id
         * @param sessionId TWT session id
         */
        void onTwtSessionCreate(int cmdId, int wakeDurationUs, long wakeIntervalUs, int linkId,
                int sessionId);
        /**
         * Called when TWT session is torn down by {@link #tearDownTwtSession(int, String, int)}.
         * Can also be called unsolicitedly by the vendor software with proper reason code.
         *
         * @param cmdId Unique command id used in {@link #tearDownTwtSession(int, String, int)}
         * @param twtSessionId TWT session Id
         * @param twtReasonCode Reason code for teardown
         */
        void onTwtSessionTeardown(int cmdId, int twtSessionId,
                @TwtSessionCallback.TwtReasonCode int twtReasonCode);

        /**
         * Called as a response to {@link #getStatsTwtSession(int, String, int)}
         *
         * @param cmdId Unique command id used in {@link #getStatsTwtSession(int, String, int)}
         * @param twtSessionId TWT session Id
         * @param twtStats TWT stats object
         */
        void onTwtSessionStats(int cmdId, int twtSessionId, Bundle twtStats);
    }


    /**
     * Sets up a TWT session for the interface
     *
     * @param commandId A unique command id to identify this command
     * @param interfaceName Interface name
     * @param twtRequest TWT request parameters
     * @return true if successful, otherwise false
     */
    public boolean setupTwtSession(int commandId, String interfaceName, TwtRequest twtRequest) {
        return mWifiVendorHal.setupTwtSession(commandId, interfaceName, twtRequest);
    }

    /**
     * Registers TWT callbacks
     *
     * @param wifiTwtCallback TWT callbacks
     */
    public void registerTwtCallbacks(WifiTwtEvents wifiTwtCallback) {
        mWifiVendorHal.registerTwtCallbacks(wifiTwtCallback);
    }

    /**
     * Teardown the TWT session
     *
     * @param commandId A unique command id to identify this command
     * @param interfaceName Interface name
     * @param sessionId TWT session id
     * @return true if successful, otherwise false
     */
    public boolean tearDownTwtSession(int commandId, String interfaceName, int sessionId) {
        return mWifiVendorHal.tearDownTwtSession(commandId, interfaceName, sessionId);
    }

    /**
     * Gets stats of the TWT session
     *
     * @param commandId A unique command id to identify this command
     * @param interfaceName Interface name
     * @param sessionId TWT session id
     * @return true if successful, otherwise false
     */
    public boolean getStatsTwtSession(int commandId, String interfaceName, int sessionId) {
        return mWifiVendorHal.getStatsTwtSession(commandId, interfaceName, sessionId);
    }
}
