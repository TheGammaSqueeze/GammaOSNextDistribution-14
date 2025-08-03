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

import static android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE;

import static com.android.server.uwb.data.UwbUciConstants.MAC_ADDRESSING_MODE_EXTENDED;
import static com.android.server.uwb.data.UwbUciConstants.MAC_ADDRESSING_MODE_SHORT;
import static com.android.server.uwb.data.UwbUciConstants.RANGING_DEVICE_ROLE_OBSERVER;
import static com.android.server.uwb.data.UwbUciConstants.REASON_STATE_CHANGE_WITH_SESSION_MANAGEMENT_COMMANDS;
import static com.android.server.uwb.data.UwbUciConstants.ROUND_USAGE_OWR_AOA_MEASUREMENT;
import static com.android.server.uwb.data.UwbUciConstants.STATUS_CODE_OK;
import static com.android.server.uwb.data.UwbUciConstants.UWB_DEVICE_EXT_MAC_ADDRESS_LEN;
import static com.android.server.uwb.data.UwbUciConstants.UWB_DEVICE_SHORT_MAC_ADDRESS_LEN;
import static com.android.server.uwb.data.UwbUciConstants.UWB_SESSION_STATE_ACTIVE;
import static com.android.server.uwb.util.DataTypeConversionUtil.macAddressByteArrayToLong;

import static com.google.uwb.support.fira.FiraParams.FILTER_TYPE_APPLICATION;
import static com.google.uwb.support.fira.FiraParams.FILTER_TYPE_DEFAULT;
import static com.google.uwb.support.fira.FiraParams.FILTER_TYPE_NONE;
import static com.google.uwb.support.fira.FiraParams.MULTICAST_LIST_UPDATE_ACTION_ADD;
import static com.google.uwb.support.fira.FiraParams.MULTICAST_LIST_UPDATE_ACTION_DELETE;
import static com.google.uwb.support.fira.FiraParams.PROTOCOL_NAME;
import static com.google.uwb.support.fira.FiraParams.P_STS_MULTICAST_LIST_UPDATE_ACTION_ADD_16_BYTE;
import static com.google.uwb.support.fira.FiraParams.P_STS_MULTICAST_LIST_UPDATE_ACTION_ADD_32_BYTE;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.ActivityManager;
import android.app.AlarmManager;
import android.content.AttributionSource;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.PersistableBundle;
import android.os.RemoteException;
import android.os.Trace;
import android.util.Log;
import android.util.Pair;
import android.uwb.IUwbAdapter;
import android.uwb.IUwbRangingCallbacks;
import android.uwb.RangingChangeReason;
import android.uwb.SessionHandle;
import android.uwb.UwbAddress;

import androidx.annotation.VisibleForTesting;

import com.android.modules.utils.build.SdkLevel;
import com.android.server.uwb.advertisement.UwbAdvertiseManager;
import com.android.server.uwb.correction.UwbFilterEngine;
import com.android.server.uwb.correction.pose.ApplicationPoseSource;
import com.android.server.uwb.correction.pose.IPoseSource;
import com.android.server.uwb.data.DtTagUpdateRangingRoundsStatus;
import com.android.server.uwb.data.UwbDeviceInfoResponse;
import com.android.server.uwb.data.UwbDlTDoAMeasurement;
import com.android.server.uwb.data.UwbMulticastListUpdateStatus;
import com.android.server.uwb.data.UwbOwrAoaMeasurement;
import com.android.server.uwb.data.UwbRadarData;
import com.android.server.uwb.data.UwbRangingData;
import com.android.server.uwb.data.UwbTwoWayMeasurement;
import com.android.server.uwb.data.UwbUciConstants;
import com.android.server.uwb.jni.INativeUwbManager;
import com.android.server.uwb.jni.NativeUwbManager;
import com.android.server.uwb.params.TlvUtil;
import com.android.server.uwb.proto.UwbStatsLog;
import com.android.server.uwb.util.ArrayUtils;
import com.android.server.uwb.util.DataTypeConversionUtil;
import com.android.server.uwb.util.LruList;
import com.android.server.uwb.util.UwbUtil;

import com.google.uwb.support.aliro.AliroOpenRangingParams;
import com.google.uwb.support.aliro.AliroParams;
import com.google.uwb.support.aliro.AliroRangingStartedParams;
import com.google.uwb.support.aliro.AliroRangingStoppedParams;
import com.google.uwb.support.aliro.AliroSpecificationParams;
import com.google.uwb.support.aliro.AliroStartRangingParams;
import com.google.uwb.support.base.Params;
import com.google.uwb.support.ccc.CccOpenRangingParams;
import com.google.uwb.support.ccc.CccParams;
import com.google.uwb.support.ccc.CccRangingStartedParams;
import com.google.uwb.support.ccc.CccRangingStoppedParams;
import com.google.uwb.support.ccc.CccSpecificationParams;
import com.google.uwb.support.ccc.CccStartRangingParams;
import com.google.uwb.support.dltdoa.DlTDoARangingRoundsUpdate;
import com.google.uwb.support.dltdoa.DlTDoARangingRoundsUpdateStatus;
import com.google.uwb.support.fira.FiraDataTransferPhaseConfig;
import com.google.uwb.support.fira.FiraDataTransferPhaseConfig.FiraDataTransferPhaseManagementList;
import com.google.uwb.support.fira.FiraDataTransferPhaseConfigStatusCode;
import com.google.uwb.support.fira.FiraHybridSessionConfig;
import com.google.uwb.support.fira.FiraOpenSessionParams;
import com.google.uwb.support.fira.FiraParams;
import com.google.uwb.support.fira.FiraPoseUpdateParams;
import com.google.uwb.support.fira.FiraProtocolVersion;
import com.google.uwb.support.fira.FiraRangingReconfigureParams;
import com.google.uwb.support.fira.FiraSpecificationParams;
import com.google.uwb.support.generic.GenericSpecificationParams;
import com.google.uwb.support.oemextension.AdvertisePointedTarget;
import com.google.uwb.support.oemextension.SessionConfigParams;
import com.google.uwb.support.oemextension.SessionStatus;

import java.io.Closeable;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

public class UwbSessionManager implements INativeUwbManager.SessionNotification,
        ActivityManager.OnUidImportanceListener {

    private static final String TAG = "UwbSessionManager";
    private static final byte OPERATION_TYPE_INIT_SESSION = 0;
    private static final int UWB_HUS_PHASE_SIZE = 8;

    @VisibleForTesting
    public static final int SESSION_OPEN_RANGING = 1;
    @VisibleForTesting
    public static final int SESSION_START_RANGING = 2;
    @VisibleForTesting
    public static final int SESSION_STOP_RANGING = 3;
    @VisibleForTesting
    public static final int SESSION_RECONFIG_RANGING = 4;
    @VisibleForTesting
    public static final int SESSION_DEINIT = 5;
    @VisibleForTesting
    public static final int SESSION_ON_DEINIT = 6;
    @VisibleForTesting
    public static final int SESSION_SEND_DATA = 7;
    @VisibleForTesting
    public static final int SESSION_UPDATE_DT_TAG_RANGING_ROUNDS = 8;
    @VisibleForTesting
    public static final int SESSION_DATA_TRANSFER_PHASE_CONFIG = 11;

    // TODO: don't expose the internal field for testing.
    @VisibleForTesting
    final ConcurrentHashMap<SessionHandle, UwbSession> mSessionTable = new ConcurrentHashMap();
    // Used for storing recently closed sessions for debugging purposes.
    final LruList<UwbSession> mDbgRecentlyClosedSessions = new LruList<>(5);
    final ConcurrentHashMap<Integer, List<UwbSession>> mNonPrivilegedUidToFiraSessionsTable =
            new ConcurrentHashMap();
    final ConcurrentHashMap<Integer, Integer> mSessionTokenMap = new ConcurrentHashMap<>();
    private final ActivityManager mActivityManager;
    private final NativeUwbManager mNativeUwbManager;
    private final UwbMetrics mUwbMetrics;
    private final UwbConfigurationManager mConfigurationManager;
    private final UwbSessionNotificationManager mSessionNotificationManager;
    private final UwbAdvertiseManager mAdvertiseManager;
    private final UwbInjector mUwbInjector;
    private final AlarmManager mAlarmManager;
    private final Looper mLooper;
    private final EventTask mEventTask;

    public UwbSessionManager(
            UwbConfigurationManager uwbConfigurationManager,
            NativeUwbManager nativeUwbManager, UwbMetrics uwbMetrics,
            UwbAdvertiseManager uwbAdvertiseManager,
            UwbSessionNotificationManager uwbSessionNotificationManager,
            UwbInjector uwbInjector, AlarmManager alarmManager, ActivityManager activityManager,
            Looper serviceLooper) {
        mNativeUwbManager = nativeUwbManager;
        mNativeUwbManager.setSessionListener(this);
        mUwbMetrics = uwbMetrics;
        mAdvertiseManager = uwbAdvertiseManager;
        mConfigurationManager = uwbConfigurationManager;
        mSessionNotificationManager = uwbSessionNotificationManager;
        mUwbInjector = uwbInjector;
        mAlarmManager = alarmManager;
        mActivityManager = activityManager;
        mLooper = serviceLooper;
        mEventTask = new EventTask(serviceLooper);
        registerUidImportanceTransitions();
    }

    @Override
    public void onUidImportance(final int uid, final int importance) {
        Handler handler = new Handler(mLooper);
        handler.post(() -> {
            List<UwbSession> uwbSessions = mNonPrivilegedUidToFiraSessionsTable.get(uid);
            // Not a uid in the watch list
            if (uwbSessions == null) return;
            boolean newModeHasNonPrivilegedFgAppOrService =
                    UwbInjector.isForegroundAppOrServiceImportance(importance);
            for (UwbSession uwbSession : uwbSessions) {
                // already at correct state.
                if (newModeHasNonPrivilegedFgAppOrService
                        == uwbSession.hasNonPrivilegedFgAppOrService()) {
                    continue;
                }
                uwbSession.setHasNonPrivilegedFgAppOrService(newModeHasNonPrivilegedFgAppOrService);
                int sessionId = uwbSession.getSessionId();
                Log.i(TAG, "App state change for session " + sessionId + ". IsFg: "
                        + newModeHasNonPrivilegedFgAppOrService);
                // Reconfigure the session based on the new fg/bg state
                Log.i(TAG, "Session " + sessionId
                        + " reconfiguring ntf control due to app state change");
                uwbSession.reconfigureFiraSessionOnFgStateChange();
                // Recalculate session priority based on the new fg/bg state.
                if (!uwbSession.mSessionPriorityOverride) {
                    int newSessionPriority = uwbSession.calculateSessionPriority();
                    Log.i(TAG, "Session " + sessionId
                            + " recalculating session priority, new priority: "
                            + newSessionPriority);
                    uwbSession.setStackSessionPriority(newSessionPriority);
                }
            }
        });
    }

    // Detect UIDs going foreground/background
    private void registerUidImportanceTransitions() {
        mActivityManager.addOnUidImportanceListener(
                UwbSessionManager.this, IMPORTANCE_FOREGROUND_SERVICE);
    }

    private static boolean hasAllRangingResultError(@NonNull UwbRangingData rangingData) {
        if (rangingData.getRangingMeasuresType()
                == UwbUciConstants.RANGING_MEASUREMENT_TYPE_TWO_WAY) {
            for (UwbTwoWayMeasurement measure : rangingData.getRangingTwoWayMeasures()) {
                if (measure.isStatusCodeOk()) {
                    return false;
                }
            }
        } else if (rangingData.getRangingMeasuresType()
                == UwbUciConstants.RANGING_MEASUREMENT_TYPE_OWR_AOA) {
            UwbOwrAoaMeasurement measure = rangingData.getRangingOwrAoaMeasure();
            if (measure.getRangingStatus() == UwbUciConstants.STATUS_CODE_OK) {
                return false;
            }
        } else if (rangingData.getRangingMeasuresType()
                == UwbUciConstants.RANGING_MEASUREMENT_TYPE_DL_TDOA) {
            for (UwbDlTDoAMeasurement measure : rangingData.getUwbDlTDoAMeasurements()) {
                if (measure.getStatus() == STATUS_CODE_OK) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public void onRangeDataNotificationReceived(UwbRangingData rangingData) {
        Trace.beginSection("UWB#onRangeDataNotificationReceived");
        long sessionId = rangingData.getSessionId();
        UwbSession uwbSession = getUwbSession((int) sessionId);
        if (uwbSession != null) {
            // TODO: b/268065070 Include UWB logs for both filtered and unfiltered data.
            mSessionNotificationManager.onRangingResult(uwbSession, rangingData);
            processRangeData(rangingData, uwbSession);
            if (mUwbInjector.getDeviceConfigFacade().isRangingErrorStreakTimerEnabled()
                    && uwbSession.mRangingErrorStreakTimeoutMs
                    != UwbSession.RANGING_RESULT_ERROR_NO_TIMEOUT) {
                if (hasAllRangingResultError(rangingData)) {
                    uwbSession.startRangingResultErrorStreakTimerIfNotSet();
                } else {
                    uwbSession.stopRangingResultErrorStreakTimerIfSet();
                }
            }
        } else {
            Log.i(TAG, "Session is not initialized or Ranging Data is Null");
        }
        Trace.endSection();
    }

    /* Notification of received data over UWB to Application*/
    @Override
    public void onDataReceived(
            long sessionId, int status, long sequenceNum, byte[] address, byte[] data) {
        Log.d(TAG, "onDataReceived(): Received data packet - "
                + "Address: " + UwbUtil.toHexString(address)
                + ", Data: " + UwbUtil.toHexString(data)
                + ", sessionId: " + sessionId
                + ", status: " + status
                + ", sequenceNum: " + sequenceNum);

        UwbSession uwbSession = getUwbSession((int) sessionId);
        if (uwbSession == null) {
            Log.e(TAG, "onDataReceived(): Received data for unknown sessionId = " + sessionId);
            return;
        }

        // Size of address in the UCI Packet for DATA_MESSAGE_RCV is always expected to be 8
        // (EXTENDED_ADDRESS_BYTE_LENGTH). It can contain the MacAddress in short format however
        // (2 LSB with MacAddress, 6 MSB zeroed out).
        if (address.length != UWB_DEVICE_EXT_MAC_ADDRESS_LEN) {
            Log.e(TAG, "onDataReceived(): Received data for sessionId = " + sessionId
                    + ", with unexpected MacAddress length = " + address.length);
            return;
        }
        mUwbMetrics.logDataRx(uwbSession, status);

        Long longAddress = macAddressByteArrayToLong(address);
        UwbAddress uwbAddress = UwbAddress.fromBytes(address);

        // When the data packet is received on a non OWR-for-AoA ranging session, send it to the
        // higher layer. For the OWR-for-AoA ranging session, the data packet is only sent when the
        // received SESSION_INFO_NTF indicate this Observer device is pointing to an Advertiser.
        if (uwbSession.getRangingRoundUsage() != ROUND_USAGE_OWR_AOA_MEASUREMENT) {
            mSessionNotificationManager.onDataReceived(
                    uwbSession, uwbAddress, new PersistableBundle(), data);
            return;
        }

        ReceivedDataInfo info = new ReceivedDataInfo();
        info.sessionId = sessionId;
        info.status = status;
        info.sequenceNum = sequenceNum;
        info.address = longAddress;
        info.payload = data;

        uwbSession.addReceivedDataInfo(info);
    }

    /* Notification of data send status */
    @Override
    public void onDataSendStatus(
            long sessionId, int dataTransferStatus, long sequenceNum, int txCount) {
        Log.d(TAG, "onDataSendStatus(): Received data send status - "
                + ", sessionId: " + sessionId
                + ", status: " + dataTransferStatus
                + ", sequenceNum: " + sequenceNum
                + ", txCount: " + txCount);

        UwbSession uwbSession = getUwbSession((int) sessionId);
        if (uwbSession == null) {
            Log.e(TAG, "onDataSendStatus(): Received data send status for unknown sessionId = "
                    + sessionId);
            return;
        }

        SendDataInfo sendDataInfo = uwbSession.getSendDataInfo(sequenceNum);
        if (sendDataInfo == null) {
            Log.e(TAG, "onDataSendStatus(): No SendDataInfo found for data packet (sessionId = "
                    + sessionId + ", sequenceNum = " + sequenceNum + ")");
            return;
        }

        // A note on status - earlier spec versions had the same status value (0x1) as an error,
        // the code is written as per recent spec versions (v2.0.0_0.0.9r0).
        if (dataTransferStatus == UwbUciConstants.STATUS_CODE_DATA_TRANSFER_REPETITION_OK
                || dataTransferStatus == UwbUciConstants.STATUS_CODE_DATA_TRANSFER_OK) {
            mSessionNotificationManager.onDataSent(
                    uwbSession, sendDataInfo.remoteDeviceAddress, sendDataInfo.params);
        } else {
            mSessionNotificationManager.onDataSendFailed(
                    uwbSession, sendDataInfo.remoteDeviceAddress, dataTransferStatus,
                     sendDataInfo.params);
            uwbSession.removeSendDataInfo(sequenceNum);
        }
        // when transmission count equals to data repetition count, SendDataInfo will be removed for
        // the particular sequence number
        if (txCount >= (uwbSession.getDataRepetitionCount() + 1)
                && dataTransferStatus == UwbUciConstants.STATUS_CODE_DATA_TRANSFER_OK) {
            uwbSession.removeSendDataInfo(sequenceNum);
        }
    }

    @Override
    public void onRadarDataMessageReceived(UwbRadarData radarData) {
        Trace.beginSection("UWB#onRadarDataMessageReceived");
        long sessionId = radarData.sessionId;
        UwbSession uwbSession = getUwbSession((int) sessionId);
        if (uwbSession != null) {
            mSessionNotificationManager.onRadarDataMessageReceived(uwbSession, radarData);
        } else {
            Log.i(TAG, "Session is not initialized or Radar Data is Null");
        }
        Trace.endSection();
    }

    @Override
    public void onDataTransferPhaseConfigNotificationReceived(long sessionId,
            int dataTransferPhaseConfigStatus) {
        Log.d(TAG, "onDataTransferPhaseConfigNotificationReceived:"
                + ", sessionId: " + sessionId
                + ", status: " + dataTransferPhaseConfigStatus);

        UwbSession uwbSession = getUwbSession((int) sessionId);
        if (uwbSession == null) {
            Log.e(TAG, "onDataTransferPhaseConfigNotificationReceived: Received data transfer"
                    + "config notification for unknown sessionId = " + sessionId);
            return;
        }

        FiraDataTransferPhaseConfigStatusCode statusCode =
                new FiraDataTransferPhaseConfigStatusCode.Builder()
                .setStatusCode(dataTransferPhaseConfigStatus).build();

        if (dataTransferPhaseConfigStatus
                == UwbUciConstants.STATUS_CODE_DATA_TRANSFER_PHASE_CONFIG_DTPCM_CONFIG_SUCCESS) {
            mSessionNotificationManager.onDataTransferPhaseConfigured(uwbSession,
                    statusCode.toBundle());
        } else {
            mSessionNotificationManager.onDataTransferPhaseConfigFailed(uwbSession,
                    statusCode.toBundle());
        }
    }

    /** Updates pose information if the session is using an ApplicationPoseSource */
    public void updatePose(SessionHandle sessionHandle, PersistableBundle params) {
        int sessionId = getSessionId(sessionHandle);
        UwbSession uwbSession = getUwbSession(sessionId);

        if (uwbSession == null) {
            // Session doesn't exist yet/anymore.
            return;
        }

        uwbSession.updatePose(FiraPoseUpdateParams.fromBundle(params));
    }

    @VisibleForTesting
    static final class ReceivedDataInfo {
        public long sessionId;
        public int status;
        public long sequenceNum;
        public long address;
        public byte[] payload;
    }

    @Override
    public void onMulticastListUpdateNotificationReceived(
            UwbMulticastListUpdateStatus multicastListUpdateStatus) {
        Log.d(TAG, "onMulticastListUpdateNotificationReceived");
        UwbSession uwbSession = getUwbSession((int) multicastListUpdateStatus.getSessionId());
        if (uwbSession == null) {
            Log.d(TAG, "onMulticastListUpdateNotificationReceived - invalid session");
            return;
        }
        uwbSession.setMulticastListUpdateStatus(multicastListUpdateStatus);
        synchronized (uwbSession.getWaitObj()) {
            uwbSession.getWaitObj().blockingNotify();
        }
    }

    @Override
    public void onSessionStatusNotificationReceived(long sessionId, int state, int reasonCode) {
        Log.i(TAG, "onSessionStatusNotificationReceived - Session ID : " + sessionId + ", state : "
                + UwbSessionNotificationHelper.getSessionStateString(state)
                + ", reasonCode:" + reasonCode);
        UwbSession uwbSession = getUwbSession((int) sessionId);

        if (uwbSession == null) {
            Log.d(TAG, "onSessionStatusNotificationReceived - invalid session");
            return;
        }

        int prevState = uwbSession.getSessionState();
        setCurrentSessionState((int) sessionId, state);

        if ((uwbSession.getOperationType() == SESSION_ON_DEINIT
                && state == UwbUciConstants.UWB_SESSION_STATE_IDLE)
                || (uwbSession.getOperationType() == SESSION_STOP_RANGING
                && state == UwbUciConstants.UWB_SESSION_STATE_IDLE
                && reasonCode != REASON_STATE_CHANGE_WITH_SESSION_MANAGEMENT_COMMANDS)) {
            Log.d(TAG, "Session status NTF is received due to in-band session state change");
        }

        // Store the reasonCode before notifying on the waitObj.
        synchronized (uwbSession.getWaitObj()) {
            uwbSession.setLastSessionStatusNtfReasonCode(reasonCode);
            uwbSession.getWaitObj().blockingNotify();
        }

        //TODO : process only error handling in this switch function, b/218921154
        switch (state) {
            case UwbUciConstants.UWB_SESSION_STATE_IDLE:
                if (prevState == UwbUciConstants.UWB_SESSION_STATE_ACTIVE) {
                    // If session was stopped explicitly, then the onStopped() is sent from
                    // stopRanging method.
                    if (reasonCode != REASON_STATE_CHANGE_WITH_SESSION_MANAGEMENT_COMMANDS) {
                        mSessionNotificationManager.onRangingStoppedWithUciReasonCode(
                                uwbSession, reasonCode);
                        mUwbMetrics.longRangingStopEvent(uwbSession);
                    }
                } else if (prevState == UwbUciConstants.UWB_SESSION_STATE_IDLE) {
                    //mSessionNotificationManager.onRangingReconfigureFailed(
                    //      uwbSession, reasonCode);
                }
                break;
            case UwbUciConstants.UWB_SESSION_STATE_DEINIT:
                mEventTask.execute(SESSION_ON_DEINIT, uwbSession);
                break;
            default:
                break;
        }
        if (mUwbInjector.getUwbServiceCore().isOemExtensionCbRegistered()) {
            String appPackageName = uwbSession.getAnyNonPrivilegedAppInAttributionSource() != null
                    ? uwbSession.getAnyNonPrivilegedAppInAttributionSource().getPackageName()
                    : uwbSession.getAttributionSource().getPackageName();
            PersistableBundle sessionStatusBundle = new SessionStatus.Builder()
                    .setSessionId(sessionId)
                    .setState(state)
                    .setReasonCode(reasonCode)
                    .setAppPackageName(appPackageName)
                    .setSessiontoken(mSessionTokenMap.getOrDefault(uwbSession.getSessionId(), 0))
                    .build()
                    .toBundle();
            try {
                mUwbInjector.getUwbServiceCore().getOemExtensionCallback()
                        .onSessionStatusNotificationReceived(sessionStatusBundle);
            } catch (RemoteException e) {
                Log.e(TAG, "Failed to send vendor notification", e);
            }
        }
    }

    private int setAppConfigurations(UwbSession uwbSession) {
        if (uwbSession.getProtocolName().equals(FiraParams.PROTOCOL_NAME)
                        && uwbSession.getParams() instanceof FiraOpenSessionParams) {
            FiraOpenSessionParams params = (FiraOpenSessionParams) uwbSession.getParams();
            if (mSessionTokenMap.containsKey(params.getReferenceSessionHandle())) {
                uwbSession.updateFiraParamsForSessionTimeBase(
                           mSessionTokenMap.get(params.getReferenceSessionHandle()));
            }
        }

        int status = mConfigurationManager.setAppConfigurations(uwbSession.getSessionId(),
                uwbSession.getParams(), uwbSession.getChipId(),
                getUwbsFiraProtocolVersion(uwbSession.getChipId()));
        if (status == UwbUciConstants.STATUS_CODE_OK
                && mUwbInjector.getUwbServiceCore().isOemExtensionCbRegistered()) {
            try {
                SessionConfigParams sessionConfigParams = new SessionConfigParams.Builder()
                        .setSessionId(uwbSession.getSessionId())
                        .setSessiontoken(
                                mSessionTokenMap.getOrDefault(uwbSession.getSessionId(), 0))
                        .setOpenSessionParamsBundle(uwbSession.getParams().toBundle())
                        .build();
                status = mUwbInjector.getUwbServiceCore().getOemExtensionCallback()
                        .onSessionConfigurationReceived(sessionConfigParams.toBundle());
            } catch (RemoteException e) {
                Log.e(TAG, "Failed to send vendor notification", e);
            }
        }
        return status;
    }

    public synchronized void initSession(AttributionSource attributionSource,
            SessionHandle sessionHandle, int sessionId, byte sessionType, String protocolName,
            Params params, IUwbRangingCallbacks rangingCallbacks, String chipId)
            throws RemoteException {
        Log.i(TAG, "initSession() - sessionId: " + sessionId + ", sessionHandle: " + sessionHandle
                + ", sessionType: " + sessionType);
        UwbSession uwbSession =  createUwbSession(attributionSource, sessionHandle, sessionId,
                sessionType, protocolName, params, rangingCallbacks, chipId);
        // Check the attribution source chain to ensure that there are no 3p apps which are not in
        // fg which can receive the ranging results.
        AttributionSource nonPrivilegedAppAttrSource =
                uwbSession.getAnyNonPrivilegedAppInAttributionSource();
        if (nonPrivilegedAppAttrSource != null) {
            Log.d(TAG, "Found a 3p app/service in the attribution source of request: "
                    + nonPrivilegedAppAttrSource);
            // TODO(b/211445008): Move this operation to uwb thread.
            boolean hasNonPrivilegedFgAppOrService = mUwbInjector.isForegroundAppOrService(
                    nonPrivilegedAppAttrSource.getUid(),
                    nonPrivilegedAppAttrSource.getPackageName());
            uwbSession.setHasNonPrivilegedFgAppOrService(hasNonPrivilegedFgAppOrService);
            if (!hasNonPrivilegedFgAppOrService) {
                if (!mUwbInjector.getDeviceConfigFacade().isBackgroundRangingEnabled()) {
                    Log.e(TAG, "openRanging - System policy disallows for non fg 3p apps");
                    rangingCallbacks.onRangingOpenFailed(sessionHandle,
                            RangingChangeReason.SYSTEM_POLICY, new PersistableBundle());
                    return;
                } else {
                    Log.d(TAG, "openRanging - System policy allows for non fg 3p apps");
                }
            }
        }
        if (isExistedSession(sessionHandle) || isExistedSession(sessionId)) {
            Log.i(TAG, "Duplicated session. SessionHandle: " + sessionHandle + ", SessionId: "
                    + sessionId);
            rangingCallbacks.onRangingOpenFailed(sessionHandle, RangingChangeReason.BAD_PARAMETERS,
                    UwbSessionNotificationHelper.convertUciStatusToParam(protocolName,
                            UwbUciConstants.STATUS_CODE_ERROR_SESSION_DUPLICATE));
            mUwbMetrics.logRangingInitEvent(uwbSession,
                    UwbUciConstants.STATUS_CODE_ERROR_SESSION_DUPLICATE);
            return;
        }

        boolean maxSessionsExceeded = false;
        // TODO: getCccSessionCount and getFiraSessionCount should be chip specific
        if (protocolName.equals(AliroParams.PROTOCOL_NAME)
                && getAliroSessionCount() >= getMaxAliroSessionsNumber(chipId)) {
            Log.i(TAG, "Max ALIRO Sessions Exceeded");
            // All ALIRO sessions have the same priority so there's no point in trying to make space
            // if max sessions are already reached.
            maxSessionsExceeded = true;
        } else if (protocolName.equals(CccParams.PROTOCOL_NAME)
                && getCccSessionCount() >= getMaxCccSessionsNumber(chipId)) {
            Log.i(TAG, "Max CCC Sessions Exceeded");
            // All CCC sessions have the same priority so there's no point in trying to make space
            // if max sessions are already reached.
            maxSessionsExceeded = true;
        } else if (protocolName.equals(FiraParams.PROTOCOL_NAME)
                && getFiraSessionCount() >= getMaxFiraSessionsNumber(chipId)) {
            Log.i(TAG, "Max Fira Sessions Exceeded");
            maxSessionsExceeded = !tryMakeSpaceForFiraSession(
                    uwbSession.getStackSessionPriority());
        }
        if (maxSessionsExceeded) {
            rangingCallbacks.onRangingOpenFailed(sessionHandle,
                    RangingChangeReason.MAX_SESSIONS_REACHED,
                    UwbSessionNotificationHelper.convertUciStatusToParam(protocolName,
                            UwbUciConstants.STATUS_CODE_ERROR_MAX_SESSIONS_EXCEEDED));
            mUwbMetrics.logRangingInitEvent(uwbSession,
                    UwbUciConstants.STATUS_CODE_ERROR_MAX_SESSIONS_EXCEEDED);
            return;
        }

        try {
            uwbSession.getBinder().linkToDeath(uwbSession, 0);
        } catch (RemoteException e) {
            uwbSession.binderDied();
            Log.e(TAG, "linkToDeath fail - sessionID : " + uwbSession.getSessionId());
            rangingCallbacks.onRangingOpenFailed(sessionHandle, RangingChangeReason.UNKNOWN,
                    UwbSessionNotificationHelper.convertUciStatusToParam(protocolName,
                            UwbUciConstants.STATUS_CODE_FAILED));
            mUwbMetrics.logRangingInitEvent(uwbSession,
                    UwbUciConstants.STATUS_CODE_FAILED);
            removeSession(uwbSession);
            return;
        }

        mSessionTable.put(sessionHandle, uwbSession);
        addToNonPrivilegedUidToFiraSessionTableIfNecessary(uwbSession);
        mEventTask.execute(SESSION_OPEN_RANGING, uwbSession);
        return;
    }

    private boolean tryMakeSpaceForFiraSession(int priorityThreshold) {
        Optional<UwbSession> lowestPrioritySession = getSessionWithLowestPriorityByProtocol(
                FiraParams.PROTOCOL_NAME);
        if (!lowestPrioritySession.isPresent()) {
            Log.w(TAG,
                    "New session blocked by max sessions exceeded, but list of sessions is "
                            + "empty");
            return false;
        }
        if (lowestPrioritySession.get().getStackSessionPriority() < priorityThreshold) {
            return deInitDueToLowPriority(lowestPrioritySession.get().getSessionHandle());
        }
        return false;
    }

    // TODO: use UwbInjector.
    @VisibleForTesting
    UwbSession createUwbSession(AttributionSource attributionSource, SessionHandle sessionHandle,
            int sessionId, byte sessionType, String protocolName, Params params,
            IUwbRangingCallbacks iUwbRangingCallbacks, String chipId) {
        return new UwbSession(attributionSource, sessionHandle, sessionId, sessionType,
                protocolName, params, iUwbRangingCallbacks, chipId);
    }

    public synchronized void deInitSession(SessionHandle sessionHandle) {
        if (!isExistedSession(sessionHandle)) {
            Log.i(TAG, "Not initialized session ID");
            return;
        }

        int sessionId = getSessionId(sessionHandle);
        Log.i(TAG, "deinitSession() - sessionId: " + sessionId
                + ", sessionHandle: " + sessionHandle);
        mEventTask.execute(SESSION_DEINIT, sessionHandle, STATUS_CODE_OK);
        return;
    }

    /**
     * Logs and executes session de-init task with low priority being sent as the reason in
     * ranging closed callback.
     */
    private synchronized boolean deInitDueToLowPriority(SessionHandle sessionHandle) {
        int sessionId = getSessionId(sessionHandle);
        if (!isExistedSession(sessionHandle)) {
            Log.w(TAG, "Session " + sessionId + " expected to exist but not found. "
                    + "Failed to de-initialize low priority session.");
            return false;
        }

        Log.i(TAG, "deInitDueToLowPriority() - sessionId: " + sessionId
                + ", sessionHandle: " + sessionHandle);
        mEventTask.execute(SESSION_DEINIT, sessionHandle,
                UwbUciConstants.STATUS_CODE_ERROR_MAX_SESSIONS_EXCEEDED);
        return true;
    }

    public synchronized void startRanging(SessionHandle sessionHandle, @Nullable Params params) {
        if (!isExistedSession(sessionHandle)) {
            Log.i(TAG, "Not initialized session ID");
            return;
        }

        int sessionId = getSessionId(sessionHandle);
        Log.i(TAG, "startRanging() - sessionId: " + sessionId
                + ", sessionHandle: " + sessionHandle);

        UwbSession uwbSession = getUwbSession(sessionId);

        int currentSessionState = getCurrentSessionState(sessionId);
        if (currentSessionState == UwbUciConstants.UWB_SESSION_STATE_IDLE) {
            if (uwbSession.getProtocolName().equals(AliroParams.PROTOCOL_NAME)
                    && params instanceof AliroStartRangingParams) {
                AliroStartRangingParams aliroStartRangingParams = (AliroStartRangingParams) params;
                Log.i(TAG, "startRanging() - update RAN multiplier: "
                        + aliroStartRangingParams.getRanMultiplier()
                        + ", stsIndex: " + aliroStartRangingParams.getStsIndex());
                // Need to update the RAN multiplier from the AliroStartRangingParams for an
                // ALIRO session.
                uwbSession.updateAliroParamsOnStart(aliroStartRangingParams);
            } else if (uwbSession.getProtocolName().equals(CccParams.PROTOCOL_NAME)
                    && params instanceof CccStartRangingParams) {
                CccStartRangingParams cccStartRangingParams = (CccStartRangingParams) params;
                Log.i(TAG, "startRanging() - update RAN multiplier: "
                        + cccStartRangingParams.getRanMultiplier()
                        + ", stsIndex: " + cccStartRangingParams.getStsIndex());
                // Need to update the RAN multiplier from the CccStartRangingParams for CCC session.
                uwbSession.updateCccParamsOnStart(cccStartRangingParams);
            } else if (uwbSession.getProtocolName().equals(FiraParams.PROTOCOL_NAME)) {
                // Need to update session priority if it changed.
                uwbSession.updateFiraParamsOnStartIfChanged();
            }
            mEventTask.execute(SESSION_START_RANGING, uwbSession);
        } else if (currentSessionState == UwbUciConstants.UWB_SESSION_STATE_ACTIVE) {
            Log.i(TAG, "session is already ranging");
            mSessionNotificationManager.onRangingStartFailed(
                    uwbSession, UwbUciConstants.STATUS_CODE_REJECTED);
        } else {
            Log.i(TAG, "session can't start ranging");
            mSessionNotificationManager.onRangingStartFailed(
                    uwbSession, UwbUciConstants.STATUS_CODE_FAILED);
            mUwbMetrics.longRangingStartEvent(uwbSession, UwbUciConstants.STATUS_CODE_FAILED);
        }
    }

    private synchronized void stopRangingInternal(SessionHandle sessionHandle,
            boolean triggeredBySystemPolicy) {
        if (!isExistedSession(sessionHandle)) {
            Log.i(TAG, "Not initialized session ID");
            return;
        }

        int sessionId = getSessionId(sessionHandle);
        Log.i(TAG, "stopRanging() - sessionId: " + sessionId
                + ", sessionHandle: " + sessionHandle);

        UwbSession uwbSession = getUwbSession(sessionId);
        int currentSessionState = getCurrentSessionState(sessionId);
        if (currentSessionState == UwbUciConstants.UWB_SESSION_STATE_ACTIVE) {
            mEventTask.execute(SESSION_STOP_RANGING, uwbSession, triggeredBySystemPolicy ? 1 : 0);
        } else if (currentSessionState == UwbUciConstants.UWB_SESSION_STATE_IDLE) {
            Log.i(TAG, "session is already idle state");
            mSessionNotificationManager.onRangingStopped(uwbSession,
                    UwbUciConstants.STATUS_CODE_OK);
            mUwbMetrics.longRangingStopEvent(uwbSession);
        } else {
            mSessionNotificationManager.onRangingStopFailed(uwbSession,
                    UwbUciConstants.STATUS_CODE_REJECTED);
            Log.i(TAG, "Not active session ID");
        }
    }

    public synchronized void stopRanging(SessionHandle sessionHandle) {
        stopRangingInternal(sessionHandle, false /* triggeredBySystemPolicy */);
    }

    /**
     * Get the UwbSession corresponding to the given UWB Session ID. This API returns {@code null}
     * when the UWB session is not found.
     */
    @Nullable
    public UwbSession getUwbSession(int sessionId) {
        return mSessionTable.values()
                .stream()
                .filter(v -> v.getSessionId() == sessionId)
                .findAny()
                .orElse(null);
    }

    /**
     * Get the UwbSession corresponding to the given UWB SessionHandle. This API returns
     * {@code null} when the UWB session is not found.
     */
    @Nullable
    private UwbSession getUwbSession(SessionHandle sessionHandle) {
        return mSessionTable.get(sessionHandle);
    }

    /**
     * Get the Uwb Session ID corresponding to the given UWB Session Handle. This API returns
     * {@code null} when the UWB session ID is not found.
     */
    @Nullable
    public Integer getSessionId(SessionHandle sessionHandle) {
        UwbSession session = mSessionTable.get(sessionHandle);
        if (session == null) return null;
        return session.getSessionId();
    }

    private int getActiveSessionCount() {
        return Math.toIntExact(
                mSessionTable.values()
                        .stream()
                        .filter(v -> v.getSessionState() == UwbUciConstants.DEVICE_STATE_ACTIVE)
                        .count()
        );
    }

    private void processRangeData(UwbRangingData rangingData, UwbSession uwbSession) {
        if (rangingData.getRangingMeasuresType()
                != UwbUciConstants.RANGING_MEASUREMENT_TYPE_OWR_AOA) {
            return;
        }

        if (!isValidUwbSessionForOwrAoaRanging(uwbSession)) {
            return;
        }

        // Record the OWR Aoa Measurement from the RANGE_DATA_NTF.
        UwbOwrAoaMeasurement uwbOwrAoaMeasurement = rangingData.getRangingOwrAoaMeasure();
        mAdvertiseManager.updateAdvertiseTarget(uwbOwrAoaMeasurement);

        byte[] macAddressBytes = getValidMacAddressFromOwrAoaMeasurement(
                rangingData, uwbOwrAoaMeasurement);
        if (macAddressBytes == null)  {
            Log.i(TAG, "OwR Aoa UwbSession: Invalid MacAddress for remote device");
            return;
        }

        boolean advertisePointingResult = mAdvertiseManager.isPointedTarget(macAddressBytes);
        if (mUwbInjector.getUwbServiceCore().isOemExtensionCbRegistered()) {
            try {
                PersistableBundle pointedTargetBundle = new AdvertisePointedTarget.Builder()
                        .setMacAddress(macAddressBytes)
                        .setAdvertisePointingResult(advertisePointingResult)
                        .build()
                        .toBundle();

                advertisePointingResult = mUwbInjector
                        .getUwbServiceCore()
                        .getOemExtensionCallback()
                        .onCheckPointedTarget(pointedTargetBundle);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }

        if (advertisePointingResult) {
            // Use a loop to notify all the received application data payload(s) (in sequence number
            // order) for this OWR AOA ranging session.
            long macAddress = macAddressByteArrayToLong(macAddressBytes);
            UwbAddress uwbAddress = UwbAddress.fromBytes(macAddressBytes);

            List<ReceivedDataInfo> receivedDataInfoList = uwbSession.getAllReceivedDataInfo(
                    macAddress);
            if (receivedDataInfoList.isEmpty()) {
                Log.i(TAG, "OwR Aoa UwbSession: Application Payload data not found for"
                        + " MacAddress = " + UwbUtil.toHexString(macAddress));
                return;
            }

            receivedDataInfoList.stream().forEach(r ->
                    mSessionNotificationManager.onDataReceived(
                            uwbSession, uwbAddress, new PersistableBundle(), r.payload));
            mUwbMetrics.logDataToUpperLayer(uwbSession, receivedDataInfoList.size());
            mAdvertiseManager.removeAdvertiseTarget(macAddress);
        }
    }

    @Nullable
    private byte[] getValidMacAddressFromOwrAoaMeasurement(UwbRangingData rangingData,
            UwbOwrAoaMeasurement uwbOwrAoaMeasurement) {
        byte[] macAddress = uwbOwrAoaMeasurement.getMacAddress();
        if (rangingData.getMacAddressMode() == MAC_ADDRESSING_MODE_SHORT) {
            return (macAddress.length == UWB_DEVICE_SHORT_MAC_ADDRESS_LEN) ? macAddress : null;
        } else if (rangingData.getMacAddressMode() == MAC_ADDRESSING_MODE_EXTENDED) {
            return (macAddress.length == UWB_DEVICE_EXT_MAC_ADDRESS_LEN) ? macAddress : null;
        }
        return null;
    }

    public boolean isExistedSession(SessionHandle sessionHandle) {
        return (getSessionId(sessionHandle) != null);
    }

    public boolean isExistedSession(int sessionId) {
        return getUwbSession(sessionId) != null;
    }

    public void stopAllRanging() {
        Log.d(TAG, "stopAllRanging()");
        for (UwbSession uwbSession : mSessionTable.values()) {
            int status = mNativeUwbManager.stopRanging(uwbSession.getSessionId(),
                    uwbSession.getChipId());

            if (status != UwbUciConstants.STATUS_CODE_OK) {
                Log.i(TAG, "stopAllRanging() - Session " + uwbSession.getSessionId()
                        + " is failed to stop ranging");
            } else {
                mUwbMetrics.longRangingStopEvent(uwbSession);
                uwbSession.setSessionState(UwbUciConstants.UWB_SESSION_STATE_IDLE);
            }
        }
    }

    public synchronized void deinitAllSession() {
        Log.d(TAG, "deinitAllSession()");
        for (UwbSession uwbSession : mSessionTable.values()) {
            handleOnDeInit(uwbSession);
        }

        // Not resetting chip on UWB toggle off.
        // mNativeUwbManager.deviceReset(UwbUciConstants.UWBS_RESET);
    }

    public synchronized void handleOnDeInit(UwbSession uwbSession) {
        if (!isExistedSession(uwbSession.getSessionHandle())) {
            Log.i(TAG, "onDeinit - Ignoring already deleted session "
                    + uwbSession.getSessionId());
            return;
        }
        Log.d(TAG, "onDeinit: " + uwbSession.getSessionId());
        mSessionNotificationManager.onRangingClosedWithApiReasonCode(uwbSession,
                RangingChangeReason.SYSTEM_POLICY);
        mUwbMetrics.logRangingCloseEvent(uwbSession, UwbUciConstants.STATUS_CODE_OK);

        // Reset all UWB session timers when the session is de-init.
        uwbSession.stopTimers();
        removeSession(uwbSession);
    }

    public void setCurrentSessionState(int sessionId, int state) {
        UwbSession uwbSession = getUwbSession(sessionId);
        if (uwbSession != null) {
            uwbSession.setSessionState(state);
        }
    }

    public int getCurrentSessionState(int sessionId) {
        UwbSession uwbSession = getUwbSession(sessionId);
        if (uwbSession != null) {
            return uwbSession.getSessionState();
        }
        return UwbUciConstants.UWB_SESSION_STATE_ERROR;
    }

    public int getSessionCount() {
        return mSessionTable.size();
    }

    public long getAliroSessionCount() {
        return getProtocolSessionCount(AliroParams.PROTOCOL_NAME);
    }

    public long getCccSessionCount() {
        return getProtocolSessionCount(CccParams.PROTOCOL_NAME);
    }

    public long getFiraSessionCount() {
        return getProtocolSessionCount(FiraParams.PROTOCOL_NAME);
    }

    private long getProtocolSessionCount(String protocolName) {
        return mSessionTable.values().stream().filter(
                s -> s.mProtocolName.equals(protocolName)).count();
    }

    /** Returns max number of ALIRO sessions possible on given chip. */
    public long getMaxAliroSessionsNumber(String chipId) {
        GenericSpecificationParams params =
                mUwbInjector.getUwbServiceCore().getCachedSpecificationParams(chipId);
        if (params != null && params.getAliroSpecificationParams() != null) {
            return params.getAliroSpecificationParams().getMaxRangingSessionNumber();
        } else {
            // Specification params are empty, return the default ALIRO max sessions value
            return AliroSpecificationParams.DEFAULT_MAX_RANGING_SESSIONS_NUMBER;
        }
    }

    /** Returns max number of CCC sessions possible on given chip. */
    public long getMaxCccSessionsNumber(String chipId) {
        GenericSpecificationParams params =
                mUwbInjector.getUwbServiceCore().getCachedSpecificationParams(chipId);
        if (params != null && params.getCccSpecificationParams() != null) {
            return params.getCccSpecificationParams().getMaxRangingSessionNumber();
        } else {
            // specification params are empty, return the default CCC max sessions value
            return CccSpecificationParams.DEFAULT_MAX_RANGING_SESSIONS_NUMBER;
        }
    }

    /** Returns max number of Fira sessions possible on given chip. */
    public long getMaxFiraSessionsNumber(String chipId) {
        GenericSpecificationParams params =
                mUwbInjector.getUwbServiceCore().getCachedSpecificationParams(chipId);
        if (params != null && params.getFiraSpecificationParams() != null) {
            return params.getFiraSpecificationParams().getMaxRangingSessionNumber();
        } else {
            // specification params are empty, return the default Fira max sessions value
            return FiraSpecificationParams.DEFAULT_MAX_RANGING_SESSIONS_NUMBER;
        }
    }

    /** Gets the session with the lowest session priority among all sessions with given protocol. */
    public Optional<UwbSession> getSessionWithLowestPriorityByProtocol(String protocolName) {
        return mSessionTable.values().stream().filter(
                s -> s.mProtocolName.equals(protocolName)).min(
                Comparator.comparingInt(UwbSession::getStackSessionPriority));
    }

    public Set<Integer> getSessionIdSet() {
        return mSessionTable.values()
                .stream()
                .map(v -> v.getSessionId())
                .collect(Collectors.toSet());
    }

    private boolean suspendRangingPreconditionCheck(UwbSession uwbSession) {
        FiraOpenSessionParams firaOpenSessionParams =
                (FiraOpenSessionParams) uwbSession.getParams();
        int deviceType = firaOpenSessionParams.getDeviceType();
        int scheduleMode = firaOpenSessionParams.getScheduledMode();
        int sessionState = uwbSession.getSessionState();
        if (deviceType != FiraParams.RANGING_DEVICE_TYPE_CONTROLLER ||
                scheduleMode != FiraParams.TIME_SCHEDULED_RANGING ||
                sessionState != UwbUciConstants.UWB_SESSION_STATE_ACTIVE) {
            Log.e(TAG, "suspendRangingPreconditionCheck failed - deviceType: " + deviceType +
                    " scheduleMode: " + scheduleMode + " sessionState: " + sessionState);
            return false;
        }
        return true;
    }

    private boolean sessionUpdateMulticastListCmdPreconditioncheck(UwbSession uwbSession,
              int action, byte[] subSessionKeyList) {
        FiraOpenSessionParams firaOpenSessionParams =
                (FiraOpenSessionParams) uwbSession.getParams();
        int deviceType = firaOpenSessionParams.getDeviceType();
        int stsConfig = firaOpenSessionParams.getStsConfig();
        byte[] sessionKey = firaOpenSessionParams.getSessionKey();
        Log.i(TAG, "sessionUpdateMulticastListCmdPreconditioncheck  - deviceType: "
                   + deviceType + " stsConfig: " + stsConfig + " action: " + action);
        if (deviceType == FiraParams.RANGING_DEVICE_TYPE_CONTROLLER) {
            switch (action) {
                case FiraParams.MULTICAST_LIST_UPDATE_ACTION_ADD:
                case FiraParams.MULTICAST_LIST_UPDATE_ACTION_DELETE:
                    if (subSessionKeyList != null) {
                        return false;
                    }
                    break;
                case FiraParams.P_STS_MULTICAST_LIST_UPDATE_ACTION_ADD_16_BYTE:
                case FiraParams.P_STS_MULTICAST_LIST_UPDATE_ACTION_ADD_32_BYTE:
                    if (stsConfig
                            != FiraParams.STS_CONFIG_PROVISIONED_FOR_CONTROLEE_INDIVIDUAL_KEY) {
                        return false;
                    }
                    // sessionKey is provided while opening the session and subSessionKeyList
                    // is provided while updating the multicast list. Check that both the
                    // sessionKey and subSessionKeyList are either set or not set (as both
                    // have to be provided by the same source - Host or SE).
                    if ((sessionKey == null && subSessionKeyList != null)
                            || (sessionKey != null && subSessionKeyList == null)) {
                        return false;
                    }
                    break;
                default:
                    break;
            }
        } else {
            return false;
        }
        return true;
    }

    private synchronized int reconfigureInternal(SessionHandle sessionHandle,
            @Nullable Params params, boolean triggeredByFgStateChange) {
        int status = UwbUciConstants.STATUS_CODE_ERROR_SESSION_NOT_EXIST;
        if (!isExistedSession(sessionHandle)) {
            Log.i(TAG, "Not initialized session ID");
            return status;
        }
        int sessionId = getSessionId(sessionHandle);
        Log.i(TAG, "reconfigure() - Session ID : " + sessionId);
        UwbSession uwbSession = getUwbSession(sessionId);
        if (uwbSession.getProtocolName().equals(FiraParams.PROTOCOL_NAME)
                && params instanceof FiraRangingReconfigureParams) {
            FiraRangingReconfigureParams rangingReconfigureParams =
                    (FiraRangingReconfigureParams) params;
            Log.i(TAG, "reconfigure() - update reconfigure params: "
                    + rangingReconfigureParams);
            // suspendRangingPreconditionCheck only on suspend ranging reconfigure
            if ((rangingReconfigureParams.getSuspendRangingRounds() != null) &&
                    (!suspendRangingPreconditionCheck(uwbSession))) {
                return UwbUciConstants.STATUS_CODE_REJECTED;
            }
            if ((rangingReconfigureParams.getAddressList() != null)
                    && (!sessionUpdateMulticastListCmdPreconditioncheck(uwbSession,
                        rangingReconfigureParams.getAction(),
                        rangingReconfigureParams.getSubSessionKeyList()))) {
                return UwbUciConstants.STATUS_CODE_REJECTED;
            }
            // Do not update mParams if this was triggered by framework.
            if (!triggeredByFgStateChange) {
                uwbSession.updateFiraParamsOnReconfigure(rangingReconfigureParams);
            }
        }
        mEventTask.execute(SESSION_RECONFIG_RANGING,
                new ReconfigureEventParams(uwbSession, params, triggeredByFgStateChange));
        return 0;
    }

    public synchronized int reconfigure(SessionHandle sessionHandle, @Nullable Params params) {
        return reconfigureInternal(sessionHandle, params, false /* triggeredByFgStateChange */);
    }

    /** Send the payload data to a remote device in the UWB session */
    public synchronized void sendData(SessionHandle sessionHandle, UwbAddress remoteDeviceAddress,
            PersistableBundle params, byte[] data) {
        SendDataInfo info = new SendDataInfo();
        info.sessionHandle = sessionHandle;
        info.remoteDeviceAddress = remoteDeviceAddress;
        info.params = params;
        info.data = data;

        mEventTask.execute(SESSION_SEND_DATA, info);
    }

    /**
     * Sets the data transfer session configuration
     *
     * @param sessionHandle : session handle
     * @param params        : protocol specific parameters to configure data transfer session
     */
    public void setDataTransferPhaseConfig(SessionHandle sessionHandle, PersistableBundle params) {
        if (!isExistedSession(sessionHandle)) {
            throw new IllegalStateException("Not initialized session ID: "
                + getSessionId(sessionHandle));
        }

        UpdateSessionInfo updateSessionInfo = new UpdateSessionInfo();
        updateSessionInfo.sessionHandle = sessionHandle;
        updateSessionInfo.params = params;

        mEventTask.execute(SESSION_DATA_TRANSFER_PHASE_CONFIG, updateSessionInfo);
    }

    /**
     * Sets the hybrid UWB configuration
     *
     * @param sessionHandle : Primary session handle
     * @param params        : protocol specific parameters to initiate the hybrid
     *                      session
     * @return the status code of the operation
     * @throws RemoteException if an error occurs during the remote call.
     */
    public int setHybridSessionConfiguration(SessionHandle sessionHandle, PersistableBundle params)
            throws RemoteException {
        if (!isExistedSession(sessionHandle)) {
            throw new IllegalStateException("Not initialized session ID");
        }

        FiraHybridSessionConfig husConfig = FiraHybridSessionConfig.fromBundle(params);
        int numberOfPhases = husConfig.getNumberOfPhases();
        int sessionId = getSessionId(sessionHandle);

        Log.i(TAG, "setHybridSessionConfiguration() - sessionId: " + sessionId
                + ", sessionHandle: " + sessionHandle
                + ", numberOfPhases: " + numberOfPhases);

        ByteBuffer buffer = ByteBuffer.allocate(numberOfPhases * UWB_HUS_PHASE_SIZE);
        buffer.order(ByteOrder.LITTLE_ENDIAN);

        for (FiraHybridSessionConfig.FiraHybridSessionPhaseList phaseList :
                husConfig.getPhaseList()) {
            buffer.putInt(mNativeUwbManager.getSessionToken(phaseList.getSessionHandle(),
                    getUwbSession(sessionId).getChipId()));
            buffer.putShort(phaseList.getStartSlotIndex());
            buffer.putShort(phaseList.getEndSlotIndex());
        }

        return mNativeUwbManager.setHybridSessionConfiguration(sessionId, numberOfPhases,
                husConfig.getUpdateTime(), buffer.array(), getUwbSession(sessionId).getChipId());
    }

    private static final class SendDataInfo {
        public SessionHandle sessionHandle;
        public UwbAddress remoteDeviceAddress;
        public PersistableBundle params;
        public byte[] data;
    }

    private static final class RangingRoundsUpdateDtTagInfo {
        public SessionHandle sessionHandle;
        public PersistableBundle params;
    }

    private static final class UpdateSessionInfo {
        public SessionHandle sessionHandle;
        public PersistableBundle params;
    }

    /** DT Tag ranging round update */
    public void rangingRoundsUpdateDtTag(SessionHandle sessionHandle,
            PersistableBundle bundle) {
        RangingRoundsUpdateDtTagInfo info = new RangingRoundsUpdateDtTagInfo();
        info.sessionHandle = sessionHandle;
        info.params = bundle;

        mEventTask.execute(SESSION_UPDATE_DT_TAG_RANGING_ROUNDS, info);
    }

    /** Query Max Application data size for the given UWB Session */
    public synchronized int queryMaxDataSizeBytes(SessionHandle sessionHandle) {
        if (!isExistedSession(sessionHandle)) {
            throw new IllegalStateException("Not initialized session ID");
        }

        int sessionId = getSessionId(sessionHandle);
        UwbSession uwbSession = getUwbSession(sessionId);
        if (uwbSession == null) {
            throw new IllegalStateException("UwbSession not found");
        }

        synchronized (uwbSession.getWaitObj()) {
            return mNativeUwbManager.queryMaxDataSizeBytes(uwbSession.getSessionId(),
                    uwbSession.getChipId());
        }
    }

    /** Handle ranging rounds update for DT Tag */
    public void handleRangingRoundsUpdateDtTag(RangingRoundsUpdateDtTagInfo info) {
        SessionHandle sessionHandle = info.sessionHandle;
        Integer sessionId = getSessionId(sessionHandle);
        if (sessionId == null) {
            Log.i(TAG, "UwbSessionId not found");
            return;
        }
        UwbSession uwbSession = getUwbSession(sessionId);
        if (uwbSession == null) {
            Log.i(TAG, "UwbSession not found");
            return;
        }
        DlTDoARangingRoundsUpdate dlTDoARangingRoundsUpdate = DlTDoARangingRoundsUpdate
                .fromBundle(info.params);

        if (dlTDoARangingRoundsUpdate.getSessionId() != getSessionId(sessionHandle)) {
            throw new IllegalArgumentException("Wrong session ID");
        }

        FutureTask<DtTagUpdateRangingRoundsStatus> rangingRoundsUpdateTask = new FutureTask<>(
                () -> {
                    synchronized (uwbSession.getWaitObj()) {
                        return mNativeUwbManager.sessionUpdateDtTagRangingRounds(
                                (int) dlTDoARangingRoundsUpdate.getSessionId(),
                                dlTDoARangingRoundsUpdate.getNoOfRangingRounds(),
                                dlTDoARangingRoundsUpdate.getRangingRoundIndexes(),
                                uwbSession.getChipId());
                    }
                }
        );

        DtTagUpdateRangingRoundsStatus status = null;
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.submit(rangingRoundsUpdateTask);
        try {
            status = rangingRoundsUpdateTask.get(IUwbAdapter
                    .RANGING_ROUNDS_UPDATE_DT_TAG_THRESHOLD_MS, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            Log.i(TAG, "Failed to update ranging rounds for Dt tag - status : TIMEOUT");
            executor.shutdownNow();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
        // Native stack returns null if unsuccessful
        if (status == null) {
            status = new DtTagUpdateRangingRoundsStatus(
                    UwbUciConstants.STATUS_CODE_ERROR_ROUND_INDEX_NOT_ACTIVATED,
                    0,
                    new byte[]{});
        }
        PersistableBundle params = new DlTDoARangingRoundsUpdateStatus.Builder()
                .setStatus(status.getStatus())
                .setNoOfRangingRounds(status.getNoOfRangingRounds())
                .setRangingRoundIndexes(status.getRangingRoundIndexes())
                .build()
                .toBundle();
        mSessionNotificationManager.onRangingRoundsUpdateStatus(uwbSession, params);
    }

    private void handleSetDataTransferPhaseConfig(UpdateSessionInfo info) {
        SessionHandle sessionHandle = info.sessionHandle;
        Integer sessionId = getSessionId(sessionHandle);
        UwbSession uwbSession = getUwbSession(sessionHandle);

        int sessionType = uwbSession.getSessionType();
        if (sessionType != FiraParams.SESSION_TYPE_RANGING_AND_IN_BAND_DATA
                && sessionType != FiraParams.SESSION_TYPE_DATA_TRANSFER
                && sessionType !=  FiraParams.SESSION_TYPE_IN_BAND_DATA_PHASE) {
            Log.e(TAG, "SetDataTransferPhaseConfig not applicable for session type: "
                    + sessionType);
            return;
        }

        FiraDataTransferPhaseConfig dataTransferPhaseConfig =
                FiraDataTransferPhaseConfig.fromBundle(info.params);

        List<FiraDataTransferPhaseManagementList> mDataTransferPhaseManagementList =
                dataTransferPhaseConfig.getDataTransferPhaseManagementList();
        int dataTransferManagementListSize = mDataTransferPhaseManagementList.size();
        int dataTransferControl = dataTransferPhaseConfig.getDataTransferControl();
        int slotBitmapSizeInBytes = 1 << ((dataTransferControl & 0X0F) >> 1);

        List<byte[]> macAddressList = new ArrayList<>();
        ByteBuffer slotBitmapByteBuffer = ByteBuffer.allocate(dataTransferManagementListSize
                * slotBitmapSizeInBytes);
        slotBitmapByteBuffer.order(ByteOrder.LITTLE_ENDIAN);

        int addressByteLength = ((dataTransferControl & 0x01)
                       == UwbUciConstants.DATA_TRANSFER_CONTROL_SHORT_MAC_ADDRESS)
                ? UwbAddress.SHORT_ADDRESS_BYTE_LENGTH : UwbAddress.EXTENDED_ADDRESS_BYTE_LENGTH;

        for (FiraDataTransferPhaseManagementList dataTransferPhaseManagementList :
                mDataTransferPhaseManagementList) {
            UwbAddress uwbAddress = dataTransferPhaseManagementList.getUwbAddress();
            byte[] slotBitMap = dataTransferPhaseManagementList.getSlotBitMap();
            if (uwbAddress != null && uwbAddress.size() == addressByteLength
                    && slotBitMap.length == slotBitmapSizeInBytes) {
                macAddressList.add(getComputedMacAddress(uwbAddress));
                slotBitmapByteBuffer.put(slotBitMap);
            } else {
                Log.e(TAG, "handleSetDataTransferPhaseConfig: slot bitmap size "
                            + "or address is not matching");
                return;
            }
        }

        // Check for buffer size mismatches
        if (slotBitmapByteBuffer.array().length
                != (slotBitmapSizeInBytes * dataTransferManagementListSize)
                || macAddressList.size() != dataTransferManagementListSize) {
            Log.e(TAG, "handleSetDataTransferPhaseConfig: slot bitmap buffer size or address list"
                    + " size mismatch");
            return;
        }

        // create session data transfer phase configuration task
        FutureTask<Integer> sessionDataTransferPhaseConfigTask = new FutureTask<>(
                (Callable<Integer>) () -> {
                    int status = UwbUciConstants.STATUS_CODE_FAILED;
                    synchronized (uwbSession.getWaitObj()) {
                        status = mNativeUwbManager.setDataTransferPhaseConfig(sessionId,
                                (byte) dataTransferPhaseConfig.getDtpcmRepetition(),
                                (byte) dataTransferControl,
                                (byte) dataTransferManagementListSize,
                                ArrayUtils.toPrimitive(macAddressList),
                                slotBitmapByteBuffer.array(),
                                uwbSession.getChipId());
                    }
                    return status;
                }
        );

        // execute task
        int status = UwbUciConstants.STATUS_CODE_FAILED;
        try {
            status = mUwbInjector.runTaskOnSingleThreadExecutor(sessionDataTransferPhaseConfigTask,
                    IUwbAdapter.SESSION_DATA_TRANSFER_PHASE_CONFIG_THRESHOLD_MS);
        } catch (TimeoutException e) {
            Log.i(TAG, "Failed to set session data transfer phase config : TIMEOUT");
            mSessionNotificationManager.onDataTransferPhaseConfigFailed(
                    uwbSession, UwbSessionNotificationHelper.convertUciStatusToParam(
                    uwbSession.getProtocolName(), status));
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }

        if (status != UwbUciConstants.STATUS_CODE_OK) {
            mSessionNotificationManager.onDataTransferPhaseConfigFailed(
                    uwbSession, UwbSessionNotificationHelper.convertUciStatusToParam(
                    uwbSession.getProtocolName(), status));
        }
    }

    void removeSession(UwbSession uwbSession) {
        if (uwbSession != null) {
            try {
                uwbSession.getBinder().unlinkToDeath(uwbSession, 0);
            } catch (NoSuchElementException e) {
                Log.e(TAG, "unlinkToDeath fail - sessionID : " + uwbSession.getSessionId());
            }
            removeAdvertiserData(uwbSession);
            uwbSession.close();
            removeFromNonPrivilegedUidToFiraSessionTableIfNecessary(uwbSession);
            if (!uwbSession.isDataDeliveryPermissionCheckNeeded()) {
                mUwbInjector.finishUwbRangingPermissionForDataDelivery(
                        uwbSession.getAttributionSource());
            }
            mSessionTokenMap.remove(uwbSession.getSessionId());
            mSessionTable.remove(uwbSession.getSessionHandle());
            mDbgRecentlyClosedSessions.add(uwbSession);
        }
    }

    private void removeAdvertiserData(UwbSession uwbSession) {
        for (long remoteMacAddress : uwbSession.getRemoteMacAddressList()) {
            mAdvertiseManager.removeAdvertiseTarget(remoteMacAddress);
        }
    }

    void addToNonPrivilegedUidToFiraSessionTableIfNecessary(@NonNull UwbSession uwbSession) {
        if (uwbSession.getSessionType() == UwbUciConstants.SESSION_TYPE_RANGING) {
            AttributionSource nonPrivilegedAppAttrSource =
                    uwbSession.getAnyNonPrivilegedAppInAttributionSource();
            if (nonPrivilegedAppAttrSource != null) {
                Log.d(TAG, "Detected start of non privileged FIRA session from "
                        + nonPrivilegedAppAttrSource);
                List<UwbSession> sessions = mNonPrivilegedUidToFiraSessionsTable.computeIfAbsent(
                        nonPrivilegedAppAttrSource.getUid(), v -> new ArrayList<>());
                sessions.add(uwbSession);
            }
        }
    }

    void removeFromNonPrivilegedUidToFiraSessionTableIfNecessary(@NonNull UwbSession uwbSession) {
        if (uwbSession.getSessionType() == UwbUciConstants.SESSION_TYPE_RANGING) {
            AttributionSource nonPrivilegedAppAttrSource =
                    uwbSession.getAnyNonPrivilegedAppInAttributionSource();
            if (nonPrivilegedAppAttrSource != null) {
                Log.d(TAG, "Detected end of non privileged FIRA session from "
                        + nonPrivilegedAppAttrSource);
                List<UwbSession> sessions = mNonPrivilegedUidToFiraSessionsTable.get(
                        nonPrivilegedAppAttrSource.getUid());
                if (sessions == null) {
                    Log.wtf(TAG, "No sessions found for uid: "
                            + nonPrivilegedAppAttrSource.getUid());
                    return;
                }
                sessions.remove(uwbSession);
                if (sessions.isEmpty()) {
                    mNonPrivilegedUidToFiraSessionsTable.remove(
                            nonPrivilegedAppAttrSource.getUid());
                }
            }
        }
    }

    private static class ReconfigureEventParams {
        public final UwbSession uwbSession;
        public final Params params;
        public final boolean triggeredByFgStateChange;

        ReconfigureEventParams(UwbSession uwbSession, Params params,
                boolean triggeredByFgStateChange) {
            this.uwbSession = uwbSession;
            this.params = params;
            this.triggeredByFgStateChange = triggeredByFgStateChange;
        }
    }

    private class EventTask extends Handler {

        EventTask(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            int type = msg.what;
            switch (type) {
                case SESSION_OPEN_RANGING: {
                    UwbSession uwbSession = (UwbSession) msg.obj;
                    handleOpenRanging(uwbSession);
                    break;
                }

                case SESSION_START_RANGING: {
                    UwbSession uwbSession = (UwbSession) msg.obj;
                    handleStartRanging(uwbSession);
                    break;
                }

                case SESSION_STOP_RANGING: {
                    UwbSession uwbSession = (UwbSession) msg.obj;
                    boolean triggeredBySystemPolicy = msg.arg1 == 1;
                    handleStopRanging(uwbSession, triggeredBySystemPolicy);
                    break;
                }

                case SESSION_RECONFIG_RANGING: {
                    Log.d(TAG, "SESSION_RECONFIG_RANGING");
                    ReconfigureEventParams params = (ReconfigureEventParams) msg.obj;
                    handleReconfigure(
                            params.uwbSession, params.params, params.triggeredByFgStateChange);
                    break;
                }

                case SESSION_DEINIT: {
                    SessionHandle sessionHandle = (SessionHandle) msg.obj;
                    int reason = msg.arg1;
                    handleDeInitWithReason(sessionHandle, reason);
                    break;
                }

                case SESSION_ON_DEINIT: {
                    UwbSession uwbSession = (UwbSession) msg.obj;
                    handleOnDeInit(uwbSession);
                    break;
                }

                case SESSION_SEND_DATA: {
                    Log.d(TAG, "SESSION_SEND_DATA");
                    SendDataInfo info = (SendDataInfo) msg.obj;
                    handleSendData(info);
                    break;
                }

                case SESSION_UPDATE_DT_TAG_RANGING_ROUNDS: {
                    Log.d(TAG, "SESSION_UPDATE_DT_TAG_RANGING_ROUNDS");
                    RangingRoundsUpdateDtTagInfo info = (RangingRoundsUpdateDtTagInfo) msg.obj;
                    handleRangingRoundsUpdateDtTag(info);
                    break;
                }

                case SESSION_DATA_TRANSFER_PHASE_CONFIG: {
                    Log.d(TAG, "SESSION_DATA_TRANSFER_PHASE_CONFIG");
                    UpdateSessionInfo info = (UpdateSessionInfo) msg.obj;
                    handleSetDataTransferPhaseConfig(info);
                    break;
                }

                default: {
                    Log.d(TAG, "EventTask : Undefined Task");
                    break;
                }
            }
        }

        public void execute(int task, Object obj) {
            Message msg = mEventTask.obtainMessage();
            msg.what = task;
            msg.obj = obj;
            this.sendMessage(msg);
        }

        public void execute(int task, Object obj, int arg1) {
            Message msg = mEventTask.obtainMessage();
            msg.what = task;
            msg.obj = obj;
            msg.arg1 = arg1;
            this.sendMessage(msg);
        }

        private void handleOpenRanging(UwbSession uwbSession) {
            Trace.beginSection("UWB#handleOpenRanging");
            // TODO(b/211445008): Consolidate to a single uwb thread.
            FutureTask<Integer> initSessionTask = new FutureTask<>(
                    () -> {
                        int status = UwbUciConstants.STATUS_CODE_FAILED;
                        synchronized (uwbSession.getWaitObj()) {
                            uwbSession.setOperationType(OPERATION_TYPE_INIT_SESSION);
                            status = mNativeUwbManager.initSession(
                                    uwbSession.getSessionId(),
                                    uwbSession.getSessionType(),
                                    uwbSession.getChipId());
                            if (status != UwbUciConstants.STATUS_CODE_OK) {
                                return status;
                            }
                            mSessionTokenMap.put(uwbSession.getSessionId(), mNativeUwbManager
                                    .getSessionToken(uwbSession.getSessionId(),
                                            uwbSession.getChipId()));
                            uwbSession.getWaitObj().blockingWait();
                            status = UwbUciConstants.STATUS_CODE_FAILED;
                            if (uwbSession.getSessionState()
                                    == UwbUciConstants.UWB_SESSION_STATE_INIT) {
                                uwbSession.setNeedsQueryUwbsTimestamp(
                                        null /* cccRangingStartParams */);
                                uwbSession.setAbsoluteInitiationTimeIfNeeded();
                                status = UwbSessionManager.this.setAppConfigurations(uwbSession);
                                uwbSession.resetAbsoluteInitiationTime();
                                if (status != UwbUciConstants.STATUS_CODE_OK) {
                                    return status;
                                }

                                uwbSession.getWaitObj().blockingWait();
                                status = UwbUciConstants.STATUS_CODE_FAILED;
                                if (uwbSession.getSessionState()
                                        == UwbUciConstants.UWB_SESSION_STATE_IDLE) {
                                    mSessionNotificationManager.onRangingOpened(uwbSession);
                                    status = UwbUciConstants.STATUS_CODE_OK;
                                } else {
                                    status = UwbUciConstants.STATUS_CODE_FAILED;
                                }
                                return status;
                            }
                            return status;
                        }
                    });

            int status = UwbUciConstants.STATUS_CODE_FAILED;
            try {
                status = mUwbInjector.runTaskOnSingleThreadExecutor(initSessionTask,
                        IUwbAdapter.RANGING_SESSION_OPEN_THRESHOLD_MS);
            } catch (TimeoutException e) {
                Log.i(TAG, "Failed to initialize session - status : TIMEOUT");
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (ExecutionException e) {
                e.printStackTrace();
            }

            mUwbMetrics.logRangingInitEvent(uwbSession, status);
            if (status != UwbUciConstants.STATUS_CODE_OK) {
                Log.i(TAG, "Failed to initialize session - status : " + status);
                mSessionNotificationManager.onRangingOpenFailed(uwbSession, status);
                uwbSession.setOperationType(SESSION_ON_DEINIT);
                mNativeUwbManager.deInitSession(uwbSession.getSessionId(), uwbSession.getChipId());
                removeSession(uwbSession);
            }
            Log.i(TAG, "sessionInit() : finish - sessionId : " + uwbSession.getSessionId());
            Trace.endSection();
        }

        private void handleStartRanging(UwbSession uwbSession) {
            Trace.beginSection("UWB#handleStartRanging");
            // TODO(b/211445008): Consolidate to a single uwb thread.
            FutureTask<Integer> startRangingTask = new FutureTask<>(
                    () -> {
                        int status = UwbUciConstants.STATUS_CODE_FAILED;
                        synchronized (uwbSession.getWaitObj()) {
                            uwbSession.setAbsoluteInitiationTimeIfNeeded();
                            if (uwbSession.getNeedsAppConfigUpdate()) {
                                uwbSession.resetNeedsAppConfigUpdate();
                                status = mConfigurationManager.setAppConfigurations(
                                        uwbSession.getSessionId(),
                                        uwbSession.getParams(), uwbSession.getChipId(),
                                        getUwbsFiraProtocolVersion(uwbSession.getChipId()));
                                uwbSession.resetAbsoluteInitiationTime();
                                if (status != UwbUciConstants.STATUS_CODE_OK) {
                                    mSessionNotificationManager.onRangingStartFailed(
                                            uwbSession, status);
                                    return status;
                                }
                            }

                            uwbSession.setOperationType(SESSION_START_RANGING);
                            status = mNativeUwbManager.startRanging(uwbSession.getSessionId(),
                                    uwbSession.getChipId());
                            if (status != UwbUciConstants.STATUS_CODE_OK) {
                                mSessionNotificationManager.onRangingStartFailed(
                                        uwbSession, status);
                                return status;
                            }
                            uwbSession.getWaitObj().blockingWait();
                            if (uwbSession.getSessionState()
                                    == UwbUciConstants.UWB_SESSION_STATE_ACTIVE) {
                                // TODO: Ensure |rangingStartedParams| is valid for FIRA sessions
                                // as well.
                                Params rangingStartedParams = uwbSession.getParams();

                                // For ALIRO sessions, retrieve the app configs
                                if (uwbSession.getProtocolName().equals(
                                        AliroParams.PROTOCOL_NAME)) {
                                    Pair<Integer, AliroRangingStartedParams> statusAndParams  =
                                            mConfigurationManager.getAppConfigurations(
                                                    uwbSession.getSessionId(),
                                                    AliroParams.PROTOCOL_NAME,
                                                    new byte[0],
                                                    AliroRangingStartedParams.class,
                                                    uwbSession.getChipId(),
                                                    AliroParams.PROTOCOL_VERSION_1_0);
                                    if (statusAndParams.first != UwbUciConstants.STATUS_CODE_OK) {
                                        Log.e(TAG, "Failed to get ALIRO ranging started params");
                                    }
                                    rangingStartedParams = statusAndParams.second;
                                }

                                // For CCC sessions, retrieve the app configs
                                if (uwbSession.getProtocolName().equals(CccParams.PROTOCOL_NAME)) {
                                    Pair<Integer, CccRangingStartedParams> statusAndParams  =
                                            mConfigurationManager.getAppConfigurations(
                                                    uwbSession.getSessionId(),
                                                    CccParams.PROTOCOL_NAME,
                                                    new byte[0],
                                                    CccRangingStartedParams.class,
                                                    uwbSession.getChipId(),
                                                    CccParams.PROTOCOL_VERSION_1_0);
                                    if (statusAndParams.first != UwbUciConstants.STATUS_CODE_OK) {
                                        Log.e(TAG, "Failed to get CCC ranging started params");
                                    }
                                    rangingStartedParams = statusAndParams.second;
                                }

                                mSessionNotificationManager.onRangingStarted(
                                        uwbSession, rangingStartedParams);
                                if (uwbSession.hasNonPrivilegedApp()
                                        && !uwbSession.hasNonPrivilegedFgAppOrService()) {
                                    Log.i(TAG, "Session " + uwbSession.getSessionId()
                                            + " reconfiguring ntf control due to app state change");
                                    uwbSession.reconfigureFiraSessionOnFgStateChange();
                                }
                            } else {
                                int reasonCode = uwbSession.getLastSessionStatusNtfReasonCode();
                                status =
                                        UwbSessionNotificationHelper.convertUciReasonCodeToUciStatusCode(
                                               reasonCode);
                                mSessionNotificationManager.onRangingStartFailedWithUciReasonCode(
                                        uwbSession, reasonCode);
                            }
                        }
                        return status;
                    });
            int status = UwbUciConstants.STATUS_CODE_FAILED;
            try {
                status = mUwbInjector.runTaskOnSingleThreadExecutor(startRangingTask,
                        IUwbAdapter.RANGING_SESSION_START_THRESHOLD_MS);
            } catch (TimeoutException e) {
                Log.i(TAG, "Failed to Start Ranging - status : TIMEOUT");
                mSessionNotificationManager.onRangingStartFailed(
                        uwbSession, UwbUciConstants.STATUS_CODE_FAILED);
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (ExecutionException e) {
                e.printStackTrace();
            }
            mUwbMetrics.longRangingStartEvent(uwbSession, status);
            Trace.endSection();
        }

        private void handleStopRanging(UwbSession uwbSession, boolean triggeredBySystemPolicy) {
            Trace.beginSection("UWB#handleStopRanging");
            // TODO(b/211445008): Consolidate to a single uwb thread.
            FutureTask<Integer> stopRangingTask = new FutureTask<>(
                    () -> {
                        int status = UwbUciConstants.STATUS_CODE_FAILED;
                        synchronized (uwbSession.getWaitObj()) {
                            uwbSession.setOperationType(SESSION_STOP_RANGING);
                            status = mNativeUwbManager.stopRanging(uwbSession.getSessionId(),
                                    uwbSession.getChipId());
                            if (status != UwbUciConstants.STATUS_CODE_OK) {
                                if (uwbSession.getSessionState()
                                        == UwbUciConstants.UWB_SESSION_STATE_IDLE) {
                                    handleStopRangingParams(uwbSession, true /*systemPolicy*/);
                                    return UwbUciConstants.STATUS_CODE_OK;
                                }
                                mSessionNotificationManager.onRangingStopFailed(uwbSession, status);
                                return status;
                            }
                            uwbSession.getWaitObj().blockingWait();
                            if (uwbSession.getSessionState()
                                    == UwbUciConstants.UWB_SESSION_STATE_IDLE) {
                                handleStopRangingParams(uwbSession, triggeredBySystemPolicy);
                            } else {
                                status = UwbUciConstants.STATUS_CODE_FAILED;
                                mSessionNotificationManager.onRangingStopFailed(uwbSession,
                                        status);
                            }
                        }
                        return status;
                    });


            int status = UwbUciConstants.STATUS_CODE_FAILED;
            int timeoutMs = IUwbAdapter.RANGING_SESSION_START_THRESHOLD_MS;
            if (uwbSession.getProtocolName().equals(PROTOCOL_NAME)) {
                int minTimeoutNecessary = uwbSession.getCurrentFiraRangingIntervalMs() * 4;
                timeoutMs = timeoutMs > minTimeoutNecessary ? timeoutMs : minTimeoutNecessary;
            }
            Log.v(TAG, "Stop timeout: " + timeoutMs);
            try {
                status = mUwbInjector.runTaskOnSingleThreadExecutor(stopRangingTask, timeoutMs);
            } catch (TimeoutException e) {
                Log.i(TAG, "Failed to Stop Ranging - status : TIMEOUT");
                mSessionNotificationManager.onRangingStopFailed(
                        uwbSession, UwbUciConstants.STATUS_CODE_FAILED);
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (ExecutionException e) {
                e.printStackTrace();
            }
            if (status != UwbUciConstants.STATUS_CODE_FAILED) {
                mUwbMetrics.longRangingStopEvent(uwbSession);
            }
            // Reset all UWB session timers when the session is stopped.
            uwbSession.stopTimers();
            removeAdvertiserData(uwbSession);
            Trace.endSection();
        }

        private void handleStopRangingParams(UwbSession uwbSession,
                boolean triggeredBySystemPolicy) {
            PersistableBundle rangingStoppedParamsBundle = new PersistableBundle();

            // For ALIRO sessions, retrieve the app configs
            if (uwbSession.getProtocolName().equals(AliroParams.PROTOCOL_NAME)
                    && mUwbInjector.getDeviceConfigFacade()
                    .isCccRangingStoppedParamsSendEnabled()) { // Use CCC Flag for ALIRO.
                Pair<Integer, AliroRangingStoppedParams> statusAndParams  =
                        mConfigurationManager.getAppConfigurations(
                                uwbSession.getSessionId(),
                                AliroParams.PROTOCOL_NAME,
                                new byte[0],
                                AliroRangingStoppedParams.class,
                                uwbSession.getChipId(),
                                AliroParams.PROTOCOL_VERSION_1_0);
                if (statusAndParams.first != UwbUciConstants.STATUS_CODE_OK) {
                    Log.e(TAG, "Failed to get ALIRO ranging stopped params");
                }
                rangingStoppedParamsBundle = statusAndParams.second.toBundle();
            }

            // For CCC sessions, retrieve the app configs
            if (uwbSession.getProtocolName().equals(CccParams.PROTOCOL_NAME)
                    && mUwbInjector.getDeviceConfigFacade()
                    .isCccRangingStoppedParamsSendEnabled()) {
                Pair<Integer, CccRangingStoppedParams> statusAndParams  =
                        mConfigurationManager.getAppConfigurations(
                                uwbSession.getSessionId(),
                                CccParams.PROTOCOL_NAME,
                                new byte[0],
                                CccRangingStoppedParams.class,
                                uwbSession.getChipId(),
                                CccParams.PROTOCOL_VERSION_1_0);
                if (statusAndParams.first != UwbUciConstants.STATUS_CODE_OK) {
                    Log.e(TAG, "Failed to get CCC ranging stopped params");
                }
                rangingStoppedParamsBundle = statusAndParams.second.toBundle();
            }

            int apiReasonCode = triggeredBySystemPolicy
                    ? RangingChangeReason.SYSTEM_POLICY
                    : RangingChangeReason.LOCAL_API;
            mSessionNotificationManager.onRangingStoppedWithApiReasonCode(
                    uwbSession, apiReasonCode, rangingStoppedParamsBundle);
        }

        private void suspendRangingCallbacks(int suspendRangingRounds, int status,
                UwbSession uwbSession) {
            if (suspendRangingRounds == FiraParams.SUSPEND_RANGING_ENABLED) {
                if (status == UwbUciConstants.STATUS_CODE_OK) {
                    mSessionNotificationManager.onRangingPaused(uwbSession);
                } else {
                    mSessionNotificationManager.onRangingPauseFailed(uwbSession, status);
                }
            } else if (suspendRangingRounds == FiraParams.SUSPEND_RANGING_DISABLED) {
                if (status == UwbUciConstants.STATUS_CODE_OK) {
                    mSessionNotificationManager.onRangingResumed(uwbSession);
                } else {
                    mSessionNotificationManager.onRangingResumeFailed(uwbSession, status);
                }
            }
        }

        private void handleReconfigure(UwbSession uwbSession, @Nullable Params param,
                boolean triggeredByFgStateChange) {
            if (!(param instanceof FiraRangingReconfigureParams)) {
                Log.e(TAG, "Invalid reconfigure params: " + param);
                mSessionNotificationManager.onRangingReconfigureFailed(
                        uwbSession, UwbUciConstants.STATUS_CODE_INVALID_PARAM);
                return;
            }
            Trace.beginSection("UWB#handleReconfigure");
            FiraRangingReconfigureParams rangingReconfigureParams =
                    (FiraRangingReconfigureParams) param;
            // TODO(b/211445008): Consolidate to a single uwb thread.
            FutureTask<Integer> cmdTask = new FutureTask<>(
                    () -> {
                        int status = UwbUciConstants.STATUS_CODE_FAILED;
                        synchronized (uwbSession.getWaitObj()) {
                            // Handle SESSION_UPDATE_CONTROLLER_MULTICAST_LIST_CMD
                            UwbAddress[] addrList = rangingReconfigureParams.getAddressList();
                            Integer action = rangingReconfigureParams.getAction();
                            // Action will indicate if this is a controlee add/remove.
                            //  if null, it's a session configuration change.
                            if (action != null) {
                                if (addrList == null) {
                                    Log.e(TAG,
                                            "Multicast update missing the address list.");
                                    return status;
                                }
                                int dstAddressListSize = addrList.length;
                                List<byte[]> dstAddressList = new ArrayList<>();
                                for (UwbAddress address : addrList) {
                                    dstAddressList.add(getComputedMacAddress(address));
                                }
                                int[] subSessionIdList;
                                if (!ArrayUtils.isEmpty(
                                        rangingReconfigureParams.getSubSessionIdList())) {
                                    subSessionIdList =
                                        rangingReconfigureParams.getSubSessionIdList();
                                } else {
                                    // Set to 0's for the UCI stack.
                                    subSessionIdList = new int[dstAddressListSize];
                                }
                                boolean isV2 = action
                                        == P_STS_MULTICAST_LIST_UPDATE_ACTION_ADD_16_BYTE
                                        || action
                                        == P_STS_MULTICAST_LIST_UPDATE_ACTION_ADD_32_BYTE;
                                status = mNativeUwbManager.controllerMulticastListUpdate(
                                        uwbSession.getSessionId(),
                                        action,
                                        subSessionIdList.length,
                                        ArrayUtils.toPrimitive(dstAddressList),
                                        subSessionIdList,
                                        isV2 ? rangingReconfigureParams
                                                .getSubSessionKeyList() : null,
                                        uwbSession.getChipId());
                                if (status != UwbUciConstants.STATUS_CODE_OK) {
                                    Log.e(TAG, "Unable to update controller multicast list.");
                                    if (isMulticastActionAdd(action)) {
                                        mSessionNotificationManager.onControleeAddFailed(
                                                uwbSession, status);
                                    } else if (action == MULTICAST_LIST_UPDATE_ACTION_DELETE) {
                                        mSessionNotificationManager.onControleeRemoveFailed(
                                                uwbSession, status);
                                    }
                                    return status;
                                }

                                uwbSession.getWaitObj().blockingWait();

                                UwbMulticastListUpdateStatus multicastList =
                                        uwbSession.getMulticastListUpdateStatus();

                                if (multicastList == null) {
                                    Log.e(TAG, "Confirmed controller multicast list is empty!");
                                    return status;
                                }

                                for (int i = 0; i < multicastList.getNumOfControlee(); i++) {
                                    int actionStatus = multicastList.getStatus()[i];
                                    if (actionStatus == UwbUciConstants.STATUS_CODE_OK) {
                                        if (isMulticastActionAdd(action)) {
                                            uwbSession.addControlee(
                                                    multicastList.getControleeUwbAddresses()[i]);
                                            mSessionNotificationManager.onControleeAdded(
                                                    uwbSession);
                                        } else if (action == MULTICAST_LIST_UPDATE_ACTION_DELETE) {
                                            uwbSession.removeControlee(
                                                    multicastList.getControleeUwbAddresses()[i]);
                                            mSessionNotificationManager.onControleeRemoved(
                                                    uwbSession);
                                        }
                                    }
                                    else {
                                        status = actionStatus;
                                        if (isMulticastActionAdd(action)) {
                                            mSessionNotificationManager.onControleeAddFailed(
                                                    uwbSession, actionStatus);
                                        } else if (action == MULTICAST_LIST_UPDATE_ACTION_DELETE) {
                                            mSessionNotificationManager.onControleeRemoveFailed(
                                                    uwbSession, actionStatus);
                                        }
                                    }
                                }
                            } else {
                                // setAppConfigurations only applies to config changes,
                                //  not controlee list changes
                                status = mConfigurationManager.setAppConfigurations(
                                        uwbSession.getSessionId(), param, uwbSession.getChipId(),
                                        getUwbsFiraProtocolVersion(uwbSession.getChipId()));
                                // send suspendRangingCallbacks only on suspend ranging reconfigure
                                Integer suspendRangingRounds =
                                    rangingReconfigureParams.getSuspendRangingRounds();
                                if (suspendRangingRounds != null) {
                                    suspendRangingCallbacks(suspendRangingRounds, status,
                                        uwbSession);
                                }
                            }
                            if (status == UwbUciConstants.STATUS_CODE_OK) {
                                // only call this if all controlees succeeded otherwise the
                                //  fail status cause a onRangingReconfigureFailed later.
                                if (!triggeredByFgStateChange) {
                                    mSessionNotificationManager.onRangingReconfigured(uwbSession);
                                }
                            }
                            Log.d(TAG, "Multicast update status: " + status);
                            return status;
                        }
                    });
            int status = UwbUciConstants.STATUS_CODE_FAILED;
            try {
                status = mUwbInjector.runTaskOnSingleThreadExecutor(cmdTask,
                        IUwbAdapter.RANGING_SESSION_OPEN_THRESHOLD_MS);
            } catch (TimeoutException e) {
                Log.i(TAG, "Failed to Reconfigure - status : TIMEOUT");
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (ExecutionException e) {
                e.printStackTrace();
            }
            if (status != UwbUciConstants.STATUS_CODE_OK) {
                Log.i(TAG, "Failed to Reconfigure : " + status);
                if (!triggeredByFgStateChange) {
                    mSessionNotificationManager.onRangingReconfigureFailed(uwbSession, status);
                }
            }
            Trace.endSection();
        }

        private boolean isMulticastActionAdd(Integer action) {
            return action == MULTICAST_LIST_UPDATE_ACTION_ADD
                    || action == P_STS_MULTICAST_LIST_UPDATE_ACTION_ADD_16_BYTE
                    || action == P_STS_MULTICAST_LIST_UPDATE_ACTION_ADD_32_BYTE;
        }

        private void handleDeInitWithReason(SessionHandle sessionHandle, int reason) {
            Trace.beginSection("UWB#handleDeInitWithReason");
            UwbSession uwbSession = getUwbSession(sessionHandle);
            if (uwbSession == null) {
                Log.w(TAG, "handleDeInitWithReason(): UWB session not found for sessionHandle: "
                        + sessionHandle);
                return;
            }

            // TODO(b/211445008): Consolidate to a single uwb thread.
            FutureTask<Integer> deInitTask = new FutureTask<>(
                    (Callable<Integer>) () -> {
                        int status = UwbUciConstants.STATUS_CODE_FAILED;
                        synchronized (uwbSession.getWaitObj()) {
                            status = mNativeUwbManager.deInitSession(uwbSession.getSessionId(),
                                    uwbSession.getChipId());
                            if (status != UwbUciConstants.STATUS_CODE_OK) {
                                return status;
                            }
                            uwbSession.getWaitObj().blockingWait();
                        }
                        return status;
                    });

            int status = UwbUciConstants.STATUS_CODE_FAILED;
            try {
                status = mUwbInjector.runTaskOnSingleThreadExecutor(deInitTask,
                        IUwbAdapter.RANGING_SESSION_CLOSE_THRESHOLD_MS);
            } catch (TimeoutException e) {
                Log.i(TAG, "Failed to Stop Ranging - status : TIMEOUT");
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }
            mUwbMetrics.logRangingCloseEvent(uwbSession, status);

            // Reset all UWB session timers when the session is de-initialized (ie, closed).
            uwbSession.stopTimers();
            removeSession(uwbSession);

            // Notify about Session closure after removing it from the SessionTable.
            Log.i(TAG, "onRangingClosed - status : " + status);
            mSessionNotificationManager.onRangingClosed(uwbSession,
                    status == STATUS_CODE_OK ? reason : status);

            Log.i(TAG, "deinit finish : status :" + status);
            Trace.endSection();
        }

        private void handleSendData(SendDataInfo sendDataInfo) {
            int status = UwbUciConstants.STATUS_CODE_ERROR_SESSION_NOT_EXIST;
            SessionHandle sessionHandle = sendDataInfo.sessionHandle;
            if (sessionHandle == null) {
                Log.i(TAG, "Not present sessionHandle");
                mSessionNotificationManager.onDataSendFailed(
                        null, sendDataInfo.remoteDeviceAddress, status, sendDataInfo.params);
                return;
            }

            Integer sessionId = getSessionId(sessionHandle);
            if (sessionId == null) {
                Log.i(TAG, "UwbSessionId not found");
                mSessionNotificationManager.onDataSendFailed(
                        null, sendDataInfo.remoteDeviceAddress, status, sendDataInfo.params);
                return;
            }

            // TODO(b/256675656): Check if there is race condition between uwbSession being
            // retrieved here and used below (and similar for uwbSession being stored in the
            //  mLooper message and being used during processing for all other message types).
            UwbSession uwbSession = getUwbSession(sessionId);
            if (uwbSession == null) {
                Log.i(TAG, "UwbSession not found");
                mSessionNotificationManager.onDataSendFailed(
                        null, sendDataInfo.remoteDeviceAddress, status, sendDataInfo.params);
                return;
            }

            // TODO(b/211445008): Consolidate to a single uwb thread.
            FutureTask<Integer> sendDataTask = new FutureTask<>((Callable<Integer>) () -> {
                int sendDataStatus = UwbUciConstants.STATUS_CODE_FAILED;
                synchronized (uwbSession.getWaitObj()) {
                    if (!isValidUwbSessionForApplicationDataTransfer(uwbSession)) {
                        sendDataStatus = UwbUciConstants.STATUS_CODE_FAILED;
                        Log.i(TAG, "UwbSession not in active state");
                        mSessionNotificationManager.onDataSendFailed(
                                uwbSession, sendDataInfo.remoteDeviceAddress, sendDataStatus,
                                sendDataInfo.params);
                        return sendDataStatus;
                    }
                    if (!isValidSendDataInfo(sendDataInfo)) {
                        sendDataStatus = UwbUciConstants.STATUS_CODE_INVALID_PARAM;
                        mSessionNotificationManager.onDataSendFailed(
                                uwbSession, sendDataInfo.remoteDeviceAddress, sendDataStatus,
                                sendDataInfo.params);
                        return sendDataStatus;
                    }

                    // Get the UCI sequence number for this data packet, and store it.
                    short sequenceNum = uwbSession.getAndIncrementDataSndSequenceNumber();
                    uwbSession.addSendDataInfo(sequenceNum, sendDataInfo);

                    sendDataStatus = mNativeUwbManager.sendData(
                            uwbSession.getSessionId(),
                            DataTypeConversionUtil.convertShortMacAddressBytesToExtended(
                                    sendDataInfo.remoteDeviceAddress.toBytes()),
                            sequenceNum, sendDataInfo.data, uwbSession.getChipId());
                    mUwbMetrics.logDataTx(uwbSession, sendDataStatus);
                    if (sendDataStatus != STATUS_CODE_OK) {
                        Log.e(TAG, "MSG_SESSION_SEND_DATA error status: " + sendDataStatus
                                + " for data packet sessionId: " + sessionId
                                + ", sequence number: " + sequenceNum);
                        mSessionNotificationManager.onDataSendFailed(
                                uwbSession, sendDataInfo.remoteDeviceAddress, sendDataStatus,
                                sendDataInfo.params);
                        uwbSession.removeSendDataInfo(sequenceNum);
                    }
                    return sendDataStatus;
                }
            });

            status = UwbUciConstants.STATUS_CODE_FAILED;
            try {
                status = mUwbInjector.runTaskOnSingleThreadExecutor(sendDataTask,
                        IUwbAdapter.RANGING_SESSION_OPEN_THRESHOLD_MS);
            } catch (TimeoutException e) {
                Log.i(TAG, "Failed to Send data - status : TIMEOUT");
                mSessionNotificationManager.onDataSendFailed(uwbSession,
                        sendDataInfo.remoteDeviceAddress, status, sendDataInfo.params);
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }
        }
    }

    private boolean isValidUwbSessionForOwrAoaRanging(UwbSession uwbSession) {
        Params params = uwbSession.getParams();
        if (params instanceof FiraOpenSessionParams) {
            FiraOpenSessionParams firaParams = (FiraOpenSessionParams) params;
            if (firaParams.getRangingRoundUsage() != ROUND_USAGE_OWR_AOA_MEASUREMENT) {
                Log.i(TAG, "OwR Aoa UwbSession: Invalid ranging round usage value = "
                        + firaParams.getRangingRoundUsage());
                return false;
            }
            if (firaParams.getDeviceRole() != RANGING_DEVICE_ROLE_OBSERVER) {
                Log.i(TAG, "OwR Aoa UwbSession: Invalid device role value = "
                        + firaParams.getDeviceRole());
                return false;
            }
            return true;
        }
        return false;
    }

    private boolean isValidUwbSessionForApplicationDataTransfer(UwbSession uwbSession) {
        // The session state must be SESSION_STATE_ACTIVE, as that's required to transmit or receive
        // application data.
        return uwbSession != null && uwbSession.getSessionState() == UWB_SESSION_STATE_ACTIVE;
    }

    private boolean isValidSendDataInfo(SendDataInfo sendDataInfo) {
        if (sendDataInfo.data == null) {
            return false;
        }

        if (sendDataInfo.remoteDeviceAddress == null) {
            return false;
        }

        if (sendDataInfo.remoteDeviceAddress.size()
                > UwbUciConstants.UWB_DEVICE_EXT_MAC_ADDRESS_LEN) {
            return false;
        }
        return true;
    }

    protected FiraProtocolVersion getUwbsFiraProtocolVersion(String chipId) {
        UwbDeviceInfoResponse deviceInfo =
                mUwbInjector.getUwbServiceCore().getCachedDeviceInfoResponse(chipId);
        if (deviceInfo != null) {
            return FiraProtocolVersion.fromLEShort((short) deviceInfo.mUciVersion);
        }

        // Return a (safe) backward-compatible FiraProtocolVersion if we couldn't retrieve it
        // from the UWBS.
        return FiraParams.PROTOCOL_VERSION_1_1;
    }

    /** Represents a UWB session */
    public class UwbSession implements IBinder.DeathRecipient, Closeable {
        @VisibleForTesting
        public static final long RANGING_RESULT_ERROR_NO_TIMEOUT = 0;
        private static final String RANGING_RESULT_ERROR_STREAK_TIMER_TAG =
                "UwbSessionRangingResultError";
        private static final long NON_PRIVILEGED_BG_APP_TIMEOUT_MS = 120_000;
        @VisibleForTesting
        public static final String NON_PRIVILEGED_BG_APP_TIMER_TAG =
                "UwbSessionNonPrivilegedBgAppError";
        @VisibleForTesting
        static final int ALIRO_SESSION_PRIORITY = 80;
        @VisibleForTesting
        static final int CCC_SESSION_PRIORITY = 80;
        @VisibleForTesting
        static final int SYSTEM_APP_SESSION_PRIORITY = 70;
        @VisibleForTesting
        static final int FG_SESSION_PRIORITY = 60;
        // Default session priority value needs to be different from other session priority buckets,
        // so we can detect overrides from the shell or System API.
        @VisibleForTesting
        static final int DEFAULT_SESSION_PRIORITY = 50;
        @VisibleForTesting
        static final int BG_SESSION_PRIORITY = 40;

        private final AttributionSource mAttributionSource;
        private final SessionHandle mSessionHandle;
        private final int mSessionId;
        private final byte mSessionType;
        private final int mRangingRoundUsage;
        private final IUwbRangingCallbacks mIUwbRangingCallbacks;
        private final String mProtocolName;
        private final IBinder mIBinder;
        private final WaitObj mWaitObj;
        private final AttributionSource mNonPrivilegedAppInAttributionSource;
        private boolean mAcquiredDefaultPose = false;
        private Params mParams;
        private int mSessionState;
        // Session priority as tracked by the UWB stack that changes based on the requesting
        // app/service bg/fg state changes. Note, it will differ from the Fira SESSION_PRIORITY
        // param given to UWBS if the state changed after the session became active.
        private int mStackSessionPriority;
        private boolean mSessionPriorityOverride = false;
        private boolean mNeedsAppConfigUpdate = false;
        private boolean mNeedsQueryUwbsTimestamp = false;
        private UwbMulticastListUpdateStatus mMulticastListUpdateStatus;
        private final int mProfileType;
        private AlarmManager.OnAlarmListener mRangingResultErrorStreakTimerListener;
        private AlarmManager.OnAlarmListener mNonPrivilegedBgAppTimerListener;
        private int mOperationType = OPERATION_TYPE_INIT_SESSION;
        private final String mChipId;
        private boolean mHasNonPrivilegedFgAppOrService = false;
        private long mRangingErrorStreakTimeoutMs = RANGING_RESULT_ERROR_NO_TIMEOUT;
        // Use a Map<RemoteMacAddress, SortedMap<SequenceNumber, ReceivedDataInfo>> to store all
        // the Application payload data packets received in this (active) UWB Session.
        // - The outer key (RemoteMacAddress) is used to identify the Advertiser device that sends
        //   the data (there can be multiple advertisers in the same UWB session).
        // - The inner key (SequenceNumber) is used to ensure we don't store duplicate packets,
        //   and notify them to the higher layers in-order.
        // TODO(b/270068278): Change the type of SequenceNumber from Long to Integer everywhere.
        private final ConcurrentHashMap<Long, SortedMap<Long, ReceivedDataInfo>>
                mReceivedDataInfoMap;
        private IPoseSource mPoseSource;
        // Application data repetition count
        private int mDataRepetitionCount;

        // Store the UCI sequence number for the next Data packet (to be sent to UWBS).
        private short mDataSndSequenceNumber;
        // Store a Map<SequenceNumber, SendDataInfo>, for every Data packet (sent to UWBS). It's
        // used when the corresponding DataTransferStatusNtf is received (from UWBS).
        private final ConcurrentHashMap<Long, SendDataInfo> mSendDataInfoMap;

        // Whether data delivery permission check is needed for the ranging session.
        private boolean mDataDeliveryPermissionCheckNeeded = true;

        // reasonCode from the last received SESSION_STATUS_NTF for this session.
        private int mLastSessionStatusNtfReasonCode = -1;

        @VisibleForTesting
        public List<UwbControlee> mControleeList;

        UwbSession(AttributionSource attributionSource, SessionHandle sessionHandle, int sessionId,
                byte sessionType, String protocolName, Params params,
                IUwbRangingCallbacks iUwbRangingCallbacks, String chipId) {
            this.mAttributionSource = attributionSource;
            this.mSessionHandle = sessionHandle;
            this.mSessionId = sessionId;
            this.mSessionType = sessionType;
            this.mProtocolName = protocolName;
            this.mIUwbRangingCallbacks = iUwbRangingCallbacks;
            this.mIBinder = iUwbRangingCallbacks.asBinder();
            this.mSessionState = UwbUciConstants.UWB_SESSION_STATE_DEINIT;
            this.mParams = params;
            this.mWaitObj = new WaitObj();
            this.mProfileType = convertProtolNameToProfileType(protocolName);
            this.mChipId = chipId;
            this.mNonPrivilegedAppInAttributionSource =
                    getAnyNonPrivilegedAppInAttributionSourceInternal();
            this.mStackSessionPriority = calculateSessionPriority();

            if (params instanceof FiraOpenSessionParams) {
                FiraOpenSessionParams firaParams = (FiraOpenSessionParams) params;

                this.mRangingRoundUsage = firaParams.getRangingRoundUsage();

                // Set up pose sources before we start creating UwbControlees.
                switch (firaParams.getFilterType()) {
                    case FILTER_TYPE_DEFAULT:
                        this.mPoseSource = mUwbInjector.acquirePoseSource();
                        this.mAcquiredDefaultPose = true;
                        break;
                    case FILTER_TYPE_APPLICATION:
                        this.mPoseSource = new ApplicationPoseSource();
                        break;
                }

                if (firaParams.getDestAddressList() != null) {
                    // Set up list of all controlees involved.
                    mControleeList = firaParams.getDestAddressList().stream()
                            .map(addr -> new UwbControlee(addr, createFilterEngine(), mUwbInjector))
                            .collect(Collectors.toList());
                }
                mRangingErrorStreakTimeoutMs = firaParams
                        .getRangingErrorStreakTimeoutMs();

                // Add stack calculated session priority to Fira open session params. The stack
                // session priority might change later based on fg/bg state changes, but the
                // SESSION_PRIORITY given to the UWBS on open session will stay the same since
                // UWBS doesn't support reconfiguring session priority while the session is active.
                // In case the session stops being active, session priority will update on next
                // start ranging call.
                if (firaParams.getSessionPriority() != DEFAULT_SESSION_PRIORITY) {
                    mSessionPriorityOverride = true;
                    mStackSessionPriority = firaParams.getSessionPriority();
                } else {
                    mParams = firaParams.toBuilder().setSessionPriority(
                            mStackSessionPriority).build();
                }
                this.mDataRepetitionCount = firaParams.getDataRepetitionCount();
            } else {
                this.mRangingRoundUsage = -1;
                this.mDataRepetitionCount = 0;
            }

            this.mReceivedDataInfoMap = new ConcurrentHashMap<>();
            this.mDataSndSequenceNumber = 0;
            this.mSendDataInfoMap = new ConcurrentHashMap<>();
        }

        /**
         * Calculates the priority of the session based on the protocol type and the originating
         * app/service requesting the session.
         *
         * Session priority ranking order (from highest to lowest priority):
         *  1. Any CCC session
         *  2. Any System app/service
         *  3. Other apps/services running in Foreground
         *  4. Other apps/services running in Background
         */
        public int calculateSessionPriority() {
            if (mProtocolName.equals(AliroParams.PROTOCOL_NAME)) {
                return ALIRO_SESSION_PRIORITY;
            }
            if (mProtocolName.equals(CccParams.PROTOCOL_NAME)) {
                return CCC_SESSION_PRIORITY;
            }
            if (mNonPrivilegedAppInAttributionSource == null) {
                return SYSTEM_APP_SESSION_PRIORITY;
            }
            boolean isFgAppOrService = mUwbInjector.isForegroundAppOrService(
                    mNonPrivilegedAppInAttributionSource.getUid(),
                    mNonPrivilegedAppInAttributionSource.getPackageName());
            if (isFgAppOrService) {
                return FG_SESSION_PRIORITY;
            }
            return BG_SESSION_PRIORITY;
        }

        private boolean isPrivilegedApp(int uid, String packageName) {
            return mUwbInjector.isSystemApp(uid, packageName)
                    || mUwbInjector.isAppSignedWithPlatformKey(uid);
        }

        /**
         * Check the attribution source chain to check if there are any 3p apps.
         * @return AttributionSource of first non-system app found in the chain, null otherwise.
         */
        @Nullable
        private AttributionSource getAnyNonPrivilegedAppInAttributionSourceInternal() {
            // Iterate attribution source chain to ensure that there is no non-fg 3p app in the
            // request.
            AttributionSource attributionSource = mAttributionSource;
            while (attributionSource != null) {
                int uid = attributionSource.getUid();
                String packageName = attributionSource.getPackageName();
                if (!isPrivilegedApp(uid, packageName)) {
                    return attributionSource;
                }
                attributionSource = attributionSource.getNext();
            }
            return null;
        }

        /**
         * Check the attribution source chain to check if there are any 3p apps.
         * @return AttributionSource of first non-system app found in the chain, null otherwise.
         */
        @Nullable
        public AttributionSource getAnyNonPrivilegedAppInAttributionSource() {
            return mNonPrivilegedAppInAttributionSource;
        }

        /**
         * Check the attribution source chain to check if there are any 3p apps.
         * @return true if 3p app found in attribution source chain.
         */
        public boolean hasNonPrivilegedApp() {
            return mNonPrivilegedAppInAttributionSource != null;
        }

        /**
         * Gets the list of controlees active under this session.
         */
        public List<UwbControlee> getControleeList() {
            return Collections.unmodifiableList(mControleeList);
        }

        /**
         * Store a ReceivedDataInfo for the UwbSession. If we already have stored data from the
         * same advertiser and with the same sequence number, this is a no-op.
         */
        public void addReceivedDataInfo(ReceivedDataInfo receivedDataInfo) {
            SortedMap<Long, ReceivedDataInfo> innerMap = mReceivedDataInfoMap.get(
                    receivedDataInfo.address);
            if (innerMap == null) {
                innerMap = new TreeMap<>();
                mReceivedDataInfoMap.put(receivedDataInfo.address, innerMap);
            }

            // Check if the sorted InnerMap has reached the max number of Rx packets we want to
            // store; if so we drop the smallest (sequence number) packet between the new received
            // packet and the stored packets.
            int maxRxPacketsToStore =
                    mUwbInjector.getDeviceConfigFacade().getRxDataMaxPacketsToStore();
            if (innerMap.size() < maxRxPacketsToStore) {
                innerMap.putIfAbsent(receivedDataInfo.sequenceNum, receivedDataInfo);
            } else if (innerMap.size() == maxRxPacketsToStore) {
                Long smallestStoredSequenceNumber = innerMap.firstKey();
                if (smallestStoredSequenceNumber < receivedDataInfo.sequenceNum
                        && !innerMap.containsKey(receivedDataInfo.sequenceNum)) {
                    innerMap.remove(smallestStoredSequenceNumber);
                    innerMap.putIfAbsent(receivedDataInfo.sequenceNum, receivedDataInfo);
                }
            }
        }

        /**
          * Return all the ReceivedDataInfo from the given remote device, in sequence number order.
          * This method also removes the returned packets from the Map, so the same packet will
          * not be returned again (in a future call).
          */
        public List<ReceivedDataInfo> getAllReceivedDataInfo(long macAddress) {
            SortedMap<Long, ReceivedDataInfo> innerMap = mReceivedDataInfoMap.get(macAddress);
            if (innerMap == null) {
                // No stored ReceivedDataInfo(s) for the address.
                return List.of();
            }

            List<ReceivedDataInfo> receivedDataInfoList = new ArrayList<>(innerMap.values());
            innerMap.clear();
            return receivedDataInfoList;
        }

        private void clearReceivedDataInfo() {
            for (long macAddress : getRemoteMacAddressList()) {
                SortedMap<Long, ReceivedDataInfo> innerMap = mReceivedDataInfoMap.get(macAddress);
                innerMap.clear();
            }
            mReceivedDataInfoMap.clear();
        }

        /**
         * Get (and increment) the UCI sequence number for the next Data packet to be sent to UWBS.
         */
        public short getAndIncrementDataSndSequenceNumber() {
            return mDataSndSequenceNumber++;
        }

        /**
         * Store a SendDataInfo for a UCI Data packet sent to UWBS.
         */
        public void addSendDataInfo(long sequenceNumber, SendDataInfo sendDataInfo) {
            mSendDataInfoMap.put(sequenceNumber, sendDataInfo);
        }

        /**
         * Remove the SendDataInfo for a UCI packet from the current UWB Session.
         */
        public void removeSendDataInfo(long sequenceNumber) {
            mSendDataInfoMap.remove(sequenceNumber);
        }

        /**
         * Get the SendDataInfo for a UCI packet from the current UWB Session.
         */
        @Nullable
        public SendDataInfo getSendDataInfo(long sequenceNumber) {
            return mSendDataInfoMap.get(sequenceNumber);
        }

        /**
         * Adds a Controlee to the session. This should only be called to reflect
         *  the state of the native UWB interface.
         * @param address The UWB address of the Controlee to add.
         */
        public void addControlee(UwbAddress address) {
            if (mControleeList != null
                    && mControleeList.stream().noneMatch(e -> e.getUwbAddress().equals(address))) {
                mControleeList.add(new UwbControlee(address, createFilterEngine(), mUwbInjector));
            }
        }

        /**
         * Fetches a {@link UwbControlee} object by {@link UwbAddress}.
         * @param address The UWB address of the Controlee to find.
         * @return The matching {@link UwbControlee}, or null if not found.
         */
        public UwbControlee getControlee(UwbAddress address) {
            UwbControlee result = null;
            if (mControleeList != null) {
               result = mControleeList
                    .stream()
                    .filter(e -> e.getUwbAddress().equals(address))
                    .findFirst()
                    .orElse(null);
               if (result == null) {
                    Log.d(TAG, "Failure to find controlee " + address);
               }
            } else {
               Log.d(TAG, "Controlee list is empty");
            }
            return result;
        }

        /**
         * Removes a Controlee from the session. This should only be called to reflect
         *  the state of the native UWB interface.
         * @param address The UWB address of the Controlee to remove.
         */
        public void removeControlee(UwbAddress address) {
            if (mControleeList != null) {
                for (UwbControlee controlee : mControleeList) {
                    if (controlee.getUwbAddress().equals(address)) {
                        controlee.close();
                        mControleeList.remove(controlee);
                        break;
                    }
                }
            }
        }

        public AttributionSource getAttributionSource() {
            return this.mAttributionSource;
        }

        public int getSessionId() {
            return this.mSessionId;
        }

        public byte getSessionType() {
            return this.mSessionType;
        }

        public int getRangingRoundUsage() {
            return this.mRangingRoundUsage;
        }

        public String getChipId() {
            return this.mChipId;
        }

        public SessionHandle getSessionHandle() {
            return this.mSessionHandle;
        }

        public Params getParams() {
            return this.mParams;
        }

        public int getDataRepetitionCount() {
            return mDataRepetitionCount;
        }

        public void updateAliroParamsOnStart(AliroStartRangingParams rangingStartParams) {
            setNeedsQueryUwbsTimestamp(rangingStartParams);

            // Need to update the RAN multiplier and initiation time
            // from the AliroStartRangingParams for CCC session.
            AliroOpenRangingParams newParams =
                    new AliroOpenRangingParams.Builder((AliroOpenRangingParams) mParams)
                            .setRanMultiplier(rangingStartParams.getRanMultiplier())
                            .setInitiationTimeMs(rangingStartParams.getInitiationTimeMs())
                            .setAbsoluteInitiationTimeUs(rangingStartParams
                                    .getAbsoluteInitiationTimeUs())
                            .setStsIndex(rangingStartParams.getStsIndex())
                            .build();
            this.mParams = newParams;
            this.mNeedsAppConfigUpdate = true;
        }

        public void updateCccParamsOnStart(CccStartRangingParams rangingStartParams) {
            setNeedsQueryUwbsTimestamp(rangingStartParams);

            // Need to update the RAN multiplier and initiation time
            // from the CccStartRangingParams for CCC session.
            CccOpenRangingParams newParams =
                    new CccOpenRangingParams.Builder((CccOpenRangingParams) mParams)
                            .setRanMultiplier(rangingStartParams.getRanMultiplier())
                            .setInitiationTimeMs(rangingStartParams.getInitiationTimeMs())
                            .setAbsoluteInitiationTimeUs(rangingStartParams
                                    .getAbsoluteInitiationTimeUs())
                            .setStsIndex(rangingStartParams.getStsIndex())
                            .build();
            this.mParams = newParams;
            this.mNeedsAppConfigUpdate = true;
        }

        /**
         * Checks if session priority of the current session changed from the initial value, if so
         * updates the session priority param and marks session for needed app config update.
         */
        public void updateFiraParamsOnStartIfChanged() {
            // Need to check if session priority changed and update if it did
            FiraOpenSessionParams firaOpenSessionParams = (FiraOpenSessionParams) mParams;
            if (mStackSessionPriority != firaOpenSessionParams.getSessionPriority()) {
                this.mParams = ((FiraOpenSessionParams) mParams).toBuilder().setSessionPriority(
                        mStackSessionPriority).build();
                this.mNeedsAppConfigUpdate = true;
            }

            setNeedsQueryUwbsTimestamp(null /* rangingStartParams */);
        }

        /**
         * Sets {@code mNeedsQueryUwbsTimestamp} to {@code true}, if the UWBS Timestamp needs to be
         * fetched from the UWBS controller (for computing an absolute UWB initiation time).
         */
        public void setNeedsQueryUwbsTimestamp(@Nullable Params startRangingParams) {
            // When the UWBS supports Fira 2.0+, the application has configured a relative UWB
            // initation time, but not configured an absolute UWB initiation time, we must fetch
            // the UWBS timestamp (to compute the absolute UWB initiation time).
            if (getUwbsFiraProtocolVersion(mChipId).getMajor() >= 2) {
                if (mParams instanceof FiraOpenSessionParams) {
                    FiraOpenSessionParams firaOpenSessionParams = (FiraOpenSessionParams) mParams;
                    if (firaOpenSessionParams.getInitiationTime() != 0
                            && firaOpenSessionParams.getAbsoluteInitiationTime() == 0) {
                        this.mNeedsQueryUwbsTimestamp = true;
                    }
                } else if (mParams instanceof CccOpenRangingParams
                        && mUwbInjector.getDeviceConfigFacade()
                        .isCccAbsoluteUwbInitiationTimeEnabled()) {
                    // When CccStartRangingParams is present; we check only for it's fields,
                    // since its values overrides the earlier CccOpenRangingParams.
                    if (startRangingParams != null
                                && startRangingParams instanceof CccStartRangingParams) {
                        CccStartRangingParams cccStartRangingParams =
                                (CccStartRangingParams) startRangingParams;
                        if (cccStartRangingParams.getInitiationTimeMs() != 0
                                && cccStartRangingParams.getAbsoluteInitiationTimeUs() == 0) {
                            this.mNeedsQueryUwbsTimestamp = true;
                        }
                    } else {
                        CccOpenRangingParams cccOpenRangingParams = (CccOpenRangingParams) mParams;
                        if (cccOpenRangingParams.getInitiationTimeMs() != 0
                                && cccOpenRangingParams.getAbsoluteInitiationTimeUs() == 0) {
                            this.mNeedsQueryUwbsTimestamp = true;
                        }
                    }
                } else if (mParams instanceof AliroOpenRangingParams
                        && mUwbInjector.getDeviceConfigFacade()
                        .isCccAbsoluteUwbInitiationTimeEnabled()) { // Re-use CCC flag for ALIRO
                    // When AliroStartRangingParams is present; we check only for it's fields,
                    // since its values overrides the earlier AliroOpenRangingParams.
                    if (startRangingParams != null
                                && startRangingParams instanceof AliroStartRangingParams) {
                        AliroStartRangingParams aliroStartRangingParams =
                                (AliroStartRangingParams) startRangingParams;
                        if (aliroStartRangingParams.getInitiationTimeMs() != 0
                                && aliroStartRangingParams.getAbsoluteInitiationTimeUs() == 0) {
                            this.mNeedsQueryUwbsTimestamp = true;
                        }
                    } else {
                        AliroOpenRangingParams aliroOpenRangingParams =
                                (AliroOpenRangingParams) mParams;
                        if (aliroOpenRangingParams.getInitiationTimeMs() != 0
                                && aliroOpenRangingParams.getAbsoluteInitiationTimeUs() == 0) {
                            this.mNeedsQueryUwbsTimestamp = true;
                        }
                    }
                }
            }
        }

        /**
         * Computes an absolute UWB initiation time, if it's needed.
         */
        public void setAbsoluteInitiationTimeIfNeeded() {
            if (this.mNeedsQueryUwbsTimestamp) {
                // Query the UWBS timestamp and add the relative initiation time
                // stored in the FiraOpenSessionParams, to get the absolute
                // initiation time to be configured.
                long uwbsTimestamp =
                        mUwbInjector.getUwbServiceCore().queryUwbsTimestampMicros();
                computeAbsoluteInitiationTime(uwbsTimestamp);
            }
        }

        /**
         * For Fira 2.0+ controller devices, replace the reference Session's SessionID with
         * its SessionToken, in the SessionTimeBase AppConfig parameter.
         */
        public void updateFiraParamsForSessionTimeBase(int sessionToken) {
            if (mParams instanceof FiraOpenSessionParams) {
                FiraOpenSessionParams firaOpenSessionParams = (FiraOpenSessionParams) mParams;
                int deviceRole = firaOpenSessionParams.getDeviceRole();
                if (deviceRole == FiraParams.RANGING_DEVICE_TYPE_CONTROLLER
                           && UwbUtil.isBitSet(firaOpenSessionParams.getReferenceTimeBase(),
                           FiraParams.SESSION_TIME_BASE_REFERENCE_FEATURE_ENABLED)) {
                    this.mParams = ((FiraOpenSessionParams) mParams).toBuilder().setSessionTimeBase(
                        firaOpenSessionParams.getReferenceTimeBase(), sessionToken,
                        firaOpenSessionParams.getSessionOffsetInMicroSeconds())
                        .build();
                }
            }
        }

       /**
         * Compute absolute initiation time, by doing a sum of the UWBS Timestamp (in micro-seconds)
         * and the relative initiation time (in milli-seconds). This method should be
         * called only for FiRa UCI ProtocolVersion >= 2.0 devices.
         */
        public void computeAbsoluteInitiationTime(long uwbsTimestamp) {
            if (this.mNeedsQueryUwbsTimestamp) {
                if (mParams instanceof FiraOpenSessionParams) {
                    FiraOpenSessionParams firaOpenSessionParams = (FiraOpenSessionParams) mParams;
                    this.mParams = ((FiraOpenSessionParams) mParams).toBuilder()
                            .setAbsoluteInitiationTime(uwbsTimestamp
                                    + (firaOpenSessionParams.getInitiationTime() * 1000))
                            .build();
                } else if (mParams instanceof CccOpenRangingParams) {
                    CccOpenRangingParams cccOpenRangingParams = (CccOpenRangingParams) mParams;
                    this.mParams = ((CccOpenRangingParams) mParams).toBuilder()
                            .setAbsoluteInitiationTimeUs(uwbsTimestamp
                                    + (cccOpenRangingParams.getInitiationTimeMs() * 1000))
                            .build();
                } else if (mParams instanceof AliroOpenRangingParams) {
                    AliroOpenRangingParams aliroOpenRangingParams =
                            (AliroOpenRangingParams) mParams;
                    this.mParams = ((AliroOpenRangingParams) mParams).toBuilder()
                            .setAbsoluteInitiationTimeUs(uwbsTimestamp
                                    + (aliroOpenRangingParams.getInitiationTimeMs() * 1000))
                            .build();
                }
                this.mNeedsAppConfigUpdate = true;
            }
        }

        /**
         * Reset the computed absolute initiation time, only when it was computed and set by this
         * class (it should not be reset when it was provided by the application).
         */
        public void resetAbsoluteInitiationTime() {
            if (this.mNeedsQueryUwbsTimestamp) {
                if (mParams instanceof FiraOpenSessionParams) {
                    // Reset the absolute Initiation time, so that it's re-computed if start
                    // ranging is called in the future for this UWB session.
                    this.mParams = ((FiraOpenSessionParams) mParams).toBuilder()
                            .setAbsoluteInitiationTime(0)
                            .build();
                } else if (mParams instanceof CccOpenRangingParams) {
                    this.mParams = ((CccOpenRangingParams) mParams).toBuilder()
                            .setAbsoluteInitiationTimeUs(0)
                            .build();
                } else if (mParams instanceof AliroOpenRangingParams) {
                    this.mParams = ((AliroOpenRangingParams) mParams).toBuilder()
                            .setAbsoluteInitiationTimeUs(0)
                            .build();
                }
                this.mNeedsQueryUwbsTimestamp = false;
            }
        }

        public void updateFiraParamsOnReconfigure(FiraRangingReconfigureParams reconfigureParams) {
            // Need to update the reconfigure params from the FiraRangingReconfigureParams for
            // FiRa session.
            FiraOpenSessionParams.Builder newParamsBuilder =
                    new FiraOpenSessionParams.Builder((FiraOpenSessionParams) mParams);
            if (reconfigureParams.getBlockStrideLength() != null) {
                newParamsBuilder.setBlockStrideLength(reconfigureParams.getBlockStrideLength());
            }
            if (reconfigureParams.getRangeDataNtfConfig() != null) {
                newParamsBuilder.setRangeDataNtfConfig(reconfigureParams.getRangeDataNtfConfig());
            }
            if (reconfigureParams.getRangeDataProximityNear() != null) {
                newParamsBuilder.setRangeDataNtfProximityNear(
                        reconfigureParams.getRangeDataProximityNear());
            }
            if (reconfigureParams.getRangeDataProximityFar() != null) {
                newParamsBuilder.setRangeDataNtfProximityFar(
                        reconfigureParams.getRangeDataProximityFar());
            }
            if (reconfigureParams.getRangeDataAoaAzimuthLower() != null) {
                newParamsBuilder.setRangeDataNtfAoaAzimuthLower(
                        reconfigureParams.getRangeDataAoaAzimuthLower());
            }
            if (reconfigureParams.getRangeDataAoaAzimuthUpper() != null) {
                newParamsBuilder.setRangeDataNtfAoaAzimuthUpper(
                        reconfigureParams.getRangeDataAoaAzimuthUpper());
            }
            if (reconfigureParams.getRangeDataAoaElevationLower() != null) {
                newParamsBuilder.setRangeDataNtfAoaElevationLower(
                        reconfigureParams.getRangeDataAoaElevationLower());
            }
            if (reconfigureParams.getRangeDataAoaElevationUpper() != null) {
                newParamsBuilder.setRangeDataNtfAoaElevationUpper(
                        reconfigureParams.getRangeDataAoaElevationUpper());
            }
            this.mParams = newParamsBuilder.build();
        }

        // Return the Ranging Interval (Fira 2.0: Ranging Duration) in milliseconds.
        public int getCurrentFiraRangingIntervalMs() {
            FiraOpenSessionParams firaOpenSessionParams = (FiraOpenSessionParams) mParams;
            return firaOpenSessionParams.getRangingIntervalMs()
                    * (firaOpenSessionParams.getBlockStrideLength() + 1);
        }

        public String getProtocolName() {
            return this.mProtocolName;
        }

        public IUwbRangingCallbacks getIUwbRangingCallbacks() {
            return this.mIUwbRangingCallbacks;
        }

        public int getSessionState() {
            return this.mSessionState;
        }

        public void setSessionState(int state) {
            this.mSessionState = state;
        }

        public int getStackSessionPriority() {
            return this.mStackSessionPriority;
        }

        public void setStackSessionPriority(int priority) {
            this.mStackSessionPriority = priority;
        }

        public boolean getNeedsAppConfigUpdate() {
            return this.mNeedsAppConfigUpdate;
        }

        /** Reset the needsAppConfigUpdate flag to false. */
        public void resetNeedsAppConfigUpdate() {
            this.mNeedsAppConfigUpdate = false;
        }

        public boolean getNeedsQueryUwbsTimestamp() {
            return this.mNeedsQueryUwbsTimestamp;
        }

        public Set<Long> getRemoteMacAddressList() {
            return mReceivedDataInfoMap.keySet();
        }

        public boolean isDataDeliveryPermissionCheckNeeded() {
            return mDataDeliveryPermissionCheckNeeded;
        }

        public void setDataDeliveryPermissionCheckNeeded(boolean permissionCheckNeeded) {
            mDataDeliveryPermissionCheckNeeded = permissionCheckNeeded;
        }
        public void setMulticastListUpdateStatus(
                UwbMulticastListUpdateStatus multicastListUpdateStatus) {
            mMulticastListUpdateStatus = multicastListUpdateStatus;
        }

        public UwbMulticastListUpdateStatus getMulticastListUpdateStatus() {
            return mMulticastListUpdateStatus;
        }

        private int convertProtolNameToProfileType(String protocolName) {
            if (protocolName.equals(FiraParams.PROTOCOL_NAME)) {
                return UwbStatsLog.UWB_SESSION_INITIATED__PROFILE__FIRA;
            } else if (protocolName.equals(CccParams.PROTOCOL_NAME)) {
                return UwbStatsLog.UWB_SESSION_INITIATED__PROFILE__CCC;
            } else if (protocolName.equals(AliroParams.PROTOCOL_NAME)) {
                return UwbStatsLog.UWB_SESSION_INITIATED__PROFILE__ALIRO;
            } else {
                return UwbStatsLog.UWB_SESSION_INITIATED__PROFILE__CUSTOMIZED;
            }
        }

        public int getProfileType() {
            return mProfileType;
        }

        public int getParallelSessionCount() {
            if (mSessionTable.containsKey(mSessionHandle)) {
                return getSessionCount() - 1;
            }
            return getSessionCount();
        }

        public IBinder getBinder() {
            return mIBinder;
        }

        public WaitObj getWaitObj() {
            return mWaitObj;
        }

        public boolean hasNonPrivilegedFgAppOrService() {
            return mHasNonPrivilegedFgAppOrService;
        }

        public void setHasNonPrivilegedFgAppOrService(boolean hasNonPrivilegedFgAppOrService) {
            mHasNonPrivilegedFgAppOrService = hasNonPrivilegedFgAppOrService;
        }

        /**
         * Starts a timer to detect if the error streak is longer than
         * {@link UwbSession#mRangingErrorStreakTimeoutMs }.
         */
        public void startRangingResultErrorStreakTimerIfNotSet() {
            // Start a timer on first failure to detect continuous failures.
            if (mRangingResultErrorStreakTimerListener == null) {
                mRangingResultErrorStreakTimerListener = () -> {
                    Log.w(TAG, "Continuous errors or no ranging results detected for "
                            + mRangingErrorStreakTimeoutMs + " ms."
                            + " Stopping session");
                    stopRangingInternal(mSessionHandle, true /* triggeredBySystemPolicy */);
                };
                Log.v(TAG, "Starting error timer for "
                        + mRangingErrorStreakTimeoutMs + " ms.");
                mAlarmManager.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        mUwbInjector.getElapsedSinceBootMillis()
                                + mRangingErrorStreakTimeoutMs,
                        RANGING_RESULT_ERROR_STREAK_TIMER_TAG,
                        mRangingResultErrorStreakTimerListener, mEventTask);
            }
        }

        public void stopRangingResultErrorStreakTimerIfSet() {
            // Cancel error streak timer on any success.
            if (mRangingResultErrorStreakTimerListener != null) {
                mAlarmManager.cancel(mRangingResultErrorStreakTimerListener);
                mRangingResultErrorStreakTimerListener = null;
            }
        }

        /**
         * Starts a timer to detect if the app that started the UWB session is in the background
         * for longer than {@link UwbSession#NON_PRIVILEGED_BG_APP_TIMEOUT_MS}.
         */
        private void startNonPrivilegedBgAppTimerIfNotSet() {
            // Start a timer when the non-privileged app goes into the background.
            if (mNonPrivilegedBgAppTimerListener == null) {
                mNonPrivilegedBgAppTimerListener = () -> {
                    Log.w(TAG, "Non-privileged app in background for longer than timeout - "
                            + " Stopping session");
                    stopRangingInternal(mSessionHandle, true /* triggeredBySystemPolicy */);
                };
                mAlarmManager.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        mUwbInjector.getElapsedSinceBootMillis()
                                + NON_PRIVILEGED_BG_APP_TIMEOUT_MS,
                        NON_PRIVILEGED_BG_APP_TIMER_TAG,
                        mNonPrivilegedBgAppTimerListener, mEventTask);
            }
        }

        private void stopNonPrivilegedBgAppTimerIfSet() {
            // Stop the timer when the non-privileged app goes into the foreground.
            if (mNonPrivilegedBgAppTimerListener != null) {
                mAlarmManager.cancel(mNonPrivilegedBgAppTimerListener);
                mNonPrivilegedBgAppTimerListener = null;
            }
        }

        private void stopTimers() {
            // Reset any stored error streak or non-privileged background app timestamps.
            stopRangingResultErrorStreakTimerIfSet();
            stopNonPrivilegedBgAppTimerIfSet();
        }

        public void reconfigureFiraSessionOnFgStateChange() {
            // Reconfigure the session to change notification control when the app transitions
            // from fg to bg and vice versa.
            FiraRangingReconfigureParams.Builder builder =
                    new FiraRangingReconfigureParams.Builder();
            // If app is in fg, use the configured ntf control, else disable.
            if (mHasNonPrivilegedFgAppOrService) {
                FiraOpenSessionParams params = (FiraOpenSessionParams) mParams;
                builder.setRangeDataNtfConfig(params.getRangeDataNtfConfig())
                        .setRangeDataProximityNear(params.getRangeDataNtfProximityNear())
                        .setRangeDataProximityFar(params.getRangeDataNtfProximityFar());
            } else {
                builder.setRangeDataNtfConfig(FiraParams.RANGE_DATA_NTF_CONFIG_DISABLE);
            }
            FiraRangingReconfigureParams reconfigureParams = builder.build();
            reconfigureInternal(
                    mSessionHandle, reconfigureParams, true /* triggeredByFgStateChange */);

            if (!mUwbInjector.getDeviceConfigFacade().isBackgroundRangingEnabled()) {
                Log.d(TAG, "reconfigureFiraSessionOnFgStateChange - System policy disallows for "
                        + "non fg 3p apps");
                // When a non-privileged app goes into the background, start a timer (that will stop
                // the ranging session). If the app goes back into the foreground, the timer will
                // get reset (but any stopped UWB session will not be auto-resumed).
                if (!mHasNonPrivilegedFgAppOrService) {
                    startNonPrivilegedBgAppTimerIfNotSet();
                } else {
                    stopNonPrivilegedBgAppTimerIfSet();
                }
            } else {
                Log.d(TAG, "reconfigureFiraSessionOnFgStateChange - System policy allows for "
                        + "non fg 3p apps");
            }
        }

        public int getOperationType() {
            return mOperationType;
        }

        public void setOperationType(int type) {
            mOperationType = type;
        }

        public int getLastSessionStatusNtfReasonCode() {
            return mLastSessionStatusNtfReasonCode;
        }

        public void setLastSessionStatusNtfReasonCode(int lastSessionStatusNtfReasonCode) {
            mLastSessionStatusNtfReasonCode = lastSessionStatusNtfReasonCode;
        }

        /** Creates a filter engine based on the device configuration. */
        public UwbFilterEngine createFilterEngine() {
            if (mParams instanceof FiraOpenSessionParams) {
                FiraOpenSessionParams firaParams = (FiraOpenSessionParams) mParams;
                if (firaParams.getFilterType() == FILTER_TYPE_NONE) {
                    return null; /* Bail early. App requested no engine. */
                }
            }

            return mUwbInjector.createFilterEngine(mPoseSource);
        }

        /** Updates the pose information if an ApplicationPoseSource is being used. */
        public void updatePose(FiraPoseUpdateParams updateParams) {
            if (mPoseSource instanceof ApplicationPoseSource) {
                ApplicationPoseSource aps = (ApplicationPoseSource) mPoseSource;
                aps.applyPose(updateParams.getPoseInfo());
            } else {
                throw new IllegalStateException("Session not configured for application poses.");
            }
        }

        @Override
        public void binderDied() {
            Log.i(TAG, "binderDied : getSessionId is getSessionId() " + getSessionId());

            synchronized (UwbSessionManager.this) {
                int status = mNativeUwbManager.deInitSession(getSessionId(), getChipId());
                mUwbMetrics.logRangingCloseEvent(this, status);
                if (status == UwbUciConstants.STATUS_CODE_OK) {
                    removeSession(this);
                    Log.i(TAG,
                            "binderDied : Fira/CCC/ALIRO Session counts currently are "
                                    + getFiraSessionCount()
                                    + "/" + getCccSessionCount()
                                    + "/" + getAliroSessionCount());
                } else {
                    Log.e(TAG,
                            "binderDied : sessionDeinit Failure because of NativeSessionDeinit "
                                    + "Error");
                }
            }
        }

        /**
         * Cleans up resources held by this object.
         */
        public void close() {
            if (this.mAcquiredDefaultPose) {
                if (mControleeList != null) {
                    for (UwbControlee controlee : mControleeList) {
                        controlee.close();
                    }
                    mControleeList.clear();
                }

                this.mAcquiredDefaultPose = false;
                mUwbInjector.releasePoseSource();
            }

            mSendDataInfoMap.clear();
            clearReceivedDataInfo();
        }

        /**
         * Gets the pose source for this session. This may be the default pose source provided
         * by UwbInjector.java when the session was created, or a specialized pose source later
         * requested by the application.
         */
        public IPoseSource getPoseSource() {
            return mPoseSource;
        }

        @Override
        public String toString() {
            return "UwbSession: { Session Id: " + getSessionId()
                    + ", Handle: " + getSessionHandle()
                    + ", Protocol: " + getProtocolName()
                    + ", State: " + getSessionState()
                    + ", Data Send Sequence Number: " + mDataSndSequenceNumber
                    + ", Params: " + getParams()
                    + ", AttributionSource: " + getAttributionSource()
                    + " }";
        }
    }

    // TODO: refactor the async operation flow.
    // Wrapper for unit test.
    @VisibleForTesting
    static class WaitObj {
        WaitObj() {
        }

        void blockingWait() throws InterruptedException {
            wait();
        }

        void blockingNotify() {
            notify();
        }
    }

    /**
     * Dump the UWB session manager debug info
     */
    public synchronized void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("---- Dump of UwbSessionManager ----");
        pw.println("Active sessions: ");
        for (UwbSession uwbSession : mSessionTable.values()) {
            pw.println(uwbSession);
        }
        pw.println("Recently closed sessions: ");
        for (UwbSession uwbSession: mDbgRecentlyClosedSessions.getEntries()) {
            pw.println(uwbSession);
        }
        List<Integer> nonPrivilegedSessionIds =
                mNonPrivilegedUidToFiraSessionsTable.entrySet()
                        .stream()
                        .map(e -> e.getValue()
                                .stream()
                                .map(UwbSession::getSessionId)
                                .collect(Collectors.toList()))
                        .flatMap(Collection::stream)
                        .collect(Collectors.toList());
        pw.println("Non Privileged Fira Session Ids: " + nonPrivilegedSessionIds);
        pw.println("---- Dump of UwbSessionManager ----");
    }

    private static byte[] getComputedMacAddress(UwbAddress address) {
        if (!SdkLevel.isAtLeastU()) {
            return TlvUtil.getReverseBytes(address.toBytes());
        }
        return address.toBytes();
    }
}
