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
import android.health.connect.datatypes.MenstruationPeriodRecord;
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
public class MenstruationPeriodRecordTest {
    private static final String TAG = "MenstruationPeriodRecordTest";
    private static final Instant START_TIME = Instant.ofEpochMilli((long) 1e9);
    private static final Instant END_TIME = Instant.ofEpochMilli((long) 1e10);

    @Rule
    public AssumptionCheckerRule mSupportedHardwareRule =
            new AssumptionCheckerRule(
                    TestUtils::isHardwareSupported, "Tests should run on supported hardware only.");

    @After
    public void tearDown() throws InterruptedException {
        TestUtils.verifyDeleteRecords(
                MenstruationPeriodRecord.class,
                new TimeInstantRangeFilter.Builder()
                        .setStartTime(Instant.EPOCH)
                        .setEndTime(Instant.now())
                        .build());
        TestUtils.deleteAllStagedRemoteData();
    }

    @Test
    public void testInsertMenstruationPeriodRecord() throws InterruptedException {
        List<Record> records =
                List.of(getBaseMenstruationPeriodRecord(), getCompleteMenstruationPeriodRecord());
        TestUtils.insertRecords(records);
    }

    @Test
    public void testReadMenstruationPeriodRecord_usingIds() throws InterruptedException {
        List<Record> recordList =
                Arrays.asList(
                        getCompleteMenstruationPeriodRecord(),
                        getCompleteMenstruationPeriodRecord());
        readMenstruationPeriodRecordUsingIds(recordList);
    }

    @Test
    public void testReadMenstruationPeriodRecord_invalidIds() throws InterruptedException {
        ReadRecordsRequestUsingIds<MenstruationPeriodRecord> request =
                new ReadRecordsRequestUsingIds.Builder<>(MenstruationPeriodRecord.class)
                        .addId(UUID.randomUUID().toString())
                        .build();
        List<MenstruationPeriodRecord> result = TestUtils.readRecords(request);
        assertThat(result.size()).isEqualTo(0);
    }

    @Test
    public void testReadMenstruationPeriodRecord_usingClientRecordIds()
            throws InterruptedException {
        List<Record> recordList =
                Arrays.asList(
                        getCompleteMenstruationPeriodRecord(),
                        getCompleteMenstruationPeriodRecord());
        List<Record> insertedRecords = TestUtils.insertRecords(recordList);
        readMenstruationPeriodRecordUsingClientId(insertedRecords);
    }

    @Test
    public void testReadMenstruationPeriodRecord_invalidClientRecordIds()
            throws InterruptedException {
        ReadRecordsRequestUsingIds<MenstruationPeriodRecord> request =
                new ReadRecordsRequestUsingIds.Builder<>(MenstruationPeriodRecord.class)
                        .addClientRecordId("abc")
                        .build();
        List<MenstruationPeriodRecord> result = TestUtils.readRecords(request);
        assertThat(result.size()).isEqualTo(0);
    }

    @Test
    public void testReadMenstruationPeriodRecordUsingFilters_default() throws InterruptedException {
        List<MenstruationPeriodRecord> oldMenstruationPeriodRecords =
                TestUtils.readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(MenstruationPeriodRecord.class)
                                .build());
        MenstruationPeriodRecord testRecord = getCompleteMenstruationPeriodRecord();
        TestUtils.insertRecords(Collections.singletonList(testRecord));
        List<MenstruationPeriodRecord> newMenstruationPeriodRecords =
                TestUtils.readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(MenstruationPeriodRecord.class)
                                .build());
        assertThat(newMenstruationPeriodRecords.size())
                .isEqualTo(oldMenstruationPeriodRecords.size() + 1);
        assertThat(
                        newMenstruationPeriodRecords
                                .get(newMenstruationPeriodRecords.size() - 1)
                                .equals(testRecord))
                .isTrue();
    }

    @Test
    public void testReadMenstruationPeriodRecordUsingFilters_timeFilter()
            throws InterruptedException {
        TimeInstantRangeFilter filter =
                new TimeInstantRangeFilter.Builder()
                        .setStartTime(Instant.now())
                        .setEndTime(Instant.now().plusMillis(3000))
                        .build();
        MenstruationPeriodRecord testRecord = getCompleteMenstruationPeriodRecord();
        TestUtils.insertRecords(Collections.singletonList(testRecord));
        List<MenstruationPeriodRecord> newMenstruationPeriodRecords =
                TestUtils.readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(MenstruationPeriodRecord.class)
                                .setTimeRangeFilter(filter)
                                .build());
        assertThat(newMenstruationPeriodRecords.size()).isEqualTo(1);
        assertThat(
                        newMenstruationPeriodRecords
                                .get(newMenstruationPeriodRecords.size() - 1)
                                .equals(testRecord))
                .isTrue();
    }

    @Test
    public void testReadMenstruationPeriodRecordUsingFilters_dataFilter_correct()
            throws InterruptedException {
        Context context = ApplicationProvider.getApplicationContext();
        List<MenstruationPeriodRecord> oldMenstruationPeriodRecords =
                TestUtils.readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(MenstruationPeriodRecord.class)
                                .addDataOrigins(
                                        new DataOrigin.Builder()
                                                .setPackageName(context.getPackageName())
                                                .build())
                                .build());
        MenstruationPeriodRecord testRecord = getCompleteMenstruationPeriodRecord();
        TestUtils.insertRecords(Collections.singletonList(testRecord));
        List<MenstruationPeriodRecord> newMenstruationPeriodRecords =
                TestUtils.readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(MenstruationPeriodRecord.class)
                                .addDataOrigins(
                                        new DataOrigin.Builder()
                                                .setPackageName(context.getPackageName())
                                                .build())
                                .build());
        assertThat(newMenstruationPeriodRecords.size() - oldMenstruationPeriodRecords.size())
                .isEqualTo(1);
        MenstruationPeriodRecord newRecord =
                newMenstruationPeriodRecords.get(newMenstruationPeriodRecords.size() - 1);
        assertThat(newRecord.equals(testRecord)).isTrue();
    }

    @Test
    public void testReadMenstruationPeriodRecordUsingFilters_dataFilter_incorrect()
            throws InterruptedException {
        TestUtils.insertRecords(Collections.singletonList(getCompleteMenstruationPeriodRecord()));
        List<MenstruationPeriodRecord> newMenstruationPeriodRecords =
                TestUtils.readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(MenstruationPeriodRecord.class)
                                .addDataOrigins(
                                        new DataOrigin.Builder().setPackageName("abc").build())
                                .build());
        assertThat(newMenstruationPeriodRecords.size()).isEqualTo(0);
    }

    @Test
    public void testDeleteMenstruationPeriodRecord_no_filters() throws InterruptedException {
        String id = TestUtils.insertRecordAndGetId(getCompleteMenstruationPeriodRecord());
        TestUtils.verifyDeleteRecords(new DeleteUsingFiltersRequest.Builder().build());
        TestUtils.assertRecordNotFound(id, MenstruationPeriodRecord.class);
    }

    @Test
    public void testDeleteMenstruationPeriodRecord_time_filters() throws InterruptedException {
        TimeInstantRangeFilter timeRangeFilter =
                new TimeInstantRangeFilter.Builder()
                        .setStartTime(Instant.now())
                        .setEndTime(Instant.now().plusMillis(1000))
                        .build();
        String id = TestUtils.insertRecordAndGetId(getCompleteMenstruationPeriodRecord());
        TestUtils.verifyDeleteRecords(
                new DeleteUsingFiltersRequest.Builder()
                        .addRecordType(MenstruationPeriodRecord.class)
                        .setTimeRangeFilter(timeRangeFilter)
                        .build());
        TestUtils.assertRecordNotFound(id, MenstruationPeriodRecord.class);
    }

    @Test
    public void testDeleteMenstruationPeriodRecord_recordId_filters() throws InterruptedException {
        List<Record> records =
                List.of(getBaseMenstruationPeriodRecord(), getCompleteMenstruationPeriodRecord());
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
    public void testDeleteMenstruationPeriodRecord_dataOrigin_filters()
            throws InterruptedException {
        Context context = ApplicationProvider.getApplicationContext();
        String id = TestUtils.insertRecordAndGetId(getCompleteMenstruationPeriodRecord());
        TestUtils.verifyDeleteRecords(
                new DeleteUsingFiltersRequest.Builder()
                        .addDataOrigin(
                                new DataOrigin.Builder()
                                        .setPackageName(context.getPackageName())
                                        .build())
                        .build());
        TestUtils.assertRecordNotFound(id, MenstruationPeriodRecord.class);
    }

    @Test
    public void testDeleteMenstruationPeriodRecord_dataOrigin_filter_incorrect()
            throws InterruptedException {
        String id = TestUtils.insertRecordAndGetId(getCompleteMenstruationPeriodRecord());
        TestUtils.verifyDeleteRecords(
                new DeleteUsingFiltersRequest.Builder()
                        .addDataOrigin(new DataOrigin.Builder().setPackageName("abc").build())
                        .build());
        TestUtils.assertRecordFound(id, MenstruationPeriodRecord.class);
    }

    @Test
    public void testDeleteMenstruationPeriodRecord_usingIds() throws InterruptedException {
        List<Record> records =
                List.of(getBaseMenstruationPeriodRecord(), getCompleteMenstruationPeriodRecord());
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
    public void testDeleteMenstruationPeriodRecord_time_range() throws InterruptedException {
        TimeInstantRangeFilter timeRangeFilter =
                new TimeInstantRangeFilter.Builder()
                        .setStartTime(Instant.now())
                        .setEndTime(Instant.now().plusMillis(1000))
                        .build();
        String id = TestUtils.insertRecordAndGetId(getCompleteMenstruationPeriodRecord());
        TestUtils.verifyDeleteRecords(MenstruationPeriodRecord.class, timeRangeFilter);
        TestUtils.assertRecordNotFound(id, MenstruationPeriodRecord.class);
    }

    @Test
    public void testZoneOffsets() {
        final ZoneOffset defaultZoneOffset =
                ZoneOffset.systemDefault().getRules().getOffset(Instant.now());
        final ZoneOffset startZoneOffset = ZoneOffset.UTC;
        final ZoneOffset endZoneOffset = ZoneOffset.MAX;
        MenstruationPeriodRecord.Builder builder =
                new MenstruationPeriodRecord.Builder(generateMetadata(), START_TIME, END_TIME)
                        .setEndZoneOffset(ZoneOffset.MAX)
                        .setStartZoneOffset(ZoneOffset.MIN);

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
    public void testMenstruationPeriod_buildSession_buildCorrectObject() {
        MenstruationPeriodRecord record =
                new MenstruationPeriodRecord.Builder(generateMetadata(), START_TIME, END_TIME)
                        .build();
        assertThat(record.getStartTime()).isEqualTo(START_TIME);
        assertThat(record.getEndTime()).isEqualTo(END_TIME);
    }

    @Test
    public void testMenstruationPeriod_buildEqualSessions_equalsReturnsTrue() {
        Metadata metadata = generateMetadata();
        MenstruationPeriodRecord record =
                new MenstruationPeriodRecord.Builder(metadata, START_TIME, END_TIME).build();
        MenstruationPeriodRecord record2 =
                new MenstruationPeriodRecord.Builder(metadata, START_TIME, END_TIME).build();
        assertThat(record).isEqualTo(record2);
    }

    @Test
    public void testMenstruationPeriod_buildSessionWithAllFields_buildCorrectObject() {
        MenstruationPeriodRecord record =
                new MenstruationPeriodRecord.Builder(generateMetadata(), START_TIME, END_TIME)
                        .setEndZoneOffset(ZoneOffset.MAX)
                        .setStartZoneOffset(ZoneOffset.MIN)
                        .build();
        assertThat(record.getEndZoneOffset()).isEqualTo(ZoneOffset.MAX);
        assertThat(record.getStartZoneOffset()).isEqualTo(ZoneOffset.MIN);
    }

    @Test
    public void testUpdateRecords_validInput_dataBaseUpdatedSuccessfully()
            throws InterruptedException {

        List<Record> insertedRecords =
                TestUtils.insertRecords(
                        Arrays.asList(
                                getCompleteMenstruationPeriodRecord(),
                                getCompleteMenstruationPeriodRecord()));

        // read inserted records and verify that the data is same as inserted.
        readMenstruationPeriodRecordUsingIds(insertedRecords);

        // Generate a new set of records that will be used to perform the update operation.
        List<Record> updateRecords =
                Arrays.asList(
                        getCompleteMenstruationPeriodRecord(),
                        getCompleteMenstruationPeriodRecord());

        // Modify the uid of the updateRecords to the uuid that was present in the insert records.
        for (int itr = 0; itr < updateRecords.size(); itr++) {
            updateRecords.set(
                    itr,
                    getMenstruationPeriodRecord_update(
                            updateRecords.get(itr),
                            insertedRecords.get(itr).getMetadata().getId(),
                            insertedRecords.get(itr).getMetadata().getClientRecordId()));
        }

        TestUtils.updateRecords(updateRecords);

        // assert the inserted data has been modified by reading the data.
        readMenstruationPeriodRecordUsingIds(updateRecords);
    }

    @Test
    public void testUpdateRecords_invalidInputRecords_noChangeInDataBase()
            throws InterruptedException {
        List<Record> insertedRecords =
                TestUtils.insertRecords(
                        Arrays.asList(
                                getCompleteMenstruationPeriodRecord(),
                                getCompleteMenstruationPeriodRecord()));

        // read inserted records and verify that the data is same as inserted.
        readMenstruationPeriodRecordUsingIds(insertedRecords);

        // Generate a second set of records that will be used to perform the update operation.
        List<Record> updateRecords =
                Arrays.asList(
                        getCompleteMenstruationPeriodRecord(),
                        getCompleteMenstruationPeriodRecord());

        // Modify the Uid of the updateRecords to the UUID that was present in the insert records,
        // leaving out alternate records so that they have a new UUID which is not present in the
        // dataBase.
        for (int itr = 0; itr < updateRecords.size(); itr++) {
            updateRecords.set(
                    itr,
                    getMenstruationPeriodRecord_update(
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
        readMenstruationPeriodRecordUsingIds(insertedRecords);
    }

    @Test
    public void testUpdateRecords_recordWithInvalidPackageName_noChangeInDataBase()
            throws InterruptedException {
        List<Record> insertedRecords =
                TestUtils.insertRecords(
                        Arrays.asList(
                                getCompleteMenstruationPeriodRecord(),
                                getCompleteMenstruationPeriodRecord()));

        // read inserted records and verify that the data is same as inserted.
        readMenstruationPeriodRecordUsingIds(insertedRecords);

        // Generate a second set of records that will be used to perform the update operation.
        List<Record> updateRecords =
                Arrays.asList(
                        getCompleteMenstruationPeriodRecord(),
                        getCompleteMenstruationPeriodRecord());

        // Modify the Uuid of the updateRecords to the uuid that was present in the insert records.
        for (int itr = 0; itr < updateRecords.size(); itr++) {
            updateRecords.set(
                    itr,
                    getMenstruationPeriodRecord_update(
                            updateRecords.get(itr),
                            insertedRecords.get(itr).getMetadata().getId(),
                            insertedRecords.get(itr).getMetadata().getClientRecordId()));
            //             adding an entry with invalid packageName.
            updateRecords.set(itr, getCompleteMenstruationPeriodRecord());
        }

        try {
            TestUtils.updateRecords(updateRecords);
            Assert.fail("Expected to fail due to invalid package.");
        } catch (Exception exception) {
            // verify that the testcase failed due to invalid argument exception.
            assertThat(exception).isNotNull();
        }

        // assert the inserted data has not been modified by reading the data.
        readMenstruationPeriodRecordUsingIds(insertedRecords);
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
                                .addRecordType(MenstruationPeriodRecord.class)
                                .build());
        ChangeLogsRequest changeLogsRequest =
                new ChangeLogsRequest.Builder(tokenResponse.getToken()).build();
        ChangeLogsResponse response = TestUtils.getChangeLogs(changeLogsRequest);
        assertThat(response.getUpsertedRecords().size()).isEqualTo(0);
        assertThat(response.getDeletedLogs().size()).isEqualTo(0);

        List<Record> testRecord = Collections.singletonList(getCompleteMenstruationPeriodRecord());
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
                        .addRecordType(MenstruationPeriodRecord.class)
                        .build());
        response = TestUtils.getChangeLogs(changeLogsRequest);
        assertThat(response.getDeletedLogs()).isEmpty();
    }

    private void readMenstruationPeriodRecordUsingClientId(List<Record> insertedRecord)
            throws InterruptedException {
        ReadRecordsRequestUsingIds.Builder<MenstruationPeriodRecord> request =
                new ReadRecordsRequestUsingIds.Builder<>(MenstruationPeriodRecord.class);
        for (Record record : insertedRecord) {
            request.addClientRecordId(record.getMetadata().getClientRecordId());
        }
        List<MenstruationPeriodRecord> result = TestUtils.readRecords(request.build());
        assertThat(result.size()).isEqualTo(insertedRecord.size());
        assertThat(result).containsExactlyElementsIn(insertedRecord);
    }

    private void readMenstruationPeriodRecordUsingIds(List<Record> recordList)
            throws InterruptedException {
        List<Record> insertedRecords = TestUtils.insertRecords(recordList);
        ReadRecordsRequestUsingIds.Builder<MenstruationPeriodRecord> request =
                new ReadRecordsRequestUsingIds.Builder<>(MenstruationPeriodRecord.class);
        for (Record record : insertedRecords) {
            request.addId(record.getMetadata().getId());
        }
        ReadRecordsRequestUsingIds requestUsingIds = request.build();
        assertThat(requestUsingIds.getRecordType()).isEqualTo(MenstruationPeriodRecord.class);
        assertThat(requestUsingIds.getRecordIdFilters()).isNotNull();
        List<MenstruationPeriodRecord> result = TestUtils.readRecords(requestUsingIds);
        assertThat(result).hasSize(insertedRecords.size());
        assertThat(result).containsExactlyElementsIn(recordList);
    }

    MenstruationPeriodRecord getMenstruationPeriodRecord_update(
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
        return new MenstruationPeriodRecord.Builder(
                        metadataWithId, Instant.now(), Instant.now().plusMillis(2000))
                .setStartZoneOffset(ZoneOffset.systemDefault().getRules().getOffset(Instant.now()))
                .setEndZoneOffset(ZoneOffset.systemDefault().getRules().getOffset(Instant.now()))
                .build();
    }

    static MenstruationPeriodRecord getBaseMenstruationPeriodRecord() {
        return new MenstruationPeriodRecord.Builder(
                        new Metadata.Builder().build(),
                        Instant.now(),
                        Instant.now().plusMillis(1000))
                .build();
    }

    static MenstruationPeriodRecord getCompleteMenstruationPeriodRecord() {

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
        testMetadataBuilder.setClientRecordId("MPR" + Math.random());

        return new MenstruationPeriodRecord.Builder(
                        testMetadataBuilder.build(), Instant.now(), Instant.now().plusMillis(1000))
                .setStartZoneOffset(ZoneOffset.systemDefault().getRules().getOffset(Instant.now()))
                .setEndZoneOffset(ZoneOffset.systemDefault().getRules().getOffset(Instant.now()))
                .build();
    }
}
