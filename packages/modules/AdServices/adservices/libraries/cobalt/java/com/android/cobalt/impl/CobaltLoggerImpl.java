/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.cobalt.impl;

import static com.google.common.base.Preconditions.checkArgument;

import android.annotation.NonNull;
import android.util.Log;

import com.android.cobalt.CobaltLogger;
import com.android.cobalt.data.DataService;
import com.android.cobalt.data.EventVector;
import com.android.cobalt.data.ReportKey;
import com.android.cobalt.domain.Project;
import com.android.cobalt.system.CobaltClock;
import com.android.cobalt.system.SystemClock;
import com.android.cobalt.system.SystemData;

import com.google.cobalt.MetricDefinition;
import com.google.cobalt.MetricDefinition.MetricType;
import com.google.cobalt.ReleaseStage;
import com.google.cobalt.ReportDefinition;
import com.google.cobalt.ReportDefinition.ReportType;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;

/** Implementation of the logging of metrics for Cobalt. */
public final class CobaltLoggerImpl implements CobaltLogger {
    private static final String LOG_TAG = "cobalt.logger";

    private final Project mProject;
    private final ReleaseStage mReleaseStage;
    private final DataService mDataService;
    private final SystemData mSystemData;
    private final ExecutorService mExecutor;
    private final SystemClock mSystemClock;
    private final boolean mEnabled;

    public CobaltLoggerImpl(
            @NonNull Project project,
            @NonNull ReleaseStage releaseStage,
            @NonNull DataService dataService,
            @NonNull SystemData systemData,
            @NonNull ExecutorService executor,
            @NonNull SystemClock systemClock,
            boolean enabled) {
        mProject = Objects.requireNonNull(project);
        mReleaseStage = Objects.requireNonNull(releaseStage);
        mDataService = Objects.requireNonNull(dataService);
        mSystemData = Objects.requireNonNull(systemData);
        mExecutor = Objects.requireNonNull(executor);
        mSystemClock = Objects.requireNonNull(systemClock);
        mEnabled = enabled;
    }

    @Override
    public ListenableFuture<Void> logOccurrence(
            long metricId, long count, List<Integer> eventCodes) {
        long currentTimeMillis = mSystemClock.currentTimeMillis();
        if (!mEnabled) {
            return mDataService.loggerDisabled(Instant.ofEpochMilli(currentTimeMillis));
        }
        if (Log.isLoggable(LOG_TAG, Log.INFO)) {
            Log.i(LOG_TAG, "start logging OCCURRENCE metric");
        }

        return FluentFuture.from(
                        mDataService.loggerEnabled(Instant.ofEpochMilli(currentTimeMillis)))
                .transform(
                        unused -> {
                            checkArgument(count >= 0, "occurrence count can't be negative");
                            return validateEventAndGetMetric(
                                    MetricType.OCCURRENCE, metricId, eventCodes);
                        },
                        mExecutor)
                .transformAsync(
                        metric ->
                                loggerEnabledLogOccurrence(
                                        metric, count, eventCodes, currentTimeMillis),
                        mExecutor);
    }

    @Override
    public ListenableFuture<Void> logString(
            long metricId, String stringValue, List<Integer> eventCodes) {
        long currentTimeMillis = mSystemClock.currentTimeMillis();
        if (!mEnabled) {
            return mDataService.loggerDisabled(Instant.ofEpochMilli(currentTimeMillis));
        }
        if (Log.isLoggable(LOG_TAG, Log.INFO)) {
            Log.i(LOG_TAG, "start logging STRING metric");
        }

        return FluentFuture.from(
                        mDataService.loggerEnabled(Instant.ofEpochMilli(currentTimeMillis)))
                .transform(
                        unused ->
                                validateEventAndGetMetric(MetricType.STRING, metricId, eventCodes),
                        mExecutor)
                .transformAsync(
                        metric ->
                                loggerEnabledLogString(
                                        metric, stringValue, eventCodes, currentTimeMillis),
                        mExecutor);
    }

    private ListenableFuture<Void> loggerEnabledLogOccurrence(
            MetricDefinition metric, long count, List<Integer> eventCodes, long currentTimeMillis) {
        EventVector eventVector = EventVector.create(eventCodes);
        if (mReleaseStage.getNumber() > metric.getMetaData().getMaxReleaseStageValue()) {
            // Don't log a metric that is not enabled for the current release stage.
            return Futures.immediateFuture(null);
        }
        return FluentFuture.from(
                        Futures.allAsList(
                                logNumberToReports(metric, count, eventVector, currentTimeMillis)))
                .transform(this::recordSuccess, mExecutor)
                .catching(
                        RuntimeException.class,
                        x -> recordFailureAndRethrow(metric.getId(), x),
                        mExecutor);
    }

    private ListenableFuture<Void> loggerEnabledLogString(
            MetricDefinition metric,
            String stringValue,
            List<Integer> eventCodes,
            long currentTimeMillis) {
        EventVector eventVector = EventVector.create(eventCodes);
        if (mReleaseStage.getNumber() > metric.getMetaData().getMaxReleaseStageValue()) {
            // Don't log a metric that is not enabled for the current release stage.
            return Futures.immediateFuture(null);
        }
        return FluentFuture.from(
                        Futures.allAsList(
                                logStringToReports(
                                        metric, stringValue, eventVector, currentTimeMillis)))
                .transform(this::recordSuccess, mExecutor)
                .catching(
                        RuntimeException.class,
                        x -> recordFailureAndRethrow(metric.getId(), x),
                        mExecutor);
    }

    private Void recordFailureAndRethrow(long metricId, RuntimeException x) {
        if (Log.isLoggable(LOG_TAG, Log.ERROR)) {
            Log.e(
                    LOG_TAG,
                    String.format(
                            Locale.US,
                            "Error logging OCCURRENCE event for metric id: %s",
                            metricId),
                    x);
        }
        throw x;
    }

    private Void recordSuccess(List<Void> successes) {
        if (Log.isLoggable(LOG_TAG, Log.INFO)) {
            Log.i(
                    LOG_TAG,
                    String.format(
                            Locale.US, "logged occurrence event to %s reports", successes.size()));
        }
        return null;
    }

    private ImmutableList<ListenableFuture<Void>> logNumberToReports(
            MetricDefinition metric, long value, EventVector eventVector, long currentTimeMillis) {
        CobaltClock clock = new CobaltClock(currentTimeMillis);
        int dayIndex = clock.dayIndex(metric);

        ImmutableList.Builder<ListenableFuture<Void>> dbWrites = ImmutableList.builder();
        for (ReportDefinition report : metric.getReportsList()) {
            if (report.getMaxReleaseStage() != ReleaseStage.RELEASE_STAGE_NOT_SET
                    && mReleaseStage.getNumber() > report.getMaxReleaseStageValue()) {
                // Don't log a report that is not enabled for the current release stage.
                continue;
            }
            ReportKey reportKey =
                    ReportKey.create(
                            mProject.getCustomerId(),
                            mProject.getProjectId(),
                            metric.getId(),
                            report.getId());

            if (report.getReportType() == ReportType.FLEETWIDE_OCCURRENCE_COUNTS) {
                dbWrites.add(
                        mDataService.aggregateCount(
                                reportKey,
                                dayIndex,
                                mSystemData.filteredSystemProfile(report),
                                eventVector,
                                report.getEventVectorBufferMax(),
                                value));
            }
        }

        return dbWrites.build();
    }

    private ImmutableList<ListenableFuture<Void>> logStringToReports(
            MetricDefinition metric,
            String stringValue,
            EventVector eventVector,
            long currentTimeMillis) {
        CobaltClock clock = new CobaltClock(currentTimeMillis);
        int dayIndex = clock.dayIndex(metric);

        ImmutableList.Builder<ListenableFuture<Void>> dbWrites = ImmutableList.builder();
        for (ReportDefinition report : metric.getReportsList()) {
            if (report.getMaxReleaseStage() != ReleaseStage.RELEASE_STAGE_NOT_SET
                    && mReleaseStage.getNumber() > report.getMaxReleaseStageValue()) {
                // Don't log a report that is not enabled for the current release stage.
                continue;
            }
            ReportKey reportKey =
                    ReportKey.create(
                            mProject.getCustomerId(),
                            mProject.getProjectId(),
                            metric.getId(),
                            report.getId());

            if (report.getReportType() == ReportType.STRING_COUNTS) {
                dbWrites.add(
                        mDataService.aggregateString(
                                reportKey,
                                dayIndex,
                                mSystemData.filteredSystemProfile(report),
                                eventVector,
                                report.getEventVectorBufferMax(),
                                report.getStringBufferMax(),
                                stringValue));
            }
        }

        return dbWrites.build();
    }

    private MetricDefinition validateEventAndGetMetric(
            MetricType metricType, long metricId, List<Integer> eventCodes) {
        checkArgument(
                eventCodes.stream().allMatch(c -> c >= 0),
                "event vectors can't contain negative event codes");

        Optional<MetricDefinition> foundMetric =
                mProject.getMetrics().stream().filter(m -> m.getId() == metricId).findFirst();
        checkArgument(foundMetric.isPresent(), "failed to find metric with ID: %s", metricId);
        MetricType foundMetricType = foundMetric.get().getMetricType();
        checkArgument(
                foundMetricType == metricType,
                "wrong metric type, expected %s, found %s",
                metricType,
                foundMetricType);

        return foundMetric.get();
    }
}
