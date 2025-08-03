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

import static android.health.connect.datatypes.StepsCadenceRecord.STEPS_CADENCE_RATE_AVG;
import static android.health.connect.datatypes.StepsCadenceRecord.STEPS_CADENCE_RATE_MAX;
import static android.health.connect.datatypes.StepsCadenceRecord.STEPS_CADENCE_RATE_MIN;

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
import android.health.connect.datatypes.DataOrigin;
import android.health.connect.datatypes.Device;
import android.health.connect.datatypes.Metadata;
import android.health.connect.datatypes.Record;
import android.health.connect.datatypes.StepsCadenceRecord;
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
public class StepsCadenceRecordTest {

    private static final String TAG = "StepsCadenceRecordTest";

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
                StepsCadenceRecord.class,
                new TimeInstantRangeFilter.Builder()
                        .setStartTime(Instant.EPOCH)
                        .setEndTime(Instant.now())
                        .build());
        TestUtils.deleteAllStagedRemoteData();
    }

    @Test
    public void testInsertStepsCadenceRecord() throws InterruptedException {
        TestUtils.insertRecords(
                Arrays.asList(getBaseStepsCadenceRecord(), getCompleteStepsCadenceRecord()));
    }

    @Test
    public void testReadStepsCadenceRecord_usingIds() throws InterruptedException {
        testReadStepsCadenceRecordIds();
    }

    @Test
    public void testReadStepsCadenceRecord_invalidIds() throws InterruptedException {
        ReadRecordsRequestUsingIds<StepsCadenceRecord> request =
                new ReadRecordsRequestUsingIds.Builder<>(StepsCadenceRecord.class)
                        .addId(UUID.randomUUID().toString())
                        .build();
        List<StepsCadenceRecord> result = TestUtils.readRecords(request);
        assertThat(result.size()).isEqualTo(0);
    }

    @Test
    public void testReadStepsCadenceRecord_usingClientRecordIds() throws InterruptedException {
        List<Record> recordList =
                Arrays.asList(getCompleteStepsCadenceRecord(), getCompleteStepsCadenceRecord());
        List<Record> insertedRecords = TestUtils.insertRecords(recordList);
        readStepsCadenceRecordUsingClientId(insertedRecords);
    }

    @Test
    public void testReadStepsCadenceRecord_invalidClientRecordIds() throws InterruptedException {
        ReadRecordsRequestUsingIds<StepsCadenceRecord> request =
                new ReadRecordsRequestUsingIds.Builder<>(StepsCadenceRecord.class)
                        .addClientRecordId("abc")
                        .build();
        List<StepsCadenceRecord> result = TestUtils.readRecords(request);
        assertThat(result.size()).isEqualTo(0);
    }

    @Test
    public void testReadStepsCadenceRecordUsingFilters_default() throws InterruptedException {
        List<StepsCadenceRecord> oldStepsCadenceRecords =
                TestUtils.readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(StepsCadenceRecord.class)
                                .build());
        StepsCadenceRecord testRecord = getCompleteStepsCadenceRecord();
        TestUtils.insertRecords(Collections.singletonList(testRecord));
        List<StepsCadenceRecord> newStepsCadenceRecords =
                TestUtils.readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(StepsCadenceRecord.class)
                                .build());
        assertThat(newStepsCadenceRecords.size()).isEqualTo(oldStepsCadenceRecords.size() + 1);
        assertThat(newStepsCadenceRecords.get(newStepsCadenceRecords.size() - 1).equals(testRecord))
                .isTrue();
    }

    @Test
    public void testReadStepsCadenceRecordUsingFilters_timeFilter() throws InterruptedException {
        TimeInstantRangeFilter filter =
                new TimeInstantRangeFilter.Builder()
                        .setStartTime(Instant.now())
                        .setEndTime(Instant.now().plusMillis(3000))
                        .build();
        StepsCadenceRecord testRecord = getCompleteStepsCadenceRecord();
        TestUtils.insertRecords(Collections.singletonList(testRecord));
        List<StepsCadenceRecord> newStepsCadenceRecords =
                TestUtils.readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(StepsCadenceRecord.class)
                                .setTimeRangeFilter(filter)
                                .build());
        assertThat(newStepsCadenceRecords.size()).isEqualTo(1);
        assertThat(newStepsCadenceRecords.get(newStepsCadenceRecords.size() - 1).equals(testRecord))
                .isTrue();
    }

    @Test
    public void testReadStepsCadenceRecordUsingFilters_dataFilter_correct()
            throws InterruptedException {
        Context context = ApplicationProvider.getApplicationContext();
        List<StepsCadenceRecord> oldStepsCadenceRecords =
                TestUtils.readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(StepsCadenceRecord.class)
                                .addDataOrigins(
                                        new DataOrigin.Builder()
                                                .setPackageName(context.getPackageName())
                                                .build())
                                .build());
        StepsCadenceRecord testRecord = getCompleteStepsCadenceRecord();
        TestUtils.insertRecords(Collections.singletonList(testRecord));
        List<StepsCadenceRecord> newStepsCadenceRecords =
                TestUtils.readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(StepsCadenceRecord.class)
                                .addDataOrigins(
                                        new DataOrigin.Builder()
                                                .setPackageName(context.getPackageName())
                                                .build())
                                .build());
        assertThat(newStepsCadenceRecords.size() - oldStepsCadenceRecords.size()).isEqualTo(1);
        assertThat(newStepsCadenceRecords.get(newStepsCadenceRecords.size() - 1).equals(testRecord))
                .isTrue();
        StepsCadenceRecord newRecord =
                newStepsCadenceRecords.get(newStepsCadenceRecords.size() - 1);
        assertThat(newRecord.equals(testRecord)).isTrue();
        for (int idx = 0; idx < newRecord.getSamples().size(); idx++) {
            assertThat(newRecord.getSamples().get(idx).getTime().toEpochMilli())
                    .isEqualTo(testRecord.getSamples().get(idx).getTime().toEpochMilli());
            assertThat(newRecord.getSamples().get(idx).getRate())
                    .isEqualTo(testRecord.getSamples().get(idx).getRate());
        }
    }

    @Test
    public void testReadStepsCadenceRecordUsingFilters_dataFilter_incorrect()
            throws InterruptedException {
        TestUtils.insertRecords(Collections.singletonList(getCompleteStepsCadenceRecord()));
        List<StepsCadenceRecord> newStepsCadenceRecords =
                TestUtils.readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(StepsCadenceRecord.class)
                                .addDataOrigins(
                                        new DataOrigin.Builder().setPackageName("abc").build())
                                .build());
        assertThat(newStepsCadenceRecords.size()).isEqualTo(0);
    }

    @Test
    public void testDeleteStepsCadenceRecord_no_filters() throws InterruptedException {
        String id = TestUtils.insertRecordAndGetId(getCompleteStepsCadenceRecord());
        TestUtils.verifyDeleteRecords(new DeleteUsingFiltersRequest.Builder().build());
        TestUtils.assertRecordNotFound(id, StepsCadenceRecord.class);
    }

    @Test
    public void testDeleteStepsCadenceRecord_time_filters() throws InterruptedException {
        TimeInstantRangeFilter timeRangeFilter =
                new TimeInstantRangeFilter.Builder()
                        .setStartTime(Instant.now())
                        .setEndTime(Instant.now().plusMillis(1000))
                        .build();
        String id = TestUtils.insertRecordAndGetId(getCompleteStepsCadenceRecord());
        TestUtils.verifyDeleteRecords(
                new DeleteUsingFiltersRequest.Builder()
                        .addRecordType(StepsCadenceRecord.class)
                        .setTimeRangeFilter(timeRangeFilter)
                        .build());
        TestUtils.assertRecordNotFound(id, StepsCadenceRecord.class);
    }

    @Test
    public void testDeleteStepsCadenceRecord_recordId_filters() throws InterruptedException {
        List<Record> records =
                List.of(getBaseStepsCadenceRecord(), getCompleteStepsCadenceRecord());
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
    public void testDeleteStepsCadenceRecord_dataOrigin_filters() throws InterruptedException {
        Context context = ApplicationProvider.getApplicationContext();
        String id = TestUtils.insertRecordAndGetId(getCompleteStepsCadenceRecord());
        TestUtils.verifyDeleteRecords(
                new DeleteUsingFiltersRequest.Builder()
                        .addDataOrigin(
                                new DataOrigin.Builder()
                                        .setPackageName(context.getPackageName())
                                        .build())
                        .build());
        TestUtils.assertRecordNotFound(id, StepsCadenceRecord.class);
    }

    @Test
    public void testDeleteStepsCadenceRecord_dataOrigin_filter_incorrect()
            throws InterruptedException {
        String id = TestUtils.insertRecordAndGetId(getCompleteStepsCadenceRecord());
        TestUtils.verifyDeleteRecords(
                new DeleteUsingFiltersRequest.Builder()
                        .addDataOrigin(new DataOrigin.Builder().setPackageName("abc").build())
                        .build());
        TestUtils.assertRecordFound(id, StepsCadenceRecord.class);
    }

    @Test
    public void testDeleteStepsCadenceRecord_usingIds() throws InterruptedException {
        List<Record> records =
                List.of(getBaseStepsCadenceRecord(), getCompleteStepsCadenceRecord());
        List<Record> insertedRecord = TestUtils.insertRecords(records);
        List<RecordIdFilter> recordIds = new ArrayList<>(records.size());
        for (Record record : insertedRecord) {
            recordIds.add(RecordIdFilter.fromId(record.getClass(), record.getMetadata().getId()));
        }

        TestUtils.verifyDeleteRecords(recordIds);
        for (Record record : records) {
            TestUtils.assertRecordNotFound(record.getMetadata().getId(), record.getClass());
        }
    }

    @Test
    public void testDeleteStepsCadenceRecord_time_range() throws InterruptedException {
        TimeInstantRangeFilter timeRangeFilter =
                new TimeInstantRangeFilter.Builder()
                        .setStartTime(Instant.now())
                        .setEndTime(Instant.now().plusMillis(1000))
                        .build();
        String id = TestUtils.insertRecordAndGetId(getCompleteStepsCadenceRecord());
        TestUtils.verifyDeleteRecords(StepsCadenceRecord.class, timeRangeFilter);
        TestUtils.assertRecordNotFound(id, StepsCadenceRecord.class);
    }

    @Test
    public void testZoneOffsets() {
        final ZoneOffset defaultZoneOffset =
                ZoneOffset.systemDefault().getRules().getOffset(Instant.now());
        final ZoneOffset startZoneOffset = ZoneOffset.UTC;
        final ZoneOffset endZoneOffset = ZoneOffset.MAX;
        StepsCadenceRecord.Builder builder =
                new StepsCadenceRecord.Builder(
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
                                getCompleteStepsCadenceRecord(), getCompleteStepsCadenceRecord()));

        // read inserted records and verify that the data is same as inserted.
        readStepsCadenceRecordUsingIds(insertedRecords);

        // Generate a new set of records that will be used to perform the update operation.
        List<Record> updateRecords =
                Arrays.asList(getCompleteStepsCadenceRecord(), getCompleteStepsCadenceRecord());

        // Modify the uid of the updateRecords to the uuid that was present in the insert records.
        for (int itr = 0; itr < updateRecords.size(); itr++) {
            updateRecords.set(
                    itr,
                    getStepsCadenceRecord_update(
                            updateRecords.get(itr),
                            insertedRecords.get(itr).getMetadata().getId(),
                            insertedRecords.get(itr).getMetadata().getClientRecordId()));
        }

        TestUtils.updateRecords(updateRecords);

        // assert the inserted data has been modified by reading the data.
        readStepsCadenceRecordUsingIds(updateRecords);
    }

    @Test
    public void testUpdateRecords_invalidInputRecords_noChangeInDataBase()
            throws InterruptedException {
        List<Record> insertedRecords =
                TestUtils.insertRecords(
                        Arrays.asList(
                                getCompleteStepsCadenceRecord(), getCompleteStepsCadenceRecord()));

        // read inserted records and verify that the data is same as inserted.
        readStepsCadenceRecordUsingIds(insertedRecords);

        // Generate a second set of records that will be used to perform the update operation.
        List<Record> updateRecords =
                Arrays.asList(getCompleteStepsCadenceRecord(), getCompleteStepsCadenceRecord());

        // Modify the Uid of the updateRecords to the UUID that was present in the insert records,
        // leaving out alternate records so that they have a new UUID which is not present in the
        // dataBase.
        for (int itr = 0; itr < updateRecords.size(); itr++) {
            updateRecords.set(
                    itr,
                    getStepsCadenceRecord_update(
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
        readStepsCadenceRecordUsingIds(insertedRecords);
    }

    @Test
    public void testUpdateRecords_recordWithInvalidPackageName_noChangeInDataBase()
            throws InterruptedException {
        List<Record> insertedRecords =
                TestUtils.insertRecords(
                        Arrays.asList(
                                getCompleteStepsCadenceRecord(), getCompleteStepsCadenceRecord()));

        // read inserted records and verify that the data is same as inserted.
        readStepsCadenceRecordUsingIds(insertedRecords);

        // Generate a second set of records that will be used to perform the update operation.
        List<Record> updateRecords =
                Arrays.asList(getCompleteStepsCadenceRecord(), getCompleteStepsCadenceRecord());

        // Modify the Uuid of the updateRecords to the uuid that was present in the insert records.
        for (int itr = 0; itr < updateRecords.size(); itr++) {
            updateRecords.set(
                    itr,
                    getStepsCadenceRecord_update(
                            updateRecords.get(itr),
                            insertedRecords.get(itr).getMetadata().getId(),
                            insertedRecords.get(itr).getMetadata().getClientRecordId()));
            //             adding an entry with invalid packageName.
            updateRecords.set(itr, getCompleteStepsCadenceRecord());
        }

        try {
            TestUtils.updateRecords(updateRecords);
            Assert.fail("Expected to fail due to invalid package.");
        } catch (Exception exception) {
            // verify that the testcase failed due to invalid argument exception.
            assertThat(exception).isNotNull();
        }

        // assert the inserted data has not been modified by reading the data.
        readStepsCadenceRecordUsingIds(insertedRecords);
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
                                .addRecordType(StepsCadenceRecord.class)
                                .build());
        ChangeLogsRequest changeLogsRequest =
                new ChangeLogsRequest.Builder(tokenResponse.getToken()).build();
        ChangeLogsResponse response = TestUtils.getChangeLogs(changeLogsRequest);
        assertThat(response.getUpsertedRecords().size()).isEqualTo(0);
        assertThat(response.getDeletedLogs().size()).isEqualTo(0);

        List<Record> testRecord = Collections.singletonList(getCompleteStepsCadenceRecord());
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
                        .addRecordType(StepsCadenceRecord.class)
                        .build());
        response = TestUtils.getChangeLogs(changeLogsRequest);
        assertThat(response.getDeletedLogs()).isEmpty();
    }

    private void testReadStepsCadenceRecordIds() throws InterruptedException {
        List<Record> recordList =
                Arrays.asList(getCompleteStepsCadenceRecord(), getCompleteStepsCadenceRecord());
        readStepsCadenceRecordUsingIds(recordList);
    }

    private void readStepsCadenceRecordUsingClientId(List<Record> insertedRecord)
            throws InterruptedException {
        ReadRecordsRequestUsingIds.Builder<StepsCadenceRecord> request =
                new ReadRecordsRequestUsingIds.Builder<>(StepsCadenceRecord.class);
        for (Record record : insertedRecord) {
            request.addClientRecordId(record.getMetadata().getClientRecordId());
        }
        List<StepsCadenceRecord> result = TestUtils.readRecords(request.build());
        assertThat(result.size()).isEqualTo(insertedRecord.size());
        assertThat(result).containsExactlyElementsIn(insertedRecord);
    }

    private void readStepsCadenceRecordUsingIds(List<Record> recordList)
            throws InterruptedException {
        List<Record> insertedRecords = TestUtils.insertRecords(recordList);
        ReadRecordsRequestUsingIds.Builder<StepsCadenceRecord> request =
                new ReadRecordsRequestUsingIds.Builder<>(StepsCadenceRecord.class);
        for (Record record : insertedRecords) {
            request.addId(record.getMetadata().getId());
        }
        ReadRecordsRequestUsingIds requestUsingIds = request.build();
        assertThat(requestUsingIds.getRecordType()).isEqualTo(StepsCadenceRecord.class);
        assertThat(requestUsingIds.getRecordIdFilters()).isNotNull();
        List<StepsCadenceRecord> result = TestUtils.readRecords(requestUsingIds);
        assertThat(result).hasSize(insertedRecords.size());
        assertThat(result.containsAll(insertedRecords)).isTrue();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCreateStepsCadenceRecord_invalidValue() {
        new StepsCadenceRecord.StepsCadenceRecordSample(10001.0, Instant.now().plusMillis(100));
    }

    @Test
    public void testInsertWithClientVersion() throws InterruptedException {
        List<Record> records = List.of(getStepsCadenceRecordWithClientVersion(10, 1, "testId"));
        final String id = TestUtils.insertRecords(records).get(0).getMetadata().getId();
        ReadRecordsRequestUsingIds<StepsCadenceRecord> request =
                new ReadRecordsRequestUsingIds.Builder<>(StepsCadenceRecord.class)
                        .addClientRecordId("testId")
                        .build();
        StepsCadenceRecord stepsRecord = TestUtils.readRecords(request).get(0);
        int sampleSize = ((StepsCadenceRecord) records.get(0)).getSamples().size();
        assertThat(stepsRecord.getSamples()).hasSize(sampleSize);
        assertThat(stepsRecord.getSamples().get(0).getRate()).isEqualTo(10);

        records = List.of(getStepsCadenceRecordWithClientVersion(20, 2, "testId"));
        TestUtils.insertRecords(records);

        stepsRecord = TestUtils.readRecords(request).get(0);
        assertThat(stepsRecord.getMetadata().getId()).isEqualTo(id);
        assertThat(stepsRecord.getSamples()).hasSize(sampleSize);
        assertThat(stepsRecord.getSamples().get(0).getRate()).isEqualTo(20);

        records = List.of(getStepsCadenceRecordWithClientVersion(30, 1, "testId"));
        TestUtils.insertRecords(records);
        stepsRecord = TestUtils.readRecords(request).get(0);
        assertThat(stepsRecord.getMetadata().getId()).isEqualTo(id);
        assertThat(stepsRecord.getSamples()).hasSize(sampleSize);
        assertThat(stepsRecord.getSamples().get(0).getRate()).isEqualTo(20);
    }

    @Test
    public void testRateAggregation_getAggregationFromThreerecords_aggResponsesAreCorrect()
            throws Exception {
        TestUtils.setupAggregation(PACKAGE_NAME, HealthDataCategory.ACTIVITY);
        List<Record> records =
                Arrays.asList(
                        buildRecordForStepsCadence(120, 100),
                        buildRecordForStepsCadence(100, 101),
                        buildRecordForStepsCadence(80, 102));
        AggregateRecordsResponse<Double> response =
                TestUtils.getAggregateResponse(
                        new AggregateRecordsRequest.Builder<Double>(
                                        new TimeInstantRangeFilter.Builder()
                                                .setStartTime(Instant.ofEpochMilli(0))
                                                .setEndTime(Instant.now().plus(1, ChronoUnit.DAYS))
                                                .build())
                                .addAggregationType(STEPS_CADENCE_RATE_MAX)
                                .addAggregationType(STEPS_CADENCE_RATE_MIN)
                                .addAggregationType(STEPS_CADENCE_RATE_AVG)
                                .build(),
                        records);
        checkAggregationResult(STEPS_CADENCE_RATE_MAX, 120, response);
        checkAggregationResult(STEPS_CADENCE_RATE_MIN, 80, response);
        checkAggregationResult(STEPS_CADENCE_RATE_AVG, 100, response);
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

    StepsCadenceRecord getStepsCadenceRecord_update(
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

        StepsCadenceRecord.StepsCadenceRecordSample stepsCadenceRecordSample =
                new StepsCadenceRecord.StepsCadenceRecordSample(8.0, Instant.now().plusMillis(100));

        return new StepsCadenceRecord.Builder(
                        metadataWithId,
                        Instant.now(),
                        Instant.now().plusMillis(2000),
                        List.of(stepsCadenceRecordSample, stepsCadenceRecordSample))
                .setStartZoneOffset(ZoneOffset.systemDefault().getRules().getOffset(Instant.now()))
                .setEndZoneOffset(ZoneOffset.systemDefault().getRules().getOffset(Instant.now()))
                .build();
    }

    static StepsCadenceRecord getStepsCadenceRecordWithClientVersion(
            int rate, int version, String clientRecordId) {
        Metadata.Builder testMetadataBuilder = new Metadata.Builder();
        testMetadataBuilder.setClientRecordId(clientRecordId);
        testMetadataBuilder.setClientRecordVersion(version);
        Metadata testMetaData = testMetadataBuilder.build();
        StepsCadenceRecord.StepsCadenceRecordSample stepsCadenceRecord =
                new StepsCadenceRecord.StepsCadenceRecordSample(
                        rate, Instant.now().plusMillis(100));
        ArrayList<StepsCadenceRecord.StepsCadenceRecordSample> stepsCadenceRecords =
                new ArrayList<>();
        stepsCadenceRecords.add(stepsCadenceRecord);
        stepsCadenceRecords.add(stepsCadenceRecord);

        return new StepsCadenceRecord.Builder(
                        testMetaData,
                        Instant.now(),
                        Instant.now().plusMillis(1000),
                        stepsCadenceRecords)
                .build();
    }

    private static StepsCadenceRecord getBaseStepsCadenceRecord() {
        StepsCadenceRecord.StepsCadenceRecordSample stepsCadenceRecord =
                new StepsCadenceRecord.StepsCadenceRecordSample(1, Instant.now().plusMillis(100));
        ArrayList<StepsCadenceRecord.StepsCadenceRecordSample> stepsCadenceRecords =
                new ArrayList<>();
        stepsCadenceRecords.add(stepsCadenceRecord);
        stepsCadenceRecords.add(stepsCadenceRecord);

        return new StepsCadenceRecord.Builder(
                        new Metadata.Builder().build(),
                        Instant.now(),
                        Instant.now().plusMillis(1000),
                        stepsCadenceRecords)
                .build();
    }

    private static StepsCadenceRecord getCompleteStepsCadenceRecord() {
        return buildRecordForStepsCadence(1, 100);
    }

    private static StepsCadenceRecord buildRecordForStepsCadence(
            double rate, long millisFromStart) {
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
        testMetadataBuilder.setClientRecordId("SCR" + Math.random());
        testMetadataBuilder.setRecordingMethod(Metadata.RECORDING_METHOD_ACTIVELY_RECORDED);
        StepsCadenceRecord.StepsCadenceRecordSample stepsCadenceRecord =
                new StepsCadenceRecord.StepsCadenceRecordSample(
                        rate, Instant.now().plusMillis(millisFromStart));
        ArrayList<StepsCadenceRecord.StepsCadenceRecordSample> stepsCadenceRecords =
                new ArrayList<>();
        stepsCadenceRecords.add(stepsCadenceRecord);
        stepsCadenceRecords.add(stepsCadenceRecord);

        return new StepsCadenceRecord.Builder(
                        testMetadataBuilder.build(),
                        Instant.now(),
                        Instant.now().plusMillis(1000),
                        stepsCadenceRecords)
                .build();
    }
}
