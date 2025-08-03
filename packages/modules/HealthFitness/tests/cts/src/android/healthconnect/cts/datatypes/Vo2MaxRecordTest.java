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
import android.health.connect.datatypes.DataOrigin;
import android.health.connect.datatypes.Device;
import android.health.connect.datatypes.Metadata;
import android.health.connect.datatypes.Record;
import android.health.connect.datatypes.Vo2MaxRecord;
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
public class Vo2MaxRecordTest {
    private static final String TAG = "Vo2MaxRecordTest";

    @Rule
    public AssumptionCheckerRule mSupportedHardwareRule =
            new AssumptionCheckerRule(
                    TestUtils::isHardwareSupported, "Tests should run on supported hardware only.");

    @After
    public void tearDown() throws InterruptedException {
        TestUtils.verifyDeleteRecords(
                Vo2MaxRecord.class,
                new TimeInstantRangeFilter.Builder()
                        .setStartTime(Instant.EPOCH)
                        .setEndTime(Instant.now())
                        .build());
        TestUtils.deleteAllStagedRemoteData();
    }

    @Test
    public void testInsertVo2MaxRecord() throws InterruptedException {
        List<Record> records = List.of(getBaseVo2MaxRecord(), getCompleteVo2MaxRecord());
        TestUtils.insertRecords(records);
    }

    @Test
    public void testReadVo2MaxRecord_usingIds() throws InterruptedException {
        List<Record> recordList =
                Arrays.asList(getCompleteVo2MaxRecord(), getCompleteVo2MaxRecord());
        List<Record> insertedRecords = TestUtils.insertRecords(recordList);
        readVo2MaxRecordUsingIds(insertedRecords);
    }

    @Test
    public void testReadVo2MaxRecord_invalidIds() throws InterruptedException {
        ReadRecordsRequestUsingIds<Vo2MaxRecord> request =
                new ReadRecordsRequestUsingIds.Builder<>(Vo2MaxRecord.class)
                        .addId(UUID.randomUUID().toString())
                        .build();
        List<Vo2MaxRecord> result = TestUtils.readRecords(request);
        assertThat(result.size()).isEqualTo(0);
    }

    @Test
    public void testReadVo2MaxRecord_usingClientRecordIds() throws InterruptedException {
        List<Record> recordList =
                Arrays.asList(getCompleteVo2MaxRecord(), getCompleteVo2MaxRecord());
        List<Record> insertedRecords = TestUtils.insertRecords(recordList);
        readVo2MaxRecordUsingClientId(insertedRecords);
    }

    @Test
    public void testReadVo2MaxRecord_invalidClientRecordIds() throws InterruptedException {
        ReadRecordsRequestUsingIds<Vo2MaxRecord> request =
                new ReadRecordsRequestUsingIds.Builder<>(Vo2MaxRecord.class)
                        .addClientRecordId("abc")
                        .build();
        List<Vo2MaxRecord> result = TestUtils.readRecords(request);
        assertThat(result.size()).isEqualTo(0);
    }

    @Test
    public void testReadVo2MaxRecordUsingFilters_default() throws InterruptedException {
        List<Vo2MaxRecord> oldVo2MaxRecords =
                TestUtils.readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(Vo2MaxRecord.class).build());
        Vo2MaxRecord testRecord = getCompleteVo2MaxRecord();
        TestUtils.insertRecords(Collections.singletonList(testRecord));
        List<Vo2MaxRecord> newVo2MaxRecords =
                TestUtils.readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(Vo2MaxRecord.class).build());
        assertThat(newVo2MaxRecords.size()).isEqualTo(oldVo2MaxRecords.size() + 1);
        assertThat(newVo2MaxRecords.get(newVo2MaxRecords.size() - 1).equals(testRecord)).isTrue();
    }

    @Test
    public void testReadVo2MaxRecordUsingFilters_timeFilter() throws InterruptedException {
        TimeInstantRangeFilter filter =
                new TimeInstantRangeFilter.Builder()
                        .setStartTime(Instant.now())
                        .setEndTime(Instant.now().plusMillis(3000))
                        .build();
        Vo2MaxRecord testRecord = getCompleteVo2MaxRecord();
        TestUtils.insertRecords(Collections.singletonList(testRecord));
        List<Vo2MaxRecord> newVo2MaxRecords =
                TestUtils.readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(Vo2MaxRecord.class)
                                .setTimeRangeFilter(filter)
                                .build());
        assertThat(newVo2MaxRecords.size()).isEqualTo(1);
        assertThat(newVo2MaxRecords.get(newVo2MaxRecords.size() - 1).equals(testRecord)).isTrue();
    }

    @Test
    public void testReadVo2MaxRecordUsingFilters_dataFilter_correct() throws InterruptedException {
        Context context = ApplicationProvider.getApplicationContext();
        List<Vo2MaxRecord> oldVo2MaxRecords =
                TestUtils.readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(Vo2MaxRecord.class)
                                .addDataOrigins(
                                        new DataOrigin.Builder()
                                                .setPackageName(context.getPackageName())
                                                .build())
                                .build());
        Vo2MaxRecord testRecord = getCompleteVo2MaxRecord();
        TestUtils.insertRecords(Collections.singletonList(testRecord));
        List<Vo2MaxRecord> newVo2MaxRecords =
                TestUtils.readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(Vo2MaxRecord.class)
                                .addDataOrigins(
                                        new DataOrigin.Builder()
                                                .setPackageName(context.getPackageName())
                                                .build())
                                .build());
        assertThat(newVo2MaxRecords.size() - oldVo2MaxRecords.size()).isEqualTo(1);
        Vo2MaxRecord newRecord = newVo2MaxRecords.get(newVo2MaxRecords.size() - 1);
        assertThat(newRecord.equals(testRecord)).isTrue();
        assertThat(newRecord.getMeasurementMethod()).isEqualTo(testRecord.getMeasurementMethod());
        assertThat(newRecord.getVo2MillilitersPerMinuteKilogram())
                .isEqualTo(testRecord.getVo2MillilitersPerMinuteKilogram());
    }

    @Test
    public void testReadVo2MaxRecordUsingFilters_dataFilter_incorrect()
            throws InterruptedException {
        TestUtils.insertRecords(Collections.singletonList(getCompleteVo2MaxRecord()));
        List<Vo2MaxRecord> newVo2MaxRecords =
                TestUtils.readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(Vo2MaxRecord.class)
                                .addDataOrigins(
                                        new DataOrigin.Builder().setPackageName("abc").build())
                                .build());
        assertThat(newVo2MaxRecords.size()).isEqualTo(0);
    }

    private void readVo2MaxRecordUsingClientId(List<Record> insertedRecord)
            throws InterruptedException {
        ReadRecordsRequestUsingIds.Builder<Vo2MaxRecord> request =
                new ReadRecordsRequestUsingIds.Builder<>(Vo2MaxRecord.class);
        for (Record record : insertedRecord) {
            request.addClientRecordId(record.getMetadata().getClientRecordId());
        }
        List<Vo2MaxRecord> result = TestUtils.readRecords(request.build());
        assertThat(result.size()).isEqualTo(insertedRecord.size());
        assertThat(result).containsExactlyElementsIn(insertedRecord);
    }

    private void readVo2MaxRecordUsingIds(List<Record> recordList) throws InterruptedException {
        ReadRecordsRequestUsingIds.Builder<Vo2MaxRecord> request =
                new ReadRecordsRequestUsingIds.Builder<>(Vo2MaxRecord.class);
        for (Record record : recordList) {
            request.addId(record.getMetadata().getId());
        }
        ReadRecordsRequestUsingIds requestUsingIds = request.build();
        assertThat(requestUsingIds.getRecordType()).isEqualTo(Vo2MaxRecord.class);
        assertThat(requestUsingIds.getRecordIdFilters()).isNotNull();
        List<Vo2MaxRecord> result = TestUtils.readRecords(requestUsingIds);
        assertThat(result).hasSize(recordList.size());
        assertThat(result).containsExactlyElementsIn(recordList);
    }

    @Test
    public void testDeleteVo2MaxRecord_no_filters() throws InterruptedException {
        String id = TestUtils.insertRecordAndGetId(getCompleteVo2MaxRecord());
        TestUtils.verifyDeleteRecords(new DeleteUsingFiltersRequest.Builder().build());
        TestUtils.assertRecordNotFound(id, Vo2MaxRecord.class);
    }

    @Test
    public void testDeleteVo2MaxRecord_time_filters() throws InterruptedException {
        TimeInstantRangeFilter timeRangeFilter =
                new TimeInstantRangeFilter.Builder()
                        .setStartTime(Instant.now())
                        .setEndTime(Instant.now().plusMillis(1000))
                        .build();
        String id = TestUtils.insertRecordAndGetId(getCompleteVo2MaxRecord());
        TestUtils.verifyDeleteRecords(
                new DeleteUsingFiltersRequest.Builder()
                        .addRecordType(Vo2MaxRecord.class)
                        .setTimeRangeFilter(timeRangeFilter)
                        .build());
        TestUtils.assertRecordNotFound(id, Vo2MaxRecord.class);
    }

    @Test
    public void testDeleteVo2MaxRecord_recordId_filters() throws InterruptedException {
        List<Record> records = List.of(getBaseVo2MaxRecord(), getCompleteVo2MaxRecord());
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
    public void testDeleteVo2MaxRecord_dataOrigin_filters() throws InterruptedException {
        Context context = ApplicationProvider.getApplicationContext();
        String id = TestUtils.insertRecordAndGetId(getCompleteVo2MaxRecord());
        TestUtils.verifyDeleteRecords(
                new DeleteUsingFiltersRequest.Builder()
                        .addDataOrigin(
                                new DataOrigin.Builder()
                                        .setPackageName(context.getPackageName())
                                        .build())
                        .build());
        TestUtils.assertRecordNotFound(id, Vo2MaxRecord.class);
    }

    @Test
    public void testDeleteVo2MaxRecord_dataOrigin_filter_incorrect() throws InterruptedException {
        String id = TestUtils.insertRecordAndGetId(getCompleteVo2MaxRecord());
        TestUtils.verifyDeleteRecords(
                new DeleteUsingFiltersRequest.Builder()
                        .addDataOrigin(new DataOrigin.Builder().setPackageName("abc").build())
                        .build());
        TestUtils.assertRecordFound(id, Vo2MaxRecord.class);
    }

    @Test
    public void testDeleteVo2MaxRecord_usingIds() throws InterruptedException {
        List<Record> records = List.of(getBaseVo2MaxRecord(), getCompleteVo2MaxRecord());
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
    public void testDeleteVo2MaxRecord_time_range() throws InterruptedException {
        TimeInstantRangeFilter timeRangeFilter =
                new TimeInstantRangeFilter.Builder()
                        .setStartTime(Instant.now())
                        .setEndTime(Instant.now().plusMillis(1000))
                        .build();
        String id = TestUtils.insertRecordAndGetId(getCompleteVo2MaxRecord());
        TestUtils.verifyDeleteRecords(Vo2MaxRecord.class, timeRangeFilter);
        TestUtils.assertRecordNotFound(id, Vo2MaxRecord.class);
    }

    @Test
    public void testZoneOffsets() {
        final ZoneOffset defaultZoneOffset =
                ZoneOffset.systemDefault().getRules().getOffset(Instant.now());
        final ZoneOffset zoneOffset = ZoneOffset.UTC;
        Vo2MaxRecord.Builder builder =
                new Vo2MaxRecord.Builder(new Metadata.Builder().build(), Instant.now(), 0, 10.0);

        assertThat(builder.setZoneOffset(zoneOffset).build().getZoneOffset()).isEqualTo(zoneOffset);
        assertThat(builder.clearZoneOffset().build().getZoneOffset()).isEqualTo(defaultZoneOffset);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCreateVo2MaxRecord_invalidValue() {
        new Vo2MaxRecord.Builder(new Metadata.Builder().build(), Instant.now(), 1, 100.1).build();
    }

    @Test
    public void testUpdateRecords_validInput_dataBaseUpdatedSuccessfully()
            throws InterruptedException {

        List<Record> insertedRecords =
                TestUtils.insertRecords(
                        Arrays.asList(getCompleteVo2MaxRecord(), getCompleteVo2MaxRecord()));

        // read inserted records and verify that the data is same as inserted.
        readVo2MaxRecordUsingIds(insertedRecords);

        // Generate a new set of records that will be used to perform the update operation.
        List<Record> updateRecords =
                Arrays.asList(getCompleteVo2MaxRecord(), getCompleteVo2MaxRecord());

        // Modify the uid of the updateRecords to the uuid that was present in the insert records.
        for (int itr = 0; itr < updateRecords.size(); itr++) {
            updateRecords.set(
                    itr,
                    getVo2MaxRecord_update(
                            updateRecords.get(itr),
                            insertedRecords.get(itr).getMetadata().getId(),
                            insertedRecords.get(itr).getMetadata().getClientRecordId()));
        }

        TestUtils.updateRecords(updateRecords);

        // assert the inserted data has been modified by reading the data.
        readVo2MaxRecordUsingIds(updateRecords);
    }

    @Test
    public void testUpdateRecords_invalidInputRecords_noChangeInDataBase()
            throws InterruptedException {
        List<Record> insertedRecords =
                TestUtils.insertRecords(
                        Arrays.asList(getCompleteVo2MaxRecord(), getCompleteVo2MaxRecord()));

        // read inserted records and verify that the data is same as inserted.
        readVo2MaxRecordUsingIds(insertedRecords);

        // Generate a second set of records that will be used to perform the update operation.
        List<Record> updateRecords =
                Arrays.asList(getCompleteVo2MaxRecord(), getCompleteVo2MaxRecord());

        // Modify the Uid of the updateRecords to the UUID that was present in the insert records,
        // leaving out alternate records so that they have a new UUID which is not present in the
        // dataBase.
        for (int itr = 0; itr < updateRecords.size(); itr++) {
            updateRecords.set(
                    itr,
                    getVo2MaxRecord_update(
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
        readVo2MaxRecordUsingIds(insertedRecords);
    }

    @Test
    public void testUpdateRecords_recordWithInvalidPackageName_noChangeInDataBase()
            throws InterruptedException {
        List<Record> insertedRecords =
                TestUtils.insertRecords(
                        Arrays.asList(getCompleteVo2MaxRecord(), getCompleteVo2MaxRecord()));

        // read inserted records and verify that the data is same as inserted.
        readVo2MaxRecordUsingIds(insertedRecords);

        // Generate a second set of records that will be used to perform the update operation.
        List<Record> updateRecords =
                Arrays.asList(getCompleteVo2MaxRecord(), getCompleteVo2MaxRecord());

        // Modify the Uuid of the updateRecords to the uuid that was present in the insert records.
        for (int itr = 0; itr < updateRecords.size(); itr++) {
            updateRecords.set(
                    itr,
                    getVo2MaxRecord_update(
                            updateRecords.get(itr),
                            insertedRecords.get(itr).getMetadata().getId(),
                            insertedRecords.get(itr).getMetadata().getClientRecordId()));
            //             adding an entry with invalid packageName.
            updateRecords.set(itr, getCompleteVo2MaxRecord());
        }

        try {
            TestUtils.updateRecords(updateRecords);
            Assert.fail("Expected to fail due to invalid package.");
        } catch (Exception exception) {
            // verify that the testcase failed due to invalid argument exception.
            assertThat(exception).isNotNull();
        }

        // assert the inserted data has not been modified by reading the data.
        readVo2MaxRecordUsingIds(insertedRecords);
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
                                .addRecordType(Vo2MaxRecord.class)
                                .build());
        ChangeLogsRequest changeLogsRequest =
                new ChangeLogsRequest.Builder(tokenResponse.getToken()).build();
        ChangeLogsResponse response = TestUtils.getChangeLogs(changeLogsRequest);
        assertThat(response.getUpsertedRecords().size()).isEqualTo(0);
        assertThat(response.getDeletedLogs().size()).isEqualTo(0);

        List<Record> testRecord = Collections.singletonList(getCompleteVo2MaxRecord());
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
                new DeleteUsingFiltersRequest.Builder().addRecordType(Vo2MaxRecord.class).build());
        response = TestUtils.getChangeLogs(changeLogsRequest);
        assertThat(response.getDeletedLogs()).isEmpty();
    }

    Vo2MaxRecord getVo2MaxRecord_update(Record record, String id, String clientRecordId) {
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
        return new Vo2MaxRecord.Builder(metadataWithId, Instant.now(), 2, 20.0)
                .setZoneOffset(ZoneOffset.systemDefault().getRules().getOffset(Instant.now()))
                .build();
    }

    private static Vo2MaxRecord getBaseVo2MaxRecord() {
        return new Vo2MaxRecord.Builder(new Metadata.Builder().build(), Instant.now(), 1, 10.0)
                .build();
    }

    private static Vo2MaxRecord getCompleteVo2MaxRecord() {
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
        testMetadataBuilder.setClientRecordId("VMR" + Math.random());
        testMetadataBuilder.setRecordingMethod(Metadata.RECORDING_METHOD_ACTIVELY_RECORDED);

        return new Vo2MaxRecord.Builder(testMetadataBuilder.build(), Instant.now(), 1, 10.0)
                .setZoneOffset(ZoneOffset.systemDefault().getRules().getOffset(Instant.now()))
                .build();
    }
}
