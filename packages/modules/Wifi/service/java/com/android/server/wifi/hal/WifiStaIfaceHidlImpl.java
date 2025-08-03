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

import static android.net.wifi.WifiUsabilityStatsEntry.LINK_STATE_UNKNOWN;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.hardware.wifi.V1_0.IWifiStaIfaceEventCallback;
import android.hardware.wifi.V1_0.StaBackgroundScanBucketEventReportSchemeMask;
import android.hardware.wifi.V1_0.StaBackgroundScanBucketParameters;
import android.hardware.wifi.V1_0.StaBackgroundScanParameters;
import android.hardware.wifi.V1_0.StaLinkLayerIfaceStats;
import android.hardware.wifi.V1_0.StaLinkLayerRadioStats;
import android.hardware.wifi.V1_0.StaLinkLayerStats;
import android.hardware.wifi.V1_0.StaRoamingConfig;
import android.hardware.wifi.V1_0.StaRoamingState;
import android.hardware.wifi.V1_0.StaScanData;
import android.hardware.wifi.V1_0.StaScanDataFlagMask;
import android.hardware.wifi.V1_0.StaScanResult;
import android.hardware.wifi.V1_0.WifiDebugPacketFateFrameType;
import android.hardware.wifi.V1_0.WifiDebugRxPacketFate;
import android.hardware.wifi.V1_0.WifiDebugRxPacketFateReport;
import android.hardware.wifi.V1_0.WifiDebugTxPacketFate;
import android.hardware.wifi.V1_0.WifiDebugTxPacketFateReport;
import android.hardware.wifi.V1_0.WifiStatus;
import android.hardware.wifi.V1_0.WifiStatusCode;
import android.hardware.wifi.V1_5.WifiBand;
import android.net.MacAddress;
import android.net.apf.ApfCapabilities;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiScanner;
import android.net.wifi.WifiSsid;
import android.os.RemoteException;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;
import com.android.server.wifi.SsidTranslator;
import com.android.server.wifi.WifiLinkLayerStats;
import com.android.server.wifi.WifiLoggerHal;
import com.android.server.wifi.WifiNative;
import com.android.server.wifi.util.BitMask;
import com.android.server.wifi.util.GeneralUtil;
import com.android.server.wifi.util.NativeUtil;
import com.android.wifi.resources.R;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;


/**
 * HIDL implementation of the IWifiStaIface interface.
 */
public class WifiStaIfaceHidlImpl implements IWifiStaIface {
    private static final String TAG = "WifiStaIfaceHidlImpl";
    private android.hardware.wifi.V1_0.IWifiStaIface mWifiStaIface;
    private String mIfaceName;
    private IWifiStaIfaceEventCallback mHalCallback;
    private WifiStaIface.Callback mFrameworkCallback;
    private Context mContext;
    private SsidTranslator mSsidTranslator;
    private static final int DEFAULT_LINK = 0;
    private static final int NUM_OF_LINKS = 1;

    public WifiStaIfaceHidlImpl(@NonNull android.hardware.wifi.V1_0.IWifiStaIface staIface,
            @NonNull Context context, @NonNull SsidTranslator ssidTranslator) {
        mWifiStaIface = staIface;
        mContext = context;
        mSsidTranslator = ssidTranslator;
        mHalCallback = new StaIfaceEventCallback();
    }

    /**
     * See comments for {@link IWifiStaIface#registerFrameworkCallback(WifiStaIface.Callback)}
     */
    public boolean registerFrameworkCallback(WifiStaIface.Callback callback) {
        final String methodStr = "registerFrameworkCallback";
        return validateAndCall(methodStr, false,
                () -> registerFrameworkCallbackInternal(methodStr, callback));
    }

    /**
     * See comments for {@link IWifiStaIface#getName()}
     */
    @Nullable
    public String getName() {
        final String methodStr = "getName";
        return validateAndCall(methodStr, null,
                () -> getNameInternal(methodStr));
    }

    /**
     * See comments for {@link IWifiStaIface#configureRoaming(List, List)}
     */
    public boolean configureRoaming(List<MacAddress> bssidBlocklist,
            List<byte[]> ssidAllowlist) {
        final String methodStr = "configureRoaming";
        return validateAndCall(methodStr, false,
                () -> configureRoamingInternal(methodStr, bssidBlocklist, ssidAllowlist));
    }

    /**
     * See comments for {@link IWifiStaIface#enableLinkLayerStatsCollection(boolean)}
     */
    public boolean enableLinkLayerStatsCollection(boolean debug) {
        final String methodStr = "enableLinkLayerStatsCollection";
        return validateAndCall(methodStr, false,
                () -> enableLinkLayerStatsCollectionInternal(methodStr, debug));
    }

    /**
     * See comments for {@link IWifiStaIface#enableNdOffload(boolean)}
     */
    public boolean enableNdOffload(boolean enable) {
        final String methodStr = "enableNdOffload";
        return validateAndCall(methodStr, false,
                () -> enableNdOffloadInternal(methodStr, enable));
    }

    /**
     * See comments for {@link IWifiStaIface#getApfPacketFilterCapabilities()}
     */
    public ApfCapabilities getApfPacketFilterCapabilities() {
        final String methodStr = "getApfPacketFilterCapabilities";
        return validateAndCall(methodStr, new ApfCapabilities(0, 0, 0),
                () -> getApfPacketFilterCapabilitiesInternal(methodStr));
    }

    /**
     * See comments for {@link IWifiStaIface#getBackgroundScanCapabilities()}
     */
    @Nullable
    public WifiNative.ScanCapabilities getBackgroundScanCapabilities() {
        final String methodStr = "getBackgroundScanCapabilities";
        return validateAndCall(methodStr, null,
                () -> getBackgroundScanCapabilitiesInternal(methodStr));
    }

    /**
     * See comments for {@link IWifiStaIface#getCapabilities()}
     */
    public long getCapabilities() {
        final String methodStr = "getCapabilities";
        return validateAndCall(methodStr, 0L,
                () -> getCapabilitiesInternal(methodStr));
    }

    /**
     * See comments for {@link IWifiStaIface#getDebugRxPacketFates()}
     */
    public List<WifiNative.RxFateReport> getDebugRxPacketFates() {
        final String methodStr = "getDebugRxPacketFates";
        return validateAndCall(methodStr, new ArrayList<>(),
                () -> getDebugRxPacketFatesInternal(methodStr));
    }

    /**
     * See comments for {@link IWifiStaIface#getDebugTxPacketFates()}
     */
    public List<WifiNative.TxFateReport> getDebugTxPacketFates() {
        final String methodStr = "getDebugTxPacketFates";
        return validateAndCall(methodStr, new ArrayList<>(),
                () -> getDebugTxPacketFatesInternal(methodStr));
    }

    /**
     * See comments for {@link IWifiStaIface#getFactoryMacAddress()}
     */
    @Nullable
    public MacAddress getFactoryMacAddress() {
        final String methodStr = "getFactoryMacAddress";
        return validateAndCall(methodStr, null,
                () -> getFactoryMacAddressInternal(methodStr));
    }

    /**
     * See comments for {@link IWifiStaIface#getCachedScanData()}
     */
    @Nullable
    public WifiScanner.ScanData getCachedScanData() {
        Log.d(TAG, "getCachedScanData is not implemented by HIDL");
        return null;
    }

    /**
     * See comments for {@link IWifiStaIface#getLinkLayerStats()}
     */
    @Nullable
    public WifiLinkLayerStats getLinkLayerStats() {
        final String methodStr = "getLinkLayerStats";
        return validateAndCall(methodStr, null,
                () -> getLinkLayerStatsInternal(methodStr));
    }

    /**
     * See comments for {@link IWifiStaIface#getRoamingCapabilities()}
     */
    @Nullable
    public WifiNative.RoamingCapabilities getRoamingCapabilities() {
        final String methodStr = "getRoamingCapabilities";
        return validateAndCall(methodStr, null,
                () -> getRoamingCapabilitiesInternal(methodStr));
    }

    /**
     * See comments for {@link IWifiStaIface#installApfPacketFilter(byte[])}
     */
    public boolean installApfPacketFilter(byte[] program) {
        final String methodStr = "installApfPacketFilter";
        return validateAndCall(methodStr, false,
                () -> installApfPacketFilterInternal(methodStr, program));
    }

    /**
     * See comments for {@link IWifiStaIface#readApfPacketFilterData()}
     */
    @Nullable
    public byte[] readApfPacketFilterData() {
        final String methodStr = "readApfPacketFilterData";
        return validateAndCall(methodStr, null,
                () -> readApfPacketFilterDataInternal(methodStr));
    }

    /**
     * See comments for {@link IWifiStaIface#setMacAddress(MacAddress)}
     */
    public boolean setMacAddress(MacAddress mac) {
        final String methodStr = "setMacAddress";
        return validateAndCall(methodStr, false,
                () -> setMacAddressInternal(methodStr, mac));
    }

    /**
     * See comments for {@link IWifiStaIface#setRoamingState(int)}
     */
    public @WifiNative.RoamingEnableStatus int setRoamingState(
            @WifiNative.RoamingEnableState int state) {
        final String methodStr = "setRoamingState";
        return validateAndCall(methodStr, WifiNative.SET_FIRMWARE_ROAMING_FAILURE,
                () -> setRoamingStateInternal(methodStr, state));
    }

    /**
     * See comments for {@link IWifiStaIface#setScanMode(boolean)}
     */
    public boolean setScanMode(boolean enable) {
        final String methodStr = "setScanMode";
        return validateAndCall(methodStr, false,
                () -> setScanModeInternal(methodStr, enable));
    }

    /**
     * See comments for
     * {@link IWifiStaIface#startBackgroundScan(int, WifiStaIface.StaBackgroundScanParameters)}
     */
    public boolean startBackgroundScan(int cmdId, WifiStaIface.StaBackgroundScanParameters params) {
        final String methodStr = "startBackgroundScan";
        return validateAndCall(methodStr, false,
                () -> startBackgroundScanInternal(methodStr, cmdId, params));
    }

    /**
     * See comments for {@link IWifiStaIface#startDebugPacketFateMonitoring()}
     */
    public boolean startDebugPacketFateMonitoring() {
        final String methodStr = "startDebugPacketFateMonitoring";
        return validateAndCall(methodStr, false,
                () -> startDebugPacketFateMonitoringInternal(methodStr));
    }

    /**
     * See comments for
     * {@link IWifiStaIface#startRssiMonitoring(int, int, int)}
     */
    public boolean startRssiMonitoring(int cmdId, int maxRssi, int minRssi) {
        final String methodStr = "startRssiMonitoring";
        return validateAndCall(methodStr, false,
                () -> startRssiMonitoringInternal(methodStr, cmdId, maxRssi, minRssi));
    }

    /**
     * See comments for {@link IWifiStaIface#startSendingKeepAlivePackets(int, byte[], int,
     *                         MacAddress, MacAddress, int)}
     */
    public boolean startSendingKeepAlivePackets(int cmdId, byte[] ipPacketData, int etherType,
            MacAddress srcAddress, MacAddress dstAddress, int periodInMs) {
        final String methodStr = "startSendingKeepAlivePackets";
        return validateAndCall(methodStr, false,
                () -> startSendingKeepAlivePacketsInternal(methodStr, cmdId, ipPacketData,
                        etherType, srcAddress, dstAddress, periodInMs));
    }

    /**
     * See comments for {@link IWifiStaIface#stopBackgroundScan(int)}
     */
    public boolean stopBackgroundScan(int cmdId) {
        final String methodStr = "stopBackgroundScan";
        return validateAndCall(methodStr, false,
                () -> stopBackgroundScanInternal(methodStr, cmdId));
    }

    /**
     * See comments for {@link IWifiStaIface#stopRssiMonitoring(int)}
     */
    public boolean stopRssiMonitoring(int cmdId) {
        final String methodStr = "stopRssiMonitoring";
        return validateAndCall(methodStr, false,
                () -> stopRssiMonitoringInternal(methodStr, cmdId));
    }

    /**
     * See comments for {@link IWifiStaIface#stopSendingKeepAlivePackets(int)}
     */
    public boolean stopSendingKeepAlivePackets(int cmdId) {
        final String methodStr = "stopSendingKeepAlivePackets";
        return validateAndCall(methodStr, false,
                () -> stopSendingKeepAlivePacketsInternal(methodStr, cmdId));
    }

    /**
     * See comments for {@link IWifiStaIface#setDtimMultiplier(int)}
     */
    public boolean setDtimMultiplier(int multiplier) {
        Log.d(TAG, "setDtimMultiplier is not implemented by HIDL");
        return false;
    }

    /**
     * See comments for {@link IWifiStaIface#setRoamingMode(int)}
     */
    public int setRoamingMode(int roamingMode) {
        Log.d(TAG, "setRoamingMode is not implemented by HIDL");
        return 0;
    }

    // Internal Implementations

    private boolean registerFrameworkCallbackInternal(String methodStr,
            WifiStaIface.Callback callback) {
        if (mFrameworkCallback != null) {
            Log.e(TAG, "Framework callback is already registered");
            return false;
        } else if (callback == null) {
            Log.e(TAG, "Cannot register a null callback");
            return false;
        }

        try {
            WifiStatus status = mWifiStaIface.registerEventCallback(mHalCallback);
            if (!isOk(status, methodStr)) return false;
            mFrameworkCallback = callback;
            return true;
        } catch (RemoteException e) {
            handleRemoteException(e, methodStr);
            return false;
        }
    }

    private String getNameInternal(String methodStr) {
        if (mIfaceName != null) return mIfaceName;
        GeneralUtil.Mutable<String> nameResp = new GeneralUtil.Mutable<>();
        try {
            mWifiStaIface.getName((WifiStatus status, String name) -> {
                if (isOk(status, methodStr)) {
                    nameResp.value = name;
                    mIfaceName = name;
                }
            });
        } catch (RemoteException e) {
            handleRemoteException(e, methodStr);
        }
        return nameResp.value;
    }

    private boolean configureRoamingInternal(String methodStr, List<MacAddress> bssidBlocklist,
            List<byte[]> ssidAllowlist) {
        StaRoamingConfig config = new StaRoamingConfig();
        config.ssidWhitelist = new ArrayList<>(ssidAllowlist);
        config.bssidBlacklist = new ArrayList<>();
        for (MacAddress bssid : bssidBlocklist) {
            config.bssidBlacklist.add(bssid.toByteArray());
        }
        try {
            WifiStatus status = mWifiStaIface.configureRoaming(config);
            return isOk(status, methodStr);
        } catch (RemoteException e) {
            handleRemoteException(e, methodStr);
            return false;
        }
    }

    private boolean enableLinkLayerStatsCollectionInternal(String methodStr, boolean debug) {
        try {
            WifiStatus status = mWifiStaIface.enableLinkLayerStatsCollection(debug);
            return isOk(status, methodStr);
        } catch (RemoteException e) {
            handleRemoteException(e, methodStr);
            return false;
        }
    }

    private boolean enableNdOffloadInternal(String methodStr, boolean enable) {
        try {
            WifiStatus status = mWifiStaIface.enableNdOffload(enable);
            return isOk(status, methodStr);
        } catch (RemoteException e) {
            handleRemoteException(e, methodStr);
            return false;
        }
    }

    private ApfCapabilities getApfPacketFilterCapabilitiesInternal(String methodStr) {
        GeneralUtil.Mutable<ApfCapabilities> apfResp =
                new GeneralUtil.Mutable<>(new ApfCapabilities(0, 0, 0));
        try {
            mWifiStaIface.getApfPacketFilterCapabilities((status, caps) -> {
                if (isOk(status, methodStr)) {
                    apfResp.value = new ApfCapabilities(
                            /* apfVersionSupported */   caps.version,
                            /* maximumApfProgramSize */ caps.maxLength,
                            /* apfPacketFormat */       android.system.OsConstants.ARPHRD_ETHER);
                }
            });
        } catch (RemoteException e) {
            handleRemoteException(e, methodStr);
        }
        return apfResp.value;
    }

    private WifiNative.ScanCapabilities getBackgroundScanCapabilitiesInternal(String methodStr) {
        GeneralUtil.Mutable<WifiNative.ScanCapabilities> scanResp = new GeneralUtil.Mutable<>();
        try {
            mWifiStaIface.getBackgroundScanCapabilities((status, caps) -> {
                if (isOk(status, methodStr)) {
                    WifiNative.ScanCapabilities out = new WifiNative.ScanCapabilities();
                    out.max_scan_cache_size = caps.maxCacheSize;
                    out.max_ap_cache_per_scan = caps.maxApCachePerScan;
                    out.max_scan_buckets = caps.maxBuckets;
                    out.max_rssi_sample_size = 0;
                    out.max_scan_reporting_threshold = caps.maxReportingThreshold;
                    scanResp.value = out;
                }
            });
        } catch (RemoteException e) {
            handleRemoteException(e, methodStr);
        }
        return scanResp.value;
    }

    private long getCapabilitiesInternal(String methodStr) {
        GeneralUtil.Mutable<Long> capsResp = new GeneralUtil.Mutable<>(0L);
        try {
            mWifiStaIface.getCapabilities((status, caps) -> {
                if (isOk(status, methodStr)) {
                    capsResp.value = halToFrameworkStaIfaceCapability(caps);
                }
            });
        } catch (RemoteException e) {
            handleRemoteException(e, methodStr);
        }
        return capsResp.value;
    }

    private List<WifiNative.RxFateReport> getDebugRxPacketFatesInternal(String methodStr) {
        try {
            List<WifiNative.RxFateReport> reportBufs = new ArrayList<>();
            mWifiStaIface.getDebugRxPacketFates((status, fates) -> {
                if (!isOk(status, methodStr)) return;
                for (WifiDebugRxPacketFateReport fate : fates) {
                    if (reportBufs.size() >= WifiLoggerHal.MAX_FATE_LOG_LEN) break;
                    byte code = halToFrameworkRxPktFate(fate.fate);
                    long us = fate.frameInfo.driverTimestampUsec;
                    byte type = halToFrameworkPktFateFrameType(fate.frameInfo.frameType);
                    byte[] frame = NativeUtil.byteArrayFromArrayList(
                            fate.frameInfo.frameContent);
                    reportBufs.add(new WifiNative.RxFateReport(code, us, type, frame));
                }
            });
            return reportBufs;
        } catch (RemoteException e) {
            handleRemoteException(e, methodStr);
            return new ArrayList<>();
        }
    }

    private List<WifiNative.TxFateReport> getDebugTxPacketFatesInternal(String methodStr) {
        try {
            List<WifiNative.TxFateReport> reportBufs = new ArrayList<>();
            mWifiStaIface.getDebugTxPacketFates((status, fates) -> {
                if (!isOk(status, methodStr)) return;
                for (WifiDebugTxPacketFateReport fate : fates) {
                    if (reportBufs.size() >= WifiLoggerHal.MAX_FATE_LOG_LEN) break;
                    byte code = halToFrameworkTxPktFate(fate.fate);
                    long us = fate.frameInfo.driverTimestampUsec;
                    byte type = halToFrameworkPktFateFrameType(fate.frameInfo.frameType);
                    byte[] frame = NativeUtil.byteArrayFromArrayList(
                            fate.frameInfo.frameContent);
                    reportBufs.add(new WifiNative.TxFateReport(code, us, type, frame));
                }
            });
            return reportBufs;
        } catch (RemoteException e) {
            handleRemoteException(e, methodStr);
            return new ArrayList<>();
        }
    }

    private MacAddress getFactoryMacAddressInternal(String methodStr) {
        GeneralUtil.Mutable<MacAddress> macResp = new GeneralUtil.Mutable<>();
        try {
            android.hardware.wifi.V1_3.IWifiStaIface sta13 = getWifiStaIfaceV1_3Mockable();
            if (sta13 == null) return null;
            sta13.getFactoryMacAddress((status, macBytes) -> {
                if (isOk(status, methodStr)) {
                    macResp.value = MacAddress.fromBytes(macBytes);
                }
            });
        } catch (RemoteException e) {
            handleRemoteException(e, methodStr);
        }
        return macResp.value;
    }

    private WifiLinkLayerStats getLinkLayerStatsInternal(String methodStr) {
        if (getWifiStaIfaceV1_6Mockable() != null) {
            return getLinkLayerStatsV1_6Internal(methodStr);
        } else if (getWifiStaIfaceV1_5Mockable() != null) {
            return getLinkLayerStatsV1_5Internal(methodStr);
        } else if (getWifiStaIfaceV1_3Mockable() != null) {
            return getLinkLayerStatsV1_3Internal(methodStr);
        } else {
            return getLinkLayerStatsV1_0Internal(methodStr);
        }
    }

    private WifiLinkLayerStats getLinkLayerStatsV1_0Internal(String methodStr) {
        final String methodStrV1_0 = methodStr + "V1_0";
        GeneralUtil.Mutable<StaLinkLayerStats> statsResp = new GeneralUtil.Mutable<>();
        try {
            mWifiStaIface.getLinkLayerStats((status, stats) -> {
                if (isOk(status, methodStrV1_0)) {
                    statsResp.value = stats;
                }
            });
        } catch (RemoteException e) {
            handleRemoteException(e, methodStrV1_0);
            return null;
        }
        return frameworkFromHalLinkLayerStats(statsResp.value);
    }

    private WifiLinkLayerStats getLinkLayerStatsV1_3Internal(String methodStr) {
        final String methodStrV1_3 = methodStr + "V1_3";
        GeneralUtil.Mutable<android.hardware.wifi.V1_3.StaLinkLayerStats> statsResp =
                new GeneralUtil.Mutable<>();
        try {
            android.hardware.wifi.V1_3.IWifiStaIface iface13 = getWifiStaIfaceV1_3Mockable();
            if (iface13 == null) return null;
            iface13.getLinkLayerStats_1_3((status, stats) -> {
                if (isOk(status, methodStrV1_3)) {
                    statsResp.value = stats;
                }
            });
        } catch (RemoteException e) {
            handleRemoteException(e, methodStrV1_3);
            return null;
        }
        return frameworkFromHalLinkLayerStats_1_3(statsResp.value);
    }

    private WifiLinkLayerStats getLinkLayerStatsV1_5Internal(String methodStr) {
        final String methodStrV1_5 = methodStr + "V1_5";
        GeneralUtil.Mutable<android.hardware.wifi.V1_5.StaLinkLayerStats> statsResp =
                new GeneralUtil.Mutable<>();
        try {
            android.hardware.wifi.V1_5.IWifiStaIface iface15 = getWifiStaIfaceV1_5Mockable();
            if (iface15 == null) return null;
            iface15.getLinkLayerStats_1_5((status, stats) -> {
                if (isOk(status, methodStrV1_5)) {
                    statsResp.value = stats;
                }
            });
        } catch (RemoteException e) {
            handleRemoteException(e, methodStrV1_5);
            return null;
        }
        return frameworkFromHalLinkLayerStats_1_5(statsResp.value);
    }

    private WifiLinkLayerStats getLinkLayerStatsV1_6Internal(String methodStr) {
        final String methodStrV1_6 = methodStr + "V1_6";
        GeneralUtil.Mutable<android.hardware.wifi.V1_6.StaLinkLayerStats> statsResp =
                new GeneralUtil.Mutable<>();
        try {
            android.hardware.wifi.V1_6.IWifiStaIface iface16 = getWifiStaIfaceV1_6Mockable();
            if (iface16 == null) return null;
            iface16.getLinkLayerStats_1_6((status, stats) -> {
                if (isOk(status, methodStrV1_6)) {
                    statsResp.value = stats;
                }
            });
        } catch (RemoteException e) {
            handleRemoteException(e, methodStrV1_6);
            return null;
        } catch (Exception e) {
            handleException(e, methodStrV1_6);
            return null;
        }
        return frameworkFromHalLinkLayerStats_1_6(statsResp.value);
    }

    private WifiNative.RoamingCapabilities getRoamingCapabilitiesInternal(String methodStr) {
        GeneralUtil.Mutable<WifiNative.RoamingCapabilities> capsResp = new GeneralUtil.Mutable<>();
        try {
            mWifiStaIface.getRoamingCapabilities((status, caps) -> {
                if (isOk(status, methodStr)) {
                    WifiNative.RoamingCapabilities out = new WifiNative.RoamingCapabilities();
                    out.maxBlocklistSize = caps.maxBlacklistSize;
                    out.maxAllowlistSize = caps.maxWhitelistSize;
                    capsResp.value = out;
                }
            });
        } catch (RemoteException e) {
            handleRemoteException(e, methodStr);
        }
        return capsResp.value;
    }

    private boolean installApfPacketFilterInternal(String methodStr, byte[] program) {
        try {
            int cmdId = 0; // We only aspire to support one program at a time.
            ArrayList<Byte> filter = NativeUtil.byteArrayToArrayList(program);
            WifiStatus status = mWifiStaIface.installApfPacketFilter(cmdId, filter);
            return isOk(status, methodStr);
        } catch (RemoteException e) {
            handleRemoteException(e, methodStr);
            return false;
        }
    }

    private byte[] readApfPacketFilterDataInternal(String methodStr) {
        GeneralUtil.Mutable<byte[]> dataResp = new GeneralUtil.Mutable<>();
        try {
            android.hardware.wifi.V1_2.IWifiStaIface iface12 = getWifiStaIfaceV1_2Mockable();
            if (iface12 == null) return null;
            iface12.readApfPacketFilterData((status, data) -> {
                if (isOk(status, methodStr)) {
                    dataResp.value = NativeUtil.byteArrayFromArrayList(data);
                }
            });
        } catch (RemoteException e) {
            handleRemoteException(e, methodStr);
        }
        return dataResp.value;
    }

    private boolean setMacAddressInternal(String methodStr, MacAddress mac) {
        try {
            android.hardware.wifi.V1_2.IWifiStaIface iface12 = getWifiStaIfaceV1_2Mockable();
            if (iface12 == null) return false;
            byte[] macBytes = mac.toByteArray();
            WifiStatus status = iface12.setMacAddress(macBytes);
            return isOk(status, methodStr);
        } catch (RemoteException e) {
            handleRemoteException(e, methodStr);
            return false;
        }
    }

    private @WifiNative.RoamingEnableStatus int setRoamingStateInternal(String methodStr,
            @WifiNative.RoamingEnableState int state) {
        byte halState = frameworkToHalStaRoamingState(state);
        if (halState == -1) {
            return WifiStaIface.SET_ROAMING_STATE_FAILURE_CODE;
        }

        try {
            WifiStatus status = mWifiStaIface.setRoamingState(halState);
            if (isOk(status, methodStr)) {
                return WifiNative.SET_FIRMWARE_ROAMING_SUCCESS;
            } else if (status.code == WifiStatusCode.ERROR_BUSY) {
                return WifiNative.SET_FIRMWARE_ROAMING_BUSY;
            } else {
                return WifiStaIface.SET_ROAMING_STATE_FAILURE_CODE;
            }
        } catch (RemoteException e) {
            handleRemoteException(e, methodStr);
            return WifiStaIface.SET_ROAMING_STATE_FAILURE_CODE;
        }
    }

    private boolean setScanModeInternal(String methodStr, boolean enable) {
        try {
            android.hardware.wifi.V1_5.IWifiStaIface iface15 = getWifiStaIfaceV1_5Mockable();
            if (iface15 == null) return false;
            WifiStatus status = iface15.setScanMode(enable);
            return isOk(status, methodStr);
        } catch (RemoteException e) {
            handleRemoteException(e, methodStr);
            return false;
        }
    }

    private boolean startBackgroundScanInternal(String methodStr, int cmdId,
            WifiStaIface.StaBackgroundScanParameters params) {
        try {
            StaBackgroundScanParameters halParams = frameworkToHalBackgroundScanParams(params);
            WifiStatus status = mWifiStaIface.startBackgroundScan(cmdId, halParams);
            return isOk(status, methodStr);
        } catch (RemoteException e) {
            handleRemoteException(e, methodStr);
            return false;
        }
    }

    private boolean startDebugPacketFateMonitoringInternal(String methodStr) {
        try {
            WifiStatus status = mWifiStaIface.startDebugPacketFateMonitoring();
            return isOk(status, methodStr);
        } catch (RemoteException e) {
            handleRemoteException(e, methodStr);
            return false;
        }
    }

    private boolean startRssiMonitoringInternal(String methodStr, int cmdId, int maxRssi,
            int minRssi) {
        try {
            WifiStatus status = mWifiStaIface.startRssiMonitoring(cmdId, maxRssi, minRssi);
            return isOk(status, methodStr);
        } catch (RemoteException e) {
            handleRemoteException(e, methodStr);
            return false;
        }
    }

    private boolean startSendingKeepAlivePacketsInternal(String methodStr, int cmdId,
            byte[] ipPacketData, int etherType, MacAddress srcAddress, MacAddress dstAddress,
            int periodInMs) {
        try {
            ArrayList<Byte> data = NativeUtil.byteArrayToArrayList(ipPacketData);
            byte[] srcMac = srcAddress.toByteArray();
            byte[] dstMac = dstAddress.toByteArray();
            WifiStatus status = mWifiStaIface.startSendingKeepAlivePackets(
                    cmdId, data, (short) etherType, srcMac, dstMac, periodInMs);
            return isOk(status, methodStr);
        } catch (RemoteException e) {
            handleRemoteException(e, methodStr);
            return false;
        }
    }

    private boolean stopBackgroundScanInternal(String methodStr, int cmdId) {
        try {
            WifiStatus status = mWifiStaIface.stopBackgroundScan(cmdId);
            return isOk(status, methodStr);
        } catch (RemoteException e) {
            handleRemoteException(e, methodStr);
            return false;
        }
    }

    private boolean stopRssiMonitoringInternal(String methodStr, int cmdId) {
        try {
            WifiStatus status = mWifiStaIface.stopRssiMonitoring(cmdId);
            return isOk(status, methodStr);
        } catch (RemoteException e) {
            handleRemoteException(e, methodStr);
            return false;
        }
    }

    private boolean stopSendingKeepAlivePacketsInternal(String methodStr, int cmdId) {
        try {
            WifiStatus status = mWifiStaIface.stopSendingKeepAlivePackets(cmdId);
            return isOk(status, methodStr);
        } catch (RemoteException e) {
            handleRemoteException(e, methodStr);
            return false;
        }
    }


    // Helper Functions

    private static byte frameworkToHalStaRoamingState(@WifiNative.RoamingEnableState int state) {
        switch (state) {
            case WifiNative.DISABLE_FIRMWARE_ROAMING:
                return StaRoamingState.DISABLED;
            case WifiNative.ENABLE_FIRMWARE_ROAMING:
                return StaRoamingState.ENABLED;
            default:
                Log.e(TAG, "Invalid firmware roaming state enum: " + state);
                return -1;
        }
    }

    private static byte halToFrameworkPktFateFrameType(int type) {
        switch (type) {
            case WifiDebugPacketFateFrameType.UNKNOWN:
                return WifiLoggerHal.FRAME_TYPE_UNKNOWN;
            case WifiDebugPacketFateFrameType.ETHERNET_II:
                return WifiLoggerHal.FRAME_TYPE_ETHERNET_II;
            case WifiDebugPacketFateFrameType.MGMT_80211:
                return WifiLoggerHal.FRAME_TYPE_80211_MGMT;
            default:
                throw new IllegalArgumentException("bad " + type);
        }
    }

    private static byte halToFrameworkRxPktFate(int type) {
        switch (type) {
            case WifiDebugRxPacketFate.SUCCESS:
                return WifiLoggerHal.RX_PKT_FATE_SUCCESS;
            case WifiDebugRxPacketFate.FW_QUEUED:
                return WifiLoggerHal.RX_PKT_FATE_FW_QUEUED;
            case WifiDebugRxPacketFate.FW_DROP_FILTER:
                return WifiLoggerHal.RX_PKT_FATE_FW_DROP_FILTER;
            case WifiDebugRxPacketFate.FW_DROP_INVALID:
                return WifiLoggerHal.RX_PKT_FATE_FW_DROP_INVALID;
            case WifiDebugRxPacketFate.FW_DROP_NOBUFS:
                return WifiLoggerHal.RX_PKT_FATE_FW_DROP_NOBUFS;
            case WifiDebugRxPacketFate.FW_DROP_OTHER:
                return WifiLoggerHal.RX_PKT_FATE_FW_DROP_OTHER;
            case WifiDebugRxPacketFate.DRV_QUEUED:
                return WifiLoggerHal.RX_PKT_FATE_DRV_QUEUED;
            case WifiDebugRxPacketFate.DRV_DROP_FILTER:
                return WifiLoggerHal.RX_PKT_FATE_DRV_DROP_FILTER;
            case WifiDebugRxPacketFate.DRV_DROP_INVALID:
                return WifiLoggerHal.RX_PKT_FATE_DRV_DROP_INVALID;
            case WifiDebugRxPacketFate.DRV_DROP_NOBUFS:
                return WifiLoggerHal.RX_PKT_FATE_DRV_DROP_NOBUFS;
            case WifiDebugRxPacketFate.DRV_DROP_OTHER:
                return WifiLoggerHal.RX_PKT_FATE_DRV_DROP_OTHER;
            default:
                throw new IllegalArgumentException("bad " + type);
        }
    }

    private static byte halToFrameworkTxPktFate(int type) {
        switch (type) {
            case WifiDebugTxPacketFate.ACKED:
                return WifiLoggerHal.TX_PKT_FATE_ACKED;
            case WifiDebugTxPacketFate.SENT:
                return WifiLoggerHal.TX_PKT_FATE_SENT;
            case WifiDebugTxPacketFate.FW_QUEUED:
                return WifiLoggerHal.TX_PKT_FATE_FW_QUEUED;
            case WifiDebugTxPacketFate.FW_DROP_INVALID:
                return WifiLoggerHal.TX_PKT_FATE_FW_DROP_INVALID;
            case WifiDebugTxPacketFate.FW_DROP_NOBUFS:
                return WifiLoggerHal.TX_PKT_FATE_FW_DROP_NOBUFS;
            case WifiDebugTxPacketFate.FW_DROP_OTHER:
                return WifiLoggerHal.TX_PKT_FATE_FW_DROP_OTHER;
            case WifiDebugTxPacketFate.DRV_QUEUED:
                return WifiLoggerHal.TX_PKT_FATE_DRV_QUEUED;
            case WifiDebugTxPacketFate.DRV_DROP_INVALID:
                return WifiLoggerHal.TX_PKT_FATE_DRV_DROP_INVALID;
            case WifiDebugTxPacketFate.DRV_DROP_NOBUFS:
                return WifiLoggerHal.TX_PKT_FATE_DRV_DROP_NOBUFS;
            case WifiDebugTxPacketFate.DRV_DROP_OTHER:
                return WifiLoggerHal.TX_PKT_FATE_DRV_DROP_OTHER;
            default:
                throw new IllegalArgumentException("bad " + type);
        }
    }

    private static boolean hasCapability(long capabilities, long desiredCapability) {
        return (capabilities & desiredCapability) != 0;
    }

    @VisibleForTesting
    long halToFrameworkStaIfaceCapability(int caps) {
        long features = 0;
        if (hasCapability(caps,
                android.hardware.wifi.V1_0.IWifiStaIface.StaIfaceCapabilityMask.HOTSPOT)) {
            features |= WifiManager.WIFI_FEATURE_PASSPOINT;
        }
        if (hasCapability(caps,
                android.hardware.wifi.V1_0.IWifiStaIface.StaIfaceCapabilityMask.BACKGROUND_SCAN)) {
            features |= WifiManager.WIFI_FEATURE_SCANNER;
        }
        if (hasCapability(caps,
                android.hardware.wifi.V1_0.IWifiStaIface.StaIfaceCapabilityMask.PNO)) {
            features |= WifiManager.WIFI_FEATURE_PNO;
        }
        if (hasCapability(caps,
                android.hardware.wifi.V1_0.IWifiStaIface.StaIfaceCapabilityMask.TDLS)) {
            features |= WifiManager.WIFI_FEATURE_TDLS;
        }
        if (hasCapability(caps,
                android.hardware.wifi.V1_0.IWifiStaIface.StaIfaceCapabilityMask.TDLS_OFFCHANNEL)) {
            features |= WifiManager.WIFI_FEATURE_TDLS_OFFCHANNEL;
        }
        if (hasCapability(caps,
                android.hardware.wifi.V1_0.IWifiStaIface.StaIfaceCapabilityMask.LINK_LAYER_STATS)) {
            features |= WifiManager.WIFI_FEATURE_LINK_LAYER_STATS;
        }
        if (hasCapability(caps,
                android.hardware.wifi.V1_0.IWifiStaIface.StaIfaceCapabilityMask.RSSI_MONITOR)) {
            features |= WifiManager.WIFI_FEATURE_RSSI_MONITOR;
        }
        if (hasCapability(caps,
                android.hardware.wifi.V1_0.IWifiStaIface.StaIfaceCapabilityMask.KEEP_ALIVE)) {
            features |= WifiManager.WIFI_FEATURE_MKEEP_ALIVE;
        }
        if (hasCapability(caps,
                android.hardware.wifi.V1_0.IWifiStaIface.StaIfaceCapabilityMask.ND_OFFLOAD)) {
            features |= WifiManager.WIFI_FEATURE_CONFIG_NDO;
        }
        if (hasCapability(caps,
                android.hardware.wifi.V1_0.IWifiStaIface.StaIfaceCapabilityMask.CONTROL_ROAMING)) {
            features |= WifiManager.WIFI_FEATURE_CONTROL_ROAMING;
        }
        if (hasCapability(caps,
                android.hardware.wifi.V1_0.IWifiStaIface.StaIfaceCapabilityMask
                        .PROBE_IE_WHITELIST)) {
            features |= WifiManager.WIFI_FEATURE_IE_WHITELIST;
        }
        if (hasCapability(caps,
                android.hardware.wifi.V1_0.IWifiStaIface.StaIfaceCapabilityMask.SCAN_RAND)) {
            features |= WifiManager.WIFI_FEATURE_SCAN_RAND;
        }
        return features;
    }

    @VisibleForTesting
    WifiLinkLayerStats frameworkFromHalLinkLayerStats(StaLinkLayerStats stats) {
        if (stats == null) return null;
        WifiLinkLayerStats out = new WifiLinkLayerStats();
        setIfaceStats(out, stats.iface);
        setRadioStats(out, stats.radios);
        out.timeStampInMs = stats.timeStampInMs;
        out.version = WifiLinkLayerStats.V1_0;
        return out;
    }

    /**
     * Makes the framework version of link layer stats from the hal version.
     */
    @VisibleForTesting
    WifiLinkLayerStats frameworkFromHalLinkLayerStats_1_3(
            android.hardware.wifi.V1_3.StaLinkLayerStats stats) {
        if (stats == null) return null;
        WifiLinkLayerStats out = new WifiLinkLayerStats();
        setIfaceStats(out, stats.iface);
        setRadioStats_1_3(out, stats.radios);
        out.timeStampInMs = stats.timeStampInMs;
        out.version = WifiLinkLayerStats.V1_3;
        return out;
    }

    /**
     * Makes the framework version of link layer stats from the hal version.
     */
    @VisibleForTesting
    WifiLinkLayerStats frameworkFromHalLinkLayerStats_1_5(
            android.hardware.wifi.V1_5.StaLinkLayerStats stats) {
        if (stats == null) return null;
        WifiLinkLayerStats out = new WifiLinkLayerStats();
        setIfaceStats_1_5(out, stats.iface);
        setRadioStats_1_5(out, stats.radios);
        out.timeStampInMs = stats.timeStampInMs;
        out.version = WifiLinkLayerStats.V1_5;
        return out;
    }

    /**
     * Makes the framework version of link layer stats from the hal version.
     */
    @VisibleForTesting
    WifiLinkLayerStats frameworkFromHalLinkLayerStats_1_6(
            android.hardware.wifi.V1_6.StaLinkLayerStats stats) {
        if (stats == null) return null;
        WifiLinkLayerStats out = new WifiLinkLayerStats();
        setIfaceStats_1_6(out, stats.iface);
        setRadioStats_1_6(out, stats.radios);
        out.timeStampInMs = stats.timeStampInMs;
        out.version = WifiLinkLayerStats.V1_5;
        return out;
    }

    private static void setIfaceStats(WifiLinkLayerStats stats, StaLinkLayerIfaceStats iface) {
        if (iface == null) return;
        stats.links = new WifiLinkLayerStats.LinkSpecificStats[NUM_OF_LINKS];
        stats.links[DEFAULT_LINK] = new WifiLinkLayerStats.LinkSpecificStats();
        stats.links[DEFAULT_LINK].link_id = 0;
        stats.links[DEFAULT_LINK].state = LINK_STATE_UNKNOWN;
        stats.links[DEFAULT_LINK].beacon_rx = iface.beaconRx;
        /* HIDL is using legacy single link layer stats. */
        stats.links[DEFAULT_LINK].radio_id = 0;
        stats.links[DEFAULT_LINK].frequencyMhz = 0;
        stats.links[DEFAULT_LINK].rssi_mgmt = iface.avgRssiMgmt;
        // Statistics are broken out by Wireless Multimedia Extensions categories
        // WME Best Effort Access Category
        stats.links[DEFAULT_LINK].rxmpdu_be = iface.wmeBePktStats.rxMpdu;
        stats.links[DEFAULT_LINK].txmpdu_be = iface.wmeBePktStats.txMpdu;
        stats.links[DEFAULT_LINK].lostmpdu_be = iface.wmeBePktStats.lostMpdu;
        stats.links[DEFAULT_LINK].retries_be = iface.wmeBePktStats.retries;
        // WME Background Access Category
        stats.links[DEFAULT_LINK].rxmpdu_bk = iface.wmeBkPktStats.rxMpdu;
        stats.links[DEFAULT_LINK].txmpdu_bk = iface.wmeBkPktStats.txMpdu;
        stats.links[DEFAULT_LINK].lostmpdu_bk = iface.wmeBkPktStats.lostMpdu;
        stats.links[DEFAULT_LINK].retries_bk = iface.wmeBkPktStats.retries;
        // WME Video Access Category
        stats.links[DEFAULT_LINK].rxmpdu_vi = iface.wmeViPktStats.rxMpdu;
        stats.links[DEFAULT_LINK].txmpdu_vi = iface.wmeViPktStats.txMpdu;
        stats.links[DEFAULT_LINK].lostmpdu_vi = iface.wmeViPktStats.lostMpdu;
        stats.links[DEFAULT_LINK].retries_vi = iface.wmeViPktStats.retries;
        // WME Voice Access Category
        stats.links[DEFAULT_LINK].rxmpdu_vo = iface.wmeVoPktStats.rxMpdu;
        stats.links[DEFAULT_LINK].txmpdu_vo = iface.wmeVoPktStats.txMpdu;
        stats.links[DEFAULT_LINK].lostmpdu_vo = iface.wmeVoPktStats.lostMpdu;
        stats.links[DEFAULT_LINK].retries_vo = iface.wmeVoPktStats.retries;
    }

    private static void setIfaceStats_1_5(WifiLinkLayerStats stats,
            android.hardware.wifi.V1_5.StaLinkLayerIfaceStats iface) {
        if (iface == null) return;
        setIfaceStats(stats, iface.V1_0);
        stats.links[DEFAULT_LINK].timeSliceDutyCycleInPercent = iface.timeSliceDutyCycleInPercent;
        // WME Best Effort Access Category
        stats.links[DEFAULT_LINK].contentionTimeMinBeInUsec =
                iface.wmeBeContentionTimeStats.contentionTimeMinInUsec;
        stats.links[DEFAULT_LINK].contentionTimeMaxBeInUsec =
                iface.wmeBeContentionTimeStats.contentionTimeMaxInUsec;
        stats.links[DEFAULT_LINK].contentionTimeAvgBeInUsec =
                iface.wmeBeContentionTimeStats.contentionTimeAvgInUsec;
        stats.links[DEFAULT_LINK].contentionNumSamplesBe =
                iface.wmeBeContentionTimeStats.contentionNumSamples;
        // WME Background Access Category
        stats.links[DEFAULT_LINK].contentionTimeMinBkInUsec =
                iface.wmeBkContentionTimeStats.contentionTimeMinInUsec;
        stats.links[DEFAULT_LINK].contentionTimeMaxBkInUsec =
                iface.wmeBkContentionTimeStats.contentionTimeMaxInUsec;
        stats.links[DEFAULT_LINK].contentionTimeAvgBkInUsec =
                iface.wmeBkContentionTimeStats.contentionTimeAvgInUsec;
        stats.links[DEFAULT_LINK].contentionNumSamplesBk =
                iface.wmeBkContentionTimeStats.contentionNumSamples;
        // WME Video Access Category
        stats.links[DEFAULT_LINK].contentionTimeMinViInUsec =
                iface.wmeViContentionTimeStats.contentionTimeMinInUsec;
        stats.links[DEFAULT_LINK].contentionTimeMaxViInUsec =
                iface.wmeViContentionTimeStats.contentionTimeMaxInUsec;
        stats.links[DEFAULT_LINK].contentionTimeAvgViInUsec =
                iface.wmeViContentionTimeStats.contentionTimeAvgInUsec;
        stats.links[DEFAULT_LINK].contentionNumSamplesVi =
                iface.wmeViContentionTimeStats.contentionNumSamples;
        // WME Voice Access Category
        stats.links[DEFAULT_LINK].contentionTimeMinVoInUsec =
                iface.wmeVoContentionTimeStats.contentionTimeMinInUsec;
        stats.links[DEFAULT_LINK].contentionTimeMaxVoInUsec =
                iface.wmeVoContentionTimeStats.contentionTimeMaxInUsec;
        stats.links[DEFAULT_LINK].contentionTimeAvgVoInUsec =
                iface.wmeVoContentionTimeStats.contentionTimeAvgInUsec;
        stats.links[DEFAULT_LINK].contentionNumSamplesVo =
                iface.wmeVoContentionTimeStats.contentionNumSamples;
        // Peer information statistics
        stats.links[DEFAULT_LINK].peerInfo = new WifiLinkLayerStats.PeerInfo[iface.peers.size()];
        for (int i = 0; i < stats.links[DEFAULT_LINK].peerInfo.length; i++) {
            WifiLinkLayerStats.PeerInfo peer = new WifiLinkLayerStats.PeerInfo();
            android.hardware.wifi.V1_5.StaPeerInfo staPeerInfo = iface.peers.get(i);
            peer.staCount = staPeerInfo.staCount;
            peer.chanUtil = staPeerInfo.chanUtil;
            WifiLinkLayerStats.RateStat[] rateStats =
                    new WifiLinkLayerStats.RateStat[staPeerInfo.rateStats.size()];
            for (int j = 0; j < staPeerInfo.rateStats.size(); j++) {
                rateStats[j] = new WifiLinkLayerStats.RateStat();
                android.hardware.wifi.V1_5.StaRateStat staRateStat = staPeerInfo.rateStats.get(j);
                rateStats[j].preamble = staRateStat.rateInfo.preamble;
                rateStats[j].nss = staRateStat.rateInfo.nss;
                rateStats[j].bw = staRateStat.rateInfo.bw;
                rateStats[j].rateMcsIdx = staRateStat.rateInfo.rateMcsIdx;
                rateStats[j].bitRateInKbps = staRateStat.rateInfo.bitRateInKbps;
                rateStats[j].txMpdu = staRateStat.txMpdu;
                rateStats[j].rxMpdu = staRateStat.rxMpdu;
                rateStats[j].mpduLost = staRateStat.mpduLost;
                rateStats[j].retries = staRateStat.retries;
            }
            peer.rateStats = rateStats;
            stats.links[DEFAULT_LINK].peerInfo[i] = peer;
        }
    }

    private static void setIfaceStats_1_6(WifiLinkLayerStats stats,
            android.hardware.wifi.V1_6.StaLinkLayerIfaceStats iface) {
        if (iface == null) return;
        setIfaceStats(stats, iface.V1_0);
        stats.links[DEFAULT_LINK].timeSliceDutyCycleInPercent = iface.timeSliceDutyCycleInPercent;
        // WME Best Effort Access Category
        stats.links[DEFAULT_LINK].contentionTimeMinBeInUsec =
                iface.wmeBeContentionTimeStats.contentionTimeMinInUsec;
        stats.links[DEFAULT_LINK].contentionTimeMaxBeInUsec =
                iface.wmeBeContentionTimeStats.contentionTimeMaxInUsec;
        stats.links[DEFAULT_LINK].contentionTimeAvgBeInUsec =
                iface.wmeBeContentionTimeStats.contentionTimeAvgInUsec;
        stats.links[DEFAULT_LINK].contentionNumSamplesBe =
                iface.wmeBeContentionTimeStats.contentionNumSamples;
        // WME Background Access Category
        stats.links[DEFAULT_LINK].contentionTimeMinBkInUsec =
                iface.wmeBkContentionTimeStats.contentionTimeMinInUsec;
        stats.links[DEFAULT_LINK].contentionTimeMaxBkInUsec =
                iface.wmeBkContentionTimeStats.contentionTimeMaxInUsec;
        stats.links[DEFAULT_LINK].contentionTimeAvgBkInUsec =
                iface.wmeBkContentionTimeStats.contentionTimeAvgInUsec;
        stats.links[DEFAULT_LINK].contentionNumSamplesBk =
                iface.wmeBkContentionTimeStats.contentionNumSamples;
        // WME Video Access Category
        stats.links[DEFAULT_LINK].contentionTimeMinViInUsec =
                iface.wmeViContentionTimeStats.contentionTimeMinInUsec;
        stats.links[DEFAULT_LINK].contentionTimeMaxViInUsec =
                iface.wmeViContentionTimeStats.contentionTimeMaxInUsec;
        stats.links[DEFAULT_LINK].contentionTimeAvgViInUsec =
                iface.wmeViContentionTimeStats.contentionTimeAvgInUsec;
        stats.links[DEFAULT_LINK].contentionNumSamplesVi =
                iface.wmeViContentionTimeStats.contentionNumSamples;
        // WME Voice Access Category
        stats.links[DEFAULT_LINK].contentionTimeMinVoInUsec =
                iface.wmeVoContentionTimeStats.contentionTimeMinInUsec;
        stats.links[DEFAULT_LINK].contentionTimeMaxVoInUsec =
                iface.wmeVoContentionTimeStats.contentionTimeMaxInUsec;
        stats.links[DEFAULT_LINK].contentionTimeAvgVoInUsec =
                iface.wmeVoContentionTimeStats.contentionTimeAvgInUsec;
        stats.links[DEFAULT_LINK].contentionNumSamplesVo =
                iface.wmeVoContentionTimeStats.contentionNumSamples;
        // Peer information statistics
        stats.links[DEFAULT_LINK].peerInfo = new WifiLinkLayerStats.PeerInfo[iface.peers.size()];
        for (int i = 0; i < stats.links[DEFAULT_LINK].peerInfo.length; i++) {
            WifiLinkLayerStats.PeerInfo peer = new WifiLinkLayerStats.PeerInfo();
            android.hardware.wifi.V1_6.StaPeerInfo staPeerInfo = iface.peers.get(i);
            peer.staCount = staPeerInfo.staCount;
            peer.chanUtil = staPeerInfo.chanUtil;
            WifiLinkLayerStats.RateStat[] rateStats =
                    new WifiLinkLayerStats.RateStat[staPeerInfo.rateStats.size()];
            for (int j = 0; j < staPeerInfo.rateStats.size(); j++) {
                rateStats[j] = new WifiLinkLayerStats.RateStat();
                android.hardware.wifi.V1_6.StaRateStat staRateStat = staPeerInfo.rateStats.get(j);
                rateStats[j].preamble = staRateStat.rateInfo.preamble;
                rateStats[j].nss = staRateStat.rateInfo.nss;
                rateStats[j].bw = staRateStat.rateInfo.bw;
                rateStats[j].rateMcsIdx = staRateStat.rateInfo.rateMcsIdx;
                rateStats[j].bitRateInKbps = staRateStat.rateInfo.bitRateInKbps;
                rateStats[j].txMpdu = staRateStat.txMpdu;
                rateStats[j].rxMpdu = staRateStat.rxMpdu;
                rateStats[j].mpduLost = staRateStat.mpduLost;
                rateStats[j].retries = staRateStat.retries;
            }
            peer.rateStats = rateStats;
            stats.links[DEFAULT_LINK].peerInfo[i] = peer;
        }
    }

    private static void setRadioStats(WifiLinkLayerStats stats,
            List<StaLinkLayerRadioStats> radios) {
        if (radios == null) return;
        // Do not coalesce this info for multi radio devices with older HALs.
        if (radios.size() > 0) {
            StaLinkLayerRadioStats radioStats = radios.get(0);
            stats.on_time = radioStats.onTimeInMs;
            stats.tx_time = radioStats.txTimeInMs;
            stats.tx_time_per_level = new int[radioStats.txTimeInMsPerLevel.size()];
            for (int i = 0; i < stats.tx_time_per_level.length; i++) {
                stats.tx_time_per_level[i] = radioStats.txTimeInMsPerLevel.get(i);
            }
            stats.rx_time = radioStats.rxTimeInMs;
            stats.on_time_scan = radioStats.onTimeInMsForScan;
            stats.numRadios = 1;
        }
    }

    private void setRadioStats_1_3(WifiLinkLayerStats stats,
            List<android.hardware.wifi.V1_3.StaLinkLayerRadioStats> radios) {
        if (radios == null) return;
        int radioIndex = 0;
        for (android.hardware.wifi.V1_3.StaLinkLayerRadioStats radioStats : radios) {
            aggregateFrameworkRadioStatsFromHidl_1_3(radioIndex, stats, radioStats);
            radioIndex++;
        }
    }

    private void setRadioStats_1_5(WifiLinkLayerStats stats,
            List<android.hardware.wifi.V1_5.StaLinkLayerRadioStats> radios) {
        if (radios == null) return;
        int radioIndex = 0;
        stats.radioStats = new WifiLinkLayerStats.RadioStat[radios.size()];
        for (android.hardware.wifi.V1_5.StaLinkLayerRadioStats radioStats : radios) {
            WifiLinkLayerStats.RadioStat radio = new WifiLinkLayerStats.RadioStat();
            setFrameworkPerRadioStatsFromHidl_1_3(radioStats.radioId, radio, radioStats.V1_3);
            stats.radioStats[radioIndex] = radio;
            aggregateFrameworkRadioStatsFromHidl_1_3(radioIndex, stats, radioStats.V1_3);
            radioIndex++;
        }
    }

    private void setRadioStats_1_6(WifiLinkLayerStats stats,
            List<android.hardware.wifi.V1_6.StaLinkLayerRadioStats> radios) {
        if (radios == null) return;
        int radioIndex = 0;
        stats.radioStats = new WifiLinkLayerStats.RadioStat[radios.size()];
        for (android.hardware.wifi.V1_6.StaLinkLayerRadioStats radioStats : radios) {
            WifiLinkLayerStats.RadioStat radio = new WifiLinkLayerStats.RadioStat();
            setFrameworkPerRadioStatsFromHidl_1_6(radio, radioStats);
            stats.radioStats[radioIndex] = radio;
            aggregateFrameworkRadioStatsFromHidl_1_6(radioIndex, stats, radioStats);
            radioIndex++;
        }
    }

    /**
     * Set individual radio stats from the hal radio stats for V1_3
     */
    private void setFrameworkPerRadioStatsFromHidl_1_3(int radioId,
            WifiLinkLayerStats.RadioStat radio,
            android.hardware.wifi.V1_3.StaLinkLayerRadioStats hidlRadioStats) {
        radio.radio_id = radioId;
        radio.on_time = hidlRadioStats.V1_0.onTimeInMs;
        radio.tx_time = hidlRadioStats.V1_0.txTimeInMs;
        radio.rx_time = hidlRadioStats.V1_0.rxTimeInMs;
        radio.on_time_scan = hidlRadioStats.V1_0.onTimeInMsForScan;
        radio.on_time_nan_scan = hidlRadioStats.onTimeInMsForNanScan;
        radio.on_time_background_scan = hidlRadioStats.onTimeInMsForBgScan;
        radio.on_time_roam_scan = hidlRadioStats.onTimeInMsForRoamScan;
        radio.on_time_pno_scan = hidlRadioStats.onTimeInMsForPnoScan;
        radio.on_time_hs20_scan = hidlRadioStats.onTimeInMsForHs20Scan;
        /* Copy list of channel stats */
        for (android.hardware.wifi.V1_3.WifiChannelStats channelStats
                : hidlRadioStats.channelStats) {
            WifiLinkLayerStats.ChannelStats channelStatsEntry =
                    new WifiLinkLayerStats.ChannelStats();
            channelStatsEntry.frequency = channelStats.channel.centerFreq;
            channelStatsEntry.radioOnTimeMs = channelStats.onTimeInMs;
            channelStatsEntry.ccaBusyTimeMs = channelStats.ccaBusyTimeInMs;
            radio.channelStatsMap.put(channelStats.channel.centerFreq, channelStatsEntry);
        }
    }

    /**
     * Set individual radio stats from the hal radio stats for V1_6
     */
    private void setFrameworkPerRadioStatsFromHidl_1_6(WifiLinkLayerStats.RadioStat radio,
            android.hardware.wifi.V1_6.StaLinkLayerRadioStats hidlRadioStats) {
        radio.radio_id = hidlRadioStats.radioId;
        radio.on_time = hidlRadioStats.V1_0.onTimeInMs;
        radio.tx_time = hidlRadioStats.V1_0.txTimeInMs;
        radio.rx_time = hidlRadioStats.V1_0.rxTimeInMs;
        radio.on_time_scan = hidlRadioStats.V1_0.onTimeInMsForScan;
        radio.on_time_nan_scan = hidlRadioStats.onTimeInMsForNanScan;
        radio.on_time_background_scan = hidlRadioStats.onTimeInMsForBgScan;
        radio.on_time_roam_scan = hidlRadioStats.onTimeInMsForRoamScan;
        radio.on_time_pno_scan = hidlRadioStats.onTimeInMsForPnoScan;
        radio.on_time_hs20_scan = hidlRadioStats.onTimeInMsForHs20Scan;
        /* Copy list of channel stats */
        for (android.hardware.wifi.V1_6.WifiChannelStats channelStats
                : hidlRadioStats.channelStats) {
            WifiLinkLayerStats.ChannelStats channelStatsEntry =
                    new WifiLinkLayerStats.ChannelStats();
            channelStatsEntry.frequency = channelStats.channel.centerFreq;
            channelStatsEntry.radioOnTimeMs = channelStats.onTimeInMs;
            channelStatsEntry.ccaBusyTimeMs = channelStats.ccaBusyTimeInMs;
            radio.channelStatsMap.put(channelStats.channel.centerFreq, channelStatsEntry);
        }
    }

    /**
     * If config_wifiLinkLayerAllRadiosStatsAggregationEnabled is set to true, aggregate
     * the radio stats from all the radios else process the stats from Radio 0 only.
     * This method is for V1_3
     */
    private void aggregateFrameworkRadioStatsFromHidl_1_3(int radioIndex,
            WifiLinkLayerStats stats,
            android.hardware.wifi.V1_3.StaLinkLayerRadioStats hidlRadioStats) {
        if (!mContext.getResources()
                .getBoolean(R.bool.config_wifiLinkLayerAllRadiosStatsAggregationEnabled)
                && radioIndex > 0) {
            return;
        }
        // Aggregate the radio stats from all the radios
        stats.on_time += hidlRadioStats.V1_0.onTimeInMs;
        stats.tx_time += hidlRadioStats.V1_0.txTimeInMs;
        // Aggregate tx_time_per_level based on the assumption that the length of
        // txTimeInMsPerLevel is the same across all radios. So txTimeInMsPerLevel on other
        // radios at array indices greater than the length of first radio will be dropped.
        if (stats.tx_time_per_level == null) {
            stats.tx_time_per_level = new int[hidlRadioStats.V1_0.txTimeInMsPerLevel.size()];
        }
        for (int i = 0; i < hidlRadioStats.V1_0.txTimeInMsPerLevel.size()
                && i < stats.tx_time_per_level.length; i++) {
            stats.tx_time_per_level[i] += hidlRadioStats.V1_0.txTimeInMsPerLevel.get(i);
        }
        stats.rx_time += hidlRadioStats.V1_0.rxTimeInMs;
        stats.on_time_scan += hidlRadioStats.V1_0.onTimeInMsForScan;
        stats.on_time_nan_scan += hidlRadioStats.onTimeInMsForNanScan;
        stats.on_time_background_scan += hidlRadioStats.onTimeInMsForBgScan;
        stats.on_time_roam_scan += hidlRadioStats.onTimeInMsForRoamScan;
        stats.on_time_pno_scan += hidlRadioStats.onTimeInMsForPnoScan;
        stats.on_time_hs20_scan += hidlRadioStats.onTimeInMsForHs20Scan;
        /* Copy list of channel stats */
        for (android.hardware.wifi.V1_3.WifiChannelStats channelStats
                : hidlRadioStats.channelStats) {
            WifiLinkLayerStats.ChannelStats channelStatsEntry =
                    stats.channelStatsMap.get(channelStats.channel.centerFreq);
            if (channelStatsEntry == null) {
                channelStatsEntry = new WifiLinkLayerStats.ChannelStats();
                channelStatsEntry.frequency = channelStats.channel.centerFreq;
                stats.channelStatsMap.put(channelStats.channel.centerFreq, channelStatsEntry);
            }
            channelStatsEntry.radioOnTimeMs += channelStats.onTimeInMs;
            channelStatsEntry.ccaBusyTimeMs += channelStats.ccaBusyTimeInMs;
        }
        stats.numRadios++;
    }

    /**
     * If config_wifiLinkLayerAllRadiosStatsAggregationEnabled is set to true, aggregate
     * the radio stats from all the radios else process the stats from Radio 0 only.
     * This method is for V1_6
     */
    private void aggregateFrameworkRadioStatsFromHidl_1_6(int radioIndex,
            WifiLinkLayerStats stats,
            android.hardware.wifi.V1_6.StaLinkLayerRadioStats hidlRadioStats) {
        if (!mContext.getResources()
                .getBoolean(R.bool.config_wifiLinkLayerAllRadiosStatsAggregationEnabled)
                && radioIndex > 0) {
            return;
        }
        // Aggregate the radio stats from all the radios
        stats.on_time += hidlRadioStats.V1_0.onTimeInMs;
        stats.tx_time += hidlRadioStats.V1_0.txTimeInMs;
        // Aggregate tx_time_per_level based on the assumption that the length of
        // txTimeInMsPerLevel is the same across all radios. So txTimeInMsPerLevel on other
        // radios at array indices greater than the length of first radio will be dropped.
        if (stats.tx_time_per_level == null) {
            stats.tx_time_per_level = new int[hidlRadioStats.V1_0.txTimeInMsPerLevel.size()];
        }
        for (int i = 0; i < hidlRadioStats.V1_0.txTimeInMsPerLevel.size()
                && i < stats.tx_time_per_level.length; i++) {
            stats.tx_time_per_level[i] += hidlRadioStats.V1_0.txTimeInMsPerLevel.get(i);
        }
        stats.rx_time += hidlRadioStats.V1_0.rxTimeInMs;
        stats.on_time_scan += hidlRadioStats.V1_0.onTimeInMsForScan;
        stats.on_time_nan_scan += hidlRadioStats.onTimeInMsForNanScan;
        stats.on_time_background_scan += hidlRadioStats.onTimeInMsForBgScan;
        stats.on_time_roam_scan += hidlRadioStats.onTimeInMsForRoamScan;
        stats.on_time_pno_scan += hidlRadioStats.onTimeInMsForPnoScan;
        stats.on_time_hs20_scan += hidlRadioStats.onTimeInMsForHs20Scan;
        /* Copy list of channel stats */
        for (android.hardware.wifi.V1_6.WifiChannelStats channelStats
                : hidlRadioStats.channelStats) {
            WifiLinkLayerStats.ChannelStats channelStatsEntry =
                    stats.channelStatsMap.get(channelStats.channel.centerFreq);
            if (channelStatsEntry == null) {
                channelStatsEntry = new WifiLinkLayerStats.ChannelStats();
                channelStatsEntry.frequency = channelStats.channel.centerFreq;
                stats.channelStatsMap.put(channelStats.channel.centerFreq, channelStatsEntry);
            }
            channelStatsEntry.radioOnTimeMs += channelStats.onTimeInMs;
            channelStatsEntry.ccaBusyTimeMs += channelStats.ccaBusyTimeInMs;
        }
        stats.numRadios++;
    }

    private static StaBackgroundScanParameters frameworkToHalBackgroundScanParams(
            WifiStaIface.StaBackgroundScanParameters frameworkParams) {
        StaBackgroundScanParameters halParams = new StaBackgroundScanParameters();
        halParams.basePeriodInMs = frameworkParams.basePeriodInMs;
        halParams.maxApPerScan = frameworkParams.maxApPerScan;
        halParams.reportThresholdPercent = frameworkParams.reportThresholdPercent;
        halParams.reportThresholdNumScans = frameworkParams.reportThresholdNumScans;
        if (frameworkParams.buckets != null) {
            for (WifiNative.BucketSettings bucket : frameworkParams.buckets) {
                halParams.buckets.add(frameworkToHalBucketParams(bucket));
            }
        }
        return halParams;
    }

    private static StaBackgroundScanBucketParameters frameworkToHalBucketParams(
            WifiNative.BucketSettings frameworkBucket) {
        StaBackgroundScanBucketParameters halBucket = new StaBackgroundScanBucketParameters();
        halBucket.bucketIdx = frameworkBucket.bucket;
        halBucket.band = frameworkToHalWifiBand(frameworkBucket.band);
        if (frameworkBucket.channels != null) {
            for (WifiNative.ChannelSettings cs : frameworkBucket.channels) {
                halBucket.frequencies.add(cs.frequency);
            }
        }
        halBucket.periodInMs = frameworkBucket.period_ms;
        halBucket.eventReportScheme = frameworkToHalReportSchemeMask(frameworkBucket.report_events);
        halBucket.exponentialMaxPeriodInMs = frameworkBucket.max_period_ms;
        // Although HAL API allows configurable base value for the truncated
        // exponential back off scan. Native API and above support only
        // truncated binary exponential back off scan.
        // Hard code value of base to 2 here.
        halBucket.exponentialBase = 2;
        halBucket.exponentialStepCount = frameworkBucket.step_count;
        return halBucket;
    }

    /**
     * Convert WifiBand from framework to HAL.
     *
     * Note: This method is only used by background scan which does not
     *       support 6GHz, hence band combinations including 6GHz are considered invalid
     *
     * @param frameworkBand one of WifiScanner.WIFI_BAND_*
     * @return A WifiBand value
     * @throws IllegalArgumentException if frameworkBand is not recognized
     */
    private static int frameworkToHalWifiBand(int frameworkBand) {
        switch (frameworkBand) {
            case WifiScanner.WIFI_BAND_UNSPECIFIED:
                return WifiBand.BAND_UNSPECIFIED;
            case WifiScanner.WIFI_BAND_24_GHZ:
                return WifiBand.BAND_24GHZ;
            case WifiScanner.WIFI_BAND_5_GHZ:
                return WifiBand.BAND_5GHZ;
            case WifiScanner.WIFI_BAND_5_GHZ_DFS_ONLY:
                return WifiBand.BAND_5GHZ_DFS;
            case WifiScanner.WIFI_BAND_5_GHZ_WITH_DFS:
                return WifiBand.BAND_5GHZ_WITH_DFS;
            case WifiScanner.WIFI_BAND_BOTH:
                return WifiBand.BAND_24GHZ_5GHZ;
            case WifiScanner.WIFI_BAND_BOTH_WITH_DFS:
                return WifiBand.BAND_24GHZ_5GHZ_WITH_DFS;
            case WifiScanner.WIFI_BAND_6_GHZ:
                return WifiBand.BAND_6GHZ;
            case WifiScanner.WIFI_BAND_24_5_6_GHZ:
                return WifiBand.BAND_24GHZ_5GHZ_6GHZ;
            case WifiScanner.WIFI_BAND_24_5_WITH_DFS_6_GHZ:
                return WifiBand.BAND_24GHZ_5GHZ_WITH_DFS_6GHZ;
            case WifiScanner.WIFI_BAND_60_GHZ:
                return WifiBand.BAND_60GHZ;
            case WifiScanner.WIFI_BAND_24_5_6_60_GHZ:
                return WifiBand.BAND_24GHZ_5GHZ_6GHZ_60GHZ;
            case WifiScanner.WIFI_BAND_24_5_WITH_DFS_6_60_GHZ:
                return WifiBand.BAND_24GHZ_5GHZ_WITH_DFS_6GHZ_60GHZ;
            case WifiScanner.WIFI_BAND_24_GHZ_WITH_5GHZ_DFS:
            default:
                throw new IllegalArgumentException("bad band " + frameworkBand);
        }
    }

    /**
     * Convert the report scheme mask from framework to hal.
     *
     * @param reportUnderscoreEvents is logical OR of WifiScanner.REPORT_EVENT_* values
     * @return Corresponding StaBackgroundScanBucketEventReportSchemeMask value
     * @throws IllegalArgumentException if a mask bit is not recognized
     */
    private static int frameworkToHalReportSchemeMask(int reportUnderscoreEvents) {
        int ans = 0;
        BitMask in = new BitMask(reportUnderscoreEvents);
        if (in.testAndClear(WifiScanner.REPORT_EVENT_AFTER_EACH_SCAN)) {
            ans |= StaBackgroundScanBucketEventReportSchemeMask.EACH_SCAN;
        }
        if (in.testAndClear(WifiScanner.REPORT_EVENT_FULL_SCAN_RESULT)) {
            ans |= StaBackgroundScanBucketEventReportSchemeMask.FULL_RESULTS;
        }
        if (in.testAndClear(WifiScanner.REPORT_EVENT_NO_BATCH)) {
            ans |= StaBackgroundScanBucketEventReportSchemeMask.NO_BATCH;
        }
        if (in.value != 0) throw new IllegalArgumentException("bad " + reportUnderscoreEvents);
        return ans;
    }

    // Only sets the fields of ScanResult used by Gscan clients.
    private ScanResult hidlToFrameworkScanResult(StaScanResult scanResult) {
        if (scanResult == null) return null;
        WifiSsid originalSsid = WifiSsid.fromBytes(
                NativeUtil.byteArrayFromArrayList(scanResult.ssid));
        MacAddress bssid;
        try {
            bssid = MacAddress.fromString(NativeUtil.macAddressFromByteArray(scanResult.bssid));
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Failed to get BSSID of scan result: " + e);
            return null;
        }
        ScanResult frameworkScanResult = new ScanResult();
        frameworkScanResult.setWifiSsid(mSsidTranslator.getTranslatedSsidAndRecordBssidCharset(
                originalSsid, bssid));
        frameworkScanResult.BSSID = bssid.toString();
        frameworkScanResult.level = scanResult.rssi;
        frameworkScanResult.frequency = scanResult.frequency;
        frameworkScanResult.timestamp = scanResult.timeStampInUs;
        return frameworkScanResult;
    }

    private ScanResult[] hidlToFrameworkScanResults(ArrayList<StaScanResult> scanResults) {
        if (scanResults == null || scanResults.isEmpty()) return new ScanResult[0];
        ScanResult[] frameworkScanResults = new ScanResult[scanResults.size()];
        int i = 0;
        for (StaScanResult scanResult : scanResults) {
            ScanResult frameworkScanResult = hidlToFrameworkScanResult(scanResult);
            if (frameworkScanResult == null) {
                Log.e(TAG, "hidlToFrameworkScanResults: unable to convert hidl to framework "
                        + "scan result!");
                continue;
            }
            frameworkScanResults[i++] = frameworkScanResult;
        }
        return frameworkScanResults;
    }

    private static int hidlToFrameworkScanDataFlags(int flag) {
        if (flag == StaScanDataFlagMask.INTERRUPTED) {
            return 1;
        } else {
            return 0;
        }
    }

    private WifiScanner.ScanData[] hidlToFrameworkScanDatas(
            int cmdId, ArrayList<StaScanData> scanDatas) {
        if (scanDatas == null || scanDatas.isEmpty()) return new WifiScanner.ScanData[0];
        WifiScanner.ScanData[] frameworkScanDatas = new WifiScanner.ScanData[scanDatas.size()];
        int i = 0;
        for (StaScanData scanData : scanDatas) {
            int flags = hidlToFrameworkScanDataFlags(scanData.flags);
            ScanResult[] frameworkScanResults = hidlToFrameworkScanResults(scanData.results);
            frameworkScanDatas[i++] =
                    new WifiScanner.ScanData(cmdId, flags, scanData.bucketsScanned,
                            WifiScanner.WIFI_BAND_UNSPECIFIED, frameworkScanResults);
        }
        return frameworkScanDatas;
    }

    protected android.hardware.wifi.V1_2.IWifiStaIface getWifiStaIfaceV1_2Mockable() {
        return android.hardware.wifi.V1_2.IWifiStaIface.castFrom(mWifiStaIface);
    }

    protected android.hardware.wifi.V1_3.IWifiStaIface getWifiStaIfaceV1_3Mockable() {
        return android.hardware.wifi.V1_3.IWifiStaIface.castFrom(mWifiStaIface);
    }

    protected android.hardware.wifi.V1_5.IWifiStaIface getWifiStaIfaceV1_5Mockable() {
        return android.hardware.wifi.V1_5.IWifiStaIface.castFrom(mWifiStaIface);
    }

    protected android.hardware.wifi.V1_6.IWifiStaIface getWifiStaIfaceV1_6Mockable() {
        return android.hardware.wifi.V1_6.IWifiStaIface.castFrom(mWifiStaIface);
    }

    private boolean isOk(WifiStatus status, String methodStr) {
        if (status.code == WifiStatusCode.SUCCESS) return true;
        Log.e(TAG, methodStr + " failed with status: " + status);
        return false;
    }

    private void handleRemoteException(RemoteException e, String methodStr) {
        Log.e(TAG, methodStr + " failed with remote exception: " + e);
        mWifiStaIface = null;
    }

    private void handleException(Exception e, String methodStr) {
        Log.e(TAG, methodStr + " failed with exception: " + e);
    }

    private <T> T validateAndCall(String methodStr, T defaultVal, @NonNull Supplier<T> supplier) {
        if (mWifiStaIface == null) {
            Log.wtf(TAG, "Cannot call " + methodStr + " because mWifiStaIface is null");
            return defaultVal;
        }
        return supplier.get();
    }


    // HAL Callback
    private class StaIfaceEventCallback extends IWifiStaIfaceEventCallback.Stub {
        @Override
        public void onBackgroundScanFailure(int cmdId) {
            if (mFrameworkCallback == null) return;
            mFrameworkCallback.onBackgroundScanFailure(cmdId);
        }

        @Override
        public void onBackgroundFullScanResult(int cmdId, int bucketsScanned,
                StaScanResult result) {
            if (mFrameworkCallback == null) return;
            ScanResult frameworkScanResult = hidlToFrameworkScanResult(result);
            if (frameworkScanResult == null) {
                Log.e(TAG, "Unable to convert scan result from HAL to framework");
                return;
            }
            mFrameworkCallback.onBackgroundFullScanResult(cmdId, bucketsScanned,
                    frameworkScanResult);
        }

        @Override
        public void onBackgroundScanResults(int cmdId, ArrayList<StaScanData> scanDatas) {
            if (mFrameworkCallback == null) return;
            WifiScanner.ScanData[] frameworkScanDatas = hidlToFrameworkScanDatas(cmdId, scanDatas);
            mFrameworkCallback.onBackgroundScanResults(cmdId, frameworkScanDatas);
        }

        @Override
        public void onRssiThresholdBreached(int cmdId, byte[/* 6 */] currBssid, int currRssi) {
            if (mFrameworkCallback == null) return;
            mFrameworkCallback.onRssiThresholdBreached(cmdId, currBssid, currRssi);
        }
    }
}
