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
import android.health.connect.datatypes.OxygenSaturationRecord;
import android.health.connect.datatypes.Record;
import android.health.connect.datatypes.units.Percentage;
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
public class OxygenSaturationRecordTest {
    private static final String TAG = "OxygenSaturationRecordTest";

    @Rule
    public AssumptionCheckerRule mSupportedHardwareRule =
            new AssumptionCheckerRule(
                    TestUtils::isHardwareSupported, "Tests should run on supported hardware only.");

    @After
    public void tearDown() throws InterruptedException {
        TestUtils.verifyDeleteRecords(
                OxygenSaturationRecord.class,
                new TimeInstantRangeFilter.Builder()
                        .setStartTime(Instant.EPOCH)
                        .setEndTime(Instant.now())
                        .build());
        TestUtils.deleteAllStagedRemoteData();
    }

    @Test
    public void testInsertOxygenSaturationRecord() throws InterruptedException {
        List<Record> records =
                List.of(getBaseOxygenSaturationRecord(), getCompleteOxygenSaturationRecord());
        TestUtils.insertRecords(records);
    }

    @Test
    public void testReadOxygenSaturationRecord_usingIds() throws InterruptedException {
        List<Record> recordList =
                Arrays.asList(
                        getCompleteOxygenSaturationRecord(), getCompleteOxygenSaturationRecord());
        List<Record> insertedRecords = TestUtils.insertRecords(recordList);
        readOxygenSaturationRecordUsingIds(insertedRecords);
    }

    @Test
    public void testReadOxygenSaturationRecord_invalidIds() throws InterruptedException {
        ReadRecordsRequestUsingIds<OxygenSaturationRecord> request =
                new ReadRecordsRequestUsingIds.Builder<>(OxygenSaturationRecord.class)
                        .addId(UUID.randomUUID().toString())
                        .build();
        List<OxygenSaturationRecord> result = TestUtils.readRecords(request);
        assertThat(result.size()).isEqualTo(0);
    }

    @Test
    public void testReadOxygenSaturationRecord_usingClientRecordIds() throws InterruptedException {
        List<Record> recordList =
                Arrays.asList(
                        getCompleteOxygenSaturationRecord(), getCompleteOxygenSaturationRecord());
        List<Record> insertedRecords = TestUtils.insertRecords(recordList);
        readOxygenSaturationRecordUsingClientId(insertedRecords);
    }

    @Test
    public void testReadOxygenSaturationRecord_invalidClientRecordIds()
            throws InterruptedException {
        ReadRecordsRequestUsingIds<OxygenSaturationRecord> request =
                new ReadRecordsRequestUsingIds.Builder<>(OxygenSaturationRecord.class)
                        .addClientRecordId("abc")
                        .build();
        List<OxygenSaturationRecord> result = TestUtils.readRecords(request);
        assertThat(result.size()).isEqualTo(0);
    }

    @Test
    public void testReadOxygenSaturationRecordUsingFilters_default() throws InterruptedException {
        List<OxygenSaturationRecord> oldOxygenSaturationRecords =
                TestUtils.readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(OxygenSaturationRecord.class)
                                .build());
        OxygenSaturationRecord testRecord = getCompleteOxygenSaturationRecord();
        TestUtils.insertRecords(Collections.singletonList(testRecord));
        List<OxygenSaturationRecord> newOxygenSaturationRecords =
                TestUtils.readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(OxygenSaturationRecord.class)
                                .build());
        assertThat(newOxygenSaturationRecords.size())
                .isEqualTo(oldOxygenSaturationRecords.size() + 1);
        assertThat(
                        newOxygenSaturationRecords
                                .get(newOxygenSaturationRecords.size() - 1)
                                .equals(testRecord))
                .isTrue();
    }

    @Test
    public void testReadOxygenSaturationRecordUsingFilters_timeFilter()
            throws InterruptedException {
        TimeInstantRangeFilter filter =
                new TimeInstantRangeFilter.Builder()
                        .setStartTime(Instant.now())
                        .setEndTime(Instant.now().plusMillis(3000))
                        .build();
        OxygenSaturationRecord testRecord = getCompleteOxygenSaturationRecord();
        TestUtils.insertRecords(Collections.singletonList(testRecord));
        List<OxygenSaturationRecord> newOxygenSaturationRecords =
                TestUtils.readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(OxygenSaturationRecord.class)
                                .setTimeRangeFilter(filter)
                                .build());
        assertThat(newOxygenSaturationRecords.size()).isEqualTo(1);
        assertThat(
                        newOxygenSaturationRecords
                                .get(newOxygenSaturationRecords.size() - 1)
                                .equals(testRecord))
                .isTrue();
    }

    @Test
    public void testReadOxygenSaturationRecordUsingFilters_dataFilter_correct()
            throws InterruptedException {
        Context context = ApplicationProvider.getApplicationContext();
        List<OxygenSaturationRecord> oldOxygenSaturationRecords =
                TestUtils.readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(OxygenSaturationRecord.class)
                                .addDataOrigins(
                                        new DataOrigin.Builder()
                                                .setPackageName(context.getPackageName())
                                                .build())
                                .build());
        OxygenSaturationRecord testRecord = getCompleteOxygenSaturationRecord();
        TestUtils.insertRecords(Collections.singletonList(testRecord));
        List<OxygenSaturationRecord> newOxygenSaturationRecords =
                TestUtils.readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(OxygenSaturationRecord.class)
                                .addDataOrigins(
                                        new DataOrigin.Builder()
                                                .setPackageName(context.getPackageName())
                                                .build())
                                .build());
        assertThat(newOxygenSaturationRecords.size() - oldOxygenSaturationRecords.size())
                .isEqualTo(1);
        OxygenSaturationRecord newRecord =
                newOxygenSaturationRecords.get(newOxygenSaturationRecords.size() - 1);
        assertThat(newRecord.equals(testRecord)).isTrue();
        assertThat(newRecord.getPercentage()).isEqualTo(testRecord.getPercentage());
    }

    @Test
    public void testReadOxygenSaturationRecordUsingFilters_dataFilter_incorrect()
            throws InterruptedException {
        TestUtils.insertRecords(Collections.singletonList(getCompleteOxygenSaturationRecord()));
        List<OxygenSaturationRecord> newOxygenSaturationRecords =
                TestUtils.readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(OxygenSaturationRecord.class)
                                .addDataOrigins(
                                        new DataOrigin.Builder().setPackageName("abc").build())
                                .build());
        assertThat(newOxygenSaturationRecords.size()).isEqualTo(0);
    }

    private void readOxygenSaturationRecordUsingClientId(List<Record> insertedRecord)
            throws InterruptedException {
        ReadRecordsRequestUsingIds.Builder<OxygenSaturationRecord> request =
                new ReadRecordsRequestUsingIds.Builder<>(OxygenSaturationRecord.class);
        for (Record record : insertedRecord) {
            request.addClientRecordId(record.getMetadata().getClientRecordId());
        }
        List<OxygenSaturationRecord> result = TestUtils.readRecords(request.build());
        assertThat(result.size()).isEqualTo(insertedRecord.size());
        assertThat(result).containsExactlyElementsIn(insertedRecord);
    }

    private void readOxygenSaturationRecordUsingIds(List<Record> recordList)
            throws InterruptedException {
        ReadRecordsRequestUsingIds.Builder<OxygenSaturationRecord> request =
                new ReadRecordsRequestUsingIds.Builder<>(OxygenSaturationRecord.class);
        for (Record record : recordList) {
            request.addId(record.getMetadata().getId());
        }
        ReadRecordsRequestUsingIds requestUsingIds = request.build();
        assertThat(requestUsingIds.getRecordType()).isEqualTo(OxygenSaturationRecord.class);
        assertThat(requestUsingIds.getRecordIdFilters()).isNotNull();
        List<OxygenSaturationRecord> result = TestUtils.readRecords(requestUsingIds);
        assertThat(result).hasSize(recordList.size());
        assertThat(result).containsExactlyElementsIn(recordList);
    }

    @Test
    public void testDeleteOxygenSaturationRecord_no_filters() throws InterruptedException {
        String id = TestUtils.insertRecordAndGetId(getCompleteOxygenSaturationRecord());
        TestUtils.verifyDeleteRecords(new DeleteUsingFiltersRequest.Builder().build());
        TestUtils.assertRecordNotFound(id, OxygenSaturationRecord.class);
    }

    @Test
    public void testDeleteOxygenSaturationRecord_time_filters() throws InterruptedException {
        TimeInstantRangeFilter timeRangeFilter =
                new TimeInstantRangeFilter.Builder()
                        .setStartTime(Instant.now())
                        .setEndTime(Instant.now().plusMillis(1000))
                        .build();
        String id = TestUtils.insertRecordAndGetId(getCompleteOxygenSaturationRecord());
        TestUtils.verifyDeleteRecords(
                new DeleteUsingFiltersRequest.Builder()
                        .addRecordType(OxygenSaturationRecord.class)
                        .setTimeRangeFilter(timeRangeFilter)
                        .build());
        TestUtils.assertRecordNotFound(id, OxygenSaturationRecord.class);
    }

    @Test
    public void testDeleteOxygenSaturationRecord_recordId_filters() throws InterruptedException {
        List<Record> records =
                List.of(getBaseOxygenSaturationRecord(), getCompleteOxygenSaturationRecord());
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
    public void testDeleteOxygenSaturationRecord_dataOrigin_filters() throws InterruptedException {
        Context context = ApplicationProvider.getApplicationContext();
        String id = TestUtils.insertRecordAndGetId(getCompleteOxygenSaturationRecord());
        TestUtils.verifyDeleteRecords(
                new DeleteUsingFiltersRequest.Builder()
                        .addDataOrigin(
                                new DataOrigin.Builder()
                                        .setPackageName(context.getPackageName())
                                        .build())
                        .build());
        TestUtils.assertRecordNotFound(id, OxygenSaturationRecord.class);
    }

    @Test
    public void testDeleteOxygenSaturationRecord_dataOrigin_filter_incorrect()
            throws InterruptedException {
        String id = TestUtils.insertRecordAndGetId(getCompleteOxygenSaturationRecord());
        TestUtils.verifyDeleteRecords(
                new DeleteUsingFiltersRequest.Builder()
                        .addDataOrigin(new DataOrigin.Builder().setPackageName("abc").build())
                        .build());
        TestUtils.assertRecordFound(id, OxygenSaturationRecord.class);
    }

    @Test
    public void testDeleteOxygenSaturationRecord_usingIds() throws InterruptedException {
        List<Record> records =
                List.of(getBaseOxygenSaturationRecord(), getCompleteOxygenSaturationRecord());
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
    public void testDeleteOxygenSaturationRecord_time_range() throws InterruptedException {
        TimeInstantRangeFilter timeRangeFilter =
                new TimeInstantRangeFilter.Builder()
                        .setStartTime(Instant.now())
                        .setEndTime(Instant.now().plusMillis(1000))
                        .build();
        String id = TestUtils.insertRecordAndGetId(getCompleteOxygenSaturationRecord());
        TestUtils.verifyDeleteRecords(OxygenSaturationRecord.class, timeRangeFilter);
        TestUtils.assertRecordNotFound(id, OxygenSaturationRecord.class);
    }

    @Test
    public void testZoneOffsets() {
        final ZoneOffset defaultZoneOffset =
                ZoneOffset.systemDefault().getRules().getOffset(Instant.now());
        final ZoneOffset zoneOffset = ZoneOffset.UTC;
        OxygenSaturationRecord.Builder builder =
                new OxygenSaturationRecord.Builder(
                        new Metadata.Builder().build(), Instant.now(), Percentage.fromValue(10.0));

        assertThat(builder.setZoneOffset(zoneOffset).build().getZoneOffset()).isEqualTo(zoneOffset);
        assertThat(builder.clearZoneOffset().build().getZoneOffset()).isEqualTo(defaultZoneOffset);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCreateOxygenSaturationRecord_invalidValue() {
        new OxygenSaturationRecord.Builder(
                        new Metadata.Builder().build(), Instant.now(), Percentage.fromValue(100.1))
                .build();
    }

    @Test
    public void testUpdateRecords_validInput_dataBaseUpdatedSuccessfully()
            throws InterruptedException {

        List<Record> insertedRecords =
                TestUtils.insertRecords(
                        Arrays.asList(
                                getCompleteOxygenSaturationRecord(),
                                getCompleteOxygenSaturationRecord()));

        // read inserted records and verify that the data is same as inserted.
        readOxygenSaturationRecordUsingIds(insertedRecords);

        // Generate a new set of records that will be used to perform the update operation.
        List<Record> updateRecords =
                Arrays.asList(
                        getCompleteOxygenSaturationRecord(), getCompleteOxygenSaturationRecord());

        // Modify the uid of the updateRecords to the uuid that was present in the insert records.
        for (int itr = 0; itr < updateRecords.size(); itr++) {
            updateRecords.set(
                    itr,
                    getOxygenSaturationRecord_update(
                            updateRecords.get(itr),
                            insertedRecords.get(itr).getMetadata().getId(),
                            insertedRecords.get(itr).getMetadata().getClientRecordId()));
        }

        TestUtils.updateRecords(updateRecords);

        // assert the inserted data has been modified by reading the data.
        readOxygenSaturationRecordUsingIds(updateRecords);
    }

    @Test
    public void testUpdateRecords_invalidInputRecords_noChangeInDataBase()
            throws InterruptedException {
        List<Record> insertedRecords =
                TestUtils.insertRecords(
                        Arrays.asList(
                                getCompleteOxygenSaturationRecord(),
                                getCompleteOxygenSaturationRecord()));

        // read inserted records and verify that the data is same as inserted.
        readOxygenSaturationRecordUsingIds(insertedRecords);

        // Generate a second set of records that will be used to perform the update operation.
        List<Record> updateRecords =
                Arrays.asList(
                        getCompleteOxygenSaturationRecord(), getCompleteOxygenSaturationRecord());

        // Modify the Uid of the updateRecords to the UUID that was present in the insert records,
        // leaving out alternate records so that they have a new UUID which is not present in the
        // dataBase.
        for (int itr = 0; itr < updateRecords.size(); itr++) {
            updateRecords.set(
                    itr,
                    getOxygenSaturationRecord_update(
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
        readOxygenSaturationRecordUsingIds(insertedRecords);
    }

    @Test
    public void testUpdateRecords_recordWithInvalidPackageName_noChangeInDataBase()
            throws InterruptedException {
        List<Record> insertedRecords =
                TestUtils.insertRecords(
                        Arrays.asList(
                                getCompleteOxygenSaturationRecord(),
                                getCompleteOxygenSaturationRecord()));

        // read inserted records and verify that the data is same as inserted.
        readOxygenSaturationRecordUsingIds(insertedRecords);

        // Generate a second set of records that will be used to perform the update operation.
        List<Record> updateRecords =
                Arrays.asList(
                        getCompleteOxygenSaturationRecord(), getCompleteOxygenSaturationRecord());

        // Modify the Uuid of the updateRecords to the uuid that was present in the insert records.
        for (int itr = 0; itr < updateRecords.size(); itr++) {
            updateRecords.set(
                    itr,
                    getOxygenSaturationRecord_update(
                            updateRecords.get(itr),
                            insertedRecords.get(itr).getMetadata().getId(),
                            insertedRecords.get(itr).getMetadata().getClientRecordId()));
            //             adding an entry with invalid packageName.
            updateRecords.set(itr, getCompleteOxygenSaturationRecord());
        }

        try {
            TestUtils.updateRecords(updateRecords);
            Assert.fail("Expected to fail due to invalid package.");
        } catch (Exception exception) {
            // verify that the testcase failed due to invalid argument exception.
            assertThat(exception).isNotNull();
        }

        // assert the inserted data has not been modified by reading the data.
        readOxygenSaturationRecordUsingIds(insertedRecords);
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
                                .addRecordType(OxygenSaturationRecord.class)
                                .build());
        ChangeLogsRequest changeLogsRequest =
                new ChangeLogsRequest.Builder(tokenResponse.getToken()).build();
        ChangeLogsResponse response = TestUtils.getChangeLogs(changeLogsRequest);
        assertThat(response.getUpsertedRecords().size()).isEqualTo(0);
        assertThat(response.getDeletedLogs().size()).isEqualTo(0);

        List<Record> testRecord = Collections.singletonList(getCompleteOxygenSaturationRecord());
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
                        .addRecordType(OxygenSaturationRecord.class)
                        .build());
        response = TestUtils.getChangeLogs(changeLogsRequest);
        assertThat(response.getDeletedLogs()).isEmpty();
    }

    OxygenSaturationRecord getOxygenSaturationRecord_update(
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
        return new OxygenSaturationRecord.Builder(
                        metadataWithId, Instant.now(), Percentage.fromValue(20.0))
                .setZoneOffset(ZoneOffset.systemDefault().getRules().getOffset(Instant.now()))
                .build();
    }

    private static OxygenSaturationRecord getBaseOxygenSaturationRecord() {
        return new OxygenSaturationRecord.Builder(
                        new Metadata.Builder().build(), Instant.now(), Percentage.fromValue(10.0))
                .build();
    }

    private static OxygenSaturationRecord getCompleteOxygenSaturationRecord() {
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
        testMetadataBuilder.setClientRecordId("OSR" + Math.random());
        testMetadataBuilder.setRecordingMethod(Metadata.RECORDING_METHOD_ACTIVELY_RECORDED);

        return new OxygenSaturationRecord.Builder(
                        testMetadataBuilder.build(), Instant.now(), Percentage.fromValue(10.0))
                .setZoneOffset(ZoneOffset.systemDefault().getRules().getOffset(Instant.now()))
                .build();
    }
}
