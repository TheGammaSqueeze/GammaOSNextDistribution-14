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

package android.healthconnect.cts.readdata;

import static android.healthconnect.cts.utils.DataFactory.getDistanceRecord;
import static android.healthconnect.cts.utils.DataFactory.getStepsRecord;
import static android.healthconnect.cts.utils.TestUtils.getReadRecordsResponse;
import static android.healthconnect.cts.utils.TestUtils.insertRecords;
import static android.healthconnect.cts.utils.TestUtils.readRecords;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import static java.time.temporal.ChronoUnit.DAYS;
import static java.time.temporal.ChronoUnit.HOURS;

import android.health.connect.ReadRecordsRequest;
import android.health.connect.ReadRecordsRequestUsingFilters;
import android.health.connect.ReadRecordsResponse;
import android.health.connect.datatypes.DistanceRecord;
import android.health.connect.datatypes.Record;
import android.health.connect.datatypes.StepsRecord;
import android.healthconnect.cts.utils.AssumptionCheckerRule;
import android.healthconnect.cts.utils.TestUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.time.Instant;
import java.util.List;

public class OrderingTests {
    @Rule
    public AssumptionCheckerRule mSupportedHardwareRule =
            new AssumptionCheckerRule(
                    TestUtils::isHardwareSupported, "Tests should run on supported hardware only.");

    @Before
    public void setup() {
        TestUtils.deleteAllStagedRemoteData();
    }

    @After
    public void tearDown() {
        TestUtils.deleteAllStagedRemoteData();
    }

    @Test
    public void ordering_ascendingSetToTrue_sortedByStartTimeInAscendingOrder() throws Exception {
        Instant startTime1 = Instant.now().minus(1, DAYS).truncatedTo(DAYS);
        Instant startTime2 = startTime1.plus(1, HOURS);
        Instant startTime3 = startTime2.plus(1, HOURS);
        insertRecords(
                List.of(
                        getDistanceRecord(567.8, startTime2, startTime2.plusMillis(1)),
                        getDistanceRecord(901.2, startTime3, startTime3.plusMillis(1)),
                        getDistanceRecord(123.4, startTime1, startTime1.plusMillis(1))));

        ReadRecordsRequest<DistanceRecord> request =
                new ReadRecordsRequestUsingFilters.Builder<>(DistanceRecord.class)
                        .setAscending(true)
                        .build();

        List<DistanceRecord> records = readRecords(request);
        assertThat(records).hasSize(3);
        assertThat(records.get(0).getStartTime()).isEqualTo(startTime1);
        assertThat(records.get(1).getStartTime()).isEqualTo(startTime2);
        assertThat(records.get(2).getStartTime()).isEqualTo(startTime3);
    }

    @Test
    public void ordering_ascendingSetToFalse_sortedByStartTimeInDescendingOrder() throws Exception {
        Instant startTime1 = Instant.now().minus(1, DAYS).truncatedTo(DAYS);
        Instant startTime2 = startTime1.plus(1, HOURS);
        Instant startTime3 = startTime2.plus(1, HOURS);
        insertRecords(
                List.of(
                        getDistanceRecord(567.8, startTime2, startTime2.plusMillis(1)),
                        getDistanceRecord(901.2, startTime3, startTime3.plusMillis(1)),
                        getDistanceRecord(123.4, startTime1, startTime1.plusMillis(1))));

        ReadRecordsRequest<DistanceRecord> request =
                new ReadRecordsRequestUsingFilters.Builder<>(DistanceRecord.class)
                        .setAscending(false)
                        .build();

        List<DistanceRecord> records = readRecords(request);
        assertThat(records).hasSize(3);
        assertThat(records.get(0).getStartTime()).isEqualTo(startTime3);
        assertThat(records.get(1).getStartTime()).isEqualTo(startTime2);
        assertThat(records.get(2).getStartTime()).isEqualTo(startTime1);
    }

    @Test
    public void ordering_ascendingNotSet_sortedByStartTimeInAscendingOrder() throws Exception {
        Instant startTime1 = Instant.now().minus(1, DAYS).truncatedTo(DAYS);
        Instant startTime2 = startTime1.plus(1, HOURS);
        Instant startTime3 = startTime2.plus(1, HOURS);

        insertRecords(
                List.of(
                        getStepsRecord(200, startTime2, startTime2.plusMillis(1), "id2"),
                        getStepsRecord(100, startTime1, startTime1.plusMillis(1), "id1"),
                        getStepsRecord(300, startTime3, startTime3.plusMillis(1), "id3")));

        ReadRecordsRequest<StepsRecord> request =
                new ReadRecordsRequestUsingFilters.Builder<>(StepsRecord.class).build();

        List<StepsRecord> records = readRecords(request);
        assertThat(records).hasSize(3);
        assertThat(records.get(0).getStartTime()).isEqualTo(startTime1);
        assertThat(records.get(1).getStartTime()).isEqualTo(startTime2);
        assertThat(records.get(2).getStartTime()).isEqualTo(startTime3);
    }

    @Test
    public void ordering_sameStartTime_sortedByRowIdAscending() throws Exception {
        Instant startTime = Instant.now().minus(1, DAYS).truncatedTo(DAYS);

        insertRecords(
                List.of(
                        getStepsRecord(100, startTime, startTime.plusMillis(21), "id1"),
                        getStepsRecord(20, startTime, startTime.plusMillis(10), "id2"),
                        getStepsRecord(300, startTime, startTime.plusMillis(15), "id3")));

        ReadRecordsRequest<StepsRecord> descendingRequest =
                new ReadRecordsRequestUsingFilters.Builder<>(StepsRecord.class)
                        .setAscending(false)
                        .build();
        List<StepsRecord> descendingRecords = readRecords(descendingRequest);
        assertThat(descendingRecords).hasSize(3);
        assertClientId(descendingRecords.get(0), "id1");
        assertClientId(descendingRecords.get(1), "id2");
        assertClientId(descendingRecords.get(2), "id3");

        ReadRecordsRequest<StepsRecord> ascendingRequest =
                new ReadRecordsRequestUsingFilters.Builder<>(StepsRecord.class)
                        .setAscending(true)
                        .build();

        List<StepsRecord> ascendingRecords = readRecords(ascendingRequest);
        assertThat(ascendingRecords).hasSize(3);
        assertClientId(ascendingRecords.get(0), "id1");
        assertClientId(ascendingRecords.get(1), "id2");
        assertClientId(ascendingRecords.get(2), "id3");
    }

    @Test
    public void ordering_pageTokenProvided_retrieveAscOrderFromPageToken() throws Exception {
        Instant startTime1 = Instant.now().minus(1, DAYS).truncatedTo(DAYS);
        Instant startTime2 = startTime1.plus(1, HOURS);
        Instant startTime3 = startTime2.plus(1, HOURS);
        Instant startTime4 = startTime3.plus(1, HOURS);
        insertRecords(
                List.of(
                        getDistanceRecord(567.8, startTime2, startTime2.plusMillis(1)),
                        getDistanceRecord(345.6, startTime4, startTime4.plusMillis(1)),
                        getDistanceRecord(901.2, startTime3, startTime3.plusMillis(1)),
                        getDistanceRecord(123.4, startTime1, startTime1.plusMillis(1))));

        ReadRecordsRequest<DistanceRecord> request =
                new ReadRecordsRequestUsingFilters.Builder<>(DistanceRecord.class)
                        .setPageSize(1)
                        .build();
        ReadRecordsResponse<DistanceRecord> response = getReadRecordsResponse(request);
        request =
                new ReadRecordsRequestUsingFilters.Builder<>(DistanceRecord.class)
                        .setPageToken(response.getNextPageToken())
                        .build();

        List<DistanceRecord> records = readRecords(request);
        assertThat(records).hasSize(3);
        assertThat(records.get(0).getStartTime()).isEqualTo(startTime2);
        assertThat(records.get(1).getStartTime()).isEqualTo(startTime3);
        assertThat(records.get(2).getStartTime()).isEqualTo(startTime4);
    }

    @Test
    public void ordering_pageTokenProvided_retrieveDecOrderFromPageToken() throws Exception {
        Instant startTime1 = Instant.now().minus(1, DAYS).truncatedTo(DAYS);
        Instant startTime2 = startTime1.plus(1, HOURS);
        Instant startTime3 = startTime2.plus(1, HOURS);
        Instant startTime4 = startTime3.plus(1, HOURS);
        insertRecords(
                List.of(
                        getDistanceRecord(567.8, startTime2, startTime2.plusMillis(1)),
                        getDistanceRecord(345.6, startTime4, startTime4.plusMillis(1)),
                        getDistanceRecord(901.2, startTime3, startTime3.plusMillis(1)),
                        getDistanceRecord(123.4, startTime1, startTime1.plusMillis(1))));

        ReadRecordsRequest<DistanceRecord> request =
                new ReadRecordsRequestUsingFilters.Builder<>(DistanceRecord.class)
                        .setAscending(false)
                        .setPageSize(1)
                        .build();
        ReadRecordsResponse<DistanceRecord> response = getReadRecordsResponse(request);
        request =
                new ReadRecordsRequestUsingFilters.Builder<>(DistanceRecord.class)
                        .setPageToken(response.getNextPageToken())
                        .build();

        List<DistanceRecord> records = readRecords(request);
        assertThat(records).hasSize(3);
        assertThat(records.get(0).getStartTime()).isEqualTo(startTime3);
        assertThat(records.get(1).getStartTime()).isEqualTo(startTime2);
        assertThat(records.get(2).getStartTime()).isEqualTo(startTime1);
    }

    @Test
    public void setBothAscendingAndPageToken_throws() {
        ReadRecordsRequestUsingFilters.Builder<?> builder =
                new ReadRecordsRequestUsingFilters.Builder<>(StepsRecord.class)
                        .setPageToken(123456)
                        .setAscending(true);
        Throwable thrown = assertThrows(IllegalStateException.class, builder::build);
        assertThat(thrown).hasMessageThat().isEqualTo("Cannot set both pageToken and sort order");
    }

    private static void assertClientId(Record record, String expected) {
        assertThat(record.getMetadata().getClientRecordId()).isEqualTo(expected);
    }
}
