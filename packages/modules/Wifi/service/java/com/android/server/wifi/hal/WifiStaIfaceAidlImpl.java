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

import static com.android.server.wifi.hal.WifiHalAidlImpl.isServiceVersionAtLeast;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.hardware.wifi.CachedScanData;
import android.hardware.wifi.CachedScanResult;
import android.hardware.wifi.IWifiStaIfaceEventCallback;
import android.hardware.wifi.Ssid;
import android.hardware.wifi.StaApfPacketFilterCapabilities;
import android.hardware.wifi.StaBackgroundScanBucketEventReportSchemeMask;
import android.hardware.wifi.StaBackgroundScanBucketParameters;
import android.hardware.wifi.StaBackgroundScanCapabilities;
import android.hardware.wifi.StaBackgroundScanParameters;
import android.hardware.wifi.StaLinkLayerIfaceStats;
import android.hardware.wifi.StaLinkLayerLinkStats;
import android.hardware.wifi.StaLinkLayerRadioStats;
import android.hardware.wifi.StaLinkLayerStats;
import android.hardware.wifi.StaPeerInfo;
import android.hardware.wifi.StaRateStat;
import android.hardware.wifi.StaRoamingCapabilities;
import android.hardware.wifi.StaRoamingConfig;
import android.hardware.wifi.StaRoamingState;
import android.hardware.wifi.StaScanData;
import android.hardware.wifi.StaScanDataFlagMask;
import android.hardware.wifi.StaScanResult;
import android.hardware.wifi.TwtSession;
import android.hardware.wifi.TwtSessionStats;
import android.hardware.wifi.WifiBand;
import android.hardware.wifi.WifiChannelStats;
import android.hardware.wifi.WifiDebugPacketFateFrameType;
import android.hardware.wifi.WifiDebugRxPacketFate;
import android.hardware.wifi.WifiDebugRxPacketFateReport;
import android.hardware.wifi.WifiDebugTxPacketFate;
import android.hardware.wifi.WifiDebugTxPacketFateReport;
import android.hardware.wifi.WifiRatePreamble;
import android.hardware.wifi.WifiStatusCode;
import android.net.MacAddress;
import android.net.apf.ApfCapabilities;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiAnnotations;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.RoamingMode;
import android.net.wifi.WifiScanner;
import android.net.wifi.WifiSsid;
import android.net.wifi.WifiUsabilityStatsEntry;
import android.net.wifi.twt.TwtRequest;
import android.net.wifi.twt.TwtSessionCallback;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.ServiceSpecificException;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;
import com.android.server.wifi.SsidTranslator;
import com.android.server.wifi.WifiLinkLayerStats;
import com.android.server.wifi.WifiLoggerHal;
import com.android.server.wifi.WifiNative;
import com.android.server.wifi.util.BitMask;
import com.android.server.wifi.util.HalAidlUtil;
import com.android.server.wifi.util.NativeUtil;
import com.android.wifi.resources.R;

import java.util.ArrayList;
import java.util.List;

/**
 * AIDL implementation of the IWifiStaIface interface.
 */
public class WifiStaIfaceAidlImpl implements IWifiStaIface {
    private static final String TAG = "WifiStaIfaceAidlImpl";
    private android.hardware.wifi.IWifiStaIface mWifiStaIface;
    private IWifiStaIfaceEventCallback mHalCallback;
    private WifiStaIface.Callback mFrameworkCallback;
    private final Object mLock = new Object();
    private String mIfaceName;
    private Context mContext;
    private SsidTranslator mSsidTranslator;

    public WifiStaIfaceAidlImpl(@NonNull android.hardware.wifi.IWifiStaIface staIface,
            @NonNull Context context, @NonNull SsidTranslator ssidTranslator) {
        mWifiStaIface = staIface;
        mContext = context;
        mSsidTranslator = ssidTranslator;
        mHalCallback = new StaIfaceEventCallback();
    }

    /**
     * See comments for {@link IWifiStaIface#registerFrameworkCallback(WifiStaIface.Callback)}
     */
    @Override
    public boolean registerFrameworkCallback(WifiStaIface.Callback callback) {
        final String methodStr = "registerFrameworkCallback";
        synchronized (mLock) {
            if (!checkIfaceAndLogFailure(methodStr)) return false;
            if (mFrameworkCallback != null) {
                Log.e(TAG, "Framework callback is already registered");
                return false;
            } else if (callback == null) {
                Log.e(TAG, "Cannot register a null callback");
                return false;
            }

            try {
                mWifiStaIface.registerEventCallback(mHalCallback);
                mFrameworkCallback = callback;
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
     * See comments for {@link IWifiStaIface#getName()}
     */
    @Override
    @Nullable
    public String getName() {
        final String methodStr = "getName";
        synchronized (mLock) {
            if (!checkIfaceAndLogFailure(methodStr)) return null;
            if (mIfaceName != null) return mIfaceName;
            try {
                String ifaceName = mWifiStaIface.getName();
                mIfaceName = ifaceName;
                return mIfaceName;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return null;
        }
    }

    /**
     * See comments for {@link IWifiStaIface#configureRoaming(List, List)}
     */
    @Override
    public boolean configureRoaming(List<MacAddress> bssidBlocklist,
            List<byte[]> ssidAllowlist) {
        final String methodStr = "configureRoaming";
        synchronized (mLock) {
            try {
                if (!checkIfaceAndLogFailure(methodStr)) return false;
                StaRoamingConfig config =
                        frameworkToHalStaRoamingConfig(bssidBlocklist, ssidAllowlist);
                mWifiStaIface.configureRoaming(config);
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
     * See comments for {@link IWifiStaIface#enableLinkLayerStatsCollection(boolean)}
     */
    @Override
    public boolean enableLinkLayerStatsCollection(boolean debug) {
        final String methodStr = "enableLinkLayerStatsCollection";
        synchronized (mLock) {
            try {
                if (!checkIfaceAndLogFailure(methodStr)) return false;
                mWifiStaIface.enableLinkLayerStatsCollection(debug);
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
     * See comments for {@link IWifiStaIface#enableNdOffload(boolean)}
     */
    @Override
    public boolean enableNdOffload(boolean enable) {
        final String methodStr = "enableNdOffload";
        synchronized (mLock) {
            try {
                if (!checkIfaceAndLogFailure(methodStr)) return false;
                mWifiStaIface.enableNdOffload(enable);
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
     * See comments for {@link IWifiStaIface#getApfPacketFilterCapabilities()}
     */
    @Override
    public ApfCapabilities getApfPacketFilterCapabilities() {
        final String methodStr = "getApfPacketFilterCapabilities";
        final ApfCapabilities defaultVal = new ApfCapabilities(0, 0, 0);
        synchronized (mLock) {
            try {
                if (!checkIfaceAndLogFailure(methodStr)) return defaultVal;
                StaApfPacketFilterCapabilities halCaps =
                        mWifiStaIface.getApfPacketFilterCapabilities();
                return new ApfCapabilities(
                        halCaps.version,    // apfVersionSupported
                        halCaps.maxLength,  // maximumApfProgramSize
                        android.system.OsConstants.ARPHRD_ETHER);   // apfPacketFormat
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return defaultVal;
        }
    }

    /**
     * See comments for {@link IWifiStaIface#getBackgroundScanCapabilities()}
     */
    @Override
    @Nullable
    public WifiNative.ScanCapabilities getBackgroundScanCapabilities() {
        final String methodStr = "getBackgroundScanCapabilities";
        synchronized (mLock) {
            if (!checkIfaceAndLogFailure(methodStr)) {
                return null;
            }
            try {
                StaBackgroundScanCapabilities halCaps =
                        mWifiStaIface.getBackgroundScanCapabilities();
                WifiNative.ScanCapabilities frameworkCaps = new WifiNative.ScanCapabilities();
                frameworkCaps.max_scan_cache_size = halCaps.maxCacheSize;
                frameworkCaps.max_ap_cache_per_scan = halCaps.maxApCachePerScan;
                frameworkCaps.max_scan_buckets = halCaps.maxBuckets;
                frameworkCaps.max_rssi_sample_size = 0;
                frameworkCaps.max_scan_reporting_threshold = halCaps.maxReportingThreshold;
                return frameworkCaps;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return null;
        }
    }

    /**
     * See comments for {@link IWifiStaIface#getCapabilities()}
     */
    @Override
    public long getCapabilities() {
        final String methodStr = "getCapabilities";
        synchronized (mLock) {
            try {
                if (!checkIfaceAndLogFailure(methodStr)) return 0L;
                long halFeatureSet = mWifiStaIface.getFeatureSet();
                return halToFrameworkStaFeatureSet(halFeatureSet);
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return 0L;
        }
    }

    /**
     * See comments for {@link IWifiStaIface#getDebugRxPacketFates()}
     */
    @Override
    public List<WifiNative.RxFateReport> getDebugRxPacketFates() {
        final String methodStr = "getDebugRxPacketFates";
        List<WifiNative.RxFateReport> fateReports = new ArrayList<>();
        synchronized (mLock) {
            try {
                if (!checkIfaceAndLogFailure(methodStr)) return fateReports;
                WifiDebugRxPacketFateReport[] halReports = mWifiStaIface.getDebugRxPacketFates();
                for (WifiDebugRxPacketFateReport report : halReports) {
                    if (fateReports.size() >= WifiLoggerHal.MAX_FATE_LOG_LEN) break;
                    byte code = halToFrameworkRxPktFate(report.fate);
                    long us = report.frameInfo.driverTimestampUsec;
                    byte type = halToFrameworkPktFateFrameType(report.frameInfo.frameType);
                    byte[] frame = report.frameInfo.frameContent;
                    fateReports.add(new WifiNative.RxFateReport(code, us, type, frame));
                }
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            } catch (IllegalArgumentException e) {
                handleIllegalArgumentException(e, methodStr);
                return new ArrayList<>();
            }
            return fateReports;
        }
    }

    /**
     * See comments for {@link IWifiStaIface#getDebugTxPacketFates()}
     */
    @Override
    public List<WifiNative.TxFateReport> getDebugTxPacketFates() {
        final String methodStr = "getDebugTxPacketFates";
        List<WifiNative.TxFateReport> fateReports = new ArrayList<>();
        synchronized (mLock) {
            try {
                if (!checkIfaceAndLogFailure(methodStr)) return fateReports;
                WifiDebugTxPacketFateReport[] halReports = mWifiStaIface.getDebugTxPacketFates();
                for (WifiDebugTxPacketFateReport report : halReports) {
                    if (fateReports.size() >= WifiLoggerHal.MAX_FATE_LOG_LEN) break;
                    byte code = halToFrameworkTxPktFate(report.fate);
                    long us = report.frameInfo.driverTimestampUsec;
                    byte type = halToFrameworkPktFateFrameType(report.frameInfo.frameType);
                    byte[] frame = report.frameInfo.frameContent;
                    fateReports.add(new WifiNative.TxFateReport(code, us, type, frame));
                }
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            } catch (IllegalArgumentException e) {
                handleIllegalArgumentException(e, methodStr);
                return new ArrayList<>();
            }
            return fateReports;
        }
    }

    /**
     * See comments for {@link IWifiStaIface#getFactoryMacAddress()}
     */
    @Override
    @Nullable
    public MacAddress getFactoryMacAddress() {
        final String methodStr = "getFactoryMacAddress";
        byte[] macBytes;
        synchronized (mLock) {
            try {
                if (!checkIfaceAndLogFailure(methodStr)) return null;
                macBytes = mWifiStaIface.getFactoryMacAddress();
                return MacAddress.fromBytes(macBytes);
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Invalid MAC address received: " + e);
            }
            return null;
        }
    }

    /**
     * See comments for {@link IWifiStaIface#getCachedScanData()}
     */
    @Override
    @Nullable
    public WifiScanner.ScanData getCachedScanData() {
        final String methodStr = "getCachedScanData";
        synchronized (mLock) {
            try {
                if (!checkIfaceAndLogFailure(methodStr)) return null;
                CachedScanData scanData = mWifiStaIface.getCachedScanData();
                return halToFrameworkCachedScanData(scanData);
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return null;
        }
    }


    /**
     * See comments for {@link IWifiStaIface#getLinkLayerStats()}
     */
    @Override
    @Nullable
    public WifiLinkLayerStats getLinkLayerStats() {
        final String methodStr = "getLinkLayerStats";
        synchronized (mLock) {
            try {
                if (!checkIfaceAndLogFailure(methodStr)) return null;
                StaLinkLayerStats halStats = mWifiStaIface.getLinkLayerStats();
                return halToFrameworkLinkLayerStats(halStats);
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            } catch (IllegalArgumentException e) {
                // May indicate a malformed return value in the HAL.
                Log.wtf(TAG, methodStr + " encountered IllegalArgumentException: " + e);
            }
            return null;
        }
    }

    /**
     * See comments for {@link IWifiStaIface#getRoamingCapabilities()}
     */
    @Override
    @Nullable
    public WifiNative.RoamingCapabilities getRoamingCapabilities() {
        final String methodStr = "getRoamingCapabilities";
        synchronized (mLock) {
            try {
                if (!checkIfaceAndLogFailure(methodStr)) return null;
                StaRoamingCapabilities halCaps = mWifiStaIface.getRoamingCapabilities();
                WifiNative.RoamingCapabilities out = new WifiNative.RoamingCapabilities();
                out.maxBlocklistSize = halCaps.maxBlocklistSize;
                out.maxAllowlistSize = halCaps.maxAllowlistSize;
                return out;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return null;
        }
    }

    /**
     * See comments for {@link IWifiStaIface#installApfPacketFilter(byte[])}
     */
    @Override
    public boolean installApfPacketFilter(byte[] program) {
        final String methodStr = "installApfPacketFilter";
        synchronized (mLock) {
            try {
                if (!checkIfaceAndLogFailure(methodStr)) return false;
                mWifiStaIface.installApfPacketFilter(program);
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
     * See comments for {@link IWifiStaIface#readApfPacketFilterData()}
     */
    @Override
    @Nullable
    public byte[] readApfPacketFilterData() {
        final String methodStr = "readApfPacketFilterData";
        synchronized (mLock) {
            try {
                if (!checkIfaceAndLogFailure(methodStr)) return null;
                return mWifiStaIface.readApfPacketFilterData();
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return null;
        }
    }

    /**
     * See comments for {@link IWifiStaIface#setMacAddress(MacAddress)}
     */
    @Override
    public boolean setMacAddress(MacAddress mac) {
        final String methodStr = "setMacAddress";
        synchronized (mLock) {
            try {
                if (!checkIfaceAndLogFailure(methodStr)) return false;
                mWifiStaIface.setMacAddress(mac.toByteArray());
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
     * See comments for {@link IWifiStaIface#setRoamingState(int)}
     */
    @Override public @WifiNative.RoamingEnableStatus int setRoamingState(
            @WifiNative.RoamingEnableState int state) {
        final String methodStr = "setRoamingState";
        final int errorCode = WifiStaIface.SET_ROAMING_STATE_FAILURE_CODE;
        final byte halState = frameworkToHalStaRoamingState(state);
        if (halState == -1) return errorCode;

        synchronized (mLock) {
            try {
                if (!checkIfaceAndLogFailure(methodStr)) return errorCode;
                mWifiStaIface.setRoamingState(halState);
                return WifiNative.SET_FIRMWARE_ROAMING_SUCCESS;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                if (e.errorCode == WifiStatusCode.ERROR_BUSY) {
                    return WifiNative.SET_FIRMWARE_ROAMING_BUSY;
                }
                handleServiceSpecificException(e, methodStr);
            }
            return errorCode;
        }
    }

    /**
     * See comments for {@link IWifiStaIface#setScanMode(boolean)}
     */
    @Override
    public boolean setScanMode(boolean enable) {
        final String methodStr = "setScanMode";
        synchronized (mLock) {
            try {
                if (!checkIfaceAndLogFailure(methodStr)) return false;
                mWifiStaIface.setScanMode(enable);
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
     * See comments for
     * {@link IWifiStaIface#startBackgroundScan(int, WifiStaIface.StaBackgroundScanParameters)}
     */
    @Override
    public boolean startBackgroundScan(int cmdId, WifiStaIface.StaBackgroundScanParameters params) {
        final String methodStr = "startBackgroundScan";
        synchronized (mLock) {
            try {
                if (!checkIfaceAndLogFailure(methodStr)) return false;
                StaBackgroundScanParameters halParams = frameworkToHalBackgroundScanParams(params);
                mWifiStaIface.startBackgroundScan(cmdId, halParams);
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            } catch (IllegalArgumentException e) {
                handleIllegalArgumentException(e, methodStr);
            }
            return false;
        }
    }

    /**
     * See comments for {@link IWifiStaIface#startDebugPacketFateMonitoring()}
     */
    @Override
    public boolean startDebugPacketFateMonitoring() {
        final String methodStr = "startDebugPacketFateMonitoring";
        synchronized (mLock) {
            try {
                if (!checkIfaceAndLogFailure(methodStr)) return false;
                mWifiStaIface.startDebugPacketFateMonitoring();
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
     * See comments for
     * {@link IWifiStaIface#startRssiMonitoring(int, int, int)}
     */
    @Override
    public boolean startRssiMonitoring(int cmdId, int maxRssi, int minRssi) {
        final String methodStr = "startRssiMonitoring";
        synchronized (mLock) {
            try {
                if (!checkIfaceAndLogFailure(methodStr)) return false;
                mWifiStaIface.startRssiMonitoring(cmdId, maxRssi, minRssi);
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
     * See comments for {@link IWifiStaIface#startSendingKeepAlivePackets(int, byte[], int,
     *                         MacAddress, MacAddress, int)}
     */
    @Override
    public boolean startSendingKeepAlivePackets(int cmdId, byte[] ipPacketData, int etherType,
            MacAddress srcAddress, MacAddress dstAddress, int periodInMs) {
        final String methodStr = "startSendingKeepAlivePackets";
        synchronized (mLock) {
            try {
                if (!checkIfaceAndLogFailure(methodStr)) return false;
                mWifiStaIface.startSendingKeepAlivePackets(cmdId, ipPacketData, (char) etherType,
                        srcAddress.toByteArray(), dstAddress.toByteArray(), periodInMs);
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
     * See comments for {@link IWifiStaIface#stopBackgroundScan(int)}
     */
    @Override
    public boolean stopBackgroundScan(int cmdId) {
        final String methodStr = "stopBackgroundScan";
        synchronized (mLock) {
            try {
                if (!checkIfaceAndLogFailure(methodStr)) return false;
                mWifiStaIface.stopBackgroundScan(cmdId);
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
     * See comments for {@link IWifiStaIface#stopRssiMonitoring(int)}
     */
    @Override
    public boolean stopRssiMonitoring(int cmdId) {
        final String methodStr = "stopRssiMonitoring";
        synchronized (mLock) {
            try {
                if (!checkIfaceAndLogFailure(methodStr)) return false;
                mWifiStaIface.stopRssiMonitoring(cmdId);
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
     * See comments for {@link IWifiStaIface#stopSendingKeepAlivePackets(int)}
     */
    @Override
    public boolean stopSendingKeepAlivePackets(int cmdId) {
        final String methodStr = "stopSendingKeepAlivePackets";
        synchronized (mLock) {
            try {
                if (!checkIfaceAndLogFailure(methodStr)) return false;
                mWifiStaIface.stopSendingKeepAlivePackets(cmdId);
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
     * See comments for {@link IWifiStaIface#setDtimMultiplier(int)}
     */
    @Override
    public boolean setDtimMultiplier(int multiplier) {
        final String methodStr = "setDtimMultiplier";
        synchronized (mLock) {
            try {
                if (!checkIfaceAndLogFailure(methodStr)) return false;
                mWifiStaIface.setDtimMultiplier(multiplier);
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
     * See comments for {@link IWifiStaIface#setRoamingMode(int)}
     */
    public @WifiStatusCode int setRoamingMode(@RoamingMode int roamingMode) {
        final String methodStr = "setRoamingMode";
        @WifiStatusCode int errorCode = WifiStatusCode.ERROR_UNKNOWN;
        synchronized (mLock) {
            try {
                if (checkIfaceAndLogFailure(methodStr)) {
                    mWifiStaIface.setRoamingState(frameworkToHalRoamingMode(roamingMode));
                    errorCode = WifiStatusCode.SUCCESS;
                }
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
                errorCode = WifiStatusCode.ERROR_NOT_STARTED;
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
                errorCode = e.errorCode;
            } catch (IllegalArgumentException e) {
                handleIllegalArgumentException(e, methodStr);
                errorCode = WifiStatusCode.ERROR_INVALID_ARGS;
            }
            return errorCode;
        }
    }

    private static byte frameworkToHalRoamingMode(
            @WifiManager.RoamingMode int mode) {
        switch (mode) {
            case WifiManager.ROAMING_MODE_NONE:
                return StaRoamingState.DISABLED;
            case WifiManager.ROAMING_MODE_NORMAL:
                return StaRoamingState.ENABLED;
            case WifiManager.ROAMING_MODE_AGGRESSIVE:
                return StaRoamingState.AGGRESSIVE;
            default:
                throw new IllegalArgumentException("frameworkToHalRoamingMode Invalid mode: "
                        + mode);
        }
    }

    /**
     * Get target wake time (TWT) capabilities.
     *
     * @return TWT capabilities as Bundle
     */
    @Override
    public Bundle getTwtCapabilities() {
        final String methodStr = "getTwtCapabilities";
        synchronized (mLock) {
            try {
                if (!isServiceVersionAtLeast(2) || !checkIfaceAndLogFailure(methodStr)) {
                    return null;
                }
                android.hardware.wifi.TwtCapabilities halTwtCapabilities =
                        mWifiStaIface.twtGetCapabilities();
                if (halTwtCapabilities == null) return null;
                Bundle twtCapabilities = new Bundle();
                twtCapabilities.putBoolean(WifiManager.TWT_CAPABILITIES_KEY_BOOLEAN_TWT_REQUESTER,
                        halTwtCapabilities.isTwtRequesterSupported);
                twtCapabilities.putInt(
                        WifiManager.TWT_CAPABILITIES_KEY_INT_MIN_WAKE_DURATION_MICROS,
                        halTwtCapabilities.minWakeDurationUs);
                twtCapabilities.putInt(
                        WifiManager.TWT_CAPABILITIES_KEY_INT_MAX_WAKE_DURATION_MICROS,
                        halTwtCapabilities.maxWakeDurationUs);
                twtCapabilities.putLong(
                        WifiManager.TWT_CAPABILITIES_KEY_LONG_MIN_WAKE_INTERVAL_MICROS,
                        halTwtCapabilities.minWakeIntervalUs);
                twtCapabilities.putLong(
                        WifiManager.TWT_CAPABILITIES_KEY_LONG_MAX_WAKE_INTERVAL_MICROS,
                        halTwtCapabilities.maxWakeIntervalUs);
                return twtCapabilities;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return null;
        }
    }

    /**
     * Set up a TWT session
     *
     * @param cmdId      Command ID to use for this invocation.
     * @param twtRequest TWT request configuration to setup TWT session
     * @return true if successful, false otherwise.
     */
    @Override
    public boolean setupTwtSession(int cmdId, TwtRequest twtRequest) {
        final String methodStr = "setupTwtSession";
        synchronized (mLock) {
            try {
                if (!isServiceVersionAtLeast(2) || !checkIfaceAndLogFailure(methodStr)) {
                    return false;
                }
                android.hardware.wifi.TwtRequest halTwtRequest =
                        new android.hardware.wifi.TwtRequest();
                halTwtRequest.maxWakeDurationUs = twtRequest.getMaxWakeDurationMicros();
                halTwtRequest.minWakeDurationUs = twtRequest.getMinWakeDurationMicros();
                halTwtRequest.maxWakeIntervalUs = twtRequest.getMaxWakeIntervalMicros();
                halTwtRequest.minWakeIntervalUs = twtRequest.getMinWakeIntervalMicros();
                mWifiStaIface.twtSessionSetup(cmdId, halTwtRequest);
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
     * Teardown a TWT session.
     *
     * @param cmdId     Command ID to use for this invocation.
     * @param sessionId TWT session identifier
     * @return true if successful, false otherwise.
     */
    @Override
    public boolean tearDownTwtSession(int cmdId, int sessionId) {
        final String methodStr = "tearDownTwtSession";
        synchronized (mLock) {
            try {
                if (!isServiceVersionAtLeast(2) || !checkIfaceAndLogFailure(methodStr)) {
                    return false;
                }
                mWifiStaIface.twtSessionTeardown(cmdId, sessionId);
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
     * Get stats for the TWT session.
     *
     * @param cmdId     Command ID to use for this invocation.
     * @param sessionId TWT session identifier
     * @return true if successful, false otherwise.
     */
    @Override
    public boolean getStatsTwtSession(int cmdId, int sessionId) {
        final String methodStr = "getStatsTwtSession";
        synchronized (mLock) {
            try {
                if (!isServiceVersionAtLeast(2) || !checkIfaceAndLogFailure(methodStr)) {
                    return false;
                }
                mWifiStaIface.twtSessionGetStats(cmdId, sessionId);
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

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
            ScanResult frameworkScanResult = halToFrameworkScanResult(result);
            if (frameworkScanResult == null) {
                Log.e(TAG, "Unable to convert scan result from HAL to framework");
                return;
            }
            mFrameworkCallback.onBackgroundFullScanResult(cmdId, bucketsScanned,
                    frameworkScanResult);
        }

        @Override
        public void onBackgroundScanResults(int cmdId, StaScanData[] scanDatas) {
            if (mFrameworkCallback == null) return;
            WifiScanner.ScanData[] frameworkScanDatas = halToFrameworkScanDatas(cmdId, scanDatas);
            mFrameworkCallback.onBackgroundScanResults(cmdId, frameworkScanDatas);
        }

        @Override
        public void onRssiThresholdBreached(int cmdId, byte[/* 6 */] currBssid, int currRssi) {
            if (mFrameworkCallback == null) return;
            mFrameworkCallback.onRssiThresholdBreached(cmdId, currBssid, currRssi);
        }

        @Override
        public String getInterfaceHash() {
            return IWifiStaIfaceEventCallback.HASH;
        }

        @Override
        public int getInterfaceVersion() {
            return IWifiStaIfaceEventCallback.VERSION;
        }

        @Override
        public void onTwtFailure(int cmdId, byte twtErrorCode) {
            if (mFrameworkCallback == null) return;
            @TwtErrorCode int errorCode;
            switch (twtErrorCode) {
                case TwtErrorCode.INVALID_PARAMS:
                    errorCode = TwtSessionCallback.TWT_ERROR_CODE_INVALID_PARAMS;
                    break;
                case TwtErrorCode.MAX_SESSION_REACHED:
                    errorCode = TwtSessionCallback.TWT_ERROR_CODE_MAX_SESSIONS_REACHED;
                    break;
                case TwtErrorCode.NOT_AVAILABLE:
                    errorCode = TwtSessionCallback.TWT_ERROR_CODE_NOT_AVAILABLE;
                    break;
                case TwtErrorCode.NOT_SUPPORTED:
                    errorCode = TwtSessionCallback.TWT_ERROR_CODE_NOT_SUPPORTED;
                    break;
                case TwtErrorCode.PEER_NOT_SUPPORTED:
                    errorCode = TwtSessionCallback.TWT_ERROR_CODE_AP_NOT_SUPPORTED;
                    break;
                case TwtErrorCode.PEER_REJECTED:
                    errorCode = TwtSessionCallback.TWT_ERROR_CODE_AP_REJECTED;
                    break;
                case TwtErrorCode.TIMEOUT:
                    errorCode = TwtSessionCallback.TWT_ERROR_CODE_TIMEOUT;
                    break;
                case TwtErrorCode.ALREADY_RESUMED:
                case TwtErrorCode.ALREADY_SUSPENDED:
                case TwtErrorCode.FAILURE_UNKNOWN:
                default:
                    errorCode = TwtSessionCallback.TWT_REASON_CODE_UNKNOWN;
            }
            mFrameworkCallback.onTwtFailure(cmdId, errorCode);
        }

        @Override
        public void onTwtSessionCreate(int cmdId, TwtSession twtSession) {
            if (mFrameworkCallback == null || twtSession == null) return;
            mFrameworkCallback.onTwtSessionCreate(cmdId, twtSession.wakeDurationUs,
                    twtSession.wakeDurationUs, twtSession.mloLinkId, twtSession.sessionId);
        }

        @Override
        public void onTwtSessionUpdate(int cmdId, TwtSession twtSession) {
            //TODO: Implementation
        }

        @Override
        public void onTwtSessionResume(int cmdId, int sessionId) {
            //TODO: Implementation
        }

        @Override
        public void onTwtSessionSuspend(int cmdId, int sessionId) {
            //TODO: Implementation
        }

        @Override
        public void onTwtSessionTeardown(int cmdId, int twtSessionId, byte twtReasonCode) {
            if (mFrameworkCallback == null) return;
            @TwtTeardownReasonCode int reasonCode;
            switch (twtReasonCode) {
                case TwtTeardownReasonCode.INTERNALLY_INITIATED:
                    reasonCode = TwtSessionCallback.TWT_REASON_CODE_INTERNALLY_INITIATED;
                    break;
                case TwtTeardownReasonCode.LOCALLY_REQUESTED:
                    reasonCode = TwtSessionCallback.TWT_REASON_CODE_LOCALLY_REQUESTED;
                    break;
                case TwtTeardownReasonCode.PEER_INITIATED:
                    reasonCode = TwtSessionCallback.TWT_REASON_CODE_PEER_INITIATED;
                    break;
                case TwtTeardownReasonCode.UNKNOWN:
                default:
                    reasonCode = TwtSessionCallback.TWT_REASON_CODE_UNKNOWN;
            }
            mFrameworkCallback.onTwtSessionTeardown(cmdId, twtSessionId, reasonCode);
        }

        @Override
        public void onTwtSessionStats(int cmdId, int twtSessionId,
                TwtSessionStats twtSessionStats) {
            if (mFrameworkCallback == null) return;
            Bundle twtStats = new Bundle();
            twtStats.putInt(
                    android.net.wifi.twt.TwtSession.TWT_STATS_KEY_INT_AVERAGE_TX_PACKET_COUNT,
                    twtSessionStats.avgTxPktCount);
            twtStats.putInt(
                    android.net.wifi.twt.TwtSession.TWT_STATS_KEY_INT_AVERAGE_TX_PACKET_SIZE,
                    twtSessionStats.avgTxPktSize);
            twtStats.putInt(
                    android.net.wifi.twt.TwtSession.TWT_STATS_KEY_INT_AVERAGE_RX_PACKET_COUNT,
                    twtSessionStats.avgRxPktCount);
            twtStats.putInt(
                    android.net.wifi.twt.TwtSession.TWT_STATS_KEY_INT_AVERAGE_RX_PACKET_SIZE,
                    twtSessionStats.avgRxPktSize);
            twtStats.putInt(
                    android.net.wifi.twt.TwtSession.TWT_STATS_KEY_INT_AVERAGE_EOSP_DURATION_MICROS,
                    twtSessionStats.avgEospDurationUs);
            twtStats.putInt(
                    android.net.wifi.twt.TwtSession.TWT_STATS_KEY_INT_AVERAGE_EOSP_DURATION_MICROS,
                    twtSessionStats.eospCount);
            mFrameworkCallback.onTwtSessionStats(cmdId, twtSessionId, twtStats);
        }
    }

    // Utilities

    // Only sets the fields of ScanResult used by Gscan clients.
    private ScanResult halToFrameworkScanResult(StaScanResult scanResult) {
        if (scanResult == null) return null;
        WifiSsid originalSsid = WifiSsid.fromBytes(scanResult.ssid);
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

    private ScanResult[] aidlToFrameworkScanResults(StaScanResult[] scanResults) {
        if (scanResults == null || scanResults.length == 0) return new ScanResult[0];
        ScanResult[] frameworkScanResults = new ScanResult[scanResults.length];
        int i = 0;
        for (StaScanResult scanResult : scanResults) {
            ScanResult frameworkScanResult = halToFrameworkScanResult(scanResult);
            if (frameworkScanResult == null) {
                Log.e(TAG, "halToFrameworkScanResults: unable to convert hidl to framework "
                        + "scan result!");
                continue;
            }
            frameworkScanResults[i++] = frameworkScanResult;
        }
        return frameworkScanResults;
    }

    private static int halToFrameworkScanDataFlags(int flag) {
        if (flag == StaScanDataFlagMask.INTERRUPTED) {
            return 1;
        } else {
            return 0;
        }
    }

    private WifiScanner.ScanData[] halToFrameworkScanDatas(int cmdId, StaScanData[] scanDatas) {
        if (scanDatas == null || scanDatas.length == 0) return new WifiScanner.ScanData[0];
        WifiScanner.ScanData[] frameworkScanDatas = new WifiScanner.ScanData[scanDatas.length];
        int i = 0;
        for (StaScanData scanData : scanDatas) {
            int flags = halToFrameworkScanDataFlags(scanData.flags);
            ScanResult[] frameworkScanResults = aidlToFrameworkScanResults(scanData.results);
            frameworkScanDatas[i++] =
                    new WifiScanner.ScanData(cmdId, flags, scanData.bucketsScanned,
                            WifiScanner.WIFI_BAND_UNSPECIFIED, frameworkScanResults);
        }
        return frameworkScanDatas;
    }

    @WifiAnnotations.WifiStandard
    private static int wifiRatePreambleToWifiStandard(int wifiRatePreamble) {
        switch (wifiRatePreamble) {
            case WifiRatePreamble.CCK:
            case WifiRatePreamble.OFDM:
                return ScanResult.WIFI_STANDARD_LEGACY;
            case WifiRatePreamble.HT:
                return ScanResult.WIFI_STANDARD_11N;
            case WifiRatePreamble.VHT:
                return ScanResult.WIFI_STANDARD_11AC;
            case WifiRatePreamble.HE:
                return ScanResult.WIFI_STANDARD_11AX;
            case WifiRatePreamble.EHT:
                return ScanResult.WIFI_STANDARD_11BE;
            default:
                return ScanResult.WIFI_STANDARD_UNKNOWN;
        }
    }

    private ScanResult halToFrameworkCachedScanResult(CachedScanResult scanResult) {
        if (scanResult == null) return null;
        WifiSsid originalSsid = WifiSsid.fromBytes(scanResult.ssid);
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
        frameworkScanResult.level = scanResult.rssiDbm;
        frameworkScanResult.frequency = scanResult.frequencyMhz;
        frameworkScanResult.timestamp = scanResult.timeStampInUs;
        frameworkScanResult.channelWidth = HalAidlUtil
                .getChannelBandwidthFromHal(scanResult.channelWidthMhz);
        frameworkScanResult.setWifiStandard(
                wifiRatePreambleToWifiStandard(scanResult.preambleType));
        return frameworkScanResult;
    }

    private ScanResult[] aidlToFrameworkCachedScanResults(CachedScanResult[] cachedScanResults) {
        if (cachedScanResults == null) return new ScanResult[0];
        List<ScanResult> frameworkScanResults = new ArrayList<>();
        for (CachedScanResult cachedScanResult : cachedScanResults) {
            ScanResult frameworkScanResult = halToFrameworkCachedScanResult(cachedScanResult);
            if (frameworkScanResult == null) {
                Log.e(TAG, "aidlToFrameworkCachedScanResults: unable to convert aidl to framework "
                        + "scan result!");
                continue;
            }
            frameworkScanResults.add(frameworkScanResult);
        }
        return frameworkScanResults.toArray(new ScanResult[0]);
    }

    private WifiScanner.ScanData halToFrameworkCachedScanData(CachedScanData cachedScanData) {
        if (cachedScanData == null) return null;
        ScanResult[] scanResults = aidlToFrameworkCachedScanResults(
                cachedScanData.cachedScanResults);

        // Todo b/319658055: map cachedScanData.scannedFrequenciesMhz to WifiScanner.WifiBand
        WifiScanner.ScanData frameworkScanData = new WifiScanner.ScanData(0, 0,
                0, WifiScanner.WIFI_BAND_UNSPECIFIED, scanResults);

        return frameworkScanData;
    }

    private static StaRoamingConfig frameworkToHalStaRoamingConfig(List<MacAddress> bssidBlocklist,
            List<byte[]> ssidAllowlist) {
        StaRoamingConfig config = new StaRoamingConfig();
        config.bssidBlocklist = new android.hardware.wifi.MacAddress[bssidBlocklist.size()];
        config.ssidAllowlist = new Ssid[ssidAllowlist.size()];
        for (int i = 0; i < bssidBlocklist.size(); i++) {
            android.hardware.wifi.MacAddress mac = new android.hardware.wifi.MacAddress();
            mac.data = bssidBlocklist.get(i).toByteArray();
            config.bssidBlocklist[i] = mac;
        }
        for (int i = 0; i < ssidAllowlist.size(); i++) {
            Ssid ssid = new Ssid();
            ssid.data = ssidAllowlist.get(i);
            config.ssidAllowlist[i] = ssid;
        }
        return config;
    }

    private static byte halToFrameworkPktFateFrameType(int type) throws IllegalArgumentException {
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

    private static byte halToFrameworkRxPktFate(int type) throws IllegalArgumentException {
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

    private static byte halToFrameworkTxPktFate(int type) throws IllegalArgumentException {
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
    protected static long halToFrameworkStaFeatureSet(long halFeatureSet) {
        long features = 0;
        if (hasCapability(halFeatureSet,
                android.hardware.wifi.IWifiStaIface.FeatureSetMask.HOTSPOT)) {
            features |= WifiManager.WIFI_FEATURE_PASSPOINT;
        }
        if (hasCapability(halFeatureSet,
                android.hardware.wifi.IWifiStaIface.FeatureSetMask.BACKGROUND_SCAN)) {
            features |= WifiManager.WIFI_FEATURE_SCANNER;
        }
        if (hasCapability(halFeatureSet,
                android.hardware.wifi.IWifiStaIface.FeatureSetMask.PNO)) {
            features |= WifiManager.WIFI_FEATURE_PNO;
        }
        if (hasCapability(halFeatureSet,
                android.hardware.wifi.IWifiStaIface.FeatureSetMask.TDLS)) {
            features |= WifiManager.WIFI_FEATURE_TDLS;
        }
        if (hasCapability(halFeatureSet,
                android.hardware.wifi.IWifiStaIface.FeatureSetMask.TDLS_OFFCHANNEL)) {
            features |= WifiManager.WIFI_FEATURE_TDLS_OFFCHANNEL;
        }
        if (hasCapability(halFeatureSet,
                android.hardware.wifi.IWifiStaIface.FeatureSetMask.LINK_LAYER_STATS)) {
            features |= WifiManager.WIFI_FEATURE_LINK_LAYER_STATS;
        }
        if (hasCapability(halFeatureSet,
                android.hardware.wifi.IWifiStaIface.FeatureSetMask.RSSI_MONITOR)) {
            features |= WifiManager.WIFI_FEATURE_RSSI_MONITOR;
        }
        if (hasCapability(halFeatureSet,
                android.hardware.wifi.IWifiStaIface.FeatureSetMask.KEEP_ALIVE)) {
            features |= WifiManager.WIFI_FEATURE_MKEEP_ALIVE;
        }
        if (hasCapability(halFeatureSet,
                android.hardware.wifi.IWifiStaIface.FeatureSetMask.ND_OFFLOAD)) {
            features |= WifiManager.WIFI_FEATURE_CONFIG_NDO;
        }
        if (hasCapability(halFeatureSet,
                android.hardware.wifi.IWifiStaIface.FeatureSetMask.CONTROL_ROAMING)) {
            features |= WifiManager.WIFI_FEATURE_CONTROL_ROAMING;
        }
        if (hasCapability(halFeatureSet,
                android.hardware.wifi.IWifiStaIface.FeatureSetMask.PROBE_IE_ALLOWLIST)) {
            features |= WifiManager.WIFI_FEATURE_IE_WHITELIST;
        }
        if (hasCapability(halFeatureSet,
                android.hardware.wifi.IWifiStaIface.FeatureSetMask.SCAN_RAND)) {
            features |= WifiManager.WIFI_FEATURE_SCAN_RAND;
        }
        if (hasCapability(halFeatureSet,
                android.hardware.wifi.IWifiStaIface.FeatureSetMask.ROAMING_MODE_CONTROL)) {
            features |= WifiManager.WIFI_FEATURE_AGGRESSIVE_ROAMING_MODE_SUPPORT;
        }
        return features;
    }

    @VisibleForTesting
    WifiLinkLayerStats halToFrameworkLinkLayerStats(StaLinkLayerStats stats) {
        if (stats == null) return null;
        WifiLinkLayerStats out = new WifiLinkLayerStats();
        setIfaceStats(out, stats.iface);
        setRadioStats(out, stats.radios);
        out.timeStampInMs = stats.timeStampInMs;
        out.version = WifiLinkLayerStats.V1_5;  // only used in unit tests, keep latest HIDL
        return out;
    }

    private static void setIfaceStats(WifiLinkLayerStats stats,
            StaLinkLayerIfaceStats iface) {
        int linkIndex = 0;
        if (iface == null || iface.links == null) return;
        stats.links = new WifiLinkLayerStats.LinkSpecificStats[iface.links.length];
        for (StaLinkLayerLinkStats link : iface.links) {
            setIfaceStatsPerLinkFromAidl(stats, link, linkIndex);
            linkIndex++;
        }
    }

    private static @WifiUsabilityStatsEntry.LinkState int halToFrameworkLinkState(
            int powerState) {
        switch(powerState) {
            case StaLinkLayerLinkStats.StaLinkState.NOT_IN_USE:
                return WifiUsabilityStatsEntry.LINK_STATE_NOT_IN_USE;
            case StaLinkLayerLinkStats.StaLinkState.IN_USE:
                return WifiUsabilityStatsEntry.LINK_STATE_IN_USE;
            default:
                return WifiUsabilityStatsEntry.LINK_STATE_UNKNOWN;
        }
    }

    private static void setIfaceStatsPerLinkFromAidl(WifiLinkLayerStats stats,
            StaLinkLayerLinkStats aidlStats, int linkIndex) {
        if (aidlStats == null) return;
        stats.links[linkIndex] = new WifiLinkLayerStats.LinkSpecificStats();
        stats.links[linkIndex].link_id = aidlStats.linkId;
        stats.links[linkIndex].state = halToFrameworkLinkState(aidlStats.state);
        stats.links[linkIndex].radio_id = aidlStats.radioId;
        stats.links[linkIndex].frequencyMhz = aidlStats.frequencyMhz;
        stats.links[linkIndex].beacon_rx = aidlStats.beaconRx;
        stats.links[linkIndex].rssi_mgmt = aidlStats.avgRssiMgmt;
        // Statistics are broken out by Wireless Multimedia Extensions categories
        // WME Best Effort Access Category
        stats.links[linkIndex].rxmpdu_be = aidlStats.wmeBePktStats.rxMpdu;
        stats.links[linkIndex].txmpdu_be = aidlStats.wmeBePktStats.txMpdu;
        stats.links[linkIndex].lostmpdu_be = aidlStats.wmeBePktStats.lostMpdu;
        stats.links[linkIndex].retries_be = aidlStats.wmeBePktStats.retries;
        // WME Background Access Category
        stats.links[linkIndex].rxmpdu_bk = aidlStats.wmeBkPktStats.rxMpdu;
        stats.links[linkIndex].txmpdu_bk = aidlStats.wmeBkPktStats.txMpdu;
        stats.links[linkIndex].lostmpdu_bk = aidlStats.wmeBkPktStats.lostMpdu;
        stats.links[linkIndex].retries_bk = aidlStats.wmeBkPktStats.retries;
        // WME Video Access Category
        stats.links[linkIndex].rxmpdu_vi = aidlStats.wmeViPktStats.rxMpdu;
        stats.links[linkIndex].txmpdu_vi = aidlStats.wmeViPktStats.txMpdu;
        stats.links[linkIndex].lostmpdu_vi = aidlStats.wmeViPktStats.lostMpdu;
        stats.links[linkIndex].retries_vi = aidlStats.wmeViPktStats.retries;
        // WME Voice Access Category
        stats.links[linkIndex].rxmpdu_vo = aidlStats.wmeVoPktStats.rxMpdu;
        stats.links[linkIndex].txmpdu_vo = aidlStats.wmeVoPktStats.txMpdu;
        stats.links[linkIndex].lostmpdu_vo = aidlStats.wmeVoPktStats.lostMpdu;
        stats.links[linkIndex].retries_vo = aidlStats.wmeVoPktStats.retries;
        // WME Best Effort Access Category
        stats.links[linkIndex].contentionTimeMinBeInUsec =
                aidlStats.wmeBeContentionTimeStats.contentionTimeMinInUsec;
        stats.links[linkIndex].contentionTimeMaxBeInUsec =
                aidlStats.wmeBeContentionTimeStats.contentionTimeMaxInUsec;
        stats.links[linkIndex].contentionTimeAvgBeInUsec =
                aidlStats.wmeBeContentionTimeStats.contentionTimeAvgInUsec;
        stats.links[linkIndex].contentionNumSamplesBe =
                aidlStats.wmeBeContentionTimeStats.contentionNumSamples;
        // WME Background Access Category
        stats.links[linkIndex].contentionTimeMinBkInUsec =
                aidlStats.wmeBkContentionTimeStats.contentionTimeMinInUsec;
        stats.links[linkIndex].contentionTimeMaxBkInUsec =
                aidlStats.wmeBkContentionTimeStats.contentionTimeMaxInUsec;
        stats.links[linkIndex].contentionTimeAvgBkInUsec =
                aidlStats.wmeBkContentionTimeStats.contentionTimeAvgInUsec;
        stats.links[linkIndex].contentionNumSamplesBk =
                aidlStats.wmeBkContentionTimeStats.contentionNumSamples;
        // WME Video Access Category
        stats.links[linkIndex].contentionTimeMinViInUsec =
                aidlStats.wmeViContentionTimeStats.contentionTimeMinInUsec;
        stats.links[linkIndex].contentionTimeMaxViInUsec =
                aidlStats.wmeViContentionTimeStats.contentionTimeMaxInUsec;
        stats.links[linkIndex].contentionTimeAvgViInUsec =
                aidlStats.wmeViContentionTimeStats.contentionTimeAvgInUsec;
        stats.links[linkIndex].contentionNumSamplesVi =
                aidlStats.wmeViContentionTimeStats.contentionNumSamples;
        // WME Voice Access Category
        stats.links[linkIndex].contentionTimeMinVoInUsec =
                aidlStats.wmeVoContentionTimeStats.contentionTimeMinInUsec;
        stats.links[linkIndex].contentionTimeMaxVoInUsec =
                aidlStats.wmeVoContentionTimeStats.contentionTimeMaxInUsec;
        stats.links[linkIndex].contentionTimeAvgVoInUsec =
                aidlStats.wmeVoContentionTimeStats.contentionTimeAvgInUsec;
        stats.links[linkIndex].contentionNumSamplesVo =
                aidlStats.wmeVoContentionTimeStats.contentionNumSamples;
        stats.links[linkIndex].timeSliceDutyCycleInPercent = aidlStats.timeSliceDutyCycleInPercent;
        // Peer information statistics
        stats.links[linkIndex].peerInfo = new WifiLinkLayerStats.PeerInfo[aidlStats.peers.length];
        for (int i = 0; i < stats.links[linkIndex].peerInfo.length; i++) {
            WifiLinkLayerStats.PeerInfo peer = new WifiLinkLayerStats.PeerInfo();
            StaPeerInfo staPeerInfo = aidlStats.peers[i];
            peer.staCount = (short) staPeerInfo.staCount;
            peer.chanUtil = (short) staPeerInfo.chanUtil;
            WifiLinkLayerStats.RateStat[] rateStats =
                    new WifiLinkLayerStats.RateStat[staPeerInfo.rateStats.length];
            for (int j = 0; j < staPeerInfo.rateStats.length; j++) {
                rateStats[j] = new WifiLinkLayerStats.RateStat();
                StaRateStat staRateStat = staPeerInfo.rateStats[j];
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
            stats.links[linkIndex].peerInfo[i] = peer;
        }
    }

    private void setRadioStats(WifiLinkLayerStats stats, StaLinkLayerRadioStats[] radios) {
        if (radios == null) return;
        int radioIndex = 0;
        stats.radioStats = new WifiLinkLayerStats.RadioStat[radios.length];
        for (StaLinkLayerRadioStats radioStats : radios) {
            WifiLinkLayerStats.RadioStat radio = new WifiLinkLayerStats.RadioStat();
            setFrameworkPerRadioStatsFromAidl(radio, radioStats);
            stats.radioStats[radioIndex] = radio;
            aggregateFrameworkRadioStatsFromAidl(radioIndex, stats, radioStats);
            radioIndex++;
        }
    }

    private static void setFrameworkPerRadioStatsFromAidl(WifiLinkLayerStats.RadioStat radio,
            StaLinkLayerRadioStats aidlRadioStats) {
        radio.radio_id = aidlRadioStats.radioId;
        radio.on_time = aidlRadioStats.onTimeInMs;
        radio.tx_time = aidlRadioStats.txTimeInMs;
        radio.rx_time = aidlRadioStats.rxTimeInMs;
        radio.on_time_scan = aidlRadioStats.onTimeInMsForScan;
        radio.on_time_nan_scan = aidlRadioStats.onTimeInMsForNanScan;
        radio.on_time_background_scan = aidlRadioStats.onTimeInMsForBgScan;
        radio.on_time_roam_scan = aidlRadioStats.onTimeInMsForRoamScan;
        radio.on_time_pno_scan = aidlRadioStats.onTimeInMsForPnoScan;
        radio.on_time_hs20_scan = aidlRadioStats.onTimeInMsForHs20Scan;
        /* Copy list of channel stats */
        for (WifiChannelStats channelStats : aidlRadioStats.channelStats) {
            WifiLinkLayerStats.ChannelStats channelStatsEntry =
                    new WifiLinkLayerStats.ChannelStats();
            channelStatsEntry.frequency = channelStats.channel.centerFreq;
            channelStatsEntry.radioOnTimeMs = channelStats.onTimeInMs;
            channelStatsEntry.ccaBusyTimeMs = channelStats.ccaBusyTimeInMs;
            radio.channelStatsMap.put(channelStats.channel.centerFreq, channelStatsEntry);
        }
    }

    private void aggregateFrameworkRadioStatsFromAidl(int radioIndex,
            WifiLinkLayerStats stats, StaLinkLayerRadioStats aidlRadioStats) {
        if (!mContext.getResources()
                .getBoolean(R.bool.config_wifiLinkLayerAllRadiosStatsAggregationEnabled)
                && radioIndex > 0) {
            return;
        }
        // Aggregate the radio stats from all the radios
        stats.on_time += aidlRadioStats.onTimeInMs;
        stats.tx_time += aidlRadioStats.txTimeInMs;
        // Aggregate tx_time_per_level based on the assumption that the length of
        // txTimeInMsPerLevel is the same across all radios. So txTimeInMsPerLevel on other
        // radios at array indices greater than the length of first radio will be dropped.
        if (stats.tx_time_per_level == null) {
            stats.tx_time_per_level = new int[aidlRadioStats.txTimeInMsPerLevel.length];
        }
        for (int i = 0; i < aidlRadioStats.txTimeInMsPerLevel.length
                && i < stats.tx_time_per_level.length; i++) {
            stats.tx_time_per_level[i] += aidlRadioStats.txTimeInMsPerLevel[i];
        }
        stats.rx_time += aidlRadioStats.rxTimeInMs;
        stats.on_time_scan += aidlRadioStats.onTimeInMsForScan;
        stats.on_time_nan_scan += aidlRadioStats.onTimeInMsForNanScan;
        stats.on_time_background_scan += aidlRadioStats.onTimeInMsForBgScan;
        stats.on_time_roam_scan += aidlRadioStats.onTimeInMsForRoamScan;
        stats.on_time_pno_scan += aidlRadioStats.onTimeInMsForPnoScan;
        stats.on_time_hs20_scan += aidlRadioStats.onTimeInMsForHs20Scan;
        /* Copy list of channel stats */
        for (WifiChannelStats channelStats : aidlRadioStats.channelStats) {
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

    private static StaBackgroundScanParameters frameworkToHalBackgroundScanParams(
            WifiStaIface.StaBackgroundScanParameters frameworkParams)
            throws IllegalArgumentException {
        StaBackgroundScanParameters halParams = new StaBackgroundScanParameters();
        halParams.basePeriodInMs = frameworkParams.basePeriodInMs;
        halParams.maxApPerScan = frameworkParams.maxApPerScan;
        halParams.reportThresholdPercent = frameworkParams.reportThresholdPercent;
        halParams.reportThresholdNumScans = frameworkParams.reportThresholdNumScans;
        int numBuckets = frameworkParams.buckets != null ? frameworkParams.buckets.size() : 0;
        halParams.buckets = new android.hardware.wifi.StaBackgroundScanBucketParameters[numBuckets];
        if (frameworkParams.buckets != null) {
            for (int i = 0; i < numBuckets; i++) {
                halParams.buckets[i] = frameworkToHalBucketParams(frameworkParams.buckets.get(i));
            }
        }
        return halParams;
    }

    private static StaBackgroundScanBucketParameters frameworkToHalBucketParams(
            WifiNative.BucketSettings frameworkBucket) throws IllegalArgumentException {
        StaBackgroundScanBucketParameters halBucket = new StaBackgroundScanBucketParameters();
        halBucket.bucketIdx = frameworkBucket.bucket;
        halBucket.band = frameworkToHalWifiBand(frameworkBucket.band);
        int numChannels = frameworkBucket.channels != null ? frameworkBucket.channels.length : 0;
        halBucket.frequencies = new int[numChannels];
        if (frameworkBucket.channels != null) {
            for (int i = 0; i < frameworkBucket.channels.length; i++) {
                halBucket.frequencies[i] = frameworkBucket.channels[i].frequency;
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

    private static int frameworkToHalWifiBand(int frameworkBand) throws IllegalArgumentException {
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

    private static int frameworkToHalReportSchemeMask(int reportUnderscoreEvents)
            throws IllegalArgumentException {
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

    private boolean checkIfaceAndLogFailure(String methodStr) {
        if (mWifiStaIface == null) {
            Log.e(TAG, "Unable to call " + methodStr + " because iface is null.");
            return false;
        }
        return true;
    }

    private void handleRemoteException(RemoteException e, String methodStr) {
        mWifiStaIface = null;
        mIfaceName = null;
        Log.e(TAG, methodStr + " failed with remote exception: " + e);
    }

    private void handleServiceSpecificException(ServiceSpecificException e, String methodStr) {
        Log.e(TAG, methodStr + " failed with service-specific exception: " + e);
    }

    private void handleIllegalArgumentException(IllegalArgumentException e, String methodStr) {
        Log.e(TAG, methodStr + " failed with illegal argument exception: " + e);
    }
}
