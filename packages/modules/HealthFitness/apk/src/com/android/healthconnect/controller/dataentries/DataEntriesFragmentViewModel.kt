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
package com.android.healthconnect.controller.dataentries

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.healthconnect.controller.data.entries.FormattedEntry
import com.android.healthconnect.controller.data.entries.FormattedEntry.FormattedAggregation
import com.android.healthconnect.controller.permissions.data.HealthPermissionType
import com.android.healthconnect.controller.permissions.data.HealthPermissionType.DISTANCE
import com.android.healthconnect.controller.permissions.data.HealthPermissionType.STEPS
import com.android.healthconnect.controller.permissions.data.HealthPermissionType.TOTAL_CALORIES_BURNED
import com.android.healthconnect.controller.shared.usecase.UseCaseResults
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.launch

/** View model for {@link DataEntriesFragment} . */
@HiltViewModel
class DataEntriesFragmentViewModel
@Inject
constructor(
    private val loadDataEntriesUseCase: LoadDataEntriesUseCase,
    private val loadMenstruationDataUseCase: LoadMenstruationDataUseCase,
    private val loadDataAggregationsUseCase: LoadDataAggregationsUseCase
) : ViewModel() {

    companion object {
        private const val TAG = "DataEntriesFragmentView"
        private val AGGREGATE_HEADER_DATA_TYPES = listOf(STEPS, DISTANCE, TOTAL_CALORIES_BURNED)
    }

    private val _dataEntries = MutableLiveData<DataEntriesFragmentState>()
    val dataEntries: LiveData<DataEntriesFragmentState>
        get() = _dataEntries

    val currentSelectedDate = MutableLiveData<Instant>()

    fun loadData(permissionType: HealthPermissionType, selectedDate: Instant) {
        _dataEntries.postValue(DataEntriesFragmentState.Loading)
        currentSelectedDate.postValue(selectedDate)
        viewModelScope.launch {
            val list = ArrayList<FormattedEntry>()
            val entriesResults =
                when (permissionType) {
                    // Special-casing Menstruation as it spans multiple days
                    HealthPermissionType.MENSTRUATION -> {
                        loadMenstruationDataUseCase.invoke(selectedDate)
                    }
                    else -> {
                        loadEntries(permissionType, selectedDate)
                    }
                }
            when (entriesResults) {
                is UseCaseResults.Success -> {
                    list.addAll(entriesResults.data)
                    if (list.isEmpty()) {
                        _dataEntries.postValue(DataEntriesFragmentState.Empty)
                    } else {
                        addAggregation(permissionType, selectedDate, list)
                        _dataEntries.postValue(DataEntriesFragmentState.WithData(list))
                    }
                }
                is UseCaseResults.Failed -> {
                    Log.e(TAG, "Loading error ", entriesResults.exception)
                    _dataEntries.postValue(DataEntriesFragmentState.LoadingFailed)
                }
            }
        }
    }

    private suspend fun loadAggregation(
        permissionType: HealthPermissionType,
        selectedDate: Instant
    ): UseCaseResults<FormattedAggregation> {
        val input = LoadAggregationInput(permissionType, selectedDate)
        return loadDataAggregationsUseCase.invoke(input)
    }

    private suspend fun loadEntries(
        permissionType: HealthPermissionType,
        selectedDate: Instant
    ): UseCaseResults<List<FormattedEntry>> {
        val input = LoadDataEntriesInput(permissionType, selectedDate)
        return loadDataEntriesUseCase.invoke(input)
    }

    private suspend fun addAggregation(
        permissionType: HealthPermissionType,
        selectedDate: Instant,
        list: ArrayList<FormattedEntry>
    ) {
        if (permissionType in AGGREGATE_HEADER_DATA_TYPES) {
            when (val aggregationResult = loadAggregation(permissionType, selectedDate)) {
                is UseCaseResults.Success -> {
                    list.add(0, aggregationResult.data)
                }
                is UseCaseResults.Failed -> {
                    Log.e(TAG, "Failed to load aggregation!", aggregationResult.exception)
                }
            }
        }
    }

    sealed class DataEntriesFragmentState {
        object Loading : DataEntriesFragmentState()

        object Empty : DataEntriesFragmentState()

        object LoadingFailed : DataEntriesFragmentState()

        data class WithData(val entries: List<FormattedEntry>) : DataEntriesFragmentState()
    }
}
