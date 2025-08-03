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

package com.android.server.healthconnect;

import static android.Manifest.permission.MIGRATE_HEALTH_CONNECT_DATA;
import static android.health.connect.HealthConnectManager.DATA_DOWNLOAD_STARTED;

import static com.android.server.healthconnect.backuprestore.BackupRestore.DATA_DOWNLOAD_STATE_KEY;
import static com.android.server.healthconnect.backuprestore.BackupRestore.DATA_RESTORE_STATE_KEY;
import static com.android.server.healthconnect.backuprestore.BackupRestore.INTERNAL_RESTORE_STATE_STAGING_DONE;
import static com.android.server.healthconnect.backuprestore.BackupRestore.INTERNAL_RESTORE_STATE_STAGING_IN_PROGRESS;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.health.connect.aidl.IDataStagingFinishedCallback;
import android.health.connect.aidl.IHealthConnectService;
import android.health.connect.aidl.IMigrationCallback;
import android.health.connect.migration.MigrationEntityParcel;
import android.health.connect.migration.MigrationException;
import android.health.connect.restore.StageRemoteDataRequest;
import android.healthconnect.cts.utils.AssumptionCheckerRule;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.os.UserHandle;
import android.util.ArrayMap;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.modules.utils.testing.ExtendedMockitoRule;
import com.android.server.LocalManagerRegistry;
import com.android.server.appop.AppOpsManagerLocal;
import com.android.server.healthconnect.migration.MigrationCleaner;
import com.android.server.healthconnect.migration.MigrationStateManager;
import com.android.server.healthconnect.migration.MigrationTestUtils;
import com.android.server.healthconnect.migration.MigrationUiStateManager;
import com.android.server.healthconnect.permission.FirstGrantTimeManager;
import com.android.server.healthconnect.permission.HealthConnectPermissionHelper;
import com.android.server.healthconnect.storage.TransactionManager;
import com.android.server.healthconnect.storage.datatypehelpers.PreferenceHelper;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.quality.Strictness;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeoutException;

/** Unit test class for {@link HealthConnectServiceImpl} */
@RunWith(AndroidJUnit4.class)
public class HealthConnectServiceImplTest {
    /**
     * Health connect service APIs that blocks calls when data sync (ex: backup and restore, data
     * migration) is in progress.
     *
     * <p><b>Before adding a method name to this list, make sure the method implementation contains
     * the blocking part (i.e: {@link HealthConnectServiceImpl#throwExceptionIfDataSyncInProgress}
     * for asynchronous APIs and {@link
     * HealthConnectServiceImpl#throwIllegalStateExceptionIfDataSyncInProgress} for synchronous
     * APIs). </b>
     *
     * <p>Also, consider adding the method to {@link
     * android.healthconnect.cts.HealthConnectManagerTest#testDataApis_migrationInProgress_apisBlocked}
     * cts test.
     */
    public static final Set<String> BLOCK_CALLS_DURING_DATA_SYNC_LIST =
            Set.of(
                    "grantHealthPermission",
                    "revokeHealthPermission",
                    "revokeAllHealthPermissions",
                    "getGrantedHealthPermissions",
                    "getHealthPermissionsFlags",
                    "setHealthPermissionsUserFixedFlagValue",
                    "getHistoricalAccessStartDateInMilliseconds",
                    "insertRecords",
                    "aggregateRecords",
                    "readRecords",
                    "updateRecords",
                    "getChangeLogToken",
                    "getChangeLogs",
                    "deleteUsingFilters",
                    "deleteUsingFiltersForSelf",
                    "getCurrentPriority",
                    "updatePriority",
                    "setRecordRetentionPeriodInDays",
                    "getRecordRetentionPeriodInDays",
                    "getContributorApplicationsInfo",
                    "queryAllRecordTypesInfo",
                    "queryAccessLogs",
                    "getActivityDates",
                    "configureScheduledExport",
                    "getScheduledExportPeriodInDays");

    /** Health connect service APIs that do not block calls when data sync is in progress. */
    public static final Set<String> DO_NOT_BLOCK_CALLS_DURING_DATA_SYNC_LIST =
            Set.of(
                    "startMigration",
                    "finishMigration",
                    "writeMigrationData",
                    "stageAllHealthConnectRemoteData",
                    "getAllDataForBackup",
                    "getAllBackupFileNames",
                    "deleteAllStagedRemoteData",
                    "updateDataDownloadState",
                    "getHealthConnectDataState",
                    "getHealthConnectMigrationUiState",
                    "insertMinDataMigrationSdkExtensionVersion",
                    "asBinder");

    @Rule
    public final ExtendedMockitoRule mExtendedMockitoRule =
            new ExtendedMockitoRule.Builder(this)
                    .mockStatic(Environment.class)
                    .mockStatic(PreferenceHelper.class)
                    .mockStatic(LocalManagerRegistry.class)
                    .mockStatic(UserHandle.class)
                    .setStrictness(Strictness.LENIENT)
                    .build();

    @Mock private TransactionManager mTransactionManager;
    @Mock private HealthConnectDeviceConfigManager mDeviceConfigManager;
    @Mock private HealthConnectPermissionHelper mHealthConnectPermissionHelper;
    @Mock private MigrationCleaner mMigrationCleaner;
    @Mock private FirstGrantTimeManager mFirstGrantTimeManager;
    @Mock private MigrationStateManager mMigrationStateManager;
    @Mock private MigrationUiStateManager mMigrationUiStateManager;
    @Mock private Context mServiceContext;
    @Mock private PreferenceHelper mPreferenceHelper;
    @Mock private AppOpsManagerLocal mAppOpsManagerLocal;
    @Mock private PackageManager mPackageManager;
    @Mock IMigrationCallback mCallback;
    private Context mContext;
    private HealthConnectServiceImpl mHealthConnectService;
    private UserHandle mUserHandle;
    private File mMockDataDirectory;

    @Rule
    public AssumptionCheckerRule mSupportedHardwareRule =
            new AssumptionCheckerRule(
                    android.healthconnect.cts.utils.TestUtils::isHardwareSupported,
                    "Tests should run on supported hardware only.");

    @Before
    public void setUp() throws Exception {
        when(UserHandle.of(anyInt())).thenCallRealMethod();
        mUserHandle = UserHandle.of(UserHandle.myUserId());
        when(mServiceContext.getPackageManager()).thenReturn(mPackageManager);
        when(mServiceContext.getUser()).thenReturn(mUserHandle);

        mContext =
                new HealthConnectUserContext(
                        InstrumentationRegistry.getInstrumentation().getContext(), mUserHandle);
        mMockDataDirectory = mContext.getDir("mock_data", Context.MODE_PRIVATE);
        when(Environment.getDataDirectory()).thenReturn(mMockDataDirectory);
        when(PreferenceHelper.getInstance()).thenReturn(mPreferenceHelper);
        when(LocalManagerRegistry.getManager(AppOpsManagerLocal.class))
                .thenReturn(mAppOpsManagerLocal);

        mHealthConnectService =
                new HealthConnectServiceImpl(
                        mTransactionManager,
                        mDeviceConfigManager,
                        mHealthConnectPermissionHelper,
                        mMigrationCleaner,
                        mFirstGrantTimeManager,
                        mMigrationStateManager,
                        mMigrationUiStateManager,
                        mServiceContext);
    }

    @After
    public void tearDown() throws TimeoutException {
        TestUtils.waitForAllScheduledTasksToComplete();
        deleteDir(mMockDataDirectory);
        clearInvocations(mPreferenceHelper);
    }

    @Test
    public void testInstantiated_attachesMigrationCleanerToMigrationStateManager() {
        verify(mMigrationCleaner).attachTo(mMigrationStateManager);
    }

    @Test
    public void testStageRemoteData_withValidInput_allFilesStaged() throws Exception {
        File dataDir = mContext.getDataDir();
        File testRestoreFile1 = createAndGetNonEmptyFile(dataDir, "testRestoreFile1");
        File testRestoreFile2 = createAndGetNonEmptyFile(dataDir, "testRestoreFile2");

        assertThat(testRestoreFile1.exists()).isTrue();
        assertThat(testRestoreFile2.exists()).isTrue();

        Map<String, ParcelFileDescriptor> pfdsByFileName = new ArrayMap<>();
        pfdsByFileName.put(
                testRestoreFile1.getName(),
                ParcelFileDescriptor.open(testRestoreFile1, ParcelFileDescriptor.MODE_READ_ONLY));
        pfdsByFileName.put(
                testRestoreFile2.getName(),
                ParcelFileDescriptor.open(testRestoreFile2, ParcelFileDescriptor.MODE_READ_ONLY));

        final IDataStagingFinishedCallback callback = mock(IDataStagingFinishedCallback.class);
        mHealthConnectService.stageAllHealthConnectRemoteData(
                new StageRemoteDataRequest(pfdsByFileName), mUserHandle, callback);

        verify(callback, timeout(5000).times(1)).onResult();
        var stagedFileNames =
                mHealthConnectService.getStagedRemoteFileNames(mUserHandle.getIdentifier());
        assertThat(stagedFileNames.size()).isEqualTo(2);
        assertThat(stagedFileNames.contains(testRestoreFile1.getName())).isTrue();
        assertThat(stagedFileNames.contains(testRestoreFile2.getName())).isTrue();
    }

    @Test
    public void testStageRemoteData_withNotReadMode_onlyValidFilesStaged() throws Exception {
        File dataDir = mContext.getDataDir();
        File testRestoreFile1 = createAndGetNonEmptyFile(dataDir, "testRestoreFile1");
        File testRestoreFile2 = createAndGetNonEmptyFile(dataDir, "testRestoreFile2");

        assertThat(testRestoreFile1.exists()).isTrue();
        assertThat(testRestoreFile2.exists()).isTrue();

        Map<String, ParcelFileDescriptor> pfdsByFileName = new ArrayMap<>();
        pfdsByFileName.put(
                testRestoreFile1.getName(),
                ParcelFileDescriptor.open(testRestoreFile1, ParcelFileDescriptor.MODE_WRITE_ONLY));
        pfdsByFileName.put(
                testRestoreFile2.getName(),
                ParcelFileDescriptor.open(testRestoreFile2, ParcelFileDescriptor.MODE_READ_ONLY));

        final IDataStagingFinishedCallback callback = mock(IDataStagingFinishedCallback.class);
        mHealthConnectService.stageAllHealthConnectRemoteData(
                new StageRemoteDataRequest(pfdsByFileName), mUserHandle, callback);

        verify(callback, timeout(5000).times(1)).onError(any());
        var stagedFileNames =
                mHealthConnectService.getStagedRemoteFileNames(mUserHandle.getIdentifier());
        assertThat(stagedFileNames.size()).isEqualTo(1);
        assertThat(stagedFileNames.contains(testRestoreFile2.getName())).isTrue();
    }

    // Imitates the state when we are not actively staging but the disk reflects that.
    // Which means we were interrupted, and therefore we should stage.
    @Test
    public void testStageRemoteData_whenStagingProgress_doesStage() throws Exception {
        File dataDir = mContext.getDataDir();
        File testRestoreFile1 = createAndGetNonEmptyFile(dataDir, "testRestoreFile1");
        File testRestoreFile2 = createAndGetNonEmptyFile(dataDir, "testRestoreFile2");

        assertThat(testRestoreFile1.exists()).isTrue();
        assertThat(testRestoreFile2.exists()).isTrue();

        Map<String, ParcelFileDescriptor> pfdsByFileName = new ArrayMap<>();
        pfdsByFileName.put(
                testRestoreFile1.getName(),
                ParcelFileDescriptor.open(testRestoreFile1, ParcelFileDescriptor.MODE_READ_ONLY));
        pfdsByFileName.put(
                testRestoreFile2.getName(),
                ParcelFileDescriptor.open(testRestoreFile2, ParcelFileDescriptor.MODE_READ_ONLY));

        when(mPreferenceHelper.getPreference(eq(DATA_RESTORE_STATE_KEY)))
                .thenReturn(String.valueOf(INTERNAL_RESTORE_STATE_STAGING_IN_PROGRESS));

        final IDataStagingFinishedCallback callback = mock(IDataStagingFinishedCallback.class);
        mHealthConnectService.stageAllHealthConnectRemoteData(
                new StageRemoteDataRequest(pfdsByFileName), mUserHandle, callback);

        verify(callback, timeout(5000)).onResult();
        var stagedFileNames =
                mHealthConnectService.getStagedRemoteFileNames(mUserHandle.getIdentifier());
        assertThat(stagedFileNames.size()).isEqualTo(2);
        assertThat(stagedFileNames.contains(testRestoreFile1.getName())).isTrue();
        assertThat(stagedFileNames.contains(testRestoreFile2.getName())).isTrue();
    }

    @Test
    public void testStageRemoteData_whenStagingDone_doesNotStage() throws Exception {
        File dataDir = mContext.getDataDir();
        File testRestoreFile1 = createAndGetNonEmptyFile(dataDir, "testRestoreFile1");
        File testRestoreFile2 = createAndGetNonEmptyFile(dataDir, "testRestoreFile2");

        assertThat(testRestoreFile1.exists()).isTrue();
        assertThat(testRestoreFile2.exists()).isTrue();

        Map<String, ParcelFileDescriptor> pfdsByFileName = new ArrayMap<>();
        pfdsByFileName.put(
                testRestoreFile1.getName(),
                ParcelFileDescriptor.open(testRestoreFile1, ParcelFileDescriptor.MODE_READ_ONLY));
        pfdsByFileName.put(
                testRestoreFile2.getName(),
                ParcelFileDescriptor.open(testRestoreFile2, ParcelFileDescriptor.MODE_READ_ONLY));

        when(mPreferenceHelper.getPreference(eq(DATA_RESTORE_STATE_KEY)))
                .thenReturn(String.valueOf(INTERNAL_RESTORE_STATE_STAGING_DONE));

        final IDataStagingFinishedCallback callback = mock(IDataStagingFinishedCallback.class);
        mHealthConnectService.stageAllHealthConnectRemoteData(
                new StageRemoteDataRequest(pfdsByFileName), mUserHandle, callback);

        verify(callback, timeout(5000)).onResult();
        var stagedFileNames =
                mHealthConnectService.getStagedRemoteFileNames(mUserHandle.getIdentifier());
        assertThat(stagedFileNames.size()).isEqualTo(0);
    }

    @Test
    public void testUpdateDataDownloadState_settingValidState_setsState() {
        mHealthConnectService.updateDataDownloadState(DATA_DOWNLOAD_STARTED);
        verify(mPreferenceHelper, times(1))
                .insertOrReplacePreference(
                        eq(DATA_DOWNLOAD_STATE_KEY), eq(String.valueOf(DATA_DOWNLOAD_STARTED)));
    }

    @Test
    public void testStartMigration_noShowMigrationInfoIntentAvailable_returnsError()
            throws InterruptedException, RemoteException {
        setUpPassingPermissionCheckFor(MIGRATE_HEALTH_CONNECT_DATA);
        mHealthConnectService.startMigration(MigrationTestUtils.MOCK_CONFIGURED_PACKAGE, mCallback);
        Thread.sleep(500);
        verifyZeroInteractions(mMigrationStateManager);
        verify(mCallback).onError(any(MigrationException.class));
    }

    @Test
    public void testStartMigration_showMigrationInfoIntentAvailable()
            throws MigrationStateManager.IllegalMigrationStateException,
                    InterruptedException,
                    RemoteException {
        setUpPassingPermissionCheckFor(MIGRATE_HEALTH_CONNECT_DATA);
        MigrationTestUtils.setResolveActivityResult(new ResolveInfo(), mPackageManager);
        mHealthConnectService.startMigration(MigrationTestUtils.MOCK_CONFIGURED_PACKAGE, mCallback);
        Thread.sleep(500);
        verify(mMigrationStateManager).startMigration(mServiceContext);
    }

    @Test
    public void testFinishMigration_noShowMigrationInfoIntentAvailable_returnsError()
            throws InterruptedException, RemoteException {
        setUpPassingPermissionCheckFor(MIGRATE_HEALTH_CONNECT_DATA);
        mHealthConnectService.finishMigration(
                MigrationTestUtils.MOCK_CONFIGURED_PACKAGE, mCallback);
        Thread.sleep(500);
        verifyZeroInteractions(mMigrationStateManager);
        verify(mCallback).onError(any(MigrationException.class));
    }

    @Test
    public void testFinishMigration_showMigrationInfoIntentAvailable()
            throws MigrationStateManager.IllegalMigrationStateException,
                    InterruptedException,
                    RemoteException {
        setUpPassingPermissionCheckFor(MIGRATE_HEALTH_CONNECT_DATA);
        MigrationTestUtils.setResolveActivityResult(new ResolveInfo(), mPackageManager);
        mHealthConnectService.finishMigration(
                MigrationTestUtils.MOCK_CONFIGURED_PACKAGE, mCallback);
        Thread.sleep(500);
        verify(mMigrationStateManager).finishMigration(mServiceContext);
    }

    @Test
    public void testWriteMigration_noShowMigrationInfoIntentAvailable_returnsError()
            throws InterruptedException, RemoteException {
        setUpPassingPermissionCheckFor(MIGRATE_HEALTH_CONNECT_DATA);
        mHealthConnectService.writeMigrationData(
                MigrationTestUtils.MOCK_CONFIGURED_PACKAGE,
                mock(MigrationEntityParcel.class),
                mCallback);
        Thread.sleep(500);
        verifyZeroInteractions(mMigrationStateManager);
        verify(mCallback).onError(any(MigrationException.class));
    }

    @Test
    public void testWriteMigration_showMigrationInfoIntentAvailable()
            throws MigrationStateManager.IllegalMigrationStateException,
                    InterruptedException,
                    RemoteException {
        setUpPassingPermissionCheckFor(MIGRATE_HEALTH_CONNECT_DATA);
        MigrationTestUtils.setResolveActivityResult(new ResolveInfo(), mPackageManager);
        mHealthConnectService.writeMigrationData(
                MigrationTestUtils.MOCK_CONFIGURED_PACKAGE,
                mock(MigrationEntityParcel.class),
                mCallback);
        Thread.sleep(500);
        verify(mMigrationStateManager).validateWriteMigrationData();
        verify(mCallback).onSuccess();
    }

    @Test
    public void testInsertMinSdkExtVersion_noShowMigrationInfoIntentAvailable_returnsError()
            throws InterruptedException, RemoteException {
        setUpPassingPermissionCheckFor(MIGRATE_HEALTH_CONNECT_DATA);
        mHealthConnectService.insertMinDataMigrationSdkExtensionVersion(
                MigrationTestUtils.MOCK_CONFIGURED_PACKAGE, 0, mCallback);
        Thread.sleep(500);
        verifyZeroInteractions(mMigrationStateManager);
        verify(mCallback).onError(any(MigrationException.class));
    }

    @Test
    public void testInsertMinSdkExtVersion_showMigrationInfoIntentAvailable()
            throws MigrationStateManager.IllegalMigrationStateException,
                    InterruptedException,
                    RemoteException {
        setUpPassingPermissionCheckFor(MIGRATE_HEALTH_CONNECT_DATA);
        MigrationTestUtils.setResolveActivityResult(new ResolveInfo(), mPackageManager);
        mHealthConnectService.insertMinDataMigrationSdkExtensionVersion(
                MigrationTestUtils.MOCK_CONFIGURED_PACKAGE, 0, mCallback);
        Thread.sleep(500);
        verify(mMigrationStateManager).validateSetMinSdkVersion();
        verify(mCallback).onSuccess();
    }

    /**
     * Tests that new HealthConnect APIs block API calls during data sync using {@link
     * HealthConnectServiceImpl.BlockCallsDuringDataSync} annotation.
     *
     * <p>If the API doesn't need to block API calls during data sync(ex: backup and restore, data
     * migration), add it to the allowedApisList list yo pass this test.
     */
    @Test
    public void testHealthConnectServiceApis_blocksCallsDuringDataSync() {
        // These APIs are not expected to block API calls during data sync.

        Method[] allMethods = IHealthConnectService.class.getMethods();
        for (Method m : allMethods) {
            assertWithMessage(
                            "Method '%s' does not belong to either"
                                + " BLOCK_CALLS_DURING_DATA_SYNC_LIST or"
                                + " DO_NOT_BLOCK_CALLS_DURING_DATA_SYNC_LIST. Make sure the method"
                                + " implementation includes a section blocking calls during data"
                                + " sync, then add the method to BLOCK_CALLS_DURING_DATA_SYNC_LIST"
                                + " (check the Javadoc for this constant for more details). If the"
                                + " method must allow calls during data sync, add it to"
                                + " DO_NOT_BLOCK_CALLS_DURING_DATA_SYNC_LIST.",
                            m.getName())
                    .that(
                            DO_NOT_BLOCK_CALLS_DURING_DATA_SYNC_LIST.contains(m.getName())
                                    || BLOCK_CALLS_DURING_DATA_SYNC_LIST.contains(m.getName()))
                    .isTrue();

            assertWithMessage(
                            "Method '%s' can not belong to both BLOCK_CALLS_DURING_DATA_SYNC_LIST"
                                    + " and DO_NOT_BLOCK_CALLS_DURING_DATA_SYNC_LIST.",
                            m.getName())
                    .that(
                            DO_NOT_BLOCK_CALLS_DURING_DATA_SYNC_LIST.contains(m.getName())
                                    && BLOCK_CALLS_DURING_DATA_SYNC_LIST.contains(m.getName()))
                    .isFalse();
        }
    }

    private void setUpPassingPermissionCheckFor(String permission) {
        doNothing()
                .when(mServiceContext)
                .enforcePermission(eq(permission), anyInt(), anyInt(), anyString());
    }

    private static File createAndGetNonEmptyFile(File dir, String fileName) throws IOException {
        File file = new File(dir, fileName);
        FileWriter fileWriter = new FileWriter(file);
        fileWriter.write("Contents of file " + fileName);
        fileWriter.close();
        return file;
    }

    private static void deleteDir(File dir) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (var file : files) {
                if (file.isDirectory()) {
                    deleteDir(file);
                } else {
                    assertThat(file.delete()).isTrue();
                }
            }
        }
        assertWithMessage(
                        "Directory "
                                + dir.getAbsolutePath()
                                + " is not empty, Files present = "
                                + Arrays.toString(dir.list()))
                .that(dir.delete())
                .isTrue();
    }
}
