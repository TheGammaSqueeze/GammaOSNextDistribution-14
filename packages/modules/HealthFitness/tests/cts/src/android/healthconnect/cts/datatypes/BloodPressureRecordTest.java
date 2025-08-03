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

import static android.health.connect.datatypes.BloodPressureRecord.DIASTOLIC_AVG;
import static android.health.connect.datatypes.BloodPressureRecord.DIASTOLIC_MAX;
import static android.health.connect.datatypes.BloodPressureRecord.DIASTOLIC_MIN;
import static android.health.connect.datatypes.BloodPressureRecord.SYSTOLIC_AVG;
import static android.health.connect.datatypes.BloodPressureRecord.SYSTOLIC_MAX;
import static android.health.connect.datatypes.BloodPressureRecord.SYSTOLIC_MIN;

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
import android.health.connect.datatypes.BloodPressureRecord;
import android.health.connect.datatypes.DataOrigin;
import android.health.connect.datatypes.Device;
import android.health.connect.datatypes.Metadata;
import android.health.connect.datatypes.Record;
import android.health.connect.datatypes.units.Pressure;
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
public class BloodPressureRecordTest {
    private static final String TAG = "BloodPressureRecordTest";
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
                BloodPressureRecord.class,
                new TimeInstantRangeFilter.Builder()
                        .setStartTime(Instant.EPOCH)
                        .setEndTime(Instant.now())
                        .build());
        TestUtils.deleteAllStagedRemoteData();
    }

    @Test
    public void testInsertBloodPressureRecord() throws InterruptedException {
        List<Record> records =
                List.of(getBaseBloodPressureRecord(), getCompleteBloodPressureRecord());
        TestUtils.insertRecords(records);
    }

    @Test
    public void testReadBloodPressureRecord_usingIds() throws InterruptedException {
        List<Record> recordList =
                Arrays.asList(getCompleteBloodPressureRecord(), getCompleteBloodPressureRecord());
        List<Record> insertedRecords = TestUtils.insertRecords(recordList);
        readBloodPressureRecordUsingIds(insertedRecords);
    }

    @Test
    public void testReadBloodPressureRecord_invalidIds() throws InterruptedException {
        ReadRecordsRequestUsingIds<BloodPressureRecord> request =
                new ReadRecordsRequestUsingIds.Builder<>(BloodPressureRecord.class)
                        .addId(UUID.randomUUID().toString())
                        .build();
        List<BloodPressureRecord> result = TestUtils.readRecords(request);
        assertThat(result.size()).isEqualTo(0);
    }

    @Test
    public void testReadBloodPressureRecord_usingClientRecordIds() throws InterruptedException {
        List<Record> recordList =
                Arrays.asList(getCompleteBloodPressureRecord(), getCompleteBloodPressureRecord());
        List<Record> insertedRecords = TestUtils.insertRecords(recordList);
        readBloodPressureRecordUsingClientId(insertedRecords);
    }

    @Test
    public void testReadBloodPressureRecord_invalidClientRecordIds() throws InterruptedException {
        ReadRecordsRequestUsingIds<BloodPressureRecord> request =
                new ReadRecordsRequestUsingIds.Builder<>(BloodPressureRecord.class)
                        .addClientRecordId("abc")
                        .build();
        List<BloodPressureRecord> result = TestUtils.readRecords(request);
        assertThat(result.size()).isEqualTo(0);
    }

    @Test
    public void testReadBloodPressureRecordUsingFilters_default() throws InterruptedException {
        List<BloodPressureRecord> oldBloodPressureRecords =
                TestUtils.readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(BloodPressureRecord.class)
                                .build());
        BloodPressureRecord testRecord = getCompleteBloodPressureRecord();
        TestUtils.insertRecords(Collections.singletonList(testRecord));
        List<BloodPressureRecord> newBloodPressureRecords =
                TestUtils.readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(BloodPressureRecord.class)
                                .build());
        assertThat(newBloodPressureRecords.size()).isEqualTo(oldBloodPressureRecords.size() + 1);
        assertThat(
                        newBloodPressureRecords
                                .get(newBloodPressureRecords.size() - 1)
                                .equals(testRecord))
                .isTrue();
    }

    @Test
    public void testReadBloodPressureRecordUsingFilters_timeFilter() throws InterruptedException {
        TimeInstantRangeFilter filter =
                new TimeInstantRangeFilter.Builder()
                        .setStartTime(Instant.now())
                        .setEndTime(Instant.now().plusMillis(3000))
                        .build();
        BloodPressureRecord testRecord = getCompleteBloodPressureRecord();
        TestUtils.insertRecords(Collections.singletonList(testRecord));
        List<BloodPressureRecord> newBloodPressureRecords =
                TestUtils.readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(BloodPressureRecord.class)
                                .setTimeRangeFilter(filter)
                                .build());
        assertThat(newBloodPressureRecords.size()).isEqualTo(1);
        assertThat(
                        newBloodPressureRecords
                                .get(newBloodPressureRecords.size() - 1)
                                .equals(testRecord))
                .isTrue();
    }

    @Test
    public void testReadBloodPressureRecordUsingFilters_dataFilter_correct()
            throws InterruptedException {
        Context context = ApplicationProvider.getApplicationContext();
        List<BloodPressureRecord> oldBloodPressureRecords =
                TestUtils.readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(BloodPressureRecord.class)
                                .addDataOrigins(
                                        new DataOrigin.Builder()
                                                .setPackageName(context.getPackageName())
                                                .build())
                                .build());
        BloodPressureRecord testRecord = getCompleteBloodPressureRecord();
        TestUtils.insertRecords(Collections.singletonList(testRecord));
        List<BloodPressureRecord> newBloodPressureRecords =
                TestUtils.readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(BloodPressureRecord.class)
                                .addDataOrigins(
                                        new DataOrigin.Builder()
                                                .setPackageName(context.getPackageName())
                                                .build())
                                .build());
        assertThat(newBloodPressureRecords.size() - oldBloodPressureRecords.size()).isEqualTo(1);
        BloodPressureRecord newRecord =
                newBloodPressureRecords.get(newBloodPressureRecords.size() - 1);
        assertThat(newRecord.equals(testRecord)).isTrue();
        assertThat(newRecord.getMeasurementLocation())
                .isEqualTo(testRecord.getMeasurementLocation());
        assertThat(newRecord.getSystolic()).isEqualTo(testRecord.getSystolic());
        assertThat(newRecord.getDiastolic()).isEqualTo(testRecord.getDiastolic());
        assertThat(newRecord.getBodyPosition()).isEqualTo(testRecord.getBodyPosition());
    }

    @Test
    public void testReadBloodPressureRecordUsingFilters_dataFilter_incorrect()
            throws InterruptedException {
        TestUtils.insertRecords(Collections.singletonList(getCompleteBloodPressureRecord()));
        List<BloodPressureRecord> newBloodPressureRecords =
                TestUtils.readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(BloodPressureRecord.class)
                                .addDataOrigins(
                                        new DataOrigin.Builder().setPackageName("abc").build())
                                .build());
        assertThat(newBloodPressureRecords.size()).isEqualTo(0);
    }

    private void readBloodPressureRecordUsingClientId(List<Record> insertedRecord)
            throws InterruptedException {
        ReadRecordsRequestUsingIds.Builder<BloodPressureRecord> request =
                new ReadRecordsRequestUsingIds.Builder<>(BloodPressureRecord.class);
        for (Record record : insertedRecord) {
            request.addClientRecordId(record.getMetadata().getClientRecordId());
        }
        List<BloodPressureRecord> result = TestUtils.readRecords(request.build());
        assertThat(result.size()).isEqualTo(insertedRecord.size());
        assertThat(result).containsExactlyElementsIn(insertedRecord);
    }

    private void readBloodPressureRecordUsingIds(List<Record> recordList)
            throws InterruptedException {
        ReadRecordsRequestUsingIds.Builder<BloodPressureRecord> request =
                new ReadRecordsRequestUsingIds.Builder<>(BloodPressureRecord.class);
        for (Record record : recordList) {
            request.addId(record.getMetadata().getId());
        }
        ReadRecordsRequestUsingIds requestUsingIds = request.build();
        assertThat(requestUsingIds.getRecordType()).isEqualTo(BloodPressureRecord.class);
        assertThat(requestUsingIds.getRecordIdFilters()).isNotNull();
        List<BloodPressureRecord> result = TestUtils.readRecords(requestUsingIds);
        assertThat(result).hasSize(recordList.size());
        assertThat(result).containsExactlyElementsIn(recordList);
    }

    @Test
    public void testDeleteBloodPressureRecord_no_filters() throws InterruptedException {
        String id = TestUtils.insertRecordAndGetId(getCompleteBloodPressureRecord());
        TestUtils.verifyDeleteRecords(new DeleteUsingFiltersRequest.Builder().build());
        TestUtils.assertRecordNotFound(id, BloodPressureRecord.class);
    }

    @Test
    public void testDeleteBloodPressureRecord_time_filters() throws InterruptedException {
        TimeInstantRangeFilter timeRangeFilter =
                new TimeInstantRangeFilter.Builder()
                        .setStartTime(Instant.now())
                        .setEndTime(Instant.now().plusMillis(1000))
                        .build();
        String id = TestUtils.insertRecordAndGetId(getCompleteBloodPressureRecord());
        TestUtils.verifyDeleteRecords(
                new DeleteUsingFiltersRequest.Builder()
                        .addRecordType(BloodPressureRecord.class)
                        .setTimeRangeFilter(timeRangeFilter)
                        .build());
        TestUtils.assertRecordNotFound(id, BloodPressureRecord.class);
    }

    @Test
    public void testDeleteBloodPressureRecord_recordId_filters() throws InterruptedException {
        List<Record> records =
                List.of(getBaseBloodPressureRecord(), getCompleteBloodPressureRecord());
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
    public void testDeleteBloodPressureRecord_dataOrigin_filters() throws InterruptedException {
        Context context = ApplicationProvider.getApplicationContext();
        String id = TestUtils.insertRecordAndGetId(getCompleteBloodPressureRecord());
        TestUtils.verifyDeleteRecords(
                new DeleteUsingFiltersRequest.Builder()
                        .addDataOrigin(
                                new DataOrigin.Builder()
                                        .setPackageName(context.getPackageName())
                                        .build())
                        .build());
        TestUtils.assertRecordNotFound(id, BloodPressureRecord.class);
    }

    @Test
    public void testDeleteBloodPressureRecord_dataOrigin_filter_incorrect()
            throws InterruptedException {
        String id = TestUtils.insertRecordAndGetId(getCompleteBloodPressureRecord());
        TestUtils.verifyDeleteRecords(
                new DeleteUsingFiltersRequest.Builder()
                        .addDataOrigin(new DataOrigin.Builder().setPackageName("abc").build())
                        .build());
        TestUtils.assertRecordFound(id, BloodPressureRecord.class);
    }

    @Test
    public void testDeleteBloodPressureRecord_usingIds() throws InterruptedException {
        List<Record> records =
                List.of(getBaseBloodPressureRecord(), getCompleteBloodPressureRecord());
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
    public void testDeleteBloodPressureRecord_time_range() throws InterruptedException {
        TimeInstantRangeFilter timeRangeFilter =
                new TimeInstantRangeFilter.Builder()
                        .setStartTime(Instant.now())
                        .setEndTime(Instant.now().plusMillis(1000))
                        .build();
        String id = TestUtils.insertRecordAndGetId(getCompleteBloodPressureRecord());
        TestUtils.verifyDeleteRecords(BloodPressureRecord.class, timeRangeFilter);
        TestUtils.assertRecordNotFound(id, BloodPressureRecord.class);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCreateBloodPressureRecord_invalidDiastolicValue() {
        new BloodPressureRecord.Builder(
                        new Metadata.Builder().build(),
                        Instant.now(),
                        1,
                        Pressure.fromMillimetersOfMercury(19.0),
                        Pressure.fromMillimetersOfMercury(10.0),
                        1)
                .build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCreateBloodPressureRecord_invalidSystolicValue() {
        new BloodPressureRecord.Builder(
                        new Metadata.Builder().build(),
                        Instant.now(),
                        1,
                        Pressure.fromMillimetersOfMercury(20.0),
                        Pressure.fromMillimetersOfMercury(9.0),
                        1)
                .build();
    }

    @Test
    public void testZoneOffsets() {
        final ZoneOffset defaultZoneOffset =
                ZoneOffset.systemDefault().getRules().getOffset(Instant.now());
        final ZoneOffset zoneOffset = ZoneOffset.UTC;
        BloodPressureRecord.Builder builder =
                new BloodPressureRecord.Builder(
                        new Metadata.Builder().build(),
                        Instant.now(),
                        1,
                        Pressure.fromMillimetersOfMercury(20.0),
                        Pressure.fromMillimetersOfMercury(10.0),
                        1);

        assertThat(builder.setZoneOffset(zoneOffset).build().getZoneOffset()).isEqualTo(zoneOffset);
        assertThat(builder.clearZoneOffset().build().getZoneOffset()).isEqualTo(defaultZoneOffset);
    }

    @Test
    public void testUpdateRecords_validInput_dataBaseUpdatedSuccessfully()
            throws InterruptedException {

        List<Record> insertedRecords =
                TestUtils.insertRecords(
                        Arrays.asList(
                                getCompleteBloodPressureRecord(),
                                getCompleteBloodPressureRecord()));

        // read inserted records and verify that the data is same as inserted.
        readBloodPressureRecordUsingIds(insertedRecords);

        // Generate a new set of records that will be used to perform the update operation.
        List<Record> updateRecords =
                Arrays.asList(getCompleteBloodPressureRecord(), getCompleteBloodPressureRecord());

        // Modify the uid of the updateRecords to the uuid that was present in the insert records.
        for (int itr = 0; itr < updateRecords.size(); itr++) {
            updateRecords.set(
                    itr,
                    getBloodPressureRecord_update(
                            updateRecords.get(itr),
                            insertedRecords.get(itr).getMetadata().getId(),
                            insertedRecords.get(itr).getMetadata().getClientRecordId()));
        }

        TestUtils.updateRecords(updateRecords);

        // assert the inserted data has been modified by reading the data.
        readBloodPressureRecordUsingIds(updateRecords);
    }

    private static BloodPressureRecord getBaseBloodPressureRecord(
            double systolic, double diastolic, int delay) {
        return new BloodPressureRecord.Builder(
                        new Metadata.Builder().build(),
                        Instant.now().minusMillis(delay),
                        1,
                        Pressure.fromMillimetersOfMercury(systolic),
                        Pressure.fromMillimetersOfMercury(diastolic),
                        1)
                .build();
    }

    @Test
    public void testAggregation_bloodPressure() throws Exception {
        TestUtils.setupAggregation(PACKAGE_NAME, HealthDataCategory.VITALS);
        Context context = ApplicationProvider.getApplicationContext();
        List<Record> records =
                Arrays.asList(
                        getBaseBloodPressureRecord(26.0, 35.0, 10),
                        getBaseBloodPressureRecord(30.0, 60.0, 5),
                        getBaseBloodPressureRecord(40.0, 52.0, 1));
        AggregateRecordsResponse<Pressure> response =
                TestUtils.getAggregateResponse(
                        new AggregateRecordsRequest.Builder<Pressure>(
                                        new TimeInstantRangeFilter.Builder()
                                                .setStartTime(Instant.ofEpochMilli(0))
                                                .setEndTime(Instant.now().plus(1, ChronoUnit.DAYS))
                                                .build())
                                .addAggregationType(DIASTOLIC_AVG)
                                .addAggregationType(DIASTOLIC_MAX)
                                .addAggregationType(DIASTOLIC_MIN)
                                .addAggregationType(SYSTOLIC_AVG)
                                .addAggregationType(SYSTOLIC_MAX)
                                .addAggregationType(SYSTOLIC_MIN)
                                .addDataOriginsFilter(
                                        new DataOrigin.Builder()
                                                .setPackageName(context.getPackageName())
                                                .build())
                                .build(),
                        records);
        Pressure maxDiastolicPressure = response.get(DIASTOLIC_MAX);
        Pressure minDiastolicPressure = response.get(DIASTOLIC_MIN);
        Pressure avgDiastolicPressure = response.get(DIASTOLIC_AVG);
        Pressure maxSystolicPressure = response.get(SYSTOLIC_MAX);
        Pressure minSystolicPressure = response.get(SYSTOLIC_MIN);
        Pressure avgSystolicPressure = response.get(SYSTOLIC_AVG);
        assertThat(maxDiastolicPressure).isNotNull();
        assertThat(maxDiastolicPressure.getInMillimetersOfMercury()).isEqualTo(60.0);
        assertThat(minDiastolicPressure).isNotNull();
        assertThat(minDiastolicPressure.getInMillimetersOfMercury()).isEqualTo(35.0);
        assertThat(avgDiastolicPressure).isNotNull();
        assertThat(avgDiastolicPressure.getInMillimetersOfMercury()).isEqualTo(49.0);
        assertThat(maxSystolicPressure).isNotNull();
        assertThat(maxSystolicPressure.getInMillimetersOfMercury()).isEqualTo(40.0);
        assertThat(minSystolicPressure).isNotNull();
        assertThat(minSystolicPressure.getInMillimetersOfMercury()).isEqualTo(26.0);
        assertThat(avgSystolicPressure).isNotNull();
        assertThat(avgSystolicPressure.getInMillimetersOfMercury()).isEqualTo(32.0);
        Set<DataOrigin> dataOrigins = response.getDataOrigins(DIASTOLIC_AVG);
        for (DataOrigin itr : dataOrigins) {
            assertThat(itr.getPackageName()).isEqualTo(PACKAGE_NAME);
        }
    }

    @Test
    public void testUpdateRecords_invalidInputRecords_noChangeInDataBase()
            throws InterruptedException {
        List<Record> insertedRecords =
                TestUtils.insertRecords(
                        Arrays.asList(
                                getCompleteBloodPressureRecord(),
                                getCompleteBloodPressureRecord()));

        // read inserted records and verify that the data is same as inserted.
        readBloodPressureRecordUsingIds(insertedRecords);

        // Generate a second set of records that will be used to perform the update operation.
        List<Record> updateRecords =
                Arrays.asList(getCompleteBloodPressureRecord(), getCompleteBloodPressureRecord());

        // Modify the Uid of the updateRecords to the UUID that was present in the insert records,
        // leaving out alternate records so that they have a new UUID which is not present in the
        // dataBase.
        for (int itr = 0; itr < updateRecords.size(); itr++) {
            updateRecords.set(
                    itr,
                    getBloodPressureRecord_update(
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
        readBloodPressureRecordUsingIds(insertedRecords);
    }

    @Test
    public void testUpdateRecords_recordWithInvalidPackageName_noChangeInDataBase()
            throws InterruptedException {
        List<Record> insertedRecords =
                TestUtils.insertRecords(
                        Arrays.asList(
                                getCompleteBloodPressureRecord(),
                                getCompleteBloodPressureRecord()));

        // read inserted records and verify that the data is same as inserted.
        readBloodPressureRecordUsingIds(insertedRecords);

        // Generate a second set of records that will be used to perform the update operation.
        List<Record> updateRecords =
                Arrays.asList(getCompleteBloodPressureRecord(), getCompleteBloodPressureRecord());

        // Modify the Uuid of the updateRecords to the uuid that was present in the insert records.
        for (int itr = 0; itr < updateRecords.size(); itr++) {
            updateRecords.set(
                    itr,
                    getBloodPressureRecord_update(
                            updateRecords.get(itr),
                            insertedRecords.get(itr).getMetadata().getId(),
                            insertedRecords.get(itr).getMetadata().getClientRecordId()));
            //             adding an entry with invalid packageName.
            updateRecords.set(itr, getCompleteBloodPressureRecord());
        }

        try {
            TestUtils.updateRecords(updateRecords);
            Assert.fail("Expected to fail due to invalid package.");
        } catch (Exception exception) {
            // verify that the testcase failed due to invalid argument exception.
            assertThat(exception).isNotNull();
        }

        // assert the inserted data has not been modified by reading the data.
        readBloodPressureRecordUsingIds(insertedRecords);
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
                                .addRecordType(BloodPressureRecord.class)
                                .build());
        ChangeLogsRequest changeLogsRequest =
                new ChangeLogsRequest.Builder(tokenResponse.getToken()).build();
        ChangeLogsResponse response = TestUtils.getChangeLogs(changeLogsRequest);
        assertThat(response.getUpsertedRecords().size()).isEqualTo(0);
        assertThat(response.getDeletedLogs().size()).isEqualTo(0);

        List<Record> testRecord = Collections.singletonList(getCompleteBloodPressureRecord());
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
                        .addRecordType(BloodPressureRecord.class)
                        .build());
        response = TestUtils.getChangeLogs(changeLogsRequest);
        assertThat(response.getDeletedLogs()).isEmpty();
    }

    BloodPressureRecord getBloodPressureRecord_update(
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
        return new BloodPressureRecord.Builder(
                        metadataWithId,
                        Instant.now(),
                        2,
                        Pressure.fromMillimetersOfMercury(30.0),
                        Pressure.fromMillimetersOfMercury(20.0),
                        2)
                .setZoneOffset(ZoneOffset.systemDefault().getRules().getOffset(Instant.now()))
                .build();
    }

    private static BloodPressureRecord getBaseBloodPressureRecord() {
        return new BloodPressureRecord.Builder(
                        new Metadata.Builder().build(),
                        Instant.now(),
                        1,
                        Pressure.fromMillimetersOfMercury(20.0),
                        Pressure.fromMillimetersOfMercury(10.0),
                        1)
                .build();
    }

    private static BloodPressureRecord getCompleteBloodPressureRecord() {
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
        testMetadataBuilder.setClientRecordId("BPR" + Math.random());
        testMetadataBuilder.setRecordingMethod(Metadata.RECORDING_METHOD_ACTIVELY_RECORDED);

        return new BloodPressureRecord.Builder(
                        testMetadataBuilder.build(),
                        Instant.now(),
                        1,
                        Pressure.fromMillimetersOfMercury(20.0),
                        Pressure.fromMillimetersOfMercury(10.0),
                        1)
                .setZoneOffset(ZoneOffset.systemDefault().getRules().getOffset(Instant.now()))
                .build();
    }
}
