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

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.health.connect.HealthConnectDataState;
import android.health.connect.migration.HealthConnectMigrationUiState;
import android.os.UserHandle;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.modules.utils.testing.ExtendedMockitoRule;
import com.android.server.healthconnect.migration.notification.MigrationNotificationSender;
import com.android.server.healthconnect.storage.datatypehelpers.PreferenceHelper;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.quality.Strictness;

/** Test class for the MigrationUiStateManager class. */
@RunWith(AndroidJUnit4.class)
public class MigrationUiStateManagerTest {

    @Rule
    public final ExtendedMockitoRule mExtendedMockitoRule =
            new ExtendedMockitoRule.Builder(this)
                    .mockStatic(PreferenceHelper.class)
                    .setStrictness(Strictness.LENIENT)
                    .build();

    @Mock private Context mContext;
    @Mock private MigrationStateManager mMigrationStateManager;
    @Mock private MigrationNotificationSender mMigrationNotificationSender;
    @Mock private PreferenceHelper mPreferenceHelper;

    private MigrationUiStateManager mMigrationUiStateManager;
    private static final UserHandle DEFAULT_USER_HANDLE = UserHandle.of(UserHandle.myUserId());

    public MigrationUiStateManagerTest() {}

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        when(PreferenceHelper.getInstance()).thenReturn(mPreferenceHelper);

        mMigrationUiStateManager =
                new MigrationUiStateManager(
                        mContext,
                        DEFAULT_USER_HANDLE,
                        mMigrationStateManager,
                        mMigrationNotificationSender);
        mMigrationUiStateManager.attachTo(mMigrationStateManager);
    }

    @After
    public void tearDown() {
        clearInvocations(mPreferenceHelper);
    }

    @Test
    public void testUiManagerInitialisation_addsListenerToMigrationStateManager() {
        verify(mMigrationStateManager).addStateChangedListener(any());
    }

    @Test
    public void testStateChanged_idle_noNotificationSent() {
        when(mMigrationStateManager.getMigrationState())
                .thenReturn(HealthConnectDataState.MIGRATION_STATE_IDLE);
        final ArgumentCaptor<MigrationStateManager.StateChangedListener> captor =
                ArgumentCaptor.forClass(MigrationStateManager.StateChangedListener.class);

        verify(mMigrationStateManager).addStateChangedListener(captor.capture());
        captor.getValue().onChanged(HealthConnectDataState.MIGRATION_STATE_IDLE);
        verify(mMigrationNotificationSender, never())
                .sendNotification(anyInt(), any(UserHandle.class));
    }

    @Test
    public void testStateChanged_appUpdateNeeded_noMigrationUiAppUpdateNeededNotificationSent() {
        when(mMigrationStateManager.getMigrationState())
                .thenReturn(HealthConnectDataState.MIGRATION_STATE_APP_UPGRADE_REQUIRED);
        final ArgumentCaptor<MigrationStateManager.StateChangedListener> captor =
                ArgumentCaptor.forClass(MigrationStateManager.StateChangedListener.class);

        verify(mMigrationStateManager).addStateChangedListener(captor.capture());
        captor.getValue().onChanged(HealthConnectDataState.MIGRATION_STATE_APP_UPGRADE_REQUIRED);
        verify(mMigrationNotificationSender, never())
                .sendNotification(
                        MigrationNotificationSender.NOTIFICATION_TYPE_MIGRATION_APP_UPDATE_NEEDED,
                        DEFAULT_USER_HANDLE);
    }

    @Test
    public void
            testStateChanged_moduleUpdateNeeded_migrationUiModuleUpdateNeededNotificationSent() {
        when(mMigrationStateManager.getMigrationState())
                .thenReturn(HealthConnectDataState.MIGRATION_STATE_MODULE_UPGRADE_REQUIRED);
        final ArgumentCaptor<MigrationStateManager.StateChangedListener> captor =
                ArgumentCaptor.forClass(MigrationStateManager.StateChangedListener.class);

        verify(mMigrationStateManager).addStateChangedListener(captor.capture());
        captor.getValue().onChanged(HealthConnectDataState.MIGRATION_STATE_MODULE_UPGRADE_REQUIRED);
        verify(mMigrationNotificationSender)
                .sendNotification(
                        MigrationNotificationSender
                                .NOTIFICATION_TYPE_MIGRATION_MODULE_UPDATE_NEEDED,
                        DEFAULT_USER_HANDLE);
    }

    @Test
    public void testStateChanged_allowedPaused_noMigrationUiPausedNotificationSent() {
        when(mMigrationStateManager.getMigrationState())
                .thenReturn(HealthConnectDataState.MIGRATION_STATE_ALLOWED);
        when(mMigrationStateManager.getMigrationStartsCount()).thenReturn(0);
        when(mMigrationStateManager.existsMigrationAwarePackage(mContext)).thenReturn(true);
        when(mMigrationStateManager.existsMigratorPackage(mContext)).thenReturn(true);
        when(mMigrationStateManager.doesMigratorHandleInfoIntent(mContext)).thenReturn(true);
        final ArgumentCaptor<MigrationStateManager.StateChangedListener> captor =
                ArgumentCaptor.forClass(MigrationStateManager.StateChangedListener.class);

        verify(mMigrationStateManager).addStateChangedListener(captor.capture());
        captor.getValue().onChanged(HealthConnectDataState.MIGRATION_STATE_ALLOWED);

        verify(mMigrationNotificationSender, never())
                .sendNotification(
                        MigrationNotificationSender.NOTIFICATION_TYPE_MIGRATION_PAUSED,
                        DEFAULT_USER_HANDLE);
    }

    @Test
    public void testStateChanged_allowedError_noMigrationUiCancelledNotificationSent() {
        when(mMigrationStateManager.getMigrationState())
                .thenReturn(HealthConnectDataState.MIGRATION_STATE_ALLOWED);
        when(mMigrationStateManager.getMigrationStartsCount()).thenReturn(1);
        when(mMigrationStateManager.doesMigratorHandleInfoIntent(mContext)).thenReturn(false);
        final ArgumentCaptor<MigrationStateManager.StateChangedListener> captor =
                ArgumentCaptor.forClass(MigrationStateManager.StateChangedListener.class);

        verify(mMigrationStateManager).addStateChangedListener(captor.capture());
        captor.getValue().onChanged(HealthConnectDataState.MIGRATION_STATE_ALLOWED);

        verify(mMigrationNotificationSender, never())
                .sendNotification(
                        MigrationNotificationSender.NOTIFICATION_TYPE_MIGRATION_CANCELLED,
                        DEFAULT_USER_HANDLE);
    }

    @Test
    public void testStateChanged_inProgressPaused_migrationUiPausedNotificationSent() {
        when(mMigrationStateManager.getMigrationState())
                .thenReturn(HealthConnectDataState.MIGRATION_STATE_ALLOWED);
        when(mMigrationStateManager.getMigrationStartsCount()).thenReturn(1);
        when(mMigrationStateManager.doesMigratorHandleInfoIntent(mContext)).thenReturn(true);
        when(mMigrationStateManager.hasInProgressStateTimedOut()).thenReturn(true);
        final ArgumentCaptor<MigrationStateManager.StateChangedListener> captor =
                ArgumentCaptor.forClass(MigrationStateManager.StateChangedListener.class);

        verify(mMigrationStateManager).addStateChangedListener(captor.capture());
        captor.getValue().onChanged(HealthConnectDataState.MIGRATION_STATE_ALLOWED);

        verify(mMigrationNotificationSender)
                .sendNotification(
                        MigrationNotificationSender.NOTIFICATION_TYPE_MIGRATION_PAUSED,
                        DEFAULT_USER_HANDLE);
    }

    @Test
    public void testStateChanged_inProgress_noMigrationUiInProgressNotificationSent() {
        when(mMigrationStateManager.getMigrationState())
                .thenReturn(HealthConnectDataState.MIGRATION_STATE_IN_PROGRESS);
        final ArgumentCaptor<MigrationStateManager.StateChangedListener> captor =
                ArgumentCaptor.forClass(MigrationStateManager.StateChangedListener.class);

        verify(mMigrationStateManager).addStateChangedListener(captor.capture());
        captor.getValue().onChanged(HealthConnectDataState.MIGRATION_STATE_IN_PROGRESS);
        verify(mMigrationNotificationSender, never())
                .sendNotification(
                        MigrationNotificationSender.NOTIFICATION_TYPE_MIGRATION_IN_PROGRESS,
                        DEFAULT_USER_HANDLE);
    }

    @Test
    public void testStateChanged_completeFromIdle_noNotificationSent() {
        when(mMigrationStateManager.getMigrationState())
                .thenReturn(HealthConnectDataState.MIGRATION_STATE_COMPLETE);
        when(mMigrationStateManager.hasIdleStateTimedOut()).thenReturn(true);
        final ArgumentCaptor<MigrationStateManager.StateChangedListener> captor =
                ArgumentCaptor.forClass(MigrationStateManager.StateChangedListener.class);

        verify(mMigrationStateManager).addStateChangedListener(captor.capture());
        captor.getValue().onChanged(HealthConnectDataState.MIGRATION_STATE_COMPLETE);
        verify(mMigrationNotificationSender, never())
                .sendNotification(anyInt(), any(UserHandle.class));
    }

    @Test
    public void testStateChanged_complete_noMigrationUiCompleteNotificationSent() {
        when(mMigrationStateManager.getMigrationState())
                .thenReturn(HealthConnectDataState.MIGRATION_STATE_COMPLETE);
        when(mMigrationStateManager.hasIdleStateTimedOut()).thenReturn(false);
        final ArgumentCaptor<MigrationStateManager.StateChangedListener> captor =
                ArgumentCaptor.forClass(MigrationStateManager.StateChangedListener.class);

        verify(mMigrationStateManager).addStateChangedListener(captor.capture());
        captor.getValue().onChanged(HealthConnectDataState.MIGRATION_STATE_COMPLETE);
        verify(mMigrationNotificationSender, never())
                .sendNotification(
                        MigrationNotificationSender.NOTIFICATION_TYPE_MIGRATION_COMPLETE,
                        DEFAULT_USER_HANDLE);
    }

    @Test
    public void getMigrationUiState_migrationStateIdle() {
        when(mMigrationStateManager.getMigrationState())
                .thenReturn(HealthConnectDataState.MIGRATION_STATE_IDLE);
        final ArgumentCaptor<MigrationStateManager.StateChangedListener> captor =
                ArgumentCaptor.forClass(MigrationStateManager.StateChangedListener.class);

        verify(mMigrationStateManager).addStateChangedListener(captor.capture());
        captor.getValue().onChanged(HealthConnectDataState.MIGRATION_STATE_IDLE);
        assertThat(mMigrationUiStateManager.getHealthConnectMigrationUiState())
                .isEqualTo(HealthConnectMigrationUiState.MIGRATION_UI_STATE_IDLE);
    }

    @Test
    public void getMigrationUiState_migrationStateAppUpgradeRequired() {
        when(mMigrationStateManager.getMigrationState())
                .thenReturn(HealthConnectDataState.MIGRATION_STATE_APP_UPGRADE_REQUIRED);
        final ArgumentCaptor<MigrationStateManager.StateChangedListener> captor =
                ArgumentCaptor.forClass(MigrationStateManager.StateChangedListener.class);

        verify(mMigrationStateManager).addStateChangedListener(captor.capture());
        captor.getValue().onChanged(HealthConnectDataState.MIGRATION_STATE_APP_UPGRADE_REQUIRED);
        assertThat(mMigrationUiStateManager.getHealthConnectMigrationUiState())
                .isEqualTo(HealthConnectMigrationUiState.MIGRATION_UI_STATE_APP_UPGRADE_REQUIRED);
    }

    @Test
    public void getMigrationUiState_migrationStateModuleUpgradeRequired() {
        when(mMigrationStateManager.getMigrationState())
                .thenReturn(HealthConnectDataState.MIGRATION_STATE_MODULE_UPGRADE_REQUIRED);
        final ArgumentCaptor<MigrationStateManager.StateChangedListener> captor =
                ArgumentCaptor.forClass(MigrationStateManager.StateChangedListener.class);

        verify(mMigrationStateManager).addStateChangedListener(captor.capture());
        captor.getValue().onChanged(HealthConnectDataState.MIGRATION_STATE_MODULE_UPGRADE_REQUIRED);
        assertThat(mMigrationUiStateManager.getHealthConnectMigrationUiState())
                .isEqualTo(
                        HealthConnectMigrationUiState.MIGRATION_UI_STATE_MODULE_UPGRADE_REQUIRED);
    }

    @Test
    public void getMigrationUiState_migrationStateAllowedPaused() {
        when(mMigrationStateManager.getMigrationState())
                .thenReturn(HealthConnectDataState.MIGRATION_STATE_ALLOWED);
        when(mMigrationStateManager.getMigrationStartsCount()).thenReturn(0);
        when(mMigrationStateManager.existsMigrationAwarePackage(mContext)).thenReturn(true);
        when(mMigrationStateManager.existsMigratorPackage(mContext)).thenReturn(true);
        when(mMigrationStateManager.doesMigratorHandleInfoIntent(mContext)).thenReturn(true);
        final ArgumentCaptor<MigrationStateManager.StateChangedListener> captor =
                ArgumentCaptor.forClass(MigrationStateManager.StateChangedListener.class);

        verify(mMigrationStateManager).addStateChangedListener(captor.capture());
        captor.getValue().onChanged(HealthConnectDataState.MIGRATION_STATE_ALLOWED);
        assertThat(mMigrationUiStateManager.getHealthConnectMigrationUiState())
                .isEqualTo(HealthConnectMigrationUiState.MIGRATION_UI_STATE_ALLOWED_NOT_STARTED);
    }

    @Test
    public void getMigrationUiState_migrationStateInProgressPaused() {
        when(mMigrationStateManager.getMigrationState())
                .thenReturn(HealthConnectDataState.MIGRATION_STATE_ALLOWED);

        when(mMigrationStateManager.getMigrationStartsCount()).thenReturn(1);
        when(mMigrationStateManager.doesMigratorHandleInfoIntent(mContext)).thenReturn(true);
        when(mMigrationStateManager.hasInProgressStateTimedOut()).thenReturn(true);
        final ArgumentCaptor<MigrationStateManager.StateChangedListener> captor =
                ArgumentCaptor.forClass(MigrationStateManager.StateChangedListener.class);

        verify(mMigrationStateManager).addStateChangedListener(captor.capture());
        captor.getValue().onChanged(HealthConnectDataState.MIGRATION_STATE_ALLOWED);
        assertThat(mMigrationUiStateManager.getHealthConnectMigrationUiState())
                .isEqualTo(HealthConnectMigrationUiState.MIGRATION_UI_STATE_ALLOWED_PAUSED);
    }

    @Test
    public void getMigrationUiState_migrationStateInProgressError() {
        when(mMigrationStateManager.getMigrationState())
                .thenReturn(HealthConnectDataState.MIGRATION_STATE_ALLOWED);

        when(mMigrationStateManager.getMigrationStartsCount()).thenReturn(1);
        when(mMigrationStateManager.doesMigratorHandleInfoIntent(mContext)).thenReturn(false);
        final ArgumentCaptor<MigrationStateManager.StateChangedListener> captor =
                ArgumentCaptor.forClass(MigrationStateManager.StateChangedListener.class);

        verify(mMigrationStateManager).addStateChangedListener(captor.capture());
        captor.getValue().onChanged(HealthConnectDataState.MIGRATION_STATE_ALLOWED);
        assertThat(mMigrationUiStateManager.getHealthConnectMigrationUiState())
                .isEqualTo(HealthConnectMigrationUiState.MIGRATION_UI_STATE_ALLOWED_ERROR);
    }

    @Test
    public void getMigrationUiState_migrationStateInProgress() {
        when(mMigrationStateManager.getMigrationState())
                .thenReturn(HealthConnectDataState.MIGRATION_STATE_IN_PROGRESS);
        final ArgumentCaptor<MigrationStateManager.StateChangedListener> captor =
                ArgumentCaptor.forClass(MigrationStateManager.StateChangedListener.class);

        verify(mMigrationStateManager).addStateChangedListener(captor.capture());
        captor.getValue().onChanged(HealthConnectDataState.MIGRATION_STATE_IN_PROGRESS);
        assertThat(mMigrationUiStateManager.getHealthConnectMigrationUiState())
                .isEqualTo(HealthConnectMigrationUiState.MIGRATION_UI_STATE_IN_PROGRESS);
    }

    @Test
    public void getMigrationUiState_migrationStateCompleteFromIdle() {
        when(mMigrationStateManager.getMigrationState())
                .thenReturn(HealthConnectDataState.MIGRATION_STATE_COMPLETE);

        when(mMigrationStateManager.hasIdleStateTimedOut()).thenReturn(true);
        final ArgumentCaptor<MigrationStateManager.StateChangedListener> captor =
                ArgumentCaptor.forClass(MigrationStateManager.StateChangedListener.class);

        verify(mMigrationStateManager).addStateChangedListener(captor.capture());
        captor.getValue().onChanged(HealthConnectDataState.MIGRATION_STATE_COMPLETE);
        assertThat(mMigrationUiStateManager.getHealthConnectMigrationUiState())
                .isEqualTo(HealthConnectMigrationUiState.MIGRATION_UI_STATE_COMPLETE_IDLE);
    }

    @Test
    public void getMigrationUiState_migrationStateComplete() {
        when(mMigrationStateManager.getMigrationState())
                .thenReturn(HealthConnectDataState.MIGRATION_STATE_COMPLETE);
        when(mMigrationStateManager.hasIdleStateTimedOut()).thenReturn(false);
        final ArgumentCaptor<MigrationStateManager.StateChangedListener> captor =
                ArgumentCaptor.forClass(MigrationStateManager.StateChangedListener.class);

        verify(mMigrationStateManager).addStateChangedListener(captor.capture());
        captor.getValue().onChanged(HealthConnectDataState.MIGRATION_STATE_COMPLETE);
        assertThat(mMigrationUiStateManager.getHealthConnectMigrationUiState())
                .isEqualTo(HealthConnectMigrationUiState.MIGRATION_UI_STATE_COMPLETE);
    }
}
