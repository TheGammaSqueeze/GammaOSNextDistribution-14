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

import android.hardware.wifi.V1_0.NanClusterEventInd;
import android.hardware.wifi.V1_0.NanDataPathConfirmInd;
import android.hardware.wifi.V1_0.NanDataPathRequestInd;
import android.hardware.wifi.V1_0.NanFollowupReceivedInd;
import android.hardware.wifi.V1_0.NanMatchInd;
import android.hardware.wifi.V1_0.NanStatusType;
import android.hardware.wifi.V1_0.WifiNanStatus;
import android.hardware.wifi.V1_2.NanDataPathScheduleUpdateInd;
import android.hardware.wifi.V1_6.IWifiNanIfaceEventCallback;
import android.hardware.wifi.V1_6.NanCipherSuiteType;
import android.hardware.wifi.V1_6.WifiChannelWidthInMhz;
import android.net.MacAddress;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiAnnotations;
import android.net.wifi.aware.Characteristics;
import android.net.wifi.aware.WifiAwareChannelInfo;
import android.net.wifi.util.HexEncoding;
import android.util.Log;

import com.android.server.wifi.aware.Capabilities;
import com.android.server.wifi.hal.WifiNanIface.NanClusterEventType;
import com.android.server.wifi.hal.WifiNanIface.NanRangingIndication;
import com.android.server.wifi.hal.WifiNanIface.NanStatusCode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Callback registered with the Vendor HAL service. On events, converts arguments
 * to their framework equivalents and calls the registered framework callback.
 */
public class WifiNanIfaceCallbackHidlImpl extends IWifiNanIfaceEventCallback.Stub {
    private static final String TAG = "WifiNanIfaceCallbackHidlImpl";

    private boolean mVerboseLoggingEnabled;
    private WifiNanIfaceHidlImpl mWifiNanIface;

    public WifiNanIfaceCallbackHidlImpl(WifiNanIfaceHidlImpl wifiNanIface) {
        mWifiNanIface = wifiNanIface;
    }

    /**
     * Enable verbose logging.
     */
    public void enableVerboseLogging(boolean verbose) {
        mVerboseLoggingEnabled = verbose;
    }

    @Override
    public void notifyCapabilitiesResponse(short id, WifiNanStatus status,
            android.hardware.wifi.V1_0.NanCapabilities capabilities) {
        if (!checkFrameworkCallback()) return;
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "notifyCapabilitiesResponse: id=" + id + ", status=" + statusString(status)
                    + ", capabilities=" + capabilities);
        }

        if (status.status == NanStatusType.SUCCESS) {
            Capabilities frameworkCapabilities = toFrameworkCapability10(capabilities);
            mWifiNanIface.getFrameworkCallback().notifyCapabilitiesResponse(
                    id, frameworkCapabilities);
        } else {
            Log.e(TAG, "notifyCapabilitiesResponse: error code=" + status.status + " ("
                    + status.description + ")");
        }
    }

    @Override
    public void notifyCapabilitiesResponse_1_5(short id, WifiNanStatus status,
            android.hardware.wifi.V1_5.NanCapabilities capabilities) {
        if (!checkFrameworkCallback()) return;
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "notifyCapabilitiesResponse_1_5: id=" + id + ", status="
                    + statusString(status) + ", capabilities=" + capabilities);
        }

        if (status.status == NanStatusType.SUCCESS) {
            Capabilities frameworkCapabilities = toFrameworkCapability10(capabilities.V1_0);
            frameworkCapabilities.isInstantCommunicationModeSupported =
                    capabilities.instantCommunicationModeSupportFlag;
            mWifiNanIface.getFrameworkCallback().notifyCapabilitiesResponse(
                    id, frameworkCapabilities);
        } else {
            Log.e(TAG, "notifyCapabilitiesResponse_1_5: error code=" + status.status + " ("
                    + status.description + ")");
        }
    }

    @Override
    public void notifyCapabilitiesResponse_1_6(short id, WifiNanStatus status,
            android.hardware.wifi.V1_6.NanCapabilities capabilities) {
        if (!checkFrameworkCallback()) return;
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "notifyCapabilitiesResponse_1_6: id=" + id + ", status="
                    + statusString(status) + ", capabilities=" + capabilities);
        }

        if (status.status == NanStatusType.SUCCESS) {
            Capabilities frameworkCapabilities = toFrameworkCapability1_6(capabilities);
            mWifiNanIface.getFrameworkCallback().notifyCapabilitiesResponse(
                    id, frameworkCapabilities);
        } else {
            Log.e(TAG, "notifyCapabilitiesResponse_1_6: error code=" + status.status + " ("
                    + status.description + ")");
        }
    }

    @Override
    public void notifyEnableResponse(short id, WifiNanStatus status) {
        if (!checkFrameworkCallback()) return;
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "notifyEnableResponse: id=" + id + ", status=" + statusString(status));
        }

        if (status.status == NanStatusType.ALREADY_ENABLED) {
            Log.wtf(TAG, "notifyEnableResponse: id=" + id + ", already enabled!?");
        }
        mWifiNanIface.getFrameworkCallback().notifyEnableResponse(
                id, NanStatusCode.fromHidl(status.status));
    }

    @Override
    public void notifyConfigResponse(short id, WifiNanStatus status) {
        if (!checkFrameworkCallback()) return;
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "notifyConfigResponse: id=" + id + ", status=" + statusString(status));
        }
        mWifiNanIface.getFrameworkCallback().notifyConfigResponse(
                id, NanStatusCode.fromHidl(status.status));
    }

    @Override
    public void notifyDisableResponse(short id, WifiNanStatus status) {
        if (!checkFrameworkCallback()) return;
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "notifyDisableResponse: id=" + id + ", status=" + statusString(status));
        }

        if (status.status != NanStatusType.SUCCESS) {
            Log.e(TAG, "notifyDisableResponse: failure - code=" + status.status + " ("
                    + status.description + ")");
        }
        mWifiNanIface.getFrameworkCallback().notifyDisableResponse(
                id, NanStatusCode.fromHidl(status.status));
    }

    @Override
    public void notifyStartPublishResponse(short id, WifiNanStatus status, byte publishId) {
        if (!checkFrameworkCallback()) return;
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "notifyStartPublishResponse: id=" + id + ", status=" + statusString(status)
                    + ", publishId=" + publishId);
        }
        mWifiNanIface.getFrameworkCallback().notifyStartPublishResponse(
                id, NanStatusCode.fromHidl(status.status), publishId);
    }

    @Override
    public void notifyStopPublishResponse(short id, WifiNanStatus status) {
        if (!checkFrameworkCallback()) return;
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "notifyStopPublishResponse: id=" + id + ", status=" + statusString(status));
        }

        if (status.status == NanStatusType.SUCCESS) {
            // NOP
        } else {
            Log.e(TAG, "notifyStopPublishResponse: failure - code=" + status.status + " ("
                    + status.description + ")");
        }
    }

    @Override
    public void notifyStartSubscribeResponse(short id, WifiNanStatus status, byte subscribeId) {
        if (!checkFrameworkCallback()) return;
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "notifyStartSubscribeResponse: id=" + id + ", status=" + statusString(status)
                    + ", subscribeId=" + subscribeId);
        }
        mWifiNanIface.getFrameworkCallback().notifyStartSubscribeResponse(
                id, NanStatusCode.fromHidl(status.status), subscribeId);
    }

    @Override
    public void notifyStopSubscribeResponse(short id, WifiNanStatus status) {
        if (!checkFrameworkCallback()) return;
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "notifyStopSubscribeResponse: id=" + id + ", status="
                    + statusString(status));
        }

        if (status.status == NanStatusType.SUCCESS) {
            // NOP
        } else {
            Log.e(TAG, "notifyStopSubscribeResponse: failure - code=" + status.status + " ("
                    + status.description + ")");
        }
    }

    @Override
    public void notifyTransmitFollowupResponse(short id, WifiNanStatus status) {
        if (!checkFrameworkCallback()) return;
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "notifyTransmitFollowupResponse: id=" + id + ", status="
                    + statusString(status));
        }
        mWifiNanIface.getFrameworkCallback().notifyTransmitFollowupResponse(
                id, NanStatusCode.fromHidl(status.status));
    }

    @Override
    public void notifyCreateDataInterfaceResponse(short id, WifiNanStatus status) {
        if (!checkFrameworkCallback()) return;
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "notifyCreateDataInterfaceResponse: id=" + id + ", status="
                    + statusString(status));
        }
        mWifiNanIface.getFrameworkCallback().notifyCreateDataInterfaceResponse(
                id, NanStatusCode.fromHidl(status.status));
    }

    @Override
    public void notifyDeleteDataInterfaceResponse(short id, WifiNanStatus status) {
        if (!checkFrameworkCallback()) return;
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "notifyDeleteDataInterfaceResponse: id=" + id + ", status="
                    + statusString(status));
        }
        mWifiNanIface.getFrameworkCallback().notifyDeleteDataInterfaceResponse(
                id, NanStatusCode.fromHidl(status.status));
    }

    @Override
    public void notifyInitiateDataPathResponse(short id, WifiNanStatus status,
            int ndpInstanceId) {
        if (!checkFrameworkCallback()) return;
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "notifyInitiateDataPathResponse: id=" + id + ", status="
                    + statusString(status) + ", ndpInstanceId=" + ndpInstanceId);
        }
        mWifiNanIface.getFrameworkCallback().notifyInitiateDataPathResponse(
                id, NanStatusCode.fromHidl(status.status), ndpInstanceId);
    }

    @Override
    public void notifyRespondToDataPathIndicationResponse(short id, WifiNanStatus status) {
        if (!checkFrameworkCallback()) return;
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "notifyRespondToDataPathIndicationResponse: id=" + id
                    + ", status=" + statusString(status));
        }
        mWifiNanIface.getFrameworkCallback().notifyRespondToDataPathIndicationResponse(
                id, NanStatusCode.fromHidl(status.status));
    }

    @Override
    public void notifyTerminateDataPathResponse(short id, WifiNanStatus status) {
        if (!checkFrameworkCallback()) return;
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "notifyTerminateDataPathResponse: id=" + id + ", status="
                    + statusString(status));
        }
        mWifiNanIface.getFrameworkCallback().notifyTerminateDataPathResponse(
                id, NanStatusCode.fromHidl(status.status));
    }

    @Override
    public void eventClusterEvent(NanClusterEventInd event) {
        if (!checkFrameworkCallback()) return;
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "eventClusterEvent: eventType=" + event.eventType + ", addr="
                    + String.valueOf(HexEncoding.encode(event.addr)));
        }
        mWifiNanIface.getFrameworkCallback().eventClusterEvent(
                NanClusterEventType.fromHidl(event.eventType), event.addr);
    }

    @Override
    public void eventDisabled(WifiNanStatus status) {
        if (!checkFrameworkCallback()) return;
        if (mVerboseLoggingEnabled) Log.v(TAG, "eventDisabled: status=" + statusString(status));
        mWifiNanIface.getFrameworkCallback().eventDisabled(
                NanStatusCode.fromHidl(status.status));
    }

    @Override
    public void eventPublishTerminated(byte sessionId, WifiNanStatus status) {
        if (!checkFrameworkCallback()) return;
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "eventPublishTerminated: sessionId=" + sessionId + ", status="
                    + statusString(status));
        }
        mWifiNanIface.getFrameworkCallback().eventPublishTerminated(
                sessionId, NanStatusCode.fromHidl(status.status));
    }

    @Override
    public void eventSubscribeTerminated(byte sessionId, WifiNanStatus status) {
        if (!checkFrameworkCallback()) return;
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "eventSubscribeTerminated: sessionId=" + sessionId + ", status="
                    + statusString(status));
        }
        mWifiNanIface.getFrameworkCallback().eventSubscribeTerminated(
                sessionId, NanStatusCode.fromHidl(status.status));
    }

    @Override
    public void eventMatch(NanMatchInd event) {
        if (!checkFrameworkCallback()) return;
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "eventMatch: discoverySessionId=" + event.discoverySessionId + ", peerId="
                    + event.peerId + ", addr=" + String.valueOf(HexEncoding.encode(event.addr))
                    + ", serviceSpecificInfo=" + Arrays.toString(
                    convertArrayListToNativeByteArray(event.serviceSpecificInfo)) + ", ssi.size()="
                    + (event.serviceSpecificInfo == null ? 0 : event.serviceSpecificInfo.size())
                    + ", matchFilter=" + Arrays.toString(
                    convertArrayListToNativeByteArray(event.matchFilter)) + ", mf.size()=" + (
                    event.matchFilter == null ? 0 : event.matchFilter.size())
                    + ", rangingIndicationType=" + event.rangingIndicationType
                    + ", rangingMeasurementInCm=" + event.rangingMeasurementInCm);
        }
        mWifiNanIface.getFrameworkCallback().eventMatch(event.discoverySessionId, event.peerId,
                event.addr, convertArrayListToNativeByteArray(event.serviceSpecificInfo),
                convertArrayListToNativeByteArray(event.matchFilter),
                NanRangingIndication.fromHidl(event.rangingIndicationType),
                event.rangingMeasurementInCm * 10, new byte[0], 0, null, null, null, null);
    }

    @Override
    public void eventMatch_1_6(android.hardware.wifi.V1_6.NanMatchInd event) {
        if (!checkFrameworkCallback()) return;
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "eventMatch_1_6: discoverySessionId=" + event.discoverySessionId
                    + ", peerId=" + event.peerId
                    + ", addr=" + String.valueOf(HexEncoding.encode(event.addr))
                    + ", serviceSpecificInfo=" + Arrays.toString(
                    convertArrayListToNativeByteArray(event.serviceSpecificInfo)) + ", ssi.size()="
                    + (event.serviceSpecificInfo == null ? 0 : event.serviceSpecificInfo.size())
                    + ", matchFilter=" + Arrays.toString(
                    convertArrayListToNativeByteArray(event.matchFilter)) + ", mf.size()=" + (
                    event.matchFilter == null ? 0 : event.matchFilter.size())
                    + ", rangingIndicationType=" + event.rangingIndicationType
                    + ", rangingMeasurementInCm=" + event.rangingMeasurementInMm + ", "
                    + "scid=" + Arrays.toString(convertArrayListToNativeByteArray(event.scid)));
        }
        mWifiNanIface.getFrameworkCallback().eventMatch(event.discoverySessionId, event.peerId,
                event.addr, convertArrayListToNativeByteArray(event.serviceSpecificInfo),
                convertArrayListToNativeByteArray(event.matchFilter),
                NanRangingIndication.fromHidl(event.rangingIndicationType),
                event.rangingMeasurementInMm, convertArrayListToNativeByteArray(event.scid),
                toPublicCipherSuites(event.peerCipherType), null, null, null, null);
    }

    @Override
    public void eventMatchExpired(byte discoverySessionId, int peerId) {
        if (!checkFrameworkCallback()) return;
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "eventMatchExpired: discoverySessionId=" + discoverySessionId
                    + ", peerId=" + peerId);
        }
        mWifiNanIface.getFrameworkCallback().eventMatchExpired(discoverySessionId, peerId);
    }

    @Override
    public void eventFollowupReceived(NanFollowupReceivedInd event) {
        if (!checkFrameworkCallback()) return;
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "eventFollowupReceived: discoverySessionId=" + event.discoverySessionId
                    + ", peerId=" + event.peerId + ", addr=" + String.valueOf(
                    HexEncoding.encode(event.addr)) + ", serviceSpecificInfo=" + Arrays.toString(
                    convertArrayListToNativeByteArray(event.serviceSpecificInfo)) + ", ssi.size()="
                    + (event.serviceSpecificInfo == null ? 0 : event.serviceSpecificInfo.size()));
        }
        mWifiNanIface.getFrameworkCallback().eventFollowupReceived(
                event.discoverySessionId, event.peerId, event.addr,
                convertArrayListToNativeByteArray(event.serviceSpecificInfo));
    }

    @Override
    public void eventTransmitFollowup(short id, WifiNanStatus status) {
        if (!checkFrameworkCallback()) return;
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "eventTransmitFollowup: id=" + id + ", status=" + statusString(status));
        }
        mWifiNanIface.getFrameworkCallback().eventTransmitFollowup(
                id, NanStatusCode.fromHidl(status.status));
    }

    @Override
    public void eventDataPathRequest(NanDataPathRequestInd event) {
        if (!checkFrameworkCallback()) return;
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "eventDataPathRequest: discoverySessionId=" + event.discoverySessionId
                    + ", peerDiscMacAddr=" + String.valueOf(
                    HexEncoding.encode(event.peerDiscMacAddr)) + ", ndpInstanceId="
                    + event.ndpInstanceId + ", appInfo.size()=" + event.appInfo.size());
        }
        mWifiNanIface.getFrameworkCallback().eventDataPathRequest(event.discoverySessionId,
                event.peerDiscMacAddr, event.ndpInstanceId,
                convertArrayListToNativeByteArray(event.appInfo));
    }

    @Override
    public void eventDataPathConfirm(NanDataPathConfirmInd event) {
        if (!checkFrameworkCallback()) return;
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "onDataPathConfirm: ndpInstanceId=" + event.ndpInstanceId
                    + ", peerNdiMacAddr=" + String.valueOf(HexEncoding.encode(event.peerNdiMacAddr))
                    + ", dataPathSetupSuccess=" + event.dataPathSetupSuccess + ", reason="
                    + event.status.status + ", appInfo.size()=" + event.appInfo.size());
        }
        mWifiNanIface.getFrameworkCallback().eventDataPathConfirm(
                NanStatusCode.fromHidl(event.status.status), event.ndpInstanceId,
                event.dataPathSetupSuccess, event.peerNdiMacAddr,
                convertArrayListToNativeByteArray(event.appInfo), null);
    }

    @Override
    public void eventDataPathConfirm_1_2(android.hardware.wifi.V1_2.NanDataPathConfirmInd event) {
        if (!checkFrameworkCallback()) return;
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "eventDataPathConfirm_1_2: ndpInstanceId=" + event.V1_0.ndpInstanceId
                    + ", peerNdiMacAddr=" + String.valueOf(
                    HexEncoding.encode(event.V1_0.peerNdiMacAddr)) + ", dataPathSetupSuccess="
                    + event.V1_0.dataPathSetupSuccess + ", reason=" + event.V1_0.status.status
                    + ", appInfo.size()=" + event.V1_0.appInfo.size()
                    + ", channelInfo" + event.channelInfo);
        }
        List<WifiAwareChannelInfo> wifiAwareChannelInfos =
                convertHalChannelInfo_1_2(event.channelInfo);
        mWifiNanIface.getFrameworkCallback().eventDataPathConfirm(
                NanStatusCode.fromHidl(event.V1_0.status.status), event.V1_0.ndpInstanceId,
                event.V1_0.dataPathSetupSuccess, event.V1_0.peerNdiMacAddr,
                convertArrayListToNativeByteArray(event.V1_0.appInfo), wifiAwareChannelInfos);
    }

    @Override
    public void eventDataPathConfirm_1_6(android.hardware.wifi.V1_6.NanDataPathConfirmInd event) {
        if (!checkFrameworkCallback()) return;
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "eventDataPathConfirm_1_6: ndpInstanceId=" + event.V1_0.ndpInstanceId
                    + ", peerNdiMacAddr=" + String.valueOf(
                    HexEncoding.encode(event.V1_0.peerNdiMacAddr)) + ", dataPathSetupSuccess="
                    + event.V1_0.dataPathSetupSuccess + ", reason=" + event.V1_0.status.status
                    + ", appInfo.size()=" + event.V1_0.appInfo.size()
                    + ", channelInfo" + event.channelInfo);
        }
        List<WifiAwareChannelInfo> wifiAwareChannelInfos =
                convertHalChannelInfo_1_6(event.channelInfo);
        mWifiNanIface.getFrameworkCallback().eventDataPathConfirm(
                NanStatusCode.fromHidl(event.V1_0.status.status), event.V1_0.ndpInstanceId,
                event.V1_0.dataPathSetupSuccess, event.V1_0.peerNdiMacAddr,
                convertArrayListToNativeByteArray(event.V1_0.appInfo), wifiAwareChannelInfos);
    }

    @Override
    public void eventDataPathScheduleUpdate(NanDataPathScheduleUpdateInd event) {
        if (!checkFrameworkCallback()) return;
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "eventDataPathScheduleUpdate: peerMac="
                    + MacAddress.fromBytes(event.peerDiscoveryAddress)
                    + ", ndpIds=" + event.ndpInstanceIds + ", channelInfo=" + event.channelInfo);
        }
        List<WifiAwareChannelInfo> wifiAwareChannelInfos =
                convertHalChannelInfo_1_2(event.channelInfo);
        mWifiNanIface.getFrameworkCallback().eventDataPathScheduleUpdate(
                event.peerDiscoveryAddress, event.ndpInstanceIds, wifiAwareChannelInfos);
    }

    @Override
    public void eventDataPathScheduleUpdate_1_6(
            android.hardware.wifi.V1_6.NanDataPathScheduleUpdateInd event) {
        if (!checkFrameworkCallback()) return;
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "eventDataPathScheduleUpdate_1_6: peerMac="
                    + MacAddress.fromBytes(event.peerDiscoveryAddress)
                    + ", ndpIds=" + event.ndpInstanceIds + ", channelInfo=" + event.channelInfo);
        }
        List<WifiAwareChannelInfo> wifiAwareChannelInfos =
                convertHalChannelInfo_1_6(event.channelInfo);
        mWifiNanIface.getFrameworkCallback().eventDataPathScheduleUpdate(
                event.peerDiscoveryAddress, event.ndpInstanceIds, wifiAwareChannelInfos);
    }

    @Override
    public void eventDataPathTerminated(int ndpInstanceId) {
        if (!checkFrameworkCallback()) return;
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "eventDataPathTerminated: ndpInstanceId=" + ndpInstanceId);
        }
        mWifiNanIface.getFrameworkCallback().eventDataPathTerminated(ndpInstanceId);
    }

    private Capabilities toFrameworkCapability10(
            android.hardware.wifi.V1_0.NanCapabilities capabilities) {
        Capabilities frameworkCapabilities = new Capabilities();
        frameworkCapabilities.maxConcurrentAwareClusters = capabilities.maxConcurrentClusters;
        frameworkCapabilities.maxPublishes = capabilities.maxPublishes;
        frameworkCapabilities.maxSubscribes = capabilities.maxSubscribes;
        frameworkCapabilities.maxServiceNameLen = capabilities.maxServiceNameLen;
        frameworkCapabilities.maxMatchFilterLen = capabilities.maxMatchFilterLen;
        frameworkCapabilities.maxTotalMatchFilterLen = capabilities.maxTotalMatchFilterLen;
        frameworkCapabilities.maxServiceSpecificInfoLen =
                capabilities.maxServiceSpecificInfoLen;
        frameworkCapabilities.maxExtendedServiceSpecificInfoLen =
                capabilities.maxExtendedServiceSpecificInfoLen;
        frameworkCapabilities.maxNdiInterfaces = capabilities.maxNdiInterfaces;
        frameworkCapabilities.maxNdpSessions = capabilities.maxNdpSessions;
        frameworkCapabilities.maxAppInfoLen = capabilities.maxAppInfoLen;
        frameworkCapabilities.maxQueuedTransmitMessages =
                capabilities.maxQueuedTransmitFollowupMsgs;
        frameworkCapabilities.maxSubscribeInterfaceAddresses =
                capabilities.maxSubscribeInterfaceAddresses;
        frameworkCapabilities.supportedDataPathCipherSuites = toPublicCipherSuites(
                capabilities.supportedCipherSuites);
        frameworkCapabilities.isInstantCommunicationModeSupported = false;
        return frameworkCapabilities;
    }

    private Capabilities toFrameworkCapability1_6(
            android.hardware.wifi.V1_6.NanCapabilities capabilities) {
        Capabilities frameworkCapabilities = new Capabilities();
        frameworkCapabilities.maxConcurrentAwareClusters = capabilities.maxConcurrentClusters;
        frameworkCapabilities.maxPublishes = capabilities.maxPublishes;
        frameworkCapabilities.maxSubscribes = capabilities.maxSubscribes;
        frameworkCapabilities.maxServiceNameLen = capabilities.maxServiceNameLen;
        frameworkCapabilities.maxMatchFilterLen = capabilities.maxMatchFilterLen;
        frameworkCapabilities.maxTotalMatchFilterLen = capabilities.maxTotalMatchFilterLen;
        frameworkCapabilities.maxServiceSpecificInfoLen =
                capabilities.maxServiceSpecificInfoLen;
        frameworkCapabilities.maxExtendedServiceSpecificInfoLen =
                capabilities.maxExtendedServiceSpecificInfoLen;
        frameworkCapabilities.maxNdiInterfaces = capabilities.maxNdiInterfaces;
        frameworkCapabilities.maxNdpSessions = capabilities.maxNdpSessions;
        frameworkCapabilities.maxAppInfoLen = capabilities.maxAppInfoLen;
        frameworkCapabilities.maxQueuedTransmitMessages =
                capabilities.maxQueuedTransmitFollowupMsgs;
        frameworkCapabilities.maxSubscribeInterfaceAddresses =
                capabilities.maxSubscribeInterfaceAddresses;
        frameworkCapabilities.supportedDataPathCipherSuites = toPublicCipherSuites(
                capabilities.supportedCipherSuites);
        frameworkCapabilities.isInstantCommunicationModeSupported =
                capabilities.instantCommunicationModeSupportFlag;
        return frameworkCapabilities;
    }

    private int toPublicCipherSuites(int nativeCipherSuites) {
        int publicCipherSuites = 0;

        if ((nativeCipherSuites & NanCipherSuiteType.SHARED_KEY_128_MASK) != 0) {
            publicCipherSuites |= Characteristics.WIFI_AWARE_CIPHER_SUITE_NCS_SK_128;
        }
        if ((nativeCipherSuites & NanCipherSuiteType.SHARED_KEY_256_MASK) != 0) {
            publicCipherSuites |= Characteristics.WIFI_AWARE_CIPHER_SUITE_NCS_SK_256;
        }
        if ((nativeCipherSuites & NanCipherSuiteType.PUBLIC_KEY_128_MASK) != 0) {
            publicCipherSuites |= Characteristics.WIFI_AWARE_CIPHER_SUITE_NCS_PK_128;
        }
        if ((nativeCipherSuites & NanCipherSuiteType.PUBLIC_KEY_256_MASK) != 0) {
            publicCipherSuites |= Characteristics.WIFI_AWARE_CIPHER_SUITE_NCS_PK_256;
        }

        return publicCipherSuites;
    }

    /**
     * Converts an ArrayList<Byte> to a byte[].
     *
     * @param from The input ArrayList<Byte></Byte> to convert from.
     *
     * @return A newly allocated byte[].
     */
    private byte[] convertArrayListToNativeByteArray(ArrayList<Byte> from) {
        if (from == null) {
            return null;
        }

        byte[] to = new byte[from.size()];
        for (int i = 0; i < from.size(); ++i) {
            to[i] = from.get(i);
        }
        return to;
    }

    private static String statusString(WifiNanStatus status) {
        if (status == null) {
            return "status=null";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(status.status).append(" (").append(status.description).append(")");
        return sb.toString();
    }

    /**
     * Convert HAL channelBandwidth to framework enum
     */
    private @WifiAnnotations.ChannelWidth int getChannelBandwidthFromHal(int channelBandwidth) {
        switch(channelBandwidth) {
            case WifiChannelWidthInMhz.WIDTH_40:
                return ScanResult.CHANNEL_WIDTH_40MHZ;
            case WifiChannelWidthInMhz.WIDTH_80:
                return ScanResult.CHANNEL_WIDTH_80MHZ;
            case WifiChannelWidthInMhz.WIDTH_160:
                return ScanResult.CHANNEL_WIDTH_160MHZ;
            case WifiChannelWidthInMhz.WIDTH_80P80:
                return ScanResult.CHANNEL_WIDTH_80MHZ_PLUS_MHZ;
            case WifiChannelWidthInMhz.WIDTH_320:
                return ScanResult.CHANNEL_WIDTH_320MHZ;
            default:
                return ScanResult.CHANNEL_WIDTH_20MHZ;
        }
    }

    /**
     * Convert HAL V1_2 NanDataPathChannelInfo to WifiAwareChannelInfo
     */
    private List<WifiAwareChannelInfo> convertHalChannelInfo_1_2(
            List<android.hardware.wifi.V1_2.NanDataPathChannelInfo> channelInfos) {
        List<WifiAwareChannelInfo> wifiAwareChannelInfos = new ArrayList<>();
        if (channelInfos == null) {
            return null;
        }
        for (android.hardware.wifi.V1_2.NanDataPathChannelInfo channelInfo : channelInfos) {
            wifiAwareChannelInfos.add(new WifiAwareChannelInfo(channelInfo.channelFreq,
                    getChannelBandwidthFromHal(channelInfo.channelBandwidth),
                    channelInfo.numSpatialStreams));
        }
        return wifiAwareChannelInfos;
    }

    /**
     * Convert HAL V1_6 NanDataPathChannelInfo to WifiAwareChannelInfo
     */
    private List<WifiAwareChannelInfo> convertHalChannelInfo_1_6(
            List<android.hardware.wifi.V1_6.NanDataPathChannelInfo> channelInfos) {
        List<WifiAwareChannelInfo> wifiAwareChannelInfos = new ArrayList<>();
        if (channelInfos == null) {
            return null;
        }
        for (android.hardware.wifi.V1_6.NanDataPathChannelInfo channelInfo : channelInfos) {
            wifiAwareChannelInfos.add(new WifiAwareChannelInfo(channelInfo.channelFreq,
                    getChannelBandwidthFromHal(channelInfo.channelBandwidth),
                    channelInfo.numSpatialStreams));
        }
        return wifiAwareChannelInfos;
    }

    private boolean checkFrameworkCallback() {
        if (mWifiNanIface == null) {
            Log.e(TAG, "mWifiNanIface is null");
            return false;
        } else if (mWifiNanIface.getFrameworkCallback() == null) {
            Log.e(TAG, "Framework callback is null");
            return false;
        }
        return true;
    }
}
