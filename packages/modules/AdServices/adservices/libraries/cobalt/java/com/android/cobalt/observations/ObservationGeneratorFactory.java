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

import com.android.cobalt.data.DaoBuildingBlocks;
import com.android.cobalt.data.ObservationGenerator;
import com.android.cobalt.data.ReportKey;
import com.android.cobalt.data.StringListEntry;
import com.android.cobalt.domain.Project;
import com.android.cobalt.system.SystemData;

import com.google.cobalt.MetricDefinition;
import com.google.cobalt.ReportDefinition;
import com.google.common.collect.ImmutableList;

import java.security.SecureRandom;

/** Creates {@link ObservationGenerator} instances for reports. */
public final class ObservationGeneratorFactory {
    private final Project mProject;
    private final SystemData mSystemData;
    private final DaoBuildingBlocks mDaoBuildingBlocks;
    private final PrivacyGenerator mPrivacyGenerator;
    private final SecureRandom mSecureRandom;

    public ObservationGeneratorFactory(
            @NonNull Project project,
            @NonNull SystemData systemData,
            @NonNull DaoBuildingBlocks daoBuildingBlocks,
            @NonNull PrivacyGenerator privacyGenerator,
            @NonNull SecureRandom secureRandom) {
        mProject = requireNonNull(project);
        mSystemData = requireNonNull(systemData);
        mDaoBuildingBlocks = requireNonNull(daoBuildingBlocks);
        mPrivacyGenerator = requireNonNull(privacyGenerator);
        mSecureRandom = requireNonNull(secureRandom);
    }

    /**
     * Creates an {@link ObservationGenerator} instance for a report.
     *
     * <p>Only FLEETWIDE_OCCURRENCE_COUNTS and non-private STRING_COUNTS are supported.
     *
     * <p>Note, day index is needed because some encoders require day-dependent data from the
     * database to be provided in their constructor. It is NOT recommended that this method be used
     * outside of a database transaction if a consistent view is required. be done elsewhere.
     *
     * <p>It should be considered if getting the string hash list can be done in the DataService.
     *
     * @param metric the metric observations are being generated for
     * @param report the metric observations are being generated for
     * @param dayIndex the day index observations are being generated for
     * @return the {@link ObservationGenerator} required for the provided report
     */
    public ObservationGenerator getObservationGenerator(
            MetricDefinition metric, ReportDefinition report, int dayIndex) {
        switch (report.getPrivacyLevel()) {
            case NO_ADDED_PRIVACY:
                return getNonPrivateObservationGenerator(metric, report, dayIndex);
            case LOW_PRIVACY:
            case MEDIUM_PRIVACY:
            case HIGH_PRIVACY:
                return getPrivateObservationGenerator(metric, report);
            default:
                throw new AssertionError(
                        "Unknown or unset privacy level: " + report.getPrivacyLevelValue());
        }
    }

    private NonPrivateObservationGenerator getNonPrivateObservationGenerator(
            MetricDefinition metric, ReportDefinition report, int dayIndex) {
        switch (report.getReportType()) {
            case FLEETWIDE_OCCURRENCE_COUNTS:
                return new NonPrivateObservationGenerator(
                        mSecureRandom,
                        new IntegerEncoder(mSecureRandom),
                        mProject.getCustomerId(),
                        mProject.getProjectId(),
                        metric.getId(),
                        report);
            case STRING_COUNTS:
                ImmutableList<StringListEntry> stringHashList =
                        ImmutableList.copyOf(
                                mDaoBuildingBlocks.queryStringHashList(
                                        ReportKey.create(
                                                (long) mProject.getCustomerId(),
                                                (long) mProject.getProjectId(),
                                                (long) metric.getId(),
                                                (long) report.getId()),
                                        dayIndex));
                return new NonPrivateObservationGenerator(
                        mSecureRandom,
                        new StringHistogramEncoder(stringHashList, mSecureRandom),
                        mProject.getCustomerId(),
                        mProject.getProjectId(),
                        metric.getId(),
                        report);
            default:
                throw new AssertionError(
                        "Unrecognized or unsupported report type: " + report.getReportTypeValue());
        }
    }

    private PrivateObservationGenerator getPrivateObservationGenerator(
            MetricDefinition metric, ReportDefinition report) {
        switch (report.getReportType()) {
            case FLEETWIDE_OCCURRENCE_COUNTS:
                return new PrivateObservationGenerator(
                        mSystemData,
                        mPrivacyGenerator,
                        mSecureRandom,
                        new PrivateIntegerEncoder(mSecureRandom, metric, report),
                        mProject.getCustomerId(),
                        mProject.getProjectId(),
                        metric,
                        report);
            default:
                throw new AssertionError(
                        "Unrecognized or unsupported report type: " + report.getReportTypeValue());
        }
    }
}
