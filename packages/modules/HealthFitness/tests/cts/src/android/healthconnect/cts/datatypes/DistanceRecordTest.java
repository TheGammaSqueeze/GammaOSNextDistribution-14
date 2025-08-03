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

import static android.health.connect.datatypes.DistanceRecord.DISTANCE_TOTAL;

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
import android.health.connect.datatypes.DistanceRecord;
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
import org.junit.BeforeClass;
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
public class DistanceRecordTest {
    private static final String TAG = "DistanceRecordTest";
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
                DistanceRecord.class,
                new TimeInstantRangeFilter.Builder()
                        .setStartTime(Instant.EPOCH)
                        .setEndTime(Instant.now())
                        .build());
        TestUtils.deleteAllStagedRemoteData();
    }

    @BeforeClass
    public static void setup() throws InterruptedException {
        if (!TestUtils.isHardwareSupported()) {
            return;
        }
        TestUtils.verifyDeleteRecords(
                DistanceRecord.class,
                new TimeInstantRangeFilter.Builder()
                        .setStartTime(Instant.EPOCH)
                        .setEndTime(Instant.now())
                        .build());
        TestUtils.deleteAllStagedRemoteData();
    }

    @Test
    public void testInsertDistanceRecord() throws InterruptedException {
        List<Record> records = List.of(getBaseDistanceRecord(), getCompleteDistanceRecord());
        TestUtils.insertRecords(records);
    }

    @Test
    public void testReadDistanceRecord_usingIds() throws InterruptedException {
        List<Record> recordList =
                Arrays.asList(getCompleteDistanceRecord(), getCompleteDistanceRecord());
        List<Record> insertedRecords = TestUtils.insertRecords(recordList);
        readDistanceRecordUsingIds(insertedRecords);
    }

    @Test
    public void testReadDistanceRecord_invalidIds() throws InterruptedException {
        ReadRecordsRequestUsingIds<DistanceRecord> request =
                new ReadRecordsRequestUsingIds.Builder<>(DistanceRecord.class)
                        .addId(UUID.randomUUID().toString())
                        .build();
        List<DistanceRecord> result = TestUtils.readRecords(request);
        assertThat(result.size()).isEqualTo(0);
    }

    @Test
    public void testReadDistanceRecord_usingClientRecordIds() throws InterruptedException {
        List<Record> recordList =
                Arrays.asList(getCompleteDistanceRecord(), getCompleteDistanceRecord());
        List<Record> insertedRecords = TestUtils.insertRecords(recordList);
        readDistanceRecordUsingClientId(insertedRecords);
    }

    @Test
    public void testReadDistanceRecord_invalidClientRecordIds() throws InterruptedException {
        ReadRecordsRequestUsingIds<DistanceRecord> request =
                new ReadRecordsRequestUsingIds.Builder<>(DistanceRecord.class)
                        .addClientRecordId("abc")
                        .build();
        List<DistanceRecord> result = TestUtils.readRecords(request);
        assertThat(result.size()).isEqualTo(0);
    }

    @Test
    public void testReadDistanceRecordUsingFilters_default() throws InterruptedException {
        List<DistanceRecord> oldDistanceRecords =
                TestUtils.readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(DistanceRecord.class).build());
        DistanceRecord testRecord = getCompleteDistanceRecord();
        TestUtils.insertRecords(Collections.singletonList(testRecord));
        List<DistanceRecord> newDistanceRecords =
                TestUtils.readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(DistanceRecord.class).build());
        assertThat(newDistanceRecords.size()).isEqualTo(oldDistanceRecords.size() + 1);
        assertThat(newDistanceRecords.get(newDistanceRecords.size() - 1).equals(testRecord))
                .isTrue();
    }

    @Test
    public void testReadDistanceRecordUsingFilters_timeFilter() throws InterruptedException {
        TimeInstantRangeFilter filter =
                new TimeInstantRangeFilter.Builder()
                        .setStartTime(Instant.now())
                        .setEndTime(Instant.now().plusMillis(3000))
                        .build();
        DistanceRecord testRecord = getCompleteDistanceRecord();
        TestUtils.insertRecords(Collections.singletonList(testRecord));
        List<DistanceRecord> newDistanceRecords =
                TestUtils.readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(DistanceRecord.class)
                                .setTimeRangeFilter(filter)
                                .build());
        assertThat(newDistanceRecords.size()).isEqualTo(1);
        assertThat(newDistanceRecords.get(newDistanceRecords.size() - 1).equals(testRecord))
                .isTrue();
    }

    @Test
    public void testReadDistanceRecordUsingFilters_dataFilter_correct()
            throws InterruptedException {
        Context context = ApplicationProvider.getApplicationContext();
        List<DistanceRecord> oldDistanceRecords =
                TestUtils.readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(DistanceRecord.class)
                                .addDataOrigins(
                                        new DataOrigin.Builder()
                                                .setPackageName(context.getPackageName())
                                                .build())
                                .build());
        DistanceRecord testRecord = getCompleteDistanceRecord();
        TestUtils.insertRecords(Collections.singletonList(testRecord));
        List<DistanceRecord> newDistanceRecords =
                TestUtils.readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(DistanceRecord.class)
                                .addDataOrigins(
                                        new DataOrigin.Builder()
                                                .setPackageName(context.getPackageName())
                                                .build())
                                .build());
        assertThat(newDistanceRecords.size() - oldDistanceRecords.size()).isEqualTo(1);
        DistanceRecord newRecord = newDistanceRecords.get(newDistanceRecords.size() - 1);
        assertThat(newRecord.equals(testRecord)).isTrue();
        assertThat(newRecord.getDistance()).isEqualTo(testRecord.getDistance());
    }

    @Test
    public void testReadDistanceRecordUsingFilters_dataFilter_incorrect()
            throws InterruptedException {
        TestUtils.insertRecords(Collections.singletonList(getCompleteDistanceRecord()));
        List<DistanceRecord> newDistanceRecords =
                TestUtils.readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(DistanceRecord.class)
                                .addDataOrigins(
                                        new DataOrigin.Builder().setPackageName("abc").build())
                                .build());
        assertThat(newDistanceRecords.size()).isEqualTo(0);
    }

    @Test
    public void testDeleteDistanceRecord_no_filters() throws InterruptedException {
        String id = TestUtils.insertRecordAndGetId(getCompleteDistanceRecord());
        TestUtils.verifyDeleteRecords(new DeleteUsingFiltersRequest.Builder().build());
        TestUtils.assertRecordNotFound(id, DistanceRecord.class);
    }

    @Test
    public void testDeleteDistanceRecord_time_filters() throws InterruptedException {
        TimeInstantRangeFilter timeRangeFilter =
                new TimeInstantRangeFilter.Builder()
                        .setStartTime(Instant.now())
                        .setEndTime(Instant.now().plusMillis(1000))
                        .build();
        String id = TestUtils.insertRecordAndGetId(getCompleteDistanceRecord());
        TestUtils.verifyDeleteRecords(
                new DeleteUsingFiltersRequest.Builder()
                        .addRecordType(DistanceRecord.class)
                        .setTimeRangeFilter(timeRangeFilter)
                        .build());
        TestUtils.assertRecordNotFound(id, DistanceRecord.class);
    }

    @Test
    public void testDeleteDistanceRecord_recordId_filters() throws InterruptedException {
        List<Record> records = List.of(getBaseDistanceRecord(), getCompleteDistanceRecord());
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
    public void testDeleteDistanceRecord_dataOrigin_filters() throws InterruptedException {
        Context context = ApplicationProvider.getApplicationContext();
        String id = TestUtils.insertRecordAndGetId(getCompleteDistanceRecord());
        TestUtils.verifyDeleteRecords(
                new DeleteUsingFiltersRequest.Builder()
                        .addDataOrigin(
                                new DataOrigin.Builder()
                                        .setPackageName(context.getPackageName())
                                        .build())
                        .build());
        TestUtils.assertRecordNotFound(id, DistanceRecord.class);
    }

    @Test
    public void testDeleteDistanceRecord_dataOrigin_filter_incorrect() throws InterruptedException {
        String id = TestUtils.insertRecordAndGetId(getCompleteDistanceRecord());
        TestUtils.verifyDeleteRecords(
                new DeleteUsingFiltersRequest.Builder()
                        .addDataOrigin(new DataOrigin.Builder().setPackageName("abc").build())
                        .build());
        TestUtils.assertRecordFound(id, DistanceRecord.class);
    }

    @Test
    public void testDeleteDistanceRecord_usingIds() throws InterruptedException {
        List<Record> records = List.of(getBaseDistanceRecord(), getCompleteDistanceRecord());
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
    public void testDeleteDistanceRecord_time_range() throws InterruptedException {
        TimeInstantRangeFilter timeRangeFilter =
                new TimeInstantRangeFilter.Builder()
                        .setStartTime(Instant.now())
                        .setEndTime(Instant.now().plusMillis(1000))
                        .build();
        String id = TestUtils.insertRecordAndGetId(getCompleteDistanceRecord());
        TestUtils.verifyDeleteRecords(DistanceRecord.class, timeRangeFilter);
        TestUtils.assertRecordNotFound(id, DistanceRecord.class);
    }

    private void readDistanceRecordUsingClientId(List<Record> insertedRecord)
            throws InterruptedException {
        ReadRecordsRequestUsingIds.Builder<DistanceRecord> request =
                new ReadRecordsRequestUsingIds.Builder<>(DistanceRecord.class);
        for (Record record : insertedRecord) {
            request.addClientRecordId(record.getMetadata().getClientRecordId());
        }
        List<DistanceRecord> result = TestUtils.readRecords(request.build());
        assertThat(result.size()).isEqualTo(insertedRecord.size());
        assertThat(result).containsExactlyElementsIn(insertedRecord);
    }

    private void readDistanceRecordUsingIds(List<Record> recordList) throws InterruptedException {
        ReadRecordsRequestUsingIds.Builder<DistanceRecord> request =
                new ReadRecordsRequestUsingIds.Builder<>(DistanceRecord.class);
        for (Record record : recordList) {
            request.addId(record.getMetadata().getId());
        }
        ReadRecordsRequestUsingIds requestUsingIds = request.build();
        assertThat(requestUsingIds.getRecordType()).isEqualTo(DistanceRecord.class);
        assertThat(requestUsingIds.getRecordIdFilters()).isNotNull();
        List<DistanceRecord> result = TestUtils.readRecords(requestUsingIds);
        assertThat(result).hasSize(recordList.size());
        assertThat(result).containsExactlyElementsIn(recordList);
    }

    private DistanceRecord getBaseDistanceRecordWithSameStartAndEndTime(
            double distance, Instant instant) {
        return new DistanceRecord.Builder(
                        new Metadata.Builder().build(),
                        instant,
                        instant,
                        Length.fromMeters(distance))
                .build();
    }

    @Test
    public void testZoneOffsets() {
        final ZoneOffset defaultZoneOffset =
                ZoneOffset.systemDefault().getRules().getOffset(Instant.now());
        final ZoneOffset startZoneOffset = ZoneOffset.UTC;
        final ZoneOffset endZoneOffset = ZoneOffset.MAX;
        DistanceRecord.Builder builder =
                new DistanceRecord.Builder(
                        new Metadata.Builder().build(),
                        Instant.now(),
                        Instant.now().plusMillis(1000),
                        Length.fromMeters(10.0));

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
                        Arrays.asList(getCompleteDistanceRecord(), getCompleteDistanceRecord()));

        // read inserted records and verify that the data is same as inserted.
        readDistanceRecordUsingIds(insertedRecords);

        // Generate a new set of records that will be used to perform the update operation.
        List<Record> updateRecords =
                Arrays.asList(getCompleteDistanceRecord(), getCompleteDistanceRecord());

        // Modify the uid of the updateRecords to the uuid that was present in the insert records.
        for (int itr = 0; itr < updateRecords.size(); itr++) {
            updateRecords.set(
                    itr,
                    getDistanceRecord_update(
                            updateRecords.get(itr),
                            insertedRecords.get(itr).getMetadata().getId(),
                            insertedRecords.get(itr).getMetadata().getClientRecordId()));
        }

        TestUtils.updateRecords(updateRecords);

        // assert the inserted data has been modified by reading the data.
        readDistanceRecordUsingIds(updateRecords);
    }

    @Test
    public void testUpdateRecords_invalidInputRecords_noChangeInDataBase()
            throws InterruptedException {
        List<Record> insertedRecords =
                TestUtils.insertRecords(
                        Arrays.asList(getCompleteDistanceRecord(), getCompleteDistanceRecord()));

        // read inserted records and verify that the data is same as inserted.
        readDistanceRecordUsingIds(insertedRecords);

        // Generate a second set of records that will be used to perform the update operation.
        List<Record> updateRecords =
                Arrays.asList(getCompleteDistanceRecord(), getCompleteDistanceRecord());

        // Modify the Uid of the updateRecords to the UUID that was present in the insert records,
        // leaving out alternate records so that they have a new UUID which is not present in the
        // dataBase.
        for (int itr = 0; itr < updateRecords.size(); itr++) {
            updateRecords.set(
                    itr,
                    getDistanceRecord_update(
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
        readDistanceRecordUsingIds(insertedRecords);
    }

    @Test
    public void testUpdateRecords_recordWithInvalidPackageName_noChangeInDataBase()
            throws InterruptedException {
        List<Record> insertedRecords =
                TestUtils.insertRecords(
                        Arrays.asList(getCompleteDistanceRecord(), getCompleteDistanceRecord()));

        // read inserted records and verify that the data is same as inserted.
        readDistanceRecordUsingIds(insertedRecords);

        // Generate a second set of records that will be used to perform the update operation.
        List<Record> updateRecords =
                Arrays.asList(getCompleteDistanceRecord(), getCompleteDistanceRecord());

        // Modify the Uuid of the updateRecords to the uuid that was present in the insert records.
        for (int itr = 0; itr < updateRecords.size(); itr++) {
            updateRecords.set(
                    itr,
                    getDistanceRecord_update(
                            updateRecords.get(itr),
                            insertedRecords.get(itr).getMetadata().getId(),
                            insertedRecords.get(itr).getMetadata().getClientRecordId()));
            //             adding an entry with invalid packageName.
            updateRecords.set(itr, getCompleteDistanceRecord());
        }

        try {
            TestUtils.updateRecords(updateRecords);
            Assert.fail("Expected to fail due to invalid package.");
        } catch (Exception exception) {
            // verify that the testcase failed due to invalid argument exception.
            assertThat(exception).isNotNull();
        }

        // assert the inserted data has not been modified by reading the data.
        readDistanceRecordUsingIds(insertedRecords);
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
                                .addRecordType(DistanceRecord.class)
                                .build());
        ChangeLogsRequest changeLogsRequest =
                new ChangeLogsRequest.Builder(tokenResponse.getToken()).build();
        ChangeLogsResponse response = TestUtils.getChangeLogs(changeLogsRequest);
        assertThat(response.getUpsertedRecords().size()).isEqualTo(0);
        assertThat(response.getDeletedLogs().size()).isEqualTo(0);

        List<Record> testRecord = Collections.singletonList(getCompleteDistanceRecord());
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
                        .addRecordType(DistanceRecord.class)
                        .build());
        response = TestUtils.getChangeLogs(changeLogsRequest);
        assertThat(response.getDeletedLogs()).isEmpty();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCreateDistanceRecord_invalidValue() {
        new DistanceRecord.Builder(
                        new Metadata.Builder().build(),
                        Instant.now(),
                        Instant.now().plusMillis(1000),
                        Length.fromMeters(1000001.0))
                .build();
    }

    DistanceRecord getDistanceRecord_update(Record record, String id, String clientRecordId) {
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
        return new DistanceRecord.Builder(
                        metadataWithId,
                        Instant.now(),
                        Instant.now().plusMillis(2000),
                        Length.fromMeters(20.0))
                .setStartZoneOffset(ZoneOffset.systemDefault().getRules().getOffset(Instant.now()))
                .setEndZoneOffset(ZoneOffset.systemDefault().getRules().getOffset(Instant.now()))
                .build();
    }

    @Test
    public void testAggregation_DistanceTotal_AtOverlapStartAndEndTime() throws Exception {
        TestUtils.setupAggregation(PACKAGE_NAME, HealthDataCategory.ACTIVITY);
        List<Record> records =
                Arrays.asList(
                        getBaseDistanceRecordWithSameStartAndEndTime(74.0, Instant.now()),
                        getBaseDistanceRecordWithSameStartAndEndTime(
                                100.5, Instant.now().minus(1, ChronoUnit.SECONDS)));
        AggregateRecordsResponse<Length> response =
                TestUtils.getAggregateResponse(
                        new AggregateRecordsRequest.Builder<Length>(
                                        new TimeInstantRangeFilter.Builder()
                                                .setStartTime(Instant.ofEpochMilli(0))
                                                .setEndTime(Instant.now().plus(1, ChronoUnit.DAYS))
                                                .build())
                                .addAggregationType(DISTANCE_TOTAL)
                                .build(),
                        records);
        assertThat(response.get(DISTANCE_TOTAL).getInMeters()).isEqualTo(174.5);
    }

    static DistanceRecord getBaseDistanceRecord() {
        return new DistanceRecord.Builder(
                        new Metadata.Builder().setDataOrigin(getDataOrigin()).build(),
                        Instant.now(),
                        Instant.now().plusMillis(1000),
                        Length.fromMeters(10.0))
                .build();
    }

    static DistanceRecord getBaseDistanceRecord(int days, double distance) {
        Instant startInstant = Instant.now().minus(days, ChronoUnit.DAYS);
        return new DistanceRecord.Builder(
                        new Metadata.Builder().setDataOrigin(getDataOrigin()).build(),
                        startInstant,
                        startInstant.plusMillis(1000),
                        Length.fromMeters(distance))
                .build();
    }

    static DistanceRecord getCompleteDistanceRecord() {

        Device device =
                new Device.Builder()
                        .setManufacturer("google")
                        .setModel("Pixel4a")
                        .setType(2)
                        .build();

        Metadata.Builder testMetadataBuilder = new Metadata.Builder();
        testMetadataBuilder.setDevice(device).setDataOrigin(getDataOrigin());
        testMetadataBuilder.setClientRecordId("DR" + Math.random());

        return new DistanceRecord.Builder(
                        testMetadataBuilder.build(),
                        Instant.now(),
                        Instant.now().plusMillis(1000),
                        Length.fromMeters(10.0))
                .setStartZoneOffset(ZoneOffset.systemDefault().getRules().getOffset(Instant.now()))
                .setEndZoneOffset(ZoneOffset.systemDefault().getRules().getOffset(Instant.now()))
                .build();
    }

    private static DataOrigin getDataOrigin() {
        return new DataOrigin.Builder().setPackageName(PACKAGE_NAME).build();
    }
}
