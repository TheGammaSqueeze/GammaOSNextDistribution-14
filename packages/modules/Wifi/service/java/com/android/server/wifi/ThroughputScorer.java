/*
 * Copyright 2019 The Android Open Source Project
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

import static com.android.server.wifi.WifiNetworkSelector.NetworkNominator.NOMINATOR_ID_SCORED;

import android.annotation.NonNull;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiContext;
import android.util.Log;

import com.android.server.wifi.WifiCandidates.Candidate;
import com.android.server.wifi.WifiCandidates.ScoredCandidate;
import com.android.wifi.resources.R;

import java.util.Collection;

/**
 * A candidate scorer that combines RSSI base score and network throughput score.
 */
final class ThroughputScorer implements WifiCandidates.CandidateScorer {
    private static final String TAG = "ThroughputScorer";
    private boolean mVerboseLoggingEnabled = false;

    /**
     * This should match WifiNetworkSelector.experimentIdFromIdentifier(getIdentifier())
     * when using the default ScoringParams.
     */
    public static final int THROUGHPUT_SCORER_DEFAULT_EXPID = 42330058;

    /**
     * Base score that is large enough to override all of the other categories.
     * This is applied to the last-select network for a limited duration.
     */
    public static final int TOP_TIER_BASE_SCORE = 1_000_000;

    private final WifiContext mWifiContext;
    private final ScoringParams mScoringParams;

    // config_wifi_framework_RSSI_SCORE_OFFSET
    public static final int RSSI_SCORE_OFFSET = 85;

    // config_wifi_framework_RSSI_SCORE_SLOPE
    public static final int RSSI_SCORE_SLOPE_IS_4 = 4;

    /**
     * Sample scoring buckets (assumes default overlay bucket sizes for metered, saved, etc):
     * 0 -> 500: OEM private
     * 500 -> 1000: OEM paid
     * 1000 -> 1500: untrusted 3rd party
     * 1500 -> 2000: untrusted carrier
     * 2000 -> 2500: metered suggestions
     * 2500 -> 3000: metered saved
     * 3000 -> 3500: unmetered suggestions
     * 3500 -> 4000: unmetered saved
     */
    public static final int TRUSTED_AWARD = 1000;
    public static final int HALF_TRUSTED_AWARD = 1000 / 2;
    public static final int NOT_OEM_PAID_AWARD = 500;
    public static final int NOT_OEM_PRIVATE_AWARD = 500;

    private static final boolean USE_USER_CONNECT_CHOICE = true;

    ThroughputScorer(WifiContext wifiContext, ScoringParams scoringParams) {
        mWifiContext = wifiContext;
        mScoringParams = scoringParams;
    }

    @Override
    public String getIdentifier() {
        return "ThroughputScorer";
    }

    /**
     * Enable (or disable) verbose logging for ThroughputScorer
     */
    public void enableVerboseLogging(boolean verboseEnabled) {
        mVerboseLoggingEnabled = verboseEnabled;
    }

    /**
     * Calculates an individual candidate's score.
     */
    private ScoredCandidate scoreCandidate(Candidate candidate, boolean currentNetworkHasInternet) {
        int rssiBaseScore = calculateRssiScore(candidate);
        int throughputBonusScore = calculateThroughputBonusScore(candidate);
        int rssiAndThroughputScore = rssiBaseScore + throughputBonusScore;
        int frequencyScore = mScoringParams.getFrequencyScore(candidate.getFrequency());

        boolean unExpectedNoInternet = candidate.hasNoInternetAccess()
                && !candidate.isNoInternetAccessExpected();
        int currentNetworkBonusMin = mScoringParams.getCurrentNetworkBonusMin();
        int currentNetworkBonus = Math.max(currentNetworkBonusMin, rssiAndThroughputScore
                * mScoringParams.getCurrentNetworkBonusPercent() / 100);
        int bandSpecificBonus = ScanResult.is6GHz(candidate.getFrequency())
                ? mScoringParams.getBand6GhzBonus() : 0;
        int currentNetworkBoost = (candidate.isCurrentNetwork() && !unExpectedNoInternet)
                ? currentNetworkBonus : 0;
        int rssiBoost = unExpectedNoInternet && candidate.getNumRebootsSinceLastUse() == 0
                ? 0 : rssiBaseScore;
        int throughputBoost = unExpectedNoInternet && candidate.getNumRebootsSinceLastUse() == 0
                ? 0 : throughputBonusScore;

        int securityAward = candidate.isOpenNetwork()
                ? 0
                : mScoringParams.getSecureNetworkBonus();

        int unmeteredAward = candidate.isMetered()
                ? 0
                : mScoringParams.getUnmeteredNetworkBonus();

        int savedNetworkAward = candidate.isEphemeral() ? 0 : mScoringParams.getSavedNetworkBonus();

        int trustedAward = TRUSTED_AWARD;
        if (!candidate.isTrusted() || candidate.isRestricted()) {
            // Saved networks are not untrusted or restricted, but clear anyway
            savedNetworkAward = 0;
            unmeteredAward = 0; // Ignore metered for untrusted and restricted networks
            if (candidate.isCarrierOrPrivileged()) {
                trustedAward = HALF_TRUSTED_AWARD;
            } else if (candidate.getNominatorId() == NOMINATOR_ID_SCORED) {
                Log.e(TAG, "ScoredNetworkNominator is not carrier or privileged!");
                trustedAward = 0;
            } else {
                trustedAward = 0;
            }
        }

        int notOemPaidAward = NOT_OEM_PAID_AWARD;
        if (candidate.isOemPaid()) {
            savedNetworkAward = 0; // Saved networks are not oem paid, but clear anyway
            unmeteredAward = 0; // Ignore metered for oem paid networks
            trustedAward = 0; // Ignore untrusted for oem paid networks.
            notOemPaidAward = 0;
        }

        int notOemPrivateAward = NOT_OEM_PRIVATE_AWARD;
        if (candidate.isOemPrivate()) {
            savedNetworkAward = 0; // Saved networks are not oem paid, but clear anyway
            unmeteredAward = 0; // Ignore metered for oem paid networks
            trustedAward = 0; // Ignore untrusted for oem paid networks.
            notOemPaidAward = 0;
            notOemPrivateAward = 0;
        }

        if (candidate.isIpProvisioningTimedOut()) {
            savedNetworkAward = 0;
            unmeteredAward = 0;
            trustedAward = 0;
        }

        // These scores determine which scoring bucket the candidate falls into. The scoring buckets
        // should not overlap so candidate in a higher bucket should always win against candidate in
        // a lower bucket.
        // Note: securityAward can be configured per carrier requirement to adjust the priority
        // bucket of non-open network.
        int scoreToDetermineBucket = unmeteredAward + savedNetworkAward + trustedAward
                + notOemPaidAward + notOemPrivateAward + securityAward;
        // Within the same scoring bucket, ties are broken by the following bonus scores. The sum
        // of these scores should be capped to the buket step size to prevent overlapping bucket.
        int scoreWithinBucket = rssiBoost + throughputBoost + currentNetworkBoost
                + bandSpecificBonus + frequencyScore;
        int score = scoreToDetermineBucket
                + Math.min(mScoringParams.getScoringBucketStepSize(), scoreWithinBucket);

        // do not select a network that has no internet when the current network has internet.
        if (currentNetworkHasInternet && !candidate.isCurrentNetwork() && unExpectedNoInternet) {
            score = 0;
        }

        if (candidate.getLastSelectionWeight() > 0.0 && (mWifiContext.getResources().getBoolean(
                R.bool.config_wifiThroughputScorerBoostForRecentlyUserSelectedNetwork)
                        || !candidate.isUserSelected())) {
            // Put a recently-selected network in a tier above everything else,
            // but include rssi and throughput contributions for BSSID selection.
            score = TOP_TIER_BASE_SCORE + rssiBaseScore + throughputBonusScore;
        }

        if (mVerboseLoggingEnabled) {
            Log.d(TAG, "Score for candidate: SSID: " + candidate.getKey().matchInfo.networkSsid
                    + " BSSID: " + candidate.getKey().bssid
                    + " rssiScore: " + rssiBaseScore
                    + " throughputScore: " + throughputBonusScore
                    + " currentNetworkBoost: " + currentNetworkBoost
                    + " securityAward: " + securityAward
                    + " unmeteredAward: " + unmeteredAward
                    + " savedNetworkAward: " + savedNetworkAward
                    + " trustedAward: " + trustedAward
                    + " notOemPaidAward: " + notOemPaidAward
                    + " notOemPrivateAward: " + notOemPrivateAward
                    + " frequencyScore: " + frequencyScore
                    + " final score: " + score);
        }

        // The old method breaks ties on the basis of RSSI, which we can
        // emulate easily since our score does not need to be an integer.
        double tieBreaker = candidate.getScanRssi() / 1000.0;
        return new ScoredCandidate(score + tieBreaker, 10,
                USE_USER_CONNECT_CHOICE, candidate);
    }

    private int calculateRssiScore(Candidate candidate) {
        int rssiSaturationThreshold = mScoringParams.getSufficientRssi(candidate.getFrequency());
        int rssi = candidate.getScanRssi();
        if (mScoringParams.is6GhzBeaconRssiBoostEnabled()
                && ScanResult.is6GHz(candidate.getFrequency())) {
            switch (candidate.getChannelWidth()) {
                case ScanResult.CHANNEL_WIDTH_40MHZ:
                    rssi += 3;
                    break;
                case ScanResult.CHANNEL_WIDTH_80MHZ:
                    rssi += 6;
                    break;
                case ScanResult.CHANNEL_WIDTH_160MHZ:
                    rssi += 9;
                    break;
                case ScanResult.CHANNEL_WIDTH_320MHZ:
                    rssi += 12;
                    break;
                default:
                    // do nothing
            }
        }

        rssi = Math.min(rssi, rssiSaturationThreshold);
        return (rssi + RSSI_SCORE_OFFSET) * RSSI_SCORE_SLOPE_IS_4;
    }

    private int calculateThroughputBonusScore(Candidate candidate) {
        int throughput =
                candidate.isMultiLinkCapable() && (candidate.getPredictedMultiLinkThroughputMbps()
                        > candidate.getPredictedThroughputMbps())
                        ? candidate.getPredictedMultiLinkThroughputMbps()
                        : candidate.getPredictedThroughputMbps();
        int throughputUpTo800Mbps = Math.min(800, throughput);
        int throughputMoreThan800Mbps = Math.max(0, throughput - 800);

        int throughputScoreRaw = (throughputUpTo800Mbps
                * mScoringParams.getThroughputBonusNumerator()
                / mScoringParams.getThroughputBonusDenominator())
                + (throughputMoreThan800Mbps
                * mScoringParams.getThroughputBonusNumeratorAfter800Mbps()
                / mScoringParams.getThroughputBonusDenominatorAfter800Mbps());
        return Math.min(throughputScoreRaw, mScoringParams.getThroughputBonusLimit());
    }

    private boolean doesAnyCurrentNetworksHaveInternet(@NonNull Collection<Candidate> candidates) {
        for (Candidate candidate : candidates) {
            if (candidate.isCurrentNetwork() && !candidate.hasNoInternetAccess()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public ScoredCandidate scoreCandidates(@NonNull Collection<Candidate> candidates) {
        ScoredCandidate choice = ScoredCandidate.NONE;
        boolean currentNetworkHasInternet = doesAnyCurrentNetworksHaveInternet(candidates);
        for (Candidate candidate : candidates) {
            ScoredCandidate scoredCandidate = scoreCandidate(candidate, currentNetworkHasInternet);
            if (scoredCandidate.value > choice.value) {
                choice = scoredCandidate;
            }
        }
        // Here we just return the highest scored candidate; we could
        // compute a new score, if desired.
        return choice;
    }

}
