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
 *
 *
 */
package com.android.healthconnect.controller.data.appdata

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.healthconnect.controller.shared.app.AppInfoReader
import com.android.healthconnect.controller.shared.app.AppMetadata
import com.android.healthconnect.controller.shared.usecase.UseCaseResults
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

/** View model for the [AppDataFragment] . */
@HiltViewModel
class AppDataViewModel
@Inject
constructor(
    private val appInfoReader: AppInfoReader,
    private val loadAppDataUseCase: AppDataUseCase
) : ViewModel() {

    companion object {
        private const val TAG = "AppDataViewModel"
    }

    private val _appData = MutableLiveData<AppDataState>()
    private val _appInfo = MutableLiveData<AppMetadata>()

    /** Provides a list of [PermissionTypesPerCategory]s to be displayed in [AppDataFragment]. */
    val appData: LiveData<AppDataState>
        get() = _appData

    val appInfo: LiveData<AppMetadata>
        get() = _appInfo

    fun loadAppData(packageName: String) {
        _appData.postValue(AppDataState.Loading)

        viewModelScope.launch {
            when (val result = loadAppDataUseCase.loadAppData(packageName)) {
                is UseCaseResults.Success -> {
                    _appData.postValue(AppDataState.WithData(result.data))
                }
                is UseCaseResults.Failed -> {
                    _appData.postValue(AppDataState.Error)
                }
            }
        }
    }

    fun loadAppInfo(packageName: String) {
        viewModelScope.launch { _appInfo.postValue(appInfoReader.getAppMetadata(packageName)) }
    }

    sealed class AppDataState {
        object Loading : AppDataState()

        object Error : AppDataState()

        data class WithData(val dataMap: List<PermissionTypesPerCategory>) : AppDataState()
    }
}
