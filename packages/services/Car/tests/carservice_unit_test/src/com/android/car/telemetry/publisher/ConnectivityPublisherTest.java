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

package com.android.car.telemetry.publisher;

import static android.net.NetworkStats.TAG_NONE;
import static android.net.NetworkTemplate.OEM_MANAGED_NO;
import static android.net.NetworkTemplate.OEM_MANAGED_PAID;
import static android.net.NetworkTemplate.OEM_MANAGED_PRIVATE;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.annotation.NonNull;
import android.app.usage.NetworkStatsManager;
import android.car.telemetry.TelemetryProto;
import android.car.telemetry.TelemetryProto.ConnectivityPublisher.OemType;
import android.car.telemetry.TelemetryProto.ConnectivityPublisher.Transport;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkIdentity;
import android.net.NetworkTemplate;
import android.os.Looper;
import android.os.PersistableBundle;
import android.os.SystemClock;
import android.telephony.TelephonyManager;

import com.android.car.telemetry.ResultStore;
import com.android.car.telemetry.UidPackageMapper;
import com.android.car.telemetry.databroker.DataSubscriber;
import com.android.car.telemetry.publisher.net.FakeNetworkStats;
import com.android.car.telemetry.publisher.net.NetworkStatsManagerProxy;
import com.android.car.telemetry.publisher.net.NetworkStatsWrapper;
import com.android.car.telemetry.sessioncontroller.SessionAnnotation;
import com.android.car.telemetry.sessioncontroller.SessionController;
import com.android.car.test.FakeHandlerWrapper;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.File;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Tests for {@link ConnectivityPublisher}.
 *
 * <p>Note that {@link TAG_NONE} is a total value across all the tags. NetworkStatsManager returns 2
 * types of netstats: summary for all (tag is equal to 0, i.e. TAG_NONE), and summary per tag.
 */
@RunWith(MockitoJUnitRunner.class)
public class ConnectivityPublisherTest {
    private static final TelemetryProto.Publisher PUBLISHER_WIFI_OEM_NONE =
            TelemetryProto.Publisher.newBuilder()
                    .setConnectivity(
                            TelemetryProto.ConnectivityPublisher.newBuilder()
                                    .setTransport(Transport.TRANSPORT_WIFI)
                                    .setOemType(OemType.OEM_NONE))
                    .build();
    private static final TelemetryProto.Publisher PUBLISHER_WIFI_OEM_MANAGED =
            TelemetryProto.Publisher.newBuilder()
                    .setConnectivity(
                            TelemetryProto.ConnectivityPublisher.newBuilder()
                                    .setTransport(Transport.TRANSPORT_WIFI)
                                    .setOemType(OemType.OEM_MANAGED))
                    .build();
    private static final TelemetryProto.Publisher PUBLISHER_CELL_OEM_NONE =
            TelemetryProto.Publisher.newBuilder()
                    .setConnectivity(
                            TelemetryProto.ConnectivityPublisher.newBuilder()
                                    .setTransport(Transport.TRANSPORT_CELLULAR)
                                    .setOemType(OemType.OEM_NONE))
                    .build();
    private static final TelemetryProto.Subscriber SUBSCRIBER_WIFI_OEM_NONE =
            TelemetryProto.Subscriber.newBuilder()
                    .setHandler("empty_handler")
                    .setPublisher(PUBLISHER_WIFI_OEM_NONE)
                    .build();
    private static final TelemetryProto.Subscriber SUBSCRIBER_WIFI_OEM_MANAGED =
            TelemetryProto.Subscriber.newBuilder()
                    .setHandler("empty_handler")
                    .setPublisher(PUBLISHER_WIFI_OEM_MANAGED)
                    .build();
    private static final TelemetryProto.Subscriber SUBSCRIBER_CELL_OEM_NONE =
            TelemetryProto.Subscriber.newBuilder()
                    .setHandler("empty_handler")
                    .setPublisher(PUBLISHER_CELL_OEM_NONE)
                    .build();
    private static final TelemetryProto.MetricsConfig METRICS_CONFIG =
            TelemetryProto.MetricsConfig.newBuilder()
                    .setName("myconfig")
                    .setVersion(1)
                    .addSubscribers(SUBSCRIBER_WIFI_OEM_NONE)
                    .addSubscribers(SUBSCRIBER_WIFI_OEM_MANAGED)
                    .addSubscribers(SUBSCRIBER_CELL_OEM_NONE)
                    .setScript("function empty_handler()\nend")
                    .build();
    private static final SessionAnnotation SESSION_ANNOTATION_BEGIN_1 =
            new SessionAnnotation(1, SessionController.STATE_ENTER_DRIVING_SESSION, 0, 0, "", 0);
    private static final SessionAnnotation SESSION_ANNOTATION_END_1 =
            new SessionAnnotation(1, SessionController.STATE_EXIT_DRIVING_SESSION, 0, 0, "", 0);
    private static final SessionAnnotation SESSION_ANNOTATION_BEGIN_2 =
            new SessionAnnotation(2, SessionController.STATE_ENTER_DRIVING_SESSION, 0, 0, "", 0);
    private static final SessionAnnotation SESSION_ANNOTATION_END_2 =
            new SessionAnnotation(2, SessionController.STATE_EXIT_DRIVING_SESSION, 0, 0, "", 0);
    private static final SessionAnnotation SESSION_ANNOTATION_BEGIN_3 =
            new SessionAnnotation(3, SessionController.STATE_ENTER_DRIVING_SESSION, 0, 0, "", 0);


    /** See {@code ConnectivityPublisher#pullInitialNetstats()}. */
    private static final int BASELINE_PULL_COUNT = 8;

    // Test network usage tags.
    private static final int TAG_1 = 1;
    private static final int TAG_2 = 2;

    // Test network usage uids.
    private static final int UID_1 = 1;
    private static final int UID_2 = 2;
    private static final int UID_3 = 3;
    private static final int UID_4 = 4;

    @Mock private Context mMockContext;
    @Mock private UidPackageMapper mMockUidMapper;

    private final long mNow = System.currentTimeMillis(); // since epoch

    private final FakeHandlerWrapper mFakeHandler =
            new FakeHandlerWrapper(Looper.getMainLooper(), FakeHandlerWrapper.Mode.QUEUEING);

    private final FakeDataSubscriber mDataSubscriberWifi =
            new FakeDataSubscriber(METRICS_CONFIG, SUBSCRIBER_WIFI_OEM_NONE);
    private final FakeDataSubscriber mDataSubscriberWifiOemManaged =
            new FakeDataSubscriber(METRICS_CONFIG, SUBSCRIBER_WIFI_OEM_MANAGED);
    private final FakeDataSubscriber mDataSubscriberCell =
            new FakeDataSubscriber(METRICS_CONFIG, SUBSCRIBER_CELL_OEM_NONE);

    private final FakePublisherListener mFakePublisherListener = new FakePublisherListener();
    private final FakeNetworkStatsManager mFakeManager = new FakeNetworkStatsManager();

    private ConnectivityPublisher mPublisher; // subject
    private File mTestRootDir;
    private ResultStore mResultStore;

    @Mock
    private SessionController mMockSessionController;
    @Captor
    private ArgumentCaptor<SessionController.SessionControllerCallback>
            mSessionControllerCallbackArgumentCaptor;

    @Before
    public void setUp() throws Exception {
        mTestRootDir = Files.createTempDirectory("car_telemetry_test").toFile();
        mResultStore = new ResultStore(mMockContext, mTestRootDir);
        when(mMockUidMapper.getPackagesForUid(anyInt())).thenReturn(List.of("pkg1"));
        mPublisher =
                new ConnectivityPublisher(
                        mFakePublisherListener, mFakeManager, mFakeHandler.getMockHandler(),
                        mResultStore, mMockSessionController, mMockUidMapper);
        verify(mMockSessionController).registerCallback(
                mSessionControllerCallbackArgumentCaptor.capture());
    }

    private boolean verifyPublisherSavedData(int expectedSessionId) {
        PersistableBundle savedResult = mResultStore.getPublisherData(
                ConnectivityPublisher.class.getSimpleName(), false);
        if (savedResult == null) {
            return false;
        }
        if (savedResult.keySet().size() != 2) {
            return false;
        }

        return savedResult.containsKey(Constants.ANNOTATION_BUNDLE_KEY_SESSION_ID)
                && savedResult.getInt(Constants.ANNOTATION_BUNDLE_KEY_SESSION_ID)
                == expectedSessionId;
    }

    @Test
    public void testAddDataSubscriber_storesIt() {
        mPublisher.addDataSubscriber(mDataSubscriberWifi);

        assertThat(mPublisher.hasDataSubscriber(mDataSubscriberWifi)).isTrue();
    }

    @Test
    public void testRemoveDataSubscriber_removesIt() {
        mPublisher.addDataSubscriber(mDataSubscriberWifi);
        mPublisher.addDataSubscriber(mDataSubscriberCell);

        mPublisher.removeDataSubscriber(mDataSubscriberWifi);

        assertThat(mPublisher.hasDataSubscriber(mDataSubscriberWifi)).isFalse();
    }

    @Test
    public void testRemoveDataSubscriber_givenWrongSubscriber_doesNothing() {
        mPublisher.addDataSubscriber(mDataSubscriberWifi);

        mPublisher.removeDataSubscriber(mDataSubscriberCell);

        assertThat(mPublisher.hasDataSubscriber(mDataSubscriberWifi)).isTrue();
    }

    @Test
    public void testRemoveAllDataSubscribers_removesAll() {
        mPublisher.addDataSubscriber(mDataSubscriberWifi);
        mPublisher.addDataSubscriber(mDataSubscriberCell);

        mPublisher.removeAllDataSubscribers();

        assertThat(mPublisher.hasDataSubscriber(mDataSubscriberWifi)).isFalse();
    }

    @Test
    public void testPullsOnlyNecessaryData() {
        // triggers pulling of empty baseline netstats
        mSessionControllerCallbackArgumentCaptor.getValue().onSessionStateChanged(
                SESSION_ANNOTATION_BEGIN_1);
        mPublisher.addDataSubscriber(mDataSubscriberWifi);

        // triggers the second pull, calculates the diff and stores the result in ResultStore
        mSessionControllerCallbackArgumentCaptor.getValue().onSessionStateChanged(
                SESSION_ANNOTATION_END_1);

        // Pulls netstats only once for wifi.
        assertThat(mFakeManager.getMethodCallCount("querySummary"))
                .isEqualTo(BASELINE_PULL_COUNT + 1);
        assertThat(mFakeManager.getMethodCallCount("queryTaggedSummary"))
                .isEqualTo(BASELINE_PULL_COUNT + 1);
    }

    @Test
    public void testPullsOnlyNecessaryData_wifiAndMobile() {
        // triggers pulling of empty baseline netstats
        mSessionControllerCallbackArgumentCaptor.getValue().onSessionStateChanged(
                SESSION_ANNOTATION_BEGIN_1);
        mPublisher.addDataSubscriber(mDataSubscriberWifi);
        mPublisher.addDataSubscriber(mDataSubscriberCell);

        // triggers the second pull, calculates the diff and stores the result in ResultStore
        mSessionControllerCallbackArgumentCaptor.getValue().onSessionStateChanged(
                SESSION_ANNOTATION_END_1);


        assertThat(mFakeManager.getMethodCallCount("querySummary"))
                .isEqualTo(BASELINE_PULL_COUNT + 2);
        assertThat(mFakeManager.getMethodCallCount("queryTaggedSummary"))
                .isEqualTo(BASELINE_PULL_COUNT + 2);
    }

    @Test
    public void testPullsTaggedAndUntaggedMobileStats() {
        // triggers pulling of empty baseline netstats
        mPublisher.handleSessionStateChange(SESSION_ANNOTATION_BEGIN_1);
        mFakeManager.addMobileStats(UID_1, TAG_1, 2500L, 3500L, OEM_MANAGED_NO, mNow);
        mFakeManager.addMobileStats(UID_1, TAG_NONE, 2502L, 3502L, OEM_MANAGED_NO, mNow);
        mFakeManager.addWifiStats(UID_1, TAG_2, 30, 30, OEM_MANAGED_NO, mNow);
        mFakeManager.addWifiStats(UID_2, TAG_2, 10, 10, OEM_MANAGED_PAID, mNow);
        mFakeManager.addWifiStats(UID_3, TAG_2, 6, 6, OEM_MANAGED_PRIVATE, mNow);
        mPublisher.addDataSubscriber(mDataSubscriberCell);

        // triggers the second pull, calculates the diff and stores the result in ResultStore
        mSessionControllerCallbackArgumentCaptor.getValue().onSessionStateChanged(
                SESSION_ANNOTATION_END_1);
        verifyPublisherSavedData(1);
        // Triggers processing of previous session results and pushing them to publishers.
        mSessionControllerCallbackArgumentCaptor.getValue().onSessionStateChanged(
                SESSION_ANNOTATION_BEGIN_2);


        assertThat(mDataSubscriberCell.mPushedData).hasSize(1);
        PersistableBundle result = mDataSubscriberCell.get(0);
        // Matches only "UID_1/TAG_1" and "UID_1/TAG_NONE" above.
        assertThat(result.getLong(Constants.CONNECTIVITY_BUNDLE_KEY_START_MILLIS))
                .isLessThan(mNow);
        assertThat(result.getLong(Constants.CONNECTIVITY_BUNDLE_KEY_END_MILLIS))
                .isGreaterThan(result.getLong(Constants.CONNECTIVITY_BUNDLE_KEY_START_MILLIS));
        assertThat(result.getInt(Constants.CONNECTIVITY_BUNDLE_KEY_SIZE))
                .isEqualTo(2);
        assertThat(result.getIntArray(Constants.CONNECTIVITY_BUNDLE_KEY_UID)).asList()
                .containsExactly(UID_1, UID_1);
        assertThat(result.getIntArray(Constants.CONNECTIVITY_BUNDLE_KEY_TAG)).asList()
                .containsExactly(TAG_1, TAG_NONE);
        assertThat(result.getLongArray(Constants.CONNECTIVITY_BUNDLE_KEY_RX_BYTES)).asList()
                .containsExactly(2500L, 2502L);
        assertThat(result.getLongArray(Constants.CONNECTIVITY_BUNDLE_KEY_TX_BYTES)).asList()
                .containsExactly(3500L, 3502L);
    }

    @Test
    public void testPullsOemManagedWifiStats() {
        // triggers pulling of empty baseline netstats
        mSessionControllerCallbackArgumentCaptor.getValue().onSessionStateChanged(
                SESSION_ANNOTATION_BEGIN_1);
        mFakeManager.addMobileStats(UID_1, TAG_2, 5000, 5000, OEM_MANAGED_NO, mNow);
        mFakeManager.addWifiStats(UID_1, TAG_1, 30, 30, OEM_MANAGED_NO, mNow);
        mFakeManager.addWifiStats(UID_2, TAG_NONE, 100L, 200L, OEM_MANAGED_PAID, mNow);
        mFakeManager.addWifiStats(UID_3, TAG_2, 6L, 7L, OEM_MANAGED_PRIVATE, mNow);
        mPublisher.addDataSubscriber(mDataSubscriberWifiOemManaged);

        // triggers the second pull, calculates the diff and stores the result in ResultStore
        mSessionControllerCallbackArgumentCaptor.getValue().onSessionStateChanged(
                SESSION_ANNOTATION_END_1);
        verifyPublisherSavedData(1);
        // Triggers processing of previous session results and pushing them to publishers.
        mSessionControllerCallbackArgumentCaptor.getValue().onSessionStateChanged(
                SESSION_ANNOTATION_BEGIN_2);

        assertThat(mDataSubscriberWifiOemManaged.mPushedData).hasSize(1);
        PersistableBundle result = mDataSubscriberWifiOemManaged.get(0);

        assertThat(result.getInt(Constants.ANNOTATION_BUNDLE_KEY_SESSION_ID))
                .isEqualTo(1);
        assertThat(result.getInt(Constants.CONNECTIVITY_BUNDLE_KEY_SIZE))
                .isEqualTo(2);
        assertThat(result.getIntArray(Constants.CONNECTIVITY_BUNDLE_KEY_UID))
                .asList().containsExactly(UID_2, UID_3);
        assertThat(result.getIntArray(Constants.CONNECTIVITY_BUNDLE_KEY_TAG))
                .asList().containsExactly(TAG_NONE, TAG_2);
        assertThat(result.getLongArray(Constants.CONNECTIVITY_BUNDLE_KEY_RX_BYTES))
                .asList().containsExactly(100L, 6L);
        assertThat(result.getLongArray(Constants.CONNECTIVITY_BUNDLE_KEY_TX_BYTES))
                .asList().containsExactly(200L, 7L);
    }

    @Test
    public void testPullsOemNotManagedWifiStats() {
        // triggers pulling of empty baseline netstats
        mSessionControllerCallbackArgumentCaptor.getValue().onSessionStateChanged(
                SESSION_ANNOTATION_BEGIN_1);
        mFakeManager.addMobileStats(UID_4, TAG_2, 5000, 5000, OEM_MANAGED_NO, mNow);
        mFakeManager.addWifiStats(UID_1, TAG_1, 30, 30, OEM_MANAGED_NO, mNow);
        mFakeManager.addWifiStats(UID_2, TAG_1, 10, 10, OEM_MANAGED_PAID, mNow);
        mFakeManager.addWifiStats(UID_3, TAG_1, 6, 6, OEM_MANAGED_PRIVATE, mNow);
        mPublisher.addDataSubscriber(mDataSubscriberWifi);

        // triggers the second pull, calculates the diff and stores the result in ResultStore
        mSessionControllerCallbackArgumentCaptor.getValue().onSessionStateChanged(
                SESSION_ANNOTATION_END_1);
        verifyPublisherSavedData(1);
        // Triggers processing of previous session results and pushing them to publishers.
        mSessionControllerCallbackArgumentCaptor.getValue().onSessionStateChanged(
                SESSION_ANNOTATION_BEGIN_2);

        assertThat(mDataSubscriberWifi.mPushedData).hasSize(1);
        PersistableBundle result = mDataSubscriberWifi.get(0);
        assertThat(result.getInt(Constants.ANNOTATION_BUNDLE_KEY_SESSION_ID)).isEqualTo(1);
        // Matches only UID_1.
        assertThat(result.getInt(Constants.CONNECTIVITY_BUNDLE_KEY_SIZE))
                .isEqualTo(1);
        assertThat(result.getIntArray(Constants.CONNECTIVITY_BUNDLE_KEY_UID))
                .asList().containsExactly(UID_1);
        assertThat(result.getIntArray(Constants.CONNECTIVITY_BUNDLE_KEY_TAG))
                .asList().containsExactly(TAG_1);
        assertThat(result.getLongArray(Constants.CONNECTIVITY_BUNDLE_KEY_TX_BYTES))
                .asList().containsExactly(30L);
        assertThat(result.getLongArray(Constants.CONNECTIVITY_BUNDLE_KEY_RX_BYTES))
                .asList().containsExactly(30L);
    }

    @Test
    public void testPullsStatsOnlyBetweenBootTimeMinus2HoursAndNow() {
        // triggers pulling of empty baseline netstats
        mSessionControllerCallbackArgumentCaptor.getValue().onSessionStateChanged(
                SESSION_ANNOTATION_BEGIN_1);
        long mBootTimeMillis = mNow - SystemClock.elapsedRealtime(); // since epoch
        long bootMinus30Mins = mBootTimeMillis - Duration.ofMinutes(30).toMillis();
        long bootMinus5Hours = mBootTimeMillis - Duration.ofHours(5).toMillis();
        mFakeManager.addMobileStats(UID_1, TAG_2, 5000, 5000, OEM_MANAGED_NO, mNow);
        mFakeManager.addWifiStats(UID_1, TAG_1, 10, 10, OEM_MANAGED_NO, mNow);
        mFakeManager.addWifiStats(UID_2, TAG_1, 10, 10, OEM_MANAGED_NO, bootMinus30Mins);
        mFakeManager.addWifiStats(UID_3, TAG_1, 7, 7, OEM_MANAGED_NO, bootMinus5Hours);
        mPublisher.addDataSubscriber(mDataSubscriberWifi);

        // triggers the second pull, calculates the diff and stores the result in ResultStore
        mSessionControllerCallbackArgumentCaptor.getValue().onSessionStateChanged(
                SESSION_ANNOTATION_END_1);
        verifyPublisherSavedData(1);
        // Triggers processing of previous session results and pushing them to publishers.
        mSessionControllerCallbackArgumentCaptor.getValue().onSessionStateChanged(
                SESSION_ANNOTATION_BEGIN_2);


        assertThat(mDataSubscriberWifi.mPushedData).hasSize(1);
        PersistableBundle result = mDataSubscriberWifi.get(0);
        // Only UID_1 and UID_2 are fetched, because other stats are outside
        // of the time range.
        assertThat(result.getInt(Constants.CONNECTIVITY_BUNDLE_KEY_SIZE))
                .isEqualTo(2);
        assertThat(result.getIntArray(Constants.CONNECTIVITY_BUNDLE_KEY_UID))
                .asList().containsExactly(UID_1, UID_2);
        assertThat(result.getIntArray(Constants.CONNECTIVITY_BUNDLE_KEY_TAG))
                .asList().containsExactly(TAG_1, TAG_1);
        assertThat(result.getLongArray(Constants.CONNECTIVITY_BUNDLE_KEY_TX_BYTES))
                .asList().containsExactly(10L, 10L);
        assertThat(result.getLongArray(Constants.CONNECTIVITY_BUNDLE_KEY_RX_BYTES))
                .asList().containsExactly(10L, 10L);
    }

    @Test
    public void testPushesDataAsNotLarge() {
        mPublisher.addDataSubscriber(mDataSubscriberWifi);
        mFakeManager.addWifiStats(UID_1, TAG_1, 10, 10, OEM_MANAGED_NO, mNow);

        mSessionControllerCallbackArgumentCaptor.getValue().onSessionStateChanged(
                SESSION_ANNOTATION_BEGIN_1);
        mSessionControllerCallbackArgumentCaptor.getValue().onSessionStateChanged(
                SESSION_ANNOTATION_END_1);
        verifyPublisherSavedData(1);
        // Triggers processing of previous session results and pushing them to publishers.
        mSessionControllerCallbackArgumentCaptor.getValue().onSessionStateChanged(
                SESSION_ANNOTATION_BEGIN_2);

        // The final data should not be marked "large".
        assertThat(mDataSubscriberWifi.mPushedData.get(0).mIsLargeData).isFalse();
    }

    @Test
    public void testSubtractsFromInitialPull() {
        long someTimeAgo = mNow - Duration.ofMinutes(1).toMillis();
        mFakeManager.addWifiStats(UID_4, TAG_1, 10, 10, OEM_MANAGED_PRIVATE, someTimeAgo);
        mFakeManager.addWifiStats(UID_4, TAG_1, 11, 11, OEM_MANAGED_PAID, someTimeAgo);
        mFakeManager.addWifiStats(UID_4, TAG_1, 12, 12, OEM_MANAGED_NO, someTimeAgo);
        mSessionControllerCallbackArgumentCaptor.getValue().onSessionStateChanged(
                SESSION_ANNOTATION_BEGIN_1); // pulls 10, 11, 12 bytes.

        // A hack to force the publisher to compute the diff from the initial pull.
        // Otherwise, we'll get "(100 + 12) - 12".
        mFakeManager.clearNetworkStats();
        mFakeManager.addWifiStats(UID_4, TAG_1, 100, 100, OEM_MANAGED_NO, mNow);
        mPublisher.addDataSubscriber(mDataSubscriberWifi);
        mSessionControllerCallbackArgumentCaptor.getValue().onSessionStateChanged(
                SESSION_ANNOTATION_END_1);

        verifyPublisherSavedData(1);
        // Triggers processing of previous session results and pushing them to publishers.
        mSessionControllerCallbackArgumentCaptor.getValue().onSessionStateChanged(
                SESSION_ANNOTATION_BEGIN_2);

        assertThat(mDataSubscriberWifi.mPushedData).hasSize(1);
        PersistableBundle result = mDataSubscriberWifi.get(0);
        assertThat(result.getInt(Constants.CONNECTIVITY_BUNDLE_KEY_SIZE))
                .isEqualTo(1);
        assertThat(result.getIntArray(Constants.CONNECTIVITY_BUNDLE_KEY_UID))
                .asList().containsExactly(UID_4);
        assertThat(result.getIntArray(Constants.CONNECTIVITY_BUNDLE_KEY_TAG))
                .asList().containsExactly(TAG_1);
        assertThat(result.getLongArray(Constants.CONNECTIVITY_BUNDLE_KEY_TX_BYTES))
                .asList().containsExactly(100L - 12L);
        assertThat(result.getLongArray(Constants.CONNECTIVITY_BUNDLE_KEY_RX_BYTES))
                .asList().containsExactly(100L - 12L);
    }

    @Test
    public void testSubtractsFromThePreviousPull() {
        // ==== 0th (initial) pull.
        long someTimeAgo = mNow - Duration.ofMinutes(1).toMillis();
        mFakeManager.addWifiStats(UID_4, TAG_1, 12, 12, OEM_MANAGED_NO, someTimeAgo);
        mSessionControllerCallbackArgumentCaptor.getValue().onSessionStateChanged(
                SESSION_ANNOTATION_BEGIN_1); // pulls 12 bytes
        // ==== 1st pull.
        mFakeManager.addWifiStats(UID_4, TAG_1, 200, 200, OEM_MANAGED_NO, someTimeAgo);
        mPublisher.addDataSubscriber(mDataSubscriberWifi);
        mSessionControllerCallbackArgumentCaptor.getValue().onSessionStateChanged(
                SESSION_ANNOTATION_END_1); // pulls 200 + 12 bytes
        verifyPublisherSavedData(1);
        // Triggers processing of previous session results and pushing them to publishers.
        mSessionControllerCallbackArgumentCaptor.getValue().onSessionStateChanged(
                SESSION_ANNOTATION_BEGIN_2);

        assertThat(mDataSubscriberWifi.mPushedData).hasSize(1);
        PersistableBundle result = mDataSubscriberWifi.get(0);
        assertThat(result.getInt(Constants.CONNECTIVITY_BUNDLE_KEY_SIZE))
                .isEqualTo(1);
        assertThat(result.getIntArray(Constants.CONNECTIVITY_BUNDLE_KEY_UID))
                .asList().containsExactly(UID_4);
        assertThat(result.getIntArray(Constants.CONNECTIVITY_BUNDLE_KEY_TAG))
                .asList().containsExactly(TAG_1);
        // It's 200, because it subtracts previous pull 12 from (200 + 12).
        assertThat(result.getLongArray(Constants.CONNECTIVITY_BUNDLE_KEY_TX_BYTES))
                .asList().containsExactly(200L);

        // ==== 2nd pull.
        mFakeManager.addWifiStats(UID_4, TAG_1, 1000, 1000, OEM_MANAGED_NO, mNow);
        mSessionControllerCallbackArgumentCaptor.getValue().onSessionStateChanged(
                SESSION_ANNOTATION_END_2); // pulls 200 + 12 + 1000 bytes
        verifyPublisherSavedData(2);
        // Triggers processing of previous session results and pushing them to publishers.
        mSessionControllerCallbackArgumentCaptor.getValue().onSessionStateChanged(
                SESSION_ANNOTATION_BEGIN_3);

        assertThat(mDataSubscriberWifi.mPushedData).hasSize(2);
        result = mDataSubscriberWifi.get(1);
        assertThat(result.getInt(Constants.CONNECTIVITY_BUNDLE_KEY_SIZE))
                .isEqualTo(1);
        assertThat(result.getIntArray(Constants.CONNECTIVITY_BUNDLE_KEY_UID))
                .asList().containsExactly(UID_4);
        assertThat(result.getIntArray(Constants.CONNECTIVITY_BUNDLE_KEY_TAG))
                .asList().containsExactly(TAG_1);
        // It's 1000, because it subtracts previous pull (200 + 12) from (200 + 12 + 1000).
        assertThat(result.getLongArray(Constants.CONNECTIVITY_BUNDLE_KEY_TX_BYTES))
                .asList().containsExactly(1000L);
    }

    @Test
    public void testWhenQueryThrowsNullPointerExceptionIsCaught() {
        mFakeManager.setSimulateFailedQuery(true);
        mSessionControllerCallbackArgumentCaptor.getValue().onSessionStateChanged(
                SESSION_ANNOTATION_BEGIN_1);

        // querySummary gets called for each QueryParam combination, but throws
        // NullPointerException each time, which is caught
        assertThat(mFakeManager.getMethodCallCount("querySummary"))
                .isEqualTo(BASELINE_PULL_COUNT);
        // queryTaggedSummary not reached because of previous NullPointerException in querySummary
        assertThat(mFakeManager.getMethodCallCount("queryTaggedSummary"))
                .isEqualTo(0);
    }

    private static class FakeDataSubscriber extends DataSubscriber {
        private final ArrayList<PushedData> mPushedData = new ArrayList<>();

        FakeDataSubscriber(
                TelemetryProto.MetricsConfig metricsConfig, TelemetryProto.Subscriber subscriber) {
            super(/* dataBroker= */ null, metricsConfig, subscriber);
        }

        @Override
        public int push(PersistableBundle data, boolean isLargeData) {
            mPushedData.add(new PushedData(data, isLargeData));
            return mPushedData.size();
        }

        /** Returns the pushed data by the given index. */
        PersistableBundle get(int index) {
            return mPushedData.get(index).mData;
        }
    }

    /** Data pushed to a subscriber. */
    private static class PushedData {
        private final PersistableBundle mData;
        private final boolean mIsLargeData;

        PushedData(PersistableBundle data, boolean isLargeData) {
            mData = data;
            mIsLargeData = isLargeData;
        }
    }

    /**
     * A fake for {@link NetworkStatsManager}.
     *
     * <p>It's used to find the matching buckets for the given conditions. Both getStartTimestamp()
     * and getEndTimestamp() is used only in matching during FakeNetworkStatsManager.querySummary()
     * method, and the values are not used in ConnectivityPublisher class later.
     */
    private static class FakeNetworkStatsManager extends NetworkStatsManagerProxy {
        private final ArrayList<FakeNetworkStats.CustomBucket> mBuckets = new ArrayList<>();
        private final HashMap<String, Integer> mMethodCallCount = new HashMap<>();

        private boolean mSimulateFailedQuery = false;

        private FakeNetworkStatsManager() {
            super(/* networkStatsManager= */ null);
        }

        /** Adds {@code NetworkStats.Bucket} that will be returned by {@code querySummary()}. */
        public void addWifiStats(
                int uid, int tag, long rx, long tx, int oemManaged, long timestampMillis) {
            NetworkIdentity identity =
                    new NetworkIdentity.Builder()
                            .setType(ConnectivityManager.TYPE_WIFI)
                            .setWifiNetworkKey("guest-wifi")
                            .setOemManaged(oemManaged)
                            .build();
            FakeNetworkStats.CustomBucket bucket =
                    new FakeNetworkStats.CustomBucket(
                            identity,
                            uid,
                            tag,
                            /* rxBytes= */ rx,
                            /* txBytes= */ tx,
                            timestampMillis);
            mBuckets.add(bucket);
        }

        /** Adds {@code NetworkStats.Bucket} that will be returned by {@code querySummary()}. */
        public void addMobileStats(
                int uid, int tag, long rx, long tx, int oemManaged, long timestampMillis) {
            NetworkIdentity identity =
                    new NetworkIdentity.Builder()
                            .setType(ConnectivityManager.TYPE_MOBILE)
                            .setRatType(TelephonyManager.NETWORK_TYPE_GPRS)
                            .setOemManaged(oemManaged)
                            .setDefaultNetwork(true)
                            .build();
            FakeNetworkStats.CustomBucket bucket =
                    new FakeNetworkStats.CustomBucket(
                            identity,
                            uid,
                            tag,
                            /* rxBytes= */ rx,
                            /* txBytes= */ tx,
                            timestampMillis);
            mBuckets.add(bucket);
        }

        public void clearNetworkStats() {
            mBuckets.clear();
        }

        /** Returns the API method call count. */
        public int getMethodCallCount(String name) {
            return mMethodCallCount.getOrDefault(name, 0);
        }

        public void setSimulateFailedQuery(boolean simulateFailedQuery) {
            mSimulateFailedQuery = simulateFailedQuery;
        }

        @Override
        @NonNull
        public NetworkStatsWrapper querySummary(NetworkTemplate template, long start, long end) {
            increaseMethodCall("querySummary", 1);
            if (mSimulateFailedQuery) {
                throw new NullPointerException();
            }
            return commonQuerySummary(false, template, start, end);
        }

        @Override
        @NonNull
        public NetworkStatsWrapper queryTaggedSummary(
                NetworkTemplate template, long start, long end) {
            increaseMethodCall("queryTaggedSummary", 1);
            if (mSimulateFailedQuery) {
                throw new NullPointerException();
            }
            return commonQuerySummary(true, template, start, end);
        }

        private NetworkStatsWrapper commonQuerySummary(
                boolean taggedOnly, NetworkTemplate template, long start, long end) {
            FakeNetworkStats result = new FakeNetworkStats();
            for (FakeNetworkStats.CustomBucket bucket : mBuckets) {
                // NOTE: the actual implementation calculates bucket ratio in the given time range
                //       instead of this simple time range checking.
                if (bucket.getStartTimeStamp() < start || bucket.getStartTimeStamp() > end) {
                    continue;
                }
                boolean bucketHasTag = bucket.getTag() != TAG_NONE;
                if (taggedOnly == bucketHasTag && template.matches(bucket.getIdentity())) {
                    result.add(bucket);
                }
            }
            return result;
        }

        private void increaseMethodCall(String methodName, int count) {
            mMethodCallCount.put(methodName, mMethodCallCount.getOrDefault(methodName, 0) + count);
        }
    }
}
