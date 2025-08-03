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

import static android.health.connect.datatypes.FloorsClimbedRecord.FLOORS_CLIMBED_TOTAL;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.health.connect.AggregateRecordsRequest;
import android.health.connect.AggregateRecordsResponse;
import android.health.connect.DeleteUsingFiltersRequest;
import android.health.connect.HealthConnectException;
import android.health.connect.HealthDataCategory;
import android.health.connect.ReadRecordsRequestUsingFilters;
import android.health.connect.ReadRecordsRequestUsingIds;
import android.health.connect.TimeInstantRangeFilter;
import android.health.connect.changelog.ChangeLogTokenRequest;
import android.health.connect.changelog.ChangeLogTokenResponse;
import android.health.connect.changelog.ChangeLogsRequest;
import android.health.connect.changelog.ChangeLogsResponse;
import android.health.connect.datatypes.DataOrigin;
import android.health.connect.datatypes.Device;
import android.health.connect.datatypes.FloorsClimbedRecord;
import android.health.connect.datatypes.Metadata;
import android.health.connect.datatypes.Record;
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
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@RunWith(AndroidJUnit4.class)
public class FloorsClimbedRecordTest {
    private static final String TAG = "FloorsClimbedRecordTest";
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
                FloorsClimbedRecord.class,
                new TimeInstantRangeFilter.Builder()
                        .setStartTime(Instant.EPOCH)
                        .setEndTime(Instant.now())
                        .build());
        TestUtils.deleteAllStagedRemoteData();
    }

    @Test
    public void testInsertFloorsClimbedRecord() throws InterruptedException {
        List<Record> records =
                List.of(getBaseFloorsClimbedRecord(1), getCompleteFloorsClimbedRecord());
        TestUtils.insertRecords(records);
    }

    @Test
    public void testReadFloorsClimbedRecord_usingIds() throws InterruptedException {
        List<Record> recordList =
                Arrays.asList(getCompleteFloorsClimbedRecord(), getCompleteFloorsClimbedRecord());
        List<Record> insertedRecords = TestUtils.insertRecords(recordList);
        readFloorsClimbedRecordUsingIds(insertedRecords);
    }

    @Test
    public void testCreateFloorsClimbedRecord_sameStartEndTime() {
        Instant startTime = Instant.now();
        new FloorsClimbedRecord.Builder(new Metadata.Builder().build(), startTime, startTime, 10)
                .build();
    }

    @Test
    public void testReadFloorsClimbedRecord_invalidIds() throws InterruptedException {
        ReadRecordsRequestUsingIds<FloorsClimbedRecord> request =
                new ReadRecordsRequestUsingIds.Builder<>(FloorsClimbedRecord.class)
                        .addId(UUID.randomUUID().toString())
                        .build();
        List<FloorsClimbedRecord> result = TestUtils.readRecords(request);
        assertThat(result.size()).isEqualTo(0);
    }

    @Test
    public void testReadFloorsClimbedRecord_usingClientRecordIds() throws InterruptedException {
        List<Record> recordList =
                Arrays.asList(getCompleteFloorsClimbedRecord(), getCompleteFloorsClimbedRecord());
        List<Record> insertedRecords = TestUtils.insertRecords(recordList);
        readFloorsClimbedRecordUsingClientId(insertedRecords);
    }

    @Test
    public void testReadFloorsClimbedRecord_invalidClientRecordIds() throws InterruptedException {
        ReadRecordsRequestUsingIds<FloorsClimbedRecord> request =
                new ReadRecordsRequestUsingIds.Builder<>(FloorsClimbedRecord.class)
                        .addClientRecordId("abc")
                        .build();
        List<FloorsClimbedRecord> result = TestUtils.readRecords(request);
        assertThat(result.size()).isEqualTo(0);
    }

    @Test
    public void testReadFloorsClimbedRecordUsingFilters_default() throws InterruptedException {
        List<FloorsClimbedRecord> oldFloorsClimbedRecords =
                TestUtils.readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(FloorsClimbedRecord.class)
                                .build());
        FloorsClimbedRecord testRecord = getCompleteFloorsClimbedRecord();
        TestUtils.insertRecords(Collections.singletonList(testRecord));
        List<FloorsClimbedRecord> newFloorsClimbedRecords =
                TestUtils.readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(FloorsClimbedRecord.class)
                                .build());
        assertThat(newFloorsClimbedRecords.size()).isEqualTo(oldFloorsClimbedRecords.size() + 1);
        assertThat(
                        newFloorsClimbedRecords
                                .get(newFloorsClimbedRecords.size() - 1)
                                .equals(testRecord))
                .isTrue();
    }

    @Test
    public void testReadFloorsClimbedRecordUsingFilters_timeFilter() throws InterruptedException {
        TimeInstantRangeFilter filter =
                new TimeInstantRangeFilter.Builder()
                        .setStartTime(Instant.now())
                        .setEndTime(Instant.now().plusMillis(3000))
                        .build();
        FloorsClimbedRecord testRecord = getCompleteFloorsClimbedRecord();
        TestUtils.insertRecords(Collections.singletonList(testRecord));
        List<FloorsClimbedRecord> newFloorsClimbedRecords =
                TestUtils.readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(FloorsClimbedRecord.class)
                                .setTimeRangeFilter(filter)
                                .build());
        assertThat(newFloorsClimbedRecords.size()).isEqualTo(1);
        assertThat(
                        newFloorsClimbedRecords
                                .get(newFloorsClimbedRecords.size() - 1)
                                .equals(testRecord))
                .isTrue();
    }

    @Test
    public void testReadFloorsClimbedRecordUsingFilters_dataFilter_correct()
            throws InterruptedException {
        Context context = ApplicationProvider.getApplicationContext();
        List<FloorsClimbedRecord> oldFloorsClimbedRecords =
                TestUtils.readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(FloorsClimbedRecord.class)
                                .addDataOrigins(
                                        new DataOrigin.Builder()
                                                .setPackageName(context.getPackageName())
                                                .build())
                                .build());
        FloorsClimbedRecord testRecord = getCompleteFloorsClimbedRecord();
        TestUtils.insertRecords(Collections.singletonList(testRecord));
        List<FloorsClimbedRecord> newFloorsClimbedRecords =
                TestUtils.readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(FloorsClimbedRecord.class)
                                .addDataOrigins(
                                        new DataOrigin.Builder()
                                                .setPackageName(context.getPackageName())
                                                .build())
                                .build());
        assertThat(newFloorsClimbedRecords.size() - oldFloorsClimbedRecords.size()).isEqualTo(1);
        FloorsClimbedRecord newRecord =
                newFloorsClimbedRecords.get(newFloorsClimbedRecords.size() - 1);
        assertThat(newRecord.equals(testRecord)).isTrue();
        assertThat(newRecord.getFloors()).isEqualTo(testRecord.getFloors());
    }

    @Test
    public void testReadFloorsClimbedRecordUsingFilters_dataFilter_incorrect()
            throws InterruptedException {
        TestUtils.insertRecords(Collections.singletonList(getCompleteFloorsClimbedRecord()));
        List<FloorsClimbedRecord> newFloorsClimbedRecords =
                TestUtils.readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(FloorsClimbedRecord.class)
                                .addDataOrigins(
                                        new DataOrigin.Builder().setPackageName("abc").build())
                                .build());
        assertThat(newFloorsClimbedRecords.size()).isEqualTo(0);
    }

    @Test
    public void testTotalAggregation_oneRecord_returnsItsTotal() throws Exception {
        TestUtils.setupAggregation(PACKAGE_NAME, HealthDataCategory.ACTIVITY);
        List<Record> records = Arrays.asList(getBaseFloorsClimbedRecord(1));
        AggregateRecordsResponse<Double> response =
                TestUtils.getAggregateResponse(
                        new AggregateRecordsRequest.Builder<Double>(
                                        new TimeInstantRangeFilter.Builder()
                                                .setStartTime(Instant.ofEpochMilli(0))
                                                .setEndTime(Instant.now().plus(1, ChronoUnit.DAYS))
                                                .build())
                                .addAggregationType(FLOORS_CLIMBED_TOTAL)
                                .build(),
                        records);
        assertThat(response.get(FLOORS_CLIMBED_TOTAL)).isNotNull();
        assertThat(response.get(FLOORS_CLIMBED_TOTAL)).isEqualTo(10);
    }

    @Test
    public void testAggregation_FloorsClimbedTotal() throws Exception {
        TestUtils.setupAggregation(PACKAGE_NAME, HealthDataCategory.ACTIVITY);
        List<Record> records =
                Arrays.asList(getBaseFloorsClimbedRecord(1), getBaseFloorsClimbedRecord(2));
        AggregateRecordsResponse<Double> oldResponse =
                TestUtils.getAggregateResponse(
                        new AggregateRecordsRequest.Builder<Double>(
                                        new TimeInstantRangeFilter.Builder()
                                                .setStartTime(Instant.ofEpochMilli(0))
                                                .setEndTime(Instant.now().plus(1, ChronoUnit.DAYS))
                                                .build())
                                .addAggregationType(FLOORS_CLIMBED_TOTAL)
                                .build(),
                        records);
        List<Record> recordNew =
                Arrays.asList(getBaseFloorsClimbedRecord(3), getBaseFloorsClimbedRecord(4));
        AggregateRecordsResponse<Double> newResponse =
                TestUtils.getAggregateResponse(
                        new AggregateRecordsRequest.Builder<Double>(
                                        new TimeInstantRangeFilter.Builder()
                                                .setStartTime(Instant.ofEpochMilli(0))
                                                .setEndTime(Instant.now().plus(1, ChronoUnit.DAYS))
                                                .build())
                                .addAggregationType(FLOORS_CLIMBED_TOTAL)
                                .build(),
                        recordNew);
        double newFloorsTotal = newResponse.get(FLOORS_CLIMBED_TOTAL);
        double oldFloorsTotal = oldResponse.get(FLOORS_CLIMBED_TOTAL);
        assertThat(newFloorsTotal).isNotNull();
        assertThat(oldFloorsTotal).isNotNull();
        assertThat(newFloorsTotal - oldFloorsTotal).isEqualTo(20);
        Set<DataOrigin> newDataOrigin = newResponse.getDataOrigins(FLOORS_CLIMBED_TOTAL);
        for (DataOrigin itr : newDataOrigin) {
            assertThat(itr.getPackageName()).isEqualTo("android.healthconnect.cts");
        }
        Set<DataOrigin> oldDataOrigin = oldResponse.getDataOrigins(FLOORS_CLIMBED_TOTAL);
        for (DataOrigin itr : oldDataOrigin) {
            assertThat(itr.getPackageName()).isEqualTo("android.healthconnect.cts");
        }
    }

    @Test
    public void testZoneOffsets() {
        final ZoneOffset defaultZoneOffset =
                ZoneOffset.systemDefault().getRules().getOffset(Instant.now());
        final ZoneOffset startZoneOffset = ZoneOffset.UTC;
        final ZoneOffset endZoneOffset = ZoneOffset.MAX;
        FloorsClimbedRecord.Builder builder =
                new FloorsClimbedRecord.Builder(
                        new Metadata.Builder().build(),
                        Instant.now(),
                        Instant.now().plusMillis(1000),
                        10);

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
                                getCompleteFloorsClimbedRecord(),
                                getCompleteFloorsClimbedRecord()));

        // read inserted records and verify that the data is same as inserted.
        readFloorsClimbedRecordUsingIds(insertedRecords);

        // Generate a new set of records that will be used to perform the update operation.
        List<Record> updateRecords =
                Arrays.asList(getCompleteFloorsClimbedRecord(), getCompleteFloorsClimbedRecord());

        // Modify the uid of the updateRecords to the uuid that was present in the insert records.
        for (int itr = 0; itr < updateRecords.size(); itr++) {
            updateRecords.set(
                    itr,
                    getFloorsClimbedRecord_update(
                            updateRecords.get(itr),
                            insertedRecords.get(itr).getMetadata().getId(),
                            insertedRecords.get(itr).getMetadata().getClientRecordId()));
        }

        TestUtils.updateRecords(updateRecords);

        // assert the inserted data has been modified by reading the data.
        readFloorsClimbedRecordUsingIds(updateRecords);
    }

    @Test
    public void testUpdateRecords_invalidInputRecords_noChangeInDataBase()
            throws InterruptedException {
        List<Record> insertedRecords =
                TestUtils.insertRecords(
                        Arrays.asList(
                                getCompleteFloorsClimbedRecord(),
                                getCompleteFloorsClimbedRecord()));

        // read inserted records and verify that the data is same as inserted.
        readFloorsClimbedRecordUsingIds(insertedRecords);

        // Generate a second set of records that will be used to perform the update operation.
        List<Record> updateRecords =
                Arrays.asList(getCompleteFloorsClimbedRecord(), getCompleteFloorsClimbedRecord());

        // Modify the Uid of the updateRecords to the UUID that was present in the insert records,
        // leaving out alternate records so that they have a new UUID which is not present in the
        // dataBase.
        for (int itr = 0; itr < updateRecords.size(); itr++) {
            updateRecords.set(
                    itr,
                    getFloorsClimbedRecord_update(
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
        readFloorsClimbedRecordUsingIds(insertedRecords);
    }

    @Test
    public void testUpdateRecords_recordWithInvalidPackageName_noChangeInDataBase()
            throws InterruptedException {
        List<Record> insertedRecords =
                TestUtils.insertRecords(
                        Arrays.asList(
                                getCompleteFloorsClimbedRecord(),
                                getCompleteFloorsClimbedRecord()));

        // read inserted records and verify that the data is same as inserted.
        readFloorsClimbedRecordUsingIds(insertedRecords);

        // Generate a second set of records that will be used to perform the update operation.
        List<Record> updateRecords =
                Arrays.asList(getCompleteFloorsClimbedRecord(), getCompleteFloorsClimbedRecord());

        // Modify the Uuid of the updateRecords to the uuid that was present in the insert records.
        for (int itr = 0; itr < updateRecords.size(); itr++) {
            updateRecords.set(
                    itr,
                    getFloorsClimbedRecord_update(
                            updateRecords.get(itr),
                            insertedRecords.get(itr).getMetadata().getId(),
                            insertedRecords.get(itr).getMetadata().getClientRecordId()));
            //             adding an entry with invalid packageName.
            updateRecords.set(itr, getCompleteFloorsClimbedRecord());
        }

        try {
            TestUtils.updateRecords(updateRecords);
            Assert.fail("Expected to fail due to invalid package.");
        } catch (Exception exception) {
            // verify that the testcase failed due to invalid argument exception.
            assertThat(exception).isNotNull();
        }

        // assert the inserted data has not been modified by reading the data.
        readFloorsClimbedRecordUsingIds(insertedRecords);
    }

    private void readFloorsClimbedRecordUsingClientId(List<Record> insertedRecord)
            throws InterruptedException {
        ReadRecordsRequestUsingIds.Builder<FloorsClimbedRecord> request =
                new ReadRecordsRequestUsingIds.Builder<>(FloorsClimbedRecord.class);
        for (Record record : insertedRecord) {
            request.addClientRecordId(record.getMetadata().getClientRecordId());
        }
        ReadRecordsRequestUsingIds requestUsingIds = request.build();
        assertThat(requestUsingIds.getRecordType()).isEqualTo(FloorsClimbedRecord.class);
        assertThat(requestUsingIds.getRecordIdFilters()).isNotNull();
        List<FloorsClimbedRecord> result = TestUtils.readRecords(requestUsingIds);
        assertThat(result.size()).isEqualTo(insertedRecord.size());
        assertThat(result).containsExactlyElementsIn(insertedRecord);
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
                                .addRecordType(FloorsClimbedRecord.class)
                                .build());
        ChangeLogsRequest changeLogsRequest =
                new ChangeLogsRequest.Builder(tokenResponse.getToken()).build();
        ChangeLogsResponse response = TestUtils.getChangeLogs(changeLogsRequest);
        assertThat(response.getUpsertedRecords().size()).isEqualTo(0);
        assertThat(response.getDeletedLogs().size()).isEqualTo(0);

        List<Record> testRecord = Collections.singletonList(getCompleteFloorsClimbedRecord());
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
                        .addRecordType(FloorsClimbedRecord.class)
                        .build());
        response = TestUtils.getChangeLogs(changeLogsRequest);
        assertThat(response.getDeletedLogs()).isEmpty();
    }

    private void readFloorsClimbedRecordUsingIds(List<Record> recordList)
            throws InterruptedException {
        ReadRecordsRequestUsingIds.Builder<FloorsClimbedRecord> request =
                new ReadRecordsRequestUsingIds.Builder<>(FloorsClimbedRecord.class);
        for (Record record : recordList) {
            request.addId(record.getMetadata().getId());
        }
        List<FloorsClimbedRecord> result = TestUtils.readRecords(request.build());
        assertThat(result).hasSize(recordList.size());
        assertThat(result).containsExactlyElementsIn(recordList);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCreateFloorsClimbedRecord_invalidValue() {
        new FloorsClimbedRecord.Builder(
                        new Metadata.Builder().build(),
                        Instant.now(),
                        Instant.now().plus(30, ChronoUnit.MINUTES),
                        1000001.0)
                .build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCreateFloorsClimbedRecord_invalidTime() {
        new FloorsClimbedRecord.Builder(
                        new Metadata.Builder().build(),
                        Instant.now().plusMillis(100),
                        Instant.now().plus(30, ChronoUnit.MINUTES),
                        1000001.0)
                .build();
    }

    FloorsClimbedRecord getFloorsClimbedRecord_update(
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
        return new FloorsClimbedRecord.Builder(
                        metadataWithId, Instant.now(), Instant.now().plusMillis(2000), 20)
                .setStartZoneOffset(ZoneOffset.systemDefault().getRules().getOffset(Instant.now()))
                .setEndZoneOffset(ZoneOffset.systemDefault().getRules().getOffset(Instant.now()))
                .build();
    }

    static FloorsClimbedRecord getBaseFloorsClimbedRecord(int days) {
        return new FloorsClimbedRecord.Builder(
                        new Metadata.Builder().build(),
                        Instant.now().minus(days, ChronoUnit.DAYS),
                        Instant.now().minus(days, ChronoUnit.DAYS).plus(30, ChronoUnit.MINUTES),
                        10)
                .build();
    }

    static FloorsClimbedRecord getCompleteFloorsClimbedRecord() {
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
        testMetadataBuilder.setClientRecordId("FCR" + Math.random());
        testMetadataBuilder.setRecordingMethod(Metadata.RECORDING_METHOD_ACTIVELY_RECORDED);
        return new FloorsClimbedRecord.Builder(
                        testMetadataBuilder.build(),
                        Instant.now(),
                        Instant.now().plusMillis(1000),
                        10)
                .setStartZoneOffset(ZoneOffset.systemDefault().getRules().getOffset(Instant.now()))
                .setEndZoneOffset(ZoneOffset.systemDefault().getRules().getOffset(Instant.now()))
                .build();
    }
}
