/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.healthconnect.cts.device;

import static android.health.connect.datatypes.ExerciseSegmentType.EXERCISE_SEGMENT_TYPE_PAUSE;
import static android.health.connect.datatypes.ExerciseSessionRecord.EXERCISE_DURATION_TOTAL;
import static android.health.connect.datatypes.ExerciseSessionType.EXERCISE_SESSION_TYPE_RUNNING;
import static android.health.connect.datatypes.StepsRecord.STEPS_COUNT_TOTAL;
import static android.healthconnect.cts.utils.DataFactory.buildExerciseSessionWithEmptyMetadata;
import static android.healthconnect.cts.utils.DataFactory.buildSleepSessionWithEmptyMetadata;
import static android.healthconnect.cts.utils.DataFactory.getDataOrigin;
import static android.healthconnect.cts.utils.DataFactory.getDataOrigins;
import static android.healthconnect.cts.utils.DataFactory.getDistanceRecordWithEmptyMetadata;
import static android.healthconnect.cts.utils.DataFactory.getEmptyMetadata;
import static android.healthconnect.cts.utils.DataFactory.getHeartRateRecordWithEmptyMetadata;
import static android.healthconnect.cts.utils.DataFactory.getMetadata;
import static android.healthconnect.cts.utils.DataFactory.getMetadataForClientId;
import static android.healthconnect.cts.utils.DataFactory.getMetadataForId;
import static android.healthconnect.cts.utils.DataFactory.getStepsRecordWithEmptyMetaData;
import static android.healthconnect.cts.utils.DataFactory.getTotalCaloriesBurnedRecordWithEmptyMetadata;
import static android.healthconnect.cts.utils.PermissionHelper.getGrantedHealthPermissions;
import static android.healthconnect.cts.utils.PermissionHelper.grantPermission;
import static android.healthconnect.cts.utils.PermissionHelper.revokeAndThenGrantHealthPermissions;
import static android.healthconnect.cts.utils.PermissionHelper.revokeHealthPermissions;
import static android.healthconnect.cts.utils.PermissionHelper.revokePermission;
import static android.healthconnect.cts.utils.TestUtils.createReadRecordsRequestUsingFilters;
import static android.healthconnect.cts.utils.TestUtils.deleteAllStagedRemoteData;
import static android.healthconnect.cts.utils.TestUtils.deleteTestData;
import static android.healthconnect.cts.utils.TestUtils.fetchDataOriginsPriorityOrder;
import static android.healthconnect.cts.utils.TestUtils.getAggregateResponse;
import static android.healthconnect.cts.utils.TestUtils.getApplicationInfo;
import static android.healthconnect.cts.utils.TestUtils.getRecordIdFilters;
import static android.healthconnect.cts.utils.TestUtils.getRecordIds;
import static android.healthconnect.cts.utils.TestUtils.insertRecordsForPriority;
import static android.healthconnect.cts.utils.TestUtils.readRecords;
import static android.healthconnect.cts.utils.TestUtils.updateDataOriginPriorityOrder;
import static android.healthconnect.cts.utils.TestUtils.verifyDeleteRecords;
import static android.healthconnect.cts.utils.TestUtils.yesterdayAt;

import static com.android.compatibility.common.util.SystemUtil.eventually;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.fail;

import static java.time.Duration.ofMinutes;

import android.app.UiAutomation;
import android.content.Context;
import android.content.pm.PackageManager;
import android.health.connect.AggregateRecordsRequest;
import android.health.connect.AggregateRecordsResponse;
import android.health.connect.HealthConnectException;
import android.health.connect.HealthDataCategory;
import android.health.connect.HealthPermissions;
import android.health.connect.ReadRecordsRequestUsingFilters;
import android.health.connect.ReadRecordsRequestUsingIds;
import android.health.connect.RecordIdFilter;
import android.health.connect.TimeInstantRangeFilter;
import android.health.connect.UpdateDataOriginPriorityOrderRequest;
import android.health.connect.changelog.ChangeLogTokenRequest;
import android.health.connect.changelog.ChangeLogsRequest;
import android.health.connect.changelog.ChangeLogsResponse;
import android.health.connect.datatypes.AggregationType;
import android.health.connect.datatypes.AppInfo;
import android.health.connect.datatypes.BasalMetabolicRateRecord;
import android.health.connect.datatypes.DataOrigin;
import android.health.connect.datatypes.DistanceRecord;
import android.health.connect.datatypes.ExerciseSegment;
import android.health.connect.datatypes.ExerciseSessionRecord;
import android.health.connect.datatypes.HeartRateRecord;
import android.health.connect.datatypes.Metadata;
import android.health.connect.datatypes.Record;
import android.health.connect.datatypes.SleepSessionRecord;
import android.health.connect.datatypes.StepsRecord;
import android.health.connect.datatypes.TotalCaloriesBurnedRecord;
import android.health.connect.datatypes.units.Power;
import android.healthconnect.cts.lib.TestAppProxy;
import android.healthconnect.cts.utils.AssumptionCheckerRule;
import android.healthconnect.cts.utils.TestUtils;
import android.util.Pair;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@RunWith(AndroidJUnit4.class)
public class HealthConnectDeviceTest {
    static final String TAG = "HealthConnectDeviceTest";
    public static final String MANAGE_HEALTH_DATA = HealthPermissions.MANAGE_HEALTH_DATA_PERMISSION;

    public static final String APP_A_DECLARED_PERMISSION = HealthPermissions.READ_STEPS;

    static final long VERSION_CODE = 1;
    private static final int ASYNC_RETRIES = 3;
    private static final int ASYNC_RETRY_DELAY_MILLIS = 500;

    private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    private static final List<Record> TEST_RECORDS =
            List.of(
                    getStepsRecord(getEmptyMetadata()),
                    getHeartRateRecord(getEmptyMetadata()),
                    getBasalMetabolicRateRecord(getEmptyMetadata()),
                    getExerciseSessionRecord(getEmptyMetadata()));

    private static final TestAppProxy APP_A_WITH_READ_WRITE_PERMS =
            TestAppProxy.forPackageName("android.healthconnect.cts.testapp.readWritePerms.A");

    private static final TestAppProxy APP_B_WITH_READ_WRITE_PERMS =
            TestAppProxy.forPackageName("android.healthconnect.cts.testapp.readWritePerms.B");

    private static final TestAppProxy APP_WITH_WRITE_PERMS_ONLY =
            TestAppProxy.forPackageName("android.healthconnect.cts.testapp.writePermsOnly");

    private static final TestAppProxy APP_WITH_DATA_MANAGE_PERMS_ONLY =
            TestAppProxy.forPackageName(
                    "android.healthconnect.cts.testapp.data.manage.permissions");

    private static final String STEPS_1000_CLIENT_ID = "client-id-1";
    private static final String STEPS_2000_CLIENT_ID = "client-id-2";
    private static final StepsRecord STEPS_1000 =
            getStepsRecord(
                    /* stepCount= */ 1000,
                    /* startTime= */ Instant.now().minus(2, ChronoUnit.HOURS),
                    /* durationInHours= */ 1,
                    STEPS_1000_CLIENT_ID);
    private static final StepsRecord STEPS_2000 =
            getStepsRecord(
                    /* stepCount= */ 2000,
                    /* startTime= */ Instant.now().minus(4, ChronoUnit.HOURS),
                    /* durationInHours= */ 1,
                    STEPS_2000_CLIENT_ID);

    private static final AggregationType<Long> WRITE_ONLY_PERM_AGGREGATION_STEPS_TOTAL =
            STEPS_COUNT_TOTAL;

    private static final AggregationType<Long> READ_PERM_AGGREGATION_EXERCISE_DURATION_TOTAL =
            EXERCISE_DURATION_TOTAL;

    private Context mContext;

    @Rule
    public AssumptionCheckerRule mSupportedHardwareRule =
            new AssumptionCheckerRule(
                    TestUtils::isHardwareSupported, "Tests should run on supported hardware only.");

    @Before
    public void setUp() {
        mContext = ApplicationProvider.getApplicationContext();
        grantPermission(APP_A_WITH_READ_WRITE_PERMS.getPackageName(), APP_A_DECLARED_PERMISSION);
    }

    @After
    public void tearDown() throws InterruptedException {
        deleteTestData();
        deleteAllStagedRemoteData();
        grantPermission(APP_A_WITH_READ_WRITE_PERMS.getPackageName(), APP_A_DECLARED_PERMISSION);
    }

    @Test
    public void testAppWithNormalReadWritePermCanInsertRecord() throws Exception {
        StepsRecord record = getStepsRecord(getEmptyMetadata());
        APP_A_WITH_READ_WRITE_PERMS.insertRecords(record);
    }

    @Test
    public void testAnAppCantDeleteAnotherAppEntry() throws Exception {
        StepsRecord record = getStepsRecord(getEmptyMetadata());
        String recordId = APP_A_WITH_READ_WRITE_PERMS.insertRecords(record).get(0);
        RecordIdFilter recordIdFilter = RecordIdFilter.fromId(StepsRecord.class, recordId);

        HealthConnectException e =
                assertThrows(
                        HealthConnectException.class,
                        () -> APP_B_WITH_READ_WRITE_PERMS.deleteRecords(recordIdFilter));

        assertThat(e.getErrorCode()).isEqualTo(HealthConnectException.ERROR_INVALID_ARGUMENT);
    }

    @Test
    public void testAnAppCantUpdateAnotherAppEntry() throws Exception {
        StepsRecord record = getStepsRecord(getEmptyMetadata());
        String recordId = APP_A_WITH_READ_WRITE_PERMS.insertRecords(record).get(0);
        StepsRecord updatedRecord = getStepsRecord(getMetadataForId(recordId));

        HealthConnectException e =
                assertThrows(
                        HealthConnectException.class,
                        () -> APP_B_WITH_READ_WRITE_PERMS.updateRecords(updatedRecord));

        assertThat(e.getErrorCode()).isEqualTo(HealthConnectException.ERROR_INVALID_ARGUMENT);
    }

    @Test
    public void testDataOriginGetsOverriddenBySelfPackageName() throws Exception {
        ExerciseSessionRecord record =
                getExerciseSessionRecord(getMetadata(getDataOrigin("ignored.package.name")));
        String recordId = APP_A_WITH_READ_WRITE_PERMS.insertRecords(record).get(0);

        List<ExerciseSessionRecord> records =
                readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(ExerciseSessionRecord.class)
                                .build());

        assertThat(records).hasSize(1);
        assertThat(records.get(0).getMetadata())
                .isEqualTo(
                        new Metadata.Builder()
                                .setId(recordId)
                                .setDataOrigin(
                                        getDataOrigin(APP_A_WITH_READ_WRITE_PERMS.getPackageName()))
                                .build());
    }

    @Test
    public void testAppWithWritePermsOnly_readOwnData_success() throws Exception {
        StepsRecord record = getStepsRecord(getEmptyMetadata());
        String recordId = APP_WITH_WRITE_PERMS_ONLY.insertRecords(record).get(0);

        List<StepsRecord> records =
                APP_WITH_WRITE_PERMS_ONLY.readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(StepsRecord.class)
                                .addDataOrigins(
                                        getDataOrigin(APP_WITH_WRITE_PERMS_ONLY.getPackageName()))
                                .build());

        assertThat(records).hasSize(1);
        assertThat(records.get(0))
                .isEqualTo(
                        getStepsRecord(
                                getMetadataForId(
                                        recordId,
                                        getDataOrigin(
                                                APP_WITH_WRITE_PERMS_ONLY.getPackageName()))));
    }

    @Test
    public void testAppWithWritePermsOnly_readDataFromAllApps_throwsError() throws Exception {
        StepsRecord record = getStepsRecord(getEmptyMetadata());
        APP_A_WITH_READ_WRITE_PERMS.insertRecords(record).get(0);

        HealthConnectException e =
                assertThrows(
                        HealthConnectException.class,
                        () ->
                                APP_WITH_WRITE_PERMS_ONLY.readRecords(
                                        new ReadRecordsRequestUsingFilters.Builder<>(
                                                        StepsRecord.class)
                                                .build()));
        assertThat(e.getErrorCode()).isEqualTo(HealthConnectException.ERROR_SECURITY);
    }

    @Test
    public void testAppWithWritePermsOnly_readDataFromOtherApps_throwsError() throws Exception {
        StepsRecord record = getStepsRecord(getEmptyMetadata());
        APP_A_WITH_READ_WRITE_PERMS.insertRecords(record);

        HealthConnectException e =
                assertThrows(
                        HealthConnectException.class,
                        () ->
                                APP_WITH_WRITE_PERMS_ONLY.readRecords(
                                        new ReadRecordsRequestUsingFilters.Builder<>(
                                                        StepsRecord.class)
                                                .addDataOrigins(
                                                        getDataOrigin(
                                                                APP_WITH_WRITE_PERMS_ONLY
                                                                        .getPackageName()))
                                                .addDataOrigins(
                                                        getDataOrigin(
                                                                APP_A_WITH_READ_WRITE_PERMS
                                                                        .getPackageName()))
                                                .build()));
        assertThat(e.getErrorCode()).isEqualTo(HealthConnectException.ERROR_SECURITY);
    }

    @Test
    public void testAppWithReadPerms_readOtherAppsDataByIds_expectSuccess() throws Exception {
        List<Pair<Class<? extends Record>, String>> classAndIdPairs =
                List.of(
                        insertRecord(APP_WITH_WRITE_PERMS_ONLY, getStepsRecordWithEmptyMetaData()),
                        insertRecord(
                                APP_WITH_WRITE_PERMS_ONLY, getHeartRateRecordWithEmptyMetadata()),
                        insertRecord(
                                APP_WITH_WRITE_PERMS_ONLY, buildSleepSessionWithEmptyMetadata()),
                        insertRecord(
                                APP_WITH_WRITE_PERMS_ONLY, getDistanceRecordWithEmptyMetadata()),
                        insertRecord(
                                APP_WITH_WRITE_PERMS_ONLY,
                                getTotalCaloriesBurnedRecordWithEmptyMetadata()),
                        insertRecord(
                                APP_WITH_WRITE_PERMS_ONLY, buildExerciseSessionWithEmptyMetadata()),
                        insertRecord(
                                APP_A_WITH_READ_WRITE_PERMS, getStepsRecordWithEmptyMetaData()),
                        insertRecord(
                                APP_A_WITH_READ_WRITE_PERMS, getHeartRateRecordWithEmptyMetadata()),
                        insertRecord(
                                APP_A_WITH_READ_WRITE_PERMS, buildSleepSessionWithEmptyMetadata()),
                        insertRecord(
                                APP_A_WITH_READ_WRITE_PERMS, getDistanceRecordWithEmptyMetadata()),
                        insertRecord(
                                APP_A_WITH_READ_WRITE_PERMS,
                                getTotalCaloriesBurnedRecordWithEmptyMetadata()),
                        insertRecord(
                                APP_A_WITH_READ_WRITE_PERMS,
                                buildExerciseSessionWithEmptyMetadata()));

        List<Record> readRecords = new ArrayList<>();
        for (var classAndId : classAndIdPairs) {
            readRecords.addAll(
                    APP_A_WITH_READ_WRITE_PERMS.readRecords(
                            new ReadRecordsRequestUsingIds.Builder<>(classAndId.first)
                                    .addId(classAndId.second)
                                    .build()));
        }

        List<String> readIds =
                readRecords.stream().map(Record::getMetadata).map(Metadata::getId).toList();
        List<String> insertedIds = classAndIdPairs.stream().map(pair -> pair.second).toList();
        assertThat(readIds).containsExactlyElementsIn(insertedIds);
    }

    @Test
    public void testAppWithReadPerms_readOtherAppsDataByFilters_expectSuccess() throws Exception {
        List<String> insertedRecordIds =
                List.of(
                        APP_WITH_WRITE_PERMS_ONLY.insertRecord(getStepsRecordWithEmptyMetaData()),
                        APP_WITH_WRITE_PERMS_ONLY.insertRecord(
                                getHeartRateRecordWithEmptyMetadata()),
                        APP_WITH_WRITE_PERMS_ONLY.insertRecord(
                                buildSleepSessionWithEmptyMetadata()),
                        APP_WITH_WRITE_PERMS_ONLY.insertRecord(
                                getDistanceRecordWithEmptyMetadata()),
                        APP_WITH_WRITE_PERMS_ONLY.insertRecord(
                                getTotalCaloriesBurnedRecordWithEmptyMetadata()),
                        APP_WITH_WRITE_PERMS_ONLY.insertRecord(
                                buildExerciseSessionWithEmptyMetadata()),
                        APP_A_WITH_READ_WRITE_PERMS.insertRecord(getStepsRecordWithEmptyMetaData()),
                        APP_A_WITH_READ_WRITE_PERMS.insertRecord(
                                getHeartRateRecordWithEmptyMetadata()),
                        APP_A_WITH_READ_WRITE_PERMS.insertRecord(
                                buildSleepSessionWithEmptyMetadata()),
                        APP_A_WITH_READ_WRITE_PERMS.insertRecord(
                                getDistanceRecordWithEmptyMetadata()),
                        APP_A_WITH_READ_WRITE_PERMS.insertRecord(
                                getTotalCaloriesBurnedRecordWithEmptyMetadata()),
                        APP_A_WITH_READ_WRITE_PERMS.insertRecord(
                                buildExerciseSessionWithEmptyMetadata()));

        List<String> readRecordIds = new ArrayList<>();
        List<String> packageNameFilters =
                List.of(
                        APP_A_WITH_READ_WRITE_PERMS.getPackageName(),
                        APP_WITH_WRITE_PERMS_ONLY.getPackageName());
        List<Class<? extends Record>> classes =
                List.of(
                        StepsRecord.class,
                        HeartRateRecord.class,
                        DistanceRecord.class,
                        TotalCaloriesBurnedRecord.class,
                        ExerciseSessionRecord.class,
                        SleepSessionRecord.class);
        for (Class<? extends Record> clazz : classes) {
            readRecordIds.addAll(
                    getRecordIds(
                            APP_A_WITH_READ_WRITE_PERMS.readRecords(
                                    createReadRecordsRequestUsingFilters(
                                            clazz, packageNameFilters))));
        }

        assertThat(readRecordIds).containsExactlyElementsIn(insertedRecordIds);
    }

    @Test
    public void testAppWithWritePermsOnly_readDataByIdForOwnApp_success() throws Exception {
        List<Record> ownRecords = TestUtils.insertRecords(List.of(STEPS_1000, STEPS_2000));
        List<String> ownRecordIds =
                ownRecords.stream().map(record -> record.getMetadata().getId()).toList();

        List<Record> readRecords =
                TestUtils.readRecords(
                        new ReadRecordsRequestUsingIds.Builder(StepsRecord.class)
                                .addId(ownRecordIds.get(0))
                                .addId(ownRecordIds.get(1))
                                .build());

        assertThat(
                        readRecords.stream()
                                .map(record -> record.getMetadata().getClientRecordId())
                                .toList())
                .containsExactly(STEPS_1000_CLIENT_ID, STEPS_2000_CLIENT_ID);
    }

    // TODO(b/309778116): Consider throwing an error in this case.
    @Test
    public void testAppWithWritePermsOnly_readDataByIdForOtherApps_filtersOutOtherAppData()
            throws Exception {
        StepsRecord otherAppRecord = getStepsRecord(getEmptyMetadata());
        String otherAppRecordId = APP_A_WITH_READ_WRITE_PERMS.insertRecords(otherAppRecord).get(0);

        List<Record> ownRecords = TestUtils.insertRecords(List.of(STEPS_1000, STEPS_2000));
        List<String> ownRecordIds =
                ownRecords.stream().map(record -> record.getMetadata().getId()).toList();

        List<Record> readRecords =
                TestUtils.readRecords(
                        new ReadRecordsRequestUsingIds.Builder(StepsRecord.class)
                                .addId(ownRecordIds.get(0))
                                .addId(ownRecordIds.get(1))
                                .addId(otherAppRecordId)
                                .build());

        assertThat(
                        readRecords.stream()
                                .map(record -> record.getMetadata().getClientRecordId())
                                .collect(Collectors.toList()))
                .containsExactly(STEPS_1000_CLIENT_ID, STEPS_2000_CLIENT_ID);
    }

    @Test
    public void testAggregateRecords_onlyWritePermissions_requestsOwnDataOnly_succeeds()
            throws Exception {
        insertRecordsForPriority(mContext.getPackageName());
        List<DataOrigin> dataOriginPrioOrder =
                List.of(new DataOrigin.Builder().setPackageName(mContext.getPackageName()).build());

        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        uiAutomation.adoptShellPermissionIdentity(MANAGE_HEALTH_DATA);
        updateDataOriginPriorityOrder(
                new UpdateDataOriginPriorityOrderRequest(
                        dataOriginPrioOrder, HealthDataCategory.ACTIVITY));
        List<String> oldPriorityList =
                fetchDataOriginsPriorityOrder(HealthDataCategory.ACTIVITY)
                        .getDataOriginsPriorityOrder()
                        .stream()
                        .map(dataOrigin -> dataOrigin.getPackageName())
                        .collect(Collectors.toList());

        assertThat(oldPriorityList).contains(mContext.getPackageName());
        uiAutomation.dropShellPermissionIdentity();

        AggregateRecordsResponse<Long> response =
                TestUtils.getAggregateResponse(
                        new AggregateRecordsRequest.Builder<Long>(
                                        new TimeInstantRangeFilter.Builder()
                                                .setStartTime(Instant.ofEpochMilli(0))
                                                .setEndTime(Instant.now().plus(1, ChronoUnit.DAYS))
                                                .build())
                                .addAggregationType(WRITE_ONLY_PERM_AGGREGATION_STEPS_TOTAL)
                                .addDataOriginsFilter(
                                        new DataOrigin.Builder()
                                                .setPackageName(mContext.getPackageName())
                                                .build())
                                .build(),
                        /* recordsToInsert= */ List.of(STEPS_1000, STEPS_2000));
        assertThat(response.get(WRITE_ONLY_PERM_AGGREGATION_STEPS_TOTAL))
                .isEqualTo(STEPS_1000.getCount() + STEPS_2000.getCount());
    }

    @Test
    public void testAggregateRecords_onlyWritePermissions_requestsOthersData_throwsHcException()
            throws InterruptedException {
        try {
            TestUtils.getAggregateResponse(
                    new AggregateRecordsRequest.Builder<Long>(
                                    new TimeInstantRangeFilter.Builder()
                                            .setStartTime(Instant.ofEpochMilli(0))
                                            .setEndTime(Instant.now().plus(1, ChronoUnit.DAYS))
                                            .build())
                            .addAggregationType(WRITE_ONLY_PERM_AGGREGATION_STEPS_TOTAL)
                            .addDataOriginsFilter(
                                    new DataOrigin.Builder()
                                            .setPackageName(mContext.getPackageName())
                                            .build())
                            .addDataOriginsFilter(
                                    new DataOrigin.Builder()
                                            .setPackageName(
                                                    APP_B_WITH_READ_WRITE_PERMS.getPackageName())
                                            .build())
                            .build(),
                    /* recordsToInsert= */ List.of(STEPS_1000, STEPS_2000));
            fail("Expected to fail with HealthConnectException but didn't");
        } catch (HealthConnectException e) {
            assertThat(e.getErrorCode()).isEqualTo(HealthConnectException.ERROR_SECURITY);
        }
    }

    @Test
    public void testAggregateRecords_onlyWritePermissions_allDataRequested_throwsHcException()
            throws InterruptedException {
        try {
            TestUtils.getAggregateResponse(
                    new AggregateRecordsRequest.Builder<Long>(
                                    new TimeInstantRangeFilter.Builder()
                                            .setStartTime(Instant.ofEpochMilli(0))
                                            .setEndTime(Instant.now().plus(1, ChronoUnit.DAYS))
                                            .build())
                            .addAggregationType(WRITE_ONLY_PERM_AGGREGATION_STEPS_TOTAL)
                            .build(),
                    /* recordsToInsert= */ List.of(STEPS_1000, STEPS_2000));
            fail("Expected to fail with HealthConnectException but didn't");
        } catch (HealthConnectException e) {
            assertThat(e.getErrorCode()).isEqualTo(HealthConnectException.ERROR_SECURITY);
        }
    }

    @Test
    public void
            testAggregateRecords_someReadAndWritePermissions_requestsOthersData_throwsHcException()
                    throws InterruptedException {
        try {
            TestUtils.getAggregateResponse(
                    new AggregateRecordsRequest.Builder<Long>(
                                    new TimeInstantRangeFilter.Builder()
                                            .setStartTime(Instant.ofEpochMilli(0))
                                            .setEndTime(Instant.now().plus(1, ChronoUnit.DAYS))
                                            .build())
                            .addAggregationType(WRITE_ONLY_PERM_AGGREGATION_STEPS_TOTAL)
                            .addAggregationType(READ_PERM_AGGREGATION_EXERCISE_DURATION_TOTAL)
                            .addDataOriginsFilter(
                                    new DataOrigin.Builder()
                                            .setPackageName(mContext.getPackageName())
                                            .build())
                            .addDataOriginsFilter(
                                    new DataOrigin.Builder()
                                            .setPackageName(
                                                    APP_B_WITH_READ_WRITE_PERMS.getPackageName())
                                            .build())
                            .build(),
                    /* recordsToInsert= */ List.of(STEPS_1000, STEPS_2000));
            fail("Expected to fail with HealthConnectException but didn't");
        } catch (HealthConnectException e) {
            assertThat(e.getErrorCode()).isEqualTo(HealthConnectException.ERROR_SECURITY);
        }
    }

    @Test
    public void testAppWithManageHealthDataPermsOnlyCantInsertRecords() {
        StepsRecord record = getStepsRecord(getEmptyMetadata());

        HealthConnectException e =
                assertThrows(
                        HealthConnectException.class,
                        () -> APP_WITH_DATA_MANAGE_PERMS_ONLY.insertRecords(record));

        assertThat(e.getErrorCode()).isEqualTo(HealthConnectException.ERROR_SECURITY);
    }

    @Test
    public void testAppWithManageHealthDataPermsOnlyCantUpdateRecords() throws Exception {
        StepsRecord record = getStepsRecord(getEmptyMetadata());
        String recordId = APP_WITH_WRITE_PERMS_ONLY.insertRecords(record).get(0);
        StepsRecord updatedRecord = getStepsRecord(getMetadataForId(recordId));

        HealthConnectException e =
                assertThrows(
                        HealthConnectException.class,
                        () -> APP_WITH_DATA_MANAGE_PERMS_ONLY.updateRecords(updatedRecord));

        assertThat(e.getErrorCode()).isEqualTo(HealthConnectException.ERROR_SECURITY);
    }

    @Test
    public void testTwoAppsCanUseSameClientRecordIdsToInsert() throws Exception {
        StepsRecord record = getStepsRecord(getMetadataForClientId("common.client.id"));

        APP_A_WITH_READ_WRITE_PERMS.insertRecords(record);
        APP_B_WITH_READ_WRITE_PERMS.insertRecords(record);
    }

    @Test
    public void testAppCanReadRecordsUsingDataOriginFilters() throws Exception {
        APP_A_WITH_READ_WRITE_PERMS.insertRecords(TEST_RECORDS);

        List<BasalMetabolicRateRecord> basalMetabolicRateRecords =
                APP_A_WITH_READ_WRITE_PERMS.readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(BasalMetabolicRateRecord.class)
                                .build());
        List<ExerciseSessionRecord> exerciseSessionRecords =
                APP_A_WITH_READ_WRITE_PERMS.readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(ExerciseSessionRecord.class)
                                .build());
        List<HeartRateRecord> heartRateRecords =
                APP_A_WITH_READ_WRITE_PERMS.readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(HeartRateRecord.class)
                                .build());
        List<StepsRecord> stepsRecords =
                APP_A_WITH_READ_WRITE_PERMS.readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(StepsRecord.class).build());

        assertThat(basalMetabolicRateRecords).hasSize(1);
        assertThat(exerciseSessionRecords).hasSize(1);
        assertThat(heartRateRecords).hasSize(1);
        assertThat(stepsRecords).hasSize(1);
    }

    @Test
    public void testAppWithReadPerms_getChangeTokensAndLogsOfOtherApps_expectSuccess()
            throws Exception {
        String changeLogTokenForAppB =
                APP_B_WITH_READ_WRITE_PERMS.getChangeLogToken(
                        new ChangeLogTokenRequest.Builder()
                                .addRecordType(StepsRecord.class)
                                .addRecordType(HeartRateRecord.class)
                                .addRecordType(BasalMetabolicRateRecord.class)
                                .addRecordType(ExerciseSessionRecord.class)
                                .addDataOriginFilter(
                                        getDataOrigin(APP_A_WITH_READ_WRITE_PERMS.getPackageName()))
                                .addRecordType(StepsRecord.class)
                                .addRecordType(HeartRateRecord.class)
                                .addRecordType(DistanceRecord.class)
                                .addRecordType(TotalCaloriesBurnedRecord.class)
                                .addRecordType(SleepSessionRecord.class)
                                .addRecordType(ExerciseSessionRecord.class)
                                .build());
        String changeLogTokenForAppA =
                APP_A_WITH_READ_WRITE_PERMS.getChangeLogToken(
                        new ChangeLogTokenRequest.Builder()
                                .addRecordType(StepsRecord.class)
                                .addRecordType(HeartRateRecord.class)
                                .addRecordType(BasalMetabolicRateRecord.class)
                                .addRecordType(ExerciseSessionRecord.class)
                                .addDataOriginFilter(
                                        getDataOrigin(APP_B_WITH_READ_WRITE_PERMS.getPackageName()))
                                .build());
        List<Record> recordsB = TEST_RECORDS;
        List<Record> recordsA =
                List.of(
                        getStepsRecord(getEmptyMetadata()),
                        getHeartRateRecord(getEmptyMetadata()),
                        getDistanceRecordWithEmptyMetadata(),
                        buildExerciseSessionWithEmptyMetadata(),
                        buildSleepSessionWithEmptyMetadata(),
                        getTotalCaloriesBurnedRecordWithEmptyMetadata());
        List<String> recordIdsA = APP_A_WITH_READ_WRITE_PERMS.insertRecords(recordsA);
        List<String> recordIdsB = APP_B_WITH_READ_WRITE_PERMS.insertRecords(recordsB);
        APP_B_WITH_READ_WRITE_PERMS.deleteRecords(getRecordIdFilters(recordIdsB, recordsB));

        ChangeLogsResponse responseB =
                APP_B_WITH_READ_WRITE_PERMS.getChangeLogs(
                        new ChangeLogsRequest.Builder(changeLogTokenForAppB).build());
        ChangeLogsResponse responseA =
                APP_A_WITH_READ_WRITE_PERMS.getChangeLogs(
                        new ChangeLogsRequest.Builder(changeLogTokenForAppA).build());

        assertThat(responseB.getUpsertedRecords()).hasSize(recordsA.size());
        assertThat(
                        responseB.getUpsertedRecords().stream()
                                .map(Record::getMetadata)
                                .map(Metadata::getId)
                                .toList())
                .containsExactlyElementsIn(recordIdsA);
        assertThat(responseB.getDeletedLogs()).isEmpty();
        assertThat(responseA.getUpsertedRecords()).isEmpty();
        assertThat(responseA.getDeletedLogs()).hasSize(recordsB.size());
    }

    @Test
    public void testGrantingCorrectPermsPutsTheAppInPriorityList() throws InterruptedException {
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();

        uiAutomation.adoptShellPermissionIdentity(MANAGE_HEALTH_DATA);
        List<String> oldPriorityList =
                fetchDataOriginsPriorityOrder(HealthDataCategory.ACTIVITY)
                        .getDataOriginsPriorityOrder()
                        .stream()
                        .map(DataOrigin::getPackageName)
                        .toList();

        List<String> healthPerms =
                getGrantedHealthPermissions(APP_A_WITH_READ_WRITE_PERMS.getPackageName());

        revokeHealthPermissions(APP_A_WITH_READ_WRITE_PERMS.getPackageName());

        for (String perm : healthPerms) {
            grantPermission(APP_A_WITH_READ_WRITE_PERMS.getPackageName(), perm);
        }

        uiAutomation.adoptShellPermissionIdentity(MANAGE_HEALTH_DATA);
        List<String> newPriorityList =
                fetchDataOriginsPriorityOrder(HealthDataCategory.ACTIVITY)
                        .getDataOriginsPriorityOrder()
                        .stream()
                        .map(DataOrigin::getPackageName)
                        .toList();

        assertThat(newPriorityList).hasSize(oldPriorityList.size() + 1);
        assertThat(newPriorityList).contains(APP_A_WITH_READ_WRITE_PERMS.getPackageName());
    }

    @Test
    public void testRevokingOnlyOneCorrectPermissionDoesntRemoveAppFromPriorityList()
            throws InterruptedException {
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();

        List<String> healthPerms =
                getGrantedHealthPermissions(APP_A_WITH_READ_WRITE_PERMS.getPackageName());

        revokeHealthPermissions(APP_A_WITH_READ_WRITE_PERMS.getPackageName());

        for (String perm : healthPerms) {
            grantPermission(APP_A_WITH_READ_WRITE_PERMS.getPackageName(), perm);
        }

        uiAutomation.adoptShellPermissionIdentity(MANAGE_HEALTH_DATA);
        List<String> oldPriorityList =
                fetchDataOriginsPriorityOrder(HealthDataCategory.ACTIVITY)
                        .getDataOriginsPriorityOrder()
                        .stream()
                        .map(dataOrigin -> dataOrigin.getPackageName())
                        .collect(Collectors.toList());

        assertThat(oldPriorityList).contains(APP_A_WITH_READ_WRITE_PERMS.getPackageName());

        revokePermission(APP_A_WITH_READ_WRITE_PERMS.getPackageName(), healthPerms.get(0));

        uiAutomation.adoptShellPermissionIdentity(MANAGE_HEALTH_DATA);
        List<String> newPriorityList =
                fetchDataOriginsPriorityOrder(HealthDataCategory.ACTIVITY)
                        .getDataOriginsPriorityOrder()
                        .stream()
                        .map(dataOrigin -> dataOrigin.getPackageName())
                        .collect(Collectors.toList());

        assertThat(newPriorityList).contains(APP_A_WITH_READ_WRITE_PERMS.getPackageName());

        grantPermission(APP_A_WITH_READ_WRITE_PERMS.getPackageName(), healthPerms.get(0));
    }

    @Test
    public void testRevokingAllCorrectPermissionsRemovesAppFromPriorityList()
            throws InterruptedException {
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();

        List<String> healthPerms =
                getGrantedHealthPermissions(APP_A_WITH_READ_WRITE_PERMS.getPackageName());

        revokeHealthPermissions(APP_A_WITH_READ_WRITE_PERMS.getPackageName());

        for (String perm : healthPerms) {
            grantPermission(APP_A_WITH_READ_WRITE_PERMS.getPackageName(), perm);
        }

        uiAutomation.adoptShellPermissionIdentity(MANAGE_HEALTH_DATA);
        List<String> oldPriorityList =
                fetchDataOriginsPriorityOrder(HealthDataCategory.ACTIVITY)
                        .getDataOriginsPriorityOrder()
                        .stream()
                        .map(dataOrigin -> dataOrigin.getPackageName())
                        .collect(Collectors.toList());

        assertThat(oldPriorityList).contains(APP_A_WITH_READ_WRITE_PERMS.getPackageName());

        for (String perm : healthPerms) {
            revokePermission(APP_A_WITH_READ_WRITE_PERMS.getPackageName(), perm);
        }

        uiAutomation.adoptShellPermissionIdentity(MANAGE_HEALTH_DATA);
        List<String> newPriorityList =
                fetchDataOriginsPriorityOrder(HealthDataCategory.ACTIVITY)
                        .getDataOriginsPriorityOrder()
                        .stream()
                        .map(dataOrigin -> dataOrigin.getPackageName())
                        .collect(Collectors.toList());

        assertThat(newPriorityList.contains(APP_A_WITH_READ_WRITE_PERMS.getPackageName()))
                .isFalse();

        for (String perm : healthPerms) {
            grantPermission(APP_A_WITH_READ_WRITE_PERMS.getPackageName(), perm);
        }
    }

    @Test
    public void testSelfRevokePermissions_revokedOnKill() throws Exception {
        grantPermission(APP_A_WITH_READ_WRITE_PERMS.getPackageName(), APP_A_DECLARED_PERMISSION);

        APP_A_WITH_READ_WRITE_PERMS.selfRevokePermission(APP_A_DECLARED_PERMISSION);
        APP_A_WITH_READ_WRITE_PERMS.kill();

        eventually(
                () ->
                        assertThat(
                                        mContext.getPackageManager()
                                                .checkPermission(
                                                        APP_A_DECLARED_PERMISSION,
                                                        APP_A_WITH_READ_WRITE_PERMS
                                                                .getPackageName()))
                                .isEqualTo(PackageManager.PERMISSION_DENIED),
                /* timeoutMillis= */ 7000);
    }

    @Test
    public void testAppWithManageHealthDataPermissionCanUpdatePriority()
            throws InterruptedException {
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();

        List<String> healthPerms =
                getGrantedHealthPermissions(APP_A_WITH_READ_WRITE_PERMS.getPackageName());

        revokeHealthPermissions(APP_A_WITH_READ_WRITE_PERMS.getPackageName());
        revokeHealthPermissions(APP_B_WITH_READ_WRITE_PERMS.getPackageName());

        for (String perm : healthPerms) {
            grantPermission(APP_A_WITH_READ_WRITE_PERMS.getPackageName(), perm);
        }

        for (String perm : healthPerms) {
            grantPermission(APP_B_WITH_READ_WRITE_PERMS.getPackageName(), perm);
        }

        List<DataOrigin> dataOriginPrioOrder =
                List.of(
                        new DataOrigin.Builder()
                                .setPackageName(APP_B_WITH_READ_WRITE_PERMS.getPackageName())
                                .build(),
                        new DataOrigin.Builder()
                                .setPackageName(APP_A_WITH_READ_WRITE_PERMS.getPackageName())
                                .build());

        uiAutomation.adoptShellPermissionIdentity(MANAGE_HEALTH_DATA);
        updateDataOriginPriorityOrder(
                new UpdateDataOriginPriorityOrderRequest(
                        dataOriginPrioOrder, HealthDataCategory.ACTIVITY));

        uiAutomation.adoptShellPermissionIdentity(MANAGE_HEALTH_DATA);
        List<String> newPriorityList =
                fetchDataOriginsPriorityOrder(HealthDataCategory.ACTIVITY)
                        .getDataOriginsPriorityOrder()
                        .stream()
                        .map(dataOrigin -> dataOrigin.getPackageName())
                        .collect(Collectors.toList());

        assertThat(
                        newPriorityList.equals(
                                dataOriginPrioOrder.stream()
                                        .map(dataOrigin -> dataOrigin.getPackageName())
                                        .collect(Collectors.toList())))
                .isTrue();
    }

    @Test
    public void testAppWithManageHealthDataPermsCanReadAnotherAppEntry() throws Exception {
        StepsRecord record = getStepsRecord(getEmptyMetadata());
        APP_A_WITH_READ_WRITE_PERMS.insertRecords(record);
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        uiAutomation.adoptShellPermissionIdentity(MANAGE_HEALTH_DATA);

        List<StepsRecord> recordsRead =
                readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(StepsRecord.class).build(),
                        ApplicationProvider.getApplicationContext());

        assertThat(recordsRead).hasSize(1);
    }

    @Test
    public void testAppWithManageHealthDataPermsCanDeleteAnotherAppEntry() throws Exception {
        StepsRecord record = getStepsRecord(getEmptyMetadata());
        String recordId = APP_A_WITH_READ_WRITE_PERMS.insertRecords(record).get(0);
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        uiAutomation.adoptShellPermissionIdentity(MANAGE_HEALTH_DATA);

        verifyDeleteRecords(List.of(RecordIdFilter.fromId(StepsRecord.class, recordId)));
    }

    @Test
    public void testToVerifyGetContributorApplicationsInfo() throws Exception {
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();

        APP_A_WITH_READ_WRITE_PERMS.insertRecords(TEST_RECORDS);
        APP_B_WITH_READ_WRITE_PERMS.insertRecords(TEST_RECORDS);

        List<String> pkgNameList =
                List.of(
                        APP_A_WITH_READ_WRITE_PERMS.getPackageName(),
                        APP_B_WITH_READ_WRITE_PERMS.getPackageName());

        uiAutomation.adoptShellPermissionIdentity(MANAGE_HEALTH_DATA);

        // Contributor information is updated asynchronously, so retry with delay until the update
        // finishes (or we run out of retries).
        for (int i = 1; i <= ASYNC_RETRIES; i++) {
            List<String> appInfoList =
                    getApplicationInfo().stream().map(AppInfo::getPackageName).toList();

            try {
                assertThat(appInfoList).containsAtLeastElementsIn(pkgNameList);
            } catch (AssertionError e) {
                if (i < ASYNC_RETRIES) {
                    Thread.sleep(ASYNC_RETRY_DELAY_MILLIS);
                    continue;
                }

                throw new AssertionError(e);
            }
        }
    }

    @Test
    public void testAggregationOutputForTotalStepsCountWithDataFromTwoAppsHavingDifferentPriority()
            throws Exception {
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();

        revokeAndThenGrantHealthPermissions(APP_A_WITH_READ_WRITE_PERMS.getPackageName());
        revokeAndThenGrantHealthPermissions(APP_B_WITH_READ_WRITE_PERMS.getPackageName());

        List<DataOrigin> dataOriginPrioOrder =
                getDataOrigins(
                        APP_A_WITH_READ_WRITE_PERMS.getPackageName(),
                        APP_B_WITH_READ_WRITE_PERMS.getPackageName());

        uiAutomation.adoptShellPermissionIdentity(MANAGE_HEALTH_DATA);
        List<String> priorityList =
                fetchDataOriginsPriorityOrder(HealthDataCategory.ACTIVITY)
                        .getDataOriginsPriorityOrder()
                        .stream()
                        .map(DataOrigin::getPackageName)
                        .toList();

        assertThat(
                        priorityList.equals(
                                dataOriginPrioOrder.stream()
                                        .map(DataOrigin::getPackageName)
                                        .toList()))
                .isTrue();

        StepsRecord stepsRecordA =
                new StepsRecord.Builder(
                                getEmptyMetadata(),
                                yesterdayAt("13:00"),
                                yesterdayAt("15:00"),
                                1000)
                        .build();
        APP_A_WITH_READ_WRITE_PERMS.insertRecords(stepsRecordA);
        StepsRecord stepsRecordB =
                new StepsRecord.Builder(
                                getEmptyMetadata(),
                                yesterdayAt("14:00"),
                                yesterdayAt("16:00"),
                                2000)
                        .build();
        APP_B_WITH_READ_WRITE_PERMS.insertRecords(stepsRecordB);

        AggregateRecordsRequest<Long> aggregateRecordsRequest =
                new AggregateRecordsRequest.Builder<Long>(
                                new TimeInstantRangeFilter.Builder()
                                        .setStartTime(yesterdayAt("13:00"))
                                        .setEndTime(yesterdayAt("16:00"))
                                        .build())
                        .addAggregationType(STEPS_COUNT_TOTAL)
                        .build();
        assertThat(aggregateRecordsRequest.getAggregationTypes()).isNotNull();
        assertThat(aggregateRecordsRequest.getTimeRangeFilter()).isNotNull();
        assertThat(aggregateRecordsRequest.getDataOriginsFilters()).isNotNull();

        AggregateRecordsResponse<Long> oldResponse = getAggregateResponse(aggregateRecordsRequest);
        assertThat(oldResponse.get(STEPS_COUNT_TOTAL)).isNotNull();
        assertThat(oldResponse.get(STEPS_COUNT_TOTAL)).isEqualTo(2000);

        dataOriginPrioOrder =
                getDataOrigins(
                        APP_B_WITH_READ_WRITE_PERMS.getPackageName(),
                        APP_A_WITH_READ_WRITE_PERMS.getPackageName());

        uiAutomation.adoptShellPermissionIdentity(MANAGE_HEALTH_DATA);
        updateDataOriginPriorityOrder(
                new UpdateDataOriginPriorityOrderRequest(
                        dataOriginPrioOrder, HealthDataCategory.ACTIVITY));

        uiAutomation.adoptShellPermissionIdentity(MANAGE_HEALTH_DATA);
        priorityList =
                fetchDataOriginsPriorityOrder(HealthDataCategory.ACTIVITY)
                        .getDataOriginsPriorityOrder()
                        .stream()
                        .map(dataOrigin -> dataOrigin.getPackageName())
                        .collect(Collectors.toList());

        assertThat(
                        priorityList.equals(
                                dataOriginPrioOrder.stream()
                                        .map(dataOrigin -> dataOrigin.getPackageName())
                                        .collect(Collectors.toList())))
                .isTrue();

        AggregateRecordsResponse<Long> newResponse = getAggregateResponse(aggregateRecordsRequest);
        assertThat(newResponse.get(STEPS_COUNT_TOTAL)).isNotNull();
        assertThat(newResponse.get(STEPS_COUNT_TOTAL)).isEqualTo(2500);
    }

    @Test
    public void testAggregationOutputForExerciseSessionWithDataFromTwoAppsHavingDifferentPriority()
            throws Exception {
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();

        revokeAndThenGrantHealthPermissions(APP_A_WITH_READ_WRITE_PERMS.getPackageName());
        revokeAndThenGrantHealthPermissions(APP_B_WITH_READ_WRITE_PERMS.getPackageName());

        List<DataOrigin> dataOriginPrioOrder =
                getDataOrigins(
                        APP_A_WITH_READ_WRITE_PERMS.getPackageName(),
                        APP_B_WITH_READ_WRITE_PERMS.getPackageName());

        uiAutomation.adoptShellPermissionIdentity(MANAGE_HEALTH_DATA);
        List<String> priorityList =
                fetchDataOriginsPriorityOrder(HealthDataCategory.ACTIVITY)
                        .getDataOriginsPriorityOrder()
                        .stream()
                        .map(DataOrigin::getPackageName)
                        .toList();

        assertThat(
                        priorityList.equals(
                                dataOriginPrioOrder.stream()
                                        .map(DataOrigin::getPackageName)
                                        .toList()))
                .isTrue();

        ExerciseSessionRecord sessionRecordA =
                new ExerciseSessionRecord.Builder(
                                getEmptyMetadata(),
                                yesterdayAt("13:00"),
                                yesterdayAt("15:00"),
                                EXERCISE_SESSION_TYPE_RUNNING)
                        .setSegments(
                                List.of(
                                        new ExerciseSegment.Builder(
                                                        yesterdayAt("14:00"),
                                                        yesterdayAt("15:00"),
                                                        EXERCISE_SEGMENT_TYPE_PAUSE)
                                                .build()))
                        .build();
        APP_A_WITH_READ_WRITE_PERMS.insertRecords(sessionRecordA);

        ExerciseSessionRecord sessionRecordB =
                new ExerciseSessionRecord.Builder(
                                getEmptyMetadata(),
                                yesterdayAt("14:00"),
                                yesterdayAt("15:00"),
                                EXERCISE_SESSION_TYPE_RUNNING)
                        .build();
        APP_B_WITH_READ_WRITE_PERMS.insertRecords(sessionRecordB);

        AggregateRecordsRequest<Long> aggregateRecordsRequest =
                new AggregateRecordsRequest.Builder<Long>(
                                new TimeInstantRangeFilter.Builder()
                                        .setStartTime(yesterdayAt("13:00"))
                                        .setEndTime(yesterdayAt("15:00"))
                                        .build())
                        .addAggregationType(EXERCISE_DURATION_TOTAL)
                        .build();
        assertThat(aggregateRecordsRequest.getAggregationTypes()).isNotNull();
        assertThat(aggregateRecordsRequest.getTimeRangeFilter()).isNotNull();
        assertThat(aggregateRecordsRequest.getDataOriginsFilters()).isNotNull();

        AggregateRecordsResponse<Long> response = getAggregateResponse(aggregateRecordsRequest);
        assertThat(response.get(EXERCISE_DURATION_TOTAL)).isNotNull();
        assertThat(response.get(EXERCISE_DURATION_TOTAL))
                .isEqualTo(
                        Duration.between(yesterdayAt("13:00"), yesterdayAt("15:00"))
                                .minus(Duration.between(yesterdayAt("14:00"), yesterdayAt("15:00")))
                                .toMillis());

        dataOriginPrioOrder =
                getDataOrigins(
                        APP_B_WITH_READ_WRITE_PERMS.getPackageName(),
                        APP_A_WITH_READ_WRITE_PERMS.getPackageName());

        uiAutomation.adoptShellPermissionIdentity(MANAGE_HEALTH_DATA);
        updateDataOriginPriorityOrder(
                new UpdateDataOriginPriorityOrderRequest(
                        dataOriginPrioOrder, HealthDataCategory.ACTIVITY));

        uiAutomation.adoptShellPermissionIdentity(MANAGE_HEALTH_DATA);
        priorityList =
                fetchDataOriginsPriorityOrder(HealthDataCategory.ACTIVITY)
                        .getDataOriginsPriorityOrder()
                        .stream()
                        .map(DataOrigin::getPackageName)
                        .toList();

        assertThat(
                        priorityList.equals(
                                dataOriginPrioOrder.stream()
                                        .map(DataOrigin::getPackageName)
                                        .toList()))
                .isTrue();

        AggregateRecordsResponse<Long> newResponse = getAggregateResponse(aggregateRecordsRequest);
        assertThat(newResponse.get(EXERCISE_DURATION_TOTAL)).isNotNull();
        assertThat(newResponse.get(EXERCISE_DURATION_TOTAL))
                .isEqualTo(Duration.between(yesterdayAt("13:00"), yesterdayAt("15:00")).toMillis());
    }

    @Test
    public void testToVerifyNoPermissionChangeLog() throws Exception {
        ArrayList<String> recordClassesToRead = new ArrayList();
        recordClassesToRead.add(HeartRateRecord.class.getName());
        recordClassesToRead.add(StepsRecord.class.getName());

        String changeLogTokenForAppB =
                APP_B_WITH_READ_WRITE_PERMS.getChangeLogToken(
                        new ChangeLogTokenRequest.Builder()
                                .addDataOriginFilter(
                                        getDataOrigin(APP_A_WITH_READ_WRITE_PERMS.getPackageName()))
                                .addRecordType(HeartRateRecord.class)
                                .addRecordType(StepsRecord.class)
                                .build());

        APP_A_WITH_READ_WRITE_PERMS.insertRecords(TEST_RECORDS);

        List<String> healthPerms =
                getGrantedHealthPermissions(APP_B_WITH_READ_WRITE_PERMS.getPackageName());

        revokeHealthPermissions(APP_B_WITH_READ_WRITE_PERMS.getPackageName());

        HealthConnectException e =
                assertThrows(
                        HealthConnectException.class,
                        () ->
                                APP_B_WITH_READ_WRITE_PERMS.getChangeLogs(
                                        new ChangeLogsRequest.Builder(changeLogTokenForAppB)
                                                .build()));
        assertThat(e.getErrorCode()).isEqualTo(HealthConnectException.ERROR_SECURITY);

        e =
                assertThrows(
                        HealthConnectException.class,
                        () ->
                                APP_B_WITH_READ_WRITE_PERMS.getChangeLogToken(
                                        new ChangeLogTokenRequest.Builder()
                                                .addRecordType(HeartRateRecord.class)
                                                .addRecordType(StepsRecord.class)
                                                .addDataOriginFilter(
                                                        getDataOrigin(
                                                                APP_A_WITH_READ_WRITE_PERMS
                                                                        .getPackageName()))
                                                .build()));
        assertThat(e.getErrorCode()).isEqualTo(HealthConnectException.ERROR_SECURITY);

        for (String perm : healthPerms) {
            grantPermission(APP_B_WITH_READ_WRITE_PERMS.getPackageName(), perm);
        }
    }

    private static Pair<Class<? extends Record>, String> insertRecord(
            TestAppProxy testAppProxy, Record record) throws Exception {
        return new Pair<>(record.getClass(), testAppProxy.insertRecord(record));
    }

    private static StepsRecord getStepsRecord(
            int stepCount, Instant startTime, int durationInHours, String clientId) {
        return new StepsRecord.Builder(
                        getMetadataForClientId(
                                clientId,
                                getDataOrigin(APP_WITH_WRITE_PERMS_ONLY.getPackageName())),
                        startTime,
                        startTime.plus(durationInHours, ChronoUnit.HOURS),
                        stepCount)
                .build();
    }

    private static StepsRecord getStepsRecord(Metadata metadata) {
        Instant startTime = NOW.minus(ofMinutes(10));
        Instant endTime = NOW.minus(ofMinutes(5));
        return new StepsRecord.Builder(metadata, startTime, endTime, 155).build();
    }

    private static HeartRateRecord getHeartRateRecord(Metadata metadata) {
        Instant startTime = NOW.minus(ofMinutes(10));
        Instant endTime = NOW.minus(ofMinutes(5));
        return new HeartRateRecord.Builder(
                        metadata,
                        startTime,
                        endTime,
                        List.of(new HeartRateRecord.HeartRateSample(75, startTime.plusSeconds(5))))
                .build();
    }

    private static BasalMetabolicRateRecord getBasalMetabolicRateRecord(Metadata metadata) {
        Instant time = NOW.minus(ofMinutes(10));
        return new BasalMetabolicRateRecord.Builder(metadata, time, Power.fromWatts(10)).build();
    }

    private static ExerciseSessionRecord getExerciseSessionRecord(Metadata metadata) {
        Instant startTime = NOW.minus(ofMinutes(10));
        Instant endTime = NOW.minus(ofMinutes(5));
        return new ExerciseSessionRecord.Builder(
                        metadata, startTime, endTime, EXERCISE_SESSION_TYPE_RUNNING)
                .build();
    }
}
