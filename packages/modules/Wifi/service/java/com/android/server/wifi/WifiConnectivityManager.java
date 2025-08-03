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

import static android.net.wifi.WifiConfiguration.INVALID_NETWORK_ID;
import static android.net.wifi.WifiConfiguration.RANDOMIZATION_NONE;

import static com.android.server.wifi.ActiveModeManager.ROLE_CLIENT_PRIMARY;
import static com.android.server.wifi.ActiveModeManager.ROLE_CLIENT_SCAN_ONLY;
import static com.android.server.wifi.ActiveModeManager.ROLE_CLIENT_SECONDARY_LONG_LIVED;
import static com.android.server.wifi.ActiveModeManager.ROLE_CLIENT_SECONDARY_TRANSIENT;
import static com.android.server.wifi.ClientModeImpl.WIFI_WORK_SOURCE;
import static com.android.server.wifi.WifiMetrics.ConnectionEvent.FAILURE_AUTHENTICATION_FAILURE;
import static com.android.server.wifi.proto.nano.WifiMetricsProto.ConnectionEvent.AUTH_FAILURE_EAP_FAILURE;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.AlarmManager;
import android.net.IpConfiguration;
import android.net.MacAddress;
import android.net.wifi.IPnoScanResultsCallback;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiContext;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.DeviceMobilityState;
import android.net.wifi.WifiNetworkSelectionConfig;
import android.net.wifi.WifiNetworkSuggestion;
import android.net.wifi.WifiScanner;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.WifiScanner.PnoSettings;
import android.net.wifi.WifiScanner.ScanSettings;
import android.net.wifi.WifiSsid;
import android.net.wifi.hotspot2.PasspointConfiguration;
import android.net.wifi.util.ScanResultUtil;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.Process;
import android.os.WorkSource;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.LocalLog;
import android.util.Log;

import androidx.annotation.RequiresApi;

import com.android.internal.annotations.VisibleForTesting;
import com.android.modules.utils.build.SdkLevel;
import com.android.server.wifi.hotspot2.PasspointManager;
import com.android.server.wifi.proto.WifiStatsLog;
import com.android.server.wifi.scanner.WifiScannerInternal;
import com.android.server.wifi.util.WifiPermissionsUtil;
import com.android.wifi.resources.R;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * This class manages all the connectivity related scanning activities.
 *
 * When the screen is turned on or off, WiFi is connected or disconnected,
 * or on-demand, a scan is initiatiated and the scan results are passed
 * to WifiNetworkSelector for it to make a recommendation on which network
 * to connect to.
 */
public class WifiConnectivityManager {
    public static final String WATCHDOG_TIMER_TAG =
            "WifiConnectivityManager Schedule Watchdog Timer";
    public static final String RESTART_SINGLE_SCAN_TIMER_TAG =
            "WifiConnectivityManager Restart Single Scan";
    public static final String RESTART_CONNECTIVITY_SCAN_TIMER_TAG =
            "WifiConnectivityManager Restart Scan";
    public static final String DELAYED_PARTIAL_SCAN_TIMER_TAG =
            "WifiConnectivityManager Schedule Delayed Partial Scan Timer";

    private static final long RESET_TIME_STAMP = Long.MIN_VALUE;
    // Constants to indicate whether a scan should start immediately or
    // it should comply to the minimum scan interval rule.
    private static final boolean SCAN_IMMEDIATELY = true;
    private static final boolean SCAN_ON_SCHEDULE = false;

    // PNO scan interval in milli-seconds. This is the scan
    // performed when screen is off and connected.
    private static final int CONNECTED_PNO_SCAN_INTERVAL_MS = 160 * 1000; // 160 seconds
    // Maximum number of retries when starting a scan failed
    @VisibleForTesting
    public static final int MAX_SCAN_RESTART_ALLOWED = 5;
    // Number of milli-seconds to delay before retry starting
    // a previously failed scan
    private static final int RESTART_SCAN_DELAY_MS = 2 * 1000; // 2 seconds
    // Restricted channel list age out value.
    private static final long CHANNEL_LIST_AGE_MS = 60 * 60 * 1000; // 1 hour
    // This is the time interval for the connection attempt rate calculation. Connection attempt
    // timestamps beyond this interval is evicted from the list.
    public static final int MAX_CONNECTION_ATTEMPTS_TIME_INTERVAL_MS = 4 * 60 * 1000; // 4 mins
    // Max number of connection attempts in the above time interval.
    public static final int MAX_CONNECTION_ATTEMPTS_RATE = 6;
    private static final int TEMP_BSSID_BLOCK_DURATION = 10 * 1000; // 10 seconds
    // Maximum age of frequencies last seen to be included in pno scans. (30 days)
    private static final long MAX_PNO_SCAN_FREQUENCY_AGE_MS = (long) 1000 * 3600 * 24 * 30;
    // Do not restart PNO scan if network changes happen more than once within this duration.
    private static final long NETWORK_CHANGE_TRIGGER_PNO_THROTTLE_MS = 3000; // 3 seconds
    private static final int POWER_SAVE_SCAN_INTERVAL_MULTIPLIER = 2;
    private static final int MAX_PRIORITIZED_PASSPOINT_SSIDS_PER_PNO_SCAN = 2;
    // ClientModeManager has a bunch of states. From the
    // WifiConnectivityManager's perspective it only cares
    // if it is in Connected state, Disconnected state or in
    // transition between these two states.
    public static final int WIFI_STATE_UNKNOWN = 0;
    public static final int WIFI_STATE_CONNECTED = 1;
    public static final int WIFI_STATE_DISCONNECTED = 2;
    public static final int WIFI_STATE_TRANSITIONING = 3;

    // Initial scan state, used to manage performing partial scans in initial scans
    // Initial scans are the first scan after enabling Wifi or turning on screen when disconnected
    @VisibleForTesting
    public static final int INITIAL_SCAN_STATE_START = 0;
    public static final int INITIAL_SCAN_STATE_AWAITING_RESPONSE = 1;
    public static final int INITIAL_SCAN_STATE_COMPLETE = 2;

    // Log tag for this class
    private static final String TAG = "WifiConnectivityManager";
    private static final String ALL_SINGLE_SCAN_LISTENER = "AllSingleScanListener";
    private static final String PNO_SCAN_LISTENER = "PnoScanListener";

    private final WifiContext mContext;
    private final WifiConfigManager mConfigManager;
    private final WifiCarrierInfoManager mWifiCarrierInfoManager;
    private final WifiCountryCode mWifiCountryCode;
    private final WifiNetworkSuggestionsManager mWifiNetworkSuggestionsManager;
    private final WifiConnectivityHelper mConnectivityHelper;
    private final WifiNetworkSelector mNetworkSelector;
    private final WifiLastResortWatchdog mWifiLastResortWatchdog;
    private final OpenNetworkNotifier mOpenNetworkNotifier;
    private final WifiMetrics mWifiMetrics;
    private final AlarmManager mAlarmManager;
    private final RunnerHandler mEventHandler;
    private final ExternalPnoScanRequestManager mExternalPnoScanRequestManager;
    private final @NonNull SsidTranslator mSsidTranslator;
    private final Clock mClock;
    private final ScoringParams mScoringParams;
    private final LocalLog mLocalLog;
    private final WifiGlobals mWifiGlobals;
    /**
     * Keeps connection attempts within the last {@link #MAX_CONNECTION_ATTEMPTS_TIME_INTERVAL_MS}
     * milliseconds.
     */
    private final LinkedList<Long> mConnectionAttemptTimeStamps = new LinkedList<>();
    private final WifiBlocklistMonitor mWifiBlocklistMonitor;
    private final PasspointManager mPasspointManager;
    private final WifiScoreCard mWifiScoreCard;
    private final WifiChannelUtilization mWifiChannelUtilization;
    private final PowerManager mPowerManager;
    private final DeviceConfigFacade mDeviceConfigFacade;
    private final ActiveModeWarden mActiveModeWarden;
    private final FrameworkFacade mFrameworkFacade;
    private final WifiPermissionsUtil mWifiPermissionsUtil;
    private final WifiDialogManager mWifiDialogManager;

    private WifiScannerInternal mScanner;
    private final MultiInternetManager mMultiInternetManager;
    private boolean mDbg = false;
    private boolean mVerboseLoggingEnabled = false;
    private boolean mWifiEnabled = false;
    private boolean mAutoJoinEnabled = false; // disabled by default, enabled by external triggers
    private boolean mRunning = false;
    private boolean mScreenOn = false;
    private int mMiracastMode = WifiP2pManager.MIRACAST_DISABLED;
    private boolean mP2pGroupStarted = false;
    private int mWifiState = WIFI_STATE_UNKNOWN;
    private int mInitialScanState = INITIAL_SCAN_STATE_COMPLETE;
    private boolean mAutoJoinEnabledExternal = true; // enabled by default
    private boolean mAutoJoinEnabledExternalSetByDeviceAdmin = false;
    private boolean mUntrustedConnectionAllowed = false;
    private Set<Integer> mRestrictedConnectionAllowedUids = new ArraySet<>();
    private boolean mOemPaidConnectionAllowed = false;
    private boolean mOemPrivateConnectionAllowed = false;
    @MultiInternetManager.MultiInternetState
    private int mMultiInternetConnectionState = MultiInternetManager.MULTI_INTERNET_STATE_NONE;
    private WorkSource mOemPaidConnectionRequestorWs = null;
    private WorkSource mOemPrivateConnectionRequestorWs = null;
    private WorkSource mMultiInternetConnectionRequestorWs = null;
    private boolean mTrustedConnectionAllowed = false;
    private boolean mSpecificNetworkRequestInProgress = false;
    private int mScanRestartCount = 0;
    private int mSingleScanRestartCount = 0;
    private int mTotalConnectivityAttemptsRateLimited = 0;
    private long mLastPeriodicSingleScanTimeStamp = RESET_TIME_STAMP;
    private long mLastNetworkSelectionTimeStamp = RESET_TIME_STAMP;
    private boolean mPnoScanStarted = false;
    private Object mDelayedPnoScanToken = new Object();
    private boolean mDelayedPnoScanPending = false;
    private boolean mPeriodicScanTimerSet = false;
    private Object mPeriodicScanTimerToken = new Object();
    private Object mDelayedStartPeriodicScanToken = new Object();
    private boolean mDelayedPartialScanTimerSet = false;
    private boolean mWatchdogScanTimerSet = false;
    private boolean mIsLocationModeEnabled;

    // Used for Initial Scan metrics
    private boolean mFailedInitialPartialScan = false;
    private int mInitialPartialScanChannelCount;

    // Device configs
    private boolean mWaitForFullBandScanResults = false;

    // scan schedule and scan type override set via WifiManager#setScreenOnScanSchedule
    private int[] mExternalSingleScanScheduleSec;
    private int[] mExternalSingleScanType;

    private int mNextScreenOnConnectivityScanDelayMs = 0;

    // Scanning Schedules for screen-on periodic scan
    // Default schedule used in case of invalid configuration
    private static final int[] DEFAULT_SCANNING_SCHEDULE_SEC = {20, 40, 80, 160};
    private int[] mConnectedSingleScanScheduleSec;
    private int[] mDisconnectedSingleScanScheduleSec;
    private int[] mConnectedSingleSavedNetworkSingleScanScheduleSec;
    // Scanning types for screen-on periodic scan. Should have one to one mapping with the scan
    // schedules.
    private static final int[] DEFAULT_SCANNING_TYPE = {WifiScanner.SCAN_TYPE_HIGH_ACCURACY};
    private int[] mConnectedSingleScanType;
    private int[] mDisconnectedSingleScanType;
    private int[] mConnectedSingleSavedNetworkSingleScanType;

    private List<WifiCandidates.Candidate> mLatestCandidates = null;
    private long mLatestCandidatesTimestampMs = 0;
    private int[] mCurrentSingleScanScheduleSec;
    private int[] mCurrentSingleScanType;
    private boolean mPnoScanEnabledByFramework = true;
    private boolean mEnablePnoScanAfterWifiToggle = true;
    private Set<String> mPnoScanPasspointSsids;

    private int mCurrentSingleScanScheduleIndex;
    // Cached WifiCandidates used in high mobility state to avoid connecting to APs that are
    // moving relative to the user.
    private CachedWifiCandidates mCachedWifiCandidates = null;
    private @DeviceMobilityState int mDeviceMobilityState =
            WifiManager.DEVICE_MOBILITY_STATE_UNKNOWN;

    // A helper to log debugging information in the local log buffer, which can
    // be retrieved in bugreport.
    private void localLog(String log) {
        mLocalLog.log(log);
        if (mVerboseLoggingEnabled) Log.v(TAG, log, null);
    }

    /**
     * Enable verbose logging for WifiConnectivityManager.
     */
    public void enableVerboseLogging(boolean verbose) {
        mVerboseLoggingEnabled = verbose;
    }

    // A periodic/PNO scan will be rescheduled up to MAX_SCAN_RESTART_ALLOWED times
    // if the start scan command failed. A timer is used here to make it a deferred retry.
    private final AlarmManager.OnAlarmListener mRestartScanListener =
            new AlarmManager.OnAlarmListener() {
                public void onAlarm() {
                    startConnectivityScan(SCAN_IMMEDIATELY);
                }
            };

    // A single scan will be rescheduled up to MAX_SCAN_RESTART_ALLOWED times
    // if the start scan command failed. An timer is used here to make it a deferred retry.
    private class RestartSingleScanListener implements AlarmManager.OnAlarmListener {
        private final boolean mIsFullBandScan;

        RestartSingleScanListener(boolean isFullBandScan) {
            mIsFullBandScan = isFullBandScan;
        }

        @Override
        public void onAlarm() {
            startSingleScan(mIsFullBandScan, WIFI_WORK_SOURCE, WifiScanner.SCAN_TYPE_HIGH_ACCURACY);
        }
    }

    // As a watchdog mechanism, a single scan will be scheduled every
    // config_wifiPnoWatchdogIntervalMinutes if it is in the WIFI_STATE_DISCONNECTED state.
    private final AlarmManager.OnAlarmListener mWatchdogListener =
            new AlarmManager.OnAlarmListener() {
                public void onAlarm() {
                    watchdogHandler();
                }
            };

    private final AlarmManager.OnAlarmListener mDelayedPartialScanTimerListener =
            new AlarmManager.OnAlarmListener() {
                public void onAlarm() {
                    if (mCachedWifiCandidates == null
                            || mCachedWifiCandidates.frequencies == null
                            || mCachedWifiCandidates.frequencies.size() == 0) {
                        return;
                    }
                    ScanSettings settings = new ScanSettings();
                    settings.type = WifiScanner.SCAN_TYPE_HIGH_ACCURACY;
                    settings.band = getScanBand(false);
                    settings.reportEvents = WifiScanner.REPORT_EVENT_FULL_SCAN_RESULT
                            | WifiScanner.REPORT_EVENT_AFTER_EACH_SCAN;
                    settings.numBssidsPerScan = 0;
                    int index = 0;
                    settings.channels =
                            new WifiScanner.ChannelSpec[mCachedWifiCandidates.frequencies.size()];
                    for (Integer freq : mCachedWifiCandidates.frequencies) {
                        settings.channels[index++] = new WifiScanner.ChannelSpec(freq);
                    }
                    SingleScanListener singleScanListener = new SingleScanListener(false);
                    mScanner.startScan(settings,
                            new WifiScannerInternal.ScanListener(singleScanListener,
                                    mEventHandler));
                    mWifiMetrics.incrementConnectivityOneshotScanCount();
                }
            };

    /**
     * Interface for callback from handling scan results.
     */
    private interface HandleScanResultsListener {
        /**
         * @param wasCandidateSelected true - if a candidate is selected by WifiNetworkSelector
         *                             false - if no candidate is selected by WifiNetworkSelector
         * @param candidateIsPasspoint true - if the selected candidate is a Passpoint network
         *                             false - if no candidate is selected OR the selected
         *                                     candidate is not a Passpoint network
         */
        void onHandled(boolean wasCandidateSelected, boolean candidateIsPasspoint);
    }

    /**
     * Helper method to consolidate handling of scan results when no candidate is selected.
     */
    private void handleScanResultsWithNoCandidate(
            @NonNull HandleScanResultsListener handleScanResultsListener) {
        if (mWifiState == WIFI_STATE_DISCONNECTED) {
            mOpenNetworkNotifier.handleScanResults(
                    mNetworkSelector.getFilteredScanDetailsForOpenUnsavedNetworks());
        }
        mWifiMetrics.noteFirstNetworkSelectionAfterBoot(false);
        handleScanResultsListener.onHandled(false, false);
    }

    /**
     * Helper method to consolidate handling of scan results when a candidate is selected.
     */
    private void handleScanResultsWithCandidate(
            @NonNull HandleScanResultsListener handleScanResultsListener,
            boolean candidateIsPasspoint) {
        mWifiMetrics.noteFirstNetworkSelectionAfterBoot(true);
        handleScanResultsListener.onHandled(true, candidateIsPasspoint);
    }

    /**
     * Utility band filter method for multi-internet use-case.
     */
    @VisibleForTesting
    public boolean filterMultiInternetFrequency(int primaryFreq, int secondaryFreq) {
        return mWifiGlobals.isSupportMultiInternetDual5G()
                ? ScanResult.isValidCombinedBandForDual5GHz(primaryFreq, secondaryFreq)
                : ScanResult.toBand(primaryFreq) != ScanResult.toBand(secondaryFreq);
    }

    /**
     * Helper method to consolidate handling of scan results when multi internet is enabled.
     */
    private boolean handleConnectToMultiInternetConnectionInternal(
            List<WifiCandidates.Candidate> candidates,
            @NonNull String listenerName,
            @NonNull HandleScanResultsListener handleScanResultsListener) {
        final ConcreteClientModeManager primaryCcm = mActiveModeWarden
                .getPrimaryClientModeManagerNullable();
        if (primaryCcm == null || !primaryCcm.isConnected()) {
            // The second internet can only be connected after the primary network connected.
            // Firmware can choose the best BSSID when connecting the primary CMM, so we must
            // wait until the primary network was connected so the secondary can choose a BSSID on
            // a different band with the primary.
            return false;
        }
        if (mActiveModeWarden.getClientModeManagerInRole(ROLE_CLIENT_SECONDARY_LONG_LIVED)
                == null && WifiInjector.getInstance().getHalDeviceManager()
                .creatingIfaceWillDeletePrivilegedIface(HalDeviceManager.HDM_CREATE_IFACE_STA,
                        mMultiInternetConnectionRequestorWs)) {
            localLog(listenerName + ": No secondary cmm candidate");
            return false;
        }
        final WifiInfo primaryInfo = primaryCcm.getConnectionInfo();
        final int primaryBand = ScanResult.toBand(primaryInfo.getFrequency());

        List<WifiCandidates.Candidate> secondaryCmmCandidates;
        if (mMultiInternetManager.isStaConcurrencyForMultiInternetMultiApAllowed()) {
            if (primaryCcm.isMlo()) {
                // An MLO connection can have links in multiple bands. So pick any candidates other
                // than affiliated BSSID's. Accordingly, firmware will adjust multi-links.
                secondaryCmmCandidates = candidates.stream()
                        .filter(c -> !primaryCcm.isAffiliatedLinkBssid(c.getKey().bssid))
                        .collect(Collectors.toList());
            } else {
                // A BSSID can only exist in one band, so when evaluating candidates, only those
                // with a different band from the primary will be considered.
                secondaryCmmCandidates = candidates.stream()
                        .filter(c -> {
                            return filterMultiInternetFrequency(
                                    primaryInfo.getFrequency(), c.getFrequency());
                        })
                        .collect(Collectors.toList());
            }
        } else {
            // Only allow the candidates have the same SSID as the primary.
            secondaryCmmCandidates = candidates.stream().filter(c -> {
                return filterMultiInternetFrequency(primaryInfo.getFrequency(), c.getFrequency())
                        && !primaryCcm.isAffiliatedLinkBssid(c.getKey().bssid) && TextUtils.equals(
                        c.getKey().matchInfo.networkSsid, primaryInfo.getSSID())
                        && c.getKey().networkId == primaryInfo.getNetworkId()
                        && c.getKey().securityType == primaryInfo.getCurrentSecurityType();
            }).collect(Collectors.toList());
        }
        // Filter by specified BSSIDs
        Map<Integer, String> specifiedBssids = mMultiInternetManager.getSpecifiedBssids();
        List<WifiCandidates.Candidate> preferredSecondaryCandidates =
                secondaryCmmCandidates.stream().filter(c -> {
                    final int band = ScanResult.toBand(c.getFrequency());
                    return specifiedBssids.containsKey(band) && specifiedBssids.get(band).equals(
                            c.getKey().bssid.toString());
                }).collect(Collectors.toList());
        // Perform network selection among secondary candidates. Create a new copy. Do not allow
        // user choice override.
        final WifiConfiguration secondaryCmmCandidate =
                mNetworkSelector.selectNetwork(specifiedBssids.isEmpty()
                                ? secondaryCmmCandidates : preferredSecondaryCandidates,
                        false /* overrideEnabled */);

        // No secondary cmm for internet selected, fallback to legacy flow.
        if (secondaryCmmCandidate == null
                || secondaryCmmCandidate.getNetworkSelectionStatus().getCandidate() == null) {
            // TODO: Consider to check secondaryCmmCandidate.secondaryInternet as well, so user
            // can specify the secondaryInternet from WifiConfiguration.
            localLog(listenerName + ": No secondary cmm candidate");
            return false;
        }
        localLog(listenerName + ":secondaryCmmCandidate "
                + secondaryCmmCandidate.getNetworkSelectionStatus().getCandidate().SSID + " / "
                + secondaryCmmCandidate.getNetworkSelectionStatus().getCandidate().BSSID);
        // Check if secondary candidate is the same SSID and network id with primary.
        final boolean isDbsAp = TextUtils.equals(primaryInfo.getSSID(),
                secondaryCmmCandidate.SSID) && (primaryInfo.getNetworkId()
                == secondaryCmmCandidate.networkId);
        final boolean isUsingStaticIp =
                (secondaryCmmCandidate.getIpAssignment() == IpConfiguration.IpAssignment.STATIC);
        if (isDbsAp && isUsingStaticIp) {
            localLog(listenerName + ": Can't connect to DBS AP with Static IP.");
            return false;
        }

        // At this point secondaryCmmCandidate must be multi internet.
        final WorkSource secondaryRequestorWs = mMultiInternetConnectionRequestorWs;
        if (secondaryRequestorWs == null) {
            localLog(listenerName + ": Requestor worksource is null in long live STA use-case,"
                    + "  falling back to single client mode manager flow.");
            return false;
        }

        final String targetBssid2 = secondaryCmmCandidate.getNetworkSelectionStatus()
                .getCandidate().BSSID;
        localLog(listenerName + " targetBssid2 " + targetBssid2 + " primary cmm connected to bssid "
                + primaryCcm.getConnectedBssid());
        // For secondary STA of multi internet connection, when ROLE_CLIENT_SECONDARY_LONG_LIVED
        // is used, specify the target BSSID explicitly to avoid firmware choosing same BSSID
        // as primary STA.
        // TODO: Use new STA+STA user case DUAL_STA_NON_TRANSIENT_SECONDARY and remove the BSSID
        // if roaming is supported on secondary.
        String bssidToConnect = null;
        if (!mConnectivityHelper.isFirmwareRoamingSupported()) {
            bssidToConnect = targetBssid2;
        }
        // Request for a new client mode manager to spin up concurrent connection
        mActiveModeWarden.requestSecondaryLongLivedClientModeManager(
                (cm) -> {
                    if (cm == null) {
                        localLog(listenerName + ": Secondary client mode manager request returned "
                                + "null, aborting (wifi off?)");
                        handleScanResultsWithNoCandidate(handleScanResultsListener);
                        return;
                    }
                    // We did not end up getting the secondary client mode manager for some reason
                    // or get a wrong secondary role, fallback to legacy flow to connect primary.
                    if (cm.getRole() != ROLE_CLIENT_SECONDARY_LONG_LIVED) {
                        localLog(listenerName + ": Secondary client mode manager request returned"
                                + cm.getRole().toString()
                                + " ,falling back to single client mode manager flow.");
                        return;
                    }
                    if (!(cm instanceof ConcreteClientModeManager)) {
                        localLog(listenerName + ": Secondary client mode manager request returned"
                                + " not for concrete client mode manager, falling back to single"
                                + " client mode manager flow.");
                        return;
                    }
                    // Set the concrete client mode manager to secondary internet usage.
                    ConcreteClientModeManager ccm = (ConcreteClientModeManager) cm;
                    ccm.setSecondaryInternet(true);
                    ccm.setSecondaryInternetDbsAp(isDbsAp);
                    localLog(listenerName + ": WNS candidate(secondary)-"
                            + secondaryCmmCandidate.SSID + " / "
                            + secondaryCmmCandidate.getNetworkSelectionStatus()
                            .getCandidate().BSSID + " isDbsAp " + isDbsAp);
                    // Secondary candidate cannot be null (otherwise we would have switched to
                    // legacy flow above). Use the explicit bssid for network connection.
                    WifiConfiguration targetNetwork = new WifiConfiguration(secondaryCmmCandidate);
                    targetNetwork.ephemeral = true;
                    targetNetwork.BSSID = targetBssid2; // specify the BSSID to disable roaming.
                    connectToNetworkUsingCmmWithoutMbb(cm, targetNetwork);

                    handleScanResultsWithCandidate(handleScanResultsListener,
                            targetNetwork.isPasspoint());
                }, secondaryRequestorWs,
                secondaryCmmCandidate.SSID,
                bssidToConnect);
        return true;
    }

    /**
     * Handles 'onResult' callbacks for the Periodic, Single & Pno ScanListener.
     * Executes selection of potential network candidates, initiation of connection attempt to that
     * network.
     */
    private void handleScanResults(@NonNull List<ScanDetail> scanDetails,
            @NonNull String listenerName,
            boolean isFullScan,
            @NonNull HandleScanResultsListener handleScanResultsListener) {
        if (mWifiGlobals.isConnectedMacRandomizationEnabled()
                && WifiInjector.getInstance().getDppManager().isSessionInProgress()) {
            localLog("Ignore scan results while DPP is in progress to prevent auto connect");
            return;
        }
        mWifiCountryCode.updateCountryCodeFromScanResults(scanDetails);

        List<WifiNetworkSelector.ClientModeManagerState> cmmStates = new ArrayList<>();
        WifiNetworkSelector.ClientModeManagerState primaryCmmState = null;
        Set<String> connectedSsids = new HashSet<>();
        boolean hasExistingSecondaryCmm = false;
        for (ClientModeManager clientModeManager :
                mActiveModeWarden.getInternetConnectivityClientModeManagers()) {
            if (clientModeManager.getRole() == ROLE_CLIENT_SECONDARY_LONG_LIVED) {
                hasExistingSecondaryCmm = true;
            }
            mWifiChannelUtilization.refreshChannelStatsAndChannelUtilization(
                    clientModeManager.getWifiLinkLayerStats(),
                    WifiChannelUtilization.UNKNOWN_FREQ);
            WifiInfo wifiInfo = clientModeManager.getConnectionInfo();
            if (clientModeManager.isConnected()) {
                connectedSsids.add(wifiInfo.getSSID());
            }
            WifiNetworkSelector.ClientModeManagerState cmmState =
                    new WifiNetworkSelector.ClientModeManagerState(clientModeManager);
            if (clientModeManager.getRole() == ROLE_CLIENT_PRIMARY) {
                primaryCmmState = cmmState;
            }
            cmmStates.add(cmmState);
        }
        // We don't have any existing secondary CMM, but are we allowed to create a secondary CMM
        // and do we have a request for OEM_PAID/OEM_PRIVATE request? If yes, we need to perform
        // network selection to check if we have any potential candidate for the secondary CMM
        // creation.
        if (!hasExistingSecondaryCmm
                && (mOemPaidConnectionAllowed || mOemPrivateConnectionAllowed)) {
            // prefer OEM PAID requestor if it exists.
            WorkSource oemPaidOrOemPrivateRequestorWs =
                    mOemPaidConnectionRequestorWs != null
                            ? mOemPaidConnectionRequestorWs
                            : mOemPrivateConnectionRequestorWs;
            if (oemPaidOrOemPrivateRequestorWs == null) {
                Log.e(TAG, "Both mOemPaidConnectionRequestorWs & mOemPrivateConnectionRequestorWs "
                        + "are null!");
            }
            if (oemPaidOrOemPrivateRequestorWs != null
                    && mActiveModeWarden.canRequestMoreClientModeManagersInRole(
                            oemPaidOrOemPrivateRequestorWs,
                            ROLE_CLIENT_SECONDARY_LONG_LIVED, false)) {
                // Add a placeholder CMM state to ensure network selection is performed for a
                // potential second STA creation.
                cmmStates.add(new WifiNetworkSelector.ClientModeManagerState());
                hasExistingSecondaryCmm = true;
            }
        }
        // If secondary cmm has not been created and need to connect secondary internet
        if (!hasExistingSecondaryCmm && isMultiInternetConnectionRequested()) {
            if (mMultiInternetConnectionRequestorWs == null) {
                Log.e(TAG, "mMultiInternetConnectionRequestorWs is null!");
            } else if (mActiveModeWarden.canRequestMoreClientModeManagersInRole(
                    mMultiInternetConnectionRequestorWs, ROLE_CLIENT_SECONDARY_LONG_LIVED, false)) {
                cmmStates.add(new WifiNetworkSelector.ClientModeManagerState());
            }
        }

        // Check if any blocklisted BSSIDs can be freed.
        List<ScanDetail> enabledDetails =
                mWifiBlocklistMonitor.tryEnablingBlockedBssids(scanDetails);
        for (ScanDetail scanDetail : enabledDetails) {
            WifiConfiguration config = mConfigManager.getSavedNetworkForScanDetail(scanDetail);
            if (config != null && config.getNetworkSelectionStatus().isNetworkTemporaryDisabled()) {
                mConfigManager.updateNetworkSelectionStatus(config.networkId,
                        WifiConfiguration.NetworkSelectionStatus.DISABLED_NONE);
            }
        }
        Set<String> bssidBlocklist = mWifiBlocklistMonitor.updateAndGetBssidBlocklistForSsids(
                connectedSsids);
        updateUserDisabledList(scanDetails);
        // Clear expired recent failure statuses
        mConfigManager.cleanupExpiredRecentFailureReasons();

        localLog(listenerName + " onResults: start network selection");

        List<WifiCandidates.Candidate> candidates = mNetworkSelector.getCandidatesFromScan(
                scanDetails, bssidBlocklist, cmmStates, mUntrustedConnectionAllowed,
                mOemPaidConnectionAllowed, mOemPrivateConnectionAllowed,
                mRestrictedConnectionAllowedUids, isMultiInternetConnectionRequested());
        mLatestCandidates = candidates;
        mLatestCandidatesTimestampMs = mClock.getElapsedSinceBootMillis();

        if (mDeviceMobilityState == WifiManager.DEVICE_MOBILITY_STATE_HIGH_MVMT
                && mContext.getResources().getBoolean(
                        R.bool.config_wifiHighMovementNetworkSelectionOptimizationEnabled)) {
            candidates = filterCandidatesHighMovement(candidates, listenerName, isFullScan);
        }

        mLastNetworkSelectionTimeStamp = mClock.getElapsedSinceBootMillis();
        mWifiLastResortWatchdog.updateAvailableNetworks(
                mNetworkSelector.getConnectableScanDetails());
        mWifiMetrics.countScanResults(scanDetails);
        // No candidates, return early.
        if (candidates == null || candidates.size() == 0) {
            localLog(listenerName + ":  No candidates");
            handleScanResultsWithNoCandidate(handleScanResultsListener);
            return;
        }

        // We have an oem paid/private network request and device supports STA + STA, check if there
        // are oem paid/private suggestions.
        if ((mOemPaidConnectionAllowed || mOemPrivateConnectionAllowed)
                && mActiveModeWarden.isStaStaConcurrencySupportedForRestrictedConnections()) {
            // Split the candidates based on whether they are oem paid/oem private or not.
            Map<Boolean, List<WifiCandidates.Candidate>> candidatesPartitioned =
                    candidates.stream()
                            .collect(Collectors.groupingBy(c -> c.isOemPaid() || c.isOemPrivate()));
            List<WifiCandidates.Candidate> primaryCmmCandidates =
                    candidatesPartitioned.getOrDefault(false, Collections.emptyList());
            List<WifiCandidates.Candidate> secondaryCmmCandidates =
                    candidatesPartitioned.getOrDefault(true, Collections.emptyList());
            // Some oem paid/private suggestions found, use secondary cmm flow.
            if (!secondaryCmmCandidates.isEmpty()) {
                handleCandidatesFromScanResultsUsingSecondaryCmmIfAvailable(
                        listenerName, primaryCmmCandidates, secondaryCmmCandidates,
                        handleScanResultsListener, scanDetails);
                return;
            }
            // intentional fallthrough: No oem paid/private suggestions, fallback to legacy flow.
        }

        // We have a dual internet network request and device supports STA + STA, check if there
        // are secondary network candidate.
        if (hasMultiInternetConnection() && mMultiInternetManager.hasPendingConnectionRequests()) {
            if (handleConnectToMultiInternetConnectionInternal(candidates,
                    listenerName, handleScanResultsListener)) {
                return;
            }
            // No multi-internet connection. Need to re-evaluate if network selection is still
            // needed on the primary.
            if (primaryCmmState == null
                    || !mNetworkSelector.isNetworkSelectionNeededForCmm(primaryCmmState)) {
                return;
            }
            // intentional fallthrough: No multi internet connections, and network selection is
            // needed on the primary. Fallback to legacy flow.
        }

        handleCandidatesFromScanResultsForPrimaryCmmUsingMbbIfAvailable(
                listenerName, candidates, handleScanResultsListener, scanDetails);
    }

    /**
     * Executes selection of best network for 2 concurrent STA's from the candidates provided,
     * initiation of connection attempt to a network on both the STA's (if found).
     */
    private void handleCandidatesFromScanResultsUsingSecondaryCmmIfAvailable(
            @NonNull String listenerName,
            @NonNull List<WifiCandidates.Candidate> primaryCmmCandidates,
            @NonNull List<WifiCandidates.Candidate> secondaryCmmCandidates,
            @NonNull HandleScanResultsListener handleScanResultsListener,
            @NonNull List<ScanDetail> scanDetails) {
        // Perform network selection among secondary candidates. Create a new copy.
        WifiConfiguration secondaryCmmCandidate =
                mNetworkSelector.selectNetwork(secondaryCmmCandidates);
        // No oem paid/private selected, fallback to legacy flow (should never happen!).
        if (secondaryCmmCandidate == null
                || secondaryCmmCandidate.getNetworkSelectionStatus().getCandidate() == null
                || (!secondaryCmmCandidate.oemPaid && !secondaryCmmCandidate.oemPrivate)) {
            localLog(listenerName + ": No secondary candidate");
            handleCandidatesFromScanResultsForPrimaryCmmUsingMbbIfAvailable(
                    listenerName,
                    Stream.concat(primaryCmmCandidates.stream(), secondaryCmmCandidates.stream())
                            .collect(Collectors.toList()),
                    handleScanResultsListener,
                    scanDetails);
            return;
        }
        String secondaryCmmCandidateBssid =
                secondaryCmmCandidate.getNetworkSelectionStatus().getCandidate().BSSID;


        // At this point secondaryCmmCandidate must be either oemPaid, oemPrivate, or both.
        // OEM_PAID takes precedence over OEM_PRIVATE, so attribute to OEM_PAID requesting app.
        WorkSource secondaryRequestorWs = secondaryCmmCandidate.oemPaid
                ? mOemPaidConnectionRequestorWs : mOemPrivateConnectionRequestorWs;

        if (secondaryRequestorWs == null) {
            localLog(listenerName + ": Requestor worksource is null in long live STA use-case,"
                    + "  falling back to single client mode manager flow.");
            handleCandidatesFromScanResultsForPrimaryCmmUsingMbbIfAvailable(
                    listenerName,
                    Stream.concat(primaryCmmCandidates.stream(), secondaryCmmCandidates.stream())
                            .collect(Collectors.toList()),
                    handleScanResultsListener,
                    scanDetails);
            return;
        }

        WifiConfiguration primaryCmmCandidate =
                mNetworkSelector.selectNetwork(primaryCmmCandidates);
        // Request for a new client mode manager to spin up concurrent connection
        mActiveModeWarden.requestSecondaryLongLivedClientModeManager(
                (cm) -> {
                    if (cm == null) {
                        localLog(listenerName + ": Secondary client mode manager request returned "
                                + "null, aborting (wifi off?)");
                        handleScanResultsWithNoCandidate(handleScanResultsListener);
                        return;
                    }
                    // We did not end up getting the secondary client mode manager for some reason
                    // after we checked above! Fallback to legacy flow.
                    if (cm.getRole() == ROLE_CLIENT_PRIMARY) {
                        localLog(listenerName + ": Secondary client mode manager request returned"
                                + " primary, falling back to single client mode manager flow.");
                        handleCandidatesFromScanResultsForPrimaryCmmUsingMbbIfAvailable(
                                listenerName,
                                Stream.concat(primaryCmmCandidates.stream(),
                                        secondaryCmmCandidates.stream())
                                        .collect(Collectors.toList()),
                                handleScanResultsListener,
                                scanDetails);
                        return;
                    }
                    // Don't use make before break for these connection requests.

                    // If we also selected a primary candidate trigger connection.
                    if (primaryCmmCandidate != null) {
                        localLog(listenerName + ":  WNS candidate(primary)-"
                                + primaryCmmCandidate.SSID);
                        connectToNetworkUsingCmmWithoutMbb(
                                getPrimaryClientModeManager(), primaryCmmCandidate);
                    }

                    localLog(listenerName + ":  WNS candidate(secondary)-"
                            + secondaryCmmCandidate.SSID + " / " + secondaryCmmCandidateBssid);
                    // Secndary candidate cannot be null (otherwise we would have switched to legacy
                    // flow above)
                    connectToNetworkUsingCmmWithoutMbb(cm, secondaryCmmCandidate);

                    handleScanResultsWithCandidate(handleScanResultsListener,
                            secondaryCmmCandidate.isPasspoint());
                }, secondaryRequestorWs,
                secondaryCmmCandidate.SSID,
                mConnectivityHelper.isFirmwareRoamingSupported()
                        ? null : secondaryCmmCandidateBssid);
    }

    /**
     * Executes selection of best network from the candidates provided, initiation of connection
     * attempt to that network.
     */
    private void handleCandidatesFromScanResultsForPrimaryCmmUsingMbbIfAvailable(
            @NonNull String listenerName, @NonNull List<WifiCandidates.Candidate> candidates,
            @NonNull HandleScanResultsListener handleScanResultsListener,
            @NonNull List<ScanDetail> scanDetails) {
        WifiConfiguration candidate = mNetworkSelector.selectNetwork(candidates);
        if (candidate != null) {
            localLog(listenerName + ":  WNS candidate-" + candidate.SSID);
            connectToNetworkForPrimaryCmmUsingMbbIfAvailable(candidate);
            handleScanResultsWithCandidate(handleScanResultsListener, candidate.isPasspoint());
        } else {
            localLog(listenerName + ":  No candidate");
            handleScanResultsWithNoCandidate(handleScanResultsListener);
        }
    }

    private List<WifiCandidates.Candidate> filterCandidatesHighMovement(
            List<WifiCandidates.Candidate> candidates, String listenerName, boolean isFullScan) {
        boolean isNotPartialScan = isFullScan || listenerName.equals(PNO_SCAN_LISTENER);
        if (candidates == null || candidates.isEmpty()) {
            // No connectable networks nearby or network selection is unnecessary
            if (isNotPartialScan) {
                mCachedWifiCandidates = new CachedWifiCandidates(mClock.getElapsedSinceBootMillis(),
                        null);
            }
            return null;
        }

        long minimumTimeBetweenScansMs = mContext.getResources().getInteger(
                R.integer.config_wifiHighMovementNetworkSelectionOptimizationScanDelayMs);
        if (mCachedWifiCandidates != null && mCachedWifiCandidates.candidateRssiMap != null) {
            // cached candidates are too recent, wait for next scan
            if (mClock.getElapsedSinceBootMillis() - mCachedWifiCandidates.timeSinceBootMs
                    < minimumTimeBetweenScansMs) {
                mWifiMetrics.incrementNumHighMovementConnectionSkipped();
                return null;
            }

            int rssiDelta = mContext.getResources().getInteger(R.integer
                    .config_wifiHighMovementNetworkSelectionOptimizationRssiDelta);
            List<WifiCandidates.Candidate> filteredCandidates = candidates.stream().filter(
                    item -> mCachedWifiCandidates.candidateRssiMap.containsKey(item.getKey())
                            && Math.abs(mCachedWifiCandidates.candidateRssiMap.get(item.getKey())
                            - item.getScanRssi()) < rssiDelta)
                    .collect(Collectors.toList());

            if (!filteredCandidates.isEmpty()) {
                if (isNotPartialScan) {
                    mCachedWifiCandidates =
                            new CachedWifiCandidates(mClock.getElapsedSinceBootMillis(),
                            candidates);
                }
                mWifiMetrics.incrementNumHighMovementConnectionStarted();
                return filteredCandidates;
            }
        }

        // Either no cached candidates, or all candidates got filtered out.
        // Update the cached candidates here and schedule a delayed partial scan.
        if (isNotPartialScan) {
            mCachedWifiCandidates = new CachedWifiCandidates(mClock.getElapsedSinceBootMillis(),
                    candidates);
            localLog("Found " + candidates.size() + " candidates at high mobility state. "
                    + "Re-doing scan to confirm network quality.");
            scheduleDelayedPartialScan(minimumTimeBetweenScansMs);
        }
        mWifiMetrics.incrementNumHighMovementConnectionSkipped();
        return null;
    }

    private void updateUserDisabledList(List<ScanDetail> scanDetails) {
        List<String> results = new ArrayList<>();
        List<ScanResult> passpointAp = new ArrayList<>();
        for (ScanDetail scanDetail : scanDetails) {
            results.add(ScanResultUtil.createQuotedSsid(scanDetail.getScanResult().SSID));
            if (!scanDetail.getScanResult().isPasspointNetwork()) {
                continue;
            }
            passpointAp.add(scanDetail.getScanResult());
        }
        if (!passpointAp.isEmpty()) {
            results.addAll(mPasspointManager
                    .getAllMatchingPasspointProfilesForScanResults(passpointAp).keySet());
        }
        mConfigManager.updateUserDisabledList(results);
    }

    private class CachedWifiCandidates {
        public final long timeSinceBootMs;
        public final Map<WifiCandidates.Key, Integer> candidateRssiMap;
        public final Set<Integer> frequencies;

        CachedWifiCandidates(long timeSinceBootMs, List<WifiCandidates.Candidate> candidates) {
            this.timeSinceBootMs = timeSinceBootMs;
            if (candidates == null) {
                this.candidateRssiMap = null;
                this.frequencies = null;
            } else {
                this.candidateRssiMap = new ArrayMap<WifiCandidates.Key, Integer>();
                this.frequencies = new HashSet<Integer>();
                for (WifiCandidates.Candidate c : candidates) {
                    candidateRssiMap.put(c.getKey(), c.getScanRssi());
                    frequencies.add(c.getFrequency());
                }
            }
        }
    }

    // All single scan results listener.
    //
    // Note: This is the listener for all the available single scan results,
    //       including the ones initiated by WifiConnectivityManager and
    //       other modules.
    private class AllSingleScanListener implements WifiScanner.ScanListener {
        private List<ScanDetail> mScanDetails = new ArrayList<ScanDetail>();
        private int mNumScanResultsIgnoredDueToSingleRadioChain = 0;

        public void clearScanDetails() {
            mScanDetails.clear();
            mNumScanResultsIgnoredDueToSingleRadioChain = 0;
        }

        @Override
        public void onSuccess() {
        }

        @Override
        public void onFailure(int reason, String description) {
            localLog("registerScanListener onFailure:"
                    + " reason: " + reason + " description: " + description);
        }

        @Override
        public void onPeriodChanged(int periodInMs) {
        }

        @Override
        public void onResults(WifiScanner.ScanData[] results) {
            if (mIsLocationModeEnabled) {
                mExternalPnoScanRequestManager.onScanResultsAvailable(mScanDetails);
            }
            if (!mWifiEnabled || !mAutoJoinEnabled) {
                clearScanDetails();
                mWaitForFullBandScanResults = false;
                return;
            }

            // We treat any full band scans (with DFS or not) as "full".
            boolean isFullBandScanResults = false;
            if (results != null && results.length > 0) {
                isFullBandScanResults =
                        WifiScanner.isFullBandScan(results[0].getScannedBandsInternal(), true);
            }
            // Full band scan results only.
            if (mWaitForFullBandScanResults) {
                if (!isFullBandScanResults) {
                    localLog("AllSingleScanListener waiting for full band scan results.");
                    clearScanDetails();
                    return;
                } else {
                    mWaitForFullBandScanResults = false;
                }
            }

            // Create a new list to avoid looping call trigger concurrent exception.
            List<ScanDetail> scanDetailList = new ArrayList<>(mScanDetails);
            clearScanDetails();

            if (results != null && results.length > 0) {
                mWifiMetrics.incrementAvailableNetworksHistograms(scanDetailList,
                        isFullBandScanResults);
            }
            if (mNumScanResultsIgnoredDueToSingleRadioChain > 0) {
                Log.i(TAG, "Number of scan results ignored due to single radio chain scan: "
                        + mNumScanResultsIgnoredDueToSingleRadioChain);
            }
            handleScanResults(scanDetailList,
                    ALL_SINGLE_SCAN_LISTENER, isFullBandScanResults,
                    (wasCandidateSelected, candidateIsPasspoint) -> {
                        // Update metrics to see if a single scan detected a valid network
                        // while PNO scan didn't.
                        // Note: We don't update the background scan metrics any more as it is
                        //       not in use.
                        if (mPnoScanStarted) {
                            if (wasCandidateSelected) {
                                mWifiMetrics.incrementNumConnectivityWatchdogPnoBad();
                            } else {
                                mWifiMetrics.incrementNumConnectivityWatchdogPnoGood();
                            }
                        }

                        // Check if we are in the middle of initial partial scan
                        if (mInitialScanState == INITIAL_SCAN_STATE_AWAITING_RESPONSE) {
                            // Done with initial scan
                            setInitialScanState(INITIAL_SCAN_STATE_COMPLETE);

                            if (wasCandidateSelected) {
                                Log.i(TAG, "Connection attempted with the reduced initial scans");
                                mWifiMetrics.reportInitialPartialScan(
                                        mInitialPartialScanChannelCount, true);
                                mInitialPartialScanChannelCount = 0;
                            } else {
                                Log.i(TAG, "Connection was not attempted, issuing a full scan");
                                startConnectivityScan(SCAN_IMMEDIATELY);
                                mFailedInitialPartialScan = true;
                            }
                        } else if (mInitialScanState == INITIAL_SCAN_STATE_COMPLETE) {
                            if (mFailedInitialPartialScan && wasCandidateSelected) {
                                // Initial scan failed, but following full scan succeeded
                                mWifiMetrics.reportInitialPartialScan(
                                        mInitialPartialScanChannelCount, false);
                            }
                            mFailedInitialPartialScan = false;
                            mInitialPartialScanChannelCount = 0;
                        }
                    });
        }

        @Override
        public void onFullResult(ScanResult fullScanResult) {
            if (!mWifiEnabled || !mAutoJoinEnabled) {
                return;
            }

            if (mDbg) {
                localLog("AllSingleScanListener onFullResult: " + fullScanResult.SSID
                        + " capabilities " + fullScanResult.capabilities);
            }

            // When the scan result has radio chain info, ensure we throw away scan results
            // not received with both radio chains (if |mUseSingleRadioChainScanResults| is false).
            if (!mContext.getResources().getBoolean(
                    R.bool.config_wifi_framework_use_single_radio_chain_scan_results_network_selection)
                    && fullScanResult.radioChainInfos != null
                    && fullScanResult.radioChainInfos.length == 1) {
                // Keep track of the number of dropped scan results for logging.
                mNumScanResultsIgnoredDueToSingleRadioChain++;
                return;
            }

            mScanDetails.add(new ScanDetail(fullScanResult));
        }
    }

    private final AllSingleScanListener mAllSingleScanListener;
    private final WifiScannerInternal.ScanListener mInternalAllSingleScanListener;

    // Single scan results listener. A single scan is initiated when
    // DisconnectedPNO scan found a valid network and woke up
    // the system, or by the watchdog timer, or to form the timer based
    // periodic scan.
    //
    // Note: This is the listener for the single scans initiated by the
    //        WifiConnectivityManager.
    private class SingleScanListener implements WifiScanner.ScanListener {
        private final boolean mIsFullBandScan;

        SingleScanListener(boolean isFullBandScan) {
            mIsFullBandScan = isFullBandScan;
        }

        @Override
        public void onSuccess() {
        }

        @Override
        public void onFailure(int reason, String description) {
            localLog("SingleScanListener onFailure:"
                    + " reason: " + reason + " description: " + description);

            // reschedule the scan
            if (mSingleScanRestartCount++ < MAX_SCAN_RESTART_ALLOWED && mScreenOn) {
                scheduleDelayedSingleScan(mIsFullBandScan);
            } else {
                localLog("Failed to successfully start single scan for "
                        + mSingleScanRestartCount + " times, mScreenOn=" + mScreenOn);
                mSingleScanRestartCount = 0;
            }
        }

        @Override
        public void onPeriodChanged(int periodInMs) {
            localLog("SingleScanListener onPeriodChanged: "
                    + "actual scan period " + periodInMs + "ms");
        }

        @Override
        public void onResults(WifiScanner.ScanData[] results) {
            mSingleScanRestartCount = 0;
        }

        @Override
        public void onFullResult(ScanResult fullScanResult) {
        }
    }

    // PNO scan results listener for both disconnected and connected PNO scanning.
    // A PNO scan is initiated when screen is off.
    private class PnoScanListener implements WifiScanner.PnoScanListener {
        private List<ScanDetail> mScanDetails = new ArrayList<ScanDetail>();
        private int mLowRssiNetworkRetryDelayMs;

        private void limitLowRssiNetworkRetryDelay() {
            mLowRssiNetworkRetryDelayMs = Math.min(mLowRssiNetworkRetryDelayMs,
                    mContext.getResources().getInteger(R.integer
                            .config_wifiPnoScanLowRssiNetworkRetryMaxDelaySec) * 1000);
        }

        public void clearScanDetails() {
            mScanDetails.clear();
        }

        // Reset to the start value when either a non-PNO scan is started or
        // WifiNetworkSelector selects a candidate from the PNO scan results.
        public void resetLowRssiNetworkRetryDelay() {
            mLowRssiNetworkRetryDelayMs = mContext.getResources().getInteger(R.integer
                    .config_wifiPnoScanLowRssiNetworkRetryStartDelaySec) * 1000;
        }

        @VisibleForTesting
        public int getLowRssiNetworkRetryDelay() {
            return mLowRssiNetworkRetryDelayMs;
        }

        @Override
        public void onSuccess() {
        }

        @Override
        public void onFailure(int reason, String description) {
            localLog("PnoScanListener onFailure:"
                    + " reason: " + reason + " description: " + description);
            WifiStatsLog.write(WifiStatsLog.PNO_SCAN_STOPPED,
                    WifiStatsLog.PNO_SCAN_STOPPED__STOP_REASON__SCAN_FAILED,
                    0, false, false, false, false, // default values
                    WifiStatsLog.PNO_SCAN_STOPPED__FAILURE_CODE__WIFI_SCANNING_SERVICE_FAILURE);

            // reschedule the scan
            if (mScanRestartCount++ < MAX_SCAN_RESTART_ALLOWED) {
                scheduleDelayedConnectivityScan(RESTART_SCAN_DELAY_MS);
            } else {
                mScanRestartCount = 0;
                localLog("Failed to successfully start PNO scan for "
                        + MAX_SCAN_RESTART_ALLOWED + " times");
            }
        }

        @Override
        public void onPeriodChanged(int periodInMs) {
            localLog("PnoScanListener onPeriodChanged: "
                    + "actual scan period " + periodInMs + "ms");
        }

        // Currently the PNO scan results doesn't include IE,
        // which contains information required by WifiNetworkSelector. Ignore them
        // for now.
        @Override
        public void onResults(WifiScanner.ScanData[] results) {
        }

        @Override
        public void onFullResult(ScanResult fullScanResult) {
        }

        @Override
        public void onPnoNetworkFound(ScanResult[] results) {
            for (ScanResult result: results) {
                if (result.informationElements == null) {
                    localLog("Skipping scan result with null information elements");
                    continue;
                }
                mScanDetails.add(new ScanDetail(result));
            }
            if (mIsLocationModeEnabled) {
                mExternalPnoScanRequestManager.onScanResultsAvailable(mScanDetails);
            }

            // Create a new list to avoid looping call trigger concurrent exception.
            List<ScanDetail> scanDetailList = new ArrayList<>(mScanDetails);
            clearScanDetails();
            mScanRestartCount = 0;

            handleScanResults(scanDetailList, PNO_SCAN_LISTENER, false,
                    (wasCandidateSelected, candidateIsPasspoint) -> {
                        WifiStatsLog.write(WifiStatsLog.PNO_SCAN_STOPPED,
                                WifiStatsLog.PNO_SCAN_STOPPED__STOP_REASON__FOUND_RESULTS,
                                scanDetailList.size(), !mPnoScanPasspointSsids.isEmpty(),
                                pnoPasspointResultFound(scanDetailList), wasCandidateSelected,
                                candidateIsPasspoint,
                                WifiStatsLog.PNO_SCAN_STOPPED__FAILURE_CODE__NO_FAILURE);
                        if (!wasCandidateSelected) {
                            // The scan results were rejected by WifiNetworkSelector due to low
                            // RSSI values
                            // Lazy initialization
                            if (mLowRssiNetworkRetryDelayMs == 0) {
                                resetLowRssiNetworkRetryDelay();
                            }
                            scheduleDelayedConnectivityScan(mLowRssiNetworkRetryDelayMs);

                            // Set up the delay value for next retry.
                            mLowRssiNetworkRetryDelayMs *= 2;
                            limitLowRssiNetworkRetryDelay();
                        } else {
                            resetLowRssiNetworkRetryDelay();
                        }
                    });
        }
    }

    private boolean pnoPasspointResultFound(List<ScanDetail> results) {
        if (mPnoScanPasspointSsids.isEmpty()) return false;
        for (ScanDetail pnoResult : results) {
            if (mPnoScanPasspointSsids.contains(pnoResult.getSSID())) {
                return true;
            }
        }
        return false;
    }

    private final PnoScanListener mPnoScanListener;
    private final WifiScannerInternal.ScanListener mInternalPnoScanListener;

    private class OnNetworkUpdateListener implements
            WifiConfigManager.OnNetworkUpdateListener {
        @Override
        public void onNetworkAdded(WifiConfiguration config) {
            triggerScanOnNetworkChanges();
        }
        @Override
        public void onNetworkEnabled(WifiConfiguration config) {
            triggerScanOnNetworkChanges();
        }
        @Override
        public void onNetworkRemoved(WifiConfiguration config) {
            triggerScanOnNetworkChanges();
        }
        @Override
        public void onNetworkUpdated(WifiConfiguration newConfig, WifiConfiguration oldConfig,
                boolean hasCredentialChanged) {
            triggerScanOnNetworkChanges();
        }

        @Override
        public void onNetworkPermanentlyDisabled(WifiConfiguration config, int disableReason) {
            triggerScanOnNetworkChanges();
        }
    }

    private class OnSuggestionUpdateListener implements
            WifiNetworkSuggestionsManager.OnSuggestionUpdateListener {
        @Override
        public void onSuggestionsAddedOrUpdated(List<WifiNetworkSuggestion> suggestions) {
            triggerScanOnNetworkChanges();
        }

        @Override
        public void onSuggestionsRemoved(List<WifiNetworkSuggestion> suggestions) {
            triggerScanOnNetworkChanges();
        }
    }

    private class ModeChangeCallback implements ActiveModeWarden.ModeChangeCallback {
        @Override
        public void onActiveModeManagerAdded(@NonNull ActiveModeManager activeModeManager) {
            update();
        }

        @Override
        public void onActiveModeManagerRemoved(@NonNull ActiveModeManager activeModeManager) {
            update();
        }

        @Override
        public void onActiveModeManagerRoleChanged(@NonNull ActiveModeManager activeModeManager) {
            // MBB will result in a brief period where there is no primary STA.
            // Need to detect these cases and avoid calling setWifiEnabled(false) since wifi is
            // not actually getting disabled.
            if (activeModeManager.getPreviousRole() == ROLE_CLIENT_PRIMARY
                    && activeModeManager.getRole() == ROLE_CLIENT_SECONDARY_TRANSIENT) {
                return;
            }
            update();
        }

        private void update() {
            List<ClientModeManager> primaryManagers =
                    mActiveModeWarden.getInternetConnectivityClientModeManagers();
            setWifiEnabled(!primaryManagers.isEmpty());
        }
    }

    /**
     * Triggered when {@link MultiInternetWifiNetworkFactory} has a pending network request.
     */
    private class InternalMultiInternetConnectionStatusListener
            implements MultiInternetManager.ConnectionStatusListener {
        @Override
        public void onStatusChange(@MultiInternetManager.MultiInternetState int state,
                WorkSource requestorWs) {
            localLog("setMultiInternetConnectionState: state=" + state + ", requestorWs="
                    + requestorWs);

            if (mMultiInternetConnectionState != state) {
                mMultiInternetConnectionState = state;
                mMultiInternetConnectionRequestorWs = requestorWs;
                checkAllStatesAndEnableAutoJoin();
            }
        }

        @Override
        public void onStartScan(WorkSource requestorWs) {
            forceConnectivityScan(requestorWs);
        }
    }

    /** WifiConnectivityManager constructor */
    WifiConnectivityManager(
            WifiContext context,
            ScoringParams scoringParams,
            WifiConfigManager configManager,
            WifiNetworkSuggestionsManager wifiNetworkSuggestionsManager,
            WifiNetworkSelector networkSelector,
            WifiConnectivityHelper connectivityHelper,
            WifiLastResortWatchdog wifiLastResortWatchdog,
            OpenNetworkNotifier openNetworkNotifier,
            WifiMetrics wifiMetrics,
            RunnerHandler handler,
            Clock clock,
            LocalLog localLog,
            WifiScoreCard scoreCard,
            WifiBlocklistMonitor wifiBlocklistMonitor,
            WifiChannelUtilization wifiChannelUtilization,
            PasspointManager passpointManager,
            MultiInternetManager multiInternetManager,
            DeviceConfigFacade deviceConfigFacade,
            ActiveModeWarden activeModeWarden,
            FrameworkFacade frameworkFacade,
            WifiGlobals wifiGlobals,
            ExternalPnoScanRequestManager externalPnoScanRequestManager,
            @NonNull SsidTranslator ssidTranslator,
            WifiPermissionsUtil wifiPermissionsUtil,
            WifiCarrierInfoManager wifiCarrierInfoManager,
            WifiCountryCode wifiCountryCode,
            @NonNull WifiDialogManager wifiDialogManager,
            WifiDeviceStateChangeManager wifiDeviceStateChangeManager) {
        mContext = context;
        mScoringParams = scoringParams;
        mConfigManager = configManager;
        mWifiNetworkSuggestionsManager = wifiNetworkSuggestionsManager;
        mNetworkSelector = networkSelector;
        mConnectivityHelper = connectivityHelper;
        mWifiLastResortWatchdog = wifiLastResortWatchdog;
        mOpenNetworkNotifier = openNetworkNotifier;
        mWifiMetrics = wifiMetrics;
        mEventHandler = handler;
        mClock = clock;
        mLocalLog = localLog;
        mWifiScoreCard = scoreCard;
        mWifiBlocklistMonitor = wifiBlocklistMonitor;
        mWifiChannelUtilization = wifiChannelUtilization;
        mPasspointManager = passpointManager;
        mMultiInternetManager = multiInternetManager;
        mDeviceConfigFacade = deviceConfigFacade;
        mActiveModeWarden = activeModeWarden;
        mFrameworkFacade = frameworkFacade;
        mWifiGlobals = wifiGlobals;

        mAlarmManager = context.getSystemService(AlarmManager.class);
        mPowerManager = mContext.getSystemService(PowerManager.class);
        mExternalPnoScanRequestManager = externalPnoScanRequestManager;
        mSsidTranslator = ssidTranslator;
        mWifiPermissionsUtil = wifiPermissionsUtil;
        mWifiCarrierInfoManager = wifiCarrierInfoManager;
        mWifiCountryCode = wifiCountryCode;
        mWifiDialogManager = wifiDialogManager;

        // Listen to WifiConfigManager network update events
        mEventHandler.postToFront(() ->
                mConfigManager.addOnNetworkUpdateListener(new OnNetworkUpdateListener()));
        // Listen to WifiNetworkSuggestionsManager suggestion update events
        mWifiNetworkSuggestionsManager.addOnSuggestionUpdateListener(
                new OnSuggestionUpdateListener());
        mActiveModeWarden.registerModeChangeCallback(new ModeChangeCallback());
        mMultiInternetManager.setConnectionStatusListener(
                new InternalMultiInternetConnectionStatusListener());
        mAllSingleScanListener = new AllSingleScanListener();
        mInternalAllSingleScanListener = new WifiScannerInternal.ScanListener(
                mAllSingleScanListener, mEventHandler);
        mPnoScanListener = new PnoScanListener();
        mInternalPnoScanListener = new WifiScannerInternal.ScanListener(mPnoScanListener,
                mEventHandler);
        mPnoScanPasspointSsids = new ArraySet<>();
        wifiDeviceStateChangeManager.registerStateChangeCallback(
                new WifiDeviceStateChangeManager.StateChangeCallback() {
                    @Override
                    public void onScreenStateChanged(boolean screenOn) {
                        handleScreenStateChanged(screenOn);
                    }
                });
    }

    @NonNull
    private WifiInfo getPrimaryWifiInfo() {
        return getPrimaryClientModeManager().getConnectionInfo();
    }

    private ClientModeManager getPrimaryClientModeManager() {
        // There should only be 1 primary client mode manager at any point of time.
        return mActiveModeWarden.getPrimaryClientModeManager();
    }

    /**
     * This checks the connection attempt rate and recommends whether the connection attempt
     * should be skipped or not. This attempts to rate limit the rate of connections to
     * prevent us from flapping between networks and draining battery rapidly.
     */
    private boolean shouldSkipConnectionAttempt(long timeMillis) {
        Iterator<Long> attemptIter = mConnectionAttemptTimeStamps.iterator();
        // First evict old entries from the queue.
        while (attemptIter.hasNext()) {
            Long connectionAttemptTimeMillis = attemptIter.next();
            if ((timeMillis - connectionAttemptTimeMillis)
                    > MAX_CONNECTION_ATTEMPTS_TIME_INTERVAL_MS) {
                attemptIter.remove();
            } else {
                // This list is sorted by timestamps, so we can skip any more checks
                break;
            }
        }
        // If we've reached the max connection attempt rate, skip this connection attempt
        return (mConnectionAttemptTimeStamps.size() >= MAX_CONNECTION_ATTEMPTS_RATE);
    }

    /**
     * Add the current connection attempt timestamp to our queue of connection attempts.
     */
    private void noteConnectionAttempt(long timeMillis) {
        localLog("noteConnectionAttempt: timeMillis=" + timeMillis);
        mConnectionAttemptTimeStamps.addLast(timeMillis);
    }

    /**
     * This is used to clear the connection attempt rate limiter. This is done when the user
     * explicitly tries to connect to a specified network.
     */
    private void clearConnectionAttemptTimeStamps() {
        mConnectionAttemptTimeStamps.clear();
    }

    private static <T> T coalesce(T a, T  b) {
        return a != null ? a : b;
    }

    private boolean isClientModeManagerConnectedOrConnectingToCandidate(
            ClientModeManager clientModeManager, WifiConfiguration candidate) {
        int targetNetworkId = candidate.networkId;
        WifiConfiguration connectedOrConnectingWifiConfiguration = coalesce(
                clientModeManager.getConnectingWifiConfiguration(),
                clientModeManager.getConnectedWifiConfiguration());
        boolean connectingOrConnectedToTarget =
                connectedOrConnectingWifiConfiguration != null
                        && (targetNetworkId == connectedOrConnectingWifiConfiguration.networkId
                        || (mContext.getResources().getBoolean(
                                R.bool.config_wifiEnableLinkedNetworkRoaming)
                        && connectedOrConnectingWifiConfiguration.isLinked(candidate)));

        // Is Firmware roaming control is supported?
        //   - Yes, framework does nothing, firmware will roam if necessary.
        //   - No, framework initiates roaming.
        if (mConnectivityHelper.isFirmwareRoamingSupported()) {
            // just check for networkID.
            return connectingOrConnectedToTarget;
        }

        // check for networkID and BSSID.
        String connectedOrConnectingBssid = coalesce(
                clientModeManager.getConnectingBssid(),
                clientModeManager.getConnectedBssid());
        ScanResult scanResultCandidate =
                candidate.getNetworkSelectionStatus().getCandidate();
        String targetBssid = scanResultCandidate.BSSID;
        return connectingOrConnectedToTarget
                && Objects.equals(targetBssid, connectedOrConnectingBssid);
    }

    private boolean mNetworkSwitchDialogRejected = false;
    private long mTimeToReenableNetworkSwitchDialogsMs = 0;
    private WifiDialogManager.DialogHandle mNetworkSwitchDialog = null;
    private int mDialogCandidateNetId = INVALID_NETWORK_ID;

    class NetworkSwitchDialogCallback implements WifiDialogManager.SimpleDialogCallback {
        @NonNull Runnable mOnSwitchApprovedRunnable;
        @NonNull Runnable mOnSwitchRejectedRunnable;

        NetworkSwitchDialogCallback(@NonNull Runnable onSwitchApprovedRunnable,
                @NonNull Runnable onSwitchRejectedRunnable) {
            mOnSwitchApprovedRunnable = onSwitchApprovedRunnable;
            mOnSwitchRejectedRunnable = onSwitchRejectedRunnable;
        }

        @Override
        public void onPositiveButtonClicked() {
            mOnSwitchApprovedRunnable.run();
        }

        @Override
        public void onNegativeButtonClicked() {
            mOnSwitchRejectedRunnable.run();
        }

        @Override
        public void onNeutralButtonClicked() {
            mOnSwitchRejectedRunnable.run();
        }

        @Override
        public void onCancelled() {
            mOnSwitchRejectedRunnable.run();
        }
    }

    /**
     * Dismisses any active network switch dialogs.
     */
    private void dismissNetworkSwitchDialog() {
        if (mNetworkSwitchDialog != null) {
            mNetworkSwitchDialog.dismissDialog();
        }
        mNetworkSwitchDialog = null;
        mDialogCandidateNetId = INVALID_NETWORK_ID;
    }

    /**
     * Resets the network switch dialog state.
     */
    private void resetNetworkSwitchDialog() {
        dismissNetworkSwitchDialog();
        mNetworkSwitchDialogRejected = false;
        mTimeToReenableNetworkSwitchDialogsMs = 0;
    }

    /**
     * Rejects any active network switch dialogs and disables them from appearing again for the
     * current connection for the specified duration.
     */
    public void disableNetworkSwitchDialog(int durationMs) {
        dismissNetworkSwitchDialog();
        mTimeToReenableNetworkSwitchDialogsMs = mClock.getElapsedSinceBootMillis() + durationMs;
    }

    /**
     * Trigger network connection for primary client mode manager using make before break.
     *
     * Note: This may trigger make before break on a secondary STA if available which will
     * eventually become primary after validation or torn down if it does not become primary.
     */
    private void connectToNetworkForPrimaryCmmUsingMbbIfAvailable(
            @NonNull WifiConfiguration candidate) {
        ClientModeManager primaryManager = mActiveModeWarden.getPrimaryClientModeManager();
        Runnable continueConnectionRunnable = () -> connectToNetworkUsingCmm(
                primaryManager, candidate,
                new ConnectHandler() {
                    @Override
                    public void triggerConnectWhenDisconnected(
                            WifiConfiguration targetNetwork,
                            String targetBssid) {
                        triggerConnectToNetworkUsingCmm(primaryManager, targetNetwork, targetBssid);
                        // since using primary manager to connect, stop any existing managers in the
                        // secondary transient role since they are no longer needed.
                        mActiveModeWarden.stopAllClientModeManagersInRole(
                                ROLE_CLIENT_SECONDARY_TRANSIENT);
                    }

                    @Override
                    public void triggerConnectWhenConnected(
                            WifiConfiguration currentNetwork,
                            WifiConfiguration targetNetwork,
                            String targetBssid) {
                        mWifiMetrics.incrementWifiToWifiSwitchTriggerCount();
                        // If both the current & target networks have MAC randomization disabled,
                        // we cannot use MBB because then both ifaces would need to use the exact
                        // same MAC address (the "designated" factory MAC for the device), which is
                        // illegal. Fallback to single STA behavior.

                        // TODO(b/172086124): Possibly move this logic to
                        // ActiveModeWarden.handleAdditionalClientModeManagerRequest() to
                        // ensure that all fallback logic in 1 central place (all the necessary
                        // info is already included in the secondary STA creation request).
                        if (currentNetwork.macRandomizationSetting == RANDOMIZATION_NONE
                                && targetNetwork.macRandomizationSetting == RANDOMIZATION_NONE) {
                            triggerConnectToNetworkUsingCmm(
                                    primaryManager, targetNetwork, targetBssid);
                            // since using primary manager to connect, stop any existing managers in
                            // the secondary transient role since they are no longer needed.
                            mActiveModeWarden.stopAllClientModeManagersInRole(
                                    ROLE_CLIENT_SECONDARY_TRANSIENT);
                            return;
                        }
                        // Else, use MBB if available.
                        triggerConnectToNetworkUsingMbbIfAvailable(targetNetwork, targetBssid);
                    }

                    @Override
                    public void triggerRoamWhenConnected(
                            WifiConfiguration currentNetwork,
                            WifiConfiguration targetNetwork,
                            String targetBssid) {
                        triggerRoamToNetworkUsingCmm(
                                primaryManager, targetNetwork, targetBssid);
                        // since using primary manager to connect, stop any existing managers in the
                        // secondary transient role since they are no longer needed.
                        mActiveModeWarden.stopAllClientModeManagersInRole(
                                ROLE_CLIENT_SECONDARY_TRANSIENT);
                    }
                });
        WifiConfiguration connectedConfig = primaryManager.getConnectedWifiConfiguration();
        if (connectedConfig == null || !connectedConfig.isUserSelected()
                || !mNetworkSelector.isSufficiencyCheckEnabled()
                || connectedConfig.networkId == candidate.networkId
                || !mContext.getResources().getBoolean(
                R.bool.config_wifiAskUserBeforeSwitchingFromUserSelectedNetwork)) {
            // Continue the connection if we don't need user confirmation for the network switch.
            continueConnectionRunnable.run();
            return;
        }

        // User confirmation for the network switch is required.
        if (mNetworkSwitchDialogRejected) {
            Log.i(TAG, "User rejected switching networks. Do not connect to candidate "
                    + candidate.getProfileKey());
            return;
        }
        if (mClock.getElapsedSinceBootMillis() < mTimeToReenableNetworkSwitchDialogsMs) {
            Log.i(TAG, "Network switching dialog temporarily disabled. Do not connect to candidate "
                    + candidate.getProfileKey());
            return;
        }
        if (candidate.networkId == mDialogCandidateNetId && mNetworkSwitchDialog != null) {
            // Already showing a dialog for this candidate.
            return;
        }
        Log.i(TAG, "Need user approval for connecting to candidate "
                + candidate.getProfileKey());
        resetNetworkSwitchDialog();
        mNetworkSwitchDialog = mWifiDialogManager.createSimpleDialog(
                mContext.getString(connectedConfig.hasNoInternetAccess()
                                ? R.string.wifi_network_switch_dialog_title_no_internet
                                : R.string.wifi_network_switch_dialog_title_bad_internet,
                        WifiInfo.removeDoubleQuotes(connectedConfig.SSID),
                        WifiInfo.removeDoubleQuotes(candidate.SSID)),
                /* message */ null,
                mContext.getString(R.string.wifi_network_switch_dialog_positive_button),
                mContext.getString(R.string.wifi_network_switch_dialog_negative_button),
                /* neutralButtonText */ null,
                new NetworkSwitchDialogCallback(
                /* onSwitchApprovedRunnable */ () -> {
                    resetNetworkSwitchDialog();
                    continueConnectionRunnable.run();
                    primaryManager.onNetworkSwitchAccepted(candidate.networkId,
                            candidate.getNetworkSelectionStatus().getNetworkSelectionBSSID());
                },
                /* onSwitchRejectedRunnable */ () -> {
                    Log.i(TAG, "User rejected network switch to "
                            + candidate.getProfileKey());
                    mNetworkSwitchDialogRejected = true;
                    primaryManager.onNetworkSwitchRejected(candidate.networkId,
                            candidate.getNetworkSelectionStatus().getNetworkSelectionBSSID());
                }),
                new WifiThreadRunner(mEventHandler));
        mNetworkSwitchDialog.launchDialog();
        mDialogCandidateNetId = candidate.networkId;
    }

    /**
     * Trigger network connection for provided client mode manager without using make before break.
     */
    private void connectToNetworkUsingCmmWithoutMbb(
            @NonNull ClientModeManager clientModeManager, @NonNull WifiConfiguration candidate) {
        connectToNetworkUsingCmm(clientModeManager, candidate,
                new ConnectHandler() {
                    @Override
                    public void triggerConnectWhenDisconnected(
                            WifiConfiguration targetNetwork,
                            String targetBssid) {
                        triggerConnectToNetworkUsingCmm(
                                clientModeManager, targetNetwork, targetBssid);
                    }

                    @Override
                    public void triggerConnectWhenConnected(
                            WifiConfiguration currentNetwork,
                            WifiConfiguration targetNetwork,
                            String targetBssid) {
                        triggerConnectToNetworkUsingCmm(
                                clientModeManager, targetNetwork, targetBssid);
                    }

                    @Override
                    public void triggerRoamWhenConnected(
                            WifiConfiguration currentNetwork,
                            WifiConfiguration targetNetwork,
                            String targetBssid) {
                        triggerRoamToNetworkUsingCmm(
                                clientModeManager, targetNetwork, targetBssid);
                    }
                });
    }

    /**
     * Interface to use for trigger connection in various scenarios.
     */
    private interface ConnectHandler {
        /**
         * Invoked to trigger connection to a network when disconnected.
         */
        void triggerConnectWhenDisconnected(
                @NonNull WifiConfiguration targetNetwork, @NonNull String targetBssid);
        /**
         * Invoked to trigger connection to a network when connected to a different network.
         */
        void triggerConnectWhenConnected(
                @NonNull WifiConfiguration currentNetwork, @NonNull WifiConfiguration targetNetwork,
                @NonNull String targetBssid);
        /**
         * Invoked to trigger roam to a specific bssid network when connected to a network.
         */
        void triggerRoamWhenConnected(
                @NonNull WifiConfiguration currentNetwork, @NonNull WifiConfiguration targetNetwork,
                @NonNull String targetBssid);
    }

    private String getAssociationId(@Nullable WifiConfiguration config, @Nullable String bssid) {
        return config == null ? "Disconnected" : config.SSID + " : " + bssid;
    }

    /**
     * Attempt to connect to a network candidate.
     *
     * Based on the currently connected network, this method determines whether we should
     * connect or roam to the network candidate recommended by WifiNetworkSelector.
     */
    private void connectToNetworkUsingCmm(@NonNull ClientModeManager clientModeManager,
            @NonNull WifiConfiguration targetNetwork,
            @NonNull ConnectHandler connectHandler) {
        if (targetNetwork.getNetworkSelectionStatus().getCandidate() == null) {
            localLog("connectToNetwork(" + clientModeManager + "): bad candidate - "
                    + targetNetwork + " scanResult is null!");
            return;
        }
        String targetBssid = targetNetwork.getNetworkSelectionStatus().getCandidate().BSSID;
        String targetAssociationId = getAssociationId(targetNetwork, targetBssid);

        if (isClientModeManagerConnectedOrConnectingToCandidate(clientModeManager, targetNetwork)) {
            localLog("connectToNetwork(" + clientModeManager + "): either already connected or is "
                    + "connecting to " + targetAssociationId);
            return;
        }

        if (targetNetwork.BSSID != null
                && !targetNetwork.BSSID.equals(ClientModeImpl.SUPPLICANT_BSSID_ANY)
                && !targetNetwork.BSSID.equals(targetBssid)) {
            localLog("connectToNetwork(" + clientModeManager + "): target BSSID " + targetBssid
                    + " does not match the config specified BSSID " + targetNetwork.BSSID
                    + ". Drop it!");
            return;
        }
        if (hasMultiInternetConnection() && clientModeManager.getRole() == ROLE_CLIENT_PRIMARY) {
            // Disconnect secondary cmm first before connecting the primary.
            final ConcreteClientModeManager secondaryCcm = mActiveModeWarden
                    .getClientModeManagerInRole(ROLE_CLIENT_SECONDARY_LONG_LIVED);
            if (secondaryCcm != null && isClientModeManagerConnectedOrConnectingToCandidate(
                    secondaryCcm, targetNetwork)) {
                localLog("Disconnect secondary first.");
                secondaryCcm.disconnect();
            }
        }

        WifiConfiguration currentNetwork = coalesce(
                clientModeManager.getConnectedWifiConfiguration(),
                clientModeManager.getConnectingWifiConfiguration());
        String currentBssid = coalesce(
                clientModeManager.getConnectedBssid(), clientModeManager.getConnectingBssid());
        String currentAssociationId = getAssociationId(currentNetwork, currentBssid);

        // Already on desired network id, we need to trigger roam since the device does not
        // support firmware roaming (already checked in
        // isClientModeManagerConnectedOrConnectingToCandidate()).
        if (currentNetwork != null
                && (currentNetwork.networkId == targetNetwork.networkId
                || (mContext.getResources().getBoolean(R.bool.config_wifiEnableLinkedNetworkRoaming)
                && currentNetwork.isLinked(targetNetwork)))) {
            localLog("connectToNetwork(" + clientModeManager + "): Roam to " + targetAssociationId
                    + " from " + currentAssociationId);
            connectHandler.triggerRoamWhenConnected(currentNetwork, targetNetwork, targetBssid);
            return;
        }

        // Need to connect to a different network id
        // Framework specifies the connection target BSSID if firmware doesn't support
        // {@link android.net.wifi.WifiManager#WIFI_FEATURE_CONTROL_ROAMING} or the
        // candidate configuration contains a specified BSSID.
        if (mConnectivityHelper.isFirmwareRoamingSupported()
                && (targetNetwork.BSSID == null
                || targetNetwork.BSSID.equals(ClientModeImpl.SUPPLICANT_BSSID_ANY))) {
            targetBssid = ClientModeImpl.SUPPLICANT_BSSID_ANY;
        }
        localLog("connectToNetwork(" + clientModeManager + "): Connect to "
                + getAssociationId(targetNetwork, targetBssid) + " from "
                + currentAssociationId);
        if (currentNetwork == null) {
            connectHandler.triggerConnectWhenDisconnected(targetNetwork, targetBssid);
            return;
        }
        connectHandler.triggerConnectWhenConnected(currentNetwork, targetNetwork, targetBssid);
    }

    private boolean shouldConnect() {
        long elapsedTimeMillis = mClock.getElapsedSinceBootMillis();
        if (!mScreenOn && shouldSkipConnectionAttempt(elapsedTimeMillis)) {
            localLog("connectToNetwork: Too many connection attempts. Skipping this attempt!");
            mTotalConnectivityAttemptsRateLimited++;
            return false;
        }
        noteConnectionAttempt(elapsedTimeMillis);
        return true;
    }

    /**
     * Trigger roaming to a new bssid while being connected to a different bssid in same network.
     */
    private void triggerRoamToNetworkUsingCmm(
            @NonNull ClientModeManager clientModeManager,
            @NonNull WifiConfiguration targetNetwork,
            @NonNull String targetBssid) {
        if (!shouldConnect()) {
            return;
        }
        clientModeManager.startRoamToNetwork(targetNetwork.networkId, targetBssid);
    }

    /**
     * Trigger connection to a new wifi network while being disconnected.
     */
    private void triggerConnectToNetworkUsingCmm(
            @NonNull ClientModeManager clientModeManager,
            @NonNull WifiConfiguration targetNetwork, @NonNull String targetBssid) {
        if (!shouldConnect()) {
            return;
        }
        if (mContext.getResources().getBoolean(R.bool.config_wifiUseHalApiToDisableFwRoaming)) {
            // If network with specified BSSID, disable roaming. Otherwise enable the roaming.
            boolean enableRoaming = targetNetwork.BSSID == null
                    || targetNetwork.BSSID.equals(ClientModeImpl.SUPPLICANT_BSSID_ANY);
            if (!clientModeManager.enableRoaming(enableRoaming)) {
                Log.w(TAG, "Failed to change roaming to "
                        + (enableRoaming ? "enabled" : "disabled"));
            }
        }
        clientModeManager.startConnectToNetwork(
                targetNetwork.networkId, Process.WIFI_UID, targetBssid);
    }

    /**
     * Trigger connection to a new wifi network while being connected to another network.
     * Depending on device configuration, this uses
     *  - MBB make before break (Dual STA), or
     *  - BBM break before make (Single STA)
     */
    private void triggerConnectToNetworkUsingMbbIfAvailable(
            @NonNull WifiConfiguration targetNetwork, @NonNull String targetBssid) {
        // Request a ClientModeManager from ActiveModeWarden to connect with - may be an existing
        // CMM or a newly created one (potentially switching networks using Make-Before-Break)
        mActiveModeWarden.requestSecondaryTransientClientModeManager(
                (@Nullable ClientModeManager clientModeManager) -> {
                    localLog("connectToNetwork: received requested ClientModeManager "
                            + clientModeManager);
                    if (clientModeManager == null) {
                        localLog("connectToNetwork: Wifi has been toggled off, aborting");
                        return;
                    }
                    // we don't know which ClientModeManager will be allocated to us. Thus, double
                    // check if we're already connected before connecting.
                    if (isClientModeManagerConnectedOrConnectingToCandidate(
                            clientModeManager, targetNetwork)) {
                        localLog("connectToNetwork: already connected or connecting to candidate="
                                + targetNetwork + " on " + clientModeManager);
                        return;
                    }
                    if (clientModeManager.getRole() == ROLE_CLIENT_SECONDARY_TRANSIENT) {
                        mWifiMetrics.incrementMakeBeforeBreakTriggerCount();
                    }
                    triggerConnectToNetworkUsingCmm(clientModeManager, targetNetwork, targetBssid);
                },
                ActiveModeWarden.INTERNAL_REQUESTOR_WS,
                targetNetwork.SSID,
                mConnectivityHelper.isFirmwareRoamingSupported() ? null : targetBssid);
    }

    // Helper for selecting the band for connectivity scan
    private int getScanBand() {
        return getScanBand(true);
    }

    private int getScanBand(boolean isFullBandScan) {
        if (isFullBandScan) {
            if (SdkLevel.isAtLeastS()) {
                if (mContext.getResources().getBoolean(R.bool.config_wifiEnable6ghzPscScanning)) {
                    return WifiScanner.WIFI_BAND_24_5_WITH_DFS_6_GHZ;
                }
                return WifiScanner.WIFI_BAND_BOTH_WITH_DFS;
            }
            return WifiScanner.WIFI_BAND_ALL;
        } else {
            // Use channel list instead.
            return WifiScanner.WIFI_BAND_UNSPECIFIED;
        }
    }

    // Helper for setting the channels for connectivity scan when band is unspecified. Returns
    // false if we can't retrieve the info.
    // If connected, return channels used for the connected network
    // If disconnected, return channels used for any network.
    private boolean setScanChannels(ScanSettings settings) {
        Set<Integer> freqs;

        WifiConfiguration config = getPrimaryClientModeManager().getConnectedWifiConfiguration();
        if (config == null) {
            long ageInMillis = 1000 * 60 * mContext.getResources().getInteger(
                    R.integer.config_wifiInitialPartialScanChannelCacheAgeMins);
            int maxCount = mContext.getResources().getInteger(
                    R.integer.config_wifiInitialPartialScanChannelMaxCount);
            int maxCountPerNetwork =
                    mContext.getResources()
                            .getInteger(
                                    R.integer
                                            .config_wifiInitialPartialScanMaxNewChannelsPerNetwork);
            freqs = fetchChannelSetForPartialScan(maxCount, maxCountPerNetwork, ageInMillis);
        } else {
            freqs = fetchChannelSetForNetworkForPartialScan(config.networkId);
        }

        if (freqs != null && freqs.size() != 0) {
            int index = 0;
            settings.channels = new WifiScanner.ChannelSpec[freqs.size()];
            for (Integer freq : freqs) {
                settings.channels[index++] = new WifiScanner.ChannelSpec(freq);
            }
            return true;
        } else {
            localLog("No history scan channels found, Perform full band scan");
            return false;
        }
    }

    /**
     * Add the channels into the channel set with a size limits.
     *
     * @param channelSet Target set for adding channel to.
     * @param ssid Identifies the network to obtain from WifiScoreCard.
     * @param maxCount Size limit of the channelSet. If equals to 0, means no limit.
     * @param maxNewChannelsPerNetwork Max number of new channels to include from the ssid. 0 to
     *     indicate no such limitation.
     * @param ageInMillis Only consider channel info whose timestamps are younger than this value.
     * @return True channelSet did not reach max limit after adding channels from the network.
     */
    private boolean addChannelFromWifiScoreCardWithLimitPerNetwork(
            @NonNull Set<Integer> channelSet,
            @NonNull String ssid,
            int maxCount,
            int maxNewChannelsPerNetwork,
            long ageInMillis) {
        int allowedChannelsPerNetwork =
                maxNewChannelsPerNetwork <= 0 ? Integer.MAX_VALUE : maxNewChannelsPerNetwork;
        WifiScoreCard.PerNetwork network = mWifiScoreCard.lookupNetwork(ssid);
        for (Integer channel : network.getFrequencies(ageInMillis)) {
            if (maxCount > 0 && channelSet.size() >= maxCount) {
                localLog(
                        "addChannelFromWifiScoreCardWithLimitPerNetwork: "
                                + "size limit reached for network:"
                                + ssid);
                return false;
            }
            if (allowedChannelsPerNetwork <= 0) {
                // max new channels per network reached, but absolute max count not reached
                localLog(
                        "addChannelFromWifiScoreCardWithLimitPerNetwork: "
                                + "per-network size limit reached for network:"
                                + ssid);
                return true;
            }
            if (channelSet.add(channel)) {
                allowedChannelsPerNetwork--;
            }
        }
        return true;
    }

    /**
     * Fetch channel set for target network.
     */
    @VisibleForTesting
    public Set<Integer> fetchChannelSetForNetworkForPartialScan(int networkId) {
        WifiConfiguration config = mConfigManager.getConfiguredNetwork(networkId);
        if (config == null) {
            return null;
        }
        final int maxNumActiveChannelsForPartialScans = mContext.getResources().getInteger(
                R.integer.config_wifi_framework_associated_partial_scan_max_num_active_channels);
        Set<Integer> channelSet = new HashSet<>();
        WifiInfo wifiInfo = getPrimaryWifiInfo();
        // First add the currently connected network channel.
        if (wifiInfo.getFrequency() > 0) {
            channelSet.add(wifiInfo.getFrequency());
        }
        // Then get channels for the network.
        addChannelFromWifiScoreCardWithLimitPerNetwork(
                channelSet,
                config.SSID,
                maxNumActiveChannelsForPartialScans,
                0,
                CHANNEL_LIST_AGE_MS);
        return channelSet;
    }

    /** Fetch channel set for all saved and suggestion non-passpoint network for partial scan. */
    @VisibleForTesting
    public Set<Integer> fetchChannelSetForPartialScan(
            int maxCountTotal, int maxCountPerNetwork, long ageInMillis) {
        List<WifiConfiguration> networks = getAllScanOptimizationNetworks();
        if (networks.isEmpty()) {
            return null;
        }

        // Sort the networks with the most frequent ones at the front of the network list.
        Collections.sort(networks, mConfigManager.getScanListComparator());

        Set<Integer> channelSet = new HashSet<>();

        for (WifiConfiguration config : networks) {
            if (!addChannelFromWifiScoreCardWithLimitPerNetwork(
                    channelSet, config.SSID, maxCountTotal, maxCountPerNetwork, ageInMillis)) {
                return channelSet;
            }
        }

        return channelSet;
    }

    // Watchdog timer handler
    private void watchdogHandler() {
        // Schedule the next timer and start a single scan if we are in disconnected state.
        // Otherwise, the watchdog timer will be scheduled when entering disconnected
        // state.
        if (mWifiState == WIFI_STATE_DISCONNECTED) {
            localLog("start a single scan from watchdogHandler");

            scheduleWatchdogTimer();
            startSingleScan(true, WIFI_WORK_SOURCE, WifiScanner.SCAN_TYPE_HIGH_ACCURACY);
        }
    }

    private void triggerScanOnNetworkChanges() {
        if (mScreenOn) {
            // Update scanning schedule if needed
            if (updateSingleScanningSchedule()) {
                localLog("Saved networks / suggestions updated impacting single scan schedule");
                startConnectivityScan(false);
            }
        } else {
            // Trigger a delayed PNO scan to avoid frequent PNO scan restart since it's possible
            // that many networks could be added back to back.
            if (mDelayedPnoScanPending) {
                localLog("PNO scan throttled for frequent Saved networks / suggestions update.");
                return;
            }
            // Update the PNO scan network list when screen is off. Here we
            // rely on startConnectivityScan() to perform all the checks and clean up.
            localLog("Saved networks / suggestions update will restart pno scan in "
                    + NETWORK_CHANGE_TRIGGER_PNO_THROTTLE_MS + "ms");
            mDelayedPnoScanPending = true;
            mEventHandler.postDelayed(
                    () -> {
                        mDelayedPnoScanPending = false;
                        startConnectivityScan(false);
                    },
                    mDelayedPnoScanToken, NETWORK_CHANGE_TRIGGER_PNO_THROTTLE_MS);
        }
    }

    // Start a single scan and set up the interval for next single scan.
    private void startPeriodicSingleScan() {
        // Reaching here with scanning schedule is null means this is a false timer alarm
        if (getSingleScanningSchedule() == null) {
            return;
        }

        long currentTimeStamp = mClock.getElapsedSinceBootMillis();

        if (mLastPeriodicSingleScanTimeStamp != RESET_TIME_STAMP) {
            long msSinceLastScan = currentTimeStamp - mLastPeriodicSingleScanTimeStamp;
            if (msSinceLastScan < getScheduledSingleScanIntervalMs(0)) {
                localLog("Last periodic single scan started " + msSinceLastScan
                        + "ms ago, defer this new scan request.");
                schedulePeriodicScanTimer(
                        getScheduledSingleScanIntervalMs(0) - (int) msSinceLastScan);
                return;
            }
        }

        boolean isScanNeeded = true;
        boolean isFullBandScan = true;

        boolean isShortTimeSinceLastNetworkSelection =
                ((currentTimeStamp - mLastNetworkSelectionTimeStamp)
                <= 1000 * mContext.getResources().getInteger(
                R.integer.config_wifiConnectedHighRssiScanMinimumWindowSizeSec));

        WifiInfo wifiInfo = getPrimaryWifiInfo();
        boolean isGoodLinkAndAcceptableInternetAndShortTimeSinceLastNetworkSelection =
                mNetworkSelector.hasSufficientLinkQuality(wifiInfo)
                && mNetworkSelector.hasInternetOrExpectNoInternet(wifiInfo)
                && isShortTimeSinceLastNetworkSelection;
        // Check it is one of following conditions to skip scan (with firmware roaming)
        // or do partial scan only (without firmware roaming).
        // 1) Network is sufficient
        // 2) link is good, internet status is acceptable
        //    and it is a short time since last network selection
        // 3) There is active stream such that scan will be likely disruptive
        // 4) There is no multi internet connection request pending
        if (mWifiState == WIFI_STATE_CONNECTED
                // If multi internet is connecting, then we do need the scan.
                && !isMultiInternetConnectionRequested()
                && (mNetworkSelector.isNetworkSufficient(wifiInfo)
                || isGoodLinkAndAcceptableInternetAndShortTimeSinceLastNetworkSelection
                || mNetworkSelector.hasActiveStream(wifiInfo))) {
            // If only partial scan is proposed and firmware roaming control is supported,
            // we will not issue any scan because firmware roaming will take care of
            // intra-SSID roam.
            if (mConnectivityHelper.isFirmwareRoamingSupported()) {
                localLog("No partial scan because firmware roaming is supported.");
                isScanNeeded = false;
            } else {
                localLog("No full band scan because current network is sufficient");
                isFullBandScan = false;
            }
        }

        if (isScanNeeded) {
            mLastPeriodicSingleScanTimeStamp = currentTimeStamp;

            if (mWifiState == WIFI_STATE_DISCONNECTED
                    && mInitialScanState == INITIAL_SCAN_STATE_START) {
                startSingleScan(false, WIFI_WORK_SOURCE,
                        getScheduledSingleScanType(mCurrentSingleScanScheduleIndex));

                // Note, initial partial scan may fail due to lack of channel history
                // Hence, we verify state before changing to AWIATING_RESPONSE
                if (mInitialScanState == INITIAL_SCAN_STATE_START) {
                    setInitialScanState(INITIAL_SCAN_STATE_AWAITING_RESPONSE);
                    mWifiMetrics.incrementInitialPartialScanCount();
                }
            } else {
                startSingleScan(isFullBandScan, WIFI_WORK_SOURCE,
                        getScheduledSingleScanType(mCurrentSingleScanScheduleIndex));
            }
            schedulePeriodicScanTimer(
                    getScheduledSingleScanIntervalMs(mCurrentSingleScanScheduleIndex));

            // Set up the next scan interval in an exponential backoff fashion.
            mCurrentSingleScanScheduleIndex++;
        } else {
            // Since we already skipped this scan, keep the same scan interval for next scan.
            schedulePeriodicScanTimer(
                    getScheduledSingleScanIntervalMs(mCurrentSingleScanScheduleIndex));
        }
    }

    // Returns the scan type based on current scan schedule and index.
    private int getScheduledSingleScanType(int index) {
        int[] scanType = mExternalSingleScanType == null ? mCurrentSingleScanType
                : mExternalSingleScanType;
        if (scanType == null) {
            Log.e(TAG, "Invalid attempt to get schedule scan type. Type array is null ");
            return DEFAULT_SCANNING_TYPE[0];
        }
        if (index >= scanType.length) {
            index = scanType.length - 1;
        }
        return scanType[index];
    }

    // Retrieve a value from single scanning schedule in ms
    private int getScheduledSingleScanIntervalMs(int index) {
        int[] schedule = mExternalSingleScanScheduleSec == null ? mCurrentSingleScanScheduleSec
                : mExternalSingleScanScheduleSec;
        if (schedule == null) {
            Log.e(TAG, "Invalid attempt to get schedule interval, Schedule array is null ");

            // Use a default value
            return DEFAULT_SCANNING_SCHEDULE_SEC[0] * 1000;
        }

        if (index >= schedule.length) {
            index = schedule.length - 1;
        }
        return getScanIntervalWithPowerSaveMultiplier(schedule[index] * 1000);
    }

    private int getScanIntervalWithPowerSaveMultiplier(int interval) {
        if (!mDeviceConfigFacade.isWifiBatterySaverEnabled()) {
            return interval;
        }
        return mPowerManager.isPowerSaveMode()
                ? POWER_SAVE_SCAN_INTERVAL_MULTIPLIER * interval : interval;
    }

    // Set the single scanning schedule
    private void setSingleScanningSchedule(int[] scheduleSec) {
        mCurrentSingleScanScheduleSec = scheduleSec;
    }

    // Set the single scanning schedule
    private void setSingleScanningType(int[] scanType) {
        mCurrentSingleScanType = scanType;
    }

    // Get the single scanning schedule
    private int[] getSingleScanningSchedule() {
        return mCurrentSingleScanScheduleSec;
    }

    // Update the single scanning schedule if needed, and return true if update occurs
    private boolean updateSingleScanningSchedule() {
        if (!mWifiEnabled || !mAutoJoinEnabled) {
            return false;
        }
        if (mWifiState != WIFI_STATE_CONNECTED) {
            // No need to update the scanning schedule
            return false;
        }

        boolean shouldUseSingleSavedNetworkSchedule = useSingleSavedNetworkSchedule();

        if (mCurrentSingleScanScheduleSec == mConnectedSingleScanScheduleSec
                && shouldUseSingleSavedNetworkSchedule) {
            setSingleScanningSchedule(mConnectedSingleSavedNetworkSingleScanScheduleSec);
            setSingleScanningType(mConnectedSingleSavedNetworkSingleScanType);
            return true;
        }
        if (mCurrentSingleScanScheduleSec == mConnectedSingleSavedNetworkSingleScanScheduleSec
                && !shouldUseSingleSavedNetworkSchedule) {
            setSingleScanningSchedule(mConnectedSingleScanScheduleSec);
            setSingleScanningType(mConnectedSingleScanType);
            return true;
        }
        return false;
    }

    // Set initial scan state
    private void setInitialScanState(int state) {
        Log.i(TAG, "SetInitialScanState to : " + state);
        mInitialScanState = state;
    }

    @VisibleForTesting
    public int getInitialScanState() {
        return mInitialScanState;
    }

    // Reset the last periodic single scan time stamp so that the next periodic single
    // scan can start immediately.
    private void resetLastPeriodicSingleScanTimeStamp() {
        mLastPeriodicSingleScanTimeStamp = RESET_TIME_STAMP;
    }

    // Start a single scan
    private void startForcedSingleScan(boolean isFullBandScan, WorkSource workSource,
            int scanType) {
        // Any scans will impact wifi performance including WFD performance,
        // So at least ignore scans triggered internally by ConnectivityManager
        // when WFD session is active. We still allow connectivity scans initiated
        // by other work source.
        if (WIFI_WORK_SOURCE.equals(workSource) && mP2pGroupStarted &&
            (mMiracastMode == WifiP2pManager.MIRACAST_SOURCE ||
            mMiracastMode == WifiP2pManager.MIRACAST_SINK)) {
            Log.d(TAG, "ignore connectivity scan, MiracastMode: " + mMiracastMode);
            return;
        }

        mPnoScanListener.resetLowRssiNetworkRetryDelay();

        ScanSettings settings = new ScanSettings();
        if (!isFullBandScan) {
            if (!setScanChannels(settings)) {
                isFullBandScan = true;
                // Skip the initial scan since no channel history available
                setInitialScanState(INITIAL_SCAN_STATE_COMPLETE);
            } else {
                mInitialPartialScanChannelCount = settings.channels.length;
            }
        }
        settings.type = scanType;
        settings.band = getScanBand(isFullBandScan);
        // Only enable RNR for full scans since we already have a known channel list for
        // partial scan. We do not want to enable RNR for partial scan since it could end up
        // wasting time scanning for 6Ghz APs that the device doesn't have credential to.
        if (SdkLevel.isAtLeastS()) {
            settings.setRnrSetting(isFullBandScan ? WifiScanner.WIFI_RNR_ENABLED
                    : WifiScanner.WIFI_RNR_NOT_NEEDED);
            settings.set6GhzPscOnlyEnabled(isFullBandScan
                    ? mContext.getResources().getBoolean(R.bool.config_wifiEnable6ghzPscScanning)
                    : false);
        }
        settings.reportEvents = WifiScanner.REPORT_EVENT_FULL_SCAN_RESULT
                            | WifiScanner.REPORT_EVENT_AFTER_EACH_SCAN;
        settings.numBssidsPerScan = 0;
        settings.hiddenNetworks.clear();
        // retrieve the list of hidden network SSIDs from saved network to scan for
        settings.hiddenNetworks.addAll(mConfigManager.retrieveHiddenNetworkList(true));
        // retrieve the list of hidden network SSIDs from Network suggestion to scan for
        settings.hiddenNetworks.addAll(
                mWifiNetworkSuggestionsManager.retrieveHiddenNetworkList(true));

        SingleScanListener singleScanListener =
                new SingleScanListener(isFullBandScan);
        mScanner.startScan(settings,
                new WifiScannerInternal.ScanListener(singleScanListener, mEventHandler));
        mWifiMetrics.incrementConnectivityOneshotScanCount();
    }

    private void startSingleScan(boolean isFullBandScan, WorkSource workSource, int scanType) {
        if (!mWifiEnabled || !mAutoJoinEnabled) {
            return;
        }
        startForcedSingleScan(isFullBandScan, workSource, scanType);
    }

    // Start a periodic scan when screen is on
    private void startPeriodicScan(boolean scanImmediately) {
        mPnoScanListener.resetLowRssiNetworkRetryDelay();

        // No connectivity scan if wifi-to-wifi switch is disabled.
        if (mWifiState == WIFI_STATE_CONNECTED
                && !mNetworkSelector.isAssociatedNetworkSelectionEnabled()) {
            return;
        }

        // Due to b/28020168, timer based single scan will be scheduled
        // to provide periodic scan in an exponential backoff fashion.
        if (scanImmediately) {
            resetLastPeriodicSingleScanTimeStamp();
        }
        mCurrentSingleScanScheduleIndex = 0;
        startPeriodicSingleScan();
    }

    private int deviceMobilityStateToPnoScanIntervalMs(@DeviceMobilityState int state) {
        switch (state) {
            case WifiManager.DEVICE_MOBILITY_STATE_UNKNOWN:
            case WifiManager.DEVICE_MOBILITY_STATE_LOW_MVMT:
            case WifiManager.DEVICE_MOBILITY_STATE_HIGH_MVMT:
                return getScanIntervalWithPowerSaveMultiplier(mContext.getResources()
                        .getInteger(R.integer.config_wifiMovingPnoScanIntervalMillis));
            case WifiManager.DEVICE_MOBILITY_STATE_STATIONARY:
                return getScanIntervalWithPowerSaveMultiplier(mContext.getResources()
                        .getInteger(R.integer.config_wifiStationaryPnoScanIntervalMillis));
            default:
                return -1;
        }
    }

    /**
     * Configures network selection parameters..
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    public void setNetworkSelectionConfig(@NonNull WifiNetworkSelectionConfig nsConfig) {
        boolean oldAssociatedNetworkSelectionEnabled =
                mNetworkSelector.isAssociatedNetworkSelectionEnabled();
        mNetworkSelector.setAssociatedNetworkSelectionOverride(
                nsConfig.getAssociatedNetworkSelectionOverride());
        mNetworkSelector.setSufficiencyCheckEnabled(
                nsConfig.isSufficiencyCheckEnabledWhenScreenOff(),
                nsConfig.isSufficiencyCheckEnabledWhenScreenOn());
        mNetworkSelector.setUserConnectChoiceOverrideEnabled(
                nsConfig.isUserConnectChoiceOverrideEnabled());
        mNetworkSelector.setLastSelectionWeightEnabled(
                nsConfig.isLastSelectionWeightEnabled());
        mScoringParams.setRssi2Thresholds(
                nsConfig.getRssiThresholds(ScanResult.WIFI_BAND_24_GHZ));
        mScoringParams.setRssi5Thresholds(
                nsConfig.getRssiThresholds(ScanResult.WIFI_BAND_5_GHZ));
        mScoringParams.setRssi6Thresholds(
                nsConfig.getRssiThresholds(ScanResult.WIFI_BAND_6_GHZ));
        mScoringParams.setFrequencyWeights(
                nsConfig.getFrequencyWeights());
        boolean newAssociatedNetworkSelectionEnabled =
                mNetworkSelector.isAssociatedNetworkSelectionEnabled();
        if (oldAssociatedNetworkSelectionEnabled && !newAssociatedNetworkSelectionEnabled) {
            dismissNetworkSwitchDialog();
        } else if (!oldAssociatedNetworkSelectionEnabled && newAssociatedNetworkSelectionEnabled) {
            resetNetworkSwitchDialog();
        }
    }

    /**
     * Sets the external scan schedule and scan type.
     */
    public void setExternalScreenOnScanSchedule(int[] scanScheduleSeconds, int[] scanType) {
        mExternalSingleScanScheduleSec = scanScheduleSeconds;
        mExternalSingleScanType = scanType;
    }

    /**
     * Sets the next screen-on connectivity scan delay in milliseconds.
     */
    public void setOneShotScreenOnConnectivityScanDelayMillis(int delayMs) {
        mNextScreenOnConnectivityScanDelayMs = delayMs;
    }

    /**
     * Pass device mobility state to WifiChannelUtilization and
     * alter the PNO scan interval based on the current device mobility state.
     * If the device is stationary, it will likely not find many new Wifi networks. Thus, increase
     * the interval between scans. Decrease the interval between scans if the device begins to move
     * again.
     * @param newState the new device mobility state
     */
    public void setDeviceMobilityState(@DeviceMobilityState int newState) {
        int oldDeviceMobilityState = mDeviceMobilityState;
        localLog("Device mobility state changed. state=" + newState);
        int newPnoScanIntervalMs = deviceMobilityStateToPnoScanIntervalMs(newState);
        if (newPnoScanIntervalMs < 0) {
            Log.e(TAG, "Invalid device mobility state: " + newState);
            return;
        }
        mDeviceMobilityState = newState;
        mWifiChannelUtilization.setDeviceMobilityState(newState);

        int oldPnoScanIntervalMs = deviceMobilityStateToPnoScanIntervalMs(oldDeviceMobilityState);
        if (newPnoScanIntervalMs == oldPnoScanIntervalMs) {
            if (mPnoScanStarted) {
                mWifiMetrics.logPnoScanStop();
                mWifiMetrics.enterDeviceMobilityState(newState);
                mWifiMetrics.logPnoScanStart();
            } else {
                mWifiMetrics.enterDeviceMobilityState(newState);
            }
        } else {
            Log.d(TAG, "PNO Scan Interval changed to " + newPnoScanIntervalMs + " ms.");

            if (mPnoScanStarted) {
                Log.d(TAG, "Restarting PNO Scan with new scan interval");
                stopPnoScan();
                mWifiMetrics.enterDeviceMobilityState(newState);
                startDisconnectedPnoScan();
            } else {
                mWifiMetrics.enterDeviceMobilityState(newState);
            }
        }
    }

    /**
     * Enable/disable the PNO scan framework feature.
     */
    public void setPnoScanEnabledByFramework(boolean enabled,
            boolean enablePnoScanAfterWifiToggle) {
        mEnablePnoScanAfterWifiToggle = enablePnoScanAfterWifiToggle;
        if (mPnoScanEnabledByFramework == enabled) {
            return;
        }
        mPnoScanEnabledByFramework = enabled;
        if (enabled) {
            if (!mScreenOn && mWifiState == WIFI_STATE_DISCONNECTED && !mPnoScanStarted) {
                startDisconnectedPnoScan();
            }
        } else {
            stopPnoScan();
        }
    }

    // Start a DisconnectedPNO scan when screen is off and Wifi is disconnected
    private void startDisconnectedPnoScan() {
        if (!mPnoScanEnabledByFramework) {
            localLog("Skipping PNO scan because it's disabled by the framework.");
            return;
        }

        // Initialize PNO settings
        PnoSettings pnoSettings = new PnoSettings();
        List<PnoSettings.PnoNetwork> pnoNetworkList = retrievePnoNetworkList();
        int listSize = pnoNetworkList.size();

        if (listSize == 0) {
            // No saved network
            localLog("No saved network for starting disconnected PNO.");
            return;
        }

        pnoSettings.networkList = new PnoSettings.PnoNetwork[listSize];
        pnoSettings.networkList = pnoNetworkList.toArray(pnoSettings.networkList);
        pnoSettings.min6GHzRssi = mScoringParams.getEntryRssi(ScanResult.BAND_6_GHZ_START_FREQ_MHZ);
        pnoSettings.min5GHzRssi = mScoringParams.getEntryRssi(ScanResult.BAND_5_GHZ_START_FREQ_MHZ);
        pnoSettings.min24GHzRssi = mScoringParams.getEntryRssi(
                ScanResult.BAND_24_GHZ_START_FREQ_MHZ);
        pnoSettings.scanIterations = mContext.getResources()
                .getInteger(R.integer.config_wifiPnoScanIterations);
        pnoSettings.scanIntervalMultiplier = mContext.getResources()
                .getInteger(R.integer.config_wifiPnoScanIntervalMultiplier);

        // Initialize scan settings
        ScanSettings scanSettings = new ScanSettings();
        scanSettings.band = getScanBand();
        scanSettings.reportEvents = WifiScanner.REPORT_EVENT_NO_BATCH;
        scanSettings.numBssidsPerScan = 0;
        scanSettings.periodInMs = deviceMobilityStateToPnoScanIntervalMs(mDeviceMobilityState);

        pnoSettings.isConnected = false;
        mScanner.startPnoScan(scanSettings, pnoSettings, mInternalPnoScanListener);
        mPnoScanStarted = true;
        WifiStatsLog.write(WifiStatsLog.PNO_SCAN_STARTED, !mPnoScanPasspointSsids.isEmpty());
    }

    private @NonNull List<WifiConfiguration> getAllScanOptimizationNetworks() {
        List<WifiConfiguration> networks = mConfigManager.getSavedNetworks(-1);
        networks.addAll(mWifiNetworkSuggestionsManager.getAllScanOptimizationSuggestionNetworks());
        // remove all saved but never connected, auto-join disabled, or network selection disabled
        // networks.
        networks.removeIf(config -> !config.allowAutojoin
                || (!config.ephemeral && !config.getNetworkSelectionStatus().hasEverConnected())
                || !config.getNetworkSelectionStatus().isNetworkEnabled()
                || mConfigManager.isNetworkTemporarilyDisabledByUser(
                        config.isPasspoint() ? config.FQDN : config.SSID)
                || (config.enterpriseConfig != null
                && config.enterpriseConfig.isAuthenticationSimBased()
                && config.carrierId != TelephonyManager.UNKNOWN_CARRIER_ID)
                && !mWifiCarrierInfoManager.isSimReady(
                        mWifiCarrierInfoManager.getBestMatchSubscriptionId(config)));
        return networks;
    }

    /**
     * Merge Passpoint PNO scan candidates into an existing network list.
     */
    private @NonNull List<WifiConfiguration> mergePasspointPnoScanCandidates(
            List<WifiConfiguration> networks) {
        List<WifiConfiguration> passpointNetworks =
                mPasspointManager.getWifiConfigsForPasspointProfiles(true);
        passpointNetworks.addAll(
                mWifiNetworkSuggestionsManager.getAllPasspointScanOptimizationSuggestionNetworks(
                        true));
        if (passpointNetworks.isEmpty()) return networks;

        // Add up to MAX_PRIORITIZED_PASSPOINT_SSIDS_PER_PNO_SCAN Passpoint networks to
        // the head of the merged network list.
        int numPasspointAtHead =
                Math.min(passpointNetworks.size(), MAX_PRIORITIZED_PASSPOINT_SSIDS_PER_PNO_SCAN);
        List<WifiConfiguration> mergedNetworks = new ArrayList<>();
        mergedNetworks.addAll(passpointNetworks.subList(0, numPasspointAtHead));
        mergedNetworks.addAll(networks);

        // Add any remaining Passpoint networks to the end of the merged network list.
        mergedNetworks.addAll(
                passpointNetworks.subList(numPasspointAtHead, passpointNetworks.size()));
        return mergedNetworks;
    }

    /**
     * Sets whether global location mode is enabled.
     */
    public void setLocationModeEnabled(boolean enabled) {
        mIsLocationModeEnabled = enabled;
    }

    /**
     * Sets a external PNO scan request
     */
    public void setExternalPnoScanRequest(int uid, @NonNull String packageName,
            @NonNull IBinder binder, @NonNull IPnoScanResultsCallback callback,
            @NonNull List<WifiSsid> ssids, @NonNull int[] frequencies) {
        if (mExternalPnoScanRequestManager.setRequest(
                uid, packageName, binder, callback, ssids, frequencies)) {
            if (mPnoScanStarted) {
                Log.d(TAG, "Restarting PNO Scan with external requested SSIDs");
                stopPnoScan();
                startDisconnectedPnoScan();
            } else if (mWifiState == WIFI_STATE_DISCONNECTED) {
                Log.d(TAG, "Starting PNO Scan with external requested SSIDs");
                startDisconnectedPnoScan();
            }
        }
    }

    /**
     * Clears the external PNO scan request.
     */
    public void clearExternalPnoScanRequest(int uid) {
        if (mExternalPnoScanRequestManager.removeRequest(uid)) {
            Log.d(TAG, "Restarting PNO Scan after removing external requested SSIDs");
            stopPnoScan();
            startDisconnectedPnoScan();
        }
    }

    /**
     * Retrieve the PnoNetworks from Saved and suggestion non-passpoint network.
     */
    @VisibleForTesting
    public List<PnoSettings.PnoNetwork> retrievePnoNetworkList() {
        List<WifiConfiguration> networks = getAllScanOptimizationNetworks();
        Set<String> externalRequestedPnoSsids = mIsLocationModeEnabled
                ? mExternalPnoScanRequestManager.getExternalPnoScanSsids() : Collections.EMPTY_SET;
        Set<Integer> externalRequestedPnoFrequencies = mIsLocationModeEnabled
                ? mExternalPnoScanRequestManager.getExternalPnoScanFrequencies()
                : Collections.EMPTY_SET;
        if (networks.isEmpty() && externalRequestedPnoSsids.isEmpty()) {
            return Collections.EMPTY_LIST;
        }
        Collections.sort(networks, mConfigManager.getScanListComparator());
        if (mDeviceConfigFacade.includePasspointSsidsInPnoScans()) {
            networks = mergePasspointPnoScanCandidates(networks);
        }
        boolean pnoFrequencyCullingEnabled = mContext.getResources()
                .getBoolean(R.bool.config_wifiPnoFrequencyCullingEnabled);

        List<PnoSettings.PnoNetwork> pnoList = new ArrayList<>();
        Set<String> pnoSet = new HashSet<>();
        mPnoScanPasspointSsids.clear();

        // Add any externally requested SSIDs to PNO scan list
        for (String ssid : externalRequestedPnoSsids) {
            if (pnoSet.contains(ssid)) {
                continue;
            }
            WifiScanner.PnoSettings.PnoNetwork pnoNetwork = new PnoSettings.PnoNetwork(ssid);
            pnoList.add(pnoNetwork);
            pnoSet.add(ssid);
            if (!pnoFrequencyCullingEnabled) {
                continue;
            }
            Set<Integer> channelList = new HashSet<>();
            addChannelFromWifiScoreCardWithLimitPerNetwork(
                    channelList, ssid, 0, 0, MAX_PNO_SCAN_FREQUENCY_AGE_MS);
            channelList.addAll(externalRequestedPnoFrequencies);
            pnoNetwork.frequencies = channelList.stream().mapToInt(Integer::intValue).toArray();
        }
        for (WifiConfiguration config : networks) {
            for (WifiSsid originalSsid : mSsidTranslator.getAllPossibleOriginalSsids(
                    WifiSsid.fromString(config.SSID))) {
                if (pnoSet.contains(originalSsid.toString())) {
                    continue;
                }
                WifiScanner.PnoSettings.PnoNetwork pnoNetwork =
                        WifiConfigurationUtil.createPnoNetwork(config);
                pnoNetwork.ssid = originalSsid.toString();
                pnoList.add(pnoNetwork);
                pnoSet.add(originalSsid.toString());
                if (config.isPasspoint()) {
                    mPnoScanPasspointSsids.add(originalSsid.toString());
                }
                if (!pnoFrequencyCullingEnabled) {
                    continue;
                }
                Set<Integer> channelList = new HashSet<>();
                addChannelFromWifiScoreCardWithLimitPerNetwork(
                        channelList, config.SSID, 0, 0, MAX_PNO_SCAN_FREQUENCY_AGE_MS);
                pnoNetwork.frequencies = channelList.stream().mapToInt(Integer::intValue).toArray();
            }
        }
        return pnoList;
    }

    // Stop PNO scan.
    private void stopPnoScan() {
        if (!mPnoScanStarted) return;

        mScanner.stopPnoScan(mInternalPnoScanListener);
        mPnoScanStarted = false;
        mWifiMetrics.logPnoScanStop();
    }

    // Set up watchdog timer
    private void scheduleWatchdogTimer() {
        localLog("scheduleWatchdogTimer");
        int alarmType = mContext.getResources().getBoolean(
                R.bool.config_wifiPnoWatchdogCanWakeUp) ? AlarmManager.ELAPSED_REALTIME_WAKEUP
                : AlarmManager.ELAPSED_REALTIME;

        mAlarmManager.set(alarmType,
                            mClock.getElapsedSinceBootMillis() + mContext.getResources().getInteger(
                                    R.integer.config_wifiPnoWatchdogIntervalMs),
                            WATCHDOG_TIMER_TAG,
                            mWatchdogListener, mEventHandler);
        mWatchdogScanTimerSet = true;
    }

    // Cancel the watchdog scan timer.
    private void cancelWatchdogScan() {
        if (mWatchdogScanTimerSet) {
            mAlarmManager.cancel(mWatchdogListener);
            mWatchdogScanTimerSet = false;
        }
    }

    // Schedules a delayed partial scan, which will scan the frequencies in mCachedWifiCandidates.
    private void scheduleDelayedPartialScan(long delayMillis) {
        mAlarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                mClock.getElapsedSinceBootMillis() + delayMillis, DELAYED_PARTIAL_SCAN_TIMER_TAG,
                mDelayedPartialScanTimerListener, mEventHandler);
        mDelayedPartialScanTimerSet = true;
    }

    // Cancel the delayed partial scan timer.
    private void cancelDelayedPartialScan() {
        if (mDelayedPartialScanTimerSet) {
            mAlarmManager.cancel(mDelayedPartialScanTimerListener);
            mDelayedPartialScanTimerSet = false;
        }
    }

    // Set up periodic scan timer
    // Due to b/28020168, timer based single scan will be scheduled
    // to provide periodic scan in an exponential backoff fashion.
    private void schedulePeriodicScanTimer(int intervalMs) {
        if (mPeriodicScanTimerSet) {
            Log.e(TAG, "A periodic scan was already scheduled.");
            return;
        }
        localLog("schedulePeriodicScanTimer intervalMs " + intervalMs);
        mPeriodicScanTimerSet = true;
        mEventHandler.postDelayed(() -> {
            mPeriodicScanTimerSet = false;
            // Schedule the next timer and start a single scan if screen is on.
            if (mScreenOn) {
                startPeriodicSingleScan();
            }
        }, mPeriodicScanTimerToken, intervalMs);
    }

    // Cancel periodic scan timer
    private void cancelPeriodicScanTimer() {
        if (mPeriodicScanTimerSet) {
            localLog("cancelPeriodicScanTimer");
            mEventHandler.removeCallbacksAndMessages(mPeriodicScanTimerToken);
            mPeriodicScanTimerSet = false;
        }
    }

    // Set up timer to start a delayed single scan after RESTART_SCAN_DELAY_MS
    private void scheduleDelayedSingleScan(boolean isFullBandScan) {
        localLog("scheduleDelayedSingleScan");

        RestartSingleScanListener restartSingleScanListener =
                new RestartSingleScanListener(isFullBandScan);
        mAlarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                            mClock.getElapsedSinceBootMillis() + RESTART_SCAN_DELAY_MS,
                            RESTART_SINGLE_SCAN_TIMER_TAG,
                            restartSingleScanListener, mEventHandler);
    }

    // Set up timer to start a delayed scan after msFromNow milli-seconds
    private void scheduleDelayedConnectivityScan(int msFromNow) {
        localLog("scheduleDelayedConnectivityScan");

        mAlarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                            mClock.getElapsedSinceBootMillis() + msFromNow,
                            RESTART_CONNECTIVITY_SCAN_TIMER_TAG,
                            mRestartScanListener, mEventHandler);

    }

    // Start a connectivity scan. The scan method is chosen according to
    // the current screen state and WiFi state.
    private void startConnectivityScan(boolean scanImmediately) {
        boolean noPotentialNetworkAvailable = hasNoPotentialNetworkAvailable();
        localLog("startConnectivityScan: screenOn=" + mScreenOn
                + " wifiState=" + stateToString(mWifiState)
                + " scanImmediately=" + scanImmediately
                + " wifiEnabled=" + mWifiEnabled
                + " mAutoJoinEnabled=" + mAutoJoinEnabled
                + " mAutoJoinEnabledExternal=" + mAutoJoinEnabledExternal
                + " mAutoJoinEnabledExternalSetByDeviceAdmin="
                + mAutoJoinEnabledExternalSetByDeviceAdmin
                + " mPnoScanEnabledByFramework=" + mPnoScanEnabledByFramework
                + " mEnablePnoScanAfterWifiToggle=" + mEnablePnoScanAfterWifiToggle
                + " mSpecificNetworkRequestInProgress=" + mSpecificNetworkRequestInProgress
                + " mTrustedConnectionAllowed=" + mTrustedConnectionAllowed
                + " isSufficiencyCheckEnabled=" + mNetworkSelector.isSufficiencyCheckEnabled()
                + " isAssociatedNetworkSelectionEnabled="
                + mNetworkSelector.isAssociatedNetworkSelectionEnabled()
                + " noPotentialNetworkAvailable=" + noPotentialNetworkAvailable);

        if (!mWifiEnabled || !mAutoJoinEnabled || noPotentialNetworkAvailable) {
            return;
        }

        // Always stop outstanding connectivity scan if there is any
        stopConnectivityScan();

        // Don't start a connectivity scan while Wifi is in the transition
        // between connected and disconnected states.
        if ((mWifiState != WIFI_STATE_CONNECTED && mWifiState != WIFI_STATE_DISCONNECTED)
                || (getSingleScanningSchedule() == null)) {
            return;
        }

        if (mScreenOn) {
            startPeriodicScan(scanImmediately);
        } else {
            if (mWifiState == WIFI_STATE_DISCONNECTED && !mPnoScanStarted) {
                startDisconnectedPnoScan();
            }
        }
    }

    // Stop connectivity scan if there is any.
    private void stopConnectivityScan() {
        // Due to b/28020168, timer based single scan will be scheduled
        // to provide periodic scan in an exponential backoff fashion.
        cancelPeriodicScanTimer();
        cancelDelayedPartialScan();
        stopPnoScan();
    }

    /**
     * Handler for screen state (on/off) changes
     */
    private void handleScreenStateChanged(boolean screenOn) {
        localLog("handleScreenStateChanged: screenOn=" + screenOn);

        mScreenOn = screenOn;
        mNetworkSelector.setScreenState(screenOn);

        if (mWifiState == WIFI_STATE_DISCONNECTED
                && mContext.getResources().getBoolean(R.bool.config_wifiEnablePartialInitialScan)) {
            setInitialScanState(INITIAL_SCAN_STATE_START);
        }

        mOpenNetworkNotifier.handleScreenStateChanged(screenOn);

        if (mScreenOn) {
            // cancel any queued PNO scans since the screen is turned on.
            mDelayedPnoScanPending = false;
            mEventHandler.removeCallbacksAndMessages(mDelayedPnoScanToken);

            if (mNextScreenOnConnectivityScanDelayMs > 0) {
                mEventHandler.postDelayed(() -> {
                    startConnectivityScan(SCAN_ON_SCHEDULE);
                }, mDelayedStartPeriodicScanToken, mNextScreenOnConnectivityScanDelayMs);
                mNextScreenOnConnectivityScanDelayMs = 0;
                return;
            }
        } else {
            mEventHandler.removeCallbacksAndMessages(mDelayedStartPeriodicScanToken);
        }
        startConnectivityScan(SCAN_ON_SCHEDULE);
    }

    /**
     * Save current miracast mode, it will be used to ignore
     * connectivity scan during the time when miracast is enabled.
     */
    public void saveMiracastMode(int mode) {
        Log.d(TAG, "saveMiracastMode: mode=" + mode);
        mMiracastMode = mode;
    }

    /**
     * Save current p2p group started or not.
     */
    public void saveP2pGroupStarted(boolean started) {
        Log.d(TAG, "saveP2pGroupStarted: started=" + started);
        mP2pGroupStarted = started;
    }

    /**
     * Helper function that converts the WIFI_STATE_XXX constants to string
     */
    private static String stateToString(int state) {
        switch (state) {
            case WIFI_STATE_CONNECTED:
                return "connected";
            case WIFI_STATE_DISCONNECTED:
                return "disconnected";
            case WIFI_STATE_TRANSITIONING:
                return "transitioning";
            default:
                return "unknown";
        }
    }

    /**
     * Check if Single saved network schedule should be used
     * This is true if the one of the following is satisfied:
     * 1. Device has a total of 1 network whether saved, passpoint, or suggestion.
     * 2. The device is connected to that network.
     */
    private boolean useSingleSavedNetworkSchedule() {
        WifiConfiguration currentNetwork =
                getPrimaryClientModeManager().getConnectedWifiConfiguration();
        if (currentNetwork == null) {
            localLog("Current network is missing, may caused by remove network and disconnecting");
            return false;
        }
        List<WifiConfiguration> savedNetworks =
                mConfigManager.getSavedNetworks(Process.WIFI_UID);
        // If we have multiple saved networks, then no need to proceed
        if (savedNetworks.size() > 1) {
            return false;
        }

        List<PasspointConfiguration> passpointNetworks =
                mPasspointManager.getProviderConfigs(Process.WIFI_UID, true);
        // If we have multiple networks (saved + passpoint), then no need to proceed
        if (passpointNetworks.size() + savedNetworks.size() > 1) {
            return false;
        }

        Set<WifiNetworkSuggestion> suggestionsNetworks =
                mWifiNetworkSuggestionsManager.getAllApprovedNetworkSuggestions();
        // If total size not equal to 1, then no need to proceed
        if (passpointNetworks.size() + savedNetworks.size() + suggestionsNetworks.size() != 1) {
            return false;
        }

        // Next verify that this network is the one device is connected to
        int currentNetworkId = currentNetwork.networkId;

        // If we have a single saved network, and we are connected to it, return true.
        if (savedNetworks.size() == 1) {
            return (savedNetworks.get(0).networkId == currentNetworkId);
        }

        // If we have a single passpoint network, and we are connected to it, return true.
        if (passpointNetworks.size() == 1) {
            String passpointKey = passpointNetworks.get(0).getUniqueId();
            WifiConfiguration config = mConfigManager.getConfiguredNetwork(passpointKey);
            return (config != null && config.networkId == currentNetworkId);
        }

        // If we have a single suggestion network, and we are connected to it, return true.
        WifiNetworkSuggestion network = suggestionsNetworks.iterator().next();
        String suggestionKey = network.getWifiConfiguration().getProfileKey();
        WifiConfiguration config = mConfigManager.getConfiguredNetwork(suggestionKey);
        return (config != null && config.networkId == currentNetworkId);
    }

    /**
     * Check if there are no potential networks available for connection
     * This is true if both of the following is satisfied:
     * 1. Device has no network whether saved, passpoint, or suggestion.
     * 2. Open network notifier is disabled.
     */
    private boolean hasNoPotentialNetworkAvailable() {
        List<WifiConfiguration> savedNetworks =
                mConfigManager.getSavedNetworks(Process.WIFI_UID);
        // If we have any saved networks, then no need to proceed
        if (savedNetworks.size() > 0) {
            return false;
        }

        List<PasspointConfiguration> passpointNetworks =
                mPasspointManager.getProviderConfigs(Process.WIFI_UID, true);
        // If we have any passpoint networks, then no need to proceed
        if (passpointNetworks.size() > 0) {
            return false;
        }

        Set<WifiNetworkSuggestion> suggestionsNetworks =
                mWifiNetworkSuggestionsManager.getAllApprovedNetworkSuggestions();
        // If we have any suggestion networks, then no need to proceed
        if (suggestionsNetworks.size() > 0) {
            return false;
        }

        // Next verify that open network notifier is disabled
        if (mOpenNetworkNotifier.isSettingEnabled()) {
            return false;
        }
        return true;
    }

    /**
     * Helper method to load a overlay resource for periodic scan schedule.
     * @param id of the overlay
     * @param defaultValue default value to return if config is invalid.
     * @param resName resource name for logging
     */
    private int[] loadScanScheduleArrayFromOverlay(int id, int[] defaultValue, String resName) {
        int[] result = loadIntArrayFromOverlay(id);
        if (result == null) {
            // resource is empty
            Log.w(TAG, resName + " is not configured! Using default scan schedule");
            return defaultValue;
        }
        if (!isValidScheduleArray(result)) {
            // invalid schedule
            Log.e(TAG, resName + " is misconfigured! Using default scan schedule");
            return defaultValue;
        }
        return result;
    }

    /**
     * Helper method to load a overlay resource for periodic scan schedule.
     * @param id of the overlay
     * @param defaultValue default value to return if config is invalid.
     * @param resName resource name for logging
     */
    private int[] loadScanTypeArrayFromOverlay(int id, int[] defaultValue, String resName) {
        int[] result = loadIntArrayFromOverlay(id);
        if (result == null) {
            // resource is empty
            Log.w(TAG, resName + " is not configured! Using default scan types");
            return defaultValue;
        }
        if (!isValidScanTypeArray(result)) {
            // invalid schedule
            Log.e(TAG, resName + " is misconfigured! Using default scan types");
            return defaultValue;
        }
        return result;
    }

    /**
     * Helper method to load a int[] from an overlay resource.
     * @param id of the overlay
     */
    private int[] loadIntArrayFromOverlay(int id) {
        int[] result = mContext.getResources().getIntArray(id);
        if (result == null || result.length == 0) {
            return null;
        }
        return result;
    }

    private boolean isValidScheduleArray(@NonNull int[] schedule) {
        for (int val : schedule) {
            if (val < 1) {
                return false;
            }
        }
        return true;
    }

    private boolean isValidScanTypeArray(@NonNull int[] scanTypes) {
        for (int val : scanTypes) {
            if (val < 0 || val > WifiScanner.SCAN_TYPE_MAX) {
                return false;
            }
        }
        return true;
    }

    private void loadScanSchedulesAndScanTypesIfNeeded() {
        // initialize scan schedule and scan type for connected scan.
        if (mConnectedSingleScanScheduleSec == null) {
            mConnectedSingleScanScheduleSec = loadScanScheduleArrayFromOverlay(
                    R.array.config_wifiConnectedScanIntervalScheduleSec,
                    DEFAULT_SCANNING_SCHEDULE_SEC, "mConnectedSingleScanScheduleSec");
        }
        if (mConnectedSingleScanType == null) {
            mConnectedSingleScanType = loadScanTypeArrayFromOverlay(
                    R.array.config_wifiConnectedScanType,
                    DEFAULT_SCANNING_TYPE, "mConnectedSingleScanType");
        }

        // initialize scan schedule and scan type for disconnected scan.
        if (mDisconnectedSingleScanScheduleSec == null) {
            mDisconnectedSingleScanScheduleSec = loadScanScheduleArrayFromOverlay(
                    R.array.config_wifiDisconnectedScanIntervalScheduleSec,
                    DEFAULT_SCANNING_SCHEDULE_SEC, "mDisconnectedSingleScanScheduleSec");
        }
        if (mDisconnectedSingleScanType == null) {
            mDisconnectedSingleScanType = loadScanTypeArrayFromOverlay(
                    R.array.config_wifiDisconnectedScanType,
                    DEFAULT_SCANNING_TYPE, "mDisconnectedSingleScanType");
        }

        // initialize scan schedule and scan type for connected scan when no other networks are
        // available.
        if (mConnectedSingleSavedNetworkSingleScanScheduleSec == null) {
            mConnectedSingleSavedNetworkSingleScanScheduleSec = loadScanScheduleArrayFromOverlay(
                    R.array.config_wifiSingleSavedNetworkConnectedScanIntervalScheduleSec,
                    mConnectedSingleScanScheduleSec,
                    "mConnectedSingleSavedNetworkSingleScanScheduleSec");
        }
        if (mConnectedSingleSavedNetworkSingleScanType == null) {
            mConnectedSingleSavedNetworkSingleScanType = loadScanTypeArrayFromOverlay(
                    R.array.config_wifiSingleSavedNetworkConnectedScanType,
                    mConnectedSingleScanType, "mConnectedSingleSavedNetworkSingleScanType");
        }
    }

    /**
     * Handler for WiFi state (connected/disconnected) changes
     */
    public void handleConnectionStateChanged(
            ConcreteClientModeManager clientModeManager, int state) {
        if (clientModeManager.getRole() != ROLE_CLIENT_PRIMARY) {
            Log.w(TAG, "Ignoring call from non primary Mode Manager " + clientModeManager,
                    new Throwable());
            return;
        }
        localLog("handleConnectionStateChanged: state=" + stateToString(state));
        loadScanSchedulesAndScanTypesIfNeeded();

        mWifiState = state;

        // Reset BSSID of last connection attempt and kick off
        // the watchdog timer if entering disconnected state.
        if (mWifiState == WIFI_STATE_DISCONNECTED) {
            if (!SdkLevel.isAtLeastU()) {
                scheduleWatchdogTimer();
            }
            // Switch to the disconnected scanning schedule
            setSingleScanningSchedule(mDisconnectedSingleScanScheduleSec);
            setSingleScanningType(mDisconnectedSingleScanType);
            startConnectivityScan(SCAN_IMMEDIATELY);
            ActiveModeManager.ClientRole role = clientModeManager.getRole();
            if (role == ROLE_CLIENT_PRIMARY || role == ROLE_CLIENT_SCAN_ONLY) {
                resetNetworkSwitchDialog();
            }
        } else if (mWifiState == WIFI_STATE_CONNECTED) {
            cancelWatchdogScan();
            if (useSingleSavedNetworkSchedule()) {
                // Switch to Single-Saved-Network connected schedule
                setSingleScanningSchedule(mConnectedSingleSavedNetworkSingleScanScheduleSec);
                setSingleScanningType(mConnectedSingleSavedNetworkSingleScanType);
            } else {
                // Switch to connected single scanning schedule
                setSingleScanningSchedule(mConnectedSingleScanScheduleSec);
                setSingleScanningType(mConnectedSingleScanType);
            }
            startConnectivityScan(SCAN_ON_SCHEDULE);
        } else {
            // Intermediate state, no applicable single scanning schedule
            setSingleScanningSchedule(null);
            setSingleScanningType(null);
            startConnectivityScan(SCAN_ON_SCHEDULE);
        }
    }

    /**
     * Handler when a WiFi connection attempt ended.
     *
     * @param failureCode {@link WifiMetrics.ConnectionEvent} failure code.
     * @param failureReason {@link WifiMetricsProto.ConnectionEvent} Level2FailureReason
     * @param bssid the failed network.
     * @param config identifies the failed network.
     */
    public void handleConnectionAttemptEnded(@NonNull ClientModeManager clientModeManager,
            int failureCode, int failureReason, @NonNull String bssid,
            @NonNull WifiConfiguration config) {
        List<ClientModeManager> internetConnectivityCmms =
                mActiveModeWarden.getInternetConnectivityClientModeManagers();
        if (!internetConnectivityCmms.contains(clientModeManager)) {
            Log.w(TAG, "Ignoring call from non primary Mode Manager " + clientModeManager,
                    new Throwable());
            return;
        }
        if (failureCode == WifiMetrics.ConnectionEvent.FAILURE_NONE) {
            String ssidUnquoted = WifiInfo.removeDoubleQuotes(getPrimaryWifiInfo().getSSID());
            mOpenNetworkNotifier.handleWifiConnected(ssidUnquoted);
        } else {
            mOpenNetworkNotifier.handleConnectionFailure();
            // Only attempt to reconnect when connection on the primary CMM fails, since MBB
            // CMM will be destroyed after the connection failure.
            if (clientModeManager.getRole() == ROLE_CLIENT_PRIMARY
                    && !mWifiPermissionsUtil.isAdminRestrictedNetwork(config)) {
                retryConnectionOnLatestCandidates(clientModeManager, bssid, config,
                        failureCode == FAILURE_AUTHENTICATION_FAILURE
                                && failureReason == AUTH_FAILURE_EAP_FAILURE);
            }
        }
    }

    private void retryConnectionOnLatestCandidates(@NonNull ClientModeManager clientModeManager,
            String bssid, @NonNull WifiConfiguration configuration, boolean ignoreSameNetwork) {
        try {
            if (mLatestCandidates == null || mLatestCandidates.size() == 0
                    || mClock.getElapsedSinceBootMillis() - mLatestCandidatesTimestampMs
                    > TEMP_BSSID_BLOCK_DURATION) {
                mLatestCandidates = null;
                return;
            }
            MacAddress macAddress = MacAddress.fromString(bssid);
            ScanResultMatchInfo scanResultMatchInfo =
                    ScanResultMatchInfo.fromWifiConfiguration(configuration);
            int prevNumCandidates = mLatestCandidates.size();
            mLatestCandidates = mLatestCandidates.stream()
                    .filter(candidate -> {
                        // filter out the same network if needed
                        if (ignoreSameNetwork && scanResultMatchInfo.matchForNetworkSelection(
                                candidate.getKey().matchInfo) != null) {
                            return false;
                        }
                        // filter out the candidate with the BSSID that just failed
                        if (macAddress.equals(candidate.getKey().bssid)) {
                            return false;
                        }
                        // filter out candidates that are disabled.
                        WifiConfiguration config =
                                mConfigManager.getConfiguredNetwork(candidate.getNetworkConfigId());
                        if (config == null || mConfigManager.isNetworkTemporarilyDisabledByUser(
                                config.isPasspoint() ? config.FQDN : config.SSID)) {
                            return false;
                        }
                        return config.getNetworkSelectionStatus().isNetworkEnabled()
                                && config.allowAutojoin;
                    })
                    .collect(Collectors.toList());
            if (prevNumCandidates == mLatestCandidates.size()) {
                return;
            }
            WifiConfiguration candidate = mNetworkSelector.selectNetwork(mLatestCandidates);
            if (candidate != null) {
                localLog("Automatic retry on the next best WNS candidate-" + candidate.SSID);
                // Make sure that the failed BSSID is blocked for at least TEMP_BSSID_BLOCK_DURATION
                // to prevent the supplicant from trying it again.
                mWifiBlocklistMonitor.blockBssidForDurationMs(bssid, configuration,
                        TEMP_BSSID_BLOCK_DURATION,
                        WifiBlocklistMonitor.REASON_FRAMEWORK_DISCONNECT_FAST_RECONNECT, 0);
                triggerConnectToNetworkUsingCmm(clientModeManager, candidate,
                        ClientModeImpl.SUPPLICANT_BSSID_ANY);
                // since using primary manager to connect, stop any existing managers in the
                // secondary transient role since they are no longer needed.
                mActiveModeWarden.stopAllClientModeManagersInRole(
                        ROLE_CLIENT_SECONDARY_TRANSIENT);
            }
        } catch (IllegalArgumentException e) {
            localLog("retryConnectionOnLatestCandidates: failed to create MacAddress from bssid="
                    + bssid);
            mLatestCandidates = null;
        }
    }

    /**
     * Clear all cached candidates.
     */
    public void clearCachedCandidates() {
        mLatestCandidates = null;
        mLatestCandidatesTimestampMs = 0;
    }

    // Enable auto-join if WifiConnectivityManager is enabled & we have any pending generic network
    // request (trusted or untrusted) and no specific network request in progress.
    private void checkAllStatesAndEnableAutoJoin() {
        // if auto-join was disabled externally, don't re-enable for any triggers.
        // External triggers to disable always trumps any internal state.
        setAutoJoinEnabled(mAutoJoinEnabledExternal
                && (mUntrustedConnectionAllowed || mOemPaidConnectionAllowed
                || mOemPrivateConnectionAllowed || mTrustedConnectionAllowed
                || mRestrictedConnectionAllowedUids.size() != 0 || hasMultiInternetConnection())
                && !mSpecificNetworkRequestInProgress);
        startConnectivityScan(SCAN_IMMEDIATELY);
    }

    /**
     * Triggered when {@link WifiNetworkFactory} has a pending general network request.
     */
    public void setTrustedConnectionAllowed(boolean allowed) {
        localLog("setTrustedConnectionAllowed: allowed=" + allowed);

        if (mTrustedConnectionAllowed != allowed) {
            mTrustedConnectionAllowed = allowed;
            checkAllStatesAndEnableAutoJoin();
        }
    }

    /**
     * Triggered when {@link UntrustedWifiNetworkFactory} has a pending ephemeral network request.
     */
    public void setUntrustedConnectionAllowed(boolean allowed) {
        localLog("setUntrustedConnectionAllowed: allowed=" + allowed);

        if (mUntrustedConnectionAllowed != allowed) {
            mUntrustedConnectionAllowed = allowed;
            checkAllStatesAndEnableAutoJoin();
        }
    }

    @VisibleForTesting
    public int getWifiState() {
        return mWifiState;
    }

    /**
     * Triggered when {@link RestrictedWifiNetworkFactory} has a new pending restricted network
     * request.
     * @param uid the uid of the latest requestor
     */
    public void addRestrictionConnectionAllowedUid(int uid) {
        localLog("addRestrictionConnectionAllowedUid: allowedUid=" + uid);

        int size = mRestrictedConnectionAllowedUids.size();
        mRestrictedConnectionAllowedUids.add(uid);
        if (size == 0) {
            checkAllStatesAndEnableAutoJoin();
        }
    }

    /**
     * Triggered when {@link RestrictedWifiNetworkFactory} release a restricted network request.
     * @param uid the uid of the latest released requestor
     */
    public void removeRestrictionConnectionAllowedUid(int uid) {
        localLog("removeRestrictionConnectionAllowedUid: allowedUid=" + uid);

        mRestrictedConnectionAllowedUids.remove(uid);
        if (mRestrictedConnectionAllowedUids.size() == 0) {
            checkAllStatesAndEnableAutoJoin();
        }
    }



    /**
     * Triggered when {@link OemPaidWifiNetworkFactory} has a pending network request.
     */
    public void setOemPaidConnectionAllowed(boolean allowed, WorkSource requestorWs) {
        localLog("setOemPaidConnectionAllowed: allowed=" + allowed + ", requestorWs="
                + requestorWs);

        if (mOemPaidConnectionAllowed != allowed) {
            mOemPaidConnectionAllowed = allowed;
            mOemPaidConnectionRequestorWs = requestorWs;
            checkAllStatesAndEnableAutoJoin();
        }
    }

    /**
     * Triggered when {@link OemPrivateWifiNetworkFactory} has a pending network request.
     */
    public void setOemPrivateConnectionAllowed(boolean allowed, WorkSource requestorWs) {
        localLog("setOemPrivateConnectionAllowed: allowed=" + allowed + ", requestorWs="
                + requestorWs);

        if (mOemPrivateConnectionAllowed != allowed) {
            mOemPrivateConnectionAllowed = allowed;
            mOemPrivateConnectionRequestorWs = requestorWs;
            checkAllStatesAndEnableAutoJoin();
        }
    }

    /**
     * Triggered when {@link WifiNetworkFactory} is processing a specific network request.
     */
    public void setSpecificNetworkRequestInProgress(boolean inProgress) {
        localLog("setSpecificNetworkRequestInProgress : inProgress=" + inProgress);

        if (mSpecificNetworkRequestInProgress != inProgress) {
            mSpecificNetworkRequestInProgress = inProgress;
            checkAllStatesAndEnableAutoJoin();
        }
    }

    /**
     * Handler to prepare for connection to a user or app specified network
     */
    public void prepareForForcedConnection(int netId) {
        WifiConfiguration config = mConfigManager.getConfiguredNetwork(netId);
        if (config == null) {
            return;
        }
        localLog("prepareForForcedConnection: SSID=" + config.SSID);

        clearConnectionAttemptTimeStamps();
        mWifiBlocklistMonitor.clearBssidBlocklistForSsid(config.SSID);
    }

    /**
     * Handler for on-demand connectivity scan
     */
    public void forceConnectivityScan(WorkSource workSource) {
        if (!mWifiEnabled || !mRunning) return;
        localLog("forceConnectivityScan in request of " + workSource);

        clearConnectionAttemptTimeStamps();
        mWaitForFullBandScanResults = true;
        startForcedSingleScan(true, workSource, WifiScanner.SCAN_TYPE_HIGH_ACCURACY);
    }

    /**
     * Helper method to populate WifiScanner handle. This is done lazily because
     * WifiScanningService is started after WifiService.
     */
    private void retrieveWifiScanner() {
        if (mScanner != null) return;
        mScanner = WifiLocalServices.getService(WifiScannerInternal.class);
        if (mScanner == null) {
            Log.wtf(TAG, "Got a null instance of WifiScanner!");
            return;
        }
        // Register for all single scan results
        mScanner.registerScanListener(mInternalAllSingleScanListener);
    }

    /**
     * Start WifiConnectivityManager
     */
    private void start() {
        if (mRunning) return;
        retrieveWifiScanner();
        mConnectivityHelper.getFirmwareRoamingInfo();
        mWifiChannelUtilization.init(getPrimaryClientModeManager().getWifiLinkLayerStats());
        clearConnectionAttemptTimeStamps(); // clear connection attempts.

        if (mContext.getResources().getBoolean(R.bool.config_wifiEnablePartialInitialScan)) {
            setInitialScanState(INITIAL_SCAN_STATE_START);
        }

        mRunning = true;
        mLatestCandidates = null;
        mLatestCandidatesTimestampMs = 0;
    }

    /**
     * Stop and reset WifiConnectivityManager
     */
    private void stop() {
        if (!mRunning) return;
        mRunning = false;
        stopConnectivityScan();
        cancelWatchdogScan();
        resetLastPeriodicSingleScanTimeStamp();
        mOpenNetworkNotifier.clearPendingNotification(true /* resetRepeatDelay */);
        mWaitForFullBandScanResults = false;
        mLatestCandidates = null;
        mLatestCandidatesTimestampMs = 0;
        mScanRestartCount = 0;
    }

    /**
     * Update WifiConnectivityManager running state
     *
     * Start WifiConnectivityManager only if both Wifi and WifiConnectivityManager
     * are enabled, otherwise stop it.
     */
    private void updateRunningState() {
        if (mWifiEnabled && mAutoJoinEnabled) {
            localLog("Starting up WifiConnectivityManager");
            start();
        } else {
            localLog("Stopping WifiConnectivityManager");
            stop();
        }
    }

    /**
     * Inform WiFi is enabled for connection or not
     */
    private void setWifiEnabled(boolean enable) {
        if (mWifiEnabled == enable) return;

        localLog("Set WiFi " + (enable ? "enabled" : "disabled"));

        if (!enable) {
            mNetworkSelector.resetOnDisable();
            mConfigManager.enableTemporaryDisabledNetworks();
            mConfigManager.stopRestrictingAutoJoinToSubscriptionId();
            mConfigManager.clearUserTemporarilyDisabledList();
            mConfigManager.removeAllEphemeralOrPasspointConfiguredNetworks();
            // Flush ANQP cache if configured to do so
            if (mWifiGlobals.flushAnqpCacheOnWifiToggleOffEvent()) {
                mPasspointManager.clearAnqpRequestsAndFlushCache();
            }
            if (mEnablePnoScanAfterWifiToggle) {
                mPnoScanEnabledByFramework = true;
            }
            saveMiracastMode(WifiP2pManager.MIRACAST_DISABLED);
            saveP2pGroupStarted(false);
        }

        mWifiEnabled = enable;
        updateRunningState();
    }

    /**
     * Turn on/off the WifiConnectivityManager at runtime
     */
    private void setAutoJoinEnabled(boolean enable) {
        mAutoJoinEnabled = enable;
        updateRunningState();
    }

    /**
     * Turn on/off the auto join at runtime
     */
    public void setAutoJoinEnabledExternal(boolean enable, boolean isDeviceAdmin) {
        localLog("Set auto join " + (enable ? "enabled" : "disabled"));
        if (!mAutoJoinEnabledExternal && mAutoJoinEnabledExternalSetByDeviceAdmin
                && !isDeviceAdmin) {
            localLog("Set auto join ignored since it was disabled by a device admin.");
            return;
        }
        mAutoJoinEnabledExternalSetByDeviceAdmin = isDeviceAdmin;
        if (mAutoJoinEnabledExternal != enable) {
            mAutoJoinEnabledExternal = enable;
            checkAllStatesAndEnableAutoJoin();
            if (!enable) {
                dismissNetworkSwitchDialog();
            }
        }
    }

    /**
     * Return whether auto join is on/off
     */
    public boolean getAutoJoinEnabledExternal() {
        return mAutoJoinEnabledExternal;
    }

    /**
     * Check if multi internet connection exists.
     *
     * @return true if multi internet connection exists.
     */
    public boolean hasMultiInternetConnection() {
        return mMultiInternetConnectionState != MultiInternetManager.MULTI_INTERNET_STATE_NONE;
    }

    /**
     * Check if multi internet connection is requested.
     *
     * @return true if multi internet connection is requested.
     */
    public boolean isMultiInternetConnectionRequested() {
        return mMultiInternetConnectionState
                == MultiInternetManager.MULTI_INTERNET_STATE_CONNECTION_REQUESTED;
    }

    @VisibleForTesting
    int getLowRssiNetworkRetryDelay() {
        return mPnoScanListener.getLowRssiNetworkRetryDelay();
    }

    @VisibleForTesting
    long getLastPeriodicSingleScanTimeStamp() {
        return mLastPeriodicSingleScanTimeStamp;
    }

    /**
     * Dump the local logs.
     */
    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("Dump of WifiConnectivityManager");
        pw.println("WifiConnectivityManager - Log Begin ----");
        pw.println("mIsLocationModeEnabled: " + mIsLocationModeEnabled);
        pw.println("mPnoScanEnabledByFramework: " + mPnoScanEnabledByFramework);
        pw.println("mEnablePnoScanAfterWifiToggle: " + mEnablePnoScanAfterWifiToggle);
        pw.println("mMultiInternetConnectionState " + mMultiInternetConnectionState);
        mLocalLog.dump(fd, pw, args);
        pw.println("WifiConnectivityManager - Log End ----");
        mOpenNetworkNotifier.dump(fd, pw, args);
        mWifiBlocklistMonitor.dump(fd, pw, args);
        mExternalPnoScanRequestManager.dump(fd, pw, args);
        mConnectivityHelper.dump(fd, pw, args);
    }
}
