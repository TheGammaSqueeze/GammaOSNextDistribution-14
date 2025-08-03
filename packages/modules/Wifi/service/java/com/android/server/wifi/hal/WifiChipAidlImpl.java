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
import android.hardware.wifi.AfcChannelAllowance;
import android.hardware.wifi.AvailableAfcChannelInfo;
import android.hardware.wifi.AvailableAfcFrequencyInfo;
import android.hardware.wifi.IWifiApIface;
import android.hardware.wifi.IWifiChip.ChannelCategoryMask;
import android.hardware.wifi.IWifiChip.CoexRestriction;
import android.hardware.wifi.IWifiChip.FeatureSetMask;
import android.hardware.wifi.IWifiChip.LatencyMode;
import android.hardware.wifi.IWifiChip.MultiStaUseCase;
import android.hardware.wifi.IWifiChip.TxPowerScenario;
import android.hardware.wifi.IWifiChip.UsableChannelFilter;
import android.hardware.wifi.IWifiChipEventCallback;
import android.hardware.wifi.IWifiNanIface;
import android.hardware.wifi.IWifiP2pIface;
import android.hardware.wifi.IWifiRttController;
import android.hardware.wifi.IWifiStaIface;
import android.hardware.wifi.IfaceConcurrencyType;
import android.hardware.wifi.IfaceType;
import android.hardware.wifi.WifiAntennaMode;
import android.hardware.wifi.WifiBand;
import android.hardware.wifi.WifiChipCapabilities;
import android.hardware.wifi.WifiDebugHostWakeReasonStats;
import android.hardware.wifi.WifiDebugRingBufferFlags;
import android.hardware.wifi.WifiDebugRingBufferStatus;
import android.hardware.wifi.WifiIfaceMode;
import android.hardware.wifi.WifiRadioCombination;
import android.hardware.wifi.WifiRadioConfiguration;
import android.hardware.wifi.WifiStatusCode;
import android.hardware.wifi.WifiUsableChannel;
import android.net.wifi.CoexUnsafeChannel;
import android.net.wifi.OuiKeyedData;
import android.net.wifi.WifiAvailableChannel;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiScanner;
import android.os.RemoteException;
import android.os.ServiceSpecificException;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;
import com.android.modules.utils.build.SdkLevel;
import com.android.server.wifi.SarInfo;
import com.android.server.wifi.SsidTranslator;
import com.android.server.wifi.WifiNative;
import com.android.server.wifi.WlanWakeReasonAndCounts;
import com.android.server.wifi.util.BitMask;
import com.android.server.wifi.util.HalAidlUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * AIDL implementation of the WifiChip interface.
 */
public class WifiChipAidlImpl implements IWifiChip {
    private static final String TAG = "WifiChipAidlImpl";
    private android.hardware.wifi.IWifiChip mWifiChip;
    private android.hardware.wifi.IWifiChipEventCallback mHalCallback;
    private WifiChip.Callback mFrameworkCallback;
    private final Object mLock = new Object();
    private Context mContext;
    private SsidTranslator mSsidTranslator;

    public WifiChipAidlImpl(@NonNull android.hardware.wifi.IWifiChip chip,
            @NonNull Context context, @NonNull SsidTranslator ssidTranslator) {
        mWifiChip = chip;
        mContext = context;
        mSsidTranslator = ssidTranslator;
    }

    /**
     * See comments for {@link IWifiChip#configureChip(int)}
     */
    @Override
    public boolean configureChip(int modeId) {
        final String methodStr = "configureChip";
        synchronized (mLock) {
            try {
                if (!checkIfaceAndLogFailure(methodStr)) return false;
                mWifiChip.configureChip(modeId);
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
     * See comments for {@link IWifiChip#createApIface(List)}
     */
    @Override
    @Nullable
    public WifiApIface createApIface(@NonNull List<OuiKeyedData> vendorData) {
        final String methodStr = "createApIface";
        synchronized (mLock) {
            try {
                if (!checkIfaceAndLogFailure(methodStr)) return null;
                IWifiApIface iface;
                if (WifiHalAidlImpl.isServiceVersionAtLeast(2) && !vendorData.isEmpty()) {
                    android.hardware.wifi.common.OuiKeyedData[] halVendorData =
                            HalAidlUtil.frameworkToHalOuiKeyedDataList(vendorData);
                    iface = mWifiChip.createApOrBridgedApIface(
                            IfaceConcurrencyType.AP, halVendorData);
                } else {
                    iface = mWifiChip.createApIface();
                }
                return new WifiApIface(iface);
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return null;
        }
    }

    /**
     * See comments for {@link IWifiChip#createBridgedApIface(List)}
     */
    @Override
    @Nullable
    public WifiApIface createBridgedApIface(@NonNull List<OuiKeyedData> vendorData) {
        final String methodStr = "createBridgedApIface";
        synchronized (mLock) {
            try {
                if (!checkIfaceAndLogFailure(methodStr)) return null;
                IWifiApIface iface;
                if (WifiHalAidlImpl.isServiceVersionAtLeast(2) && !vendorData.isEmpty()) {
                    android.hardware.wifi.common.OuiKeyedData[] halVendorData =
                            HalAidlUtil.frameworkToHalOuiKeyedDataList(vendorData);
                    iface = mWifiChip.createApOrBridgedApIface(
                            IfaceConcurrencyType.AP_BRIDGED, halVendorData);
                } else {
                    iface = mWifiChip.createBridgedApIface();
                }
                return new WifiApIface(iface);
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return null;
        }
    }

    /**
     * See comments for {@link IWifiChip#createNanIface()}
     */
    @Override
    @Nullable
    public WifiNanIface createNanIface() {
        final String methodStr = "createNanIface";
        synchronized (mLock) {
            try {
                if (!checkIfaceAndLogFailure(methodStr)) return null;
                IWifiNanIface iface = mWifiChip.createNanIface();
                return new WifiNanIface(iface);
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return null;
        }
    }

    /**
     * See comments for {@link IWifiChip#createP2pIface()}
     */
    @Override
    @Nullable
    public WifiP2pIface createP2pIface() {
        final String methodStr = "createP2pIface";
        synchronized (mLock) {
            try {
                if (!checkIfaceAndLogFailure(methodStr)) return null;
                IWifiP2pIface iface = mWifiChip.createP2pIface();
                return new WifiP2pIface(iface);
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return null;
        }
    }

    /**
     * See comments for {@link IWifiChip#createRttController()}
     */
    @Override
    @Nullable
    public WifiRttController createRttController() {
        final String methodStr = "createRttController";
        synchronized (mLock) {
            try {
                if (!checkIfaceAndLogFailure(methodStr)) return null;
                IWifiRttController rttController = mWifiChip.createRttController(null);
                return new WifiRttController(rttController);
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return null;
        }
    }

    /**
     * See comments for {@link IWifiChip#createStaIface()}
     */
    @Override
    @Nullable
    public WifiStaIface createStaIface() {
        final String methodStr = "createStaIface";
        synchronized (mLock) {
            try {
                if (!checkIfaceAndLogFailure(methodStr)) return null;
                IWifiStaIface iface = mWifiChip.createStaIface();
                return new WifiStaIface(iface, mContext, mSsidTranslator);
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return null;
        }
    }

    /**
     * See comments for {@link IWifiChip#enableDebugErrorAlerts(boolean)}
     */
    @Override
    public boolean enableDebugErrorAlerts(boolean enable) {
        final String methodStr = "enableDebugErrorAlerts";
        synchronized (mLock) {
            try {
                if (!checkIfaceAndLogFailure(methodStr)) return false;
                mWifiChip.enableDebugErrorAlerts(enable);
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
     * See comments for {@link IWifiChip#flushRingBufferToFile()}
     */
    @Override
    public boolean flushRingBufferToFile() {
        final String methodStr = "flushRingBufferToFile";
        synchronized (mLock) {
            try {
                if (!checkIfaceAndLogFailure(methodStr)) return false;
                mWifiChip.flushRingBufferToFile();
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
     * See comments for {@link IWifiChip#forceDumpToDebugRingBuffer(String)}
     */
    @Override
    public boolean forceDumpToDebugRingBuffer(String ringName) {
        final String methodStr = "forceDumpToDebugRingBuffer";
        synchronized (mLock) {
            try {
                if (!checkIfaceAndLogFailure(methodStr)) return false;
                mWifiChip.forceDumpToDebugRingBuffer(ringName);
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
     * See comments for {@link IWifiChip#getApIface(String)}
     */
    @Override
    @Nullable
    public WifiApIface getApIface(String ifaceName) {
        final String methodStr = "getApIface";
        synchronized (mLock) {
            try {
                if (!checkIfaceAndLogFailure(methodStr)) return null;
                IWifiApIface iface = mWifiChip.getApIface(ifaceName);
                return new WifiApIface(iface);
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return null;
        }
    }

    /**
     * See comments for {@link IWifiChip#getApIfaceNames()}
     */
    @Override
    @Nullable
    public List<String> getApIfaceNames() {
        final String methodStr = "getApIfaceNames";
        synchronized (mLock) {
            try {
                if (!checkIfaceAndLogFailure(methodStr)) return null;
                String[] ifaceNames = mWifiChip.getApIfaceNames();
                return Arrays.asList(ifaceNames);
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return null;
        }
    }

    /**
     * See comments for {@link IWifiChip#getAvailableModes()}
     */
    @Override
    @Nullable
    public List<WifiChip.ChipMode> getAvailableModes() {
        final String methodStr = "getAvailableModes";
        synchronized (mLock) {
            try {
                if (!checkIfaceAndLogFailure(methodStr)) return null;
                android.hardware.wifi.IWifiChip.ChipMode[] halModes = mWifiChip.getAvailableModes();
                return halToFrameworkChipModeList(halModes);
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return null;
        }
    }

    /**
     * See comments for {@link IWifiChip#getCapabilitiesBeforeIfacesExist()}
     */
    @Override
    public WifiChip.Response<Long> getCapabilitiesBeforeIfacesExist() {
        final String methodStr = "getCapabilitiesBeforeIfacesExist";
        return getCapabilitiesInternal(methodStr);
    }

    /**
     * See comments for {@link IWifiChip#getCapabilitiesAfterIfacesExist()}
     */
    @Override
    public WifiChip.Response<Long> getCapabilitiesAfterIfacesExist() {
        final String methodStr = "getCapabilitiesAfterIfacesExist";
        return getCapabilitiesInternal(methodStr);
    }

    private WifiChip.Response<Long> getCapabilitiesInternal(String methodStr) {
        // getCapabilities uses the same logic in AIDL, regardless of whether the call
        // happens before or after any interfaces have been created.
        WifiChip.Response<Long> featuresResp = new WifiChip.Response<>(0L);
        synchronized (mLock) {
            try {
                if (!checkIfaceAndLogFailure(methodStr)) return featuresResp;
                long halFeatureSet = mWifiChip.getFeatureSet();
                featuresResp.setValue(halToFrameworkChipFeatureSet(halFeatureSet));
                featuresResp.setStatusCode(WifiHal.WIFI_STATUS_SUCCESS);
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
                featuresResp.setStatusCode(WifiHal.WIFI_STATUS_ERROR_REMOTE_EXCEPTION);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
                // TODO: convert to framework status code once WifiHalAidlImpl exists
                featuresResp.setStatusCode(e.errorCode);
            }
            return featuresResp;
        }
    }

    /**
     * See comments for {@link IWifiChip#getDebugHostWakeReasonStats()}
     */
    @Override
    @Nullable
    public WlanWakeReasonAndCounts getDebugHostWakeReasonStats() {
        final String methodStr = "getDebugHostWakeReasonStats";
        synchronized (mLock) {
            try {
                if (!checkIfaceAndLogFailure(methodStr)) return null;
                WifiDebugHostWakeReasonStats stats = mWifiChip.getDebugHostWakeReasonStats();
                return halToFrameworkWakeReasons(stats);
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return null;
        }
    }

    /**
     * See comments for {@link IWifiChip#getDebugRingBuffersStatus()}
     */
    @Override
    @Nullable
    public List<WifiNative.RingBufferStatus> getDebugRingBuffersStatus() {
        final String methodStr = "getDebugRingBuffersStatus";
        synchronized (mLock) {
            try {
                if (!checkIfaceAndLogFailure(methodStr)) return null;
                WifiDebugRingBufferStatus[] stats = mWifiChip.getDebugRingBuffersStatus();
                return halToFrameworkRingBufferStatusList(stats);
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            } catch (IllegalArgumentException e) {
                handleIllegalArgumentException(e, methodStr);
            }
            return null;
        }
    }

    /**
     * See comments for {@link IWifiChip#getId()}
     */
    @Override
    public int getId() {
        final String methodStr = "getId";
        synchronized (mLock) {
            try {
                if (!checkIfaceAndLogFailure(methodStr)) return -1;
                return mWifiChip.getId();
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return -1;
        }
    }

    /**
     * See comments for {@link IWifiChip#getMode()}
     */
    @Override
    public WifiChip.Response<Integer> getMode() {
        final String methodStr = "getMode";
        WifiChip.Response<Integer> modeResp = new WifiChip.Response<>(0);
        synchronized (mLock) {
            try {
                if (!checkIfaceAndLogFailure(methodStr)) return modeResp;
                int mode = mWifiChip.getMode();
                modeResp.setValue(mode);
                modeResp.setStatusCode(WifiHal.WIFI_STATUS_SUCCESS);
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
                modeResp.setStatusCode(WifiHal.WIFI_STATUS_ERROR_REMOTE_EXCEPTION);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
                // TODO: convert to framework status code once WifiHalAidlImpl exists
                modeResp.setStatusCode(e.errorCode);
            }
            return modeResp;
        }
    }

    /**
     * See comments for {@link IWifiChip#getNanIface(String)}
     */
    @Override
    @Nullable
    public WifiNanIface getNanIface(String ifaceName) {
        final String methodStr = "getNanIface";
        synchronized (mLock) {
            try {
                if (!checkIfaceAndLogFailure(methodStr)) return null;
                IWifiNanIface iface = mWifiChip.getNanIface(ifaceName);
                return new WifiNanIface(iface);
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return null;
        }
    }

    /**
     * See comments for {@link IWifiChip#getNanIfaceNames()}
     */
    @Override
    @Nullable
    public List<String> getNanIfaceNames() {
        final String methodStr = "getNanIfaceNames";
        synchronized (mLock) {
            try {
                if (!checkIfaceAndLogFailure(methodStr)) return null;
                String[] ifaceNames = mWifiChip.getNanIfaceNames();
                return Arrays.asList(ifaceNames);
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return null;
        }
    }

    /**
     * See comments for {@link IWifiChip#getP2pIface(String)}
     */
    @Override
    @Nullable
    public WifiP2pIface getP2pIface(String ifaceName) {
        final String methodStr = "getP2pIface";
        synchronized (mLock) {
            try {
                if (!checkIfaceAndLogFailure(methodStr)) return null;
                IWifiP2pIface iface = mWifiChip.getP2pIface(ifaceName);
                return new WifiP2pIface(iface);
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return null;
        }
    }

    /**
     * See comments for {@link IWifiChip#getP2pIfaceNames()}
     */
    @Override
    @Nullable
    public List<String> getP2pIfaceNames() {
        final String methodStr = "getP2pIfaceNames";
        synchronized (mLock) {
            try {
                if (!checkIfaceAndLogFailure(methodStr)) return null;
                String[] ifaceNames = mWifiChip.getP2pIfaceNames();
                return Arrays.asList(ifaceNames);
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return null;
        }
    }

    /**
     * See comments for {@link IWifiChip#getStaIface(String)}
     */
    @Override
    @Nullable
    public WifiStaIface getStaIface(String ifaceName) {
        final String methodStr = "getStaIface";
        synchronized (mLock) {
            try {
                if (!checkIfaceAndLogFailure(methodStr)) return null;
                IWifiStaIface iface = mWifiChip.getStaIface(ifaceName);
                return new WifiStaIface(iface, mContext, mSsidTranslator);
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return null;
        }
    }

    /**
     * See comments for {@link IWifiChip#getStaIfaceNames()}
     */
    @Override
    @Nullable
    public List<String> getStaIfaceNames() {
        final String methodStr = "getStaIfaceNames";
        synchronized (mLock) {
            try {
                if (!checkIfaceAndLogFailure(methodStr)) return null;
                String[] ifaceNames = mWifiChip.getStaIfaceNames();
                return Arrays.asList(ifaceNames);
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return null;
        }
    }

    /**
     * See comments for {@link IWifiChip#getSupportedRadioCombinations()}
     */
    @Override
    @Nullable
    public List<WifiChip.WifiRadioCombination> getSupportedRadioCombinations() {
        final String methodStr = "getSupportedRadioCombinations";
        synchronized (mLock) {
            try {
                if (!checkIfaceAndLogFailure(methodStr)) return null;
                WifiRadioCombination[] halCombos = mWifiChip.getSupportedRadioCombinations();
                return halToFrameworkRadioCombinations(halCombos);
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return null;
        }
    }

    /**
     * See comments for {@link IWifiChip#getWifiChipCapabilities()}
     */
    public WifiChip.WifiChipCapabilities getWifiChipCapabilities() {
        final String methodStr = "getWifiChipCapabilities";
        synchronized (mLock) {
            try {
                if (!checkIfaceAndLogFailure(methodStr)) return null;
                WifiChipCapabilities halCapab = mWifiChip.getWifiChipCapabilities();
                return halToFrameworkWifiChipCapabilities(halCapab);
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return null;
        }
    }

    /**
     * See comments for {@link IWifiChip#getUsableChannels(int, int, int)}
     */
    @Override
    @Nullable
    public List<WifiAvailableChannel> getUsableChannels(@WifiScanner.WifiBand int band,
            @WifiAvailableChannel.OpMode int mode, @WifiAvailableChannel.Filter int filter) {
        final String methodStr = "getUsableChannels";
        synchronized (mLock) {
            try {
                if (!checkIfaceAndLogFailure(methodStr)) return null;
                WifiUsableChannel[] halChannels = mWifiChip.getUsableChannels(
                        frameworkToHalWifiBand(band),
                        frameworkToHalIfaceMode(mode),
                        frameworkToHalUsableFilter(filter));
                List<WifiAvailableChannel> frameworkChannels = new ArrayList<>();
                for (WifiUsableChannel ch : halChannels) {
                    frameworkChannels.add(new WifiAvailableChannel(
                            ch.channel, halToFrameworkIfaceMode(ch.ifaceModeMask)));
                }
                return frameworkChannels;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            } catch (IllegalArgumentException e) {
                handleIllegalArgumentException(e, methodStr);
            }
            return null;
        }
    }

    /**
     * See comments for {@link IWifiChip#registerCallback(WifiChip.Callback)}
     */
    @Override
    public boolean registerCallback(WifiChip.Callback callback) {
        final String methodStr = "registerCallback";
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
                mHalCallback = new ChipEventCallback();
                mWifiChip.registerEventCallback(mHalCallback);
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
     * See comments for {@link IWifiChip#removeApIface(String)}
     */
    @Override
    public boolean removeApIface(String ifaceName) {
        final String methodStr = "removeApIface";
        synchronized (mLock) {
            try {
                if (!checkIfaceAndLogFailure(methodStr)) return false;
                mWifiChip.removeApIface(ifaceName);
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
     * See comments for {@link IWifiChip#removeIfaceInstanceFromBridgedApIface(String, String)}
     */
    @Override
    public boolean removeIfaceInstanceFromBridgedApIface(String brIfaceName, String ifaceName) {
        final String methodStr = "removeIfaceInstanceFromBridgedApIface";
        synchronized (mLock) {
            try {
                if (!checkIfaceAndLogFailure(methodStr)) return false;
                mWifiChip.removeIfaceInstanceFromBridgedApIface(brIfaceName, ifaceName);
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
     * See comments for {@link IWifiChip#removeNanIface(String)}
     */
    @Override
    public boolean removeNanIface(String ifaceName) {
        final String methodStr = "removeNanIface";
        synchronized (mLock) {
            try {
                if (!checkIfaceAndLogFailure(methodStr)) return false;
                mWifiChip.removeNanIface(ifaceName);
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
     * See comments for {@link IWifiChip#removeP2pIface(String)}
     */
    @Override
    public boolean removeP2pIface(String ifaceName) {
        final String methodStr = "removeP2pIface";
        synchronized (mLock) {
            try {
                if (!checkIfaceAndLogFailure(methodStr)) return false;
                mWifiChip.removeP2pIface(ifaceName);
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
     * See comments for {@link IWifiChip#removeStaIface(String)}
     */
    @Override
    public boolean removeStaIface(String ifaceName) {
        final String methodStr = "removeStaIface";
        synchronized (mLock) {
            try {
                if (!checkIfaceAndLogFailure(methodStr)) return false;
                mWifiChip.removeStaIface(ifaceName);
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
     * See comments for {@link IWifiChip#requestChipDebugInfo()}
     */
    @Override
    @Nullable
    public WifiChip.ChipDebugInfo requestChipDebugInfo() {
        final String methodStr = "requestChipDebugInfo";
        synchronized (mLock) {
            try {
                if (!checkIfaceAndLogFailure(methodStr)) return null;
                android.hardware.wifi.IWifiChip.ChipDebugInfo info =
                        mWifiChip.requestChipDebugInfo();
                return new WifiChip.ChipDebugInfo(info.driverDescription, info.firmwareDescription);
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return null;
        }
    }

    /**
     * See comments for {@link IWifiChip#requestDriverDebugDump()}
     */
    @Override
    @Nullable
    public byte[] requestDriverDebugDump() {
        final String methodStr = "requestDriverDebugDump";
        synchronized (mLock) {
            try {
                if (!checkIfaceAndLogFailure(methodStr)) return null;
                return mWifiChip.requestDriverDebugDump();
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return null;
        }
    }

    /**
     * See comments for {@link IWifiChip#requestFirmwareDebugDump()}
     */
    @Override
    @Nullable
    public byte[] requestFirmwareDebugDump() {
        final String methodStr = "requestFirmwareDebugDump";
        synchronized (mLock) {
            try {
                if (!checkIfaceAndLogFailure(methodStr)) return null;
                return mWifiChip.requestFirmwareDebugDump();
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return null;
        }
    }

    /**
     * See comments for {@link IWifiChip#selectTxPowerScenario(SarInfo)}
     */
    @Override
    public boolean selectTxPowerScenario(SarInfo sarInfo) {
        final String methodStr = "selectTxPowerScenario";
        synchronized (mLock) {
            try {
                if (!checkIfaceAndLogFailure(methodStr)) return false;
                if (sarPowerBackoffRequired(sarInfo)) {
                    // Power backoff is needed, so calculate and set the required scenario.
                    int halScenario = frameworkToHalTxPowerScenario(sarInfo);
                    if (sarInfo.setSarScenarioNeeded(halScenario)) {
                        Log.d(TAG, "Attempting to set SAR scenario to " + halScenario);
                        mWifiChip.selectTxPowerScenario(halScenario);
                    }
                    // Reaching here means that setting SAR scenario would be redundant,
                    // do nothing and return with success.
                    return true;
                }

                // We don't need to perform power backoff, so attempt to reset the SAR scenario.
                if (sarInfo.resetSarScenarioNeeded()) {
                    Log.d(TAG, "Attempting to reset the SAR scenario");
                    mWifiChip.resetTxPowerScenario();
                }

                // If no if-statement was executed, then setting/resetting the SAR scenario would
                // have been redundant. Do nothing and return with success.
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
     * See comments for {@link IWifiChip#setCoexUnsafeChannels(List, int)}
     */
    @Override
    public boolean setCoexUnsafeChannels(List<CoexUnsafeChannel> unsafeChannels, int restrictions) {
        final String methodStr = "setCoexUnsafeChannels";
        synchronized (mLock) {
            try {
                if (!checkIfaceAndLogFailure(methodStr)) return false;
                android.hardware.wifi.IWifiChip.CoexUnsafeChannel[] halChannels =
                        frameworkToHalCoexUnsafeChannels(unsafeChannels);
                int halRestrictions = frameworkToHalCoexRestrictions(restrictions);
                mWifiChip.setCoexUnsafeChannels(halChannels, halRestrictions);
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
     * See comments for {@link IWifiChip#setCountryCode(byte[])}
     */
    @Override
    public boolean setCountryCode(byte[] code) {
        final String methodStr = "setCountryCode";
        synchronized (mLock) {
            try {
                if (!checkIfaceAndLogFailure(methodStr)) return false;
                mWifiChip.setCountryCode(code);
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
     * See comments for {@link IWifiChip#setLowLatencyMode(boolean)}
     */
    @Override
    public boolean setLowLatencyMode(boolean enable) {
        final String methodStr = "setLowLatencyMode";
        synchronized (mLock) {
            try {
                if (!checkIfaceAndLogFailure(methodStr)) return false;
                int mode = enable ? LatencyMode.LOW : LatencyMode.NORMAL;
                mWifiChip.setLatencyMode(mode);
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
     * See comments for {@link IWifiChip#setMultiStaPrimaryConnection(String)}
     */
    @Override
    public boolean setMultiStaPrimaryConnection(String ifaceName) {
        final String methodStr = "setMultiStaPrimaryConnection";
        synchronized (mLock) {
            try {
                if (!checkIfaceAndLogFailure(methodStr)) return false;
                mWifiChip.setMultiStaPrimaryConnection(ifaceName);
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
     * See comments for {@link IWifiChip#setMultiStaUseCase(int)}
     */
    @Override
    public boolean setMultiStaUseCase(@WifiNative.MultiStaUseCase int useCase) {
        final String methodStr = "setMultiStaUseCase";
        synchronized (mLock) {
            try {
                if (!checkIfaceAndLogFailure(methodStr)) return false;
                mWifiChip.setMultiStaUseCase(frameworkToHalMultiStaUseCase(useCase));
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
     * See comments for {@link IWifiChip#startLoggingToDebugRingBuffer(String, int, int, int)}
     */
    @Override
    public boolean startLoggingToDebugRingBuffer(String ringName, int verboseLevel,
            int maxIntervalInSec, int minDataSizeInBytes) {
        final String methodStr = "startLoggingToDebugRingBuffer";
        synchronized (mLock) {
            try {
                if (!checkIfaceAndLogFailure(methodStr)) return false;
                mWifiChip.startLoggingToDebugRingBuffer(
                        ringName, verboseLevel, maxIntervalInSec, minDataSizeInBytes);
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
     * See comments for {@link IWifiChip#stopLoggingToDebugRingBuffer()}
     */
    @Override
    public boolean stopLoggingToDebugRingBuffer() {
        final String methodStr = "stopLoggingToDebugRingBuffer";
        synchronized (mLock) {
            try {
                if (!checkIfaceAndLogFailure(methodStr)) return false;
                mWifiChip.stopLoggingToDebugRingBuffer();
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
     * See comments for {@link IWifiChip#triggerSubsystemRestart()}
     */
    @Override
    public boolean triggerSubsystemRestart() {
        final String methodStr = "triggerSubsystemRestart";
        synchronized (mLock) {
            try {
                if (!checkIfaceAndLogFailure(methodStr)) return false;
                mWifiChip.triggerSubsystemRestart();
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
     * See comments for {@link IWifiChip#enableStaChannelForPeerNetwork(boolean, boolean)}
     */
    @Override
    public boolean enableStaChannelForPeerNetwork(boolean enableIndoorChannel,
            boolean enableDfsChannel) {
        final String methodStr = "enableStaChannelForPeerNetwork";
        synchronized (mLock) {
            try {
                if (!checkIfaceAndLogFailure(methodStr)) return false;
                int halChannelCategoryEnableFlag = 0;
                if (enableIndoorChannel) {
                    halChannelCategoryEnableFlag |= ChannelCategoryMask.INDOOR_CHANNEL;
                }
                if (enableDfsChannel) {
                    halChannelCategoryEnableFlag |= ChannelCategoryMask.DFS_CHANNEL;
                }
                mWifiChip.enableStaChannelForPeerNetwork(halChannelCategoryEnableFlag);
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    private class ChipEventCallback extends IWifiChipEventCallback.Stub {
        @Override
        public void onChipReconfigured(int modeId) throws RemoteException {
            if (mFrameworkCallback == null) return;
            mFrameworkCallback.onChipReconfigured(modeId);
        }

        @Override
        public void onChipReconfigureFailure(int statusCode) {
            if (mFrameworkCallback == null) return;
            // TODO: convert to framework status code once WifiHalAidlImpl exists
            mFrameworkCallback.onChipReconfigureFailure(statusCode);
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
        public void onDebugRingBufferDataAvailable(WifiDebugRingBufferStatus status, byte[] data) {
            if (mFrameworkCallback == null) return;
            try {
                mFrameworkCallback.onDebugRingBufferDataAvailable(
                        halToFrameworkRingBufferStatus(status), data);
            } catch (IllegalArgumentException e) {
                handleIllegalArgumentException(e, "onDebugRingBufferDataAvailable");
            }
        }

        @Override
        public void onDebugErrorAlert(int errorCode, byte[] debugData) {
            if (mFrameworkCallback == null) return;
            mFrameworkCallback.onDebugErrorAlert(errorCode, debugData);
        }

        @Override
        public void onRadioModeChange(RadioModeInfo[] radioModeInfoList) {
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

        @Override
        public String getInterfaceHash() {
            return IWifiChipEventCallback.HASH;
        }

        @Override
        public int getInterfaceVersion() {
            return IWifiChipEventCallback.VERSION;
        }
    }


    // Utilities

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
            case IfaceConcurrencyType.NAN_IFACE:
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
            case IfaceType.NAN_IFACE:
                return WifiChip.IFACE_TYPE_NAN;
            default:
                Log.e(TAG, "Invalid IfaceType received: " + type);
                return -1;
        }
    }

    private static @Nullable List<WifiNative.RingBufferStatus> halToFrameworkRingBufferStatusList(
            WifiDebugRingBufferStatus[] ringBuffers) throws IllegalArgumentException {
        if (ringBuffers == null) return null;
        List<WifiNative.RingBufferStatus> ans = new ArrayList<>();
        for (WifiDebugRingBufferStatus b : ringBuffers) {
            ans.add(halToFrameworkRingBufferStatus(b));
        }
        return ans;
    }

    private static WifiNative.RingBufferStatus halToFrameworkRingBufferStatus(
            WifiDebugRingBufferStatus h) throws IllegalArgumentException {
        WifiNative.RingBufferStatus ans = new WifiNative.RingBufferStatus();
        ans.name = h.ringName;
        ans.flag = halToFrameworkRingBufferFlags(h.flags);
        ans.ringBufferId = h.ringId;
        ans.ringBufferByteSize = h.sizeInBytes;
        ans.verboseLevel = h.verboseLevel;
        // Remaining fields are unavailable
        //  writtenBytes;
        //  readBytes;
        //  writtenRecords;
        return ans;
    }

    private static int halToFrameworkRingBufferFlags(int wifiDebugRingBufferFlag)
            throws IllegalArgumentException {
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
        ans.cmdEventWakeCntArray = h.cmdEventWakeCntPerType;
        ans.driverFWLocalWakeCntArray = h.driverFwLocalWakeCntPerType;
        return ans;
    }

    private static byte frameworkToHalMultiStaUseCase(@WifiNative.MultiStaUseCase int useCase)
            throws IllegalArgumentException {
        switch (useCase) {
            case WifiNative.DUAL_STA_TRANSIENT_PREFER_PRIMARY:
                return MultiStaUseCase.DUAL_STA_TRANSIENT_PREFER_PRIMARY;
            case WifiNative.DUAL_STA_NON_TRANSIENT_UNBIASED:
                return MultiStaUseCase.DUAL_STA_NON_TRANSIENT_UNBIASED;
            default:
                throw new IllegalArgumentException("Invalid use case " + useCase);
        }
    }

    private static @NonNull android.hardware.wifi.IWifiChip.CoexUnsafeChannel[]
            frameworkToHalCoexUnsafeChannels(
            @NonNull List<android.net.wifi.CoexUnsafeChannel> frameworkUnsafeChannels) {
        final ArrayList<android.hardware.wifi.IWifiChip.CoexUnsafeChannel> halList =
                new ArrayList<>();
        if (!SdkLevel.isAtLeastS()) {
            return new android.hardware.wifi.IWifiChip.CoexUnsafeChannel[0];
        }
        for (android.net.wifi.CoexUnsafeChannel frameworkUnsafeChannel : frameworkUnsafeChannels) {
            final android.hardware.wifi.IWifiChip.CoexUnsafeChannel halUnsafeChannel =
                    new android.hardware.wifi.IWifiChip.CoexUnsafeChannel();
            switch (frameworkUnsafeChannel.getBand()) {
                case (WifiScanner.WIFI_BAND_24_GHZ):
                    halUnsafeChannel.band = WifiBand.BAND_24GHZ;
                    break;
                case (WifiScanner.WIFI_BAND_5_GHZ):
                    halUnsafeChannel.band = WifiBand.BAND_5GHZ;
                    break;
                case (WifiScanner.WIFI_BAND_6_GHZ):
                    halUnsafeChannel.band = WifiBand.BAND_6GHZ;
                    break;
                case (WifiScanner.WIFI_BAND_60_GHZ):
                    halUnsafeChannel.band = WifiBand.BAND_60GHZ;
                    break;
                default:
                    Log.e(TAG, "Tried to set unsafe channel with unknown band: "
                            + frameworkUnsafeChannel.getBand());
                    continue;
            }
            halUnsafeChannel.channel = frameworkUnsafeChannel.getChannel();
            final int powerCapDbm = frameworkUnsafeChannel.getPowerCapDbm();
            if (powerCapDbm != POWER_CAP_NONE) {
                halUnsafeChannel.powerCapDbm = powerCapDbm;
            } else {
                halUnsafeChannel.powerCapDbm =
                        android.hardware.wifi.IWifiChip.NO_POWER_CAP_CONSTANT;
            }
            halList.add(halUnsafeChannel);
        }

        android.hardware.wifi.IWifiChip.CoexUnsafeChannel[] halArray =
                new android.hardware.wifi.IWifiChip.CoexUnsafeChannel[halList.size()];
        for (int i = 0; i < halList.size(); i++) {
            halArray[i] = halList.get(i);
        }
        return halArray;
    }

    private static int frameworkToHalCoexRestrictions(
            @WifiManager.CoexRestriction int restrictions) {
        int halRestrictions = 0;
        if (!SdkLevel.isAtLeastS()) {
            return halRestrictions;
        }
        if ((restrictions & WifiManager.COEX_RESTRICTION_WIFI_DIRECT) != 0) {
            halRestrictions |= CoexRestriction.WIFI_DIRECT;
        }
        if ((restrictions & WifiManager.COEX_RESTRICTION_SOFTAP) != 0) {
            halRestrictions |= CoexRestriction.SOFTAP;
        }
        if ((restrictions & WifiManager.COEX_RESTRICTION_WIFI_AWARE) != 0) {
            halRestrictions |= CoexRestriction.WIFI_AWARE;
        }
        return halRestrictions;
    }

    /**
     * Check if we need to backoff wifi Tx power due to SAR requirements.
     */
    private static boolean sarPowerBackoffRequired(SarInfo sarInfo) {
        if (sarInfo.sarSapSupported && sarInfo.isWifiSapEnabled) {
            return true;
        }
        if (sarInfo.sarVoiceCallSupported && (sarInfo.isVoiceCall || sarInfo.isEarPieceActive)) {
            return true;
        }
        return false;
    }

    /**
     * Maps the information in the SarInfo instance to a TxPowerScenario.
     * If SAR SoftAP input is supported, we make these assumptions:
     *   - All voice calls are treated as if device is near the head.
     *   - SoftAP scenario is treated as if device is near the body.
     * If SoftAP is not supported, the only valid scenario is when a voice call is ongoing.
     */
    private static int frameworkToHalTxPowerScenario(SarInfo sarInfo)
            throws IllegalArgumentException {
        if (sarInfo.sarSapSupported && sarInfo.sarVoiceCallSupported) {
            if (sarInfo.isVoiceCall || sarInfo.isEarPieceActive) {
                return TxPowerScenario.ON_HEAD_CELL_ON;
            } else if (sarInfo.isWifiSapEnabled) {
                return TxPowerScenario.ON_BODY_CELL_ON;
            } else {
                throw new IllegalArgumentException("bad scenario: no voice call/softAP active");
            }
        } else if (sarInfo.sarVoiceCallSupported) {
            if (sarInfo.isVoiceCall || sarInfo.isEarPieceActive) {
                return TxPowerScenario.VOICE_CALL;
            } else {
                throw new IllegalArgumentException("bad scenario: voice call not active");
            }
        } else {
            throw new IllegalArgumentException("Invalid case: voice call not supported");
        }
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

    private static int frameworkToHalIfaceMode(@WifiAvailableChannel.OpMode int mode) {
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

    private static @WifiAvailableChannel.OpMode int halToFrameworkIfaceMode(int halMode) {
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

    private static int frameworkToHalUsableFilter(@WifiAvailableChannel.Filter int filter) {
        int halFilter = 0;  // O implies no additional filter other than regulatory (default)
        if ((filter & WifiAvailableChannel.FILTER_CONCURRENCY) != 0) {
            halFilter |= UsableChannelFilter.CONCURRENCY;
        }
        if ((filter & WifiAvailableChannel.FILTER_CELLULAR_COEXISTENCE) != 0) {
            halFilter |= UsableChannelFilter.CELLULAR_COEXISTENCE;
        }
        if ((filter & WifiAvailableChannel.FILTER_NAN_INSTANT_MODE) != 0) {
            halFilter |= UsableChannelFilter.NAN_INSTANT_MODE;
        }

        return halFilter;
    }

    private static boolean bitmapContains(long bitmap, long expectedBit) {
        return (bitmap & expectedBit) != 0;
    }

    @VisibleForTesting
    protected static long halToFrameworkChipFeatureSet(long halFeatureSet) {
        long features = 0;
        if (bitmapContains(halFeatureSet, FeatureSetMask.SET_TX_POWER_LIMIT)) {
            features |= WifiManager.WIFI_FEATURE_TX_POWER_LIMIT;
        }
        if (bitmapContains(halFeatureSet, FeatureSetMask.D2D_RTT)) {
            features |= WifiManager.WIFI_FEATURE_D2D_RTT;
        }
        if (bitmapContains(halFeatureSet, FeatureSetMask.D2AP_RTT)) {
            features |= WifiManager.WIFI_FEATURE_D2AP_RTT;
        }
        if (bitmapContains(halFeatureSet, FeatureSetMask.SET_LATENCY_MODE)) {
            features |= WifiManager.WIFI_FEATURE_LOW_LATENCY;
        }
        if (bitmapContains(halFeatureSet, FeatureSetMask.P2P_RAND_MAC)) {
            features |= WifiManager.WIFI_FEATURE_P2P_RAND_MAC;
        }
        if (bitmapContains(halFeatureSet, FeatureSetMask.WIGIG)) {
            features |= WifiManager.WIFI_FEATURE_INFRA_60G;
        }
        if (bitmapContains(halFeatureSet, FeatureSetMask.T2LM_NEGOTIATION)) {
            features |= WifiManager.WIFI_FEATURE_T2LM_NEGOTIATION;
        }
        return features;
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
                return WifiChip.WIFI_ANTENNA_MODE_UNSPECIFIED;
        }
    }

    private static List<WifiChip.ChipMode> halToFrameworkChipModeList(
            android.hardware.wifi.IWifiChip.ChipMode[] halModes) {
        List<WifiChip.ChipMode> frameworkModes = new ArrayList<>();
        for (android.hardware.wifi.IWifiChip.ChipMode halMode : halModes) {
            frameworkModes.add(halToFrameworkChipMode(halMode));
        }
        return frameworkModes;
    }

    private static WifiChip.ChipMode halToFrameworkChipMode(
            android.hardware.wifi.IWifiChip.ChipMode halMode) {
        List<WifiChip.ChipConcurrencyCombination> frameworkCombos = new ArrayList<>();
        for (android.hardware.wifi.IWifiChip.ChipConcurrencyCombination halCombo :
                halMode.availableCombinations) {
            frameworkCombos.add(halToFrameworkChipConcurrencyCombination(halCombo));
        }
        return new WifiChip.ChipMode(halMode.id, frameworkCombos);
    }

    private static WifiChip.ChipConcurrencyCombination halToFrameworkChipConcurrencyCombination(
            android.hardware.wifi.IWifiChip.ChipConcurrencyCombination halCombo) {
        List<WifiChip.ChipConcurrencyCombinationLimit> frameworkLimits = new ArrayList<>();
        for (android.hardware.wifi.IWifiChip.ChipConcurrencyCombinationLimit halLimit :
                halCombo.limits) {
            frameworkLimits.add(halToFrameworkChipConcurrencyCombinationLimit(halLimit));
        }
        return new WifiChip.ChipConcurrencyCombination(frameworkLimits);
    }

    private static WifiChip.ChipConcurrencyCombinationLimit
            halToFrameworkChipConcurrencyCombinationLimit(
            android.hardware.wifi.IWifiChip.ChipConcurrencyCombinationLimit halLimit) {
        List<Integer> frameworkTypes = new ArrayList<>();
        for (int halType : halLimit.types) {
            frameworkTypes.add(halToFrameworkIfaceConcurrencyType(halType));
        }
        return new WifiChip.ChipConcurrencyCombinationLimit(halLimit.maxIfaces, frameworkTypes);
    }

    private static List<WifiChip.WifiRadioCombination> halToFrameworkRadioCombinations(
            WifiRadioCombination[] halCombos) {
        List<WifiChip.WifiRadioCombination> frameworkCombos = new ArrayList<>();
        for (WifiRadioCombination combo : halCombos) {
            frameworkCombos.add(halToFrameworkRadioCombination(combo));
        }
        return frameworkCombos;
    }

    /**
     * Converts the framework version of an AvailableAfcFrequencyInfo object to its AIDL equivalent.
     */
    private static AvailableAfcFrequencyInfo frameworkToHalAvailableAfcFrequencyInfo(
            WifiChip.AvailableAfcFrequencyInfo availableFrequencyInfo) {
        if (availableFrequencyInfo == null) {
            return null;
        }

        AvailableAfcFrequencyInfo halAvailableAfcFrequencyInfo = new AvailableAfcFrequencyInfo();
        halAvailableAfcFrequencyInfo.startFrequencyMhz = availableFrequencyInfo.startFrequencyMhz;
        halAvailableAfcFrequencyInfo.endFrequencyMhz = availableFrequencyInfo.endFrequencyMhz;
        halAvailableAfcFrequencyInfo.maxPsd = availableFrequencyInfo.maxPsdDbmPerMhz;

        return halAvailableAfcFrequencyInfo;
    }

    /**
     * Converts the framework version of an AvailableAfcChannelInfo object to its AIDL equivalent.
     */
    private static AvailableAfcChannelInfo frameworkToHalAvailableAfcChannelInfo(
            WifiChip.AvailableAfcChannelInfo availableChannelInfo) {
        if (availableChannelInfo == null) {
            return null;
        }

        AvailableAfcChannelInfo halAvailableAfcChannelInfo = new AvailableAfcChannelInfo();
        halAvailableAfcChannelInfo.globalOperatingClass = availableChannelInfo.globalOperatingClass;
        halAvailableAfcChannelInfo.channelCfi = availableChannelInfo.channelCfi;
        halAvailableAfcChannelInfo.maxEirpDbm = availableChannelInfo.maxEirpDbm;

        return halAvailableAfcChannelInfo;
    }

    private static AfcChannelAllowance frameworkToHalAfcChannelAllowance(
            WifiChip.AfcChannelAllowance afcChannelAllowance) {
        AfcChannelAllowance halAfcChannelAllowance = new AfcChannelAllowance();

        // convert allowed frequencies and channels to their HAL version
        if (afcChannelAllowance.availableAfcFrequencyInfos == null) {
            // this should not be left uninitialized, or it may result in an exception
            halAfcChannelAllowance.availableAfcFrequencyInfos = new AvailableAfcFrequencyInfo[0];
        } else {
            halAfcChannelAllowance.availableAfcFrequencyInfos = new AvailableAfcFrequencyInfo[
                    afcChannelAllowance.availableAfcFrequencyInfos.size()];

            for (int i = 0; i < afcChannelAllowance.availableAfcFrequencyInfos.size(); ++i) {
                halAfcChannelAllowance.availableAfcFrequencyInfos[i] =
                        frameworkToHalAvailableAfcFrequencyInfo(
                                afcChannelAllowance.availableAfcFrequencyInfos.get(i));
            }
        }

        if (afcChannelAllowance.availableAfcChannelInfos == null) {
            // this should not be left uninitialized, or it may result in an exception
            halAfcChannelAllowance.availableAfcChannelInfos = new AvailableAfcChannelInfo[0];
        } else {
            halAfcChannelAllowance.availableAfcChannelInfos = new AvailableAfcChannelInfo[
                    afcChannelAllowance.availableAfcChannelInfos.size()];

            for (int i = 0; i < afcChannelAllowance.availableAfcChannelInfos.size(); ++i) {
                halAfcChannelAllowance.availableAfcChannelInfos[i] =
                        frameworkToHalAvailableAfcChannelInfo(
                                afcChannelAllowance.availableAfcChannelInfos.get(i));
            }
        }

        halAfcChannelAllowance.availabilityExpireTimeMs =
                afcChannelAllowance.availabilityExpireTimeMs;

        return halAfcChannelAllowance;
    }

    private static WifiChip.WifiChipCapabilities halToFrameworkWifiChipCapabilities(
            WifiChipCapabilities halCapabilities) {
        return new WifiChip.WifiChipCapabilities(halCapabilities.maxMloAssociationLinkCount,
                halCapabilities.maxMloStrLinkCount, halCapabilities.maxConcurrentTdlsSessionCount);
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

    private boolean checkIfaceAndLogFailure(String methodStr) {
        if (mWifiChip == null) {
            Log.e(TAG, "Unable to call " + methodStr + " because iface is null.");
            return false;
        }
        return true;
    }

    private void handleRemoteException(RemoteException e, String methodStr) {
        mWifiChip = null;
        Log.e(TAG, methodStr + " failed with remote exception: " + e);
    }

    private void handleServiceSpecificException(ServiceSpecificException e, String methodStr) {
        Log.e(TAG, methodStr + " failed with service-specific exception: " + e);
    }

    private void handleIllegalArgumentException(IllegalArgumentException e, String methodStr) {
        Log.e(TAG, methodStr + " failed with illegal argument exception: " + e);
    }

    /**
     * See comments for {@link IWifiChip#setMloMode(int)}.
     */
    @Override
    public @WifiStatusCode int setMloMode(@WifiManager.MloMode int mode) {
        final String methodStr = "setMloMode";
        @WifiStatusCode int errorCode = WifiStatusCode.ERROR_UNKNOWN;
        synchronized (mLock) {
            try {
                if (checkIfaceAndLogFailure(methodStr)) {
                    mWifiChip.setMloMode(frameworkToAidlMloMode(mode));
                    errorCode = WifiStatusCode.SUCCESS;
                }
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
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

    /**
     * See comments for {@link IWifiChip#setAfcChannelAllowance(WifiChip.AfcChannelAllowance)}
     */
    @Override
    public boolean setAfcChannelAllowance(WifiChip.AfcChannelAllowance afcChannelAllowance) {
        final String methodStr = "setAfcChannelAllowance";

        try {
            AfcChannelAllowance halAfcChannelAllowance =
                    frameworkToHalAfcChannelAllowance(afcChannelAllowance);
            mWifiChip.setAfcChannelAllowance(halAfcChannelAllowance);
            return true;
        } catch (RemoteException e) {
            handleRemoteException(e, methodStr);
        } catch (ServiceSpecificException e) {
            handleServiceSpecificException(e, methodStr);
        }
        return false;
    }

    private @android.hardware.wifi.IWifiChip.ChipMloMode int frameworkToAidlMloMode(
            @WifiManager.MloMode int mode) {
        switch(mode) {
            case WifiManager.MLO_MODE_DEFAULT:
                return android.hardware.wifi.IWifiChip.ChipMloMode.DEFAULT;
            case WifiManager.MLO_MODE_LOW_LATENCY:
                return android.hardware.wifi.IWifiChip.ChipMloMode.LOW_LATENCY;
            case WifiManager.MLO_MODE_HIGH_THROUGHPUT:
                return android.hardware.wifi.IWifiChip.ChipMloMode.HIGH_THROUGHPUT;
            case WifiManager.MLO_MODE_LOW_POWER:
                return android.hardware.wifi.IWifiChip.ChipMloMode.LOW_POWER;
            default:
                throw new IllegalArgumentException("frameworkToAidlMloMode: Invalid mode: " + mode);
        }
    }
}
