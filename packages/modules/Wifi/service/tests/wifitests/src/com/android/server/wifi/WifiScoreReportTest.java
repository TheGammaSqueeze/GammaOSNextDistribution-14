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

import static com.android.server.wifi.ClientModeImpl.WIFI_WORK_SOURCE;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;
import static org.mockito.AdditionalAnswers.answerVoid;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.res.Resources;
import android.net.MacAddress;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkScore;
import android.net.wifi.IScoreUpdateObserver;
import android.net.wifi.IWifiConnectedNetworkScorer;
import android.net.wifi.MloLink;
import android.net.wifi.WifiConnectedSessionInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiScanner;
import android.net.wifi.WifiUsabilityStatsEntry;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.test.TestLooper;

import androidx.test.filters.SmallTest;

import com.android.modules.utils.build.SdkLevel;
import com.android.wifi.resources.R;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatcher;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.verification.VerificationMode;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Unit tests for {@link com.android.server.wifi.WifiScoreReport}.
 */
@RunWith(Parameterized.class)
@SmallTest
public class WifiScoreReportTest extends WifiBaseTest {
    class FakeClock extends Clock {
        long mWallClockMillis = 1500000000000L;
        long mElapsedSinceBootMillis = 0L;
        int mStepMillis = 1001;

        @Override
        public long getWallClockMillis() {
            mWallClockMillis += mStepMillis;
            return mWallClockMillis;
        }

        @Override
        public long getElapsedSinceBootMillis() {
            return mElapsedSinceBootMillis;
        }
    }

    private static final int TEST_LOW_CONNECTED_SCORE_SCAN_PERIOD_SECONDS = 60;
    private static final int TEST_NETWORK_ID = 860370;
    private static final int TEST_SESSION_ID = 8603703; // last digit is a check digit
    private static final String TEST_IFACE_NAME = "wlan0";
    public static final String TEST_BSSID = "00:00:00:00:00:00";
    public static final boolean TEST_USER_SELECTED = true;
    public static final int TEST_NETWORK_SWITCH_DIALOG_DISABLED_MS = 300_000;
    private static final int TEST_UID = 435546654;

    FakeClock mClock;
    WifiScoreReport mWifiScoreReport;
    ExtendedWifiInfo mWifiInfo;
    ScoringParams mScoringParams;
    @Mock WifiNetworkAgent mNetworkAgent;
    WifiThreadRunner mWifiThreadRunner;
    @Mock Context mContext;
    @Mock Resources mResources;
    @Mock WifiMetrics mWifiMetrics;
    @Mock PrintWriter mPrintWriter;
    @Mock IBinder mAppBinder;
    @Mock IWifiConnectedNetworkScorer mWifiConnectedNetworkScorer;
    @Mock WifiNative mWifiNative;
    @Mock WifiBlocklistMonitor mWifiBlocklistMonitor;
    @Mock Network mNetwork;
    @Mock WifiScoreCard mWifiScoreCard;
    @Mock WifiScoreCard.PerNetwork mPerNetwork;
    @Mock DeviceConfigFacade mDeviceConfigFacade;
    @Mock AdaptiveConnectivityEnabledSettingObserver mAdaptiveConnectivityEnabledSettingObserver;
    @Mock ExternalScoreUpdateObserverProxy mExternalScoreUpdateObserverProxy;
    @Mock WifiSettingsStore mWifiSettingsStore;
    @Mock WifiGlobals mWifiGlobals;
    @Mock ActiveModeWarden mActiveModeWarden;
    @Mock WifiConnectivityManager mWifiConnectivityManager;
    @Mock WifiConfigManager mWifiConfigManager;
    @Captor ArgumentCaptor<WifiManager.ScoreUpdateObserver> mExternalScoreUpdateObserverCbCaptor;
    private TestLooper mLooper;

    public class WifiConnectedNetworkScorerImpl extends IWifiConnectedNetworkScorer.Stub {
        public int mSessionId = -1;

        @Override
        public void onStart(WifiConnectedSessionInfo sessionInfo) {
            mSessionId = sessionInfo.getSessionId();
        }
        @Override
        public void onStop(int sessionId) {
        }
        @Override
        public void onSetScoreUpdateObserver(IScoreUpdateObserver observerImpl) {
        }
        @Override
        public void onNetworkSwitchAccepted(
                int sessionId, int targetNetworkId, String targetBssid) {
        }
        @Override
        public void onNetworkSwitchRejected(
                int sessionId, int targetNetworkId, String targetBssid) {
        }
    }

    @Parameterized.Parameter(0)
    public boolean mIsPrimary;

    @Parameters(name = "{0}")
    public static List<Boolean> isPrimaryValueList() {
        if (SdkLevel.isAtLeastT()) {
            return Arrays.asList(true, false);
        }
        return Arrays.asList(true);
    }

    /**
     * Sets up resource values for testing
     *
     * See frameworks/base/core/res/res/values/config.xml
     */
    private void setUpResources(Resources resources) {
        when(resources.getInteger(
                R.integer.config_wifi_framework_wifi_score_bad_rssi_threshold_5GHz))
            .thenReturn(-82);
        when(resources.getInteger(
                R.integer.config_wifi_framework_wifi_score_entry_rssi_threshold_5GHz))
            .thenReturn(-77);
        when(resources.getInteger(
                R.integer.config_wifi_framework_wifi_score_low_rssi_threshold_5GHz))
            .thenReturn(-70);
        when(resources.getInteger(
                R.integer.config_wifi_framework_wifi_score_good_rssi_threshold_5GHz))
            .thenReturn(-57);
        when(resources.getInteger(
                R.integer.config_wifi_framework_wifi_score_bad_rssi_threshold_24GHz))
            .thenReturn(-85);
        when(resources.getInteger(
                R.integer.config_wifi_framework_wifi_score_entry_rssi_threshold_24GHz))
            .thenReturn(-80);
        when(resources.getInteger(
                R.integer.config_wifi_framework_wifi_score_low_rssi_threshold_24GHz))
            .thenReturn(-73);
        when(resources.getInteger(
                R.integer.config_wifi_framework_wifi_score_good_rssi_threshold_24GHz))
            .thenReturn(-60);
        when(resources.getInteger(
                R.integer.config_wifiFrameworkScoreBadRssiThreshold6ghz))
            .thenReturn(-82);
        when(resources.getInteger(
                R.integer.config_wifiFrameworkScoreEntryRssiThreshold6ghz))
            .thenReturn(-77);
        when(resources.getInteger(
                R.integer.config_wifiFrameworkScoreLowRssiThreshold6ghz))
            .thenReturn(-70);
        when(resources.getInteger(
                R.integer.config_wifiFrameworkScoreGoodRssiThreshold6ghz))
            .thenReturn(-57);
        when(resources.getInteger(
                R.integer.config_wifiFrameworkMinPacketPerSecondHighTraffic))
            .thenReturn(100);
        when(resources.getBoolean(
                R.bool.config_wifiMinConfirmationDurationSendNetworkScoreEnabled))
            .thenReturn(false);
    }

    /**
     * Sets up for unit test
     */
    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        setUpResources(mResources);
        mWifiInfo = new ExtendedWifiInfo(mWifiGlobals, TEST_IFACE_NAME);
        mWifiInfo.setFrequency(2412);
        mWifiInfo.setBSSID(TEST_BSSID);
        mLooper = new TestLooper();
        when(mActiveModeWarden.canRequestSecondaryTransientClientModeManager()).thenReturn(true);
        when(mWifiGlobals.getWifiLowConnectedScoreThresholdToTriggerScanForMbb()).thenReturn(
                getTransitionScore());
        when(mWifiGlobals.getWifiLowConnectedScoreScanPeriodSeconds()).thenReturn(
                TEST_LOW_CONNECTED_SCORE_SCAN_PERIOD_SECONDS);
        when(mContext.getResources()).thenReturn(mResources);
        when(mResources.getInteger(
                R.integer.config_wifiNetworkSwitchDialogDisabledMsWhenMarkedUsable))
                .thenReturn(TEST_NETWORK_SWITCH_DIALOG_DISABLED_MS);
        when(mNetwork.getNetId()).thenReturn(0);
        when(mNetworkAgent.getNetwork()).thenReturn(mNetwork);
        when(mNetworkAgent.getCurrentNetworkCapabilities()).thenReturn(
                new NetworkCapabilities.Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                        .build());
        mClock = new FakeClock();
        mScoringParams = new ScoringParams();
        mWifiThreadRunner = new WifiThreadRunner(new Handler(mLooper.getLooper()));
        when(mAdaptiveConnectivityEnabledSettingObserver.get()).thenReturn(true);
        mWifiScoreReport = new WifiScoreReport(mScoringParams, mClock, mWifiMetrics, mWifiInfo,
                mWifiNative, mWifiBlocklistMonitor, mWifiThreadRunner, mWifiScoreCard,
                mDeviceConfigFacade, mContext,
                mAdaptiveConnectivityEnabledSettingObserver, TEST_IFACE_NAME,
                mExternalScoreUpdateObserverProxy, mWifiSettingsStore,
                mWifiGlobals, mActiveModeWarden, mWifiConnectivityManager, mWifiConfigManager);
        mWifiScoreReport.onRoleChanged(mIsPrimary ? ActiveModeManager.ROLE_CLIENT_PRIMARY
                : ActiveModeManager.ROLE_CLIENT_SECONDARY_LONG_LIVED);
        mWifiScoreReport.setNetworkAgent(mNetworkAgent);
        when(mDeviceConfigFacade.getMinConfirmationDurationSendLowScoreMs()).thenReturn(
                DeviceConfigFacade.DEFAULT_MIN_CONFIRMATION_DURATION_SEND_LOW_SCORE_MS);
        when(mDeviceConfigFacade.getMinConfirmationDurationSendHighScoreMs()).thenReturn(
                DeviceConfigFacade.DEFAULT_MIN_CONFIRMATION_DURATION_SEND_HIGH_SCORE_MS);
        when(mDeviceConfigFacade.getRssiThresholdNotSendLowScoreToCsDbm()).thenReturn(
                DeviceConfigFacade.DEFAULT_RSSI_THRESHOLD_NOT_SEND_LOW_SCORE_TO_CS_DBM);
        when(mWifiSettingsStore.isWifiScoringEnabled()).thenReturn(true);
        when(mPerNetwork.getTxLinkBandwidthKbps()).thenReturn(40_000);
        when(mPerNetwork.getRxLinkBandwidthKbps()).thenReturn(50_000);
        when(mWifiScoreCard.lookupNetwork(any())).thenReturn(mPerNetwork);
    }

    /**
     * Cleans up after test
     */
    @After
    public void tearDown() throws Exception {
        mResources = null;
        mWifiScoreReport = null;
        mWifiMetrics = null;
    }

    /**
     * Assert a certain score was sent. Works on all SDK levels.
     * @param score expected score
     * @param mode times(n), never(), atLeastOnce(), etc.
     */
    private void verifySentNetworkScore(int score, VerificationMode mode) {
        if (SdkLevel.isAtLeastS()) {
            verify(mNetworkAgent, mode).sendNetworkScore(argThat(
                    // note that a lambda doesn't work here, will cause a crash due to missing
                    // class `NetworkScore` on R even though this code path is never reached on R.
                    // Maybe lambdas are eagerly loaded by the classloader, while inner classes
                    // aren't?
                    new ArgumentMatcher<NetworkScore>() {
                        @Override
                        public boolean matches(NetworkScore networkScore) {
                            return networkScore.getLegacyInt() == score;
                        }
                    }));
        } else {
            verify(mNetworkAgent, mode).sendNetworkScore(score);
        }
    }

    private void verifySentNetworkScore(int score) {
        verifySentNetworkScore(score, times(1));
    }

    private void verifySentAnyNetworkScore(VerificationMode mode) {
        if (SdkLevel.isAtLeastS()) {
            verify(mNetworkAgent, mode).sendNetworkScore(any(NetworkScore.class));
        } else {
            verify(mNetworkAgent, mode).sendNetworkScore(anyInt());
        }
    }

    private void verifySentAnyNetworkScore() {
        verifySentAnyNetworkScore(times(1));
    }

    private int getTransitionScore() {
        return mIsPrimary ? ConnectedScore.WIFI_TRANSITION_SCORE
                : ConnectedScore.WIFI_SECONDARY_TRANSITION_SCORE;
    }

    private int getMaxScore() {
        return mIsPrimary ? ConnectedScore.WIFI_MAX_SCORE
                : ConnectedScore.WIFI_SECONDARY_MAX_SCORE;
    }

    /**
     * Test for score reporting
     *
     * The score should be sent to both NetworkAgent and WifiMetrics
     */
    @Test
    public void calculateAndReportScoreSucceeds() throws Exception {
        // initially called once
        verifySentAnyNetworkScore();

        mWifiInfo.setRssi(-77);
        mWifiScoreReport.calculateAndReportScore();
        // called again after calculateAndReportScore()
        verifySentAnyNetworkScore(times(2));
        verify(mWifiMetrics).incrementWifiScoreCount(eq(TEST_IFACE_NAME), anyInt());
    }

    @Test
    public void mbbNetworkForceKeepUp() throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());
        reset(mNetworkAgent);

        ArgumentCaptor<NetworkScore> networkScoreCaptor =
                ArgumentCaptor.forClass(NetworkScore.class);

        // start as SECONDARY_TRANSIENT
        mWifiScoreReport.onRoleChanged(ActiveModeManager.ROLE_CLIENT_SECONDARY_TRANSIENT);

        verify(mNetworkAgent).sendNetworkScore(networkScoreCaptor.capture());
        {
            NetworkScore networkScore = networkScoreCaptor.getValue();
            assertNotEquals(WifiScoreReport.LINGERING_SCORE, networkScore.getLegacyInt());
            assertFalse(networkScore.isExiting());
            assertFalse(networkScore.isTransportPrimary());
            // force keep up network
            assertEquals(NetworkScore.KEEP_CONNECTED_FOR_HANDOVER,
                    networkScore.getKeepConnectedReason());
        }

        // network validated, becomes primary
        mWifiScoreReport.onRoleChanged(ActiveModeManager.ROLE_CLIENT_PRIMARY);
        verify(mNetworkAgent, times(2)).sendNetworkScore(networkScoreCaptor.capture());
        {
            NetworkScore networkScore = networkScoreCaptor.getValue();
            assertNotEquals(WifiScoreReport.LINGERING_SCORE, networkScore.getLegacyInt());
            assertFalse(networkScore.isExiting());
            assertTrue(networkScore.isTransportPrimary());
            // no longer need to force keep up network
            assertEquals(NetworkScore.KEEP_CONNECTED_NONE, networkScore.getKeepConnectedReason());
        }
    }

    @Test
    public void calculateAndReportScoreWhileLingering_sendLingeringScore() throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());
        reset(mNetworkAgent);

        ArgumentCaptor<NetworkScore> networkScoreCaptor =
                ArgumentCaptor.forClass(NetworkScore.class);

        // start as primary
        mWifiScoreReport.onRoleChanged(ActiveModeManager.ROLE_CLIENT_PRIMARY);

        verify(mNetworkAgent).sendNetworkScore(networkScoreCaptor.capture());
        {
            NetworkScore networkScore = networkScoreCaptor.getValue();
            assertNotEquals(WifiScoreReport.LINGERING_SCORE, networkScore.getLegacyInt());
            assertFalse(networkScore.isExiting());
            assertTrue(networkScore.isTransportPrimary());
            assertEquals(NetworkScore.KEEP_CONNECTED_NONE, networkScore.getKeepConnectedReason());
        }

        // then, role changed to SECONDARY_TRANSIENT and started lingering
        mWifiScoreReport.onRoleChanged(ActiveModeManager.ROLE_CLIENT_SECONDARY_TRANSIENT);
        mWifiScoreReport.setShouldReduceNetworkScore(true);
        // upon lingering, immediately send LINGERING_SCORE
        // capture most recent invocation
        verify(mNetworkAgent, times(3)).sendNetworkScore(networkScoreCaptor.capture());
        {
            NetworkScore networkScore = networkScoreCaptor.getValue();
            assertEquals(WifiScoreReport.LINGERING_SCORE, networkScore.getLegacyInt());
            assertFalse(networkScore.isExiting());
            assertFalse(networkScore.isTransportPrimary());
            assertEquals(NetworkScore.KEEP_CONNECTED_NONE, networkScore.getKeepConnectedReason());
        }

        mWifiInfo.setRssi(-77);
        mWifiScoreReport.calculateAndReportScore();
        // score not sent again while lingering
        verify(mNetworkAgent, times(3)).sendNetworkScore(any());

        // disable lingering
        mWifiScoreReport.onRoleChanged(ActiveModeManager.ROLE_CLIENT_PRIMARY);
        mWifiScoreReport.setShouldReduceNetworkScore(false);
        // report score again
        mWifiInfo.setRssi(-60);
        mWifiScoreReport.calculateAndReportScore();
        // Some non-lingering score is sent
        // capture most recent invocation
        verify(mNetworkAgent, times(6)).sendNetworkScore(networkScoreCaptor.capture());
        {
            NetworkScore networkScore = networkScoreCaptor.getValue();
            assertNotEquals(WifiScoreReport.LINGERING_SCORE, networkScore.getLegacyInt());
            assertFalse(networkScore.isExiting());
            assertTrue(networkScore.isTransportPrimary());
            assertEquals(NetworkScore.KEEP_CONNECTED_NONE, networkScore.getKeepConnectedReason());
        }
    }

    @Test
    public void testExternalScorerWhileLingering_sendLingeringScore() throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());

        mWifiScoreReport.onRoleChanged(ActiveModeManager.ROLE_CLIENT_SECONDARY_LONG_LIVED);

        // Register Client for verification.
        mWifiScoreReport.setWifiConnectedNetworkScorer(mAppBinder, mWifiConnectedNetworkScorer,
                TEST_UID);
        verify(mExternalScoreUpdateObserverProxy).registerCallback(
                mExternalScoreUpdateObserverCbCaptor.capture());
        when(mNetwork.getNetId()).thenReturn(TEST_NETWORK_ID);
        mWifiScoreReport.startConnectedNetworkScorer(TEST_NETWORK_ID, TEST_USER_SELECTED);
        verify(mWifiConnectedNetworkScorer).onStart(
                argThat(sessionInfo -> sessionInfo.getSessionId() == TEST_SESSION_ID
                        && sessionInfo.isUserSelected() == TEST_USER_SELECTED));

        reset(mNetworkAgent);

        ArgumentCaptor<NetworkScore> networkScoreCaptor =
                ArgumentCaptor.forClass(NetworkScore.class);
        mWifiScoreReport.setShouldReduceNetworkScore(true);
        // upon lingering, immediately send LINGERING_SCORE
        // capture most recent invocation
        verify(mNetworkAgent).sendNetworkScore(networkScoreCaptor.capture());
        {
            NetworkScore networkScore = networkScoreCaptor.getValue();
            assertEquals(WifiScoreReport.LINGERING_SCORE, networkScore.getLegacyInt());
            assertFalse(networkScore.isExiting());
            assertFalse(networkScore.isTransportPrimary());
        }
        // upon lingering, send session end to client.
        verify(mWifiConnectedNetworkScorer).onStop(TEST_SESSION_ID);

        // send score after session has ended
        mExternalScoreUpdateObserverCbCaptor.getValue().notifyStatusUpdate(TEST_SESSION_ID, false);
        mLooper.dispatchAll();
        // score not sent since session ended
        verify(mNetworkAgent, never()).sendNetworkScore(argThat(
                new ArgumentMatcher<NetworkScore>() {
                    @Override
                    public boolean matches(NetworkScore ns) {
                        return ns.getLegacyInt() == 49 && ns.isExiting() && ns.isTransportPrimary();
                    }
                }));
        assertTrue(mWifiInfo.isUsable());
    }

    /**
     * Test for no score report if rssi is invalid
     *
     * The score should be sent to neither the NetworkAgent nor the
     * WifiMetrics
     */
    @Test
    public void calculateAndReportScoreDoesNotReportWhenRssiIsNotValid() throws Exception {
        // initially called once
        verifySentAnyNetworkScore();

        mWifiInfo.setRssi(WifiInfo.INVALID_RSSI);
        mWifiScoreReport.calculateAndReportScore();
        // still only called once
        verifySentAnyNetworkScore();
        verify(mWifiMetrics, never()).incrementWifiScoreCount(any(), anyInt());
    }

    /**
     * Test for operation with null NetworkAgent
     *
     * Expect to not die, and to calculate the score and report to metrics.
     */
    @Test
    public void networkAgentMayBeNull() throws Exception {
        mWifiInfo.setRssi(-33);
        mWifiScoreReport.enableVerboseLogging(true);
        mWifiScoreReport.setNetworkAgent(null);
        mWifiScoreReport.calculateAndReportScore();
        verify(mWifiMetrics).incrementWifiScoreCount(eq(TEST_IFACE_NAME), anyInt());
    }

    /**
     * Exercise the rates with low RSSI
     *
     * The setup has a low (not bad) RSSI, and data movement (txSuccessRate) above
     * the threshold.
     *
     * Expect a score above threshold.
     */
    @Test
    public void allowLowRssiIfDataIsMoving() throws Exception {
        assumeTrue(mIsPrimary);
        mWifiInfo.setRssi(-80);
        mWifiInfo.setLinkSpeed(6); // Mbps
        mWifiInfo.setSuccessfulTxPacketsPerSecond(5.1); // proportional to pps
        mWifiInfo.setSuccessfulRxPacketsPerSecond(5.1);
        for (int i = 0; i < 10; i++) {
            mWifiScoreReport.calculateAndReportScore();
        }
        int score = mWifiInfo.getScore();
        assertTrue(score > getTransitionScore());
        verify(mWifiConnectivityManager, never()).forceConnectivityScan(any());
    }

    /**
     * Bad RSSI without data moving should allow handoff
     *
     * The setup has a bad RSSI, and the txSuccessRate is below threshold; several
     * scoring iterations are performed.
     *
     * Expect the score to drop below the handoff threshold.
     */
    @Test
    public void giveUpOnBadRssiWhenDataIsNotMoving() throws Exception {
        // initially called once
        verifySentAnyNetworkScore();

        mWifiInfo.setRssi(-100);
        mWifiInfo.setLinkSpeed(6); // Mbps
        mWifiInfo.setFrequency(5220);
        mWifiScoreReport.enableVerboseLogging(true);
        mWifiInfo.setSuccessfulTxPacketsPerSecond(0.1);
        mWifiInfo.setSuccessfulRxPacketsPerSecond(0.1);
        for (int i = 0; i < 10; i++) {
            mWifiScoreReport.calculateAndReportScore();
        }
        int score = mWifiInfo.getScore();
        assertTrue(score < getTransitionScore());
        if (SdkLevel.isAtLeastS()) {
            ArgumentCaptor<NetworkScore> scoreCaptor = ArgumentCaptor.forClass(NetworkScore.class);
            verify(mNetworkAgent, times(2)).sendNetworkScore(scoreCaptor.capture());
            NetworkScore ns = scoreCaptor.getValue();
            assertEquals(score, ns.getLegacyInt());
            assertTrue(ns.isExiting());
            if (mIsPrimary) assertTrue(ns.isTransportPrimary());
        } else {
            verify(mNetworkAgent).sendNetworkScore(score);
        }
    }

    @Test
    public void testAospScoreBreachTriggersScan() throws Exception {
        // initially called once
        verifySentAnyNetworkScore();

        // First has good RSSI but low traffic
        mWifiInfo.setRssi(-33);
        mWifiInfo.setLinkSpeed(6); // Mbps
        mWifiInfo.setFrequency(5220);
        mWifiScoreReport.enableVerboseLogging(true);
        mWifiInfo.setSuccessfulTxPacketsPerSecond(0.1);
        mWifiInfo.setSuccessfulRxPacketsPerSecond(0.1);
        for (int i = 0; i < 10; i++) {
            mWifiScoreReport.calculateAndReportScore();
        }
        assertTrue("wifi score should be high",
                mWifiInfo.getScore() >= getTransitionScore());
        verify(mWifiConnectivityManager, never()).forceConnectivityScan(any());

        // Then simulate low RSSI and traffic, and verify score becomes low and a scan is triggered.
        mWifiInfo.setRssi(-100);
        for (int i = 0; i < 10; i++) {
            mWifiScoreReport.calculateAndReportScore();
        }
        assertTrue("wifi score should be low",
                mWifiInfo.getScore() < getTransitionScore());
        verify(mWifiConnectivityManager).forceConnectivityScan(WIFI_WORK_SOURCE);

        // move time forward slightly, not passing enough time to do another scan.
        mClock.mElapsedSinceBootMillis = (long) TEST_LOW_CONNECTED_SCORE_SCAN_PERIOD_SECONDS * 1000;

        // trigger low score again and verify no additional scan yet.
        mWifiScoreReport.calculateAndReportScore();
        verify(mWifiConnectivityManager).forceConnectivityScan(WIFI_WORK_SOURCE);

        // move time past the threshold to trigger scan again and verify the scan happens
        mClock.mElapsedSinceBootMillis =
                (long) TEST_LOW_CONNECTED_SCORE_SCAN_PERIOD_SECONDS * 1000 + 1;
        mWifiScoreReport.calculateAndReportScore();
        verify(mWifiConnectivityManager, times(2)).forceConnectivityScan(WIFI_WORK_SOURCE);
    }

    /**
     * When the AOSP score is low but external scorer is registered, scan will not be triggered.
     */
    @Test
    public void testAospScoreBreachNoScanWhenExternalScorerEnabled() throws Exception {
        // register external scorer
        mWifiScoreReport.setWifiConnectedNetworkScorer(mAppBinder, mWifiConnectedNetworkScorer,
                TEST_UID);
        verify(mWifiGlobals).setUsingExternalScorer(true);
        mWifiInfo.setFrequency(5220);
        for (int rssi = -60; rssi >= -83; rssi -= 1) {
            mWifiInfo.setRssi(rssi);
            mWifiScoreReport.calculateAndReportScore();
        }
        int score = mWifiInfo.getScore();
        assertTrue(score < getTransitionScore());
        verify(mWifiConnectivityManager, never()).forceConnectivityScan(any());

        // when the external score is cleared, scan should be triggered.
        mWifiScoreReport.clearWifiConnectedNetworkScorer();
        mWifiScoreReport.calculateAndReportScore();
        assertTrue(score < getTransitionScore());
        verify(mWifiConnectivityManager).forceConnectivityScan(any());
        verify(mWifiGlobals).setUsingExternalScorer(false);
    }

    /**
     * When the score ramps down to the exit theshold, let go.
     */
    @Test
    public void giveUpOnBadRssiAggressively() throws Exception {
        String oops = "giveUpOnBadRssiAggressively";
        mWifiInfo.setFrequency(5220);
        for (int rssi = -60; rssi >= -83; rssi -= 1) {
            mWifiInfo.setRssi(rssi);
            oops += " " + mClock.mWallClockMillis + "," + rssi;
            mWifiScoreReport.calculateAndReportScore();
            oops += ":" + mWifiInfo.getScore();
        }
        int score = mWifiInfo.getScore();
        verifySentNetworkScore(score);
        assertTrue(oops, score < getTransitionScore());
    }

    /**
     * RSSI that falls rapidly but does not cross entry threshold should not cause handoff
     *
     * Expect the score to not drop below the handoff threshold.
     */
    @Test
    public void stayOnIfRssiDoesNotGetBelowEntryThreshold() throws Exception {
        assumeTrue(mIsPrimary);
        String oops = "didNotStickLanding";
        int minScore = 100;
        mWifiInfo.setLinkSpeed(6); // Mbps
        mWifiInfo.setFrequency(5220);
        mWifiScoreReport.enableVerboseLogging(true);
        mWifiInfo.setSuccessfulTxPacketsPerSecond(0.1);
        mWifiInfo.setSuccessfulRxPacketsPerSecond(0.1);
        assertTrue(mScoringParams.update("rssi5=-83:-80:-66:-55"));
        for (int r = -30; r >= -100; r -= 1) {
            int rssi = Math.max(r, -80);
            mWifiInfo.setRssi(rssi);
            oops += " " + mClock.mWallClockMillis + "," + rssi;
            mWifiScoreReport.calculateAndReportScore();
            oops += ":" + mWifiInfo.getScore();
            if (mWifiInfo.getScore() < minScore) minScore = mWifiInfo.getScore();
        }
        assertTrue(oops, minScore > getTransitionScore());
    }

    /**
     * Don't breach if the success rates are great
     *
     * Ramp the RSSI down, but maintain a high packet throughput
     *
     * Expect score to stay above above threshold.
     */
    @Test
    public void allowTerribleRssiIfDataIsMovingWell() throws Exception {
        assumeTrue(mIsPrimary);
        mWifiInfo.setSuccessfulTxPacketsPerSecond(
                mScoringParams.getYippeeSkippyPacketsPerSecond() + 0.1);
        mWifiInfo.setSuccessfulRxPacketsPerSecond(
                mScoringParams.getYippeeSkippyPacketsPerSecond() + 0.1);
        assertTrue(mWifiInfo.getSuccessfulTxPacketsPerSecond() > 10);
        mWifiInfo.setFrequency(5220);
        for (int r = -30; r >= -120; r -= 2) {
            mWifiInfo.setRssi(r);
            mWifiScoreReport.calculateAndReportScore();
            assertTrue(mWifiInfo.getScore() > getTransitionScore());
        }
        // If the throughput dips, we should let go
        mWifiInfo.setSuccessfulRxPacketsPerSecond(
                mScoringParams.getYippeeSkippyPacketsPerSecond() - 0.1);
        mWifiScoreReport.calculateAndReportScore();
        assertTrue(mWifiInfo.getScore() < getTransitionScore());
        // And even if throughput improves again, once we have decided to let go, disregard
        // the good rates.
        mWifiInfo.setSuccessfulRxPacketsPerSecond(
                mScoringParams.getYippeeSkippyPacketsPerSecond() + 0.1);
        mWifiScoreReport.calculateAndReportScore();
        assertTrue(mWifiInfo.getScore() < getTransitionScore());
    }

    /**
     * Never ask for nud check when nud=0
     */
    @Test
    public void neverAskForNudCheckWhenNudKnobIsZero() throws Exception {
        assertTrue(mScoringParams.update("nud=0"));
        assertEquals(0, mScoringParams.getNudKnob());
        mWifiInfo.setFrequency(5220);
        for (int rssi = -30; rssi >= -120; rssi -= 1) {
            mWifiInfo.setRssi(rssi);
            mWifiScoreReport.calculateAndReportScore();
            assertFalse(mWifiScoreReport.shouldCheckIpLayer());
        }
    }

    /**
     * Eventually ask for nud check when nud=1
     */
    @Test
    public void eventuallyAskForNudCheckWhenNudKnobIsOne() throws Exception {
        assumeTrue(mIsPrimary);
        String oops = "nud=1";
        long lastAskedMillis = 0; // Check that we don't send too soon
        int asks = 0; // Keep track of how many time we asked
        assertTrue(mScoringParams.update("nud=1"));
        assertEquals(1, mScoringParams.getNudKnob());
        mWifiInfo.setFrequency(5220);
        for (int rssi = -40; rssi >= -120; rssi -= 1) {
            mWifiInfo.setRssi(rssi);
            mWifiScoreReport.calculateAndReportScore();
            boolean ask = mWifiScoreReport.shouldCheckIpLayer();
            if (ask) {
                assertTrue(mWifiInfo.getScore() < getTransitionScore());
                assertTrue(oops, mClock.mWallClockMillis >= lastAskedMillis + 5000);
                lastAskedMillis = mClock.mWallClockMillis;
                oops += " " + lastAskedMillis + ":" + mWifiInfo.getScore();
                mWifiScoreReport.noteIpCheck();
                asks++;
            }
        }
        assertTrue(oops + " asks:" + asks, asks > 5 && asks < 15);
    }


    /**
     * Ask for more nud checks when nud=10
     */
    @Test
    public void askForMoreNudChecksWhenNudKnobIsBigger() throws Exception {
        assumeTrue(mIsPrimary);
        String oops = "nud=10";
        long lastAskedMillis = 0; // Check that we don't send too soon
        int asks = 0; // Keep track of how many time we asked
        assertTrue(mScoringParams.update("nud=10"));
        assertEquals(10, mScoringParams.getNudKnob());
        mWifiInfo.setFrequency(5220);
        for (int rssi = -40; rssi >= -120; rssi -= 1) {
            mWifiInfo.setRssi(rssi);
            mWifiScoreReport.calculateAndReportScore();
            boolean ask = mWifiScoreReport.shouldCheckIpLayer();
            if (ask) {
                assertTrue("rssi " + rssi + " score " + mWifiInfo.getScore()
                        + " higher than " + getTransitionScore(),
                        mWifiInfo.getScore() < getTransitionScore());
                assertTrue(oops, mClock.mWallClockMillis >= lastAskedMillis + 5000);
                lastAskedMillis = mClock.mWallClockMillis;
                oops += " " + lastAskedMillis + ":" + mWifiInfo.getScore();
                mWifiScoreReport.noteIpCheck();
                asks++;
            }
        }
        assertTrue(oops + " asks:" + asks, asks > 12 && asks < 80);
    }

    /**
     * Test initial conditions, and after reset()
     */
    @Test
    public void exerciseReset() throws Exception {
        assertFalse(mWifiScoreReport.shouldCheckIpLayer());
        mWifiScoreReport.reset();
        assertFalse(mWifiScoreReport.shouldCheckIpLayer());
    }

    /**
     * This setup causes some reports to be generated when println
     * methods are called, to check for "concurrent" modification
     * errors.
     */
    private void setupToGenerateAReportWhenPrintlnIsCalled() {
        int[] counter = new int[1];
        doAnswer(answerVoid((String line) -> {
            if (counter[0]++ < 3) {
                mWifiScoreReport.calculateAndReportScore();
            }
        })).when(mPrintWriter).println(anyString());
    }

    /**
     * Test data logging
     */
    @Test
    public void testDataLogging() throws Exception {
        for (int i = 0; i < 10; i++) {
            mWifiInfo.setRssi(-65 + i);
            mWifiInfo.setLinkSpeed(300);
            mWifiInfo.setFrequency(5220);
            mWifiInfo.setSuccessfulTxPacketsPerSecond(0.1 + i);
            mWifiInfo.setRetriedTxPacketsRate(0.2 + i);
            mWifiInfo.setLostTxPacketsPerSecond(0.01 * i);
            mWifiInfo.setSuccessfulRxPacketsPerSecond(0.3 + i);
            mWifiScoreReport.calculateAndReportScore();
        }
        setupToGenerateAReportWhenPrintlnIsCalled();
        mWifiScoreReport.dump(null, mPrintWriter, null);
        verify(mPrintWriter, times(13)).println(anyString());
    }

    /** Test data logging with MLO */
    @Test
    public void testDataLoggingMlo() throws Exception {
        List<MloLink> mloLinks = new ArrayList<>();
        MloLink link1 = new MloLink();
        link1.setBand(WifiScanner.WIFI_BAND_24_GHZ);
        link1.setChannel(6);
        link1.setApMacAddress(MacAddress.fromString("01:02:03:04:05:06"));
        link1.setTxLinkSpeedMbps(300);
        link1.setState(MloLink.MLO_LINK_STATE_ACTIVE);
        link1.setLinkId(1);

        MloLink link2 = new MloLink();
        link2.setBand(WifiScanner.WIFI_BAND_5_GHZ);
        link2.setChannel(44);
        link2.setApMacAddress(MacAddress.fromString("01:02:03:04:05:07"));
        link2.setTxLinkSpeedMbps(600);
        link2.setLinkId(2);
        link2.setState(MloLink.MLO_LINK_STATE_ACTIVE);

        mloLinks.add(link1);
        mloLinks.add(link2);

        when(mWifiMetrics.getTotalBeaconRxCount(1)).thenReturn(500L);
        when(mWifiMetrics.getLinkUsageState(1))
                .thenReturn(WifiUsabilityStatsEntry.LINK_STATE_IN_USE);
        when(mWifiMetrics.getTotalBeaconRxCount(2)).thenReturn(0L);
        when(mWifiMetrics.getLinkUsageState(2))
                .thenReturn(WifiUsabilityStatsEntry.LINK_STATE_NOT_IN_USE);

        mWifiInfo.setAffiliatedMloLinks(mloLinks);

        for (int i = 0; i < 10; i++) {
            mWifiInfo.setRssi(-65 + i);
            mWifiInfo.setLinkSpeed(300);
            mWifiInfo.setFrequency(5220);
            mWifiInfo.setSuccessfulTxPacketsPerSecond(0.1 + i);
            mWifiInfo.setRetriedTxPacketsRate(0.2 + i);
            mWifiInfo.setLostTxPacketsPerSecond(0.01 * i);
            mWifiInfo.setSuccessfulRxPacketsPerSecond(0.3 + i);
            link1.setRssi(-65 + i);
            link1.setRetriedTxPacketsRate(0.2 + i);
            link1.setLostTxPacketsPerSecond(0.01 * i);
            link1.setSuccessfulRxPacketsPerSecond(0.3 + i);
            link2.setRssi(-65 + i);
            link2.setRetriedTxPacketsRate(0.2 + i);
            link2.setLostTxPacketsPerSecond(0.01 * i);
            link2.setSuccessfulRxPacketsPerSecond(0.3 + i);
            mWifiScoreReport.calculateAndReportScore();
        }
        setupToGenerateAReportWhenPrintlnIsCalled();
        mWifiScoreReport.dump(null, mPrintWriter, null);
        verify(mPrintWriter, times(13)).println(anyString());
    }

    /**
     *  Test data logging limit
     *  <p>
     *  Check that only a bounded amount of data is collected for dumpsys report
     */
    @Test
    public void testDataLoggingLimit() throws Exception {
        for (int i = 0; i < 3620; i++) {
            mWifiInfo.setRssi(-65 + i % 20);
            mWifiInfo.setLinkSpeed(300);
            mWifiInfo.setFrequency(5220);
            mWifiInfo.setSuccessfulTxPacketsPerSecond(0.1 + i % 100);
            mWifiInfo.setRetriedTxPacketsRate(0.2 + i % 100);
            mWifiInfo.setLostTxPacketsPerSecond(0.0001 * i);
            mWifiInfo.setSuccessfulRxPacketsPerSecond(0.3 + i % 200);
            mWifiScoreReport.calculateAndReportScore();
        }
        mWifiScoreReport.dump(null, mPrintWriter, null);
        verify(mPrintWriter, atMost(3603)).println(anyString());
    }

    /**
     * Test for staying at below transition score for a certain period of time.
     */
    @Test
    public void stayAtBelowTransitionScoreForCertainPeriodOfTime() throws Exception {
        mWifiScoreReport.enableVerboseLogging(true);
        mWifiInfo.setFrequency(5220);

        // Reduce RSSI value to fall below the transition score
        for (int rssi = -60; rssi >= -83; rssi -= 1) {
            mWifiInfo.setRssi(rssi);
            mWifiScoreReport.calculateAndReportScore();
        }
        assertTrue(mWifiInfo.getScore() < getTransitionScore());

        // Then, set high RSSI value to exceed the transition score
        mWifiInfo.setRssi(-50);
        // 8 seconds elapse
        for (int i = 0; i < 8; i++) {
            mWifiScoreReport.calculateAndReportScore();
        }
        assertTrue(mWifiInfo.getScore() < getTransitionScore());

        // 9 seconds elapse
        mWifiScoreReport.calculateAndReportScore();
        assertTrue(mWifiInfo.getScore() > getTransitionScore());
    }

    /**
     * Test for resetting the internal timer which is used to keep staying at
     * below transition score for a certain period of time.
     */
    @Test
    public void stayAtBelowTransitionScoreWithReset() throws Exception {
        mWifiScoreReport.enableVerboseLogging(true);
        mWifiInfo.setFrequency(5220);

        // Reduce RSSI value to fall below the transition score
        for (int rssi = -60; rssi >= -83; rssi -= 1) {
            mWifiInfo.setRssi(rssi);
            mWifiScoreReport.calculateAndReportScore();
        }
        assertTrue(mWifiInfo.getScore() < getTransitionScore());

        // Then, set high RSSI value to exceed the transition score
        mWifiInfo.setRssi(-50);
        // Reset the internal timer so that no need to wait for 9 seconds
        mWifiScoreReport.reset();
        mWifiScoreReport.calculateAndReportScore();
        assertTrue(mWifiInfo.getScore() > getTransitionScore());
    }

    /**
     * Verify that client gets ScoreChangeCallback object when client sets its scorer.
     */
    @Test
    public void testClientNotification() throws RemoteException {
        // Register Client for verification.
        mWifiScoreReport.setWifiConnectedNetworkScorer(mAppBinder, mWifiConnectedNetworkScorer,
                TEST_UID);
        // Client should get ScoreChangeCallback.
        verify(mExternalScoreUpdateObserverProxy).registerCallback(any());
    }

    /**
     * Verify that clear client should be handled.
     */
    @Test
    public void testClearClient() throws RemoteException {
        // Register Client for verification.
        mWifiScoreReport.setWifiConnectedNetworkScorer(mAppBinder, mWifiConnectedNetworkScorer,
                TEST_UID);
        verify(mExternalScoreUpdateObserverProxy).registerCallback(any());
        mWifiScoreReport.clearWifiConnectedNetworkScorer();
        verify(mAppBinder).unlinkToDeath(any(), anyInt());
        verify(mExternalScoreUpdateObserverProxy).unregisterCallback(any());

        mWifiScoreReport.startConnectedNetworkScorer(10, true);
        verify(mWifiConnectedNetworkScorer, never()).onStart(any());
    }

    /**
     * Verify that WifiScoreReport adds for death notification on setting client.
     */
    @Test
    public void testAddsForBinderDeathOnSetClient() throws Exception {
        mWifiScoreReport.setWifiConnectedNetworkScorer(mAppBinder, mWifiConnectedNetworkScorer,
                TEST_UID);
        verify(mExternalScoreUpdateObserverProxy).registerCallback(any());
        verify(mAppBinder).linkToDeath(any(IBinder.DeathRecipient.class), anyInt());
    }

    /**
     * Verify that client fails to get message when scorer add failed.
     */
    @Test
    public void testAddsScorerFailureOnLinkToDeath() throws Exception {
        doThrow(new RemoteException())
                .when(mAppBinder).linkToDeath(any(IBinder.DeathRecipient.class), anyInt());
        mWifiScoreReport.setWifiConnectedNetworkScorer(mAppBinder, mWifiConnectedNetworkScorer,
                TEST_UID);
        verify(mExternalScoreUpdateObserverProxy, never()).registerCallback(any());
        verify(mAppBinder).linkToDeath(any(IBinder.DeathRecipient.class), anyInt());

        // Client should not get any message when scorer add failed.
        verify(mWifiConnectedNetworkScorer, never()).onSetScoreUpdateObserver(any());
    }

    /**
     * Verify netId to sessionId conversion.
     */
    @Test
    public void testSessionId() throws Exception {
        assertEquals(-1, WifiScoreReport.sessionIdFromNetId(Integer.MIN_VALUE));
        assertEquals(-1, WifiScoreReport.sessionIdFromNetId(-42));
        assertEquals(-1, WifiScoreReport.sessionIdFromNetId(-1));
        assertEquals(-1, WifiScoreReport.sessionIdFromNetId(0));
        assertEquals(18, WifiScoreReport.sessionIdFromNetId(1));
        assertEquals(3339, WifiScoreReport.sessionIdFromNetId(333));
        assertEquals(TEST_SESSION_ID, WifiScoreReport.sessionIdFromNetId(TEST_NETWORK_ID));
        int dangerOfOverflow = Integer.MAX_VALUE / 10;
        assertEquals(214748364, dangerOfOverflow);
        assertEquals(2147483646, WifiScoreReport.sessionIdFromNetId(dangerOfOverflow));
        assertEquals(8, WifiScoreReport.sessionIdFromNetId(dangerOfOverflow + 1));
        assertEquals(8, WifiScoreReport.sessionIdFromNetId(Integer.MAX_VALUE));
    }

    /**
     * Verify that client gets session ID when onStart() method is called.
     */
    @Test
    public void testClientGetSessionIdOnStart() throws Exception {
        // Register Client for verification.
        mWifiScoreReport.setWifiConnectedNetworkScorer(mAppBinder, mWifiConnectedNetworkScorer,
                TEST_UID);
        verify(mExternalScoreUpdateObserverProxy).registerCallback(any());
        when(mNetwork.getNetId()).thenReturn(TEST_NETWORK_ID);
        mWifiScoreReport.startConnectedNetworkScorer(TEST_NETWORK_ID, TEST_USER_SELECTED);
        verify(mWifiConnectedNetworkScorer).onStart(
                argThat(sessionInfo -> sessionInfo.getSessionId() == TEST_SESSION_ID
                        && sessionInfo.isUserSelected() == TEST_USER_SELECTED));
    }

    /**
     * Verify that onStart is called if there is already an active network when registered.
     */
    @Test
    public void testClientStartOnRegWhileActive() throws Exception {
        when(mNetwork.getNetId()).thenReturn(TEST_NETWORK_ID);
        mWifiScoreReport.startConnectedNetworkScorer(TEST_NETWORK_ID, TEST_USER_SELECTED);
        mWifiScoreReport.setWifiConnectedNetworkScorer(mAppBinder, mWifiConnectedNetworkScorer,
                TEST_UID);
        verify(mExternalScoreUpdateObserverProxy).registerCallback(any());
        verify(mWifiConnectedNetworkScorer).onStart(
                argThat(sessionInfo -> sessionInfo.getSessionId() == TEST_SESSION_ID
                        && sessionInfo.isUserSelected() == TEST_USER_SELECTED));
    }

    /**
     * Verify that client gets session ID when onStop() method is called.
     */
    @Test
    public void testClientGetSessionIdOnStop() throws Exception {
        // Register Client for verification.
        mWifiScoreReport.setWifiConnectedNetworkScorer(mAppBinder, mWifiConnectedNetworkScorer,
                TEST_UID);
        verify(mExternalScoreUpdateObserverProxy).registerCallback(any());
        when(mNetwork.getNetId()).thenReturn(TEST_NETWORK_ID);
        mWifiScoreReport.startConnectedNetworkScorer(TEST_NETWORK_ID, TEST_USER_SELECTED);
        verify(mWifiConnectedNetworkScorer).onStart(
                argThat(sessionInfo -> sessionInfo.getSessionId() == TEST_SESSION_ID
                        && sessionInfo.isUserSelected() == TEST_USER_SELECTED));
        mWifiScoreReport.stopConnectedNetworkScorer();
        verify(mWifiConnectedNetworkScorer).onStop(TEST_SESSION_ID);
        // After the session stops, it should not start again (without a new NetworkAgent)
        mWifiScoreReport.startConnectedNetworkScorer(TEST_NETWORK_ID, TEST_USER_SELECTED);
        verify(mWifiConnectedNetworkScorer).onStart(
                argThat(sessionInfo -> sessionInfo.getSessionId() == TEST_SESSION_ID
                        && sessionInfo.isUserSelected() == TEST_USER_SELECTED));
    }

    /**
     * Verify that only a single Wi-Fi connected network scorer can be registered successfully.
     */
    @Test
    public void verifyOnlyASingleScorerCanBeRegisteredSuccessively() throws Exception {
        WifiConnectedNetworkScorerImpl scorerImpl = new WifiConnectedNetworkScorerImpl();
        assertEquals(true, mWifiScoreReport.setWifiConnectedNetworkScorer(
                mAppBinder, scorerImpl, TEST_UID));
        verify(mExternalScoreUpdateObserverProxy).registerCallback(any());
        assertEquals(false, mWifiScoreReport.setWifiConnectedNetworkScorer(
                mAppBinder, scorerImpl, TEST_UID));
    }

    /**
     * Verify that WifiScoreReport gets updated score when notifyScoreUpdate() is called by apps.
     */
    @Test
    public void testFrameworkGetsUpdatesScore() throws Exception {
        assumeFalse(SdkLevel.isAtLeastS());
        // initially called once
        verifySentNetworkScore(60);
        WifiConnectedNetworkScorerImpl scorerImpl = new WifiConnectedNetworkScorerImpl();
        // Register Client for verification.
        mWifiScoreReport.setWifiConnectedNetworkScorer(mAppBinder, scorerImpl, TEST_UID);
        verify(mExternalScoreUpdateObserverProxy).registerCallback(
                mExternalScoreUpdateObserverCbCaptor.capture());
        when(mNetwork.getNetId()).thenReturn(TEST_NETWORK_ID);
        mWifiScoreReport.startConnectedNetworkScorer(TEST_NETWORK_ID, TEST_USER_SELECTED);

        assertEquals(TEST_SESSION_ID, scorerImpl.mSessionId);

        // Invalid session ID
        mExternalScoreUpdateObserverCbCaptor.getValue().notifyScoreUpdate(-1, 49);
        if (SdkLevel.isAtLeastS()) {
            NetworkScore score = mWifiScoreReport.getScore();
            assertEquals(getMaxScore(), score.getLegacyInt());
            if (mIsPrimary) assertTrue(score.isTransportPrimary());
            assertFalse(score.isExiting());
        } else {
            assertEquals(getMaxScore(), mWifiScoreReport.getLegacyIntScore());
        }

        // Incorrect session ID
        mExternalScoreUpdateObserverCbCaptor.getValue().notifyScoreUpdate(
                scorerImpl.mSessionId + 10, 49);
        if (SdkLevel.isAtLeastS()) {
            NetworkScore score = mWifiScoreReport.getScore();
            assertEquals(getMaxScore(), score.getLegacyInt());
            if (mIsPrimary) assertTrue(score.isTransportPrimary());
            assertFalse(score.isExiting());
        } else {
            assertEquals(getMaxScore(), mWifiScoreReport.getLegacyIntScore());
        }

        mExternalScoreUpdateObserverCbCaptor.getValue().notifyScoreUpdate(
                scorerImpl.mSessionId, 49);
        mLooper.dispatchAll();
        verifySentNetworkScore(49);
        if (SdkLevel.isAtLeastS()) {
            NetworkScore score = mWifiScoreReport.getScore();
            assertEquals(score.getLegacyInt(), 49);
            if (mIsPrimary) assertTrue(score.isTransportPrimary());
            assertTrue(score.isExiting());
        } else {
            assertEquals(49, mWifiScoreReport.getLegacyIntScore());
        }

        mExternalScoreUpdateObserverCbCaptor.getValue().notifyScoreUpdate(
                scorerImpl.mSessionId, 59);
        mLooper.dispatchAll();
        verifySentNetworkScore(59);
        if (SdkLevel.isAtLeastS()) {
            NetworkScore score = mWifiScoreReport.getScore();
            assertEquals(score.getLegacyInt(), 59);
            if (mIsPrimary) assertTrue(score.isTransportPrimary());
            assertFalse(score.isExiting());
        } else {
            assertEquals(59, mWifiScoreReport.getLegacyIntScore());
        }
    }

    /**
     * Verify that WifiScoreReport triggers an update of WifiUsabilityStatsEntry.
     */
    @Test
    public void testFrameworkTriggersUpdateOfWifiUsabilityStats() throws Exception {
        WifiConnectedNetworkScorerImpl scorerImpl = new WifiConnectedNetworkScorerImpl();
        // Register Client for verification.
        mWifiScoreReport.setWifiConnectedNetworkScorer(mAppBinder, scorerImpl, TEST_UID);
        verify(mExternalScoreUpdateObserverProxy).registerCallback(
                mExternalScoreUpdateObserverCbCaptor.capture());
        when(mNetwork.getNetId()).thenReturn(TEST_NETWORK_ID);
        mWifiScoreReport.startConnectedNetworkScorer(TEST_NETWORK_ID, TEST_USER_SELECTED);

        mExternalScoreUpdateObserverCbCaptor.getValue().triggerUpdateOfWifiUsabilityStats(
                scorerImpl.mSessionId);
        mLooper.dispatchAll();
        verify(mWifiNative).getWifiLinkLayerStats(TEST_IFACE_NAME);
        verify(mWifiNative).signalPoll(TEST_IFACE_NAME);
    }

    /**
     * Ask for nud when score from external scorer breaches
     */
    @Test
    public void askForNudCheckWhenExternalScoreBreaches() throws Exception {
        assumeFalse(SdkLevel.isAtLeastS());
        WifiConnectedNetworkScorerImpl scorerImpl = new WifiConnectedNetworkScorerImpl();
        // Register Client for verification.
        mWifiScoreReport.setWifiConnectedNetworkScorer(mAppBinder, scorerImpl, TEST_UID);
        verify(mExternalScoreUpdateObserverProxy).registerCallback(
                mExternalScoreUpdateObserverCbCaptor.capture());
        when(mNetwork.getNetId()).thenReturn(TEST_NETWORK_ID);
        mWifiScoreReport.startConnectedNetworkScorer(TEST_NETWORK_ID, TEST_USER_SELECTED);
        mClock.mStepMillis = 0;

        mClock.mWallClockMillis = 5001;
        mExternalScoreUpdateObserverCbCaptor.getValue().notifyScoreUpdate(
                scorerImpl.mSessionId, 49);
        mLooper.dispatchAll();
        assertTrue(mWifiScoreReport.shouldCheckIpLayer());
        assertEquals(1, mWifiScoreReport.getNudYes());
        mWifiScoreReport.noteIpCheck();

        mClock.mWallClockMillis = 10000;
        mExternalScoreUpdateObserverCbCaptor.getValue().notifyScoreUpdate(
                scorerImpl.mSessionId, 49);
        mLooper.dispatchAll();
        assertFalse(mWifiScoreReport.shouldCheckIpLayer());

        mClock.mWallClockMillis = 10001;
        mExternalScoreUpdateObserverCbCaptor.getValue().notifyScoreUpdate(
                scorerImpl.mSessionId, 49);
        mLooper.dispatchAll();
        assertTrue(mWifiScoreReport.shouldCheckIpLayer());
        assertEquals(2, mWifiScoreReport.getNudYes());
    }

    /**
     * Verify BSSID blocklist does not happen when score stays below threshold for less than the
     * minimum duration
     */
    @Test
    public void bssidBlockListDoesnotHappenWhenExitingIsLessThanMinDuration() throws Exception {
        WifiConnectedNetworkScorerImpl scorerImpl = new WifiConnectedNetworkScorerImpl();
        // Register Client for verification.
        mWifiScoreReport.setWifiConnectedNetworkScorer(mAppBinder, scorerImpl, TEST_UID);
        verify(mExternalScoreUpdateObserverProxy).registerCallback(
                mExternalScoreUpdateObserverCbCaptor.capture());
        when(mNetwork.getNetId()).thenReturn(TEST_NETWORK_ID);
        mWifiScoreReport.startConnectedNetworkScorer(TEST_NETWORK_ID, TEST_USER_SELECTED);
        mClock.mStepMillis = 0;

        mClock.mWallClockMillis = 10;
        mExternalScoreUpdateObserverCbCaptor.getValue().notifyScoreUpdate(
                scorerImpl.mSessionId, 49);
        mLooper.dispatchAll();
        mClock.mWallClockMillis = 29009;
        mExternalScoreUpdateObserverCbCaptor.getValue().notifyScoreUpdate(
                scorerImpl.mSessionId, 49);
        mLooper.dispatchAll();
        mWifiScoreReport.stopConnectedNetworkScorer();
        mLooper.dispatchAll();
        verify(mWifiBlocklistMonitor, never()).handleBssidConnectionFailure(any(), any(),
                anyInt(), anyInt());
    }

    /**
     * Verify BSSID blocklist happens when score stays below threshold for longer than the
     * minimum duration
     */
    @Test
    public void bssidBlockListHappensWhenExitingIsLongerThanMinDuration() throws Exception {
        assumeFalse(SdkLevel.isAtLeastS());
        WifiConnectedNetworkScorerImpl scorerImpl = new WifiConnectedNetworkScorerImpl();
        // Register Client for verification.
        mWifiScoreReport.setWifiConnectedNetworkScorer(mAppBinder, scorerImpl, TEST_UID);
        verify(mExternalScoreUpdateObserverProxy).registerCallback(
                mExternalScoreUpdateObserverCbCaptor.capture());
        when(mNetwork.getNetId()).thenReturn(TEST_NETWORK_ID);
        mWifiScoreReport.startConnectedNetworkScorer(TEST_NETWORK_ID, TEST_USER_SELECTED);
        mClock.mStepMillis = 0;

        mClock.mWallClockMillis = 10;
        mExternalScoreUpdateObserverCbCaptor.getValue().notifyScoreUpdate(
                scorerImpl.mSessionId, 49);
        mLooper.dispatchAll();
        mClock.mWallClockMillis = 29011;
        mExternalScoreUpdateObserverCbCaptor.getValue().notifyScoreUpdate(
                scorerImpl.mSessionId, 49);
        mLooper.dispatchAll();
        mWifiScoreReport.stopConnectedNetworkScorer();
        mLooper.dispatchAll();
        verify(mWifiBlocklistMonitor).handleBssidConnectionFailure(any(), any(),
                eq(WifiBlocklistMonitor.REASON_FRAMEWORK_DISCONNECT_CONNECTED_SCORE), anyInt());
    }

    /**
     * Verify BSSID blocklist does not happen when there is score flip flop
     */
    @Test
    public void bssidBlockListDoesnotHappenWhenExitingIsReset() throws Exception {
        WifiConnectedNetworkScorerImpl scorerImpl = new WifiConnectedNetworkScorerImpl();
        // Register Client for verification.
        mWifiScoreReport.setWifiConnectedNetworkScorer(mAppBinder, scorerImpl, TEST_UID);
        verify(mExternalScoreUpdateObserverProxy).registerCallback(
                mExternalScoreUpdateObserverCbCaptor.capture());
        when(mNetwork.getNetId()).thenReturn(TEST_NETWORK_ID);
        mWifiScoreReport.startConnectedNetworkScorer(TEST_NETWORK_ID, TEST_USER_SELECTED);
        mClock.mStepMillis = 0;

        mClock.mWallClockMillis = 10;
        mExternalScoreUpdateObserverCbCaptor.getValue().notifyScoreUpdate(
                scorerImpl.mSessionId, 49);
        mLooper.dispatchAll();
        mClock.mWallClockMillis = 15000;
        mExternalScoreUpdateObserverCbCaptor.getValue().notifyScoreUpdate(
                scorerImpl.mSessionId, 51);
        mLooper.dispatchAll();
        mClock.mWallClockMillis = 29011;
        mExternalScoreUpdateObserverCbCaptor.getValue().notifyScoreUpdate(
                scorerImpl.mSessionId, 49);
        mLooper.dispatchAll();
        mWifiScoreReport.stopConnectedNetworkScorer();
        mLooper.dispatchAll();
        verify(mWifiBlocklistMonitor, never()).handleBssidConnectionFailure(any(), any(),
                anyInt(), anyInt());
    }

    /**
     * Verify that the initial score value in WifiInfo is the max when onStart is called.
     */
    @Test
    public void testOnStartInitialScoreInWifiInfoIsMaxScore() throws Exception {
        when(mNetwork.getNetId()).thenReturn(TEST_NETWORK_ID);
        mWifiScoreReport.setWifiConnectedNetworkScorer(mAppBinder, mWifiConnectedNetworkScorer,
                TEST_UID);
        mWifiScoreReport.startConnectedNetworkScorer(TEST_NETWORK_ID, TEST_USER_SELECTED);
        assertEquals(getMaxScore(), mWifiInfo.getScore());
    }

    /**
     * Verify confirmation duration is not added when it is not enabled in config overlay by default
     */
    @Test
    public void confirmationDurationIsNotAddedWhenItIsNotEnabledInConfigOverlay() throws Exception {
        assumeFalse(SdkLevel.isAtLeastS());
        verifySentAnyNetworkScore();
        WifiConnectedNetworkScorerImpl scorerImpl = new WifiConnectedNetworkScorerImpl();
        // Register Client for verification.
        mWifiScoreReport.setWifiConnectedNetworkScorer(mAppBinder, scorerImpl, TEST_UID);
        verify(mExternalScoreUpdateObserverProxy).registerCallback(
                mExternalScoreUpdateObserverCbCaptor.capture());
        when(mNetwork.getNetId()).thenReturn(TEST_NETWORK_ID);
        mWifiScoreReport.startConnectedNetworkScorer(TEST_NETWORK_ID, TEST_USER_SELECTED);
        mClock.mStepMillis = 0;

        mClock.mWallClockMillis = 10;
        mWifiInfo.setRssi(-65);
        mExternalScoreUpdateObserverCbCaptor.getValue().notifyScoreUpdate(
                scorerImpl.mSessionId, 49);
        mLooper.dispatchAll();
        verifySentAnyNetworkScore(times(2));
    }

    /**
     * Verify confirmation duration is not added when there is no score breach
     */
    @Test
    public void confirmationDurationIsNotAddedWhenThereIsNoScoreBreach() throws Exception {
        assumeFalse(SdkLevel.isAtLeastS());
        // initially sent score = 60
        verifySentNetworkScore(60);
        WifiConnectedNetworkScorerImpl scorerImpl = new WifiConnectedNetworkScorerImpl();
        // Register Client for verification.
        mWifiScoreReport.setWifiConnectedNetworkScorer(mAppBinder, scorerImpl, TEST_UID);
        verify(mExternalScoreUpdateObserverProxy).registerCallback(
                mExternalScoreUpdateObserverCbCaptor.capture());
        when(mNetwork.getNetId()).thenReturn(TEST_NETWORK_ID);
        mWifiScoreReport.startConnectedNetworkScorer(TEST_NETWORK_ID, TEST_USER_SELECTED);
        mClock.mStepMillis = 0;
        when(mContext.getResources().getBoolean(
            R.bool.config_wifiMinConfirmationDurationSendNetworkScoreEnabled)).thenReturn(true);

        mClock.mWallClockMillis = 10;
        mExternalScoreUpdateObserverCbCaptor.getValue().notifyScoreUpdate(
                scorerImpl.mSessionId, 60);
        mWifiInfo.setRssi(-70);
        mLooper.dispatchAll();
        verifySentNetworkScore(60, times(2));
        mClock.mWallClockMillis = 3010;
        mWifiInfo.setRssi(-65);
        mExternalScoreUpdateObserverCbCaptor.getValue().notifyScoreUpdate(
                scorerImpl.mSessionId, 59);
        mLooper.dispatchAll();
        verifySentNetworkScore(59);
        mClock.mWallClockMillis = 6010;
        mExternalScoreUpdateObserverCbCaptor.getValue().notifyScoreUpdate(
                scorerImpl.mSessionId, 58);
        mLooper.dispatchAll();
        verifySentNetworkScore(58);
    }

    /**
     * Verify confirmation duration and RSSI check is added for reporting low score when it is
     * enabled in config overlay
     */
    @Test
    public void confirmationDurationAndRssiCheckIsAddedForSendingLowScore() throws Exception {
        assumeFalse(SdkLevel.isAtLeastS());
        // initially called once
        verifySentAnyNetworkScore();
        WifiConnectedNetworkScorerImpl scorerImpl = new WifiConnectedNetworkScorerImpl();
        // Register Client for verification.
        mWifiScoreReport.setWifiConnectedNetworkScorer(mAppBinder, scorerImpl, TEST_UID);
        verify(mExternalScoreUpdateObserverProxy).registerCallback(
                mExternalScoreUpdateObserverCbCaptor.capture());
        when(mNetwork.getNetId()).thenReturn(TEST_NETWORK_ID);
        mWifiScoreReport.startConnectedNetworkScorer(TEST_NETWORK_ID, TEST_USER_SELECTED);
        mClock.mStepMillis = 0;
        when(mContext.getResources().getBoolean(
                R.bool.config_wifiMinConfirmationDurationSendNetworkScoreEnabled)).thenReturn(true);

        mClock.mWallClockMillis = 10;
        mExternalScoreUpdateObserverCbCaptor.getValue().notifyScoreUpdate(
                scorerImpl.mSessionId, 49);
        mLooper.dispatchAll();
        // still only called once
        verifySentAnyNetworkScore();
        mClock.mWallClockMillis = 10
                + mDeviceConfigFacade.DEFAULT_MIN_CONFIRMATION_DURATION_SEND_LOW_SCORE_MS - 1;
        mExternalScoreUpdateObserverCbCaptor.getValue().notifyScoreUpdate(
                scorerImpl.mSessionId, 48);
        mLooper.dispatchAll();
        // still only called once
        verifySentAnyNetworkScore();
        mClock.mWallClockMillis = 10
                + mDeviceConfigFacade.DEFAULT_MIN_CONFIRMATION_DURATION_SEND_LOW_SCORE_MS;
        mWifiInfo.setRssi(-65);
        mExternalScoreUpdateObserverCbCaptor.getValue().notifyScoreUpdate(
                scorerImpl.mSessionId, 47);
        mLooper.dispatchAll();
        verifySentNetworkScore(47, never());
        mClock.mWallClockMillis = 10
                + mDeviceConfigFacade.DEFAULT_MIN_CONFIRMATION_DURATION_SEND_LOW_SCORE_MS + 3000;
        mWifiInfo.setRssi(-68);
        mExternalScoreUpdateObserverCbCaptor.getValue().notifyScoreUpdate(
                scorerImpl.mSessionId, 46);
        mLooper.dispatchAll();
        verifySentNetworkScore(46);
    }

    /**
     * Verify confirmation duration is not added for reporting high score with default zero value
     */
    @Test
    public void confirmationDurationIsNotAddedForSendingHighScore() throws Exception {
        assumeFalse(SdkLevel.isAtLeastS());
        // initially called once
        verifySentAnyNetworkScore();
        WifiConnectedNetworkScorerImpl scorerImpl = new WifiConnectedNetworkScorerImpl();
        // Register Client for verification.
        mWifiScoreReport.setWifiConnectedNetworkScorer(mAppBinder, scorerImpl, TEST_UID);
        verify(mExternalScoreUpdateObserverProxy).registerCallback(
                mExternalScoreUpdateObserverCbCaptor.capture());
        when(mNetwork.getNetId()).thenReturn(TEST_NETWORK_ID);
        mWifiScoreReport.startConnectedNetworkScorer(TEST_NETWORK_ID, TEST_USER_SELECTED);
        mClock.mStepMillis = 0;
        when(mContext.getResources().getBoolean(
                R.bool.config_wifiMinConfirmationDurationSendNetworkScoreEnabled)).thenReturn(true);

        mClock.mWallClockMillis = 10;
        mExternalScoreUpdateObserverCbCaptor.getValue().notifyScoreUpdate(
                scorerImpl.mSessionId, 49);
        mLooper.dispatchAll();
        // still only called once
        verifySentAnyNetworkScore();
        mClock.mWallClockMillis = 3000;
        mWifiInfo.setRssi(-70);
        mExternalScoreUpdateObserverCbCaptor.getValue().notifyScoreUpdate(
                scorerImpl.mSessionId, 51);
        mLooper.dispatchAll();
        verifySentNetworkScore(51);
        mClock.mWallClockMillis = 6000;
        mExternalScoreUpdateObserverCbCaptor.getValue().notifyScoreUpdate(
                scorerImpl.mSessionId, 52);
        mLooper.dispatchAll();
        verifySentNetworkScore(52);

    }

    /**
     * Verify confirmation duration is added for reporting high score with non-zero value
     */
    @Test
    public void confirmationDurationIsAddedForSendingHighScore() throws Exception {
        assumeFalse(SdkLevel.isAtLeastS());
        // initially called once
        verifySentAnyNetworkScore();
        WifiConnectedNetworkScorerImpl scorerImpl = new WifiConnectedNetworkScorerImpl();
        // Register Client for verification.
        mWifiScoreReport.setWifiConnectedNetworkScorer(mAppBinder, scorerImpl, TEST_UID);
        verify(mExternalScoreUpdateObserverProxy).registerCallback(
                mExternalScoreUpdateObserverCbCaptor.capture());
        when(mNetwork.getNetId()).thenReturn(TEST_NETWORK_ID);
        mWifiScoreReport.startConnectedNetworkScorer(TEST_NETWORK_ID, TEST_USER_SELECTED);
        mClock.mStepMillis = 0;
        when(mContext.getResources().getBoolean(
                R.bool.config_wifiMinConfirmationDurationSendNetworkScoreEnabled)).thenReturn(true);
        when(mDeviceConfigFacade.getMinConfirmationDurationSendHighScoreMs()).thenReturn(4000);

        mClock.mWallClockMillis = 10;
        mExternalScoreUpdateObserverCbCaptor.getValue().notifyScoreUpdate(
                scorerImpl.mSessionId, 49);
        mLooper.dispatchAll();
        // still only called once
        verifySentAnyNetworkScore();
        mClock.mWallClockMillis = 3000;
        mExternalScoreUpdateObserverCbCaptor.getValue().notifyScoreUpdate(
                scorerImpl.mSessionId, 51);
        mLooper.dispatchAll();
        // still only called once
        verifySentAnyNetworkScore();
        mClock.mWallClockMillis = 6999;
        mExternalScoreUpdateObserverCbCaptor.getValue().notifyScoreUpdate(
                scorerImpl.mSessionId, 52);
        mLooper.dispatchAll();
        // still only called once
        verifySentAnyNetworkScore();
        mClock.mWallClockMillis = 7000;
        mExternalScoreUpdateObserverCbCaptor.getValue().notifyScoreUpdate(
                scorerImpl.mSessionId, 53);
        mLooper.dispatchAll();
        verifySentNetworkScore(53);
    }

    /**
     * Verify NUD check is not recommended and the score of 51 is sent to connectivity service
     * when adaptive connectivity is disabled for AOSP scorer.
     */
    @Test
    public void verifyNudCheckAndScoreIfToggleOffForAospScorer() throws Exception {
        // initially called once
        verifySentAnyNetworkScore();

        mWifiInfo.setFrequency(5220);
        mWifiInfo.setRssi(-85);
        when(mAdaptiveConnectivityEnabledSettingObserver.get()).thenReturn(false);
        mWifiScoreReport.calculateAndReportScore();
        assertFalse(mWifiScoreReport.shouldCheckIpLayer());

        if (SdkLevel.isAtLeastS()) {
            ArgumentCaptor<NetworkScore> scoreCaptor = ArgumentCaptor.forClass(NetworkScore.class);
            verify(mNetworkAgent, times(2)).sendNetworkScore(scoreCaptor.capture());
            NetworkScore ns = scoreCaptor.getValue();
            assertEquals(51, ns.getLegacyInt());
            assertFalse(ns.isExiting());
            if (mIsPrimary) assertTrue(ns.isTransportPrimary());
        } else {
            verify(mNetworkAgent).sendNetworkScore(51);
        }
    }

    /**
     * Verify NUD check is not recommended and the score of 51 is sent to connectivity service
     * when Wifi scoring is disabled.
     */
    @Test
    public void verifyNudCheckAndScoreIfScoringDisabledForAospScorer() throws Exception {
        // initially called once
        verifySentAnyNetworkScore();
        mWifiInfo.setFrequency(5220);
        mWifiInfo.setRssi(-85);
        when(mWifiSettingsStore.isWifiScoringEnabled()).thenReturn(false);
        mWifiScoreReport.calculateAndReportScore();
        assertFalse(mWifiScoreReport.shouldCheckIpLayer());

        if (SdkLevel.isAtLeastS()) {
            ArgumentCaptor<NetworkScore> scoreCaptor = ArgumentCaptor.forClass(NetworkScore.class);
            verify(mNetworkAgent, times(2)).sendNetworkScore(scoreCaptor.capture());
            NetworkScore ns = scoreCaptor.getValue();
            assertEquals(51, ns.getLegacyInt());
            assertFalse(ns.isExiting());
            if (mIsPrimary) assertTrue(ns.isTransportPrimary());
        } else {
            verify(mNetworkAgent).sendNetworkScore(51);
        }
    }

    /**
     * Verify NUD check is not recommended and the score of 51 is sent to connectivity service
     * when adaptive connectivity is disabled for external Wi-Fi scorer.
     */
    @Test
    public void verifyNudCheckAndScoreIfToggleOffForExternalScorer() throws Exception {
        assumeFalse(SdkLevel.isAtLeastS());
        // initially called once
        verifySentAnyNetworkScore();
        WifiConnectedNetworkScorerImpl scorerImpl = new WifiConnectedNetworkScorerImpl();
        // Register Client for verification.
        mWifiScoreReport.setWifiConnectedNetworkScorer(mAppBinder, scorerImpl, TEST_UID);
        verify(mExternalScoreUpdateObserverProxy).registerCallback(
                mExternalScoreUpdateObserverCbCaptor.capture());
        when(mNetwork.getNetId()).thenReturn(TEST_NETWORK_ID);
        mWifiScoreReport.startConnectedNetworkScorer(TEST_NETWORK_ID, TEST_USER_SELECTED);
        mClock.mStepMillis = 0;
        when(mContext.getResources().getBoolean(
                R.bool.config_wifiMinConfirmationDurationSendNetworkScoreEnabled)).thenReturn(true);
        when(mDeviceConfigFacade.getMinConfirmationDurationSendHighScoreMs()).thenReturn(4000);

        when(mAdaptiveConnectivityEnabledSettingObserver.get()).thenReturn(false);

        mClock.mWallClockMillis = 10;
        mExternalScoreUpdateObserverCbCaptor.getValue().notifyScoreUpdate(
                scorerImpl.mSessionId, 49);
        mLooper.dispatchAll();
        // still only called once
        verifySentAnyNetworkScore();
        assertFalse(mWifiScoreReport.shouldCheckIpLayer());

        mClock.mWallClockMillis = 10
                + mDeviceConfigFacade.DEFAULT_MIN_CONFIRMATION_DURATION_SEND_LOW_SCORE_MS - 1;
        mExternalScoreUpdateObserverCbCaptor.getValue().notifyScoreUpdate(
                scorerImpl.mSessionId, 48);
        mLooper.dispatchAll();
        // still only called once
        verifySentAnyNetworkScore();
        mClock.mWallClockMillis = 10
                + mDeviceConfigFacade.DEFAULT_MIN_CONFIRMATION_DURATION_SEND_LOW_SCORE_MS;
        mExternalScoreUpdateObserverCbCaptor.getValue().notifyScoreUpdate(
                scorerImpl.mSessionId, 47);
        mLooper.dispatchAll();
        verifySentNetworkScore(51);
    }

    /**
     * Verify NUD check is not recommended and the score of 51 is sent to connectivity service
     * when Wifi scoring is disabled for external Wi-Fi scorer.
     */
    @Test
    public void verifyNudCheckAndScoreIfScoringDisabledForExternalScorer() throws Exception {
        assumeFalse(SdkLevel.isAtLeastS());
        WifiConnectedNetworkScorerImpl scorerImpl = new WifiConnectedNetworkScorerImpl();
        // Register Client for verification.
        mWifiScoreReport.setWifiConnectedNetworkScorer(mAppBinder, scorerImpl, TEST_UID);
        verify(mExternalScoreUpdateObserverProxy).registerCallback(
                mExternalScoreUpdateObserverCbCaptor.capture());
        when(mNetwork.getNetId()).thenReturn(TEST_NETWORK_ID);
        mWifiScoreReport.startConnectedNetworkScorer(TEST_NETWORK_ID, TEST_USER_SELECTED);
        when(mWifiSettingsStore.isWifiScoringEnabled()).thenReturn(false);

        mExternalScoreUpdateObserverCbCaptor.getValue().notifyScoreUpdate(
                scorerImpl.mSessionId, 47);
        mLooper.dispatchAll();
        assertFalse(mWifiScoreReport.shouldCheckIpLayer());
        verifySentNetworkScore(51);
    }

    /**
     * Verify that WifiScoreReport gets updated score when notifyStoreUpdate() is called by apps.
     */
    @Test
    public void testFrameworkGetsNotifiedOfUpdatedScore() throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());
        WifiConnectedNetworkScorerImpl scorerImpl = new WifiConnectedNetworkScorerImpl();
        // Register Client for verification.
        mWifiScoreReport.setWifiConnectedNetworkScorer(mAppBinder, scorerImpl, TEST_UID);
        verify(mExternalScoreUpdateObserverProxy).registerCallback(
                mExternalScoreUpdateObserverCbCaptor.capture());
        when(mNetwork.getNetId()).thenReturn(TEST_NETWORK_ID);
        mWifiScoreReport.startConnectedNetworkScorer(TEST_NETWORK_ID, TEST_USER_SELECTED);

        mExternalScoreUpdateObserverCbCaptor.getValue().notifyScoreUpdate(
                scorerImpl.mSessionId, 59);
        mLooper.dispatchAll();
        verify(mWifiMetrics).incrementWifiScoreCount(eq(TEST_IFACE_NAME), anyInt());
    }

    /**
     * Verify that WifiScoreReport gets updated status when notifyStatusUpdate() is called by apps.
     */
    @Test
    public void testFrameworkGetsNotifiedOfUpdatedStatus() throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());
        // initially called once
        verify(mNetworkAgent).sendNetworkScore(any());
        assertTrue(mWifiInfo.isUsable());
        WifiConnectedNetworkScorerImpl scorerImpl = new WifiConnectedNetworkScorerImpl();
        // Register Client for verification.
        mWifiScoreReport.setWifiConnectedNetworkScorer(mAppBinder, scorerImpl, TEST_UID);
        verify(mExternalScoreUpdateObserverProxy).registerCallback(
                mExternalScoreUpdateObserverCbCaptor.capture());
        when(mNetwork.getNetId()).thenReturn(TEST_NETWORK_ID);
        mWifiScoreReport.startConnectedNetworkScorer(TEST_NETWORK_ID, TEST_USER_SELECTED);

        mExternalScoreUpdateObserverCbCaptor.getValue().notifyStatusUpdate(
                scorerImpl.mSessionId, true);
        mLooper.dispatchAll();

        {
            ArgumentCaptor<NetworkScore> scoreCaptor = ArgumentCaptor.forClass(NetworkScore.class);
            verify(mNetworkAgent, times(2)).sendNetworkScore(scoreCaptor.capture());
            assertTrue(mWifiInfo.isUsable());
            NetworkScore ns = scoreCaptor.getValue();
            assertEquals(60, ns.getLegacyInt());
            assertFalse(ns.isExiting());
            if (mIsPrimary) assertTrue(ns.isTransportPrimary());
        }

        mExternalScoreUpdateObserverCbCaptor.getValue().notifyStatusUpdate(
                scorerImpl.mSessionId, false);
        mLooper.dispatchAll();

        {
            ArgumentCaptor<NetworkScore> scoreCaptor = ArgumentCaptor.forClass(NetworkScore.class);
            verify(mNetworkAgent, times(3)).sendNetworkScore(scoreCaptor.capture());
            assertFalse(mWifiInfo.isUsable());
            NetworkScore ns = scoreCaptor.getValue();
            assertEquals(60, ns.getLegacyInt());
            assertTrue(ns.isExiting());
            if (mIsPrimary) assertTrue(ns.isTransportPrimary());
        }

        // Not usable -> Usable should disable the network switch dialog for the specified duration.
        mExternalScoreUpdateObserverCbCaptor.getValue().notifyStatusUpdate(
                scorerImpl.mSessionId, true);
        mLooper.dispatchAll();
        verify(mWifiConnectivityManager).disableNetworkSwitchDialog(
                TEST_NETWORK_SWITCH_DIALOG_DISABLED_MS);
    }

    /**
     * Verify that WifiScoreReport gets NUD request only once when requestNudOperation() is called
     * by apps.
     */
    @Test
    public void testFrameworkGetsNotifiedOfRequestedNudOperation() throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());
        WifiConnectedNetworkScorerImpl scorerImpl = new WifiConnectedNetworkScorerImpl();
        // Register Client for verification.
        mWifiScoreReport.setWifiConnectedNetworkScorer(mAppBinder, scorerImpl, TEST_UID);
        verify(mExternalScoreUpdateObserverProxy).registerCallback(
                mExternalScoreUpdateObserverCbCaptor.capture());
        when(mNetwork.getNetId()).thenReturn(TEST_NETWORK_ID);
        mClock.mStepMillis = 0;
        mWifiScoreReport.startConnectedNetworkScorer(TEST_NETWORK_ID, TEST_USER_SELECTED);

        mClock.mWallClockMillis = 5001;
        mExternalScoreUpdateObserverCbCaptor.getValue().requestNudOperation(scorerImpl.mSessionId);
        mLooper.dispatchAll();
        assertTrue(mWifiScoreReport.shouldCheckIpLayer());
        assertEquals(1, mWifiScoreReport.getNudYes());
        mWifiScoreReport.noteIpCheck();
        // Assert NUD is triggered only once by one request.
        assertFalse(mWifiScoreReport.shouldCheckIpLayer());

        mClock.mWallClockMillis = 10000;
        mExternalScoreUpdateObserverCbCaptor.getValue().requestNudOperation(scorerImpl.mSessionId);
        mLooper.dispatchAll();
        assertFalse(mWifiScoreReport.shouldCheckIpLayer());
    }

    /**
     * Verify that blocklisting happens when blocklistCurrentBssid() is called by apps.
     */
    @Test
    public void testFrameworkGetsBlocklistCurrentBssidOperation() throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());
        WifiConnectedNetworkScorerImpl scorerImpl = new WifiConnectedNetworkScorerImpl();
        // Register Client for verification.
        mWifiScoreReport.setWifiConnectedNetworkScorer(mAppBinder, scorerImpl, TEST_UID);
        verify(mExternalScoreUpdateObserverProxy).registerCallback(
                mExternalScoreUpdateObserverCbCaptor.capture());
        when(mNetwork.getNetId()).thenReturn(TEST_NETWORK_ID);
        mWifiScoreReport.startConnectedNetworkScorer(TEST_NETWORK_ID, TEST_USER_SELECTED);

        mExternalScoreUpdateObserverCbCaptor.getValue().blocklistCurrentBssid(
                scorerImpl.mSessionId);
        mLooper.dispatchAll();
        verify(mWifiBlocklistMonitor).handleBssidConnectionFailure(any(), any(),
                eq(WifiBlocklistMonitor.REASON_FRAMEWORK_DISCONNECT_CONNECTED_SCORE), anyInt());
    }

    @Test
    public void testClientNotNotifiedForLocalOnlyConnection() throws Exception {
        when(mNetworkAgent.getCurrentNetworkCapabilities()).thenReturn(
                new NetworkCapabilities.Builder()
                        // no internet
                        .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                        .build());
        // Register Client for verification.
        mWifiScoreReport.setWifiConnectedNetworkScorer(mAppBinder, mWifiConnectedNetworkScorer,
                TEST_UID);
        verify(mExternalScoreUpdateObserverProxy).registerCallback(
                mExternalScoreUpdateObserverCbCaptor.capture());
        when(mNetwork.getNetId()).thenReturn(TEST_NETWORK_ID);
        mWifiScoreReport.startConnectedNetworkScorer(TEST_NETWORK_ID, TEST_USER_SELECTED);
        verify(mWifiConnectedNetworkScorer, never()).onStart(any());
    }

    @Test
    public void testClientNotNotifiedForOemPaidConnection() throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());
        when(mNetworkAgent.getCurrentNetworkCapabilities()).thenReturn(
                new NetworkCapabilities.Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        // oem paid
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_OEM_PAID)
                        .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                        .build());
        // Register Client for verification.
        mWifiScoreReport.setWifiConnectedNetworkScorer(mAppBinder, mWifiConnectedNetworkScorer,
                TEST_UID);
        verify(mExternalScoreUpdateObserverProxy).registerCallback(
                mExternalScoreUpdateObserverCbCaptor.capture());
        when(mNetwork.getNetId()).thenReturn(TEST_NETWORK_ID);
        mWifiScoreReport.startConnectedNetworkScorer(TEST_NETWORK_ID, TEST_USER_SELECTED);
        verify(mWifiConnectedNetworkScorer, never()).onStart(any());
    }

    @Test
    public void testClientNotNotifiedForOemPrivateConnection() throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());
        when(mNetworkAgent.getCurrentNetworkCapabilities()).thenReturn(
                new NetworkCapabilities.Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        // oem private
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_OEM_PRIVATE)
                        .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                        .build());
        // Register Client for verification.
        mWifiScoreReport.setWifiConnectedNetworkScorer(mAppBinder, mWifiConnectedNetworkScorer,
                TEST_UID);
        verify(mExternalScoreUpdateObserverProxy).registerCallback(
                mExternalScoreUpdateObserverCbCaptor.capture());
        when(mNetwork.getNetId()).thenReturn(TEST_NETWORK_ID);
        mWifiScoreReport.startConnectedNetworkScorer(TEST_NETWORK_ID, TEST_USER_SELECTED);
        verify(mWifiConnectedNetworkScorer, never()).onStart(any());
    }

    /**
     * Verify BSSID is added onto blocklist when the score value of -2 is sent from external Wi-Fi
     * scorer.
     */
    @Test
    public void verifyBssidBlocklistWithScoreValueOfMinus2() throws Exception {
        assumeFalse(SdkLevel.isAtLeastS());
        WifiConnectedNetworkScorerImpl scorerImpl = new WifiConnectedNetworkScorerImpl();
        // Register Client for verification.
        mWifiScoreReport.setWifiConnectedNetworkScorer(mAppBinder, scorerImpl, TEST_UID);
        verify(mExternalScoreUpdateObserverProxy).registerCallback(
                mExternalScoreUpdateObserverCbCaptor.capture());
        when(mNetwork.getNetId()).thenReturn(TEST_NETWORK_ID);
        mWifiScoreReport.startConnectedNetworkScorer(TEST_NETWORK_ID, TEST_USER_SELECTED);
        assertEquals(TEST_SESSION_ID, scorerImpl.mSessionId);
        mExternalScoreUpdateObserverCbCaptor.getValue()
                .notifyScoreUpdate(scorerImpl.mSessionId, -2);
        mClock.mStepMillis = 0;
        mClock.mWallClockMillis = 10;
        mWifiInfo.setRssi(-65);
        mLooper.dispatchAll();
        verify(mWifiBlocklistMonitor).handleBssidConnectionFailure(any(), any(),
                eq(WifiBlocklistMonitor.REASON_FRAMEWORK_DISCONNECT_CONNECTED_SCORE), anyInt());
    }
}
