/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.helpers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import androidx.test.runner.AndroidJUnit4;

import com.android.os.nano.StatsLog;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Map;

/**
 * Android Unit tests for {@link StatsdStatsHelper}.
 *
 * <p>To run: Disable SELinux: adb shell setenforce 0; if this fails with "permission denied", try
 * Build and install Development apk. "adb shell su 0 setenforce 0" atest
 * CollectorsHelperTest:com.android.helpers.StatsdStatsTest
 */
@RunWith(AndroidJUnit4.class)
public class StatsdStatsHelperTest {

    private static class TestNonEmptyStatsdHelper implements StatsdStatsHelper.IStatsdHelper {

        StatsLog.StatsdStatsReport testReport = new StatsLog.StatsdStatsReport();

        static final int ATOM_STATS_COUNT = 2;
        static final int CONFIG_STATS_COUNT = 1;
        static final int CONFIG_STATS_METRIC_COUNT = 2;
        static final int CONFIG_STATS_CONDITION_COUNT = 2;
        static final int CONFIG_STATS_ALERT_COUNT = 2;
        static final int CONFIG_STATS_MATCHER_COUNT = 2;
        static final int PULLED_ATOM_STATS_COUNT = 2;
        static final int ATOM_METRIC_STATS_COUNT = 1;
        static final int DETECTED_LOG_LOSS_STATS_COUNT = 1;

        public TestNonEmptyStatsdHelper() {
            populateAtomStatsTestData(testReport);
            populateConfigStatsTestData(testReport);
            populateAnomalyAlarmStatsTestData(testReport);
            populatePulledAtomStatsTestData(testReport);
            populateAtomMetricStatsTestData(testReport);
            populateDetectedLogLossStatsTestData(testReport);
            populateEventQueueOverflowStatsTestData(testReport);
        }

        private static void populateAtomStatsTestData(StatsLog.StatsdStatsReport testReport) {
            testReport.atomStats = new StatsLog.StatsdStatsReport.AtomStats[ATOM_STATS_COUNT];

            for (int i = 0; i < ATOM_STATS_COUNT; i++) {
                testReport.atomStats[i] = new StatsLog.StatsdStatsReport.AtomStats();
                int fieldValue = i + 1;
                testReport.atomStats[i].tag = fieldValue++;
                testReport.atomStats[i].count = fieldValue++;
                testReport.atomStats[i].errorCount = fieldValue++;
            }
        }

        private static void populateConfigStatsTestData(StatsLog.StatsdStatsReport testReport) {
            testReport.configStats = new StatsLog.StatsdStatsReport.ConfigStats[CONFIG_STATS_COUNT];
            for (int i = 0; i < CONFIG_STATS_COUNT; i++) {
                testReport.configStats[i] = new StatsLog.StatsdStatsReport.ConfigStats();
                testReport.configStats[i].id = i + 1;
                testReport.configStats[i].metricCount = CONFIG_STATS_METRIC_COUNT;
                testReport.configStats[i].conditionCount = CONFIG_STATS_CONDITION_COUNT;
                testReport.configStats[i].alertCount = CONFIG_STATS_ALERT_COUNT;
                testReport.configStats[i].matcherCount = CONFIG_STATS_MATCHER_COUNT;

                testReport.configStats[i].metricStats =
                        populateConfigStatsMetricTestData(CONFIG_STATS_METRIC_COUNT);
                testReport.configStats[i].conditionStats =
                        populateConfigStatsConditionTestData(CONFIG_STATS_CONDITION_COUNT);
                testReport.configStats[i].matcherStats =
                        populateConfigStatsMatcherTestData(CONFIG_STATS_ALERT_COUNT);
                testReport.configStats[i].alertStats =
                        populateConfigStatsAlertTestData(CONFIG_STATS_MATCHER_COUNT);
            }
        }

        private static StatsLog.StatsdStatsReport.AlertStats[] populateConfigStatsAlertTestData(
                int configStatsAlertCount) {
            StatsLog.StatsdStatsReport.AlertStats[] alertStats =
                    new StatsLog.StatsdStatsReport.AlertStats[configStatsAlertCount];

            for (int i = 0; i < configStatsAlertCount; i++) {
                alertStats[i] = new StatsLog.StatsdStatsReport.AlertStats();
                int fieldValue = i + 1;
                alertStats[i].id = fieldValue++;
                alertStats[i].alertedTimes = fieldValue++;
            }

            return alertStats;
        }

        private static StatsLog.StatsdStatsReport.MetricStats[] populateConfigStatsMetricTestData(
                int configStatsMetricCount) {
            StatsLog.StatsdStatsReport.MetricStats[] metricStats =
                    new StatsLog.StatsdStatsReport.MetricStats[configStatsMetricCount];

            for (int i = 0; i < configStatsMetricCount; i++) {
                metricStats[i] = new StatsLog.StatsdStatsReport.MetricStats();
                int fieldValue = i + 1;
                metricStats[i].id = fieldValue++;
                metricStats[i].maxTupleCounts = fieldValue++;
            }

            return metricStats;
        }

        private static StatsLog.StatsdStatsReport.ConditionStats[]
                populateConfigStatsConditionTestData(int configStatsConditionCount) {
            StatsLog.StatsdStatsReport.ConditionStats[] conditionStats =
                    new StatsLog.StatsdStatsReport.ConditionStats[configStatsConditionCount];

            for (int i = 0; i < configStatsConditionCount; i++) {
                conditionStats[i] = new StatsLog.StatsdStatsReport.ConditionStats();
                int fieldValue = i + 1;
                conditionStats[i].id = fieldValue++;
                conditionStats[i].maxTupleCounts = fieldValue++;
            }

            return conditionStats;
        }

        private static StatsLog.StatsdStatsReport.MatcherStats[] populateConfigStatsMatcherTestData(
                int configStatsMatcherCount) {
            StatsLog.StatsdStatsReport.MatcherStats[] matcherStats =
                    new StatsLog.StatsdStatsReport.MatcherStats[configStatsMatcherCount];

            for (int i = 0; i < configStatsMatcherCount; i++) {
                matcherStats[i] = new StatsLog.StatsdStatsReport.MatcherStats();
                int fieldValue = i + 1;
                matcherStats[i].id = fieldValue++;
                matcherStats[i].matchedTimes = fieldValue++;
            }

            return matcherStats;
        }

        private static void populateAnomalyAlarmStatsTestData(
                StatsLog.StatsdStatsReport testReport) {
            testReport.anomalyAlarmStats = new StatsLog.StatsdStatsReport.AnomalyAlarmStats();
            testReport.anomalyAlarmStats.alarmsRegistered = 1;
        }

        private static void populatePulledAtomStatsTestData(StatsLog.StatsdStatsReport testReport) {
            testReport.pulledAtomStats =
                    new StatsLog.StatsdStatsReport.PulledAtomStats[PULLED_ATOM_STATS_COUNT];

            for (int i = 0; i < PULLED_ATOM_STATS_COUNT; i++) {
                testReport.pulledAtomStats[i] = new StatsLog.StatsdStatsReport.PulledAtomStats();
                int fieldValue = i + 1;
                testReport.pulledAtomStats[i].atomId = fieldValue++;
                testReport.pulledAtomStats[i].totalPull = fieldValue++;
                testReport.pulledAtomStats[i].totalPullFromCache = fieldValue++;
                testReport.pulledAtomStats[i].minPullIntervalSec = fieldValue++;
                testReport.pulledAtomStats[i].averagePullTimeNanos = fieldValue++;
                testReport.pulledAtomStats[i].maxPullTimeNanos = fieldValue++;
                testReport.pulledAtomStats[i].averagePullDelayNanos = fieldValue++;
                testReport.pulledAtomStats[i].dataError = fieldValue++;
                testReport.pulledAtomStats[i].pullTimeout = fieldValue++;
                testReport.pulledAtomStats[i].pullExceedMaxDelay = fieldValue++;
                testReport.pulledAtomStats[i].pullFailed = fieldValue++;
                testReport.pulledAtomStats[i].emptyData = fieldValue++;
                testReport.pulledAtomStats[i].registeredCount = fieldValue++;
                testReport.pulledAtomStats[i].unregisteredCount = fieldValue++;
                testReport.pulledAtomStats[i].atomErrorCount = fieldValue++;
                testReport.pulledAtomStats[i].binderCallFailed = fieldValue++;
                testReport.pulledAtomStats[i].failedUidProviderNotFound = fieldValue++;
                testReport.pulledAtomStats[i].pullerNotFound = fieldValue++;
            }
        }

        private static void populateAtomMetricStatsTestData(StatsLog.StatsdStatsReport testReport) {
            testReport.atomMetricStats =
                    new StatsLog.StatsdStatsReport.AtomMetricStats[ATOM_METRIC_STATS_COUNT];
            for (int i = 0; i < ATOM_METRIC_STATS_COUNT; i++) {
                testReport.atomMetricStats[i] = new StatsLog.StatsdStatsReport.AtomMetricStats();
                int fieldValue = i + 1;
                testReport.atomMetricStats[i].metricId = fieldValue++;
                testReport.atomMetricStats[i].hardDimensionLimitReached = fieldValue++;
                testReport.atomMetricStats[i].lateLogEventSkipped = fieldValue++;
                testReport.atomMetricStats[i].skippedForwardBuckets = fieldValue++;
                testReport.atomMetricStats[i].badValueType = fieldValue++;
                testReport.atomMetricStats[i].conditionChangeInNextBucket = fieldValue++;
                testReport.atomMetricStats[i].invalidatedBucket = fieldValue++;
                testReport.atomMetricStats[i].bucketDropped = fieldValue++;
                testReport.atomMetricStats[i].minBucketBoundaryDelayNs = fieldValue++;
                testReport.atomMetricStats[i].maxBucketBoundaryDelayNs = fieldValue++;
                testReport.atomMetricStats[i].bucketUnknownCondition = fieldValue++;
                testReport.atomMetricStats[i].bucketCount = fieldValue++;
            }
        }

        private static void populateDetectedLogLossStatsTestData(
                StatsLog.StatsdStatsReport testReport) {
            testReport.detectedLogLoss =
                    new StatsLog.StatsdStatsReport.LogLossStats[DETECTED_LOG_LOSS_STATS_COUNT];

            for (int i = 0; i < DETECTED_LOG_LOSS_STATS_COUNT; i++) {
                testReport.detectedLogLoss[i] = new StatsLog.StatsdStatsReport.LogLossStats();
                int fieldValue = i + 1;
                testReport.detectedLogLoss[i].detectedTimeSec = fieldValue++;
                testReport.detectedLogLoss[i].count = fieldValue++;
                testReport.detectedLogLoss[i].lastError = fieldValue++;
                testReport.detectedLogLoss[i].lastTag = fieldValue++;
                testReport.detectedLogLoss[i].uid = fieldValue++;
                testReport.detectedLogLoss[i].pid = fieldValue++;
            }
        }

        private static void populateEventQueueOverflowStatsTestData(
                StatsLog.StatsdStatsReport testReport) {
            testReport.queueOverflow = new StatsLog.StatsdStatsReport.EventQueueOverflow();
            int fieldValue = 1;
            testReport.queueOverflow.count = fieldValue++;
            testReport.queueOverflow.minQueueHistoryNs = fieldValue++;
            testReport.queueOverflow.maxQueueHistoryNs = fieldValue++;
        }

        @Override
        public StatsLog.StatsdStatsReport getStatsdStatsReport() {
            return testReport;
        }
    }

    private static class TestEmptyStatsdHelper implements StatsdStatsHelper.IStatsdHelper {

        StatsLog.StatsdStatsReport testReport = new StatsLog.StatsdStatsReport();

        public TestEmptyStatsdHelper() {}

        @Override
        public StatsLog.StatsdStatsReport getStatsdStatsReport() {
            return testReport;
        }
    }

    private static void verifyAtomStats(Map<String, Long> result, int atomsCount) {
        for (int i = 0; i < atomsCount; i++) {
            final String metricKeyPrefix =
                    MetricUtility.constructKey(
                            StatsdStatsHelper.STATSDSTATS_PREFIX,
                            StatsdStatsHelper.ATOM_STATS_PREFIX,
                            String.valueOf(i + 1));
            int fieldValue = i + 2;
            assertEquals(
                    result.get(MetricUtility.constructKey(metricKeyPrefix, "count")),
                    Long.valueOf(fieldValue++));
            assertEquals(
                    result.get(MetricUtility.constructKey(metricKeyPrefix, "error_count")),
                    Long.valueOf(fieldValue++));
        }
    }

    private static void verifyConfigAlertStats(
            Map<String, Long> result, String metricKeyPrefix, long count) {
        for (long i = 0; i < count; i++) {
            final String metricKey =
                    MetricUtility.constructKey(
                            metricKeyPrefix,
                            StatsdStatsHelper.ALERT_STATS_PREFIX,
                            String.valueOf(i + 1),
                            "alerted_times");
            assertEquals(result.get(metricKey), Long.valueOf(i + 2));
        }
    }

    private static void verifyConfigMatcherStats(
            Map<String, Long> result, String metricKeyPrefix, long count) {
        for (long i = 0; i < count; i++) {
            final String metricKey =
                    MetricUtility.constructKey(
                            metricKeyPrefix,
                            StatsdStatsHelper.MATCHER_STATS_PREFIX,
                            String.valueOf(i + 1),
                            "matched_times");
            assertEquals(result.get(metricKey), Long.valueOf(i + 2));
        }
    }

    private static void verifyConfigConditionStats(
            Map<String, Long> result, String metricKeyPrefix, long count) {
        for (long i = 0; i < count; i++) {
            final String metricKey =
                    MetricUtility.constructKey(
                            metricKeyPrefix,
                            StatsdStatsHelper.CONDITION_STATS_PREFIX,
                            String.valueOf(i + 1),
                            "max_tuple_counts");
            assertEquals(result.get(metricKey), Long.valueOf(i + 2));
        }
    }

    private static void verifyConfigMetricStats(
            Map<String, Long> result, String metricKeyPrefix, long count) {
        for (long i = 0; i < count; i++) {
            final String metricKey =
                    MetricUtility.constructKey(
                            metricKeyPrefix,
                            StatsdStatsHelper.METRIC_STATS_PREFIX,
                            String.valueOf(i + 1),
                            "max_tuple_counts");
            assertEquals(result.get(metricKey), Long.valueOf(i + 2));
        }
    }

    private static void verifyConfigStats(
            Map<String, Long> result,
            int configStatsCount,
            int configStatsMetricCount,
            int configStatsConditionCount,
            int configStatsMatcherCount,
            int configStatsAlertCount) {

        for (int i = 0; i < configStatsCount; i++) {
            final String metricKeyPrefix =
                    MetricUtility.constructKey(
                            StatsdStatsHelper.STATSDSTATS_PREFIX,
                            StatsdStatsHelper.CONFIG_STATS_PREFIX,
                            String.valueOf(i + 1));

            final String metricCountKey =
                    MetricUtility.constructKey(metricKeyPrefix, "metric_count");
            assertEquals(result.get(metricCountKey), Long.valueOf(configStatsMetricCount));
            verifyConfigMetricStats(result, metricKeyPrefix, result.get(metricCountKey));
            final String conditionCountKey =
                    MetricUtility.constructKey(metricKeyPrefix, "condition_count");
            assertEquals(result.get(conditionCountKey), Long.valueOf(configStatsConditionCount));
            verifyConfigConditionStats(result, metricKeyPrefix, result.get(conditionCountKey));
            final String matcherCountKey =
                    MetricUtility.constructKey(metricKeyPrefix, "matcher_count");
            assertEquals(result.get(matcherCountKey), Long.valueOf(configStatsMatcherCount));
            verifyConfigMatcherStats(result, metricKeyPrefix, result.get(matcherCountKey));
            final String alertCountKey = MetricUtility.constructKey(metricKeyPrefix, "alert_count");
            assertEquals(result.get(alertCountKey), Long.valueOf(configStatsAlertCount));
            verifyConfigAlertStats(result, metricKeyPrefix, result.get(alertCountKey));
        }
    }

    private static void verifyAnomalyAlarmStats(Map<String, Long> result) {
        final String metricKeyPrefix =
                MetricUtility.constructKey(
                        StatsdStatsHelper.STATSDSTATS_PREFIX,
                        StatsdStatsHelper.ANOMALY_ALARM_STATS_PREFIX);
        final String metricKey = MetricUtility.constructKey(metricKeyPrefix, "alarms_registered");
        assertEquals(result.get(metricKey), Long.valueOf(1));
    }

    private static void verifyPulledAtomStats(Map<String, Long> result, int pulledAtomStatsCount) {
        for (int i = 0; i < pulledAtomStatsCount; i++) {
            int fieldValue = i + 1;
            final String metricKeyPrefix =
                    MetricUtility.constructKey(
                            StatsdStatsHelper.STATSDSTATS_PREFIX,
                            StatsdStatsHelper.PULLED_ATOM_STATS_PREFIX,
                            String.valueOf(fieldValue++));
            assertEquals(
                    result.get(MetricUtility.constructKey(metricKeyPrefix, "total_pull")),
                    Long.valueOf(fieldValue++));
            assertEquals(
                    result.get(
                            MetricUtility.constructKey(metricKeyPrefix, "total_pull_from_cache")),
                    Long.valueOf(fieldValue++));
            assertEquals(
                    result.get(
                            MetricUtility.constructKey(metricKeyPrefix, "min_pull_interval_sec")),
                    Long.valueOf(fieldValue++));
            assertEquals(
                    result.get(
                            MetricUtility.constructKey(metricKeyPrefix, "average_pull_time_nanos")),
                    Long.valueOf(fieldValue++));
            assertEquals(
                    result.get(MetricUtility.constructKey(metricKeyPrefix, "max_pull_time_nanos")),
                    Long.valueOf(fieldValue++));
            assertEquals(
                    result.get(
                            MetricUtility.constructKey(
                                    metricKeyPrefix, "average_pull_delay_nanos")),
                    Long.valueOf(fieldValue++));
            assertEquals(
                    result.get(MetricUtility.constructKey(metricKeyPrefix, "data_error")),
                    Long.valueOf(fieldValue++));
            assertEquals(
                    result.get(MetricUtility.constructKey(metricKeyPrefix, "pull_timeout")),
                    Long.valueOf(fieldValue++));
            assertEquals(
                    result.get(
                            MetricUtility.constructKey(metricKeyPrefix, "pull_exceed_max_delay")),
                    Long.valueOf(fieldValue++));
            assertEquals(
                    result.get(MetricUtility.constructKey(metricKeyPrefix, "pull_failed")),
                    Long.valueOf(fieldValue++));
            assertEquals(
                    result.get(MetricUtility.constructKey(metricKeyPrefix, "empty_data")),
                    Long.valueOf(fieldValue++));
            assertEquals(
                    result.get(
                            MetricUtility.constructKey(metricKeyPrefix, "pull_registered_count")),
                    Long.valueOf(fieldValue++));
            assertEquals(
                    result.get(
                            MetricUtility.constructKey(metricKeyPrefix, "pull_unregistered_count")),
                    Long.valueOf(fieldValue++));
            assertEquals(
                    result.get(MetricUtility.constructKey(metricKeyPrefix, "atom_error_count")),
                    Long.valueOf(fieldValue++));
            assertEquals(
                    result.get(MetricUtility.constructKey(metricKeyPrefix, "binder_call_failed")),
                    Long.valueOf(fieldValue++));
            assertEquals(
                    result.get(
                            MetricUtility.constructKey(
                                    metricKeyPrefix, "failed_uid_provider_not_found")),
                    Long.valueOf(fieldValue++));
            assertEquals(
                    result.get(MetricUtility.constructKey(metricKeyPrefix, "puller_not_found")),
                    Long.valueOf(fieldValue++));
        }
    }

    private static void verifyAtomMetricStats(Map<String, Long> result, int atomMetricCount) {
        for (int i = 0; i < atomMetricCount; i++) {
            int fieldValue = i + 1;
            final String metricKeyPrefix =
                    MetricUtility.constructKey(
                            StatsdStatsHelper.STATSDSTATS_PREFIX,
                            StatsdStatsHelper.ATOM_METRIC_STATS_PREFIX,
                            String.valueOf(fieldValue++));
            assertEquals(
                    result.get(
                            MetricUtility.constructKey(
                                    metricKeyPrefix, "hard_dimension_limit_reached")),
                    Long.valueOf(fieldValue++));
            assertEquals(
                    result.get(
                            MetricUtility.constructKey(metricKeyPrefix, "late_log_event_skipped")),
                    Long.valueOf(fieldValue++));
            assertEquals(
                    result.get(
                            MetricUtility.constructKey(metricKeyPrefix, "skipped_forward_buckets")),
                    Long.valueOf(fieldValue++));
            assertEquals(
                    result.get(MetricUtility.constructKey(metricKeyPrefix, "bad_value_type")),
                    Long.valueOf(fieldValue++));
            assertEquals(
                    result.get(
                            MetricUtility.constructKey(
                                    metricKeyPrefix, "condition_change_in_next_bucket")),
                    Long.valueOf(fieldValue++));
            assertEquals(
                    result.get(MetricUtility.constructKey(metricKeyPrefix, "invalidated_bucket")),
                    Long.valueOf(fieldValue++));
            assertEquals(
                    result.get(MetricUtility.constructKey(metricKeyPrefix, "bucket_dropped")),
                    Long.valueOf(fieldValue++));
            assertEquals(
                    result.get(
                            MetricUtility.constructKey(
                                    metricKeyPrefix, "min_bucket_boundary_delay_ns")),
                    Long.valueOf(fieldValue++));
            assertEquals(
                    result.get(
                            MetricUtility.constructKey(
                                    metricKeyPrefix, "max_bucket_boundary_delay_ns")),
                    Long.valueOf(fieldValue++));
            assertEquals(
                    result.get(
                            MetricUtility.constructKey(
                                    metricKeyPrefix, "bucket_unknown_condition")),
                    Long.valueOf(fieldValue++));
            assertEquals(
                    result.get(MetricUtility.constructKey(metricKeyPrefix, "bucket_count")),
                    Long.valueOf(fieldValue++));
        }
    }

    private static void verifyDetectedLogLossStats(
            Map<String, Long> result, int logLossStatsCount) {
        for (int i = 0; i < logLossStatsCount; i++) {
            int fieldValue = i + 1;
            final String metricKeyPrefix =
                    MetricUtility.constructKey(
                            StatsdStatsHelper.STATSDSTATS_PREFIX,
                            StatsdStatsHelper.DETECTED_LOG_LOSS_STATS_PREFIX,
                            String.valueOf(fieldValue++));
            assertEquals(
                    result.get(MetricUtility.constructKey(metricKeyPrefix, "count")),
                    Long.valueOf(fieldValue++));
            assertEquals(
                    result.get(MetricUtility.constructKey(metricKeyPrefix, "last_error")),
                    Long.valueOf(fieldValue++));
            assertEquals(
                    result.get(MetricUtility.constructKey(metricKeyPrefix, "last_tag")),
                    Long.valueOf(fieldValue++));
            assertEquals(
                    result.get(MetricUtility.constructKey(metricKeyPrefix, "uid")),
                    Long.valueOf(fieldValue++));
            assertEquals(
                    result.get(MetricUtility.constructKey(metricKeyPrefix, "pid")),
                    Long.valueOf(fieldValue++));
        }
    }

    private static void verifyEventQueueOverfowStats(Map<String, Long> result) {
        final String metricKeyPrefix =
                MetricUtility.constructKey(
                        StatsdStatsHelper.STATSDSTATS_PREFIX,
                        StatsdStatsHelper.EVENT_QUEUE_OVERFLOW_STATS_PREFIX);

        int fieldValue = 1;
        assertEquals(
                result.get(MetricUtility.constructKey(metricKeyPrefix, "count")),
                Long.valueOf(fieldValue++));
        assertEquals(
                result.get(MetricUtility.constructKey(metricKeyPrefix, "min_queue_history_nanos")),
                Long.valueOf(fieldValue++));
        assertEquals(
                result.get(MetricUtility.constructKey(metricKeyPrefix, "max_queue_history_nanos")),
                Long.valueOf(fieldValue++));
    }

    private static void verifySummaryMetrics(Map<String, Long> result) {
        final String metrics[] = {
            "statsdstats_atom_stats_count",
            "statsdstats_atom_stats_error_count",
            "statsdstats_pulled_atom_stats_pull_failed",
            "statsdstats_pulled_atom_stats_pull_timeout",
            "statsdstats_pulled_atom_stats_pull_exceed_max_delay",
            "statsdstats_pulled_atom_stats_empty_data",
            "statsdstats_pulled_atom_stats_atom_error_count",
            "statsdstats_pulled_atom_stats_binder_call_failed",
            "statsdstats_pulled_atom_stats_failed_uid_provider_not_found",
            "statsdstats_pulled_atom_stats_puller_not_found",
            "statsdstats_pulled_atom_stats_total_pull",
            "statsdstats_pulled_atom_stats_total_pull_from_cache",
            "statsdstats_atom_metric_stats_hard_dimension_limit_reached",
            "statsdstats_atom_metric_stats_late_log_event_skipped",
            "statsdstats_atom_metric_stats_skipped_forward_buckets",
            "statsdstats_atom_metric_stats_bad_value_type",
            "statsdstats_atom_metric_stats_condition_change_in_next_bucket",
            "statsdstats_atom_metric_stats_invalidated_bucket",
            "statsdstats_atom_metric_stats_bucket_dropped",
            "statsdstats_atom_metric_stats_bucket_unknown_condition"
        };
        for (int i = 0; i < metrics.length; i++) {
            assertNotNull(result.get(metrics[i]));
        }
    }

    @Test
    public void testNonEmptyReport() throws Exception {
        StatsdStatsHelper.IStatsdHelper statsdHelper = new TestNonEmptyStatsdHelper();
        StatsdStatsHelper statsdStatsHelper = new StatsdStatsHelper(statsdHelper);

        assertTrue(statsdStatsHelper.startCollecting());
        final Map<String, Long> result = statsdStatsHelper.getMetrics();
        verifyAtomStats(result, TestNonEmptyStatsdHelper.ATOM_STATS_COUNT);
        verifyConfigStats(
                result,
                TestNonEmptyStatsdHelper.CONFIG_STATS_COUNT,
                TestNonEmptyStatsdHelper.CONFIG_STATS_METRIC_COUNT,
                TestNonEmptyStatsdHelper.CONFIG_STATS_CONDITION_COUNT,
                TestNonEmptyStatsdHelper.CONFIG_STATS_MATCHER_COUNT,
                TestNonEmptyStatsdHelper.CONFIG_STATS_ALERT_COUNT);
        verifyAnomalyAlarmStats(result);
        verifyPulledAtomStats(result, TestNonEmptyStatsdHelper.PULLED_ATOM_STATS_COUNT);
        verifyAtomMetricStats(result, TestNonEmptyStatsdHelper.ATOM_METRIC_STATS_COUNT);
        verifyDetectedLogLossStats(result, TestNonEmptyStatsdHelper.DETECTED_LOG_LOSS_STATS_COUNT);
        verifyEventQueueOverfowStats(result);
        verifySummaryMetrics(result);
        assertTrue(statsdStatsHelper.stopCollecting());
    }

    @Test
    public void testEmptyReport() throws Exception {
        StatsdStatsHelper.IStatsdHelper statsdHelper = new TestEmptyStatsdHelper();
        StatsdStatsHelper statsdStatsHelper = new StatsdStatsHelper(statsdHelper);

        assertTrue(statsdStatsHelper.startCollecting());
        final Map<String, Long> result = statsdStatsHelper.getMetrics();
        verifySummaryMetrics(result);
        assertTrue(statsdStatsHelper.stopCollecting());
    }
}
