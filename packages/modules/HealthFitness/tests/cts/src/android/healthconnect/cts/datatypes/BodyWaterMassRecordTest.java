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
import android.health.connect.datatypes.BodyWaterMassRecord;
import android.health.connect.datatypes.DataOrigin;
import android.health.connect.datatypes.Device;
import android.health.connect.datatypes.Metadata;
import android.health.connect.datatypes.Record;
import android.health.connect.datatypes.units.Mass;
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
public class BodyWaterMassRecordTest {
    private static final String TAG = "BodyWaterMassRecordTest";
    private static final Instant TIME = Instant.ofEpochMilli((long) 1e9);

    @Rule
    public AssumptionCheckerRule mSupportedHardwareRule =
            new AssumptionCheckerRule(
                    TestUtils::isHardwareSupported, "Tests should run on supported hardware only.");

    @After
    public void tearDown() throws InterruptedException {
        TestUtils.verifyDeleteRecords(
                BodyWaterMassRecord.class,
                new TimeInstantRangeFilter.Builder()
                        .setStartTime(Instant.EPOCH)
                        .setEndTime(Instant.now())
                        .build());
        TestUtils.deleteAllStagedRemoteData();
    }

    @Test
    public void testInsertBodyWaterMassRecord() throws InterruptedException {
        List<Record> records =
                List.of(getBaseBodyWaterMassRecord(), getCompleteBodyWaterMassRecord());
        TestUtils.insertRecords(records);
    }

    @Test
    public void testReadBodyWaterMassRecord_usingIds() throws InterruptedException {
        List<Record> recordList =
                Arrays.asList(getCompleteBodyWaterMassRecord(), getCompleteBodyWaterMassRecord());
        readBodyWaterMassRecordUsingIds(recordList);
    }

    @Test
    public void testReadBodyWaterMassRecord_invalidIds() throws InterruptedException {
        ReadRecordsRequestUsingIds<BodyWaterMassRecord> request =
                new ReadRecordsRequestUsingIds.Builder<>(BodyWaterMassRecord.class)
                        .addId(UUID.randomUUID().toString())
                        .build();
        List<BodyWaterMassRecord> result = TestUtils.readRecords(request);
        assertThat(result.size()).isEqualTo(0);
    }

    @Test
    public void testReadBodyWaterMassRecord_usingClientRecordIds() throws InterruptedException {
        List<Record> recordList =
                Arrays.asList(getCompleteBodyWaterMassRecord(), getCompleteBodyWaterMassRecord());
        List<Record> insertedRecords = TestUtils.insertRecords(recordList);
        readBodyWaterMassRecordUsingClientId(insertedRecords);
    }

    @Test
    public void testReadBodyWaterMassRecord_invalidClientRecordIds() throws InterruptedException {
        ReadRecordsRequestUsingIds<BodyWaterMassRecord> request =
                new ReadRecordsRequestUsingIds.Builder<>(BodyWaterMassRecord.class)
                        .addClientRecordId("abc")
                        .build();
        List<BodyWaterMassRecord> result = TestUtils.readRecords(request);
        assertThat(result.size()).isEqualTo(0);
    }

    @Test
    public void testReadBodyWaterMassRecordUsingFilters_default() throws InterruptedException {
        List<BodyWaterMassRecord> oldBodyWaterMassRecords =
                TestUtils.readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(BodyWaterMassRecord.class)
                                .build());
        BodyWaterMassRecord testRecord = getCompleteBodyWaterMassRecord();
        TestUtils.insertRecords(Collections.singletonList(testRecord));
        List<BodyWaterMassRecord> newBodyWaterMassRecords =
                TestUtils.readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(BodyWaterMassRecord.class)
                                .build());
        assertThat(newBodyWaterMassRecords.size()).isEqualTo(oldBodyWaterMassRecords.size() + 1);
        assertThat(
                        newBodyWaterMassRecords
                                .get(newBodyWaterMassRecords.size() - 1)
                                .equals(testRecord))
                .isTrue();
    }

    @Test
    public void testReadBodyWaterMassRecordUsingFilters_timeFilter() throws InterruptedException {
        TimeInstantRangeFilter filter =
                new TimeInstantRangeFilter.Builder()
                        .setStartTime(Instant.now())
                        .setEndTime(Instant.now().plusMillis(3000))
                        .build();
        BodyWaterMassRecord testRecord = getCompleteBodyWaterMassRecord();
        TestUtils.insertRecords(Collections.singletonList(testRecord));
        List<BodyWaterMassRecord> newBodyWaterMassRecords =
                TestUtils.readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(BodyWaterMassRecord.class)
                                .setTimeRangeFilter(filter)
                                .build());
        assertThat(newBodyWaterMassRecords.size()).isEqualTo(1);
        assertThat(
                        newBodyWaterMassRecords
                                .get(newBodyWaterMassRecords.size() - 1)
                                .equals(testRecord))
                .isTrue();
    }

    @Test
    public void testReadBodyWaterMassRecordUsingFilters_dataFilter_correct()
            throws InterruptedException {
        Context context = ApplicationProvider.getApplicationContext();
        List<BodyWaterMassRecord> oldBodyWaterMassRecords =
                TestUtils.readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(BodyWaterMassRecord.class)
                                .addDataOrigins(
                                        new DataOrigin.Builder()
                                                .setPackageName(context.getPackageName())
                                                .build())
                                .build());
        BodyWaterMassRecord testRecord = getCompleteBodyWaterMassRecord();
        TestUtils.insertRecords(Collections.singletonList(testRecord));
        List<BodyWaterMassRecord> newBodyWaterMassRecords =
                TestUtils.readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(BodyWaterMassRecord.class)
                                .addDataOrigins(
                                        new DataOrigin.Builder()
                                                .setPackageName(context.getPackageName())
                                                .build())
                                .build());
        assertThat(newBodyWaterMassRecords.size() - oldBodyWaterMassRecords.size()).isEqualTo(1);
        BodyWaterMassRecord newRecord =
                newBodyWaterMassRecords.get(newBodyWaterMassRecords.size() - 1);
        assertThat(newRecord.equals(testRecord)).isTrue();
    }

    @Test
    public void testReadBodyWaterMassRecordUsingFilters_dataFilter_incorrect()
            throws InterruptedException {
        TestUtils.insertRecords(Collections.singletonList(getCompleteBodyWaterMassRecord()));
        List<BodyWaterMassRecord> newBodyWaterMassRecords =
                TestUtils.readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(BodyWaterMassRecord.class)
                                .addDataOrigins(
                                        new DataOrigin.Builder().setPackageName("abc").build())
                                .build());
        assertThat(newBodyWaterMassRecords.size()).isEqualTo(0);
    }

    @Test
    public void testDeleteBodyWaterMassRecord_no_filters() throws InterruptedException {
        String id = TestUtils.insertRecordAndGetId(getCompleteBodyWaterMassRecord());
        TestUtils.verifyDeleteRecords(new DeleteUsingFiltersRequest.Builder().build());
        TestUtils.assertRecordNotFound(id, BodyWaterMassRecord.class);
    }

    @Test
    public void testDeleteBodyWaterMassRecord_time_filters() throws InterruptedException {
        TimeInstantRangeFilter timeRangeFilter =
                new TimeInstantRangeFilter.Builder()
                        .setStartTime(Instant.now())
                        .setEndTime(Instant.now().plusMillis(1000))
                        .build();
        String id = TestUtils.insertRecordAndGetId(getCompleteBodyWaterMassRecord());
        TestUtils.verifyDeleteRecords(
                new DeleteUsingFiltersRequest.Builder()
                        .addRecordType(BodyWaterMassRecord.class)
                        .setTimeRangeFilter(timeRangeFilter)
                        .build());
        TestUtils.assertRecordNotFound(id, BodyWaterMassRecord.class);
    }

    @Test
    public void testDeleteBodyWaterMassRecord_recordId_filters() throws InterruptedException {
        List<Record> records =
                List.of(getBaseBodyWaterMassRecord(), getCompleteBodyWaterMassRecord());
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
    public void testDeleteBodyWaterMassRecord_dataOrigin_filters() throws InterruptedException {
        Context context = ApplicationProvider.getApplicationContext();
        String id = TestUtils.insertRecordAndGetId(getCompleteBodyWaterMassRecord());
        TestUtils.verifyDeleteRecords(
                new DeleteUsingFiltersRequest.Builder()
                        .addDataOrigin(
                                new DataOrigin.Builder()
                                        .setPackageName(context.getPackageName())
                                        .build())
                        .build());
        TestUtils.assertRecordNotFound(id, BodyWaterMassRecord.class);
    }

    @Test
    public void testDeleteBodyWaterMassRecord_dataOrigin_filter_incorrect()
            throws InterruptedException {
        String id = TestUtils.insertRecordAndGetId(getCompleteBodyWaterMassRecord());
        TestUtils.verifyDeleteRecords(
                new DeleteUsingFiltersRequest.Builder()
                        .addDataOrigin(new DataOrigin.Builder().setPackageName("abc").build())
                        .build());
        TestUtils.assertRecordFound(id, BodyWaterMassRecord.class);
    }

    @Test
    public void testDeleteBodyWaterMassRecord_usingIds() throws InterruptedException {
        List<Record> records =
                List.of(getBaseBodyWaterMassRecord(), getCompleteBodyWaterMassRecord());
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
    public void testDeleteBodyWaterMassRecord_time_range() throws InterruptedException {
        TimeInstantRangeFilter timeRangeFilter =
                new TimeInstantRangeFilter.Builder()
                        .setStartTime(Instant.now())
                        .setEndTime(Instant.now().plusMillis(1000))
                        .build();
        String id = TestUtils.insertRecordAndGetId(getCompleteBodyWaterMassRecord());
        TestUtils.verifyDeleteRecords(BodyWaterMassRecord.class, timeRangeFilter);
        TestUtils.assertRecordNotFound(id, BodyWaterMassRecord.class);
    }

    @Test
    public void testZoneOffsets() {
        final ZoneOffset defaultZoneOffset =
                ZoneOffset.systemDefault().getRules().getOffset(Instant.now());
        final ZoneOffset zoneOffset = ZoneOffset.UTC;
        BodyWaterMassRecord.Builder builder =
                new BodyWaterMassRecord.Builder(
                        new Metadata.Builder().build(), Instant.now(), Mass.fromGrams(1));

        assertThat(builder.setZoneOffset(zoneOffset).build().getZoneOffset()).isEqualTo(zoneOffset);
        assertThat(builder.clearZoneOffset().build().getZoneOffset()).isEqualTo(defaultZoneOffset);
    }

    @Test
    public void testBodyWaterMass_buildSession_buildCorrectObject() {
        BodyWaterMassRecord record =
                new BodyWaterMassRecord.Builder(generateMetadata(), TIME, Mass.fromGrams(40))
                        .build();
        assertThat(record.getTime()).isEqualTo(TIME);
    }

    @Test
    public void testBodyWaterMass_buildEqualSessions_equalsReturnsTrue() {
        Metadata metadata = generateMetadata();
        BodyWaterMassRecord record =
                new BodyWaterMassRecord.Builder(metadata, TIME, Mass.fromGrams(40)).build();
        BodyWaterMassRecord record2 =
                new BodyWaterMassRecord.Builder(metadata, TIME, Mass.fromGrams(40)).build();
        assertThat(record).isEqualTo(record2);
    }

    @Test
    public void testBodyWaterMass_buildSessionWithAllFields_buildCorrectObject() {
        BodyWaterMassRecord record =
                new BodyWaterMassRecord.Builder(generateMetadata(), TIME, Mass.fromGrams(40))
                        .setZoneOffset(ZoneOffset.MAX)
                        .build();
        assertThat(record.getZoneOffset()).isEqualTo(ZoneOffset.MAX);
        assertThat(record.getBodyWaterMass()).isEqualTo(Mass.fromGrams(40));
    }

    @Test
    public void testUpdateRecords_validInput_dataBaseUpdatedSuccessfully()
            throws InterruptedException {

        List<Record> insertedRecords =
                TestUtils.insertRecords(
                        Arrays.asList(
                                getCompleteBodyWaterMassRecord(),
                                getCompleteBodyWaterMassRecord()));

        // read inserted records and verify that the data is same as inserted.
        readBodyWaterMassRecordUsingIds(insertedRecords);

        // Generate a new set of records that will be used to perform the update operation.
        List<Record> updateRecords =
                Arrays.asList(getCompleteBodyWaterMassRecord(), getCompleteBodyWaterMassRecord());

        // Modify the uid of the updateRecords to the uuid that was present in the insert records.
        for (int itr = 0; itr < updateRecords.size(); itr++) {
            updateRecords.set(
                    itr,
                    getBodyWaterMassRecord_update(
                            updateRecords.get(itr),
                            insertedRecords.get(itr).getMetadata().getId(),
                            insertedRecords.get(itr).getMetadata().getClientRecordId()));
        }

        TestUtils.updateRecords(updateRecords);

        // assert the inserted data has been modified by reading the data.
        readBodyWaterMassRecordUsingIds(updateRecords);
    }

    @Test
    public void testUpdateRecords_invalidInputRecords_noChangeInDataBase()
            throws InterruptedException {
        List<Record> insertedRecords =
                TestUtils.insertRecords(
                        Arrays.asList(
                                getCompleteBodyWaterMassRecord(),
                                getCompleteBodyWaterMassRecord()));

        // read inserted records and verify that the data is same as inserted.
        readBodyWaterMassRecordUsingIds(insertedRecords);

        // Generate a second set of records that will be used to perform the update operation.
        List<Record> updateRecords =
                Arrays.asList(getCompleteBodyWaterMassRecord(), getCompleteBodyWaterMassRecord());

        // Modify the Uid of the updateRecords to the UUID that was present in the insert records,
        // leaving out alternate records so that they have a new UUID which is not present in the
        // dataBase.
        for (int itr = 0; itr < updateRecords.size(); itr++) {
            updateRecords.set(
                    itr,
                    getBodyWaterMassRecord_update(
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
        readBodyWaterMassRecordUsingIds(insertedRecords);
    }

    @Test
    public void testUpdateRecords_recordWithInvalidPackageName_noChangeInDataBase()
            throws InterruptedException {
        List<Record> insertedRecords =
                TestUtils.insertRecords(
                        Arrays.asList(
                                getCompleteBodyWaterMassRecord(),
                                getCompleteBodyWaterMassRecord()));

        // read inserted records and verify that the data is same as inserted.
        readBodyWaterMassRecordUsingIds(insertedRecords);

        // Generate a second set of records that will be used to perform the update operation.
        List<Record> updateRecords =
                Arrays.asList(getCompleteBodyWaterMassRecord(), getCompleteBodyWaterMassRecord());

        // Modify the Uuid of the updateRecords to the uuid that was present in the insert records.
        for (int itr = 0; itr < updateRecords.size(); itr++) {
            updateRecords.set(
                    itr,
                    getBodyWaterMassRecord_update(
                            updateRecords.get(itr),
                            insertedRecords.get(itr).getMetadata().getId(),
                            insertedRecords.get(itr).getMetadata().getClientRecordId()));
            //             adding an entry with invalid packageName.
            updateRecords.set(itr, getCompleteBodyWaterMassRecord());
        }

        try {
            TestUtils.updateRecords(updateRecords);
            Assert.fail("Expected to fail due to invalid package.");
        } catch (Exception exception) {
            // verify that the testcase failed due to invalid argument exception.
            assertThat(exception).isNotNull();
        }

        // assert the inserted data has not been modified by reading the data.
        readBodyWaterMassRecordUsingIds(insertedRecords);
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
                                .addRecordType(BodyWaterMassRecord.class)
                                .build());
        ChangeLogsRequest changeLogsRequest =
                new ChangeLogsRequest.Builder(tokenResponse.getToken()).build();
        ChangeLogsResponse response = TestUtils.getChangeLogs(changeLogsRequest);
        assertThat(response.getUpsertedRecords().size()).isEqualTo(0);
        assertThat(response.getDeletedLogs().size()).isEqualTo(0);

        List<Record> testRecord = Collections.singletonList(getCompleteBodyWaterMassRecord());
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
                        .addRecordType(BodyWaterMassRecord.class)
                        .build());
        response = TestUtils.getChangeLogs(changeLogsRequest);
        assertThat(response.getDeletedLogs()).isEmpty();
    }

    private void readBodyWaterMassRecordUsingClientId(List<Record> insertedRecord)
            throws InterruptedException {
        ReadRecordsRequestUsingIds.Builder<BodyWaterMassRecord> request =
                new ReadRecordsRequestUsingIds.Builder<>(BodyWaterMassRecord.class);
        for (Record record : insertedRecord) {
            request.addClientRecordId(record.getMetadata().getClientRecordId());
        }
        List<BodyWaterMassRecord> result = TestUtils.readRecords(request.build());
        assertThat(result.size()).isEqualTo(insertedRecord.size());
        assertThat(result).containsExactlyElementsIn(insertedRecord);
    }

    private void readBodyWaterMassRecordUsingIds(List<Record> recordList)
            throws InterruptedException {
        List<Record> insertedRecords = TestUtils.insertRecords(recordList);
        ReadRecordsRequestUsingIds.Builder<BodyWaterMassRecord> request =
                new ReadRecordsRequestUsingIds.Builder<>(BodyWaterMassRecord.class);
        for (Record record : insertedRecords) {
            request.addId(record.getMetadata().getId());
        }
        ReadRecordsRequestUsingIds requestUsingIds = request.build();
        assertThat(requestUsingIds.getRecordType()).isEqualTo(BodyWaterMassRecord.class);
        assertThat(requestUsingIds.getRecordIdFilters()).isNotNull();
        List<BodyWaterMassRecord> result = TestUtils.readRecords(requestUsingIds);
        assertThat(result).hasSize(insertedRecords.size());
        assertThat(result).containsExactlyElementsIn(recordList);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCreateBodyWaterMassRecord_invalidValue() {
        new BodyWaterMassRecord.Builder(
                        new Metadata.Builder().build(), Instant.now(), Mass.fromGrams(1001000.0))
                .build();
    }

    BodyWaterMassRecord getBodyWaterMassRecord_update(
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
        return new BodyWaterMassRecord.Builder(metadataWithId, Instant.now(), Mass.fromGrams(20.0))
                .setZoneOffset(ZoneOffset.systemDefault().getRules().getOffset(Instant.now()))
                .build();
    }

    static BodyWaterMassRecord getBaseBodyWaterMassRecord() {
        return new BodyWaterMassRecord.Builder(
                        new Metadata.Builder().build(), Instant.now(), Mass.fromGrams(100.0))
                .build();
    }

    static BodyWaterMassRecord getBodyWaterMassRecord(double mass) {
        return new BodyWaterMassRecord.Builder(
                        new Metadata.Builder().build(), Instant.now(), Mass.fromGrams(mass))
                .build();
    }

    private static BodyWaterMassRecord getCompleteBodyWaterMassRecord() {
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
        testMetadataBuilder.setClientRecordId("BWM" + Math.random());
        testMetadataBuilder.setRecordingMethod(Metadata.RECORDING_METHOD_ACTIVELY_RECORDED);

        return new BodyWaterMassRecord.Builder(
                        testMetadataBuilder.build(), Instant.now(), Mass.fromGrams(100.0))
                .build();
    }
}
