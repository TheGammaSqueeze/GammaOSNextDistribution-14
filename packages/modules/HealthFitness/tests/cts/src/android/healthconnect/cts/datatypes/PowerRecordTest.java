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

import static android.health.connect.datatypes.PowerRecord.POWER_AVG;
import static android.health.connect.datatypes.PowerRecord.POWER_MAX;
import static android.health.connect.datatypes.PowerRecord.POWER_MIN;

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
import android.health.connect.datatypes.PowerRecord;
import android.health.connect.datatypes.Record;
import android.health.connect.datatypes.units.Power;
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
public class PowerRecordTest {
    private static final String TAG = "PowerRecordTest";
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
                PowerRecord.class,
                new TimeInstantRangeFilter.Builder()
                        .setStartTime(Instant.EPOCH)
                        .setEndTime(Instant.now())
                        .build());
        TestUtils.deleteAllStagedRemoteData();
    }

    @Test
    public void testInsertPowerRecord() throws InterruptedException {
        TestUtils.insertRecords(Arrays.asList(getBasePowerRecord(), getCompletePowerRecord()));
    }

    @Test
    public void testReadPowerRecord_usingIds() throws InterruptedException {
        testReadPowerRecordIds();
    }

    @Test
    public void testReadPowerRecord_invalidIds() throws InterruptedException {
        ReadRecordsRequestUsingIds<PowerRecord> request =
                new ReadRecordsRequestUsingIds.Builder<>(PowerRecord.class)
                        .addId(UUID.randomUUID().toString())
                        .build();
        List<PowerRecord> result = TestUtils.readRecords(request);
        assertThat(result.size()).isEqualTo(0);
    }

    @Test
    public void testReadPowerRecord_usingClientRecordIds() throws InterruptedException {
        List<Record> recordList = Arrays.asList(getCompletePowerRecord(), getCompletePowerRecord());
        List<Record> insertedRecords = TestUtils.insertRecords(recordList);
        readPowerRecordUsingClientId(insertedRecords);
    }

    @Test
    public void testReadPowerRecord_invalidClientRecordIds() throws InterruptedException {
        ReadRecordsRequestUsingIds<PowerRecord> request =
                new ReadRecordsRequestUsingIds.Builder<>(PowerRecord.class)
                        .addClientRecordId("abc")
                        .build();
        List<PowerRecord> result = TestUtils.readRecords(request);
        assertThat(result.size()).isEqualTo(0);
    }

    @Test
    public void testReadPowerRecordUsingFilters_default() throws InterruptedException {
        List<PowerRecord> oldPowerRecords =
                TestUtils.readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(PowerRecord.class).build());
        PowerRecord testRecord = getCompletePowerRecord();
        TestUtils.insertRecords(Collections.singletonList(testRecord));
        List<PowerRecord> newPowerRecords =
                TestUtils.readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(PowerRecord.class).build());
        assertThat(newPowerRecords.size()).isEqualTo(oldPowerRecords.size() + 1);
        assertThat(newPowerRecords.get(newPowerRecords.size() - 1).equals(testRecord)).isTrue();
    }

    @Test
    public void testReadPowerRecordUsingFilters_timeFilter() throws InterruptedException {
        TimeInstantRangeFilter filter =
                new TimeInstantRangeFilter.Builder()
                        .setStartTime(Instant.now())
                        .setEndTime(Instant.now().plusMillis(3000))
                        .build();
        PowerRecord testRecord = getCompletePowerRecord();
        TestUtils.insertRecords(Collections.singletonList(testRecord));
        List<PowerRecord> newPowerRecords =
                TestUtils.readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(PowerRecord.class)
                                .setTimeRangeFilter(filter)
                                .build());
        assertThat(newPowerRecords.get(newPowerRecords.size() - 1).equals(testRecord)).isTrue();
    }

    @Test
    public void testReadPowerRecordUsingFilters_dataFilter_correct() throws InterruptedException {
        Context context = ApplicationProvider.getApplicationContext();
        List<PowerRecord> oldPowerRecords =
                TestUtils.readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(PowerRecord.class)
                                .addDataOrigins(
                                        new DataOrigin.Builder()
                                                .setPackageName(context.getPackageName())
                                                .build())
                                .build());
        PowerRecord testRecord = getCompletePowerRecord();
        TestUtils.insertRecords(Collections.singletonList(testRecord));
        List<PowerRecord> newPowerRecords =
                TestUtils.readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(PowerRecord.class)
                                .addDataOrigins(
                                        new DataOrigin.Builder()
                                                .setPackageName(context.getPackageName())
                                                .build())
                                .build());
        assertThat(newPowerRecords.size() - oldPowerRecords.size()).isEqualTo(1);
        assertThat(newPowerRecords.get(newPowerRecords.size() - 1).equals(testRecord)).isTrue();
        PowerRecord newRecord = newPowerRecords.get(newPowerRecords.size() - 1);
        assertThat(newRecord.equals(testRecord)).isTrue();
        for (int idx = 0; idx < newRecord.getSamples().size(); idx++) {
            assertThat(newRecord.getSamples().get(idx).getTime().toEpochMilli())
                    .isEqualTo(testRecord.getSamples().get(idx).getTime().toEpochMilli());
            assertThat(newRecord.getSamples().get(idx).getPower())
                    .isEqualTo(testRecord.getSamples().get(idx).getPower());
        }
    }

    @Test
    public void testReadPowerRecordUsingFilters_dataFilter_incorrect() throws InterruptedException {
        TestUtils.insertRecords(Collections.singletonList(getCompletePowerRecord()));
        List<PowerRecord> newPowerRecords =
                TestUtils.readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(PowerRecord.class)
                                .addDataOrigins(
                                        new DataOrigin.Builder().setPackageName("abc").build())
                                .build());
        assertThat(newPowerRecords.size()).isEqualTo(0);
    }

    @Test
    public void testDeletePowerRecord_no_filters() throws InterruptedException {
        String id = TestUtils.insertRecordAndGetId(getCompletePowerRecord());
        TestUtils.verifyDeleteRecords(new DeleteUsingFiltersRequest.Builder().build());
        TestUtils.assertRecordNotFound(id, PowerRecord.class);
    }

    @Test
    public void testDeletePowerRecord_time_filters() throws InterruptedException {
        TimeInstantRangeFilter timeRangeFilter =
                new TimeInstantRangeFilter.Builder()
                        .setStartTime(Instant.now())
                        .setEndTime(Instant.now().plusMillis(1000))
                        .build();
        String id = TestUtils.insertRecordAndGetId(getCompletePowerRecord());
        TestUtils.verifyDeleteRecords(
                new DeleteUsingFiltersRequest.Builder()
                        .addRecordType(PowerRecord.class)
                        .setTimeRangeFilter(timeRangeFilter)
                        .build());
        TestUtils.assertRecordNotFound(id, PowerRecord.class);
    }

    @Test
    public void testDeletePowerRecord_recordId_filters() throws InterruptedException {
        List<Record> records = List.of(getBasePowerRecord(), getCompletePowerRecord());
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
    public void testDeletePowerRecord_dataOrigin_filters() throws InterruptedException {
        Context context = ApplicationProvider.getApplicationContext();
        String id = TestUtils.insertRecordAndGetId(getCompletePowerRecord());
        TestUtils.verifyDeleteRecords(
                new DeleteUsingFiltersRequest.Builder()
                        .addDataOrigin(
                                new DataOrigin.Builder()
                                        .setPackageName(context.getPackageName())
                                        .build())
                        .build());
        TestUtils.assertRecordNotFound(id, PowerRecord.class);
    }

    @Test
    public void testDeletePowerRecord_dataOrigin_filter_incorrect() throws InterruptedException {
        String id = TestUtils.insertRecordAndGetId(getCompletePowerRecord());
        TestUtils.verifyDeleteRecords(
                new DeleteUsingFiltersRequest.Builder()
                        .addDataOrigin(new DataOrigin.Builder().setPackageName("abc").build())
                        .build());
        TestUtils.assertRecordFound(id, PowerRecord.class);
    }

    @Test
    public void testDeletePowerRecord_usingIds() throws InterruptedException {
        List<Record> records = List.of(getBasePowerRecord(), getCompletePowerRecord());
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
    public void testDeletePowerRecord_time_range() throws InterruptedException {
        TimeInstantRangeFilter timeRangeFilter =
                new TimeInstantRangeFilter.Builder()
                        .setStartTime(Instant.now())
                        .setEndTime(Instant.now().plusMillis(1000))
                        .build();
        String id = TestUtils.insertRecordAndGetId(getCompletePowerRecord());
        TestUtils.verifyDeleteRecords(PowerRecord.class, timeRangeFilter);
        TestUtils.assertRecordNotFound(id, PowerRecord.class);
    }

    @Test
    public void testAggregation_power() throws Exception {
        TestUtils.setupAggregation(PACKAGE_NAME, HealthDataCategory.ACTIVITY);
        Context context = ApplicationProvider.getApplicationContext();
        List<Record> records =
                Arrays.asList(getPowerRecord(5.0), getPowerRecord(10.0), getPowerRecord(15.0));
        AggregateRecordsResponse<Power> response =
                TestUtils.getAggregateResponse(
                        new AggregateRecordsRequest.Builder<Power>(
                                        new TimeInstantRangeFilter.Builder()
                                                .setStartTime(Instant.ofEpochMilli(0))
                                                .setEndTime(Instant.now().plus(1, ChronoUnit.DAYS))
                                                .build())
                                .addAggregationType(POWER_MAX)
                                .addAggregationType(POWER_MIN)
                                .addAggregationType(POWER_AVG)
                                .addDataOriginsFilter(
                                        new DataOrigin.Builder()
                                                .setPackageName(context.getPackageName())
                                                .build())
                                .build(),
                        records);
        Power maxPower = response.get(POWER_MAX);
        Power minPower = response.get(POWER_MIN);
        Power avgPower = response.get(POWER_AVG);
        assertThat(maxPower).isNotNull();
        assertThat(maxPower.getInWatts()).isEqualTo(15.0);
        assertThat(minPower).isNotNull();
        assertThat(minPower.getInWatts()).isEqualTo(5.0);
        assertThat(avgPower).isNotNull();
        assertThat(avgPower.getInWatts()).isEqualTo(10.0);
        Set<DataOrigin> newDataOrigin = response.getDataOrigins(POWER_AVG);
        for (DataOrigin itr : newDataOrigin) {
            assertThat(itr.getPackageName()).isEqualTo("android.healthconnect.cts");
        }
    }

    @Test
    public void testZoneOffsets() {
        final ZoneOffset defaultZoneOffset =
                ZoneOffset.systemDefault().getRules().getOffset(Instant.now());
        final ZoneOffset startZoneOffset = ZoneOffset.UTC;
        final ZoneOffset endZoneOffset = ZoneOffset.MAX;
        PowerRecord.Builder builder =
                new PowerRecord.Builder(
                        new Metadata.Builder().build(),
                        Instant.now(),
                        Instant.now().plusMillis(1000),
                        Collections.emptyList());

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
                        Arrays.asList(getCompletePowerRecord(), getCompletePowerRecord()));

        // read inserted records and verify that the data is same as inserted.
        readPowerRecordUsingIds(insertedRecords);

        // Generate a new set of records that will be used to perform the update operation.
        List<Record> updateRecords =
                Arrays.asList(getCompletePowerRecord(), getCompletePowerRecord());

        // Modify the uid of the updateRecords to the uuid that was present in the insert records.
        for (int itr = 0; itr < updateRecords.size(); itr++) {
            updateRecords.set(
                    itr,
                    getPowerRecord_update(
                            updateRecords.get(itr),
                            insertedRecords.get(itr).getMetadata().getId(),
                            insertedRecords.get(itr).getMetadata().getClientRecordId()));
        }

        TestUtils.updateRecords(updateRecords);

        // assert the inserted data has been modified by reading the data.
        readPowerRecordUsingIds(updateRecords);
    }

    @Test
    public void testUpdateRecords_invalidInputRecords_noChangeInDataBase()
            throws InterruptedException {
        List<Record> insertedRecords =
                TestUtils.insertRecords(
                        Arrays.asList(getCompletePowerRecord(), getCompletePowerRecord()));

        // read inserted records and verify that the data is same as inserted.
        readPowerRecordUsingIds(insertedRecords);

        // Generate a second set of records that will be used to perform the update operation.
        List<Record> updateRecords =
                Arrays.asList(getCompletePowerRecord(), getCompletePowerRecord());

        // Modify the Uid of the updateRecords to the UUID that was present in the insert records,
        // leaving out alternate records so that they have a new UUID which is not present in the
        // dataBase.
        for (int itr = 0; itr < updateRecords.size(); itr++) {
            updateRecords.set(
                    itr,
                    getPowerRecord_update(
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
        readPowerRecordUsingIds(insertedRecords);
    }

    @Test
    public void testUpdateRecords_recordWithInvalidPackageName_noChangeInDataBase()
            throws InterruptedException {
        List<Record> insertedRecords =
                TestUtils.insertRecords(
                        Arrays.asList(getCompletePowerRecord(), getCompletePowerRecord()));

        // read inserted records and verify that the data is same as inserted.
        readPowerRecordUsingIds(insertedRecords);

        // Generate a second set of records that will be used to perform the update operation.
        List<Record> updateRecords =
                Arrays.asList(getCompletePowerRecord(), getCompletePowerRecord());

        // Modify the Uuid of the updateRecords to the uuid that was present in the insert records.
        for (int itr = 0; itr < updateRecords.size(); itr++) {
            updateRecords.set(
                    itr,
                    getPowerRecord_update(
                            updateRecords.get(itr),
                            insertedRecords.get(itr).getMetadata().getId(),
                            insertedRecords.get(itr).getMetadata().getClientRecordId()));
            //             adding an entry with invalid packageName.
            updateRecords.set(itr, getCompletePowerRecord());
        }

        try {
            TestUtils.updateRecords(updateRecords);
            Assert.fail("Expected to fail due to invalid package.");
        } catch (Exception exception) {
            // verify that the testcase failed due to invalid argument exception.
            assertThat(exception).isNotNull();
        }

        // assert the inserted data has not been modified by reading the data.
        readPowerRecordUsingIds(insertedRecords);
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
                                .addRecordType(PowerRecord.class)
                                .build());
        ChangeLogsRequest changeLogsRequest =
                new ChangeLogsRequest.Builder(tokenResponse.getToken()).build();
        ChangeLogsResponse response = TestUtils.getChangeLogs(changeLogsRequest);
        assertThat(response.getUpsertedRecords().size()).isEqualTo(0);
        assertThat(response.getDeletedLogs().size()).isEqualTo(0);

        List<Record> testRecord = Collections.singletonList(getCompletePowerRecord());
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
                new DeleteUsingFiltersRequest.Builder().addRecordType(PowerRecord.class).build());
        response = TestUtils.getChangeLogs(changeLogsRequest);
        assertThat(response.getDeletedLogs()).isEmpty();
    }

    private void testReadPowerRecordIds() throws InterruptedException {
        List<Record> recordList = Arrays.asList(getCompletePowerRecord(), getCompletePowerRecord());
        readPowerRecordUsingIds(recordList);
    }

    private void readPowerRecordUsingClientId(List<Record> insertedRecord)
            throws InterruptedException {
        ReadRecordsRequestUsingIds.Builder<PowerRecord> request =
                new ReadRecordsRequestUsingIds.Builder<>(PowerRecord.class);
        for (Record record : insertedRecord) {
            request.addClientRecordId(record.getMetadata().getClientRecordId());
        }
        List<PowerRecord> result = TestUtils.readRecords(request.build());
        assertThat(result.size()).isEqualTo(insertedRecord.size());
        assertThat(result).containsExactlyElementsIn(insertedRecord);
    }

    private void readPowerRecordUsingIds(List<Record> recordList) throws InterruptedException {
        List<Record> insertedRecords = TestUtils.insertRecords(recordList);
        ReadRecordsRequestUsingIds.Builder<PowerRecord> request =
                new ReadRecordsRequestUsingIds.Builder<>(PowerRecord.class);
        for (Record record : insertedRecords) {
            request.addId(record.getMetadata().getId());
        }
        ReadRecordsRequestUsingIds requestUsingIds = request.build();
        assertThat(requestUsingIds.getRecordType()).isEqualTo(PowerRecord.class);
        assertThat(requestUsingIds.getRecordIdFilters()).isNotNull();
        List<PowerRecord> result = TestUtils.readRecords(requestUsingIds);
        assertThat(result).hasSize(insertedRecords.size());
        assertThat(result.containsAll(insertedRecords)).isTrue();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCreatePowerRecord_invalidValue() {
        new PowerRecord.PowerRecordSample(Power.fromWatts(100001.0), Instant.now().plusMillis(100));
    }

    PowerRecord getPowerRecord_update(Record record, String id, String clientRecordId) {
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

        PowerRecord.PowerRecordSample powerRecordSample =
                new PowerRecord.PowerRecordSample(
                        Power.fromWatts(8.0), Instant.now().plusMillis(100));

        return new PowerRecord.Builder(
                        metadataWithId,
                        Instant.now(),
                        Instant.now().plusMillis(2000),
                        List.of(powerRecordSample, powerRecordSample))
                .setStartZoneOffset(ZoneOffset.systemDefault().getRules().getOffset(Instant.now()))
                .setEndZoneOffset(ZoneOffset.systemDefault().getRules().getOffset(Instant.now()))
                .build();
    }

    private static PowerRecord getBasePowerRecord() {
        PowerRecord.PowerRecordSample powerRecord =
                new PowerRecord.PowerRecordSample(
                        Power.fromWatts(10.0), Instant.now().plusMillis(100));
        ArrayList<PowerRecord.PowerRecordSample> powerRecords = new ArrayList<>();
        powerRecords.add(powerRecord);
        powerRecords.add(powerRecord);

        return new PowerRecord.Builder(
                        new Metadata.Builder().build(),
                        Instant.now(),
                        Instant.now().plusMillis(1000),
                        powerRecords)
                .build();
    }

    static PowerRecord getPowerRecord(double power) {
        PowerRecord.PowerRecordSample powerRecord =
                new PowerRecord.PowerRecordSample(
                        Power.fromWatts(power), Instant.now().plusMillis(100));
        ArrayList<PowerRecord.PowerRecordSample> powerRecords = new ArrayList<>();
        powerRecords.add(powerRecord);
        powerRecords.add(powerRecord);

        return new PowerRecord.Builder(
                        new Metadata.Builder().setClientRecordId("PR" + Math.random()).build(),
                        Instant.now(),
                        Instant.now().plusMillis(1000),
                        powerRecords)
                .build();
    }

    private static PowerRecord getCompletePowerRecord() {

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
        testMetadataBuilder.setClientRecordId("PR" + Math.random());
        testMetadataBuilder.setRecordingMethod(Metadata.RECORDING_METHOD_ACTIVELY_RECORDED);

        PowerRecord.PowerRecordSample powerRecord =
                new PowerRecord.PowerRecordSample(
                        Power.fromWatts(10.0), Instant.now().plusMillis(100));

        ArrayList<PowerRecord.PowerRecordSample> powerRecords = new ArrayList<>();
        powerRecords.add(powerRecord);
        powerRecords.add(powerRecord);

        return new PowerRecord.Builder(
                        testMetadataBuilder.build(),
                        Instant.now(),
                        Instant.now().plusMillis(1000),
                        powerRecords)
                .build();
    }
}
