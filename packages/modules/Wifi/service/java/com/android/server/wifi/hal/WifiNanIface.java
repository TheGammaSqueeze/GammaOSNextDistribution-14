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

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.net.MacAddress;
import android.net.wifi.OuiKeyedData;
import android.net.wifi.aware.AwarePairingConfig;
import android.net.wifi.aware.ConfigRequest;
import android.net.wifi.aware.PublishConfig;
import android.net.wifi.aware.SubscribeConfig;
import android.net.wifi.aware.WifiAwareChannelInfo;
import android.net.wifi.aware.WifiAwareDataPathSecurityConfig;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;
import com.android.server.wifi.aware.Capabilities;
import com.android.server.wifi.aware.PairingConfigManager;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Wrapper class for IWifiNanIface HAL calls.
 */
public class WifiNanIface implements WifiHal.WifiInterface {
    private static final String TAG = "WifiNanIface";
    private IWifiNanIface mWifiNanIface;

    @VisibleForTesting
    static final String SERVICE_NAME_FOR_OOB_DATA_PATH = "Wi-Fi Aware Data Path";

    /**
     * Event types for a cluster event indication.
     */
    public static class NanClusterEventType {
        public static final int DISCOVERY_MAC_ADDRESS_CHANGED = 0;
        public static final int STARTED_CLUSTER = 1;
        public static final int JOINED_CLUSTER = 2;

        /**
         * Convert NanClusterEventType from HIDL to framework.
         */
        public static int fromHidl(int code) {
            switch (code) {
                case android.hardware.wifi.V1_0.NanClusterEventType.DISCOVERY_MAC_ADDRESS_CHANGED:
                    return DISCOVERY_MAC_ADDRESS_CHANGED;
                case android.hardware.wifi.V1_0.NanClusterEventType.STARTED_CLUSTER:
                    return STARTED_CLUSTER;
                case android.hardware.wifi.V1_0.NanClusterEventType.JOINED_CLUSTER:
                    return JOINED_CLUSTER;
                default:
                    Log.e(TAG, "Unknown NanClusterEventType received from HIDL: " + code);
                    return -1;
            }
        }

        /**
         * Convert NanClusterEventType from AIDL to framework.
         */
        public static int fromAidl(int code) {
            switch (code) {
                case android.hardware.wifi.NanClusterEventType.DISCOVERY_MAC_ADDRESS_CHANGED:
                    return DISCOVERY_MAC_ADDRESS_CHANGED;
                case android.hardware.wifi.NanClusterEventType.STARTED_CLUSTER:
                    return STARTED_CLUSTER;
                case android.hardware.wifi.NanClusterEventType.JOINED_CLUSTER:
                    return JOINED_CLUSTER;
                default:
                    Log.e(TAG, "Unknown NanClusterEventType received from HIDL: " + code);
                    return -1;
            }
        }
    }

    /**
     * NAN DP (data-path) channel config options.
     */
    public static class NanDataPathChannelCfg {
        public static final int CHANNEL_NOT_REQUESTED = 0;
        public static final int REQUEST_CHANNEL_SETUP = 1;
        public static final int FORCE_CHANNEL_SETUP = 2;

        /**
         * Convert NanDataPathChannelCfg from framework to HIDL.
         */
        public static int toHidl(int code) {
            switch (code) {
                case CHANNEL_NOT_REQUESTED:
                    return android.hardware.wifi.V1_0.NanDataPathChannelCfg.CHANNEL_NOT_REQUESTED;
                case REQUEST_CHANNEL_SETUP:
                    return android.hardware.wifi.V1_0.NanDataPathChannelCfg.REQUEST_CHANNEL_SETUP;
                case FORCE_CHANNEL_SETUP:
                    return android.hardware.wifi.V1_0.NanDataPathChannelCfg.FORCE_CHANNEL_SETUP;
                default:
                    Log.e(TAG, "Unknown NanDataPathChannelCfg received from framework: " + code);
                    return -1;
            }
        }

        /**
         * Convert NanDataPathChannelCfg from framework to AIDL.
         */
        public static int toAidl(int code) {
            switch (code) {
                case CHANNEL_NOT_REQUESTED:
                    return android.hardware.wifi.NanDataPathChannelCfg.CHANNEL_NOT_REQUESTED;
                case REQUEST_CHANNEL_SETUP:
                    return android.hardware.wifi.NanDataPathChannelCfg.REQUEST_CHANNEL_SETUP;
                case FORCE_CHANNEL_SETUP:
                    return android.hardware.wifi.NanDataPathChannelCfg.FORCE_CHANNEL_SETUP;
                default:
                    Log.e(TAG, "Unknown NanDataPathChannelCfg received from framework: " + code);
                    return -1;
            }
        }
    }

    /**
     * Ranging in the context of discovery session indication controls.
     */
    public static class NanRangingIndication {
        public static final int CONTINUOUS_INDICATION_MASK = 1 << 0;
        public static final int INGRESS_MET_MASK = 1 << 1;
        public static final int EGRESS_MET_MASK = 1 << 2;

        /**
         * Convert NanRangingIndication from HIDL to framework.
         */
        public static int fromHidl(int rangingInd) {
            int frameworkRangingInd = 0;
            if ((android.hardware.wifi.V1_0.NanRangingIndication.CONTINUOUS_INDICATION_MASK
                    & rangingInd) != 0) {
                frameworkRangingInd |= CONTINUOUS_INDICATION_MASK;
            }
            if ((android.hardware.wifi.V1_0.NanRangingIndication.INGRESS_MET_MASK
                    & rangingInd) != 0) {
                frameworkRangingInd |= INGRESS_MET_MASK;
            }
            if ((android.hardware.wifi.V1_0.NanRangingIndication.EGRESS_MET_MASK
                    & rangingInd) != 0) {
                frameworkRangingInd |= EGRESS_MET_MASK;
            }
            return frameworkRangingInd;
        }

        /**
         * Convert NanRangingIndication from AIDL to framework.
         */
        public static int fromAidl(int rangingInd) {
            int frameworkRangingInd = 0;
            if ((android.hardware.wifi.NanRangingIndication.CONTINUOUS_INDICATION_MASK
                    & rangingInd) != 0) {
                frameworkRangingInd |= CONTINUOUS_INDICATION_MASK;
            }
            if ((android.hardware.wifi.NanRangingIndication.INGRESS_MET_MASK
                    & rangingInd) != 0) {
                frameworkRangingInd |= INGRESS_MET_MASK;
            }
            if ((android.hardware.wifi.NanRangingIndication.EGRESS_MET_MASK
                    & rangingInd) != 0) {
                frameworkRangingInd |= EGRESS_MET_MASK;
            }
            return frameworkRangingInd;
        }
    }

    /**
     * NAN API response codes used in request notifications and events.
     */
    public static class NanStatusCode {
        public static final int SUCCESS = 0;
        public static final int INTERNAL_FAILURE = 1;
        public static final int PROTOCOL_FAILURE = 2;
        public static final int INVALID_SESSION_ID = 3;
        public static final int NO_RESOURCES_AVAILABLE = 4;
        public static final int INVALID_ARGS = 5;
        public static final int INVALID_PEER_ID = 6;
        public static final int INVALID_NDP_ID = 7;
        public static final int NAN_NOT_ALLOWED = 8;
        public static final int NO_OTA_ACK = 9;
        public static final int ALREADY_ENABLED = 10;
        public static final int FOLLOWUP_TX_QUEUE_FULL = 11;
        public static final int UNSUPPORTED_CONCURRENCY_NAN_DISABLED = 12;
        public static final int INVALID_PAIRING_ID = 13;
        public static final int INVALID_BOOTSTRAPPING_ID = 14;
        public static final int REDUNDANT_REQUEST = 15;
        public static final int NOT_SUPPORTED = 16;
        public static final int NO_CONNECTION = 17;

        /**
         * Convert NanStatusCode from HIDL to framework.
         */
        public static int fromHidl(int code) {
            switch (code) {
                case android.hardware.wifi.V1_0.NanStatusType.SUCCESS:
                    return SUCCESS;
                case android.hardware.wifi.V1_0.NanStatusType.INTERNAL_FAILURE:
                    return INTERNAL_FAILURE;
                case android.hardware.wifi.V1_0.NanStatusType.PROTOCOL_FAILURE:
                    return PROTOCOL_FAILURE;
                case android.hardware.wifi.V1_0.NanStatusType.INVALID_SESSION_ID:
                    return INVALID_SESSION_ID;
                case android.hardware.wifi.V1_0.NanStatusType.NO_RESOURCES_AVAILABLE:
                    return NO_RESOURCES_AVAILABLE;
                case android.hardware.wifi.V1_0.NanStatusType.INVALID_ARGS:
                    return INVALID_ARGS;
                case android.hardware.wifi.V1_0.NanStatusType.INVALID_PEER_ID:
                    return INVALID_PEER_ID;
                case android.hardware.wifi.V1_0.NanStatusType.INVALID_NDP_ID:
                    return INVALID_NDP_ID;
                case android.hardware.wifi.V1_0.NanStatusType.NAN_NOT_ALLOWED:
                    return NAN_NOT_ALLOWED;
                case android.hardware.wifi.V1_0.NanStatusType.NO_OTA_ACK:
                    return NO_OTA_ACK;
                case android.hardware.wifi.V1_0.NanStatusType.ALREADY_ENABLED:
                    return ALREADY_ENABLED;
                case android.hardware.wifi.V1_0.NanStatusType.FOLLOWUP_TX_QUEUE_FULL:
                    return FOLLOWUP_TX_QUEUE_FULL;
                case android.hardware.wifi.V1_0.NanStatusType.UNSUPPORTED_CONCURRENCY_NAN_DISABLED:
                    return UNSUPPORTED_CONCURRENCY_NAN_DISABLED;
                default:
                    Log.e(TAG, "Unknown NanStatusType received from HIDL: " + code);
                    return -1;
            }
        }

        /**
         * Convert NanStatusCode from AIDL to framework.
         */
        public static int fromAidl(int code) {
            switch (code) {
                case android.hardware.wifi.NanStatusCode.SUCCESS:
                    return SUCCESS;
                case android.hardware.wifi.NanStatusCode.INTERNAL_FAILURE:
                    return INTERNAL_FAILURE;
                case android.hardware.wifi.NanStatusCode.PROTOCOL_FAILURE:
                    return PROTOCOL_FAILURE;
                case android.hardware.wifi.NanStatusCode.INVALID_SESSION_ID:
                    return INVALID_SESSION_ID;
                case android.hardware.wifi.NanStatusCode.NO_RESOURCES_AVAILABLE:
                    return NO_RESOURCES_AVAILABLE;
                case android.hardware.wifi.NanStatusCode.INVALID_ARGS:
                    return INVALID_ARGS;
                case android.hardware.wifi.NanStatusCode.INVALID_PEER_ID:
                    return INVALID_PEER_ID;
                case android.hardware.wifi.NanStatusCode.INVALID_NDP_ID:
                    return INVALID_NDP_ID;
                case android.hardware.wifi.NanStatusCode.NAN_NOT_ALLOWED:
                    return NAN_NOT_ALLOWED;
                case android.hardware.wifi.NanStatusCode.NO_OTA_ACK:
                    return NO_OTA_ACK;
                case android.hardware.wifi.NanStatusCode.ALREADY_ENABLED:
                    return ALREADY_ENABLED;
                case android.hardware.wifi.NanStatusCode.FOLLOWUP_TX_QUEUE_FULL:
                    return FOLLOWUP_TX_QUEUE_FULL;
                case android.hardware.wifi.NanStatusCode.UNSUPPORTED_CONCURRENCY_NAN_DISABLED:
                    return UNSUPPORTED_CONCURRENCY_NAN_DISABLED;
                case android.hardware.wifi.NanStatusCode.INVALID_PAIRING_ID:
                    return INVALID_PAIRING_ID;
                case android.hardware.wifi.NanStatusCode.INVALID_BOOTSTRAPPING_ID:
                    return INVALID_BOOTSTRAPPING_ID;
                case android.hardware.wifi.NanStatusCode.REDUNDANT_REQUEST:
                    return REDUNDANT_REQUEST;
                case android.hardware.wifi.NanStatusCode.NOT_SUPPORTED:
                    return NOT_SUPPORTED;
                case android.hardware.wifi.NanStatusCode.NO_CONNECTION:
                    return NO_CONNECTION;
                default:
                    Log.e(TAG, "Unknown NanStatusType received from AIDL: " + code);
                    return -1;
            }
        }
    }

    /**
     * Configuration parameters used in the call to enableAndConfigure.
     */
    public static class PowerParameters {
        public int discoveryWindow24Ghz;
        public int discoveryWindow5Ghz;
        public int discoveryWindow6Ghz;
        public int discoveryBeaconIntervalMs;
        public int numberOfSpatialStreamsInDiscovery;
        public boolean enableDiscoveryWindowEarlyTermination;
    }

    public WifiNanIface(@NonNull android.hardware.wifi.V1_0.IWifiNanIface nanIface) {
        Log.i(TAG, "Creating WifiNanIface using the HIDL implementation");
        mWifiNanIface = createWifiNanIfaceHidlImplMockable(nanIface);
    }

    public WifiNanIface(@NonNull android.hardware.wifi.IWifiNanIface nanIface) {
        mWifiNanIface = createWifiNanIfaceAidlImplMockable(nanIface);
    }

    protected WifiNanIfaceHidlImpl createWifiNanIfaceHidlImplMockable(
            android.hardware.wifi.V1_0.IWifiNanIface nanIface) {
        return new WifiNanIfaceHidlImpl(nanIface);
    }

    protected WifiNanIfaceAidlImpl createWifiNanIfaceAidlImplMockable(
            android.hardware.wifi.IWifiNanIface nanIface) {
        return new WifiNanIfaceAidlImpl(nanIface);
    }

    private <T> T validateAndCall(String methodStr, T defaultVal, @NonNull Supplier<T> supplier) {
        if (mWifiNanIface == null) {
            Log.wtf(TAG, "Cannot call " + methodStr + " because mWifiNanIface is null");
            return defaultVal;
        }
        return supplier.get();
    }

    /**
     * Enable verbose logging.
     */
    public void enableVerboseLogging(boolean verbose) {
        if (mWifiNanIface != null) {
            mWifiNanIface.enableVerboseLogging(verbose);
        }
    }

    /**
     * See comments for {@link IWifiNanIface#registerFrameworkCallback(Callback)}
     */
    public boolean registerFrameworkCallback(Callback cb) {
        return validateAndCall("registerFrameworkCallback", false,
                () -> mWifiNanIface.registerFrameworkCallback(cb));
    }

    /**
     * See comments for {@link IWifiNanIface#getName()}
     */
    @Override
    @Nullable
    public String getName() {
        return validateAndCall("getName", null,
                () -> mWifiNanIface.getName());
    }

    /**
     * See comments for {@link IWifiNanIface#getCapabilities(short)}
     */
    public boolean getCapabilities(short transactionId) {
        return validateAndCall("getCapabilities", false,
                () -> mWifiNanIface.getCapabilities(transactionId));
    }

    /**
     * See comments for {@link IWifiNanIface#enableAndConfigure(short, ConfigRequest, boolean,
     *                         boolean, boolean, boolean, int, int, PowerParameters)}
     */
    public boolean enableAndConfigure(short transactionId, ConfigRequest configRequest,
            boolean notifyIdentityChange, boolean initialConfiguration, boolean rangingEnabled,
            boolean isInstantCommunicationEnabled, int instantModeChannel, int clusterId,
            int macAddressRandomizationIntervalSec, PowerParameters powerParameters) {
        return validateAndCall("enableAndConfigure", false,
                () -> mWifiNanIface.enableAndConfigure(transactionId, configRequest,
                        notifyIdentityChange, initialConfiguration, rangingEnabled,
                        isInstantCommunicationEnabled, instantModeChannel, clusterId,
                        macAddressRandomizationIntervalSec, powerParameters));
    }

    /**
     * See comments for {@link IWifiNanIface#disable(short)}
     */
    public boolean disable(short transactionId) {
        return validateAndCall("disable", false,
                () -> mWifiNanIface.disable(transactionId));
    }

    /**
     * See comments for {@link IWifiNanIface#publish(short, byte, PublishConfig, byte[])}
     */
    public boolean publish(short transactionId, byte publishId, PublishConfig publishConfig,
            byte[] nik) {
        return validateAndCall("publish", false,
                () -> mWifiNanIface.publish(transactionId, publishId, publishConfig, nik));
    }

    /**
     * See comments for {@link IWifiNanIface#subscribe(short, byte, SubscribeConfig, byte[])}
     */
    public boolean subscribe(short transactionId, byte subscribeId,
            SubscribeConfig subscribeConfig, byte[] nik) {
        return validateAndCall("subscribe", false,
                () -> mWifiNanIface.subscribe(transactionId, subscribeId, subscribeConfig, nik));
    }

    /**
     * See comments for {@link IWifiNanIface#sendMessage(short, byte, int, MacAddress, byte[])}
     */
    public boolean sendMessage(short transactionId, byte pubSubId, int requestorInstanceId,
            MacAddress dest, byte[] message) {
        return validateAndCall("sendMessage", false,
                () -> mWifiNanIface.sendMessage(transactionId, pubSubId, requestorInstanceId,
                        dest, message));
    }

    /**
     * See comments for {@link IWifiNanIface#stopPublish(short, byte)}
     */
    public boolean stopPublish(short transactionId, byte pubSubId) {
        return validateAndCall("stopPublish", false,
                () -> mWifiNanIface.stopPublish(transactionId, pubSubId));
    }

    /**
     * See comments for {@link IWifiNanIface#stopSubscribe(short, byte)}
     */
    public boolean stopSubscribe(short transactionId, byte pubSubId) {
        return validateAndCall("stopSubscribe", false,
                () -> mWifiNanIface.stopSubscribe(transactionId, pubSubId));
    }

    /**
     * See comments for {@link IWifiNanIface#createAwareNetworkInterface(short, String)}
     */
    public boolean createAwareNetworkInterface(short transactionId, String interfaceName) {
        return validateAndCall("createAwareNetworkInterface", false,
                () -> mWifiNanIface.createAwareNetworkInterface(transactionId, interfaceName));
    }

    /**
     * See comments for {@link IWifiNanIface#deleteAwareNetworkInterface(short, String)}
     */
    public boolean deleteAwareNetworkInterface(short transactionId, String interfaceName) {
        return validateAndCall("deleteAwareNetworkInterface", false,
                () -> mWifiNanIface.deleteAwareNetworkInterface(transactionId, interfaceName));
    }

    /**
     * See comments for
     * {@link IWifiNanIface#initiateDataPath(short, int, int, int, MacAddress, String, boolean, byte[], Capabilities, WifiAwareDataPathSecurityConfig, byte)}
     */
    public boolean initiateDataPath(short transactionId, int peerId, int channelRequestType,
            int channel, MacAddress peer, String interfaceName,
            boolean isOutOfBand, byte[] appInfo, Capabilities capabilities,
            WifiAwareDataPathSecurityConfig securityConfig, byte pubSubId) {
        return validateAndCall("initiateDataPath", false,
                () -> mWifiNanIface.initiateDataPath(transactionId, peerId, channelRequestType,
                        channel, peer, interfaceName, isOutOfBand, appInfo, capabilities,
                        securityConfig, pubSubId));
    }

    /**
     * See comments for
     * {@link IWifiNanIface#respondToDataPathRequest(short, boolean, int, String, byte[], boolean, Capabilities, WifiAwareDataPathSecurityConfig, byte)}
     */
    public boolean respondToDataPathRequest(short transactionId, boolean accept, int ndpId,
            String interfaceName, byte[] appInfo,
            boolean isOutOfBand, Capabilities capabilities,
            WifiAwareDataPathSecurityConfig securityConfig, byte pubSubId) {
        return validateAndCall("respondToDataPathRequest", false,
                () -> mWifiNanIface.respondToDataPathRequest(transactionId, accept, ndpId,
                        interfaceName, appInfo, isOutOfBand, capabilities, securityConfig,
                        pubSubId));
    }

    /**
     * See comments for {@link IWifiNanIface#endDataPath(short, int)}
     */
    public boolean endDataPath(short transactionId, int ndpId) {
        return validateAndCall("endDataPath", false,
                () -> mWifiNanIface.endDataPath(transactionId, ndpId));
    }

    /**
     * {@link IWifiNanIface#initiateNanPairingRequest(short, int, MacAddress, byte[], boolean, int, byte[], String, int, int)}
     */
    public boolean initiatePairing(short transactionId, int peerId, MacAddress peer,
            byte[] pairingIdentityKey, boolean enablePairingCache, int requestType, byte[] pmk,
            String password, int akm, int cipherSuite) {
        return validateAndCall("initiatePairing", false,
                () -> mWifiNanIface.initiateNanPairingRequest(transactionId, peerId, peer,
                        pairingIdentityKey, enablePairingCache, requestType, pmk, password, akm,
                        cipherSuite));
    }

    /**
     * {@link IWifiNanIface#endPairing(short, int)}
     */
    public boolean endPairing(short transactionId, int pairingId) {
        return validateAndCall("initiatePairing", false,
                () -> mWifiNanIface.endPairing(transactionId, pairingId));
    }

    /**
     * {@link IWifiNanIface#respondToPairingRequest(short, int, boolean, byte[], boolean, int, byte[], String, int, int)}
     */
    public boolean respondToPairingRequest(short transactionId, int pairingId, boolean accept,
            byte[] pairingIdentityKey, boolean enablePairingCache, int requestType, byte[] pmk,
            String password, int akm, int cipherSuite) {
        return validateAndCall("respondToPairingRequest", false,
                () -> mWifiNanIface.respondToPairingRequest(transactionId, pairingId, accept,
                        pairingIdentityKey, enablePairingCache, requestType, pmk, password, akm,
                        cipherSuite));
    }
    /**
     * {@link IWifiNanIface#initiateNanBootstrappingRequest(short, int, MacAddress, int, byte[], byte, boolean)}
     */
    public boolean initiateBootstrapping(short transactionId, int peerId, MacAddress peer,
            int method, byte[] cookie, byte pubSubId, boolean isComeBack) {
        return validateAndCall("initiateBootstrapping", false,
                () -> mWifiNanIface.initiateNanBootstrappingRequest(transactionId, peerId, peer,
                        method, cookie, pubSubId, isComeBack));
    }
    /**
     * {@link IWifiNanIface#respondToNanBootstrappingRequest(short, int, boolean, byte)}
     */
    public boolean respondToBootstrappingRequest(short transactionId, int bootstrappingId,
            boolean accept, byte pubSubId) {
        return validateAndCall("initiateBootstrapping", false,
                () -> mWifiNanIface.respondToNanBootstrappingRequest(transactionId, bootstrappingId,
                        accept, pubSubId));
    }

    /**
     * See comments for {@link IWifiNanIface#suspend(short, byte)}
     */
    public boolean suspendRequest(short transactionId, byte pubSubId) {
        return validateAndCall("suspendRequest", false,
            () -> mWifiNanIface.suspend(transactionId, pubSubId));
    }

    /**
     * See comments for {@link IWifiNanIface#resume(short, byte)}
     */
    public boolean resumeRequest(short transactionId, byte pubSubId) {
        return validateAndCall("resumeRequest", false,
            () -> mWifiNanIface.resume(transactionId, pubSubId));
    }

    /**
     * Framework callback object. Will get called when the equivalent events are received
     * from the HAL.
     */
    public interface Callback {
        /**
         * Invoked in response to a capability request.
         * @param id ID corresponding to the original request.
         * @param capabilities Capability data.
         */
        void notifyCapabilitiesResponse(short id, Capabilities capabilities);

        /**
         * Invoked in response to an enable request.
         * @param id ID corresponding to the original request.
         * @param status Status the operation (see {@link NanStatusCode}).
         */
        void notifyEnableResponse(short id, int status);

        /**
         * Invoked in response to a config request.
         * @param id ID corresponding to the original request.
         * @param status Status the operation (see {@link NanStatusCode}).
         */
        void notifyConfigResponse(short id, int status);

        /**
         * Invoked in response to a disable request.
         * @param id ID corresponding to the original request.
         * @param status Status the operation (see {@link NanStatusCode}).
         */
        void notifyDisableResponse(short id, int status);

        /**
         * Invoked to notify the status of the start publish request.
         * @param id ID corresponding to the original request.
         * @param status Status the operation (see {@link NanStatusCode}).
         * @param publishId
         */
        void notifyStartPublishResponse(short id, int status, byte publishId);

        /**
         * Invoked to notify the status of the start subscribe request.
         * @param id ID corresponding to the original request.
         * @param status Status the operation (see {@link NanStatusCode}).
         * @param subscribeId ID of the new subscribe session (if successfully created).
         */
        void notifyStartSubscribeResponse(short id, int status, byte subscribeId);

        /**
         * Invoked in response to a transmit followup request.
         * @param id ID corresponding to the original request.
         * @param status Status the operation (see {@link NanStatusCode}).
         */
        void notifyTransmitFollowupResponse(short id, int status);

        /**
         * Invoked in response to a create data interface request.
         * @param id ID corresponding to the original request.
         * @param status Status the operation (see {@link NanStatusCode}).
         */
        void notifyCreateDataInterfaceResponse(short id, int status);

        /**
         * Invoked in response to a delete data interface request.
         * @param id ID corresponding to the original request.
         * @param status Status the operation (see {@link NanStatusCode}).
         */
        void notifyDeleteDataInterfaceResponse(short id, int status);

        /**
         * Invoked in response to a delete data interface request.
         * @param id ID corresponding to the original request.
         * @param status Status the operation (see {@link NanStatusCode}).
         * @param ndpInstanceId
         */
        void notifyInitiateDataPathResponse(short id, int status, int ndpInstanceId);

        /**
         * Invoked in response to a respond to data path indication request.
         * @param id ID corresponding to the original request.
         * @param status Status the operation (see {@link NanStatusCode}).
         */
        void notifyRespondToDataPathIndicationResponse(short id, int status);

        /**
         * Invoked in response to a terminate data path request.
         * @param id ID corresponding to the original request.
         * @param status Status the operation (see {@link NanStatusCode}).
         */
        void notifyTerminateDataPathResponse(short id, int status);

        /**
         * Invoked in response to a initiate NAN pairing request.
         * @param id ID corresponding to the original request.
         * @param status Status the operation (see {@link NanStatusCode}).
         */
        void notifyInitiatePairingResponse(short id, int status,
                int pairingInstanceId);

        /**
         * Invoked in response to a response NAN pairing request.
         * @param id ID corresponding to the original request.
         * @param status Status the operation (see {@link NanStatusCode}).
         */
        void notifyRespondToPairingIndicationResponse(short id, int status);

        /**
         * Invoked in response to a initiate NAN Bootstrapping request.
         * @param id ID corresponding to the original request.
         * @param status Status the operation (see {@link NanStatusCode}).
         */
        void notifyInitiateBootstrappingResponse(short id, int status,
                int bootstrappingInstanceId);

        /**
         * Invoked in response to a response NAN Bootstrapping request.
         * @param id ID corresponding to the original request.
         * @param status Status the operation (see {@link NanStatusCode}).
         */
        void notifyRespondToBootstrappingIndicationResponse(short id, int status);

        /**
         * Invoked in response to a connection suspension request.
         * @param id ID corresponding to the original request.
         * @param status Status the operation (see {@link NanStatusCode}).
         */
        void notifySuspendResponse(short id, int status);

        /**
         * Invoked in response to a connection resume request.
         * @param id ID corresponding to the original request.
         * @param status Status the operation (see {@link NanStatusCode}).
         */
        void notifyResumeResponse(short id, int status);

        /**
         * Invoked in response to a pairing termination request.
         * @param id ID corresponding to the original request.
         * @param status Status the operation (see {@link NanStatusCode}).
         */
        void notifyTerminatePairingResponse(short id, int status);

        /**
         * Indicates that a cluster event has been received.
         * @param eventType Type of the cluster event (see {@link NanClusterEventType}).
         * @param addr MAC Address associated with the corresponding event.
         */
        void eventClusterEvent(int eventType, byte[] addr);

        /**
         * Indicates that a NAN has been disabled.
         * @param status Status the operation (see {@link NanStatusCode}).
         */
        void eventDisabled(int status);

        /**
         * Indicates that an active publish session has terminated.
         * @param sessionId Discovery session ID of the terminated session.
         * @param status Status the operation (see {@link NanStatusCode}).
         */
        void eventPublishTerminated(byte sessionId, int status);

        /**
         * Indicates that an active subscribe session has terminated.
         * @param sessionId Discovery session ID of the terminated session.
         * @param status Status the operation (see {@link NanStatusCode}).
         */
        void eventSubscribeTerminated(byte sessionId, int status);

        /**
         * Indicates that a match has occurred - i.e. a service has been discovered.
         * @param discoverySessionId Publish or subscribe discovery session ID of an existing
         *                           discovery session.
         * @param peerId Unique ID of the peer. Can be used subsequently in sendMessage()
         *               or to set up a data-path.
         * @param addr NAN Discovery (management) MAC address of the peer.
         * @param serviceSpecificInfo The arbitrary information contained in the
         *                            |NanDiscoveryCommonConfig.serviceSpecificInfo| of
         *                            the peer's discovery session configuration.
         * @param matchFilter The match filter from the discovery packet (publish or subscribe)
         *                    which caused service discovery. Matches the
         *                    |NanDiscoveryCommonConfig.txMatchFilter| of the peer's unsolicited
         *                    publish message or of the local device's Active subscribe message.
         * @param rangingIndicationType The ranging event(s) which triggered the ranging. Ex. can
         *                              indicate that continuous ranging was requested, or else that
         *                              an ingress event occurred. See {@link NanRangingIndication}.
         * @param rangingMeasurementInMm If ranging was required and executed, contains the
         *                               distance to the peer in mm.
         * @param scid Security Context Identifier identifies the Security Context.
         *             For NAN Shared Key Cipher Suite, this field contains the 16 octet PMKID
         *             identifying the PMK used for setting up the Secure Data Path.
         * @param peerCipherType Cipher type for data-paths constructed in the context of this
         *                       discovery session.
         * @param vendorData Additional vendor-specific parameters, or null if not provided.
         */
        void eventMatch(byte discoverySessionId, int peerId, byte[] addr,
                byte[] serviceSpecificInfo, byte[] matchFilter, int rangingIndicationType,
                int rangingMeasurementInMm, byte[] scid, int peerCipherType, byte[] nonce,
                byte[] tag, AwarePairingConfig pairingConfig,
                @Nullable List<OuiKeyedData> vendorData);

        /**
         * Indicates that a previously discovered match (service) has expired.
         * @param discoverySessionId Discovery session ID of the expired match.
         * @param peerId Peer ID of the expired match.
         */
        void eventMatchExpired(byte discoverySessionId, int peerId);

        /**
         * Indicates that a followup message has been received from a peer.
         * @param discoverySessionId Discovery session (publish or subscribe) ID of a previously
         *                           created discovery session.
         * @param peerId Unique ID of the peer.
         * @param addr NAN Discovery (management) MAC address of the peer.
         * @param serviceSpecificInfo Received message from the peer. There is no semantic
         *                            meaning to these bytes. They are passed-through from sender
         *                            to receiver as-is with no parsing.
         */
        void eventFollowupReceived(byte discoverySessionId, int peerId, byte[] addr,
                byte[] serviceSpecificInfo);

        /**
         * Provides status of a completed followup message transmit operation.
         * @param id ID corresponding to the original request.
         * @param status Status the operation (see {@link NanStatusCode}).
         */
        void eventTransmitFollowup(short id, int status);

        /**
         * Indicates that a data-path (NDP) setup has been requested by an initiator peer
         * (received by the intended responder).
         * @param discoverySessionId ID of an active publish or subscribe discovery session.
         * @param peerDiscMacAddr MAC address of the Initiator peer. This is the MAC address of
         *                        the peer's management/discovery NAN interface.
         * @param ndpInstanceId ID of the data-path. Used to identify the data-path in further
         *                      negotiation/APIs.
         * @param appInfo Arbitrary information communicated from the peer as part of the
         *                data-path setup process. There is no semantic meaning to these bytes.
         *                They are passed from sender to receiver as-is with no parsing.
         */
        void eventDataPathRequest(byte discoverySessionId, byte[] peerDiscMacAddr,
                int ndpInstanceId, byte[] appInfo);

        /**
         * Indicates that a data-path (NDP) setup has been completed. Received by both the
         * Initiator and Responder.
         * @param status Status the operation (see {@link NanStatusCode}).
         * @param ndpInstanceId ID of the data-path.
         * @param dataPathSetupSuccess Indicates whether the data-path setup succeeded (true)
         *                             or failed (false).
         * @param peerNdiMacAddr MAC address of the peer's data-interface (not its
         *                       management/discovery interface).
         * @param appInfo Arbitrary information communicated from the peer as part of the
         *                data-path setup process. There is no semantic meaning to  these bytes.
         *                They are passed from sender to receiver as-is with no parsing.
         * @param channelInfos
         */
        void eventDataPathConfirm(int status, int ndpInstanceId, boolean dataPathSetupSuccess,
                byte[] peerNdiMacAddr, byte[] appInfo, List<WifiAwareChannelInfo> channelInfos);

        /**
         * Indicates that a data-path (NDP) schedule has been updated (ex. channels
         * have been changed).
         * @param peerDiscoveryAddress Discovery address (NMI) of the peer to which the NDP
         *                             is connected.
         * @param ndpInstanceIds List of NDPs to which this update applies.
         * @param channelInfo Updated channel(s) information.
         */
        void eventDataPathScheduleUpdate(byte[] peerDiscoveryAddress,
                ArrayList<Integer> ndpInstanceIds, List<WifiAwareChannelInfo> channelInfo);

        /**
         * Indicates that a list of data-paths (NDP) have been terminated. Received by both the
         * Initiator and Responder.
         * @param ndpInstanceId Data-path ID of the terminated data-path.
         */
        void eventDataPathTerminated(int ndpInstanceId);

        /**
         * Indicates that the pairing request is from the peer device.
         */
        void eventPairingRequest(int discoverySessionId, int peerId, byte[] peerDiscMacAddr,
                int ndpInstanceId, int requestType, boolean enableCache, byte[] nonce, byte[] tag);

        /**
         * Indicates that the pairing is finished
         */
        void eventPairingConfirm(int pairingId, boolean accept, int reason, int requestType,
                boolean enableCache,
                PairingConfigManager.PairingSecurityAssociationInfo npksa);

        /**
         * Indicates that the bootstrapping request is from the peer device.
         */
        void eventBootstrappingRequest(int discoverySessionId, int peerId, byte[] peerDiscMacAddr,
                int bootstrappingInstanceId, int method);

        /**
         * Indicates that the bootstrapping is finished
         */
        void eventBootstrappingConfirm(int pairingId, int responseCode, int reason,
                int comebackDelay, byte[] cookie);

        /**
         * Indicates that the suspension mode has changed, i.e., the device has entered or exited
         * the suspension mode
         */
        void eventSuspensionModeChanged(boolean isSuspended);
    }
}
