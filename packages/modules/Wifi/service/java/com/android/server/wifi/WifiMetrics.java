/*
 * Copyright (C) 2016 The Android Open Source Project
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

import static android.net.wifi.WifiConfiguration.MeteredOverride;

import static com.android.server.wifi.ActiveModeManager.ROLE_CLIENT_PRIMARY;
import static com.android.server.wifi.proto.WifiStatsLog.WIFI_CONFIG_SAVED;
import static com.android.server.wifi.proto.WifiStatsLog.WIFI_IS_UNUSABLE_REPORTED;

import static java.lang.StrictMath.toIntExact;

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.NetworkCapabilities;
import android.net.wifi.EAPConstants;
import android.net.wifi.IOnWifiUsabilityStatsListener;
import android.net.wifi.MloLink;
import android.net.wifi.ScanResult;
import android.net.wifi.SecurityParams;
import android.net.wifi.SoftApCapability;
import android.net.wifi.SoftApConfiguration;
import android.net.wifi.SoftApInfo;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiAnnotations;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiConfiguration.NetworkSelectionStatus;
import android.net.wifi.WifiEnterpriseConfig;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.DeviceMobilityState;
import android.net.wifi.WifiScanner;
import android.net.wifi.WifiUsabilityStatsEntry.ProbeStatus;
import android.net.wifi.hotspot2.PasspointConfiguration;
import android.net.wifi.hotspot2.ProvisioningCallback;
import android.net.wifi.hotspot2.ProvisioningCallback.OsuFailure;
import android.net.wifi.nl80211.WifiNl80211Manager;
import android.net.wifi.util.ScanResultUtil;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.os.SystemProperties;
import android.os.WorkSource;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Base64;
import android.util.Log;
import android.util.Pair;
import android.util.SparseArray;
import android.util.SparseBooleanArray;
import android.util.SparseIntArray;

import com.android.internal.annotations.VisibleForTesting;
import com.android.server.wifi.SupplicantStaIfaceHal.StaIfaceReasonCode;
import com.android.server.wifi.SupplicantStaIfaceHal.StaIfaceStatusCode;
import com.android.server.wifi.aware.WifiAwareMetrics;
import com.android.server.wifi.hotspot2.ANQPNetworkKey;
import com.android.server.wifi.hotspot2.NetworkDetail;
import com.android.server.wifi.hotspot2.PasspointManager;
import com.android.server.wifi.hotspot2.PasspointMatch;
import com.android.server.wifi.hotspot2.PasspointProvider;
import com.android.server.wifi.hotspot2.Utils;
import com.android.server.wifi.p2p.WifiP2pMetrics;
import com.android.server.wifi.proto.WifiStatsLog;
import com.android.server.wifi.proto.nano.WifiMetricsProto;
import com.android.server.wifi.proto.nano.WifiMetricsProto.ConnectToNetworkNotificationAndActionCount;
import com.android.server.wifi.proto.nano.WifiMetricsProto.ContentionTimeStats;
import com.android.server.wifi.proto.nano.WifiMetricsProto.DeviceMobilityStatePnoScanStats;
import com.android.server.wifi.proto.nano.WifiMetricsProto.ExperimentValues;
import com.android.server.wifi.proto.nano.WifiMetricsProto.FirstConnectAfterBootStats;
import com.android.server.wifi.proto.nano.WifiMetricsProto.FirstConnectAfterBootStats.Attempt;
import com.android.server.wifi.proto.nano.WifiMetricsProto.HealthMonitorMetrics;
import com.android.server.wifi.proto.nano.WifiMetricsProto.InitPartialScanStats;
import com.android.server.wifi.proto.nano.WifiMetricsProto.LinkProbeStats;
import com.android.server.wifi.proto.nano.WifiMetricsProto.LinkProbeStats.ExperimentProbeCounts;
import com.android.server.wifi.proto.nano.WifiMetricsProto.LinkProbeStats.LinkProbeFailureReasonCount;
import com.android.server.wifi.proto.nano.WifiMetricsProto.LinkSpeedCount;
import com.android.server.wifi.proto.nano.WifiMetricsProto.MeteredNetworkStats;
import com.android.server.wifi.proto.nano.WifiMetricsProto.NetworkDisableReason;
import com.android.server.wifi.proto.nano.WifiMetricsProto.NetworkSelectionExperimentDecisions;
import com.android.server.wifi.proto.nano.WifiMetricsProto.PasspointProfileTypeCount;
import com.android.server.wifi.proto.nano.WifiMetricsProto.PasspointProvisionStats;
import com.android.server.wifi.proto.nano.WifiMetricsProto.PasspointProvisionStats.ProvisionFailureCount;
import com.android.server.wifi.proto.nano.WifiMetricsProto.PnoScanMetrics;
import com.android.server.wifi.proto.nano.WifiMetricsProto.RadioStats;
import com.android.server.wifi.proto.nano.WifiMetricsProto.RateStats;
import com.android.server.wifi.proto.nano.WifiMetricsProto.SoftApConnectedClientsEvent;
import com.android.server.wifi.proto.nano.WifiMetricsProto.StaEvent;
import com.android.server.wifi.proto.nano.WifiMetricsProto.StaEvent.ConfigInfo;
import com.android.server.wifi.proto.nano.WifiMetricsProto.TargetNetworkInfo;
import com.android.server.wifi.proto.nano.WifiMetricsProto.UserActionEvent;
import com.android.server.wifi.proto.nano.WifiMetricsProto.UserReactionToApprovalUiEvent;
import com.android.server.wifi.proto.nano.WifiMetricsProto.UserReactionToApprovalUiEvent.UserReaction;
import com.android.server.wifi.proto.nano.WifiMetricsProto.WifiIsUnusableEvent;
import com.android.server.wifi.proto.nano.WifiMetricsProto.WifiLinkLayerUsageStats;
import com.android.server.wifi.proto.nano.WifiMetricsProto.WifiLockStats;
import com.android.server.wifi.proto.nano.WifiMetricsProto.WifiNetworkRequestApiLog;
import com.android.server.wifi.proto.nano.WifiMetricsProto.WifiNetworkSuggestionApiLog;
import com.android.server.wifi.proto.nano.WifiMetricsProto.WifiNetworkSuggestionApiLog.SuggestionAppCount;
import com.android.server.wifi.proto.nano.WifiMetricsProto.WifiStatus;
import com.android.server.wifi.proto.nano.WifiMetricsProto.WifiToWifiSwitchStats;
import com.android.server.wifi.proto.nano.WifiMetricsProto.WifiToggleStats;
import com.android.server.wifi.proto.nano.WifiMetricsProto.WifiUsabilityStats;
import com.android.server.wifi.proto.nano.WifiMetricsProto.WifiUsabilityStatsEntry;
import com.android.server.wifi.rtt.RttMetrics;
import com.android.server.wifi.scanner.KnownBandsChannelHelper;
import com.android.server.wifi.util.InformationElementUtil;
import com.android.server.wifi.util.InformationElementUtil.ApType6GHz;
import com.android.server.wifi.util.InformationElementUtil.WifiMode;
import com.android.server.wifi.util.IntCounter;
import com.android.server.wifi.util.IntHistogram;
import com.android.server.wifi.util.MetricsUtils;
import com.android.server.wifi.util.ObjectCounter;
import com.android.server.wifi.util.StringUtil;
import com.android.wifi.resources.R;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Calendar;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Provides storage for wireless connectivity metrics, as they are generated.
 * Metrics logged by this class include:
 *   Aggregated connection stats (num of connections, num of failures, ...)
 *   Discrete connection event stats (time, duration, failure codes, ...)
 *   Router details (technology type, authentication type, ...)
 *   Scan stats
 */
public class WifiMetrics {
    private static final String TAG = "WifiMetrics";
    private static final boolean DBG = false;

    /**
     * Clamp the RSSI poll counts to values between [MIN,MAX]_RSSI_POLL
     */
    private static final int MAX_RSSI_POLL = 0;
    private static final int MIN_RSSI_POLL = -127;
    public static final int MAX_RSSI_DELTA = 127;
    public static final int MIN_RSSI_DELTA = -127;
    /** Minimum link speed (Mbps) to count for link_speed_counts */
    public static final int MIN_LINK_SPEED_MBPS = 0;
    /** Maximum time period between ScanResult and RSSI poll to generate rssi delta datapoint */
    public static final long TIMEOUT_RSSI_DELTA_MILLIS =  3000;
    private static final int MIN_WIFI_SCORE = 0;
    private static final int MAX_WIFI_SCORE = ConnectedScore.WIFI_MAX_SCORE;
    private static final int MIN_WIFI_USABILITY_SCORE = 0; // inclusive
    private static final int MAX_WIFI_USABILITY_SCORE = 100; // inclusive
    @VisibleForTesting
    static final int LOW_WIFI_SCORE = 50; // Mobile data score
    @VisibleForTesting
    static final int LOW_WIFI_USABILITY_SCORE = 50; // Mobile data score
    private final Object mLock = new Object();
    private static final int MAX_CONNECTION_EVENTS = 256;
    // Largest bucket in the NumConnectableNetworkCount histogram,
    // anything large will be stored in this bucket
    public static final int MAX_CONNECTABLE_SSID_NETWORK_BUCKET = 20;
    public static final int MAX_CONNECTABLE_BSSID_NETWORK_BUCKET = 50;
    public static final int MAX_TOTAL_SCAN_RESULT_SSIDS_BUCKET = 100;
    public static final int MAX_TOTAL_SCAN_RESULTS_BUCKET = 250;
    public static final int MAX_TOTAL_PASSPOINT_APS_BUCKET = 50;
    public static final int MAX_TOTAL_PASSPOINT_UNIQUE_ESS_BUCKET = 20;
    public static final int MAX_PASSPOINT_APS_PER_UNIQUE_ESS_BUCKET = 50;
    public static final int MAX_TOTAL_80211MC_APS_BUCKET = 20;
    private static final int CONNECT_TO_NETWORK_NOTIFICATION_ACTION_KEY_MULTIPLIER = 1000;
    // Max limit for number of soft AP related events, extra events will be dropped.
    private static final int MAX_NUM_SOFT_AP_EVENTS = 256;
    // Maximum number of WifiIsUnusableEvent
    public static final int MAX_UNUSABLE_EVENTS = 20;
    // Minimum time wait before generating next WifiIsUnusableEvent from data stall
    public static final int MIN_DATA_STALL_WAIT_MS = 120 * 1000; // 2 minutes
    // Max number of WifiUsabilityStatsEntry elements to store in the ringbuffer.
    public static final int MAX_WIFI_USABILITY_STATS_ENTRIES_LIST_SIZE = 40;
    // Max number of WifiUsabilityStats elements to store for each type.
    public static final int MAX_WIFI_USABILITY_STATS_LIST_SIZE_PER_TYPE = 10;
    // Max number of WifiUsabilityStats per labeled type to upload to server
    public static final int MAX_WIFI_USABILITY_STATS_PER_TYPE_TO_UPLOAD = 2;
    public static final int NUM_WIFI_USABILITY_STATS_ENTRIES_PER_WIFI_GOOD = 100;
    public static final int MIN_WIFI_GOOD_USABILITY_STATS_PERIOD_MS = 1000 * 3600; // 1 hour
    public static final int PASSPOINT_DEAUTH_IMMINENT_SCOPE_ESS = 0;
    public static final int PASSPOINT_DEAUTH_IMMINENT_SCOPE_BSS = 1;
    public static final int COUNTRY_CODE_CONFLICT_WIFI_SCAN = -1;
    public static final int COUNTRY_CODE_CONFLICT_WIFI_SCAN_TELEPHONY = -2;
    public static final int MAX_COUNTRY_CODE_COUNT = 4;
    // Histogram for WifiConfigStore IO duration times. Indicates the following 5 buckets (in ms):
    //   < 50
    //   [50, 100)
    //   [100, 150)
    //   [150, 200)
    //   [200, 300)
    //   >= 300
    private static final int[] WIFI_CONFIG_STORE_IO_DURATION_BUCKET_RANGES_MS =
            {50, 100, 150, 200, 300};
    // Minimum time wait before generating a LABEL_GOOD stats after score breaching low.
    public static final int MIN_SCORE_BREACH_TO_GOOD_STATS_WAIT_TIME_MS = 60 * 1000; // 1 minute
    // Maximum time that a score breaching low event stays valid.
    public static final int VALIDITY_PERIOD_OF_SCORE_BREACH_LOW_MS = 90 * 1000; // 1.5 minutes

    private static final int WIFI_RECONNECT_DURATION_SHORT_MILLIS = 10 * 1000;
    private static final int WIFI_RECONNECT_DURATION_MEDIUM_MILLIS = 60 * 1000;
    // Number of WME Access Categories
    private static final int NUM_WME_ACCESS_CATEGORIES = 4;
    private static final int MBB_LINGERING_DURATION_MAX_SECONDS = 30;

    private Clock mClock;
    private boolean mScreenOn;
    private int mWifiState;
    private WifiAwareMetrics mWifiAwareMetrics;
    private RttMetrics mRttMetrics;
    private final PnoScanMetrics mPnoScanMetrics = new PnoScanMetrics();
    private final WifiLinkLayerUsageStats mWifiLinkLayerUsageStats = new WifiLinkLayerUsageStats();
    /** Mapping of radio id values to RadioStats objects. */
    private final SparseArray<RadioStats> mRadioStats = new SparseArray<>();
    private final ExperimentValues mExperimentValues = new ExperimentValues();
    private Handler mHandler;
    private ScoringParams mScoringParams;
    private WifiConfigManager mWifiConfigManager;
    private WifiBlocklistMonitor mWifiBlocklistMonitor;
    private WifiNetworkSelector mWifiNetworkSelector;
    private PasspointManager mPasspointManager;
    private Context mContext;
    private FrameworkFacade mFacade;
    private WifiDataStall mWifiDataStall;
    private WifiLinkLayerStats mLastLinkLayerStats;
    private WifiHealthMonitor mWifiHealthMonitor;
    private WifiScoreCard mWifiScoreCard;
    private SessionData mPreviousSession;
    private SessionData mCurrentSession;
    private String mLastBssid;
    private int mLastFrequency = -1;
    private int mSeqNumInsideFramework = 0;
    private int mLastWifiUsabilityScore = -1;
    private int mLastWifiUsabilityScoreNoReset = -1;
    private int mLastPredictionHorizonSec = -1;
    private int mLastPredictionHorizonSecNoReset = -1;
    private int mSeqNumToFramework = -1;
    @ProbeStatus private int mProbeStatusSinceLastUpdate =
            android.net.wifi.WifiUsabilityStatsEntry.PROBE_STATUS_NO_PROBE;
    private int mProbeElapsedTimeSinceLastUpdateMs = -1;
    private int mProbeMcsRateSinceLastUpdate = -1;
    private long mScoreBreachLowTimeMillis = -1;

    public static final int MAX_STA_EVENTS = 768;
    @VisibleForTesting static final int MAX_USER_ACTION_EVENTS = 200;
    private LinkedList<StaEventWithTime> mStaEventList = new LinkedList<>();
    private LinkedList<UserActionEventWithTime> mUserActionEventList = new LinkedList<>();
    private WifiStatusBuilder mWifiStatusBuilder = new WifiStatusBuilder();
    private int mLastPollRssi = -127;
    private int mLastPollLinkSpeed = -1;
    private int mLastPollRxLinkSpeed = -1;
    private int mLastPollFreq = -1;
    private int mLastScore = -1;
    private boolean mAdaptiveConnectivityEnabled = true;
    private ScanMetrics mScanMetrics;
    private WifiChannelUtilization mWifiChannelUtilization;
    private WifiSettingsStore mWifiSettingsStore;
    private IntCounter mPasspointDeauthImminentScope = new IntCounter();
    private IntCounter mRecentFailureAssociationStatus = new IntCounter();
    private boolean mFirstConnectionAfterBoot = true;
    private long mLastTotalBeaconRx = 0;
    private int mScorerUid = Process.WIFI_UID;

    /**
     * Wi-Fi usability state per interface as predicted by the network scorer.
     */
    public enum WifiUsabilityState {UNKNOWN, USABLE, UNUSABLE};
    private final Map<String, WifiUsabilityState> mWifiUsabilityStatePerIface = new ArrayMap<>();

    /**
     * Metrics are stored within an instance of the WifiLog proto during runtime,
     * The ConnectionEvent, SystemStateEntries & ScanReturnEntries metrics are stored during
     * runtime in member lists of this WifiMetrics class, with the final WifiLog proto being pieced
     * together at dump-time
     */
    private final WifiMetricsProto.WifiLog mWifiLogProto = new WifiMetricsProto.WifiLog();
    /**
     * Session information that gets logged for every Wifi connection attempt.
     */
    private final Deque<ConnectionEvent> mConnectionEventList = new ArrayDeque<>();
    /**
     * The latest started (but un-ended) connection attempt per interface.
     */
    private final Map<String, ConnectionEvent> mCurrentConnectionEventPerIface = new ArrayMap<>();
    /**
     * Count of number of times each scan return code, indexed by WifiLog.ScanReturnCode
     */
    private final SparseIntArray mScanReturnEntries = new SparseIntArray();
    /**
     * Mapping of system state to the counts of scans requested in that wifi state * screenOn
     * combination. Indexed by WifiLog.WifiState * (1 + screenOn)
     */
    private final SparseIntArray mWifiSystemStateEntries = new SparseIntArray();
    /** Mapping of channel frequency to its RSSI distribution histogram **/
    private final Map<Integer, SparseIntArray> mRssiPollCountsMap = new HashMap<>();
    /** Mapping of RSSI scan-poll delta values to counts. */
    private final SparseIntArray mRssiDeltaCounts = new SparseIntArray();
    /** Mapping of link speed values to LinkSpeedCount objects. */
    private final SparseArray<LinkSpeedCount> mLinkSpeedCounts = new SparseArray<>();

    private final IntCounter mTxLinkSpeedCount2g = new IntCounter();
    private final IntCounter mTxLinkSpeedCount5gLow = new IntCounter();
    private final IntCounter mTxLinkSpeedCount5gMid = new IntCounter();
    private final IntCounter mTxLinkSpeedCount5gHigh = new IntCounter();
    private final IntCounter mTxLinkSpeedCount6gLow = new IntCounter();
    private final IntCounter mTxLinkSpeedCount6gMid = new IntCounter();
    private final IntCounter mTxLinkSpeedCount6gHigh = new IntCounter();

    private final IntCounter mRxLinkSpeedCount2g = new IntCounter();
    private final IntCounter mRxLinkSpeedCount5gLow = new IntCounter();
    private final IntCounter mRxLinkSpeedCount5gMid = new IntCounter();
    private final IntCounter mRxLinkSpeedCount5gHigh = new IntCounter();
    private final IntCounter mRxLinkSpeedCount6gLow = new IntCounter();
    private final IntCounter mRxLinkSpeedCount6gMid = new IntCounter();
    private final IntCounter mRxLinkSpeedCount6gHigh = new IntCounter();

    private final IntCounter mMakeBeforeBreakLingeringDurationSeconds = new IntCounter();

    /** RSSI of the scan result for the last connection event*/
    private int mScanResultRssi = 0;
    /** Boot-relative timestamp when the last candidate scanresult was received, used to calculate
        RSSI deltas. -1 designates no candidate scanResult being tracked */
    private long mScanResultRssiTimestampMillis = -1;
    /** Mapping of alert reason to the respective alert count. */
    private final SparseIntArray mWifiAlertReasonCounts = new SparseIntArray();
    /**
     * Records the getElapsedSinceBootMillis (in seconds) that represents the beginning of data
     * capture for for this WifiMetricsProto
     */
    private long mRecordStartTimeSec;
    /** Mapping of Wifi Scores to counts */
    private final SparseIntArray mWifiScoreCounts = new SparseIntArray();
    /** Mapping of Wifi Usability Scores to counts */
    private final SparseIntArray mWifiUsabilityScoreCounts = new SparseIntArray();
    /** Mapping of SoftApManager start SoftAp return codes to counts */
    private final SparseIntArray mSoftApManagerReturnCodeCounts = new SparseIntArray();

    private final SparseIntArray mTotalSsidsInScanHistogram = new SparseIntArray();
    private final SparseIntArray mTotalBssidsInScanHistogram = new SparseIntArray();
    private final SparseIntArray mAvailableOpenSsidsInScanHistogram = new SparseIntArray();
    private final SparseIntArray mAvailableOpenBssidsInScanHistogram = new SparseIntArray();
    private final SparseIntArray mAvailableSavedSsidsInScanHistogram = new SparseIntArray();
    private final SparseIntArray mAvailableSavedBssidsInScanHistogram = new SparseIntArray();
    private final SparseIntArray mAvailableOpenOrSavedSsidsInScanHistogram = new SparseIntArray();
    private final SparseIntArray mAvailableOpenOrSavedBssidsInScanHistogram = new SparseIntArray();
    private final SparseIntArray mAvailableSavedPasspointProviderProfilesInScanHistogram =
            new SparseIntArray();
    private final SparseIntArray mAvailableSavedPasspointProviderBssidsInScanHistogram =
            new SparseIntArray();

    private final IntCounter mInstalledPasspointProfileTypeForR1 = new IntCounter();
    private final IntCounter mInstalledPasspointProfileTypeForR2 = new IntCounter();

    /** Mapping of "Connect to Network" notifications to counts. */
    private final SparseIntArray mConnectToNetworkNotificationCount = new SparseIntArray();
    /** Mapping of "Connect to Network" notification user actions to counts. */
    private final SparseIntArray mConnectToNetworkNotificationActionCount = new SparseIntArray();
    private int mOpenNetworkRecommenderBlocklistSize = 0;
    private boolean mIsWifiNetworksAvailableNotificationOn = false;
    private int mNumOpenNetworkConnectMessageFailedToSend = 0;
    private int mNumOpenNetworkRecommendationUpdates = 0;
    /** List of soft AP events related to number of connected clients in tethered mode */
    private final List<SoftApConnectedClientsEvent> mSoftApEventListTethered = new ArrayList<>();
    /** List of soft AP events related to number of connected clients in local only mode */
    private final List<SoftApConnectedClientsEvent> mSoftApEventListLocalOnly = new ArrayList<>();

    private final SparseIntArray mObservedHotspotR1ApInScanHistogram = new SparseIntArray();
    private final SparseIntArray mObservedHotspotR2ApInScanHistogram = new SparseIntArray();
    private final SparseIntArray mObservedHotspotR3ApInScanHistogram = new SparseIntArray();
    private final SparseIntArray mObservedHotspotR1EssInScanHistogram = new SparseIntArray();
    private final SparseIntArray mObservedHotspotR2EssInScanHistogram = new SparseIntArray();
    private final SparseIntArray mObservedHotspotR3EssInScanHistogram = new SparseIntArray();
    private final SparseIntArray mObservedHotspotR1ApsPerEssInScanHistogram = new SparseIntArray();
    private final SparseIntArray mObservedHotspotR2ApsPerEssInScanHistogram = new SparseIntArray();
    private final SparseIntArray mObservedHotspotR3ApsPerEssInScanHistogram = new SparseIntArray();

    private final SparseIntArray mObserved80211mcApInScanHistogram = new SparseIntArray();

    // link probing stats
    private final IntCounter mLinkProbeSuccessRssiCounts = new IntCounter(-85, -65);
    private final IntCounter mLinkProbeFailureRssiCounts = new IntCounter(-85, -65);
    private final IntCounter mLinkProbeSuccessLinkSpeedCounts = new IntCounter();
    private final IntCounter mLinkProbeFailureLinkSpeedCounts = new IntCounter();

    private static final int[] LINK_PROBE_TIME_SINCE_LAST_TX_SUCCESS_SECONDS_HISTOGRAM_BUCKETS =
            {5, 15, 45, 135};
    private final IntHistogram mLinkProbeSuccessSecondsSinceLastTxSuccessHistogram =
            new IntHistogram(LINK_PROBE_TIME_SINCE_LAST_TX_SUCCESS_SECONDS_HISTOGRAM_BUCKETS);
    private final IntHistogram mLinkProbeFailureSecondsSinceLastTxSuccessHistogram =
            new IntHistogram(LINK_PROBE_TIME_SINCE_LAST_TX_SUCCESS_SECONDS_HISTOGRAM_BUCKETS);

    private static final int[] LINK_PROBE_ELAPSED_TIME_MS_HISTOGRAM_BUCKETS =
            {5, 10, 15, 20, 25, 50, 100, 200, 400, 800};
    private final IntHistogram mLinkProbeSuccessElapsedTimeMsHistogram = new IntHistogram(
            LINK_PROBE_ELAPSED_TIME_MS_HISTOGRAM_BUCKETS);
    private final IntCounter mLinkProbeFailureReasonCounts = new IntCounter();
    private final MeteredNetworkStatsBuilder mMeteredNetworkStatsBuilder =
            new MeteredNetworkStatsBuilder();

    /**
     * Maps a String link probe experiment ID to the number of link probes that were sent for this
     * experiment.
     */
    private final ObjectCounter<String> mLinkProbeExperimentProbeCounts = new ObjectCounter<>();
    private int mLinkProbeStaEventCount = 0;
    @VisibleForTesting static final int MAX_LINK_PROBE_STA_EVENTS = MAX_STA_EVENTS / 4;

    private final LinkedList<WifiUsabilityStatsEntry> mWifiUsabilityStatsEntriesList =
            new LinkedList<>();
    private final LinkedList<WifiUsabilityStats> mWifiUsabilityStatsListBad = new LinkedList<>();
    private final LinkedList<WifiUsabilityStats> mWifiUsabilityStatsListGood = new LinkedList<>();
    private int mWifiUsabilityStatsCounter = 0;
    private final Random mRand = new Random();
    private final RemoteCallbackList<IOnWifiUsabilityStatsListener> mOnWifiUsabilityListeners;

    private final SparseArray<DeviceMobilityStatePnoScanStats> mMobilityStatePnoStatsMap =
            new SparseArray<>();
    private int mCurrentDeviceMobilityState;
    /**
     * The timestamp of the start of the current device mobility state.
     */
    private long mCurrentDeviceMobilityStateStartMs;
    /**
     * The timestamp of when the PNO scan started in the current device mobility state.
     */
    private long mCurrentDeviceMobilityStatePnoScanStartMs;

    /** Wifi power metrics*/
    private WifiPowerMetrics mWifiPowerMetrics;

    /** Wifi Wake metrics */
    private final WifiWakeMetrics mWifiWakeMetrics = new WifiWakeMetrics();

    /** Wifi P2p metrics */
    private final WifiP2pMetrics mWifiP2pMetrics;

    /** DPP */
    private final DppMetrics mDppMetrics;

    private final WifiMonitor mWifiMonitor;
    private ActiveModeWarden mActiveModeWarden;
    private final Map<String, ActiveModeManager.ClientRole> mIfaceToRoleMap = new ArrayMap<>();

    /** WifiConfigStore read duration histogram. */
    private SparseIntArray mWifiConfigStoreReadDurationHistogram = new SparseIntArray();

    /** WifiConfigStore write duration histogram. */
    private SparseIntArray mWifiConfigStoreWriteDurationHistogram = new SparseIntArray();

    /** New  API surface metrics */
    private final WifiNetworkRequestApiLog mWifiNetworkRequestApiLog =
            new WifiNetworkRequestApiLog();
    private static final int[] NETWORK_REQUEST_API_MATCH_SIZE_HISTOGRAM_BUCKETS =
            {0, 1, 5, 10};
    private final IntHistogram mWifiNetworkRequestApiMatchSizeHistogram =
            new IntHistogram(NETWORK_REQUEST_API_MATCH_SIZE_HISTOGRAM_BUCKETS);

    private static final int[] NETWORK_REQUEST_API_DURATION_SEC_BUCKETS =
            {0, toIntExact(Duration.ofMinutes(3).getSeconds()),
                    toIntExact(Duration.ofMinutes(10).getSeconds()),
                    toIntExact(Duration.ofMinutes(30).getSeconds()),
                    toIntExact(Duration.ofHours(1).getSeconds()),
                    toIntExact(Duration.ofHours(6).getSeconds())};
    private final IntHistogram mWifiNetworkRequestApiConnectionDurationSecOnPrimaryIfaceHistogram =
            new IntHistogram(NETWORK_REQUEST_API_DURATION_SEC_BUCKETS);
    private final IntHistogram
            mWifiNetworkRequestApiConnectionDurationSecOnSecondaryIfaceHistogram =
            new IntHistogram(NETWORK_REQUEST_API_DURATION_SEC_BUCKETS);
    private final IntHistogram mWifiNetworkRequestApiConcurrentConnectionDurationSecHistogram =
            new IntHistogram(NETWORK_REQUEST_API_DURATION_SEC_BUCKETS);

    private final WifiNetworkSuggestionApiLog mWifiNetworkSuggestionApiLog =
            new WifiNetworkSuggestionApiLog();
    private static final int[] NETWORK_SUGGESTION_API_LIST_SIZE_HISTOGRAM_BUCKETS =
            {5, 20, 50, 100, 500};
    private final IntHistogram mWifiNetworkSuggestionApiListSizeHistogram =
            new IntHistogram(NETWORK_SUGGESTION_API_LIST_SIZE_HISTOGRAM_BUCKETS);
    private final IntCounter mWifiNetworkSuggestionApiAppTypeCounter = new IntCounter();
    private final List<UserReaction> mUserApprovalSuggestionAppUiReactionList =
            new ArrayList<>();
    private final List<UserReaction> mUserApprovalCarrierUiReactionList =
            new ArrayList<>();
    private final SparseBooleanArray mWifiNetworkSuggestionPriorityGroups =
            new SparseBooleanArray();
    private final Set<String> mWifiNetworkSuggestionCoexistSavedNetworks = new ArraySet<>();

    private final WifiLockStats mWifiLockStats = new WifiLockStats();
    private static final int[] WIFI_LOCK_SESSION_DURATION_HISTOGRAM_BUCKETS =
            {1, 10, 60, 600, 3600};
    private final WifiToggleStats mWifiToggleStats = new WifiToggleStats();
    private BssidBlocklistStats mBssidBlocklistStats = new BssidBlocklistStats();

    private final IntHistogram mWifiLockHighPerfAcqDurationSecHistogram =
            new IntHistogram(WIFI_LOCK_SESSION_DURATION_HISTOGRAM_BUCKETS);
    private final IntHistogram mWifiLockLowLatencyAcqDurationSecHistogram =
            new IntHistogram(WIFI_LOCK_SESSION_DURATION_HISTOGRAM_BUCKETS);

    private final IntHistogram mWifiLockHighPerfActiveSessionDurationSecHistogram =
            new IntHistogram(WIFI_LOCK_SESSION_DURATION_HISTOGRAM_BUCKETS);
    private final IntHistogram mWifiLockLowLatencyActiveSessionDurationSecHistogram =
            new IntHistogram(WIFI_LOCK_SESSION_DURATION_HISTOGRAM_BUCKETS);

    /**
     * (experiment1Id, experiment2Id) =>
     *     (sameSelectionNumChoicesCounter, differentSelectionNumChoicesCounter)
     */
    private Map<Pair<Integer, Integer>, NetworkSelectionExperimentResults>
            mNetworkSelectionExperimentPairNumChoicesCounts = new ArrayMap<>();

    private int mNetworkSelectorExperimentId;

    /**
     * Tracks the nominator for each network (i.e. which entity made the suggestion to connect).
     * This object should not be cleared.
     */
    private final SparseIntArray mNetworkIdToNominatorId = new SparseIntArray();

    /** passpoint provision success count */
    private int mNumProvisionSuccess = 0;

    /** Mapping of failure code to the respective passpoint provision failure count. */
    private final IntCounter mPasspointProvisionFailureCounts = new IntCounter();

    // Connection duration stats collected while link layer stats reports are on
    private final ConnectionDurationStats mConnectionDurationStats = new ConnectionDurationStats();

    private static final int[] CHANNEL_UTILIZATION_BUCKETS =
            {25, 50, 75, 100, 125, 150, 175, 200, 225};

    private final IntHistogram mChannelUtilizationHistogram2G =
            new IntHistogram(CHANNEL_UTILIZATION_BUCKETS);

    private final IntHistogram mChannelUtilizationHistogramAbove2G =
            new IntHistogram(CHANNEL_UTILIZATION_BUCKETS);

    private static final int[] THROUGHPUT_MBPS_BUCKETS =
            {1, 5, 10, 15, 25, 50, 100, 150, 200, 300, 450, 600, 800, 1200, 1600};
    private final IntHistogram mTxThroughputMbpsHistogram2G =
            new IntHistogram(THROUGHPUT_MBPS_BUCKETS);
    private final IntHistogram mRxThroughputMbpsHistogram2G =
            new IntHistogram(THROUGHPUT_MBPS_BUCKETS);
    private final IntHistogram mTxThroughputMbpsHistogramAbove2G =
            new IntHistogram(THROUGHPUT_MBPS_BUCKETS);
    private final IntHistogram mRxThroughputMbpsHistogramAbove2G =
            new IntHistogram(THROUGHPUT_MBPS_BUCKETS);

    // Init partial scan metrics
    private int mInitPartialScanTotalCount;
    private int mInitPartialScanSuccessCount;
    private int mInitPartialScanFailureCount;
    private static final int[] INIT_PARTIAL_SCAN_HISTOGRAM_BUCKETS =
            {1, 3, 5, 10};
    private final IntHistogram mInitPartialScanSuccessHistogram =
            new IntHistogram(INIT_PARTIAL_SCAN_HISTOGRAM_BUCKETS);
    private final IntHistogram mInitPartialScanFailureHistogram =
            new IntHistogram(INIT_PARTIAL_SCAN_HISTOGRAM_BUCKETS);

    // Wi-Fi off metrics
    private final WifiOffMetrics mWifiOffMetrics = new WifiOffMetrics();

    private final SoftApConfigLimitationMetrics mSoftApConfigLimitationMetrics =
            new SoftApConfigLimitationMetrics();

    private final CarrierWifiMetrics mCarrierWifiMetrics =
            new CarrierWifiMetrics();

    @Nullable
    private FirstConnectAfterBootStats mFirstConnectAfterBootStats =
            new FirstConnectAfterBootStats();
    private boolean mIsFirstConnectionAttemptComplete = false;

    private final WifiToWifiSwitchStats mWifiToWifiSwitchStats = new WifiToWifiSwitchStats();

    /** Wi-Fi link specific metrics (MLO). */
    public static class LinkMetrics {
        private long mTotalBeaconRx = 0;
        private @android.net.wifi.WifiUsabilityStatsEntry.LinkState int mLinkUsageState =
                android.net.wifi.WifiUsabilityStatsEntry.LINK_STATE_UNKNOWN;

        /** Get Total beacon received on this link */
        public long getTotalBeaconRx() {
            return mTotalBeaconRx;
        }

        /** Set Total beacon received on this link */
        public void setTotalBeaconRx(long totalBeaconRx) {
            this.mTotalBeaconRx = totalBeaconRx;
        }

        /** Get link usage state */
        public @android.net.wifi.WifiUsabilityStatsEntry.LinkState int getLinkUsageState() {
            return mLinkUsageState;
        }

        /** Set link usage state */
        public void setLinkUsageState(
                @android.net.wifi.WifiUsabilityStatsEntry.LinkState int linkUsageState) {
            this.mLinkUsageState = linkUsageState;
        }
    }

    public SparseArray<LinkMetrics> mLastLinkMetrics = new SparseArray<>();

    @VisibleForTesting
    static class NetworkSelectionExperimentResults {
        public static final int MAX_CHOICES = 10;

        public IntCounter sameSelectionNumChoicesCounter = new IntCounter(0, MAX_CHOICES);
        public IntCounter differentSelectionNumChoicesCounter = new IntCounter(0, MAX_CHOICES);

        @Override
        public String toString() {
            return "NetworkSelectionExperimentResults{"
                    + "sameSelectionNumChoicesCounter="
                    + sameSelectionNumChoicesCounter
                    + ", differentSelectionNumChoicesCounter="
                    + differentSelectionNumChoicesCounter
                    + '}';
        }
    }

    private static class SessionData {
        private String mSsid;
        private long mSessionStartTimeMillis;
        private long mSessionEndTimeMillis;
        private int mBand;
        private int mAuthType;
        private ConnectionEvent mConnectionEvent;
        private long mLastRoamCompleteMillis;

        SessionData(ConnectionEvent connectionEvent, String ssid, long sessionStartTimeMillis,
                int band, int authType) {
            mConnectionEvent = connectionEvent;
            mSsid = ssid;
            mSessionStartTimeMillis = sessionStartTimeMillis;
            mBand = band;
            mAuthType = authType;
            mLastRoamCompleteMillis = sessionStartTimeMillis;
        }
    }

    /**
     * Sets the timestamp after roaming is complete.
     */
    public void onRoamComplete() {
        if (mCurrentSession != null) {
            mCurrentSession.mLastRoamCompleteMillis = mClock.getElapsedSinceBootMillis();
        }
    }

    class RouterFingerPrint {
        private final WifiMetricsProto.RouterFingerPrint mRouterFingerPrintProto =
                new WifiMetricsProto.RouterFingerPrint();
        // Additional parameters which is not captured in WifiMetricsProto.RouterFingerPrint.
        private boolean mIsFrameworkInitiatedRoaming = false;
        private @WifiConfiguration.SecurityType int mSecurityMode =
                WifiConfiguration.SECURITY_TYPE_OPEN;
        private boolean mIsIncorrectlyConfiguredAsHidden = false;
        private int mWifiStandard = WifiMode.MODE_UNDEFINED;
        private boolean mIs11bSupported = false;
        private boolean mIsMboSupported = false;
        private boolean mIsOceSupported = false;
        private boolean mIsFilsSupported = false;
        private boolean mIsIndividualTwtSupported = false;
        private boolean mIsBroadcastTwtSupported = false;
        private boolean mIsRestrictedTwtSupported = false;
        private boolean mIsTwtRequired = false;
        private boolean mIs11AzSupported = false;
        private boolean mIs11McSupported = false;
        private boolean mIsEcpsPriorityAccessSupported = false;
        private NetworkDetail.HSRelease mHsRelease = NetworkDetail.HSRelease.Unknown;
        private ApType6GHz mApType6GHz = ApType6GHz.AP_TYPE_6GHZ_UNKNOWN;

        public String toString() {
            StringBuilder sb = new StringBuilder();
            synchronized (mLock) {
                sb.append("mConnectionEvent.roamType=" + mRouterFingerPrintProto.roamType);
                sb.append(", mChannelInfo=" + mRouterFingerPrintProto.channelInfo);
                sb.append(", mDtim=" + mRouterFingerPrintProto.dtim);
                sb.append(", mAuthentication=" + mRouterFingerPrintProto.authentication);
                sb.append(", mHidden=" + mRouterFingerPrintProto.hidden);
                sb.append(", mRouterTechnology=" + mRouterFingerPrintProto.routerTechnology);
                sb.append(", mSupportsIpv6=" + mRouterFingerPrintProto.supportsIpv6);
                sb.append(", mEapMethod=" + mRouterFingerPrintProto.eapMethod);
                sb.append(", mAuthPhase2Method=" + mRouterFingerPrintProto.authPhase2Method);
                sb.append(", mOcspType=" + mRouterFingerPrintProto.ocspType);
                sb.append(", mPmkCache=" + mRouterFingerPrintProto.pmkCacheEnabled);
                sb.append(", mMaxSupportedTxLinkSpeedMbps=" + mRouterFingerPrintProto
                        .maxSupportedTxLinkSpeedMbps);
                sb.append(", mMaxSupportedRxLinkSpeedMbps=" + mRouterFingerPrintProto
                        .maxSupportedRxLinkSpeedMbps);
                sb.append(", mIsFrameworkInitiatedRoaming=" + mIsFrameworkInitiatedRoaming);
                sb.append(", mIsIncorrectlyConfiguredAsHidden=" + mIsIncorrectlyConfiguredAsHidden);
                sb.append(", mWifiStandard=" + mWifiStandard);
                sb.append(", mIs11bSupported=" + mIs11bSupported);
                sb.append(", mIsMboSupported=" + mIsMboSupported);
                sb.append(", mIsOceSupported=" + mIsOceSupported);
                sb.append(", mIsFilsSupported=" + mIsFilsSupported);
                sb.append(", mIsIndividualTwtSupported=" + mIsIndividualTwtSupported);
                sb.append(", mIsBroadcastTwtSupported=" + mIsBroadcastTwtSupported);
                sb.append(", mIsRestrictedTwtSupported=" + mIsRestrictedTwtSupported);
                sb.append(", mIsTwtRequired=" + mIsTwtRequired);
                sb.append(", mIs11mcSupported=" + mIs11McSupported);
                sb.append(", mIs11azSupported=" + mIs11AzSupported);
                sb.append(", mApType6Ghz=" + mApType6GHz);
                sb.append(", mIsEcpsPriorityAccessSupported=" + mIsEcpsPriorityAccessSupported);
                sb.append(", mHsRelease=" + mHsRelease);
            }
            return sb.toString();
        }

        public void setPmkCache(boolean isEnabled) {
            synchronized (mLock) {
                mRouterFingerPrintProto.pmkCacheEnabled = isEnabled;
            }
        }

        public void setMaxSupportedLinkSpeedMbps(int maxSupportedTxLinkSpeedMbps,
                int maxSupportedRxLinkSpeedMbps) {
            synchronized (mLock) {
                mRouterFingerPrintProto.maxSupportedTxLinkSpeedMbps = maxSupportedTxLinkSpeedMbps;
                mRouterFingerPrintProto.maxSupportedRxLinkSpeedMbps = maxSupportedRxLinkSpeedMbps;
            }
        }
    }
    private int getEapMethodProto(int eapMethod) {
        switch (eapMethod) {
            case WifiEnterpriseConfig.Eap.WAPI_CERT:
                return WifiMetricsProto.RouterFingerPrint.TYPE_EAP_WAPI_CERT;
            case WifiEnterpriseConfig.Eap.TLS:
                return WifiMetricsProto.RouterFingerPrint.TYPE_EAP_TLS;
            case WifiEnterpriseConfig.Eap.UNAUTH_TLS:
                return WifiMetricsProto.RouterFingerPrint.TYPE_EAP_UNAUTH_TLS;
            case WifiEnterpriseConfig.Eap.PEAP:
                return WifiMetricsProto.RouterFingerPrint.TYPE_EAP_PEAP;
            case WifiEnterpriseConfig.Eap.PWD:
                return WifiMetricsProto.RouterFingerPrint.TYPE_EAP_PWD;
            case WifiEnterpriseConfig.Eap.TTLS:
                return WifiMetricsProto.RouterFingerPrint.TYPE_EAP_TTLS;
            case WifiEnterpriseConfig.Eap.SIM:
                return WifiMetricsProto.RouterFingerPrint.TYPE_EAP_SIM;
            case WifiEnterpriseConfig.Eap.AKA:
                return WifiMetricsProto.RouterFingerPrint.TYPE_EAP_AKA;
            case WifiEnterpriseConfig.Eap.AKA_PRIME:
                return WifiMetricsProto.RouterFingerPrint.TYPE_EAP_AKA_PRIME;
            default:
                return WifiMetricsProto.RouterFingerPrint.TYPE_EAP_UNKNOWN;
        }
    }

    private static int getAuthPhase2MethodProto(int phase2Method) {
        switch (phase2Method) {
            case WifiEnterpriseConfig.Phase2.PAP:
                return WifiMetricsProto.RouterFingerPrint.TYPE_PHASE2_PAP;
            case WifiEnterpriseConfig.Phase2.MSCHAP:
                return WifiMetricsProto.RouterFingerPrint.TYPE_PHASE2_MSCHAP;
            case WifiEnterpriseConfig.Phase2.MSCHAPV2:
                return WifiMetricsProto.RouterFingerPrint.TYPE_PHASE2_MSCHAPV2;
            case WifiEnterpriseConfig.Phase2.GTC:
                return WifiMetricsProto.RouterFingerPrint.TYPE_PHASE2_GTC;
            case WifiEnterpriseConfig.Phase2.SIM:
                return WifiMetricsProto.RouterFingerPrint.TYPE_PHASE2_SIM;
            case WifiEnterpriseConfig.Phase2.AKA:
                return WifiMetricsProto.RouterFingerPrint.TYPE_PHASE2_AKA;
            case WifiEnterpriseConfig.Phase2.AKA_PRIME:
                return WifiMetricsProto.RouterFingerPrint.TYPE_PHASE2_AKA_PRIME;
            default:
                return WifiMetricsProto.RouterFingerPrint.TYPE_PHASE2_NONE;
        }
    }

    private int getOcspTypeProto(int ocspType) {
        switch (ocspType) {
            case WifiEnterpriseConfig.OCSP_NONE:
                return WifiMetricsProto.RouterFingerPrint.TYPE_OCSP_NONE;
            case WifiEnterpriseConfig.OCSP_REQUEST_CERT_STATUS:
                return WifiMetricsProto.RouterFingerPrint.TYPE_OCSP_REQUEST_CERT_STATUS;
            case WifiEnterpriseConfig.OCSP_REQUIRE_CERT_STATUS:
                return WifiMetricsProto.RouterFingerPrint.TYPE_OCSP_REQUIRE_CERT_STATUS;
            case WifiEnterpriseConfig.OCSP_REQUIRE_ALL_NON_TRUSTED_CERTS_STATUS:
                return WifiMetricsProto.RouterFingerPrint
                        .TYPE_OCSP_REQUIRE_ALL_NON_TRUSTED_CERTS_STATUS;
            default:
                return WifiMetricsProto.RouterFingerPrint.TYPE_OCSP_NONE;
        }
    }

    class BssidBlocklistStats {
        public IntCounter networkSelectionFilteredBssidCount = new IntCounter();
        public int numHighMovementConnectionSkipped = 0;
        public int numHighMovementConnectionStarted = 0;
        private final IntCounter mBlockedBssidPerReasonCount = new IntCounter();
        private final IntCounter mBlockedConfigurationPerReasonCount = new IntCounter();

        public WifiMetricsProto.BssidBlocklistStats toProto() {
            WifiMetricsProto.BssidBlocklistStats proto = new WifiMetricsProto.BssidBlocklistStats();
            proto.networkSelectionFilteredBssidCount = networkSelectionFilteredBssidCount.toProto();
            proto.highMovementMultipleScansFeatureEnabled = mContext.getResources().getBoolean(
                    R.bool.config_wifiHighMovementNetworkSelectionOptimizationEnabled);
            proto.numHighMovementConnectionSkipped = numHighMovementConnectionSkipped;
            proto.numHighMovementConnectionStarted = numHighMovementConnectionStarted;
            proto.bssidBlocklistPerReasonCount = mBlockedBssidPerReasonCount.toProto();
            proto.wifiConfigBlocklistPerReasonCount = mBlockedConfigurationPerReasonCount.toProto();
            return proto;
        }

        public void incrementBssidBlocklistCount(int blockReason) {
            mBlockedBssidPerReasonCount.increment(blockReason);
        }

        public void incrementWificonfigurationBlocklistCount(int blockReason) {
            mBlockedConfigurationPerReasonCount.increment(blockReason);
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("networkSelectionFilteredBssidCount=" + networkSelectionFilteredBssidCount);
            sb.append("\nmBlockedBssidPerReasonCount=" + mBlockedBssidPerReasonCount);
            sb.append("\nmBlockedConfigurationPerReasonCount="
                    + mBlockedConfigurationPerReasonCount);

            sb.append(", highMovementMultipleScansFeatureEnabled="
                    + mContext.getResources().getBoolean(
                            R.bool.config_wifiHighMovementNetworkSelectionOptimizationEnabled));
            sb.append(", numHighMovementConnectionSkipped=" + numHighMovementConnectionSkipped);
            sb.append(", numHighMovementConnectionStarted=" + numHighMovementConnectionStarted);
            sb.append(", mBlockedBssidPerReasonCount=" + mBlockedBssidPerReasonCount);
            sb.append(", mBlockedConfigurationPerReasonCount="
                    + mBlockedConfigurationPerReasonCount);
            return sb.toString();
        }
    }

    class ConnectionDurationStats {
        private int mConnectionDurationCellularDataOffMs;
        private int mConnectionDurationSufficientThroughputMs;
        private int mConnectionDurationInSufficientThroughputMs;
        private int mConnectionDurationInSufficientThroughputDefaultWifiMs;

        public WifiMetricsProto.ConnectionDurationStats toProto() {
            WifiMetricsProto.ConnectionDurationStats proto =
                    new WifiMetricsProto.ConnectionDurationStats();
            proto.totalTimeSufficientThroughputMs = mConnectionDurationSufficientThroughputMs;
            proto.totalTimeInsufficientThroughputMs = mConnectionDurationInSufficientThroughputMs;
            proto.totalTimeInsufficientThroughputDefaultWifiMs =
                    mConnectionDurationInSufficientThroughputDefaultWifiMs;
            proto.totalTimeCellularDataOffMs = mConnectionDurationCellularDataOffMs;
            return proto;
        }
        public void clear() {
            mConnectionDurationCellularDataOffMs = 0;
            mConnectionDurationSufficientThroughputMs = 0;
            mConnectionDurationInSufficientThroughputMs = 0;
            mConnectionDurationInSufficientThroughputDefaultWifiMs = 0;
        }
        public void incrementDurationCount(int timeDeltaLastTwoPollsMs,
                boolean isThroughputSufficient, boolean isCellularDataAvailable,
                boolean isDefaultOnWifi) {
            if (!isCellularDataAvailable) {
                mConnectionDurationCellularDataOffMs += timeDeltaLastTwoPollsMs;
            } else {
                if (isThroughputSufficient) {
                    mConnectionDurationSufficientThroughputMs += timeDeltaLastTwoPollsMs;
                } else {
                    mConnectionDurationInSufficientThroughputMs += timeDeltaLastTwoPollsMs;
                    if (isDefaultOnWifi) {
                        mConnectionDurationInSufficientThroughputDefaultWifiMs +=
                                timeDeltaLastTwoPollsMs;
                    }
                }
            }
        }
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("connectionDurationSufficientThroughputMs=")
                    .append(mConnectionDurationSufficientThroughputMs)
                    .append(", connectionDurationInSufficientThroughputMs=")
                    .append(mConnectionDurationInSufficientThroughputMs)
                    .append(", connectionDurationInSufficientThroughputDefaultWifiMs=")
                    .append(mConnectionDurationInSufficientThroughputDefaultWifiMs)
                    .append(", connectionDurationCellularDataOffMs=")
                    .append(mConnectionDurationCellularDataOffMs);
            return sb.toString();
        }
    }

    class WifiStatusBuilder {
        private int mNetworkId = WifiConfiguration.INVALID_NETWORK_ID;
        private boolean mConnected;
        private boolean mValidated;
        private int mRssi;
        private int mEstimatedTxKbps;
        private int mEstimatedRxKbps;
        private boolean mIsStuckDueToUserChoice;

        public void setNetworkId(int networkId) {
            mNetworkId = networkId;
        }

        public int getNetworkId() {
            return mNetworkId;
        }

        public void setConnected(boolean connected) {
            mConnected = connected;
        }

        public void setValidated(boolean validated) {
            mValidated = validated;
        }

        public void setRssi(int rssi) {
            mRssi = rssi;
        }

        public void setEstimatedTxKbps(int estimatedTxKbps) {
            mEstimatedTxKbps = estimatedTxKbps;
        }

        public void setEstimatedRxKbps(int estimatedRxKbps) {
            mEstimatedRxKbps = estimatedRxKbps;
        }

        public void setUserChoice(boolean userChoice) {
            mIsStuckDueToUserChoice = userChoice;
        }

        public WifiStatus toProto() {
            WifiStatus result = new WifiStatus();
            result.isConnected = mConnected;
            result.isValidated = mValidated;
            result.lastRssi = mRssi;
            result.estimatedTxKbps = mEstimatedTxKbps;
            result.estimatedRxKbps = mEstimatedRxKbps;
            result.isStuckDueToUserConnectChoice = mIsStuckDueToUserChoice;
            return result;
        }
    }

    private NetworkDisableReason convertToNetworkDisableReason(
            WifiConfiguration config, Set<Integer> bssidBlocklistReasons) {
        NetworkSelectionStatus status = config.getNetworkSelectionStatus();
        NetworkDisableReason result = new NetworkDisableReason();
        if (config.allowAutojoin) {
            if (!status.isNetworkEnabled()) {
                result.disableReason =
                        MetricsUtils.convertNetworkSelectionDisableReasonToWifiProtoEnum(
                                status.getNetworkSelectionDisableReason());
                if (status.isNetworkPermanentlyDisabled()) {
                    result.configPermanentlyDisabled = true;
                } else {
                    result.configTemporarilyDisabled = true;
                }
            }
        } else {
            result.disableReason = NetworkDisableReason.REASON_AUTO_JOIN_DISABLED;
            result.configPermanentlyDisabled = true;
        }

        int[] convertedBssidBlockReasons = bssidBlocklistReasons.stream()
                .mapToInt(i -> MetricsUtils.convertBssidBlocklistReasonToWifiProtoEnum(i))
                .toArray();
        if (convertedBssidBlockReasons.length > 0) {
            result.bssidDisableReasons = convertedBssidBlockReasons;
        }
        return result;
    }

    class UserActionEventWithTime {
        private UserActionEvent mUserActionEvent;
        private long mWallClockTimeMs = 0; // wall clock time for debugging only

        UserActionEventWithTime(int eventType, TargetNetworkInfo targetNetworkInfo) {
            mUserActionEvent = new UserActionEvent();
            mUserActionEvent.eventType = eventType;
            mUserActionEvent.startTimeMillis = mClock.getElapsedSinceBootMillis();
            mWallClockTimeMs = mClock.getWallClockMillis();
            mUserActionEvent.targetNetworkInfo = targetNetworkInfo;
            mUserActionEvent.wifiStatus = mWifiStatusBuilder.toProto();
        }

        UserActionEventWithTime(int eventType, int targetNetId) {
            this(eventType, null);
            if (targetNetId >= 0) {
                WifiConfiguration config = mWifiConfigManager.getConfiguredNetwork(targetNetId);
                if (config != null) {
                    TargetNetworkInfo networkInfo = new TargetNetworkInfo();
                    networkInfo.isEphemeral = config.isEphemeral();
                    networkInfo.isPasspoint = config.isPasspoint();
                    mUserActionEvent.targetNetworkInfo = networkInfo;
                    mUserActionEvent.networkDisableReason = convertToNetworkDisableReason(
                            config, mWifiBlocklistMonitor.getFailureReasonsForSsid(config.SSID));
                }
            }
        }

        public String toString() {
            StringBuilder sb = new StringBuilder();
            Calendar c = Calendar.getInstance();
            c.setTimeInMillis(mWallClockTimeMs);
            sb.append(StringUtil.calendarToString(c));
            String eventType = "UNKNOWN";
            switch (mUserActionEvent.eventType) {
                case UserActionEvent.EVENT_FORGET_WIFI:
                    eventType = "EVENT_FORGET_WIFI";
                    break;
                case UserActionEvent.EVENT_DISCONNECT_WIFI:
                    eventType = "EVENT_DISCONNECT_WIFI";
                    break;
                case UserActionEvent.EVENT_CONFIGURE_METERED_STATUS_METERED:
                    eventType = "EVENT_CONFIGURE_METERED_STATUS_METERED";
                    break;
                case UserActionEvent.EVENT_CONFIGURE_METERED_STATUS_UNMETERED:
                    eventType = "EVENT_CONFIGURE_METERED_STATUS_UNMETERED";
                    break;
                case UserActionEvent.EVENT_CONFIGURE_METERED_STATUS_AUTO:
                    eventType = "EVENT_CONFIGURE_METERED_STATUS_AUTO";
                    break;
                case UserActionEvent.EVENT_CONFIGURE_MAC_RANDOMIZATION_ON:
                    eventType = "EVENT_CONFIGURE_MAC_RANDOMIZATION_ON";
                    break;
                case UserActionEvent.EVENT_CONFIGURE_MAC_RANDOMIZATION_OFF:
                    eventType = "EVENT_CONFIGURE_MAC_RANDOMIZATION_OFF";
                    break;
                case UserActionEvent.EVENT_CONFIGURE_AUTO_CONNECT_ON:
                    eventType = "EVENT_CONFIGURE_AUTO_CONNECT_ON";
                    break;
                case UserActionEvent.EVENT_CONFIGURE_AUTO_CONNECT_OFF:
                    eventType = "EVENT_CONFIGURE_AUTO_CONNECT_OFF";
                    break;
                case UserActionEvent.EVENT_TOGGLE_WIFI_ON:
                    eventType = "EVENT_TOGGLE_WIFI_ON";
                    break;
                case UserActionEvent.EVENT_TOGGLE_WIFI_OFF:
                    eventType = "EVENT_TOGGLE_WIFI_OFF";
                    break;
                case UserActionEvent.EVENT_MANUAL_CONNECT:
                    eventType = "EVENT_MANUAL_CONNECT";
                    break;
                case UserActionEvent.EVENT_ADD_OR_UPDATE_NETWORK:
                    eventType = "EVENT_ADD_OR_UPDATE_NETWORK";
                    break;
                case UserActionEvent.EVENT_RESTART_WIFI_SUB_SYSTEM:
                    eventType = "EVENT_RESTART_WIFI_SUB_SYSTEM";
                    break;
            }
            sb.append(" eventType=").append(eventType);
            sb.append(" startTimeMillis=").append(mUserActionEvent.startTimeMillis);
            TargetNetworkInfo networkInfo = mUserActionEvent.targetNetworkInfo;
            if (networkInfo != null) {
                sb.append(" isEphemeral=").append(networkInfo.isEphemeral);
                sb.append(" isPasspoint=").append(networkInfo.isPasspoint);
            }
            WifiStatus wifiStatus = mUserActionEvent.wifiStatus;
            if (wifiStatus != null) {
                sb.append("\nWifiStatus: isConnected=").append(wifiStatus.isConnected);
                sb.append(" isValidated=").append(wifiStatus.isValidated);
                sb.append(" lastRssi=").append(wifiStatus.lastRssi);
                sb.append(" estimatedTxKbps=").append(wifiStatus.estimatedTxKbps);
                sb.append(" estimatedRxKbps=").append(wifiStatus.estimatedRxKbps);
                sb.append(" isStuckDueToUserConnectChoice=")
                        .append(wifiStatus.isStuckDueToUserConnectChoice);
            }
            NetworkDisableReason disableReason = mUserActionEvent.networkDisableReason;
            if (disableReason != null) {
                sb.append("\nNetworkDisableReason: DisableReason=")
                        .append(disableReason.disableReason);
                sb.append(" configTemporarilyDisabled=")
                        .append(disableReason.configTemporarilyDisabled);
                sb.append(" configPermanentlyDisabled=")
                        .append(disableReason.configPermanentlyDisabled);
                sb.append(" bssidDisableReasons=")
                        .append(Arrays.toString(disableReason.bssidDisableReasons));
            }
            return sb.toString();
        }

        public UserActionEvent toProto() {
            return mUserActionEvent;
        }
    }

    /**
     * Log event, tracking the start time, end time and result of a wireless connection attempt.
     */
    class ConnectionEvent {
        final WifiMetricsProto.ConnectionEvent mConnectionEvent;
        //<TODO> Move these constants into a wifi.proto Enum, and create a new Failure Type field
        //covering more than just l2 failures. see b/27652362
        /**
         * Failure codes, used for the 'level_2_failure_code' Connection event field (covers a lot
         * more failures than just l2 though, since the proto does not have a place to log
         * framework failures)
         */
        // Failure is unknown
        public static final int FAILURE_UNKNOWN = 0;
        // NONE
        public static final int FAILURE_NONE = 1;
        // ASSOCIATION_REJECTION_EVENT
        public static final int FAILURE_ASSOCIATION_REJECTION = 2;
        // AUTHENTICATION_FAILURE_EVENT
        public static final int FAILURE_AUTHENTICATION_FAILURE = 3;
        // SSID_TEMP_DISABLED (Also Auth failure)
        public static final int FAILURE_SSID_TEMP_DISABLED = 4;
        // reconnect() or reassociate() call to WifiNative failed
        public static final int FAILURE_CONNECT_NETWORK_FAILED = 5;
        // NETWORK_DISCONNECTION_EVENT
        public static final int FAILURE_NETWORK_DISCONNECTION = 6;
        // NEW_CONNECTION_ATTEMPT before previous finished
        public static final int FAILURE_NEW_CONNECTION_ATTEMPT = 7;
        // New connection attempt to the same network & bssid
        public static final int FAILURE_REDUNDANT_CONNECTION_ATTEMPT = 8;
        // Roam Watchdog timer triggered (Roaming timed out)
        public static final int FAILURE_ROAM_TIMEOUT = 9;
        // DHCP failure
        public static final int FAILURE_DHCP = 10;
        // ASSOCIATION_TIMED_OUT
        public static final int FAILURE_ASSOCIATION_TIMED_OUT = 11;
        // NETWORK_NOT_FOUND
        public static final int FAILURE_NETWORK_NOT_FOUND = 12;
        // Connection attempt aborted by the watchdog because the AP didn't respond.
        public static final int FAILURE_NO_RESPONSE = 13;

        RouterFingerPrint mRouterFingerPrint;
        private String mConfigSsid;
        private String mConfigBssid;
        private int mWifiState;
        private boolean mScreenOn;
        private int mAuthType;
        private int mTrigger;
        private boolean mHasEverConnected;
        private boolean mIsCarrierWifi;
        private boolean mIsOobPseudonymEnabled;
        private int mRole;
        private int mUid;
        private int mCarrierId;
        private int mEapType;
        private int mPhase2Method;
        private int mPasspointRoamingType;
        private int mTofuConnectionState;

        private ConnectionEvent() {
            mConnectionEvent = new WifiMetricsProto.ConnectionEvent();
            mRouterFingerPrint = new RouterFingerPrint();
            mConnectionEvent.routerFingerprint = mRouterFingerPrint.mRouterFingerPrintProto;
            mConfigSsid = "<NULL>";
            mConfigBssid = "<NULL>";
            mWifiState = WifiMetricsProto.WifiLog.WIFI_UNKNOWN;
            mScreenOn = false;
            mIsCarrierWifi = false;
            mIsOobPseudonymEnabled = false;
        }

        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("startTime=");
            Calendar c = Calendar.getInstance();
            synchronized (mLock) {
                c.setTimeInMillis(mConnectionEvent.startTimeMillis);
                if (mConnectionEvent.startTimeMillis == 0) {
                    sb.append("            <null>");
                } else {
                    sb.append(StringUtil.calendarToString(c));
                }
                sb.append(", SSID=");
                sb.append(mConfigSsid);
                sb.append(", BSSID=");
                sb.append(mConfigBssid);
                sb.append(", durationMillis=");
                sb.append(mConnectionEvent.durationTakenToConnectMillis);
                sb.append(", roamType=");
                switch(mConnectionEvent.roamType) {
                    case 1:
                        sb.append("ROAM_NONE");
                        break;
                    case 2:
                        sb.append("ROAM_DBDC");
                        break;
                    case 3:
                        sb.append("ROAM_ENTERPRISE");
                        break;
                    case 4:
                        sb.append("ROAM_USER_SELECTED");
                        break;
                    case 5:
                        sb.append("ROAM_UNRELATED");
                        break;
                    default:
                        sb.append("ROAM_UNKNOWN");
                }
                sb.append(", connectionResult=");
                sb.append(mConnectionEvent.connectionResult);
                sb.append(", level2FailureCode=");
                switch(mConnectionEvent.level2FailureCode) {
                    case FAILURE_NONE:
                        sb.append("NONE");
                        break;
                    case FAILURE_ASSOCIATION_REJECTION:
                        sb.append("ASSOCIATION_REJECTION");
                        break;
                    case FAILURE_AUTHENTICATION_FAILURE:
                        sb.append("AUTHENTICATION_FAILURE");
                        break;
                    case FAILURE_SSID_TEMP_DISABLED:
                        sb.append("SSID_TEMP_DISABLED");
                        break;
                    case FAILURE_CONNECT_NETWORK_FAILED:
                        sb.append("CONNECT_NETWORK_FAILED");
                        break;
                    case FAILURE_NETWORK_DISCONNECTION:
                        sb.append("NETWORK_DISCONNECTION");
                        break;
                    case FAILURE_NEW_CONNECTION_ATTEMPT:
                        sb.append("NEW_CONNECTION_ATTEMPT");
                        break;
                    case FAILURE_REDUNDANT_CONNECTION_ATTEMPT:
                        sb.append("REDUNDANT_CONNECTION_ATTEMPT");
                        break;
                    case FAILURE_ROAM_TIMEOUT:
                        sb.append("ROAM_TIMEOUT");
                        break;
                    case FAILURE_DHCP:
                        sb.append("DHCP");
                        break;
                    case FAILURE_ASSOCIATION_TIMED_OUT:
                        sb.append("ASSOCIATION_TIMED_OUT");
                        break;
                    case FAILURE_NETWORK_NOT_FOUND:
                        sb.append("FAILURE_NETWORK_NOT_FOUND");
                        break;
                    case FAILURE_NO_RESPONSE:
                        sb.append("FAILURE_NO_RESPONSE");
                        break;
                    default:
                        sb.append("UNKNOWN");
                        break;
                }
                sb.append(", connectivityLevelFailureCode=");
                switch(mConnectionEvent.connectivityLevelFailureCode) {
                    case WifiMetricsProto.ConnectionEvent.HLF_NONE:
                        sb.append("NONE");
                        break;
                    case WifiMetricsProto.ConnectionEvent.HLF_DHCP:
                        sb.append("DHCP");
                        break;
                    case WifiMetricsProto.ConnectionEvent.HLF_NO_INTERNET:
                        sb.append("NO_INTERNET");
                        break;
                    case WifiMetricsProto.ConnectionEvent.HLF_UNWANTED:
                        sb.append("UNWANTED");
                        break;
                    default:
                        sb.append("UNKNOWN");
                        break;
                }
                sb.append(", signalStrength=");
                sb.append(mConnectionEvent.signalStrength);
                sb.append(", wifiState=");
                switch(mWifiState) {
                    case WifiMetricsProto.WifiLog.WIFI_DISABLED:
                        sb.append("WIFI_DISABLED");
                        break;
                    case WifiMetricsProto.WifiLog.WIFI_DISCONNECTED:
                        sb.append("WIFI_DISCONNECTED");
                        break;
                    case WifiMetricsProto.WifiLog.WIFI_ASSOCIATED:
                        sb.append("WIFI_ASSOCIATED");
                        break;
                    default:
                        sb.append("WIFI_UNKNOWN");
                        break;
                }
                sb.append(", screenOn=");
                sb.append(mScreenOn);
                sb.append(", mRouterFingerprint=");
                sb.append(mRouterFingerPrint.toString());
                sb.append(", useRandomizedMac=");
                sb.append(mConnectionEvent.useRandomizedMac);
                sb.append(", useAggressiveMac=" + mConnectionEvent.useAggressiveMac);
                sb.append(", connectionNominator=");
                switch (mConnectionEvent.connectionNominator) {
                    case WifiMetricsProto.ConnectionEvent.NOMINATOR_UNKNOWN:
                        sb.append("NOMINATOR_UNKNOWN");
                        break;
                    case WifiMetricsProto.ConnectionEvent.NOMINATOR_MANUAL:
                        sb.append("NOMINATOR_MANUAL");
                        break;
                    case WifiMetricsProto.ConnectionEvent.NOMINATOR_SAVED:
                        sb.append("NOMINATOR_SAVED");
                        break;
                    case WifiMetricsProto.ConnectionEvent.NOMINATOR_SUGGESTION:
                        sb.append("NOMINATOR_SUGGESTION");
                        break;
                    case WifiMetricsProto.ConnectionEvent.NOMINATOR_PASSPOINT:
                        sb.append("NOMINATOR_PASSPOINT");
                        break;
                    case WifiMetricsProto.ConnectionEvent.NOMINATOR_CARRIER:
                        sb.append("NOMINATOR_CARRIER");
                        break;
                    case WifiMetricsProto.ConnectionEvent.NOMINATOR_EXTERNAL_SCORED:
                        sb.append("NOMINATOR_EXTERNAL_SCORED");
                        break;
                    case WifiMetricsProto.ConnectionEvent.NOMINATOR_SPECIFIER:
                        sb.append("NOMINATOR_SPECIFIER");
                        break;
                    case WifiMetricsProto.ConnectionEvent.NOMINATOR_SAVED_USER_CONNECT_CHOICE:
                        sb.append("NOMINATOR_SAVED_USER_CONNECT_CHOICE");
                        break;
                    case WifiMetricsProto.ConnectionEvent.NOMINATOR_OPEN_NETWORK_AVAILABLE:
                        sb.append("NOMINATOR_OPEN_NETWORK_AVAILABLE");
                        break;
                    default:
                        sb.append("UnrecognizedNominator(" + mConnectionEvent.connectionNominator
                                + ")");
                }
                sb.append(", networkSelectorExperimentId=");
                sb.append(mConnectionEvent.networkSelectorExperimentId);
                sb.append(", numBssidInBlocklist=" + mConnectionEvent.numBssidInBlocklist);
                sb.append(", level2FailureReason=");
                switch(mConnectionEvent.level2FailureReason) {
                    case WifiMetricsProto.ConnectionEvent.AUTH_FAILURE_NONE:
                        sb.append("AUTH_FAILURE_NONE");
                        break;
                    case WifiMetricsProto.ConnectionEvent.AUTH_FAILURE_TIMEOUT:
                        sb.append("AUTH_FAILURE_TIMEOUT");
                        break;
                    case WifiMetricsProto.ConnectionEvent.AUTH_FAILURE_WRONG_PSWD:
                        sb.append("AUTH_FAILURE_WRONG_PSWD");
                        break;
                    case WifiMetricsProto.ConnectionEvent.AUTH_FAILURE_EAP_FAILURE:
                        sb.append("AUTH_FAILURE_EAP_FAILURE");
                        break;
                    case WifiMetricsProto.ConnectionEvent.DISCONNECTION_NON_LOCAL:
                        sb.append("DISCONNECTION_NON_LOCAL");
                        break;
                    default:
                        sb.append("FAILURE_REASON_UNKNOWN");
                        break;
                }
                sb.append(", networkType=");
                switch(mConnectionEvent.networkType) {
                    case WifiMetricsProto.ConnectionEvent.TYPE_UNKNOWN:
                        sb.append("TYPE_UNKNOWN");
                        break;
                    case WifiMetricsProto.ConnectionEvent.TYPE_WPA2:
                        sb.append("TYPE_WPA2");
                        break;
                    case WifiMetricsProto.ConnectionEvent.TYPE_WPA3:
                        sb.append("TYPE_WPA3");
                        break;
                    case WifiMetricsProto.ConnectionEvent.TYPE_PASSPOINT:
                        sb.append("TYPE_PASSPOINT");
                        break;
                    case WifiMetricsProto.ConnectionEvent.TYPE_EAP:
                        sb.append("TYPE_EAP");
                        break;
                    case WifiMetricsProto.ConnectionEvent.TYPE_OWE:
                        sb.append("TYPE_OWE");
                        break;
                    case WifiMetricsProto.ConnectionEvent.TYPE_OPEN:
                        sb.append("TYPE_OPEN");
                        break;
                    case WifiMetricsProto.ConnectionEvent.TYPE_WAPI:
                        sb.append("TYPE_WAPI");
                        break;
                }
                sb.append(", networkCreator=");
                switch (mConnectionEvent.networkCreator) {
                    case WifiMetricsProto.ConnectionEvent.CREATOR_UNKNOWN:
                        sb.append("CREATOR_UNKNOWN");
                        break;
                    case WifiMetricsProto.ConnectionEvent.CREATOR_USER:
                        sb.append("CREATOR_USER");
                        break;
                    case WifiMetricsProto.ConnectionEvent.CREATOR_CARRIER:
                        sb.append("CREATOR_CARRIER");
                        break;
                }
                sb.append(", numConsecutiveConnectionFailure="
                        + mConnectionEvent.numConsecutiveConnectionFailure);
                sb.append(", isOsuProvisioned=" + mConnectionEvent.isOsuProvisioned);
                sb.append(" interfaceName=").append(mConnectionEvent.interfaceName);
                sb.append(" interfaceRole=").append(
                        clientRoleEnumToString(mConnectionEvent.interfaceRole));
                sb.append(", isFirstConnectionAfterBoot="
                        + mConnectionEvent.isFirstConnectionAfterBoot);
                sb.append(", isCarrierWifi=" + mIsCarrierWifi);
                sb.append(", isOobPseudonymEnabled=" + mIsOobPseudonymEnabled);
                sb.append(", uid=" + mUid);
                return sb.toString();
            }
        }

        private void updateFromWifiConfiguration(WifiConfiguration config) {
            synchronized (mLock) {
                if (config != null) {
                    // Is this a hidden network
                    mRouterFingerPrint.mRouterFingerPrintProto.hidden = config.hiddenSSID;
                    // Config may not have a valid dtimInterval set yet, in which case dtim will be
                    // zero (These are only populated from beacon frame scan results, which are
                    // returned as scan results from the chip far less frequently than
                    // Probe-responses)
                    if (config.dtimInterval > 0) {
                        mRouterFingerPrint.mRouterFingerPrintProto.dtim = config.dtimInterval;
                    }

                    if (config.carrierId != TelephonyManager.UNKNOWN_CARRIER_ID) {
                        mIsCarrierWifi = true;
                    }

                    mConfigSsid = config.SSID;
                    // Get AuthType information from config (We do this again from ScanResult after
                    // associating with BSSID)
                    if (config.isSecurityType(WifiConfiguration.SECURITY_TYPE_OPEN)) {
                        mRouterFingerPrint.mRouterFingerPrintProto.authentication =
                                WifiMetricsProto.RouterFingerPrint.AUTH_OPEN;
                    } else if (config.isEnterprise()) {
                        mRouterFingerPrint.mRouterFingerPrintProto.authentication =
                                WifiMetricsProto.RouterFingerPrint.AUTH_ENTERPRISE;
                    } else {
                        mRouterFingerPrint.mRouterFingerPrintProto.authentication =
                                WifiMetricsProto.RouterFingerPrint.AUTH_PERSONAL;
                    }
                    mRouterFingerPrint.mRouterFingerPrintProto.passpoint = config.isPasspoint();
                    mRouterFingerPrint.mRouterFingerPrintProto.isPasspointHomeProvider =
                            config.isHomeProviderNetwork;
                    // If there's a ScanResult candidate associated with this config already, get it
                    // and log (more accurate) metrics from it
                    ScanResult candidate = config.getNetworkSelectionStatus().getCandidate();
                    if (candidate != null) {
                        updateMetricsFromScanResult(this, candidate);
                    }
                    if (mRouterFingerPrint.mRouterFingerPrintProto.authentication
                            == WifiMetricsProto.RouterFingerPrint.AUTH_ENTERPRISE
                            && config.enterpriseConfig != null) {
                        int eapMethod = config.enterpriseConfig.getEapMethod();
                        mRouterFingerPrint.mRouterFingerPrintProto.eapMethod =
                                getEapMethodProto(eapMethod);
                        int phase2Method = config.enterpriseConfig.getPhase2Method();
                        mRouterFingerPrint.mRouterFingerPrintProto.authPhase2Method =
                                getAuthPhase2MethodProto(phase2Method);
                        int ocspType = config.enterpriseConfig.getOcsp();
                        mRouterFingerPrint.mRouterFingerPrintProto.ocspType =
                                getOcspTypeProto(ocspType);
                    }
                    mTofuConnectionState = convertTofuConnectionStateToProto(config);
                }
            }
        }
    }

    class WifiOffMetrics {
        public int numWifiOff = 0;
        public int numWifiOffDeferring = 0;
        public int numWifiOffDeferringTimeout = 0;
        public final IntCounter wifiOffDeferringTimeHistogram = new IntCounter();

        public WifiMetricsProto.WifiOffMetrics toProto() {
            WifiMetricsProto.WifiOffMetrics proto =
                    new WifiMetricsProto.WifiOffMetrics();
            proto.numWifiOff = numWifiOff;
            proto.numWifiOffDeferring = numWifiOffDeferring;
            proto.numWifiOffDeferringTimeout = numWifiOffDeferringTimeout;
            proto.wifiOffDeferringTimeHistogram = wifiOffDeferringTimeHistogram.toProto();
            return proto;
        }

        public void clear() {
            numWifiOff = 0;
            numWifiOffDeferring = 0;
            numWifiOffDeferringTimeout = 0;
            wifiOffDeferringTimeHistogram.clear();
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("numWifiOff=")
                    .append(numWifiOff)
                    .append(", numWifiOffDeferring=")
                    .append(numWifiOffDeferring)
                    .append(", numWifiOffDeferringTimeout=")
                    .append(numWifiOffDeferringTimeout)
                    .append(", wifiOffDeferringTimeHistogram=")
                    .append(wifiOffDeferringTimeHistogram);
            return sb.toString();
        }
    }

    class SoftApConfigLimitationMetrics {
        // Collect the number of softap security setting reset to default during the restore
        public int numSecurityTypeResetToDefault = 0;
        // Collect the number of softap max client setting reset to default during the restore
        public int numMaxClientSettingResetToDefault = 0;
        // Collect the number of softap client control setting reset to default during the restore
        public int numClientControlByUserResetToDefault = 0;
        // Collect the max client setting when reach it cause client is blocked
        public final IntCounter maxClientSettingWhenReachHistogram = new IntCounter();

        public WifiMetricsProto.SoftApConfigLimitationMetrics toProto() {
            WifiMetricsProto.SoftApConfigLimitationMetrics proto =
                    new WifiMetricsProto.SoftApConfigLimitationMetrics();
            proto.numSecurityTypeResetToDefault = numSecurityTypeResetToDefault;
            proto.numMaxClientSettingResetToDefault = numMaxClientSettingResetToDefault;
            proto.numClientControlByUserResetToDefault = numClientControlByUserResetToDefault;
            proto.maxClientSettingWhenReachHistogram = maxClientSettingWhenReachHistogram.toProto();
            return proto;
        }

        public void clear() {
            numSecurityTypeResetToDefault = 0;
            numMaxClientSettingResetToDefault = 0;
            numClientControlByUserResetToDefault = 0;
            maxClientSettingWhenReachHistogram.clear();
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("numSecurityTypeResetToDefault=")
                    .append(numSecurityTypeResetToDefault)
                    .append(", numMaxClientSettingResetToDefault=")
                    .append(numMaxClientSettingResetToDefault)
                    .append(", numClientControlByUserResetToDefault=")
                    .append(numClientControlByUserResetToDefault)
                    .append(", maxClientSettingWhenReachHistogram=")
                    .append(maxClientSettingWhenReachHistogram);
            return sb.toString();
        }
    }

    class CarrierWifiMetrics {
        public int numConnectionSuccess = 0;
        public int numConnectionAuthFailure = 0;
        public int numConnectionNonAuthFailure = 0;

        public WifiMetricsProto.CarrierWifiMetrics toProto() {
            WifiMetricsProto.CarrierWifiMetrics proto =
                    new WifiMetricsProto.CarrierWifiMetrics();
            proto.numConnectionSuccess = numConnectionSuccess;
            proto.numConnectionAuthFailure = numConnectionAuthFailure;
            proto.numConnectionNonAuthFailure = numConnectionNonAuthFailure;
            return proto;
        }

        public void clear() {
            numConnectionSuccess = 0;
            numConnectionAuthFailure = 0;
            numConnectionNonAuthFailure = 0;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("numConnectionSuccess=")
                    .append(numConnectionSuccess)
                    .append(", numConnectionAuthFailure=")
                    .append(numConnectionAuthFailure)
                    .append(", numConnectionNonAuthFailure")
                    .append(numConnectionNonAuthFailure);
            return sb.toString();
        }
    }

    public WifiMetrics(
            Context context,
            FrameworkFacade facade,
            Clock clock,
            Looper looper,
            WifiAwareMetrics awareMetrics,
            RttMetrics rttMetrics,
            WifiPowerMetrics wifiPowerMetrics,
            WifiP2pMetrics wifiP2pMetrics,
            DppMetrics dppMetrics,
            WifiMonitor wifiMonitor,
            WifiDeviceStateChangeManager wifiDeviceStateChangeManager) {
        mContext = context;
        mFacade = facade;
        mClock = clock;
        mWifiState = WifiMetricsProto.WifiLog.WIFI_DISABLED;
        mRecordStartTimeSec = mClock.getElapsedSinceBootMillis() / 1000;
        mWifiAwareMetrics = awareMetrics;
        mRttMetrics = rttMetrics;
        mWifiPowerMetrics = wifiPowerMetrics;
        mWifiP2pMetrics = wifiP2pMetrics;
        mDppMetrics = dppMetrics;
        mWifiMonitor = wifiMonitor;
        mHandler = new Handler(looper) {
            public void handleMessage(Message msg) {
                synchronized (mLock) {
                    processMessage(msg);
                }
            }
        };

        mCurrentDeviceMobilityState = WifiManager.DEVICE_MOBILITY_STATE_UNKNOWN;
        DeviceMobilityStatePnoScanStats unknownStateStats =
                getOrCreateDeviceMobilityStatePnoScanStats(mCurrentDeviceMobilityState);
        unknownStateStats.numTimesEnteredState++;
        mCurrentDeviceMobilityStateStartMs = mClock.getElapsedSinceBootMillis();
        mCurrentDeviceMobilityStatePnoScanStartMs = -1;
        mOnWifiUsabilityListeners = new RemoteCallbackList<>();
        mScanMetrics = new ScanMetrics(context, clock);
        wifiDeviceStateChangeManager.registerStateChangeCallback(
                new WifiDeviceStateChangeManager.StateChangeCallback() {
                    @Override
                    public void onScreenStateChanged(boolean screenOn) {
                        setScreenState(screenOn);
                    }
                });
    }

    /** Sets internal ScoringParams member */
    public void setScoringParams(ScoringParams scoringParams) {
        mScoringParams = scoringParams;
    }

    /** Sets internal WifiConfigManager member */
    public void setWifiConfigManager(WifiConfigManager wifiConfigManager) {
        mWifiConfigManager = wifiConfigManager;
    }

    /** Sets internal WifiNetworkSelector member */
    public void setWifiNetworkSelector(WifiNetworkSelector wifiNetworkSelector) {
        mWifiNetworkSelector = wifiNetworkSelector;
    }

    /** Sets internal PasspointManager member */
    public void setPasspointManager(PasspointManager passpointManager) {
        mPasspointManager = passpointManager;
    }

    /** Sets internal WifiDataStall member */
    public void setWifiDataStall(WifiDataStall wifiDataStall) {
        mWifiDataStall = wifiDataStall;
    }

    /** Sets internal WifiBlocklistMonitor member */
    public void setWifiBlocklistMonitor(WifiBlocklistMonitor wifiBlocklistMonitor) {
        mWifiBlocklistMonitor = wifiBlocklistMonitor;
    }

    /** Sets internal WifiHealthMonitor member */
    public void setWifiHealthMonitor(WifiHealthMonitor wifiHealthMonitor) {
        mWifiHealthMonitor = wifiHealthMonitor;
    }

    /** Sets internal WifiScoreCard member */
    public void setWifiScoreCard(WifiScoreCard wifiScoreCard) {
        mWifiScoreCard = wifiScoreCard;
    }

    /** Sets internal WifiChannelUtilization member */
    public void setWifiChannelUtilization(WifiChannelUtilization wifiChannelUtilization) {
        mWifiChannelUtilization = wifiChannelUtilization;
    }

    /** Sets internal WifiSettingsStore member */
    public void setWifiSettingsStore(WifiSettingsStore wifiSettingsStore) {
        mWifiSettingsStore = wifiSettingsStore;
    }

    /** Sets internal ActiveModeWarden member */
    public void setActiveModeWarden(ActiveModeWarden activeModeWarden) {
        mActiveModeWarden = activeModeWarden;
        mActiveModeWarden.registerModeChangeCallback(new ModeChangeCallback());
    }

    /**
     * Implements callbacks that set the internal ifaceName to ClientRole mapping.
     */
    @VisibleForTesting
    private class ModeChangeCallback implements ActiveModeWarden.ModeChangeCallback {
        @Override
        public void onActiveModeManagerAdded(@NonNull ActiveModeManager activeModeManager) {
            if (!(activeModeManager instanceof ConcreteClientModeManager)) {
                return;
            }
            synchronized (mLock) {
                ConcreteClientModeManager clientModeManager =
                        (ConcreteClientModeManager) activeModeManager;
                mIfaceToRoleMap.put(clientModeManager.getInterfaceName(),
                        clientModeManager.getRole());
            }
        }

        @Override
        public void onActiveModeManagerRemoved(@NonNull ActiveModeManager activeModeManager) {
            if (!(activeModeManager instanceof ConcreteClientModeManager)) {
                return;
            }
            synchronized (mLock) {
                ConcreteClientModeManager clientModeManager =
                        (ConcreteClientModeManager) activeModeManager;
                mIfaceToRoleMap.remove(clientModeManager.getInterfaceName());
            }
        }

        @Override
        public void onActiveModeManagerRoleChanged(@NonNull ActiveModeManager activeModeManager) {
            if (!(activeModeManager instanceof ConcreteClientModeManager)) {
                return;
            }
            synchronized (mLock) {
                ConcreteClientModeManager clientModeManager =
                        (ConcreteClientModeManager) activeModeManager;
                mIfaceToRoleMap.put(clientModeManager.getInterfaceName(),
                        clientModeManager.getRole());
            }
        }
    }

    /**
     * Increment cumulative counters for link layer stats.
     * @param newStats
     */
    public void incrementWifiLinkLayerUsageStats(String ifaceName, WifiLinkLayerStats newStats) {
        // This is only collected for primary STA currently because RSSI polling is disabled for
        // non-primary STAs.
        if (!isPrimary(ifaceName)) {
            return;
        }
        if (newStats == null) {
            return;
        }
        if (mLastLinkLayerStats == null) {
            mLastLinkLayerStats = newStats;
            return;
        }
        if (!newLinkLayerStatsIsValid(mLastLinkLayerStats, newStats)) {
            // This could mean the radio chip is reset or the data is incorrectly reported.
            // Don't increment any counts and discard the possibly corrupt |newStats| completely.
            mLastLinkLayerStats = null;
            return;
        }
        mWifiLinkLayerUsageStats.loggingDurationMs +=
                (newStats.timeStampInMs - mLastLinkLayerStats.timeStampInMs);
        mWifiLinkLayerUsageStats.radioOnTimeMs += (newStats.on_time - mLastLinkLayerStats.on_time);
        mWifiLinkLayerUsageStats.radioTxTimeMs += (newStats.tx_time - mLastLinkLayerStats.tx_time);
        mWifiLinkLayerUsageStats.radioRxTimeMs += (newStats.rx_time - mLastLinkLayerStats.rx_time);
        mWifiLinkLayerUsageStats.radioScanTimeMs +=
                (newStats.on_time_scan - mLastLinkLayerStats.on_time_scan);
        mWifiLinkLayerUsageStats.radioNanScanTimeMs +=
                (newStats.on_time_nan_scan - mLastLinkLayerStats.on_time_nan_scan);
        mWifiLinkLayerUsageStats.radioBackgroundScanTimeMs +=
                (newStats.on_time_background_scan - mLastLinkLayerStats.on_time_background_scan);
        mWifiLinkLayerUsageStats.radioRoamScanTimeMs +=
                (newStats.on_time_roam_scan - mLastLinkLayerStats.on_time_roam_scan);
        mWifiLinkLayerUsageStats.radioPnoScanTimeMs +=
                (newStats.on_time_pno_scan - mLastLinkLayerStats.on_time_pno_scan);
        mWifiLinkLayerUsageStats.radioHs20ScanTimeMs +=
                (newStats.on_time_hs20_scan - mLastLinkLayerStats.on_time_hs20_scan);
        incrementPerRadioUsageStats(mLastLinkLayerStats, newStats);

        mLastLinkLayerStats = newStats;
    }

    /**
     * Increment individual radio stats usage
     */
    private void incrementPerRadioUsageStats(WifiLinkLayerStats oldStats,
            WifiLinkLayerStats newStats) {
        if (newStats.radioStats != null && newStats.radioStats.length > 0
                && oldStats.radioStats != null && oldStats.radioStats.length > 0
                && newStats.radioStats.length == oldStats.radioStats.length) {
            int numRadios = newStats.radioStats.length;
            for (int i = 0; i < numRadios; i++) {
                WifiLinkLayerStats.RadioStat newRadio = newStats.radioStats[i];
                WifiLinkLayerStats.RadioStat oldRadio = oldStats.radioStats[i];
                if (newRadio.radio_id != oldRadio.radio_id) {
                    continue;
                }
                RadioStats radioStats = mRadioStats.get(newRadio.radio_id);
                if (radioStats == null) {
                    radioStats = new RadioStats();
                    radioStats.radioId = newRadio.radio_id;
                    mRadioStats.put(newRadio.radio_id, radioStats);
                }
                radioStats.totalRadioOnTimeMs
                        += newRadio.on_time - oldRadio.on_time;
                radioStats.totalRadioTxTimeMs
                        += newRadio.tx_time - oldRadio.tx_time;
                radioStats.totalRadioRxTimeMs
                        += newRadio.rx_time - oldRadio.rx_time;
                radioStats.totalScanTimeMs
                        += newRadio.on_time_scan - oldRadio.on_time_scan;
                radioStats.totalNanScanTimeMs
                        += newRadio.on_time_nan_scan - oldRadio.on_time_nan_scan;
                radioStats.totalBackgroundScanTimeMs
                        += newRadio.on_time_background_scan - oldRadio.on_time_background_scan;
                radioStats.totalRoamScanTimeMs
                        += newRadio.on_time_roam_scan - oldRadio.on_time_roam_scan;
                radioStats.totalPnoScanTimeMs
                        += newRadio.on_time_pno_scan - oldRadio.on_time_pno_scan;
                radioStats.totalHotspot2ScanTimeMs
                        += newRadio.on_time_hs20_scan - oldRadio.on_time_hs20_scan;
            }
        }
    }

    private boolean newLinkLayerStatsIsValid(WifiLinkLayerStats oldStats,
            WifiLinkLayerStats newStats) {
        if (newStats.on_time < oldStats.on_time
                || newStats.tx_time < oldStats.tx_time
                || newStats.rx_time < oldStats.rx_time
                || newStats.on_time_scan < oldStats.on_time_scan) {
            return false;
        }
        return true;
    }

    /**
     * Increment total number of attempts to start a pno scan
     */
    public void incrementPnoScanStartAttemptCount() {
        synchronized (mLock) {
            mPnoScanMetrics.numPnoScanAttempts++;
        }
    }

    /**
     * Increment total number of attempts with pno scan failed
     */
    public void incrementPnoScanFailedCount() {
        synchronized (mLock) {
            mPnoScanMetrics.numPnoScanFailed++;
        }
    }

    /**
     * Increment number of times pno scan found a result
     */
    public void incrementPnoFoundNetworkEventCount() {
        synchronized (mLock) {
            mPnoScanMetrics.numPnoFoundNetworkEvents++;
        }
    }

    // Values used for indexing SystemStateEntries
    private static final int SCREEN_ON = 1;
    private static final int SCREEN_OFF = 0;

    private int convertSecurityTypeToWifiMetricsNetworkType(
            @WifiConfiguration.SecurityType int type) {
        switch (type) {
            case WifiConfiguration.SECURITY_TYPE_OPEN:
                return WifiMetricsProto.ConnectionEvent.TYPE_OPEN;
            case WifiConfiguration.SECURITY_TYPE_PSK:
                return WifiMetricsProto.ConnectionEvent.TYPE_WPA2;
            case WifiConfiguration.SECURITY_TYPE_EAP:
                return WifiMetricsProto.ConnectionEvent.TYPE_EAP;
            case WifiConfiguration.SECURITY_TYPE_SAE:
                return WifiMetricsProto.ConnectionEvent.TYPE_WPA3;
            case WifiConfiguration.SECURITY_TYPE_EAP_WPA3_ENTERPRISE_192_BIT:
                return WifiMetricsProto.ConnectionEvent.TYPE_EAP;
            case WifiConfiguration.SECURITY_TYPE_OWE:
                return WifiMetricsProto.ConnectionEvent.TYPE_OWE;
            case WifiConfiguration.SECURITY_TYPE_WAPI_PSK:
            case WifiConfiguration.SECURITY_TYPE_WAPI_CERT:
                return WifiMetricsProto.ConnectionEvent.TYPE_WAPI;
            case WifiConfiguration.SECURITY_TYPE_EAP_WPA3_ENTERPRISE:
                return WifiMetricsProto.ConnectionEvent.TYPE_EAP;
            // No metric network type for WEP, OSEN, and DPP.
            default:
                return WifiMetricsProto.ConnectionEvent.TYPE_UNKNOWN;
        }
    }

    /**
     * Create a new connection event and check if the new one overlaps with previous one.
     * Call when wifi attempts to make a new network connection
     * If there is a current 'un-ended' connection event, it will be ended with UNKNOWN connectivity
     * failure code.
     * Gathers and sets the RouterFingerPrint data as well
     *
     * @param ifaceName interface name for this connection event
     * @param config WifiConfiguration of the config used for the current connection attempt
     * @param roamType Roam type that caused connection attempt, see WifiMetricsProto.WifiLog.ROAM_X
     * @return The duration in ms since the last unfinished connection attempt,
     * or 0 if there is no unfinished connection
     */
    public int startConnectionEvent(
            String ifaceName, WifiConfiguration config, String targetBSSID, int roamType,
            boolean isOobPseudonymEnabled, int role, int uid) {
        synchronized (mLock) {
            int overlapWithLastConnectionMs = 0;
            ConnectionEvent currentConnectionEvent = mCurrentConnectionEventPerIface.get(ifaceName);
            if (currentConnectionEvent != null) {
                overlapWithLastConnectionMs = (int) (mClock.getElapsedSinceBootMillis()
                        - currentConnectionEvent.mConnectionEvent.startTimeSinceBootMillis);
                // Is this new Connection Event the same as the current one
                if (currentConnectionEvent.mConfigSsid != null
                        && currentConnectionEvent.mConfigBssid != null
                        && config != null
                        && currentConnectionEvent.mConfigSsid.equals(config.SSID)
                        && (currentConnectionEvent.mConfigBssid.equals("any")
                        || currentConnectionEvent.mConfigBssid.equals(targetBSSID))) {
                    currentConnectionEvent.mConfigBssid = targetBSSID;
                    // End Connection Event due to new connection attempt to the same network
                    endConnectionEvent(ifaceName,
                            ConnectionEvent.FAILURE_REDUNDANT_CONNECTION_ATTEMPT,
                            WifiMetricsProto.ConnectionEvent.HLF_NONE,
                            WifiMetricsProto.ConnectionEvent.FAILURE_REASON_UNKNOWN, 0, 0);
                } else {
                    // End Connection Event due to new connection attempt to different network
                    endConnectionEvent(ifaceName,
                            ConnectionEvent.FAILURE_NEW_CONNECTION_ATTEMPT,
                            WifiMetricsProto.ConnectionEvent.HLF_NONE,
                            WifiMetricsProto.ConnectionEvent.FAILURE_REASON_UNKNOWN, 0, 0);
                }
            }
            // If past maximum connection events, start removing the oldest
            while(mConnectionEventList.size() >= MAX_CONNECTION_EVENTS) {
                mConnectionEventList.removeFirst();
            }
            currentConnectionEvent = new ConnectionEvent();
            mCurrentConnectionEventPerIface.put(ifaceName, currentConnectionEvent);
            currentConnectionEvent.mConnectionEvent.interfaceName = ifaceName;
            currentConnectionEvent.mConnectionEvent.interfaceRole = convertIfaceToEnum(ifaceName);
            currentConnectionEvent.mConnectionEvent.startTimeMillis =
                    mClock.getWallClockMillis();
            currentConnectionEvent.mConnectionEvent.startTimeSinceBootMillis =
                    mClock.getElapsedSinceBootMillis();
            currentConnectionEvent.mConfigBssid = targetBSSID;
            currentConnectionEvent.mConnectionEvent.roamType = roamType;
            currentConnectionEvent.mConnectionEvent.networkSelectorExperimentId =
                    mNetworkSelectorExperimentId;
            currentConnectionEvent.updateFromWifiConfiguration(config);
            currentConnectionEvent.mIsOobPseudonymEnabled = isOobPseudonymEnabled;
            currentConnectionEvent.mConfigBssid = "any";
            currentConnectionEvent.mWifiState = mWifiState;
            currentConnectionEvent.mScreenOn = mScreenOn;
            currentConnectionEvent.mConnectionEvent.isFirstConnectionAfterBoot =
                    mFirstConnectionAfterBoot;
            currentConnectionEvent.mRole = role;
            currentConnectionEvent.mUid = uid;
            mFirstConnectionAfterBoot = false;
            mConnectionEventList.add(currentConnectionEvent);
            mScanResultRssiTimestampMillis = -1;
            if (config != null) {
                try {
                    currentConnectionEvent.mAuthType = config.getAuthType();
                } catch (IllegalStateException e) {
                    currentConnectionEvent.mAuthType = 0;
                }
                currentConnectionEvent.mHasEverConnected =
                        config.getNetworkSelectionStatus().hasEverConnected();
                currentConnectionEvent.mConnectionEvent.useRandomizedMac =
                        config.macRandomizationSetting
                        != WifiConfiguration.RANDOMIZATION_NONE;
                currentConnectionEvent.mConnectionEvent.useAggressiveMac =
                        mWifiConfigManager.shouldUseNonPersistentRandomization(config);
                currentConnectionEvent.mConnectionEvent.connectionNominator =
                        mNetworkIdToNominatorId.get(config.networkId,
                                WifiMetricsProto.ConnectionEvent.NOMINATOR_UNKNOWN);
                currentConnectionEvent.mConnectionEvent.isCarrierMerged = config.carrierMerged;
                currentConnectionEvent.mCarrierId = config.carrierId;
                if (config.enterpriseConfig != null) {
                    currentConnectionEvent.mEapType = config.enterpriseConfig.getEapMethod();
                    currentConnectionEvent.mPhase2Method =
                            config.enterpriseConfig.getPhase2Method();
                    currentConnectionEvent.mPasspointRoamingType = Utils.getRoamingType(config);
                }

                ScanResult candidate = config.getNetworkSelectionStatus().getCandidate();
                if (candidate != null) {
                    // Cache the RSSI of the candidate, as the connection event level is updated
                    // from other sources (polls, bssid_associations) and delta requires the
                    // scanResult rssi
                    mScanResultRssi = candidate.level;
                    mScanResultRssiTimestampMillis = mClock.getElapsedSinceBootMillis();
                }
                currentConnectionEvent.mConnectionEvent.numBssidInBlocklist =
                        mWifiBlocklistMonitor.updateAndGetNumBlockedBssidsForSsid(config.SSID);
                currentConnectionEvent.mConnectionEvent.networkType =
                        WifiMetricsProto.ConnectionEvent.TYPE_UNKNOWN;
                currentConnectionEvent.mConnectionEvent.isOsuProvisioned = false;
                SecurityParams params = config.getNetworkSelectionStatus()
                        .getCandidateSecurityParams();
                currentConnectionEvent.mRouterFingerPrint.mSecurityMode =
                        getSecurityMode(config, true);
                if (config.isPasspoint()) {
                    currentConnectionEvent.mConnectionEvent.networkType =
                            WifiMetricsProto.ConnectionEvent.TYPE_PASSPOINT;
                    currentConnectionEvent.mConnectionEvent.isOsuProvisioned =
                            !TextUtils.isEmpty(config.updateIdentifier);
                } else if (null != params) {
                    currentConnectionEvent.mConnectionEvent.networkType =
                            convertSecurityTypeToWifiMetricsNetworkType(params.getSecurityType());
                } else if (WifiConfigurationUtil.isConfigForSaeNetwork(config)) {
                    currentConnectionEvent.mConnectionEvent.networkType =
                            WifiMetricsProto.ConnectionEvent.TYPE_WPA3;
                } else if (WifiConfigurationUtil.isConfigForWapiPskNetwork(config)) {
                    currentConnectionEvent.mConnectionEvent.networkType =
                            WifiMetricsProto.ConnectionEvent.TYPE_WAPI;
                } else if (WifiConfigurationUtil.isConfigForWapiCertNetwork(config)) {
                    currentConnectionEvent.mConnectionEvent.networkType =
                            WifiMetricsProto.ConnectionEvent.TYPE_WAPI;
                } else if (WifiConfigurationUtil.isConfigForPskNetwork(config)) {
                    currentConnectionEvent.mConnectionEvent.networkType =
                            WifiMetricsProto.ConnectionEvent.TYPE_WPA2;
                } else if (WifiConfigurationUtil.isConfigForEnterpriseNetwork(config)) {
                    currentConnectionEvent.mConnectionEvent.networkType =
                            WifiMetricsProto.ConnectionEvent.TYPE_EAP;
                } else if (WifiConfigurationUtil.isConfigForOweNetwork(config)) {
                    currentConnectionEvent.mConnectionEvent.networkType =
                            WifiMetricsProto.ConnectionEvent.TYPE_OWE;
                } else if (WifiConfigurationUtil.isConfigForOpenNetwork(config)) {
                    currentConnectionEvent.mConnectionEvent.networkType =
                            WifiMetricsProto.ConnectionEvent.TYPE_OPEN;
                }

                if (!config.fromWifiNetworkSuggestion) {
                    currentConnectionEvent.mConnectionEvent.networkCreator =
                            WifiMetricsProto.ConnectionEvent.CREATOR_USER;
                } else if (config.carrierId != TelephonyManager.UNKNOWN_CARRIER_ID) {
                    currentConnectionEvent.mConnectionEvent.networkCreator =
                            WifiMetricsProto.ConnectionEvent.CREATOR_CARRIER;
                } else {
                    currentConnectionEvent.mConnectionEvent.networkCreator =
                            WifiMetricsProto.ConnectionEvent.CREATOR_UNKNOWN;
                }

                currentConnectionEvent.mConnectionEvent.screenOn = mScreenOn;
                if (currentConnectionEvent.mConfigSsid != null) {
                    WifiScoreCard.NetworkConnectionStats recentStats = mWifiScoreCard.lookupNetwork(
                            currentConnectionEvent.mConfigSsid).getRecentStats();
                    currentConnectionEvent.mConnectionEvent.numConsecutiveConnectionFailure =
                            recentStats.getCount(WifiScoreCard.CNT_CONSECUTIVE_CONNECTION_FAILURE);
                }

                String ssid = currentConnectionEvent.mConfigSsid;
                int nominator = currentConnectionEvent.mConnectionEvent.connectionNominator;
                int trigger = WifiStatsLog.WIFI_CONNECTION_RESULT_REPORTED__TRIGGER__UNKNOWN;

                if (nominator == WifiMetricsProto.ConnectionEvent.NOMINATOR_MANUAL) {
                    trigger = WifiStatsLog.WIFI_CONNECTION_RESULT_REPORTED__TRIGGER__MANUAL;
                } else if (mPreviousSession == null) {
                    trigger = WifiStatsLog.WIFI_CONNECTION_RESULT_REPORTED__TRIGGER__AUTOCONNECT_BOOT;
                } else if (ssid != null && ssid.equals(mPreviousSession.mSsid)) {
                    trigger = WifiStatsLog.WIFI_CONNECTION_RESULT_REPORTED__TRIGGER__RECONNECT_SAME_NETWORK;
                } else if (nominator != WifiMetricsProto.ConnectionEvent.NOMINATOR_UNKNOWN) {
                    trigger = WifiStatsLog.WIFI_CONNECTION_RESULT_REPORTED__TRIGGER__AUTOCONNECT_CONFIGURED_NETWORK;
                }
                currentConnectionEvent.mTrigger = trigger;
            }

            return overlapWithLastConnectionMs;
        }
    }

    /**
     * Set AP related metrics from ScanDetail
     */
    public void setConnectionScanDetail(String ifaceName, ScanDetail scanDetail) {
        synchronized (mLock) {
            ConnectionEvent currentConnectionEvent = mCurrentConnectionEventPerIface.get(ifaceName);
            if (currentConnectionEvent == null || scanDetail == null) {
                return;
            }
            NetworkDetail networkDetail = scanDetail.getNetworkDetail();
            ScanResult scanResult = scanDetail.getScanResult();
            // Ensure that we have a networkDetail, and that it corresponds to the currently
            // tracked connection attempt
            if (networkDetail == null || scanResult == null
                    || currentConnectionEvent.mConfigSsid == null
                    || !currentConnectionEvent.mConfigSsid
                    .equals("\"" + networkDetail.getSSID() + "\"")) {
                return;
            }
            updateMetricsFromNetworkDetail(currentConnectionEvent, networkDetail);
            updateMetricsFromScanResult(currentConnectionEvent, scanResult);
        }
    }

    /**
     * Set PMK cache status for a connection event
     */
    public void setConnectionPmkCache(String ifaceName, boolean isEnabled) {
        synchronized (mLock) {
            ConnectionEvent currentConnectionEvent = mCurrentConnectionEventPerIface.get(ifaceName);
            if (currentConnectionEvent != null) {
                currentConnectionEvent.mRouterFingerPrint.setPmkCache(isEnabled);
            }
        }
    }

    /**
     * Set the max link speed supported by current network
     */
    public void setConnectionMaxSupportedLinkSpeedMbps(
            String ifaceName, int maxSupportedTxLinkSpeedMbps, int maxSupportedRxLinkSpeedMbps) {
        synchronized (mLock) {
            ConnectionEvent currentConnectionEvent = mCurrentConnectionEventPerIface.get(ifaceName);
            if (currentConnectionEvent != null) {
                currentConnectionEvent.mRouterFingerPrint.setMaxSupportedLinkSpeedMbps(
                        maxSupportedTxLinkSpeedMbps, maxSupportedRxLinkSpeedMbps);
            }
        }
    }

    private int toMetricEapType(int eapType) {
        switch (eapType) {
            case WifiEnterpriseConfig.Eap.TLS:
                return WifiStatsLog.WIFI_CONNECTION_RESULT_REPORTED__EAP_TYPE__TYPE_EAP_TLS;
            case WifiEnterpriseConfig.Eap.TTLS:
                return WifiStatsLog.WIFI_CONNECTION_RESULT_REPORTED__EAP_TYPE__TYPE_EAP_TTLS;
            case WifiEnterpriseConfig.Eap.SIM:
                return WifiStatsLog.WIFI_CONNECTION_RESULT_REPORTED__EAP_TYPE__TYPE_EAP_SIM;
            case WifiEnterpriseConfig.Eap.AKA:
                return WifiStatsLog.WIFI_CONNECTION_RESULT_REPORTED__EAP_TYPE__TYPE_EAP_AKA;
            case WifiEnterpriseConfig.Eap.AKA_PRIME:
                return WifiStatsLog.WIFI_CONNECTION_RESULT_REPORTED__EAP_TYPE__TYPE_EAP_AKA_PRIME;
            case WifiEnterpriseConfig.Eap.WAPI_CERT:
                return WifiStatsLog.WIFI_CONNECTION_RESULT_REPORTED__EAP_TYPE__TYPE_EAP_WAPI_CERT;
            case WifiEnterpriseConfig.Eap.UNAUTH_TLS:
                return WifiStatsLog.WIFI_CONNECTION_RESULT_REPORTED__EAP_TYPE__TYPE_EAP_UNAUTH_TLS;
            case WifiEnterpriseConfig.Eap.PEAP:
                return WifiStatsLog.WIFI_CONNECTION_RESULT_REPORTED__EAP_TYPE__TYPE_EAP_PEAP;
            case WifiEnterpriseConfig.Eap.PWD:
                return WifiStatsLog.WIFI_CONNECTION_RESULT_REPORTED__EAP_TYPE__TYPE_EAP_PWD;
            default:
                return WifiStatsLog.WIFI_CONNECTION_RESULT_REPORTED__EAP_TYPE__TYPE_EAP_OTHERS;
        }
    }

    private int toMetricPhase2Method(int phase2Method) {
        switch (phase2Method) {
            case WifiEnterpriseConfig.Phase2.PAP:
                return WifiStatsLog.WIFI_CONNECTION_RESULT_REPORTED__EAP_INNER_METHOD__METHOD_PAP;
            case WifiEnterpriseConfig.Phase2.MSCHAP:
                return WifiStatsLog.WIFI_CONNECTION_RESULT_REPORTED__EAP_INNER_METHOD__METHOD_MSCHAP;
            case WifiEnterpriseConfig.Phase2.MSCHAPV2:
                return WifiStatsLog.WIFI_CONNECTION_RESULT_REPORTED__EAP_INNER_METHOD__METHOD_MSCHAP_V2;
            default:
                return  WifiStatsLog.WIFI_CONNECTION_RESULT_REPORTED__EAP_INNER_METHOD__METHOD_OTHERS;
        }
    }

    /**
     * End a Connection event record. Call when wifi connection attempt succeeds or fails.
     * If a Connection event has not been started and is active when .end is called, then this
     * method will do nothing.
     *
     * @param ifaceName
     * @param level2FailureCode Level 2 failure code returned by supplicant
     * @param connectivityFailureCode WifiMetricsProto.ConnectionEvent.HLF_X
     * @param level2FailureReason Breakdown of level2FailureCode with more detailed reason
     */
    public void endConnectionEvent(
            String ifaceName,
            int level2FailureCode,
            int connectivityFailureCode,
            int level2FailureReason,
            int frequency,
            int statusCode) {
        synchronized (mLock) {
            ConnectionEvent currentConnectionEvent = mCurrentConnectionEventPerIface.get(ifaceName);
            if (currentConnectionEvent != null) {
                boolean connectionSucceeded = (level2FailureCode == 1)
                        && (connectivityFailureCode == WifiMetricsProto.ConnectionEvent.HLF_NONE);

                int band = KnownBandsChannelHelper.getBand(frequency);
                int durationTakenToConnectMillis =
                        (int) (mClock.getElapsedSinceBootMillis()
                                - currentConnectionEvent.mConnectionEvent.startTimeSinceBootMillis);

                if (connectionSucceeded) {
                    mCurrentSession = new SessionData(currentConnectionEvent,
                            currentConnectionEvent.mConfigSsid,
                            mClock.getElapsedSinceBootMillis(),
                            band, currentConnectionEvent.mAuthType);
                    if (currentConnectionEvent.mRole == WifiStatsLog.WIFI_CONNECTION_RESULT_REPORTED__ROLE__ROLE_CLIENT_PRIMARY) {
                        WifiStatsLog.write(WifiStatsLog.WIFI_CONNECTION_STATE_CHANGED,
                                true, band, currentConnectionEvent.mAuthType);
                    }
                }

                currentConnectionEvent.mConnectionEvent.connectionResult =
                        connectionSucceeded ? 1 : 0;
                currentConnectionEvent.mConnectionEvent.durationTakenToConnectMillis =
                        durationTakenToConnectMillis;
                currentConnectionEvent.mConnectionEvent.level2FailureCode = level2FailureCode;
                currentConnectionEvent.mConnectionEvent.connectivityLevelFailureCode =
                        connectivityFailureCode;
                currentConnectionEvent.mConnectionEvent.level2FailureReason = level2FailureReason;

                // Write metrics to statsd
                int wwFailureCode = getConnectionResultFailureCode(level2FailureCode,
                        level2FailureReason);
                int timeSinceConnectedSeconds = (int) ((mPreviousSession != null
                        ? (mClock.getElapsedSinceBootMillis()
                                - mPreviousSession.mSessionEndTimeMillis) :
                        mClock.getElapsedSinceBootMillis()) / 1000);
                WifiStatsLog.write(WifiStatsLog.WIFI_CONNECTION_RESULT_REPORTED,
                        connectionSucceeded,
                        wwFailureCode, currentConnectionEvent.mConnectionEvent.signalStrength,
                        durationTakenToConnectMillis, band, currentConnectionEvent.mAuthType,
                        currentConnectionEvent.mTrigger,
                        currentConnectionEvent.mHasEverConnected,
                        timeSinceConnectedSeconds,
                        currentConnectionEvent.mIsCarrierWifi,
                        currentConnectionEvent.mIsOobPseudonymEnabled,
                        currentConnectionEvent.mRole,
                        statusCode,
                        toMetricEapType(currentConnectionEvent.mEapType),
                        toMetricPhase2Method(currentConnectionEvent.mPhase2Method),
                        currentConnectionEvent.mPasspointRoamingType,
                        currentConnectionEvent.mCarrierId,
                        currentConnectionEvent.mTofuConnectionState,
                        currentConnectionEvent.mUid);

                if (connectionSucceeded) {
                    reportRouterCapabilities(currentConnectionEvent.mRouterFingerPrint);
                }
                // ConnectionEvent already added to ConnectionEvents List. Safe to remove here.
                mCurrentConnectionEventPerIface.remove(ifaceName);
                if (!connectionSucceeded) {
                    mScanResultRssiTimestampMillis = -1;
                }
                mWifiStatusBuilder.setConnected(connectionSucceeded);
            }
        }
    }

    protected static int convertTofuConnectionStateToProto(WifiConfiguration config) {
        if (!config.isEnterprise()) {
            return WifiStatsLog
                    .WIFI_CONFIGURED_NETWORK_INFO__TOFU_CONFIGURATION__TOFU_CONFIGURATION_UNSPECIFIED;
        }

        switch (config.enterpriseConfig.getTofuConnectionState()) {
            case WifiEnterpriseConfig.TOFU_STATE_NOT_ENABLED:
                return WifiStatsLog
                        .WIFI_CONFIGURED_NETWORK_INFO__TOFU_CONFIGURATION__TOFU_CONFIGURATION_NOT_ENABLED;
            case WifiEnterpriseConfig.TOFU_STATE_ENABLED_PRE_CONNECTION:
                return WifiStatsLog
                        .WIFI_CONFIGURED_NETWORK_INFO__TOFU_CONFIGURATION__TOFU_CONFIGURATION_ENABLED_PRE_CONNECTION;
            case WifiEnterpriseConfig.TOFU_STATE_CONFIGURE_ROOT_CA:
                return WifiStatsLog
                        .WIFI_CONFIGURED_NETWORK_INFO__TOFU_CONFIGURATION__TOFU_CONFIGURATION_CONFIGURE_ROOT_CA;
            case WifiEnterpriseConfig.TOFU_STATE_CERT_PINNING:
                return WifiStatsLog
                        .WIFI_CONFIGURED_NETWORK_INFO__TOFU_CONFIGURATION__TOFU_CONFIGURATION_CERT_PINNING;
            default:
                return WifiStatsLog
                        .WIFI_CONFIGURED_NETWORK_INFO__TOFU_CONFIGURATION__TOFU_CONFIGURATION_UNSPECIFIED;
        }
    }

    protected static int convertTofuDialogStateToProto(WifiConfiguration config) {
        if (!config.isEnterprise()) {
            return WifiStatsLog
                    .WIFI_CONFIGURED_NETWORK_INFO__TOFU_DIALOG_STATE__TOFU_DIALOG_STATE_UNSPECIFIED;
        }

        switch (config.enterpriseConfig.getTofuDialogState()) {
            case WifiEnterpriseConfig.TOFU_DIALOG_STATE_REJECTED:
                return WifiStatsLog
                        .WIFI_CONFIGURED_NETWORK_INFO__TOFU_DIALOG_STATE__TOFU_DIALOG_STATE_REJECTED;
            case WifiEnterpriseConfig.TOFU_DIALOG_STATE_ACCEPTED:
                return WifiStatsLog
                        .WIFI_CONFIGURED_NETWORK_INFO__TOFU_DIALOG_STATE__TOFU_DIALOG_STATE_ACCEPTED;
            default:
                return WifiStatsLog
                        .WIFI_CONFIGURED_NETWORK_INFO__TOFU_DIALOG_STATE__TOFU_DIALOG_STATE_UNSPECIFIED;
        }
    }

    protected static int convertMacRandomizationToProto(
            @WifiConfiguration.MacRandomizationSetting int macRandomizationSetting) {
        switch (macRandomizationSetting) {
            case WifiConfiguration.RANDOMIZATION_NONE:
                return WifiStatsLog
                        .WIFI_CONFIGURED_NETWORK_INFO__MAC_RANDOMIZATION__MAC_RANDOMIZATION_NONE;
            case WifiConfiguration.RANDOMIZATION_PERSISTENT:
                return WifiStatsLog
                        .WIFI_CONFIGURED_NETWORK_INFO__MAC_RANDOMIZATION__MAC_RANDOMIZATION_PERSISTENT;
            case WifiConfiguration.RANDOMIZATION_NON_PERSISTENT:
                return WifiStatsLog
                        .WIFI_CONFIGURED_NETWORK_INFO__MAC_RANDOMIZATION__MAC_RANDOMIZATION_NON_PERSISTENT;
            case WifiConfiguration.RANDOMIZATION_AUTO:
                return WifiStatsLog
                        .WIFI_CONFIGURED_NETWORK_INFO__MAC_RANDOMIZATION__MAC_RANDOMIZATION_AUTO;
            default:
                return WifiStatsLog
                        .WIFI_CONFIGURED_NETWORK_INFO__MAC_RANDOMIZATION__MAC_RANDOMIZATION_UNSPECIFIED;
        }
    }

    protected static int convertMeteredOverrideToProto(
            @WifiConfiguration.MeteredOverride int meteredOverride) {
        switch (meteredOverride) {
            case WifiConfiguration.METERED_OVERRIDE_NONE:
                return WifiStatsLog
                        .WIFI_CONFIGURED_NETWORK_INFO__METERED_OVERRIDE__METERED_OVERRIDE_NONE;
            case WifiConfiguration.METERED_OVERRIDE_METERED:
                return WifiStatsLog
                        .WIFI_CONFIGURED_NETWORK_INFO__METERED_OVERRIDE__METERED_OVERRIDE_METERED;
            case WifiConfiguration.METERED_OVERRIDE_NOT_METERED:
                return WifiStatsLog
                        .WIFI_CONFIGURED_NETWORK_INFO__METERED_OVERRIDE__METERED_OVERRIDE_NOT_METERED;
            default:
                return WifiStatsLog
                        .WIFI_CONFIGURED_NETWORK_INFO__METERED_OVERRIDE__METERED_OVERRIDE_UNSPECIFIED;
        }
    }

    protected static int getSecurityMode(WifiConfiguration config, boolean useCandidateParams) {
        SecurityParams params =
                useCandidateParams
                        ? config.getNetworkSelectionStatus().getCandidateSecurityParams()
                        : config.getNetworkSelectionStatus().getLastUsedSecurityParams();
        if (params != null) {
            return params.getSecurityType();
        } else if (WifiConfigurationUtil.isConfigForWpa3Enterprise192BitNetwork(config)) {
            return WifiConfiguration.SECURITY_TYPE_EAP_WPA3_ENTERPRISE_192_BIT;
        } else if (WifiConfigurationUtil.isConfigForWpa3EnterpriseNetwork(config)) {
            return WifiConfiguration.SECURITY_TYPE_EAP_WPA3_ENTERPRISE;
        } else if (WifiConfigurationUtil.isConfigForDppNetwork(config)) {
            return WifiConfiguration.SECURITY_TYPE_DPP;
        } else if (WifiConfigurationUtil.isConfigForSaeNetwork(config)) {
            return WifiConfiguration.SECURITY_TYPE_SAE;
        } else if (WifiConfigurationUtil.isConfigForWapiPskNetwork(config)) {
            return WifiConfiguration.SECURITY_TYPE_WAPI_PSK;
        } else if (WifiConfigurationUtil.isConfigForWapiCertNetwork(config)) {
            return WifiConfiguration.SECURITY_TYPE_WAPI_CERT;
        } else if (WifiConfigurationUtil.isConfigForPskNetwork(config)) {
            return WifiConfiguration.SECURITY_TYPE_PSK;
        } else if (WifiConfigurationUtil.isConfigForOweNetwork(config)) {
            return WifiConfiguration.SECURITY_TYPE_OWE;
        } else if (WifiConfigurationUtil.isConfigForWepNetwork(config)) {
            return WifiConfiguration.SECURITY_TYPE_WEP;
        } else if (WifiConfigurationUtil.isConfigForOpenNetwork(config)) {
            return WifiConfiguration.SECURITY_TYPE_OPEN;
        } else {
            Log.e(TAG, "Unknown security mode for config " + config);
            return -1;
        }
    }

    /**
     * Check if the provided security type is enabled in the security params list.
     */
    private static boolean securityTypeEnabled(List<SecurityParams> securityParamsList,
            int securityType) {
        for (SecurityParams params : securityParamsList) {
            if (params.isSecurityType(securityType) && params.isEnabled()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if any security parameters with the provided type were added by auto-upgrade.
     */
    private static boolean securityTypeAddedByAutoUpgrade(List<SecurityParams> securityParamsList,
            int securityType) {
        for (SecurityParams params : securityParamsList) {
            if (params.isSecurityType(securityType) && params.isAddedByAutoUpgrade()) {
                return true;
            }
        }
        return false;
    }

    protected static int convertSecurityModeToProto(WifiConfiguration config) {
        if (config == null || config.getDefaultSecurityParams() == null) {
            return WifiStatsLog.WIFI_CONFIGURED_NETWORK_INFO__CONNECTED_SECURITY_MODE__SECURITY_MODE_UNKNOWN;
        }
        SecurityParams defaultParams = config.getDefaultSecurityParams();
        List<SecurityParams> securityParamsList = config.getSecurityParamsList();
        switch (defaultParams.getSecurityType()) {
            case WifiConfiguration.SECURITY_TYPE_OPEN:
            case WifiConfiguration.SECURITY_TYPE_OWE: {
                boolean openEnabled = securityTypeEnabled(
                        securityParamsList, WifiConfiguration.SECURITY_TYPE_OPEN);
                boolean oweEnabled = securityTypeEnabled(
                        securityParamsList, WifiConfiguration.SECURITY_TYPE_OWE);
                if (openEnabled && !oweEnabled) {
                    // OWE params may be disabled or may not exist.
                    return WifiStatsLog.WIFI_CONFIGURED_NETWORK_INFO__CONNECTED_SECURITY_MODE__SECURITY_MODE_NONE;
                } else if (!openEnabled && oweEnabled) {
                    // Open params may get disabled via TDI.
                    return WifiStatsLog.WIFI_CONFIGURED_NETWORK_INFO__CONNECTED_SECURITY_MODE__SECURITY_MODE_OWE;
                }

                if (securityTypeAddedByAutoUpgrade(
                        securityParamsList, WifiConfiguration.SECURITY_TYPE_OWE)) {
                    // User configured this network using Open, but OWE params were auto-added.
                    return WifiStatsLog.WIFI_CONFIGURED_NETWORK_INFO__CONNECTED_SECURITY_MODE__SECURITY_MODE_NONE;
                }
                // User manually configured this network with both Open and OWE params.
                return WifiStatsLog.WIFI_CONFIGURED_NETWORK_INFO__CONNECTED_SECURITY_MODE__SECURITY_MODE_OWE_TRANSITION;
            }
            case WifiConfiguration.SECURITY_TYPE_WEP:
                return WifiStatsLog.WIFI_AP_CAPABILITIES_REPORTED__CONNECTED_SECURITY_MODE__SECURITY_MODE_WEP;
            case WifiConfiguration.SECURITY_TYPE_PSK: {
                boolean pskEnabled = securityTypeEnabled(
                        securityParamsList, WifiConfiguration.SECURITY_TYPE_PSK);
                boolean saeEnabled = securityTypeEnabled(
                        securityParamsList, WifiConfiguration.SECURITY_TYPE_SAE);
                if (pskEnabled && !saeEnabled) {
                    // WPA3 params may be disabled or may not exist.
                    return WifiStatsLog.WIFI_CONFIGURED_NETWORK_INFO__CONNECTED_SECURITY_MODE__SECURITY_MODE_WPA2_PERSONAL;
                } else if (!pskEnabled && saeEnabled) {
                    // WPA2 params may get disabled via TDI.
                    return WifiStatsLog.WIFI_CONFIGURED_NETWORK_INFO__CONNECTED_SECURITY_MODE__SECURITY_MODE_WPA3_PERSONAL;
                }

                if (securityTypeAddedByAutoUpgrade(
                        securityParamsList, WifiConfiguration.SECURITY_TYPE_SAE)) {
                    // User configured this network using WPA2, but WPA3 params were auto-added.
                    return WifiStatsLog.WIFI_CONFIGURED_NETWORK_INFO__CONNECTED_SECURITY_MODE__SECURITY_MODE_WPA2_PERSONAL;
                }
                // User manually configured this network with both WPA2 and WPA3 params.
                return WifiStatsLog.WIFI_CONFIGURED_NETWORK_INFO__CONNECTED_SECURITY_MODE__SECURITY_MODE_WPA3_WPA2_PERSONAL_TRANSITION;
            }
            case WifiConfiguration.SECURITY_TYPE_SAE:
                return WifiStatsLog.WIFI_CONFIGURED_NETWORK_INFO__CONNECTED_SECURITY_MODE__SECURITY_MODE_WPA3_PERSONAL;
            case WifiConfiguration.SECURITY_TYPE_WAPI_PSK:
                return WifiStatsLog.WIFI_CONFIGURED_NETWORK_INFO__CONNECTED_SECURITY_MODE__SECURITY_MODE_WAPI_PSK;
            case WifiConfiguration.SECURITY_TYPE_WAPI_CERT:
                return WifiStatsLog.WIFI_CONFIGURED_NETWORK_INFO__CONNECTED_SECURITY_MODE__SECURITY_MODE_WAPI_CERT;
            case WifiConfiguration.SECURITY_TYPE_DPP:
                return WifiStatsLog.WIFI_CONFIGURED_NETWORK_INFO__CONNECTED_SECURITY_MODE__SECURITY_MODE_DPP;
            case WifiConfiguration.SECURITY_TYPE_EAP_WPA3_ENTERPRISE_192_BIT:
                return WifiStatsLog.WIFI_CONFIGURED_NETWORK_INFO__CONNECTED_SECURITY_MODE__SECURITY_MODE_WPA3_ENTERPRISE_192_BIT;
            case WifiConfiguration.SECURITY_TYPE_EAP:
            case WifiConfiguration.SECURITY_TYPE_EAP_WPA3_ENTERPRISE:
            case WifiConfiguration.SECURITY_TYPE_PASSPOINT_R1_R2:
            case WifiConfiguration.SECURITY_TYPE_PASSPOINT_R3: {
                if (WifiConfigurationUtil.isConfigForWpa3EnterpriseNetwork(config)) {
                    return WifiStatsLog.WIFI_CONFIGURED_NETWORK_INFO__CONNECTED_SECURITY_MODE__SECURITY_MODE_WPA3_ENTERPRISE;
                }
                return WifiStatsLog.WIFI_AP_CAPABILITIES_REPORTED__CONNECTED_SECURITY_MODE__SECURITY_MODE_WPA_ENTERPRISE_LEGACY;
            }
            default:
                return WifiStatsLog.WIFI_CONFIGURED_NETWORK_INFO__CONNECTED_SECURITY_MODE__SECURITY_MODE_INVALID;
        }
    }

    static int convertSecurityModeToProto(@WifiConfiguration.SecurityType int securityMode) {
        switch (securityMode) {
            case WifiConfiguration.SECURITY_TYPE_OPEN:
                return WifiStatsLog.WIFI_AP_CAPABILITIES_REPORTED__CONNECTED_SECURITY_MODE__SECURITY_MODE_NONE;
            case WifiConfiguration.SECURITY_TYPE_WEP:
                return WifiStatsLog.WIFI_AP_CAPABILITIES_REPORTED__CONNECTED_SECURITY_MODE__SECURITY_MODE_WEP;
            case WifiConfiguration.SECURITY_TYPE_PSK:
                return WifiStatsLog.WIFI_AP_CAPABILITIES_REPORTED__CONNECTED_SECURITY_MODE__SECURITY_MODE_WPA2_PERSONAL;
            case WifiConfiguration.SECURITY_TYPE_PASSPOINT_R1_R2:
                // Passpoint R1 & R2 uses WPA2 Enterprise (Legacy)
            case WifiConfiguration.SECURITY_TYPE_EAP:
                return WifiStatsLog.WIFI_AP_CAPABILITIES_REPORTED__CONNECTED_SECURITY_MODE__SECURITY_MODE_WPA_ENTERPRISE_LEGACY;
            case WifiConfiguration.SECURITY_TYPE_SAE:
                return WifiStatsLog.WIFI_AP_CAPABILITIES_REPORTED__CONNECTED_SECURITY_MODE__SECURITY_MODE_WPA3_PERSONAL;
            case WifiConfiguration.SECURITY_TYPE_EAP_WPA3_ENTERPRISE_192_BIT:
                return WifiStatsLog.WIFI_AP_CAPABILITIES_REPORTED__CONNECTED_SECURITY_MODE__SECURITY_MODE_WPA3_ENTERPRISE_192_BIT;
            case WifiConfiguration.SECURITY_TYPE_OWE:
                return WifiStatsLog.WIFI_AP_CAPABILITIES_REPORTED__CONNECTED_SECURITY_MODE__SECURITY_MODE_OWE;
            case WifiConfiguration.SECURITY_TYPE_WAPI_PSK:
                return WifiStatsLog.WIFI_AP_CAPABILITIES_REPORTED__CONNECTED_SECURITY_MODE__SECURITY_MODE_WAPI_PSK;
            case WifiConfiguration.SECURITY_TYPE_WAPI_CERT:
                return WifiStatsLog.WIFI_AP_CAPABILITIES_REPORTED__CONNECTED_SECURITY_MODE__SECURITY_MODE_WAPI_CERT;
            case WifiConfiguration.SECURITY_TYPE_PASSPOINT_R3:
                // Passpoint R3 uses WPA3 Enterprise
            case WifiConfiguration.SECURITY_TYPE_EAP_WPA3_ENTERPRISE:
                return WifiStatsLog.WIFI_AP_CAPABILITIES_REPORTED__CONNECTED_SECURITY_MODE__SECURITY_MODE_WPA3_ENTERPRISE;
            case WifiConfiguration.SECURITY_TYPE_DPP:
                return WifiStatsLog.WIFI_AP_CAPABILITIES_REPORTED__CONNECTED_SECURITY_MODE__SECURITY_MODE_DPP;
            default:
                return WifiStatsLog.WIFI_AP_CAPABILITIES_REPORTED__CONNECTED_SECURITY_MODE__SECURITY_MODE_UNKNOWN;
        }
    }

    private int convertHsReleasetoProto(NetworkDetail.HSRelease hsRelease) {
        if (hsRelease == NetworkDetail.HSRelease.R1) {
            return WifiStatsLog.WIFI_AP_CAPABILITIES_REPORTED__PASSPOINT_RELEASE__PASSPOINT_RELEASE_1;
        } else if (hsRelease == NetworkDetail.HSRelease.R2) {
            return WifiStatsLog.WIFI_AP_CAPABILITIES_REPORTED__PASSPOINT_RELEASE__PASSPOINT_RELEASE_2;
        } else if (hsRelease == NetworkDetail.HSRelease.R3) {
            return WifiStatsLog.WIFI_AP_CAPABILITIES_REPORTED__PASSPOINT_RELEASE__PASSPOINT_RELEASE_3;
        } else {
            return WifiStatsLog.WIFI_AP_CAPABILITIES_REPORTED__PASSPOINT_RELEASE__PASSPOINT_RELEASE_UNKNOWN;
        }
    }

    private int convertApType6GhzToProto(ApType6GHz apType6Ghz) {
        if (apType6Ghz == ApType6GHz.AP_TYPE_6GHZ_INDOOR) {
            return WifiStatsLog.WIFI_AP_CAPABILITIES_REPORTED__AP_TYPE_6GHZ__AP_TYPE_6GHZ_INDOOR;
        } else if (apType6Ghz == ApType6GHz.AP_TYPE_6GHZ_STANDARD_POWER) {
            return WifiStatsLog.WIFI_AP_CAPABILITIES_REPORTED__AP_TYPE_6GHZ__AP_TYPE_6GHZ_STANDARD_POWER;
        } else {
            return WifiStatsLog.WIFI_AP_CAPABILITIES_REPORTED__AP_TYPE_6GHZ__AP_TYPE_6HZ_UNKNOWN;
        }
    }

    private int convertWifiStandardToProto(int wifiMode) {
        switch (wifiMode) {
            case WifiMode.MODE_11A:
            case WifiMode.MODE_11B:
            case WifiMode.MODE_11G:
                return WifiStatsLog.WIFI_AP_CAPABILITIES_REPORTED__STANDARD__WIFI_STANDARD_LEGACY;
            case WifiMode.MODE_11N:
                return WifiStatsLog.WIFI_AP_CAPABILITIES_REPORTED__STANDARD__WIFI_STANDARD_11N;
            case WifiMode.MODE_11AC:
                return WifiStatsLog.WIFI_AP_CAPABILITIES_REPORTED__STANDARD__WIFI_STANDARD_11AC;
            case WifiMode.MODE_11AX:
                return WifiStatsLog.WIFI_AP_CAPABILITIES_REPORTED__STANDARD__WIFI_STANDARD_11AX;
            case WifiMode.MODE_11BE:
                return WifiStatsLog.WIFI_AP_CAPABILITIES_REPORTED__STANDARD__WIFI_STANDARD_11BE;
            case WifiMode.MODE_UNDEFINED:
            default:
                return WifiStatsLog.WIFI_AP_CAPABILITIES_REPORTED__STANDARD__WIFI_STANDARD_UNKNOWN;
        }

    }

    protected static int convertEapMethodToProto(WifiConfiguration config) {
        if (config.enterpriseConfig == null) {
            return WifiStatsLog.WIFI_AP_CAPABILITIES_REPORTED__EAP_TYPE__TYPE_UNKNOWN;
        }
        return convertEapMethodToProto(config.enterpriseConfig.getEapMethod());
    }

    private static int convertEapMethodToProto(int eapMethod) {
        switch (eapMethod) {
            case WifiMetricsProto.RouterFingerPrint.TYPE_EAP_WAPI_CERT:
                return WifiStatsLog.WIFI_AP_CAPABILITIES_REPORTED__EAP_TYPE__TYPE_EAP_WAPI_CERT;
            case WifiMetricsProto.RouterFingerPrint.TYPE_EAP_TLS:
                return WifiStatsLog.WIFI_AP_CAPABILITIES_REPORTED__EAP_TYPE__TYPE_EAP_TLS;
            case WifiMetricsProto.RouterFingerPrint.TYPE_EAP_UNAUTH_TLS:
                return WifiStatsLog.WIFI_AP_CAPABILITIES_REPORTED__EAP_TYPE__TYPE_EAP_UNAUTH_TLS;
            case WifiMetricsProto.RouterFingerPrint.TYPE_EAP_PEAP:
                return WifiStatsLog.WIFI_AP_CAPABILITIES_REPORTED__EAP_TYPE__TYPE_EAP_PEAP;
            case WifiMetricsProto.RouterFingerPrint.TYPE_EAP_PWD:
                return WifiStatsLog.WIFI_AP_CAPABILITIES_REPORTED__EAP_TYPE__TYPE_EAP_PWD;
            case WifiMetricsProto.RouterFingerPrint.TYPE_EAP_TTLS:
                return WifiStatsLog.WIFI_AP_CAPABILITIES_REPORTED__EAP_TYPE__TYPE_EAP_TTLS;
            case WifiMetricsProto.RouterFingerPrint.TYPE_EAP_SIM:
                return WifiStatsLog.WIFI_AP_CAPABILITIES_REPORTED__EAP_TYPE__TYPE_EAP_SIM;
            case WifiMetricsProto.RouterFingerPrint.TYPE_EAP_AKA:
                return WifiStatsLog.WIFI_AP_CAPABILITIES_REPORTED__EAP_TYPE__TYPE_EAP_AKA;
            case WifiMetricsProto.RouterFingerPrint.TYPE_EAP_AKA_PRIME:
                return WifiStatsLog.WIFI_AP_CAPABILITIES_REPORTED__EAP_TYPE__TYPE_EAP_AKA_PRIME;
            default:
                return WifiStatsLog.WIFI_AP_CAPABILITIES_REPORTED__EAP_TYPE__TYPE_UNKNOWN;
        }
    }

    protected static int convertEapInnerMethodToProto(WifiConfiguration config) {
        if (config.enterpriseConfig == null) {
            return WifiStatsLog.WIFI_AP_CAPABILITIES_REPORTED__EAP_INNER_METHOD__METHOD_UNKNOWN;
        }
        int phase2Method = config.enterpriseConfig.getPhase2Method();
        return convertEapInnerMethodToProto(getAuthPhase2MethodProto(phase2Method));
    }

    private static int convertEapInnerMethodToProto(int phase2Method) {
        switch (phase2Method) {
            case WifiMetricsProto.RouterFingerPrint.TYPE_PHASE2_PAP:
                return WifiStatsLog.WIFI_AP_CAPABILITIES_REPORTED__EAP_INNER_METHOD__METHOD_PAP;
            case WifiEnterpriseConfig.Phase2.MSCHAP:
                return WifiStatsLog.WIFI_AP_CAPABILITIES_REPORTED__EAP_INNER_METHOD__METHOD_MSCHAP;
            case WifiMetricsProto.RouterFingerPrint.TYPE_PHASE2_MSCHAPV2:
                return WifiStatsLog.WIFI_AP_CAPABILITIES_REPORTED__EAP_INNER_METHOD__METHOD_MSCHAP_V2;
            case WifiMetricsProto.RouterFingerPrint.TYPE_PHASE2_GTC:
                return WifiStatsLog.WIFI_AP_CAPABILITIES_REPORTED__EAP_INNER_METHOD__METHOD_GTC;
            case WifiMetricsProto.RouterFingerPrint.TYPE_PHASE2_SIM:
                return WifiStatsLog.WIFI_AP_CAPABILITIES_REPORTED__EAP_INNER_METHOD__METHOD_SIM;
            case WifiMetricsProto.RouterFingerPrint.TYPE_PHASE2_AKA:
                return WifiStatsLog.WIFI_AP_CAPABILITIES_REPORTED__EAP_INNER_METHOD__METHOD_AKA;
            case WifiMetricsProto.RouterFingerPrint.TYPE_PHASE2_AKA_PRIME:
                return WifiStatsLog.WIFI_AP_CAPABILITIES_REPORTED__EAP_INNER_METHOD__METHOD_AKA_PRIME;
            default:
                return WifiStatsLog.WIFI_AP_CAPABILITIES_REPORTED__EAP_INNER_METHOD__METHOD_UNKNOWN;
        }
    }

    private int convertOcspTypeToProto(int ocspType) {
        switch (ocspType) {
            case WifiMetricsProto.RouterFingerPrint.TYPE_OCSP_NONE:
                return WifiStatsLog.WIFI_AP_CAPABILITIES_REPORTED__OCSP_TYPE__TYPE_OCSP_NONE;
            case WifiMetricsProto.RouterFingerPrint.TYPE_OCSP_REQUEST_CERT_STATUS:
                return WifiStatsLog.WIFI_AP_CAPABILITIES_REPORTED__OCSP_TYPE__TYPE_OCSP_REQUEST_CERT_STATUS;
            case WifiMetricsProto.RouterFingerPrint.TYPE_OCSP_REQUIRE_CERT_STATUS:
                return WifiStatsLog.WIFI_AP_CAPABILITIES_REPORTED__OCSP_TYPE__TYPE_OCSP_REQUIRE_CERT_STATUS;
            case WifiMetricsProto.RouterFingerPrint.TYPE_OCSP_REQUIRE_ALL_NON_TRUSTED_CERTS_STATUS:
                return WifiStatsLog.WIFI_AP_CAPABILITIES_REPORTED__OCSP_TYPE__TYPE_OCSP_REQUIRE_ALL_NON_TRUSTED_CERTS_STATUS;
            default:
                return WifiStatsLog.WIFI_AP_CAPABILITIES_REPORTED__OCSP_TYPE__TYPE_OCSP_UNKNOWN;
        }
    }

    protected static boolean isFreeOpenRoaming(WifiConfiguration config) {
        return Utils.getRoamingType(config)
                == WifiStatsLog.WIFI_CONNECTION_RESULT_REPORTED__PASSPOINT_ROAMING_TYPE__ROAMING_RCOI_OPENROAMING_FREE;
    }

    protected static boolean isSettledOpenRoaming(WifiConfiguration config) {
        return Utils.getRoamingType(config)
                == WifiStatsLog.WIFI_CONNECTION_RESULT_REPORTED__PASSPOINT_ROAMING_TYPE__ROAMING_RCOI_OPENROAMING_SETTLED;
    }

    private void reportRouterCapabilities(RouterFingerPrint r) {
        WifiStatsLog.write(WifiStatsLog.WIFI_AP_CAPABILITIES_REPORTED,
                r.mIsFrameworkInitiatedRoaming, r.mRouterFingerPrintProto.channelInfo,
                KnownBandsChannelHelper.getBand(r.mRouterFingerPrintProto.channelInfo),
                r.mRouterFingerPrintProto.dtim, convertSecurityModeToProto(r.mSecurityMode),
                r.mRouterFingerPrintProto.hidden, r.mIsIncorrectlyConfiguredAsHidden,
                convertWifiStandardToProto(r.mWifiStandard), r.mIs11bSupported,
                convertEapMethodToProto(r.mRouterFingerPrintProto.eapMethod),
                convertEapInnerMethodToProto(r.mRouterFingerPrintProto.authPhase2Method),
                convertOcspTypeToProto(r.mRouterFingerPrintProto.ocspType),
                r.mRouterFingerPrintProto.pmkCacheEnabled, r.mIsMboSupported, r.mIsOceSupported,
                r.mIsFilsSupported, r.mIsTwtRequired, r.mIsIndividualTwtSupported,
                r.mIsBroadcastTwtSupported, r.mIsRestrictedTwtSupported, r.mIs11McSupported,
                r.mIs11AzSupported, convertHsReleasetoProto(r.mHsRelease),
                r.mRouterFingerPrintProto.isPasspointHomeProvider,
                convertApType6GhzToProto(r.mApType6GHz), r.mIsEcpsPriorityAccessSupported);
    }

    /**
     * Report that an active Wifi network connection was dropped.
     *
     * @param disconnectReason Error code for the disconnect.
     * @param rssi Last seen RSSI.
     * @param linkSpeed Last seen link speed.
     */
    public void reportNetworkDisconnect(String ifaceName, int disconnectReason, int rssi,
            int linkSpeed, long lastRssiUpdateMillis) {
        synchronized (mLock) {
            if (!isPrimary(ifaceName)) {
                return;
            }
            if (mCurrentSession != null) {
                if (mCurrentSession.mConnectionEvent.mRole == WifiStatsLog.WIFI_CONNECTION_RESULT_REPORTED__ROLE__ROLE_CLIENT_PRIMARY) {
                    WifiStatsLog.write(WifiStatsLog.WIFI_CONNECTION_STATE_CHANGED,
                            false,
                            mCurrentSession.mBand,
                            mCurrentSession.mAuthType);
                }
                mCurrentSession.mSessionEndTimeMillis = mClock.getElapsedSinceBootMillis();
                int durationSeconds = (int) (mCurrentSession.mSessionEndTimeMillis
                        - mCurrentSession.mSessionStartTimeMillis) / 1000;
                int connectedSinceLastRoamSeconds = (int) (mCurrentSession.mSessionEndTimeMillis
                        - mCurrentSession.mLastRoamCompleteMillis) / 1000;
                int timeSinceLastRssiUpdateSeconds = (int) (mClock.getElapsedSinceBootMillis()
                        - lastRssiUpdateMillis) / 1000;

                WifiStatsLog.write(WifiStatsLog.WIFI_DISCONNECT_REPORTED,
                        durationSeconds,
                        disconnectReason,
                        mCurrentSession.mBand,
                        mCurrentSession.mAuthType,
                        rssi,
                        linkSpeed,
                        timeSinceLastRssiUpdateSeconds,
                        connectedSinceLastRoamSeconds,
                        mCurrentSession.mConnectionEvent.mRole,
                        toMetricEapType(mCurrentSession.mConnectionEvent.mEapType),
                        toMetricPhase2Method(mCurrentSession.mConnectionEvent.mPhase2Method),
                        mCurrentSession.mConnectionEvent.mPasspointRoamingType,
                        mCurrentSession.mConnectionEvent.mCarrierId);

                mPreviousSession = mCurrentSession;
                mCurrentSession = null;
            }
        }
    }

    /**
     * Report an airplane mode session.
     *
     * @param wifiOnBeforeEnteringApm Whether Wi-Fi is on before entering airplane mode
     * @param wifiOnAfterEnteringApm Whether Wi-Fi is on after entering airplane mode
     * @param wifiOnBeforeExitingApm Whether Wi-Fi is on before exiting airplane mode
     * @param apmEnhancementActive Whether the user has activated the airplane mode enhancement
     *                            feature by toggling Wi-Fi in airplane mode
     * @param userToggledWifiDuringApm Whether the user toggled Wi-Fi during the current
     *                                  airplane mode
     * @param userToggledWifiAfterEnteringApmWithinMinute Whether the user toggled Wi-Fi within one
     *                                                    minute of entering airplane mode
     */
    public void reportAirplaneModeSession(boolean wifiOnBeforeEnteringApm,
            boolean wifiOnAfterEnteringApm, boolean wifiOnBeforeExitingApm,
            boolean apmEnhancementActive, boolean userToggledWifiDuringApm,
            boolean userToggledWifiAfterEnteringApmWithinMinute) {
        WifiStatsLog.write(WifiStatsLog.AIRPLANE_MODE_SESSION_REPORTED,
                WifiStatsLog.AIRPLANE_MODE_SESSION_REPORTED__PACKAGE_NAME__WIFI,
                wifiOnBeforeEnteringApm, wifiOnAfterEnteringApm, wifiOnBeforeExitingApm,
                apmEnhancementActive, userToggledWifiDuringApm,
                userToggledWifiAfterEnteringApmWithinMinute, false);
    }

    /**
     * Report a Wi-Fi state change.
     *
     * @param wifiState Whether Wi-Fi is enabled
     * @param wifiWakeState Whether Wi-Fi Wake is enabled
     * @param enabledByWifiWake Whether Wi-Fi was enabled by Wi-Fi Wake
     */
    public void reportWifiStateChanged(boolean wifiState, boolean wifiWakeState,
            boolean enabledByWifiWake) {
        WifiStatsLog.write(WifiStatsLog.WIFI_STATE_CHANGED, wifiState, wifiWakeState,
                enabledByWifiWake);
    }

    private int getConnectionResultFailureCode(int level2FailureCode, int level2FailureReason) {
        switch (level2FailureCode) {
            case ConnectionEvent.FAILURE_NONE:
                return WifiStatsLog.WIFI_CONNECTION_RESULT_REPORTED__FAILURE_CODE__FAILURE_UNKNOWN;
            case ConnectionEvent.FAILURE_ASSOCIATION_TIMED_OUT:
                return WifiStatsLog.WIFI_CONNECTION_RESULT_REPORTED__FAILURE_CODE__FAILURE_ASSOCIATION_TIMEOUT;
            case ConnectionEvent.FAILURE_ASSOCIATION_REJECTION:
                return WifiStatsLog.WIFI_CONNECTION_RESULT_REPORTED__FAILURE_CODE__FAILURE_ASSOCIATION_REJECTION;
            case ConnectionEvent.FAILURE_AUTHENTICATION_FAILURE:
                switch (level2FailureReason) {
                    case WifiMetricsProto.ConnectionEvent.AUTH_FAILURE_EAP_FAILURE:
                        return WifiStatsLog.WIFI_CONNECTION_RESULT_REPORTED__FAILURE_CODE__FAILURE_AUTHENTICATION_EAP;
                    case WifiMetricsProto.ConnectionEvent.AUTH_FAILURE_WRONG_PSWD:
                        return WifiStatsLog.WIFI_CONNECTION_RESULT_REPORTED__FAILURE_CODE__FAILURE_WRONG_PASSWORD;
                    default:
                        return WifiStatsLog.WIFI_CONNECTION_RESULT_REPORTED__FAILURE_CODE__FAILURE_AUTHENTICATION_GENERAL;
                }
            case ConnectionEvent.FAILURE_DHCP:
                return WifiStatsLog.WIFI_CONNECTION_RESULT_REPORTED__FAILURE_CODE__FAILURE_DHCP;
            case ConnectionEvent.FAILURE_NETWORK_DISCONNECTION:
                return WifiStatsLog.WIFI_CONNECTION_RESULT_REPORTED__FAILURE_CODE__FAILURE_NETWORK_DISCONNECTION;
            case ConnectionEvent.FAILURE_ROAM_TIMEOUT:
                return WifiStatsLog.WIFI_CONNECTION_RESULT_REPORTED__FAILURE_CODE__FAILURE_ROAM_TIMEOUT;
            case ConnectionEvent.FAILURE_CONNECT_NETWORK_FAILED:
                return WifiStatsLog.WIFI_CONNECTION_RESULT_REPORTED__FAILURE_CODE__FAILURE_CONNECT_NETWORK_FAILED;
            case ConnectionEvent.FAILURE_NEW_CONNECTION_ATTEMPT:
                return WifiStatsLog.WIFI_CONNECTION_RESULT_REPORTED__FAILURE_CODE__FAILURE_NEW_CONNECTION_ATTEMPT;
            case ConnectionEvent.FAILURE_REDUNDANT_CONNECTION_ATTEMPT:
                return WifiStatsLog.WIFI_CONNECTION_RESULT_REPORTED__FAILURE_CODE__FAILURE_REDUNDANT_CONNECTION_ATTEMPT;
            case ConnectionEvent.FAILURE_NETWORK_NOT_FOUND:
                return WifiStatsLog.WIFI_CONNECTION_RESULT_REPORTED__FAILURE_CODE__FAILURE_NETWORK_NOT_FOUND;
            case ConnectionEvent.FAILURE_NO_RESPONSE:
                return WifiStatsLog.WIFI_CONNECTION_RESULT_REPORTED__FAILURE_CODE__FAILURE_NO_RESPONSE;
            default:
                return WifiStatsLog.WIFI_CONNECTION_RESULT_REPORTED__FAILURE_CODE__FAILURE_OTHERS;
        }
    }

    /**
     * Set ConnectionEvent DTIM Interval (if set), and 802.11 Connection mode, from NetworkDetail
     */
    private void updateMetricsFromNetworkDetail(
            ConnectionEvent currentConnectionEvent, NetworkDetail networkDetail) {
        int dtimInterval = networkDetail.getDtimInterval();
        if (dtimInterval > 0) {
            currentConnectionEvent.mRouterFingerPrint.mRouterFingerPrintProto.dtim =
                    dtimInterval;
        }

        if (currentConnectionEvent.mRouterFingerPrint.mRouterFingerPrintProto.hidden
                && !networkDetail.isHiddenBeaconFrame()) {
            currentConnectionEvent.mRouterFingerPrint.mIsIncorrectlyConfiguredAsHidden = true;
        }

        final int connectionWifiMode;
        switch (networkDetail.getWifiMode()) {
            case InformationElementUtil.WifiMode.MODE_UNDEFINED:
                connectionWifiMode = WifiMetricsProto.RouterFingerPrint.ROUTER_TECH_UNKNOWN;
                break;
            case InformationElementUtil.WifiMode.MODE_11A:
                connectionWifiMode = WifiMetricsProto.RouterFingerPrint.ROUTER_TECH_A;
                break;
            case InformationElementUtil.WifiMode.MODE_11B:
                currentConnectionEvent.mRouterFingerPrint.mIs11bSupported = true;
                connectionWifiMode = WifiMetricsProto.RouterFingerPrint.ROUTER_TECH_B;
                break;
            case InformationElementUtil.WifiMode.MODE_11G:
                connectionWifiMode = WifiMetricsProto.RouterFingerPrint.ROUTER_TECH_G;
                break;
            case InformationElementUtil.WifiMode.MODE_11N:
                connectionWifiMode = WifiMetricsProto.RouterFingerPrint.ROUTER_TECH_N;
                break;
            case InformationElementUtil.WifiMode.MODE_11AC  :
                connectionWifiMode = WifiMetricsProto.RouterFingerPrint.ROUTER_TECH_AC;
                break;
            case InformationElementUtil.WifiMode.MODE_11AX  :
                connectionWifiMode = WifiMetricsProto.RouterFingerPrint.ROUTER_TECH_AX;
                break;
            default:
                connectionWifiMode = WifiMetricsProto.RouterFingerPrint.ROUTER_TECH_OTHER;
                break;
        }
        currentConnectionEvent.mRouterFingerPrint.mRouterFingerPrintProto.routerTechnology =
                connectionWifiMode;
        currentConnectionEvent.mRouterFingerPrint.mWifiStandard = networkDetail.getWifiMode();

        if (networkDetail.isMboSupported()) {
            mWifiLogProto.numConnectToNetworkSupportingMbo++;
            if (networkDetail.isOceSupported()) {
                mWifiLogProto.numConnectToNetworkSupportingOce++;
            }
        }

        currentConnectionEvent.mRouterFingerPrint.mApType6GHz =
                networkDetail.getApType6GHz();
        currentConnectionEvent.mRouterFingerPrint.mIsBroadcastTwtSupported =
                networkDetail.isBroadcastTwtSupported();
        currentConnectionEvent.mRouterFingerPrint.mIsRestrictedTwtSupported =
                networkDetail.isRestrictedTwtSupported();
        currentConnectionEvent.mRouterFingerPrint.mIsIndividualTwtSupported =
                networkDetail.isIndividualTwtSupported();
        currentConnectionEvent.mRouterFingerPrint.mIsTwtRequired = networkDetail.isTwtRequired();
        currentConnectionEvent.mRouterFingerPrint.mIsFilsSupported = networkDetail.isFilsCapable();
        currentConnectionEvent.mRouterFingerPrint.mIs11AzSupported =
                networkDetail.is11azSupported();
        currentConnectionEvent.mRouterFingerPrint.mIs11McSupported =
                networkDetail.is80211McResponderSupport();
        currentConnectionEvent.mRouterFingerPrint.mIsMboSupported = networkDetail.isMboSupported();
        currentConnectionEvent.mRouterFingerPrint.mIsOceSupported = networkDetail.isOceSupported();
        currentConnectionEvent.mRouterFingerPrint.mIsEcpsPriorityAccessSupported =
                networkDetail.isEpcsPriorityAccessSupported();
        currentConnectionEvent.mRouterFingerPrint.mHsRelease = networkDetail.getHSRelease();
    }

    /**
     * Set ConnectionEvent RSSI and authentication type from ScanResult
     */
    private void updateMetricsFromScanResult(
            ConnectionEvent currentConnectionEvent, ScanResult scanResult) {
        currentConnectionEvent.mConnectionEvent.signalStrength = scanResult.level;
        currentConnectionEvent.mRouterFingerPrint.mRouterFingerPrintProto.authentication =
                WifiMetricsProto.RouterFingerPrint.AUTH_OPEN;
        currentConnectionEvent.mConfigBssid = scanResult.BSSID;
        if (scanResult.capabilities != null) {
            if (ScanResultUtil.isScanResultForWepNetwork(scanResult)) {
                currentConnectionEvent.mRouterFingerPrint.mRouterFingerPrintProto.authentication =
                        WifiMetricsProto.RouterFingerPrint.AUTH_PERSONAL;
            } else if (ScanResultUtil.isScanResultForPskNetwork(scanResult)
                    || ScanResultUtil.isScanResultForSaeNetwork(scanResult)) {
                currentConnectionEvent.mRouterFingerPrint.mRouterFingerPrintProto.authentication =
                        WifiMetricsProto.RouterFingerPrint.AUTH_PERSONAL;
            } else if (ScanResultUtil.isScanResultForWpa3EnterpriseTransitionNetwork(scanResult)
                    || ScanResultUtil.isScanResultForWpa3EnterpriseOnlyNetwork(scanResult)
                    || ScanResultUtil.isScanResultForWpa2EnterpriseOnlyNetwork(scanResult)
                    || ScanResultUtil.isScanResultForEapSuiteBNetwork(scanResult)) {
                currentConnectionEvent.mRouterFingerPrint.mRouterFingerPrintProto.authentication =
                        WifiMetricsProto.RouterFingerPrint.AUTH_ENTERPRISE;
            }
        }
        currentConnectionEvent.mRouterFingerPrint.mRouterFingerPrintProto.channelInfo =
                scanResult.frequency;
    }

    void setIsLocationEnabled(boolean enabled) {
        synchronized (mLock) {
            mWifiLogProto.isLocationEnabled = enabled;
        }
    }

    void setIsScanningAlwaysEnabled(boolean enabled) {
        synchronized (mLock) {
            mWifiLogProto.isScanningAlwaysEnabled = enabled;
        }
    }

    /**
     * Developer options toggle value for verbose logging.
     */
    public void setVerboseLoggingEnabled(boolean enabled) {
        synchronized (mLock) {
            mWifiLogProto.isVerboseLoggingEnabled = enabled;
        }
    }

    /**
     * Developer options toggle value for non-persistent MAC randomization.
     */
    public void setNonPersistentMacRandomizationForceEnabled(boolean enabled) {
        synchronized (mLock) {
            mWifiLogProto.isEnhancedMacRandomizationForceEnabled = enabled;
        }
    }

    /**
     * Wifi wake feature toggle.
     */
    public void setWifiWakeEnabled(boolean enabled) {
        synchronized (mLock) {
            mWifiLogProto.isWifiWakeEnabled = enabled;
        }
    }

    /**
     * Increment Non Empty Scan Results count
     */
    public void incrementNonEmptyScanResultCount() {
        if (DBG) Log.v(TAG, "incrementNonEmptyScanResultCount");
        synchronized (mLock) {
            mWifiLogProto.numNonEmptyScanResults++;
        }
    }

    /**
     * Increment Empty Scan Results count
     */
    public void incrementEmptyScanResultCount() {
        if (DBG) Log.v(TAG, "incrementEmptyScanResultCount");
        synchronized (mLock) {
            mWifiLogProto.numEmptyScanResults++;
        }
    }

    /**
     * Increment background scan count
     */
    public void incrementBackgroundScanCount() {
        if (DBG) Log.v(TAG, "incrementBackgroundScanCount");
        synchronized (mLock) {
            mWifiLogProto.numBackgroundScans++;
        }
    }

    /**
     * Get Background scan count
     */
    public int getBackgroundScanCount() {
        synchronized (mLock) {
            return mWifiLogProto.numBackgroundScans;
        }
    }

    /**
     * Increment oneshot scan count, and the associated WifiSystemScanStateCount entry
     */
    public void incrementOneshotScanCount() {
        synchronized (mLock) {
            mWifiLogProto.numOneshotScans++;
        }
        incrementWifiSystemScanStateCount(mWifiState, mScreenOn);
    }

    /**
     * Increment the count of oneshot scans that include DFS channels.
     */
    public void incrementOneshotScanWithDfsCount() {
        synchronized (mLock) {
            mWifiLogProto.numOneshotHasDfsChannelScans++;
        }
    }

    /**
     * Increment connectivity oneshot scan count.
     */
    public void incrementConnectivityOneshotScanCount() {
        synchronized (mLock) {
            mWifiLogProto.numConnectivityOneshotScans++;
        }
    }

    /**
     * Get oneshot scan count
     */
    public int getOneshotScanCount() {
        synchronized (mLock) {
            return mWifiLogProto.numOneshotScans;
        }
    }

    /**
     * Get connectivity oneshot scan count
     */
    public int getConnectivityOneshotScanCount() {
        synchronized (mLock) {
            return mWifiLogProto.numConnectivityOneshotScans;
        }
    }

    /**
     * Get the count of oneshot scan requests that included DFS channels.
     */
    public int getOneshotScanWithDfsCount() {
        synchronized (mLock) {
            return mWifiLogProto.numOneshotHasDfsChannelScans;
        }
    }

    /**
     * Increment oneshot scan count for external apps.
     */
    public void incrementExternalAppOneshotScanRequestsCount() {
        synchronized (mLock) {
            mWifiLogProto.numExternalAppOneshotScanRequests++;
        }
    }
    /**
     * Increment oneshot scan throttle count for external foreground apps.
     */
    public void incrementExternalForegroundAppOneshotScanRequestsThrottledCount() {
        synchronized (mLock) {
            mWifiLogProto.numExternalForegroundAppOneshotScanRequestsThrottled++;
        }
    }

    /**
     * Increment oneshot scan throttle count for external background apps.
     */
    public void incrementExternalBackgroundAppOneshotScanRequestsThrottledCount() {
        synchronized (mLock) {
            mWifiLogProto.numExternalBackgroundAppOneshotScanRequestsThrottled++;
        }
    }

    private String returnCodeToString(int scanReturnCode) {
        switch(scanReturnCode){
            case WifiMetricsProto.WifiLog.SCAN_UNKNOWN:
                return "SCAN_UNKNOWN";
            case WifiMetricsProto.WifiLog.SCAN_SUCCESS:
                return "SCAN_SUCCESS";
            case WifiMetricsProto.WifiLog.SCAN_FAILURE_INTERRUPTED:
                return "SCAN_FAILURE_INTERRUPTED";
            case WifiMetricsProto.WifiLog.SCAN_FAILURE_INVALID_CONFIGURATION:
                return "SCAN_FAILURE_INVALID_CONFIGURATION";
            case WifiMetricsProto.WifiLog.FAILURE_WIFI_DISABLED:
                return "FAILURE_WIFI_DISABLED";
            default:
                return "<UNKNOWN>";
        }
    }

    /**
     * Increment count of scan return code occurrence
     *
     * @param scanReturnCode Return code from scan attempt WifiMetricsProto.WifiLog.SCAN_X
     */
    public void incrementScanReturnEntry(int scanReturnCode, int countToAdd) {
        synchronized (mLock) {
            if (DBG) Log.v(TAG, "incrementScanReturnEntry " + returnCodeToString(scanReturnCode));
            int entry = mScanReturnEntries.get(scanReturnCode);
            entry += countToAdd;
            mScanReturnEntries.put(scanReturnCode, entry);
        }
    }
    /**
     * Get the count of this scanReturnCode
     * @param scanReturnCode that we are getting the count for
     */
    public int getScanReturnEntry(int scanReturnCode) {
        synchronized (mLock) {
            return mScanReturnEntries.get(scanReturnCode);
        }
    }

    private String wifiSystemStateToString(int state) {
        switch(state){
            case WifiMetricsProto.WifiLog.WIFI_UNKNOWN:
                return "WIFI_UNKNOWN";
            case WifiMetricsProto.WifiLog.WIFI_DISABLED:
                return "WIFI_DISABLED";
            case WifiMetricsProto.WifiLog.WIFI_DISCONNECTED:
                return "WIFI_DISCONNECTED";
            case WifiMetricsProto.WifiLog.WIFI_ASSOCIATED:
                return "WIFI_ASSOCIATED";
            default:
                return "default";
        }
    }

    /**
     * Increments the count of scans initiated by each wifi state, accounts for screenOn/Off
     *
     * @param state State of the system when scan was initiated, see WifiMetricsProto.WifiLog.WIFI_X
     * @param screenOn Is the screen on
     */
    public void incrementWifiSystemScanStateCount(int state, boolean screenOn) {
        synchronized (mLock) {
            if (DBG) {
                Log.v(TAG, "incrementWifiSystemScanStateCount " + wifiSystemStateToString(state)
                        + " " + screenOn);
            }
            int index = (state * 2) + (screenOn ? SCREEN_ON : SCREEN_OFF);
            int entry = mWifiSystemStateEntries.get(index);
            entry++;
            mWifiSystemStateEntries.put(index, entry);
        }
    }

    /**
     * Get the count of this system State Entry
     */
    public int getSystemStateCount(int state, boolean screenOn) {
        synchronized (mLock) {
            int index = state * 2 + (screenOn ? SCREEN_ON : SCREEN_OFF);
            return mWifiSystemStateEntries.get(index);
        }
    }

    /**
     * Increment number of times the Watchdog of Last Resort triggered, resetting the wifi stack
     */
    public void incrementNumLastResortWatchdogTriggers() {
        synchronized (mLock) {
            mWifiLogProto.numLastResortWatchdogTriggers++;
        }
    }
    /**
     * @param count number of networks over bad association threshold when watchdog triggered
     */
    public void addCountToNumLastResortWatchdogBadAssociationNetworksTotal(int count) {
        synchronized (mLock) {
            mWifiLogProto.numLastResortWatchdogBadAssociationNetworksTotal += count;
        }
    }
    /**
     * @param count number of networks over bad authentication threshold when watchdog triggered
     */
    public void addCountToNumLastResortWatchdogBadAuthenticationNetworksTotal(int count) {
        synchronized (mLock) {
            mWifiLogProto.numLastResortWatchdogBadAuthenticationNetworksTotal += count;
        }
    }
    /**
     * @param count number of networks over bad dhcp threshold when watchdog triggered
     */
    public void addCountToNumLastResortWatchdogBadDhcpNetworksTotal(int count) {
        synchronized (mLock) {
            mWifiLogProto.numLastResortWatchdogBadDhcpNetworksTotal += count;
        }
    }
    /**
     * @param count number of networks over bad other threshold when watchdog triggered
     */
    public void addCountToNumLastResortWatchdogBadOtherNetworksTotal(int count) {
        synchronized (mLock) {
            mWifiLogProto.numLastResortWatchdogBadOtherNetworksTotal += count;
        }
    }
    /**
     * @param count number of networks seen when watchdog triggered
     */
    public void addCountToNumLastResortWatchdogAvailableNetworksTotal(int count) {
        synchronized (mLock) {
            mWifiLogProto.numLastResortWatchdogAvailableNetworksTotal += count;
        }
    }
    /**
     * Increment count of triggers with atleast one bad association network
     */
    public void incrementNumLastResortWatchdogTriggersWithBadAssociation() {
        synchronized (mLock) {
            mWifiLogProto.numLastResortWatchdogTriggersWithBadAssociation++;
        }
    }
    /**
     * Increment count of triggers with atleast one bad authentication network
     */
    public void incrementNumLastResortWatchdogTriggersWithBadAuthentication() {
        synchronized (mLock) {
            mWifiLogProto.numLastResortWatchdogTriggersWithBadAuthentication++;
        }
    }
    /**
     * Increment count of triggers with atleast one bad dhcp network
     */
    public void incrementNumLastResortWatchdogTriggersWithBadDhcp() {
        synchronized (mLock) {
            mWifiLogProto.numLastResortWatchdogTriggersWithBadDhcp++;
        }
    }
    /**
     * Increment count of triggers with atleast one bad other network
     */
    public void incrementNumLastResortWatchdogTriggersWithBadOther() {
        synchronized (mLock) {
            mWifiLogProto.numLastResortWatchdogTriggersWithBadOther++;
        }
    }

    /**
     * Increment number of times connectivity watchdog confirmed pno is working
     */
    public void incrementNumConnectivityWatchdogPnoGood() {
        synchronized (mLock) {
            mWifiLogProto.numConnectivityWatchdogPnoGood++;
        }
    }
    /**
     * Increment number of times connectivity watchdog found pno not working
     */
    public void incrementNumConnectivityWatchdogPnoBad() {
        synchronized (mLock) {
            mWifiLogProto.numConnectivityWatchdogPnoBad++;
        }
    }
    /**
     * Increment number of times connectivity watchdog confirmed background scan is working
     */
    public void incrementNumConnectivityWatchdogBackgroundGood() {
        synchronized (mLock) {
            mWifiLogProto.numConnectivityWatchdogBackgroundGood++;
        }
    }
    /**
     * Increment number of times connectivity watchdog found background scan not working
     */
    public void incrementNumConnectivityWatchdogBackgroundBad() {
        synchronized (mLock) {
            mWifiLogProto.numConnectivityWatchdogBackgroundBad++;
        }
    }

    /**
     * Increment various poll related metrics, and cache performance data for StaEvent logging
     */
    public void handlePollResult(String ifaceName, WifiInfo wifiInfo) {
        if (!isPrimary(ifaceName)) {
            return;
        }
        mLastPollRssi = wifiInfo.getRssi();
        mLastPollLinkSpeed = wifiInfo.getLinkSpeed();
        mLastPollFreq = wifiInfo.getFrequency();
        incrementRssiPollRssiCount(mLastPollFreq, mLastPollRssi);
        incrementLinkSpeedCount(mLastPollLinkSpeed, mLastPollRssi);
        mLastPollRxLinkSpeed = wifiInfo.getRxLinkSpeedMbps();
        incrementTxLinkSpeedBandCount(mLastPollLinkSpeed, mLastPollFreq);
        incrementRxLinkSpeedBandCount(mLastPollRxLinkSpeed, mLastPollFreq);
        mWifiStatusBuilder.setRssi(mLastPollRssi);
        mWifiStatusBuilder.setNetworkId(wifiInfo.getNetworkId());
    }

    /**
     * Increment occurence count of RSSI level from RSSI poll for the given frequency.
     * @param frequency (MHz)
     * @param rssi
     */
    @VisibleForTesting
    public void incrementRssiPollRssiCount(int frequency, int rssi) {
        if (!(rssi >= MIN_RSSI_POLL && rssi <= MAX_RSSI_POLL)) {
            return;
        }
        synchronized (mLock) {
            if (!mRssiPollCountsMap.containsKey(frequency)) {
                mRssiPollCountsMap.put(frequency, new SparseIntArray());
            }
            SparseIntArray sparseIntArray = mRssiPollCountsMap.get(frequency);
            int count = sparseIntArray.get(rssi);
            sparseIntArray.put(rssi, count + 1);
            maybeIncrementRssiDeltaCount(rssi - mScanResultRssi);
        }
    }

    /**
     * Increment occurence count of difference between scan result RSSI and the first RSSI poll.
     * Ignores rssi values outside the bounds of [MIN_RSSI_DELTA, MAX_RSSI_DELTA]
     * mLock must be held when calling this method.
     */
    private void maybeIncrementRssiDeltaCount(int rssi) {
        // Check if this RSSI poll is close enough to a scan result RSSI to log a delta value
        if (mScanResultRssiTimestampMillis >= 0) {
            long timeDelta = mClock.getElapsedSinceBootMillis() - mScanResultRssiTimestampMillis;
            if (timeDelta <= TIMEOUT_RSSI_DELTA_MILLIS) {
                if (rssi >= MIN_RSSI_DELTA && rssi <= MAX_RSSI_DELTA) {
                    int count = mRssiDeltaCounts.get(rssi);
                    mRssiDeltaCounts.put(rssi, count + 1);
                }
            }
            mScanResultRssiTimestampMillis = -1;
        }
    }

    /**
     * Increment occurrence count of link speed.
     * Ignores link speed values that are lower than MIN_LINK_SPEED_MBPS
     * and rssi values outside the bounds of [MIN_RSSI_POLL, MAX_RSSI_POLL]
     */
    @VisibleForTesting
    public void incrementLinkSpeedCount(int linkSpeed, int rssi) {
        if (!(mContext.getResources().getBoolean(R.bool.config_wifiLinkSpeedMetricsEnabled)
                && linkSpeed >= MIN_LINK_SPEED_MBPS
                && rssi >= MIN_RSSI_POLL
                && rssi <= MAX_RSSI_POLL)) {
            return;
        }
        synchronized (mLock) {
            LinkSpeedCount linkSpeedCount = mLinkSpeedCounts.get(linkSpeed);
            if (linkSpeedCount == null) {
                linkSpeedCount = new LinkSpeedCount();
                linkSpeedCount.linkSpeedMbps = linkSpeed;
                mLinkSpeedCounts.put(linkSpeed, linkSpeedCount);
            }
            linkSpeedCount.count++;
            linkSpeedCount.rssiSumDbm += Math.abs(rssi);
            linkSpeedCount.rssiSumOfSquaresDbmSq += rssi * rssi;
        }
    }

    /**
     * Increment occurrence count of Tx link speed for operating sub-band
     * Ignores link speed values that are lower than MIN_LINK_SPEED_MBPS
     * @param txLinkSpeed PHY layer Tx link speed in Mbps
     * @param frequency Channel frequency of beacon frames in MHz
     */
    @VisibleForTesting
    public void incrementTxLinkSpeedBandCount(int txLinkSpeed, int frequency) {
        if (!(mContext.getResources().getBoolean(R.bool.config_wifiLinkSpeedMetricsEnabled)
                && txLinkSpeed >= MIN_LINK_SPEED_MBPS)) {
            return;
        }
        synchronized (mLock) {
            if (ScanResult.is24GHz(frequency)) {
                mTxLinkSpeedCount2g.increment(txLinkSpeed);
            } else if (frequency <= KnownBandsChannelHelper.BAND_5_GHZ_LOW_END_FREQ) {
                mTxLinkSpeedCount5gLow.increment(txLinkSpeed);
            } else if (frequency <= KnownBandsChannelHelper.BAND_5_GHZ_MID_END_FREQ) {
                mTxLinkSpeedCount5gMid.increment(txLinkSpeed);
            } else if (frequency <= KnownBandsChannelHelper.BAND_5_GHZ_HIGH_END_FREQ) {
                mTxLinkSpeedCount5gHigh.increment(txLinkSpeed);
            } else if (frequency <= KnownBandsChannelHelper.BAND_6_GHZ_LOW_END_FREQ) {
                mTxLinkSpeedCount6gLow.increment(txLinkSpeed);
            } else if (frequency <= KnownBandsChannelHelper.BAND_6_GHZ_MID_END_FREQ) {
                mTxLinkSpeedCount6gMid.increment(txLinkSpeed);
            } else if (frequency <= KnownBandsChannelHelper.BAND_6_GHZ_HIGH_END_FREQ) {
                mTxLinkSpeedCount6gHigh.increment(txLinkSpeed);
            }
        }
    }

    /**
     * Increment occurrence count of Rx link speed for operating sub-band
     * Ignores link speed values that are lower than MIN_LINK_SPEED_MBPS
     * @param rxLinkSpeed PHY layer Tx link speed in Mbps
     * @param frequency Channel frequency of beacon frames in MHz
     */
    @VisibleForTesting
    public void incrementRxLinkSpeedBandCount(int rxLinkSpeed, int frequency) {
        if (!(mContext.getResources().getBoolean(R.bool.config_wifiLinkSpeedMetricsEnabled)
                && rxLinkSpeed >= MIN_LINK_SPEED_MBPS)) {
            return;
        }
        synchronized (mLock) {
            if (ScanResult.is24GHz(frequency)) {
                mRxLinkSpeedCount2g.increment(rxLinkSpeed);
            } else if (frequency <= KnownBandsChannelHelper.BAND_5_GHZ_LOW_END_FREQ) {
                mRxLinkSpeedCount5gLow.increment(rxLinkSpeed);
            } else if (frequency <= KnownBandsChannelHelper.BAND_5_GHZ_MID_END_FREQ) {
                mRxLinkSpeedCount5gMid.increment(rxLinkSpeed);
            } else if (frequency <= KnownBandsChannelHelper.BAND_5_GHZ_HIGH_END_FREQ) {
                mRxLinkSpeedCount5gHigh.increment(rxLinkSpeed);
            } else if (frequency <= KnownBandsChannelHelper.BAND_6_GHZ_LOW_END_FREQ) {
                mRxLinkSpeedCount6gLow.increment(rxLinkSpeed);
            } else if (frequency <= KnownBandsChannelHelper.BAND_6_GHZ_MID_END_FREQ) {
                mRxLinkSpeedCount6gMid.increment(rxLinkSpeed);
            } else if (frequency <= KnownBandsChannelHelper.BAND_6_GHZ_HIGH_END_FREQ) {
                mRxLinkSpeedCount6gHigh.increment(rxLinkSpeed);
            }
        }
    }

    /**
     * Increment occurrence count of channel utilization
     * @param channelUtilization Channel utilization of current network
     * @param frequency Channel frequency of current network
     */
    @VisibleForTesting
    public void incrementChannelUtilizationCount(int channelUtilization, int frequency) {
        if (channelUtilization < InformationElementUtil.BssLoad.MIN_CHANNEL_UTILIZATION
                || channelUtilization > InformationElementUtil.BssLoad.MAX_CHANNEL_UTILIZATION) {
            return;
        }
        synchronized (mLock) {
            if (ScanResult.is24GHz(frequency)) {
                mChannelUtilizationHistogram2G.increment(channelUtilization);
            } else {
                mChannelUtilizationHistogramAbove2G.increment(channelUtilization);
            }
        }
    }

    /**
     * Increment occurrence count of Tx and Rx throughput
     * @param txThroughputKbps Tx throughput of current network in Kbps
     * @param rxThroughputKbps Rx throughput of current network in Kbps
     * @param frequency Channel frequency of current network in MHz
     */
    @VisibleForTesting
    public void incrementThroughputKbpsCount(int txThroughputKbps, int rxThroughputKbps,
            int frequency) {
        synchronized (mLock) {
            if (ScanResult.is24GHz(frequency)) {
                if (txThroughputKbps >= 0) {
                    mTxThroughputMbpsHistogram2G.increment(txThroughputKbps / 1000);
                }
                if (rxThroughputKbps >= 0) {
                    mRxThroughputMbpsHistogram2G.increment(rxThroughputKbps / 1000);
                }
            } else {
                if (txThroughputKbps >= 0) {
                    mTxThroughputMbpsHistogramAbove2G.increment(txThroughputKbps / 1000);
                }
                if (rxThroughputKbps >= 0) {
                    mRxThroughputMbpsHistogramAbove2G.increment(rxThroughputKbps / 1000);
                }
            }
            mWifiStatusBuilder.setEstimatedTxKbps(txThroughputKbps);
            mWifiStatusBuilder.setEstimatedRxKbps(rxThroughputKbps);
        }
    }

    /**
     * Increment count of Watchdog successes.
     */
    public void incrementNumLastResortWatchdogSuccesses() {
        synchronized (mLock) {
            mWifiLogProto.numLastResortWatchdogSuccesses++;
        }
    }

    /**
     * Increment the count of network connection failures that happened after watchdog has been
     * triggered.
     */
    public void incrementWatchdogTotalConnectionFailureCountAfterTrigger() {
        synchronized (mLock) {
            mWifiLogProto.watchdogTotalConnectionFailureCountAfterTrigger++;
        }
    }

    /**
     * Sets the time taken for wifi to connect after a watchdog triggers a restart.
     * @param milliseconds
     */
    public void setWatchdogSuccessTimeDurationMs(long ms) {
        synchronized (mLock) {
            mWifiLogProto.watchdogTriggerToConnectionSuccessDurationMs = ms;
        }
    }

    /**
     * Increments the count of alerts by alert reason.
     *
     * @param reason The cause of the alert. The reason values are driver-specific.
     */
    private void incrementAlertReasonCount(int reason) {
        if (reason > WifiLoggerHal.WIFI_ALERT_REASON_MAX
                || reason < WifiLoggerHal.WIFI_ALERT_REASON_MIN) {
            reason = WifiLoggerHal.WIFI_ALERT_REASON_RESERVED;
        }
        synchronized (mLock) {
            int alertCount = mWifiAlertReasonCounts.get(reason);
            mWifiAlertReasonCounts.put(reason, alertCount + 1);
        }
    }

    /**
     * Counts all the different types of networks seen in a set of scan results
     */
    public void countScanResults(List<ScanDetail> scanDetails) {
        if (scanDetails == null) {
            return;
        }
        int totalResults = 0;
        int openNetworks = 0;
        int personalNetworks = 0;
        int enterpriseNetworks = 0;
        int hiddenNetworks = 0;
        int hotspot2r1Networks = 0;
        int hotspot2r2Networks = 0;
        int hotspot2r3Networks = 0;
        int enhacedOpenNetworks = 0;
        int wpa3PersonalNetworks = 0;
        int wpa3EnterpriseNetworks = 0;
        int wapiPersonalNetworks = 0;
        int wapiEnterpriseNetworks = 0;
        int mboSupportedNetworks = 0;
        int mboCellularDataAwareNetworks = 0;
        int oceSupportedNetworks = 0;
        int filsSupportedNetworks = 0;
        int band6gNetworks = 0;
        int band6gPscNetworks = 0;
        int standard11axNetworks = 0;

        for (ScanDetail scanDetail : scanDetails) {
            NetworkDetail networkDetail = scanDetail.getNetworkDetail();
            ScanResult scanResult = scanDetail.getScanResult();
            totalResults++;
            if (networkDetail != null) {
                if (networkDetail.isHiddenBeaconFrame()) {
                    hiddenNetworks++;
                }
                if (networkDetail.getHSRelease() != null) {
                    if (networkDetail.getHSRelease() == NetworkDetail.HSRelease.R1) {
                        hotspot2r1Networks++;
                    } else if (networkDetail.getHSRelease() == NetworkDetail.HSRelease.R2) {
                        hotspot2r2Networks++;
                    } else if (networkDetail.getHSRelease() == NetworkDetail.HSRelease.R3) {
                        hotspot2r3Networks++;
                    }
                }
                if (networkDetail.isMboSupported()) {
                    mboSupportedNetworks++;
                    if (networkDetail.isMboCellularDataAware()) {
                        mboCellularDataAwareNetworks++;
                    }
                    if (networkDetail.isOceSupported()) {
                        oceSupportedNetworks++;
                    }
                }
                if (networkDetail.getWifiMode() == InformationElementUtil.WifiMode.MODE_11AX) {
                    standard11axNetworks++;
                }
            }
            if (scanResult != null && scanResult.capabilities != null) {
                if (ScanResultUtil.isScanResultForFilsSha256Network(scanResult)
                        || ScanResultUtil.isScanResultForFilsSha384Network(scanResult)) {
                    filsSupportedNetworks++;
                }
                if (scanResult.is6GHz()) {
                    band6gNetworks++;
                    if (scanResult.is6GhzPsc()) {
                        band6gPscNetworks++;
                    }
                }
                if (ScanResultUtil.isScanResultForEapSuiteBNetwork(scanResult)
                        || ScanResultUtil.isScanResultForWpa3EnterpriseTransitionNetwork(scanResult)
                        || ScanResultUtil.isScanResultForWpa3EnterpriseOnlyNetwork(scanResult)) {
                    wpa3EnterpriseNetworks++;
                } else if (ScanResultUtil.isScanResultForWapiPskNetwork(scanResult)) {
                    wapiPersonalNetworks++;
                } else if (ScanResultUtil.isScanResultForWapiCertNetwork(scanResult)) {
                    wapiEnterpriseNetworks++;
                } else if (ScanResultUtil.isScanResultForWpa2EnterpriseOnlyNetwork(scanResult)) {
                    enterpriseNetworks++;
                } else if (ScanResultUtil.isScanResultForSaeNetwork(scanResult)) {
                    wpa3PersonalNetworks++;
                } else if (ScanResultUtil.isScanResultForPskNetwork(scanResult)
                        || ScanResultUtil.isScanResultForWepNetwork(scanResult)) {
                    personalNetworks++;
                } else if (ScanResultUtil.isScanResultForOweNetwork(scanResult)) {
                    enhacedOpenNetworks++;
                } else {
                    openNetworks++;
                }
            }
        }
        synchronized (mLock) {
            mWifiLogProto.numTotalScanResults += totalResults;
            mWifiLogProto.numOpenNetworkScanResults += openNetworks;
            mWifiLogProto.numLegacyPersonalNetworkScanResults += personalNetworks;
            mWifiLogProto.numLegacyEnterpriseNetworkScanResults += enterpriseNetworks;
            mWifiLogProto.numEnhancedOpenNetworkScanResults += enhacedOpenNetworks;
            mWifiLogProto.numWpa3PersonalNetworkScanResults += wpa3PersonalNetworks;
            mWifiLogProto.numWpa3EnterpriseNetworkScanResults += wpa3EnterpriseNetworks;
            mWifiLogProto.numWapiPersonalNetworkScanResults += wapiPersonalNetworks;
            mWifiLogProto.numWapiEnterpriseNetworkScanResults += wapiEnterpriseNetworks;
            mWifiLogProto.numHiddenNetworkScanResults += hiddenNetworks;
            mWifiLogProto.numHotspot2R1NetworkScanResults += hotspot2r1Networks;
            mWifiLogProto.numHotspot2R2NetworkScanResults += hotspot2r2Networks;
            mWifiLogProto.numHotspot2R3NetworkScanResults += hotspot2r3Networks;
            mWifiLogProto.numMboSupportedNetworkScanResults += mboSupportedNetworks;
            mWifiLogProto.numMboCellularDataAwareNetworkScanResults += mboCellularDataAwareNetworks;
            mWifiLogProto.numOceSupportedNetworkScanResults += oceSupportedNetworks;
            mWifiLogProto.numFilsSupportedNetworkScanResults += filsSupportedNetworks;
            mWifiLogProto.num11AxNetworkScanResults += standard11axNetworks;
            mWifiLogProto.num6GNetworkScanResults += band6gNetworks;
            mWifiLogProto.num6GPscNetworkScanResults += band6gPscNetworks;
            mWifiLogProto.numScans++;
        }
    }

    private boolean mWifiWins = false; // Based on scores, use wifi instead of mobile data?
    // Based on Wifi usability scores. use wifi instead of mobile data?
    private boolean mWifiWinsUsabilityScore = false;

    /**
     * Increments occurence of a particular wifi score calculated
     * in WifiScoreReport by current connected network. Scores are bounded
     * within  [MIN_WIFI_SCORE, MAX_WIFI_SCORE] to limit size of SparseArray.
     *
     * Also records events when the current score breaches significant thresholds.
     */
    public void incrementWifiScoreCount(String ifaceName, int score) {
        if (score < MIN_WIFI_SCORE || score > MAX_WIFI_SCORE) {
            return;
        }
        synchronized (mLock) {
            int count = mWifiScoreCounts.get(score);
            mWifiScoreCounts.put(score, count + 1);

            boolean wifiWins = mWifiWins;
            if (mWifiWins && score < LOW_WIFI_SCORE) {
                wifiWins = false;
            } else if (!mWifiWins && score > LOW_WIFI_SCORE) {
                wifiWins = true;
            }
            mLastScore = score;
            mLastScoreNoReset = score;
            if (wifiWins != mWifiWins) {
                mWifiWins = wifiWins;
                StaEvent event = new StaEvent();
                event.type = StaEvent.TYPE_SCORE_BREACH;
                addStaEvent(ifaceName, event);
                // Only record the first score breach by checking whether mScoreBreachLowTimeMillis
                // has been set to -1
                if (!wifiWins && mScoreBreachLowTimeMillis == -1) {
                    mScoreBreachLowTimeMillis = mClock.getElapsedSinceBootMillis();
                }
            }
        }
    }

    /**
     * Increments occurence of the results from attempting to start SoftAp.
     * Maps the |result| and WifiManager |failureCode| constant to proto defined SoftApStartResult
     * codes.
     */
    public void incrementSoftApStartResult(boolean result, int failureCode) {
        synchronized (mLock) {
            if (result) {
                int count = mSoftApManagerReturnCodeCounts.get(
                        WifiMetricsProto.SoftApReturnCodeCount.SOFT_AP_STARTED_SUCCESSFULLY);
                mSoftApManagerReturnCodeCounts.put(
                        WifiMetricsProto.SoftApReturnCodeCount.SOFT_AP_STARTED_SUCCESSFULLY,
                        count + 1);
                return;
            }

            // now increment failure modes - if not explicitly handled, dump into the general
            // error bucket.
            if (failureCode == WifiManager.SAP_START_FAILURE_NO_CHANNEL) {
                int count = mSoftApManagerReturnCodeCounts.get(
                        WifiMetricsProto.SoftApReturnCodeCount.SOFT_AP_FAILED_NO_CHANNEL);
                mSoftApManagerReturnCodeCounts.put(
                        WifiMetricsProto.SoftApReturnCodeCount.SOFT_AP_FAILED_NO_CHANNEL,
                        count + 1);
            } else if (failureCode == WifiManager.SAP_START_FAILURE_UNSUPPORTED_CONFIGURATION) {
                int count = mSoftApManagerReturnCodeCounts.get(
                        WifiMetricsProto.SoftApReturnCodeCount
                        .SOFT_AP_FAILED_UNSUPPORTED_CONFIGURATION);
                mSoftApManagerReturnCodeCounts.put(
                        WifiMetricsProto.SoftApReturnCodeCount
                        .SOFT_AP_FAILED_UNSUPPORTED_CONFIGURATION,
                        count + 1);
            } else {
                // failure mode not tracked at this time...  count as a general error for now.
                int count = mSoftApManagerReturnCodeCounts.get(
                        WifiMetricsProto.SoftApReturnCodeCount.SOFT_AP_FAILED_GENERAL_ERROR);
                mSoftApManagerReturnCodeCounts.put(
                        WifiMetricsProto.SoftApReturnCodeCount.SOFT_AP_FAILED_GENERAL_ERROR,
                        count + 1);
            }
        }
    }

    /**
     * Adds a record indicating the current up state of soft AP
     */
    public void addSoftApUpChangedEvent(boolean isUp, int mode, long defaultShutdownTimeoutMillis,
            boolean isBridged) {
        int numOfEventNeedToAdd = isBridged && isUp ? 2 : 1;
        for (int i = 0; i < numOfEventNeedToAdd; i++) {
            SoftApConnectedClientsEvent event = new SoftApConnectedClientsEvent();
            if (isUp) {
                event.eventType = isBridged ? SoftApConnectedClientsEvent.DUAL_AP_BOTH_INSTANCES_UP
                        : SoftApConnectedClientsEvent.SOFT_AP_UP;
            } else {
                event.eventType = SoftApConnectedClientsEvent.SOFT_AP_DOWN;
            }
            event.numConnectedClients = 0;
            event.defaultShutdownTimeoutSetting = defaultShutdownTimeoutMillis;
            addSoftApConnectedClientsEvent(event, mode);
        }
    }

    /**
     * Adds a record indicating the one of the dual AP instances is down.
     */
    public void addSoftApInstanceDownEventInDualMode(int mode, @NonNull SoftApInfo info) {
        SoftApConnectedClientsEvent event = new SoftApConnectedClientsEvent();
        event.eventType = SoftApConnectedClientsEvent.DUAL_AP_ONE_INSTANCE_DOWN;
        event.channelFrequency = info.getFrequency();
        event.channelBandwidth = info.getBandwidth();
        event.generation = info.getWifiStandardInternal();
        addSoftApConnectedClientsEvent(event, mode);
    }

    /**
     * Adds a record for current number of associated stations to soft AP
     */
    public void addSoftApNumAssociatedStationsChangedEvent(int numTotalStations,
            int numStationsOnCurrentFrequency, int mode, @Nullable SoftApInfo info) {
        SoftApConnectedClientsEvent event = new SoftApConnectedClientsEvent();
        event.eventType = SoftApConnectedClientsEvent.NUM_CLIENTS_CHANGED;
        if (info != null) {
            event.channelFrequency = info.getFrequency();
            event.channelBandwidth = info.getBandwidth();
            event.generation = info.getWifiStandardInternal();
        }
        event.numConnectedClients = numTotalStations;
        event.numConnectedClientsOnCurrentFrequency = numStationsOnCurrentFrequency;
        addSoftApConnectedClientsEvent(event, mode);
    }

    /**
     * Adds a record to the corresponding event list based on mode param
     */
    private void addSoftApConnectedClientsEvent(SoftApConnectedClientsEvent event, int mode) {
        synchronized (mLock) {
            List<SoftApConnectedClientsEvent> softApEventList;
            switch (mode) {
                case WifiManager.IFACE_IP_MODE_TETHERED:
                    softApEventList = mSoftApEventListTethered;
                    break;
                case WifiManager.IFACE_IP_MODE_LOCAL_ONLY:
                    softApEventList = mSoftApEventListLocalOnly;
                    break;
                default:
                    return;
            }

            if (softApEventList.size() > MAX_NUM_SOFT_AP_EVENTS) {
                return;
            }

            event.timeStampMillis = mClock.getElapsedSinceBootMillis();
            softApEventList.add(event);
        }
    }

    /**
     * Updates current soft AP events with channel info
     */
    public void addSoftApChannelSwitchedEvent(List<SoftApInfo> infos, int mode, boolean isBridged) {
        synchronized (mLock) {
            int numOfEventNeededToUpdate = infos.size();
            if (isBridged && numOfEventNeededToUpdate == 1) {
                // Ignore the channel info update when only 1 info in bridged mode because it means
                // that one of the instance was been shutdown.
                return;
            }
            int apUpEvent = isBridged ? SoftApConnectedClientsEvent.DUAL_AP_BOTH_INSTANCES_UP
                    : SoftApConnectedClientsEvent.SOFT_AP_UP;
            List<SoftApConnectedClientsEvent> softApEventList;
            switch (mode) {
                case WifiManager.IFACE_IP_MODE_TETHERED:
                    softApEventList = mSoftApEventListTethered;
                    break;
                case WifiManager.IFACE_IP_MODE_LOCAL_ONLY:
                    softApEventList = mSoftApEventListLocalOnly;
                    break;
                default:
                    return;
            }

            for (int index = softApEventList.size() - 1;
                    index >= 0 && numOfEventNeededToUpdate != 0; index--) {
                SoftApConnectedClientsEvent event = softApEventList.get(index);
                if (event != null && event.eventType == apUpEvent) {
                    int infoIndex = numOfEventNeededToUpdate - 1;
                    event.channelFrequency = infos.get(infoIndex).getFrequency();
                    event.channelBandwidth = infos.get(infoIndex).getBandwidth();
                    event.generation = infos.get(infoIndex).getWifiStandardInternal();
                    numOfEventNeededToUpdate--;
                }
            }
        }
    }

    /**
     * Updates current soft AP events with softap configuration
     */
    public void updateSoftApConfiguration(SoftApConfiguration config, int mode, boolean isBridged) {
        synchronized (mLock) {
            List<SoftApConnectedClientsEvent> softApEventList;
            switch (mode) {
                case WifiManager.IFACE_IP_MODE_TETHERED:
                    softApEventList = mSoftApEventListTethered;
                    break;
                case WifiManager.IFACE_IP_MODE_LOCAL_ONLY:
                    softApEventList = mSoftApEventListLocalOnly;
                    break;
                default:
                    return;
            }

            int numOfEventNeededToUpdate = isBridged ? 2 : 1;
            int apUpEvent = isBridged ? SoftApConnectedClientsEvent.DUAL_AP_BOTH_INSTANCES_UP
                    : SoftApConnectedClientsEvent.SOFT_AP_UP;

            for (int index = softApEventList.size() - 1;
                    index >= 0 && numOfEventNeededToUpdate != 0; index--) {
                SoftApConnectedClientsEvent event = softApEventList.get(index);
                if (event != null && event.eventType == apUpEvent) {
                    event.maxNumClientsSettingInSoftapConfiguration =
                            config.getMaxNumberOfClients();
                    event.shutdownTimeoutSettingInSoftapConfiguration =
                            config.getShutdownTimeoutMillis();
                    event.clientControlIsEnabled = config.isClientControlByUserEnabled();
                    numOfEventNeededToUpdate--;
                }
            }
        }
    }

    /**
     * Updates current soft AP events with softap capability
     */
    public void updateSoftApCapability(SoftApCapability capability, int mode, boolean isBridged) {
        synchronized (mLock) {
            List<SoftApConnectedClientsEvent> softApEventList;
            switch (mode) {
                case WifiManager.IFACE_IP_MODE_TETHERED:
                    softApEventList = mSoftApEventListTethered;
                    break;
                case WifiManager.IFACE_IP_MODE_LOCAL_ONLY:
                    softApEventList = mSoftApEventListLocalOnly;
                    break;
                default:
                    return;
            }

            int numOfEventNeededToUpdate = isBridged ? 2 : 1;
            int apUpEvent = isBridged ? SoftApConnectedClientsEvent.DUAL_AP_BOTH_INSTANCES_UP
                    : SoftApConnectedClientsEvent.SOFT_AP_UP;

            for (int index = softApEventList.size() - 1;
                    index >= 0 && numOfEventNeededToUpdate != 0; index--) {
                SoftApConnectedClientsEvent event = softApEventList.get(index);
                if (event != null && event.eventType == apUpEvent) {
                    event.maxNumClientsSettingInSoftapCapability =
                            capability.getMaxSupportedClients();
                    numOfEventNeededToUpdate--;
                }
            }
        }
    }

    /**
     * Increment number of times the HAL crashed.
     */
    public synchronized void incrementNumHalCrashes() {
        mWifiLogProto.numHalCrashes++;
        WifiStatsLog.write(WifiStatsLog.WIFI_SETUP_FAILURE_CRASH_REPORTED,
                WifiStatsLog.WIFI_SETUP_FAILURE_CRASH_REPORTED__TYPE__HAL_CRASH);
    }

    /**
     * Increment number of times the Wificond crashed.
     */
    public synchronized void incrementNumWificondCrashes() {
        mWifiLogProto.numWificondCrashes++;
        WifiStatsLog.write(WifiStatsLog.WIFI_SETUP_FAILURE_CRASH_REPORTED,
                WifiStatsLog.WIFI_SETUP_FAILURE_CRASH_REPORTED__TYPE__WIFICOND_CRASH);
    }

    /**
     * Increment number of times the supplicant crashed.
     */
    public synchronized void incrementNumSupplicantCrashes() {
        mWifiLogProto.numSupplicantCrashes++;
        WifiStatsLog.write(WifiStatsLog.WIFI_SETUP_FAILURE_CRASH_REPORTED,
                WifiStatsLog.WIFI_SETUP_FAILURE_CRASH_REPORTED__TYPE__SUPPLICANT_CRASH);
    }

    /**
     * Increment number of times the hostapd crashed.
     */
    public synchronized void incrementNumHostapdCrashes() {
        mWifiLogProto.numHostapdCrashes++;
        WifiStatsLog.write(WifiStatsLog.WIFI_SETUP_FAILURE_CRASH_REPORTED,
                WifiStatsLog.WIFI_SETUP_FAILURE_CRASH_REPORTED__TYPE__HOSTAPD_CRASH);
    }

    /**
     * Increment number of times the wifi on failed due to an error in HAL.
     */
    public synchronized void incrementNumSetupClientInterfaceFailureDueToHal() {
        mWifiLogProto.numSetupClientInterfaceFailureDueToHal++;
        WifiStatsLog.write(WifiStatsLog.WIFI_SETUP_FAILURE_CRASH_REPORTED,
                WifiStatsLog.WIFI_SETUP_FAILURE_CRASH_REPORTED__TYPE__CLIENT_FAILURE_HAL);
    }

    /**
     * Increment number of times the wifi on failed due to an error in wificond.
     */
    public synchronized void incrementNumSetupClientInterfaceFailureDueToWificond() {
        mWifiLogProto.numSetupClientInterfaceFailureDueToWificond++;
        WifiStatsLog.write(WifiStatsLog.WIFI_SETUP_FAILURE_CRASH_REPORTED,
                WifiStatsLog.WIFI_SETUP_FAILURE_CRASH_REPORTED__TYPE__CLIENT_FAILURE_WIFICOND);
    }

    /**
     * Increment number of times the wifi on failed due to an error in supplicant.
     */
    public synchronized void incrementNumSetupClientInterfaceFailureDueToSupplicant() {
        mWifiLogProto.numSetupClientInterfaceFailureDueToSupplicant++;
        WifiStatsLog.write(WifiStatsLog.WIFI_SETUP_FAILURE_CRASH_REPORTED,
                WifiStatsLog.WIFI_SETUP_FAILURE_CRASH_REPORTED__TYPE__CLIENT_FAILURE_SUPPLICANT);
    }

    /**
     * Increment number of times the SoftAp on failed due to an error in HAL.
     */
    public synchronized void incrementNumSetupSoftApInterfaceFailureDueToHal() {
        mWifiLogProto.numSetupSoftApInterfaceFailureDueToHal++;
        WifiStatsLog.write(WifiStatsLog.WIFI_SETUP_FAILURE_CRASH_REPORTED,
                WifiStatsLog.WIFI_SETUP_FAILURE_CRASH_REPORTED__TYPE__SOFT_AP_FAILURE_HAL);
    }

    /**
     * Increment number of times the SoftAp on failed due to an error in wificond.
     */
    public synchronized void incrementNumSetupSoftApInterfaceFailureDueToWificond() {
        mWifiLogProto.numSetupSoftApInterfaceFailureDueToWificond++;
        WifiStatsLog.write(WifiStatsLog.WIFI_SETUP_FAILURE_CRASH_REPORTED,
                WifiStatsLog.WIFI_SETUP_FAILURE_CRASH_REPORTED__TYPE__SOFT_AP_FAILURE_WIFICOND);
    }

    /**
     * Increment number of times the SoftAp on failed due to an error in hostapd.
     */
    public synchronized void incrementNumSetupSoftApInterfaceFailureDueToHostapd() {
        mWifiLogProto.numSetupSoftApInterfaceFailureDueToHostapd++;
        WifiStatsLog.write(WifiStatsLog.WIFI_SETUP_FAILURE_CRASH_REPORTED,
                WifiStatsLog.WIFI_SETUP_FAILURE_CRASH_REPORTED__TYPE__SOFT_AP_FAILURE_HOSTAPD);
    }

    /**
     * Increment number of times the P2p on failed due to an error in HAL.
     */
    public synchronized void incrementNumSetupP2pInterfaceFailureDueToHal() {
        WifiStatsLog.write(WifiStatsLog.WIFI_SETUP_FAILURE_CRASH_REPORTED,
                WifiStatsLog.WIFI_SETUP_FAILURE_CRASH_REPORTED__TYPE__P2P_FAILURE_HAL);
    }

    /**
     * Increment number of times the P2p on failed due to an error in supplicant.
     */
    public synchronized void incrementNumSetupP2pInterfaceFailureDueToSupplicant() {
        WifiStatsLog.write(WifiStatsLog.WIFI_SETUP_FAILURE_CRASH_REPORTED,
                WifiStatsLog.WIFI_SETUP_FAILURE_CRASH_REPORTED__TYPE__P2P_FAILURE_SUPPLICANT);
    }

    /**
     * Increment number of times we got client interface down.
     */
    public void incrementNumClientInterfaceDown() {
        synchronized (mLock) {
            mWifiLogProto.numClientInterfaceDown++;
        }
    }

    /**
     * Increment number of times we got client interface down.
     */
    public void incrementNumSoftApInterfaceDown() {
        synchronized (mLock) {
            mWifiLogProto.numSoftApInterfaceDown++;
        }
    }

    /**
     * Increment number of times Passpoint provider being installed.
     */
    public void incrementNumPasspointProviderInstallation() {
        synchronized (mLock) {
            mWifiLogProto.numPasspointProviderInstallation++;
        }
    }

    /**
     * Increment number of times Passpoint provider is installed successfully.
     */
    public void incrementNumPasspointProviderInstallSuccess() {
        synchronized (mLock) {
            mWifiLogProto.numPasspointProviderInstallSuccess++;
        }
    }

    /**
     * Increment number of times Passpoint provider being uninstalled.
     */
    public void incrementNumPasspointProviderUninstallation() {
        synchronized (mLock) {
            mWifiLogProto.numPasspointProviderUninstallation++;
        }
    }

    /**
     * Increment number of times Passpoint provider is uninstalled successfully.
     */
    public void incrementNumPasspointProviderUninstallSuccess() {
        synchronized (mLock) {
            mWifiLogProto.numPasspointProviderUninstallSuccess++;
        }
    }

    /**
     * Increment number of Passpoint providers with no Root CA in their profile.
     */
    public void incrementNumPasspointProviderWithNoRootCa() {
        synchronized (mLock) {
            mWifiLogProto.numPasspointProviderWithNoRootCa++;
        }
    }

    /**
     * Increment number of Passpoint providers with a self-signed Root CA in their profile.
     */
    public void incrementNumPasspointProviderWithSelfSignedRootCa() {
        synchronized (mLock) {
            mWifiLogProto.numPasspointProviderWithSelfSignedRootCa++;
        }
    }

    /**
     * Increment number of Passpoint providers with subscription expiration date in their profile.
     */
    public void incrementNumPasspointProviderWithSubscriptionExpiration() {
        synchronized (mLock) {
            mWifiLogProto.numPasspointProviderWithSubscriptionExpiration++;
        }
    }

    /**
     * Increment number of times we detected a radio mode change to MCC.
     */
    public void incrementNumRadioModeChangeToMcc() {
        synchronized (mLock) {
            mWifiLogProto.numRadioModeChangeToMcc++;
        }
    }

    /**
     * Increment number of times we detected a radio mode change to SCC.
     */
    public void incrementNumRadioModeChangeToScc() {
        synchronized (mLock) {
            mWifiLogProto.numRadioModeChangeToScc++;
        }
    }

    /**
     * Increment number of times we detected a radio mode change to SBS.
     */
    public void incrementNumRadioModeChangeToSbs() {
        synchronized (mLock) {
            mWifiLogProto.numRadioModeChangeToSbs++;
        }
    }

    /**
     * Increment number of times we detected a radio mode change to DBS.
     */
    public void incrementNumRadioModeChangeToDbs() {
        synchronized (mLock) {
            mWifiLogProto.numRadioModeChangeToDbs++;
        }
    }

    /**
     * Increment number of times we detected a channel did not satisfy user band preference.
     */
    public void incrementNumSoftApUserBandPreferenceUnsatisfied() {
        synchronized (mLock) {
            mWifiLogProto.numSoftApUserBandPreferenceUnsatisfied++;
        }
    }

    /**
     * Increment N-Way network selection decision histograms:
     * Counts the size of various sets of scanDetails within a scan, and increment the occurrence
     * of that size for the associated histogram. There are ten histograms generated for each
     * combination of: {SSID, BSSID} *{Total, Saved, Open, Saved_or_Open, Passpoint}
     * Only performs this count if isFullBand is true, otherwise, increments the partial scan count
     */
    public void incrementAvailableNetworksHistograms(List<ScanDetail> scanDetails,
            boolean isFullBand) {
        synchronized (mLock) {
            if (mWifiConfigManager == null || mWifiNetworkSelector == null
                    || mPasspointManager == null) {
                return;
            }
            if (!isFullBand) {
                mWifiLogProto.partialAllSingleScanListenerResults++;
                return;
            }

            Set<ScanResultMatchInfo> ssids = new HashSet<ScanResultMatchInfo>();
            int bssids = 0;
            Set<ScanResultMatchInfo> openSsids = new HashSet<ScanResultMatchInfo>();
            int openBssids = 0;
            Set<ScanResultMatchInfo> savedSsids = new HashSet<ScanResultMatchInfo>();
            int savedBssids = 0;
            // openOrSavedSsids calculated from union of savedSsids & openSsids
            int openOrSavedBssids = 0;
            Set<PasspointProvider> savedPasspointProviderProfiles =
                    new HashSet<PasspointProvider>();
            int savedPasspointProviderBssids = 0;
            int passpointR1Aps = 0;
            int passpointR2Aps = 0;
            int passpointR3Aps = 0;
            Map<ANQPNetworkKey, Integer> passpointR1UniqueEss = new HashMap<>();
            Map<ANQPNetworkKey, Integer> passpointR2UniqueEss = new HashMap<>();
            Map<ANQPNetworkKey, Integer> passpointR3UniqueEss = new HashMap<>();
            int supporting80211mcAps = 0;
            for (ScanDetail scanDetail : scanDetails) {
                NetworkDetail networkDetail = scanDetail.getNetworkDetail();
                ScanResult scanResult = scanDetail.getScanResult();

                // statistics to be collected for ALL APs (irrespective of signal power)
                if (networkDetail.is80211McResponderSupport()) {
                    supporting80211mcAps++;
                }

                ScanResultMatchInfo matchInfo = ScanResultMatchInfo.fromScanResult(scanResult);
                List<Pair<PasspointProvider, PasspointMatch>> matchedProviders = null;
                if (networkDetail.isInterworking()) {
                    // Try to match provider, but do not allow new ANQP messages. Use cached data.
                    matchedProviders = mPasspointManager.matchProvider(scanResult, false);
                    if (networkDetail.getHSRelease() == NetworkDetail.HSRelease.R1) {
                        passpointR1Aps++;
                    } else if (networkDetail.getHSRelease() == NetworkDetail.HSRelease.R2) {
                        passpointR2Aps++;
                    } else if (networkDetail.getHSRelease() == NetworkDetail.HSRelease.R3) {
                        passpointR3Aps++;
                    }

                    long bssid = 0;
                    boolean validBssid = false;
                    try {
                        bssid = Utils.parseMac(scanResult.BSSID);
                        validBssid = true;
                    } catch (IllegalArgumentException e) {
                        Log.e(TAG,
                                "Invalid BSSID provided in the scan result: " + scanResult.BSSID);
                    }
                    if (validBssid) {
                        ANQPNetworkKey uniqueEss = ANQPNetworkKey.buildKey(scanResult.SSID, bssid,
                                scanResult.hessid, networkDetail.getAnqpDomainID());
                        if (networkDetail.getHSRelease() == NetworkDetail.HSRelease.R1) {
                            Integer countObj = passpointR1UniqueEss.get(uniqueEss);
                            int count = countObj == null ? 0 : countObj;
                            passpointR1UniqueEss.put(uniqueEss, count + 1);
                        } else if (networkDetail.getHSRelease() == NetworkDetail.HSRelease.R2) {
                            Integer countObj = passpointR2UniqueEss.get(uniqueEss);
                            int count = countObj == null ? 0 : countObj;
                            passpointR2UniqueEss.put(uniqueEss, count + 1);
                        } else if (networkDetail.getHSRelease() == NetworkDetail.HSRelease.R3) {
                            Integer countObj = passpointR3UniqueEss.get(uniqueEss);
                            int count = countObj == null ? 0 : countObj;
                            passpointR3UniqueEss.put(uniqueEss, count + 1);
                        }
                    }
                }

                if (mWifiNetworkSelector.isSignalTooWeak(scanResult)) {
                    continue;
                }

                // statistics to be collected ONLY for those APs with sufficient signal power

                ssids.add(matchInfo);
                bssids++;
                boolean isOpen = ScanResultUtil.isScanResultForOpenNetwork(scanResult)
                        || ScanResultUtil.isScanResultForOweNetwork(scanResult);
                WifiConfiguration config =
                        mWifiConfigManager.getSavedNetworkForScanDetail(scanDetail);
                boolean isSaved = (config != null) && !config.isEphemeral()
                        && !config.isPasspoint();
                if (isOpen) {
                    openSsids.add(matchInfo);
                    openBssids++;
                }
                if (isSaved) {
                    savedSsids.add(matchInfo);
                    savedBssids++;
                }
                if (isOpen || isSaved) {
                    openOrSavedBssids++;
                    // Calculate openOrSavedSsids union later
                }
                if (matchedProviders != null && !matchedProviders.isEmpty()) {
                    for (Pair<PasspointProvider, PasspointMatch> passpointProvider :
                            matchedProviders) {
                        savedPasspointProviderProfiles.add(passpointProvider.first);
                    }
                    savedPasspointProviderBssids++;
                }
            }
            mWifiLogProto.fullBandAllSingleScanListenerResults++;
            incrementTotalScanSsids(mTotalSsidsInScanHistogram, ssids.size());
            incrementTotalScanResults(mTotalBssidsInScanHistogram, bssids);
            incrementSsid(mAvailableOpenSsidsInScanHistogram, openSsids.size());
            incrementBssid(mAvailableOpenBssidsInScanHistogram, openBssids);
            incrementSsid(mAvailableSavedSsidsInScanHistogram, savedSsids.size());
            incrementBssid(mAvailableSavedBssidsInScanHistogram, savedBssids);
            openSsids.addAll(savedSsids); // openSsids = Union(openSsids, savedSsids)
            incrementSsid(mAvailableOpenOrSavedSsidsInScanHistogram, openSsids.size());
            incrementBssid(mAvailableOpenOrSavedBssidsInScanHistogram, openOrSavedBssids);
            incrementSsid(mAvailableSavedPasspointProviderProfilesInScanHistogram,
                    savedPasspointProviderProfiles.size());
            incrementBssid(mAvailableSavedPasspointProviderBssidsInScanHistogram,
                    savedPasspointProviderBssids);
            incrementTotalPasspointAps(mObservedHotspotR1ApInScanHistogram, passpointR1Aps);
            incrementTotalPasspointAps(mObservedHotspotR2ApInScanHistogram, passpointR2Aps);
            incrementTotalPasspointAps(mObservedHotspotR3ApInScanHistogram, passpointR3Aps);
            incrementTotalUniquePasspointEss(mObservedHotspotR1EssInScanHistogram,
                    passpointR1UniqueEss.size());
            incrementTotalUniquePasspointEss(mObservedHotspotR2EssInScanHistogram,
                    passpointR2UniqueEss.size());
            incrementTotalUniquePasspointEss(mObservedHotspotR3EssInScanHistogram,
                    passpointR3UniqueEss.size());
            for (Integer count : passpointR1UniqueEss.values()) {
                incrementPasspointPerUniqueEss(mObservedHotspotR1ApsPerEssInScanHistogram, count);
            }
            for (Integer count : passpointR2UniqueEss.values()) {
                incrementPasspointPerUniqueEss(mObservedHotspotR2ApsPerEssInScanHistogram, count);
            }
            for (Integer count : passpointR3UniqueEss.values()) {
                incrementPasspointPerUniqueEss(mObservedHotspotR3ApsPerEssInScanHistogram, count);
            }
            increment80211mcAps(mObserved80211mcApInScanHistogram, supporting80211mcAps);
        }
    }

    /** Increments the occurence of a "Connect to Network" notification. */
    public void incrementConnectToNetworkNotification(String notifierTag, int notificationType) {
        synchronized (mLock) {
            int count = mConnectToNetworkNotificationCount.get(notificationType);
            mConnectToNetworkNotificationCount.put(notificationType, count + 1);
        }
    }

    /** Increments the occurence of an "Connect to Network" notification user action. */
    public void incrementConnectToNetworkNotificationAction(String notifierTag,
            int notificationType, int actionType) {
        synchronized (mLock) {
            int key = notificationType * CONNECT_TO_NETWORK_NOTIFICATION_ACTION_KEY_MULTIPLIER
                    + actionType;
            int count = mConnectToNetworkNotificationActionCount.get(key);
            mConnectToNetworkNotificationActionCount.put(key, count + 1);
        }
    }

    /**
     * Sets the number of SSIDs blocklisted from recommendation by the open network notification
     * recommender.
     */
    public void setNetworkRecommenderBlocklistSize(String notifierTag, int size) {
        synchronized (mLock) {
            mOpenNetworkRecommenderBlocklistSize = size;
        }
    }

    /** Sets if the available network notification feature is enabled. */
    public void setIsWifiNetworksAvailableNotificationEnabled(String notifierTag, boolean enabled) {
        synchronized (mLock) {
            mIsWifiNetworksAvailableNotificationOn = enabled;
        }
    }

    /** Increments the occurence of connection attempts that were initiated unsuccessfully */
    public void incrementNumNetworkRecommendationUpdates(String notifierTag) {
        synchronized (mLock) {
            mNumOpenNetworkRecommendationUpdates++;
        }
    }

    /** Increments the occurence of connection attempts that were initiated unsuccessfully */
    public void incrementNumNetworkConnectMessageFailedToSend(String notifierTag) {
        synchronized (mLock) {
            mNumOpenNetworkConnectMessageFailedToSend++;
        }
    }

    /** Log firmware alert related metrics */
    public void logFirmwareAlert(String ifaceName, int errorCode) {
        incrementAlertReasonCount(errorCode);
        logWifiIsUnusableEvent(ifaceName, WifiIsUnusableEvent.TYPE_FIRMWARE_ALERT, errorCode);
        addToWifiUsabilityStatsList(ifaceName, WifiUsabilityStats.LABEL_BAD,
                WifiUsabilityStats.TYPE_FIRMWARE_ALERT, errorCode);
    }

    public static final String PROTO_DUMP_ARG = "wifiMetricsProto";
    public static final String CLEAN_DUMP_ARG = "clean";

    /**
     * Dump all WifiMetrics. Collects some metrics from ConfigStore, Settings and WifiManager
     * at this time.
     *
     * @param fd unused
     * @param pw PrintWriter for writing dump to
     * @param args [wifiMetricsProto [clean]]
     */
    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        synchronized (mLock) {
            consolidateScoringParams();
            if (args != null && args.length > 0 && PROTO_DUMP_ARG.equals(args[0])) {
                // Dump serialized WifiLog proto
                consolidateProto();
                byte[] wifiMetricsProto = WifiMetricsProto.WifiLog.toByteArray(mWifiLogProto);
                String metricsProtoDump = Base64.encodeToString(wifiMetricsProto, Base64.DEFAULT);
                if (args.length > 1 && CLEAN_DUMP_ARG.equals(args[1])) {
                    // Output metrics proto bytes (base64) and nothing else
                    pw.print(metricsProtoDump);
                } else {
                    // Tag the start and end of the metrics proto bytes
                    pw.println("WifiMetrics:");
                    pw.println(metricsProtoDump);
                    pw.println("EndWifiMetrics");
                }
                clear();
            } else {
                pw.println("WifiMetrics:");
                pw.println("mConnectionEvents:");
                for (ConnectionEvent event : mConnectionEventList) {
                    String eventLine = event.toString();
                    if (mCurrentConnectionEventPerIface.containsValue(event)) {
                        eventLine += " CURRENTLY OPEN EVENT";
                    }
                    pw.println(eventLine);
                }
                pw.println("mWifiLogProto.numSavedNetworks=" + mWifiLogProto.numSavedNetworks);
                pw.println("mWifiLogProto.numSavedNetworksWithMacRandomization="
                        + mWifiLogProto.numSavedNetworksWithMacRandomization);
                pw.println("mWifiLogProto.numOpenNetworks=" + mWifiLogProto.numOpenNetworks);
                pw.println("mWifiLogProto.numLegacyPersonalNetworks="
                        + mWifiLogProto.numLegacyPersonalNetworks);
                pw.println("mWifiLogProto.numLegacyEnterpriseNetworks="
                        + mWifiLogProto.numLegacyEnterpriseNetworks);
                pw.println("mWifiLogProto.numEnhancedOpenNetworks="
                        + mWifiLogProto.numEnhancedOpenNetworks);
                pw.println("mWifiLogProto.numWpa3PersonalNetworks="
                        + mWifiLogProto.numWpa3PersonalNetworks);
                pw.println("mWifiLogProto.numWpa3EnterpriseNetworks="
                        + mWifiLogProto.numWpa3EnterpriseNetworks);
                pw.println("mWifiLogProto.numWapiPersonalNetworks="
                        + mWifiLogProto.numWapiPersonalNetworks);
                pw.println("mWifiLogProto.numWapiEnterpriseNetworks="
                        + mWifiLogProto.numWapiEnterpriseNetworks);
                pw.println("mWifiLogProto.numHiddenNetworks=" + mWifiLogProto.numHiddenNetworks);
                pw.println("mWifiLogProto.numPasspointNetworks="
                        + mWifiLogProto.numPasspointNetworks);
                pw.println("mWifiLogProto.isLocationEnabled=" + mWifiLogProto.isLocationEnabled);
                pw.println("mWifiLogProto.isScanningAlwaysEnabled="
                        + mWifiLogProto.isScanningAlwaysEnabled);
                pw.println("mWifiLogProto.isVerboseLoggingEnabled="
                        + mWifiLogProto.isVerboseLoggingEnabled);
                pw.println("mWifiLogProto.isEnhancedMacRandomizationForceEnabled="
                        + mWifiLogProto.isEnhancedMacRandomizationForceEnabled);
                pw.println("mWifiLogProto.isWifiWakeEnabled=" + mWifiLogProto.isWifiWakeEnabled);
                pw.println("mWifiLogProto.numNetworksAddedByUser="
                        + mWifiLogProto.numNetworksAddedByUser);
                pw.println("mWifiLogProto.numNetworksAddedByApps="
                        + mWifiLogProto.numNetworksAddedByApps);
                pw.println("mWifiLogProto.numNonEmptyScanResults="
                        + mWifiLogProto.numNonEmptyScanResults);
                pw.println("mWifiLogProto.numEmptyScanResults="
                        + mWifiLogProto.numEmptyScanResults);
                pw.println("mWifiLogProto.numConnecitvityOneshotScans="
                        + mWifiLogProto.numConnectivityOneshotScans);
                pw.println("mWifiLogProto.numOneshotScans="
                        + mWifiLogProto.numOneshotScans);
                pw.println("mWifiLogProto.numOneshotHasDfsChannelScans="
                        + mWifiLogProto.numOneshotHasDfsChannelScans);
                pw.println("mWifiLogProto.numBackgroundScans="
                        + mWifiLogProto.numBackgroundScans);
                pw.println("mWifiLogProto.numExternalAppOneshotScanRequests="
                        + mWifiLogProto.numExternalAppOneshotScanRequests);
                pw.println("mWifiLogProto.numExternalForegroundAppOneshotScanRequestsThrottled="
                        + mWifiLogProto.numExternalForegroundAppOneshotScanRequestsThrottled);
                pw.println("mWifiLogProto.numExternalBackgroundAppOneshotScanRequestsThrottled="
                        + mWifiLogProto.numExternalBackgroundAppOneshotScanRequestsThrottled);
                pw.println("mWifiLogProto.meteredNetworkStatsSaved=");
                pw.println(mMeteredNetworkStatsBuilder.toProto(false));
                pw.println("mWifiLogProto.meteredNetworkStatsSuggestion=");
                pw.println(mMeteredNetworkStatsBuilder.toProto(true));
                pw.println("mScanReturnEntries:");
                pw.println("  SCAN_UNKNOWN: " + getScanReturnEntry(
                        WifiMetricsProto.WifiLog.SCAN_UNKNOWN));
                pw.println("  SCAN_SUCCESS: " + getScanReturnEntry(
                        WifiMetricsProto.WifiLog.SCAN_SUCCESS));
                pw.println("  SCAN_FAILURE_INTERRUPTED: " + getScanReturnEntry(
                        WifiMetricsProto.WifiLog.SCAN_FAILURE_INTERRUPTED));
                pw.println("  SCAN_FAILURE_INVALID_CONFIGURATION: " + getScanReturnEntry(
                        WifiMetricsProto.WifiLog.SCAN_FAILURE_INVALID_CONFIGURATION));
                pw.println("  FAILURE_WIFI_DISABLED: " + getScanReturnEntry(
                        WifiMetricsProto.WifiLog.FAILURE_WIFI_DISABLED));

                pw.println("mSystemStateEntries: <state><screenOn> : <scansInitiated>");
                pw.println("  WIFI_UNKNOWN       ON: "
                        + getSystemStateCount(WifiMetricsProto.WifiLog.WIFI_UNKNOWN, true));
                pw.println("  WIFI_DISABLED      ON: "
                        + getSystemStateCount(WifiMetricsProto.WifiLog.WIFI_DISABLED, true));
                pw.println("  WIFI_DISCONNECTED  ON: "
                        + getSystemStateCount(WifiMetricsProto.WifiLog.WIFI_DISCONNECTED, true));
                pw.println("  WIFI_ASSOCIATED    ON: "
                        + getSystemStateCount(WifiMetricsProto.WifiLog.WIFI_ASSOCIATED, true));
                pw.println("  WIFI_UNKNOWN      OFF: "
                        + getSystemStateCount(WifiMetricsProto.WifiLog.WIFI_UNKNOWN, false));
                pw.println("  WIFI_DISABLED     OFF: "
                        + getSystemStateCount(WifiMetricsProto.WifiLog.WIFI_DISABLED, false));
                pw.println("  WIFI_DISCONNECTED OFF: "
                        + getSystemStateCount(WifiMetricsProto.WifiLog.WIFI_DISCONNECTED, false));
                pw.println("  WIFI_ASSOCIATED   OFF: "
                        + getSystemStateCount(WifiMetricsProto.WifiLog.WIFI_ASSOCIATED, false));
                pw.println("mWifiLogProto.numConnectivityWatchdogPnoGood="
                        + mWifiLogProto.numConnectivityWatchdogPnoGood);
                pw.println("mWifiLogProto.numConnectivityWatchdogPnoBad="
                        + mWifiLogProto.numConnectivityWatchdogPnoBad);
                pw.println("mWifiLogProto.numConnectivityWatchdogBackgroundGood="
                        + mWifiLogProto.numConnectivityWatchdogBackgroundGood);
                pw.println("mWifiLogProto.numConnectivityWatchdogBackgroundBad="
                        + mWifiLogProto.numConnectivityWatchdogBackgroundBad);
                pw.println("mWifiLogProto.numLastResortWatchdogTriggers="
                        + mWifiLogProto.numLastResortWatchdogTriggers);
                pw.println("mWifiLogProto.numLastResortWatchdogBadAssociationNetworksTotal="
                        + mWifiLogProto.numLastResortWatchdogBadAssociationNetworksTotal);
                pw.println("mWifiLogProto.numLastResortWatchdogBadAuthenticationNetworksTotal="
                        + mWifiLogProto.numLastResortWatchdogBadAuthenticationNetworksTotal);
                pw.println("mWifiLogProto.numLastResortWatchdogBadDhcpNetworksTotal="
                        + mWifiLogProto.numLastResortWatchdogBadDhcpNetworksTotal);
                pw.println("mWifiLogProto.numLastResortWatchdogBadOtherNetworksTotal="
                        + mWifiLogProto.numLastResortWatchdogBadOtherNetworksTotal);
                pw.println("mWifiLogProto.numLastResortWatchdogAvailableNetworksTotal="
                        + mWifiLogProto.numLastResortWatchdogAvailableNetworksTotal);
                pw.println("mWifiLogProto.numLastResortWatchdogTriggersWithBadAssociation="
                        + mWifiLogProto.numLastResortWatchdogTriggersWithBadAssociation);
                pw.println("mWifiLogProto.numLastResortWatchdogTriggersWithBadAuthentication="
                        + mWifiLogProto.numLastResortWatchdogTriggersWithBadAuthentication);
                pw.println("mWifiLogProto.numLastResortWatchdogTriggersWithBadDhcp="
                        + mWifiLogProto.numLastResortWatchdogTriggersWithBadDhcp);
                pw.println("mWifiLogProto.numLastResortWatchdogTriggersWithBadOther="
                        + mWifiLogProto.numLastResortWatchdogTriggersWithBadOther);
                pw.println("mWifiLogProto.numLastResortWatchdogSuccesses="
                        + mWifiLogProto.numLastResortWatchdogSuccesses);
                pw.println("mWifiLogProto.watchdogTotalConnectionFailureCountAfterTrigger="
                        + mWifiLogProto.watchdogTotalConnectionFailureCountAfterTrigger);
                pw.println("mWifiLogProto.watchdogTriggerToConnectionSuccessDurationMs="
                        + mWifiLogProto.watchdogTriggerToConnectionSuccessDurationMs);
                pw.println("mWifiLogProto.recordDurationSec="
                        + ((mClock.getElapsedSinceBootMillis() / 1000) - mRecordStartTimeSec));

                try {
                    JSONObject rssiMap = new JSONObject();
                    for (Map.Entry<Integer, SparseIntArray> entry : mRssiPollCountsMap.entrySet()) {
                        int frequency = entry.getKey();
                        final SparseIntArray histogram = entry.getValue();
                        JSONArray histogramElements = new JSONArray();
                        for (int i = MIN_RSSI_POLL; i <= MAX_RSSI_POLL; i++) {
                            int count = histogram.get(i);
                            if (count == 0) {
                                continue;
                            }
                            JSONObject histogramElement = new JSONObject();
                            histogramElement.put(Integer.toString(i), count);
                            histogramElements.put(histogramElement);
                        }
                        rssiMap.put(Integer.toString(frequency), histogramElements);
                    }
                    pw.println("mWifiLogProto.rssiPollCount: " + rssiMap.toString());
                } catch (JSONException e) {
                    pw.println("JSONException occurred: " + e.getMessage());
                }

                pw.println("mWifiLogProto.rssiPollDeltaCount: Printing counts for ["
                        + MIN_RSSI_DELTA + ", " + MAX_RSSI_DELTA + "]");
                StringBuilder sb = new StringBuilder();
                for (int i = MIN_RSSI_DELTA; i <= MAX_RSSI_DELTA; i++) {
                    sb.append(mRssiDeltaCounts.get(i) + " ");
                }
                pw.println("  " + sb.toString());
                pw.println("mWifiLogProto.linkSpeedCounts: ");
                sb.setLength(0);
                for (int i = 0; i < mLinkSpeedCounts.size(); i++) {
                    LinkSpeedCount linkSpeedCount = mLinkSpeedCounts.valueAt(i);
                    sb.append(linkSpeedCount.linkSpeedMbps).append(":{")
                            .append(linkSpeedCount.count).append(", ")
                            .append(linkSpeedCount.rssiSumDbm).append(", ")
                            .append(linkSpeedCount.rssiSumOfSquaresDbmSq).append("} ");
                }
                if (sb.length() > 0) {
                    pw.println(sb.toString());
                }
                pw.print("mWifiLogProto.alertReasonCounts=");
                sb.setLength(0);
                for (int i = WifiLoggerHal.WIFI_ALERT_REASON_MIN;
                        i <= WifiLoggerHal.WIFI_ALERT_REASON_MAX; i++) {
                    int count = mWifiAlertReasonCounts.get(i);
                    if (count > 0) {
                        sb.append("(" + i + "," + count + "),");
                    }
                }
                if (sb.length() > 1) {
                    sb.setLength(sb.length() - 1);  // strip trailing comma
                    pw.println(sb.toString());
                } else {
                    pw.println("()");
                }
                pw.println("mWifiLogProto.numTotalScanResults="
                        + mWifiLogProto.numTotalScanResults);
                pw.println("mWifiLogProto.numOpenNetworkScanResults="
                        + mWifiLogProto.numOpenNetworkScanResults);
                pw.println("mWifiLogProto.numLegacyPersonalNetworkScanResults="
                        + mWifiLogProto.numLegacyPersonalNetworkScanResults);
                pw.println("mWifiLogProto.numLegacyEnterpriseNetworkScanResults="
                        + mWifiLogProto.numLegacyEnterpriseNetworkScanResults);
                pw.println("mWifiLogProto.numEnhancedOpenNetworkScanResults="
                        + mWifiLogProto.numEnhancedOpenNetworkScanResults);
                pw.println("mWifiLogProto.numWpa3PersonalNetworkScanResults="
                        + mWifiLogProto.numWpa3PersonalNetworkScanResults);
                pw.println("mWifiLogProto.numWpa3EnterpriseNetworkScanResults="
                        + mWifiLogProto.numWpa3EnterpriseNetworkScanResults);
                pw.println("mWifiLogProto.numWapiPersonalNetworkScanResults="
                        + mWifiLogProto.numWapiPersonalNetworkScanResults);
                pw.println("mWifiLogProto.numWapiEnterpriseNetworkScanResults="
                        + mWifiLogProto.numWapiEnterpriseNetworkScanResults);
                pw.println("mWifiLogProto.numHiddenNetworkScanResults="
                        + mWifiLogProto.numHiddenNetworkScanResults);
                pw.println("mWifiLogProto.numHotspot2R1NetworkScanResults="
                        + mWifiLogProto.numHotspot2R1NetworkScanResults);
                pw.println("mWifiLogProto.numHotspot2R2NetworkScanResults="
                        + mWifiLogProto.numHotspot2R2NetworkScanResults);
                pw.println("mWifiLogProto.numHotspot2R3NetworkScanResults="
                        + mWifiLogProto.numHotspot2R3NetworkScanResults);
                pw.println("mWifiLogProto.numMboSupportedNetworkScanResults="
                        + mWifiLogProto.numMboSupportedNetworkScanResults);
                pw.println("mWifiLogProto.numMboCellularDataAwareNetworkScanResults="
                        + mWifiLogProto.numMboCellularDataAwareNetworkScanResults);
                pw.println("mWifiLogProto.numOceSupportedNetworkScanResults="
                        + mWifiLogProto.numOceSupportedNetworkScanResults);
                pw.println("mWifiLogProto.numFilsSupportedNetworkScanResults="
                        + mWifiLogProto.numFilsSupportedNetworkScanResults);
                pw.println("mWifiLogProto.num11AxNetworkScanResults="
                        + mWifiLogProto.num11AxNetworkScanResults);
                pw.println("mWifiLogProto.num6GNetworkScanResults"
                        + mWifiLogProto.num6GNetworkScanResults);
                pw.println("mWifiLogProto.num6GPscNetworkScanResults"
                        + mWifiLogProto.num6GPscNetworkScanResults);
                pw.println("mWifiLogProto.numBssidFilteredDueToMboAssocDisallowInd="
                        + mWifiLogProto.numBssidFilteredDueToMboAssocDisallowInd);
                pw.println("mWifiLogProto.numConnectToNetworkSupportingMbo="
                        + mWifiLogProto.numConnectToNetworkSupportingMbo);
                pw.println("mWifiLogProto.numConnectToNetworkSupportingOce="
                        + mWifiLogProto.numConnectToNetworkSupportingOce);
                pw.println("mWifiLogProto.numSteeringRequest="
                        + mWifiLogProto.numSteeringRequest);
                pw.println("mWifiLogProto.numForceScanDueToSteeringRequest="
                        + mWifiLogProto.numForceScanDueToSteeringRequest);
                pw.println("mWifiLogProto.numMboCellularSwitchRequest="
                        + mWifiLogProto.numMboCellularSwitchRequest);
                pw.println("mWifiLogProto.numSteeringRequestIncludingMboAssocRetryDelay="
                        + mWifiLogProto.numSteeringRequestIncludingMboAssocRetryDelay);
                pw.println("mWifiLogProto.numConnectRequestWithFilsAkm="
                        + mWifiLogProto.numConnectRequestWithFilsAkm);
                pw.println("mWifiLogProto.numL2ConnectionThroughFilsAuthentication="
                        + mWifiLogProto.numL2ConnectionThroughFilsAuthentication);
                pw.println("mWifiLogProto.recentFailureAssociationStatus="
                        + mRecentFailureAssociationStatus.toString());

                pw.println("mWifiLogProto.numScans=" + mWifiLogProto.numScans);
                pw.println("mWifiLogProto.WifiScoreCount: [" + MIN_WIFI_SCORE + ", "
                        + MAX_WIFI_SCORE + "]");
                for (int i = 0; i <= MAX_WIFI_SCORE; i++) {
                    pw.print(mWifiScoreCounts.get(i) + " ");
                }
                pw.println(); // add a line after wifi scores
                pw.println("mWifiLogProto.WifiUsabilityScoreCount: [" + MIN_WIFI_USABILITY_SCORE
                        + ", " + MAX_WIFI_USABILITY_SCORE + "]");
                for (int i = MIN_WIFI_USABILITY_SCORE; i <= MAX_WIFI_USABILITY_SCORE; i++) {
                    pw.print(mWifiUsabilityScoreCounts.get(i) + " ");
                }
                pw.println(); // add a line after wifi usability scores
                pw.println("mWifiLogProto.SoftApManagerReturnCodeCounts:");
                pw.println("  SUCCESS: " + mSoftApManagerReturnCodeCounts.get(
                        WifiMetricsProto.SoftApReturnCodeCount.SOFT_AP_STARTED_SUCCESSFULLY));
                pw.println("  FAILED_GENERAL_ERROR: " + mSoftApManagerReturnCodeCounts.get(
                        WifiMetricsProto.SoftApReturnCodeCount.SOFT_AP_FAILED_GENERAL_ERROR));
                pw.println("  FAILED_NO_CHANNEL: " + mSoftApManagerReturnCodeCounts.get(
                        WifiMetricsProto.SoftApReturnCodeCount.SOFT_AP_FAILED_NO_CHANNEL));
                pw.println("  FAILED_UNSUPPORTED_CONFIGURATION: "
                        + mSoftApManagerReturnCodeCounts.get(
                        WifiMetricsProto.SoftApReturnCodeCount
                        .SOFT_AP_FAILED_UNSUPPORTED_CONFIGURATION));
                pw.print("\n");
                pw.println("mWifiLogProto.numHalCrashes="
                        + mWifiLogProto.numHalCrashes);
                pw.println("mWifiLogProto.numWificondCrashes="
                        + mWifiLogProto.numWificondCrashes);
                pw.println("mWifiLogProto.numSupplicantCrashes="
                        + mWifiLogProto.numSupplicantCrashes);
                pw.println("mWifiLogProto.numHostapdCrashes="
                        + mWifiLogProto.numHostapdCrashes);
                pw.println("mWifiLogProto.numSetupClientInterfaceFailureDueToHal="
                        + mWifiLogProto.numSetupClientInterfaceFailureDueToHal);
                pw.println("mWifiLogProto.numSetupClientInterfaceFailureDueToWificond="
                        + mWifiLogProto.numSetupClientInterfaceFailureDueToWificond);
                pw.println("mWifiLogProto.numSetupClientInterfaceFailureDueToSupplicant="
                        + mWifiLogProto.numSetupClientInterfaceFailureDueToSupplicant);
                pw.println("mWifiLogProto.numSetupSoftApInterfaceFailureDueToHal="
                        + mWifiLogProto.numSetupSoftApInterfaceFailureDueToHal);
                pw.println("mWifiLogProto.numSetupSoftApInterfaceFailureDueToWificond="
                        + mWifiLogProto.numSetupSoftApInterfaceFailureDueToWificond);
                pw.println("mWifiLogProto.numSetupSoftApInterfaceFailureDueToHostapd="
                        + mWifiLogProto.numSetupSoftApInterfaceFailureDueToHostapd);
                pw.println("StaEventList:");
                for (StaEventWithTime event : mStaEventList) {
                    pw.println(event);
                }
                pw.println("UserActionEvents:");
                for (UserActionEventWithTime event : mUserActionEventList) {
                    pw.println(event);
                }

                pw.println("mWifiLogProto.numPasspointProviders="
                        + mWifiLogProto.numPasspointProviders);
                pw.println("mWifiLogProto.numPasspointProviderInstallation="
                        + mWifiLogProto.numPasspointProviderInstallation);
                pw.println("mWifiLogProto.numPasspointProviderInstallSuccess="
                        + mWifiLogProto.numPasspointProviderInstallSuccess);
                pw.println("mWifiLogProto.numPasspointProviderUninstallation="
                        + mWifiLogProto.numPasspointProviderUninstallation);
                pw.println("mWifiLogProto.numPasspointProviderUninstallSuccess="
                        + mWifiLogProto.numPasspointProviderUninstallSuccess);
                pw.println("mWifiLogProto.numPasspointProvidersSuccessfullyConnected="
                        + mWifiLogProto.numPasspointProvidersSuccessfullyConnected);

                pw.println("mWifiLogProto.installedPasspointProfileTypeForR1:"
                        + mInstalledPasspointProfileTypeForR1);
                pw.println("mWifiLogProto.installedPasspointProfileTypeForR2:"
                        + mInstalledPasspointProfileTypeForR2);

                pw.println("mWifiLogProto.passpointProvisionStats.numProvisionSuccess="
                        + mNumProvisionSuccess);
                pw.println("mWifiLogProto.passpointProvisionStats.provisionFailureCount:"
                        + mPasspointProvisionFailureCounts);
                pw.println("mWifiLogProto.totalNumberOfPasspointConnectionsWithVenueUrl="
                        + mWifiLogProto.totalNumberOfPasspointConnectionsWithVenueUrl);
                pw.println(
                        "mWifiLogProto.totalNumberOfPasspointConnectionsWithTermsAndConditionsUrl="
                                + mWifiLogProto
                                .totalNumberOfPasspointConnectionsWithTermsAndConditionsUrl);
                pw.println(
                        "mWifiLogProto"
                                + ".totalNumberOfPasspointAcceptanceOfTermsAndConditions="
                                + mWifiLogProto
                                .totalNumberOfPasspointAcceptanceOfTermsAndConditions);
                pw.println("mWifiLogProto.totalNumberOfPasspointProfilesWithDecoratedIdentity="
                        + mWifiLogProto.totalNumberOfPasspointProfilesWithDecoratedIdentity);
                pw.println("mWifiLogProto.passpointDeauthImminentScope="
                        + mPasspointDeauthImminentScope.toString());

                pw.println("mWifiLogProto.numRadioModeChangeToMcc="
                        + mWifiLogProto.numRadioModeChangeToMcc);
                pw.println("mWifiLogProto.numRadioModeChangeToScc="
                        + mWifiLogProto.numRadioModeChangeToScc);
                pw.println("mWifiLogProto.numRadioModeChangeToSbs="
                        + mWifiLogProto.numRadioModeChangeToSbs);
                pw.println("mWifiLogProto.numRadioModeChangeToDbs="
                        + mWifiLogProto.numRadioModeChangeToDbs);
                pw.println("mWifiLogProto.numSoftApUserBandPreferenceUnsatisfied="
                        + mWifiLogProto.numSoftApUserBandPreferenceUnsatisfied);
                pw.println("mTotalSsidsInScanHistogram:"
                        + mTotalSsidsInScanHistogram.toString());
                pw.println("mTotalBssidsInScanHistogram:"
                        + mTotalBssidsInScanHistogram.toString());
                pw.println("mAvailableOpenSsidsInScanHistogram:"
                        + mAvailableOpenSsidsInScanHistogram.toString());
                pw.println("mAvailableOpenBssidsInScanHistogram:"
                        + mAvailableOpenBssidsInScanHistogram.toString());
                pw.println("mAvailableSavedSsidsInScanHistogram:"
                        + mAvailableSavedSsidsInScanHistogram.toString());
                pw.println("mAvailableSavedBssidsInScanHistogram:"
                        + mAvailableSavedBssidsInScanHistogram.toString());
                pw.println("mAvailableOpenOrSavedSsidsInScanHistogram:"
                        + mAvailableOpenOrSavedSsidsInScanHistogram.toString());
                pw.println("mAvailableOpenOrSavedBssidsInScanHistogram:"
                        + mAvailableOpenOrSavedBssidsInScanHistogram.toString());
                pw.println("mAvailableSavedPasspointProviderProfilesInScanHistogram:"
                        + mAvailableSavedPasspointProviderProfilesInScanHistogram.toString());
                pw.println("mAvailableSavedPasspointProviderBssidsInScanHistogram:"
                        + mAvailableSavedPasspointProviderBssidsInScanHistogram.toString());
                pw.println("mWifiLogProto.partialAllSingleScanListenerResults="
                        + mWifiLogProto.partialAllSingleScanListenerResults);
                pw.println("mWifiLogProto.fullBandAllSingleScanListenerResults="
                        + mWifiLogProto.fullBandAllSingleScanListenerResults);
                pw.println("mWifiAwareMetrics:");
                mWifiAwareMetrics.dump(fd, pw, args);
                pw.println("mRttMetrics:");
                mRttMetrics.dump(fd, pw, args);

                pw.println("mPnoScanMetrics.numPnoScanAttempts="
                        + mPnoScanMetrics.numPnoScanAttempts);
                pw.println("mPnoScanMetrics.numPnoScanFailed="
                        + mPnoScanMetrics.numPnoScanFailed);
                pw.println("mPnoScanMetrics.numPnoScanStartedOverOffload="
                        + mPnoScanMetrics.numPnoScanStartedOverOffload);
                pw.println("mPnoScanMetrics.numPnoScanFailedOverOffload="
                        + mPnoScanMetrics.numPnoScanFailedOverOffload);
                pw.println("mPnoScanMetrics.numPnoFoundNetworkEvents="
                        + mPnoScanMetrics.numPnoFoundNetworkEvents);

                pw.println("mWifiLinkLayerUsageStats.loggingDurationMs="
                        + mWifiLinkLayerUsageStats.loggingDurationMs);
                pw.println("mWifiLinkLayerUsageStats.radioOnTimeMs="
                        + mWifiLinkLayerUsageStats.radioOnTimeMs);
                pw.println("mWifiLinkLayerUsageStats.radioTxTimeMs="
                        + mWifiLinkLayerUsageStats.radioTxTimeMs);
                pw.println("mWifiLinkLayerUsageStats.radioRxTimeMs="
                        + mWifiLinkLayerUsageStats.radioRxTimeMs);
                pw.println("mWifiLinkLayerUsageStats.radioScanTimeMs="
                        + mWifiLinkLayerUsageStats.radioScanTimeMs);
                pw.println("mWifiLinkLayerUsageStats.radioNanScanTimeMs="
                        + mWifiLinkLayerUsageStats.radioNanScanTimeMs);
                pw.println("mWifiLinkLayerUsageStats.radioBackgroundScanTimeMs="
                        + mWifiLinkLayerUsageStats.radioBackgroundScanTimeMs);
                pw.println("mWifiLinkLayerUsageStats.radioRoamScanTimeMs="
                        + mWifiLinkLayerUsageStats.radioRoamScanTimeMs);
                pw.println("mWifiLinkLayerUsageStats.radioPnoScanTimeMs="
                        + mWifiLinkLayerUsageStats.radioPnoScanTimeMs);
                pw.println("mWifiLinkLayerUsageStats.radioHs20ScanTimeMs="
                        + mWifiLinkLayerUsageStats.radioHs20ScanTimeMs);
                pw.println("mWifiLinkLayerUsageStats per Radio Stats: ");
                for (int i = 0; i < mRadioStats.size(); i++) {
                    RadioStats radioStat = mRadioStats.valueAt(i);
                    pw.println("radioId=" + radioStat.radioId);
                    pw.println("totalRadioOnTimeMs=" + radioStat.totalRadioOnTimeMs);
                    pw.println("totalRadioTxTimeMs=" + radioStat.totalRadioTxTimeMs);
                    pw.println("totalRadioRxTimeMs=" + radioStat.totalRadioRxTimeMs);
                    pw.println("totalScanTimeMs=" + radioStat.totalScanTimeMs);
                    pw.println("totalNanScanTimeMs=" + radioStat.totalNanScanTimeMs);
                    pw.println("totalBackgroundScanTimeMs=" + radioStat.totalBackgroundScanTimeMs);
                    pw.println("totalRoamScanTimeMs=" + radioStat.totalRoamScanTimeMs);
                    pw.println("totalPnoScanTimeMs=" + radioStat.totalPnoScanTimeMs);
                    pw.println("totalHotspot2ScanTimeMs=" + radioStat.totalHotspot2ScanTimeMs);
                }

                pw.println("mWifiLogProto.connectToNetworkNotificationCount="
                        + mConnectToNetworkNotificationCount.toString());
                pw.println("mWifiLogProto.connectToNetworkNotificationActionCount="
                        + mConnectToNetworkNotificationActionCount.toString());
                pw.println("mWifiLogProto.openNetworkRecommenderBlocklistSize="
                        + mOpenNetworkRecommenderBlocklistSize);
                pw.println("mWifiLogProto.isWifiNetworksAvailableNotificationOn="
                        + mIsWifiNetworksAvailableNotificationOn);
                pw.println("mWifiLogProto.numOpenNetworkRecommendationUpdates="
                        + mNumOpenNetworkRecommendationUpdates);
                pw.println("mWifiLogProto.numOpenNetworkConnectMessageFailedToSend="
                        + mNumOpenNetworkConnectMessageFailedToSend);

                pw.println("mWifiLogProto.observedHotspotR1ApInScanHistogram="
                        + mObservedHotspotR1ApInScanHistogram);
                pw.println("mWifiLogProto.observedHotspotR2ApInScanHistogram="
                        + mObservedHotspotR2ApInScanHistogram);
                pw.println("mWifiLogProto.observedHotspotR3ApInScanHistogram="
                        + mObservedHotspotR3ApInScanHistogram);
                pw.println("mWifiLogProto.observedHotspotR1EssInScanHistogram="
                        + mObservedHotspotR1EssInScanHistogram);
                pw.println("mWifiLogProto.observedHotspotR2EssInScanHistogram="
                        + mObservedHotspotR2EssInScanHistogram);
                pw.println("mWifiLogProto.observedHotspotR3EssInScanHistogram="
                        + mObservedHotspotR3EssInScanHistogram);
                pw.println("mWifiLogProto.observedHotspotR1ApsPerEssInScanHistogram="
                        + mObservedHotspotR1ApsPerEssInScanHistogram);
                pw.println("mWifiLogProto.observedHotspotR2ApsPerEssInScanHistogram="
                        + mObservedHotspotR2ApsPerEssInScanHistogram);
                pw.println("mWifiLogProto.observedHotspotR3ApsPerEssInScanHistogram="
                        + mObservedHotspotR3ApsPerEssInScanHistogram);

                pw.println("mWifiLogProto.observed80211mcSupportingApsInScanHistogram"
                        + mObserved80211mcApInScanHistogram);
                pw.println("mWifiLogProto.bssidBlocklistStats:");
                pw.println(mBssidBlocklistStats.toString());

                pw.println("mSoftApTetheredEvents:");
                for (SoftApConnectedClientsEvent event : mSoftApEventListTethered) {
                    StringBuilder eventLine = new StringBuilder();
                    eventLine.append("event_type=" + event.eventType);
                    eventLine.append(",time_stamp_millis=" + event.timeStampMillis);
                    eventLine.append(",num_connected_clients=" + event.numConnectedClients);
                    eventLine.append(",num_connected_clients_on_current_frequency="
                            + event.numConnectedClientsOnCurrentFrequency);
                    eventLine.append(",channel_frequency=" + event.channelFrequency);
                    eventLine.append(",channel_bandwidth=" + event.channelBandwidth);
                    eventLine.append(",generation=" + event.generation);
                    eventLine.append(",max_num_clients_setting_in_softap_configuration="
                            + event.maxNumClientsSettingInSoftapConfiguration);
                    eventLine.append(",max_num_clients_setting_in_softap_capability="
                            + event.maxNumClientsSettingInSoftapCapability);
                    eventLine.append(",shutdown_timeout_setting_in_softap_configuration="
                            + event.shutdownTimeoutSettingInSoftapConfiguration);
                    eventLine.append(",default_shutdown_timeout_setting="
                            + event.defaultShutdownTimeoutSetting);
                    eventLine.append(",client_control_is_enabled=" + event.clientControlIsEnabled);
                    pw.println(eventLine.toString());
                }
                pw.println("mSoftApLocalOnlyEvents:");
                for (SoftApConnectedClientsEvent event : mSoftApEventListLocalOnly) {
                    StringBuilder eventLine = new StringBuilder();
                    eventLine.append("event_type=" + event.eventType);
                    eventLine.append(",time_stamp_millis=" + event.timeStampMillis);
                    eventLine.append(",num_connected_clients=" + event.numConnectedClients);
                    eventLine.append(",num_connected_clients_on_current_frequency="
                            + event.numConnectedClientsOnCurrentFrequency);
                    eventLine.append(",channel_frequency=" + event.channelFrequency);
                    eventLine.append(",channel_bandwidth=" + event.channelBandwidth);
                    eventLine.append(",generation=" + event.generation);
                    eventLine.append(",max_num_clients_setting_in_softap_configuration="
                            + event.maxNumClientsSettingInSoftapConfiguration);
                    eventLine.append(",max_num_clients_setting_in_softap_capability="
                            + event.maxNumClientsSettingInSoftapCapability);
                    eventLine.append(",shutdown_timeout_setting_in_softap_configuration="
                            + event.shutdownTimeoutSettingInSoftapConfiguration);
                    eventLine.append(",default_shutdown_timeout_setting="
                            + event.defaultShutdownTimeoutSetting);
                    eventLine.append(",client_control_is_enabled=" + event.clientControlIsEnabled);
                    pw.println(eventLine.toString());
                }

                mWifiPowerMetrics.dump(pw);
                mWifiWakeMetrics.dump(pw);

                pw.println("mWifiLogProto.isMacRandomizationOn="
                        + mContext.getResources().getBoolean(
                                R.bool.config_wifi_connected_mac_randomization_supported));
                pw.println("mWifiLogProto.scoreExperimentId=" + mWifiLogProto.scoreExperimentId);
                pw.println("mExperimentValues.wifiDataStallMinTxBad="
                        + mContext.getResources().getInteger(
                                R.integer.config_wifiDataStallMinTxBad));
                pw.println("mExperimentValues.wifiDataStallMinTxSuccessWithoutRx="
                        + mContext.getResources().getInteger(
                                R.integer.config_wifiDataStallMinTxSuccessWithoutRx));
                pw.println("mExperimentValues.linkSpeedCountsLoggingEnabled="
                        + mContext.getResources().getBoolean(
                                R.bool.config_wifiLinkSpeedMetricsEnabled));
                pw.println("mExperimentValues.dataStallDurationMs="
                        + mExperimentValues.dataStallDurationMs);
                pw.println("mExperimentValues.dataStallTxTputThrKbps="
                        + mExperimentValues.dataStallTxTputThrKbps);
                pw.println("mExperimentValues.dataStallRxTputThrKbps="
                        + mExperimentValues.dataStallRxTputThrKbps);
                pw.println("mExperimentValues.dataStallTxPerThr="
                        + mExperimentValues.dataStallTxPerThr);
                pw.println("mExperimentValues.dataStallCcaLevelThr="
                        + mExperimentValues.dataStallCcaLevelThr);
                pw.println("WifiIsUnusableEventList: ");
                for (WifiIsUnusableWithTime event : mWifiIsUnusableList) {
                    pw.println(event);
                }
                pw.println("Hardware Version: " + SystemProperties.get("ro.boot.revision", ""));

                pw.println("mWifiUsabilityStatsEntriesList:");
                for (WifiUsabilityStatsEntry stats : mWifiUsabilityStatsEntriesList) {
                    printWifiUsabilityStatsEntry(pw, stats);
                }
                pw.println("mWifiUsabilityStatsList:");
                for (WifiUsabilityStats stats : mWifiUsabilityStatsListGood) {
                    pw.println("\nlabel=" + stats.label);
                    pw.println("\ntrigger_type=" + stats.triggerType);
                    pw.println("\ntime_stamp_ms=" + stats.timeStampMs);
                    for (WifiUsabilityStatsEntry entry : stats.stats) {
                        printWifiUsabilityStatsEntry(pw, entry);
                    }
                }
                for (WifiUsabilityStats stats : mWifiUsabilityStatsListBad) {
                    pw.println("\nlabel=" + stats.label);
                    pw.println("\ntrigger_type=" + stats.triggerType);
                    pw.println("\ntime_stamp_ms=" + stats.timeStampMs);
                    for (WifiUsabilityStatsEntry entry : stats.stats) {
                        printWifiUsabilityStatsEntry(pw, entry);
                    }
                }

                pw.println("mMobilityStatePnoStatsMap:");
                for (int i = 0; i < mMobilityStatePnoStatsMap.size(); i++) {
                    printDeviceMobilityStatePnoScanStats(pw, mMobilityStatePnoStatsMap.valueAt(i));
                }

                mWifiP2pMetrics.dump(pw);
                pw.println("mDppMetrics:");
                mDppMetrics.dump(pw);

                pw.println("mWifiConfigStoreReadDurationHistogram:"
                        + mWifiConfigStoreReadDurationHistogram.toString());
                pw.println("mWifiConfigStoreWriteDurationHistogram:"
                        + mWifiConfigStoreWriteDurationHistogram.toString());

                pw.println("mLinkProbeSuccessRssiCounts:" + mLinkProbeSuccessRssiCounts);
                pw.println("mLinkProbeFailureRssiCounts:" + mLinkProbeFailureRssiCounts);
                pw.println("mLinkProbeSuccessLinkSpeedCounts:" + mLinkProbeSuccessLinkSpeedCounts);
                pw.println("mLinkProbeFailureLinkSpeedCounts:" + mLinkProbeFailureLinkSpeedCounts);
                pw.println("mLinkProbeSuccessSecondsSinceLastTxSuccessHistogram:"
                        + mLinkProbeSuccessSecondsSinceLastTxSuccessHistogram);
                pw.println("mLinkProbeFailureSecondsSinceLastTxSuccessHistogram:"
                        + mLinkProbeFailureSecondsSinceLastTxSuccessHistogram);
                pw.println("mLinkProbeSuccessElapsedTimeMsHistogram:"
                        + mLinkProbeSuccessElapsedTimeMsHistogram);
                pw.println("mLinkProbeFailureReasonCounts:" + mLinkProbeFailureReasonCounts);
                pw.println("mLinkProbeExperimentProbeCounts:" + mLinkProbeExperimentProbeCounts);

                pw.println("mNetworkSelectionExperimentPairNumChoicesCounts:"
                        + mNetworkSelectionExperimentPairNumChoicesCounts);
                pw.println("mLinkProbeStaEventCount:" + mLinkProbeStaEventCount);

                pw.println("mWifiNetworkRequestApiLog:\n" + mWifiNetworkRequestApiLog);
                pw.println("mWifiNetworkRequestApiMatchSizeHistogram:\n"
                        + mWifiNetworkRequestApiMatchSizeHistogram);
                pw.println("mWifiNetworkRequestApiConnectionDurationSecOnPrimaryIfaceHistogram:\n"
                        + mWifiNetworkRequestApiConnectionDurationSecOnPrimaryIfaceHistogram);
                pw.println("mWifiNetworkRequestApiConnectionDurationSecOnSecondaryIfaceHistogram:\n"
                        + mWifiNetworkRequestApiConnectionDurationSecOnSecondaryIfaceHistogram);
                pw.println("mWifiNetworkRequestApiConcurrentConnectionDurationSecHistogram:\n"
                        + mWifiNetworkRequestApiConcurrentConnectionDurationSecHistogram);
                pw.println("mWifiNetworkSuggestionApiLog:\n" + mWifiNetworkSuggestionApiLog);
                pw.println("mWifiNetworkSuggestionApiMatchSizeHistogram:\n"
                        + mWifiNetworkSuggestionApiListSizeHistogram);
                pw.println("mWifiNetworkSuggestionApiAppTypeCounter:\n"
                        + mWifiNetworkSuggestionApiAppTypeCounter);
                pw.println("mWifiNetworkSuggestionPriorityGroups:\n"
                        + mWifiNetworkSuggestionPriorityGroups.toString());
                pw.println("mWifiNetworkSuggestionCoexistSavedNetworks:\n"
                        + mWifiNetworkSuggestionCoexistSavedNetworks.toString());
                printUserApprovalSuggestionAppReaction(pw);
                printUserApprovalCarrierReaction(pw);
                pw.println("mNetworkIdToNominatorId:\n" + mNetworkIdToNominatorId);
                pw.println("mWifiLockStats:\n" + mWifiLockStats);
                pw.println("mWifiLockHighPerfAcqDurationSecHistogram:\n"
                        + mWifiLockHighPerfAcqDurationSecHistogram);
                pw.println("mWifiLockLowLatencyAcqDurationSecHistogram:\n"
                        + mWifiLockLowLatencyAcqDurationSecHistogram);
                pw.println("mWifiLockHighPerfActiveSessionDurationSecHistogram:\n"
                        + mWifiLockHighPerfActiveSessionDurationSecHistogram);
                pw.println("mWifiLockLowLatencyActiveSessionDurationSecHistogram:\n"
                        + mWifiLockLowLatencyActiveSessionDurationSecHistogram);
                pw.println("mWifiToggleStats:\n" + mWifiToggleStats);
                pw.println("mWifiLogProto.numAddOrUpdateNetworkCalls="
                        + mWifiLogProto.numAddOrUpdateNetworkCalls);
                pw.println("mWifiLogProto.numEnableNetworkCalls="
                        + mWifiLogProto.numEnableNetworkCalls);

                pw.println("mWifiLogProto.txLinkSpeedCount2g=" + mTxLinkSpeedCount2g);
                pw.println("mWifiLogProto.txLinkSpeedCount5gLow=" + mTxLinkSpeedCount5gLow);
                pw.println("mWifiLogProto.txLinkSpeedCount5gMid=" + mTxLinkSpeedCount5gMid);
                pw.println("mWifiLogProto.txLinkSpeedCount5gHigh=" + mTxLinkSpeedCount5gHigh);
                pw.println("mWifiLogProto.txLinkSpeedCount6gLow=" + mTxLinkSpeedCount6gLow);
                pw.println("mWifiLogProto.txLinkSpeedCount6gMid=" + mTxLinkSpeedCount6gMid);
                pw.println("mWifiLogProto.txLinkSpeedCount6gHigh=" + mTxLinkSpeedCount6gHigh);

                pw.println("mWifiLogProto.rxLinkSpeedCount2g=" + mRxLinkSpeedCount2g);
                pw.println("mWifiLogProto.rxLinkSpeedCount5gLow=" + mRxLinkSpeedCount5gLow);
                pw.println("mWifiLogProto.rxLinkSpeedCount5gMid=" + mRxLinkSpeedCount5gMid);
                pw.println("mWifiLogProto.rxLinkSpeedCount5gHigh=" + mRxLinkSpeedCount5gHigh);
                pw.println("mWifiLogProto.rxLinkSpeedCount6gLow=" + mRxLinkSpeedCount6gLow);
                pw.println("mWifiLogProto.rxLinkSpeedCount6gMid=" + mRxLinkSpeedCount6gMid);
                pw.println("mWifiLogProto.rxLinkSpeedCount6gHigh=" + mRxLinkSpeedCount6gHigh);

                pw.println("mWifiLogProto.numIpRenewalFailure="
                        + mWifiLogProto.numIpRenewalFailure);
                pw.println("mWifiLogProto.connectionDurationStats="
                        + mConnectionDurationStats.toString());
                pw.println("mWifiLogProto.isExternalWifiScorerOn="
                        + mWifiLogProto.isExternalWifiScorerOn);
                pw.println("mWifiLogProto.wifiOffMetrics="
                        + mWifiOffMetrics.toString());
                pw.println("mWifiLogProto.softApConfigLimitationMetrics="
                        + mSoftApConfigLimitationMetrics.toString());
                pw.println("mChannelUtilizationHistogram2G:\n"
                        + mChannelUtilizationHistogram2G);
                pw.println("mChannelUtilizationHistogramAbove2G:\n"
                        + mChannelUtilizationHistogramAbove2G);
                pw.println("mTxThroughputMbpsHistogram2G:\n"
                        + mTxThroughputMbpsHistogram2G);
                pw.println("mRxThroughputMbpsHistogram2G:\n"
                        + mRxThroughputMbpsHistogram2G);
                pw.println("mTxThroughputMbpsHistogramAbove2G:\n"
                        + mTxThroughputMbpsHistogramAbove2G);
                pw.println("mRxThroughputMbpsHistogramAbove2G:\n"
                        + mRxThroughputMbpsHistogramAbove2G);
                pw.println("mCarrierWifiMetrics:\n"
                        + mCarrierWifiMetrics);
                pw.println(firstConnectAfterBootStatsToString(mFirstConnectAfterBootStats));
                pw.println(wifiToWifiSwitchStatsToString(mWifiToWifiSwitchStats));

                dumpInitPartialScanMetrics(pw);
            }
        }
    }

    private void dumpInitPartialScanMetrics(PrintWriter pw) {
        pw.println("mInitPartialScanTotalCount:\n" + mInitPartialScanTotalCount);
        pw.println("mInitPartialScanSuccessCount:\n" + mInitPartialScanSuccessCount);
        pw.println("mInitPartialScanFailureCount:\n" + mInitPartialScanFailureCount);
        pw.println("mInitPartialScanSuccessHistogram:\n" + mInitPartialScanSuccessHistogram);
        pw.println("mInitPartialScanFailureHistogram:\n" + mInitPartialScanFailureHistogram);
    }

    private void printWifiUsabilityStatsEntry(PrintWriter pw, WifiUsabilityStatsEntry entry) {
        StringBuilder line = new StringBuilder();
        line.append("timestamp_ms=" + entry.timeStampMs);
        line.append(",rssi=" + entry.rssi);
        line.append(",link_speed_mbps=" + entry.linkSpeedMbps);
        line.append(",total_tx_success=" + entry.totalTxSuccess);
        line.append(",total_tx_retries=" + entry.totalTxRetries);
        line.append(",total_tx_bad=" + entry.totalTxBad);
        line.append(",total_rx_success=" + entry.totalRxSuccess);
        if (entry.radioStats != null) {
            for (RadioStats radioStat : entry.radioStats) {
                line.append(",Radio Stats from radio_id=" + radioStat.radioId);
                line.append(",radio_on_time_ms=" + radioStat.totalRadioOnTimeMs);
                line.append(",radio_tx_time_ms=" + radioStat.totalRadioTxTimeMs);
                line.append(",radio_rx_time_ms=" + radioStat.totalRadioRxTimeMs);
                line.append(",scan_time_ms=" + radioStat.totalScanTimeMs);
                line.append(",nan_scan_time_ms=" + radioStat.totalNanScanTimeMs);
                line.append(",background_scan_time_ms=" + radioStat.totalBackgroundScanTimeMs);
                line.append(",roam_scan_time_ms=" + radioStat.totalRoamScanTimeMs);
                line.append(",pno_scan_time_ms=" + radioStat.totalPnoScanTimeMs);
                line.append(",hotspot_2_scan_time_ms=" + radioStat.totalHotspot2ScanTimeMs);
            }
        }
        line.append(",total_radio_on_time_ms=" + entry.totalRadioOnTimeMs);
        line.append(",total_radio_tx_time_ms=" + entry.totalRadioTxTimeMs);
        line.append(",total_radio_rx_time_ms=" + entry.totalRadioRxTimeMs);
        line.append(",total_scan_time_ms=" + entry.totalScanTimeMs);
        line.append(",total_nan_scan_time_ms=" + entry.totalNanScanTimeMs);
        line.append(",total_background_scan_time_ms=" + entry.totalBackgroundScanTimeMs);
        line.append(",total_roam_scan_time_ms=" + entry.totalRoamScanTimeMs);
        line.append(",total_pno_scan_time_ms=" + entry.totalPnoScanTimeMs);
        line.append(",total_hotspot_2_scan_time_ms=" + entry.totalHotspot2ScanTimeMs);
        line.append(",wifi_score=" + entry.wifiScore);
        line.append(",wifi_usability_score=" + entry.wifiUsabilityScore);
        line.append(",seq_num_to_framework=" + entry.seqNumToFramework);
        line.append(",prediction_horizon_sec=" + entry.predictionHorizonSec);
        line.append(",total_cca_busy_freq_time_ms=" + entry.totalCcaBusyFreqTimeMs);
        line.append(",total_radio_on_freq_time_ms=" + entry.totalRadioOnFreqTimeMs);
        line.append(",total_beacon_rx=" + entry.totalBeaconRx);
        line.append(",probe_status_since_last_update=" + entry.probeStatusSinceLastUpdate);
        line.append(",probe_elapsed_time_ms_since_last_update="
                + entry.probeElapsedTimeSinceLastUpdateMs);
        line.append(",probe_mcs_rate_since_last_update=" + entry.probeMcsRateSinceLastUpdate);
        line.append(",rx_link_speed_mbps=" + entry.rxLinkSpeedMbps);
        line.append(",seq_num_inside_framework=" + entry.seqNumInsideFramework);
        line.append(",is_same_bssid_and_freq=" + entry.isSameBssidAndFreq);
        line.append(",device_mobility_state=" + entry.deviceMobilityState);
        line.append(",time_slice_duty_cycle_in_percent=" + entry.timeSliceDutyCycleInPercent);
        if (entry.contentionTimeStats != null) {
            for (ContentionTimeStats stat : entry.contentionTimeStats) {
                line.append(",access_category=" + stat.accessCategory);
                line.append(",contention_time_min_micros=" + stat.contentionTimeMinMicros);
                line.append(",contention_time_max_micros=" + stat.contentionTimeMaxMicros);
                line.append(",contention_time_avg_micros=" + stat.contentionTimeAvgMicros);
                line.append(",contention_num_samples=" + stat.contentionNumSamples);
            }
        }
        line.append(",channel_utilization_ratio=" + entry.channelUtilizationRatio);
        line.append(",is_throughput_sufficient=" + entry.isThroughputSufficient);
        line.append(",is_wifi_scoring_enabled=" + entry.isWifiScoringEnabled);
        line.append(",is_cellular_data_available=" + entry.isCellularDataAvailable);
        line.append(",sta_count=" + entry.staCount);
        line.append(",channel_utilization=" + entry.channelUtilization);
        if (entry.rateStats != null) {
            for (RateStats rateStat : entry.rateStats) {
                line.append(",preamble=" + rateStat.preamble);
                line.append(",nss=" + rateStat.nss);
                line.append(",bw=" + rateStat.bw);
                line.append(",rate_mcs_idx=" + rateStat.rateMcsIdx);
                line.append(",bit_rate_in_kbps=" + rateStat.bitRateInKbps);
                line.append(",tx_mpdu=" + rateStat.txMpdu);
                line.append(",rx_mpdu=" + rateStat.rxMpdu);
                line.append(",mpdu_lost=" + rateStat.mpduLost);
                line.append(",retries=" + rateStat.retries);
            }
        }
        pw.println(line.toString());
    }

    private void printDeviceMobilityStatePnoScanStats(PrintWriter pw,
            DeviceMobilityStatePnoScanStats stats) {
        StringBuilder line = new StringBuilder();
        line.append("device_mobility_state=" + stats.deviceMobilityState);
        line.append(",num_times_entered_state=" + stats.numTimesEnteredState);
        line.append(",total_duration_ms=" + stats.totalDurationMs);
        line.append(",pno_duration_ms=" + stats.pnoDurationMs);
        pw.println(line.toString());
    }

    private void printUserApprovalSuggestionAppReaction(PrintWriter pw) {
        pw.println("mUserApprovalSuggestionAppUiUserReaction:");
        for (UserReaction event : mUserApprovalSuggestionAppUiReactionList) {
            pw.println(event);
        }
    }

    private void printUserApprovalCarrierReaction(PrintWriter pw) {
        pw.println("mUserApprovalCarrierUiUserReaction:");
        for (UserReaction event : mUserApprovalCarrierUiReactionList) {
            pw.println(event);
        }
    }

    /**
     * Update various counts of saved network types
     * @param networks List of WifiConfigurations representing all saved networks, must not be null
     */
    public void updateSavedNetworks(List<WifiConfiguration> networks) {
        synchronized (mLock) {
            mWifiLogProto.numSavedNetworks = networks.size();
            mWifiLogProto.numSavedNetworksWithMacRandomization = 0;
            mWifiLogProto.numOpenNetworks = 0;
            mWifiLogProto.numLegacyPersonalNetworks = 0;
            mWifiLogProto.numLegacyEnterpriseNetworks = 0;
            mWifiLogProto.numEnhancedOpenNetworks = 0;
            mWifiLogProto.numWpa3PersonalNetworks = 0;
            mWifiLogProto.numWpa3EnterpriseNetworks = 0;
            mWifiLogProto.numWapiPersonalNetworks = 0;
            mWifiLogProto.numWapiEnterpriseNetworks = 0;
            mWifiLogProto.numNetworksAddedByUser = 0;
            mWifiLogProto.numNetworksAddedByApps = 0;
            mWifiLogProto.numHiddenNetworks = 0;
            mWifiLogProto.numPasspointNetworks = 0;

            for (WifiConfiguration config : networks) {
                if (config.isSecurityType(WifiConfiguration.SECURITY_TYPE_OPEN)) {
                    mWifiLogProto.numOpenNetworks++;
                } else if (config.isSecurityType(WifiConfiguration.SECURITY_TYPE_OWE)) {
                    mWifiLogProto.numEnhancedOpenNetworks++;
                } else if (config.isSecurityType(WifiConfiguration.SECURITY_TYPE_WAPI_PSK)) {
                    mWifiLogProto.numWapiPersonalNetworks++;
                } else if (config.isEnterprise()) {
                    if (config.isSecurityType(
                            WifiConfiguration.SECURITY_TYPE_EAP_WPA3_ENTERPRISE_192_BIT)) {
                        mWifiLogProto.numWpa3EnterpriseNetworks++;
                    } else if (config.isSecurityType(
                            WifiConfiguration.SECURITY_TYPE_WAPI_CERT)) {
                        mWifiLogProto.numWapiEnterpriseNetworks++;
                    } else {
                        mWifiLogProto.numLegacyEnterpriseNetworks++;
                    }
                } else {
                    if (config.isSecurityType(WifiConfiguration.SECURITY_TYPE_PSK)) {
                        mWifiLogProto.numLegacyPersonalNetworks++;
                    }
                    else if (config.isSecurityType(WifiConfiguration.SECURITY_TYPE_SAE)) {
                        mWifiLogProto.numWpa3PersonalNetworks++;
                    }
                }
                mWifiLogProto.numNetworksAddedByApps++;
                if (config.hiddenSSID) {
                    mWifiLogProto.numHiddenNetworks++;
                }
                if (config.isPasspoint()) {
                    mWifiLogProto.numPasspointNetworks++;
                }
                if (config.macRandomizationSetting != WifiConfiguration.RANDOMIZATION_NONE) {
                    mWifiLogProto.numSavedNetworksWithMacRandomization++;
                }
            }
        }
    }

    /**
     * Update metrics for saved Passpoint profiles.
     *
     * @param numSavedProfiles The number of saved Passpoint profiles
     * @param numConnectedProfiles The number of saved Passpoint profiles that have ever resulted
     *                             in a successful network connection
     */
    public void updateSavedPasspointProfiles(int numSavedProfiles, int numConnectedProfiles) {
        synchronized (mLock) {
            mWifiLogProto.numPasspointProviders = numSavedProfiles;
            mWifiLogProto.numPasspointProvidersSuccessfullyConnected = numConnectedProfiles;
        }
    }

    /**
     * Update number of times for type of saved Passpoint profile.
     *
     * @param providers Passpoint providers installed on the device.
     */
    public void updateSavedPasspointProfilesInfo(
            Map<String, PasspointProvider> providers) {
        int passpointType;
        int eapType;
        PasspointConfiguration config;
        synchronized (mLock) {
            mInstalledPasspointProfileTypeForR1.clear();
            mInstalledPasspointProfileTypeForR2.clear();
            for (Map.Entry<String, PasspointProvider> entry : providers.entrySet()) {
                config = entry.getValue().getConfig();
                if (config.getCredential().getUserCredential() != null) {
                    eapType = EAPConstants.EAP_TTLS;
                } else if (config.getCredential().getCertCredential() != null) {
                    eapType = EAPConstants.EAP_TLS;
                } else if (config.getCredential().getSimCredential() != null) {
                    eapType = config.getCredential().getSimCredential().getEapType();
                } else {
                    eapType = -1;
                }
                switch (eapType) {
                    case EAPConstants.EAP_TLS:
                        passpointType = WifiMetricsProto.PasspointProfileTypeCount.TYPE_EAP_TLS;
                        break;
                    case EAPConstants.EAP_TTLS:
                        passpointType = WifiMetricsProto.PasspointProfileTypeCount.TYPE_EAP_TTLS;
                        break;
                    case EAPConstants.EAP_SIM:
                        passpointType = WifiMetricsProto.PasspointProfileTypeCount.TYPE_EAP_SIM;
                        break;
                    case EAPConstants.EAP_AKA:
                        passpointType = WifiMetricsProto.PasspointProfileTypeCount.TYPE_EAP_AKA;
                        break;
                    case EAPConstants.EAP_AKA_PRIME:
                        passpointType =
                                WifiMetricsProto.PasspointProfileTypeCount.TYPE_EAP_AKA_PRIME;
                        break;
                    default:
                        passpointType = WifiMetricsProto.PasspointProfileTypeCount.TYPE_UNKNOWN;

                }
                if (config.validateForR2()) {
                    mInstalledPasspointProfileTypeForR2.increment(passpointType);
                } else {
                    mInstalledPasspointProfileTypeForR1.increment(passpointType);
                }
            }
        }
    }

    /**
     * Increment initial partial scan count
     */
    public void incrementInitialPartialScanCount() {
        synchronized (mLock) {
            mInitPartialScanTotalCount++;
        }
    }

    /**
     * Report of initial partial scan
     * @param channelCount number of channels used in this scan
     * @param status true if scan resulted in a network connection attempt, false otherwise
     */
    public void reportInitialPartialScan(int channelCount, boolean status) {
        synchronized (mLock) {
            if (status) {
                mInitPartialScanSuccessCount++;
                mInitPartialScanSuccessHistogram.increment(channelCount);
            } else {
                mInitPartialScanFailureCount++;
                mInitPartialScanFailureHistogram.increment(channelCount);
            }
        }
    }

    /**
     * Put all metrics that were being tracked separately into mWifiLogProto
     */
    private void consolidateProto() {
        List<WifiMetricsProto.RssiPollCount> rssis = new ArrayList<>();
        synchronized (mLock) {
            mWifiLogProto.connectionEvent = mConnectionEventList
                    .stream()
                    // Exclude active un-ended connection events
                    .filter(connectionEvent ->
                            !mCurrentConnectionEventPerIface.containsValue(connectionEvent))
                    // unwrap WifiMetrics.ConnectionEvent to get WifiMetricsProto.ConnectionEvent
                    .map(connectionEvent -> connectionEvent.mConnectionEvent)
                    .toArray(WifiMetricsProto.ConnectionEvent[]::new);

            //Convert the SparseIntArray of scanReturnEntry integers into ScanReturnEntry proto list
            mWifiLogProto.scanReturnEntries =
                    new WifiMetricsProto.WifiLog.ScanReturnEntry[mScanReturnEntries.size()];
            for (int i = 0; i < mScanReturnEntries.size(); i++) {
                mWifiLogProto.scanReturnEntries[i] = new WifiMetricsProto.WifiLog.ScanReturnEntry();
                mWifiLogProto.scanReturnEntries[i].scanReturnCode = mScanReturnEntries.keyAt(i);
                mWifiLogProto.scanReturnEntries[i].scanResultsCount = mScanReturnEntries.valueAt(i);
            }

            // Convert the SparseIntArray of systemStateEntry into WifiSystemStateEntry proto list
            // This one is slightly more complex, as the Sparse are indexed with:
            //     key: wifiState * 2 + isScreenOn, value: wifiStateCount
            mWifiLogProto.wifiSystemStateEntries =
                    new WifiMetricsProto.WifiLog
                    .WifiSystemStateEntry[mWifiSystemStateEntries.size()];
            for (int i = 0; i < mWifiSystemStateEntries.size(); i++) {
                mWifiLogProto.wifiSystemStateEntries[i] =
                        new WifiMetricsProto.WifiLog.WifiSystemStateEntry();
                mWifiLogProto.wifiSystemStateEntries[i].wifiState =
                        mWifiSystemStateEntries.keyAt(i) / 2;
                mWifiLogProto.wifiSystemStateEntries[i].wifiStateCount =
                        mWifiSystemStateEntries.valueAt(i);
                mWifiLogProto.wifiSystemStateEntries[i].isScreenOn =
                        (mWifiSystemStateEntries.keyAt(i) % 2) > 0;
            }
            mWifiLogProto.recordDurationSec = (int) ((mClock.getElapsedSinceBootMillis() / 1000)
                    - mRecordStartTimeSec);

            /**
             * Convert the SparseIntArrays of RSSI poll rssi, counts, and frequency to the
             * proto's repeated IntKeyVal array.
             */
            for (Map.Entry<Integer, SparseIntArray> entry : mRssiPollCountsMap.entrySet()) {
                int frequency = entry.getKey();
                SparseIntArray histogram = entry.getValue();
                for (int i = 0; i < histogram.size(); i++) {
                    WifiMetricsProto.RssiPollCount keyVal = new WifiMetricsProto.RssiPollCount();
                    keyVal.rssi = histogram.keyAt(i);
                    keyVal.count = histogram.valueAt(i);
                    keyVal.frequency = frequency;
                    rssis.add(keyVal);
                }
            }
            mWifiLogProto.rssiPollRssiCount = rssis.toArray(mWifiLogProto.rssiPollRssiCount);

            /**
             * Convert the SparseIntArray of RSSI delta rssi's and counts to the proto's repeated
             * IntKeyVal array.
             */
            mWifiLogProto.rssiPollDeltaCount =
                    new WifiMetricsProto.RssiPollCount[mRssiDeltaCounts.size()];
            for (int i = 0; i < mRssiDeltaCounts.size(); i++) {
                mWifiLogProto.rssiPollDeltaCount[i] = new WifiMetricsProto.RssiPollCount();
                mWifiLogProto.rssiPollDeltaCount[i].rssi = mRssiDeltaCounts.keyAt(i);
                mWifiLogProto.rssiPollDeltaCount[i].count = mRssiDeltaCounts.valueAt(i);
            }

            /**
             * Add LinkSpeedCount objects from mLinkSpeedCounts to proto.
             */
            mWifiLogProto.linkSpeedCounts =
                    new WifiMetricsProto.LinkSpeedCount[mLinkSpeedCounts.size()];
            for (int i = 0; i < mLinkSpeedCounts.size(); i++) {
                mWifiLogProto.linkSpeedCounts[i] = mLinkSpeedCounts.valueAt(i);
            }

            /**
             * Convert the SparseIntArray of alert reasons and counts to the proto's repeated
             * IntKeyVal array.
             */
            mWifiLogProto.alertReasonCount =
                    new WifiMetricsProto.AlertReasonCount[mWifiAlertReasonCounts.size()];
            for (int i = 0; i < mWifiAlertReasonCounts.size(); i++) {
                mWifiLogProto.alertReasonCount[i] = new WifiMetricsProto.AlertReasonCount();
                mWifiLogProto.alertReasonCount[i].reason = mWifiAlertReasonCounts.keyAt(i);
                mWifiLogProto.alertReasonCount[i].count = mWifiAlertReasonCounts.valueAt(i);
            }

            /**
            *  Convert the SparseIntArray of Wifi Score and counts to proto's repeated
            * IntKeyVal array.
            */
            mWifiLogProto.wifiScoreCount =
                    new WifiMetricsProto.WifiScoreCount[mWifiScoreCounts.size()];
            for (int score = 0; score < mWifiScoreCounts.size(); score++) {
                mWifiLogProto.wifiScoreCount[score] = new WifiMetricsProto.WifiScoreCount();
                mWifiLogProto.wifiScoreCount[score].score = mWifiScoreCounts.keyAt(score);
                mWifiLogProto.wifiScoreCount[score].count = mWifiScoreCounts.valueAt(score);
            }

            /**
             * Convert the SparseIntArray of Wifi Usability Score and counts to proto's repeated
             * IntKeyVal array.
             */
            mWifiLogProto.wifiUsabilityScoreCount =
                new WifiMetricsProto.WifiUsabilityScoreCount[mWifiUsabilityScoreCounts.size()];
            for (int scoreIdx = 0; scoreIdx < mWifiUsabilityScoreCounts.size(); scoreIdx++) {
                mWifiLogProto.wifiUsabilityScoreCount[scoreIdx] =
                    new WifiMetricsProto.WifiUsabilityScoreCount();
                mWifiLogProto.wifiUsabilityScoreCount[scoreIdx].score =
                    mWifiUsabilityScoreCounts.keyAt(scoreIdx);
                mWifiLogProto.wifiUsabilityScoreCount[scoreIdx].count =
                    mWifiUsabilityScoreCounts.valueAt(scoreIdx);
            }

            /**
             * Convert the SparseIntArray of SoftAp Return codes and counts to proto's repeated
             * IntKeyVal array.
             */
            int codeCounts = mSoftApManagerReturnCodeCounts.size();
            mWifiLogProto.softApReturnCode = new WifiMetricsProto.SoftApReturnCodeCount[codeCounts];
            for (int sapCode = 0; sapCode < codeCounts; sapCode++) {
                mWifiLogProto.softApReturnCode[sapCode] =
                        new WifiMetricsProto.SoftApReturnCodeCount();
                mWifiLogProto.softApReturnCode[sapCode].startResult =
                        mSoftApManagerReturnCodeCounts.keyAt(sapCode);
                mWifiLogProto.softApReturnCode[sapCode].count =
                        mSoftApManagerReturnCodeCounts.valueAt(sapCode);
            }

            /**
             * Convert StaEventList to array of StaEvents
             */
            mWifiLogProto.staEventList = new StaEvent[mStaEventList.size()];
            for (int i = 0; i < mStaEventList.size(); i++) {
                mWifiLogProto.staEventList[i] = mStaEventList.get(i).staEvent;
            }
            mWifiLogProto.userActionEvents = new UserActionEvent[mUserActionEventList.size()];
            for (int i = 0; i < mUserActionEventList.size(); i++) {
                mWifiLogProto.userActionEvents[i] = mUserActionEventList.get(i).toProto();
            }
            mWifiLogProto.totalSsidsInScanHistogram =
                    makeNumConnectableNetworksBucketArray(mTotalSsidsInScanHistogram);
            mWifiLogProto.totalBssidsInScanHistogram =
                    makeNumConnectableNetworksBucketArray(mTotalBssidsInScanHistogram);
            mWifiLogProto.availableOpenSsidsInScanHistogram =
                    makeNumConnectableNetworksBucketArray(mAvailableOpenSsidsInScanHistogram);
            mWifiLogProto.availableOpenBssidsInScanHistogram =
                    makeNumConnectableNetworksBucketArray(mAvailableOpenBssidsInScanHistogram);
            mWifiLogProto.availableSavedSsidsInScanHistogram =
                    makeNumConnectableNetworksBucketArray(mAvailableSavedSsidsInScanHistogram);
            mWifiLogProto.availableSavedBssidsInScanHistogram =
                    makeNumConnectableNetworksBucketArray(mAvailableSavedBssidsInScanHistogram);
            mWifiLogProto.availableOpenOrSavedSsidsInScanHistogram =
                    makeNumConnectableNetworksBucketArray(
                    mAvailableOpenOrSavedSsidsInScanHistogram);
            mWifiLogProto.availableOpenOrSavedBssidsInScanHistogram =
                    makeNumConnectableNetworksBucketArray(
                    mAvailableOpenOrSavedBssidsInScanHistogram);
            mWifiLogProto.availableSavedPasspointProviderProfilesInScanHistogram =
                    makeNumConnectableNetworksBucketArray(
                    mAvailableSavedPasspointProviderProfilesInScanHistogram);
            mWifiLogProto.availableSavedPasspointProviderBssidsInScanHistogram =
                    makeNumConnectableNetworksBucketArray(
                    mAvailableSavedPasspointProviderBssidsInScanHistogram);
            mWifiLogProto.wifiAwareLog = mWifiAwareMetrics.consolidateProto();
            mWifiLogProto.wifiRttLog = mRttMetrics.consolidateProto();

            mWifiLogProto.pnoScanMetrics = mPnoScanMetrics;
            mWifiLogProto.wifiLinkLayerUsageStats = mWifiLinkLayerUsageStats;
            mWifiLogProto.wifiLinkLayerUsageStats.radioStats =
                    new WifiMetricsProto.RadioStats[mRadioStats.size()];
            for (int i = 0; i < mRadioStats.size(); i++) {
                mWifiLogProto.wifiLinkLayerUsageStats.radioStats[i] = mRadioStats.valueAt(i);
            }

            /**
             * Convert the SparseIntArray of "Connect to Network" notification types and counts to
             * proto's repeated IntKeyVal array.
             */
            ConnectToNetworkNotificationAndActionCount[] notificationCountArray =
                    new ConnectToNetworkNotificationAndActionCount[
                            mConnectToNetworkNotificationCount.size()];
            for (int i = 0; i < mConnectToNetworkNotificationCount.size(); i++) {
                ConnectToNetworkNotificationAndActionCount keyVal =
                        new ConnectToNetworkNotificationAndActionCount();
                keyVal.notification = mConnectToNetworkNotificationCount.keyAt(i);
                keyVal.recommender =
                        ConnectToNetworkNotificationAndActionCount.RECOMMENDER_OPEN;
                keyVal.count = mConnectToNetworkNotificationCount.valueAt(i);
                notificationCountArray[i] = keyVal;
            }
            mWifiLogProto.connectToNetworkNotificationCount = notificationCountArray;

            /**
             * Convert the SparseIntArray of "Connect to Network" notification types and counts to
             * proto's repeated IntKeyVal array.
             */
            ConnectToNetworkNotificationAndActionCount[] notificationActionCountArray =
                    new ConnectToNetworkNotificationAndActionCount[
                            mConnectToNetworkNotificationActionCount.size()];
            for (int i = 0; i < mConnectToNetworkNotificationActionCount.size(); i++) {
                ConnectToNetworkNotificationAndActionCount keyVal =
                        new ConnectToNetworkNotificationAndActionCount();
                int k = mConnectToNetworkNotificationActionCount.keyAt(i);
                keyVal.notification =  k / CONNECT_TO_NETWORK_NOTIFICATION_ACTION_KEY_MULTIPLIER;
                keyVal.action = k % CONNECT_TO_NETWORK_NOTIFICATION_ACTION_KEY_MULTIPLIER;
                keyVal.recommender =
                        ConnectToNetworkNotificationAndActionCount.RECOMMENDER_OPEN;
                keyVal.count = mConnectToNetworkNotificationActionCount.valueAt(i);
                notificationActionCountArray[i] = keyVal;
            }

            mWifiLogProto.installedPasspointProfileTypeForR1 =
                    convertPasspointProfilesToProto(mInstalledPasspointProfileTypeForR1);
            mWifiLogProto.installedPasspointProfileTypeForR2 =
                    convertPasspointProfilesToProto(mInstalledPasspointProfileTypeForR2);

            mWifiLogProto.connectToNetworkNotificationActionCount = notificationActionCountArray;

            mWifiLogProto.openNetworkRecommenderBlocklistSize =
                    mOpenNetworkRecommenderBlocklistSize;
            mWifiLogProto.isWifiNetworksAvailableNotificationOn =
                    mIsWifiNetworksAvailableNotificationOn;
            mWifiLogProto.numOpenNetworkRecommendationUpdates =
                    mNumOpenNetworkRecommendationUpdates;
            mWifiLogProto.numOpenNetworkConnectMessageFailedToSend =
                    mNumOpenNetworkConnectMessageFailedToSend;

            mWifiLogProto.observedHotspotR1ApsInScanHistogram =
                    makeNumConnectableNetworksBucketArray(mObservedHotspotR1ApInScanHistogram);
            mWifiLogProto.observedHotspotR2ApsInScanHistogram =
                    makeNumConnectableNetworksBucketArray(mObservedHotspotR2ApInScanHistogram);
            mWifiLogProto.observedHotspotR3ApsInScanHistogram =
                makeNumConnectableNetworksBucketArray(mObservedHotspotR3ApInScanHistogram);
            mWifiLogProto.observedHotspotR1EssInScanHistogram =
                    makeNumConnectableNetworksBucketArray(mObservedHotspotR1EssInScanHistogram);
            mWifiLogProto.observedHotspotR2EssInScanHistogram =
                    makeNumConnectableNetworksBucketArray(mObservedHotspotR2EssInScanHistogram);
            mWifiLogProto.observedHotspotR3EssInScanHistogram =
                    makeNumConnectableNetworksBucketArray(mObservedHotspotR3EssInScanHistogram);
            mWifiLogProto.observedHotspotR1ApsPerEssInScanHistogram =
                    makeNumConnectableNetworksBucketArray(
                            mObservedHotspotR1ApsPerEssInScanHistogram);
            mWifiLogProto.observedHotspotR2ApsPerEssInScanHistogram =
                    makeNumConnectableNetworksBucketArray(
                            mObservedHotspotR2ApsPerEssInScanHistogram);
            mWifiLogProto.observedHotspotR3ApsPerEssInScanHistogram =
                makeNumConnectableNetworksBucketArray(
                    mObservedHotspotR3ApsPerEssInScanHistogram);

            mWifiLogProto.observed80211McSupportingApsInScanHistogram =
                    makeNumConnectableNetworksBucketArray(mObserved80211mcApInScanHistogram);

            if (mSoftApEventListTethered.size() > 0) {
                mWifiLogProto.softApConnectedClientsEventsTethered =
                        mSoftApEventListTethered.toArray(
                        mWifiLogProto.softApConnectedClientsEventsTethered);
            }
            if (mSoftApEventListLocalOnly.size() > 0) {
                mWifiLogProto.softApConnectedClientsEventsLocalOnly =
                        mSoftApEventListLocalOnly.toArray(
                        mWifiLogProto.softApConnectedClientsEventsLocalOnly);
            }

            mWifiLogProto.wifiPowerStats = mWifiPowerMetrics.buildProto();
            mWifiLogProto.wifiRadioUsage = mWifiPowerMetrics.buildWifiRadioUsageProto();
            mWifiLogProto.wifiWakeStats = mWifiWakeMetrics.buildProto();
            mWifiLogProto.isMacRandomizationOn = mContext.getResources().getBoolean(
                    R.bool.config_wifi_connected_mac_randomization_supported);
            mExperimentValues.linkSpeedCountsLoggingEnabled = mContext.getResources().getBoolean(
                    R.bool.config_wifiLinkSpeedMetricsEnabled);
            mExperimentValues.wifiDataStallMinTxBad = mContext.getResources().getInteger(
                    R.integer.config_wifiDataStallMinTxBad);
            mExperimentValues.wifiDataStallMinTxSuccessWithoutRx =
                    mContext.getResources().getInteger(
                            R.integer.config_wifiDataStallMinTxSuccessWithoutRx);
            mWifiLogProto.experimentValues = mExperimentValues;
            mWifiLogProto.wifiIsUnusableEventList =
                    new WifiIsUnusableEvent[mWifiIsUnusableList.size()];
            for (int i = 0; i < mWifiIsUnusableList.size(); i++) {
                mWifiLogProto.wifiIsUnusableEventList[i] = mWifiIsUnusableList.get(i).event;
            }
            mWifiLogProto.hardwareRevision = SystemProperties.get("ro.boot.revision", "");

            // Postprocessing on WifiUsabilityStats to upload an equal number of LABEL_GOOD and
            // LABEL_BAD WifiUsabilityStats
            final int numUsabilityStats = Math.min(
                    Math.min(mWifiUsabilityStatsListBad.size(),
                            mWifiUsabilityStatsListGood.size()),
                    MAX_WIFI_USABILITY_STATS_PER_TYPE_TO_UPLOAD);
            LinkedList<WifiUsabilityStats> usabilityStatsGoodCopy =
                    new LinkedList<>(mWifiUsabilityStatsListGood);
            LinkedList<WifiUsabilityStats> usabilityStatsBadCopy =
                    new LinkedList<>(mWifiUsabilityStatsListBad);
            mWifiLogProto.wifiUsabilityStatsList = new WifiUsabilityStats[numUsabilityStats * 2];
            for (int i = 0; i < numUsabilityStats; i++) {
                mWifiLogProto.wifiUsabilityStatsList[2 * i] = usabilityStatsGoodCopy.remove(
                        mRand.nextInt(usabilityStatsGoodCopy.size()));
                mWifiLogProto.wifiUsabilityStatsList[2 * i + 1] = usabilityStatsBadCopy.remove(
                        mRand.nextInt(usabilityStatsBadCopy.size()));
            }
            mWifiLogProto.mobilityStatePnoStatsList =
                    new DeviceMobilityStatePnoScanStats[mMobilityStatePnoStatsMap.size()];
            for (int i = 0; i < mMobilityStatePnoStatsMap.size(); i++) {
                mWifiLogProto.mobilityStatePnoStatsList[i] = mMobilityStatePnoStatsMap.valueAt(i);
            }
            mWifiLogProto.wifiP2PStats = mWifiP2pMetrics.consolidateProto();
            mWifiLogProto.wifiDppLog = mDppMetrics.consolidateProto();
            mWifiLogProto.wifiConfigStoreIo = new WifiMetricsProto.WifiConfigStoreIO();
            mWifiLogProto.wifiConfigStoreIo.readDurations =
                    makeWifiConfigStoreIODurationBucketArray(mWifiConfigStoreReadDurationHistogram);
            mWifiLogProto.wifiConfigStoreIo.writeDurations =
                    makeWifiConfigStoreIODurationBucketArray(
                            mWifiConfigStoreWriteDurationHistogram);

            LinkProbeStats linkProbeStats = new LinkProbeStats();
            linkProbeStats.successRssiCounts = mLinkProbeSuccessRssiCounts.toProto();
            linkProbeStats.failureRssiCounts = mLinkProbeFailureRssiCounts.toProto();
            linkProbeStats.successLinkSpeedCounts = mLinkProbeSuccessLinkSpeedCounts.toProto();
            linkProbeStats.failureLinkSpeedCounts = mLinkProbeFailureLinkSpeedCounts.toProto();
            linkProbeStats.successSecondsSinceLastTxSuccessHistogram =
                    mLinkProbeSuccessSecondsSinceLastTxSuccessHistogram.toProto();
            linkProbeStats.failureSecondsSinceLastTxSuccessHistogram =
                    mLinkProbeFailureSecondsSinceLastTxSuccessHistogram.toProto();
            linkProbeStats.successElapsedTimeMsHistogram =
                    mLinkProbeSuccessElapsedTimeMsHistogram.toProto();
            linkProbeStats.failureReasonCounts = mLinkProbeFailureReasonCounts.toProto(
                    LinkProbeFailureReasonCount.class,
                    (reason, count) -> {
                        LinkProbeFailureReasonCount c = new LinkProbeFailureReasonCount();
                        c.failureReason = linkProbeFailureReasonToProto(reason);
                        c.count = count;
                        return c;
                    });
            linkProbeStats.experimentProbeCounts = mLinkProbeExperimentProbeCounts.toProto(
                    ExperimentProbeCounts.class,
                    (experimentId, probeCount) -> {
                        ExperimentProbeCounts c = new ExperimentProbeCounts();
                        c.experimentId = experimentId;
                        c.probeCount = probeCount;
                        return c;
                    });
            mWifiLogProto.linkProbeStats = linkProbeStats;

            mWifiLogProto.networkSelectionExperimentDecisionsList =
                    makeNetworkSelectionExperimentDecisionsList();

            mWifiNetworkRequestApiLog.networkMatchSizeHistogram =
                    mWifiNetworkRequestApiMatchSizeHistogram.toProto();
            mWifiNetworkRequestApiLog.connectionDurationSecOnPrimaryIfaceHistogram =
                    mWifiNetworkRequestApiConnectionDurationSecOnPrimaryIfaceHistogram.toProto();
            mWifiNetworkRequestApiLog.connectionDurationSecOnSecondaryIfaceHistogram =
                    mWifiNetworkRequestApiConnectionDurationSecOnSecondaryIfaceHistogram.toProto();
            mWifiNetworkRequestApiLog.concurrentConnectionDurationSecHistogram =
                    mWifiNetworkRequestApiConcurrentConnectionDurationSecHistogram.toProto();
            mWifiLogProto.wifiNetworkRequestApiLog = mWifiNetworkRequestApiLog;

            mWifiNetworkSuggestionApiLog.networkListSizeHistogram =
                    mWifiNetworkSuggestionApiListSizeHistogram.toProto();
            mWifiNetworkSuggestionApiLog.appCountPerType =
                    mWifiNetworkSuggestionApiAppTypeCounter.toProto(SuggestionAppCount.class,
                            (key, count) -> {
                                SuggestionAppCount entry = new SuggestionAppCount();
                                entry.appType = key;
                                entry.count = count;
                                return entry;
                            });
            mWifiNetworkSuggestionApiLog.numPriorityGroups =
                    mWifiNetworkSuggestionPriorityGroups.size();
            mWifiNetworkSuggestionApiLog.numSavedNetworksWithConfiguredSuggestion =
                    mWifiNetworkSuggestionCoexistSavedNetworks.size();
            mWifiLogProto.wifiNetworkSuggestionApiLog = mWifiNetworkSuggestionApiLog;

            UserReactionToApprovalUiEvent events = new UserReactionToApprovalUiEvent();
            events.userApprovalAppUiReaction = mUserApprovalSuggestionAppUiReactionList
                    .toArray(new UserReaction[0]);
            events.userApprovalCarrierUiReaction = mUserApprovalCarrierUiReactionList
                    .toArray(new UserReaction[0]);
            mWifiLogProto.userReactionToApprovalUiEvent = events;

            mWifiLockStats.highPerfLockAcqDurationSecHistogram =
                    mWifiLockHighPerfAcqDurationSecHistogram.toProto();

            mWifiLockStats.lowLatencyLockAcqDurationSecHistogram =
                    mWifiLockLowLatencyAcqDurationSecHistogram.toProto();

            mWifiLockStats.highPerfActiveSessionDurationSecHistogram =
                    mWifiLockHighPerfActiveSessionDurationSecHistogram.toProto();

            mWifiLockStats.lowLatencyActiveSessionDurationSecHistogram =
                    mWifiLockLowLatencyActiveSessionDurationSecHistogram.toProto();

            mWifiLogProto.wifiLockStats = mWifiLockStats;
            mWifiLogProto.wifiToggleStats = mWifiToggleStats;

            /**
             * Convert the SparseIntArray of passpoint provision failure code
             * and counts to the proto's repeated IntKeyVal array.
             */
            mWifiLogProto.passpointProvisionStats = new PasspointProvisionStats();
            mWifiLogProto.passpointProvisionStats.numProvisionSuccess = mNumProvisionSuccess;
            mWifiLogProto.passpointProvisionStats.provisionFailureCount =
                    mPasspointProvisionFailureCounts.toProto(ProvisionFailureCount.class,
                            (key, count) -> {
                                ProvisionFailureCount entry = new ProvisionFailureCount();
                                entry.failureCode = key;
                                entry.count = count;
                                return entry;
                            });
            // 'G' is due to that 1st Letter after _ becomes capital during protobuff compilation
            mWifiLogProto.txLinkSpeedCount2G = mTxLinkSpeedCount2g.toProto();
            mWifiLogProto.txLinkSpeedCount5GLow = mTxLinkSpeedCount5gLow.toProto();
            mWifiLogProto.txLinkSpeedCount5GMid = mTxLinkSpeedCount5gMid.toProto();
            mWifiLogProto.txLinkSpeedCount5GHigh = mTxLinkSpeedCount5gHigh.toProto();
            mWifiLogProto.txLinkSpeedCount6GLow = mTxLinkSpeedCount6gLow.toProto();
            mWifiLogProto.txLinkSpeedCount6GMid = mTxLinkSpeedCount6gMid.toProto();
            mWifiLogProto.txLinkSpeedCount6GHigh = mTxLinkSpeedCount6gHigh.toProto();

            mWifiLogProto.rxLinkSpeedCount2G = mRxLinkSpeedCount2g.toProto();
            mWifiLogProto.rxLinkSpeedCount5GLow = mRxLinkSpeedCount5gLow.toProto();
            mWifiLogProto.rxLinkSpeedCount5GMid = mRxLinkSpeedCount5gMid.toProto();
            mWifiLogProto.rxLinkSpeedCount5GHigh = mRxLinkSpeedCount5gHigh.toProto();
            mWifiLogProto.rxLinkSpeedCount6GLow = mRxLinkSpeedCount6gLow.toProto();
            mWifiLogProto.rxLinkSpeedCount6GMid = mRxLinkSpeedCount6gMid.toProto();
            mWifiLogProto.rxLinkSpeedCount6GHigh = mRxLinkSpeedCount6gHigh.toProto();

            HealthMonitorMetrics healthMonitorMetrics = mWifiHealthMonitor.buildProto();
            if (healthMonitorMetrics != null) {
                mWifiLogProto.healthMonitorMetrics = healthMonitorMetrics;
            }
            mWifiLogProto.bssidBlocklistStats = mBssidBlocklistStats.toProto();
            mWifiLogProto.connectionDurationStats = mConnectionDurationStats.toProto();
            mWifiLogProto.wifiOffMetrics = mWifiOffMetrics.toProto();
            mWifiLogProto.softApConfigLimitationMetrics = mSoftApConfigLimitationMetrics.toProto();
            mWifiLogProto.channelUtilizationHistogram =
                    new WifiMetricsProto.ChannelUtilizationHistogram();
            mWifiLogProto.channelUtilizationHistogram.utilization2G =
                    mChannelUtilizationHistogram2G.toProto();
            mWifiLogProto.channelUtilizationHistogram.utilizationAbove2G =
                    mChannelUtilizationHistogramAbove2G.toProto();
            mWifiLogProto.throughputMbpsHistogram =
                    new WifiMetricsProto.ThroughputMbpsHistogram();
            mWifiLogProto.throughputMbpsHistogram.tx2G =
                    mTxThroughputMbpsHistogram2G.toProto();
            mWifiLogProto.throughputMbpsHistogram.txAbove2G =
                    mTxThroughputMbpsHistogramAbove2G.toProto();
            mWifiLogProto.throughputMbpsHistogram.rx2G =
                    mRxThroughputMbpsHistogram2G.toProto();
            mWifiLogProto.throughputMbpsHistogram.rxAbove2G =
                    mRxThroughputMbpsHistogramAbove2G.toProto();
            mWifiLogProto.meteredNetworkStatsSaved = mMeteredNetworkStatsBuilder.toProto(false);
            mWifiLogProto.meteredNetworkStatsSuggestion = mMeteredNetworkStatsBuilder.toProto(true);

            InitPartialScanStats initialPartialScanStats = new InitPartialScanStats();
            initialPartialScanStats.numScans = mInitPartialScanTotalCount;
            initialPartialScanStats.numSuccessScans = mInitPartialScanSuccessCount;
            initialPartialScanStats.numFailureScans = mInitPartialScanFailureCount;
            initialPartialScanStats.successfulScanChannelCountHistogram =
                    mInitPartialScanSuccessHistogram.toProto();
            initialPartialScanStats.failedScanChannelCountHistogram =
                    mInitPartialScanFailureHistogram.toProto();
            mWifiLogProto.initPartialScanStats = initialPartialScanStats;
            mWifiLogProto.carrierWifiMetrics = mCarrierWifiMetrics.toProto();
            mWifiLogProto.mainlineModuleVersion = mWifiHealthMonitor.getWifiStackVersion();
            mWifiLogProto.firstConnectAfterBootStats = mFirstConnectAfterBootStats;
            mWifiLogProto.wifiToWifiSwitchStats = buildWifiToWifiSwitchStats();
            mWifiLogProto.bandwidthEstimatorStats = mWifiScoreCard.dumpBandwidthEstimatorStats();
            mWifiLogProto.passpointDeauthImminentScope = mPasspointDeauthImminentScope.toProto();
            mWifiLogProto.recentFailureAssociationStatus =
                    mRecentFailureAssociationStatus.toProto();
        }
    }

    private WifiToWifiSwitchStats buildWifiToWifiSwitchStats() {
        mWifiToWifiSwitchStats.makeBeforeBreakLingerDurationSeconds =
                mMakeBeforeBreakLingeringDurationSeconds.toProto();
        return mWifiToWifiSwitchStats;
    }

    private static int linkProbeFailureReasonToProto(int reason) {
        switch (reason) {
            case WifiNl80211Manager.SEND_MGMT_FRAME_ERROR_MCS_UNSUPPORTED:
                return LinkProbeStats.LINK_PROBE_FAILURE_REASON_MCS_UNSUPPORTED;
            case WifiNl80211Manager.SEND_MGMT_FRAME_ERROR_NO_ACK:
                return LinkProbeStats.LINK_PROBE_FAILURE_REASON_NO_ACK;
            case WifiNl80211Manager.SEND_MGMT_FRAME_ERROR_TIMEOUT:
                return LinkProbeStats.LINK_PROBE_FAILURE_REASON_TIMEOUT;
            case WifiNl80211Manager.SEND_MGMT_FRAME_ERROR_ALREADY_STARTED:
                return LinkProbeStats.LINK_PROBE_FAILURE_REASON_ALREADY_STARTED;
            default:
                return LinkProbeStats.LINK_PROBE_FAILURE_REASON_UNKNOWN;
        }
    }

    private NetworkSelectionExperimentDecisions[] makeNetworkSelectionExperimentDecisionsList() {
        NetworkSelectionExperimentDecisions[] results = new NetworkSelectionExperimentDecisions[
                mNetworkSelectionExperimentPairNumChoicesCounts.size()];
        int i = 0;
        for (Map.Entry<Pair<Integer, Integer>, NetworkSelectionExperimentResults> entry :
                mNetworkSelectionExperimentPairNumChoicesCounts.entrySet()) {
            NetworkSelectionExperimentDecisions result = new NetworkSelectionExperimentDecisions();
            result.experiment1Id = entry.getKey().first;
            result.experiment2Id = entry.getKey().second;
            result.sameSelectionNumChoicesCounter =
                    entry.getValue().sameSelectionNumChoicesCounter.toProto();
            result.differentSelectionNumChoicesCounter =
                    entry.getValue().differentSelectionNumChoicesCounter.toProto();
            results[i] = result;
            i++;
        }
        return results;
    }

    /** Sets the scoring experiment id to current value */
    private void consolidateScoringParams() {
        synchronized (mLock) {
            if (mScoringParams != null) {
                int experimentIdentifier = mScoringParams.getExperimentIdentifier();
                if (experimentIdentifier == 0) {
                    mWifiLogProto.scoreExperimentId = "";
                } else {
                    mWifiLogProto.scoreExperimentId = "x" + experimentIdentifier;
                }
            }
        }
    }

    private WifiMetricsProto.NumConnectableNetworksBucket[] makeNumConnectableNetworksBucketArray(
            SparseIntArray sia) {
        WifiMetricsProto.NumConnectableNetworksBucket[] array =
                new WifiMetricsProto.NumConnectableNetworksBucket[sia.size()];
        for (int i = 0; i < sia.size(); i++) {
            WifiMetricsProto.NumConnectableNetworksBucket keyVal =
                    new WifiMetricsProto.NumConnectableNetworksBucket();
            keyVal.numConnectableNetworks = sia.keyAt(i);
            keyVal.count = sia.valueAt(i);
            array[i] = keyVal;
        }
        return array;
    }

    private WifiMetricsProto.WifiConfigStoreIO.DurationBucket[]
            makeWifiConfigStoreIODurationBucketArray(SparseIntArray sia) {
        MetricsUtils.GenericBucket[] genericBuckets =
                MetricsUtils.linearHistogramToGenericBuckets(sia,
                        WIFI_CONFIG_STORE_IO_DURATION_BUCKET_RANGES_MS);
        WifiMetricsProto.WifiConfigStoreIO.DurationBucket[] array =
                new WifiMetricsProto.WifiConfigStoreIO.DurationBucket[genericBuckets.length];
        try {
            for (int i = 0; i < genericBuckets.length; i++) {
                array[i] = new WifiMetricsProto.WifiConfigStoreIO.DurationBucket();
                array[i].rangeStartMs = toIntExact(genericBuckets[i].start);
                array[i].rangeEndMs = toIntExact(genericBuckets[i].end);
                array[i].count = genericBuckets[i].count;
            }
        } catch (ArithmeticException e) {
            // Return empty array on any overflow errors.
            array = new WifiMetricsProto.WifiConfigStoreIO.DurationBucket[0];
        }
        return array;
    }

    /**
     * Clear all WifiMetrics, except for currentConnectionEvent and Open Network Notification
     * feature enabled state, blocklist size.
     */
    private void clear() {
        synchronized (mLock) {
            mConnectionEventList.clear();
            // Add in-progress events back
            mConnectionEventList.addAll(mCurrentConnectionEventPerIface.values());

            mScanReturnEntries.clear();
            mWifiSystemStateEntries.clear();
            mRecordStartTimeSec = mClock.getElapsedSinceBootMillis() / 1000;
            mRssiPollCountsMap.clear();
            mRssiDeltaCounts.clear();
            mLinkSpeedCounts.clear();
            mTxLinkSpeedCount2g.clear();
            mTxLinkSpeedCount5gLow.clear();
            mTxLinkSpeedCount5gMid.clear();
            mTxLinkSpeedCount5gHigh.clear();
            mTxLinkSpeedCount6gLow.clear();
            mTxLinkSpeedCount6gMid.clear();
            mTxLinkSpeedCount6gHigh.clear();
            mRxLinkSpeedCount2g.clear();
            mRxLinkSpeedCount5gLow.clear();
            mRxLinkSpeedCount5gMid.clear();
            mRxLinkSpeedCount5gHigh.clear();
            mRxLinkSpeedCount6gLow.clear();
            mRxLinkSpeedCount6gMid.clear();
            mRxLinkSpeedCount6gHigh.clear();
            mWifiAlertReasonCounts.clear();
            mMakeBeforeBreakLingeringDurationSeconds.clear();
            mWifiScoreCounts.clear();
            mWifiUsabilityScoreCounts.clear();
            mWifiLogProto.clear();
            mScanResultRssiTimestampMillis = -1;
            mSoftApManagerReturnCodeCounts.clear();
            mStaEventList.clear();
            mUserActionEventList.clear();
            mWifiAwareMetrics.clear();
            mRttMetrics.clear();
            mTotalSsidsInScanHistogram.clear();
            mTotalBssidsInScanHistogram.clear();
            mAvailableOpenSsidsInScanHistogram.clear();
            mAvailableOpenBssidsInScanHistogram.clear();
            mAvailableSavedSsidsInScanHistogram.clear();
            mAvailableSavedBssidsInScanHistogram.clear();
            mAvailableOpenOrSavedSsidsInScanHistogram.clear();
            mAvailableOpenOrSavedBssidsInScanHistogram.clear();
            mAvailableSavedPasspointProviderProfilesInScanHistogram.clear();
            mAvailableSavedPasspointProviderBssidsInScanHistogram.clear();
            mPnoScanMetrics.clear();
            mWifiLinkLayerUsageStats.clear();
            mRadioStats.clear();
            mConnectToNetworkNotificationCount.clear();
            mConnectToNetworkNotificationActionCount.clear();
            mNumOpenNetworkRecommendationUpdates = 0;
            mNumOpenNetworkConnectMessageFailedToSend = 0;
            mObservedHotspotR1ApInScanHistogram.clear();
            mObservedHotspotR2ApInScanHistogram.clear();
            mObservedHotspotR3ApInScanHistogram.clear();
            mObservedHotspotR1EssInScanHistogram.clear();
            mObservedHotspotR2EssInScanHistogram.clear();
            mObservedHotspotR3EssInScanHistogram.clear();
            mObservedHotspotR1ApsPerEssInScanHistogram.clear();
            mObservedHotspotR2ApsPerEssInScanHistogram.clear();
            mObservedHotspotR3ApsPerEssInScanHistogram.clear();
            mSoftApEventListTethered.clear();
            mSoftApEventListLocalOnly.clear();
            mWifiWakeMetrics.clear();
            mObserved80211mcApInScanHistogram.clear();
            mWifiIsUnusableList.clear();
            mInstalledPasspointProfileTypeForR1.clear();
            mInstalledPasspointProfileTypeForR2.clear();
            mWifiUsabilityStatsListGood.clear();
            mWifiUsabilityStatsListBad.clear();
            mWifiUsabilityStatsEntriesList.clear();
            mMobilityStatePnoStatsMap.clear();
            mWifiP2pMetrics.clear();
            mDppMetrics.clear();
            mWifiUsabilityStatsCounter = 0;
            mLastBssid = null;
            mLastFrequency = -1;
            mSeqNumInsideFramework = 0;
            mLastWifiUsabilityScore = -1;
            mLastWifiUsabilityScoreNoReset = -1;
            mLastPredictionHorizonSec = -1;
            mLastPredictionHorizonSecNoReset = -1;
            mSeqNumToFramework = -1;
            mProbeStatusSinceLastUpdate =
                    android.net.wifi.WifiUsabilityStatsEntry.PROBE_STATUS_NO_PROBE;
            mProbeElapsedTimeSinceLastUpdateMs = -1;
            mProbeMcsRateSinceLastUpdate = -1;
            mScoreBreachLowTimeMillis = -1;
            mMeteredNetworkStatsBuilder.clear();
            mWifiConfigStoreReadDurationHistogram.clear();
            mWifiConfigStoreWriteDurationHistogram.clear();
            mLinkProbeSuccessRssiCounts.clear();
            mLinkProbeFailureRssiCounts.clear();
            mLinkProbeSuccessLinkSpeedCounts.clear();
            mLinkProbeFailureLinkSpeedCounts.clear();
            mLinkProbeSuccessSecondsSinceLastTxSuccessHistogram.clear();
            mLinkProbeFailureSecondsSinceLastTxSuccessHistogram.clear();
            mLinkProbeSuccessElapsedTimeMsHistogram.clear();
            mLinkProbeFailureReasonCounts.clear();
            mLinkProbeExperimentProbeCounts.clear();
            mLinkProbeStaEventCount = 0;
            mNetworkSelectionExperimentPairNumChoicesCounts.clear();
            mWifiNetworkSuggestionApiLog.clear();
            mWifiNetworkRequestApiMatchSizeHistogram.clear();
            mWifiNetworkRequestApiConnectionDurationSecOnPrimaryIfaceHistogram.clear();
            mWifiNetworkRequestApiConnectionDurationSecOnSecondaryIfaceHistogram.clear();
            mWifiNetworkRequestApiConcurrentConnectionDurationSecHistogram.clear();
            mWifiNetworkSuggestionApiListSizeHistogram.clear();
            mWifiNetworkSuggestionApiAppTypeCounter.clear();
            mUserApprovalSuggestionAppUiReactionList.clear();
            mUserApprovalCarrierUiReactionList.clear();
            mWifiLockHighPerfAcqDurationSecHistogram.clear();
            mWifiLockLowLatencyAcqDurationSecHistogram.clear();
            mWifiLockHighPerfActiveSessionDurationSecHistogram.clear();
            mWifiLockLowLatencyActiveSessionDurationSecHistogram.clear();
            mWifiLockStats.clear();
            mWifiToggleStats.clear();
            mChannelUtilizationHistogram2G.clear();
            mChannelUtilizationHistogramAbove2G.clear();
            mTxThroughputMbpsHistogram2G.clear();
            mRxThroughputMbpsHistogram2G.clear();
            mTxThroughputMbpsHistogramAbove2G.clear();
            mRxThroughputMbpsHistogramAbove2G.clear();
            mPasspointProvisionFailureCounts.clear();
            mNumProvisionSuccess = 0;
            mBssidBlocklistStats = new BssidBlocklistStats();
            mConnectionDurationStats.clear();
            mWifiOffMetrics.clear();
            mSoftApConfigLimitationMetrics.clear();
            //Initial partial scan metrics
            mInitPartialScanTotalCount = 0;
            mInitPartialScanSuccessCount = 0;
            mInitPartialScanFailureCount = 0;
            mInitPartialScanSuccessHistogram.clear();
            mInitPartialScanFailureHistogram.clear();
            mCarrierWifiMetrics.clear();
            mFirstConnectAfterBootStats = null;
            mWifiToWifiSwitchStats.clear();
            mPasspointDeauthImminentScope.clear();
            mRecentFailureAssociationStatus.clear();
            mWifiNetworkSuggestionPriorityGroups.clear();
            mWifiNetworkSuggestionCoexistSavedNetworks.clear();
        }
    }

    /**
     *  Set screen state (On/Off)
     */
    private void setScreenState(boolean screenOn) {
        synchronized (mLock) {
            mScreenOn = screenOn;
        }
    }

    private boolean isPrimary(String ifaceName) {
        return mIfaceToRoleMap.get(ifaceName) == ActiveModeManager.ROLE_CLIENT_PRIMARY;
    }

    /**
     *  Set wifi state (WIFI_UNKNOWN, WIFI_DISABLED, WIFI_DISCONNECTED, WIFI_ASSOCIATED)
     */
    public void setWifiState(String ifaceName, int wifiState) {
        synchronized (mLock) {
            mWifiState = wifiState;
            // set wifi priority over setting when any STA gets connected.
            if (wifiState == WifiMetricsProto.WifiLog.WIFI_ASSOCIATED) {
                mWifiWins = true;
                mWifiWinsUsabilityScore = true;
            }
            if (isPrimary(ifaceName) && (wifiState == WifiMetricsProto.WifiLog.WIFI_DISCONNECTED
                    || wifiState == WifiMetricsProto.WifiLog.WIFI_DISABLED)) {
                mWifiStatusBuilder = new WifiStatusBuilder();
            }
        }
    }

    /**
     * Message handler for interesting WifiMonitor messages. Generates StaEvents
     */
    private void processMessage(Message msg) {
        String ifaceName = msg.getData().getString(WifiMonitor.KEY_IFACE);

        StaEvent event = new StaEvent();
        boolean logEvent = true;
        switch (msg.what) {
            case WifiMonitor.ASSOCIATION_REJECTION_EVENT:
                event.type = StaEvent.TYPE_ASSOCIATION_REJECTION_EVENT;
                AssocRejectEventInfo assocRejectEventInfo = (AssocRejectEventInfo) msg.obj;
                event.associationTimedOut = assocRejectEventInfo.timedOut;
                event.status = assocRejectEventInfo.statusCode;
                break;
            case WifiMonitor.AUTHENTICATION_FAILURE_EVENT:
                event.type = StaEvent.TYPE_AUTHENTICATION_FAILURE_EVENT;
                AuthenticationFailureEventInfo authenticationFailureEventInfo =
                        (AuthenticationFailureEventInfo) msg.obj;
                switch (authenticationFailureEventInfo.reasonCode) {
                    case WifiManager.ERROR_AUTH_FAILURE_NONE:
                        event.authFailureReason = StaEvent.AUTH_FAILURE_NONE;
                        break;
                    case WifiManager.ERROR_AUTH_FAILURE_TIMEOUT:
                        event.authFailureReason = StaEvent.AUTH_FAILURE_TIMEOUT;
                        break;
                    case WifiManager.ERROR_AUTH_FAILURE_WRONG_PSWD:
                        event.authFailureReason = StaEvent.AUTH_FAILURE_WRONG_PSWD;
                        break;
                    case WifiManager.ERROR_AUTH_FAILURE_EAP_FAILURE:
                        event.authFailureReason = StaEvent.AUTH_FAILURE_EAP_FAILURE;
                        break;
                    default:
                        break;
                }
                break;
            case WifiMonitor.NETWORK_CONNECTION_EVENT:
                event.type = StaEvent.TYPE_NETWORK_CONNECTION_EVENT;
                break;
            case WifiMonitor.NETWORK_DISCONNECTION_EVENT:
                event.type = StaEvent.TYPE_NETWORK_DISCONNECTION_EVENT;
                DisconnectEventInfo disconnectEventInfo = (DisconnectEventInfo) msg.obj;
                event.reason = disconnectEventInfo.reasonCode;
                event.localGen = disconnectEventInfo.locallyGenerated;
                break;
            case WifiMonitor.SUPPLICANT_STATE_CHANGE_EVENT:
                logEvent = false;
                StateChangeResult stateChangeResult = (StateChangeResult) msg.obj;
                mSupplicantStateChangeBitmask |= supplicantStateToBit(stateChangeResult.state);
                break;
            case WifiMonitor.ASSOCIATED_BSSID_EVENT:
                event.type = StaEvent.TYPE_CMD_ASSOCIATED_BSSID;
                break;
            case WifiMonitor.TARGET_BSSID_EVENT:
                event.type = StaEvent.TYPE_CMD_TARGET_BSSID;
                break;
            default:
                return;
        }
        if (logEvent) {
            addStaEvent(ifaceName, event);
        }
    }
    /**
     * Log a StaEvent from ClientModeImpl. The StaEvent must not be one of the supplicant
     * generated event types, which are logged through 'sendMessage'
     * @param type StaEvent.EventType describing the event
     */
    public void logStaEvent(String ifaceName, int type) {
        logStaEvent(ifaceName, type, StaEvent.DISCONNECT_UNKNOWN, null);
    }
    /**
     * Log a StaEvent from ClientModeImpl. The StaEvent must not be one of the supplicant
     * generated event types, which are logged through 'sendMessage'
     * @param type StaEvent.EventType describing the event
     * @param config WifiConfiguration for a framework initiated connection attempt
     */
    public void logStaEvent(String ifaceName, int type, WifiConfiguration config) {
        logStaEvent(ifaceName, type, StaEvent.DISCONNECT_UNKNOWN, config);
    }
    /**
     * Log a StaEvent from ClientModeImpl. The StaEvent must not be one of the supplicant
     * generated event types, which are logged through 'sendMessage'
     * @param type StaEvent.EventType describing the event
     * @param frameworkDisconnectReason StaEvent.FrameworkDisconnectReason explaining why framework
     *                                  initiated a FRAMEWORK_DISCONNECT
     */
    public void logStaEvent(String ifaceName, int type, int frameworkDisconnectReason) {
        logStaEvent(ifaceName, type, frameworkDisconnectReason, null);
    }
    /**
     * Log a StaEvent from ClientModeImpl. The StaEvent must not be one of the supplicant
     * generated event types, which are logged through 'sendMessage'
     * @param type StaEvent.EventType describing the event
     * @param frameworkDisconnectReason StaEvent.FrameworkDisconnectReason explaining why framework
     *                                  initiated a FRAMEWORK_DISCONNECT
     * @param config WifiConfiguration for a framework initiated connection attempt
     */
    public void logStaEvent(String ifaceName, int type, int frameworkDisconnectReason,
            WifiConfiguration config) {
        switch (type) {
            case StaEvent.TYPE_CMD_START_ROAM:
                ConnectionEvent currentConnectionEvent = mCurrentConnectionEventPerIface.get(
                        ifaceName);
                if (currentConnectionEvent != null) {
                    currentConnectionEvent.mRouterFingerPrint.mIsFrameworkInitiatedRoaming = true;
                }
                break;
            case StaEvent.TYPE_CMD_IP_CONFIGURATION_SUCCESSFUL:
            case StaEvent.TYPE_CMD_IP_CONFIGURATION_LOST:
            case StaEvent.TYPE_CMD_IP_REACHABILITY_LOST:
            case StaEvent.TYPE_CMD_START_CONNECT:
            case StaEvent.TYPE_CONNECT_NETWORK:
                break;
            case StaEvent.TYPE_NETWORK_AGENT_VALID_NETWORK:
                mWifiStatusBuilder.setValidated(true);
                break;
            case StaEvent.TYPE_FRAMEWORK_DISCONNECT:
            case StaEvent.TYPE_SCORE_BREACH:
            case StaEvent.TYPE_MAC_CHANGE:
            case StaEvent.TYPE_WIFI_ENABLED:
            case StaEvent.TYPE_WIFI_DISABLED:
            case StaEvent.TYPE_WIFI_USABILITY_SCORE_BREACH:
                break;
            default:
                Log.e(TAG, "Unknown StaEvent:" + type);
                return;
        }
        StaEvent event = new StaEvent();
        event.type = type;
        if (frameworkDisconnectReason != StaEvent.DISCONNECT_UNKNOWN) {
            event.frameworkDisconnectReason = frameworkDisconnectReason;
        }
        event.configInfo = createConfigInfo(config);
        addStaEvent(ifaceName, event);
    }

    private void addStaEvent(String ifaceName, StaEvent staEvent) {
        // Nano proto runtime will throw a NPE during serialization if interfaceName is null
        if (ifaceName == null) {
            // Check if any ConcreteClientModeManager's role is switching to ROLE_CLIENT_PRIMARY
            ConcreteClientModeManager targetConcreteClientModeManager =
                    mActiveModeWarden.getClientModeManagerTransitioningIntoRole(
                            ROLE_CLIENT_PRIMARY);
            if (targetConcreteClientModeManager == null) {
                Log.wtf(TAG, "Null StaEvent.ifaceName: " + staEventToString(staEvent));
            }
            return;
        }
        staEvent.interfaceName = ifaceName;
        staEvent.interfaceRole = convertIfaceToEnum(ifaceName);
        staEvent.startTimeMillis = mClock.getElapsedSinceBootMillis();
        staEvent.lastRssi = mLastPollRssi;
        staEvent.lastFreq = mLastPollFreq;
        staEvent.lastLinkSpeed = mLastPollLinkSpeed;
        staEvent.supplicantStateChangesBitmask = mSupplicantStateChangeBitmask;
        staEvent.lastScore = mLastScore;
        staEvent.lastWifiUsabilityScore = mLastWifiUsabilityScore;
        staEvent.lastPredictionHorizonSec = mLastPredictionHorizonSec;
        staEvent.mobileTxBytes = mFacade.getMobileTxBytes();
        staEvent.mobileRxBytes = mFacade.getMobileRxBytes();
        staEvent.totalTxBytes = mFacade.getTotalTxBytes();
        staEvent.totalRxBytes = mFacade.getTotalRxBytes();
        staEvent.screenOn = mScreenOn;
        if (mWifiDataStall != null) {
            staEvent.isCellularDataAvailable = mWifiDataStall.isCellularDataAvailable();
        }
        staEvent.isAdaptiveConnectivityEnabled = mAdaptiveConnectivityEnabled;
        mSupplicantStateChangeBitmask = 0;
        mLastPollRssi = -127;
        mLastPollFreq = -1;
        mLastPollLinkSpeed = -1;
        mLastPollRxLinkSpeed = -1;
        mLastScore = -1;
        mLastWifiUsabilityScore = -1;
        mLastPredictionHorizonSec = -1;
        synchronized (mLock) {
            mStaEventList.add(new StaEventWithTime(staEvent, mClock.getWallClockMillis()));
            // Prune StaEventList if it gets too long
            if (mStaEventList.size() > MAX_STA_EVENTS) mStaEventList.remove();
        }
    }

    private ConfigInfo createConfigInfo(WifiConfiguration config) {
        if (config == null) return null;
        ConfigInfo info = new ConfigInfo();
        info.allowedKeyManagement = bitSetToInt(config.allowedKeyManagement);
        info.allowedProtocols = bitSetToInt(config.allowedProtocols);
        info.allowedAuthAlgorithms = bitSetToInt(config.allowedAuthAlgorithms);
        info.allowedPairwiseCiphers = bitSetToInt(config.allowedPairwiseCiphers);
        info.allowedGroupCiphers = bitSetToInt(config.allowedGroupCiphers);
        info.hiddenSsid = config.hiddenSSID;
        info.isPasspoint = config.isPasspoint();
        info.isEphemeral = config.isEphemeral();
        info.hasEverConnected = config.getNetworkSelectionStatus().hasEverConnected();
        ScanResult candidate = config.getNetworkSelectionStatus().getCandidate();
        if (candidate != null) {
            info.scanRssi = candidate.level;
            info.scanFreq = candidate.frequency;
        }
        return info;
    }

    private static final int[] WIFI_MONITOR_EVENTS = {
            WifiMonitor.ASSOCIATION_REJECTION_EVENT,
            WifiMonitor.AUTHENTICATION_FAILURE_EVENT,
            WifiMonitor.NETWORK_CONNECTION_EVENT,
            WifiMonitor.NETWORK_DISCONNECTION_EVENT,
            WifiMonitor.SUPPLICANT_STATE_CHANGE_EVENT,
            WifiMonitor.ASSOCIATED_BSSID_EVENT,
            WifiMonitor.TARGET_BSSID_EVENT,
    };

    public void registerForWifiMonitorEvents(String ifaceName) {
        for (int event : WIFI_MONITOR_EVENTS) {
            mWifiMonitor.registerHandler(ifaceName, event, mHandler);
        }
    }

    public void deregisterForWifiMonitorEvents(String ifaceName) {
        for (int event : WIFI_MONITOR_EVENTS) {
            mWifiMonitor.deregisterHandler(ifaceName, event, mHandler);
        }
    }

    public WifiAwareMetrics getWifiAwareMetrics() {
        return mWifiAwareMetrics;
    }

    public WifiWakeMetrics getWakeupMetrics() {
        return mWifiWakeMetrics;
    }

    public RttMetrics getRttMetrics() {
        return mRttMetrics;
    }

    // Rather than generate a StaEvent for each SUPPLICANT_STATE_CHANGE, cache these in a bitmask
    // and attach it to the next event which is generated.
    private int mSupplicantStateChangeBitmask = 0;

    /**
     * Converts a SupplicantState value to a single bit, with position defined by
     * {@code StaEvent.SupplicantState}
     */
    public static int supplicantStateToBit(SupplicantState state) {
        switch(state) {
            case DISCONNECTED:
                return 1 << StaEvent.STATE_DISCONNECTED;
            case INTERFACE_DISABLED:
                return 1 << StaEvent.STATE_INTERFACE_DISABLED;
            case INACTIVE:
                return 1 << StaEvent.STATE_INACTIVE;
            case SCANNING:
                return 1 << StaEvent.STATE_SCANNING;
            case AUTHENTICATING:
                return 1 << StaEvent.STATE_AUTHENTICATING;
            case ASSOCIATING:
                return 1 << StaEvent.STATE_ASSOCIATING;
            case ASSOCIATED:
                return 1 << StaEvent.STATE_ASSOCIATED;
            case FOUR_WAY_HANDSHAKE:
                return 1 << StaEvent.STATE_FOUR_WAY_HANDSHAKE;
            case GROUP_HANDSHAKE:
                return 1 << StaEvent.STATE_GROUP_HANDSHAKE;
            case COMPLETED:
                return 1 << StaEvent.STATE_COMPLETED;
            case DORMANT:
                return 1 << StaEvent.STATE_DORMANT;
            case UNINITIALIZED:
                return 1 << StaEvent.STATE_UNINITIALIZED;
            case INVALID:
                return 1 << StaEvent.STATE_INVALID;
            default:
                Log.wtf(TAG, "Got unknown supplicant state: " + state.ordinal());
                return 0;
        }
    }

    private static String supplicantStateChangesBitmaskToString(int mask) {
        StringBuilder sb = new StringBuilder();
        sb.append("supplicantStateChangeEvents: {");
        if ((mask & (1 << StaEvent.STATE_DISCONNECTED)) > 0) sb.append(" DISCONNECTED");
        if ((mask & (1 << StaEvent.STATE_INTERFACE_DISABLED)) > 0) sb.append(" INTERFACE_DISABLED");
        if ((mask & (1 << StaEvent.STATE_INACTIVE)) > 0) sb.append(" INACTIVE");
        if ((mask & (1 << StaEvent.STATE_SCANNING)) > 0) sb.append(" SCANNING");
        if ((mask & (1 << StaEvent.STATE_AUTHENTICATING)) > 0) sb.append(" AUTHENTICATING");
        if ((mask & (1 << StaEvent.STATE_ASSOCIATING)) > 0) sb.append(" ASSOCIATING");
        if ((mask & (1 << StaEvent.STATE_ASSOCIATED)) > 0) sb.append(" ASSOCIATED");
        if ((mask & (1 << StaEvent.STATE_FOUR_WAY_HANDSHAKE)) > 0) sb.append(" FOUR_WAY_HANDSHAKE");
        if ((mask & (1 << StaEvent.STATE_GROUP_HANDSHAKE)) > 0) sb.append(" GROUP_HANDSHAKE");
        if ((mask & (1 << StaEvent.STATE_COMPLETED)) > 0) sb.append(" COMPLETED");
        if ((mask & (1 << StaEvent.STATE_DORMANT)) > 0) sb.append(" DORMANT");
        if ((mask & (1 << StaEvent.STATE_UNINITIALIZED)) > 0) sb.append(" UNINITIALIZED");
        if ((mask & (1 << StaEvent.STATE_INVALID)) > 0) sb.append(" INVALID");
        sb.append(" }");
        return sb.toString();
    }

    /**
     * Returns a human readable string from a Sta Event. Only adds information relevant to the event
     * type.
     */
    public static String staEventToString(StaEvent event) {
        if (event == null) return "<NULL>";
        StringBuilder sb = new StringBuilder();
        switch (event.type) {
            case StaEvent.TYPE_ASSOCIATION_REJECTION_EVENT:
                sb.append("ASSOCIATION_REJECTION_EVENT")
                        .append(" timedOut=").append(event.associationTimedOut)
                        .append(" status=").append(event.status).append(":")
                        .append(StaIfaceStatusCode.toString(event.status));
                break;
            case StaEvent.TYPE_AUTHENTICATION_FAILURE_EVENT:
                sb.append("AUTHENTICATION_FAILURE_EVENT reason=").append(event.authFailureReason)
                        .append(":").append(authFailureReasonToString(event.authFailureReason));
                break;
            case StaEvent.TYPE_NETWORK_CONNECTION_EVENT:
                sb.append("NETWORK_CONNECTION_EVENT");
                break;
            case StaEvent.TYPE_NETWORK_DISCONNECTION_EVENT:
                sb.append("NETWORK_DISCONNECTION_EVENT")
                        .append(" local_gen=").append(event.localGen)
                        .append(" reason=").append(event.reason).append(":")
                        .append(StaIfaceReasonCode.toString(
                                (event.reason >= 0 ? event.reason : -1 * event.reason)));
                break;
            case StaEvent.TYPE_CMD_ASSOCIATED_BSSID:
                sb.append("CMD_ASSOCIATED_BSSID");
                break;
            case StaEvent.TYPE_CMD_IP_CONFIGURATION_SUCCESSFUL:
                sb.append("CMD_IP_CONFIGURATION_SUCCESSFUL");
                break;
            case StaEvent.TYPE_CMD_IP_CONFIGURATION_LOST:
                sb.append("CMD_IP_CONFIGURATION_LOST");
                break;
            case StaEvent.TYPE_CMD_IP_REACHABILITY_LOST:
                sb.append("CMD_IP_REACHABILITY_LOST");
                break;
            case StaEvent.TYPE_CMD_TARGET_BSSID:
                sb.append("CMD_TARGET_BSSID");
                break;
            case StaEvent.TYPE_CMD_START_CONNECT:
                sb.append("CMD_START_CONNECT");
                break;
            case StaEvent.TYPE_CMD_START_ROAM:
                sb.append("CMD_START_ROAM");
                break;
            case StaEvent.TYPE_CONNECT_NETWORK:
                sb.append("CONNECT_NETWORK");
                break;
            case StaEvent.TYPE_NETWORK_AGENT_VALID_NETWORK:
                sb.append("NETWORK_AGENT_VALID_NETWORK");
                break;
            case StaEvent.TYPE_FRAMEWORK_DISCONNECT:
                sb.append("FRAMEWORK_DISCONNECT")
                        .append(" reason=")
                        .append(frameworkDisconnectReasonToString(event.frameworkDisconnectReason));
                break;
            case StaEvent.TYPE_SCORE_BREACH:
                sb.append("SCORE_BREACH");
                break;
            case StaEvent.TYPE_MAC_CHANGE:
                sb.append("MAC_CHANGE");
                break;
            case StaEvent.TYPE_WIFI_ENABLED:
                sb.append("WIFI_ENABLED");
                break;
            case StaEvent.TYPE_WIFI_DISABLED:
                sb.append("WIFI_DISABLED");
                break;
            case StaEvent.TYPE_WIFI_USABILITY_SCORE_BREACH:
                sb.append("WIFI_USABILITY_SCORE_BREACH");
                break;
            case StaEvent.TYPE_LINK_PROBE:
                sb.append("LINK_PROBE");
                sb.append(" linkProbeWasSuccess=").append(event.linkProbeWasSuccess);
                if (event.linkProbeWasSuccess) {
                    sb.append(" linkProbeSuccessElapsedTimeMs=")
                            .append(event.linkProbeSuccessElapsedTimeMs);
                } else {
                    sb.append(" linkProbeFailureReason=").append(event.linkProbeFailureReason);
                }
                break;
            default:
                sb.append("UNKNOWN " + event.type + ":");
                break;
        }
        if (event.lastRssi != -127) sb.append(" lastRssi=").append(event.lastRssi);
        if (event.lastFreq != -1) sb.append(" lastFreq=").append(event.lastFreq);
        if (event.lastLinkSpeed != -1) sb.append(" lastLinkSpeed=").append(event.lastLinkSpeed);
        if (event.lastScore != -1) sb.append(" lastScore=").append(event.lastScore);
        if (event.lastWifiUsabilityScore != -1) {
            sb.append(" lastWifiUsabilityScore=").append(event.lastWifiUsabilityScore);
            sb.append(" lastPredictionHorizonSec=").append(event.lastPredictionHorizonSec);
        }
        sb.append(" screenOn=").append(event.screenOn);
        sb.append(" cellularData=").append(event.isCellularDataAvailable);
        sb.append(" adaptiveConnectivity=").append(event.isAdaptiveConnectivityEnabled);
        if (event.supplicantStateChangesBitmask != 0) {
            sb.append(", ").append(supplicantStateChangesBitmaskToString(
                    event.supplicantStateChangesBitmask));
        }
        if (event.configInfo != null) {
            sb.append(", ").append(configInfoToString(event.configInfo));
        }
        if (event.mobileTxBytes > 0) sb.append(" mobileTxBytes=").append(event.mobileTxBytes);
        if (event.mobileRxBytes > 0) sb.append(" mobileRxBytes=").append(event.mobileRxBytes);
        if (event.totalTxBytes > 0) sb.append(" totalTxBytes=").append(event.totalTxBytes);
        if (event.totalRxBytes > 0) sb.append(" totalRxBytes=").append(event.totalRxBytes);
        sb.append(" interfaceName=").append(event.interfaceName);
        sb.append(" interfaceRole=").append(clientRoleEnumToString(event.interfaceRole));
        return sb.toString();
    }

    private int convertIfaceToEnum(String ifaceName) {
        ActiveModeManager.ClientRole role = mIfaceToRoleMap.get(ifaceName);
        if (role == ActiveModeManager.ROLE_CLIENT_SCAN_ONLY) {
            return WifiMetricsProto.ROLE_CLIENT_SCAN_ONLY;
        } else if (role == ActiveModeManager.ROLE_CLIENT_SECONDARY_TRANSIENT) {
            return WifiMetricsProto.ROLE_CLIENT_SECONDARY_TRANSIENT;
        } else if (role == ActiveModeManager.ROLE_CLIENT_LOCAL_ONLY) {
            return WifiMetricsProto.ROLE_CLIENT_LOCAL_ONLY;
        } else if (role == ActiveModeManager.ROLE_CLIENT_PRIMARY) {
            return WifiMetricsProto.ROLE_CLIENT_PRIMARY;
        } else if (role == ActiveModeManager.ROLE_CLIENT_SECONDARY_LONG_LIVED) {
            return WifiMetricsProto.ROLE_CLIENT_SECONDARY_LONG_LIVED;
        }
        return WifiMetricsProto.ROLE_UNKNOWN;
    }

    private static String clientRoleEnumToString(int role) {
        switch (role) {
            case WifiMetricsProto.ROLE_CLIENT_SCAN_ONLY:
                return "ROLE_CLIENT_SCAN_ONLY";
            case WifiMetricsProto.ROLE_CLIENT_SECONDARY_TRANSIENT:
                return "ROLE_CLIENT_SECONDARY_TRANSIENT";
            case WifiMetricsProto.ROLE_CLIENT_LOCAL_ONLY:
                return "ROLE_CLIENT_LOCAL_ONLY";
            case WifiMetricsProto.ROLE_CLIENT_PRIMARY:
                return "ROLE_CLIENT_PRIMARY";
            case WifiMetricsProto.ROLE_CLIENT_SECONDARY_LONG_LIVED:
                return "ROLE_CLIENT_SECONDARY_LONG_LIVED";
            default:
                return "ROLE_UNKNOWN";
        }
    }

    private static String authFailureReasonToString(int authFailureReason) {
        switch (authFailureReason) {
            case StaEvent.AUTH_FAILURE_NONE:
                return "ERROR_AUTH_FAILURE_NONE";
            case StaEvent.AUTH_FAILURE_TIMEOUT:
                return "ERROR_AUTH_FAILURE_TIMEOUT";
            case StaEvent.AUTH_FAILURE_WRONG_PSWD:
                return "ERROR_AUTH_FAILURE_WRONG_PSWD";
            case StaEvent.AUTH_FAILURE_EAP_FAILURE:
                return "ERROR_AUTH_FAILURE_EAP_FAILURE";
            default:
                return "";
        }
    }

    private static String frameworkDisconnectReasonToString(int frameworkDisconnectReason) {
        switch (frameworkDisconnectReason) {
            case StaEvent.DISCONNECT_API:
                return "DISCONNECT_API";
            case StaEvent.DISCONNECT_GENERIC:
                return "DISCONNECT_GENERIC";
            case StaEvent.DISCONNECT_UNWANTED:
                return "DISCONNECT_UNWANTED";
            case StaEvent.DISCONNECT_ROAM_WATCHDOG_TIMER:
                return "DISCONNECT_ROAM_WATCHDOG_TIMER";
            case StaEvent.DISCONNECT_P2P_DISCONNECT_WIFI_REQUEST:
                return "DISCONNECT_P2P_DISCONNECT_WIFI_REQUEST";
            case StaEvent.DISCONNECT_RESET_SIM_NETWORKS:
                return "DISCONNECT_RESET_SIM_NETWORKS";
            case StaEvent.DISCONNECT_MBB_NO_INTERNET:
                return "DISCONNECT_MBB_NO_INTERNET";
            case StaEvent.DISCONNECT_NETWORK_REMOVED:
                return "DISCONNECT_NETWORK_REMOVED";
            case StaEvent.DISCONNECT_NETWORK_METERED:
                return "DISCONNECT_NETWORK_METERED";
            case StaEvent.DISCONNECT_NETWORK_TEMPORARY_DISABLED:
                return "DISCONNECT_NETWORK_TEMPORARY_DISABLED";
            case StaEvent.DISCONNECT_NETWORK_PERMANENT_DISABLED:
                return "DISCONNECT_NETWORK_PERMANENT_DISABLED";
            case StaEvent.DISCONNECT_CARRIER_OFFLOAD_DISABLED:
                return "DISCONNECT_CARRIER_OFFLOAD_DISABLED";
            case StaEvent.DISCONNECT_PASSPOINT_TAC:
                return "DISCONNECT_PASSPOINT_TAC";
            case StaEvent.DISCONNECT_VCN_REQUEST:
                return "DISCONNECT_VCN_REQUEST";
            case StaEvent.DISCONNECT_UNKNOWN_NETWORK:
                return "DISCONNECT_UNKNOWN_NETWORK";
            default:
                return "DISCONNECT_UNKNOWN=" + frameworkDisconnectReason;
        }
    }

    private static String configInfoToString(ConfigInfo info) {
        StringBuilder sb = new StringBuilder();
        sb.append("ConfigInfo:")
                .append(" allowed_key_management=").append(info.allowedKeyManagement)
                .append(" allowed_protocols=").append(info.allowedProtocols)
                .append(" allowed_auth_algorithms=").append(info.allowedAuthAlgorithms)
                .append(" allowed_pairwise_ciphers=").append(info.allowedPairwiseCiphers)
                .append(" allowed_group_ciphers=").append(info.allowedGroupCiphers)
                .append(" hidden_ssid=").append(info.hiddenSsid)
                .append(" is_passpoint=").append(info.isPasspoint)
                .append(" is_ephemeral=").append(info.isEphemeral)
                .append(" has_ever_connected=").append(info.hasEverConnected)
                .append(" scan_rssi=").append(info.scanRssi)
                .append(" scan_freq=").append(info.scanFreq);
        return sb.toString();
    }

    /**
     * Converts the first 31 bits of a BitSet to a little endian int
     */
    private static int bitSetToInt(BitSet bits) {
        int value = 0;
        int nBits = bits.length() < 31 ? bits.length() : 31;
        for (int i = 0; i < nBits; i++) {
            value += bits.get(i) ? (1 << i) : 0;
        }
        return value;
    }
    private void incrementSsid(SparseIntArray sia, int element) {
        increment(sia, Math.min(element, MAX_CONNECTABLE_SSID_NETWORK_BUCKET));
    }
    private void incrementBssid(SparseIntArray sia, int element) {
        increment(sia, Math.min(element, MAX_CONNECTABLE_BSSID_NETWORK_BUCKET));
    }
    private void incrementTotalScanResults(SparseIntArray sia, int element) {
        increment(sia, Math.min(element, MAX_TOTAL_SCAN_RESULTS_BUCKET));
    }
    private void incrementTotalScanSsids(SparseIntArray sia, int element) {
        increment(sia, Math.min(element, MAX_TOTAL_SCAN_RESULT_SSIDS_BUCKET));
    }
    private void incrementTotalPasspointAps(SparseIntArray sia, int element) {
        increment(sia, Math.min(element, MAX_TOTAL_PASSPOINT_APS_BUCKET));
    }
    private void incrementTotalUniquePasspointEss(SparseIntArray sia, int element) {
        increment(sia, Math.min(element, MAX_TOTAL_PASSPOINT_UNIQUE_ESS_BUCKET));
    }
    private void incrementPasspointPerUniqueEss(SparseIntArray sia, int element) {
        increment(sia, Math.min(element, MAX_PASSPOINT_APS_PER_UNIQUE_ESS_BUCKET));
    }
    private void increment80211mcAps(SparseIntArray sia, int element) {
        increment(sia, Math.min(element, MAX_TOTAL_80211MC_APS_BUCKET));
    }
    private void increment(SparseIntArray sia, int element) {
        int count = sia.get(element);
        sia.put(element, count + 1);
    }

    private static class StaEventWithTime {
        public StaEvent staEvent;
        public long wallClockMillis;

        StaEventWithTime(StaEvent event, long wallClockMillis) {
            staEvent = event;
            this.wallClockMillis = wallClockMillis;
        }

        public String toString() {
            StringBuilder sb = new StringBuilder();
            Calendar c = Calendar.getInstance();
            c.setTimeInMillis(wallClockMillis);
            if (wallClockMillis != 0) {
                sb.append(String.format("%tm-%td %tH:%tM:%tS.%tL", c, c, c, c, c, c));
            } else {
                sb.append("                  ");
            }
            sb.append(" ").append(staEventToString(staEvent));
            return sb.toString();
        }
    }

    private LinkedList<WifiIsUnusableWithTime> mWifiIsUnusableList =
            new LinkedList<WifiIsUnusableWithTime>();
    private long mTxScucessDelta = 0;
    private long mTxRetriesDelta = 0;
    private long mTxBadDelta = 0;
    private long mRxSuccessDelta = 0;
    private long mLlStatsUpdateTimeDelta = 0;
    private long mLlStatsLastUpdateTime = 0;
    private int mLastScoreNoReset = -1;
    private long mLastDataStallTime = Long.MIN_VALUE;

    private static class WifiIsUnusableWithTime {
        public WifiIsUnusableEvent event;
        public long wallClockMillis;

        WifiIsUnusableWithTime(WifiIsUnusableEvent event, long wallClockMillis) {
            this.event = event;
            this.wallClockMillis = wallClockMillis;
        }

        public String toString() {
            if (event == null) return "<NULL>";
            StringBuilder sb = new StringBuilder();
            if (wallClockMillis != 0) {
                Calendar c = Calendar.getInstance();
                c.setTimeInMillis(wallClockMillis);
                sb.append(String.format("%tm-%td %tH:%tM:%tS.%tL", c, c, c, c, c, c));
            } else {
                sb.append("                  ");
            }
            sb.append(" ");

            switch(event.type) {
                case WifiIsUnusableEvent.TYPE_DATA_STALL_BAD_TX:
                    sb.append("DATA_STALL_BAD_TX");
                    break;
                case WifiIsUnusableEvent.TYPE_DATA_STALL_TX_WITHOUT_RX:
                    sb.append("DATA_STALL_TX_WITHOUT_RX");
                    break;
                case WifiIsUnusableEvent.TYPE_DATA_STALL_BOTH:
                    sb.append("DATA_STALL_BOTH");
                    break;
                case WifiIsUnusableEvent.TYPE_FIRMWARE_ALERT:
                    sb.append("FIRMWARE_ALERT");
                    break;
                case WifiIsUnusableEvent.TYPE_IP_REACHABILITY_LOST:
                    sb.append("IP_REACHABILITY_LOST");
                    break;
                default:
                    sb.append("UNKNOWN " + event.type);
                    break;
            }

            sb.append(" lastScore=").append(event.lastScore);
            sb.append(" txSuccessDelta=").append(event.txSuccessDelta);
            sb.append(" txRetriesDelta=").append(event.txRetriesDelta);
            sb.append(" txBadDelta=").append(event.txBadDelta);
            sb.append(" rxSuccessDelta=").append(event.rxSuccessDelta);
            sb.append(" packetUpdateTimeDelta=").append(event.packetUpdateTimeDelta)
                    .append("ms");
            if (event.firmwareAlertCode != -1) {
                sb.append(" firmwareAlertCode=").append(event.firmwareAlertCode);
            }
            sb.append(" lastWifiUsabilityScore=").append(event.lastWifiUsabilityScore);
            sb.append(" lastPredictionHorizonSec=").append(event.lastPredictionHorizonSec);
            sb.append(" screenOn=").append(event.screenOn);
            sb.append(" mobileTxBytes=").append(event.mobileTxBytes);
            sb.append(" mobileRxBytes=").append(event.mobileRxBytes);
            sb.append(" totalTxBytes=").append(event.totalTxBytes);
            sb.append(" totalRxBytes=").append(event.totalRxBytes);
            return sb.toString();
        }
    }

    /**
     * Converts MeteredOverride enum to UserActionEvent type.
     * @param value
     */
    public static int convertMeteredOverrideEnumToUserActionEventType(@MeteredOverride int value) {
        int result = UserActionEvent.EVENT_UNKNOWN;
        switch(value) {
            case WifiConfiguration.METERED_OVERRIDE_NONE:
                result = UserActionEvent.EVENT_CONFIGURE_METERED_STATUS_AUTO;
                break;
            case WifiConfiguration.METERED_OVERRIDE_METERED:
                result = UserActionEvent.EVENT_CONFIGURE_METERED_STATUS_METERED;
                break;
            case WifiConfiguration.METERED_OVERRIDE_NOT_METERED:
                result = UserActionEvent.EVENT_CONFIGURE_METERED_STATUS_UNMETERED;
                break;
        }
        return result;
    }

    /**
     * Converts Adaptive Connectivity state to UserActionEvent type.
     * @param value
     */
    public static int convertAdaptiveConnectivityStateToUserActionEventType(boolean value) {
        return value ? UserActionEvent.EVENT_CONFIGURE_ADAPTIVE_CONNECTIVITY_ON
                : UserActionEvent.EVENT_CONFIGURE_ADAPTIVE_CONNECTIVITY_OFF;
    }

    static class MeteredNetworkStatsBuilder {
        // A map from network identifier to MeteredDetail
        Map<String, MeteredDetail> mNetworkMap = new ArrayMap<>();

        void put(WifiConfiguration config, boolean detectedAsMetered) {
            MeteredDetail meteredDetail = new MeteredDetail();
            boolean isMetered = detectedAsMetered;
            if (config.meteredOverride == WifiConfiguration.METERED_OVERRIDE_METERED) {
                isMetered = true;
            } else if (config.meteredOverride == WifiConfiguration.METERED_OVERRIDE_NOT_METERED) {
                isMetered = false;
            }
            meteredDetail.isMetered = isMetered;
            meteredDetail.isMeteredOverrideSet = config.meteredOverride
                    != WifiConfiguration.METERED_OVERRIDE_NONE;
            meteredDetail.isFromSuggestion = config.fromWifiNetworkSuggestion;
            mNetworkMap.put(config.getProfileKey(), meteredDetail);
        }

        void clear() {
            mNetworkMap.clear();
        }

        MeteredNetworkStats toProto(boolean isFromSuggestion) {
            MeteredNetworkStats result = new MeteredNetworkStats();
            for (MeteredDetail meteredDetail : mNetworkMap.values()) {
                if (meteredDetail.isFromSuggestion != isFromSuggestion) {
                    continue;
                }
                if (meteredDetail.isMetered) {
                    result.numMetered++;
                } else {
                    result.numUnmetered++;
                }
                if (meteredDetail.isMeteredOverrideSet) {
                    if (meteredDetail.isMetered) {
                        result.numOverrideMetered++;
                    } else {
                        result.numOverrideUnmetered++;
                    }
                }
            }
            return result;
        }

        static class MeteredDetail {
            public boolean isMetered;
            public boolean isMeteredOverrideSet;
            public boolean isFromSuggestion;
        }
    }

    /**
     * Add metered information of this network.
     * @param config WifiConfiguration representing the netework.
     * @param detectedAsMetered is the network detected as metered.
     */
    public void addMeteredStat(WifiConfiguration config, boolean detectedAsMetered) {
        synchronized (mLock) {
            if (config == null) {
                return;
            }
            mMeteredNetworkStatsBuilder.put(config, detectedAsMetered);
        }
    }
    /**
     * Logs a UserActionEvent without a target network.
     * @param eventType the type of user action (one of WifiMetricsProto.UserActionEvent.EventType)
     */
    public void logUserActionEvent(int eventType) {
        logUserActionEvent(eventType, -1);
    }

    /**
     * Logs a UserActionEvent which has a target network.
     * @param eventType the type of user action (one of WifiMetricsProto.UserActionEvent.EventType)
     * @param networkId networkId of the target network.
     */
    public void logUserActionEvent(int eventType, int networkId) {
        synchronized (mLock) {
            mUserActionEventList.add(new UserActionEventWithTime(eventType, networkId));
            if (mUserActionEventList.size() > MAX_USER_ACTION_EVENTS) {
                mUserActionEventList.remove();
            }
        }
    }

    /**
     * Logs a UserActionEvent, directly specifying the target network's properties.
     * @param eventType the type of user action (one of WifiMetricsProto.UserActionEvent.EventType)
     * @param isEphemeral true if the target network is ephemeral.
     * @param isPasspoint true if the target network is passpoint.
     */
    public void logUserActionEvent(int eventType, boolean isEphemeral, boolean isPasspoint) {
        synchronized (mLock) {
            TargetNetworkInfo networkInfo = new TargetNetworkInfo();
            networkInfo.isEphemeral = isEphemeral;
            networkInfo.isPasspoint = isPasspoint;
            mUserActionEventList.add(new UserActionEventWithTime(eventType, networkInfo));
            if (mUserActionEventList.size() > MAX_USER_ACTION_EVENTS) {
                mUserActionEventList.remove();
            }
        }
    }

    /**
     * Update the difference between the last two WifiLinkLayerStats for WifiIsUnusableEvent
     */
    public void updateWifiIsUnusableLinkLayerStats(long txSuccessDelta, long txRetriesDelta,
            long txBadDelta, long rxSuccessDelta, long updateTimeDelta) {
        mTxScucessDelta = txSuccessDelta;
        mTxRetriesDelta = txRetriesDelta;
        mTxBadDelta = txBadDelta;
        mRxSuccessDelta = rxSuccessDelta;
        mLlStatsUpdateTimeDelta = updateTimeDelta;
        mLlStatsLastUpdateTime = mClock.getElapsedSinceBootMillis();
    }

    /**
     * Clear the saved difference between the last two WifiLinkLayerStats
     */
    public void resetWifiIsUnusableLinkLayerStats() {
        mTxScucessDelta = 0;
        mTxRetriesDelta = 0;
        mTxBadDelta = 0;
        mRxSuccessDelta = 0;
        mLlStatsUpdateTimeDelta = 0;
        mLlStatsLastUpdateTime = 0;
        mLastDataStallTime = Long.MIN_VALUE;
    }

    /**
     * Log a WifiIsUnusableEvent
     * @param triggerType WifiIsUnusableEvent.type describing the event
     * @param ifaceName name of the interface.
     */
    public void logWifiIsUnusableEvent(String ifaceName, int triggerType) {
        logWifiIsUnusableEvent(ifaceName, triggerType, -1);
    }

    /**
     * Log a WifiIsUnusableEvent
     * @param triggerType WifiIsUnusableEvent.type describing the event
     * @param firmwareAlertCode WifiIsUnusableEvent.firmwareAlertCode for firmware alert code
     * @param ifaceName name of the interface.
     */
    public void logWifiIsUnusableEvent(String ifaceName, int triggerType, int firmwareAlertCode) {
        if (!isPrimary(ifaceName)) {
            return;
        }
        mScoreBreachLowTimeMillis = -1;

        long currentBootTime = mClock.getElapsedSinceBootMillis();
        switch (triggerType) {
            case WifiIsUnusableEvent.TYPE_DATA_STALL_BAD_TX:
            case WifiIsUnusableEvent.TYPE_DATA_STALL_TX_WITHOUT_RX:
            case WifiIsUnusableEvent.TYPE_DATA_STALL_BOTH:
                // Have a time-based throttle for generating WifiIsUnusableEvent from data stalls
                if (currentBootTime < mLastDataStallTime + MIN_DATA_STALL_WAIT_MS) {
                    return;
                }
                mLastDataStallTime = currentBootTime;
                break;
            case WifiIsUnusableEvent.TYPE_FIRMWARE_ALERT:
                break;
            case WifiIsUnusableEvent.TYPE_IP_REACHABILITY_LOST:
                break;
            default:
                Log.e(TAG, "Unknown WifiIsUnusableEvent: " + triggerType);
                return;
        }

        WifiIsUnusableEvent event = new WifiIsUnusableEvent();
        event.type = triggerType;
        if (triggerType == WifiIsUnusableEvent.TYPE_FIRMWARE_ALERT) {
            event.firmwareAlertCode = firmwareAlertCode;
        }
        event.startTimeMillis = currentBootTime;
        event.lastScore = mLastScoreNoReset;
        event.lastWifiUsabilityScore = mLastWifiUsabilityScoreNoReset;
        event.lastPredictionHorizonSec = mLastPredictionHorizonSecNoReset;
        event.txSuccessDelta = mTxScucessDelta;
        event.txRetriesDelta = mTxRetriesDelta;
        event.txBadDelta = mTxBadDelta;
        event.rxSuccessDelta = mRxSuccessDelta;
        event.packetUpdateTimeDelta = mLlStatsUpdateTimeDelta;
        event.lastLinkLayerStatsUpdateTime = mLlStatsLastUpdateTime;
        event.screenOn = mScreenOn;
        event.mobileTxBytes = mFacade.getMobileTxBytes();
        event.mobileRxBytes = mFacade.getMobileRxBytes();
        event.totalTxBytes = mFacade.getTotalTxBytes();
        event.totalRxBytes = mFacade.getTotalRxBytes();

        mWifiIsUnusableList.add(new WifiIsUnusableWithTime(event, mClock.getWallClockMillis()));
        if (mWifiIsUnusableList.size() > MAX_UNUSABLE_EVENTS) {
            mWifiIsUnusableList.removeFirst();
        }
        WifiUsabilityState wifiUsabilityState = mWifiUsabilityStatePerIface.getOrDefault(
                ifaceName, WifiUsabilityState.UNKNOWN);

        WifiStatsLog.write(WIFI_IS_UNUSABLE_REPORTED,
                convertWifiUnUsableTypeToProto(triggerType),
                mScorerUid, wifiUsabilityState == WifiUsabilityState.USABLE,
                convertWifiUsabilityState(wifiUsabilityState));
    }

    private int convertWifiUsabilityState(WifiUsabilityState usabilityState) {
        if (usabilityState == WifiUsabilityState.USABLE) {
            return WifiStatsLog.WIFI_IS_UNUSABLE_REPORTED__WIFI_PREDICTED_USABILITY_STATE__WIFI_USABILITY_PREDICTED_USABLE;
        } else if (usabilityState == WifiUsabilityState.UNUSABLE) {
            return WifiStatsLog.WIFI_IS_UNUSABLE_REPORTED__WIFI_PREDICTED_USABILITY_STATE__WIFI_USABILITY_PREDICTED_UNUSABLE;
        } else {
            return WifiStatsLog.WIFI_IS_UNUSABLE_REPORTED__WIFI_PREDICTED_USABILITY_STATE__WIFI_USABILITY_PREDICTED_UNKNOWN;
        }
    }

    private int convertWifiUnUsableTypeToProto(int triggerType) {
        switch (triggerType) {
            case WifiIsUnusableEvent.TYPE_DATA_STALL_BAD_TX:
                return WifiStatsLog.WIFI_IS_UNUSABLE_REPORTED__TYPE__TYPE_DATA_STALL_BAD_TX;
            case WifiIsUnusableEvent.TYPE_DATA_STALL_TX_WITHOUT_RX:
                return WifiStatsLog.WIFI_IS_UNUSABLE_REPORTED__TYPE__TYPE_DATA_STALL_TX_WITHOUT_RX;
            case WifiIsUnusableEvent.TYPE_DATA_STALL_BOTH:
                return WifiStatsLog.WIFI_IS_UNUSABLE_REPORTED__TYPE__TYPE_DATA_STALL_BOTH;
            case WifiIsUnusableEvent.TYPE_FIRMWARE_ALERT:
                return WifiStatsLog.WIFI_IS_UNUSABLE_REPORTED__TYPE__TYPE_FIRMWARE_ALERT;
            case WifiIsUnusableEvent.TYPE_IP_REACHABILITY_LOST:
                return WifiStatsLog.WIFI_IS_UNUSABLE_REPORTED__TYPE__TYPE_IP_REACHABILITY_LOST;
            default:
                return WifiStatsLog.WIFI_IS_UNUSABLE_REPORTED__TYPE__TYPE_UNKNOWN;
        }
    }

    /**
     * Extract data from |info| and |stats| to build a WifiUsabilityStatsEntry and then adds it
     * into an internal ring buffer.
     * @param info
     * @param stats
     * @param ifaceName
     */
    public void updateWifiUsabilityStatsEntries(String ifaceName, WifiInfo info,
            WifiLinkLayerStats stats) {
        // This is only collected for primary STA currently because RSSI polling is disabled for
        // non-primary STAs.
        synchronized (mLock) {
            if (info == null) {
                return;
            }
            if (stats == null) {
                // For devices lacking vendor hal, fill in the parts that we can
                stats = new WifiLinkLayerStats();
                stats.timeStampInMs = mClock.getElapsedSinceBootMillis();
                stats.txmpdu_be = info.txSuccess;
                stats.retries_be = info.txRetries;
                stats.lostmpdu_be = info.txBad;
                stats.rxmpdu_be = info.rxSuccess;
            }
            WifiUsabilityStatsEntry wifiUsabilityStatsEntry =
                    mWifiUsabilityStatsEntriesList.size()
                    < MAX_WIFI_USABILITY_STATS_ENTRIES_LIST_SIZE
                    ? new WifiUsabilityStatsEntry() : mWifiUsabilityStatsEntriesList.remove();
            wifiUsabilityStatsEntry.timeStampMs = stats.timeStampInMs;
            wifiUsabilityStatsEntry.totalTxSuccess = stats.txmpdu_be + stats.txmpdu_bk
                    + stats.txmpdu_vi + stats.txmpdu_vo;
            wifiUsabilityStatsEntry.totalTxRetries = stats.retries_be + stats.retries_bk
                    + stats.retries_vi + stats.retries_vo;
            wifiUsabilityStatsEntry.totalTxBad = stats.lostmpdu_be + stats.lostmpdu_bk
                    + stats.lostmpdu_vi + stats.lostmpdu_vo;
            wifiUsabilityStatsEntry.totalRxSuccess = stats.rxmpdu_be + stats.rxmpdu_bk
                    + stats.rxmpdu_vi + stats.rxmpdu_vo;
            /* Update per radio stats */
            if (stats.radioStats != null && stats.radioStats.length > 0) {
                int numRadios = stats.radioStats.length;
                wifiUsabilityStatsEntry.radioStats =
                        new RadioStats[numRadios];
                for (int i = 0; i < numRadios; i++) {
                    RadioStats radioStats = new RadioStats();
                    WifiLinkLayerStats.RadioStat radio = stats.radioStats[i];
                    radioStats.radioId = radio.radio_id;
                    radioStats.totalRadioOnTimeMs = radio.on_time;
                    radioStats.totalRadioTxTimeMs = radio.tx_time;
                    radioStats.totalRadioRxTimeMs = radio.rx_time;
                    radioStats.totalScanTimeMs = radio.on_time_scan;
                    radioStats.totalNanScanTimeMs = radio.on_time_nan_scan;
                    radioStats.totalBackgroundScanTimeMs = radio.on_time_background_scan;
                    radioStats.totalRoamScanTimeMs = radio.on_time_roam_scan;
                    radioStats.totalPnoScanTimeMs = radio.on_time_pno_scan;
                    radioStats.totalHotspot2ScanTimeMs = radio.on_time_hs20_scan;
                    wifiUsabilityStatsEntry.radioStats[i] = radioStats;
                }
            }
            wifiUsabilityStatsEntry.totalRadioOnTimeMs = stats.on_time;
            wifiUsabilityStatsEntry.totalRadioTxTimeMs = stats.tx_time;
            wifiUsabilityStatsEntry.totalRadioRxTimeMs = stats.rx_time;
            wifiUsabilityStatsEntry.totalScanTimeMs = stats.on_time_scan;
            wifiUsabilityStatsEntry.totalNanScanTimeMs = stats.on_time_nan_scan;
            wifiUsabilityStatsEntry.totalBackgroundScanTimeMs = stats.on_time_background_scan;
            wifiUsabilityStatsEntry.totalRoamScanTimeMs = stats.on_time_roam_scan;
            wifiUsabilityStatsEntry.totalPnoScanTimeMs = stats.on_time_pno_scan;
            wifiUsabilityStatsEntry.totalHotspot2ScanTimeMs = stats.on_time_hs20_scan;
            wifiUsabilityStatsEntry.rssi = info.getRssi();
            wifiUsabilityStatsEntry.linkSpeedMbps = info.getLinkSpeed();
            WifiLinkLayerStats.ChannelStats statsMap =
                    stats.channelStatsMap.get(info.getFrequency());
            if (statsMap != null) {
                wifiUsabilityStatsEntry.totalRadioOnFreqTimeMs = statsMap.radioOnTimeMs;
                wifiUsabilityStatsEntry.totalCcaBusyFreqTimeMs = statsMap.ccaBusyTimeMs;
            }
            wifiUsabilityStatsEntry.totalBeaconRx = stats.beacon_rx;
            mLastTotalBeaconRx = stats.beacon_rx;
            wifiUsabilityStatsEntry.timeSliceDutyCycleInPercent = stats.timeSliceDutyCycleInPercent;

            boolean isSameBssidAndFreq = mLastBssid == null || mLastFrequency == -1
                    || (mLastBssid.equals(info.getBSSID())
                    && mLastFrequency == info.getFrequency());
            mLastBssid = info.getBSSID();
            mLastFrequency = info.getFrequency();
            wifiUsabilityStatsEntry.wifiScore = mLastScoreNoReset;
            wifiUsabilityStatsEntry.wifiUsabilityScore = mLastWifiUsabilityScoreNoReset;
            wifiUsabilityStatsEntry.seqNumToFramework = mSeqNumToFramework;
            wifiUsabilityStatsEntry.predictionHorizonSec = mLastPredictionHorizonSecNoReset;
            switch (mProbeStatusSinceLastUpdate) {
                case android.net.wifi.WifiUsabilityStatsEntry.PROBE_STATUS_NO_PROBE:
                    wifiUsabilityStatsEntry.probeStatusSinceLastUpdate =
                            WifiUsabilityStatsEntry.PROBE_STATUS_NO_PROBE;
                    break;
                case android.net.wifi.WifiUsabilityStatsEntry.PROBE_STATUS_SUCCESS:
                    wifiUsabilityStatsEntry.probeStatusSinceLastUpdate =
                            WifiUsabilityStatsEntry.PROBE_STATUS_SUCCESS;
                    break;
                case android.net.wifi.WifiUsabilityStatsEntry.PROBE_STATUS_FAILURE:
                    wifiUsabilityStatsEntry.probeStatusSinceLastUpdate =
                            WifiUsabilityStatsEntry.PROBE_STATUS_FAILURE;
                    break;
                default:
                    wifiUsabilityStatsEntry.probeStatusSinceLastUpdate =
                            WifiUsabilityStatsEntry.PROBE_STATUS_UNKNOWN;
                    Log.e(TAG, "Unknown link probe status: " + mProbeStatusSinceLastUpdate);
            }
            wifiUsabilityStatsEntry.probeElapsedTimeSinceLastUpdateMs =
                    mProbeElapsedTimeSinceLastUpdateMs;
            wifiUsabilityStatsEntry.probeMcsRateSinceLastUpdate = mProbeMcsRateSinceLastUpdate;
            wifiUsabilityStatsEntry.rxLinkSpeedMbps = info.getRxLinkSpeedMbps();
            wifiUsabilityStatsEntry.isSameBssidAndFreq = isSameBssidAndFreq;
            wifiUsabilityStatsEntry.seqNumInsideFramework = mSeqNumInsideFramework;
            wifiUsabilityStatsEntry.deviceMobilityState = mCurrentDeviceMobilityState;
            wifiUsabilityStatsEntry.contentionTimeStats =
                    new ContentionTimeStats[NUM_WME_ACCESS_CATEGORIES];
            for (int ac = 0; ac < NUM_WME_ACCESS_CATEGORIES; ac++) {
                ContentionTimeStats contentionTimeStats = new ContentionTimeStats();
                switch (ac) {
                    case ContentionTimeStats.WME_ACCESS_CATEGORY_BE:
                        contentionTimeStats.accessCategory =
                                ContentionTimeStats.WME_ACCESS_CATEGORY_BE;
                        contentionTimeStats.contentionTimeMinMicros =
                                stats.contentionTimeMinBeInUsec;
                        contentionTimeStats.contentionTimeMaxMicros =
                                stats.contentionTimeMaxBeInUsec;
                        contentionTimeStats.contentionTimeAvgMicros =
                                stats.contentionTimeAvgBeInUsec;
                        contentionTimeStats.contentionNumSamples =
                                stats.contentionNumSamplesBe;
                        break;
                    case ContentionTimeStats.WME_ACCESS_CATEGORY_BK:
                        contentionTimeStats.accessCategory =
                                ContentionTimeStats.WME_ACCESS_CATEGORY_BK;
                        contentionTimeStats.contentionTimeMinMicros =
                                stats.contentionTimeMinBkInUsec;
                        contentionTimeStats.contentionTimeMaxMicros =
                                stats.contentionTimeMaxBkInUsec;
                        contentionTimeStats.contentionTimeAvgMicros =
                                stats.contentionTimeAvgBkInUsec;
                        contentionTimeStats.contentionNumSamples =
                                stats.contentionNumSamplesBk;
                        break;
                    case ContentionTimeStats.WME_ACCESS_CATEGORY_VI:
                        contentionTimeStats.accessCategory =
                                ContentionTimeStats.WME_ACCESS_CATEGORY_VI;
                        contentionTimeStats.contentionTimeMinMicros =
                                stats.contentionTimeMinViInUsec;
                        contentionTimeStats.contentionTimeMaxMicros =
                                stats.contentionTimeMaxViInUsec;
                        contentionTimeStats.contentionTimeAvgMicros =
                                stats.contentionTimeAvgViInUsec;
                        contentionTimeStats.contentionNumSamples =
                                stats.contentionNumSamplesVi;
                        break;
                    case ContentionTimeStats.WME_ACCESS_CATEGORY_VO:
                        contentionTimeStats.accessCategory =
                                ContentionTimeStats.WME_ACCESS_CATEGORY_VO;
                        contentionTimeStats.contentionTimeMinMicros =
                                stats.contentionTimeMinVoInUsec;
                        contentionTimeStats.contentionTimeMaxMicros =
                                stats.contentionTimeMaxVoInUsec;
                        contentionTimeStats.contentionTimeAvgMicros =
                                stats.contentionTimeAvgVoInUsec;
                        contentionTimeStats.contentionNumSamples =
                                stats.contentionNumSamplesVo;
                        break;
                    default:
                        Log.e(TAG, "Unknown WME Access Category: " + ac);
                }
                wifiUsabilityStatsEntry.contentionTimeStats[ac] = contentionTimeStats;
            }
            if (mWifiChannelUtilization != null) {
                wifiUsabilityStatsEntry.channelUtilizationRatio =
                        mWifiChannelUtilization.getUtilizationRatio(mLastFrequency);
            }
            if (mWifiDataStall != null) {
                wifiUsabilityStatsEntry.isThroughputSufficient =
                        mWifiDataStall.isThroughputSufficient();
                wifiUsabilityStatsEntry.isCellularDataAvailable =
                        mWifiDataStall.isCellularDataAvailable();
            }
            if (mWifiSettingsStore != null) {
                wifiUsabilityStatsEntry.isWifiScoringEnabled =
                        mWifiSettingsStore.isWifiScoringEnabled();
            }
            // Here it is assumed there is only one peer information from HAL and the peer is the
            // AP that STA is associated with.
            if (stats.peerInfo != null && stats.peerInfo.length > 0
                    && stats.peerInfo[0].rateStats != null) {
                wifiUsabilityStatsEntry.staCount = stats.peerInfo[0].staCount;
                wifiUsabilityStatsEntry.channelUtilization = stats.peerInfo[0].chanUtil;
                int numRates = stats.peerInfo[0].rateStats != null
                        ? stats.peerInfo[0].rateStats.length : 0;
                wifiUsabilityStatsEntry.rateStats = new RateStats[numRates];
                for (int i = 0; i < numRates; i++) {
                    RateStats rate = new RateStats();
                    WifiLinkLayerStats.RateStat curRate = stats.peerInfo[0].rateStats[i];
                    rate.preamble = curRate.preamble;
                    rate.nss = curRate.nss;
                    rate.bw = curRate.bw;
                    rate.rateMcsIdx = curRate.rateMcsIdx;
                    rate.bitRateInKbps = curRate.bitRateInKbps;
                    rate.txMpdu = curRate.txMpdu;
                    rate.rxMpdu = curRate.rxMpdu;
                    rate.mpduLost = curRate.mpduLost;
                    rate.retries = curRate.retries;
                    wifiUsabilityStatsEntry.rateStats[i] = rate;
                }
            }

            mWifiUsabilityStatsEntriesList.add(wifiUsabilityStatsEntry);
            mWifiUsabilityStatsCounter++;
            if (mWifiUsabilityStatsCounter >= NUM_WIFI_USABILITY_STATS_ENTRIES_PER_WIFI_GOOD) {
                addToWifiUsabilityStatsList(ifaceName, WifiUsabilityStats.LABEL_GOOD,
                        WifiUsabilityStats.TYPE_UNKNOWN, -1);
            }
            if (mScoreBreachLowTimeMillis != -1) {
                long elapsedTime =  mClock.getElapsedSinceBootMillis() - mScoreBreachLowTimeMillis;
                if (elapsedTime >= MIN_SCORE_BREACH_TO_GOOD_STATS_WAIT_TIME_MS) {
                    mScoreBreachLowTimeMillis = -1;
                    if (elapsedTime <= VALIDITY_PERIOD_OF_SCORE_BREACH_LOW_MS) {
                        addToWifiUsabilityStatsList(ifaceName, WifiUsabilityStats.LABEL_GOOD,
                                WifiUsabilityStats.TYPE_UNKNOWN, -1);
                    }
                }
            }

            // Invoke Wifi usability stats listener.
            // TODO(b/179518316): Enable this for secondary transient STA also if external scorer
            // is in charge of MBB.
            if (isPrimary(ifaceName)) {
                sendWifiUsabilityStats(mSeqNumInsideFramework, isSameBssidAndFreq,
                        createNewWifiUsabilityStatsEntryParcelable(wifiUsabilityStatsEntry, stats,
                                info));
            }

            mSeqNumInsideFramework++;
            mProbeStatusSinceLastUpdate =
                    android.net.wifi.WifiUsabilityStatsEntry.PROBE_STATUS_NO_PROBE;
            mProbeElapsedTimeSinceLastUpdateMs = -1;
            mProbeMcsRateSinceLastUpdate = -1;
        }
    }

    /**
     * Send Wifi usability stats.
     * @param seqNum
     * @param isSameBssidAndFreq
     * @param statsEntry
     */
    private void sendWifiUsabilityStats(int seqNum, boolean isSameBssidAndFreq,
            android.net.wifi.WifiUsabilityStatsEntry statsEntry) {
        int itemCount = mOnWifiUsabilityListeners.beginBroadcast();
        for (int i = 0; i < itemCount; i++) {
            try {
                mOnWifiUsabilityListeners.getBroadcastItem(i).onWifiUsabilityStats(seqNum,
                        isSameBssidAndFreq, statsEntry);
            } catch (RemoteException e) {
                Log.e(TAG, "Unable to invoke Wifi usability stats entry listener ", e);
            }
        }
        mOnWifiUsabilityListeners.finishBroadcast();
    }

    private android.net.wifi.WifiUsabilityStatsEntry.ContentionTimeStats[]
            convertContentionTimeStats(WifiLinkLayerStats.LinkSpecificStats stats) {
        android.net.wifi.WifiUsabilityStatsEntry.ContentionTimeStats[] contentionTimeStatsArray =
                new android.net.wifi.WifiUsabilityStatsEntry.ContentionTimeStats[
                        android.net.wifi.WifiUsabilityStatsEntry.NUM_WME_ACCESS_CATEGORIES];
        for (int ac = 0; ac < android.net.wifi.WifiUsabilityStatsEntry.NUM_WME_ACCESS_CATEGORIES;
                ac++) {
            android.net.wifi.WifiUsabilityStatsEntry.ContentionTimeStats contentionTimeStats = null;
            switch (ac) {
                case android.net.wifi.WifiUsabilityStatsEntry.WME_ACCESS_CATEGORY_BE:
                    contentionTimeStats =
                            new android.net.wifi.WifiUsabilityStatsEntry.ContentionTimeStats(
                                    stats.contentionTimeMinBeInUsec,
                                    stats.contentionTimeMaxBeInUsec,
                                    stats.contentionTimeAvgBeInUsec,
                                    stats.contentionNumSamplesBe
                            );
                    break;
                case android.net.wifi.WifiUsabilityStatsEntry.WME_ACCESS_CATEGORY_BK:
                    contentionTimeStats =
                            new android.net.wifi.WifiUsabilityStatsEntry.ContentionTimeStats(
                                    stats.contentionTimeMinBkInUsec,
                                    stats.contentionTimeMaxBkInUsec,
                                    stats.contentionTimeAvgBkInUsec,
                                    stats.contentionNumSamplesBk
                            );
                    break;
                case android.net.wifi.WifiUsabilityStatsEntry.WME_ACCESS_CATEGORY_VO:
                    contentionTimeStats =
                            new android.net.wifi.WifiUsabilityStatsEntry.ContentionTimeStats(
                                    stats.contentionTimeMinVoInUsec,
                                    stats.contentionTimeMaxVoInUsec,
                                    stats.contentionTimeAvgVoInUsec,
                                    stats.contentionNumSamplesVo
                            );
                    break;
                case android.net.wifi.WifiUsabilityStatsEntry.WME_ACCESS_CATEGORY_VI:
                    contentionTimeStats =
                            new android.net.wifi.WifiUsabilityStatsEntry.ContentionTimeStats(
                                    stats.contentionTimeMinViInUsec,
                                    stats.contentionTimeMaxViInUsec,
                                    stats.contentionTimeAvgViInUsec,
                                    stats.contentionNumSamplesVi
                            );
                    break;
                default:
                    Log.d(TAG, "Unknown WME Access Category: " + ac);
                    contentionTimeStats = null;
            }
            contentionTimeStatsArray[ac] = contentionTimeStats;
        }
        return contentionTimeStatsArray;
    }

    private android.net.wifi.WifiUsabilityStatsEntry.RateStats[] convertRateStats(
            WifiLinkLayerStats.LinkSpecificStats stats) {
        android.net.wifi.WifiUsabilityStatsEntry.RateStats[] rateStats = null;
        if (stats.peerInfo != null && stats.peerInfo.length > 0
                && stats.peerInfo[0].rateStats != null) {
            int numRates = stats.peerInfo[0].rateStats != null
                    ? stats.peerInfo[0].rateStats.length : 0;
            rateStats = new android.net.wifi.WifiUsabilityStatsEntry.RateStats[numRates];
            for (int i = 0; i < numRates; i++) {
                WifiLinkLayerStats.RateStat curRate = stats.peerInfo[0].rateStats[i];
                android.net.wifi.WifiUsabilityStatsEntry.RateStats rate =
                        new android.net.wifi.WifiUsabilityStatsEntry.RateStats(
                                convertPreambleTypeEnumToUsabilityStatsType(curRate.preamble),
                                convertSpatialStreamEnumToUsabilityStatsType(curRate.nss),
                                convertBandwidthEnumToUsabilityStatsType(curRate.bw),
                                curRate.rateMcsIdx, curRate.bitRateInKbps,
                                curRate.txMpdu, curRate.rxMpdu, curRate.mpduLost, curRate.retries);
                rateStats[i] = rate;
            }
        }
        return rateStats;
    }

    private SparseArray<android.net.wifi.WifiUsabilityStatsEntry.LinkStats> convertLinkStats(
            WifiLinkLayerStats stats, WifiInfo info) {
        SparseArray<android.net.wifi.WifiUsabilityStatsEntry.LinkStats> linkStats =
                new SparseArray<>();
        if (stats == null || stats.links == null || stats.links.length == 0) return linkStats;
        // Create a link id to MLO link mapping
        SparseArray<MloLink> mloLinks = new SparseArray<>();
        for (MloLink link: info.getAffiliatedMloLinks()) {
            mloLinks.put(link.getLinkId(), link);
        }
        mLastLinkMetrics.clear();
        // Fill per link stats.
        for (WifiLinkLayerStats.LinkSpecificStats inStat : stats.links) {
            if (inStat == null) break;
            LinkMetrics linkMetrics = new LinkMetrics();
            linkMetrics.setTotalBeaconRx(inStat.beacon_rx);
            linkMetrics.setLinkUsageState(inStat.state);
            mLastLinkMetrics.put(inStat.link_id, linkMetrics);
            WifiLinkLayerStats.ChannelStats channelStatsMap = stats.channelStatsMap.get(
                    inStat.frequencyMhz);
            // Note: RSSI, Tx & Rx link speed are derived from signal poll stats which is updated in
            // Mlolink or WifiInfo (non-MLO case).
            android.net.wifi.WifiUsabilityStatsEntry.LinkStats outStat =
                    new android.net.wifi.WifiUsabilityStatsEntry.LinkStats(inStat.link_id,
                            inStat.state, inStat.radio_id,
                            (mloLinks.size() > 0) ? mloLinks.get(inStat.link_id,
                                    new MloLink()).getRssi() : info.getRssi(),
                            (mloLinks.size() > 0) ? mloLinks.get(inStat.link_id,
                                    new MloLink()).getTxLinkSpeedMbps() : info.getTxLinkSpeedMbps(),
                            (mloLinks.size() > 0) ? mloLinks.get(inStat.link_id,
                                    new MloLink()).getRxLinkSpeedMbps() : info.getRxLinkSpeedMbps(),
                            inStat.txmpdu_be + inStat.txmpdu_bk + inStat.txmpdu_vi
                                    + inStat.txmpdu_vo,
                            inStat.retries_be + inStat.retries_bk + inStat.retries_vi
                                    + inStat.retries_vo,
                            inStat.lostmpdu_be + inStat.lostmpdu_bk + inStat.lostmpdu_vo
                                    + inStat.lostmpdu_vi,
                            inStat.rxmpdu_be + inStat.rxmpdu_bk + inStat.rxmpdu_vo
                                    + inStat.rxmpdu_vi,
                            inStat.beacon_rx, inStat.timeSliceDutyCycleInPercent,
                            (channelStatsMap != null) ? channelStatsMap.ccaBusyTimeMs : 0 ,
                            (channelStatsMap != null) ? channelStatsMap.radioOnTimeMs : 0,
                            convertContentionTimeStats(inStat),
                            convertRateStats(inStat));
            linkStats.put(inStat.link_id, outStat);
        }

        return linkStats;
    }

    private android.net.wifi.WifiUsabilityStatsEntry createNewWifiUsabilityStatsEntryParcelable(
            WifiUsabilityStatsEntry s, WifiLinkLayerStats stats, WifiInfo info) {
        int probeStatus;
        switch (s.probeStatusSinceLastUpdate) {
            case WifiUsabilityStatsEntry.PROBE_STATUS_NO_PROBE:
                probeStatus = android.net.wifi.WifiUsabilityStatsEntry.PROBE_STATUS_NO_PROBE;
                break;
            case WifiUsabilityStatsEntry.PROBE_STATUS_SUCCESS:
                probeStatus = android.net.wifi.WifiUsabilityStatsEntry.PROBE_STATUS_SUCCESS;
                break;
            case WifiUsabilityStatsEntry.PROBE_STATUS_FAILURE:
                probeStatus = android.net.wifi.WifiUsabilityStatsEntry.PROBE_STATUS_FAILURE;
                break;
            default:
                probeStatus = android.net.wifi.WifiUsabilityStatsEntry.PROBE_STATUS_UNKNOWN;
                Log.e(TAG, "Unknown link probe status: " + s.probeStatusSinceLastUpdate);
        }
        android.net.wifi.WifiUsabilityStatsEntry.ContentionTimeStats[] contentionTimeStats =
                new android.net.wifi.WifiUsabilityStatsEntry.ContentionTimeStats[
                        android.net.wifi.WifiUsabilityStatsEntry.NUM_WME_ACCESS_CATEGORIES];
        createNewContentionTimeStatsParcelable(contentionTimeStats, s.contentionTimeStats);
        int numRates = s.rateStats != null ? s.rateStats.length : 0;
        android.net.wifi.WifiUsabilityStatsEntry.RateStats[] rateStats =
                new android.net.wifi.WifiUsabilityStatsEntry.RateStats[numRates];
        createNewRateStatsParcelable(rateStats, s.rateStats);
        int numRadios = s.radioStats != null ? s.radioStats.length : 0;
        android.net.wifi.WifiUsabilityStatsEntry.RadioStats[] radioStats =
                new android.net.wifi.WifiUsabilityStatsEntry.RadioStats[numRadios];
        createNewRadioStatsParcelable(radioStats, s.radioStats);
        // TODO: remove the following hardcoded values once if they are removed from public API
        return new android.net.wifi.WifiUsabilityStatsEntry(s.timeStampMs, s.rssi,
                s.linkSpeedMbps, s.totalTxSuccess, s.totalTxRetries,
                s.totalTxBad, s.totalRxSuccess, s.totalRadioOnTimeMs,
                s.totalRadioTxTimeMs, s.totalRadioRxTimeMs, s.totalScanTimeMs,
                s.totalNanScanTimeMs, s.totalBackgroundScanTimeMs, s.totalRoamScanTimeMs,
                s.totalPnoScanTimeMs, s.totalHotspot2ScanTimeMs, s.totalCcaBusyFreqTimeMs,
                s.totalRadioOnFreqTimeMs, s.totalBeaconRx, probeStatus,
                s.probeElapsedTimeSinceLastUpdateMs, s.probeMcsRateSinceLastUpdate,
                s.rxLinkSpeedMbps, s.timeSliceDutyCycleInPercent, contentionTimeStats, rateStats,
                radioStats, s.channelUtilizationRatio, s.isThroughputSufficient,
                s.isWifiScoringEnabled, s.isCellularDataAvailable, 0, 0, 0, false,
                convertLinkStats(stats, info)
        );
    }

    private void createNewContentionTimeStatsParcelable(
            android.net.wifi.WifiUsabilityStatsEntry.ContentionTimeStats[] statsParcelable,
                    ContentionTimeStats[] stats) {
        if (statsParcelable.length != stats.length || stats.length != NUM_WME_ACCESS_CATEGORIES) {
            Log.e(TAG, "The two ContentionTimeStats do not match in length: "
                    + " in proto: " + stats.length
                    + " in system API: " + statsParcelable.length);
            return;
        }
        for (int ac = 0; ac < NUM_WME_ACCESS_CATEGORIES; ac++) {
            android.net.wifi.WifiUsabilityStatsEntry.ContentionTimeStats stat =
                    new android.net.wifi.WifiUsabilityStatsEntry.ContentionTimeStats(
                            stats[ac].contentionTimeMinMicros,
                            stats[ac].contentionTimeMaxMicros,
                            stats[ac].contentionTimeAvgMicros,
                            stats[ac].contentionNumSamples);
            switch (ac) {
                case ContentionTimeStats.WME_ACCESS_CATEGORY_BE:
                    statsParcelable[
                            android.net.wifi.WifiUsabilityStatsEntry.WME_ACCESS_CATEGORY_BE] = stat;
                    break;
                case ContentionTimeStats.WME_ACCESS_CATEGORY_BK:
                    statsParcelable[
                            android.net.wifi.WifiUsabilityStatsEntry.WME_ACCESS_CATEGORY_BK] = stat;
                    break;
                case ContentionTimeStats.WME_ACCESS_CATEGORY_VI:
                    statsParcelable[
                            android.net.wifi.WifiUsabilityStatsEntry.WME_ACCESS_CATEGORY_VI] = stat;
                    break;
                case ContentionTimeStats.WME_ACCESS_CATEGORY_VO:
                    statsParcelable[
                            android.net.wifi.WifiUsabilityStatsEntry.WME_ACCESS_CATEGORY_VO] = stat;
                    break;
                default:
                    Log.e(TAG, "Unknown WME Access Category: " + ac);
            }
        }
    }

    private void createNewRateStatsParcelable(
            android.net.wifi.WifiUsabilityStatsEntry.RateStats[] statsParcelable,
                    RateStats[] stats) {
        if (stats == null) {
            return;
        }
        for (int i = 0; i < stats.length; i++) {
            statsParcelable[i] = new android.net.wifi.WifiUsabilityStatsEntry.RateStats(
                    convertPreambleTypeEnumToUsabilityStatsType(stats[i].preamble),
                    convertSpatialStreamEnumToUsabilityStatsType(stats[i].nss),
                    convertBandwidthEnumToUsabilityStatsType(stats[i].bw),
                    stats[i].rateMcsIdx, stats[i].bitRateInKbps, stats[i].txMpdu, stats[i].rxMpdu,
                    stats[i].mpduLost, stats[i].retries
            );
        }
    }

    /**
     * Converts bandwidth enum in proto to WifiUsabilityStatsEntry type.
     * @param value
     */
    @VisibleForTesting
    public static int convertBandwidthEnumToUsabilityStatsType(int value) {
        switch (value) {
            case RateStats.WIFI_BANDWIDTH_20_MHZ:
                return android.net.wifi.WifiUsabilityStatsEntry.WIFI_BANDWIDTH_20_MHZ;
            case RateStats.WIFI_BANDWIDTH_40_MHZ:
                return android.net.wifi.WifiUsabilityStatsEntry.WIFI_BANDWIDTH_40_MHZ;
            case RateStats.WIFI_BANDWIDTH_80_MHZ:
                return android.net.wifi.WifiUsabilityStatsEntry.WIFI_BANDWIDTH_80_MHZ;
            case RateStats.WIFI_BANDWIDTH_160_MHZ:
                return android.net.wifi.WifiUsabilityStatsEntry.WIFI_BANDWIDTH_160_MHZ;
            case RateStats.WIFI_BANDWIDTH_80P80_MHZ:
                return android.net.wifi.WifiUsabilityStatsEntry.WIFI_BANDWIDTH_80P80_MHZ;
            case RateStats.WIFI_BANDWIDTH_5_MHZ:
                return android.net.wifi.WifiUsabilityStatsEntry.WIFI_BANDWIDTH_5_MHZ;
            case RateStats.WIFI_BANDWIDTH_10_MHZ:
                return android.net.wifi.WifiUsabilityStatsEntry.WIFI_BANDWIDTH_10_MHZ;
        }
        return android.net.wifi.WifiUsabilityStatsEntry.WIFI_BANDWIDTH_INVALID;
    }

    /**
     * Converts spatial streams enum in proto to WifiUsabilityStatsEntry type.
     * @param value
     */
    @VisibleForTesting
    public static int convertSpatialStreamEnumToUsabilityStatsType(int value) {
        switch (value) {
            case RateStats.WIFI_SPATIAL_STREAMS_ONE:
                return android.net.wifi.WifiUsabilityStatsEntry.WIFI_SPATIAL_STREAMS_ONE;
            case RateStats.WIFI_SPATIAL_STREAMS_TWO:
                return android.net.wifi.WifiUsabilityStatsEntry.WIFI_SPATIAL_STREAMS_TWO;
            case RateStats.WIFI_SPATIAL_STREAMS_THREE:
                return android.net.wifi.WifiUsabilityStatsEntry.WIFI_SPATIAL_STREAMS_THREE;
            case RateStats.WIFI_SPATIAL_STREAMS_FOUR:
                return android.net.wifi.WifiUsabilityStatsEntry.WIFI_SPATIAL_STREAMS_FOUR;
        }
        return android.net.wifi.WifiUsabilityStatsEntry.WIFI_SPATIAL_STREAMS_INVALID;
    }

    /**
     * Converts preamble type enum in proto to WifiUsabilityStatsEntry type.
     * @param value
     */
    @VisibleForTesting
    public static int convertPreambleTypeEnumToUsabilityStatsType(int value) {
        switch (value) {
            case RateStats.WIFI_PREAMBLE_OFDM:
                return android.net.wifi.WifiUsabilityStatsEntry.WIFI_PREAMBLE_OFDM;
            case RateStats.WIFI_PREAMBLE_CCK:
                return android.net.wifi.WifiUsabilityStatsEntry.WIFI_PREAMBLE_CCK;
            case RateStats.WIFI_PREAMBLE_HT:
                return android.net.wifi.WifiUsabilityStatsEntry.WIFI_PREAMBLE_HT;
            case RateStats.WIFI_PREAMBLE_VHT:
                return android.net.wifi.WifiUsabilityStatsEntry.WIFI_PREAMBLE_VHT;
            case RateStats.WIFI_PREAMBLE_HE:
                return android.net.wifi.WifiUsabilityStatsEntry.WIFI_PREAMBLE_HE;
        }
        return android.net.wifi.WifiUsabilityStatsEntry.WIFI_PREAMBLE_INVALID;
    }

    private void createNewRadioStatsParcelable(
            android.net.wifi.WifiUsabilityStatsEntry.RadioStats[] statsParcelable,
            RadioStats[] stats) {
        if (stats == null) {
            return;
        }
        for (int i = 0; i < stats.length; i++) {
            statsParcelable[i] =
                    new android.net.wifi.WifiUsabilityStatsEntry.RadioStats(
                            stats[i].radioId,
                            stats[i].totalRadioOnTimeMs,
                            stats[i].totalRadioTxTimeMs,
                            stats[i].totalRadioRxTimeMs,
                            stats[i].totalScanTimeMs,
                            stats[i].totalNanScanTimeMs,
                            stats[i].totalBackgroundScanTimeMs,
                            stats[i].totalRoamScanTimeMs,
                            stats[i].totalPnoScanTimeMs,
                            stats[i].totalHotspot2ScanTimeMs);
        }
    }

    private WifiUsabilityStatsEntry createNewWifiUsabilityStatsEntry(WifiUsabilityStatsEntry s) {
        WifiUsabilityStatsEntry out = new WifiUsabilityStatsEntry();
        out.timeStampMs = s.timeStampMs;
        out.totalTxSuccess = s.totalTxSuccess;
        out.totalTxRetries = s.totalTxRetries;
        out.totalTxBad = s.totalTxBad;
        out.totalRxSuccess = s.totalRxSuccess;
        out.totalRadioOnTimeMs = s.totalRadioOnTimeMs;
        out.totalRadioTxTimeMs = s.totalRadioTxTimeMs;
        out.totalRadioRxTimeMs = s.totalRadioRxTimeMs;
        out.totalScanTimeMs = s.totalScanTimeMs;
        out.totalNanScanTimeMs = s.totalNanScanTimeMs;
        out.totalBackgroundScanTimeMs = s.totalBackgroundScanTimeMs;
        out.totalRoamScanTimeMs = s.totalRoamScanTimeMs;
        out.totalPnoScanTimeMs = s.totalPnoScanTimeMs;
        out.totalHotspot2ScanTimeMs = s.totalHotspot2ScanTimeMs;
        out.rssi = s.rssi;
        out.linkSpeedMbps = s.linkSpeedMbps;
        out.totalCcaBusyFreqTimeMs = s.totalCcaBusyFreqTimeMs;
        out.totalRadioOnFreqTimeMs = s.totalRadioOnFreqTimeMs;
        out.totalBeaconRx = s.totalBeaconRx;
        out.wifiScore = s.wifiScore;
        out.wifiUsabilityScore = s.wifiUsabilityScore;
        out.seqNumToFramework = s.seqNumToFramework;
        out.predictionHorizonSec = s.predictionHorizonSec;
        out.probeStatusSinceLastUpdate = s.probeStatusSinceLastUpdate;
        out.probeElapsedTimeSinceLastUpdateMs = s.probeElapsedTimeSinceLastUpdateMs;
        out.probeMcsRateSinceLastUpdate = s.probeMcsRateSinceLastUpdate;
        out.rxLinkSpeedMbps = s.rxLinkSpeedMbps;
        out.isSameBssidAndFreq = s.isSameBssidAndFreq;
        out.seqNumInsideFramework = s.seqNumInsideFramework;
        out.deviceMobilityState = s.deviceMobilityState;
        out.timeSliceDutyCycleInPercent = s.timeSliceDutyCycleInPercent;
        out.contentionTimeStats = s.contentionTimeStats;
        out.channelUtilizationRatio = s.channelUtilizationRatio;
        out.isThroughputSufficient = s.isThroughputSufficient;
        out.isWifiScoringEnabled = s.isWifiScoringEnabled;
        out.isCellularDataAvailable = s.isCellularDataAvailable;
        out.rateStats = s.rateStats;
        out.staCount = s.staCount;
        out.channelUtilization = s.channelUtilization;
        out.radioStats = s.radioStats;
        return out;
    }

    private WifiUsabilityStats createWifiUsabilityStatsWithLabel(int label, int triggerType,
            int firmwareAlertCode) {
        WifiUsabilityStats wifiUsabilityStats = new WifiUsabilityStats();
        wifiUsabilityStats.label = label;
        wifiUsabilityStats.triggerType = triggerType;
        wifiUsabilityStats.firmwareAlertCode = firmwareAlertCode;
        wifiUsabilityStats.timeStampMs = mClock.getElapsedSinceBootMillis();
        wifiUsabilityStats.stats =
                new WifiUsabilityStatsEntry[mWifiUsabilityStatsEntriesList.size()];
        for (int i = 0; i < mWifiUsabilityStatsEntriesList.size(); i++) {
            wifiUsabilityStats.stats[i] =
                    createNewWifiUsabilityStatsEntry(mWifiUsabilityStatsEntriesList.get(i));
        }
        return wifiUsabilityStats;
    }

    /**
     * Label the current snapshot of WifiUsabilityStatsEntrys and save the labeled data in memory.
     * @param label WifiUsabilityStats.LABEL_GOOD or WifiUsabilityStats.LABEL_BAD
     * @param triggerType what event triggers WifiUsabilityStats
     * @param firmwareAlertCode the firmware alert code when the stats was triggered by a
     *        firmware alert
     */
    public void addToWifiUsabilityStatsList(String ifaceName, int label, int triggerType,
            int firmwareAlertCode) {
        synchronized (mLock) {
            if (!isPrimary(ifaceName)) {
                return;
            }
            if (mWifiUsabilityStatsEntriesList.isEmpty() || !mScreenOn) {
                return;
            }
            if (label == WifiUsabilityStats.LABEL_GOOD) {
                // Only add a good event if at least |MIN_WIFI_GOOD_USABILITY_STATS_PERIOD_MS|
                // has passed.
                if (mWifiUsabilityStatsListGood.isEmpty()
                        || mWifiUsabilityStatsListGood.getLast().stats[mWifiUsabilityStatsListGood
                        .getLast().stats.length - 1].timeStampMs
                        + MIN_WIFI_GOOD_USABILITY_STATS_PERIOD_MS
                        < mWifiUsabilityStatsEntriesList.getLast().timeStampMs) {
                    while (mWifiUsabilityStatsListGood.size()
                            >= MAX_WIFI_USABILITY_STATS_LIST_SIZE_PER_TYPE) {
                        mWifiUsabilityStatsListGood.remove(
                                mRand.nextInt(mWifiUsabilityStatsListGood.size()));
                    }
                    mWifiUsabilityStatsListGood.add(
                            createWifiUsabilityStatsWithLabel(label, triggerType,
                                    firmwareAlertCode));
                }
            } else {
                // Only add a bad event if at least |MIN_DATA_STALL_WAIT_MS|
                // has passed.
                mScoreBreachLowTimeMillis = -1;
                if (mWifiUsabilityStatsListBad.isEmpty()
                        || (mWifiUsabilityStatsListBad.getLast().stats[mWifiUsabilityStatsListBad
                        .getLast().stats.length - 1].timeStampMs
                        + MIN_DATA_STALL_WAIT_MS
                        < mWifiUsabilityStatsEntriesList.getLast().timeStampMs)) {
                    while (mWifiUsabilityStatsListBad.size()
                            >= MAX_WIFI_USABILITY_STATS_LIST_SIZE_PER_TYPE) {
                        mWifiUsabilityStatsListBad.remove(
                                mRand.nextInt(mWifiUsabilityStatsListBad.size()));
                    }
                    mWifiUsabilityStatsListBad.add(
                            createWifiUsabilityStatsWithLabel(label, triggerType,
                                    firmwareAlertCode));
                }
            }
            mWifiUsabilityStatsCounter = 0;
            mWifiUsabilityStatsEntriesList.clear();
        }
    }

    private DeviceMobilityStatePnoScanStats getOrCreateDeviceMobilityStatePnoScanStats(
            @DeviceMobilityState int deviceMobilityState) {
        DeviceMobilityStatePnoScanStats stats = mMobilityStatePnoStatsMap.get(deviceMobilityState);
        if (stats == null) {
            stats = new DeviceMobilityStatePnoScanStats();
            stats.deviceMobilityState = deviceMobilityState;
            stats.numTimesEnteredState = 0;
            stats.totalDurationMs = 0;
            stats.pnoDurationMs = 0;
            mMobilityStatePnoStatsMap.put(deviceMobilityState, stats);
        }
        return stats;
    }

    /**
     * Updates the current device mobility state's total duration. This method should be called
     * before entering a new device mobility state.
     */
    private void updateCurrentMobilityStateTotalDuration(long now) {
        DeviceMobilityStatePnoScanStats stats =
                getOrCreateDeviceMobilityStatePnoScanStats(mCurrentDeviceMobilityState);
        stats.totalDurationMs += now - mCurrentDeviceMobilityStateStartMs;
        mCurrentDeviceMobilityStateStartMs = now;
    }

    /**
     * Convert the IntCounter of passpoint profile types and counts to proto's
     * repeated IntKeyVal array.
     *
     * @param passpointProfileTypes passpoint profile types and counts.
     */
    private PasspointProfileTypeCount[] convertPasspointProfilesToProto(
                IntCounter passpointProfileTypes) {
        return passpointProfileTypes.toProto(PasspointProfileTypeCount.class, (key, count) -> {
            PasspointProfileTypeCount entry = new PasspointProfileTypeCount();
            entry.eapMethodType = key;
            entry.count = count;
            return entry;
        });
    }

    /**
     * Reports that the device entered a new mobility state.
     *
     * @param newState the new device mobility state.
     */
    public void enterDeviceMobilityState(@DeviceMobilityState int newState) {
        synchronized (mLock) {
            long now = mClock.getElapsedSinceBootMillis();
            updateCurrentMobilityStateTotalDuration(now);

            if (newState == mCurrentDeviceMobilityState) return;

            mCurrentDeviceMobilityState = newState;
            DeviceMobilityStatePnoScanStats stats =
                    getOrCreateDeviceMobilityStatePnoScanStats(mCurrentDeviceMobilityState);
            stats.numTimesEnteredState++;
        }
    }

    /**
     * Logs the start of a PNO scan.
     */
    public void logPnoScanStart() {
        synchronized (mLock) {
            long now = mClock.getElapsedSinceBootMillis();
            mCurrentDeviceMobilityStatePnoScanStartMs = now;
            updateCurrentMobilityStateTotalDuration(now);
        }
    }

    /**
     * Logs the end of a PNO scan. This is attributed to the current device mobility state, as
     * logged by {@link #enterDeviceMobilityState(int)}. Thus, if the mobility state changes during
     * a PNO scan, one should call {@link #logPnoScanStop()}, {@link #enterDeviceMobilityState(int)}
     * , then {@link #logPnoScanStart()} so that the portion of PNO scan before the mobility state
     * change can be correctly attributed to the previous mobility state.
     */
    public void logPnoScanStop() {
        synchronized (mLock) {
            if (mCurrentDeviceMobilityStatePnoScanStartMs < 0) {
                Log.e(TAG, "Called WifiMetrics#logPNoScanStop() without calling "
                        + "WifiMetrics#logPnoScanStart() first!");
                return;
            }
            DeviceMobilityStatePnoScanStats stats =
                    getOrCreateDeviceMobilityStatePnoScanStats(mCurrentDeviceMobilityState);
            long now = mClock.getElapsedSinceBootMillis();
            stats.pnoDurationMs += now - mCurrentDeviceMobilityStatePnoScanStartMs;
            mCurrentDeviceMobilityStatePnoScanStartMs = -1;
            updateCurrentMobilityStateTotalDuration(now);
        }
    }

    /**
     * Logs that wifi bug report is taken
     */
    public void logBugReport() {
        synchronized (mLock) {
            for (ConnectionEvent connectionEvent : mCurrentConnectionEventPerIface.values()) {
                if (connectionEvent != null) {
                    connectionEvent.mConnectionEvent.automaticBugReportTaken = true;
                }
            }
        }
    }

    /**
     * Add a new listener for Wi-Fi usability stats handling.
     */
    public void addOnWifiUsabilityListener(@NonNull IOnWifiUsabilityStatsListener listener) {
        if (!mOnWifiUsabilityListeners.register(listener)) {
            Log.e(TAG, "Failed to add listener");
            return;
        }
        if (DBG) {
            Log.v(TAG, "Adding listener. Num listeners: "
                    + mOnWifiUsabilityListeners.getRegisteredCallbackCount());
        }
    }

    /**
     * Remove an existing listener for Wi-Fi usability stats handling.
     */
    public void removeOnWifiUsabilityListener(@NonNull IOnWifiUsabilityStatsListener listener) {
        mOnWifiUsabilityListeners.unregister(listener);
        if (DBG) {
            Log.v(TAG, "Removing listener. Num listeners: "
                    + mOnWifiUsabilityListeners.getRegisteredCallbackCount());
        }
    }

    /**
     * Updates the Wi-Fi usability score and increments occurence of a particular Wifi usability
     * score passed in from outside framework. Scores are bounded within
     * [MIN_WIFI_USABILITY_SCORE, MAX_WIFI_USABILITY_SCORE].
     *
     * Also records events when the Wifi usability score breaches significant thresholds.
     *
     * @param seqNum Sequence number of the Wi-Fi usability score.
     * @param score The Wi-Fi usability score.
     * @param predictionHorizonSec Prediction horizon of the Wi-Fi usability score.
     */
    public void incrementWifiUsabilityScoreCount(String ifaceName, int seqNum, int score,
            int predictionHorizonSec) {
        if (score < MIN_WIFI_USABILITY_SCORE || score > MAX_WIFI_USABILITY_SCORE) {
            return;
        }
        synchronized (mLock) {
            mSeqNumToFramework = seqNum;
            mLastWifiUsabilityScore = score;
            mLastWifiUsabilityScoreNoReset = score;
            mWifiUsabilityScoreCounts.put(score, mWifiUsabilityScoreCounts.get(score) + 1);
            mLastPredictionHorizonSec = predictionHorizonSec;
            mLastPredictionHorizonSecNoReset = predictionHorizonSec;

            boolean wifiWins = mWifiWinsUsabilityScore;
            if (score > LOW_WIFI_USABILITY_SCORE) {
                wifiWins = true;
            } else if (score < LOW_WIFI_USABILITY_SCORE) {
                wifiWins = false;
            }

            if (wifiWins != mWifiWinsUsabilityScore) {
                mWifiWinsUsabilityScore = wifiWins;
                StaEvent event = new StaEvent();
                event.type = StaEvent.TYPE_WIFI_USABILITY_SCORE_BREACH;
                addStaEvent(ifaceName, event);
                // Only record the first score breach by checking whether mScoreBreachLowTimeMillis
                // has been set to -1
                if (!wifiWins && mScoreBreachLowTimeMillis == -1) {
                    mScoreBreachLowTimeMillis = mClock.getElapsedSinceBootMillis();
                }
            }
        }
    }

    /**
     * Reports stats for a successful link probe.
     *
     * @param timeSinceLastTxSuccessMs At {@code startTimestampMs}, the number of milliseconds since
     *                                 the last Tx success (according to
     *                                 {@link WifiInfo#txSuccess}).
     * @param rssi The Rx RSSI at {@code startTimestampMs}.
     * @param linkSpeed The Tx link speed in Mbps at {@code startTimestampMs}.
     * @param elapsedTimeMs The number of milliseconds between when the command to transmit the
     *                      probe was sent to the driver and when the driver responded that the
     *                      probe was ACKed. Note: this number should be correlated with the number
     *                      of retries that the driver attempted before the probe was ACKed.
     */
    public void logLinkProbeSuccess(String ifaceName, long timeSinceLastTxSuccessMs,
            int rssi, int linkSpeed, int elapsedTimeMs) {
        synchronized (mLock) {
            mProbeStatusSinceLastUpdate =
                    android.net.wifi.WifiUsabilityStatsEntry.PROBE_STATUS_SUCCESS;
            mProbeElapsedTimeSinceLastUpdateMs = elapsedTimeMs;

            mLinkProbeSuccessSecondsSinceLastTxSuccessHistogram.increment(
                    (int) (timeSinceLastTxSuccessMs / 1000));
            mLinkProbeSuccessRssiCounts.increment(rssi);
            mLinkProbeSuccessLinkSpeedCounts.increment(linkSpeed);
            mLinkProbeSuccessElapsedTimeMsHistogram.increment(elapsedTimeMs);

            if (mLinkProbeStaEventCount < MAX_LINK_PROBE_STA_EVENTS) {
                StaEvent event = new StaEvent();
                event.type = StaEvent.TYPE_LINK_PROBE;
                event.linkProbeWasSuccess = true;
                event.linkProbeSuccessElapsedTimeMs = elapsedTimeMs;
                addStaEvent(ifaceName, event);
            }
            mLinkProbeStaEventCount++;
        }
    }

    /**
     * Reports stats for an unsuccessful link probe.
     *
     * @param timeSinceLastTxSuccessMs At {@code startTimestampMs}, the number of milliseconds since
     *                                 the last Tx success (according to
     *                                 {@link WifiInfo#txSuccess}).
     * @param rssi The Rx RSSI at {@code startTimestampMs}.
     * @param linkSpeed The Tx link speed in Mbps at {@code startTimestampMs}.
     * @param reason The error code for the failure. See
     * {@link WifiNl80211Manager.SendMgmtFrameError}.
     */
    public void logLinkProbeFailure(String ifaceName, long timeSinceLastTxSuccessMs,
            int rssi, int linkSpeed, int reason) {
        synchronized (mLock) {
            mProbeStatusSinceLastUpdate =
                    android.net.wifi.WifiUsabilityStatsEntry.PROBE_STATUS_FAILURE;
            mProbeElapsedTimeSinceLastUpdateMs = Integer.MAX_VALUE;

            mLinkProbeFailureSecondsSinceLastTxSuccessHistogram.increment(
                    (int) (timeSinceLastTxSuccessMs / 1000));
            mLinkProbeFailureRssiCounts.increment(rssi);
            mLinkProbeFailureLinkSpeedCounts.increment(linkSpeed);
            mLinkProbeFailureReasonCounts.increment(reason);

            if (mLinkProbeStaEventCount < MAX_LINK_PROBE_STA_EVENTS) {
                StaEvent event = new StaEvent();
                event.type = StaEvent.TYPE_LINK_PROBE;
                event.linkProbeWasSuccess = false;
                event.linkProbeFailureReason = linkProbeFailureReasonToProto(reason);
                addStaEvent(ifaceName, event);
            }
            mLinkProbeStaEventCount++;
        }
    }

    /**
     * Increments the number of probes triggered by the experiment `experimentId`.
     */
    public void incrementLinkProbeExperimentProbeCount(String experimentId) {
        synchronized (mLock) {
            mLinkProbeExperimentProbeCounts.increment(experimentId);
        }
    }

    /**
     * Update wifi config store read duration.
     *
     * @param timeMs Time it took to complete the operation, in milliseconds
     */
    public void noteWifiConfigStoreReadDuration(int timeMs) {
        synchronized (mLock) {
            MetricsUtils.addValueToLinearHistogram(timeMs, mWifiConfigStoreReadDurationHistogram,
                    WIFI_CONFIG_STORE_IO_DURATION_BUCKET_RANGES_MS);
        }
    }

    /**
     * Update wifi config store write duration.
     *
     * @param timeMs Time it took to complete the operation, in milliseconds
     */
    public void noteWifiConfigStoreWriteDuration(int timeMs) {
        synchronized (mLock) {
            MetricsUtils.addValueToLinearHistogram(timeMs, mWifiConfigStoreWriteDurationHistogram,
                    WIFI_CONFIG_STORE_IO_DURATION_BUCKET_RANGES_MS);
        }
    }

    /**
     * Logs the decision of a network selection algorithm when compared against another network
     * selection algorithm.
     *
     * @param experiment1Id ID of one experiment
     * @param experiment2Id ID of the other experiment
     * @param isSameDecision did the 2 experiments make the same decision?
     * @param numNetworkChoices the number of non-null network choices there were, where the null
     *                          choice is not selecting any network
     */
    public void logNetworkSelectionDecision(int experiment1Id, int experiment2Id,
            boolean isSameDecision, int numNetworkChoices) {
        if (numNetworkChoices < 0) {
            Log.e(TAG, "numNetworkChoices cannot be negative!");
            return;
        }
        if (experiment1Id == experiment2Id) {
            Log.e(TAG, "comparing the same experiment id: " + experiment1Id);
            return;
        }

        Pair<Integer, Integer> key = new Pair<>(experiment1Id, experiment2Id);
        synchronized (mLock) {
            NetworkSelectionExperimentResults results =
                    mNetworkSelectionExperimentPairNumChoicesCounts
                            .computeIfAbsent(key, k -> new NetworkSelectionExperimentResults());

            IntCounter counter = isSameDecision
                    ? results.sameSelectionNumChoicesCounter
                    : results.differentSelectionNumChoicesCounter;

            counter.increment(numNetworkChoices);
        }
    }

    /** Increment number of network request API usage stats */
    public void incrementNetworkRequestApiNumRequest() {
        synchronized (mLock) {
            mWifiNetworkRequestApiLog.numRequest++;
        }
    }

    /** Add to the network request API match size histogram */
    public void incrementNetworkRequestApiMatchSizeHistogram(int matchSize) {
        synchronized (mLock) {
            mWifiNetworkRequestApiMatchSizeHistogram.increment(matchSize);
        }
    }

    /** Increment number of connection success on primary iface via network request API */
    public void incrementNetworkRequestApiNumConnectSuccessOnPrimaryIface() {
        synchronized (mLock) {
            mWifiNetworkRequestApiLog.numConnectSuccessOnPrimaryIface++;
        }
    }

    /** Increment number of requests that bypassed user approval via network request API */
    public void incrementNetworkRequestApiNumUserApprovalBypass() {
        synchronized (mLock) {
            mWifiNetworkRequestApiLog.numUserApprovalBypass++;
        }
    }

    /** Increment number of requests that user rejected via network request API */
    public void incrementNetworkRequestApiNumUserReject() {
        synchronized (mLock) {
            mWifiNetworkRequestApiLog.numUserReject++;
        }
    }

    /** Increment number of requests from unique apps via network request API */
    public void incrementNetworkRequestApiNumApps() {
        synchronized (mLock) {
            mWifiNetworkRequestApiLog.numApps++;
        }
    }

    /** Add to the network request API connection duration histogram */
    public void incrementNetworkRequestApiConnectionDurationSecOnPrimaryIfaceHistogram(
            int durationSec) {
        synchronized (mLock) {
            mWifiNetworkRequestApiConnectionDurationSecOnPrimaryIfaceHistogram.increment(
                    durationSec);
        }
    }

    /** Add to the network request API connection duration on secondary iface histogram */
    public void incrementNetworkRequestApiConnectionDurationSecOnSecondaryIfaceHistogram(
            int durationSec) {
        synchronized (mLock) {
            mWifiNetworkRequestApiConnectionDurationSecOnSecondaryIfaceHistogram.increment(
                    durationSec);
        }
    }

    /** Increment number of connection on primary iface via network request API */
    public void incrementNetworkRequestApiNumConnectOnPrimaryIface() {
        synchronized (mLock) {
            mWifiNetworkRequestApiLog.numConnectOnPrimaryIface++;
        }
    }

    /** Increment number of connection on secondary iface via network request API */
    public void incrementNetworkRequestApiNumConnectOnSecondaryIface() {
        synchronized (mLock) {
            mWifiNetworkRequestApiLog.numConnectOnSecondaryIface++;
        }
    }

    /** Increment number of connection success on secondary iface via network request API */
    public void incrementNetworkRequestApiNumConnectSuccessOnSecondaryIface() {
        synchronized (mLock) {
            mWifiNetworkRequestApiLog.numConnectSuccessOnSecondaryIface++;
        }
    }

    /** Increment number of concurrent connection via network request API */
    public void incrementNetworkRequestApiNumConcurrentConnection() {
        synchronized (mLock) {
            mWifiNetworkRequestApiLog.numConcurrentConnection++;
        }
    }

    /** Add to the network request API concurrent connection duration histogram */
    public void incrementNetworkRequestApiConcurrentConnectionDurationSecHistogram(
            int durationSec) {
        synchronized (mLock) {
            mWifiNetworkRequestApiConcurrentConnectionDurationSecHistogram.increment(
                    durationSec);
        }
    }

    /** Increment number of network suggestion API modification by app stats */
    public void incrementNetworkSuggestionApiNumModification() {
        synchronized (mLock) {
            mWifiNetworkSuggestionApiLog.numModification++;
        }
    }

    /** Increment number of connection success via network suggestion API */
    public void incrementNetworkSuggestionApiNumConnectSuccess() {
        synchronized (mLock) {
            mWifiNetworkSuggestionApiLog.numConnectSuccess++;
        }
    }

    /** Increment number of connection failure via network suggestion API */
    public void incrementNetworkSuggestionApiNumConnectFailure() {
        synchronized (mLock) {
            mWifiNetworkSuggestionApiLog.numConnectFailure++;
        }
    }

    /** Increment number of user revoke suggestion permission. Including from settings or
     * disallowed from UI.
     */
    public void incrementNetworkSuggestionUserRevokePermission() {
        synchronized (mLock) {
            mWifiNetworkSuggestionApiLog.userRevokeAppSuggestionPermission++;
        }
    }

    /**
     * Increment number of times a ScanResult matches more than one WifiNetworkSuggestion.
     */
    public void incrementNetworkSuggestionMoreThanOneSuggestionForSingleScanResult() {
        synchronized (mLock) {
            mWifiNetworkSuggestionApiLog.numMultipleSuggestions++;
        }
    }

    /**
     * Add a saved network which has at least has one suggestion for same network on the device.
     */
    public void addSuggestionExistsForSavedNetwork(String key) {
        synchronized (mLock) {
            mWifiNetworkSuggestionCoexistSavedNetworks.add(key);
        }
    }

    /**
     * Add a priority group which is using on the device.(Except default priority group).
     */
    public void addNetworkSuggestionPriorityGroup(int priorityGroup) {
        synchronized (mLock) {
            // Ignore the default group
            if (priorityGroup == 0) {
                return;
            }
            mWifiNetworkSuggestionPriorityGroups.put(priorityGroup, true);
        }

    }

    /** Clear and set the latest network suggestion API max list size histogram */
    public void noteNetworkSuggestionApiListSizeHistogram(List<Integer> listSizes) {
        synchronized (mLock) {
            mWifiNetworkSuggestionApiListSizeHistogram.clear();
            for (Integer listSize : listSizes) {
                mWifiNetworkSuggestionApiListSizeHistogram.increment(listSize);
            }
        }
    }

    /** Increment number of app add suggestion with different privilege */
    public void incrementNetworkSuggestionApiUsageNumOfAppInType(int appType) {
        int typeCode;
        synchronized (mLock) {
            switch (appType) {
                case WifiNetworkSuggestionsManager.APP_TYPE_CARRIER_PRIVILEGED:
                    typeCode = WifiNetworkSuggestionApiLog.TYPE_CARRIER_PRIVILEGED;
                    break;
                case WifiNetworkSuggestionsManager.APP_TYPE_NETWORK_PROVISIONING:
                    typeCode = WifiNetworkSuggestionApiLog.TYPE_NETWORK_PROVISIONING;
                    break;
                case WifiNetworkSuggestionsManager.APP_TYPE_NON_PRIVILEGED:
                    typeCode = WifiNetworkSuggestionApiLog.TYPE_NON_PRIVILEGED;
                    break;
                default:
                    typeCode = WifiNetworkSuggestionApiLog.TYPE_UNKNOWN;
            }
            mWifiNetworkSuggestionApiAppTypeCounter.increment(typeCode);
        }
    }

    /** Add user action to the approval suggestion app UI */
    public void addUserApprovalSuggestionAppUiReaction(@WifiNetworkSuggestionsManager.UserActionCode
            int actionType, boolean isDialog) {
        int actionCode;
        switch (actionType) {
            case WifiNetworkSuggestionsManager.ACTION_USER_ALLOWED_APP:
                actionCode = UserReactionToApprovalUiEvent.ACTION_ALLOWED;
                break;
            case WifiNetworkSuggestionsManager.ACTION_USER_DISALLOWED_APP:
                actionCode = UserReactionToApprovalUiEvent.ACTION_DISALLOWED;
                break;
            case WifiNetworkSuggestionsManager.ACTION_USER_DISMISS:
                actionCode = UserReactionToApprovalUiEvent.ACTION_DISMISS;
                break;
            default:
                actionCode = UserReactionToApprovalUiEvent.ACTION_UNKNOWN;
        }
        UserReaction event = new UserReaction();
        event.userAction = actionCode;
        event.isDialog = isDialog;
        synchronized (mLock) {
            mUserApprovalSuggestionAppUiReactionList.add(event);
        }
    }

    /** Add user action to the approval Carrier Imsi protection exemption UI */
    public void addUserApprovalCarrierUiReaction(@WifiCarrierInfoManager.UserActionCode
            int actionType, boolean isDialog) {
        int actionCode;
        switch (actionType) {
            case WifiCarrierInfoManager.ACTION_USER_ALLOWED_CARRIER:
                actionCode = UserReactionToApprovalUiEvent.ACTION_ALLOWED;
                break;
            case WifiCarrierInfoManager.ACTION_USER_DISALLOWED_CARRIER:
                actionCode = UserReactionToApprovalUiEvent.ACTION_DISALLOWED;
                break;
            case WifiCarrierInfoManager.ACTION_USER_DISMISS:
                actionCode = UserReactionToApprovalUiEvent.ACTION_DISMISS;
                break;
            default:
                actionCode = UserReactionToApprovalUiEvent.ACTION_UNKNOWN;
        }
        UserReaction event = new UserReaction();
        event.userAction = actionCode;
        event.isDialog = isDialog;

        synchronized (mLock) {
            mUserApprovalCarrierUiReactionList.add(event);
        }
    }

    /**
     * Sets the nominator for a network (i.e. which entity made the suggestion to connect)
     * @param networkId the ID of the network, from its {@link WifiConfiguration}
     * @param nominatorId the entity that made the suggestion to connect to this network,
     *                    from {@link WifiMetricsProto.ConnectionEvent.ConnectionNominator}
     */
    public void setNominatorForNetwork(int networkId, int nominatorId) {
        synchronized (mLock) {
            if (networkId == WifiConfiguration.INVALID_NETWORK_ID) return;
            mNetworkIdToNominatorId.put(networkId, nominatorId);

            // user connect choice is preventing switching off from the connected network
            if (nominatorId
                    == WifiMetricsProto.ConnectionEvent.NOMINATOR_SAVED_USER_CONNECT_CHOICE
                    && mWifiStatusBuilder.getNetworkId() == networkId) {
                mWifiStatusBuilder.setUserChoice(true);
            }
        }
    }

    /**
     * Sets the numeric CandidateScorer id.
     */
    public void setNetworkSelectorExperimentId(int expId) {
        synchronized (mLock) {
            mNetworkSelectorExperimentId = expId;
        }
    }

    /** Add a WifiLock acquisition session */
    public void addWifiLockAcqSession(int lockType, int[] attrUids, String[] attrTags,
            int callerType, long duration, boolean isPowersaveDisableAllowed,
            boolean isAppExemptedFromScreenOn, boolean isAppExemptedFromForeground) {
        int lockMode;
        switch (lockType) {
            case WifiManager.WIFI_MODE_FULL_HIGH_PERF:
                mWifiLockHighPerfAcqDurationSecHistogram.increment((int) (duration / 1000));
                lockMode = WifiStatsLog.WIFI_LOCK_RELEASED__MODE__WIFI_MODE_FULL_HIGH_PERF;
                break;

            case WifiManager.WIFI_MODE_FULL_LOW_LATENCY:
                mWifiLockLowLatencyAcqDurationSecHistogram.increment((int) (duration / 1000));
                lockMode = WifiStatsLog.WIFI_LOCK_RELEASED__MODE__WIFI_MODE_FULL_LOW_LATENCY;
                break;
            default:
                Log.e(TAG, "addWifiLockAcqSession: Invalid lock type: " + lockType);
                return;
        }
        WifiStatsLog.write(WifiStatsLog.WIFI_LOCK_RELEASED,
                attrUids,
                attrTags,
                callerType,
                lockMode,
                duration,
                isPowersaveDisableAllowed,
                isAppExemptedFromScreenOn,
                isAppExemptedFromForeground);
    }

    /** Add a WifiLock active session */
    public void addWifiLockActiveSession(int lockType, int[] attrUids, String[] attrTags,
            long duration, boolean isPowersaveDisableAllowed,
            boolean isAppExemptedFromScreenOn, boolean isAppExemptedFromForeground) {
        int lockMode;
        switch (lockType) {
            case WifiManager.WIFI_MODE_FULL_HIGH_PERF:
                lockMode = WifiStatsLog.WIFI_LOCK_DEACTIVATED__MODE__WIFI_MODE_FULL_HIGH_PERF;
                mWifiLockStats.highPerfActiveTimeMs += duration;
                mWifiLockHighPerfActiveSessionDurationSecHistogram.increment(
                        (int) (duration / 1000));
                break;

            case WifiManager.WIFI_MODE_FULL_LOW_LATENCY:
                lockMode = WifiStatsLog.WIFI_LOCK_DEACTIVATED__MODE__WIFI_MODE_FULL_LOW_LATENCY;
                mWifiLockStats.lowLatencyActiveTimeMs += duration;
                mWifiLockLowLatencyActiveSessionDurationSecHistogram.increment(
                        (int) (duration / 1000));
                break;

            default:
                Log.e(TAG, "addWifiLockActiveSession: Invalid lock type: " + lockType);
                return;
        }
        WifiStatsLog.write(WifiStatsLog.WIFI_LOCK_DEACTIVATED,
                attrUids,
                attrTags,
                lockMode,
                duration,
                isPowersaveDisableAllowed,
                isAppExemptedFromScreenOn,
                isAppExemptedFromForeground);
    }

    /** Increments metrics counting number of addOrUpdateNetwork calls. **/
    public void incrementNumAddOrUpdateNetworkCalls() {
        synchronized (mLock) {
            mWifiLogProto.numAddOrUpdateNetworkCalls++;
        }
    }

    /** Increments metrics counting number of enableNetwork calls. **/
    public void incrementNumEnableNetworkCalls() {
        synchronized (mLock) {
            mWifiLogProto.numEnableNetworkCalls++;
        }
    }

    /** Add to WifiToggleStats **/
    public void incrementNumWifiToggles(boolean isPrivileged, boolean enable) {
        synchronized (mLock) {
            if (isPrivileged && enable) {
                mWifiToggleStats.numToggleOnPrivileged++;
            } else if (isPrivileged && !enable) {
                mWifiToggleStats.numToggleOffPrivileged++;
            } else if (!isPrivileged && enable) {
                mWifiToggleStats.numToggleOnNormal++;
            } else {
                mWifiToggleStats.numToggleOffNormal++;
            }
        }
    }

    /**
     * Increment number of passpoint provision failure
     * @param failureCode indicates error condition
     */
    public void incrementPasspointProvisionFailure(@OsuFailure int failureCode) {
        int provisionFailureCode;
        synchronized (mLock) {
            switch (failureCode) {
                case ProvisioningCallback.OSU_FAILURE_AP_CONNECTION:
                    provisionFailureCode = PasspointProvisionStats.OSU_FAILURE_AP_CONNECTION;
                    break;
                case ProvisioningCallback.OSU_FAILURE_SERVER_URL_INVALID:
                    provisionFailureCode = PasspointProvisionStats.OSU_FAILURE_SERVER_URL_INVALID;
                    break;
                case ProvisioningCallback.OSU_FAILURE_SERVER_CONNECTION:
                    provisionFailureCode = PasspointProvisionStats.OSU_FAILURE_SERVER_CONNECTION;
                    break;
                case ProvisioningCallback.OSU_FAILURE_SERVER_VALIDATION:
                    provisionFailureCode = PasspointProvisionStats.OSU_FAILURE_SERVER_VALIDATION;
                    break;
                case ProvisioningCallback.OSU_FAILURE_SERVICE_PROVIDER_VERIFICATION:
                    provisionFailureCode = PasspointProvisionStats
                            .OSU_FAILURE_SERVICE_PROVIDER_VERIFICATION;
                    break;
                case ProvisioningCallback.OSU_FAILURE_PROVISIONING_ABORTED:
                    provisionFailureCode = PasspointProvisionStats.OSU_FAILURE_PROVISIONING_ABORTED;
                    break;
                case ProvisioningCallback.OSU_FAILURE_PROVISIONING_NOT_AVAILABLE:
                    provisionFailureCode = PasspointProvisionStats
                            .OSU_FAILURE_PROVISIONING_NOT_AVAILABLE;
                    break;
                case ProvisioningCallback.OSU_FAILURE_INVALID_URL_FORMAT_FOR_OSU:
                    provisionFailureCode = PasspointProvisionStats
                            .OSU_FAILURE_INVALID_URL_FORMAT_FOR_OSU;
                    break;
                case ProvisioningCallback.OSU_FAILURE_UNEXPECTED_COMMAND_TYPE:
                    provisionFailureCode = PasspointProvisionStats
                            .OSU_FAILURE_UNEXPECTED_COMMAND_TYPE;
                    break;
                case ProvisioningCallback.OSU_FAILURE_UNEXPECTED_SOAP_MESSAGE_TYPE:
                    provisionFailureCode = PasspointProvisionStats
                            .OSU_FAILURE_UNEXPECTED_SOAP_MESSAGE_TYPE;
                    break;
                case ProvisioningCallback.OSU_FAILURE_SOAP_MESSAGE_EXCHANGE:
                    provisionFailureCode = PasspointProvisionStats
                            .OSU_FAILURE_SOAP_MESSAGE_EXCHANGE;
                    break;
                case ProvisioningCallback.OSU_FAILURE_START_REDIRECT_LISTENER:
                    provisionFailureCode = PasspointProvisionStats
                            .OSU_FAILURE_START_REDIRECT_LISTENER;
                    break;
                case ProvisioningCallback.OSU_FAILURE_TIMED_OUT_REDIRECT_LISTENER:
                    provisionFailureCode = PasspointProvisionStats
                            .OSU_FAILURE_TIMED_OUT_REDIRECT_LISTENER;
                    break;
                case ProvisioningCallback.OSU_FAILURE_NO_OSU_ACTIVITY_FOUND:
                    provisionFailureCode = PasspointProvisionStats
                            .OSU_FAILURE_NO_OSU_ACTIVITY_FOUND;
                    break;
                case ProvisioningCallback.OSU_FAILURE_UNEXPECTED_SOAP_MESSAGE_STATUS:
                    provisionFailureCode = PasspointProvisionStats
                            .OSU_FAILURE_UNEXPECTED_SOAP_MESSAGE_STATUS;
                    break;
                case ProvisioningCallback.OSU_FAILURE_NO_PPS_MO:
                    provisionFailureCode = PasspointProvisionStats.OSU_FAILURE_NO_PPS_MO;
                    break;
                case ProvisioningCallback.OSU_FAILURE_NO_AAA_SERVER_TRUST_ROOT_NODE:
                    provisionFailureCode = PasspointProvisionStats
                            .OSU_FAILURE_NO_AAA_SERVER_TRUST_ROOT_NODE;
                    break;
                case ProvisioningCallback.OSU_FAILURE_NO_REMEDIATION_SERVER_TRUST_ROOT_NODE:
                    provisionFailureCode = PasspointProvisionStats
                            .OSU_FAILURE_NO_REMEDIATION_SERVER_TRUST_ROOT_NODE;
                    break;
                case ProvisioningCallback.OSU_FAILURE_NO_POLICY_SERVER_TRUST_ROOT_NODE:
                    provisionFailureCode = PasspointProvisionStats
                            .OSU_FAILURE_NO_POLICY_SERVER_TRUST_ROOT_NODE;
                    break;
                case ProvisioningCallback.OSU_FAILURE_RETRIEVE_TRUST_ROOT_CERTIFICATES:
                    provisionFailureCode = PasspointProvisionStats
                            .OSU_FAILURE_RETRIEVE_TRUST_ROOT_CERTIFICATES;
                    break;
                case ProvisioningCallback.OSU_FAILURE_NO_AAA_TRUST_ROOT_CERTIFICATE:
                    provisionFailureCode = PasspointProvisionStats
                            .OSU_FAILURE_NO_AAA_TRUST_ROOT_CERTIFICATE;
                    break;
                case ProvisioningCallback.OSU_FAILURE_ADD_PASSPOINT_CONFIGURATION:
                    provisionFailureCode = PasspointProvisionStats
                            .OSU_FAILURE_ADD_PASSPOINT_CONFIGURATION;
                    break;
                case ProvisioningCallback.OSU_FAILURE_OSU_PROVIDER_NOT_FOUND:
                    provisionFailureCode = PasspointProvisionStats
                            .OSU_FAILURE_OSU_PROVIDER_NOT_FOUND;
                    break;
                default:
                    provisionFailureCode = PasspointProvisionStats.OSU_FAILURE_UNKNOWN;
            }
            mPasspointProvisionFailureCounts.increment(provisionFailureCode);
        }
    }

    /**
     * Add to the histogram of number of BSSIDs filtered out from network selection.
     */
    public void incrementNetworkSelectionFilteredBssidCount(int numBssid) {
        mBssidBlocklistStats.networkSelectionFilteredBssidCount.increment(numBssid);
    }

    /**
     * Increment the number of network connections skipped due to the high movement feature.
     */
    public void incrementNumHighMovementConnectionSkipped() {
        mBssidBlocklistStats.numHighMovementConnectionSkipped++;
    }

    /**
     * Increment the number of network connections initiated while under the high movement
     * feature.
     */
    public void incrementNumHighMovementConnectionStarted() {
        mBssidBlocklistStats.numHighMovementConnectionStarted++;
    }

    /**
     * Increment the number of times BSSIDs are blocked per reason.
     * @param blockReason one of {@link WifiBlocklistMonitor.FailureReason}
     */
    public void incrementBssidBlocklistCount(int blockReason) {
        mBssidBlocklistStats.incrementBssidBlocklistCount(blockReason);
    }

    /**
     * Increment the number of times WifiConfigurations are blocked per reason.
     * @param blockReason one of {@Link NetworkSelectionStatus.NetworkSelectionDisableReason}
     */
    public void incrementWificonfigurationBlocklistCount(int blockReason) {
        mBssidBlocklistStats.incrementWificonfigurationBlocklistCount(blockReason);
    }

    /**
     * Increment number of passpoint provision success
     */
    public void incrementPasspointProvisionSuccess() {
        synchronized (mLock) {
            mNumProvisionSuccess++;
        }
    }

    /**
     * Increment number of IP renewal failures.
     */
    public void incrementIpRenewalFailure() {
        synchronized (mLock) {
            mWifiLogProto.numIpRenewalFailure++;
        }
    }

    /**
     * Sets the duration for evaluating Wifi condition to trigger a data stall
     */
    public void setDataStallDurationMs(int duration) {
        synchronized (mLock) {
            mExperimentValues.dataStallDurationMs = duration;
        }
    }

    /**
     * Sets the threshold of Tx throughput below which to trigger a data stall
     */
    public void setDataStallTxTputThrKbps(int txTputThr) {
        synchronized (mLock) {
            mExperimentValues.dataStallTxTputThrKbps = txTputThr;
        }
    }

    /**
     * Sets the threshold of Rx throughput below which to trigger a data stall
     */
    public void setDataStallRxTputThrKbps(int rxTputThr) {
        synchronized (mLock) {
            mExperimentValues.dataStallRxTputThrKbps = rxTputThr;
        }
    }

    /**
     * Sets the threshold of Tx packet error rate above which to trigger a data stall
     */
    public void setDataStallTxPerThr(int txPerThr) {
        synchronized (mLock) {
            mExperimentValues.dataStallTxPerThr = txPerThr;
        }
    }

    /**
     * Sets the threshold of CCA level above which to trigger a data stall
     */
    public void setDataStallCcaLevelThr(int ccaLevel) {
        synchronized (mLock) {
            mExperimentValues.dataStallCcaLevelThr = ccaLevel;
        }
    }

    /**
     * Sets health monitor RSSI poll valid time in ms
     */
    public void setHealthMonitorRssiPollValidTimeMs(int rssiPollValidTimeMs) {
        synchronized (mLock) {
            mExperimentValues.healthMonitorRssiPollValidTimeMs = rssiPollValidTimeMs;
        }
    }

    /**
     * Increment connection duration while link layer stats report are on
     */
    public void incrementConnectionDuration(String ifaceName, int timeDeltaLastTwoPollsMs,
            boolean isThroughputSufficient, boolean isCellularDataAvailable, int rssi, int txKbps,
            int rxKbps) {
        synchronized (mLock) {
            if (!isPrimary(ifaceName)) {
                return;
            }
            mConnectionDurationStats.incrementDurationCount(timeDeltaLastTwoPollsMs,
                    isThroughputSufficient, isCellularDataAvailable, mWifiWins);
            WifiUsabilityState wifiUsabilityState = mWifiUsabilityStatePerIface.getOrDefault(
                    ifaceName, WifiUsabilityState.UNKNOWN);
            int band = KnownBandsChannelHelper.getBand(mLastPollFreq);
            WifiStatsLog.write(WifiStatsLog.WIFI_HEALTH_STAT_REPORTED, timeDeltaLastTwoPollsMs,
                    isThroughputSufficient || !mWifiWins, isCellularDataAvailable, band, rssi,
                    txKbps, rxKbps, mScorerUid, (wifiUsabilityState == WifiUsabilityState.USABLE),
                    convertWifiUsabilityState(wifiUsabilityState));
        }
    }

    /**
     * Sets the status to indicate whether external WiFi connected network scorer is present or not.
     */
    public void setIsExternalWifiScorerOn(boolean value, int callerUid) {
        synchronized (mLock) {
            mWifiLogProto.isExternalWifiScorerOn = value;
            mScorerUid = callerUid;
        }
    }

    /**
     * Note Wi-Fi off metrics
     */
    public void noteWifiOff(boolean isDeferred, boolean isTimeout, int duration) {
        synchronized (mLock) {
            mWifiOffMetrics.numWifiOff++;
            if (isDeferred) {
                mWifiOffMetrics.numWifiOffDeferring++;
                if (isTimeout) {
                    mWifiOffMetrics.numWifiOffDeferringTimeout++;
                }
                mWifiOffMetrics.wifiOffDeferringTimeHistogram.increment(duration);
            }
        }
    }

    /**
     * Increment number of BSSIDs filtered out from network selection due to MBO Association
     * disallowed indication.
     */
    public void incrementNetworkSelectionFilteredBssidCountDueToMboAssocDisallowInd() {
        synchronized (mLock) {
            mWifiLogProto.numBssidFilteredDueToMboAssocDisallowInd++;
        }
    }

    /**
     * Increment number of times BSS transition management request frame is received from the AP.
     */
    public void incrementSteeringRequestCount() {
        synchronized (mLock) {
            mWifiLogProto.numSteeringRequest++;
        }
    }

    /**
     * Increment number of times force scan is triggered due to a
     * BSS transition management request frame from AP.
     */
    public void incrementForceScanCountDueToSteeringRequest() {
        synchronized (mLock) {
            mWifiLogProto.numForceScanDueToSteeringRequest++;
        }
    }

    /**
     * Increment number of times STA received cellular switch
     * request from MBO supported AP.
     */
    public void incrementMboCellularSwitchRequestCount() {
        synchronized (mLock) {
            mWifiLogProto.numMboCellularSwitchRequest++;
        }
    }

    /**
     * Increment number of times STA received steering request
     * including MBO association retry delay.
     */
    public void incrementSteeringRequestCountIncludingMboAssocRetryDelay() {
        synchronized (mLock) {
            mWifiLogProto.numSteeringRequestIncludingMboAssocRetryDelay++;
        }
    }

    /**
     * Increment number of connect request to AP adding FILS AKM.
     */
    public void incrementConnectRequestWithFilsAkmCount() {
        synchronized (mLock) {
            mWifiLogProto.numConnectRequestWithFilsAkm++;
        }
    }

    /**
     * Increment number of times STA connected through FILS
     * authentication.
     */
    public void incrementL2ConnectionThroughFilsAuthCount() {
        synchronized (mLock) {
            mWifiLogProto.numL2ConnectionThroughFilsAuthentication++;
        }
    }

    /**
     * Note SoftapConfig Reset Metrics
     */
    public void noteSoftApConfigReset(SoftApConfiguration originalConfig,
            SoftApConfiguration newConfig) {
        synchronized (mLock) {
            if (originalConfig.getSecurityType() != newConfig.getSecurityType()) {
                mSoftApConfigLimitationMetrics.numSecurityTypeResetToDefault++;
            }
            if (originalConfig.getMaxNumberOfClients() != newConfig.getMaxNumberOfClients()) {
                mSoftApConfigLimitationMetrics.numMaxClientSettingResetToDefault++;
            }
            if (originalConfig.isClientControlByUserEnabled()
                    != newConfig.isClientControlByUserEnabled()) {
                mSoftApConfigLimitationMetrics.numClientControlByUserResetToDefault++;
            }
        }
    }

    /**
     * Note Softap client blocked due to max client limitation
     */
    public void noteSoftApClientBlocked(int maxClient) {
        mSoftApConfigLimitationMetrics.maxClientSettingWhenReachHistogram.increment(maxClient);
    }

    /**
     * Increment number of connection with different BSSID between framework and firmware selection.
     */
    public void incrementNumBssidDifferentSelectionBetweenFrameworkAndFirmware() {
        synchronized (mLock) {
            mWifiLogProto.numBssidDifferentSelectionBetweenFrameworkAndFirmware++;
        }
    }

    /**
     * Note the carrier wifi network connected successfully.
     */
    public void incrementNumOfCarrierWifiConnectionSuccess() {
        synchronized (mLock) {
            mCarrierWifiMetrics.numConnectionSuccess++;
        }
    }

    /**
     * Note the carrier wifi network connection authentication failure.
     */
    public void incrementNumOfCarrierWifiConnectionAuthFailure() {
        synchronized (mLock) {
            mCarrierWifiMetrics.numConnectionAuthFailure++;
        }
    }

    /**
     * Note the carrier wifi network connection non-authentication failure.
     */
    public void incrementNumOfCarrierWifiConnectionNonAuthFailure() {
        synchronized (mLock) {
            mCarrierWifiMetrics.numConnectionNonAuthFailure++;
        }
    }

    /**
     *  Set Adaptive Connectivity state (On/Off)
     */
    public void setAdaptiveConnectivityState(boolean adaptiveConnectivityEnabled) {
        synchronized (mLock) {
            mAdaptiveConnectivityEnabled = adaptiveConnectivityEnabled;
        }
    }

    /**
     * Get total beacon receive count
     */
    public long getTotalBeaconRxCount() {
        return mLastTotalBeaconRx;
    }

    /** Get total beacon receive count for the link */
    public long getTotalBeaconRxCount(int linkId) {
        if (!mLastLinkMetrics.contains(linkId)) return 0;
        return mLastLinkMetrics.get(linkId).getTotalBeaconRx();
    }

    /** Get link usage state */
    public @android.net.wifi.WifiUsabilityStatsEntry.LinkState int getLinkUsageState(int linkId) {
        if (!mLastLinkMetrics.contains(linkId)) {
            return android.net.wifi.WifiUsabilityStatsEntry.LINK_STATE_UNKNOWN;
        }
        return mLastLinkMetrics.get(linkId).getLinkUsageState();
    }

    /** Note whether Wifi was enabled at boot time. */
    public void noteWifiEnabledDuringBoot(boolean isWifiEnabled) {
        synchronized (mLock) {
            if (mIsFirstConnectionAttemptComplete
                    || mFirstConnectAfterBootStats == null
                    || mFirstConnectAfterBootStats.wifiEnabledAtBoot != null) {
                return;
            }
            Attempt wifiEnabledAtBoot = new Attempt();
            wifiEnabledAtBoot.isSuccess = isWifiEnabled;
            wifiEnabledAtBoot.timestampSinceBootMillis = mClock.getElapsedSinceBootMillis();
            mFirstConnectAfterBootStats.wifiEnabledAtBoot = wifiEnabledAtBoot;
            if (!isWifiEnabled) {
                mIsFirstConnectionAttemptComplete = true;
            }
        }
    }

    /** Note the first network selection after boot. */
    public void noteFirstNetworkSelectionAfterBoot(boolean wasAnyCandidatesFound) {
        synchronized (mLock) {
            if (mIsFirstConnectionAttemptComplete
                    || mFirstConnectAfterBootStats == null
                    || mFirstConnectAfterBootStats.firstNetworkSelection != null) {
                return;
            }
            Attempt firstNetworkSelection = new Attempt();
            firstNetworkSelection.isSuccess = wasAnyCandidatesFound;
            firstNetworkSelection.timestampSinceBootMillis = mClock.getElapsedSinceBootMillis();
            mFirstConnectAfterBootStats.firstNetworkSelection = firstNetworkSelection;
            if (!wasAnyCandidatesFound) {
                mIsFirstConnectionAttemptComplete = true;
            }
        }
    }

    /** Note the first L2 connection after boot. */
    public void noteFirstL2ConnectionAfterBoot(boolean wasConnectionSuccessful) {
        synchronized (mLock) {
            if (mIsFirstConnectionAttemptComplete
                    || mFirstConnectAfterBootStats == null
                    || mFirstConnectAfterBootStats.firstL2Connection != null) {
                return;
            }
            Attempt firstL2Connection = new Attempt();
            firstL2Connection.isSuccess = wasConnectionSuccessful;
            firstL2Connection.timestampSinceBootMillis = mClock.getElapsedSinceBootMillis();
            mFirstConnectAfterBootStats.firstL2Connection = firstL2Connection;
            if (!wasConnectionSuccessful) {
                mIsFirstConnectionAttemptComplete = true;
            }
        }
    }

    /** Note the first L3 connection after boot. */
    public void noteFirstL3ConnectionAfterBoot(boolean wasConnectionSuccessful) {
        synchronized (mLock) {
            if (mIsFirstConnectionAttemptComplete
                    || mFirstConnectAfterBootStats == null
                    || mFirstConnectAfterBootStats.firstL3Connection != null) {
                return;
            }
            Attempt firstL3Connection = new Attempt();
            firstL3Connection.isSuccess = wasConnectionSuccessful;
            firstL3Connection.timestampSinceBootMillis = mClock.getElapsedSinceBootMillis();
            mFirstConnectAfterBootStats.firstL3Connection = firstL3Connection;
            if (!wasConnectionSuccessful) {
                mIsFirstConnectionAttemptComplete = true;
            }
        }
    }

    private static String attemptToString(@Nullable Attempt attempt) {
        if (attempt == null) return "Attempt=null";
        return "Attempt{"
                + "timestampSinceBootMillis=" + attempt.timestampSinceBootMillis
                + ",isSuccess=" + attempt.isSuccess
                + "}";
    }

    private static String firstConnectAfterBootStatsToString(
            @Nullable FirstConnectAfterBootStats stats) {
        if (stats == null) return "FirstConnectAfterBootStats=null";
        return "FirstConnectAfterBootStats{"
                + "wifiEnabledAtBoot=" + attemptToString(stats.wifiEnabledAtBoot)
                + ",firstNetworkSelection" + attemptToString(stats.firstNetworkSelection)
                + ",firstL2Connection" + attemptToString(stats.firstL2Connection)
                + ",firstL3Connection" + attemptToString(stats.firstL3Connection)
                + "}";
    }

    public ScanMetrics getScanMetrics() {
        return mScanMetrics;
    }

    public enum ScanType { SINGLE, BACKGROUND }

    public enum PnoScanState { STARTED, FAILED_TO_START, COMPLETED_NETWORK_FOUND, FAILED }

    /**
     * This class reports Scan metrics to statsd and holds intermediate scan request state.
     */
    public static class ScanMetrics {
        private static final String TAG_SCANS = "ScanMetrics";
        private static final String GMS_PACKAGE = "com.google.android.gms";

        // Scan types.
        public static final int SCAN_TYPE_SINGLE = 0;
        public static final int SCAN_TYPE_BACKGROUND = 1;
        public static final int SCAN_TYPE_MAX_VALUE = SCAN_TYPE_BACKGROUND;
        @IntDef(prefix = { "SCAN_TYPE_" }, value = {
                SCAN_TYPE_SINGLE,
                SCAN_TYPE_BACKGROUND,
        })
        public @interface ScanType {}

        // PNO scan states.
        public static final int PNO_SCAN_STATE_STARTED = 1;
        public static final int PNO_SCAN_STATE_FAILED_TO_START = 2;
        public static final int PNO_SCAN_STATE_COMPLETED_NETWORK_FOUND = 3;
        public static final int PNO_SCAN_STATE_FAILED = 4;
        @IntDef(prefix = { "PNO_SCAN_STATE_" }, value = {
                PNO_SCAN_STATE_STARTED,
                PNO_SCAN_STATE_FAILED_TO_START,
                PNO_SCAN_STATE_COMPLETED_NETWORK_FOUND,
                PNO_SCAN_STATE_FAILED
        })
        public @interface PnoScanState {}

        private final Object mLock = new Object();
        private Clock mClock;

        private List<String> mSettingsPackages = new ArrayList<>();
        private int mGmsUid = -1;

        // mNextScanState collects metadata about the next scan that's about to happen.
        // It is mutated by external callers via setX methods before the call to logScanStarted.
        private State mNextScanState = new State();
        // mActiveScanState is an immutable copy of mNextScanState during the scan process,
        // i.e. between logScanStarted and logScanSucceeded/Failed. Since the state is pushed to
        // statsd only when a scan ends, it's important to keep the immutable copy
        // for the duration of the scan.
        private State[] mActiveScanStates = new State[SCAN_TYPE_MAX_VALUE + 1];

        ScanMetrics(Context context, Clock clock) {
            mClock = clock;

            PackageManager pm = context.getPackageManager();
            if (pm != null) {
                Intent settingsIntent = new Intent(Settings.ACTION_SETTINGS);
                List<ResolveInfo> packages = pm.queryIntentActivities(settingsIntent, 0);
                for (ResolveInfo res : packages) {
                    String packageName = res.activityInfo.packageName;
                    Log.d(TAG_SCANS, "Settings package: " + packageName);
                    mSettingsPackages.add(packageName);
                }
            }

            try {
                mGmsUid = context.getPackageManager().getApplicationInfo(GMS_PACKAGE, 0).uid;
                Log.d(TAG_SCANS, "GMS uid: " + mGmsUid);
            } catch (Exception e) {
                Log.e(TAG_SCANS, "Can't get GMS uid");
            }
        }

        /**
         * Set WorkSource for the upcoming scan request.
         *
         * @param workSource
         */
        public void setWorkSource(WorkSource workSource) {
            synchronized (mLock) {
                if (mNextScanState.mWorkSource == null) {
                    mNextScanState.mWorkSource = workSource;
                    if (DBG) Log.d(TAG_SCANS, "setWorkSource: workSource = " + workSource);
                }
            }
        }

        /**
         * Set ClientUid for the upcoming scan request.
         *
         * @param uid
         */
        public void setClientUid(int uid) {
            synchronized (mLock) {
                mNextScanState.mClientUid = uid;

                if (DBG) Log.d(TAG_SCANS, "setClientUid: uid = " + uid);
            }
        }

        /**
         * Set Importance for the upcoming scan request.
         *
         * @param packageImportance See {@link ActivityManager.RunningAppProcessInfo.Importance}
         */
        public void setImportance(int packageImportance) {
            synchronized (mLock) {
                mNextScanState.mPackageImportance = packageImportance;

                if (DBG) {
                    Log.d(TAG_SCANS,
                            "setRequestFromBackground: packageImportance = " + packageImportance);
                }
            }
        }

        /**
         * Indicate that a scan started.
         * @param scanType See {@link ScanMetrics.ScanType}
         */
        public void logScanStarted(@ScanType int scanType) {
            synchronized (mLock) {
                if (DBG) Log.d(TAG_SCANS, "logScanStarted");

                mNextScanState.mTimeStartMillis = mClock.getElapsedSinceBootMillis();
                mActiveScanStates[scanType] = mNextScanState;
                mNextScanState = new State();
            }
        }

        /**
         * Indicate that a scan failed to start.
         * @param scanType See {@link ScanMetrics.ScanType}
         */
        public void logScanFailedToStart(@ScanType int scanType) {
            synchronized (mLock) {
                Log.d(TAG_SCANS, "logScanFailedToStart");

                mNextScanState.mTimeStartMillis = mClock.getElapsedSinceBootMillis();
                mActiveScanStates[scanType] = mNextScanState;
                mNextScanState = new State();

                log(scanType, WifiStatsLog.WIFI_SCAN_REPORTED__RESULT__RESULT_FAILED_TO_START, 0);
                mActiveScanStates[scanType] = null;
            }
        }

        /**
         * Indicate that a scan finished successfully.
         * @param scanType See {@link ScanMetrics.ScanType}
         * @param countOfNetworksFound How many networks were found.
         */
        public void logScanSucceeded(@ScanType int scanType, int countOfNetworksFound) {
            synchronized (mLock) {
                if (DBG) Log.d(TAG_SCANS, "logScanSucceeded: found = " + countOfNetworksFound);

                log(scanType, WifiStatsLog.WIFI_SCAN_REPORTED__RESULT__RESULT_SUCCESS,
                        countOfNetworksFound);
                mActiveScanStates[scanType] = null;
            }
        }

        /**
         * Log a PNO scan event: start/finish/fail.
         * @param pnoScanState See {@link PnoScanState}
         */
        public void logPnoScanEvent(@PnoScanState int pnoScanState) {
            synchronized (mLock) {
                int state = 0;

                switch (pnoScanState) {
                    case PNO_SCAN_STATE_STARTED:
                        state = WifiStatsLog.WIFI_PNO_SCAN_REPORTED__STATE__STARTED;
                        break;
                    case PNO_SCAN_STATE_FAILED_TO_START:
                        state = WifiStatsLog.WIFI_PNO_SCAN_REPORTED__STATE__FAILED_TO_START;
                        break;
                    case PNO_SCAN_STATE_COMPLETED_NETWORK_FOUND:
                        state = WifiStatsLog.WIFI_PNO_SCAN_REPORTED__STATE__FINISHED_NETWORKS_FOUND;
                        break;
                    case PNO_SCAN_STATE_FAILED:
                        state = WifiStatsLog.WIFI_PNO_SCAN_REPORTED__STATE__FAILED;
                        break;
                }

                WifiStatsLog.write(WifiStatsLog.WIFI_PNO_SCAN_REPORTED, state);

                if (DBG) Log.d(TAG_SCANS, "logPnoScanEvent: pnoScanState = " + pnoScanState);
            }
        }

        /**
         * Indicate that a scan failed.
         */
        public void logScanFailed(@ScanType int scanType) {
            synchronized (mLock) {
                if (DBG) Log.d(TAG_SCANS, "logScanFailed");

                log(scanType, WifiStatsLog.WIFI_SCAN_REPORTED__RESULT__RESULT_FAILED_TO_SCAN, 0);
                mActiveScanStates[scanType] = null;
            }
        }

        private void log(@ScanType int scanType, int result, int countNetworks) {
            State state = mActiveScanStates[scanType];

            if (state == null) {
                if (DBG) Log.e(TAG_SCANS, "Wifi scan result log called with no prior start calls!");
                return;
            }

            int type = WifiStatsLog.WIFI_SCAN_REPORTED__TYPE__TYPE_UNKNOWN;
            if (scanType == SCAN_TYPE_SINGLE) {
                type = WifiStatsLog.WIFI_SCAN_REPORTED__TYPE__TYPE_SINGLE;
            } else if (scanType == SCAN_TYPE_BACKGROUND) {
                type = WifiStatsLog.WIFI_SCAN_REPORTED__TYPE__TYPE_BACKGROUND;
            }

            long duration = mClock.getElapsedSinceBootMillis() - state.mTimeStartMillis;

            int source = WifiStatsLog.WIFI_SCAN_REPORTED__SOURCE__SOURCE_NO_WORK_SOURCE;
            if (state.mClientUid != -1 && state.mClientUid == mGmsUid) {
                source = WifiStatsLog.WIFI_SCAN_REPORTED__SOURCE__SOURCE_GMS;
            } else if (state.mWorkSource != null) {
                if (state.mWorkSource.equals(ClientModeImpl.WIFI_WORK_SOURCE)) {
                    source = WifiStatsLog.WIFI_SCAN_REPORTED__SOURCE__SOURCE_WIFI_STACK;
                } else {
                    source = WifiStatsLog.WIFI_SCAN_REPORTED__SOURCE__SOURCE_OTHER_APP;

                    for (int i = 0; i < state.mWorkSource.size(); i++) {
                        if (mSettingsPackages.contains(
                                state.mWorkSource.getPackageName(i))) {
                            source = WifiStatsLog.WIFI_SCAN_REPORTED__SOURCE__SOURCE_SETTINGS_APP;
                            break;
                        }
                    }
                }
            }

            int importance = WifiStatsLog.WIFI_SCAN_REPORTED__IMPORTANCE__IMPORTANCE_UNKNOWN;
            if (state.mPackageImportance != -1) {
                if (state.mPackageImportance
                        <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
                    importance = WifiStatsLog.WIFI_SCAN_REPORTED__IMPORTANCE__IMPORTANCE_FOREGROUND;
                } else if (state.mPackageImportance
                        <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE) {
                    importance =
                            WifiStatsLog.WIFI_SCAN_REPORTED__IMPORTANCE__IMPORTANCE_FOREGROUND_SERVICE;
                } else {
                    importance = WifiStatsLog.WIFI_SCAN_REPORTED__IMPORTANCE__IMPORTANCE_BACKGROUND;
                }
            }

            WifiStatsLog.write(WifiStatsLog.WIFI_SCAN_REPORTED,
                    type,
                    result,
                    source,
                    importance,
                    (int) duration,
                    countNetworks);

            if (DBG) {
                Log.d(TAG_SCANS,
                        "WifiScanReported: type = " + type
                                + ", result = " + result
                                + ", source = " + source
                                + ", importance = " + importance
                                + ", networks = " + countNetworks);
            }
        }

        static class State {
            WorkSource mWorkSource = null;
            int mClientUid = -1;
            // see @ActivityManager.RunningAppProcessInfo.Importance
            int mPackageImportance = -1;

            long mTimeStartMillis;
        }
    }

    /** Set whether Make Before Break is supported by the hardware and enabled. */
    public void setIsMakeBeforeBreakSupported(boolean supported) {
        synchronized (mLock) {
            mWifiToWifiSwitchStats.isMakeBeforeBreakSupported = supported;
        }
    }

    /**
     * Increment the number of times Wifi to Wifi switch was triggered. This includes Make Before
     * Break and Break Before Make.
     */
    public void incrementWifiToWifiSwitchTriggerCount() {
        synchronized (mLock) {
            mWifiToWifiSwitchStats.wifiToWifiSwitchTriggerCount++;
        }
    }

    /**
     * Increment the Number of times Wifi to Wifi switch was triggered using Make Before Break
     * (MBB). Note that MBB may not always be used for various reasons e.g. no additional iface
     * available due to ongoing SoftAP, both old and new network have MAC randomization disabled,
     * etc.
     */
    public void incrementMakeBeforeBreakTriggerCount() {
        synchronized (mLock) {
            mWifiToWifiSwitchStats.makeBeforeBreakTriggerCount++;
        }
    }

    /**
     * Increment the number of times Make Before Break was aborted due to the new network not having
     * internet.
     */
    public void incrementMakeBeforeBreakNoInternetCount() {
        synchronized (mLock) {
            mWifiToWifiSwitchStats.makeBeforeBreakNoInternetCount++;
        }
    }

    /**
     * Increment the number of times where, for some reason, Make Before Break resulted in the
     * loss of the primary ClientModeManager, and we needed to recover by making one of the
     * SECONDARY_TRANSIENT ClientModeManagers primary.
     */
    public void incrementMakeBeforeBreakRecoverPrimaryCount() {
        synchronized (mLock) {
            mWifiToWifiSwitchStats.makeBeforeBreakRecoverPrimaryCount++;
        }
    }

    /**
     * Increment the number of times the new network in Make Before Break had its internet
     * connection validated.
     */
    public void incrementMakeBeforeBreakInternetValidatedCount() {
        synchronized (mLock) {
            mWifiToWifiSwitchStats.makeBeforeBreakInternetValidatedCount++;
        }
    }

    /**
     * Increment the number of times the old network in Make Before Break was successfully
     * transitioned from PRIMARY to SECONDARY_TRANSIENT role.
     */
    public void incrementMakeBeforeBreakSuccessCount() {
        synchronized (mLock) {
            mWifiToWifiSwitchStats.makeBeforeBreakSuccessCount++;
        }
    }

    /**
     * Increment the number of times the old network in Make Before Break completed lingering and
     * was disconnected.
     * @param duration the lingering duration in ms
     */
    public void incrementMakeBeforeBreakLingerCompletedCount(long duration) {
        synchronized (mLock) {
            mWifiToWifiSwitchStats.makeBeforeBreakLingerCompletedCount++;
            int lingeringDurationSeconds = Math.min(MBB_LINGERING_DURATION_MAX_SECONDS,
                    (int) duration / 1000);
            mMakeBeforeBreakLingeringDurationSeconds.increment(lingeringDurationSeconds);
        }
    }

    private String wifiToWifiSwitchStatsToString(WifiToWifiSwitchStats stats) {
        return "WifiToWifiSwitchStats{"
                + "isMakeBeforeBreakSupported=" + stats.isMakeBeforeBreakSupported
                + ",wifiToWifiSwitchTriggerCount=" + stats.wifiToWifiSwitchTriggerCount
                + ",makeBeforeBreakTriggerCount=" + stats.makeBeforeBreakTriggerCount
                + ",makeBeforeBreakNoInternetCount=" + stats.makeBeforeBreakNoInternetCount
                + ",makeBeforeBreakRecoverPrimaryCount=" + stats.makeBeforeBreakRecoverPrimaryCount
                + ",makeBeforeBreakInternetValidatedCount="
                + stats.makeBeforeBreakInternetValidatedCount
                + ",makeBeforeBreakSuccessCount=" + stats.makeBeforeBreakSuccessCount
                + ",makeBeforeBreakLingerCompletedCount="
                + stats.makeBeforeBreakLingerCompletedCount
                + ",makeBeforeBreakLingeringDurationSeconds="
                + mMakeBeforeBreakLingeringDurationSeconds
                + "}";
    }

    /**
     * Increment number of number of Passpoint connections with a venue URL
     */
    public void incrementTotalNumberOfPasspointConnectionsWithVenueUrl() {
        synchronized (mLock) {
            mWifiLogProto.totalNumberOfPasspointConnectionsWithVenueUrl++;
        }
    }

    /**
     * Increment number of number of Passpoint connections with a T&C URL
     */
    public void incrementTotalNumberOfPasspointConnectionsWithTermsAndConditionsUrl() {
        synchronized (mLock) {
            mWifiLogProto.totalNumberOfPasspointConnectionsWithTermsAndConditionsUrl++;
        }
    }

    /**
     * Increment number of successful acceptance of Passpoint T&C
     */
    public void incrementTotalNumberOfPasspointAcceptanceOfTermsAndConditions() {
        synchronized (mLock) {
            mWifiLogProto.totalNumberOfPasspointAcceptanceOfTermsAndConditions++;
        }
    }

    /**
     * Increment number of Passpoint profiles with decorated identity prefix
     */
    public void incrementTotalNumberOfPasspointProfilesWithDecoratedIdentity() {
        synchronized (mLock) {
            mWifiLogProto.totalNumberOfPasspointProfilesWithDecoratedIdentity++;
        }
    }

    /**
     * Increment number of Passpoint Deauth-Imminent notification scope
     */
    public void incrementPasspointDeauthImminentScope(boolean isEss) {
        synchronized (mLock) {
            mPasspointDeauthImminentScope.increment(isEss ? PASSPOINT_DEAUTH_IMMINENT_SCOPE_ESS
                    : PASSPOINT_DEAUTH_IMMINENT_SCOPE_BSS);
        }
    }

    /**
     * Increment number of times connection failure status reported per
     * WifiConfiguration.RecentFailureReason
     */
    public void incrementRecentFailureAssociationStatusCount(
            @WifiConfiguration.RecentFailureReason int reason) {
        synchronized (mLock) {
            mRecentFailureAssociationStatus.increment(reason);
        }
    }

    /**
     * Logging the time it takes for save config to the storage.
     * @param time the time it take to write to the storage
     */
    public void wifiConfigStored(int time) {
        WifiStatsLog.write(WIFI_CONFIG_SAVED, time);
    }

    /**
     * Set Wi-Fi usability state per interface as predicted by the scorer
     */
    public void setScorerPredictedWifiUsabilityState(String ifaceName,
            WifiUsabilityState usabilityState) {
        mWifiUsabilityStatePerIface.put(ifaceName, usabilityState);
    }

    private static int getSoftApStartedStartResult(@SoftApManager.StartResult int startResult) {
        switch (startResult) {
            case SoftApManager.START_RESULT_UNKNOWN:
                return WifiStatsLog.SOFT_AP_STARTED__RESULT__START_RESULT_UNKNOWN;
            case SoftApManager.START_RESULT_SUCCESS:
                return WifiStatsLog.SOFT_AP_STARTED__RESULT__START_RESULT_SUCCESS;
            case SoftApManager.START_RESULT_FAILURE_GENERAL:
                return WifiStatsLog.SOFT_AP_STARTED__RESULT__START_RESULT_FAILURE_GENERAL;

            case SoftApManager.START_RESULT_FAILURE_NO_CHANNEL:
                return WifiStatsLog.SOFT_AP_STARTED__RESULT__START_RESULT_FAILURE_NO_CHANNEL;
            case SoftApManager.START_RESULT_FAILURE_UNSUPPORTED_CONFIG:
                return WifiStatsLog.SOFT_AP_STARTED__RESULT__START_RESULT_FAILURE_UNSUPPORTED_CONFIG;
            case SoftApManager.START_RESULT_FAILURE_START_HAL:
                return WifiStatsLog.SOFT_AP_STARTED__RESULT__START_RESULT_FAILURE_START_HAL;
            case SoftApManager.START_RESULT_FAILURE_START_HOSTAPD:
                return WifiStatsLog.SOFT_AP_STARTED__RESULT__START_RESULT_FAILURE_START_HOSTAPD;
            case SoftApManager.START_RESULT_FAILURE_INTERFACE_CONFLICT_USER_REJECTED:
                return WifiStatsLog.SOFT_AP_STARTED__RESULT__START_RESULT_FAILURE_INTERFACE_CONFLICT_USER_REJECTED;
            case SoftApManager.START_RESULT_FAILURE_INTERFACE_CONFLICT:
                return WifiStatsLog.SOFT_AP_STARTED__RESULT__START_RESULT_FAILURE_INTERFACE_CONFLICT;
            case SoftApManager.START_RESULT_FAILURE_CREATE_INTERFACE:
                return WifiStatsLog.SOFT_AP_STARTED__RESULT__START_RESULT_FAILURE_CREATE_INTERFACE;
            case SoftApManager.START_RESULT_FAILURE_SET_COUNTRY_CODE:
                return WifiStatsLog.SOFT_AP_STARTED__RESULT__START_RESULT_FAILURE_SET_COUNTRY_CODE;
            case SoftApManager.START_RESULT_FAILURE_SET_MAC_ADDRESS:
                return WifiStatsLog.SOFT_AP_STARTED__RESULT__START_RESULT_FAILURE_SET_MAC_ADDRESS;
            case SoftApManager.START_RESULT_FAILURE_REGISTER_AP_CALLBACK_HOSTAPD:
                return WifiStatsLog.SOFT_AP_STARTED__RESULT__START_RESULT_FAILURE_REGISTER_AP_CALLBACK_HOSTAPD;
            case SoftApManager.START_RESULT_FAILURE_REGISTER_AP_CALLBACK_WIFICOND:
                return WifiStatsLog.SOFT_AP_STARTED__RESULT__START_RESULT_FAILURE_REGISTER_AP_CALLBACK_WIFICOND;
            case SoftApManager.START_RESULT_FAILURE_ADD_AP_HOSTAPD:
                return WifiStatsLog.SOFT_AP_STARTED__RESULT__START_RESULT_FAILURE_ADD_AP_HOSTAPD;
            default:
                Log.wtf(TAG, "getSoftApStartedStartResult: unknown StartResult" + startResult);
                return WifiStatsLog.SOFT_AP_STARTED__RESULT__START_RESULT_UNKNOWN;
        }
    }

    private static int getSoftApStartedRole(ActiveModeManager.SoftApRole role) {
        if (ActiveModeManager.ROLE_SOFTAP_LOCAL_ONLY.equals(role)) {
            return WifiStatsLog.SOFT_AP_STARTED__ROLE__ROLE_LOCAL_ONLY;
        } else if (ActiveModeManager.ROLE_SOFTAP_TETHERED.equals(role)) {
            return WifiStatsLog.SOFT_AP_STARTED__ROLE__ROLE_TETHERING;
        }
        Log.wtf(TAG, "getSoftApStartedRole: unknown role " + role);
        return WifiStatsLog.SOFT_AP_STARTED__ROLE__ROLE_UNKNOWN;
    }

    private static int getSoftApStartedStaApConcurrency(
            boolean isStaApSupported, boolean isStaDbsSupported) {
        if (isStaDbsSupported) {
            return WifiStatsLog.SOFT_AP_STARTED__STA_AP_CONCURRENCY__STA_AP_CONCURRENCY_DBS;
        }
        if (isStaApSupported) {
            return WifiStatsLog.SOFT_AP_STARTED__STA_AP_CONCURRENCY__STA_AP_CONCURRENCY_SINGLE;
        }
        return WifiStatsLog.SOFT_AP_STARTED__STA_AP_CONCURRENCY__STA_AP_CONCURRENCY_UNSUPPORTED;
    }

    private static int getSoftApStartedStaStatus(int staFreqMhz) {
        if (staFreqMhz == WifiInfo.UNKNOWN_FREQUENCY) {
            return WifiStatsLog.SOFT_AP_STARTED__STA_STATUS__STA_STATUS_DISCONNECTED;
        }
        if (ScanResult.is24GHz(staFreqMhz)) {
            return WifiStatsLog.SOFT_AP_STARTED__STA_STATUS__STA_STATUS_CONNECTED_2_GHZ;
        }
        if (ScanResult.is5GHz(staFreqMhz)) {
            return WifiStatsLog.SOFT_AP_STARTED__STA_STATUS__STA_STATUS_CONNECTED_5_GHZ;
        }
        if (ScanResult.is6GHz(staFreqMhz)) {
            return WifiStatsLog.SOFT_AP_STARTED__STA_STATUS__STA_STATUS_CONNECTED_6_GHZ;
        }
        Log.wtf(TAG, "getSoftApStartedStaStatus: unknown band for freq " + staFreqMhz);
        return WifiStatsLog.SOFT_AP_STARTED__STA_STATUS__STA_STATUS_UNKNOWN;
    }

    private static int getSoftApStartedAuthType(
            @SoftApConfiguration.SecurityType int securityType) {
        switch (securityType) {
            case SoftApConfiguration.SECURITY_TYPE_OPEN:
                return WifiStatsLog.SOFT_AP_STARTED__AUTH_TYPE__AUTH_TYPE_NONE;
            case SoftApConfiguration.SECURITY_TYPE_WPA2_PSK:
                return WifiStatsLog.SOFT_AP_STARTED__AUTH_TYPE__AUTH_TYPE_WPA2_PSK;
            case SoftApConfiguration.SECURITY_TYPE_WPA3_SAE_TRANSITION:
                return WifiStatsLog.SOFT_AP_STARTED__AUTH_TYPE__AUTH_TYPE_SAE_TRANSITION;
            case SoftApConfiguration.SECURITY_TYPE_WPA3_SAE:
                return WifiStatsLog.SOFT_AP_STARTED__AUTH_TYPE__AUTH_TYPE_SAE;
            case SoftApConfiguration.SECURITY_TYPE_WPA3_OWE_TRANSITION:
                return WifiStatsLog.SOFT_AP_STARTED__AUTH_TYPE__AUTH_TYPE_OWE_TRANSITION;
            case SoftApConfiguration.SECURITY_TYPE_WPA3_OWE:
                return WifiStatsLog.SOFT_AP_STARTED__AUTH_TYPE__AUTH_TYPE_OWE;
            default:
                Log.wtf(TAG, "getSoftApStartedAuthType: unknown type " + securityType);
                return WifiStatsLog.SOFT_AP_STARTED__STA_STATUS__STA_STATUS_UNKNOWN;
        }
    }

    /**
     * Writes the SoftApStarted event to WifiStatsLog.
     */
    public void writeSoftApStartedEvent(@SoftApManager.StartResult int startResult,
            @NonNull ActiveModeManager.SoftApRole role,
            @WifiScanner.WifiBand int band1,
            @WifiScanner.WifiBand int band2,
            boolean isDbsSupported,
            boolean isStaApSupported,
            boolean isStaDbsSupported,
            int staFreqMhz,
            @SoftApConfiguration.SecurityType int securityType) {
        WifiStatsLog.write(WifiStatsLog.SOFT_AP_STARTED,
                getSoftApStartedStartResult(startResult),
                getSoftApStartedRole(role),
                band1,
                band2,
                isDbsSupported,
                getSoftApStartedStaApConcurrency(isStaApSupported, isStaDbsSupported),
                getSoftApStartedStaStatus(staFreqMhz),
                getSoftApStartedAuthType(securityType));
        if (startResult == SoftApManager.START_RESULT_SUCCESS) {
            WifiStatsLog.write(WifiStatsLog.SOFT_AP_STATE_CHANGED,
                    WifiStatsLog.SOFT_AP_STATE_CHANGED__HOTSPOT_ON__STATE_ON);
        }
    }

    private static int getSoftApStoppedStopEvent(@SoftApManager.StopEvent int stopEvent) {
        switch (stopEvent) {
            case SoftApManager.STOP_EVENT_UNKNOWN:
                return WifiStatsLog.SOFT_AP_STOPPED__STOP_EVENT__STOP_EVENT_UNKNOWN;
            case SoftApManager.STOP_EVENT_STOPPED:
                return WifiStatsLog.SOFT_AP_STOPPED__STOP_EVENT__STOP_EVENT_STOPPED;
            case SoftApManager.STOP_EVENT_INTERFACE_DOWN:
                return WifiStatsLog.SOFT_AP_STOPPED__STOP_EVENT__STOP_EVENT_INTERFACE_DOWN;
            case SoftApManager.STOP_EVENT_INTERFACE_DESTROYED:
                return WifiStatsLog.SOFT_AP_STOPPED__STOP_EVENT__STOP_EVENT_INTERFACE_DESTROYED;
            case SoftApManager.STOP_EVENT_HOSTAPD_FAILURE:
                return WifiStatsLog.SOFT_AP_STOPPED__STOP_EVENT__STOP_EVENT_HOSTAPD_FAILURE;
            case SoftApManager.STOP_EVENT_NO_USAGE_TIMEOUT:
                return WifiStatsLog.SOFT_AP_STOPPED__STOP_EVENT__STOP_EVENT_NO_USAGE_TIMEOUT;
            default:
                Log.wtf(TAG, "getSoftApStoppedStopEvent: unknown StopEvent " + stopEvent);
                return WifiStatsLog.SOFT_AP_STOPPED__STOP_EVENT__STOP_EVENT_UNKNOWN;
        }
    }

    private static int getSoftApStoppedRole(ActiveModeManager.SoftApRole role) {
        if (ActiveModeManager.ROLE_SOFTAP_LOCAL_ONLY.equals(role)) {
            return WifiStatsLog.SOFT_AP_STOPPED__ROLE__ROLE_LOCAL_ONLY;
        } else if (ActiveModeManager.ROLE_SOFTAP_TETHERED.equals(role)) {
            return WifiStatsLog.SOFT_AP_STOPPED__ROLE__ROLE_TETHERING;
        }
        Log.wtf(TAG, "getSoftApStoppedRole: unknown role " + role);
        return WifiStatsLog.SOFT_AP_STOPPED__ROLE__ROLE_UNKNOWN;
    }

    private static int getSoftApStoppedStaApConcurrency(
            boolean isStaApSupported, boolean isStaDbsSupported) {
        if (isStaDbsSupported) {
            return WifiStatsLog.SOFT_AP_STOPPED__STA_AP_CONCURRENCY__STA_AP_CONCURRENCY_DBS;
        }
        if (isStaApSupported) {
            return WifiStatsLog.SOFT_AP_STOPPED__STA_AP_CONCURRENCY__STA_AP_CONCURRENCY_SINGLE;
        }
        return WifiStatsLog.SOFT_AP_STOPPED__STA_AP_CONCURRENCY__STA_AP_CONCURRENCY_UNSUPPORTED;
    }
    private static int getSoftApStoppedStaStatus(int staFreqMhz) {
        if (staFreqMhz == WifiInfo.UNKNOWN_FREQUENCY) {
            return WifiStatsLog.SOFT_AP_STOPPED__STA_STATUS__STA_STATUS_DISCONNECTED;
        }
        if (ScanResult.is24GHz(staFreqMhz)) {
            return WifiStatsLog.SOFT_AP_STOPPED__STA_STATUS__STA_STATUS_CONNECTED_2_GHZ;
        }
        if (ScanResult.is5GHz(staFreqMhz)) {
            return WifiStatsLog.SOFT_AP_STOPPED__STA_STATUS__STA_STATUS_CONNECTED_5_GHZ;
        }
        if (ScanResult.is6GHz(staFreqMhz)) {
            return WifiStatsLog.SOFT_AP_STOPPED__STA_STATUS__STA_STATUS_CONNECTED_6_GHZ;
        }
        Log.wtf(TAG, "getSoftApStoppedStaStatus: unknown band for freq " + staFreqMhz);
        return WifiStatsLog.SOFT_AP_STOPPED__STA_STATUS__STA_STATUS_UNKNOWN;
    }

    private static int getSoftApStoppedAuthType(
            @SoftApConfiguration.SecurityType int securityType) {
        switch (securityType) {
            case SoftApConfiguration.SECURITY_TYPE_OPEN:
                return WifiStatsLog.SOFT_AP_STOPPED__AUTH_TYPE__AUTH_TYPE_NONE;
            case SoftApConfiguration.SECURITY_TYPE_WPA2_PSK:
                return WifiStatsLog.SOFT_AP_STOPPED__AUTH_TYPE__AUTH_TYPE_WPA2_PSK;
            case SoftApConfiguration.SECURITY_TYPE_WPA3_SAE_TRANSITION:
                return WifiStatsLog.SOFT_AP_STOPPED__AUTH_TYPE__AUTH_TYPE_SAE_TRANSITION;
            case SoftApConfiguration.SECURITY_TYPE_WPA3_SAE:
                return WifiStatsLog.SOFT_AP_STOPPED__AUTH_TYPE__AUTH_TYPE_SAE;
            case SoftApConfiguration.SECURITY_TYPE_WPA3_OWE_TRANSITION:
                return WifiStatsLog.SOFT_AP_STOPPED__AUTH_TYPE__AUTH_TYPE_OWE_TRANSITION;
            case SoftApConfiguration.SECURITY_TYPE_WPA3_OWE:
                return WifiStatsLog.SOFT_AP_STOPPED__AUTH_TYPE__AUTH_TYPE_OWE;
            default:
                Log.wtf(TAG, "getSoftApStoppedAuthType: unknown type " + securityType);
                return WifiStatsLog.SOFT_AP_STOPPED__STA_STATUS__STA_STATUS_UNKNOWN;
        }
    }

    private static int getSoftApStoppedStandard(@WifiAnnotations.WifiStandard int standard) {
        switch (standard) {
            case ScanResult.WIFI_STANDARD_UNKNOWN:
                return WifiStatsLog.SOFT_AP_STOPPED__STANDARD__WIFI_STANDARD_UNKNOWN;
            case ScanResult.WIFI_STANDARD_LEGACY:
                return WifiStatsLog.SOFT_AP_STOPPED__STANDARD__WIFI_STANDARD_LEGACY;
            case ScanResult.WIFI_STANDARD_11N:
                return WifiStatsLog.SOFT_AP_STOPPED__STANDARD__WIFI_STANDARD_11N;
            case ScanResult.WIFI_STANDARD_11AC:
                return WifiStatsLog.SOFT_AP_STOPPED__STANDARD__WIFI_STANDARD_11AC;
            case ScanResult.WIFI_STANDARD_11AX:
                return WifiStatsLog.SOFT_AP_STOPPED__STANDARD__WIFI_STANDARD_11AX;
            case ScanResult.WIFI_STANDARD_11AD:
                return WifiStatsLog.SOFT_AP_STOPPED__STANDARD__WIFI_STANDARD_11AD;
            case ScanResult.WIFI_STANDARD_11BE:
                return WifiStatsLog.SOFT_AP_STOPPED__STANDARD__WIFI_STANDARD_11BE;
            default:
                Log.wtf(TAG, "getSoftApStoppedStandard: unknown standard " + standard);
                return WifiStatsLog.SOFT_AP_STOPPED__STANDARD__WIFI_STANDARD_UNKNOWN;
        }
    }

    private static int getSoftApStoppedUpstreamType(@Nullable NetworkCapabilities caps) {
        if (caps == null) {
            return WifiStatsLog.SOFT_AP_STOPPED__UPSTREAM_TRANSPORT__TT_UNKNOWN;
        }
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                    return WifiStatsLog.SOFT_AP_STOPPED__UPSTREAM_TRANSPORT__TT_WIFI_CELLULAR_VPN;
                }
                return WifiStatsLog.SOFT_AP_STOPPED__UPSTREAM_TRANSPORT__TT_WIFI_VPN;
            }
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                return WifiStatsLog.SOFT_AP_STOPPED__UPSTREAM_TRANSPORT__TT_CELLULAR_VPN;
            }
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH)) {
                return WifiStatsLog.SOFT_AP_STOPPED__UPSTREAM_TRANSPORT__TT_BLUETOOTH_VPN;
            }
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                return WifiStatsLog.SOFT_AP_STOPPED__UPSTREAM_TRANSPORT__TT_ETHERNET_VPN;
            }
        }
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            return WifiStatsLog.SOFT_AP_STOPPED__UPSTREAM_TRANSPORT__TT_WIFI;
        }
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
            return WifiStatsLog.SOFT_AP_STOPPED__UPSTREAM_TRANSPORT__TT_CELLULAR;
        }
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH)) {
            return WifiStatsLog.SOFT_AP_STOPPED__UPSTREAM_TRANSPORT__TT_BLUETOOTH;
        }
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
            return WifiStatsLog.SOFT_AP_STOPPED__UPSTREAM_TRANSPORT__TT_ETHERNET;
        }
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI_AWARE)) {
            return WifiStatsLog.SOFT_AP_STOPPED__UPSTREAM_TRANSPORT__TT_WIFI_AWARE;
        }
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_LOWPAN)) {
            return WifiStatsLog.SOFT_AP_STOPPED__UPSTREAM_TRANSPORT__TT_LOWPAN;
        }
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_TEST)) {
            return WifiStatsLog.SOFT_AP_STOPPED__UPSTREAM_TRANSPORT__TT_TEST;
        }
        Log.wtf(TAG, "getSoftApStoppedStandard: unknown transport types for caps "
                + Arrays.toString(caps.getTransportTypes()));
        return WifiStatsLog.SOFT_AP_STOPPED__UPSTREAM_TRANSPORT__TT_UNKNOWN;
    }

    /**
     * Writes the SoftApStoppedEvent to WifiStatsLog.
     */
    public void writeSoftApStoppedEvent(@SoftApManager.StopEvent int stopEvent,
            @NonNull ActiveModeManager.SoftApRole role,
            @WifiScanner.WifiBand int band,
            boolean isDbs,
            boolean isStaApSupported,
            boolean isStaBridgedApSupported,
            int staFreqMhz,
            boolean isTimeoutEnabled,
            int sessionDurationSeconds,
            @SoftApConfiguration.SecurityType int securityType,
            @WifiAnnotations.WifiStandard int standard,
            int maxClients,
            boolean isDbsTimeoutEnabled,
            int dbsFailureBand,
            int dbsTimeoutBand,
            @Nullable NetworkCapabilities upstreamCaps) {
        WifiStatsLog.write(WifiStatsLog.SOFT_AP_STOPPED,
                getSoftApStoppedStopEvent(stopEvent),
                getSoftApStoppedRole(role),
                band,
                isDbs,
                getSoftApStoppedStaApConcurrency(isStaApSupported, isStaBridgedApSupported),
                getSoftApStoppedStaStatus(staFreqMhz),
                isTimeoutEnabled,
                sessionDurationSeconds,
                getSoftApStoppedAuthType(securityType),
                getSoftApStoppedStandard(standard),
                maxClients,
                isDbsTimeoutEnabled,
                dbsFailureBand,
                dbsTimeoutBand,
                getSoftApStoppedUpstreamType(upstreamCaps));
        WifiStatsLog.write(WifiStatsLog.SOFT_AP_STATE_CHANGED,
                WifiStatsLog.SOFT_AP_STATE_CHANGED__HOTSPOT_ON__STATE_OFF);
    }
}
