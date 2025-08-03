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
import android.health.connect.datatypes.Metadata;
import android.health.connect.datatypes.Record;
import android.health.connect.datatypes.RestingHeartRateRecord;
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
public class RestingHeartRateRecordTest {
    private static final String TAG = "RestingHeartRateRecordTest";
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
                RestingHeartRateRecord.class,
                new TimeInstantRangeFilter.Builder()
                        .setStartTime(Instant.EPOCH)
                        .setEndTime(Instant.now())
                        .build());
        TestUtils.deleteAllStagedRemoteData();
    }

    @Test
    public void testInsertRestingHeartRateRecord() throws InterruptedException {
        List<Record> records =
                List.of(getBaseRestingHeartRateRecord(), getCompleteRestingHeartRateRecord());
        TestUtils.insertRecords(records);
    }

    @Test
    public void testReadRestingHeartRateRecord_usingIds() throws InterruptedException {
        List<Record> recordList =
                Arrays.asList(
                        getCompleteRestingHeartRateRecord(), getCompleteRestingHeartRateRecord());
        List<Record> insertedRecords = TestUtils.insertRecords(recordList);
        readRestingHeartRateRecordUsingIds(insertedRecords);
    }

    @Test
    public void testReadRestingHeartRateRecord_invalidIds() throws InterruptedException {
        ReadRecordsRequestUsingIds<RestingHeartRateRecord> request =
                new ReadRecordsRequestUsingIds.Builder<>(RestingHeartRateRecord.class)
                        .addId(UUID.randomUUID().toString())
                        .build();
        List<RestingHeartRateRecord> result = TestUtils.readRecords(request);
        assertThat(result.size()).isEqualTo(0);
    }

    @Test
    public void testReadRestingHeartRateRecord_usingClientRecordIds() throws InterruptedException {
        List<Record> recordList =
                Arrays.asList(
                        getCompleteRestingHeartRateRecord(), getCompleteRestingHeartRateRecord());
        List<Record> insertedRecords = TestUtils.insertRecords(recordList);
        readRestingHeartRateRecordUsingClientId(insertedRecords);
    }

    @Test
    public void testReadRestingHeartRateRecord_invalidClientRecordIds()
            throws InterruptedException {
        ReadRecordsRequestUsingIds<RestingHeartRateRecord> request =
                new ReadRecordsRequestUsingIds.Builder<>(RestingHeartRateRecord.class)
                        .addClientRecordId("abc")
                        .build();
        List<RestingHeartRateRecord> result = TestUtils.readRecords(request);
        assertThat(result.size()).isEqualTo(0);
    }

    @Test
    public void testReadRestingHeartRateRecordUsingFilters_default() throws InterruptedException {
        List<RestingHeartRateRecord> oldRestingHeartRateRecords =
                TestUtils.readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(RestingHeartRateRecord.class)
                                .build());
        RestingHeartRateRecord testRecord = getCompleteRestingHeartRateRecord();
        TestUtils.insertRecords(Collections.singletonList(testRecord));
        List<RestingHeartRateRecord> newRestingHeartRateRecords =
                TestUtils.readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(RestingHeartRateRecord.class)
                                .build());
        assertThat(newRestingHeartRateRecords.size())
                .isEqualTo(oldRestingHeartRateRecords.size() + 1);
        assertThat(
                        newRestingHeartRateRecords
                                .get(newRestingHeartRateRecords.size() - 1)
                                .equals(testRecord))
                .isTrue();
    }

    @Test
    public void testReadRestingHeartRateRecordUsingFilters_timeFilter()
            throws InterruptedException {
        TimeInstantRangeFilter filter =
                new TimeInstantRangeFilter.Builder()
                        .setStartTime(Instant.now())
                        .setEndTime(Instant.now().plusMillis(3000))
                        .build();
        RestingHeartRateRecord testRecord = getCompleteRestingHeartRateRecord();
        TestUtils.insertRecords(Collections.singletonList(testRecord));
        List<RestingHeartRateRecord> newRestingHeartRateRecords =
                TestUtils.readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(RestingHeartRateRecord.class)
                                .setTimeRangeFilter(filter)
                                .build());
        assertThat(newRestingHeartRateRecords.size()).isEqualTo(1);
        assertThat(
                        newRestingHeartRateRecords
                                .get(newRestingHeartRateRecords.size() - 1)
                                .equals(testRecord))
                .isTrue();
    }

    @Test
    public void testReadRestingHeartRateRecordUsingFilters_dataFilter_correct()
            throws InterruptedException {
        Context context = ApplicationProvider.getApplicationContext();
        List<RestingHeartRateRecord> oldRestingHeartRateRecords =
                TestUtils.readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(RestingHeartRateRecord.class)
                                .addDataOrigins(
                                        new DataOrigin.Builder()
                                                .setPackageName(context.getPackageName())
                                                .build())
                                .build());
        RestingHeartRateRecord testRecord = getCompleteRestingHeartRateRecord();
        TestUtils.insertRecords(Collections.singletonList(testRecord));
        List<RestingHeartRateRecord> newRestingHeartRateRecords =
                TestUtils.readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(RestingHeartRateRecord.class)
                                .addDataOrigins(
                                        new DataOrigin.Builder()
                                                .setPackageName(context.getPackageName())
                                                .build())
                                .build());
        assertThat(newRestingHeartRateRecords.size() - oldRestingHeartRateRecords.size())
                .isEqualTo(1);
        RestingHeartRateRecord newRecord =
                newRestingHeartRateRecords.get(newRestingHeartRateRecords.size() - 1);
        assertThat(newRecord.equals(testRecord)).isTrue();
        assertThat(newRecord.getBeatsPerMinute()).isEqualTo(testRecord.getBeatsPerMinute());
    }

    @Test
    public void testReadRestingHeartRateRecordUsingFilters_dataFilter_incorrect()
            throws InterruptedException {
        TestUtils.insertRecords(Collections.singletonList(getCompleteRestingHeartRateRecord()));
        List<RestingHeartRateRecord> newRestingHeartRateRecords =
                TestUtils.readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(RestingHeartRateRecord.class)
                                .addDataOrigins(
                                        new DataOrigin.Builder().setPackageName("abc").build())
                                .build());
        assertThat(newRestingHeartRateRecords.size()).isEqualTo(0);
    }

    private void readRestingHeartRateRecordUsingClientId(List<Record> insertedRecord)
            throws InterruptedException {
        ReadRecordsRequestUsingIds.Builder<RestingHeartRateRecord> request =
                new ReadRecordsRequestUsingIds.Builder<>(RestingHeartRateRecord.class);
        for (Record record : insertedRecord) {
            request.addClientRecordId(record.getMetadata().getClientRecordId());
        }
        List<RestingHeartRateRecord> result = TestUtils.readRecords(request.build());
        assertThat(result.size()).isEqualTo(insertedRecord.size());
        assertThat(result).containsExactlyElementsIn(insertedRecord);
    }

    private void readRestingHeartRateRecordUsingIds(List<Record> recordList)
            throws InterruptedException {
        ReadRecordsRequestUsingIds.Builder<RestingHeartRateRecord> request =
                new ReadRecordsRequestUsingIds.Builder<>(RestingHeartRateRecord.class);
        for (Record record : recordList) {
            request.addId(record.getMetadata().getId());
        }
        ReadRecordsRequestUsingIds requestUsingIds = request.build();
        assertThat(requestUsingIds.getRecordType()).isEqualTo(RestingHeartRateRecord.class);
        assertThat(requestUsingIds.getRecordIdFilters()).isNotNull();
        List<RestingHeartRateRecord> result = TestUtils.readRecords(requestUsingIds);
        assertThat(result).hasSize(recordList.size());
        assertThat(result).containsExactlyElementsIn(recordList);
    }

    @Test
    public void testDeleteRestingHeartRateRecord_no_filters() throws InterruptedException {
        String id = TestUtils.insertRecordAndGetId(getCompleteRestingHeartRateRecord());
        TestUtils.verifyDeleteRecords(new DeleteUsingFiltersRequest.Builder().build());
        TestUtils.assertRecordNotFound(id, RestingHeartRateRecord.class);
    }

    @Test
    public void testDeleteRestingHeartRateRecord_time_filters() throws InterruptedException {
        TimeInstantRangeFilter timeRangeFilter =
                new TimeInstantRangeFilter.Builder()
                        .setStartTime(Instant.now())
                        .setEndTime(Instant.now().plusMillis(1000))
                        .build();
        String id = TestUtils.insertRecordAndGetId(getCompleteRestingHeartRateRecord());
        TestUtils.verifyDeleteRecords(
                new DeleteUsingFiltersRequest.Builder()
                        .addRecordType(RestingHeartRateRecord.class)
                        .setTimeRangeFilter(timeRangeFilter)
                        .build());
        TestUtils.assertRecordNotFound(id, RestingHeartRateRecord.class);
    }

    @Test
    public void testDeleteRestingHeartRateRecord_recordId_filters() throws InterruptedException {
        List<Record> records =
                List.of(getBaseRestingHeartRateRecord(), getCompleteRestingHeartRateRecord());
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
    public void testDeleteRestingHeartRateRecord_dataOrigin_filters() throws InterruptedException {
        Context context = ApplicationProvider.getApplicationContext();
        String id = TestUtils.insertRecordAndGetId(getCompleteRestingHeartRateRecord());
        TestUtils.verifyDeleteRecords(
                new DeleteUsingFiltersRequest.Builder()
                        .addDataOrigin(
                                new DataOrigin.Builder()
                                        .setPackageName(context.getPackageName())
                                        .build())
                        .build());
        TestUtils.assertRecordNotFound(id, RestingHeartRateRecord.class);
    }

    @Test
    public void testDeleteRestingHeartRateRecord_dataOrigin_filter_incorrect()
            throws InterruptedException {
        String id = TestUtils.insertRecordAndGetId(getCompleteRestingHeartRateRecord());
        TestUtils.verifyDeleteRecords(
                new DeleteUsingFiltersRequest.Builder()
                        .addDataOrigin(new DataOrigin.Builder().setPackageName("abc").build())
                        .build());
        TestUtils.assertRecordFound(id, RestingHeartRateRecord.class);
    }

    @Test
    public void testDeleteRestingHeartRateRecord_usingIds() throws InterruptedException {
        List<Record> records =
                List.of(getBaseRestingHeartRateRecord(), getCompleteRestingHeartRateRecord());
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
    public void testDeleteRestingHeartRateRecord_time_range() throws InterruptedException {
        TimeInstantRangeFilter timeRangeFilter =
                new TimeInstantRangeFilter.Builder()
                        .setStartTime(Instant.now())
                        .setEndTime(Instant.now().plusMillis(1000))
                        .build();
        String id = TestUtils.insertRecordAndGetId(getCompleteRestingHeartRateRecord());
        TestUtils.verifyDeleteRecords(RestingHeartRateRecord.class, timeRangeFilter);
        TestUtils.assertRecordNotFound(id, RestingHeartRateRecord.class);
    }

    @Test
    public void testBpmAggregation_timeRange_all() throws Exception {
        TestUtils.setupAggregation(PACKAGE_NAME, HealthDataCategory.VITALS);
        List<Record> records =
                Arrays.asList(
                        getBaseRestingHeartRateRecord(3),
                        getBaseRestingHeartRateRecord(5),
                        getBaseRestingHeartRateRecord(10));
        AggregateRecordsResponse<Long> response =
                TestUtils.getAggregateResponse(
                        new AggregateRecordsRequest.Builder<Long>(
                                        new TimeInstantRangeFilter.Builder()
                                                .setStartTime(Instant.ofEpochMilli(0))
                                                .setEndTime(Instant.now().plus(1, ChronoUnit.DAYS))
                                                .build())
                                .addAggregationType(RestingHeartRateRecord.BPM_MAX)
                                .addAggregationType(RestingHeartRateRecord.BPM_MIN)
                                .addAggregationType(RestingHeartRateRecord.BPM_AVG)
                                .build(),
                        records);
        assertThat(response.get(RestingHeartRateRecord.BPM_MAX)).isNotNull();
        assertThat(response.get(RestingHeartRateRecord.BPM_MAX)).isEqualTo(10);
        assertThat(response.getZoneOffset(RestingHeartRateRecord.BPM_MAX))
                .isEqualTo(ZoneOffset.systemDefault().getRules().getOffset(Instant.now()));
        assertThat(response.get(RestingHeartRateRecord.BPM_MIN)).isNotNull();
        assertThat(response.get(RestingHeartRateRecord.BPM_MIN)).isEqualTo(3);
        assertThat(response.getZoneOffset(RestingHeartRateRecord.BPM_MIN))
                .isEqualTo(ZoneOffset.systemDefault().getRules().getOffset(Instant.now()));
        assertThat(response.get(RestingHeartRateRecord.BPM_AVG)).isNotNull();
        assertThat(response.get(RestingHeartRateRecord.BPM_AVG)).isEqualTo(6);
        assertThat(response.getZoneOffset(RestingHeartRateRecord.BPM_AVG))
                .isEqualTo(ZoneOffset.systemDefault().getRules().getOffset(Instant.now()));
        Set<DataOrigin> dataOrigins = response.getDataOrigins(RestingHeartRateRecord.BPM_MIN);
        for (DataOrigin itr : dataOrigins) {
            assertThat(itr.getPackageName()).isEqualTo("android.healthconnect.cts");
        }
    }

    @Test
    public void testZoneOffsets() {
        final ZoneOffset defaultZoneOffset =
                ZoneOffset.systemDefault().getRules().getOffset(Instant.now());
        final ZoneOffset zoneOffset = ZoneOffset.UTC;
        RestingHeartRateRecord.Builder builder =
                new RestingHeartRateRecord.Builder(
                        new Metadata.Builder().build(), Instant.now(), 1);

        assertThat(builder.setZoneOffset(zoneOffset).build().getZoneOffset()).isEqualTo(zoneOffset);
        assertThat(builder.clearZoneOffset().build().getZoneOffset()).isEqualTo(defaultZoneOffset);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCreateRestingHeartRateRecord_invalidValue() {
        new RestingHeartRateRecord.Builder(new Metadata.Builder().build(), Instant.now(), 500)
                .build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCreateRestingHeartRateRecord_invalidTime() {
        new RestingHeartRateRecord.Builder(
                        new Metadata.Builder().build(), Instant.now().plusMillis(100), 500)
                .build();
    }

    @Test
    public void testUpdateRecords_validInput_dataBaseUpdatedSuccessfully()
            throws InterruptedException {

        List<Record> insertedRecords =
                TestUtils.insertRecords(
                        Arrays.asList(
                                getCompleteRestingHeartRateRecord(),
                                getCompleteRestingHeartRateRecord()));

        // read inserted records and verify that the data is same as inserted.
        readRestingHeartRateRecordUsingIds(insertedRecords);

        // Generate a new set of records that will be used to perform the update operation.
        List<Record> updateRecords =
                Arrays.asList(
                        getCompleteRestingHeartRateRecord(), getCompleteRestingHeartRateRecord());

        // Modify the uid of the updateRecords to the uuid that was present in the insert records.
        for (int itr = 0; itr < updateRecords.size(); itr++) {
            updateRecords.set(
                    itr,
                    getRestingHeartRateRecord_update(
                            updateRecords.get(itr),
                            insertedRecords.get(itr).getMetadata().getId(),
                            insertedRecords.get(itr).getMetadata().getClientRecordId()));
        }

        TestUtils.updateRecords(updateRecords);

        // assert the inserted data has been modified by reading the data.
        readRestingHeartRateRecordUsingIds(updateRecords);
    }

    @Test
    public void testUpdateRecords_invalidInputRecords_noChangeInDataBase()
            throws InterruptedException {
        List<Record> insertedRecords =
                TestUtils.insertRecords(
                        Arrays.asList(
                                getCompleteRestingHeartRateRecord(),
                                getCompleteRestingHeartRateRecord()));

        // read inserted records and verify that the data is same as inserted.
        readRestingHeartRateRecordUsingIds(insertedRecords);

        // Generate a second set of records that will be used to perform the update operation.
        List<Record> updateRecords =
                Arrays.asList(
                        getCompleteRestingHeartRateRecord(), getCompleteRestingHeartRateRecord());

        // Modify the Uid of the updateRecords to the UUID that was present in the insert records,
        // leaving out alternate records so that they have a new UUID which is not present in the
        // dataBase.
        for (int itr = 0; itr < updateRecords.size(); itr++) {
            updateRecords.set(
                    itr,
                    getRestingHeartRateRecord_update(
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
        readRestingHeartRateRecordUsingIds(insertedRecords);
    }

    @Test
    public void testUpdateRecords_recordWithInvalidPackageName_noChangeInDataBase()
            throws InterruptedException {
        List<Record> insertedRecords =
                TestUtils.insertRecords(
                        Arrays.asList(
                                getCompleteRestingHeartRateRecord(),
                                getCompleteRestingHeartRateRecord()));

        // read inserted records and verify that the data is same as inserted.
        readRestingHeartRateRecordUsingIds(insertedRecords);

        // Generate a second set of records that will be used to perform the update operation.
        List<Record> updateRecords =
                Arrays.asList(
                        getCompleteRestingHeartRateRecord(), getCompleteRestingHeartRateRecord());

        // Modify the Uuid of the updateRecords to the uuid that was present in the insert records.
        for (int itr = 0; itr < updateRecords.size(); itr++) {
            updateRecords.set(
                    itr,
                    getRestingHeartRateRecord_update(
                            updateRecords.get(itr),
                            insertedRecords.get(itr).getMetadata().getId(),
                            insertedRecords.get(itr).getMetadata().getClientRecordId()));
            //             adding an entry with invalid packageName.
            updateRecords.set(itr, getCompleteRestingHeartRateRecord());
        }

        try {
            TestUtils.updateRecords(updateRecords);
            Assert.fail("Expected to fail due to invalid package.");
        } catch (Exception exception) {
            // verify that the testcase failed due to invalid argument exception.
            assertThat(exception).isNotNull();
        }

        // assert the inserted data has not been modified by reading the data.
        readRestingHeartRateRecordUsingIds(insertedRecords);
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
                                .addRecordType(RestingHeartRateRecord.class)
                                .build());
        ChangeLogsRequest changeLogsRequest =
                new ChangeLogsRequest.Builder(tokenResponse.getToken()).build();
        ChangeLogsResponse response = TestUtils.getChangeLogs(changeLogsRequest);
        assertThat(response.getUpsertedRecords().size()).isEqualTo(0);
        assertThat(response.getDeletedLogs().size()).isEqualTo(0);

        List<Record> testRecord = Collections.singletonList(getCompleteRestingHeartRateRecord());
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
                        .addRecordType(RestingHeartRateRecord.class)
                        .build());
        response = TestUtils.getChangeLogs(changeLogsRequest);
        assertThat(response.getDeletedLogs()).isEmpty();
    }

    RestingHeartRateRecord getRestingHeartRateRecord_update(
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
        return new RestingHeartRateRecord.Builder(metadataWithId, Instant.now(), 2)
                .setZoneOffset(ZoneOffset.systemDefault().getRules().getOffset(Instant.now()))
                .build();
    }

    private static RestingHeartRateRecord getBaseRestingHeartRateRecord() {
        return new RestingHeartRateRecord.Builder(new Metadata.Builder().build(), Instant.now(), 1)
                .build();
    }

    static RestingHeartRateRecord getBaseRestingHeartRateRecord(int beats) {
        return new RestingHeartRateRecord.Builder(
                        new Metadata.Builder().setClientRecordId("RHRR" + Math.random()).build(),
                        Instant.now(),
                        beats)
                .build();
    }

    private static RestingHeartRateRecord getCompleteRestingHeartRateRecord() {
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
        testMetadataBuilder.setClientRecordId("RHRR" + Math.random());
        testMetadataBuilder.setRecordingMethod(Metadata.RECORDING_METHOD_ACTIVELY_RECORDED);

        return new RestingHeartRateRecord.Builder(testMetadataBuilder.build(), Instant.now(), 1)
                .setZoneOffset(ZoneOffset.systemDefault().getRules().getOffset(Instant.now()))
                .build();
    }
}
