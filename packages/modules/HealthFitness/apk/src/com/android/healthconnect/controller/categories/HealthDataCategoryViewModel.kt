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
package com.android.healthconnect.controller.categories

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.healthconnect.controller.shared.usecase.UseCaseResults
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
class HealthDataCategoryViewModel
@Inject
constructor(
    private val loadCategoriesUseCase: LoadHealthCategoriesUseCase,
) : ViewModel() {

    private val _categoriesData = MutableLiveData<CategoriesFragmentState>()
    /**
     * Provides a list of HealthDataCategories displayed in {@link HealthDataCategoriesFragment}.
     */
    val categoriesData: LiveData<CategoriesFragmentState>
        get() = _categoriesData

    init {
        loadCategories()
    }

    fun loadCategories() {
        _categoriesData.postValue(CategoriesFragmentState.Loading)
        viewModelScope.launch {
            when (val result = loadCategoriesUseCase()) {
                is UseCaseResults.Success -> {
                    _categoriesData.postValue(CategoriesFragmentState.WithData(result.data))
                }
                is UseCaseResults.Failed -> {
                    _categoriesData.postValue(CategoriesFragmentState.Error)
                }
            }
        }
    }

    sealed class CategoriesFragmentState {
        object Loading : CategoriesFragmentState()
        object Error : CategoriesFragmentState()
        data class WithData(val categories: List<HealthCategoryUiState>) :
            CategoriesFragmentState()
    }
}
