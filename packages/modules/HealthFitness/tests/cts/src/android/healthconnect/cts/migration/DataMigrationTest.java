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

package android.healthconnect.cts.migration;

import static android.content.pm.PackageManager.GET_PERMISSIONS;
import static android.content.pm.PackageManager.PackageInfoFlags;
import static android.health.connect.HealthConnectDataState.MIGRATION_STATE_ALLOWED;
import static android.health.connect.HealthConnectDataState.MIGRATION_STATE_IDLE;
import static android.health.connect.HealthConnectDataState.MIGRATION_STATE_MODULE_UPGRADE_REQUIRED;
import static android.health.connect.HealthPermissions.READ_HEIGHT;
import static android.health.connect.HealthPermissions.WRITE_HEIGHT;
import static android.health.connect.datatypes.units.Length.fromMeters;
import static android.health.connect.datatypes.units.Power.fromWatts;

import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.fail;
import static org.junit.Assume.assumeFalse;

import static java.util.concurrent.Executors.newSingleThreadExecutor;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.health.connect.ApplicationInfoResponse;
import android.health.connect.DeleteUsingFiltersRequest;
import android.health.connect.FetchDataOriginsPriorityOrderResponse;
import android.health.connect.HealthConnectDataState;
import android.health.connect.HealthConnectException;
import android.health.connect.HealthConnectManager;
import android.health.connect.HealthDataCategory;
import android.health.connect.HealthPermissions;
import android.health.connect.ReadRecordsRequest;
import android.health.connect.ReadRecordsRequestUsingFilters;
import android.health.connect.ReadRecordsRequestUsingIds;
import android.health.connect.ReadRecordsResponse;
import android.health.connect.TimeInstantRangeFilter;
import android.health.connect.datatypes.AppInfo;
import android.health.connect.datatypes.DataOrigin;
import android.health.connect.datatypes.Device;
import android.health.connect.datatypes.HeightRecord;
import android.health.connect.datatypes.HydrationRecord;
import android.health.connect.datatypes.Metadata;
import android.health.connect.datatypes.NutritionRecord;
import android.health.connect.datatypes.PowerRecord;
import android.health.connect.datatypes.PowerRecord.PowerRecordSample;
import android.health.connect.datatypes.Record;
import android.health.connect.datatypes.StepsRecord;
import android.health.connect.datatypes.units.Length;
import android.health.connect.datatypes.units.Mass;
import android.health.connect.datatypes.units.Volume;
import android.health.connect.migration.AppInfoMigrationPayload;
import android.health.connect.migration.MetadataMigrationPayload;
import android.health.connect.migration.MigrationEntity;
import android.health.connect.migration.MigrationException;
import android.health.connect.migration.PermissionMigrationPayload;
import android.health.connect.migration.PriorityMigrationPayload;
import android.health.connect.migration.RecordMigrationPayload;
import android.healthconnect.cts.utils.AssumptionCheckerRule;
import android.healthconnect.cts.utils.TestUtils;
import android.os.Build;
import android.os.OutcomeReceiver;
import android.os.UserHandle;
import android.os.ext.SdkExtensions;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.google.common.truth.Expect;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@RunWith(AndroidJUnit4.class)
public class DataMigrationTest {

    private static final String PACKAGE_NAME = "android.healthconnect.cts";
    private static final String APP_PACKAGE_NAME = "android.healthconnect.cts.app";
    private static final String APP_PACKAGE_NAME_2 = "android.healthconnect.cts.app2";
    private static final String PACKAGE_NAME_NOT_INSTALLED = "not.installed.package";
    private static final String INVALID_PERMISSION_1 = "invalid.permission.1";
    private static final String INVALID_PERMISSION_2 = "invalid.permission.2";
    private static final String HEALTH_PERMISSION_PREFIX = "android.permission.health.";
    private static final String APP_NAME = "Test App";
    private static final String APP_NAME_NEW = "Test App 2";
    private static final String ENABLE_PAUSE_STATE_CHANGE_JOBS_FLAG =
            "enable_pause_state_change_jobs";
    private static final String ENABLE_COMPLETE_STATE_CHANGE_JOBS_FLAG =
            "enable_complete_state_change_jobs";
    private static final String ENABLE_PAUSE_STATE_CHANGE_JOB_DEFAULT_FLAG_VALUE = "true";
    private static final String ENABLE_COMPLETE_STATE_CHANGE_JOB_DEFAULT_FLAG_VALUE = "false";
    // DEFAULT_PAGE_SIZE should hold the same value as Constants#DEFAULT_PAGE_SIZE
    private static final int DEFAULT_PAGE_SIZE = 1000;

    @Rule public final Expect mExpect = Expect.create();

    private final Executor mOutcomeExecutor = newSingleThreadExecutor();
    private final Instant mEndTime = Instant.now().truncatedTo(ChronoUnit.MILLIS);
    private final Instant mStartTime = mEndTime.minus(Duration.ofHours(1));

    private final LocalDate mStartDate =
            LocalDate.ofInstant(
                    mStartTime, ZoneOffset.systemDefault().getRules().getOffset(mStartTime));

    private final LocalDate mEndDate =
            LocalDate.ofInstant(
                    mEndTime, ZoneOffset.systemDefault().getRules().getOffset(mEndTime));
    private Context mTargetContext;
    private HealthConnectManager mManager;
    private String mEnableCompletionJobsBackup;
    private String mEnablePauseJobsBackup;
    private String mStateChangeJobAdbDumpsys;

    private static <T, E extends RuntimeException> T blockingCall(
            Consumer<OutcomeReceiver<T, E>> action) {
        final BlockingOutcomeReceiver<T, E> outcomeReceiver = new BlockingOutcomeReceiver<>();
        action.accept(outcomeReceiver);
        return outcomeReceiver.await();
    }

    private static <T, E extends RuntimeException> T blockingCallWithPermissions(
            Consumer<OutcomeReceiver<T, E>> action, String... permissions) {
        try {
            return runWithShellPermissionIdentity(() -> blockingCall(action), permissions);
        } catch (RuntimeException e) {
            // runWithShellPermissionIdentity wraps and rethrows all exceptions as RuntimeException,
            // but we need the original RuntimeException if there is one.
            final Throwable cause = e.getCause();
            throw cause instanceof RuntimeException ? (RuntimeException) cause : e;
        }
    }

    private static Metadata getMetadata(UUID id, String clientRecordId, String packageName) {
        return new Metadata.Builder()
                .setId(id == null ? "" : id.toString())
                .setClientRecordId(clientRecordId)
                .setDataOrigin(new DataOrigin.Builder().setPackageName(packageName).build())
                .setDevice(new Device.Builder().setManufacturer("Device").setModel("Model").build())
                .build();
    }

    private static Metadata getMetadata(UUID uuid) {
        return getMetadata(uuid, /* clientRecordId= */ null, PACKAGE_NAME);
    }

    private static Metadata getMetadata(String clientRecordId, String packageName) {
        return getMetadata(/* id= */ null, clientRecordId, packageName);
    }

    private static Metadata getMetadata(String clientRecordId) {
        return getMetadata(/* id= */ null, clientRecordId, PACKAGE_NAME);
    }

    private static Metadata getMetadata() {
        return getMetadata(/* id= */ null, /* clientRecordId= */ null, PACKAGE_NAME);
    }

    private static byte[] getBitmapBytes(Bitmap bitmap) {
        if (bitmap == null) {
            return null;
        }

        try (ByteArrayOutputStream stream = new ByteArrayOutputStream()) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
            return stream.toByteArray();
        } catch (IOException exception) {
            throw new IllegalArgumentException(exception);
        }
    }

    @Rule
    public AssumptionCheckerRule mSupportedHardwareRule =
            new AssumptionCheckerRule(
                    TestUtils::isHardwareSupported, "Tests should run on supported hardware only.");

    @Before
    public void setUp() {
        mTargetContext = InstrumentationRegistry.getTargetContext();
        mManager = mTargetContext.getSystemService(HealthConnectManager.class);
        clearData();
    }

    @After
    public void tearDown() {
        clearData();
    }

    private void clearData() {
        deleteAllStagedRemoteData();
        deleteAllRecords();
    }

    @Test
    public void migrateHeight_clientRecordId_heightSaved() throws InterruptedException {
        final String entityId = "height";

        migrate(
                new HeightRecord.Builder(getMetadata(entityId), mEndTime, fromMeters(3D)).build(),
                entityId);

        finishMigration();
        final HeightRecord record = getRecord(HeightRecord.class, entityId);
        mExpect.that(record).isNotNull();
        mExpect.that(record.getHeight().getInMeters()).isEqualTo(3D);
        mExpect.that(record.getTime()).isEqualTo(mEndTime);

        List<LocalDate> activityDates = TestUtils.getActivityDates(List.of(record.getClass()));
        assertThat(activityDates.size()).isEqualTo(1);
        assertThat(activityDates.get(0).compareTo(mEndDate)).isEqualTo(0);
    }

    @Test
    public void migrateHeight_uuid_heightSaved() throws InterruptedException {
        final UUID uuid = UUID.randomUUID();
        final String entityId = "height";

        migrate(
                new HeightRecord.Builder(getMetadata(uuid), mEndTime, fromMeters(3D)).build(),
                entityId);

        finishMigration();
        final HeightRecord record = getRecord(HeightRecord.class, uuid);
        mExpect.that(record).isNotNull();
        mExpect.that(record.getHeight().getInMeters()).isEqualTo(3D);
        mExpect.that(record.getTime()).isEqualTo(mEndTime);

        List<LocalDate> activityDates = TestUtils.getActivityDates(List.of(record.getClass()));
        assertThat(activityDates.size()).isEqualTo(1);
        assertThat(activityDates.get(0).compareTo(mEndDate)).isEqualTo(0);
    }

    @Test
    public void migrateHeightUsingSharedMemory_heightSaved() throws InterruptedException {
        final String entityId = "height";
        int recordsToAdd = DEFAULT_PAGE_SIZE;

        List<MigrationEntity> inputMigrationEntityList = new ArrayList<>();
        for (int i = 0; i < recordsToAdd; i++) {
            String recordEntityId = entityId + i;
            HeightRecord heightRecord =
                    new HeightRecord.Builder(getMetadata(recordEntityId), mEndTime, fromMeters(3D))
                            .build();
            MigrationEntity heightMigrationEntity = getRecordEntity(heightRecord, recordEntityId);
            inputMigrationEntityList.add(heightMigrationEntity);
        }

        migrate(inputMigrationEntityList.toArray(MigrationEntity[]::new));
        finishMigration();

        final List<HeightRecord> outputRecords = getRecords(HeightRecord.class);

        mExpect.that(recordsToAdd).isEqualTo(outputRecords.size());

        for (int i = 0; i < recordsToAdd; i++) {
            RecordMigrationPayload inputRecordMigrationPayload =
                    (RecordMigrationPayload) inputMigrationEntityList.get(i).getPayload();
            HeightRecord inputHeightRecord = (HeightRecord) inputRecordMigrationPayload.getRecord();

            HeightRecord outputHeightRecord = outputRecords.get(i);

            mExpect.that(inputHeightRecord.getHeight()).isEqualTo(outputHeightRecord.getHeight());
        }

        List<LocalDate> activityDates =
                TestUtils.getActivityDates(
                        outputRecords.stream().map(Record::getClass).collect(Collectors.toList()));
        assertThat(activityDates.size()).isEqualTo(1);
        assertThat(activityDates.get(0).compareTo(mEndDate)).isEqualTo(0);
    }

    @Test
    public void migrateSteps_clientRecordId_stepsSaved() throws InterruptedException {
        final String entityId = "steps";

        migrate(
                new StepsRecord.Builder(getMetadata(entityId), mStartTime, mEndTime, 10).build(),
                entityId);

        finishMigration();
        final StepsRecord record = getRecord(StepsRecord.class, entityId);
        mExpect.that(record).isNotNull();
        mExpect.that(record.getCount()).isEqualTo(10);
        mExpect.that(record.getStartTime()).isEqualTo(mStartTime);
        mExpect.that(record.getEndTime()).isEqualTo(mEndTime);

        List<LocalDate> activityDates = TestUtils.getActivityDates(List.of(record.getClass()));
        assertThat(activityDates.size()).isEqualTo(1);
        assertThat(activityDates.get(0).compareTo(mStartDate)).isEqualTo(0);
    }

    @Test
    public void migrateSteps_uuid_stepsSaved() throws InterruptedException {
        final UUID uuid = UUID.randomUUID();
        final String entityId = "steps";

        migrate(
                new StepsRecord.Builder(getMetadata(uuid), mStartTime, mEndTime, 10).build(),
                entityId);

        finishMigration();
        final StepsRecord record = getRecord(StepsRecord.class, uuid);
        mExpect.that(record).isNotNull();
        mExpect.that(record.getCount()).isEqualTo(10);
        mExpect.that(record.getStartTime()).isEqualTo(mStartTime);
        mExpect.that(record.getEndTime()).isEqualTo(mEndTime);

        List<LocalDate> activityDates = TestUtils.getActivityDates(List.of(record.getClass()));
        assertThat(activityDates.size()).isEqualTo(1);
        assertThat(activityDates.get(0).compareTo(mStartDate)).isEqualTo(0);
    }

    @Test
    public void migratePower_powerSaved() throws InterruptedException {
        final String entityId = "power";

        migrate(
                new PowerRecord.Builder(
                                getMetadata(entityId),
                                mStartTime,
                                mEndTime,
                                List.of(
                                        new PowerRecordSample(fromWatts(10D), mEndTime),
                                        new PowerRecordSample(fromWatts(20D), mEndTime),
                                        new PowerRecordSample(fromWatts(30D), mEndTime)))
                        .build(),
                entityId);

        finishMigration();
        final PowerRecord record = getRecord(PowerRecord.class, entityId);

        mExpect.that(record).isNotNull();

        mExpect.that(record.getSamples())
                .containsExactly(
                        new PowerRecordSample(fromWatts(10D), mEndTime),
                        new PowerRecordSample(fromWatts(20D), mEndTime),
                        new PowerRecordSample(fromWatts(30D), mEndTime))
                .inOrder();

        mExpect.that(record.getStartTime()).isEqualTo(mStartTime);
        mExpect.that(record.getEndTime()).isEqualTo(mEndTime);

        List<LocalDate> activityDates = TestUtils.getActivityDates(List.of(record.getClass()));
        assertThat(activityDates.size()).isEqualTo(1);
        assertThat(activityDates.get(0).compareTo(mStartDate)).isEqualTo(0);
    }

    @Test
    public void migrateRecord_sameEntityId_notIgnored() throws InterruptedException {
        final String entityId = "height";

        final Length height1 = fromMeters(3D);
        migrate(new HeightRecord.Builder(getMetadata(), mEndTime, height1).build(), entityId);

        final Length height2 = fromMeters(1D);
        migrate(new HeightRecord.Builder(getMetadata(), mStartTime, height2).build(), entityId);

        finishMigration();

        final List<HeightRecord> records = getRecords(HeightRecord.class);
        mExpect.that(records.stream().map(HeightRecord::getHeight).toList())
                .containsExactly(height1, height2);

        List<LocalDate> activityDates =
                TestUtils.getActivityDates(
                        records.stream()
                                .map(Record::getClass)
                                .distinct()
                                .collect(Collectors.toList()));
        assertThat(activityDates.size()).isAtLeast(1);
    }

    @Test
    public void migrateInstantRecord_noClientIdsAndSameTime_ignored() throws InterruptedException {
        final String entityId1 = "steps1";
        final long steps1 = 1000L;
        migrate(
                new StepsRecord.Builder(getMetadata(), mStartTime, mEndTime, steps1).build(),
                entityId1);

        final String entityId2 = "steps2";
        final long steps2 = 2000L;
        migrate(
                new StepsRecord.Builder(getMetadata(), mStartTime, mEndTime, steps2).build(),
                entityId2);

        finishMigration();

        final List<StepsRecord> records = getRecords(StepsRecord.class);
        mExpect.that(records.stream().map(StepsRecord::getCount).toList()).containsExactly(steps1);

        List<LocalDate> activityDates =
                TestUtils.getActivityDates(
                        records.stream().map(Record::getClass).collect(Collectors.toList()));
        assertThat(activityDates.size()).isEqualTo(1);
        assertThat(activityDates.get(0).compareTo(mStartDate)).isEqualTo(0);
    }

    @Test
    public void migrateInstantRecord_differentClientIdsAndSameTime_notIgnored()
            throws InterruptedException {
        final String entityId1 = "steps1";
        final long steps1 = 1000L;
        migrate(
                new StepsRecord.Builder(getMetadata(entityId1), mStartTime, mEndTime, steps1)
                        .build(),
                entityId1);

        final String entityId2 = "steps2";
        final long steps2 = 2000L;
        migrate(
                new StepsRecord.Builder(getMetadata(entityId2), mStartTime, mEndTime, steps2)
                        .build(),
                entityId2);

        finishMigration();

        final List<StepsRecord> records = getRecords(StepsRecord.class);
        mExpect.that(records.stream().map(StepsRecord::getCount).toList())
                .containsExactly(steps1, steps2);

        List<LocalDate> activityDates =
                TestUtils.getActivityDates(
                        records.stream().map(Record::getClass).collect(Collectors.toList()));
        assertThat(activityDates.size()).isEqualTo(1);
        assertThat(activityDates.get(0).compareTo(mStartDate)).isEqualTo(0);
    }

    @Test
    public void migrateIntervalRecord_noClientIdsAndSameTime_ignored() throws InterruptedException {
        final String entityId1 = "height1";
        final Length height1 = fromMeters(1.0);
        migrate(new HeightRecord.Builder(getMetadata(), mEndTime, height1).build(), entityId1);

        final String entityId2 = "height2";
        final Length height2 = fromMeters(2.0);
        migrate(new HeightRecord.Builder(getMetadata(), mEndTime, height2).build(), entityId2);

        finishMigration();

        final List<HeightRecord> records = getRecords(HeightRecord.class);
        mExpect.that(records.stream().map(HeightRecord::getHeight).toList())
                .containsExactly(height1);

        List<LocalDate> activityDates =
                TestUtils.getActivityDates(
                        records.stream().map(Record::getClass).collect(Collectors.toList()));
        assertThat(activityDates.size()).isEqualTo(1);
        assertThat(activityDates.get(0).compareTo(mEndDate)).isEqualTo(0);
    }

    @Test
    public void migrateIntervalRecord_differentClientIdsAndSameTime_notIgnored()
            throws InterruptedException {
        final String entityId1 = "height1";
        final Length height1 = fromMeters(1.0);
        migrate(
                new HeightRecord.Builder(getMetadata(entityId1), mEndTime, height1).build(),
                entityId1);

        final String entityId2 = "height2";
        final Length height2 = fromMeters(2.0);
        migrate(
                new HeightRecord.Builder(getMetadata(entityId2), mEndTime, height2).build(),
                entityId2);

        finishMigration();

        final List<HeightRecord> records = getRecords(HeightRecord.class);
        mExpect.that(records.stream().map(HeightRecord::getHeight).toList())
                .containsExactly(height1, height2);

        List<LocalDate> activityDates =
                TestUtils.getActivityDates(
                        records.stream().map(Record::getClass).collect(Collectors.toList()));
        assertThat(activityDates.size()).isEqualTo(1);
        assertThat(activityDates.get(0).compareTo(mEndDate)).isEqualTo(0);
    }

    // Special case for hydration, must not ignore
    @Test
    public void migrateHydration_noClientIdsAndSameTime_notIgnored() throws InterruptedException {
        final String entityId1 = "hydration1";
        final Volume volume1 = Volume.fromLiters(0.2);
        migrate(
                new HydrationRecord.Builder(getMetadata(), mStartTime, mEndTime, volume1).build(),
                entityId1);

        final String entityId2 = "hydration2";
        final Volume volume2 = Volume.fromLiters(0.3);
        migrate(
                new HydrationRecord.Builder(getMetadata(), mStartTime, mEndTime, volume2).build(),
                entityId2);

        finishMigration();

        final List<HydrationRecord> records = getRecords(HydrationRecord.class);
        mExpect.that(records.stream().map(HydrationRecord::getVolume).toList())
                .containsExactly(volume1, volume2);

        List<LocalDate> activityDates =
                TestUtils.getActivityDates(
                        records.stream().map(Record::getClass).collect(Collectors.toList()));
        assertThat(activityDates.size()).isEqualTo(1);
        assertThat(activityDates.get(0).compareTo(mStartDate)).isEqualTo(0);
    }

    // Special case for nutrition, must not ignore
    @Test
    public void migrateNutrition_noClientIdsAndSameTime_notIgnored() throws InterruptedException {
        final String entityId1 = "nutrition1";
        final Mass protein1 = Mass.fromGrams(1.0);
        migrate(
                new NutritionRecord.Builder(getMetadata(), mStartTime, mEndTime)
                        .setProtein(protein1)
                        .build(),
                entityId1);

        final String entityId2 = "nutrition2";
        final Mass protein2 = Mass.fromGrams(2.0);
        migrate(
                new NutritionRecord.Builder(getMetadata(), mStartTime, mEndTime)
                        .setProtein(protein2)
                        .build(),
                entityId2);

        finishMigration();

        final List<NutritionRecord> records = getRecords(NutritionRecord.class);
        mExpect.that(records.stream().map(NutritionRecord::getProtein).toList())
                .containsExactly(protein1, protein2);

        List<LocalDate> activityDates =
                TestUtils.getActivityDates(
                        records.stream().map(Record::getClass).collect(Collectors.toList()));
        assertThat(activityDates.size()).isEqualTo(1);
        assertThat(activityDates.get(0).compareTo(mStartDate)).isEqualTo(0);
    }

    @Test
    public void migratePermissions_hasValidPermissions_validPermissionsGranted() {
        assumeFalse(TestUtils.isHardwareAutomotive());

        revokeHealthPermissions(APP_PACKAGE_NAME);

        final String entityId = "permissions";

        migrate(
                new MigrationEntity(
                        entityId,
                        new PermissionMigrationPayload.Builder(APP_PACKAGE_NAME, Instant.now())
                                .addPermission(INVALID_PERMISSION_1)
                                .addPermission(READ_HEIGHT)
                                .addPermission(INVALID_PERMISSION_2)
                                .addPermission(WRITE_HEIGHT)
                                .build()));
        finishMigration();
        final List<String> grantedPermissions = getGrantedAppPermissions();
        mExpect.that(grantedPermissions).containsAtLeast(READ_HEIGHT, WRITE_HEIGHT);
        mExpect.that(grantedPermissions).containsNoneOf(INVALID_PERMISSION_1, INVALID_PERMISSION_2);
    }

    @Test
    public void migratePermissions_allInvalidPermissions_throwsMigrationException() {
        assumeFalse(TestUtils.isHardwareAutomotive());

        revokeHealthPermissions(APP_PACKAGE_NAME);

        final String entityId = "permissions";

        final MigrationEntity entity =
                new MigrationEntity(
                        entityId,
                        new PermissionMigrationPayload.Builder(APP_PACKAGE_NAME, Instant.now())
                                .addPermission(INVALID_PERMISSION_1)
                                .addPermission(INVALID_PERMISSION_2)
                                .build());
        try {
            migrate(entity);
            fail("Expected to fail with MigrationException but didn't");
        } catch (MigrationException e) {
            mExpect.that(e.getErrorCode()).isEqualTo(MigrationException.ERROR_MIGRATE_ENTITY);
            mExpect.that(e.getFailedEntityId()).isEqualTo(entityId);
            mExpect.that(getGrantedAppPermissions())
                    .containsNoneOf(INVALID_PERMISSION_1, INVALID_PERMISSION_2);
            finishMigration();
        }
    }

    /** Test metadata migration, migrating record retention period. */
    @Test
    public void migrateMetadata_migratingRetentionPeriod_metadataSaved() {

        final String entityId = "metadata";

        migrate(
                new MigrationEntity(
                        entityId,
                        new MetadataMigrationPayload.Builder()
                                .setRecordRetentionPeriodDays(100)
                                .build()));
        finishMigration();

        assertThat(getRecordRetentionPeriodInDays()).isEqualTo(100);
    }

    /** Test priority migration where migration payload have additional apps. */
    @Test
    public void migratePriority_additionalAppsInMigrationPayload_prioritySaved() {
        assumeFalse(TestUtils.isHardwareAutomotive());

        revokeHealthPermissions(APP_PACKAGE_NAME);
        revokeHealthPermissions(APP_PACKAGE_NAME_2);

        String permissionMigrationEntityId1 = "permissionMigration1";
        String permissionMigrationEntityId2 = "permissionMigration2";
        String priorityMigrationEntityId = "priorityMigration";

        /*
        Migrating permissions first as any package that do not have required permissions would be
        removed from the priority list during priority migration.
        */

        migrate(
                new MigrationEntity(
                        permissionMigrationEntityId1,
                        new PermissionMigrationPayload.Builder(APP_PACKAGE_NAME, Instant.now())
                                .addPermission(READ_HEIGHT)
                                .addPermission(WRITE_HEIGHT)
                                .build()),
                new MigrationEntity(
                        permissionMigrationEntityId2,
                        new PermissionMigrationPayload.Builder(APP_PACKAGE_NAME_2, Instant.now())
                                .addPermission(READ_HEIGHT)
                                .addPermission(WRITE_HEIGHT)
                                .build()),
                new MigrationEntity(
                        priorityMigrationEntityId,
                        new PriorityMigrationPayload.Builder()
                                .setDataCategory(HealthDataCategory.BODY_MEASUREMENTS)
                                .addDataOrigin(
                                        new DataOrigin.Builder()
                                                .setPackageName(APP_PACKAGE_NAME_2)
                                                .build())
                                .addDataOrigin(
                                        new DataOrigin.Builder()
                                                .setPackageName(APP_PACKAGE_NAME)
                                                .build())
                                .build()));

        finishMigration();

        List<String> activityPriorityList =
                getHealthDataCategoryPriority(HealthDataCategory.BODY_MEASUREMENTS);

        verifyAddedPriorityOrder(
                List.of(APP_PACKAGE_NAME_2, APP_PACKAGE_NAME), activityPriorityList);
    }

    @Test
    public void testStartMigrationFromIdleState() throws IOException {
        try {
            enableStateChangeJobs();
            runWithShellPermissionIdentity(
                    () -> {
                        assertStateChangeJobDoesNotExist();
                        assertThat(TestUtils.getHealthConnectDataMigrationState())
                                .isEqualTo(MIGRATION_STATE_IDLE);
                        TestUtils.startMigration();
                        assertThat(TestUtils.getHealthConnectDataMigrationState())
                                .isEqualTo(HealthConnectDataState.MIGRATION_STATE_IN_PROGRESS);
                        assertStateChangeJobExists();
                        TestUtils.finishMigration();
                        assertThat(TestUtils.getHealthConnectDataMigrationState())
                                .isEqualTo(HealthConnectDataState.MIGRATION_STATE_COMPLETE);
                        assertStateChangeJobDoesNotExist();
                    },
                    Manifest.permission.MIGRATE_HEALTH_CONNECT_DATA);
        } finally {
            restoreDeviceConfigs();
        }
    }

    @Test
    public void testInsertMinDataMigrationSdkExtensionVersion_upgradeRequired() throws IOException {
        int version = SdkExtensions.getExtensionVersion(Build.VERSION_CODES.UPSIDE_DOWN_CAKE) + 1;
        try {
            enableStateChangeJobs();
            runWithShellPermissionIdentity(
                    () -> {
                        assertStateChangeJobDoesNotExist();
                        assertThat(TestUtils.getHealthConnectDataMigrationState())
                                .isEqualTo(MIGRATION_STATE_IDLE);
                        TestUtils.insertMinDataMigrationSdkExtensionVersion(version);
                        assertThat(TestUtils.getHealthConnectDataMigrationState())
                                .isEqualTo(MIGRATION_STATE_MODULE_UPGRADE_REQUIRED);
                        assertStateChangeJobExists();
                        TestUtils.startMigration();
                        assertThat(TestUtils.getHealthConnectDataMigrationState())
                                .isEqualTo(HealthConnectDataState.MIGRATION_STATE_IN_PROGRESS);
                        assertStateChangeJobExists();
                        TestUtils.finishMigration();
                        assertThat(TestUtils.getHealthConnectDataMigrationState())
                                .isEqualTo(HealthConnectDataState.MIGRATION_STATE_COMPLETE);
                        assertStateChangeJobDoesNotExist();
                    },
                    Manifest.permission.MIGRATE_HEALTH_CONNECT_DATA);
        } finally {
            restoreDeviceConfigs();
        }
    }

    @Test
    public void testInsertMinDataMigrationSdkExtensionVersion_noUpgradeRequired()
            throws IOException {
        int version = SdkExtensions.getExtensionVersion(Build.VERSION_CODES.UPSIDE_DOWN_CAKE);
        try {
            enableStateChangeJobs();
            runWithShellPermissionIdentity(
                    () -> {
                        assertStateChangeJobDoesNotExist();
                        assertThat(TestUtils.getHealthConnectDataMigrationState())
                                .isEqualTo(MIGRATION_STATE_IDLE);
                        TestUtils.insertMinDataMigrationSdkExtensionVersion(version);
                        assertThat(TestUtils.getHealthConnectDataMigrationState())
                                .isEqualTo(MIGRATION_STATE_ALLOWED);
                        assertStateChangeJobExists();
                        TestUtils.startMigration();
                        assertThat(TestUtils.getHealthConnectDataMigrationState())
                                .isEqualTo(HealthConnectDataState.MIGRATION_STATE_IN_PROGRESS);
                        assertStateChangeJobExists();
                        TestUtils.finishMigration();
                        assertThat(TestUtils.getHealthConnectDataMigrationState())
                                .isEqualTo(HealthConnectDataState.MIGRATION_STATE_COMPLETE);
                        assertStateChangeJobDoesNotExist();
                    },
                    Manifest.permission.MIGRATE_HEALTH_CONNECT_DATA);
        } finally {
            restoreDeviceConfigs();
        }
    }

    @Test
    public void testInsertMinDataMigrationSdkExtensionVersion_jobsDisabled() throws IOException {
        int version = SdkExtensions.getExtensionVersion(Build.VERSION_CODES.UPSIDE_DOWN_CAKE);
        try {
            disableStateChangeJobs();
            runWithShellPermissionIdentity(
                    () -> {
                        assertStateChangeJobDoesNotExist();
                        assertThat(TestUtils.getHealthConnectDataMigrationState())
                                .isEqualTo(MIGRATION_STATE_IDLE);
                        TestUtils.insertMinDataMigrationSdkExtensionVersion(version);
                        assertThat(TestUtils.getHealthConnectDataMigrationState())
                                .isEqualTo(MIGRATION_STATE_ALLOWED);
                        assertStateChangeJobDoesNotExist();
                        TestUtils.startMigration();
                        assertThat(TestUtils.getHealthConnectDataMigrationState())
                                .isEqualTo(HealthConnectDataState.MIGRATION_STATE_IN_PROGRESS);
                        assertStateChangeJobDoesNotExist();
                        TestUtils.finishMigration();
                        assertThat(TestUtils.getHealthConnectDataMigrationState())
                                .isEqualTo(HealthConnectDataState.MIGRATION_STATE_COMPLETE);
                        assertStateChangeJobDoesNotExist();
                    },
                    Manifest.permission.MIGRATE_HEALTH_CONNECT_DATA);
        } finally {
            restoreDeviceConfigs();
        }
    }

    @Test
    public void migrateAppInfo_notInstalledAppAndRecordsMigrated_appInfoSaved()
            throws InterruptedException {
        final String recordEntityId = "steps";
        final String appInfoEntityId = "appInfo";
        final Bitmap icon = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888);
        final byte[] iconBytes = getBitmapBytes(icon);
        migrate(
                new StepsRecord.Builder(
                                getMetadata(recordEntityId, PACKAGE_NAME_NOT_INSTALLED),
                                mStartTime,
                                mEndTime,
                                10)
                        .build(),
                recordEntityId);
        migrate(
                new MigrationEntity(
                        appInfoEntityId,
                        new AppInfoMigrationPayload.Builder(PACKAGE_NAME_NOT_INSTALLED, APP_NAME)
                                .setAppIcon(iconBytes)
                                .build()));

        finishMigration();
        // When recordTypes are modified the appInfo also gets updated and this update happens on
        // a background thread. To ensure the test has the latest values for appInfo, add a wait
        // time before fetching it.
        Thread.sleep(500);
        final AppInfo appInfo = getContributorApplicationInfo(PACKAGE_NAME_NOT_INSTALLED);

        mExpect.that(appInfo).isNotNull();
        mExpect.that(appInfo.getName()).isEqualTo(APP_NAME);
        mExpect.that(getBitmapBytes(appInfo.getIcon())).isEqualTo(iconBytes);
    }

    @Test
    public void migrateAppInfo_notInstalledAppAndRecordsNotMigrated_appInfoNotSaved() {
        final String entityId = "appInfo";
        final Bitmap icon = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888);
        final byte[] iconBytes = getBitmapBytes(icon);

        migrate(
                new MigrationEntity(
                        entityId,
                        new AppInfoMigrationPayload.Builder(
                                        PACKAGE_NAME_NOT_INSTALLED, APP_NAME_NEW)
                                .setAppIcon(iconBytes)
                                .build()));

        finishMigration();
        final AppInfo appInfo = getContributorApplicationInfo(PACKAGE_NAME_NOT_INSTALLED);

        assertThat(appInfo).isNull();
    }

    @Test
    public void migrateAppInfo_installedAppAndRecordsMigrated_appInfoNotSaved()
            throws InterruptedException {
        final String recordEntityId = "steps";
        final String appInfoEntityId = "appInfo";
        final Bitmap icon = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888);
        final byte[] iconBytes = getBitmapBytes(icon);
        migrate(
                new StepsRecord.Builder(
                                getMetadata(recordEntityId, APP_PACKAGE_NAME),
                                mStartTime,
                                mEndTime,
                                10)
                        .build(),
                recordEntityId);

        migrate(
                new MigrationEntity(
                        appInfoEntityId,
                        new AppInfoMigrationPayload.Builder(APP_PACKAGE_NAME, APP_NAME_NEW)
                                .setAppIcon(iconBytes)
                                .build()));

        finishMigration();
        // When recordTypes are modified the appInfo also gets updated and this update happens on
        // a background thread. To ensure the test has the latest values for appInfo, add a wait
        // time before fetching it.
        Thread.sleep(500);
        final AppInfo appInfo = getContributorApplicationInfo(APP_PACKAGE_NAME);

        mExpect.that(appInfo).isNotNull();
        mExpect.that(appInfo.getName()).isNotEqualTo(APP_NAME_NEW);
        mExpect.that(getBitmapBytes(appInfo.getIcon())).isNotEqualTo(iconBytes);
    }

    private boolean checkJobExists() throws IOException {
        String scheduledStateChangeJobPattern =
                "JOB class com\\.android\\.server\\.healthconnect\\.migration\\"
                        + ".MigrationStateChangeJob:\\d+/\\d+: [a-f0-9]+ @class com\\"
                        + ".android\\.server\\.healthconnect\\.migration\\"
                        + ".MigrationStateChangeJob@android/com\\.android\\.server\\"
                        + ".healthconnect\\.HealthConnectDailyService";

        String commandOutput = TestUtils.runShellCommand("dumpsys jobscheduler");

        Pattern regexPattern = Pattern.compile(scheduledStateChangeJobPattern);
        Matcher matcher = regexPattern.matcher(commandOutput);
        storeStateChangeJobAdbDumpsysOutput(commandOutput);
        return matcher.find();
    }

    private void storeStateChangeJobAdbDumpsysOutput(String commandOutput) {
        String stateChangeJobPattern = ".*MigrationStateChangeJob.*";
        Pattern regexPattern = Pattern.compile(stateChangeJobPattern);
        Matcher matcher = regexPattern.matcher(commandOutput);
        StringBuilder output = new StringBuilder();
        while (matcher.find()) {
            output.append(matcher.group()).append("\n");
        }
        mStateChangeJobAdbDumpsys = output.toString();
    }

    private String getHealthFitnessDeviceConfig(String key, String defaultValue)
            throws IOException {
        String value = TestUtils.runShellCommand("device_config get health_fitness " + key);
        return value.isBlank() ? defaultValue : value.strip();
    }

    private void enableStateChangeJobs() throws IOException {
        mEnableCompletionJobsBackup =
                getHealthFitnessDeviceConfig(
                        ENABLE_COMPLETE_STATE_CHANGE_JOBS_FLAG,
                        ENABLE_COMPLETE_STATE_CHANGE_JOB_DEFAULT_FLAG_VALUE);
        mEnablePauseJobsBackup =
                getHealthFitnessDeviceConfig(
                        ENABLE_PAUSE_STATE_CHANGE_JOBS_FLAG,
                        ENABLE_PAUSE_STATE_CHANGE_JOB_DEFAULT_FLAG_VALUE);
        setHealthFitnessDeviceConfig(ENABLE_COMPLETE_STATE_CHANGE_JOBS_FLAG, "true");
        setHealthFitnessDeviceConfig(ENABLE_PAUSE_STATE_CHANGE_JOBS_FLAG, "true");
    }

    private void disableStateChangeJobs() throws IOException {
        mEnableCompletionJobsBackup =
                getHealthFitnessDeviceConfig(
                        ENABLE_COMPLETE_STATE_CHANGE_JOBS_FLAG,
                        ENABLE_COMPLETE_STATE_CHANGE_JOB_DEFAULT_FLAG_VALUE);
        mEnablePauseJobsBackup =
                getHealthFitnessDeviceConfig(
                        ENABLE_PAUSE_STATE_CHANGE_JOBS_FLAG,
                        ENABLE_PAUSE_STATE_CHANGE_JOB_DEFAULT_FLAG_VALUE);
        setHealthFitnessDeviceConfig(ENABLE_COMPLETE_STATE_CHANGE_JOBS_FLAG, "false");
        setHealthFitnessDeviceConfig(ENABLE_PAUSE_STATE_CHANGE_JOBS_FLAG, "false");
    }

    private void restoreDeviceConfigs() throws IOException {
        setHealthFitnessDeviceConfig(
                ENABLE_COMPLETE_STATE_CHANGE_JOBS_FLAG, mEnableCompletionJobsBackup);
        setHealthFitnessDeviceConfig(ENABLE_PAUSE_STATE_CHANGE_JOBS_FLAG, mEnablePauseJobsBackup);
    }

    private void setHealthFitnessDeviceConfig(String key, String value) throws IOException {
        TestUtils.runShellCommand("device_config put health_fitness " + key + " " + value);
    }

    private void assertStateChangeJobExists() throws IOException {
        boolean checkJobExists = checkJobExists();
        assertWithMessage(
                        "Expected to find a scheduled MigrationStateChangeJob but not found. ADB"
                                + " 'dumpsys jobscheduler' output for MigrationStateChangeJob: \n"
                                + mStateChangeJobAdbDumpsys)
                .that(checkJobExists)
                .isTrue();
    }

    private void assertStateChangeJobDoesNotExist() throws IOException {
        boolean checkJobExists = checkJobExists();
        assertWithMessage(
                        "Did not expect a scheduled MigrationStateChangeJob but found one. ADB"
                                + " 'dumpsys jobscheduler' output for MigrationStateChangeJob: \n"
                                + mStateChangeJobAdbDumpsys)
                .that(checkJobExists)
                .isFalse();
    }

    private void migrate(MigrationEntity... entities) {
        DataMigrationTest.<Void, MigrationException>blockingCallWithPermissions(
                callback -> mManager.startMigration(mOutcomeExecutor, callback),
                Manifest.permission.MIGRATE_HEALTH_CONNECT_DATA);

        DataMigrationTest.<Void, MigrationException>blockingCallWithPermissions(
                callback ->
                        mManager.writeMigrationData(List.of(entities), mOutcomeExecutor, callback),
                Manifest.permission.MIGRATE_HEALTH_CONNECT_DATA);
    }

    private void finishMigration() {
        DataMigrationTest.<Void, MigrationException>blockingCallWithPermissions(
                callback -> mManager.finishMigration(mOutcomeExecutor, callback),
                Manifest.permission.MIGRATE_HEALTH_CONNECT_DATA);
    }

    private void migrate(Record record, String entityId) {
        migrate(getRecordEntity(record, entityId));
    }

    private MigrationEntity getRecordEntity(Record record, String entityId) {
        return new MigrationEntity(
                entityId,
                new RecordMigrationPayload.Builder(
                                record.getMetadata().getDataOrigin().getPackageName(),
                                "Example App",
                                record)
                        .build());
    }

    private <T extends Record> List<T> getRecords(Class<T> clazz) {
        final Consumer<OutcomeReceiver<ReadRecordsResponse<T>, HealthConnectException>> action =
                callback -> getRecordsAsync(clazz, callback);

        final ReadRecordsResponse<T> response =
                blockingCallWithPermissions(
                        action, HealthPermissions.MANAGE_HEALTH_DATA_PERMISSION);

        return response.getRecords();
    }

    private <T extends Record> T getRecord(Class<T> clazz, String clientRecordId) {
        return getRecord(
                new ReadRecordsRequestUsingIds.Builder<>(clazz)
                        .addClientRecordId(clientRecordId)
                        .build());
    }

    private <T extends Record> T getRecord(Class<T> clazz, UUID uuid) {
        return getRecord(
                new ReadRecordsRequestUsingIds.Builder<>(clazz).addId(uuid.toString()).build());
    }

    private <T extends Record> T getRecord(ReadRecordsRequest<T> request) {
        final Consumer<OutcomeReceiver<ReadRecordsResponse<T>, HealthConnectException>> action =
                callback -> mManager.readRecords(request, mOutcomeExecutor, callback);

        final ReadRecordsResponse<T> response =
                blockingCallWithPermissions(
                        action, HealthPermissions.MANAGE_HEALTH_DATA_PERMISSION);

        return response.getRecords().stream().findFirst().orElse(null);
    }

    private <T extends Record> void getRecordsAsync(
            Class<T> clazz,
            OutcomeReceiver<ReadRecordsResponse<T>, HealthConnectException> callback) {
        mManager.readRecords(
                new ReadRecordsRequestUsingFilters.Builder<>(clazz)
                        .setTimeRangeFilter(
                                new TimeInstantRangeFilter.Builder()
                                        .setStartTime(mStartTime)
                                        // Some tests in this file are using mEndTime as `time` for
                                        // instant data points, and HC reads filters data points by
                                        // [inclusive, exclusive), hence we need to add a positive
                                        // amount of ms.
                                        .setEndTime(mEndTime.plusMillis(1))
                                        .build())
                        .build(),
                mOutcomeExecutor,
                callback);
    }

    private void deleteAllRecords() {
        runWithShellPermissionIdentity(
                () -> blockingCall(this::deleteAllRecordsAsync),
                HealthPermissions.MANAGE_HEALTH_DATA_PERMISSION);
    }

    private void deleteAllRecordsAsync(OutcomeReceiver<Void, HealthConnectException> callback) {
        mManager.deleteRecords(
                new DeleteUsingFiltersRequest.Builder()
                        .addDataOrigin(
                                new DataOrigin.Builder().setPackageName(APP_PACKAGE_NAME).build())
                        .build(),
                mOutcomeExecutor,
                callback);
    }

    private void deleteAllStagedRemoteData() {
        runWithShellPermissionIdentity(
                () ->
                        // TODO(b/241542162): Avoid reflection once TestApi can be called from CTS
                        mManager.getClass().getMethod("deleteAllStagedRemoteData").invoke(mManager),
                "android.permission.DELETE_STAGED_HEALTH_CONNECT_REMOTE_DATA");
    }

    private void revokeHealthPermissions(String packageName) {
        runWithShellPermissionIdentity(() -> revokeHealthPermissionsPrivileged(packageName));
    }

    private void revokeHealthPermissionsPrivileged(String packageName)
            throws PackageManager.NameNotFoundException {
        final PackageManager packageManager = mTargetContext.getPackageManager();
        final UserHandle user = mTargetContext.getUser();

        final PackageInfo packageInfo =
                packageManager.getPackageInfo(
                        packageName,
                        PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS));

        final String[] permissions = packageInfo.requestedPermissions;
        if (permissions == null) {
            return;
        }

        for (String permission : permissions) {
            if (permission.startsWith(HEALTH_PERMISSION_PREFIX)) {
                packageManager.revokeRuntimePermission(packageName, permission, user);
            }
        }
    }

    private List<String> getGrantedAppPermissions() {
        final PackageInfo pi = getAppPackageInfo();
        final String[] requestedPermissions = pi.requestedPermissions;
        final int[] requestedPermissionsFlags = pi.requestedPermissionsFlags;

        if (requestedPermissions == null) {
            return List.of();
        }

        final List<String> permissions = new ArrayList<>();

        for (int i = 0; i < requestedPermissions.length; i++) {
            if ((requestedPermissionsFlags[i] & PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0) {
                permissions.add(requestedPermissions[i]);
            }
        }

        return permissions;
    }

    private PackageInfo getAppPackageInfo() {
        return runWithShellPermissionIdentity(
                () ->
                        mTargetContext
                                .getPackageManager()
                                .getPackageInfo(
                                        APP_PACKAGE_NAME, PackageInfoFlags.of(GET_PERMISSIONS)));
    }

    private AppInfo getContributorApplicationInfo(String packageName) {
        return getContributorApplicationsInfo().stream()
                .filter(ai -> ai.getPackageName().equals(packageName))
                .findFirst()
                .orElse(null);
    }

    private List<AppInfo> getContributorApplicationsInfo() {
        final ApplicationInfoResponse response =
                runWithShellPermissionIdentity(
                        () -> blockingCall(this::getContributorApplicationsInfoAsync));
        return response.getApplicationInfoList();
    }

    private void getContributorApplicationsInfoAsync(
            OutcomeReceiver<ApplicationInfoResponse, HealthConnectException> callback) {
        mManager.getContributorApplicationsInfo(mOutcomeExecutor, callback);
    }

    @SuppressWarnings("NewClassNamingConvention") // False positive
    private static class BlockingOutcomeReceiver<T, E extends Throwable>
            implements OutcomeReceiver<T, E> {
        private final Object[] mResult = new Object[1];
        private final Throwable[] mError = new Throwable[1];
        private final CountDownLatch mCountDownLatch = new CountDownLatch(1);

        @Override
        public void onResult(T result) {
            mResult[0] = result;
            mCountDownLatch.countDown();
        }

        @Override
        public void onError(E error) {
            mError[0] = error;
            mCountDownLatch.countDown();
        }

        public T await() throws E {
            try {
                if (!mCountDownLatch.await(10, TimeUnit.SECONDS)) {
                    throw new RuntimeException("Timeout waiting for outcome");
                }

                if (mError[0] != null) {
                    //noinspection unchecked
                    throw (E) mError[0];
                }

                //noinspection unchecked
                return (T) mResult[0];
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * Verifies that the added priority matches with expected priority
     *
     * @param expectedAddedPriorityList expected added packages priority list.
     * @param actualPriorityList actual priority stored in HealthConnect module
     */
    private void verifyAddedPriorityOrder(
            List<String> expectedAddedPriorityList, List<String> actualPriorityList) {
        assertThat(actualPriorityList.size()).isAtLeast(expectedAddedPriorityList.size());

        List<String> addedPriorityList =
                actualPriorityList.stream()
                        .filter(packageName -> expectedAddedPriorityList.contains(packageName))
                        .toList();

        assertThat(addedPriorityList).isEqualTo(expectedAddedPriorityList);
    }

    /**
     * Fetches data category's priority of packages.
     *
     * @param dataCategory category for which priority is being fetched
     * @return list of packages ordered in priority.
     */
    private List<String> getHealthDataCategoryPriority(int dataCategory) {
        return runWithShellPermissionIdentity(
                () ->
                        DataMigrationTest
                                .<FetchDataOriginsPriorityOrderResponse, HealthConnectException>
                                        blockingCall(
                                                callback ->
                                                        getHealthDataCategoryPriorityAsync(
                                                                dataCategory, callback))
                                .getDataOriginsPriorityOrder()
                                .stream()
                                .map(dataOrigin -> dataOrigin.getPackageName())
                                .collect(Collectors.toList()),
                HealthPermissions.MANAGE_HEALTH_DATA_PERMISSION);
    }

    /**
     * Fetches data category's priority of packages asynchronously
     *
     * @param dataCategory category for which priority is being fetched
     * @param callback called after receiving results.
     */
    private void getHealthDataCategoryPriorityAsync(
            int dataCategory,
            OutcomeReceiver<FetchDataOriginsPriorityOrderResponse, HealthConnectException>
                    callback) {
        mManager.fetchDataOriginsPriorityOrder(dataCategory, newSingleThreadExecutor(), callback);
    }

    /**
     * Fetched recordRetentionPeriodInDays from HealthConnectModule
     *
     * @return number of days for retention.
     */
    private int getRecordRetentionPeriodInDays() {
        return runWithShellPermissionIdentity(
                () -> mManager.getRecordRetentionPeriodInDays(),
                HealthPermissions.MANAGE_HEALTH_DATA_PERMISSION);
    }
}
