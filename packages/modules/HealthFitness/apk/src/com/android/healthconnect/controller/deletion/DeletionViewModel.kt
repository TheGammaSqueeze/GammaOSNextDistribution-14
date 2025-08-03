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
package com.android.healthconnect.controller.deletion

import android.health.connect.TimeInstantRangeFilter
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.healthconnect.controller.deletion.api.DeleteAllDataUseCase
import com.android.healthconnect.controller.deletion.api.DeleteAppDataUseCase
import com.android.healthconnect.controller.deletion.api.DeleteCategoryUseCase
import com.android.healthconnect.controller.deletion.api.DeleteEntryUseCase
import com.android.healthconnect.controller.deletion.api.DeletePermissionTypeUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
@Deprecated("This won't be used once the NEW_INFORMATION_ARCHITECTURE feature is enabled.")
class DeletionViewModel
@Inject
constructor(
    private val deleteAllDataUseCase: DeleteAllDataUseCase,
    private val deleteCategoryUseCase: DeleteCategoryUseCase,
    private val deletePermissionTypeUseCase: DeletePermissionTypeUseCase,
    private val deleteEntryUseCase: DeleteEntryUseCase,
    private val deleteAppDataUseCase: DeleteAppDataUseCase
) : ViewModel() {

    companion object {
        private const val TAG = "DeletionViewModel"
    }

    private val _deletionParameters = MutableLiveData(DeletionParameters())
    private var _removePermissions = false
    var isInactiveApp = false

    val deletionParameters: LiveData<DeletionParameters>
        get() = _deletionParameters

    val showTimeRangeDialogFragment: Boolean
        get() = currentDeletionParameters().showTimeRangePickerDialog

    private fun currentDeletionParameters() = _deletionParameters.value!!

    fun setRemovePermissions(boolean: Boolean) {
        _removePermissions = boolean
    }

    fun setDeletionType(deletionType: DeletionType) {
        val showTimeRangePickerDialog =
            when (deletionType) {
                is DeletionType.DeleteDataEntry -> false
                else -> true
            }
        _deletionParameters.value =
            currentDeletionParameters()
                .copy(
                    showTimeRangePickerDialog = showTimeRangePickerDialog,
                    deletionType = deletionType)
    }

    fun setChosenRange(chosenRange: ChosenRange) {
        _deletionParameters.value = _deletionParameters.value?.copy(chosenRange = chosenRange)
    }

    fun setEndTime(endTime: Instant) {
        _deletionParameters.value =
            _deletionParameters.value?.copy(endTimeMs = endTime.toEpochMilli())
    }

    fun setStartTime(startTime: Instant) {
        _deletionParameters.value =
            _deletionParameters.value?.copy(startTimeMs = startTime.toEpochMilli())
    }

    private var _categoriesReloadNeeded = MutableLiveData(false)
    private val _appPermissionReloadNeeded = MutableLiveData(false)

    // Whether the categories screen needs to be reloaded after category data deletion.
    val categoriesReloadNeeded: LiveData<Boolean>
        get() = _categoriesReloadNeeded

    val appPermissionReloadNeeded: LiveData<Boolean>
        get() = _appPermissionReloadNeeded

    private fun setDeletionState(newState: DeletionState) {
        _deletionParameters.value = currentDeletionParameters().copy(deletionState = newState)
    }

    fun delete() {
        viewModelScope.launch {
            setDeletionState(DeletionState.STATE_DELETION_STARTED)

            try {

                setDeletionState(DeletionState.STATE_PROGRESS_INDICATOR_STARTED)

                val timeRangeFilter =
                    TimeInstantRangeFilter.Builder()
                        .setStartTime(currentDeletionParameters().getStartTimeInstant())
                        .setEndTime(currentDeletionParameters().getEndTimeInstant())
                        .build()

                when (val deletionType = currentDeletionParameters().deletionType) {
                    is DeletionType.DeleteDataEntry -> {
                        _deletionParameters.value?.let { deleteEntryUseCase.invoke(deletionType) }
                    }
                    is DeletionType.DeletionTypeAllData -> {
                        _deletionParameters.value?.let {
                            deleteAllDataUseCase.invoke(timeRangeFilter)
                            _categoriesReloadNeeded.postValue(true)
                        }
                    }
                    is DeletionType.DeletionTypeCategoryData -> {
                        deletionParameters.value?.let {
                            deleteCategoryUseCase.invoke(deletionType, timeRangeFilter)
                            _categoriesReloadNeeded.postValue(true)
                        }
                    }
                    is DeletionType.DeletionTypeHealthPermissionTypeData -> {
                        deletionParameters.value?.let {
                            deletePermissionTypeUseCase.invoke(deletionType, timeRangeFilter)
                        }
                    }
                    is DeletionType.DeletionTypeAppData -> {
                        deletionParameters.value?.let {
                            deleteAppDataUseCase.invoke(
                                deletionType, timeRangeFilter, _removePermissions)
                            if (_removePermissions) {
                                _appPermissionReloadNeeded.value = true
                            }
                        }
                    }
                    else -> {}
                }
                setDeletionState(DeletionState.STATE_DELETION_SUCCESSFUL)
            } catch (error: Exception) {
                Log.e(TAG, "Failed to delete data ${currentDeletionParameters()}", error)

                setDeletionState(DeletionState.STATE_DELETION_FAILED)
            } finally {
                setDeletionState(DeletionState.STATE_PROGRESS_INDICATOR_CAN_END)
            }
        }
    }
}
