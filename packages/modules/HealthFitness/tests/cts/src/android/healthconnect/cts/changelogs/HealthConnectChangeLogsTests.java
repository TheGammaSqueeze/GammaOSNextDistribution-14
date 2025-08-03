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

package android.healthconnect.cts.changelogs;

import static android.healthconnect.cts.utils.DataFactory.buildExerciseSession;
import static android.healthconnect.cts.utils.DataFactory.generateMetadata;
import static android.healthconnect.cts.utils.DataFactory.getBasalMetabolicRateRecord;
import static android.healthconnect.cts.utils.DataFactory.getDataOrigin;
import static android.healthconnect.cts.utils.DataFactory.getDistanceRecord;
import static android.healthconnect.cts.utils.DataFactory.getHeartRateRecord;
import static android.healthconnect.cts.utils.DataFactory.getMetadataForId;
import static android.healthconnect.cts.utils.DataFactory.getStepsRecord;
import static android.healthconnect.cts.utils.DataFactory.getTestRecords;
import static android.healthconnect.cts.utils.DataFactory.getChangeLogTokenRequestForTestRecordTypes;
import static android.healthconnect.cts.utils.TestUtils.deleteAllStagedRemoteData;
import static android.healthconnect.cts.utils.TestUtils.deleteRecords;
import static android.healthconnect.cts.utils.TestUtils.deleteRecordsByIdFilter;
import static android.healthconnect.cts.utils.TestUtils.getChangeLogToken;
import static android.healthconnect.cts.utils.TestUtils.getChangeLogs;
import static android.healthconnect.cts.utils.TestUtils.insertRecordAndGetId;
import static android.healthconnect.cts.utils.TestUtils.insertRecords;
import static android.healthconnect.cts.utils.TestUtils.readRecords;
import static android.healthconnect.cts.utils.TestUtils.updateRecords;
import static android.healthconnect.cts.utils.TestUtils.verifyDeleteRecords;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import static java.time.temporal.ChronoUnit.HOURS;
import static java.time.temporal.ChronoUnit.MILLIS;
import static java.time.temporal.ChronoUnit.MINUTES;

import android.content.Context;
import android.health.connect.DeleteUsingFiltersRequest;
import android.health.connect.ReadRecordsRequestUsingIds;
import android.health.connect.RecordIdFilter;
import android.health.connect.changelog.ChangeLogTokenRequest;
import android.health.connect.changelog.ChangeLogTokenResponse;
import android.health.connect.changelog.ChangeLogsRequest;
import android.health.connect.changelog.ChangeLogsResponse;
import android.health.connect.datatypes.DataOrigin;
import android.health.connect.datatypes.DistanceRecord;
import android.health.connect.datatypes.HeartRateRecord;
import android.health.connect.datatypes.Metadata;
import android.health.connect.datatypes.Record;
import android.health.connect.datatypes.StepsRecord;
import android.healthconnect.cts.utils.AssumptionCheckerRule;
import android.healthconnect.cts.utils.TestUtils;
import android.platform.test.annotations.AppModeFull;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.runner.AndroidJUnit4;

import com.google.common.collect.ImmutableList;
import com.google.common.truth.Correspondence;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/** CTS test for API provided by HealthConnectManager. */
@AppModeFull(reason = "HealthConnectManager is not accessible to instant apps")
@RunWith(AndroidJUnit4.class)
public class HealthConnectChangeLogsTests {
    private final Context mContext = ApplicationProvider.getApplicationContext();
    private final String mPackageName = mContext.getPackageName();

    private static final Correspondence<ChangeLogsResponse.DeletedLog, Record>
            DELETED_LOG_TO_RECORD_CORRESPONDENCE =
                    Correspondence.from(
                            (deletedLog, record) ->
                                    deletedLog
                                            .getDeletedRecordId()
                                            .equals(record.getMetadata().getId()),
                            "has matching id");

    private static final Correspondence<ChangeLogsResponse.DeletedLog, String>
            DELETED_LOG_TO_STRING_CORRESPONDENCE =
                    Correspondence.from(
                            (deletedLog, stringValue) ->
                                    deletedLog.getDeletedRecordId().equals(stringValue),
                            "has matching string");

    private static final Correspondence<StepsRecord, StepsRecord> STEPS_RECORD_CORRESPONDENCE =
            Correspondence.from(
                    (record1, record2) ->
                            record1.getMetadata().getId().equals(record2.getMetadata().getId())
                                    && Objects.equals(
                                            record1.getMetadata().getClientRecordId(),
                                            record2.getMetadata().getClientRecordId())
                                    && record1.getCount() == record2.getCount(),
                    "has same id, same client id and same count");

    @Rule
    public AssumptionCheckerRule mSupportedHardwareRule =
            new AssumptionCheckerRule(
                    TestUtils::isHardwareSupported, "Tests should run on supported hardware only.");

    @After
    public void tearDown() throws InterruptedException {
        verifyDeleteRecords(
                new DeleteUsingFiltersRequest.Builder()
                        .addDataOrigin(
                                new DataOrigin.Builder().setPackageName(mPackageName).build())
                        .build());
        deleteAllStagedRemoteData();
    }

    @Test
    public void testBuildChangeLogTokenRequest_hasFieldsSet() {
        ChangeLogTokenRequest changeLogTokenRequest =
                new ChangeLogTokenRequest.Builder().addRecordType(StepsRecord.class).build();

        assertThat(changeLogTokenRequest.getRecordTypes()).containsExactly(StepsRecord.class);
        assertThat(changeLogTokenRequest.getDataOriginFilters()).isEmpty();
    }

    @Test
    public void testBuildChangeLogsRequest_hasFieldsSet() throws InterruptedException {
        ChangeLogTokenResponse tokenResponse =
                getChangeLogToken(getChangeLogTokenRequestForTestRecordTypes().build());
        ChangeLogsRequest changeLogsRequest =
                new ChangeLogsRequest.Builder(tokenResponse.getToken()).build();

        assertThat(changeLogsRequest.getToken()).isEqualTo(tokenResponse.getToken());
        assertThat(changeLogsRequest.getPageSize()).isEqualTo(1000);
    }

    @Test
    public void testChangeLogs_noOperations_returnsEmptyChangelogs() throws InterruptedException {
        ChangeLogTokenResponse tokenResponse =
                getChangeLogToken(getChangeLogTokenRequestForTestRecordTypes().build());
        ChangeLogsRequest changeLogsRequest =
                new ChangeLogsRequest.Builder(tokenResponse.getToken()).build();

        ChangeLogsResponse response = getChangeLogs(changeLogsRequest);

        assertThat(response.getUpsertedRecords()).isEmpty();
        assertThat(response.getDeletedLogs()).isEmpty();
    }

    @Test
    public void testChangeLogs_insert_returnsUpsertedLogsOnly() throws InterruptedException {
        ChangeLogTokenResponse tokenResponse =
                getChangeLogToken(getChangeLogTokenRequestForTestRecordTypes().build());
        ChangeLogsRequest changeLogsRequest =
                new ChangeLogsRequest.Builder(tokenResponse.getToken()).build();

        List<Record> testRecords = insertRecords(getTestRecords());
        ChangeLogsResponse response = getChangeLogs(changeLogsRequest);

        assertThat(response.getUpsertedRecords()).containsExactlyElementsIn(testRecords);
        assertThat(response.getDeletedLogs()).isEmpty();
    }

    @Test
    public void testChangeLogs_insert_filterWithNonExistingDataOrigin_returnsEmptyLogs()
            throws InterruptedException {
        ChangeLogTokenResponse tokenResponse =
                getChangeLogToken(
                        getChangeLogTokenRequestForTestRecordTypes()
                                .addDataOriginFilter(
                                        new DataOrigin.Builder().setPackageName("random").build())
                                .build());
        ChangeLogsRequest changeLogsRequest =
                new ChangeLogsRequest.Builder(tokenResponse.getToken()).build();

        insertRecords(getTestRecords());
        ChangeLogsResponse response = getChangeLogs(changeLogsRequest);

        assertThat(response.getUpsertedRecords()).isEmpty();
    }

    // TODO(b/322305654): insert data from other apps
    @Test
    public void testChangeLogs_insert_filterWithExistingDataOrigin_returnsUpsertedLogs()
            throws InterruptedException {
        Context context = ApplicationProvider.getApplicationContext();
        ChangeLogTokenResponse tokenResponse =
                getChangeLogToken(
                        getChangeLogTokenRequestForTestRecordTypes()
                                .addDataOriginFilter(
                                        new DataOrigin.Builder()
                                                .setPackageName(context.getPackageName())
                                                .build())
                                .build());
        ChangeLogsRequest changeLogsRequest =
                new ChangeLogsRequest.Builder(tokenResponse.getToken()).build();

        List<Record> testRecords = insertRecords(getTestRecords());
        ChangeLogsResponse response = getChangeLogs(changeLogsRequest);

        assertThat(response.getUpsertedRecords()).containsExactlyElementsIn(testRecords);
    }

    @Test
    public void testChangeLogs_insert_filterRecordType_returnsOnlyUpsertedLogForRecordType()
            throws InterruptedException {
        Context context = ApplicationProvider.getApplicationContext();
        ChangeLogTokenResponse tokenResponse =
                getChangeLogToken(
                        new ChangeLogTokenRequest.Builder()
                                .addDataOriginFilter(
                                        new DataOrigin.Builder()
                                                .setPackageName(context.getPackageName())
                                                .build())
                                .addRecordType(StepsRecord.class)
                                .build());
        ChangeLogsRequest changeLogsRequest =
                new ChangeLogsRequest.Builder(tokenResponse.getToken()).build();

        List<Record> testRecords = ImmutableList.of(getStepsRecord(), getHeartRateRecord());
        StepsRecord stepsRecord = (StepsRecord) insertRecords(testRecords).get(0);

        ChangeLogsResponse response = getChangeLogs(changeLogsRequest);
        assertThat(response.getUpsertedRecords()).containsExactly(stepsRecord);
    }

    @Test
    public void testChangeLogs_insertAndDeleteDataById_returnsDeletedLogsOnly()
            throws InterruptedException {
        ChangeLogTokenResponse tokenResponse =
                getChangeLogToken(getChangeLogTokenRequestForTestRecordTypes().build());
        ChangeLogsRequest changeLogsRequest =
                new ChangeLogsRequest.Builder(tokenResponse.getToken()).build();

        List<Record> testRecords = insertRecords(getTestRecords());
        deleteRecords(testRecords);
        ChangeLogsResponse response = getChangeLogs(changeLogsRequest);

        assertThat(response.getDeletedLogs())
                .comparingElementsUsing(DELETED_LOG_TO_RECORD_CORRESPONDENCE)
                .containsExactlyElementsIn(testRecords);
        assertThat(response.getUpsertedRecords()).isEmpty();
    }

    @Test
    public void testChangeLogs_insertAndDeleteByClientId_returnsDeletedLogsOnly()
            throws InterruptedException {
        ChangeLogTokenResponse tokenResponse =
                getChangeLogToken(getChangeLogTokenRequestForTestRecordTypes().build());
        ChangeLogsRequest changeLogsRequest =
                new ChangeLogsRequest.Builder(tokenResponse.getToken()).build();

        StepsRecord stepsRecord = getStepsRecord(/* steps = */ 10, "stepsId");
        insertRecords(ImmutableList.of(stepsRecord));
        deleteRecordsByIdFilter(
                ImmutableList.of(RecordIdFilter.fromClientRecordId(StepsRecord.class, "stepsId")));
        ChangeLogsResponse response = getChangeLogs(changeLogsRequest);

        assertThat(response.getDeletedLogs())
                .comparingElementsUsing(DELETED_LOG_TO_RECORD_CORRESPONDENCE)
                .containsExactly(stepsRecord);
        assertThat(response.getUpsertedRecords()).isEmpty();
    }

    @Test
    public void testChangeLogs_insertOldRecords_onlyReturnsUpsertedLogsAfterHistoricalAccess()
            throws InterruptedException {
        ChangeLogTokenResponse tokenResponse =
                getChangeLogToken(getChangeLogTokenRequestForTestRecordTypes().build());
        ChangeLogsRequest changeLogsRequest =
                new ChangeLogsRequest.Builder(tokenResponse.getToken()).build();

        StepsRecord stepsRecord45DaysAgo = getStepsRecord_minusDays(45);
        StepsRecord stepsRecord20DaysAgo = getStepsRecord_minusDays(20);
        StepsRecord stepsRecord5DaysAgo = getStepsRecord_minusDays(5);
        List<Record> insertedRecords =
                insertRecords(
                        ImmutableList.of(
                                stepsRecord45DaysAgo, stepsRecord20DaysAgo, stepsRecord5DaysAgo));
        List<StepsRecord> expectedRecords =
                readRecords(
                        new ReadRecordsRequestUsingIds.Builder<>(StepsRecord.class)
                                .addId(insertedRecords.get(1).getMetadata().getId())
                                .addId(insertedRecords.get(2).getMetadata().getId())
                                .build());
        ChangeLogsResponse response = getChangeLogs(changeLogsRequest);

        assertThat(response.getUpsertedRecords()).containsExactlyElementsIn(expectedRecords);
    }

    @Test
    public void testChangeLogs_insertAndDeleteOldRecords_returnsAllDeletedLogs()
            throws InterruptedException {
        ChangeLogTokenResponse tokenResponse =
                getChangeLogToken(getChangeLogTokenRequestForTestRecordTypes().build());
        ChangeLogsRequest changeLogsRequest =
                new ChangeLogsRequest.Builder(tokenResponse.getToken()).build();

        List<Record> testRecords =
                insertRecords(
                        ImmutableList.of(
                                getStepsRecord_minusDays(45),
                                getStepsRecord_minusDays(20),
                                getStepsRecord_minusDays(5)));
        deleteRecords(testRecords);
        ChangeLogsResponse response = getChangeLogs(changeLogsRequest);

        assertThat(response.getDeletedLogs())
                .comparingElementsUsing(DELETED_LOG_TO_RECORD_CORRESPONDENCE)
                .containsExactlyElementsIn(testRecords);
    }

    @Test
    public void testChangeLogs_insertAndDelete_nonExistingDataOriginFilter_returnsEmptyLogs()
            throws InterruptedException {
        ChangeLogTokenResponse tokenResponse =
                getChangeLogToken(
                        getChangeLogTokenRequestForTestRecordTypes()
                                .addDataOriginFilter(
                                        new DataOrigin.Builder().setPackageName("random").build())
                                .build());
        ChangeLogsRequest changeLogsRequest =
                new ChangeLogsRequest.Builder(tokenResponse.getToken()).build();

        List<Record> testRecords = getTestRecords();
        insertRecords(testRecords);
        deleteRecords(testRecords);
        ChangeLogsResponse response = getChangeLogs(changeLogsRequest);

        assertThat(response.getUpsertedRecords()).isEmpty();
        assertThat(response.getDeletedLogs()).isEmpty();
    }

    // TODO(b/322305654): insert data from other apps
    @Test
    public void testChangeLogs_insertAndDelete_existingDataOriginFilter_onlyReturnsDeletedLogs()
            throws InterruptedException {
        Context context = ApplicationProvider.getApplicationContext();
        ChangeLogTokenResponse tokenResponse =
                getChangeLogToken(
                        getChangeLogTokenRequestForTestRecordTypes()
                                .addDataOriginFilter(
                                        new DataOrigin.Builder()
                                                .setPackageName(context.getPackageName())
                                                .build())
                                .build());
        ChangeLogsRequest changeLogsRequest =
                new ChangeLogsRequest.Builder(tokenResponse.getToken()).build();

        List<Record> testRecords = insertRecords(getTestRecords());
        deleteRecords(testRecords);
        ChangeLogsResponse response = getChangeLogs(changeLogsRequest);

        assertThat(response.getUpsertedRecords()).isEmpty();
        assertThat(response.getDeletedLogs())
                .comparingElementsUsing(DELETED_LOG_TO_RECORD_CORRESPONDENCE)
                .containsExactlyElementsIn(testRecords);
    }

    @Test
    public void
            testChangeLogs_insertAndDelete_recordFilter_onlyReturnsDeletedLogsForFilteredRecord()
                    throws InterruptedException {
        Context context = ApplicationProvider.getApplicationContext();
        ChangeLogTokenResponse tokenResponse =
                getChangeLogToken(
                        new ChangeLogTokenRequest.Builder()
                                .addDataOriginFilter(
                                        new DataOrigin.Builder()
                                                .setPackageName(context.getPackageName())
                                                .build())
                                .addRecordType(StepsRecord.class)
                                .build());
        ChangeLogsRequest changeLogsRequest =
                new ChangeLogsRequest.Builder(tokenResponse.getToken()).build();

        List<Record> testRecords = ImmutableList.of(getStepsRecord(), getHeartRateRecord());
        insertRecords(testRecords);
        deleteRecords(testRecords);
        ChangeLogsResponse response = getChangeLogs(changeLogsRequest);

        StepsRecord stepsRecord = (StepsRecord) testRecords.get(0);
        assertThat(response.getUpsertedRecords()).isEmpty();
        assertThat(response.getDeletedLogs())
                .comparingElementsUsing(DELETED_LOG_TO_RECORD_CORRESPONDENCE)
                .containsExactly(stepsRecord);
    }

    @Test
    public void testChangeLogs_insertAndUpdateById_returnsUpdateChangeLogOnly()
            throws InterruptedException {
        ChangeLogTokenResponse tokenResponse =
                getChangeLogToken(getChangeLogTokenRequestForTestRecordTypes().build());
        ChangeLogsRequest changeLogsRequest =
                new ChangeLogsRequest.Builder(tokenResponse.getToken()).build();

        Metadata insertedRecordMetadata =
                insertRecords(
                                ImmutableList.of(
                                        getStepsRecord(
                                                /* steps = */ 10, new Metadata.Builder().build())))
                        .get(0)
                        .getMetadata();

        updateRecords(ImmutableList.of(getStepsRecord(/* steps = */ 123, insertedRecordMetadata)));
        ChangeLogsResponse response = getChangeLogs(changeLogsRequest);

        assertThat(response.getUpsertedRecords())
                .comparingElementsUsing(STEPS_RECORD_CORRESPONDENCE)
                .containsExactly(getStepsRecord(/* steps = */ 123, insertedRecordMetadata));
        assertThat(response.getDeletedLogs()).isEmpty();
    }

    @Test
    public void testChangeLogs_insertAndUpdateByClientId_returnsUpdateChangeLogOnly()
            throws InterruptedException {
        ChangeLogTokenResponse tokenResponse =
                getChangeLogToken(getChangeLogTokenRequestForTestRecordTypes().build());
        ChangeLogsRequest changeLogsRequest =
                new ChangeLogsRequest.Builder(tokenResponse.getToken()).build();

        Metadata insertedRecordMetadata =
                insertRecords(ImmutableList.of(getStepsRecord(/* steps = */ 10, "stepsId")))
                        .get(0)
                        .getMetadata();
        updateRecords(ImmutableList.of(getStepsRecord(/* steps = */ 123, "stepsId")));
        ChangeLogsResponse response = getChangeLogs(changeLogsRequest);

        assertThat(response.getUpsertedRecords())
                .comparingElementsUsing(STEPS_RECORD_CORRESPONDENCE)
                .containsExactly(getStepsRecord(/* steps = */ 123, insertedRecordMetadata));
        assertThat(response.getDeletedLogs()).isEmpty();
    }

    @Test
    public void testChangeLogs_insertUpdateAndDeleteById_returnsDeleteChangeLogOnly()
            throws InterruptedException {
        ChangeLogTokenResponse tokenResponse =
                getChangeLogToken(getChangeLogTokenRequestForTestRecordTypes().build());
        ChangeLogsRequest changeLogsRequest =
                new ChangeLogsRequest.Builder(tokenResponse.getToken()).build();

        Metadata insertedRecordMetadata =
                insertRecords(
                                ImmutableList.of(
                                        getStepsRecord(
                                                /* steps = */ 10, new Metadata.Builder().build())))
                        .get(0)
                        .getMetadata();
        assertThat(insertedRecordMetadata.getClientRecordId()).isNull();
        updateRecords(ImmutableList.of(getStepsRecord(/* steps = */ 123, insertedRecordMetadata)));
        deleteRecords(ImmutableList.of(getStepsRecord(/* steps = */ 123, insertedRecordMetadata)));
        ChangeLogsResponse response = getChangeLogs(changeLogsRequest);

        assertThat(response.getUpsertedRecords()).isEmpty();
        assertThat(response.getDeletedLogs())
                .comparingElementsUsing(DELETED_LOG_TO_STRING_CORRESPONDENCE)
                .containsExactly(insertedRecordMetadata.getId());
    }

    @Test
    public void testChangeLogs_insertUpdateAndDeleteByClientId_returnsDeleteChangeLogOnly()
            throws InterruptedException {
        ChangeLogTokenResponse tokenResponse =
                getChangeLogToken(getChangeLogTokenRequestForTestRecordTypes().build());
        ChangeLogsRequest changeLogsRequest =
                new ChangeLogsRequest.Builder(tokenResponse.getToken()).build();

        String insertedRecordId = insertRecordAndGetId(getStepsRecord(/* steps = */ 10, "stepsId"));
        updateRecords(ImmutableList.of(getStepsRecord(/* steps = */ 123, "stepsId")));
        deleteRecordsByIdFilter(
                ImmutableList.of(RecordIdFilter.fromClientRecordId(StepsRecord.class, "stepsId")));
        ChangeLogsResponse response = getChangeLogs(changeLogsRequest);

        assertThat(response.getUpsertedRecords()).isEmpty();
        assertThat(response.getDeletedLogs())
                .comparingElementsUsing(DELETED_LOG_TO_STRING_CORRESPONDENCE)
                .containsExactly(insertedRecordId);
    }

    @Test
    public void testChangeLogs_noOperations_withPageSize_returnsEmptyChangeLogs()
            throws InterruptedException {
        ChangeLogTokenResponse tokenResponse =
                getChangeLogToken(getChangeLogTokenRequestForTestRecordTypes().build());
        ChangeLogsRequest changeLogsRequest =
                new ChangeLogsRequest.Builder(tokenResponse.getToken()).setPageSize(1).build();

        ChangeLogsResponse response = getChangeLogs(changeLogsRequest);
        assertThat(response.getUpsertedRecords()).isEmpty();
        assertThat(response.getDeletedLogs()).isEmpty();
    }

    @Test
    public void testChangeLogs_insert_withPageSize_doesNotExceedPageSize()
            throws InterruptedException {
        ChangeLogTokenResponse tokenResponse =
                getChangeLogToken(getChangeLogTokenRequestForTestRecordTypes().build());
        ChangeLogsRequest changeLogsRequest =
                new ChangeLogsRequest.Builder(tokenResponse.getToken()).setPageSize(1).build();

        List<Record> testRecords = insertRecords(getTestRecords());

        ChangeLogsResponse response = getChangeLogs(changeLogsRequest);
        assertThat(response.getUpsertedRecords()).containsExactly(testRecords.get(0));
    }

    @Test
    public void testChangeLogs_insert_withPageSize_paginatesThroughAllChangeLogs()
            throws InterruptedException {
        ChangeLogTokenResponse tokenResponse =
                getChangeLogToken(getChangeLogTokenRequestForTestRecordTypes().build());
        ChangeLogsRequest changeLogsRequest =
                new ChangeLogsRequest.Builder(tokenResponse.getToken()).setPageSize(1).build();

        List<Record> testRecord =
                ImmutableList.of(
                        getStepsRecord(),
                        getHeartRateRecord(),
                        getBasalMetabolicRateRecord(),
                        buildExerciseSession());
        insertRecords(testRecord);

        ChangeLogsResponse response = getChangeLogs(changeLogsRequest);
        assertThat(response.hasMorePages()).isTrue();
        assertThat(response.getUpsertedRecords()).hasSize(1);

        ChangeLogsRequest nextChangeLogsRequest =
                new ChangeLogsRequest.Builder(response.getNextChangesToken())
                        .setPageSize(1)
                        .build();
        ChangeLogsResponse nextResponse = getChangeLogs(nextChangeLogsRequest);

        assertThat(response.getUpsertedRecords()).hasSize(1);
        assertThat(nextResponse.hasMorePages()).isTrue();
        assertThat(nextResponse.getNextChangesToken())
                .isGreaterThan(response.getNextChangesToken());

        nextChangeLogsRequest =
                new ChangeLogsRequest.Builder(nextResponse.getNextChangesToken()).build();
        nextResponse = getChangeLogs(nextChangeLogsRequest);

        assertThat(nextResponse.getUpsertedRecords()).hasSize(2);
        assertThat(nextResponse.hasMorePages()).isFalse();
    }

    // Test added for b/271607816 to make sure that getChangeLogs() method returns the requested
    // changelog token as nextPageToken in the response when it is the end of page.
    // ( i.e. hasMoreRecords is false)
    @Test
    public void testChangeLogs_checkToken_hasMorePages_False() throws InterruptedException {
        ChangeLogTokenResponse tokenResponse =
                getChangeLogToken(getChangeLogTokenRequestForTestRecordTypes().build());
        ChangeLogsRequest changeLogsRequest =
                new ChangeLogsRequest.Builder(tokenResponse.getToken()).build();

        List<Record> testRecord = getTestRecords();
        insertRecords(testRecord);

        ChangeLogsResponse response = getChangeLogs(changeLogsRequest);
        assertThat(response.getUpsertedRecords().size()).isEqualTo(testRecord.size());
        assertThat(response.hasMorePages()).isFalse();

        ChangeLogsRequest newChangeLogsRequest =
                new ChangeLogsRequest.Builder(response.getNextChangesToken())
                        .setPageSize(2)
                        .build();
        ChangeLogsResponse newResponse = getChangeLogs(newChangeLogsRequest);

        assertThat(newResponse.getUpsertedRecords()).isEmpty();
        assertThat(newResponse.hasMorePages()).isFalse();
        assertThat(newResponse.getNextChangesToken()).isEqualTo(newChangeLogsRequest.getToken());
    }

    @Test
    public void testGetChangeLogToken_emptyRecordTypes_throwsException() throws Exception {
        Throwable thrown =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> getChangeLogToken(new ChangeLogTokenRequest.Builder().build()));
        assertThat(thrown).hasMessageThat().contains("Requested record types must not be empty");
    }

    @Test
    public void testChangeLogs_operationCombinations_expectCorrectChangeLogs() throws Exception {
        Instant yesterday = Instant.now().minus(1, ChronoUnit.DAYS).truncatedTo(MILLIS);

        // insert some unrelated records
        insertRecords(getTestRecords());
        StepsRecord preExistingStepsRecord =
                getStepsRecord(
                        /* steps= */ 3456,
                        yesterday.plus(4, HOURS),
                        yesterday.plus(5, HOURS),
                        /* clientId= */ "preExistingStepsRecord");
        String preExistingStepsId = insertRecordAndGetId(preExistingStepsRecord);
        StepsRecord updatedPreExistingStepsRecord =
                new StepsRecord.Builder(
                                getMetadataForId(preExistingStepsId, getDataOrigin(mPackageName)),
                                preExistingStepsRecord.getStartTime(),
                                preExistingStepsRecord.getEndTime(),
                                6543)
                        .build();
        String preExistingRecordId = insertRecordAndGetId(getDistanceRecord());
        // then get changes token. The above records shouldn't be included in the change logs.
        String changeToken =
                getChangeLogToken(
                                getChangeLogTokenRequestForTestRecordTypes()
                                        .addRecordType(DistanceRecord.class)
                                        .build())
                        .getToken();

        ImmutableList.Builder<Record> recordBuilder = ImmutableList.builder();
        for (int i = 0; i < 50; i++) {
            recordBuilder.add(
                    getStepsRecord(
                            i + 100,
                            yesterday.plus(i, MINUTES),
                            yesterday.plus(i + 1, MINUTES),
                            "stepsId_" + i));
        }
        for (int i = 0; i < 50; i++) {
            int heartRate = (int) (Math.random() * 30) + 80;
            recordBuilder.add(
                    getHeartRateRecord(heartRate, yesterday.plus(i, MINUTES), "heartRateId_" + i));
        }
        List<Record> records = insertRecords(recordBuilder.build());

        StepsRecord stepsRecordToUpdate = (StepsRecord) records.get(2);
        StepsRecord updatedStepsRecord =
                new StepsRecord.Builder(
                                getMetadataForId(
                                        stepsRecordToUpdate.getMetadata().getId(),
                                        getDataOrigin(mPackageName)), // workaround for b/325029194
                                stepsRecordToUpdate.getStartTime(),
                                stepsRecordToUpdate.getEndTime(),
                                /* count= */ 9876)
                        .build();
        HeartRateRecord heartRateRecordToUpdate = (HeartRateRecord) records.get(68);
        HeartRateRecord updatedHeartRateRecord =
                new HeartRateRecord.Builder(
                                getMetadataForId(
                                        heartRateRecordToUpdate.getMetadata().getId(),
                                        getDataOrigin(mPackageName)),
                                heartRateRecordToUpdate.getStartTime(),
                                heartRateRecordToUpdate.getEndTime(),
                                List.of(
                                        new HeartRateRecord.HeartRateSample(
                                                150, heartRateRecordToUpdate.getStartTime())))
                        .build();
        // Update steps record, only the updated value should appear in upsert change log
        updateRecords(
                List.of(updatedPreExistingStepsRecord, updatedStepsRecord, updatedHeartRateRecord));

        deleteRecordsByIdFilter(
                List.of(
                        // Delete updated HR record, it should not appear in upsert change log
                        RecordIdFilter.fromId(
                                HeartRateRecord.class,
                                updatedHeartRateRecord.getMetadata().getId()),
                        RecordIdFilter.fromClientRecordId(
                                StepsRecord.class,
                                records.get(6).getMetadata().getClientRecordId()),
                        RecordIdFilter.fromId(DistanceRecord.class, preExistingRecordId)));
        List<String> expectedDeletedIds =
                List.of(
                        updatedHeartRateRecord.getMetadata().getId(),
                        records.get(6).getMetadata().getId(),
                        preExistingRecordId);

        ChangeLogsResponse response =
                getChangeLogs(new ChangeLogsRequest.Builder(changeToken).build());
        List<String> updatedIdsFromLog =
                response.getUpsertedRecords().stream()
                        .map(log -> log.getMetadata().getId())
                        .toList();
        List<String> deletedIdsFromLog =
                response.getDeletedLogs().stream()
                        .map(ChangeLogsResponse.DeletedLog::getDeletedRecordId)
                        .collect(Collectors.toList());

        // inserted 50*2 - deleted 2 + updated pre-existing 1
        assertThat(response.getUpsertedRecords()).hasSize(99);
        assertThat(updatedIdsFromLog).doesNotContain(updatedHeartRateRecord.getMetadata().getId());
        assertThat(response.getUpsertedRecords()).contains(updatedPreExistingStepsRecord);
        assertThat(response.getUpsertedRecords()).doesNotContain(stepsRecordToUpdate);
        assertThat(response.getUpsertedRecords()).contains(updatedStepsRecord);
        assertThat(deletedIdsFromLog).containsExactlyElementsIn(expectedDeletedIds);
    }

    private static StepsRecord getStepsRecord_minusDays(int days) {
        return new StepsRecord.Builder(
                        generateMetadata(),
                        Instant.now().minus(days, ChronoUnit.DAYS).truncatedTo(MILLIS),
                        Instant.now()
                                .minus(days, ChronoUnit.DAYS)
                                .plusMillis(1000)
                                .truncatedTo(MILLIS),
                        10)
                .build();
    }
}
