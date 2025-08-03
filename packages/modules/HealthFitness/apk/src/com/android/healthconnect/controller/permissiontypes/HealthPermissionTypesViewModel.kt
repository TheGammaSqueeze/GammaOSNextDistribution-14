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
package com.android.healthconnect.controller.permissiontypes

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.healthconnect.controller.permissions.data.HealthPermissionType
import com.android.healthconnect.controller.permissiontypes.api.FilterPermissionTypesUseCase
import com.android.healthconnect.controller.permissiontypes.api.LoadContributingAppsUseCase
import com.android.healthconnect.controller.permissiontypes.api.LoadPermissionTypesUseCase
import com.android.healthconnect.controller.permissiontypes.api.LoadPriorityListUseCase
import com.android.healthconnect.controller.permissiontypes.api.UpdatePriorityListUseCase
import com.android.healthconnect.controller.shared.HealthDataCategoryInt
import com.android.healthconnect.controller.shared.app.AppInfoReader
import com.android.healthconnect.controller.shared.app.AppMetadata
import com.android.healthconnect.controller.shared.usecase.UseCaseResults
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
class HealthPermissionTypesViewModel
@Inject
constructor(
    private val loadPermissionTypesUseCase: LoadPermissionTypesUseCase,
    private val loadPriorityListUseCase: LoadPriorityListUseCase,
    private val updatePriorityListUseCase: UpdatePriorityListUseCase,
    private val appInfoReader: AppInfoReader,
    private val loadContributingAppsUseCase: LoadContributingAppsUseCase,
    private val filterPermissionTypesUseCase: FilterPermissionTypesUseCase
) : ViewModel() {

    companion object {
        private const val TAG = "PriorityListView"
    }

    private val _permissionTypesData = MutableLiveData<PermissionTypesState>()
    private val _priorityList = MutableLiveData<PriorityListState>()
    private val _appsWithData = MutableLiveData<AppsWithDataFragmentState>()
    private val _selectedAppFilter = MutableLiveData("All apps")
    private val _editedPriorityList = MutableLiveData<List<AppMetadata>>()
    private val _categoryLabel = MutableLiveData<String>()

    /** Provides a list of [HealthPermissionType]s displayed in [HealthPermissionTypesFragment]. */
    val permissionTypesData: LiveData<PermissionTypesState>
        get() = _permissionTypesData

    /**
     * Provides a list of [AppMetadata]s used in [HealthPermissionTypesFragment] and in
     * [com.android.healthconnect.controller.permissiontypes.prioritylist.PriorityListDialogFragment].
     */
    val priorityList: LiveData<PriorityListState>
        get() = _priorityList


    /** Provides a list of apps with data in [HealthPermissionTypesFragment]. */
    val appsWithData: LiveData<AppsWithDataFragmentState>
        get() = _appsWithData

    /** Stores currently selected app filter. */
    val selectedAppFilter: LiveData<String>
        get() = _selectedAppFilter

    /**
     * Provides a reordered version of [priorityList] that is shown on the priority dialog but is
     * not saved yet by the user.
     *
     * If it is empty then the original [priorityList] should be shown on the dialog.
     */
    val editedPriorityList: LiveData<List<AppMetadata>>
        get() = _editedPriorityList

    val categoryLabel: LiveData<String>
        get() = _categoryLabel

    fun setAppFilter(selectedAppFilter: String) {
        _selectedAppFilter.postValue(selectedAppFilter)
    }

    fun setEditedPriorityList(newList: List<AppMetadata>) {
        _editedPriorityList.postValue(newList)
    }

    fun setCategoryLabel(label: String) {
        _categoryLabel.postValue(label)
    }

    fun loadData(category: @HealthDataCategoryInt Int) {
        _permissionTypesData.postValue(PermissionTypesState.Loading)
        _priorityList.postValue(PriorityListState.Loading)

        viewModelScope.launch {
            val permissionTypes = loadPermissionTypesUseCase.invoke(category)
            _permissionTypesData.postValue(PermissionTypesState.WithData(permissionTypes))

            when (val result = loadPriorityListUseCase.invoke(category)) {
                is UseCaseResults.Success -> {
                    _priorityList.postValue(
                        if (result.data.isEmpty()) {
                            PriorityListState.WithData(listOf())
                        } else {
                            PriorityListState.WithData(result.data)
                        })
                }
                is UseCaseResults.Failed -> {
                    Log.e(TAG, "Load error ", result.exception)
                    _priorityList.postValue(PriorityListState.LoadingFailed)
                }
            }
        }
    }

    fun loadAppsWithData(category: @HealthDataCategoryInt Int) {
        _appsWithData.postValue(AppsWithDataFragmentState.Loading)
        viewModelScope.launch {
            val appsWithHealthPermissions = loadContributingAppsUseCase.invoke(category)
            _appsWithData.postValue(AppsWithDataFragmentState.WithData(appsWithHealthPermissions))
        }
    }

    fun filterPermissionTypes(
        category: @HealthDataCategoryInt Int,
        selectedAppPackageName: String
    ) {
        _permissionTypesData.postValue(PermissionTypesState.Loading)
        viewModelScope.launch {
            val permissionTypes =
                filterPermissionTypesUseCase.invoke(category, selectedAppPackageName)
            if (permissionTypes.isNotEmpty()) {
                _permissionTypesData.postValue(PermissionTypesState.WithData(permissionTypes))
            } else {
                val allPermissionTypes = loadPermissionTypesUseCase.invoke(category)
                _permissionTypesData.postValue(PermissionTypesState.WithData(allPermissionTypes))
            }
        }
    }

    fun updatePriorityList(category: @HealthDataCategoryInt Int, newPriorityList: List<String>) {
        _priorityList.postValue(PriorityListState.Loading)

        viewModelScope.launch {
            updatePriorityListUseCase.invoke(newPriorityList, category)
            val appMetadataList: List<AppMetadata> =
                newPriorityList.map { appInfoReader.getAppMetadata(it) }
            _priorityList.postValue(PriorityListState.WithData(appMetadataList))
        }
    }

    sealed class PermissionTypesState {
        object Loading : PermissionTypesState()
        data class WithData(val permissionTypes: List<HealthPermissionType>) :
            PermissionTypesState()
    }

    sealed class PriorityListState {
        object Loading : PriorityListState()
        object LoadingFailed : PriorityListState()
        data class WithData(val priorityList: List<AppMetadata>) : PriorityListState()
    }

    sealed class AppsWithDataFragmentState {
        object Loading : AppsWithDataFragmentState()
        data class WithData(val appsWithData: List<AppMetadata>) : AppsWithDataFragmentState()
    }
}
