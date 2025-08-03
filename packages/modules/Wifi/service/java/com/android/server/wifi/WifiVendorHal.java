/*
 * Copyright (C) 2017 The Android Open Source Project
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

import static com.android.server.wifi.HalDeviceManager.HDM_CREATE_IFACE_AP;
import static com.android.server.wifi.HalDeviceManager.HDM_CREATE_IFACE_AP_BRIDGE;
import static com.android.server.wifi.HalDeviceManager.HDM_CREATE_IFACE_STA;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.wifi.WifiStatusCode;
import android.net.MacAddress;
import android.net.apf.ApfCapabilities;
import android.net.wifi.OuiKeyedData;
import android.net.wifi.ScanResult;
import android.net.wifi.SoftApConfiguration;
import android.net.wifi.WifiAvailableChannel;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.RoamingMode;
import android.net.wifi.WifiScanner;
import android.net.wifi.WifiSsid;
import android.net.wifi.twt.TwtRequest;
import android.os.Bundle;
import android.os.Handler;
import android.os.WorkSource;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;
import android.util.SparseArray;

import com.android.internal.annotations.VisibleForTesting;
import com.android.server.wifi.HalDeviceManager.InterfaceDestroyedListener;
import com.android.server.wifi.WifiNative.RxFateReport;
import com.android.server.wifi.WifiNative.TxFateReport;
import com.android.server.wifi.hal.WifiApIface;
import com.android.server.wifi.hal.WifiChip;
import com.android.server.wifi.hal.WifiHal;
import com.android.server.wifi.hal.WifiStaIface;

import com.google.errorprone.annotations.CompileTimeConstant;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Wi-Fi Vendor HAL.
 * Interface management is handled by the HalDeviceManager.
 */
public class WifiVendorHal {

    private static final WifiLog sNoLog = new FakeWifiLog();

    /**
     * Chatty logging should use mVerboseLog
     */
    @VisibleForTesting
    WifiLog mVerboseLog = sNoLog;

    /**
     * Errors should use mLog
     */
    @VisibleForTesting
    WifiLog mLog = new LogcatLog("WifiVendorHal");

    /**
     * Enables or disables verbose logging
     *
     */
    public void enableVerboseLogging(boolean verboseEnabled, boolean halVerboseEnabled) {
        synchronized (sLock) {
            if (verboseEnabled) {
                mVerboseLog = mLog;
                enter("verbose=true").flush();
            } else {
                enter("verbose=false").flush();
                mVerboseLog = sNoLog;
            }
        }
    }

    /**
     * Logs at method entry
     *
     * @param format string with % placeholders
     * @return LogMessage formatter (remember to .flush())
     */
    private WifiLog.LogMessage enter(@CompileTimeConstant final String format) {
        if (mVerboseLog == sNoLog) return sNoLog.info(format);
        return mVerboseLog.trace(format, 1);
    }

    // Vendor HAL interface objects.
    private WifiChip mWifiChip;
    private HashMap<String, WifiStaIface> mWifiStaIfaces = new HashMap<>();
    private HashMap<String, WifiApIface> mWifiApIfaces = new HashMap<>();
    private static Context sContext;
    private final HalDeviceManager mHalDeviceManager;
    private final WifiGlobals mWifiGlobals;
    private final SsidTranslator mSsidTranslator;
    private final HalDeviceManagerStatusListener mHalDeviceManagerStatusCallbacks;
    private final WifiStaIface.Callback mWifiStaIfaceEventCallback;
    private final ChipEventCallback mWifiChipEventCallback;
    private WifiNative.WifiTwtEvents mWifiTwtEvents;

    // Plumbing for event handling.
    //
    // Being final fields, they can be accessed without synchronization under
    // some reasonable assumptions. See
    // https://docs.oracle.com/javase/specs/jls/se7/html/jls-17.html#jls-17.5
    private final Handler mHalEventHandler;

    /**
     * Wi-Fi chip related info.
     */
    private static class WifiChipInfo {
        public WifiChip.WifiChipCapabilities capabilities = null;
        /**
         * Add more chip specific parameters here. Basically it avoids frequent call to chip by
         * caching it on {@link mCachedWifiChipInfos}.
         */
    }
    /** A cache which maps chip id to {@link WifiChipInfo} */
    private SparseArray<WifiChipInfo> mCachedWifiChipInfos = new SparseArray<>();

    public WifiVendorHal(Context context, HalDeviceManager halDeviceManager, Handler handler,
            WifiGlobals wifiGlobals,
            @NonNull SsidTranslator ssidTranslator) {
        sContext = context;
        mHalDeviceManager = halDeviceManager;
        mHalEventHandler = handler;
        mWifiGlobals = wifiGlobals;
        mSsidTranslator = ssidTranslator;
        mHalDeviceManagerStatusCallbacks = new HalDeviceManagerStatusListener();
        mWifiStaIfaceEventCallback = new StaIfaceEventCallback();
        mWifiChipEventCallback = new ChipEventCallback();
    }

    public static final Object sLock = new Object();

    private WifiNative.VendorHalDeathEventHandler mDeathEventHandler;

    /**
     * Initialize the Hal device manager and register for status callbacks.
     *
     * @param handler Handler to notify if the vendor HAL dies.
     * @return true on success, false otherwise.
     */
    public boolean initialize(WifiNative.VendorHalDeathEventHandler handler) {
        synchronized (sLock) {
            mHalDeviceManager.initialize();
            mHalDeviceManager.registerStatusListener(
                    mHalDeviceManagerStatusCallbacks, mHalEventHandler);
            mDeathEventHandler = handler;
            return true;
        }
    }

    private WifiNative.VendorHalRadioModeChangeEventHandler mRadioModeChangeEventHandler;

    /**
     * Register to listen for radio mode change events from the HAL.
     *
     * @param handler Handler to notify when the vendor HAL detects a radio mode change.
     */
    public void registerRadioModeChangeHandler(
            WifiNative.VendorHalRadioModeChangeEventHandler handler) {
        synchronized (sLock) {
            mRadioModeChangeEventHandler = handler;
        }
    }

    /**
     * Register to listen for subsystem restart events from the HAL.
     *
     * @param listener SubsystemRestartListener listener object.
     */
    public void registerSubsystemRestartListener(
            HalDeviceManager.SubsystemRestartListener listener) {
        mHalDeviceManager.registerSubsystemRestartListener(listener, mHalEventHandler);
    }

    /**
     * Returns whether the vendor HAL is supported on this device or not.
     */
    public boolean isVendorHalSupported() {
        synchronized (sLock) {
            return mHalDeviceManager.isSupported();
        }
    }

    /**
     * Returns whether the vendor HAL is ready or not.
     */
    public boolean isVendorHalReady() {
        synchronized (sLock) {
            return mHalDeviceManager.isReady();
        }
    }

    /**
     * Bring up the Vendor HAL and configure for STA (Station) mode
     *
     * @return true for success
     */
    public boolean startVendorHalSta(@NonNull ConcreteClientModeManager concreteClientModeManager) {
        synchronized (sLock) {
            if (!startVendorHal()) {
                return false;
            }
            if (TextUtils.isEmpty(createStaIface(null, null,
                    concreteClientModeManager))) {
                stopVendorHal();
                return false;
            }
            return true;
        }
    }

    /**
     * Bring up the Vendor HAL.
     * @return true on success, false otherwise.
     */
    public boolean startVendorHal() {
        synchronized (sLock) {
            if (!mHalDeviceManager.start()) {
                mLog.err("Failed to start vendor HAL").flush();
                return false;
            }
            mLog.info("Vendor Hal started successfully").flush();
            return true;
        }
    }


    /** Helper method to lookup the corresponding STA iface object using iface name. */
    private WifiStaIface getStaIface(@NonNull String ifaceName) {
        synchronized (sLock) {
            return mWifiStaIfaces.get(ifaceName);
        }
    }

    private class StaInterfaceDestroyedListenerInternal implements InterfaceDestroyedListener {
        private final InterfaceDestroyedListener mExternalListener;

        StaInterfaceDestroyedListenerInternal(InterfaceDestroyedListener externalListener) {
            mExternalListener = externalListener;
        }

        @Override
        public void onDestroyed(@NonNull String ifaceName) {
            synchronized (sLock) {
                mWifiStaIfaces.remove(ifaceName);
            }
            if (mExternalListener != null) {
                mExternalListener.onDestroyed(ifaceName);
            }
        }
    }

    /**
     * Create a STA iface using {@link HalDeviceManager}.
     *
     * @param destroyedListener Listener to be invoked when the interface is destroyed.
     * @param requestorWs Requestor worksource.
     * @param concreteClientModeManager ConcreteClientModeManager requesting the interface.
     * @return iface name on success, null otherwise.
     */
    public String createStaIface(@Nullable InterfaceDestroyedListener destroyedListener,
            @NonNull WorkSource requestorWs,
            @NonNull ConcreteClientModeManager concreteClientModeManager) {
        synchronized (sLock) {
            WifiStaIface iface = mHalDeviceManager.createStaIface(
                    new StaInterfaceDestroyedListenerInternal(destroyedListener), mHalEventHandler,
                    requestorWs, concreteClientModeManager);
            if (iface == null) {
                mLog.err("Failed to create STA iface").flush();
                return null;
            }
            String ifaceName = iface.getName();
            if (TextUtils.isEmpty(ifaceName)) {
                mLog.err("Failed to get iface name").flush();
                return null;
            }
            if (!registerStaIfaceCallback(iface)) {
                mLog.err("Failed to register STA iface callback").flush();
                return null;
            }
            if (!retrieveWifiChip(iface)) {
                mLog.err("Failed to get wifi chip").flush();
                return null;
            }
            mWifiStaIfaces.put(ifaceName, iface);
            return ifaceName;
        }
    }

    /**
     * Replace the requestor worksource info for a STA iface using {@link HalDeviceManager}.
     *
     * @param ifaceName Name of the interface being removed.
     * @param requestorWs Requestor worksource.
     * @return true on success, false otherwise.
     */
    public boolean replaceStaIfaceRequestorWs(@NonNull String ifaceName,
            @NonNull WorkSource requestorWs) {
        synchronized (sLock) {
            WifiStaIface iface = getStaIface(ifaceName);
            if (iface == null) return false;

            if (!mHalDeviceManager.replaceRequestorWs(iface, requestorWs)) {
                mLog.err("Failed to replace requestor worksource for STA iface").flush();
                return false;
            }
            return true;
        }
    }

    /**
     * Remove a STA iface using {@link HalDeviceManager}.
     *
     * @param ifaceName Name of the interface being removed.
     * @return true on success, false otherwise.
     */
    public boolean removeStaIface(@NonNull String ifaceName) {
        synchronized (sLock) {
            WifiStaIface iface = getStaIface(ifaceName);
            if (iface == null) return false;
            if (!mHalDeviceManager.removeIface(iface)) {
                mLog.err("Failed to remove STA iface").flush();
                return false;
            }
            mWifiStaIfaces.remove(ifaceName);
            return true;
        }
    }

    /** Helper method to lookup the corresponding AP iface object using iface name. */
    private WifiApIface getApIface(@NonNull String ifaceName) {
        synchronized (sLock) {
            return mWifiApIfaces.get(ifaceName);
        }
    }

    private class ApInterfaceDestroyedListenerInternal implements InterfaceDestroyedListener {
        private final InterfaceDestroyedListener mExternalListener;

        ApInterfaceDestroyedListenerInternal(InterfaceDestroyedListener externalListener) {
            mExternalListener = externalListener;
        }

        @Override
        public void onDestroyed(@NonNull String ifaceName) {
            synchronized (sLock) {
                mWifiApIfaces.remove(ifaceName);
            }
            if (mExternalListener != null) {
                mExternalListener.onDestroyed(ifaceName);
            }
        }
    }

    private long getNecessaryCapabilitiesForSoftApMode(@SoftApConfiguration.BandType int band) {
        long caps = HalDeviceManager.CHIP_CAPABILITY_ANY;
        if ((band & SoftApConfiguration.BAND_60GHZ) != 0) {
            caps |= WifiManager.WIFI_FEATURE_INFRA_60G;
        }
        return caps;
    }

    /**
     * Create an AP iface using {@link HalDeviceManager}.
     *
     * @param destroyedListener Listener to be invoked when the interface is destroyed.
     * @param requestorWs Requestor worksource.
     * @param band The requesting band for this AP interface.
     * @param isBridged Whether or not AP interface is a bridge interface.
     * @param softApManager SoftApManager of the request.
     * @param vendorData List of {@link OuiKeyedData} containing vendor-provided
     *                   configuration data. Empty list indicates no vendor data.
     * @return iface name on success, null otherwise.
     */
    public String createApIface(@Nullable InterfaceDestroyedListener destroyedListener,
            @NonNull WorkSource requestorWs,
            @SoftApConfiguration.BandType int band,
            boolean isBridged,
            @NonNull SoftApManager softApManager, @NonNull List<OuiKeyedData> vendorData) {
        synchronized (sLock) {
            WifiApIface iface = mHalDeviceManager.createApIface(
                    getNecessaryCapabilitiesForSoftApMode(band),
                    new ApInterfaceDestroyedListenerInternal(destroyedListener), mHalEventHandler,
                    requestorWs, isBridged, softApManager, vendorData);
            if (iface == null) {
                mLog.err("Failed to create AP iface").flush();
                return null;
            }
            String ifaceName = iface.getName();
            if (TextUtils.isEmpty(ifaceName)) {
                mLog.err("Failed to get iface name").flush();
                return null;
            }
            if (!retrieveWifiChip(iface)) {
                mLog.err("Failed to get wifi chip").flush();
                return null;
            }
            mWifiApIfaces.put(ifaceName, iface);
            return ifaceName;
        }
    }

    /**
     * Replace the requestor worksource info for an AP iface using {@link HalDeviceManager}.
     *
     * @param ifaceName Name of the interface being removed.
     * @param requestorWs Requestor worksource.
     * @return true on success, false otherwise.
     */
    public boolean replaceApIfaceRequestorWs(@NonNull String ifaceName,
            @NonNull WorkSource requestorWs) {
        synchronized (sLock) {
            WifiApIface iface = getApIface(ifaceName);
            if (iface == null) return false;

            if (!mHalDeviceManager.replaceRequestorWs(iface, requestorWs)) {
                mLog.err("Failed to replace requestor worksource for AP iface").flush();
                return false;
            }
            return true;
        }
    }

    /**
     * Remove an AP iface using {@link HalDeviceManager}.
     *
     * @param ifaceName Name of the interface being removed.
     * @return true on success, false otherwise.
     */
    public boolean removeApIface(@NonNull String ifaceName) {
        synchronized (sLock) {
            WifiApIface iface = getApIface(ifaceName);
            if (iface == null) return false;

            if (!mHalDeviceManager.removeIface(iface)) {
                mLog.err("Failed to remove AP iface").flush();
                return false;
            }
            mWifiApIfaces.remove(ifaceName);
            return true;
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
        if (mWifiChip == null) return false;
        return mWifiChip.removeIfaceInstanceFromBridgedApIface(ifaceName, apIfaceInstance);
    }

    /**
     * Set the current coex unsafe channels to avoid and their restrictions.
     * @param unsafeChannels List of {@link android.net.wifi.CoexUnsafeChannel} to avoid.
     * @param restrictions int containing a bitwise-OR combination of
     *                     {@link WifiManager.CoexRestriction}.
     * @return true if the operation succeeded, false if there is an error in Hal.
     */
    public boolean setCoexUnsafeChannels(
            @NonNull List<android.net.wifi.CoexUnsafeChannel> unsafeChannels, int restrictions) {
        if (mWifiChip == null) return false;
        return mWifiChip.setCoexUnsafeChannels(unsafeChannels, restrictions);
    }

    private boolean retrieveWifiChip(WifiHal.WifiInterface iface) {
        synchronized (sLock) {
            boolean registrationNeeded = mWifiChip == null;
            mWifiChip = mHalDeviceManager.getChip(iface);
            if (mWifiChip == null) {
                mLog.err("Failed to get the chip created for the Iface").flush();
                return false;
            }
            if (!registrationNeeded) {
                return true;
            }
            if (!registerChipCallback()) {
                mLog.err("Failed to register chip callback").flush();
                mWifiChip = null;
                return false;
            }
            cacheWifiChipInfo(mWifiChip);
            return true;
        }
    }

    /**
     * Registers the sta iface callback.
     */
    private boolean registerStaIfaceCallback(WifiStaIface iface) {
        synchronized (sLock) {
            if (iface == null) return false;
            if (mWifiStaIfaceEventCallback == null) return false;
            return iface.registerFrameworkCallback(mWifiStaIfaceEventCallback);
        }
    }

    /**
     * Registers the sta iface callback.
     */
    private boolean registerChipCallback() {
        synchronized (sLock) {
            if (mWifiChip == null) return false;
            return mWifiChip.registerCallback(mWifiChipEventCallback);
        }
    }

    /**
     * Stops the HAL
     */
    public void stopVendorHal() {
        synchronized (sLock) {
            mHalDeviceManager.stop();
            clearState();
            mLog.info("Vendor Hal stopped").flush();
        }
    }

    /**
     * Clears the state associated with a started Iface
     *
     * Caller should hold the lock.
     */
    private void clearState() {
        mWifiChip = null;
        mWifiStaIfaces.clear();
        mWifiApIfaces.clear();
        mDriverDescription = null;
        mFirmwareDescription = null;
    }

    /**
     * Tests whether the HAL is started and at least one iface is up.
     */
    public boolean isHalStarted() {
        // For external use only. Methods in this class should test for null directly.
        synchronized (sLock) {
            return (!mWifiStaIfaces.isEmpty() || !mWifiApIfaces.isEmpty());
        }
    }

    /**
     * Gets the scan capabilities
     *
     * @param ifaceName Name of the interface.
     * @param capabilities object to be filled in
     * @return true for success, false for failure
     */
    public boolean getBgScanCapabilities(
            @NonNull String ifaceName, WifiNative.ScanCapabilities capabilities) {
        synchronized (sLock) {
            WifiStaIface iface = getStaIface(ifaceName);
            if (iface == null) return false;

            WifiNative.ScanCapabilities result = iface.getBackgroundScanCapabilities();
            if (result != null) {
                capabilities.max_scan_cache_size = result.max_scan_cache_size;
                capabilities.max_ap_cache_per_scan = result.max_ap_cache_per_scan;
                capabilities.max_scan_buckets = result.max_scan_buckets;
                capabilities.max_rssi_sample_size = result.max_rssi_sample_size;
                capabilities.max_scan_reporting_threshold = result.max_scan_reporting_threshold;
            }
            return result != null;
        }
    }

    /**
     * Holds the current background scan state, to implement pause and restart
     */
    @VisibleForTesting
    class CurrentBackgroundScan {
        public int cmdId;
        public WifiStaIface.StaBackgroundScanParameters param;
        public WifiNative.ScanEventHandler eventHandler = null;
        public boolean paused = false;
        public WifiScanner.ScanData[] latestScanResults = null;

        CurrentBackgroundScan(int id, WifiNative.ScanSettings settings) {
            cmdId = id;
            List<WifiNative.BucketSettings> buckets = new ArrayList<>();
            if (settings.buckets != null) {
                buckets = Arrays.asList(settings.buckets);
            }
            param = new WifiStaIface.StaBackgroundScanParameters(settings.base_period_ms,
                    settings.max_ap_per_scan, settings.report_threshold_percent,
                    settings.report_threshold_num_scans, buckets);
        }
    }

    private int mLastScanCmdId; // For assigning cmdIds to scans

    @VisibleForTesting
    CurrentBackgroundScan mScan = null;

    /**
     * Starts a background scan
     *
     * Any ongoing scan will be stopped first
     *
     * @param ifaceName    Name of the interface.
     * @param settings     to control the scan
     * @param eventHandler to call with the results
     * @return true for success
     */
    public boolean startBgScan(@NonNull String ifaceName,
                               WifiNative.ScanSettings settings,
                               WifiNative.ScanEventHandler eventHandler) {
        if (eventHandler == null) return false;
        synchronized (sLock) {
            WifiStaIface iface = getStaIface(ifaceName);
            if (iface == null) return false;
            if (mScan != null && !mScan.paused) {
                iface.stopBackgroundScan(mScan.cmdId);
                mScan = null;
            }

            mLastScanCmdId = (mLastScanCmdId % 9) + 1; // cycle through non-zero single digits
            CurrentBackgroundScan scan = new CurrentBackgroundScan(mLastScanCmdId, settings);
            boolean success = iface.startBackgroundScan(scan.cmdId, scan.param);
            if (!success) return false;

            scan.eventHandler = eventHandler;
            mScan = scan;
            return true;
        }
    }


    /**
     * Stops any ongoing background scan
     *
     * @param ifaceName Name of the interface.
     */
    public void stopBgScan(@NonNull String ifaceName) {
        synchronized (sLock) {
            WifiStaIface iface = getStaIface(ifaceName);
            if (iface == null) return;
            if (mScan != null) {
                iface.stopBackgroundScan(mScan.cmdId);
                mScan = null;
            }
        }
    }

    /**
     * Pauses an ongoing background scan
     *
     * @param ifaceName Name of the interface.
     */
    public void pauseBgScan(@NonNull String ifaceName) {
        synchronized (sLock) {
            WifiStaIface iface = getStaIface(ifaceName);
            if (iface == null) return;
            if (mScan != null && !mScan.paused) {
                if (!iface.stopBackgroundScan(mScan.cmdId)) return;
                mScan.paused = true;
            }
        }
    }

    /**
     * Restarts a paused background scan
     *
     * @param ifaceName Name of the interface.
     */
    public void restartBgScan(@NonNull String ifaceName) {
        synchronized (sLock) {
            WifiStaIface iface = getStaIface(ifaceName);
            if (iface == null) return;
            if (mScan != null && mScan.paused) {
                if (!iface.startBackgroundScan(mScan.cmdId, mScan.param)) return;
                mScan.paused = false;
            }
        }
    }

    /**
     * Gets the latest scan results received from the HAL interface callback.
     *
     * @param ifaceName Name of the interface.
     */
    public WifiScanner.ScanData[] getBgScanResults(@NonNull String ifaceName) {
        synchronized (sLock) {
            WifiStaIface iface = getStaIface(ifaceName);
            if (iface == null) return null;
            if (mScan == null) return null;
            return mScan.latestScanResults;
        }
    }

    /**
     * Gets the cached scan data.
     *
     * @param ifaceName Name of the interface.
     */
    @Nullable
    public WifiScanner.ScanData getCachedScanData(@NonNull String ifaceName) {
        synchronized (sLock) {
            WifiStaIface iface = getStaIface(ifaceName);
            if (iface == null) return null;
            return iface.getCachedScanData();
        }
    }

    /**
     * Get the link layer statistics
     *
     * Note - we always enable link layer stats on a STA interface.
     *
     * @param ifaceName Name of the interface.
     * @return the statistics, or null if unable to do so
     */
    public WifiLinkLayerStats getWifiLinkLayerStats(@NonNull String ifaceName) {
        synchronized (sLock) {
            WifiStaIface iface = getStaIface(ifaceName);
            if (iface == null) return null;
            return iface.getLinkLayerStats();
        }
    }

    @VisibleForTesting
    boolean mLinkLayerStatsDebug = false;  // Passed to Hal

    /**
     * Enables the linkLayerStats in the Hal.
     *
     * This is called unconditionally whenever we create a STA interface.
     *
     * @param ifaceName Name of the interface.
     */
    public void enableLinkLayerStats(@NonNull String ifaceName) {
        synchronized (sLock) {
            WifiStaIface iface = getStaIface(ifaceName);
            if (iface == null) {
                mLog.err("STA iface object is NULL - Failed to enable link layer stats")
                        .flush();
                return;
            }
            if (!iface.enableLinkLayerStatsCollection(mLinkLayerStatsDebug)) {
                mLog.err("unable to enable link layer stats collection").flush();
            }
        }
    }

    /**
     * Translation table used by getSupportedFeatureSetFromPackageManager
     * for translating System caps
     */
    private static final Pair[] sSystemFeatureCapabilityTranslation = new Pair[] {
            Pair.create(WifiManager.WIFI_FEATURE_INFRA, PackageManager.FEATURE_WIFI),
            Pair.create(WifiManager.WIFI_FEATURE_P2P, PackageManager.FEATURE_WIFI_DIRECT),
            Pair.create(WifiManager.WIFI_FEATURE_AWARE, PackageManager.FEATURE_WIFI_AWARE),
    };

    /**
     * If VendorHal is not supported, reading PackageManager
     * system features to return basic capabilities.
     *
     * @return bitmask defined by WifiManager.WIFI_FEATURE_*
     */
    private long getSupportedFeatureSetFromPackageManager() {
        long featureSet = 0;
        final PackageManager pm = sContext.getPackageManager();
        for (Pair pair: sSystemFeatureCapabilityTranslation) {
            if (pm.hasSystemFeature((String) pair.second)) {
                featureSet |= (long) pair.first;
            }
        }
        enter("System feature set: %").c(featureSet).flush();
        return featureSet;
    }

    /**
     * Get maximum number of links supported by the chip for MLO association.
     *
     * @param ifaceName Name of the interface.
     * @return maximum number of association links or -1 if error or not available.
     */
    public int getMaxMloAssociationLinkCount(String ifaceName) {
        WifiChipInfo wifiChipInfo = getCachedWifiChipInfo(
                ifaceName);
        if (wifiChipInfo == null || wifiChipInfo.capabilities == null) return -1;
        return wifiChipInfo.capabilities.maxMloAssociationLinkCount;
    }

    /**
     * Get the maximum number of STR links used in Multi-Link Operation.
     *
     * @param ifaceName Name of the interface.
     * @return maximum number of MLO STR links or -1 if error or not available.
     */
    public int getMaxMloStrLinkCount(String ifaceName) {
        WifiChipInfo wifiChipInfo = getCachedWifiChipInfo(
                ifaceName);
        if (wifiChipInfo == null || wifiChipInfo.capabilities == null) return -1;
        return wifiChipInfo.capabilities.maxMloStrLinkCount;
    }

    /**
     * Get the maximum number of concurrent TDLS sessions supported by the device.
     *
     * @param ifaceName Name of the interface.
     * @return maximum number of concurrent TDLS sessions or -1 if error or not available.
     */
    public int getMaxSupportedConcurrentTdlsSessions(String ifaceName) {
        WifiChipInfo wifiChipInfo = getCachedWifiChipInfo(
                ifaceName);
        if (wifiChipInfo == null || wifiChipInfo.capabilities == null) return -1;
        return wifiChipInfo.capabilities.maxConcurrentTdlsSessionCount;
    }

    /**
     * Get Chip specific cached info.
     *
     * @param ifaceName Name of the interface
     * @return the cached information.
     */
    private WifiChipInfo getCachedWifiChipInfo(String ifaceName) {
        WifiStaIface iface = getStaIface(ifaceName);
        if (iface == null) return null;

        WifiChip chip = mHalDeviceManager.getChip(iface);
        if (chip == null) return null;

        return mCachedWifiChipInfos.get(chip.getId());
    }

    /**
     * Cache chip specific info.
     *
     * @param chip Wi-Fi chip
     */
    private void cacheWifiChipInfo(@NonNull WifiChip chip) {
        if (mCachedWifiChipInfos.contains(chip.getId())) return;
        WifiChipInfo wifiChipInfo = new WifiChipInfo();
        wifiChipInfo.capabilities = chip.getWifiChipCapabilities();
        mCachedWifiChipInfos.put(chip.getId(), wifiChipInfo);
    }

    /**
     * Get the supported features
     *
     * The result may differ depending on the mode (STA or AP)
     *
     * @param ifaceName Name of the interface.
     * @return bitmask defined by WifiManager.WIFI_FEATURE_*
     */
    public long getSupportedFeatureSet(@NonNull String ifaceName) {
        long featureSet = 0L;
        if (!mHalDeviceManager.isStarted() || !mHalDeviceManager.isSupported()) {
            return getSupportedFeatureSetFromPackageManager();
        }

        synchronized (sLock) {
            if (mWifiChip != null) {
                WifiChip.Response<Long> capsResp = mWifiChip.getCapabilitiesAfterIfacesExist();
                if (capsResp.getStatusCode() == WifiHal.WIFI_STATUS_SUCCESS) {
                    featureSet = capsResp.getValue();
                } else if (capsResp.getStatusCode() == WifiHal.WIFI_STATUS_ERROR_REMOTE_EXCEPTION) {
                    return 0;
                }
            }

            WifiStaIface iface = getStaIface(ifaceName);
            if (iface != null) {
                featureSet |= iface.getCapabilities();
                if (mHalDeviceManager.is24g5gDbsSupported(iface)
                        || mHalDeviceManager.is5g6gDbsSupported(iface)) {
                    featureSet |= WifiManager.WIFI_FEATURE_DUAL_BAND_SIMULTANEOUS;
                }
            }
        }

        if (mWifiGlobals.isWpa3SaeH2eSupported()) {
            featureSet |= WifiManager.WIFI_FEATURE_SAE_H2E;
        }

        Set<Integer> supportedIfaceTypes = mHalDeviceManager.getSupportedIfaceTypes();
        if (supportedIfaceTypes.contains(WifiChip.IFACE_TYPE_STA)) {
            featureSet |= WifiManager.WIFI_FEATURE_INFRA;
        }
        if (supportedIfaceTypes.contains(WifiChip.IFACE_TYPE_AP)) {
            featureSet |= WifiManager.WIFI_FEATURE_MOBILE_HOTSPOT;
        }
        if (supportedIfaceTypes.contains(WifiChip.IFACE_TYPE_P2P)) {
            featureSet |= WifiManager.WIFI_FEATURE_P2P;
        }
        if (supportedIfaceTypes.contains(WifiChip.IFACE_TYPE_NAN)) {
            featureSet |= WifiManager.WIFI_FEATURE_AWARE;
        }

        return featureSet;
    }

    /**
     * Set Mac address on the given interface
     *
     * @param ifaceName Name of the interface
     * @param mac MAC address to change into
     * @return true for success
     */
    public boolean setStaMacAddress(@NonNull String ifaceName, @NonNull MacAddress mac) {
        synchronized (sLock) {
            WifiStaIface iface = getStaIface(ifaceName);
            if (iface == null) return false;
            return iface.setMacAddress(mac);
        }
    }

    /**
     * Reset MAC address to factory MAC address on the given interface
     *
     * @param ifaceName Name of the interface
     * @return true for success
     */
    public boolean resetApMacToFactoryMacAddress(@NonNull String ifaceName) {
        synchronized (sLock) {
            WifiApIface iface = getApIface(ifaceName);
            if (iface == null) return false;
            return iface.resetToFactoryMacAddress();
        }
    }

    /**
     * Set Mac address on the given interface
     *
     * @param ifaceName Name of the interface
     * @param mac MAC address to change into
     * @return true for success
     */
    public boolean setApMacAddress(@NonNull String ifaceName, @NonNull MacAddress mac) {
        synchronized (sLock) {
            WifiApIface iface = getApIface(ifaceName);
            if (iface == null) return false;
            return iface.setMacAddress(mac);
        }
    }

    /**
     * Returns true if Hal version supports setMacAddress, otherwise false.
     *
     * @param ifaceName Name of the interface
     */
    public boolean isApSetMacAddressSupported(@NonNull String ifaceName) {
        synchronized (sLock) {
            WifiApIface iface = getApIface(ifaceName);
            if (iface == null) return false;
            return iface.isSetMacAddressSupported();
        }
    }

    /**
     * Get factory MAC address of the given interface
     *
     * @param ifaceName Name of the interface
     * @return factory MAC address of the interface or null.
     */
    public MacAddress getStaFactoryMacAddress(@NonNull String ifaceName) {
        synchronized (sLock) {
            WifiStaIface iface = getStaIface(ifaceName);
            if (iface == null) return null;
            return iface.getFactoryMacAddress();
        }
    }

    /**
     * Get factory MAC address of the given interface
     *
     * @param ifaceName Name of the interface
     * @return factory MAC address of the interface or null.
     */
    public MacAddress getApFactoryMacAddress(@NonNull String ifaceName) {
        synchronized (sLock) {
            WifiApIface iface = getApIface(ifaceName);
            if (iface == null) return null;
            return iface.getFactoryMacAddress();
        }
    }

    /**
     * Get the APF (Android Packet Filter) capabilities of the device
     *
     * @param ifaceName Name of the interface.
     * @return APF capabilities object.
     */
    public ApfCapabilities getApfCapabilities(@NonNull String ifaceName) {
        synchronized (sLock) {
            WifiStaIface iface = getStaIface(ifaceName);
            if (iface == null) return sNoApfCapabilities;
            return iface.getApfPacketFilterCapabilities();
        }
    }

    private static final ApfCapabilities sNoApfCapabilities = new ApfCapabilities(0, 0, 0);

    /**
     * Installs an APF program on this iface, replacing any existing program.
     *
     * @param ifaceName Name of the interface.
     * @param filter is the android packet filter program
     * @return true for success
     */
    public boolean installPacketFilter(@NonNull String ifaceName, byte[] filter) {
        if (filter == null) return false;
        enter("filter length %").c(filter.length).flush();
        synchronized (sLock) {
            WifiStaIface iface = getStaIface(ifaceName);
            if (iface == null) return false;
            return iface.installApfPacketFilter(filter);
        }
    }

    /**
     * Reads the APF program and data buffer on this iface.
     *
     * @param ifaceName Name of the interface
     * @return the buffer returned by the driver, or null in case of an error
     */
    public byte[] readPacketFilter(@NonNull String ifaceName) {
        enter("").flush();
        // TODO: Must also take the wakelock here to prevent going to sleep with APF disabled.
        synchronized (sLock) {
            WifiStaIface iface = getStaIface(ifaceName);
            if (iface == null) return null;
            return iface.readApfPacketFilterData();
        }
    }

    /**
     * Set country code for this Wifi chip
     *
     * @param countryCode - two-letter country code (as ISO 3166)
     * @return true for success
     */
    public boolean setChipCountryCode(String countryCode) {
        synchronized (sLock) {
            if (mWifiChip == null) return false;
            return mWifiChip.setCountryCode(countryCode);
        }
    }

    /**
     * Get the names of the bridged AP instances.
     *
     * @param ifaceName Name of the bridged interface.
     * @return A list which contains the names of the bridged AP instances.
     */
    @Nullable
    public List<String> getBridgedApInstances(@NonNull String ifaceName) {
        synchronized (sLock) {
            WifiApIface iface = getApIface(ifaceName);
            if (iface == null) return null;
            return iface.getBridgedInstances();
        }
    }

    /**
     * Set country code for this AP iface.
     *
     * @param ifaceName Name of the interface.
     * @param countryCode - two-letter country code (as ISO 3166)
     * @return true for success
     */
    public boolean setApCountryCode(@NonNull String ifaceName, String countryCode) {
        synchronized (sLock) {
            WifiApIface iface = getApIface(ifaceName);
            if (iface == null) return false;
            return iface.setCountryCode(countryCode);
        }
    }

    private WifiNative.WifiLoggerEventHandler mLogEventHandler = null;

    /**
     * Registers the logger callback and enables alerts.
     * Ring buffer data collection is only triggered when |startLoggingRingBuffer| is invoked.
     */
    public boolean setLoggingEventHandler(WifiNative.WifiLoggerEventHandler handler) {
        if (handler == null) return false;
        synchronized (sLock) {
            if (mWifiChip == null) return false;
            if (mLogEventHandler != null) return false;
            if (!mWifiChip.enableDebugErrorAlerts(true)) {
                return false;
            }
            mLogEventHandler = handler;
            return true;
        }
    }

    /**
     * Stops all logging and resets the logger callback.
     * This stops both the alerts and ring buffer data collection.
     * Existing log handler is cleared.
     */
    public boolean resetLogHandler() {
        synchronized (sLock) {
            mLogEventHandler = null;
            if (mWifiChip == null) return false;
            if (!mWifiChip.enableDebugErrorAlerts(false)) {
                return false;
            }
            return mWifiChip.stopLoggingToDebugRingBuffer();
        }
    }

    /**
     * Control debug data collection
     *
     * @param verboseLevel       0 to 3, inclusive. 0 stops logging.
     * @param flags              Ignored.
     * @param maxIntervalInSec   Maximum interval between reports; ignore if 0.
     * @param minDataSizeInBytes Minimum data size in buffer for report; ignore if 0.
     * @param ringName           Name of the ring for which data collection is to start.
     * @return true for success
     */
    public boolean startLoggingRingBuffer(int verboseLevel, int flags, int maxIntervalInSec,
                                          int minDataSizeInBytes, String ringName) {
        enter("verboseLevel=%, flags=%, maxIntervalInSec=%, minDataSizeInBytes=%, ringName=%")
                .c(verboseLevel).c(flags).c(maxIntervalInSec).c(minDataSizeInBytes).c(ringName)
                .flush();
        synchronized (sLock) {
            if (mWifiChip == null) return false;
            return mWifiChip.startLoggingToDebugRingBuffer(
                    ringName,
                    verboseLevel,
                    maxIntervalInSec,
                    minDataSizeInBytes
            );
        }
    }

    /**
     * Pointlessly fail
     *
     * @return -1
     */
    public int getSupportedLoggerFeatureSet() {
        return -1;
    }

    private String mDriverDescription; // Cached value filled by requestChipDebugInfo()

    /**
     * Vendor-provided wifi driver version string
     */
    public String getDriverVersion() {
        synchronized (sLock) {
            if (mDriverDescription == null) requestChipDebugInfo();
            return mDriverDescription;
        }
    }

    private String mFirmwareDescription; // Cached value filled by requestChipDebugInfo()

    /**
     * Vendor-provided wifi firmware version string
     */
    public String getFirmwareVersion() {
        synchronized (sLock) {
            if (mFirmwareDescription == null) requestChipDebugInfo();
            return mFirmwareDescription;
        }
    }

    /**
     * Refreshes our idea of the driver and firmware versions
     */
    private void requestChipDebugInfo() {
        mDriverDescription = null;
        mFirmwareDescription = null;
        synchronized (sLock) {
            if (mWifiChip == null) return;
            WifiChip.ChipDebugInfo info = mWifiChip.requestChipDebugInfo();
            if (info == null) return;
            mDriverDescription = info.driverDescription;
            mFirmwareDescription = info.firmwareDescription;
        }
        mLog.info("Driver: % Firmware: %")
                .c(mDriverDescription)
                .c(mFirmwareDescription)
                .flush();
    }

    /**
     * API to get the status of all ring buffers supported by driver
     */
    public WifiNative.RingBufferStatus[] getRingBufferStatus() {
        synchronized (sLock) {
            if (mWifiChip == null) return null;
            List<WifiNative.RingBufferStatus> statusList = mWifiChip.getDebugRingBuffersStatus();
            if (statusList == null) return null;

            WifiNative.RingBufferStatus[] statusArray =
                    new WifiNative.RingBufferStatus[statusList.size()];
            statusList.toArray(statusArray);
            return statusArray;
        }
    }

    /**
     * Indicates to driver that all the data has to be uploaded urgently
     */
    public boolean getRingBufferData(String ringName) {
        enter("ringName %").c(ringName).flush();
        synchronized (sLock) {
            if (mWifiChip == null) return false;
            return mWifiChip.forceDumpToDebugRingBuffer(ringName);
        }
    }

    /**
     * request hal to flush ring buffers to files
     */
    public boolean flushRingBufferData() {
        synchronized (sLock) {
            if (mWifiChip == null) return false;
            return mWifiChip.flushRingBufferToFile();
        }
    }

    /**
     * Request vendor debug info from the firmware
     */
    public byte[] getFwMemoryDump() {
        synchronized (sLock) {
            if (mWifiChip == null) return null;
            return mWifiChip.requestFirmwareDebugDump();
        }
    }

    /**
     * Request vendor debug info from the driver
     */
    public byte[] getDriverStateDump() {
        synchronized (sLock) {
            if (mWifiChip == null) return (null);
            return mWifiChip.requestDriverDebugDump();
        }
    }

    /**
     * Start packet fate monitoring
     * <p>
     * Once started, monitoring remains active until HAL is unloaded.
     *
     * @param ifaceName Name of the interface.
     * @return true for success
     */
    public boolean startPktFateMonitoring(@NonNull String ifaceName) {
        synchronized (sLock) {
            WifiStaIface iface = getStaIface(ifaceName);
            if (iface == null) return false;
            return iface.startDebugPacketFateMonitoring();
        }
    }

    /**
     * Retrieve fates of outbound packets
     * <p>
     * Reports the outbound frames for the most recent association (space allowing).
     *
     * @param ifaceName Name of the interface.
     * @return list of TxFateReports up to size {@link WifiLoggerHal#MAX_FATE_LOG_LEN}, or empty
     * list on failure.
     */
    public List<TxFateReport> getTxPktFates(@NonNull String ifaceName) {
        synchronized (sLock) {
            WifiStaIface iface = getStaIface(ifaceName);
            if (iface == null) return new ArrayList<>();
            return iface.getDebugTxPacketFates();
        }
    }

    /**
     * Retrieve fates of inbound packets
     * <p>
     * Reports the inbound frames for the most recent association (space allowing).
     *
     * @param ifaceName Name of the interface.
     * @return list of RxFateReports up to size {@link WifiLoggerHal#MAX_FATE_LOG_LEN}, or empty
     * list on failure.
     */
    public List<RxFateReport> getRxPktFates(@NonNull String ifaceName) {
        synchronized (sLock) {
            WifiStaIface iface = getStaIface(ifaceName);
            if (iface == null) return new ArrayList<>();
            return iface.getDebugRxPacketFates();
        }
    }

    /**
     * Start sending the specified keep alive packets periodically.
     *
     * @param ifaceName Name of the interface.
     * @param slot Command ID to use for this invocation.
     * @param srcAddr Source MAC address of the packet.
     * @param dstAddr Destination MAC address of the packet.
     * @param packet IP packet contents to be transmitted.
     * @param protocol Ether type to be set in the ethernet frame transmitted.
     * @param periodInMs Interval at which this packet must be transmitted.
     * @return 0 for success, -1 for error
     */
    public int startSendingOffloadedPacket(
            @NonNull String ifaceName, int slot, byte[] srcAddr, byte[] dstAddr,
            byte[] packet, int protocol, int periodInMs) {
        enter("slot=% periodInMs=%").c(slot).c(periodInMs).flush();
        synchronized (sLock) {
            WifiStaIface iface = getStaIface(ifaceName);
            if (iface == null) return -1;
            MacAddress srcMac, dstMac;
            try {
                srcMac = MacAddress.fromBytes(srcAddr);
                dstMac = MacAddress.fromBytes(dstAddr);
            } catch (IllegalArgumentException e) {
                mLog.info("Invalid MacAddress in startSendingOffloadedPacket").flush();
                return -1;
            }
            boolean success = iface.startSendingKeepAlivePackets(
                        slot,
                        packet,
                        (short) protocol,
                        srcMac,
                        dstMac,
                        periodInMs);
            return success ? 0 : -1;
        }
    }

    /**
     * Stop sending the specified keep alive packets.
     *
     * @param ifaceName Name of the interface.
     * @param slot id - same as startSendingOffloadedPacket call.
     * @return 0 for success, -1 for error
     */
    public int stopSendingOffloadedPacket(@NonNull String ifaceName, int slot) {
        enter("slot=%").c(slot).flush();

        synchronized (sLock) {
            WifiStaIface iface = getStaIface(ifaceName);
            if (iface == null) return -1;
            boolean success = iface.stopSendingKeepAlivePackets(slot);
            return success ? 0 : -1;
        }
    }

    /**
     * A fixed cmdId for our RssiMonitoring (we only do one at a time)
     */
    @VisibleForTesting
    static final int sRssiMonCmdId = 7551;

    /**
     * Our client's handler
     */
    private WifiNative.WifiRssiEventHandler mWifiRssiEventHandler;

    /**
     * Start RSSI monitoring on the currently connected access point.
     *
     * @param ifaceName        Name of the interface.
     * @param maxRssi          Maximum RSSI threshold.
     * @param minRssi          Minimum RSSI threshold.
     * @param rssiEventHandler Called when RSSI goes above maxRssi or below minRssi
     * @return 0 for success, -1 for failure
     */
    public int startRssiMonitoring(@NonNull String ifaceName, byte maxRssi, byte minRssi,
                                   WifiNative.WifiRssiEventHandler rssiEventHandler) {
        enter("maxRssi=% minRssi=%").c(maxRssi).c(minRssi).flush();
        if (maxRssi <= minRssi) return -1;
        if (rssiEventHandler == null) return -1;
        synchronized (sLock) {
            WifiStaIface iface = getStaIface(ifaceName);
            if (iface == null) return -1;
            iface.stopRssiMonitoring(sRssiMonCmdId);
            if (!iface.startRssiMonitoring(sRssiMonCmdId, maxRssi, minRssi)) return -1;
            mWifiRssiEventHandler = rssiEventHandler;
            return 0;
        }
    }

    /**
     * Stop RSSI monitoring
     *
     * @param ifaceName Name of the interface.
     * @return 0 for success, -1 for failure
     */
    public int stopRssiMonitoring(@NonNull String ifaceName) {
        synchronized (sLock) {
            mWifiRssiEventHandler = null;
            WifiStaIface iface = getStaIface(ifaceName);
            if (iface == null) return -1;
            boolean success = iface.stopRssiMonitoring(sRssiMonCmdId);
            return success ? 0 : -1;
        }
    }

    /**
     * Fetch the host wakeup reasons stats from wlan driver.
     *
     * @return the |WlanWakeReasonAndCounts| from the wlan driver, or null on failure.
     */
    public WlanWakeReasonAndCounts getWlanWakeReasonCount() {
        synchronized (sLock) {
            if (mWifiChip == null) return null;
            return mWifiChip.getDebugHostWakeReasonStats();
        }
    }

    /**
     * Enable/Disable Neighbour discovery offload functionality in the firmware.
     *
     * @param ifaceName Name of the interface.
     * @param enabled true to enable, false to disable.
     * @return true for success, false for failure
     */
    public boolean configureNeighborDiscoveryOffload(@NonNull String ifaceName, boolean enabled) {
        enter("enabled=%").c(enabled).flush();
        synchronized (sLock) {
            WifiStaIface iface = getStaIface(ifaceName);
            if (iface == null) return false;
            return iface.enableNdOffload(enabled);
        }
    }

    // Firmware roaming control.

    /**
     * Query the firmware roaming capabilities.
     *
     * @param ifaceName Name of the interface.
     * @return capabilities object on success, null otherwise.
     */
    @Nullable
    public WifiNative.RoamingCapabilities getRoamingCapabilities(@NonNull String ifaceName) {
        synchronized (sLock) {
            WifiStaIface iface = getStaIface(ifaceName);
            if (iface == null) return null;
            return iface.getRoamingCapabilities();
        }
    }

    /**
     * Enable/disable firmware roaming.
     *
     * @param ifaceName Name of the interface.
     * @param state the intended roaming state
     * @return SET_FIRMWARE_ROAMING_SUCCESS, SET_FIRMWARE_ROAMING_FAILURE,
     *         or SET_FIRMWARE_ROAMING_BUSY
     */
    public @WifiNative.RoamingEnableStatus int enableFirmwareRoaming(@NonNull String ifaceName,
            @WifiNative.RoamingEnableState int state) {
        synchronized (sLock) {
            WifiStaIface iface = getStaIface(ifaceName);
            if (iface == null) return WifiNative.SET_FIRMWARE_ROAMING_FAILURE;
            return iface.setRoamingState(state);
        }
    }

    /**
     * Set firmware roaming configurations.
     *
     * @param ifaceName Name of the interface.
     * @param config new roaming configuration object
     * @return true for success; false for failure
     */
    public boolean configureRoaming(@NonNull String ifaceName, WifiNative.RoamingConfig config) {
        synchronized (sLock) {
            WifiStaIface iface = getStaIface(ifaceName);
            if (iface == null) return false;
            try {
                // parse the blocklist BSSIDs if any
                List<MacAddress> bssidBlocklist = new ArrayList<>();
                if (config.blocklistBssids != null) {
                    for (String bssid : config.blocklistBssids) {
                        bssidBlocklist.add(MacAddress.fromString(bssid));
                    }
                }

                // parse the allowlist SSIDs if any
                List<byte[]> ssidAllowlist = new ArrayList<>();
                if (config.allowlistSsids != null) {
                    for (String ssidStr : config.allowlistSsids) {
                        for (WifiSsid originalSsid : mSsidTranslator.getAllPossibleOriginalSsids(
                                WifiSsid.fromString(ssidStr))) {
                            // HIDL code is throwing InvalidArgumentException when ssidWhitelist has
                            // SSIDs with less than 32 byte length this is due to HAL definition of
                            // SSID declared it as 32-byte fixed length array. Thus pad additional
                            // bytes with 0's to pass SSIDs as byte arrays of 32 length
                            ssidAllowlist.add(
                                    Arrays.copyOf(originalSsid.getBytes(), 32));
                        }
                    }
                }

                return iface.configureRoaming(bssidBlocklist, ssidAllowlist);
            } catch (IllegalArgumentException e) {
                mLog.err("Illegal argument for roaming configuration").c(e.toString()).flush();
                return false;
            }
        }
    }

    /**
     * Select one of the pre-configured TX power level scenarios or reset it back to normal.
     * Primarily used for meeting SAR requirements during voice calls.
     *
     * Note: If it was found out that the scenario to be reported is the same as last reported one,
     *       then exit with success.
     *       This is to handle the case when some HAL versions deal with different inputs equally,
     *       in that case, we should not call the hal unless there is a change in scenario.
     * Note: It is assumed that this method is only called if SAR is enabled. The logic of whether
     *       to call it or not resides in SarManager class.
     *
     * @param sarInfo The collection of inputs to select the SAR scenario.
     * @return true for success; false for failure or if the HAL version does not support this API.
     */
    public boolean selectTxPowerScenario(SarInfo sarInfo) {
        synchronized (sLock) {
            if (mWifiChip == null) return false;
            return mWifiChip.selectTxPowerScenario(sarInfo);
        }
    }

    /**
     * Enable/Disable low-latency mode
     *
     * @param enabled true to enable low-latency mode, false to disable it
     */
    public boolean setLowLatencyMode(boolean enabled) {
        synchronized (sLock) {
            if (mWifiChip == null) return false;
            return mWifiChip.setLowLatencyMode(enabled);
        }
    }

    /**
     * Returns whether the given HdmIfaceTypeForCreation combo is supported or not.
     */
    public boolean canDeviceSupportCreateTypeCombo(SparseArray<Integer> combo) {
        synchronized (sLock) {
            return mHalDeviceManager.canDeviceSupportCreateTypeCombo(combo);
        }
    }

    /**
     * Returns whether a new iface can be created without tearing down any existing ifaces.
     */
    public boolean canDeviceSupportAdditionalIface(
            @HalDeviceManager.HdmIfaceTypeForCreation int createIfaceType,
            @NonNull WorkSource requestorWs) {
        synchronized (sLock) {
            List<Pair<Integer, WorkSource>> creationImpact =
                    mHalDeviceManager.reportImpactToCreateIface(createIfaceType, true, requestorWs);
            return creationImpact != null && creationImpact.isEmpty();
        }
    }

    /**
     * Returns whether STA + AP concurrency is supported or not.
     */
    public boolean isStaApConcurrencySupported() {
        synchronized (sLock) {
            return mHalDeviceManager.canDeviceSupportCreateTypeCombo(new SparseArray<Integer>() {{
                    put(HDM_CREATE_IFACE_STA, 1);
                    put(HDM_CREATE_IFACE_AP, 1);
                }});
        }
    }

    /**
     * Returns whether STA + STA concurrency is supported or not.
     */
    public boolean isStaStaConcurrencySupported() {
        synchronized (sLock) {
            return mHalDeviceManager.canDeviceSupportCreateTypeCombo(new SparseArray<Integer>() {{
                    put(HDM_CREATE_IFACE_STA, 2);
                }});
        }
    }

    /**
     * Returns whether a new AP iface can be created or not.
     */
    public boolean isItPossibleToCreateApIface(@NonNull WorkSource requestorWs) {
        synchronized (sLock) {
            return mHalDeviceManager.isItPossibleToCreateIface(HDM_CREATE_IFACE_AP, requestorWs);
        }
    }

    /**
     * Returns whether a new AP iface can be created or not.
     */
    public boolean isItPossibleToCreateBridgedApIface(@NonNull WorkSource requestorWs) {
        synchronized (sLock) {
            return mHalDeviceManager.isItPossibleToCreateIface(
                    HDM_CREATE_IFACE_AP_BRIDGE, requestorWs);
        }
    }

    /**
     * Returns whether a new STA iface can be created or not.
     */
    public boolean isItPossibleToCreateStaIface(@NonNull WorkSource requestorWs) {
        synchronized (sLock) {
            return mHalDeviceManager.isItPossibleToCreateIface(HDM_CREATE_IFACE_STA, requestorWs);
        }

    }
    /**
     * Set primary connection when multiple STA ifaces are active.
     *
     * @param ifaceName Name of the interface.
     * @return true for success
     */
    public boolean setMultiStaPrimaryConnection(@NonNull String ifaceName) {
        if (TextUtils.isEmpty(ifaceName)) return false;
        synchronized (sLock) {
            if (mWifiChip == null) return false;
            return mWifiChip.setMultiStaPrimaryConnection(ifaceName);
        }
    }

    /**
     * Set use-case when multiple STA ifaces are active.
     *
     * @param useCase one of the use cases.
     * @return true for success
     */
    public boolean setMultiStaUseCase(@WifiNative.MultiStaUseCase int useCase) {
        synchronized (sLock) {
            if (mWifiChip == null) return false;
            return mWifiChip.setMultiStaUseCase(useCase);
        }
    }

    /**
     * Notify scan mode state to driver to save power in scan-only mode.
     *
     * @param ifaceName Name of the interface.
     * @param enable whether is in scan-only mode
     * @return true for success
     */
    public boolean setScanMode(@NonNull String ifaceName, boolean enable) {
        synchronized (sLock) {
            WifiStaIface iface = getStaIface(ifaceName);
            if (iface == null) return false;
            return iface.setScanMode(enable);
        }
    }

    /**
     * Callback for events on the STA interface.
     */
    private class StaIfaceEventCallback implements WifiStaIface.Callback {
        @Override
        public void onBackgroundScanFailure(int cmdId) {
            mVerboseLog.d("onBackgroundScanFailure " + cmdId);
            WifiNative.ScanEventHandler eventHandler;
            synchronized (sLock) {
                if (mScan == null || cmdId != mScan.cmdId) return;
                eventHandler = mScan.eventHandler;
            }
            eventHandler.onScanStatus(WifiNative.WIFI_SCAN_FAILED);
        }

        @Override
        public void onBackgroundFullScanResult(
                int cmdId, int bucketsScanned, ScanResult result) {
            mVerboseLog.d("onBackgroundFullScanResult " + cmdId);
            WifiNative.ScanEventHandler eventHandler;
            synchronized (sLock) {
                if (mScan == null || cmdId != mScan.cmdId) return;
                eventHandler = mScan.eventHandler;
            }
            eventHandler.onFullScanResult(result, bucketsScanned);
        }

        @Override
        public void onBackgroundScanResults(int cmdId, WifiScanner.ScanData[] scanDatas) {
            mVerboseLog.d("onBackgroundScanResults " + cmdId);
            WifiNative.ScanEventHandler eventHandler;
            // WifiScanner currently uses the results callback to fetch the scan results.
            // So, simulate that by sending out the notification and then caching the results
            // locally. This will then be returned to WifiScanner via getScanResults.
            synchronized (sLock) {
                if (mScan == null || cmdId != mScan.cmdId) return;
                eventHandler = mScan.eventHandler;
                mScan.latestScanResults = scanDatas;
            }
            eventHandler.onScanStatus(WifiNative.WIFI_SCAN_RESULTS_AVAILABLE);
        }

        @Override
        public void onRssiThresholdBreached(int cmdId, byte[/* 6 */] currBssid, int currRssi) {
            mVerboseLog.d("onRssiThresholdBreached " + cmdId + "currRssi " + currRssi);
            WifiNative.WifiRssiEventHandler eventHandler;
            synchronized (sLock) {
                if (mWifiRssiEventHandler == null || cmdId != sRssiMonCmdId) return;
                eventHandler = mWifiRssiEventHandler;
            }
            eventHandler.onRssiThresholdBreached((byte) currRssi);
        }

        /**
         * Called when a TWT operation fails.
         *
         * @param cmdId        Unique command id which is failed
         * @param twtErrorCode Error code
         */
        @Override
        public void onTwtFailure(int cmdId, int twtErrorCode) {
            synchronized (sLock) {
                mHalEventHandler.post(() -> {
                    if (mWifiTwtEvents == null) return;
                    mWifiTwtEvents.onTwtFailure(cmdId, twtErrorCode);
                });
            }
        }

        /**
         * Called when {@link WifiStaIface#setupTwtSession(int, TwtRequest)} succeeds.
         *
         * @param cmdId          Unique command id used in
         *                       {@link WifiStaIface#setupTwtSession(int, TwtRequest)}
         * @param wakeDurationUs TWT wake duration for the session in microseconds
         * @param wakeIntervalUs TWT wake interval for the session in microseconds
         * @param linkId         Multi link operation link id
         * @param sessionId      TWT session id
         */
        @Override
        public void onTwtSessionCreate(int cmdId, int wakeDurationUs, long wakeIntervalUs,
                int linkId, int sessionId) {
            synchronized (sLock) {
                mHalEventHandler.post(() -> {
                    if (mWifiTwtEvents == null) return;
                    mWifiTwtEvents.onTwtSessionCreate(cmdId, wakeDurationUs, wakeIntervalUs,
                            linkId,
                            sessionId);
                });
            }

        }

        /**
         * Called when TWT session is torndown by {@link WifiStaIface#tearDownTwtSession(int, int)}.
         * Can also be called unsolicitedly by the vendor software with proper reason code.
         *
         * @param cmdId        Unique command id used in
         *                     {@link WifiStaIface#tearDownTwtSession(int, int)}
         * @param twtSessionId TWT session Id
         */
        @Override
        public void onTwtSessionTeardown(int cmdId, int twtSessionId, int twtReasonCode) {
            synchronized (sLock) {
                mHalEventHandler.post(() -> {
                    if (mWifiTwtEvents == null) return;
                    mWifiTwtEvents.onTwtSessionTeardown(cmdId, twtSessionId, twtReasonCode);
                });
            }
        }

        /**
         * Called as a response to {@link WifiStaIface#getStatsTwtSession(int, int)}
         *
         * @param cmdId        Unique command id used in
         *                     {@link WifiStaIface#getStatsTwtSession(int, int)}
         * @param twtSessionId TWT session Id
         * @param twtStats     TWT stats bundle
         */
        @Override
        public void onTwtSessionStats(int cmdId, int twtSessionId, Bundle twtStats) {
            synchronized (sLock) {
                mHalEventHandler.post(() -> {
                    if (mWifiTwtEvents == null) return;
                    mWifiTwtEvents.onTwtSessionStats(cmdId, twtSessionId, twtStats);
                });
            }
        }
    }

    /**
     * Callback for events on the chip.
     */
    private class ChipEventCallback implements WifiChip.Callback {
        @Override
        public void onChipReconfigured(int modeId) {
            mVerboseLog.d("onChipReconfigured " + modeId);
        }

        @Override
        public void onChipReconfigureFailure(int status) {
            mVerboseLog.d("onChipReconfigureFailure " + status);
        }

        public void onIfaceAdded(int type, String name) {
            mVerboseLog.d("onIfaceAdded " + type + ", name: " + name);
        }

        @Override
        public void onIfaceRemoved(int type, String name) {
            mVerboseLog.d("onIfaceRemoved " + type + ", name: " + name);
        }

        @Override
        public void onDebugRingBufferDataAvailable(
                WifiNative.RingBufferStatus status, byte[] data) {
            mHalEventHandler.post(() -> {
                WifiNative.WifiLoggerEventHandler eventHandler;
                synchronized (sLock) {
                    if (mLogEventHandler == null || status == null || data == null) return;
                    eventHandler = mLogEventHandler;
                }
                // Because |sLock| has been released, there is a chance that we'll execute
                // a spurious callback (after someone has called resetLogHandler()).
                //
                // However, the alternative risks deadlock. Consider:
                // [T1.1] WifiDiagnostics.captureBugReport()
                // [T1.2] -- acquire WifiDiagnostics object's intrinsic lock
                // [T1.3]    -> WifiVendorHal.getRingBufferData()
                // [T1.4]       -- acquire WifiVendorHal.sLock
                // [T2.1] <lambda>()
                // [T2.2] -- acquire WifiVendorHal.sLock
                // [T2.3]    -> WifiDiagnostics.onRingBufferData()
                // [T2.4]       -- acquire WifiDiagnostics object's intrinsic lock
                //
                // The problem here is that the two threads acquire the locks in opposite order.
                // If, for example, T2.2 executes between T1.2 and 1.4, then T1 and T2
                // will be deadlocked.
                int sizeBefore = data.length;
                boolean conversionFailure = false;
                try {
                    eventHandler.onRingBufferData(status, data);
                    int sizeAfter = data.length;
                    if (sizeAfter != sizeBefore) {
                        conversionFailure = true;
                    }
                } catch (ArrayIndexOutOfBoundsException e) {
                    conversionFailure = true;
                }
                if (conversionFailure) {
                    Log.wtf("WifiVendorHal", "Conversion failure detected in "
                            + "onDebugRingBufferDataAvailable. "
                            + "The input ArrayList |data| is potentially corrupted. "
                            + "Starting size=" + sizeBefore + ", "
                            + "final size=" + data.length);
                }
            });
        }

        @Override
        public void onDebugErrorAlert(int errorCode, byte[] debugData) {
            mLog.w("onDebugErrorAlert " + errorCode);
            mHalEventHandler.post(() -> {
                WifiNative.WifiLoggerEventHandler eventHandler;
                synchronized (sLock) {
                    if (mLogEventHandler == null || debugData == null) return;
                    eventHandler = mLogEventHandler;
                }
                // See comment in onDebugRingBufferDataAvailable(), for an explanation
                // of why this callback is invoked without |sLock| held.
                eventHandler.onWifiAlert(errorCode, debugData);
            });
        }

        @Override
        public void onRadioModeChange(List<WifiChip.RadioModeInfo> radioModeInfoList) {
            mVerboseLog.d("onRadioModeChange " + radioModeInfoList);
            WifiNative.VendorHalRadioModeChangeEventHandler handler;
            synchronized (sLock) {
                if (mRadioModeChangeEventHandler == null || radioModeInfoList == null) return;
                handler = mRadioModeChangeEventHandler;
            }
            // Should only contain 1 or 2 radio infos.
            if (radioModeInfoList.size() == 0 || radioModeInfoList.size() > 2) {
                mLog.e("Unexpected number of radio info in list " + radioModeInfoList.size());
                return;
            }
            WifiChip.RadioModeInfo radioModeInfo0 = radioModeInfoList.get(0);
            WifiChip.RadioModeInfo radioModeInfo1 =
                    radioModeInfoList.size() == 2 ? radioModeInfoList.get(1) : null;
            // Number of ifaces on each radio should be equal.
            if (radioModeInfo1 != null
                    && radioModeInfo0.ifaceInfos.size() != radioModeInfo1.ifaceInfos.size()) {
                mLog.e("Unexpected number of iface info in list "
                        + radioModeInfo0.ifaceInfos.size() + ", "
                        + radioModeInfo1.ifaceInfos.size());
                return;
            }
            int numIfacesOnEachRadio = radioModeInfo0.ifaceInfos.size();
            // Only 1 or 2 ifaces should be present on each radio.
            if (numIfacesOnEachRadio == 0 || numIfacesOnEachRadio > 2) {
                mLog.e("Unexpected number of iface info in list " + numIfacesOnEachRadio);
                return;
            }
            Runnable runnable = null;
            // 2 ifaces simultaneous on 2 radios.
            if (radioModeInfoList.size() == 2 && numIfacesOnEachRadio == 1) {
                // Iface on radio0 should be different from the iface on radio1 for DBS & SBS.
                if (areSameIfaceNames(radioModeInfo0.ifaceInfos, radioModeInfo1.ifaceInfos)) {
                    mLog.e("Unexpected for both radio infos to have same iface");
                    return;
                }
                if (radioModeInfo0.bandInfo != radioModeInfo1.bandInfo) {
                    runnable = () -> {
                        handler.onDbs();
                    };
                } else {
                    runnable = () -> {
                        handler.onSbs(radioModeInfo0.bandInfo);
                    };
                }
            // 2 ifaces time sharing on 1 radio.
            } else if (radioModeInfoList.size() == 1 && numIfacesOnEachRadio == 2) {
                WifiChip.IfaceInfo ifaceInfo0 = radioModeInfo0.ifaceInfos.get(0);
                WifiChip.IfaceInfo ifaceInfo1 = radioModeInfo0.ifaceInfos.get(1);
                if (ifaceInfo0.channel != ifaceInfo1.channel) {
                    runnable = () -> {
                        handler.onMcc(radioModeInfo0.bandInfo);
                    };
                } else {
                    runnable = () -> {
                        handler.onScc(radioModeInfo0.bandInfo);
                    };
                }
            } else {
                // Not concurrency scenario, uninteresting...
            }
            if (runnable != null) mHalEventHandler.post(runnable);
        }
    }

    private boolean areSameIfaceNames(List<WifiChip.IfaceInfo> ifaceList1,
            List<WifiChip.IfaceInfo> ifaceList2) {
        List<String> ifaceNamesList1 = ifaceList1
                .stream()
                .map(i -> i.name)
                .collect(Collectors.toList());
        List<String> ifaceNamesList2 = ifaceList2
                .stream()
                .map(i -> i.name)
                .collect(Collectors.toList());
        return ifaceNamesList1.containsAll(ifaceNamesList2);
    }

    /**
     * Hal Device Manager callbacks.
     */
    public class HalDeviceManagerStatusListener implements HalDeviceManager.ManagerStatusListener {
        @Override
        public void onStatusChanged() {
            boolean isReady = mHalDeviceManager.isReady();
            boolean isStarted = mHalDeviceManager.isStarted();

            mVerboseLog.i("Device Manager onStatusChanged. isReady(): " + isReady
                    + ", isStarted(): " + isStarted);
            if (!isReady) {
                // Probably something unpleasant, e.g. the server died
                WifiNative.VendorHalDeathEventHandler handler;
                synchronized (sLock) {
                    clearState();
                    handler = mDeathEventHandler;
                }
                if (handler != null) {
                    handler.onDeath();
                }
            }
        }
    }

    /**
     * Trigger subsystem restart in vendor side
     */
    public boolean startSubsystemRestart() {
        synchronized (sLock) {
            if (mWifiChip == null) return false;
            return mWifiChip.triggerSubsystemRestart();
        }
    }

    /**
     * Retrieve the list of usable Wifi channels.
     */
    public List<WifiAvailableChannel> getUsableChannels(
            @WifiScanner.WifiBand int band,
            @WifiAvailableChannel.OpMode int mode,
            @WifiAvailableChannel.Filter int filter) {
        synchronized (sLock) {
            if (mWifiChip == null) return null;
            return mWifiChip.getUsableChannels(band, mode, filter);
        }
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
    public boolean setDtimMultiplier(@NonNull String ifaceName, int multiplier) {
        synchronized (sLock) {
            WifiStaIface iface = getStaIface(ifaceName);
            if (iface == null) return false;
            return iface.setDtimMultiplier(multiplier);
        }
    }

    /**
     * Set the Multi-Link Operation mode.
     *
     * @param mode Multi-Link operation mode {@link android.net.wifi.WifiManager.MloMode}.
     * @return {@code true} if success, otherwise {@code false}.
     */
    public @WifiStatusCode int setMloMode(@WifiManager.MloMode int mode) {
        synchronized (sLock) {
            if (mWifiChip == null) return WifiStatusCode.ERROR_WIFI_CHIP_INVALID;
            return mWifiChip.setMloMode(mode);
        }
    }

    /**
     * Enable/disable the feature of allowing current STA-connected channel for WFA GO, SAP and
     * Aware when the regulatory allows.
     *
     * @param enableIndoorChannel enable or disable indoor channel.
     * @param enableDfsChannel    enable or disable DFS channel.
     * @return true if the operation succeeded, false if there is an error in Hal.
     */
    public boolean enableStaChannelForPeerNetwork(boolean enableIndoorChannel,
            boolean enableDfsChannel) {
        synchronized (sLock) {
            if (mWifiChip == null) return false;
            return mWifiChip.enableStaChannelForPeerNetwork(enableIndoorChannel, enableDfsChannel);
        }
    }

    /**
     * See {@link WifiNative#isBandCombinationSupported(String, List)}.
     */
    public boolean isBandCombinationSupported(@NonNull String ifaceName,
            @NonNull List<Integer> bands) {
        synchronized (sLock) {
            WifiStaIface iface = getStaIface(ifaceName);
            if (iface == null) return false;
            return mHalDeviceManager.isBandCombinationSupported(iface, bands);
        }
    }

    /**
     * See {@link WifiNative#getSupportedBandCombinations(String)}.
     */
    public Set<List<Integer>> getSupportedBandCombinations(String ifaceName) {
        synchronized (sLock) {
            WifiStaIface iface = getStaIface(ifaceName);
            if (iface == null) return null;
            return mHalDeviceManager.getSupportedBandCombinations(iface);
        }
    }

    /**
     * See {@link WifiNative#setAfcChannelAllowance(WifiChip.AfcChannelAllowance)}
     */
    public boolean setAfcChannelAllowance(WifiChip.AfcChannelAllowance afcChannelAllowance) {
        if (mWifiChip == null) return false;
        return mWifiChip.setAfcChannelAllowance(afcChannelAllowance);
    }

    /**
     * See {@link WifiNative#setRoamingMode(String, int)}.
     */
    public @WifiStatusCode int setRoamingMode(@NonNull String ifaceName,
                                              @RoamingMode int roamingMode) {
        synchronized (sLock) {
            WifiStaIface iface = getStaIface(ifaceName);
            if (iface == null) return WifiStatusCode.ERROR_WIFI_IFACE_INVALID;
            return iface.setRoamingMode(roamingMode);
        }
    }

    /**
     * See {@link WifiNative#getTwtCapabilities(String)}
     */
    public Bundle getTwtCapabilities(String ifaceName) {
        synchronized (sLock) {
            WifiStaIface wifiStaIface = getStaIface(ifaceName);
            if (wifiStaIface == null) return null;
            return wifiStaIface.getTwtCapabilities();
        }
    }

    /**
     * See {@link WifiNative#registerTwtCallbacks(TwtManager.WifiNativeTwtEvents)}
     */
    public void registerTwtCallbacks(WifiNative.WifiTwtEvents wifiTwtCallback) {
        mWifiTwtEvents = wifiTwtCallback;
    }

    /**
     * See {@link WifiNative#setupTwtSession(int, String, TwtRequest)}
     */
    public boolean setupTwtSession(int cmdId, String ifaceName, TwtRequest twtRequest) {
        synchronized (sLock) {
            WifiStaIface wifiStaIface = getStaIface(ifaceName);
            if (wifiStaIface == null) return false;
            return wifiStaIface.setupTwtSession(cmdId, twtRequest);
        }
    }

    /**
     * See {@link WifiNative#tearDownTwtSession(int, String, int)}
     */
    public boolean tearDownTwtSession(int cmdId, String ifaceName, int sessionId) {
        synchronized (sLock) {
            WifiStaIface wifiStaIface = getStaIface(ifaceName);
            if (wifiStaIface == null) return false;
            return wifiStaIface.tearDownTwtSession(cmdId, sessionId);
        }
    }

    /**
     * See {@link WifiNative#getStatsTwtSession(int, String, int)}
     */
    public boolean getStatsTwtSession(int cmdId, String ifaceName, int sessionId) {
        synchronized (sLock) {
            WifiStaIface wifiStaIface = getStaIface(ifaceName);
            if (wifiStaIface == null) return false;
            return wifiStaIface.getStatsTwtSession(cmdId, sessionId);
        }
    }
}
