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
import android.health.connect.TimeInstantRangeFilter;
import android.health.connect.changelog.ChangeLogTokenRequest;
import android.health.connect.changelog.ChangeLogTokenResponse;
import android.health.connect.changelog.ChangeLogsRequest;
import android.health.connect.changelog.ChangeLogsResponse;
import android.health.connect.datatypes.DataOrigin;
import android.health.connect.datatypes.Device;
import android.health.connect.datatypes.Metadata;
import android.health.connect.datatypes.Record;
import android.health.connect.datatypes.TotalCaloriesBurnedRecord;
import android.health.connect.datatypes.units.Energy;
import android.healthconnect.cts.utils.AssumptionCheckerRule;
import android.healthconnect.cts.utils.TestUtils;

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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@RunWith(AndroidJUnit4.class)
public class TotalCaloriesBurnedRecordTest {
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
                TotalCaloriesBurnedRecord.class,
                new TimeInstantRangeFilter.Builder()
                        .setStartTime(Instant.EPOCH)
                        .setEndTime(Instant.now())
                        .build());
        TestUtils.deleteAllStagedRemoteData();
    }

    @Test
    public void testInsertTotalCaloriesBurnedRecord() throws InterruptedException {
        List<Record> records =
                List.of(getBaseTotalCaloriesBurnedRecord(), getCompleteTotalCaloriesBurnedRecord());
        TestUtils.insertRecords(records);
    }

    @Test
    public void testReadTotalCaloriesBurnedRecord_usingIds() throws InterruptedException {
        List<Record> recordList =
                Arrays.asList(
                        getCompleteTotalCaloriesBurnedRecord(),
                        getCompleteTotalCaloriesBurnedRecord());
        readTotalCaloriesBurnedRecordUsingIds(recordList);
    }

    @Test
    public void testReadTotalCaloriesBurnedRecord_invalidIds() throws InterruptedException {
        ReadRecordsRequestUsingIds<TotalCaloriesBurnedRecord> request =
                new ReadRecordsRequestUsingIds.Builder<>(TotalCaloriesBurnedRecord.class)
                        .addId(UUID.randomUUID().toString())
                        .build();
        List<TotalCaloriesBurnedRecord> result = TestUtils.readRecords(request);
        assertThat(result.size()).isEqualTo(0);
    }

    @Test
    public void testReadTotalCaloriesBurnedRecord_usingClientRecordIds()
            throws InterruptedException {
        List<Record> recordList =
                Arrays.asList(
                        getCompleteTotalCaloriesBurnedRecord(),
                        getCompleteTotalCaloriesBurnedRecord());
        List<Record> insertedRecords = TestUtils.insertRecords(recordList);
        readTotalCaloriesBurnedRecordUsingClientId(insertedRecords);
    }

    @Test
    public void testReadTotalCaloriesBurnedRecord_invalidClientRecordIds()
            throws InterruptedException {
        ReadRecordsRequestUsingIds<TotalCaloriesBurnedRecord> request =
                new ReadRecordsRequestUsingIds.Builder<>(TotalCaloriesBurnedRecord.class)
                        .addClientRecordId("abc")
                        .build();
        List<TotalCaloriesBurnedRecord> result = TestUtils.readRecords(request);
        assertThat(result.size()).isEqualTo(0);
    }

    @Test
    public void testReadTotalCaloriesBurnedRecordUsingFilters_default()
            throws InterruptedException {
        List<TotalCaloriesBurnedRecord> oldTotalCaloriesBurnedRecords =
                TestUtils.readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(
                                        TotalCaloriesBurnedRecord.class)
                                .build());
        TotalCaloriesBurnedRecord testRecord = getCompleteTotalCaloriesBurnedRecord();
        TestUtils.insertRecords(Collections.singletonList(testRecord));
        List<TotalCaloriesBurnedRecord> newTotalCaloriesBurnedRecords =
                TestUtils.readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(
                                        TotalCaloriesBurnedRecord.class)
                                .build());
        assertThat(newTotalCaloriesBurnedRecords.size())
                .isEqualTo(oldTotalCaloriesBurnedRecords.size() + 1);
        assertThat(
                        newTotalCaloriesBurnedRecords
                                .get(newTotalCaloriesBurnedRecords.size() - 1)
                                .equals(testRecord))
                .isTrue();
    }

    @Test
    public void testReadTotalCaloriesBurnedRecordUsingFilters_timeFilter()
            throws InterruptedException {
        TimeInstantRangeFilter filter =
                new TimeInstantRangeFilter.Builder()
                        .setStartTime(Instant.now())
                        .setEndTime(Instant.now().plusMillis(3000))
                        .build();
        TotalCaloriesBurnedRecord testRecord = getCompleteTotalCaloriesBurnedRecord();
        TestUtils.insertRecords(Collections.singletonList(testRecord));
        List<TotalCaloriesBurnedRecord> newTotalCaloriesBurnedRecords =
                TestUtils.readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(
                                        TotalCaloriesBurnedRecord.class)
                                .setTimeRangeFilter(filter)
                                .build());
        assertThat(newTotalCaloriesBurnedRecords.size()).isEqualTo(1);
        assertThat(
                        newTotalCaloriesBurnedRecords
                                .get(newTotalCaloriesBurnedRecords.size() - 1)
                                .equals(testRecord))
                .isTrue();
    }

    @Test
    public void testReadTotalCaloriesBurnedRecordUsingFilters_dataFilter_correct()
            throws InterruptedException {
        Context context = ApplicationProvider.getApplicationContext();
        List<TotalCaloriesBurnedRecord> oldTotalCaloriesBurnedRecords =
                TestUtils.readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(
                                        TotalCaloriesBurnedRecord.class)
                                .addDataOrigins(
                                        new DataOrigin.Builder()
                                                .setPackageName(context.getPackageName())
                                                .build())
                                .build());
        TotalCaloriesBurnedRecord testRecord = getCompleteTotalCaloriesBurnedRecord();
        TestUtils.insertRecords(Collections.singletonList(testRecord));
        List<TotalCaloriesBurnedRecord> newTotalCaloriesBurnedRecords =
                TestUtils.readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(
                                        TotalCaloriesBurnedRecord.class)
                                .addDataOrigins(
                                        new DataOrigin.Builder()
                                                .setPackageName(context.getPackageName())
                                                .build())
                                .build());
        assertThat(newTotalCaloriesBurnedRecords.size() - oldTotalCaloriesBurnedRecords.size())
                .isEqualTo(1);
        TotalCaloriesBurnedRecord newRecord =
                newTotalCaloriesBurnedRecords.get(newTotalCaloriesBurnedRecords.size() - 1);
        assertThat(
                        newTotalCaloriesBurnedRecords
                                .get(newTotalCaloriesBurnedRecords.size() - 1)
                                .equals(testRecord))
                .isTrue();
        assertThat(newRecord.getEnergy()).isEqualTo(testRecord.getEnergy());
    }

    @Test
    public void testReadTotalCaloriesBurnedRecordUsingFilters_dataFilter_incorrect()
            throws InterruptedException {
        TestUtils.insertRecords(Collections.singletonList(getCompleteTotalCaloriesBurnedRecord()));
        List<TotalCaloriesBurnedRecord> newTotalCaloriesBurnedRecords =
                TestUtils.readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(
                                        TotalCaloriesBurnedRecord.class)
                                .addDataOrigins(
                                        new DataOrigin.Builder().setPackageName("abc").build())
                                .build());
        assertThat(newTotalCaloriesBurnedRecords.size()).isEqualTo(0);
    }

    @Test
    public void testZoneOffsets() {
        final ZoneOffset defaultZoneOffset =
                ZoneOffset.systemDefault().getRules().getOffset(Instant.now());
        final ZoneOffset startZoneOffset = ZoneOffset.UTC;
        final ZoneOffset endZoneOffset = ZoneOffset.MAX;
        TotalCaloriesBurnedRecord.Builder builder =
                new TotalCaloriesBurnedRecord.Builder(
                        new Metadata.Builder().build(),
                        Instant.now(),
                        Instant.now().plusMillis(1000),
                        Energy.fromCalories(10.0));

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
                                getCompleteTotalCaloriesBurnedRecord(),
                                getCompleteTotalCaloriesBurnedRecord()));

        // read inserted records and verify that the data is same as inserted.
        readTotalCaloriesBurnedRecordUsingIds(insertedRecords);

        // Generate a new set of records that will be used to perform the update operation.
        List<Record> updateRecords =
                Arrays.asList(
                        getCompleteTotalCaloriesBurnedRecord(),
                        getCompleteTotalCaloriesBurnedRecord());

        // Modify the uid of the updateRecords to the uuid that was present in the insert records.
        for (int itr = 0; itr < updateRecords.size(); itr++) {
            updateRecords.set(
                    itr,
                    getTotalCaloriesBurnedRecord_update(
                            updateRecords.get(itr),
                            insertedRecords.get(itr).getMetadata().getId(),
                            insertedRecords.get(itr).getMetadata().getClientRecordId()));
        }

        TestUtils.updateRecords(updateRecords);

        // assert the inserted data has been modified by reading the data.
        readTotalCaloriesBurnedRecordUsingIds(updateRecords);
    }

    @Test
    public void testUpdateRecords_invalidInputRecords_noChangeInDataBase()
            throws InterruptedException {
        List<Record> insertedRecords =
                TestUtils.insertRecords(
                        Arrays.asList(
                                getCompleteTotalCaloriesBurnedRecord(),
                                getCompleteTotalCaloriesBurnedRecord()));

        // read inserted records and verify that the data is same as inserted.
        readTotalCaloriesBurnedRecordUsingIds(insertedRecords);

        // Generate a second set of records that will be used to perform the update operation.
        List<Record> updateRecords =
                Arrays.asList(
                        getCompleteTotalCaloriesBurnedRecord(),
                        getCompleteTotalCaloriesBurnedRecord());

        // Modify the Uid of the updateRecords to the UUID that was present in the insert records,
        // leaving out alternate records so that they have a new UUID which is not present in the
        // dataBase.
        for (int itr = 0; itr < updateRecords.size(); itr++) {
            updateRecords.set(
                    itr,
                    getTotalCaloriesBurnedRecord_update(
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
        readTotalCaloriesBurnedRecordUsingIds(insertedRecords);
    }

    @Test
    public void testUpdateRecords_recordWithInvalidPackageName_noChangeInDataBase()
            throws InterruptedException {
        List<Record> insertedRecords =
                TestUtils.insertRecords(
                        Arrays.asList(
                                getCompleteTotalCaloriesBurnedRecord(),
                                getCompleteTotalCaloriesBurnedRecord()));

        // read inserted records and verify that the data is same as inserted.
        readTotalCaloriesBurnedRecordUsingIds(insertedRecords);

        // Generate a second set of records that will be used to perform the update operation.
        List<Record> updateRecords =
                Arrays.asList(
                        getCompleteTotalCaloriesBurnedRecord(),
                        getCompleteTotalCaloriesBurnedRecord());

        // Modify the Uuid of the updateRecords to the uuid that was present in the insert records.
        for (int itr = 0; itr < updateRecords.size(); itr++) {
            updateRecords.set(
                    itr,
                    getTotalCaloriesBurnedRecord_update(
                            updateRecords.get(itr),
                            insertedRecords.get(itr).getMetadata().getId(),
                            insertedRecords.get(itr).getMetadata().getClientRecordId()));
            //             adding an entry with invalid packageName.
            updateRecords.set(itr, getCompleteTotalCaloriesBurnedRecord());
        }

        try {
            TestUtils.updateRecords(updateRecords);
            Assert.fail("Expected to fail due to invalid package.");
        } catch (Exception exception) {
            // verify that the testcase failed due to invalid argument exception.
            assertThat(exception).isNotNull();
        }

        // assert the inserted data has not been modified by reading the data.
        readTotalCaloriesBurnedRecordUsingIds(insertedRecords);
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
                                .addRecordType(TotalCaloriesBurnedRecord.class)
                                .build());
        ChangeLogsRequest changeLogsRequest =
                new ChangeLogsRequest.Builder(tokenResponse.getToken()).build();
        ChangeLogsResponse response = TestUtils.getChangeLogs(changeLogsRequest);
        assertThat(response.getUpsertedRecords().size()).isEqualTo(0);
        assertThat(response.getDeletedLogs().size()).isEqualTo(0);

        List<Record> testRecord = Collections.singletonList(getCompleteTotalCaloriesBurnedRecord());
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
                        .addRecordType(TotalCaloriesBurnedRecord.class)
                        .build());
        response = TestUtils.getChangeLogs(changeLogsRequest);
        assertThat(response.getDeletedLogs()).isEmpty();
    }

    private void readTotalCaloriesBurnedRecordUsingClientId(List<Record> insertedRecord)
            throws InterruptedException {
        ReadRecordsRequestUsingIds.Builder<TotalCaloriesBurnedRecord> request =
                new ReadRecordsRequestUsingIds.Builder<>(TotalCaloriesBurnedRecord.class);
        for (Record record : insertedRecord) {
            request.addClientRecordId(record.getMetadata().getClientRecordId());
        }
        List<TotalCaloriesBurnedRecord> result = TestUtils.readRecords(request.build());
        assertThat(result.size()).isEqualTo(insertedRecord.size());
        assertThat(result).containsExactlyElementsIn(insertedRecord);
    }

    private void readTotalCaloriesBurnedRecordUsingIds(List<Record> recordList)
            throws InterruptedException {
        List<Record> insertedRecords = TestUtils.insertRecords(recordList);
        ReadRecordsRequestUsingIds.Builder<TotalCaloriesBurnedRecord> request =
                new ReadRecordsRequestUsingIds.Builder<>(TotalCaloriesBurnedRecord.class);
        for (Record record : insertedRecords) {
            request.addId(record.getMetadata().getId());
        }
        List<TotalCaloriesBurnedRecord> result = TestUtils.readRecords(request.build());
        assertThat(result).hasSize(insertedRecords.size());
        assertThat(result).containsExactlyElementsIn(insertedRecords);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCreateTotalCaloriesBurnedRecord_invalidValue() {
        new TotalCaloriesBurnedRecord.Builder(
                        new Metadata.Builder().build(),
                        Instant.now(),
                        Instant.now().plusMillis(1000),
                        Energy.fromCalories(1000000001.0))
                .build();
    }

    TotalCaloriesBurnedRecord getTotalCaloriesBurnedRecord_update(
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
        return new TotalCaloriesBurnedRecord.Builder(
                        metadataWithId,
                        Instant.now(),
                        Instant.now().plusMillis(2000),
                        Energy.fromCalories(20.0))
                .setStartZoneOffset(ZoneOffset.systemDefault().getRules().getOffset(Instant.now()))
                .setEndZoneOffset(ZoneOffset.systemDefault().getRules().getOffset(Instant.now()))
                .build();
    }

    static TotalCaloriesBurnedRecord getCompleteTotalCaloriesBurnedRecord() {

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
        testMetadataBuilder.setClientRecordId("TCBR" + Math.random());

        return new TotalCaloriesBurnedRecord.Builder(
                        testMetadataBuilder.build(),
                        Instant.now(),
                        Instant.now().plusMillis(1000),
                        Energy.fromCalories(10.0))
                .setStartZoneOffset(ZoneOffset.systemDefault().getRules().getOffset(Instant.now()))
                .setEndZoneOffset(ZoneOffset.systemDefault().getRules().getOffset(Instant.now()))
                .build();
    }

    static TotalCaloriesBurnedRecord getBaseTotalCaloriesBurnedRecord() {
        return new TotalCaloriesBurnedRecord.Builder(
                        new Metadata.Builder().build(),
                        Instant.now(),
                        Instant.now().plusMillis(1000),
                        Energy.fromCalories(10.0))
                .build();
    }
}
