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

import static android.health.connect.datatypes.CyclingPedalingCadenceRecord.RPM_AVG;
import static android.health.connect.datatypes.CyclingPedalingCadenceRecord.RPM_MAX;
import static android.health.connect.datatypes.CyclingPedalingCadenceRecord.RPM_MIN;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.health.connect.AggregateRecordsRequest;
import android.health.connect.AggregateRecordsResponse;
import android.health.connect.DeleteUsingFiltersRequest;
import android.health.connect.HealthConnectException;
import android.health.connect.HealthDataCategory;
import android.health.connect.ReadRecordsRequestUsingFilters;
import android.health.connect.ReadRecordsRequestUsingIds;
import android.health.connect.RecordIdFilter;
import android.health.connect.TimeInstantRangeFilter;
import android.health.connect.changelog.ChangeLogTokenRequest;
import android.health.connect.changelog.ChangeLogTokenResponse;
import android.health.connect.changelog.ChangeLogsRequest;
import android.health.connect.changelog.ChangeLogsResponse;
import android.health.connect.datatypes.AggregationType;
import android.health.connect.datatypes.CyclingPedalingCadenceRecord;
import android.health.connect.datatypes.DataOrigin;
import android.health.connect.datatypes.Device;
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

import java.time.Instant;
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
public class CyclingPedalingCadenceRecordTest {

    private static final String TAG = "CyclingPedalingCadenceRecordTest";
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
                CyclingPedalingCadenceRecord.class,
                new TimeInstantRangeFilter.Builder()
                        .setStartTime(Instant.EPOCH)
                        .setEndTime(Instant.now())
                        .build());
        TestUtils.deleteAllStagedRemoteData();
    }

    @Test
    public void testInsertCyclingPedalingCadenceRecord() throws InterruptedException {
        CyclingPedalingCadenceRecord baseCyclingPedalingCadenceRecord =
                getBaseCyclingPedalingCadenceRecord();

        assertThat(baseCyclingPedalingCadenceRecord.getSamples().get(0).getTime()).isNotNull();
        assertThat(baseCyclingPedalingCadenceRecord.getSamples().get(0).getRevolutionsPerMinute())
                .isNotNull();
        TestUtils.insertRecords(
                Arrays.asList(
                        baseCyclingPedalingCadenceRecord,
                        getCompleteCyclingPedalingCadenceRecord()));
    }

    @Test
    public void testReadCyclingPedalingCadenceRecord_usingIds() throws InterruptedException {
        testReadCyclingPedalingCadenceRecordIds();
    }

    @Test
    public void testReadCyclingPedalingCadenceRecord_invalidIds() throws InterruptedException {
        ReadRecordsRequestUsingIds<CyclingPedalingCadenceRecord> request =
                new ReadRecordsRequestUsingIds.Builder<>(CyclingPedalingCadenceRecord.class)
                        .addId(UUID.randomUUID().toString())
                        .build();
        List<CyclingPedalingCadenceRecord> result = TestUtils.readRecords(request);
        assertThat(result.size()).isEqualTo(0);
    }

    @Test
    public void testReadCyclingPedalingCadenceRecord_usingClientRecordIds()
            throws InterruptedException {
        List<Record> recordList =
                Arrays.asList(
                        getCompleteCyclingPedalingCadenceRecord(),
                        getCompleteCyclingPedalingCadenceRecord());
        List<Record> insertedRecords = TestUtils.insertRecords(recordList);
        readCyclingPedalingCadenceRecordUsingClientId(insertedRecords);
    }

    @Test
    public void testReadCyclingPedalingCadenceRecord_invalidClientRecordIds()
            throws InterruptedException {
        ReadRecordsRequestUsingIds<CyclingPedalingCadenceRecord> request =
                new ReadRecordsRequestUsingIds.Builder<>(CyclingPedalingCadenceRecord.class)
                        .addClientRecordId("abc")
                        .build();
        List<CyclingPedalingCadenceRecord> result = TestUtils.readRecords(request);
        assertThat(result.size()).isEqualTo(0);
    }

    @Test
    public void testReadCyclingPedalingCadenceRecordUsingFilters_default()
            throws InterruptedException {
        List<CyclingPedalingCadenceRecord> oldCyclingPedalingCadenceRecords =
                TestUtils.readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(
                                        CyclingPedalingCadenceRecord.class)
                                .build());
        CyclingPedalingCadenceRecord testRecord = getCompleteCyclingPedalingCadenceRecord();
        TestUtils.insertRecords(Collections.singletonList(testRecord));
        List<CyclingPedalingCadenceRecord> newCyclingPedalingCadenceRecords =
                TestUtils.readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(
                                        CyclingPedalingCadenceRecord.class)
                                .build());
        assertThat(newCyclingPedalingCadenceRecords.size())
                .isEqualTo(oldCyclingPedalingCadenceRecords.size() + 1);
        assertThat(
                        newCyclingPedalingCadenceRecords
                                .get(newCyclingPedalingCadenceRecords.size() - 1)
                                .equals(testRecord))
                .isTrue();
    }

    @Test
    public void testReadCyclingPedalingCadenceRecordUsingFilters_timeFilter()
            throws InterruptedException {
        TimeInstantRangeFilter filter =
                new TimeInstantRangeFilter.Builder()
                        .setStartTime(Instant.now())
                        .setEndTime(Instant.now().plusMillis(3000))
                        .build();
        CyclingPedalingCadenceRecord testRecord = getCompleteCyclingPedalingCadenceRecord();
        TestUtils.insertRecords(Collections.singletonList(testRecord));
        List<CyclingPedalingCadenceRecord> newCyclingPedalingCadenceRecords =
                TestUtils.readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(
                                        CyclingPedalingCadenceRecord.class)
                                .setTimeRangeFilter(filter)
                                .build());
        assertThat(newCyclingPedalingCadenceRecords.size()).isEqualTo(1);
        assertThat(
                        newCyclingPedalingCadenceRecords
                                .get(newCyclingPedalingCadenceRecords.size() - 1)
                                .equals(testRecord))
                .isTrue();
    }

    @Test
    public void testReadCyclingPedalingCadenceRecordUsingFilters_dataFilter_correct()
            throws InterruptedException {
        Context context = ApplicationProvider.getApplicationContext();
        List<CyclingPedalingCadenceRecord> oldCyclingPedalingCadenceRecords =
                TestUtils.readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(
                                        CyclingPedalingCadenceRecord.class)
                                .addDataOrigins(
                                        new DataOrigin.Builder()
                                                .setPackageName(context.getPackageName())
                                                .build())
                                .build());
        CyclingPedalingCadenceRecord testRecord = getCompleteCyclingPedalingCadenceRecord();
        TestUtils.insertRecords(Collections.singletonList(testRecord));
        List<CyclingPedalingCadenceRecord> newCyclingPedalingCadenceRecords =
                TestUtils.readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(
                                        CyclingPedalingCadenceRecord.class)
                                .addDataOrigins(
                                        new DataOrigin.Builder()
                                                .setPackageName(context.getPackageName())
                                                .build())
                                .build());
        assertThat(
                        newCyclingPedalingCadenceRecords.size()
                                - oldCyclingPedalingCadenceRecords.size())
                .isEqualTo(1);
        assertThat(
                        newCyclingPedalingCadenceRecords
                                .get(newCyclingPedalingCadenceRecords.size() - 1)
                                .equals(testRecord))
                .isTrue();

        CyclingPedalingCadenceRecord newRecord =
                newCyclingPedalingCadenceRecords.get(newCyclingPedalingCadenceRecords.size() - 1);
        for (int idx = 0; idx < newRecord.getSamples().size(); idx++) {
            assertThat(newRecord.getSamples().get(idx).getTime().toEpochMilli())
                    .isEqualTo(testRecord.getSamples().get(idx).getTime().toEpochMilli());
            assertThat(newRecord.getSamples().get(idx).getRevolutionsPerMinute())
                    .isEqualTo(testRecord.getSamples().get(idx).getRevolutionsPerMinute());
        }
    }

    @Test
    public void testReadCyclingPedalingCadenceRecordUsingFilters_dataFilter_incorrect()
            throws InterruptedException {
        TestUtils.insertRecords(
                Collections.singletonList(getCompleteCyclingPedalingCadenceRecord()));
        List<CyclingPedalingCadenceRecord> newCyclingPedalingCadenceRecords =
                TestUtils.readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(
                                        CyclingPedalingCadenceRecord.class)
                                .addDataOrigins(
                                        new DataOrigin.Builder().setPackageName("abc").build())
                                .build());
        assertThat(newCyclingPedalingCadenceRecords.size()).isEqualTo(0);
    }

    @Test
    public void testDeleteCyclingPedalingCadenceRecord_no_filters() throws InterruptedException {
        String id = TestUtils.insertRecordAndGetId(getCompleteCyclingPedalingCadenceRecord());
        TestUtils.verifyDeleteRecords(new DeleteUsingFiltersRequest.Builder().build());
        TestUtils.assertRecordNotFound(id, CyclingPedalingCadenceRecord.class);
    }

    @Test
    public void testDeleteCyclingPedalingCadenceRecord_time_filters() throws InterruptedException {
        TimeInstantRangeFilter timeRangeFilter =
                new TimeInstantRangeFilter.Builder()
                        .setStartTime(Instant.now())
                        .setEndTime(Instant.now().plusMillis(1000))
                        .build();
        String id = TestUtils.insertRecordAndGetId(getCompleteCyclingPedalingCadenceRecord());
        TestUtils.verifyDeleteRecords(
                new DeleteUsingFiltersRequest.Builder()
                        .addRecordType(CyclingPedalingCadenceRecord.class)
                        .setTimeRangeFilter(timeRangeFilter)
                        .build());
        TestUtils.assertRecordNotFound(id, CyclingPedalingCadenceRecord.class);
    }

    @Test
    public void testDeleteCyclingPedalingCadenceRecord_recordId_filters()
            throws InterruptedException {
        List<Record> records =
                List.of(
                        getBaseCyclingPedalingCadenceRecord(),
                        getCompleteCyclingPedalingCadenceRecord());
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
    public void testDeleteCyclingPedalingCadenceRecord_dataOrigin_filters()
            throws InterruptedException {
        Context context = ApplicationProvider.getApplicationContext();
        String id = TestUtils.insertRecordAndGetId(getCompleteCyclingPedalingCadenceRecord());
        TestUtils.verifyDeleteRecords(
                new DeleteUsingFiltersRequest.Builder()
                        .addDataOrigin(
                                new DataOrigin.Builder()
                                        .setPackageName(context.getPackageName())
                                        .build())
                        .build());
        TestUtils.assertRecordNotFound(id, CyclingPedalingCadenceRecord.class);
    }

    @Test
    public void testDeleteCyclingPedalingCadenceRecord_dataOrigin_filter_incorrect()
            throws InterruptedException {
        String id = TestUtils.insertRecordAndGetId(getCompleteCyclingPedalingCadenceRecord());
        TestUtils.verifyDeleteRecords(
                new DeleteUsingFiltersRequest.Builder()
                        .addDataOrigin(new DataOrigin.Builder().setPackageName("abc").build())
                        .build());
        TestUtils.assertRecordFound(id, CyclingPedalingCadenceRecord.class);
    }

    @Test
    public void testDeleteCyclingPedalingCadenceRecord_usingIds() throws InterruptedException {
        List<Record> records =
                List.of(
                        getBaseCyclingPedalingCadenceRecord(),
                        getCompleteCyclingPedalingCadenceRecord());
        List<Record> insertedRecords = TestUtils.insertRecords(records);
        List<RecordIdFilter> recordIds = new ArrayList<>(records.size());
        for (Record record : insertedRecords) {
            recordIds.add(RecordIdFilter.fromId(record.getClass(), record.getMetadata().getId()));
        }

        TestUtils.verifyDeleteRecords(recordIds);
        for (Record record : records) {
            TestUtils.assertRecordNotFound(record.getMetadata().getId(), record.getClass());
        }
    }

    @Test
    public void testDeleteCyclingPedalingCadenceRecord_time_range() throws InterruptedException {
        TimeInstantRangeFilter timeRangeFilter =
                new TimeInstantRangeFilter.Builder()
                        .setStartTime(Instant.now())
                        .setEndTime(Instant.now().plusMillis(1000))
                        .build();
        String id = TestUtils.insertRecordAndGetId(getCompleteCyclingPedalingCadenceRecord());
        TestUtils.verifyDeleteRecords(CyclingPedalingCadenceRecord.class, timeRangeFilter);
        TestUtils.assertRecordNotFound(id, CyclingPedalingCadenceRecord.class);
    }

    @Test
    public void testZoneOffsets() {
        final ZoneOffset defaultZoneOffset =
                ZoneOffset.systemDefault().getRules().getOffset(Instant.now());
        final ZoneOffset startZoneOffset = ZoneOffset.UTC;
        final ZoneOffset endZoneOffset = ZoneOffset.MAX;
        CyclingPedalingCadenceRecord.Builder builder =
                new CyclingPedalingCadenceRecord.Builder(
                        new Metadata.Builder().build(),
                        Instant.now(),
                        Instant.now().plusMillis(1000),
                        Collections.emptyList());

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
    public void testUpdateRecords_validInput_dataBaseUpdatedSuccessfully()
            throws InterruptedException {

        List<Record> insertedRecords =
                TestUtils.insertRecords(
                        Arrays.asList(
                                getCompleteCyclingPedalingCadenceRecord(),
                                getCompleteCyclingPedalingCadenceRecord()));

        // read inserted records and verify that the data is same as inserted.
        readCyclingPedalingCadenceRecordUsingIds(insertedRecords);

        // Generate a new set of records that will be used to perform the update operation.
        List<Record> updateRecords =
                Arrays.asList(
                        getCompleteCyclingPedalingCadenceRecord(),
                        getCompleteCyclingPedalingCadenceRecord());

        // Modify the uid of the updateRecords to the uuid that was present in the insert records.
        for (int itr = 0; itr < updateRecords.size(); itr++) {
            updateRecords.set(
                    itr,
                    getCyclingPedalingCadenceRecord_update(
                            updateRecords.get(itr),
                            insertedRecords.get(itr).getMetadata().getId(),
                            insertedRecords.get(itr).getMetadata().getClientRecordId()));
        }

        TestUtils.updateRecords(updateRecords);

        // assert the inserted data has been modified by reading the data.
        readCyclingPedalingCadenceRecordUsingIds(updateRecords);
    }

    @Test
    public void testUpdateRecords_invalidInputRecords_noChangeInDataBase()
            throws InterruptedException {
        List<Record> insertedRecords =
                TestUtils.insertRecords(
                        Arrays.asList(
                                getCompleteCyclingPedalingCadenceRecord(),
                                getCompleteCyclingPedalingCadenceRecord()));

        // read inserted records and verify that the data is same as inserted.
        readCyclingPedalingCadenceRecordUsingIds(insertedRecords);

        // Generate a second set of records that will be used to perform the update operation.
        List<Record> updateRecords =
                Arrays.asList(
                        getCompleteCyclingPedalingCadenceRecord(),
                        getCompleteCyclingPedalingCadenceRecord());

        // Modify the Uid of the updateRecords to the UUID that was present in the insert records,
        // leaving out alternate records so that they have a new UUID which is not present in the
        // dataBase.
        for (int itr = 0; itr < updateRecords.size(); itr++) {
            updateRecords.set(
                    itr,
                    getCyclingPedalingCadenceRecord_update(
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
        readCyclingPedalingCadenceRecordUsingIds(insertedRecords);
    }

    @Test
    public void testUpdateRecords_recordWithInvalidPackageName_noChangeInDataBase()
            throws InterruptedException {
        List<Record> insertedRecords =
                TestUtils.insertRecords(
                        Arrays.asList(
                                getCompleteCyclingPedalingCadenceRecord(),
                                getCompleteCyclingPedalingCadenceRecord()));

        // read inserted records and verify that the data is same as inserted.
        readCyclingPedalingCadenceRecordUsingIds(insertedRecords);

        // Generate a second set of records that will be used to perform the update operation.
        List<Record> updateRecords =
                Arrays.asList(
                        getCompleteCyclingPedalingCadenceRecord(),
                        getCompleteCyclingPedalingCadenceRecord());

        // Modify the Uuid of the updateRecords to the uuid that was present in the insert records.
        for (int itr = 0; itr < updateRecords.size(); itr++) {
            updateRecords.set(
                    itr,
                    getCyclingPedalingCadenceRecord_update(
                            updateRecords.get(itr),
                            insertedRecords.get(itr).getMetadata().getId(),
                            insertedRecords.get(itr).getMetadata().getClientRecordId()));
            //             adding an entry with invalid packageName.
            updateRecords.set(itr, getCompleteCyclingPedalingCadenceRecord());
        }

        try {
            TestUtils.updateRecords(updateRecords);
            Assert.fail("Expected to fail due to invalid package.");
        } catch (Exception exception) {
            // verify that the testcase failed due to invalid argument exception.
            assertThat(exception).isNotNull();
        }

        // assert the inserted data has not been modified by reading the data.
        readCyclingPedalingCadenceRecordUsingIds(insertedRecords);
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
                                .addRecordType(CyclingPedalingCadenceRecord.class)
                                .build());
        ChangeLogsRequest changeLogsRequest =
                new ChangeLogsRequest.Builder(tokenResponse.getToken()).build();
        ChangeLogsResponse response = TestUtils.getChangeLogs(changeLogsRequest);
        assertThat(response.getUpsertedRecords().size()).isEqualTo(0);
        assertThat(response.getDeletedLogs().size()).isEqualTo(0);

        List<Record> testRecord =
                Collections.singletonList(getCompleteCyclingPedalingCadenceRecord());
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
                        .addRecordType(CyclingPedalingCadenceRecord.class)
                        .build());
        response = TestUtils.getChangeLogs(changeLogsRequest);
        assertThat(response.getDeletedLogs()).isEmpty();
    }

    @Test
    public void testRpmAggregation_getAggregationFromThreerecords_aggResponsesAreCorrect()
            throws Exception {
        TestUtils.setupAggregation(PACKAGE_NAME, HealthDataCategory.ACTIVITY);
        List<Record> records =
                Arrays.asList(
                        buildRecordForRpm(120, 100),
                        buildRecordForRpm(100, 101),
                        buildRecordForRpm(80, 102));
        AggregateRecordsResponse<Double> response =
                TestUtils.getAggregateResponse(
                        new AggregateRecordsRequest.Builder<Double>(
                                        new TimeInstantRangeFilter.Builder()
                                                .setStartTime(Instant.ofEpochMilli(0))
                                                .setEndTime(Instant.now().plus(1, ChronoUnit.DAYS))
                                                .build())
                                .addAggregationType(RPM_MAX)
                                .addAggregationType(RPM_MIN)
                                .addAggregationType(RPM_AVG)
                                .build(),
                        records);
        checkAggregationResult(RPM_MIN, 80, response);
        checkAggregationResult(RPM_AVG, 100, response);
        checkAggregationResult(RPM_MAX, 120, response);
    }

    private void checkAggregationResult(
            AggregationType<Double> type,
            double expectedResult,
            AggregateRecordsResponse<Double> response) {
        assertThat(response.get(type)).isNotNull();
        assertThat(response.get(type)).isEqualTo(expectedResult);
        assertThat(response.getZoneOffset(type))
                .isEqualTo(ZoneOffset.systemDefault().getRules().getOffset(Instant.now()));
        Set<DataOrigin> dataOrigins = response.getDataOrigins(type);
        assertThat(dataOrigins).hasSize(1);
        for (DataOrigin itr : dataOrigins) {
            assertThat(itr.getPackageName()).isEqualTo("android.healthconnect.cts");
        }
    }

    private void testReadCyclingPedalingCadenceRecordIds() throws InterruptedException {
        List<Record> recordList =
                Arrays.asList(
                        getCompleteCyclingPedalingCadenceRecord(),
                        getCompleteCyclingPedalingCadenceRecord());
        readCyclingPedalingCadenceRecordUsingIds(recordList);
    }

    private void readCyclingPedalingCadenceRecordUsingClientId(List<Record> insertedRecords)
            throws InterruptedException {
        ReadRecordsRequestUsingIds.Builder<CyclingPedalingCadenceRecord> request =
                new ReadRecordsRequestUsingIds.Builder<>(CyclingPedalingCadenceRecord.class);
        for (Record record : insertedRecords) {
            request.addClientRecordId(record.getMetadata().getClientRecordId());
        }
        List<CyclingPedalingCadenceRecord> result = TestUtils.readRecords(request.build());
        assertThat(result.containsAll(insertedRecords)).isTrue();
    }

    private void readCyclingPedalingCadenceRecordUsingIds(List<Record> recordList)
            throws InterruptedException {
        List<Record> insertedRecords = TestUtils.insertRecords(recordList);
        ReadRecordsRequestUsingIds.Builder<CyclingPedalingCadenceRecord> request =
                new ReadRecordsRequestUsingIds.Builder<>(CyclingPedalingCadenceRecord.class);
        for (Record record : insertedRecords) {
            request.addId(record.getMetadata().getId());
        }
        ReadRecordsRequestUsingIds requestUsingIds = request.build();
        assertThat(requestUsingIds.getRecordType()).isEqualTo(CyclingPedalingCadenceRecord.class);
        assertThat(requestUsingIds.getRecordIdFilters()).isNotNull();
        List<CyclingPedalingCadenceRecord> result = TestUtils.readRecords(requestUsingIds);
        assertThat(result).hasSize(insertedRecords.size());
        assertThat(result.containsAll(insertedRecords)).isTrue();
    }

    CyclingPedalingCadenceRecord getCyclingPedalingCadenceRecord_update(
            Record record, String id, String clientRecordId) {
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

        CyclingPedalingCadenceRecord.CyclingPedalingCadenceRecordSample
                cyclingPedalingCadenceRecordSample =
                        new CyclingPedalingCadenceRecord.CyclingPedalingCadenceRecordSample(
                                8, Instant.now().plusMillis(100));

        return new CyclingPedalingCadenceRecord.Builder(
                        metadataWithId,
                        Instant.now(),
                        Instant.now().plusMillis(2000),
                        List.of(
                                cyclingPedalingCadenceRecordSample,
                                cyclingPedalingCadenceRecordSample))
                .setStartZoneOffset(ZoneOffset.systemDefault().getRules().getOffset(Instant.now()))
                .setEndZoneOffset(ZoneOffset.systemDefault().getRules().getOffset(Instant.now()))
                .build();
    }

    private static CyclingPedalingCadenceRecord getBaseCyclingPedalingCadenceRecord() {
        CyclingPedalingCadenceRecord.CyclingPedalingCadenceRecordSample
                cyclingPedalingCadenceRecord =
                        new CyclingPedalingCadenceRecord.CyclingPedalingCadenceRecordSample(
                                1, Instant.now().plusMillis(100));
        ArrayList<CyclingPedalingCadenceRecord.CyclingPedalingCadenceRecordSample>
                cyclingPedalingCadenceRecords = new ArrayList<>();
        cyclingPedalingCadenceRecords.add(cyclingPedalingCadenceRecord);
        cyclingPedalingCadenceRecords.add(cyclingPedalingCadenceRecord);

        return new CyclingPedalingCadenceRecord.Builder(
                        new Metadata.Builder().build(),
                        Instant.now(),
                        Instant.now().plusMillis(1000),
                        cyclingPedalingCadenceRecords)
                .build();
    }

    private static CyclingPedalingCadenceRecord getCompleteCyclingPedalingCadenceRecord() {
        return buildRecordForRpm(1, 100);
    }

    private static CyclingPedalingCadenceRecord buildRecordForRpm(
            double rpm, long millisFromStart) {
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
        testMetadataBuilder.setClientRecordId("CPCR" + Math.random());
        testMetadataBuilder.setRecordingMethod(Metadata.RECORDING_METHOD_ACTIVELY_RECORDED);
        Instant recordStartTime = Instant.now();

        CyclingPedalingCadenceRecord.CyclingPedalingCadenceRecordSample
                cyclingPedalingCadenceRecord =
                        new CyclingPedalingCadenceRecord.CyclingPedalingCadenceRecordSample(
                                rpm, recordStartTime.plusMillis(millisFromStart));

        ArrayList<CyclingPedalingCadenceRecord.CyclingPedalingCadenceRecordSample>
                cyclingPedalingCadenceRecords = new ArrayList<>();
        cyclingPedalingCadenceRecords.add(cyclingPedalingCadenceRecord);
        cyclingPedalingCadenceRecords.add(cyclingPedalingCadenceRecord);

        return new CyclingPedalingCadenceRecord.Builder(
                        testMetadataBuilder.build(),
                        recordStartTime,
                        recordStartTime.plusMillis(1000),
                        cyclingPedalingCadenceRecords)
                .build();
    }
}
