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
package com.android.server.uwb;

import android.content.AttributionSource;
import android.util.SparseArray;
import android.uwb.RangingMeasurement;

import com.android.server.uwb.UwbSessionManager.UwbSession;
import com.android.server.uwb.data.UwbDlTDoAMeasurement;
import com.android.server.uwb.data.UwbOwrAoaMeasurement;
import com.android.server.uwb.data.UwbRangingData;
import com.android.server.uwb.data.UwbTwoWayMeasurement;
import com.android.server.uwb.data.UwbUciConstants;
import com.android.server.uwb.proto.UwbStatsLog;

import com.google.common.collect.ImmutableSet;
import com.google.uwb.support.aliro.AliroOpenRangingParams;
import com.google.uwb.support.base.Params;
import com.google.uwb.support.ccc.CccOpenRangingParams;
import com.google.uwb.support.fira.FiraOpenSessionParams;
import com.google.uwb.support.fira.FiraParams;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayDeque;
import java.util.Calendar;
import java.util.Deque;

/**
 * A class to collect and report UWB metrics.
 */
public class UwbMetrics {
    private static final String TAG = "UwbMetrics";

    private static final int MAX_STATE_CHANGES = 20;
    private static final int MAX_RANGING_SESSIONS = 128;
    private static final int MAX_RANGING_REPORTS = 1024;
    public static final int INVALID_DISTANCE = 0xFFFF;
    private static final int ONE_SECOND_IN_MS = 1000;
    private static final int TEN_SECOND_IN_MS = 10 * 1000;
    private static final int ONE_MIN_IN_MS = 60 * 1000;
    private static final int TEN_MIN_IN_MS = 600 * 1000;
    private static final int ONE_HOUR_IN_MS = 3600 * 1000;
    private static final ImmutableSet<Integer> SUPPORTED_RANGING_MEASUREMENT_TYPES = ImmutableSet
            .of((int) UwbUciConstants.RANGING_MEASUREMENT_TYPE_TWO_WAY,
            (int) UwbUciConstants.RANGING_MEASUREMENT_TYPE_DL_TDOA,
            (int) UwbUciConstants.RANGING_MEASUREMENT_TYPE_OWR_AOA);
    private final UwbInjector mUwbInjector;
    private final Deque<UwbStateChangeInfo> mUwbStateChangeInfoList = new ArrayDeque<>();
    private final Deque<RangingSessionStats> mRangingSessionList = new ArrayDeque<>();
    private final SparseArray<RangingSessionStats> mOpenedSessionMap = new SparseArray<>();
    private final Deque<RangingReportEvent> mRangingReportList = new ArrayDeque<>();
    private int mNumApps = 0;
    private long mLastRangingDataLogTimeMs;
    private final Object mLock = new Object();

    public class UwbStateChangeInfo {
        private boolean mEnable;
        private boolean mSucceeded;
        private long mInitTimeWallClockMs;

        public UwbStateChangeInfo(boolean enable, boolean succeeded) {
            mEnable = enable;
            mSucceeded = succeeded;
            mInitTimeWallClockMs = mUwbInjector.getWallClockMillis();
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("initTime=");
            Calendar c = Calendar.getInstance();
            synchronized (mLock) {
                c.setTimeInMillis(mInitTimeWallClockMs);
                sb.append(mInitTimeWallClockMs == 0 ? "            <null>" :
                        String.format("%tm-%td %tH:%tM:%tS.%tL", c, c, c, c, c, c));
                sb.append(", mEnable=").append(mEnable);
                sb.append(", mSucceeded=").append(mSucceeded);
                return sb.toString();
            }
        }
    }

    /**
     * The class storing the stats of a ranging session.
     */
    public class RangingSessionStats {
        private int mSessionId;
        private int mChannel = 9;
        private long mInitTimeWallClockMs;
        private long mStartTimeSinceBootMs;
        private int mInitLatencyMs;
        private int mInitStatus;
        private int mRangingStatus;
        private int mActiveDuration;
        private int mRangingCount;
        private int mValidRangingCount;
        private boolean mHasValidRangingSinceStart;
        private int mStartCount;
        private int mStartFailureCount;
        private int mStartNoValidReportCount;
        private int mStsType = UwbStatsLog.UWB_SESSION_INITIATED__STS__UNKNOWN_STS;
        private boolean mIsInitiator;
        private boolean mIsController;
        private boolean mIsDiscoveredByFramework = false;
        private boolean mIsOutOfBand = true;
        private int mRangingIntervalMs;
        private int mParallelSessionCount;
        private int mRxPacketCount;
        private int mTxPacketCount;
        private int mRxErrorCount;
        private int mTxErrorCount;
        private int mRxToUpperLayerCount;
        private int mRangingType = UwbStatsLog
                .UWB_RANGING_MEASUREMENT_RECEIVED__RANGING_TYPE__TYPE_UNKNOWN;
        private int mFilterConfigValue = composeFilterConfigValue();
        private AttributionSource mAttributionSource;

        RangingSessionStats(int sessionId, AttributionSource attributionSource,
                int parallelSessionCount) {
            mSessionId = sessionId;
            mInitTimeWallClockMs = mUwbInjector.getWallClockMillis();
            mAttributionSource = attributionSource;
            mParallelSessionCount = parallelSessionCount;
        }

        private int composeFilterConfigValue() {
            DeviceConfigFacade cfg = mUwbInjector.getDeviceConfigFacade();
            int filter_enabled = cfg.isEnableFilters() ? 1 : 0;
            int enable_azimuth_mirroring = (cfg.isEnableBackAzimuth() ? 1 : 0) << 1;
            int enable_primer_aoa = (cfg.isEnablePrimerAoA() ? 1 : 0) << 2;
            int enable_primer_est_elevation = (cfg.isEnablePrimerEstElevation() ? 1 : 0) << 3;
            int enable_primer_fov = (cfg.isEnablePrimerFov() ? 1 : 0) << 4;
            int predict_rear_azimuths = (cfg.isEnableBackAzimuthMasking() ? 1 : 0) << 5;
            return filter_enabled + enable_azimuth_mirroring + enable_primer_aoa
                    + enable_primer_est_elevation + enable_primer_fov + predict_rear_azimuths;
        }

        /**
         * Parse UWB profile parameters
         */
        public void parseParams(Params params) {
            if (params instanceof FiraOpenSessionParams) {
                parseFiraParams((FiraOpenSessionParams) params);
            } else if (params instanceof CccOpenRangingParams) {
                parseCccParams((CccOpenRangingParams) params);
            } else if (params instanceof AliroOpenRangingParams) {
                parseAliroParams((AliroOpenRangingParams) params);
            }
        }

        private void parseFiraParams(FiraOpenSessionParams params) {
            if (params.getStsConfig() == FiraParams.STS_CONFIG_STATIC) {
                mStsType = UwbStatsLog.UWB_SESSION_INITIATED__STS__STATIC;
            } else if (params.getStsConfig() == FiraParams.STS_CONFIG_DYNAMIC) {
                mStsType = UwbStatsLog.UWB_SESSION_INITIATED__STS__DYNAMIC;
            } else {
                mStsType = UwbStatsLog.UWB_SESSION_INITIATED__STS__PROVISIONED;
            }

            mIsInitiator = params.getDeviceRole() == FiraParams.RANGING_DEVICE_ROLE_INITIATOR;
            mIsController = params.getDeviceType() == FiraParams.RANGING_DEVICE_TYPE_CONTROLLER;
            mChannel = params.getChannelNumber();
            mRangingIntervalMs = params.getRangingIntervalMs();
        }

        private void parseCccParams(CccOpenRangingParams params) {
            mChannel = params.getChannel();
        }

        private void parseAliroParams(AliroOpenRangingParams params) {
            mChannel = params.getChannel();
        }

        private void convertInitStatus(int status) {
            mInitStatus = UwbStatsLog.UWB_SESSION_INITIATED__STATUS__GENERAL_FAILURE;
            switch (status) {
                case UwbUciConstants.STATUS_CODE_OK:
                    mInitStatus = UwbStatsLog.UWB_SESSION_INITIATED__STATUS__SUCCESS;
                    break;
                case UwbUciConstants.STATUS_CODE_ERROR_MAX_SESSIONS_EXCEEDED:
                    mInitStatus = UwbStatsLog.UWB_SESSION_INITIATED__STATUS__SESSION_EXCEEDED;
                    break;
                case UwbUciConstants.STATUS_CODE_ERROR_SESSION_DUPLICATE:
                    mInitStatus = UwbStatsLog.UWB_SESSION_INITIATED__STATUS__SESSION_DUPLICATE;
                    break;
                case UwbUciConstants.STATUS_CODE_INVALID_PARAM:
                case UwbUciConstants.STATUS_CODE_INVALID_RANGE:
                case UwbUciConstants.STATUS_CODE_INVALID_MESSAGE_SIZE:
                    mInitStatus = UwbStatsLog.UWB_SESSION_INITIATED__STATUS__BAD_PARAMS;
                    break;
            }
        }

        private void convertRangingStatus(int status) {
            mRangingStatus = UwbStatsLog.UWB_START_RANGING__STATUS__RANGING_GENERAL_FAILURE;
            switch (status) {
                case UwbUciConstants.STATUS_CODE_OK:
                case UwbUciConstants.STATUS_CODE_OK_NEGATIVE_DISTANCE_REPORT:
                    mRangingStatus = UwbStatsLog.UWB_START_RANGING__STATUS__RANGING_SUCCESS;
                    break;
                case UwbUciConstants.STATUS_CODE_RANGING_TX_FAILED:
                    mRangingStatus = UwbStatsLog.UWB_START_RANGING__STATUS__TX_FAILED;
                    break;
                case UwbUciConstants.STATUS_CODE_RANGING_RX_PHY_DEC_FAILED:
                    mRangingStatus = UwbStatsLog.UWB_START_RANGING__STATUS__RX_PHY_DEC_FAILED;
                    break;
                case UwbUciConstants.STATUS_CODE_RANGING_RX_PHY_TOA_FAILED:
                    mRangingStatus = UwbStatsLog.UWB_START_RANGING__STATUS__RX_PHY_TOA_FAILED;
                    break;
                case UwbUciConstants.STATUS_CODE_RANGING_RX_PHY_STS_FAILED:
                    mRangingStatus = UwbStatsLog.UWB_START_RANGING__STATUS__RX_PHY_STS_FAILED;
                    break;
                case UwbUciConstants.STATUS_CODE_RANGING_RX_MAC_DEC_FAILED:
                    mRangingStatus = UwbStatsLog.UWB_START_RANGING__STATUS__RX_MAC_DEC_FAILED;
                    break;
                case UwbUciConstants.STATUS_CODE_RANGING_RX_MAC_IE_DEC_FAILED:
                    mRangingStatus = UwbStatsLog.UWB_START_RANGING__STATUS__RX_MAC_IE_DEC_FAILED;
                    break;
                case UwbUciConstants.STATUS_CODE_RANGING_RX_MAC_IE_MISSING:
                    mRangingStatus = UwbStatsLog.UWB_START_RANGING__STATUS__RX_MAC_IE_MISSING;
                    break;
                case UwbUciConstants.STATUS_CODE_INVALID_PARAM:
                case UwbUciConstants.STATUS_CODE_INVALID_RANGE:
                case UwbUciConstants.STATUS_CODE_INVALID_MESSAGE_SIZE:
                    mRangingStatus = UwbStatsLog.UWB_START_RANGING__STATUS__RANGING_BAD_PARAMS;
                    break;
            }
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("initTime=");
            Calendar c = Calendar.getInstance();
            synchronized (mLock) {
                c.setTimeInMillis(mInitTimeWallClockMs);
                sb.append(mInitTimeWallClockMs == 0 ? "            <null>" :
                        String.format("%tm-%td %tH:%tM:%tS.%tL", c, c, c, c, c, c));
                sb.append(", sessionId=").append(mSessionId);
                sb.append(", initLatencyMs=").append(mInitLatencyMs);
                sb.append(", activeDurationMs=").append(mActiveDuration);
                sb.append(", rangingCount=").append(mRangingCount);
                sb.append(", validRangingCount=").append(mValidRangingCount);
                sb.append(", startCount=").append(mStartCount);
                sb.append(", startFailureCount=").append(mStartFailureCount);
                sb.append(", startNoValidReportCount=").append(mStartNoValidReportCount);
                sb.append(", initStatus=").append(mInitStatus);
                sb.append(", channel=").append(mChannel);
                sb.append(", initiator=").append(mIsInitiator);
                sb.append(", controller=").append(mIsController);
                sb.append(", discoveredByFramework=").append(mIsDiscoveredByFramework);
                sb.append(", uid=").append(mAttributionSource.getUid());
                sb.append(", packageName=").append(mAttributionSource.getPackageName());
                sb.append(", rangingIntervalMs=").append(mRangingIntervalMs);
                sb.append(", parallelSessionCount=").append(mParallelSessionCount);
                sb.append(", rxPacketCount=").append(mRxPacketCount);
                sb.append(", txPacketCount=").append(mTxPacketCount);
                sb.append(", rxErrorCount=").append(mRxErrorCount);
                sb.append(", txErrorCount=").append(mTxErrorCount);
                sb.append(", rxToUpperLayerCount=").append(mRxToUpperLayerCount);
                sb.append(", rangingType=").append(mRangingType);
                return sb.toString();
            }
        }
    }

    private class RangingReportEvent {
        private int mSessionId;
        private int mNlos;
        private int mDistanceCm = INVALID_DISTANCE;
        private int mAzimuthDegree;
        private int mAzimuthFom;
        private int mElevationDegree;
        private int mElevationFom;
        private int mRssiDbm = RangingMeasurement.RSSI_UNKNOWN;
        private int mRangingType;
        private int mFilteredDistanceCm = INVALID_DISTANCE;
        private int mFilteredAzimuthDegree;
        private int mFilteredAzimuthFom;
        private int mFilteredElevationDegree;
        private int mFilteredElevationFom;
        private long mWallClockMillis = mUwbInjector.getWallClockMillis();;
        private boolean mIsStatusOk;

        RangingReportEvent(UwbTwoWayMeasurement measurement) {
            mNlos = convertNlos(measurement.getNLoS());
            mDistanceCm = measurement.getDistance();
            mAzimuthDegree = (int) measurement.getAoaAzimuth();
            mAzimuthFom = measurement.getAoaAzimuthFom();
            mElevationDegree = (int) measurement.getAoaElevation();
            mElevationFom = measurement.getAoaElevationFom();
            mRssiDbm = measurement.getRssi();
            mRangingType = UwbStatsLog.UWB_RANGING_MEASUREMENT_RECEIVED__RANGING_TYPE__TWO_WAY;
            mIsStatusOk = measurement.isStatusCodeOk();
        }

        RangingReportEvent(UwbDlTDoAMeasurement measurement) {
            mNlos = convertNlos(measurement.getNLoS());
            mAzimuthDegree = (int) measurement.getAoaAzimuth();
            mAzimuthFom = measurement.getAoaAzimuthFom();
            mElevationDegree = (int) measurement.getAoaElevation();
            mElevationFom = measurement.getAoaElevationFom();
            mRssiDbm = measurement.getRssi();
            mRangingType = UwbStatsLog.UWB_RANGING_MEASUREMENT_RECEIVED__RANGING_TYPE__DL_TDOA;
            mIsStatusOk = measurement.getStatus() == UwbUciConstants.STATUS_CODE_OK;
        }

        RangingReportEvent(UwbOwrAoaMeasurement measurement) {
            mNlos = convertNlos(measurement.getNLoS());
            mAzimuthDegree = (int) measurement.getAoaAzimuth();
            mAzimuthFom = measurement.getAoaAzimuthFom();
            mElevationDegree = (int) measurement.getAoaElevation();
            mElevationFom = measurement.getAoaElevationFom();
            mRangingType = UwbStatsLog.UWB_RANGING_MEASUREMENT_RECEIVED__RANGING_TYPE__OWR_AOA;
            mIsStatusOk = measurement.getRangingStatus() == UwbUciConstants.STATUS_CODE_OK;
        }

        private void addFilteredResults(RangingMeasurement filteredRangingMeasurement) {
            if (filteredRangingMeasurement == null) {
                return;
            }
            if (filteredRangingMeasurement.getDistanceMeasurement() != null) {
                mFilteredDistanceCm = (int) (filteredRangingMeasurement
                        .getDistanceMeasurement().getMeters() * 100);
            }
            if (filteredRangingMeasurement.getAngleOfArrivalMeasurement() != null) {
                mFilteredAzimuthDegree = (int) Math.toDegrees(filteredRangingMeasurement
                        .getAngleOfArrivalMeasurement().getAzimuth().getRadians());
                mFilteredAzimuthFom = (int) (filteredRangingMeasurement
                        .getAngleOfArrivalMeasurement().getAzimuth()
                        .getConfidenceLevel() * 100);
                mFilteredElevationDegree = (int) Math.toDegrees(filteredRangingMeasurement
                        .getAngleOfArrivalMeasurement().getAltitude().getRadians());
                mFilteredElevationFom = (int) (filteredRangingMeasurement
                        .getAngleOfArrivalMeasurement().getAltitude()
                        .getConfidenceLevel() * 100);
            }
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("time=");
            Calendar c = Calendar.getInstance();
            synchronized (mLock) {
                c.setTimeInMillis(mWallClockMillis);
                sb.append(mWallClockMillis == 0 ? "            <null>" :
                        String.format("%tm-%td %tH:%tM:%tS.%tL", c, c, c, c, c, c));
                sb.append(", sessionId=").append(mSessionId);
                sb.append(", Nlos=").append(mNlos);
                sb.append(", DistanceCm=").append(mDistanceCm);
                sb.append(", AzimuthDegree=").append(mAzimuthDegree);
                sb.append(", AzimuthFom=").append(mAzimuthFom);
                sb.append(", ElevationDegree=").append(mElevationDegree);
                sb.append(", ElevationFom=").append(mElevationFom);
                sb.append(", RssiDbm=").append(mRssiDbm);
                sb.append(", FilteredDistanceCm=").append(mFilteredDistanceCm);
                sb.append(", FilteredAzimuthDegree=").append(mFilteredAzimuthDegree);
                sb.append(", FilteredAzimuthFom=").append(mFilteredAzimuthFom);
                sb.append(", FilteredElevationDegree=").append(mFilteredElevationDegree);
                sb.append(", FilteredElevationFom=").append(mFilteredElevationFom);
                sb.append(", RangingType=").append(mRangingType);
                return sb.toString();
            }
        }
    }

    public UwbMetrics(UwbInjector uwbInjector) {
        mUwbInjector = uwbInjector;
    }

    /**
     * Log UWB state change event.
     */
    public void logUwbStateChangeEvent(boolean enable, boolean succeeded,
            boolean isFirstInitAttempt) {
        synchronized (mLock) {
            // If past maximum events, start removing the oldest
            while (mUwbStateChangeInfoList.size() >= MAX_STATE_CHANGES) {
                mUwbStateChangeInfoList.removeFirst();
            }
            UwbStateChangeInfo uwbStateChangeInfo = new UwbStateChangeInfo(enable, succeeded);
            mUwbStateChangeInfoList.add(uwbStateChangeInfo);
            if (enable) {
                if (succeeded) {
                    incrementDeviceInitSuccessCount();
                } else {
                    incrementDeviceInitFailureCount(isFirstInitAttempt);
                }
            }
        }
    }

    /**
     * Log the ranging session initialization event
     */
    public void logRangingInitEvent(UwbSession uwbSession, int status) {
        synchronized (mLock) {
            // If past maximum events, start removing the oldest
            while (mRangingSessionList.size() >= MAX_RANGING_SESSIONS) {
                mRangingSessionList.removeFirst();
            }
            RangingSessionStats session = new RangingSessionStats(uwbSession.getSessionId(),
                    uwbSession.getAttributionSource(), uwbSession.getParallelSessionCount());
            session.parseParams(uwbSession.getParams());
            session.convertInitStatus(status);
            mRangingSessionList.add(session);
            mOpenedSessionMap.put(uwbSession.getSessionId(), session);
            if (status != UwbUciConstants.STATUS_CODE_OK) {
                takBugReportSessionInitError("UWB Bugreport: session init failed reason " + status);
            }
            UwbStatsLog.write(UwbStatsLog.UWB_SESSION_INITED, uwbSession.getProfileType(),
                    session.mStsType, session.mIsInitiator,
                    session.mIsController, session.mIsDiscoveredByFramework, session.mIsOutOfBand,
                    session.mChannel, session.mInitStatus,
                    session.mInitLatencyMs, session.mInitLatencyMs / 20,
                    uwbSession.getAttributionSource().getUid(), session.mRangingIntervalMs,
                    session.mParallelSessionCount, session.mFilterConfigValue
            );
        }
    }

    /**
     * Log the ranging session start event
     */
    public void longRangingStartEvent(UwbSession uwbSession, int status) {
        synchronized (mLock) {
            RangingSessionStats session = mOpenedSessionMap.get(uwbSession.getSessionId());
            if (session == null) {
                return;
            }
            session.mStartCount++;
            session.convertRangingStatus(status);
            UwbStatsLog.write(UwbStatsLog.UWB_RANGING_START, uwbSession.getProfileType(),
                    session.mStsType, session.mIsInitiator,
                    session.mIsController, session.mIsDiscoveredByFramework, session.mIsOutOfBand,
                    session.mRangingStatus);
            if (status != UwbUciConstants.STATUS_CODE_OK) {
                session.mStartFailureCount++;
                session.mStartTimeSinceBootMs = 0;
                session.mHasValidRangingSinceStart = false;
                return;
            }
            session.mStartTimeSinceBootMs = mUwbInjector.getElapsedSinceBootMillis();
        }
    }

    private void takBugReportSessionInitError(String bugTitle) {
        if (mUwbInjector.getDeviceConfigFacade().isSessionInitErrorBugreportEnabled()) {
            mUwbInjector.getUwbDiagnostics().takeBugReport(bugTitle);
        }
    }

    /**
     * Log the ranging session stop event
     */
    public void longRangingStopEvent(UwbSession uwbSession) {
        synchronized (mLock) {
            RangingSessionStats session = mOpenedSessionMap.get(uwbSession.getSessionId());
            if (session == null) {
                return;
            }
            if (session.mStartTimeSinceBootMs == 0) {
                return;
            }
            if (!session.mHasValidRangingSinceStart) {
                session.mStartNoValidReportCount++;
            }
            session.mHasValidRangingSinceStart = false;
            session.mActiveDuration += (int) (mUwbInjector.getElapsedSinceBootMillis()
                    - session.mStartTimeSinceBootMs);
            session.mStartTimeSinceBootMs = 0;
        }
    }

    /**
     * Log the ranging session close event
     */
    public void logRangingCloseEvent(UwbSession uwbSession, int status) {
        synchronized (mLock) {
            RangingSessionStats session = mOpenedSessionMap.get(uwbSession.getSessionId());
            if (session == null) {
                return;
            }
            if (status != UwbUciConstants.STATUS_CODE_OK) {
                return;
            }
            // Ranging may close without stop event
            if (session.mStartTimeSinceBootMs != 0) {
                session.mActiveDuration += (int) (mUwbInjector.getElapsedSinceBootMillis()
                        - session.mStartTimeSinceBootMs);
                if (!session.mHasValidRangingSinceStart) {
                    session.mStartNoValidReportCount++;
                }
                session.mStartTimeSinceBootMs = 0;
                session.mHasValidRangingSinceStart = false;
            }

            UwbStatsLog.write(UwbStatsLog.UWB_SESSION_CLOSED, uwbSession.getProfileType(),
                    session.mStsType, session.mIsInitiator,
                    session.mIsController, session.mIsDiscoveredByFramework, session.mIsOutOfBand,
                    session.mActiveDuration, getDurationBucket(session.mActiveDuration),
                    session.mRangingCount, session.mValidRangingCount,
                    getCountBucket(session.mRangingCount),
                    getCountBucket(session.mValidRangingCount),
                    session.mStartCount,
                    session.mStartFailureCount,
                    session.mStartNoValidReportCount,
                    session.mRxPacketCount, session.mTxPacketCount, session.mRxErrorCount,
                    session.mTxErrorCount, session.mRxToUpperLayerCount, session.mRangingType);
            mOpenedSessionMap.delete(uwbSession.getSessionId());
        }
    }

    private int getDurationBucket(int durationMs) {
        if (durationMs <= ONE_SECOND_IN_MS) {
            return UwbStatsLog.UWB_SESSION_CLOSED__DURATION_BUCKET__WITHIN_ONE_SEC;
        } else if (durationMs <= TEN_SECOND_IN_MS) {
            return UwbStatsLog.UWB_SESSION_CLOSED__DURATION_BUCKET__ONE_TO_TEN_SEC;
        } else if (durationMs <= ONE_MIN_IN_MS) {
            return UwbStatsLog.UWB_SESSION_CLOSED__DURATION_BUCKET__TEN_SEC_TO_ONE_MIN;
        } else if (durationMs <= TEN_MIN_IN_MS) {
            return UwbStatsLog.UWB_SESSION_CLOSED__DURATION_BUCKET__ONE_TO_TEN_MIN;
        } else if (durationMs <= ONE_HOUR_IN_MS) {
            return UwbStatsLog.UWB_SESSION_CLOSED__DURATION_BUCKET__TEN_MIN_TO_ONE_HOUR;
        } else {
            return UwbStatsLog.UWB_SESSION_CLOSED__DURATION_BUCKET__MORE_THAN_ONE_HOUR;
        }
    }

    private int getCountBucket(int count) {
        if (count <= 0) {
            return UwbStatsLog.UWB_SESSION_CLOSED__RANGING_COUNT_BUCKET__ZERO;
        } else if (count <= 5) {
            return UwbStatsLog.UWB_SESSION_CLOSED__RANGING_COUNT_BUCKET__ONE_TO_FIVE;
        } else if (count <= 20) {
            return UwbStatsLog.UWB_SESSION_CLOSED__RANGING_COUNT_BUCKET__FIVE_TO_TWENTY;
        } else if (count <= 100) {
            return UwbStatsLog.UWB_SESSION_CLOSED__RANGING_COUNT_BUCKET__TWENTY_TO_ONE_HUNDRED;
        } else if (count <= 500) {
            return UwbStatsLog
                    .UWB_SESSION_CLOSED__RANGING_COUNT_BUCKET__ONE_HUNDRED_TO_FIVE_HUNDRED;
        } else {
            return UwbStatsLog.UWB_SESSION_CLOSED__RANGING_COUNT_BUCKET__MORE_THAN_FIVE_HUNDRED;
        }
    }

    /**
     * Log the usage of API from a new App
     */
    public void logNewAppUsage() {
        synchronized (mLock) {
            mNumApps++;
        }
    }

    /**
     * Log the ranging measurement result
     */
    public void logRangingResult(int profileType, UwbRangingData rawRangingData,
            RangingMeasurement filteredRangingMeasurement) {
        synchronized (mLock) {
            int rangingMeasuresType = rawRangingData.getRangingMeasuresType();
            if (!SUPPORTED_RANGING_MEASUREMENT_TYPES.contains(rangingMeasuresType)
                    || rawRangingData.getNoOfRangingMeasures() < 1) {
                return;
            }

            int sessionId = (int) rawRangingData.getSessionId();
            RangingSessionStats session = mOpenedSessionMap.get(sessionId);
            if (session == null) {
                return;
            }
            session.mRangingCount++;

            RangingReportEvent report = getRangingReport(rangingMeasuresType, rawRangingData);
            if (report == null) {
                return;
            }
            report.mSessionId = sessionId;
            session.mRangingType = report.mRangingType;

            if (!report.mIsStatusOk) {
                return;
            }
            report.addFilteredResults(filteredRangingMeasurement);

            session.mValidRangingCount++;
            if (!session.mHasValidRangingSinceStart) {
                session.mHasValidRangingSinceStart = true;
                writeFirstValidRangingResultSinceStart(profileType, session);
            }

            while (mRangingReportList.size() >= MAX_RANGING_REPORTS) {
                mRangingReportList.removeFirst();
            }
            mRangingReportList.add(report);

            long currTimeMs = mUwbInjector.getElapsedSinceBootMillis();
            if ((currTimeMs - mLastRangingDataLogTimeMs) < mUwbInjector.getDeviceConfigFacade()
                    .getRangingResultLogIntervalMs()) {
                return;
            }
            mLastRangingDataLogTimeMs = currTimeMs;

            boolean isDistanceValid = report.mDistanceCm != INVALID_DISTANCE;
            boolean isAzimuthValid = report.mAzimuthFom > 0;
            boolean isElevationValid = report.mElevationFom > 0;
            int distance50Cm = isDistanceValid ? report.mDistanceCm / 50 : 0;
            int azimuth10Degree = isAzimuthValid ? report.mAzimuthDegree / 10 : 0;
            int elevation10Degree = isElevationValid ? report.mElevationDegree / 10 : 0;
            UwbStatsLog.write(UwbStatsLog.UWB_RANGING_MEASUREMENT_RECEIVED,
                    profileType, report.mNlos,
                    isDistanceValid, report.mDistanceCm, distance50Cm, report.mRssiDbm,
                    isAzimuthValid, report.mAzimuthDegree, azimuth10Degree, report.mAzimuthFom,
                    isElevationValid, report.mElevationDegree, elevation10Degree,
                    report.mElevationFom, session.mRangingType, report.mFilteredDistanceCm,
                    report.mFilteredAzimuthDegree, report.mFilteredAzimuthFom,
                    report.mFilteredElevationDegree, report.mFilteredElevationFom);
        }
    }

    private void writeFirstValidRangingResultSinceStart(int profileType,
            RangingSessionStats session) {
        int latencyMs = (int) (mUwbInjector.getElapsedSinceBootMillis()
                - session.mStartTimeSinceBootMs);
        UwbStatsLog.write(UwbStatsLog.UWB_FIRST_RANGING_RECEIVED,
                profileType, latencyMs, latencyMs / 200);
    }

    private int convertNlos(int nlos) {
        if (nlos == 0) {
            return UwbStatsLog.UWB_RANGING_MEASUREMENT_RECEIVED__NLOS__LOS;
        } else if (nlos == 1) {
            return UwbStatsLog.UWB_RANGING_MEASUREMENT_RECEIVED__NLOS__NLOS;
        } else {
            return UwbStatsLog.UWB_RANGING_MEASUREMENT_RECEIVED__NLOS__NLOS_UNKNOWN;
        }
    }

    private RangingReportEvent getRangingReport(int rangingType, UwbRangingData rangingData) {
        switch (rangingType) {
            case UwbUciConstants.RANGING_MEASUREMENT_TYPE_TWO_WAY:
                UwbTwoWayMeasurement[] uwbTwoWayMeasurements =
                        rangingData.getRangingTwoWayMeasures();
                return new RangingReportEvent(uwbTwoWayMeasurements[0]);
            case UwbUciConstants.RANGING_MEASUREMENT_TYPE_DL_TDOA:
                UwbDlTDoAMeasurement[] uwbDlTDoAMeasurements =
                        rangingData.getUwbDlTDoAMeasurements();
                return new RangingReportEvent(uwbDlTDoAMeasurements[0]);
            case UwbUciConstants.RANGING_MEASUREMENT_TYPE_OWR_AOA:
                return new RangingReportEvent(rangingData.getRangingOwrAoaMeasure());
            default:
                return null;
        }
    }

    /**
     * Log Rx data packet count
     */
    public synchronized void logDataRx(UwbSession uwbSession, int status) {
        synchronized (mLock) {
            RangingSessionStats session = mOpenedSessionMap.get(uwbSession.getSessionId());
            if (session == null) {
                return;
            }
            if (status == UwbUciConstants.STATUS_CODE_OK) {
                session.mRxPacketCount++;
            } else {
                session.mRxErrorCount++;
            }
        }
    }

    /**
     * Log Tx data packet count
     */
    public synchronized void logDataTx(UwbSession uwbSession, int status) {
        synchronized (mLock) {
            RangingSessionStats session = mOpenedSessionMap.get(uwbSession.getSessionId());
            if (session == null) {
                return;
            }
            if (status == UwbUciConstants.STATUS_CODE_OK) {
                session.mTxPacketCount++;
            } else {
                session.mTxErrorCount++;
            }
        }
    }

    /**
     * Log count of Rx data packets sent to upper layer
     */
    public synchronized void logDataToUpperLayer(UwbSession uwbSession, int packetCount) {
        synchronized (mLock) {
            RangingSessionStats session = mOpenedSessionMap.get(uwbSession.getSessionId());
            if (session == null) {
                return;
            }
            session.mRxToUpperLayerCount += packetCount;
        }
    }

    private int mNumDeviceInitSuccess = 0;
    private int mNumDeviceInitFailure = 0;
    private boolean mFirstDeviceInitFailure = false;
    private int mNumDeviceStatusError = 0;
    private int mNumUciGenericError = 0;

    /**
     * Increment the count of device initialization success
     */
    private void incrementDeviceInitSuccessCount() {
        mNumDeviceInitSuccess++;
    }

    /**
     * Increment the count of device initialization failure
     */
    private void incrementDeviceInitFailureCount(boolean isFirstInitAttempt) {
        mNumDeviceInitFailure++;
        if (isFirstInitAttempt) {
            mFirstDeviceInitFailure = true;
        } else {
            UwbStatsLog.write(UwbStatsLog.UWB_DEVICE_ERROR_REPORTED,
                    UwbStatsLog.UWB_DEVICE_ERROR_REPORTED__TYPE__INIT_ERROR);
        }
    }

    /**
     * Increment the count of device status error
     */
    public synchronized void incrementDeviceStatusErrorCount() {
        mNumDeviceStatusError++;
        UwbStatsLog.write(UwbStatsLog.UWB_DEVICE_ERROR_REPORTED,
                UwbStatsLog.UWB_DEVICE_ERROR_REPORTED__TYPE__DEVICE_STATUS_ERROR);
    }

    /**
     * Increment the count of UCI generic error which will trigger UCI command retry
     */
    public synchronized void incrementUciGenericErrorCount() {
        mNumUciGenericError++;
        UwbStatsLog.write(UwbStatsLog.UWB_DEVICE_ERROR_REPORTED,
                UwbStatsLog.UWB_DEVICE_ERROR_REPORTED__TYPE__UCI_GENERIC_ERROR);
    }

    /**
     * Dump the UWB logs
     */
    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        synchronized (mLock) {
            pw.println("---- Dump of UwbMetrics ----");
            pw.println("-- mUwbStateChangeInfoList --");
            for (UwbStateChangeInfo stateChangeInfo: mUwbStateChangeInfoList) {
                pw.println(stateChangeInfo.toString());
            }
            pw.println("-- mRangingSessionList --");
            for (RangingSessionStats stats: mRangingSessionList) {
                pw.println(stats.toString());
            }
            pw.println("-- mOpenedSessionMap --");
            for (int i = 0; i < mOpenedSessionMap.size(); i++) {
                pw.println(mOpenedSessionMap.valueAt(i).toString());
            }
            pw.println("-- mRangingReportList --");
            for (RangingReportEvent event: mRangingReportList) {
                pw.println(event.toString());
            }
            pw.println("mNumApps=" + mNumApps);
            pw.println("-- Device operation success/error count --");
            pw.println("mNumDeviceInitSuccess = " + mNumDeviceInitSuccess);
            pw.println("mNumDeviceInitFailure = " + mNumDeviceInitFailure);
            pw.println("mFirstDeviceInitFailure = " + mFirstDeviceInitFailure);
            pw.println("mNumDeviceStatusError = " + mNumDeviceStatusError);
            pw.println("mNumUciGenericError = " + mNumUciGenericError);
            pw.println("---- Dump of UwbMetrics ----");
        }
    }
}
