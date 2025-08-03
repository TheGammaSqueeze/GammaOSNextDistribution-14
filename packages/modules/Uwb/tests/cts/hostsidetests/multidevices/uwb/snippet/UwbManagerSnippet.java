/*
 * Copyright 2022 The Android Open Source Project
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

package com.google.snippet.uwb;

import android.app.UiAutomation;
import android.content.Context;
import android.net.ConnectivityManager;
import android.os.Build;
import android.os.PersistableBundle;
import android.uwb.RangingMeasurement;
import android.uwb.RangingReport;
import android.uwb.RangingSession;
import android.uwb.UwbAddress;
import android.uwb.UwbManager;

import androidx.test.platform.app.InstrumentationRegistry;

import com.google.android.mobly.snippet.Snippet;
import com.google.android.mobly.snippet.event.EventCache;
import com.google.android.mobly.snippet.event.SnippetEvent;
import com.google.android.mobly.snippet.rpc.AsyncRpc;
import com.google.android.mobly.snippet.rpc.Rpc;
import com.google.android.mobly.snippet.util.Log;
import com.google.uwb.support.ccc.CccOpenRangingParams;
import com.google.uwb.support.ccc.CccParams;
import com.google.uwb.support.ccc.CccPulseShapeCombo;
import com.google.uwb.support.ccc.CccRangingStartedParams;
import com.google.uwb.support.fira.FiraControleeParams;
import com.google.uwb.support.fira.FiraOpenSessionParams;
import com.google.uwb.support.fira.FiraParams;
import com.google.uwb.support.fira.FiraRangingReconfigureParams;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/** Snippet class exposing Android APIs for Uwb. */
public class UwbManagerSnippet implements Snippet {
    private static class UwbManagerSnippetException extends Exception {

        UwbManagerSnippetException(String msg) {
            super(msg);
        }

        UwbManagerSnippetException(String msg, Throwable err) {
            super(msg, err);
        }
    }

    private static final String TAG = "UwbManagerSnippet: ";
    private final UwbManager mUwbManager;
    private final ConnectivityManager mConnectivityManager;
    private final Context mContext;
    private final Executor mExecutor = Executors.newSingleThreadExecutor();
    private final EventCache mEventCache = EventCache.getInstance();
    private static HashMap<String, RangingSessionCallback> sRangingSessionCallbackMap =
            new HashMap<String, RangingSessionCallback>();
    private static HashMap<String, UwbAdapterStateCallback> sUwbAdapterStateCallbackMap =
            new HashMap<String, UwbAdapterStateCallback>();

    public UwbManagerSnippet() throws Throwable {
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        mUwbManager = mContext.getSystemService(UwbManager.class);
        mConnectivityManager = mContext.getSystemService(ConnectivityManager.class);
        adoptShellPermission();
    }

    private enum Event {
        Invalid(0),
        Opened(1 << 0),
        Started(1 << 1),
        Reconfigured(1 << 2),
        Stopped(1 << 3),
        Closed(1 << 4),
        OpenFailed(1 << 5),
        StartFailed(1 << 6),
        ReconfigureFailed(1 << 7),
        StopFailed(1 << 8),
        CloseFailed(1 << 9),
        ReportReceived(1 << 10),
        ControleeAdded(1 << 11),
        ControleeAddFailed(1 << 12),
        ControleeRemoved(1 << 13),
        ControleeRemoveFailed(1 << 14),
        Paused(1 << 15),
        PauseFailed(1 << 16),
        Resumed(1 << 17),
        ResumeFailed(1 << 18),
        DataSent(1 << 19),
        DataSendFailed(1 << 20),
        DataReceived(1 << 21),
        DataReceiveFailed(1 << 22),
        ServiceDiscovered(1 << 23),
        ServiceConnected(1 << 24),
        RangingRoundsUpdateDtTagStatus(1 << 25),
        EventAll(
                1 << 0
                | 1 << 1
                | 1 << 2
                | 1 << 3
                | 1 << 4
                | 1 << 5
                | 1 << 6
                | 1 << 7
                | 1 << 8
                | 1 << 9
                | 1 << 10
                | 1 << 11
                | 1 << 12
                | 1 << 13
                | 1 << 14
                | 1 << 15
                | 1 << 16
                | 1 << 17
                | 1 << 18
                | 1 << 19
                | 1 << 20
                | 1 << 21
                | 1 << 22
                | 1 << 23
                | 1 << 24
                | 1 << 25
        );

        private final int mType;
        Event(int type) {
            mType = type;
        }
        private int getType() {
            return mType;
        }
    }

    class UwbAdapterStateCallback implements UwbManager.AdapterStateCallback {

        public String mId;

        UwbAdapterStateCallback(String id) {
            mId = id;
        }

        public String toString(int state) {
            switch (state) {
                case 1: return "Inactive";
                case 2: return "Active";
                default: return "Disabled";
            }
        }

        @Override
        public void onStateChanged(int state, int reason) {
            Log.d(TAG + "UwbAdapterStateCallback#onStateChanged() called");
            Log.d(TAG + "Adapter state " + String.valueOf(state)
                    + ", state changed reason " + String.valueOf(reason));
            SnippetEvent event = new SnippetEvent(mId, "UwbAdapterStateCallback");
            event.getData().putString("uwbAdapterStateEvent", toString(state));
            mEventCache.postEvent(event);
        }
    }

    class RangingSessionCallback implements RangingSession.Callback {

        public RangingSession rangingSession;
        public PersistableBundle persistableBundle;
        public PersistableBundle sessionInfo;
        public RangingReport rangingReport;
        public String mId;
        public UwbAddress uwbAddress;
        public byte[] dataReceived;

        RangingSessionCallback(String id, int events) {
            mId = id;
        }

        private void handleEvent(Event e) {
            Log.d(TAG + "RangingSessionCallback#handleEvent() for " + e.toString());
            SnippetEvent event = new SnippetEvent(mId, "RangingSessionCallback");
            event.getData().putString("rangingSessionEvent", e.toString());
            mEventCache.postEvent(event);
        }

        @Override
        public void onOpened(RangingSession session) {
            Log.d(TAG + "RangingSessionCallback#onOpened() called");
            rangingSession = session;
            handleEvent(Event.Opened);
        }

        @Override
        public void onOpenFailed(int reason, PersistableBundle params) {
            Log.d(TAG + "RangingSessionCallback#onOpenedFailed() called");
            Log.d(TAG + "OpenFailed reason " + String.valueOf(reason));
            persistableBundle = params;
            handleEvent(Event.OpenFailed);
        }

        @Override
        public void onStarted(PersistableBundle info) {
            Log.d(TAG + "RangingSessionCallback#onStarted() called");
            sessionInfo = info;
            handleEvent(Event.Started);
        }

        @Override
        public void onStartFailed(int reason, PersistableBundle params) {
            Log.d(TAG + "RangingSessionCallback#onStartFailed() called");
            Log.d(TAG + "StartFailed reason " + String.valueOf(reason));
            persistableBundle = params;
            handleEvent(Event.StartFailed);
        }

        @Override
        public void onReconfigured(PersistableBundle params) {
            Log.d(TAG + "RangingSessionCallback#oniReconfigured() called");
            persistableBundle = params;
            handleEvent(Event.Reconfigured);
        }

        @Override
        public void onReconfigureFailed(int reason, PersistableBundle params) {
            Log.d(TAG + "RangingSessionCallback#onReconfigureFailed() called");
            Log.d(TAG + "ReconfigureFailed reason " + String.valueOf(reason));
            persistableBundle = params;
            handleEvent(Event.ReconfigureFailed);
        }

        @Override
        public void onStopped(int reason, PersistableBundle params) {
            Log.d(TAG + "RangingSessionCallback#onStopped() called");
            Log.d(TAG + "Stopped reason " + String.valueOf(reason));
            persistableBundle = params;
            handleEvent(Event.Stopped);
        }

        @Override
        public void onStopFailed(int reason, PersistableBundle params) {
            Log.d(TAG + "RangingSessionCallback#onStopFailed() called");
            Log.d(TAG + "StopFailed reason " + String.valueOf(reason));
            persistableBundle = params;
            handleEvent(Event.StopFailed);
        }

        @Override
        public void onClosed(int reason, PersistableBundle params) {
            Log.d(TAG + "RangingSessionCallback#onClosed() called");
            Log.d(TAG + "Closed reason " + String.valueOf(reason));
            persistableBundle = params;
            handleEvent(Event.Closed);
        }

        @Override
        public void onReportReceived(RangingReport report) {
            Log.d(TAG + "RangingSessionCallback#onReportReceived() called");
            rangingReport = report;
            handleEvent(Event.ReportReceived);
        }

        @Override
        public void onControleeAdded(PersistableBundle params) {
            Log.d(TAG + "RangingSessionCallback#onControleeAdded() called");
            persistableBundle = params;
            handleEvent(Event.ControleeAdded);

        }

        @Override
        public void onControleeAddFailed(
                int reason, PersistableBundle params) {
            Log.d(TAG + "RangingSessionCallback#onControleeAddFailed() called");
            Log.d(TAG + "ControleeAddFailed reason " + String.valueOf(reason));
            persistableBundle = params;
            handleEvent(Event.ControleeAddFailed);

        }

        @Override
        public void onControleeRemoved(PersistableBundle params) {
            Log.d(TAG + "RangingSessionCallback#onControleeRemoved() called");
            persistableBundle = params;
            handleEvent(Event.ControleeRemoved);
        }

        @Override
        public void onControleeRemoveFailed(
                int reason, PersistableBundle params) {
            Log.d(TAG + "RangingSessionCallback#onControleeRemoveFailed() called");
            Log.d(TAG + "ControleeRemoveFailed reason " + String.valueOf(reason));
            persistableBundle = params;
            handleEvent(Event.ControleeRemoveFailed);
        }

        @Override
        public void onPaused(PersistableBundle params) {
            Log.d(TAG + "RangingSessionCallback#onPaused() called");
            persistableBundle = params;
            handleEvent(Event.Paused);
        }

        @Override
        public void onPauseFailed(int reason, PersistableBundle params) {
            Log.d(TAG + "RangingSessionCallback#onPauseFailed() called");
            Log.d(TAG + "PauseFailed reason " + String.valueOf(reason));
            persistableBundle = params;
            handleEvent(Event.PauseFailed);
        }

        @Override
        public void onResumed(PersistableBundle params) {
            Log.d(TAG + "RangingSessionCallback#onResumed() called");
            persistableBundle = params;
            handleEvent(Event.Resumed);
        }

        @Override
        public void onResumeFailed(int reason, PersistableBundle params) {
            Log.d(TAG + "RangingSessionCallback#onResumeFailed() called");
            Log.d(TAG + "ResumeFailed reason " + String.valueOf(reason));
            persistableBundle = params;
            handleEvent(Event.ResumeFailed);
        }

        @Override
        public void onDataSent(UwbAddress remoteDeviceAddress,
                PersistableBundle params) {
            Log.d(TAG + "RangingSessionCallback#onDataSent() called");
            uwbAddress = getComputedMacAddress(remoteDeviceAddress);
            persistableBundle = params;
            handleEvent(Event.DataSent);
        }

        @Override
        public void onDataSendFailed(UwbAddress remoteDeviceAddress,
                int reason, PersistableBundle params) {
            Log.d(TAG + "RangingSessionCallback#onDataSendFailed() called");
            Log.d(TAG + "DataSendFailed reason " + String.valueOf(reason));
            uwbAddress = getComputedMacAddress(remoteDeviceAddress);
            persistableBundle = params;
            handleEvent(Event.DataSendFailed);
        }

        @Override
        public void onDataReceived(UwbAddress remoteDeviceAddress,
                PersistableBundle params, byte[] data) {
            Log.d(TAG + "RangingSessionCallback#onDataReceived() called");
            uwbAddress = getComputedMacAddress(remoteDeviceAddress);
            dataReceived = data;
            persistableBundle = params;
            handleEvent(Event.DataReceived);
        }

        @Override
        public void onDataReceiveFailed(UwbAddress remoteDeviceAddress,
                int reason, PersistableBundle params) {
            Log.d(TAG + "RangingSessionCallback#onDataReceiveFailed() called");
            Log.d(TAG + "DataReceiveFailed reason " + String.valueOf(reason));
            uwbAddress = getComputedMacAddress(remoteDeviceAddress);
            persistableBundle = params;
            handleEvent(Event.DataReceiveFailed);
        }

        @Override
        public void onServiceDiscovered(PersistableBundle params) {
            Log.d(TAG + "RangingSessionCallback#onServiceDiscovered() called");
            persistableBundle = params;
            handleEvent(Event.ServiceDiscovered);
        }

        @Override
        public void onServiceConnected(PersistableBundle params) {
            Log.d(TAG + "RangingSessionCallback#onServiceConnected() called");
            persistableBundle = params;
            handleEvent(Event.ServiceConnected);
        }

        // TODO: This is only available in Android U SDK. So, expose it there only.
        public void onRangingRoundsUpdateDtTagStatus(PersistableBundle params) {
            Log.d(TAG + "RangingSessionCallback#onRangingRoundsUpdateDtTagStatus() called");
            persistableBundle = params;
            handleEvent(Event.RangingRoundsUpdateDtTagStatus);
        }
    }

    /** Register uwb adapter state callback. */
    @AsyncRpc(description = "Register uwb adapter state callback")
    public void registerUwbAdapterStateCallback(String callbackId, String key) {
        UwbAdapterStateCallback uwbAdapterStateCallback = new UwbAdapterStateCallback(callbackId);
        sUwbAdapterStateCallbackMap.put(key, uwbAdapterStateCallback);
        mUwbManager.registerAdapterStateCallback(mExecutor, uwbAdapterStateCallback);
    }

    /** Unregister uwb adapter state callback. */
    @Rpc(description = "Unregister uwb adapter state callback.")
    public void unregisterUwbAdapterStateCallback(String key) {
        UwbAdapterStateCallback uwbAdapterStateCallback = sUwbAdapterStateCallbackMap.get(key);
        mUwbManager.unregisterAdapterStateCallback(uwbAdapterStateCallback);
        sUwbAdapterStateCallbackMap.remove(key);
    }

    /** Get UWB adapter state. */
    @Rpc(description = "Get Uwb adapter state")
    public int getAdapterState() {
        return mUwbManager.getAdapterState();
    }

    /** Get the UWB state. */
    @Rpc(description = "Get Uwb state")
    public boolean isUwbEnabled() {
        return mUwbManager.isUwbEnabled();
    }

    /** Get the UWB state. */
    @Rpc(description = "Set Uwb state")
    public void setUwbEnabled(boolean enabled) {
        mUwbManager.setUwbEnabled(enabled);
    }

    private byte[] convertJSONArrayToByteArray(JSONArray jArray) throws JSONException {
        if (jArray == null) {
            return null;
        }
        byte[] bArray = new byte[jArray.length()];
        for (int i = 0; i < jArray.length(); i++) {
            bArray[i] = (byte) jArray.getInt(i);
        }
        return bArray;
    }

    private FiraRangingReconfigureParams generateFiraRangingReconfigureParams(JSONObject j)
            throws JSONException {
        if (j == null) {
            return null;
        }
        FiraRangingReconfigureParams.Builder builder = new FiraRangingReconfigureParams.Builder();
        if (j.has("action")) {
            builder.setAction(j.getInt("action"));
        }
        if (j.has("addressList")) {
            JSONArray jArray = j.getJSONArray("addressList");
            UwbAddress[] addressList = new UwbAddress[jArray.length()];
            for (int i = 0; i < jArray.length(); i++) {
                addressList[i] = getComputedMacAddress(UwbAddress.fromBytes(
                        convertJSONArrayToByteArray(jArray.getJSONArray(i))));
            }
            builder.setAddressList(addressList);
        }
        if (j.has("subSessionIdList")) {
            JSONArray jArray = j.getJSONArray("subSessionIdList");
            int[] subSessionIdList = new int[jArray.length()];
            for (int i = 0; i < jArray.length(); i++) {
                subSessionIdList[i] = jArray.getInt(i);
            }
            builder.setSubSessionIdList(subSessionIdList);
        }
        if (j.has("subSessionKeyList")) {
            JSONArray jSubSessionKeyListArray = j.getJSONArray("subSessionKeyList");
            builder.setSubSessionKeyList(convertJSONArrayToByteArray(jSubSessionKeyListArray));
        }
        if (j.has("blockStrideLength")) {
            builder.setBlockStrideLength(j.getInt("blockStrideLength"));
        }
        return builder.build();
    }

    private FiraControleeParams generateFiraControleeParams(JSONObject j) throws JSONException {
        if (j == null) {
            return null;
        }
        FiraControleeParams.Builder builder = new FiraControleeParams.Builder();
        if (j.has("action")) {
            builder.setAction(j.getInt("action"));
        }
        if (j.has("addressList")) {
            JSONArray jArray = j.getJSONArray("addressList");
            UwbAddress[] addressList = new UwbAddress[jArray.length()];
            for (int i = 0; i < jArray.length(); i++) {
                addressList[i] = getComputedMacAddress(UwbAddress.fromBytes(
                        convertJSONArrayToByteArray(jArray.getJSONArray(i))));
            }
            builder.setAddressList(addressList);
        }
        if (j.has("subSessionIdList")) {
            JSONArray jArray = j.getJSONArray("subSessionIdList");
            int[] subSessionIdList = new int[jArray.length()];
            for (int i = 0; i < jArray.length(); i++) {
                subSessionIdList[i] = jArray.getInt(i);
            }
            builder.setSubSessionIdList(subSessionIdList);
        }
        if (j.has("subSessionKeyList")) {
            JSONArray jSubSessionKeyListArray = j.getJSONArray("subSessionKeyList");
            builder.setSubSessionKeyList(convertJSONArrayToByteArray(jSubSessionKeyListArray));
        }
        return builder.build();
    }

    private CccRangingStartedParams generateCccRangingStartedParams(JSONObject j)
            throws JSONException {
        if (j == null) {
            return null;
        }
        CccRangingStartedParams.Builder builder = new CccRangingStartedParams.Builder();
        if (j.has("stsIndex")) {
            builder.setStartingStsIndex(j.getInt("stsIndex"));
        }
        if (j.has("uwbTime")) {
            builder.setUwbTime0(j.getInt("uwbTime"));
        }
        if (j.has("hopModeKey")) {
            builder.setHopModeKey(j.getInt("hopModeKey"));
        }
        if (j.has("syncCodeIndex")) {
            builder.setSyncCodeIndex(j.getInt("syncCodeIndex"));
        }
        if (j.has("ranMultiplier")) {
            builder.setRanMultiplier(j.getInt("ranMultiplier"));
        }

        return builder.build();
    }

    private CccOpenRangingParams generateCccOpenRangingParams(JSONObject j) throws JSONException {
        if (j == null) {
            return null;
        }
        CccOpenRangingParams.Builder builder = new CccOpenRangingParams.Builder();
        builder.setProtocolVersion(CccParams.PROTOCOL_VERSION_1_0);
        if (j.has("sessionId")) {
            builder.setSessionId(j.getInt("sessionId"));
        }
        if (j.has("uwbConfig")) {
            builder.setUwbConfig(j.getInt("uwbConfig"));
        }
        if (j.has("ranMultiplier")) {
            builder.setRanMultiplier(j.getInt("ranMultiplier"));
        }
        if (j.has("channel")) {
            builder.setChannel(j.getInt("channel"));
        }
        if (j.has("chapsPerSlot")) {
            builder.setNumChapsPerSlot(j.getInt("chapsPerSlot"));
        }
        if (j.has("responderNodes")) {
            builder.setNumResponderNodes(j.getInt("responderNodes"));
        }
        if (j.has("slotsPerRound")) {
            builder.setNumSlotsPerRound(j.getInt("slotsPerRound"));
        }
        if (j.has("hoppingMode")) {
            builder.setHoppingConfigMode(j.getInt("hoppingMode"));
        }
        if (j.has("hoppingSequence")) {
            builder.setHoppingSequence(j.getInt("hoppingSequence"));
        }
        if (j.has("syncCodeIndex")) {
            builder.setSyncCodeIndex(j.getInt("syncCodeIndex"));
        }
        if (j.has("pulseShapeCombo")) {
            JSONObject pulseShapeCombo = j.getJSONObject("pulseShapeCombo");
            builder.setPulseShapeCombo(new CccPulseShapeCombo(
                    pulseShapeCombo.getInt("pulseShapeComboTx"),
                    pulseShapeCombo.getInt("pulseShapeComboRx")));
        }

        return builder.build();
    }

    private FiraOpenSessionParams generateFiraOpenSessionParams(JSONObject j) throws JSONException {
        if (j == null) {
            return null;
        }
        FiraOpenSessionParams.Builder builder = new FiraOpenSessionParams.Builder();
        builder.setProtocolVersion(FiraParams.PROTOCOL_VERSION_1_1);
        if (j.has("sessionId")) {
            builder.setSessionId(j.getInt("sessionId"));
        }
        if (j.has("deviceType")) {
            builder.setDeviceType(j.getInt("deviceType"));
        }
        if (j.has("deviceRole")) {
            builder.setDeviceRole(j.getInt("deviceRole"));
        }
        if (j.has("rangingRoundUsage")) {
            builder.setRangingRoundUsage(j.getInt("rangingRoundUsage"));
        }
        if (j.has("multiNodeMode")) {
            builder.setMultiNodeMode(j.getInt("multiNodeMode"));
        }
        if (j.has("deviceAddress")) {
            JSONArray jArray = j.getJSONArray("deviceAddress");
            byte[] bArray = convertJSONArrayToByteArray(jArray);
            UwbAddress deviceAddress = getComputedMacAddress(UwbAddress.fromBytes(bArray));
            builder.setDeviceAddress(deviceAddress);
        }
        if (j.has("destinationAddresses")) {
            JSONArray jArray = j.getJSONArray("destinationAddresses");
            UwbAddress[] destinationUwbAddresses = new UwbAddress[jArray.length()];
            for (int i = 0; i < jArray.length(); i++) {
                destinationUwbAddresses[i] = getComputedMacAddress(UwbAddress.fromBytes(
                        convertJSONArrayToByteArray(jArray.getJSONArray(i))));
            }
            builder.setDestAddressList(Arrays.asList(destinationUwbAddresses));
        }
        if (j.has("initiationTimeMs")) {
            builder.setInitiationTime(j.getInt("initiationTimeMs"));
        }
        if (j.has("slotDurationRstu")) {
            builder.setSlotDurationRstu(j.getInt("slotDurationRstu"));
        }
        if (j.has("slotsPerRangingRound")) {
            builder.setSlotsPerRangingRound(j.getInt("slotsPerRangingRound"));
        }
        if (j.has("rangingIntervalMs")) {
            builder.setRangingIntervalMs(j.getInt("rangingIntervalMs"));
        }
        if (j.has("blockStrideLength")) {
            builder.setBlockStrideLength(j.getInt("blockStrideLength"));
        }
        if (j.has("hoppingMode")) {
            builder.setHoppingMode(j.getInt("hoppingMode"));
        }
        if (j.has("maxRangingRoundRetries")) {
            builder.setMaxRangingRoundRetries(j.getInt("maxRangingRoundRetries"));
        }
        if (j.has("sessionPriority")) {
            builder.setSessionPriority(j.getInt("sessionPriority"));
        }
        if (j.has("macAddressMode")) {
            builder.setMacAddressMode(j.getInt("macAddressMode"));
        }
        if (j.has("inBandTerminationAttemptCount")) {
            builder.setInBandTerminationAttemptCount(j.getInt("inBandTerminationAttemptCount"));
        }
        if (j.has("channel")) {
            builder.setChannelNumber(j.getInt("channel"));
        }
        if (j.has("preamble")) {
            builder.setPreambleCodeIndex(j.getInt("preamble"));
        }
        if (j.getInt("stsConfig") == FiraParams.STS_CONFIG_STATIC) {
            JSONArray jVendorIdArray = j.getJSONArray("vendorId");
            builder.setVendorId(getComputedVendorId(convertJSONArrayToByteArray(jVendorIdArray)));
            JSONArray jStatisStsIVArray = j.getJSONArray("staticStsIV");
            builder.setStaticStsIV(convertJSONArrayToByteArray(jStatisStsIVArray));
        } else if (j.getInt("stsConfig") == FiraParams.STS_CONFIG_PROVISIONED) {
            builder.setStsConfig(j.getInt("stsConfig"));
            JSONArray jSessionKeyArray = j.getJSONArray("sessionKey");
            builder.setSessionKey(convertJSONArrayToByteArray(jSessionKeyArray));
        } else if (j.getInt(
                "stsConfig") == FiraParams.STS_CONFIG_PROVISIONED_FOR_CONTROLEE_INDIVIDUAL_KEY) {
            builder.setStsConfig(j.getInt("stsConfig"));
            JSONArray jSessionKeyArray = j.getJSONArray("sessionKey");
            builder.setSessionKey(convertJSONArrayToByteArray(jSessionKeyArray));
            if (j.getInt("deviceType") == FiraParams.RANGING_DEVICE_TYPE_CONTROLEE) {
                JSONArray jSubSessionKeyArray = j.getJSONArray("subSessionKey");
                builder.setSubsessionKey(convertJSONArrayToByteArray(jSubSessionKeyArray));
                builder.setSubSessionId(j.getInt("subSessionId"));
            }
        }
        if (j.has("aoaResultRequest")) {
            builder.setAoaResultRequest(j.getInt("aoaResultRequest"));
        }
        if (j.has("filterType")) {
            builder.setFilterType(j.getInt("filterType"));
        }

        return builder.build();
    }

    private RangingMeasurement getRangingMeasurement(String key, JSONArray jArray)
            throws JSONException {
        byte[] bArray = convertJSONArrayToByteArray(jArray);
        UwbAddress peerAddress = getComputedMacAddress(UwbAddress.fromBytes(bArray));
        RangingSessionCallback rangingSessionCallback = sRangingSessionCallbackMap.get(key);
        List<RangingMeasurement> rangingMeasurements =
                rangingSessionCallback.rangingReport.getMeasurements();
        for (RangingMeasurement r: rangingMeasurements) {
            if (r.getStatus() == RangingMeasurement.RANGING_STATUS_SUCCESS
                    && r.getRemoteDeviceAddress().equals(peerAddress)) {
                Log.d(TAG + "Found peer " + peerAddress.toString());
                return r;
            }
        }
        Log.w(TAG + "Invalid ranging status or peer not found.");
        return null;
    }

    /** Open FIRA UWB ranging session. */
    @AsyncRpc(description = "Open FIRA UWB ranging session")
    public void openFiraRangingSession(String callbackId, String key, JSONObject config)
            throws JSONException {
        RangingSessionCallback rangingSessionCallback = new RangingSessionCallback(
                callbackId, Event.EventAll.getType());
        FiraOpenSessionParams params = generateFiraOpenSessionParams(config);
        mUwbManager.openRangingSession(params.toBundle(), mExecutor, rangingSessionCallback);
        sRangingSessionCallbackMap.put(key, rangingSessionCallback);
    }

    /** Open CCC UWB ranging session. */
    @AsyncRpc(description = "Open CCC UWB ranging session")
    public void openCccRangingSession(String callbackId, String key, JSONObject config)
            throws JSONException {
        RangingSessionCallback rangingSessionCallback = new RangingSessionCallback(
                callbackId, Event.EventAll.getType());
        CccOpenRangingParams params = generateCccOpenRangingParams(config);
        mUwbManager.openRangingSession(params.toBundle(), mExecutor, rangingSessionCallback);
        sRangingSessionCallbackMap.put(key, rangingSessionCallback);
    }

    /** Start FIRA UWB ranging. */
    @Rpc(description = "Start FIRA UWB ranging")
    public void startFiraRangingSession(String key) {
        RangingSessionCallback rangingSessionCallback = sRangingSessionCallbackMap.get(key);
        rangingSessionCallback.rangingSession.start(new PersistableBundle());
    }

    /** Start CCC UWB ranging. */
    @Rpc(description = "Start CCC UWB ranging")
    public void startCccRangingSession(String key, JSONObject config) throws JSONException {
        RangingSessionCallback rangingSessionCallback = sRangingSessionCallbackMap.get(key);
        CccRangingStartedParams params = generateCccRangingStartedParams(config);
        rangingSessionCallback.rangingSession.start(params.toBundle());
    }

    /** Reconfigures FIRA UWB ranging session. */
    @Rpc(description = "Reconfigure FIRA UWB ranging session")
    public void reconfigureFiraRangingSession(String key, JSONObject config) throws JSONException {
        RangingSessionCallback rangingSessionCallback = sRangingSessionCallbackMap.get(key);
        FiraRangingReconfigureParams params = generateFiraRangingReconfigureParams(config);
        rangingSessionCallback.rangingSession.reconfigure(params.toBundle());
    }

    /** Reconfigures FIRA UWB ranging session to add controlee. */
    @Rpc(description = "Reconfigure FIRA UWB ranging session to add controlee")
    public void addControleeFiraRangingSession(String key, JSONObject config) throws JSONException {
        RangingSessionCallback rangingSessionCallback = sRangingSessionCallbackMap.get(key);
        FiraControleeParams params = generateFiraControleeParams(config);
        rangingSessionCallback.rangingSession.addControlee(params.toBundle());
    }

    /** Reconfigures FIRA UWB ranging session to remove controlee. */
    @Rpc(description = "Reconfigure FIRA UWB ranging session to remove controlee")
    public void removeControleeFiraRangingSession(String key, JSONObject config)
            throws JSONException {
        RangingSessionCallback rangingSessionCallback = sRangingSessionCallbackMap.get(key);
        FiraControleeParams params = generateFiraControleeParams(config);
        rangingSessionCallback.rangingSession.removeControlee(params.toBundle());
    }

    /**
     * Find if UWB peer is found.
     */
    @Rpc(description = "Find if UWB peer is found")
    public boolean isUwbPeerFound(String key, JSONArray jArray) throws JSONException {
        return getRangingMeasurement(key, jArray) != null;
    }

    /** Get UWB distance measurement. */
    @Rpc(description = "Get UWB ranging distance measurement with peer.")
    public double getDistanceMeasurement(String key, JSONArray jArray) throws JSONException {
        RangingMeasurement rangingMeasurement = getRangingMeasurement(key, jArray);
        if (rangingMeasurement == null || rangingMeasurement.getDistanceMeasurement() == null) {
            throw new NullPointerException("Cannot get Distance Measurement on null object.");
        }
        return rangingMeasurement.getDistanceMeasurement().getMeters();
    }

    /** Get angle of arrival azimuth measurement. */
    @Rpc(description = "Get UWB AoA Azimuth measurement.")
    public double getAoAAzimuthMeasurement(String key, JSONArray jArray) throws JSONException {
        RangingMeasurement rangingMeasurement = getRangingMeasurement(key, jArray);
        if (rangingMeasurement == null
                || rangingMeasurement.getAngleOfArrivalMeasurement() == null
                || rangingMeasurement.getAngleOfArrivalMeasurement().getAzimuth() == null) {
            throw new NullPointerException("Cannot get AoA azimuth measurement on null object.");
        }
        return rangingMeasurement.getAngleOfArrivalMeasurement().getAzimuth().getRadians();
    }

    /** Get angle of arrival altitude measurement. */
    @Rpc(description = "Get UWB AoA Altitude measurement.")
    public double getAoAAltitudeMeasurement(String key, JSONArray jArray) throws JSONException {
        RangingMeasurement rangingMeasurement = getRangingMeasurement(key, jArray);
        if (rangingMeasurement == null
                || rangingMeasurement.getAngleOfArrivalMeasurement() == null
                || rangingMeasurement.getAngleOfArrivalMeasurement().getAltitude() == null) {
            throw new NullPointerException("Cannot get AoA altitude measurement on null object.");
        }
        return rangingMeasurement.getAngleOfArrivalMeasurement().getAltitude().getRadians();
    }

    /** Get RSSI measurement. */
    @Rpc(description = "Get RSSI measurement.")
    public int getRssiDbmMeasurement(String key, JSONArray jArray) throws JSONException {
        RangingMeasurement rangingMeasurement = getRangingMeasurement(key, jArray);
        if (rangingMeasurement == null) {
            throw new NullPointerException("Cannot get RSSI dBm measurement on null object.");
        }
        return rangingMeasurement.getRssiDbm();
    }

    /** Stop UWB ranging. */
    @Rpc(description = "Stop UWB ranging")
    public void stopRangingSession(String key) {
        RangingSessionCallback rangingSessionCallback = sRangingSessionCallbackMap.get(key);
        rangingSessionCallback.rangingSession.stop();
    }

    /** Close UWB ranging session. */
    @Rpc(description = "Close UWB ranging session")
    public void closeRangingSession(String key) {
        RangingSessionCallback rangingSessionCallback = sRangingSessionCallbackMap.remove(key);
        if (rangingSessionCallback != null && rangingSessionCallback.rangingSession != null) {
            rangingSessionCallback.rangingSession.close();
        }
    }

    private JSONObject convertPersistableBundleToJson(PersistableBundle bundle)
            throws JSONException {
        JSONObject jsonObj = new JSONObject();
        Set<String> keys = bundle.keySet();
        for (String key: keys) {
            if (bundle.get(key) instanceof PersistableBundle) {
                jsonObj.put(key, convertPersistableBundleToJson(
                        (PersistableBundle) bundle.get(key)));
            } else {
                jsonObj.put(key, JSONObject.wrap(bundle.get(key)));
            }
        }
        return jsonObj;
    }

    /** Get UWB specification info */
    @Rpc(description = "Get Uwb specification info")
    public JSONObject getSpecificationInfo() throws JSONException {
        return convertPersistableBundleToJson(mUwbManager.getSpecificationInfo());
    }

    /** Set airplane mode to True or False */
    @Rpc(description = "Set airplane mode")
    public void setAirplaneMode(Boolean enabled) {
        mConnectivityManager.setAirplaneMode(enabled);
    }

    @Rpc(description = "Log info level message to device logcat")
    public void logInfo(String message) throws JSONException {
        Log.i(TAG + message);
    }

    @Override
    public void shutdown() {}

    private void adoptShellPermission() throws Throwable {
        UiAutomation uia = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        uia.adoptShellPermissionIdentity();
        try {
            Class<?> cls = Class.forName("android.app.UiAutomation");
            Method destroyMethod = cls.getDeclaredMethod("destroy");
            destroyMethod.invoke(uia);
        } catch (ReflectiveOperationException e) {
            throw new UwbManagerSnippetException("Failed to cleaup Ui Automation", e);
        }
    }

    private static byte[] getReverseBytes(byte[] data) {
        byte[] buffer = new byte[data.length];
        for (int i = 0; i < data.length; i++) {
            buffer[i] = data[data.length - 1 - i];
        }
        return buffer;
    }
    private static UwbAddress getComputedMacAddress(UwbAddress address) {
        if (android.os.Build.VERSION.SDK_INT <= Build.VERSION_CODES.TIRAMISU) {
            return UwbAddress.fromBytes(getReverseBytes(address.toBytes()));
        }
        return address;
    }

    private static byte[] getComputedVendorId(byte[] data) {
        if (android.os.Build.VERSION.SDK_INT <= Build.VERSION_CODES.TIRAMISU) {
            return getReverseBytes(data);
        }
        return data;
    }
}
