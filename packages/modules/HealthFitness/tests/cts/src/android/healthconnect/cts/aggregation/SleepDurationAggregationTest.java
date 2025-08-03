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

import static android.health.connect.datatypes.SleepSessionRecord.SLEEP_DURATION_TOTAL;
import static android.healthconnect.cts.utils.DataFactory.SESSION_END_TIME;
import static android.healthconnect.cts.utils.DataFactory.SESSION_START_TIME;
import static android.healthconnect.cts.utils.DataFactory.generateMetadata;

import static com.google.common.truth.Truth.assertThat;

import android.health.connect.AggregateRecordsGroupedByDurationResponse;
import android.health.connect.AggregateRecordsRequest;
import android.health.connect.AggregateRecordsResponse;
import android.health.connect.HealthDataCategory;
import android.health.connect.TimeInstantRangeFilter;
import android.health.connect.datatypes.SleepSessionRecord;
import android.healthconnect.cts.utils.AssumptionCheckerRule;
import android.healthconnect.cts.utils.TestUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;

public class SleepDurationAggregationTest {
    private final TimeInstantRangeFilter mFilterAllSession =
            new TimeInstantRangeFilter.Builder()
                    .setStartTime(Instant.EPOCH)
                    .setEndTime(Instant.now().plusSeconds(1000))
                    .build();

    private final AggregateRecordsRequest<Long> mAggregateAllRecordsRequest =
            new AggregateRecordsRequest.Builder<Long>(mFilterAllSession)
                    .addAggregationType(SLEEP_DURATION_TOTAL)
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
                SleepSessionRecord.class,
                new TimeInstantRangeFilter.Builder()
                        .setStartTime(Instant.EPOCH)
                        .setEndTime(Instant.now())
                        .build());
    }

    @Test
    public void testSimpleAggregation_oneSession_returnsItsDuration() throws InterruptedException {
        TestUtils.setupAggregation(PACKAGE_NAME, HealthDataCategory.SLEEP);
        SleepSessionRecord session =
                new SleepSessionRecord.Builder(
                                generateMetadata(), SESSION_START_TIME, SESSION_END_TIME)
                        .build();
        AggregateRecordsResponse<Long> response =
                TestUtils.getAggregateResponse(mAggregateAllRecordsRequest, List.of(session));

        assertThat(response.get(SLEEP_DURATION_TOTAL)).isNotNull();
        assertThat(response.get(SLEEP_DURATION_TOTAL))
                .isEqualTo(
                        session.getEndTime().toEpochMilli()
                                - session.getStartTime().toEpochMilli());
        assertThat(response.getZoneOffset(SLEEP_DURATION_TOTAL))
                .isEqualTo(ZoneOffset.systemDefault().getRules().getOffset(Instant.now()));
    }

    @Test
    public void testSimpleAggregation_oneSessionWithAwake_returnsDurationMinusAwake()
            throws InterruptedException {
        TestUtils.setupAggregation(PACKAGE_NAME, HealthDataCategory.SLEEP);
        SleepSessionRecord.Stage awakeStage =
                new SleepSessionRecord.Stage(
                        SESSION_START_TIME,
                        SESSION_START_TIME.plusSeconds(100),
                        SleepSessionRecord.StageType.STAGE_TYPE_AWAKE);
        SleepSessionRecord session =
                new SleepSessionRecord.Builder(
                                generateMetadata(), SESSION_START_TIME, SESSION_END_TIME)
                        .setStages(
                                List.of(
                                        awakeStage,
                                        new SleepSessionRecord.Stage(
                                                SESSION_START_TIME.plusSeconds(200),
                                                SESSION_START_TIME.plusSeconds(1400),
                                                SleepSessionRecord.StageType
                                                        .STAGE_TYPE_SLEEPING_DEEP),
                                        new SleepSessionRecord.Stage(
                                                SESSION_START_TIME.plusSeconds(1500),
                                                SESSION_START_TIME.plusSeconds(2000),
                                                SleepSessionRecord.StageType
                                                        .STAGE_TYPE_SLEEPING_LIGHT),
                                        new SleepSessionRecord.Stage(
                                                SESSION_START_TIME.plusSeconds(2100),
                                                SESSION_START_TIME.plusSeconds(3000),
                                                SleepSessionRecord.StageType
                                                        .STAGE_TYPE_SLEEPING_REM)))
                        .build();

        AggregateRecordsResponse<Long> response =
                TestUtils.getAggregateResponse(mAggregateAllRecordsRequest, List.of(session));

        assertThat(response.get(SLEEP_DURATION_TOTAL)).isNotNull();

        long awakeDuration =
                awakeStage.getEndTime().toEpochMilli() - awakeStage.getStartTime().toEpochMilli();
        assertThat(response.get(SLEEP_DURATION_TOTAL))
                .isEqualTo(
                        session.getEndTime().toEpochMilli()
                                - session.getStartTime().toEpochMilli()
                                - awakeDuration);
    }

    @Test
    public void testAggregationByDuration_oneSession_returnsSplitDurationIntoGroups()
            throws InterruptedException {
        TestUtils.setupAggregation(PACKAGE_NAME, HealthDataCategory.SLEEP);
        Instant endTime = SESSION_START_TIME.plus(10, ChronoUnit.HOURS);
        SleepSessionRecord session =
                new SleepSessionRecord.Builder(generateMetadata(), SESSION_START_TIME, endTime)
                        .build();
        TestUtils.insertRecords(List.of(session));

        List<AggregateRecordsGroupedByDurationResponse<Long>> responses =
                TestUtils.getAggregateResponseGroupByDuration(
                        new AggregateRecordsRequest.Builder<Long>(
                                        new TimeInstantRangeFilter.Builder()
                                                .setStartTime(SESSION_START_TIME)
                                                .setEndTime(endTime)
                                                .build())
                                .addAggregationType(SLEEP_DURATION_TOTAL)
                                .build(),
                        Duration.of(1, ChronoUnit.HOURS));

        assertThat(responses).isNotEmpty();
        assertThat(responses.size()).isEqualTo(10);
        for (AggregateRecordsGroupedByDurationResponse<Long> response : responses) {
            assertThat(response.get(SLEEP_DURATION_TOTAL)).isEqualTo(3600000);
        }
    }
}
