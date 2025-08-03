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

import static com.android.cobalt.collect.ImmutableHelpers.toImmutableMap;

import static com.google.common.base.Preconditions.checkArgument;

import android.annotation.NonNull;
import android.util.Log;

import com.android.cobalt.CobaltPeriodicJob;
import com.android.cobalt.crypto.Encrypter;
import com.android.cobalt.crypto.EncryptionFailedException;
import com.android.cobalt.data.DataService;
import com.android.cobalt.data.ObservationGenerator;
import com.android.cobalt.data.ObservationStoreEntity;
import com.android.cobalt.data.ReportKey;
import com.android.cobalt.domain.Project;
import com.android.cobalt.observations.ObservationGeneratorFactory;
import com.android.cobalt.observations.PrivacyGenerator;
import com.android.cobalt.system.CobaltClock;
import com.android.cobalt.system.SystemClock;
import com.android.cobalt.system.SystemData;
import com.android.cobalt.upload.Uploader;
import com.android.internal.annotations.VisibleForTesting;

import com.google.cobalt.EncryptedMessage;
import com.google.cobalt.Envelope;
import com.google.cobalt.MetricDefinition;
import com.google.cobalt.ObservationBatch;
import com.google.cobalt.ObservationMetadata;
import com.google.cobalt.ReleaseStage;
import com.google.cobalt.ReportDefinition;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.protobuf.ByteString;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/** Implementation of observation generation and upload for Cobalt. */
public final class CobaltPeriodicJobImpl implements CobaltPeriodicJob {
    private static final String LOG_TAG = "cobalt.periodic";

    // Cobalt's backend requires encrypted data be under 1MiB. Use a value much smaller to allow for
    // some overhead.
    @VisibleForTesting public static final int ENVELOPE_MAX_OBSERVATION_BYTES = 100000;

    // The largest aggregation window period (in days) that is supported.
    @VisibleForTesting public static final int LARGEST_AGGREGATION_WINDOW = 1;

    private final Project mProject;
    private final ReleaseStage mReleaseStage;
    private final DataService mDataService;
    private final ExecutorService mExecutor;
    private final ListeningScheduledExecutorService mScheduledExecutor;
    private final Duration mUploadDoneDelay;
    private final SystemClock mSystemClock;
    private final boolean mEnabled;
    private final Uploader mUploader;
    private final Encrypter mEncrypter;
    private final ByteString mApiKey;
    private final ObservationGeneratorFactory mObservationGeneratorFactory;

    public CobaltPeriodicJobImpl(
            @NonNull Project project,
            @NonNull ReleaseStage releaseStage,
            @NonNull DataService dataService,
            @NonNull ExecutorService executor,
            @NonNull ScheduledExecutorService scheduledExecutor,
            @NonNull SystemClock systemClock,
            @NonNull SystemData systemData,
            @NonNull PrivacyGenerator privacyGenerator,
            @NonNull SecureRandom secureRandom,
            @NonNull Uploader uploader,
            @NonNull Encrypter encrypter,
            @NonNull ByteString apiKey,
            @NonNull Duration uploadDoneDelay,
            boolean enabled) {
        mProject = Objects.requireNonNull(project);
        mReleaseStage = Objects.requireNonNull(releaseStage);
        mDataService = Objects.requireNonNull(dataService);
        mExecutor = Objects.requireNonNull(executor);
        mScheduledExecutor =
                MoreExecutors.listeningDecorator(Objects.requireNonNull(scheduledExecutor));
        mUploadDoneDelay = Objects.requireNonNull(uploadDoneDelay);
        mSystemClock = Objects.requireNonNull(systemClock);
        mEnabled = enabled;
        mUploader = Objects.requireNonNull(uploader);
        mEncrypter = Objects.requireNonNull(encrypter);
        mApiKey = Objects.requireNonNull(apiKey);
        mObservationGeneratorFactory =
                new ObservationGeneratorFactory(
                        mProject,
                        Objects.requireNonNull(systemData),
                        mDataService.getDaoBuildingBlocks(),
                        Objects.requireNonNull(privacyGenerator),
                        Objects.requireNonNull(secureRandom));
    }

    /**
     * Generates observations from the aggregated report data and uploads them to Cobalt's backend.
     *
     * <p>Observations are generated and stored in the database in a transaction to be sure that
     * multiple observations aren't generated for the same event/data. Observations are written the
     * to database after generation and read again before being grouped into envelopes and uploaded.
     * The observations are deleted after upload.
     */
    @Override
    public ListenableFuture<Void> generateAggregatedObservations() {
        Instant currentTime = Instant.ofEpochMilli(mSystemClock.currentTimeMillis());
        if (!mEnabled) {
            return mDataService.loggerDisabled(currentTime);
        }

        logInfo("Start log creation and sending");
        return FluentFuture.from(mDataService.loggerEnabled(currentTime))
                .transformAsync(this::generateAndSaveObservations, mExecutor)
                .catching(Throwable.class, this::logSaveFailure, mExecutor)
                .transform(unused -> mDataService.getOldestObservationsToSend(), mExecutor)
                .transform(this::uploadObservations, mExecutor)
                .catching(RuntimeException.class, this::logUploadFailure, mExecutor)
                .transformAsync(unused -> uploadDone(), mExecutor);
    }

    /**
     * Generate observations since the logger was enabled and save them to the database.
     *
     * @param initialTimeEnabled the initial time the logger was enabled
     * @return the oldest day index aggregate values which should be kept for
     */
    private FluentFuture<Void> generateAndSaveObservations(Instant initialTimeEnabled) {
        logInfo("Start observation generation");

        CobaltClock currentClock = new CobaltClock(mSystemClock.currentTimeMillis());
        CobaltClock initialEnabledClock = new CobaltClock(initialTimeEnabled.toEpochMilli());

        ImmutableList.Builder<ReportKey> relevantReports = ImmutableList.builder();
        ImmutableList.Builder<ListenableFuture<Void>> results = ImmutableList.builder();
        for (MetricDefinition metric : mProject.getMetrics()) {
            if (mReleaseStage.getNumber() > metric.getMetaData().getMaxReleaseStageValue()) {
                // Don't upload a metric that is not enabled for the current release stage.
                continue;
            }

            // Generate the observations up to yesterday.
            int dayIndexToGenerate = currentClock.dayIndex(metric) - 1;
            int dayIndexLoggerEnabled = initialEnabledClock.dayIndex(metric);
            for (ReportDefinition report : metric.getReportsList()) {
                if (report.getMaxReleaseStage() != ReleaseStage.RELEASE_STAGE_NOT_SET
                        && mReleaseStage.getNumber() > report.getMaxReleaseStageValue()) {
                    // Don't upload a report that is not enabled for the current release stage.
                    continue;
                }

                ReportKey reportKey =
                        ReportKey.create(
                                mProject.getCustomerId(),
                                mProject.getProjectId(),
                                metric.getId(),
                                report.getId());
                relevantReports.add(reportKey);
                logInfo(
                        "Generating observations for day %s for report %s",
                        dayIndexToGenerate, reportKey);
                Function<Integer, ObservationGenerator> generatorSupplier =
                        (dayIndex) ->
                                mObservationGeneratorFactory.getObservationGenerator(
                                        metric, report, dayIndex);
                results.add(
                        mDataService.generateObservations(
                                reportKey,
                                dayIndexToGenerate,
                                dayIndexLoggerEnabled,
                                generatorSupplier));
            }
        }

        // Aggregate data for more than the largest aggregation window (1 day) ago can not be
        // needed to generate observations any more. We also subtract one because the aggregation
        // runs are for the previous day, and another one because we are using the UTC day index for
        // all reports instead of the individual metric's time zone in the registry.
        int oldestDayIndex = currentClock.dayIndexUtc() - LARGEST_AGGREGATION_WINDOW - 2;
        return FluentFuture.from(Futures.allAsList(results.build()))
                .transformAsync(
                        unused -> mDataService.cleanup(relevantReports.build(), oldestDayIndex),
                        mExecutor);
    }

    /**
     * Upload a set of observations, working in chunks to ensure data sent is below the limit
     * imposed by Cobalt's backend.
     */
    private Void uploadObservations(ImmutableList<ObservationStoreEntity> observations)
            throws EncryptionFailedException {
        // Send observations in limited-sized batches to ensure they're under Cobalt's size limit.
        int currentTotalBytes = 0;
        ImmutableList.Builder<ObservationBatch> currentBatches = ImmutableList.builder();
        ImmutableList.Builder<Integer> currentObservationIds = ImmutableList.builder();
        for (ObservationStoreEntity observation : observations) {
            ObservationBatch batch = observation.encrypt(mEncrypter);
            int batchBytes = batch.getSerializedSize();
            if (currentTotalBytes + batchBytes >= ENVELOPE_MAX_OBSERVATION_BYTES) {
                uploadAndRemoveObservationBatches(
                        currentBatches.build(), currentObservationIds.build());

                // Reset state.
                currentTotalBytes = 0;
                currentBatches = ImmutableList.builder();
                currentObservationIds = ImmutableList.builder();
            }

            currentTotalBytes += batchBytes;
            currentBatches.add(batch);
            currentObservationIds.add(observation.observationStoreId());
        }

        // Send the final set of observations not sent in the loop.
        uploadAndRemoveObservationBatches(currentBatches.build(), currentObservationIds.build());

        return null;
    }

    /** Upload a set of observation batches and remove them from the observation store. */
    private void uploadAndRemoveObservationBatches(
            ImmutableList<ObservationBatch> observationBatches,
            ImmutableList<Integer> observationStoreIds)
            throws EncryptionFailedException {
        checkArgument(
                observationBatches.size() == observationStoreIds.size(),
                "Mismatch in number of observation batches and observation store ids");
        if (observationBatches.isEmpty()) {
            return;
        }

        logInfo("Uploading %s observations", observationBatches.size());
        Optional<EncryptedMessage> encryptionResult =
                mEncrypter.encryptEnvelope(buildEnvelope(observationBatches));
        encryptionResult.ifPresent(mUploader::upload);
        mDataService.removeSentObservations(observationStoreIds);
    }

    /** Build an envelope from a list of observations, deduplicating by metadata in the process. */
    private Envelope buildEnvelope(ImmutableList<ObservationBatch> observationBatches) {
        ImmutableMap<ObservationMetadata, List<EncryptedMessage>> byMetadata =
                observationBatches.stream()
                        .collect(
                                toImmutableMap(
                                        ObservationBatch::getMetaData,
                                        ObservationBatch::getEncryptedObservationList));
        ImmutableList.Builder<ObservationBatch> newObservationBatches = ImmutableList.builder();
        for (Map.Entry<ObservationMetadata, List<EncryptedMessage>> entry : byMetadata.entrySet()) {
            newObservationBatches.add(
                    ObservationBatch.newBuilder()
                            .setMetaData(entry.getKey())
                            .addAllEncryptedObservation(entry.getValue())
                            .build());
        }
        return Envelope.newBuilder()
                .setApiKey(mApiKey)
                .addAllBatch(newObservationBatches.build())
                .build();
    }

    private FluentFuture<Void> uploadDone() {
        return FluentFuture.from(
                mScheduledExecutor.schedule(
                        () -> {
                            mUploader.uploadDone();
                            return null;
                        },
                        mUploadDoneDelay.toNanos(),
                        TimeUnit.NANOSECONDS));
    }

    private Void logSaveFailure(Throwable t) {
        logThrownAtError("One or more reports failed observation generation", t);
        return null;
    }

    private Void logUploadFailure(Throwable t) {
        logThrownAtError("One or more observations failed to be uploaded", t);
        return null;
    }

    private static void logInfo(String format, Object... params) {
        if (Log.isLoggable(LOG_TAG, Log.INFO)) {
            Log.i(LOG_TAG, String.format(Locale.US, format, params));
        }
    }

    private static void logThrownAtError(String msg, Throwable t) {
        if (Log.isLoggable(LOG_TAG, Log.ERROR)) {
            Log.e(LOG_TAG, msg, t);
        }
    }
}
