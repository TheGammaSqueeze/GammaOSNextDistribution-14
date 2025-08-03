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

import static com.android.telephony.qns.QnsConstants.INVALID_ID;

import android.annotation.NonNull;
import android.net.NetworkCapabilities;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.telephony.AccessNetworkConstants;
import android.telephony.Annotation;
import android.telephony.CallQuality;
import android.telephony.CallState;
import android.telephony.PreciseCallState;
import android.telephony.PreciseDataConnectionState;
import android.telephony.TelephonyManager;
import android.telephony.ims.ImsCallProfile;
import android.telephony.ims.MediaQualityStatus;
import android.util.Log;
import android.util.SparseArray;

import com.android.internal.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Tracking IMS Call status and update call type changed event to ANE.
 */
public class QnsCallStatusTracker {
    private final String mLogTag;
    private QnsTelephonyListener mTelephonyListener;
    private QnsCarrierConfigManager mConfigManager;
    private List<CallState> mCallStates = new ArrayList<>();
    private QnsRegistrant mCallTypeChangedEventListener;
    private QnsRegistrant mEmergencyCallTypeChangedEventListener;
    private final QnsTimer mQnsTimer;
    private int mLastNormalCallType = QnsConstants.CALL_TYPE_IDLE;
    private int mLastEmergencyCallType = QnsConstants.CALL_TYPE_IDLE;
    private boolean mEmergencyOverIms;
    private ActiveCallTracker mActiveCallTracker;
    private Consumer<List<CallState>> mCallStatesConsumer =
            callStateList -> updateCallState(callStateList);
    private Consumer<Integer> mSrvccStateConsumer = state -> onSrvccStateChangedInternal(state);
    private Consumer<MediaQualityStatus> mMediaQualityStatusConsumer =
            status -> mActiveCallTracker.onMediaQualityStatusChanged(status);

    static class CallQualityBlock {
        int mUpLinkLevel;
        int mDownLinkLevel;
        long mCreatedElapsedTime;
        long mDurationMillis;
        CallQualityBlock(int uplinkLevel, int downLinkLevel, long createdElapsedTime) {
            mUpLinkLevel = uplinkLevel;
            mDownLinkLevel = downLinkLevel;
            mCreatedElapsedTime = createdElapsedTime;
        }

        long getUpLinkQualityVolume() {
            if (mDurationMillis > 0) {
                return mUpLinkLevel * mDurationMillis;
            } else {
                long now = QnsUtils.getSystemElapsedRealTime();
                return (now - mCreatedElapsedTime) * mUpLinkLevel;
            }
        }

        long getDownLinkQualityVolume() {
            if (mDurationMillis > 0) {
                return mDownLinkLevel * mDurationMillis;
            } else {
                long now = QnsUtils.getSystemElapsedRealTime();
                return (now - mCreatedElapsedTime) * mDownLinkLevel;
            }
        }
    }

    class ActiveCallTracker {
        private static final int EVENT_DATA_CONNECTION_STATUS_CHANGED = 3300;

        @QnsConstants.QnsCallType
        private int mCallType = QnsConstants.CALL_TYPE_IDLE;
        @Annotation.NetCapability
        private int mNetCapability = QnsConstants.INVALID_VALUE;
        private QnsRegistrantList mLowMediaQualityListeners = new QnsRegistrantList();
        private int mAccessNetwork = AccessNetworkConstants.AccessNetworkType.UNKNOWN;
        private int mTransportType = AccessNetworkConstants.TRANSPORT_TYPE_INVALID;
        private SparseArray<CallQuality> mCallQualities = new SparseArray();
        private TransportQuality mCurrentQuality;
        /** A list of TransportQuality for each Transport type */
        private SparseArray<List<TransportQuality>> mTransportQualityArray = new SparseArray<>();
        private boolean mWwanAvailable = false;
        private boolean mWlanAvailable = false;

        private boolean mMediaThresholdBreached = false;
        private HandlerThread mHandlerThread;
        private ActiveCallTrackerHandler mActiveCallHandler;
        private MediaLowQualityHandler mLowQualityHandler;
        private String mLogTag;

        private class ActiveCallTrackerHandler extends Handler {
            ActiveCallTrackerHandler(Looper l) {
                super(l);
            }

            @Override
            public void handleMessage(Message message) {
                QnsAsyncResult ar;
                int transportType;
                Log.d(mLogTag, "handleMessage : " + message.what);
                switch (message.what) {
                    case EVENT_DATA_CONNECTION_STATUS_CHANGED:
                        ar = (QnsAsyncResult) message.obj;
                        onDataConnectionStatusChanged(
                                (PreciseDataConnectionState) ar.mResult);
                        break;

                    default:
                        Log.d(mLogTag, "unHandleMessage : " + message.what);
                        break;
                }

            }
        }

        /** Tracking low quality status */
        private class MediaLowQualityHandler extends Handler {
            private static final int EVENT_MEDIA_QUALITY_CHANGED = 3401;
            private static final int EVENT_PACKET_LOSS_TIMER_EXPIRED = 3402;
            private static final int EVENT_HYSTERESIS_FOR_NORMAL_QUALITY = 3403;
            private static final int EVENT_POLLING_CHECK_LOW_QUALITY = 3404;

            private static final int STATE_NORMAL_QUALITY = 0;
            private static final int STATE_SUSPECT_LOW_QUALITY = 1;
            private static final int STATE_LOW_QUALITY = 2;

            private static final int HYSTERESIS_TIME_NORMAL_QUALITY_MILLIS = 3000;
            private static final int LOW_QUALITY_CHECK_INTERVAL_MILLIS = 15000;
            private static final int LOW_QUALITY_CHECK_AFTER_HO_MILLIS = 3000;
            private static final int LOW_QUALITY_REPORTED_TIME_INITIAL_VALUE = -1;

            private int mState = STATE_NORMAL_QUALITY;
            private int mPacketLossTimerId = INVALID_ID;
            private int mHysteresisTimerId = INVALID_ID;
            private int mPollingCheckTimerId = INVALID_ID;
            private MediaQualityStatus mMediaQualityStatus;
            private String mTag;

            MediaLowQualityHandler(Looper l) {
                super(l);
                mTag = mLogTag + "_LQH";
            }

            @Override
            public void handleMessage(Message message) {
                Log.d(mTag, "handleMessage : " + message.what);
                switch (message.what) {
                    case EVENT_MEDIA_QUALITY_CHANGED:
                        MediaQualityStatus status = (MediaQualityStatus) message.obj;
                        onMediaQualityChanged(status);
                        break;

                    case EVENT_PACKET_LOSS_TIMER_EXPIRED:
                        onPacketLossTimerExpired(message.arg1);
                        break;

                    case EVENT_HYSTERESIS_FOR_NORMAL_QUALITY:
                        exitLowQualityState();
                        break;

                    case EVENT_POLLING_CHECK_LOW_QUALITY:
                        checkLowQuality();
                        break;

                    default:
                        Log.d(mLogTag, "unHandleMessage : " + message.what);
                        break;
                }
            }

            private void onMediaQualityChanged(MediaQualityStatus status) {
                Log.d(mTag, "onMediaQualityChanged " + status);
                int reason = thresholdBreached(status);
                boolean needNotify = false;
                if (reason == 0) {
                    // Threshold not breached.
                    mMediaQualityStatus = status;
                    if (mState == STATE_NORMAL_QUALITY) {
                        Log.d(mTag, "keeps normal quality.");
                        mMediaQualityStatus = status;
                        return;
                    } else {
                        // check normal quality is stable or not.
                        mHysteresisTimerId = mQnsTimer.registerTimer(
                                Message.obtain(this, EVENT_HYSTERESIS_FOR_NORMAL_QUALITY),
                                HYSTERESIS_TIME_NORMAL_QUALITY_MILLIS);
                    }
                } else {
                    // Threshold breached.
                    mQnsTimer.unregisterTimer(mHysteresisTimerId);
                    mHysteresisTimerId = INVALID_ID;
                    switch (mState) {
                        case STATE_NORMAL_QUALITY:
                        case STATE_SUSPECT_LOW_QUALITY:
                            if (reason == (1 << QnsConstants.RTP_LOW_QUALITY_REASON_PACKET_LOSS)) {
                                int delayMillis = (mConfigManager.getRTPMetricsData()).mPktLossTime;
                                if (delayMillis > 0) {
                                    if (mState == STATE_NORMAL_QUALITY) {
                                        enterSuspectLowQualityState(delayMillis);
                                    }
                                } else if (delayMillis == 0) {
                                    needNotify = true;
                                }
                            } else {
                                mQnsTimer.unregisterTimer(mPacketLossTimerId);
                                mPacketLossTimerId = INVALID_ID;
                                enterLowQualityState(status);
                                needNotify = true;
                            }
                            break;

                        case STATE_LOW_QUALITY:
                            if (mMediaQualityStatus.getTransportType() == status.getTransportType()
                                    && thresholdBreached(mMediaQualityStatus)
                                    != thresholdBreached(status)) {
                                needNotify = true;
                            }
                            break;
                    }
                    mMediaQualityStatus = status;
                }
                if (needNotify) {
                    enterLowQualityState(status);
                    notifyLowMediaQuality(reason);
                }

            }

            @VisibleForTesting
            void enterLowQualityState(MediaQualityStatus status) {
                Log.d(mTag, "enterLowQualityState " + status);
                mState = STATE_LOW_QUALITY;
                mPollingCheckTimerId = mQnsTimer.registerTimer(
                        Message.obtain(this, EVENT_POLLING_CHECK_LOW_QUALITY),
                        LOW_QUALITY_CHECK_INTERVAL_MILLIS);
            }

            void enterSuspectLowQualityState(int delayMillis) {
                Log.d(mTag, "enterSuspectLowQualityState.");
                mQnsTimer.unregisterTimer(mPacketLossTimerId);
                Log.d(mTag, "Packet loss timer start. " + delayMillis);
                Message msg = this.obtainMessage(
                        EVENT_PACKET_LOSS_TIMER_EXPIRED, mTransportType, 0);
                mPacketLossTimerId = mQnsTimer.registerTimer(msg, delayMillis);
                mState = STATE_SUSPECT_LOW_QUALITY;
            }

            void exitLowQualityState() {
                mState = STATE_NORMAL_QUALITY;
                this.removeCallbacksAndMessages(null);
                mQnsTimer.unregisterTimer(mPacketLossTimerId);
                mQnsTimer.unregisterTimer(mHysteresisTimerId);
                mQnsTimer.unregisterTimer(mPollingCheckTimerId);
                mPacketLossTimerId = INVALID_ID;
                mHysteresisTimerId = INVALID_ID;
                mPollingCheckTimerId = INVALID_ID;
                notifyLowMediaQuality(0);
            }

            void checkLowQuality() {
                if (mState == STATE_NORMAL_QUALITY) {
                    Log.w(mTag, "checkLowQuality on unexpected state(normal state).");
                } else {
                    Log.d(mTag, "checkLowQuality");
                    int reason = thresholdBreached(mMediaQualityStatus);
                    if (reason > 0) {
                        notifyLowMediaQuality(thresholdBreached(mMediaQualityStatus));
                    } else if (mHysteresisTimerId != INVALID_ID) {
                        // hysteresis time to be normal state is running. let's check after that.
                        mPollingCheckTimerId = mQnsTimer.registerTimer(
                                Message.obtain(this, EVENT_POLLING_CHECK_LOW_QUALITY),
                                HYSTERESIS_TIME_NORMAL_QUALITY_MILLIS);
                    } else {
                        Log.w(mTag, "Unexpected case.");
                    }
                }
            }

            void updateForHandover(int transportType) {
                // restart timers that they need to be restarted on new transport type.
                if (mState == STATE_SUSPECT_LOW_QUALITY) {
                    mQnsTimer.unregisterTimer(mPacketLossTimerId);
                    Message msg = this.obtainMessage(
                            EVENT_PACKET_LOSS_TIMER_EXPIRED, transportType, 0);
                    mPacketLossTimerId = mQnsTimer.registerTimer(msg,
                            (mConfigManager.getRTPMetricsData()).mPktLossTime);
                }
                if (mHysteresisTimerId != INVALID_ID) {
                    mQnsTimer.unregisterTimer(mHysteresisTimerId);
                    mHysteresisTimerId = mQnsTimer.registerTimer(
                            Message.obtain(this, EVENT_HYSTERESIS_FOR_NORMAL_QUALITY),
                            HYSTERESIS_TIME_NORMAL_QUALITY_MILLIS);
                }
                if (mState == STATE_LOW_QUALITY) {
                    mQnsTimer.unregisterTimer(mPollingCheckTimerId);
                    mPollingCheckTimerId = mQnsTimer.registerTimer(
                            Message.obtain(this, EVENT_POLLING_CHECK_LOW_QUALITY),
                            LOW_QUALITY_CHECK_AFTER_HO_MILLIS);
                }
            }

            private void onPacketLossTimerExpired(int transportType) {
                if (mTransportType != transportType) {
                    Log.d(mTag, "onPacketLossTimerExpired transport type mismatched.");
                    if (mState == STATE_SUSPECT_LOW_QUALITY) {
                        mState = STATE_NORMAL_QUALITY;
                    }
                    return;
                }
                if (thresholdBreached(mMediaQualityStatus)
                        == (1 << QnsConstants.RTP_LOW_QUALITY_REASON_PACKET_LOSS)) {
                    enterLowQualityState(mMediaQualityStatus);
                    notifyLowMediaQuality(1 << QnsConstants.RTP_LOW_QUALITY_REASON_PACKET_LOSS);
                }
            }

            private void notifyLowMediaQuality(int reason) {
                long now = QnsUtils.getSystemElapsedRealTime();
                TransportQuality tq = getLastTransportQuality(mTransportType);
                if (tq != null) {
                    if (reason > 0) {
                        tq.mLowRtpQualityReportedTime = now;
                    } else {
                        tq.mLowRtpQualityReportedTime = LOW_QUALITY_REPORTED_TIME_INITIAL_VALUE;
                    }
                }
                Log.d(mTag, "notifyLowMediaQuality reason:" + reason + " transport type:"
                        + QnsConstants.transportTypeToString(mTransportType));
                mLowMediaQualityListeners.notifyResult(reason);
            }
        }

        class TransportQuality {
            int mTransportType = AccessNetworkConstants.TRANSPORT_TYPE_INVALID;
            long mLowRtpQualityReportedTime =
                    MediaLowQualityHandler.LOW_QUALITY_REPORTED_TIME_INITIAL_VALUE;
            List<CallQualityBlock> mCallQualityBlockList;

            TransportQuality(int transportType) {
                mTransportType = transportType;
                mCallQualityBlockList = new ArrayList<>();
            }

            boolean isLowRtpQualityReported() {
                return mLowRtpQualityReportedTime
                        != MediaLowQualityHandler.LOW_QUALITY_REPORTED_TIME_INITIAL_VALUE;
            }

            CallQualityBlock getLastCallQualityBlock() {
                int length = mCallQualityBlockList.size();
                if (length > 0) {
                    return mCallQualityBlockList.get(length - 1);
                } else {
                    return null;
                }
            }
        }

        ActiveCallTracker(int slotIndex, Looper looper) {
            mLogTag = ActiveCallTracker.class.getSimpleName() + "_" + slotIndex;
            if (looper == null) {
                mHandlerThread = new HandlerThread(ActiveCallTracker.class.getSimpleName());
                mHandlerThread.start();
                mActiveCallHandler = new ActiveCallTrackerHandler(mHandlerThread.getLooper());
                mLowQualityHandler = new MediaLowQualityHandler(mHandlerThread.getLooper());
            } else {
                mActiveCallHandler = new ActiveCallTrackerHandler(looper);
                mLowQualityHandler = new MediaLowQualityHandler(looper);
            }
            mTelephonyListener.addMediaQualityStatusCallback(mMediaQualityStatusConsumer);
            mTransportQualityArray.put(
                    AccessNetworkConstants.TRANSPORT_TYPE_WLAN, new ArrayList<>());
            mTransportQualityArray.put(
                    AccessNetworkConstants.TRANSPORT_TYPE_WWAN, new ArrayList<>());
        }

        void close() {
            mTelephonyListener.removeMediaQualityStatusCallback(mMediaQualityStatusConsumer);
            if (mNetCapability != QnsConstants.INVALID_VALUE) {
                mTelephonyListener.unregisterPreciseDataConnectionStateChanged(
                        mNetCapability, mActiveCallHandler);
                mNetCapability = QnsConstants.INVALID_VALUE;
            }
            if (mHandlerThread != null) {
                mHandlerThread.quitSafely();
            }
        }

        @VisibleForTesting
        void onDataConnectionStatusChanged(PreciseDataConnectionState state) {
            if (state == null) {
                Log.d(mLogTag, "onDataConnectionStatusChanged with null info");
                return;
            }
            if (state.getState() == TelephonyManager.DATA_CONNECTED) {
                int transportType = state.getTransportType();
                if (transportType == AccessNetworkConstants.TRANSPORT_TYPE_INVALID) {
                    Log.w(mLogTag, "Unexpected transport type on connected DataNetwork.");
                    return;
                }
                if (mTransportType == AccessNetworkConstants.TRANSPORT_TYPE_INVALID) {
                    Log.d(mLogTag, "Call started with "
                            + QnsConstants.transportTypeToString(transportType));
                    mTransportType = transportType;
                    startTrackingTransportQuality(transportType);
                } else if (mTransportType != transportType) {
                    Log.d(mLogTag, "Call Handed over to "
                            + QnsConstants.transportTypeToString(transportType));
                    mTransportType = transportType;
                    onHandoverCompleted(transportType);
                }
            }
        }

        private void onHandoverCompleted(
                @AccessNetworkConstants.TransportType int dstTransportType) {
            long now = QnsUtils.getSystemElapsedRealTime();
            // complete to update TransportQuality for prev transport type
            CallQualityBlock last = null;
            int prevTransportType = QnsUtils.getOtherTransportType(dstTransportType);
            TransportQuality prev = getLastTransportQuality(prevTransportType);
            if (prev != null) {
                last = prev.getLastCallQualityBlock();
            }
            // add a new TransportQuality for new transport type
            mTransportQualityArray.get(dstTransportType)
                    .add(new TransportQuality(dstTransportType));
            TransportQuality current = getLastTransportQuality(dstTransportType);
            if (last != null) {
                last.mDurationMillis = now - last.mCreatedElapsedTime;
                current.mCallQualityBlockList
                        .add(new CallQualityBlock(last.mUpLinkLevel, last.mDownLinkLevel, now));
            }
            mLowQualityHandler.updateForHandover(dstTransportType);
        }

        private void startTrackingTransportQuality(int transportType) {
            mTransportQualityArray.get(AccessNetworkConstants.TRANSPORT_TYPE_WLAN).clear();
            mTransportQualityArray.get(AccessNetworkConstants.TRANSPORT_TYPE_WWAN).clear();
            mTransportQualityArray.get(transportType)
                    .add(new TransportQuality(transportType));
        }

        void callStarted(@QnsConstants.QnsCallType int callType, int netCapability) {
            if (mCallType != QnsConstants.CALL_TYPE_IDLE) {
                if (mCallType != callType) {
                    callTypeUpdated(callType);
                } else {
                    Log.w(mLogTag, "call type:" + callType + " already started.");
                }
            }
            Log.d(mLogTag, "callStarted callType: " + callType + " netCapa:"
                    + QnsUtils.getNameOfNetCapability(netCapability));
            mCallType = callType;
            mNetCapability = netCapability;
            //Transport type will be updated when EVENT_DATA_CONNECTION_STATUS_CHANGED occurs.
            PreciseDataConnectionState dataState =
                    mTelephonyListener.getLastPreciseDataConnectionState(netCapability);
            if (dataState != null && dataState.getTransportType()
                    != AccessNetworkConstants.TRANSPORT_TYPE_INVALID) {
                mTransportType = dataState.getTransportType();
                startTrackingTransportQuality(mTransportType);
            }
            mTelephonyListener.registerPreciseDataConnectionStateChanged(mNetCapability,
                    mActiveCallHandler, EVENT_DATA_CONNECTION_STATUS_CHANGED, null, true);
        }

        private void callTypeUpdated(@QnsConstants.QnsCallType int callType) {
            Log.d(mLogTag, "callTypeUpdated from " + mCallType + " to " + callType);
            mCallType = callType;
        }

        void callEnded() {
            mLowQualityHandler.exitLowQualityState();
            long now = QnsUtils.getSystemElapsedRealTime();
            // complete to update TransportQuality for prev transport type
            CallQualityBlock last = null;
            TransportQuality prev = getLastTransportQuality(mTransportType);
            if (prev != null) {
                last = prev.getLastCallQualityBlock();
            }
            if (last != null) {
                last.mDurationMillis = now - last.mCreatedElapsedTime;
            }
            long upLinkQualityOverWwan = mActiveCallTracker
                    .getUpLinkQualityLevelDuringCall(AccessNetworkConstants.TRANSPORT_TYPE_WWAN);
            long upLinkQualityOverWlan = mActiveCallTracker
                    .getUpLinkQualityLevelDuringCall(AccessNetworkConstants.TRANSPORT_TYPE_WLAN);
            long downLinkQualityOverWwan = mActiveCallTracker
                    .getDownLinkQualityLevelDuringCall(AccessNetworkConstants.TRANSPORT_TYPE_WWAN);
            long downLinkQualityOverWlan = mActiveCallTracker
                    .getDownLinkQualityLevelDuringCall(AccessNetworkConstants.TRANSPORT_TYPE_WLAN);
            StringBuilder sb = new StringBuilder();
            sb.append("CallQuality [WWAN:");
            if (upLinkQualityOverWwan == QnsConstants.INVALID_VALUE
                    || downLinkQualityOverWwan == QnsConstants.INVALID_VALUE) {
                sb.append("Not available] ");
            } else {
                sb.append("upLinkQualityOverWwan = ").append(upLinkQualityOverWwan)
                        .append(", downLinkQualityOverWwan = ").append(downLinkQualityOverWwan)
                        .append("] ");
            }
            sb.append("[WLAN:");
            if (upLinkQualityOverWlan == QnsConstants.INVALID_VALUE
                    || downLinkQualityOverWlan == QnsConstants.INVALID_VALUE) {
                sb.append("Not available] ");
            } else {
                sb.append("upLinkQualityOverWlan = ").append(upLinkQualityOverWwan)
                        .append(", downLinkQualityOverWlan = ").append(downLinkQualityOverWwan)
                        .append("] ");
            }
            Log.d(mLogTag, "callEnded callType: " + mCallType + " netCapa:"
                    + QnsUtils.getNameOfNetCapability(mNetCapability) + " " + sb.toString());
            mCallType = QnsConstants.CALL_TYPE_IDLE;
            mTelephonyListener.unregisterPreciseDataConnectionStateChanged(
                    mNetCapability, mActiveCallHandler);
            mNetCapability = QnsConstants.INVALID_VALUE;
            mAccessNetwork = AccessNetworkConstants.AccessNetworkType.UNKNOWN;
            mTransportType = AccessNetworkConstants.TRANSPORT_TYPE_INVALID;
        }

        void onMediaQualityStatusChanged(MediaQualityStatus status) {
            if (status == null) {
                Log.e(mLogTag, "null MediaQualityStatus received.");
                return;
            }
            Message msg = mLowQualityHandler
                    .obtainMessage(MediaLowQualityHandler.EVENT_MEDIA_QUALITY_CHANGED, status);
            mLowQualityHandler.sendMessage(msg);
        }

        int getTransportType() {
            return this.mTransportType;
        }

        int getCallType() {
            return this.mCallType;
        }

        int getNetCapability() {
            return this.mNetCapability;
        }

        @VisibleForTesting
        TransportQuality getLastTransportQuality(int transportType) {
            if (transportType == AccessNetworkConstants.TRANSPORT_TYPE_INVALID) {
                Log.w(mLogTag, "getLastTransportQuality with invalid transport type.");
                return null;
            }
            int size = mTransportQualityArray.get(transportType).size();
            if (size > 0) {
                return mTransportQualityArray.get(transportType).get(size - 1);
            } else {
                return null;
            }
        }

        @VisibleForTesting
        List<TransportQuality> getTransportQualityList(int transportType) {
            return mTransportQualityArray.get(transportType);
        }

        long getUpLinkQualityLevelDuringCall(int transportType) {
            List<TransportQuality> tqList = getTransportQualityList(transportType);
            long sumUplinkQualityLevelVolume = 0;
            long totalDuration = 0;
            for (int i = 0; i < tqList.size(); i++) {
                List<CallQualityBlock> callQualityBlockList = tqList.get(i).mCallQualityBlockList;
                for (int j = 0; j < callQualityBlockList.size(); j++) {
                    CallQualityBlock cq = callQualityBlockList.get(j);
                    sumUplinkQualityLevelVolume += cq.getUpLinkQualityVolume();
                    long durationMillis = cq.mDurationMillis;
                    if (i == tqList.size() - 1 && j == callQualityBlockList.size() - 1) {
                        if (durationMillis == 0) {
                            durationMillis = QnsUtils.getSystemElapsedRealTime()
                                    - cq.mCreatedElapsedTime;
                        }
                    }
                    if (durationMillis > 0) {
                        totalDuration += durationMillis;
                    } else {
                        return -1;
                    }
                }
            }
            if (totalDuration <= 0) {
                return QnsConstants.INVALID_VALUE;
            }
            long qualityLevel = sumUplinkQualityLevelVolume / totalDuration;
            Log.d(mLogTag, "getUplinkQualityLevel for [" + QnsConstants
                    .transportTypeToString(transportType) + "] totalQualityVolume: "
                    + sumUplinkQualityLevelVolume + ", totalDuration: " + totalDuration
                    + " level:" + qualityLevel);
            return qualityLevel;
        }

        long getDownLinkQualityLevelDuringCall(int transportType) {
            List<TransportQuality> tqList = getTransportQualityList(transportType);
            long sumDownLinkQualityLevelVolume = 0;
            long totalDuration = 0;
            for (int i = 0; i < tqList.size(); i++) {
                List<CallQualityBlock> callQualityBlockList = tqList.get(i).mCallQualityBlockList;
                for (int j = 0; j < callQualityBlockList.size(); j++) {
                    CallQualityBlock cq = callQualityBlockList.get(j);
                    sumDownLinkQualityLevelVolume += cq.getDownLinkQualityVolume();
                    long durationMillis = cq.mDurationMillis;
                    if (i == tqList.size() - 1 && j == callQualityBlockList.size() - 1) {
                        if (durationMillis == 0) {
                            durationMillis = QnsUtils.getSystemElapsedRealTime()
                                    - cq.mCreatedElapsedTime;
                        }
                    }
                    if (durationMillis > 0) {
                        totalDuration += durationMillis;
                    } else {
                        return QnsConstants.INVALID_VALUE;
                    }
                }
            }
            if (totalDuration <= 0) {
                return QnsConstants.INVALID_VALUE;
            }
            long qualityLevel = sumDownLinkQualityLevelVolume / totalDuration;
            Log.d(mLogTag, "getDownLinkQualityLevel for [" + QnsConstants
                    .transportTypeToString(transportType) + "] totalQualityVolume: "
                    + sumDownLinkQualityLevelVolume + ", totalDuration: " + totalDuration
                    + " level:" + qualityLevel);
            return qualityLevel;
        }

        void updateCallQuality(CallState state) {
            if (state == null) {
                Log.w(mLogTag, "updateCallQuality Null CallState.");
                return;
            }
            CallQuality cq = state.getCallQuality();
            if (cq == null || isDummyCallQuality(cq)) {
                return;
            }
            mActiveCallHandler.post(() -> onUpdateCallQuality(cq));
        }

        private void onUpdateCallQuality(CallQuality cq) {
            TransportQuality transportQuality = getLastTransportQuality(mTransportType);
            if (transportQuality != null) {
                long now = QnsUtils.getSystemElapsedRealTime();
                CallQualityBlock prev = transportQuality.getLastCallQualityBlock();
                if (prev != null) {
                    prev.mDurationMillis = now - prev.mCreatedElapsedTime;
                }
                transportQuality.mCallQualityBlockList.add(
                        new CallQualityBlock(
                                cq.getUplinkCallQualityLevel(), cq.getDownlinkCallQualityLevel(),
                                now));
            }
        }

        private boolean isDummyCallQuality(CallQuality cq) {
            return (cq.getNumRtpPacketsTransmitted() == 0
                    && cq.getNumRtpPacketsReceived() == 0
                    && cq.getUplinkCallQualityLevel() == 0
                    && cq.getDownlinkCallQualityLevel() == 0);
        }
        /**
         * Register an event for low media quality report.
         *
         * @param h the Handler to get event.
         * @param what the event.
         * @param userObj user object.
         */
        void registerLowMediaQualityListener(
                Handler h, int what, Object userObj) {
            Log.d(mLogTag, "registerLowMediaQualityListener");
            if (h != null) {
                QnsRegistrant r = new QnsRegistrant(h, what, userObj);
                mLowMediaQualityListeners.add(r);
            }
        }

        /**
         * Unregister an event for low media quality report.
         *
         * @param h the handler to get event.
         */
        void unregisterLowMediaQualityListener(Handler h) {
            if (h != null) {
                mLowMediaQualityListeners.remove(h);
            }
        }

        @VisibleForTesting
        int thresholdBreached(MediaQualityStatus status) {
            int breachedReason = 0;
            QnsCarrierConfigManager.RtpMetricsConfig rtpConfig = mConfigManager.getRTPMetricsData();
            if (status.getRtpPacketLossRate() > 0
                    && status.getRtpPacketLossRate() >= rtpConfig.mPktLossRate) {
                breachedReason |= 1 << QnsConstants.RTP_LOW_QUALITY_REASON_PACKET_LOSS;
            }
            if (status.getRtpJitterMillis() > 0
                    && status.getRtpJitterMillis() >= rtpConfig.mJitter) {
                breachedReason |= 1 << QnsConstants.RTP_LOW_QUALITY_REASON_JITTER;
            }
            if (status.getRtpInactivityMillis() > 0
                    && status.getRtpInactivityMillis() >= rtpConfig.mNoRtpInterval) {
                breachedReason |= 1 << QnsConstants.RTP_LOW_QUALITY_REASON_NO_RTP;
            }
            return breachedReason;
        }

        boolean worseThanBefore(MediaQualityStatus before, MediaQualityStatus now) {
            return thresholdBreached(now) > thresholdBreached(before);
        }
    }

    QnsCallStatusTracker(QnsTelephonyListener telephonyListener,
            QnsCarrierConfigManager configManager, QnsTimer qnsTimer, int slotIndex) {
        this(telephonyListener, configManager, qnsTimer, slotIndex, null);
    }

    /** Only for test */
    @VisibleForTesting
    QnsCallStatusTracker(QnsTelephonyListener telephonyListener,
            QnsCarrierConfigManager configManager, QnsTimer qnsTimer, int slotIndex,
            Looper looper) {
        mLogTag = QnsCallStatusTracker.class.getSimpleName() + "_" + slotIndex;
        mTelephonyListener = telephonyListener;
        mConfigManager = configManager;
        mQnsTimer = qnsTimer;
        mActiveCallTracker = new ActiveCallTracker(slotIndex, looper);
        mTelephonyListener.addCallStatesChangedCallback(mCallStatesConsumer);
        mTelephonyListener.addSrvccStateChangedCallback(mSrvccStateConsumer);
    }

    void close() {
        mTelephonyListener.removeCallStatesChangedCallback(mCallStatesConsumer);
        mTelephonyListener.removeSrvccStateChangedCallback(mSrvccStateConsumer);
        if (mActiveCallTracker != null) {
            mActiveCallTracker.close();
        }
    }

    void updateCallState(List<CallState> callStateList) {
        List<CallState> imsCallStateList = new ArrayList<>();
        StringBuilder sb = new StringBuilder("");

        if (callStateList.size() > 0) {
            for (CallState cs : callStateList) {
                if (cs.getImsCallServiceType() != ImsCallProfile.SERVICE_TYPE_NONE
                        || cs.getImsCallType() != ImsCallProfile.CALL_TYPE_NONE) {
                    if (cs.getCallState() != PreciseCallState.PRECISE_CALL_STATE_DISCONNECTED) {
                        imsCallStateList.add(cs);
                        sb.append("{" + cs + "}");
                    }
                }
            }
        }
        int ongoingCallNum = imsCallStateList.size();
        mCallStates = imsCallStateList;
        Log.d(mLogTag, "updateCallState callNum:(" + ongoingCallNum + "): [" + sb + "]");
        if (imsCallStateList.size() == 0) {
            if (mLastNormalCallType != QnsConstants.CALL_TYPE_IDLE) {
                mLastNormalCallType = QnsConstants.CALL_TYPE_IDLE;
                notifyCallType(NetworkCapabilities.NET_CAPABILITY_IMS, mLastNormalCallType);
            }
            if (mLastEmergencyCallType != QnsConstants.CALL_TYPE_IDLE) {
                mLastEmergencyCallType = QnsConstants.CALL_TYPE_IDLE;
                if (mEmergencyOverIms) {
                    mEmergencyOverIms = false;
                    notifyCallType(NetworkCapabilities.NET_CAPABILITY_IMS, mLastEmergencyCallType);
                } else {
                    notifyCallType(NetworkCapabilities.NET_CAPABILITY_EIMS, mLastEmergencyCallType);
                }
            }
        } else {
            //1. Notify Call Type IDLE, if the call was removed from the call list.
            if (mLastNormalCallType != QnsConstants.CALL_TYPE_IDLE
                    && !hasVideoCall() && !hasVoiceCall()) {
                mLastNormalCallType = QnsConstants.CALL_TYPE_IDLE;
                notifyCallType(NetworkCapabilities.NET_CAPABILITY_IMS, mLastNormalCallType);

            }
            if (mLastEmergencyCallType != QnsConstants.CALL_TYPE_IDLE && !hasEmergencyCall()) {
                mLastEmergencyCallType = QnsConstants.CALL_TYPE_IDLE;
                if (mEmergencyOverIms) {
                    mEmergencyOverIms = false;
                    notifyCallType(NetworkCapabilities.NET_CAPABILITY_IMS, mLastEmergencyCallType);
                } else {
                    notifyCallType(NetworkCapabilities.NET_CAPABILITY_EIMS, mLastEmergencyCallType);
                }
            }
            //2. Notify a new ongoing call type
            if (hasEmergencyCall()) {
                if (mLastEmergencyCallType != QnsConstants.CALL_TYPE_EMERGENCY) {
                    mLastEmergencyCallType = QnsConstants.CALL_TYPE_EMERGENCY;
                    if (!isDataNetworkConnected(NetworkCapabilities.NET_CAPABILITY_EIMS)
                            && isDataNetworkConnected(NetworkCapabilities.NET_CAPABILITY_IMS)) {
                        notifyCallType(NetworkCapabilities.NET_CAPABILITY_IMS,
                                mLastEmergencyCallType);
                        mEmergencyOverIms = true;
                    } else {
                        notifyCallType(NetworkCapabilities.NET_CAPABILITY_EIMS,
                                mLastEmergencyCallType);
                    }
                }
            } else if (hasVideoCall()) {
                if (mLastNormalCallType != QnsConstants.CALL_TYPE_VIDEO) {
                    mLastNormalCallType = QnsConstants.CALL_TYPE_VIDEO;
                    notifyCallType(NetworkCapabilities.NET_CAPABILITY_IMS, mLastNormalCallType);
                }
            } else if (hasVoiceCall()) {
                if (mLastNormalCallType != QnsConstants.CALL_TYPE_VOICE) {
                    mLastNormalCallType = QnsConstants.CALL_TYPE_VOICE;
                    notifyCallType(NetworkCapabilities.NET_CAPABILITY_IMS, mLastNormalCallType);
                }
            }
            if (mActiveCallTracker.getCallType() != QnsConstants.CALL_TYPE_IDLE) {
                mActiveCallTracker.updateCallQuality(getActiveCall());
            }
        }
    }

    private void notifyCallType(int netCapability, int callType) {
        Log.d(mLogTag, "notifyCallType for " + QnsUtils.getNameOfNetCapability(netCapability)
                + ", callType:" + callType);
        if (netCapability == NetworkCapabilities.NET_CAPABILITY_IMS
                && mCallTypeChangedEventListener != null) {
            mCallTypeChangedEventListener.notifyResult(callType);
        } else if (netCapability == NetworkCapabilities.NET_CAPABILITY_EIMS
                && mEmergencyCallTypeChangedEventListener != null) {
            mEmergencyCallTypeChangedEventListener.notifyResult(callType);
        }
        if (callType == QnsConstants.CALL_TYPE_IDLE) {
            mActiveCallTracker.callEnded();
        } else {
            mActiveCallTracker.callStarted(callType, netCapability);
        }
        mQnsTimer.updateCallState(callType);
    }

    boolean isCallIdle() {
        return mCallStates.size() == 0;
    }

    boolean isCallIdle(int netCapability) {
        int callNum = mCallStates.size();
        if (callNum == 0) {
            return true;
        }
        if (netCapability == NetworkCapabilities.NET_CAPABILITY_IMS) {
            return (mLastNormalCallType == QnsConstants.CALL_TYPE_IDLE)
                    && (mLastEmergencyCallType != QnsConstants.CALL_TYPE_IDLE
                    && !mEmergencyOverIms);
        } else if (netCapability == NetworkCapabilities.NET_CAPABILITY_EIMS) {
            return mLastEmergencyCallType == QnsConstants.CALL_TYPE_IDLE || mEmergencyOverIms;
        }
        return false;
    }

    boolean hasEmergencyCall() {
        for (CallState cs : mCallStates) {
            if (cs.getImsCallServiceType() == ImsCallProfile.SERVICE_TYPE_EMERGENCY
                    && cs.getCallState() == PreciseCallState.PRECISE_CALL_STATE_ACTIVE) {
                return true;
            }
        }
        return false;
    }

    CallState getActiveCall() {
        for (CallState cs : mCallStates) {
            if (cs.getCallState() == PreciseCallState.PRECISE_CALL_STATE_ACTIVE) {
                return cs;
            }
        }
        return null;
    }

    boolean hasVideoCall() {
        for (CallState cs : mCallStates) {
            if (cs.getImsCallServiceType() == ImsCallProfile.SERVICE_TYPE_NORMAL
                    && cs.getImsCallType() == ImsCallProfile.CALL_TYPE_VT
                    && (cs.getCallState() == PreciseCallState.PRECISE_CALL_STATE_DISCONNECTING
                            || cs.getCallState() == PreciseCallState.PRECISE_CALL_STATE_HOLDING
                            || cs.getCallState() == PreciseCallState.PRECISE_CALL_STATE_ACTIVE)) {
                return true;
            }
        }
        return false;
    }

    boolean hasVoiceCall() {
        for (CallState cs : mCallStates) {
            if (cs.getImsCallServiceType() == ImsCallProfile.SERVICE_TYPE_NORMAL
                    && cs.getImsCallType() == ImsCallProfile.CALL_TYPE_VOICE) {
                return true;
            }
        }
        return false;
    }

    /**
     * register call type changed event.
     *
     * @param netCapability Network Capability of caller
     * @param h Handler want to receive event.
     * @param what event Id to receive
     * @param userObj user object
     */
    void registerCallTypeChangedListener(
            int netCapability, @NonNull Handler h, int what, Object userObj) {
        if (netCapability != NetworkCapabilities.NET_CAPABILITY_IMS
                && netCapability != NetworkCapabilities.NET_CAPABILITY_EIMS) {
            Log.d(mLogTag, "registerCallTypeChangedListener : wrong netCapability");
            return;
        }
        if (h != null) {
            QnsRegistrant r = new QnsRegistrant(h, what, userObj);
            if (netCapability == NetworkCapabilities.NET_CAPABILITY_IMS) {
                mCallTypeChangedEventListener = r;
            } else if (netCapability == NetworkCapabilities.NET_CAPABILITY_EIMS) {
                mEmergencyCallTypeChangedEventListener = r;
            }
        } else {
            Log.d(mLogTag, "registerCallTypeChangedListener : Handler is Null");
        }
    }

    /**
     * Unregister call type changed event.
     *
     * @param netCapability Network Capability of caller
     * @param h Handler want to receive event.
     */
    void unregisterCallTypeChangedListener(int netCapability, @NonNull Handler h) {
        if (netCapability != NetworkCapabilities.NET_CAPABILITY_IMS
                && netCapability != NetworkCapabilities.NET_CAPABILITY_EIMS) {
            Log.d(mLogTag, "unregisterCallTypeChangedListener : wrong netCapability");
            return;
        }
        if (h != null) {
            if (netCapability == NetworkCapabilities.NET_CAPABILITY_IMS) {
                mCallTypeChangedEventListener = null;
            } else if (netCapability == NetworkCapabilities.NET_CAPABILITY_EIMS) {
                mEmergencyCallTypeChangedEventListener = null;
            }
        } else {
            Log.d(mLogTag, "unregisterCallTypeChangedListener : Handler is Null");
        }
    }

    ActiveCallTracker getActiveCallTracker() {
        return mActiveCallTracker;
    }

    @VisibleForTesting
    void onSrvccStateChangedInternal(int srvccState) {
        if (srvccState == TelephonyManager.SRVCC_STATE_HANDOVER_COMPLETED) {
            mCallStates.clear();
            if (mLastNormalCallType != QnsConstants.CALL_TYPE_IDLE) {
                mLastNormalCallType = QnsConstants.CALL_TYPE_IDLE;
                if (mCallTypeChangedEventListener != null) {
                    mCallTypeChangedEventListener.notifyResult(mLastNormalCallType);
                }
            }
            if (mLastEmergencyCallType != QnsConstants.CALL_TYPE_IDLE) {
                mLastEmergencyCallType = QnsConstants.CALL_TYPE_IDLE;
                if (mEmergencyOverIms) {
                    mEmergencyOverIms = false;
                    if (mCallTypeChangedEventListener != null) {
                        mCallTypeChangedEventListener.notifyResult(mLastEmergencyCallType);
                    }
                } else {
                    if (mEmergencyCallTypeChangedEventListener != null) {
                        mEmergencyCallTypeChangedEventListener.notifyResult(mLastEmergencyCallType);
                    }
                }
            }
        }
    }


    private boolean isDataNetworkConnected(int netCapability) {
        PreciseDataConnectionState preciseDataStatus =
                mTelephonyListener.getLastPreciseDataConnectionState(netCapability);

        if (preciseDataStatus == null) return false;
        int state = preciseDataStatus.getState();
        return (state == TelephonyManager.DATA_CONNECTED
                || state == TelephonyManager.DATA_HANDOVER_IN_PROGRESS
                || state == TelephonyManager.DATA_SUSPENDED);
    }
}
