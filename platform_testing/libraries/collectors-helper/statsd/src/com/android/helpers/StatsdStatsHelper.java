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

package com.android.helpers;

import androidx.annotation.VisibleForTesting;

import com.android.os.nano.StatsLog;

import java.util.HashMap;
import java.util.Map;

/**
 * StatsdStatsHelper consist of helper methods to set the Statsd metadata collection and retrieve
 * the necessary information from statsd.
 */
public class StatsdStatsHelper implements ICollectorHelper<Long> {

    static final String STATSDSTATS_PREFIX = "statsdstats";
    static final String ATOM_STATS_PREFIX = "atom_stats";
    static final String MATCHER_STATS_PREFIX = "matcher_stats";
    static final String CONDITION_STATS_PREFIX = "condition_stats";
    static final String METRIC_STATS_PREFIX = "metric_stats";
    static final String ALERT_STATS_PREFIX = "alert_stats";
    static final String CONFIG_STATS_PREFIX = "config_stats";
    static final String ANOMALY_ALARM_STATS_PREFIX = "anomaly_alarm_stats";
    static final String PULLED_ATOM_STATS_PREFIX = "pulled_atom_stats";
    static final String ATOM_METRIC_STATS_PREFIX = "atom_metric_stats";
    static final String DETECTED_LOG_LOSS_STATS_PREFIX = "detected_log_loss_stats";
    static final String EVENT_QUEUE_OVERFLOW_STATS_PREFIX = "event_queue_overflow_stats";

    interface IStatsdHelper {
        StatsLog.StatsdStatsReport getStatsdStatsReport();
    }

    private static class DefaultStatsdHelper implements IStatsdHelper {

        private StatsdHelper mStatsdHelper = new StatsdHelper();

        @Override
        public StatsLog.StatsdStatsReport getStatsdStatsReport() {
            return mStatsdHelper.getStatsdStatsReport();
        }
    }

    private IStatsdHelper mStatsdHelper = new DefaultStatsdHelper();

    public StatsdStatsHelper() {}

    /**
     * Constructor to simulate an externally provided statsd helper instance. Should not be used
     * except for testing.
     */
    @VisibleForTesting
    StatsdStatsHelper(IStatsdHelper helper) {
        mStatsdHelper = helper;
    }

    /** Resets statsd metadata */
    @Override
    public boolean startCollecting() {
        // TODO: http://b/204890512 implement metadata reset
        return true;
    }

    /** Collect the statsd metadata accumulated during the test run. */
    @Override
    public Map<String, Long> getMetrics() {
        Map<String, Long> resultMap = new HashMap<>();

        final StatsLog.StatsdStatsReport report = mStatsdHelper.getStatsdStatsReport();
        populateAtomStats(report.atomStats, resultMap);
        populateConfigStats(report.configStats, resultMap);
        populateAnomalyAlarmStats(report.anomalyAlarmStats, resultMap);
        populatePulledAtomStats(report.pulledAtomStats, resultMap);
        populateAtomMetricStats(report.atomMetricStats, resultMap);
        populateDetectedLogLossStats(report.detectedLogLoss, resultMap);
        populateEventQueueOverflowStats(report.queueOverflow, resultMap);

        return resultMap;
    }

    @Override
    public boolean stopCollecting() {
        return true;
    }

    private static void populateAtomStats(
            StatsLog.StatsdStatsReport.AtomStats[] stats, Map<String, Long> resultMap) {
        final String metricKeyPrefix =
                MetricUtility.constructKey(STATSDSTATS_PREFIX, ATOM_STATS_PREFIX);

        int summaryCount = 0;
        int summaryErrorCount = 0;

        for (final StatsLog.StatsdStatsReport.AtomStats dataItem : stats) {
            final String metricKeyPrefixWithTag =
                    MetricUtility.constructKey(metricKeyPrefix, String.valueOf(dataItem.tag));

            resultMap.put(
                    MetricUtility.constructKey(metricKeyPrefixWithTag, "count"),
                    Long.valueOf(dataItem.count));
            resultMap.put(
                    MetricUtility.constructKey(metricKeyPrefixWithTag, "error_count"),
                    Long.valueOf(dataItem.errorCount));

            summaryCount += dataItem.count;
            summaryErrorCount += dataItem.errorCount;
        }

        resultMap.put(
                MetricUtility.constructKey(metricKeyPrefix, "count"), Long.valueOf(summaryCount));
        resultMap.put(
                MetricUtility.constructKey(metricKeyPrefix, "error_count"),
                Long.valueOf(summaryErrorCount));
    }

    private static void populateConfigStats(
            StatsLog.StatsdStatsReport.ConfigStats[] configStats, Map<String, Long> resultMap) {
        final String metricKeyPrefix =
                MetricUtility.constructKey(STATSDSTATS_PREFIX, CONFIG_STATS_PREFIX);

        for (final StatsLog.StatsdStatsReport.ConfigStats dataItem : configStats) {
            final String metricKeyPrefixWithTag =
                    MetricUtility.constructKey(metricKeyPrefix, String.valueOf(dataItem.id));

            resultMap.put(
                    MetricUtility.constructKey(metricKeyPrefixWithTag, "metric_count"),
                    Long.valueOf(dataItem.metricCount));
            resultMap.put(
                    MetricUtility.constructKey(metricKeyPrefixWithTag, "condition_count"),
                    Long.valueOf(dataItem.conditionCount));
            resultMap.put(
                    MetricUtility.constructKey(metricKeyPrefixWithTag, "matcher_count"),
                    Long.valueOf(dataItem.matcherCount));
            resultMap.put(
                    MetricUtility.constructKey(metricKeyPrefixWithTag, "alert_count"),
                    Long.valueOf(dataItem.alertCount));

            populateMatcherStats(
                    dataItem.matcherStats, resultMap, metricKeyPrefixWithTag, metricKeyPrefix);
            populateConditionStats(
                    dataItem.conditionStats, resultMap, metricKeyPrefixWithTag, metricKeyPrefix);
            populateMetricStats(
                    dataItem.metricStats, resultMap, metricKeyPrefixWithTag, metricKeyPrefix);
            populateAlertStats(
                    dataItem.alertStats, resultMap, metricKeyPrefixWithTag, metricKeyPrefix);
        }
    }

    private static void populateMetricStats(
            StatsLog.StatsdStatsReport.MetricStats[] stats,
            Map<String, Long> resultMap,
            String metricKeyPrefixWithTag,
            String metricKeyPrefix) {
        int summaryCount = 0;

        for (final StatsLog.StatsdStatsReport.MetricStats dataItem : stats) {
            final String metricKey =
                    MetricUtility.constructKey(
                            metricKeyPrefixWithTag,
                            METRIC_STATS_PREFIX,
                            String.valueOf(dataItem.id),
                            "max_tuple_counts");
            resultMap.put(metricKey, Long.valueOf(dataItem.maxTupleCounts));

            summaryCount += dataItem.maxTupleCounts;
        }

        final String metricKey =
                MetricUtility.constructKey(
                        metricKeyPrefix, METRIC_STATS_PREFIX, "max_tuple_counts");
        resultMap.put(metricKey, Long.valueOf(summaryCount));
    }

    private static void populateConditionStats(
            StatsLog.StatsdStatsReport.ConditionStats[] stats,
            Map<String, Long> resultMap,
            String metricKeyPrefixWithTag,
            String metricKeyPrefix) {
        int summaryCount = 0;

        for (final StatsLog.StatsdStatsReport.ConditionStats dataItem : stats) {
            final String metricKey =
                    MetricUtility.constructKey(
                            metricKeyPrefixWithTag,
                            CONDITION_STATS_PREFIX,
                            String.valueOf(dataItem.id),
                            "max_tuple_counts");
            resultMap.put(metricKey, Long.valueOf(dataItem.maxTupleCounts));

            summaryCount += dataItem.maxTupleCounts;
        }

        final String metricKey =
                MetricUtility.constructKey(
                        metricKeyPrefix, CONDITION_STATS_PREFIX, "max_tuple_counts");
        resultMap.put(metricKey, Long.valueOf(summaryCount));
    }

    private static void populateMatcherStats(
            StatsLog.StatsdStatsReport.MatcherStats[] stats,
            Map<String, Long> resultMap,
            String metricKeyPrefixWithTag,
            String metricKeyPrefix) {
        int summaryCount = 0;

        for (final StatsLog.StatsdStatsReport.MatcherStats dataItem : stats) {
            final String metricKey =
                    MetricUtility.constructKey(
                            metricKeyPrefixWithTag,
                            MATCHER_STATS_PREFIX,
                            String.valueOf(dataItem.id),
                            "matched_times");
            resultMap.put(metricKey, Long.valueOf(dataItem.matchedTimes));

            final String sumPerIdMetricKey =
                    MetricUtility.constructKey(
                            metricKeyPrefix,
                            MATCHER_STATS_PREFIX,
                            String.valueOf(dataItem.id),
                            "matched_times");
            resultMap.merge(sumPerIdMetricKey, Long.valueOf(dataItem.matchedTimes), Long::sum);

            summaryCount += dataItem.matchedTimes;
        }

        final String metricKey =
                MetricUtility.constructKey(metricKeyPrefix, MATCHER_STATS_PREFIX, "matched_times");
        resultMap.merge(metricKey, Long.valueOf(summaryCount), Long::sum);
    }

    private static void populateAlertStats(
            StatsLog.StatsdStatsReport.AlertStats[] stats,
            Map<String, Long> resultMap,
            String metricKeyPrefixWithTag,
            String metricKeyPrefix) {
        int summaryCount = 0;

        for (final StatsLog.StatsdStatsReport.AlertStats dataItem : stats) {
            final String metricKey =
                    MetricUtility.constructKey(
                            metricKeyPrefixWithTag,
                            ALERT_STATS_PREFIX,
                            String.valueOf(dataItem.id),
                            "alerted_times");
            resultMap.put(metricKey, Long.valueOf(dataItem.alertedTimes));

            summaryCount += dataItem.alertedTimes;
        }

        final String metricKey =
                MetricUtility.constructKey(metricKeyPrefix, ALERT_STATS_PREFIX, "alerted_times");
        resultMap.merge(metricKey, Long.valueOf(summaryCount), Long::sum);
    }

    private static void populateAnomalyAlarmStats(
            StatsLog.StatsdStatsReport.AnomalyAlarmStats anomalyAlarmStats,
            Map<String, Long> resultMap) {
        if (anomalyAlarmStats == null) {
            return;
        }
        final String metricKey =
                MetricUtility.constructKey(
                        STATSDSTATS_PREFIX, ANOMALY_ALARM_STATS_PREFIX, "alarms_registered");
        resultMap.put(metricKey, Long.valueOf(anomalyAlarmStats.alarmsRegistered));
    }

    private static void populatePulledAtomStats(
            StatsLog.StatsdStatsReport.PulledAtomStats[] stats, Map<String, Long> resultMap) {
        final String metricKeyPrefix =
                MetricUtility.constructKey(STATSDSTATS_PREFIX, PULLED_ATOM_STATS_PREFIX);

        long summaryTotalPull = 0;
        long summaryTotalPullFromCache = 0;
        long summaryDataError = 0;
        long summaryPullTimeout = 0;
        long summaryExceedMaxDelay = 0;
        long summaryFailed = 0;
        long summaryEmptyData = 0;
        long summaryAtomErrorCount = 0;
        long summaryBinderCallFailed = 0;
        long summaryFailedUIDProviderNotFound = 0;
        long summaryPullerNotFound = 0;

        for (final StatsLog.StatsdStatsReport.PulledAtomStats dataItem : stats) {
            final String metricKeyWithTag =
                    MetricUtility.constructKey(metricKeyPrefix, String.valueOf(dataItem.atomId));
            resultMap.put(
                    MetricUtility.constructKey(metricKeyWithTag, "total_pull"),
                    Long.valueOf(dataItem.totalPull));
            resultMap.put(
                    MetricUtility.constructKey(metricKeyWithTag, "total_pull_from_cache"),
                    Long.valueOf(dataItem.totalPullFromCache));
            resultMap.put(
                    MetricUtility.constructKey(metricKeyWithTag, "min_pull_interval_sec"),
                    Long.valueOf(dataItem.minPullIntervalSec));
            resultMap.put(
                    MetricUtility.constructKey(metricKeyWithTag, "average_pull_time_nanos"),
                    Long.valueOf(dataItem.averagePullTimeNanos));
            resultMap.put(
                    MetricUtility.constructKey(metricKeyWithTag, "max_pull_time_nanos"),
                    Long.valueOf(dataItem.maxPullTimeNanos));
            resultMap.put(
                    MetricUtility.constructKey(metricKeyWithTag, "average_pull_delay_nanos"),
                    Long.valueOf(dataItem.averagePullDelayNanos));
            resultMap.put(
                    MetricUtility.constructKey(metricKeyWithTag, "data_error"),
                    Long.valueOf(dataItem.dataError));
            resultMap.put(
                    MetricUtility.constructKey(metricKeyWithTag, "pull_timeout"),
                    Long.valueOf(dataItem.pullTimeout));
            resultMap.put(
                    MetricUtility.constructKey(metricKeyWithTag, "pull_exceed_max_delay"),
                    Long.valueOf(dataItem.pullExceedMaxDelay));
            resultMap.put(
                    MetricUtility.constructKey(metricKeyWithTag, "pull_failed"),
                    Long.valueOf(dataItem.pullFailed));
            resultMap.put(
                    MetricUtility.constructKey(metricKeyWithTag, "empty_data"),
                    Long.valueOf(dataItem.emptyData));
            resultMap.put(
                    MetricUtility.constructKey(metricKeyWithTag, "pull_registered_count"),
                    Long.valueOf(dataItem.registeredCount));
            resultMap.put(
                    MetricUtility.constructKey(metricKeyWithTag, "pull_unregistered_count"),
                    Long.valueOf(dataItem.unregisteredCount));
            resultMap.put(
                    MetricUtility.constructKey(metricKeyWithTag, "atom_error_count"),
                    Long.valueOf(dataItem.atomErrorCount));
            resultMap.put(
                    MetricUtility.constructKey(metricKeyWithTag, "binder_call_failed"),
                    Long.valueOf(dataItem.binderCallFailed));
            resultMap.put(
                    MetricUtility.constructKey(metricKeyWithTag, "failed_uid_provider_not_found"),
                    Long.valueOf(dataItem.failedUidProviderNotFound));
            resultMap.put(
                    MetricUtility.constructKey(metricKeyWithTag, "puller_not_found"),
                    Long.valueOf(dataItem.pullerNotFound));

            summaryTotalPull += dataItem.totalPull;
            summaryTotalPullFromCache += dataItem.totalPullFromCache;
            summaryDataError += dataItem.dataError;
            summaryPullTimeout += dataItem.pullTimeout;
            summaryExceedMaxDelay += dataItem.pullExceedMaxDelay;
            summaryFailed += dataItem.pullFailed;
            summaryEmptyData += dataItem.emptyData;
            summaryAtomErrorCount += dataItem.atomErrorCount;
            summaryBinderCallFailed += dataItem.binderCallFailed;
            summaryFailedUIDProviderNotFound += dataItem.failedUidProviderNotFound;
            summaryPullerNotFound += dataItem.pullerNotFound;
        }

        resultMap.put(MetricUtility.constructKey(metricKeyPrefix, "total_pull"), summaryTotalPull);
        resultMap.put(
                MetricUtility.constructKey(metricKeyPrefix, "total_pull_from_cache"),
                summaryTotalPullFromCache);
        resultMap.put(MetricUtility.constructKey(metricKeyPrefix, "data_error"), summaryDataError);
        resultMap.put(
                MetricUtility.constructKey(metricKeyPrefix, "pull_timeout"), summaryPullTimeout);
        resultMap.put(
                MetricUtility.constructKey(metricKeyPrefix, "pull_exceed_max_delay"),
                summaryExceedMaxDelay);
        resultMap.put(MetricUtility.constructKey(metricKeyPrefix, "pull_failed"), summaryFailed);
        resultMap.put(MetricUtility.constructKey(metricKeyPrefix, "empty_data"), summaryEmptyData);
        resultMap.put(
                MetricUtility.constructKey(metricKeyPrefix, "atom_error_count"),
                summaryAtomErrorCount);
        resultMap.put(
                MetricUtility.constructKey(metricKeyPrefix, "binder_call_failed"),
                summaryBinderCallFailed);
        resultMap.put(
                MetricUtility.constructKey(metricKeyPrefix, "failed_uid_provider_not_found"),
                summaryFailedUIDProviderNotFound);
        resultMap.put(
                MetricUtility.constructKey(metricKeyPrefix, "puller_not_found"),
                summaryPullerNotFound);
    }

    private static void populateAtomMetricStats(
            StatsLog.StatsdStatsReport.AtomMetricStats[] stats, Map<String, Long> resultMap) {
        final String metricKeyPrefix =
                MetricUtility.constructKey(STATSDSTATS_PREFIX, ATOM_METRIC_STATS_PREFIX);

        long summaryHardDimensionLimitReached = 0;
        long summaryLateLogEventSkipped = 0;
        long summarySkippedForwardBuckets = 0;
        long summaryBadValueType = 0;
        long summaryConditionChangeInNextBucket = 0;
        long summaryInvalidatedBucket = 0;
        long summaryBucketDropped = 0;
        long summaryBucketUnknownCondition = 0;

        for (StatsLog.StatsdStatsReport.AtomMetricStats dataItem : stats) {
            final String metricKeyPrefixWithTag =
                    MetricUtility.constructKey(metricKeyPrefix, String.valueOf(dataItem.metricId));

            resultMap.put(
                    MetricUtility.constructKey(
                            metricKeyPrefixWithTag, "hard_dimension_limit_reached"),
                    dataItem.hardDimensionLimitReached);
            resultMap.put(
                    MetricUtility.constructKey(metricKeyPrefixWithTag, "late_log_event_skipped"),
                    dataItem.lateLogEventSkipped);
            resultMap.put(
                    MetricUtility.constructKey(metricKeyPrefixWithTag, "skipped_forward_buckets"),
                    dataItem.skippedForwardBuckets);
            resultMap.put(
                    MetricUtility.constructKey(metricKeyPrefixWithTag, "bad_value_type"),
                    dataItem.badValueType);
            resultMap.put(
                    MetricUtility.constructKey(
                            metricKeyPrefixWithTag, "condition_change_in_next_bucket"),
                    dataItem.conditionChangeInNextBucket);
            resultMap.put(
                    MetricUtility.constructKey(metricKeyPrefixWithTag, "invalidated_bucket"),
                    dataItem.invalidatedBucket);
            resultMap.put(
                    MetricUtility.constructKey(metricKeyPrefixWithTag, "bucket_dropped"),
                    dataItem.bucketDropped);
            resultMap.put(
                    MetricUtility.constructKey(
                            metricKeyPrefixWithTag, "min_bucket_boundary_delay_ns"),
                    dataItem.minBucketBoundaryDelayNs);
            resultMap.put(
                    MetricUtility.constructKey(
                            metricKeyPrefixWithTag, "max_bucket_boundary_delay_ns"),
                    dataItem.maxBucketBoundaryDelayNs);
            resultMap.put(
                    MetricUtility.constructKey(metricKeyPrefixWithTag, "bucket_unknown_condition"),
                    dataItem.bucketUnknownCondition);
            resultMap.put(
                    MetricUtility.constructKey(metricKeyPrefixWithTag, "bucket_count"),
                    dataItem.bucketCount);

            summaryHardDimensionLimitReached += dataItem.hardDimensionLimitReached;
            summaryLateLogEventSkipped += dataItem.lateLogEventSkipped;
            summarySkippedForwardBuckets += dataItem.skippedForwardBuckets;
            summaryBadValueType += dataItem.badValueType;
            summaryConditionChangeInNextBucket += dataItem.conditionChangeInNextBucket;
            summaryInvalidatedBucket += dataItem.invalidatedBucket;
            summaryBucketDropped += dataItem.bucketDropped;
            summaryBucketUnknownCondition += dataItem.bucketUnknownCondition;
        }

        resultMap.put(
                MetricUtility.constructKey(metricKeyPrefix, "hard_dimension_limit_reached"),
                summaryHardDimensionLimitReached);
        resultMap.put(
                MetricUtility.constructKey(metricKeyPrefix, "late_log_event_skipped"),
                summaryLateLogEventSkipped);
        resultMap.put(
                MetricUtility.constructKey(metricKeyPrefix, "skipped_forward_buckets"),
                summarySkippedForwardBuckets);
        resultMap.put(
                MetricUtility.constructKey(metricKeyPrefix, "bad_value_type"), summaryBadValueType);
        resultMap.put(
                MetricUtility.constructKey(metricKeyPrefix, "condition_change_in_next_bucket"),
                summaryConditionChangeInNextBucket);
        resultMap.put(
                MetricUtility.constructKey(metricKeyPrefix, "invalidated_bucket"),
                summaryInvalidatedBucket);
        resultMap.put(
                MetricUtility.constructKey(metricKeyPrefix, "bucket_dropped"),
                summaryBucketDropped);
        resultMap.put(
                MetricUtility.constructKey(metricKeyPrefix, "bucket_unknown_condition"),
                summaryBucketUnknownCondition);
    }

    private static void populateDetectedLogLossStats(
            StatsLog.StatsdStatsReport.LogLossStats[] detectedLogLoss,
            Map<String, Long> resultMap) {
        final String metricKeyPrefix =
                MetricUtility.constructKey(STATSDSTATS_PREFIX, DETECTED_LOG_LOSS_STATS_PREFIX);

        for (final StatsLog.StatsdStatsReport.LogLossStats dataItem : detectedLogLoss) {
            final String metricKeyPrefixWithTag =
                    MetricUtility.constructKey(
                            metricKeyPrefix, String.valueOf(dataItem.detectedTimeSec));

            resultMap.put(
                    MetricUtility.constructKey(metricKeyPrefixWithTag, "count"),
                    Long.valueOf(dataItem.count));
            resultMap.put(
                    MetricUtility.constructKey(metricKeyPrefixWithTag, "last_error"),
                    Long.valueOf(dataItem.lastError));
            resultMap.put(
                    MetricUtility.constructKey(metricKeyPrefixWithTag, "last_tag"),
                    Long.valueOf(dataItem.lastTag));
            resultMap.put(
                    MetricUtility.constructKey(metricKeyPrefixWithTag, "uid"),
                    Long.valueOf(dataItem.uid));
            resultMap.put(
                    MetricUtility.constructKey(metricKeyPrefixWithTag, "pid"),
                    Long.valueOf(dataItem.pid));
        }
    }

    private static void populateEventQueueOverflowStats(
            StatsLog.StatsdStatsReport.EventQueueOverflow queueOverflow,
            Map<String, Long> resultMap) {
        if (queueOverflow == null) {
            return;
        }
        final String metricKeyPrefix =
                MetricUtility.constructKey(STATSDSTATS_PREFIX, EVENT_QUEUE_OVERFLOW_STATS_PREFIX);

        resultMap.put(
                MetricUtility.constructKey(metricKeyPrefix, "count"),
                Long.valueOf(queueOverflow.count));
        resultMap.put(
                MetricUtility.constructKey(metricKeyPrefix, "max_queue_history_nanos"),
                Long.valueOf(queueOverflow.maxQueueHistoryNs));
        resultMap.put(
                MetricUtility.constructKey(metricKeyPrefix, "min_queue_history_nanos"),
                Long.valueOf(queueOverflow.minQueueHistoryNs));
    }

}
