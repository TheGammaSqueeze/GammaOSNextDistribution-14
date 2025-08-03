/*
 * Copyright (C) 2021 The Android Open Source Project
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

import static com.android.telephony.qns.DataConnectionStatusTracker.STATE_CONNECTED;
import static com.android.telephony.qns.DataConnectionStatusTracker.STATE_HANDOVER;
import static com.android.telephony.qns.QnsConstants.INVALID_ID;

import android.annotation.IntDef;
import android.net.NetworkCapabilities;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.telephony.AccessNetworkConstants;
import android.telephony.Annotation;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.util.Pair;

import com.android.internal.annotations.VisibleForTesting;
import com.android.telephony.qns.DataConnectionStatusTracker.DataConnectionChangedInfo;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Prevents HO pingpong between Cellular and IWLAN. Provide Throttling for certain cause. Provide
 * Handover not allowed policy.
 */
class RestrictManager {
    private final String mLogTag;
    private final boolean mDebugFlag = true;
    static final int RESTRICT_TYPE_GUARDING = 1;
    static final int RESTRICT_TYPE_THROTTLING = 2;
    static final int RESTRICT_TYPE_HO_NOT_ALLOWED = 3;
    static final int RESTRICT_TYPE_NON_PREFERRED_TRANSPORT = 4;
    static final int RESTRICT_TYPE_RTP_LOW_QUALITY = 5;
    static final int RESTRICT_TYPE_RESTRICT_IWLAN_IN_CALL = 6;
    static final int RESTRICT_TYPE_RESTRICT_IWLAN_CS_CALL = 7;
    static final int RESTRICT_TYPE_FALLBACK_TO_WWAN_IMS_REGI_FAIL = 8;
    static final int RESTRICT_TYPE_FALLBACK_ON_DATA_CONNECTION_FAIL = 9;
    static final int RESTRICT_TYPE_FALLBACK_TO_WWAN_RTT_BACKHAUL_FAIL = 10;

    @IntDef(
            value = {
                RESTRICT_TYPE_GUARDING,
                RESTRICT_TYPE_THROTTLING,
                RESTRICT_TYPE_HO_NOT_ALLOWED,
                RESTRICT_TYPE_NON_PREFERRED_TRANSPORT,
                RESTRICT_TYPE_RTP_LOW_QUALITY,
                RESTRICT_TYPE_RESTRICT_IWLAN_IN_CALL,
                RESTRICT_TYPE_RESTRICT_IWLAN_CS_CALL,
                RESTRICT_TYPE_FALLBACK_TO_WWAN_IMS_REGI_FAIL,
                RESTRICT_TYPE_FALLBACK_ON_DATA_CONNECTION_FAIL,
                RESTRICT_TYPE_FALLBACK_TO_WWAN_RTT_BACKHAUL_FAIL,
            })
    @interface RestrictType {}

    static final int RELEASE_EVENT_DISCONNECT = 1;
    static final int RELEASE_EVENT_WIFI_AP_CHANGED = 2;
    static final int RELEASE_EVENT_WFC_PREFER_MODE_CHANGED = 3;
    static final int RELEASE_EVENT_CALL_END = 4;
    static final int RELEASE_EVENT_IMS_NOT_SUPPORT_RAT = 5;

    @IntDef(
            value = {
                RELEASE_EVENT_DISCONNECT,
                RELEASE_EVENT_WIFI_AP_CHANGED,
                RELEASE_EVENT_WFC_PREFER_MODE_CHANGED,
                RELEASE_EVENT_CALL_END,
                RELEASE_EVENT_IMS_NOT_SUPPORT_RAT,
            })
    @interface ReleaseEvent {}

    private static final int EVENT_DATA_CONNECTION_CHANGED = 3001;
    private static final int EVENT_CALL_STATE_CHANGED = 3002;
    private static final int EVENT_SRVCC_STATE_CHANGED = 3003;
    private static final int EVENT_IMS_REGISTRATION_STATE_CHANGED = 3004;
    private static final int EVENT_LOW_RTP_QUALITY_REPORTED = 3006;
    private static final int EVENT_RELEASE_RESTRICTION = 3008;
    protected static final int EVENT_INITIAL_DATA_CONNECTION_FAIL_RETRY_TIMER_EXPIRED = 3009;
    private static final int EVENT_WIFI_RTT_BACKHAUL_CHECK_STATUS = 3010;

    @VisibleForTesting static final int GUARDING_TIMER_HANDOVER_INIT = 30000;

    static final HashMap<Integer, int[]> sReleaseEventMap =
            new HashMap<Integer, int[]>() {
                {
                    put(
                            RESTRICT_TYPE_GUARDING,
                            new int[] {
                                RELEASE_EVENT_DISCONNECT, RELEASE_EVENT_WFC_PREFER_MODE_CHANGED
                            });
                    put(
                            RESTRICT_TYPE_RTP_LOW_QUALITY,
                            new int[] {RELEASE_EVENT_CALL_END, RELEASE_EVENT_WIFI_AP_CHANGED});
                    put(RESTRICT_TYPE_RESTRICT_IWLAN_IN_CALL, new int[] {RELEASE_EVENT_CALL_END});
                    put(
                            RESTRICT_TYPE_FALLBACK_TO_WWAN_IMS_REGI_FAIL,
                            new int[] {
                                RELEASE_EVENT_DISCONNECT, RELEASE_EVENT_IMS_NOT_SUPPORT_RAT
                            });
                    put(
                            RESTRICT_TYPE_FALLBACK_ON_DATA_CONNECTION_FAIL,
                            new int[] {
                                RELEASE_EVENT_DISCONNECT,
                                RELEASE_EVENT_WIFI_AP_CHANGED,
                                RELEASE_EVENT_WFC_PREFER_MODE_CHANGED,
                                RELEASE_EVENT_IMS_NOT_SUPPORT_RAT
                            });
                    put(
                            RESTRICT_TYPE_FALLBACK_TO_WWAN_RTT_BACKHAUL_FAIL,
                            new int[] {
                                RELEASE_EVENT_DISCONNECT,
                                RELEASE_EVENT_WIFI_AP_CHANGED,
                                RELEASE_EVENT_IMS_NOT_SUPPORT_RAT
                            });
                }
            };
    private static final int[] ignorableRestrictionsOnSingleRat =
            new int[] {
                RESTRICT_TYPE_GUARDING,
                //Ignore throttling restriction at single RAT, let FWK control throttling.
                RESTRICT_TYPE_THROTTLING,
                RESTRICT_TYPE_RTP_LOW_QUALITY,
                RESTRICT_TYPE_RESTRICT_IWLAN_IN_CALL,
                RESTRICT_TYPE_FALLBACK_TO_WWAN_IMS_REGI_FAIL,
                RESTRICT_TYPE_FALLBACK_ON_DATA_CONNECTION_FAIL,
                RESTRICT_TYPE_FALLBACK_TO_WWAN_RTT_BACKHAUL_FAIL
            };

    private QnsCarrierConfigManager mQnsCarrierConfigManager;
    private QnsTelephonyListener mTelephonyListener;
    private QnsEventDispatcher mQnsEventDispatcher;
    @VisibleForTesting Handler mHandler;

    @QnsConstants.CellularCoverage
    int mCellularCoverage; // QnsConstants.COVERAGE_HOME or QnsConstants.COVERAGE_ROAM

    int mCellularAccessNetwork;

    @VisibleForTesting QnsRegistrant mRestrictInfoRegistrant;
    private DataConnectionStatusTracker mDataConnectionStatusTracker;
    private CellularNetworkStatusTracker mCellularNetworkStatusTracker;
    private QnsCallStatusTracker mQnsCallStatusTracker;
    private QnsCallStatusTracker.ActiveCallTracker mActiveCallTracker;
    private QnsImsManager mQnsImsManager;
    private QnsTimer mQnsTimer;
    private WifiBackhaulMonitor mWifiBackhaulMonitor;
    private QnsMetrics mQnsMetrics;
    private int mNetCapability;
    private int mSlotId;
    private int mTransportType = AccessNetworkConstants.TRANSPORT_TYPE_INVALID;
    private int mLastEvaluatedTransportType = AccessNetworkConstants.TRANSPORT_TYPE_INVALID;
    private int mWfcPreference;
    private int mWfcRoamingPreference;
    private int mCounterForIwlanRestrictionInCall;
    private int mRetryCounterOnDataConnectionFail;
    private int mFallbackCounterOnDataConnectionFail;
    private boolean mIsRttStatusCheckRegistered = false;
    private int mLastDataConnectionTransportType;
    private int mFallbackTimerId = -1;
    private boolean mIsTimerRunningOnDataConnectionFail = false;
    private Pair<Integer, Long> mDeferredThrottlingEvent = null;

    /** IMS call type */
    @QnsConstants.QnsCallType private int mImsCallType;
    /** Call state from TelephonyCallback.CallStateListener */
    @Annotation.CallState private int mCallState;

    private Map<Integer, RestrictInfo> mRestrictInfos = new ConcurrentHashMap<>();
    private Map<Restriction, Integer> mRestrictionTimers = new ConcurrentHashMap<>();

    private class RestrictManagerHandler extends Handler {
        RestrictManagerHandler(Looper l) {
            super(l);
        }

        @Override
        public void handleMessage(Message message) {
            QnsAsyncResult ar;
            int transportType;
            Log.d(mLogTag, "handleMessage : " + message.what);
            switch (message.what) {
                case EVENT_DATA_CONNECTION_CHANGED:
                    ar = (QnsAsyncResult) message.obj;
                    onDataConnectionChanged((DataConnectionChangedInfo) ar.mResult);
                    break;

                case EVENT_CALL_STATE_CHANGED:
                    ar = (QnsAsyncResult) message.obj;
                    int callState = (int) ar.mResult;
                    onCallStateChanged(callState, mTransportType, mCellularAccessNetwork);
                    break;

                case EVENT_SRVCC_STATE_CHANGED:
                    ar = (QnsAsyncResult) message.obj;
                    int srvccState = (int) ar.mResult;
                    onSrvccStateChanged(srvccState);
                    break;

                case EVENT_LOW_RTP_QUALITY_REPORTED:
                    ar = (QnsAsyncResult) message.obj;
                    int reason = (int) ar.mResult;
                    Log.d(mLogTag, "EVENT_LOW_RTP_QUALITY_REPORTED reason: " + reason);
                    onLowRtpQualityEvent(reason);
                    break;

                case EVENT_IMS_REGISTRATION_STATE_CHANGED:
                    ar = (QnsAsyncResult) message.obj;
                    onImsRegistrationStateChanged((QnsImsManager.ImsRegistrationState) ar.mResult);
                    break;

                case EVENT_RELEASE_RESTRICTION:
                    transportType = message.arg1;
                    Restriction restriction = (Restriction) message.obj;
                    Log.d(
                            mLogTag,
                            "EVENT_RELEASE_RESTRICTION : "
                                    + QnsConstants.transportTypeToString(transportType)
                                    + " "
                                    + restrictTypeToString(restriction.mRestrictType));
                    if (restriction
                            == mRestrictInfos
                                    .get(transportType)
                                    .getRestrictionMap()
                                    .get(restriction.mRestrictType)) {
                        releaseRestriction(transportType, restriction.mRestrictType);
                        mQnsTimer.unregisterTimer(mRestrictionTimers
                                .getOrDefault(restriction, INVALID_ID));
                        mRestrictionTimers.remove(restriction);
                    }
                    break;

                case EVENT_INITIAL_DATA_CONNECTION_FAIL_RETRY_TIMER_EXPIRED:
                    Log.d(
                            mLogTag,
                            "Initial Data Connection fail timer expired"
                                    + mIsTimerRunningOnDataConnectionFail);

                    mQnsTimer.unregisterTimer(mFallbackTimerId);
                    if (mIsTimerRunningOnDataConnectionFail) {
                        int currTransportType = message.arg1;
                        fallbackToOtherTransportOnDataConnectionFail(currTransportType);
                    }
                    break;

                case EVENT_WIFI_RTT_BACKHAUL_CHECK_STATUS:
                    ar = (QnsAsyncResult) message.obj;
                    boolean rttCheckStatus = (boolean) ar.mResult;
                    if (!rttCheckStatus) { // rtt Backhaul check failed
                        Log.d(mLogTag, "Rtt check status received:Fail");
                        onWlanRttFail();
                    }
                    break;

                case QnsEventDispatcher.QNS_EVENT_WFC_MODE_TO_WIFI_ONLY:
                    onWfcModeChanged(QnsConstants.WIFI_ONLY, QnsConstants.COVERAGE_HOME);
                    break;
                case QnsEventDispatcher.QNS_EVENT_WFC_MODE_TO_CELLULAR_PREFERRED:
                    onWfcModeChanged(QnsConstants.CELL_PREF, QnsConstants.COVERAGE_HOME);
                    break;

                case QnsEventDispatcher.QNS_EVENT_WFC_MODE_TO_WIFI_PREFERRED:
                    onWfcModeChanged(QnsConstants.WIFI_PREF, QnsConstants.COVERAGE_HOME);
                    break;

                case QnsEventDispatcher.QNS_EVENT_WFC_ROAMING_MODE_TO_WIFI_ONLY:
                    onWfcModeChanged(QnsConstants.WIFI_ONLY, QnsConstants.COVERAGE_ROAM);
                    break;

                case QnsEventDispatcher.QNS_EVENT_WFC_ROAMING_MODE_TO_CELLULAR_PREFERRED:
                    onWfcModeChanged(QnsConstants.CELL_PREF, QnsConstants.COVERAGE_ROAM);
                    break;

                case QnsEventDispatcher.QNS_EVENT_WFC_ROAMING_MODE_TO_WIFI_PREFERRED:
                    onWfcModeChanged(QnsConstants.WIFI_PREF, QnsConstants.COVERAGE_ROAM);
                    break;

                case QnsEventDispatcher.QNS_EVENT_APM_ENABLED:
                case QnsEventDispatcher.QNS_EVENT_WFC_DISABLED:
                case QnsEventDispatcher.QNS_EVENT_WIFI_DISABLING:
                    if (mFallbackCounterOnDataConnectionFail > 0) {
                        Log.d(mLogTag, "Reset Fallback Counter on APM On/WFC off/Wifi Off");
                        mFallbackCounterOnDataConnectionFail = 0;
                    }

                    if (mNetCapability == NetworkCapabilities.NET_CAPABILITY_IMS
                            && hasRestrictionType(
                                    AccessNetworkConstants.TRANSPORT_TYPE_WLAN,
                                    RESTRICT_TYPE_FALLBACK_TO_WWAN_RTT_BACKHAUL_FAIL)) {

                        releaseRestriction(
                                AccessNetworkConstants.TRANSPORT_TYPE_WLAN,
                                RESTRICT_TYPE_FALLBACK_TO_WWAN_RTT_BACKHAUL_FAIL);
                    }
                    break;
                default:
                    break;
            }
        }
    }

    class LowRtpQualityRestriction extends Restriction{
        private int mReason;
        LowRtpQualityRestriction(int type, int[] releaseEvents, long restrictTime, int reason) {
            super(type, releaseEvents, restrictTime);
            mReason = reason;
        }
        int getReason() {
            return mReason;
        }
    }

    class Restriction {
        private final int mRestrictType;
        final ArrayList<Integer> mReleaseEventList;
        long mReleaseTime;

        Restriction(int type, int[] releaseEvents, long restrictTime) {
            mRestrictType = type;
            if (restrictTime == 0) {
                mReleaseTime = 0;
            } else {
                mReleaseTime = restrictTime + SystemClock.elapsedRealtime();
                if (restrictTime > 0 && mReleaseTime < 0) {
                    mReleaseTime = Long.MAX_VALUE;
                }
            }
            if (releaseEvents != null && releaseEvents.length > 0) {
                mReleaseEventList = new ArrayList<>();
                for (int i : releaseEvents) {
                    mReleaseEventList.add(i);
                }
            } else {
                mReleaseEventList = null;
            }
        }

        boolean needRelease(int event) {
            if (mReleaseEventList == null) {
                return false;
            }
            for (Integer i : mReleaseEventList) {
                if (event == i.intValue()) {
                    return true;
                }
            }
            return false;
        }

        void updateRestrictTime(long timeMillis) {
            mReleaseTime = SystemClock.elapsedRealtime() + timeMillis;
            if (timeMillis > 0 && mReleaseTime < 0) {
                mReleaseTime = Long.MAX_VALUE;
            }
        }

        boolean isRestrictionExpired(long elapsedRealTime) {
            if (mReleaseTime != 0 && (mReleaseTime - elapsedRealTime < 0)) {
                Log.d(mLogTag, restrictTypeToString(mRestrictType) + " was expired."
                        + "release time:" + mReleaseTime + ", now:" + elapsedRealTime);
                return true;
            }
            return false;
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            builder.append("[RESTRICTION type:").append(restrictTypeToString(mRestrictType));
            builder.append(" releaseEvents:( ");
            if (mReleaseEventList != null) {
                for (Integer i : mReleaseEventList) {
                    builder.append(i).append(" ");
                }
            }
            builder.append(") remainedTimeMillis:");
            if (mReleaseTime == 0) {
                builder.append("N/A");
            } else {
                long remain = mReleaseTime - SystemClock.elapsedRealtime();
                builder.append(remain);
            }
            builder.append("]");
            return builder.toString();
        }
    }

    class RestrictInfo {
        private int mTransportMode; // AccessNetworkConstants.TRANSPORT_TYPE_WWAN;
        private Map<Integer, Restriction> mRestrictionMap = new ConcurrentHashMap<>();

        RestrictInfo(int transportMode) {
            mTransportMode = transportMode;
        }

        Map<Integer, Restriction> getRestrictionMap() {
            return mRestrictionMap;
        }

        boolean isRestricted() {
            checkUpExpirationTime();
            return mRestrictionMap.size() != 0;
        }

        private void checkUpExpirationTime() {
            if (mRestrictionMap.size() > 0) {
                long now = QnsUtils.getSystemElapsedRealTime();
                mRestrictionMap.entrySet().removeIf(e -> e.getValue().isRestrictionExpired(now));
            }
        }

        /**
         * This method returns if the restriction info has given restriction type.
         *
         * @param restrictType integer value of restriction type.
         * @return true if restrictinfo has the restriction; otherwise false.
         */
        boolean hasRestrictionType(@RestrictType int restrictType) {
            return mRestrictionMap.get(restrictType) != null;
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            builder.append("RestrictInfo[")
                    .append(QnsConstants.transportTypeToString(mTransportMode))
                    .append("] : ");
            if (isRestricted()) {
                for (Restriction restriction : mRestrictionMap.values()) {
                    builder.append(restriction.toString()).append(" ");
                }
            } else {
                builder.append("No restriction");
            }
            return builder.toString();
        }
    }

    RestrictManager(
            QnsComponents qnsComponents,
            Looper loop,
            int netCapability,
            DataConnectionStatusTracker dcst,
            int slotId) {
        mRestrictInfos.put(
                AccessNetworkConstants.TRANSPORT_TYPE_WWAN,
                new RestrictInfo(AccessNetworkConstants.TRANSPORT_TYPE_WWAN));
        mRestrictInfos.put(
                AccessNetworkConstants.TRANSPORT_TYPE_WLAN,
                new RestrictInfo(AccessNetworkConstants.TRANSPORT_TYPE_WLAN));
        mSlotId = slotId;
        mLogTag =
                RestrictManager.class.getSimpleName()
                        + "_"
                        + mSlotId
                        + "_"
                        + QnsUtils.getNameOfNetCapability(netCapability);
        mTelephonyListener = qnsComponents.getQnsTelephonyListener(mSlotId);
        mQnsEventDispatcher = qnsComponents.getQnsEventDispatcher(mSlotId);
        mQnsCarrierConfigManager = qnsComponents.getQnsCarrierConfigManager(mSlotId);
        mQnsTimer = qnsComponents.getQnsTimer();
        mHandler = new RestrictManagerHandler(loop);
        mNetCapability = netCapability;
        mDataConnectionStatusTracker = dcst;
        mQnsCallStatusTracker = qnsComponents.getQnsCallStatusTracker(mSlotId);
        mActiveCallTracker = qnsComponents.getQnsCallStatusTracker(mSlotId).getActiveCallTracker();
        mQnsMetrics = qnsComponents.getQnsMetrics();
        mDataConnectionStatusTracker.registerDataConnectionStatusChanged(
                mHandler, EVENT_DATA_CONNECTION_CHANGED);
        if (mNetCapability == NetworkCapabilities.NET_CAPABILITY_IMS) {
            mTelephonyListener.registerCallStateListener(
                    mHandler, EVENT_CALL_STATE_CHANGED, null, true);
            mTelephonyListener.registerSrvccStateListener(
                    mHandler, EVENT_SRVCC_STATE_CHANGED, null);
        }
        if (mNetCapability == NetworkCapabilities.NET_CAPABILITY_IMS) {
            mQnsImsManager = qnsComponents.getQnsImsManager(mSlotId);
            mQnsImsManager.registerImsRegistrationStatusChanged(
                    mHandler, EVENT_IMS_REGISTRATION_STATE_CHANGED);
            mWifiBackhaulMonitor = qnsComponents.getWifiBackhaulMonitor(mSlotId);
        }

        // check if we can pass "mQnsImsManager"
        mWfcPreference = QnsUtils.getWfcMode(qnsComponents.getQnsImsManager(mSlotId), false);
        mWfcRoamingPreference = QnsUtils.getWfcMode(qnsComponents.getQnsImsManager(mSlotId), true);

        List<Integer> events = new ArrayList<>();
        events.add(QnsEventDispatcher.QNS_EVENT_WFC_MODE_TO_WIFI_ONLY);
        events.add(QnsEventDispatcher.QNS_EVENT_WFC_MODE_TO_CELLULAR_PREFERRED);
        events.add(QnsEventDispatcher.QNS_EVENT_WFC_MODE_TO_WIFI_PREFERRED);
        events.add(QnsEventDispatcher.QNS_EVENT_WFC_ROAMING_MODE_TO_WIFI_ONLY);
        events.add(QnsEventDispatcher.QNS_EVENT_WFC_ROAMING_MODE_TO_CELLULAR_PREFERRED);
        events.add(QnsEventDispatcher.QNS_EVENT_WFC_ROAMING_MODE_TO_WIFI_PREFERRED);
        events.add(QnsEventDispatcher.QNS_EVENT_APM_ENABLED);
        events.add(QnsEventDispatcher.QNS_EVENT_WFC_DISABLED);
        events.add(QnsEventDispatcher.QNS_EVENT_WIFI_DISABLING);
        mQnsEventDispatcher.registerEvent(events, mHandler);

        mCellularNetworkStatusTracker = qnsComponents.getCellularNetworkStatusTracker(mSlotId);
        restrictNonPreferredTransport();
    }

    void clearRestrictions() {
        mRestrictInfos.get(AccessNetworkConstants.TRANSPORT_TYPE_WWAN).getRestrictionMap().clear();
        mRestrictInfos.get(AccessNetworkConstants.TRANSPORT_TYPE_WLAN).getRestrictionMap().clear();
    }

    void close() {
        mDataConnectionStatusTracker.unRegisterDataConnectionStatusChanged(mHandler);
        if (mIsRttStatusCheckRegistered
                && mNetCapability == NetworkCapabilities.NET_CAPABILITY_IMS) {
            mIsRttStatusCheckRegistered = false;
            mWifiBackhaulMonitor.unRegisterForRttStatusChange(mHandler);
        }

        if (mNetCapability == NetworkCapabilities.NET_CAPABILITY_IMS) {
            mTelephonyListener.unregisterCallStateChanged(mHandler);
            mTelephonyListener.unregisterSrvccStateChanged(mHandler);
        }
        mQnsEventDispatcher.unregisterEvent(mHandler);
        if (mNetCapability == NetworkCapabilities.NET_CAPABILITY_IMS
                || mNetCapability == NetworkCapabilities.NET_CAPABILITY_EIMS) {
            if (mActiveCallTracker != null) {
                mActiveCallTracker.unregisterLowMediaQualityListener(mHandler);
            }
        }
        if (mNetCapability == NetworkCapabilities.NET_CAPABILITY_IMS) {
            mQnsImsManager.unregisterImsRegistrationStatusChanged(mHandler);
        }
        mRestrictionTimers.clear();
    }

    private void onWfcModeChanged(int prefMode, @QnsConstants.CellularCoverage int coverage) {
        Log.d(mLogTag, "onWfcModeChanged  prefMode :" + prefMode + "  coverage:" + coverage);
        if (coverage == QnsConstants.COVERAGE_HOME) {
            mWfcPreference = prefMode;
        } else if (coverage == QnsConstants.COVERAGE_ROAM) {
            mWfcRoamingPreference = prefMode;
        }
        if (mCellularCoverage == coverage) {
            if (prefMode == QnsConstants.CELL_PREF) {
                processReleaseEvent(
                        AccessNetworkConstants.TRANSPORT_TYPE_WWAN,
                        RELEASE_EVENT_WFC_PREFER_MODE_CHANGED);
            }
            if (prefMode == QnsConstants.WIFI_PREF || prefMode == QnsConstants.WIFI_ONLY) {
                processReleaseEvent(
                        AccessNetworkConstants.TRANSPORT_TYPE_WLAN,
                        RELEASE_EVENT_WFC_PREFER_MODE_CHANGED);
            }
        }
        checkIfCancelNonPreferredRestriction(getPreferredTransportType());
    }

    @VisibleForTesting
    void restrictNonPreferredTransport() {
        if (mNetCapability == NetworkCapabilities.NET_CAPABILITY_IMS
                && !mCellularNetworkStatusTracker.isAirplaneModeEnabled()) {
            Log.d(mLogTag, "Restrict non-preferred transport at power up");
            int transportType = getPreferredTransportType();
            int waitingTimer =
                    mQnsCarrierConfigManager.getWaitingTimerForPreferredTransportOnPowerOn(
                            transportType);
            if (waitingTimer != QnsConstants.KEY_DEFAULT_VALUE) {
                int preventTransportType = QnsUtils.getOtherTransportType(transportType);
                Log.d(
                        mLogTag,
                        "prevent "
                                + QnsConstants.transportTypeToString(preventTransportType)
                                + " "
                                + waitingTimer
                                + " milli seconds");
                addRestriction(
                        preventTransportType,
                        RESTRICT_TYPE_NON_PREFERRED_TRANSPORT,
                        sReleaseEventMap.get(RESTRICT_TYPE_NON_PREFERRED_TRANSPORT),
                        waitingTimer);
            }
        }
    }

    private void checkIfCancelNonPreferredRestriction(int transportType) {
        if (mNetCapability == NetworkCapabilities.NET_CAPABILITY_IMS) {
            releaseRestriction(transportType, RESTRICT_TYPE_NON_PREFERRED_TRANSPORT);
        }
    }

    private int getPreferredTransportType() {
        int transportType;
        int preference = mWfcPreference;
        if (mCellularCoverage == QnsConstants.COVERAGE_ROAM) {
            preference = mWfcRoamingPreference;
        }
        if (preference == QnsConstants.WIFI_PREF || preference == QnsConstants.WIFI_ONLY) {
            transportType = AccessNetworkConstants.TRANSPORT_TYPE_WLAN;
        } else {
            transportType = AccessNetworkConstants.TRANSPORT_TYPE_WWAN;
        }
        return transportType;
    }

    private void onCallStateChanged(int callState, int transportType, int cellularAn) {
        Log.d(
                mLogTag,
                "onCallStateChanged :"
                        + callState
                        + " transport:"
                        + transportType
                        + " cellularAN:"
                        + cellularAn);
        mCallState = callState;
        if (callState != TelephonyManager.CALL_STATE_IDLE) {
            if (transportType != AccessNetworkConstants.TRANSPORT_TYPE_WLAN
                    && cellularAn != AccessNetworkConstants.AccessNetworkType.EUTRAN
                    && cellularAn != AccessNetworkConstants.AccessNetworkType.NGRAN) {
                onCsCallStarted();
            }
        } else {
            releaseRestriction(
                    AccessNetworkConstants.TRANSPORT_TYPE_WLAN,
                    RESTRICT_TYPE_RESTRICT_IWLAN_CS_CALL);
        }
    }

    private void onSrvccStateChanged(int srvccState) {
        Log.d(mLogTag, "onSrvccStateChanged :" + srvccState);
        if (mImsCallType != QnsConstants.CALL_TYPE_IDLE
                && srvccState == TelephonyManager.SRVCC_STATE_HANDOVER_STARTED) {
            addRestriction(
                    AccessNetworkConstants.TRANSPORT_TYPE_WLAN,
                    RESTRICT_TYPE_RESTRICT_IWLAN_CS_CALL,
                    sReleaseEventMap.get(RESTRICT_TYPE_RESTRICT_IWLAN_CS_CALL),
                    0);
        } else if (mCallState == TelephonyManager.CALL_STATE_IDLE
                || srvccState == TelephonyManager.SRVCC_STATE_HANDOVER_CANCELED
                || srvccState == TelephonyManager.SRVCC_STATE_HANDOVER_FAILED) {
            releaseRestriction(
                    AccessNetworkConstants.TRANSPORT_TYPE_WLAN,
                    RESTRICT_TYPE_RESTRICT_IWLAN_CS_CALL);
        }
    }

    private void onCsCallStarted() {
        if (!mQnsCarrierConfigManager.allowImsOverIwlanCellularLimitedCase()) {
            addRestriction(
                    AccessNetworkConstants.TRANSPORT_TYPE_WLAN,
                    RESTRICT_TYPE_RESTRICT_IWLAN_CS_CALL,
                    sReleaseEventMap.get(RESTRICT_TYPE_RESTRICT_IWLAN_CS_CALL),
                    0);
        }
    }

    @VisibleForTesting
    void onLowRtpQualityEvent(@QnsConstants.RtpLowQualityReason int reason) {
        int lowRtpQualityRestrictTime =
                mQnsCarrierConfigManager.getHoRestrictedTimeOnLowRTPQuality(mTransportType);
        if ((mTransportType == AccessNetworkConstants.TRANSPORT_TYPE_WLAN
                        || mTransportType == AccessNetworkConstants.TRANSPORT_TYPE_WWAN)
                && lowRtpQualityRestrictTime > 0
                && (mImsCallType == QnsConstants.CALL_TYPE_VOICE
                    || mImsCallType == QnsConstants.CALL_TYPE_EMERGENCY)) {
            if (reason > 0) {
                Restriction restriction =
                        new LowRtpQualityRestriction(RESTRICT_TYPE_RTP_LOW_QUALITY,
                                sReleaseEventMap.get(RESTRICT_TYPE_RTP_LOW_QUALITY),
                                lowRtpQualityRestrictTime,
                                reason);
                // If current report has 'no RTP reason' and previous report at previous
                // transport type doesn't have 'no RTP reason', let's move back to previous
                // transport type.
                if ((reason & 1 << QnsConstants.RTP_LOW_QUALITY_REASON_NO_RTP) != 0) {
                    releaseRestriction(QnsUtils.getOtherTransportType(mTransportType),
                            RESTRICT_TYPE_GUARDING, true);
                    Map<Integer, Restriction> restrictionMap = mRestrictInfos
                            .get(QnsUtils.getOtherTransportType(mTransportType))
                            .getRestrictionMap();
                    Restriction restrictionOtherSide = restrictionMap.get(
                            RESTRICT_TYPE_RTP_LOW_QUALITY);
                    if (restrictionOtherSide != null
                            && restrictionOtherSide instanceof LowRtpQualityRestriction) {
                        int reasonOtherSide =
                                ((LowRtpQualityRestriction) restrictionOtherSide).getReason();
                        if ((reasonOtherSide & 1 << QnsConstants.RTP_LOW_QUALITY_REASON_NO_RTP)
                                == 0) {
                            releaseRestriction(QnsUtils.getOtherTransportType(mTransportType),
                                    RESTRICT_TYPE_RTP_LOW_QUALITY, true);
                        }
                    }
                }
                // If both transport have low RTP quality restriction, let ANE do final decision.
                addRestriction(mTransportType, restriction, lowRtpQualityRestrictTime);

                if (mTransportType == AccessNetworkConstants.TRANSPORT_TYPE_WLAN) {
                    int fallbackReason = mQnsCarrierConfigManager.getQnsIwlanHoRestrictReason();
                    if (fallbackReason == QnsConstants.FALLBACK_REASON_RTP_OR_WIFI
                            || fallbackReason == QnsConstants.FALLBACK_REASON_RTP_ONLY) {
                        increaseCounterToRestrictIwlanInCall();
                    }
                }
            } else {
                if (hasRestrictionType(mTransportType, RESTRICT_TYPE_RTP_LOW_QUALITY)) {
                    releaseRestriction(mTransportType, RESTRICT_TYPE_RTP_LOW_QUALITY);
                }
            }
        }
    }

    @VisibleForTesting
    void onDataConnectionChanged(DataConnectionChangedInfo status) {
        int dataConnectionState = status.getState();
        if (dataConnectionState == STATE_CONNECTED || dataConnectionState == STATE_HANDOVER) {
            mTransportType = status.getTransportType();
        } else {
            mTransportType = AccessNetworkConstants.TRANSPORT_TYPE_INVALID;
        }

        Log.d(mLogTag, "onDataConnectionChanged transportType:" + status);
        switch (status.getEvent()) {
            case DataConnectionStatusTracker.EVENT_DATA_CONNECTION_DISCONNECTED:
                processDataConnectionDisconnected();
                break;
            case DataConnectionStatusTracker.EVENT_DATA_CONNECTION_STARTED:
                processDataConnectionStarted(status.getTransportType());
                break;
            case DataConnectionStatusTracker.EVENT_DATA_CONNECTION_CONNECTED:
                processDataConnectionConnected(mTransportType);
                break;
            case DataConnectionStatusTracker.EVENT_DATA_CONNECTION_HANDOVER_STARTED:
                processDataConnectionHandoverStarted();
                break;
            case DataConnectionStatusTracker.EVENT_DATA_CONNECTION_HANDOVER_SUCCESS:
                processDataConnectionHandoverSuccess();
                break;
            case DataConnectionStatusTracker.EVENT_DATA_CONNECTION_HANDOVER_FAILED:
                processDataConnectionHandoverFailed(mTransportType);
                break;
            case DataConnectionStatusTracker.EVENT_DATA_CONNECTION_FAILED:
                processDataConnectionFailed(status.getTransportType());
                break;
            default:
                Log.d(mLogTag, "unknown DataConnectionChangedEvent:");
                break;
        }
    }

    private void processDataConnectionConnected(int transportType) {
        // Since HO hysterisis Guard timer is expected
        checkToCancelInitialPdnConnectionFailFallback();
        clearInitialPdnConnectionFailFallbackRestriction();

        checkIfCancelNonPreferredRestriction(QnsUtils.getOtherTransportType(transportType));
        releaseRestriction(transportType, RESTRICT_TYPE_THROTTLING, true);
        if (mNetCapability == NetworkCapabilities.NET_CAPABILITY_IMS) {
            if (mLastEvaluatedTransportType == AccessNetworkConstants.TRANSPORT_TYPE_INVALID
                    || transportType == mLastEvaluatedTransportType) {
                processHandoverGuardingOperation(transportType);
            } else {
                Log.d(
                        mLogTag,
                        "DataConnectionConnected, but transport type is different,"
                                + " Handover init may follow");
            }
        }
    }

    private void clearInitialPdnConnectionFailFallbackRestriction() {
        mFallbackCounterOnDataConnectionFail = 0;
        if (hasRestrictionType(
                AccessNetworkConstants.TRANSPORT_TYPE_WWAN,
                RESTRICT_TYPE_FALLBACK_ON_DATA_CONNECTION_FAIL)) {
            releaseRestriction(
                    AccessNetworkConstants.TRANSPORT_TYPE_WWAN,
                    RESTRICT_TYPE_FALLBACK_ON_DATA_CONNECTION_FAIL);
        }
        if (hasRestrictionType(
                AccessNetworkConstants.TRANSPORT_TYPE_WLAN,
                RESTRICT_TYPE_FALLBACK_ON_DATA_CONNECTION_FAIL)) {
            releaseRestriction(
                    AccessNetworkConstants.TRANSPORT_TYPE_WLAN,
                    RESTRICT_TYPE_FALLBACK_ON_DATA_CONNECTION_FAIL);
        }
    }

    private void checkToCancelInitialPdnConnectionFailFallback() {
        Log.d(mLogTag, "clear Initial PDN Connection fail Timer checks");

        mIsTimerRunningOnDataConnectionFail = false;
        mRetryCounterOnDataConnectionFail = 0;

        mQnsTimer.unregisterTimer(mFallbackTimerId);
    }

    private void processDataConnectionDisconnected() {
        processReleaseEvent(AccessNetworkConstants.TRANSPORT_TYPE_WWAN, RELEASE_EVENT_DISCONNECT);
        processReleaseEvent(AccessNetworkConstants.TRANSPORT_TYPE_WLAN, RELEASE_EVENT_DISCONNECT);
        mCounterForIwlanRestrictionInCall = 0;
        if (mDeferredThrottlingEvent != null) {
            long delayMillis =
                    mDeferredThrottlingEvent.second - QnsUtils.getSystemElapsedRealTime();
            if (delayMillis > 0) {
                if (mDebugFlag) Log.d(mLogTag, "onDisconnected, process deferred Throttling event");
                addRestriction(
                        mDeferredThrottlingEvent.first,
                        RESTRICT_TYPE_THROTTLING,
                        sReleaseEventMap.get(RESTRICT_TYPE_THROTTLING),
                        delayMillis);
            }
            mDeferredThrottlingEvent = null;
        }
    }

    private void processDataConnectionStarted(int currTransportType) {
        if (mLastDataConnectionTransportType != currTransportType) {
            Log.d(
                    mLogTag,
                    "clear Initial PDN Connection fallback checks for last transport type:"
                            + mLastDataConnectionTransportType);
            checkToCancelInitialPdnConnectionFailFallback();

            if (hasRestrictionType(
                    mLastDataConnectionTransportType,
                    RESTRICT_TYPE_FALLBACK_ON_DATA_CONNECTION_FAIL)) {
                Log.d(
                        mLogTag,
                        "PreIncrement_Fallback Counter : " + mFallbackCounterOnDataConnectionFail);
                mFallbackCounterOnDataConnectionFail += 1;
            }
            mLastDataConnectionTransportType = currTransportType;
        }
    }

    private void processDataConnectionHandoverStarted() {
        if ((mTransportType != AccessNetworkConstants.TRANSPORT_TYPE_INVALID)
                && !hasRestrictionType(mTransportType, RestrictManager.RESTRICT_TYPE_GUARDING)) {
            startGuarding(GUARDING_TIMER_HANDOVER_INIT, mTransportType);
        }
    }

    private void processDataConnectionHandoverSuccess() {
        // Handover Guarding Timer operation
        processHandoverGuardingOperation(mTransportType);

        // update LowRtpQualityListener
        if (mTransportType == AccessNetworkConstants.TRANSPORT_TYPE_WLAN
                || mTransportType == AccessNetworkConstants.TRANSPORT_TYPE_WWAN) {
            // Return to the transport type restricted by low RTP. It may be singleRAT case, release
            // the restriction.
            releaseRestriction(mTransportType, RESTRICT_TYPE_RTP_LOW_QUALITY);
        }
    }

    private void processDataConnectionHandoverFailed(int transportType) {
        cancelGuarding(transportType);
    }

    private void processHandoverGuardingOperation(int transportType) {
        int guardingTransport = QnsUtils.getOtherTransportType(transportType);
        int delayMillis = getGuardingTimeMillis(guardingTransport, mImsCallType);
        int minimumGuardingTimer = mQnsCarrierConfigManager.getMinimumHandoverGuardingTimer();
        if (delayMillis == 0 && minimumGuardingTimer > 0) {
            delayMillis = minimumGuardingTimer;
        }

        if (delayMillis > 0) {
            startGuarding(delayMillis, guardingTransport);
        } else {
            cancelGuarding(guardingTransport);
        }
    }

    private void processDataConnectionFailed(int dataConnectionTransportType) {
        if (mCellularNetworkStatusTracker != null
                && !mCellularNetworkStatusTracker.isAirplaneModeEnabled()) {
            Log.d(mLogTag, "Initiate data connection fail Fallback support check");
            checkFallbackOnDataConnectionFail(dataConnectionTransportType);
        } else {
            checkToCancelInitialPdnConnectionFailFallback();
        }
    }

    private void checkFallbackOnDataConnectionFail(int transportType) {
        int[] fallbackConfigOnInitDataFail =
                mQnsCarrierConfigManager.getInitialDataConnectionFallbackConfig(mNetCapability);

        Log.d(
                mLogTag,
                "FallbackConfig set is :"
                        + fallbackConfigOnInitDataFail[0]
                        + ":"
                        + fallbackConfigOnInitDataFail[1]
                        + ":"
                        + fallbackConfigOnInitDataFail[2]);

        if ((fallbackConfigOnInitDataFail != null && fallbackConfigOnInitDataFail[0] == 1)
                && !hasRestrictionType(
                        transportType, RESTRICT_TYPE_FALLBACK_ON_DATA_CONNECTION_FAIL)
                && (fallbackConfigOnInitDataFail[3] == 0
                        || mFallbackCounterOnDataConnectionFail
                                < fallbackConfigOnInitDataFail[3])) {
            Log.d(
                    mLogTag,
                    "FallbackCount: "
                            + fallbackConfigOnInitDataFail[3]
                            + "_"
                            + mFallbackCounterOnDataConnectionFail);
            enableFallbackRetryCountCheckOnInitialPdnFail(
                    transportType, fallbackConfigOnInitDataFail[1]);
            enableFallbackRetryTimerCheckOnInitialPdnFail(
                    transportType, fallbackConfigOnInitDataFail[2]);
        }
    }

    private void enableFallbackRetryTimerCheckOnInitialPdnFail(
            int transportType, int fallbackRetryTimer) {
        Log.d(
                mLogTag,
                "Start Initial Data Connection fail retry_timer On TransportType"
                        + fallbackRetryTimer
                        + "_"
                        + QnsConstants.transportTypeToString(transportType));
        if (fallbackRetryTimer > 0 && !mIsTimerRunningOnDataConnectionFail) {
            Message msg =
                    mHandler.obtainMessage(
                            EVENT_INITIAL_DATA_CONNECTION_FAIL_RETRY_TIMER_EXPIRED,
                            transportType,
                            0,
                            null);
            mFallbackTimerId = mQnsTimer.registerTimer(msg, fallbackRetryTimer);
            mIsTimerRunningOnDataConnectionFail = true;
        }
    }

    private void enableFallbackRetryCountCheckOnInitialPdnFail(
            int transportType, int fallbackRetryCount) {
        Log.d(
                mLogTag,
                "Start Initial Data Connection fail retry_count On TransportType"
                        + fallbackRetryCount
                        + "_"
                        + mRetryCounterOnDataConnectionFail
                        + "_"
                        + QnsConstants.transportTypeToString(transportType));
        if (fallbackRetryCount > 0) {
            if (mRetryCounterOnDataConnectionFail == fallbackRetryCount) {
                fallbackToOtherTransportOnDataConnectionFail(transportType);
            } else {
                mRetryCounterOnDataConnectionFail += 1;
            }
        }
    }

    private void fallbackToOtherTransportOnDataConnectionFail(int currTransportType) {

        checkToCancelInitialPdnConnectionFailFallback();

        addRestriction(
                currTransportType,
                RESTRICT_TYPE_FALLBACK_ON_DATA_CONNECTION_FAIL,
                sReleaseEventMap.get(RESTRICT_TYPE_FALLBACK_ON_DATA_CONNECTION_FAIL),
                mQnsCarrierConfigManager.getFallbackGuardTimerOnInitialConnectionFail(
                        mNetCapability));
    }

    @VisibleForTesting
    void onImsRegistrationStateChanged(QnsImsManager.ImsRegistrationState event) {
        Log.d(
                mLogTag,
                "onImsRegistrationStateChanged["
                        + QnsConstants.transportTypeToString(mTransportType)
                        + "] transportType["
                        + QnsConstants.transportTypeToString(event.getTransportType())
                        + "] RegistrationState["
                        + QnsConstants.imsRegistrationEventToString(event.getEvent())
                        + "]");
        int prefMode =
                mCellularCoverage == QnsConstants.COVERAGE_HOME
                        ? mWfcPreference
                        : mWfcRoamingPreference;

        registerRttStatusCheckEvent();

        switch (event.getEvent()) {
            case QnsConstants.IMS_REGISTRATION_CHANGED_UNREGISTERED:
                onImsUnregistered(event, mTransportType, prefMode);
                break;
            case QnsConstants.IMS_REGISTRATION_CHANGED_ACCESS_NETWORK_CHANGE_FAILED:
                onImsHoRegisterFailed(event, mTransportType, prefMode);
                break;
            case QnsConstants.IMS_REGISTRATION_CHANGED_REGISTERED:
                Log.d(
                        mLogTag,
                        "On Ims Registered: "
                                + QnsConstants.transportTypeToString(event.getTransportType()));
                if (event.getTransportType() == AccessNetworkConstants.TRANSPORT_TYPE_WLAN
                        && hasRestrictionType(
                                AccessNetworkConstants.TRANSPORT_TYPE_WLAN,
                                RESTRICT_TYPE_FALLBACK_TO_WWAN_IMS_REGI_FAIL)) {
                    releaseRestriction(
                            AccessNetworkConstants.TRANSPORT_TYPE_WLAN,
                            RESTRICT_TYPE_FALLBACK_TO_WWAN_IMS_REGI_FAIL);
                }
                break;
            default:
                break;
        }
    }

    private void registerRttStatusCheckEvent() {
        if (mNetCapability == NetworkCapabilities.NET_CAPABILITY_IMS) {

            if (mWifiBackhaulMonitor.isRttCheckEnabled()) {
                if (!mIsRttStatusCheckRegistered) {
                    mIsRttStatusCheckRegistered = true;
                    mWifiBackhaulMonitor.registerForRttStatusChange(
                            mHandler, EVENT_WIFI_RTT_BACKHAUL_CHECK_STATUS);
                }
            } else {
                if (mIsRttStatusCheckRegistered) {
                    mIsRttStatusCheckRegistered = false;
                    mWifiBackhaulMonitor.unRegisterForRttStatusChange(mHandler);
                }
            }
        }
    }

    private void onImsUnregistered(
            QnsImsManager.ImsRegistrationState event, int transportType, int prefMode) {
        if (transportType == AccessNetworkConstants.TRANSPORT_TYPE_WLAN) {
            int fallbackTimeMillis =
                    mQnsCarrierConfigManager.getFallbackTimeImsUnregistered(
                            event.getReasonInfo().getCode(), prefMode);
            if (fallbackTimeMillis > 0
                    && mQnsCarrierConfigManager.isAccessNetworkAllowed(
                            mCellularAccessNetwork, NetworkCapabilities.NET_CAPABILITY_IMS)) {
                fallbackToWwanForImsRegistration(fallbackTimeMillis);
            }
        }
    }

    private void onImsHoRegisterFailed(
            QnsImsManager.ImsRegistrationState event, int transportType, int prefMode) {
        if (transportType == AccessNetworkConstants.TRANSPORT_TYPE_WLAN
                && transportType == event.getTransportType()) {
            int fallbackTimeMillis =
                    mQnsCarrierConfigManager.getFallbackTimeImsHoRegisterFailed(
                            event.getReasonInfo().getCode(), prefMode);
            if (fallbackTimeMillis > 0
                    && mQnsCarrierConfigManager.isAccessNetworkAllowed(
                            mCellularAccessNetwork, NetworkCapabilities.NET_CAPABILITY_IMS)) {
                fallbackToWwanForImsRegistration(fallbackTimeMillis);
            }
        }
    }

    protected void onWlanRttFail() {
        Log.d(mLogTag, "start RTT Fallback:");
        int fallbackTimeMillis = mQnsCarrierConfigManager.getWlanRttFallbackHystTimer();
        if (fallbackTimeMillis > 0
                && mQnsCarrierConfigManager.isAccessNetworkAllowed(
                        mCellularAccessNetwork, NetworkCapabilities.NET_CAPABILITY_IMS)) {

            fallbackToWwanForImsRegistration(
                    AccessNetworkConstants.TRANSPORT_TYPE_WLAN,
                    RESTRICT_TYPE_FALLBACK_TO_WWAN_RTT_BACKHAUL_FAIL,
                    fallbackTimeMillis);
        }
    }

    private void fallbackToWwanForImsRegistration(int fallbackTimeMillis) {
        fallbackToWwanForImsRegistration(
                AccessNetworkConstants.TRANSPORT_TYPE_WLAN,
                RESTRICT_TYPE_FALLBACK_TO_WWAN_IMS_REGI_FAIL,
                fallbackTimeMillis);
    }

    private void fallbackToWwanForImsRegistration(
            int transportType, int restrictType, int fallbackTimeMillis) {
        Log.d(mLogTag, "release ignorable restrictions on WWAN to fallback.");
        for (int restriction : ignorableRestrictionsOnSingleRat) {
            releaseRestriction(QnsUtils.getOtherTransportType(transportType), restriction, false);
        }
        addRestriction(
                transportType,
                restrictType,
                sReleaseEventMap.get(restrictType),
                fallbackTimeMillis);
    }

    /** Update Last notified transport type from ANE which owns this RestrictManager */
    void updateLastNotifiedTransportType(@AccessNetworkConstants.TransportType int transportType) {
        if (mDebugFlag) {
            Log.d(
                    mLogTag,
                    "updateLastEvaluatedTransportType: "
                            + QnsConstants.transportTypeToString(transportType));
        }
        mLastEvaluatedTransportType = transportType;
        if (mDataConnectionStatusTracker.isActiveState() && mTransportType != transportType) {
            startGuarding(GUARDING_TIMER_HANDOVER_INIT,
                    QnsUtils.getOtherTransportType(transportType));
        }
    }

    @VisibleForTesting
    void setCellularCoverage(@QnsConstants.CellularCoverage int coverage) {
        Log.d(mLogTag, "setCellularCoverage:" + QnsConstants.coverageToString(coverage));
        mCellularCoverage = coverage;
        checkIfCancelNonPreferredRestriction(getPreferredTransportType());
    }

    protected void setQnsCallType(@QnsConstants.QnsCallType int callType) {
        if (callType != mImsCallType) {
            updateGuardingTimerConditionOnCallState(mImsCallType, callType);
        }

        mImsCallType = callType;

        Log.d(mLogTag, "setQnsCallType: " + QnsConstants.callTypeToString(callType));
        if (callType == QnsConstants.CALL_TYPE_IDLE) {
            Log.d(mLogTag, "Call end. init mCounterForIwlanRestrictionInCall");
            mCounterForIwlanRestrictionInCall = 0;

            processReleaseEvent(AccessNetworkConstants.TRANSPORT_TYPE_WLAN, RELEASE_EVENT_CALL_END);
            processReleaseEvent(AccessNetworkConstants.TRANSPORT_TYPE_WWAN, RELEASE_EVENT_CALL_END);
            unregisterLowRtpQualityEvent();
        } else {
            registerLowRtpQualityEvent();
        }
    }

    private void updateGuardingTimerConditionOnCallState(int prevCallType, int newCallType) {
        int currGuardingTransport = QnsUtils.getOtherTransportType(mTransportType);
        if (mRestrictInfos.get(currGuardingTransport) == null) return;

        Map<Integer, Restriction> restrictionMap =
                mRestrictInfos.get(currGuardingTransport).getRestrictionMap();
        Restriction restriction = restrictionMap.get(RESTRICT_TYPE_GUARDING);

        if (restriction != null) {
            int prevCallTypeMillis = getGuardingTimeMillis(currGuardingTransport, prevCallType);
            if (prevCallTypeMillis == 0) {
                return; // We don't need to update minimum guarding timer.
            }
            int newCallTypeMillis =
                    getGuardingTimeMillis(
                            currGuardingTransport, newCallType); // new Call type timer
            if (newCallTypeMillis == prevCallTypeMillis) return;

            if (newCallTypeMillis != 0) {
                // remaining time on current call type
                long prevCallTypeRemainingMillis =
                        restriction.mReleaseTime - SystemClock.elapsedRealtime();
                int guardTimerElapsed = prevCallTypeMillis - (int) prevCallTypeRemainingMillis;
                int newGuardTimer = newCallTypeMillis - guardTimerElapsed;

                if (mDebugFlag) {
                    Log.d(
                            mLogTag,
                            "Prev Call Type Guarding millis:"
                                    + prevCallTypeMillis
                                    + "Prev Call type remaining millis:"
                                    + prevCallTypeRemainingMillis
                                    + "New Call type Guarding millis:"
                                    + newCallTypeMillis
                                    + "Guard timer Elapsed:"
                                    + guardTimerElapsed
                                    + "New Guard timer to set:"
                                    + newGuardTimer);
                }
                if (newGuardTimer > 0) {
                    startGuarding(newGuardTimer, currGuardingTransport);
                    return;
                }
            }
            cancelGuarding(currGuardingTransport);
        }
    }

    @VisibleForTesting
    void setCellularAccessNetwork(int accessNetwork) {
        mCellularAccessNetwork = accessNetwork;
        Log.d(mLogTag, "Current Cellular Network:" + mCellularAccessNetwork);
        if (mNetCapability == NetworkCapabilities.NET_CAPABILITY_IMS
                && !mQnsCarrierConfigManager.isAccessNetworkAllowed(
                        accessNetwork, mNetCapability)) {
            processReleaseEvent(
                    AccessNetworkConstants.TRANSPORT_TYPE_WLAN, RELEASE_EVENT_IMS_NOT_SUPPORT_RAT);
        }
    }

    void addRestriction(int transport, Restriction restrictObj, long timeMillis) {
        boolean needNotify = false;
        Map<Integer, Restriction> restrictionMap =
                mRestrictInfos.get(transport).getRestrictionMap();
        Restriction restriction = restrictionMap.get(restrictObj.mRestrictType);
        Log.d(
                mLogTag,
                "addRestriction["
                        + QnsConstants.transportTypeToString(transport)
                        + "] "
                        + restrictTypeToString(restrictObj.mRestrictType)
                        + " was restrict:"
                        + (restriction != null)
                        + " timeMillis:" + timeMillis);
        if (restriction == null) {
            restriction = restrictObj;
            restrictionMap.put(restrictObj.mRestrictType, restriction);
            Log.d(
                    mLogTag,
                    "addRestriction["
                            + QnsConstants.transportTypeToString(transport)
                            + "] "
                            + restriction);
            needNotify = true;
        } else {
            if (timeMillis > 0) {
                restriction.updateRestrictTime(timeMillis);
                removeReleaseRestrictionMessage(restriction);
            }
            Log.d(
                    mLogTag,
                    "updateRestriction["
                            + QnsConstants.transportTypeToString(transport)
                            + "] "
                            + restriction);
        }
        if (timeMillis > 0) {
            sendReleaseRestrictionMessage(transport, restriction);
        }
        if (needNotify) {
            notifyRestrictInfoChanged();
        }
    }

    void addRestriction(int transport, int type, int[] releaseEvents, long timeMillis) {
        boolean needNotify = false;
        Map<Integer, Restriction> restrictionMap =
                mRestrictInfos.get(transport).getRestrictionMap();
        Restriction restriction = restrictionMap.get(type);
        Log.d(
                mLogTag,
                "addRestriction["
                        + QnsConstants.transportTypeToString(transport)
                        + "] "
                        + restrictTypeToString(type)
                        + " was restrict:"
                        + (restriction != null)
                        + " timeMillis:" + timeMillis);
        if (restriction == null) {
            restriction = new Restriction(type, releaseEvents, timeMillis);
            restrictionMap.put(type, restriction);
            Log.d(
                    mLogTag,
                    "addRestriction["
                            + QnsConstants.transportTypeToString(transport)
                            + "] "
                            + restriction);
            needNotify = true;
        } else {
            if (timeMillis > 0) {
                restriction.updateRestrictTime(timeMillis);
                removeReleaseRestrictionMessage(restriction);
            }
            Log.d(
                    mLogTag,
                    "updateRestriction["
                            + QnsConstants.transportTypeToString(transport)
                            + "] "
                            + restriction);
        }
        if (timeMillis > 0) {
            sendReleaseRestrictionMessage(transport, restriction);
        }
        if (needNotify) {
            notifyRestrictInfoChanged();
        }
    }

    void releaseRestriction(int transport, int type) {
        releaseRestriction(transport, type, false);
    }

    void releaseRestriction(int transport, int type, boolean skipNotify) {
        boolean needNotify = false;
        Map<Integer, Restriction> restrictionMap =
                mRestrictInfos.get(transport).getRestrictionMap();
        Restriction restriction = restrictionMap.get(type);
        Log.d(
                mLogTag,
                "releaseRestriction["
                        + QnsConstants.transportTypeToString(transport)
                        + "] "
                        + restrictTypeToString(type)
                        + " was restrict:"
                        + (restriction != null));
        if (restriction == null) {
            Log.d(mLogTag, "no restriction to release " + restrictTypeToString(type) + " " + type);
        } else {
            if (restriction.mReleaseTime > 0) {
                removeReleaseRestrictionMessage(restriction);
            }
            restrictionMap.remove(restriction.mRestrictType);
            mRestrictionTimers.remove(restriction);
            needNotify = true;
        }
        if (needNotify && !skipNotify) {
            notifyRestrictInfoChanged();
        }
    }

    void processReleaseEvent(int transportType, int event) {
        ArrayList<Integer> releaseList = new ArrayList<>();
        Map<Integer, Restriction> restrictMap =
                mRestrictInfos.get(transportType).getRestrictionMap();
        Log.d(
                mLogTag,
                "processReleaseEvent["
                        + QnsConstants.transportTypeToString(transportType)
                        + "] "
                        + event);

        for (Integer restrictType : restrictMap.keySet()) {
            if (restrictMap.get(restrictType).needRelease(event)) {
                releaseList.add(restrictType);
            }
        }
        for (Integer restrictType : releaseList) {
            releaseRestriction(transportType, restrictType);
        }
    }

    private void sendReleaseRestrictionMessage(int transportType, Restriction restriction) {
        if (restriction == null) {
            Log.e(mLogTag, "sendReleaseRestrictionMessage restriction is null");
            return;
        }
        Message msg =
                mHandler.obtainMessage(EVENT_RELEASE_RESTRICTION, transportType, 0, restriction);
        long delayInMillis = restriction.mReleaseTime - SystemClock.elapsedRealtime();
        int timerId = mQnsTimer.registerTimer(msg, delayInMillis);
        mRestrictionTimers.put(restriction, timerId);
        Log.d(
                mLogTag,
                restrictTypeToString(restriction.mRestrictType)
                        + " will be released after "
                        + delayInMillis
                        + " millisecs");
    }

    private void removeReleaseRestrictionMessage(Restriction restriction) {
        if (restriction == null) {
            Log.e(mLogTag, "removeReleaseRestrictionMessage restriction is null");
            return;
        }
        mQnsTimer.unregisterTimer(mRestrictionTimers.getOrDefault(restriction, INVALID_ID));
        mRestrictionTimers.remove(restriction);
    }

    void registerRestrictInfoChanged(Handler h, int what) {
        mRestrictInfoRegistrant = new QnsRegistrant(h, what, null);
    }

    void unRegisterRestrictInfoChanged(Handler h) {
        mRestrictInfoRegistrant = null;
    }

    @VisibleForTesting
    boolean isRestricted(int transportType) {
        if (mRestrictInfos.isEmpty()) return false;

        if (mRestrictInfos.get(transportType) != null) {
            return mRestrictInfos.get(transportType).isRestricted();
        }

        return false;
    }

    boolean isRestrictedExceptGuarding(int transportType) {
        try {
            RestrictInfo info = mRestrictInfos.get(transportType);
            int size = info.getRestrictionMap().size();
            if (info.hasRestrictionType(RESTRICT_TYPE_GUARDING)) {
                size--;
            }
            return size > 0;
        } catch (Exception e) {
        }
        return false;
    }

    @VisibleForTesting
    boolean hasRestrictionType(int transportType, int restrictType) {
        try {
            if (mRestrictInfos != null) {
                return mRestrictInfos.get(transportType).hasRestrictionType(restrictType);
            }
        } catch (Exception e) {

        }
        return false;
    }

    /** This method is only for Testing */
    @VisibleForTesting
    protected long getRemainingGuardTimer(int transportType) {
        return mRestrictInfos
                        .get(transportType)
                        .getRestrictionMap()
                        .get(RESTRICT_TYPE_GUARDING)
                        .mReleaseTime
                - SystemClock.elapsedRealtime();
    }

    @VisibleForTesting
    boolean isAllowedOnSingleTransport(int transportType) {
        if (mRestrictInfos.isEmpty()) return false;
        Log.d(
                mLogTag,
                "isAllowedOnSingleTransport ("
                        + QnsConstants.transportTypeToString(transportType)
                        + ")  restriction :"
                        + mRestrictInfos.get(transportType).toString());
        int countIgnorableRestriction = 0;
        for (int restrictType : ignorableRestrictionsOnSingleRat) {
            if (mRestrictInfos.get(transportType).hasRestrictionType(restrictType)) {
                countIgnorableRestriction++;
            }
        }
        if (mRestrictInfos.get(transportType).getRestrictionMap().size()
                == countIgnorableRestriction) {
            return true;
        }
        return false;
    }

    void increaseCounterToRestrictIwlanInCall() {
        mCounterForIwlanRestrictionInCall += 1;
        int maxAllowedRoveOutByLowRtpQuality =
                mQnsCarrierConfigManager.getQnsMaxIwlanHoCountDuringCall();
        if (maxAllowedRoveOutByLowRtpQuality > 0
                && mCounterForIwlanRestrictionInCall == maxAllowedRoveOutByLowRtpQuality) {
            Log.d(mLogTag, "reached maxAllowedRoveOutByLowRtpQuality");
            addRestriction(
                    AccessNetworkConstants.TRANSPORT_TYPE_WLAN,
                    RESTRICT_TYPE_RESTRICT_IWLAN_IN_CALL,
                    sReleaseEventMap.get(RESTRICT_TYPE_RESTRICT_IWLAN_IN_CALL),
                    0);
        }
    }

    private void notifyRestrictInfoChanged() {
        Log.d(mLogTag, "notifyRestrictInfoChanged");
        if (mRestrictInfoRegistrant != null) {
            mRestrictInfoRegistrant.notifyResult(mRestrictInfos);

            // metrics
            sendRestrictionsForMetrics();
        } else {
            Log.d(mLogTag, "notifyRestrictInfoChanged. no Registrant.");
        }
    }

    private void registerLowRtpQualityEvent() {
        if ((mImsCallType == QnsConstants.CALL_TYPE_VOICE
                        || mImsCallType == QnsConstants.CALL_TYPE_EMERGENCY)
                && (mTransportType == AccessNetworkConstants.TRANSPORT_TYPE_WLAN
                        || mTransportType == AccessNetworkConstants.TRANSPORT_TYPE_WWAN)
                && mActiveCallTracker != null) {
            int hoRestrictTimeOnLowRtpQuality =
                    mQnsCarrierConfigManager.getHoRestrictedTimeOnLowRTPQuality(mTransportType);
            if (hoRestrictTimeOnLowRtpQuality > 0) {
                Log.d(mLogTag, "registerLowRtpQualityEvent");
                mActiveCallTracker.registerLowMediaQualityListener(
                        mHandler, EVENT_LOW_RTP_QUALITY_REPORTED, null);
            }
        }
    }

    private void unregisterLowRtpQualityEvent() {
        if (mNetCapability == NetworkCapabilities.NET_CAPABILITY_IMS
                || mNetCapability == NetworkCapabilities.NET_CAPABILITY_EIMS) {
            if (mActiveCallTracker != null) {
                mActiveCallTracker.unregisterLowMediaQualityListener(mHandler);
            }
        }
    }

    private int getGuardingTimeMillis(int transportType, int callType) {
        int delayMillis;
        switch (mNetCapability) {
            case NetworkCapabilities.NET_CAPABILITY_IMS:
            case NetworkCapabilities.NET_CAPABILITY_EIMS:
                if (!mQnsCarrierConfigManager.isHysteresisTimerEnabled(mCellularCoverage)) {
                    Log.d(
                            mLogTag,
                            "getGuardingTimeMillis: handover guarding timer is not enabled at "
                                    + QnsConstants.coverageToString(mCellularCoverage));
                    return 0;
                }
                if (transportType == AccessNetworkConstants.TRANSPORT_TYPE_WWAN) {
                    delayMillis =
                            mQnsCarrierConfigManager.getWwanHysteresisTimer(
                                    mNetCapability, callType);
                } else {
                    delayMillis =
                            mQnsCarrierConfigManager.getWlanHysteresisTimer(
                                    mNetCapability, callType);
                }
                if (delayMillis > 0
                        && mQnsCarrierConfigManager.isGuardTimerHysteresisOnPrefSupported()) {
                    int preference = mWfcPreference;
                    if (mCellularCoverage == QnsConstants.COVERAGE_ROAM) {
                        preference = mWfcRoamingPreference;
                    }
                    if (preference == QnsConstants.CELL_PREF
                            && transportType == AccessNetworkConstants.TRANSPORT_TYPE_WLAN) {
                        Log.d(
                                mLogTag,
                                "getGuardingTimeMillis: cellular preferred case, don't guard"
                                        + " handover to WLAN");
                        delayMillis = 0;
                    } else if (preference == QnsConstants.WIFI_PREF
                            && transportType == AccessNetworkConstants.TRANSPORT_TYPE_WWAN) {
                        Log.d(
                                mLogTag,
                                "getGuardingTimeMillis: wifi preferred case, don't guard handover"
                                        + " to WWAN");
                        delayMillis = 0;
                    }
                }
                break;
            case NetworkCapabilities.NET_CAPABILITY_MMS:
            case NetworkCapabilities.NET_CAPABILITY_XCAP:
            case NetworkCapabilities.NET_CAPABILITY_CBS:
                callType = mQnsCallStatusTracker.isCallIdle() ? QnsConstants.CALL_TYPE_IDLE
                                : QnsConstants.CALL_TYPE_VOICE;
                if (transportType == AccessNetworkConstants.TRANSPORT_TYPE_WWAN) {
                    delayMillis =
                            mQnsCarrierConfigManager.getWwanHysteresisTimer(
                                    mNetCapability, callType);
                } else if (transportType == AccessNetworkConstants.TRANSPORT_TYPE_WLAN) {
                    delayMillis =
                            mQnsCarrierConfigManager.getWlanHysteresisTimer(
                                    mNetCapability, callType);
                } else {
                    delayMillis = 0;
                }
                break;
            default:
                delayMillis = 0;
                break;
        }
        Log.d(
                mLogTag,
                "getGuardingTimeMillis: timer = "
                        + delayMillis
                        + " for transport type = "
                        + QnsConstants.transportTypeToString(transportType)
                        + " in "
                        + QnsConstants.callTypeToString(callType)
                        + " state.");

        return delayMillis;
    }

    @VisibleForTesting
    void startGuarding(int delay, int transportType) {
        // It is invalid to run to RESTRICT_TYPE_GUARDING for both Transport at same time
        // Make sure to release source TransportType Guarding before starting guarding for New
        // Transport
        // Type
        if (transportType != AccessNetworkConstants.TRANSPORT_TYPE_INVALID
                && hasRestrictionType(QnsUtils.getOtherTransportType(transportType),
                RestrictManager.RESTRICT_TYPE_GUARDING)) {
            Log.d(
                    mLogTag,
                    "RESTRICT_TYPE_GUARDING cleared from Guarding for:"
                            + QnsConstants.transportTypeToString(mTransportType));
            // addRestriction() will take care to notify the ANE of Restrict Info status
            releaseRestriction(
                    QnsUtils.getOtherTransportType(transportType), RESTRICT_TYPE_GUARDING, true);
        }

        addRestriction(
                transportType,
                RESTRICT_TYPE_GUARDING,
                sReleaseEventMap.get(RESTRICT_TYPE_GUARDING),
                delay);
    }

    private void cancelGuarding(int transportType) {
        releaseRestriction(transportType, RESTRICT_TYPE_GUARDING);
    }

    protected void notifyThrottling(boolean throttle, long throttleTime, int transportType) {
        Log.d(
                mLogTag,
                "notifyThrottling throttle:"
                        + throttle
                        + "  throttleTime:"
                        + throttleTime
                        + "  transportType:"
                        + QnsConstants.transportTypeToString(transportType));
        if (throttle) {
            if (throttleTime < 0) {
                //FWK send minus value of throttle expiration time, consider anomaly report at here.
                return;
            }
            long delayMillis = throttleTime - SystemClock.elapsedRealtime();
            if (delayMillis > 0) {
                if (mDataConnectionStatusTracker.isActiveState()) {
                    Log.d(
                            mLogTag,
                            "Defer Throttling event during active state transportType:"
                                    + transportType
                                    + " ThrottleTime:"
                                    + throttleTime);
                    mDeferredThrottlingEvent = new Pair<>(transportType, throttleTime);
                } else {
                    if (throttleTime == Long.MAX_VALUE || throttleTime == Integer.MAX_VALUE) {
                        //Keep throttle status until receiving un-throttle event.
                        delayMillis = 0;
                    }
                    addRestriction(
                            transportType,
                            RESTRICT_TYPE_THROTTLING,
                            sReleaseEventMap.get(RESTRICT_TYPE_THROTTLING),
                            delayMillis);
                }
            }
        } else {
            releaseRestriction(transportType, RESTRICT_TYPE_THROTTLING);
            if (mDeferredThrottlingEvent != null) mDeferredThrottlingEvent = null;
        }
    }

    static String restrictTypeToString(int restrictType) {
        switch (restrictType) {
            case RESTRICT_TYPE_GUARDING:
                return "RESTRICT_TYPE_GUARDING";
            case RESTRICT_TYPE_THROTTLING:
                return "RESTRICT_TYPE_THROTTLING";
            case RESTRICT_TYPE_HO_NOT_ALLOWED:
                return "RESTRICT_TYPE_HO_NOT_ALLOWED";
            case RESTRICT_TYPE_NON_PREFERRED_TRANSPORT:
                return "RESTRICT_TYPE_NON_PREFERRED_TRANSPORT";
            case RESTRICT_TYPE_RTP_LOW_QUALITY:
                return "RESTRICT_TYPE_RTP_LOW_QUALITY";
            case RESTRICT_TYPE_RESTRICT_IWLAN_IN_CALL:
                return "RESTRICT_TYPE_RESTRICT_IWLAN_IN_CALL";
            case RESTRICT_TYPE_RESTRICT_IWLAN_CS_CALL:
                return "RESTRICT_TYPE_RESTRICT_IWLAN_CS_CALL";
            case RESTRICT_TYPE_FALLBACK_TO_WWAN_IMS_REGI_FAIL:
                return "RESTRICT_TYPE_FALLBACK_TO_WWAN_IMS_REGI_FAIL";
            case RESTRICT_TYPE_FALLBACK_ON_DATA_CONNECTION_FAIL:
                return "RESTRICT_TYPE_FALLBACK_ON_DATA_CONNECTION_FAIL";
            case RESTRICT_TYPE_FALLBACK_TO_WWAN_RTT_BACKHAUL_FAIL:
                return "RESTRICT_TYPE_FALLBACK_TO_WWAN_RTT_BACKHAUL_FAIL";
        }
        return "";
    }

    /**
     * Dumps the state of {@link QualityMonitor}
     *
     * @param pw {@link PrintWriter} to write the state of the object.
     * @param prefix String to append at start of dumped log.
     */
    void dump(PrintWriter pw, String prefix) {
        pw.println(prefix + "------------------------------");
        pw.println(
                prefix
                        + "RestrictManager["
                        + QnsUtils.getNameOfNetCapability(mNetCapability)
                        + "_"
                        + mSlotId
                        + "]:");
        pw.println(
                prefix
                        + "mTransportType="
                        + QnsConstants.transportTypeToString(mTransportType)
                        + ", mLastEvaluatedTransportType="
                        + QnsConstants.transportTypeToString(mLastEvaluatedTransportType)
                        + ", mLastDataConnectionTransportType="
                        + QnsConstants.transportTypeToString(mLastDataConnectionTransportType));
        pw.println(
                prefix
                        + "mCounterForIwlanRestrictionInCall="
                        + mCounterForIwlanRestrictionInCall
                        + ", mRetryCounterOnDataConnectionFail="
                        + mRetryCounterOnDataConnectionFail
                        + ", mFallbackCounterOnDataConnectionFail="
                        + mFallbackCounterOnDataConnectionFail);
        pw.println(
                prefix
                        + "mImsCallType="
                        + QnsConstants.callTypeToString(mImsCallType)
                        + ", mCallState="
                        + QnsConstants.callStateToString(mCallState));
        pw.println(prefix + "mRestrictInfos=" + mRestrictInfos);
    }

    private void sendRestrictionsForMetrics() {
        if (mNetCapability != NetworkCapabilities.NET_CAPABILITY_IMS) {
            return;
        }
        ArrayList<Integer> wlanRestrictions =
                new ArrayList<>(
                        mRestrictInfos
                                .get(AccessNetworkConstants.TRANSPORT_TYPE_WLAN)
                                .getRestrictionMap()
                                .keySet());
        ArrayList<Integer> wwanRestrictions =
                new ArrayList<>(
                        mRestrictInfos
                                .get(AccessNetworkConstants.TRANSPORT_TYPE_WLAN)
                                .getRestrictionMap()
                                .keySet());
        mQnsMetrics.reportAtomForRestrictions(mNetCapability, mSlotId,
                wlanRestrictions, wwanRestrictions, mQnsCarrierConfigManager.getCarrierId());
    }
}
