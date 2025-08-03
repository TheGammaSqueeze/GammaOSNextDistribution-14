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

import static android.health.connect.datatypes.WheelchairPushesRecord.WHEEL_CHAIR_PUSHES_COUNT_TOTAL;

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
import android.health.connect.datatypes.Metadata;
import android.health.connect.datatypes.Record;
import android.health.connect.datatypes.WheelchairPushesRecord;
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
public class WheelchairPushesRecordTest {
    private static final String TAG = "WheelchairPushesRecordTest";
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
                WheelchairPushesRecord.class,
                new TimeInstantRangeFilter.Builder()
                        .setStartTime(Instant.EPOCH)
                        .setEndTime(Instant.now())
                        .build());
        TestUtils.deleteAllStagedRemoteData();
    }

    @Test
    public void testInsertWheelchairPushesRecord() throws InterruptedException {
        List<Record> records =
                List.of(getBaseWheelchairPushesRecord(), getCompleteWheelchairPushesRecord());
        TestUtils.insertRecords(records);
    }

    @Test
    public void testReadWheelchairPushesRecord_usingIds() throws InterruptedException {
        List<Record> recordList =
                Arrays.asList(
                        getCompleteWheelchairPushesRecord(), getCompleteWheelchairPushesRecord());
        readWheelchairPushesRecordUsingIds(recordList);
    }

    @Test
    public void testReadWheelchairPushesRecord_invalidIds() throws InterruptedException {
        ReadRecordsRequestUsingIds<WheelchairPushesRecord> request =
                new ReadRecordsRequestUsingIds.Builder<>(WheelchairPushesRecord.class)
                        .addId(UUID.randomUUID().toString())
                        .build();
        List<WheelchairPushesRecord> result = TestUtils.readRecords(request);
        assertThat(result.size()).isEqualTo(0);
    }

    @Test
    public void testReadWheelchairPushesRecord_usingClientRecordIds() throws InterruptedException {
        List<Record> recordList =
                Arrays.asList(
                        getCompleteWheelchairPushesRecord(), getCompleteWheelchairPushesRecord());
        List<Record> insertedRecords = TestUtils.insertRecords(recordList);
        readWheelchairPushesRecordUsingClientId(insertedRecords);
    }

    @Test
    public void testReadWheelchairPushesRecord_invalidClientRecordIds()
            throws InterruptedException {
        ReadRecordsRequestUsingIds<WheelchairPushesRecord> request =
                new ReadRecordsRequestUsingIds.Builder<>(WheelchairPushesRecord.class)
                        .addClientRecordId("abc")
                        .build();
        List<WheelchairPushesRecord> result = TestUtils.readRecords(request);
        assertThat(result.size()).isEqualTo(0);
    }

    @Test
    public void testReadWheelchairPushesRecordUsingFilters_default() throws InterruptedException {
        List<WheelchairPushesRecord> oldWheelchairPushesRecords =
                TestUtils.readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(WheelchairPushesRecord.class)
                                .build());
        WheelchairPushesRecord testRecord = getCompleteWheelchairPushesRecord();
        TestUtils.insertRecords(Collections.singletonList(testRecord));
        List<WheelchairPushesRecord> newWheelchairPushesRecords =
                TestUtils.readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(WheelchairPushesRecord.class)
                                .build());
        assertThat(newWheelchairPushesRecords.size())
                .isEqualTo(oldWheelchairPushesRecords.size() + 1);
        assertThat(
                        newWheelchairPushesRecords
                                .get(newWheelchairPushesRecords.size() - 1)
                                .equals(testRecord))
                .isTrue();
    }

    @Test
    public void testReadWheelchairPushesRecordUsingFilters_timeFilter()
            throws InterruptedException {
        TimeInstantRangeFilter filter =
                new TimeInstantRangeFilter.Builder()
                        .setStartTime(Instant.now())
                        .setEndTime(Instant.now().plusMillis(3000))
                        .build();
        WheelchairPushesRecord testRecord = getCompleteWheelchairPushesRecord();
        TestUtils.insertRecords(Collections.singletonList(testRecord));
        List<WheelchairPushesRecord> newWheelchairPushesRecords =
                TestUtils.readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(WheelchairPushesRecord.class)
                                .setTimeRangeFilter(filter)
                                .build());
        assertThat(newWheelchairPushesRecords.size()).isEqualTo(1);
        assertThat(
                        newWheelchairPushesRecords
                                .get(newWheelchairPushesRecords.size() - 1)
                                .equals(testRecord))
                .isTrue();
    }

    @Test
    public void testReadWheelchairPushesRecordUsingFilters_dataFilter_correct()
            throws InterruptedException {
        Context context = ApplicationProvider.getApplicationContext();
        List<WheelchairPushesRecord> oldWheelchairPushesRecords =
                TestUtils.readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(WheelchairPushesRecord.class)
                                .addDataOrigins(
                                        new DataOrigin.Builder()
                                                .setPackageName(context.getPackageName())
                                                .build())
                                .build());
        WheelchairPushesRecord testRecord = getCompleteWheelchairPushesRecord();
        TestUtils.insertRecords(Collections.singletonList(testRecord));
        List<WheelchairPushesRecord> newWheelchairPushesRecords =
                TestUtils.readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(WheelchairPushesRecord.class)
                                .addDataOrigins(
                                        new DataOrigin.Builder()
                                                .setPackageName(context.getPackageName())
                                                .build())
                                .build());
        assertThat(newWheelchairPushesRecords.size() - oldWheelchairPushesRecords.size())
                .isEqualTo(1);
        WheelchairPushesRecord newRecord =
                newWheelchairPushesRecords.get(newWheelchairPushesRecords.size() - 1);
        assertThat(
                        newWheelchairPushesRecords
                                .get(newWheelchairPushesRecords.size() - 1)
                                .equals(testRecord))
                .isTrue();
        assertThat(newRecord.getCount()).isEqualTo(testRecord.getCount());
    }

    @Test
    public void testReadWheelchairPushesRecordUsingFilters_dataFilter_incorrect()
            throws InterruptedException {
        TestUtils.insertRecords(Collections.singletonList(getCompleteWheelchairPushesRecord()));
        List<WheelchairPushesRecord> newWheelchairPushesRecords =
                TestUtils.readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(WheelchairPushesRecord.class)
                                .addDataOrigins(
                                        new DataOrigin.Builder().setPackageName("abc").build())
                                .build());
        assertThat(newWheelchairPushesRecords.size()).isEqualTo(0);
    }

    @Test
    public void testAggregation_countTotal() throws Exception {
        TestUtils.setupAggregation(PACKAGE_NAME, HealthDataCategory.ACTIVITY);
        List<Record> records =
                Arrays.asList(getBaseWheelchairPushesRecord(1), getBaseWheelchairPushesRecord(2));
        AggregateRecordsResponse<Long> oldResponse =
                TestUtils.getAggregateResponse(
                        new AggregateRecordsRequest.Builder<Long>(
                                        new TimeInstantRangeFilter.Builder()
                                                .setStartTime(Instant.ofEpochMilli(0))
                                                .setEndTime(Instant.now().plus(1, ChronoUnit.DAYS))
                                                .build())
                                .addAggregationType(WHEEL_CHAIR_PUSHES_COUNT_TOTAL)
                                .build(),
                        records);
        List<Record> recordNew =
                Arrays.asList(getBaseWheelchairPushesRecord(3), getBaseWheelchairPushesRecord(4));
        AggregateRecordsResponse<Long> newResponse =
                TestUtils.getAggregateResponse(
                        new AggregateRecordsRequest.Builder<Long>(
                                        new TimeInstantRangeFilter.Builder()
                                                .setStartTime(Instant.ofEpochMilli(0))
                                                .setEndTime(Instant.now().plus(1, ChronoUnit.DAYS))
                                                .build())
                                .addAggregationType(WHEEL_CHAIR_PUSHES_COUNT_TOTAL)
                                .build(),
                        recordNew);
        assertThat(newResponse.get(WHEEL_CHAIR_PUSHES_COUNT_TOTAL)).isNotNull();
        assertThat(newResponse.get(WHEEL_CHAIR_PUSHES_COUNT_TOTAL))
                .isEqualTo(oldResponse.get(WHEEL_CHAIR_PUSHES_COUNT_TOTAL) + 20);
        Set<DataOrigin> newDataOrigin = newResponse.getDataOrigins(WHEEL_CHAIR_PUSHES_COUNT_TOTAL);
        for (DataOrigin itr : newDataOrigin) {
            assertThat(itr.getPackageName()).isEqualTo("android.healthconnect.cts");
        }
        Set<DataOrigin> oldDataOrigin = oldResponse.getDataOrigins(WHEEL_CHAIR_PUSHES_COUNT_TOTAL);
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
        WheelchairPushesRecord.Builder builder =
                new WheelchairPushesRecord.Builder(
                        new Metadata.Builder().build(),
                        Instant.now(),
                        Instant.now().plusMillis(1000),
                        1);

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
                                getCompleteWheelchairPushesRecord(),
                                getCompleteWheelchairPushesRecord()));

        // read inserted records and verify that the data is same as inserted.
        readWheelchairPushesRecordUsingIds(insertedRecords);

        // Generate a new set of records that will be used to perform the update operation.
        List<Record> updateRecords =
                Arrays.asList(
                        getCompleteWheelchairPushesRecord(), getCompleteWheelchairPushesRecord());

        // Modify the uid of the updateRecords to the uuid that was present in the insert records.
        for (int itr = 0; itr < updateRecords.size(); itr++) {
            updateRecords.set(
                    itr,
                    getWheelchairPushesRecord_update(
                            updateRecords.get(itr),
                            insertedRecords.get(itr).getMetadata().getId(),
                            insertedRecords.get(itr).getMetadata().getClientRecordId()));
        }

        TestUtils.updateRecords(updateRecords);

        // assert the inserted data has been modified by reading the data.
        readWheelchairPushesRecordUsingIds(updateRecords);
    }

    @Test
    public void testUpdateRecords_invalidInputRecords_noChangeInDataBase()
            throws InterruptedException {
        List<Record> insertedRecords =
                TestUtils.insertRecords(
                        Arrays.asList(
                                getCompleteWheelchairPushesRecord(),
                                getCompleteWheelchairPushesRecord()));

        // read inserted records and verify that the data is same as inserted.
        readWheelchairPushesRecordUsingIds(insertedRecords);

        // Generate a second set of records that will be used to perform the update operation.
        List<Record> updateRecords =
                Arrays.asList(
                        getCompleteWheelchairPushesRecord(), getCompleteWheelchairPushesRecord());

        // Modify the Uid of the updateRecords to the UUID that was present in the insert records,
        // leaving out alternate records so that they have a new UUID which is not present in the
        // dataBase.
        for (int itr = 0; itr < updateRecords.size(); itr++) {
            updateRecords.set(
                    itr,
                    getWheelchairPushesRecord_update(
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
        readWheelchairPushesRecordUsingIds(insertedRecords);
    }

    @Test
    public void testUpdateRecords_recordWithInvalidPackageName_noChangeInDataBase()
            throws InterruptedException {
        List<Record> insertedRecords =
                TestUtils.insertRecords(
                        Arrays.asList(
                                getCompleteWheelchairPushesRecord(),
                                getCompleteWheelchairPushesRecord()));

        // read inserted records and verify that the data is same as inserted.
        readWheelchairPushesRecordUsingIds(insertedRecords);

        // Generate a second set of records that will be used to perform the update operation.
        List<Record> updateRecords =
                Arrays.asList(
                        getCompleteWheelchairPushesRecord(), getCompleteWheelchairPushesRecord());

        // Modify the Uuid of the updateRecords to the uuid that was present in the insert records.
        for (int itr = 0; itr < updateRecords.size(); itr++) {
            updateRecords.set(
                    itr,
                    getWheelchairPushesRecord_update(
                            updateRecords.get(itr),
                            insertedRecords.get(itr).getMetadata().getId(),
                            insertedRecords.get(itr).getMetadata().getClientRecordId()));
            //             adding an entry with invalid packageName.
            updateRecords.set(itr, getCompleteWheelchairPushesRecord());
        }

        try {
            TestUtils.updateRecords(updateRecords);
            Assert.fail("Expected to fail due to invalid package.");
        } catch (Exception exception) {
            // verify that the testcase failed due to invalid argument exception.
            assertThat(exception).isNotNull();
        }

        // assert the inserted data has not been modified by reading the data.
        readWheelchairPushesRecordUsingIds(insertedRecords);
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
                                .addRecordType(WheelchairPushesRecord.class)
                                .build());
        ChangeLogsRequest changeLogsRequest =
                new ChangeLogsRequest.Builder(tokenResponse.getToken()).build();
        ChangeLogsResponse response = TestUtils.getChangeLogs(changeLogsRequest);
        assertThat(response.getUpsertedRecords().size()).isEqualTo(0);
        assertThat(response.getDeletedLogs().size()).isEqualTo(0);

        List<Record> testRecord = Collections.singletonList(getCompleteWheelchairPushesRecord());
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
                        .addRecordType(WheelchairPushesRecord.class)
                        .build());
        response = TestUtils.getChangeLogs(changeLogsRequest);
        assertThat(response.getDeletedLogs()).isEmpty();
    }

    private void readWheelchairPushesRecordUsingClientId(List<Record> insertedRecord)
            throws InterruptedException {
        ReadRecordsRequestUsingIds.Builder<WheelchairPushesRecord> request =
                new ReadRecordsRequestUsingIds.Builder<>(WheelchairPushesRecord.class);
        for (Record record : insertedRecord) {
            request.addClientRecordId(record.getMetadata().getClientRecordId());
        }
        List<WheelchairPushesRecord> result = TestUtils.readRecords(request.build());
        assertThat(result.size()).isEqualTo(insertedRecord.size());
        assertThat(result).containsExactlyElementsIn(insertedRecord);
    }

    private void readWheelchairPushesRecordUsingIds(List<Record> recordList)
            throws InterruptedException {
        List<Record> insertedRecords = TestUtils.insertRecords(recordList);
        ReadRecordsRequestUsingIds.Builder<WheelchairPushesRecord> request =
                new ReadRecordsRequestUsingIds.Builder<>(WheelchairPushesRecord.class);
        for (Record record : insertedRecords) {
            request.addId(record.getMetadata().getId());
        }
        List<WheelchairPushesRecord> result = TestUtils.readRecords(request.build());
        assertThat(result).hasSize(insertedRecords.size());
        assertThat(result.size()).isEqualTo(insertedRecords.size());
        assertThat(result).containsExactlyElementsIn(insertedRecords);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCreateWheelchairPushesRecord_invalidValue() {
        new WheelchairPushesRecord.Builder(
                        new Metadata.Builder().build(),
                        Instant.now(),
                        Instant.now().plusMillis(1000),
                        1000001)
                .build();
    }

    WheelchairPushesRecord getWheelchairPushesRecord_update(
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
        return new WheelchairPushesRecord.Builder(
                        metadataWithId, Instant.now(), Instant.now().plusMillis(2000), 12)
                .setStartZoneOffset(ZoneOffset.systemDefault().getRules().getOffset(Instant.now()))
                .setEndZoneOffset(ZoneOffset.systemDefault().getRules().getOffset(Instant.now()))
                .build();
    }

    static WheelchairPushesRecord getCompleteWheelchairPushesRecord() {

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
        testMetadataBuilder.setClientRecordId("WPR" + Math.random());

        return new WheelchairPushesRecord.Builder(
                        testMetadataBuilder.build(),
                        Instant.now(),
                        Instant.now().plusMillis(1000),
                        1)
                .setStartZoneOffset(ZoneOffset.systemDefault().getRules().getOffset(Instant.now()))
                .setEndZoneOffset(ZoneOffset.systemDefault().getRules().getOffset(Instant.now()))
                .build();
    }

    static WheelchairPushesRecord getBaseWheelchairPushesRecord() {
        return new WheelchairPushesRecord.Builder(
                        new Metadata.Builder().build(),
                        Instant.now(),
                        Instant.now().plusMillis(1000),
                        10)
                .build();
    }

    static WheelchairPushesRecord getBaseWheelchairPushesRecord(int days) {
        Instant startTime = Instant.now().minus(days, ChronoUnit.DAYS);
        return new WheelchairPushesRecord.Builder(
                        new Metadata.Builder().build(), startTime, startTime.plusMillis(1000), 10)
                .build();
    }
}
