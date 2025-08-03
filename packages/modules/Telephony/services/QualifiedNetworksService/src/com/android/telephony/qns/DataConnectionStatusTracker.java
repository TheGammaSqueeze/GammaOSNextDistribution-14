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

import android.annotation.IntDef;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.telephony.AccessNetworkConstants;
import android.telephony.PreciseDataConnectionState;
import android.telephony.TelephonyManager;
import android.telephony.data.ApnSetting;
import android.util.Log;
import android.util.SparseArray;

import com.android.internal.annotations.VisibleForTesting;

class DataConnectionStatusTracker {
    private static final int EVENT_DATA_CONNECTION_STATE_CHANGED = 11001;
    protected final int mSlotIndex;
    private final String mLogTag;
    private final int mNetCapability;
    @VisibleForTesting protected final Handler mHandler;
    private DataConnectionChangedInfo mLastUpdatedDcChangedInfo;
    private final QnsTelephonyListener mQnsTelephonyListener;
    static final int STATE_INACTIVE = 0;
    static final int STATE_CONNECTING = 1;
    static final int STATE_CONNECTED = 2;
    static final int STATE_HANDOVER = 3;

    @IntDef(
            value = {
                STATE_INACTIVE,
                STATE_CONNECTING,
                STATE_CONNECTED,
                STATE_HANDOVER,
            })
    @interface DataConnectionState {}

    static final int EVENT_DATA_CONNECTION_DISCONNECTED = 0;
    static final int EVENT_DATA_CONNECTION_STARTED = 1;
    static final int EVENT_DATA_CONNECTION_CONNECTED = 2;
    static final int EVENT_DATA_CONNECTION_FAILED = 3;
    static final int EVENT_DATA_CONNECTION_HANDOVER_STARTED = 4;
    static final int EVENT_DATA_CONNECTION_HANDOVER_SUCCESS = 5;
    static final int EVENT_DATA_CONNECTION_HANDOVER_FAILED = 6;

    @IntDef(
            value = {
                EVENT_DATA_CONNECTION_DISCONNECTED,
                EVENT_DATA_CONNECTION_STARTED,
                EVENT_DATA_CONNECTION_CONNECTED,
                EVENT_DATA_CONNECTION_FAILED,
                EVENT_DATA_CONNECTION_HANDOVER_STARTED,
                EVENT_DATA_CONNECTION_HANDOVER_SUCCESS,
                EVENT_DATA_CONNECTION_HANDOVER_FAILED,
            })
    @interface DataConnectionChangedEvent {}

    private final QnsRegistrantList mDataConnectionStatusRegistrants;
    private int mState = STATE_INACTIVE;
    private int mDataConnectionFailCause;
    private int mTransportType = AccessNetworkConstants.TRANSPORT_TYPE_INVALID;
    private SparseArray<ApnSetting> mLastApnSettings = new SparseArray<>();

    /**
     * Constructor to instantiate CellularQualityMonitor
     *
     * @param qnsTelephonyListener QnsTelephonyListener instance
     * @param looper looper to bind class' handler.
     * @param slotIndex slot index
     * @param netCapability integer value of network capability
     */
    DataConnectionStatusTracker(
            QnsTelephonyListener qnsTelephonyListener,
            Looper looper,
            int slotIndex,
            int netCapability) {
        mLogTag =
                QnsConstants.QNS_TAG
                        + "_"
                        + DataConnectionStatusTracker.class.getSimpleName()
                        + "_"
                        + slotIndex
                        + "_"
                        + QnsUtils.getNameOfNetCapability(netCapability);

        mSlotIndex = slotIndex;
        mNetCapability = netCapability;

        mHandler = new DataConnectionStatusTrackerHandler(looper);
        mDataConnectionStatusRegistrants = new QnsRegistrantList();
        mQnsTelephonyListener = qnsTelephonyListener;
        mQnsTelephonyListener.registerPreciseDataConnectionStateChanged(
                mNetCapability, mHandler, EVENT_DATA_CONNECTION_STATE_CHANGED, null, true);
    }

    protected void log(String s) {
        Log.d(mLogTag, s);
    }

    boolean isInactiveState() {
        return mState == STATE_INACTIVE;
    }

    boolean isActiveState() {
        return mState == STATE_CONNECTED || mState == STATE_HANDOVER;
    }

    boolean isHandoverState() {
        return mState == STATE_HANDOVER;
    }

    boolean isConnectionInProgress() {
        return mState == STATE_CONNECTING || mState == STATE_HANDOVER;
    }

    int getLastTransportType() {
        return mTransportType;
    }

    int getLastFailCause() {
        return mDataConnectionFailCause;
    }

    /** Returns Latest APN setting for the transport type */
    ApnSetting getLastApnSetting(int transportType) {
        try {
            return mLastApnSettings.get(transportType);
        } catch (Exception e) {
            return null;
        }
    }

    void registerDataConnectionStatusChanged(Handler h, int what) {
        if (h != null) {
            mDataConnectionStatusRegistrants.addUnique(h, what, null);
        }
        if (mLastUpdatedDcChangedInfo != null) {
            QnsRegistrant r = new QnsRegistrant(h, what, null);
            r.notifyResult(mLastUpdatedDcChangedInfo);
        }
    }

    void unRegisterDataConnectionStatusChanged(Handler h) {
        if (h != null) {
            mDataConnectionStatusRegistrants.remove(h);
        }
    }

    private void onDataConnectionStateChanged(PreciseDataConnectionState status) {
        int transportType = status.getTransportType();
        int state = status.getState();
        mDataConnectionFailCause = status.getLastCauseCode();
        log(
                "onDataConnectionChanged transportType:"
                        + QnsConstants.transportTypeToString(transportType)
                        + " state:"
                        + QnsConstants.dataStateToString(status.getState())
                        + " cause:"
                        + status.getLastCauseCode());

        switch (state) {
            case TelephonyManager.DATA_DISCONNECTED:
                if (mState == STATE_CONNECTED || mState == STATE_HANDOVER) {
                    mState = STATE_INACTIVE;
                    mTransportType = AccessNetworkConstants.TRANSPORT_TYPE_INVALID;
                    log("Connection Disconnected");
                    notifyDataConnectionStatusChangedEvent(EVENT_DATA_CONNECTION_DISCONNECTED);
                } else {
                    if (mState == STATE_CONNECTING) {
                        // Initial connect Failed.
                        mState = STATE_INACTIVE;
                        mTransportType = AccessNetworkConstants.TRANSPORT_TYPE_INVALID;
                        log("Initial connect failed");
                        notifyDataConnectionFailed(transportType);
                    }
                }
                break;

            case TelephonyManager.DATA_CONNECTING:
                if (mState == STATE_INACTIVE) {
                    mState = STATE_CONNECTING;
                    log(
                            "Initial Connect inited transport: "
                                    + QnsConstants.transportTypeToString(transportType));
                    notifyDataConnectionStarted(transportType);
                }
                break;

            case TelephonyManager.DATA_CONNECTED:
                if (mState == STATE_CONNECTING || mState == STATE_INACTIVE) {
                    mState = STATE_CONNECTED;
                    mTransportType = transportType;
                    log(
                            "Data Connected Transport: "
                                    + QnsConstants.transportTypeToString(mTransportType));
                    notifyDataConnectionStatusChangedEvent(EVENT_DATA_CONNECTION_CONNECTED);
                } else if (mState == STATE_HANDOVER && mTransportType != transportType) {
                    mState = STATE_CONNECTED;
                    mTransportType = transportType;
                    log(
                            "Handover completed to: "
                                    + QnsConstants.transportTypeToString(mTransportType));
                    notifyDataConnectionStatusChangedEvent(EVENT_DATA_CONNECTION_HANDOVER_SUCCESS);
                } else if (mState == STATE_HANDOVER && mTransportType == transportType) {
                    mState = STATE_CONNECTED;
                    log(
                            "Handover failed and return to: "
                                    + QnsConstants.transportTypeToString(mTransportType));
                    notifyDataConnectionStatusChangedEvent(EVENT_DATA_CONNECTION_HANDOVER_FAILED);
                }
                break;

            case TelephonyManager.DATA_SUSPENDED:
                if (mState == STATE_HANDOVER && mTransportType != transportType) {
                    mState = STATE_CONNECTED;
                    mTransportType = transportType;
                    log(
                            "QNS assumes Handover completed to: "
                                    + QnsConstants.transportTypeToString(mTransportType));
                    notifyDataConnectionStatusChangedEvent(EVENT_DATA_CONNECTION_HANDOVER_SUCCESS);
                }
                break;

            case TelephonyManager.DATA_HANDOVER_IN_PROGRESS:
                if (mState == STATE_CONNECTED && mTransportType == transportType) {
                    mState = STATE_HANDOVER;
                    log(
                            "Handover initiated from "
                                    + QnsConstants.transportTypeToString(transportType));
                    notifyDataConnectionStatusChangedEvent(EVENT_DATA_CONNECTION_HANDOVER_STARTED);
                } else {
                    log(
                            "Ignore STATE_HANDOVER since request is not for Src TransportType: "
                                    + QnsConstants.transportTypeToString(mTransportType));
                }
                break;

            default:
                break;
        }
        mLastApnSettings.put(mTransportType, status.getApnSetting());
    }

    private void notifyDataConnectionStarted(int transportType) {
        DataConnectionChangedInfo info =
                new DataConnectionChangedInfo(EVENT_DATA_CONNECTION_STARTED, mState, transportType);
        mLastUpdatedDcChangedInfo = info;
        mDataConnectionStatusRegistrants.notifyResult(info);
    }

    private void notifyDataConnectionFailed(int transportType) {
        DataConnectionChangedInfo info =
                new DataConnectionChangedInfo(EVENT_DATA_CONNECTION_FAILED, mState, transportType);
        mLastUpdatedDcChangedInfo = info;
        mDataConnectionStatusRegistrants.notifyResult(info);
    }

    private void notifyDataConnectionStatusChangedEvent(int event) {
        DataConnectionChangedInfo info =
                new DataConnectionChangedInfo(event, mState, mTransportType);
        mLastUpdatedDcChangedInfo = info;
        mDataConnectionStatusRegistrants.notifyResult(info);
    }

    void close() {
        mQnsTelephonyListener.unregisterPreciseDataConnectionStateChanged(mNetCapability, mHandler);
        mDataConnectionStatusRegistrants.removeAll();
    }

    static String stateToString(int state) {
        switch (state) {
            case STATE_INACTIVE:
                return "STATE_INCATIVE";
            case STATE_CONNECTING:
                return "STATE_CONNCTING";
            case STATE_CONNECTED:
                return "STATE_CONNECTED";
            case STATE_HANDOVER:
                return "STATE_HANDOVER";
        }
        return "INVALID";
    }

    static String eventToString(int event) {
        switch (event) {
            case EVENT_DATA_CONNECTION_DISCONNECTED:
                return "EVENT_DATA_CONNECTION_DISCONNECTED";
            case EVENT_DATA_CONNECTION_STARTED:
                return "EVENT_DATA_CONNECTION_STARTED";
            case EVENT_DATA_CONNECTION_CONNECTED:
                return "EVENT_DATA_CONNECTION_CONNECTED";
            case EVENT_DATA_CONNECTION_FAILED:
                return "EVENT_DATA_CONNECTION_FAILED";
            case EVENT_DATA_CONNECTION_HANDOVER_STARTED:
                return "EVENT_DATA_CONNECTION_HANDOVER_STARTED";
            case EVENT_DATA_CONNECTION_HANDOVER_SUCCESS:
                return "EVENT_DATA_CONNECTION_HANDOVER_SUCCESS";
            case EVENT_DATA_CONNECTION_HANDOVER_FAILED:
                return "EVENT_DATA_CONNECTION_HANDOVER_FAILED";
        }
        return "INVALID";
    }

    static class DataConnectionChangedInfo {
        private final int mEvent;
        private final int mState;
        private final int mCurrentTransportType;

        @Override
        public String toString() {
            return "DataConnectionChangedInfo{"
                    + "mEvent="
                    + eventToString(mEvent)
                    + ", mState="
                    + stateToString(mState)
                    + ", mCurrentTransportType="
                    + QnsConstants.transportTypeToString(mCurrentTransportType)
                    + '}';
        }

        DataConnectionChangedInfo(
                @DataConnectionChangedEvent int event,
                @DataConnectionState int state,
                @AccessNetworkConstants.TransportType int transportType) {
            mEvent = event;
            mState = state;
            mCurrentTransportType = transportType;
        }

        int getState() {
            return mState;
        }

        int getEvent() {
            return mEvent;
        }

        int getTransportType() {
            return mCurrentTransportType;
        }
    }

    class DataConnectionStatusTrackerHandler extends Handler {
        DataConnectionStatusTrackerHandler(Looper l) {
            super(l);
        }

        @Override
        public void handleMessage(Message message) {
            log("handleMessage msg=" + message.what);
            QnsAsyncResult ar = (QnsAsyncResult) message.obj;
            switch (message.what) {
                case EVENT_DATA_CONNECTION_STATE_CHANGED:
                    onDataConnectionStateChanged((PreciseDataConnectionState) ar.mResult);
                    break;
                default:
                    log("never reach here msg=" + message.what);
            }
        }
    }
}
