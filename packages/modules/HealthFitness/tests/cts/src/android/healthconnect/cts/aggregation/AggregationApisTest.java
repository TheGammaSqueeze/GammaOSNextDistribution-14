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

import static android.health.connect.datatypes.ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL;
import static android.health.connect.datatypes.DistanceRecord.DISTANCE_TOTAL;
import static android.health.connect.datatypes.StepsCadenceRecord.STEPS_CADENCE_RATE_MAX;
import static android.health.connect.datatypes.StepsRecord.STEPS_COUNT_TOTAL;
import static android.health.connect.datatypes.WeightRecord.WEIGHT_AVG;
import static android.health.connect.datatypes.WeightRecord.WEIGHT_MAX;
import static android.health.connect.datatypes.WeightRecord.WEIGHT_MIN;
import static android.healthconnect.cts.aggregation.DataFactory.getActiveCaloriesBurnedRecord;
import static android.healthconnect.cts.aggregation.DataFactory.getTimeFilter;
import static android.healthconnect.cts.aggregation.Utils.assertDoubleWithTolerance;
import static android.healthconnect.cts.aggregation.Utils.assertEnergyWithTolerance;
import static android.healthconnect.cts.aggregation.Utils.assertMassWithTolerance;
import static android.healthconnect.cts.utils.DataFactory.getEmptyMetadata;
import static android.healthconnect.cts.utils.DataFactory.getStepsRecord;
import static android.healthconnect.cts.utils.TestUtils.deleteAllStagedRemoteData;
import static android.healthconnect.cts.utils.TestUtils.getAggregateResponse;
import static android.healthconnect.cts.utils.TestUtils.getAggregateResponseGroupByDuration;
import static android.healthconnect.cts.utils.TestUtils.getAggregateResponseGroupByPeriod;
import static android.healthconnect.cts.utils.TestUtils.insertRecords;
import static android.healthconnect.cts.utils.TestUtils.setupAggregation;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import static java.time.Instant.EPOCH;
import static java.time.ZoneOffset.UTC;
import static java.time.temporal.ChronoUnit.DAYS;
import static java.time.temporal.ChronoUnit.HOURS;
import static java.time.temporal.ChronoUnit.MILLIS;

import android.content.Context;
import android.health.connect.AggregateRecordsGroupedByDurationResponse;
import android.health.connect.AggregateRecordsGroupedByPeriodResponse;
import android.health.connect.AggregateRecordsRequest;
import android.health.connect.AggregateRecordsResponse;
import android.health.connect.HealthConnectException;
import android.health.connect.HealthDataCategory;
import android.health.connect.LocalTimeRangeFilter;
import android.health.connect.TimeInstantRangeFilter;
import android.health.connect.datatypes.StepsCadenceRecord;
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
import java.time.Period;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;

public class AggregationApisTest {
    private static final int MAXIMUM_GROUP_SIZE = 5000;
    private final Context mContext = ApplicationProvider.getApplicationContext();
    private final String mPackageName = mContext.getPackageName();

    @Rule
    public AssumptionCheckerRule mSupportedHardwareRule =
            new AssumptionCheckerRule(
                    TestUtils::isHardwareSupported, "Tests should run on supported hardware only.");

    @Before
    public void setUp() throws InterruptedException {
        deleteAllStagedRemoteData();
        setupAggregation(mPackageName, HealthDataCategory.ACTIVITY);
        setupAggregation(mPackageName, HealthDataCategory.BODY_MEASUREMENTS);
    }

    @After
    public void tearDown() {
        deleteAllStagedRemoteData();
    }

    @Test
    public void aggregate_noData_nullResponse() throws Exception {
        AggregateRecordsRequest<Long> request =
                new AggregateRecordsRequest.Builder<Long>(
                                new TimeInstantRangeFilter.Builder()
                                        .setEndTime(Instant.now())
                                        .build())
                        .addAggregationType(STEPS_COUNT_TOTAL)
                        .build();

        AggregateRecordsResponse<Long> response = getAggregateResponse(request);
        assertThat(response.get(STEPS_COUNT_TOTAL)).isNull();
    }

    @Test
    public void groupByDuration_noData_nullResponses() throws Exception {
        Instant time = Instant.now().minus(2, DAYS);
        AggregateRecordsRequest<Length> request =
                new AggregateRecordsRequest.Builder<Length>(
                                getTimeFilter(time, time.plus(2, HOURS)))
                        .addAggregationType(DISTANCE_TOTAL)
                        .build();

        List<AggregateRecordsGroupedByDurationResponse<Length>> responses =
                getAggregateResponseGroupByDuration(request, Duration.ofHours(1));
        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).get(DISTANCE_TOTAL)).isNull();
        assertThat(responses.get(1).get(DISTANCE_TOTAL)).isNull();
    }

    @Test
    public void groupByPeriod_noData_nullResponses() throws Exception {
        LocalDateTime time = LocalDateTime.now(ZoneOffset.UTC).minusDays(2);
        AggregateRecordsRequest<Energy> request =
                new AggregateRecordsRequest.Builder<Energy>(getTimeFilter(time, time.plusDays(2)))
                        .addAggregationType(ACTIVE_CALORIES_TOTAL)
                        .build();

        List<AggregateRecordsGroupedByPeriodResponse<Energy>> responses =
                getAggregateResponseGroupByPeriod(request, Period.ofDays(1));
        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).get(ACTIVE_CALORIES_TOTAL)).isNull();
        assertThat(responses.get(1).get(ACTIVE_CALORIES_TOTAL)).isNull();
    }

    @Test
    public void aggregateWithInstantFilter_stepsCountTotal() throws Exception {
        Instant time = Instant.now().minus(2, DAYS);
        insertRecords(
                List.of(
                        getStepsRecord(1234, time.plus(1, DAYS), time.plus(25, HOURS)),
                        getStepsRecord(4321, time, time.plus(1, HOURS))));

        TimeInstantRangeFilter timeFilter = getTimeFilter(time.minus(1, DAYS), time.plus(2, DAYS));
        AggregateRecordsRequest<Long> aggregateRecordsRequest =
                new AggregateRecordsRequest.Builder<Long>(timeFilter)
                        .addAggregationType(STEPS_COUNT_TOTAL)
                        .build();

        AggregateRecordsResponse<Long> response = getAggregateResponse(aggregateRecordsRequest);
        assertThat(response.get(STEPS_COUNT_TOTAL)).isEqualTo(5555);
    }

    @Test
    public void groupByDurationWithInstantFilter_activeCaloriesBurnedTotal() throws Exception {
        Instant time = Instant.now().minus(1, DAYS).truncatedTo(MILLIS);
        ZoneOffset defaultOffset = ZoneOffset.systemDefault().getRules().getOffset(time);
        insertRecords(
                List.of(
                        getActiveCaloriesBurnedRecord(210.0, time, time.plus(3, HOURS)),
                        getActiveCaloriesBurnedRecord(
                                15.0, time.plus(3, HOURS), time.plus(4, HOURS))));
        TimeInstantRangeFilter timeFilter =
                getTimeFilter(time.minus(2, HOURS), time.plus(4, HOURS));

        List<AggregateRecordsGroupedByDurationResponse<Energy>> responses =
                getAggregateResponseGroupByDuration(
                        new AggregateRecordsRequest.Builder<Energy>(timeFilter)
                                .addAggregationType(ACTIVE_CALORIES_TOTAL)
                                .build(),
                        Duration.ofHours(2));

        assertThat(responses).hasSize(3);
        // 2 hours before time, no active energy
        assertThat(responses.get(0).get(ACTIVE_CALORIES_TOTAL)).isNull();
        assertThat(responses.get(0).getStartTime()).isEqualTo(time.minus(2, HOURS));
        assertThat(responses.get(0).getEndTime()).isEqualTo(time);
        assertThat(responses.get(0).getZoneOffset(ACTIVE_CALORIES_TOTAL)).isNull();
        // hour 0-2, active energy = 210 / 3 * 2
        assertEnergyWithTolerance(responses.get(1).get(ACTIVE_CALORIES_TOTAL), 140.0);
        assertThat(responses.get(1).getStartTime()).isEqualTo(time);
        assertThat(responses.get(1).getEndTime()).isEqualTo(time.plus(2, HOURS));
        assertThat(responses.get(1).getZoneOffset(ACTIVE_CALORIES_TOTAL)).isEqualTo(defaultOffset);
        // hour 2-4, active energy = 210 / 3 + 15
        assertEnergyWithTolerance(responses.get(2).get(ACTIVE_CALORIES_TOTAL), 85.0);
        assertThat(responses.get(2).getStartTime()).isEqualTo(time.plus(2, HOURS));
        assertThat(responses.get(2).getEndTime()).isEqualTo(time.plus(4, HOURS));
        assertThat(responses.get(2).getZoneOffset(ACTIVE_CALORIES_TOTAL)).isEqualTo(defaultOffset);
    }

    @Test
    public void aggregateWithInstantFilter_weightMax() throws Exception {
        Instant time = Instant.now().minus(1, DAYS).truncatedTo(DAYS);
        insertRecords(
                List.of(
                        getWeightRecord(50.0, time.minus(30, DAYS)),
                        getWeightRecord(60.0, time.minus(15, DAYS)),
                        getWeightRecord(70.0, time.minus(60, DAYS))));

        TimeInstantRangeFilter timeFilter = getTimeFilter(time.minus(90, DAYS), time);
        AggregateRecordsRequest<Mass> aggregateRecordsRequest =
                new AggregateRecordsRequest.Builder<Mass>(timeFilter)
                        .addAggregationType(WEIGHT_MAX)
                        .build();

        AggregateRecordsResponse<Mass> response = getAggregateResponse(aggregateRecordsRequest);
        assertMassWithTolerance(response.get(WEIGHT_MAX), 70.0);
    }

    @Test
    public void groupByDurationWithInstantFilter_weightMin() throws Exception {
        Instant time = Instant.now().minus(1, DAYS).truncatedTo(DAYS);
        ZoneOffset defaultOffset = ZoneOffset.systemDefault().getRules().getOffset(time);
        insertRecords(
                List.of(
                        getWeightRecord(50.0, time.minus(24, DAYS)),
                        getWeightRecord(60.0, time.minus(11, DAYS)),
                        getWeightRecord(70.0, time.minus(4, DAYS).minusMillis(1))));

        TimeInstantRangeFilter timeFilter = getTimeFilter(time.minus(25, DAYS), time.plus(1, DAYS));
        List<AggregateRecordsGroupedByDurationResponse<Mass>> responses =
                getAggregateResponseGroupByDuration(
                        new AggregateRecordsRequest.Builder<Mass>(timeFilter)
                                .addAggregationType(WEIGHT_MIN)
                                .build(),
                        Duration.ofDays(7));

        assertThat(responses).hasSize(4);
        // (day -25) - (day -18), min weight = 50
        assertMassWithTolerance(responses.get(0).get(WEIGHT_MIN), 50.0);
        assertThat(responses.get(0).getStartTime()).isEqualTo(time.minus(25, DAYS));
        assertThat(responses.get(0).getEndTime()).isEqualTo(time.minus(18, DAYS));
        assertThat(responses.get(0).getZoneOffset(WEIGHT_MIN)).isEqualTo(defaultOffset);
        // (day -18) - (day -11), no weight
        assertThat(responses.get(1).get(WEIGHT_MIN)).isNull();
        assertThat(responses.get(1).getStartTime()).isEqualTo(time.minus(18, DAYS));
        assertThat(responses.get(1).getEndTime()).isEqualTo(time.minus(11, DAYS));
        assertThat(responses.get(1).getZoneOffset(WEIGHT_MIN)).isNull();
        // (day -11) - (day -4), min weight = min(60, 70)
        assertMassWithTolerance(responses.get(2).get(WEIGHT_MIN), 60.0);
        assertThat(responses.get(2).getStartTime()).isEqualTo(time.minus(11, DAYS));
        assertThat(responses.get(2).getEndTime()).isEqualTo(time.minus(4, DAYS));
        assertThat(responses.get(2).getZoneOffset(WEIGHT_MIN)).isEqualTo(defaultOffset);
        // (day -4) - (day 1), no weight
        assertThat(responses.get(3).get(WEIGHT_MIN)).isNull();
        assertThat(responses.get(3).getStartTime()).isEqualTo(time.minus(4, DAYS));
        assertThat(responses.get(3).getEndTime()).isEqualTo(time.plus(1, DAYS));
        assertThat(responses.get(3).getZoneOffset(WEIGHT_MIN)).isNull();
    }

    // TODO(b/326058390) add tests back after bug fix

    @Test
    public void groupByPeriod_stepsCadenceMax() throws Exception {
        Instant time = Instant.now().minus(1, DAYS).truncatedTo(DAYS);
        LocalDateTime localTime = time.atOffset(UTC).toLocalDateTime();
        insertRecords(
                List.of(
                        getStepsCadenceRecord(
                                time.minus(1, HOURS),
                                time.plus(6, HOURS),
                                UTC,
                                getStepsCadenceRecordSample(1234.5, time.minusSeconds(1))),
                        getStepsCadenceRecord(
                                time.plus(6, HOURS),
                                time.plus(8, HOURS),
                                UTC,
                                getStepsCadenceRecordSample(369.2, time.plus(7, HOURS)))));

        LocalTimeRangeFilter timeFilter = getTimeFilter(localTime, localTime.plusDays(1));
        List<AggregateRecordsGroupedByPeriodResponse<Double>> responses =
                getAggregateResponseGroupByPeriod(
                        new AggregateRecordsRequest.Builder<Double>(timeFilter)
                                .addAggregationType(STEPS_CADENCE_RATE_MAX)
                                .build(),
                        Period.ofDays(1));

        assertThat(responses).hasSize(1);
        assertDoubleWithTolerance(responses.get(0).get(STEPS_CADENCE_RATE_MAX), 369.2);
        assertThat(responses.get(0).getStartTime()).isEqualTo(localTime);
        assertThat(responses.get(0).getEndTime()).isEqualTo(localTime.plusDays(1));
        assertThat(responses.get(0).getZoneOffset(STEPS_CADENCE_RATE_MAX)).isEqualTo(UTC);
    }

    @Test
    public void aggregationRequest_emptyAggregationType_throws() {
        Instant time = Instant.now().minus(1, DAYS);
        AggregateRecordsRequest.Builder<?> requestBuilder =
                new AggregateRecordsRequest.Builder<>(getTimeFilter(time, time.plusMillis(1)));

        Throwable thrown = assertThrows(IllegalArgumentException.class, requestBuilder::build);
        assertThat(thrown).hasMessageThat().contains("At least one of the aggregation types");
    }

    @Test
    public void groupByPeriod_withInstantTimeFilter_throws() {
        Instant time = Instant.now().minus(1, DAYS);

        AggregateRecordsRequest<Long> request =
                new AggregateRecordsRequest.Builder<Long>(getTimeFilter(time, time.plusMillis(1)))
                        .addAggregationType(STEPS_COUNT_TOTAL)
                        .build();
        Throwable thrown =
                assertThrows(
                        UnsupportedOperationException.class,
                        () -> getAggregateResponseGroupByPeriod(request, Period.ofDays(1)));
        assertThat(thrown).hasMessageThat().contains("should use LocalTimeRangeFilter");
    }

    @Test
    public void groupByPeriod_zeroPeriod_throws() {
        AggregateRecordsRequest<Mass> request =
                new AggregateRecordsRequest.Builder<Mass>(
                                getTimeFilter(
                                        LocalDateTime.now(ZoneOffset.UTC).minusDays(1),
                                        LocalDateTime.now(ZoneOffset.UTC)))
                        .addAggregationType(WEIGHT_AVG)
                        .build();

        Throwable thrown =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> getAggregateResponseGroupByPeriod(request, Period.ZERO));
        assertThat(thrown).hasMessageThat().isEqualTo("Period duration should be at least a day");
    }

    @Test
    public void groupByPeriod_hugeNumberOfGroups_throws() {
        LocalDateTime localTime = LocalDateTime.now(ZoneOffset.UTC);
        LocalTimeRangeFilter timeFilter =
                getTimeFilter(localTime.minusDays(MAXIMUM_GROUP_SIZE + 1), localTime);

        Throwable thrown =
                assertThrows(
                        HealthConnectException.class,
                        () ->
                                getAggregateResponseGroupByPeriod(
                                        new AggregateRecordsRequest.Builder<Mass>(timeFilter)
                                                .addAggregationType(WEIGHT_AVG)
                                                .build(),
                                        Period.ofDays(1)));
        assertThat(thrown).hasMessageThat().contains("Number of groups");
    }

    @Test
    public void groupByDuration_zeroDuration_throws() {
        AggregateRecordsRequest<Mass> request =
                new AggregateRecordsRequest.Builder<Mass>(getTimeFilter(EPOCH, Instant.now()))
                        .addAggregationType(WEIGHT_AVG)
                        .build();
        Throwable thrown =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> getAggregateResponseGroupByDuration(request, Duration.ZERO));
        assertThat(thrown).hasMessageThat().isEqualTo("Duration should be at least 1 millisecond");
    }

    @Test
    public void groupByDuration_hugeNumberOfGroups_throws() {
        Instant time = Instant.now();
        TimeInstantRangeFilter timeFilter =
                getTimeFilter(time.minusSeconds(MAXIMUM_GROUP_SIZE + 1), time);
        Throwable thrown =
                assertThrows(
                        HealthConnectException.class,
                        () ->
                                getAggregateResponseGroupByDuration(
                                        new AggregateRecordsRequest.Builder<Mass>(timeFilter)
                                                .addAggregationType(WEIGHT_AVG)
                                                .build(),
                                        Duration.ofSeconds(1)));
        assertThat(thrown).hasMessageThat().contains("Number of buckets");
    }

    private static WeightRecord getWeightRecord(double weight, Instant time) {
        return new WeightRecord.Builder(getEmptyMetadata(), time, Mass.fromGrams(weight)).build();
    }

    private static StepsCadenceRecord getStepsCadenceRecord(
            Instant start,
            Instant end,
            ZoneOffset offset,
            StepsCadenceRecord.StepsCadenceRecordSample... samples) {
        return new StepsCadenceRecord.Builder(
                        getEmptyMetadata(), start, end, Arrays.asList(samples))
                .setStartZoneOffset(offset)
                .setEndZoneOffset(offset)
                .build();
    }

    private static StepsCadenceRecord.StepsCadenceRecordSample getStepsCadenceRecordSample(
            double rate, Instant time) {
        return new StepsCadenceRecord.StepsCadenceRecordSample(rate, time);
    }
}
