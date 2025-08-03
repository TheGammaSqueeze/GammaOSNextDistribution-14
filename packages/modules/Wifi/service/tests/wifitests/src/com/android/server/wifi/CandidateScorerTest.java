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

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.lessThan;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import android.content.res.Resources;
import android.net.MacAddress;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiContext;
import android.net.wifi.WifiNetworkSelectionConfig;
import android.util.SparseArray;

import androidx.test.filters.SmallTest;

import com.android.modules.utils.build.SdkLevel;
import com.android.net.module.util.MacAddressUtils;
import com.android.server.wifi.WifiCandidates.Candidate;
import com.android.server.wifi.WifiCandidates.CandidateScorer;
import com.android.server.wifi.WifiCandidates.ScoredCandidate;
import com.android.wifi.resources.R;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;

/**
 * Unit tests for implementations of
 * {@link com.android.server.wifi.WifiCandidates.CandidateScorer}.
 *
 * Runs tests that any reasonable CandidateScorer should pass.
 * Individual scorers may have additional tests of their own.
 */
@SmallTest
@RunWith(Parameterized.class)
public class CandidateScorerTest extends WifiBaseTest {
    static @Mock WifiContext sContext;
    @Mock Resources mResources;

    @Parameters(name = "{index}: {0}")
    public static List<Object[]> listOfObjectArraysBecauseJUnitMadeUs() {
        ScoringParams sp;
        ArrayList<Object[]> ans = new ArrayList<>();

        sp = new ScoringParams();
        ans.add(new Object[]{
                "Compatibility Scorer",
                CompatibilityScorer.COMPATIBILITY_SCORER_DEFAULT_EXPID,
                new CompatibilityScorer(sp),
                sp});

        sp = new ScoringParams();
        ans.add(new Object[]{
                "Score Card Based Scorer",
                ScoreCardBasedScorer.SCORE_CARD_BASED_SCORER_DEFAULT_EXPID,
                new ScoreCardBasedScorer(sp),
                sp});

        sp = new ScoringParams();
        ans.add(new Object[]{
                "Bubble Function Scorer",
                BubbleFunScorer.BUBBLE_FUN_SCORER_DEFAULT_EXPID,
                new BubbleFunScorer(sp),
                sp});

        sp = new ScoringParams();
        ans.add(new Object[]{
                "Throughput Scorer",
                ThroughputScorer.THROUGHPUT_SCORER_DEFAULT_EXPID,
                new ThroughputScorer(sContext, sp),
                sp});

        return ans;
    }

    @Parameter(0)
    public String mTitleForUseInGeneratedParameterNames;

    @Parameter(1)
    public int mExpectedExpId;

    @Parameter(2)
    public CandidateScorer mCandidateScorer;

    @Parameter(3)
    public ScoringParams mScoringParams;

    private static final double TOL = 1e-6; // for assertEquals(double, double, tolerance)

    private ConcreteCandidate mCandidate1;
    private ConcreteCandidate mCandidate2;
    private WifiCandidates.Key mKey1;
    private WifiCandidates.Key mKey2;

    /**
     * Sets up for unit test
     */
    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        when(sContext.getResources()).thenReturn(mResources);
        when(mResources.getBoolean(
                R.bool.config_wifiThroughputScorerBoostForRecentlyUserSelectedNetwork))
                .thenReturn(true);
        if (mExpectedExpId == ThroughputScorer.THROUGHPUT_SCORER_DEFAULT_EXPID) {
            mScoringParams = spy(new ScoringParams());
            mCandidateScorer = new ThroughputScorer(sContext, mScoringParams);
            ((ThroughputScorer) mCandidateScorer).enableVerboseLogging(true);
        }
        mScoringParams.update("");
        ScanResultMatchInfo matchInfo1 = ScanResultMatchInfo
                .fromWifiConfiguration(WifiConfigurationTestUtil.createOpenNetwork());
        ScanResultMatchInfo matchInfo2 = ScanResultMatchInfo
                .fromWifiConfiguration(WifiConfigurationTestUtil.createEphemeralNetwork());
        MacAddress mac1 = MacAddressUtils.createRandomUnicastAddress();
        MacAddress mac2 = MacAddressUtils.createRandomUnicastAddress();

        mKey1 = new WifiCandidates.Key(matchInfo1, mac1, 1);
        mKey2 = new WifiCandidates.Key(matchInfo2, mac2, 2);
        mCandidate1 = new ConcreteCandidate().setNominatorId(0)
                .setScanRssi(-50).setFrequency(5180).setKey(mKey1);
        mCandidate2 = new ConcreteCandidate().setNominatorId(0)
                .setScanRssi(-50).setFrequency(5180).setKey(mKey1);
    }

    /**
     * Test that the expected expid is computed with the built-in defaults.
     */
    @Test
    public void testExpid() throws Exception {
        String identifier = mCandidateScorer.getIdentifier();
        assertEquals(identifier,
                mExpectedExpId,
                WifiNetworkSelector.experimentIdFromIdentifier(identifier));
    }

    /**
     * Utility function to build and evaluate a candidate.
     */
    private double evaluate(ConcreteCandidate candidate) {
        ArrayList<Candidate> candidates = new ArrayList<>(1);
        candidates.add(candidate);
        ScoredCandidate choice = mCandidateScorer.scoreCandidates(candidates);
        return Math.max(-999999999.0, choice.value);
    }

    /**
     * Evaluating equal inputs should give the same result.
     */
    @Test
    public void testEqualInputsShouldGiveTheSameResult() throws Exception {
        assertEquals(evaluate(mCandidate1), evaluate(mCandidate2), TOL);
    }

    /**
     * Prefer 5 GHz over 2.4 GHz in non-fringe conditions, similar rssi.
     */
    @Test
    public void testPrefer5GhzOver2GhzInNonFringeConditionsSimilarRssi() throws Exception {
        assertThat(evaluate(mCandidate1.setFrequency(5180).setScanRssi(-44)),
                greaterThan(evaluate(mCandidate2.setFrequency(2432).setScanRssi(-44))));
    }

    /**
     * Prefer higher rssi.
     */
    @Test
    public void testPreferHigherRssi() throws Exception {
        assertThat(evaluate(mCandidate1.setScanRssi(-70)),
                greaterThan(evaluate(mCandidate2.setScanRssi(-71))));
    }

    /**
     * Prefer a secure network over an open one.
     */
    @Test
    public void testPreferASecureNetworkOverAnOpenOne() throws Exception {
        assertThat(evaluate(mCandidate1),
                greaterThan(evaluate(mCandidate2.setOpenNetwork(true))));
    }

    /**
     * Prefer the current network, even if rssi difference is significant.
     */
    @Test
    public void testPreferTheCurrentNetworkEvenIfRssiDifferenceIsSignificant() throws Exception {
        assertThat(evaluate(mCandidate1.setScanRssi(-76).setCurrentNetwork(true)
                                    .setPredictedThroughputMbps(433)),
                greaterThan(evaluate(mCandidate2.setScanRssi(-69)
                                    .setPredictedThroughputMbps(433))));
    }

    /**
     * Prefer the current network, even if throughput difference is significant.
     */
    @Test
    public void testPreferTheCurrentNetworkEvenIfTputDifferenceIsSignificant() throws Exception {
        assertThat(evaluate(mCandidate1.setScanRssi(-57)
                                    .setCurrentNetwork(true)
                                    .setPredictedThroughputMbps(433)),
                greaterThan(evaluate(mCandidate2.setScanRssi(-57)
                                    .setPredictedThroughputMbps(560))));
    }

    /**
     * Prefer to switch when current network has low throughput and no internet (unexpected)
     */
    @Test
    public void testSwitchifCurrentNetworkNoInternetUnexpectedAndLowThroughput() throws Exception {
        if (mExpectedExpId != ThroughputScorer.THROUGHPUT_SCORER_DEFAULT_EXPID) return;
        assertThat(evaluate(mCandidate1.setScanRssi(-57)
                        .setCurrentNetwork(true)
                        .setPredictedThroughputMbps(433)
                        .setNoInternetAccess(true)
                        .setNoInternetAccessExpected(false)),
                lessThan(evaluate(mCandidate2.setScanRssi(-57)
                        .setPredictedThroughputMbps(560))));
    }

    /**
     * Prefer current network when current network has low throughput and no internet (but expected)
     */
    @Test
    public void testSwitchifCurrentNetworkHasNoInternetExpectedAndLowThroughput() throws Exception {
        if (mExpectedExpId != ThroughputScorer.THROUGHPUT_SCORER_DEFAULT_EXPID) return;
        assertThat(evaluate(mCandidate1.setScanRssi(-57)
                        .setCurrentNetwork(true)
                        .setPredictedThroughputMbps(433)
                        .setNoInternetAccess(true)
                        .setNoInternetAccessExpected(true)),
                greaterThan(evaluate(mCandidate2.setScanRssi(-57)
                        .setPredictedThroughputMbps(560))));
    }

    /**
     * Prefer to switch when current network has higher throughput but no internet access
     */
    @Test
    public void testSwitchifCurrentNetworkNoInternetAndHighThroughput() throws Exception {
        if (mExpectedExpId != ThroughputScorer.THROUGHPUT_SCORER_DEFAULT_EXPID) return;
        assertThat(evaluate(mCandidate1.setScanRssi(-57)
                        .setCurrentNetwork(true)
                        .setPredictedThroughputMbps(560)
                        .setNoInternetAccess(true)
                        .setNoInternetAccessExpected(false)),
                lessThan(evaluate(mCandidate2.setScanRssi(-57)
                        .setPredictedThroughputMbps(433))));
    }

    /**
     * Prefer to switch when current network has lower RSSI but no internet access
     */
    @Test
    public void testSwitchifCurrentNetworkNoInternetAndLowRssi() throws Exception {
        if (mExpectedExpId != ThroughputScorer.THROUGHPUT_SCORER_DEFAULT_EXPID) return;
        assertThat(evaluate(mCandidate1.setScanRssi(-57)
                        .setCurrentNetwork(true)
                        .setPredictedThroughputMbps(560)
                        .setNoInternetAccess(true)
                        .setNoInternetAccessExpected(false)),
                lessThan(evaluate(mCandidate2.setScanRssi(-70)
                        .setPredictedThroughputMbps(560))));
    }

    /**
     * With everything else the same, the current network and another candidate both without
     * internet should get the same score.
     */
    @Test
    public void testNoInternetNetworksEvaluateTheSame() throws Exception {
        if (mExpectedExpId != ThroughputScorer.THROUGHPUT_SCORER_DEFAULT_EXPID) return;
        double score1 = evaluate(mCandidate1.setScanRssi(-57)
                        .setCurrentNetwork(true)
                        .setPredictedThroughputMbps(560)
                        .setNoInternetAccess(true)
                        .setNoInternetAccessExpected(false));
        double score2 = evaluate(mCandidate2.setScanRssi(-57)
                        .setPredictedThroughputMbps(560)
                        .setNoInternetAccess(true)
                        .setNoInternetAccessExpected(false));

        // Both networks no internet and have no reboot since last use. Expect same same.
        assertEquals(score1, score2, TOL);

        // score candidate 2 but after a reboot. It should have higher score.
        double score3 = evaluate(mCandidate2.setNumRebootsSinceLastUse(1));
        assertThat(score3, greaterThan(score1));
    }

    /**
     * Prefer to switch with a larger rssi difference.
     */
    @Test
    public void testSwitchWithLargerDifference() throws Exception {
        assertThat(evaluate(mCandidate1.setScanRssi(-80)
                                       .setCurrentNetwork(true)),
                lessThan(evaluate(mCandidate2.setScanRssi(-60))));
    }

    /**
     * Stay on recently selected network.
     */
    @Test
    public void testStayOnRecentlySelected() throws Exception {
        assertThat(evaluate(mCandidate1.setScanRssi(-80)
                                       .setCurrentNetwork(true)
                                       .setLastSelectionWeight(0.25)),
                greaterThan(evaluate(mCandidate2.setScanRssi(-60))));
    }

    /**
     * Above saturation, don't switch from current even with a large rssi difference.
     */
    @Test
    public void testAboveSaturationDoNotSwitchAwayEvenWithALargeRssiDifference() throws Exception {
        int currentRssi = (mExpectedExpId == ThroughputScorer.THROUGHPUT_SCORER_DEFAULT_EXPID)
                ? mScoringParams.getSufficientRssi(mCandidate1.getFrequency()) :
                mScoringParams.getGoodRssi(mCandidate1.getFrequency());
        int unbelievablyGoodRssi = -1;
        assertThat(evaluate(mCandidate1.setScanRssi(currentRssi).setCurrentNetwork(true)),
                greaterThan(evaluate(mCandidate2.setScanRssi(unbelievablyGoodRssi))));
    }


    /**
     * Prefer high throughput network.
     */
    @Test
    public void testPreferHighThroughputNetwork() throws Exception {
        if (mExpectedExpId == ThroughputScorer.THROUGHPUT_SCORER_DEFAULT_EXPID) {
            assertThat(evaluate(mCandidate1.setScanRssi(-74)
                            .setPredictedThroughputMbps(100)),
                    greaterThan(evaluate(mCandidate2.setScanRssi(-74)
                            .setPredictedThroughputMbps(50))));
        }
    }

    /**
     * Prefer saved over suggestion.
     */
    @Test
    public void testPreferSavedOverSuggestion() throws Exception {
        if (mExpectedExpId != ThroughputScorer.THROUGHPUT_SCORER_DEFAULT_EXPID) return;
        assertThat(evaluate(mCandidate1.setScanRssi(-77).setEphemeral(false)),
                greaterThan(evaluate(mCandidate2.setScanRssi(-40)
                                                .setEphemeral(true)
                                                .setPredictedThroughputMbps(1000))));
    }

    /**
     * Prefer metered saved over unmetered suggestion.
     */
    @Test
    public void testPreferMeteredSavedOverUnmeteredSuggestion() throws Exception {
        if (mExpectedExpId != ThroughputScorer.THROUGHPUT_SCORER_DEFAULT_EXPID) return;
        assertThat(evaluate(mCandidate1.setScanRssi(-77).setEphemeral(false).setMetered(false)),
                greaterThan(evaluate(mCandidate2.setScanRssi(-40)
                                                .setEphemeral(true)
                                                .setMetered(true)
                                                .setPredictedThroughputMbps(1000))));
    }

    /**
     * Prefer trusted metered suggestion over privileged untrusted.
     */
    @Test
    public void testPreferTrustedOverUntrusted() throws Exception {
        if (mExpectedExpId != ThroughputScorer.THROUGHPUT_SCORER_DEFAULT_EXPID) return;
        assertThat(evaluate(mCandidate1.setScanRssi(-77).setEphemeral(true).setMetered(true)),
                greaterThan(evaluate(mCandidate2.setScanRssi(-40)
                                                .setEphemeral(true)
                                                .setPredictedThroughputMbps(1000)
                                                .setTrusted(false)
                                                .setCarrierOrPrivileged(true))));
    }

    /**
     * Prefer not oem paid suggestion over privileged oem paid suggestion even though the OEM paid
     * network has better t'put & RSSI.
     */
    @Test
    public void testPreferNotOemPaidOverOemPaid() throws Exception {
        if (mExpectedExpId != ThroughputScorer.THROUGHPUT_SCORER_DEFAULT_EXPID) return;
        assertThat(evaluate(mCandidate1.setScanRssi(-71)
                        .setPredictedThroughputMbps(100)
                        .setEphemeral(true)
                        .setOemPaid(false)),
                greaterThan(evaluate(mCandidate2.setScanRssi(-40)
                        .setEphemeral(true)
                        .setPredictedThroughputMbps(1000)
                        .setOemPaid(true))));
    }

    /**
     * Prefer not oem paid metered suggestion over privileged oem paid suggestion even though the
     * OEM paid network has better t'put & RSSI.
     */
    @Test
    public void testPreferNotOemPaidMeteredOverOemPaid() throws Exception {
        if (mExpectedExpId != ThroughputScorer.THROUGHPUT_SCORER_DEFAULT_EXPID) return;
        assertThat(evaluate(mCandidate1.setScanRssi(-71)
                        .setPredictedThroughputMbps(100)
                        .setEphemeral(true)
                        .setMetered(true)
                        .setOemPaid(false)),
                greaterThan(evaluate(mCandidate2.setScanRssi(-40)
                        .setEphemeral(true)
                        .setPredictedThroughputMbps(1000)
                        .setOemPaid(true))));
    }

    /**
     * Prefer not oem paid suggestion over privileged oem private suggestion even though the OEM
     * paid network has better t'put & RSSI.
     */
    @Test
    public void testPreferNotOemPrivateOverOemPrivate() throws Exception {
        if (mExpectedExpId != ThroughputScorer.THROUGHPUT_SCORER_DEFAULT_EXPID) return;
        assertThat(evaluate(mCandidate1.setScanRssi(-71)
                        .setPredictedThroughputMbps(100)
                        .setEphemeral(true)
                        .setOemPrivate(false)),
                greaterThan(evaluate(mCandidate2.setScanRssi(-40)
                        .setEphemeral(true)
                        .setPredictedThroughputMbps(1000)
                        .setOemPaid(true))));
    }

    /**
     * Prefer not oem paid metered suggestion over privileged oem private suggestion even though the
     * OEM paid network has better t'put & RSSI.
     */
    @Test
    public void testPreferNotOemPrivateMeteredOverOemPrivate() throws Exception {
        if (mExpectedExpId != ThroughputScorer.THROUGHPUT_SCORER_DEFAULT_EXPID) return;
        assertThat(evaluate(mCandidate1.setScanRssi(-71)
                        .setPredictedThroughputMbps(100)
                        .setEphemeral(true)
                        .setMetered(true)
                        .setOemPrivate(false)),
                greaterThan(evaluate(mCandidate2.setScanRssi(-40)
                        .setEphemeral(true)
                        .setPredictedThroughputMbps(1000)
                        .setOemPaid(true))));
    }

    /**
     * Prefer oem paid suggestion over oem private suggestion even though the
     * OEM private network has better t'put & RSSI.
     */
    @Test
    public void testPreferOemPaidOverOemPrivate() throws Exception {
        if (mExpectedExpId != ThroughputScorer.THROUGHPUT_SCORER_DEFAULT_EXPID) return;
        assertThat(evaluate(mCandidate1.setScanRssi(-71)
                        .setPredictedThroughputMbps(100)
                        .setEphemeral(true)
                        .setOemPaid(true)),
                greaterThan(evaluate(mCandidate2.setScanRssi(-40)
                        .setPredictedThroughputMbps(1000)
                        .setEphemeral(true)
                        .setOemPrivate(true))));
    }

    /**
     * Prefer carrier untrusted over other untrusted.
     */
    @Test
    public void testPreferCarrierUntrustedOverOtherUntrusted() throws Exception {
        if (mExpectedExpId != ThroughputScorer.THROUGHPUT_SCORER_DEFAULT_EXPID) return;
        assertThat(evaluate(mCandidate1.setScanRssi(-77)
                                       .setEphemeral(true)
                                       .setMetered(true)
                                       .setCarrierOrPrivileged(true)),
                greaterThan(evaluate(mCandidate2.setScanRssi(-40)
                                                .setPredictedThroughputMbps(1000)
                                                .setTrusted(false))));
    }

    /**
     * Verify that the ThroughputScorer prefers a current network that has internet over a
     * candidate that has no internet.
     */
    @Test
    public void testPreferCurrentNetworkWithInternetOverNetworkWithNoInternet() throws Exception {
        if (mExpectedExpId == ThroughputScorer.THROUGHPUT_SCORER_DEFAULT_EXPID) {
            // mCandidate2 should have lower score due to not having internet
            mCandidate1.setScanRssi(-77)
                    .setPredictedThroughputMbps(30)
                    .setCurrentNetwork(true)
                    .setNoInternetAccess(false)
                    .setNoInternetAccessExpected(false);
            mCandidate2.setScanRssi(-40)
                    .setPredictedThroughputMbps(100)
                    .setCurrentNetwork(false)
                    .setNoInternetAccess(true)
                    .setNoInternetAccessExpected(false);
            double score1 = evaluate(mCandidate1);
            assertThat(evaluate(mCandidate2), lessThan(score1));

            // Then verify that when evaluated together, mCandidate1 wins because it is the current
            // network and has internet
            List<Candidate> candidates = new ArrayList<>();
            candidates.add(mCandidate1);
            candidates.add(mCandidate2);
            ScoredCandidate choice = mCandidateScorer.scoreCandidates(candidates);
            assertEquals(score1, choice.value, TOL);
        }
    }

    @Test
    public void test6GhzRssiBoost() {
        if (mExpectedExpId == ThroughputScorer.THROUGHPUT_SCORER_DEFAULT_EXPID) {
            // setup a 5Ghz and a 6Ghz candidate with the same RSSI
            mCandidate1 = new ConcreteCandidate().setNominatorId(0)
                    .setScanRssi(-77).setFrequency(5180).setKey(mKey1)
                    .setChannelWidth(ScanResult.CHANNEL_WIDTH_20MHZ);
            mCandidate2 = new ConcreteCandidate().setNominatorId(0)
                    .setScanRssi(-77).setFrequency(5975)
                    .setChannelWidth(ScanResult.CHANNEL_WIDTH_20MHZ).setKey(mKey2);

            // both should be equal score when both networks have 20Mhz channel width
            assertEquals(evaluate(mCandidate2), evaluate(mCandidate1), TOL);

            // increase channel width of 6Ghz network and verify it now has a higher score
            mCandidate2.setChannelWidth(ScanResult.CHANNEL_WIDTH_80MHZ);
            assertTrue(evaluate(mCandidate2) > evaluate(mCandidate1));
        }
    }

    @Test
    public void testThroughputBeforeAndAfter800Mbps() {
        if (mExpectedExpId == ThroughputScorer.THROUGHPUT_SCORER_DEFAULT_EXPID) {
            // setup candidate
            mCandidate1 = new ConcreteCandidate().setNominatorId(0).setScanRssi(-50)
                    .setFrequency(5180).setPredictedThroughputMbps(0).setKey(mKey1);
            double scoreThroughput0Mbps = evaluate(mCandidate1);

            mCandidate1.setPredictedThroughputMbps(800);
            double scoreThroughput800Mbps = evaluate(mCandidate1);

            mCandidate1.setPredictedThroughputMbps(2000);
            double scoreThroughput2800Mbps = evaluate(mCandidate1);

            // verify score awarded according to expected slope when throughput <= 800Mbps
            assertEquals("expected score under 800Mbps does not match",
                    800 * mScoringParams.getThroughputBonusNumerator()
                    / mScoringParams.getThroughputBonusDenominator(),
                    scoreThroughput800Mbps - scoreThroughput0Mbps, TOL);

            // verify score awarded according to expected slope when throughput > 800Mbps
            assertEquals("expected score over 800Mbps does not match",
                    1200 * mScoringParams.getThroughputBonusNumeratorAfter800Mbps()
                    / mScoringParams.getThroughputBonusDenominatorAfter800Mbps(),
                    scoreThroughput2800Mbps - scoreThroughput800Mbps, TOL);
        }
    }

    @Test
    public void testBand6GhzBonusIsCapped() {
        if (mExpectedExpId == ThroughputScorer.THROUGHPUT_SCORER_DEFAULT_EXPID) {
            doReturn(0).when(mScoringParams).getBand6GhzBonus();
            doReturn(500).when(mScoringParams).getScoringBucketStepSize();

            // setup a 6Ghz candidate
            mCandidate1 = new ConcreteCandidate().setNominatorId(0)
                    .setScanRssi(-77).setFrequency(5975).setKey(mKey1);
            double scoreNoBandBonus = evaluate(mCandidate1);

            // Verify the 6Ghz band specific bonus is applied.
            doReturn(5).when(mScoringParams).getBand6GhzBonus();
            assertEquals(scoreNoBandBonus + 5, evaluate(mCandidate1), TOL);

            // Verify the band bonus is capped by the scoring bucket size.
            doReturn(500).when(mScoringParams).getBand6GhzBonus();
            assertTrue(evaluate(mCandidate1) < scoreNoBandBonus + 500);

            // Increasing the scoring bucket size should allow more band bonus to get applied.
            doReturn(1000).when(mScoringParams).getScoringBucketStepSize();
            assertEquals(scoreNoBandBonus + 500, evaluate(mCandidate1), TOL);
        }
    }

    @Test
    public void testFrequencyScore() {
        assumeTrue(SdkLevel.isAtLeastT());
        if (mExpectedExpId == ThroughputScorer.THROUGHPUT_SCORER_DEFAULT_EXPID) {
            // setup two candidates with the same RSSI
            mCandidate1 = new ConcreteCandidate().setNominatorId(0)
                    .setScanRssi(-77).setFrequency(5180).setKey(mKey1);
            mCandidate2 = new ConcreteCandidate().setNominatorId(0)
                    .setScanRssi(-77).setFrequency(5975).setKey(mKey2);

            // both should be equal score
            assertEquals(evaluate(mCandidate2), evaluate(mCandidate1), TOL);

            // set high frequency weight and verify candidate 2 has higher score
            SparseArray<Integer> weights = new SparseArray<>();
            weights.put(5975, WifiNetworkSelectionConfig.FREQUENCY_WEIGHT_HIGH);
            mScoringParams.setFrequencyWeights(weights);
            assertTrue(evaluate(mCandidate2) > evaluate(mCandidate1));

            // set low frequency weight and verify candidate 2 has lower score
            weights.put(5975, WifiNetworkSelectionConfig.FREQUENCY_WEIGHT_LOW);
            mScoringParams.setFrequencyWeights(weights);
            assertTrue(evaluate(mCandidate2) < evaluate(mCandidate1));
        }
    }
}
