/*
 * Copyright 2018 The Android Open Source Project
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

package com.android.server.wifi;

import static android.net.wifi.WifiInfo.DEFAULT_MAC_ADDRESS;
import static android.net.wifi.WifiInfo.INVALID_RSSI;
import static android.net.wifi.WifiInfo.LINK_SPEED_UNKNOWN;

import static com.android.server.wifi.WifiHealthMonitor.HEALTH_MONITOR_COUNT_TX_SPEED_MIN_MBPS;
import static com.android.server.wifi.WifiHealthMonitor.HEALTH_MONITOR_MIN_TX_PACKET_PER_SEC;
import static com.android.server.wifi.WifiHealthMonitor.REASON_ASSOC_REJECTION;
import static com.android.server.wifi.WifiHealthMonitor.REASON_ASSOC_TIMEOUT;
import static com.android.server.wifi.WifiHealthMonitor.REASON_AUTH_FAILURE;
import static com.android.server.wifi.WifiHealthMonitor.REASON_CONNECTION_FAILURE;
import static com.android.server.wifi.WifiHealthMonitor.REASON_CONNECTION_FAILURE_DISCONNECTION;
import static com.android.server.wifi.WifiHealthMonitor.REASON_DISCONNECTION_NONLOCAL;
import static com.android.server.wifi.WifiHealthMonitor.REASON_NO_FAILURE;
import static com.android.server.wifi.WifiHealthMonitor.REASON_SHORT_CONNECTION_NONLOCAL;

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.net.MacAddress;
import android.net.wifi.ScanResult;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiManager;
import android.util.ArrayMap;
import android.util.Base64;
import android.util.LocalLog;
import android.util.Log;
import android.util.Pair;
import android.util.SparseLongArray;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.Preconditions;
import com.android.server.wifi.WifiBlocklistMonitor.FailureReason;
import com.android.server.wifi.WifiHealthMonitor.FailureStats;
import com.android.server.wifi.proto.WifiScoreCardProto;
import com.android.server.wifi.proto.WifiScoreCardProto.AccessPoint;
import com.android.server.wifi.proto.WifiScoreCardProto.BandwidthStats;
import com.android.server.wifi.proto.WifiScoreCardProto.BandwidthStatsAll;
import com.android.server.wifi.proto.WifiScoreCardProto.BandwidthStatsAllLevel;
import com.android.server.wifi.proto.WifiScoreCardProto.BandwidthStatsAllLink;
import com.android.server.wifi.proto.WifiScoreCardProto.ConnectionStats;
import com.android.server.wifi.proto.WifiScoreCardProto.Event;
import com.android.server.wifi.proto.WifiScoreCardProto.HistogramBucket;
import com.android.server.wifi.proto.WifiScoreCardProto.Network;
import com.android.server.wifi.proto.WifiScoreCardProto.NetworkList;
import com.android.server.wifi.proto.WifiScoreCardProto.NetworkStats;
import com.android.server.wifi.proto.WifiScoreCardProto.SecurityType;
import com.android.server.wifi.proto.WifiScoreCardProto.Signal;
import com.android.server.wifi.proto.WifiScoreCardProto.UnivariateStatistic;
import com.android.server.wifi.proto.nano.WifiMetricsProto.BandwidthEstimatorStats;
import com.android.server.wifi.util.IntHistogram;
import com.android.server.wifi.util.LruList;
import com.android.server.wifi.util.NativeUtil;
import com.android.server.wifi.util.RssiUtil;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import javax.annotation.concurrent.NotThreadSafe;

/**
 * Retains statistical information about the performance of various
 * access points and networks, as experienced by this device.
 *
 * The purpose is to better inform future network selection and switching
 * by this device and help health monitor detect network issues.
 */
@NotThreadSafe
public class WifiScoreCard {

    public static final String DUMP_ARG = "WifiScoreCard";

    private static final String TAG = "WifiScoreCard";
    private boolean mVerboseLoggingEnabled = false;

    @VisibleForTesting
    boolean mPersistentHistograms = true;

    private static final int TARGET_IN_MEMORY_ENTRIES = 50;
    private static final int UNKNOWN_REASON = -1;

    public static final String PER_BSSID_DATA_NAME = "scorecard.proto";
    public static final String PER_NETWORK_DATA_NAME = "perNetworkData";

    static final int INSUFFICIENT_RECENT_STATS = 0;
    static final int SUFFICIENT_RECENT_STATS_ONLY = 1;
    static final int SUFFICIENT_RECENT_PREV_STATS = 2;

    private static final int MAX_FREQUENCIES_PER_SSID = 30;
    private static final int MAX_TRAFFIC_STATS_POLL_TIME_DELTA_MS = 6_000;

    private final Clock mClock;
    private final String mL2KeySeed;
    private MemoryStore mMemoryStore;
    private final DeviceConfigFacade mDeviceConfigFacade;
    private final Context mContext;
    private final WifiGlobals mWifiGlobals;
    private final LocalLog mLocalLog = new LocalLog(256);
    private final long[][][] mL2ErrorAccPercent =
            new long[NUM_LINK_BAND][NUM_LINK_DIRECTION][NUM_SIGNAL_LEVEL];
    private final long[][][] mBwEstErrorAccPercent =
            new long[NUM_LINK_BAND][NUM_LINK_DIRECTION][NUM_SIGNAL_LEVEL];
    private final long[][][] mBwEstValue =
            new long[NUM_LINK_BAND][NUM_LINK_DIRECTION][NUM_SIGNAL_LEVEL];
    private final int[][][] mBwEstCount =
            new int[NUM_LINK_BAND][NUM_LINK_DIRECTION][NUM_SIGNAL_LEVEL];

    @VisibleForTesting
    static final int[] RSSI_BUCKETS = intsInRange(-100, -20);

    private static int[] intsInRange(int min, int max) {
        int[] a = new int[max - min + 1];
        for (int i = 0; i < a.length; i++) {
            a[i] = min + i;
        }
        return a;
    }

    /** Our view of the memory store */
    public interface MemoryStore {
        /** Requests a read, with asynchronous reply */
        void read(String key, String name, BlobListener blobListener);
        /** Requests a write, does not wait for completion */
        void write(String key, String name, byte[] value);
        /** Sets the cluster identifier */
        void setCluster(String key, String cluster);
        /** Requests removal of all entries matching the cluster */
        void removeCluster(String cluster);
    }
    /** Asynchronous response to a read request */
    public interface BlobListener {
        /** Provides the previously stored value, or null if none */
        void onBlobRetrieved(@Nullable byte[] value);
    }

    /**
     * Installs a memory store.
     *
     * Normally this happens just once, shortly after we start. But wifi can
     * come up before the disk is ready, and we might not yet have a valid wall
     * clock when we start up, so we need to be prepared to begin recording data
     * even if the MemoryStore is not yet available.
     *
     * When the store is installed for the first time, we want to merge any
     * recently recorded data together with data already in the store. But if
     * the store restarts and has to be reinstalled, we don't want to do
     * this merge, because that would risk double-counting the old data.
     *
     */
    public void installMemoryStore(@NonNull MemoryStore memoryStore) {
        Preconditions.checkNotNull(memoryStore);
        if (mMemoryStore == null) {
            mMemoryStore = memoryStore;
            Log.i(TAG, "Installing MemoryStore");
            requestReadForAllChanged();
        } else {
            mMemoryStore = memoryStore;
            Log.e(TAG, "Reinstalling MemoryStore");
            // Our caller will call doWrites() eventually, so nothing more to do here.
        }
    }

    /**
     * Enable/Disable verbose logging.
     *
     * @param verbose true to enable and false to disable.
     */
    public void enableVerboseLogging(boolean verbose) {
        mVerboseLoggingEnabled = verbose;
    }

    @VisibleForTesting
    static final long TS_NONE = -1;

    /** Tracks the connection status per Wifi interface. */
    private static final class IfaceInfo {
        /**
         * Timestamp of the start of the most recent connection attempt.
         *
         * Based on mClock.getElapsedSinceBootMillis().
         *
         * This is for calculating the time to connect and the duration of the connection.
         * Any negative value means we are not currently connected.
         */
        public long tsConnectionAttemptStart = TS_NONE;

        /**
         * Timestamp captured when we find out about a firmware roam
         */
        public long tsRoam = TS_NONE;

        /**
         * Becomes true the first time we see a poll with a valid RSSI in a connection
         */
        public boolean polled = false;

        /**
         * Records validation success for the current connection.
         *
         * We want to gather statistics only on the first success.
         */
        public boolean validatedThisConnectionAtLeastOnce = false;

        /**
         * A note to ourself that we are attempting a network switch
         */
        public boolean attemptingSwitch = false;

        /**
         *  SSID of currently connected or connecting network. Used during disconnection
         */
        public String ssidCurr = "";
        /**
         *  SSID of previously connected network. Used during disconnection when connection attempt
         *  of current network is issued before the disconnection of previous network.
         */
        public String ssidPrev = "";
        /**
         * A flag that notes that current disconnection is not generated by wpa_supplicant
         * which may indicate abnormal disconnection.
         */
        public boolean nonlocalDisconnection = false;
        public int disconnectionReason;

        public long firmwareAlertTimeMs = TS_NONE;
    }

    /**
     * String key: iface name
     * IfaceInfo value: current status of iface
     */
    private final Map<String, IfaceInfo> mIfaceToInfoMap = new ArrayMap<>();

    /** Gets the IfaceInfo, or create it if it doesn't exist. */
    private IfaceInfo getIfaceInfo(String ifaceName) {
        return mIfaceToInfoMap.computeIfAbsent(ifaceName, k -> new IfaceInfo());
    }

    /**
     * @param clock is the time source
     * @param l2KeySeed is for making our L2Keys usable only on this device
     */
    public WifiScoreCard(Clock clock, String l2KeySeed, DeviceConfigFacade deviceConfigFacade,
            Context context, WifiGlobals wifiGlobals) {
        mClock = clock;
        mContext = context;
        mL2KeySeed = l2KeySeed;
        mPlaceholderPerBssid = new PerBssid("", MacAddress.fromString(DEFAULT_MAC_ADDRESS));
        mPlaceholderPerNetwork = new PerNetwork("");
        mDeviceConfigFacade = deviceConfigFacade;
        mWifiGlobals = wifiGlobals;
    }

    /**
     * Gets the L2Key and GroupHint associated with the connection.
     */
    public @NonNull Pair<String, String> getL2KeyAndGroupHint(ExtendedWifiInfo wifiInfo) {
        PerBssid perBssid = lookupBssid(wifiInfo.getSSID(), wifiInfo.getBSSID());
        if (perBssid == mPlaceholderPerBssid) {
            return new Pair<>(null, null);
        }
        return new Pair<>(perBssid.getL2Key(), groupHintFromSsid(perBssid.ssid));
    }

    /**
     * Computes the GroupHint associated with the given ssid.
     */
    public @NonNull String groupHintFromSsid(String ssid) {
        final long groupIdHash = computeHashLong(ssid, mPlaceholderPerBssid.bssid, mL2KeySeed);
        return groupHintFromLong(groupIdHash);
    }

    /** Handle network disconnection. */
    public void resetConnectionState(String ifaceName) {
        IfaceInfo ifaceInfo = getIfaceInfo(ifaceName);
        noteDisconnectionForIface(ifaceInfo);
        resetConnectionStateForIfaceInternal(ifaceInfo, true);
    }

    /** Handle shutdown event. */
    public void resetAllConnectionStates() {
        for (IfaceInfo ifaceInfo : mIfaceToInfoMap.values()) {
            noteDisconnectionForIface(ifaceInfo);
            resetConnectionStateForIfaceInternal(ifaceInfo, true);
        }
    }

    private void noteDisconnectionForIface(IfaceInfo ifaceInfo) {
        String ssidDisconnected = ifaceInfo.attemptingSwitch
                ? ifaceInfo.ssidPrev : ifaceInfo.ssidCurr;
        updatePerNetwork(Event.DISCONNECTION, ssidDisconnected, INVALID_RSSI, LINK_SPEED_UNKNOWN,
                UNKNOWN_REASON, ifaceInfo);
        if (mVerboseLoggingEnabled && ifaceInfo.tsConnectionAttemptStart > TS_NONE
                && !ifaceInfo.attemptingSwitch) {
            Log.v(TAG, "handleNetworkDisconnect", new Exception());
        }
    }

    private void resetAllConnectionStatesInternal() {
        for (IfaceInfo ifaceInfo : mIfaceToInfoMap.values()) {
            resetConnectionStateForIfaceInternal(ifaceInfo, false);
        }
    }

    /**
     * @param calledFromResetConnectionState says the call is from outside the class,
     *        indicating that we need to respect the value of mAttemptingSwitch.
     */
    private void resetConnectionStateForIfaceInternal(IfaceInfo ifaceInfo,
            boolean calledFromResetConnectionState) {
        if (!calledFromResetConnectionState) {
            ifaceInfo.attemptingSwitch = false;
        }
        if (!ifaceInfo.attemptingSwitch) {
            ifaceInfo.tsConnectionAttemptStart = TS_NONE;
        }
        ifaceInfo.tsRoam = TS_NONE;
        ifaceInfo.polled = false;
        ifaceInfo.validatedThisConnectionAtLeastOnce = false;
        ifaceInfo.nonlocalDisconnection = false;
        ifaceInfo.firmwareAlertTimeMs = TS_NONE;
    }

    /**
     * Updates perBssid using relevant parts of WifiInfo
     *
     * @param wifiInfo object holding relevant values.
     */
    private void updatePerBssid(WifiScoreCardProto.Event event, ExtendedWifiInfo wifiInfo) {
        PerBssid perBssid = lookupBssid(wifiInfo.getSSID(), wifiInfo.getBSSID());
        perBssid.updateEventStats(event,
                wifiInfo.getFrequency(),
                wifiInfo.getRssi(),
                wifiInfo.getLinkSpeed(),
                wifiInfo.getIfaceName());
        perBssid.setNetworkConfigId(wifiInfo.getNetworkId());
        logd("BSSID update " + event + " ID: " + perBssid.id + " " + wifiInfo);
    }

    /**
     * Updates perNetwork with SSID, current RSSI and failureReason. failureReason is  meaningful
     * only during connection failure.
     */
    private void updatePerNetwork(WifiScoreCardProto.Event event, String ssid, int rssi,
            int txSpeed, int failureReason, IfaceInfo ifaceInfo) {
        PerNetwork perNetwork = lookupNetwork(ssid);
        logd("network update " + event + ((ssid == null) ? " " : " "
                    + ssid) + " ID: " + perNetwork.id + " RSSI " + rssi + " txSpeed " + txSpeed);
        perNetwork.updateEventStats(event, rssi, txSpeed, failureReason, ifaceInfo);
    }

    /**
     * Updates the score card after a signal poll
     *
     * @param wifiInfo object holding relevant values
     */
    public void noteSignalPoll(@NonNull ExtendedWifiInfo wifiInfo) {
        IfaceInfo ifaceInfo = getIfaceInfo(wifiInfo.getIfaceName());
        if (!ifaceInfo.polled && wifiInfo.getRssi() != INVALID_RSSI) {
            updatePerBssid(Event.FIRST_POLL_AFTER_CONNECTION, wifiInfo);
            ifaceInfo.polled = true;
        }
        updatePerBssid(Event.SIGNAL_POLL, wifiInfo);
        int validTxSpeed = geTxLinkSpeedWithSufficientTxRate(wifiInfo);
        updatePerNetwork(Event.SIGNAL_POLL, wifiInfo.getSSID(), wifiInfo.getRssi(),
                validTxSpeed, UNKNOWN_REASON, ifaceInfo);
        if (ifaceInfo.tsRoam > TS_NONE && wifiInfo.getRssi() != INVALID_RSSI) {
            long duration = mClock.getElapsedSinceBootMillis() - ifaceInfo.tsRoam;
            if (duration >= SUCCESS_MILLIS_SINCE_ROAM) {
                updatePerBssid(Event.ROAM_SUCCESS, wifiInfo);
                ifaceInfo.tsRoam = TS_NONE;
                doWritesBssid();
            }
        }
    }

    private int geTxLinkSpeedWithSufficientTxRate(@NonNull ExtendedWifiInfo wifiInfo) {
        int txRate = (int) Math.ceil(wifiInfo.getSuccessfulTxPacketsPerSecond()
                + wifiInfo.getLostTxPacketsPerSecond()
                + wifiInfo.getRetriedTxPacketsPerSecond());
        int txSpeed = wifiInfo.getTxLinkSpeedMbps();
        logd("txRate: " + txRate + " txSpeed: " + txSpeed);
        return (txRate >= HEALTH_MONITOR_MIN_TX_PACKET_PER_SEC) ? txSpeed : LINK_SPEED_UNKNOWN;
    }

    /** Wait a few seconds before considering the roam successful */
    private static final long SUCCESS_MILLIS_SINCE_ROAM = 4_000;

    /**
     * Updates the score card after IP configuration
     *
     * @param wifiInfo object holding relevant values
     */
    public void noteIpConfiguration(@NonNull ExtendedWifiInfo wifiInfo) {
        IfaceInfo ifaceInfo = getIfaceInfo(wifiInfo.getIfaceName());
        updatePerBssid(Event.IP_CONFIGURATION_SUCCESS, wifiInfo);
        updatePerNetwork(Event.IP_CONFIGURATION_SUCCESS, wifiInfo.getSSID(), wifiInfo.getRssi(),
                wifiInfo.getTxLinkSpeedMbps(), UNKNOWN_REASON, ifaceInfo);
        PerNetwork perNetwork = lookupNetwork(wifiInfo.getSSID());
        perNetwork.initBandwidthFilter(wifiInfo);
        ifaceInfo.attemptingSwitch = false;
        doWrites();
    }

    /**
     * Updates the score card after network validation success.
     *
     * @param wifiInfo object holding relevant values
     */
    public void noteValidationSuccess(@NonNull ExtendedWifiInfo wifiInfo) {
        IfaceInfo ifaceInfo = getIfaceInfo(wifiInfo.getIfaceName());
        if (ifaceInfo.validatedThisConnectionAtLeastOnce) return; // Only once per connection
        updatePerBssid(Event.VALIDATION_SUCCESS, wifiInfo);
        ifaceInfo.validatedThisConnectionAtLeastOnce = true;
        doWrites();
    }

    /**
     * Updates the score card after network validation failure
     *
     * @param wifiInfo object holding relevant values
     */
    public void noteValidationFailure(@NonNull ExtendedWifiInfo wifiInfo) {
        // VALIDATION_FAILURE is not currently recorded.
    }

    /**
     * Records the start of a connection attempt
     *
     * @param wifiInfo may have state about an existing connection
     * @param scanRssi is the highest RSSI of recent scan found from scanDetailCache
     * @param ssid is the network SSID of connection attempt
     */
    public void noteConnectionAttempt(@NonNull ExtendedWifiInfo wifiInfo,
            int scanRssi, String ssid) {
        IfaceInfo ifaceInfo = getIfaceInfo(wifiInfo.getIfaceName());
        // We may or may not be currently connected. If not, simply record the start.
        // But if we are connected, wrap up the old one first.
        if (ifaceInfo.tsConnectionAttemptStart > TS_NONE) {
            if (ifaceInfo.polled) {
                updatePerBssid(Event.LAST_POLL_BEFORE_SWITCH, wifiInfo);
            }
            ifaceInfo.attemptingSwitch = true;
        }
        ifaceInfo.tsConnectionAttemptStart = mClock.getElapsedSinceBootMillis();
        ifaceInfo.polled = false;
        ifaceInfo.ssidPrev = ifaceInfo.ssidCurr;
        ifaceInfo.ssidCurr = ssid;
        ifaceInfo.firmwareAlertTimeMs = TS_NONE;

        updatePerNetwork(Event.CONNECTION_ATTEMPT, ssid, scanRssi, LINK_SPEED_UNKNOWN,
                UNKNOWN_REASON, ifaceInfo);
        logd("CONNECTION_ATTEMPT" + (ifaceInfo.attemptingSwitch ? " X " : " ") + wifiInfo);
    }

    /**
     * Records a newly assigned NetworkAgent netId.
     */
    public void noteNetworkAgentCreated(@NonNull ExtendedWifiInfo wifiInfo, int networkAgentId) {
        PerBssid perBssid = lookupBssid(wifiInfo.getSSID(), wifiInfo.getBSSID());
        logd("NETWORK_AGENT_ID: " + networkAgentId + " ID: " + perBssid.id);
        perBssid.mNetworkAgentId = networkAgentId;
    }

    /**
     * Record disconnection not initiated by wpa_supplicant in connected mode
     * @param reason is detailed disconnection reason code
     */
    public void noteNonlocalDisconnect(String ifaceName, int reason) {
        IfaceInfo ifaceInfo = getIfaceInfo(ifaceName);

        ifaceInfo.nonlocalDisconnection = true;
        ifaceInfo.disconnectionReason = reason;
        logd("nonlocal disconnection with reason: " + reason);
    }

    /**
     * Record firmware alert timestamp and error code
     */
    public void noteFirmwareAlert(int errorCode) {
        long ts = mClock.getElapsedSinceBootMillis();
        // Firmware alert is device-level, not per-iface. Thus, note firmware alert on all ifaces.
        for (IfaceInfo ifaceInfo : mIfaceToInfoMap.values()) {
            ifaceInfo.firmwareAlertTimeMs = ts;
        }
        logd("firmware alert with error code: " + errorCode);
    }

    /**
     * Updates the score card after a failed connection attempt
     *
     * @param wifiInfo object holding relevant values.
     * @param scanRssi is the highest RSSI of recent scan found from scanDetailCache
     * @param ssid is the network SSID.
     * @param failureReason is connection failure reason
     */
    public void noteConnectionFailure(@NonNull ExtendedWifiInfo wifiInfo,
            int scanRssi, String ssid, @FailureReason int failureReason) {
        IfaceInfo ifaceInfo = getIfaceInfo(wifiInfo.getIfaceName());
        // TODO: add the breakdown of level2FailureReason
        updatePerBssid(Event.CONNECTION_FAILURE, wifiInfo);
        updatePerNetwork(Event.CONNECTION_FAILURE, ssid, scanRssi, LINK_SPEED_UNKNOWN,
                failureReason, ifaceInfo);
        resetConnectionStateForIfaceInternal(ifaceInfo, false);
    }

    /**
     * Updates the score card after network reachability failure
     *
     * @param wifiInfo object holding relevant values
     */
    public void noteIpReachabilityLost(@NonNull ExtendedWifiInfo wifiInfo) {
        IfaceInfo ifaceInfo = getIfaceInfo(wifiInfo.getIfaceName());
        if (ifaceInfo.tsRoam > TS_NONE) {
            ifaceInfo.tsConnectionAttemptStart = ifaceInfo.tsRoam; // just to update elapsed
            updatePerBssid(Event.ROAM_FAILURE, wifiInfo);
        } else {
            updatePerBssid(Event.IP_REACHABILITY_LOST, wifiInfo);
        }
        // No need to call resetConnectionStateInternal() because
        // resetConnectionState() will be called after WifiNative.disconnect() in ClientModeImpl
        doWrites();
    }

    /**
     * Updates the score card before a roam
     *
     * We may have already done a firmware roam, but wifiInfo has not yet
     * been updated, so we still have the old state.
     *
     * @param wifiInfo object holding relevant values
     */
    private void noteRoam(IfaceInfo ifaceInfo, @NonNull ExtendedWifiInfo wifiInfo) {
        updatePerBssid(Event.LAST_POLL_BEFORE_ROAM, wifiInfo);
        ifaceInfo.tsRoam = mClock.getElapsedSinceBootMillis();
    }

    /**
     * Called when the supplicant state is about to change, before wifiInfo is updated
     *
     * @param wifiInfo object holding old values
     * @param state the new supplicant state
     */
    public void noteSupplicantStateChanging(@NonNull ExtendedWifiInfo wifiInfo,
            SupplicantState state) {
        IfaceInfo ifaceInfo = getIfaceInfo(wifiInfo.getIfaceName());
        if (state == SupplicantState.COMPLETED && wifiInfo.getSupplicantState() == state) {
            // Our signal that a firmware roam has occurred
            noteRoam(ifaceInfo, wifiInfo);
        }
        logd("Changing state to " + state + " " + wifiInfo);
    }

    /**
     * Called after the supplicant state changed
     *
     * @param wifiInfo object holding old values
     */
    public void noteSupplicantStateChanged(ExtendedWifiInfo wifiInfo) {
        logd("ifaceName=" + wifiInfo.getIfaceName() + ",wifiInfo=" + wifiInfo);
    }

    /**
     * Updates the score card when wifi is disabled
     *
     * @param wifiInfo object holding relevant values
     */
    public void noteWifiDisabled(@NonNull ExtendedWifiInfo wifiInfo) {
        updatePerBssid(Event.WIFI_DISABLED, wifiInfo);
    }

    /**
     * Records the last successful L2 connection timestamp for a BSSID.
     * @return the previous BSSID connection time.
     */
    public long setBssidConnectionTimestampMs(String ssid, String bssid, long timeMs) {
        PerBssid perBssid = lookupBssid(ssid, bssid);
        long prev = perBssid.lastConnectionTimestampMs;
        perBssid.lastConnectionTimestampMs = timeMs;
        return prev;
    }

    /**
     * Returns the last successful L2 connection time for this BSSID.
     */
    public long getBssidConnectionTimestampMs(String ssid, String bssid) {
        return lookupBssid(ssid, bssid).lastConnectionTimestampMs;
    }

    /**
     * Increment the blocklist streak count for a failure reason on an AP.
     * @return the updated count
     */
    public int incrementBssidBlocklistStreak(String ssid, String bssid,
            @WifiBlocklistMonitor.FailureReason int reason) {
        PerBssid perBssid = lookupBssid(ssid, bssid);
        return ++perBssid.blocklistStreakCount[reason];
    }

    /**
     * Get the blocklist streak count for a failure reason on an AP.
     * @return the blocklist streak count
     */
    public int getBssidBlocklistStreak(String ssid, String bssid,
            @WifiBlocklistMonitor.FailureReason int reason) {
        return lookupBssid(ssid, bssid).blocklistStreakCount[reason];
    }

    /**
     * Clear the blocklist streak count for a failure reason on an AP.
     */
    public void resetBssidBlocklistStreak(String ssid, String bssid,
            @WifiBlocklistMonitor.FailureReason int reason) {
        lookupBssid(ssid, bssid).blocklistStreakCount[reason] = 0;
    }

    /**
     * Clear the blocklist streak count for all APs that belong to this SSID.
     */
    public void resetBssidBlocklistStreakForSsid(@NonNull String ssid) {
        Iterator<Map.Entry<MacAddress, PerBssid>> it = mApForBssid.entrySet().iterator();
        while (it.hasNext()) {
            PerBssid perBssid = it.next().getValue();
            if (!ssid.equals(perBssid.ssid)) {
                continue;
            }
            for (int i = 0; i < perBssid.blocklistStreakCount.length; i++) {
                perBssid.blocklistStreakCount[i] = 0;
            }
        }
    }

    /**
     * Detect abnormal disconnection at high RSSI with a high rate
     */
    public int detectAbnormalDisconnection(String ifaceName) {
        IfaceInfo ifaceInfo = getIfaceInfo(ifaceName);
        String ssid = ifaceInfo.attemptingSwitch ? ifaceInfo.ssidPrev : ifaceInfo.ssidCurr;
        PerNetwork perNetwork = lookupNetwork(ssid);
        NetworkConnectionStats recentStats = perNetwork.getRecentStats();
        if (recentStats.getRecentCountCode() == CNT_SHORT_CONNECTION_NONLOCAL) {
            return detectAbnormalFailureReason(recentStats, CNT_SHORT_CONNECTION_NONLOCAL,
                    REASON_SHORT_CONNECTION_NONLOCAL,
                    mDeviceConfigFacade.getShortConnectionNonlocalHighThrPercent(),
                    mDeviceConfigFacade.getShortConnectionNonlocalCountMin(),
                    CNT_DISCONNECTION);
        } else if (recentStats.getRecentCountCode() == CNT_DISCONNECTION_NONLOCAL) {
            return detectAbnormalFailureReason(recentStats, CNT_DISCONNECTION_NONLOCAL,
                    REASON_DISCONNECTION_NONLOCAL,
                    mDeviceConfigFacade.getDisconnectionNonlocalHighThrPercent(),
                    mDeviceConfigFacade.getDisconnectionNonlocalCountMin(),
                    CNT_DISCONNECTION);
        } else {
            return REASON_NO_FAILURE;
        }
    }

    /**
     * Detect abnormal connection failure at high RSSI with a high rate
     */
    public int detectAbnormalConnectionFailure(String ssid) {
        PerNetwork perNetwork = lookupNetwork(ssid);
        NetworkConnectionStats recentStats = perNetwork.getRecentStats();
        int recentCountCode = recentStats.getRecentCountCode();
        if (recentCountCode == CNT_AUTHENTICATION_FAILURE) {
            return detectAbnormalFailureReason(recentStats, CNT_AUTHENTICATION_FAILURE,
                    REASON_AUTH_FAILURE,
                    mDeviceConfigFacade.getAuthFailureHighThrPercent(),
                    mDeviceConfigFacade.getAuthFailureCountMin(),
                    CNT_CONNECTION_ATTEMPT);
        } else if (recentCountCode == CNT_ASSOCIATION_REJECTION) {
            return detectAbnormalFailureReason(recentStats, CNT_ASSOCIATION_REJECTION,
                    REASON_ASSOC_REJECTION,
                    mDeviceConfigFacade.getAssocRejectionHighThrPercent(),
                    mDeviceConfigFacade.getAssocRejectionCountMin(),
                    CNT_CONNECTION_ATTEMPT);
        } else if (recentCountCode == CNT_ASSOCIATION_TIMEOUT) {
            return detectAbnormalFailureReason(recentStats, CNT_ASSOCIATION_TIMEOUT,
                    REASON_ASSOC_TIMEOUT,
                    mDeviceConfigFacade.getAssocTimeoutHighThrPercent(),
                    mDeviceConfigFacade.getAssocTimeoutCountMin(),
                    CNT_CONNECTION_ATTEMPT);
        } else if (recentCountCode == CNT_DISCONNECTION_NONLOCAL_CONNECTING) {
            return detectAbnormalFailureReason(recentStats, CNT_DISCONNECTION_NONLOCAL_CONNECTING,
                    REASON_CONNECTION_FAILURE_DISCONNECTION,
                    mDeviceConfigFacade.getConnectionFailureDisconnectionHighThrPercent(),
                    mDeviceConfigFacade.getConnectionFailureDisconnectionCountMin(),
                    CNT_CONNECTION_ATTEMPT);
        } else if (recentCountCode == CNT_CONNECTION_FAILURE) {
            return detectAbnormalFailureReason(recentStats, CNT_CONNECTION_FAILURE,
                    REASON_CONNECTION_FAILURE,
                    mDeviceConfigFacade.getConnectionFailureHighThrPercent(),
                    mDeviceConfigFacade.getConnectionFailureCountMin(),
                    CNT_CONNECTION_ATTEMPT);
        } else {
            return REASON_NO_FAILURE;
        }
    }

    private int detectAbnormalFailureReason(NetworkConnectionStats stats, int countCode,
            int reasonCode, int highThresholdPercent, int minCount, int refCountCode) {
        // To detect abnormal failure which may trigger bugReport,
        // increase the detection threshold by thresholdRatio
        int thresholdRatio =
                mDeviceConfigFacade.getBugReportThresholdExtraRatio();
        if (isHighPercentageAndEnoughCount(stats, countCode, reasonCode,
                highThresholdPercent * thresholdRatio,
                minCount * thresholdRatio,
                refCountCode)) {
            return reasonCode;
        } else {
            return REASON_NO_FAILURE;
        }
    }

    private boolean isHighPercentageAndEnoughCount(NetworkConnectionStats stats, int countCode,
            int reasonCode, int highThresholdPercent, int minCount, int refCountCode) {
        highThresholdPercent = Math.min(highThresholdPercent, 100);
        // Use Laplace's rule of succession, useful especially for a small
        // connection attempt count
        // R = (f+1)/(n+2) with a pseudo count of 2 (one for f and one for s)
        return ((stats.getCount(countCode) >= minCount)
                && ((stats.getCount(countCode) + 1) * 100)
                >= (highThresholdPercent * (stats.getCount(refCountCode) + 2)));
    }

    final class PerBssid extends MemoryStoreAccessBase {
        public int id;
        public final String ssid;
        public final MacAddress bssid;
        public final int[] blocklistStreakCount =
                new int[WifiBlocklistMonitor.NUMBER_REASON_CODES];
        public long[][][] bandwidthStatsValue =
                new long[NUM_LINK_BAND][NUM_LINK_DIRECTION][NUM_SIGNAL_LEVEL];
        public int[][][] bandwidthStatsCount =
                new int[NUM_LINK_BAND][NUM_LINK_DIRECTION][NUM_SIGNAL_LEVEL];
        // The wall clock time in milliseconds for the last successful l2 connection.
        public long lastConnectionTimestampMs;
        public boolean changed;
        public boolean referenced;

        private SecurityType mSecurityType = null;
        private int mNetworkAgentId = Integer.MIN_VALUE;
        private int mNetworkConfigId = Integer.MIN_VALUE;
        private final Map<Pair<Event, Integer>, PerSignal>
                mSignalForEventAndFrequency = new ArrayMap<>();

        PerBssid(String ssid, MacAddress bssid) {
            super(computeHashLong(ssid, bssid, mL2KeySeed));
            this.ssid = ssid;
            this.bssid = bssid;
            this.id = idFromLong();
            this.changed = false;
            this.referenced = false;
        }
        void updateEventStats(Event event, int frequency, int rssi, int linkspeed,
                String ifaceName) {
            PerSignal perSignal = lookupSignal(event, frequency);
            if (rssi != INVALID_RSSI) {
                perSignal.rssi.update(rssi);
                changed = true;
            }
            if (linkspeed > 0) {
                perSignal.linkspeed.update(linkspeed);
                changed = true;
            }
            IfaceInfo ifaceInfo = getIfaceInfo(ifaceName);
            if (perSignal.elapsedMs != null && ifaceInfo.tsConnectionAttemptStart > TS_NONE) {
                long millis =
                        mClock.getElapsedSinceBootMillis() - ifaceInfo.tsConnectionAttemptStart;
                if (millis >= 0) {
                    perSignal.elapsedMs.update(millis);
                    changed = true;
                }
            }
        }
        PerSignal lookupSignal(Event event, int frequency) {
            finishPendingRead();
            Pair<Event, Integer> key = new Pair<>(event, frequency);
            PerSignal ans = mSignalForEventAndFrequency.get(key);
            if (ans == null) {
                ans = new PerSignal(event, frequency);
                mSignalForEventAndFrequency.put(key, ans);
            }
            return ans;
        }
        SecurityType getSecurityType() {
            finishPendingRead();
            return mSecurityType;
        }
        void setSecurityType(SecurityType securityType) {
            finishPendingRead();
            if (!Objects.equals(securityType, mSecurityType)) {
                mSecurityType = securityType;
                changed = true;
            }
        }
        void setNetworkConfigId(int networkConfigId) {
            // Not serialized, so don't need to set changed, etc.
            if (networkConfigId >= 0) {
                mNetworkConfigId = networkConfigId;
            }
        }
        AccessPoint toAccessPoint() {
            return toAccessPoint(false);
        }
        AccessPoint toAccessPoint(boolean obfuscate) {
            finishPendingRead();
            AccessPoint.Builder builder = AccessPoint.newBuilder();
            builder.setId(id);
            if (!obfuscate) {
                builder.setBssid(ByteString.copyFrom(bssid.toByteArray()));
            }
            if (mSecurityType != null) {
                builder.setSecurityType(mSecurityType);
            }
            for (PerSignal sig: mSignalForEventAndFrequency.values()) {
                builder.addEventStats(sig.toSignal());
            }
            builder.setBandwidthStatsAll(toBandwidthStatsAll(
                    bandwidthStatsValue, bandwidthStatsCount));
            return builder.build();
        }
        PerBssid merge(AccessPoint ap) {
            if (ap.hasId() && this.id != ap.getId()) {
                return this;
            }
            if (ap.hasSecurityType()) {
                SecurityType prev = ap.getSecurityType();
                if (mSecurityType == null) {
                    mSecurityType = prev;
                } else if (!mSecurityType.equals(prev)) {
                    if (mVerboseLoggingEnabled) {
                        Log.i(TAG, "ID: " + id
                                + "SecurityType changed: " + prev + " to " + mSecurityType);
                    }
                    changed = true;
                }
            }
            for (Signal signal: ap.getEventStatsList()) {
                Pair<Event, Integer> key = new Pair<>(signal.getEvent(), signal.getFrequency());
                PerSignal perSignal = mSignalForEventAndFrequency.get(key);
                if (perSignal == null) {
                    mSignalForEventAndFrequency.put(key,
                            new PerSignal(key.first, key.second).merge(signal));
                    // No need to set changed for this, since we are in sync with what's stored
                } else {
                    perSignal.merge(signal);
                    changed = true;
                }
            }
            if (ap.hasBandwidthStatsAll()) {
                mergeBandwidthStatsAll(ap.getBandwidthStatsAll(),
                        bandwidthStatsValue, bandwidthStatsCount);
            }
            return this;
        }

        /**
         * Handles (when convenient) the arrival of previously stored data.
         *
         * The response from IpMemoryStore arrives on a different thread, so we
         * defer handling it until here, when we're on our favorite thread and
         * in a good position to deal with it. We may have already collected some
         * data before now, so we need to be prepared to merge the new and old together.
         */
        void finishPendingRead() {
            final byte[] serialized = finishPendingReadBytes();
            if (serialized == null) return;
            AccessPoint ap;
            try {
                ap = AccessPoint.parseFrom(serialized);
            } catch (InvalidProtocolBufferException e) {
                Log.e(TAG, "Failed to deserialize", e);
                return;
            }
            merge(ap);
        }

        /**
         * Estimates the probability of getting internet access, based on the
         * device experience.
         *
         * @return a probability, expressed as a percentage in the range 0 to 100
         */
        public int estimatePercentInternetAvailability() {
            // Initialize counts accoring to Laplace's rule of succession
            int trials = 2;
            int successes = 1;
            // Aggregate over all of the frequencies
            for (PerSignal s : mSignalForEventAndFrequency.values()) {
                switch (s.event) {
                    case IP_CONFIGURATION_SUCCESS:
                        if (s.elapsedMs != null) {
                            trials += s.elapsedMs.count;
                        }
                        break;
                    case VALIDATION_SUCCESS:
                        if (s.elapsedMs != null) {
                            successes += s.elapsedMs.count;
                        }
                        break;
                    default:
                        break;
                }
            }
            // Note that because of roaming it is possible to count successes
            // without corresponding trials.
            return Math.min(Math.max(Math.round(successes * 100.0f / trials), 0), 100);
        }
    }

    private BandwidthStatsAll toBandwidthStatsAll(long[][][] values, int[][][] counts) {
        BandwidthStatsAll.Builder builder = BandwidthStatsAll.newBuilder();
        builder.setStats2G(toBandwidthStatsAllLink(values[0], counts[0]));
        builder.setStatsAbove2G(toBandwidthStatsAllLink(values[1], counts[1]));
        return builder.build();
    }

    private BandwidthStatsAllLink toBandwidthStatsAllLink(long[][] values, int[][] counts) {
        BandwidthStatsAllLink.Builder builder = BandwidthStatsAllLink.newBuilder();
        builder.setTx(toBandwidthStatsAllLevel(values[LINK_TX], counts[LINK_TX]));
        builder.setRx(toBandwidthStatsAllLevel(values[LINK_RX], counts[LINK_RX]));
        return builder.build();
    }

    private BandwidthStatsAllLevel toBandwidthStatsAllLevel(long[] values, int[] counts) {
        BandwidthStatsAllLevel.Builder builder = BandwidthStatsAllLevel.newBuilder();
        for (int i = 0; i < NUM_SIGNAL_LEVEL; i++) {
            builder.addLevel(toBandwidthStats(values[i], counts[i]));
        }
        return builder.build();
    }

    private BandwidthStats toBandwidthStats(long value, int count) {
        BandwidthStats.Builder builder = BandwidthStats.newBuilder();
        builder.setValue(value);
        builder.setCount(count);
        return builder.build();
    }

    private void mergeBandwidthStatsAll(BandwidthStatsAll source,
            long[][][] values, int[][][] counts) {
        if (source.hasStats2G()) {
            mergeBandwidthStatsAllLink(source.getStats2G(), values[0], counts[0]);
        }
        if (source.hasStatsAbove2G()) {
            mergeBandwidthStatsAllLink(source.getStatsAbove2G(), values[1], counts[1]);
        }
    }

    private void mergeBandwidthStatsAllLink(BandwidthStatsAllLink source,
            long[][] values, int[][] counts) {
        if (source.hasTx()) {
            mergeBandwidthStatsAllLevel(source.getTx(), values[LINK_TX], counts[LINK_TX]);
        }
        if (source.hasRx()) {
            mergeBandwidthStatsAllLevel(source.getRx(), values[LINK_RX], counts[LINK_RX]);
        }
    }

    private void mergeBandwidthStatsAllLevel(BandwidthStatsAllLevel source,
            long[] values, int[] counts) {
        int levelCnt = source.getLevelCount();
        for (int i = 0; i < levelCnt; i++) {
            BandwidthStats stats = source.getLevel(i);
            if (stats.hasValue()) {
                values[i] += stats.getValue();
            }
            if (stats.hasCount()) {
                counts[i] += stats.getCount();
            }
        }
    }

    // TODO: b/178641307 move the following parameters to config.xml
    // Array dimension : int [NUM_LINK_BAND][NUM_LINK_DIRECTION][NUM_SIGNAL_LEVEL]
    static final int[][][] LINK_BANDWIDTH_INIT_KBPS =
            {{{500, 2500, 10000, 12000, 12000}, {500, 2500, 10000, 30000, 30000}},
            {{1500, 7500, 12000, 12000, 12000}, {1500, 7500, 30000, 60000, 60000}}};
    // To be used in link bandwidth estimation, each TrafficStats poll sample needs to be above
    // the following values. Defined per signal level.
    // int [NUM_LINK_DIRECTION][NUM_SIGNAL_LEVEL]
    // Use the low Tx threshold because xDSL UL speed could be below 1Mbps.
    static final int[][] LINK_BANDWIDTH_BYTE_DELTA_THR_KBYTE =
            {{200, 300, 300, 300, 300}, {200, 500, 1000, 2000, 2000}};
    // To be used in the long term avg, each count needs to be above the following value
    static final int BANDWIDTH_STATS_COUNT_THR = 5;
    private static final int TIME_CONSTANT_SMALL_SEC = 6;
    // If RSSI changes by more than the below value, update BW filter with small time constant
    private static final int RSSI_DELTA_THR_DB = 8;
    private static final int FILTER_SCALE = 128;
    // Force weight to 0 if the elapsed time is above LARGE_TIME_DECAY_RATIO * time constant
    private static final int LARGE_TIME_DECAY_RATIO = 4;
    // Used to derive byte count threshold from avg BW
    private static final int LOW_BW_TO_AVG_BW_RATIO_NUM = 6;
    private static final int LOW_BW_TO_AVG_BW_RATIO_DEN = 8;
    // For some high speed connections, heavy DL traffic could falsely trigger UL BW update due to
    // TCP ACK and the low Tx byte count threshold. To work around the issue, skip Tx BW update if
    // Rx Bytes / Tx Bytes > RX_OVER_TX_BYTE_RATIO_MAX (heavy DL and light UL traffic)
    private static final int RX_OVER_TX_BYTE_RATIO_MAX = 5;
    // radio on time below the following value is ignored.
    static final int RADIO_ON_TIME_MIN_MS = 200;
    static final int RADIO_ON_ELAPSED_TIME_DELTA_MAX_MS = 200;
    static final int NUM_SIGNAL_LEVEL = 5;
    static final int LINK_TX = 0;
    static final int LINK_RX = 1;
    private static final int NUM_LINK_BAND = 2;
    private static final int NUM_LINK_DIRECTION = 2;
    private static final long BW_UPDATE_TIME_RESET_MS = TIME_CONSTANT_SMALL_SEC * 1000 * -10;
    private static final int MAX_ERROR_PERCENT = 100 * 100;
    private static final int EXTRA_SAMPLE_BW_FILTERING = 2;
    /**
     * A class collecting the connection and link bandwidth stats of one network or SSID.
     */
    final class PerNetwork extends MemoryStoreAccessBase {
        public int id;
        public final String ssid;
        public boolean changed;
        private int mLastRssiPoll = INVALID_RSSI;
        private int mLastTxSpeedPoll = LINK_SPEED_UNKNOWN;
        private long mLastRssiPollTimeMs = TS_NONE;
        private long mConnectionSessionStartTimeMs = TS_NONE;
        private NetworkConnectionStats mRecentStats;
        private NetworkConnectionStats mStatsCurrBuild;
        private NetworkConnectionStats mStatsPrevBuild;
        private LruList<Integer> mFrequencyList;
        // In memory keep frequency with timestamp last time available, the elapsed time since boot.
        private SparseLongArray mFreqTimestamp;
        private long mLastRxBytes;
        private long mLastTxBytes;
        private boolean mLastTrafficValid = true;
        private String mBssid = "";
        private int mSignalLevel;  // initialize to zero to handle possible race condition
        private int mBandIdx;  // initialize to zero to handle possible race condition
        private int[] mByteDeltaAccThr = new int[NUM_LINK_DIRECTION];
        private int[] mFilterKbps = new int[NUM_LINK_DIRECTION];
        private int[] mBandwidthSampleKbps = new int[NUM_LINK_DIRECTION];
        private int[] mAvgUsedKbps = new int[NUM_LINK_DIRECTION];
        private int mBandwidthUpdateRssiDbm = -1;
        private int mBandwidthUpdateBandIdx = -1;
        private boolean[] mBandwidthSampleValid = new boolean[NUM_LINK_DIRECTION];
        private long[] mBandwidthSampleValidTimeMs = new long[]{BW_UPDATE_TIME_RESET_MS,
                BW_UPDATE_TIME_RESET_MS};
        long[][][] mBandwidthStatsValue =
                new long[NUM_LINK_BAND][NUM_LINK_DIRECTION][NUM_SIGNAL_LEVEL];
        int[][][] mBandwidthStatsCount =
                new int[NUM_LINK_BAND][NUM_LINK_DIRECTION][NUM_SIGNAL_LEVEL];

        PerNetwork(String ssid) {
            super(computeHashLong(ssid, MacAddress.fromString(DEFAULT_MAC_ADDRESS), mL2KeySeed));
            this.ssid = ssid;
            this.id = idFromLong();
            this.changed = false;
            mRecentStats = new NetworkConnectionStats();
            mStatsCurrBuild = new NetworkConnectionStats();
            mStatsPrevBuild = new NetworkConnectionStats();
            mFrequencyList = new LruList<>(MAX_FREQUENCIES_PER_SSID);
            mFreqTimestamp = new SparseLongArray();
        }

        void updateEventStats(Event event, int rssi, int txSpeed, int failureReason,
                IfaceInfo ifaceInfo) {
            finishPendingRead();
            long currTimeMs = mClock.getElapsedSinceBootMillis();
            switch (event) {
                case SIGNAL_POLL:
                    mLastRssiPoll = rssi;
                    mLastRssiPollTimeMs = currTimeMs;
                    mLastTxSpeedPoll = txSpeed;
                    changed = true;
                    break;
                case CONNECTION_ATTEMPT:
                    logd(" scan rssi: " + rssi);
                    if (rssi >= mDeviceConfigFacade.getHealthMonitorMinRssiThrDbm()) {
                        mRecentStats.incrementCount(CNT_CONNECTION_ATTEMPT);
                    }
                    mConnectionSessionStartTimeMs = currTimeMs;
                    changed = true;
                    break;
                case CONNECTION_FAILURE:
                    mConnectionSessionStartTimeMs = TS_NONE;
                    if (rssi >= mDeviceConfigFacade.getHealthMonitorMinRssiThrDbm()) {
                        if (failureReason != WifiBlocklistMonitor.REASON_WRONG_PASSWORD) {
                            mRecentStats.incrementCount(CNT_CONNECTION_FAILURE);
                            mRecentStats.incrementCount(CNT_CONSECUTIVE_CONNECTION_FAILURE);
                        }
                        switch (failureReason) {
                            case WifiBlocklistMonitor.REASON_ASSOCIATION_REJECTION:
                                mRecentStats.incrementCount(CNT_ASSOCIATION_REJECTION);
                                break;
                            case WifiBlocklistMonitor.REASON_ASSOCIATION_TIMEOUT:
                                mRecentStats.incrementCount(CNT_ASSOCIATION_TIMEOUT);
                                break;
                            case WifiBlocklistMonitor.REASON_AUTHENTICATION_FAILURE:
                            case WifiBlocklistMonitor.REASON_EAP_FAILURE:
                                mRecentStats.incrementCount(CNT_AUTHENTICATION_FAILURE);
                                break;
                            case WifiBlocklistMonitor.REASON_NONLOCAL_DISCONNECT_CONNECTING:
                                mRecentStats.incrementCount(CNT_DISCONNECTION_NONLOCAL_CONNECTING);
                                break;
                            case WifiBlocklistMonitor.REASON_WRONG_PASSWORD:
                                mRecentStats.incrementCount(CNT_CONSECUTIVE_WRONG_PASSWORD_FAILURE);
                                break;
                            case WifiBlocklistMonitor.REASON_AP_UNABLE_TO_HANDLE_NEW_STA:
                            case WifiBlocklistMonitor.REASON_DHCP_FAILURE:
                            default:
                                break;
                        }
                    }
                    changed = true;
                    break;
                case IP_CONFIGURATION_SUCCESS:
                    // Reset CNT_CONSECUTIVE_CONNECTION_FAILURE since L3 is also connected
                    mRecentStats.clearCount(CNT_CONSECUTIVE_CONNECTION_FAILURE);
                    mRecentStats.clearCount(CNT_CONSECUTIVE_WRONG_PASSWORD_FAILURE);
                    changed = true;
                    logd(this.toString());
                    break;
                case WIFI_DISABLED:
                case DISCONNECTION:
                    if (mConnectionSessionStartTimeMs <= TS_NONE) {
                        return;
                    }
                    handleDisconnectionAfterConnection(ifaceInfo);
                    mConnectionSessionStartTimeMs = TS_NONE;
                    mLastRssiPollTimeMs = TS_NONE;
                    mFilterKbps[LINK_TX] = 0;
                    mFilterKbps[LINK_RX] = 0;
                    mBandwidthUpdateRssiDbm = -1;
                    mBandwidthUpdateBandIdx = -1;
                    changed = true;
                    break;
                default:
                    break;
            }
        }
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("SSID: ").append(ssid).append("\n");
            if (mLastRssiPollTimeMs != TS_NONE) {
                sb.append(" LastRssiPollTime: ");
                sb.append(mLastRssiPollTimeMs);
            }
            sb.append(" LastRssiPoll: " + mLastRssiPoll);
            sb.append(" LastTxSpeedPoll: " + mLastTxSpeedPoll);
            sb.append("\n");
            sb.append(" StatsRecent: ").append(mRecentStats).append("\n");
            sb.append(" StatsCurr: ").append(mStatsCurrBuild).append("\n");
            sb.append(" StatsPrev: ").append(mStatsPrevBuild);
            sb.append(" BandwidthStats:\n");
            for (int i = 0; i < NUM_LINK_BAND; i++) {
                for (int j = 0; j < NUM_LINK_DIRECTION; j++) {
                    sb.append(" avgKbps: ");
                    for (int k = 0; k < NUM_SIGNAL_LEVEL; k++) {
                        int avgKbps = mBandwidthStatsCount[i][j][k] == 0 ? 0 : (int)
                                (mBandwidthStatsValue[i][j][k] / mBandwidthStatsCount[i][j][k]);
                        sb.append(" " + avgKbps);
                    }
                    sb.append("\n count: ");
                    for (int k = 0; k < NUM_SIGNAL_LEVEL; k++) {
                        sb.append(" " + mBandwidthStatsCount[i][j][k]);
                    }
                    sb.append("\n");
                }
                sb.append("\n");
            }
            return sb.toString();
        }

        private void handleDisconnectionAfterConnection(IfaceInfo ifaceInfo) {
            long currTimeMs = mClock.getElapsedSinceBootMillis();
            int currSessionDurationMs = (int) (currTimeMs - mConnectionSessionStartTimeMs);
            int currSessionDurationSec = currSessionDurationMs / 1000;
            mRecentStats.accumulate(CNT_CONNECTION_DURATION_SEC, currSessionDurationSec);
            long timeSinceLastRssiPollMs = currTimeMs - mLastRssiPollTimeMs;
            boolean hasRecentRssiPoll = mLastRssiPollTimeMs > TS_NONE
                    && timeSinceLastRssiPollMs <= mDeviceConfigFacade
                    .getHealthMonitorRssiPollValidTimeMs();
            if (hasRecentRssiPoll) {
                mRecentStats.incrementCount(CNT_DISCONNECTION);
            }
            int fwAlertValidTimeMs = mDeviceConfigFacade.getHealthMonitorFwAlertValidTimeMs();
            long timeSinceLastFirmAlert = currTimeMs - ifaceInfo.firmwareAlertTimeMs;
            boolean isInvalidFwAlertTime = ifaceInfo.firmwareAlertTimeMs == TS_NONE;
            boolean disableFwAlertCheck = fwAlertValidTimeMs == -1;
            boolean passFirmwareAlertCheck = disableFwAlertCheck ? true : (isInvalidFwAlertTime
                    ? false : timeSinceLastFirmAlert < fwAlertValidTimeMs);
            boolean hasHighRssiOrHighTxSpeed =
                    mLastRssiPoll >= mDeviceConfigFacade.getHealthMonitorMinRssiThrDbm()
                    || mLastTxSpeedPoll >= HEALTH_MONITOR_COUNT_TX_SPEED_MIN_MBPS;
            if (ifaceInfo.nonlocalDisconnection && hasRecentRssiPoll
                    && isAbnormalDisconnectionReason(ifaceInfo.disconnectionReason)
                    && passFirmwareAlertCheck
                    && hasHighRssiOrHighTxSpeed) {
                mRecentStats.incrementCount(CNT_DISCONNECTION_NONLOCAL);
                if (currSessionDurationMs <= mDeviceConfigFacade
                        .getHealthMonitorShortConnectionDurationThrMs()) {
                    mRecentStats.incrementCount(CNT_SHORT_CONNECTION_NONLOCAL);
                }
            }

        }

        private boolean isAbnormalDisconnectionReason(int disconnectionReason) {
            long mask = mDeviceConfigFacade.getAbnormalDisconnectionReasonCodeMask();
            return disconnectionReason >= 0 && disconnectionReason <= 63
                    && ((mask >> disconnectionReason) & 0x1) == 0x1;
        }

        @NonNull NetworkConnectionStats getRecentStats() {
            return mRecentStats;
        }
        @NonNull NetworkConnectionStats getStatsCurrBuild() {
            return mStatsCurrBuild;
        }
        @NonNull NetworkConnectionStats getStatsPrevBuild() {
            return mStatsPrevBuild;
        }

        /**
         * Retrieve the list of frequencies seen for this network, with the most recent first.
         * @param ageInMills Max age to filter the channels.
         * @return a list of frequencies
         */
        List<Integer> getFrequencies(Long ageInMills) {
            List<Integer> results = new ArrayList<>();
            Long nowInMills = mClock.getElapsedSinceBootMillis();
            for (Integer freq : mFrequencyList.getEntries()) {
                if (nowInMills - mFreqTimestamp.get(freq, 0L) > ageInMills) {
                    continue;
                }
                results.add(freq);
            }
            return results;
        }

        /**
         * Add a frequency to the list of frequencies for this network.
         * Will evict the least recently added frequency if the cache is full.
         */
        void addFrequency(int frequency) {
            mFrequencyList.add(frequency);
            mFreqTimestamp.put(frequency, mClock.getElapsedSinceBootMillis());
        }

        /**
         * Update link bandwidth estimates based on TrafficStats byte counts and radio on time
         */
        void updateLinkBandwidth(WifiLinkLayerStats oldStats, WifiLinkLayerStats newStats,
                ExtendedWifiInfo wifiInfo, long txBytes, long rxBytes) {
            mBandwidthSampleValid[LINK_TX] = false;
            mBandwidthSampleValid[LINK_RX] = false;
            // Sometimes TrafficStats byte counts return invalid values
            // Ignore next two polls if it happens
            boolean trafficValid = txBytes >= mLastTxBytes && rxBytes >= mLastRxBytes;
            if (!mLastTrafficValid || !trafficValid) {
                mLastTrafficValid = trafficValid;
                logv("invalid traffic count tx " + txBytes + " last " + mLastTxBytes
                        + " rx " + rxBytes + " last " + mLastRxBytes);
                mLastTxBytes = txBytes;
                mLastRxBytes = rxBytes;
                return;
            }

            updateWifiInfo(wifiInfo);
            updateLinkBandwidthTxRxSample(oldStats, newStats, wifiInfo, txBytes, rxBytes);
            mLastTxBytes = txBytes;
            mLastRxBytes = rxBytes;

            updateBandwidthWithFilterApplied(LINK_TX, wifiInfo);
            updateBandwidthWithFilterApplied(LINK_RX, wifiInfo);
            mBandwidthUpdateRssiDbm = wifiInfo.getRssi();
            mBandwidthUpdateBandIdx = mBandIdx;
        }

        void updateWifiInfo(ExtendedWifiInfo wifiInfo) {
            int rssi = wifiInfo.getRssi();
            mSignalLevel = RssiUtil.calculateSignalLevel(mContext, rssi);
            mSignalLevel = Math.min(mSignalLevel, NUM_SIGNAL_LEVEL - 1);
            mBandIdx = getBandIdx(wifiInfo);
            mBssid = wifiInfo.getBSSID();
            mByteDeltaAccThr[LINK_TX] = getByteDeltaAccThr(LINK_TX);
            mByteDeltaAccThr[LINK_RX] = getByteDeltaAccThr(LINK_RX);
        }

        private void updateLinkBandwidthTxRxSample(WifiLinkLayerStats oldStats,
                WifiLinkLayerStats newStats, ExtendedWifiInfo wifiInfo,
                long txBytes, long rxBytes) {
            // oldStats is reset to null after screen off or disconnection
            if (oldStats == null || newStats == null) {
                return;
            }

            int elapsedTimeMs = (int) (newStats.timeStampInMs - oldStats.timeStampInMs);
            if (elapsedTimeMs > MAX_TRAFFIC_STATS_POLL_TIME_DELTA_MS) {
                return;
            }

            int onTimeMs = getTotalRadioOnTimeMs(newStats) - getTotalRadioOnTimeMs(oldStats);
            if (onTimeMs <= RADIO_ON_TIME_MIN_MS
                    || onTimeMs > RADIO_ON_ELAPSED_TIME_DELTA_MAX_MS + elapsedTimeMs) {
                return;
            }
            onTimeMs = Math.min(elapsedTimeMs, onTimeMs);

            long txBytesDelta = txBytes - mLastTxBytes;
            long rxBytesDelta = rxBytes - mLastRxBytes;
            int txLinkSpeedMbps = wifiInfo.getTxLinkSpeedMbps();
            int txBandwidthCapMbps =  (txLinkSpeedMbps == LINK_SPEED_UNKNOWN)
                    ? wifiInfo.getMaxSupportedTxLinkSpeedMbps() : txLinkSpeedMbps;
            if (txBytesDelta * RX_OVER_TX_BYTE_RATIO_MAX >= rxBytesDelta) {
                updateBandwidthSample(txBytesDelta, LINK_TX, onTimeMs, txBandwidthCapMbps);
            }

            int rxLinkSpeedMbps = wifiInfo.getRxLinkSpeedMbps();
            // Rx link speed is not available in many devices. In these cases, fall back to Tx link
            // speed which is available in most devices.
            int rxBandwidthCapMbps = (rxLinkSpeedMbps == LINK_SPEED_UNKNOWN)
                    ? txBandwidthCapMbps : rxLinkSpeedMbps;
            updateBandwidthSample(rxBytesDelta, LINK_RX, onTimeMs, rxBandwidthCapMbps);

            if (!mBandwidthSampleValid[LINK_RX] && !mBandwidthSampleValid[LINK_TX]) {
                return;
            }
            StringBuilder sb = new StringBuilder();
            logv(sb.append(" rssi ").append(wifiInfo.getRssi())
                    .append(" level ").append(mSignalLevel)
                    .append(" bssid ").append(wifiInfo.getBSSID())
                    .append(" freq ").append(wifiInfo.getFrequency())
                    .append(" onTimeMs ").append(onTimeMs)
                    .append(" txKB ").append(txBytesDelta / 1024)
                    .append(" rxKB ").append(rxBytesDelta / 1024)
                    .append(" txKBThr ").append(mByteDeltaAccThr[LINK_TX] / 1024)
                    .append(" rxKBThr ").append(mByteDeltaAccThr[LINK_RX] / 1024)
                    .toString());
        }

        private int getTotalRadioOnTimeMs(@NonNull WifiLinkLayerStats stats) {
            if (stats.radioStats != null && stats.radioStats.length > 0) {
                int totalRadioOnTime = 0;
                for (WifiLinkLayerStats.RadioStat stat : stats.radioStats) {
                    totalRadioOnTime += stat.on_time;
                }
                return totalRadioOnTime;
            }
            return stats.on_time;
        }

        private int getBandIdx(ExtendedWifiInfo wifiInfo) {
            return ScanResult.is24GHz(wifiInfo.getFrequency()) ? 0 : 1;
        }

        private void updateBandwidthSample(long bytesDelta, int link, int onTimeMs,
                int bandwidthCapMbps) {
            if (bytesDelta < mByteDeltaAccThr[link]) {
                return;
            }
            long speedKbps = bytesDelta / onTimeMs * 8;
            if (speedKbps > (bandwidthCapMbps * 1000)) {
                return;
            }
            int linkBandwidthKbps = (int) speedKbps;
            changed = true;
            mBandwidthSampleValid[link] = true;
            mBandwidthSampleKbps[link] = linkBandwidthKbps;
            // Update SSID level stats
            mBandwidthStatsValue[mBandIdx][link][mSignalLevel] += linkBandwidthKbps;
            mBandwidthStatsCount[mBandIdx][link][mSignalLevel]++;
            // Update BSSID level stats
            PerBssid perBssid = lookupBssid(ssid, mBssid);
            if (perBssid != mPlaceholderPerBssid) {
                perBssid.changed = true;
                perBssid.bandwidthStatsValue[mBandIdx][link][mSignalLevel] += linkBandwidthKbps;
                perBssid.bandwidthStatsCount[mBandIdx][link][mSignalLevel]++;
            }
        }

        private int getByteDeltaAccThr(int link) {
            int maxTimeDeltaMs = mWifiGlobals.getPollRssiIntervalMillis();
            int lowBytes = calculateByteCountThreshold(getAvgUsedLinkBandwidthKbps(link),
                    maxTimeDeltaMs);
            // Start with a predefined value
            int deltaAccThr = LINK_BANDWIDTH_BYTE_DELTA_THR_KBYTE[link][mSignalLevel] * 1024;
            if (lowBytes > 0) {
                // Raise the threshold if the avg usage BW is high
                deltaAccThr = Math.max(lowBytes, deltaAccThr);
                deltaAccThr = Math.min(deltaAccThr, mDeviceConfigFacade
                        .getTrafficStatsThresholdMaxKbyte() * 1024);
            }
            return deltaAccThr;
        }

        private void initBandwidthFilter(ExtendedWifiInfo wifiInfo) {
            updateWifiInfo(wifiInfo);
            for (int link = 0; link < NUM_LINK_DIRECTION; link++) {
                mFilterKbps[link] = getAvgLinkBandwidthKbps(link);
            }
        }

        private void updateBandwidthWithFilterApplied(int link, ExtendedWifiInfo wifiInfo) {
            int avgKbps = getAvgLinkBandwidthKbps(link);
            // Feed the filter with the long term avg if there is no valid BW sample so that filter
            // will gradually converge the long term avg.
            int filterInKbps = mBandwidthSampleValid[link] ? mBandwidthSampleKbps[link] : avgKbps;

            long currTimeMs = mClock.getElapsedSinceBootMillis();
            int timeDeltaSec = (int) ((currTimeMs - mBandwidthSampleValidTimeMs[link]) / 1000);

            // If the operation condition changes since the last valid sample or the current sample
            // has higher BW, use a faster filter. Otherwise, use a slow filter
            int timeConstantSec;
            if (Math.abs(mBandwidthUpdateRssiDbm - wifiInfo.getRssi()) > RSSI_DELTA_THR_DB
                    || (mBandwidthSampleValid[link] && mBandwidthSampleKbps[link] > avgKbps)
                    || mBandwidthUpdateBandIdx != mBandIdx) {
                timeConstantSec = TIME_CONSTANT_SMALL_SEC;
            } else {
                timeConstantSec = mDeviceConfigFacade.getBandwidthEstimatorLargeTimeConstantSec();
            }
            // Update timestamp for next iteration
            if (mBandwidthSampleValid[link]) {
                mBandwidthSampleValidTimeMs[link] = currTimeMs;
            }

            if (filterInKbps == mFilterKbps[link]) {
                return;
            }
            int alpha = timeDeltaSec > LARGE_TIME_DECAY_RATIO * timeConstantSec ? 0
                    : (int) (FILTER_SCALE * Math.exp(-1.0 * timeDeltaSec / timeConstantSec));

            if (alpha == 0) {
                mFilterKbps[link] = filterInKbps;
                return;
            }
            long filterOutKbps = (long) mFilterKbps[link] * alpha
                    + filterInKbps * FILTER_SCALE - filterInKbps * alpha;
            filterOutKbps = filterOutKbps / FILTER_SCALE;
            mFilterKbps[link] = (int) Math.min(filterOutKbps, Integer.MAX_VALUE);
            StringBuilder sb = new StringBuilder();
            logd(sb.append(link)
                    .append(" lastSampleWeight=").append(alpha)
                    .append("/").append(FILTER_SCALE)
                    .append(" filterInKbps=").append(filterInKbps)
                    .append(" avgKbps=").append(avgKbps)
                    .append(" filterOutKbps=").append(mFilterKbps[link])
                    .toString());
        }

        private int getAvgLinkBandwidthKbps(int link) {
            mAvgUsedKbps[link] = getAvgUsedLinkBandwidthKbps(link);
            if (mAvgUsedKbps[link] > 0) {
                return mAvgUsedKbps[link];
            }

            int avgBwAdjSignalKbps = getAvgUsedBandwidthAdjacentThreeLevelKbps(link);
            if (avgBwAdjSignalKbps > 0) {
                return avgBwAdjSignalKbps;
            }

            // Fall back to a cold-start value
            return LINK_BANDWIDTH_INIT_KBPS[mBandIdx][link][mSignalLevel];
        }

        private int getAvgUsedLinkBandwidthKbps(int link) {
            // Check if current BSSID/signal level has enough count
            PerBssid perBssid = lookupBssid(ssid, mBssid);
            int count = perBssid.bandwidthStatsCount[mBandIdx][link][mSignalLevel];
            long value = perBssid.bandwidthStatsValue[mBandIdx][link][mSignalLevel];
            if (count >= BANDWIDTH_STATS_COUNT_THR) {
                return (int) (value / count);
            }

            // Check if current SSID/band/signal level has enough count
            count = mBandwidthStatsCount[mBandIdx][link][mSignalLevel];
            value = mBandwidthStatsValue[mBandIdx][link][mSignalLevel];
            if (count >= BANDWIDTH_STATS_COUNT_THR) {
                return (int) (value / count);
            }

            return -1;
        }

        private int getAvgUsedBandwidthAdjacentThreeLevelKbps(int link) {
            int count = 0;
            long value = 0;
            for (int i = -1; i <= 1; i++) {
                int currLevel = mSignalLevel + i;
                if (currLevel < 0 || currLevel >= NUM_SIGNAL_LEVEL) {
                    continue;
                }
                count += mBandwidthStatsCount[mBandIdx][link][currLevel];
                value += mBandwidthStatsValue[mBandIdx][link][currLevel];
            }
            if (count >= BANDWIDTH_STATS_COUNT_THR) {
                return (int) (value / count);
            }

            return -1;
        }

        // Calculate a byte count threshold for the given avg BW and observation window size
        private int calculateByteCountThreshold(int avgBwKbps, int durationMs) {
            long avgBytes = (long) avgBwKbps / 8 * durationMs;
            long result = avgBytes * LOW_BW_TO_AVG_BW_RATIO_NUM / LOW_BW_TO_AVG_BW_RATIO_DEN;
            return (int) Math.min(result, Integer.MAX_VALUE);
        }

        /**
         * Get the latest TrafficStats based end-to-end Tx link bandwidth estimation in Kbps
         */
        public int getTxLinkBandwidthKbps() {
            return (mFilterKbps[LINK_TX] > 0) ? mFilterKbps[LINK_TX]
                    : getAvgLinkBandwidthKbps(LINK_TX);
        }

        /**
         * Get the latest TrafficStats based end-to-end Rx link bandwidth estimation in Kbps
         */
        public int getRxLinkBandwidthKbps() {
            return (mFilterKbps[LINK_RX] > 0) ? mFilterKbps[LINK_RX]
                    : getAvgLinkBandwidthKbps(LINK_RX);
        }

        /**
         * Update Bandwidth metrics with the latest reported bandwidth and L2 BW values
         */
        public void updateBwMetrics(int[] reportedKbps, int[] l2Kbps) {
            for (int link = 0; link < NUM_LINK_DIRECTION; link++) {
                calculateError(link, reportedKbps[link], l2Kbps[link]);
            }
        }

        private void calculateError(int link, int reportedKbps, int l2Kbps) {
            if (mBandwidthStatsCount[mBandIdx][link][mSignalLevel] < (BANDWIDTH_STATS_COUNT_THR
                    + EXTRA_SAMPLE_BW_FILTERING) || !mBandwidthSampleValid[link]
                    || mAvgUsedKbps[link] <= 0) {
                return;
            }
            int bwSampleKbps = mBandwidthSampleKbps[link];
            int bwEstExtErrPercent = calculateErrorPercent(reportedKbps, bwSampleKbps);
            int bwEstIntErrPercent = calculateErrorPercent(mFilterKbps[link], bwSampleKbps);
            int l2ErrPercent = calculateErrorPercent(l2Kbps, bwSampleKbps);
            mBwEstErrorAccPercent[mBandIdx][link][mSignalLevel] += Math.abs(bwEstExtErrPercent);
            mL2ErrorAccPercent[mBandIdx][link][mSignalLevel] += Math.abs(l2ErrPercent);
            mBwEstValue[mBandIdx][link][mSignalLevel] += bwSampleKbps;
            mBwEstCount[mBandIdx][link][mSignalLevel]++;
            StringBuilder sb = new StringBuilder();
            logv(sb.append(link)
                    .append(" sampKbps ").append(bwSampleKbps)
                    .append(" filtKbps ").append(mFilterKbps[link])
                    .append(" reportedKbps ").append(reportedKbps)
                    .append(" avgUsedKbps ").append(mAvgUsedKbps[link])
                    .append(" l2Kbps ").append(l2Kbps)
                    .append(" intErrPercent ").append(bwEstIntErrPercent)
                    .append(" extErrPercent ").append(bwEstExtErrPercent)
                    .append(" l2ErrPercent ").append(l2ErrPercent)
                    .toString());
        }

        private int calculateErrorPercent(int inKbps, int bwSampleKbps) {
            long errorPercent = 100L * (inKbps - bwSampleKbps) / bwSampleKbps;
            return (int) Math.max(-MAX_ERROR_PERCENT, Math.min(errorPercent, MAX_ERROR_PERCENT));
        }

        /**
        /* Detect a significant failure stats change with historical data
        /* or high failure stats without historical data.
        /* @return 0 if recentStats doesn't have sufficient data
         *         1 if recentStats has sufficient data while statsPrevBuild doesn't
         *         2 if recentStats and statsPrevBuild have sufficient data
         */
        int dailyDetection(FailureStats statsDec, FailureStats statsInc, FailureStats statsHigh) {
            finishPendingRead();
            dailyDetectionDisconnectionEvent(statsDec, statsInc, statsHigh);
            return dailyDetectionConnectionEvent(statsDec, statsInc, statsHigh);
        }

        private int dailyDetectionConnectionEvent(FailureStats statsDec, FailureStats statsInc,
                FailureStats statsHigh) {
            // Skip daily detection if recentStats is not sufficient
            if (!isRecentConnectionStatsSufficient()) return INSUFFICIENT_RECENT_STATS;
            if (mStatsPrevBuild.getCount(CNT_CONNECTION_ATTEMPT)
                    < mDeviceConfigFacade.getHealthMonitorMinNumConnectionAttempt()) {
                // don't have enough historical data,
                // so only detect high failure stats without relying on mStatsPrevBuild.
                recentStatsHighDetectionConnection(statsHigh);
                return SUFFICIENT_RECENT_STATS_ONLY;
            } else {
                // mStatsPrevBuild has enough updates,
                // detect improvement or degradation
                statsDeltaDetectionConnection(statsDec, statsInc);
                return SUFFICIENT_RECENT_PREV_STATS;
            }
        }

        private void dailyDetectionDisconnectionEvent(FailureStats statsDec, FailureStats statsInc,
                FailureStats statsHigh) {
            // Skip daily detection if recentStats is not sufficient
            int minConnectAttempt = mDeviceConfigFacade.getHealthMonitorMinNumConnectionAttempt();
            if (mRecentStats.getCount(CNT_CONNECTION_ATTEMPT) < minConnectAttempt) {
                return;
            }
            if (mStatsPrevBuild.getCount(CNT_CONNECTION_ATTEMPT) < minConnectAttempt) {
                recentStatsHighDetectionDisconnection(statsHigh);
            } else {
                statsDeltaDetectionDisconnection(statsDec, statsInc);
            }
        }

        private void statsDeltaDetectionConnection(FailureStats statsDec,
                FailureStats statsInc) {
            statsDeltaDetection(statsDec, statsInc, CNT_CONNECTION_FAILURE,
                    REASON_CONNECTION_FAILURE,
                    mDeviceConfigFacade.getConnectionFailureCountMin(),
                    CNT_CONNECTION_ATTEMPT);
            statsDeltaDetection(statsDec, statsInc, CNT_DISCONNECTION_NONLOCAL_CONNECTING,
                    REASON_CONNECTION_FAILURE_DISCONNECTION,
                    mDeviceConfigFacade.getConnectionFailureDisconnectionCountMin(),
                    CNT_CONNECTION_ATTEMPT);
            statsDeltaDetection(statsDec, statsInc, CNT_AUTHENTICATION_FAILURE,
                    REASON_AUTH_FAILURE,
                    mDeviceConfigFacade.getAuthFailureCountMin(),
                    CNT_CONNECTION_ATTEMPT);
            statsDeltaDetection(statsDec, statsInc, CNT_ASSOCIATION_REJECTION,
                    REASON_ASSOC_REJECTION,
                    mDeviceConfigFacade.getAssocRejectionCountMin(),
                    CNT_CONNECTION_ATTEMPT);
            statsDeltaDetection(statsDec, statsInc, CNT_ASSOCIATION_TIMEOUT,
                    REASON_ASSOC_TIMEOUT,
                    mDeviceConfigFacade.getAssocTimeoutCountMin(),
                    CNT_CONNECTION_ATTEMPT);
        }

        private void recentStatsHighDetectionConnection(FailureStats statsHigh) {
            recentStatsHighDetection(statsHigh, CNT_CONNECTION_FAILURE,
                    REASON_CONNECTION_FAILURE,
                    mDeviceConfigFacade.getConnectionFailureHighThrPercent(),
                    mDeviceConfigFacade.getConnectionFailureCountMin(),
                    CNT_CONNECTION_ATTEMPT);
            recentStatsHighDetection(statsHigh, CNT_DISCONNECTION_NONLOCAL_CONNECTING,
                    REASON_CONNECTION_FAILURE_DISCONNECTION,
                    mDeviceConfigFacade.getConnectionFailureDisconnectionHighThrPercent(),
                    mDeviceConfigFacade.getConnectionFailureDisconnectionCountMin(),
                    CNT_CONNECTION_ATTEMPT);
            recentStatsHighDetection(statsHigh, CNT_AUTHENTICATION_FAILURE,
                    REASON_AUTH_FAILURE,
                    mDeviceConfigFacade.getAuthFailureHighThrPercent(),
                    mDeviceConfigFacade.getAuthFailureCountMin(),
                    CNT_CONNECTION_ATTEMPT);
            recentStatsHighDetection(statsHigh, CNT_ASSOCIATION_REJECTION,
                    REASON_ASSOC_REJECTION,
                    mDeviceConfigFacade.getAssocRejectionHighThrPercent(),
                    mDeviceConfigFacade.getAssocRejectionCountMin(),
                    CNT_CONNECTION_ATTEMPT);
            recentStatsHighDetection(statsHigh, CNT_ASSOCIATION_TIMEOUT,
                    REASON_ASSOC_TIMEOUT,
                    mDeviceConfigFacade.getAssocTimeoutHighThrPercent(),
                    mDeviceConfigFacade.getAssocTimeoutCountMin(),
                    CNT_CONNECTION_ATTEMPT);
        }

        private void statsDeltaDetectionDisconnection(FailureStats statsDec,
                FailureStats statsInc) {
            statsDeltaDetection(statsDec, statsInc, CNT_SHORT_CONNECTION_NONLOCAL,
                    REASON_SHORT_CONNECTION_NONLOCAL,
                    mDeviceConfigFacade.getShortConnectionNonlocalCountMin(),
                    CNT_CONNECTION_ATTEMPT);
            statsDeltaDetection(statsDec, statsInc, CNT_DISCONNECTION_NONLOCAL,
                    REASON_DISCONNECTION_NONLOCAL,
                    mDeviceConfigFacade.getDisconnectionNonlocalCountMin(),
                    CNT_CONNECTION_ATTEMPT);
        }

        private void recentStatsHighDetectionDisconnection(FailureStats statsHigh) {
            recentStatsHighDetection(statsHigh, CNT_SHORT_CONNECTION_NONLOCAL,
                    REASON_SHORT_CONNECTION_NONLOCAL,
                    mDeviceConfigFacade.getShortConnectionNonlocalHighThrPercent(),
                    mDeviceConfigFacade.getShortConnectionNonlocalCountMin(),
                    CNT_DISCONNECTION);
            recentStatsHighDetection(statsHigh, CNT_DISCONNECTION_NONLOCAL,
                    REASON_DISCONNECTION_NONLOCAL,
                    mDeviceConfigFacade.getDisconnectionNonlocalHighThrPercent(),
                    mDeviceConfigFacade.getDisconnectionNonlocalCountMin(),
                    CNT_DISCONNECTION);
        }

        private boolean statsDeltaDetection(FailureStats statsDec,
                FailureStats statsInc, int countCode, int reasonCode,
                int minCount, int refCountCode) {
            if (isRatioAboveThreshold(mRecentStats, mStatsPrevBuild, countCode, refCountCode)
                    && mRecentStats.getCount(countCode) >= minCount) {
                statsInc.incrementCount(reasonCode);
                return true;
            }

            if (isRatioAboveThreshold(mStatsPrevBuild, mRecentStats, countCode, refCountCode)
                    && mStatsPrevBuild.getCount(countCode) >= minCount) {
                statsDec.incrementCount(reasonCode);
                return true;
            }
            return false;
        }

        private boolean recentStatsHighDetection(FailureStats statsHigh, int countCode,
                int reasonCode, int highThresholdPercent, int minCount, int refCountCode) {
            if (isHighPercentageAndEnoughCount(mRecentStats, countCode, reasonCode,
                    highThresholdPercent, minCount, refCountCode)) {
                statsHigh.incrementCount(reasonCode);
                return true;
            }
            return false;
        }

        private boolean isRatioAboveThreshold(NetworkConnectionStats stats1,
                NetworkConnectionStats stats2,
                @ConnectionCountCode int countCode, int refCountCode) {
            // Also with Laplace's rule of succession discussed above
            // R1 = (stats1(countCode) + 1) / (stats1(refCountCode) + 2)
            // R2 = (stats2(countCode) + 1) / (stats2(refCountCode) + 2)
            // Check R1 / R2 >= ratioThr
            return ((stats1.getCount(countCode) + 1) * (stats2.getCount(refCountCode) + 2)
                    * mDeviceConfigFacade.HEALTH_MONITOR_RATIO_THR_DENOMINATOR)
                    >= ((stats1.getCount(refCountCode) + 2) * (stats2.getCount(countCode) + 1)
                    * mDeviceConfigFacade.getHealthMonitorRatioThrNumerator());
        }

        private boolean isRecentConnectionStatsSufficient() {
            return (mRecentStats.getCount(CNT_CONNECTION_ATTEMPT)
                >= mDeviceConfigFacade.getHealthMonitorMinNumConnectionAttempt());
        }

        // Update StatsCurrBuild with recentStats and clear recentStats
        void updateAfterDailyDetection() {
            // Skip update if recentStats is not sufficient since daily detection is also skipped
            if (!isRecentConnectionStatsSufficient()) return;
            mStatsCurrBuild.accumulateAll(mRecentStats);
            mRecentStats.clear();
            changed = true;
        }

        // Refresh StatsPrevBuild with StatsCurrBuild which is cleared afterwards
        void updateAfterSwBuildChange() {
            finishPendingRead();
            mStatsPrevBuild.copy(mStatsCurrBuild);
            mRecentStats.clear();
            mStatsCurrBuild.clear();
            changed = true;
        }

        NetworkStats toNetworkStats() {
            finishPendingRead();
            NetworkStats.Builder builder = NetworkStats.newBuilder();
            builder.setId(id);
            builder.setRecentStats(toConnectionStats(mRecentStats));
            builder.setStatsCurrBuild(toConnectionStats(mStatsCurrBuild));
            builder.setStatsPrevBuild(toConnectionStats(mStatsPrevBuild));
            if (mFrequencyList.size() > 0) {
                builder.addAllFrequencies(mFrequencyList.getEntries());
            }
            builder.setBandwidthStatsAll(toBandwidthStatsAll(
                    mBandwidthStatsValue, mBandwidthStatsCount));
            return builder.build();
        }

        private ConnectionStats toConnectionStats(NetworkConnectionStats stats) {
            ConnectionStats.Builder builder = ConnectionStats.newBuilder();
            builder.setNumConnectionAttempt(stats.getCount(CNT_CONNECTION_ATTEMPT));
            builder.setNumConnectionFailure(stats.getCount(CNT_CONNECTION_FAILURE));
            builder.setConnectionDurationSec(stats.getCount(CNT_CONNECTION_DURATION_SEC));
            builder.setNumDisconnectionNonlocal(stats.getCount(CNT_DISCONNECTION_NONLOCAL));
            builder.setNumDisconnection(stats.getCount(CNT_DISCONNECTION));
            builder.setNumShortConnectionNonlocal(stats.getCount(CNT_SHORT_CONNECTION_NONLOCAL));
            builder.setNumAssociationRejection(stats.getCount(CNT_ASSOCIATION_REJECTION));
            builder.setNumAssociationTimeout(stats.getCount(CNT_ASSOCIATION_TIMEOUT));
            builder.setNumAuthenticationFailure(stats.getCount(CNT_AUTHENTICATION_FAILURE));
            builder.setNumDisconnectionNonlocalConnecting(
                    stats.getCount(CNT_DISCONNECTION_NONLOCAL_CONNECTING));
            return builder.build();
        }

        void finishPendingRead() {
            final byte[] serialized = finishPendingReadBytes();
            if (serialized == null) return;
            NetworkStats ns;
            try {
                ns = NetworkStats.parseFrom(serialized);
            } catch (InvalidProtocolBufferException e) {
                Log.e(TAG, "Failed to deserialize", e);
                return;
            }
            mergeNetworkStatsFromMemory(ns);
            changed = true;
        }

        PerNetwork mergeNetworkStatsFromMemory(@NonNull NetworkStats ns) {
            if (ns.hasId() && this.id != ns.getId()) {
                return this;
            }
            if (ns.hasRecentStats()) {
                ConnectionStats recentStats = ns.getRecentStats();
                mergeConnectionStats(recentStats, mRecentStats);
            }
            if (ns.hasStatsCurrBuild()) {
                ConnectionStats statsCurr = ns.getStatsCurrBuild();
                mStatsCurrBuild.clear();
                mergeConnectionStats(statsCurr, mStatsCurrBuild);
            }
            if (ns.hasStatsPrevBuild()) {
                ConnectionStats statsPrev = ns.getStatsPrevBuild();
                mStatsPrevBuild.clear();
                mergeConnectionStats(statsPrev, mStatsPrevBuild);
            }
            if (ns.getFrequenciesList().size() > 0) {
                // This merge assumes that whatever data is in memory is more recent that what's
                // in store
                List<Integer> mergedFrequencyList = mFrequencyList.getEntries();
                mergedFrequencyList.addAll(ns.getFrequenciesList());
                mFrequencyList = new LruList<>(MAX_FREQUENCIES_PER_SSID);
                for (int i = mergedFrequencyList.size() - 1; i >= 0; i--) {
                    mFrequencyList.add(mergedFrequencyList.get(i));
                }
            }
            if (ns.hasBandwidthStatsAll()) {
                mergeBandwidthStatsAll(ns.getBandwidthStatsAll(),
                        mBandwidthStatsValue, mBandwidthStatsCount);
            }
            return this;
        }

        private void mergeConnectionStats(ConnectionStats source, NetworkConnectionStats target) {
            if (source.hasNumConnectionAttempt()) {
                target.accumulate(CNT_CONNECTION_ATTEMPT, source.getNumConnectionAttempt());
            }
            if (source.hasNumConnectionFailure()) {
                target.accumulate(CNT_CONNECTION_FAILURE, source.getNumConnectionFailure());
            }
            if (source.hasConnectionDurationSec()) {
                target.accumulate(CNT_CONNECTION_DURATION_SEC, source.getConnectionDurationSec());
            }
            if (source.hasNumDisconnectionNonlocal()) {
                target.accumulate(CNT_DISCONNECTION_NONLOCAL, source.getNumDisconnectionNonlocal());
            }
            if (source.hasNumDisconnection()) {
                target.accumulate(CNT_DISCONNECTION, source.getNumDisconnection());
            }
            if (source.hasNumShortConnectionNonlocal()) {
                target.accumulate(CNT_SHORT_CONNECTION_NONLOCAL,
                        source.getNumShortConnectionNonlocal());
            }
            if (source.hasNumAssociationRejection()) {
                target.accumulate(CNT_ASSOCIATION_REJECTION, source.getNumAssociationRejection());
            }
            if (source.hasNumAssociationTimeout()) {
                target.accumulate(CNT_ASSOCIATION_TIMEOUT, source.getNumAssociationTimeout());
            }
            if (source.hasNumAuthenticationFailure()) {
                target.accumulate(CNT_AUTHENTICATION_FAILURE, source.getNumAuthenticationFailure());
            }
            if (source.hasNumDisconnectionNonlocalConnecting()) {
                target.accumulate(CNT_DISCONNECTION_NONLOCAL_CONNECTING,
                        source.getNumDisconnectionNonlocalConnecting());
            }
        }
    }

    // Codes for various connection related counts
    public static final int CNT_INVALID = -1;
    public static final int CNT_CONNECTION_ATTEMPT = 0;
    public static final int CNT_CONNECTION_FAILURE = 1;
    public static final int CNT_CONNECTION_DURATION_SEC = 2;
    public static final int CNT_ASSOCIATION_REJECTION = 3;
    public static final int CNT_ASSOCIATION_TIMEOUT = 4;
    public static final int CNT_AUTHENTICATION_FAILURE = 5;
    public static final int CNT_SHORT_CONNECTION_NONLOCAL = 6;
    public static final int CNT_DISCONNECTION_NONLOCAL = 7;
    public static final int CNT_DISCONNECTION = 8;
    public static final int CNT_CONSECUTIVE_CONNECTION_FAILURE = 9;
    public static final int CNT_DISCONNECTION_NONLOCAL_CONNECTING = 10;
    public static final int CNT_CONSECUTIVE_WRONG_PASSWORD_FAILURE = 11;
    // Constant being used to keep track of how many counter there are.
    public static final int NUMBER_CONNECTION_CNT_CODE = 12;
    private static final String[] CONNECTION_CNT_NAME = {
        " ConnectAttempt: ",
        " ConnectFailure: ",
        " ConnectDurSec: ",
        " AssocRej: ",
        " AssocTimeout: ",
        " AuthFailure: ",
        " ShortDiscNonlocal: ",
        " DisconnectNonlocal: ",
        " Disconnect: ",
        " ConsecutiveConnectFailure: ",
        " ConnectFailureDiscon: ",
        " ConsecutiveWrongPassword: "
    };

    @IntDef(prefix = { "CNT_" }, value = {
        CNT_CONNECTION_ATTEMPT,
        CNT_CONNECTION_FAILURE,
        CNT_CONNECTION_DURATION_SEC,
        CNT_ASSOCIATION_REJECTION,
        CNT_ASSOCIATION_TIMEOUT,
        CNT_AUTHENTICATION_FAILURE,
        CNT_SHORT_CONNECTION_NONLOCAL,
        CNT_DISCONNECTION_NONLOCAL,
        CNT_DISCONNECTION,
        CNT_CONSECUTIVE_CONNECTION_FAILURE,
        CNT_DISCONNECTION_NONLOCAL_CONNECTING,
        CNT_CONSECUTIVE_WRONG_PASSWORD_FAILURE
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface ConnectionCountCode {}

    /**
     * A class maintaining the connection related statistics of a Wifi network.
     */
    public static class NetworkConnectionStats {
        private final int[] mCount = new int[NUMBER_CONNECTION_CNT_CODE];
        private int mRecentCountCode = CNT_INVALID;
        /**
         * Copy all values
         * @param src is the source of copy
         */
        public void copy(NetworkConnectionStats src) {
            for (int i = 0; i < NUMBER_CONNECTION_CNT_CODE; i++) {
                mCount[i] = src.getCount(i);
            }
            mRecentCountCode = src.mRecentCountCode;
        }

        /**
         * Clear all counters
         */
        public void clear() {
            for (int i = 0; i < NUMBER_CONNECTION_CNT_CODE; i++) {
                mCount[i] = 0;
            }
            mRecentCountCode = CNT_INVALID;
        }

        /**
         * Get counter value
         * @param countCode is the selected counter
         * @return the value of selected counter
         */
        public int getCount(@ConnectionCountCode int countCode) {
            return mCount[countCode];
        }

        /**
         * Clear counter value
         * @param countCode is the selected counter to be cleared
         */
        public void clearCount(@ConnectionCountCode int countCode) {
            mCount[countCode] = 0;
        }

        /**
         * Increment count value by 1
         * @param countCode is the selected counter
         */
        public void incrementCount(@ConnectionCountCode int countCode) {
            mCount[countCode]++;
            mRecentCountCode = countCode;
        }

        /**
         * Got the recent incremented count code
         */
        public int getRecentCountCode() {
            return mRecentCountCode;
        }

        /**
         * Decrement count value by 1
         * @param countCode is the selected counter
         */
        public void decrementCount(@ConnectionCountCode int countCode) {
            mCount[countCode]--;
        }

        /**
         * Add and accumulate the selected counter
         * @param countCode is the selected counter
         * @param cnt is the value to be added to the counter
         */
        public void accumulate(@ConnectionCountCode int countCode, int cnt) {
            mCount[countCode] += cnt;
        }

        /**
         * Accumulate daily stats to historical data
         * @param recentStats are the raw daily counts
         */
        public void accumulateAll(NetworkConnectionStats recentStats) {
            // 32-bit counter in second can support connection duration up to 68 years.
            // Similarly 32-bit counter can support up to continuous connection attempt
            // up to 68 years with one attempt per second.
            for (int i = 0; i < NUMBER_CONNECTION_CNT_CODE; i++) {
                mCount[i] += recentStats.getCount(i);
            }
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < NUMBER_CONNECTION_CNT_CODE; i++) {
                sb.append(CONNECTION_CNT_NAME[i]);
                sb.append(mCount[i]);
            }
            return sb.toString();
        }
    }

    /**
     * A base class dealing with common operations of MemoryStore.
     */
    public static class MemoryStoreAccessBase {
        private final String mL2Key;
        private final long mHash;
        private static final String TAG = "WifiMemoryStoreAccessBase";
        private final AtomicReference<byte[]> mPendingReadFromStore = new AtomicReference<>();
        MemoryStoreAccessBase(long hash) {
            mHash = hash;
            mL2Key = l2KeyFromLong();
        }
        String getL2Key() {
            return mL2Key;
        }

        private String l2KeyFromLong() {
            return "W" + Long.toHexString(mHash);
        }

        /**
         * Callback function when MemoryStore read is done
         * @param serialized is the readback value
         */
        void readBackListener(byte[] serialized) {
            if (serialized == null) return;
            byte[] old = mPendingReadFromStore.getAndSet(serialized);
            if (old != null) {
                Log.e(TAG, "More answers than we expected!");
            }
        }

        /**
         * Handles (when convenient) the arrival of previously stored data.
         *
         * The response from IpMemoryStore arrives on a different thread, so we
         * defer handling it until here, when we're on our favorite thread and
         * in a good position to deal with it. We may have already collected some
         * data before now, so we need to be prepared to merge the new and old together.
         */
        byte[] finishPendingReadBytes() {
            return mPendingReadFromStore.getAndSet(null);
        }

        int idFromLong() {
            return (int) mHash & 0x7fffffff;
        }
    }

    private void logd(String string) {
        if (mVerboseLoggingEnabled) {
            Log.d(TAG, string, null);
        }
    }

    private void logv(String string) {
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, string, null);
        }
        mLocalLog.log(string);
    }

    // Returned by lookupBssid when the BSSID is not available,
    // for instance when we are not associated.
    private final PerBssid mPlaceholderPerBssid;

    private final Map<MacAddress, PerBssid> mApForBssid = new ArrayMap<>();
    private int mApForBssidTargetSize = TARGET_IN_MEMORY_ENTRIES;
    private int mApForBssidReferenced = 0;

    // TODO should be private, but WifiCandidates needs it
    @NonNull PerBssid lookupBssid(String ssid, String bssid) {
        MacAddress mac;
        if (ssid == null || WifiManager.UNKNOWN_SSID.equals(ssid) || bssid == null) {
            return mPlaceholderPerBssid;
        }
        try {
            mac = MacAddress.fromString(bssid);
        } catch (IllegalArgumentException e) {
            return mPlaceholderPerBssid;
        }
        if (mac.equals(mPlaceholderPerBssid.bssid)) {
            return mPlaceholderPerBssid;
        }
        PerBssid ans = mApForBssid.get(mac);
        if (ans == null || !ans.ssid.equals(ssid)) {
            ans = new PerBssid(ssid, mac);
            PerBssid old = mApForBssid.put(mac, ans);
            if (old != null) {
                Log.i(TAG, "Discarding stats for score card (ssid changed) ID: " + old.id);
                if (old.referenced) mApForBssidReferenced--;
            }
            requestReadBssid(ans);
        }
        if (!ans.referenced) {
            ans.referenced = true;
            mApForBssidReferenced++;
            clean();
        }
        return ans;
    }

    private void requestReadBssid(final PerBssid perBssid) {
        if (mMemoryStore != null) {
            mMemoryStore.read(perBssid.getL2Key(), PER_BSSID_DATA_NAME,
                    (value) -> perBssid.readBackListener(value));
        }
    }

    private void requestReadForAllChanged() {
        for (PerBssid perBssid : mApForBssid.values()) {
            if (perBssid.changed) {
                requestReadBssid(perBssid);
            }
        }
    }

    // Returned by lookupNetwork when the network is not available,
    // for instance when we are not associated.
    private final PerNetwork mPlaceholderPerNetwork;
    private final Map<String, PerNetwork> mApForNetwork = new ArrayMap<>();
    @NonNull PerNetwork lookupNetwork(String ssid) {
        if (ssid == null || WifiManager.UNKNOWN_SSID.equals(ssid)) {
            return mPlaceholderPerNetwork;
        }

        PerNetwork ans = mApForNetwork.get(ssid);
        if (ans == null) {
            ans = new PerNetwork(ssid);
            mApForNetwork.put(ssid, ans);
            requestReadNetwork(ans);
        }
        return ans;
    }

    /**
     * Remove network from cache and memory store
     * @param ssid is the network SSID
     */
    public void removeNetwork(String ssid) {
        if (ssid == null || WifiManager.UNKNOWN_SSID.equals(ssid)) {
            return;
        }
        mApForNetwork.remove(ssid);
        mApForBssid.entrySet().removeIf(entry -> ssid.equals(entry.getValue().ssid));
        if (mMemoryStore == null) return;
        mMemoryStore.removeCluster(groupHintFromSsid(ssid));
    }

    void requestReadNetwork(final PerNetwork perNetwork) {
        if (mMemoryStore != null) {
            mMemoryStore.read(perNetwork.getL2Key(), PER_NETWORK_DATA_NAME,
                    (value) -> perNetwork.readBackListener(value));
        }
    }

    /**
     * Issues write requests for all changed entries.
     *
     * This should be called from time to time to save the state to persistent
     * storage. Since we always check internal state first, this does not need
     * to be called very often, but it should be called before shutdown.
     *
     * @returns number of writes issued.
     */
    public int doWrites() {
        return doWritesBssid() + doWritesNetwork();
    }

    private int doWritesBssid() {
        if (mMemoryStore == null) return 0;
        int count = 0;
        int bytes = 0;
        for (PerBssid perBssid : mApForBssid.values()) {
            if (perBssid.changed) {
                perBssid.finishPendingRead();
                byte[] serialized = perBssid.toAccessPoint(/* No BSSID */ true).toByteArray();
                mMemoryStore.setCluster(perBssid.getL2Key(), groupHintFromSsid(perBssid.ssid));
                mMemoryStore.write(perBssid.getL2Key(), PER_BSSID_DATA_NAME, serialized);

                perBssid.changed = false;
                count++;
                bytes += serialized.length;
            }
        }
        if (mVerboseLoggingEnabled && count > 0) {
            Log.v(TAG, "Write count: " + count + ", bytes: " + bytes);
        }
        return count;
    }

    private int doWritesNetwork() {
        if (mMemoryStore == null) return 0;
        int count = 0;
        int bytes = 0;
        for (PerNetwork perNetwork : mApForNetwork.values()) {
            if (perNetwork.changed) {
                perNetwork.finishPendingRead();
                byte[] serialized = perNetwork.toNetworkStats().toByteArray();
                mMemoryStore.setCluster(perNetwork.getL2Key(), groupHintFromSsid(perNetwork.ssid));
                mMemoryStore.write(perNetwork.getL2Key(), PER_NETWORK_DATA_NAME, serialized);
                perNetwork.changed = false;
                count++;
                bytes += serialized.length;
            }
        }
        if (mVerboseLoggingEnabled && count > 0) {
            Log.v(TAG, "Write count: " + count + ", bytes: " + bytes);
        }
        return count;
    }

    /**
     * Evicts older entries from memory.
     *
     * This uses an approximate least-recently-used method. When the number of
     * referenced entries exceeds the target value, any items that have not been
     * referenced since the last round are evicted, and the remaining entries
     * are marked as unreferenced. The total count varies between the target
     * value and twice the target value.
     */
    private void clean() {
        if (mMemoryStore == null) return;
        if (mApForBssidReferenced >= mApForBssidTargetSize) {
            doWritesBssid(); // Do not want to evict changed items
            // Evict the unreferenced ones, and clear all the referenced bits for the next round.
            Iterator<Map.Entry<MacAddress, PerBssid>> it = mApForBssid.entrySet().iterator();
            while (it.hasNext()) {
                PerBssid perBssid = it.next().getValue();
                if (perBssid.referenced) {
                    perBssid.referenced = false;
                } else {
                    it.remove();
                    if (mVerboseLoggingEnabled) Log.v(TAG, "Evict " + perBssid.id);
                }
            }
            mApForBssidReferenced = 0;
        }
    }

    /**
     * Compute a hash value with the given SSID and MAC address
     * @param ssid is the network SSID
     * @param mac is the network MAC address
     * @param l2KeySeed is the seed for hash generation
     * @return
     */
    public static long computeHashLong(String ssid, MacAddress mac, String l2KeySeed) {
        final ArrayList<Byte> decodedSsid;
        try {
            decodedSsid = NativeUtil.decodeSsid(ssid);
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "NativeUtil.decodeSsid failed: malformed string: " + ssid);
            return 0;
        }
        byte[][] parts = {
                // Our seed keeps the L2Keys specific to this device
                l2KeySeed.getBytes(),
                // ssid is either quoted utf8 or hex-encoded bytes; turn it into plain bytes.
                NativeUtil.byteArrayFromArrayList(decodedSsid),
                // And the BSSID
                mac.toByteArray()
        };
        // Assemble the parts into one, with single-byte lengths before each.
        int n = 0;
        for (int i = 0; i < parts.length; i++) {
            n += 1 + parts[i].length;
        }
        byte[] mashed = new byte[n];
        int p = 0;
        for (int i = 0; i < parts.length; i++) {
            byte[] part = parts[i];
            mashed[p++] = (byte) part.length;
            for (int j = 0; j < part.length; j++) {
                mashed[p++] = part[j];
            }
        }
        // Finally, turn that into a long
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            Log.e(TAG, "SHA-256 not supported.");
            return 0;
        }
        ByteBuffer buffer = ByteBuffer.wrap(md.digest(mashed));
        return buffer.getLong();
    }

    private static String groupHintFromLong(long hash) {
        return "G" + Long.toHexString(hash);
    }

    @VisibleForTesting
    PerBssid fetchByBssid(MacAddress mac) {
        return mApForBssid.get(mac);
    }

    @VisibleForTesting
    PerNetwork fetchByNetwork(String ssid) {
        return mApForNetwork.get(ssid);
    }

    @VisibleForTesting
    PerBssid perBssidFromAccessPoint(String ssid, AccessPoint ap) {
        MacAddress bssid = MacAddress.fromBytes(ap.getBssid().toByteArray());
        return new PerBssid(ssid, bssid).merge(ap);
    }

    @VisibleForTesting
    PerNetwork perNetworkFromNetworkStats(String ssid, NetworkStats ns) {
        return new PerNetwork(ssid).mergeNetworkStatsFromMemory(ns);
    }

    final class PerSignal {
        public final Event event;
        public final int frequency;
        public final PerUnivariateStatistic rssi;
        public final PerUnivariateStatistic linkspeed;
        @Nullable public final PerUnivariateStatistic elapsedMs;
        PerSignal(Event event, int frequency) {
            this.event = event;
            this.frequency = frequency;
            switch (event) {
                case SIGNAL_POLL:
                case IP_CONFIGURATION_SUCCESS:
                case IP_REACHABILITY_LOST:
                    this.rssi = new PerUnivariateStatistic(RSSI_BUCKETS);
                    break;
                default:
                    this.rssi = new PerUnivariateStatistic();
                    break;
            }
            this.linkspeed = new PerUnivariateStatistic();
            switch (event) {
                case FIRST_POLL_AFTER_CONNECTION:
                case IP_CONFIGURATION_SUCCESS:
                case VALIDATION_SUCCESS:
                case CONNECTION_FAILURE:
                case DISCONNECTION:
                case WIFI_DISABLED:
                case ROAM_FAILURE:
                    this.elapsedMs = new PerUnivariateStatistic();
                    break;
                default:
                    this.elapsedMs = null;
                    break;
            }
        }
        PerSignal merge(Signal signal) {
            Preconditions.checkArgument(event == signal.getEvent());
            Preconditions.checkArgument(frequency == signal.getFrequency());
            rssi.merge(signal.getRssi());
            linkspeed.merge(signal.getLinkspeed());
            if (elapsedMs != null && signal.hasElapsedMs()) {
                elapsedMs.merge(signal.getElapsedMs());
            }
            return this;
        }
        Signal toSignal() {
            Signal.Builder builder = Signal.newBuilder();
            builder.setEvent(event)
                    .setFrequency(frequency)
                    .setRssi(rssi.toUnivariateStatistic())
                    .setLinkspeed(linkspeed.toUnivariateStatistic());
            if (elapsedMs != null) {
                builder.setElapsedMs(elapsedMs.toUnivariateStatistic());
            }
            if (rssi.intHistogram != null
                    && rssi.intHistogram.numNonEmptyBuckets() > 0) {
                logd("Histogram " + event + " RSSI" + rssi.intHistogram);
            }
            return builder.build();
        }
    }

    final class PerUnivariateStatistic {
        public long count = 0;
        public double sum = 0.0;
        public double sumOfSquares = 0.0;
        public double minValue = Double.POSITIVE_INFINITY;
        public double maxValue = Double.NEGATIVE_INFINITY;
        public double historicalMean = 0.0;
        public double historicalVariance = Double.POSITIVE_INFINITY;
        public IntHistogram intHistogram = null;
        PerUnivariateStatistic() {}
        PerUnivariateStatistic(int[] bucketBoundaries) {
            intHistogram = new IntHistogram(bucketBoundaries);
        }
        void update(double value) {
            count++;
            sum += value;
            sumOfSquares += value * value;
            minValue = Math.min(minValue, value);
            maxValue = Math.max(maxValue, value);
            if (intHistogram != null) {
                intHistogram.add(Math.round((float) value), 1);
            }
        }
        void age() {
            //TODO  Fold the current stats into the historical stats
        }
        void merge(UnivariateStatistic stats) {
            if (stats.hasCount()) {
                count += stats.getCount();
                sum += stats.getSum();
                sumOfSquares += stats.getSumOfSquares();
            }
            if (stats.hasMinValue()) {
                minValue = Math.min(minValue, stats.getMinValue());
            }
            if (stats.hasMaxValue()) {
                maxValue = Math.max(maxValue, stats.getMaxValue());
            }
            if (stats.hasHistoricalVariance()) {
                if (historicalVariance < Double.POSITIVE_INFINITY) {
                    // Combine the estimates; c.f.
                    // Maybeck, Stochasic Models, Estimation, and Control, Vol. 1
                    // equations (1-3) and (1-4)
                    double numer1 = stats.getHistoricalVariance();
                    double numer2 = historicalVariance;
                    double denom = numer1 + numer2;
                    historicalMean = (numer1 * historicalMean
                                    + numer2 * stats.getHistoricalMean())
                                    / denom;
                    historicalVariance = numer1 * numer2 / denom;
                } else {
                    historicalMean = stats.getHistoricalMean();
                    historicalVariance = stats.getHistoricalVariance();
                }
            }
            if (intHistogram != null) {
                for (HistogramBucket bucket : stats.getBucketsList()) {
                    long low = bucket.getLow();
                    long count = bucket.getNumber();
                    if (low != (int) low || count != (int) count || count < 0) {
                        Log.e(TAG, "Found corrupted histogram! Clearing.");
                        intHistogram.clear();
                        break;
                    }
                    intHistogram.add((int) low, (int) count);
                }
            }
        }
        UnivariateStatistic toUnivariateStatistic() {
            UnivariateStatistic.Builder builder = UnivariateStatistic.newBuilder();
            if (count != 0) {
                builder.setCount(count)
                        .setSum(sum)
                        .setSumOfSquares(sumOfSquares)
                        .setMinValue(minValue)
                        .setMaxValue(maxValue);
            }
            if (historicalVariance < Double.POSITIVE_INFINITY) {
                builder.setHistoricalMean(historicalMean)
                        .setHistoricalVariance(historicalVariance);
            }
            if (mPersistentHistograms
                    && intHistogram != null && intHistogram.numNonEmptyBuckets() > 0) {
                for (IntHistogram.Bucket b : intHistogram) {
                    if (b.count == 0) continue;
                    builder.addBuckets(
                            HistogramBucket.newBuilder().setLow(b.start).setNumber(b.count));
                }
            }
            return builder.build();
        }
    }

    /**
     * Returns the current scorecard in the form of a protobuf com_android_server_wifi.NetworkList
     *
     * Synchronization is the caller's responsibility.
     *
     * @param obfuscate - if true, ssids and bssids are omitted (short id only)
     */
    public byte[] getNetworkListByteArray(boolean obfuscate) {
        // These are really grouped by ssid, ignoring the security type.
        Map<String, Network.Builder> networks = new ArrayMap<>();
        for (PerBssid perBssid: mApForBssid.values()) {
            String key = perBssid.ssid;
            Network.Builder network = networks.get(key);
            if (network == null) {
                network = Network.newBuilder();
                networks.put(key, network);
                if (!obfuscate) {
                    network.setSsid(perBssid.ssid);
                }
            }
            if (perBssid.mNetworkAgentId >= network.getNetworkAgentId()) {
                network.setNetworkAgentId(perBssid.mNetworkAgentId);
            }
            if (perBssid.mNetworkConfigId >= network.getNetworkConfigId()) {
                network.setNetworkConfigId(perBssid.mNetworkConfigId);
            }
            network.addAccessPoints(perBssid.toAccessPoint(obfuscate));
        }
        for (PerNetwork perNetwork: mApForNetwork.values()) {
            String key = perNetwork.ssid;
            Network.Builder network = networks.get(key);
            if (network != null) {
                network.setNetworkStats(perNetwork.toNetworkStats());
            }
        }
        NetworkList.Builder builder = NetworkList.newBuilder();
        for (Network.Builder network: networks.values()) {
            builder.addNetworks(network);
        }
        return builder.build().toByteArray();
    }

    /**
     * Returns the current scorecard as a base64-encoded protobuf
     *
     * Synchronization is the caller's responsibility.
     *
     * @param obfuscate - if true, bssids are omitted (short id only)
     */
    public String getNetworkListBase64(boolean obfuscate) {
        byte[] raw = getNetworkListByteArray(obfuscate);
        return Base64.encodeToString(raw, Base64.DEFAULT);
    }

    /**
     * Clears the internal state.
     *
     * This is called in response to a factoryReset call from Settings.
     * The memory store will be called after we are called, to wipe the stable
     * storage as well. Since we will have just removed all of our networks,
     * it is very unlikely that we're connected, or will connect immediately.
     * Any in-flight reads will land in the objects we are dropping here, and
     * the memory store should drop the in-flight writes. Ideally we would
     * avoid issuing reads until we were sure that the memory store had
     * received the factoryReset.
     */
    public void clear() {
        mApForBssid.clear();
        mApForNetwork.clear();
        resetAllConnectionStatesInternal();
    }

    /**
     * build bandwidth estimator stats proto and then clear all related counters
     */
    public BandwidthEstimatorStats dumpBandwidthEstimatorStats() {
        BandwidthEstimatorStats stats = new BandwidthEstimatorStats();
        stats.stats2G = dumpBandwdithStatsPerBand(0);
        stats.statsAbove2G = dumpBandwdithStatsPerBand(1);
        return stats;
    }

    private BandwidthEstimatorStats.PerBand dumpBandwdithStatsPerBand(int bandIdx) {
        BandwidthEstimatorStats.PerBand stats = new BandwidthEstimatorStats.PerBand();
        stats.tx = dumpBandwidthStatsPerLink(bandIdx, LINK_TX);
        stats.rx = dumpBandwidthStatsPerLink(bandIdx, LINK_RX);
        return stats;
    }

    private BandwidthEstimatorStats.PerLink dumpBandwidthStatsPerLink(
            int bandIdx, int linkIdx) {
        BandwidthEstimatorStats.PerLink stats = new BandwidthEstimatorStats.PerLink();
        List<BandwidthEstimatorStats.PerLevel> levels = new ArrayList<>();
        for (int level = 0; level < NUM_SIGNAL_LEVEL; level++) {
            BandwidthEstimatorStats.PerLevel currStats =
                    dumpBandwidthStatsPerLevel(bandIdx, linkIdx, level);
            if (currStats != null) {
                levels.add(currStats);
            }
        }
        stats.level = levels.toArray(new BandwidthEstimatorStats.PerLevel[0]);
        return stats;
    }

    private BandwidthEstimatorStats.PerLevel dumpBandwidthStatsPerLevel(
            int bandIdx, int linkIdx, int level) {
        int count = mBwEstCount[bandIdx][linkIdx][level];
        if (count <= 0) {
            return null;
        }

        BandwidthEstimatorStats.PerLevel stats = new BandwidthEstimatorStats.PerLevel();
        stats.signalLevel = level;
        stats.count = count;
        stats.avgBandwidthKbps = calculateAvg(mBwEstValue[bandIdx][linkIdx][level], count);
        stats.l2ErrorPercent = calculateAvg(
                mL2ErrorAccPercent[bandIdx][linkIdx][level], count);
        stats.bandwidthEstErrorPercent = calculateAvg(
                mBwEstErrorAccPercent[bandIdx][linkIdx][level], count);

        // reset counters for next run
        mBwEstCount[bandIdx][linkIdx][level] = 0;
        mBwEstValue[bandIdx][linkIdx][level] = 0;
        mL2ErrorAccPercent[bandIdx][linkIdx][level] = 0;
        mBwEstErrorAccPercent[bandIdx][linkIdx][level] = 0;
        return stats;
    }

    private int calculateAvg(long acc, int count) {
        return (count > 0) ? (int) (acc / count) : 0;
    }

    /**
     * Dump the internal state and local logs
     */
    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("Dump of WifiScoreCard");
        pw.println("current SSID(s):" + mIfaceToInfoMap.entrySet().stream()
                .map(entry ->
                        "{iface=" + entry.getKey() + ",ssid=" + entry.getValue().ssidCurr + "}")
                .collect(Collectors.joining(",")));
        try {
            mLocalLog.dump(fd, pw, args);
        } catch (Exception e) {
            e.printStackTrace();
        }

        pw.println(" BW Estimation Stats");
        for (int i = 0; i < 2; i++) {
            pw.println((i == 0 ? "2G" : "5G"));
            for (int j = 0; j < NUM_LINK_DIRECTION; j++) {
                pw.println((j == 0 ? " Tx" : " Rx"));
                pw.println(" Count");
                printValues(mBwEstCount[i][j], pw);
                pw.println(" AvgKbps");
                printAvgStats(mBwEstValue[i][j], mBwEstCount[i][j], pw);
                pw.println(" BwEst error");
                printAvgStats(mBwEstErrorAccPercent[i][j], mBwEstCount[i][j], pw);
                pw.println(" L2 error");
                printAvgStats(mL2ErrorAccPercent[i][j], mBwEstCount[i][j], pw);
            }
        }
        pw.println();
    }

    private void printValues(int[] values, PrintWriter pw) {
        StringBuilder sb = new StringBuilder();
        for (int k = 0; k < NUM_SIGNAL_LEVEL; k++) {
            sb.append(" " + values[k]);
        }
        pw.println(sb.toString());
    }

    private void printAvgStats(long[] stats, int[] count, PrintWriter pw) {
        StringBuilder sb = new StringBuilder();
        for (int k = 0; k < NUM_SIGNAL_LEVEL; k++) {
            sb.append(" " + calculateAvg(stats[k], count[k]));
        }
        pw.println(sb.toString());
    }
}
