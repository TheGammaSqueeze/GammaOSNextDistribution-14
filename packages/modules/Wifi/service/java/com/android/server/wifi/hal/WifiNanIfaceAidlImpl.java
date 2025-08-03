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

import static com.android.server.wifi.aware.WifiAwareStateManager.NAN_PAIRING_AKM_SAE;
import static com.android.server.wifi.aware.WifiAwareStateManager.NAN_PAIRING_REQUEST_TYPE_SETUP;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.hardware.wifi.NanBandIndex;
import android.hardware.wifi.NanBandSpecificConfig;
import android.hardware.wifi.NanBootstrappingRequest;
import android.hardware.wifi.NanBootstrappingResponse;
import android.hardware.wifi.NanCipherSuiteType;
import android.hardware.wifi.NanConfigRequest;
import android.hardware.wifi.NanConfigRequestSupplemental;
import android.hardware.wifi.NanDataPathSecurityConfig;
import android.hardware.wifi.NanDataPathSecurityType;
import android.hardware.wifi.NanDebugConfig;
import android.hardware.wifi.NanDiscoveryCommonConfig;
import android.hardware.wifi.NanEnableRequest;
import android.hardware.wifi.NanInitiateDataPathRequest;
import android.hardware.wifi.NanMatchAlg;
import android.hardware.wifi.NanPairingAkm;
import android.hardware.wifi.NanPairingConfig;
import android.hardware.wifi.NanPairingRequest;
import android.hardware.wifi.NanPairingRequestType;
import android.hardware.wifi.NanPairingSecurityConfig;
import android.hardware.wifi.NanPairingSecurityType;
import android.hardware.wifi.NanPublishRequest;
import android.hardware.wifi.NanRangingIndication;
import android.hardware.wifi.NanRespondToDataPathIndicationRequest;
import android.hardware.wifi.NanRespondToPairingIndicationRequest;
import android.hardware.wifi.NanSubscribeRequest;
import android.hardware.wifi.NanTransmitFollowupRequest;
import android.hardware.wifi.NanTxType;
import android.net.MacAddress;
import android.net.wifi.aware.AwarePairingConfig;
import android.net.wifi.aware.ConfigRequest;
import android.net.wifi.aware.PublishConfig;
import android.net.wifi.aware.SubscribeConfig;
import android.net.wifi.aware.WifiAwareDataPathSecurityConfig;
import android.os.RemoteException;
import android.os.ServiceSpecificException;
import android.util.Log;

import com.android.modules.utils.build.SdkLevel;
import com.android.server.wifi.aware.Capabilities;
import com.android.server.wifi.util.HalAidlUtil;

import java.nio.charset.StandardCharsets;

/**
 * AIDL implementation of the IWifiNanIface interface.
 */
public class WifiNanIfaceAidlImpl implements IWifiNanIface {
    private static final String TAG = "WifiNanIfaceAidlImpl";
    private android.hardware.wifi.IWifiNanIface mWifiNanIface;
    private String mIfaceName;
    private final Object mLock = new Object();
    private final WifiNanIfaceCallbackAidlImpl mHalCallback;
    private WifiNanIface.Callback mFrameworkCallback;

    public WifiNanIfaceAidlImpl(@NonNull android.hardware.wifi.IWifiNanIface nanIface) {
        mWifiNanIface = nanIface;
        mHalCallback = new WifiNanIfaceCallbackAidlImpl(this);
    }

    /**
     * Enable verbose logging.
     */
    @Override
    public void enableVerboseLogging(boolean verbose) {
        synchronized (mLock) {
            if (mHalCallback != null) {
                mHalCallback.enableVerboseLogging(verbose);
            }
        }
    }

    protected WifiNanIface.Callback getFrameworkCallback() {
        return mFrameworkCallback;
    }

    /**
     * See comments for {@link IWifiNanIface#registerFrameworkCallback(WifiNanIface.Callback)}
     */
    @Override
    public boolean registerFrameworkCallback(WifiNanIface.Callback callback) {
        final String methodStr = "registerFrameworkCallback";
        synchronized (mLock) {
            if (!checkIfaceAndLogFailure(methodStr)) return false;
            if (mFrameworkCallback != null) {
                Log.e(TAG, "Framework callback is already registered");
                return false;
            } else if (callback == null) {
                Log.e(TAG, "Cannot register a null framework callback");
                return false;
            }

            try {
                mWifiNanIface.registerEventCallback(mHalCallback);
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
     * See comments for {@link IWifiNanIface#getName()}
     */
    @Override
    @Nullable
    public String getName() {
        final String methodStr = "getName";
        synchronized (mLock) {
            if (!checkIfaceAndLogFailure(methodStr)) return null;
            if (mIfaceName != null) return mIfaceName;
            try {
                mIfaceName = mWifiNanIface.getName();
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
     * See comments for {@link IWifiNanIface#getCapabilities(short)}
     */
    @Override
    public boolean getCapabilities(short transactionId) {
        final String methodStr = "getCapabilities";
        synchronized (mLock) {
            try {
                if (!checkIfaceAndLogFailure(methodStr)) return false;
                mWifiNanIface.getCapabilitiesRequest((char) transactionId);
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
     * See comments for {@link IWifiNanIface#enableAndConfigure(short, ConfigRequest, boolean,
     * boolean, boolean, boolean, int, int, int, WifiNanIface.PowerParameters)}
     */
    @Override
    public boolean enableAndConfigure(short transactionId, ConfigRequest configRequest,
            boolean notifyIdentityChange, boolean initialConfiguration, boolean rangingEnabled,
            boolean isInstantCommunicationEnabled, int instantModeChannel, int clusterId,
            int macAddressRandomizationIntervalSec, WifiNanIface.PowerParameters powerParameters) {
        final String methodStr = "enableAndConfigure";
        try {
            if (!checkIfaceAndLogFailure(methodStr)) return false;
            NanConfigRequestSupplemental supplemental = createNanConfigRequestSupplemental(
                    rangingEnabled, isInstantCommunicationEnabled, instantModeChannel, clusterId);
            if (initialConfiguration) {
                NanEnableRequest req = createNanEnableRequest(
                        configRequest, notifyIdentityChange, supplemental,
                        macAddressRandomizationIntervalSec, powerParameters);
                mWifiNanIface.enableRequest((char) transactionId, req, supplemental);
            } else {
                NanConfigRequest req = createNanConfigRequest(
                        configRequest, notifyIdentityChange, supplemental,
                        macAddressRandomizationIntervalSec, powerParameters);
                mWifiNanIface.configRequest((char) transactionId, req, supplemental);
            }
            return true;
        } catch (RemoteException e) {
            handleRemoteException(e, methodStr);
        } catch (ServiceSpecificException e) {
            handleServiceSpecificException(e, methodStr);
        }
        return false;
    }

    /**
     * See comments for {@link IWifiNanIface#disable(short)}
     */
    @Override
    public boolean disable(short transactionId) {
        final String methodStr = "disable";
        synchronized (mLock) {
            try {
                if (!checkIfaceAndLogFailure(methodStr)) return false;
                mWifiNanIface.disableRequest((char) transactionId);
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
     * See comments for {@link IWifiNanIface#publish(short, byte, PublishConfig, byte[])}
     */
    @Override
    public boolean publish(short transactionId, byte publishId, PublishConfig publishConfig,
            byte[] nanIdentityKey) {
        final String methodStr = "publish";
        synchronized (mLock) {
            try {
                if (!checkIfaceAndLogFailure(methodStr)) return false;
                NanPublishRequest req = createNanPublishRequest(publishId, publishConfig,
                        nanIdentityKey);
                mWifiNanIface.startPublishRequest((char) transactionId, req);
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
     * See comments for {@link IWifiNanIface#subscribe(short, byte, SubscribeConfig, byte[])}
     */
    @Override
    public boolean subscribe(short transactionId, byte subscribeId,
            SubscribeConfig subscribeConfig,
            byte[] nanIdentityKey) {
        final String methodStr = "subscribe";
        synchronized (mLock) {
            try {
                if (!checkIfaceAndLogFailure(methodStr)) return false;
                NanSubscribeRequest req = createNanSubscribeRequest(subscribeId, subscribeConfig,
                        nanIdentityKey);
                mWifiNanIface.startSubscribeRequest((char) transactionId, req);
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
     * See comments for {@link IWifiNanIface#sendMessage(short, byte, int, MacAddress, byte[])}
     */
    @Override
    public boolean sendMessage(short transactionId, byte pubSubId, int requesterInstanceId,
            MacAddress dest, byte[] message) {
        final String methodStr = "sendMessage";
        synchronized (mLock) {
            try {
                if (!checkIfaceAndLogFailure(methodStr)) return false;
                NanTransmitFollowupRequest req = createNanTransmitFollowupRequest(
                        pubSubId, requesterInstanceId, dest, message);
                mWifiNanIface.transmitFollowupRequest((char) transactionId, req);
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
     * See comments for {@link IWifiNanIface#stopPublish(short, byte)}
     */
    @Override
    public boolean stopPublish(short transactionId, byte pubSubId) {
        final String methodStr = "stopPublish";
        synchronized (mLock) {
            try {
                if (!checkIfaceAndLogFailure(methodStr)) return false;
                mWifiNanIface.stopPublishRequest((char) transactionId, pubSubId);
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
     * See comments for {@link IWifiNanIface#stopSubscribe(short, byte)}
     */
    @Override
    public boolean stopSubscribe(short transactionId, byte pubSubId) {
        final String methodStr = "stopSubscribe";
        synchronized (mLock) {
            try {
                if (!checkIfaceAndLogFailure(methodStr)) return false;
                mWifiNanIface.stopSubscribeRequest((char) transactionId, pubSubId);
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
     * See comments for {@link IWifiNanIface#createAwareNetworkInterface(short, String)}
     */
    @Override
    public boolean createAwareNetworkInterface(short transactionId, String interfaceName) {
        final String methodStr = "createAwareNetworkInterface";
        synchronized (mLock) {
            try {
                if (!checkIfaceAndLogFailure(methodStr)) return false;
                mWifiNanIface.createDataInterfaceRequest((char) transactionId, interfaceName);
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
     * See comments for {@link IWifiNanIface#deleteAwareNetworkInterface(short, String)}
     */
    @Override
    public boolean deleteAwareNetworkInterface(short transactionId, String interfaceName) {
        final String methodStr = "deleteAwareNetworkInterface";
        synchronized (mLock) {
            try {
                if (!checkIfaceAndLogFailure(methodStr)) return false;
                mWifiNanIface.deleteDataInterfaceRequest((char) transactionId, interfaceName);
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
     * {@link IWifiNanIface#initiateDataPath(short, int, int, int, MacAddress, String, boolean, byte[], Capabilities, WifiAwareDataPathSecurityConfig, byte)}
     */
    @Override
    public boolean initiateDataPath(short transactionId, int peerId, int channelRequestType,
            int channel, MacAddress peer, String interfaceName,
            boolean isOutOfBand, byte[] appInfo, Capabilities capabilities,
            WifiAwareDataPathSecurityConfig securityConfig, byte pubSubId) {
        final String methodStr = "initiateDataPath";
        synchronized (mLock) {
            try {
                if (!checkIfaceAndLogFailure(methodStr)) return false;
                NanInitiateDataPathRequest req = createNanInitiateDataPathRequest(
                        peerId, channelRequestType, channel, peer, interfaceName, isOutOfBand,
                        appInfo, securityConfig, pubSubId);
                mWifiNanIface.initiateDataPathRequest((char) transactionId, req);
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
     * {@link IWifiNanIface#respondToDataPathRequest(short, boolean, int, String, byte[], boolean, Capabilities, WifiAwareDataPathSecurityConfig, byte)}
     */
    @Override
    public boolean respondToDataPathRequest(short transactionId, boolean accept, int ndpId,
            String interfaceName, byte[] appInfo, boolean isOutOfBand, Capabilities capabilities,
            WifiAwareDataPathSecurityConfig securityConfig, byte pubSubId) {
        final String methodStr = "respondToDataPathRequest";
        synchronized (mLock) {
            try {
                if (!checkIfaceAndLogFailure(methodStr)) return false;
                NanRespondToDataPathIndicationRequest req =
                        createNanRespondToDataPathIndicationRequest(
                                accept, ndpId, interfaceName, appInfo, isOutOfBand,
                                securityConfig, pubSubId);
                mWifiNanIface.respondToDataPathIndicationRequest((char) transactionId, req);
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
     * See comments for {@link IWifiNanIface#endDataPath(short, int)}
     */
    @Override
    public boolean endDataPath(short transactionId, int ndpId) {
        final String methodStr = "endDataPath";
        synchronized (mLock) {
            try {
                if (!checkIfaceAndLogFailure(methodStr)) return false;
                mWifiNanIface.terminateDataPathRequest((char) transactionId, ndpId);
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    @Override
    public boolean respondToPairingRequest(short transactionId, int pairingId, boolean accept,
            byte[] pairingIdentityKey, boolean enablePairingCache, int requestType, byte[] pmk,
            String password, int akm, int cipherSuite) {
        String methodStr = "respondToPairingRequest";
        NanRespondToPairingIndicationRequest request = createNanPairingResponse(pairingId, accept,
                pairingIdentityKey, enablePairingCache, requestType, pmk, password, akm,
                cipherSuite);
        synchronized (mLock) {
            try {
                if (!checkIfaceAndLogFailure(methodStr)) return false;
                mWifiNanIface.respondToPairingIndicationRequest((char) transactionId, request);
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    @Override
    public boolean initiateNanPairingRequest(short transactionId, int peerId, MacAddress peer,
            byte[] pairingIdentityKey, boolean enablePairingCache, int requestType, byte[] pmk,
            String password, int akm, int cipherSuite) {
        String methodStr = "initiateNanPairingRequest";
        NanPairingRequest nanPairingRequest = createNanPairingRequest(peerId, peer,
                pairingIdentityKey, enablePairingCache, requestType, pmk, password, akm,
                cipherSuite);
        synchronized (mLock) {
            try {
                if (!checkIfaceAndLogFailure(methodStr)) return false;
                mWifiNanIface.initiatePairingRequest((char) transactionId, nanPairingRequest);
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    @Override
    public boolean endPairing(short transactionId, int pairingId) {
        String methodStr = "endPairing";
        synchronized (mLock) {
            try {
                if (!checkIfaceAndLogFailure(methodStr)) return false;
                mWifiNanIface.terminatePairingRequest((char) transactionId, pairingId);
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    @Override
    public boolean initiateNanBootstrappingRequest(short transactionId, int peerId, MacAddress peer,
            int method, byte[] cookie, byte pubSubId, boolean isComeBack) {
        String methodStr = "initiateNanBootstrappingRequest";
        NanBootstrappingRequest request = createNanBootstrappingRequest(peerId, peer, method,
                cookie, pubSubId, isComeBack);
        synchronized (mLock) {
            try {
                if (!checkIfaceAndLogFailure(methodStr)) return false;
                mWifiNanIface.initiateBootstrappingRequest((char) transactionId, request);
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    @Override
    public boolean respondToNanBootstrappingRequest(short transactionId, int bootstrappingId,
            boolean accept, byte pubSubId) {
        String methodStr = "respondToNanBootstrappingRequest";
        NanBootstrappingResponse request = createNanBootstrappingResponse(bootstrappingId, accept,
                pubSubId);
        synchronized (mLock) {
            try {
                if (!checkIfaceAndLogFailure(methodStr)) return false;
                mWifiNanIface.respondToBootstrappingIndicationRequest((char) transactionId,
                        request);
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    @Override
    public boolean suspend(short transactionId, byte pubSubId) {
        String methodStr = "suspend";
        synchronized (mLock) {
            try {
                if (!checkIfaceAndLogFailure(methodStr)) return false;
                mWifiNanIface.suspendRequest((char) transactionId, pubSubId);
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    @Override
    public boolean resume(short transactionId, byte pubSubId) {
        String methodStr = "resume";
        synchronized (mLock) {
            try {
                if (!checkIfaceAndLogFailure(methodStr)) return false;
                mWifiNanIface.resumeRequest((char) transactionId, pubSubId);
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    // Utilities

    private static NanBootstrappingResponse createNanBootstrappingResponse(int bootstrappingId,
            boolean accept, byte pubSubId) {
        NanBootstrappingResponse request = new NanBootstrappingResponse();
        request.acceptRequest = accept;
        request.bootstrappingInstanceId = bootstrappingId;
        request.discoverySessionId = pubSubId;
        return request;
    }

    private static NanBootstrappingRequest createNanBootstrappingRequest(int peerId,
            MacAddress peer, int method, byte[] cookie, byte pubSubId, boolean isComeBack) {
        NanBootstrappingRequest request = new NanBootstrappingRequest();
        request.peerId = peerId;
        request.peerDiscMacAddr = peer.toByteArray();
        request.requestBootstrappingMethod = method;
        request.cookie = copyArray(cookie);
        request.discoverySessionId = pubSubId;
        request.isComeback = isComeBack;
        return request;
    }

    private static NanConfigRequestSupplemental createNanConfigRequestSupplemental(
            boolean rangingEnabled, boolean isInstantCommunicationEnabled, int instantModeChannel,
            int clusterId) {
        NanConfigRequestSupplemental out = new NanConfigRequestSupplemental();
        out.discoveryBeaconIntervalMs = 0;
        out.numberOfSpatialStreamsInDiscovery = 0;
        out.enableDiscoveryWindowEarlyTermination = false;
        out.enableRanging = rangingEnabled;
        out.enableInstantCommunicationMode = isInstantCommunicationEnabled;
        out.instantModeChannel = instantModeChannel;
        out.clusterId = clusterId;
        return out;
    }

    private static NanBandSpecificConfig[] createNanBandSpecificConfigs(
            ConfigRequest configRequest) {
        NanBandSpecificConfig config24 = new NanBandSpecificConfig();
        config24.rssiClose = 60;
        config24.rssiMiddle = 70;
        config24.rssiCloseProximity = 60;
        config24.dwellTimeMs = 200;
        config24.scanPeriodSec = 20;
        if (configRequest.mDiscoveryWindowInterval[ConfigRequest.NAN_BAND_24GHZ]
                == ConfigRequest.DW_INTERVAL_NOT_INIT) {
            config24.validDiscoveryWindowIntervalVal = false;
        } else {
            config24.validDiscoveryWindowIntervalVal = true;
            config24.discoveryWindowIntervalVal =
                    (byte) configRequest.mDiscoveryWindowInterval[ConfigRequest.NAN_BAND_24GHZ];
        }

        NanBandSpecificConfig config5 = new NanBandSpecificConfig();
        config5.rssiClose = 60;
        config5.rssiMiddle = 75;
        config5.rssiCloseProximity = 60;
        config5.dwellTimeMs = 200;
        config5.scanPeriodSec = 20;
        if (configRequest.mDiscoveryWindowInterval[ConfigRequest.NAN_BAND_5GHZ]
                == ConfigRequest.DW_INTERVAL_NOT_INIT) {
            config5.validDiscoveryWindowIntervalVal = false;
        } else {
            config5.validDiscoveryWindowIntervalVal = true;
            config5.discoveryWindowIntervalVal =
                    (byte) configRequest.mDiscoveryWindowInterval[ConfigRequest.NAN_BAND_5GHZ];
        }

        NanBandSpecificConfig config6 = new NanBandSpecificConfig();
        config6.rssiClose = 60;
        config6.rssiMiddle = 75;
        config6.rssiCloseProximity = 60;
        config6.dwellTimeMs = 200;
        config6.scanPeriodSec = 20;
        if (configRequest.mDiscoveryWindowInterval[ConfigRequest.NAN_BAND_6GHZ]
                == ConfigRequest.DW_INTERVAL_NOT_INIT) {
            config6.validDiscoveryWindowIntervalVal = false;
        } else {
            config6.validDiscoveryWindowIntervalVal = true;
            config6.discoveryWindowIntervalVal =
                    (byte) configRequest.mDiscoveryWindowInterval[ConfigRequest.NAN_BAND_6GHZ];
        }

        return new NanBandSpecificConfig[]{config24, config5, config6};
    }

    private static NanEnableRequest createNanEnableRequest(
            ConfigRequest configRequest, boolean notifyIdentityChange,
            NanConfigRequestSupplemental configSupplemental,
            int macAddressRandomizationIntervalSec, WifiNanIface.PowerParameters powerParameters) {
        NanEnableRequest req = new NanEnableRequest();
        NanBandSpecificConfig[] nanBandSpecificConfigs =
                createNanBandSpecificConfigs(configRequest);

        req.operateInBand = new boolean[3];
        req.operateInBand[NanBandIndex.NAN_BAND_24GHZ] = true;
        req.operateInBand[NanBandIndex.NAN_BAND_5GHZ] = configRequest.mSupport5gBand;
        req.operateInBand[NanBandIndex.NAN_BAND_6GHZ] = configRequest.mSupport6gBand;
        req.hopCountMax = 2;
        req.configParams = new NanConfigRequest();
        req.configParams.masterPref = (byte) configRequest.mMasterPreference;
        req.configParams.disableDiscoveryAddressChangeIndication = !notifyIdentityChange;
        req.configParams.disableStartedClusterIndication = !notifyIdentityChange;
        req.configParams.disableJoinedClusterIndication = !notifyIdentityChange;
        req.configParams.includePublishServiceIdsInBeacon = true;
        req.configParams.numberOfPublishServiceIdsInBeacon = 0;
        req.configParams.includeSubscribeServiceIdsInBeacon = true;
        req.configParams.numberOfSubscribeServiceIdsInBeacon = 0;
        req.configParams.rssiWindowSize = 8;
        req.configParams.macAddressRandomizationIntervalSec = macAddressRandomizationIntervalSec;

        req.configParams.bandSpecificConfig = new NanBandSpecificConfig[3];
        req.configParams.bandSpecificConfig[NanBandIndex.NAN_BAND_24GHZ] =
                nanBandSpecificConfigs[0];
        req.configParams.bandSpecificConfig[NanBandIndex.NAN_BAND_5GHZ] = nanBandSpecificConfigs[1];
        req.configParams.bandSpecificConfig[NanBandIndex.NAN_BAND_6GHZ] = nanBandSpecificConfigs[2];

        req.debugConfigs = new NanDebugConfig();
        req.debugConfigs.validClusterIdVals = true;
        req.debugConfigs.clusterIdTopRangeVal = (char) configRequest.mClusterHigh;
        req.debugConfigs.clusterIdBottomRangeVal = (char) configRequest.mClusterLow;
        req.debugConfigs.validIntfAddrVal = false;
        req.debugConfigs.intfAddrVal = new byte[6];
        req.debugConfigs.validOuiVal = false;
        req.debugConfigs.ouiVal = 0;
        req.debugConfigs.validRandomFactorForceVal = false;
        req.debugConfigs.randomFactorForceVal = 0;
        req.debugConfigs.validHopCountForceVal = false;
        req.debugConfigs.hopCountForceVal = 0;
        req.debugConfigs.validDiscoveryChannelVal = false;
        req.debugConfigs.discoveryChannelMhzVal = new int[3];
        req.debugConfigs.discoveryChannelMhzVal[NanBandIndex.NAN_BAND_24GHZ] = 0;
        req.debugConfigs.discoveryChannelMhzVal[NanBandIndex.NAN_BAND_5GHZ] = 0;
        req.debugConfigs.discoveryChannelMhzVal[NanBandIndex.NAN_BAND_6GHZ] = 0;
        req.debugConfigs.validUseBeaconsInBandVal = false;
        req.debugConfigs.useBeaconsInBandVal = new boolean[3];
        req.debugConfigs.useBeaconsInBandVal[NanBandIndex.NAN_BAND_24GHZ] = true;
        req.debugConfigs.useBeaconsInBandVal[NanBandIndex.NAN_BAND_5GHZ] = true;
        req.debugConfigs.useBeaconsInBandVal[NanBandIndex.NAN_BAND_6GHZ] = true;
        req.debugConfigs.validUseSdfInBandVal = false;
        req.debugConfigs.useSdfInBandVal = new boolean[3];
        req.debugConfigs.useSdfInBandVal[NanBandIndex.NAN_BAND_24GHZ] = true;
        req.debugConfigs.useSdfInBandVal[NanBandIndex.NAN_BAND_5GHZ] = true;
        req.debugConfigs.useSdfInBandVal[NanBandIndex.NAN_BAND_6GHZ] = true;
        updateConfigForPowerSettings(req.configParams, configSupplemental, powerParameters);
        updateConfigRequestVendorData(req.configParams, configRequest);
        return req;
    }

    private static NanConfigRequest createNanConfigRequest(
            ConfigRequest configRequest, boolean notifyIdentityChange,
            NanConfigRequestSupplemental configSupplemental,
            int macAddressRandomizationIntervalSec, WifiNanIface.PowerParameters powerParameters) {
        NanConfigRequest req = new NanConfigRequest();
        NanBandSpecificConfig[] nanBandSpecificConfigs =
                createNanBandSpecificConfigs(configRequest);

        req.masterPref = (byte) configRequest.mMasterPreference;
        req.disableDiscoveryAddressChangeIndication = !notifyIdentityChange;
        req.disableStartedClusterIndication = !notifyIdentityChange;
        req.disableJoinedClusterIndication = !notifyIdentityChange;
        req.includePublishServiceIdsInBeacon = true;
        req.numberOfPublishServiceIdsInBeacon = 0;
        req.includeSubscribeServiceIdsInBeacon = true;
        req.numberOfSubscribeServiceIdsInBeacon = 0;
        req.rssiWindowSize = 8;
        req.macAddressRandomizationIntervalSec = macAddressRandomizationIntervalSec;

        req.bandSpecificConfig = new NanBandSpecificConfig[3];
        req.bandSpecificConfig[NanBandIndex.NAN_BAND_24GHZ] = nanBandSpecificConfigs[0];
        req.bandSpecificConfig[NanBandIndex.NAN_BAND_5GHZ] = nanBandSpecificConfigs[1];
        req.bandSpecificConfig[NanBandIndex.NAN_BAND_6GHZ] = nanBandSpecificConfigs[2];
        updateConfigForPowerSettings(req, configSupplemental, powerParameters);
        updateConfigRequestVendorData(req, configRequest);
        return req;
    }

    /**
     * Update the NAN configuration to reflect the current power settings
     */
    private static void updateConfigForPowerSettings(NanConfigRequest req,
            NanConfigRequestSupplemental configSupplemental,
            WifiNanIface.PowerParameters powerParameters) {
        updateSingleConfigForPowerSettings(req.bandSpecificConfig[NanBandIndex.NAN_BAND_5GHZ],
                powerParameters.discoveryWindow5Ghz);
        updateSingleConfigForPowerSettings(req.bandSpecificConfig[NanBandIndex.NAN_BAND_24GHZ],
                powerParameters.discoveryWindow24Ghz);
        updateSingleConfigForPowerSettings(req.bandSpecificConfig[NanBandIndex.NAN_BAND_6GHZ],
                powerParameters.discoveryWindow6Ghz);

        configSupplemental.discoveryBeaconIntervalMs = powerParameters.discoveryBeaconIntervalMs;
        configSupplemental.numberOfSpatialStreamsInDiscovery =
                powerParameters.numberOfSpatialStreamsInDiscovery;
        configSupplemental.enableDiscoveryWindowEarlyTermination =
                powerParameters.enableDiscoveryWindowEarlyTermination;
    }

    private static void updateSingleConfigForPowerSettings(
            NanBandSpecificConfig cfg, int override) {
        if (override != -1) {
            cfg.validDiscoveryWindowIntervalVal = true;
            cfg.discoveryWindowIntervalVal = (byte) override;
        }
    }

    private static void updateConfigRequestVendorData(
            NanConfigRequest halReq, ConfigRequest frameworkReq) {
        if (SdkLevel.isAtLeastV() && WifiHalAidlImpl.isServiceVersionAtLeast(2)
                && !frameworkReq.getVendorData().isEmpty()) {
            halReq.vendorData =
                    HalAidlUtil.frameworkToHalOuiKeyedDataList(frameworkReq.getVendorData());
        }
    }

    private static NanPublishRequest createNanPublishRequest(
            byte publishId, PublishConfig publishConfig, byte[] nik) {
        NanPublishRequest req = new NanPublishRequest();
        req.baseConfigs = new NanDiscoveryCommonConfig();
        req.baseConfigs.sessionId = publishId;
        req.baseConfigs.ttlSec = (char) publishConfig.mTtlSec;
        req.baseConfigs.discoveryWindowPeriod = 1;
        req.baseConfigs.discoveryCount = 0;
        req.baseConfigs.serviceName = copyArray(publishConfig.mServiceName);
        req.baseConfigs.discoveryMatchIndicator = NanMatchAlg.MATCH_NEVER;
        if (publishConfig.mServiceSpecificInfo != null
                && publishConfig.mServiceSpecificInfo.length > 255) {
            req.baseConfigs.extendedServiceSpecificInfo =
                    copyArray(publishConfig.mServiceSpecificInfo);
            req.baseConfigs.serviceSpecificInfo = new byte[0];
        } else {
            req.baseConfigs.serviceSpecificInfo = copyArray(publishConfig.mServiceSpecificInfo);
            req.baseConfigs.extendedServiceSpecificInfo = new byte[0];
        }
        if (publishConfig.mPublishType == PublishConfig.PUBLISH_TYPE_UNSOLICITED) {
            req.baseConfigs.txMatchFilter = copyArray(publishConfig.mMatchFilter);
            req.baseConfigs.rxMatchFilter = new byte[0];
        } else {
            req.baseConfigs.rxMatchFilter = copyArray(publishConfig.mMatchFilter);
            req.baseConfigs.txMatchFilter = new byte[0];
        }
        req.baseConfigs.useRssiThreshold = false;
        req.baseConfigs.disableDiscoveryTerminationIndication =
                !publishConfig.mEnableTerminateNotification;
        req.baseConfigs.disableMatchExpirationIndication = true;
        req.baseConfigs.disableFollowupReceivedIndication = false;

        req.autoAcceptDataPathRequests = false;

        req.baseConfigs.rangingRequired = publishConfig.mEnableRanging;

        req.baseConfigs.securityConfig = new NanDataPathSecurityConfig();
        req.baseConfigs.securityConfig.pmk = new byte[32];
        req.baseConfigs.securityConfig.passphrase = new byte[0];
        req.baseConfigs.securityConfig.scid = new byte[16];
        req.baseConfigs.securityConfig.securityType = NanDataPathSecurityType.OPEN;
        WifiAwareDataPathSecurityConfig securityConfig = publishConfig.getSecurityConfig();
        if (securityConfig != null) {
            req.baseConfigs.securityConfig.cipherType = getHalCipherSuiteType(
                    securityConfig.getCipherSuite());
            if (securityConfig.getPmk() != null && securityConfig.getPmk().length != 0) {
                req.baseConfigs.securityConfig.securityType = NanDataPathSecurityType.PMK;
                req.baseConfigs.securityConfig.pmk = copyArray(securityConfig.getPmk());
            }
            if (securityConfig.getPskPassphrase() != null
                    && securityConfig.getPskPassphrase().length() != 0) {
                req.baseConfigs.securityConfig.securityType = NanDataPathSecurityType.PASSPHRASE;
                req.baseConfigs.securityConfig.passphrase =
                        securityConfig.getPskPassphrase().getBytes();
            }
            if (securityConfig.getPmkId() != null && securityConfig.getPmkId().length != 0) {
                req.baseConfigs.securityConfig.scid = copyArray(securityConfig.getPmkId());
            }
        }

        req.baseConfigs.enableSessionSuspendability = SdkLevel.isAtLeastU()
                && publishConfig.isSuspendable();

        req.publishType = publishConfig.mPublishType;
        req.txType = NanTxType.BROADCAST;
        req.pairingConfig = createAidlPairingConfig(publishConfig.getPairingConfig());
        req.identityKey = copyArray(nik, 16);

        if (SdkLevel.isAtLeastV() && !publishConfig.getVendorData().isEmpty()) {
            req.vendorData =
                    HalAidlUtil.frameworkToHalOuiKeyedDataList(publishConfig.getVendorData());
        }

        return req;
    }

    private static NanSubscribeRequest createNanSubscribeRequest(
            byte subscribeId, SubscribeConfig subscribeConfig, byte[] nik) {
        NanSubscribeRequest req = new NanSubscribeRequest();
        req.baseConfigs = new NanDiscoveryCommonConfig();
        req.baseConfigs.sessionId = subscribeId;
        req.baseConfigs.ttlSec = (char) subscribeConfig.mTtlSec;
        req.baseConfigs.discoveryWindowPeriod = 1;
        req.baseConfigs.discoveryCount = 0;
        req.baseConfigs.serviceName = copyArray(subscribeConfig.mServiceName);
        req.baseConfigs.discoveryMatchIndicator = NanMatchAlg.MATCH_ONCE;
        if (subscribeConfig.mServiceSpecificInfo != null
                && subscribeConfig.mServiceSpecificInfo.length > 255) {
            req.baseConfigs.extendedServiceSpecificInfo =
                    copyArray(subscribeConfig.mServiceSpecificInfo);
            req.baseConfigs.serviceSpecificInfo = new byte[0];
        } else {
            req.baseConfigs.serviceSpecificInfo = copyArray(subscribeConfig.mServiceSpecificInfo);
            req.baseConfigs.extendedServiceSpecificInfo = new byte[0];
        }
        if (subscribeConfig.mSubscribeType == SubscribeConfig.SUBSCRIBE_TYPE_ACTIVE) {
            req.baseConfigs.txMatchFilter = copyArray(subscribeConfig.mMatchFilter);
            req.baseConfigs.rxMatchFilter = new byte[0];
        } else {
            req.baseConfigs.rxMatchFilter = copyArray(subscribeConfig.mMatchFilter);
            req.baseConfigs.txMatchFilter = new byte[0];
        }
        req.baseConfigs.useRssiThreshold = false;
        req.baseConfigs.disableDiscoveryTerminationIndication =
                !subscribeConfig.mEnableTerminateNotification;
        req.baseConfigs.disableMatchExpirationIndication = false;
        req.baseConfigs.disableFollowupReceivedIndication = false;

        req.baseConfigs.rangingRequired =
                subscribeConfig.mMinDistanceMmSet || subscribeConfig.mMaxDistanceMmSet;
        req.baseConfigs.configRangingIndications = 0;
        if (subscribeConfig.mMinDistanceMmSet) {
            req.baseConfigs.distanceEgressCm = (char) Math.min(
                    subscribeConfig.mMinDistanceMm / 10, Short.MAX_VALUE);
            req.baseConfigs.configRangingIndications |= NanRangingIndication.EGRESS_MET_MASK;
        }
        if (subscribeConfig.mMaxDistanceMmSet) {
            req.baseConfigs.distanceIngressCm = (char) Math.min(
                    subscribeConfig.mMaxDistanceMm / 10, Short.MAX_VALUE);
            req.baseConfigs.configRangingIndications |= NanRangingIndication.INGRESS_MET_MASK;
        }

        // TODO: configure security
        req.baseConfigs.securityConfig = new NanDataPathSecurityConfig();
        req.baseConfigs.securityConfig.securityType = NanDataPathSecurityType.OPEN;
        req.baseConfigs.securityConfig.pmk = new byte[32];
        req.baseConfigs.securityConfig.passphrase = new byte[0];
        req.baseConfigs.securityConfig.scid = new byte[16];

        req.baseConfigs.enableSessionSuspendability = SdkLevel.isAtLeastU()
                && subscribeConfig.isSuspendable();

        req.subscribeType = subscribeConfig.mSubscribeType;
        req.pairingConfig = createAidlPairingConfig(subscribeConfig.getPairingConfig());
        req.identityKey = copyArray(nik, 16);
        req.intfAddr = new android.hardware.wifi.MacAddress[0];

        if (SdkLevel.isAtLeastV() && !subscribeConfig.getVendorData().isEmpty()) {
            req.vendorData =
                    HalAidlUtil.frameworkToHalOuiKeyedDataList(subscribeConfig.getVendorData());
        }

        return req;
    }

    private static NanPairingConfig createAidlPairingConfig(
            @Nullable AwarePairingConfig pairingConfig) {
        NanPairingConfig config = new NanPairingConfig();
        if (pairingConfig == null) {
            return config;
        }
        config.enablePairingCache = pairingConfig.isPairingCacheEnabled();
        config.enablePairingSetup = pairingConfig.isPairingSetupEnabled();
        config.enablePairingVerification = pairingConfig.isPairingVerificationEnabled();
        config.supportedBootstrappingMethods = pairingConfig.getBootstrappingMethods();
        return config;
    }

    private static NanTransmitFollowupRequest createNanTransmitFollowupRequest(
            byte pubSubId, int requesterInstanceId, MacAddress dest, byte[] message) {
        NanTransmitFollowupRequest req = new NanTransmitFollowupRequest();
        req.discoverySessionId = pubSubId;
        req.peerId = requesterInstanceId;
        req.addr = dest.toByteArray();
        req.isHighPriority = false;
        req.shouldUseDiscoveryWindow = true;
        if (message != null && message.length > 255) {
            req.extendedServiceSpecificInfo = copyArray(message);
            req.serviceSpecificInfo = new byte[0];
        } else {
            req.serviceSpecificInfo = copyArray(message);
            req.extendedServiceSpecificInfo = new byte[0];
        }
        req.disableFollowupResultIndication = false;
        return req;
    }

    private static NanInitiateDataPathRequest createNanInitiateDataPathRequest(
            int peerId, int channelRequestType, int channel, MacAddress peer, String interfaceName,
            boolean isOutOfBand, byte[] appInfo, WifiAwareDataPathSecurityConfig securityConfig,
            byte pubSubId) {
        NanInitiateDataPathRequest req = new NanInitiateDataPathRequest();
        req.peerId = peerId;
        req.peerDiscMacAddr = peer.toByteArray();
        req.channelRequestType = WifiNanIface.NanDataPathChannelCfg.toAidl(channelRequestType);
        req.serviceNameOutOfBand = new byte[0];
        req.channel = channel;
        req.ifaceName = interfaceName;
        req.securityConfig = new NanDataPathSecurityConfig();
        req.securityConfig.pmk = new byte[32];
        req.securityConfig.passphrase = new byte[0];
        req.securityConfig.scid = new byte[16];
        req.securityConfig.securityType = NanDataPathSecurityType.OPEN;
        if (securityConfig != null) {
            req.securityConfig.cipherType = getHalCipherSuiteType(securityConfig.getCipherSuite());
            if (securityConfig.getPmk() != null && securityConfig.getPmk().length != 0) {
                req.securityConfig.securityType = NanDataPathSecurityType.PMK;
                req.securityConfig.pmk = copyArray(securityConfig.getPmk());
                req.securityConfig.passphrase = new byte[0];
            }
            if (securityConfig.getPskPassphrase() != null
                    && securityConfig.getPskPassphrase().length() != 0) {
                req.securityConfig.securityType = NanDataPathSecurityType.PASSPHRASE;
                req.securityConfig.passphrase = securityConfig.getPskPassphrase().getBytes();
                req.securityConfig.pmk = new byte[32];
            }
            req.securityConfig.scid = copyArray(securityConfig.getPmkId(), 16);
        }

        if (req.securityConfig.securityType != NanDataPathSecurityType.OPEN && isOutOfBand) {
            req.serviceNameOutOfBand = WifiNanIface.SERVICE_NAME_FOR_OOB_DATA_PATH
                    .getBytes(StandardCharsets.UTF_8);
        }
        req.appInfo = copyArray(appInfo);
        req.discoverySessionId = pubSubId;
        return req;
    }

    private static NanPairingRequest createNanPairingRequest(int peerId, MacAddress peer,
            byte[] pairingIdentityKey, boolean enablePairingCache, int requestType, byte[] pmk,
            String password, int akm, int cipherSuite) {
        NanPairingRequest request = new NanPairingRequest();
        request.peerId = peerId;
        request.peerDiscMacAddr = peer.toByteArray();
        request.pairingIdentityKey = copyArray(pairingIdentityKey, 16);
        request.enablePairingCache = enablePairingCache;
        request.requestType = requestType == NAN_PAIRING_REQUEST_TYPE_SETUP
                ? NanPairingRequestType.NAN_PAIRING_SETUP
                : NanPairingRequestType.NAN_PAIRING_VERIFICATION;
        request.securityConfig = new NanPairingSecurityConfig();
        request.securityConfig.pmk = new byte[32];
        request.securityConfig.cipherType = cipherSuite;
        request.securityConfig.passphrase = new byte[0];
        if (pmk != null && pmk.length != 0) {
            request.securityConfig.securityType = NanPairingSecurityType.PMK;
            request.securityConfig.pmk = copyArray(pmk);
            request.securityConfig.akm = akm == NAN_PAIRING_AKM_SAE ? NanPairingAkm.SAE
                    : NanPairingAkm.PASN;
        } else if (password != null && password.length() != 0) {
            request.securityConfig.securityType = NanPairingSecurityType.PASSPHRASE;
            request.securityConfig.passphrase = password.getBytes();
            request.securityConfig.akm = NanPairingAkm.SAE;
        } else {
            request.securityConfig.securityType = NanPairingSecurityType.OPPORTUNISTIC;
            request.securityConfig.akm = NanPairingAkm.PASN;
        }
        return request;
    }

    private static NanRespondToPairingIndicationRequest createNanPairingResponse(
            int pairingInstanceId, boolean accept, byte[] pairingIdentityKey,
            boolean enablePairingCache, int requestType, byte[] pmk, String password, int akm,
            int cipherSuite) {
        NanRespondToPairingIndicationRequest request = new NanRespondToPairingIndicationRequest();
        request.pairingInstanceId = pairingInstanceId;
        request.acceptRequest = accept;
        request.pairingIdentityKey = copyArray(pairingIdentityKey, 16);
        request.enablePairingCache = enablePairingCache;
        request.requestType = requestType == NAN_PAIRING_REQUEST_TYPE_SETUP
                ? NanPairingRequestType.NAN_PAIRING_SETUP
                : NanPairingRequestType.NAN_PAIRING_VERIFICATION;
        request.securityConfig = new NanPairingSecurityConfig();
        request.securityConfig.pmk = new byte[32];
        request.securityConfig.passphrase = new byte[0];
        request.securityConfig.cipherType = cipherSuite;
        if (pmk != null && pmk.length != 0) {
            request.securityConfig.securityType = NanPairingSecurityType.PMK;
            request.securityConfig.pmk = copyArray(pmk);
            request.securityConfig.akm = akm == NAN_PAIRING_AKM_SAE ? NanPairingAkm.SAE
                    : NanPairingAkm.PASN;
        } else if (password != null && password.length() != 0) {
            request.securityConfig.securityType = NanPairingSecurityType.PASSPHRASE;
            request.securityConfig.passphrase = password.getBytes();
            request.securityConfig.akm = NanPairingAkm.SAE;
        } else {
            request.securityConfig.securityType = NanPairingSecurityType.OPPORTUNISTIC;
            request.securityConfig.akm = NanPairingAkm.PASN;
        }
        return request;
    }

    private static NanRespondToDataPathIndicationRequest
            createNanRespondToDataPathIndicationRequest(boolean accept, int ndpId,
            String interfaceName, byte[] appInfo, boolean isOutOfBand,
            WifiAwareDataPathSecurityConfig securityConfig, byte pubSubId) {
        NanRespondToDataPathIndicationRequest req = new NanRespondToDataPathIndicationRequest();
        req.acceptRequest = accept;
        req.ndpInstanceId = ndpId;
        req.ifaceName = interfaceName;
        req.serviceNameOutOfBand = new byte[0];
        req.securityConfig = new NanDataPathSecurityConfig();
        req.securityConfig.pmk = new byte[32];
        req.securityConfig.passphrase = new byte[0];
        req.securityConfig.scid = new byte[16];
        req.securityConfig.securityType = NanDataPathSecurityType.OPEN;
        if (securityConfig != null) {
            req.securityConfig.cipherType = getHalCipherSuiteType(securityConfig.getCipherSuite());
            if (securityConfig.getPmk() != null && securityConfig.getPmk().length != 0) {
                req.securityConfig.securityType = NanDataPathSecurityType.PMK;
                req.securityConfig.pmk = copyArray(securityConfig.getPmk());
            }
            if (securityConfig.getPskPassphrase() != null
                    && securityConfig.getPskPassphrase().length() != 0) {
                req.securityConfig.securityType = NanDataPathSecurityType.PASSPHRASE;
                req.securityConfig.passphrase = securityConfig.getPskPassphrase().getBytes();
            }
            req.securityConfig.scid = copyArray(securityConfig.getPmkId(), 16);
        }

        if (req.securityConfig.securityType != NanDataPathSecurityType.OPEN && isOutOfBand) {
            req.serviceNameOutOfBand = WifiNanIface.SERVICE_NAME_FOR_OOB_DATA_PATH
                    .getBytes(StandardCharsets.UTF_8);
        }
        req.appInfo = copyArray(appInfo);
        req.discoverySessionId = pubSubId;
        return req;
    }

    private static int getHalCipherSuiteType(int frameworkCipherSuites) {
        switch (frameworkCipherSuites) {
            case WIFI_AWARE_CIPHER_SUITE_NCS_SK_128:
                return NanCipherSuiteType.SHARED_KEY_128_MASK;
            case WIFI_AWARE_CIPHER_SUITE_NCS_SK_256:
                return NanCipherSuiteType.SHARED_KEY_256_MASK;
            case WIFI_AWARE_CIPHER_SUITE_NCS_PK_128:
                return NanCipherSuiteType.PUBLIC_KEY_2WDH_256_MASK;
            case WIFI_AWARE_CIPHER_SUITE_NCS_PK_256:
                return NanCipherSuiteType.PUBLIC_KEY_2WDH_256_MASK;
        }
        return NanCipherSuiteType.NONE;
    }

    private static byte[] copyArray(byte[] source) {
        return copyArray(source, 0);
    }

    private static byte[] copyArray(byte[] source, int length) {
        return source != null && source.length != 0 ? source.clone() : new byte[length];
    }

    private boolean checkIfaceAndLogFailure(String methodStr) {
        if (mWifiNanIface == null) {
            Log.e(TAG, "Unable to call " + methodStr + " because iface is null.");
            return false;
        }
        return true;
    }

    private void handleRemoteException(RemoteException e, String methodStr) {
        mWifiNanIface = null;
        mIfaceName = null;
        Log.e(TAG, methodStr + " failed with remote exception: " + e);
    }

    private void handleServiceSpecificException(ServiceSpecificException e, String methodStr) {
        Log.e(TAG, methodStr + " failed with service-specific exception: " + e);
    }
}
