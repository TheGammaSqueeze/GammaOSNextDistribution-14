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

package com.android.cobalt.observations;

import static java.util.Objects.requireNonNull;

import android.annotation.NonNull;

import com.android.cobalt.data.EventRecordAndSystemProfile;
import com.android.cobalt.data.EventVector;
import com.android.cobalt.data.ObservationGenerator;
import com.android.cobalt.system.SystemData;

import com.google.cobalt.AggregateValue;
import com.google.cobalt.MetricDefinition;
import com.google.cobalt.Observation;
import com.google.cobalt.ObservationMetadata;
import com.google.cobalt.ObservationToEncrypt;
import com.google.cobalt.PrivateIndexObservation;
import com.google.cobalt.ReportDefinition;
import com.google.cobalt.ReportParticipationObservation;
import com.google.cobalt.SystemProfile;
import com.google.cobalt.UnencryptedObservationBatch;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;

import java.security.SecureRandom;

/** Generates private observations from event data and report privacy parameters. */
final class PrivateObservationGenerator implements ObservationGenerator {
    /**
     * Interface to encode an aggregated value as a private index observation for private reports.
     */
    interface Encoder {
        /**
         * Encodes one event and aggregated value as a single private observation.
         *
         * <p>Note, retuning a single private observation implies that report types that have
         * multiple values in their {@link AggregateValue}, like histograms, aren't supported.
         *
         * @param eventVector the event vector to encode
         * @param aggregateValue the aggregated value to encode
         * @return the privacy encoded observation
         */
        PrivateIndexObservation encode(EventVector eventVector, AggregateValue aggregateValue);
    }

    private final SystemData mSystemData;
    private final PrivacyGenerator mPrivacyGenerator;
    private final SecureRandom mSecureRandom;
    private final Encoder mEncoder;
    private final int mCustomerId;
    private final int mProjectId;
    private final MetricDefinition mMetric;
    private final ReportDefinition mReport;

    PrivateObservationGenerator(
            @NonNull SystemData systemData,
            @NonNull PrivacyGenerator privacyGenerator,
            @NonNull SecureRandom secureRandom,
            @NonNull Encoder encoder,
            int customerId,
            int projectId,
            @NonNull MetricDefinition metric,
            @NonNull ReportDefinition report) {
        this.mSystemData = requireNonNull(systemData);
        this.mPrivacyGenerator = requireNonNull(privacyGenerator);
        this.mSecureRandom = requireNonNull(secureRandom);
        this.mEncoder = requireNonNull(encoder);
        this.mCustomerId = customerId;
        this.mProjectId = projectId;
        this.mMetric = requireNonNull(metric);
        this.mReport = requireNonNull(report);
    }

    /**
     * Generate the private observations that for a report and day.
     *
     * @param dayIndex the day index to generate observations for
     * @param allEventData the data for events that occurred that are relevant to the day and Report
     * @return the observations to store in the DB for later sending, contained in
     *     UnencryptedObservationBatches with their metadata
     */
    @Override
    public ImmutableList<UnencryptedObservationBatch> generateObservations(
            int dayIndex,
            ImmutableListMultimap<SystemProfile, EventRecordAndSystemProfile> allEventData) {
        if (allEventData.isEmpty()) {
            return ImmutableList.of(
                    generateObservations(
                            dayIndex,
                            // Use the current system profile since none is provided.
                            mSystemData.filteredSystemProfile(mReport),
                            ImmutableList.of()));
        }

        ImmutableList.Builder<UnencryptedObservationBatch> batches = ImmutableList.builder();
        for (SystemProfile systemProfile : allEventData.keySet()) {
            batches.add(
                    generateObservations(dayIndex, systemProfile, allEventData.get(systemProfile)));
        }

        return batches.build();
    }

    /**
     * Generate an observation batch from events for a report, day, and system profile.
     *
     * @param dayIndex the day observations are being generated for
     * @param systemProfile the system profile of the observations
     * @param events the events
     * @return an UnencryptedObservation batch holding the generated observations
     */
    private UnencryptedObservationBatch generateObservations(
            int dayIndex,
            SystemProfile systemProfile,
            ImmutableList<EventRecordAndSystemProfile> events) {
        if (mReport.getEventVectorBufferMax() != 0
                && events.size() > mReport.getEventVectorBufferMax()) {
            // Each EventRecordAndSystemProfile contains a unique event vector for the system
            // profile and day so the number of events can be compared to the event vector
            // buffer max of the report.
            events = events.subList(0, (int) mReport.getEventVectorBufferMax());
        }

        ImmutableList.Builder<Observation> observations = ImmutableList.builder();
        for (EventRecordAndSystemProfile event : events) {
            observations.add(
                    Observation.newBuilder()
                            .setPrivateIndex(
                                    mEncoder.encode(event.eventVector(), event.aggregateValue()))
                            .setRandomId(RandomId.generate(mSecureRandom))
                            .build());
        }
        for (PrivateIndexObservation privateIndex :
                mPrivacyGenerator.generateNoise(maxIndexForReport(), mReport)) {
            observations.add(
                    Observation.newBuilder()
                            .setPrivateIndex(privateIndex)
                            .setRandomId(RandomId.generate(mSecureRandom))
                            .build());
        }
        observations.add(
                Observation.newBuilder()
                        .setReportParticipation(ReportParticipationObservation.getDefaultInstance())
                        .setRandomId(RandomId.generate(mSecureRandom))
                        .build());

        ImmutableList.Builder<ObservationToEncrypt> toEncrypt = ImmutableList.builder();
        boolean setContributionId = true;
        for (Observation observation : observations.build()) {
            ObservationToEncrypt.Builder builder = ObservationToEncrypt.newBuilder();
            builder.setObservation(observation);
            if (setContributionId) {
                builder.setContributionId(RandomId.generate(mSecureRandom));
            }

            // Reports with privacy enabled split a single contribution across multiple
            // observations, both private and participation. However, only 1 needs the contribution
            // id set.
            toEncrypt.add(builder.build());
            setContributionId = false;
        }

        return UnencryptedObservationBatch.newBuilder()
                .setMetadata(
                        ObservationMetadata.newBuilder()
                                .setCustomerId(mCustomerId)
                                .setProjectId(mProjectId)
                                .setMetricId(mMetric.getId())
                                .setReportId(mReport.getId())
                                .setDayIndex(dayIndex)
                                .setSystemProfile(systemProfile))
                .addAllUnencryptedObservations(toEncrypt.build())
                .build();
    }

    private int maxIndexForReport() {
        return PrivateIndexCalculations.getNumEventVectors(mMetric.getMetricDimensionsList())
                        * mReport.getNumIndexPoints()
                - 1;
    }
}
