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
package com.android.healthconnect.controller.migration

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.healthconnect.controller.migration.api.LoadMigrationRestoreStateUseCase
import com.android.healthconnect.controller.migration.api.MigrationRestoreState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

@HiltViewModel
class MigrationViewModel
@Inject
constructor(
    private val loadMigrationRestoreStateUseCase: LoadMigrationRestoreStateUseCase,
) : ViewModel() {

    private val _migrationState = MutableLiveData<MigrationFragmentState>()
    val migrationState: LiveData<MigrationFragmentState>
        get() = _migrationState

    init {
        loadHealthConnectMigrationUiState()
    }

    private fun loadHealthConnectMigrationUiState() {
        viewModelScope.launch {
            _migrationState.postValue(
                MigrationFragmentState.WithData(loadMigrationRestoreStateUseCase.invoke()))
        }
    }

    fun getCurrentMigrationUiState(): MigrationRestoreState {
        return runBlocking { loadMigrationRestoreStateUseCase.invoke() }
    }

    sealed class MigrationFragmentState {
        object Loading : MigrationFragmentState()

        object Error : MigrationFragmentState()

        data class WithData(val migrationRestoreState: MigrationRestoreState) :
            MigrationFragmentState()
    }
}
