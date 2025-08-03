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
import com.android.cobalt.data.ObservationGenerator;

import com.google.cobalt.Observation;
import com.google.cobalt.ObservationMetadata;
import com.google.cobalt.ObservationToEncrypt;
import com.google.cobalt.ReportDefinition;
import com.google.cobalt.SystemProfile;
import com.google.cobalt.UnencryptedObservationBatch;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;

import java.security.SecureRandom;
import java.util.Collection;
import java.util.Map;

/** Generates non-private observations from event data. */
final class NonPrivateObservationGenerator implements ObservationGenerator {
    /** Interface to encode aggregated values as observations for non-private reports. */
    interface Encoder {
        /**
         * Encodes multiple events for the same report, day, and system profile as a single
         * non-private observation.
         *
         * @param events the events that need to be encoded
         * @return a non-private observation that contains all input event data
         */
        Observation encode(ImmutableList<EventRecordAndSystemProfile> events);
    }

    private final SecureRandom mSecureRandom;
    private final Encoder mEncoder;
    private final int mCustomerId;
    private final int mProjectId;
    private final int mMetricId;
    private final ReportDefinition mReport;

    NonPrivateObservationGenerator(
            @NonNull SecureRandom secureRandom,
            @NonNull Encoder encoder,
            int customerId,
            int projectId,
            int metricId,
            ReportDefinition report) {
        this.mSecureRandom = requireNonNull(secureRandom);
        this.mEncoder = requireNonNull(encoder);
        this.mCustomerId = customerId;
        this.mProjectId = projectId;
        this.mMetricId = metricId;
        this.mReport = report;
    }

    /**
     * Generate the non-private observations that occurred for a report and day.
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
        ImmutableList.Builder<UnencryptedObservationBatch> batches = ImmutableList.builder();
        for (Map.Entry<SystemProfile, Collection<EventRecordAndSystemProfile>> eventData :
                allEventData.asMap().entrySet()) {
            SystemProfile systemProfile = eventData.getKey();
            ImmutableList<EventRecordAndSystemProfile> events =
                    ImmutableList.copyOf(eventData.getValue());
            if (mReport.getEventVectorBufferMax() != 0
                    && events.size() > mReport.getEventVectorBufferMax()) {
                // Each EventRecordAndSystemProfile contains a unique event vector for the
                // system profile and day so the number of events can be compared to the event
                // vector buffer max of the report.
                events = events.subList(0, (int) mReport.getEventVectorBufferMax());
            }

            ObservationToEncrypt observation =
                    ObservationToEncrypt.newBuilder()
                            .setObservation(mEncoder.encode(events))
                            .setContributionId(RandomId.generate(mSecureRandom))
                            .build();
            UnencryptedObservationBatch.Builder batch =
                    UnencryptedObservationBatch.newBuilder()
                            .setMetadata(
                                    ObservationMetadata.newBuilder()
                                            .setCustomerId(mCustomerId)
                                            .setProjectId(mProjectId)
                                            .setMetricId(mMetricId)
                                            .setReportId(mReport.getId())
                                            .setDayIndex(dayIndex)
                                            .setSystemProfile(systemProfile))
                            .addUnencryptedObservations(observation);
            batches.add(batch.build());
        }
        return batches.build();
    }
}
