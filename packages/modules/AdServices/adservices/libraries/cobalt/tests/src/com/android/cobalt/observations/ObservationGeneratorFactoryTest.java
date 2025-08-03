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

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.android.adservices.common.AdServicesMockitoTestCase;
import com.android.cobalt.data.DaoBuildingBlocks;
import com.android.cobalt.data.ObservationGenerator;
import com.android.cobalt.data.ReportKey;
import com.android.cobalt.data.StringListEntry;
import com.android.cobalt.domain.Project;
import com.android.cobalt.observations.testing.FakeSecureRandom;
import com.android.cobalt.system.SystemData;

import com.google.cobalt.MetricDefinition;
import com.google.cobalt.ReportDefinition;
import com.google.cobalt.ReportDefinition.PrivacyLevel;
import com.google.cobalt.ReportDefinition.ReportType;
import com.google.common.collect.ImmutableList;
import com.google.common.hash.HashCode;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;

import java.security.SecureRandom;
import java.util.List;

@RunWith(JUnit4.class)
public final class ObservationGeneratorFactoryTest extends AdServicesMockitoTestCase {
    private static final int CUSTOMER_ID = 1;
    private static final int PROJECT_ID = 2;
    private static final int METRIC_ID = 3;
    private static final int REPORT_ID = 4;
    private static final int UNUSED_DAY_INDEX = 12345;

    @Mock private DaoBuildingBlocks mDaoBuildingBlocks;
    private ObservationGeneratorFactory mFactory;

    @Before
    public void setup() {
        Project project = Project.create(CUSTOMER_ID, PROJECT_ID, /* metrics= */ List.of());
        SecureRandom secureRandom = new FakeSecureRandom();
        mFactory =
                new ObservationGeneratorFactory(
                        project,
                        new SystemData(),
                        mDaoBuildingBlocks,
                        new PrivacyGenerator(secureRandom),
                        secureRandom);
    }

    @Test
    public void getObservationGenerator_nonPrivateFleetwideOccurrenceCounts() throws Exception {
        ObservationGenerator generator =
                mFactory.getObservationGenerator(
                        MetricDefinition.getDefaultInstance(),
                        ReportDefinition.newBuilder()
                                .setReportType(ReportType.FLEETWIDE_OCCURRENCE_COUNTS)
                                .setPrivacyLevel(PrivacyLevel.NO_ADDED_PRIVACY)
                                .build(),
                        UNUSED_DAY_INDEX);

        assertThat(generator).isInstanceOf(NonPrivateObservationGenerator.class);
    }

    @Test
    public void getObservationGenerator_privateFleetwideOccurrenceCounts() throws Exception {
        ObservationGenerator generator =
                mFactory.getObservationGenerator(
                        MetricDefinition.getDefaultInstance(),
                        ReportDefinition.newBuilder()
                                .setReportType(ReportType.FLEETWIDE_OCCURRENCE_COUNTS)
                                .setPrivacyLevel(PrivacyLevel.HIGH_PRIVACY)
                                .build(),
                        UNUSED_DAY_INDEX);

        assertThat(generator).isInstanceOf(PrivateObservationGenerator.class);
    }

    @Test
    public void getObservationGenerator_fleetwideOccurrenceCounts_noPrivacyLevelSet()
            throws Exception {
        assertThrows(
                AssertionError.class,
                () ->
                        mFactory.getObservationGenerator(
                                MetricDefinition.getDefaultInstance(),
                                ReportDefinition.newBuilder()
                                        .setReportType(ReportType.FLEETWIDE_OCCURRENCE_COUNTS)
                                        .build(),
                                UNUSED_DAY_INDEX));
    }

    @Test
    public void getObservationGenerator_nonPrivateStringsCounts() throws Exception {
        ObservationGenerator generator =
                mFactory.getObservationGenerator(
                        MetricDefinition.getDefaultInstance(),
                        ReportDefinition.newBuilder()
                                .setReportType(ReportType.STRING_COUNTS)
                                .setPrivacyLevel(PrivacyLevel.NO_ADDED_PRIVACY)
                                .build(),
                        UNUSED_DAY_INDEX);

        assertThat(generator).isInstanceOf(NonPrivateObservationGenerator.class);
    }

    @Test
    public void getObservationGenerator_nonPrivateStringsCounts_queriesStringHashList()
            throws Exception {
        ReportKey reportKey = ReportKey.create(CUSTOMER_ID, PROJECT_ID, METRIC_ID, REPORT_ID);
        int dayIndex = 10;

        when(mDaoBuildingBlocks.queryStringHashList(reportKey, dayIndex))
                .thenReturn(
                        ImmutableList.of(
                                StringListEntry.create(1, HashCode.fromInt(100)),
                                StringListEntry.create(3, HashCode.fromInt(101))));

        ObservationGenerator generator =
                mFactory.getObservationGenerator(
                        MetricDefinition.newBuilder().setId(METRIC_ID).build(),
                        ReportDefinition.newBuilder()
                                .setId(REPORT_ID)
                                .setReportType(ReportType.STRING_COUNTS)
                                .setPrivacyLevel(PrivacyLevel.NO_ADDED_PRIVACY)
                                .build(),
                        dayIndex);

        verify(mDaoBuildingBlocks).queryStringHashList(reportKey, dayIndex);
        verifyNoMoreInteractions(mDaoBuildingBlocks);

        assertThat(generator).isInstanceOf(NonPrivateObservationGenerator.class);
    }

    @Test
    public void getObservationGenerator_privateStringsCounts() throws Exception {
        assertThrows(
                AssertionError.class,
                () ->
                        mFactory.getObservationGenerator(
                                MetricDefinition.getDefaultInstance(),
                                ReportDefinition.newBuilder()
                                        .setReportType(ReportType.STRING_COUNTS)
                                        .setPrivacyLevel(PrivacyLevel.HIGH_PRIVACY)
                                        .build(),
                                UNUSED_DAY_INDEX));
    }

    @Test
    public void getObservationGenerator_stringsCounts_noPrivacyLevelSet() throws Exception {
        assertThrows(
                AssertionError.class,
                () ->
                        mFactory.getObservationGenerator(
                                MetricDefinition.getDefaultInstance(),
                                ReportDefinition.newBuilder()
                                        .setReportType(ReportType.STRING_COUNTS)
                                        .build(),
                                UNUSED_DAY_INDEX));
    }

    @Test
    public void getObservationGenerator_reportTypeNotSet_throwsAssertionError() throws Exception {
        assertThrows(
                AssertionError.class,
                () ->
                        mFactory.getObservationGenerator(
                                MetricDefinition.getDefaultInstance(),
                                ReportDefinition.newBuilder().build(),
                                UNUSED_DAY_INDEX));
    }

    @Test
    public void getObservationGenerator_reportTypeUnset_throwsAssertionError() throws Exception {
        assertThrows(
                AssertionError.class,
                () ->
                        mFactory.getObservationGenerator(
                                MetricDefinition.getDefaultInstance(),
                                ReportDefinition.newBuilder()
                                        .setReportType(ReportType.REPORT_TYPE_UNSET)
                                        .build(),
                                UNUSED_DAY_INDEX));
    }

    @Test
    public void getObservationGenerator_uniqueDeviceCounts_throwsAssertionError() throws Exception {
        assertThrows(
                AssertionError.class,
                () ->
                        mFactory.getObservationGenerator(
                                MetricDefinition.getDefaultInstance(),
                                ReportDefinition.newBuilder()
                                        .setReportType(ReportType.UNIQUE_DEVICE_COUNTS)
                                        .build(),
                                UNUSED_DAY_INDEX));
    }

    @Test
    public void getObservationGenerator_uniqueDeviceHistograms_throwsAssertionError()
            throws Exception {
        assertThrows(
                AssertionError.class,
                () ->
                        mFactory.getObservationGenerator(
                                MetricDefinition.getDefaultInstance(),
                                ReportDefinition.newBuilder()
                                        .setReportType(ReportType.UNIQUE_DEVICE_HISTOGRAMS)
                                        .build(),
                                UNUSED_DAY_INDEX));
    }

    @Test
    public void getObservationGenerator_hourlyValueHistograms_throwsAssertionError()
            throws Exception {
        assertThrows(
                AssertionError.class,
                () ->
                        mFactory.getObservationGenerator(
                                MetricDefinition.getDefaultInstance(),
                                ReportDefinition.newBuilder()
                                        .setReportType(ReportType.HOURLY_VALUE_HISTOGRAMS)
                                        .build(),
                                UNUSED_DAY_INDEX));
    }

    @Test
    public void getObservationGenerator_fleetwideHistograms_throwsAssertionError()
            throws Exception {
        assertThrows(
                AssertionError.class,
                () ->
                        mFactory.getObservationGenerator(
                                MetricDefinition.getDefaultInstance(),
                                ReportDefinition.newBuilder()
                                        .setReportType(ReportType.FLEETWIDE_HISTOGRAMS)
                                        .build(),
                                UNUSED_DAY_INDEX));
    }

    @Test
    public void getObservationGenerator_fleetwideMeans_throwsAssertionError() throws Exception {
        assertThrows(
                AssertionError.class,
                () ->
                        mFactory.getObservationGenerator(
                                MetricDefinition.getDefaultInstance(),
                                ReportDefinition.newBuilder()
                                        .setReportType(ReportType.FLEETWIDE_MEANS)
                                        .build(),
                                UNUSED_DAY_INDEX));
    }

    @Test
    public void getObservationGenerator_uniqueDeviceNumericStats_throwsAssertionError()
            throws Exception {
        assertThrows(
                AssertionError.class,
                () ->
                        mFactory.getObservationGenerator(
                                MetricDefinition.getDefaultInstance(),
                                ReportDefinition.newBuilder()
                                        .setReportType(ReportType.UNIQUE_DEVICE_NUMERIC_STATS)
                                        .build(),
                                UNUSED_DAY_INDEX));
    }

    @Test
    public void getObservationGenerator_hourlyValueNumericStats_throwsAssertionError()
            throws Exception {
        assertThrows(
                AssertionError.class,
                () ->
                        mFactory.getObservationGenerator(
                                MetricDefinition.getDefaultInstance(),
                                ReportDefinition.newBuilder()
                                        .setReportType(ReportType.HOURLY_VALUE_NUMERIC_STATS)
                                        .build(),
                                UNUSED_DAY_INDEX));
    }

    @Test
    public void getObservationGenerator_uniqueDeviceStringCounts_throwsAssertionError()
            throws Exception {
        assertThrows(
                AssertionError.class,
                () ->
                        mFactory.getObservationGenerator(
                                MetricDefinition.getDefaultInstance(),
                                ReportDefinition.newBuilder()
                                        .setReportType(ReportType.UNIQUE_DEVICE_STRING_COUNTS)
                                        .build(),
                                UNUSED_DAY_INDEX));
    }
}
