/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.server.connectivity;

import static android.net.NetworkCapabilities.TRANSPORT_CELLULAR;
import static android.net.NetworkCapabilities.TRANSPORT_WIFI;

import static com.android.testutils.DevSdkIgnoreRule.IgnoreAfter;
import static com.android.testutils.DevSdkIgnoreRule.IgnoreUpTo;
import static com.android.testutils.HandlerUtils.visibleOnHandlerThread;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.TelephonyNetworkSpecifier;
import android.net.wifi.WifiInfo;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.SubscriptionManager.OnSubscriptionsChangedListener;
import android.telephony.TelephonyManager;

import androidx.test.filters.SmallTest;

import com.android.metrics.DailykeepaliveInfoReported;
import com.android.metrics.DurationForNumOfKeepalive;
import com.android.metrics.DurationPerNumOfKeepalive;
import com.android.metrics.KeepaliveLifetimeForCarrier;
import com.android.metrics.KeepaliveLifetimePerCarrier;
import com.android.modules.utils.BackgroundThread;
import com.android.net.module.util.CollectionUtils;
import com.android.testutils.DevSdkIgnoreRule;
import com.android.testutils.DevSdkIgnoreRunner;
import com.android.testutils.HandlerUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@RunWith(DevSdkIgnoreRunner.class)
@SmallTest
@DevSdkIgnoreRule.IgnoreUpTo(Build.VERSION_CODES.TIRAMISU)
public class KeepaliveStatsTrackerTest {
    private static final int TIMEOUT_MS = 30_000;

    private static final int TEST_SLOT = 1;
    private static final int TEST_SLOT2 = 2;
    private static final int TEST_KEEPALIVE_INTERVAL_SEC = 10;
    private static final int TEST_KEEPALIVE_INTERVAL2_SEC = 20;
    private static final int TEST_SUB_ID_1 = 1;
    private static final int TEST_SUB_ID_2 = 2;
    private static final int TEST_CARRIER_ID_1 = 135;
    private static final int TEST_CARRIER_ID_2 = 246;
    private static final Network TEST_NETWORK = new Network(123);
    private static final NetworkCapabilities TEST_NETWORK_CAPABILITIES =
            buildCellNetworkCapabilitiesWithSubId(TEST_SUB_ID_1);
    private static final NetworkCapabilities TEST_NETWORK_CAPABILITIES_2 =
            buildCellNetworkCapabilitiesWithSubId(TEST_SUB_ID_2);
    private static final int TEST_UID = 1234;

    private static NetworkCapabilities buildCellNetworkCapabilitiesWithSubId(int subId) {
        final TelephonyNetworkSpecifier telephonyNetworkSpecifier =
                new TelephonyNetworkSpecifier.Builder().setSubscriptionId(subId).build();
        return new NetworkCapabilities.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                .setNetworkSpecifier(telephonyNetworkSpecifier)
                .build();
    }

    @Rule public final DevSdkIgnoreRule ignoreRule = new DevSdkIgnoreRule();

    private HandlerThread mHandlerThread;
    private Handler mTestHandler;

    private KeepaliveStatsTracker mKeepaliveStatsTracker;

    @Mock private Context mContext;
    @Mock private KeepaliveStatsTracker.Dependencies mDependencies;
    @Mock private SubscriptionManager mSubscriptionManager;

    private BroadcastReceiver getBroadcastReceiver() {
        final ArgumentCaptor<BroadcastReceiver> receiverCaptor =
                ArgumentCaptor.forClass(BroadcastReceiver.class);
        verify(mContext).registerReceiver(
                receiverCaptor.capture(),
                argThat(intentFilter -> intentFilter.matchAction(
                        SubscriptionManager.ACTION_DEFAULT_SUBSCRIPTION_CHANGED)),
                /* broadcastPermission= */ any(),
                eq(mTestHandler));

        return receiverCaptor.getValue();
    }

    private void triggerBroadcastDefaultSubId(int subId) {
        final Intent intent =
                new Intent(SubscriptionManager.ACTION_DEFAULT_SUBSCRIPTION_CHANGED);
        intent.putExtra(SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX, subId);

        getBroadcastReceiver().onReceive(mContext, intent);
    }

    private OnSubscriptionsChangedListener getOnSubscriptionsChangedListener() {
        final ArgumentCaptor<OnSubscriptionsChangedListener> listenerCaptor =
                ArgumentCaptor.forClass(OnSubscriptionsChangedListener.class);
        verify(mSubscriptionManager)
                .addOnSubscriptionsChangedListener(any(), listenerCaptor.capture());
        return listenerCaptor.getValue();
    }

    private static final class KeepaliveCarrierStats {
        public final int carrierId;
        public final int transportTypes;
        public final int intervalMs;
        public final int lifetimeMs;
        public final int activeLifetimeMs;

        KeepaliveCarrierStats(
                int carrierId,
                int transportTypes,
                int intervalMs,
                int lifetimeMs,
                int activeLifetimeMs) {
            this.carrierId = carrierId;
            this.transportTypes = transportTypes;
            this.intervalMs = intervalMs;
            this.lifetimeMs = lifetimeMs;
            this.activeLifetimeMs = activeLifetimeMs;
        }

        // Equals method on only the key, (carrierId, tranportTypes, intervalMs)
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            final KeepaliveCarrierStats that = (KeepaliveCarrierStats) o;

            return carrierId == that.carrierId && transportTypes == that.transportTypes
                    && intervalMs == that.intervalMs;
        }

        @Override
        public int hashCode() {
            return carrierId + 3 * transportTypes + 5 * intervalMs;
        }
    }

    // Use the default test carrier id, transportType and keepalive interval.
    private KeepaliveCarrierStats getDefaultCarrierStats(int lifetimeMs, int activeLifetimeMs) {
        return new KeepaliveCarrierStats(
                TEST_CARRIER_ID_1,
                /* transportTypes= */ (1 << TRANSPORT_CELLULAR),
                TEST_KEEPALIVE_INTERVAL_SEC * 1000,
                lifetimeMs,
                activeLifetimeMs);
    }

    private <T> void mockService(String serviceName, Class<T> serviceClass, T service) {
        doReturn(serviceName).when(mContext).getSystemServiceName(serviceClass);
        doReturn(service).when(mContext).getSystemService(serviceName);
        if (mContext.getSystemService(serviceClass) == null) {
            // Test is using mockito-extended
            doCallRealMethod().when(mContext).getSystemService(serviceClass);
        }
    }

    private SubscriptionInfo makeSubInfoMock(int subId, int carrierId) {
        final SubscriptionInfo subInfo = mock(SubscriptionInfo.class);
        doReturn(subId).when(subInfo).getSubscriptionId();
        doReturn(carrierId).when(subInfo).getCarrierId();
        return subInfo;
    }

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mockService(Context.TELEPHONY_SUBSCRIPTION_SERVICE, SubscriptionManager.class,
                mSubscriptionManager);

        final SubscriptionInfo subInfo1 = makeSubInfoMock(TEST_SUB_ID_1, TEST_CARRIER_ID_1);
        final SubscriptionInfo subInfo2 = makeSubInfoMock(TEST_SUB_ID_2, TEST_CARRIER_ID_2);

        doReturn(List.of(subInfo1, subInfo2))
                .when(mSubscriptionManager)
                .getActiveSubscriptionInfoList();

        mHandlerThread = new HandlerThread("KeepaliveStatsTrackerTest");
        mHandlerThread.start();
        mTestHandler = new Handler(mHandlerThread.getLooper());

        setElapsedRealtime(0);
        mKeepaliveStatsTracker = new KeepaliveStatsTracker(mContext, mTestHandler, mDependencies);
        HandlerUtils.waitForIdle(BackgroundThread.getHandler(), TIMEOUT_MS);

        // Initial onSubscriptionsChanged.
        getOnSubscriptionsChangedListener().onSubscriptionsChanged();
        HandlerUtils.waitForIdle(mTestHandler, TIMEOUT_MS);
    }

    @After
    public void tearDown() throws Exception {
        if (mHandlerThread != null) {
            mHandlerThread.quitSafely();
            mHandlerThread.join();
        }
    }

    private void setElapsedRealtime(long time) {
        doReturn(time).when(mDependencies).getElapsedRealtime();
    }

    private DailykeepaliveInfoReported buildKeepaliveMetrics(long time) {
        setElapsedRealtime(time);

        return visibleOnHandlerThread(
                mTestHandler, () -> mKeepaliveStatsTracker.buildKeepaliveMetrics());
    }

    private DailykeepaliveInfoReported buildAndResetMetrics(long time) {
        setElapsedRealtime(time);

        return visibleOnHandlerThread(
                mTestHandler, () -> mKeepaliveStatsTracker.buildAndResetMetrics());
    }

    private void onStartKeepalive(long time, int slot) {
        onStartKeepalive(time, slot, TEST_KEEPALIVE_INTERVAL_SEC);
    }

    private void onStartKeepalive(long time, int slot, int intervalSeconds) {
        onStartKeepalive(time, slot, TEST_NETWORK_CAPABILITIES, intervalSeconds);
    }

    private void onStartKeepalive(long time, int slot, NetworkCapabilities nc) {
        onStartKeepalive(time, slot, nc, TEST_KEEPALIVE_INTERVAL_SEC);
    }

    private void onStartKeepalive(
            long time, int slot, NetworkCapabilities nc, int intervalSeconds) {
        onStartKeepalive(time, slot, nc, intervalSeconds, TEST_UID, /* isAutoKeepalive= */ true);
    }

    private void onStartKeepalive(long time, int slot, NetworkCapabilities nc, int intervalSeconds,
            int uid, boolean isAutoKeepalive) {
        setElapsedRealtime(time);
        visibleOnHandlerThread(mTestHandler, () ->
                mKeepaliveStatsTracker.onStartKeepalive(TEST_NETWORK, slot, nc, intervalSeconds,
                        uid, isAutoKeepalive));
    }

    private void onPauseKeepalive(long time, int slot) {
        setElapsedRealtime(time);
        visibleOnHandlerThread(
                mTestHandler, () -> mKeepaliveStatsTracker.onPauseKeepalive(TEST_NETWORK, slot));
    }

    private void onResumeKeepalive(long time, int slot) {
        setElapsedRealtime(time);
        visibleOnHandlerThread(
                mTestHandler, () -> mKeepaliveStatsTracker.onResumeKeepalive(TEST_NETWORK, slot));
    }

    private void onStopKeepalive(long time, int slot) {
        setElapsedRealtime(time);
        visibleOnHandlerThread(
                mTestHandler, () -> mKeepaliveStatsTracker.onStopKeepalive(TEST_NETWORK, slot));
    }

    @Test
    public void testEnsureRunningOnHandlerThread() {
        // Not running on handler thread
        assertThrows(
                IllegalStateException.class,
                () -> mKeepaliveStatsTracker.onStartKeepalive(
                        TEST_NETWORK,
                        TEST_SLOT,
                        TEST_NETWORK_CAPABILITIES,
                        TEST_KEEPALIVE_INTERVAL_SEC,
                        TEST_UID,
                        /* isAutoKeepalive */ true));
        assertThrows(
                IllegalStateException.class,
                () -> mKeepaliveStatsTracker.onPauseKeepalive(TEST_NETWORK, TEST_SLOT));
        assertThrows(
                IllegalStateException.class,
                () -> mKeepaliveStatsTracker.onResumeKeepalive(TEST_NETWORK, TEST_SLOT));
        assertThrows(
                IllegalStateException.class,
                () -> mKeepaliveStatsTracker.onStopKeepalive(TEST_NETWORK, TEST_SLOT));
        assertThrows(
                IllegalStateException.class, () -> mKeepaliveStatsTracker.buildKeepaliveMetrics());
        assertThrows(
                IllegalStateException.class, () -> mKeepaliveStatsTracker.buildAndResetMetrics());
    }

    /**
     * Asserts that a DurationPerNumOfKeepalive contains expected values
     *
     * @param expectRegisteredDurations integer array where the index is the number of concurrent
     *     keepalives and the value is the expected duration of time that the tracker is in a state
     *     with the given number of keepalives registered.
     * @param expectActiveDurations integer array where the index is the number of concurrent
     *     keepalives and the value is the expected duration of time that the tracker is in a state
     *     with the given number of keepalives active.
     * @param actualDurationsPerNumOfKeepalive the DurationPerNumOfKeepalive message to assert.
     */
    private void assertDurationMetrics(
            int[] expectRegisteredDurations,
            int[] expectActiveDurations,
            DurationPerNumOfKeepalive actualDurationsPerNumOfKeepalive) {
        final int maxNumOfKeepalive = expectRegisteredDurations.length;
        assertEquals(maxNumOfKeepalive, expectActiveDurations.length);
        assertEquals(
                maxNumOfKeepalive,
                actualDurationsPerNumOfKeepalive.getDurationForNumOfKeepaliveCount());
        for (int numOfKeepalive = 0; numOfKeepalive < maxNumOfKeepalive; numOfKeepalive++) {
            final DurationForNumOfKeepalive actualDurations =
                    actualDurationsPerNumOfKeepalive.getDurationForNumOfKeepalive(numOfKeepalive);

            assertEquals(numOfKeepalive, actualDurations.getNumOfKeepalive());
            assertEquals(
                    expectRegisteredDurations[numOfKeepalive],
                    actualDurations.getKeepaliveRegisteredDurationsMsec());
            assertEquals(
                    expectActiveDurations[numOfKeepalive],
                    actualDurations.getKeepaliveActiveDurationsMsec());
        }
    }

    /**
     * Asserts the actual KeepaliveLifetimePerCarrier contains an expected KeepaliveCarrierStats.
     * This finds and checks only for the (carrierId, transportTypes, intervalMs) of the given
     * expectKeepaliveCarrierStats and asserts the lifetime metrics.
     *
     * @param expectKeepaliveCarrierStats a keepalive lifetime metric that is expected to be in the
     *     proto.
     * @param actualKeepaliveLifetimePerCarrier the KeepaliveLifetimePerCarrier message to assert.
     */
    private void findAndAssertCarrierLifetimeMetrics(
            KeepaliveCarrierStats expectKeepaliveCarrierStats,
            KeepaliveLifetimePerCarrier actualKeepaliveLifetimePerCarrier) {
        for (KeepaliveLifetimeForCarrier keepaliveLifetimeForCarrier :
                actualKeepaliveLifetimePerCarrier.getKeepaliveLifetimeForCarrierList()) {
            if (expectKeepaliveCarrierStats.carrierId == keepaliveLifetimeForCarrier.getCarrierId()
                    && expectKeepaliveCarrierStats.transportTypes
                            == keepaliveLifetimeForCarrier.getTransportTypes()
                    && expectKeepaliveCarrierStats.intervalMs
                            == keepaliveLifetimeForCarrier.getIntervalsMsec()) {
                assertEquals(
                        expectKeepaliveCarrierStats.lifetimeMs,
                        keepaliveLifetimeForCarrier.getLifetimeMsec());
                assertEquals(
                        expectKeepaliveCarrierStats.activeLifetimeMs,
                        keepaliveLifetimeForCarrier.getActiveLifetimeMsec());
                return;
            }
        }
        fail("KeepaliveLifetimeForCarrier not found for a given expected KeepaliveCarrierStats");
    }

    private void assertNoDuplicates(Object[] arr) {
        final Set<Object> s = new HashSet<Object>(Arrays.asList(arr));
        assertEquals(arr.length, s.size());
    }

    /**
     * Asserts that a KeepaliveLifetimePerCarrier contains all the expected KeepaliveCarrierStats.
     *
     * @param expectKeepaliveCarrierStatsArray an array of keepalive lifetime metrics that is
     *     expected to be in the KeepaliveLifetimePerCarrier.
     * @param actualKeepaliveLifetimePerCarrier the KeepaliveLifetimePerCarrier message to assert.
     */
    private void assertCarrierLifetimeMetrics(
            KeepaliveCarrierStats[] expectKeepaliveCarrierStatsArray,
            KeepaliveLifetimePerCarrier actualKeepaliveLifetimePerCarrier) {
        assertNoDuplicates(expectKeepaliveCarrierStatsArray);
        assertEquals(
                expectKeepaliveCarrierStatsArray.length,
                actualKeepaliveLifetimePerCarrier.getKeepaliveLifetimeForCarrierCount());
        for (KeepaliveCarrierStats keepaliveCarrierStats : expectKeepaliveCarrierStatsArray) {
            findAndAssertCarrierLifetimeMetrics(
                    keepaliveCarrierStats, actualKeepaliveLifetimePerCarrier);
        }
    }

    private void assertDailyKeepaliveInfoReported(
            DailykeepaliveInfoReported dailyKeepaliveInfoReported,
            int expectRequestsCount,
            int expectAutoRequestsCount,
            int[] expectAppUids,
            int[] expectRegisteredDurations,
            int[] expectActiveDurations,
            KeepaliveCarrierStats[] expectKeepaliveCarrierStatsArray) {
        assertEquals(expectRequestsCount, dailyKeepaliveInfoReported.getKeepaliveRequests());
        assertEquals(
                expectAutoRequestsCount,
                dailyKeepaliveInfoReported.getAutomaticKeepaliveRequests());
        assertEquals(expectAppUids.length, dailyKeepaliveInfoReported.getDistinctUserCount());

        final int[] uidArray = CollectionUtils.toIntArray(dailyKeepaliveInfoReported.getUidList());
        assertArrayEquals(expectAppUids, uidArray);

        final DurationPerNumOfKeepalive actualDurations =
                dailyKeepaliveInfoReported.getDurationPerNumOfKeepalive();
        assertDurationMetrics(expectRegisteredDurations, expectActiveDurations, actualDurations);

        final KeepaliveLifetimePerCarrier actualCarrierLifetime =
                dailyKeepaliveInfoReported.getKeepaliveLifetimePerCarrier();

        assertCarrierLifetimeMetrics(expectKeepaliveCarrierStatsArray, actualCarrierLifetime);
    }

    // The KeepaliveStatsTracker will be disabled when an error occurs with the keepalive states.
    // Most tests should assert that the tracker is still active to ensure no errors occurred.
    private void assertKeepaliveStatsTrackerActive() {
        assertTrue(mKeepaliveStatsTracker.isEnabled());
    }

    private void assertKeepaliveStatsTrackerDisabled() {
        assertFalse(mKeepaliveStatsTracker.isEnabled());

        final OnSubscriptionsChangedListener listener = getOnSubscriptionsChangedListener();
        // BackgroundThread will remove the OnSubscriptionsChangedListener.
        HandlerUtils.waitForIdle(BackgroundThread.getHandler(), TIMEOUT_MS);
        verify(mSubscriptionManager).removeOnSubscriptionsChangedListener(listener);

        final BroadcastReceiver receiver = getBroadcastReceiver();
        verify(mContext).unregisterReceiver(receiver);
    }

    @Test
    public void testNoKeepalive() {
        final int writeTime = 5000;

        final DailykeepaliveInfoReported dailyKeepaliveInfoReported =
                buildKeepaliveMetrics(writeTime);

        // Expect that the durations are all in numOfKeepalive = 0.
        final int[] expectRegisteredDurations = new int[] {writeTime};
        final int[] expectActiveDurations = new int[] {writeTime};

        assertDailyKeepaliveInfoReported(
                dailyKeepaliveInfoReported,
                /* expectRequestsCount= */ 0,
                /* expectAutoRequestsCount= */ 0,
                /* expectAppUids= */ new int[0],
                expectRegisteredDurations,
                expectActiveDurations,
                new KeepaliveCarrierStats[0]);
        assertKeepaliveStatsTrackerActive();
    }

    /*
     * Diagram of test (not to scale):
     * Key: S - Start/Stop, P - Pause, R - Resume, W - Write
     *
     * Keepalive     S                          W
     * Timeline  |------------------------------|
     */
    @Test
    public void testOneKeepalive_startOnly() {
        final int startTime = 1000;
        final int writeTime = 5000;

        onStartKeepalive(startTime, TEST_SLOT);

        final DailykeepaliveInfoReported dailyKeepaliveInfoReported =
                buildKeepaliveMetrics(writeTime);

        // The keepalive is never stopped, expect the duration for numberOfKeepalive of 1 to range
        // from startTime to writeTime.
        final int[] expectRegisteredDurations = new int[] {startTime, writeTime - startTime};
        final int[] expectActiveDurations = new int[] {startTime, writeTime - startTime};
        assertDailyKeepaliveInfoReported(
                dailyKeepaliveInfoReported,
                /* expectRequestsCount= */ 1,
                /* expectAutoRequestsCount= */ 1,
                /* expectAppUids= */ new int[] {TEST_UID},
                expectRegisteredDurations,
                expectActiveDurations,
                new KeepaliveCarrierStats[] {
                    getDefaultCarrierStats(expectRegisteredDurations[1], expectActiveDurations[1])
                });
        assertKeepaliveStatsTrackerActive();
    }

    /*
     * Diagram of test (not to scale):
     * Key: S - Start/Stop, P - Pause, R - Resume, W - Write
     *
     * Keepalive     S       P                  W
     * Timeline  |------------------------------|
     */
    @Test
    public void testOneKeepalive_paused() {
        final int startTime = 1000;
        final int pauseTime = 2030;
        final int writeTime = 5000;

        onStartKeepalive(startTime, TEST_SLOT);

        onPauseKeepalive(pauseTime, TEST_SLOT);

        final DailykeepaliveInfoReported dailyKeepaliveInfoReported =
                buildKeepaliveMetrics(writeTime);

        // The keepalive is paused but not stopped, expect the registered duration for
        // numberOfKeepalive of 1 to still range from startTime to writeTime while the active
        // duration stops at pauseTime.
        final int[] expectRegisteredDurations = new int[] {startTime, writeTime - startTime};
        final int[] expectActiveDurations =
                new int[] {startTime + (writeTime - pauseTime), pauseTime - startTime};
        assertDailyKeepaliveInfoReported(
                dailyKeepaliveInfoReported,
                /* expectRequestsCount= */ 1,
                /* expectAutoRequestsCount= */ 1,
                /* expectAppUids= */ new int[] {TEST_UID},
                expectRegisteredDurations,
                expectActiveDurations,
                new KeepaliveCarrierStats[] {
                    getDefaultCarrierStats(expectRegisteredDurations[1], expectActiveDurations[1])
                });
        assertKeepaliveStatsTrackerActive();
    }

    /*
     * Diagram of test (not to scale):
     * Key: S - Start/Stop, P - Pause, R - Resume, W - Write
     *
     * Keepalive     S       P        R         W
     * Timeline  |------------------------------|
     */
    @Test
    public void testOneKeepalive_resumed() {
        final int startTime = 1000;
        final int pauseTime = 2030;
        final int resumeTime = 3450;
        final int writeTime = 5000;

        onStartKeepalive(startTime, TEST_SLOT);

        onPauseKeepalive(pauseTime, TEST_SLOT);

        onResumeKeepalive(resumeTime, TEST_SLOT);

        final DailykeepaliveInfoReported dailyKeepaliveInfoReported =
                buildKeepaliveMetrics(writeTime);

        // The keepalive is paused and resumed but not stopped, expect the registered duration for
        // numberOfKeepalive of 1 to still range from startTime to writeTime while the active
        // duration stops at pauseTime but resumes at resumeTime and stops at writeTime.
        final int[] expectRegisteredDurations = new int[] {startTime, writeTime - startTime};
        final int[] expectActiveDurations =
                new int[] {
                    startTime + (resumeTime - pauseTime),
                    (pauseTime - startTime) + (writeTime - resumeTime)
                };
        assertDailyKeepaliveInfoReported(
                dailyKeepaliveInfoReported,
                /* expectRequestsCount= */ 1,
                /* expectAutoRequestsCount= */ 1,
                /* expectAppUids= */ new int[] {TEST_UID},
                expectRegisteredDurations,
                expectActiveDurations,
                new KeepaliveCarrierStats[] {
                    getDefaultCarrierStats(expectRegisteredDurations[1], expectActiveDurations[1])
                });
        assertKeepaliveStatsTrackerActive();
    }

    /*
     * Diagram of test (not to scale):
     * Key: S - Start/Stop, P - Pause, R - Resume, W - Write
     *
     * Keepalive     S       P      R     S     W
     * Timeline  |------------------------------|
     */
    @Test
    public void testOneKeepalive_stopped() {
        final int startTime = 1000;
        final int pauseTime = 2930;
        final int resumeTime = 3452;
        final int stopTime = 4157;
        final int writeTime = 5000;

        onStartKeepalive(startTime, TEST_SLOT);

        onPauseKeepalive(pauseTime, TEST_SLOT);

        onResumeKeepalive(resumeTime, TEST_SLOT);

        onStopKeepalive(stopTime, TEST_SLOT);

        final DailykeepaliveInfoReported dailyKeepaliveInfoReported =
                buildKeepaliveMetrics(writeTime);

        // The keepalive is now stopped, expect the registered duration for numberOfKeepalive of 1
        // to now range from startTime to stopTime while the active duration stops at pauseTime but
        // resumes at resumeTime and stops again at stopTime.
        final int[] expectRegisteredDurations =
                new int[] {startTime + (writeTime - stopTime), stopTime - startTime};
        final int[] expectActiveDurations =
                new int[] {
                    startTime + (resumeTime - pauseTime) + (writeTime - stopTime),
                    (pauseTime - startTime) + (stopTime - resumeTime)
                };
        assertDailyKeepaliveInfoReported(
                dailyKeepaliveInfoReported,
                /* expectRequestsCount= */ 1,
                /* expectAutoRequestsCount= */ 1,
                /* expectAppUids= */ new int[] {TEST_UID},
                expectRegisteredDurations,
                expectActiveDurations,
                new KeepaliveCarrierStats[] {
                    getDefaultCarrierStats(expectRegisteredDurations[1], expectActiveDurations[1])
                });
        assertKeepaliveStatsTrackerActive();
    }

    /*
     * Diagram of test (not to scale):
     * Key: S - Start/Stop, P - Pause, R - Resume, W - Write
     *
     * Keepalive     S       P            S     W
     * Timeline  |------------------------------|
     */
    @Test
    public void testOneKeepalive_pausedStopped() {
        final int startTime = 1000;
        final int pauseTime = 2930;
        final int stopTime = 4157;
        final int writeTime = 5000;

        onStartKeepalive(startTime, TEST_SLOT);

        onPauseKeepalive(pauseTime, TEST_SLOT);

        onStopKeepalive(stopTime, TEST_SLOT);

        final DailykeepaliveInfoReported dailyKeepaliveInfoReported =
                buildKeepaliveMetrics(writeTime);

        // The keepalive is stopped while paused, expect the registered duration for
        // numberOfKeepalive of 1 to range from startTime to stopTime while the active duration
        // simply stops at pauseTime.
        final int[] expectRegisteredDurations =
                new int[] {startTime + (writeTime - stopTime), stopTime - startTime};
        final int[] expectActiveDurations =
                new int[] {startTime + (writeTime - pauseTime), (pauseTime - startTime)};
        assertDailyKeepaliveInfoReported(
                dailyKeepaliveInfoReported,
                /* expectRequestsCount= */ 1,
                /* expectAutoRequestsCount= */ 1,
                /* expectAppUids= */ new int[] {TEST_UID},
                expectRegisteredDurations,
                expectActiveDurations,
                new KeepaliveCarrierStats[] {
                    getDefaultCarrierStats(expectRegisteredDurations[1], expectActiveDurations[1])
                });
        assertKeepaliveStatsTrackerActive();
    }

    /*
     * Diagram of test (not to scale):
     * Key: S - Start/Stop, P - Pause, R - Resume, W - Write
     *
     * Keepalive     S  P R P R P R       S     W
     * Timeline  |------------------------------|
     */
    @Test
    public void testOneKeepalive_multiplePauses() {
        final int startTime = 1000;
        // Alternating timestamps of pause and resume
        final int[] pauseResumeTimes = new int[] {1200, 1400, 1700, 2000, 2400, 2800};
        final int stopTime = 4000;
        final int writeTime = 5000;

        onStartKeepalive(startTime, TEST_SLOT);

        for (int i = 0; i < pauseResumeTimes.length; i++) {
            if (i % 2 == 0) {
                onPauseKeepalive(pauseResumeTimes[i], TEST_SLOT);
            } else {
                onResumeKeepalive(pauseResumeTimes[i], TEST_SLOT);
            }
        }

        onStopKeepalive(stopTime, TEST_SLOT);

        final DailykeepaliveInfoReported dailyKeepaliveInfoReported =
                buildKeepaliveMetrics(writeTime);

        final int[] expectRegisteredDurations =
                new int[] {startTime + (writeTime - stopTime), stopTime - startTime};
        final int[] expectActiveDurations =
                new int[] {
                    startTime + /* sum of (Resume - Pause) */ (900) + (writeTime - stopTime),
                    (pauseResumeTimes[0] - startTime)
                            + /* sum of (Pause - Resume) */ (700)
                            + (stopTime - pauseResumeTimes[5])
                };
        assertDailyKeepaliveInfoReported(
                dailyKeepaliveInfoReported,
                /* expectRequestsCount= */ 1,
                /* expectAutoRequestsCount= */ 1,
                /* expectAppUids= */ new int[] {TEST_UID},
                expectRegisteredDurations,
                expectActiveDurations,
                new KeepaliveCarrierStats[] {
                    getDefaultCarrierStats(expectRegisteredDurations[1], expectActiveDurations[1])
                });
        assertKeepaliveStatsTrackerActive();
    }

    /*
     * Diagram of test (not to scale):
     * Key: S - Start/Stop, P - Pause, R - Resume, W - Write
     *
     * Keepalive1    S1  P1     R1         S1    W
     * Keepalive2           S2     P2   R2       W
     * Timeline   |------------------------------|
     */
    @Test
    public void testTwoKeepalives() {
        // The suffix 1/2 indicates which keepalive it is referring to.
        final int startTime1 = 1000;
        final int pauseTime1 = 1500;
        final int startTime2 = 2000;
        final int resumeTime1 = 2500;
        final int pauseTime2 = 3000;
        final int resumeTime2 = 3500;
        final int stopTime1 = 4157;
        final int writeTime = 5000;

        onStartKeepalive(startTime1, TEST_SLOT);

        onPauseKeepalive(pauseTime1, TEST_SLOT);

        onStartKeepalive(startTime2, TEST_SLOT2);

        onResumeKeepalive(resumeTime1, TEST_SLOT);

        onPauseKeepalive(pauseTime2, TEST_SLOT2);

        onResumeKeepalive(resumeTime2, TEST_SLOT2);

        onStopKeepalive(stopTime1, TEST_SLOT);

        final DailykeepaliveInfoReported dailyKeepaliveInfoReported =
                buildKeepaliveMetrics(writeTime);

        // With two keepalives, the number of concurrent keepalives can vary from 0-2 depending on
        // both keepalive states.
        final int[] expectRegisteredDurations =
                new int[] {
                    startTime1,
                    // 1 registered keepalive before keepalive2 starts and after keepalive1 stops.
                    (startTime2 - startTime1) + (writeTime - stopTime1),
                    // 2 registered keepalives between keepalive2 start and keepalive1 stop.
                    stopTime1 - startTime2
                };

        final int[] expectActiveDurations =
                new int[] {
                    // 0 active keepalives when keepalive1 is paused before keepalive2 starts.
                    startTime1 + (startTime2 - pauseTime1),
                    // 1 active keepalive before keepalive1 is paused.
                    (pauseTime1 - startTime1)
                            // before keepalive1 is resumed and after keepalive2 starts.
                            + (resumeTime1 - startTime2)
                            // during keepalive2 is paused since keepalive1 has been resumed.
                            + (resumeTime2 - pauseTime2)
                            // after keepalive1 stops since keepalive2 has been resumed.
                            + (writeTime - stopTime1),
                    // 2 active keepalives before keepalive2 is paused and before keepalive1 stops.
                    (pauseTime2 - resumeTime1) + (stopTime1 - resumeTime2)
                };

        assertDailyKeepaliveInfoReported(
                dailyKeepaliveInfoReported,
                /* expectRequestsCount= */ 2,
                /* expectAutoRequestsCount= */ 2,
                /* expectAppUids= */ new int[] {TEST_UID},
                expectRegisteredDurations,
                expectActiveDurations,
                // The carrier stats are aggregated here since the keepalives have the same
                // (carrierId, transportTypes, intervalMs).
                new KeepaliveCarrierStats[] {
                    getDefaultCarrierStats(
                            expectRegisteredDurations[1] + 2 * expectRegisteredDurations[2],
                            expectActiveDurations[1] + 2 * expectActiveDurations[2])
                });
        assertKeepaliveStatsTrackerActive();
    }

    /*
     * Diagram of test (not to scale):
     * Key: S - Start/Stop, P - Pause, R - Resume, W - Write
     *
     * Keepalive     S   W(reset+W)         S    W
     * Timeline   |------------------------------|
     */
    @Test
    public void testResetMetrics() {
        final int startTime = 1000;
        final int writeTime = 5000;
        final int stopTime = 7000;
        final int writeTime2 = 10000;

        onStartKeepalive(startTime, TEST_SLOT);

        final DailykeepaliveInfoReported dailyKeepaliveInfoReported =
                buildAndResetMetrics(writeTime);

        // Same expect as testOneKeepalive_startOnly
        final int[] expectRegisteredDurations = new int[] {startTime, writeTime - startTime};
        final int[] expectActiveDurations = new int[] {startTime, writeTime - startTime};
        assertDailyKeepaliveInfoReported(
                dailyKeepaliveInfoReported,
                /* expectRequestsCount= */ 1,
                /* expectAutoRequestsCount= */ 1,
                /* expectAppUids= */ new int[] {TEST_UID},
                expectRegisteredDurations,
                expectActiveDurations,
                new KeepaliveCarrierStats[] {
                    getDefaultCarrierStats(expectRegisteredDurations[1], expectActiveDurations[1])
                });

        // Check metrics was reset from above.
        final DailykeepaliveInfoReported dailyKeepaliveInfoReported2 =
                buildKeepaliveMetrics(writeTime);

        // Expect the stored durations to be 0 but still contain the number of keepalive = 1.
        assertDailyKeepaliveInfoReported(
                dailyKeepaliveInfoReported2,
                /* expectRequestsCount= */ 1,
                /* expectAutoRequestsCount= */ 1,
                /* expectAppUids= */ new int[] {TEST_UID},
                /* expectRegisteredDurations= */ new int[] {0, 0},
                /* expectActiveDurations= */ new int[] {0, 0},
                new KeepaliveCarrierStats[] {getDefaultCarrierStats(0, 0)});

        // Expect that the keepalive is still registered after resetting so it can be stopped.
        onStopKeepalive(stopTime, TEST_SLOT);

        final DailykeepaliveInfoReported dailyKeepaliveInfoReported3 =
                buildKeepaliveMetrics(writeTime2);

        final int[] expectRegisteredDurations2 =
                new int[] {writeTime2 - stopTime, stopTime - writeTime};
        final int[] expectActiveDurations2 =
                new int[] {writeTime2 - stopTime, stopTime - writeTime};
        assertDailyKeepaliveInfoReported(
                dailyKeepaliveInfoReported3,
                /* expectRequestsCount= */ 1,
                /* expectAutoRequestsCount= */ 1,
                /* expectAppUids= */ new int[] {TEST_UID},
                expectRegisteredDurations2,
                expectActiveDurations2,
                new KeepaliveCarrierStats[] {
                    getDefaultCarrierStats(expectRegisteredDurations2[1], expectActiveDurations2[1])
                });
        assertKeepaliveStatsTrackerActive();
    }

    /*
     * Diagram of test (not to scale):
     * Key: S - Start/Stop, P - Pause, R - Resume, W - Write
     *
     * Keepalive1     S1      S1  W+reset         W
     * Keepalive2         S2      W+reset         W
     * Timeline    |------------------------------|
     */
    @Test
    public void testResetMetrics_twoKeepalives() {
        final int startTime1 = 1000;
        final int startTime2 = 2000;
        final int stopTime1 = 4157;
        final int writeTime = 5000;
        final int writeTime2 = 10000;

        onStartKeepalive(startTime1, TEST_SLOT);

        onStartKeepalive(startTime2, TEST_SLOT2, TEST_NETWORK_CAPABILITIES_2,
                TEST_KEEPALIVE_INTERVAL2_SEC);

        onStopKeepalive(stopTime1, TEST_SLOT);

        final DailykeepaliveInfoReported dailyKeepaliveInfoReported =
                buildAndResetMetrics(writeTime);

        final int[] expectRegisteredDurations =
                new int[] {
                    startTime1,
                    // 1 keepalive before keepalive2 starts and after keepalive1 stops.
                    (startTime2 - startTime1) + (writeTime - stopTime1),
                    stopTime1 - startTime2
                };
        // Since there is no pause, expect the same as registered durations.
        final int[] expectActiveDurations =
                new int[] {
                    startTime1,
                    (startTime2 - startTime1) + (writeTime - stopTime1),
                    stopTime1 - startTime2
                };

        // Lifetime carrier stats are independent of each other since they have different intervals.
        final KeepaliveCarrierStats expectKeepaliveCarrierStats1 =
                getDefaultCarrierStats(stopTime1 - startTime1, stopTime1 - startTime1);
        final KeepaliveCarrierStats expectKeepaliveCarrierStats2 =
                new KeepaliveCarrierStats(
                        TEST_CARRIER_ID_2,
                        /* transportTypes= */ (1 << TRANSPORT_CELLULAR),
                        TEST_KEEPALIVE_INTERVAL2_SEC * 1000,
                        writeTime - startTime2,
                        writeTime - startTime2);

        assertDailyKeepaliveInfoReported(
                dailyKeepaliveInfoReported,
                /* expectRequestsCount= */ 2,
                /* expectAutoRequestsCount= */ 2,
                /* expectAppUids= */ new int[] {TEST_UID},
                expectRegisteredDurations,
                expectActiveDurations,
                new KeepaliveCarrierStats[] {
                    expectKeepaliveCarrierStats1, expectKeepaliveCarrierStats2
                });

        final DailykeepaliveInfoReported dailyKeepaliveInfoReported2 =
                buildKeepaliveMetrics(writeTime2);

        // Only 1 keepalive is registered and active since the reset until the writeTime2.
        final int[] expectRegisteredDurations2 = new int[] {0, writeTime2 - writeTime};
        final int[] expectActiveDurations2 = new int[] {0, writeTime2 - writeTime};

        // Only the keepalive with interval of intervalSec2 is present.
        final KeepaliveCarrierStats expectKeepaliveCarrierStats3 =
                new KeepaliveCarrierStats(
                        TEST_CARRIER_ID_2,
                        /* transportTypes= */ (1 << TRANSPORT_CELLULAR),
                        TEST_KEEPALIVE_INTERVAL2_SEC * 1000,
                        writeTime2 - writeTime,
                        writeTime2 - writeTime);

        assertDailyKeepaliveInfoReported(
                dailyKeepaliveInfoReported2,
                /* expectRequestsCount= */ 1,
                /* expectAutoRequestsCount= */ 1,
                /* expectAppUids= */ new int[] {TEST_UID},
                expectRegisteredDurations2,
                expectActiveDurations2,
                new KeepaliveCarrierStats[] {expectKeepaliveCarrierStats3});
        assertKeepaliveStatsTrackerActive();
    }

    @Test
    public void testReusableSlot_keepaliveNotStopped() {
        final int startTime1 = 1000;
        final int startTime2 = 2000;
        final int writeTime = 5000;

        onStartKeepalive(startTime1, TEST_SLOT);

        // Attempt to use the same (network, slot)
        onStartKeepalive(startTime2, TEST_SLOT);
        // Starting a 2nd keepalive on the same slot is unexpected and an error so the stats tracker
        // is disabled.
        assertKeepaliveStatsTrackerDisabled();

        final DailykeepaliveInfoReported dailyKeepaliveInfoReported =
                buildKeepaliveMetrics(writeTime);

        // Expect the duration to be from startTime1 and not startTime2, it should not start again.
        final int[] expectRegisteredDurations = new int[] {startTime1, writeTime - startTime1};
        final int[] expectActiveDurations = new int[] {startTime1, writeTime - startTime1};

        assertDailyKeepaliveInfoReported(
                dailyKeepaliveInfoReported,
                /* expectRequestsCount= */ 1,
                /* expectAutoRequestsCount= */ 1,
                /* expectAppUids= */ new int[] {TEST_UID},
                expectRegisteredDurations,
                expectActiveDurations,
                new KeepaliveCarrierStats[] {
                    getDefaultCarrierStats(expectRegisteredDurations[1], expectActiveDurations[1])
                });
    }

    @Test
    public void testReusableSlot_keepaliveStopped() {
        final int startTime1 = 1000;
        final int stopTime = 2000;
        final int startTime2 = 3000;
        final int writeTime = 5000;

        onStartKeepalive(startTime1, TEST_SLOT);

        onStopKeepalive(stopTime, TEST_SLOT);

        // Attempt to use the same (network, slot)
        onStartKeepalive(startTime2, TEST_SLOT);

        final DailykeepaliveInfoReported dailyKeepaliveInfoReported =
                buildKeepaliveMetrics(writeTime);

        // Expect the durations to be an aggregate of both periods.
        // i.e. onStartKeepalive works on the same (network, slot) if it has been stopped.
        final int[] expectRegisteredDurations =
                new int[] {
                    startTime1 + (startTime2 - stopTime),
                    (stopTime - startTime1) + (writeTime - startTime2)
                };
        final int[] expectActiveDurations =
                new int[] {
                    startTime1 + (startTime2 - stopTime),
                    (stopTime - startTime1) + (writeTime - startTime2)
                };

        assertDailyKeepaliveInfoReported(
                dailyKeepaliveInfoReported,
                /* expectRequestsCount= */ 2,
                /* expectAutoRequestsCount= */ 2,
                /* expectAppUids= */ new int[] {TEST_UID},
                expectRegisteredDurations,
                expectActiveDurations,
                new KeepaliveCarrierStats[] {
                    getDefaultCarrierStats(expectRegisteredDurations[1], expectActiveDurations[1])
                });
        assertKeepaliveStatsTrackerActive();
    }

    @Test
    public void testCarrierIdChange_changeBeforeStart() {
        // Update the list to only have sub_id_2 with carrier_id_1.
        final SubscriptionInfo subInfo = makeSubInfoMock(TEST_SUB_ID_2, TEST_CARRIER_ID_1);
        doReturn(List.of(subInfo)).when(mSubscriptionManager).getActiveSubscriptionInfoList();

        getOnSubscriptionsChangedListener().onSubscriptionsChanged();
        HandlerUtils.waitForIdle(mTestHandler, TIMEOUT_MS);

        final int startTime = 1000;
        final int writeTime = 5000;

        onStartKeepalive(startTime, TEST_SLOT, TEST_NETWORK_CAPABILITIES);
        onStartKeepalive(startTime, TEST_SLOT2, TEST_NETWORK_CAPABILITIES_2);

        final DailykeepaliveInfoReported dailyKeepaliveInfoReported =
                buildKeepaliveMetrics(writeTime);

        // The network with sub_id_1 has an unknown carrier id.
        final KeepaliveCarrierStats expectKeepaliveCarrierStats1 =
                new KeepaliveCarrierStats(
                        TelephonyManager.UNKNOWN_CARRIER_ID,
                        /* transportTypes= */ (1 << TRANSPORT_CELLULAR),
                        TEST_KEEPALIVE_INTERVAL_SEC * 1000,
                        writeTime - startTime,
                        writeTime - startTime);

        // The network with sub_id_2 has carrier_id_1.
        final KeepaliveCarrierStats expectKeepaliveCarrierStats2 =
                new KeepaliveCarrierStats(
                        TEST_CARRIER_ID_1,
                        /* transportTypes= */ (1 << TRANSPORT_CELLULAR),
                        TEST_KEEPALIVE_INTERVAL_SEC * 1000,
                        writeTime - startTime,
                        writeTime - startTime);
        assertDailyKeepaliveInfoReported(
                dailyKeepaliveInfoReported,
                /* expectRequestsCount= */ 2,
                /* expectAutoRequestsCount= */ 2,
                /* expectAppUids= */ new int[] {TEST_UID},
                /* expectRegisteredDurations= */ new int[] {startTime, 0, writeTime - startTime},
                /* expectActiveDurations= */ new int[] {startTime, 0, writeTime - startTime},
                new KeepaliveCarrierStats[] {
                    expectKeepaliveCarrierStats1, expectKeepaliveCarrierStats2
                });
        assertKeepaliveStatsTrackerActive();
    }

    @Test
    public void testCarrierIdFromWifiInfo() {
        final int startTime = 1000;
        final int writeTime = 5000;

        final WifiInfo wifiInfo = mock(WifiInfo.class);
        final WifiInfo wifiInfoCopy = mock(WifiInfo.class);

        // Building NetworkCapabilities stores a copy of the WifiInfo with makeCopy.
        doReturn(wifiInfoCopy).when(wifiInfo).makeCopy(anyLong());
        doReturn(TEST_SUB_ID_1).when(wifiInfo).getSubscriptionId();
        doReturn(TEST_SUB_ID_1).when(wifiInfoCopy).getSubscriptionId();
        final NetworkCapabilities nc =
                new NetworkCapabilities.Builder()
                        .addTransportType(TRANSPORT_WIFI)
                        .setTransportInfo(wifiInfo)
                        .build();

        onStartKeepalive(startTime, TEST_SLOT, nc);

        final DailykeepaliveInfoReported dailyKeepaliveInfoReported =
                buildKeepaliveMetrics(writeTime);

        final KeepaliveCarrierStats expectKeepaliveCarrierStats =
                new KeepaliveCarrierStats(
                        TEST_CARRIER_ID_1,
                        /* transportTypes= */ (1 << TRANSPORT_WIFI),
                        TEST_KEEPALIVE_INTERVAL_SEC * 1000,
                        writeTime - startTime,
                        writeTime - startTime);

        assertDailyKeepaliveInfoReported(
                dailyKeepaliveInfoReported,
                /* expectRequestsCount= */ 1,
                /* expectAutoRequestsCount= */ 1,
                /* expectAppUids= */ new int[] {TEST_UID},
                /* expectRegisteredDurations= */ new int[] {startTime, writeTime - startTime},
                /* expectActiveDurations= */ new int[] {startTime, writeTime - startTime},
                new KeepaliveCarrierStats[] {expectKeepaliveCarrierStats});
        assertKeepaliveStatsTrackerActive();
    }

    @Test
    public void testKeepaliveCountsAndUids() {
        final int startTime1 = 1000, startTime2 = 2000, startTime3 = 3000;
        final int writeTime = 5000;
        final int[] uids = new int[] {TEST_UID, TEST_UID + 1, TEST_UID + 2};
        onStartKeepalive(startTime1, TEST_SLOT, TEST_NETWORK_CAPABILITIES,
                TEST_KEEPALIVE_INTERVAL_SEC, uids[0], /* isAutoKeepalive= */ true);
        onStartKeepalive(startTime2, TEST_SLOT + 1, TEST_NETWORK_CAPABILITIES,
                TEST_KEEPALIVE_INTERVAL_SEC, uids[1], /* isAutoKeepalive= */ false);
        onStartKeepalive(startTime3, TEST_SLOT + 2, TEST_NETWORK_CAPABILITIES,
                TEST_KEEPALIVE_INTERVAL_SEC, uids[2], /* isAutoKeepalive= */ true);

        final DailykeepaliveInfoReported dailyKeepaliveInfoReported =
                buildKeepaliveMetrics(writeTime);
        final int[] expectRegisteredDurations =
                new int[] {
                    startTime1,
                    (startTime2 - startTime1),
                    (startTime3 - startTime2),
                    (writeTime - startTime3)
                };
        final int[] expectActiveDurations =
                new int[] {
                    startTime1,
                    (startTime2 - startTime1),
                    (startTime3 - startTime2),
                    (writeTime - startTime3)
                };
        assertDailyKeepaliveInfoReported(
                dailyKeepaliveInfoReported,
                /* expectRequestsCount= */ 3,
                /* expectAutoRequestsCount= */ 2,
                /* expectAppUids= */ uids,
                expectRegisteredDurations,
                expectActiveDurations,
                new KeepaliveCarrierStats[] {
                    getDefaultCarrierStats(
                            writeTime * 3 - startTime1 - startTime2 - startTime3,
                            writeTime * 3 - startTime1 - startTime2 - startTime3)
                });
        assertKeepaliveStatsTrackerActive();
    }

    @Test
    public void testUpdateDefaultSubId() {
        final int startTime1 = 1000;
        final int startTime2 = 3000;
        final int writeTime = 5000;

        // No TelephonyNetworkSpecifier set with subId to force the use of default subId.
        final NetworkCapabilities nc =
                new NetworkCapabilities.Builder().addTransportType(TRANSPORT_CELLULAR).build();
        onStartKeepalive(startTime1, TEST_SLOT, nc);
        // Update default subId
        triggerBroadcastDefaultSubId(TEST_SUB_ID_1);
        onStartKeepalive(startTime2, TEST_SLOT2, nc);

        final DailykeepaliveInfoReported dailyKeepaliveInfoReported =
                buildKeepaliveMetrics(writeTime);

        final int[] expectRegisteredDurations =
                new int[] {startTime1, startTime2 - startTime1, writeTime - startTime2};
        final int[] expectActiveDurations =
                new int[] {startTime1, startTime2 - startTime1, writeTime - startTime2};
        // Expect the carrier id of the first keepalive to be unknown
        final KeepaliveCarrierStats expectKeepaliveCarrierStats1 =
                new KeepaliveCarrierStats(
                        TelephonyManager.UNKNOWN_CARRIER_ID,
                        /* transportTypes= */ (1 << TRANSPORT_CELLULAR),
                        TEST_KEEPALIVE_INTERVAL_SEC * 1000,
                        writeTime - startTime1,
                        writeTime - startTime1);
        // Expect the carrier id of the second keepalive to be TEST_CARRIER_ID_1, from TEST_SUB_ID_1
        final KeepaliveCarrierStats expectKeepaliveCarrierStats2 =
                new KeepaliveCarrierStats(
                        TEST_CARRIER_ID_1,
                        /* transportTypes= */ (1 << TRANSPORT_CELLULAR),
                        TEST_KEEPALIVE_INTERVAL_SEC * 1000,
                        writeTime - startTime2,
                        writeTime - startTime2);
        assertDailyKeepaliveInfoReported(
                dailyKeepaliveInfoReported,
                /* expectRequestsCount= */ 2,
                /* expectAutoRequestsCount= */ 2,
                /* expectAppUids= */ new int[] {TEST_UID},
                expectRegisteredDurations,
                expectActiveDurations,
                new KeepaliveCarrierStats[] {
                    expectKeepaliveCarrierStats1, expectKeepaliveCarrierStats2
                });
        assertKeepaliveStatsTrackerActive();
    }

    @Test
    @IgnoreAfter(Build.VERSION_CODES.S_V2)
    public void testWriteMetrics_doNothingBeforeT() {
        // Keepalive stats use repeated atoms, which are only supported on T+. If written to statsd
        // on S- they will bootloop the system, so they must not be sent on S-. See b/289471411.
        final int writeTime = 1000;
        setElapsedRealtime(writeTime);
        visibleOnHandlerThread(mTestHandler, () -> mKeepaliveStatsTracker.writeAndResetMetrics());
        verify(mDependencies, never()).writeStats(any());
    }

    @Test
    @IgnoreUpTo(Build.VERSION_CODES.S_V2)
    public void testWriteMetrics() {
        final int writeTime = 1000;

        final ArgumentCaptor<DailykeepaliveInfoReported> dailyKeepaliveInfoReportedCaptor =
                ArgumentCaptor.forClass(DailykeepaliveInfoReported.class);

        setElapsedRealtime(writeTime);
        visibleOnHandlerThread(mTestHandler, () -> mKeepaliveStatsTracker.writeAndResetMetrics());
        // Ensure writeStats is called with the correct DailykeepaliveInfoReported metrics.
        verify(mDependencies).writeStats(dailyKeepaliveInfoReportedCaptor.capture());
        final DailykeepaliveInfoReported dailyKeepaliveInfoReported =
                dailyKeepaliveInfoReportedCaptor.getValue();

        // Same as the no keepalive case
        final int[] expectRegisteredDurations = new int[] {writeTime};
        final int[] expectActiveDurations = new int[] {writeTime};
        assertDailyKeepaliveInfoReported(
                dailyKeepaliveInfoReported,
                /* expectRequestsCount= */ 0,
                /* expectAutoRequestsCount= */ 0,
                /* expectAppUids= */ new int[0],
                expectRegisteredDurations,
                expectActiveDurations,
                new KeepaliveCarrierStats[0]);

        assertTrue(mKeepaliveStatsTracker.allMetricsExpected(dailyKeepaliveInfoReported));

        // Write time after 27 hours.
        final int writeTime2 = 27 * 60 * 60 * 1000;
        setElapsedRealtime(writeTime2);

        visibleOnHandlerThread(mTestHandler, () -> mKeepaliveStatsTracker.writeAndResetMetrics());
        verify(mDependencies, times(2)).writeStats(dailyKeepaliveInfoReportedCaptor.capture());
        final DailykeepaliveInfoReported dailyKeepaliveInfoReported2 =
                dailyKeepaliveInfoReportedCaptor.getValue();

        assertFalse(mKeepaliveStatsTracker.allMetricsExpected(dailyKeepaliveInfoReported2));
    }
}
