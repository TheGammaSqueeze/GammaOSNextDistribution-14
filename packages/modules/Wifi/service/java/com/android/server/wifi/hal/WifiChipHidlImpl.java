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

import static android.net.wifi.CoexUnsafeChannel.POWER_CAP_NONE;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.content.res.Resources;
import android.hardware.wifi.V1_0.IWifiChipEventCallback;
import android.hardware.wifi.V1_0.IfaceType;
import android.hardware.wifi.V1_0.WifiDebugHostWakeReasonStats;
import android.hardware.wifi.V1_0.WifiDebugRingBufferFlags;
import android.hardware.wifi.V1_0.WifiDebugRingBufferStatus;
import android.hardware.wifi.V1_0.WifiStatus;
import android.hardware.wifi.V1_0.WifiStatusCode;
import android.hardware.wifi.V1_5.WifiBand;
import android.hardware.wifi.V1_5.WifiIfaceMode;
import android.hardware.wifi.V1_6.IfaceConcurrencyType;
import android.hardware.wifi.V1_6.WifiAntennaMode;
import android.hardware.wifi.V1_6.WifiRadioCombination;
import android.hardware.wifi.V1_6.WifiRadioConfiguration;
import android.net.wifi.CoexUnsafeChannel;
import android.net.wifi.OuiKeyedData;
import android.net.wifi.WifiAvailableChannel;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiScanner;
import android.os.RemoteException;
import android.util.Log;
import android.util.SparseIntArray;

import com.android.internal.annotations.VisibleForTesting;
import com.android.modules.utils.build.SdkLevel;
import com.android.server.wifi.SarInfo;
import com.android.server.wifi.SsidTranslator;
import com.android.server.wifi.WifiNative;
import com.android.server.wifi.WlanWakeReasonAndCounts;
import com.android.server.wifi.util.BitMask;
import com.android.server.wifi.util.GeneralUtil.Mutable;
import com.android.server.wifi.util.NativeUtil;
import com.android.wifi.resources.R;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

/**
 * HIDL implementation of the WifiChip interface.
 */
public class WifiChipHidlImpl implements IWifiChip {
    private static final String TAG = "WifiChipHidlImpl";
    private android.hardware.wifi.V1_0.IWifiChip mWifiChip;
    private android.hardware.wifi.V1_0.IWifiChipEventCallback mHalCallback10;
    private android.hardware.wifi.V1_2.IWifiChipEventCallback mHalCallback12;
    private android.hardware.wifi.V1_4.IWifiChipEventCallback mHalCallback14;
    private WifiChip.Callback mFrameworkCallback;
    private Context mContext;
    private SsidTranslator mSsidTranslator;
    private boolean mIsBridgedSoftApSupported;
    private boolean mIsStaWithBridgedSoftApConcurrencySupported;

    public WifiChipHidlImpl(@NonNull android.hardware.wifi.V1_0.IWifiChip chip,
            @NonNull Context context, @NonNull SsidTranslator ssidTranslator) {
        mWifiChip = chip;
        mContext = context;
        mSsidTranslator = ssidTranslator;
        Resources res = context.getResources();
        mIsBridgedSoftApSupported = res.getBoolean(R.bool.config_wifiBridgedSoftApSupported);
        mIsStaWithBridgedSoftApConcurrencySupported =
                res.getBoolean(R.bool.config_wifiStaWithBridgedSoftApConcurrencySupported);
    }

    /**
     * See comments for {@link IWifiChip#configureChip(int)}
     */
    @Override
    public boolean configureChip(int modeId) {
        String methodStr = "configureChip";
        return validateAndCall(methodStr, false,
                () -> configureChipInternal(methodStr, modeId));
    }

    /**
     * See comments for {@link IWifiChip#createApIface(List)}
     */
    @Override
    @Nullable
    public WifiApIface createApIface(@NonNull List<OuiKeyedData> vendorData) {
        String methodStr = "createApIface";
        return validateAndCall(methodStr, null,
                () -> createApIfaceInternal(methodStr));
    }

    /**
     * See comments for {@link IWifiChip#createBridgedApIface(List)}
     */
    @Override
    @Nullable
    public WifiApIface createBridgedApIface(@NonNull List<OuiKeyedData> vendorData) {
        String methodStr = "createBridgedApIface";
        return validateAndCall(methodStr, null,
                () -> createBridgedApIfaceInternal(methodStr));
    }

    /**
     * See comments for {@link IWifiChip#createNanIface()}
     */
    @Override
    @Nullable
    public WifiNanIface createNanIface() {
        String methodStr = "createNanIface";
        return validateAndCall(methodStr, null,
                () -> createNanIfaceInternal(methodStr));
    }

    /**
     * See comments for {@link IWifiChip#createP2pIface()}
     */
    @Override
    @Nullable
    public WifiP2pIface createP2pIface() {
        String methodStr = "createP2pIface";
        return validateAndCall(methodStr, null,
                () -> createP2pIfaceInternal(methodStr));
    }

    /**
     * See comments for {@link IWifiChip#createRttController()}
     */
    @Override
    @Nullable
    public WifiRttController createRttController() {
        String methodStr = "createRttController";
        return validateAndCall(methodStr, null,
                () -> createRttControllerInternal(methodStr));
    }

    /**
     * See comments for {@link IWifiChip#createStaIface()}
     */
    @Override
    @Nullable
    public WifiStaIface createStaIface() {
        String methodStr = "createStaIface";
        return validateAndCall(methodStr, null,
                () -> createStaIfaceInternal(methodStr));
    }

    /**
     * See comments for {@link IWifiChip#enableDebugErrorAlerts(boolean)}
     */
    @Override
    public boolean enableDebugErrorAlerts(boolean enable) {
        String methodStr = "enableDebugErrorAlerts";
        return validateAndCall(methodStr, false,
                () -> enableDebugErrorAlertsInternal(methodStr, enable));
    }

    /**
     * See comments for {@link IWifiChip#flushRingBufferToFile()}
     */
    @Override
    public boolean flushRingBufferToFile() {
        String methodStr = "flushRingBufferToFile";
        return validateAndCall(methodStr, false,
                () -> flushRingBufferToFileInternal(methodStr));
    }

    /**
     * See comments for {@link IWifiChip#forceDumpToDebugRingBuffer(String)}
     */
    @Override
    public boolean forceDumpToDebugRingBuffer(String ringName) {
        String methodStr = "forceDumpToDebugRingBuffer";
        return validateAndCall(methodStr, false,
                () -> forceDumpToDebugRingBufferInternal(methodStr, ringName));
    }

    /**
     * See comments for {@link IWifiChip#getApIface(String)}
     */
    @Override
    @Nullable
    public WifiApIface getApIface(String ifaceName) {
        String methodStr = "getApIface";
        return validateAndCall(methodStr, null,
                () -> getApIfaceInternal(methodStr, ifaceName));
    }

    /**
     * See comments for {@link IWifiChip#getApIfaceNames()}
     */
    @Override
    @Nullable
    public List<String> getApIfaceNames() {
        String methodStr = "getApIfaceNames";
        return validateAndCall(methodStr, null,
                () -> getApIfaceNamesInternal(methodStr));
    }

    /**
     * See comments for {@link IWifiChip#getAvailableModes()}
     */
    @Override
    @Nullable
    public List<WifiChip.ChipMode> getAvailableModes() {
        String methodStr = "getAvailableModes";
        return validateAndCall(methodStr, null,
                () -> getAvailableModesInternal(methodStr));
    }

    /**
     * See comments for {@link IWifiChip#getCapabilitiesBeforeIfacesExist()}
     */
    @Override
    public WifiChip.Response<Long> getCapabilitiesBeforeIfacesExist() {
        String methodStr = "getCapabilitiesBeforeIfacesExist";
        return validateAndCall(methodStr, new WifiChip.Response<>(0L),
                () -> getCapabilitiesBeforeIfacesExistInternal(methodStr));
    }

    /**
     * See comments for {@link IWifiChip#getCapabilitiesAfterIfacesExist()}
     */
    @Override
    public WifiChip.Response<Long> getCapabilitiesAfterIfacesExist() {
        String methodStr = "getCapabilitiesAfterIfacesExist";
        return validateAndCall(methodStr, new WifiChip.Response<>(0L),
                () -> getCapabilitiesAfterIfacesExistInternal(methodStr));
    }

    /**
     * See comments for {@link IWifiChip#getDebugHostWakeReasonStats()}
     */
    @Override
    @Nullable
    public WlanWakeReasonAndCounts getDebugHostWakeReasonStats() {
        String methodStr = "getDebugHostWakeReasonStats";
        return validateAndCall(methodStr, null,
                () -> getDebugHostWakeReasonStatsInternal(methodStr));
    }

    /**
     * See comments for {@link IWifiChip#getDebugRingBuffersStatus()}
     */
    @Override
    @Nullable
    public List<WifiNative.RingBufferStatus> getDebugRingBuffersStatus() {
        String methodStr = "getDebugRingBuffersStatus";
        return validateAndCall(methodStr, null,
                () -> getDebugRingBuffersStatusInternal(methodStr));
    }

    /**
     * See comments for {@link IWifiChip#getId()}
     */
    @Override
    public int getId() {
        String methodStr = "getId";
        return validateAndCall(methodStr, -1, () -> getIdInternal(methodStr));
    }

    /**
     * See comments for {@link IWifiChip#getMode()}
     */
    @Override
    public WifiChip.Response<Integer> getMode() {
        String methodStr = "getMode";
        return validateAndCall(methodStr, new WifiChip.Response<>(0),
                () -> getModeInternal(methodStr));
    }

    /**
     * See comments for {@link IWifiChip#getNanIface(String)}
     */
    @Override
    @Nullable
    public WifiNanIface getNanIface(String ifaceName) {
        String methodStr = "getNanIface";
        return validateAndCall(methodStr, null,
                () -> getNanIfaceInternal(methodStr, ifaceName));
    }

    /**
     * See comments for {@link IWifiChip#getNanIfaceNames()}
     */
    @Override
    @Nullable
    public List<String> getNanIfaceNames() {
        String methodStr = "getNanIfaceNames";
        return validateAndCall(methodStr, null,
                () -> getNanIfaceNamesInternal(methodStr));
    }

    /**
     * See comments for {@link IWifiChip#getP2pIface(String)}
     */
    @Override
    @Nullable
    public WifiP2pIface getP2pIface(String ifaceName) {
        String methodStr = "getP2pIface";
        return validateAndCall(methodStr, null,
                () -> getP2pIfaceInternal(methodStr, ifaceName));
    }

    /**
     * See comments for {@link IWifiChip#getP2pIfaceNames()}
     */
    @Override
    @Nullable
    public List<String> getP2pIfaceNames() {
        String methodStr = "getP2pIfaceNames";
        return validateAndCall(methodStr, null,
                () -> getP2pIfaceNamesInternal(methodStr));
    }

    /**
     * See comments for {@link IWifiChip#getStaIface(String)}
     */
    @Override
    @Nullable
    public WifiStaIface getStaIface(String ifaceName) {
        String methodStr = "getStaIface";
        return validateAndCall(methodStr, null,
                () -> getStaIfaceInternal(methodStr, ifaceName));
    }

    /**
     * See comments for {@link IWifiChip#getStaIfaceNames()}
     */
    @Override
    @Nullable
    public List<String> getStaIfaceNames() {
        String methodStr = "getStaIfaceNames";
        return validateAndCall(methodStr, null,
                () -> getStaIfaceNamesInternal(methodStr));
    }

    /**
     * See comments for {@link IWifiChip#getSupportedRadioCombinations()}
     */
    @Override
    @Nullable
    public List<WifiChip.WifiRadioCombination> getSupportedRadioCombinations() {
        String methodStr = "getSupportedRadioCombinations";
        return validateAndCall(methodStr, null,
                () -> getSupportedRadioCombinationsInternal(methodStr));
    }

    /**
     * See comments for {@link IWifiChip#getWifiChipCapabilities()}
     */
    public WifiChip.WifiChipCapabilities getWifiChipCapabilities() {
        return null;
    }

    /**
     * See comments for {@link IWifiChip#getUsableChannels(int, int, int)}
     */
    @Override
    @Nullable
    public List<WifiAvailableChannel> getUsableChannels(@WifiScanner.WifiBand int band,
            @WifiAvailableChannel.OpMode int mode, @WifiAvailableChannel.Filter int filter) {
        String methodStr = "getUsableChannels";
        return validateAndCall(methodStr, null,
                () -> getUsableChannelsInternal(methodStr, band, mode, filter));
    }

    /**
     * See comments for {@link IWifiChip#registerCallback(WifiChip.Callback)}
     */
    @Override
    public boolean registerCallback(WifiChip.Callback callback) {
        String methodStr = "registerCallback";
        return validateAndCall(methodStr, false,
                () -> registerCallbackInternal(methodStr, callback));
    }

    /**
     * See comments for {@link IWifiChip#removeApIface(String)}
     */
    @Override
    public boolean removeApIface(String ifaceName) {
        String methodStr = "removeApIface";
        return validateAndCall(methodStr, false,
                () -> removeApIfaceInternal(methodStr, ifaceName));
    }

    /**
     * See comments for {@link IWifiChip#removeIfaceInstanceFromBridgedApIface(String, String)}
     */
    @Override
    public boolean removeIfaceInstanceFromBridgedApIface(String brIfaceName, String ifaceName) {
        String methodStr = "removeIfaceInstanceFromBridgedApIface";
        return validateAndCall(methodStr, false,
                () -> removeIfaceInstanceFromBridgedApIfaceInternal(methodStr,
                        brIfaceName, ifaceName));
    }

    /**
     * See comments for {@link IWifiChip#removeNanIface(String)}
     */
    @Override
    public boolean removeNanIface(String ifaceName) {
        String methodStr = "removeNanIface";
        return validateAndCall(methodStr, false,
                () -> removeNanIfaceInternal(methodStr, ifaceName));
    }

    /**
     * See comments for {@link IWifiChip#removeP2pIface(String)}
     */
    @Override
    public boolean removeP2pIface(String ifaceName) {
        String methodStr = "removeP2pIface";
        return validateAndCall(methodStr, false,
                () -> removeP2pIfaceInternal(methodStr, ifaceName));
    }

    /**
     * See comments for {@link IWifiChip#removeStaIface(String)}
     */
    @Override
    public boolean removeStaIface(String ifaceName) {
        String methodStr = "removeStaIface";
        return validateAndCall(methodStr, false,
                () -> removeStaIfaceInternal(methodStr, ifaceName));
    }

    /**
     * See comments for {@link IWifiChip#requestChipDebugInfo()}
     */
    @Override
    @Nullable
    public WifiChip.ChipDebugInfo requestChipDebugInfo() {
        String methodStr = "requestChipDebugInfo";
        return validateAndCall(methodStr, null,
                () -> requestChipDebugInfoInternal(methodStr));
    }

    /**
     * See comments for {@link IWifiChip#requestDriverDebugDump()}
     */
    @Override
    @Nullable
    public byte[] requestDriverDebugDump() {
        String methodStr = "requestDriverDebugDump";
        return validateAndCall(methodStr, null,
                () -> requestDriverDebugDumpInternal(methodStr));
    }

    /**
     * See comments for {@link IWifiChip#requestFirmwareDebugDump()}
     */
    @Override
    @Nullable
    public byte[] requestFirmwareDebugDump() {
        String methodStr = "requestFirmwareDebugDump";
        return validateAndCall(methodStr, null,
                () -> requestFirmwareDebugDumpInternal(methodStr));
    }

    /**
     * See comments for {@link IWifiChip#selectTxPowerScenario(SarInfo)}
     */
    @Override
    public boolean selectTxPowerScenario(SarInfo sarInfo) {
        String methodStr = "selectTxPowerScenario";
        return validateAndCall(methodStr, false,
                () -> selectTxPowerScenarioInternal(methodStr, sarInfo));
    }

    /**
     * See comments for {@link IWifiChip#setCoexUnsafeChannels(List, int)}
     */
    @Override
    public boolean setCoexUnsafeChannels(List<CoexUnsafeChannel> unsafeChannels, int restrictions) {
        String methodStr = "setCoexUnsafeChannels";
        return validateAndCall(methodStr, false,
                () -> setCoexUnsafeChannelsInternal(methodStr, unsafeChannels,
                        restrictions));
    }

    /**
     * See comments for {@link IWifiChip#setCountryCode(byte[])}
     */
    @Override
    public boolean setCountryCode(byte[] code) {
        String methodStr = "setCountryCode";
        return validateAndCall(methodStr, false,
                () -> setCountryCodeInternal(methodStr, code));
    }

    /**
     * See comments for {@link IWifiChip#setLowLatencyMode(boolean)}
     */
    @Override
    public boolean setLowLatencyMode(boolean enable) {
        String methodStr = "setLowLatencyMode";
        return validateAndCall(methodStr, false,
                () -> setLowLatencyModeInternal(methodStr, enable));
    }

    /**
     * See comments for {@link IWifiChip#setMultiStaPrimaryConnection(String)}
     */
    @Override
    public boolean setMultiStaPrimaryConnection(String ifaceName) {
        String methodStr = "setMultiStaPrimaryConnection";
        return validateAndCall(methodStr, false,
                () -> setMultiStaPrimaryConnectionInternal(methodStr, ifaceName));
    }

    /**
     * See comments for {@link IWifiChip#setMultiStaUseCase(int)}
     */
    @Override
    public boolean setMultiStaUseCase(@WifiNative.MultiStaUseCase int useCase) {
        String methodStr = "setMultiStaUseCase";
        return validateAndCall(methodStr, false,
                () -> setMultiStaUseCaseInternal(methodStr, useCase));
    }

    /**
     * See comments for {@link IWifiChip#startLoggingToDebugRingBuffer(String, int, int, int)}
     */
    @Override
    public boolean startLoggingToDebugRingBuffer(String ringName, int verboseLevel,
            int maxIntervalInSec, int minDataSizeInBytes) {
        String methodStr = "startLoggingToDebugRingBuffer";
        return validateAndCall(methodStr, false,
                () -> startLoggingToDebugRingBufferInternal(methodStr, ringName,
                        verboseLevel, maxIntervalInSec, minDataSizeInBytes));
    }

    /**
     * See comments for {@link IWifiChip#stopLoggingToDebugRingBuffer()}
     */
    @Override
    public boolean stopLoggingToDebugRingBuffer() {
        String methodStr = "stopLoggingToDebugRingBuffer";
        return validateAndCall(methodStr, false,
                () -> stopLoggingToDebugRingBufferInternal(methodStr));
    }

    /**
     * See comments for {@link IWifiChip#triggerSubsystemRestart()}
     */
    @Override
    public boolean triggerSubsystemRestart() {
        String methodStr = "triggerSubsystemRestart";
        return validateAndCall(methodStr, false,
                () -> triggerSubsystemRestartInternal(methodStr));
    }

    /**
     * See comments for {@link IWifiChip#setMloMode(int)}.
     */
    @Override
    public @android.hardware.wifi.WifiStatusCode int setMloMode(@WifiManager.MloMode int mode) {
        return android.hardware.wifi.WifiStatusCode.ERROR_NOT_SUPPORTED;
    }

    /**
     * See comments for {@link IWifiChip#enableStaChannelForPeerNetwork(boolean, boolean)}
     */
    @Override
    public boolean enableStaChannelForPeerNetwork(boolean enableIndoorChannel,
            boolean enableDfsChannel) {
        Log.d(TAG, "enableStaChannelForPeerNetwork() is not implemented in hidl.");
        return false;
    }

    /**
     * See comments for {@link IWifiChip#setAfcChannelAllowance(WifiChip.AfcChannelAllowance)}
     */
    @Override
    public boolean setAfcChannelAllowance(WifiChip.AfcChannelAllowance afcChannelAllowance) {
        Log.d(TAG, "setAfcChannelAllowance() is not implemented in hidl.");
        return false;
    }

    // Internal Implementations

    private boolean configureChipInternal(String methodStr, int modeId) {
        try {
            WifiStatus status = mWifiChip.configureChip(modeId);
            return isOk(status, methodStr);
        } catch (RemoteException e) {
            handleRemoteException(e, methodStr);
            return false;
        }
    }

    private WifiApIface createApIfaceInternal(String methodStr) {
        Mutable<WifiApIface> ifaceResp = new Mutable<>();
        try {
            mWifiChip.createApIface((status, iface) -> {
                if (isOk(status, methodStr)) {
                    ifaceResp.value = new WifiApIface(iface);
                }
            });
        } catch (RemoteException e) {
            handleRemoteException(e, methodStr);
        }
        return ifaceResp.value;
    }

    private WifiApIface createBridgedApIfaceInternal(String methodStr) {
        Mutable<WifiApIface> ifaceResp = new Mutable<>();
        try {
            android.hardware.wifi.V1_5.IWifiChip chip15 = getWifiChipV1_5Mockable();
            if (chip15 == null) return null;
            chip15.createBridgedApIface((status, iface) -> {
                if (isOk(status, methodStr)) {
                    ifaceResp.value = new WifiApIface(iface);
                }
            });
        } catch (RemoteException e) {
            handleRemoteException(e, methodStr);
        }
        return ifaceResp.value;
    }

    private WifiNanIface createNanIfaceInternal(String methodStr) {
        Mutable<WifiNanIface> ifaceResp = new Mutable<>();
        try {
            mWifiChip.createNanIface((status, iface) -> {
                if (isOk(status, methodStr)) {
                    ifaceResp.value = new WifiNanIface(iface);
                }
            });
        } catch (RemoteException e) {
            handleRemoteException(e, methodStr);
        }
        return ifaceResp.value;
    }

    private WifiP2pIface createP2pIfaceInternal(String methodStr) {
        Mutable<WifiP2pIface> ifaceResp = new Mutable<>();
        try {
            mWifiChip.createP2pIface((status, iface) -> {
                if (isOk(status, methodStr)) {
                    ifaceResp.value = new WifiP2pIface(iface);
                }
            });
        } catch (RemoteException e) {
            handleRemoteException(e, methodStr);
        }
        return ifaceResp.value;
    }

    private WifiRttController createRttControllerInternal(String methodStr) {
        Mutable<WifiRttController> controllerResp = new Mutable<>();
        try {
            android.hardware.wifi.V1_6.IWifiChip chip16 = getWifiChipV1_6Mockable();
            android.hardware.wifi.V1_4.IWifiChip chip14 = getWifiChipV1_4Mockable();
            if (chip16 != null) {
                chip16.createRttController_1_6(null, (status, controller) -> {
                    if (isOk(status, methodStr)) {
                        controllerResp.value = new WifiRttController(controller);
                    }
                });
            } else if (chip14 != null) {
                chip14.createRttController_1_4(null, (status, controller) -> {
                    if (isOk(status, methodStr)) {
                        controllerResp.value = new WifiRttController(controller);
                    }
                });
            } else {
                mWifiChip.createRttController(null, (status, controller) -> {
                    if (isOk(status, methodStr)) {
                        controllerResp.value = new WifiRttController(controller);
                    }
                });
            }
        } catch (RemoteException e) {
            handleRemoteException(e, methodStr);
        }
        return controllerResp.value;
    }

    private WifiStaIface createStaIfaceInternal(String methodStr) {
        Mutable<WifiStaIface> ifaceResp = new Mutable<>();
        try {
            mWifiChip.createStaIface((status, iface) -> {
                if (isOk(status, methodStr)) {
                    ifaceResp.value = new WifiStaIface(iface, mContext, mSsidTranslator);
                }
            });
        } catch (RemoteException e) {
            handleRemoteException(e, methodStr);
        }
        return ifaceResp.value;
    }

    private boolean enableDebugErrorAlertsInternal(String methodStr, boolean enable) {
        try {
            WifiStatus status = mWifiChip.enableDebugErrorAlerts(enable);
            return isOk(status, methodStr);
        } catch (RemoteException e) {
            handleRemoteException(e, methodStr);
            return false;
        }
    }

    private boolean flushRingBufferToFileInternal(String methodStr) {
        try {
            android.hardware.wifi.V1_3.IWifiChip chip13 = getWifiChipV1_3Mockable();
            if (chip13 == null) return false;
            WifiStatus status = chip13.flushRingBufferToFile();
            return isOk(status, methodStr);
        } catch (RemoteException e) {
            handleRemoteException(e, methodStr);
            return false;
        }
    }

    private boolean forceDumpToDebugRingBufferInternal(String methodStr, String ringName) {
        try {
            WifiStatus status = mWifiChip.forceDumpToDebugRingBuffer(ringName);
            return isOk(status, methodStr);
        } catch (RemoteException e) {
            handleRemoteException(e, methodStr);
            return false;
        }
    }

    private WifiApIface getApIfaceInternal(String methodStr, String ifaceName) {
        Mutable<WifiApIface> ifaceResp = new Mutable<>();
        try {
            mWifiChip.getApIface(ifaceName, (status, iface) -> {
                if (isOk(status, methodStr)) {
                    ifaceResp.value = new WifiApIface(iface);
                }
            });
        } catch (RemoteException e) {
            handleRemoteException(e, methodStr);
        }
        return ifaceResp.value;
    }

    private List<String> getApIfaceNamesInternal(String methodStr) {
        Mutable<List<String>> ifaceNameResp = new Mutable<>();
        try {
            mWifiChip.getApIfaceNames((status, ifaceNames) -> {
                if (isOk(status, methodStr)) {
                    ifaceNameResp.value = ifaceNames;
                }
            });
        } catch (RemoteException e) {
            handleRemoteException(e, methodStr);
        }
        return ifaceNameResp.value;
    }

    private List<WifiChip.ChipMode> getAvailableModesInternal(String methodStr) {
        Mutable<List<WifiChip.ChipMode>> modeResp = new Mutable<>();
        try {
            android.hardware.wifi.V1_6.IWifiChip chip16 = getWifiChipV1_6Mockable();
            if (chip16 != null) {
                chip16.getAvailableModes_1_6((status, modes) -> {
                    if (isOk(status, methodStr)) {
                        modeResp.value = halToFrameworkChipModeListV1_6(modes);
                    }
                });
            } else {
                mWifiChip.getAvailableModes((status, modes) -> {
                    if (isOk(status, methodStr)) {
                        modeResp.value = halToFrameworkChipModeListV1_0(modes);
                    }
                });
            }
        } catch (RemoteException e) {
            handleRemoteException(e, methodStr);
        }
        return modeResp.value;
    }

    private WifiChip.Response<Long> getCapabilitiesBeforeIfacesExistInternal(String methodStr) {
        WifiChip.Response<Long> capsResp = new WifiChip.Response<>(0L);
        try {
            // HAL newer than v1.5 supports getting capabilities before creating an interface.
            android.hardware.wifi.V1_5.IWifiChip chip15 = getWifiChipV1_5Mockable();
            if (chip15 != null) {
                chip15.getCapabilities_1_5((status, caps) -> {
                    if (isOk(status, methodStr)) {
                        capsResp.setValue(wifiFeatureMaskFromChipCapabilities_1_5(caps));
                        capsResp.setStatusCode(WifiHal.WIFI_STATUS_SUCCESS);
                    } else {
                        capsResp.setStatusCode(
                                WifiHalHidlImpl.halToFrameworkWifiStatusCode(status.code));
                    }
                });
            }
        } catch (RemoteException e) {
            handleRemoteException(e, methodStr);
            capsResp.setStatusCode(WifiHal.WIFI_STATUS_ERROR_REMOTE_EXCEPTION);
        }
        return capsResp;
    }

    private WifiChip.Response<Long> getCapabilitiesAfterIfacesExistInternal(String methodStr) {
        WifiChip.Response<Long> capsResp = new WifiChip.Response<>(0L);
        try {
            android.hardware.wifi.V1_5.IWifiChip chip15 = getWifiChipV1_5Mockable();
            android.hardware.wifi.V1_3.IWifiChip chip13 = getWifiChipV1_3Mockable();
            if (chip15 != null) {
                chip15.getCapabilities_1_5((status, caps) -> {
                    if (isOk(status, methodStr)) {
                        capsResp.setValue(wifiFeatureMaskFromChipCapabilities_1_5(caps));
                        capsResp.setStatusCode(WifiHal.WIFI_STATUS_SUCCESS);
                    } else {
                        capsResp.setStatusCode(
                                WifiHalHidlImpl.halToFrameworkWifiStatusCode(status.code));
                    }
                });
            } else if (chip13 != null) {
                chip13.getCapabilities_1_3((status, caps) -> {
                    if (isOk(status, methodStr)) {
                        capsResp.setValue(wifiFeatureMaskFromChipCapabilities_1_3(caps));
                        capsResp.setStatusCode(WifiHal.WIFI_STATUS_SUCCESS);
                    } else {
                        capsResp.setStatusCode(
                                WifiHalHidlImpl.halToFrameworkWifiStatusCode(status.code));
                    }
                });
            } else {
                mWifiChip.getCapabilities((status, caps) -> {
                    if (isOk(status, methodStr)) {
                        capsResp.setValue((long) wifiFeatureMaskFromChipCapabilities(caps));
                        capsResp.setStatusCode(WifiHal.WIFI_STATUS_SUCCESS);
                    } else {
                        capsResp.setStatusCode(
                                WifiHalHidlImpl.halToFrameworkWifiStatusCode(status.code));
                    }
                });
            }
        } catch (RemoteException e) {
            handleRemoteException(e, methodStr);
            capsResp.setStatusCode(WifiHal.WIFI_STATUS_ERROR_REMOTE_EXCEPTION);
        }
        return capsResp;
    }

    private WlanWakeReasonAndCounts getDebugHostWakeReasonStatsInternal(String methodStr) {
        Mutable<WlanWakeReasonAndCounts> debugResp = new Mutable<>();
        try {
            mWifiChip.getDebugHostWakeReasonStats((status, debugStats) -> {
                if (isOk(status, methodStr)) {
                    debugResp.value = halToFrameworkWakeReasons(debugStats);
                }
            });
        } catch (RemoteException e) {
            handleRemoteException(e, methodStr);
        }
        return debugResp.value;
    }

    private List<WifiNative.RingBufferStatus> getDebugRingBuffersStatusInternal(String methodStr) {
        Mutable<List<WifiNative.RingBufferStatus>> ringBufResp = new Mutable<>();
        try {
            mWifiChip.getDebugRingBuffersStatus((status, ringBuffers) -> {
                if (isOk(status, methodStr)) {
                    WifiNative.RingBufferStatus[] ringBufArray =
                            makeRingBufferStatusArray(ringBuffers);
                    ringBufResp.value = Arrays.asList(ringBufArray);
                }
            });
        } catch (RemoteException e) {
            handleRemoteException(e, methodStr);
        }
        return ringBufResp.value;
    }

    private int getIdInternal(String methodStr) {
        Mutable<Integer> idResp = new Mutable<>(-1);
        try {
            mWifiChip.getId((status, id) -> {
                if (isOk(status, methodStr)) {
                    idResp.value = id;
                }
            });
        } catch (RemoteException e) {
            handleRemoteException(e, methodStr);
        }
        return idResp.value;
    }

    private WifiChip.Response<Integer> getModeInternal(String methodStr) {
        WifiChip.Response<Integer> modeResp = new WifiChip.Response<>(0);
        try {
            mWifiChip.getMode((status, mode) -> {
                if (isOk(status, methodStr)) {
                    modeResp.setValue(mode);
                    modeResp.setStatusCode(WifiHal.WIFI_STATUS_SUCCESS);
                } else {
                    modeResp.setStatusCode(
                            WifiHalHidlImpl.halToFrameworkWifiStatusCode(status.code));
                }
            });
        } catch (RemoteException e) {
            handleRemoteException(e, methodStr);
            modeResp.setStatusCode(WifiHal.WIFI_STATUS_ERROR_REMOTE_EXCEPTION);
        }
        return modeResp;
    }

    private WifiNanIface getNanIfaceInternal(String methodStr, String ifaceName) {
        Mutable<WifiNanIface> ifaceResp = new Mutable<>();
        try {
            mWifiChip.getNanIface(ifaceName, (status, iface) -> {
                if (isOk(status, methodStr)) {
                    ifaceResp.value = new WifiNanIface(iface);
                }
            });
        } catch (RemoteException e) {
            handleRemoteException(e, methodStr);
        }
        return ifaceResp.value;
    }

    private List<String> getNanIfaceNamesInternal(String methodStr) {
        Mutable<List<String>> ifaceNameResp = new Mutable<>();
        try {
            mWifiChip.getNanIfaceNames((status, ifaceNames) -> {
                if (isOk(status, methodStr)) {
                    ifaceNameResp.value = ifaceNames;
                }
            });
        } catch (RemoteException e) {
            handleRemoteException(e, methodStr);
        }
        return ifaceNameResp.value;
    }

    private WifiP2pIface getP2pIfaceInternal(String methodStr, String ifaceName) {
        Mutable<WifiP2pIface> ifaceResp = new Mutable<>();
        try {
            mWifiChip.getP2pIface(ifaceName, (status, iface) -> {
                if (isOk(status, methodStr)) {
                    ifaceResp.value = new WifiP2pIface(iface);
                }
            });
        } catch (RemoteException e) {
            handleRemoteException(e, methodStr);
        }
        return ifaceResp.value;
    }

    private List<String> getP2pIfaceNamesInternal(String methodStr) {
        Mutable<List<String>> ifaceNameResp = new Mutable<>();
        try {
            mWifiChip.getP2pIfaceNames((status, ifaceNames) -> {
                if (isOk(status, methodStr)) {
                    ifaceNameResp.value = ifaceNames;
                }
            });
        } catch (RemoteException e) {
            handleRemoteException(e, methodStr);
        }
        return ifaceNameResp.value;
    }

    private WifiStaIface getStaIfaceInternal(String methodStr, String ifaceName) {
        Mutable<WifiStaIface> ifaceResp = new Mutable<>();
        try {
            mWifiChip.getStaIface(ifaceName, (status, iface) -> {
                if (isOk(status, methodStr)) {
                    ifaceResp.value = new WifiStaIface(iface, mContext, mSsidTranslator);
                }
            });
        } catch (RemoteException e) {
            handleRemoteException(e, methodStr);
        }
        return ifaceResp.value;
    }

    private List<String> getStaIfaceNamesInternal(String methodStr) {
        Mutable<List<String>> ifaceNameResp = new Mutable<>();
        try {
            mWifiChip.getStaIfaceNames((status, ifaceNames) -> {
                if (isOk(status, methodStr)) {
                    ifaceNameResp.value = ifaceNames;
                }
            });
        } catch (RemoteException e) {
            handleRemoteException(e, methodStr);
        }
        return ifaceNameResp.value;
    }

    private List<WifiChip.WifiRadioCombination> getSupportedRadioCombinationsInternal(
            String methodStr) {
        Mutable<List<WifiChip.WifiRadioCombination>> radioComboResp = new Mutable<>();
        try {
            android.hardware.wifi.V1_6.IWifiChip chip16 = getWifiChipV1_6Mockable();
            if (chip16 == null) return null;
            chip16.getSupportedRadioCombinationsMatrix((status, matrix) -> {
                if (matrix != null) {
                    radioComboResp.value =
                            halToFrameworkRadioCombinations(matrix.radioCombinations);
                }
            });
        } catch (RemoteException e) {
            handleRemoteException(e, methodStr);
        }
        return radioComboResp.value;
    }

    private List<WifiAvailableChannel> getUsableChannelsInternal(String methodStr,
            @WifiScanner.WifiBand int band, @WifiAvailableChannel.OpMode int mode,
            @WifiAvailableChannel.Filter int filter) {
        Mutable<List<WifiAvailableChannel>> channelResp = new Mutable<>();
        try {
            android.hardware.wifi.V1_5.IWifiChip chip15 = getWifiChipV1_5Mockable();
            android.hardware.wifi.V1_6.IWifiChip chip16 = getWifiChipV1_6Mockable();
            if (chip15 == null && chip16 == null) return null;
            if (chip16 != null) {
                chip16.getUsableChannels_1_6(
                        frameworkToHalWifiBand(band),
                        frameworkToHalIfaceMode(mode),
                        frameworkToHalUsableFilter_1_6(filter),
                        (status, channels) -> {
                            if (isOk(status, methodStr)) {
                                channelResp.value = new ArrayList<>();
                                for (android.hardware.wifi.V1_6.WifiUsableChannel ch : channels) {
                                    channelResp.value.add(new WifiAvailableChannel(ch.channel,
                                            halToFrameworkIfaceMode(ch.ifaceModeMask)));
                                }
                            }
                        });
            } else {
                chip15.getUsableChannels(
                        frameworkToHalWifiBand(band),
                        frameworkToHalIfaceMode(mode),
                        frameworkToHalUsableFilter(filter),
                        (status, channels) -> {
                            if (isOk(status, methodStr)) {
                                channelResp.value = new ArrayList<>();
                                for (android.hardware.wifi.V1_5.WifiUsableChannel ch : channels) {
                                    channelResp.value.add(new WifiAvailableChannel(ch.channel,
                                            halToFrameworkIfaceMode(ch.ifaceModeMask)));
                                }
                            }
                        });
            }
        } catch (RemoteException e) {
            handleRemoteException(e, methodStr);
        }
        return channelResp.value;
    }

    private boolean registerCallbackInternal(String methodStr, WifiChip.Callback callback) {
        if (mFrameworkCallback != null) {
            Log.e(TAG, "Framework callback is already registered");
            return false;
        } else if (callback == null) {
            Log.e(TAG, "Cannot register a null callback");
            return false;
        }

        try {
            android.hardware.wifi.V1_2.IWifiChip chip12 = getWifiChipV1_2Mockable();
            android.hardware.wifi.V1_4.IWifiChip chip14 = getWifiChipV1_4Mockable();
            mHalCallback10 = new ChipEventCallback();
            WifiStatus status;
            if (chip14 != null) {
                mHalCallback12 = new ChipEventCallbackV12();
                mHalCallback14 = new ChipEventCallbackV14();
                status = chip14.registerEventCallback_1_4(mHalCallback14);
            } else if (chip12 != null) {
                mHalCallback12 = new ChipEventCallbackV12();
                status = chip12.registerEventCallback_1_2(mHalCallback12);
            } else {
                status = mWifiChip.registerEventCallback(mHalCallback10);
            }
            if (!isOk(status, methodStr)) return false;
            mFrameworkCallback = callback;
            return true;
        } catch (RemoteException e) {
            handleRemoteException(e, methodStr);
            return false;
        }
    }

    private boolean removeApIfaceInternal(String methodStr, String ifaceName) {
        try {
            WifiStatus status = mWifiChip.removeApIface(ifaceName);
            return isOk(status, methodStr);
        } catch (RemoteException e) {
            handleRemoteException(e, methodStr);
            return false;
        }
    }

    private boolean removeIfaceInstanceFromBridgedApIfaceInternal(String methodStr,
            String brIfaceName, String ifaceName) {
        try {
            android.hardware.wifi.V1_5.IWifiChip chip15 = getWifiChipV1_5Mockable();
            if (chip15 == null) return false;
            WifiStatus status =
                    chip15.removeIfaceInstanceFromBridgedApIface(brIfaceName, ifaceName);
            return isOk(status, methodStr);
        } catch (RemoteException e) {
            handleRemoteException(e, methodStr);
            return false;
        }
    }

    private boolean removeNanIfaceInternal(String methodStr, String ifaceName) {
        try {
            WifiStatus status = mWifiChip.removeNanIface(ifaceName);
            return isOk(status, methodStr);
        } catch (RemoteException e) {
            handleRemoteException(e, methodStr);
            return false;
        }
    }

    private boolean removeP2pIfaceInternal(String methodStr, String ifaceName) {
        try {
            WifiStatus status = mWifiChip.removeP2pIface(ifaceName);
            return isOk(status, methodStr);
        } catch (RemoteException e) {
            handleRemoteException(e, methodStr);
            return false;
        }
    }

    private boolean removeStaIfaceInternal(String methodStr, String ifaceName) {
        try {
            WifiStatus status = mWifiChip.removeStaIface(ifaceName);
            return isOk(status, methodStr);
        } catch (RemoteException e) {
            handleRemoteException(e, methodStr);
            return false;
        }
    }

    private WifiChip.ChipDebugInfo requestChipDebugInfoInternal(String methodStr) {
        Mutable<WifiChip.ChipDebugInfo> debugResp = new Mutable<>();
        try {
            mWifiChip.requestChipDebugInfo((status, halInfo) -> {
                if (isOk(status, methodStr)) {
                    debugResp.value = new WifiChip.ChipDebugInfo(
                            halInfo.driverDescription, halInfo.firmwareDescription);
                }
            });
        } catch (RemoteException e) {
            handleRemoteException(e, methodStr);
        }
        return debugResp.value;
    }

    private byte[] requestDriverDebugDumpInternal(String methodStr) {
        Mutable<byte[]> debugResp = new Mutable<>();
        try {
            mWifiChip.requestDriverDebugDump((status, blob) -> {
                if (isOk(status, methodStr)) {
                    debugResp.value = NativeUtil.byteArrayFromArrayList(blob);
                }
            });
        } catch (RemoteException e) {
            handleRemoteException(e, methodStr);
        }
        return debugResp.value;
    }

    private byte[] requestFirmwareDebugDumpInternal(String methodStr) {
        Mutable<byte[]> debugResp = new Mutable<>();
        try {
            mWifiChip.requestFirmwareDebugDump((status, blob) -> {
                if (isOk(status, methodStr)) {
                    debugResp.value = NativeUtil.byteArrayFromArrayList(blob);
                }
            });
        } catch (RemoteException e) {
            handleRemoteException(e, methodStr);
        }
        return debugResp.value;
    }

    private boolean selectTxPowerScenarioInternal(String methodStr, SarInfo sarInfo) {
        if (getWifiChipV1_2Mockable() != null) {
            return selectTxPowerScenarioInternal_1_2(methodStr, sarInfo);
        } else if (getWifiChipV1_1Mockable() != null) {
            return selectTxPowerScenarioInternal_1_1(methodStr, sarInfo);
        }
        return false;
    }

    private boolean selectTxPowerScenarioInternal_1_1(String methodStr, SarInfo sarInfo) {
        methodStr += "_1_1";
        try {
            WifiStatus status;
            android.hardware.wifi.V1_1.IWifiChip chip11 = getWifiChipV1_1Mockable();

            if (sarPowerBackoffRequired_1_1(sarInfo)) {
                // Power backoff is needed, so calculate the required scenario
                // and attempt to set it.
                int halScenario = frameworkToHalTxPowerScenario_1_1(sarInfo);
                if (sarInfo.setSarScenarioNeeded(halScenario)) {
                    Log.d(TAG, "Attempting to set SAR scenario to " + halScenario);
                    status = chip11.selectTxPowerScenario(halScenario);
                    return isOk(status, methodStr);
                }
                // Reaching here means setting SAR scenario would be redundant,
                // do nothing and return with success.
                return true;
            }

            // We don't need to perform power backoff, so attempt to reset the SAR scenario.
            if (sarInfo.resetSarScenarioNeeded()) {
                status = chip11.resetTxPowerScenario();
                Log.d(TAG, "Attempting to reset the SAR scenario");
                return isOk(status, methodStr);
            }

            // Resetting SAR scenario would be redundant; do nothing and return with success.
            return true;
        } catch (RemoteException e) {
            handleRemoteException(e, methodStr);
            return false;
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "IllegalArgumentException in " + methodStr);
            return false;
        }
    }

    private boolean selectTxPowerScenarioInternal_1_2(String methodStr, SarInfo sarInfo) {
        methodStr += "_1_2";
        try {
            WifiStatus status;
            android.hardware.wifi.V1_2.IWifiChip chip12 = getWifiChipV1_2Mockable();

            if (sarPowerBackoffRequired_1_2(sarInfo)) {
                // Power backoff is needed, so calculate the required scenario
                // and attempt to set it.
                int halScenario = frameworkToHalTxPowerScenario_1_2(sarInfo);
                if (sarInfo.setSarScenarioNeeded(halScenario)) {
                    Log.d(TAG, "Attempting to set SAR scenario to " + halScenario);
                    status = chip12.selectTxPowerScenario_1_2(halScenario);
                    return isOk(status, methodStr);
                }
                // Reaching here means setting SAR scenario would be redundant,
                // do nothing and return with success.
                return true;
            }

            // We don't need to perform power backoff, so attempt to reset the SAR scenario.
            if (sarInfo.resetSarScenarioNeeded()) {
                status = chip12.resetTxPowerScenario();
                Log.d(TAG, "Attempting to reset the SAR scenario");
                return isOk(status, methodStr);
            }

            // Resetting SAR scenario would be redundant; do nothing and return with success.
            return true;
        } catch (RemoteException e) {
            handleRemoteException(e, methodStr);
            return false;
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "IllegalArgumentException in " + methodStr);
            return false;
        }
    }

    private boolean setCoexUnsafeChannelsInternal(String methodStr,
            List<CoexUnsafeChannel> unsafeChannels, int restrictions) {
        try {
            android.hardware.wifi.V1_5.IWifiChip chip15 = getWifiChipV1_5Mockable();
            if (chip15 == null) return false;
            WifiStatus status = chip15.setCoexUnsafeChannels(
                    frameworkCoexUnsafeChannelsToHidl(unsafeChannels),
                    frameworkCoexRestrictionsToHidl(restrictions));
            return isOk(status, methodStr);
        } catch (RemoteException e) {
            handleRemoteException(e, methodStr);
            return false;
        }
    }

    private boolean setCountryCodeInternal(String methodStr, byte[] code) {
        try {
            android.hardware.wifi.V1_5.IWifiChip chip15 = getWifiChipV1_5Mockable();
            if (chip15 == null) return false;
            WifiStatus status = chip15.setCountryCode(code);
            return isOk(status, methodStr);
        } catch (RemoteException e) {
            handleRemoteException(e, methodStr);
            return false;
        }
    }

    private boolean setLowLatencyModeInternal(String methodStr, boolean enable) {
        try {
            android.hardware.wifi.V1_3.IWifiChip chip13 = getWifiChipV1_3Mockable();
            if (chip13 == null) return false;
            int mode;
            if (enable) {
                mode = android.hardware.wifi.V1_3.IWifiChip.LatencyMode.LOW;
            } else {
                mode = android.hardware.wifi.V1_3.IWifiChip.LatencyMode.NORMAL;
            }
            WifiStatus status = chip13.setLatencyMode(mode);
            return isOk(status, methodStr);
        } catch (RemoteException e) {
            handleRemoteException(e, methodStr);
            return false;
        }
    }

    private boolean setMultiStaPrimaryConnectionInternal(String methodStr, String ifaceName) {
        try {
            android.hardware.wifi.V1_5.IWifiChip chip15 = getWifiChipV1_5Mockable();
            if (chip15 == null) return false;
            WifiStatus status = chip15.setMultiStaPrimaryConnection(ifaceName);
            return isOk(status, methodStr);
        } catch (RemoteException e) {
            handleRemoteException(e, methodStr);
            return false;
        }
    }

    private boolean setMultiStaUseCaseInternal(String methodStr,
            @WifiNative.MultiStaUseCase int useCase) {
        try {
            android.hardware.wifi.V1_5.IWifiChip chip15 = getWifiChipV1_5Mockable();
            if (chip15 == null) return false;
            WifiStatus status = chip15.setMultiStaUseCase(frameworkMultiStaUseCaseToHidl(useCase));
            return isOk(status, methodStr);
        } catch (RemoteException e) {
            handleRemoteException(e, methodStr);
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Invalid argument " + useCase + " in " + methodStr);
        }
        return false;
    }

    private boolean startLoggingToDebugRingBufferInternal(String methodStr, String ringName,
            int verboseLevel, int maxIntervalInSec, int minDataSizeInBytes) {
        try {
            WifiStatus status = mWifiChip.startLoggingToDebugRingBuffer(ringName, verboseLevel,
                    maxIntervalInSec, minDataSizeInBytes);
            return isOk(status, methodStr);
        } catch (RemoteException e) {
            handleRemoteException(e, methodStr);
            return false;
        }
    }

    private boolean stopLoggingToDebugRingBufferInternal(String methodStr) {
        try {
            WifiStatus status = mWifiChip.stopLoggingToDebugRingBuffer();
            return isOk(status, methodStr);
        } catch (RemoteException e) {
            handleRemoteException(e, methodStr);
            return false;
        }
    }

    private boolean triggerSubsystemRestartInternal(String methodStr) {
        try {
            android.hardware.wifi.V1_5.IWifiChip chip15 = getWifiChipV1_5Mockable();
            if (chip15 == null) return false;
            WifiStatus status = chip15.triggerSubsystemRestart();
            return isOk(status, methodStr);
        } catch (RemoteException e) {
            handleRemoteException(e, methodStr);
            return false;
        }
    }

    /**
     * Callback for events on the chip.
     */
    private class ChipEventCallback extends IWifiChipEventCallback.Stub {
        @Override
        public void onChipReconfigured(int modeId) {
            if (mFrameworkCallback == null) return;
            mFrameworkCallback.onChipReconfigured(modeId);
        }

        @Override
        public void onChipReconfigureFailure(WifiStatus status) {
            if (mFrameworkCallback == null) return;
            mFrameworkCallback.onChipReconfigureFailure(
                    WifiHalHidlImpl.halToFrameworkWifiStatusCode(status.code));
        }

        @Override
        public void onIfaceAdded(int type, String name) {
            if (mFrameworkCallback == null) return;
            mFrameworkCallback.onIfaceAdded(halToFrameworkIfaceType(type), name);
        }

        @Override
        public void onIfaceRemoved(int type, String name) {
            if (mFrameworkCallback == null) return;
            mFrameworkCallback.onIfaceRemoved(halToFrameworkIfaceType(type), name);
        }

        @Override
        public void onDebugRingBufferDataAvailable(
                WifiDebugRingBufferStatus status, java.util.ArrayList<Byte> data) {
            if (mFrameworkCallback == null) return;
            mFrameworkCallback.onDebugRingBufferDataAvailable(
                    halToFrameworkRingBufferStatus(status),
                    NativeUtil.byteArrayFromArrayList(data));
        }

        @Override
        public void onDebugErrorAlert(int errorCode, java.util.ArrayList<Byte> debugData) {
            if (mFrameworkCallback == null) return;
            mFrameworkCallback.onDebugErrorAlert(errorCode,
                    NativeUtil.byteArrayFromArrayList(debugData));
        }
    }

    /**
     * Callback for events on the 1.2 chip.
     */
    private class ChipEventCallbackV12 extends
            android.hardware.wifi.V1_2.IWifiChipEventCallback.Stub {
        @Override
        public void onChipReconfigured(int modeId) throws RemoteException {
            mHalCallback10.onChipReconfigured(modeId);
        }

        @Override
        public void onChipReconfigureFailure(WifiStatus status) throws RemoteException {
            mHalCallback10.onChipReconfigureFailure(status);
        }

        public void onIfaceAdded(int type, String name) throws RemoteException {
            mHalCallback10.onIfaceAdded(type, name);
        }

        @Override
        public void onIfaceRemoved(int type, String name) throws RemoteException {
            mHalCallback10.onIfaceRemoved(type, name);
        }

        @Override
        public void onDebugRingBufferDataAvailable(
                WifiDebugRingBufferStatus status, java.util.ArrayList<Byte> data)
                throws RemoteException {
            mHalCallback10.onDebugRingBufferDataAvailable(status, data);
        }

        @Override
        public void onDebugErrorAlert(int errorCode, java.util.ArrayList<Byte> debugData)
                throws RemoteException {
            mHalCallback10.onDebugErrorAlert(errorCode, debugData);
        }

        @Override
        public void onRadioModeChange(ArrayList<RadioModeInfo> radioModeInfoList) {
            if (mFrameworkCallback == null) return;
            List<WifiChip.RadioModeInfo> frameworkRadioModeInfos = new ArrayList<>();
            for (RadioModeInfo radioInfo : radioModeInfoList) {
                List<WifiChip.IfaceInfo> frameworkIfaceInfos = new ArrayList<>();
                for (IfaceInfo ifaceInfo : radioInfo.ifaceInfos) {
                    frameworkIfaceInfos.add(
                            new WifiChip.IfaceInfo(ifaceInfo.name, ifaceInfo.channel));
                }
                frameworkRadioModeInfos.add(
                        new WifiChip.RadioModeInfo(
                                radioInfo.radioId, radioInfo.bandInfo, frameworkIfaceInfos));
            }
            mFrameworkCallback.onRadioModeChange(frameworkRadioModeInfos);
        }
    }

    /**
     * Callback for events on the 1.4 chip.
     */
    private class ChipEventCallbackV14 extends
            android.hardware.wifi.V1_4.IWifiChipEventCallback.Stub {
        @Override
        public void onChipReconfigured(int modeId) throws RemoteException {
            mHalCallback10.onChipReconfigured(modeId);
        }

        @Override
        public void onChipReconfigureFailure(WifiStatus status) throws RemoteException {
            mHalCallback10.onChipReconfigureFailure(status);
        }

        public void onIfaceAdded(int type, String name) throws RemoteException {
            mHalCallback10.onIfaceAdded(type, name);
        }

        @Override
        public void onIfaceRemoved(int type, String name) throws RemoteException {
            mHalCallback10.onIfaceRemoved(type, name);
        }

        @Override
        public void onDebugRingBufferDataAvailable(
                WifiDebugRingBufferStatus status, java.util.ArrayList<Byte> data)
                throws RemoteException {
            mHalCallback10.onDebugRingBufferDataAvailable(status, data);
        }

        @Override
        public void onDebugErrorAlert(int errorCode, java.util.ArrayList<Byte> debugData)
                throws RemoteException {
            mHalCallback10.onDebugErrorAlert(errorCode, debugData);
        }

        @Override
        public void onRadioModeChange(
                ArrayList<android.hardware.wifi.V1_2.IWifiChipEventCallback.RadioModeInfo>
                        radioModeInfoList) throws RemoteException {
            mHalCallback12.onRadioModeChange(radioModeInfoList);
        }

        @Override
        public void onRadioModeChange_1_4(ArrayList<RadioModeInfo> radioModeInfoList) {
            if (mFrameworkCallback == null) return;
            List<WifiChip.RadioModeInfo> frameworkRadioModeInfos = new ArrayList<>();
            for (RadioModeInfo radioInfo : radioModeInfoList) {
                List<WifiChip.IfaceInfo> frameworkIfaceInfos = new ArrayList<>();
                for (IfaceInfo ifaceInfo : radioInfo.ifaceInfos) {
                    frameworkIfaceInfos.add(
                            new WifiChip.IfaceInfo(ifaceInfo.name, ifaceInfo.channel));
                }
                frameworkRadioModeInfos.add(
                        new WifiChip.RadioModeInfo(
                                radioInfo.radioId, radioInfo.bandInfo, frameworkIfaceInfos));
            }
            mFrameworkCallback.onRadioModeChange(frameworkRadioModeInfos);
        }
    }


    // Helper Functions

    protected boolean isBridgedSoftApSupportedMockable() {
        return mIsBridgedSoftApSupported;
    }

    protected boolean isStaWithBridgedSoftApConcurrencySupportedMockable() {
        return mIsStaWithBridgedSoftApConcurrencySupported;
    }

    private static final SparseIntArray IFACE_TYPE_TO_CONCURRENCY_TYPE_MAP = new SparseIntArray() {{
                put(IfaceType.STA, android.hardware.wifi.V1_6.IfaceConcurrencyType.STA);
                put(IfaceType.AP, android.hardware.wifi.V1_6.IfaceConcurrencyType.AP);
                put(IfaceType.P2P, android.hardware.wifi.V1_6.IfaceConcurrencyType.P2P);
                put(IfaceType.NAN, android.hardware.wifi.V1_6.IfaceConcurrencyType.NAN);
            }};

    private List<android.hardware.wifi.V1_6.IWifiChip.ChipMode> upgradeV1_0ChipModesToV1_6(
            List<android.hardware.wifi.V1_0.IWifiChip.ChipMode> oldChipModes) {
        ArrayList<android.hardware.wifi.V1_6.IWifiChip.ChipMode> newChipModes = new ArrayList<>();
        for (android.hardware.wifi.V1_0.IWifiChip.ChipMode oldChipMode : oldChipModes) {
            android.hardware.wifi.V1_6.IWifiChip.ChipMode newChipMode =
                    new android.hardware.wifi.V1_6.IWifiChip.ChipMode();
            newChipMode.id = oldChipMode.id;
            newChipMode.availableCombinations = new ArrayList<>();
            for (android.hardware.wifi.V1_0.IWifiChip.ChipIfaceCombination oldCombo
                    : oldChipMode.availableCombinations) {
                android.hardware.wifi.V1_6.IWifiChip.ChipConcurrencyCombination
                        newCombo = new android.hardware.wifi.V1_6.IWifiChip
                                .ChipConcurrencyCombination();
                newCombo.limits = new ArrayList<>();
                // Define a duplicate combination list with AP converted to AP_BRIDGED
                android.hardware.wifi.V1_6.IWifiChip.ChipConcurrencyCombination
                        newComboWithBridgedAp =
                        new android.hardware.wifi.V1_6.IWifiChip.ChipConcurrencyCombination();
                newComboWithBridgedAp.limits = new ArrayList<>();
                android.hardware.wifi.V1_6.IWifiChip.ChipConcurrencyCombinationLimit
                        bridgedApLimit = new android.hardware.wifi.V1_6.IWifiChip
                                .ChipConcurrencyCombinationLimit();
                bridgedApLimit.maxIfaces = 1;
                bridgedApLimit.types = new ArrayList<>();
                bridgedApLimit.types.add(IfaceConcurrencyType.AP_BRIDGED);
                newComboWithBridgedAp.limits.add(bridgedApLimit);

                boolean apInCombo = false;
                // Populate both the combo with AP_BRIDGED and the combo without AP_BRIDGED
                for (android.hardware.wifi.V1_0.IWifiChip.ChipIfaceCombinationLimit oldLimit
                        : oldCombo.limits) {
                    android.hardware.wifi.V1_6.IWifiChip.ChipConcurrencyCombinationLimit newLimit =
                            new android.hardware.wifi.V1_6
                                    .IWifiChip.ChipConcurrencyCombinationLimit();
                    newLimit.types = new ArrayList<>();
                    newLimit.maxIfaces = oldLimit.maxIfaces;
                    for (int oldType : oldLimit.types) {
                        newLimit.types.add(IFACE_TYPE_TO_CONCURRENCY_TYPE_MAP.get(oldType));
                    }
                    newCombo.limits.add(newLimit);

                    android.hardware.wifi.V1_6.IWifiChip.ChipConcurrencyCombinationLimit
                            newLimitForBridgedApCombo = new android.hardware.wifi.V1_6.IWifiChip
                                    .ChipConcurrencyCombinationLimit();
                    newLimitForBridgedApCombo.types = new ArrayList<>(newLimit.types);
                    newLimitForBridgedApCombo.maxIfaces = newLimit.maxIfaces;
                    if (newLimitForBridgedApCombo.types.contains(IfaceConcurrencyType.AP)) {
                        // Skip the limit if it contains AP, since this corresponds to the
                        // AP_BRIDGED in the duplicate AP_BRIDGED combo.
                        apInCombo = true;
                    } else if (!isStaWithBridgedSoftApConcurrencySupportedMockable()
                            && newLimitForBridgedApCombo.types.contains(IfaceConcurrencyType.STA)) {
                        // Don't include STA in the AP_BRIDGED combo if STA + AP_BRIDGED is not
                        // supported.
                        newLimitForBridgedApCombo.types.remove((Integer) IfaceConcurrencyType.STA);
                        if (!newLimitForBridgedApCombo.types.isEmpty()) {
                            newComboWithBridgedAp.limits.add(newLimitForBridgedApCombo);
                        }
                    } else {
                        newComboWithBridgedAp.limits.add(newLimitForBridgedApCombo);
                    }
                }
                newChipMode.availableCombinations.add(newCombo);
                if (isBridgedSoftApSupportedMockable() && apInCombo) {
                    newChipMode.availableCombinations.add(newComboWithBridgedAp);
                }
            }
            newChipModes.add(newChipMode);
        }
        return newChipModes;
    }

    private List<WifiChip.ChipMode> halToFrameworkChipModeListV1_0(
            List<android.hardware.wifi.V1_0.IWifiChip.ChipMode> halModes) {
        List<android.hardware.wifi.V1_6.IWifiChip.ChipMode> modes16 =
                upgradeV1_0ChipModesToV1_6(halModes);
        return halToFrameworkChipModeListV1_6(modes16);
    }

    private static List<WifiChip.ChipMode> halToFrameworkChipModeListV1_6(
            List<android.hardware.wifi.V1_6.IWifiChip.ChipMode> halModes) {
        List<WifiChip.ChipMode> frameworkModes = new ArrayList<>();
        for (android.hardware.wifi.V1_6.IWifiChip.ChipMode halMode : halModes) {
            frameworkModes.add(halToFrameworkChipModeV1_6(halMode));
        }
        return frameworkModes;
    }

    private static WifiChip.ChipMode halToFrameworkChipModeV1_6(
            android.hardware.wifi.V1_6.IWifiChip.ChipMode halMode) {
        List<WifiChip.ChipConcurrencyCombination> frameworkCombos = new ArrayList<>();
        for (android.hardware.wifi.V1_6.IWifiChip.ChipConcurrencyCombination halCombo :
                halMode.availableCombinations) {
            frameworkCombos.add(halToFrameworkChipConcurrencyCombinationV1_6(halCombo));
        }
        return new WifiChip.ChipMode(halMode.id, frameworkCombos);
    }

    private static WifiChip.ChipConcurrencyCombination halToFrameworkChipConcurrencyCombinationV1_6(
            android.hardware.wifi.V1_6.IWifiChip.ChipConcurrencyCombination halCombo) {
        List<WifiChip.ChipConcurrencyCombinationLimit> frameworkLimits = new ArrayList<>();
        for (android.hardware.wifi.V1_6.IWifiChip.ChipConcurrencyCombinationLimit halLimit :
                halCombo.limits) {
            frameworkLimits.add(halToFrameworkChipConcurrencyCombinationLimitV1_6(halLimit));
        }
        return new WifiChip.ChipConcurrencyCombination(frameworkLimits);
    }

    private static WifiChip.ChipConcurrencyCombinationLimit
            halToFrameworkChipConcurrencyCombinationLimitV1_6(
            android.hardware.wifi.V1_6.IWifiChip.ChipConcurrencyCombinationLimit halLimit) {
        List<Integer> frameworkTypes = new ArrayList<>();
        for (int halType : halLimit.types) {
            frameworkTypes.add(halToFrameworkIfaceConcurrencyType(halType));
        }
        return new WifiChip.ChipConcurrencyCombinationLimit(halLimit.maxIfaces, frameworkTypes);
    }

    private static @WifiChip.IfaceConcurrencyType int halToFrameworkIfaceConcurrencyType(int type) {
        switch (type) {
            case IfaceConcurrencyType.STA:
                return WifiChip.IFACE_CONCURRENCY_TYPE_STA;
            case IfaceConcurrencyType.AP:
                return WifiChip.IFACE_CONCURRENCY_TYPE_AP;
            case IfaceConcurrencyType.AP_BRIDGED:
                return WifiChip.IFACE_CONCURRENCY_TYPE_AP_BRIDGED;
            case IfaceConcurrencyType.P2P:
                return WifiChip.IFACE_CONCURRENCY_TYPE_P2P;
            case IfaceConcurrencyType.NAN:
                return WifiChip.IFACE_CONCURRENCY_TYPE_NAN;
            default:
                Log.e(TAG, "Invalid IfaceConcurrencyType received: " + type);
                return -1;
        }
    }

    private static @WifiChip.IfaceType int halToFrameworkIfaceType(int type) {
        switch (type) {
            case IfaceType.STA:
                return WifiChip.IFACE_TYPE_STA;
            case IfaceType.AP:
                return WifiChip.IFACE_TYPE_AP;
            case IfaceType.P2P:
                return WifiChip.IFACE_TYPE_P2P;
            case IfaceType.NAN:
                return WifiChip.IFACE_TYPE_NAN;
            default:
                Log.e(TAG, "Invalid IfaceType received: " + type);
                return -1;
        }
    }

    private static final long[][] sChipFeatureCapabilityTranslation = {
            {WifiManager.WIFI_FEATURE_TX_POWER_LIMIT,
                    android.hardware.wifi.V1_1.IWifiChip.ChipCapabilityMask.SET_TX_POWER_LIMIT
            },
            {WifiManager.WIFI_FEATURE_D2D_RTT,
                    android.hardware.wifi.V1_1.IWifiChip.ChipCapabilityMask.D2D_RTT
            },
            {WifiManager.WIFI_FEATURE_D2AP_RTT,
                    android.hardware.wifi.V1_1.IWifiChip.ChipCapabilityMask.D2AP_RTT
            }
    };

    /**
     * Translation table used by getSupportedFeatureSet for translating IWifiChip caps for
     * additional capabilities introduced in V1.5
     */
    private static final long[][] sChipFeatureCapabilityTranslation15 = {
            {WifiManager.WIFI_FEATURE_INFRA_60G,
                    android.hardware.wifi.V1_5.IWifiChip.ChipCapabilityMask.WIGIG
            }
    };

    /**
     * Translation table used by getSupportedFeatureSet for translating IWifiChip caps for
     * additional capabilities introduced in V1.3
     */
    private static final long[][] sChipFeatureCapabilityTranslation13 = {
            {WifiManager.WIFI_FEATURE_LOW_LATENCY,
                    android.hardware.wifi.V1_3.IWifiChip.ChipCapabilityMask.SET_LATENCY_MODE
            },
            {WifiManager.WIFI_FEATURE_P2P_RAND_MAC,
                    android.hardware.wifi.V1_3.IWifiChip.ChipCapabilityMask.P2P_RAND_MAC
            }

    };

    /**
     * Feature bit mask translation for Chip V1.1
     *
     * @param capabilities bitmask defined IWifiChip.ChipCapabilityMask
     * @return bitmask defined by WifiManager.WIFI_FEATURE_*
     */
    @VisibleForTesting
    int wifiFeatureMaskFromChipCapabilities(int capabilities) {
        int features = 0;
        for (int i = 0; i < sChipFeatureCapabilityTranslation.length; i++) {
            if ((capabilities & sChipFeatureCapabilityTranslation[i][1]) != 0) {
                features |= sChipFeatureCapabilityTranslation[i][0];
            }
        }
        return features;
    }

    /**
     * Feature bit mask translation for Chip V1.5
     *
     * @param capabilities bitmask defined IWifiChip.ChipCapabilityMask
     * @return bitmask defined by WifiManager.WIFI_FEATURE_*
     */
    @VisibleForTesting
    long wifiFeatureMaskFromChipCapabilities_1_5(int capabilities) {
        // First collect features from previous versions
        long features = wifiFeatureMaskFromChipCapabilities_1_3(capabilities);

        // Next collect features for V1_5 version
        for (int i = 0; i < sChipFeatureCapabilityTranslation15.length; i++) {
            if ((capabilities & sChipFeatureCapabilityTranslation15[i][1]) != 0) {
                features |= sChipFeatureCapabilityTranslation15[i][0];
            }
        }
        return features;
    }

    /**
     * Feature bit mask translation for Chip V1.3
     *
     * @param capabilities bitmask defined IWifiChip.ChipCapabilityMask
     * @return bitmask defined by WifiManager.WIFI_FEATURE_*
     */
    @VisibleForTesting
    long wifiFeatureMaskFromChipCapabilities_1_3(int capabilities) {
        // First collect features from previous versions
        long features = wifiFeatureMaskFromChipCapabilities(capabilities);

        // Next collect features for V1_3 version
        for (int i = 0; i < sChipFeatureCapabilityTranslation13.length; i++) {
            if ((capabilities & sChipFeatureCapabilityTranslation13[i][1]) != 0) {
                features |= sChipFeatureCapabilityTranslation13[i][0];
            }
        }
        return features;
    }

    /**
     * Translates from Hal version of wake reason stats to the framework version of same
     *
     * @param h - Hal version of wake reason stats
     * @return framework version of same
     */
    private static WlanWakeReasonAndCounts halToFrameworkWakeReasons(
            WifiDebugHostWakeReasonStats h) {
        if (h == null) return null;
        WlanWakeReasonAndCounts ans = new WlanWakeReasonAndCounts();
        ans.totalCmdEventWake = h.totalCmdEventWakeCnt;
        ans.totalDriverFwLocalWake = h.totalDriverFwLocalWakeCnt;
        ans.totalRxDataWake = h.totalRxPacketWakeCnt;
        ans.rxUnicast = h.rxPktWakeDetails.rxUnicastCnt;
        ans.rxMulticast = h.rxPktWakeDetails.rxMulticastCnt;
        ans.rxBroadcast = h.rxPktWakeDetails.rxBroadcastCnt;
        ans.icmp = h.rxIcmpPkWakeDetails.icmpPkt;
        ans.icmp6 = h.rxIcmpPkWakeDetails.icmp6Pkt;
        ans.icmp6Ra = h.rxIcmpPkWakeDetails.icmp6Ra;
        ans.icmp6Na = h.rxIcmpPkWakeDetails.icmp6Na;
        ans.icmp6Ns = h.rxIcmpPkWakeDetails.icmp6Ns;
        ans.ipv4RxMulticast = h.rxMulticastPkWakeDetails.ipv4RxMulticastAddrCnt;
        ans.ipv6Multicast = h.rxMulticastPkWakeDetails.ipv6RxMulticastAddrCnt;
        ans.otherRxMulticast = h.rxMulticastPkWakeDetails.otherRxMulticastAddrCnt;
        ans.cmdEventWakeCntArray = intsFromArrayList(h.cmdEventWakeCntPerType);
        ans.driverFWLocalWakeCntArray = intsFromArrayList(h.driverFwLocalWakeCntPerType);
        return ans;
    }

    /**
     * Creates array of RingBufferStatus from the Hal version
     */
    private static WifiNative.RingBufferStatus[] makeRingBufferStatusArray(
            ArrayList<WifiDebugRingBufferStatus> ringBuffers) {
        WifiNative.RingBufferStatus[] ans = new WifiNative.RingBufferStatus[ringBuffers.size()];
        int i = 0;
        for (WifiDebugRingBufferStatus b : ringBuffers) {
            ans[i++] = halToFrameworkRingBufferStatus(b);
        }
        return ans;
    }

    /**
     * Creates RingBufferStatus from the Hal version
     */
    private static WifiNative.RingBufferStatus halToFrameworkRingBufferStatus(
            WifiDebugRingBufferStatus h) {
        WifiNative.RingBufferStatus ans = new WifiNative.RingBufferStatus();
        ans.name = h.ringName;
        ans.flag = frameworkRingBufferFlagsFromHal(h.flags);
        ans.ringBufferId = h.ringId;
        ans.ringBufferByteSize = h.sizeInBytes;
        ans.verboseLevel = h.verboseLevel;
        // Remaining fields are unavailable
        //  writtenBytes;
        //  readBytes;
        //  writtenRecords;
        return ans;
    }

    /**
     * Translates a hal wifiDebugRingBufferFlag to the WifiNative version
     */
    private static int frameworkRingBufferFlagsFromHal(int wifiDebugRingBufferFlag) {
        BitMask checkoff = new BitMask(wifiDebugRingBufferFlag);
        int flags = 0;
        if (checkoff.testAndClear(WifiDebugRingBufferFlags.HAS_BINARY_ENTRIES)) {
            flags |= WifiNative.RingBufferStatus.HAS_BINARY_ENTRIES;
        }
        if (checkoff.testAndClear(WifiDebugRingBufferFlags.HAS_ASCII_ENTRIES)) {
            flags |= WifiNative.RingBufferStatus.HAS_ASCII_ENTRIES;
        }
        if (checkoff.testAndClear(WifiDebugRingBufferFlags.HAS_PER_PACKET_ENTRIES)) {
            flags |= WifiNative.RingBufferStatus.HAS_PER_PACKET_ENTRIES;
        }
        if (checkoff.value != 0) {
            throw new IllegalArgumentException("Unknown WifiDebugRingBufferFlag " + checkoff.value);
        }
        return flags;
    }

    private static List<WifiChip.WifiRadioCombination> halToFrameworkRadioCombinations(
            List<WifiRadioCombination> halCombos) {
        List<WifiChip.WifiRadioCombination> frameworkCombos = new ArrayList<>();
        for (WifiRadioCombination combo : halCombos) {
            frameworkCombos.add(halToFrameworkRadioCombination(combo));
        }
        return frameworkCombos;
    }

    private static WifiChip.WifiRadioCombination halToFrameworkRadioCombination(
            WifiRadioCombination halCombo) {
        List<WifiChip.WifiRadioConfiguration> frameworkConfigs = new ArrayList<>();
        for (WifiRadioConfiguration config : halCombo.radioConfigurations) {
            frameworkConfigs.add(halToFrameworkRadioConfiguration(config));
        }
        return new WifiChip.WifiRadioCombination(frameworkConfigs);
    }

    private static WifiChip.WifiRadioConfiguration halToFrameworkRadioConfiguration(
            WifiRadioConfiguration halConfig) {
        return new WifiChip.WifiRadioConfiguration(halToFrameworkWifiBand(halConfig.bandInfo),
                halToFrameworkAntennaMode(halConfig.antennaMode));
    }

    /**
     * Makes the Hal flavor of WifiScanner's band indication
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

    private static boolean bitmapContains(int bitmap, int expectedBit) {
        return (bitmap & expectedBit) != 0;
    }

    private static int halToFrameworkWifiBand(int halBand) {
        int frameworkBand = 0;
        if (bitmapContains(halBand, WifiBand.BAND_24GHZ)) {
            frameworkBand |= WifiScanner.WIFI_BAND_24_GHZ;
        }
        if (bitmapContains(halBand, WifiBand.BAND_5GHZ)) {
            frameworkBand |= WifiScanner.WIFI_BAND_5_GHZ;
        }
        if (bitmapContains(halBand, WifiBand.BAND_5GHZ_DFS)) {
            frameworkBand |= WifiScanner.WIFI_BAND_5_GHZ_DFS_ONLY;
        }
        if (bitmapContains(halBand, WifiBand.BAND_6GHZ)) {
            frameworkBand |= WifiScanner.WIFI_BAND_6_GHZ;
        }
        if (bitmapContains(halBand, WifiBand.BAND_60GHZ)) {
            frameworkBand |= WifiScanner.WIFI_BAND_60_GHZ;
        }
        return frameworkBand;
    }

    private static @WifiChip.WifiAntennaMode int halToFrameworkAntennaMode(int mode) {
        switch (mode) {
            case WifiAntennaMode.WIFI_ANTENNA_MODE_UNSPECIFIED:
                return WifiChip.WIFI_ANTENNA_MODE_UNSPECIFIED;
            case WifiAntennaMode.WIFI_ANTENNA_MODE_1X1:
                return WifiChip.WIFI_ANTENNA_MODE_1X1;
            case WifiAntennaMode.WIFI_ANTENNA_MODE_2X2:
                return WifiChip.WIFI_ANTENNA_MODE_2X2;
            case WifiAntennaMode.WIFI_ANTENNA_MODE_3X3:
                return WifiChip.WIFI_ANTENNA_MODE_3X3;
            case WifiAntennaMode.WIFI_ANTENNA_MODE_4X4:
                return WifiChip.WIFI_ANTENNA_MODE_4X4;
            default:
                Log.e(TAG, "Invalid WifiAntennaMode: " + mode);
                return -1;
        }
    }

    /**
     * Convert framework's operational mode to HAL's operational mode.
     */
    private int frameworkToHalIfaceMode(@WifiAvailableChannel.OpMode int mode) {
        int halMode = 0;
        if ((mode & WifiAvailableChannel.OP_MODE_STA) != 0) {
            halMode |= WifiIfaceMode.IFACE_MODE_STA;
        }
        if ((mode & WifiAvailableChannel.OP_MODE_SAP) != 0) {
            halMode |= WifiIfaceMode.IFACE_MODE_SOFTAP;
        }
        if ((mode & WifiAvailableChannel.OP_MODE_WIFI_DIRECT_CLI) != 0) {
            halMode |= WifiIfaceMode.IFACE_MODE_P2P_CLIENT;
        }
        if ((mode & WifiAvailableChannel.OP_MODE_WIFI_DIRECT_GO) != 0) {
            halMode |= WifiIfaceMode.IFACE_MODE_P2P_GO;
        }
        if ((mode & WifiAvailableChannel.OP_MODE_WIFI_AWARE) != 0) {
            halMode |= WifiIfaceMode.IFACE_MODE_NAN;
        }
        if ((mode & WifiAvailableChannel.OP_MODE_TDLS) != 0) {
            halMode |= WifiIfaceMode.IFACE_MODE_TDLS;
        }
        return halMode;
    }

    /**
     * Convert framework's WifiAvailableChannel.FILTER_* to HAL's UsableChannelFilter.
     */
    private int frameworkToHalUsableFilter(@WifiAvailableChannel.Filter int filter) {
        int halFilter = 0;  // O implies no additional filter other than regulatory (default)

        if ((filter & WifiAvailableChannel.FILTER_CONCURRENCY) != 0) {
            halFilter |= android.hardware.wifi.V1_6.IWifiChip.UsableChannelFilter.CONCURRENCY;
        }
        if ((filter & WifiAvailableChannel.FILTER_CELLULAR_COEXISTENCE) != 0) {
            halFilter |= android.hardware.wifi.V1_6.IWifiChip.UsableChannelFilter
                    .CELLULAR_COEXISTENCE;
        }

        return halFilter;
    }

    /**
     * Convert framework's WifiAvailableChannel.FILTER_* to HAL's UsableChannelFilter 1.6.
     */
    private int frameworkToHalUsableFilter_1_6(@WifiAvailableChannel.Filter int filter) {
        int halFilter = 0;  // O implies no additional filter other than regulatory (default)

        if ((filter & WifiAvailableChannel.FILTER_CONCURRENCY) != 0) {
            halFilter |= android.hardware.wifi.V1_6.IWifiChip.UsableChannelFilter.CONCURRENCY;
        }
        if ((filter & WifiAvailableChannel.FILTER_CELLULAR_COEXISTENCE) != 0) {
            halFilter |= android.hardware.wifi.V1_6.IWifiChip.UsableChannelFilter
                    .CELLULAR_COEXISTENCE;
        }
        if ((filter & WifiAvailableChannel.FILTER_NAN_INSTANT_MODE) != 0) {
            halFilter |= android.hardware.wifi.V1_6.IWifiChip.UsableChannelFilter.NAN_INSTANT_MODE;
        }

        return halFilter;
    }

    /**
     * Convert from HAL's operational mode to framework's operational mode.
     */
    private @WifiAvailableChannel.OpMode int halToFrameworkIfaceMode(int halMode) {
        int mode = 0;
        if ((halMode & WifiIfaceMode.IFACE_MODE_STA) != 0) {
            mode |= WifiAvailableChannel.OP_MODE_STA;
        }
        if ((halMode & WifiIfaceMode.IFACE_MODE_SOFTAP) != 0) {
            mode |= WifiAvailableChannel.OP_MODE_SAP;
        }
        if ((halMode & WifiIfaceMode.IFACE_MODE_P2P_CLIENT) != 0) {
            mode |= WifiAvailableChannel.OP_MODE_WIFI_DIRECT_CLI;
        }
        if ((halMode & WifiIfaceMode.IFACE_MODE_P2P_GO) != 0) {
            mode |= WifiAvailableChannel.OP_MODE_WIFI_DIRECT_GO;
        }
        if ((halMode & WifiIfaceMode.IFACE_MODE_NAN) != 0) {
            mode |= WifiAvailableChannel.OP_MODE_WIFI_AWARE;
        }
        if ((halMode & WifiIfaceMode.IFACE_MODE_TDLS) != 0) {
            mode |= WifiAvailableChannel.OP_MODE_TDLS;
        }
        return mode;
    }

    @NonNull
    private ArrayList<android.hardware.wifi.V1_5.IWifiChip.CoexUnsafeChannel>
            frameworkCoexUnsafeChannelsToHidl(
            @NonNull List<android.net.wifi.CoexUnsafeChannel> frameworkUnsafeChannels) {
        final ArrayList<android.hardware.wifi.V1_5.IWifiChip.CoexUnsafeChannel> hidlList =
                new ArrayList<>();
        if (!SdkLevel.isAtLeastS()) {
            return hidlList;
        }
        for (android.net.wifi.CoexUnsafeChannel frameworkUnsafeChannel : frameworkUnsafeChannels) {
            final android.hardware.wifi.V1_5.IWifiChip.CoexUnsafeChannel hidlUnsafeChannel =
                    new android.hardware.wifi.V1_5.IWifiChip.CoexUnsafeChannel();
            switch (frameworkUnsafeChannel.getBand()) {
                case (WifiScanner.WIFI_BAND_24_GHZ):
                    hidlUnsafeChannel.band = WifiBand.BAND_24GHZ;
                    break;
                case (WifiScanner.WIFI_BAND_5_GHZ):
                    hidlUnsafeChannel.band = WifiBand.BAND_5GHZ;
                    break;
                case (WifiScanner.WIFI_BAND_6_GHZ):
                    hidlUnsafeChannel.band = WifiBand.BAND_6GHZ;
                    break;
                case (WifiScanner.WIFI_BAND_60_GHZ):
                    hidlUnsafeChannel.band = WifiBand.BAND_60GHZ;
                    break;
                default:
                    Log.e(TAG, "Tried to set unsafe channel with unknown band: "
                            + frameworkUnsafeChannel.getBand());
                    continue;
            }
            hidlUnsafeChannel.channel = frameworkUnsafeChannel.getChannel();
            final int powerCapDbm = frameworkUnsafeChannel.getPowerCapDbm();
            if (powerCapDbm != POWER_CAP_NONE) {
                hidlUnsafeChannel.powerCapDbm = powerCapDbm;
            } else {
                hidlUnsafeChannel.powerCapDbm =
                        android.hardware.wifi.V1_5.IWifiChip.PowerCapConstant.NO_POWER_CAP;
            }
            hidlList.add(hidlUnsafeChannel);
        }
        return hidlList;
    }

    private int frameworkCoexRestrictionsToHidl(@WifiManager.CoexRestriction int restrictions) {
        int hidlRestrictions = 0;
        if (!SdkLevel.isAtLeastS()) {
            return hidlRestrictions;
        }
        if ((restrictions & WifiManager.COEX_RESTRICTION_WIFI_DIRECT) != 0) {
            hidlRestrictions |= android.hardware.wifi.V1_5.IWifiChip.CoexRestriction.WIFI_DIRECT;
        }
        if ((restrictions & WifiManager.COEX_RESTRICTION_SOFTAP) != 0) {
            hidlRestrictions |= android.hardware.wifi.V1_5.IWifiChip.CoexRestriction.SOFTAP;
        }
        if ((restrictions & WifiManager.COEX_RESTRICTION_WIFI_AWARE) != 0) {
            hidlRestrictions |= android.hardware.wifi.V1_5.IWifiChip.CoexRestriction.WIFI_AWARE;
        }
        return hidlRestrictions;
    }

    private byte frameworkMultiStaUseCaseToHidl(@WifiNative.MultiStaUseCase int useCase)
            throws IllegalArgumentException {
        switch (useCase) {
            case WifiNative.DUAL_STA_TRANSIENT_PREFER_PRIMARY:
                return android.hardware.wifi.V1_5.IWifiChip
                        .MultiStaUseCase.DUAL_STA_TRANSIENT_PREFER_PRIMARY;
            case WifiNative.DUAL_STA_NON_TRANSIENT_UNBIASED:
                return android.hardware.wifi.V1_5.IWifiChip
                        .MultiStaUseCase.DUAL_STA_NON_TRANSIENT_UNBIASED;
            default:
                throw new IllegalArgumentException("Invalid use case " + useCase);
        }
    }

    /**
     * This method checks if we need to backoff wifi Tx power due to SAR requirements.
     * It handles the case when the device is running the V1_1 version of WifiChip HAL
     * In that HAL version, it is required to perform wifi Tx power backoff only if
     * a voice call is ongoing.
     */
    private static boolean sarPowerBackoffRequired_1_1(SarInfo sarInfo) {
        /* As long as no voice call is active (in case voice call is supported),
         * no backoff is needed
         */
        if (sarInfo.sarVoiceCallSupported) {
            return (sarInfo.isVoiceCall || sarInfo.isEarPieceActive);
        } else {
            return false;
        }
    }

    /**
     * This method maps the information inside the SarInfo instance into a SAR scenario
     * when device is running the V1_1 version of WifiChip HAL.
     * In this HAL version, only one scenario is defined which is for VOICE_CALL (if voice call is
     * supported).
     * Otherwise, an exception is thrown.
     */
    private static int frameworkToHalTxPowerScenario_1_1(SarInfo sarInfo) {
        if (sarInfo.sarVoiceCallSupported && (sarInfo.isVoiceCall || sarInfo.isEarPieceActive)) {
            return android.hardware.wifi.V1_1.IWifiChip.TxPowerScenario.VOICE_CALL;
        } else {
            throw new IllegalArgumentException("bad scenario: voice call not active/supported");
        }
    }

    /**
     * This method checks if we need to backoff wifi Tx power due to SAR requirements.
     * It handles the case when the device is running the V1_2 version of WifiChip HAL
     */
    private static boolean sarPowerBackoffRequired_1_2(SarInfo sarInfo) {
        if (sarInfo.sarSapSupported && sarInfo.isWifiSapEnabled) {
            return true;
        }
        if (sarInfo.sarVoiceCallSupported && (sarInfo.isVoiceCall || sarInfo.isEarPieceActive)) {
            return true;
        }
        return false;
    }

    /**
     * This method maps the information inside the SarInfo instance into a SAR scenario
     * when device is running the V1_2 version of WifiChip HAL.
     * If SAR SoftAP input is supported,
     * we make these assumptions:
     *   - All voice calls are treated as if device is near the head.
     *   - SoftAP scenario is treated as if device is near the body.
     * In case SoftAP is not supported, then we should revert to the V1_1 HAL
     * behavior, and the only valid scenario would be when a voice call is ongoing.
     */
    private static int frameworkToHalTxPowerScenario_1_2(SarInfo sarInfo) {
        if (sarInfo.sarSapSupported && sarInfo.sarVoiceCallSupported) {
            if (sarInfo.isVoiceCall || sarInfo.isEarPieceActive) {
                return android.hardware.wifi.V1_2.IWifiChip
                        .TxPowerScenario.ON_HEAD_CELL_ON;
            } else if (sarInfo.isWifiSapEnabled) {
                return android.hardware.wifi.V1_2.IWifiChip
                        .TxPowerScenario.ON_BODY_CELL_ON;
            } else {
                throw new IllegalArgumentException("bad scenario: no voice call/softAP active");
            }
        } else if (sarInfo.sarVoiceCallSupported) {
            /* SAR SoftAP input not supported, act like V1_1 */
            if (sarInfo.isVoiceCall || sarInfo.isEarPieceActive) {
                return android.hardware.wifi.V1_1.IWifiChip.TxPowerScenario.VOICE_CALL;
            } else {
                throw new IllegalArgumentException("bad scenario: voice call not active");
            }
        } else {
            throw new IllegalArgumentException("Invalid case: voice call not supported");
        }
    }

    protected android.hardware.wifi.V1_1.IWifiChip getWifiChipV1_1Mockable() {
        return android.hardware.wifi.V1_1.IWifiChip.castFrom(mWifiChip);
    }

    protected android.hardware.wifi.V1_2.IWifiChip getWifiChipV1_2Mockable() {
        return android.hardware.wifi.V1_2.IWifiChip.castFrom(mWifiChip);
    }

    protected android.hardware.wifi.V1_3.IWifiChip getWifiChipV1_3Mockable() {
        return android.hardware.wifi.V1_3.IWifiChip.castFrom(mWifiChip);
    }

    protected android.hardware.wifi.V1_4.IWifiChip getWifiChipV1_4Mockable() {
        return android.hardware.wifi.V1_4.IWifiChip.castFrom(mWifiChip);
    }

    protected android.hardware.wifi.V1_5.IWifiChip getWifiChipV1_5Mockable() {
        return android.hardware.wifi.V1_5.IWifiChip.castFrom(mWifiChip);
    }

    protected android.hardware.wifi.V1_6.IWifiChip getWifiChipV1_6Mockable() {
        return android.hardware.wifi.V1_6.IWifiChip.castFrom(mWifiChip);
    }

    private static int[] intsFromArrayList(ArrayList<Integer> a) {
        if (a == null) return null;
        int[] b = new int[a.size()];
        int i = 0;
        for (Integer e : a) b[i++] = e;
        return b;
    }

    private boolean isOk(WifiStatus status, String methodStr) {
        if (status.code == WifiStatusCode.SUCCESS) return true;
        Log.e(TAG, methodStr + " failed with status: " + status);
        return false;
    }

    private void handleRemoteException(RemoteException e, String methodStr) {
        Log.e(TAG, methodStr + " failed with remote exception: " + e);
        mWifiChip = null;
    }

    private <T> T validateAndCall(String methodStr, T defaultVal, @NonNull Supplier<T> supplier) {
        if (mWifiChip == null) {
            Log.e(TAG, "Cannot call " + methodStr + " because mWifiChip is null");
            return defaultVal;
        }
        return supplier.get();
    }
}
