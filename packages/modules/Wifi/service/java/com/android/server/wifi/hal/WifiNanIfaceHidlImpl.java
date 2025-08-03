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

import static android.net.wifi.aware.Characteristics.WIFI_AWARE_CIPHER_SUITE_NCS_PK_128;
import static android.net.wifi.aware.Characteristics.WIFI_AWARE_CIPHER_SUITE_NCS_PK_256;
import static android.net.wifi.aware.Characteristics.WIFI_AWARE_CIPHER_SUITE_NCS_SK_128;
import static android.net.wifi.aware.Characteristics.WIFI_AWARE_CIPHER_SUITE_NCS_SK_256;

import android.annotation.NonNull;
import android.hardware.wifi.V1_0.NanBandIndex;
import android.hardware.wifi.V1_0.NanBandSpecificConfig;
import android.hardware.wifi.V1_0.NanConfigRequest;
import android.hardware.wifi.V1_0.NanDataPathSecurityType;
import android.hardware.wifi.V1_0.NanEnableRequest;
import android.hardware.wifi.V1_0.NanInitiateDataPathRequest;
import android.hardware.wifi.V1_0.NanMatchAlg;
import android.hardware.wifi.V1_0.NanPublishRequest;
import android.hardware.wifi.V1_0.NanRespondToDataPathIndicationRequest;
import android.hardware.wifi.V1_0.NanSubscribeRequest;
import android.hardware.wifi.V1_0.NanTransmitFollowupRequest;
import android.hardware.wifi.V1_0.NanTxType;
import android.hardware.wifi.V1_0.WifiStatus;
import android.hardware.wifi.V1_0.WifiStatusCode;
import android.hardware.wifi.V1_6.NanCipherSuiteType;
import android.net.MacAddress;
import android.net.wifi.aware.ConfigRequest;
import android.net.wifi.aware.PublishConfig;
import android.net.wifi.aware.SubscribeConfig;
import android.net.wifi.aware.WifiAwareDataPathSecurityConfig;
import android.os.RemoteException;
import android.util.Log;

import com.android.server.wifi.aware.Capabilities;
import com.android.server.wifi.util.GeneralUtil.Mutable;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.function.Supplier;

/**
 * HIDL implementation of the IWifiNanIface interface.
 */
public class WifiNanIfaceHidlImpl implements IWifiNanIface {
    private static final String TAG = "WifiNanIfaceHidlImpl";
    private android.hardware.wifi.V1_0.IWifiNanIface mWifiNanIface;
    private String mIfaceName;
    private WifiNanIfaceCallbackHidlImpl mHalCallback;
    private WifiNanIface.Callback mFrameworkCallback;

    public WifiNanIfaceHidlImpl(@NonNull android.hardware.wifi.V1_0.IWifiNanIface nanIface) {
        mWifiNanIface = nanIface;
        mHalCallback = new WifiNanIfaceCallbackHidlImpl(this);
    }

    /**
     * Enable verbose logging.
     */
    public void enableVerboseLogging(boolean verbose) {
        if (mHalCallback != null) {
            mHalCallback.enableVerboseLogging(verbose);
        }
    }

    protected WifiNanIface.Callback getFrameworkCallback() {
        return mFrameworkCallback;
    }

    /**
     * See comments for {@link IWifiNanIface#registerFrameworkCallback(WifiNanIface.Callback)}
     */
    public boolean registerFrameworkCallback(WifiNanIface.Callback callback) {
        final String methodStr = "registerFrameworkCallback";
        return validateAndCall(methodStr, false,
                () -> registerFrameworkCallbackInternal(methodStr, callback));
    }

    /**
     * See comments for {@link IWifiNanIface#getName()}
     */
    public String getName() {
        final String methodStr = "getName";
        return validateAndCall(methodStr, null,
                () -> getNameInternal(methodStr));
    }

    /**
     * See comments for {@link IWifiNanIface#getCapabilities(short)}
     */
    public boolean getCapabilities(short transactionId) {
        final String methodStr = "getCapabilities";
        return validateAndCall(methodStr, false,
                () -> getCapabilitiesInternal(methodStr, transactionId));
    }

    /**
     * See comments for {@link IWifiNanIface#enableAndConfigure(short, ConfigRequest, boolean,
     *                         boolean, boolean, boolean, int, int, WifiNanIface.PowerParameters)}
     */
    public boolean enableAndConfigure(short transactionId, ConfigRequest configRequest,
            boolean notifyIdentityChange, boolean initialConfiguration, boolean rangingEnabled,
            boolean isInstantCommunicationEnabled, int instantModeChannel, int clusterId,
            int macAddressRandomizationIntervalSec, WifiNanIface.PowerParameters powerParameters) {
        final String methodStr = "enableAndConfigure";
        return validateAndCall(methodStr, false,
                () -> enableAndConfigureInternal(methodStr, transactionId, configRequest,
                        notifyIdentityChange, initialConfiguration, rangingEnabled,
                        isInstantCommunicationEnabled, instantModeChannel,
                        macAddressRandomizationIntervalSec, powerParameters));
    }

    /**
     * See comments for {@link IWifiNanIface#disable(short)}
     */
    public boolean disable(short transactionId) {
        final String methodStr = "disable";
        return validateAndCall(methodStr, false,
                () -> disableInternal(methodStr, transactionId));
    }

    /**
     * See comments for {@link IWifiNanIface#publish(short, byte, PublishConfig, byte[])}
     */
    public boolean publish(short transactionId, byte publishId, PublishConfig publishConfig,
            byte[] nanIdentityKey) {
        final String methodStr = "publish";
        return validateAndCall(methodStr, false,
                () -> publishInternal(methodStr, transactionId, publishId, publishConfig));
    }

    /**
     * See comments for {@link IWifiNanIface#subscribe(short, byte, SubscribeConfig, byte[])}
     */
    public boolean subscribe(short transactionId, byte subscribeId,
            SubscribeConfig subscribeConfig,
            byte[] nanIdentityKey) {
        final String methodStr = "subscribe";
        return validateAndCall(methodStr, false,
                () -> subscribeInternal(methodStr, transactionId, subscribeId, subscribeConfig));
    }

    /**
     * See comments for {@link IWifiNanIface#sendMessage(short, byte, int, MacAddress, byte[])}
     */
    public boolean sendMessage(short transactionId, byte pubSubId, int requestorInstanceId,
            MacAddress dest, byte[] message) {
        final String methodStr = "sendMessage";
        return validateAndCall(methodStr, false,
                () -> sendMessageInternal(methodStr, transactionId, pubSubId, requestorInstanceId,
                        dest, message));
    }

    /**
     * See comments for {@link IWifiNanIface#stopPublish(short, byte)}
     */
    public boolean stopPublish(short transactionId, byte pubSubId) {
        final String methodStr = "stopPublish";
        return validateAndCall(methodStr, false,
                () -> stopPublishInternal(methodStr, transactionId, pubSubId));
    }

    /**
     * See comments for {@link IWifiNanIface#stopSubscribe(short, byte)}
     */
    public boolean stopSubscribe(short transactionId, byte pubSubId) {
        final String methodStr = "stopSubscribe";
        return validateAndCall(methodStr, false,
                () -> stopSubscribeInternal(methodStr, transactionId, pubSubId));
    }

    /**
     * See comments for {@link IWifiNanIface#createAwareNetworkInterface(short, String)}
     */
    public boolean createAwareNetworkInterface(short transactionId, String interfaceName) {
        final String methodStr = "createAwareNetworkInterface";
        return validateAndCall(methodStr, false,
                () -> createAwareNetworkInterfaceInternal(methodStr, transactionId, interfaceName));
    }

    /**
     * See comments for {@link IWifiNanIface#deleteAwareNetworkInterface(short, String)}
     */
    public boolean deleteAwareNetworkInterface(short transactionId, String interfaceName) {
        final String methodStr = "deleteAwareNetworkInterface";
        return validateAndCall(methodStr, false,
                () -> deleteAwareNetworkInterfaceInternal(methodStr, transactionId, interfaceName));
    }

    /**
     * See comments for
     * {@link IWifiNanIface#initiateDataPath(short, int, int, int, MacAddress, String, boolean, byte[], Capabilities, WifiAwareDataPathSecurityConfig, byte)}
     */
    public boolean initiateDataPath(short transactionId, int peerId, int channelRequestType,
            int channel, MacAddress peer, String interfaceName,
            boolean isOutOfBand, byte[] appInfo, Capabilities capabilities,
            WifiAwareDataPathSecurityConfig securityConfig, byte pubSubId) {
        final String methodStr = "initiateDataPath";
        return validateAndCall(methodStr, false,
                () -> initiateDataPathInternal(methodStr, transactionId, peerId, channelRequestType,
                        channel, peer, interfaceName, isOutOfBand, appInfo, capabilities,
                        securityConfig));
    }

    /**
     * See comments for
     * {@link IWifiNanIface#respondToDataPathRequest(short, boolean, int, String, byte[], boolean, Capabilities, WifiAwareDataPathSecurityConfig, byte)}
     */
    public boolean respondToDataPathRequest(short transactionId, boolean accept, int ndpId,
            String interfaceName, byte[] appInfo, boolean isOutOfBand, Capabilities capabilities,
            WifiAwareDataPathSecurityConfig securityConfig, byte pubSubId) {
        final String methodStr = "respondToDataPathRequest";
        return validateAndCall(methodStr, false,
                () -> respondToDataPathRequestInternal(methodStr, transactionId, accept, ndpId,
                        interfaceName, appInfo, isOutOfBand, capabilities, securityConfig));
    }

    /**
     * See comments for {@link IWifiNanIface#endDataPath(short, int)}
     */
    public boolean endDataPath(short transactionId, int ndpId) {
        final String methodStr = "endDataPath";
        return validateAndCall(methodStr, false,
                () -> endDataPathInternal(methodStr, transactionId, ndpId));
    }

    @Override
    public boolean respondToPairingRequest(short transactionId, int pairingId, boolean accept,
            byte[] pairingIdentityKey, boolean enablePairingCache, int requestType, byte[] pmk,
            String password, int akm, int cipherSuite) {
        return false;
    }

    @Override
    public boolean initiateNanPairingRequest(short transactionId, int peerId, MacAddress peer,
            byte[] pairingIdentityKey, boolean enablePairingCache, int requestType, byte[] pmk,
            String password, int akm, int cipherSuite) {
        return false;
    }

    @Override
    public boolean endPairing(short transactionId, int pairingId) {
        return false;
    }

    @Override
    public boolean initiateNanBootstrappingRequest(short transactionId, int peerId, MacAddress peer,
            int method, byte[] cookie, byte pubSubId, boolean isComeBack) {
        return false;
    }

    @Override
    public boolean respondToNanBootstrappingRequest(short transactionId, int bootstrappingId,
            boolean accept, byte pubSubId) {
        return false;
    }

    @Override
    public boolean suspend(short transactionId, byte pubSubId) {
        return false;
    }

    @Override
    public boolean resume(short transactionId, byte pubSubId) {
        return false;
    }

    // Internal Implementations

    private boolean registerFrameworkCallbackInternal(String methodStr, WifiNanIface.Callback cb) {
        if (mFrameworkCallback != null) {
            Log.e(TAG, "Framework callback is already registered");
            return false;
        } else if (cb == null) {
            Log.e(TAG, "Cannot register a null framework callback");
            return false;
        }

        WifiStatus status;
        try {
            android.hardware.wifi.V1_2.IWifiNanIface iface12 = mockableCastTo_1_2();
            android.hardware.wifi.V1_5.IWifiNanIface iface15 = mockableCastTo_1_5();
            android.hardware.wifi.V1_6.IWifiNanIface iface16 = mockableCastTo_1_6();
            if (iface16 != null) {
                status = iface16.registerEventCallback_1_6(mHalCallback);
            } else if (iface15 != null) {
                status = iface15.registerEventCallback_1_5(mHalCallback);
            } else if (iface12 != null) {
                status = iface12.registerEventCallback_1_2(mHalCallback);
            } else {
                status = mWifiNanIface.registerEventCallback(mHalCallback);
            }
            if (!isOk(status, methodStr)) return false;
            mFrameworkCallback = cb;
            return true;
        } catch (RemoteException e) {
            handleRemoteException(e, methodStr);
            return false;
        }
    }

    private String getNameInternal(String methodStr) {
        if (mIfaceName != null) return mIfaceName;
        Mutable<String> nameResp = new Mutable<>();
        try {
            mWifiNanIface.getName((WifiStatus status, String name) -> {
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

    private boolean getCapabilitiesInternal(String methodStr, short transactionId) {
        android.hardware.wifi.V1_5.IWifiNanIface iface15 = mockableCastTo_1_5();
        try {
            WifiStatus status;
            if (iface15 == null) {
                status = mWifiNanIface.getCapabilitiesRequest(transactionId);
            }  else {
                status = iface15.getCapabilitiesRequest_1_5(transactionId);
            }
            return isOk(status, methodStr);
        } catch (RemoteException e) {
            handleRemoteException(e, methodStr);
            return false;
        }
    }

    boolean enableAndConfigureInternal(
            String methodStr, short transactionId, ConfigRequest configRequest,
            boolean notifyIdentityChange, boolean initialConfiguration, boolean rangingEnabled,
            boolean isInstantCommunicationEnabled, int instantModeChannel,
            int macAddressRandomizationIntervalSec, WifiNanIface.PowerParameters powerParameters) {
        android.hardware.wifi.V1_2.IWifiNanIface iface12 = mockableCastTo_1_2();
        android.hardware.wifi.V1_4.IWifiNanIface iface14 = mockableCastTo_1_4();
        android.hardware.wifi.V1_5.IWifiNanIface iface15 = mockableCastTo_1_5();
        android.hardware.wifi.V1_6.IWifiNanIface iface16 = mockableCastTo_1_6();
        android.hardware.wifi.V1_2.NanConfigRequestSupplemental configSupplemental12 =
                new android.hardware.wifi.V1_2.NanConfigRequestSupplemental();
        android.hardware.wifi.V1_5.NanConfigRequestSupplemental configSupplemental15 =
                new android.hardware.wifi.V1_5.NanConfigRequestSupplemental();
        android.hardware.wifi.V1_6.NanConfigRequestSupplemental configSupplemental16 =
                new android.hardware.wifi.V1_6.NanConfigRequestSupplemental();
        if (iface12 != null || iface14 != null) {
            configSupplemental12.discoveryBeaconIntervalMs = 0;
            configSupplemental12.numberOfSpatialStreamsInDiscovery = 0;
            configSupplemental12.enableDiscoveryWindowEarlyTermination = false;
            configSupplemental12.enableRanging = rangingEnabled;
        }

        if (iface15 != null) {
            configSupplemental15.V1_2 = configSupplemental12;
            configSupplemental15.enableInstantCommunicationMode = isInstantCommunicationEnabled;
        }
        if (iface16 != null) {
            configSupplemental16.V1_5 = configSupplemental15;
            configSupplemental16.instantModeChannel = instantModeChannel;
        }

        NanBandSpecificConfig config24 = new NanBandSpecificConfig();
        config24.rssiClose = 60;
        config24.rssiMiddle = 70;
        config24.rssiCloseProximity = 60;
        config24.dwellTimeMs = (byte) 200;
        config24.scanPeriodSec = 20;
        if (configRequest.mDiscoveryWindowInterval[ConfigRequest.NAN_BAND_24GHZ]
                == ConfigRequest.DW_INTERVAL_NOT_INIT) {
            config24.validDiscoveryWindowIntervalVal = false;
        } else {
            config24.validDiscoveryWindowIntervalVal = true;
            config24.discoveryWindowIntervalVal =
                    (byte) configRequest.mDiscoveryWindowInterval[ConfigRequest
                            .NAN_BAND_24GHZ];
        }

        NanBandSpecificConfig config5 = new NanBandSpecificConfig();
        config5.rssiClose = 60;
        config5.rssiMiddle = 75;
        config5.rssiCloseProximity = 60;
        config5.dwellTimeMs = (byte) 200;
        config5.scanPeriodSec = 20;
        if (configRequest.mDiscoveryWindowInterval[ConfigRequest.NAN_BAND_5GHZ]
                == ConfigRequest.DW_INTERVAL_NOT_INIT) {
            config5.validDiscoveryWindowIntervalVal = false;
        } else {
            config5.validDiscoveryWindowIntervalVal = true;
            config5.discoveryWindowIntervalVal =
                    (byte) configRequest.mDiscoveryWindowInterval[ConfigRequest
                            .NAN_BAND_5GHZ];
        }

        NanBandSpecificConfig config6 = new NanBandSpecificConfig();
        config6.rssiClose = 60;
        config6.rssiMiddle = 75;
        config6.rssiCloseProximity = 60;
        config6.dwellTimeMs = (byte) 200;
        config6.scanPeriodSec = 20;
        if (configRequest.mDiscoveryWindowInterval[ConfigRequest.NAN_BAND_6GHZ]
                == ConfigRequest.DW_INTERVAL_NOT_INIT) {
            config6.validDiscoveryWindowIntervalVal = false;
        } else {
            config6.validDiscoveryWindowIntervalVal = true;
            config6.discoveryWindowIntervalVal =
                    (byte) configRequest.mDiscoveryWindowInterval[ConfigRequest
                            .NAN_BAND_6GHZ];
        }

        try {
            WifiStatus status;
            if (initialConfiguration) {
                if (iface14 != null || iface15 != null || iface16 != null) {
                    // translate framework to HIDL configuration (V_1.4)
                    android.hardware.wifi.V1_4.NanEnableRequest req =
                            new android.hardware.wifi.V1_4.NanEnableRequest();

                    req.operateInBand[NanBandIndex.NAN_BAND_24GHZ] = true;
                    req.operateInBand[NanBandIndex.NAN_BAND_5GHZ] = configRequest.mSupport5gBand;
                    req.operateInBand[android.hardware.wifi.V1_4.NanBandIndex.NAN_BAND_6GHZ] =
                            configRequest.mSupport6gBand;
                    req.hopCountMax = 2;
                    req.configParams.masterPref = (byte) configRequest.mMasterPreference;
                    req.configParams.disableDiscoveryAddressChangeIndication =
                            !notifyIdentityChange;
                    req.configParams.disableStartedClusterIndication = !notifyIdentityChange;
                    req.configParams.disableJoinedClusterIndication = !notifyIdentityChange;
                    req.configParams.includePublishServiceIdsInBeacon = true;
                    req.configParams.numberOfPublishServiceIdsInBeacon = 0;
                    req.configParams.includeSubscribeServiceIdsInBeacon = true;
                    req.configParams.numberOfSubscribeServiceIdsInBeacon = 0;
                    req.configParams.rssiWindowSize = 8;
                    req.configParams.macAddressRandomizationIntervalSec =
                            macAddressRandomizationIntervalSec;

                    req.configParams.bandSpecificConfig[NanBandIndex.NAN_BAND_24GHZ] = config24;
                    req.configParams.bandSpecificConfig[NanBandIndex.NAN_BAND_5GHZ] = config5;
                    req.configParams.bandSpecificConfig[
                            android.hardware.wifi.V1_4.NanBandIndex.NAN_BAND_6GHZ] = config6;

                    req.debugConfigs.validClusterIdVals = true;
                    req.debugConfigs.clusterIdTopRangeVal = (short) configRequest.mClusterHigh;
                    req.debugConfigs.clusterIdBottomRangeVal = (short) configRequest.mClusterLow;
                    req.debugConfigs.validIntfAddrVal = false;
                    req.debugConfigs.validOuiVal = false;
                    req.debugConfigs.ouiVal = 0;
                    req.debugConfigs.validRandomFactorForceVal = false;
                    req.debugConfigs.randomFactorForceVal = 0;
                    req.debugConfigs.validHopCountForceVal = false;
                    req.debugConfigs.hopCountForceVal = 0;
                    req.debugConfigs.validDiscoveryChannelVal = false;
                    req.debugConfigs.discoveryChannelMhzVal[NanBandIndex.NAN_BAND_24GHZ] = 0;
                    req.debugConfigs.discoveryChannelMhzVal[NanBandIndex.NAN_BAND_5GHZ] = 0;
                    req.debugConfigs.discoveryChannelMhzVal[
                            android.hardware.wifi.V1_4.NanBandIndex.NAN_BAND_6GHZ] = 0;
                    req.debugConfigs.validUseBeaconsInBandVal = false;
                    req.debugConfigs.useBeaconsInBandVal[NanBandIndex.NAN_BAND_24GHZ] = true;
                    req.debugConfigs.useBeaconsInBandVal[NanBandIndex.NAN_BAND_5GHZ] = true;
                    req.debugConfigs.useBeaconsInBandVal[
                            android.hardware.wifi.V1_4.NanBandIndex.NAN_BAND_6GHZ] = true;
                    req.debugConfigs.validUseSdfInBandVal = false;
                    req.debugConfigs.useSdfInBandVal[NanBandIndex.NAN_BAND_24GHZ] = true;
                    req.debugConfigs.useSdfInBandVal[NanBandIndex.NAN_BAND_5GHZ] = true;
                    req.debugConfigs.useSdfInBandVal[
                            android.hardware.wifi.V1_4.NanBandIndex.NAN_BAND_6GHZ] = true;
                    updateConfigForPowerSettings14(req.configParams, configSupplemental12,
                            powerParameters);

                    if (iface16 != null) {
                        status = iface16.enableRequest_1_6(transactionId, req,
                                configSupplemental16);
                    } else if (iface15 != null) {
                        status = iface15.enableRequest_1_5(transactionId, req,
                                configSupplemental15);
                    } else {
                        status = iface14.enableRequest_1_4(transactionId, req,
                                configSupplemental12);
                    }
                } else {
                    // translate framework to HIDL configuration (before V_1.4)
                    NanEnableRequest req = new NanEnableRequest();

                    req.operateInBand[NanBandIndex.NAN_BAND_24GHZ] = true;
                    req.operateInBand[NanBandIndex.NAN_BAND_5GHZ] = configRequest.mSupport5gBand;
                    req.hopCountMax = 2;
                    req.configParams.masterPref = (byte) configRequest.mMasterPreference;
                    req.configParams.disableDiscoveryAddressChangeIndication =
                            !notifyIdentityChange;
                    req.configParams.disableStartedClusterIndication = !notifyIdentityChange;
                    req.configParams.disableJoinedClusterIndication = !notifyIdentityChange;
                    req.configParams.includePublishServiceIdsInBeacon = true;
                    req.configParams.numberOfPublishServiceIdsInBeacon = 0;
                    req.configParams.includeSubscribeServiceIdsInBeacon = true;
                    req.configParams.numberOfSubscribeServiceIdsInBeacon = 0;
                    req.configParams.rssiWindowSize = 8;
                    req.configParams.macAddressRandomizationIntervalSec =
                            macAddressRandomizationIntervalSec;

                    req.configParams.bandSpecificConfig[NanBandIndex.NAN_BAND_24GHZ] = config24;
                    req.configParams.bandSpecificConfig[NanBandIndex.NAN_BAND_5GHZ] = config5;

                    req.debugConfigs.validClusterIdVals = true;
                    req.debugConfigs.clusterIdTopRangeVal = (short) configRequest.mClusterHigh;
                    req.debugConfigs.clusterIdBottomRangeVal = (short) configRequest.mClusterLow;
                    req.debugConfigs.validIntfAddrVal = false;
                    req.debugConfigs.validOuiVal = false;
                    req.debugConfigs.ouiVal = 0;
                    req.debugConfigs.validRandomFactorForceVal = false;
                    req.debugConfigs.randomFactorForceVal = 0;
                    req.debugConfigs.validHopCountForceVal = false;
                    req.debugConfigs.hopCountForceVal = 0;
                    req.debugConfigs.validDiscoveryChannelVal = false;
                    req.debugConfigs.discoveryChannelMhzVal[NanBandIndex.NAN_BAND_24GHZ] = 0;
                    req.debugConfigs.discoveryChannelMhzVal[NanBandIndex.NAN_BAND_5GHZ] = 0;
                    req.debugConfigs.validUseBeaconsInBandVal = false;
                    req.debugConfigs.useBeaconsInBandVal[NanBandIndex.NAN_BAND_24GHZ] = true;
                    req.debugConfigs.useBeaconsInBandVal[NanBandIndex.NAN_BAND_5GHZ] = true;
                    req.debugConfigs.validUseSdfInBandVal = false;
                    req.debugConfigs.useSdfInBandVal[NanBandIndex.NAN_BAND_24GHZ] = true;
                    req.debugConfigs.useSdfInBandVal[NanBandIndex.NAN_BAND_5GHZ] = true;

                    updateConfigForPowerSettings(req.configParams, configSupplemental12,
                            powerParameters);

                    if (iface12 != null) {
                        status = iface12.enableRequest_1_2(transactionId, req,
                                configSupplemental12);
                    } else {
                        status = mWifiNanIface.enableRequest(transactionId, req);
                    }
                }
            } else {
                if (iface14 != null || iface15 != null || iface16 != null) {
                    android.hardware.wifi.V1_4.NanConfigRequest req =
                            new android.hardware.wifi.V1_4.NanConfigRequest();
                    req.masterPref = (byte) configRequest.mMasterPreference;
                    req.disableDiscoveryAddressChangeIndication = !notifyIdentityChange;
                    req.disableStartedClusterIndication = !notifyIdentityChange;
                    req.disableJoinedClusterIndication = !notifyIdentityChange;
                    req.includePublishServiceIdsInBeacon = true;
                    req.numberOfPublishServiceIdsInBeacon = 0;
                    req.includeSubscribeServiceIdsInBeacon = true;
                    req.numberOfSubscribeServiceIdsInBeacon = 0;
                    req.rssiWindowSize = 8;
                    req.macAddressRandomizationIntervalSec =
                            macAddressRandomizationIntervalSec;

                    req.bandSpecificConfig[NanBandIndex.NAN_BAND_24GHZ] = config24;
                    req.bandSpecificConfig[NanBandIndex.NAN_BAND_5GHZ] = config5;
                    req.bandSpecificConfig[android.hardware.wifi.V1_4.NanBandIndex.NAN_BAND_6GHZ] =
                            config6;

                    updateConfigForPowerSettings14(req, configSupplemental12,
                            powerParameters);
                    if (iface16 != null) {
                        status = iface16.configRequest_1_6(transactionId, req,
                                configSupplemental16);
                    } else if (iface15 != null) {
                        status = iface15.configRequest_1_5(transactionId, req,
                                configSupplemental15);
                    } else {
                        status = iface14.configRequest_1_4(transactionId, req,
                                configSupplemental12);
                    }
                } else {
                    NanConfigRequest req = new NanConfigRequest();
                    req.masterPref = (byte) configRequest.mMasterPreference;
                    req.disableDiscoveryAddressChangeIndication = !notifyIdentityChange;
                    req.disableStartedClusterIndication = !notifyIdentityChange;
                    req.disableJoinedClusterIndication = !notifyIdentityChange;
                    req.includePublishServiceIdsInBeacon = true;
                    req.numberOfPublishServiceIdsInBeacon = 0;
                    req.includeSubscribeServiceIdsInBeacon = true;
                    req.numberOfSubscribeServiceIdsInBeacon = 0;
                    req.rssiWindowSize = 8;
                    req.macAddressRandomizationIntervalSec =
                            macAddressRandomizationIntervalSec;

                    req.bandSpecificConfig[NanBandIndex.NAN_BAND_24GHZ] = config24;
                    req.bandSpecificConfig[NanBandIndex.NAN_BAND_5GHZ] = config5;

                    updateConfigForPowerSettings(req, configSupplemental12, powerParameters);

                    if (iface12 != null) {
                        status = iface12.configRequest_1_2(transactionId, req,
                                configSupplemental12);
                    } else {
                        status = mWifiNanIface.configRequest(transactionId, req);
                    }
                }
            }
            return isOk(status, methodStr);
        } catch (RemoteException e) {
            handleRemoteException(e, methodStr);
            return false;
        }
    }

    private boolean disableInternal(String methodStr, short transactionId) {
        try {
            WifiStatus status = mWifiNanIface.disableRequest(transactionId);
            return isOk(status, methodStr);
        } catch (RemoteException e) {
            handleRemoteException(e, methodStr);
            return false;
        }
    }

    private boolean publishInternal(String methodStr, short transactionId, byte publishId,
            PublishConfig publishConfig) {
        android.hardware.wifi.V1_6.IWifiNanIface iface16 = mockableCastTo_1_6();
        if (iface16 == null) {
            NanPublishRequest req = new NanPublishRequest();
            req.baseConfigs.sessionId = publishId;
            req.baseConfigs.ttlSec = (short) publishConfig.mTtlSec;
            req.baseConfigs.discoveryWindowPeriod = 1;
            req.baseConfigs.discoveryCount = 0;
            convertNativeByteArrayToArrayList(publishConfig.mServiceName,
                    req.baseConfigs.serviceName);
            req.baseConfigs.discoveryMatchIndicator = NanMatchAlg.MATCH_NEVER;
            convertNativeByteArrayToArrayList(publishConfig.mServiceSpecificInfo,
                    req.baseConfigs.serviceSpecificInfo);
            convertNativeByteArrayToArrayList(publishConfig.mMatchFilter,
                    publishConfig.mPublishType == PublishConfig.PUBLISH_TYPE_UNSOLICITED
                            ? req.baseConfigs.txMatchFilter : req.baseConfigs.rxMatchFilter);
            req.baseConfigs.useRssiThreshold = false;
            req.baseConfigs.disableDiscoveryTerminationIndication =
                    !publishConfig.mEnableTerminateNotification;
            req.baseConfigs.disableMatchExpirationIndication = true;
            req.baseConfigs.disableFollowupReceivedIndication = false;

            req.autoAcceptDataPathRequests = false;

            req.baseConfigs.rangingRequired = publishConfig.mEnableRanging;

            req.baseConfigs.securityConfig.securityType = NanDataPathSecurityType.OPEN;
            WifiAwareDataPathSecurityConfig securityConfig = publishConfig.getSecurityConfig();
            if (securityConfig != null) {
                req.baseConfigs.securityConfig.cipherType = getHalCipherSuiteType(
                        securityConfig.getCipherSuite());
                if (securityConfig.getPmk() != null && securityConfig.getPmk().length != 0) {
                    req.baseConfigs.securityConfig.securityType = NanDataPathSecurityType.PMK;
                    copyArray(securityConfig.getPmk(), req.baseConfigs.securityConfig.pmk);
                }
                if (securityConfig.getPskPassphrase() != null
                        && securityConfig.getPskPassphrase().length() != 0) {
                    req.baseConfigs.securityConfig.securityType =
                            NanDataPathSecurityType.PASSPHRASE;
                    convertNativeByteArrayToArrayList(securityConfig.getPskPassphrase().getBytes(),
                            req.baseConfigs.securityConfig.passphrase);
                }
            }

            req.publishType = publishConfig.mPublishType;
            req.txType = NanTxType.BROADCAST;

            try {
                WifiStatus status = mWifiNanIface.startPublishRequest(transactionId, req);
                return isOk(status, methodStr);
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
                return false;
            }
        } else {
            android.hardware.wifi.V1_6.NanPublishRequest req =
                    new android.hardware.wifi.V1_6.NanPublishRequest();
            req.baseConfigs.sessionId = publishId;
            req.baseConfigs.ttlSec = (short) publishConfig.mTtlSec;
            req.baseConfigs.discoveryWindowPeriod = 1;
            req.baseConfigs.discoveryCount = 0;
            convertNativeByteArrayToArrayList(publishConfig.mServiceName,
                    req.baseConfigs.serviceName);
            req.baseConfigs.discoveryMatchIndicator = NanMatchAlg.MATCH_NEVER;
            convertNativeByteArrayToArrayList(publishConfig.mServiceSpecificInfo,
                    req.baseConfigs.serviceSpecificInfo);
            convertNativeByteArrayToArrayList(publishConfig.mMatchFilter,
                    publishConfig.mPublishType == PublishConfig.PUBLISH_TYPE_UNSOLICITED
                            ? req.baseConfigs.txMatchFilter : req.baseConfigs.rxMatchFilter);
            req.baseConfigs.useRssiThreshold = false;
            req.baseConfigs.disableDiscoveryTerminationIndication =
                    !publishConfig.mEnableTerminateNotification;
            req.baseConfigs.disableMatchExpirationIndication = true;
            req.baseConfigs.disableFollowupReceivedIndication = false;

            req.autoAcceptDataPathRequests = false;

            req.baseConfigs.rangingRequired = publishConfig.mEnableRanging;

            req.baseConfigs.securityConfig.securityType = NanDataPathSecurityType.OPEN;
            WifiAwareDataPathSecurityConfig securityConfig = publishConfig.getSecurityConfig();
            if (securityConfig != null) {
                req.baseConfigs.securityConfig.cipherType = getHalCipherSuiteType(
                        securityConfig.getCipherSuite());
                if (securityConfig.getPmk() != null && securityConfig.getPmk().length != 0) {
                    req.baseConfigs.securityConfig.securityType = NanDataPathSecurityType.PMK;
                    copyArray(securityConfig.getPmk(), req.baseConfigs.securityConfig.pmk);
                }
                if (securityConfig.getPskPassphrase() != null
                        && securityConfig.getPskPassphrase().length() != 0) {
                    req.baseConfigs.securityConfig.securityType =
                            NanDataPathSecurityType.PASSPHRASE;
                    convertNativeByteArrayToArrayList(securityConfig.getPskPassphrase().getBytes(),
                            req.baseConfigs.securityConfig.passphrase);
                }
                if (securityConfig.getPmkId() != null && securityConfig.getPmkId().length != 0) {
                    copyArray(securityConfig.getPmkId(), req.baseConfigs.securityConfig.scid);
                }
            }

            req.publishType = publishConfig.mPublishType;
            req.txType = NanTxType.BROADCAST;

            try {
                WifiStatus status = iface16.startPublishRequest_1_6(transactionId, req);
                return isOk(status, methodStr);
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
                return false;
            }
        }
    }

    private boolean subscribeInternal(String methodStr, short transactionId, byte subscribeId,
            SubscribeConfig subscribeConfig) {
        NanSubscribeRequest req = new NanSubscribeRequest();
        req.baseConfigs.sessionId = subscribeId;
        req.baseConfigs.ttlSec = (short) subscribeConfig.mTtlSec;
        req.baseConfigs.discoveryWindowPeriod = 1;
        req.baseConfigs.discoveryCount = 0;
        convertNativeByteArrayToArrayList(subscribeConfig.mServiceName,
                req.baseConfigs.serviceName);
        req.baseConfigs.discoveryMatchIndicator = NanMatchAlg.MATCH_ONCE;
        convertNativeByteArrayToArrayList(subscribeConfig.mServiceSpecificInfo,
                req.baseConfigs.serviceSpecificInfo);
        convertNativeByteArrayToArrayList(subscribeConfig.mMatchFilter,
                subscribeConfig.mSubscribeType == SubscribeConfig.SUBSCRIBE_TYPE_ACTIVE
                        ? req.baseConfigs.txMatchFilter : req.baseConfigs.rxMatchFilter);
        req.baseConfigs.useRssiThreshold = false;
        req.baseConfigs.disableDiscoveryTerminationIndication =
                !subscribeConfig.mEnableTerminateNotification;
        req.baseConfigs.disableMatchExpirationIndication = false;
        req.baseConfigs.disableFollowupReceivedIndication = false;

        req.baseConfigs.rangingRequired =
                subscribeConfig.mMinDistanceMmSet || subscribeConfig.mMaxDistanceMmSet;
        req.baseConfigs.configRangingIndications = 0;
        if (subscribeConfig.mMinDistanceMmSet) {
            req.baseConfigs.distanceEgressCm = (short) Math.min(
                    subscribeConfig.mMinDistanceMm / 10, Short.MAX_VALUE);
            req.baseConfigs.configRangingIndications |=
                    android.hardware.wifi.V1_0.NanRangingIndication.EGRESS_MET_MASK;
        }
        if (subscribeConfig.mMaxDistanceMmSet) {
            req.baseConfigs.distanceIngressCm = (short) Math.min(
                    subscribeConfig.mMaxDistanceMm / 10, Short.MAX_VALUE);
            req.baseConfigs.configRangingIndications |=
                    android.hardware.wifi.V1_0.NanRangingIndication.INGRESS_MET_MASK;
        }

        // TODO: configure security
        req.baseConfigs.securityConfig.securityType = NanDataPathSecurityType.OPEN;

        req.subscribeType = subscribeConfig.mSubscribeType;

        try {
            WifiStatus status = mWifiNanIface.startSubscribeRequest(transactionId, req);
            return isOk(status, methodStr);
        } catch (RemoteException e) {
            handleRemoteException(e, methodStr);
            return false;
        }
    }

    private boolean sendMessageInternal(String methodStr, short transactionId, byte pubSubId,
            int requestorInstanceId, MacAddress dest, byte[] message) {
        NanTransmitFollowupRequest req = new NanTransmitFollowupRequest();
        req.discoverySessionId = pubSubId;
        req.peerId = requestorInstanceId;
        req.addr = dest.toByteArray();
        req.isHighPriority = false;
        req.shouldUseDiscoveryWindow = true;
        convertNativeByteArrayToArrayList(message, req.serviceSpecificInfo);
        req.disableFollowupResultIndication = false;

        try {
            WifiStatus status = mWifiNanIface.transmitFollowupRequest(transactionId, req);
            return isOk(status, methodStr);
        } catch (RemoteException e) {
            handleRemoteException(e, methodStr);
            return false;
        }
    }

    private boolean stopPublishInternal(String methodStr, short transactionId, byte pubSubId) {
        try {
            WifiStatus status = mWifiNanIface.stopPublishRequest(transactionId, pubSubId);
            return isOk(status, methodStr);
        } catch (RemoteException e) {
            handleRemoteException(e, methodStr);
            return false;
        }
    }

    private boolean stopSubscribeInternal(String methodStr, short transactionId, byte pubSubId) {
        try {
            WifiStatus status = mWifiNanIface.stopSubscribeRequest(transactionId, pubSubId);
            return isOk(status, methodStr);
        } catch (RemoteException e) {
            handleRemoteException(e, methodStr);
            return false;
        }
    }

    private boolean createAwareNetworkInterfaceInternal(String methodStr, short transactionId,
            String interfaceName) {
        try {
            WifiStatus status =
                    mWifiNanIface.createDataInterfaceRequest(transactionId, interfaceName);
            return isOk(status, methodStr);
        } catch (RemoteException e) {
            handleRemoteException(e, methodStr);
            return false;
        }
    }

    private boolean deleteAwareNetworkInterfaceInternal(String methodStr, short transactionId,
            String interfaceName) {
        try {
            WifiStatus status =
                    mWifiNanIface.deleteDataInterfaceRequest(transactionId, interfaceName);
            return isOk(status, methodStr);
        } catch (RemoteException e) {
            handleRemoteException(e, methodStr);
            return false;
        }
    }

    private boolean initiateDataPathInternal(String methodStr, short transactionId, int peerId,
            int channelRequestType, int channel, MacAddress peer, String interfaceName,
            boolean isOutOfBand, byte[] appInfo, Capabilities capabilities,
            WifiAwareDataPathSecurityConfig securityConfig) {
        if (capabilities == null) {
            Log.e(TAG, "Null capabilities were received");
            return false;
        }

        android.hardware.wifi.V1_6.IWifiNanIface iface16 = mockableCastTo_1_6();

        if (iface16 == null) {
            NanInitiateDataPathRequest req = new NanInitiateDataPathRequest();
            req.peerId = peerId;
            req.peerDiscMacAddr = peer.toByteArray();
            req.channelRequestType = WifiNanIface.NanDataPathChannelCfg.toHidl(channelRequestType);
            req.channel = channel;
            req.ifaceName = interfaceName;
            req.securityConfig.securityType = NanDataPathSecurityType.OPEN;
            if (securityConfig != null) {
                req.securityConfig.cipherType = getHalCipherSuiteType(
                        securityConfig.getCipherSuite());
                if (securityConfig.getPmk() != null && securityConfig.getPmk().length != 0) {

                    req.securityConfig.securityType = NanDataPathSecurityType.PMK;
                    copyArray(securityConfig.getPmk(), req.securityConfig.pmk);
                }
                if (securityConfig.getPskPassphrase() != null
                        && securityConfig.getPskPassphrase().length() != 0) {
                    req.securityConfig.securityType = NanDataPathSecurityType.PASSPHRASE;
                    convertNativeByteArrayToArrayList(securityConfig.getPskPassphrase().getBytes(),
                            req.securityConfig.passphrase);
                }
            }

            if (req.securityConfig.securityType != NanDataPathSecurityType.OPEN && isOutOfBand) {
                convertNativeByteArrayToArrayList(WifiNanIface.SERVICE_NAME_FOR_OOB_DATA_PATH
                                .getBytes(StandardCharsets.UTF_8), req.serviceNameOutOfBand);
            }
            convertNativeByteArrayToArrayList(appInfo, req.appInfo);

            try {
                WifiStatus status = mWifiNanIface.initiateDataPathRequest(transactionId, req);
                return isOk(status, methodStr);
            } catch (RemoteException e) {
                Log.e(TAG, "initiateDataPath: exception: " + e);
                return false;
            }
        } else {
            android.hardware.wifi.V1_6.NanInitiateDataPathRequest req =
                    new android.hardware.wifi.V1_6.NanInitiateDataPathRequest();
            req.peerId = peerId;
            req.peerDiscMacAddr = peer.toByteArray();
            req.channelRequestType = WifiNanIface.NanDataPathChannelCfg.toHidl(channelRequestType);
            req.channel = channel;
            req.ifaceName = interfaceName;
            req.securityConfig.securityType = NanDataPathSecurityType.OPEN;
            if (securityConfig != null) {
                req.securityConfig.cipherType = getHalCipherSuiteType(
                        securityConfig.getCipherSuite());
                if (securityConfig.getPmk() != null && securityConfig.getPmk().length != 0) {
                    req.securityConfig.securityType = NanDataPathSecurityType.PMK;
                    copyArray(securityConfig.getPmk(), req.securityConfig.pmk);
                }
                if (securityConfig.getPskPassphrase() != null
                        && securityConfig.getPskPassphrase().length() != 0) {
                    req.securityConfig.securityType = NanDataPathSecurityType.PASSPHRASE;
                    convertNativeByteArrayToArrayList(securityConfig.getPskPassphrase().getBytes(),
                            req.securityConfig.passphrase);
                }
                if (securityConfig.getPmkId() != null && securityConfig.getPmkId().length != 0) {
                    copyArray(securityConfig.getPmkId(), req.securityConfig.scid);
                }
            }

            if (req.securityConfig.securityType != NanDataPathSecurityType.OPEN && isOutOfBand) {
                convertNativeByteArrayToArrayList(WifiNanIface.SERVICE_NAME_FOR_OOB_DATA_PATH
                                .getBytes(StandardCharsets.UTF_8), req.serviceNameOutOfBand);
            }
            convertNativeByteArrayToArrayList(appInfo, req.appInfo);

            try {
                WifiStatus status = iface16.initiateDataPathRequest_1_6(transactionId, req);
                return isOk(status, methodStr);
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
                return false;
            }
        }
    }

    private boolean respondToDataPathRequestInternal(String methodStr, short transactionId,
            boolean accept, int ndpId, String interfaceName, byte[] appInfo, boolean isOutOfBand,
            Capabilities capabilities, WifiAwareDataPathSecurityConfig securityConfig) {
        if (capabilities == null) {
            Log.e(TAG, "Null capabilities were received");
            return false;
        }

        android.hardware.wifi.V1_6.IWifiNanIface iface16 = mockableCastTo_1_6();

        if (iface16 == null) {
            NanRespondToDataPathIndicationRequest req = new NanRespondToDataPathIndicationRequest();
            req.acceptRequest = accept;
            req.ndpInstanceId = ndpId;
            req.ifaceName = interfaceName;
            req.securityConfig.securityType = NanDataPathSecurityType.OPEN;
            if (securityConfig != null) {
                req.securityConfig.cipherType = getHalCipherSuiteType(
                        securityConfig.getCipherSuite());
                if (securityConfig.getPmk() != null && securityConfig.getPmk().length != 0) {

                    req.securityConfig.securityType = NanDataPathSecurityType.PMK;
                    copyArray(securityConfig.getPmk(), req.securityConfig.pmk);
                }
                if (securityConfig.getPskPassphrase() != null
                        && securityConfig.getPskPassphrase().length() != 0) {
                    req.securityConfig.securityType = NanDataPathSecurityType.PASSPHRASE;
                    convertNativeByteArrayToArrayList(securityConfig.getPskPassphrase().getBytes(),
                            req.securityConfig.passphrase);
                }
            }

            if (req.securityConfig.securityType != NanDataPathSecurityType.OPEN && isOutOfBand) {
                convertNativeByteArrayToArrayList(WifiNanIface.SERVICE_NAME_FOR_OOB_DATA_PATH
                        .getBytes(StandardCharsets.UTF_8), req.serviceNameOutOfBand);
            }
            convertNativeByteArrayToArrayList(appInfo, req.appInfo);

            try {
                WifiStatus status = mWifiNanIface.respondToDataPathIndicationRequest(
                        transactionId, req);
                return isOk(status, methodStr);
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
                return false;
            }
        } else {
            android.hardware.wifi.V1_6.NanRespondToDataPathIndicationRequest req =
                    new android.hardware.wifi.V1_6.NanRespondToDataPathIndicationRequest();
            req.acceptRequest = accept;
            req.ndpInstanceId = ndpId;
            req.ifaceName = interfaceName;
            req.securityConfig.securityType = NanDataPathSecurityType.OPEN;
            if (securityConfig != null) {
                req.securityConfig.cipherType = getHalCipherSuiteType(
                        securityConfig.getCipherSuite());
                if (securityConfig.getPmk() != null && securityConfig.getPmk().length != 0) {

                    req.securityConfig.securityType = NanDataPathSecurityType.PMK;
                    copyArray(securityConfig.getPmk(), req.securityConfig.pmk);
                }
                if (securityConfig.getPskPassphrase() != null
                        && securityConfig.getPskPassphrase().length() != 0) {
                    req.securityConfig.securityType = NanDataPathSecurityType.PASSPHRASE;
                    convertNativeByteArrayToArrayList(securityConfig.getPskPassphrase().getBytes(),
                            req.securityConfig.passphrase);
                }
                if (securityConfig.getPmkId() != null && securityConfig.getPmkId().length != 0) {
                    copyArray(securityConfig.getPmkId(), req.securityConfig.scid);
                }
            }

            if (req.securityConfig.securityType != NanDataPathSecurityType.OPEN && isOutOfBand) {
                convertNativeByteArrayToArrayList(WifiNanIface.SERVICE_NAME_FOR_OOB_DATA_PATH
                                .getBytes(StandardCharsets.UTF_8), req.serviceNameOutOfBand);
            }
            convertNativeByteArrayToArrayList(appInfo, req.appInfo);

            try {
                WifiStatus status = iface16
                        .respondToDataPathIndicationRequest_1_6(transactionId, req);
                return isOk(status, methodStr);
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
                return false;
            }
        }
    }

    private boolean endDataPathInternal(String methodStr, short transactionId, int ndpId) {
        try {
            WifiStatus status = mWifiNanIface.terminateDataPathRequest(transactionId, ndpId);
            return isOk(status, methodStr);
        } catch (RemoteException e) {
            handleRemoteException(e, methodStr);
            return false;
        }
    }


    // Helper Functions

    /**
     * Update the NAN configuration to reflect the current power settings (before V1.4)
     */
    private void updateConfigForPowerSettings(NanConfigRequest req,
            android.hardware.wifi.V1_2.NanConfigRequestSupplemental configSupplemental12,
            WifiNanIface.PowerParameters powerParameters) {
        updateSingleConfigForPowerSettings(req.bandSpecificConfig[NanBandIndex.NAN_BAND_5GHZ],
                powerParameters.discoveryWindow5Ghz);
        updateSingleConfigForPowerSettings(req.bandSpecificConfig[NanBandIndex.NAN_BAND_24GHZ],
                powerParameters.discoveryWindow24Ghz);

        configSupplemental12.discoveryBeaconIntervalMs = powerParameters.discoveryBeaconIntervalMs;
        configSupplemental12.numberOfSpatialStreamsInDiscovery =
                powerParameters.numberOfSpatialStreamsInDiscovery;
        configSupplemental12.enableDiscoveryWindowEarlyTermination =
                powerParameters.enableDiscoveryWindowEarlyTermination;
    }

    /**
     * Update the NAN configuration to reflect the current power settings (V1.4)
     */
    private void updateConfigForPowerSettings14(android.hardware.wifi.V1_4.NanConfigRequest req,
            android.hardware.wifi.V1_2.NanConfigRequestSupplemental configSupplemental12,
            WifiNanIface.PowerParameters powerParameters) {
        updateSingleConfigForPowerSettings(req.bandSpecificConfig[NanBandIndex.NAN_BAND_5GHZ],
                powerParameters.discoveryWindow5Ghz);
        updateSingleConfigForPowerSettings(req.bandSpecificConfig[NanBandIndex.NAN_BAND_24GHZ],
                powerParameters.discoveryWindow24Ghz);
        updateSingleConfigForPowerSettings(req.bandSpecificConfig[
                        android.hardware.wifi.V1_4.NanBandIndex.NAN_BAND_6GHZ],
                powerParameters.discoveryWindow6Ghz);

        configSupplemental12.discoveryBeaconIntervalMs = powerParameters.discoveryBeaconIntervalMs;
        configSupplemental12.numberOfSpatialStreamsInDiscovery =
                powerParameters.numberOfSpatialStreamsInDiscovery;
        configSupplemental12.enableDiscoveryWindowEarlyTermination =
                powerParameters.enableDiscoveryWindowEarlyTermination;
    }

    private void updateSingleConfigForPowerSettings(NanBandSpecificConfig cfg, int override) {
        if (override != -1) {
            cfg.validDiscoveryWindowIntervalVal = true;
            cfg.discoveryWindowIntervalVal = (byte) override;
        }
    }

    /**
     * Returns the HAL cipher suite.
     */
    private int getHalCipherSuiteType(int frameworkCipherSuites) {
        switch (frameworkCipherSuites) {
            case WIFI_AWARE_CIPHER_SUITE_NCS_SK_128:
                return NanCipherSuiteType.SHARED_KEY_128_MASK;
            case WIFI_AWARE_CIPHER_SUITE_NCS_SK_256:
                return NanCipherSuiteType.SHARED_KEY_256_MASK;
            case WIFI_AWARE_CIPHER_SUITE_NCS_PK_128:
                return NanCipherSuiteType.PUBLIC_KEY_128_MASK;
            case WIFI_AWARE_CIPHER_SUITE_NCS_PK_256:
                return NanCipherSuiteType.PUBLIC_KEY_256_MASK;
        }
        return NanCipherSuiteType.NONE;
    }

    /**
     * Converts a byte[] to an ArrayList<Byte>. Fills in the entries of the 'to' array if
     * provided (non-null), otherwise creates and returns a new ArrayList<>.
     *
     * @param from The input byte[] to convert from.
     * @param to An optional ArrayList<> to fill in from 'from'.
     *
     * @return A newly allocated ArrayList<> if 'to' is null, otherwise null.
     */
    private ArrayList<Byte> convertNativeByteArrayToArrayList(byte[] from, ArrayList<Byte> to) {
        if (from == null) {
            from = new byte[0];
        }

        if (to == null) {
            to = new ArrayList<>(from.length);
        } else {
            to.ensureCapacity(from.length);
        }
        for (int i = 0; i < from.length; ++i) {
            to.add(from[i]);
        }
        return to;
    }

    private void copyArray(byte[] from, byte[] to) {
        if (from == null || to == null || from.length != to.length) {
            Log.e(TAG, "copyArray error: from=" + Arrays.toString(from) + ", to="
                    + Arrays.toString(to));
            return;
        }
        for (int i = 0; i < from.length; ++i) {
            to[i] = from[i];
        }
    }

    protected android.hardware.wifi.V1_2.IWifiNanIface mockableCastTo_1_2() {
        return android.hardware.wifi.V1_2.IWifiNanIface.castFrom(mWifiNanIface);
    }

    protected android.hardware.wifi.V1_4.IWifiNanIface mockableCastTo_1_4() {
        return android.hardware.wifi.V1_4.IWifiNanIface.castFrom(mWifiNanIface);
    }

    protected android.hardware.wifi.V1_5.IWifiNanIface mockableCastTo_1_5() {
        return android.hardware.wifi.V1_5.IWifiNanIface.castFrom(mWifiNanIface);
    }

    protected android.hardware.wifi.V1_6.IWifiNanIface mockableCastTo_1_6() {
        return android.hardware.wifi.V1_6.IWifiNanIface.castFrom(mWifiNanIface);
    }

    private boolean isOk(WifiStatus status, String methodStr) {
        if (status.code == WifiStatusCode.SUCCESS) return true;
        Log.e(TAG, methodStr + " failed with status: " + status);
        return false;
    }

    private void handleRemoteException(RemoteException e, String methodStr) {
        Log.e(TAG, methodStr + " failed with remote exception: " + e);
        mWifiNanIface = null;
    }

    private <T> T validateAndCall(String methodStr, T defaultVal, @NonNull Supplier<T> supplier) {
        if (mWifiNanIface == null) {
            Log.wtf(TAG, "Cannot call " + methodStr + " because mWifiNanIface is null");
            return defaultVal;
        }
        return supplier.get();
    }
}
