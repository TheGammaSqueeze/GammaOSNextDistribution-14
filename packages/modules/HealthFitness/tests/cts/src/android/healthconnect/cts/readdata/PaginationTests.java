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
import static android.healthconnect.cts.utils.DataFactory.getHeartRateRecord;
import static android.healthconnect.cts.utils.DataFactory.getStepsRecord;
import static android.healthconnect.cts.utils.DataFactory.getTotalCaloriesBurnedRecord;
import static android.healthconnect.cts.utils.TestUtils.getReadRecordsResponse;
import static android.healthconnect.cts.utils.TestUtils.insertRecords;
import static android.healthconnect.cts.utils.TestUtils.readRecords;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import static java.time.temporal.ChronoUnit.DAYS;
import static java.time.temporal.ChronoUnit.MINUTES;

import android.health.connect.ReadRecordsRequest;
import android.health.connect.ReadRecordsRequestUsingFilters;
import android.health.connect.ReadRecordsResponse;
import android.health.connect.datatypes.DistanceRecord;
import android.health.connect.datatypes.HeartRateRecord;
import android.health.connect.datatypes.Record;
import android.health.connect.datatypes.StepsRecord;
import android.health.connect.datatypes.TotalCaloriesBurnedRecord;
import android.healthconnect.cts.utils.AssumptionCheckerRule;
import android.healthconnect.cts.utils.TestUtils;

import com.google.common.collect.ImmutableList;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.time.Instant;
import java.util.List;

public class PaginationTests {
    private static final int MINIMUM_PAGE_SIZE = 1;
    private static final int MAXIMUM_PAGE_SIZE = 5000;

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
    public void readAllData_moreThanOnePage_allPagesReturnedCorrectly() throws Exception {
        Instant startTime = Instant.now().minus(1, DAYS);
        insertRecords(
                List.of(
                        getDistanceRecord(
                                4.0,
                                startTime.plus(40, MINUTES),
                                startTime.plus(50, MINUTES),
                                "id4"),
                        getDistanceRecord(1.0, startTime, startTime.plus(5, MINUTES), "id1"),
                        getDistanceRecord(
                                10.0,
                                startTime.plus(25, MINUTES),
                                startTime.plus(30, MINUTES),
                                "id3"),
                        getDistanceRecord(
                                5.0,
                                startTime.plus(5, MINUTES),
                                startTime.plus(15, MINUTES),
                                "id2"),
                        getDistanceRecord(
                                2.0,
                                startTime.plus(55, MINUTES),
                                startTime.plus(60, MINUTES),
                                "id5")));

        ReadRecordsRequest<DistanceRecord> request1 =
                new ReadRecordsRequestUsingFilters.Builder<>(DistanceRecord.class)
                        .setPageSize(2)
                        .setAscending(false)
                        .build();
        ReadRecordsResponse<DistanceRecord> page1 = getReadRecordsResponse(request1);
        assertThat(page1.getRecords()).hasSize(2);
        assertClientId(page1.getRecords().get(0), "id5");
        assertClientId(page1.getRecords().get(1), "id4");
        assertThat(page1.getNextPageToken()).isNotEqualTo(-1);

        ReadRecordsRequest<DistanceRecord> request2 =
                new ReadRecordsRequestUsingFilters.Builder<>(DistanceRecord.class)
                        .setPageSize(2)
                        .setPageToken(page1.getNextPageToken())
                        .build();
        ReadRecordsResponse<DistanceRecord> page2 = getReadRecordsResponse(request2);
        assertThat(page2.getRecords()).hasSize(2);
        assertClientId(page2.getRecords().get(0), "id3");
        assertClientId(page2.getRecords().get(1), "id2");
        assertThat(page2.getNextPageToken()).isNotEqualTo(-1);

        ReadRecordsRequest<DistanceRecord> request3 =
                new ReadRecordsRequestUsingFilters.Builder<>(DistanceRecord.class)
                        .setPageSize(2)
                        .setPageToken(page2.getNextPageToken())
                        .build();
        ReadRecordsResponse<DistanceRecord> page3 = getReadRecordsResponse(request3);
        assertThat(page3.getRecords()).hasSize(1);
        assertClientId(page3.getRecords().get(0), "id1");
        assertThat(page3.getNextPageToken()).isEqualTo(-1);
    }

    @Test
    public void readAllData_multiplePagesSameStartTimeRecords_paginatedCorrectly()
            throws Exception {
        Instant startTime = Instant.now().minus(1, DAYS);

        insertRecords(
                List.of(
                        getStepsRecord(100, startTime, startTime.plusSeconds(500), "id1"),
                        getStepsRecord(100, startTime, startTime.plusSeconds(200), "id2"),
                        getStepsRecord(100, startTime, startTime.plusSeconds(400), "id3"),
                        getStepsRecord(100, startTime, startTime.plusSeconds(300), "id4")));

        ReadRecordsRequest<StepsRecord> request1 =
                new ReadRecordsRequestUsingFilters.Builder<>(StepsRecord.class)
                        .setPageSize(2)
                        .build();
        ReadRecordsResponse<StepsRecord> page1 = getReadRecordsResponse(request1);
        assertThat(page1.getRecords()).hasSize(2);
        assertClientId(page1.getRecords().get(0), "id1");
        assertClientId(page1.getRecords().get(1), "id2");

        ReadRecordsRequest<StepsRecord> request2 =
                new ReadRecordsRequestUsingFilters.Builder<>(StepsRecord.class)
                        .setPageSize(2)
                        .setPageToken(page1.getNextPageToken())
                        .build();
        ReadRecordsResponse<StepsRecord> page2 = getReadRecordsResponse(request2);
        assertThat(page2.getRecords()).hasSize(2);
        assertClientId(page2.getRecords().get(0), "id3");
        assertClientId(page2.getRecords().get(1), "id4");
        assertThat(page2.getNextPageToken()).isEqualTo(-1);
    }

    @Test
    public void readOnePage_pageTokenSpecified_returnsCorrectPage() throws Exception {
        Instant now = Instant.now();
        insertRecords(
                List.of(
                        getHeartRateRecord(60, now.minusMillis(5000), "id1"),
                        getHeartRateRecord(70, now.minusMillis(4000), "id2"),
                        getHeartRateRecord(80, now.minusMillis(3000), "id3")));

        ReadRecordsRequest<HeartRateRecord> request1 =
                new ReadRecordsRequestUsingFilters.Builder<>(HeartRateRecord.class)
                        .setPageSize(1)
                        .build();
        ReadRecordsResponse<HeartRateRecord> page1 = getReadRecordsResponse(request1);

        ReadRecordsRequest<HeartRateRecord> request2 =
                new ReadRecordsRequestUsingFilters.Builder<>(HeartRateRecord.class)
                        .setPageSize(1)
                        .setPageToken(page1.getNextPageToken())
                        .build();
        ReadRecordsResponse<HeartRateRecord> page2 = getReadRecordsResponse(request2);
        String expectedId = page2.getRecords().get(0).getMetadata().getClientRecordId();

        ReadRecordsRequest<HeartRateRecord> request3 =
                new ReadRecordsRequestUsingFilters.Builder<>(HeartRateRecord.class)
                        .setPageSize(1)
                        .setPageToken(page2.getNextPageToken())
                        .build();
        ReadRecordsResponse<HeartRateRecord> page3 = getReadRecordsResponse(request3);
        assertThat(page3.getNextPageToken()).isEqualTo(-1);

        // Read page2 again with page1 page token, see if the correct record is returned
        ReadRecordsRequest<HeartRateRecord> request =
                new ReadRecordsRequestUsingFilters.Builder<>(HeartRateRecord.class)
                        .setPageSize(1)
                        .setPageToken(page1.getNextPageToken())
                        .build();
        assertClientId(readRecords(request).get(0), expectedId);
    }

    @Test
    public void readOnePage_pageSizeNotSet_defaultPageSizeUsed() throws Exception {
        ImmutableList.Builder<TotalCaloriesBurnedRecord> builder = new ImmutableList.Builder<>();
        for (int i = 0; i < 1001; i++) {
            builder.add(getTotalCaloriesBurnedRecord("id" + i));
        }
        insertRecords(builder.build());
        ReadRecordsRequest<TotalCaloriesBurnedRecord> request =
                new ReadRecordsRequestUsingFilters.Builder<>(TotalCaloriesBurnedRecord.class)
                        .build();
        ReadRecordsResponse<TotalCaloriesBurnedRecord> response = getReadRecordsResponse(request);
        assertThat(response.getRecords()).hasSize(1000);
        assertThat(response.getNextPageToken()).isNotEqualTo(-1);
    }

    @Test
    public void readRequest_pageSizeOutOfRange_throws() {
        ReadRecordsRequestUsingFilters.Builder<StepsRecord> request =
                new ReadRecordsRequestUsingFilters.Builder<>(StepsRecord.class);

        Throwable thrown =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> request.setPageSize(MAXIMUM_PAGE_SIZE + 1));
        assertThat(thrown.getMessage()).contains("Valid pageSize range is");
        thrown =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> request.setPageSize(MINIMUM_PAGE_SIZE - 1));
        assertThat(thrown.getMessage()).contains("Valid pageSize range is");
    }

    private static void assertClientId(Record record, String expected) {
        assertThat(record.getMetadata().getClientRecordId()).isEqualTo(expected);
    }
}
