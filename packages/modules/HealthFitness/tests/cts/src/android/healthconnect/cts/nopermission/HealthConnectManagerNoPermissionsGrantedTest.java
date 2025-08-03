/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.healthconnect.cts.nopermission;

import static android.health.connect.datatypes.HeartRateRecord.BPM_MAX;
import static android.health.connect.datatypes.HeartRateRecord.BPM_MIN;
import static android.healthconnect.cts.utils.DataFactory.buildExerciseSession;
import static android.healthconnect.cts.utils.DataFactory.buildSleepSession;
import static android.healthconnect.cts.utils.DataFactory.getDistanceRecordWithNonEmptyId;
import static android.healthconnect.cts.utils.DataFactory.getHeartRateRecord;
import static android.healthconnect.cts.utils.DataFactory.getStepsRecord;
import static android.healthconnect.cts.utils.DataFactory.getTotalCaloriesBurnedRecord;
import static android.healthconnect.cts.utils.TestUtils.deleteRecords;
import static android.healthconnect.cts.utils.TestUtils.getAggregateResponse;
import static android.healthconnect.cts.utils.TestUtils.getAggregateResponseGroupByDuration;
import static android.healthconnect.cts.utils.TestUtils.getAggregateResponseGroupByPeriod;
import static android.healthconnect.cts.utils.TestUtils.getChangeLogToken;
import static android.healthconnect.cts.utils.TestUtils.insertRecords;
import static android.healthconnect.cts.utils.TestUtils.readRecords;
import static android.healthconnect.cts.utils.TestUtils.updateRecords;
import static android.healthconnect.cts.utils.TestUtils.verifyDeleteRecords;

import static com.google.common.truth.Truth.assertThat;

import android.health.connect.AggregateRecordsRequest;
import android.health.connect.HealthConnectException;
import android.health.connect.LocalTimeRangeFilter;
import android.health.connect.ReadRecordsRequestUsingFilters;
import android.health.connect.ReadRecordsRequestUsingIds;
import android.health.connect.TimeInstantRangeFilter;
import android.health.connect.changelog.ChangeLogTokenRequest;
import android.health.connect.datatypes.DataOrigin;
import android.health.connect.datatypes.Record;
import android.healthconnect.cts.utils.AssumptionCheckerRule;
import android.healthconnect.cts.utils.TestUtils;
import android.platform.test.annotations.AppModeFull;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** These test run under an environment which has no HC permissions */
@AppModeFull(reason = "HealthConnectManager is not accessible to instant apps")
@RunWith(AndroidJUnit4.class)
public class HealthConnectManagerNoPermissionsGrantedTest {

    @Rule
    public AssumptionCheckerRule mSupportedHardwareRule =
            new AssumptionCheckerRule(
                    TestUtils::isHardwareSupported, "Tests should run on supported hardware only.");

    @Test
    public void testInsertNotAllowed() throws InterruptedException {
        for (Record testRecord : getTestRecords()) {
            try {
                insertRecords(Collections.singletonList(testRecord));
                Assert.fail("Insert must be not allowed without right HC permission");
            } catch (HealthConnectException healthConnectException) {
                assertThat(healthConnectException.getErrorCode())
                        .isEqualTo(HealthConnectException.ERROR_SECURITY);
            }
        }
    }

    @Test
    public void testUpdateNotAllowed() throws InterruptedException {
        for (Record testRecord : getTestRecords()) {
            try {
                updateRecords(Collections.singletonList(testRecord));
                Assert.fail("Update must be not allowed without right HC permission");
            } catch (HealthConnectException healthConnectException) {
                assertThat(healthConnectException.getErrorCode())
                        .isEqualTo(HealthConnectException.ERROR_SECURITY);
            }
        }
    }

    @Test
    public void testDeleteUsingIdNotAllowed() throws InterruptedException {
        for (Record testRecord : getTestRecords()) {
            try {
                deleteRecords(Collections.singletonList(testRecord));
                Assert.fail("Delete using ids must be not allowed without right HC permission");
            } catch (HealthConnectException healthConnectException) {
                assertThat(healthConnectException.getErrorCode())
                        .isEqualTo(HealthConnectException.ERROR_SECURITY);
            }
        }
    }

    @Test
    public void testDeleteUsingFilterNotAllowed() throws InterruptedException {
        for (Record testRecord : getTestRecords()) {
            try {
                verifyDeleteRecords(
                        testRecord.getClass(),
                        new TimeInstantRangeFilter.Builder()
                                .setStartTime(Instant.now())
                                .setEndTime(Instant.now().plusMillis(1000))
                                .build());
                Assert.fail("Delete using filters must be not allowed without right HC permission");
            } catch (HealthConnectException healthConnectException) {
                assertThat(healthConnectException.getErrorCode())
                        .isEqualTo(HealthConnectException.ERROR_SECURITY);
            }
        }
    }

    @Test
    public void testChangeLogsTokenNotAllowed() throws InterruptedException {
        for (Record testRecord : getTestRecords()) {
            try {
                getChangeLogToken(
                        new ChangeLogTokenRequest.Builder()
                                .addRecordType(testRecord.getClass())
                                .build());
                Assert.fail(
                        "Getting change log token must be not allowed without right HC permission");
            } catch (HealthConnectException healthConnectException) {
                assertThat(healthConnectException.getErrorCode())
                        .isEqualTo(HealthConnectException.ERROR_SECURITY);
            }
        }
    }

    @Test
    public void testReadByFiltersNotAllowed() throws InterruptedException {
        for (Record testRecord : getTestRecords()) {
            try {
                readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(testRecord.getClass())
                                .build());
                Assert.fail(
                        "Read records by filters must be not allowed without right HC permission");
            } catch (HealthConnectException healthConnectException) {
                assertThat(healthConnectException.getErrorCode())
                        .isEqualTo(HealthConnectException.ERROR_SECURITY);
            }
        }
    }

    @Test
    public void testReadByRecordIdsNotAllowed() throws InterruptedException {
        for (Record testRecord : getTestRecords()) {
            try {
                readRecords(
                        new ReadRecordsRequestUsingIds.Builder<>(testRecord.getClass())
                                .addId("id")
                                .build());
                Assert.fail(
                        "Read records by record ids must be not allowed without right HC "
                                + "permission");
            } catch (HealthConnectException healthConnectException) {
                assertThat(healthConnectException.getErrorCode())
                        .isEqualTo(HealthConnectException.ERROR_SECURITY);
            }
        }
    }

    @Test
    public void testReadByClientIdsNotAllowed() throws InterruptedException {
        for (Record testRecord : getTestRecords()) {
            try {
                readRecords(
                        new ReadRecordsRequestUsingIds.Builder<>(testRecord.getClass())
                                .addClientRecordId("client_id")
                                .build());
                Assert.fail(
                        "Read records by client ids must be not allowed without right HC "
                                + "permission");
            } catch (HealthConnectException healthConnectException) {
                assertThat(healthConnectException.getErrorCode())
                        .isEqualTo(HealthConnectException.ERROR_SECURITY);
            }
        }
    }

    @Test
    public void testAggregateNotAllowed() throws InterruptedException {
        try {
            List<Record> records =
                    Arrays.asList(
                            getHeartRateRecord(71), getHeartRateRecord(72), getHeartRateRecord(73));
            getAggregateResponse(
                    new AggregateRecordsRequest.Builder<Long>(
                                    new TimeInstantRangeFilter.Builder()
                                            .setStartTime(Instant.ofEpochMilli(0))
                                            .setEndTime(Instant.now())
                                            .build())
                            .addAggregationType(BPM_MAX)
                            .addAggregationType(BPM_MIN)
                            .addDataOriginsFilter(
                                    new DataOrigin.Builder().setPackageName("abc").build())
                            .build(),
                    records);
            Assert.fail("Get Aggregations must be not allowed without right HC permission");
        } catch (HealthConnectException healthConnectException) {
            assertThat(healthConnectException.getErrorCode())
                    .isEqualTo(HealthConnectException.ERROR_SECURITY);
        }
    }

    @Test
    public void testAggregateGroupByDurationNotAllowed() throws InterruptedException {
        try {
            Instant start = Instant.now().minusMillis(500);
            Instant end = Instant.now().plusMillis(2500);
            getAggregateResponseGroupByDuration(
                    new AggregateRecordsRequest.Builder<Long>(
                                    new TimeInstantRangeFilter.Builder()
                                            .setStartTime(start)
                                            .setEndTime(end)
                                            .build())
                            .addAggregationType(BPM_MAX)
                            .addAggregationType(BPM_MIN)
                            .build(),
                    Duration.ofSeconds(1));
            Assert.fail(
                    "Aggregations group by duration must be not allowed without right HC"
                            + " permission");
        } catch (HealthConnectException healthConnectException) {
            assertThat(healthConnectException.getErrorCode())
                    .isEqualTo(HealthConnectException.ERROR_SECURITY);
        }
    }

    @Test
    public void testAggregateGroupByPeriodNotAllowed() throws InterruptedException {
        try {
            Instant start = Instant.now().minus(3, ChronoUnit.DAYS);
            Instant end = start.plus(3, ChronoUnit.DAYS);
            getAggregateResponseGroupByPeriod(
                    new AggregateRecordsRequest.Builder<Long>(
                                    new LocalTimeRangeFilter.Builder()
                                            .setStartTime(
                                                    LocalDateTime.ofInstant(start, ZoneOffset.UTC))
                                            .setEndTime(
                                                    LocalDateTime.ofInstant(end, ZoneOffset.UTC))
                                            .build())
                            .addAggregationType(BPM_MAX)
                            .addAggregationType(BPM_MIN)
                            .build(),
                    Period.ofDays(1));
            Assert.fail(
                    "Aggregation group by period must be not allowed without right HC permission");
        } catch (HealthConnectException healthConnectException) {
            assertThat(healthConnectException.getErrorCode())
                    .isEqualTo(HealthConnectException.ERROR_SECURITY);
        }
    }

    private static List<Record> getTestRecords() {
        return Arrays.asList(
                getStepsRecord(),
                getHeartRateRecord(),
                buildSleepSession(),
                getDistanceRecordWithNonEmptyId(),
                getTotalCaloriesBurnedRecord("client_id"),
                buildExerciseSession());
    }
}
