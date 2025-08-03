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

package com.android.server.healthconnect.migration;

import static android.health.connect.HealthConnectDataState.MIGRATION_STATE_ALLOWED;
import static android.health.connect.HealthConnectDataState.MIGRATION_STATE_APP_UPGRADE_REQUIRED;
import static android.health.connect.HealthConnectDataState.MIGRATION_STATE_COMPLETE;
import static android.health.connect.HealthConnectDataState.MIGRATION_STATE_IDLE;
import static android.health.connect.HealthConnectDataState.MIGRATION_STATE_IN_PROGRESS;
import static android.health.connect.HealthConnectDataState.MIGRATION_STATE_MODULE_UPGRADE_REQUIRED;

import static com.android.server.healthconnect.migration.MigrationConstants.CURRENT_STATE_START_TIME_KEY;
import static com.android.server.healthconnect.migration.MigrationConstants.HAVE_RESET_MIGRATION_STATE_KEY;
import static com.android.server.healthconnect.migration.MigrationConstants.IDLE_TIMEOUT_REACHED_KEY;
import static com.android.server.healthconnect.migration.MigrationConstants.IN_PROGRESS_TIMEOUT_REACHED_KEY;
import static com.android.server.healthconnect.migration.MigrationConstants.MIGRATION_COMPLETE_JOB_NAME;
import static com.android.server.healthconnect.migration.MigrationConstants.MIGRATION_PAUSE_JOB_NAME;
import static com.android.server.healthconnect.migration.MigrationConstants.MIGRATION_STARTS_COUNT_KEY;
import static com.android.server.healthconnect.migration.MigrationConstants.MIGRATION_STATE_PREFERENCE_KEY;
import static com.android.server.healthconnect.migration.MigrationConstants.MIN_DATA_MIGRATION_SDK_EXTENSION_VERSION_KEY;
import static com.android.server.healthconnect.migration.MigrationConstants.PREMATURE_MIGRATION_TIMEOUT_DATE;
import static com.android.server.healthconnect.migration.MigrationTestUtils.MOCK_CERTIFICATE_ONE;
import static com.android.server.healthconnect.migration.MigrationTestUtils.MOCK_CERTIFICATE_TWO;
import static com.android.server.healthconnect.migration.MigrationTestUtils.MOCK_CONFIGURED_PACKAGE;
import static com.android.server.healthconnect.migration.MigrationTestUtils.MOCK_UNCONFIGURED_PACKAGE_ONE;
import static com.android.server.healthconnect.migration.MigrationTestUtils.PERMISSIONS_TO_CHECK;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.pm.InstallSourceInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import android.content.res.Resources;
import android.health.connect.HealthConnectDataState;
import android.os.Build;
import android.os.UserHandle;
import android.os.ext.SdkExtensions;

import androidx.test.runner.AndroidJUnit4;

import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.modules.utils.testing.ExtendedMockitoRule;
import com.android.server.healthconnect.HealthConnectDeviceConfigManager;
import com.android.server.healthconnect.TestUtils;
import com.android.server.healthconnect.migration.MigrationStateManager.IllegalMigrationStateException;
import com.android.server.healthconnect.storage.datatypehelpers.PreferenceHelper;

import libcore.util.HexEncoding;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.quality.Strictness;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Test class for the MigrationStateManager class. */
@RunWith(AndroidJUnit4.class)
public class MigrationStateManagerTest {

    @Rule
    public final ExtendedMockitoRule mExtendedMockitoRule =
            new ExtendedMockitoRule.Builder(this)
                    .mockStatic(PreferenceHelper.class)
                    .mockStatic(MigrationStateChangeJob.class)
                    .mockStatic(HexEncoding.class)
                    .mockStatic(HealthConnectDeviceConfigManager.class)
                    .setStrictness(Strictness.LENIENT)
                    .build();

    @Mock private Context mContext;
    @Mock private PackageManager mPackageManager;
    @Mock private PreferenceHelper mPreferenceHelper;
    @Mock private Resources mResources;
    @Mock private PackageInfo mPackageInfo;
    @Mock private SigningInfo mSigningInfo;
    @Mock private MockListener mMockListener;
    @Mock private HealthConnectDeviceConfigManager mHealthConnectDeviceConfigManager;
    private MigrationStateManager mMigrationStateManager;

    private static final UserHandle DEFAULT_USER_HANDLE = UserHandle.of(UserHandle.myUserId());
    private static final long EXECUTION_TIME_BUFFER_MOCK_VALUE =
            TimeUnit.MINUTES.toMillis(
                    HealthConnectDeviceConfigManager
                            .EXECUTION_TIME_BUFFER_MINUTES_DEFAULT_FLAG_VALUE);
    private static final Duration NON_IDLE_STATE_TIMEOUT_MOCK_VALUE =
            Duration.ofDays(
                    HealthConnectDeviceConfigManager
                            .NON_IDLE_STATE_TIMEOUT_DAYS_DEFAULT_FLAG_VALUE);
    private static final int MAX_START_MIGRATION_CALLS_MOCK_VALUE =
            HealthConnectDeviceConfigManager.MAX_START_MIGRATION_CALLS_DEFAULT_FLAG_VALUE;

    @Before
    public void setUp() {
        when(mContext.getResources()).thenReturn(mResources);
        when(mResources.getIdentifier(anyString(), anyString(), anyString())).thenReturn(1);
        when(mResources.getString(anyInt())).thenReturn(MOCK_CONFIGURED_PACKAGE);
        when(mContext.getPackageManager()).thenReturn(mPackageManager);
        when(PreferenceHelper.getInstance()).thenReturn(mPreferenceHelper);
        when(HealthConnectDeviceConfigManager.getInitialisedInstance())
                .thenReturn(mHealthConnectDeviceConfigManager);
        when(mHealthConnectDeviceConfigManager.getExecutionTimeBuffer())
                .thenReturn(EXECUTION_TIME_BUFFER_MOCK_VALUE);
        when(mHealthConnectDeviceConfigManager.getNonIdleStateTimeoutPeriod())
                .thenReturn(NON_IDLE_STATE_TIMEOUT_MOCK_VALUE);
        when(mHealthConnectDeviceConfigManager.getMaxStartMigrationCalls())
                .thenReturn(MAX_START_MIGRATION_CALLS_MOCK_VALUE);
        MigrationStateManager.resetInitializedInstanceForTest();
        mMigrationStateManager =
                MigrationStateManager.initializeInstance(DEFAULT_USER_HANDLE.getIdentifier());
        mMigrationStateManager.addStateChangedListener(mMockListener::onMigrationStateChanged);
    }

    @After
    public void tearDown() throws TimeoutException {
        TestUtils.waitForAllScheduledTasksToComplete();
        MigrationStateManager.resetInitializedInstanceForTest();
        clearInvocations(mPreferenceHelper);
    }

    /**
     * Tests on package install method if the package is migration aware. Expected behavior: Move to
     * allowed state.
     */
    @Test
    public void testOnPackageInstalledOrChanged_fromIdleState_migrationAwarePackage() {
        setMigrationState(MIGRATION_STATE_IDLE);
        // Configure migration aware package available
        configureMigrationAwarePackage();
        mMigrationStateManager.onPackageInstalledOrChanged(mContext, MOCK_CONFIGURED_PACKAGE);
        verifyStateChange(MIGRATION_STATE_ALLOWED);
        verifyCancelAllJobs();
        verifyScheduleMigrationCompletionJob();
    }

    /**
     * Tests on package install method if the package is migration unaware. Expected behavior: Move
     * to app upgrade required state.
     */
    @Test
    public void testOnPackageInstalledOrChanged_fromIdleState_migrationUnawarePackage()
            throws PackageManager.NameNotFoundException {
        setMigrationState(MIGRATION_STATE_IDLE);
        // configure migration unaware package available
        configureMigrationUnAwarePackage();
        mMigrationStateManager.onPackageInstalledOrChanged(mContext, MOCK_CONFIGURED_PACKAGE);
        verifyStateChange(MIGRATION_STATE_APP_UPGRADE_REQUIRED);
        verifyCancelAllJobs();
        verifyScheduleMigrationCompletionJob();
    }

    /** Expected behavior: No state change. */
    @Test
    public void testOnPackageInstalledOrChanged_fromIdleState_migrationUnawareStubPackage()
            throws PackageManager.NameNotFoundException {
        setMigrationState(MIGRATION_STATE_IDLE);
        // configure migration unaware package available
        configureMigrationUnAwarePackage();
        configureStubMigratorPackage();
        mMigrationStateManager.onPackageInstalledOrChanged(mContext, MOCK_CONFIGURED_PACKAGE);
        verifyNoStateChange();
    }

    /** Expected behavior: Move to allowed state. */
    @Test
    public void
            testOnPackageInstalledOrChanged_fromAppUpgradeRequiredState_migrationAwarePackage() {
        setMigrationState(MIGRATION_STATE_APP_UPGRADE_REQUIRED);
        // configure migration unaware package available
        configureMigrationAwarePackage();
        mMigrationStateManager.onPackageInstalledOrChanged(mContext, MOCK_CONFIGURED_PACKAGE);
        verifyStateChange(MIGRATION_STATE_ALLOWED);
        verifyCancelAllJobs();
        verifyScheduleMigrationCompletionJob();
    }

    /**
     * Tests on package install or change in appUpgradeRequired state.If package is migration
     * unaware, there shouldn't be any state change. Expected behavior: No state change.
     */
    @Test
    public void
            testOnPackageInstalledOrChanged_fromAppUpgradeRequiredState_migrationUnAwarePackage()
                    throws PackageManager.NameNotFoundException {
        setMigrationState(MIGRATION_STATE_APP_UPGRADE_REQUIRED);
        // configure migration unaware package available
        configureMigrationUnAwarePackage();
        mMigrationStateManager.onPackageInstalledOrChanged(mContext, MOCK_CONFIGURED_PACKAGE);
        verifyNoStateChange();
        verifyNoJobCancelled();
        verifyNoJobScheduled();
    }

    /**
     * Package install/change should not have any effect to the state with this state. Expected
     * behavior: No state change.
     */
    @Test
    public void testOnPackageInstalledOrChanged_fromAllowedState() {
        configureMigrationAwarePackage();
        setMigrationState(MIGRATION_STATE_ALLOWED);
        mMigrationStateManager.onPackageInstalledOrChanged(mContext, MOCK_CONFIGURED_PACKAGE);
        verifyNoStateChange();
        verifyNoJobCancelled();
        verifyNoJobScheduled();
    }

    /**
     * Package install/change should not have any effect to the state with this state. Expected
     * behavior: No state change.
     */
    @Test
    public void testOnPackageInstalledOrChanged_fromInProgressState() {
        configureMigrationAwarePackage();
        setMigrationState(MIGRATION_STATE_IN_PROGRESS);
        mMigrationStateManager.onPackageInstalledOrChanged(mContext, MOCK_CONFIGURED_PACKAGE);
        verifyNoStateChange();
        verifyNoJobCancelled();
        verifyNoJobScheduled();
    }

    /**
     * Package install/change should not have any effect to the state with this state. Expected
     * behavior: No state change.
     */
    @Test
    public void testOnPackageInstalledOrChanged_fromModuleUpgradeRequiredState() {
        configureMigrationAwarePackage();
        setMigrationState(MIGRATION_STATE_MODULE_UPGRADE_REQUIRED);
        mMigrationStateManager.onPackageInstalledOrChanged(mContext, MOCK_CONFIGURED_PACKAGE);
        verifyNoStateChange();
        verifyNoJobCancelled();
        verifyNoJobScheduled();
    }

    /**
     * Package install/change should not have any effect to the state with this state. Expected
     * behavior: No state change.
     */
    @Test
    public void testOnPackageInstalledOrChanged_fromCompleteState() {
        configureMigrationAwarePackage();
        setMigrationState(MIGRATION_STATE_COMPLETE);
        mMigrationStateManager.onPackageInstalledOrChanged(mContext, MOCK_CONFIGURED_PACKAGE);
        verifyNoStateChange();
        verifyNoJobCancelled();
        verifyNoJobScheduled();
    }

    /** Expected behavior: No state change. */
    @Test
    public void testOnPackageRemoved_migratorPackageExists_nonMigratorPackagePassed() {
        configureMigrationAwarePackage();
        when(mResources.getIdentifier(anyString(), any(), any())).thenReturn(1);
        setMigrationState(MIGRATION_STATE_ALLOWED);
        mMigrationStateManager.onPackageRemoved(mContext, MOCK_UNCONFIGURED_PACKAGE_ONE);
        verifyNoMoreInteractions(mPreferenceHelper);
        verifyNoStateChange();
        verifyNoJobCancelled();
        verifyNoJobScheduled();
    }

    /**
     * Tests on package removed while migration is in idle state. Expected behavior: Move to
     * complete state.
     */
    @Test
    public void testOnPackageRemoved_fromIdleState_migrationAwarePackage() {
        configureMigrationAwarePackage();
        setMigrationState(MIGRATION_STATE_IDLE);
        mMigrationStateManager.onPackageRemoved(mContext, MOCK_CONFIGURED_PACKAGE);
        verifyStateChange(MIGRATION_STATE_COMPLETE);
        verifyCancelAllJobs();
        verifyNoJobScheduled();
    }

    /**
     * Tests on package removed while migration is in app upgrade required state. Expected behavior:
     * Move to complete state.
     */
    @Test
    public void testOnPackageRemoved_fromAppUpgradeRequiredState_migrationAwarePackage() {
        configureMigrationAwarePackage();
        setMigrationState(MIGRATION_STATE_APP_UPGRADE_REQUIRED);
        mMigrationStateManager.onPackageRemoved(mContext, MOCK_CONFIGURED_PACKAGE);
        verifyStateChange(MIGRATION_STATE_COMPLETE);
        verifyCancelAllJobs();
        verifyNoJobScheduled();
    }

    /**
     * Tests on package removed while migration is in module upgrade required state. Expected
     * behavior: Move to complete state.
     */
    @Test
    public void testOnPackageRemoved_fromModuleUpgradeRequiredState_migrationAwarePackage() {
        configureMigrationAwarePackage();
        setMigrationState(MIGRATION_STATE_MODULE_UPGRADE_REQUIRED);
        mMigrationStateManager.onPackageRemoved(mContext, MOCK_CONFIGURED_PACKAGE);
        verifyStateChange(MIGRATION_STATE_COMPLETE);
        verifyCancelAllJobs();
        verifyNoJobScheduled();
    }

    /**
     * Tests on package removed while migration is in allowed state. Expected behavior: Move to
     * complete state.
     */
    @Test
    public void testOnPackageRemoved_fromAllowedState_migrationAwarePackage() {
        configureMigrationAwarePackage();
        setMigrationState(MIGRATION_STATE_ALLOWED);
        mMigrationStateManager.onPackageRemoved(mContext, MOCK_CONFIGURED_PACKAGE);
        verifyStateChange(MIGRATION_STATE_COMPLETE);
        verifyCancelAllJobs();
        verifyNoJobScheduled();
    }

    /**
     * Tests on package removed while migration is in progress state. Expected behavior: Move to
     * complete state.
     */
    @Test
    public void testOnPackageRemoved_fromInProgressState_migrationAwarePackage() {
        configureMigrationAwarePackage();
        setMigrationState(MIGRATION_STATE_IN_PROGRESS);
        mMigrationStateManager.onPackageRemoved(mContext, MOCK_CONFIGURED_PACKAGE);
        verifyStateChange(MIGRATION_STATE_COMPLETE);
        verifyCancelAllJobs();
        verifyNoJobScheduled();
    }

    /** Expected behavior: No state change. */
    @Test
    public void testOnPackageRemoved_fromCompleteState_migrationAwarePackage() {
        configureMigrationAwarePackage();
        setMigrationState(MIGRATION_STATE_COMPLETE);
        mMigrationStateManager.onPackageRemoved(mContext, MOCK_CONFIGURED_PACKAGE);
        verifyNoStateChange();
        verifyNoJobCancelled();
        verifyNoJobScheduled();
    }

    /** Expected behavior: Move to allowed state. */
    @Test
    public void
            testReconcilePackageChangesWithStates_fromIdleState_migrationAwarePackageAvailable() {
        setMigrationState(MIGRATION_STATE_IDLE);
        // Configure migration aware package available
        configureMigrationAwarePackage();
        ExtendedMockito.doReturn(true)
                .when(
                        () ->
                                MigrationStateChangeJob.existsAStateChangeJob(
                                        eq(mContext), eq(MIGRATION_COMPLETE_JOB_NAME)));
        mMigrationStateManager.switchToSetupForUser(mContext);
        verifyStateChange(MIGRATION_STATE_ALLOWED);
        verifyCancelAllJobs();
        verifyScheduleMigrationCompletionJob();
    }

    /** Expected behavior: Move to app upgrade required state. */
    @Test
    public void
            testReconcilePackageChangesWithStates_fromIdleState_migrationUnawarePackageAvailable()
                    throws PackageManager.NameNotFoundException {
        setMigrationState(MIGRATION_STATE_IDLE);
        // Configure migration unaware package available
        configureMigrationUnAwarePackage();
        ExtendedMockito.doReturn(true)
                .when(
                        () ->
                                MigrationStateChangeJob.existsAStateChangeJob(
                                        eq(mContext), eq(MIGRATION_COMPLETE_JOB_NAME)));
        mMigrationStateManager.switchToSetupForUser(mContext);
        verifyStateChange(MIGRATION_STATE_APP_UPGRADE_REQUIRED);
        verifyCancelAllJobs();
        verifyScheduleMigrationCompletionJob();
    }

    /** Expected behavior: No state change. */
    @Test
    public void testReconcilePackageChangesWithStates_fromIdleState_migrationUnawareStubPackage()
            throws PackageManager.NameNotFoundException {
        setMigrationState(MIGRATION_STATE_IDLE);
        // Configure migration unaware package available
        configureMigrationUnAwarePackage();
        configureStubMigratorPackage();
        ExtendedMockito.doReturn(true)
                .when(
                        () ->
                                MigrationStateChangeJob.existsAStateChangeJob(
                                        eq(mContext), eq(MIGRATION_COMPLETE_JOB_NAME)));
        mMigrationStateManager.switchToSetupForUser(mContext);
        verifyNoStateChange();
    }

    /** Expected behavior: No state change */
    @Test
    public void testReconcilePackageChangesWithStates_fromIdleState_noMigratorPackageAvailable() {
        setMigrationState(MIGRATION_STATE_IDLE);
        // Configure no migrator package available
        configureNoMigratorPackage();
        mMigrationStateManager.switchToSetupForUser(mContext);
        verifyCancelAllJobs();
        verifyScheduleMigrationCompletionJob();
    }

    /**
     * Tests if migration state is ALLOWED and there is no migrator package, migration should be
     * completed. Expected behavior: Move to complete state.
     */
    @Test
    public void
            testReconcilePackageChangesWithStates_fromAllowedState_noMigratorPackageAvailable() {
        setMigrationState(MIGRATION_STATE_ALLOWED);
        // Configure no migrator package available
        configureNoMigratorPackage();
        mMigrationStateManager.switchToSetupForUser(mContext);
        verifyStateChange(MIGRATION_STATE_COMPLETE);
        verifyCancelAllJobs();
    }

    /**
     * Tests on user boot/switch with the module sdk extension version equal to the one saved in
     * preferences. Expected behavior: Move to allowed state, and schedule a migration completion
     * job.
     */
    @Test
    public void testHandleIsUpdateStillRequired_isMinSdkExtensionVersion() {
        setMigrationState(MIGRATION_STATE_MODULE_UPGRADE_REQUIRED);
        configureMigrationAwarePackage();
        when(mPreferenceHelper.getPreference(MIN_DATA_MIGRATION_SDK_EXTENSION_VERSION_KEY))
                .thenReturn(
                        String.valueOf(
                                SdkExtensions.getExtensionVersion(
                                        Build.VERSION_CODES.UPSIDE_DOWN_CAKE)));
        mMigrationStateManager.switchToSetupForUser(mContext);
        verifyStateChange(MIGRATION_STATE_ALLOWED);
        verifyCancelAllJobs();
        verifyScheduleMigrationCompletionJob();
    }

    /**
     * Tests on user boot/switch with the module sdk extension version earlier than the one saved in
     * preferences. Expected behavior: No state change.
     */
    @Test
    public void testHandleIsUpdateStillRequired_isNotMinSdkExtensionVersion() {
        setMigrationState(MIGRATION_STATE_MODULE_UPGRADE_REQUIRED);
        configureMigrationAwarePackage();
        when(mPreferenceHelper.getPreference(MIN_DATA_MIGRATION_SDK_EXTENSION_VERSION_KEY))
                .thenReturn(
                        String.valueOf(
                                SdkExtensions.getExtensionVersion(
                                                Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
                                        + 1));
        mMigrationStateManager.switchToSetupForUser(mContext);
        verifyNoStateChange();
        verifyCancelAllJobs();
        verifyScheduleMigrationCompletionJob();
    }

    /**
     * Tests on user boot/switch while no migration aware package installed. Expected behavior: Move
     * to complete state.
     */
    @Test
    public void testHandleIsUpdateStillRequired_noMigratorPackageAvailable() {
        setMigrationState(MIGRATION_STATE_MODULE_UPGRADE_REQUIRED);
        configureNoMigratorPackage();
        when(mPreferenceHelper.getPreference(MIN_DATA_MIGRATION_SDK_EXTENSION_VERSION_KEY))
                .thenReturn(
                        String.valueOf(
                                SdkExtensions.getExtensionVersion(
                                                Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
                                        + 1));
        mMigrationStateManager.switchToSetupForUser(mContext);
        verifyStateChange(MIGRATION_STATE_COMPLETE);
        verifyCancelAllJobs();
    }

    /**
     * Tests state change no when app upgrade is required and there is no migration aware package.
     * Expected behavior: Move to complete state.
     */
    @Test
    public void
            testReconcilePackageChangesWithStates_fromAppUpgradeRequiredState_noMigratorPackage() {
        setMigrationState(MIGRATION_STATE_APP_UPGRADE_REQUIRED);
        configureNoMigratorPackage();
        mMigrationStateManager.switchToSetupForUser(mContext);
        verifyStateChange(MIGRATION_STATE_COMPLETE);
        verifyCancelAllJobs();
    }

    /**
     * Tests state change no when app upgrade is required and there is a migration aware package.
     * Expected behavior: Move to allowed state.
     */
    @Test
    public void
            testReconcilePackageChangesWithStates_fromAppUpgradeRequiredState_migratorPackage() {
        setMigrationState(MIGRATION_STATE_APP_UPGRADE_REQUIRED);
        configureMigrationAwarePackage();
        ExtendedMockito.doReturn(true)
                .when(
                        () ->
                                MigrationStateChangeJob.existsAStateChangeJob(
                                        eq(mContext), eq(MIGRATION_COMPLETE_JOB_NAME)));
        mMigrationStateManager.switchToSetupForUser(mContext);
        verifyStateChange(MIGRATION_STATE_ALLOWED);
        verifyCancelAllJobs();
        verifyScheduleMigrationCompletionJob();
    }

    /**
     * Tests on user boot/switch while migration state is in_progress and there is no migration
     * aware package. Expected behavior: Move to complete state.
     */
    @Test
    public void
            testReconcilePackageChangesWithStates_fromInProgressState_noMigratorPackageAvailable() {
        setMigrationState(MIGRATION_STATE_IN_PROGRESS);
        // Configure no migrator package available
        configureNoMigratorPackage();
        mMigrationStateManager.switchToSetupForUser(mContext);
        verifyStateChange(MIGRATION_STATE_COMPLETE);
        verifyCancelAllJobs();
    }

    /**
     * Tests on user boot/switch while migration state is in_progress and there is a migration aware
     * package. Expected behavior: No state change.
     */
    @Test
    public void
            testReconcilePackageChangesWithStates_fromInProgressState_migratorPackageAvailable() {
        setMigrationState(MIGRATION_STATE_IN_PROGRESS);
        // Configure a migrator package available
        configureMigrationAwarePackage();
        mMigrationStateManager.switchToSetupForUser(mContext);
        verifyNoStateChange();
        verifyCancelAllJobs();
        verifyScheduleMigrationPauseJob();
    }

    /**
     * Tests on user boot/switch while migration state is complete. Expected behavior: No state
     * change.
     */
    @Test
    public void testReconcilePackageChangesWithStates_fromCompleteState() {
        setMigrationState(MIGRATION_STATE_COMPLETE);
        // Configure a migrator package available
        configureMigrationAwarePackage();
        mMigrationStateManager.switchToSetupForUser(mContext);
        verifyNoStateChange();
        verifyCancelAllJobs();
        verifyNoJobScheduled();
    }

    @Test
    public void testStartMigration_startMigrationCountIsUpdated() {
        int count = 0;
        setMigrationState(MIGRATION_STATE_ALLOWED);
        when(mPreferenceHelper.getPreference(eq(MIGRATION_STARTS_COUNT_KEY)))
                .thenReturn(String.valueOf(count));
        mMigrationStateManager.updateMigrationState(mContext, MIGRATION_STATE_IN_PROGRESS);
        verify(mPreferenceHelper)
                .insertOrReplacePreference(MIGRATION_STARTS_COUNT_KEY, String.valueOf(count + 1));
    }

    @Test
    public void testPauseMigration_maxStartMigrationCountReached_shouldCompleteMigration() {
        int maxStartMigrationCount = MAX_START_MIGRATION_CALLS_MOCK_VALUE;
        setMigrationState(MIGRATION_STATE_IN_PROGRESS);
        when(mPreferenceHelper.getPreference(eq(MIGRATION_STARTS_COUNT_KEY)))
                .thenReturn(String.valueOf(maxStartMigrationCount));
        mMigrationStateManager.updateMigrationState(mContext, MIGRATION_STATE_ALLOWED);
        verifyStateChange(MIGRATION_STATE_COMPLETE);
        ExtendedMockito.verify(() -> MigrationStateChangeJob.cancelAllJobs(eq(mContext)));
    }

    @Test
    public void testPauseMigration_maxStartMigrationCountNotReached_shouldNotCompleteMigration() {
        int maxStartMigrationCount = MAX_START_MIGRATION_CALLS_MOCK_VALUE;
        setMigrationState(MIGRATION_STATE_IN_PROGRESS);
        when(mPreferenceHelper.getPreference(eq(MIGRATION_STARTS_COUNT_KEY)))
                .thenReturn(String.valueOf(maxStartMigrationCount - 1));
        mMigrationStateManager.updateMigrationState(mContext, MIGRATION_STATE_ALLOWED);
        verifyStateChange(MIGRATION_STATE_ALLOWED);
        verifyCancelAllJobs();
        verifyScheduleMigrationCompletionJob();
    }

    /**
     * Expected behavior: - Change state, - Cancel any completion job available, - Schedule new
     * pause job
     */
    @Test
    public void testUpdateState_toInProgress_shouldSchedulePauseJob() {
        mMigrationStateManager.updateMigrationState(mContext, MIGRATION_STATE_IN_PROGRESS);
        verifyStateChange(MIGRATION_STATE_IN_PROGRESS);
        verifyCancelAllJobs();
        verifyScheduleMigrationPauseJob();
    }

    /**
     * Expected behavior: - Change state, - Cancel all jobs available, - Schedule new completion job
     */
    @Test
    public void testUpdateState_toComplete_shouldCancelAllJobs() {
        mMigrationStateManager.updateMigrationState(mContext, MIGRATION_STATE_COMPLETE);
        verifyStateChange(MIGRATION_STATE_COMPLETE);
        verifyCancelAllJobs();
    }

    /**
     * Expected behavior: - Change state, - Cancel any completion job available, - Schedule new
     * completion job
     */
    @Test
    public void testUpdateState_toAppUpgradeRequired_shouldScheduleCompletionJob() {
        mMigrationStateManager.updateMigrationState(mContext, MIGRATION_STATE_APP_UPGRADE_REQUIRED);
        verifyStateChange(MIGRATION_STATE_APP_UPGRADE_REQUIRED);
        verifyCancelAllJobs();
        verifyScheduleMigrationCompletionJob();
    }

    /**
     * Expected behavior: - Change state, - Cancel any completion job available, - Schedule new
     * completion job
     */
    @Test
    public void testUpdateState_toModuleUpgradeRequired_shouldScheduleCompletionJob() {
        mMigrationStateManager.updateMigrationState(
                mContext, MIGRATION_STATE_MODULE_UPGRADE_REQUIRED);
        verifyStateChange(MIGRATION_STATE_MODULE_UPGRADE_REQUIRED);
        verifyCancelAllJobs();
        verifyScheduleMigrationCompletionJob();
    }

    /**
     * Expected behavior: - Change state, - Cancel all jobs available, - Schedule new completion job
     */
    @Test
    public void testUpdateState_toAllowed_shouldScheduleCompletionJob() {
        mMigrationStateManager.updateMigrationState(mContext, MIGRATION_STATE_ALLOWED);
        verifyStateChange(MIGRATION_STATE_ALLOWED);
        verifyCancelAllJobs();
        verifyScheduleMigrationCompletionJob();
    }

    /**
     * Tests on user boot/switch. If no migration completion job scheduled, it should be scheduled.
     */
    @Test
    public void testReconcileStateChangeJob_fromIdleState_shouldReschedule() {
        setMigrationState(MIGRATION_STATE_IDLE);
        ExtendedMockito.doReturn(false)
                .when(
                        () ->
                                MigrationStateChangeJob.existsAStateChangeJob(
                                        eq(mContext), eq(MIGRATION_COMPLETE_JOB_NAME)));
        mMigrationStateManager.switchToSetupForUser(mContext);
        verifyScheduleMigrationCompletionJob();
    }

    /**
     * Tests on user boot/switch. If there is a migration completion job, no other should be
     * scheduled.
     */
    @Test
    public void testReconcileStateChangeJob_fromIdleState_shouldNotReschedule() {
        setMigrationState(MIGRATION_STATE_IDLE);
        ExtendedMockito.doReturn(true)
                .when(
                        () ->
                                MigrationStateChangeJob.existsAStateChangeJob(
                                        eq(mContext), eq(MIGRATION_COMPLETE_JOB_NAME)));
        mMigrationStateManager.switchToSetupForUser(mContext);
        verifyNoJobScheduled();
    }

    /**
     * Tests on user boot/switch. If no migration completion job scheduled, it should be scheduled.
     */
    @Test
    public void testReconcileStateChangeJob_fromAppUpgradeRequiredState_shouldReschedule() {
        setMigrationState(MIGRATION_STATE_APP_UPGRADE_REQUIRED);
        ExtendedMockito.doReturn(false)
                .when(
                        () ->
                                MigrationStateChangeJob.existsAStateChangeJob(
                                        eq(mContext), eq(MIGRATION_COMPLETE_JOB_NAME)));
        mMigrationStateManager.switchToSetupForUser(mContext);
        verifyScheduleMigrationCompletionJob();
    }

    /**
     * Tests on user boot/switch. If there is a migration completion job, no other should be
     * scheduled.
     */
    @Test
    public void testReconcileStateChangeJob_fromAppUpgradeRequiredState_shouldNotReschedule() {
        setMigrationState(MIGRATION_STATE_APP_UPGRADE_REQUIRED);
        ExtendedMockito.doReturn(true)
                .when(
                        () ->
                                MigrationStateChangeJob.existsAStateChangeJob(
                                        eq(mContext), eq(MIGRATION_COMPLETE_JOB_NAME)));
        mMigrationStateManager.switchToSetupForUser(mContext);
        verifyNoJobScheduled();
    }

    /**
     * Tests on user boot/switch. If no migration completion job scheduled, it should be scheduled.
     */
    @Test
    public void testReconcileStateChangeJob_fromAllowedState_shouldReschedule() {
        setMigrationState(MIGRATION_STATE_ALLOWED);
        ExtendedMockito.doReturn(false)
                .when(
                        () ->
                                MigrationStateChangeJob.existsAStateChangeJob(
                                        eq(mContext), eq(MIGRATION_COMPLETE_JOB_NAME)));
        mMigrationStateManager.switchToSetupForUser(mContext);
        verifyScheduleMigrationCompletionJob();
    }

    /**
     * Tests on user boot/switch. If there is a migration completion job, no other should be
     * scheduled.
     */
    @Test
    public void testReconcileStateChangeJob_fromAllowedState_shouldNotReschedule() {
        setMigrationState(MIGRATION_STATE_ALLOWED);
        ExtendedMockito.doReturn(true)
                .when(
                        () ->
                                MigrationStateChangeJob.existsAStateChangeJob(
                                        eq(mContext), eq(MIGRATION_COMPLETE_JOB_NAME)));
        mMigrationStateManager.switchToSetupForUser(mContext);
        verifyNoJobScheduled();
    }

    /** Tests on user boot/switch. If no migration pause job scheduled, it should be scheduled. */
    @Test
    public void testReconcileStateChangeJob_fromInProgressState_shouldReschedule() {
        setMigrationState(MIGRATION_STATE_IN_PROGRESS);
        ExtendedMockito.doReturn(false)
                .when(
                        () ->
                                MigrationStateChangeJob.existsAStateChangeJob(
                                        eq(mContext), eq(MIGRATION_PAUSE_JOB_NAME)));
        mMigrationStateManager.switchToSetupForUser(mContext);
        verifyScheduleMigrationPauseJob();
    }

    /**
     * Tests on user boot/switch. If there is a migration pause job, no other should be scheduled.
     */
    @Test
    public void testReconcileStateChangeJob_fromInProgressState_shouldNotReschedule() {
        setMigrationState(MIGRATION_STATE_IN_PROGRESS);
        ExtendedMockito.doReturn(true)
                .when(
                        () ->
                                MigrationStateChangeJob.existsAStateChangeJob(
                                        eq(mContext), eq(MIGRATION_PAUSE_JOB_NAME)));
        mMigrationStateManager.switchToSetupForUser(mContext);
        verifyNoJobScheduled();
    }

    /**
     * Tests on user boot/switch. If the state is complete, there should be no jobs scheduled, and
     * all existing ones should be canceled.
     */
    @Test
    public void testReconcileStateChangeJob_fromCompleteState() {
        setMigrationState(MIGRATION_STATE_COMPLETE);
        mMigrationStateManager.switchToSetupForUser(mContext);
        verifyNoJobScheduled();
        verifyCancelAllJobs();
    }

    @Test
    public void testDoesMigrationHandleInfoIntent_returnsFalse_whenDisabledPackage() {
        configureMigrationAwarePackage();
        configureMigratorDisabledPackage();
        setMigrationState(MIGRATION_STATE_ALLOWED);
        assertFalse(mMigrationStateManager.doesMigratorHandleInfoIntent(mContext));
    }

    @Test
    public void testDoesMigrationHandleInfoIntent_returnsTrue_whenEnabledPackage() {
        configureMigrationAwarePackage();
        configureMigratorEnabledPackage();
        setMigrationState(MIGRATION_STATE_ALLOWED);
        assertTrue(mMigrationStateManager.doesMigratorHandleInfoIntent(mContext));
    }

    @Test
    public void testHasInProgressStateTimedOut_returnsTrue_whenTrue() {
        when(mPreferenceHelper.getPreference(eq(IN_PROGRESS_TIMEOUT_REACHED_KEY)))
                .thenReturn(String.valueOf(true));
        assertTrue(mMigrationStateManager.hasInProgressStateTimedOut());
    }

    @Test
    public void testHasInProgressStateTimedOut_returnsFalse_whenNotSet() {
        when(mPreferenceHelper.getPreference(eq(IN_PROGRESS_TIMEOUT_REACHED_KEY))).thenReturn(null);
        assertFalse(mMigrationStateManager.hasInProgressStateTimedOut());
    }

    @Test
    public void testHasIdleStateTimedOut_returnsTrue_whenTrue() {
        when(mPreferenceHelper.getPreference(eq(IDLE_TIMEOUT_REACHED_KEY)))
                .thenReturn(String.valueOf(true));
        assertTrue(mMigrationStateManager.hasIdleStateTimedOut());
    }

    @Test
    public void testHasIdleStateTimedOut_returnsFalse_whenNotSet() {
        when(mPreferenceHelper.getPreference(eq(IDLE_TIMEOUT_REACHED_KEY))).thenReturn(null);
        assertFalse(mMigrationStateManager.hasIdleStateTimedOut());
    }

    @Test
    public void testSwitchToSetupForUser_migrationHasTimedOutPrematurely() {
        Instant mockStartTime =
                PREMATURE_MIGRATION_TIMEOUT_DATE
                        .minusDays(10)
                        .atStartOfDay()
                        .toInstant(ZoneOffset.UTC);
        when(mPreferenceHelper.getPreference(eq(CURRENT_STATE_START_TIME_KEY)))
                .thenReturn(mockStartTime.toString());
        setMigrationState(MIGRATION_STATE_COMPLETE);
        mMigrationStateManager.resetMigrationStateIfNeeded(mContext);
        verifyStateChange(MIGRATION_STATE_IDLE);
    }

    @Test
    public void testSwitchToSetupForUser_migrationHasNotTimedOut() {
        setMigrationState(MIGRATION_STATE_IDLE);
        mMigrationStateManager.resetMigrationStateIfNeeded(mContext);
        verifyNoStateChange();
    }

    @Test
    public void testSwitchToSetupForUser_migrationHasTimedOutNotPrematurely() {
        Instant mockStartTime =
                PREMATURE_MIGRATION_TIMEOUT_DATE
                        .plusDays(10)
                        .atStartOfDay()
                        .toInstant(ZoneOffset.UTC);
        when(mPreferenceHelper.getPreference(eq(CURRENT_STATE_START_TIME_KEY)))
                .thenReturn(mockStartTime.toString());
        setMigrationState(MIGRATION_STATE_COMPLETE);
        mMigrationStateManager.resetMigrationStateIfNeeded(mContext);
        verifyNoStateChange();
    }

    @Test
    public void testSwitchToSetupForUser_migrationHasTimedOutPrematurely_stateAlreadyReset() {
        Instant mockStartTime =
                PREMATURE_MIGRATION_TIMEOUT_DATE
                        .minusDays(10)
                        .atStartOfDay()
                        .toInstant(ZoneOffset.UTC);
        when(mPreferenceHelper.getPreference(eq(HAVE_RESET_MIGRATION_STATE_KEY)))
                .thenReturn(String.valueOf(true));
        when(mPreferenceHelper.getPreference(eq(CURRENT_STATE_START_TIME_KEY)))
                .thenReturn(mockStartTime.toString());
        setMigrationState(MIGRATION_STATE_COMPLETE);
        mMigrationStateManager.resetMigrationStateIfNeeded(mContext);
        verifyNoStateChange();
    }

    private void setMigrationState(int state) {
        when(mPreferenceHelper.getPreference(eq(MIGRATION_STATE_PREFERENCE_KEY)))
                .thenReturn(String.valueOf(state));
    }

    private void configureMigrationAwarePackage() {
        when(mContext.getString(anyInt())).thenReturn(MOCK_CONFIGURED_PACKAGE);
        ArrayList<PackageInfo> packageInfoArray = createPackageInfoArray(MOCK_CONFIGURED_PACKAGE);
        when(mPackageManager.getPackagesHoldingPermissions(
                        eq(PERMISSIONS_TO_CHECK), argThat(flag -> (flag.getValue() == 0))))
                .thenReturn(packageInfoArray);
        MigrationTestUtils.setResolveActivityResult(
                new ResolveInfo(),
                mPackageManager,
                PackageManager.MATCH_ALL | PackageManager.MATCH_DISABLED_COMPONENTS);
    }

    private void configureMigrationUnAwarePackage() throws PackageManager.NameNotFoundException {
        when(mContext.getString(anyInt())).thenReturn(MOCK_CONFIGURED_PACKAGE);
        when(mResources.getStringArray(anyInt())).thenReturn(getCorrectKnownSignerCertificates());
        when(mPackageManager.getPackageInfo(
                        eq(MOCK_CONFIGURED_PACKAGE), eq(PackageManager.GET_SIGNING_CERTIFICATES)))
                .thenReturn(mPackageInfo);
        mPackageInfo.signingInfo = mSigningInfo;

        when(mSigningInfo.getApkContentsSigners())
                .thenReturn(
                        new Signature[] {new Signature(getCorrectKnownSignerCertificates()[0])});
        ExtendedMockito.doReturn(getCorrectKnownSignerCertificates()[0])
                .when(() -> HexEncoding.encodeToString(any(), anyBoolean()));
        when(mPackageManager.getInstalledPackages(PackageManager.GET_SIGNING_CERTIFICATES))
                .thenReturn(createPackageInfoArray(MOCK_CONFIGURED_PACKAGE));

        InstallSourceInfo installSourceInfo = mock(InstallSourceInfo.class);
        when(mPackageManager.getInstallSourceInfo(any())).thenReturn(installSourceInfo);
        when(installSourceInfo.getInstallingPackageName()).thenReturn(MOCK_CONFIGURED_PACKAGE);
    }

    private void configureNoMigratorPackage() {
        when(mContext.getString(anyInt())).thenReturn(MOCK_UNCONFIGURED_PACKAGE_ONE);
    }

    private void configureMigratorDisabledPackage() {
        List<ResolveInfo> enabledComponents = new ArrayList<ResolveInfo>();
        when(mPackageManager.queryIntentActivities(any(), any())).thenReturn(enabledComponents);
    }

    private void configureMigratorEnabledPackage() {
        List<ResolveInfo> enabledComponents = new ArrayList<ResolveInfo>();
        enabledComponents.add(new ResolveInfo());
        when(mPackageManager.queryIntentActivities(any(), any())).thenReturn(enabledComponents);
    }

    private void configureStubMigratorPackage() throws PackageManager.NameNotFoundException {
        InstallSourceInfo installSourceInfo = mock(InstallSourceInfo.class);
        when(mPackageManager.getInstallSourceInfo(any())).thenReturn(installSourceInfo);
        when(installSourceInfo.getInstallingPackageName()).thenReturn(null);
    }

    private void verifyStateChange(int state) {
        verify(mPreferenceHelper)
                .insertOrReplacePreferencesTransaction(
                        argThat(
                                map ->
                                        map.getOrDefault(
                                                        MIGRATION_STATE_PREFERENCE_KEY,
                                                        String.valueOf(-1))
                                                .equals(String.valueOf(state))));

        verify(mMockListener).onMigrationStateChanged(state);
    }

    private void verifyNoStateChange() {
        verify(mPreferenceHelper, never()).insertOrReplacePreferencesTransaction(any());
    }

    private String[] getCorrectKnownSignerCertificates() {
        return new String[] {MOCK_CERTIFICATE_ONE, MOCK_CERTIFICATE_TWO};
    }

    private void verifyCancelAllJobs() {
        ExtendedMockito.verify(
                () -> MigrationStateChangeJob.cancelAllJobs(eq(mContext)), atLeastOnce());
    }

    private void verifyScheduleMigrationCompletionJob() {
        ExtendedMockito.verify(
                () ->
                        MigrationStateChangeJob.scheduleMigrationCompletionJob(
                                eq(mContext), anyInt()));
    }

    private void verifyScheduleMigrationPauseJob() {
        ExtendedMockito.verify(
                () -> MigrationStateChangeJob.scheduleMigrationPauseJob(eq(mContext), anyInt()));
    }

    private void verifyNoJobScheduled() {
        ExtendedMockito.verify(
                () -> MigrationStateChangeJob.scheduleMigrationPauseJob(eq(mContext), anyInt()),
                never());
        ExtendedMockito.verify(
                () ->
                        MigrationStateChangeJob.scheduleMigrationCompletionJob(
                                eq(mContext), anyInt()),
                never());
    }

    private void verifyNoJobCancelled() {
        ExtendedMockito.verify(() -> MigrationStateChangeJob.cancelAllJobs(eq(mContext)), never());
    }

    private ArrayList<PackageInfo> createPackageInfoArray(String... packageNames) {
        ArrayList<PackageInfo> packageInfoArray = new ArrayList<PackageInfo>();
        for (String packageName : packageNames) {
            PackageInfo packageInfo = new PackageInfo();
            packageInfo.packageName = packageName;
            packageInfo.signingInfo = mSigningInfo;
            packageInfoArray.add(packageInfo);
        }
        return packageInfoArray;
    }

    public static class MockListener {
        void onMigrationStateChanged(@HealthConnectDataState.DataMigrationState int state) {}
    }

    @Test
    public void testValidateSetMinSdkVersion_stateAllowed_allowedToSetSdkVersion()
            throws IllegalMigrationStateException {
        when(mPreferenceHelper.getPreference(eq(MIGRATION_STATE_PREFERENCE_KEY)))
                .thenReturn(Integer.toString(MIGRATION_STATE_ALLOWED));
        mMigrationStateManager.validateSetMinSdkVersion();
    }

    @Test(expected = IllegalMigrationStateException.class)
    public void testValidateSetMinSdkVersion_stateInProgress_notAllowedToSetSdkVersion()
            throws IllegalMigrationStateException {
        when(mPreferenceHelper.getPreference(eq(MIGRATION_STATE_PREFERENCE_KEY)))
                .thenReturn(Integer.toString(MIGRATION_STATE_IN_PROGRESS));
        mMigrationStateManager.validateSetMinSdkVersion();
    }

    @Test(expected = IllegalMigrationStateException.class)
    public void testValidateSetMinSdkVersion_stateComplete_notAllowedToSetSdkVersion()
            throws IllegalMigrationStateException {
        when(mPreferenceHelper.getPreference(eq(MIGRATION_STATE_PREFERENCE_KEY)))
                .thenReturn(Integer.toString(MIGRATION_STATE_COMPLETE));
        mMigrationStateManager.validateSetMinSdkVersion();
    }
}
