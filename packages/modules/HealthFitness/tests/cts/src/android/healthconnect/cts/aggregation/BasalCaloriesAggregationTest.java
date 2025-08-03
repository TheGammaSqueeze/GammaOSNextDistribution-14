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

import static android.health.connect.datatypes.BasalMetabolicRateRecord.BASAL_CALORIES_TOTAL;
import static android.healthconnect.cts.aggregation.DataFactory.getBasalMetabolicRateRecord;
import static android.healthconnect.cts.aggregation.Utils.assertEnergyWithTolerance;
import static android.healthconnect.cts.utils.DataFactory.getEmptyMetadata;
import static android.healthconnect.cts.utils.TestUtils.deleteAllStagedRemoteData;
import static android.healthconnect.cts.utils.TestUtils.getAggregateResponse;
import static android.healthconnect.cts.utils.TestUtils.getAggregateResponseGroupByDuration;
import static android.healthconnect.cts.utils.TestUtils.insertRecords;
import static android.healthconnect.cts.utils.TestUtils.setupAggregation;

import static com.google.common.truth.Truth.assertThat;

import static java.time.temporal.ChronoUnit.DAYS;
import static java.time.temporal.ChronoUnit.MINUTES;

import android.content.Context;
import android.health.connect.AggregateRecordsGroupedByDurationResponse;
import android.health.connect.AggregateRecordsRequest;
import android.health.connect.AggregateRecordsResponse;
import android.health.connect.HealthDataCategory;
import android.health.connect.LocalTimeRangeFilter;
import android.health.connect.TimeInstantRangeFilter;
import android.health.connect.datatypes.DataOrigin;
import android.health.connect.datatypes.HeightRecord;
import android.health.connect.datatypes.LeanBodyMassRecord;
import android.health.connect.datatypes.WeightRecord;
import android.health.connect.datatypes.units.Energy;
import android.health.connect.datatypes.units.Length;
import android.health.connect.datatypes.units.Mass;
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
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

public class BasalCaloriesAggregationTest {
    @Rule
    public AssumptionCheckerRule mSupportedHardwareRule =
            new AssumptionCheckerRule(
                    TestUtils::isHardwareSupported, "Tests should run on supported hardware only.");

    @Before
    public void setUp() throws InterruptedException {
        Context context = ApplicationProvider.getApplicationContext();
        String packageName = context.getPackageName();

        deleteAllStagedRemoteData();
        setupAggregation(packageName, HealthDataCategory.BODY_MEASUREMENTS);
    }

    @After
    public void tearDown() {
        deleteAllStagedRemoteData();
    }

    @Test
    public void testAggregation_BasalCaloriesBurntTotal_noRecord() throws Exception {
        Instant now = Instant.now();

        AggregateRecordsResponse<Energy> response =
                getAggregateResponse(
                        new AggregateRecordsRequest.Builder<Energy>(
                                        new TimeInstantRangeFilter.Builder()
                                                .setStartTime(now.minus(2, DAYS))
                                                .setEndTime(now.minus(1, DAYS))
                                                .build())
                                .addAggregationType(BASAL_CALORIES_TOTAL)
                                .build());

        assertEnergyWithTolerance(response.get(BASAL_CALORIES_TOTAL), 1564500);
    }

    @Test
    public void testAggregation_BasalCaloriesBurntTotal_lbm() throws Exception {
        Instant now = Instant.now();
        insertRecords(List.of(getBaseLeanBodyMassRecord(Instant.now(), 50000)));
        AggregateRecordsResponse<Energy> response =
                getAggregateResponse(
                        new AggregateRecordsRequest.Builder<Energy>(
                                        new TimeInstantRangeFilter.Builder()
                                                .setStartTime(Instant.now().minus(2, DAYS))
                                                .setEndTime(Instant.now().minus(1, DAYS))
                                                .build())
                                .addAggregationType(BASAL_CALORIES_TOTAL)
                                .build());
        assertEnergyWithTolerance(response.get(BASAL_CALORIES_TOTAL), 1564500);

        insertRecords(List.of(getBaseLeanBodyMassRecord(now.minus(2, DAYS), 50000)));
        response =
                getAggregateResponse(
                        new AggregateRecordsRequest.Builder<Energy>(
                                        new TimeInstantRangeFilter.Builder()
                                                .setStartTime(Instant.now().minus(2, DAYS))
                                                .setEndTime(Instant.now().minus(1, DAYS))
                                                .build())
                                .addAggregationType(BASAL_CALORIES_TOTAL)
                                .build());
        assertEnergyWithTolerance(response.get(BASAL_CALORIES_TOTAL), 1450000);
    }

    @Test
    public void testAggregation_BasalCaloriesBurntTotal_lbm_group() throws Exception {
        Instant now = Instant.now();
        insertRecords(
                List.of(
                        getBaseLeanBodyMassRecord(now, 50000),
                        getBaseLeanBodyMassRecord(now.minus(1, DAYS), 40000),
                        getBaseLeanBodyMassRecord(now.minus(2, DAYS), 30000),
                        getBaseLeanBodyMassRecord(now.minus(3, DAYS), 20000)));
        List<AggregateRecordsGroupedByDurationResponse<Energy>> responses =
                getAggregateResponseGroupByDuration(
                        new AggregateRecordsRequest.Builder<Energy>(
                                        new TimeInstantRangeFilter.Builder()
                                                .setStartTime(now.minus(3, DAYS))
                                                .setEndTime(now)
                                                .build())
                                .addAggregationType(BASAL_CALORIES_TOTAL)
                                .build(),
                        Duration.ofDays(1));
        assertThat(responses).hasSize(3);
        assertEnergyWithTolerance(responses.get(0).get(BASAL_CALORIES_TOTAL), 802000);
        assertEnergyWithTolerance(responses.get(1).get(BASAL_CALORIES_TOTAL), 1018000);
        assertEnergyWithTolerance(responses.get(2).get(BASAL_CALORIES_TOTAL), 1234000);

        AggregateRecordsResponse<Energy> response =
                getAggregateResponse(
                        new AggregateRecordsRequest.Builder<Energy>(
                                        new TimeInstantRangeFilter.Builder()
                                                .setStartTime(now.minus(3, DAYS))
                                                .setEndTime(now)
                                                .build())
                                .addAggregationType(BASAL_CALORIES_TOTAL)
                                .build());
        assertEnergyWithTolerance(response.get(BASAL_CALORIES_TOTAL), 3054000);
    }

    @Test
    public void testAggregation_BasalCaloriesBurntTotal_groupByDuration_lbmDerived()
            throws Exception {
        Instant now = Instant.now();
        insertRecords(
                List.of(
                        getBaseLeanBodyMassRecord(now, 50000),
                        getBaseLeanBodyMassRecord(now.minus(1, DAYS), 40000),
                        getBaseLeanBodyMassRecord(now.minus(2, DAYS), 30000),
                        getBaseLeanBodyMassRecord(now.minus(3, DAYS), 20000)));

        List<AggregateRecordsGroupedByDurationResponse<Energy>> responses =
                getAggregateResponseGroupByDuration(
                        new AggregateRecordsRequest.Builder<Energy>(
                                        new TimeInstantRangeFilter.Builder()
                                                .setStartTime(now.minus(3, DAYS))
                                                .setEndTime(now)
                                                .build())
                                .addAggregationType(BASAL_CALORIES_TOTAL)
                                .build(),
                        Duration.ofDays(1));

        assertThat(responses).hasSize(3);
        assertEnergyWithTolerance(responses.get(0).get(BASAL_CALORIES_TOTAL), 802000);
        assertEnergyWithTolerance(responses.get(1).get(BASAL_CALORIES_TOTAL), 1018000);
        assertEnergyWithTolerance(responses.get(2).get(BASAL_CALORIES_TOTAL), 1234000);
    }

    @Test
    public void testAggregation_BasalCaloriesBurntTotal_profile_group() throws Exception {
        Instant now = Instant.now();
        insertRecords(
                List.of(
                        getBaseHeightRecord(now, 1.8),
                        getBaseWeightRecord(now, 50000),
                        getBaseHeightRecord(now.minus(1, DAYS), 1.7),
                        getBaseWeightRecord(now.minus(1, DAYS), 40000),
                        getBaseHeightRecord(now.minus(2, DAYS), 1.6),
                        getBaseWeightRecord(now.minus(2, DAYS), 30000),
                        getBaseHeightRecord(now.minus(3, DAYS), 1.5),
                        getBaseWeightRecord(now.minus(3, DAYS), 20000)));

        List<AggregateRecordsGroupedByDurationResponse<Energy>> responses =
                getAggregateResponseGroupByDuration(
                        new AggregateRecordsRequest.Builder<Energy>(
                                        new TimeInstantRangeFilter.Builder()
                                                .setStartTime(now.minus(3, DAYS))
                                                .setEndTime(now)
                                                .build())
                                .addAggregationType(BASAL_CALORIES_TOTAL)
                                .build(),
                        Duration.ofDays(1));

        assertThat(responses).hasSize(3);
        assertEnergyWithTolerance(responses.get(0).get(BASAL_CALORIES_TOTAL), 909500);
        assertEnergyWithTolerance(responses.get(1).get(BASAL_CALORIES_TOTAL), 1072000);
        assertEnergyWithTolerance(responses.get(2).get(BASAL_CALORIES_TOTAL), 1234500);

        AggregateRecordsResponse<Energy> response =
                getAggregateResponse(
                        new AggregateRecordsRequest.Builder<Energy>(
                                        new TimeInstantRangeFilter.Builder()
                                                .setStartTime(now.minus(3, DAYS))
                                                .setEndTime(now)
                                                .build())
                                .addAggregationType(BASAL_CALORIES_TOTAL)
                                .build());

        assertEnergyWithTolerance(response.get(BASAL_CALORIES_TOTAL), 3216000);
    }

    @Test
    public void testAggregation_basalCaloriesBurntTotal_onlyWeightBeforeInterval_usesProfile()
            throws Exception {
        Instant now = Instant.now();
        insertRecords(List.of(getBaseWeightRecord(now.minus(10, DAYS), /* weight= */ 40000)));

        AggregateRecordsResponse<Energy> response =
                getAggregateResponse(
                        new AggregateRecordsRequest.Builder<Energy>(
                                        new TimeInstantRangeFilter.Builder()
                                                .setStartTime(now.minus(2, DAYS))
                                                .setEndTime(now)
                                                .build())
                                .addAggregationType(BASAL_CALORIES_TOTAL)
                                .build());

        assertEnergyWithTolerance(response.get(BASAL_CALORIES_TOTAL), 2469000);
    }

    @Test
    public void testAggregation_basalCaloriesBurntTotal_HeightAndWeightBeforeInterval_usesProfile()
            throws Exception {
        Instant now = Instant.now();
        insertRecords(
                List.of(
                        getBaseWeightRecord(now.minus(10, DAYS), /* weight= */ 40000),
                        getBaseHeightRecord(now.minus(9, DAYS), /* height= */ 2.0)));

        AggregateRecordsResponse<Energy> response =
                getAggregateResponse(
                        new AggregateRecordsRequest.Builder<Energy>(
                                        new TimeInstantRangeFilter.Builder()
                                                .setStartTime(now.minus(2, DAYS))
                                                .setEndTime(now)
                                                .build())
                                .addAggregationType(BASAL_CALORIES_TOTAL)
                                .build());

        assertEnergyWithTolerance(response.get(BASAL_CALORIES_TOTAL), 2844000);
    }

    @Test
    public void
            testAggregation_basalCaloriesBurntTotal_HeightWeightBeforeAndAfterInterval_usesProfile()
                    throws Exception {
        Instant now = Instant.now();
        insertRecords(
                List.of(
                        getBaseWeightRecord(now.minus(10, DAYS), /* weight= */ 40000),
                        getBaseHeightRecord(now.minus(9, DAYS), /* height= */ 1.8),
                        getBaseWeightRecord(now.minus(1, DAYS), /* weight= */ 60000),
                        getBaseHeightRecord(now.minus(1, DAYS), /* height= */ 1.9),
                        getBaseHeightRecord(now, /* height= */ 2.0),
                        getBaseWeightRecord(now, /* weight= */ 70000),
                        getBaseHeightRecord(now, /* height= */ 2.0)));

        AggregateRecordsResponse<Energy> response =
                getAggregateResponse(
                        new AggregateRecordsRequest.Builder<Energy>(
                                        new TimeInstantRangeFilter.Builder()
                                                .setStartTime(now.minus(3, DAYS))
                                                .setEndTime(now.minus(1, DAYS))
                                                .build())
                                .addAggregationType(BASAL_CALORIES_TOTAL)
                                .build());

        assertEnergyWithTolerance(response.get(BASAL_CALORIES_TOTAL), 2594000);
    }

    @Test
    public void testAggregation_basalCaloriesBurntTotal_onlyWeightDuringInterval_usesProfile()
            throws Exception {
        Instant now = Instant.now();
        insertRecords(List.of(getBaseWeightRecord(now.minus(1, DAYS), /* weight= */ 40000)));

        AggregateRecordsResponse<Energy> response =
                getAggregateResponse(
                        new AggregateRecordsRequest.Builder<Energy>(
                                        new TimeInstantRangeFilter.Builder()
                                                .setStartTime(now.minus(2, DAYS))
                                                .setEndTime(now)
                                                .build())
                                .addAggregationType(BASAL_CALORIES_TOTAL)
                                .build());

        assertEnergyWithTolerance(response.get(BASAL_CALORIES_TOTAL), 2799000);
    }

    @Test
    public void testAggregation_basalCaloriesBurntTotal_onlyWeightAfterInterval_usesDefaultProfile()
            throws Exception {
        Instant now = Instant.now();
        insertRecords(List.of(getBaseWeightRecord(now, /* weight= */ 40000)));

        AggregateRecordsResponse<Energy> response =
                getAggregateResponse(
                        new AggregateRecordsRequest.Builder<Energy>(
                                        new TimeInstantRangeFilter.Builder()
                                                .setStartTime(now.minus(2, DAYS))
                                                .setEndTime(now.minus(1, DAYS))
                                                .build())
                                .addAggregationType(BASAL_CALORIES_TOTAL)
                                .build());

        assertEnergyWithTolerance(response.get(BASAL_CALORIES_TOTAL), 1564500);
    }

    @Test
    public void testAggregation_basalCaloriesBurntTotal_onlyHeightBeforeInterval_usesProfile()
            throws Exception {
        Instant now = Instant.now();
        insertRecords(List.of(getBaseHeightRecord(now.minus(10, DAYS), /* height= */ 2.0)));

        AggregateRecordsResponse<Energy> response =
                getAggregateResponse(
                        new AggregateRecordsRequest.Builder<Energy>(
                                        new TimeInstantRangeFilter.Builder()
                                                .setStartTime(now.minus(2, DAYS))
                                                .setEndTime(now)
                                                .build())
                                .addAggregationType(BASAL_CALORIES_TOTAL)
                                .build());

        assertEnergyWithTolerance(response.get(BASAL_CALORIES_TOTAL), 3504000);
    }

    @Test
    public void testAggregation_basalCaloriesBurntTotal_onlyHeightDuringInterval_usesProfile()
            throws Exception {
        Instant now = Instant.now();
        insertRecords(List.of(getBaseHeightRecord(now.minus(1, DAYS), /* height= */ 2.0)));

        AggregateRecordsResponse<Energy> response =
                getAggregateResponse(
                        new AggregateRecordsRequest.Builder<Energy>(
                                        new TimeInstantRangeFilter.Builder()
                                                .setStartTime(now.minus(2, DAYS))
                                                .setEndTime(now)
                                                .build())
                                .addAggregationType(BASAL_CALORIES_TOTAL)
                                .build());

        assertEnergyWithTolerance(response.get(BASAL_CALORIES_TOTAL), 3316500);
    }

    @Test
    public void testAggregation_basalCaloriesBurntTotal_onlyHeightAfterInterval_usesDefaultProfile()
            throws Exception {
        Instant now = Instant.now();
        insertRecords(List.of(getBaseHeightRecord(now, /* height= */ 2.0)));

        AggregateRecordsResponse<Energy> response =
                getAggregateResponse(
                        new AggregateRecordsRequest.Builder<Energy>(
                                        new TimeInstantRangeFilter.Builder()
                                                .setStartTime(now.minus(2, DAYS))
                                                .setEndTime(now.minus(1, DAYS))
                                                .build())
                                .addAggregationType(BASAL_CALORIES_TOTAL)
                                .build());

        assertEnergyWithTolerance(response.get(BASAL_CALORIES_TOTAL), 1564500);
    }

    @Test
    public void testAggregation_BasalCaloriesBurntTotal_groupByDuration_profileDerived()
            throws Exception {
        Instant now = Instant.now();
        insertRecords(
                List.of(
                        getBaseHeightRecord(now, 1.8),
                        getBaseWeightRecord(now, 50000),
                        getBaseHeightRecord(now.minus(1, DAYS), 1.7),
                        getBaseWeightRecord(now.minus(1, DAYS), 40000),
                        getBaseHeightRecord(now.minus(2, DAYS), 1.6),
                        getBaseWeightRecord(now.minus(2, DAYS), 30000),
                        getBaseHeightRecord(now.minus(3, DAYS), 1.5),
                        getBaseWeightRecord(now.minus(3, DAYS), 20000)));

        List<AggregateRecordsGroupedByDurationResponse<Energy>> responses =
                getAggregateResponseGroupByDuration(
                        new AggregateRecordsRequest.Builder<Energy>(
                                        new TimeInstantRangeFilter.Builder()
                                                .setStartTime(now.minus(3, DAYS))
                                                .setEndTime(now)
                                                .build())
                                .addAggregationType(BASAL_CALORIES_TOTAL)
                                .build(),
                        Duration.ofDays(1));

        assertThat(responses).hasSize(3);
        assertEnergyWithTolerance(responses.get(0).get(BASAL_CALORIES_TOTAL), 909500);
        assertEnergyWithTolerance(responses.get(1).get(BASAL_CALORIES_TOTAL), 1072000);
        assertEnergyWithTolerance(responses.get(2).get(BASAL_CALORIES_TOTAL), 1234500);
    }

    @Test
    public void testAggregation_BasalCaloriesBurntTotal() throws Exception {
        insertRecords(
                List.of(
                        getBasalMetabolicRateRecord(30.0, Instant.now().minus(3, DAYS)),
                        getBasalMetabolicRateRecord(75.0, Instant.now().minus(2, DAYS))));

        AggregateRecordsResponse<Energy> oldResponse =
                getAggregateResponse(
                        new AggregateRecordsRequest.Builder<Energy>(
                                        new TimeInstantRangeFilter.Builder()
                                                .setStartTime(Instant.now().minus(10, DAYS))
                                                .setEndTime(Instant.now().minus(1, DAYS))
                                                .build())
                                .addAggregationType(BASAL_CALORIES_TOTAL)
                                .build());

        insertRecords(List.of(getBasalMetabolicRateRecord(46, Instant.now().minus(1, DAYS))));

        AggregateRecordsResponse<Energy> newResponse =
                getAggregateResponse(
                        new AggregateRecordsRequest.Builder<Energy>(
                                        new TimeInstantRangeFilter.Builder()
                                                .setStartTime(Instant.now().minus(10, DAYS))
                                                .setEndTime(Instant.now())
                                                .build())
                                .addAggregationType(BASAL_CALORIES_TOTAL)
                                .build());
        assertThat(newResponse.get(BASAL_CALORIES_TOTAL)).isNotNull();
        Energy newEnergy = newResponse.get(BASAL_CALORIES_TOTAL);
        Energy oldEnergy = oldResponse.get(BASAL_CALORIES_TOTAL);
        assertThat(oldEnergy.getInCalories() / 1000).isWithin(1).of(13118);
        assertThat(newEnergy.getInCalories() / 1000).isGreaterThan(13118);
        assertThat((double) Math.round(newEnergy.getInCalories() - oldEnergy.getInCalories()))
                .isWithin(1)
                .of(949440);
        Set<DataOrigin> newDataOrigin = newResponse.getDataOrigins(BASAL_CALORIES_TOTAL);
        for (DataOrigin itr : newDataOrigin) {
            assertThat(itr.getPackageName()).isEqualTo("android.healthconnect.cts");
        }
        Set<DataOrigin> oldDataOrigin = oldResponse.getDataOrigins(BASAL_CALORIES_TOTAL);
        for (DataOrigin itr : oldDataOrigin) {
            assertThat(itr.getPackageName()).isEqualTo("android.healthconnect.cts");
        }
    }

    @Test
    public void testAggregation_BasalCaloriesBurntTotal_groupDuration() throws Exception {
        Instant now = Instant.now();
        insertRecords(
                List.of(
                        getBasalMetabolicRateRecord(50.0, now),
                        getBasalMetabolicRateRecord(40.0, now.minus(1, DAYS)),
                        getBasalMetabolicRateRecord(30.0, now.minus(2, DAYS)),
                        getBasalMetabolicRateRecord(20.0, now.minus(3, DAYS))));

        List<AggregateRecordsGroupedByDurationResponse<Energy>> responses =
                getAggregateResponseGroupByDuration(
                        new AggregateRecordsRequest.Builder<Energy>(
                                        new TimeInstantRangeFilter.Builder()
                                                .setStartTime(now.minus(3, DAYS))
                                                .setEndTime(now)
                                                .build())
                                .addAggregationType(BASAL_CALORIES_TOTAL)
                                .build(),
                        Duration.ofDays(1));

        assertThat(responses).hasSize(3);
        assertEnergyWithTolerance(responses.get(0).get(BASAL_CALORIES_TOTAL), 412800);
        assertEnergyWithTolerance(responses.get(1).get(BASAL_CALORIES_TOTAL), 619200);
        assertEnergyWithTolerance(responses.get(2).get(BASAL_CALORIES_TOTAL), 825600);

        AggregateRecordsResponse<Energy> response =
                getAggregateResponse(
                        new AggregateRecordsRequest.Builder<Energy>(
                                        new TimeInstantRangeFilter.Builder()
                                                .setStartTime(now.minus(3, DAYS))
                                                .setEndTime(now)
                                                .build())
                                .addAggregationType(BASAL_CALORIES_TOTAL)
                                .build());

        assertEnergyWithTolerance(response.get(BASAL_CALORIES_TOTAL), 1857600);
    }

    @Test
    public void testAggregation_BasalCaloriesBurntTotal_groupDurationLocalFilter()
            throws Exception {
        Instant now = Instant.now();
        ZoneOffset offset = ZoneOffset.MIN;
        LocalDateTime nowLocal = LocalDateTime.ofInstant(now, offset);

        insertRecords(
                List.of(
                        getBasalMetabolicRateRecord(50.0, now, offset),
                        getBasalMetabolicRateRecord(40.0, now.minus(1, DAYS), offset),
                        getBasalMetabolicRateRecord(30.0, now.minus(2, DAYS), offset),
                        getBasalMetabolicRateRecord(20.0, now.minus(3, DAYS), offset)));
        var request =
                new AggregateRecordsRequest.Builder<Energy>(
                                new LocalTimeRangeFilter.Builder()
                                        .setStartTime(nowLocal.minusDays(3))
                                        .setEndTime(nowLocal)
                                        .build())
                        .addAggregationType(BASAL_CALORIES_TOTAL)
                        .build();
        List<AggregateRecordsGroupedByDurationResponse<Energy>> responses =
                getAggregateResponseGroupByDuration(request, Duration.ofDays(1));

        assertThat(responses).hasSize(3);
        assertEnergyWithTolerance(responses.get(0).get(BASAL_CALORIES_TOTAL), 412800);
        assertEnergyWithTolerance(responses.get(1).get(BASAL_CALORIES_TOTAL), 619200);
        assertEnergyWithTolerance(responses.get(2).get(BASAL_CALORIES_TOTAL), 825600);

        AggregateRecordsResponse<Energy> response = getAggregateResponse(request);
        assertEnergyWithTolerance(response.get(BASAL_CALORIES_TOTAL), 1857600);
    }

    @Test
    public void testAggregation_BasalCaloriesBurntTotal_group() throws Exception {
        Instant now = Instant.now();
        insertRecords(
                List.of(
                        getBasalMetabolicRateRecord(50.0, now),
                        getBasalMetabolicRateRecord(40.0, now.minus(1, DAYS)),
                        getBasalMetabolicRateRecord(30.0, now.minus(2, DAYS)),
                        getBasalMetabolicRateRecord(20.0, now.minus(3, DAYS))));

        List<AggregateRecordsGroupedByDurationResponse<Energy>> responses =
                getAggregateResponseGroupByDuration(
                        new AggregateRecordsRequest.Builder<Energy>(
                                        new TimeInstantRangeFilter.Builder()
                                                .setStartTime(now.minus(3, DAYS))
                                                .setEndTime(now)
                                                .build())
                                .addAggregationType(BASAL_CALORIES_TOTAL)
                                .build(),
                        Duration.ofDays(1));

        assertThat(responses).hasSize(3);
        assertEnergyWithTolerance(responses.get(0).get(BASAL_CALORIES_TOTAL), 412800);
        assertEnergyWithTolerance(responses.get(1).get(BASAL_CALORIES_TOTAL), 619200);
        assertEnergyWithTolerance(responses.get(2).get(BASAL_CALORIES_TOTAL), 825600);
    }

    @Test
    public void testAggregation_BasalCaloriesBurntTotal_profile() throws Exception {
        insertRecords(
                List.of(
                        getBaseHeightRecord(Instant.now().minus(2, DAYS), 1.8),
                        getBaseWeightRecord(Instant.now().minus(2, DAYS), 50000)));

        AggregateRecordsResponse<Energy> response =
                getAggregateResponse(
                        new AggregateRecordsRequest.Builder<Energy>(
                                        new TimeInstantRangeFilter.Builder()
                                                .setStartTime(Instant.now().minus(2, DAYS))
                                                .setEndTime(Instant.now().minus(1, DAYS))
                                                .build())
                                .addAggregationType(BASAL_CALORIES_TOTAL)
                                .build());

        assertEnergyWithTolerance(response.get(BASAL_CALORIES_TOTAL), 1397000);
    }

    @Test
    public void testAggregate_withDifferentTimeZone() throws Exception {
        Instant instant = Instant.now().minus(1, DAYS);
        insertRecords(
                List.of(
                        getBasalMetabolicRateRecord(
                                20.0, instant.plus(20, MINUTES), ZoneOffset.ofHours(2)),
                        getBasalMetabolicRateRecord(
                                30.0, instant.plus(10, MINUTES), ZoneOffset.ofHours(3)),
                        getBasalMetabolicRateRecord(
                                40.0, instant.plus(30, MINUTES), ZoneOffset.ofHours(1))));

        AggregateRecordsResponse<Energy> oldResponse =
                getAggregateResponse(
                        new AggregateRecordsRequest.Builder<Energy>(
                                        new TimeInstantRangeFilter.Builder()
                                                .setStartTime(Instant.ofEpochMilli(0))
                                                .setEndTime(Instant.now().plus(1, DAYS))
                                                .build())
                                .addAggregationType(BASAL_CALORIES_TOTAL)
                                .build());
        assertThat(oldResponse.getZoneOffset(BASAL_CALORIES_TOTAL))
                .isEqualTo(ZoneOffset.ofHours(3));

        insertRecords(List.of(getBasalMetabolicRateRecord(50.0, instant, ZoneOffset.ofHours(5))));
        AggregateRecordsResponse<Energy> newResponse =
                getAggregateResponse(
                        new AggregateRecordsRequest.Builder<Energy>(
                                        new TimeInstantRangeFilter.Builder()
                                                .setStartTime(Instant.ofEpochMilli(0))
                                                .setEndTime(Instant.now().plus(1, DAYS))
                                                .build())
                                .addAggregationType(BASAL_CALORIES_TOTAL)
                                .build());

        assertThat(newResponse.get(BASAL_CALORIES_TOTAL)).isNotNull();
        assertThat(newResponse.getZoneOffset(BASAL_CALORIES_TOTAL))
                .isEqualTo(ZoneOffset.ofHours(5));
    }

    private static LeanBodyMassRecord getBaseLeanBodyMassRecord(Instant time, double value) {
        return new LeanBodyMassRecord.Builder(getEmptyMetadata(), time, Mass.fromGrams(value))
                .build();
    }

    private static HeightRecord getBaseHeightRecord(Instant time, double height) {
        return new HeightRecord.Builder(getEmptyMetadata(), time, Length.fromMeters(height))
                .build();
    }

    private static WeightRecord getBaseWeightRecord(Instant time, double weight) {
        return new WeightRecord.Builder(getEmptyMetadata(), time, Mass.fromGrams(weight)).build();
    }
}
