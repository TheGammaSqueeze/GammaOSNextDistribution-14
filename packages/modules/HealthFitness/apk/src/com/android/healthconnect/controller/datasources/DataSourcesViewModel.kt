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
package com.android.healthconnect.controller.datasources

import android.health.connect.HealthDataCategory
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.healthconnect.controller.datasources.api.ILoadMostRecentAggregationsUseCase
import com.android.healthconnect.controller.datasources.api.ILoadPotentialPriorityListUseCase
import com.android.healthconnect.controller.datasources.api.IUpdatePriorityListUseCase
import com.android.healthconnect.controller.permissiontypes.api.ILoadPriorityListUseCase
import com.android.healthconnect.controller.shared.HealthDataCategoryInt
import com.android.healthconnect.controller.shared.app.AppInfoReader
import com.android.healthconnect.controller.shared.app.AppMetadata
import com.android.healthconnect.controller.shared.usecase.UseCaseResults
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@HiltViewModel
class DataSourcesViewModel
@Inject
constructor(
    private val loadDatesWithDataUseCase: ILoadMostRecentAggregationsUseCase,
    private val loadPotentialAppSourcesUseCase: ILoadPotentialPriorityListUseCase,
    private val loadPriorityListUseCase: ILoadPriorityListUseCase,
    private val updatePriorityListUseCase: IUpdatePriorityListUseCase,
    private val appInfoReader: AppInfoReader
) : ViewModel() {

    companion object {
        private const val TAG = "DataSourcesViewModel"
    }

    private val _aggregationCardsData = MutableLiveData<AggregationCardsState>()

    private val _updatedAggregationCardsData = MutableLiveData<AggregationCardsState>()

    // Used to control the reloading of the aggregation cards after reordering the priority list
    // To avoid reloading the whole screen when only the cards need updating
    // TODO (b/305907256) improve flow by observing the aggregationCardsData directly
    val updatedAggregationCardsData: LiveData<AggregationCardsState>
        get() = _updatedAggregationCardsData

    private val _potentialAppSources = MutableLiveData<PotentialAppSourcesState>()

    private val _editedPotentialAppSources = MutableLiveData<List<AppMetadata>>()

    private val _currentPriorityList = MutableLiveData<PriorityListState>()

    private val _editedPriorityList = MutableLiveData<List<AppMetadata>>()

    private val _dataSourcesAndAggregationsInfo = MediatorLiveData<DataSourcesAndAggregationsInfo>()
    val dataSourcesAndAggregationsInfo: LiveData<DataSourcesAndAggregationsInfo>
        get() = _dataSourcesAndAggregationsInfo

    private val _dataSourcesInfo = MediatorLiveData<DataSourcesInfo>()
    val dataSourcesInfo: LiveData<DataSourcesInfo>
        get() = _dataSourcesInfo

    init {
        _dataSourcesAndAggregationsInfo.addSource(_currentPriorityList) { priorityListState ->
            if (!priorityListState.shouldObserve) {
                return@addSource
            }
            _dataSourcesAndAggregationsInfo.value =
                DataSourcesAndAggregationsInfo(
                    priorityListState = priorityListState,
                    potentialAppSourcesState = _potentialAppSources.value,
                    aggregationCardsState = _aggregationCardsData.value)
        }
        _dataSourcesAndAggregationsInfo.addSource(_potentialAppSources) { potentialAppSourcesState
            ->
            if (!potentialAppSourcesState.shouldObserve) {
                return@addSource
            }
            _dataSourcesAndAggregationsInfo.value =
                DataSourcesAndAggregationsInfo(
                    priorityListState = _currentPriorityList.value,
                    potentialAppSourcesState = potentialAppSourcesState,
                    aggregationCardsState = _aggregationCardsData.value)
        }
        _dataSourcesAndAggregationsInfo.addSource(_aggregationCardsData) { aggregationCardsState ->
            if (!aggregationCardsState.shouldObserve) {
                return@addSource
            }
            _dataSourcesAndAggregationsInfo.value =
                DataSourcesAndAggregationsInfo(
                    priorityListState = _currentPriorityList.value,
                    potentialAppSourcesState = _potentialAppSources.value,
                    aggregationCardsState = aggregationCardsState)
        }

        _dataSourcesInfo.addSource(_currentPriorityList) { priorityListState ->
            _dataSourcesInfo.value =
                DataSourcesInfo(
                    priorityListState = priorityListState,
                    potentialAppSourcesState = _potentialAppSources.value)
        }

        _dataSourcesInfo.addSource(_potentialAppSources) { potentialAppSourcesState ->
            _dataSourcesInfo.value =
                DataSourcesInfo(
                    priorityListState = _currentPriorityList.value,
                    potentialAppSourcesState = potentialAppSourcesState)
        }
    }

    private var currentSelection = HealthDataCategory.ACTIVITY

    fun getCurrentSelection(): Int = currentSelection

    fun setCurrentSelection(category: @HealthDataCategoryInt Int) {
        currentSelection = category
    }

    fun loadData(category: @HealthDataCategoryInt Int) {
        loadMostRecentAggregations(category)
        loadCurrentPriorityList(category)
        loadPotentialAppSources(category)
    }

    private fun loadMostRecentAggregations(category: @HealthDataCategoryInt Int) {
        _aggregationCardsData.postValue(AggregationCardsState.Loading(true))
        viewModelScope.launch {
            when (val aggregationInfoResult = loadDatesWithDataUseCase.invoke(category)) {
                is UseCaseResults.Success -> {
                    _aggregationCardsData.postValue(
                        AggregationCardsState.WithData(true, aggregationInfoResult.data))
                }
                is UseCaseResults.Failed -> {
                    Log.e(TAG, "Failed loading dates with data ", aggregationInfoResult.exception)
                    _aggregationCardsData.postValue(AggregationCardsState.LoadingFailed(true))
                }
            }
        }
    }

    fun loadPotentialAppSources(
        category: @HealthDataCategoryInt Int,
        shouldObserve: Boolean = true
    ) {
        _potentialAppSources.postValue(PotentialAppSourcesState.Loading(shouldObserve))
        viewModelScope.launch {
            when (val appSourcesResult = loadPotentialAppSourcesUseCase.invoke(category)) {
                is UseCaseResults.Success -> {
                    _potentialAppSources.postValue(
                        PotentialAppSourcesState.WithData(shouldObserve, appSourcesResult.data))
                }
                is UseCaseResults.Failed -> {
                    Log.e(
                        TAG,
                        "Failed to load possible priority list candidates",
                        appSourcesResult.exception)
                    _potentialAppSources.postValue(
                        PotentialAppSourcesState.LoadingFailed(shouldObserve))
                }
            }
        }
    }

    private fun loadCurrentPriorityList(category: @HealthDataCategoryInt Int) {
        _currentPriorityList.postValue(PriorityListState.Loading(true))
        viewModelScope.launch {
            when (val result = loadPriorityListUseCase.invoke(category)) {
                is UseCaseResults.Success ->
                    _currentPriorityList.postValue(
                        if (result.data.isEmpty()) {
                            PriorityListState.WithData(true, listOf())
                        } else {
                            PriorityListState.WithData(true, result.data)
                        })
                is UseCaseResults.Failed -> {
                    Log.e(TAG, "Load error ", result.exception)
                    _currentPriorityList.postValue(PriorityListState.LoadingFailed(true))
                }
            }
        }
    }

    fun updatePriorityList(newPriorityList: List<String>, category: @HealthDataCategoryInt Int) {
        _currentPriorityList.postValue(PriorityListState.Loading(false))
        viewModelScope.launch {
            updatePriorityListUseCase.invoke(newPriorityList, category)
            updateMostRecentAggregations(category)
            val appMetadataList: List<AppMetadata> =
                newPriorityList.map { appInfoReader.getAppMetadata(it) }
            _currentPriorityList.postValue(PriorityListState.WithData(false, appMetadataList))
        }
    }

    private fun updateMostRecentAggregations(category: @HealthDataCategoryInt Int) {
        _aggregationCardsData.postValue(AggregationCardsState.Loading(false))
        _updatedAggregationCardsData.postValue(AggregationCardsState.Loading(true))
        viewModelScope.launch {
            val job = async { loadDatesWithDataUseCase.invoke(category) }
            delay(1000)

            when (val aggregationInfoResult = job.await()) {
                is UseCaseResults.Success -> {
                    _aggregationCardsData.postValue(
                        AggregationCardsState.WithData(false, aggregationInfoResult.data))
                    _updatedAggregationCardsData.postValue(
                        AggregationCardsState.WithData(true, aggregationInfoResult.data))
                }
                is UseCaseResults.Failed -> {
                    Log.e(TAG, "Failed loading dates with data ", aggregationInfoResult.exception)
                    _aggregationCardsData.postValue(AggregationCardsState.LoadingFailed(false))
                    _updatedAggregationCardsData.postValue(
                        AggregationCardsState.LoadingFailed(true))
                }
            }
        }
    }

    fun setEditedPriorityList(newList: List<AppMetadata>) {
        _editedPriorityList.value = newList
    }

    fun setEditedPotentialAppSources(newList: List<AppMetadata>) {
        _editedPotentialAppSources.value = newList
    }

    fun getEditedPotentialAppSources(): List<AppMetadata> {
        return _editedPotentialAppSources.value ?: emptyList()
    }

    fun getEditedPriorityList(): List<AppMetadata> {
        return _editedPriorityList.value ?: emptyList()
    }

    sealed class AggregationCardsState(open val shouldObserve: Boolean) {
        data class Loading(override val shouldObserve: Boolean) :
            AggregationCardsState(shouldObserve)

        data class LoadingFailed(override val shouldObserve: Boolean) :
            AggregationCardsState(shouldObserve)

        data class WithData(
            override val shouldObserve: Boolean,
            val dataTotals: List<AggregationCardInfo>
        ) : AggregationCardsState(shouldObserve)
    }

    sealed class PotentialAppSourcesState(open val shouldObserve: Boolean) {
        data class Loading(override val shouldObserve: Boolean) :
            PotentialAppSourcesState(shouldObserve)

        data class LoadingFailed(override val shouldObserve: Boolean) :
            PotentialAppSourcesState(shouldObserve)

        data class WithData(
            override val shouldObserve: Boolean,
            val appSources: List<AppMetadata>
        ) : PotentialAppSourcesState(shouldObserve)
    }

    sealed class PriorityListState(open val shouldObserve: Boolean) {
        data class Loading(override val shouldObserve: Boolean) : PriorityListState(shouldObserve)

        data class LoadingFailed(override val shouldObserve: Boolean) :
            PriorityListState(shouldObserve)

        data class WithData(
            override val shouldObserve: Boolean,
            val priorityList: List<AppMetadata>
        ) : PriorityListState(shouldObserve)
    }

    class DataSourcesInfo(
        val priorityListState: PriorityListState?,
        val potentialAppSourcesState: PotentialAppSourcesState?
    ) {
        fun isLoading(): Boolean {
            return priorityListState is PriorityListState.Loading ||
                potentialAppSourcesState is PotentialAppSourcesState.Loading
        }

        fun isLoadingFailed(): Boolean {
            return priorityListState is PriorityListState.LoadingFailed ||
                potentialAppSourcesState is PotentialAppSourcesState.LoadingFailed
        }

        fun isWithData(): Boolean {
            return priorityListState is PriorityListState.WithData &&
                potentialAppSourcesState is PotentialAppSourcesState.WithData
        }
    }

    data class DataSourcesAndAggregationsInfo(
        val priorityListState: PriorityListState?,
        val potentialAppSourcesState: PotentialAppSourcesState?,
        val aggregationCardsState: AggregationCardsState?
    ) {
        fun isLoading(): Boolean {
            return priorityListState is PriorityListState.Loading ||
                potentialAppSourcesState is PotentialAppSourcesState.Loading ||
                aggregationCardsState is AggregationCardsState.Loading
        }

        fun isLoadingFailed(): Boolean {
            return priorityListState is PriorityListState.LoadingFailed ||
                potentialAppSourcesState is PotentialAppSourcesState.LoadingFailed ||
                aggregationCardsState is AggregationCardsState.LoadingFailed
        }

        fun isWithData(): Boolean {
            return priorityListState is PriorityListState.WithData &&
                potentialAppSourcesState is PotentialAppSourcesState.WithData &&
                aggregationCardsState is AggregationCardsState.WithData
        }
    }
}
