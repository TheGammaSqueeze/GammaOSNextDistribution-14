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

package android.healthconnect.cts.datatypes;

import static android.health.connect.HealthConnectException.ERROR_INVALID_ARGUMENT;
import static android.health.connect.datatypes.StepsRecord.STEPS_COUNT_TOTAL;
import static android.healthconnect.cts.utils.DataFactory.NOW;
import static android.healthconnect.cts.utils.DataFactory.generateMetadata;
import static android.healthconnect.cts.utils.DataFactory.getCompleteStepsRecord;
import static android.healthconnect.cts.utils.DataFactory.getUpdatedStepsRecord;
import static android.healthconnect.cts.utils.TestUtils.distinctByUuid;
import static android.healthconnect.cts.utils.TestUtils.insertRecords;
import static android.healthconnect.cts.utils.TestUtils.readRecords;
import static android.healthconnect.cts.utils.TestUtils.readRecordsWithPagination;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import static java.time.ZoneOffset.UTC;
import static java.time.temporal.ChronoUnit.DAYS;
import static java.time.temporal.ChronoUnit.HOURS;

import android.content.Context;
import android.health.connect.AggregateRecordsGroupedByDurationResponse;
import android.health.connect.AggregateRecordsGroupedByPeriodResponse;
import android.health.connect.AggregateRecordsRequest;
import android.health.connect.AggregateRecordsResponse;
import android.health.connect.DeleteUsingFiltersRequest;
import android.health.connect.HealthConnectException;
import android.health.connect.HealthDataCategory;
import android.health.connect.LocalTimeRangeFilter;
import android.health.connect.ReadRecordsRequestUsingFilters;
import android.health.connect.ReadRecordsRequestUsingIds;
import android.health.connect.ReadRecordsResponse;
import android.health.connect.RecordIdFilter;
import android.health.connect.TimeInstantRangeFilter;
import android.health.connect.changelog.ChangeLogTokenRequest;
import android.health.connect.changelog.ChangeLogTokenResponse;
import android.health.connect.changelog.ChangeLogsRequest;
import android.health.connect.changelog.ChangeLogsResponse;
import android.health.connect.datatypes.DataOrigin;
import android.health.connect.datatypes.Metadata;
import android.health.connect.datatypes.Record;
import android.health.connect.datatypes.StepsRecord;
import android.healthconnect.cts.utils.AssumptionCheckerRule;
import android.healthconnect.cts.utils.DataFactory;
import android.healthconnect.cts.utils.TestUtils;
import android.platform.test.annotations.AppModeFull;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@AppModeFull(reason = "HealthConnectManager is not accessible to instant apps")
@RunWith(AndroidJUnit4.class)
public class StepsRecordTest {
    private static final String TAG = "StepsRecordTest";
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
                StepsRecord.class,
                new TimeInstantRangeFilter.Builder()
                        .setStartTime(Instant.EPOCH)
                        .setEndTime(Instant.now().plus(1, ChronoUnit.DAYS))
                        .build());
        TestUtils.deleteAllStagedRemoteData();
    }

    @Test
    public void testInsertStepsRecord() throws InterruptedException {
        List<Record> records = List.of(getBaseStepsRecord(), getCompleteStepsRecord());
        TestUtils.insertRecords(records);
    }

    @Test
    public void testUpdateStepsRecordToDuplicate() throws InterruptedException {
        List<Record> records = List.of(getBaseStepsRecord(), getStepsRecord_minusDays(1));
        records = TestUtils.insertRecords(records);

        try {
            TestUtils.updateRecords(
                    Collections.singletonList(
                            getStepsRecordDuplicateEntry(
                                    (StepsRecord) records.get(1), (StepsRecord) records.get(0))));
            Assert.fail();
        } catch (HealthConnectException healthConnectException) {
            assertThat(healthConnectException.getErrorCode()).isEqualTo(ERROR_INVALID_ARGUMENT);
            assertThat(healthConnectException.getMessage())
                    .contains(records.get(0).getMetadata().getId());
            assertThat(healthConnectException.getMessage())
                    .contains(records.get(1).getMetadata().getId());
        }
    }

    @Test
    public void testReadStepsRecord_usingIds() throws InterruptedException {
        List<Record> recordList = Arrays.asList(getCompleteStepsRecord(), getCompleteStepsRecord());
        List<Record> insertedRecords = TestUtils.insertRecords(recordList);
        assertStepsRecordUsingIds(insertedRecords);
    }

    @Test
    public void testReadStepsRecord_invalidIds() throws InterruptedException {
        ReadRecordsRequestUsingIds<StepsRecord> request =
                new ReadRecordsRequestUsingIds.Builder<>(StepsRecord.class)
                        .addId(UUID.randomUUID().toString())
                        .build();
        assertThat(request.getRecordType()).isEqualTo(StepsRecord.class);
        assertThat(request.getRecordIdFilters()).isNotNull();
        List<StepsRecord> result = TestUtils.readRecords(request);
        assertThat(result.size()).isEqualTo(0);
    }

    @Test
    public void testReadStepsRecord_usingClientRecordIds() throws InterruptedException {
        List<Record> recordList = Arrays.asList(getCompleteStepsRecord(), getCompleteStepsRecord());
        List<Record> insertedRecords = TestUtils.insertRecords(recordList);
        readStepsRecordUsingClientId(insertedRecords);
    }

    @Test
    public void testReadStepsRecord_invalidClientRecordIds() throws InterruptedException {
        ReadRecordsRequestUsingIds<StepsRecord> request =
                new ReadRecordsRequestUsingIds.Builder<>(StepsRecord.class)
                        .addClientRecordId("abc")
                        .build();
        List<StepsRecord> result = TestUtils.readRecords(request);
        assertThat(result.size()).isEqualTo(0);
    }

    @Test
    public void testReadStepsRecordUsingFilters_default() throws InterruptedException {
        List<StepsRecord> oldStepsRecords =
                TestUtils.readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(StepsRecord.class)
                                .setAscending(true)
                                .build());
        StepsRecord testRecord = getCompleteStepsRecord();
        TestUtils.insertRecords(Collections.singletonList(testRecord));
        List<StepsRecord> newStepsRecords =
                TestUtils.readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(StepsRecord.class)
                                .setAscending(true)
                                .build());
        assertThat(newStepsRecords.size()).isEqualTo(oldStepsRecords.size() + 1);
        assertThat(newStepsRecords.get(newStepsRecords.size() - 1).equals(testRecord)).isTrue();
    }

    @Test
    public void testReadStepsRecordUsingFilters_timeFilter() throws InterruptedException {
        TimeInstantRangeFilter filter =
                new TimeInstantRangeFilter.Builder()
                        .setStartTime(NOW)
                        .setEndTime(NOW.plusMillis(3000))
                        .build();
        StepsRecord testRecord = getCompleteStepsRecord();
        TestUtils.insertRecords(Collections.singletonList(testRecord));
        List<StepsRecord> newStepsRecords =
                TestUtils.readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(StepsRecord.class)
                                .setTimeRangeFilter(filter)
                                .build());
        assertThat(newStepsRecords.size()).isEqualTo(1);
        assertThat(newStepsRecords.get(newStepsRecords.size() - 1).equals(testRecord)).isTrue();
    }

    static StepsRecord getBaseStepsRecord(Instant time, ZoneOffset zoneOffset, int value) {
        return new StepsRecord.Builder(
                        new Metadata.Builder().build(),
                        time,
                        time.plus(1, ChronoUnit.SECONDS),
                        value)
                .setStartZoneOffset(zoneOffset)
                .setEndZoneOffset(zoneOffset)
                .build();
    }

    @Test
    public void testReadStepsRecordUsingFilters_dataFilter_correct() throws InterruptedException {
        Context context = ApplicationProvider.getApplicationContext();
        List<StepsRecord> oldStepsRecords =
                TestUtils.readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(StepsRecord.class)
                                .addDataOrigins(
                                        new DataOrigin.Builder()
                                                .setPackageName(context.getPackageName())
                                                .build())
                                .build());
        StepsRecord testRecord = getCompleteStepsRecord();
        TestUtils.insertRecords(Collections.singletonList(testRecord));
        List<StepsRecord> newStepsRecords =
                TestUtils.readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(StepsRecord.class)
                                .addDataOrigins(
                                        new DataOrigin.Builder()
                                                .setPackageName(context.getPackageName())
                                                .build())
                                .build());
        assertThat(newStepsRecords.size() - oldStepsRecords.size()).isEqualTo(1);
        StepsRecord newRecord = newStepsRecords.get(newStepsRecords.size() - 1);
        assertThat(newRecord.equals(testRecord)).isTrue();
    }

    @Test
    public void testReadStepsRecordUsingFilters_dataFilter_incorrect() throws InterruptedException {
        TestUtils.insertRecords(Collections.singletonList(getCompleteStepsRecord()));
        ReadRecordsRequestUsingFilters<StepsRecord> requestUsingFilters =
                new ReadRecordsRequestUsingFilters.Builder<>(StepsRecord.class)
                        .addDataOrigins(new DataOrigin.Builder().setPackageName("abc").build())
                        .setAscending(false)
                        .build();
        assertThat(requestUsingFilters.getDataOrigins()).isNotNull();
        assertThat(requestUsingFilters.isAscending()).isFalse();
        List<StepsRecord> newStepsRecords = TestUtils.readRecords(requestUsingFilters);
        assertThat(newStepsRecords.size()).isEqualTo(0);
    }

    @Test
    public void testReadStepsRecordUsingFilters_withPageSize() throws InterruptedException {
        List<Record> recordList =
                Arrays.asList(getStepsRecord_minusDays(1), getStepsRecord_minusDays(2));
        TestUtils.insertRecords(recordList);
        ReadRecordsResponse<StepsRecord> response =
                readRecordsWithPagination(
                        new ReadRecordsRequestUsingFilters.Builder<>(StepsRecord.class)
                                .setPageSize(1)
                                .build());
        assertThat(response.getRecords()).hasSize(1);
    }

    @Test
    public void testReadStepsRecordUsingFilters_timeFilterLocal() throws InterruptedException {
        LocalDateTime recordTime = LocalDateTime.now(ZoneOffset.MIN);
        LocalTimeRangeFilter filter =
                new LocalTimeRangeFilter.Builder()
                        .setStartTime(recordTime.minus(1, ChronoUnit.SECONDS))
                        .setEndTime(recordTime.plus(1, ChronoUnit.SECONDS))
                        .build();
        StepsRecord testRecord =
                getBaseStepsRecord(recordTime.toInstant(ZoneOffset.MIN), ZoneOffset.MIN, 50);
        TestUtils.insertRecords(Collections.singletonList(testRecord));
        List<StepsRecord> newStepsRecords =
                TestUtils.readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(StepsRecord.class)
                                .setTimeRangeFilter(filter)
                                .build());
        assertThat(newStepsRecords.size()).isEqualTo(1);
        StepsRecord stepsRecord = newStepsRecords.get(newStepsRecords.size() - 1);
        assertThat(stepsRecord.getCount()).isEqualTo(50);
        assertThat(stepsRecord.getStartZoneOffset()).isEqualTo(ZoneOffset.MIN);
        assertThat(stepsRecord.getEndZoneOffset()).isEqualTo(ZoneOffset.MIN);

        TimeInstantRangeFilter timeInstantRangeFilter =
                new TimeInstantRangeFilter.Builder()
                        .setStartTime(
                                recordTime.minus(1, ChronoUnit.SECONDS).toInstant(ZoneOffset.MIN))
                        .setEndTime(
                                recordTime.plus(1, ChronoUnit.SECONDS).toInstant(ZoneOffset.MIN))
                        .build();

        newStepsRecords =
                TestUtils.readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(StepsRecord.class)
                                .setTimeRangeFilter(timeInstantRangeFilter)
                                .build());
        stepsRecord = newStepsRecords.get(newStepsRecords.size() - 1);
        assertThat(newStepsRecords.size()).isEqualTo(1);
        assertThat(stepsRecord.getCount()).isEqualTo(50);
        assertThat(stepsRecord.getStartZoneOffset()).isEqualTo(ZoneOffset.MIN);
        assertThat(stepsRecord.getEndZoneOffset()).isEqualTo(ZoneOffset.MIN);
    }

    @Test
    public void testReadStepsRecordUsingFilters_withPageToken() throws InterruptedException {
        List<Record> recordList =
                Arrays.asList(
                        getStepsRecord_minusDays(1),
                        getStepsRecord_minusDays(2),
                        getStepsRecord_minusDays(3),
                        getStepsRecord_minusDays(4));
        TestUtils.insertRecords(recordList);
        ReadRecordsRequestUsingFilters<StepsRecord> requestUsingFilters =
                new ReadRecordsRequestUsingFilters.Builder<>(StepsRecord.class)
                        .setPageSize(1)
                        .setAscending(true)
                        .build();
        assertThat(requestUsingFilters.isAscending()).isTrue();
        assertThat(requestUsingFilters.getPageSize()).isEqualTo(1);
        assertThat(requestUsingFilters.getTimeRangeFilter()).isNull();
        ReadRecordsResponse<StepsRecord> oldStepsRecord =
                readRecordsWithPagination(requestUsingFilters);
        assertThat(oldStepsRecord.getRecords()).hasSize(1);
        ReadRecordsRequestUsingFilters<StepsRecord> requestUsingFiltersNew =
                new ReadRecordsRequestUsingFilters.Builder<>(StepsRecord.class)
                        .setPageSize(1)
                        .setPageToken(oldStepsRecord.getNextPageToken())
                        .build();
        assertThat(requestUsingFiltersNew.getPageSize()).isEqualTo(1);
        assertThat(requestUsingFiltersNew.getPageToken())
                .isEqualTo(oldStepsRecord.getNextPageToken());
        assertThat(requestUsingFiltersNew.getTimeRangeFilter()).isNull();
        ReadRecordsResponse<StepsRecord> newStepsRecords =
                readRecordsWithPagination(requestUsingFiltersNew);
        assertThat(newStepsRecords.getRecords()).hasSize(1);
    }

    @Test
    public void testReadStepsRecordUsingFilters_withPageTokenReverse() throws InterruptedException {
        List<Record> recordList =
                Arrays.asList(
                        getStepsRecord_minusDays(1),
                        getStepsRecord_minusDays(2),
                        getStepsRecord_minusDays(3),
                        getStepsRecord_minusDays(4));
        TestUtils.insertRecords(recordList);
        ReadRecordsResponse<StepsRecord> page1 =
                readRecordsWithPagination(
                        new ReadRecordsRequestUsingFilters.Builder<>(StepsRecord.class)
                                .setPageSize(1)
                                .setAscending(false)
                                .build());
        assertThat(page1.getRecords()).hasSize(1);
        ReadRecordsResponse<StepsRecord> page2 =
                readRecordsWithPagination(
                        new ReadRecordsRequestUsingFilters.Builder<>(StepsRecord.class)
                                .setPageSize(1)
                                .setPageToken(page1.getNextPageToken())
                                .build());
        assertThat(page2.getRecords()).hasSize(1);
        assertThat(page2.getNextPageToken()).isNotEqualTo(page1.getNextPageToken());
    }

    @Test
    public void testStepsRecordUsingFilters_nextPageTokenEnd() throws InterruptedException {
        List<Record> recordList =
                Arrays.asList(DataFactory.getStepsRecord(), DataFactory.getStepsRecord());
        TestUtils.insertRecords(recordList);
        ReadRecordsResponse<StepsRecord> prevPage =
                readRecordsWithPagination(
                        new ReadRecordsRequestUsingFilters.Builder<>(StepsRecord.class).build());
        ReadRecordsResponse<StepsRecord> nextPage;
        while (prevPage.getNextPageToken() != -1) {
            nextPage =
                    readRecordsWithPagination(
                            new ReadRecordsRequestUsingFilters.Builder<>(StepsRecord.class)
                                    .setPageToken(prevPage.getNextPageToken())
                                    .build());
            prevPage = nextPage;
        }
        assertThat(prevPage.getNextPageToken()).isEqualTo(-1);
    }

    @Test
    public void testReadStepsRecordUsingFilters_withPageToken_NewOrder()
            throws InterruptedException {
        List<Record> recordList =
                Arrays.asList(
                        getStepsRecord_minusDays(1),
                        getStepsRecord_minusDays(2),
                        getStepsRecord_minusDays(3),
                        getStepsRecord_minusDays(4));
        TestUtils.insertRecords(recordList);
        ReadRecordsResponse<StepsRecord> oldStepsRecord =
                readRecordsWithPagination(
                        new ReadRecordsRequestUsingFilters.Builder<>(StepsRecord.class)
                                .setPageSize(1)
                                .setAscending(false)
                                .build());
        assertThat(oldStepsRecord.getRecords()).hasSize(1);
        try {
            ReadRecordsRequestUsingFilters<StepsRecord> requestUsingFilters =
                    new ReadRecordsRequestUsingFilters.Builder<>(StepsRecord.class)
                            .setPageSize(1)
                            .setPageToken(oldStepsRecord.getNextPageToken())
                            .setAscending(true)
                            .build();
            readRecordsWithPagination(requestUsingFilters);
            Assert.fail(
                    "IllegalStateException  expected when both page token and page order is set");
        } catch (Exception exception) {
            assertThat(exception).isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    public void testReadStepsRecordUsingFilters_withPageToken_SameOrder()
            throws InterruptedException {
        List<Record> recordList =
                Arrays.asList(
                        getStepsRecord_minusDays(1),
                        getStepsRecord_minusDays(2),
                        getStepsRecord_minusDays(3),
                        getStepsRecord_minusDays(4));
        TestUtils.insertRecords(recordList);
        ReadRecordsResponse<StepsRecord> oldStepsRecord =
                readRecordsWithPagination(
                        new ReadRecordsRequestUsingFilters.Builder<>(StepsRecord.class)
                                .setPageSize(1)
                                .setAscending(false)
                                .build());
        assertThat(oldStepsRecord.getRecords()).hasSize(1);
        try {
            ReadRecordsRequestUsingFilters<StepsRecord> requestUsingFilters =
                    new ReadRecordsRequestUsingFilters.Builder<>(StepsRecord.class)
                            .setPageSize(1)
                            .setPageToken(oldStepsRecord.getNextPageToken())
                            .setAscending(false)
                            .build();
            readRecordsWithPagination(requestUsingFilters);
            Assert.fail(
                    "IllegalStateException  expected when both page token and page order is set");
        } catch (Exception exception) {
            assertThat(exception).isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    public void testDeleteStepsRecord_no_filters() throws InterruptedException {
        String id = TestUtils.insertRecordAndGetId(getCompleteStepsRecord());
        TestUtils.verifyDeleteRecords(new DeleteUsingFiltersRequest.Builder().build());
        TestUtils.assertRecordNotFound(id, StepsRecord.class);
    }

    @Test
    public void testDeleteStepsRecord_time_filters() throws InterruptedException {
        TimeInstantRangeFilter timeRangeFilter =
                new TimeInstantRangeFilter.Builder()
                        .setStartTime(NOW)
                        .setEndTime(NOW.plusMillis(1000))
                        .build();
        String id = TestUtils.insertRecordAndGetId(getCompleteStepsRecord());
        TestUtils.verifyDeleteRecords(
                new DeleteUsingFiltersRequest.Builder()
                        .addRecordType(StepsRecord.class)
                        .setTimeRangeFilter(timeRangeFilter)
                        .build());
        TestUtils.assertRecordNotFound(id, StepsRecord.class);
    }

    @Test
    public void testReadStepsRecordUsingFiltersLocal_withPageSize() throws InterruptedException {
        LocalDateTime recordTime = LocalDateTime.now(ZoneOffset.MIN).minus(10, ChronoUnit.SECONDS);
        LocalTimeRangeFilter filter =
                new LocalTimeRangeFilter.Builder()
                        .setStartTime(recordTime.minus(2, ChronoUnit.SECONDS))
                        .setEndTime(recordTime.plus(2, ChronoUnit.SECONDS))
                        .build();
        List<Record> testRecord =
                List.of(
                        getBaseStepsRecord(
                                recordTime.plus(1, ChronoUnit.SECONDS).toInstant(ZoneOffset.MIN),
                                ZoneOffset.MIN,
                                20),
                        getBaseStepsRecord(
                                recordTime.toInstant(ZoneOffset.MIN), ZoneOffset.MIN, 50),
                        getBaseStepsRecord(
                                recordTime.minus(1, ChronoUnit.SECONDS).toInstant(ZoneOffset.MIN),
                                ZoneOffset.MIN,
                                70));
        TestUtils.insertRecords(testRecord);
        ReadRecordsResponse<StepsRecord> newStepsRecords =
                readRecordsWithPagination(
                        new ReadRecordsRequestUsingFilters.Builder<>(StepsRecord.class)
                                .setTimeRangeFilter(filter)
                                .setPageSize(1)
                                .build());
        assertThat(newStepsRecords.getRecords()).hasSize(1);
        assertThat(newStepsRecords.getRecords().get(0).getCount()).isEqualTo(70);
        newStepsRecords =
                readRecordsWithPagination(
                        new ReadRecordsRequestUsingFilters.Builder<>(StepsRecord.class)
                                .setTimeRangeFilter(filter)
                                .setPageSize(1)
                                .setPageToken(newStepsRecords.getNextPageToken())
                                .build());
        assertThat(newStepsRecords.getRecords()).hasSize(1);
        assertThat(newStepsRecords.getRecords().get(0).getCount()).isEqualTo(50);
        newStepsRecords =
                readRecordsWithPagination(
                        new ReadRecordsRequestUsingFilters.Builder<>(StepsRecord.class)
                                .setTimeRangeFilter(filter)
                                .setPageSize(1)
                                .setPageToken(newStepsRecords.getNextPageToken())
                                .build());
        assertThat(newStepsRecords.getRecords()).hasSize(1);
        assertThat(newStepsRecords.getRecords().get(0).getCount()).isEqualTo(20);
        assertThat(newStepsRecords.getNextPageToken()).isEqualTo(-1);
    }

    @Test
    public void testDeleteStepsRecord_time_filters_local() throws InterruptedException {
        LocalDateTime recordTime = LocalDateTime.now(ZoneOffset.MIN);
        LocalTimeRangeFilter timeRangeFilter =
                new LocalTimeRangeFilter.Builder()
                        .setStartTime(recordTime.minus(1, ChronoUnit.SECONDS))
                        .setEndTime(recordTime.plus(2, ChronoUnit.SECONDS))
                        .build();
        String id1 =
                TestUtils.insertRecordAndGetId(
                        getBaseStepsRecord(
                                recordTime.toInstant(ZoneOffset.MIN), ZoneOffset.MIN, 50));
        String id2 =
                TestUtils.insertRecordAndGetId(
                        getBaseStepsRecord(
                                recordTime.toInstant(ZoneOffset.MAX), ZoneOffset.MAX, 50));
        TestUtils.assertRecordFound(id1, StepsRecord.class);
        TestUtils.assertRecordFound(id2, StepsRecord.class);
        TestUtils.verifyDeleteRecords(
                new DeleteUsingFiltersRequest.Builder()
                        .addRecordType(StepsRecord.class)
                        .setTimeRangeFilter(timeRangeFilter)
                        .build());
        TestUtils.assertRecordNotFound(id1, StepsRecord.class);
        TestUtils.assertRecordNotFound(id2, StepsRecord.class);
    }

    @Test
    public void testDeleteStepsRecord_recordId_filters() throws InterruptedException {
        List<Record> records = List.of(getBaseStepsRecord(), getCompleteStepsRecord());
        TestUtils.insertRecords(records);

        for (Record record : records) {
            TestUtils.verifyDeleteRecords(
                    new DeleteUsingFiltersRequest.Builder()
                            .addRecordType(record.getClass())
                            .build());
            TestUtils.assertRecordNotFound(record.getMetadata().getId(), record.getClass());
        }
    }

    @Test
    public void testDeleteStepsRecord_dataOrigin_filters() throws InterruptedException {
        Context context = ApplicationProvider.getApplicationContext();
        String id = TestUtils.insertRecordAndGetId(getCompleteStepsRecord());
        TestUtils.verifyDeleteRecords(
                new DeleteUsingFiltersRequest.Builder()
                        .addDataOrigin(
                                new DataOrigin.Builder()
                                        .setPackageName(context.getPackageName())
                                        .build())
                        .build());
        TestUtils.assertRecordNotFound(id, StepsRecord.class);
    }

    @Test
    public void testDeleteStepsRecord_dataOrigin_filter_incorrect() throws InterruptedException {
        String id = TestUtils.insertRecordAndGetId(getCompleteStepsRecord());
        TestUtils.verifyDeleteRecords(
                new DeleteUsingFiltersRequest.Builder()
                        .addDataOrigin(new DataOrigin.Builder().setPackageName("abc").build())
                        .build());
        TestUtils.assertRecordFound(id, StepsRecord.class);
    }

    @Test
    public void testDeleteStepsRecord_usingIds() throws InterruptedException {
        List<Record> records = List.of(getBaseStepsRecord(), getCompleteStepsRecord());
        List<Record> insertedRecord = TestUtils.insertRecords(records);
        List<RecordIdFilter> recordIds = new ArrayList<>(records.size());
        for (Record record : insertedRecord) {
            recordIds.add(RecordIdFilter.fromId(record.getClass(), record.getMetadata().getId()));
        }
        for (RecordIdFilter recordIdFilter : recordIds) {
            assertThat(recordIdFilter.getClientRecordId()).isNull();
            assertThat(recordIdFilter.getId()).isNotNull();
            assertThat(recordIdFilter.getRecordType()).isEqualTo(StepsRecord.class);
        }

        TestUtils.verifyDeleteRecords(recordIds);
        for (Record record : records) {
            TestUtils.assertRecordNotFound(record.getMetadata().getId(), record.getClass());
        }
    }

    @Test
    public void testDeleteStepsRecords_usingInvalidId() throws InterruptedException {
        List<RecordIdFilter> recordIds =
                Collections.singletonList(RecordIdFilter.fromId(StepsRecord.class, "foo"));
        HealthConnectException e =
                assertThrows(
                        HealthConnectException.class,
                        () -> TestUtils.verifyDeleteRecords(recordIds));
        assertThat(e.getErrorCode()).isEqualTo(ERROR_INVALID_ARGUMENT);
    }

    @Test
    public void testDeleteStepsRecord_usingUnknownId() throws InterruptedException {
        List<RecordIdFilter> recordIds =
                Collections.singletonList(
                        RecordIdFilter.fromId(StepsRecord.class, UUID.randomUUID().toString()));
        TestUtils.verifyDeleteRecords(recordIds);
    }

    @Test
    public void testDeleteStepsRecord_usingInvalidClientIds() throws InterruptedException {
        List<Record> records = List.of(getBaseStepsRecord(), getCompleteStepsRecord());
        List<Record> insertedRecord = TestUtils.insertRecords(records);
        List<RecordIdFilter> recordIds = new ArrayList<>(records.size());
        for (Record record : insertedRecord) {
            recordIds.add(
                    RecordIdFilter.fromClientRecordId(
                            record.getClass(), record.getMetadata().getId()));
        }
        for (RecordIdFilter recordIdFilter : recordIds) {
            assertThat(recordIdFilter.getClientRecordId()).isNotNull();
            assertThat(recordIdFilter.getId()).isNull();
            assertThat(recordIdFilter.getRecordType()).isEqualTo(StepsRecord.class);
        }

        TestUtils.verifyDeleteRecords(recordIds);
        for (Record record : records) {
            TestUtils.assertRecordFound(record.getMetadata().getId(), record.getClass());
        }
    }

    @Test
    public void testDeleteStepsRecord_time_range() throws InterruptedException {
        TimeInstantRangeFilter timeRangeFilter =
                new TimeInstantRangeFilter.Builder()
                        .setStartTime(NOW)
                        .setEndTime(NOW.plusMillis(1000))
                        .build();
        String id = TestUtils.insertRecordAndGetId(getCompleteStepsRecord());
        TestUtils.verifyDeleteRecords(StepsRecord.class, timeRangeFilter);
        TestUtils.assertRecordNotFound(id, StepsRecord.class);
    }

    @Test
    public void testZoneOffsets() {
        final ZoneOffset defaultZoneOffset =
                ZoneOffset.systemDefault().getRules().getOffset(Instant.now());
        final ZoneOffset startZoneOffset = ZoneOffset.UTC;
        final ZoneOffset endZoneOffset = ZoneOffset.MAX;
        StepsRecord.Builder builder =
                new StepsRecord.Builder(
                        new Metadata.Builder().build(),
                        Instant.now(),
                        Instant.now().plusMillis(1000),
                        10);

        assertThat(builder.setStartZoneOffset(startZoneOffset).build().getStartZoneOffset())
                .isEqualTo(startZoneOffset);
        assertThat(builder.setEndZoneOffset(endZoneOffset).build().getEndZoneOffset())
                .isEqualTo(endZoneOffset);
        assertThat(builder.clearStartZoneOffset().build().getStartZoneOffset())
                .isEqualTo(defaultZoneOffset);
        assertThat(builder.clearEndZoneOffset().build().getEndZoneOffset())
                .isEqualTo(defaultZoneOffset);
    }

    private void readStepsRecordUsingClientId(List<Record> insertedRecord)
            throws InterruptedException {
        ReadRecordsRequestUsingIds.Builder<StepsRecord> request =
                new ReadRecordsRequestUsingIds.Builder<>(StepsRecord.class);
        for (Record record : insertedRecord) {
            request.addClientRecordId(record.getMetadata().getClientRecordId());
        }
        ReadRecordsRequestUsingIds<StepsRecord> readRequest = request.build();
        assertThat(readRequest.getRecordType()).isNotNull();
        assertThat(readRequest.getRecordType()).isEqualTo(StepsRecord.class);
        List<StepsRecord> result = TestUtils.readRecords(readRequest);
        assertThat(result.size()).isEqualTo(insertedRecord.size());
        assertThat(result).containsExactlyElementsIn(insertedRecord);
    }

    @Test
    public void testInsertWithClientVersion() throws InterruptedException {
        List<Record> records = List.of(getStepsRecordWithClientVersion(10, 1, "testId"));
        final String id = TestUtils.insertRecords(records).get(0).getMetadata().getId();
        ReadRecordsRequestUsingIds<StepsRecord> request =
                new ReadRecordsRequestUsingIds.Builder<>(StepsRecord.class)
                        .addClientRecordId("testId")
                        .build();
        StepsRecord stepsRecord = TestUtils.readRecords(request).get(0);
        assertThat(stepsRecord.getCount()).isEqualTo(10);
        records = List.of(getStepsRecordWithClientVersion(20, 2, "testId"));
        TestUtils.insertRecords(records);

        stepsRecord = TestUtils.readRecords(request).get(0);
        assertThat(stepsRecord.getMetadata().getId()).isEqualTo(id);
        assertThat(stepsRecord.getCount()).isEqualTo(20);
        records = List.of(getStepsRecordWithClientVersion(30, 1, "testId"));
        TestUtils.insertRecords(records);
        stepsRecord = TestUtils.readRecords(request).get(0);
        assertThat(stepsRecord.getMetadata().getId()).isEqualTo(id);
        assertThat(stepsRecord.getCount()).isEqualTo(20);
    }

    @Test
    public void testAggregation_recordStartsBeforeAggWindow_returnsRescaledStepsCountInResult()
            throws Exception {
        TestUtils.setupAggregation(PACKAGE_NAME, HealthDataCategory.ACTIVITY);
        Instant start = Instant.now().minus(1, ChronoUnit.DAYS);
        List<Record> record =
                Arrays.asList(
                        new StepsRecord.Builder(
                                        new Metadata.Builder().build(),
                                        start,
                                        start.plus(1, HOURS),
                                        600)
                                .build());
        AggregateRecordsRequest<Long> request =
                new AggregateRecordsRequest.Builder<Long>(
                                new TimeInstantRangeFilter.Builder()
                                        .setStartTime(start.plus(10, ChronoUnit.MINUTES))
                                        .setEndTime(start.plus(1, ChronoUnit.DAYS))
                                        .build())
                        .addAggregationType(STEPS_COUNT_TOTAL)
                        .build();

        AggregateRecordsResponse<Long> response = TestUtils.getAggregateResponse(request, record);
        assertThat(response.get(STEPS_COUNT_TOTAL)).isEqualTo(500);
        assertThat(response.getZoneOffset(STEPS_COUNT_TOTAL))
                .isEqualTo(ZoneOffset.systemDefault().getRules().getOffset(Instant.now()));
    }

    @Test
    public void testStepsCountAggregation_groupByDurationWithInstantFilter() throws Exception {
        TestUtils.setupAggregation(PACKAGE_NAME, HealthDataCategory.ACTIVITY);
        Instant end = Instant.now();
        Instant start = end.minus(5, ChronoUnit.DAYS);
        List<Record> records =
                Arrays.asList(
                        getStepsRecord(1000, 1, 1),
                        getStepsRecord(1000, 2, 1),
                        getStepsRecord(1000, 3, 1),
                        getStepsRecord(1000, 4, 1),
                        getStepsRecord(1000, 5, 1));
        TestUtils.insertRecords(records);
        List<AggregateRecordsGroupedByDurationResponse<Long>> responses =
                TestUtils.getAggregateResponseGroupByDuration(
                        new AggregateRecordsRequest.Builder<Long>(
                                        new TimeInstantRangeFilter.Builder()
                                                .setStartTime(start)
                                                .setEndTime(end)
                                                .build())
                                .addAggregationType(STEPS_COUNT_TOTAL)
                                .build(),
                        Duration.ofDays(1));
        assertThat(responses.size()).isEqualTo(5);
    }

    @Test
    public void testStepsCountAggregation_groupByDuration() throws Exception {
        TestUtils.setupAggregation(PACKAGE_NAME, HealthDataCategory.ACTIVITY);
        Instant end = Instant.now();
        Instant start = end.minus(3, ChronoUnit.DAYS);
        insertStepsRecordWithDelay(1000, 3);

        List<AggregateRecordsGroupedByDurationResponse<Long>> responses =
                TestUtils.getAggregateResponseGroupByDuration(
                        new AggregateRecordsRequest.Builder<Long>(
                                        new TimeInstantRangeFilter.Builder()
                                                .setStartTime(start)
                                                .setEndTime(end)
                                                .build())
                                .addAggregationType(STEPS_COUNT_TOTAL)
                                .build(),
                        Duration.ofDays(1));
        assertThat(responses.size()).isEqualTo(3);
        for (AggregateRecordsGroupedByDurationResponse<Long> response : responses) {
            assertThat(response.get(STEPS_COUNT_TOTAL)).isNotNull();
            assertThat(response.get(STEPS_COUNT_TOTAL)).isEqualTo(1000);
            assertThat(response.getZoneOffset(STEPS_COUNT_TOTAL))
                    .isEqualTo(ZoneOffset.systemDefault().getRules().getOffset(Instant.now()));
        }
    }

    @Test
    public void testAggregation_insertForEveryHour_returnsAggregateForHourAndHalfHours()
            throws Exception {
        TestUtils.setupAggregation(PACKAGE_NAME, HealthDataCategory.ACTIVITY);
        Instant start = Instant.now().minus(1, ChronoUnit.DAYS);
        Instant end = Instant.now();
        for (int i = 0; i < 10; i++) {
            Instant st = start.plus(i, HOURS);
            List<Record> records =
                    Arrays.asList(
                            new StepsRecord.Builder(
                                            new Metadata.Builder().build(),
                                            st,
                                            st.plus(1, HOURS),
                                            1000)
                                    .build());
            TestUtils.insertRecords(records);
            Thread.sleep(100);
        }

        start = start.plus(30, ChronoUnit.MINUTES);
        List<AggregateRecordsGroupedByDurationResponse<Long>> responses =
                TestUtils.getAggregateResponseGroupByDuration(
                        new AggregateRecordsRequest.Builder<Long>(
                                        new TimeInstantRangeFilter.Builder()
                                                .setStartTime(start)
                                                .setEndTime(end)
                                                .build())
                                .addAggregationType(STEPS_COUNT_TOTAL)
                                .build(),
                        Duration.ofHours(1));
        assertThat(responses.size()).isEqualTo(24);
        for (int i = 0; i < responses.size(); i++) {
            AggregateRecordsGroupedByDurationResponse<Long> response = responses.get(i);
            if (i > 9) {
                assertThat(response.get(STEPS_COUNT_TOTAL)).isNull();
            } else if (i == 9) {
                assertThat(response.get(STEPS_COUNT_TOTAL)).isEqualTo(500);
            } else {
                assertThat(response.get(STEPS_COUNT_TOTAL)).isEqualTo(1000);
            }
        }
    }

    @Test
    public void testAggregation_groupByDurationInstant_halfSizeGroupResultIsCorrect()
            throws Exception {
        TestUtils.setupAggregation(PACKAGE_NAME, HealthDataCategory.ACTIVITY);
        Instant end = Instant.now();
        TestUtils.insertRecords(List.of(getStepsRecord(end, 100, 1, 2)));

        List<AggregateRecordsGroupedByDurationResponse<Long>> responses =
                TestUtils.getAggregateResponseGroupByDuration(
                        new AggregateRecordsRequest.Builder<Long>(
                                        new TimeInstantRangeFilter.Builder()
                                                .setStartTime(end.minus(24, HOURS))
                                                .setEndTime(
                                                        end.minus(22, HOURS)
                                                                .minus(30, ChronoUnit.MINUTES))
                                                .build())
                                .addAggregationType(STEPS_COUNT_TOTAL)
                                .build(),
                        Duration.ofHours(1));
        assertThat(responses.size()).isEqualTo(2);
        assertThat(responses.get(0).get(STEPS_COUNT_TOTAL)).isEqualTo(50);
        assertThat(responses.get(1).get(STEPS_COUNT_TOTAL)).isEqualTo(25);
    }

    @Test
    public void testAggregation_StepsCountTotal_withDuplicateEntry() throws Exception {
        TestUtils.setupAggregation(PACKAGE_NAME, HealthDataCategory.ACTIVITY);
        List<Record> records =
                Arrays.asList(getStepsRecord(1000, 1, 1), getStepsRecord(1000, 2, 1));
        AggregateRecordsResponse<Long> oldResponse =
                TestUtils.getAggregateResponse(
                        new AggregateRecordsRequest.Builder<Long>(
                                        new TimeInstantRangeFilter.Builder()
                                                .setStartTime(Instant.ofEpochMilli(0))
                                                .setEndTime(Instant.now().plus(1, ChronoUnit.DAYS))
                                                .build())
                                .addAggregationType(STEPS_COUNT_TOTAL)
                                .build(),
                        records);
        List<Record> recordNew =
                Arrays.asList(getStepsRecord(1000, 3, 1), getStepsRecord(1000, 3, 1));
        AggregateRecordsResponse<Long> newResponse =
                TestUtils.getAggregateResponse(
                        new AggregateRecordsRequest.Builder<Long>(
                                        new TimeInstantRangeFilter.Builder()
                                                .setStartTime(Instant.ofEpochMilli(0))
                                                .setEndTime(Instant.now().plus(1, ChronoUnit.DAYS))
                                                .build())
                                .addAggregationType(STEPS_COUNT_TOTAL)
                                .build(),
                        recordNew);
        assertThat(newResponse.get(STEPS_COUNT_TOTAL)).isNotNull();
        assertThat(newResponse.get(STEPS_COUNT_TOTAL))
                .isEqualTo(oldResponse.get(STEPS_COUNT_TOTAL) + 1000);
        Set<DataOrigin> newDataOrigin = newResponse.getDataOrigins(STEPS_COUNT_TOTAL);
        for (DataOrigin itr : newDataOrigin) {
            assertThat(itr.getPackageName()).isEqualTo(PACKAGE_NAME);
        }
        Set<DataOrigin> oldDataOrigin = oldResponse.getDataOrigins(STEPS_COUNT_TOTAL);
        for (DataOrigin itr : oldDataOrigin) {
            assertThat(itr.getPackageName()).isEqualTo(PACKAGE_NAME);
        }
    }

    @Test
    public void testUpdateRecords_validInput_dataBaseUpdatedSuccessfully()
            throws InterruptedException {

        List<Record> insertedRecords =
                TestUtils.insertRecords(
                        Arrays.asList(getCompleteStepsRecord(), getCompleteStepsRecord()));

        // read inserted records and verify that the data is same as inserted.
        assertStepsRecordUsingIds(insertedRecords);

        // Generate a new set of records that will be used to perform the update operation.
        List<Record> updateRecords =
                Arrays.asList(getCompleteStepsRecord(), getCompleteStepsRecord());

        // Modify the uid of the updateRecords to the uuid that was present in the insert records.
        for (int itr = 0; itr < updateRecords.size(); itr++) {
            updateRecords.set(
                    itr,
                    getUpdatedStepsRecord(
                            updateRecords.get(itr),
                            insertedRecords.get(itr).getMetadata().getId(),
                            insertedRecords.get(itr).getMetadata().getClientRecordId()));
        }

        TestUtils.updateRecords(updateRecords);

        // assert the inserted data has been modified by reading the data.
        assertStepsRecordUsingIds(updateRecords);
    }

    @Test
    public void testUpdateRecords_invalidInputRecords_noChangeInDataBase()
            throws InterruptedException {
        List<Record> insertedRecords =
                TestUtils.insertRecords(
                        Arrays.asList(getCompleteStepsRecord(), getCompleteStepsRecord()));

        // read inserted records and verify that the data is same as inserted.
        assertStepsRecordUsingIds(insertedRecords);

        // Generate a second set of records that will be used to perform the update operation.
        List<Record> updateRecords =
                Arrays.asList(getCompleteStepsRecord(), getCompleteStepsRecord());

        // Modify the Uid of the updateRecords to the UUID that was present in the insert records,
        // leaving out alternate records so that they have a new UUID which is not present in the
        // dataBase.
        for (int itr = 0; itr < updateRecords.size(); itr++) {
            updateRecords.set(
                    itr,
                    getUpdatedStepsRecord(
                            updateRecords.get(itr),
                            itr % 2 == 0
                                    ? insertedRecords.get(itr).getMetadata().getId()
                                    : UUID.randomUUID().toString(),
                            itr % 2 == 0
                                    ? insertedRecords.get(itr).getMetadata().getId()
                                    : UUID.randomUUID().toString()));
        }

        try {
            TestUtils.updateRecords(updateRecords);
            Assert.fail("Expected to fail due to invalid records ids.");
        } catch (HealthConnectException exception) {
            assertThat(exception.getErrorCode()).isEqualTo(ERROR_INVALID_ARGUMENT);
        }

        // assert the inserted data has not been modified by reading the data.
        assertStepsRecordUsingIds(insertedRecords);
    }

    @Test
    public void testUpdateRecords_recordWithInvalidPackageName_noChangeInDataBase()
            throws InterruptedException {
        List<Record> insertedRecords =
                TestUtils.insertRecords(
                        Arrays.asList(getCompleteStepsRecord(), getCompleteStepsRecord()));

        // read inserted records and verify that the data is same as inserted.
        assertStepsRecordUsingIds(insertedRecords);

        // Generate a second set of records that will be used to perform the update operation.
        List<Record> updateRecords =
                Arrays.asList(getCompleteStepsRecord(), getCompleteStepsRecord());

        // Modify the Uuid of the updateRecords to the uuid that was present in the insert records.
        for (int itr = 0; itr < updateRecords.size(); itr++) {
            updateRecords.set(
                    itr,
                    getUpdatedStepsRecord(
                            updateRecords.get(itr),
                            insertedRecords.get(itr).getMetadata().getId(),
                            insertedRecords.get(itr).getMetadata().getClientRecordId()));
            //             adding an entry with invalid packageName.
            updateRecords.set(itr, getCompleteStepsRecord());
        }

        try {
            TestUtils.updateRecords(updateRecords);
            Assert.fail("Expected to fail due to invalid package.");
        } catch (Exception exception) {
            // verify that the testcase failed due to invalid argument exception.
            assertThat(exception).isNotNull();
        }

        // assert the inserted data has not been modified by reading the data.
        assertStepsRecordUsingIds(insertedRecords);
    }

    @Test
    public void testInsertAndDeleteRecord_changelogs() throws InterruptedException {
        Context context = ApplicationProvider.getApplicationContext();
        ChangeLogTokenResponse tokenResponse =
                TestUtils.getChangeLogToken(
                        new ChangeLogTokenRequest.Builder()
                                .addDataOriginFilter(
                                        new DataOrigin.Builder()
                                                .setPackageName(context.getPackageName())
                                                .build())
                                .addRecordType(StepsRecord.class)
                                .build());
        ChangeLogsRequest changeLogsRequest =
                new ChangeLogsRequest.Builder(tokenResponse.getToken()).build();
        ChangeLogsResponse response = TestUtils.getChangeLogs(changeLogsRequest);
        assertThat(response.getUpsertedRecords().size()).isEqualTo(0);
        assertThat(response.getDeletedLogs().size()).isEqualTo(0);

        List<Record> testRecord = Collections.singletonList(getCompleteStepsRecord());
        TestUtils.insertRecords(testRecord);
        response = TestUtils.getChangeLogs(changeLogsRequest);
        assertThat(response.getUpsertedRecords().size()).isEqualTo(1);
        assertThat(
                        response.getUpsertedRecords().stream()
                                .map(Record::getMetadata)
                                .map(Metadata::getId)
                                .toList())
                .containsExactlyElementsIn(
                        testRecord.stream().map(Record::getMetadata).map(Metadata::getId).toList());
        assertThat(response.getDeletedLogs().size()).isEqualTo(0);

        TestUtils.verifyDeleteRecords(
                new DeleteUsingFiltersRequest.Builder().addRecordType(StepsRecord.class).build());
        response = TestUtils.getChangeLogs(changeLogsRequest);
        assertThat(response.getDeletedLogs()).isEmpty();
    }

    private void insertStepsRecordWithDelay(long delayInMillis, int times)
            throws InterruptedException {
        for (int i = 0; i < times; i++) {
            List<Record> records =
                    Arrays.asList(
                            getStepsRecord(1000, 1, 1),
                            getStepsRecord(1000, 2, 1),
                            getStepsRecord(1000, 3, 1));
            TestUtils.insertRecords(records);
            Thread.sleep(delayInMillis);
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCreateStepsRecord_invalidValue() {
        new StepsRecord.Builder(
                        new Metadata.Builder().build(),
                        Instant.now(),
                        Instant.now().plusMillis(1000),
                        1000001)
                .build();
    }

    @Test
    public void testAggregatePeriod_withLocalDateTime() throws Exception {
        testAggregationLocalTimeOffset(ZoneOffset.ofHours(-4));
        testAggregationLocalTimeOffset(ZoneOffset.MIN);
        testAggregationLocalTimeOffset(ZoneOffset.MAX);
        testAggregationLocalTimeOffset(ZoneOffset.UTC);
        testAggregationLocalTimeOffset(ZoneOffset.ofHours(4));
    }

    @Test
    public void testAggregateGroupByMonthPeriod_slicedCorrectly() throws Exception {
        TestUtils.setupAggregation(PACKAGE_NAME, HealthDataCategory.ACTIVITY);
        Instant startTime = Instant.now().minus(40, DAYS);
        LocalDateTime startLocalTime =
                LocalDateTime.ofInstant(Instant.ofEpochMilli(startTime.toEpochMilli()), UTC);
        Instant endTime = startTime.plus(35, DAYS);
        LocalDateTime endLocalTime =
                LocalDateTime.ofInstant(Instant.ofEpochMilli(endTime.toEpochMilli()), UTC);
        Instant bucketBoundary = startLocalTime.plusMonths(1).toInstant(UTC);
        int stepsCount1 = 123;
        int stepsCount2 = 456;
        int stepsCount3 = 789;
        int stepsCount4 = 951;

        // CTS tests only have permission to read data from past 30 days
        StepsRecord month1Steps1 =
                getStepsRecord(
                        Instant.now(),
                        stepsCount1,
                        /* daysPast= */ 30,
                        /* durationInHours= */ 1,
                        UTC);
        StepsRecord month1Steps2 =
                getStepsRecord(
                        bucketBoundary.minus(1, HOURS),
                        stepsCount2,
                        /* daysPast= */ 0,
                        /* durationInHours= */ 1,
                        UTC);
        StepsRecord month2Steps1 =
                getStepsRecord(
                        bucketBoundary,
                        stepsCount3,
                        /* daysPast= */ 0,
                        /* durationInHours= */ 1,
                        UTC);
        StepsRecord month2Steps2 =
                getStepsRecord(
                        endTime.minus(1, HOURS),
                        stepsCount4,
                        /* daysPast= */ 0,
                        /* durationInHours= */ 1,
                        UTC);
        TestUtils.insertRecords(
                Arrays.asList(month1Steps1, month1Steps2, month2Steps1, month2Steps2));

        // Due to the Parcel implementation, we have to set local time at UTC zone
        AggregateRecordsRequest<Long> request =
                new AggregateRecordsRequest.Builder<Long>(
                                new LocalTimeRangeFilter.Builder()
                                        .setStartTime(startLocalTime)
                                        .setEndTime(endLocalTime)
                                        .build())
                        .addAggregationType(STEPS_COUNT_TOTAL)
                        .build();
        List<AggregateRecordsGroupedByPeriodResponse<Long>> aggregateResponse =
                TestUtils.getAggregateResponseGroupByPeriod(request, Period.ofMonths(1));

        assertThat(aggregateResponse.size()).isEqualTo(2);
        assertThat(aggregateResponse.get(0).getStartTime()).isEqualTo(startLocalTime);
        assertThat(aggregateResponse.get(0).getEndTime()).isEqualTo(startLocalTime.plusMonths(1));
        assertThat(aggregateResponse.get(0).get(STEPS_COUNT_TOTAL))
                .isEqualTo(stepsCount1 + stepsCount2);
        assertThat(aggregateResponse.get(1).getStartTime()).isEqualTo(startLocalTime.plusMonths(1));
        assertThat(aggregateResponse.get(1).getEndTime()).isEqualTo(endLocalTime);
        assertThat(aggregateResponse.get(1).get(STEPS_COUNT_TOTAL))
                .isEqualTo(stepsCount3 + stepsCount4);
    }

    private void testAggregationLocalTimeOffset(ZoneOffset offset) throws InterruptedException {
        TestUtils.setupAggregation(PACKAGE_NAME, HealthDataCategory.ACTIVITY);
        LocalDateTime endTimeLocal = LocalDateTime.now(offset);
        LocalDateTime startTimeLocal = endTimeLocal.minusDays(4);
        Instant endTimeInstant = endTimeLocal.toInstant(offset);
        insertFourStepsRecordsWithZoneOffset(endTimeInstant, offset);

        List<AggregateRecordsGroupedByPeriodResponse<Long>> responses =
                TestUtils.getAggregateResponseGroupByPeriod(
                        new AggregateRecordsRequest.Builder<Long>(
                                        new LocalTimeRangeFilter.Builder()
                                                .setStartTime(startTimeLocal)
                                                .setEndTime(endTimeLocal)
                                                .build())
                                .addAggregationType(STEPS_COUNT_TOTAL)
                                .build(),
                        Period.ofDays(1));

        assertThat(responses).hasSize(4);
        LocalDateTime groupBoundary = startTimeLocal;
        for (int i = 0; i < 4; i++) {
            assertThat(responses.get(i).get(STEPS_COUNT_TOTAL)).isEqualTo(10);
            assertThat(responses.get(i).getZoneOffset(STEPS_COUNT_TOTAL)).isEqualTo(offset);
            assertThat(responses.get(i).getStartTime().getDayOfYear())
                    .isEqualTo(groupBoundary.getDayOfYear());
            groupBoundary = groupBoundary.plusDays(1);
            assertThat(responses.get(i).getEndTime().getDayOfYear())
                    .isEqualTo(groupBoundary.getDayOfYear());
            assertThat(responses.get(i).getDataOrigins(STEPS_COUNT_TOTAL)).hasSize(1);
            assertThat(
                            responses
                                    .get(i)
                                    .getDataOrigins(STEPS_COUNT_TOTAL)
                                    .iterator()
                                    .next()
                                    .getPackageName())
                    .isEqualTo(ApplicationProvider.getApplicationContext().getPackageName());
        }

        tearDown();
    }

    @Test
    public void testAggregatePeriod_withLocalDateTime_halfSizeGroupResultIsCorrect()
            throws Exception {
        TestUtils.setupAggregation(PACKAGE_NAME, HealthDataCategory.ACTIVITY);
        Instant end = Instant.now();
        // Insert steps from -48 hours to -12 hours, 36 hours session
        TestUtils.insertRecords(List.of(getStepsRecord(end, 2160, 2, 36, ZoneOffset.UTC)));

        LocalDateTime endTimeLocal = LocalDateTime.ofInstant(end, ZoneOffset.UTC);
        List<AggregateRecordsGroupedByPeriodResponse<Long>> responses =
                TestUtils.getAggregateResponseGroupByPeriod(
                        new AggregateRecordsRequest.Builder<Long>(
                                        new LocalTimeRangeFilter.Builder()
                                                .setStartTime(endTimeLocal.minusHours(60))
                                                .setEndTime(endTimeLocal.minusHours(24))
                                                .build())
                                .addAggregationType(STEPS_COUNT_TOTAL)
                                .build(),
                        Period.ofDays(1));

        assertThat(responses).hasSize(2);
        // -60 hours to -36 hours, 12 hours intersection with the group
        assertThat(responses.get(0).get(STEPS_COUNT_TOTAL)).isEqualTo(720);
        // -36 hours to -24 hours, 12 hours intersection with the group
        assertThat(responses.get(1).get(STEPS_COUNT_TOTAL)).isEqualTo(720);
    }

    @Test
    public void testAggregateLocalFilter_minOffsetRecord() throws Exception {
        TestUtils.setupAggregation(PACKAGE_NAME, HealthDataCategory.ACTIVITY);
        LocalDateTime endTimeLocal = LocalDateTime.now(ZoneOffset.UTC);
        Instant endTimeInstant = Instant.now();

        AggregateRecordsResponse<Long> response =
                TestUtils.getAggregateResponse(
                        new AggregateRecordsRequest.Builder<Long>(
                                        new LocalTimeRangeFilter.Builder()
                                                .setStartTime(endTimeLocal.minusHours(25))
                                                .setEndTime(endTimeLocal.minusHours(15))
                                                .build())
                                .addAggregationType(STEPS_COUNT_TOTAL)
                                .build(),
                        List.of(
                                new StepsRecord.Builder(
                                                generateMetadata(),
                                                endTimeInstant.minusSeconds(500),
                                                endTimeInstant.minusSeconds(100),
                                                100)
                                        .setStartZoneOffset(ZoneOffset.MIN)
                                        .setEndZoneOffset(ZoneOffset.MIN)
                                        .build(),
                                new StepsRecord.Builder(
                                                generateMetadata(),
                                                endTimeInstant.minusSeconds(1000),
                                                endTimeInstant.minusSeconds(800),
                                                100)
                                        .setStartZoneOffset(ZoneOffset.MIN)
                                        .setEndZoneOffset(ZoneOffset.MIN)
                                        .build()));

        assertThat(response.get(STEPS_COUNT_TOTAL)).isEqualTo(200);
    }

    @Test
    public void testAggregate_withDifferentTimeZone() throws Exception {
        TestUtils.setupAggregation(PACKAGE_NAME, HealthDataCategory.ACTIVITY);
        Instant instant = Instant.now();
        List<Record> records =
                List.of(
                        getStepsRecord(instant, 10, 4, 1, ZoneOffset.ofHours(1)),
                        getStepsRecord(instant, 20, 5, 1, ZoneOffset.ofHours(2)),
                        getStepsRecord(instant, 30, 3, 1, ZoneOffset.ofHours(3)),
                        getStepsRecord(instant, 40, 1, 1, ZoneOffset.ofHours(4)));
        AggregateRecordsRequest<Long> aggregateRecordsRequest =
                new AggregateRecordsRequest.Builder<Long>(
                                new TimeInstantRangeFilter.Builder()
                                        .setStartTime(Instant.ofEpochMilli(0))
                                        .setEndTime(Instant.now().plus(1, ChronoUnit.DAYS))
                                        .build())
                        .addAggregationType(STEPS_COUNT_TOTAL)
                        .build();
        AggregateRecordsResponse<Long> oldResponse =
                TestUtils.getAggregateResponse(aggregateRecordsRequest, records);
        assertThat(oldResponse.getZoneOffset(STEPS_COUNT_TOTAL)).isEqualTo(ZoneOffset.ofHours(2));
        List<Record> recordNew =
                Arrays.asList(
                        getStepsRecord(instant, 1000, 7, 1, ZoneOffset.UTC),
                        getStepsRecord(1000, 4, 1));
        AggregateRecordsResponse<Long> newResponse =
                TestUtils.getAggregateResponse(
                        new AggregateRecordsRequest.Builder<Long>(
                                        new TimeInstantRangeFilter.Builder()
                                                .setStartTime(Instant.ofEpochMilli(0))
                                                .setEndTime(Instant.now().plus(1, ChronoUnit.DAYS))
                                                .build())
                                .addAggregationType(STEPS_COUNT_TOTAL)
                                .build(),
                        recordNew);
        assertThat(newResponse.get(STEPS_COUNT_TOTAL)).isNotNull();
        assertThat(newResponse.getZoneOffset(STEPS_COUNT_TOTAL)).isEqualTo(ZoneOffset.UTC);
    }

    @Test
    public void testAggregateGroup_withDifferentTimeZone() throws Exception {
        TestUtils.setupAggregation(PACKAGE_NAME, HealthDataCategory.ACTIVITY);
        Instant instant = Instant.now();
        Instant endTime = Instant.now();
        LocalDateTime endTimeLocal = LocalDateTime.ofInstant(endTime, ZoneOffset.UTC);
        LocalDateTime startTimeLocal = endTimeLocal.minusDays(5);
        TestUtils.insertRecords(
                List.of(
                        getStepsRecord(instant, 10, 5, 1, ZoneOffset.ofHours(2)),
                        getStepsRecord(instant.plus(3, HOURS), 10, 5, 13, ZoneOffset.UTC),
                        getStepsRecord(instant, 20, 4, 1, ZoneOffset.ofHours(3)),
                        getStepsRecord(instant.plus(4, HOURS), 10, 4, 3, ZoneOffset.UTC),
                        getStepsRecord(instant, 30, 3, 1, ZoneOffset.ofHours(5)),
                        getStepsRecord(instant.plus(5, HOURS), 10, 3, 3, ZoneOffset.UTC),
                        getStepsRecord(instant, 10, 2, 1, ZoneOffset.ofHours(2)),
                        getStepsRecord(instant, 40, 1, 1, ZoneOffset.UTC)));
        List<AggregateRecordsGroupedByDurationResponse<Long>> responses =
                TestUtils.getAggregateResponseGroupByDuration(
                        new AggregateRecordsRequest.Builder<Long>(
                                        new LocalTimeRangeFilter.Builder()
                                                .setStartTime(startTimeLocal)
                                                .setEndTime(endTimeLocal)
                                                .build())
                                .addAggregationType(STEPS_COUNT_TOTAL)
                                .build(),
                        Duration.ofDays(1));
        assertThat(responses.get(0).getZoneOffset(STEPS_COUNT_TOTAL))
                .isEqualTo(ZoneOffset.ofHours(2));
        assertThat(responses.get(1).getZoneOffset(STEPS_COUNT_TOTAL))
                .isEqualTo(ZoneOffset.ofHours(3));
        assertThat(responses.get(2).getZoneOffset(STEPS_COUNT_TOTAL))
                .isEqualTo(ZoneOffset.ofHours(5));
        assertThat(responses.get(3).getZoneOffset(STEPS_COUNT_TOTAL))
                .isEqualTo(ZoneOffset.ofHours(2));
        assertThat(responses.get(4).getZoneOffset(STEPS_COUNT_TOTAL)).isEqualTo(ZoneOffset.UTC);
    }

    @Test
    public void testAggregateDuration_differentTimeZones_correctBucketTimes() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        Duration oneHour = Duration.ofHours(1);
        Instant t1 = Instant.now().minus(Duration.ofDays(1)).truncatedTo(ChronoUnit.MILLIS);
        Instant t2 = t1.plus(oneHour);
        Instant t3 = t2.plus(oneHour);

        Metadata metadata =
                new Metadata.Builder()
                        .setDataOrigin(
                                new DataOrigin.Builder()
                                        .setPackageName(context.getPackageName())
                                        .build())
                        .build();

        ZoneOffset zonePlusFive = ZoneOffset.ofHours(5);
        ZoneOffset zonePlusSix = ZoneOffset.ofHours(6);
        ZoneOffset zonePlusSeven = ZoneOffset.ofHours(7);

        StepsRecord rec1 =
                new StepsRecord.Builder(metadata, t1, t2, /* count= */ 100)
                        .setStartZoneOffset(zonePlusFive)
                        .setEndZoneOffset(zonePlusFive)
                        .build();
        StepsRecord rec2 =
                new StepsRecord.Builder(metadata, t2, t2, /* count= */ 300)
                        .setStartZoneOffset(zonePlusSix)
                        .setEndZoneOffset(zonePlusSeven)
                        .build();

        TestUtils.setupAggregation(PACKAGE_NAME, HealthDataCategory.ACTIVITY);
        TestUtils.insertRecords(List.of(rec1, rec2));

        // Aggregating between [t1+5, t2+7] with 1 hour group duration
        List<AggregateRecordsGroupedByDurationResponse<Long>> result =
                TestUtils.getAggregateResponseGroupByDuration(
                        new AggregateRecordsRequest.Builder<Long>(
                                        new LocalTimeRangeFilter.Builder()
                                                .setStartTime(
                                                        LocalDateTime.ofInstant(t1, zonePlusFive))
                                                .setEndTime(
                                                        LocalDateTime.ofInstant(t2, zonePlusSeven))
                                                .build())
                                .addAggregationType(STEPS_COUNT_TOTAL)
                                .build(),
                        oneHour);

        assertThat(result).hasSize(3);

        // Bucket #0: [t1+5, t2+5]
        AggregateRecordsGroupedByDurationResponse<Long> response0 = result.get(0);
        assertThat(response0.getStartTime()).isEqualTo(t1);
        assertThat(response0.getEndTime()).isEqualTo(t2);
        assertThat(response0.getZoneOffset(STEPS_COUNT_TOTAL)).isEqualTo(zonePlusFive);

        // Empty bucket in the middle
        assertThat(result.get(1).get(STEPS_COUNT_TOTAL)).isNull();

        // Bucket #2: [t2+6, t3+6]
        AggregateRecordsGroupedByDurationResponse<Long> response2 = result.get(2);
        assertThat(response2.getStartTime()).isEqualTo(t2);
        assertThat(response2.getEndTime()).isEqualTo(t3);
        assertThat(response2.getZoneOffset(STEPS_COUNT_TOTAL)).isEqualTo(zonePlusSix);
    }

    @Test
    public void testAggregateDuration_withLocalDateTime() throws Exception {
        testAggregateDurationWithLocalTimeForZoneOffset(ZoneOffset.MIN);
        testAggregateDurationWithLocalTimeForZoneOffset(ZoneOffset.ofHours(-4));
        testAggregateDurationWithLocalTimeForZoneOffset(ZoneOffset.UTC);
        testAggregateDurationWithLocalTimeForZoneOffset(ZoneOffset.ofHours(4));
        testAggregateDurationWithLocalTimeForZoneOffset(ZoneOffset.MAX);
    }

    @Test
    public void insertRecords_withDuplicatedClientRecordId_readNoDuplicates() throws Exception {
        int distinctRecordCount = 10;
        List<StepsRecord> records = new ArrayList<>();
        Instant now = Instant.now();
        for (int i = 0; i < distinctRecordCount; i++) {
            StepsRecord record =
                    getCompleteStepsRecord(
                            /* startTime= */ now.minusMillis(i + 1),
                            /* endTime= */ now.minusMillis(i),
                            /* clientRecordId= */ "client_id_" + i);

            records.add(record);
            records.add(record); // Add each record twice
        }

        List<Record> insertedRecords = TestUtils.insertRecords(records);
        assertThat(insertedRecords.size()).isEqualTo(records.size());

        List<Record> distinctRecords = distinctByUuid(insertedRecords);
        assertThat(distinctRecords.size()).isEqualTo(distinctRecordCount);

        assertStepsRecordUsingIds(distinctRecords);
    }

    @Test
    public void insertRecords_sameClientRecordIdAndNewData_readNewData() throws Exception {
        int recordCount = 10;
        insertAndReadRecords(recordCount, /* stepCount= */ 10);

        int newCount = 20;
        List<StepsRecord> newRecords = insertAndReadRecords(recordCount, newCount);

        for (StepsRecord record : newRecords) {
            assertThat(record.getCount()).isEqualTo(newCount);
        }
    }

    @Test
    public void insertRecords_sameClientRecordIdAndNewerVersion_readNewData() throws Exception {
        int recordCount = 10;
        long oldVersion = 0L;
        int oldCount = 10;
        insertAndReadRecords(recordCount, oldVersion, oldCount);

        long newVersion = 1L;
        int newCount = 20;
        List<StepsRecord> newRecords = insertAndReadRecords(recordCount, newVersion, newCount);

        for (StepsRecord record : newRecords) {
            assertThat(record.getCount()).isEqualTo(newCount);
        }
    }

    @Test
    public void insertRecords_sameClientRecordIdAndSameVersion_readNewData() throws Exception {
        int recordCount = 10;
        long version = 1L;
        int oldCount = 10;
        insertAndReadRecords(recordCount, version, oldCount);

        int newCount = 20;
        List<StepsRecord> newRecords = insertAndReadRecords(recordCount, version, newCount);

        for (StepsRecord record : newRecords) {
            assertThat(record.getCount()).isEqualTo(newCount);
        }
    }

    @Test
    public void insertRecords_sameClientRecordIdAndOlderVersion_readOldData() throws Exception {
        int recordCount = 10;
        long oldVersion = 1L;
        int oldCount = 10;
        insertAndReadRecords(recordCount, oldVersion, oldCount);

        long newVersion = 0L;
        int newCount = 20;
        List<StepsRecord> newRecords = insertAndReadRecords(recordCount, newVersion, newCount);

        for (StepsRecord record : newRecords) {
            assertThat(record.getCount()).isEqualTo(oldCount);
        }
    }

    private static List<StepsRecord> insertAndReadRecords(int recordCount, int stepCount)
            throws Exception {
        return insertAndReadRecords(recordCount, /* version= */ 0L, stepCount);
    }

    private static List<StepsRecord> insertAndReadRecords(
            int recordCount, long version, int stepCount) throws Exception {
        List<StepsRecord> records = new ArrayList<>();
        Instant now = Instant.now();
        for (int i = 0; i < recordCount; i++) {
            Instant startTime = now.minusSeconds(i + 1);
            Instant endTime = now.minusSeconds(i);
            String recordId = "client_id_" + i;
            records.add(getCompleteStepsRecord(startTime, endTime, recordId, version, stepCount));
        }
        List<Record> insertedRecords = insertRecords(records);
        assertThat(insertedRecords).hasSize(recordCount);

        List<StepsRecord> readRecords =
                readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(StepsRecord.class).build());
        assertThat(readRecords).hasSize(recordCount);

        return readRecords;
    }

    private void testAggregateDurationWithLocalTimeForZoneOffset(ZoneOffset offset)
            throws Exception {
        TestUtils.setupAggregation(PACKAGE_NAME, HealthDataCategory.ACTIVITY);
        Instant endTime = Instant.now();
        LocalDateTime endTimeLocal = LocalDateTime.ofInstant(endTime, offset);
        LocalDateTime startTimeLocal = endTimeLocal.minusDays(4);
        insertFourStepsRecordsWithZoneOffset(endTime, offset);

        List<AggregateRecordsGroupedByDurationResponse<Long>> responses =
                TestUtils.getAggregateResponseGroupByDuration(
                        new AggregateRecordsRequest.Builder<Long>(
                                        new LocalTimeRangeFilter.Builder()
                                                .setStartTime(startTimeLocal)
                                                .setEndTime(endTimeLocal)
                                                .build())
                                .addAggregationType(STEPS_COUNT_TOTAL)
                                .build(),
                        Duration.ofDays(1));

        assertThat(responses).hasSize(4);
        Instant groupBoundary = startTimeLocal.toInstant(offset);
        for (int i = 0; i < 4; i++) {
            assertThat(responses.get(i).get(STEPS_COUNT_TOTAL)).isEqualTo(10);
            assertThat(responses.get(i).getZoneOffset(STEPS_COUNT_TOTAL)).isEqualTo(offset);
            assertThat(responses.get(i).getStartTime().getEpochSecond())
                    .isEqualTo(groupBoundary.getEpochSecond());
            groupBoundary = groupBoundary.plus(1, ChronoUnit.DAYS);
            assertThat(responses.get(i).getEndTime().getEpochSecond())
                    .isEqualTo(groupBoundary.getEpochSecond());
            assertThat(responses.get(i).getDataOrigins(STEPS_COUNT_TOTAL)).hasSize(1);
            assertThat(
                            responses
                                    .get(i)
                                    .getDataOrigins(STEPS_COUNT_TOTAL)
                                    .iterator()
                                    .next()
                                    .getPackageName())
                    .isEqualTo(ApplicationProvider.getApplicationContext().getPackageName());
        }

        tearDown();
    }

    private void insertFourStepsRecordsWithZoneOffset(Instant endTime, ZoneOffset offset)
            throws InterruptedException {
        TestUtils.insertRecords(
                List.of(
                        getStepsRecord(endTime, 10, 1, 1, offset),
                        getStepsRecord(endTime, 10, 2, 1, offset),
                        getStepsRecord(endTime, 10, 3, 1, offset),
                        getStepsRecord(endTime, 10, 4, 1, offset)));
    }

    StepsRecord getStepsRecordDuplicateEntry(
            StepsRecord recordToUpdate, StepsRecord duplicateRecord) {
        Metadata metadata = recordToUpdate.getMetadata();
        Metadata metadataWithId =
                new Metadata.Builder()
                        .setId(metadata.getId())
                        .setClientRecordVersion(metadata.getClientRecordVersion())
                        .setDataOrigin(metadata.getDataOrigin())
                        .setDevice(metadata.getDevice())
                        .setLastModifiedTime(metadata.getLastModifiedTime())
                        .build();
        return new StepsRecord.Builder(
                        metadataWithId,
                        duplicateRecord.getStartTime(),
                        duplicateRecord.getEndTime(),
                        20)
                .setStartZoneOffset(ZoneOffset.systemDefault().getRules().getOffset(Instant.now()))
                .setEndZoneOffset(ZoneOffset.systemDefault().getRules().getOffset(Instant.now()))
                .build();
    }

    static void assertStepsRecordUsingIds(List<Record> recordList) throws InterruptedException {
        ReadRecordsRequestUsingIds.Builder<StepsRecord> request =
                new ReadRecordsRequestUsingIds.Builder<>(StepsRecord.class);
        for (Record record : recordList) {
            request.addId(record.getMetadata().getId());
        }
        ReadRecordsRequestUsingIds requestUsingIds = request.build();
        assertThat(requestUsingIds.getRecordType()).isEqualTo(StepsRecord.class);
        assertThat(requestUsingIds.getRecordIdFilters()).isNotNull();
        List<StepsRecord> result = TestUtils.readRecords(requestUsingIds);
        assertThat(result).hasSize(recordList.size());
        assertThat(result).containsExactlyElementsIn(recordList);
    }

    static StepsRecord getBaseStepsRecord() {
        return new StepsRecord.Builder(
                        new Metadata.Builder().setDataOrigin(getDataOrigin()).build(),
                        Instant.now(),
                        Instant.now().plusMillis(1000),
                        10)
                .build();
    }

    static StepsRecord getStepsRecord(int count, int daysPast, int durationInHours) {
        return getStepsRecord(Instant.now(), count, daysPast, durationInHours);
    }

    static StepsRecord getStepsRecord(Instant time, int count, int daysPast, int durationInHours) {
        return getStepsRecord(time, count, daysPast, durationInHours, null);
    }

    static StepsRecord getStepsRecord(
            Instant time, int count, int daysPast, int durationInHours, ZoneOffset offset) {
        StepsRecord.Builder builder =
                new StepsRecord.Builder(
                        new Metadata.Builder().setDataOrigin(getDataOrigin()).build(),
                        time.minus(daysPast, ChronoUnit.DAYS),
                        time.minus(daysPast, ChronoUnit.DAYS).plus(durationInHours, HOURS),
                        count);
        if (offset != null) {
            builder.setStartZoneOffset(offset).setEndZoneOffset(offset);
        }
        return builder.build();
    }

    static StepsRecord getStepsRecordWithClientVersion(
            int steps, int version, String clientRecordId) {
        Metadata.Builder testMetadataBuilder = new Metadata.Builder();
        testMetadataBuilder.setDataOrigin(getDataOrigin());
        testMetadataBuilder.setClientRecordId(clientRecordId);
        testMetadataBuilder.setClientRecordVersion(version);
        Metadata testMetaData = testMetadataBuilder.build();
        return new StepsRecord.Builder(
                        testMetaData, Instant.now(), Instant.now().plusMillis(1000), steps)
                .build();
    }

    static StepsRecord getStepsRecord_minusDays(int days) {
        return new StepsRecord.Builder(
                        new Metadata.Builder().build(),
                        Instant.now().minus(days, ChronoUnit.DAYS),
                        Instant.now().minus(days, ChronoUnit.DAYS).plusMillis(1000),
                        10)
                .build();
    }

    private static DataOrigin getDataOrigin() {
        return new DataOrigin.Builder().setPackageName(PACKAGE_NAME).build();
    }
}
