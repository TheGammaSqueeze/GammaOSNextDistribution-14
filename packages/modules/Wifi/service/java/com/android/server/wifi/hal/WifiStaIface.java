/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.server.wifi.hal;

import static android.net.wifi.WifiManager.ROAMING_MODE_NORMAL;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.hardware.wifi.WifiStatusCode;
import android.net.MacAddress;
import android.net.apf.ApfCapabilities;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager.RoamingMode;
import android.net.wifi.WifiScanner;
import android.net.wifi.twt.TwtRequest;
import android.net.wifi.twt.TwtSessionCallback;
import android.os.Bundle;
import android.util.Log;

import com.android.server.wifi.SsidTranslator;
import com.android.server.wifi.WifiLinkLayerStats;
import com.android.server.wifi.WifiNative;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Wrapper around a WifiStaIface.
 * May be initialized using a HIDL or AIDL WifiStaIface.
 */
public class WifiStaIface implements WifiHal.WifiInterface {
    private static final String TAG = "WifiStaIface";
    private final IWifiStaIface mWifiStaIface;

    public static final int SET_ROAMING_STATE_FAILURE_CODE =
            WifiNative.SET_FIRMWARE_ROAMING_FAILURE;

    /**
     * Parameters for a background scan request.
     */
    public static class StaBackgroundScanParameters {
        public int basePeriodInMs;
        public int maxApPerScan;
        public int reportThresholdPercent;
        public int reportThresholdNumScans;
        public List<WifiNative.BucketSettings> buckets;

        public StaBackgroundScanParameters(int inBasePeriodInMs, int inMaxApPerScan,
                int inReportThresholdPercent, int inReportThresholdNumScans,
                List<WifiNative.BucketSettings> inBuckets) {
            basePeriodInMs = inBasePeriodInMs;
            maxApPerScan = inMaxApPerScan;
            reportThresholdPercent = inReportThresholdPercent;
            reportThresholdNumScans = inReportThresholdNumScans;
            buckets = inBuckets;
        }
    }

    /**
     * Framework callback object. Will get called when the equivalent events are received
     * from the HAL.
     */
    public interface Callback {
        /**
         * Called for each received beacon/probe response for a scan with the
         * |REPORT_EVENTS_FULL_RESULTS| flag set in
         * |StaBackgroundScanBucketParameters.eventReportScheme|.
         *
         * @param cmdId Command ID corresponding to the request.
         * @param bucketsScanned Bitset where each bit indicates if the bucket with
         *        that index (starting at 0) was scanned.
         * @param result Full scan result for an AP.
         */
        void onBackgroundFullScanResult(int cmdId, int bucketsScanned, ScanResult result);

        /**
         * Callback indicating that an ongoing background scan request has failed.
         * The background scan needs to be restarted to continue scanning.
         *
         * @param cmdId Command ID corresponding to the request.
         */
        void onBackgroundScanFailure(int cmdId);

        /**
         * Called when the |StaBackgroundScanBucketParameters.eventReportScheme| flags
         * for at least one bucket that was just scanned was |REPORT_EVENTS_EACH_SCAN|,
         * or one of the configured thresholds was breached.
         *
         * @param cmdId Command ID corresponding to the request.
         * @param scanDatas List of scan results for all APs seen since the last callback.
         */
        void onBackgroundScanResults(int cmdId, WifiScanner.ScanData[] scanDatas);

        /**
         * Called when the RSSI of the currently connected access point goes beyond the
         * thresholds set via
         * {@link IWifiStaIface#startRssiMonitoring(int, int, int)}
         *
         * @param cmdId Command ID corresponding to the request.
         * @param currBssid BSSID of the currently connected access point.
         * @param currRssi RSSI of the currently connected access point.
         */
        void onRssiThresholdBreached(int cmdId, byte[] currBssid, int currRssi);

        /**
         * Called when TWT operation fails.
         *
         * @param cmdId Unique command id which is failed
         * @param twtErrorCode Error code
         */
        void onTwtFailure(int cmdId, @TwtSessionCallback.TwtErrorCode int twtErrorCode);

        /**
         * Called when {@link WifiStaIface#setupTwtSession(int, TwtRequest)} succeeds.
         *
         * @param cmdId Unique command id used in
         *              {@link WifiStaIface#setupTwtSession(int, TwtRequest)}
         * @param wakeDurationUs TWT wake duration for the session in microseconds
         * @param wakeIntervalUs TWT wake interval for the session in microseconds
         * @param linkId Multi link operation link id
         * @param sessionId TWT session id
         */
        void onTwtSessionCreate(int cmdId, int wakeDurationUs, long wakeIntervalUs, int linkId,
                int sessionId);

        /**
         * Called when TWT session is torndown by {@link WifiStaIface#tearDownTwtSession(int, int)}.
         * Can also be called unsolicitedly by the vendor software with proper reason code.
         *
         * @param cmdId Unique command id used in {@link WifiStaIface#tearDownTwtSession(int, int)}
         * @param twtSessionId TWT session Id
         * @param twtReasonCode Reason code for teardown
         */
        void onTwtSessionTeardown(int cmdId, int twtSessionId,
                @TwtSessionCallback.TwtReasonCode int twtReasonCode);

        /**
         * Called as a response to {@link WifiStaIface#getStatsTwtSession(int, int)}
         *
         * @param cmdId Unique command id used in {@link WifiStaIface#getStatsTwtSession(int, int)}
         * @param twtSessionId TWT session Id
         * @param twtStats TWT stats bundle
         */
        void onTwtSessionStats(int cmdId, int twtSessionId, Bundle twtStats);
    }

    public WifiStaIface(@NonNull android.hardware.wifi.V1_0.IWifiStaIface staIface,
            @NonNull Context context, @NonNull SsidTranslator ssidTranslator) {
        mWifiStaIface = createWifiStaIfaceHidlImplMockable(staIface, context, ssidTranslator);
    }

    public WifiStaIface(@NonNull android.hardware.wifi.IWifiStaIface staIface,
            @NonNull Context context, @NonNull SsidTranslator ssidTranslator) {
        mWifiStaIface = createWifiStaIfaceAidlImplMockable(staIface, context, ssidTranslator);
    }

    protected WifiStaIfaceHidlImpl createWifiStaIfaceHidlImplMockable(
            android.hardware.wifi.V1_0.IWifiStaIface staIface, @NonNull Context context,
            @NonNull SsidTranslator ssidTranslator) {
        return new WifiStaIfaceHidlImpl(staIface, context, ssidTranslator);
    }

    protected WifiStaIfaceAidlImpl createWifiStaIfaceAidlImplMockable(
            android.hardware.wifi.IWifiStaIface staIface, @NonNull Context context,
            @NonNull SsidTranslator ssidTranslator) {
        return new WifiStaIfaceAidlImpl(staIface, context, ssidTranslator);
    }

    private <T> T validateAndCall(String methodStr, T defaultVal, @NonNull Supplier<T> supplier) {
        if (mWifiStaIface == null) {
            Log.wtf(TAG, "Cannot call " + methodStr + " because mWifiStaIface is null");
            return defaultVal;
        }
        return supplier.get();
    }

    /**
     * See comments for {@link IWifiStaIface#registerFrameworkCallback(Callback)}
     */
    public boolean registerFrameworkCallback(Callback callback) {
        return validateAndCall("registerFrameworkCallback", false,
                () -> mWifiStaIface.registerFrameworkCallback(callback));
    }

    /**
     * See comments for {@link IWifiStaIface#getName()}
     */
    @Override
    @Nullable
    public String getName() {
        return validateAndCall("getName", null,
                () -> mWifiStaIface.getName());
    }

    /**
     * See comments for {@link IWifiStaIface#configureRoaming(List, List)}
     */
    public boolean configureRoaming(List<MacAddress> bssidBlocklist,
            List<byte[]> ssidAllowlist) {
        return validateAndCall("configureRoaming", false,
                () -> mWifiStaIface.configureRoaming(bssidBlocklist, ssidAllowlist));
    }

    /**
     * See comments for {@link IWifiStaIface#enableLinkLayerStatsCollection(boolean)}
     */
    public boolean enableLinkLayerStatsCollection(boolean debug) {
        return validateAndCall("enableLinkLayerStatsCollection", false,
                () -> mWifiStaIface.enableLinkLayerStatsCollection(debug));
    }

    /**
     * See comments for {@link IWifiStaIface#enableNdOffload(boolean)}
     */
    public boolean enableNdOffload(boolean enable) {
        return validateAndCall("enableNdOffload", false,
                () -> mWifiStaIface.enableNdOffload(enable));
    }

    /**
     * See comments for {@link IWifiStaIface#getApfPacketFilterCapabilities()}
     */
    public ApfCapabilities getApfPacketFilterCapabilities() {
        return validateAndCall("getApfPacketFilterCapabilities", new ApfCapabilities(0, 0, 0),
                () -> mWifiStaIface.getApfPacketFilterCapabilities());
    }

    /**
     * See comments for {@link IWifiStaIface#getBackgroundScanCapabilities()}
     */
    @Nullable
    public WifiNative.ScanCapabilities getBackgroundScanCapabilities() {
        return validateAndCall("getBackgroundScanCapabilities", null,
                () -> mWifiStaIface.getBackgroundScanCapabilities());
    }

    /**
     * See comments for {@link IWifiStaIface#getCapabilities()}
     */
    public long getCapabilities() {
        return validateAndCall("getCapabilities", 0L,
                () -> mWifiStaIface.getCapabilities());
    }

    /**
     * See comments for {@link IWifiStaIface#getDebugRxPacketFates()}
     */
    public List<WifiNative.RxFateReport> getDebugRxPacketFates() {
        return validateAndCall("getDebugRxPacketFates", new ArrayList<>(),
                () -> mWifiStaIface.getDebugRxPacketFates());
    }

    /**
     * See comments for {@link IWifiStaIface#getDebugTxPacketFates()}
     */
    public List<WifiNative.TxFateReport> getDebugTxPacketFates() {
        return validateAndCall("getDebugTxPacketFates", new ArrayList<>(),
                () -> mWifiStaIface.getDebugTxPacketFates());
    }

    /**
     * See comments for {@link IWifiStaIface#getFactoryMacAddress()}
     */
    @Nullable
    public MacAddress getFactoryMacAddress() {
        return validateAndCall("getFactoryMacAddress", null,
                () -> mWifiStaIface.getFactoryMacAddress());
    }

    /**
     * See comments for {@link IWifiStaIface#getCachedScanData()}
     */
    @Nullable
    public WifiScanner.ScanData getCachedScanData() {
        return validateAndCall("getCachedScanData", null,
                () -> mWifiStaIface.getCachedScanData());
    }


    /**
     * See comments for {@link IWifiStaIface#getLinkLayerStats()}
     */
    @Nullable
    public WifiLinkLayerStats getLinkLayerStats() {
        return validateAndCall("getLinkLayerStats", null,
                () -> mWifiStaIface.getLinkLayerStats());
    }

    /**
     * See comments for {@link IWifiStaIface#getRoamingCapabilities()}
     */
    @Nullable
    public WifiNative.RoamingCapabilities getRoamingCapabilities() {
        return validateAndCall("getRoamingCapabilities", null,
                () -> mWifiStaIface.getRoamingCapabilities());
    }

    /**
     * See comments for {@link IWifiStaIface#installApfPacketFilter(byte[])}
     */
    public boolean installApfPacketFilter(byte[] program) {
        return validateAndCall("installApfPacketFilter", false,
                () -> mWifiStaIface.installApfPacketFilter(program));
    }

    /**
     * See comments for {@link IWifiStaIface#readApfPacketFilterData()}
     */
    @Nullable
    public byte[] readApfPacketFilterData() {
        return validateAndCall("readApfPacketFilterData", null,
                () -> mWifiStaIface.readApfPacketFilterData());
    }

    /**
     * See comments for {@link IWifiStaIface#setMacAddress(MacAddress)}
     */
    public boolean setMacAddress(MacAddress mac) {
        return validateAndCall("setMacAddress", false,
                () -> mWifiStaIface.setMacAddress(mac));
    }

    /**
     * See comments for {@link IWifiStaIface#setRoamingState(int)}
     */
    public @WifiNative.RoamingEnableStatus int setRoamingState(
            @WifiNative.RoamingEnableState int state) {
        return validateAndCall("setRoamingState", SET_ROAMING_STATE_FAILURE_CODE,
                () -> mWifiStaIface.setRoamingState(state));
    }

    /**
     * See comments for {@link IWifiStaIface#setScanMode(boolean)}
     */
    public boolean setScanMode(boolean enable) {
        return validateAndCall("setScanMode", false,
                () -> mWifiStaIface.setScanMode(enable));
    }

    /**
     * See comments for {@link IWifiStaIface#startBackgroundScan(int, StaBackgroundScanParameters)}
     */
    public boolean startBackgroundScan(int cmdId, StaBackgroundScanParameters params) {
        return validateAndCall("startBackgroundScan", false,
                () -> mWifiStaIface.startBackgroundScan(cmdId, params));
    }

    /**
     * See comments for {@link IWifiStaIface#startDebugPacketFateMonitoring()}
     */
    public boolean startDebugPacketFateMonitoring() {
        return validateAndCall("startDebugPacketFateMonitoring", false,
                () -> mWifiStaIface.startDebugPacketFateMonitoring());
    }

    /**
     * See comments for {@link IWifiStaIface#startRssiMonitoring(int, int, int)}
     */
    public boolean startRssiMonitoring(int cmdId, int maxRssi, int minRssi) {
        return validateAndCall("startRssiMonitoring", false,
                () -> mWifiStaIface.startRssiMonitoring(cmdId, maxRssi, minRssi));
    }

    /**
     * See comments for {@link IWifiStaIface#startSendingKeepAlivePackets(int, byte[], int,
     *                         MacAddress, MacAddress, int)}
     */
    public boolean startSendingKeepAlivePackets(int cmdId, byte[] ipPacketData, int etherType,
            MacAddress srcAddress, MacAddress dstAddress, int periodInMs) {
        return validateAndCall("startSendingKeepAlivePackets", false,
                () -> mWifiStaIface.startSendingKeepAlivePackets(cmdId, ipPacketData, etherType,
                        srcAddress, dstAddress, periodInMs));
    }

    /**
     * See comments for {@link IWifiStaIface#stopBackgroundScan(int)}
     */
    public boolean stopBackgroundScan(int cmdId) {
        return validateAndCall("stopBackgroundScan", false,
                () -> mWifiStaIface.stopBackgroundScan(cmdId));
    }

    /**
     * See comments for {@link IWifiStaIface#stopRssiMonitoring(int)}
     */
    public boolean stopRssiMonitoring(int cmdId) {
        return validateAndCall("stopRssiMonitoring", false,
                () -> mWifiStaIface.stopRssiMonitoring(cmdId));
    }

    /**
     * See comments for {@link IWifiStaIface#stopSendingKeepAlivePackets(int)}
     */
    public boolean stopSendingKeepAlivePackets(int cmdId) {
        return validateAndCall("stopSendingKeepAlivePackets", false,
                () -> mWifiStaIface.stopSendingKeepAlivePackets(cmdId));
    }

    /**
     * See comments for {@link IWifiStaIface#setDtimMultiplier(int)}
     */
    public boolean setDtimMultiplier(int multiplier) {
        return validateAndCall("setDtimMultiplier", false,
                () -> mWifiStaIface.setDtimMultiplier(multiplier));
    }

    /**
     * See comments for {@link IWifiStaIface#setRoamingMode(int)}
     */
    public @WifiStatusCode int setRoamingMode(@RoamingMode int roamingMode) {
        return validateAndCall("setRoamingMode", ROAMING_MODE_NORMAL,
                () -> mWifiStaIface.setRoamingMode(roamingMode));
    }

    /**
     * See {@link IWifiStaIface#getTwtCapabilities()}
     */
    public Bundle getTwtCapabilities() {
        return validateAndCall("getTwtCapabilities", null,
                () -> mWifiStaIface.getTwtCapabilities());
    }

    /**
     * See {@link IWifiStaIface#setupTwtSession(int, TwtRequest)}
     */
    public boolean setupTwtSession(int cmdId, TwtRequest twtRequest) {
        return validateAndCall("setupTwtSession", false,
                () -> mWifiStaIface.setupTwtSession(cmdId, twtRequest));
    }

    /**
     * See {@link IWifiStaIface#tearDownTwtSession(int, int)}
     */
    public boolean tearDownTwtSession(int commandId, int sessionId) {
        return validateAndCall("tearDownTwtSession", false,
                () -> mWifiStaIface.tearDownTwtSession(commandId, sessionId));
    }

    /**
     * See {@link IWifiStaIface#getStatsTwtSession(int, int)}
     */
    public boolean getStatsTwtSession(int commandId, int sessionId) {
        return validateAndCall("getStatsTwtSession", false,
                () -> mWifiStaIface.getStatsTwtSession(commandId, sessionId));
    }
}

