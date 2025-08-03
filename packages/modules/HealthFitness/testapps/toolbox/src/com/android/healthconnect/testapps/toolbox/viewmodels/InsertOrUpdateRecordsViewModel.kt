/**
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * ```
 *      http://www.apache.org/licenses/LICENSE-2.0
 * ```
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.android.healthconnect.testapps.toolbox.viewmodels

import android.health.connect.HealthConnectException
import android.health.connect.HealthConnectManager
import android.health.connect.datatypes.Record
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.healthconnect.testapps.toolbox.utils.GeneralUtils
import com.android.healthconnect.testapps.toolbox.utils.GeneralUtils.Companion.insertRecords
import kotlinx.coroutines.launch

class InsertOrUpdateRecordsViewModel : ViewModel() {

    private val _insertedRecordsState = MutableLiveData<InsertedRecordsState>()
    val insertedRecordsState: LiveData<InsertedRecordsState>
        get() = _insertedRecordsState

    private val _updatedRecordsState = MutableLiveData<UpdatedRecordsState>()
    val updatedRecordsState: LiveData<UpdatedRecordsState>
        get() = _updatedRecordsState

    fun insertRecordsViaViewModel(records: List<Record>, manager: HealthConnectManager) {
        viewModelScope.launch {
            try {
                val response = insertRecords(records, manager)
                _insertedRecordsState.postValue(InsertedRecordsState.WithData(response))
            } catch (exception: HealthConnectException) {
                _insertedRecordsState.postValue(
                    InsertedRecordsState.Error(exception.localizedMessage!!))
            } catch (exception: SecurityException) {
                _insertedRecordsState.postValue(
                    InsertedRecordsState.Error(exception.localizedMessage!!))
            }
        }
    }

    fun updateRecordsViaViewModel(records: List<Record>, manager: HealthConnectManager) {
        viewModelScope.launch {
            try {
                GeneralUtils.updateRecords(records, manager)
                _updatedRecordsState.postValue(UpdatedRecordsState.Success)
            } catch (exception: HealthConnectException) {
                _updatedRecordsState.postValue(
                    UpdatedRecordsState.Error(exception.localizedMessage!!))
            } catch (exception: SecurityException) {
                _updatedRecordsState.postValue(
                    UpdatedRecordsState.Error(exception.localizedMessage!!))
            }
        }
    }

    sealed class InsertedRecordsState {
        data class Error(val errorMessage: String) : InsertedRecordsState()
        data class WithData(val entries: List<Record>) : InsertedRecordsState()
    }

    sealed class UpdatedRecordsState {
        data class Error(val errorMessage: String) : UpdatedRecordsState()
        object Success : UpdatedRecordsState()
    }
}
