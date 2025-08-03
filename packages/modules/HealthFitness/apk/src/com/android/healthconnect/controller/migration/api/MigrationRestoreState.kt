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
package com.android.healthconnect.controller.migration.api

/**
 * Internal class representing the [HealthConnectDataState] received from the HealthConnectManager.
 */
data class MigrationRestoreState(
    val migrationUiState: MigrationUiState,
    val dataRestoreState: DataRestoreUiState,
    val dataRestoreError: DataRestoreUiError
) {
    enum class MigrationUiState {
        IDLE,
        ALLOWED_MIGRATOR_DISABLED,
        ALLOWED_NOT_STARTED,
        ALLOWED_PAUSED,
        ALLOWED_ERROR,
        IN_PROGRESS,
        APP_UPGRADE_REQUIRED,
        MODULE_UPGRADE_REQUIRED,
        COMPLETE,
        COMPLETE_IDLE,
        UNKNOWN,
    }

    enum class DataRestoreUiState {
        IDLE,
        PENDING,
        IN_PROGRESS,
    }

    enum class DataRestoreUiError {
        ERROR_NONE,
        ERROR_UNKNOWN,
        ERROR_FETCHING_DATA,
        ERROR_VERSION_DIFF,
    }
}
