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

package com.android.healthconnect.controller.tests.migration.api

import android.health.connect.HealthConnectDataState
import android.health.connect.migration.HealthConnectMigrationUiState.MIGRATION_UI_STATE_ALLOWED_ERROR
import android.health.connect.migration.HealthConnectMigrationUiState.MIGRATION_UI_STATE_ALLOWED_MIGRATOR_DISABLED
import android.health.connect.migration.HealthConnectMigrationUiState.MIGRATION_UI_STATE_ALLOWED_NOT_STARTED
import android.health.connect.migration.HealthConnectMigrationUiState.MIGRATION_UI_STATE_ALLOWED_PAUSED
import android.health.connect.migration.HealthConnectMigrationUiState.MIGRATION_UI_STATE_APP_UPGRADE_REQUIRED
import android.health.connect.migration.HealthConnectMigrationUiState.MIGRATION_UI_STATE_COMPLETE
import android.health.connect.migration.HealthConnectMigrationUiState.MIGRATION_UI_STATE_COMPLETE_IDLE
import android.health.connect.migration.HealthConnectMigrationUiState.MIGRATION_UI_STATE_IDLE
import android.health.connect.migration.HealthConnectMigrationUiState.MIGRATION_UI_STATE_IN_PROGRESS
import android.health.connect.migration.HealthConnectMigrationUiState.MIGRATION_UI_STATE_MODULE_UPGRADE_REQUIRED
import com.android.healthconnect.controller.migration.api.LoadMigrationRestoreStateUseCase
import com.android.healthconnect.controller.migration.api.MigrationRestoreState
import com.android.healthconnect.controller.migration.api.MigrationRestoreState.DataRestoreUiError
import com.android.healthconnect.controller.migration.api.MigrationRestoreState.DataRestoreUiState
import com.android.healthconnect.controller.migration.api.MigrationRestoreState.MigrationUiState
import com.android.healthconnect.controller.tests.utils.di.FakeHealthMigrationManager
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LoadMigrationRestoreStateUseCaseTest {

    private val migrationManager = FakeHealthMigrationManager()

    @Test
    fun invoke_migrationStateIdle_mapsStateToIdle() = runTest {
        val useCase = LoadMigrationRestoreStateUseCase(migrationManager)
        migrationManager.setMigrationUiState(MIGRATION_UI_STATE_IDLE)

        assertThat(useCase.invoke())
            .isEqualTo(
                MigrationRestoreState(
                    migrationUiState = MigrationUiState.IDLE,
                    dataRestoreState = DataRestoreUiState.IDLE,
                    dataRestoreError = DataRestoreUiError.ERROR_NONE))
    }

    @Test
    fun invoke_migrationStateAllowedMigratorDisabled_mapsStateToAllowedMigratorDisabled() =
        runTest {
            val useCase = LoadMigrationRestoreStateUseCase(migrationManager)
            migrationManager.setMigrationUiState(MIGRATION_UI_STATE_ALLOWED_MIGRATOR_DISABLED)

            assertThat(useCase.invoke())
                .isEqualTo(
                    MigrationRestoreState(
                        migrationUiState = MigrationUiState.ALLOWED_MIGRATOR_DISABLED,
                        dataRestoreState = DataRestoreUiState.IDLE,
                        dataRestoreError = DataRestoreUiError.ERROR_NONE))
        }

    @Test
    fun invoke_migrationStateAllowedNotStarted_mapsAllowedNotStarted() = runTest {
        val useCase = LoadMigrationRestoreStateUseCase(migrationManager)
        migrationManager.setMigrationUiState(MIGRATION_UI_STATE_ALLOWED_NOT_STARTED)

        assertThat(useCase.invoke())
            .isEqualTo(
                MigrationRestoreState(
                    migrationUiState = MigrationUiState.ALLOWED_NOT_STARTED,
                    dataRestoreState = DataRestoreUiState.IDLE,
                    dataRestoreError = DataRestoreUiError.ERROR_NONE))
    }

    @Test
    fun invoke_migrationStateAllowedPaused_mapsStateToAllowedPaused() = runTest {
        val useCase = LoadMigrationRestoreStateUseCase(migrationManager)
        migrationManager.setMigrationUiState(MIGRATION_UI_STATE_ALLOWED_PAUSED)

        assertThat(useCase.invoke())
            .isEqualTo(
                MigrationRestoreState(
                    migrationUiState = MigrationUiState.ALLOWED_PAUSED,
                    dataRestoreState = DataRestoreUiState.IDLE,
                    dataRestoreError = DataRestoreUiError.ERROR_NONE))
    }

    @Test
    fun invoke_migrationStateIdleAllowedError_mapsStateToAllowedError() = runTest {
        val useCase = LoadMigrationRestoreStateUseCase(migrationManager)
        migrationManager.setMigrationUiState(MIGRATION_UI_STATE_ALLOWED_ERROR)

        assertThat(useCase.invoke())
            .isEqualTo(
                MigrationRestoreState(
                    migrationUiState = MigrationUiState.ALLOWED_ERROR,
                    dataRestoreState = DataRestoreUiState.IDLE,
                    dataRestoreError = DataRestoreUiError.ERROR_NONE))
    }

    @Test
    fun invoke_migrationStateInProgress_mapsStateToInProgress() = runTest {
        val useCase = LoadMigrationRestoreStateUseCase(migrationManager)
        migrationManager.setMigrationUiState(MIGRATION_UI_STATE_IN_PROGRESS)

        assertThat(useCase.invoke())
            .isEqualTo(
                MigrationRestoreState(
                    migrationUiState = MigrationUiState.IN_PROGRESS,
                    dataRestoreState = DataRestoreUiState.IDLE,
                    dataRestoreError = DataRestoreUiError.ERROR_NONE))
    }

    @Test
    fun invoke_migrationStateAppUpgradeRequired_mapsStateToAppUpgradeRequired() = runTest {
        val useCase = LoadMigrationRestoreStateUseCase(migrationManager)
        migrationManager.setMigrationUiState(MIGRATION_UI_STATE_APP_UPGRADE_REQUIRED)

        assertThat(useCase.invoke())
            .isEqualTo(
                MigrationRestoreState(
                    migrationUiState = MigrationUiState.APP_UPGRADE_REQUIRED,
                    dataRestoreState = DataRestoreUiState.IDLE,
                    dataRestoreError = DataRestoreUiError.ERROR_NONE))
    }

    @Test
    fun invoke_migrationStateModuleUpgradeRequired_mapsStateToModuleUpgradeRequired() = runTest {
        val useCase = LoadMigrationRestoreStateUseCase(migrationManager)
        migrationManager.setMigrationUiState(MIGRATION_UI_STATE_MODULE_UPGRADE_REQUIRED)

        assertThat(useCase.invoke())
            .isEqualTo(
                MigrationRestoreState(
                    migrationUiState = MigrationUiState.MODULE_UPGRADE_REQUIRED,
                    dataRestoreState = DataRestoreUiState.IDLE,
                    dataRestoreError = DataRestoreUiError.ERROR_NONE))
    }

    @Test
    fun invoke_migrationStateComplete_mapsStateToComplete() = runTest {
        val useCase = LoadMigrationRestoreStateUseCase(migrationManager)
        migrationManager.setMigrationUiState(MIGRATION_UI_STATE_COMPLETE)

        assertThat(useCase.invoke())
            .isEqualTo(
                MigrationRestoreState(
                    migrationUiState = MigrationUiState.COMPLETE,
                    dataRestoreState = DataRestoreUiState.IDLE,
                    dataRestoreError = DataRestoreUiError.ERROR_NONE))
    }

    @Test
    fun invoke_migrationStateCompleteIdle_mapsStateToCompleteIdle() = runTest {
        val useCase = LoadMigrationRestoreStateUseCase(migrationManager)
        migrationManager.setMigrationUiState(MIGRATION_UI_STATE_COMPLETE_IDLE)

        assertThat(useCase.invoke())
            .isEqualTo(
                MigrationRestoreState(
                    migrationUiState = MigrationUiState.COMPLETE_IDLE,
                    dataRestoreState = DataRestoreUiState.IDLE,
                    dataRestoreError = DataRestoreUiError.ERROR_NONE))
    }

    @Test
    fun invoke_dataRestoreStateIdle_mapsStateToIdle() = runTest {
        val useCase = LoadMigrationRestoreStateUseCase(migrationManager)
        migrationManager.setDataMigrationState(
            HealthConnectDataState(
                HealthConnectDataState.RESTORE_STATE_IDLE,
                HealthConnectDataState.RESTORE_ERROR_NONE,
                HealthConnectDataState.MIGRATION_STATE_IDLE))
        assertThat(useCase.invoke())
            .isEqualTo(
                MigrationRestoreState(
                    migrationUiState = MigrationUiState.IDLE,
                    dataRestoreState = DataRestoreUiState.IDLE,
                    dataRestoreError = DataRestoreUiError.ERROR_NONE))
    }

    @Test
    fun invoke_dataRestoreStatePending_mapsStateToPending() = runTest {
        val useCase = LoadMigrationRestoreStateUseCase(migrationManager)
        migrationManager.setDataMigrationState(
            HealthConnectDataState(
                HealthConnectDataState.RESTORE_STATE_PENDING,
                HealthConnectDataState.RESTORE_ERROR_NONE,
                HealthConnectDataState.MIGRATION_STATE_IDLE))
        assertThat(useCase.invoke())
            .isEqualTo(
                MigrationRestoreState(
                    migrationUiState = MigrationUiState.IDLE,
                    dataRestoreState = DataRestoreUiState.PENDING,
                    dataRestoreError = DataRestoreUiError.ERROR_NONE))
    }

    @Test
    fun invoke_dataRestoreStateInProgress_mapsStateToInProgress() = runTest {
        val useCase = LoadMigrationRestoreStateUseCase(migrationManager)
        migrationManager.setDataMigrationState(
            HealthConnectDataState(
                HealthConnectDataState.RESTORE_STATE_IN_PROGRESS,
                HealthConnectDataState.RESTORE_ERROR_NONE,
                HealthConnectDataState.MIGRATION_STATE_IDLE))
        assertThat(useCase.invoke())
            .isEqualTo(
                MigrationRestoreState(
                    migrationUiState = MigrationUiState.IDLE,
                    dataRestoreState = DataRestoreUiState.IN_PROGRESS,
                    dataRestoreError = DataRestoreUiError.ERROR_NONE))
    }

    @Test
    fun invoke_dataRestoreErrorUnknown_mapsStateToErrorUnknown() = runTest {
        val useCase = LoadMigrationRestoreStateUseCase(migrationManager)
        migrationManager.setDataMigrationState(
            HealthConnectDataState(
                HealthConnectDataState.RESTORE_STATE_IDLE,
                HealthConnectDataState.RESTORE_ERROR_UNKNOWN,
                HealthConnectDataState.MIGRATION_STATE_IDLE))
        assertThat(useCase.invoke())
            .isEqualTo(
                MigrationRestoreState(
                    migrationUiState = MigrationUiState.IDLE,
                    dataRestoreState = DataRestoreUiState.IDLE,
                    dataRestoreError = DataRestoreUiError.ERROR_UNKNOWN))
    }

    @Test
    fun invoke_dataRestoreErrorFetchingData_mapsStateToErrorFetchingData() = runTest {
        val useCase = LoadMigrationRestoreStateUseCase(migrationManager)
        migrationManager.setDataMigrationState(
            HealthConnectDataState(
                HealthConnectDataState.RESTORE_STATE_IDLE,
                HealthConnectDataState.RESTORE_ERROR_FETCHING_DATA,
                HealthConnectDataState.MIGRATION_STATE_IDLE))
        assertThat(useCase.invoke())
            .isEqualTo(
                MigrationRestoreState(
                    migrationUiState = MigrationUiState.IDLE,
                    dataRestoreState = DataRestoreUiState.IDLE,
                    dataRestoreError = DataRestoreUiError.ERROR_FETCHING_DATA))
    }

    @Test
    fun invoke_dataRestoreErrorVersionDiff_mapsStateToErrorVersionDiff() = runTest {
        val useCase = LoadMigrationRestoreStateUseCase(migrationManager)
        migrationManager.setDataMigrationState(
            HealthConnectDataState(
                HealthConnectDataState.RESTORE_STATE_IDLE,
                HealthConnectDataState.RESTORE_ERROR_VERSION_DIFF,
                HealthConnectDataState.MIGRATION_STATE_IDLE))
        assertThat(useCase.invoke())
            .isEqualTo(
                MigrationRestoreState(
                    migrationUiState = MigrationUiState.IDLE,
                    dataRestoreState = DataRestoreUiState.IDLE,
                    dataRestoreError = DataRestoreUiError.ERROR_VERSION_DIFF))
    }
}
