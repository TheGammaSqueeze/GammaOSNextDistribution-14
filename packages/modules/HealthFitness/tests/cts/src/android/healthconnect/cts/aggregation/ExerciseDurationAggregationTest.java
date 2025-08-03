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

package android.healthconnect.cts.aggregation;

import static android.health.connect.datatypes.ExerciseSessionRecord.EXERCISE_DURATION_TOTAL;
import static android.healthconnect.cts.utils.DataFactory.SESSION_END_TIME;
import static android.healthconnect.cts.utils.DataFactory.SESSION_START_TIME;
import static android.healthconnect.cts.utils.DataFactory.generateMetadata;

import static com.google.common.truth.Truth.assertThat;

import android.health.connect.AggregateRecordsGroupedByDurationResponse;
import android.health.connect.AggregateRecordsRequest;
import android.health.connect.AggregateRecordsResponse;
import android.health.connect.HealthDataCategory;
import android.health.connect.LocalTimeRangeFilter;
import android.health.connect.TimeInstantRangeFilter;
import android.health.connect.datatypes.ExerciseSegment;
import android.health.connect.datatypes.ExerciseSegmentType;
import android.health.connect.datatypes.ExerciseSessionRecord;
import android.health.connect.datatypes.ExerciseSessionType;
import android.healthconnect.cts.utils.AssumptionCheckerRule;
import android.healthconnect.cts.utils.TestUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;

public class ExerciseDurationAggregationTest {
    private final TimeInstantRangeFilter mFilterAllSession =
            new TimeInstantRangeFilter.Builder()
                    .setStartTime(Instant.EPOCH)
                    .setEndTime(Instant.now().plusSeconds(1000))
                    .build();

    private final TimeInstantRangeFilter mFilterSmallWindow =
            new TimeInstantRangeFilter.Builder()
                    .setStartTime(SESSION_START_TIME)
                    .setEndTime(SESSION_END_TIME)
                    .build();

    private final AggregateRecordsRequest<Long> mAggregateAllRecordsRequest =
            new AggregateRecordsRequest.Builder<Long>(mFilterAllSession)
                    .addAggregationType(EXERCISE_DURATION_TOTAL)
                    .build();

    private final AggregateRecordsRequest<Long> mAggregateInSmallWindow =
            new AggregateRecordsRequest.Builder<Long>(mFilterSmallWindow)
                    .addAggregationType(EXERCISE_DURATION_TOTAL)
                    .build();

    private static final String PACKAGE_NAME = "android.healthconnect.cts";

    @Rule
    public AssumptionCheckerRule mSupportedHardwareRule =
            new AssumptionCheckerRule(
                    TestUtils::isHardwareSupported, "Tests should run on supported hardware only.");

    @Before
    public void setUp() throws InterruptedException {
        TestUtils.deleteAllStagedRemoteData();
    }

    @After
    public void tearDown() throws InterruptedException {
        TestUtils.verifyDeleteRecords(
                ExerciseSessionRecord.class,
                new TimeInstantRangeFilter.Builder()
                        .setStartTime(Instant.EPOCH)
                        .setEndTime(Instant.now())
                        .build());
    }

    @Test
    public void testSimpleAggregation_oneSession_returnsItsDuration() throws InterruptedException {
        TestUtils.setupAggregation(PACKAGE_NAME, HealthDataCategory.ACTIVITY);
        ExerciseSessionRecord session =
                new ExerciseSessionRecord.Builder(
                                generateMetadata(),
                                SESSION_START_TIME,
                                SESSION_END_TIME,
                                ExerciseSessionType
                                        .EXERCISE_SESSION_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING)
                        .build();
        AggregateRecordsResponse<Long> response =
                TestUtils.getAggregateResponse(mAggregateAllRecordsRequest, List.of(session));

        assertThat(response.get(EXERCISE_DURATION_TOTAL)).isNotNull();
        assertThat(response.get(EXERCISE_DURATION_TOTAL))
                .isEqualTo(
                        session.getEndTime().toEpochMilli()
                                - session.getStartTime().toEpochMilli());
        assertThat(response.getZoneOffset(EXERCISE_DURATION_TOTAL))
                .isEqualTo(ZoneOffset.systemDefault().getRules().getOffset(Instant.now()));
    }

    @Test
    public void testSimpleAggregation_oneSessionStartEarlierThanWindow_returnsOverlapDuration()
            throws InterruptedException {
        TestUtils.setupAggregation(PACKAGE_NAME, HealthDataCategory.ACTIVITY);
        ExerciseSessionRecord session =
                new ExerciseSessionRecord.Builder(
                                generateMetadata(),
                                SESSION_START_TIME.minusSeconds(10),
                                SESSION_END_TIME,
                                ExerciseSessionType
                                        .EXERCISE_SESSION_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING)
                        .build();
        AggregateRecordsResponse<Long> response =
                TestUtils.getAggregateResponse(mAggregateInSmallWindow, List.of(session));

        assertThat(response.get(EXERCISE_DURATION_TOTAL)).isNotNull();
        assertThat(response.get(EXERCISE_DURATION_TOTAL))
                .isEqualTo(SESSION_END_TIME.toEpochMilli() - SESSION_START_TIME.toEpochMilli());
        assertThat(response.getZoneOffset(EXERCISE_DURATION_TOTAL))
                .isEqualTo(ZoneOffset.systemDefault().getRules().getOffset(Instant.now()));
    }

    @Test
    public void testSimpleAggregation_oneSessionBiggerThanWindow_returnsOverlapDuration()
            throws InterruptedException {
        TestUtils.setupAggregation(PACKAGE_NAME, HealthDataCategory.ACTIVITY);

        ExerciseSessionRecord session =
                new ExerciseSessionRecord.Builder(
                                generateMetadata(),
                                SESSION_START_TIME.minusSeconds(100),
                                SESSION_END_TIME.plusSeconds(100),
                                ExerciseSessionType
                                        .EXERCISE_SESSION_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING)
                        .build();
        AggregateRecordsResponse<Long> response =
                TestUtils.getAggregateResponse(mAggregateInSmallWindow, List.of(session));

        assertThat(response.get(EXERCISE_DURATION_TOTAL)).isNotNull();
        assertThat(response.get(EXERCISE_DURATION_TOTAL))
                .isEqualTo(SESSION_END_TIME.toEpochMilli() - SESSION_START_TIME.toEpochMilli());
        assertThat(response.getZoneOffset(EXERCISE_DURATION_TOTAL))
                .isEqualTo(ZoneOffset.systemDefault().getRules().getOffset(Instant.now()));
    }

    @Test
    public void testSimpleAggregation_oneSessionWithRest_returnsDurationMinusRest()
            throws InterruptedException {
        TestUtils.setupAggregation(PACKAGE_NAME, HealthDataCategory.ACTIVITY);

        ExerciseSegment restSegment =
                new ExerciseSegment.Builder(
                                SESSION_START_TIME,
                                SESSION_START_TIME.plusSeconds(100),
                                ExerciseSegmentType.EXERCISE_SEGMENT_TYPE_REST)
                        .build();
        ExerciseSessionRecord session =
                new ExerciseSessionRecord.Builder(
                                generateMetadata(),
                                SESSION_START_TIME,
                                SESSION_END_TIME,
                                ExerciseSessionType.EXERCISE_SESSION_TYPE_CALISTHENICS)
                        .setSegments(
                                List.of(
                                        restSegment,
                                        new ExerciseSegment.Builder(
                                                        SESSION_START_TIME.plusSeconds(200),
                                                        SESSION_START_TIME.plusSeconds(600),
                                                        ExerciseSegmentType
                                                                .EXERCISE_SEGMENT_TYPE_BURPEE)
                                                .build()))
                        .build();

        AggregateRecordsResponse<Long> response =
                TestUtils.getAggregateResponse(mAggregateAllRecordsRequest, List.of(session));

        assertThat(response.get(EXERCISE_DURATION_TOTAL)).isNotNull();

        long restDuration =
                restSegment.getEndTime().toEpochMilli() - restSegment.getStartTime().toEpochMilli();
        assertThat(response.get(EXERCISE_DURATION_TOTAL))
                .isEqualTo(
                        session.getEndTime().toEpochMilli()
                                - session.getStartTime().toEpochMilli()
                                - restDuration);
    }

    @Test
    public void testAggregationByDuration_oneSession_returnsSplitDurationIntoGroups()
            throws InterruptedException {
        TestUtils.setupAggregation(PACKAGE_NAME, HealthDataCategory.ACTIVITY);
        Instant endTime = SESSION_START_TIME.plus(10, ChronoUnit.HOURS);
        ExerciseSessionRecord session =
                new ExerciseSessionRecord.Builder(
                                generateMetadata(),
                                SESSION_START_TIME,
                                endTime,
                                ExerciseSessionType.EXERCISE_SESSION_TYPE_BADMINTON)
                        .build();
        TestUtils.insertRecords(List.of(session));

        List<AggregateRecordsGroupedByDurationResponse<Long>> responses =
                TestUtils.getAggregateResponseGroupByDuration(
                        new AggregateRecordsRequest.Builder<Long>(
                                        new TimeInstantRangeFilter.Builder()
                                                .setStartTime(SESSION_START_TIME)
                                                .setEndTime(endTime)
                                                .build())
                                .addAggregationType(EXERCISE_DURATION_TOTAL)
                                .build(),
                        Duration.of(1, ChronoUnit.HOURS));

        assertThat(responses).isNotEmpty();
        assertThat(responses.size()).isEqualTo(10);
        for (AggregateRecordsGroupedByDurationResponse<Long> response : responses) {
            assertThat(response.get(EXERCISE_DURATION_TOTAL)).isEqualTo(3600000);
        }
    }

    @Test
    public void testAggregation_oneSessionLocalTimeFilter_findsSessionWithMinOffset()
            throws InterruptedException {
        TestUtils.setupAggregation(PACKAGE_NAME, HealthDataCategory.ACTIVITY);
        Instant endTime = Instant.now();
        LocalDateTime endTimeLocal = LocalDateTime.ofInstant(endTime, ZoneOffset.UTC);

        long sessionDurationSeconds = 3600;
        ExerciseSessionRecord session =
                new ExerciseSessionRecord.Builder(
                                generateMetadata(),
                                endTime.minusSeconds(sessionDurationSeconds),
                                endTime,
                                ExerciseSessionType.EXERCISE_SESSION_TYPE_BADMINTON)
                        .setStartZoneOffset(ZoneOffset.MIN)
                        .setEndZoneOffset(ZoneOffset.MIN)
                        .build();

        AggregateRecordsResponse<Long> response =
                TestUtils.getAggregateResponse(
                        new AggregateRecordsRequest.Builder<Long>(
                                        new LocalTimeRangeFilter.Builder()
                                                .setStartTime(endTimeLocal.minusHours(25))
                                                .setEndTime(endTimeLocal.minusHours(15))
                                                .build())
                                .addAggregationType(EXERCISE_DURATION_TOTAL)
                                .build(),
                        List.of(session));

        assertThat(response.get(EXERCISE_DURATION_TOTAL)).isEqualTo(sessionDurationSeconds * 1000);
    }

    @Test
    public void testAggregation_oneSessionLocalTimeFilterExcludeSegment_substractsExcludeInterval()
            throws InterruptedException {
        TestUtils.setupAggregation(PACKAGE_NAME, HealthDataCategory.ACTIVITY);
        Instant endTime = SESSION_START_TIME.plus(1, ChronoUnit.HOURS);
        ExerciseSessionRecord session =
                new ExerciseSessionRecord.Builder(
                                generateMetadata(),
                                SESSION_START_TIME,
                                endTime,
                                ExerciseSessionType.EXERCISE_SESSION_TYPE_BADMINTON)
                        .setStartZoneOffset(ZoneOffset.MIN)
                        .setEndZoneOffset(ZoneOffset.MIN)
                        .setSegments(
                                List.of(
                                        new ExerciseSegment.Builder(
                                                        SESSION_START_TIME.plusSeconds(10),
                                                        endTime.minusSeconds(10),
                                                        ExerciseSegmentType
                                                                .EXERCISE_SEGMENT_TYPE_PAUSE)
                                                .build()))
                        .build();

        LocalDateTime endTimeLocal = LocalDateTime.ofInstant(endTime, ZoneOffset.UTC);
        AggregateRecordsResponse<Long> response =
                TestUtils.getAggregateResponse(
                        new AggregateRecordsRequest.Builder<Long>(
                                        new LocalTimeRangeFilter.Builder()
                                                .setStartTime(endTimeLocal.minusHours(25))
                                                .setEndTime(endTimeLocal.minusHours(15))
                                                .build())
                                .addAggregationType(EXERCISE_DURATION_TOTAL)
                                .build(),
                        List.of(session));

        assertThat(response.get(EXERCISE_DURATION_TOTAL)).isEqualTo(20000);
    }
}
