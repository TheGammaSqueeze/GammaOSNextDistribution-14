/*
 * Copyright (C) 2018 The Android Open Source Project
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

import static com.android.server.wifi.WifiSettingsConfigStore.WIFI_SCAN_THROTTLE_ENABLED;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.ActivityManager;
import android.app.AppOpsManager;
import android.app.BroadcastOptions;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.IScanResultsCallback;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiScanner;
import android.net.wifi.util.ScanResultUtil;
import android.os.Bundle;
import android.os.Handler;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.os.UserHandle;
import android.os.WorkSource;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.Log;
import android.util.LruCache;
import android.util.Pair;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.modules.utils.build.SdkLevel;
import com.android.server.wifi.scanner.WifiScannerInternal;
import com.android.server.wifi.util.WifiPermissionsUtil;
import com.android.wifi.resources.R;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.annotation.concurrent.NotThreadSafe;

/**
 * This class manages all scan requests originating from external apps using the
 * {@link WifiManager#startScan()}.
 *
 * This class is responsible for:
 * a) Enable/Disable scanning based on the request from {@link ActiveModeWarden}.
 * a) Forwarding scan requests from {@link WifiManager#startScan()} to
 * {@link WifiScanner#startScan(WifiScanner.ScanSettings, WifiScanner.ScanListener)}.
 * Will essentially proxy scan requests from WifiService to WifiScanningService.
 * b) Cache the results of these scan requests and return them when
 * {@link WifiManager#getScanResults()} is invoked.
 * c) Will send out the {@link WifiManager#SCAN_RESULTS_AVAILABLE_ACTION} broadcast when new
 * scan results are available.
 * d) Throttle scan requests from non-setting apps:
 *  a) Each foreground app can request a max of
 *   {@link #SCAN_REQUEST_THROTTLE_MAX_IN_TIME_WINDOW_FG_APPS} scan every
 *   {@link #SCAN_REQUEST_THROTTLE_TIME_WINDOW_FG_APPS_MS}.
 *  b) Background apps combined can request 1 scan every
 *   {@link #SCAN_REQUEST_THROTTLE_INTERVAL_BG_APPS_MS}.
 * Note: This class is not thread-safe. It needs to be invoked from the main Wifi thread only.
 */
@NotThreadSafe
public class ScanRequestProxy {
    private static final String TAG = "WifiScanRequestProxy";

    @VisibleForTesting
    public static final int SCAN_REQUEST_THROTTLE_TIME_WINDOW_FG_APPS_MS = 120 * 1000;
    @VisibleForTesting
    public static final int SCAN_REQUEST_THROTTLE_MAX_IN_TIME_WINDOW_FG_APPS = 4;
    @VisibleForTesting
    public static final int SCAN_REQUEST_THROTTLE_INTERVAL_BG_APPS_MS = 30 * 60 * 1000;

    public static final int PARTIAL_SCAN_CACHE_SIZE = 200;

    private final Context mContext;
    private final Handler mHandler;
    private final AppOpsManager mAppOps;
    private final ActivityManager mActivityManager;
    private final WifiInjector mWifiInjector;
    private final WifiConfigManager mWifiConfigManager;
    private final WifiPermissionsUtil mWifiPermissionsUtil;
    private final WifiMetrics mWifiMetrics;
    private final Clock mClock;
    private final WifiSettingsConfigStore mSettingsConfigStore;
    private WifiScannerInternal mWifiScanner;

    // Verbose logging flag.
    private boolean mVerboseLoggingEnabled = false;
    private final Object mThrottleEnabledLock = new Object();
    @GuardedBy("mThrottleEnabledLock")
    private boolean mThrottleEnabled = true;
    // Flag to decide if we need to scan or not.
    private boolean mScanningEnabled = false;
    // Flag to decide if we need to scan for hidden networks or not.
    private boolean mScanningForHiddenNetworksEnabled = false;
    // Timestamps for the last scan requested by any background app.
    private long mLastScanTimestampForBgApps = 0;
    // Timestamps for the list of last few scan requests by each foreground app.
    // Keys in the map = Pair<Uid, PackageName> of the app.
    // Values in the map = List of the last few scan request timestamps from the app.
    private final ArrayMap<Pair<Integer, String>, LinkedList<Long>> mLastScanTimestampsForFgApps =
            new ArrayMap();
    // Full scan results cached from the last full single scan request.
    // Stored as a map of bssid -> ScanResult to allow other clients to perform ScanResult lookup
    // for bssid more efficiently.
    private final Map<String, ScanResult> mFullScanCache = new HashMap<>();
    // Partial scan results cached since the last full single scan request.
    private final LruCache<String, ScanResult> mPartialScanCache =
            new LruCache<>(PARTIAL_SCAN_CACHE_SIZE);
    // external ScanResultCallback tracker
    private final RemoteCallbackList<IScanResultsCallback> mRegisteredScanResultsCallbacks;
    private class GlobalScanListener implements WifiScanner.ScanListener {
        @Override
        public void onSuccess() {
            // Ignore. These will be processed from the scan request listener.
        }

        @Override
        public void onFailure(int reason, String description) {
            // Ignore. These will be processed from the scan request listener.
        }

        @Override
        public void onResults(WifiScanner.ScanData[] scanDatas) {
            if (mVerboseLoggingEnabled) {
                Log.d(TAG, "Scan results received");
            }
            // For single scans, the array size should always be 1.
            if (scanDatas.length != 1) {
                Log.wtf(TAG, "Found more than 1 batch of scan results, Failing...");
                sendScanResultBroadcast(false);
                return;
            }
            WifiScanner.ScanData scanData = scanDatas[0];
            ScanResult[] scanResults = scanData.getResults();
            if (mVerboseLoggingEnabled) {
                Log.d(TAG, "Received " + scanResults.length + " scan results");
            }
            // Only process full band scan results.
            boolean isFullBandScan = WifiScanner.isFullBandScan(
                    scanData.getScannedBandsInternal(), false);
            if (isFullBandScan) {
                // If is full scan, clear the cache so only the latest data is available
                mFullScanCache.clear();
                mPartialScanCache.evictAll();
            }
            for (ScanResult s : scanResults) {
                ScanResult scanResult = mFullScanCache.get(s.BSSID);
                if (isFullBandScan && scanResult == null) {
                    mFullScanCache.put(s.BSSID, s);
                    continue;
                }
                // If a hidden network is configured, wificond may report two scan results for
                // the same BSS, ie. One with the SSID and another one without SSID. So avoid
                // overwriting the scan result of the same BSS with Hidden SSID scan result
                if (scanResult != null) {
                    if (TextUtils.isEmpty(scanResult.SSID) || !TextUtils.isEmpty(s.SSID)) {
                        mFullScanCache.put(s.BSSID, s);
                    }
                    continue;
                }
                scanResult = mPartialScanCache.get(s.BSSID);
                if (scanResult == null
                        || TextUtils.isEmpty(scanResult.SSID) || !TextUtils.isEmpty(s.SSID)) {
                    mPartialScanCache.put(s.BSSID, s);
                }
            }
            if (isFullBandScan) {
                // Only trigger broadcasts for full scans
                sendScanResultBroadcast(true);
                sendScanResultsAvailableToCallbacks();
            }
        }

        @Override
        public void onFullResult(ScanResult fullScanResult) {
            // Ignore for single scans.
        }

        @Override
        public void onPeriodChanged(int periodInMs) {
            // Ignore for single scans.
        }
    };

    // Common scan listener for scan requests initiated by this class.
    private class ScanRequestProxyScanListener implements WifiScanner.ScanListener {
        @Override
        public void onSuccess() {
            // Scan request succeeded, wait for results to report to external clients.
            if (mVerboseLoggingEnabled) {
                Log.d(TAG, "Scan request succeeded");
            }
        }

        @Override
        public void onFailure(int reason, String description) {
            Log.e(TAG, "Scan failure received. reason: " + reason + ",description: " + description);
            sendScanResultBroadcast(false);
        }

        @Override
        public void onResults(WifiScanner.ScanData[] scanDatas) {
            // Ignore. These will be processed from the global listener.
        }

        @Override
        public void onFullResult(ScanResult fullScanResult) {
            // Ignore for single scans.
        }

        @Override
        public void onPeriodChanged(int periodInMs) {
            // Ignore for single scans.
        }
    };

    ScanRequestProxy(Context context, AppOpsManager appOpsManager, ActivityManager activityManager,
                     WifiInjector wifiInjector, WifiConfigManager configManager,
                     WifiPermissionsUtil wifiPermissionUtil, WifiMetrics wifiMetrics, Clock clock,
                     Handler handler, WifiSettingsConfigStore settingsConfigStore) {
        mContext = context;
        mHandler = handler;
        mAppOps = appOpsManager;
        mActivityManager = activityManager;
        mWifiInjector = wifiInjector;
        mWifiConfigManager = configManager;
        mWifiPermissionsUtil = wifiPermissionUtil;
        mWifiMetrics = wifiMetrics;
        mClock = clock;
        mSettingsConfigStore = settingsConfigStore;
        mRegisteredScanResultsCallbacks = new RemoteCallbackList<>();
    }

    /**
     * Enable verbose logging.
     */
    public void enableVerboseLogging(boolean verboseEnabled) {
        mVerboseLoggingEnabled = verboseEnabled;
    }

    private void updateThrottleEnabled() {
        synchronized (mThrottleEnabledLock) {
            // Start listening for throttle settings change after we retrieve scanner instance.
            mThrottleEnabled = mSettingsConfigStore.get(WIFI_SCAN_THROTTLE_ENABLED);
            if (mVerboseLoggingEnabled) {
                Log.v(TAG, "Scan throttle enabled " + mThrottleEnabled);
            }
        }
    }

    /**
     * Helper method to populate WifiScanner handle. This is done lazily because
     * WifiScanningService is started after WifiService.
     */
    private boolean retrieveWifiScannerIfNecessary() {
        if (mWifiScanner == null) {
            mWifiScanner = WifiLocalServices.getService(WifiScannerInternal.class);
            updateThrottleEnabled();
            // Register the global scan listener.
            if (mWifiScanner != null) {
                mWifiScanner.registerScanListener(
                        new WifiScannerInternal.ScanListener(new GlobalScanListener(), mHandler));
            }
        }
        return mWifiScanner != null;
    }

    /**
     * Method that lets public apps know that scans are available.
     *
     * @param context Context to use for the notification
     * @param available boolean indicating if scanning is available
     */
    private void sendScanAvailableBroadcast(Context context, boolean available) {
        Log.d(TAG, "Sending scan available broadcast: " + available);
        final Intent intent = new Intent(WifiManager.ACTION_WIFI_SCAN_AVAILABILITY_CHANGED);
        intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT);
        intent.putExtra(WifiManager.EXTRA_SCAN_AVAILABLE, available);
        context.sendStickyBroadcastAsUser(intent, UserHandle.ALL);
    }

    private void enableScanningInternal(boolean enable) {
        if (!retrieveWifiScannerIfNecessary()) {
            Log.e(TAG, "Failed to retrieve wifiscanner");
            return;
        }
        mWifiScanner.setScanningEnabled(enable);
        sendScanAvailableBroadcast(mContext, enable);
        if (!enable) clearScanResults();
        Log.i(TAG, "Scanning is " + (enable ? "enabled" : "disabled"));
    }

    /**
     * Enable/disable scanning.
     *
     * @param enable true to enable, false to disable.
     * @param enableScanningForHiddenNetworks true to enable scanning for hidden networks,
     *                                        false to disable.
     */
    public void enableScanning(boolean enable, boolean enableScanningForHiddenNetworks) {
        if (enable) {
            enableScanningInternal(true);
            mScanningForHiddenNetworksEnabled = enableScanningForHiddenNetworks;
            Log.i(TAG, "Scanning for hidden networks is "
                    + (enableScanningForHiddenNetworks ? "enabled" : "disabled"));
        } else {
            enableScanningInternal(false);
        }
        mScanningEnabled = enable;
    }


    /**
     * Helper method to send the scan request status broadcast.
     */
    private void sendScanResultBroadcast(boolean scanSucceeded) {
        Intent intent = new Intent(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT);
        intent.putExtra(WifiManager.EXTRA_RESULTS_UPDATED, scanSucceeded);
        mContext.sendBroadcastAsUser(intent, UserHandle.ALL, null,
                createBroadcastOptionsForScanResultsAvailable(scanSucceeded));
    }

    /**
     * Helper method to send the scan request failure broadcast to specified package.
     */
    private void sendScanResultFailureBroadcastToPackage(String packageName) {
        final boolean scanSucceeded = false;
        Intent intent = new Intent(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT);
        intent.putExtra(WifiManager.EXTRA_RESULTS_UPDATED, scanSucceeded);
        intent.setPackage(packageName);
        mContext.sendBroadcastAsUser(intent, UserHandle.ALL, null,
                createBroadcastOptionsForScanResultsAvailable(scanSucceeded));
    }

    static Bundle createBroadcastOptionsForScanResultsAvailable(boolean scanSucceeded) {
        if (!SdkLevel.isAtLeastU()) return null;

        // Delay delivering the broadcast to apps in the Cached state and apply policy such
        // that when a new SCAN_RESULTS_AVAILABLE broadcast is sent, any older pending
        // broadcasts with the same 'scanSucceeded' extra value will be discarded.
        return BroadcastOptions.makeBasic()
                .setDeliveryGroupMatchingKey(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION,
                        String.valueOf(scanSucceeded))
                .setDeliveryGroupPolicy(BroadcastOptions.DELIVERY_GROUP_POLICY_MOST_RECENT)
                .setDeferralPolicy(BroadcastOptions.DEFERRAL_POLICY_UNTIL_ACTIVE)
                .toBundle();
    }

    private void trimPastScanRequestTimesForForegroundApp(
            List<Long> scanRequestTimestamps, long currentTimeMillis) {
        Iterator<Long> timestampsIter = scanRequestTimestamps.iterator();
        while (timestampsIter.hasNext()) {
            Long scanRequestTimeMillis = timestampsIter.next();
            if ((currentTimeMillis - scanRequestTimeMillis)
                    > SCAN_REQUEST_THROTTLE_TIME_WINDOW_FG_APPS_MS) {
                timestampsIter.remove();
            } else {
                // This list is sorted by timestamps, so we can skip any more checks
                break;
            }
        }
    }

    private LinkedList<Long> getOrCreateScanRequestTimestampsForForegroundApp(
            int callingUid, String packageName) {
        Pair<Integer, String> uidAndPackageNamePair = Pair.create(callingUid, packageName);
        synchronized (mThrottleEnabledLock) {
            LinkedList<Long> scanRequestTimestamps =
                    mLastScanTimestampsForFgApps.get(uidAndPackageNamePair);
            if (scanRequestTimestamps == null) {
                scanRequestTimestamps = new LinkedList<>();
                mLastScanTimestampsForFgApps.put(uidAndPackageNamePair, scanRequestTimestamps);
            }
            return scanRequestTimestamps;
        }
    }

    /**
     * Checks if the scan request from the app (specified by packageName) needs
     * to be throttled.
     * The throttle limit allows a max of {@link #SCAN_REQUEST_THROTTLE_MAX_IN_TIME_WINDOW_FG_APPS}
     * in {@link #SCAN_REQUEST_THROTTLE_TIME_WINDOW_FG_APPS_MS} window.
     */
    private boolean shouldScanRequestBeThrottledForForegroundApp(
            int callingUid, String packageName) {
        if (isPackageNameInExceptionList(packageName, true)) {
            return false;
        }
        LinkedList<Long> scanRequestTimestamps =
                getOrCreateScanRequestTimestampsForForegroundApp(callingUid, packageName);
        long currentTimeMillis = mClock.getElapsedSinceBootMillis();
        // First evict old entries from the list.
        trimPastScanRequestTimesForForegroundApp(scanRequestTimestamps, currentTimeMillis);
        if (scanRequestTimestamps.size() >= SCAN_REQUEST_THROTTLE_MAX_IN_TIME_WINDOW_FG_APPS) {
            return true;
        }
        // Proceed with the scan request and record the time.
        scanRequestTimestamps.addLast(currentTimeMillis);
        return false;
    }

    private boolean isPackageNameInExceptionList(String packageName, boolean isForeground) {
        if (packageName == null) {
            return false;
        }
        String[] exceptionList = mContext.getResources().getStringArray(isForeground
                ? R.array.config_wifiForegroundScanThrottleExceptionList
                : R.array.config_wifiBackgroundScanThrottleExceptionList);
        if (exceptionList == null) {
            return false;
        }
        for (String name : exceptionList) {
            if (TextUtils.equals(packageName, name)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if the scan request from a background app needs to be throttled.
     */
    private boolean shouldScanRequestBeThrottledForBackgroundApp(String packageName) {
        if (isPackageNameInExceptionList(packageName, false)) {
            return false;
        }
        synchronized (mThrottleEnabledLock) {
            long lastScanMs = mLastScanTimestampForBgApps;
            long elapsedRealtime = mClock.getElapsedSinceBootMillis();
            if (lastScanMs != 0
                    && (elapsedRealtime - lastScanMs) < SCAN_REQUEST_THROTTLE_INTERVAL_BG_APPS_MS) {
                return true;
            }
            // Proceed with the scan request and record the time.
            mLastScanTimestampForBgApps = elapsedRealtime;
            return false;
        }
    }

    /**
     * Safely retrieve package importance.
     */
    private int getPackageImportance(int callingUid, String packageName) {
        mAppOps.checkPackage(callingUid, packageName);
        try {
            return mActivityManager.getPackageImportance(packageName);
        } catch (SecurityException e) {
            Log.e(TAG, "Failed to check the app state", e);
            return ActivityManager.RunningAppProcessInfo.IMPORTANCE_GONE;
        }
    }

    /**
     * Checks if the scan request from the app (specified by callingUid & packageName) needs
     * to be throttled.
     */
    private boolean shouldScanRequestBeThrottledForApp(int callingUid, String packageName,
            int packageImportance) {
        boolean isThrottled;
        if (packageImportance
                > ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE) {
            isThrottled = shouldScanRequestBeThrottledForBackgroundApp(packageName);
            if (isThrottled) {
                if (mVerboseLoggingEnabled) {
                    Log.v(TAG, "Background scan app request [" + callingUid + ", "
                            + packageName + "]");
                }
                mWifiMetrics.incrementExternalBackgroundAppOneshotScanRequestsThrottledCount();
            }
        } else {
            isThrottled = shouldScanRequestBeThrottledForForegroundApp(callingUid, packageName);
            if (isThrottled) {
                if (mVerboseLoggingEnabled) {
                    Log.v(TAG, "Foreground scan app request [" + callingUid + ", "
                            + packageName + "]");
                }
                mWifiMetrics.incrementExternalForegroundAppOneshotScanRequestsThrottledCount();
            }
        }
        mWifiMetrics.incrementExternalAppOneshotScanRequestsCount();
        return isThrottled;
    }

    /**
     * Initiate a wifi scan.
     *
     * @param callingUid The uid initiating the wifi scan. Blame will be given to this uid.
     * @return true if the scan request was placed or a scan is already ongoing, false otherwise.
     */
    public boolean startScan(int callingUid, String packageName) {
        if (!mScanningEnabled || !retrieveWifiScannerIfNecessary()) {
            Log.e(TAG, "Failed to retrieve wifiscanner");
            sendScanResultFailureBroadcastToPackage(packageName);
            return false;
        }
        boolean fromSettingsOrSetupWizard =
                mWifiPermissionsUtil.checkNetworkSettingsPermission(callingUid)
                        || mWifiPermissionsUtil.checkNetworkSetupWizardPermission(callingUid);
        // Check and throttle scan request unless,
        // a) App has either NETWORK_SETTINGS or NETWORK_SETUP_WIZARD permission.
        // b) Throttling has been disabled by user.
        int packageImportance = getPackageImportance(callingUid, packageName);
        if (!fromSettingsOrSetupWizard && isScanThrottleEnabled()
                && shouldScanRequestBeThrottledForApp(callingUid, packageName,
                packageImportance)) {
            Log.i(TAG, "Scan request from " + packageName + " throttled");
            sendScanResultFailureBroadcastToPackage(packageName);
            return false;
        }
        // Create a worksource using the caller's UID.
        WorkSource workSource = new WorkSource(callingUid, packageName);
        mWifiMetrics.getScanMetrics().setWorkSource(workSource);
        mWifiMetrics.getScanMetrics().setImportance(packageImportance);

        // Create the scan settings.
        WifiScanner.ScanSettings settings = new WifiScanner.ScanSettings();
        // Scan requests from apps with network settings will be of high accuracy type.
        if (fromSettingsOrSetupWizard) {
            settings.type = WifiScanner.SCAN_TYPE_HIGH_ACCURACY;
        } else {
            if (SdkLevel.isAtLeastS()) {
                // since the scan request is from a normal app, do not scan all 6Ghz channels.
                settings.set6GhzPscOnlyEnabled(true);
            }
        }
        settings.band = WifiScanner.WIFI_BAND_ALL;
        settings.reportEvents = WifiScanner.REPORT_EVENT_AFTER_EACH_SCAN
                | WifiScanner.REPORT_EVENT_FULL_SCAN_RESULT;
        if (mScanningForHiddenNetworksEnabled) {
            settings.hiddenNetworks.clear();
            // retrieve the list of hidden network SSIDs from saved network to scan if enabled.
            settings.hiddenNetworks.addAll(mWifiConfigManager.retrieveHiddenNetworkList(false));
            // retrieve the list of hidden network SSIDs from Network suggestion to scan for.
            settings.hiddenNetworks.addAll(mWifiInjector.getWifiNetworkSuggestionsManager()
                    .retrieveHiddenNetworkList(false));
        }
        mWifiScanner.startScan(settings,
                new WifiScannerInternal.ScanListener(new ScanRequestProxyScanListener(), mHandler),
                workSource);
        return true;
    }

    /**
     * Return the results of the most recent access point scan, in the form of
     * a list of {@link ScanResult} objects.
     * @return the list of results
     */
    public List<ScanResult> getScanResults() {
        // return a copy to prevent external modification
        return new ArrayList<>(combineScanResultsCache().values());
    }

    /**
     * Return the ScanResult from the most recent access point scan for the provided bssid.
     *
     * @param bssid BSSID as string {@link ScanResult#BSSID}.
     * @return ScanResult for the corresponding bssid if found, null otherwise.
     */
    public @Nullable ScanResult getScanResult(@Nullable String bssid) {
        if (bssid == null) return null;
        ScanResult scanResult = mFullScanCache.get(bssid);
        if (scanResult == null) {
            scanResult = mPartialScanCache.get(bssid);
            if (scanResult == null) return null;
        }
        // return a copy to prevent external modification
        return new ScanResult(scanResult);
    }


    /**
     * Clear the stored scan results.
     */
    private void clearScanResults() {
        synchronized (mThrottleEnabledLock) {
            mFullScanCache.clear();
            mPartialScanCache.evictAll();
            mLastScanTimestampForBgApps = 0;
            mLastScanTimestampsForFgApps.clear();
        }
    }

    /**
     * Clear any scan timestamps being stored for the app.
     *
     * @param uid Uid of the package.
     * @param packageName Name of the package.
     */
    public void clearScanRequestTimestampsForApp(@NonNull String packageName, int uid) {
        synchronized (mThrottleEnabledLock) {
            if (mVerboseLoggingEnabled) {
                Log.v(TAG, "Clearing scan request timestamps for uid=" + uid + ", packageName="
                        + packageName);
            }
            mLastScanTimestampsForFgApps.remove(Pair.create(uid, packageName));
        }
    }

    private void sendScanResultsAvailableToCallbacks() {
        int itemCount = mRegisteredScanResultsCallbacks.beginBroadcast();
        for (int i = 0; i < itemCount; i++) {
            try {
                mRegisteredScanResultsCallbacks.getBroadcastItem(i).onScanResultsAvailable();
            } catch (RemoteException e) {
                Log.e(TAG, "onScanResultsAvailable: remote exception -- " + e);
            }
        }
        mRegisteredScanResultsCallbacks.finishBroadcast();
    }

    /** Combine the full and partial scan results */
    private Map<String, ScanResult> combineScanResultsCache() {
        Map<String, ScanResult> combinedCache = new HashMap<>();
        combinedCache.putAll(mFullScanCache);
        combinedCache.putAll(mPartialScanCache.snapshot());
        return combinedCache;
    }

    /**
     * Register a callback on scan event
     * @param callback IScanResultListener instance to add.
     * @return true if succeed otherwise false.
     */
    public boolean registerScanResultsCallback(IScanResultsCallback callback) {
        return mRegisteredScanResultsCallbacks.register(callback);
    }

    /**
     * Unregister a callback on scan event
     * @param callback IScanResultListener instance to add.
     */
    public void unregisterScanResultsCallback(IScanResultsCallback callback) {
        mRegisteredScanResultsCallbacks.unregister(callback);
    }

    /**
     * Enable/disable wifi scan throttling from 3rd party apps.
     */
    public void setScanThrottleEnabled(boolean enable) {
        synchronized (mThrottleEnabledLock) {
            mThrottleEnabled = enable;
            mSettingsConfigStore.put(WIFI_SCAN_THROTTLE_ENABLED, enable);
            if (mVerboseLoggingEnabled) {
                Log.i(TAG, "Scan throttle enabled " + mThrottleEnabled);
            }
            // reset internal counters when enabling/disabling throttling
            mLastScanTimestampsForFgApps.clear();
            mLastScanTimestampForBgApps = 0;
        }
    }

    /**
     * Get the persisted Wi-Fi scan throttle state, set by
     * {@link #setScanThrottleEnabled(boolean)}.
     */
    public boolean isScanThrottleEnabled() {
        synchronized (mThrottleEnabledLock) {
            return mThrottleEnabled;
        }
    }

    /** Indicate whether there are WPA2 personal only networks. */
    public boolean isWpa2PersonalOnlyNetworkInRange(String ssid) {
        return combineScanResultsCache().values().stream().anyMatch(r ->
                TextUtils.equals(ssid, r.getWifiSsid().toString())
                        && ScanResultUtil.isScanResultForPskOnlyNetwork(r));
    }

    /** Indicate whether there are WPA3 only networks. */
    public boolean isWpa3PersonalOnlyNetworkInRange(String ssid) {
        return combineScanResultsCache().values().stream().anyMatch(r ->
                TextUtils.equals(ssid, r.getWifiSsid().toString())
                        && ScanResultUtil.isScanResultForSaeOnlyNetwork(r));
    }

    /** Indicate whether there are WPA2/WPA3 transition mode networks. */
    public boolean isWpa2Wpa3PersonalTransitionNetworkInRange(String ssid) {
        return combineScanResultsCache().values().stream().anyMatch(r ->
                TextUtils.equals(ssid, ScanResultUtil.createQuotedSsid(r.SSID))
                        && ScanResultUtil.isScanResultForPskSaeTransitionNetwork(r));
    }

    /** Indicate whether there are OPEN only networks. */
    public boolean isOpenOnlyNetworkInRange(String ssid) {
        return combineScanResultsCache().values().stream().anyMatch(r ->
                TextUtils.equals(ssid, r.getWifiSsid().toString())
                        && ScanResultUtil.isScanResultForOpenOnlyNetwork(r));
    }

    /** Indicate whether there are OWE only networks. */
    public boolean isOweOnlyNetworkInRange(String ssid) {
        return combineScanResultsCache().values().stream().anyMatch(r ->
                TextUtils.equals(ssid, r.getWifiSsid().toString())
                        && ScanResultUtil.isScanResultForOweOnlyNetwork(r));
    }

    /** Indicate whether there are WPA2 Enterprise only networks. */
    public boolean isWpa2EnterpriseOnlyNetworkInRange(String ssid) {
        return combineScanResultsCache().values().stream().anyMatch(r ->
                TextUtils.equals(ssid, r.getWifiSsid().toString())
                        && ScanResultUtil.isScanResultForWpa2EnterpriseOnlyNetwork(r));
    }

    /** Indicate whether there are WPA3 Enterprise only networks. */
    public boolean isWpa3EnterpriseOnlyNetworkInRange(String ssid) {
        return combineScanResultsCache().values().stream().anyMatch(r ->
                TextUtils.equals(ssid, r.getWifiSsid().toString())
                        && ScanResultUtil.isScanResultForWpa3EnterpriseOnlyNetwork(r));
    }
}
