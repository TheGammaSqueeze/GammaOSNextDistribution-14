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

package com.android.server.wifi;

import static android.net.wifi.WifiConfiguration.NetworkSelectionStatus.DISABLE_REASON_INFOS;

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.content.Context;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiConfiguration.NetworkSelectionStatus;
import android.net.wifi.WifiConfiguration.NetworkSelectionStatus.DisableReasonInfo;
import android.net.wifi.WifiConfiguration.NetworkSelectionStatus.NetworkSelectionDisableReason;
import android.net.wifi.WifiSsid;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.LocalLog;
import android.util.Log;
import android.util.SparseArray;

import com.android.internal.annotations.VisibleForTesting;
import com.android.server.wifi.util.StringUtil;
import com.android.server.wifi.util.WifiPermissionsUtil;
import com.android.wifi.resources.R;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * This class manages the addition and removal of BSSIDs to the BSSID blocklist, which is used
 * for firmware roaming and network selection.
 */
public class WifiBlocklistMonitor {
    // A special type association rejection
    public static final int REASON_AP_UNABLE_TO_HANDLE_NEW_STA = 0;
    // No internet
    public static final int REASON_NETWORK_VALIDATION_FAILURE = 1;
    // Wrong password error
    public static final int REASON_WRONG_PASSWORD = 2;
    // Incorrect EAP credentials
    public static final int REASON_EAP_FAILURE = 3;
    // Other association rejection failures
    public static final int REASON_ASSOCIATION_REJECTION = 4;
    // Association timeout failures.
    public static final int REASON_ASSOCIATION_TIMEOUT = 5;
    // Other authentication failures
    public static final int REASON_AUTHENTICATION_FAILURE = 6;
    // DHCP failures
    public static final int REASON_DHCP_FAILURE = 7;
    // Abnormal disconnect error
    public static final int REASON_ABNORMAL_DISCONNECT = 8;
    // AP initiated disconnect for a given duration.
    public static final int REASON_FRAMEWORK_DISCONNECT_MBO_OCE = 9;
    // Avoid connecting to the failed AP when trying to reconnect on other available candidates.
    public static final int REASON_FRAMEWORK_DISCONNECT_FAST_RECONNECT = 10;
    // The connected scorer has disconnected this network.
    public static final int REASON_FRAMEWORK_DISCONNECT_CONNECTED_SCORE = 11;
    // Non-local disconnection in the middle of connecting state
    public static final int REASON_NONLOCAL_DISCONNECT_CONNECTING = 12;
    // Connection attempt aborted by the watchdog because the AP didn't respond.
    public static final int REASON_FAILURE_NO_RESPONSE = 13;
    // Constant being used to keep track of how many failure reasons there are.
    public static final int NUMBER_REASON_CODES = 14;
    public static final int INVALID_REASON = -1;

    @IntDef(prefix = { "REASON_" }, value = {
            REASON_AP_UNABLE_TO_HANDLE_NEW_STA,
            REASON_NETWORK_VALIDATION_FAILURE,
            REASON_WRONG_PASSWORD,
            REASON_EAP_FAILURE,
            REASON_ASSOCIATION_REJECTION,
            REASON_ASSOCIATION_TIMEOUT,
            REASON_AUTHENTICATION_FAILURE,
            REASON_DHCP_FAILURE,
            REASON_ABNORMAL_DISCONNECT,
            REASON_FRAMEWORK_DISCONNECT_MBO_OCE,
            REASON_FRAMEWORK_DISCONNECT_FAST_RECONNECT,
            REASON_FRAMEWORK_DISCONNECT_CONNECTED_SCORE,
            REASON_NONLOCAL_DISCONNECT_CONNECTING,
            REASON_FAILURE_NO_RESPONSE
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface FailureReason {}

    // To be filled with values from the overlay.
    private static final int[] FAILURE_COUNT_DISABLE_THRESHOLD = new int[NUMBER_REASON_CODES];
    private boolean mFailureCountDisableThresholdArrayInitialized = false;
    private static final long ABNORMAL_DISCONNECT_RESET_TIME_MS = TimeUnit.HOURS.toMillis(3);
    private static final int MIN_RSSI_DIFF_TO_UNBLOCK_BSSID = 5;
    @VisibleForTesting
    public static final int NUM_CONSECUTIVE_FAILURES_PER_NETWORK_EXP_BACKOFF = 5;
    private static final String TAG = "WifiBlocklistMonitor";

    private final Context mContext;
    private final WifiLastResortWatchdog mWifiLastResortWatchdog;
    private final WifiConnectivityHelper mConnectivityHelper;
    private final Clock mClock;
    private final LocalLog mLocalLog;
    private final WifiScoreCard mWifiScoreCard;
    private final ScoringParams mScoringParams;
    private final WifiMetrics mWifiMetrics;
    private final WifiPermissionsUtil mWifiPermissionsUtil;
    private ScanRequestProxy mScanRequestProxy;
    private final Map<Integer, BssidDisableReason> mBssidDisableReasons =
            buildBssidDisableReasons();
    private final SparseArray<DisableReasonInfo> mDisableReasonInfo;
    private final WifiGlobals mWifiGlobals;

    // Map of bssid to BssidStatus
    private Map<String, BssidStatus> mBssidStatusMap = new ArrayMap<>();
    private Set<String> mDisabledSsids = new ArraySet<>();

    // Internal logger to make sure imporatant logs do not get lost.
    private BssidBlocklistMonitorLogger mBssidBlocklistMonitorLogger =
            new BssidBlocklistMonitorLogger(60);

    // Map of ssid to Allowlist SSIDs
    private Map<String, List<String>> mSsidAllowlistMap = new ArrayMap<>();
    private Set<WifiSsid> mSsidsAllowlistForNetworkSelection = new ArraySet<>();

    /**
     * Verbose logging flag. Toggled by developer options.
     */
    private boolean mVerboseLoggingEnabled = false;


    private Map<Integer, BssidDisableReason> buildBssidDisableReasons() {
        Map<Integer, BssidDisableReason> result = new ArrayMap<>();
        result.put(REASON_AP_UNABLE_TO_HANDLE_NEW_STA, new BssidDisableReason(
                "REASON_AP_UNABLE_TO_HANDLE_NEW_STA", false, false));
        result.put(REASON_NETWORK_VALIDATION_FAILURE, new BssidDisableReason(
                "REASON_NETWORK_VALIDATION_FAILURE", true, false));
        result.put(REASON_WRONG_PASSWORD, new BssidDisableReason(
                "REASON_WRONG_PASSWORD", false, true));
        result.put(REASON_EAP_FAILURE, new BssidDisableReason(
                "REASON_EAP_FAILURE", true, true));
        result.put(REASON_ASSOCIATION_REJECTION, new BssidDisableReason(
                "REASON_ASSOCIATION_REJECTION", true, true));
        result.put(REASON_ASSOCIATION_TIMEOUT, new BssidDisableReason(
                "REASON_ASSOCIATION_TIMEOUT", true, true));
        result.put(REASON_AUTHENTICATION_FAILURE, new BssidDisableReason(
                "REASON_AUTHENTICATION_FAILURE", true, true));
        result.put(REASON_DHCP_FAILURE, new BssidDisableReason(
                "REASON_DHCP_FAILURE", true, false));
        result.put(REASON_ABNORMAL_DISCONNECT, new BssidDisableReason(
                "REASON_ABNORMAL_DISCONNECT", true, false));
        result.put(REASON_FRAMEWORK_DISCONNECT_MBO_OCE, new BssidDisableReason(
                "REASON_FRAMEWORK_DISCONNECT_MBO_OCE", false, false));
        result.put(REASON_FRAMEWORK_DISCONNECT_FAST_RECONNECT, new BssidDisableReason(
                "REASON_FRAMEWORK_DISCONNECT_FAST_RECONNECT", false, false));
        result.put(REASON_FRAMEWORK_DISCONNECT_CONNECTED_SCORE, new BssidDisableReason(
                "REASON_FRAMEWORK_DISCONNECT_CONNECTED_SCORE", true, false));
        // TODO: b/174166637, add the same reason code in SSID blocklist and mark ignoreIfOnlyBssid
        // to true once it is covered in SSID blocklist.
        result.put(REASON_NONLOCAL_DISCONNECT_CONNECTING, new BssidDisableReason(
                "REASON_NONLOCAL_DISCONNECT_CONNECTING", true, false));
        result.put(REASON_FAILURE_NO_RESPONSE, new BssidDisableReason(
                "REASON_FAILURE_NO_RESPONSE", true, true));
        return result;
    }

    class BssidDisableReason {
        public final String reasonString;
        public final boolean isLowRssiSensitive;
        public final boolean ignoreIfOnlyBssid;

        BssidDisableReason(String reasonString, boolean isLowRssiSensitive,
                boolean ignoreIfOnlyBssid) {
            this.reasonString = reasonString;
            this.isLowRssiSensitive = isLowRssiSensitive;
            this.ignoreIfOnlyBssid = ignoreIfOnlyBssid;
        }
    }

    /** Map of BSSID to affiliated BSSIDs. */
    private Map<String, List<String>> mAffiliatedBssidMap = new ArrayMap<>();

    /**
     * Set the mapping of BSSID to affiliated BSSIDs.
     *
     * @param bssid A unique identifier of the AP.
     * @param bssids List of affiliated BSSIDs.
     */
    public void setAffiliatedBssids(@NonNull String bssid, @NonNull List<String> bssids) {
        mAffiliatedBssidMap.put(bssid, bssids);
    }

    /**
     *  Get affiliated BSSIDs mapped to a BSSID.
     *
     * @param bssid A unique identifier of the AP.
     * @return List of affiliated BSSIDs or an empty list.
     */
    public List<String> getAffiliatedBssids(@NonNull String bssid) {
        List<String> affiliatedBssids = mAffiliatedBssidMap.get(bssid);
        return affiliatedBssids == null ? Collections.EMPTY_LIST : affiliatedBssids;
    }

    /**
     * Remove affiliated BSSIDs mapped to a BSSID.
     *
     * @param bssid A unique identifier of the AP.
     */
    public void removeAffiliatedBssids(@NonNull String bssid) {
        mAffiliatedBssidMap.remove(bssid);
    }

    /** Clear affiliated BSSID mapping table. */
    public void clearAffiliatedBssids() {
        mAffiliatedBssidMap.clear();
    }

    /** Sets the ScanRequestProxy **/
    public void setScanRequestProxy(ScanRequestProxy scanRequestProxy) {
        mScanRequestProxy = scanRequestProxy;
    }

    /**
     * Create a new instance of WifiBlocklistMonitor
     */
    WifiBlocklistMonitor(Context context, WifiConnectivityHelper connectivityHelper,
            WifiLastResortWatchdog wifiLastResortWatchdog, Clock clock, LocalLog localLog,
            WifiScoreCard wifiScoreCard, ScoringParams scoringParams, WifiMetrics wifiMetrics,
            WifiPermissionsUtil wifiPermissionsUtil, WifiGlobals wifiGlobals) {
        mContext = context;
        mConnectivityHelper = connectivityHelper;
        mWifiLastResortWatchdog = wifiLastResortWatchdog;
        mClock = clock;
        mLocalLog = localLog;
        mWifiScoreCard = wifiScoreCard;
        mScoringParams = scoringParams;
        mDisableReasonInfo = DISABLE_REASON_INFOS.clone();
        mWifiMetrics = wifiMetrics;
        mWifiPermissionsUtil = wifiPermissionsUtil;
        mWifiGlobals = wifiGlobals;
        loadCustomConfigsForDisableReasonInfos();
    }

    // A helper to log debugging information in the local log buffer, which can
    // be retrieved in bugreport.
    private void localLog(String log) {
        mLocalLog.log(log);
    }

    /**
     * calculates the blocklist duration based on the current failure streak with exponential
     * backoff.
     * @param failureStreak should be greater or equal to 0.
     * @return duration to block the BSSID in milliseconds
     */
    private long getBlocklistDurationWithExponentialBackoff(int failureStreak,
            int baseBlocklistDurationMs) {
        failureStreak = Math.min(failureStreak, mContext.getResources().getInteger(
                R.integer.config_wifiBssidBlocklistMonitorFailureStreakCap));
        if (failureStreak < 1) {
            return baseBlocklistDurationMs;
        }
        return (long) (Math.pow(2.0, (double) failureStreak) * baseBlocklistDurationMs);
    }

    /**
     * Dump the local log buffer and other internal state of WifiBlocklistMonitor.
     */
    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("Dump of WifiBlocklistMonitor");
        mLocalLog.dump(fd, pw, args);
        pw.println("WifiBlocklistMonitor - Bssid blocklist begin ----");
        mBssidStatusMap.values().stream().forEach(entry -> pw.println(entry));
        pw.println("WifiBlocklistMonitor - Bssid blocklist end ----");
        pw.println("Dump of BSSID to Affiliated BSSID mapping");
        mAffiliatedBssidMap.forEach((bssid, aList) -> pw.println(bssid + " -> " + aList));
        mBssidBlocklistMonitorLogger.dump(pw);
    }

    private void addToBlocklist(@NonNull BssidStatus entry, long durationMs,
            @FailureReason int reason, int rssi) {
        entry.setAsBlocked(durationMs, reason, rssi);
        localLog(TAG + " addToBlocklist: bssid=" + entry.bssid + ", ssid=" + entry.ssid
                + ", durationMs=" + durationMs + ", reason=" + getFailureReasonString(reason)
                + ", rssi=" + rssi);
    }

    /**
     * increments the number of failures for the given bssid and returns the number of failures so
     * far.
     * @return the BssidStatus for the BSSID
     */
    private @NonNull BssidStatus incrementFailureCountForBssid(
            @NonNull String bssid, @NonNull String ssid, int reasonCode) {
        BssidStatus status = getOrCreateBssidStatus(bssid, ssid);
        status.incrementFailureCount(reasonCode);
        return status;
    }

    /**
     * Get the BssidStatus representing the BSSID or create a new one if it doesn't exist.
     */
    private @NonNull BssidStatus getOrCreateBssidStatus(@NonNull String bssid,
            @NonNull String ssid) {
        BssidStatus status = mBssidStatusMap.get(bssid);
        if (status == null || !ssid.equals(status.ssid)) {
            if (status != null) {
                localLog("getOrCreateBssidStatus: BSSID=" + bssid + ", SSID changed from "
                        + status.ssid + " to " + ssid);
            }
            status = new BssidStatus(bssid, ssid);
            mBssidStatusMap.put(bssid, status);
        }
        return status;
    }

    /**
     * Set a list of SSIDs that will always be enabled for network selection.
     */
    public void setSsidsAllowlist(@NonNull List<WifiSsid> ssids) {
        mSsidsAllowlistForNetworkSelection = new ArraySet<>(ssids);
    }

    /**
     * Get the list of SSIDs that will always be enabled for network selection.
     */
    public List<WifiSsid> getSsidsAllowlist() {
        return new ArrayList<>(mSsidsAllowlistForNetworkSelection);
    }

    private boolean isValidNetworkAndFailureReasonForBssidBlocking(String bssid,
            WifiConfiguration config, @FailureReason int reasonCode) {
        if (bssid == null || config == null
                || bssid.equals(ClientModeImpl.SUPPLICANT_BSSID_ANY)
                || reasonCode < 0 || reasonCode >= NUMBER_REASON_CODES) {
            Log.e(TAG, "Invalid input: BSSID=" + bssid + ", config=" + config
                    + ", reasonCode=" + reasonCode);
            return false;
        }
        return !isConfigExemptFromBlocklist(config);
    }

    private boolean isConfigExemptFromBlocklist(@NonNull WifiConfiguration config) {
        try {
            // Only enterprise owned configs that are in the doNoBlocklist are exempt from
            // blocklisting.
            WifiSsid wifiSsid = WifiSsid.fromString(config.SSID);
            return mSsidsAllowlistForNetworkSelection.contains(wifiSsid)
                    && mWifiPermissionsUtil.isAdmin(config.creatorUid, config.creatorName);
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Failed to convert raw ssid=" + config.SSID + " to WifiSsid");
            return false;
        }
    }

    private boolean shouldWaitForWatchdogToTriggerFirst(String bssid,
            @FailureReason int reasonCode) {
        boolean isWatchdogRelatedFailure = reasonCode == REASON_ASSOCIATION_REJECTION
                || reasonCode == REASON_AUTHENTICATION_FAILURE
                || reasonCode == REASON_DHCP_FAILURE;
        return isWatchdogRelatedFailure && mWifiLastResortWatchdog.shouldIgnoreBssidUpdate(bssid);
    }

    /**
     * Block any attempts to auto-connect to the BSSID for the specified duration.
     * This is meant to be used by features that need wifi to avoid a BSSID for a certain duration,
     * and thus will not increase the failure streak counters.
     * @param bssid identifies the AP to block.
     * @param config identifies the WifiConfiguration.
     * @param durationMs duration in millis to block.
     * @param blockReason reason for blocking the BSSID.
     * @param rssi the latest RSSI observed.
     */
    public void blockBssidForDurationMs(@NonNull String bssid, WifiConfiguration config,
            long durationMs, @FailureReason int blockReason, int rssi) {
        if (durationMs <= 0 || !isValidNetworkAndFailureReasonForBssidBlocking(
                bssid, config, blockReason)) {
            Log.e(TAG, "Invalid input: BSSID=" + bssid + ", config=" + config
                    + ", durationMs=" + durationMs + ", blockReason=" + blockReason
                    + ", rssi=" + rssi);
            return;
        }
        BssidStatus status = getOrCreateBssidStatus(bssid, config.SSID);
        if (status.isInBlocklist
                && status.blocklistEndTimeMs - mClock.getWallClockMillis() > durationMs) {
            // Return because this BSSID is already being blocked for a longer time.
            return;
        }
        addToBlocklist(status, durationMs, blockReason, rssi);
        /**
         * Add affiliated BSSIDs also into the block list with the same parameters as connected
         * BSSID.
         */
        for (String affiliatedBssid : getAffiliatedBssids(bssid)) {
            status = getOrCreateBssidStatus(affiliatedBssid, config.SSID);
            addToBlocklist(status, durationMs, blockReason, rssi);
        }
    }

    private String getFailureReasonString(@FailureReason int reasonCode) {
        if (reasonCode == INVALID_REASON) {
            return "INVALID_REASON";
        }
        BssidDisableReason disableReason = mBssidDisableReasons.get(reasonCode);
        if (disableReason == null) {
            return "REASON_UNKNOWN";
        }
        return disableReason.reasonString;
    }

    private int getFailureThresholdForReason(@FailureReason int reasonCode) {
        if (mFailureCountDisableThresholdArrayInitialized) {
            return FAILURE_COUNT_DISABLE_THRESHOLD[reasonCode];
        }
        FAILURE_COUNT_DISABLE_THRESHOLD[REASON_AP_UNABLE_TO_HANDLE_NEW_STA] =
                mContext.getResources().getInteger(
                        R.integer.config_wifiBssidBlocklistMonitorApUnableToHandleNewStaThreshold);
        FAILURE_COUNT_DISABLE_THRESHOLD[REASON_NETWORK_VALIDATION_FAILURE] =
                mContext.getResources().getInteger(R.integer
                        .config_wifiBssidBlocklistMonitorNetworkValidationFailureThreshold);
        FAILURE_COUNT_DISABLE_THRESHOLD[REASON_WRONG_PASSWORD] =
                mContext.getResources().getInteger(
                        R.integer.config_wifiBssidBlocklistMonitorWrongPasswordThreshold);
        FAILURE_COUNT_DISABLE_THRESHOLD[REASON_EAP_FAILURE] =
                mContext.getResources().getInteger(
                        R.integer.config_wifiBssidBlocklistMonitorEapFailureThreshold);
        FAILURE_COUNT_DISABLE_THRESHOLD[REASON_ASSOCIATION_REJECTION] =
                mContext.getResources().getInteger(
                        R.integer.config_wifiBssidBlocklistMonitorAssociationRejectionThreshold);
        FAILURE_COUNT_DISABLE_THRESHOLD[REASON_ASSOCIATION_TIMEOUT] =
                mContext.getResources().getInteger(
                        R.integer.config_wifiBssidBlocklistMonitorAssociationTimeoutThreshold);
        FAILURE_COUNT_DISABLE_THRESHOLD[REASON_AUTHENTICATION_FAILURE] =
                mContext.getResources().getInteger(
                        R.integer.config_wifiBssidBlocklistMonitorAuthenticationFailureThreshold);
        FAILURE_COUNT_DISABLE_THRESHOLD[REASON_DHCP_FAILURE] =
                mContext.getResources().getInteger(
                        R.integer.config_wifiBssidBlocklistMonitorDhcpFailureThreshold);
        FAILURE_COUNT_DISABLE_THRESHOLD[REASON_ABNORMAL_DISCONNECT] =
                mContext.getResources().getInteger(
                        R.integer.config_wifiBssidBlocklistMonitorAbnormalDisconnectThreshold);
        FAILURE_COUNT_DISABLE_THRESHOLD[REASON_NONLOCAL_DISCONNECT_CONNECTING] =
                mContext.getResources().getInteger(R.integer
                        .config_wifiBssidBlocklistMonitorNonlocalDisconnectConnectingThreshold);
        FAILURE_COUNT_DISABLE_THRESHOLD[REASON_FAILURE_NO_RESPONSE] =
                mContext.getResources().getInteger(R.integer
                        .config_wifiBssidBlocklistMonitorNoResponseThreshold);
        mFailureCountDisableThresholdArrayInitialized = true;
        return FAILURE_COUNT_DISABLE_THRESHOLD[reasonCode];
    }

    private boolean handleBssidConnectionFailureInternal(String bssid, String ssid,
            @FailureReason int reasonCode, int rssi) {
        BssidStatus entry = incrementFailureCountForBssid(bssid, ssid, reasonCode);
        int failureThreshold = getFailureThresholdForReason(reasonCode);
        int currentStreak = mWifiScoreCard.getBssidBlocklistStreak(ssid, bssid, reasonCode);
        if (currentStreak > 0 || entry.failureCount[reasonCode] >= failureThreshold) {
            // To rule out potential device side issues, don't add to blocklist if
            // WifiLastResortWatchdog is still not triggered
            if (shouldWaitForWatchdogToTriggerFirst(bssid, reasonCode)) {
                localLog("Ignoring failure to wait for watchdog to trigger first.");
                return false;
            }
            // rssi may be unavailable for the first ever connection to a newly added network
            // because it hasn't been cached inside the ScanDetailsCache yet. In this case, try to
            // read the RSSI from the latest scan results.
            if (rssi == WifiConfiguration.INVALID_RSSI && bssid != null) {
                if (mScanRequestProxy != null) {
                    ScanResult scanResult = mScanRequestProxy.getScanResult(bssid);
                    if (scanResult != null) {
                        rssi = scanResult.level;
                    }
                } else {
                    localLog("mScanRequestProxy is null");
                    Log.w(TAG, "mScanRequestProxy is null");
                }
            }
            int baseBlockDurationMs = getBaseBlockDurationForReason(reasonCode);
            long expBackoff = getBlocklistDurationWithExponentialBackoff(currentStreak,
                    baseBlockDurationMs);
            addToBlocklist(entry, expBackoff, reasonCode, rssi);
            mWifiScoreCard.incrementBssidBlocklistStreak(ssid, bssid, reasonCode);

            /**
             * Block list affiliated BSSID with same parameters, e.g. reason code, rssi ..etc.
             * as connected BSSID.
             */
            for (String affiliatedBssid : getAffiliatedBssids(bssid)) {
                BssidStatus affEntry = getOrCreateBssidStatus(affiliatedBssid, ssid);
                affEntry.failureCount[reasonCode] = entry.failureCount[reasonCode];
                addToBlocklist(affEntry, expBackoff, reasonCode, rssi);
                mWifiScoreCard.incrementBssidBlocklistStreak(ssid, affiliatedBssid, reasonCode);
            }

            return true;
        }
        return false;
    }

    private int getBaseBlockDurationForReason(int blockReason) {
        switch (blockReason) {
            case REASON_FRAMEWORK_DISCONNECT_CONNECTED_SCORE:
                return mContext.getResources().getInteger(R.integer
                        .config_wifiBssidBlocklistMonitorConnectedScoreBaseBlockDurationMs);
            case REASON_NETWORK_VALIDATION_FAILURE:
                return mContext.getResources().getInteger(
                        R.integer.config_wifiBssidBlocklistMonitorValidationFailureBaseBlockDurationMs);
            default:
                return mContext.getResources().getInteger(
                    R.integer.config_wifiBssidBlocklistMonitorBaseBlockDurationMs);
        }
    }

    /**
     * Note a failure event on a bssid and perform appropriate actions.
     * @return True if the blocklist has been modified.
     */
    public boolean handleBssidConnectionFailure(String bssid, WifiConfiguration config,
            @FailureReason int reasonCode, int rssi) {
        if (!isValidNetworkAndFailureReasonForBssidBlocking(bssid, config, reasonCode)) {
            return false;
        }
        String ssid = config.SSID;
        BssidDisableReason bssidDisableReason = mBssidDisableReasons.get(reasonCode);
        if (bssidDisableReason == null) {
            Log.e(TAG, "Bssid disable reason not found. ReasonCode=" + reasonCode);
            return false;
        }
        if (bssidDisableReason.ignoreIfOnlyBssid && !mDisabledSsids.contains(ssid)
                && mWifiLastResortWatchdog.isBssidOnlyApOfSsid(bssid)) {
            localLog("Ignoring BSSID failure due to no other APs available. BSSID=" + bssid);
            return false;
        }
        if (reasonCode == REASON_ABNORMAL_DISCONNECT) {
            long connectionTime = mWifiScoreCard.getBssidConnectionTimestampMs(ssid, bssid);
            // only count disconnects that happen shortly after a connection.
            if (mClock.getWallClockMillis() - connectionTime
                    > mContext.getResources().getInteger(
                            R.integer.config_wifiBssidBlocklistAbnormalDisconnectTimeWindowMs)) {
                return false;
            }
        }
        return handleBssidConnectionFailureInternal(bssid, ssid, reasonCode, rssi);
    }

    /**
     * To be called when a WifiConfiguration is either temporarily disabled or permanently disabled.
     * @param ssid of the WifiConfiguration that is disabled.
     */
    public void handleWifiConfigurationDisabled(String ssid) {
        if (ssid != null) {
            mDisabledSsids.add(ssid);
        }
    }

    /**
     * Note a connection success event on a bssid and clear appropriate failure counters.
     */
    public void handleBssidConnectionSuccess(@NonNull String bssid, @NonNull String ssid) {
        mDisabledSsids.remove(ssid);
        resetFailuresAfterConnection(bssid, ssid);
        for (String affiliatedBssid : getAffiliatedBssids(bssid)) {
            resetFailuresAfterConnection(affiliatedBssid, ssid);
        }
    }

    /**
     * Reset all failure counters related to a connection.
     *
     * @param bssid A unique identifier of the AP.
     * @param ssid Network name.
     */
    private void resetFailuresAfterConnection(@NonNull String bssid, @NonNull String ssid) {

        /**
         * First reset the blocklist streak.
         * This needs to be done even if a BssidStatus is not found, since the BssidStatus may
         * have been removed due to blocklist timeout.
         */
        mWifiScoreCard.resetBssidBlocklistStreak(ssid, bssid, REASON_AP_UNABLE_TO_HANDLE_NEW_STA);
        mWifiScoreCard.resetBssidBlocklistStreak(ssid, bssid, REASON_WRONG_PASSWORD);
        mWifiScoreCard.resetBssidBlocklistStreak(ssid, bssid, REASON_EAP_FAILURE);
        mWifiScoreCard.resetBssidBlocklistStreak(ssid, bssid, REASON_ASSOCIATION_REJECTION);
        mWifiScoreCard.resetBssidBlocklistStreak(ssid, bssid, REASON_ASSOCIATION_TIMEOUT);
        mWifiScoreCard.resetBssidBlocklistStreak(ssid, bssid, REASON_AUTHENTICATION_FAILURE);
        mWifiScoreCard.resetBssidBlocklistStreak(ssid, bssid,
                REASON_NONLOCAL_DISCONNECT_CONNECTING);
        mWifiScoreCard.resetBssidBlocklistStreak(ssid, bssid, REASON_FAILURE_NO_RESPONSE);

        long connectionTime = mClock.getWallClockMillis();
        long prevConnectionTime = mWifiScoreCard.setBssidConnectionTimestampMs(
                ssid, bssid, connectionTime);
        if (connectionTime - prevConnectionTime > ABNORMAL_DISCONNECT_RESET_TIME_MS) {
            mWifiScoreCard.resetBssidBlocklistStreak(ssid, bssid, REASON_ABNORMAL_DISCONNECT);
            mWifiScoreCard.resetBssidBlocklistStreak(ssid, bssid,
                    REASON_FRAMEWORK_DISCONNECT_CONNECTED_SCORE);
        }

        BssidStatus status = mBssidStatusMap.get(bssid);
        if (status == null) {
            return;
        }
        // Clear the L2 failure counters
        status.failureCount[REASON_AP_UNABLE_TO_HANDLE_NEW_STA] = 0;
        status.failureCount[REASON_WRONG_PASSWORD] = 0;
        status.failureCount[REASON_EAP_FAILURE] = 0;
        status.failureCount[REASON_ASSOCIATION_REJECTION] = 0;
        status.failureCount[REASON_ASSOCIATION_TIMEOUT] = 0;
        status.failureCount[REASON_AUTHENTICATION_FAILURE] = 0;
        status.failureCount[REASON_NONLOCAL_DISCONNECT_CONNECTING] = 0;
        status.failureCount[REASON_FAILURE_NO_RESPONSE] = 0;
        if (connectionTime - prevConnectionTime > ABNORMAL_DISCONNECT_RESET_TIME_MS) {
            status.failureCount[REASON_ABNORMAL_DISCONNECT] = 0;
        }
    }

    /**
     * Note a successful network validation on a BSSID and clear appropriate failure counters.
     * And then remove the BSSID from blocklist.
     */
    public void handleNetworkValidationSuccess(@NonNull String bssid, @NonNull String ssid) {
        resetNetworkValidationFailures(bssid, ssid);
        /**
         * Network validation may take more than 1 tries to succeed.
         * remove the BSSID from blocklist to make sure we are not accidentally blocking good
         * BSSIDs.
         **/
        removeFromBlocklist(bssid, "Network validation success");

        for (String affiliatedBssid : getAffiliatedBssids(bssid)) {
            resetNetworkValidationFailures(affiliatedBssid, ssid);
            removeFromBlocklist(affiliatedBssid, "Network validation success");
        }
    }

    /**
     * Clear failure counters related to network validation.
     *
     * @param bssid A unique identifier of the AP.
     * @param ssid Network name.
     */
    private void resetNetworkValidationFailures(@NonNull String bssid, @NonNull String ssid) {
        mWifiScoreCard.resetBssidBlocklistStreak(ssid, bssid, REASON_NETWORK_VALIDATION_FAILURE);
        BssidStatus status = mBssidStatusMap.get(bssid);
        if (status == null) {
            return;
        }
        status.failureCount[REASON_NETWORK_VALIDATION_FAILURE] = 0;
    }

    /**
     * Remove BSSID from block list.
     *
     * @param bssid A unique identifier of the AP.
     * @param reasonString A string to be logged while removing the entry from the block list.
     */
    private void removeFromBlocklist(@NonNull String bssid, final String reasonString) {
        BssidStatus status = mBssidStatusMap.get(bssid);
        if (status == null) {
            return;
        }

        if (status.isInBlocklist) {
            mBssidBlocklistMonitorLogger.logBssidUnblocked(status, reasonString);
            mBssidStatusMap.remove(bssid);
        }
    }

    /**
     * Note a successful DHCP provisioning and clear appropriate failure counters.
     */
    public void handleDhcpProvisioningSuccess(@NonNull String bssid, @NonNull String ssid) {
        resetDhcpFailures(bssid, ssid);
        for (String affiliatedBssid : getAffiliatedBssids(bssid)) {
            resetDhcpFailures(affiliatedBssid, ssid);
        }
    }

    /**
     * Reset failure counters related to DHCP.
     */
    private void resetDhcpFailures(@NonNull String bssid, @NonNull String ssid) {
        mWifiScoreCard.resetBssidBlocklistStreak(ssid, bssid, REASON_DHCP_FAILURE);
        BssidStatus status = mBssidStatusMap.get(bssid);
        if (status == null) {
            return;
        }
        status.failureCount[REASON_DHCP_FAILURE] = 0;
    }

    /**
     * Note the removal of a network from the Wifi stack's internal database and reset
     * appropriate failure counters.
     * @param ssid
     */
    public void handleNetworkRemoved(@NonNull String ssid) {
        clearBssidBlocklistForSsid(ssid);
        mWifiScoreCard.resetBssidBlocklistStreakForSsid(ssid);
    }

    /**
     * Clears the blocklist for BSSIDs associated with the input SSID only.
     * @param ssid
     */
    public void clearBssidBlocklistForSsid(@NonNull String ssid) {
        int prevSize = mBssidStatusMap.size();
        mBssidStatusMap.entrySet().removeIf(e -> {
            BssidStatus status = e.getValue();
            if (status.ssid == null) {
                return false;
            }
            if (status.ssid.equals(ssid)) {
                mBssidBlocklistMonitorLogger.logBssidUnblocked(
                        status, "clearBssidBlocklistForSsid");
                return true;
            }
            return false;
        });
        int diff = prevSize - mBssidStatusMap.size();
        if (diff > 0) {
            localLog(TAG + " clearBssidBlocklistForSsid: SSID=" + ssid
                    + ", num BSSIDs cleared=" + diff);
        }
    }

    /**
     * Clears the BSSID blocklist and failure counters.
     */
    public void clearBssidBlocklist() {
        if (mBssidStatusMap.size() > 0) {
            int prevSize = mBssidStatusMap.size();
            for (BssidStatus status : mBssidStatusMap.values()) {
                mBssidBlocklistMonitorLogger.logBssidUnblocked(status, "clearBssidBlocklist");
            }
            mBssidStatusMap.clear();
            localLog(TAG + " clearBssidBlocklist: num BSSIDs cleared="
                    + (prevSize - mBssidStatusMap.size()));
        }
        mDisabledSsids.clear();
    }

    /**
     * @param ssid
     * @return the number of BSSIDs currently in the blocklist for the |ssid|.
     */
    public int updateAndGetNumBlockedBssidsForSsid(@NonNull String ssid) {
        return (int) updateAndGetBssidBlocklistInternal()
                .filter(entry -> ssid.equals(entry.ssid)).count();
    }

    private int getNumBlockedBssidsForSsids(@NonNull Set<String> ssids) {
        if (ssids.isEmpty()) {
            return 0;
        }
        return (int) mBssidStatusMap.values().stream()
                .filter(entry -> entry.isInBlocklist && ssids.contains(entry.ssid))
                .count();
    }

    /**
     * Overloaded version of updateAndGetBssidBlocklist.
     * Accepts a @Nullable String ssid as input, and updates the firmware roaming
     * configuration if the blocklist for the input ssid has been changed.
     * @param ssids set of ssids to update firmware roaming configuration for.
     * @return Set of BSSIDs currently in the blocklist
     */
    public Set<String> updateAndGetBssidBlocklistForSsids(@NonNull Set<String> ssids) {
        int numBefore = getNumBlockedBssidsForSsids(ssids);
        Set<String> bssidBlocklist = updateAndGetBssidBlocklist();
        if (getNumBlockedBssidsForSsids(ssids) != numBefore) {
            updateFirmwareRoamingConfiguration(ssids);
        }
        return bssidBlocklist;
    }

    /**
     * Gets the BSSIDs that are currently in the blocklist.
     * @return Set of BSSIDs currently in the blocklist
     */
    public Set<String> updateAndGetBssidBlocklist() {
        return updateAndGetBssidBlocklistInternal()
                .map(entry -> entry.bssid)
                .collect(Collectors.toSet());
    }

    /**
     * Gets the list of block reasons for BSSIDs currently in the blocklist.
     * @return The set of unique reasons for blocking BSSIDs with this SSID.
     */
    public Set<Integer> getFailureReasonsForSsid(@NonNull String ssid) {
        if (ssid == null) {
            return Collections.emptySet();
        }
        return mBssidStatusMap.values().stream()
                .filter(entry -> entry.isInBlocklist && ssid.equals(entry.ssid))
                .map(entry -> entry.blockReason)
                .collect(Collectors.toSet());
    }

    /**
     * Attempts to re-enable BSSIDs that likely experienced failures due to low RSSI.
     * @param scanDetails
     * @return the list of ScanDetails for which BSSIDs were re-enabled.
     */
    public @NonNull List<ScanDetail> tryEnablingBlockedBssids(List<ScanDetail> scanDetails) {
        if (scanDetails == null) {
            return Collections.EMPTY_LIST;
        }
        List<ScanDetail> results = new ArrayList<>();
        for (ScanDetail scanDetail : scanDetails) {
            ScanResult scanResult = scanDetail.getScanResult();
            if (scanResult == null) {
                continue;
            }
            BssidStatus status = mBssidStatusMap.get(scanResult.BSSID);
            if (status == null || !status.isInBlocklist
                    || !isLowRssiSensitiveFailure(status.blockReason)) {
                continue;
            }
            int sufficientRssi = mScoringParams.getSufficientRssi(scanResult.frequency);
            int goodRssi = mScoringParams.getGoodRssi(scanResult.frequency);
            boolean rssiMinDiffAchieved = scanResult.level - status.lastRssi
                    >= MIN_RSSI_DIFF_TO_UNBLOCK_BSSID;
            boolean sufficientRssiBreached =
                    status.lastRssi < sufficientRssi && scanResult.level >= sufficientRssi;
            boolean goodRssiBreached = status.lastRssi < goodRssi && scanResult.level >= goodRssi;
            if (rssiMinDiffAchieved && (sufficientRssiBreached || goodRssiBreached)) {
                removeFromBlocklist(status.bssid, "rssi significantly improved");
                for (String affiliatedBssid : getAffiliatedBssids(status.bssid)) {
                    removeFromBlocklist(affiliatedBssid, "rssi significantly improved");
                }
                results.add(scanDetail);
            }
        }
        return results;
    }

    private boolean isLowRssiSensitiveFailure(int blockReason) {
        return mBssidDisableReasons.get(blockReason) == null ? false
                : mBssidDisableReasons.get(blockReason).isLowRssiSensitive;
    }

    /**
     * Removes expired BssidStatus entries and then return remaining entries in the blocklist.
     * @return Stream of BssidStatus for BSSIDs that are in the blocklist.
     */
    private Stream<BssidStatus> updateAndGetBssidBlocklistInternal() {
        Stream.Builder<BssidStatus> builder = Stream.builder();
        long curTime = mClock.getWallClockMillis();
        mBssidStatusMap.entrySet().removeIf(e -> {
            BssidStatus status = e.getValue();
            if (status.isInBlocklist) {
                if (status.blocklistEndTimeMs < curTime) {
                    mBssidBlocklistMonitorLogger.logBssidUnblocked(
                            status, "updateAndGetBssidBlocklistInternal");
                    return true;
                }
                builder.accept(status);
            }
            return false;
        });
        return builder.build();
    }

    /**
     * Sends the BSSIDs belonging to the input SSID down to the firmware to prevent auto-roaming
     * to those BSSIDs.
     * @param ssids
     */
    public void updateFirmwareRoamingConfiguration(@NonNull Set<String> ssids) {
        if (!mConnectivityHelper.isFirmwareRoamingSupported()) {
            return;
        }
        ArrayList<String> bssidBlocklist = updateAndGetBssidBlocklistInternal()
                .filter(entry -> ssids.contains(entry.ssid))
                .sorted((o1, o2) -> (int) (o2.blocklistEndTimeMs - o1.blocklistEndTimeMs))
                .map(entry -> entry.bssid)
                .collect(Collectors.toCollection(ArrayList::new));
        int fwMaxBlocklistSize = mConnectivityHelper.getMaxNumBlocklistBssid();
        if (fwMaxBlocklistSize <= 0) {
            Log.e(TAG, "Invalid max BSSID blocklist size:  " + fwMaxBlocklistSize);
            return;
        }
        // Having the blocklist size exceeding firmware max limit is unlikely because we have
        // already flitered based on SSID. But just in case this happens, we are prioritizing
        // sending down BSSIDs blocked for the longest time.
        if (bssidBlocklist.size() > fwMaxBlocklistSize) {
            bssidBlocklist = new ArrayList<String>(bssidBlocklist.subList(0,
                    fwMaxBlocklistSize));
        }

        // Collect all the allowed SSIDs
        Set<String> allowedSsidSet = new HashSet<>();
        for (String ssid : ssids) {
            List<String> allowedSsidsForSsid = mSsidAllowlistMap.get(ssid);
            if (allowedSsidsForSsid != null) {
                allowedSsidSet.addAll(allowedSsidsForSsid);
            }
        }
        ArrayList<String> ssidAllowlist = new ArrayList<>(allowedSsidSet);
        int allowlistSize = ssidAllowlist.size();
        int maxAllowlistSize = mConnectivityHelper.getMaxNumAllowlistSsid();
        if (maxAllowlistSize <= 0) {
            Log.wtf(TAG, "Invalid max SSID allowlist size:  " + maxAllowlistSize);
            return;
        }
        if (allowlistSize > maxAllowlistSize) {
            ssidAllowlist = new ArrayList<>(ssidAllowlist.subList(0, maxAllowlistSize));
            localLog("Trim down SSID allowlist size from " + allowlistSize + " to "
                    + ssidAllowlist.size());
        }

        // plumb down to HAL
        String message = "set firmware roaming configurations. "
                + "bssidBlocklist=";
        if (bssidBlocklist.size() == 0) {
            message += "<EMPTY>";
        } else {
            message += String.join(", ", bssidBlocklist);
        }
        if (!mConnectivityHelper.setFirmwareRoamingConfiguration(bssidBlocklist, ssidAllowlist)) {
            Log.e(TAG, "Failed to " + message);
            mBssidBlocklistMonitorLogger.log("Failed to " + message);
        } else {
            mBssidBlocklistMonitorLogger.log("Successfully " + message);
        }
    }

    @VisibleForTesting
    public int getBssidBlocklistMonitorLoggerSize() {
        return mBssidBlocklistMonitorLogger.size();
    }

    private class BssidBlocklistMonitorLogger {
        private LinkedList<String> mLogBuffer = new LinkedList<>();
        private int mBufferSize;

        BssidBlocklistMonitorLogger(int bufferSize) {
            mBufferSize = bufferSize;
        }

        public void logBssidUnblocked(BssidStatus bssidStatus, String unblockReason) {
            // only log history for Bssids that had been blocked.
            if (bssidStatus == null || !bssidStatus.isInBlocklist) {
                return;
            }
            StringBuilder sb = createStringBuilderWithLogTime();
            sb.append(", Bssid unblocked, Reason=" + unblockReason);
            sb.append(", Unblocked BssidStatus={" + bssidStatus.toString() + "}");
            logInternal(sb.toString());
        }

        // cache a single line of log message in the rotating buffer
        public void log(String message) {
            if (message == null) {
                return;
            }
            StringBuilder sb = createStringBuilderWithLogTime();
            sb.append(" " + message);
            logInternal(sb.toString());
        }

        private StringBuilder createStringBuilderWithLogTime() {
            StringBuilder sb = new StringBuilder();
            Calendar c = Calendar.getInstance();
            c.setTimeInMillis(mClock.getWallClockMillis());
            sb.append("logTime=").append(StringUtil.calendarToString(c));
            return sb;
        }

        private void logInternal(String message) {
            mLogBuffer.add(message);
            if (mLogBuffer.size() > mBufferSize) {
                mLogBuffer.removeFirst();
            }
        }

        @VisibleForTesting
        public int size() {
            return mLogBuffer.size();
        }

        public void dump(PrintWriter pw) {
            pw.println("WifiBlocklistMonitor - Bssid blocklist logs begin ----");
            for (String line : mLogBuffer) {
                pw.println(line);
            }
            pw.println("List of SSIDs to never block:");
            for (WifiSsid ssid : mSsidsAllowlistForNetworkSelection) {
                pw.println(ssid.toString());
            }
            pw.println("WifiBlocklistMonitor - Bssid blocklist logs end ----");
        }
    }

    /**
     * Helper class that counts the number of failures per BSSID.
     */
    private class BssidStatus {
        public final String bssid;
        public final String ssid;
        public final int[] failureCount = new int[NUMBER_REASON_CODES];
        public int blockReason = INVALID_REASON; // reason of blocking this BSSID
        // The latest RSSI that's seen before this BSSID is added to blocklist.
        public int lastRssi = 0;

        // The following are used to flag how long this BSSID stays in the blocklist.
        public boolean isInBlocklist;
        public long blocklistEndTimeMs;
        public long blocklistStartTimeMs;

        BssidStatus(String bssid, String ssid) {
            this.bssid = bssid;
            this.ssid = ssid;
        }

        /**
         * increments the failure count for the reasonCode by 1.
         * @return the incremented failure count
         */
        public int incrementFailureCount(int reasonCode) {
            return ++failureCount[reasonCode];
        }

        /**
         * Set this BSSID as blocked for the specified duration.
         * @param durationMs
         * @param blockReason
         * @param rssi
         */
        public void setAsBlocked(long durationMs, @FailureReason int blockReason, int rssi) {
            isInBlocklist = true;
            blocklistStartTimeMs = mClock.getWallClockMillis();
            blocklistEndTimeMs = blocklistStartTimeMs + durationMs;
            this.blockReason = blockReason;
            lastRssi = rssi;
            mWifiMetrics.incrementBssidBlocklistCount(blockReason);
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("BSSID=" + bssid);
            sb.append(", SSID=" + ssid);
            sb.append(", isInBlocklist=" + isInBlocklist);
            if (isInBlocklist) {
                sb.append(", blockReason=" + getFailureReasonString(blockReason));
                sb.append(", lastRssi=" + lastRssi);
                Calendar c = Calendar.getInstance();
                c.setTimeInMillis(blocklistStartTimeMs);
                sb.append(", blocklistStartTime=").append(StringUtil.calendarToString(c));
                c.setTimeInMillis(blocklistEndTimeMs);
                sb.append(", blocklistEndTime=").append(StringUtil.calendarToString(c));
            }
            return sb.toString();
        }
    }

    /**
     * Enable/disable verbose logging in WifiBlocklistMonitor.
     */
    public void enableVerboseLogging(boolean verbose) {
        mVerboseLoggingEnabled = verbose;
    }

    /**
     * Modify the internal copy of DisableReasonInfo with custom configurations defined in
     * an overlay.
     */
    private void loadCustomConfigsForDisableReasonInfos() {
        mDisableReasonInfo.put(NetworkSelectionStatus.DISABLED_ASSOCIATION_REJECTION,
                new DisableReasonInfo(
                        // Note that there is a space at the end of this string. Cannot fix
                        // since this string is persisted.
                        "NETWORK_SELECTION_DISABLED_ASSOCIATION_REJECTION ",
                        mContext.getResources().getInteger(R.integer
                                .config_wifiDisableReasonAssociationRejectionThreshold),
                        mContext.getResources().getInteger(R.integer
                                .config_wifiDisableReasonAssociationRejectionDurationMs)));

        mDisableReasonInfo.put(NetworkSelectionStatus.DISABLED_AUTHENTICATION_FAILURE,
                new DisableReasonInfo(
                        "NETWORK_SELECTION_DISABLED_AUTHENTICATION_FAILURE",
                        mContext.getResources().getInteger(R.integer
                                .config_wifiDisableReasonAuthenticationFailureThreshold),
                        mContext.getResources().getInteger(R.integer
                                .config_wifiDisableReasonAuthenticationFailureDurationMs)));

        mDisableReasonInfo.put(NetworkSelectionStatus.DISABLED_DHCP_FAILURE,
                new DisableReasonInfo(
                        "NETWORK_SELECTION_DISABLED_DHCP_FAILURE",
                        mContext.getResources().getInteger(R.integer
                                .config_wifiDisableReasonDhcpFailureThreshold),
                        mContext.getResources().getInteger(R.integer
                                .config_wifiDisableReasonDhcpFailureDurationMs)));

        mDisableReasonInfo.put(NetworkSelectionStatus.DISABLED_NETWORK_NOT_FOUND,
                new DisableReasonInfo(
                        "NETWORK_SELECTION_DISABLED_NETWORK_NOT_FOUND",
                        mContext.getResources().getInteger(R.integer
                                .config_wifiDisableReasonNetworkNotFoundThreshold),
                        mContext.getResources().getInteger(R.integer
                                .config_wifiDisableReasonNetworkNotFoundDurationMs)));

        mDisableReasonInfo.put(NetworkSelectionStatus.DISABLED_NO_INTERNET_TEMPORARY,
                new DisableReasonInfo(
                "NETWORK_SELECTION_DISABLED_NO_INTERNET_TEMPORARY",
                mContext.getResources().getInteger(R.integer
                    .config_wifiDisableReasonNoInternetTemporaryThreshold),
                mContext.getResources().getInteger(R.integer
                    .config_wifiDisableReasonNoInternetTemporaryDurationMs)));

        mDisableReasonInfo.put(NetworkSelectionStatus.DISABLED_AUTHENTICATION_NO_CREDENTIALS,
                new DisableReasonInfo(
                        "NETWORK_SELECTION_DISABLED_AUTHENTICATION_NO_CREDENTIALS",
                        mContext.getResources().getInteger(R.integer
                                .config_wifiDisableReasonAuthenticationNoCredentialsThreshold),
                        mContext.getResources().getInteger(R.integer
                                .config_wifiDisableReasonAuthenticationNoCredentialsDurationMs)));

        mDisableReasonInfo.put(NetworkSelectionStatus.DISABLED_NO_INTERNET_PERMANENT,
                new DisableReasonInfo(
                        "NETWORK_SELECTION_DISABLED_NO_INTERNET_PERMANENT",
                        mContext.getResources().getInteger(R.integer
                                .config_wifiDisableReasonNoInternetPermanentThreshold),
                        mContext.getResources().getInteger(R.integer
                                .config_wifiDisableReasonNoInternetPermanentDurationMs)));

        mDisableReasonInfo.put(NetworkSelectionStatus.DISABLED_BY_WRONG_PASSWORD,
                new DisableReasonInfo(
                        "NETWORK_SELECTION_DISABLED_BY_WRONG_PASSWORD",
                        mContext.getResources().getInteger(R.integer
                                .config_wifiDisableReasonByWrongPasswordThreshold),
                        mContext.getResources().getInteger(R.integer
                                .config_wifiDisableReasonByWrongPasswordDurationMs)));

        mDisableReasonInfo.put(NetworkSelectionStatus.DISABLED_AUTHENTICATION_NO_SUBSCRIPTION,
                new DisableReasonInfo(
                        "NETWORK_SELECTION_DISABLED_AUTHENTICATION_NO_SUBSCRIPTION",
                        mContext.getResources().getInteger(R.integer
                                .config_wifiDisableReasonAuthenticationNoSubscriptionThreshold),
                        mContext.getResources().getInteger(R.integer
                                .config_wifiDisableReasonAuthenticationNoSubscriptionDurationMs)));

        mDisableReasonInfo.put(NetworkSelectionStatus.DISABLED_CONSECUTIVE_FAILURES,
                new DisableReasonInfo(
                        "NETWORK_SELECTION_DISABLED_CONSECUTIVE_FAILURES",
                        mContext.getResources().getInteger(R.integer
                                .config_wifiDisableReasonConsecutiveFailuresThreshold),
                        mContext.getResources().getInteger(R.integer
                                .config_wifiDisableReasonConsecutiveFailuresDurationMs)));
    }

    /**
     * Update DisableReasonInfo with carrier configurations defined in an overlay.
     *
     * TODO(236173881): mDisableReasonInfo storing the carrier specific EAP failure threshold and
     * duration is always keyed by NetworkSelectionStatus.DISABLED_AUTHENTICATION_PRIVATE_EAP_ERROR.
     * This is error prone now that different carrier networks could have different thresholds and
     * durations. But with the current code only the last updated one will remain in
     * mDisableReasonInfo. Need to clean this up to be more robust.
     */
    public void loadCarrierConfigsForDisableReasonInfos(
            @NonNull CarrierSpecificEapFailureConfig config) {
        if (config == null) {
            Log.e(TAG, "Unexpected null CarrierSpecificEapFailureConfig");
            return;
        }
        DisableReasonInfo disableReasonInfo = new DisableReasonInfo(
                "NETWORK_SELECTION_DISABLED_AUTHENTICATION_PRIVATE_EAP_ERROR",
                config.threshold, config.durationMs);
        mDisableReasonInfo.put(
                NetworkSelectionStatus.DISABLED_AUTHENTICATION_PRIVATE_EAP_ERROR,
                disableReasonInfo);
    }

    /**
     * Class to be used to represent blocklist behavior for a certain EAP error code.
     */
    public static class CarrierSpecificEapFailureConfig {
        // number of failures to disable
        public final int threshold;
        // disable duration in ms. -1 means permanent disable.
        public final int durationMs;
        public final boolean displayNotification;
        public CarrierSpecificEapFailureConfig(int threshold, int durationMs,
                boolean displayNotification) {
            this.threshold = threshold;
            this.durationMs = durationMs;
            this.displayNotification = displayNotification;
        }

        @Override
        public int hashCode() {
            return Objects.hash(threshold, durationMs, displayNotification);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof CarrierSpecificEapFailureConfig)) {
                return false;
            }
            CarrierSpecificEapFailureConfig lhs = (CarrierSpecificEapFailureConfig) obj;
            return threshold == lhs.threshold && durationMs == lhs.durationMs
                    && displayNotification == lhs.displayNotification;
        }

        @Override
        public String toString() {
            return new StringBuilder()
                    .append("threshold=").append(threshold)
                    .append(" durationMs=").append(durationMs)
                    .append(" displayNotification=").append(displayNotification)
                    .toString();
        }
    }

    /**
     * Returns true if the disable duration for this WifiConfiguration has passed. Returns false
     * if the WifiConfiguration is either not disabled or is permanently disabled.
     */
    public boolean shouldEnableNetwork(WifiConfiguration config) {
        NetworkSelectionStatus networkStatus = config.getNetworkSelectionStatus();
        if (networkStatus.isNetworkTemporaryDisabled()) {
            return mClock.getElapsedSinceBootMillis() >= networkStatus.getDisableEndTime();
        }
        return false;
    }

    /**
     * Update a network's status (both internal and public) according to the update reason and
     * its current state. This method is expects to directly modify the internal WifiConfiguration
     * that is stored by WifiConfigManager.
     *
     * @param config the internal WifiConfiguration to be updated.
     * @param reason reason code for update.
     * @return true if the input configuration has been updated, false otherwise.
     */
    public boolean updateNetworkSelectionStatus(WifiConfiguration config, int reason) {
        if (reason < 0 || reason >= NetworkSelectionStatus.NETWORK_SELECTION_DISABLED_MAX) {
            Log.e(TAG, "Invalid Network disable reason " + reason);
            return false;
        }
        NetworkSelectionStatus networkStatus = config.getNetworkSelectionStatus();
        if (reason != NetworkSelectionStatus.DISABLED_NONE) {
            // Do not disable if in the exception list
            if (reason != NetworkSelectionStatus.DISABLED_BY_WIFI_MANAGER
                    && isConfigExemptFromBlocklist(config)) {
                return false;
            }

            // Do not update SSID blocklist with information if this is the only
            // SSID be observed. By ignoring it we will cause additional failures
            // which will trigger Watchdog.
            if (reason == NetworkSelectionStatus.DISABLED_ASSOCIATION_REJECTION
                    || reason == NetworkSelectionStatus.DISABLED_AUTHENTICATION_FAILURE
                    || reason == NetworkSelectionStatus.DISABLED_DHCP_FAILURE) {
                if (mWifiLastResortWatchdog.shouldIgnoreSsidUpdate()) {
                    if (mVerboseLoggingEnabled) {
                        Log.v(TAG, "Ignore update network selection status "
                                + "since Watchdog trigger is activated");
                    }
                    return false;
                }
            }

            networkStatus.incrementDisableReasonCounter(reason);
            // For network disable reasons, we should only update the status if we cross the
            // threshold.
            int disableReasonCounter = networkStatus.getDisableReasonCounter(reason);
            int disableReasonThreshold = getNetworkSelectionDisableThreshold(reason);
            if (disableReasonCounter < disableReasonThreshold) {
                if (mVerboseLoggingEnabled) {
                    Log.v(TAG, "Disable counter for network " + config.getPrintableSsid()
                            + " for reason "
                            + NetworkSelectionStatus.getNetworkSelectionDisableReasonString(reason)
                            + " is " + networkStatus.getDisableReasonCounter(reason)
                            + " and threshold is " + disableReasonThreshold);
                }
                return true;
            }
        }
        setNetworkSelectionStatus(config, reason);
        return true;
    }

    /**
     * Sets a network's status (both internal and public) according to the update reason and
     * its current state.
     *
     * This updates the network's {@link WifiConfiguration#mNetworkSelectionStatus} field and the
     * public {@link WifiConfiguration#status} field if the network is either enabled or
     * permanently disabled.
     *
     * @param config network to be updated.
     * @param reason reason code for update.
     */
    private void setNetworkSelectionStatus(WifiConfiguration config, int reason) {
        NetworkSelectionStatus networkStatus = config.getNetworkSelectionStatus();
        if (reason == NetworkSelectionStatus.DISABLED_NONE) {
            setNetworkSelectionEnabled(config);
        } else if (getNetworkSelectionDisableTimeoutMillis(reason)
                != DisableReasonInfo.PERMANENT_DISABLE_TIMEOUT) {
            setNetworkSelectionTemporarilyDisabled(config, reason);
        } else {
            setNetworkSelectionPermanentlyDisabled(config, reason);
        }
        localLog("setNetworkSelectionStatus: configKey=" + config.getProfileKey()
                + " networkStatus=" + networkStatus.getNetworkStatusString() + " disableReason="
                + networkStatus.getNetworkSelectionDisableReasonString());
    }

    /**
     * Helper method to mark a network enabled for network selection.
     */
    private void setNetworkSelectionEnabled(WifiConfiguration config) {
        NetworkSelectionStatus status = config.getNetworkSelectionStatus();
        if (status.getNetworkSelectionStatus()
                != NetworkSelectionStatus.NETWORK_SELECTION_ENABLED) {
            localLog("setNetworkSelectionEnabled: configKey=" + config.getProfileKey()
                    + " old networkStatus=" + status.getNetworkStatusString()
                    + " disableReason=" + status.getNetworkSelectionDisableReasonString());
        }
        status.setNetworkSelectionStatus(
                NetworkSelectionStatus.NETWORK_SELECTION_ENABLED);
        status.setDisableTime(
                NetworkSelectionStatus.INVALID_NETWORK_SELECTION_DISABLE_TIMESTAMP);
        status.setDisableEndTime(
                NetworkSelectionStatus.INVALID_NETWORK_SELECTION_DISABLE_TIMESTAMP);
        status.setNetworkSelectionDisableReason(NetworkSelectionStatus.DISABLED_NONE);

        // Clear out all the disable reason counters.
        status.clearDisableReasonCounter();
        if (config.status == WifiConfiguration.Status.DISABLED) {
            config.status = WifiConfiguration.Status.ENABLED;
        }
    }

    /**
     * Helper method to mark a network temporarily disabled for network selection.
     */
    private void setNetworkSelectionTemporarilyDisabled(
            WifiConfiguration config, int disableReason) {
        NetworkSelectionStatus status = config.getNetworkSelectionStatus();
        status.setNetworkSelectionStatus(
                NetworkSelectionStatus.NETWORK_SELECTION_TEMPORARY_DISABLED);
        // Only need a valid time filled in for temporarily disabled networks.
        status.setDisableTime(mClock.getElapsedSinceBootMillis());
        status.setDisableEndTime(calculateDisableEndTime(config, disableReason));
        status.setNetworkSelectionDisableReason(disableReason);
        handleWifiConfigurationDisabled(config.SSID);
        mWifiMetrics.incrementWificonfigurationBlocklistCount(disableReason);
    }

    private long calculateDisableEndTime(WifiConfiguration config, int disableReason) {
        long disableDurationMs = (long) getNetworkSelectionDisableTimeoutMillis(disableReason);
        int exponentialBackoffCount = mWifiScoreCard.lookupNetwork(config.SSID)
                .getRecentStats().getCount(WifiScoreCard.CNT_CONSECUTIVE_CONNECTION_FAILURE)
                - NUM_CONSECUTIVE_FAILURES_PER_NETWORK_EXP_BACKOFF;
        for (int i = 0; i < exponentialBackoffCount; i++) {
            disableDurationMs *= 2;
            if (disableDurationMs > mWifiGlobals.getWifiConfigMaxDisableDurationMs()) {
                disableDurationMs = mWifiGlobals.getWifiConfigMaxDisableDurationMs();
                break;
            }
        }
        return mClock.getElapsedSinceBootMillis() + disableDurationMs;
    }

    /**
     * Helper method to mark a network permanently disabled for network selection.
     */
    private void setNetworkSelectionPermanentlyDisabled(
            WifiConfiguration config, int disableReason) {
        NetworkSelectionStatus status = config.getNetworkSelectionStatus();
        status.setNetworkSelectionStatus(
                NetworkSelectionStatus.NETWORK_SELECTION_PERMANENTLY_DISABLED);
        status.setDisableTime(
                NetworkSelectionStatus.INVALID_NETWORK_SELECTION_DISABLE_TIMESTAMP);
        status.setNetworkSelectionDisableReason(disableReason);
        handleWifiConfigurationDisabled(config.SSID);
        config.status = WifiConfiguration.Status.DISABLED;
        mWifiMetrics.incrementWificonfigurationBlocklistCount(disableReason);
    }

    /**
     * Network Selection disable reason thresholds. These numbers are used to debounce network
     * failures before we disable them.
     *
     * @param reason int reason code
     * @return the disable threshold, or -1 if not found.
     */
    @VisibleForTesting
    public int getNetworkSelectionDisableThreshold(@NetworkSelectionDisableReason int reason) {
        DisableReasonInfo info = mDisableReasonInfo.get(reason);
        if (info == null) {
            Log.e(TAG, "Unrecognized network disable reason code for disable threshold: " + reason);
            return -1;
        } else {
            return info.mDisableThreshold;
        }
    }

    /**
     * Network Selection disable timeout for each kind of error. After the timeout in milliseconds,
     * enable the network again.
     */
    @VisibleForTesting
    public int getNetworkSelectionDisableTimeoutMillis(@NetworkSelectionDisableReason int reason) {
        DisableReasonInfo info = mDisableReasonInfo.get(reason);
        if (info == null) {
            Log.e(TAG, "Unrecognized network disable reason code for disable timeout: " + reason);
            return -1;
        } else {
            return info.mDisableTimeoutMillis;
        }
    }

    /**
     * Sets the allowlist ssids for the given ssid
     */
    public void setAllowlistSsids(@NonNull String ssid, @NonNull List<String> ssidAllowlist) {
        mSsidAllowlistMap.put(ssid, ssidAllowlist);
    }
}
