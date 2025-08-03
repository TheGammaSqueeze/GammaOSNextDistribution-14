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

package com.android.cobalt.data;

import static com.android.cobalt.collect.ImmutableHelpers.toImmutableList;
import static com.android.cobalt.collect.ImmutableHelpers.toImmutableListMultimap;

import static java.util.stream.Collectors.toMap;

import android.annotation.NonNull;
import android.util.Log;

import com.google.cobalt.AggregateValue;
import com.google.cobalt.SystemProfile;
import com.google.cobalt.UnencryptedObservationBatch;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.hash.HashCode;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;

/** Provides essential operations for interacting with Cobalt's database. */
public final class DataService {
    private static final String LOG_TAG = "cobalt.data";

    // The Logger resets and doesn't backfill if it is disabled for more than 2 days. 2 days means
    // that the logger was disabled for at least a single full day, and so data should not be sent
    // for at least one day.
    private static final Duration sDisabledResetTime = Duration.ofDays(2);

    private final ExecutorService mExecutorService;
    private final CobaltDatabase mCobaltDatabase;
    private final DaoBuildingBlocks mDaoBuildingBlocks;

    public DataService(@NonNull ExecutorService executor, @NonNull CobaltDatabase cobaltDatabase) {
        this.mExecutorService = Objects.requireNonNull(executor);
        this.mCobaltDatabase = Objects.requireNonNull(cobaltDatabase);

        this.mDaoBuildingBlocks = mCobaltDatabase.daoBuildingBlocks();
    }

    public DaoBuildingBlocks getDaoBuildingBlocks() {
        return mDaoBuildingBlocks;
    }

    /**
     * Record that the logger is currently disabled.
     *
     * @param currentTime the current time
     * @return ListenableFuture to track the completion of this change
     */
    public ListenableFuture<Void> loggerDisabled(Instant currentTime) {
        GlobalValueEntity entity =
                GlobalValueEntity.create(
                        GlobalValueEntity.Key.INITIAL_DISABLED_TIME,
                        GlobalValueEntity.timeToDbString(currentTime));
        return Futures.submit(
                () ->
                        mCobaltDatabase.runInTransaction(
                                () -> mDaoBuildingBlocks.insertGlobalValue(entity)),
                mExecutorService);
    }

    /**
     * Record that the logger is currently enabled, and determine how far back to backfill.
     *
     * @param currentTime the current time
     * @return the time when the logger was initially enabled since the last disabling, if it was
     *     for long enough
     */
    public ListenableFuture<Instant> loggerEnabled(Instant currentTime) {
        return Futures.submit(
                () -> mCobaltDatabase.runInTransaction(() -> loggerEnabledSync(currentTime)),
                mExecutorService);
    }

    /**
     * Generate the observations for a report.
     *
     * <p>Observations are generated in a transaction to be sure that multiple observations aren't
     * generated for the same event/data. The process is in one transaction:
     *
     * <ol>
     *   <li>Read the last_sent_day_index for the report.
     *   <li>Load the aggregated data for each day that needs observations to be generated.
     *   <li>Call the ObservationGenerator to generate observations for each day.
     *   <li>Store the observations in the database.
     *   <li>Updates the last_sent_day_index for the report.
     * </ol>
     *
     * @param reportKey the report to get the Count aggregated data for
     * @param mostRecentDayIndex the most recent day to check for aggregated events to send data for
     * @param dayIndexLoggerEnabled the day index that the logger was enabled
     * @param generatorSupplier a function that returns ObservationGenerator to convert the
     *     EventRecordAndSystemProfile for a specific day into observations
     */
    public ListenableFuture<Void> generateObservations(
            ReportKey reportKey,
            int mostRecentDayIndex,
            int dayIndexLoggerEnabled,
            Function<Integer, ObservationGenerator> generatorSupplier) {
        return Futures.submit(
                () ->
                        mCobaltDatabase.runInTransaction(
                                () ->
                                        generateObservationsSync(
                                                reportKey,
                                                mostRecentDayIndex,
                                                dayIndexLoggerEnabled,
                                                generatorSupplier)),
                mExecutorService);
    }

    private Instant loggerEnabledSync(Instant currentTime) {
        Map<GlobalValueEntity.Key, Instant> enablementTimes =
                mDaoBuildingBlocks.queryEnablementTimes().entrySet().stream()
                        .collect(
                                toMap(
                                        Map.Entry::getKey,
                                        v -> GlobalValueEntity.timeFromDbString(v.getValue())));
        Instant initialEnabledTime =
                enablementTimes.get(GlobalValueEntity.Key.INITIAL_ENABLED_TIME);
        Instant startDisabledTime =
                enablementTimes.get(GlobalValueEntity.Key.INITIAL_DISABLED_TIME);

        if (startDisabledTime != null) {
            // The logger was disabled, this is the first run since it was enabled.
            if (Duration.between(startDisabledTime, currentTime).compareTo(sDisabledResetTime)
                    > 0) {
                // Disabled for too long, start over from now.
                initialEnabledTime = null;
            }
            mDaoBuildingBlocks.deleteDisabledTime();
        }

        if (initialEnabledTime == null) {
            // Set/update the initial enabled time to the current time.
            initialEnabledTime = currentTime;
            GlobalValueEntity entity =
                    GlobalValueEntity.create(
                            GlobalValueEntity.Key.INITIAL_ENABLED_TIME,
                            GlobalValueEntity.timeToDbString(initialEnabledTime));
            mDaoBuildingBlocks.insertOrReplaceGlobalValue(entity);
        }

        return initialEnabledTime;
    }

    /**
     * Updates the aggregated data for a COUNT report in response to an event that occurred.
     *
     * <p>For the given report, dayIndex, systemProfile, and eventVector at the time of the event,
     * check whether the database already contains an entry. If so, add count to its aggregateValue
     * and update the entry. If not, create a new entry with count as the aggregateValue. This only
     * supports the REPORT_ALL system profile selection policy.
     *
     * @param reportKey the report being aggregated
     * @param dayIndex the day on which the event occurred
     * @param systemProfile the SystemProfile on the device at the time of the event
     * @param eventVector the event the value was logged for
     * @param eventVectorBufferMax the maximum number of event vectors to store per
     *     report/day/profile
     * @param count the count value of the event
     */
    public ListenableFuture<Void> aggregateCount(
            ReportKey reportKey,
            int dayIndex,
            SystemProfile systemProfile,
            EventVector eventVector,
            long eventVectorBufferMax,
            long count) {
        return Futures.submit(
                () ->
                        mCobaltDatabase.runInTransaction(
                                () ->
                                        aggregateCountSync(
                                                reportKey,
                                                dayIndex,
                                                systemProfile,
                                                eventVector,
                                                eventVectorBufferMax,
                                                count)),
                mExecutorService);
    }

    /**
     * Updates the aggregated data for a STRING_COUNT report in response to an event that occurred.
     *
     * <p>For the given report, dayIndex, systemProfile, and eventVector at the time of the event,
     * check whether the string hash can be logged or the database already contains an entry. If so,
     * add the string occurrence to its aggregateValue and update the entry. If not, create a new
     * entry with a single string occurrence count as the aggregateValue, if the string buffer max
     * hasn't been reached. This only supports the REPORT_ALL system profile selection policy.
     *
     * @param reportKey the report being aggregated
     * @param dayIndex the day on which the event occurred
     * @param systemProfile the SystemProfile on the device at the time of the event
     * @param eventVector the event the value was logged for
     * @param eventVectorBufferMax the maximum number of event vectors to store per
     *     report/day/profile
     * @param stringBufferMax the maximum number of strings to store per report/day
     * @param stringValue the logged string
     */
    public ListenableFuture<Void> aggregateString(
            ReportKey reportKey,
            int dayIndex,
            SystemProfile systemProfile,
            EventVector eventVector,
            long eventVectorBufferMax,
            long stringBufferMax,
            String stringValue) {
        return Futures.submit(
                () ->
                        mCobaltDatabase.runInTransaction(
                                () ->
                                        aggregateStringSync(
                                                reportKey,
                                                dayIndex,
                                                systemProfile,
                                                eventVector,
                                                eventVectorBufferMax,
                                                stringBufferMax,
                                                stringValue)),
                mExecutorService);
    }

    /**
     * Delete data from the database that is no longer needed.
     *
     * @param relevantReports reports which are in the registry and collected
     * @param oldestDayIndex the oldest day index to keep aggregate data for
     * @return a ListenableFuture to track the completion of this change
     */
    public ListenableFuture<Void> cleanup(
            ImmutableList<ReportKey> relevantReports, int oldestDayIndex) {
        return Futures.submit(
                () ->
                        mCobaltDatabase.runInTransaction(
                                () -> {
                                    mDaoBuildingBlocks.deleteOldAggregates(oldestDayIndex);
                                    mDaoBuildingBlocks.deleteReports(
                                            irrelevantReports(relevantReports));
                                    mDaoBuildingBlocks.deleteUnusedStringHashes();
                                    mDaoBuildingBlocks.deleteUnusedSystemProfileHashes();
                                }),
                mExecutorService);
    }

    /**
     * Get the observations that are waiting to be sent, sorted by the order they were added.
     *
     * @return the ordered obeservations
     */
    public ImmutableList<ObservationStoreEntity> getOldestObservationsToSend() {
        return ImmutableList.copyOf(mDaoBuildingBlocks.queryOldestObservations());
    }

    /**
     * Delete some sent observations from the database.
     *
     * @param observationIds the IDs of the observations that were successfully sent
     */
    public void removeSentObservations(List<Integer> observationIds) {
        if (observationIds.isEmpty()) {
            return;
        }

        mDaoBuildingBlocks.deleteByObservationId(observationIds);
    }

    private void aggregateCountSync(
            ReportKey reportKey,
            int dayIndex,
            SystemProfile systemProfile,
            EventVector eventVector,
            long eventVectorBufferMax,
            long count) {
        aggregateValueReportAll(
                reportKey,
                dayIndex,
                systemProfile,
                eventVector,
                eventVectorBufferMax,
                count,
                LogAggregators.countAggregator());
    }

    private void aggregateStringSync(
            ReportKey reportKey,
            int dayIndex,
            SystemProfile systemProfile,
            EventVector eventVector,
            long eventVectorBufferMax,
            long stringBufferMax,
            String stringValue) {
        HashCode hash = StringHashEntity.getHash(stringValue);
        int index =
                mDaoBuildingBlocks.queryStringListIndex(reportKey, dayIndex, stringBufferMax, hash);
        if (index == -1) {
            return;
        }

        StringHashEntity stringHash = StringHashEntity.create(reportKey, dayIndex, index, hash);
        if (aggregateValueReportAll(
                reportKey,
                dayIndex,
                systemProfile,
                eventVector,
                eventVectorBufferMax,
                stringHash,
                LogAggregators.stringIndexAggregator())) {
            // `stringHash`'s index was written to the database, ensure it and the string it's for
            // are in the string hash table for report/day combination.
            mDaoBuildingBlocks.insertStringHash(stringHash);
        }
    }

    /**
     * Generic method for aggregated values and updating them in the aggregate store with a system
     * profile selection policy of REPORT_ALL.
     *
     * @param <ToAggregate> the type of the value being aggregated
     * @param reportKey the report being aggregated
     * @param dayIndex the day on which the event occurred
     * @param systemProfile the SystemProfile on the device at the time of the event
     * @param eventVector the event the value was logged for
     * @param eventVectorBufferMax the maximum number of event vectors to store per
     *     report/day/profile
     * @param value the new value
     * @param aggregator the {@link LogAggregator} used to aggregate the new and existing values
     * @return whether a value was inserted or updated in the aggregate store
     */
    private <ToAggregate> boolean aggregateValueReportAll(
            ReportKey reportKey,
            int dayIndex,
            SystemProfile systemProfile,
            EventVector eventVector,
            long eventVectorBufferMax,
            ToAggregate value,
            LogAggregator aggregator) {
        long systemProfileHash = SystemProfileEntity.getSystemProfileHash(systemProfile);
        mDaoBuildingBlocks.insertSystemProfile(
                SystemProfileEntity.create(systemProfileHash, systemProfile));
        mDaoBuildingBlocks.insertLastSentDayIndex(reportKey, dayIndex - 1);

        Optional<SystemProfileAndAggregateValue> existingSystemProfileAndAggregateValue =
                mDaoBuildingBlocks.queryOneSystemProfileAndAggregateValue(
                        reportKey, dayIndex, eventVector, systemProfileHash);

        if (!existingSystemProfileAndAggregateValue.isPresent()) {
            // No aggregate value was found for the provided report, day index, and event vector
            // combination, insert one.
            return insertAggregateRow(
                    reportKey,
                    dayIndex,
                    systemProfileHash,
                    eventVector,
                    eventVectorBufferMax,
                    aggregator.initialValue(value));
        }

        // An existing entry matches the provided report, day index, and event vector combination,
        // update an existing system profile or add a new one.
        long existingSystemProfileHash =
                existingSystemProfileAndAggregateValue.get().systemProfileHash();
        if (existingSystemProfileHash == systemProfileHash) {
            // The system profile in the DB should be used, update the value.
            AggregateValue existingAggregateValue =
                    existingSystemProfileAndAggregateValue.get().aggregateValue();
            mDaoBuildingBlocks.updateAggregateValue(
                    reportKey,
                    dayIndex,
                    eventVector,
                    systemProfileHash,
                    aggregator.aggregateValues(value, existingAggregateValue));
            return true;
        }

        // All system profiles should be reported, add the system profile and value.
        return insertAggregateRow(
                reportKey,
                dayIndex,
                systemProfileHash,
                eventVector,
                eventVectorBufferMax,
                aggregator.initialValue(value));
    }

    /**
     * Associates `newValue` with the provided report, day index, event vector, and system profile
     * in the DB, if present. Does nothing otherwise.
     *
     * @return true if a value was inserted, false otherwise
     */
    private boolean insertAggregateRow(
            ReportKey reportKey,
            int dayIndex,
            long systemProfileHash,
            EventVector eventVector,
            long eventVectorBufferMax,
            AggregateValue newValue) {
        if (!canAddEventVectorToSystemProfile(
                reportKey, dayIndex, systemProfileHash, eventVectorBufferMax)) {
            return false;
        }
        mDaoBuildingBlocks.insertAggregateValue(
                AggregateStoreEntity.create(
                        reportKey, dayIndex, eventVector, systemProfileHash, newValue));
        return true;
    }

    private boolean canAddEventVectorToSystemProfile(
            ReportKey reportKey, int dayIndex, long systemProfileHash, long eventVectorBufferMax) {
        if (eventVectorBufferMax == 0) {
            return true;
        }

        long numEventVectors =
                mDaoBuildingBlocks.queryCountEventVectors(reportKey, dayIndex, systemProfileHash);
        if (numEventVectors >= eventVectorBufferMax) {
            logWarn(
                    "Dropping eventVector for report %s, due to exceeding event_vector_buffer_max"
                            + " %s",
                    reportKey, eventVectorBufferMax);
            return false;
        }
        return true;
    }

    private void generateObservationsSync(
            ReportKey reportKey,
            int mostRecentDayIndex,
            int dayIndexLoggerEnabled,
            Function<Integer, ObservationGenerator> generatorSupplier) {
        // Read the aggregate store data that has already been sent.
        int nextDayIndex =
                nextDayIndexToAggregate(reportKey, mostRecentDayIndex, dayIndexLoggerEnabled);

        ImmutableList.Builder<UnencryptedObservationBatch> generatedObservations =
                ImmutableList.builder();

        // Iterate over all outstanding days to generate data for, will be 1 under normal
        // circumstances.
        for (int dayIndex = nextDayIndex; dayIndex <= mostRecentDayIndex; dayIndex++) {
            logInfo("Generating observations for day index %s for report %s", dayIndex, reportKey);
            ImmutableListMultimap<SystemProfile, EventRecordAndSystemProfile> eventData =
                    mDaoBuildingBlocks.queryEventRecordsForDay(reportKey, dayIndex).stream()
                            .collect(
                                    toImmutableListMultimap(
                                            EventRecordAndSystemProfile::systemProfile, e -> e));
            ObservationGenerator generator = generatorSupplier.apply(dayIndex);
            ImmutableList<UnencryptedObservationBatch> batches =
                    generator.generateObservations(dayIndex, eventData);
            int numObservations =
                    batches.stream()
                            .mapToInt(UnencryptedObservationBatch::getUnencryptedObservationsCount)
                            .sum();
            logInfo(
                    "Generated %s observations in %s observation batches for day index %s for"
                            + " report %s",
                    numObservations, batches.size(), dayIndex, reportKey);
            generatedObservations.addAll(batches);
        }

        // Insert the observations and update the report with the day index data has been generated
        // for.
        mDaoBuildingBlocks.insertObservationBatches(generatedObservations.build());
        mDaoBuildingBlocks.updateLastSentDayIndex(reportKey, mostRecentDayIndex);
    }

    private int nextDayIndexToAggregate(
            ReportKey reportKey, int mostRecentDayIndex, int dayIndexLoggerEnabled) {
        Optional<Integer> lastSentDayIndex = mDaoBuildingBlocks.queryLastSentDayIndex(reportKey);

        if (!lastSentDayIndex.isPresent()) {
            // Report is missing. Store it with most recent day index as last_sent_day_index, so it
            // can be updated at the end of the observation generation.
            mDaoBuildingBlocks.insertLastSentDayIndex(reportKey, mostRecentDayIndex);
        }

        // A new report requires the day index be greater than the most recently completed day index
        // to prevent aggregation from happening in this run.
        Integer nextDayIndex = lastSentDayIndex.orElse(mostRecentDayIndex);
        nextDayIndex += 1;

        // Only go back at most 4 days; older data is dropped by the server.
        if (nextDayIndex < mostRecentDayIndex - 3) {
            nextDayIndex = mostRecentDayIndex - 3;
        }

        // Don't generate data for days before the logger was enabled.
        if (nextDayIndex < dayIndexLoggerEnabled) {
            nextDayIndex = dayIndexLoggerEnabled;
        }

        return nextDayIndex;
    }

    private ImmutableList<ReportKey> irrelevantReports(ImmutableList<ReportKey> registryReports) {
        return mDaoBuildingBlocks.queryReportKeys().stream()
                .filter(r -> !registryReports.contains(r))
                .collect(toImmutableList());
    }

    private static void logInfo(String format, Object... params) {
        if (Log.isLoggable(LOG_TAG, Log.INFO)) {
            Log.i(LOG_TAG, String.format(Locale.US, format, params));
        }
    }

    private static void logWarn(String format, Object... params) {
        if (Log.isLoggable(LOG_TAG, Log.WARN)) {
            Log.w(LOG_TAG, String.format(Locale.US, format, params));
        }
    }
}
