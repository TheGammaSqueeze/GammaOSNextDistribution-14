/*
 * Copyright (C) 2024 The Android Open Source Project
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

import static android.health.connect.datatypes.SkinTemperatureRecord.MEASUREMENT_LOCATION_TOE;
import static android.health.connect.datatypes.SkinTemperatureRecord.SKIN_TEMPERATURE_DELTA_AVG;
import static android.health.connect.datatypes.SkinTemperatureRecord.SKIN_TEMPERATURE_DELTA_MAX;
import static android.health.connect.datatypes.SkinTemperatureRecord.SKIN_TEMPERATURE_DELTA_MIN;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.health.connect.AggregateRecordsGroupedByDurationResponse;
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
import android.health.connect.datatypes.SkinTemperatureRecord;
import android.health.connect.datatypes.units.Temperature;
import android.health.connect.datatypes.units.TemperatureDelta;
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

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

@AppModeFull(reason = "HealthConnectManager is not accessible to instant apps")
@RunWith(AndroidJUnit4.class)
public class SkinTemperatureRecordTest {
    private static final String PACKAGE_NAME = "android.healthconnect.cts";

    @Rule
    public AssumptionCheckerRule mSupportedHardwareRule =
            new AssumptionCheckerRule(
                    TestUtils::isHardwareSupported, "Tests should run on supported hardware only.");

    @After
    public void tearDown() throws InterruptedException {
        TestUtils.verifyDeleteRecords(
                SkinTemperatureRecord.class,
                new TimeInstantRangeFilter.Builder()
                        .setStartTime(Instant.EPOCH)
                        .setEndTime(Instant.now())
                        .build());
        TestUtils.deleteAllStagedRemoteData();
    }

    @Before
    public void setUp() throws InterruptedException {
        TestUtils.deleteAllStagedRemoteData();
    }

    @Test
    public void testSkinTemperatureRecordDelta_correctFields() {
        Instant recordTime = Instant.now();

        SkinTemperatureRecord.Delta delta =
                new SkinTemperatureRecord.Delta(TemperatureDelta.fromCelsius(0.5), recordTime);

        assertThat(delta.getDelta()).isEqualTo(TemperatureDelta.fromCelsius(0.5));
        assertThat(delta.getTime()).isEqualTo(recordTime);
    }

    @Test
    public void testSkinTemperatureRecordBuilder_correctFields() {
        Instant recordStartTime = Instant.now();
        Instant recordEndTime = recordStartTime.plusMillis(1000);

        SkinTemperatureRecord.Delta delta =
                new SkinTemperatureRecord.Delta(TemperatureDelta.fromCelsius(0.5), recordStartTime);

        SkinTemperatureRecord record =
                new SkinTemperatureRecord.Builder(
                                new Metadata.Builder().build(), recordStartTime, recordEndTime)
                        .setMeasurementLocation(MEASUREMENT_LOCATION_TOE)
                        .setBaseline(Temperature.fromCelsius(36.9))
                        .setStartZoneOffset(ZoneOffset.UTC)
                        .setEndZoneOffset(ZoneOffset.UTC)
                        .setDeltas(List.of(delta))
                        .build();

        assertThat(record.getDeltas()).isEqualTo(List.of(delta));
        assertThat(record.getBaseline()).isEqualTo(Temperature.fromCelsius(36.9));
        assertThat(record.getMeasurementLocation()).isEqualTo(MEASUREMENT_LOCATION_TOE);
        assertThat(record.getStartZoneOffset()).isEqualTo(ZoneOffset.UTC);
        assertThat(record.getEndZoneOffset()).isEqualTo(ZoneOffset.UTC);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCreateSkinTemperatureRecord_invalidBaseline() {
        getSkinTemperatureRecordWithBaseline(100000);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCreateSkinTemperatureRecord_invalidTimeOfTemperatureDelta() {
        Instant recordStartTime = Instant.now();
        Instant recordEndTime = recordStartTime.plusMillis(1000);

        // The measurement time of the delta is not within the record interval.
        SkinTemperatureRecord.Delta delta =
                new SkinTemperatureRecord.Delta(
                        TemperatureDelta.fromCelsius(0.5), recordEndTime.plusMillis(1000));

        new SkinTemperatureRecord.Builder(
                        getMetadataBuilder().build(), recordStartTime, recordEndTime)
                .setDeltas(List.of(delta))
                .build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCreateSkinTemperatureRecord_invalidMeasurementLocation() {
        SkinTemperatureRecord record = getSkinTemperatureRecord();

        // Wrong measurement location.
        new SkinTemperatureRecord.Builder(
                        record.getMetadata(), record.getStartTime(), record.getEndTime())
                .setDeltas(record.getDeltas())
                .setMeasurementLocation(234)
                .build();
    }

    @Test
    public void testInsertSkinTemperatureRecord() throws InterruptedException {
        List<SkinTemperatureRecord> records =
                Arrays.asList(getSkinTemperatureRecord(), getSkinTemperatureRecordWithBaseline(38));
        TestUtils.insertRecords(records);
    }

    @Test
    public void testReadSkinTemperatureRecord_usingIds() throws InterruptedException {
        List<Record> records =
                List.of(getSkinTemperatureRecord(), getSkinTemperatureRecordWithBaseline(38));
        List<Record> insertedRecords = TestUtils.insertRecords(records);

        ReadRecordsRequestUsingIds.Builder<SkinTemperatureRecord> request =
                new ReadRecordsRequestUsingIds.Builder<>(SkinTemperatureRecord.class);
        for (Record record : insertedRecords) {
            request.addId(record.getMetadata().getId());
        }
        ReadRecordsRequestUsingIds requestUsingIds = request.build();

        readSkinTemperatureRecordUsingIds(requestUsingIds, insertedRecords);
    }

    @Test
    public void testReadSkinTemperatureRecord_invalidIds() throws InterruptedException {
        List<SkinTemperatureRecord> records =
                Arrays.asList(getSkinTemperatureRecord(), getSkinTemperatureRecordWithBaseline(38));
        List<Record> insertedRecords = TestUtils.insertRecords(records);

        ReadRecordsRequestUsingIds<SkinTemperatureRecord> request =
                new ReadRecordsRequestUsingIds.Builder<>(SkinTemperatureRecord.class)
                        .addId(UUID.randomUUID().toString())
                        .build();
        List<SkinTemperatureRecord> result = TestUtils.readRecords(request);

        assertThat(insertedRecords.size()).isNotEqualTo(0);
        assertThat(result.size()).isEqualTo(0);
    }

    @Test
    public void testReadSkinTemperatureRecord_usingClientRecordIds() throws InterruptedException {
        List<Record> recordList =
                Arrays.asList(getSkinTemperatureRecord(), getSkinTemperatureRecordWithBaseline(37));
        List<Record> insertedRecords = TestUtils.insertRecords(recordList);

        ReadRecordsRequestUsingIds.Builder<SkinTemperatureRecord> request =
                new ReadRecordsRequestUsingIds.Builder<>(SkinTemperatureRecord.class);
        for (Record record : insertedRecords) {
            request.addClientRecordId(record.getMetadata().getClientRecordId());
        }
        List<SkinTemperatureRecord> result = TestUtils.readRecords(request.build());

        assertThat(result.size()).isEqualTo(insertedRecords.size());
        assertThat(result).containsExactlyElementsIn(insertedRecords);
    }

    @Test
    public void testReadSkinTemperatureRecord_invalidClientRecordIds() throws InterruptedException {
        List<Record> recordList =
                Arrays.asList(getSkinTemperatureRecord(), getSkinTemperatureRecordWithBaseline(37));
        List<Record> insertedRecords = TestUtils.insertRecords(recordList);
        ReadRecordsRequestUsingIds<SkinTemperatureRecord> request =
                new ReadRecordsRequestUsingIds.Builder<>(SkinTemperatureRecord.class)
                        .addClientRecordId("abc")
                        .build();
        List<SkinTemperatureRecord> result = TestUtils.readRecords(request);

        assertThat(insertedRecords.size()).isNotEqualTo(0);
        assertThat(result.size()).isEqualTo(0);
    }

    @Test
    public void testReadSkinTemperatureRecordUsingFilters_default() throws InterruptedException {
        List<SkinTemperatureRecord> oldSkinTemperatureRecords =
                TestUtils.readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(SkinTemperatureRecord.class)
                                .build());
        SkinTemperatureRecord testRecord = getSkinTemperatureRecord();
        TestUtils.insertRecords(Collections.singletonList(testRecord));

        List<SkinTemperatureRecord> newSkinTemperatureRecords =
                TestUtils.readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(SkinTemperatureRecord.class)
                                .build());

        assertThat(newSkinTemperatureRecords.size())
                .isEqualTo(oldSkinTemperatureRecords.size() + 1);
        assertThat(
                        newSkinTemperatureRecords
                                .get(newSkinTemperatureRecords.size() - 1)
                                .equals(testRecord))
                .isTrue();
    }

    @Test
    public void testReadSkinTemperatureRecordUsingFilters_timeFilter() throws InterruptedException {
        TimeInstantRangeFilter filter =
                new TimeInstantRangeFilter.Builder()
                        .setStartTime(Instant.now())
                        .setEndTime(Instant.now().plusMillis(3000))
                        .build();
        SkinTemperatureRecord testRecord = getSkinTemperatureRecordWithBaseline(38);
        TestUtils.insertRecords(Collections.singletonList(testRecord));

        List<SkinTemperatureRecord> newSkinTemperatureRecords =
                TestUtils.readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(SkinTemperatureRecord.class)
                                .setTimeRangeFilter(filter)
                                .build());

        assertThat(
                        newSkinTemperatureRecords
                                .get(newSkinTemperatureRecords.size() - 1)
                                .equals(testRecord))
                .isTrue();
    }

    @Test
    public void testReadSkinTemperatureRecordUsingFilters_dataFilter_correct()
            throws InterruptedException {
        Context context = ApplicationProvider.getApplicationContext();
        List<SkinTemperatureRecord> oldSkinTemperatureRecords =
                TestUtils.readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(SkinTemperatureRecord.class)
                                .addDataOrigins(
                                        new DataOrigin.Builder()
                                                .setPackageName(context.getPackageName())
                                                .build())
                                .build());
        SkinTemperatureRecord testRecord = getSkinTemperatureRecordWithBaseline(38);
        TestUtils.insertRecords(Collections.singletonList(testRecord));
        List<SkinTemperatureRecord> newSkinTemperatureRecords =
                TestUtils.readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(SkinTemperatureRecord.class)
                                .addDataOrigins(
                                        new DataOrigin.Builder()
                                                .setPackageName(context.getPackageName())
                                                .build())
                                .build());
        assertThat(newSkinTemperatureRecords.size() - oldSkinTemperatureRecords.size())
                .isEqualTo(1);
        assertThat(
                        newSkinTemperatureRecords
                                .get(newSkinTemperatureRecords.size() - 1)
                                .equals(testRecord))
                .isTrue();
        SkinTemperatureRecord newRecord =
                newSkinTemperatureRecords.get(newSkinTemperatureRecords.size() - 1);
        assertThat(newRecord.equals(testRecord)).isTrue();

        for (int idx = 0; idx < newRecord.getDeltas().size(); idx++) {
            assertThat(newRecord.getDeltas().get(idx).getTime().toEpochMilli())
                    .isEqualTo(testRecord.getDeltas().get(idx).getTime().toEpochMilli());
            assertThat(newRecord.getDeltas().get(idx).getDelta())
                    .isEqualTo(testRecord.getDeltas().get(idx).getDelta());
        }
    }

    @Test
    public void testReadSkinTemperatureUsingFilters_dataFilter_incorrect()
            throws InterruptedException {
        TestUtils.insertRecords(
                Collections.singletonList(getSkinTemperatureRecordWithBaseline(38)));
        List<SkinTemperatureRecord> newSkinTemperatureRecords =
                TestUtils.readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(SkinTemperatureRecord.class)
                                .addDataOrigins(
                                        new DataOrigin.Builder().setPackageName("abc").build())
                                .build());
        assertThat(newSkinTemperatureRecords.size()).isEqualTo(0);
    }

    @Test
    public void testDeleteSkinTemperatureRecord_noFilters() throws InterruptedException {
        String id = TestUtils.insertRecordAndGetId(getSkinTemperatureRecordWithBaseline(38));
        TestUtils.verifyDeleteRecords(new DeleteUsingFiltersRequest.Builder().build());
        TestUtils.assertRecordNotFound(id, SkinTemperatureRecord.class);
    }

    @Test
    public void testDeleteSkinTemperatureRecord_timeFilters() throws InterruptedException {
        TimeInstantRangeFilter timeRangeFilter =
                new TimeInstantRangeFilter.Builder()
                        .setStartTime(Instant.now())
                        .setEndTime(Instant.now().plusMillis(1000))
                        .build();
        String id = TestUtils.insertRecordAndGetId(getSkinTemperatureRecordWithBaseline(38));

        TestUtils.verifyDeleteRecords(
                new DeleteUsingFiltersRequest.Builder()
                        .addRecordType(SkinTemperatureRecord.class)
                        .setTimeRangeFilter(timeRangeFilter)
                        .build());

        TestUtils.assertRecordNotFound(id, SkinTemperatureRecord.class);
    }

    @Test
    public void testDeleteSkinTemperatureRecord_recordIdFilters() throws InterruptedException {
        List<Record> records =
                List.of(getSkinTemperatureRecordWithBaseline(37.5), getSkinTemperatureRecord());
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
    public void testDeleteSkinTemperatureRecord_dataOriginFilters() throws InterruptedException {
        Context context = ApplicationProvider.getApplicationContext();
        String id = TestUtils.insertRecordAndGetId(getSkinTemperatureRecord());
        TestUtils.verifyDeleteRecords(
                new DeleteUsingFiltersRequest.Builder()
                        .addDataOrigin(
                                new DataOrigin.Builder()
                                        .setPackageName(context.getPackageName())
                                        .build())
                        .build());
        TestUtils.assertRecordNotFound(id, SkinTemperatureRecord.class);
    }

    @Test
    public void testDeleteSkinTemperatureRecord_dataOriginFilter_incorrect()
            throws InterruptedException {
        String id = TestUtils.insertRecordAndGetId(getSkinTemperatureRecordWithBaseline(37.1));
        TestUtils.verifyDeleteRecords(
                new DeleteUsingFiltersRequest.Builder()
                        .addDataOrigin(new DataOrigin.Builder().setPackageName("abc").build())
                        .build());
        TestUtils.assertRecordFound(id, SkinTemperatureRecord.class);
    }

    @Test
    public void testDeleteSkinTemperatureRecord_usingIds() throws InterruptedException {
        List<Record> records =
                List.of(getSkinTemperatureRecordWithBaseline(37.2), getSkinTemperatureRecord());
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
    public void testDeleteSkinTemperatureRecord_timeRange() throws InterruptedException {
        TimeInstantRangeFilter timeRangeFilter =
                new TimeInstantRangeFilter.Builder()
                        .setStartTime(Instant.now())
                        .setEndTime(Instant.now().plusMillis(1000))
                        .build();
        String id = TestUtils.insertRecordAndGetId(getSkinTemperatureRecord());
        TestUtils.verifyDeleteRecords(SkinTemperatureRecord.class, timeRangeFilter);
        TestUtils.assertRecordNotFound(id, SkinTemperatureRecord.class);
    }

    @Test
    public void testSkinTemperatureRecord_checkingZoneOffsets() {
        final ZoneOffset defaultZoneOffset =
                ZoneOffset.systemDefault().getRules().getOffset(Instant.now());
        final ZoneOffset startZoneOffset = ZoneOffset.UTC;
        final ZoneOffset endZoneOffset = ZoneOffset.MAX;
        SkinTemperatureRecord.Builder builder =
                new SkinTemperatureRecord.Builder(
                        new Metadata.Builder().build(),
                        Instant.now(),
                        Instant.now().plusMillis(1000));

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
    public void testUpdateRecords_validInput_databaseUpdatedSuccessfully()
            throws InterruptedException {

        List<Record> insertedRecords =
                TestUtils.insertRecords(
                        List.of(
                                getSkinTemperatureRecord(),
                                getSkinTemperatureRecordWithBaseline(37.4)));

        ReadRecordsRequestUsingIds.Builder<SkinTemperatureRecord> request =
                new ReadRecordsRequestUsingIds.Builder<>(SkinTemperatureRecord.class);
        for (Record record : insertedRecords) {
            request.addId(record.getMetadata().getId());
        }
        ReadRecordsRequestUsingIds requestUsingIds = request.build();

        // Read inserted records and verify that the data is same as inserted.
        readSkinTemperatureRecordUsingIds(requestUsingIds, insertedRecords);

        // Generate a new set of records that will be used to perform the update operation.
        List<Record> updateRecords =
                Arrays.asList(getSkinTemperatureRecord(), getSkinTemperatureRecord());

        // Modify the uid of the updateRecords to the uuid that was present in the insert records
        for (int itr = 0; itr < updateRecords.size(); itr++) {
            updateRecords.set(
                    itr,
                    getSkinTemperatureRecordUpdate(
                            updateRecords.get(itr),
                            insertedRecords.get(itr).getMetadata().getId(),
                            insertedRecords.get(itr).getMetadata().getClientRecordId()));
        }

        TestUtils.updateRecords(updateRecords);

        // Assert the inserted data has been modified by reading the data.
        readSkinTemperatureRecordUsingIds(requestUsingIds, updateRecords);
    }

    @Test
    public void testUpdateRecords_invalidInputRecords_noChangeInDatabase()
            throws InterruptedException {
        List<Record> insertedRecords =
                TestUtils.insertRecords(
                        Arrays.asList(
                                getSkinTemperatureRecordWithBaseline(36.99),
                                getSkinTemperatureRecord()));
        ReadRecordsRequestUsingIds.Builder<SkinTemperatureRecord> request =
                new ReadRecordsRequestUsingIds.Builder<>(SkinTemperatureRecord.class);
        for (Record record : insertedRecords) {
            request.addId(record.getMetadata().getId());
        }
        ReadRecordsRequestUsingIds requestUsingIds = request.build();

        // Read inserted records and verify that the data is same as inserted.
        readSkinTemperatureRecordUsingIds(requestUsingIds, insertedRecords);

        // Generate a second set of records that will be used to perform the update operation.
        List<Record> updateRecords =
                Arrays.asList(getSkinTemperatureRecord(), getSkinTemperatureRecord());

        // Modify the Uid of the updateRecords to the UUID that was present in the insert records,
        // leaving out alternate records so that they have a new UUID which is not present in the
        // database.
        for (int itr = 0; itr < updateRecords.size(); itr++) {
            updateRecords.set(
                    itr,
                    getSkinTemperatureRecordUpdate(
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

        // Assert the inserted data has not been modified by reading the data.
        readSkinTemperatureRecordUsingIds(requestUsingIds, insertedRecords);
    }

    @Test
    public void testUpdateRecords_recordWithInvalidPackageName_noChangeInDataBase()
            throws InterruptedException {
        List<Record> insertedRecords =
                TestUtils.insertRecords(
                        Arrays.asList(
                                getSkinTemperatureRecordWithBaseline(35.9),
                                getSkinTemperatureRecord()));
        ReadRecordsRequestUsingIds.Builder<SkinTemperatureRecord> request =
                new ReadRecordsRequestUsingIds.Builder<>(SkinTemperatureRecord.class);
        for (Record record : insertedRecords) {
            request.addId(record.getMetadata().getId());
        }
        ReadRecordsRequestUsingIds requestUsingIds = request.build();

        // Read inserted records and verify that the data is same as inserted.
        readSkinTemperatureRecordUsingIds(requestUsingIds, insertedRecords);

        // Generate a second set of records that will be used to perform the update operation.
        List<Record> updateRecords =
                Arrays.asList(getSkinTemperatureRecord(), getSkinTemperatureRecord());

        // Modify the Uuid of the updateRecords to the uuid that was present in the insert records.
        for (int itr = 0; itr < updateRecords.size(); itr++) {
            updateRecords.set(
                    itr,
                    getSkinTemperatureRecordUpdate(
                            updateRecords.get(itr),
                            insertedRecords.get(itr).getMetadata().getId(),
                            insertedRecords.get(itr).getMetadata().getClientRecordId()));
            // Add an entry with invalid packageName.
            updateRecords.set(itr, getSkinTemperatureRecord());
        }

        try {
            TestUtils.updateRecords(updateRecords);
            Assert.fail("Expected to fail due to invalid package.");
        } catch (Exception exception) {
            // Verify that the testcase failed due to invalid argument exception.
            assertThat(exception).isNotNull();
        }

        // Assert the inserted data has not been modified by reading the data.
        readSkinTemperatureRecordUsingIds(requestUsingIds, insertedRecords);
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
                                .addRecordType(SkinTemperatureRecord.class)
                                .build());
        ChangeLogsRequest changeLogsRequest =
                new ChangeLogsRequest.Builder(tokenResponse.getToken()).build();
        ChangeLogsResponse response = TestUtils.getChangeLogs(changeLogsRequest);
        assertThat(response.getUpsertedRecords().size()).isEqualTo(0);
        assertThat(response.getDeletedLogs().size()).isEqualTo(0);

        List<Record> testRecord = Collections.singletonList(getSkinTemperatureRecord());
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
                        .addRecordType(SkinTemperatureRecord.class)
                        .build());
        response = TestUtils.getChangeLogs(changeLogsRequest);
        assertThat(response.getDeletedLogs()).isEmpty();
    }

    @Test
    public void testAggregation_allTimeRange_correctResults() throws Exception {
        TestUtils.setupAggregation(PACKAGE_NAME, HealthDataCategory.VITALS);

        List<Record> records =
                List.of(
                        getSkinTemperatureRecordWithDeltas(0.55, -0.55),
                        getSkinTemperatureRecordWithDeltas(0.1, 0.3));

        TestUtils.insertRecords(records);

        AggregateRecordsResponse<TemperatureDelta> response =
                TestUtils.getAggregateResponse(
                        new AggregateRecordsRequest.Builder<TemperatureDelta>(
                                        new TimeInstantRangeFilter.Builder()
                                                .setStartTime(Instant.ofEpochMilli(0))
                                                .setEndTime(Instant.now().plus(1, ChronoUnit.DAYS))
                                                .build())
                                .addAggregationType(SKIN_TEMPERATURE_DELTA_MIN)
                                .addAggregationType(SKIN_TEMPERATURE_DELTA_MAX)
                                .addAggregationType(SKIN_TEMPERATURE_DELTA_AVG)
                                .addDataOriginsFilter(
                                        new DataOrigin.Builder()
                                                .setPackageName(
                                                        ApplicationProvider.getApplicationContext()
                                                                .getPackageName())
                                                .build())
                                .build(),
                        records);

        assertThat(response.get(SKIN_TEMPERATURE_DELTA_MIN).getInCelsius()).isEqualTo(-0.55);
        assertThat(response.get(SKIN_TEMPERATURE_DELTA_MAX).getInCelsius()).isEqualTo(0.55);
        assertThat(response.get(SKIN_TEMPERATURE_DELTA_AVG).getInCelsius()).isEqualTo(0.1);
    }

    @Test
    public void testAggregation_nonOverlappedTimeRange_noResults() throws Exception {
        TestUtils.setupAggregation(PACKAGE_NAME, HealthDataCategory.VITALS);

        List<Record> records =
                List.of(
                        getSkinTemperatureRecordWithDeltas(0.55, -0.55),
                        getSkinTemperatureRecordWithDeltas(0.22, -0.22));

        TestUtils.insertRecords(records);

        AggregateRecordsResponse<TemperatureDelta> response =
                TestUtils.getAggregateResponse(
                        new AggregateRecordsRequest.Builder<TemperatureDelta>(
                                        // Inserted records are not within this time range.
                                        new TimeInstantRangeFilter.Builder()
                                                .setStartTime(Instant.now().plusMillis(1000))
                                                .setEndTime(Instant.now().plusMillis(2000))
                                                .build())
                                .addAggregationType(SKIN_TEMPERATURE_DELTA_MIN)
                                .addAggregationType(SKIN_TEMPERATURE_DELTA_MAX)
                                .addAggregationType(SKIN_TEMPERATURE_DELTA_AVG)
                                .addDataOriginsFilter(
                                        new DataOrigin.Builder()
                                                .setPackageName(
                                                        ApplicationProvider.getApplicationContext()
                                                                .getPackageName())
                                                .build())
                                .build(),
                        records);

        assertThat(response.get(SKIN_TEMPERATURE_DELTA_MIN)).isNull();
        assertThat(response.get(SKIN_TEMPERATURE_DELTA_MAX)).isNull();
        assertThat(response.get(SKIN_TEMPERATURE_DELTA_AVG)).isNull();
    }

    @Test
    public void testAggregation_groupByDuration_correctResults() throws Exception {
        TestUtils.setupAggregation(PACKAGE_NAME, HealthDataCategory.VITALS);
        Instant recordStartTime = Instant.now();
        // The inserted records are only within the first 1 second.
        List<Record> records =
                List.of(getSkinTemperatureRecordWithDeltas(recordStartTime, 0.55, -0.55));
        TestUtils.insertRecords(records);

        List<AggregateRecordsGroupedByDurationResponse<TemperatureDelta>> responses =
                TestUtils.getAggregateResponseGroupByDuration(
                        new AggregateRecordsRequest.Builder<TemperatureDelta>(
                                        // The retrieved time range is 2 seconds.
                                        new TimeInstantRangeFilter.Builder()
                                                .setStartTime(recordStartTime)
                                                .setEndTime(recordStartTime.plusMillis(2000))
                                                .build())
                                .addAggregationType(SKIN_TEMPERATURE_DELTA_MIN)
                                .addAggregationType(SKIN_TEMPERATURE_DELTA_MAX)
                                .addAggregationType(SKIN_TEMPERATURE_DELTA_AVG)
                                .addDataOriginsFilter(
                                        new DataOrigin.Builder()
                                                .setPackageName(
                                                        ApplicationProvider.getApplicationContext()
                                                                .getPackageName())
                                                .build())
                                .build(),
                        Duration.ofSeconds(1));

        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).get(SKIN_TEMPERATURE_DELTA_MIN).getInCelsius())
                .isEqualTo(-0.55);
        assertThat(responses.get(0).get(SKIN_TEMPERATURE_DELTA_MAX).getInCelsius()).isEqualTo(0.55);
        assertThat(responses.get(0).get(SKIN_TEMPERATURE_DELTA_AVG).getInCelsius()).isEqualTo(0);
        assertThat(responses.get(1).get(SKIN_TEMPERATURE_DELTA_MIN)).isEqualTo(null);
        assertThat(responses.get(1).get(SKIN_TEMPERATURE_DELTA_MAX)).isEqualTo(null);
        assertThat(responses.get(1).get(SKIN_TEMPERATURE_DELTA_AVG)).isEqualTo(null);
    }

    private void readSkinTemperatureRecordUsingIds(
            ReadRecordsRequestUsingIds requestUsingIds, List<Record> insertedRecords)
            throws InterruptedException {
        assertThat(requestUsingIds.getRecordType()).isEqualTo(SkinTemperatureRecord.class);
        assertThat(requestUsingIds.getRecordIdFilters()).isNotNull();
        List<SkinTemperatureRecord> result = TestUtils.readRecords(requestUsingIds);

        assertThat(result).hasSize(insertedRecords.size());
        assertThat(result.containsAll(insertedRecords)).isTrue();
    }

    private SkinTemperatureRecord getSkinTemperatureRecordUpdate(
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

        SkinTemperatureRecord.Delta deltaA =
                new SkinTemperatureRecord.Delta(
                        TemperatureDelta.fromCelsius(0.22), Instant.now().plusMillis(100));

        SkinTemperatureRecord.Delta deltaB =
                new SkinTemperatureRecord.Delta(
                        TemperatureDelta.fromCelsius(-0.22), Instant.now().plusMillis(100));

        return new SkinTemperatureRecord.Builder(
                        metadataWithId, Instant.now(), Instant.now().plusMillis(2000))
                .setDeltas(List.of(deltaA, deltaB))
                .setStartZoneOffset(ZoneOffset.systemDefault().getRules().getOffset(Instant.now()))
                .setEndZoneOffset(ZoneOffset.systemDefault().getRules().getOffset(Instant.now()))
                .build();
    }

    private static SkinTemperatureRecord getSkinTemperatureRecord() {
        // Used in case that the values of deltas do not matter for the test results.
        return getSkinTemperatureRecordWithDeltas(0.5, -0.5);
    }

    private static SkinTemperatureRecord getSkinTemperatureRecordWithDeltas(
            double... deltaDoubles) {
        // Used in case that the time of record does not matter.
        return getSkinTemperatureRecordWithDeltas(Instant.now(), deltaDoubles);
    }

    private static SkinTemperatureRecord getSkinTemperatureRecordWithDeltas(
            Instant recordStartTime, double... deltaDoubles) {
        List<SkinTemperatureRecord.Delta> deltas =
                IntStream.range(0, deltaDoubles.length)
                        .mapToObj(
                                idx ->
                                        new SkinTemperatureRecord.Delta(
                                                TemperatureDelta.fromCelsius(deltaDoubles[idx]),
                                                recordStartTime.plusMillis(idx * 100)))
                        .toList();
        SkinTemperatureRecord record =
                new SkinTemperatureRecord.Builder(
                                getMetadataBuilder().build(),
                                recordStartTime,
                                recordStartTime.plusMillis(1000))
                        .setDeltas(deltas)
                        .build();
        return record;
    }

    private static Metadata.Builder getMetadataBuilder() {
        Device device =
                new Device.Builder()
                        .setManufacturer("google")
                        .setModel("Pixel4a")
                        .setType(2)
                        .build();
        DataOrigin dataOrigin = new DataOrigin.Builder().setPackageName(PACKAGE_NAME).build();
        Metadata.Builder testMetadataBuilder = new Metadata.Builder();
        testMetadataBuilder.setDevice(device).setDataOrigin(dataOrigin);
        testMetadataBuilder.setClientRecordId("CPCR" + Math.random());
        testMetadataBuilder.setRecordingMethod(Metadata.RECORDING_METHOD_ACTIVELY_RECORDED);
        return testMetadataBuilder;
    }

    private static SkinTemperatureRecord getSkinTemperatureRecordWithBaseline(double baseline) {
        SkinTemperatureRecord record = getSkinTemperatureRecord();
        return new SkinTemperatureRecord.Builder(
                        record.getMetadata(), record.getStartTime(), record.getEndTime())
                .setDeltas(record.getDeltas())
                .setBaseline(Temperature.fromCelsius(baseline))
                .setMeasurementLocation(0)
                .build();
    }
}
