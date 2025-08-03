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

import android.annotation.NonNull;
import android.content.Context;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiNetworkSelectionConfig;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;
import android.util.SparseArray;

import androidx.annotation.RequiresApi;

import com.android.internal.annotations.VisibleForTesting;
import com.android.modules.utils.build.SdkLevel;
import com.android.server.wifi.util.KeyValueListParser;
import com.android.wifi.resources.R;

/**
 * Holds parameters used for scoring networks.
 *
 * Doing this in one place means that there's a better chance of consistency between
 * connected score and network selection.
 *
 */
public class ScoringParams {
    private final Context mContext;

    private static final String TAG = "WifiScoringParams";
    private static final int EXIT = 0;
    private static final int ENTRY = 1;
    private static final int SUFFICIENT = 2;
    private static final int GOOD = 3;

    private static final int ACTIVE_TRAFFIC = 1;
    private static final int HIGH_TRAFFIC = 2;

    @VisibleForTesting
    public static final int FREQUENCY_WEIGHT_LOW = -40;
    @VisibleForTesting
    public static final int FREQUENCY_WEIGHT_DEFAULT = 0;
    @VisibleForTesting
    public static final int FREQUENCY_WEIGHT_HIGH = 40;

    SparseArray<Integer> mFrequencyWeights = new SparseArray<>();

    /**
     * Parameter values are stored in a separate container so that a new collection of values can
     * be checked for consistency before activating them.
     */
    private class Values {
        /** RSSI thresholds for 2.4 GHz band (dBm) */
        public static final String KEY_RSSI2 = "rssi2";
        public final int[] rssi2 = {-83, -80, -73, -60};

        /** RSSI thresholds for 5 GHz band (dBm) */
        public static final String KEY_RSSI5 = "rssi5";
        public final int[] rssi5 = {-80, -77, -70, -57};

        /** RSSI thresholds for 6 GHz band (dBm) */
        public static final String KEY_RSSI6 = "rssi6";
        public final int[] rssi6 = {-80, -77, -70, -57};

       /** Guidelines based on packet rates (packets/sec) */
        public static final String KEY_PPS = "pps";
        public final int[] pps = {0, 1, 100};

        /** Number of seconds for RSSI forecast */
        public static final String KEY_HORIZON = "horizon";
        public static final int MIN_HORIZON = -9;
        public static final int MAX_HORIZON = 60;
        public int horizon = 15;

        /** Number 0-10 influencing requests for network unreachability detection */
        public static final String KEY_NUD = "nud";
        public static final int MIN_NUD = 0;
        public static final int MAX_NUD = 10;
        public int nud = 8;

        /** Experiment identifier */
        public static final String KEY_EXPID = "expid";
        public static final int MIN_EXPID = 0;
        public static final int MAX_EXPID = Integer.MAX_VALUE;
        public int expid = 0;

        /** CandidateScorer parameters */
        public int throughputBonusNumerator = 120;
        public int throughputBonusDenominator = 433;
        public int throughputBonusNumeratorAfter800Mbps = 1;
        public int throughputBonusDenominatorAfter800Mbps = 16;
        public boolean enable6GhzBeaconRssiBoost = true;
        public int throughputBonusLimit = 320;
        public int savedNetworkBonus = 500;
        public int unmeteredNetworkBonus = 1000;
        public int currentNetworkBonusMin = 16;
        public int currentNetworkBonusPercent = 20;
        public int secureNetworkBonus = 40;
        public int band6GhzBonus = 0;
        public int scoringBucketStepSize = 500;
        public int lastUnmeteredSelectionMinutes = 480;
        public int lastMeteredSelectionMinutes = 120;
        public int estimateRssiErrorMargin = 5;
        public static final int MIN_MINUTES = 1;
        public static final int MAX_MINUTES = Integer.MAX_VALUE / (60 * 1000);

        Values() {
        }

        Values(Values source) {
            for (int i = 0; i < rssi2.length; i++) {
                rssi2[i] = source.rssi2[i];
            }
            for (int i = 0; i < rssi5.length; i++) {
                rssi5[i] = source.rssi5[i];
            }
            for (int i = 0; i < rssi6.length; i++) {
                rssi6[i] = source.rssi6[i];
            }
            for (int i = 0; i < pps.length; i++) {
                pps[i] = source.pps[i];
            }
            horizon = source.horizon;
            nud = source.nud;
            expid = source.expid;
        }

        public void validate() throws IllegalArgumentException {
            validateRssiArray(rssi2);
            validateRssiArray(rssi5);
            validateRssiArray(rssi6);
            validateOrderedNonNegativeArray(pps);
            validateRange(horizon, MIN_HORIZON, MAX_HORIZON);
            validateRange(nud, MIN_NUD, MAX_NUD);
            validateRange(expid, MIN_EXPID, MAX_EXPID);
            validateRange(lastUnmeteredSelectionMinutes, MIN_MINUTES, MAX_MINUTES);
            validateRange(lastMeteredSelectionMinutes, MIN_MINUTES, MAX_MINUTES);
        }

        private void validateRssiArray(int[] rssi) throws IllegalArgumentException {
            int low = WifiInfo.MIN_RSSI;
            int high = Math.min(WifiInfo.MAX_RSSI, -1); // Stricter than Wifiinfo
            for (int i = 0; i < rssi.length; i++) {
                validateRange(rssi[i], low, high);
                low = rssi[i];
            }
        }

        private void validateRange(int k, int low, int high) throws IllegalArgumentException {
            if (k < low || k > high) {
                throw new IllegalArgumentException();
            }
        }

        private void validateOrderedNonNegativeArray(int[] a) throws IllegalArgumentException {
            int low = 0;
            for (int i = 0; i < a.length; i++) {
                if (a[i] < low) {
                    throw new IllegalArgumentException();
                }
                low = a[i];
            }
        }

        public void parseString(String kvList) throws IllegalArgumentException {
            KeyValueListParser parser = new KeyValueListParser(',');
            parser.setString(kvList);
            if (parser.size() != ("" + kvList).split(",").length) {
                throw new IllegalArgumentException("dup keys");
            }
            updateIntArray(rssi2, parser, KEY_RSSI2);
            updateIntArray(rssi5, parser, KEY_RSSI5);
            updateIntArray(rssi6, parser, KEY_RSSI6);
            updateIntArray(pps, parser, KEY_PPS);
            horizon = updateInt(parser, KEY_HORIZON, horizon);
            nud = updateInt(parser, KEY_NUD, nud);
            expid = updateInt(parser, KEY_EXPID, expid);
        }

        private int updateInt(KeyValueListParser parser, String key, int defaultValue)
                throws IllegalArgumentException {
            String value = parser.getString(key, null);
            if (value == null) return defaultValue;
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException();
            }
        }

        private void updateIntArray(final int[] dest, KeyValueListParser parser, String key)
                throws IllegalArgumentException {
            if (parser.getString(key, null) == null) return;
            int[] ints = parser.getIntArray(key, null);
            if (ints == null) throw new IllegalArgumentException();
            if (ints.length != dest.length) throw new IllegalArgumentException();
            for (int i = 0; i < dest.length; i++) {
                dest[i] = ints[i];
            }
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            appendKey(sb, KEY_RSSI2);
            appendInts(sb, rssi2);
            appendKey(sb, KEY_RSSI5);
            appendInts(sb, rssi5);
            appendKey(sb, KEY_RSSI6);
            appendInts(sb, rssi6);
            appendKey(sb, KEY_PPS);
            appendInts(sb, pps);
            appendKey(sb, KEY_HORIZON);
            sb.append(horizon);
            appendKey(sb, KEY_NUD);
            sb.append(nud);
            appendKey(sb, KEY_EXPID);
            sb.append(expid);
            return sb.toString();
        }

        private void appendKey(StringBuilder sb, String key) {
            if (sb.length() != 0) sb.append(",");
            sb.append(key).append("=");
        }

        private void appendInts(StringBuilder sb, final int[] a) {
            final int n = a.length;
            for (int i = 0; i < n; i++) {
                if (i > 0) sb.append(":");
                sb.append(a[i]);
            }
        }
    }

    @NonNull private Values mVal = null;

    @VisibleForTesting
    public ScoringParams() {
        mContext = null;
        mVal = new Values();
    }

    public ScoringParams(Context context) {
        mContext = context;
        loadResources(mContext);
    }

    private void loadResources(Context context) {
        if (mVal != null) return;
        mVal = new Values();
        mVal.rssi2[EXIT] = context.getResources().getInteger(
                R.integer.config_wifi_framework_wifi_score_bad_rssi_threshold_24GHz);
        mVal.rssi2[ENTRY] = context.getResources().getInteger(
                R.integer.config_wifi_framework_wifi_score_entry_rssi_threshold_24GHz);
        mVal.rssi2[SUFFICIENT] = context.getResources().getInteger(
                R.integer.config_wifi_framework_wifi_score_low_rssi_threshold_24GHz);
        mVal.rssi2[GOOD] = context.getResources().getInteger(
                R.integer.config_wifi_framework_wifi_score_good_rssi_threshold_24GHz);
        mVal.rssi5[EXIT] = context.getResources().getInteger(
                R.integer.config_wifi_framework_wifi_score_bad_rssi_threshold_5GHz);
        mVal.rssi5[ENTRY] = context.getResources().getInteger(
                R.integer.config_wifi_framework_wifi_score_entry_rssi_threshold_5GHz);
        mVal.rssi5[SUFFICIENT] = context.getResources().getInteger(
                R.integer.config_wifi_framework_wifi_score_low_rssi_threshold_5GHz);
        mVal.rssi5[GOOD] = context.getResources().getInteger(
                R.integer.config_wifi_framework_wifi_score_good_rssi_threshold_5GHz);
        mVal.rssi6[EXIT] = context.getResources().getInteger(
                R.integer.config_wifiFrameworkScoreBadRssiThreshold6ghz);
        mVal.rssi6[ENTRY] = context.getResources().getInteger(
                R.integer.config_wifiFrameworkScoreEntryRssiThreshold6ghz);
        mVal.rssi6[SUFFICIENT] = context.getResources().getInteger(
                R.integer.config_wifiFrameworkScoreLowRssiThreshold6ghz);
        mVal.rssi6[GOOD] = context.getResources().getInteger(
                R.integer.config_wifiFrameworkScoreGoodRssiThreshold6ghz);
        mVal.throughputBonusNumerator = context.getResources().getInteger(
                R.integer.config_wifiFrameworkThroughputBonusNumerator);
        mVal.throughputBonusDenominator = context.getResources().getInteger(
                R.integer.config_wifiFrameworkThroughputBonusDenominator);
        mVal.throughputBonusNumeratorAfter800Mbps = context.getResources().getInteger(
                R.integer.config_wifiFrameworkThroughputBonusNumeratorAfter800Mbps);
        mVal.throughputBonusDenominatorAfter800Mbps = context.getResources().getInteger(
                R.integer.config_wifiFrameworkThroughputBonusDenominatorAfter800Mbps);
        mVal.enable6GhzBeaconRssiBoost = context.getResources().getBoolean(
                R.bool.config_wifiEnable6GhzBeaconRssiBoost);
        mVal.throughputBonusLimit = context.getResources().getInteger(
                R.integer.config_wifiFrameworkThroughputBonusLimit);
        mVal.savedNetworkBonus = context.getResources().getInteger(
                R.integer.config_wifiFrameworkSavedNetworkBonus);
        mVal.unmeteredNetworkBonus = context.getResources().getInteger(
                R.integer.config_wifiFrameworkUnmeteredNetworkBonus);
        mVal.currentNetworkBonusMin = context.getResources().getInteger(
                R.integer.config_wifiFrameworkCurrentNetworkBonusMin);
        mVal.currentNetworkBonusPercent = context.getResources().getInteger(
            R.integer.config_wifiFrameworkCurrentNetworkBonusPercent);
        mVal.secureNetworkBonus = context.getResources().getInteger(
                R.integer.config_wifiFrameworkSecureNetworkBonus);
        mVal.band6GhzBonus = context.getResources().getInteger(R.integer.config_wifiBand6GhzBonus);
        mVal.scoringBucketStepSize = context.getResources().getInteger(
                R.integer.config_wifiScoringBucketStepSize);
        mVal.lastUnmeteredSelectionMinutes = context.getResources().getInteger(
                R.integer.config_wifiFrameworkLastSelectionMinutes);
        mVal.lastMeteredSelectionMinutes = context.getResources().getInteger(
                R.integer.config_wifiFrameworkLastMeteredSelectionMinutes);
        mVal.estimateRssiErrorMargin = context.getResources().getInteger(
                R.integer.config_wifiEstimateRssiErrorMarginDb);
        mVal.pps[ACTIVE_TRAFFIC] = context.getResources().getInteger(
                R.integer.config_wifiFrameworkMinPacketPerSecondActiveTraffic);
        mVal.pps[HIGH_TRAFFIC] = context.getResources().getInteger(
                R.integer.config_wifiFrameworkMinPacketPerSecondHighTraffic);
        try {
            mVal.validate();
        } catch (IllegalArgumentException e) {
            Log.wtf(TAG, "Inconsistent config_wifi_framework_ resources: " + this, e);
        }
    }

    private static final String COMMA_KEY_VAL_STAR = "^(,[A-Za-z_][A-Za-z0-9_]*=[0-9.:+-]+)*$";

    /**
     * Updates the parameters from the given parameter string.
     * If any errors are detected, no change is made.
     * @param kvList is a comma-separated key=value list.
     * @return true for success
     */
    @VisibleForTesting
    public boolean update(String kvList) {
        if (TextUtils.isEmpty(kvList)) {
            return true;
        }
        if (!("," + kvList).matches(COMMA_KEY_VAL_STAR)) {
            return false;
        }
        Values v = new Values(mVal);
        try {
            v.parseString(kvList);
            v.validate();
            mVal = v;
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Sanitize a string to make it safe for printing.
     * @param params is the untrusted string
     * @return string with questionable characters replaced with question marks
     */
    public String sanitize(String params) {
        if (params == null) return "";
        String printable = params.replaceAll("[^A-Za-z_0-9=,:.+-]", "?");
        if (printable.length() > 100) {
            printable = printable.substring(0, 98) + "...";
        }
        return printable;
    }

    /**
     * Returns the RSSI value at which the connection is deemed to be unusable,
     * in the absence of other indications.
     */
    public int getExitRssi(int frequencyMegaHertz) {
        return getRssiArray(frequencyMegaHertz)[EXIT];
    }

    /**
     * Returns the minimum scan RSSI for making a connection attempt.
     */
    public int getEntryRssi(int frequencyMegaHertz) {
        return getRssiArray(frequencyMegaHertz)[ENTRY];
    }

    /**
     * Returns a connected RSSI value that indicates the connection is
     * good enough that we needn't scan for alternatives.
     */
    public int getSufficientRssi(int frequencyMegaHertz) {
        return getRssiArray(frequencyMegaHertz)[SUFFICIENT];
    }

    /**
     * Returns a connected RSSI value that indicates a good connection.
     */
    public int getGoodRssi(int frequencyMegaHertz) {
        return getRssiArray(frequencyMegaHertz)[GOOD];
    }

    /**
     * Sets the RSSI thresholds for 2.4 GHz.
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    public void setRssi2Thresholds(int[] rssi2) {
        if (WifiNetworkSelectionConfig.isRssiThresholdResetArray(rssi2)) {
            mVal.rssi2[EXIT] = mContext.getResources().getInteger(
                    R.integer.config_wifi_framework_wifi_score_bad_rssi_threshold_24GHz);
            mVal.rssi2[ENTRY] = mContext.getResources().getInteger(
                    R.integer.config_wifi_framework_wifi_score_entry_rssi_threshold_24GHz);
            mVal.rssi2[SUFFICIENT] = mContext.getResources().getInteger(
                    R.integer.config_wifi_framework_wifi_score_low_rssi_threshold_24GHz);
            mVal.rssi2[GOOD] = mContext.getResources().getInteger(
                    R.integer.config_wifi_framework_wifi_score_good_rssi_threshold_24GHz);
        } else {
            mVal.rssi2[EXIT] = rssi2[EXIT];
            mVal.rssi2[ENTRY] = rssi2[ENTRY];
            mVal.rssi2[SUFFICIENT] = rssi2[SUFFICIENT];
            mVal.rssi2[GOOD] = rssi2[GOOD];
        }
    }

    /**
     * Sets the RSSI thresholds for 5 GHz.
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    public void setRssi5Thresholds(int[] rssi5) {
        if (WifiNetworkSelectionConfig.isRssiThresholdResetArray(rssi5)) {
            mVal.rssi5[EXIT] = mContext.getResources().getInteger(
                    R.integer.config_wifi_framework_wifi_score_bad_rssi_threshold_5GHz);
            mVal.rssi5[ENTRY] = mContext.getResources().getInteger(
                    R.integer.config_wifi_framework_wifi_score_entry_rssi_threshold_5GHz);
            mVal.rssi5[SUFFICIENT] = mContext.getResources().getInteger(
                    R.integer.config_wifi_framework_wifi_score_low_rssi_threshold_5GHz);
            mVal.rssi5[GOOD] = mContext.getResources().getInteger(
                    R.integer.config_wifi_framework_wifi_score_good_rssi_threshold_5GHz);
        } else {
            mVal.rssi5[EXIT] = rssi5[EXIT];
            mVal.rssi5[ENTRY] = rssi5[ENTRY];
            mVal.rssi5[SUFFICIENT] = rssi5[SUFFICIENT];
            mVal.rssi5[GOOD] = rssi5[GOOD];
        }
    }

    /**
     * Sets the RSSI thresholds for 6 GHz.
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    public void setRssi6Thresholds(int[] rssi6) {
        if (WifiNetworkSelectionConfig.isRssiThresholdResetArray(rssi6)) {
            mVal.rssi6[EXIT] = mContext.getResources().getInteger(
                    R.integer.config_wifiFrameworkScoreBadRssiThreshold6ghz);
            mVal.rssi6[ENTRY] = mContext.getResources().getInteger(
                    R.integer.config_wifiFrameworkScoreEntryRssiThreshold6ghz);
            mVal.rssi6[SUFFICIENT] = mContext.getResources().getInteger(
                    R.integer.config_wifiFrameworkScoreLowRssiThreshold6ghz);
            mVal.rssi6[GOOD] = mContext.getResources().getInteger(
                    R.integer.config_wifiFrameworkScoreGoodRssiThreshold6ghz);
        } else {
            mVal.rssi6[EXIT] = rssi6[EXIT];
            mVal.rssi6[ENTRY] = rssi6[ENTRY];
            mVal.rssi6[SUFFICIENT] = rssi6[SUFFICIENT];
            mVal.rssi6[GOOD] = rssi6[GOOD];
        }
    }

    /**
     * Sets the frequency weights list
     */
    public void setFrequencyWeights(SparseArray<Integer> weights) {
        mFrequencyWeights = weights;
    }

    /**
     * Returns the frequency weight score for the provided frequency
     */
    public int getFrequencyScore(int frequencyMegaHertz) {
        if (SdkLevel.isAtLeastT() && mFrequencyWeights.contains(frequencyMegaHertz)) {
            switch(mFrequencyWeights.get(frequencyMegaHertz)) {
                case WifiNetworkSelectionConfig.FREQUENCY_WEIGHT_LOW:
                    return FREQUENCY_WEIGHT_LOW;
                case WifiNetworkSelectionConfig.FREQUENCY_WEIGHT_HIGH:
                    return FREQUENCY_WEIGHT_HIGH;
                default:
                    // This should never happen because we've validated the frequency weights
                    // in WifiNetworkSectionConfig.
                    Log.wtf(TAG, "Invalid frequency weight type "
                            + mFrequencyWeights.get(frequencyMegaHertz));
            }
        }
        return FREQUENCY_WEIGHT_DEFAULT;
    }

    /**
     * Returns the number of seconds to use for rssi forecast.
     */
    public int getHorizonSeconds() {
        return mVal.horizon;
    }

    /**
     * Returns a packet rate that should be considered acceptable for staying on wifi,
     * no matter how bad the RSSI gets (packets per second).
     */
    public int getYippeeSkippyPacketsPerSecond() {
        return mVal.pps[HIGH_TRAFFIC];
    }

    /**
     * Returns a packet rate that should be considered acceptable to skip scan or network selection
     */
    public int getActiveTrafficPacketsPerSecond() {
        return mVal.pps[ACTIVE_TRAFFIC];
    }

    /**
     * Returns a number between 0 and 10 inclusive that indicates
     * how aggressive to be about asking for IP configuration checks
     * (also known as Network Unreachabilty Detection, or NUD).
     *
     * 0 - no nud checks requested by scorer (framework still checks after roam)
     * 1 - check when score becomes very low
     *     ...
     * 10 - check when score first breaches threshold, and again as it gets worse
     *
     */
    public int getNudKnob() {
        return mVal.nud;
    }

    /**
     * Returns the estimate rssi error margin to account minor differences in the environment
     * and the device's orientation.
     *
     */
    public int getEstimateRssiErrorMargin() {
        return mVal.estimateRssiErrorMargin;
    }

    /**
     */
    public int getThroughputBonusNumerator() {
        return mVal.throughputBonusNumerator;
    }

    /**
     */
    public int getThroughputBonusDenominator() {
        return mVal.throughputBonusDenominator;
    }

    /**
     * Getter for throughput numerator after 800Mbps.
     */
    public int getThroughputBonusNumeratorAfter800Mbps() {
        return mVal.throughputBonusNumeratorAfter800Mbps;
    }

    /**
     * Getter for throughput denominator after 800Mbps.
     */
    public int getThroughputBonusDenominatorAfter800Mbps() {
        return mVal.throughputBonusDenominatorAfter800Mbps;
    }

    /**
     * Feature flag for boosting 6Ghz RSSI based on channel width.
     */
    public boolean is6GhzBeaconRssiBoostEnabled() {
        return mVal.enable6GhzBeaconRssiBoost;
    }

    /*
     * Returns the maximum bonus for the network selection candidate score
     * for the contribution of the selected score.
     */
    public int getThroughputBonusLimit() {
        return mVal.throughputBonusLimit;
    }

    /*
     * Returns the bonus for the network selection candidate score
     * for a saved network (i.e., not a suggestion).
     */
    public int getSavedNetworkBonus() {
        return mVal.savedNetworkBonus;
    }

    /*
     * Returns the bonus for the network selection candidate score
     * for an unmetered network.
     */
    public int getUnmeteredNetworkBonus() {
        return mVal.unmeteredNetworkBonus;
    }

    /*
     * Returns the minimum bonus for the network selection candidate score
     * for the currently connected network.
     */
    public int getCurrentNetworkBonusMin() {
        return mVal.currentNetworkBonusMin;
    }

    /*
     * Returns the percentage bonus for the network selection candidate score
     * for the currently connected network. The percent value is applied to rssi score and
     * throughput score;
     */
    public int getCurrentNetworkBonusPercent() {
        return mVal.currentNetworkBonusPercent;
    }

    /*
     * Returns the bonus for the network selection candidate score
     * for a secure network.
     */
    public int getSecureNetworkBonus() {
        return mVal.secureNetworkBonus;
    }

    /**
     * Returns the bonus given if the network belongs to the 6Ghz band.
     */
    public int getBand6GhzBonus() {
        return mVal.band6GhzBonus;
    }

    /**
     * Returns the expected amount of score to reach the next tier during candidate scoring. This
     * value should be configured according to the value of parameters that determine the
     * scoring buckets such as {@code config_wifiFrameworkSavedNetworkBonus} and
     * {@code config_wifiFrameworkUnmeteredNetworkBonus}.
     */
    public int getScoringBucketStepSize() {
        return mVal.scoringBucketStepSize;
    }

    /*
     * Returns the duration in minutes for a recently selected non-metered network
     * to be strongly favored.
     */
    public int getLastUnmeteredSelectionMinutes() {
        return mVal.lastUnmeteredSelectionMinutes;
    }

    /*
     * Returns the duration in minutes for a recently selected metered network
     * to be strongly favored.
     */
    public int getLastMeteredSelectionMinutes() {
        return mVal.lastMeteredSelectionMinutes;
    }

    /**
     * Returns the experiment identifier.
     *
     * This value may be used to tag a set of experimental settings.
     */
    public int getExperimentIdentifier() {
        return mVal.expid;
    }

    /**
     * Returns the RSSI thresholds array for the input band.
     */
    public int[] getRssiArray(int frequency) {
        if (ScanResult.is24GHz(frequency)) {
            return mVal.rssi2;
        } else if (ScanResult.is5GHz(frequency)) {
            return mVal.rssi5;
        } else if (ScanResult.is6GHz(frequency)) {
            return mVal.rssi6;
        }
        // Invalid frequency use
        Log.e(TAG, "Invalid frequency(" + frequency + "), using 5G as default rssi array");
        return mVal.rssi5;
    }

    @Override
    public String toString() {
        return mVal.toString();
    }
}
