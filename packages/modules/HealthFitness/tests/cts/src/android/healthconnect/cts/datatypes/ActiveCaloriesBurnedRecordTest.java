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
import android.health.connect.datatypes.ActiveCaloriesBurnedRecord;
import android.health.connect.datatypes.DataOrigin;
import android.health.connect.datatypes.Device;
import android.health.connect.datatypes.Metadata;
import android.health.connect.datatypes.Record;
import android.health.connect.datatypes.units.Energy;
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
import java.util.UUID;

@AppModeFull(reason = "HealthConnectManager is not accessible to instant apps")
@RunWith(AndroidJUnit4.class)
public class ActiveCaloriesBurnedRecordTest {
    private static final String TAG = "ActiveCaloriesBurnedRecordTest";
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
                ActiveCaloriesBurnedRecord.class,
                new TimeInstantRangeFilter.Builder()
                        .setStartTime(Instant.EPOCH)
                        .setEndTime(Instant.now())
                        .build());
        TestUtils.deleteAllStagedRemoteData();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCreateActiveCaloriesBurnedRecord_invalidValue() {
        new ActiveCaloriesBurnedRecord.Builder(
                        new Metadata.Builder().build(),
                        Instant.now(),
                        Instant.now().plus(1, ChronoUnit.HOURS),
                        Energy.fromCalories(1000000001.0))
                .build();
    }

    @Test
    public void testInsertActiveCaloriesBurnedRecord() throws InterruptedException {
        List<Record> records =
                List.of(
                        getBaseActiveCaloriesBurnedRecord(),
                        getCompleteActiveCaloriesBurnedRecord());
        TestUtils.insertRecords(records);
    }

    @Test
    public void testReadActiveCaloriesBurnedRecord_usingIds() throws InterruptedException {
        List<Record> recordList =
                Arrays.asList(
                        getCompleteActiveCaloriesBurnedRecord(),
                        getCompleteActiveCaloriesBurnedRecord());
        List<Record> insertedRecords = TestUtils.insertRecords(recordList);
        readActiveCaloriesBurnedRecordUsingIds(insertedRecords);
    }

    @Test
    public void testReadActiveCaloriesBurnedRecord_invalidIds() throws InterruptedException {
        ReadRecordsRequestUsingIds<ActiveCaloriesBurnedRecord> request =
                new ReadRecordsRequestUsingIds.Builder<>(ActiveCaloriesBurnedRecord.class)
                        .addId(UUID.randomUUID().toString())
                        .build();
        List<ActiveCaloriesBurnedRecord> result = TestUtils.readRecords(request);
        assertThat(result.size()).isEqualTo(0);
    }

    @Test
    public void testReadActiveCaloriesBurnedRecord_usingClientRecordIds()
            throws InterruptedException {
        List<Record> recordList =
                Arrays.asList(
                        getCompleteActiveCaloriesBurnedRecord(),
                        getCompleteActiveCaloriesBurnedRecord());
        List<Record> insertedRecords = TestUtils.insertRecords(recordList);
        readActiveCaloriesBurnedRecordUsingClientId(insertedRecords);
    }

    @Test
    public void testReadActiveCaloriesBurnedRecord_invalidClientRecordIds()
            throws InterruptedException {
        ReadRecordsRequestUsingIds<ActiveCaloriesBurnedRecord> request =
                new ReadRecordsRequestUsingIds.Builder<>(ActiveCaloriesBurnedRecord.class)
                        .addClientRecordId("abc")
                        .build();
        List<ActiveCaloriesBurnedRecord> result = TestUtils.readRecords(request);
        assertThat(result.size()).isEqualTo(0);
    }

    @Test
    public void testReadActiveCaloriesBurnedRecordUsingFilters_default()
            throws InterruptedException {
        List<ActiveCaloriesBurnedRecord> oldActiveCaloriesBurnedRecords =
                TestUtils.readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(
                                        ActiveCaloriesBurnedRecord.class)
                                .build());
        ActiveCaloriesBurnedRecord testRecord = getCompleteActiveCaloriesBurnedRecord();
        TestUtils.insertRecords(Collections.singletonList(testRecord));
        List<ActiveCaloriesBurnedRecord> newActiveCaloriesBurnedRecords =
                TestUtils.readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(
                                        ActiveCaloriesBurnedRecord.class)
                                .build());
        assertThat(newActiveCaloriesBurnedRecords.size())
                .isEqualTo(oldActiveCaloriesBurnedRecords.size() + 1);
        assertThat(
                        newActiveCaloriesBurnedRecords
                                .get(newActiveCaloriesBurnedRecords.size() - 1)
                                .equals(testRecord))
                .isTrue();
    }

    @Test
    public void testReadActiveCaloriesBurnedRecordUsingFilters_timeFilter()
            throws InterruptedException {
        TimeInstantRangeFilter filter =
                new TimeInstantRangeFilter.Builder()
                        .setStartTime(Instant.now())
                        .setEndTime(Instant.now().plusMillis(3000))
                        .build();
        ActiveCaloriesBurnedRecord testRecord = getCompleteActiveCaloriesBurnedRecord();
        TestUtils.insertRecords(Collections.singletonList(testRecord));
        List<ActiveCaloriesBurnedRecord> newActiveCaloriesBurnedRecords =
                TestUtils.readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(
                                        ActiveCaloriesBurnedRecord.class)
                                .setTimeRangeFilter(filter)
                                .build());
        assertThat(newActiveCaloriesBurnedRecords.size()).isEqualTo(1);
        assertThat(
                        newActiveCaloriesBurnedRecords
                                .get(newActiveCaloriesBurnedRecords.size() - 1)
                                .equals(testRecord))
                .isTrue();
    }

    @Test
    public void testReadActiveCaloriesBurnedRecordUsingFilters_dataFilter_correct()
            throws InterruptedException {
        Context context = ApplicationProvider.getApplicationContext();
        List<ActiveCaloriesBurnedRecord> oldActiveCaloriesBurnedRecords =
                TestUtils.readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(
                                        ActiveCaloriesBurnedRecord.class)
                                .addDataOrigins(
                                        new DataOrigin.Builder()
                                                .setPackageName(context.getPackageName())
                                                .build())
                                .build());
        ActiveCaloriesBurnedRecord testRecord = getCompleteActiveCaloriesBurnedRecord();
        TestUtils.insertRecords(Collections.singletonList(testRecord));
        List<ActiveCaloriesBurnedRecord> newActiveCaloriesBurnedRecords =
                TestUtils.readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(
                                        ActiveCaloriesBurnedRecord.class)
                                .addDataOrigins(
                                        new DataOrigin.Builder()
                                                .setPackageName(context.getPackageName())
                                                .build())
                                .build());
        assertThat(newActiveCaloriesBurnedRecords.size() - oldActiveCaloriesBurnedRecords.size())
                .isEqualTo(1);
        ActiveCaloriesBurnedRecord newRecord =
                newActiveCaloriesBurnedRecords.get(newActiveCaloriesBurnedRecords.size() - 1);
        assertThat(newRecord.equals(testRecord)).isTrue();
        assertThat(newRecord.getEnergy()).isEqualTo(testRecord.getEnergy());
    }

    @Test
    public void testReadActiveCaloriesBurnedRecordUsingFilters_dataFilter_incorrect()
            throws InterruptedException {
        TestUtils.insertRecords(Collections.singletonList(getCompleteActiveCaloriesBurnedRecord()));
        List<ActiveCaloriesBurnedRecord> newActiveCaloriesBurnedRecords =
                TestUtils.readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(
                                        ActiveCaloriesBurnedRecord.class)
                                .addDataOrigins(
                                        new DataOrigin.Builder().setPackageName("abc").build())
                                .build());
        assertThat(newActiveCaloriesBurnedRecords.size()).isEqualTo(0);
    }

    @Test
    public void testDeleteActiveCaloriesBurnedRecord_no_filters() throws InterruptedException {
        String id = TestUtils.insertRecordAndGetId(getCompleteActiveCaloriesBurnedRecord());
        TestUtils.verifyDeleteRecords(new DeleteUsingFiltersRequest.Builder().build());
        TestUtils.assertRecordNotFound(id, ActiveCaloriesBurnedRecord.class);
    }

    @Test
    public void testDeleteActiveCaloriesBurnedRecord_time_filters() throws InterruptedException {
        TimeInstantRangeFilter timeRangeFilter =
                new TimeInstantRangeFilter.Builder()
                        .setStartTime(Instant.now())
                        .setEndTime(Instant.now().plusMillis(1000))
                        .build();
        String id = TestUtils.insertRecordAndGetId(getCompleteActiveCaloriesBurnedRecord());
        TestUtils.verifyDeleteRecords(
                new DeleteUsingFiltersRequest.Builder()
                        .addRecordType(ActiveCaloriesBurnedRecord.class)
                        .setTimeRangeFilter(timeRangeFilter)
                        .build());
        TestUtils.assertRecordNotFound(id, ActiveCaloriesBurnedRecord.class);
    }

    @Test
    public void testDeleteActiveCaloriesBurnedRecord_recordId_filters()
            throws InterruptedException {
        List<Record> records =
                List.of(
                        getBaseActiveCaloriesBurnedRecord(),
                        getCompleteActiveCaloriesBurnedRecord());
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
    public void testDeleteActiveCaloriesBurnedRecord_dataOrigin_filters()
            throws InterruptedException {
        Context context = ApplicationProvider.getApplicationContext();
        String id = TestUtils.insertRecordAndGetId(getCompleteActiveCaloriesBurnedRecord());
        TestUtils.verifyDeleteRecords(
                new DeleteUsingFiltersRequest.Builder()
                        .addDataOrigin(
                                new DataOrigin.Builder()
                                        .setPackageName(context.getPackageName())
                                        .build())
                        .build());
        TestUtils.assertRecordNotFound(id, ActiveCaloriesBurnedRecord.class);
    }

    @Test
    public void testDeleteActiveCaloriesBurnedRecord_dataOrigin_filter_incorrect()
            throws InterruptedException {
        String id = TestUtils.insertRecordAndGetId(getCompleteActiveCaloriesBurnedRecord());
        TestUtils.verifyDeleteRecords(
                new DeleteUsingFiltersRequest.Builder()
                        .addDataOrigin(new DataOrigin.Builder().setPackageName("abc").build())
                        .build());
        TestUtils.assertRecordFound(id, ActiveCaloriesBurnedRecord.class);
    }

    @Test
    public void testDeleteActiveCaloriesBurnedRecord_usingIds() throws InterruptedException {
        List<Record> records =
                List.of(
                        getBaseActiveCaloriesBurnedRecord(),
                        getCompleteActiveCaloriesBurnedRecord());
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
    public void testDeleteActiveCaloriesBurnedRecord_time_range() throws InterruptedException {
        TimeInstantRangeFilter timeRangeFilter =
                new TimeInstantRangeFilter.Builder()
                        .setStartTime(Instant.now())
                        .setEndTime(Instant.now().plusMillis(1000))
                        .build();
        String id = TestUtils.insertRecordAndGetId(getCompleteActiveCaloriesBurnedRecord());
        TestUtils.verifyDeleteRecords(ActiveCaloriesBurnedRecord.class, timeRangeFilter);
        TestUtils.assertRecordNotFound(id, ActiveCaloriesBurnedRecord.class);
    }

    @Test
    public void testZoneOffsets() {
        final ZoneOffset defaultZoneOffset =
                ZoneOffset.systemDefault().getRules().getOffset(Instant.now());
        final ZoneOffset startZoneOffset = ZoneOffset.UTC;
        final ZoneOffset endZoneOffset = ZoneOffset.MAX;
        ActiveCaloriesBurnedRecord.Builder builder =
                new ActiveCaloriesBurnedRecord.Builder(
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
                                getCompleteActiveCaloriesBurnedRecord(),
                                getCompleteActiveCaloriesBurnedRecord()));

        // read inserted records and verify that the data is same as inserted.
        readActiveCaloriesBurnedRecordUsingIds(insertedRecords);

        // Generate a new set of records that will be used to perform the update operation.
        List<Record> updateRecords =
                Arrays.asList(
                        getCompleteActiveCaloriesBurnedRecord(),
                        getCompleteActiveCaloriesBurnedRecord());

        // Modify the uid of the updateRecords to the uuid that was present in the insert records.
        for (int itr = 0; itr < updateRecords.size(); itr++) {
            updateRecords.set(
                    itr,
                    getActiveCaloriesBurnedRecord_update(
                            updateRecords.get(itr),
                            insertedRecords.get(itr).getMetadata().getId(),
                            insertedRecords.get(itr).getMetadata().getClientRecordId()));
        }

        TestUtils.updateRecords(updateRecords);

        // assert the inserted data has been modified by reading the data.
        readActiveCaloriesBurnedRecordUsingIds(updateRecords);
    }

    @Test
    public void testUpdateRecords_invalidInputRecords_noChangeInDataBase()
            throws InterruptedException {
        List<Record> insertedRecords =
                TestUtils.insertRecords(
                        Arrays.asList(
                                getCompleteActiveCaloriesBurnedRecord(),
                                getCompleteActiveCaloriesBurnedRecord()));

        // read inserted records and verify that the data is same as inserted.
        readActiveCaloriesBurnedRecordUsingIds(insertedRecords);

        // Generate a second set of records that will be used to perform the update operation.
        List<Record> updateRecords =
                Arrays.asList(
                        getCompleteActiveCaloriesBurnedRecord(),
                        getCompleteActiveCaloriesBurnedRecord());

        // Modify the Uid of the updateRecords to the UUID that was present in the insert records,
        // leaving out alternate records so that they have a new UUID which is not present in the
        // dataBase.
        for (int itr = 0; itr < updateRecords.size(); itr++) {
            updateRecords.set(
                    itr,
                    getActiveCaloriesBurnedRecord_update(
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
        readActiveCaloriesBurnedRecordUsingIds(insertedRecords);
    }

    @Test
    public void testUpdateRecords_recordWithInvalidPackageName_noChangeInDataBase()
            throws InterruptedException {
        List<Record> insertedRecords =
                TestUtils.insertRecords(
                        Arrays.asList(
                                getCompleteActiveCaloriesBurnedRecord(),
                                getCompleteActiveCaloriesBurnedRecord()));

        // read inserted records and verify that the data is same as inserted.
        readActiveCaloriesBurnedRecordUsingIds(insertedRecords);

        // Generate a second set of records that will be used to perform the update operation.
        List<Record> updateRecords =
                Arrays.asList(
                        getCompleteActiveCaloriesBurnedRecord(),
                        getCompleteActiveCaloriesBurnedRecord());

        // Modify the Uuid of the updateRecords to the uuid that was present in the insert records.
        for (int itr = 0; itr < updateRecords.size(); itr++) {
            updateRecords.set(
                    itr,
                    getActiveCaloriesBurnedRecord_update(
                            updateRecords.get(itr),
                            insertedRecords.get(itr).getMetadata().getId(),
                            insertedRecords.get(itr).getMetadata().getClientRecordId()));
            //             adding an entry with invalid packageName.
            updateRecords.set(itr, getCompleteActiveCaloriesBurnedRecord());
        }

        try {
            TestUtils.updateRecords(updateRecords);
            Assert.fail("Expected to fail due to invalid package.");
        } catch (Exception exception) {
            // verify that the testcase failed due to invalid argument exception.
            assertThat(exception).isNotNull();
        }

        // assert the inserted data has not been modified by reading the data.
        readActiveCaloriesBurnedRecordUsingIds(insertedRecords);
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
                                .addRecordType(ActiveCaloriesBurnedRecord.class)
                                .build());
        ChangeLogsRequest changeLogsRequest =
                new ChangeLogsRequest.Builder(tokenResponse.getToken()).build();
        ChangeLogsResponse response = TestUtils.getChangeLogs(changeLogsRequest);
        assertThat(response.getUpsertedRecords().size()).isEqualTo(0);
        assertThat(response.getDeletedLogs().size()).isEqualTo(0);

        List<Record> testRecord =
                Collections.singletonList(getCompleteActiveCaloriesBurnedRecord());
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
                        .addRecordType(ActiveCaloriesBurnedRecord.class)
                        .build());
        response = TestUtils.getChangeLogs(changeLogsRequest);
        assertThat(response.getDeletedLogs()).isEmpty();
    }

    private void readActiveCaloriesBurnedRecordUsingClientId(List<Record> insertedRecords)
            throws InterruptedException {
        ReadRecordsRequestUsingIds.Builder<ActiveCaloriesBurnedRecord> request =
                new ReadRecordsRequestUsingIds.Builder<>(ActiveCaloriesBurnedRecord.class);
        for (Record record : insertedRecords) {
            request.addClientRecordId(record.getMetadata().getClientRecordId());
        }
        List<ActiveCaloriesBurnedRecord> result = TestUtils.readRecords(request.build());
        assertThat(result).hasSize(insertedRecords.size());
        assertThat(result.containsAll(insertedRecords)).isTrue();
    }

    private void readActiveCaloriesBurnedRecordUsingIds(List<Record> recordList)
            throws InterruptedException {
        ReadRecordsRequestUsingIds.Builder<ActiveCaloriesBurnedRecord> request =
                new ReadRecordsRequestUsingIds.Builder<>(ActiveCaloriesBurnedRecord.class);
        for (Record record : recordList) {
            request.addId(record.getMetadata().getId());
        }
        ReadRecordsRequestUsingIds requestUsingIds = request.build();
        assertThat(requestUsingIds.getRecordType()).isEqualTo(ActiveCaloriesBurnedRecord.class);
        assertThat(requestUsingIds.getRecordIdFilters()).isNotNull();
        List<ActiveCaloriesBurnedRecord> result = TestUtils.readRecords(requestUsingIds);
        assertThat(result).hasSize(recordList.size());
        assertThat(result).containsExactlyElementsIn(recordList);
    }

    ActiveCaloriesBurnedRecord getActiveCaloriesBurnedRecord_update(
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
        return new ActiveCaloriesBurnedRecord.Builder(
                        metadataWithId,
                        Instant.now(),
                        Instant.now().plusMillis(2000),
                        Energy.fromCalories(20.0))
                .setStartZoneOffset(ZoneOffset.systemDefault().getRules().getOffset(Instant.now()))
                .setEndZoneOffset(ZoneOffset.systemDefault().getRules().getOffset(Instant.now()))
                .build();
    }

    static ActiveCaloriesBurnedRecord getBaseActiveCaloriesBurnedRecord() {
        return new ActiveCaloriesBurnedRecord.Builder(
                        new Metadata.Builder().build(),
                        Instant.now(),
                        Instant.now().plusMillis(1000),
                        Energy.fromCalories(10.0))
                .build();
    }

    static ActiveCaloriesBurnedRecord getBaseActiveCaloriesBurnedRecord(double energy, int days) {
        return new ActiveCaloriesBurnedRecord.Builder(
                        new Metadata.Builder().build(),
                        Instant.now().minus(days, ChronoUnit.DAYS),
                        Instant.now().minus(days, ChronoUnit.DAYS).plus(1, ChronoUnit.HOURS),
                        Energy.fromCalories(energy))
                .build();
    }

    static ActiveCaloriesBurnedRecord getCompleteActiveCaloriesBurnedRecord() {
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
        testMetadataBuilder.setRecordingMethod(Metadata.RECORDING_METHOD_ACTIVELY_RECORDED);
        testMetadataBuilder.setClientRecordId("ACBR" + Math.random());

        return new ActiveCaloriesBurnedRecord.Builder(
                        testMetadataBuilder.build(),
                        Instant.now(),
                        Instant.now().plusMillis(1000),
                        Energy.fromCalories(10.0))
                .setStartZoneOffset(ZoneOffset.systemDefault().getRules().getOffset(Instant.now()))
                .setEndZoneOffset(ZoneOffset.systemDefault().getRules().getOffset(Instant.now()))
                .build();
    }
}
