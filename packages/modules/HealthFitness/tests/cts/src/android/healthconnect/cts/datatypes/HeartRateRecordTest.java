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

import static android.health.connect.datatypes.HeartRateRecord.BPM_AVG;
import static android.health.connect.datatypes.HeartRateRecord.BPM_MAX;
import static android.health.connect.datatypes.HeartRateRecord.BPM_MIN;
import static android.health.connect.datatypes.HeartRateRecord.HEART_MEASUREMENTS_COUNT;
import static android.healthconnect.cts.utils.DataFactory.getHeartRateRecord;
import static android.healthconnect.cts.utils.TestUtils.readRecordsWithPagination;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.health.connect.AggregateRecordsGroupedByDurationResponse;
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
import android.health.connect.datatypes.Device;
import android.health.connect.datatypes.HeartRateRecord;
import android.health.connect.datatypes.Metadata;
import android.health.connect.datatypes.Record;
import android.healthconnect.cts.utils.AssumptionCheckerRule;
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
public class HeartRateRecordTest {
    private static final String TAG = "HeartRateRecordTest";
    private static final String PACKAGE_NAME = "android.healthconnect.cts";

    @Rule
    public AssumptionCheckerRule mSupportedHardwareRule =
            new AssumptionCheckerRule(
                    TestUtils::isHardwareSupported, "Tests should run on supported hardware only.");

    @After
    public void tearDown() throws InterruptedException {
        TestUtils.verifyDeleteRecords(
                HeartRateRecord.class,
                new TimeInstantRangeFilter.Builder()
                        .setStartTime(Instant.EPOCH)
                        .setEndTime(Instant.now())
                        .build());
        TestUtils.deleteAllStagedRemoteData();
    }

    @Before
    public void setUp() throws InterruptedException {
        TestUtils.deleteAllStagedRemoteData();
    }

    @Test
    public void testInsertHeartRateRecord_huge() throws InterruptedException {
        ArrayList<Record> hearRateRecords = new ArrayList<>();
        for (int i = 0; i < 2500; i++) {
            hearRateRecords.addAll(
                    Arrays.asList(getBaseHeartRateRecord(10), getCompleteHeartRateRecord()));
        }
        TestUtils.insertRecords(hearRateRecords);
    }

    @Test
    public void testInsertHeartRateRecord() throws InterruptedException {
        TestUtils.insertRecords(
                Arrays.asList(getBaseHeartRateRecord(10), getCompleteHeartRateRecord()));
    }

    @Test
    public void testReadHeartRateRecord_usingIds() throws InterruptedException {
        testReadHeartRateRecordIds();
    }

    @Test
    public void testReadHeartRateRecord_invalidIds() throws InterruptedException {
        ReadRecordsRequestUsingIds<HeartRateRecord> request =
                new ReadRecordsRequestUsingIds.Builder<>(HeartRateRecord.class)
                        .addId(UUID.randomUUID().toString())
                        .build();
        List<HeartRateRecord> result = TestUtils.readRecords(request);
        assertThat(result.size()).isEqualTo(0);
    }

    @Test
    public void testReadHeartRateRecord_usingClientRecordIds() throws InterruptedException {
        List<Record> recordList =
                Arrays.asList(getCompleteHeartRateRecord(), getCompleteHeartRateRecord());
        List<Record> insertedRecords = TestUtils.insertRecords(recordList);
        readHeartRateRecordUsingClientId(insertedRecords);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCreateHeartRateRecord_invalidValue() {
        new HeartRateRecord.HeartRateSample(301, Instant.now().plusMillis(100));
    }

    @Test
    public void testReadHeartRateRecord_invalidClientRecordIds() throws InterruptedException {
        ReadRecordsRequestUsingIds<HeartRateRecord> request =
                new ReadRecordsRequestUsingIds.Builder<>(HeartRateRecord.class)
                        .addClientRecordId("abc")
                        .build();
        List<HeartRateRecord> result = TestUtils.readRecords(request);
        assertThat(result.size()).isEqualTo(0);
    }

    @Test
    public void testReadHeartRateRecordUsingFilters_default() throws InterruptedException {
        List<HeartRateRecord> oldHeartRateRecords =
                TestUtils.readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(HeartRateRecord.class)
                                .build());
        HeartRateRecord testRecord = getCompleteHeartRateRecord();
        TestUtils.insertRecords(Collections.singletonList(testRecord));
        List<HeartRateRecord> newHeartRateRecords =
                TestUtils.readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(HeartRateRecord.class)
                                .build());
        assertThat(newHeartRateRecords.size()).isEqualTo(oldHeartRateRecords.size() + 1);
        assertThat(newHeartRateRecords.get(newHeartRateRecords.size() - 1).equals(testRecord))
                .isTrue();
    }

    @Test
    public void testReadHeartRateRecordUsingFilters_timeFilter() throws InterruptedException {
        TimeInstantRangeFilter filter =
                new TimeInstantRangeFilter.Builder()
                        .setStartTime(Instant.now())
                        .setEndTime(Instant.now().plusMillis(3000))
                        .build();
        HeartRateRecord testRecord = getCompleteHeartRateRecord();
        TestUtils.insertRecords(Collections.singletonList(testRecord));
        ReadRecordsRequestUsingFilters<HeartRateRecord> requestUsingFilters =
                new ReadRecordsRequestUsingFilters.Builder<>(HeartRateRecord.class)
                        .setTimeRangeFilter(filter)
                        .build();
        assertThat(requestUsingFilters.getTimeRangeFilter()).isNotNull();
        assertThat(requestUsingFilters.isAscending()).isTrue();
        assertThat(requestUsingFilters.getPageSize()).isEqualTo(1000);
        List<HeartRateRecord> newHeartRateRecords = TestUtils.readRecords(requestUsingFilters);
        assertThat(newHeartRateRecords.size()).isEqualTo(1);
        assertThat(newHeartRateRecords.get(newHeartRateRecords.size() - 1).equals(testRecord))
                .isTrue();
    }

    @Test
    public void testReadHeartRateRecordUsingFilters_dataFilter_correct()
            throws InterruptedException {
        Context context = ApplicationProvider.getApplicationContext();
        List<HeartRateRecord> oldHeartRateRecords =
                TestUtils.readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(HeartRateRecord.class)
                                .addDataOrigins(
                                        new DataOrigin.Builder()
                                                .setPackageName(context.getPackageName())
                                                .build())
                                .build());
        HeartRateRecord testRecord = getCompleteHeartRateRecord();
        TestUtils.insertRecords(Collections.singletonList(testRecord));
        List<HeartRateRecord> newHeartRateRecords =
                TestUtils.readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(HeartRateRecord.class)
                                .addDataOrigins(
                                        new DataOrigin.Builder()
                                                .setPackageName(context.getPackageName())
                                                .build())
                                .build());
        assertThat(newHeartRateRecords.size() - oldHeartRateRecords.size()).isEqualTo(1);
        assertThat(newHeartRateRecords.get(newHeartRateRecords.size() - 1).equals(testRecord))
                .isTrue();
        HeartRateRecord newRecord = newHeartRateRecords.get(newHeartRateRecords.size() - 1);
        for (int idx = 0; idx < newRecord.getSamples().size(); idx++) {
            assertThat(newRecord.getSamples().get(idx).getTime().toEpochMilli())
                    .isEqualTo(testRecord.getSamples().get(idx).getTime().toEpochMilli());
            assertThat(newRecord.getSamples().get(idx).getBeatsPerMinute())
                    .isEqualTo(testRecord.getSamples().get(idx).getBeatsPerMinute());
        }
    }

    @Test
    public void testReadHeartRateRecordUsingFilters_withPageSize() throws InterruptedException {
        List<Record> recordList =
                Arrays.asList(
                        getHeartRateRecord(72, Instant.now().minus(1, ChronoUnit.DAYS)),
                        getHeartRateRecord(72, Instant.now().minus(2, ChronoUnit.DAYS)));
        TestUtils.insertRecords(recordList);
        ReadRecordsResponse<HeartRateRecord> newHeartRecords =
                readRecordsWithPagination(
                        new ReadRecordsRequestUsingFilters.Builder<>(HeartRateRecord.class)
                                .setPageSize(1)
                                .build());
        assertThat(newHeartRecords.getRecords()).hasSize(1);
    }

    @Test
    public void testReadHeartRateRecordUsingFilters_withPageToken() throws InterruptedException {
        List<Record> recordList =
                Arrays.asList(
                        getHeartRateRecord(72, Instant.now().minusMillis(1000)),
                        getHeartRateRecord(72, Instant.now().minusMillis(2000)),
                        getHeartRateRecord(72, Instant.now().minusMillis(3000)),
                        getHeartRateRecord(72, Instant.now().minusMillis(4000)));
        TestUtils.insertRecords(recordList);
        ReadRecordsResponse<HeartRateRecord> oldHeartRecords =
                readRecordsWithPagination(
                        new ReadRecordsRequestUsingFilters.Builder<>(HeartRateRecord.class)
                                .setPageSize(1)
                                .setAscending(true)
                                .build());
        assertThat(oldHeartRecords.getRecords()).hasSize(1);
        ReadRecordsResponse<HeartRateRecord> newHeartRecords =
                readRecordsWithPagination(
                        new ReadRecordsRequestUsingFilters.Builder<>(HeartRateRecord.class)
                                .setPageSize(2)
                                .setPageToken(oldHeartRecords.getNextPageToken())
                                .build());
        assertThat(newHeartRecords.getRecords()).hasSize(2);
        assertThat(newHeartRecords.getNextPageToken())
                .isNotEqualTo(oldHeartRecords.getNextPageToken());
    }

    @Test
    public void testReadHeartRateRecordUsingFilters_nextPageTokenEnd() throws InterruptedException {
        List<Record> recordList = Arrays.asList(getHeartRateRecord(), getHeartRateRecord());
        TestUtils.insertRecords(recordList);
        ReadRecordsResponse<HeartRateRecord> oldHeartRecords =
                readRecordsWithPagination(
                        new ReadRecordsRequestUsingFilters.Builder<>(HeartRateRecord.class)
                                .build());
        ReadRecordsResponse<HeartRateRecord> newHeartRecords;
        while (oldHeartRecords.getNextPageToken() != -1) {
            newHeartRecords =
                    readRecordsWithPagination(
                            new ReadRecordsRequestUsingFilters.Builder<>(HeartRateRecord.class)
                                    .setPageToken(oldHeartRecords.getNextPageToken())
                                    .build());
            oldHeartRecords = newHeartRecords;
        }
        assertThat(oldHeartRecords.getNextPageToken()).isEqualTo(-1);
    }

    @Test
    public void testReadHeartRateRecordUsingFilters_dataFilter_incorrect()
            throws InterruptedException {
        TestUtils.insertRecords(Collections.singletonList(getCompleteHeartRateRecord()));
        ReadRecordsRequestUsingFilters<HeartRateRecord> readRequest =
                new ReadRecordsRequestUsingFilters.Builder<>(HeartRateRecord.class)
                        .addDataOrigins(new DataOrigin.Builder().setPackageName("abc").build())
                        .setAscending(false)
                        .build();
        assertThat(readRequest.getRecordType()).isNotNull();
        assertThat(readRequest.getRecordType()).isEqualTo(HeartRateRecord.class);
        List<HeartRateRecord> newHeartRateRecords = TestUtils.readRecords(readRequest);
        assertThat(newHeartRateRecords.size()).isEqualTo(0);
    }

    @Test
    public void testDeleteHeartRateRecord_no_filters() throws InterruptedException {
        String id = TestUtils.insertRecordAndGetId(getCompleteHeartRateRecord());
        TestUtils.verifyDeleteRecords(new DeleteUsingFiltersRequest.Builder().build());
        TestUtils.assertRecordNotFound(id, HeartRateRecord.class);
    }

    @Test
    public void testDeleteHeartRateRecord_time_filters() throws InterruptedException {
        TimeInstantRangeFilter timeRangeFilter =
                new TimeInstantRangeFilter.Builder()
                        .setStartTime(Instant.now())
                        .setEndTime(Instant.now().plusMillis(1000))
                        .build();
        String id = TestUtils.insertRecordAndGetId(getCompleteHeartRateRecord());
        TestUtils.verifyDeleteRecords(
                new DeleteUsingFiltersRequest.Builder()
                        .addRecordType(HeartRateRecord.class)
                        .setTimeRangeFilter(timeRangeFilter)
                        .build());
        TestUtils.assertRecordNotFound(id, HeartRateRecord.class);
    }

    static HeartRateRecord getBaseHeartRateRecord(Instant time, ZoneOffset zoneOffset) {
        HeartRateRecord.HeartRateSample heartRateRecord =
                new HeartRateRecord.HeartRateSample(50, time.plusMillis(100));
        ArrayList<HeartRateRecord.HeartRateSample> heartRateRecords = new ArrayList<>();
        heartRateRecords.add(heartRateRecord);
        heartRateRecords.add(heartRateRecord);

        return new HeartRateRecord.Builder(
                        new Metadata.Builder().build(),
                        time,
                        time.plus(1, ChronoUnit.SECONDS),
                        heartRateRecords)
                .setStartZoneOffset(zoneOffset)
                .setEndZoneOffset(zoneOffset)
                .build();
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
                        getBaseHeartRateRecord(
                                recordTime.toInstant(ZoneOffset.MIN), ZoneOffset.MIN));
        String id2 =
                TestUtils.insertRecordAndGetId(
                        getBaseHeartRateRecord(
                                recordTime.toInstant(ZoneOffset.MAX), ZoneOffset.MAX));
        TestUtils.assertRecordFound(id1, HeartRateRecord.class);
        TestUtils.assertRecordFound(id2, HeartRateRecord.class);
        TestUtils.verifyDeleteRecords(
                new DeleteUsingFiltersRequest.Builder()
                        .addRecordType(HeartRateRecord.class)
                        .setTimeRangeFilter(timeRangeFilter)
                        .build());
        TestUtils.assertRecordNotFound(id1, HeartRateRecord.class);
        TestUtils.assertRecordNotFound(id2, HeartRateRecord.class);
    }

    @Test
    public void testDeleteHeartRateRecord_recordId_filters() throws InterruptedException {
        List<Record> records = List.of(getBaseHeartRateRecord(10), getCompleteHeartRateRecord());
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
    public void testDeleteHeartRateRecord_dataOrigin_filters() throws InterruptedException {
        Context context = ApplicationProvider.getApplicationContext();
        String id = TestUtils.insertRecordAndGetId(getCompleteHeartRateRecord());
        TestUtils.verifyDeleteRecords(
                new DeleteUsingFiltersRequest.Builder()
                        .addDataOrigin(
                                new DataOrigin.Builder()
                                        .setPackageName(context.getPackageName())
                                        .build())
                        .build());
        TestUtils.assertRecordNotFound(id, HeartRateRecord.class);
    }

    @Test
    public void testDeleteHeartRateRecord_dataOrigin_filter_incorrect()
            throws InterruptedException {
        String id = TestUtils.insertRecordAndGetId(getCompleteHeartRateRecord());
        TestUtils.verifyDeleteRecords(
                new DeleteUsingFiltersRequest.Builder()
                        .addDataOrigin(new DataOrigin.Builder().setPackageName("abc").build())
                        .build());
        TestUtils.assertRecordFound(id, HeartRateRecord.class);
    }

    @Test
    public void testDeleteHeartRateRecord_usingIds() throws InterruptedException {
        List<Record> records = List.of(getBaseHeartRateRecord(10), getCompleteHeartRateRecord());
        List<Record> insertedRecord = TestUtils.insertRecords(records);
        List<RecordIdFilter> recordIds = new ArrayList<>(records.size());
        for (Record record : insertedRecord) {
            recordIds.add(RecordIdFilter.fromId(record.getClass(), record.getMetadata().getId()));
        }
        for (RecordIdFilter recordIdFilter : recordIds) {
            assertThat(recordIdFilter.getClientRecordId()).isNull();
            assertThat(recordIdFilter.getId()).isNotNull();
            assertThat(recordIdFilter.getRecordType()).isEqualTo(HeartRateRecord.class);
        }

        TestUtils.verifyDeleteRecords(recordIds);
        for (Record record : records) {
            TestUtils.assertRecordNotFound(record.getMetadata().getId(), record.getClass());
        }
    }

    @Test
    public void testDeleteHeartRateRecord_usingInvalidClientIds() throws InterruptedException {
        List<Record> records = List.of(getBaseHeartRateRecord(10), getCompleteHeartRateRecord());
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
            assertThat(recordIdFilter.getRecordType()).isEqualTo(HeartRateRecord.class);
        }

        TestUtils.verifyDeleteRecords(recordIds);
        for (Record record : records) {
            TestUtils.assertRecordFound(record.getMetadata().getId(), record.getClass());
        }
    }

    @Test
    public void testDeleteHeartRateRecord_time_range() throws InterruptedException {
        TimeInstantRangeFilter timeRangeFilter =
                new TimeInstantRangeFilter.Builder()
                        .setStartTime(Instant.now())
                        .setEndTime(Instant.now().plusMillis(1000))
                        .build();
        String id = TestUtils.insertRecordAndGetId(getCompleteHeartRateRecord());
        TestUtils.verifyDeleteRecords(HeartRateRecord.class, timeRangeFilter);
        TestUtils.assertRecordNotFound(id, HeartRateRecord.class);
    }

    @Test
    public void testZoneOffsets() {
        final ZoneOffset defaultZoneOffset =
                ZoneOffset.systemDefault().getRules().getOffset(Instant.now());
        final ZoneOffset startZoneOffset = ZoneOffset.UTC;
        final ZoneOffset endZoneOffset = ZoneOffset.MAX;
        Instant timeInstant = Instant.now().plusMillis(100);
        HeartRateRecord.HeartRateSample heartRateRecord =
                new HeartRateRecord.HeartRateSample(10, timeInstant);
        ArrayList<HeartRateRecord.HeartRateSample> heartRateRecords = new ArrayList<>();
        heartRateRecords.add(heartRateRecord);
        heartRateRecords.add(heartRateRecord);
        HeartRateRecord.Builder builder =
                new HeartRateRecord.Builder(
                        new Metadata.Builder().build(),
                        Instant.now(),
                        Instant.now().plusMillis(500),
                        heartRateRecords);

        assertThat(heartRateRecord.getTime()).isEqualTo(timeInstant);
        assertThat(heartRateRecord.getBeatsPerMinute()).isEqualTo(10);
        assertThat(builder.setStartZoneOffset(startZoneOffset).build().getStartZoneOffset())
                .isEqualTo(startZoneOffset);
        assertThat(builder.setEndZoneOffset(endZoneOffset).build().getEndZoneOffset())
                .isEqualTo(endZoneOffset);
        assertThat(builder.clearStartZoneOffset().build().getStartZoneOffset())
                .isEqualTo(defaultZoneOffset);
        assertThat(builder.clearEndZoneOffset().build().getEndZoneOffset())
                .isEqualTo(defaultZoneOffset);
    }

    @Test
    public void testBpmAggregation_timeRange_all() throws Exception {
        TestUtils.setupAggregation(PACKAGE_NAME, HealthDataCategory.VITALS);
        List<Record> records =
                Arrays.asList(
                        getBaseHeartRateRecord(71),
                        getBaseHeartRateRecord(72),
                        getBaseHeartRateRecord(73));
        AggregateRecordsResponse<Long> response =
                TestUtils.getAggregateResponse(
                        new AggregateRecordsRequest.Builder<Long>(
                                        new TimeInstantRangeFilter.Builder()
                                                .setStartTime(Instant.ofEpochMilli(0))
                                                .setEndTime(Instant.now().plus(1, ChronoUnit.DAYS))
                                                .build())
                                .addAggregationType(BPM_MAX)
                                .addAggregationType(BPM_MIN)
                                .addAggregationType(BPM_AVG)
                                .build(),
                        records);
        assertThat(response.get(BPM_MAX)).isNotNull();
        assertThat(response.get(BPM_MAX)).isEqualTo(73);
        assertThat(response.getZoneOffset(BPM_MAX))
                .isEqualTo(ZoneOffset.systemDefault().getRules().getOffset(Instant.now()));
        assertThat(response.get(BPM_MIN)).isNotNull();
        assertThat(response.get(BPM_MIN)).isEqualTo(71);
        assertThat(response.getZoneOffset(BPM_MIN))
                .isEqualTo(ZoneOffset.systemDefault().getRules().getOffset(Instant.now()));
        assertThat(response.get(BPM_AVG)).isNotNull();
        assertThat(response.get(BPM_AVG)).isEqualTo(72);
        assertThat(response.getZoneOffset(BPM_AVG))
                .isEqualTo(ZoneOffset.systemDefault().getRules().getOffset(Instant.now()));
        Set<DataOrigin> dataOrigins = response.getDataOrigins(BPM_AVG);
        for (DataOrigin itr : dataOrigins) {
            assertThat(itr.getPackageName()).isEqualTo("android.healthconnect.cts");
        }
        dataOrigins = response.getDataOrigins(BPM_MIN);
        for (DataOrigin itr : dataOrigins) {
            assertThat(itr.getPackageName()).isEqualTo("android.healthconnect.cts");
        }
        dataOrigins = response.getDataOrigins(BPM_MAX);
        for (DataOrigin itr : dataOrigins) {
            assertThat(itr.getPackageName()).isEqualTo("android.healthconnect.cts");
        }
    }

    @Test
    public void testBpmAggregation_timeRange_not_present() throws Exception {
        TestUtils.setupAggregation(PACKAGE_NAME, HealthDataCategory.VITALS);
        List<Record> records =
                Arrays.asList(
                        getHeartRateRecord(71), getHeartRateRecord(72), getHeartRateRecord(73));
        AggregateRecordsResponse<Long> response =
                TestUtils.getAggregateResponse(
                        new AggregateRecordsRequest.Builder<Long>(
                                        new TimeInstantRangeFilter.Builder()
                                                .setStartTime(Instant.now().plusMillis(1000))
                                                .setEndTime(Instant.now().plusMillis(2000))
                                                .build())
                                .addAggregationType(BPM_MAX)
                                .addAggregationType(BPM_MIN)
                                .addAggregationType(BPM_AVG)
                                .build(),
                        records);
        assertThat(response.get(BPM_MAX)).isNull();
        assertThat(response.getZoneOffset(BPM_MAX)).isNull();
        assertThat(response.get(BPM_MIN)).isNull();
        assertThat(response.getZoneOffset(BPM_MIN)).isNull();
        assertThat(response.get(BPM_AVG)).isNull();
        assertThat(response.getZoneOffset(BPM_AVG)).isNull();
    }

    @Test
    public void testBpmAggregation_withDataOrigin_correct() throws Exception {
        TestUtils.setupAggregation(PACKAGE_NAME, HealthDataCategory.VITALS);
        Context context = ApplicationProvider.getApplicationContext();
        List<Record> records =
                Arrays.asList(
                        getBaseHeartRateRecord(71),
                        getBaseHeartRateRecord(72),
                        getBaseHeartRateRecord(73));
        AggregateRecordsResponse<Long> response =
                TestUtils.getAggregateResponse(
                        new AggregateRecordsRequest.Builder<Long>(
                                        new TimeInstantRangeFilter.Builder()
                                                .setStartTime(Instant.ofEpochMilli(0))
                                                .setEndTime(Instant.now().plus(1, ChronoUnit.DAYS))
                                                .build())
                                .addAggregationType(BPM_MAX)
                                .addAggregationType(BPM_MIN)
                                .addAggregationType(BPM_AVG)
                                .addDataOriginsFilter(
                                        new DataOrigin.Builder()
                                                .setPackageName(context.getPackageName())
                                                .build())
                                .build(),
                        records);
        assertThat(response.get(BPM_MAX)).isNotNull();
        assertThat(response.get(BPM_MAX)).isEqualTo(73);
        assertThat(response.getZoneOffset(BPM_MAX))
                .isEqualTo(ZoneOffset.systemDefault().getRules().getOffset(Instant.now()));
        assertThat(response.get(BPM_MIN)).isNotNull();
        assertThat(response.get(BPM_MIN)).isEqualTo(71);
        assertThat(response.getZoneOffset(BPM_MIN))
                .isEqualTo(ZoneOffset.systemDefault().getRules().getOffset(Instant.now()));
        assertThat(response.get(BPM_AVG)).isNotNull();
        assertThat(response.get(BPM_AVG)).isEqualTo(72);
        assertThat(response.getZoneOffset(BPM_AVG))
                .isEqualTo(ZoneOffset.systemDefault().getRules().getOffset(Instant.now()));
    }

    @Test
    public void testBpmAggregation_withDataOrigin_incorrect() throws Exception {
        TestUtils.setupAggregation(PACKAGE_NAME, HealthDataCategory.VITALS);
        List<Record> records =
                Arrays.asList(
                        getHeartRateRecord(71), getHeartRateRecord(72), getHeartRateRecord(73));
        AggregateRecordsResponse<Long> response =
                TestUtils.getAggregateResponse(
                        new AggregateRecordsRequest.Builder<Long>(
                                        new TimeInstantRangeFilter.Builder()
                                                .setStartTime(Instant.ofEpochMilli(0))
                                                .setEndTime(Instant.now().plus(1, ChronoUnit.DAYS))
                                                .build())
                                .addAggregationType(BPM_MAX)
                                .addAggregationType(BPM_MIN)
                                .addAggregationType(BPM_AVG)
                                .addDataOriginsFilter(
                                        new DataOrigin.Builder().setPackageName("abc").build())
                                .build(),
                        records);
        assertThat(response.get(BPM_MAX)).isNull();
        assertThat(response.getZoneOffset(BPM_MAX)).isNull();
        assertThat(response.get(BPM_MIN)).isNull();
        assertThat(response.getZoneOffset(BPM_MIN)).isNull();
        assertThat(response.get(BPM_AVG)).isNull();
        assertThat(response.getZoneOffset(BPM_AVG)).isNull();
    }

    @Test
    public void testBpmAggregation_groupBy_Duration() throws Exception {
        TestUtils.setupAggregation(PACKAGE_NAME, HealthDataCategory.VITALS);

        Instant end = Instant.now();
        Instant start = end.minusSeconds(3);
        for (Instant instant = start.plusMillis(500);
                instant.isBefore(end);
                instant = instant.plusSeconds(1)) {
            List<Record> records =
                    Arrays.asList(
                            getBaseHeartRateRecord(71, instant, /* offset= */ null),
                            getBaseHeartRateRecord(72, instant.plusMillis(1), /* offset= */ null),
                            getBaseHeartRateRecord(73, instant.plusMillis(2), /* offset= */ null));
            TestUtils.insertRecords(records);
        }

        List<AggregateRecordsGroupedByDurationResponse<Long>> responses =
                TestUtils.getAggregateResponseGroupByDuration(
                        new AggregateRecordsRequest.Builder<Long>(
                                        new TimeInstantRangeFilter.Builder()
                                                .setStartTime(start)
                                                .setEndTime(end)
                                                .build())
                                .addAggregationType(BPM_MAX)
                                .addAggregationType(BPM_MIN)
                                .addAggregationType(BPM_AVG)
                                .build(),
                        Duration.ofSeconds(1));

        assertThat(responses).hasSize(3);
        for (AggregateRecordsGroupedByDurationResponse<Long> response : responses) {
            assertThat(response.get(BPM_MAX)).isNotNull();
            assertThat(response.get(BPM_MAX)).isEqualTo(73);
            assertThat(response.getZoneOffset(BPM_MAX))
                    .isEqualTo(ZoneOffset.systemDefault().getRules().getOffset(Instant.now()));
            assertThat(response.get(BPM_MIN)).isNotNull();
            assertThat(response.get(BPM_MIN)).isEqualTo(71);
            assertThat(response.getZoneOffset(BPM_MIN))
                    .isEqualTo(ZoneOffset.systemDefault().getRules().getOffset(Instant.now()));
            assertThat(response.get(BPM_AVG)).isNotNull();
            assertThat(response.get(BPM_AVG)).isEqualTo(72);
            assertThat(response.getZoneOffset(BPM_AVG))
                    .isEqualTo(ZoneOffset.systemDefault().getRules().getOffset(Instant.now()));
            assertThat(response.getStartTime()).isNotNull();
            assertThat(response.getEndTime()).isNotNull();
        }
    }

    @Test
    public void testBpmAggregation_groupByDuration() throws Exception {
        TestUtils.setupAggregation(PACKAGE_NAME, HealthDataCategory.VITALS);
        Instant start = Instant.now().minus(3, ChronoUnit.DAYS);
        Instant end = start.plus(3, ChronoUnit.DAYS);
        insertHeartRateRecordsInPastDays(4);
        List<AggregateRecordsGroupedByDurationResponse<Long>> responses =
                TestUtils.getAggregateResponseGroupByDuration(
                        new AggregateRecordsRequest.Builder<Long>(
                                        new TimeInstantRangeFilter.Builder()
                                                .setStartTime(start)
                                                .setEndTime(end)
                                                .build())
                                .addAggregationType(BPM_MAX)
                                .addAggregationType(BPM_MIN)
                                .build(),
                        Duration.ofDays(1));
        assertThat(responses.size()).isAtLeast(3);
        for (AggregateRecordsGroupedByDurationResponse<Long> response : responses) {
            if (start.toEpochMilli()
                    < response.getStartTime()
                            .atZone(ZoneOffset.systemDefault())
                            .toInstant()
                            .toEpochMilli()) {
                Long bpm = response.get(BPM_MAX);
                ZoneOffset zoneOffset = response.getZoneOffset(BPM_MAX);

                if (bpm == null) {
                    assertThat(zoneOffset).isNull();
                } else {
                    assertThat(zoneOffset).isNotNull();
                }
                // skip the check if our request doesn't fall with in the instant time we inserted
                // the record
                continue;
            }

            if (end.toEpochMilli()
                    > response.getEndTime()
                            .atZone(ZoneOffset.systemDefault())
                            .toInstant()
                            .toEpochMilli()) {
                Long bpm = response.get(BPM_MAX);
                ZoneOffset zoneOffset = response.getZoneOffset(BPM_MAX);

                if (bpm == null) {
                    assertThat(zoneOffset).isNull();
                } else {
                    assertThat(zoneOffset).isNotNull();
                }
                // skip the check if our request doesn't fall with in the instant time we inserted
                // the record
                continue;
            }

            assertThat(response.get(BPM_MAX)).isNotNull();
            assertThat(response.get(BPM_MAX)).isEqualTo(73);
            assertThat(response.getZoneOffset(BPM_MAX))
                    .isEqualTo(ZoneOffset.systemDefault().getRules().getOffset(Instant.now()));
            assertThat(response.get(BPM_MIN)).isNotNull();
            assertThat(response.get(BPM_MIN)).isEqualTo(71);
            assertThat(response.getZoneOffset(BPM_MIN))
                    .isEqualTo(ZoneOffset.systemDefault().getRules().getOffset(Instant.now()));
        }
    }

    @Test
    public void testHeartAggregation_measurement_count() throws Exception {
        TestUtils.setupAggregation(PACKAGE_NAME, HealthDataCategory.VITALS);
        List<Record> records =
                Arrays.asList(
                        getBaseHeartRateRecord(71),
                        getBaseHeartRateRecord(72),
                        getBaseHeartRateRecord(73));
        AggregateRecordsResponse<Long> response =
                TestUtils.getAggregateResponse(
                        new AggregateRecordsRequest.Builder<Long>(
                                        new TimeInstantRangeFilter.Builder()
                                                .setStartTime(Instant.ofEpochMilli(0))
                                                .setEndTime(Instant.now().plus(1, ChronoUnit.DAYS))
                                                .build())
                                .addAggregationType(HEART_MEASUREMENTS_COUNT)
                                .addAggregationType(BPM_MAX)
                                .build(),
                        records);
        List<Record> recordsNew =
                Arrays.asList(
                        getBaseHeartRateRecord(71),
                        getBaseHeartRateRecord(72),
                        getBaseHeartRateRecord(73));
        AggregateRecordsResponse<Long> newResponse =
                TestUtils.getAggregateResponse(
                        new AggregateRecordsRequest.Builder<Long>(
                                        new TimeInstantRangeFilter.Builder()
                                                .setStartTime(Instant.ofEpochMilli(0))
                                                .setEndTime(Instant.now().plus(1, ChronoUnit.DAYS))
                                                .build())
                                .addAggregationType(HEART_MEASUREMENTS_COUNT)
                                .build(),
                        recordsNew);
        assertThat(newResponse.get(HEART_MEASUREMENTS_COUNT)).isNotNull();
        assertThat(response.get(HEART_MEASUREMENTS_COUNT)).isNotNull();
        assertThat(
                        newResponse.get(HEART_MEASUREMENTS_COUNT)
                                - response.get(HEART_MEASUREMENTS_COUNT))
                .isEqualTo(6);
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
                                .addRecordType(HeartRateRecord.class)
                                .build());
        ChangeLogsRequest changeLogsRequest =
                new ChangeLogsRequest.Builder(tokenResponse.getToken()).build();
        ChangeLogsResponse response = TestUtils.getChangeLogs(changeLogsRequest);
        assertThat(response.getUpsertedRecords().size()).isEqualTo(0);
        assertThat(response.getDeletedLogs().size()).isEqualTo(0);

        List<Record> testRecord = Collections.singletonList(getCompleteHeartRateRecord());
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
                new DeleteUsingFiltersRequest.Builder()
                        .addRecordType(HeartRateRecord.class)
                        .build());
        response = TestUtils.getChangeLogs(changeLogsRequest);
        assertThat(response.getDeletedLogs()).isEmpty();
    }

    @Test
    public void testUpdateRecords_validInput_dataBaseUpdatedSuccessfully()
            throws InterruptedException {

        List<Record> insertedRecords =
                TestUtils.insertRecords(
                        Arrays.asList(getCompleteHeartRateRecord(), getCompleteHeartRateRecord()));

        // read inserted records and verify that the data is same as inserted.
        readHeartRateRecordUsingIds(insertedRecords);

        // Generate a new set of records that will be used to perform the update operation.
        List<Record> updateRecords =
                Arrays.asList(getCompleteHeartRateRecord(), getCompleteHeartRateRecord());

        // Modify the uid of the updateRecords to the uuid that was present in the insert records.
        for (int itr = 0; itr < updateRecords.size(); itr++) {
            updateRecords.set(
                    itr,
                    getHeartRateRecord_update(
                            updateRecords.get(itr),
                            insertedRecords.get(itr).getMetadata().getId(),
                            insertedRecords.get(itr).getMetadata().getClientRecordId()));
        }

        TestUtils.updateRecords(updateRecords);

        // assert the inserted data has been modified by reading the data.
        readHeartRateRecordUsingIds(updateRecords);
    }

    @Test
    public void testUpdateRecords_invalidInputRecords_noChangeInDataBase()
            throws InterruptedException {
        List<Record> insertedRecords =
                TestUtils.insertRecords(
                        Arrays.asList(getCompleteHeartRateRecord(), getCompleteHeartRateRecord()));

        // read inserted records and verify that the data is same as inserted.
        readHeartRateRecordUsingIds(insertedRecords);

        // Generate a second set of records that will be used to perform the update operation.
        List<Record> updateRecords =
                Arrays.asList(getCompleteHeartRateRecord(), getCompleteHeartRateRecord());

        // Modify the Uid of the updateRecords to the UUID that was present in the insert records,
        // leaving out alternate records so that they have a new UUID which is not present in the
        // dataBase.
        for (int itr = 0; itr < updateRecords.size(); itr++) {
            updateRecords.set(
                    itr,
                    getHeartRateRecord_update(
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
            assertThat(exception.getErrorCode())
                    .isEqualTo(HealthConnectException.ERROR_INVALID_ARGUMENT);
        }

        // assert the inserted data has not been modified by reading the data.
        readHeartRateRecordUsingIds(insertedRecords);
    }

    @Test
    public void testAggregateLocalFilter_minOffsetRecord() throws Exception {
        TestUtils.setupAggregation(PACKAGE_NAME, HealthDataCategory.VITALS);
        LocalDateTime endTimeLocal = LocalDateTime.now(ZoneOffset.UTC);
        Instant endTimeInstant = Instant.now();

        AggregateRecordsResponse<Long> response =
                TestUtils.getAggregateResponse(
                        new AggregateRecordsRequest.Builder<Long>(
                                        new LocalTimeRangeFilter.Builder()
                                                .setStartTime(endTimeLocal.minusHours(25))
                                                .setEndTime(endTimeLocal.minusHours(15))
                                                .build())
                                .addAggregationType(BPM_AVG)
                                .addAggregationType(BPM_MAX)
                                .addAggregationType(BPM_MIN)
                                .build(),
                        List.of(
                                getBaseHeartRateRecord(
                                        70, endTimeInstant.minusSeconds(500), ZoneOffset.MIN),
                                getBaseHeartRateRecord(
                                        130, endTimeInstant.minusSeconds(1500), ZoneOffset.MIN)));

        assertThat(response.get(BPM_MAX)).isNotNull();
        assertThat(response.get(BPM_MAX)).isEqualTo(130);
        assertThat(response.get(BPM_MIN)).isNotNull();
        assertThat(response.get(BPM_MIN)).isEqualTo(70);
        assertThat(response.get(BPM_AVG)).isNotNull();
        assertThat(response.get(BPM_AVG)).isEqualTo(100);
    }

    @Test
    public void testUpdateRecords_recordWithInvalidPackageName_noChangeInDataBase()
            throws InterruptedException {
        List<Record> insertedRecords =
                TestUtils.insertRecords(
                        Arrays.asList(getCompleteHeartRateRecord(), getCompleteHeartRateRecord()));

        // read inserted records and verify that the data is same as inserted.
        readHeartRateRecordUsingIds(insertedRecords);

        // Generate a second set of records that will be used to perform the update operation.
        List<Record> updateRecords =
                Arrays.asList(getCompleteHeartRateRecord(), getCompleteHeartRateRecord());

        // Modify the Uuid of the updateRecords to the uuid that was present in the insert records.
        for (int itr = 0; itr < updateRecords.size(); itr++) {
            updateRecords.set(
                    itr,
                    getHeartRateRecord_update(
                            updateRecords.get(itr),
                            insertedRecords.get(itr).getMetadata().getId(),
                            insertedRecords.get(itr).getMetadata().getClientRecordId()));
            //             adding an entry with invalid packageName.
            updateRecords.set(itr, getCompleteHeartRateRecord());
        }

        try {
            TestUtils.updateRecords(updateRecords);
            Assert.fail("Expected to fail due to invalid package.");
        } catch (Exception exception) {
            // verify that the testcase failed due to invalid argument exception.
            assertThat(exception).isNotNull();
        }

        // assert the inserted data has not been modified by reading the data.
        readHeartRateRecordUsingIds(insertedRecords);
    }

    private void testReadHeartRateRecordIds() throws InterruptedException {
        List<Record> recordList =
                Arrays.asList(getCompleteHeartRateRecord(), getCompleteHeartRateRecord());
        readHeartRateRecordUsingIds(recordList);
    }

    private void readHeartRateRecordUsingClientId(List<Record> insertedRecord)
            throws InterruptedException {
        ReadRecordsRequestUsingIds.Builder<HeartRateRecord> request =
                new ReadRecordsRequestUsingIds.Builder<>(HeartRateRecord.class);
        for (Record record : insertedRecord) {
            request.addClientRecordId(record.getMetadata().getClientRecordId());
        }
        List<HeartRateRecord> result = TestUtils.readRecords(request.build());
        assertThat(result.size()).isEqualTo(insertedRecord.size());
        assertThat(result).containsExactlyElementsIn(insertedRecord);
    }

    private void readHeartRateRecordUsingIds(List<Record> recordList) throws InterruptedException {
        List<Record> insertedRecords = TestUtils.insertRecords(recordList);
        ReadRecordsRequestUsingIds.Builder<HeartRateRecord> request =
                new ReadRecordsRequestUsingIds.Builder<>(HeartRateRecord.class);
        for (Record record : insertedRecords) {
            request.addId(record.getMetadata().getId());
        }
        ReadRecordsRequestUsingIds requestUsingIds = request.build();
        assertThat(requestUsingIds.getRecordType()).isEqualTo(HeartRateRecord.class);
        assertThat(requestUsingIds.getRecordIdFilters()).isNotNull();
        List<HeartRateRecord> result = TestUtils.readRecords(requestUsingIds);
        assertThat(result.containsAll(insertedRecords)).isTrue();
    }

    private void insertHeartRateRecordsInPastDays(int numDays) throws InterruptedException {
        for (int i = numDays; i > 0; i--) {
            List<Record> records =
                    Arrays.asList(
                            getHeartRateRecord(71, Instant.now().minus(i, ChronoUnit.DAYS)),
                            getHeartRateRecord(72, Instant.now().minus(i, ChronoUnit.DAYS)),
                            getHeartRateRecord(73, Instant.now().minus(i, ChronoUnit.DAYS)));

            TestUtils.insertRecords(records);
        }
    }

    HeartRateRecord getHeartRateRecord_update(Record record, String id, String clientRecordId) {
        Metadata metadata = record.getMetadata();
        Metadata metadataWithId =
                new Metadata.Builder()
                        .setId(id)
                        .setClientRecordId(clientRecordId)
                        .setClientRecordVersion(metadata.getClientRecordVersion())
                        .setDataOrigin(metadata.getDataOrigin())
                        .setDevice(metadata.getDevice())
                        .setLastModifiedTime(metadata.getLastModifiedTime())
                        .build();

        HeartRateRecord.HeartRateSample heartRateRecordSample =
                new HeartRateRecord.HeartRateSample(8, Instant.now().plusMillis(100));

        return new HeartRateRecord.Builder(
                        metadataWithId,
                        Instant.now(),
                        Instant.now().plusMillis(2000),
                        List.of(heartRateRecordSample, heartRateRecordSample))
                .setStartZoneOffset(ZoneOffset.systemDefault().getRules().getOffset(Instant.now()))
                .setEndZoneOffset(ZoneOffset.systemDefault().getRules().getOffset(Instant.now()))
                .build();
    }

    private static HeartRateRecord getBaseHeartRateRecord(long beatsPerMinute) {
        return getBaseHeartRateRecord(beatsPerMinute, Instant.now(), null);
    }

    private static HeartRateRecord getBaseHeartRateRecord(
            long beatsPerMinute, Instant time, ZoneOffset offset) {
        HeartRateRecord.HeartRateSample heartRateRecord =
                new HeartRateRecord.HeartRateSample(beatsPerMinute, time.plusMillis(100));
        ArrayList<HeartRateRecord.HeartRateSample> heartRateRecords = new ArrayList<>();
        heartRateRecords.add(heartRateRecord);
        heartRateRecords.add(heartRateRecord);

        HeartRateRecord.Builder builder =
                new HeartRateRecord.Builder(
                        new Metadata.Builder().setClientRecordId("HRR" + Math.random()).build(),
                        time,
                        time.plusMillis(500),
                        heartRateRecords);

        if (offset != null) {
            builder.setStartZoneOffset(offset).setEndZoneOffset(offset);
        } else {
            ZoneOffset currentOffset =
                    ZoneOffset.systemDefault().getRules().getOffset(Instant.now());
            builder.setStartZoneOffset(currentOffset).setEndZoneOffset(currentOffset);
        }
        return builder.build();
    }

    private static HeartRateRecord getCompleteHeartRateRecord() {
        Device device =
                new Device.Builder()
                        .setManufacturer("google")
                        .setModel("Pixel4a")
                        .setType(2)
                        .build();
        DataOrigin dataOrigin =
                new DataOrigin.Builder().setPackageName("android.healthconnect.cts").build();
        Metadata.Builder testMetadataBuilder = new Metadata.Builder();
        testMetadataBuilder.setDevice(device).setDataOrigin(dataOrigin);
        testMetadataBuilder.setClientRecordId("HRR" + Math.random());
        testMetadataBuilder.setRecordingMethod(Metadata.RECORDING_METHOD_ACTIVELY_RECORDED);

        Instant start = Instant.now();
        HeartRateRecord.HeartRateSample heartRateRecord =
                new HeartRateRecord.HeartRateSample(10, start.plusMillis(100));

        ArrayList<HeartRateRecord.HeartRateSample> heartRateRecords = new ArrayList<>();
        heartRateRecords.add(heartRateRecord);
        heartRateRecords.add(heartRateRecord);

        return new HeartRateRecord.Builder(
                        testMetadataBuilder.build(), start, start.plusMillis(500), heartRateRecords)
                .build();
    }
}
