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
package com.android.healthconnect.controller.migration.api

import android.health.connect.HealthConnectDataState
import android.health.connect.migration.HealthConnectMigrationUiState
import android.util.Log
import androidx.core.os.asOutcomeReceiver
import com.android.healthconnect.controller.migration.api.MigrationRestoreState.DataRestoreUiState
import com.android.healthconnect.controller.migration.api.MigrationRestoreState.MigrationUiState
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

@Singleton
class LoadMigrationRestoreStateUseCase
@Inject
constructor(private val manager: HealthMigrationManager) {

    suspend operator fun invoke(): MigrationRestoreState {
        return withContext(Dispatchers.IO) {
            try {
                // Gets the data restore state
                val migrationRestoreState = suspendCancellableCoroutine { continuation ->
                    manager.getHealthDataState(Runnable::run, continuation.asOutcomeReceiver())
                }

                // Gets the migration UI state
                val migrationUiState =
                    suspendCancellableCoroutine { continuation ->
                            manager.getHealthConnectMigrationUiState(
                                Runnable::run, continuation.asOutcomeReceiver())
                        }
                        .healthConnectMigrationUiState

                MigrationRestoreState(
                    migrationUiState =
                        migrationUiStateMapping.getOrDefault(
                            migrationUiState, MigrationUiState.IDLE),
                    dataRestoreState =
                        dataRestoreUiStateMapping.getOrDefault(
                            migrationRestoreState.dataRestoreState, DataRestoreUiState.IDLE),
                    dataRestoreError =
                        dataRestoreUiErrorMapping.getOrDefault(
                            migrationRestoreState.dataRestoreError,
                            MigrationRestoreState.DataRestoreUiError.ERROR_NONE))
            } catch (e: Exception) {
                Log.e(TAG, "Load error ", e)
                defaultMigrationRestoreState
            }
        }
    }

    companion object {
        private const val TAG = "LoadMigrationState"

        private val migrationUiStateMapping =
            mapOf(
                HealthConnectMigrationUiState.MIGRATION_UI_STATE_IDLE to MigrationUiState.IDLE,
                HealthConnectMigrationUiState.MIGRATION_UI_STATE_ALLOWED_MIGRATOR_DISABLED to
                    MigrationUiState.ALLOWED_MIGRATOR_DISABLED,
                HealthConnectMigrationUiState.MIGRATION_UI_STATE_ALLOWED_NOT_STARTED to
                    MigrationUiState.ALLOWED_NOT_STARTED,
                HealthConnectMigrationUiState.MIGRATION_UI_STATE_ALLOWED_PAUSED to
                    MigrationUiState.ALLOWED_PAUSED,
                HealthConnectMigrationUiState.MIGRATION_UI_STATE_ALLOWED_ERROR to
                    MigrationUiState.ALLOWED_ERROR,
                HealthConnectMigrationUiState.MIGRATION_UI_STATE_IN_PROGRESS to
                    MigrationUiState.IN_PROGRESS,
                HealthConnectMigrationUiState.MIGRATION_UI_STATE_APP_UPGRADE_REQUIRED to
                    MigrationUiState.APP_UPGRADE_REQUIRED,
                HealthConnectMigrationUiState.MIGRATION_UI_STATE_MODULE_UPGRADE_REQUIRED to
                    MigrationUiState.MODULE_UPGRADE_REQUIRED,
                HealthConnectMigrationUiState.MIGRATION_UI_STATE_COMPLETE to
                    MigrationUiState.COMPLETE,
                HealthConnectMigrationUiState.MIGRATION_UI_STATE_COMPLETE_IDLE to
                    MigrationUiState.COMPLETE_IDLE,
            )

        private val dataRestoreUiStateMapping =
            mapOf(
                HealthConnectDataState.RESTORE_STATE_IDLE to DataRestoreUiState.IDLE,
                HealthConnectDataState.RESTORE_STATE_PENDING to DataRestoreUiState.PENDING,
                HealthConnectDataState.RESTORE_STATE_IN_PROGRESS to DataRestoreUiState.IN_PROGRESS,
            )

        private val dataRestoreUiErrorMapping =
            mapOf(
                HealthConnectDataState.RESTORE_ERROR_NONE to
                    MigrationRestoreState.DataRestoreUiError.ERROR_NONE,
                HealthConnectDataState.RESTORE_ERROR_UNKNOWN to
                    MigrationRestoreState.DataRestoreUiError.ERROR_UNKNOWN,
                HealthConnectDataState.RESTORE_ERROR_FETCHING_DATA to
                    MigrationRestoreState.DataRestoreUiError.ERROR_FETCHING_DATA,
                HealthConnectDataState.RESTORE_ERROR_VERSION_DIFF to
                    MigrationRestoreState.DataRestoreUiError.ERROR_VERSION_DIFF)

        private val defaultMigrationRestoreState =
            MigrationRestoreState(
                migrationUiState = MigrationUiState.IDLE,
                dataRestoreState = DataRestoreUiState.IDLE,
                dataRestoreError = MigrationRestoreState.DataRestoreUiError.ERROR_NONE)
    }
}
