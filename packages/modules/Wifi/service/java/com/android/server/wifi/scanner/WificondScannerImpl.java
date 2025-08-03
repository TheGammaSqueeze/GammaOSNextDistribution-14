/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.server.wifi.scanner;

import android.app.AlarmManager;
import android.content.Context;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiScanner;
import android.net.wifi.WifiScanner.WifiBandIndex;
import android.net.wifi.util.ScanResultUtil;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import com.android.server.wifi.Clock;
import com.android.server.wifi.ScanDetail;
import com.android.server.wifi.WifiGlobals;
import com.android.server.wifi.WifiMonitor;
import com.android.server.wifi.WifiNative;
import com.android.server.wifi.scanner.ChannelHelper.ChannelCollection;
import com.android.server.wifi.util.NativeUtil;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.concurrent.GuardedBy;

/**
 * Implementation of the WifiScanner HAL API that uses wificond to perform all scans
 * @see com.android.server.wifi.scanner.WifiScannerImpl for more details on each method.
 */
public class WificondScannerImpl extends WifiScannerImpl implements Handler.Callback {
    private static final String TAG = "WificondScannerImpl";
    private static final boolean DBG = false;

    public static final String TIMEOUT_ALARM_TAG = TAG + " Scan Timeout";
    // Default number of networks that can be specified to wificond per scan request
    public static final int DEFAULT_NUM_HIDDEN_NETWORK_IDS_PER_SCAN = 16;

    private static final int SCAN_BUFFER_CAPACITY = 10;
    private static final int MAX_APS_PER_SCAN = 32;
    private static final int MAX_SCAN_BUCKETS = 16;

    private final Context mContext;
    private final WifiGlobals mWifiGlobals;
    private final WifiNative mWifiNative;
    private final WifiMonitor mWifiMonitor;
    private final AlarmManager mAlarmManager;
    private final Handler mEventHandler;
    private final ChannelHelper mChannelHelper;
    private final Clock mClock;

    private final Object mSettingsLock = new Object();

    private ArrayList<ScanDetail> mNativeScanResults;
    private ArrayList<ScanDetail> mNativePnoScanResults;
    private WifiScanner.ScanData mLatestSingleScanResult =
            new WifiScanner.ScanData(0, 0, new ScanResult[0]);
    private int mMaxNumScanSsids = -1;
    private int mNextHiddenNetworkScanId = 0;

    // Settings for the currently running single scan, null if no scan active
    private LastScanSettings mLastScanSettings = null;
    // Settings for the currently running pno scan, null if no scan active
    private LastPnoScanSettings mLastPnoScanSettings = null;

    /**
     * Duration to wait before timing out a scan.
     *
     * The expected behavior is that the hardware will return a failed scan if it does not
     * complete, but timeout just in case it does not.
     */
    private static final long SCAN_TIMEOUT_MS = 15000;

    @GuardedBy("mSettingsLock")
    private AlarmManager.OnAlarmListener mScanTimeoutListener;

    public WificondScannerImpl(Context context, String ifaceName, WifiGlobals wifiGlobals,
                               WifiNative wifiNative, WifiMonitor wifiMonitor,
                               ChannelHelper channelHelper, Looper looper, Clock clock) {
        super(ifaceName);
        mContext = context;
        mWifiGlobals = wifiGlobals;
        mWifiNative = wifiNative;
        mWifiMonitor = wifiMonitor;
        mChannelHelper = channelHelper;
        mAlarmManager = (AlarmManager) mContext.getSystemService(Context.ALARM_SERVICE);
        mEventHandler = new Handler(looper, this);
        mClock = clock;

        wifiMonitor.registerHandler(getIfaceName(),
                WifiMonitor.SCAN_FAILED_EVENT, mEventHandler);
        wifiMonitor.registerHandler(getIfaceName(),
                WifiMonitor.PNO_SCAN_RESULTS_EVENT, mEventHandler);
        wifiMonitor.registerHandler(getIfaceName(),
                WifiMonitor.SCAN_RESULTS_EVENT, mEventHandler);
    }

    @Override
    public void cleanup() {
        synchronized (mSettingsLock) {
            cancelScanTimeout();
            reportScanFailure(WifiScanner.REASON_UNSPECIFIED);
            stopHwPnoScan();
            mMaxNumScanSsids = -1;
            mNextHiddenNetworkScanId = 0;
            mLastScanSettings = null; // finally clear any active scan
            mLastPnoScanSettings = null; // finally clear any active scan
            mWifiMonitor.deregisterHandler(getIfaceName(),
                    WifiMonitor.SCAN_FAILED_EVENT, mEventHandler);
            mWifiMonitor.deregisterHandler(getIfaceName(),
                    WifiMonitor.PNO_SCAN_RESULTS_EVENT, mEventHandler);
            mWifiMonitor.deregisterHandler(getIfaceName(),
                    WifiMonitor.SCAN_RESULTS_EVENT, mEventHandler);
        }
    }

    @Override
    public boolean getScanCapabilities(WifiNative.ScanCapabilities capabilities) {
        capabilities.max_scan_cache_size = Integer.MAX_VALUE;
        capabilities.max_scan_buckets = MAX_SCAN_BUCKETS;
        capabilities.max_ap_cache_per_scan = MAX_APS_PER_SCAN;
        capabilities.max_rssi_sample_size = 8;
        capabilities.max_scan_reporting_threshold = SCAN_BUFFER_CAPACITY;
        return true;
    }

    @Override
    public ChannelHelper getChannelHelper() {
        return mChannelHelper;
    }

    @Override
    public boolean startSingleScan(WifiNative.ScanSettings settings,
            WifiNative.ScanEventHandler eventHandler) {
        if (eventHandler == null || settings == null) {
            Log.w(TAG, "Invalid arguments for startSingleScan: settings=" + settings
                    + ",eventHandler=" + eventHandler);
            return false;
        }
        synchronized (mSettingsLock) {
            if (mLastScanSettings != null) {
                Log.w(TAG, "A single scan is already running");
                return false;
            }

            ChannelCollection allFreqs = mChannelHelper.createChannelCollection();
            boolean reportFullResults = false;

            for (int i = 0; i < settings.num_buckets; ++i) {
                WifiNative.BucketSettings bucketSettings = settings.buckets[i];
                if ((bucketSettings.report_events
                                & WifiScanner.REPORT_EVENT_FULL_SCAN_RESULT) != 0) {
                    reportFullResults = true;
                }
                allFreqs.addChannels(bucketSettings);
            }

            List<String> hiddenNetworkSSIDSet = new ArrayList<>();
            if (settings.hiddenNetworks != null) {
                boolean executeRoundRobin = true;
                int maxNumScanSsids = mMaxNumScanSsids;
                if (maxNumScanSsids <= 0) {
                    // Subtract 1 to account for the wildcard/broadcast probe request that
                    // wificond adds to the scan set.
                    mMaxNumScanSsids = mWifiNative.getMaxSsidsPerScan(getIfaceName()) - 1;
                    if (mMaxNumScanSsids > 0) {
                        maxNumScanSsids = mMaxNumScanSsids;
                    } else {
                        maxNumScanSsids = DEFAULT_NUM_HIDDEN_NETWORK_IDS_PER_SCAN;
                        executeRoundRobin = false;
                    }
                }
                int numHiddenNetworksPerScan =
                        Math.min(settings.hiddenNetworks.length, maxNumScanSsids);
                if (numHiddenNetworksPerScan == settings.hiddenNetworks.length
                        || mNextHiddenNetworkScanId >= settings.hiddenNetworks.length
                        || !executeRoundRobin) {
                    mNextHiddenNetworkScanId = 0;
                }
                if (DBG) {
                    Log.d(TAG, "Scanning for " + numHiddenNetworksPerScan + " out of "
                            + settings.hiddenNetworks.length + " total hidden networks");
                    Log.d(TAG, "Scan hidden networks starting at id=" + mNextHiddenNetworkScanId);
                }

                int id = mNextHiddenNetworkScanId;
                for (int i = 0; i < numHiddenNetworksPerScan; i++, id++) {
                    hiddenNetworkSSIDSet.add(
                            settings.hiddenNetworks[id % settings.hiddenNetworks.length].ssid);
                }
                mNextHiddenNetworkScanId = id % settings.hiddenNetworks.length;
            }
            mLastScanSettings = new LastScanSettings(
                    mClock.getElapsedSinceBootNanos(),
                    reportFullResults, allFreqs, eventHandler);

            int scanStatus = WifiScanner.REASON_UNSPECIFIED;
            Set<Integer> freqs = Collections.emptySet();
            if (!allFreqs.isEmpty()) {
                freqs = allFreqs.getScanFreqs();
                scanStatus = mWifiNative.scan(
                        getIfaceName(), settings.scanType, freqs, hiddenNetworkSSIDSet,
                        settings.enable6GhzRnr, settings.vendorIes);
                if (scanStatus != WifiScanner.REASON_SUCCEEDED) {
                    Log.e(TAG, "Failed to start scan, freqs=" + freqs + " status: "
                            + scanStatus);
                }
            } else {
                // There is a scan request but no available channels could be scanned for.
                // We regard it as a scan failure in this case.
                Log.e(TAG, "Failed to start scan because there is no available channel to scan");
            }
            if (scanStatus == WifiScanner.REASON_SUCCEEDED) {
                if (DBG) {
                    Log.d(TAG, "Starting wifi scan for freqs=" + freqs
                            + " on iface " + getIfaceName());
                }

                mScanTimeoutListener = new AlarmManager.OnAlarmListener() {
                    @Override public void onAlarm() {
                        handleScanTimeout();
                    }
                };

                mAlarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        mClock.getElapsedSinceBootMillis() + SCAN_TIMEOUT_MS,
                        TIMEOUT_ALARM_TAG, mScanTimeoutListener, mEventHandler);
            } else {
                // indicate scan failure async
                int finalScanStatus = scanStatus;
                mEventHandler.post(() -> reportScanFailure(finalScanStatus));
            }

            return true;
        }
    }

    @Override
    public WifiScanner.ScanData getLatestSingleScanResults() {
        return mLatestSingleScanResult;
    }

    @Override
    public boolean startBatchedScan(WifiNative.ScanSettings settings,
            WifiNative.ScanEventHandler eventHandler) {
        Log.w(TAG, "startBatchedScan() is not supported");
        return false;
    }

    @Override
    public void stopBatchedScan() {
        Log.w(TAG, "stopBatchedScan() is not supported");
    }

    @Override
    public void pauseBatchedScan() {
        Log.w(TAG, "pauseBatchedScan() is not supported");
    }

    @Override
    public void restartBatchedScan() {
        Log.w(TAG, "restartBatchedScan() is not supported");
    }

    private void handleScanTimeout() {
        synchronized (mSettingsLock) {
            Log.e(TAG, "Timed out waiting for scan result from wificond");
            reportScanFailure(WifiScanner.REASON_TIMEOUT);
            mScanTimeoutListener = null;
        }
    }

    @Override
    public boolean handleMessage(Message msg) {
        switch(msg.what) {
            case WifiMonitor.SCAN_FAILED_EVENT:
                Log.w(TAG, "Scan failed: error code: " + msg.arg1);
                cancelScanTimeout();
                reportScanFailure(msg.arg1);
                break;
            case WifiMonitor.PNO_SCAN_RESULTS_EVENT:
                pollLatestScanDataForPno();
                break;
            case WifiMonitor.SCAN_RESULTS_EVENT:
                cancelScanTimeout();
                pollLatestScanData();
                break;
            default:
                // ignore unknown event
        }
        return true;
    }

    private void cancelScanTimeout() {
        synchronized (mSettingsLock) {
            if (mScanTimeoutListener != null) {
                mAlarmManager.cancel(mScanTimeoutListener);
                mScanTimeoutListener = null;
            }
        }
    }

    private void reportScanFailure(int errorCode) {
        synchronized (mSettingsLock) {
            if (mLastScanSettings != null) {
                if (mLastScanSettings.singleScanEventHandler != null) {
                    mLastScanSettings.singleScanEventHandler
                            .onScanRequestFailed(errorCode);
                }
                mLastScanSettings = null;
            }
        }
    }

    private void reportPnoScanFailure() {
        synchronized (mSettingsLock) {
            if (mLastPnoScanSettings != null) {
                if (mLastPnoScanSettings.pnoScanEventHandler != null) {
                    mLastPnoScanSettings.pnoScanEventHandler.onPnoScanFailed();
                }
                // Clean up PNO state, we don't want to continue PNO scanning.
                mLastPnoScanSettings = null;
            }
        }
    }

    private void pollLatestScanDataForPno() {
        synchronized (mSettingsLock) {
            if (mLastPnoScanSettings == null) {
                 // got a scan before we started scanning or after scan was canceled
                return;
            }
            mNativePnoScanResults = mWifiNative.getPnoScanResults(getIfaceName());
            List<ScanResult> hwPnoScanResults = new ArrayList<>();
            int numFilteredScanResults = 0;
            for (int i = 0; i < mNativePnoScanResults.size(); ++i) {
                ScanResult result = mNativePnoScanResults.get(i).getScanResult();
                // nanoseconds -> microseconds
                if (result.timestamp >= mLastPnoScanSettings.startTimeNanos / 1_000) {
                    hwPnoScanResults.add(result);
                } else {
                    numFilteredScanResults++;
                }
            }

            if (numFilteredScanResults != 0) {
                Log.d(TAG, "Filtering out " + numFilteredScanResults + " pno scan results.");
            }

            if (mLastPnoScanSettings.pnoScanEventHandler != null) {
                ScanResult[] pnoScanResultsArray =
                        hwPnoScanResults.toArray(new ScanResult[hwPnoScanResults.size()]);
                mLastPnoScanSettings.pnoScanEventHandler.onPnoNetworkFound(pnoScanResultsArray);
            }
        }
    }

    /**
     * Return one of the WIFI_BAND_# values that was scanned for in this scan.
     */
    private static int getScannedBandsInternal(ChannelCollection channelCollection) {
        int bandsScanned = WifiScanner.WIFI_BAND_UNSPECIFIED;

        for (@WifiBandIndex int i = 0; i < WifiScanner.WIFI_BAND_COUNT; i++) {
            if (channelCollection.containsBand(1 << i)) {
                bandsScanned |= 1 << i;
            }
        }
        return bandsScanned;
    }

    private void pollLatestScanData() {
        synchronized (mSettingsLock) {
            if (mLastScanSettings == null) {
                 // got a scan before we started scanning or after scan was canceled
                return;
            }

            mNativeScanResults = mWifiNative.getScanResults(getIfaceName());
            List<ScanResult> singleScanResults = new ArrayList<>();
            int numFilteredScanResults = 0;
            for (int i = 0; i < mNativeScanResults.size(); ++i) {
                ScanResult result = mNativeScanResults.get(i).getScanResult();
                // nanoseconds -> microseconds
                if (result.timestamp >= mLastScanSettings.startTimeNanos / 1_000) {
                    // Allow even not explicitly requested 6Ghz results because they could be found
                    // via 6Ghz RNR.
                    if (mLastScanSettings.singleScanFreqs.containsChannel(
                                    result.frequency) || ScanResult.is6GHz(result.frequency)) {
                        singleScanResults.add(result);
                    } else {
                        numFilteredScanResults++;
                    }
                } else {
                    numFilteredScanResults++;
                }
            }
            if (numFilteredScanResults != 0) {
                Log.d(TAG, "Filtering out " + numFilteredScanResults + " scan results.");
            }

            if (mLastScanSettings.singleScanEventHandler != null) {
                if (mLastScanSettings.reportSingleScanFullResults) {
                    for (ScanResult scanResult : singleScanResults) {
                        // ignore buckets scanned since there is only one bucket for a single scan
                        mLastScanSettings.singleScanEventHandler.onFullScanResult(scanResult,
                                /* bucketsScanned */ 0);
                    }
                }
                Collections.sort(singleScanResults, SCAN_RESULT_SORT_COMPARATOR);
                mLatestSingleScanResult = new WifiScanner.ScanData(0, 0, 0,
                        getScannedBandsInternal(mLastScanSettings.singleScanFreqs),
                        singleScanResults.toArray(new ScanResult[singleScanResults.size()]));
                mLastScanSettings.singleScanEventHandler
                        .onScanStatus(WifiNative.WIFI_SCAN_RESULTS_AVAILABLE);
            }

            mLastScanSettings = null;
        }
    }


    @Override
    public WifiScanner.ScanData[] getLatestBatchedScanResults(boolean flush) {
        return null;
    }

    private boolean startHwPnoScan(WifiNative.PnoSettings pnoSettings) {
        return mWifiNative.startPnoScan(getIfaceName(), pnoSettings);
    }

    private void stopHwPnoScan() {
        mWifiNative.stopPnoScan(getIfaceName());
    }

    /**
     * Hw Pno Scan is required only for disconnected PNO when the device supports it.
     * @param isConnectedPno Whether this is connected PNO vs disconnected PNO.
     * @return true if HW PNO scan is required, false otherwise.
     */
    private boolean isHwPnoScanRequired(boolean isConnectedPno) {
        return (!isConnectedPno
                && mWifiGlobals.isBackgroundScanSupported());
    }

    @Override
    public boolean setHwPnoList(WifiNative.PnoSettings settings,
            WifiNative.PnoEventHandler eventHandler) {
        synchronized (mSettingsLock) {
            if (mLastPnoScanSettings != null) {
                Log.w(TAG, "Already running a PNO scan");
                return false;
            }
            if (!isHwPnoScanRequired(settings.isConnected)) {
                return false;
            }

            mLastPnoScanSettings = new LastPnoScanSettings(
                    mClock.getElapsedSinceBootNanos(),
                    settings.networkList, eventHandler);

            if (!startHwPnoScan(settings)) {
                Log.e(TAG, "Failed to start PNO scan");
                reportPnoScanFailure();
            }
            return true;
        }
    }

    @Override
    public boolean resetHwPnoList() {
        synchronized (mSettingsLock) {
            if (mLastPnoScanSettings == null) {
                Log.w(TAG, "No PNO scan running");
                return false;
            }
            mLastPnoScanSettings = null;
            // For wificond based PNO, we stop the scan immediately when we reset pno list.
            stopHwPnoScan();
            return true;
        }
    }

    @Override
    public boolean isHwPnoSupported(boolean isConnectedPno) {
        // Hw Pno Scan is supported only for disconnected PNO when the device supports it.
        return isHwPnoScanRequired(isConnectedPno);
    }

    @Override
    protected void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        synchronized (mSettingsLock) {
            long nowMs = mClock.getElapsedSinceBootMillis();
            Log.d(TAG, "Latest native scan results nowMs = " + nowMs);
            pw.println("Latest native scan results:");
            if (mNativeScanResults != null) {
                List<ScanResult> scanResults = mNativeScanResults.stream().map(r -> {
                    return r.getScanResult();
                }).collect(Collectors.toList());
                ScanResultUtil.dumpScanResults(pw, scanResults, nowMs);
            }
            pw.println("Latest native pno scan results:");
            if (mNativePnoScanResults != null) {
                List<ScanResult> pnoScanResults = mNativePnoScanResults.stream().map(r -> {
                    return r.getScanResult();
                }).collect(Collectors.toList());
                ScanResultUtil.dumpScanResults(pw, pnoScanResults, nowMs);
            }
            pw.println("Latest native scan results IEs:");
            if (mNativeScanResults != null) {
                for (ScanDetail detail : mNativeScanResults) {
                    if (detail.getInformationElementRawData() != null) {
                        pw.println(NativeUtil.hexStringFromByteArray(
                                detail.getInformationElementRawData()));
                    }
                }
            }
            pw.println("");
        }
    }

    private static class LastScanSettings {
        LastScanSettings(long startTimeNanos,
                boolean reportSingleScanFullResults,
                ChannelCollection singleScanFreqs,
                WifiNative.ScanEventHandler singleScanEventHandler) {
            this.startTimeNanos = startTimeNanos;
            this.reportSingleScanFullResults = reportSingleScanFullResults;
            this.singleScanFreqs = singleScanFreqs;
            this.singleScanEventHandler = singleScanEventHandler;
        }

        public long startTimeNanos;
        public boolean reportSingleScanFullResults;
        public ChannelCollection singleScanFreqs;
        public WifiNative.ScanEventHandler singleScanEventHandler;

    }

    private static class LastPnoScanSettings {
        LastPnoScanSettings(long startTimeNanos,
                WifiNative.PnoNetwork[] pnoNetworkList,
                WifiNative.PnoEventHandler pnoScanEventHandler) {
            this.startTimeNanos = startTimeNanos;
            this.pnoNetworkList = pnoNetworkList;
            this.pnoScanEventHandler = pnoScanEventHandler;
        }

        public long startTimeNanos;
        public WifiNative.PnoNetwork[] pnoNetworkList;
        public WifiNative.PnoEventHandler pnoScanEventHandler;

    }

}
