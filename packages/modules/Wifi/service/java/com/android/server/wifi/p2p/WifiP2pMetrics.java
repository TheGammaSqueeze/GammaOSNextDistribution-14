/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.server.wifi.p2p;

import static android.os.Process.SYSTEM_UID;

import android.content.Context;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pGroup;
import android.net.wifi.p2p.WifiP2pGroupList;
import android.util.Log;

import com.android.server.wifi.Clock;
import com.android.server.wifi.proto.WifiStatsLog;
import com.android.server.wifi.proto.nano.WifiMetricsProto.GroupEvent;
import com.android.server.wifi.proto.nano.WifiMetricsProto.P2pConnectionEvent;
import com.android.server.wifi.proto.nano.WifiMetricsProto.WifiP2pStats;
import com.android.server.wifi.util.StringUtil;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.List;

/**
 * Provides storage for wireless connectivity P2p metrics, as they are generated.
 * Metrics logged by this class include:
 *   Aggregated connection stats (num of connections, num of failures, ...)
 *   Discrete connection event stats (time, duration, failure codes, ...)
 */
public class WifiP2pMetrics {
    private static final String TAG = "WifiP2pMetrics";
    private static final boolean DBG = false;

    private static final int MAX_CONNECTION_EVENTS = 256;
    private static final int MAX_GROUP_EVENTS = 256;
    private static final int MIN_2G_FREQUENCY_MHZ = 2412;

    private static final int MAX_CONNECTION_ATTEMPT_TIME_INTERVAL_MS = 30 * 1000;

    private Clock mClock;
    private final Context mContext;
    private final Object mLock = new Object();
    private boolean mIsCountryCodeWorldMode = true;

    /**
     * Metrics are stored within an instance of the WifiP2pStats proto during runtime,
     * The P2pConnectionEvent and GroupEvent metrics are stored during runtime in member
     * lists of this WifiP2pMetrics class, with the final WifiLog proto being pieced
     * together at dump-time
     */
    private final WifiP2pStats mWifiP2pStatsProto =
            new WifiP2pStats();

    /**
     * Connection information that gets logged for every P2P connection attempt.
     */
    private final List<P2pConnectionEvent> mConnectionEventList =
            new ArrayList<>();

    /**
     * The latest started (but un-ended) connection attempt
     */
    private P2pConnectionEvent mCurrentConnectionEvent;

    /**
     * The latest started (but un-ended) connection attempt start time
     */
    private long mCurrentConnectionEventStartTime;

    private long mLastConnectionEventStartTime;

    private int mLastConnectionEventUid;

    private int mLastConnectionTryCount;

    /**
     * Group Session information that gets logged for every formed group.
     */
    private final List<GroupEvent> mGroupEventList =
            new ArrayList<>();

    /**
     * The latest started (but un-ended) group
     */
    private GroupEvent mCurrentGroupEvent;

    /**
     * The latest started (but un-ended) group start time
     */
    private long mCurrentGroupEventStartTime;

    /**
     * The latest started (but un-ended) group idle start time.
     * The group is idle if there is no connected client.
     */
    private long mCurrentGroupEventIdleStartTime;

    /**
     * The current number of persistent groups.
     * This should be persisted after a dump.
     */
    private int mNumPersistentGroup;

    public WifiP2pMetrics(Clock clock, Context context) {
        mClock = clock;
        mContext = context;
        mNumPersistentGroup = 0;
    }

    /**
     * Clear all WifiP2pMetrics, except for currentConnectionEvent.
     */
    public void clear() {
        synchronized (mLock) {
            mConnectionEventList.clear();
            if (mCurrentConnectionEvent != null) {
                mConnectionEventList.add(mCurrentConnectionEvent);
            }
            mGroupEventList.clear();
            if (mCurrentGroupEvent != null) {
                mGroupEventList.add(mCurrentGroupEvent);
            }
            mWifiP2pStatsProto.clear();
        }
    }

    /**
     * Put all metrics that were being tracked separately into mWifiP2pStatsProto
     */
    public WifiP2pStats consolidateProto() {
        synchronized (mLock) {
            mWifiP2pStatsProto.numPersistentGroup = mNumPersistentGroup;
            int connectionEventCount = mConnectionEventList.size();
            if (mCurrentConnectionEvent != null) {
                connectionEventCount--;
            }
            mWifiP2pStatsProto.connectionEvent =
                    new P2pConnectionEvent[connectionEventCount];
            for (int i = 0; i < connectionEventCount; i++) {
                mWifiP2pStatsProto.connectionEvent[i] = mConnectionEventList.get(i);
            }

            int groupEventCount = mGroupEventList.size();
            if (mCurrentGroupEvent != null) {
                groupEventCount--;
            }
            mWifiP2pStatsProto.groupEvent =
                    new GroupEvent[groupEventCount];
            for (int i = 0; i < groupEventCount; i++) {
                mWifiP2pStatsProto.groupEvent[i] = mGroupEventList.get(i);
            }
            return mWifiP2pStatsProto;
        }
    }

    /**
     * Dump all WifiP2pMetrics. Collects some metrics at this time.
     *
     * @param pw PrintWriter for writing dump to
     */
    public void dump(PrintWriter pw) {
        synchronized (mLock) {
            pw.println("WifiP2pMetrics:");
            pw.println("mConnectionEvents:");
            for (P2pConnectionEvent event : mConnectionEventList) {
                StringBuilder sb = new StringBuilder();
                Calendar c = Calendar.getInstance();
                c.setTimeInMillis(event.startTimeMillis);
                sb.append("startTime=");
                if (event.startTimeMillis == 0) {
                    sb.append("            <null>");
                } else {
                    sb.append(StringUtil.calendarToString(c));
                }
                sb.append(", connectionType=").append(
                        getconnectionTypeToString(event.connectionType));
                sb.append(", wpsMethod=");
                switch (event.wpsMethod) {
                    case P2pConnectionEvent.WPS_NA:
                        sb.append("NA");
                        break;
                    case P2pConnectionEvent.WPS_PBC:
                        sb.append("PBC");
                        break;
                    case P2pConnectionEvent.WPS_DISPLAY:
                        sb.append("DISPLAY");
                        break;
                    case P2pConnectionEvent.WPS_KEYPAD:
                        sb.append("KEYPAD");
                        break;
                    case P2pConnectionEvent.WPS_LABEL:
                        sb.append("LABLE");
                        break;
                    default:
                        sb.append("UNKNOWN");
                        break;
                }
                sb.append(", durationTakenToConnectMillis=");
                sb.append(event.durationTakenToConnectMillis);
                sb.append(", groupRole=").append(getGroupRoleToString(event.groupRole));
                sb.append(", tryCount=");
                sb.append(event.tryCount);
                sb.append(", inviteToNeg=");
                sb.append(event.fallbackToNegotiationOnInviteStatusInfoUnavailable);
                sb.append(", isCcWw=");
                sb.append(event.isCountryCodeWorldMode);
                sb.append(", band=");
                sb.append(event.band);
                sb.append(", freq=");
                sb.append(event.frequencyMhz);
                sb.append(", sta freq=");
                sb.append(event.staFrequencyMhz);
                sb.append(", uid=");
                sb.append(event.uid);
                sb.append(", connectivityLevelFailureCode=").append(
                        getConnectivityLevelFailureCodeToString(
                                event.connectivityLevelFailureCode));
                if (event == mCurrentConnectionEvent) {
                    sb.append(" CURRENTLY OPEN EVENT");
                }
                pw.println(sb.toString());
            }
            pw.println("mGroupEvents:");
            for (GroupEvent event : mGroupEventList) {
                StringBuilder sb = new StringBuilder();
                Calendar c = Calendar.getInstance();
                c.setTimeInMillis(event.startTimeMillis);
                sb.append("netId=");
                sb.append(event.netId);
                sb.append(", startTime=");
                sb.append(event.startTimeMillis == 0 ? "            <null>" :
                        String.format("%tm-%td %tH:%tM:%tS.%tL", c, c, c, c, c, c));
                sb.append(", channelFrequency=");
                sb.append(event.channelFrequency);
                sb.append(", groupRole=");
                switch (event.groupRole) {
                    case GroupEvent.GROUP_CLIENT:
                        sb.append("GroupClient");
                        break;
                    case GroupEvent.GROUP_OWNER:
                    default:
                        sb.append("GroupOwner");
                        break;
                }
                sb.append(", numConnectedClients=");
                sb.append(event.numConnectedClients);
                sb.append(", numCumulativeClients=");
                sb.append(event.numCumulativeClients);
                sb.append(", sessionDurationMillis=");
                sb.append(event.sessionDurationMillis);
                sb.append(", idleDurationMillis=");
                sb.append(event.idleDurationMillis);

                if (event == mCurrentGroupEvent) {
                    sb.append(" CURRENTLY OPEN EVENT");
                }
                pw.println(sb.toString());
            }
            pw.println("mWifiP2pStatsProto.numPersistentGroup="
                    + mNumPersistentGroup);
            pw.println("mWifiP2pStatsProto.numTotalPeerScans="
                    + mWifiP2pStatsProto.numTotalPeerScans);
            pw.println("mWifiP2pStatsProto.numTotalServiceScans="
                    + mWifiP2pStatsProto.numTotalServiceScans);
        }
    }

    private String getconnectionTypeToString(int connectionType) {
        switch (connectionType) {
            case P2pConnectionEvent.CONNECTION_FRESH:
                return "FRESH";
            case P2pConnectionEvent.CONNECTION_REINVOKE:
                return "REINVOKE";
            case P2pConnectionEvent.CONNECTION_LOCAL:
                return "LOCAL";
            case P2pConnectionEvent.CONNECTION_FAST:
                return "FAST";
            default:
                return "UNKNOWN";
        }
    }

    private String getGroupRoleToString(int groupRole) {
        switch (groupRole) {
            case GroupEvent.GROUP_OWNER:
                return "OWNER";
            case GroupEvent.GROUP_CLIENT:
                return "CLIENT";
            default:
                return "UNKNOWN DURING CONNECT";
        }
    }

    private String getConnectivityLevelFailureCodeToString(int connectivityLevelFailureCode) {
        switch (connectivityLevelFailureCode) {
            case P2pConnectionEvent.CLF_NONE:
                return "NONE";
            case P2pConnectionEvent.CLF_TIMEOUT:
                return "TIMEOUT";
            case P2pConnectionEvent.CLF_CANCEL:
                return "CANCEL";
            case P2pConnectionEvent.CLF_PROV_DISC_FAIL:
                return "PROV_DISC_FAIL";
            case P2pConnectionEvent.CLF_INVITATION_FAIL:
                return "INVITATION_FAIL";
            case P2pConnectionEvent.CLF_USER_REJECT:
                return "USER_REJECT";
            case P2pConnectionEvent.CLF_NEW_CONNECTION_ATTEMPT:
                return "NEW_CONNECTION_ATTEMPT";
            case P2pConnectionEvent.CLF_GROUP_REMOVED:
                return "GROUP_REMOVED";
            case P2pConnectionEvent.CLF_CREATE_GROUP_FAILED:
                return "CREATE_GROUP_FAILED";
            case P2pConnectionEvent.CLF_UNKNOWN:
            default:
                return "UNKNOWN";
        }
    }

    /** Increment total number of peer scans */
    public void incrementPeerScans() {
        synchronized (mLock) {
            mWifiP2pStatsProto.numTotalPeerScans++;
        }
    }

    /** Increment total number of service scans */
    public void incrementServiceScans() {
        synchronized (mLock) {
            mWifiP2pStatsProto.numTotalServiceScans++;
        }
    }

    /** Set the number of saved persistent group */
    public void updatePersistentGroup(WifiP2pGroupList groups) {
        synchronized (mLock) {
            final Collection<WifiP2pGroup> list = groups.getGroupList();
            mNumPersistentGroup = list.size();
        }
    }

    /** Returns if current connection event type is FAST connection */
    public boolean isP2pFastConnectionType() {
        if (mCurrentConnectionEvent == null) {
            return false;
        }
        return P2pConnectionEvent.CONNECTION_FAST == mCurrentConnectionEvent.connectionType;
    }

    /** Gets current connection event group role string */
    public String getP2pGroupRoleString() {
        if (mCurrentConnectionEvent == null) {
            return "UNKNOWN";
        }
        return (GroupEvent.GROUP_OWNER == mCurrentConnectionEvent.groupRole) ? "GO" : "GC";
    }

    /**
     * Create a new connection event. Call when p2p attempts to make a new connection to
     * another peer. If there is a current 'un-ended' connection event, it will be ended with
     * P2pConnectionEvent.CLF_NEW_CONNECTION_ATTEMPT.
     *
     * @param connectionType indicate this connection is fresh or reinvoke.
     * @param config configuration used for this connection.
     * @param groupRole groupRole used for this connection.
     */
    public void startConnectionEvent(int connectionType, WifiP2pConfig config, int groupRole,
            int uid) {
        synchronized (mLock) {
            StringBuilder stringBuilder = new StringBuilder("Start connection event");
            if (mCurrentConnectionEvent == null) {
                stringBuilder.append(", mCurrentConnectionEvent:null");
            } else {
                stringBuilder.append(", curConnectionType:")
                        .append(getconnectionTypeToString(mCurrentConnectionEvent.connectionType))
                        .append(", curGroupRole:")
                        .append(getGroupRoleToString(mCurrentConnectionEvent.groupRole))
                        .append(", curUid:").append(mCurrentConnectionEvent.uid)
                        .append(", curConnectivityLevelFailureCode:")
                        .append(getConnectivityLevelFailureCodeToString(
                                mCurrentConnectionEvent.connectivityLevelFailureCode));
            }
            stringBuilder.append(", startConnectionType:")
                    .append(getconnectionTypeToString(connectionType))
                    .append(", startGroupRole:").append(getGroupRoleToString(groupRole))
                    .append(", startUid:").append(uid);
            Log.d(TAG, stringBuilder.toString());
            // handle overlapping connection event first.
            if (mCurrentConnectionEvent != null) {
                endConnectionEvent(P2pConnectionEvent.CLF_NEW_CONNECTION_ATTEMPT);
            }

            while (mConnectionEventList.size() >= MAX_CONNECTION_EVENTS) {
                mConnectionEventList.remove(0);
            }
            mCurrentConnectionEventStartTime = mClock.getElapsedSinceBootMillis();

            mCurrentConnectionEvent = new P2pConnectionEvent();
            mCurrentConnectionEvent.startTimeMillis = mClock.getWallClockMillis();
            mCurrentConnectionEvent.connectionType = connectionType;
            mCurrentConnectionEvent.groupRole = groupRole;

            if (config != null) {
                mCurrentConnectionEvent.wpsMethod = config.wps.setup;
                mCurrentConnectionEvent.band = convertGroupOwnerBand(config.groupOwnerBand);
                mCurrentConnectionEvent.frequencyMhz =
                        (config.groupOwnerBand < MIN_2G_FREQUENCY_MHZ) ? 0 : config.groupOwnerBand;
            }
            mCurrentConnectionEvent.staFrequencyMhz = getWifiStaFrequency();
            mCurrentConnectionEvent.uid = uid;
            if (mLastConnectionEventUid == uid && mCurrentConnectionEventStartTime < (
                    mLastConnectionEventStartTime + MAX_CONNECTION_ATTEMPT_TIME_INTERVAL_MS)) {
                mLastConnectionTryCount += 1;
            } else {
                mLastConnectionTryCount = 1;
            }
            mLastConnectionEventUid = uid;
            mLastConnectionEventStartTime = mCurrentConnectionEventStartTime;
            mCurrentConnectionEvent.tryCount = mLastConnectionTryCount;

            mConnectionEventList.add(mCurrentConnectionEvent);
        }
    }

    /** Returns if there is an ongoing connection */
    public boolean hasOngoingConnection() {
        return mCurrentConnectionEvent != null;
    }

    /**
     * End a Connection event record. Call when p2p connection attempt succeeds or fails.
     * If a Connection event has not been started when .end is called,
     * a new one is created with zero duration.
     *
     * @param failure indicate the failure with WifiMetricsProto.P2pConnectionEvent.CLF_X.
     */
    public void endConnectionEvent(int failure) {
        synchronized (mLock) {
            StringBuilder stringBuilder = new StringBuilder("End connection event");
            if (mCurrentConnectionEvent == null) {
                stringBuilder.append(", mCurrentConnectionEvent:null");
            } else {
                stringBuilder.append(", curConnectionType:")
                        .append(getconnectionTypeToString(mCurrentConnectionEvent.connectionType))
                        .append(", curGroupRole:")
                        .append(getGroupRoleToString(mCurrentConnectionEvent.groupRole))
                        .append(", curUid:")
                        .append(mCurrentConnectionEvent.uid)
                        .append(", curConnectivityLevelFailureCode:")
                        .append(getConnectivityLevelFailureCodeToString(
                                mCurrentConnectionEvent.connectivityLevelFailureCode));
            }
            stringBuilder.append(", endConnectivityLevelFailureCode:")
                    .append(getConnectivityLevelFailureCodeToString(failure));
            Log.d(TAG, stringBuilder.toString());
            if (mCurrentConnectionEvent == null) {
                // Reinvoking a group with invitation will be handled in supplicant.
                // There won't be a connection starting event in framework.
                // THe framework only get the connection ending event in GroupStarted state.
                startConnectionEvent(P2pConnectionEvent.CONNECTION_REINVOKE, null,
                        GroupEvent.GROUP_UNKNOWN, SYSTEM_UID);
            }

            mCurrentConnectionEvent.durationTakenToConnectMillis = (int)
                    (mClock.getElapsedSinceBootMillis()
                    - mCurrentConnectionEventStartTime);
            mCurrentConnectionEvent.connectivityLevelFailureCode = failure;
            mCurrentConnectionEvent.isCountryCodeWorldMode = mIsCountryCodeWorldMode;

            WifiStatsLog.write(WifiStatsLog.WIFI_P2P_CONNECTION_REPORTED,
                    convertConnectionType(mCurrentConnectionEvent.connectionType),
                    mCurrentConnectionEvent.durationTakenToConnectMillis,
                    mCurrentConnectionEvent.durationTakenToConnectMillis / 200,
                    convertFailureCode(failure),
                    convertGroupRole(mCurrentConnectionEvent.groupRole),
                    convertBandStatsLog(mCurrentConnectionEvent.band),
                    mCurrentConnectionEvent.frequencyMhz,
                    mCurrentConnectionEvent.staFrequencyMhz,
                    mCurrentConnectionEvent.uid,
                    mIsCountryCodeWorldMode,
                    mCurrentConnectionEvent.fallbackToNegotiationOnInviteStatusInfoUnavailable,
                    mCurrentConnectionEvent.tryCount);
            mCurrentConnectionEvent = null;
            if (P2pConnectionEvent.CLF_NONE == failure) {
                mLastConnectionTryCount = 0;
            }
        }
    }

    /**
     * Fallback to GO negotiation if device receives invitation response status code -
     * information is currently unavailable
     */
    public void setFallbackToNegotiationOnInviteStatusInfoUnavailable() {
        if (mCurrentConnectionEvent == null) {
            return;
        }
        mCurrentConnectionEvent.fallbackToNegotiationOnInviteStatusInfoUnavailable = true;
    }

   /** Sets if the Country Code is in world mode */
    public void setIsCountryCodeWorldMode(boolean isCountryCodeWorldMode) {
        mIsCountryCodeWorldMode = isCountryCodeWorldMode;
    }

    private int convertConnectionType(int connectionType) {
        switch (connectionType) {
            case P2pConnectionEvent.CONNECTION_FRESH:
                return WifiStatsLog.WIFI_P2P_CONNECTION_REPORTED__TYPE__FRESH;
            case P2pConnectionEvent.CONNECTION_REINVOKE:
                return WifiStatsLog.WIFI_P2P_CONNECTION_REPORTED__TYPE__REINVOKE;
            case P2pConnectionEvent.CONNECTION_LOCAL:
                return WifiStatsLog.WIFI_P2P_CONNECTION_REPORTED__TYPE__LOCAL;
            case P2pConnectionEvent.CONNECTION_FAST:
                return WifiStatsLog.WIFI_P2P_CONNECTION_REPORTED__TYPE__FAST;
            default:
                return WifiStatsLog.WIFI_P2P_CONNECTION_REPORTED__TYPE__UNSPECIFIED;
        }
    }

    private int convertFailureCode(int failureCode) {
        switch (failureCode) {
            case P2pConnectionEvent.CLF_NONE:
                return WifiStatsLog.WIFI_P2P_CONNECTION_REPORTED__FAILURE_CODE__NONE;
            case P2pConnectionEvent.CLF_TIMEOUT:
                return WifiStatsLog.WIFI_P2P_CONNECTION_REPORTED__FAILURE_CODE__TIMEOUT;
            case P2pConnectionEvent.CLF_CANCEL:
                return WifiStatsLog.WIFI_P2P_CONNECTION_REPORTED__FAILURE_CODE__CANCEL;
            case P2pConnectionEvent.CLF_PROV_DISC_FAIL:
                return WifiStatsLog.WIFI_P2P_CONNECTION_REPORTED__FAILURE_CODE__PROV_DISC_FAIL;
            case P2pConnectionEvent.CLF_INVITATION_FAIL:
                return WifiStatsLog.WIFI_P2P_CONNECTION_REPORTED__FAILURE_CODE__INVITATION_FAIL;
            case P2pConnectionEvent.CLF_USER_REJECT:
                return WifiStatsLog.WIFI_P2P_CONNECTION_REPORTED__FAILURE_CODE__USER_REJECT;
            case P2pConnectionEvent.CLF_NEW_CONNECTION_ATTEMPT:
                return WifiStatsLog
                        .WIFI_P2P_CONNECTION_REPORTED__FAILURE_CODE__NEW_CONNECTION_ATTEMPT;
            case P2pConnectionEvent.CLF_GROUP_REMOVED:
                return WifiStatsLog
                        .WIFI_P2P_CONNECTION_REPORTED__FAILURE_CODE__GROUP_REMOVED;
            case P2pConnectionEvent.CLF_CREATE_GROUP_FAILED:
                return WifiStatsLog
                        .WIFI_P2P_CONNECTION_REPORTED__FAILURE_CODE__CREATE_GROUP_FAILED;
            case P2pConnectionEvent.CLF_UNKNOWN:
            default:
                return WifiStatsLog.WIFI_P2P_CONNECTION_REPORTED__FAILURE_CODE__UNKNOWN;
        }
    }

    private int convertGroupRole(int groupRole) {
        switch (groupRole) {
            case GroupEvent.GROUP_OWNER:
                return WifiStatsLog.WIFI_P2P_CONNECTION_REPORTED__GROUP_ROLE__GROUP_OWNER;
            case GroupEvent.GROUP_CLIENT:
                return WifiStatsLog.WIFI_P2P_CONNECTION_REPORTED__GROUP_ROLE__GROUP_CLIENT;
            default:
                return WifiStatsLog.WIFI_P2P_CONNECTION_REPORTED__GROUP_ROLE__GROUP_UNKNOWN;
        }
    }

    private int convertGroupOwnerBand(int bandOrFrequency) {
        if (bandOrFrequency >= MIN_2G_FREQUENCY_MHZ) {
            return P2pConnectionEvent.BAND_FREQUENCY;
        } else {
            switch (bandOrFrequency) {
                case WifiP2pConfig.GROUP_OWNER_BAND_AUTO:
                    return P2pConnectionEvent.BAND_AUTO;
                case WifiP2pConfig.GROUP_OWNER_BAND_2GHZ:
                    return P2pConnectionEvent.BAND_2G;
                case WifiP2pConfig.GROUP_OWNER_BAND_5GHZ:
                    return P2pConnectionEvent.BAND_5G;
                default:
                    return P2pConnectionEvent.BAND_UNKNOWN;
            }
        }
    }

    private int convertBandStatsLog(int band) {
        switch (band) {
            case P2pConnectionEvent.BAND_AUTO:
                return WifiStatsLog.WIFI_P2P_CONNECTION_REPORTED__BAND__BAND_AUTO;
            case P2pConnectionEvent.BAND_2G:
                return WifiStatsLog.WIFI_P2P_CONNECTION_REPORTED__BAND__BAND_2G;
            case P2pConnectionEvent.BAND_5G:
                return WifiStatsLog.WIFI_P2P_CONNECTION_REPORTED__BAND__BAND_5G;
            case P2pConnectionEvent.BAND_6G:
                return WifiStatsLog.WIFI_P2P_CONNECTION_REPORTED__BAND__BAND_6G;
            case P2pConnectionEvent.BAND_FREQUENCY:
                return WifiStatsLog.WIFI_P2P_CONNECTION_REPORTED__BAND__BAND_FREQUENCY;
            default:
                return WifiStatsLog.WIFI_P2P_CONNECTION_REPORTED__BAND__BAND_UNKNOWN;
        }
    }

    private int getWifiStaFrequency() {
        WifiManager wifiManager = mContext.getSystemService(WifiManager.class);
        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
        if (wifiInfo.getFrequency() > 0) {
            return wifiInfo.getFrequency();
        } else {
            return 0;
        }
    }

    /**
     * Create a new group event.
     *
     * @param group the information of started group.
     */
    public void startGroupEvent(WifiP2pGroup group) {
        if (group == null) {
            if (DBG) Log.d(TAG, "Cannot start group event due to null group");
            return;
        }
        synchronized (mLock) {
            // handle overlapping group event first.
            if (mCurrentGroupEvent != null) {
                if (DBG) Log.d(TAG, "Overlapping group event!");
                endGroupEvent();
            }

            while (mGroupEventList.size() >= MAX_GROUP_EVENTS) {
                mGroupEventList.remove(0);
            }
            mCurrentGroupEventStartTime = mClock.getElapsedSinceBootMillis();
            if (group.getClientList().size() == 0) {
                mCurrentGroupEventIdleStartTime = mClock.getElapsedSinceBootMillis();
            } else {
                mCurrentGroupEventIdleStartTime = 0;
            }

            mCurrentGroupEvent = new GroupEvent();
            mCurrentGroupEvent.netId = group.getNetworkId();
            mCurrentGroupEvent.startTimeMillis = mClock.getWallClockMillis();
            mCurrentGroupEvent.numConnectedClients = group.getClientList().size();
            mCurrentGroupEvent.channelFrequency = group.getFrequency();
            mCurrentGroupEvent.groupRole = group.isGroupOwner()
                    ? GroupEvent.GROUP_OWNER
                    : GroupEvent.GROUP_CLIENT;
            mGroupEventList.add(mCurrentGroupEvent);
        }
    }

    /**
     * Update the information of started group.
     */
    public void updateGroupEvent(WifiP2pGroup group) {
        if (group == null) {
            if (DBG) Log.d(TAG, "Cannot update group event due to null group.");
            return;
        }
        synchronized (mLock) {
            if (mCurrentGroupEvent == null) {
                Log.w(TAG, "Cannot update group event due to no current group.");
                return;
            }

            if (mCurrentGroupEvent.netId != group.getNetworkId()) {
                Log.w(TAG, "Updating group id " + group.getNetworkId()
                        + " is different from current group id " + mCurrentGroupEvent.netId
                        + ".");
                return;
            }

            int delta = group.getClientList().size() - mCurrentGroupEvent.numConnectedClients;
            mCurrentGroupEvent.numConnectedClients = group.getClientList().size();
            if (delta > 0) {
                mCurrentGroupEvent.numCumulativeClients += delta;
            }

            // if new client comes during idle period, cumulate idle duration and reset idle timer.
            // if the last client disconnected during non-idle period, start idle timer.
            if (mCurrentGroupEventIdleStartTime > 0) {
                if (group.getClientList().size() > 0) {
                    mCurrentGroupEvent.idleDurationMillis +=
                            (mClock.getElapsedSinceBootMillis()
                            - mCurrentGroupEventIdleStartTime);
                    mCurrentGroupEventIdleStartTime = 0;
                }
            } else {
                if (group.getClientList().size() == 0) {
                    mCurrentGroupEventIdleStartTime = mClock.getElapsedSinceBootMillis();
                }
            }
        }
    }

    /**
     * End a group event.
     */
    public void endGroupEvent() {
        synchronized (mLock) {
            if (mCurrentGroupEvent != null) {
                mCurrentGroupEvent.sessionDurationMillis = (int)
                        (mClock.getElapsedSinceBootMillis()
                        - mCurrentGroupEventStartTime);
                if (mCurrentGroupEventIdleStartTime > 0) {
                    mCurrentGroupEvent.idleDurationMillis +=
                            (mClock.getElapsedSinceBootMillis()
                            - mCurrentGroupEventIdleStartTime);
                    mCurrentGroupEventIdleStartTime = 0;
                }
            } else {
                Log.e(TAG, "No current group!");
            }
            mCurrentGroupEvent = null;
        }
    }

    /* Log Metrics */
}
