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

import static android.healthconnect.cts.utils.DataFactory.SESSION_END_TIME;
import static android.healthconnect.cts.utils.DataFactory.SESSION_START_TIME;
import static android.healthconnect.cts.utils.DataFactory.buildSleepSession;
import static android.healthconnect.cts.utils.DataFactory.generateMetadata;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.fail;

import android.content.Context;
import android.health.connect.DeleteUsingFiltersRequest;
import android.health.connect.HealthConnectException;
import android.health.connect.ReadRecordsRequestUsingFilters;
import android.health.connect.ReadRecordsRequestUsingIds;
import android.health.connect.TimeInstantRangeFilter;
import android.health.connect.changelog.ChangeLogTokenRequest;
import android.health.connect.changelog.ChangeLogTokenResponse;
import android.health.connect.changelog.ChangeLogsRequest;
import android.health.connect.changelog.ChangeLogsResponse;
import android.health.connect.datatypes.DataOrigin;
import android.health.connect.datatypes.Metadata;
import android.health.connect.datatypes.Record;
import android.health.connect.datatypes.SleepSessionRecord;
import android.healthconnect.cts.utils.AssumptionCheckerRule;
import android.healthconnect.cts.utils.TestUtils;

import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class SleepSessionRecordTest {
    private static final Instant START_TIME = Instant.ofEpochMilli((long) 1e10);
    private static final Instant INTERMEDIATE_TIME = Instant.ofEpochMilli((long) 1e10 + 300);
    private static final Instant INTERMEDIATE_TIME2 = Instant.ofEpochMilli((long) 1e10 + 700);
    private static final Instant END_TIME = Instant.ofEpochMilli((long) 1e10 + 1000);
    private static final CharSequence NOTES = "felt sleepy";
    private static final CharSequence TITLE = "Afternoon nap";

    @Rule
    public AssumptionCheckerRule mSupportedHardwareRule =
            new AssumptionCheckerRule(
                    TestUtils::isHardwareSupported, "Tests should run on supported hardware only.");

    @After
    public void tearDown() throws InterruptedException {
        TestUtils.verifyDeleteRecords(
                SleepSessionRecord.class,
                new TimeInstantRangeFilter.Builder()
                        .setStartTime(Instant.EPOCH)
                        .setEndTime(Instant.now())
                        .build());
        TestUtils.deleteAllStagedRemoteData();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSleepStage_startTimeLaterThanEnd_throwsException() {
        new SleepSessionRecord.Stage(
                END_TIME, START_TIME, SleepSessionRecord.StageType.STAGE_TYPE_AWAKE);
        fail("Must throw exception if sleep stage start time is after end time.");
    }

    @Test
    public void testSleepStage_buildStage_equalsIsCorrect() {
        SleepSessionRecord.Stage stage1 =
                new SleepSessionRecord.Stage(
                        START_TIME, END_TIME, SleepSessionRecord.StageType.STAGE_TYPE_AWAKE);
        SleepSessionRecord.Stage stage2 =
                new SleepSessionRecord.Stage(
                        START_TIME, END_TIME, SleepSessionRecord.StageType.STAGE_TYPE_AWAKE);
        assertThat(stage1).isEqualTo(stage2);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSleepSession_sleepStageEndTimeIllegal_throwsException() {
        new SleepSessionRecord.Builder(generateMetadata(), START_TIME, INTERMEDIATE_TIME)
                .setStages(
                        List.of(
                                new SleepSessionRecord.Stage(
                                        START_TIME,
                                        END_TIME,
                                        SleepSessionRecord.StageType.STAGE_TYPE_SLEEPING_LIGHT)))
                .build();
        fail("Must throw an exception if sleep stage end time is later than session end time.");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSleepSession_stagesOverlap_throwsException() {
        new SleepSessionRecord.Builder(generateMetadata(), START_TIME, END_TIME)
                .setStages(
                        List.of(
                                new SleepSessionRecord.Stage(
                                        START_TIME,
                                        INTERMEDIATE_TIME2,
                                        SleepSessionRecord.StageType.STAGE_TYPE_SLEEPING_LIGHT),
                                new SleepSessionRecord.Stage(
                                        INTERMEDIATE_TIME,
                                        END_TIME,
                                        SleepSessionRecord.StageType.STAGE_TYPE_SLEEPING_LIGHT)))
                .build();
        fail("Must throw an exception if sleep stages overlap.");
    }

    @Test
    public void testSleepSession_buildStage_gettersAreCorrect() {
        SleepSessionRecord.Stage stage1 =
                new SleepSessionRecord.Stage(
                        START_TIME, END_TIME, SleepSessionRecord.StageType.STAGE_TYPE_AWAKE);
        assertThat(stage1.getType()).isEqualTo(SleepSessionRecord.StageType.STAGE_TYPE_AWAKE);
        assertThat(stage1.getStartTime()).isEqualTo(START_TIME);
        assertThat(stage1.getEndTime()).isEqualTo(END_TIME);
    }

    @Test
    public void testSleepSession_setsStagesOneByOne_stagesEqualsToLastSet() {
        SleepSessionRecord.Stage stage1 =
                new SleepSessionRecord.Stage(
                        START_TIME,
                        INTERMEDIATE_TIME,
                        SleepSessionRecord.StageType.STAGE_TYPE_AWAKE);
        SleepSessionRecord.Stage stage2 =
                new SleepSessionRecord.Stage(
                        INTERMEDIATE_TIME,
                        END_TIME,
                        SleepSessionRecord.StageType.STAGE_TYPE_SLEEPING_DEEP);
        SleepSessionRecord record =
                new SleepSessionRecord.Builder(generateMetadata(), START_TIME, END_TIME)
                        .setStages(List.of(stage1))
                        .setStages(List.of(stage2))
                        .build();
        assertThat(record.getStages()).isEqualTo(List.of(stage2));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSleepSession_sleepStageStartTimeIllegal_throwsException() {
        new SleepSessionRecord.Builder(generateMetadata(), INTERMEDIATE_TIME, END_TIME)
                .setStages(
                        List.of(
                                new SleepSessionRecord.Stage(
                                        START_TIME,
                                        END_TIME,
                                        SleepSessionRecord.StageType.STAGE_TYPE_SLEEPING_LIGHT)))
                .build();
        fail(
                "Must throw an exception if sleep stage start time is earlier than session start"
                        + " time.");
    }

    @Test
    public void testSleepSession_buildSession_buildsCorrectObject() {
        SleepSessionRecord record =
                new SleepSessionRecord.Builder(generateMetadata(), START_TIME, END_TIME)
                        .setStages(
                                List.of(
                                        new SleepSessionRecord.Stage(
                                                START_TIME,
                                                INTERMEDIATE_TIME,
                                                SleepSessionRecord.StageType
                                                        .STAGE_TYPE_SLEEPING_LIGHT),
                                        new SleepSessionRecord.Stage(
                                                INTERMEDIATE_TIME,
                                                END_TIME,
                                                SleepSessionRecord.StageType
                                                        .STAGE_TYPE_SLEEPING_DEEP)))
                        .setNotes(NOTES)
                        .setTitle(TITLE)
                        .build();
        assertThat(record.getStartTime()).isEqualTo(START_TIME);
        assertThat(record.getEndTime()).isEqualTo(END_TIME);
        assertThat(record.getStages()).hasSize(2);
        assertThat(CharSequence.compare(record.getTitle(), TITLE)).isEqualTo(0);
        assertThat(CharSequence.compare(record.getNotes(), NOTES)).isEqualTo(0);
    }

    @Test
    public void testZoneOffsets() {
        final ZoneOffset defaultZoneOffset =
                ZoneOffset.systemDefault().getRules().getOffset(Instant.now());
        final ZoneOffset startZoneOffset = ZoneOffset.UTC;
        final ZoneOffset endZoneOffset = ZoneOffset.MAX;
        SleepSessionRecord.Builder builder =
                new SleepSessionRecord.Builder(
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
    public void testReadById_insertAndReadByIdOne_recordsAreEqual() throws InterruptedException {
        List<Record> records = List.of(buildSleepSession());
        TestUtils.insertRecords(records);

        ReadRecordsRequestUsingIds.Builder<SleepSessionRecord> request =
                new ReadRecordsRequestUsingIds.Builder<>(SleepSessionRecord.class);
        request.addId(records.get(0).getMetadata().getId());

        SleepSessionRecord readRecord = TestUtils.readRecords(request.build()).get(0);
        SleepSessionRecord insertedRecord = (SleepSessionRecord) records.get(0);
        assertThat(readRecord.getMetadata()).isEqualTo(insertedRecord.getMetadata());
        assertThat(CharSequence.compare(readRecord.getTitle(), insertedRecord.getTitle()))
                .isEqualTo(0);
        assertThat(CharSequence.compare(readRecord.getNotes(), insertedRecord.getNotes()))
                .isEqualTo(0);
        assertThat(readRecord.getStages()).isEqualTo(insertedRecord.getStages());
        assertThat(readRecord).isEqualTo(insertedRecord);
    }

    @Test
    public void testReadById_insertAndReadById_recordsAreEqual() throws InterruptedException {
        List<Record> records = List.of(buildSleepSession(), buildSleepSessionMinimal());
        TestUtils.insertRecords(records);

        ReadRecordsRequestUsingIds.Builder<SleepSessionRecord> request =
                new ReadRecordsRequestUsingIds.Builder<>(SleepSessionRecord.class);
        request.addId(records.get(0).getMetadata().getId());
        request.addId(records.get(1).getMetadata().getId());

        assertRecordsAreEqual(records, TestUtils.readRecords(request.build()));
    }

    @Test
    public void testReadByClientId_insertAndReadByClientId_recordsAreEqual()
            throws InterruptedException {
        List<Record> records = List.of(buildSleepSession(), buildSleepSessionMinimal());
        TestUtils.insertRecords(records);

        ReadRecordsRequestUsingIds.Builder<SleepSessionRecord> request =
                new ReadRecordsRequestUsingIds.Builder<>(SleepSessionRecord.class);
        request.addClientRecordId(records.get(0).getMetadata().getClientRecordId());
        request.addClientRecordId(records.get(1).getMetadata().getClientRecordId());

        assertRecordsAreEqual(records, TestUtils.readRecords(request.build()));
    }

    @Test
    public void testReadByClientId_insertAndReadByDefaultFilter_filteredAll()
            throws InterruptedException {
        List<Record> records = List.of(buildSleepSession(), buildSleepSessionMinimal());
        TestUtils.insertRecords(records);

        List<SleepSessionRecord> readRecords =
                TestUtils.readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(SleepSessionRecord.class)
                                .build());
        assertRecordsAreEqual(records, readRecords);
    }

    @Test
    public void testDeleteRecords_insertAndDeleteById_recordsNotFoundAnymore()
            throws InterruptedException {
        List<Record> records = List.of(buildSleepSession(), buildSleepSessionMinimal());
        List<Record> insertedRecords = TestUtils.insertRecords(records);

        TestUtils.assertRecordFound(records.get(0).getMetadata().getId(), SleepSessionRecord.class);
        TestUtils.assertRecordFound(records.get(1).getMetadata().getId(), SleepSessionRecord.class);

        TestUtils.deleteRecords(insertedRecords);

        TestUtils.assertRecordNotFound(
                records.get(0).getMetadata().getId(), SleepSessionRecord.class);
        TestUtils.assertRecordNotFound(
                records.get(1).getMetadata().getId(), SleepSessionRecord.class);
    }

    @Test
    public void testUpdateRecords_validInput_dataBaseUpdatedSuccessfully()
            throws InterruptedException {

        List<Record> insertedRecords =
                TestUtils.insertRecords(Arrays.asList(buildSleepSession(), buildSleepSession()));

        // read inserted records and verify that the data is same as inserted.
        readAndAssertEquals(insertedRecords);

        // Generate a new set of records that will be used to perform the update operation.
        List<Record> updateRecords = Arrays.asList(buildSleepSession(), buildSleepSession());

        // Modify the uid of the updateRecords to the uuid that was present in the insert
        // records.
        for (int itr = 0; itr < updateRecords.size(); itr++) {
            updateRecords.set(
                    itr,
                    getSleepSessionRecord_update(
                            updateRecords.get(itr),
                            insertedRecords.get(itr).getMetadata().getId(),
                            insertedRecords.get(itr).getMetadata().getClientRecordId()));
        }

        TestUtils.updateRecords(updateRecords);

        // assert the inserted data has been modified by reading the data.
        readAndAssertEquals(updateRecords);
    }

    @Test
    public void testUpdateRecords_invalidInputRecords_noChangeInDataBase()
            throws InterruptedException {
        List<Record> insertedRecords =
                TestUtils.insertRecords(Arrays.asList(buildSleepSession(), buildSleepSession()));

        // read inserted records and verify that the data is same as inserted.
        readAndAssertEquals(insertedRecords);

        // Generate a second set of records that will be used to perform the update operation.
        List<Record> updateRecords = Arrays.asList(buildSleepSession(), buildSleepSession());

        // Modify the Uid of the updateRecords to the UUID that was present in the insert records,
        // leaving out alternate records so that they have a new UUID which is not present in the
        // dataBase.
        for (int itr = 0; itr < updateRecords.size(); itr++) {
            updateRecords.set(
                    itr,
                    getSleepSessionRecord_update(
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
        readAndAssertEquals(insertedRecords);
    }

    @Test
    public void testUpdateRecords_recordWithInvalidPackageName_noChangeInDataBase()
            throws InterruptedException {
        List<Record> insertedRecords =
                TestUtils.insertRecords(Arrays.asList(buildSleepSession(), buildSleepSession()));

        // read inserted records and verify that the data is same as inserted.
        readAndAssertEquals(insertedRecords);

        // Generate a second set of records that will be used to perform the update operation.
        List<Record> updateRecords = Arrays.asList(buildSleepSession(), buildSleepSession());

        // Modify the Uuid of the updateRecords to the uuid that was present in the insert records.
        for (int itr = 0; itr < updateRecords.size(); itr++) {
            updateRecords.set(
                    itr,
                    getSleepSessionRecord_update(
                            updateRecords.get(itr),
                            insertedRecords.get(itr).getMetadata().getId(),
                            insertedRecords.get(itr).getMetadata().getClientRecordId()));
            //             adding an entry with invalid packageName.
            updateRecords.set(itr, buildSleepSession());
        }

        try {
            TestUtils.updateRecords(updateRecords);
            Assert.fail("Expected to fail due to invalid package.");
        } catch (Exception exception) {
            // verify that the testcase failed due to invalid argument exception.
            assertThat(exception).isNotNull();
        }

        // assert the inserted data has not been modified by reading the data.
        readAndAssertEquals(insertedRecords);
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
                                .addRecordType(SleepSessionRecord.class)
                                .build());
        ChangeLogsRequest changeLogsRequest =
                new ChangeLogsRequest.Builder(tokenResponse.getToken()).build();
        ChangeLogsResponse response = TestUtils.getChangeLogs(changeLogsRequest);
        assertThat(response.getUpsertedRecords().size()).isEqualTo(0);
        assertThat(response.getDeletedLogs().size()).isEqualTo(0);

        List<Record> testRecord = Collections.singletonList(buildSleepSession());
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
                        .addRecordType(SleepSessionRecord.class)
                        .build());
        response = TestUtils.getChangeLogs(changeLogsRequest);
        assertThat(response.getDeletedLogs()).isEmpty();
    }

    private void assertRecordsAreEqual(List<Record> records, List<SleepSessionRecord> result) {
        ArrayList<SleepSessionRecord> recordsExercises = new ArrayList<>();
        for (Record record : records) {
            recordsExercises.add((SleepSessionRecord) record);
        }
        assertThat(result.size()).isEqualTo(recordsExercises.size());
        assertThat(result).containsExactlyElementsIn(recordsExercises);
    }

    SleepSessionRecord getSleepSessionRecord_update(
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
        return new SleepSessionRecord.Builder(
                        metadataWithId, SESSION_START_TIME, SESSION_START_TIME.plusSeconds(800))
                .setNotes("updated note")
                .setTitle("Evening nap")
                .setStages(
                        List.of(
                                new SleepSessionRecord.Stage(
                                        SESSION_START_TIME,
                                        SESSION_START_TIME.plusSeconds(100),
                                        SleepSessionRecord.StageType.STAGE_TYPE_UNKNOWN),
                                new SleepSessionRecord.Stage(
                                        SESSION_START_TIME.plusSeconds(200),
                                        SESSION_START_TIME.plusSeconds(300),
                                        SleepSessionRecord.StageType.STAGE_TYPE_SLEEPING_REM),
                                new SleepSessionRecord.Stage(
                                        SESSION_START_TIME.plusSeconds(400),
                                        SESSION_START_TIME.plusSeconds(500),
                                        SleepSessionRecord.StageType.STAGE_TYPE_SLEEPING_DEEP)))
                .setStartZoneOffset(ZoneOffset.systemDefault().getRules().getOffset(Instant.now()))
                .setEndZoneOffset(ZoneOffset.systemDefault().getRules().getOffset(Instant.now()))
                .build();
    }

    private void readAndAssertEquals(List<Record> records) throws InterruptedException {
        List<SleepSessionRecord> readRecords =
                TestUtils.readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(SleepSessionRecord.class)
                                .build());
        assertRecordsAreEqual(records, readRecords);
    }

    public static SleepSessionRecord buildSleepSessionMinimal() {
        return new SleepSessionRecord.Builder(
                        generateMetadata(), SESSION_START_TIME, SESSION_END_TIME)
                .build();
    }
}
