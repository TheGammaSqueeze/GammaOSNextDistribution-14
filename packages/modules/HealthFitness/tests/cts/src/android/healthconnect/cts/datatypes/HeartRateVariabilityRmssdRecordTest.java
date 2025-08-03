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

import static android.healthconnect.cts.utils.DataFactory.generateMetadata;

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
import android.health.connect.datatypes.DataOrigin;
import android.health.connect.datatypes.Device;
import android.health.connect.datatypes.HeartRateVariabilityRmssdRecord;
import android.health.connect.datatypes.Metadata;
import android.health.connect.datatypes.Record;
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
public class HeartRateVariabilityRmssdRecordTest {
    private static final String TAG = "HeartRateVariabilityRmssdRecordTest";
    private static final Instant TIME = Instant.ofEpochMilli((long) 1e9);

    @Rule
    public AssumptionCheckerRule mSupportedHardwareRule =
            new AssumptionCheckerRule(
                    TestUtils::isHardwareSupported, "Tests should run on supported hardware only.");

    @After
    public void tearDown() throws InterruptedException {
        TestUtils.verifyDeleteRecords(
                HeartRateVariabilityRmssdRecord.class,
                new TimeInstantRangeFilter.Builder()
                        .setStartTime(Instant.EPOCH)
                        .setEndTime(Instant.now())
                        .build());
        TestUtils.deleteAllStagedRemoteData();
    }

    @Test
    public void testInsertHeartRateVariabilityRmssdRecord() throws InterruptedException {
        List<Record> records =
                List.of(
                        getBaseHeartRateVariabilityRmssdRecord(),
                        getCompleteHeartRateVariabilityRmssdRecord());
        TestUtils.insertRecords(records);
    }

    @Test
    public void testReadHeartRateVariabilityRmssdRecord_usingIds() throws InterruptedException {
        List<Record> recordList =
                Arrays.asList(
                        getCompleteHeartRateVariabilityRmssdRecord(),
                        getCompleteHeartRateVariabilityRmssdRecord());
        readHeartRateVariabilityRmssdRecordUsingIds(recordList);
    }

    @Test
    public void testReadHeartRateVariabilityRmssdRecord_invalidIds() throws InterruptedException {
        ReadRecordsRequestUsingIds<HeartRateVariabilityRmssdRecord> request =
                new ReadRecordsRequestUsingIds.Builder<>(HeartRateVariabilityRmssdRecord.class)
                        .addId(UUID.randomUUID().toString())
                        .build();
        List<HeartRateVariabilityRmssdRecord> result = TestUtils.readRecords(request);
        assertThat(result.size()).isEqualTo(0);
    }

    @Test
    public void testReadHeartRateVariabilityRmssdRecord_usingClientRecordIds()
            throws InterruptedException {
        List<Record> recordList =
                Arrays.asList(
                        getCompleteHeartRateVariabilityRmssdRecord(),
                        getCompleteHeartRateVariabilityRmssdRecord());
        List<Record> insertedRecords = TestUtils.insertRecords(recordList);
        readHeartRateVariabilityRmssdRecordUsingClientId(insertedRecords);
    }

    @Test
    public void testReadHeartRateVariabilityRmssdRecord_invalidClientRecordIds()
            throws InterruptedException {
        ReadRecordsRequestUsingIds<HeartRateVariabilityRmssdRecord> request =
                new ReadRecordsRequestUsingIds.Builder<>(HeartRateVariabilityRmssdRecord.class)
                        .addClientRecordId("abc")
                        .build();
        List<HeartRateVariabilityRmssdRecord> result = TestUtils.readRecords(request);
        assertThat(result.size()).isEqualTo(0);
    }

    @Test
    public void testReadHeartRateVariabilityRmssdRecordUsingFilters_default()
            throws InterruptedException {
        List<HeartRateVariabilityRmssdRecord> oldHeartRateVariabilityRmssdRecords =
                TestUtils.readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(
                                        HeartRateVariabilityRmssdRecord.class)
                                .build());
        HeartRateVariabilityRmssdRecord testRecord = getCompleteHeartRateVariabilityRmssdRecord();
        TestUtils.insertRecords(Collections.singletonList(testRecord));
        List<HeartRateVariabilityRmssdRecord> newHeartRateVariabilityRmssdRecords =
                TestUtils.readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(
                                        HeartRateVariabilityRmssdRecord.class)
                                .build());
        assertThat(newHeartRateVariabilityRmssdRecords.size())
                .isEqualTo(oldHeartRateVariabilityRmssdRecords.size() + 1);
        assertThat(
                        newHeartRateVariabilityRmssdRecords
                                .get(newHeartRateVariabilityRmssdRecords.size() - 1)
                                .equals(testRecord))
                .isTrue();
    }

    @Test
    public void testReadHeartRateVariabilityRmssdRecordUsingFilters_timeFilter()
            throws InterruptedException {
        TimeInstantRangeFilter filter =
                new TimeInstantRangeFilter.Builder()
                        .setStartTime(Instant.now())
                        .setEndTime(Instant.now().plusMillis(3000))
                        .build();
        HeartRateVariabilityRmssdRecord testRecord = getCompleteHeartRateVariabilityRmssdRecord();
        TestUtils.insertRecords(Collections.singletonList(testRecord));
        List<HeartRateVariabilityRmssdRecord> newHeartRateVariabilityRmssdRecords =
                TestUtils.readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(
                                        HeartRateVariabilityRmssdRecord.class)
                                .setTimeRangeFilter(filter)
                                .build());
        assertThat(newHeartRateVariabilityRmssdRecords.size()).isEqualTo(1);
        assertThat(
                        newHeartRateVariabilityRmssdRecords
                                .get(newHeartRateVariabilityRmssdRecords.size() - 1)
                                .equals(testRecord))
                .isTrue();
    }

    @Test
    public void testReadHeartRateVariabilityRmssdRecordUsingFilters_dataFilter_correct()
            throws InterruptedException {
        Context context = ApplicationProvider.getApplicationContext();
        List<HeartRateVariabilityRmssdRecord> oldHeartRateVariabilityRmssdRecords =
                TestUtils.readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(
                                        HeartRateVariabilityRmssdRecord.class)
                                .addDataOrigins(
                                        new DataOrigin.Builder()
                                                .setPackageName(context.getPackageName())
                                                .build())
                                .build());
        HeartRateVariabilityRmssdRecord testRecord = getCompleteHeartRateVariabilityRmssdRecord();
        TestUtils.insertRecords(Collections.singletonList(testRecord));
        List<HeartRateVariabilityRmssdRecord> newHeartRateVariabilityRmssdRecords =
                TestUtils.readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(
                                        HeartRateVariabilityRmssdRecord.class)
                                .addDataOrigins(
                                        new DataOrigin.Builder()
                                                .setPackageName(context.getPackageName())
                                                .build())
                                .build());
        assertThat(
                        newHeartRateVariabilityRmssdRecords.size()
                                - oldHeartRateVariabilityRmssdRecords.size())
                .isEqualTo(1);
        HeartRateVariabilityRmssdRecord newRecord =
                newHeartRateVariabilityRmssdRecords.get(
                        newHeartRateVariabilityRmssdRecords.size() - 1);
        assertThat(newRecord.equals(testRecord)).isTrue();
    }

    @Test
    public void testReadHeartRateVariabilityRmssdRecordUsingFilters_dataFilter_incorrect()
            throws InterruptedException {
        TestUtils.insertRecords(
                Collections.singletonList(getCompleteHeartRateVariabilityRmssdRecord()));
        List<HeartRateVariabilityRmssdRecord> newHeartRateVariabilityRmssdRecords =
                TestUtils.readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(
                                        HeartRateVariabilityRmssdRecord.class)
                                .addDataOrigins(
                                        new DataOrigin.Builder().setPackageName("abc").build())
                                .build());
        assertThat(newHeartRateVariabilityRmssdRecords.size()).isEqualTo(0);
    }

    @Test
    public void testDeleteHeartRateVariabilityRmssdRecord_no_filters() throws InterruptedException {
        String id = TestUtils.insertRecordAndGetId(getCompleteHeartRateVariabilityRmssdRecord());
        TestUtils.verifyDeleteRecords(new DeleteUsingFiltersRequest.Builder().build());
        TestUtils.assertRecordNotFound(id, HeartRateVariabilityRmssdRecord.class);
    }

    @Test
    public void testDeleteHeartRateVariabilityRmssdRecord_time_filters()
            throws InterruptedException {
        TimeInstantRangeFilter timeRangeFilter =
                new TimeInstantRangeFilter.Builder()
                        .setStartTime(Instant.now())
                        .setEndTime(Instant.now().plusMillis(1000))
                        .build();
        String id = TestUtils.insertRecordAndGetId(getCompleteHeartRateVariabilityRmssdRecord());
        TestUtils.verifyDeleteRecords(
                new DeleteUsingFiltersRequest.Builder()
                        .addRecordType(HeartRateVariabilityRmssdRecord.class)
                        .setTimeRangeFilter(timeRangeFilter)
                        .build());
        TestUtils.assertRecordNotFound(id, HeartRateVariabilityRmssdRecord.class);
    }

    @Test
    public void testDeleteHeartRateVariabilityRmssdRecord_recordId_filters()
            throws InterruptedException {
        List<Record> records =
                List.of(
                        getBaseHeartRateVariabilityRmssdRecord(),
                        getCompleteHeartRateVariabilityRmssdRecord());
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
    public void testDeleteHeartRateVariabilityRmssdRecord_dataOrigin_filters()
            throws InterruptedException {
        Context context = ApplicationProvider.getApplicationContext();
        String id = TestUtils.insertRecordAndGetId(getCompleteHeartRateVariabilityRmssdRecord());
        TestUtils.verifyDeleteRecords(
                new DeleteUsingFiltersRequest.Builder()
                        .addDataOrigin(
                                new DataOrigin.Builder()
                                        .setPackageName(context.getPackageName())
                                        .build())
                        .build());
        TestUtils.assertRecordNotFound(id, HeartRateVariabilityRmssdRecord.class);
    }

    @Test
    public void testDeleteHeartRateVariabilityRmssdRecord_dataOrigin_filter_incorrect()
            throws InterruptedException {
        String id = TestUtils.insertRecordAndGetId(getCompleteHeartRateVariabilityRmssdRecord());
        TestUtils.verifyDeleteRecords(
                new DeleteUsingFiltersRequest.Builder()
                        .addDataOrigin(new DataOrigin.Builder().setPackageName("abc").build())
                        .build());
        TestUtils.assertRecordFound(id, HeartRateVariabilityRmssdRecord.class);
    }

    @Test
    public void testDeleteHeartRateVariabilityRmssdRecord_usingIds() throws InterruptedException {
        List<Record> records =
                List.of(
                        getBaseHeartRateVariabilityRmssdRecord(),
                        getCompleteHeartRateVariabilityRmssdRecord());
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
    public void testDeleteHeartRateVariabilityRmssdRecord_time_range() throws InterruptedException {
        TimeInstantRangeFilter timeRangeFilter =
                new TimeInstantRangeFilter.Builder()
                        .setStartTime(Instant.now())
                        .setEndTime(Instant.now().plusMillis(1000))
                        .build();
        String id = TestUtils.insertRecordAndGetId(getCompleteHeartRateVariabilityRmssdRecord());
        TestUtils.verifyDeleteRecords(HeartRateVariabilityRmssdRecord.class, timeRangeFilter);
        TestUtils.assertRecordNotFound(id, HeartRateVariabilityRmssdRecord.class);
    }

    @Test
    public void testZoneOffsets() {
        final ZoneOffset defaultZoneOffset =
                ZoneOffset.systemDefault().getRules().getOffset(Instant.now());
        final ZoneOffset zoneOffset = ZoneOffset.UTC;
        HeartRateVariabilityRmssdRecord.Builder builder =
                new HeartRateVariabilityRmssdRecord.Builder(
                        new Metadata.Builder().build(), Instant.now(), 1.3);

        assertThat(builder.setZoneOffset(zoneOffset).build().getZoneOffset()).isEqualTo(zoneOffset);
        assertThat(builder.clearZoneOffset().build().getZoneOffset()).isEqualTo(defaultZoneOffset);
    }

    @Test
    public void testHeartRateVariabilityRmssd_buildSession_buildCorrectObject() {
        HeartRateVariabilityRmssdRecord record =
                new HeartRateVariabilityRmssdRecord.Builder(generateMetadata(), TIME, 1.3).build();
        assertThat(record.getTime()).isEqualTo(TIME);
    }

    @Test
    public void testHeartRateVariabilityRmssd_buildEqualSessions_equalsReturnsTrue() {
        Metadata metadata = generateMetadata();
        HeartRateVariabilityRmssdRecord record =
                new HeartRateVariabilityRmssdRecord.Builder(metadata, TIME, 1.3).build();
        HeartRateVariabilityRmssdRecord record2 =
                new HeartRateVariabilityRmssdRecord.Builder(metadata, TIME, 1.3).build();
        assertThat(record).isEqualTo(record2);
    }

    @Test
    public void testHeartRateVariabilityRmssd_buildSessionWithAllFields_buildCorrectObject() {
        HeartRateVariabilityRmssdRecord record =
                new HeartRateVariabilityRmssdRecord.Builder(generateMetadata(), TIME, 1.3)
                        .setZoneOffset(ZoneOffset.MAX)
                        .build();
        assertThat(record.getZoneOffset()).isEqualTo(ZoneOffset.MAX);
        assertThat(record.getHeartRateVariabilityMillis()).isEqualTo(1.3);
    }

    @Test
    public void testUpdateRecords_validInput_dataBaseUpdatedSuccessfully()
            throws InterruptedException {

        List<Record> insertedRecords =
                TestUtils.insertRecords(
                        Arrays.asList(
                                getCompleteHeartRateVariabilityRmssdRecord(),
                                getCompleteHeartRateVariabilityRmssdRecord()));

        // read inserted records and verify that the data is same as inserted.
        readHeartRateVariabilityRmssdRecordUsingIds(insertedRecords);

        // Generate a new set of records that will be used to perform the update operation.
        List<Record> updateRecords =
                Arrays.asList(
                        getCompleteHeartRateVariabilityRmssdRecord(),
                        getCompleteHeartRateVariabilityRmssdRecord());

        // Modify the uid of the updateRecords to the uuid that was present in the insert records.
        for (int itr = 0; itr < updateRecords.size(); itr++) {
            updateRecords.set(
                    itr,
                    getHeartRateVariabilityRmssdRecord_update(
                            updateRecords.get(itr),
                            insertedRecords.get(itr).getMetadata().getId(),
                            insertedRecords.get(itr).getMetadata().getClientRecordId()));
        }

        TestUtils.updateRecords(updateRecords);

        // assert the inserted data has been modified by reading the data.
        readHeartRateVariabilityRmssdRecordUsingIds(updateRecords);
    }

    @Test
    public void testUpdateRecords_invalidInputRecords_noChangeInDataBase()
            throws InterruptedException {
        List<Record> insertedRecords =
                TestUtils.insertRecords(
                        Arrays.asList(
                                getCompleteHeartRateVariabilityRmssdRecord(),
                                getCompleteHeartRateVariabilityRmssdRecord()));

        // read inserted records and verify that the data is same as inserted.
        readHeartRateVariabilityRmssdRecordUsingIds(insertedRecords);

        // Generate a second set of records that will be used to perform the update operation.
        List<Record> updateRecords =
                Arrays.asList(
                        getCompleteHeartRateVariabilityRmssdRecord(),
                        getCompleteHeartRateVariabilityRmssdRecord());

        // Modify the Uid of the updateRecords to the UUID that was present in the insert records,
        // leaving out alternate records so that they have a new UUID which is not present in the
        // dataBase.
        for (int itr = 0; itr < updateRecords.size(); itr++) {
            updateRecords.set(
                    itr,
                    getHeartRateVariabilityRmssdRecord_update(
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
        readHeartRateVariabilityRmssdRecordUsingIds(insertedRecords);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCreateHeartRateVariabilityRecord_invalidValue() {
        new HeartRateVariabilityRmssdRecord.Builder(
                        new Metadata.Builder().build(), Instant.now(), 0.39)
                .build();
    }

    @Test
    public void testUpdateRecords_recordWithInvalidPackageName_noChangeInDataBase()
            throws InterruptedException {
        List<Record> insertedRecords =
                TestUtils.insertRecords(
                        Arrays.asList(
                                getCompleteHeartRateVariabilityRmssdRecord(),
                                getCompleteHeartRateVariabilityRmssdRecord()));

        // read inserted records and verify that the data is same as inserted.
        readHeartRateVariabilityRmssdRecordUsingIds(insertedRecords);

        // Generate a second set of records that will be used to perform the update operation.
        List<Record> updateRecords =
                Arrays.asList(
                        getCompleteHeartRateVariabilityRmssdRecord(),
                        getCompleteHeartRateVariabilityRmssdRecord());

        // Modify the Uuid of the updateRecords to the uuid that was present in the insert records.
        for (int itr = 0; itr < updateRecords.size(); itr++) {
            updateRecords.set(
                    itr,
                    getHeartRateVariabilityRmssdRecord_update(
                            updateRecords.get(itr),
                            insertedRecords.get(itr).getMetadata().getId(),
                            insertedRecords.get(itr).getMetadata().getClientRecordId()));
            //             adding an entry with invalid packageName.
            updateRecords.set(itr, getCompleteHeartRateVariabilityRmssdRecord());
        }

        try {
            TestUtils.updateRecords(updateRecords);
            Assert.fail("Expected to fail due to invalid package.");
        } catch (Exception exception) {
            // verify that the testcase failed due to invalid argument exception.
            assertThat(exception).isNotNull();
        }

        // assert the inserted data has not been modified by reading the data.
        readHeartRateVariabilityRmssdRecordUsingIds(insertedRecords);
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
                                .addRecordType(HeartRateVariabilityRmssdRecord.class)
                                .build());
        ChangeLogsRequest changeLogsRequest =
                new ChangeLogsRequest.Builder(tokenResponse.getToken()).build();
        ChangeLogsResponse response = TestUtils.getChangeLogs(changeLogsRequest);
        assertThat(response.getUpsertedRecords().size()).isEqualTo(0);
        assertThat(response.getDeletedLogs().size()).isEqualTo(0);

        List<Record> testRecord =
                Collections.singletonList(getCompleteHeartRateVariabilityRmssdRecord());
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
                        .addRecordType(HeartRateVariabilityRmssdRecord.class)
                        .build());
        response = TestUtils.getChangeLogs(changeLogsRequest);
        assertThat(response.getDeletedLogs()).isEmpty();
    }

    private void readHeartRateVariabilityRmssdRecordUsingClientId(List<Record> insertedRecord)
            throws InterruptedException {
        ReadRecordsRequestUsingIds.Builder<HeartRateVariabilityRmssdRecord> request =
                new ReadRecordsRequestUsingIds.Builder<>(HeartRateVariabilityRmssdRecord.class);
        for (Record record : insertedRecord) {
            request.addClientRecordId(record.getMetadata().getClientRecordId());
        }
        List<HeartRateVariabilityRmssdRecord> result = TestUtils.readRecords(request.build());
        assertThat(result.size()).isEqualTo(insertedRecord.size());
        assertThat(result).containsExactlyElementsIn(insertedRecord);
    }

    private void readHeartRateVariabilityRmssdRecordUsingIds(List<Record> recordList)
            throws InterruptedException {
        List<Record> insertedRecords = TestUtils.insertRecords(recordList);
        ReadRecordsRequestUsingIds.Builder<HeartRateVariabilityRmssdRecord> request =
                new ReadRecordsRequestUsingIds.Builder<>(HeartRateVariabilityRmssdRecord.class);
        for (Record record : insertedRecords) {
            request.addId(record.getMetadata().getId());
        }
        ReadRecordsRequestUsingIds requestUsingIds = request.build();
        assertThat(requestUsingIds.getRecordType())
                .isEqualTo(HeartRateVariabilityRmssdRecord.class);
        assertThat(requestUsingIds.getRecordIdFilters()).isNotNull();
        List<HeartRateVariabilityRmssdRecord> result = TestUtils.readRecords(requestUsingIds);
        assertThat(result).hasSize(insertedRecords.size());
        assertThat(result).containsExactlyElementsIn(recordList);
    }

    HeartRateVariabilityRmssdRecord getHeartRateVariabilityRmssdRecord_update(
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
        return new HeartRateVariabilityRmssdRecord.Builder(metadataWithId, Instant.now(), 1.99)
                .setZoneOffset(ZoneOffset.systemDefault().getRules().getOffset(Instant.now()))
                .build();
    }

    static HeartRateVariabilityRmssdRecord getBaseHeartRateVariabilityRmssdRecord() {
        return new HeartRateVariabilityRmssdRecord.Builder(
                        new Metadata.Builder().build(), Instant.now(), 1.99)
                .build();
    }

    static HeartRateVariabilityRmssdRecord getHeartRateVariabilityRmssdRecord(double power) {
        return new HeartRateVariabilityRmssdRecord.Builder(
                        new Metadata.Builder().build(), Instant.now(), 1.39)
                .build();
    }

    private static HeartRateVariabilityRmssdRecord getCompleteHeartRateVariabilityRmssdRecord() {
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
        testMetadataBuilder.setClientRecordId("HRV" + Math.random());
        testMetadataBuilder.setRecordingMethod(Metadata.RECORDING_METHOD_ACTIVELY_RECORDED);

        return new HeartRateVariabilityRmssdRecord.Builder(
                        testMetadataBuilder.build(), Instant.now(), 1.3)
                .build();
    }
}
