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
import static android.healthconnect.cts.utils.DataFactory.buildExerciseRoute;
import static android.healthconnect.cts.utils.DataFactory.buildExerciseSession;
import static android.healthconnect.cts.utils.DataFactory.buildLocationTimePoint;
import static android.healthconnect.cts.utils.DataFactory.generateMetadata;
import static android.healthconnect.cts.utils.TestUtils.distinctByUuid;
import static android.healthconnect.cts.utils.TestUtils.insertRecords;
import static android.healthconnect.cts.utils.TestUtils.readRecords;

import static com.google.common.truth.Truth.assertThat;

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
import android.health.connect.datatypes.ExerciseLap;
import android.health.connect.datatypes.ExerciseRoute;
import android.health.connect.datatypes.ExerciseRoute.Location;
import android.health.connect.datatypes.ExerciseSegment;
import android.health.connect.datatypes.ExerciseSegmentType;
import android.health.connect.datatypes.ExerciseSessionRecord;
import android.health.connect.datatypes.ExerciseSessionType;
import android.health.connect.datatypes.Metadata;
import android.health.connect.datatypes.Record;
import android.health.connect.datatypes.units.Length;
import android.healthconnect.cts.utils.AssumptionCheckerRule;
import android.healthconnect.cts.utils.TestUtils;
import android.util.Pair;

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

@RunWith(AndroidJUnit4.class)
public class ExerciseSessionRecordTest {

    @Rule
    public AssumptionCheckerRule mSupportedHardwareRule =
            new AssumptionCheckerRule(
                    TestUtils::isHardwareSupported, "Tests should run on supported hardware only.");

    @After
    public void tearDown() throws InterruptedException {
        TestUtils.verifyDeleteRecords(
                ExerciseSessionRecord.class,
                new TimeInstantRangeFilter.Builder()
                        .setStartTime(Instant.EPOCH)
                        .setEndTime(Instant.now())
                        .build());
        TestUtils.deleteAllStagedRemoteData();
    }

    @Test
    public void testExerciseSession_buildSession_buildCorrectObject() {
        ExerciseSessionRecord record = buildSessionMinimal();
        assertThat(record.getStartTime()).isEqualTo(SESSION_START_TIME);
        assertThat(record.getEndTime()).isEqualTo(SESSION_END_TIME);
        assertThat(record.hasRoute()).isFalse();
        assertThat(record.getRoute()).isNull();
        assertThat(record.getNotes()).isNull();
        assertThat(record.getTitle()).isNull();
        assertThat(record.getSegments()).isEmpty();
        assertThat(record.getLaps()).isEmpty();
    }

    @Test
    public void testBuildSession_noException() {
        for (int i = 0; i < 200; i++) {
            buildExerciseSession();
        }
    }

    @Test
    public void testExerciseSession_buildEqualSessions_equalsReturnsTrue() {
        Metadata metadata = generateMetadata();
        ExerciseSessionRecord record =
                new ExerciseSessionRecord.Builder(
                                metadata,
                                SESSION_START_TIME,
                                SESSION_END_TIME,
                                ExerciseSessionType.EXERCISE_SESSION_TYPE_BADMINTON)
                        .build();
        ExerciseSessionRecord record2 =
                new ExerciseSessionRecord.Builder(
                                metadata,
                                SESSION_START_TIME,
                                SESSION_END_TIME,
                                ExerciseSessionType.EXERCISE_SESSION_TYPE_BADMINTON)
                        .build();
        assertThat(record).isEqualTo(record2);
    }

    @Test
    public void testExerciseSession_buildSessionWithAllFields_buildCorrectObject() {
        ExerciseRoute route = buildExerciseRoute();
        String notes = "rain";
        String title = "Morning training";
        List<ExerciseSegment> segmentList =
                List.of(
                        new ExerciseSegment.Builder(
                                        SESSION_START_TIME,
                                        SESSION_END_TIME,
                                        ExerciseSegmentType.EXERCISE_SEGMENT_TYPE_OTHER_WORKOUT)
                                .setRepetitionsCount(10)
                                .build());

        List<ExerciseLap> lapsList =
                List.of(
                        new ExerciseLap.Builder(SESSION_START_TIME, SESSION_END_TIME)
                                .setLength(Length.fromMeters(10))
                                .build());
        ExerciseSessionRecord record =
                new ExerciseSessionRecord.Builder(
                                generateMetadata(),
                                SESSION_START_TIME,
                                SESSION_END_TIME,
                                ExerciseSessionType.EXERCISE_SESSION_TYPE_FOOTBALL_AMERICAN)
                        .setRoute(route)
                        .setEndZoneOffset(ZoneOffset.MAX)
                        .setStartZoneOffset(ZoneOffset.MIN)
                        .setNotes(notes)
                        .setTitle(title)
                        .setSegments(segmentList)
                        .setLaps(lapsList)
                        .build();

        assertThat(record.hasRoute()).isTrue();
        assertThat(record.getRoute()).isEqualTo(route);
        assertThat(record.getEndZoneOffset()).isEqualTo(ZoneOffset.MAX);
        assertThat(record.getStartZoneOffset()).isEqualTo(ZoneOffset.MIN);
        assertThat(record.getExerciseType())
                .isEqualTo(ExerciseSessionType.EXERCISE_SESSION_TYPE_FOOTBALL_AMERICAN);
        assertThat(record.getNotes().toString()).isEqualTo(notes);
        assertThat(record.getSegments()).isEqualTo(segmentList);
        assertThat(record.getLaps()).isEqualTo(lapsList);
        assertThat(record.getTitle().toString()).isEqualTo(title);
    }

    @Test
    public void testUpdateRecord_updateToRecordWithoutRouteWithWritePerm_routeIsNullAfterUpdate()
            throws InterruptedException {
        ExerciseRoute route = buildExerciseRoute();
        ExerciseSessionRecord record =
                new ExerciseSessionRecord.Builder(
                                new Metadata.Builder().build(),
                                SESSION_START_TIME,
                                SESSION_END_TIME,
                                ExerciseSessionType.EXERCISE_SESSION_TYPE_FOOTBALL_AMERICAN)
                        .setRoute(route)
                        .build();
        List<Record> recordList = TestUtils.insertRecords(Collections.singletonList(record));

        ReadRecordsRequestUsingIds.Builder<ExerciseSessionRecord> request =
                new ReadRecordsRequestUsingIds.Builder<>(ExerciseSessionRecord.class);
        request.addId(recordList.get(0).getMetadata().getId());

        ExerciseSessionRecord insertedRecord = TestUtils.readRecords(request.build()).get(0);
        assertThat(insertedRecord.hasRoute()).isTrue();
        assertThat(insertedRecord.getRoute()).isNotNull();

        TestUtils.updateRecords(
                Collections.singletonList(
                        getExerciseSessionRecord_update(
                                record, recordList.get(0).getMetadata().getId(), null)));

        insertedRecord = TestUtils.readRecords(request.build()).get(0);
        assertThat(insertedRecord.hasRoute()).isFalse();
        assertThat(insertedRecord.getRoute()).isNull();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testExerciseSessionBuilds_routeTimestampAfterSessionEnd_throwsException() {
        new ExerciseSessionRecord.Builder(
                        new Metadata.Builder().build(),
                        SESSION_START_TIME,
                        SESSION_END_TIME,
                        ExerciseSessionType.EXERCISE_SESSION_TYPE_FOOTBALL_AMERICAN)
                .setRoute(
                        new ExerciseRoute(
                                List.of(
                                        new Location.Builder(
                                                        SESSION_END_TIME.plusSeconds(1), 10.0, 10.0)
                                                .build())))
                .build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testExerciseSessionBuilds_routeTimestampBeforeSessionStart_throwsException() {
        new ExerciseSessionRecord.Builder(
                        new Metadata.Builder().build(),
                        SESSION_START_TIME,
                        SESSION_END_TIME,
                        ExerciseSessionType.EXERCISE_SESSION_TYPE_FOOTBALL_AMERICAN)
                .setRoute(
                        new ExerciseRoute(
                                List.of(
                                        new Location.Builder(
                                                        SESSION_START_TIME.minusSeconds(1),
                                                        10.0,
                                                        10.0)
                                                .build())))
                .build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testExerciseSessionBuilds_sessionTypeDoesntMatchSegment_throwsException() {
        buildRecordWithOneSegment(
                ExerciseSessionType.EXERCISE_SESSION_TYPE_BIKING_STATIONARY,
                ExerciseSegmentType.EXERCISE_SEGMENT_TYPE_BURPEE);
    }

    @Test
    public void testExerciseSessionBuilds_sessionTypeSwimming_noException() {
        buildRecordWithOneSegment(
                ExerciseSessionType.EXERCISE_SESSION_TYPE_SWIMMING_OPEN_WATER,
                ExerciseSegmentType.EXERCISE_SEGMENT_TYPE_SWIMMING_BREASTSTROKE);
    }

    @Test
    public void testExerciseSessionBuilds_segmentsTypeExercises_noException() {
        buildRecordWithOneSegment(
                ExerciseSessionType.EXERCISE_SESSION_TYPE_CALISTHENICS,
                ExerciseSegmentType.EXERCISE_SEGMENT_TYPE_BURPEE);
    }

    @Test
    public void testExerciseSessionBuilds_segmentTypeRest_noException() {
        buildRecordWithOneSegment(
                ExerciseSessionType.EXERCISE_SESSION_TYPE_CALISTHENICS,
                ExerciseSegmentType.EXERCISE_SEGMENT_TYPE_REST);
    }

    @Test
    public void testExerciseSessionBuilds_universalSegment_noException() {
        buildRecordWithOneSegment(
                ExerciseSessionType.EXERCISE_SESSION_TYPE_BIKING_STATIONARY,
                ExerciseSegmentType.EXERCISE_SEGMENT_TYPE_REST);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testExerciseSessionBuilds_negativeSessionType_throwsException() {
        buildRecordWithOneSegment(-1, ExerciseSegmentType.EXERCISE_SEGMENT_TYPE_REST);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testExerciseSessionBuilds_negativeSegmentType_throwsException() {
        buildRecordWithOneSegment(ExerciseSessionType.EXERCISE_SESSION_TYPE_BIKING_STATIONARY, -2);
    }

    @Test
    public void testExerciseSessionBuilds_unknownSessionType_noException() {
        buildRecordWithOneSegment(1000, ExerciseSegmentType.EXERCISE_SEGMENT_TYPE_REST);
    }

    @Test
    public void testExerciseSessionBuilds_unknownSegmentType_noException() {
        buildRecordWithOneSegment(
                ExerciseSessionType.EXERCISE_SESSION_TYPE_BIKING_STATIONARY, 1000);
    }

    @Test
    public void testExerciseSessionBuilds_zoneOffsets_offsetsAreDefault() {
        final ZoneOffset defaultZoneOffset =
                ZoneOffset.systemDefault().getRules().getOffset(Instant.now());
        final ZoneOffset startZoneOffset = ZoneOffset.UTC;
        final ZoneOffset endZoneOffset = ZoneOffset.MAX;
        ExerciseRoute route = buildExerciseRoute();
        CharSequence notes = "rain";
        CharSequence title = "Morning training";
        ExerciseSessionRecord.Builder builder =
                new ExerciseSessionRecord.Builder(
                                generateMetadata(),
                                SESSION_START_TIME,
                                SESSION_END_TIME,
                                ExerciseSessionType.EXERCISE_SESSION_TYPE_FOOTBALL_AMERICAN)
                        .setRoute(route)
                        .setEndZoneOffset(ZoneOffset.MAX)
                        .setStartZoneOffset(ZoneOffset.MIN)
                        .setNotes(notes)
                        .setTitle(title);

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
    public void testInsertRecord_apiReturnsRequestedRecords() throws InterruptedException {
        List<Record> records = Arrays.asList(buildExerciseSession(), buildExerciseSession());
        List<Record> insertedRecords = TestUtils.insertRecords(records);
        assertThat(records.size()).isEqualTo(insertedRecords.size());
        assertThat(records).containsExactlyElementsIn(insertedRecords);
    }

    @Test
    public void testReadById_insertAndReadById_recordsAreEqual() throws InterruptedException {
        List<Record> records = List.of(buildExerciseSession(), buildSessionMinimal());
        TestUtils.insertRecords(records);

        ReadRecordsRequestUsingIds.Builder<ExerciseSessionRecord> request =
                new ReadRecordsRequestUsingIds.Builder<>(ExerciseSessionRecord.class);
        request.addId(records.get(0).getMetadata().getId());
        request.addId(records.get(1).getMetadata().getId());

        assertRecordsAreEqual(records, TestUtils.readRecords(request.build()));
    }

    @Test
    public void testReadById_insertAndReadByIdOne_recordsAreEqual() throws InterruptedException {
        List<Record> records = List.of(buildExerciseSession());
        TestUtils.insertRecords(records);

        ReadRecordsRequestUsingIds.Builder<ExerciseSessionRecord> request =
                new ReadRecordsRequestUsingIds.Builder<>(ExerciseSessionRecord.class);
        request.addId(records.get(0).getMetadata().getId());

        ExerciseSessionRecord readRecord = TestUtils.readRecords(request.build()).get(0);
        ExerciseSessionRecord insertedRecord = (ExerciseSessionRecord) records.get(0);
        assertThat(readRecord.hasRoute()).isEqualTo(insertedRecord.hasRoute());
        assertThat(readRecord.getMetadata()).isEqualTo(insertedRecord.getMetadata());
        assertThat(readRecord.getRoute()).isEqualTo(insertedRecord.getRoute());
        assertThat(readRecord.getLaps()).isEqualTo(insertedRecord.getLaps());
        assertThat(readRecord.getSegments()).isEqualTo(insertedRecord.getSegments());
    }

    @Test
    public void testReadByClientId_insertAndReadByClientId_recordsAreEqual()
            throws InterruptedException {
        List<Record> records = List.of(buildExerciseSession(), buildSessionMinimal());
        TestUtils.insertRecords(records);

        ReadRecordsRequestUsingIds.Builder<ExerciseSessionRecord> request =
                new ReadRecordsRequestUsingIds.Builder<>(ExerciseSessionRecord.class);
        request.addClientRecordId(records.get(0).getMetadata().getClientRecordId());
        request.addClientRecordId(records.get(1).getMetadata().getClientRecordId());

        assertRecordsAreEqual(records, TestUtils.readRecords(request.build()));
    }

    @Test
    public void testReadByClientId_insertAndReadByDefaultFilter_filteredAll()
            throws InterruptedException {
        List<Record> records = List.of(buildExerciseSession(), buildSessionMinimal());
        assertThat(TestUtils.insertRecords(records)).hasSize(2);

        List<ExerciseSessionRecord> readRecords =
                TestUtils.readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(ExerciseSessionRecord.class)
                                .build());
        assertRecordsAreEqual(records, readRecords);
    }

    @Test
    public void testReadByClientId_insertAndReadByTimeFilter_filteredCorrectly()
            throws InterruptedException {
        List<Record> records = List.of(buildExerciseSession(), buildSessionMinimal());
        TestUtils.insertRecords(records);

        TimeInstantRangeFilter filter =
                new TimeInstantRangeFilter.Builder()
                        .setStartTime(SESSION_START_TIME.minusMillis(10))
                        .setEndTime(SESSION_END_TIME.plusMillis(10))
                        .build();

        ExerciseSessionRecord outOfRangeRecord =
                buildSession(SESSION_END_TIME.plusMillis(100), SESSION_END_TIME.plusMillis(200));
        TestUtils.insertRecords(List.of(outOfRangeRecord));

        List<ExerciseSessionRecord> readRecords =
                TestUtils.readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(ExerciseSessionRecord.class)
                                .setTimeRangeFilter(filter)
                                .build());
        assertRecordsAreEqual(records, readRecords);
    }

    @Test
    public void testDeleteRecords_insertAndDeleteById_recordsNotFoundAnymore()
            throws InterruptedException {
        List<Record> records = List.of(buildExerciseSession(), buildSessionMinimal());
        List<Record> insertedRecords = TestUtils.insertRecords(records);

        TestUtils.assertRecordFound(
                records.get(0).getMetadata().getId(), ExerciseSessionRecord.class);
        TestUtils.assertRecordFound(
                records.get(1).getMetadata().getId(), ExerciseSessionRecord.class);

        TestUtils.deleteRecords(insertedRecords);

        TestUtils.assertRecordNotFound(
                records.get(0).getMetadata().getId(), ExerciseSessionRecord.class);
        TestUtils.assertRecordNotFound(
                records.get(1).getMetadata().getId(), ExerciseSessionRecord.class);
    }

    @Test
    public void testUpdateRecords_validInput_dataBaseUpdatedSuccessfully()
            throws InterruptedException {

        List<Record> insertedRecords =
                TestUtils.insertRecords(
                        Arrays.asList(
                                buildSession(Instant.now(), Instant.now().plusMillis(10000)),
                                buildSession(Instant.now(), Instant.now().plusMillis(10000))));

        // read inserted records and verify that the data is same as inserted.
        readAndAssertEquals(insertedRecords);

        // Generate a new set of records that will be used to perform the update operation.
        List<Record> updateRecords =
                Arrays.asList(
                        buildSession(Instant.now(), Instant.now().plusMillis(10000)),
                        buildSession(Instant.now(), Instant.now().plusMillis(10000)));

        // Modify the uid of the updateRecords to the uuid that was present in the insert records.
        for (int itr = 0; itr < updateRecords.size(); itr++) {
            updateRecords.set(
                    itr,
                    getExerciseSessionRecord_update(
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
                TestUtils.insertRecords(
                        Arrays.asList(
                                buildSession(Instant.now(), Instant.now().plusMillis(10000)),
                                buildSession(Instant.now(), Instant.now().plusMillis(10000))));
        // read inserted records and verify that the data is same as inserted.
        readAndAssertEquals(insertedRecords);

        // Generate a second set of records that will be used to perform the update operation.
        List<Record> updateRecords =
                Arrays.asList(
                        buildSession(Instant.now(), Instant.now().plusMillis(10000)),
                        buildSession(Instant.now(), Instant.now().plusMillis(10000)));
        // Modify the Uid of the updateRecords to the UUID that was present in the insert records,
        // leaving out alternate records so that they have a new UUID which is not present in the
        // dataBase.
        for (int itr = 0; itr < updateRecords.size(); itr++) {
            updateRecords.set(
                    itr,
                    getExerciseSessionRecord_update(
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
                TestUtils.insertRecords(
                        Arrays.asList(
                                buildSession(Instant.now(), Instant.now().plusMillis(10000)),
                                buildSession(Instant.now(), Instant.now().plusMillis(10000))));

        // read inserted records and verify that the data is same as inserted.
        readAndAssertEquals(insertedRecords);

        // Generate a second set of records that will be used to perform the update operation.
        List<Record> updateRecords =
                Arrays.asList(
                        buildSession(Instant.now(), Instant.now().plusMillis(10000)),
                        buildSession(Instant.now(), Instant.now().plusMillis(10000)));

        // Modify the Uuid of the updateRecords to the uuid that was present in the insert records.
        for (int itr = 0; itr < updateRecords.size(); itr++) {
            updateRecords.set(
                    itr,
                    getExerciseSessionRecord_update(
                            updateRecords.get(itr),
                            insertedRecords.get(itr).getMetadata().getId(),
                            insertedRecords.get(itr).getMetadata().getClientRecordId()));
            //             adding an entry with invalid packageName.
            updateRecords.set(itr, buildSession(Instant.now(), Instant.now().plusMillis(10000)));
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
                                .addRecordType(ExerciseSessionRecord.class)
                                .build());
        ChangeLogsRequest changeLogsRequest =
                new ChangeLogsRequest.Builder(tokenResponse.getToken()).build();
        ChangeLogsResponse response = TestUtils.getChangeLogs(changeLogsRequest);
        assertThat(response.getUpsertedRecords().size()).isEqualTo(0);
        assertThat(response.getDeletedLogs().size()).isEqualTo(0);

        List<Record> testRecord = Collections.singletonList(buildExerciseSession());
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
                        .addRecordType(ExerciseSessionRecord.class)
                        .build());
        response = TestUtils.getChangeLogs(changeLogsRequest);
        assertThat(response.getDeletedLogs()).isEmpty();
    }

    @Test
    public void insertRecords_withDuplicatedClientRecordId_readNoDuplicates() throws Exception {
        int distinctRecordCount = 10;
        List<ExerciseSessionRecord> records = new ArrayList<>();
        Instant now = Instant.now();
        for (int i = 0; i < distinctRecordCount; i++) {
            ExerciseSessionRecord record =
                    buildSession(
                            /* startTime= */ now.minusSeconds(i + 1),
                            /* endTime= */ now.minusSeconds(i),
                            /* clientRecordId= */ "client_id_" + i);

            records.add(record);
            records.add(record); // Add each record twice
        }

        List<Record> insertedRecords = TestUtils.insertRecords(records);
        assertThat(insertedRecords.size()).isEqualTo(records.size());

        List<Record> distinctRecords = distinctByUuid(insertedRecords);
        assertThat(distinctRecords.size()).isEqualTo(distinctRecordCount);

        readAndAssertEquals(distinctRecords);
    }

    @Test
    public void insertRecords_sameClientRecordIdAndNewData_readNewData() throws Exception {
        int recordCount = 10;
        double oldLat = 10;
        double oldLng = 20;
        insertAndReadRecords(recordCount, oldLat, oldLng);

        double newLat = 30;
        double newLng = 40;
        List<ExerciseSessionRecord> newRecords = insertAndReadRecords(recordCount, newLat, newLng);

        for (ExerciseSessionRecord record : newRecords) {
            assertRoute(record, newLat, newLng);
        }
    }

    @Test
    public void insertRecords_sameClientRecordIdAndNewerVersion_readNewData() throws Exception {
        int recordCount = 10;
        long oldVersion = 0L;
        double oldLat = 10;
        double oldLng = 20;
        insertAndReadRecords(recordCount, oldVersion, oldLat, oldLng);

        long newVersion = 1L;
        double newLat = 30;
        double newLng = 40;
        List<ExerciseSessionRecord> newRecords =
                insertAndReadRecords(recordCount, newVersion, newLat, newLng);

        for (ExerciseSessionRecord record : newRecords) {
            assertRoute(record, newLat, newLng);
        }
    }

    @Test
    public void insertRecords_sameClientRecordIdAndSameVersion_readNewData() throws Exception {
        int recordCount = 10;
        long version = 1L;
        double oldLat = 10;
        double oldLng = 20;
        insertAndReadRecords(recordCount, version, oldLat, oldLng);

        double newLat = 30;
        double newLng = 40;
        List<ExerciseSessionRecord> newRecords =
                insertAndReadRecords(recordCount, version, newLat, newLng);

        for (ExerciseSessionRecord record : newRecords) {
            assertRoute(record, newLat, newLng);
        }
    }

    @Test
    public void insertRecords_sameClientRecordIdAndOlderVersion_readOldData() throws Exception {
        int recordCount = 10;
        long oldVersion = 1L;
        double oldLat = 10;
        double oldLng = 20;
        insertAndReadRecords(recordCount, oldVersion, oldLat, oldLng);

        long newVersion = 0L;
        double newLat = 30;
        double newLng = 40;
        List<ExerciseSessionRecord> newRecords =
                insertAndReadRecords(recordCount, newVersion, newLat, newLng);

        for (ExerciseSessionRecord record : newRecords) {
            assertRoute(record, oldLat, oldLng);
        }
    }

    private static void assertRoute(ExerciseSessionRecord record, double lat, double lng) {
        ExerciseRoute route = record.getRoute();
        assertThat(route).isNotNull();

        assertThat(
                        route.getRouteLocations().stream()
                                .map(loc -> new Pair<>(loc.getLatitude(), loc.getLongitude()))
                                .distinct()
                                .toList())
                .containsExactly(new Pair<>(lat, lng));
    }

    private static List<ExerciseSessionRecord> insertAndReadRecords(
            int recordCount, double lat, double lng) throws Exception {
        return insertAndReadRecords(recordCount, /* version= */ 0L, lat, lng);
    }

    private static List<ExerciseSessionRecord> insertAndReadRecords(
            int recordCount, long version, double lat, double lng) throws Exception {
        List<ExerciseSessionRecord> records = new ArrayList<>();
        Instant now = Instant.now();
        for (int i = 0; i < recordCount; i++) {
            Instant startTime = now.minusSeconds(i + 1);
            Instant endTime = now.minusSeconds(i);
            String clientRecordId = "client_id_" + i;
            Location location = new Location.Builder(startTime, lat, lng).build();
            records.add(buildSession(startTime, endTime, clientRecordId, version, location));
        }
        List<Record> insertedRecords = insertRecords(records);
        assertThat(insertedRecords).hasSize(recordCount);

        List<ExerciseSessionRecord> readRecords =
                readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(ExerciseSessionRecord.class)
                                .build());
        assertThat(readRecords).hasSize(recordCount);

        return readRecords;
    }

    private ExerciseSessionRecord buildRecordWithOneSegment(int sessionType, int segmentType) {
        return new ExerciseSessionRecord.Builder(
                        generateMetadata(), SESSION_START_TIME, SESSION_END_TIME, sessionType)
                .setSegments(
                        List.of(
                                new ExerciseSegment.Builder(
                                                SESSION_START_TIME, SESSION_END_TIME, segmentType)
                                        .build()))
                .build();
    }

    private void assertRecordsAreEqual(List<Record> records, List<ExerciseSessionRecord> result) {
        ArrayList<ExerciseSessionRecord> recordsExercises = new ArrayList<>();
        for (Record record : records) {
            recordsExercises.add((ExerciseSessionRecord) record);
        }
        assertThat(result.size()).isEqualTo(recordsExercises.size());
        assertThat(result).containsExactlyElementsIn(recordsExercises);
    }

    private ExerciseSessionRecord getExerciseSessionRecord_update(
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
        return new ExerciseSessionRecord.Builder(
                        metadataWithId, Instant.now(), Instant.now().plusMillis(2000), 2)
                .setEndZoneOffset(ZoneOffset.MAX)
                .setStartZoneOffset(ZoneOffset.MIN)
                .setNotes("notes")
                .setTitle("title")
                .build();
    }

    private void readAndAssertEquals(List<Record> records) throws InterruptedException {
        List<ExerciseSessionRecord> readRecords =
                TestUtils.readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(ExerciseSessionRecord.class)
                                .build());
        assertRecordsAreEqual(records, readRecords);
    }

    private static ExerciseSessionRecord buildSession(Instant startTime, Instant endTime) {
        return buildSession(
                startTime, endTime, /* clientRecordId= */ "ExerciseSessionClient" + Math.random());
    }

    private static ExerciseSessionRecord buildSession(
            Instant startTime, Instant endTime, String clientRecordId) {
        return buildSession(startTime, endTime, clientRecordId, buildLocationTimePoint(startTime));
    }

    private static ExerciseSessionRecord buildSession(
            Instant startTime, Instant endTime, String clientRecordId, Location location) {
        return buildSession(
                startTime, endTime, clientRecordId, /* clientRecordVersion= */ 0L, location);
    }

    private static ExerciseSessionRecord buildSession(
            Instant startTime,
            Instant endTime,
            String clientRecordId,
            long clientRecordVersion,
            Location location) {
        return new ExerciseSessionRecord.Builder(
                        buildMetadata(clientRecordId, clientRecordVersion),
                        startTime,
                        endTime,
                        ExerciseSessionType.EXERCISE_SESSION_TYPE_FOOTBALL_AMERICAN)
                .setEndZoneOffset(ZoneOffset.MAX)
                .setStartZoneOffset(ZoneOffset.MIN)
                .setRoute(new ExerciseRoute(List.of(location)))
                .setNotes("notes")
                .setTitle("title")
                .build();
    }

    private static ExerciseSessionRecord buildSessionMinimal() {
        return new ExerciseSessionRecord.Builder(
                        buildMetadata("ExerciseSessionClient" + Math.random()),
                        SESSION_START_TIME,
                        SESSION_END_TIME,
                        ExerciseSessionType.EXERCISE_SESSION_TYPE_FOOTBALL_AMERICAN)
                .build();
    }

    private static Metadata buildMetadata(String clientRecordId) {
        return buildMetadata(clientRecordId, /* clientRecordVersion= */ 0L);
    }

    private static Metadata buildMetadata(String clientRecordId, long clientRecordVersion) {
        return new Metadata.Builder()
                .setDataOrigin(
                        new DataOrigin.Builder()
                                .setPackageName("android.healthconnect.cts")
                                .build())
                .setId(UUID.randomUUID().toString())
                .setClientRecordId(clientRecordId)
                .setClientRecordVersion(clientRecordVersion)
                .setRecordingMethod(Metadata.RECORDING_METHOD_ACTIVELY_RECORDED)
                .build();
    }
}
