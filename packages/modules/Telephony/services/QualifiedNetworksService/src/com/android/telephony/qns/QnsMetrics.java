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

package com.android.telephony.qns;

import android.content.Context;
import android.net.NetworkCapabilities;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.telephony.AccessNetworkConstants;
import android.telephony.AccessNetworkConstants.AccessNetworkType;
import android.telephony.SignalThresholdInfo;
import android.telephony.qns.QnsProtoEnums;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.State;
import com.android.internal.util.StateMachine;
import com.android.telephony.qns.DataConnectionStatusTracker.DataConnectionChangedInfo;
import com.android.telephony.qns.QualifiedNetworksServiceImpl.QualifiedNetworksInfo;
import com.android.telephony.qns.atoms.AtomsQnsFallbackRestrictionChangedInfo;
import com.android.telephony.qns.atoms.AtomsQnsHandoverPingPongInfo;
import com.android.telephony.qns.atoms.AtomsQnsHandoverTimeMillisInfo;
import com.android.telephony.qns.atoms.AtomsQnsImsCallDropStats;
import com.android.telephony.qns.atoms.AtomsQnsRatPreferenceMismatchInfo;
import com.android.telephony.qns.atoms.AtomsQualifiedRatListChangedInfo;
import com.android.telephony.statslib.StatsLib;

import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/** QnsStats class */
class QnsMetrics {

    private StatsLib mStats;
    private final String mLogTag;
    private final Handler mHandler;
    private final HandlerThread mHandlerThread;

    // For HandoverTIme.
    private final ConcurrentHashMap<Integer, HandoverTimeStateMachine> mHandoverTimeMap;
    private final ConcurrentHashMap<Integer, PingPongTime> mPingPongTime;
    private final ConcurrentHashMap<Integer, RatMismatchTime> mRatMismatchTime;
    private final ConcurrentHashMap<Integer, RtpCallDrop> mRtpCallDrop;

    /** Constructor */
    QnsMetrics(Context context) {
        mStats = new StatsLib(context);
        mLogTag = QnsMetrics.class.getSimpleName();

        mHandoverTimeMap = new ConcurrentHashMap<>();
        mPingPongTime = new ConcurrentHashMap<>();
        mRatMismatchTime = new ConcurrentHashMap<>();
        mRtpCallDrop = new ConcurrentHashMap<>();

        mHandlerThread = new HandlerThread(QnsMetrics.class.getSimpleName());
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper());
    }

    @VisibleForTesting
    QnsMetrics(StatsLib statsLib) {
        mStats = statsLib;
        mLogTag = QnsMetrics.class.getSimpleName();

        mHandoverTimeMap = new ConcurrentHashMap<>();
        mPingPongTime = new ConcurrentHashMap<>();
        mRatMismatchTime = new ConcurrentHashMap<>();
        mRtpCallDrop = new ConcurrentHashMap<>();

        mHandlerThread = new HandlerThread(QnsMetrics.class.getSimpleName());
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper());
    }

    @VisibleForTesting
    Handler getHandler() {
        return mHandler;
    }

    /** close */
    public void close() {
        mStats = null;
        mHandlerThread.quitSafely();
    }

    /**
     * Report atoms when the qualified access network is reported.
     *
     * @param info QualifiedNetworksInfo
     * @param slotId slot index
     * @param dataConnectionCurrentTransportType transportType currently stayed in.
     * @param coverage coverage home or roam.
     * @param settingWfcEnabled setting for wfc
     * @param settingWfcRoamingEnabled roaming setting for wfc
     * @param settingWfcMode setting for wfc mode
     * @param settingWfcRoamingMode roaming setting for wfc mode
     * @param cellularAccessNetworkType cellular rat
     * @param iwlanAvailable iwlan available
     * @param isCrossWfc cross sim wfc enabled
     * @param restrictManager restriction manager
     * @param cellularQualityMonitor cellular quality monitor
     * @param wifiQualityMonitor wifi quality monitor
     * @param callType call type
     */
    public void reportAtomForQualifiedNetworks(
            QualifiedNetworksInfo info,
            int slotId,
            int dataConnectionCurrentTransportType,
            int coverage,
            boolean settingWfcEnabled,
            boolean settingWfcRoamingEnabled,
            int settingWfcMode,
            int settingWfcRoamingMode,
            int cellularAccessNetworkType,
            boolean iwlanAvailable,
            boolean isCrossWfc,
            RestrictManager restrictManager,
            QualityMonitor cellularQualityMonitor,
            QualityMonitor wifiQualityMonitor,
            int callType) {
        mHandler.post(() -> procQualifiedNetworksForHandoverTime(info, slotId));
        mHandler.post(() ->
                procQualifiedRatListChanged(
                        info,
                        slotId,
                        dataConnectionCurrentTransportType,
                        coverage,
                        settingWfcEnabled,
                        settingWfcRoamingEnabled,
                        settingWfcMode,
                        settingWfcRoamingMode,
                        cellularAccessNetworkType,
                        iwlanAvailable,
                        isCrossWfc,
                        restrictManager,
                        cellularQualityMonitor,
                        wifiQualityMonitor,
                        callType));
    }

    /**
     * Report atom when data connection is changed
     *
     * @param netCapability Network Capability
     * @param slotId slot Index
     * @param info DataConnectionChangedInfo
     * @param carrierId carrier id.
     */
    public void reportAtomForDataConnectionChanged(
            int netCapability, int slotId, DataConnectionChangedInfo info, int carrierId) {
        mHandler.post(() -> procDataConnectionChangedForHandoverTime(netCapability, slotId, info));
        mHandler.post(() -> procDataConnectionChangedForHandoverPingPong(
                netCapability, slotId, info, carrierId));
        mHandler.post(() -> procDataConnectionChangedForRatMismatch(
                netCapability, slotId, info, carrierId));
    }

    /**
     * Report atom when a restriction is set.
     *
     * @param netCapability Network Capability
     * @param slotId slot Index
     * @param wlanRestrictions list of restrictions on wlan
     * @param wwanRestrictions list of restrictions on wwan
     * @param carrierId carrier id.
     */
    public void reportAtomForRestrictions(
            int netCapability,
            int slotId,
            List<Integer> wlanRestrictions,
            List<Integer> wwanRestrictions,
            int carrierId) {
        mHandler.post(() -> procRestrictionsForFallback(
                netCapability, slotId, wlanRestrictions, wwanRestrictions, carrierId));
    }

    /**
     * Report atom when call type change
     *
     * @param netCapability Network Capability
     * @param slotId slot Index
     * @param oldCallType previous call type
     * @param newCallType new call type
     * @param restrictManager restriction manager
     * @param transportTypeOfCall transport type in call
     */
    public void reportAtomForCallTypeChanged(
            int netCapability,
            int slotId,
            int oldCallType,
            int newCallType,
            RestrictManager restrictManager,
            int transportTypeOfCall) {
        mHandler.post(() -> procCallTypeChangedForImsCallDrop(netCapability, slotId,
                oldCallType, newCallType, restrictManager, transportTypeOfCall));
    }

    /**
     * Report atom when ims call is dropped
     *
     * @param netCapability Network Capability
     * @param slotId slot Index
     * @param restrictManager restriction manager
     * @param cellularQualityMonitor cellular quality monitor
     * @param wifiQualityMonitor wifi quality monitor
     * @param transportTypeOfCall transport type in call
     * @param cellularAccessNetworkType cellular access network
     */
    public void reportAtomForImsCallDropStats(
            int netCapability,
            int slotId,
            RestrictManager restrictManager,
            QualityMonitor cellularQualityMonitor,
            QualityMonitor wifiQualityMonitor,
            int transportTypeOfCall,
            int cellularAccessNetworkType) {
        mHandler.post(() -> procCallDroppedForImsCallDrop(netCapability, slotId, restrictManager,
                cellularQualityMonitor, wifiQualityMonitor, transportTypeOfCall,
                cellularAccessNetworkType));
    }

    private void procQualifiedRatListChanged(
            QualifiedNetworksInfo info,
            int slotId,
            int dataConnectedTransportType,
            int coverage,
            boolean settingWfcEnabled,
            boolean settingWfcRoamingEnabled,
            int settingWfcMode,
            int settingWfcRoamingMode,
            int cellularAccessNetworkType,
            boolean iwlanAvailable,
            boolean isCrossWfc,
            RestrictManager restrictManager,
            QualityMonitor cellularQualityMonitor,
            QualityMonitor wifiQualityMonitor,
            int callType) {
        int netCapability = info.getNetCapability();
        int firstQualifiedRat = getQualifiedAccessNetwork(info, 0);
        int secondQualifiedRat = getQualifiedAccessNetwork(info, 1);
        boolean wfcEnabled = getWfcEnabled(coverage, settingWfcEnabled, settingWfcRoamingEnabled);
        int wfcMode = getWfcMode(coverage, settingWfcMode, settingWfcRoamingMode);
        int iwlanNetworkType = getIwlanNetworkType(iwlanAvailable, isCrossWfc);
        int restrictionsOnWwan = getRestrictionsBitmask(
                AccessNetworkConstants.TRANSPORT_TYPE_WWAN, restrictManager);
        int restrictionsOnWlan = getRestrictionsBitmask(
                AccessNetworkConstants.TRANSPORT_TYPE_WLAN, restrictManager);
        int signalStrength = getSignalStrength(cellularQualityMonitor, cellularAccessNetworkType);
        int signalQuality = getSignalQuality(cellularQualityMonitor, cellularAccessNetworkType);
        int signalNoise = getSignalNoise(cellularQualityMonitor, cellularAccessNetworkType);
        int iwlanSignalStrength = getSignalStrength(wifiQualityMonitor, AccessNetworkType.IWLAN);
        int updateReason = 0;
        int imsCallQuality = 0;

        writeQualifiedRatListChangedInfo(
                netCapability,
                slotId,
                firstQualifiedRat,
                secondQualifiedRat,
                dataConnectedTransportType,
                wfcEnabled,
                wfcMode,
                cellularAccessNetworkType,
                iwlanNetworkType,
                restrictionsOnWwan,
                restrictionsOnWlan,
                signalStrength,
                signalQuality,
                signalNoise,
                iwlanSignalStrength,
                updateReason,
                callType,
                imsCallQuality);
    }


    private void procQualifiedNetworksForHandoverTime(QualifiedNetworksInfo info, int slotId) {
        if (info.getNetCapability() != NetworkCapabilities.NET_CAPABILITY_IMS) {
            return;
        }

        HandoverTimeStateMachine handoverTimeStateMachine = mHandoverTimeMap.get(slotId);
        if (handoverTimeStateMachine == null) {
            handoverTimeStateMachine =
                    new HandoverTimeStateMachine(info.getNetCapability(), slotId, mHandler);
            mHandoverTimeMap.put(slotId, handoverTimeStateMachine);
        }

        handoverTimeStateMachine.sendQualifiedRatChanged(info);
    }

    private void procDataConnectionChangedForHandoverTime(
            int netCapability, int slotId, DataConnectionChangedInfo info) {
        if (netCapability != NetworkCapabilities.NET_CAPABILITY_IMS) {
            return;
        }

        HandoverTimeStateMachine handoverTimeStateMachine = mHandoverTimeMap.get(slotId);
        if (handoverTimeStateMachine == null) {
            handoverTimeStateMachine =
                    new HandoverTimeStateMachine(netCapability, slotId, mHandler);
            mHandoverTimeMap.put(slotId, handoverTimeStateMachine);
        }

        handoverTimeStateMachine.sendDataStateChanged(info);
    }

    private void procDataConnectionChangedForHandoverPingPong(int netCapability, int slotId,
            DataConnectionChangedInfo info, int carrierId) {
        if (netCapability != NetworkCapabilities.NET_CAPABILITY_IMS) {
            return;
        }

        PingPongTime pingPongTime = mPingPongTime.get(slotId);
        if (pingPongTime == null) {
            pingPongTime = new PingPongTime();
            mPingPongTime.put(slotId, pingPongTime);
        }

        boolean bActiveState;
        switch (info.getState()) {
            case DataConnectionStatusTracker.STATE_INACTIVE:
            case DataConnectionStatusTracker.STATE_CONNECTING:
                bActiveState = false;
                break;
            case DataConnectionStatusTracker.STATE_CONNECTED:
            case DataConnectionStatusTracker.STATE_HANDOVER:
            default:
                bActiveState = true;
                break;
        }
        switch (info.getEvent()) {
            case DataConnectionStatusTracker.EVENT_DATA_CONNECTION_CONNECTED:
                pingPongTime.mSuccessTime = QnsUtils.getSystemElapsedRealTime();
                pingPongTime.mHandoverCount = 0;
                break;
            case DataConnectionStatusTracker.EVENT_DATA_CONNECTION_HANDOVER_STARTED:
                pingPongTime.mStartTime = QnsUtils.getSystemElapsedRealTime();
                break;
            case DataConnectionStatusTracker.EVENT_DATA_CONNECTION_HANDOVER_SUCCESS:
                pingPongTime.mHandoverCount++;
                pingPongTime.mSuccessTime = QnsUtils.getSystemElapsedRealTime();
                break;
        }

        if (pingPongTime.mStartTime != 0L && pingPongTime.mSuccessTime != 0L) {
            long pingPongTimeLimit = AtomsQnsHandoverPingPongInfo.PING_PONG_TIME_IN_MILLIS;
            long elapsed =
                    (pingPongTime.mSuccessTime > pingPongTime.mStartTime)
                            ? (pingPongTime.mSuccessTime - pingPongTime.mStartTime)
                            : (pingPongTime.mStartTime - pingPongTime.mSuccessTime);
            if (pingPongTime.mHandoverCount > 0) {
                log("HandoverPingPong elapsed:" + elapsed
                        + " ping-pong count:" + (pingPongTime.mHandoverCount / 2));
            }
            if (elapsed > pingPongTimeLimit || !bActiveState) {
                if (pingPongTime.mHandoverCount > 1) {
                    int pingpongCount = pingPongTime.mHandoverCount / 2;
                    writeQnsHandoverPingPong(slotId, pingpongCount, carrierId);
                }
                pingPongTime.mHandoverCount = 0;
                pingPongTime.mStartTime = 0L;
                pingPongTime.mSuccessTime = 0L;
                procDataConnectionChangedForHandoverPingPong(
                        netCapability, slotId, info, carrierId);
            }
        }
        if (!bActiveState) {
            pingPongTime.mHandoverCount = 0;
            pingPongTime.mStartTime = 0L;
            pingPongTime.mSuccessTime = 0L;
        }
    }

    private void procRestrictionsForFallback(int netCapability, int slotId,
            List<Integer> wlanRestrictions, List<Integer> wwanRestrictions, int carrierId) {
        if (netCapability != NetworkCapabilities.NET_CAPABILITY_IMS
                || wlanRestrictions == null
                || wwanRestrictions == null) {
            return;
        }

        boolean bRestrictionOnWlanByRtpThresholdBreached =
                wlanRestrictions.contains(RestrictManager.RESTRICT_TYPE_RTP_LOW_QUALITY);
        boolean bRestrictionOnWwanByRtpThresholdBreached =
                wwanRestrictions.contains(RestrictManager.RESTRICT_TYPE_RTP_LOW_QUALITY);
        boolean bRestrictionOnWlanByImsRegistrationFailed =
                wlanRestrictions.contains(
                        RestrictManager.RESTRICT_TYPE_FALLBACK_TO_WWAN_IMS_REGI_FAIL);
        boolean bRestrictionOnWlanByWifiBackhaulProblem =
                wlanRestrictions.contains(
                        RestrictManager.RESTRICT_TYPE_FALLBACK_TO_WWAN_RTT_BACKHAUL_FAIL);

        // all false will not write atom because this atom is used for count metric.
        if (bRestrictionOnWlanByRtpThresholdBreached
                || bRestrictionOnWwanByRtpThresholdBreached
                || bRestrictionOnWlanByImsRegistrationFailed
                || bRestrictionOnWlanByWifiBackhaulProblem) {
            writeQnsFallbackRestrictionChangedInfo(
                    bRestrictionOnWlanByRtpThresholdBreached,
                    bRestrictionOnWwanByRtpThresholdBreached,
                    bRestrictionOnWlanByImsRegistrationFailed,
                    bRestrictionOnWlanByWifiBackhaulProblem,
                    carrierId,
                    slotId);
        }
    }

    private void procDataConnectionChangedForRatMismatch(
            int netCapability, int slotId, DataConnectionChangedInfo info, int carrierId) {

        RatMismatchTime mismatchTime = mRatMismatchTime.get(slotId);
        if (mismatchTime == null) {
            mismatchTime = new RatMismatchTime();
            mRatMismatchTime.put(slotId, mismatchTime);
        }

        switch (info.getEvent()) {
            case DataConnectionStatusTracker.EVENT_DATA_CONNECTION_HANDOVER_STARTED:
                if (mismatchTime.mCount == 0) {
                    mismatchTime.mStartTime = QnsUtils.getSystemElapsedRealTime();
                }
                break;
            case DataConnectionStatusTracker.EVENT_DATA_CONNECTION_HANDOVER_FAILED:
                mismatchTime.mCount++;
                break;
            default:
                if (mismatchTime.mCount > 0) {
                    long duration = QnsUtils.getSystemElapsedRealTime() - mismatchTime.mStartTime;
                    int count = mismatchTime.mCount;

                    writeQnsRatPreferenceMismatchInfo(
                            netCapability, count, (int) duration, carrierId, slotId);
                    mismatchTime.mCount = 0;
                    mismatchTime.mStartTime = 0L;
                }
                break;
        }
    }

    private void writeQualifiedRatListChangedInfo(
            int netCapability,
            int slotId,
            int firstQualifiedRat,
            int secondQualifiedRat,
            int currentTransportType,
            boolean wfcEnabled,
            int wfcMode,
            int cellularNetworkType,
            int iwlanNetworkType,
            int restrictionsOnWwan,
            int restrictionsOnWlan,
            int signalStrength,
            int signalQuality,
            int signalNoise,
            int iwlanSignalStrength,
            int updateReason,
            int imsCallType,
            int imsCallQuality) {
        AtomsQualifiedRatListChangedInfo atoms =
                new AtomsQualifiedRatListChangedInfo(
                        netCapability,
                        firstQualifiedRat,
                        secondQualifiedRat,
                        currentTransportType,
                        wfcEnabled,
                        wfcMode,
                        cellularNetworkType,
                        iwlanNetworkType,
                        restrictionsOnWwan,
                        restrictionsOnWlan,
                        signalStrength,
                        signalQuality,
                        signalNoise,
                        iwlanSignalStrength,
                        updateReason,
                        imsCallType,
                        imsCallQuality,
                        slotId);
        mStats.write(atoms);
    }


    private void writeQnsHandoverTimeMillisInfo(long handoverTime, int slotIndex) {
        AtomsQnsHandoverTimeMillisInfo atoms =
                new AtomsQnsHandoverTimeMillisInfo((int) handoverTime, slotIndex);
        mStats.append(atoms);
    }

    private void writeQnsHandoverPingPong(int slotId, int pingPongCount, int carrierId) {
        AtomsQnsHandoverPingPongInfo atoms =
                new AtomsQnsHandoverPingPongInfo(pingPongCount, carrierId, slotId);
        mStats.append(atoms);
    }

    private void writeQnsFallbackRestrictionChangedInfo(
            boolean bRestrictionOnWlanByRtpThresholdBreached,
            boolean bRestrictionOnWwanByRtpThresholdBreached,
            boolean bRestrictionOnWlanByImsRegistrationFailed,
            boolean bRestrictionOnWlanByWifiBackhaulProblem,
            int carrierId,
            int slotId) {
        AtomsQnsFallbackRestrictionChangedInfo atom =
                new AtomsQnsFallbackRestrictionChangedInfo(
                        bRestrictionOnWlanByRtpThresholdBreached,
                        bRestrictionOnWwanByRtpThresholdBreached,
                        bRestrictionOnWlanByImsRegistrationFailed,
                        bRestrictionOnWlanByWifiBackhaulProblem,
                        carrierId,
                        slotId);
        mStats.write(atom);
    }

    private void writeQnsRatPreferenceMismatchInfo(int netCapability, int handoverFailCount,
            int durationMismatch, int carrierId, int slotId) {
        AtomsQnsRatPreferenceMismatchInfo atoms = new AtomsQnsRatPreferenceMismatchInfo(
                netCapability, handoverFailCount, durationMismatch, carrierId, slotId);
        mStats.append(atoms);
    }

    class HandoverTimeStateMachine extends StateMachine {

        private static final int EVENT_DATA_STATE_CHANGED = 0;
        private static final int EVENT_QUALIFIED_RAT_CHANGED = 1;

        private final IdleState mIdleState;
        private final ConnectedState mConnectedState;
        private final HandoverRequestedState mHandoverRequestedState;
        private final HandoverInProgressState mHandoverInProgressState;

        private final int mNetCapability;
        private final int mSlotId;
        int mDataTransportType;
        long mHandoverRequestedTime;

        HandoverTimeStateMachine(int netCapability, int slotId, Handler handler) {
            super(mLogTag + "_" + HandoverTimeStateMachine.class.getSimpleName() + "_" + slotId
                    + "_" + QnsUtils.getNameOfNetCapability(netCapability), handler);

            mIdleState = new IdleState();
            mConnectedState = new ConnectedState();
            mHandoverRequestedState = new HandoverRequestedState();
            mHandoverInProgressState = new HandoverInProgressState();

            mNetCapability = netCapability;
            mSlotId = slotId;
            mDataTransportType = AccessNetworkConstants.TRANSPORT_TYPE_INVALID;
            mHandoverRequestedTime = 0L;

            addState(mIdleState);
            addState(mConnectedState);
            addState(mHandoverRequestedState);
            addState(mHandoverInProgressState);
            setInitialState(mIdleState);
            start();
        }

        public void sendDataStateChanged(DataConnectionChangedInfo info) {
            sendMessage(EVENT_DATA_STATE_CHANGED, info);
        }

        public void sendQualifiedRatChanged(QualifiedNetworksInfo info) {
            sendMessage(EVENT_QUALIFIED_RAT_CHANGED, info);
        }

        private final class IdleState extends State {
            @Override
            public void enter() {
                log("IdleState");
                mDataTransportType = AccessNetworkConstants.TRANSPORT_TYPE_INVALID;
                mHandoverRequestedTime = 0L;
            }

            @Override
            public boolean processMessage(Message msg) {
                log("IdleState processMessage=" + msg.what);
                switch (msg.what) {
                    case EVENT_DATA_STATE_CHANGED:
                        DataConnectionChangedInfo dataInfo = (DataConnectionChangedInfo) msg.obj;
                        switch (dataInfo.getState()) {
                            case DataConnectionStatusTracker.STATE_CONNECTED:
                                mDataTransportType = dataInfo.getTransportType();
                                transitionTo(mConnectedState);
                                return HANDLED;
                            case DataConnectionStatusTracker.STATE_HANDOVER:
                                mDataTransportType = dataInfo.getTransportType();
                                transitionTo(mConnectedState);
                                break;
                        }
                        break;
                    case EVENT_QUALIFIED_RAT_CHANGED:
                        break;
                }
                return super.processMessage(msg);
            }

            @Override
            public void exit() {
                super.exit();
            }
        }

        private final class ConnectedState extends State {
            @Override
            public void enter() {
                log("ConnectedState");
                mHandoverRequestedTime = 0L;
            }

            @Override
            public boolean processMessage(Message msg) {
                log("ConnectedState processMessage=" + msg.what);
                switch (msg.what) {
                    case EVENT_DATA_STATE_CHANGED:
                        DataConnectionChangedInfo dataInfo = (DataConnectionChangedInfo) msg.obj;
                        switch (dataInfo.getState()) {
                            case DataConnectionStatusTracker.STATE_INACTIVE:
                            case DataConnectionStatusTracker.STATE_CONNECTING:
                                transitionTo(mIdleState);
                                break;
                            case DataConnectionStatusTracker.STATE_CONNECTED:
                            case DataConnectionStatusTracker.STATE_HANDOVER:
                                mDataTransportType = dataInfo.getTransportType();
                                return HANDLED;
                        }
                        break;
                    case EVENT_QUALIFIED_RAT_CHANGED:
                        QualifiedNetworksInfo qualifiedInfo = (QualifiedNetworksInfo) msg.obj;
                        // handover trigger
                        if (mNetCapability == qualifiedInfo.getNetCapability()
                                && qualifiedInfo.getAccessNetworkTypes().stream().anyMatch(
                                        accessNetwork -> QnsUtils.getTransportTypeFromAccessNetwork(
                                                accessNetwork) != mDataTransportType)) {
                            mHandoverRequestedTime = QnsUtils.getSystemElapsedRealTime();
                            transitionTo(mHandoverRequestedState);
                            return HANDLED;
                        }
                        break;
                }

                return super.processMessage(msg);
            }

            @Override
            public void exit() {
                super.exit();
            }
        }

        private final class HandoverRequestedState extends State {
            @Override
            public void enter() {
                log("HandoverRequestedState");
            }

            @Override
            public boolean processMessage(Message msg) {
                log("HandoverRequestedState processMessage=" + msg.what);
                switch (msg.what) {
                    case EVENT_DATA_STATE_CHANGED:
                        DataConnectionChangedInfo dataInfo = (DataConnectionChangedInfo) msg.obj;
                        switch (dataInfo.getState()) {
                            case DataConnectionStatusTracker.STATE_INACTIVE:
                            case DataConnectionStatusTracker.STATE_CONNECTING:
                                transitionTo(mIdleState);
                                break;
                            case DataConnectionStatusTracker.STATE_CONNECTED:
                                if (dataInfo.getTransportType() != mDataTransportType) {
                                    // back to connected state, already reached to target transport.
                                    mDataTransportType = dataInfo.getTransportType();
                                    transitionTo(mConnectedState);
                                }
                                break;
                            case DataConnectionStatusTracker.STATE_HANDOVER:
                                if (dataInfo.getTransportType() != mDataTransportType) {
                                    // back to connected state, already reached to target transport.
                                    mDataTransportType = dataInfo.getTransportType();
                                    transitionTo(mConnectedState);
                                }
                                transitionTo(mHandoverInProgressState);
                                break;
                        }
                        break;
                    case EVENT_QUALIFIED_RAT_CHANGED:
                        QualifiedNetworksInfo qualifiedInfo = (QualifiedNetworksInfo) msg.obj;
                        if (mNetCapability != qualifiedInfo.getNetCapability()) {
                            break;
                        }
                        if (qualifiedInfo.getAccessNetworkTypes().stream().noneMatch(
                                accessNetwork -> QnsUtils.getTransportTypeFromAccessNetwork(
                                        accessNetwork) != mDataTransportType)) {
                            // back to connected state. no handover target transport.
                            transitionTo(mConnectedState);
                            return HANDLED;
                        }
                        break;
                }
                return super.processMessage(msg);
            }

            @Override
            public void exit() {
                super.exit();
            }
        }

        private final class HandoverInProgressState extends State {
            @Override
            public void enter() {
                log("HandoverInProgressState");
            }

            @Override
            public boolean processMessage(Message msg) {
                log("HandoverInProgressState processMessage=" + msg.what);
                switch (msg.what) {
                    case EVENT_DATA_STATE_CHANGED:
                        DataConnectionChangedInfo dataInfo = (DataConnectionChangedInfo) msg.obj;
                        switch (dataInfo.getState()) {
                            case DataConnectionStatusTracker.STATE_INACTIVE:
                            case DataConnectionStatusTracker.STATE_CONNECTING:
                                transitionTo(mIdleState);
                                break;
                            case DataConnectionStatusTracker.STATE_CONNECTED:
                                if (dataInfo.getTransportType() != mDataTransportType) {
                                    if (mHandoverRequestedTime == 0L) {
                                        break;
                                    }
                                    // handover done.
                                    long handoverTime = QnsUtils.getSystemElapsedRealTime()
                                            - mHandoverRequestedTime;
                                    writeQnsHandoverTimeMillisInfo(handoverTime, mSlotId);
                                    mDataTransportType = dataInfo.getTransportType();
                                    mHandoverRequestedTime = 0L;
                                    transitionTo(mConnectedState);
                                } else {
                                    // handover didn't have done yet.
                                    transitionTo(mHandoverRequestedState);
                                }
                                break;
                            case DataConnectionStatusTracker.STATE_HANDOVER:
                                if (dataInfo.getTransportType() != mDataTransportType) {
                                    // back to connected state, already reached to target transport.
                                    mDataTransportType = dataInfo.getTransportType();
                                    transitionTo(mConnectedState);
                                }
                                break;
                        }
                        break;
                    case EVENT_QUALIFIED_RAT_CHANGED:
                        QualifiedNetworksInfo qualifiedInfo = (QualifiedNetworksInfo) msg.obj;
                        if (mNetCapability == qualifiedInfo.getNetCapability()
                                && qualifiedInfo.getAccessNetworkTypes().stream().noneMatch(
                                        accessNetwork -> QnsUtils.getTransportTypeFromAccessNetwork(
                                                accessNetwork) != mDataTransportType)) {
                            // back to connected state. no handover request
                            transitionTo(mConnectedState);
                            return HANDLED;
                        }
                        break;
                }
                return super.processMessage(msg);
            }

            @Override
            public void exit() {
                super.exit();
            }
        }
    }

    private static class PingPongTime {
        int mHandoverCount = 0;
        long mStartTime = 0L;
        long mSuccessTime = 0L;
    }

    private static class RatMismatchTime {
        int mCount = 0;
        long mStartTime = 0L;
    }

    private static class RtpCallDrop {
        boolean mRtpThresholdBreached;
        int mRestrictionsOnOtherTransportType;
    }


    private void procCallTypeChangedForImsCallDrop(
            int netCapability,
            int slotId,
            int oldCallType,
            int newCallType,
            RestrictManager restrictManager,
            int transportTypeOfCall) {
        if (netCapability != NetworkCapabilities.NET_CAPABILITY_IMS) {
            return;
        }

        RtpCallDrop rtpCallDrop = mRtpCallDrop.get(slotId);
        if (rtpCallDrop == null) {
            rtpCallDrop = new RtpCallDrop();
            mRtpCallDrop.put(slotId, rtpCallDrop);
        }

        // call ended
        if (newCallType == QnsConstants.CALL_TYPE_IDLE
                && oldCallType != QnsConstants.CALL_TYPE_IDLE) {
            rtpCallDrop.mRtpThresholdBreached =
                    restrictManager.hasRestrictionType(
                            transportTypeOfCall, RestrictManager.RESTRICT_TYPE_RTP_LOW_QUALITY);
            int otherTransportType =
                    transportTypeOfCall == AccessNetworkConstants.TRANSPORT_TYPE_WLAN
                            ? AccessNetworkConstants.TRANSPORT_TYPE_WWAN
                            : AccessNetworkConstants.TRANSPORT_TYPE_WLAN;
            rtpCallDrop.mRestrictionsOnOtherTransportType =
                    getRestrictionsBitmask(otherTransportType, restrictManager);
        } else {
            rtpCallDrop.mRtpThresholdBreached = false;
            rtpCallDrop.mRestrictionsOnOtherTransportType = 0;
        }
    }

    private void procCallDroppedForImsCallDrop(
            int netCapability,
            int slotId,
            RestrictManager restrictManager,
            QualityMonitor cellularQualityMonitor,
            QualityMonitor wifiQualityMonitor,
            int transportTypeOfCall,
            int cellularAccessNetworkType) {
        if (netCapability != NetworkCapabilities.NET_CAPABILITY_IMS) {
            return;
        }

        RtpCallDrop rtpCallDrop = mRtpCallDrop.get(slotId);
        if (rtpCallDrop == null) {
            rtpCallDrop = new RtpCallDrop();
            mRtpCallDrop.put(slotId, rtpCallDrop);
        }

        if (!rtpCallDrop.mRtpThresholdBreached
                || rtpCallDrop.mRestrictionsOnOtherTransportType == 0) {
            rtpCallDrop.mRtpThresholdBreached = restrictManager.hasRestrictionType(
                            transportTypeOfCall, RestrictManager.RESTRICT_TYPE_RTP_LOW_QUALITY);
            int otherTransportType =
                    transportTypeOfCall == AccessNetworkConstants.TRANSPORT_TYPE_WLAN
                            ? AccessNetworkConstants.TRANSPORT_TYPE_WWAN
                            : AccessNetworkConstants.TRANSPORT_TYPE_WLAN;
            rtpCallDrop.mRestrictionsOnOtherTransportType =
                    getRestrictionsBitmask(otherTransportType, restrictManager);
        }

        int signalStrength = getSignalStrength(cellularQualityMonitor, cellularAccessNetworkType);
        int signalQuality = getSignalQuality(cellularQualityMonitor, cellularAccessNetworkType);
        int signalNoise = getSignalNoise(cellularQualityMonitor, cellularAccessNetworkType);
        int iwlanSignalStrength = getSignalStrength(wifiQualityMonitor, AccessNetworkType.IWLAN);

        writeQnsImsCallDropStats(
                transportTypeOfCall,
                rtpCallDrop.mRtpThresholdBreached,
                rtpCallDrop.mRestrictionsOnOtherTransportType,
                signalStrength,
                signalQuality,
                signalNoise,
                iwlanSignalStrength,
                cellularAccessNetworkType,
                slotId);
    }

    private void writeQnsImsCallDropStats(
            int transportTypeCallDropped,
            boolean rtpThresholdBreached,
            int restrictionsOnOtherTransportType,
            int signalStrength,
            int signalQuality,
            int signalNoise,
            int iwlanSignalStrength,
            int cellularNetworkType,
            int slotId) {
        AtomsQnsImsCallDropStats atoms =
                new AtomsQnsImsCallDropStats(
                        transportTypeCallDropped,
                        rtpThresholdBreached,
                        restrictionsOnOtherTransportType,
                        signalStrength,
                        signalQuality,
                        signalNoise,
                        iwlanSignalStrength,
                        slotId,
                        cellularNetworkType);
        mStats.write(atoms);
    }

    private int getQualifiedAccessNetwork(QualifiedNetworksInfo info, int index) {
        List<Integer> types = info.getAccessNetworkTypes();
        if (types == null || index >= types.size()) {
            return QnsProtoEnums.EMPTY;
        }
        return types.get(index);
    }

    private boolean getWfcEnabled(int coverage, boolean wfcEnabled, boolean wfcRoamingEnabled) {
        return coverage == QnsConstants.COVERAGE_HOME ? wfcEnabled : wfcRoamingEnabled;
    }

    private int getWfcMode(int coverage, int wfcMode, int wfcRoamingMode) {
        return coverage == QnsConstants.COVERAGE_HOME ? wfcMode : wfcRoamingMode;
    }

    private int getIwlanNetworkType(boolean iwlanAvailable, boolean isCrossWfc) {
        if (!iwlanAvailable) {
            return QnsProtoEnums.IWLAN_NETWORK_TYPE_NONE;
        } else if (isCrossWfc) {
            return QnsProtoEnums.IWLAN_NETWORK_TYPE_CST;
        }
        return QnsProtoEnums.IWLAN_NETWORK_TYPE_WIFI;
    }

    static final HashMap<Integer, Integer> sAtomRestrictionsMap;

    static {
        sAtomRestrictionsMap = new HashMap<>();
        sAtomRestrictionsMap.put(
                RestrictManager.RESTRICT_TYPE_GUARDING, QnsProtoEnums.RESTRICT_TYPE_GUARDING);
        sAtomRestrictionsMap.put(
                RestrictManager.RESTRICT_TYPE_THROTTLING, QnsProtoEnums.RESTRICT_TYPE_THROTTLING);
        sAtomRestrictionsMap.put(
                RestrictManager.RESTRICT_TYPE_HO_NOT_ALLOWED,
                QnsProtoEnums.RESTRICT_TYPE_HO_NOT_ALLOWED);
        sAtomRestrictionsMap.put(
                RestrictManager.RESTRICT_TYPE_NON_PREFERRED_TRANSPORT,
                QnsProtoEnums.RESTRICT_TYPE_NON_PREFERRED_TRANSPORT);
        sAtomRestrictionsMap.put(
                RestrictManager.RESTRICT_TYPE_RTP_LOW_QUALITY,
                QnsProtoEnums.RESTRICT_TYPE_RTP_LOW_QUALITY);
        sAtomRestrictionsMap.put(
                RestrictManager.RESTRICT_TYPE_RESTRICT_IWLAN_IN_CALL,
                QnsProtoEnums.RESTRICT_TYPE_RESTRICT_IWLAN_IN_CALL);
        sAtomRestrictionsMap.put(
                RestrictManager.RESTRICT_TYPE_RESTRICT_IWLAN_CS_CALL,
                QnsProtoEnums.RESTRICT_TYPE_RESTRICT_IWLAN_CS_CALL);
        sAtomRestrictionsMap.put(
                RestrictManager.RESTRICT_TYPE_FALLBACK_TO_WWAN_IMS_REGI_FAIL,
                QnsProtoEnums.RESTRICT_TYPE_FALLBACK_TO_WWAN_IMS_REGI_FAIL);
        sAtomRestrictionsMap.put(
                RestrictManager.RESTRICT_TYPE_FALLBACK_ON_DATA_CONNECTION_FAIL,
                QnsProtoEnums.RESTRICT_TYPE_FALLBACK_ON_DATA_CONNECTION_FAIL);
        sAtomRestrictionsMap.put(
                RestrictManager.RESTRICT_TYPE_FALLBACK_TO_WWAN_RTT_BACKHAUL_FAIL,
                QnsProtoEnums.RESTRICT_TYPE_FALLBACK_TO_WWAN_RTT_BACKHAUL_FAIL);
    }

    private int getRestrictionsBitmask(int transportType, RestrictManager restrictManager) {
        int restrictions = QnsProtoEnums.RESTRICT_TYPE_NONE;
        for (int restrictionType : sAtomRestrictionsMap.keySet()) {
            if (restrictManager.hasRestrictionType(transportType, restrictionType)) {
                restrictions |= sAtomRestrictionsMap.get(restrictionType);
            }
        }
        return restrictions;
    }

    private int getSignalStrength(QualityMonitor qm, int accessNetworkType) {
        switch (accessNetworkType) {
            case AccessNetworkType.GERAN:
            case AccessNetworkType.IWLAN:
                return qm.getCurrentQuality(
                        accessNetworkType, SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSSI);
            case AccessNetworkType.UTRAN:
                return qm.getCurrentQuality(
                        accessNetworkType, SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSCP);
            case AccessNetworkType.EUTRAN:
                return qm.getCurrentQuality(
                        accessNetworkType, SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSRP);
            case AccessNetworkType.NGRAN:
                return qm.getCurrentQuality(
                        accessNetworkType, SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_SSRSRP);
        }
        return 0;
    }

    private int getSignalQuality(QualityMonitor qm, int accessNetworkType) {
        switch (accessNetworkType) {
            case AccessNetworkType.EUTRAN:
                return qm.getCurrentQuality(
                        accessNetworkType, SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSRQ);
            case AccessNetworkType.NGRAN:
                return qm.getCurrentQuality(
                        accessNetworkType, SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_SSRSRQ);
        }
        return 0;
    }

    private int getSignalNoise(QualityMonitor qm, int accessNetworkType) {
        switch (accessNetworkType) {
            case AccessNetworkType.EUTRAN:
                return qm.getCurrentQuality(
                        accessNetworkType, SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSSNR);
            case AccessNetworkType.NGRAN:
                return qm.getCurrentQuality(
                        accessNetworkType, SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_SSSINR);
        }
        return 0;
    }

    protected void log(String s) {
        Log.d(mLogTag, s);
    }
}
