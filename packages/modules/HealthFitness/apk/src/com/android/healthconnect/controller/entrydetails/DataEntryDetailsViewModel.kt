/**
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.android.healthconnect.controller.entrydetails

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.healthconnect.controller.data.entries.FormattedEntry
import com.android.healthconnect.controller.permissions.data.HealthPermissionType
import com.android.healthconnect.controller.shared.usecase.UseCaseResults
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
class DataEntryDetailsViewModel
@Inject
constructor(private val loadEntryDetailsUseCase: LoadEntryDetailsUseCase) : ViewModel() {

    companion object {
        private const val TAG = "DataEntryDetailsVM"
    }

    private val _sessionData = MutableLiveData<DateEntryFragmentState>()
    val sessionData: LiveData<DateEntryFragmentState>
        get() = _sessionData

    fun loadEntryData(
        permissionType: HealthPermissionType,
        entryId: String,
        showDataOrigin: Boolean
    ) {
        viewModelScope.launch {
            val response =
                loadEntryDetailsUseCase.invoke(
                    LoadDataEntryInput(permissionType, entryId, showDataOrigin))
            when (response) {
                is UseCaseResults.Success -> {
                    _sessionData.postValue(DateEntryFragmentState.WithData(response.data))
                }
                is UseCaseResults.Failed -> {
                    _sessionData.postValue(DateEntryFragmentState.LoadingFailed)
                    Log.e(TAG, "Failed to Load Entry!", response.exception)
                }
            }
        }
    }

    sealed class DateEntryFragmentState {
        object Loading : DateEntryFragmentState()

        object LoadingFailed : DateEntryFragmentState()

        data class WithData(val data: List<FormattedEntry>) : DateEntryFragmentState()
    }
}
