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

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.health.connect.DeleteUsingFiltersRequest;
import android.health.connect.HealthConnectException;
import android.health.connect.ReadRecordsRequestUsingFilters;
import android.health.connect.ReadRecordsRequestUsingIds;
import android.health.connect.RecordIdFilter;
import android.health.connect.TimeInstantRangeFilter;
import android.health.connect.changelog.ChangeLogTokenRequest;
import android.health.connect.changelog.ChangeLogTokenResponse;
import android.health.connect.changelog.ChangeLogsRequest;
import android.health.connect.changelog.ChangeLogsResponse;
import android.health.connect.datatypes.BloodGlucoseRecord;
import android.health.connect.datatypes.DataOrigin;
import android.health.connect.datatypes.Device;
import android.health.connect.datatypes.Metadata;
import android.health.connect.datatypes.Record;
import android.health.connect.datatypes.units.BloodGlucose;
import android.healthconnect.cts.utils.AssumptionCheckerRule;
import android.healthconnect.cts.utils.TestUtils;
import android.platform.test.annotations.AppModeFull;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@AppModeFull(reason = "HealthConnectManager is not accessible to instant apps")
@RunWith(AndroidJUnit4.class)
public class BloodGlucoseRecordTest {
    private static final String TAG = "BloodGlucoseRecordTest";

    @Rule
    public AssumptionCheckerRule mSupportedHardwareRule =
            new AssumptionCheckerRule(
                    TestUtils::isHardwareSupported, "Tests should run on supported hardware only.");

    @After
    public void tearDown() throws InterruptedException {
        TestUtils.verifyDeleteRecords(
                BloodGlucoseRecord.class,
                new TimeInstantRangeFilter.Builder()
                        .setStartTime(Instant.EPOCH)
                        .setEndTime(Instant.now())
                        .build());
        TestUtils.deleteAllStagedRemoteData();
    }

    @Test
    public void testInsertBloodGlucoseRecord() throws InterruptedException {
        List<Record> records =
                List.of(getBaseBloodGlucoseRecord(), getCompleteBloodGlucoseRecord());
        TestUtils.insertRecords(records);
    }

    @Test
    public void testReadBloodGlucoseRecord_usingIds() throws InterruptedException {
        List<Record> recordList =
                Arrays.asList(getCompleteBloodGlucoseRecord(), getCompleteBloodGlucoseRecord());
        List<Record> insertedRecords = TestUtils.insertRecords(recordList);
        readBloodGlucoseRecordUsingIds(insertedRecords);
    }

    @Test
    public void testReadBloodGlucoseRecord_invalidIds() throws InterruptedException {
        ReadRecordsRequestUsingIds<BloodGlucoseRecord> request =
                new ReadRecordsRequestUsingIds.Builder<>(BloodGlucoseRecord.class)
                        .addId(UUID.randomUUID().toString())
                        .build();
        List<BloodGlucoseRecord> result = TestUtils.readRecords(request);
        assertThat(result.size()).isEqualTo(0);
    }

    @Test
    public void testReadBloodGlucoseRecord_usingClientRecordIds() throws InterruptedException {
        List<Record> recordList =
                Arrays.asList(getCompleteBloodGlucoseRecord(), getCompleteBloodGlucoseRecord());
        List<Record> insertedRecords = TestUtils.insertRecords(recordList);
        readBloodGlucoseRecordUsingClientId(insertedRecords);
    }

    @Test
    public void testReadBloodGlucoseRecord_invalidClientRecordIds() throws InterruptedException {
        ReadRecordsRequestUsingIds<BloodGlucoseRecord> request =
                new ReadRecordsRequestUsingIds.Builder<>(BloodGlucoseRecord.class)
                        .addClientRecordId("abc")
                        .build();
        List<BloodGlucoseRecord> result = TestUtils.readRecords(request);
        assertThat(result.size()).isEqualTo(0);
    }

    @Test
    public void testReadBloodGlucoseRecordUsingFilters_default() throws InterruptedException {
        List<BloodGlucoseRecord> oldBloodGlucoseRecords =
                TestUtils.readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(BloodGlucoseRecord.class)
                                .build());
        BloodGlucoseRecord testRecord = getCompleteBloodGlucoseRecord();
        TestUtils.insertRecords(Collections.singletonList(testRecord));
        List<BloodGlucoseRecord> newBloodGlucoseRecords =
                TestUtils.readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(BloodGlucoseRecord.class)
                                .build());
        assertThat(newBloodGlucoseRecords.size()).isEqualTo(oldBloodGlucoseRecords.size() + 1);
        assertThat(newBloodGlucoseRecords.get(newBloodGlucoseRecords.size() - 1).equals(testRecord))
                .isTrue();
    }

    @Test
    public void testReadBloodGlucoseRecordUsingFilters_timeFilter() throws InterruptedException {
        TimeInstantRangeFilter filter =
                new TimeInstantRangeFilter.Builder()
                        .setStartTime(Instant.now())
                        .setEndTime(Instant.now().plusMillis(3000))
                        .build();
        BloodGlucoseRecord testRecord = getCompleteBloodGlucoseRecord();
        TestUtils.insertRecords(Collections.singletonList(testRecord));
        List<BloodGlucoseRecord> newBloodGlucoseRecords =
                TestUtils.readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(BloodGlucoseRecord.class)
                                .setTimeRangeFilter(filter)
                                .build());
        assertThat(newBloodGlucoseRecords.size()).isEqualTo(1);
        assertThat(newBloodGlucoseRecords.get(newBloodGlucoseRecords.size() - 1).equals(testRecord))
                .isTrue();
    }

    @Test
    public void testReadBloodGlucoseRecordUsingFilters_dataFilter_correct()
            throws InterruptedException {
        Context context = ApplicationProvider.getApplicationContext();
        List<BloodGlucoseRecord> oldBloodGlucoseRecords =
                TestUtils.readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(BloodGlucoseRecord.class)
                                .addDataOrigins(
                                        new DataOrigin.Builder()
                                                .setPackageName(context.getPackageName())
                                                .build())
                                .build());
        BloodGlucoseRecord testRecord = getCompleteBloodGlucoseRecord();
        TestUtils.insertRecords(Collections.singletonList(testRecord));
        List<BloodGlucoseRecord> newBloodGlucoseRecords =
                TestUtils.readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(BloodGlucoseRecord.class)
                                .addDataOrigins(
                                        new DataOrigin.Builder()
                                                .setPackageName(context.getPackageName())
                                                .build())
                                .build());
        assertThat(newBloodGlucoseRecords.size() - oldBloodGlucoseRecords.size()).isEqualTo(1);
        BloodGlucoseRecord newRecord =
                newBloodGlucoseRecords.get(newBloodGlucoseRecords.size() - 1);
        assertThat(newRecord.equals(testRecord)).isTrue();
        assertThat(newRecord.getSpecimenSource()).isEqualTo(testRecord.getSpecimenSource());
        assertThat(newRecord.getLevel()).isEqualTo(testRecord.getLevel());
        assertThat(newRecord.getRelationToMeal()).isEqualTo(testRecord.getRelationToMeal());
        assertThat(newRecord.getMealType()).isEqualTo(testRecord.getMealType());
    }

    @Test
    public void testReadBloodGlucoseRecordUsingFilters_dataFilter_incorrect()
            throws InterruptedException {
        TestUtils.insertRecords(Collections.singletonList(getCompleteBloodGlucoseRecord()));
        List<BloodGlucoseRecord> newBloodGlucoseRecords =
                TestUtils.readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(BloodGlucoseRecord.class)
                                .addDataOrigins(
                                        new DataOrigin.Builder().setPackageName("abc").build())
                                .build());
        assertThat(newBloodGlucoseRecords.size()).isEqualTo(0);
    }

    private void readBloodGlucoseRecordUsingClientId(List<Record> insertedRecord)
            throws InterruptedException {
        ReadRecordsRequestUsingIds.Builder<BloodGlucoseRecord> request =
                new ReadRecordsRequestUsingIds.Builder<>(BloodGlucoseRecord.class);
        for (Record record : insertedRecord) {
            request.addClientRecordId(record.getMetadata().getClientRecordId());
        }
        List<BloodGlucoseRecord> result = TestUtils.readRecords(request.build());
        assertThat(result.size()).isEqualTo(insertedRecord.size());
        assertThat(result).containsExactlyElementsIn(insertedRecord);
    }

    private void readBloodGlucoseRecordUsingIds(List<Record> recordList)
            throws InterruptedException {
        ReadRecordsRequestUsingIds.Builder<BloodGlucoseRecord> request =
                new ReadRecordsRequestUsingIds.Builder<>(BloodGlucoseRecord.class);
        for (Record record : recordList) {
            request.addId(record.getMetadata().getId());
        }
        ReadRecordsRequestUsingIds requestUsingIds = request.build();
        assertThat(requestUsingIds.getRecordType()).isEqualTo(BloodGlucoseRecord.class);
        assertThat(requestUsingIds.getRecordIdFilters()).isNotNull();
        List<BloodGlucoseRecord> result = TestUtils.readRecords(requestUsingIds);
        assertThat(result).hasSize(recordList.size());
        assertThat(result).containsExactlyElementsIn(recordList);
    }

    @Test
    public void testDeleteBloodGlucoseRecord_no_filters() throws InterruptedException {
        String id = TestUtils.insertRecordAndGetId(getCompleteBloodGlucoseRecord());
        TestUtils.verifyDeleteRecords(new DeleteUsingFiltersRequest.Builder().build());
        TestUtils.assertRecordNotFound(id, BloodGlucoseRecord.class);
    }

    @Test
    public void testDeleteBloodGlucoseRecord_time_filters() throws InterruptedException {
        TimeInstantRangeFilter timeRangeFilter =
                new TimeInstantRangeFilter.Builder()
                        .setStartTime(Instant.now())
                        .setEndTime(Instant.now().plusMillis(1000))
                        .build();
        String id = TestUtils.insertRecordAndGetId(getCompleteBloodGlucoseRecord());
        TestUtils.verifyDeleteRecords(
                new DeleteUsingFiltersRequest.Builder()
                        .addRecordType(BloodGlucoseRecord.class)
                        .setTimeRangeFilter(timeRangeFilter)
                        .build());
        TestUtils.assertRecordNotFound(id, BloodGlucoseRecord.class);
    }

    @Test
    public void testDeleteBloodGlucoseRecord_recordId_filters() throws InterruptedException {
        List<Record> records =
                List.of(getBaseBloodGlucoseRecord(), getCompleteBloodGlucoseRecord());
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
    public void testDeleteBloodGlucoseRecord_dataOrigin_filters() throws InterruptedException {
        Context context = ApplicationProvider.getApplicationContext();
        String id = TestUtils.insertRecordAndGetId(getCompleteBloodGlucoseRecord());
        TestUtils.verifyDeleteRecords(
                new DeleteUsingFiltersRequest.Builder()
                        .addDataOrigin(
                                new DataOrigin.Builder()
                                        .setPackageName(context.getPackageName())
                                        .build())
                        .build());
        TestUtils.assertRecordNotFound(id, BloodGlucoseRecord.class);
    }

    @Test
    public void testDeleteBloodGlucoseRecord_dataOrigin_filter_incorrect()
            throws InterruptedException {
        String id = TestUtils.insertRecordAndGetId(getCompleteBloodGlucoseRecord());
        TestUtils.verifyDeleteRecords(
                new DeleteUsingFiltersRequest.Builder()
                        .addDataOrigin(new DataOrigin.Builder().setPackageName("abc").build())
                        .build());
        TestUtils.assertRecordFound(id, BloodGlucoseRecord.class);
    }

    @Test
    public void testDeleteBloodGlucoseRecord_usingIds() throws InterruptedException {
        List<Record> records =
                List.of(getBaseBloodGlucoseRecord(), getCompleteBloodGlucoseRecord());
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
    public void testDeleteBloodGlucoseRecord_time_range() throws InterruptedException {
        TimeInstantRangeFilter timeRangeFilter =
                new TimeInstantRangeFilter.Builder()
                        .setStartTime(Instant.now())
                        .setEndTime(Instant.now().plusMillis(1000))
                        .build();
        String id = TestUtils.insertRecordAndGetId(getCompleteBloodGlucoseRecord());
        TestUtils.verifyDeleteRecords(BloodGlucoseRecord.class, timeRangeFilter);
        TestUtils.assertRecordNotFound(id, BloodGlucoseRecord.class);
    }

    @Test
    public void testZoneOffsets() {
        final ZoneOffset defaultZoneOffset =
                ZoneOffset.systemDefault().getRules().getOffset(Instant.now());
        final ZoneOffset zoneOffset = ZoneOffset.UTC;
        BloodGlucoseRecord.Builder builder =
                new BloodGlucoseRecord.Builder(
                        new Metadata.Builder().build(),
                        Instant.now(),
                        1,
                        BloodGlucose.fromMillimolesPerLiter(10.0),
                        1,
                        1);

        assertThat(builder.setZoneOffset(zoneOffset).build().getZoneOffset()).isEqualTo(zoneOffset);
        assertThat(builder.clearZoneOffset().build().getZoneOffset()).isEqualTo(defaultZoneOffset);
    }

    @Test
    public void testUpdateRecords_validInput_dataBaseUpdatedSuccessfully()
            throws InterruptedException {

        List<Record> insertedRecords =
                TestUtils.insertRecords(
                        Arrays.asList(
                                getCompleteBloodGlucoseRecord(), getCompleteBloodGlucoseRecord()));

        // read inserted records and verify that the data is same as inserted.
        readBloodGlucoseRecordUsingIds(insertedRecords);

        // Generate a new set of records that will be used to perform the update operation.
        List<Record> updateRecords =
                Arrays.asList(getCompleteBloodGlucoseRecord(), getCompleteBloodGlucoseRecord());

        // Modify the uid of the updateRecords to the uuid that was present in the insert records.
        for (int itr = 0; itr < updateRecords.size(); itr++) {
            updateRecords.set(
                    itr,
                    getBloodGlucoseRecord_update(
                            updateRecords.get(itr),
                            insertedRecords.get(itr).getMetadata().getId(),
                            insertedRecords.get(itr).getMetadata().getClientRecordId()));
        }

        TestUtils.updateRecords(updateRecords);

        // assert the inserted data has been modified by reading the data.
        readBloodGlucoseRecordUsingIds(updateRecords);
    }

    @Test
    public void testUpdateRecords_invalidInputRecords_noChangeInDataBase()
            throws InterruptedException {
        List<Record> insertedRecords =
                TestUtils.insertRecords(
                        Arrays.asList(
                                getCompleteBloodGlucoseRecord(), getCompleteBloodGlucoseRecord()));

        // read inserted records and verify that the data is same as inserted.
        readBloodGlucoseRecordUsingIds(insertedRecords);

        // Generate a second set of records that will be used to perform the update operation.
        List<Record> updateRecords =
                Arrays.asList(getCompleteBloodGlucoseRecord(), getCompleteBloodGlucoseRecord());

        // Modify the Uid of the updateRecords to the UUID that was present in the insert records,
        // leaving out alternate records so that they have a new UUID which is not present in the
        // dataBase.
        for (int itr = 0; itr < updateRecords.size(); itr++) {
            updateRecords.set(
                    itr,
                    getBloodGlucoseRecord_update(
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
        readBloodGlucoseRecordUsingIds(insertedRecords);
    }

    @Test
    public void testUpdateRecords_recordWithInvalidPackageName_noChangeInDataBase()
            throws InterruptedException {
        List<Record> insertedRecords =
                TestUtils.insertRecords(
                        Arrays.asList(
                                getCompleteBloodGlucoseRecord(), getCompleteBloodGlucoseRecord()));

        // read inserted records and verify that the data is same as inserted.
        readBloodGlucoseRecordUsingIds(insertedRecords);

        // Generate a second set of records that will be used to perform the update operation.
        List<Record> updateRecords =
                Arrays.asList(getCompleteBloodGlucoseRecord(), getCompleteBloodGlucoseRecord());

        // Modify the Uuid of the updateRecords to the uuid that was present in the insert records.
        for (int itr = 0; itr < updateRecords.size(); itr++) {
            updateRecords.set(
                    itr,
                    getBloodGlucoseRecord_update(
                            updateRecords.get(itr),
                            insertedRecords.get(itr).getMetadata().getId(),
                            insertedRecords.get(itr).getMetadata().getClientRecordId()));
            //             adding an entry with invalid packageName.
            updateRecords.set(itr, getCompleteBloodGlucoseRecord());
        }

        try {
            TestUtils.updateRecords(updateRecords);
            Assert.fail("Expected to fail due to invalid package.");
        } catch (Exception exception) {
            // verify that the testcase failed due to invalid argument exception.
            assertThat(exception).isNotNull();
        }

        // assert the inserted data has not been modified by reading the data.
        readBloodGlucoseRecordUsingIds(insertedRecords);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCreateBloodGlucoseRecord_invalidValue() {
        new BloodGlucoseRecord.Builder(
                        new Metadata.Builder().build(),
                        Instant.now(),
                        1,
                        BloodGlucose.fromMillimolesPerLiter(51.0),
                        1,
                        1)
                .build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCreateBloodGlucoseRecord_invalidMealTypeValue() {
        new BloodGlucoseRecord.Builder(
                        new Metadata.Builder().build(),
                        Instant.now(),
                        1,
                        BloodGlucose.fromMillimolesPerLiter(20.0),
                        1,
                        5)
                .build();
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
                                .addRecordType(BloodGlucoseRecord.class)
                                .build());
        ChangeLogsRequest changeLogsRequest =
                new ChangeLogsRequest.Builder(tokenResponse.getToken()).build();
        ChangeLogsResponse response = TestUtils.getChangeLogs(changeLogsRequest);
        assertThat(response.getUpsertedRecords().size()).isEqualTo(0);
        assertThat(response.getDeletedLogs().size()).isEqualTo(0);

        List<Record> testRecord = Collections.singletonList(getCompleteBloodGlucoseRecord());
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
                        .addRecordType(BloodGlucoseRecord.class)
                        .build());
        response = TestUtils.getChangeLogs(changeLogsRequest);
        assertThat(response.getDeletedLogs()).isEmpty();
    }

    BloodGlucoseRecord getBloodGlucoseRecord_update(
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
        return new BloodGlucoseRecord.Builder(
                        metadataWithId,
                        Instant.now(),
                        2,
                        BloodGlucose.fromMillimolesPerLiter(20.0),
                        2,
                        2)
                .setZoneOffset(ZoneOffset.systemDefault().getRules().getOffset(Instant.now()))
                .build();
    }

    private static BloodGlucoseRecord getBaseBloodGlucoseRecord() {
        return new BloodGlucoseRecord.Builder(
                        new Metadata.Builder().build(),
                        Instant.now(),
                        1,
                        BloodGlucose.fromMillimolesPerLiter(10.0),
                        1,
                        1)
                .build();
    }

    private static BloodGlucoseRecord getCompleteBloodGlucoseRecord() {
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
        testMetadataBuilder.setClientRecordId("BGR" + Math.random());
        testMetadataBuilder.setRecordingMethod(Metadata.RECORDING_METHOD_ACTIVELY_RECORDED);

        return new BloodGlucoseRecord.Builder(
                        testMetadataBuilder.build(),
                        Instant.now(),
                        1,
                        BloodGlucose.fromMillimolesPerLiter(10.0),
                        1,
                        1)
                .setZoneOffset(ZoneOffset.systemDefault().getRules().getOffset(Instant.now()))
                .build();
    }
}
