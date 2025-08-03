/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.healthconnect.cts.aggregation;

import static android.health.connect.datatypes.TotalCaloriesBurnedRecord.ENERGY_TOTAL;
import static android.healthconnect.cts.aggregation.DataFactory.getBasalMetabolicRateRecord;
import static android.healthconnect.cts.aggregation.Utils.assertEnergyWithTolerance;
import static android.healthconnect.cts.utils.DataFactory.getDataOrigin;
import static android.healthconnect.cts.utils.DataFactory.getEmptyMetadata;
import static android.healthconnect.cts.utils.TestUtils.deleteAllStagedRemoteData;
import static android.healthconnect.cts.utils.TestUtils.getAggregateResponse;
import static android.healthconnect.cts.utils.TestUtils.getAggregateResponseGroupByDuration;
import static android.healthconnect.cts.utils.TestUtils.getAggregateResponseGroupByPeriod;
import static android.healthconnect.cts.utils.TestUtils.insertRecords;
import static android.healthconnect.cts.utils.TestUtils.setupAggregation;

import static com.google.common.truth.Truth.assertThat;

import static java.time.temporal.ChronoUnit.DAYS;

import android.health.connect.AggregateRecordsGroupedByDurationResponse;
import android.health.connect.AggregateRecordsRequest;
import android.health.connect.AggregateRecordsResponse;
import android.health.connect.HealthDataCategory;
import android.health.connect.LocalTimeRangeFilter;
import android.health.connect.TimeInstantRangeFilter;
import android.health.connect.datatypes.ActiveCaloriesBurnedRecord;
import android.health.connect.datatypes.DataOrigin;
import android.health.connect.datatypes.TotalCaloriesBurnedRecord;
import android.health.connect.datatypes.units.Energy;
import android.healthconnect.cts.utils.AssumptionCheckerRule;
import android.healthconnect.cts.utils.TestUtils;

import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

public class TotalCaloriesAggregationTest {
    private final String mPackageName =
            ApplicationProvider.getApplicationContext().getPackageName();

    @Rule
    public AssumptionCheckerRule mSupportedHardwareRule =
            new AssumptionCheckerRule(
                    TestUtils::isHardwareSupported, "Tests should run on supported hardware only.");

    @Before
    public void setUp() throws InterruptedException {
        deleteAllStagedRemoteData();
        setupAggregation(mPackageName, HealthDataCategory.ACTIVITY);
    }

    @After
    public void tearDown() {
        deleteAllStagedRemoteData();
    }

    @Test
    public void testAggregation_totalCaloriesBurnt() throws Exception {
        Instant now = Instant.now();
        insertRecords(
                List.of(
                        getBaseTotalCaloriesBurnedRecord(now.minus(1, DAYS)),
                        getBaseTotalCaloriesBurnedRecord(now.minus(2, DAYS))));
        AggregateRecordsResponse<Energy> oldResponse =
                getAggregateResponse(
                        new AggregateRecordsRequest.Builder<Energy>(
                                        new TimeInstantRangeFilter.Builder()
                                                .setStartTime(now.minus(5, DAYS))
                                                .setEndTime(now)
                                                .build())
                                .addAggregationType(ENERGY_TOTAL)
                                .addDataOriginsFilter(getDataOrigin(mPackageName))
                                .build());

        insertRecords(
                List.of(
                        getBaseTotalCaloriesBurnedRecord(now.minus(3, DAYS)),
                        getBaseTotalCaloriesBurnedRecord(now.minus(4, DAYS))));
        AggregateRecordsResponse<Energy> newResponse =
                getAggregateResponse(
                        new AggregateRecordsRequest.Builder<Energy>(
                                        new TimeInstantRangeFilter.Builder()
                                                .setStartTime(now.minus(5, DAYS))
                                                .setEndTime(now)
                                                .build())
                                .addAggregationType(ENERGY_TOTAL)
                                .addDataOriginsFilter(getDataOrigin(mPackageName))
                                .build());

        Energy totEnergyBefore = oldResponse.get(ENERGY_TOTAL);
        Energy totEnergyAfter = newResponse.get(ENERGY_TOTAL);
        assertThat(totEnergyBefore).isNotNull();
        assertThat(totEnergyAfter).isNotNull();
        // The default total calories burned for one day is approx 1564.5 kCals
        assertThat(totEnergyBefore.getInCalories()).isWithin(1).of(4_693_520);
        assertThat(totEnergyAfter.getInCalories()).isWithin(1).of(1_564_540);

        Set<DataOrigin> newDataOrigin = newResponse.getDataOrigins(ENERGY_TOTAL);
        for (DataOrigin itr : newDataOrigin) {
            assertThat(itr.getPackageName()).isEqualTo("android.healthconnect.cts");
        }
        Set<DataOrigin> oldDataOrigin = oldResponse.getDataOrigins(ENERGY_TOTAL);
        for (DataOrigin itr : oldDataOrigin) {
            assertThat(itr.getPackageName()).isEqualTo("android.healthconnect.cts");
        }
    }

    @Test
    public void testAggregation_totalCaloriesBurnt_activeCalories() throws Exception {
        Instant now = Instant.now();
        insertRecords(
                Arrays.asList(
                        getBaseTotalCaloriesBurnedRecord(now.minus(1, DAYS)),
                        getBaseTotalCaloriesBurnedRecord(now.minus(2, DAYS)),
                        getBaseActiveCaloriesBurnedRecord(now.minus(4, DAYS), 20)));

        AggregateRecordsResponse<Energy> response =
                getAggregateResponse(
                        new AggregateRecordsRequest.Builder<Energy>(
                                        new TimeInstantRangeFilter.Builder()
                                                .setStartTime(now.minus(5, DAYS))
                                                .setEndTime(now)
                                                .build())
                                .addAggregationType(ENERGY_TOTAL)
                                .addDataOriginsFilter(getDataOrigin(mPackageName))
                                .build());

        assertEnergyWithTolerance(response.get(ENERGY_TOTAL), 4693540);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testAggregation_totalCaloriesBurnt_activeCalories_groupBy() throws Exception {
        Instant now = Instant.now();
        getAggregateResponseGroupByPeriod(
                new AggregateRecordsRequest.Builder<Energy>(
                                new TimeInstantRangeFilter.Builder()
                                        .setStartTime(now.minus(5, DAYS))
                                        .setEndTime(now)
                                        .build())
                        .addAggregationType(ENERGY_TOTAL)
                        .addDataOriginsFilter(getDataOrigin(mPackageName))
                        .build(),
                Period.ofDays(1));
    }

    @Test
    public void testAggregation_totalCaloriesBurnt_activeCalories_groupBy_duration()
            throws Exception {
        Instant now = Instant.now();
        insertRecords(
                List.of(
                        getBaseTotalCaloriesBurnedRecord(now.minus(1, DAYS), 10),
                        getBaseTotalCaloriesBurnedRecord(now.minus(2, DAYS), 20),
                        getBaseActiveCaloriesBurnedRecord(now.minus(4, DAYS), 20),
                        getBasalMetabolicRateRecord(30, now.minus(3, DAYS))));

        List<AggregateRecordsGroupedByDurationResponse<Energy>> responses =
                getAggregateResponseGroupByDuration(
                        new AggregateRecordsRequest.Builder<Energy>(
                                        new TimeInstantRangeFilter.Builder()
                                                .setStartTime(now.minus(5, DAYS))
                                                .setEndTime(now)
                                                .build())
                                .addAggregationType(ENERGY_TOTAL)
                                .addDataOriginsFilter(getDataOrigin(mPackageName))
                                .build(),
                        Duration.ofDays(1));

        assertThat(responses).hasSize(5);
        assertEnergyWithTolerance(responses.get(0).get(ENERGY_TOTAL), 1564500);
        assertEnergyWithTolerance(responses.get(1).get(ENERGY_TOTAL), 1564520);
        assertEnergyWithTolerance(responses.get(2).get(ENERGY_TOTAL), 619200);
        assertEnergyWithTolerance(responses.get(3).get(ENERGY_TOTAL), 20);
        assertEnergyWithTolerance(responses.get(4).get(ENERGY_TOTAL), 10);
    }

    @Test
    public void testAggregation_groupByDurationLocalFilter_shiftRecordsAndFilterWithOffset()
            throws Exception {
        Instant now = Instant.now();
        ZoneOffset offset = ZoneOffset.ofHours(-1);
        LocalDateTime localNow = LocalDateTime.ofInstant(now, offset);

        insertRecords(
                Arrays.asList(
                        getBaseTotalCaloriesBurnedRecord(now.minus(1, DAYS), 10, offset),
                        getBaseTotalCaloriesBurnedRecord(now.minus(2, DAYS), 20, offset),
                        getBaseActiveCaloriesBurnedRecord(now.minus(4, DAYS), 20, offset),
                        getBasalMetabolicRateRecord(30, now.minus(3, DAYS), offset)));

        List<AggregateRecordsGroupedByDurationResponse<Energy>> responses =
                getAggregateResponseGroupByDuration(
                        new AggregateRecordsRequest.Builder<Energy>(
                                        new LocalTimeRangeFilter.Builder()
                                                .setStartTime(localNow.minusDays(5))
                                                .setEndTime(localNow)
                                                .build())
                                .addAggregationType(ENERGY_TOTAL)
                                .addDataOriginsFilter(getDataOrigin(mPackageName))
                                .build(),
                        Duration.ofDays(1));

        assertThat(responses).hasSize(5);
        assertEnergyWithTolerance(responses.get(0).get(ENERGY_TOTAL), 1564500);
        assertEnergyWithTolerance(responses.get(1).get(ENERGY_TOTAL), 1564520);
        assertEnergyWithTolerance(responses.get(2).get(ENERGY_TOTAL), 619200);
        assertEnergyWithTolerance(responses.get(3).get(ENERGY_TOTAL), 20);
        assertEnergyWithTolerance(responses.get(4).get(ENERGY_TOTAL), 10);
    }

    private static TotalCaloriesBurnedRecord getBaseTotalCaloriesBurnedRecord(Instant startTime) {
        return new TotalCaloriesBurnedRecord.Builder(
                        getEmptyMetadata(),
                        startTime,
                        startTime.plus(1, DAYS),
                        Energy.fromCalories(10.0))
                .build();
    }

    private static TotalCaloriesBurnedRecord getBaseTotalCaloriesBurnedRecord(
            Instant startTime, double value) {
        return getBaseTotalCaloriesBurnedRecord(startTime, value, null);
    }

    private static TotalCaloriesBurnedRecord getBaseTotalCaloriesBurnedRecord(
            Instant startTime, double value, ZoneOffset offset) {
        TotalCaloriesBurnedRecord.Builder builder =
                new TotalCaloriesBurnedRecord.Builder(
                        getEmptyMetadata(),
                        startTime,
                        startTime.plus(1, DAYS),
                        Energy.fromCalories(value));

        if (offset != null) {
            builder.setStartZoneOffset(offset).setEndZoneOffset(offset);
        }
        return builder.build();
    }

    private static ActiveCaloriesBurnedRecord getBaseActiveCaloriesBurnedRecord(
            Instant startTime, double energy) {
        return new ActiveCaloriesBurnedRecord.Builder(
                        getEmptyMetadata(),
                        startTime,
                        startTime.plus(1, DAYS),
                        Energy.fromCalories(energy))
                .build();
    }

    private static ActiveCaloriesBurnedRecord getBaseActiveCaloriesBurnedRecord(
            Instant startTime, double energy, ZoneOffset offset) {
        return new ActiveCaloriesBurnedRecord.Builder(
                        getEmptyMetadata(),
                        startTime,
                        startTime.plus(1, DAYS),
                        Energy.fromCalories(energy))
                .setStartZoneOffset(offset)
                .setEndZoneOffset(offset)
                .build();
    }
}
