/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.healthconnect.controller.autodelete

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.healthconnect.controller.autodelete.api.LoadAutoDeleteUseCase
import com.android.healthconnect.controller.autodelete.api.UpdateAutoDeleteUseCase
import com.android.healthconnect.controller.shared.usecase.UseCaseResults
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

/** View model for [AutoDeleteFragment]. */
@HiltViewModel
class AutoDeleteViewModel
@Inject
constructor(
    private val loadAutoDeleteUseCase: LoadAutoDeleteUseCase,
    private val updateAutoDeleteUseCase: UpdateAutoDeleteUseCase
) : ViewModel() {

    companion object {
        private const val TAG = "AutoDeleteRange"
    }

    private val _storedAutoDeleteRange = MutableLiveData<AutoDeleteState>()
    private val _newAutoDeleteRange = MutableLiveData<AutoDeleteRange>()
    private val _oldAutoDeleteRange = MutableLiveData<AutoDeleteRange>()

    /** Holds the auto-delete range value that is stored in the database. */
    val storedAutoDeleteRange: LiveData<AutoDeleteState>
        get() = _storedAutoDeleteRange

    /** Holds the new proposed auto-delete range that the user has to confirm. */
    val newAutoDeleteRange: LiveData<AutoDeleteRange>
        get() = _newAutoDeleteRange

    /** Holds the old auto-delete range that is restored if the user cancels the update. */
    val oldAutoDeleteRange: LiveData<AutoDeleteRange>
        get() = _oldAutoDeleteRange

    init {
        loadAutoDeleteRange()
    }

    private fun loadAutoDeleteRange() {
        _storedAutoDeleteRange.postValue(AutoDeleteState.Loading)
        viewModelScope.launch {
            when (val result = loadAutoDeleteUseCase.invoke()) {
                is UseCaseResults.Success -> {
                    _storedAutoDeleteRange.postValue(
                        AutoDeleteState.WithData(fromNumberOfMonths(result.data)))
                }
                is UseCaseResults.Failed -> {
                    Log.e(TAG, "Load error ", result.exception)
                    _storedAutoDeleteRange.postValue(AutoDeleteState.LoadingFailed)
                }
            }
        }
    }

    fun updateAutoDeleteRange(autoDeleteRange: AutoDeleteRange) {
        viewModelScope.launch {
            when (val result = updateAutoDeleteUseCase.invoke(autoDeleteRange.numberOfMonths)) {
                is UseCaseResults.Success -> {
                    _storedAutoDeleteRange.postValue(AutoDeleteState.WithData(autoDeleteRange))
                }
                is UseCaseResults.Failed -> {
                    Log.e(TAG, "Update error ", result.exception)
                    _storedAutoDeleteRange.postValue(AutoDeleteState.LoadingFailed)
                }
            }
        }
    }

    fun updateAutoDeleteDialogArguments(
        newAutoDeleteRange: AutoDeleteRange,
        oldAutoDeleteRange: AutoDeleteRange
    ) {
        _newAutoDeleteRange.value = newAutoDeleteRange
        _oldAutoDeleteRange.value = oldAutoDeleteRange
    }

    sealed class AutoDeleteState {
        object Loading : AutoDeleteState()
        object LoadingFailed : AutoDeleteState()
        data class WithData(val autoDeleteRange: AutoDeleteRange) : AutoDeleteState()
    }
}
