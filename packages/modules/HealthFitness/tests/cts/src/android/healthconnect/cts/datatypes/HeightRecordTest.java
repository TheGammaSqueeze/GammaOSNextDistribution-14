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

import static android.health.connect.datatypes.HeightRecord.HEIGHT_AVG;
import static android.health.connect.datatypes.HeightRecord.HEIGHT_MAX;
import static android.health.connect.datatypes.HeightRecord.HEIGHT_MIN;
import static android.healthconnect.cts.utils.TestUtils.distinctByUuid;
import static android.healthconnect.cts.utils.TestUtils.insertRecords;
import static android.healthconnect.cts.utils.TestUtils.readRecords;

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
import android.health.connect.datatypes.DataOrigin;
import android.health.connect.datatypes.Device;
import android.health.connect.datatypes.HeightRecord;
import android.health.connect.datatypes.Metadata;
import android.health.connect.datatypes.Record;
import android.health.connect.datatypes.units.Length;
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
public class HeightRecordTest {
    private static final String TAG = "HeightRecordTest";
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
                HeightRecord.class,
                new TimeInstantRangeFilter.Builder()
                        .setStartTime(Instant.EPOCH)
                        .setEndTime(Instant.now())
                        .build());
        TestUtils.deleteAllStagedRemoteData();
    }

    @Test
    public void testInsertHeightRecord() throws InterruptedException {
        List<Record> records = List.of(getBaseHeightRecord(), getCompleteHeightRecord());
        TestUtils.insertRecords(records);
    }

    @Test
    public void testReadHeightRecord_usingIds() throws InterruptedException {
        List<Record> recordList =
                Arrays.asList(getCompleteHeightRecord(), getCompleteHeightRecord());
        List<Record> insertedRecords = TestUtils.insertRecords(recordList);
        readHeightRecordUsingIds(insertedRecords);
    }

    @Test
    public void testReadHeightRecord_invalidIds() throws InterruptedException {
        ReadRecordsRequestUsingIds<HeightRecord> request =
                new ReadRecordsRequestUsingIds.Builder<>(HeightRecord.class)
                        .addId(UUID.randomUUID().toString())
                        .build();
        List<HeightRecord> result = TestUtils.readRecords(request);
        assertThat(result.size()).isEqualTo(0);
    }

    @Test
    public void testReadHeightRecord_usingClientRecordIds() throws InterruptedException {
        List<Record> recordList =
                Arrays.asList(getCompleteHeightRecord(), getCompleteHeightRecord());
        List<Record> insertedRecords = TestUtils.insertRecords(recordList);
        readHeightRecordUsingClientId(insertedRecords);
    }

    @Test
    public void testReadHeightRecord_invalidClientRecordIds() throws InterruptedException {
        ReadRecordsRequestUsingIds<HeightRecord> request =
                new ReadRecordsRequestUsingIds.Builder<>(HeightRecord.class)
                        .addClientRecordId("abc")
                        .build();
        List<HeightRecord> result = TestUtils.readRecords(request);
        assertThat(result.size()).isEqualTo(0);
    }

    @Test
    public void testReadHeightRecordUsingFilters_default() throws InterruptedException {
        List<HeightRecord> oldHeightRecords =
                TestUtils.readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(HeightRecord.class).build());
        HeightRecord testRecord = getCompleteHeightRecord();
        TestUtils.insertRecords(Collections.singletonList(testRecord));
        List<HeightRecord> newHeightRecords =
                TestUtils.readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(HeightRecord.class).build());
        assertThat(newHeightRecords.size()).isEqualTo(oldHeightRecords.size() + 1);
        assertThat(newHeightRecords.get(newHeightRecords.size() - 1).equals(testRecord)).isTrue();
    }

    @Test
    public void testReadHeightRecordUsingFilters_timeFilter() throws InterruptedException {
        TimeInstantRangeFilter filter =
                new TimeInstantRangeFilter.Builder()
                        .setStartTime(Instant.now())
                        .setEndTime(Instant.now().plusMillis(3000))
                        .build();
        HeightRecord testRecord = getCompleteHeightRecord();
        TestUtils.insertRecords(Collections.singletonList(testRecord));
        List<HeightRecord> newHeightRecords =
                TestUtils.readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(HeightRecord.class)
                                .setTimeRangeFilter(filter)
                                .build());
        assertThat(newHeightRecords.size()).isEqualTo(1);
        assertThat(newHeightRecords.get(newHeightRecords.size() - 1).equals(testRecord)).isTrue();
    }

    @Test
    public void testReadHeightRecordUsingFilters_dataFilter_correct() throws InterruptedException {
        Context context = ApplicationProvider.getApplicationContext();
        List<HeightRecord> oldHeightRecords =
                TestUtils.readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(HeightRecord.class)
                                .addDataOrigins(
                                        new DataOrigin.Builder()
                                                .setPackageName(context.getPackageName())
                                                .build())
                                .build());
        HeightRecord testRecord = getCompleteHeightRecord();
        TestUtils.insertRecords(Collections.singletonList(testRecord));
        List<HeightRecord> newHeightRecords =
                TestUtils.readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(HeightRecord.class)
                                .addDataOrigins(
                                        new DataOrigin.Builder()
                                                .setPackageName(context.getPackageName())
                                                .build())
                                .build());
        assertThat(newHeightRecords.size() - oldHeightRecords.size()).isEqualTo(1);
        HeightRecord newRecord = newHeightRecords.get(newHeightRecords.size() - 1);
        assertThat(newRecord.equals(testRecord)).isTrue();
        assertThat(newRecord.getHeight()).isEqualTo(testRecord.getHeight());
    }

    @Test
    public void testReadHeightRecordUsingFilters_dataFilter_incorrect()
            throws InterruptedException {
        TestUtils.insertRecords(Collections.singletonList(getCompleteHeightRecord()));
        List<HeightRecord> newHeightRecords =
                TestUtils.readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(HeightRecord.class)
                                .addDataOrigins(
                                        new DataOrigin.Builder().setPackageName("abc").build())
                                .build());
        assertThat(newHeightRecords.size()).isEqualTo(0);
    }

    private void readHeightRecordUsingClientId(List<Record> insertedRecord)
            throws InterruptedException {
        ReadRecordsRequestUsingIds.Builder<HeightRecord> request =
                new ReadRecordsRequestUsingIds.Builder<>(HeightRecord.class);
        for (Record record : insertedRecord) {
            request.addClientRecordId(record.getMetadata().getClientRecordId());
        }
        List<HeightRecord> result = TestUtils.readRecords(request.build());
        assertThat(result.size()).isEqualTo(insertedRecord.size());
        assertThat(result).containsExactlyElementsIn(insertedRecord);
    }

    private void readHeightRecordUsingIds(List<Record> recordList) throws InterruptedException {
        ReadRecordsRequestUsingIds.Builder<HeightRecord> request =
                new ReadRecordsRequestUsingIds.Builder<>(HeightRecord.class);
        for (Record record : recordList) {
            request.addId(record.getMetadata().getId());
        }
        ReadRecordsRequestUsingIds requestUsingIds = request.build();
        assertThat(requestUsingIds.getRecordType()).isEqualTo(HeightRecord.class);
        assertThat(requestUsingIds.getRecordIdFilters()).isNotNull();
        List<HeightRecord> result = TestUtils.readRecords(requestUsingIds);
        assertThat(result).hasSize(recordList.size());
        assertThat(result).containsExactlyElementsIn(recordList);
    }

    @Test
    public void testDeleteHeightRecord_no_filters() throws InterruptedException {
        String id = TestUtils.insertRecordAndGetId(getCompleteHeightRecord());
        TestUtils.verifyDeleteRecords(new DeleteUsingFiltersRequest.Builder().build());
        TestUtils.assertRecordNotFound(id, HeightRecord.class);
    }

    @Test
    public void testDeleteHeightRecord_time_filters() throws InterruptedException {
        TimeInstantRangeFilter timeRangeFilter =
                new TimeInstantRangeFilter.Builder()
                        .setStartTime(Instant.now())
                        .setEndTime(Instant.now().plusMillis(1000))
                        .build();
        String id = TestUtils.insertRecordAndGetId(getCompleteHeightRecord());
        TestUtils.verifyDeleteRecords(
                new DeleteUsingFiltersRequest.Builder()
                        .addRecordType(HeightRecord.class)
                        .setTimeRangeFilter(timeRangeFilter)
                        .build());
        TestUtils.assertRecordNotFound(id, HeightRecord.class);
    }

    @Test
    public void testDeleteHeightRecord_recordId_filters() throws InterruptedException {
        List<Record> records = List.of(getBaseHeightRecord(), getCompleteHeightRecord());
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
    public void testDeleteHeightRecord_dataOrigin_filters() throws InterruptedException {
        Context context = ApplicationProvider.getApplicationContext();
        String id = TestUtils.insertRecordAndGetId(getCompleteHeightRecord());
        TestUtils.verifyDeleteRecords(
                new DeleteUsingFiltersRequest.Builder()
                        .addDataOrigin(
                                new DataOrigin.Builder()
                                        .setPackageName(context.getPackageName())
                                        .build())
                        .build());
        TestUtils.assertRecordNotFound(id, HeightRecord.class);
    }

    @Test
    public void testDeleteHeightRecord_dataOrigin_filter_incorrect() throws InterruptedException {
        String id = TestUtils.insertRecordAndGetId(getCompleteHeightRecord());
        TestUtils.verifyDeleteRecords(
                new DeleteUsingFiltersRequest.Builder()
                        .addDataOrigin(new DataOrigin.Builder().setPackageName("abc").build())
                        .build());
        TestUtils.assertRecordFound(id, HeightRecord.class);
    }

    @Test
    public void testDeleteHeightRecord_usingIds() throws InterruptedException {
        List<Record> records = List.of(getBaseHeightRecord(), getCompleteHeightRecord());
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
    public void testDeleteHeightRecord_time_range() throws InterruptedException {
        TimeInstantRangeFilter timeRangeFilter =
                new TimeInstantRangeFilter.Builder()
                        .setStartTime(Instant.now())
                        .setEndTime(Instant.now().plusMillis(1000))
                        .build();
        String id = TestUtils.insertRecordAndGetId(getCompleteHeightRecord());
        TestUtils.verifyDeleteRecords(HeightRecord.class, timeRangeFilter);
        TestUtils.assertRecordNotFound(id, HeightRecord.class);
    }

    @Test
    public void testAggregation_Height() throws Exception {
        TestUtils.setupAggregation(PACKAGE_NAME, HealthDataCategory.BODY_MEASUREMENTS);
        Context context = ApplicationProvider.getApplicationContext();
        List<Record> records =
                Arrays.asList(
                        getBaseHeightRecord(0.5),
                        getBaseHeightRecord(1.0),
                        getBaseHeightRecord(1.5));
        AggregateRecordsResponse<Length> response =
                TestUtils.getAggregateResponse(
                        new AggregateRecordsRequest.Builder<Length>(
                                        new TimeInstantRangeFilter.Builder()
                                                .setStartTime(Instant.ofEpochMilli(0))
                                                .setEndTime(Instant.now().plus(1, ChronoUnit.DAYS))
                                                .build())
                                .addAggregationType(HEIGHT_MAX)
                                .addAggregationType(HEIGHT_MIN)
                                .addAggregationType(HEIGHT_AVG)
                                .addDataOriginsFilter(
                                        new DataOrigin.Builder()
                                                .setPackageName(context.getPackageName())
                                                .build())
                                .build(),
                        records);
        Length maxHeight = response.get(HEIGHT_MAX);
        Length minHeight = response.get(HEIGHT_MIN);
        Length avgHeight = response.get(HEIGHT_AVG);
        assertThat(maxHeight).isNotNull();
        assertThat(maxHeight.getInMeters()).isEqualTo(1.5);
        assertThat(minHeight).isNotNull();
        assertThat(minHeight.getInMeters()).isEqualTo(0.5);
        assertThat(avgHeight).isNotNull();
        assertThat(avgHeight.getInMeters()).isEqualTo(1.0);
        Set<DataOrigin> dataOrigins = response.getDataOrigins(HEIGHT_AVG);
        for (DataOrigin itr : dataOrigins) {
            assertThat(itr.getPackageName()).isEqualTo("android.healthconnect.cts");
        }
    }

    @Test
    public void testZoneOffsets() {
        final ZoneOffset defaultZoneOffset =
                ZoneOffset.systemDefault().getRules().getOffset(Instant.now());
        final ZoneOffset zoneOffset = ZoneOffset.UTC;
        HeightRecord.Builder builder =
                new HeightRecord.Builder(
                        new Metadata.Builder().build(), Instant.now(), Length.fromMeters(1.0));

        assertThat(builder.setZoneOffset(zoneOffset).build().getZoneOffset()).isEqualTo(zoneOffset);
        assertThat(builder.clearZoneOffset().build().getZoneOffset()).isEqualTo(defaultZoneOffset);
    }

    @Test
    public void testUpdateRecords_validInput_dataBaseUpdatedSuccessfully()
            throws InterruptedException {

        List<Record> insertedRecords =
                TestUtils.insertRecords(
                        Arrays.asList(getCompleteHeightRecord(), getCompleteHeightRecord()));

        // read inserted records and verify that the data is same as inserted.
        readHeightRecordUsingIds(insertedRecords);

        // Generate a new set of records that will be used to perform the update operation.
        List<Record> updateRecords =
                Arrays.asList(getCompleteHeightRecord(), getCompleteHeightRecord());

        // Modify the uid of the updateRecords to the uuid that was present in the insert records.
        for (int itr = 0; itr < updateRecords.size(); itr++) {
            updateRecords.set(
                    itr,
                    getHeightRecord_update(
                            updateRecords.get(itr),
                            insertedRecords.get(itr).getMetadata().getId(),
                            insertedRecords.get(itr).getMetadata().getClientRecordId()));
        }

        TestUtils.updateRecords(updateRecords);

        // assert the inserted data has been modified by reading the data.
        readHeightRecordUsingIds(updateRecords);
    }

    @Test
    public void testUpdateRecords_invalidInputRecords_noChangeInDataBase()
            throws InterruptedException {
        List<Record> insertedRecords =
                TestUtils.insertRecords(
                        Arrays.asList(getCompleteHeightRecord(), getCompleteHeightRecord()));

        // read inserted records and verify that the data is same as inserted.
        readHeightRecordUsingIds(insertedRecords);

        // Generate a second set of records that will be used to perform the update operation.
        List<Record> updateRecords =
                Arrays.asList(getCompleteHeightRecord(), getCompleteHeightRecord());

        // Modify the Uid of the updateRecords to the UUID that was present in the insert records,
        // leaving out alternate records so that they have a new UUID which is not present in the
        // dataBase.
        for (int itr = 0; itr < updateRecords.size(); itr++) {
            updateRecords.set(
                    itr,
                    getHeightRecord_update(
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
        readHeightRecordUsingIds(insertedRecords);
    }

    @Test
    public void testUpdateRecords_recordWithInvalidPackageName_noChangeInDataBase()
            throws InterruptedException {
        List<Record> insertedRecords =
                TestUtils.insertRecords(
                        Arrays.asList(getCompleteHeightRecord(), getCompleteHeightRecord()));

        // read inserted records and verify that the data is same as inserted.
        readHeightRecordUsingIds(insertedRecords);

        // Generate a second set of records that will be used to perform the update operation.
        List<Record> updateRecords =
                Arrays.asList(getCompleteHeightRecord(), getCompleteHeightRecord());

        // Modify the Uuid of the updateRecords to the uuid that was present in the insert records.
        for (int itr = 0; itr < updateRecords.size(); itr++) {
            updateRecords.set(
                    itr,
                    getHeightRecord_update(
                            updateRecords.get(itr),
                            insertedRecords.get(itr).getMetadata().getId(),
                            insertedRecords.get(itr).getMetadata().getClientRecordId()));
            //             adding an entry with invalid packageName.
            updateRecords.set(itr, getCompleteHeightRecord());
        }

        try {
            TestUtils.updateRecords(updateRecords);
            Assert.fail("Expected to fail due to invalid package.");
        } catch (Exception exception) {
            // verify that the testcase failed due to invalid argument exception.
            assertThat(exception).isNotNull();
        }

        // assert the inserted data has not been modified by reading the data.
        readHeightRecordUsingIds(insertedRecords);
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
                                .addRecordType(HeightRecord.class)
                                .build());
        ChangeLogsRequest changeLogsRequest =
                new ChangeLogsRequest.Builder(tokenResponse.getToken()).build();
        ChangeLogsResponse response = TestUtils.getChangeLogs(changeLogsRequest);
        assertThat(response.getUpsertedRecords().size()).isEqualTo(0);
        assertThat(response.getDeletedLogs().size()).isEqualTo(0);

        List<Record> testRecord = Collections.singletonList(getCompleteHeightRecord());
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
                new DeleteUsingFiltersRequest.Builder().addRecordType(HeightRecord.class).build());
        response = TestUtils.getChangeLogs(changeLogsRequest);
        assertThat(response.getDeletedLogs()).isEmpty();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCreateHeightRecord_invalidValue() {
        new HeightRecord.Builder(
                        new Metadata.Builder().build(), Instant.now(), Length.fromMeters(3.1))
                .build();
    }

    @Test
    public void insertRecords_withDuplicatedClientRecordId_readNoDuplicates() throws Exception {
        int distinctRecordCount = 10;
        List<HeightRecord> records = new ArrayList<>();
        Instant now = Instant.now();
        for (int i = 0; i < distinctRecordCount; i++) {
            HeightRecord record =
                    getCompleteHeightRecord(
                            now.minusMillis(i), /* clientRecordId= */ "client_id_" + i);

            records.add(record);
            records.add(record); // Add each record twice
        }

        List<Record> distinctRecords = distinctByUuid(insertRecords(records));
        assertThat(distinctRecords.size()).isEqualTo(distinctRecordCount);

        readHeightRecordUsingIds(distinctRecords);
    }

    @Test
    public void insertRecords_sameClientRecordIdAndNewData_readNewData() throws Exception {
        int recordCount = 10;
        insertAndReadRecords(recordCount, Length.fromMeters(1.0));

        Length newHeight = Length.fromMeters(2.0);
        List<HeightRecord> newRecords = insertAndReadRecords(recordCount, newHeight);

        for (HeightRecord record : newRecords) {
            assertThat(record.getHeight()).isEqualTo(newHeight);
        }
    }

    @Test
    public void insertRecords_sameClientRecordIdAndNewerVersion_readNewData() throws Exception {
        int recordCount = 10;
        long oldVersion = 0L;
        Length oldHeight = Length.fromMeters(1.0);
        insertAndReadRecords(recordCount, oldVersion, oldHeight);

        long newVersion = 1L;
        Length newHeight = Length.fromMeters(2.0);
        List<HeightRecord> newRecords = insertAndReadRecords(recordCount, newVersion, newHeight);

        for (HeightRecord record : newRecords) {
            assertThat(record.getHeight()).isEqualTo(newHeight);
        }
    }

    @Test
    public void insertRecords_sameClientRecordIdAndSameVersion_readNewData() throws Exception {
        int recordCount = 10;
        long version = 1L;
        Length oldHeight = Length.fromMeters(1.0);
        insertAndReadRecords(recordCount, version, oldHeight);

        Length newHeight = Length.fromMeters(2.0);
        List<HeightRecord> newRecords = insertAndReadRecords(recordCount, version, newHeight);

        for (HeightRecord record : newRecords) {
            assertThat(record.getHeight()).isEqualTo(newHeight);
        }
    }

    @Test
    public void insertRecords_sameClientRecordIdAndOlderVersion_readOldData() throws Exception {
        int recordCount = 10;
        long oldVersion = 1L;
        Length oldHeight = Length.fromMeters(1.0);
        insertAndReadRecords(recordCount, oldVersion, oldHeight);

        long newVersion = 0L;
        Length newHeight = Length.fromMeters(2.0);
        List<HeightRecord> newRecords = insertAndReadRecords(recordCount, newVersion, newHeight);

        for (HeightRecord record : newRecords) {
            assertThat(record.getHeight()).isEqualTo(oldHeight);
        }
    }

    private static List<HeightRecord> insertAndReadRecords(int count, Length height)
            throws Exception {
        return insertAndReadRecords(count, /* version= */ 0L, height);
    }

    private static List<HeightRecord> insertAndReadRecords(
            int recordCount, long version, Length height) throws Exception {
        List<HeightRecord> records = new ArrayList<>();
        Instant now = Instant.now();
        for (int i = 0; i < recordCount; i++) {
            Instant time = now.minusMillis(i);
            String clientRecordId = "client_id_" + i;
            records.add(getCompleteHeightRecord(time, clientRecordId, version, height));
        }
        List<Record> insertedRecords = insertRecords(records);
        assertThat(insertedRecords).hasSize(recordCount);

        List<HeightRecord> readRecords =
                readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(HeightRecord.class).build());
        assertThat(readRecords).hasSize(recordCount);

        return readRecords;
    }

    HeightRecord getHeightRecord_update(Record record, String id, String clientRecordId) {
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
        return new HeightRecord.Builder(metadataWithId, Instant.now(), Length.fromMeters(2.0))
                .setZoneOffset(ZoneOffset.systemDefault().getRules().getOffset(Instant.now()))
                .build();
    }

    private static HeightRecord getBaseHeightRecord() {
        return new HeightRecord.Builder(
                        new Metadata.Builder().build(), Instant.now(), Length.fromMeters(1.0))
                .build();
    }

    static HeightRecord getBaseHeightRecord(double height) {
        return new HeightRecord.Builder(
                        new Metadata.Builder().setClientRecordId("HR" + Math.random()).build(),
                        Instant.now(),
                        Length.fromMeters(height))
                .build();
    }

    private static HeightRecord getCompleteHeightRecord() {
        return getCompleteHeightRecord(Instant.now(), /* clientRecordId= */ "HR" + Math.random());
    }

    private static HeightRecord getCompleteHeightRecord(Instant time, String clientRecordId) {
        return getCompleteHeightRecord(time, clientRecordId, Length.fromMeters(1.0));
    }

    private static HeightRecord getCompleteHeightRecord(
            Instant time, String clientRecordId, Length height) {
        return getCompleteHeightRecord(time, clientRecordId, /* clientRecordVersion= */ 0L, height);
    }

    private static HeightRecord getCompleteHeightRecord(
            Instant time, String clientRecordId, long clientRecordVersion, Length height) {
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
        testMetadataBuilder.setClientRecordId(clientRecordId);
        testMetadataBuilder.setClientRecordVersion(clientRecordVersion);
        testMetadataBuilder.setRecordingMethod(Metadata.RECORDING_METHOD_ACTIVELY_RECORDED);

        return new HeightRecord.Builder(testMetadataBuilder.build(), time, height)
                .setZoneOffset(ZoneOffset.systemDefault().getRules().getOffset(Instant.now()))
                .build();
    }
}
