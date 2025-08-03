/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.server.wifi.aware;

import android.annotation.Nullable;
import android.net.wifi.OuiKeyedData;
import android.net.wifi.aware.AwarePairingConfig;
import android.net.wifi.aware.IdentityChangedListener;
import android.net.wifi.aware.WifiAwareChannelInfo;
import android.util.Log;
import android.util.SparseArray;
import android.util.SparseIntArray;

import com.android.modules.utils.BasicShellCommandHandler;
import com.android.server.wifi.hal.WifiNanIface;
import com.android.server.wifi.hal.WifiNanIface.NanClusterEventType;
import com.android.server.wifi.hal.WifiNanIface.NanStatusCode;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages the callbacks from Wi-Fi Aware HAL.
 */
public class WifiAwareNativeCallback implements WifiNanIface.Callback,
        WifiAwareShellCommand.DelegatedShellCommand {
    private static final String TAG = "WifiAwareNativeCallback";
    private boolean mVerboseLoggingEnabled = false;

    private final WifiAwareStateManager mWifiAwareStateManager;

    public WifiAwareNativeCallback(WifiAwareStateManager wifiAwareStateManager) {
        mWifiAwareStateManager = wifiAwareStateManager;
    }

    /**
     * Enable/Disable verbose logging.
     *
     */
    public void enableVerboseLogging(boolean verboseEnabled) {
        mVerboseLoggingEnabled = verboseEnabled;
    }

    /*
     * Counts of callbacks from HAL. Retrievable through shell command.
     */
    private static final int CB_EV_CLUSTER = 0;
    private static final int CB_EV_DISABLED = 1;
    private static final int CB_EV_PUBLISH_TERMINATED = 2;
    private static final int CB_EV_SUBSCRIBE_TERMINATED = 3;
    private static final int CB_EV_MATCH = 4;
    private static final int CB_EV_MATCH_EXPIRED = 5;
    private static final int CB_EV_FOLLOWUP_RECEIVED = 6;
    private static final int CB_EV_TRANSMIT_FOLLOWUP = 7;
    private static final int CB_EV_DATA_PATH_REQUEST = 8;
    private static final int CB_EV_DATA_PATH_CONFIRM = 9;
    private static final int CB_EV_DATA_PATH_TERMINATED = 10;
    private static final int CB_EV_DATA_PATH_SCHED_UPDATE = 11;

    private final SparseIntArray mCallbackCounter = new SparseIntArray();
    private final SparseArray<List<WifiAwareChannelInfo>> mChannelInfoPerNdp = new SparseArray<>();

    private void incrementCbCount(int callbackId) {
        mCallbackCounter.put(callbackId, mCallbackCounter.get(callbackId) + 1);
    }

    /**
     * Interpreter of adb shell command 'adb shell cmd wifiaware native_cb ...'.
     *
     * @return -1 if parameter not recognized or invalid value, 0 otherwise.
     */
    @Override
    public int onCommand(BasicShellCommandHandler parentShell) {
        final PrintWriter pwe = parentShell.getErrPrintWriter();
        final PrintWriter pwo = parentShell.getOutPrintWriter();

        String subCmd = parentShell.getNextArgRequired();
        switch (subCmd) {
            case "get_cb_count": {
                String option = parentShell.getNextOption();
                boolean reset = false;
                if (option != null) {
                    if ("--reset".equals(option)) {
                        reset = true;
                    } else {
                        pwe.println("Unknown option to 'get_cb_count'");
                        return -1;
                    }
                }

                JSONObject j = new JSONObject();
                try {
                    for (int i = 0; i < mCallbackCounter.size(); ++i) {
                        j.put(Integer.toString(mCallbackCounter.keyAt(i)),
                                mCallbackCounter.valueAt(i));
                    }
                } catch (JSONException e) {
                    Log.e(TAG, "onCommand: get_cb_count e=" + e);
                }
                pwo.println(j.toString());
                if (reset) {
                    mCallbackCounter.clear();
                }
                return 0;
            }
            case  "get_channel_info": {
                String option = parentShell.getNextOption();
                if (option != null) {
                    pwe.println("Unknown option to 'get_channel_info'");
                    return -1;
                }
                String channelInfoString = convertChannelInfoToJsonString();
                pwo.println(channelInfoString);
                return 0;
            }
            default:
                pwe.println("Unknown 'wifiaware native_cb <cmd>'");
        }

        return -1;
    }

    @Override
    public void onReset() {
        // NOP (onReset is intended for configuration reset - not data reset)
    }

    @Override
    public void onHelp(String command, BasicShellCommandHandler parentShell) {
        final PrintWriter pw = parentShell.getOutPrintWriter();

        pw.println("  " + command);
        pw.println("    get_cb_count [--reset]: gets the number of callbacks (and optionally reset "
                + "count)");
        pw.println("    get_channel_info: prints out existing NDP channel info as a JSON String");
    }

    @Override
    public void notifyCapabilitiesResponse(short id, Capabilities capabilities) {
        mWifiAwareStateManager.onCapabilitiesUpdateResponse(id, capabilities);
    }

    @Override
    public void notifyEnableResponse(short id, int status) {
        if (status == NanStatusCode.SUCCESS
                || status == NanStatusCode.ALREADY_ENABLED) {
            mWifiAwareStateManager.onConfigSuccessResponse(id);
        } else {
            mWifiAwareStateManager.onConfigFailedResponse(id, status);
        }
    }

    @Override
    public void notifyConfigResponse(short id, int status) {
        if (status == NanStatusCode.SUCCESS) {
            mWifiAwareStateManager.onConfigSuccessResponse(id);
        } else {
            mWifiAwareStateManager.onConfigFailedResponse(id, status);
        }
    }

    @Override
    public void notifyDisableResponse(short id, int status) {
        mWifiAwareStateManager.onDisableResponse(id, status);
    }

    @Override
    public void notifyStartPublishResponse(short id, int status, byte publishId) {
        if (status == NanStatusCode.SUCCESS) {
            mWifiAwareStateManager.onSessionConfigSuccessResponse(id, true, publishId);
        } else {
            mWifiAwareStateManager.onSessionConfigFailResponse(id, true, status);
        }
    }

    @Override
    public void notifyStartSubscribeResponse(short id, int status, byte subscribeId) {
        if (status == NanStatusCode.SUCCESS) {
            mWifiAwareStateManager.onSessionConfigSuccessResponse(id, false, subscribeId);
        } else {
            mWifiAwareStateManager.onSessionConfigFailResponse(id, false, status);
        }
    }

    @Override
    public void notifyTransmitFollowupResponse(short id, int status) {
        if (status == NanStatusCode.SUCCESS) {
            mWifiAwareStateManager.onMessageSendQueuedSuccessResponse(id);
        } else {
            mWifiAwareStateManager.onMessageSendQueuedFailResponse(id, status);
        }
    }

    @Override
    public void notifyCreateDataInterfaceResponse(short id, int status) {
        mWifiAwareStateManager.onCreateDataPathInterfaceResponse(id,
                status == NanStatusCode.SUCCESS, status);
    }

    @Override
    public void notifyDeleteDataInterfaceResponse(short id, int status) {
        mWifiAwareStateManager.onDeleteDataPathInterfaceResponse(id,
                status == NanStatusCode.SUCCESS, status);
    }

    @Override
    public void notifyInitiateDataPathResponse(short id, int status,
            int ndpInstanceId) {
        if (status == NanStatusCode.SUCCESS) {
            mWifiAwareStateManager.onInitiateDataPathResponseSuccess(id, ndpInstanceId);
        } else {
            mWifiAwareStateManager.onInitiateDataPathResponseFail(id, status);
        }
    }

    @Override
    public void notifyRespondToDataPathIndicationResponse(short id, int status) {
        mWifiAwareStateManager.onRespondToDataPathSetupRequestResponse(id,
                status == NanStatusCode.SUCCESS, status);
    }

    @Override
    public void notifyTerminateDataPathResponse(short id, int status) {
        mWifiAwareStateManager.onEndDataPathResponse(id, status == NanStatusCode.SUCCESS,
                status);
    }

    @Override
    public void notifyInitiatePairingResponse(short id, int status,
            int pairingInstanceId) {
        if (status == NanStatusCode.SUCCESS) {
            mWifiAwareStateManager.onInitiatePairingResponseSuccess(id, pairingInstanceId);
        } else {
            mWifiAwareStateManager.onInitiatePairingResponseFail(id, status);
        }

    }

    @Override
    public void notifyRespondToPairingIndicationResponse(short id, int status) {
        if (status == NanStatusCode.SUCCESS) {
            mWifiAwareStateManager.onRespondToPairingIndicationResponseSuccess(id);
        } else {
            mWifiAwareStateManager.onRespondToPairingIndicationResponseFail(id, status);
        }
    }

    @Override
    public void notifyInitiateBootstrappingResponse(short id, int status,
            int bootstrappingInstanceId) {
        if (status == NanStatusCode.SUCCESS) {
            mWifiAwareStateManager.onInitiateBootStrappingResponseSuccess(id,
                    bootstrappingInstanceId);
        } else {
            mWifiAwareStateManager.onInitiateBootStrappingResponseFail(id, status);
        }
    }

    @Override
    public void notifyRespondToBootstrappingIndicationResponse(short id, int status) {
        if (status == NanStatusCode.SUCCESS) {
            mWifiAwareStateManager.onRespondToBootstrappingIndicationResponseSuccess(id);
        } else {
            mWifiAwareStateManager.onRespondToBootstrappingIndicationResponseFail(id, status);
        }
    }

    @Override
    public void notifySuspendResponse(short id, int status) {
        mWifiAwareStateManager.onSuspendResponse(id, status);
    }

    @Override
    public void notifyResumeResponse(short id, int status) {
        mWifiAwareStateManager.onResumeResponse(id, status);
    }

    @Override
    public void notifyTerminatePairingResponse(short id, int status) {
        mWifiAwareStateManager.onEndPairingResponse(id, status == NanStatusCode.SUCCESS,
                status);
    }

    @Override
    public void eventClusterEvent(int eventType, byte[] addr) {
        incrementCbCount(CB_EV_CLUSTER);
        if (eventType == NanClusterEventType.DISCOVERY_MAC_ADDRESS_CHANGED) {
            mWifiAwareStateManager.onInterfaceAddressChangeNotification(addr);
        } else if (eventType == NanClusterEventType.STARTED_CLUSTER) {
            mWifiAwareStateManager.onClusterChangeNotification(
                    IdentityChangedListener.CLUSTER_CHANGE_EVENT_STARTED, addr);
        } else if (eventType == NanClusterEventType.JOINED_CLUSTER) {
            mWifiAwareStateManager.onClusterChangeNotification(
                    IdentityChangedListener.CLUSTER_CHANGE_EVENT_JOINED, addr);
        } else {
            Log.e(TAG, "eventClusterEvent: invalid eventType=" + eventType);
        }
    }

    @Override
    public void eventDisabled(int status) {
        incrementCbCount(CB_EV_DISABLED);
        mWifiAwareStateManager.onAwareDownNotification(status);
    }

    @Override
    public void eventPublishTerminated(byte sessionId, int status) {
        incrementCbCount(CB_EV_PUBLISH_TERMINATED);
        mWifiAwareStateManager.onSessionTerminatedNotification(sessionId, status, true);
    }

    @Override
    public void eventSubscribeTerminated(byte sessionId, int status) {
        incrementCbCount(CB_EV_SUBSCRIBE_TERMINATED);
        mWifiAwareStateManager.onSessionTerminatedNotification(sessionId, status, false);
    }

    @Override
    public void eventMatch(byte discoverySessionId, int peerId, byte[] addr,
            byte[] serviceSpecificInfo, byte[] matchFilter, int rangingIndicationType,
            int rangingMeasurementInMm, byte[] scid, int peerCipherType, byte[] nonce, byte[] tag,
            AwarePairingConfig pairingConfig, @Nullable List<OuiKeyedData> vendorData) {
        incrementCbCount(CB_EV_MATCH);
        mWifiAwareStateManager.onMatchNotification(discoverySessionId, peerId,
                addr, serviceSpecificInfo, matchFilter, rangingIndicationType,
                rangingMeasurementInMm, scid, peerCipherType, nonce, tag, pairingConfig,
                vendorData);
    }

    @Override
    public void eventMatchExpired(byte discoverySessionId, int peerId) {
        incrementCbCount(CB_EV_MATCH_EXPIRED);
        mWifiAwareStateManager.onMatchExpiredNotification(discoverySessionId, peerId);
    }

    @Override
    public void eventFollowupReceived(byte discoverySessionId, int peerId, byte[] addr,
            byte[] serviceSpecificInfo) {
        incrementCbCount(CB_EV_FOLLOWUP_RECEIVED);
        mWifiAwareStateManager.onMessageReceivedNotification(discoverySessionId, peerId,
                addr, serviceSpecificInfo);
    }

    @Override
    public void eventTransmitFollowup(short id, int status) {
        incrementCbCount(CB_EV_TRANSMIT_FOLLOWUP);
        if (status == NanStatusCode.SUCCESS) {
            mWifiAwareStateManager.onMessageSendSuccessNotification(id);
        } else {
            mWifiAwareStateManager.onMessageSendFailNotification(id, status);
        }
    }

    @Override
    public void eventDataPathRequest(byte discoverySessionId, byte[] peerDiscMacAddr,
            int ndpInstanceId, byte[] appInfo) {
        incrementCbCount(CB_EV_DATA_PATH_REQUEST);
        mWifiAwareStateManager.onDataPathRequestNotification(discoverySessionId,
                peerDiscMacAddr, ndpInstanceId, appInfo);
    }

    @Override
    public void eventDataPathConfirm(int status, int ndpInstanceId, boolean dataPathSetupSuccess,
            byte[] peerNdiMacAddr, byte[] appInfo,
            List<WifiAwareChannelInfo> channelInfos) {
        incrementCbCount(CB_EV_DATA_PATH_CONFIRM);
        if (channelInfos != null) {
            mChannelInfoPerNdp.put(ndpInstanceId, channelInfos);
        }
        mWifiAwareStateManager.onDataPathConfirmNotification(ndpInstanceId,
                peerNdiMacAddr, dataPathSetupSuccess, status, appInfo, channelInfos);
    }

    @Override
    public void eventDataPathScheduleUpdate(byte[] peerDiscoveryAddress,
            ArrayList<Integer> ndpInstanceIds, List<WifiAwareChannelInfo> channelInfo) {
        incrementCbCount(CB_EV_DATA_PATH_SCHED_UPDATE);
        for (int ndpInstanceId : ndpInstanceIds) {
            mChannelInfoPerNdp.put(ndpInstanceId, channelInfo);
        }
        mWifiAwareStateManager.onDataPathScheduleUpdateNotification(peerDiscoveryAddress,
                ndpInstanceIds, channelInfo);
    }

    @Override
    public void eventDataPathTerminated(int ndpInstanceId) {
        incrementCbCount(CB_EV_DATA_PATH_TERMINATED);
        mChannelInfoPerNdp.remove(ndpInstanceId);
        mWifiAwareStateManager.onDataPathEndNotification(ndpInstanceId);
    }

    @Override
    public void eventPairingRequest(int discoverySessionId, int peerId, byte[] peerDiscMacAddr,
            int ndpInstanceId, int requestType, boolean enableCache, byte[] nonce, byte[] tag) {
        mWifiAwareStateManager.onPairingRequestNotification(discoverySessionId, peerId,
                peerDiscMacAddr, ndpInstanceId, requestType, enableCache, nonce, tag);
    }

    @Override
    public void eventPairingConfirm(int pairingId, boolean accept, int reason, int requestType,
            boolean enableCache,
            PairingConfigManager.PairingSecurityAssociationInfo npksa) {
        mWifiAwareStateManager.onPairingConfirmNotification(pairingId, accept, reason, requestType,
                enableCache, npksa);
    }

    @Override
    public void eventBootstrappingRequest(int discoverySessionId, int peerId,
            byte[] peerDiscMacAddr, int bootstrappingInstanceId, int method) {
        mWifiAwareStateManager.onBootstrappingRequestNotification(discoverySessionId, peerId,
                peerDiscMacAddr, bootstrappingInstanceId, method);
    }

    @Override
    public void eventBootstrappingConfirm(int bootstrappingId, int responseCode, int reason,
            int comebackDelay, byte[] cookie) {
        mWifiAwareStateManager.onBootstrappingConfirmNotification(bootstrappingId, responseCode,
                reason, comebackDelay, cookie);
    }

    @Override
    public void eventSuspensionModeChanged(boolean isSuspended) {
        mWifiAwareStateManager.onSuspensionModeChangedNotification(isSuspended);
    }

    /**
     * Reset the channel info when Aware is down.
     */
    /* package */ void resetChannelInfo() {
        mChannelInfoPerNdp.clear();
    }

    /**
     * Dump the internal state of the class.
     */
    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("WifiAwareNativeCallback:");
        pw.println("  mCallbackCounter: " + mCallbackCounter);
        pw.println("  mChannelInfoPerNdp: " + mChannelInfoPerNdp);
    }


    // utilities

    /**
     * Transfer the channel Info dict into a Json String which can be decoded by Json reader.
     * The Format is: "{ndpInstanceId: [{"channelFreq": channelFreq,
     * "channelBandwidth": channelBandwidth, "numSpatialStreams": numSpatialStreams}]}"
     * @return Json String.
     */
    private String convertChannelInfoToJsonString() {
        JSONObject channelInfoJson = new JSONObject();
        try {
            for (int i = 0; i < mChannelInfoPerNdp.size(); i++) {
                JSONArray infoJsonArray = new JSONArray();
                for (WifiAwareChannelInfo info : mChannelInfoPerNdp.valueAt(i)) {
                    JSONObject j = new JSONObject();
                    j.put("channelFreq", info.getChannelFrequencyMhz());
                    j.put("channelBandwidth", info.getChannelBandwidth());
                    j.put("numSpatialStreams", info.getSpatialStreamCount());
                    infoJsonArray.put(j);
                }
                channelInfoJson.put(Integer.toString(mChannelInfoPerNdp.keyAt(i)), infoJsonArray);
            }
        } catch (JSONException e) {
            Log.e(TAG, "onCommand: get_channel_info e=" + e);
        }
        return channelInfoJson.toString();
    }
}
