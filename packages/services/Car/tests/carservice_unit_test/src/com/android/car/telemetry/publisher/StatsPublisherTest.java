/*
 * Copyright (C) 2021 The Android Open Source Project
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

import static android.car.telemetry.TelemetryProto.StatsPublisher.SystemMetric.ACTIVITY_FOREGROUND_STATE_CHANGED;
import static android.car.telemetry.TelemetryProto.StatsPublisher.SystemMetric.APP_START_MEMORY_STATE_CAPTURED;
import static android.car.telemetry.TelemetryProto.StatsPublisher.SystemMetric.PROCESS_MEMORY_STATE;

import static com.android.car.telemetry.AtomsProto.Atom.ACTIVITY_FOREGROUND_STATE_CHANGED_FIELD_NUMBER;
import static com.android.car.telemetry.AtomsProto.Atom.APP_START_MEMORY_STATE_CAPTURED_FIELD_NUMBER;
import static com.android.car.telemetry.AtomsProto.Atom.PROCESS_MEMORY_STATE_FIELD_NUMBER;
import static com.android.car.telemetry.publisher.StatsPublisher.ACTIVITY_FOREGROUND_STATE_CHANGED_ATOM_MATCHER_ID;
import static com.android.car.telemetry.publisher.StatsPublisher.ACTIVITY_FOREGROUND_STATE_CHANGED_EVENT_METRIC_ID;
import static com.android.car.telemetry.publisher.StatsPublisher.APP_START_MEMORY_STATE_CAPTURED_ATOM_MATCHER_ID;
import static com.android.car.telemetry.publisher.StatsPublisher.APP_START_MEMORY_STATE_CAPTURED_EVENT_METRIC_ID;
import static com.android.car.telemetry.publisher.StatsPublisher.PROCESS_MEMORY_STATE_FIELDS_MATCHER;
import static com.android.car.telemetry.publisher.StatsPublisher.PROCESS_MEMORY_STATE_GAUGE_METRIC_ID;
import static com.android.car.telemetry.publisher.StatsPublisher.PROCESS_MEMORY_STATE_MATCHER_ID;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.StatsManager;
import android.car.telemetry.TelemetryProto;
import android.content.Context;
import android.os.Looper;
import android.os.Message;
import android.os.PersistableBundle;
import android.os.Process;
import android.os.SystemClock;

import com.android.car.telemetry.AtomsProto.AppStartMemoryStateCaptured;
import com.android.car.telemetry.AtomsProto.Atom;
import com.android.car.telemetry.AtomsProto.ProcessMemoryState;
import com.android.car.telemetry.ResultStore;
import com.android.car.telemetry.StatsLogProto;
import com.android.car.telemetry.StatsLogProto.ConfigMetricsReport;
import com.android.car.telemetry.StatsLogProto.DimensionsValue;
import com.android.car.telemetry.StatsLogProto.DimensionsValueTuple;
import com.android.car.telemetry.StatsLogProto.EventMetricData;
import com.android.car.telemetry.StatsLogProto.GaugeBucketInfo;
import com.android.car.telemetry.StatsLogProto.GaugeMetricData;
import com.android.car.telemetry.StatsLogProto.StatsLogReport;
import com.android.car.telemetry.StatsdConfigProto;
import com.android.car.telemetry.databroker.DataSubscriber;
import com.android.car.test.FakeHandlerWrapper;

import com.google.common.collect.Range;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.nio.file.Files;
import java.util.Arrays;

@RunWith(MockitoJUnitRunner.class)
public class StatsPublisherTest {
    private static final TelemetryProto.Publisher STATS_PUBLISHER_PARAMS_1 =
            TelemetryProto.Publisher.newBuilder()
                    .setStats(TelemetryProto.StatsPublisher.newBuilder()
                            .setSystemMetric(APP_START_MEMORY_STATE_CAPTURED))
                    .build();
    private static final TelemetryProto.Publisher STATS_PUBLISHER_PARAMS_2 =
            TelemetryProto.Publisher.newBuilder()
                    .setStats(TelemetryProto.StatsPublisher.newBuilder()
                            .setSystemMetric(PROCESS_MEMORY_STATE))
                    .build();
    private static final TelemetryProto.Publisher STATS_PUBLISHER_PARAMS_3 =
            TelemetryProto.Publisher.newBuilder()
                    .setStats(TelemetryProto.StatsPublisher.newBuilder()
                            .setSystemMetric(ACTIVITY_FOREGROUND_STATE_CHANGED))
                    .build();
    private static final TelemetryProto.Subscriber SUBSCRIBER_1 =
            TelemetryProto.Subscriber.newBuilder()
                    .setHandler("handler_fn_1")
                    .setPublisher(STATS_PUBLISHER_PARAMS_1)
                    .build();
    private static final TelemetryProto.Subscriber SUBSCRIBER_2 =
            TelemetryProto.Subscriber.newBuilder()
                    .setHandler("handler_fn_2")
                    .setPublisher(STATS_PUBLISHER_PARAMS_2)
                    .build();
    private static final TelemetryProto.Subscriber SUBSCRIBER_3 =
            TelemetryProto.Subscriber.newBuilder()
                    .setHandler("handler_fn_3")
                    .setPublisher(STATS_PUBLISHER_PARAMS_3)
                    .build();
    private static final TelemetryProto.MetricsConfig METRICS_CONFIG =
            TelemetryProto.MetricsConfig.newBuilder()
                    .setName("myconfig")
                    .setVersion(1)
                    .addSubscribers(SUBSCRIBER_1)
                    .addSubscribers(SUBSCRIBER_2)
                    .addSubscribers(SUBSCRIBER_3)
                    .build();

    private static final long SUBSCRIBER_1_HASH = -8101507323446050791L;  // Used as configKey.
    private static final long SUBSCRIBER_2_HASH = 2778197004730583271L;  // Used as configKey.
    private static final long SUBSCRIBER_3_HASH = 7046592220837963576L;  // Used as configKey.

    // This StatsdConfig is generated for SUBSCRIBER_1.
    private static final StatsdConfigProto.StatsdConfig STATSD_CONFIG_1 =
            StatsdConfigProto.StatsdConfig.newBuilder()
                    .setId(SUBSCRIBER_1_HASH)
                    .addAtomMatcher(StatsdConfigProto.AtomMatcher.newBuilder()
                            .setId(APP_START_MEMORY_STATE_CAPTURED_ATOM_MATCHER_ID)
                            .setSimpleAtomMatcher(
                                    StatsdConfigProto.SimpleAtomMatcher.newBuilder()
                                            .setAtomId(
                                                    APP_START_MEMORY_STATE_CAPTURED_FIELD_NUMBER)))
                    .addEventMetric(StatsdConfigProto.EventMetric.newBuilder()
                            .setId(APP_START_MEMORY_STATE_CAPTURED_EVENT_METRIC_ID)
                            .setWhat(APP_START_MEMORY_STATE_CAPTURED_ATOM_MATCHER_ID))
                    .addAllowedLogSource("AID_SYSTEM")
                    .build();

    // This StatsdConfig is generated for SUBSCRIBER_2.
    private static final StatsdConfigProto.StatsdConfig STATSD_CONFIG_2 =
            StatsdConfigProto.StatsdConfig.newBuilder()
                    .setId(SUBSCRIBER_2_HASH)
                    .addAtomMatcher(StatsdConfigProto.AtomMatcher.newBuilder()
                            // The id must be unique within StatsdConfig/matchers
                            .setId(PROCESS_MEMORY_STATE_MATCHER_ID)
                            .setSimpleAtomMatcher(StatsdConfigProto.SimpleAtomMatcher.newBuilder()
                                    .setAtomId(PROCESS_MEMORY_STATE_FIELD_NUMBER)))
                    .addGaugeMetric(StatsdConfigProto.GaugeMetric.newBuilder()
                            // The id must be unique within StatsdConfig/metrics
                            .setId(PROCESS_MEMORY_STATE_GAUGE_METRIC_ID)
                            .setWhat(PROCESS_MEMORY_STATE_MATCHER_ID)
                            .setDimensionsInWhat(StatsdConfigProto.FieldMatcher.newBuilder()
                                    .setField(PROCESS_MEMORY_STATE_FIELD_NUMBER)
                                    .addChild(StatsdConfigProto.FieldMatcher.newBuilder()
                                            .setField(1))  // ProcessMemoryState.uid
                                    .addChild(StatsdConfigProto.FieldMatcher.newBuilder()
                                            .setField(2))  // ProcessMemoryState.process_name
                            )
                            .setGaugeFieldsFilter(StatsdConfigProto.FieldFilter.newBuilder()
                                    .setFields(PROCESS_MEMORY_STATE_FIELDS_MATCHER))
                            .setSamplingType(
                                    StatsdConfigProto.GaugeMetric.SamplingType.RANDOM_ONE_SAMPLE)
                            .setBucket(StatsdConfigProto.TimeUnit.FIVE_MINUTES)
                    )
                    .addAllowedLogSource("AID_SYSTEM")
                    .addPullAtomPackages(StatsdConfigProto.PullAtomPackages.newBuilder()
                        .setAtomId(PROCESS_MEMORY_STATE_FIELD_NUMBER)
                        .addPackages("AID_SYSTEM"))
                    .build();

    // This StatsdConfig is generated for SUBSCRIBER_3.
    private static final StatsdConfigProto.StatsdConfig STATSD_CONFIG_3 =
            StatsdConfigProto.StatsdConfig.newBuilder()
                    .setId(SUBSCRIBER_3_HASH)
                    .addAtomMatcher(StatsdConfigProto.AtomMatcher.newBuilder()
                            .setId(ACTIVITY_FOREGROUND_STATE_CHANGED_ATOM_MATCHER_ID)
                            .setSimpleAtomMatcher(
                                    StatsdConfigProto.SimpleAtomMatcher.newBuilder()
                                            .setAtomId(
                                                    ACTIVITY_FOREGROUND_STATE_CHANGED_FIELD_NUMBER)
                                                ))
                    .addEventMetric(StatsdConfigProto.EventMetric.newBuilder()
                            .setId(ACTIVITY_FOREGROUND_STATE_CHANGED_EVENT_METRIC_ID)
                            .setWhat(ACTIVITY_FOREGROUND_STATE_CHANGED_ATOM_MATCHER_ID))
                    .addAllowedLogSource("AID_SYSTEM")
                    .build();

    private static final EventMetricData EVENT_DATA =
            EventMetricData.newBuilder()
                    .setElapsedTimestampNanos(99999999L)
                    .setAtom(Atom.newBuilder()
                            .setAppStartMemoryStateCaptured(
                                    AppStartMemoryStateCaptured.newBuilder()
                                            .setUid(1000)
                                            .setActivityName("activityName")
                                            .setRssInBytes(1234L)))
                    .build();

    private static final GaugeMetricData GAUGE_DATA =
            GaugeMetricData.newBuilder()
                    .addBucketInfo(GaugeBucketInfo.newBuilder()
                            .addAtom(Atom.newBuilder()
                                    .setProcessMemoryState(ProcessMemoryState.newBuilder()
                                            .setRssInBytes(4567L)))
                            .addElapsedTimestampNanos(445678901L))
                    .addDimensionLeafValuesInWhat(DimensionsValue.newBuilder()
                            .setValueInt(234))
                    .build();

    private static final StatsLogProto.ConfigMetricsReportList METRICS_REPORT =
            StatsLogProto.ConfigMetricsReportList.newBuilder()
                    .addReports(ConfigMetricsReport.newBuilder()
                            .addMetrics(StatsLogReport.newBuilder()
                                    .setMetricId(APP_START_MEMORY_STATE_CAPTURED_EVENT_METRIC_ID)
                                    .setEventMetrics(
                                            StatsLogReport.EventMetricDataWrapper.newBuilder()
                                                    .addData(EVENT_DATA))))
                    .addReports(ConfigMetricsReport.newBuilder()
                            .addMetrics(StatsLogReport.newBuilder()
                                    .setMetricId(PROCESS_MEMORY_STATE_GAUGE_METRIC_ID)
                                    .setGaugeMetrics(
                                            StatsLogReport.GaugeMetricDataWrapper.newBuilder()
                                                    .addData(GAUGE_DATA))
                                    .setDimensionsPathInWhat(DimensionsValue.newBuilder()
                                            .setValueTuple(DimensionsValueTuple.newBuilder()
                                                    .addDimensionsValue(DimensionsValue.newBuilder()
                                                            .setField(1))))))
                    .build();

    // By default the test assumes all the StatsdConfigs are valid.
    private static final StatsLogProto.StatsdStatsReport CONFIG_STATS_REPORT =
            StatsLogProto.StatsdStatsReport.newBuilder()
                    .addConfigStats(StatsLogProto.StatsdStatsReport.ConfigStats.newBuilder()
                            // in unit tests UID of test and app are the same
                            .setUid(Process.myUid())
                            .setId(SUBSCRIBER_1_HASH)  // id is the same as configKey
                            .setIsValid(true))
                    .addConfigStats(StatsLogProto.StatsdStatsReport.ConfigStats.newBuilder()
                            // in unit tests UID of test and app are the same
                            .setUid(Process.myUid())
                            .setId(SUBSCRIBER_2_HASH)  // id is the same as configKey
                            .setIsValid(true))
                    .build();

    private static final StatsLogProto.ConfigMetricsReportList EMPTY_METRICS_REPORT =
            StatsLogProto.ConfigMetricsReportList.newBuilder().build();

    private static final DataSubscriber DATA_SUBSCRIBER_1 =
            new DataSubscriber(null, METRICS_CONFIG, SUBSCRIBER_1);

    private final FakeHandlerWrapper mFakeHandlerWrapper =
            new FakeHandlerWrapper(Looper.getMainLooper(), FakeHandlerWrapper.Mode.QUEUEING);
    private final FakePublisherListener mFakePublisherListener = new FakePublisherListener();

    private ResultStore mResultStore;
    private StatsPublisher mPublisher;  // subject

    @Mock private Context mMockContext;
    @Mock private StatsManagerProxy mStatsManager;

    @Captor private ArgumentCaptor<PersistableBundle> mBundleCaptor;

    @Before
    public void setUp() throws Exception {
        mResultStore = new ResultStore(
                mMockContext, Files.createTempDirectory("telemetry_test").toFile());
        mPublisher = createRestartedPublisher();
        when(mStatsManager.getStatsMetadata()).thenReturn(CONFIG_STATS_REPORT.toByteArray());
    }

    /**
     * Emulates a restart by creating a new StatsPublisher. StatsManager and PersistableBundle
     * stays the same.
     */
    private StatsPublisher createRestartedPublisher() {
        return new StatsPublisher(
                mFakePublisherListener,
                mStatsManager,
                mResultStore,
                mFakeHandlerWrapper.getMockHandler());
    }

    @Test
    public void testAddDataSubscriber_registersNewListener() throws Exception {
        mPublisher.addDataSubscriber(DATA_SUBSCRIBER_1);

        verify(mStatsManager, times(1))
                .addConfig(SUBSCRIBER_1_HASH, STATSD_CONFIG_1.toByteArray());
        assertThat(mPublisher.hasDataSubscriber(DATA_SUBSCRIBER_1)).isTrue();
    }

    @Test
    public void testAddDataSubscriber_sameVersion_addsToStatsdOnce() throws Exception {
        mPublisher.addDataSubscriber(DATA_SUBSCRIBER_1);
        mPublisher.addDataSubscriber(DATA_SUBSCRIBER_1);

        verify(mStatsManager, times(1))
                .addConfig(SUBSCRIBER_1_HASH, STATSD_CONFIG_1.toByteArray());
        assertThat(mPublisher.hasDataSubscriber(DATA_SUBSCRIBER_1)).isTrue();
    }

    @Test
    public void testAddDataSubscriber_whenRestarted_addsToStatsdOnce() throws Exception {
        mPublisher.addDataSubscriber(DATA_SUBSCRIBER_1);
        StatsPublisher publisher2 = createRestartedPublisher();

        publisher2.addDataSubscriber(DATA_SUBSCRIBER_1);

        verify(mStatsManager, times(1))
                .addConfig(SUBSCRIBER_1_HASH, STATSD_CONFIG_1.toByteArray());
        assertThat(publisher2.hasDataSubscriber(DATA_SUBSCRIBER_1)).isTrue();
    }

    @Test
    public void testAddDataSubscriber_forProcessMemoryState_generatesStatsdMetrics()
            throws Exception {
        DataSubscriber processMemoryStateSubscriber =
                new DataSubscriber(null, METRICS_CONFIG, SUBSCRIBER_2);

        mPublisher.addDataSubscriber(processMemoryStateSubscriber);

        verify(mStatsManager, times(1))
                .addConfig(SUBSCRIBER_2_HASH, STATSD_CONFIG_2.toByteArray());
        assertThat(mPublisher.hasDataSubscriber(processMemoryStateSubscriber)).isTrue();
    }

    @Test
    public void testAddDataSubscriber_forActivityForegroundState_generatesStatsdMetrics()
            throws Exception {
        DataSubscriber activityForegroundStateSubscriber =
                new DataSubscriber(null, METRICS_CONFIG, SUBSCRIBER_3);

        mPublisher.addDataSubscriber(activityForegroundStateSubscriber);

        verify(mStatsManager, times(1))
                .addConfig(SUBSCRIBER_3_HASH, STATSD_CONFIG_3.toByteArray());
        assertThat(mPublisher.hasDataSubscriber(activityForegroundStateSubscriber)).isTrue();
    }

    @Test
    public void testRemoveDataSubscriber_removesFromStatsd() throws Exception {
        mPublisher.addDataSubscriber(DATA_SUBSCRIBER_1);

        mPublisher.removeDataSubscriber(DATA_SUBSCRIBER_1);

        verify(mStatsManager, times(1)).removeConfig(SUBSCRIBER_1_HASH);
        assertThat(mResultStore.getPublisherData(StatsPublisher.class.getSimpleName(), false))
                .isNull();
        assertThat(mPublisher.hasDataSubscriber(DATA_SUBSCRIBER_1)).isFalse();
    }

    @Test
    public void testRemoveDataSubscriber_ifNotFound_nothingHappensButCallsStatsdRemove()
            throws Exception {
        mPublisher.removeDataSubscriber(DATA_SUBSCRIBER_1);

        // It should try removing StatsdConfig from StatsD, in case it was added there before and
        // left dangled.
        verify(mStatsManager, times(1)).removeConfig(SUBSCRIBER_1_HASH);
        assertThat(mPublisher.hasDataSubscriber(DATA_SUBSCRIBER_1)).isFalse();
    }

    @Test
    public void testRemoveAllDataSubscriber_whenRestarted_removesFromStatsdAndClears()
            throws Exception {
        mPublisher.addDataSubscriber(DATA_SUBSCRIBER_1);
        StatsPublisher publisher2 = createRestartedPublisher();

        publisher2.removeAllDataSubscribers();

        verify(mStatsManager, times(1)).removeConfig(SUBSCRIBER_1_HASH);
        assertThat(mResultStore.getPublisherData(StatsPublisher.class.getSimpleName(), false))
                .isNull();
        assertThat(publisher2.hasDataSubscriber(DATA_SUBSCRIBER_1)).isFalse();
    }

    @Test
    public void testAddDataSubscriber_queuesPeriodicTaskInTheHandler() {
        mPublisher.addDataSubscriber(DATA_SUBSCRIBER_1);

        assertThat(mFakeHandlerWrapper.getQueuedMessages()).hasSize(1);
        Message msg = mFakeHandlerWrapper.getQueuedMessages().get(0);
        assertThatMessageIsScheduledWithGivenDelay(msg, 0);
    }

    @Test
    public void testAddDataSubscriber_whenFails_notifiesFailureConsumer() throws Exception {
        doThrow(new StatsManager.StatsUnavailableException("fail"))
                .when(mStatsManager).addConfig(anyLong(), any());

        mPublisher.addDataSubscriber(DATA_SUBSCRIBER_1);

        assertThat(mFakePublisherListener.mPublisherFailure)
                .hasMessageThat().contains("Failed to add config");
        assertThat(mFakePublisherListener.mFailedConfigs).hasSize(1);  // got all the failed configs
    }

    @Test
    public void testRemoveDataSubscriber_removesPeriodicStatsdReportPull() {
        mPublisher.addDataSubscriber(DATA_SUBSCRIBER_1);

        mPublisher.removeDataSubscriber(DATA_SUBSCRIBER_1);

        assertThat(mFakeHandlerWrapper.getQueuedMessages()).isEmpty();
    }

    @Test
    public void testRemoveAllDataSubscriber_removesPeriodicStatsdReportPull() {
        mPublisher.addDataSubscriber(DATA_SUBSCRIBER_1);

        mPublisher.removeAllDataSubscribers();

        assertThat(mFakeHandlerWrapper.getQueuedMessages()).isEmpty();
    }

    @Test
    public void testAfterDispatchItSchedulesANewPullReportTask() throws Exception {
        mPublisher.addDataSubscriber(DATA_SUBSCRIBER_1);
        Message firstMessage = mFakeHandlerWrapper.getQueuedMessages().get(0);
        when(mStatsManager.getReports(anyLong())).thenReturn(EMPTY_METRICS_REPORT.toByteArray());

        mFakeHandlerWrapper.dispatchQueuedMessages();

        assertThat(mFakeHandlerWrapper.getQueuedMessages()).hasSize(1);
        Message newMessage = mFakeHandlerWrapper.getQueuedMessages().get(0);
        assertThat(newMessage).isNotEqualTo(firstMessage);
        long expectedPullPeriodMillis = 10 * 60 * 1000;  // 10 minutes
        assertThatMessageIsScheduledWithGivenDelay(newMessage, expectedPullPeriodMillis);
    }

    @Test
    public void testPullStatsdReport_correctlyPushesBundlesToSubscribers() throws Exception {
        DataSubscriber subscriber1 = Mockito.mock(DataSubscriber.class);
        when(subscriber1.getSubscriber()).thenReturn(SUBSCRIBER_1);
        when(subscriber1.getMetricsConfig()).thenReturn(METRICS_CONFIG);
        when(subscriber1.getPublisherParam()).thenReturn(SUBSCRIBER_1.getPublisher());
        mPublisher.addDataSubscriber(subscriber1);
        DataSubscriber subscriber2 = Mockito.mock(DataSubscriber.class);
        when(subscriber2.getSubscriber()).thenReturn(SUBSCRIBER_2);
        when(subscriber2.getMetricsConfig()).thenReturn(METRICS_CONFIG);
        when(subscriber2.getPublisherParam()).thenReturn(SUBSCRIBER_2.getPublisher());
        mPublisher.addDataSubscriber(subscriber2);
        when(mStatsManager.getReports(anyLong())).thenReturn(METRICS_REPORT.toByteArray());

        mFakeHandlerWrapper.dispatchQueuedMessages();

        verify(subscriber1).push(mBundleCaptor.capture(), anyBoolean());
        PersistableBundle bundle1 = mBundleCaptor.getValue();
        assertThat(bundle1.getLongArray("stats.elapsed_timestamp_nanos"))
            .asList().containsExactly(99999999L);
        assertThat(bundle1.getIntArray("stats.uid")).asList().containsExactly(1000);
        assertThat(Arrays.asList(bundle1.getStringArray("stats.activity_name")))
            .containsExactly("activityName");
        assertThat(bundle1.getLongArray("stats.rss_in_bytes")).asList().containsExactly(1234L);
        verify(subscriber2).push(mBundleCaptor.capture(), anyBoolean());
        PersistableBundle bundle2 = mBundleCaptor.getValue();
        assertThat(bundle2.getIntArray("stats.uid")).asList().containsExactly(234);
        assertThat(bundle2.getLongArray("stats.rss_in_bytes")).asList().containsExactly(4567L);
        assertThat(bundle2.getLongArray("stats.elapsed_timestamp_nanos"))
            .asList().containsExactly(445678901L);
    }

    @Test
    public void testBundleWithLargeSize_isLargeData() {
        PersistableBundle bundle = new PersistableBundle();
        bundle.putBooleanArray("bool", new boolean[1000]);
        bundle.putLongArray("long", new long[1000]);
        bundle.putIntArray("int", new int[1000]);
        bundle.putDoubleArray("double", new double[1000]);
        String[] strArray = new String[1000];
        for (int i = 0; i < strArray.length; ++i) {
            strArray[i] = "test";
        }
        bundle.putStringArray("string", strArray);

        assertThat(mPublisher.isBundleLargeData(bundle)).isTrue();
    }

    @Test
    public void testBundleWithSmallSize_isNotLargeData() {
        PersistableBundle bundle = new PersistableBundle();
        bundle.putBooleanArray("bool", new boolean[100]);
        bundle.putLongArray("long", new long[100]);
        bundle.putIntArray("int", new int[100]);
        bundle.putDoubleArray("double", new double[100]);
        String[] strArray = new String[100];
        for (int i = 0; i < strArray.length; ++i) {
            strArray[i] = "test";
        }
        bundle.putStringArray("string", strArray);

        assertThat(mPublisher.isBundleLargeData(bundle)).isFalse();
    }

    @Test
    public void testOnInvalidConfig_notifiesPublisherFailureListener() throws Exception {
        DataSubscriber subscriber = spy(new DataSubscriber(null, METRICS_CONFIG, SUBSCRIBER_1));
        mPublisher.addDataSubscriber(subscriber);
        reset(mStatsManager);
        when(mStatsManager.getStatsMetadata()).thenReturn(
                StatsLogProto.StatsdStatsReport.newBuilder()
                        .addConfigStats(StatsLogProto.StatsdStatsReport.ConfigStats.newBuilder()
                                // in unit tests UID of test and app are the same
                                .setUid(Process.myUid())
                                .setId(SUBSCRIBER_1_HASH)  // id is the same as configKey
                                .setIsValid(false))
                .build().toByteArray());
        when(mStatsManager.getReports(anyLong())).thenReturn(EMPTY_METRICS_REPORT.toByteArray());

        mFakeHandlerWrapper.dispatchQueuedMessages();

        // subscriber shouldn't get data, because of EMPTY_METRICS_REPORT.
        verify(subscriber, times(0)).push(any(), anyBoolean());
        assertThat(mFakePublisherListener.mFailedConfigs).containsExactly(METRICS_CONFIG);
        assertThat(mFakePublisherListener.mPublisherFailure)
                .hasMessageThat().contains("Found invalid configs");
    }

    private static void assertThatMessageIsScheduledWithGivenDelay(Message msg, long delayMillis) {
        long expectedTimeMillis = SystemClock.uptimeMillis() + delayMillis;
        long deltaMillis = 1000;  // +/- 1 seconds is good enough for testing
        assertThat(msg.getWhen()).isIn(Range
                .closed(expectedTimeMillis - deltaMillis, expectedTimeMillis + deltaMillis));
    }
}
