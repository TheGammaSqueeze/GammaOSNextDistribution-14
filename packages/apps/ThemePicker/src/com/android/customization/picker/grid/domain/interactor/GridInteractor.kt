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
 */

package com.android.customization.picker.grid.domain.interactor

import com.android.customization.model.CustomizationManager
import com.android.customization.model.grid.GridOption
import com.android.customization.picker.grid.data.repository.GridRepository
import com.android.customization.picker.grid.shared.model.GridOptionItemModel
import com.android.customization.picker.grid.shared.model.GridOptionItemsModel
import javax.inject.Provider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn

class GridInteractor(
    private val applicationScope: CoroutineScope,
    private val repository: GridRepository,
    private val snapshotRestorer: Provider<GridSnapshotRestorer>,
) {
    val options: Flow<GridOptionItemsModel> =
        flow { emit(repository.isAvailable()) }
            .flatMapLatest { isAvailable ->
                if (isAvailable) {
                    // this upstream flow tells us each time the options are changed.
                    repository
                        .getOptionChanges()
                        // when we start, we pretend the options _just_ changed. This way, we load
                        // something as soon as possible into the flow so it's ready by the time the
                        // first observer starts to observe.
                        .onStart { emit(Unit) }
                        // each time the options changed, we load them.
                        .map { reload() }
                        // we place the loaded options in a SharedFlow so downstream observers all
                        // share the same flow and don't trigger a new one each time they want to
                        // start observing.
                        .shareIn(
                            scope = applicationScope,
                            started = SharingStarted.WhileSubscribed(),
                            replay = 1,
                        )
                } else {
                    emptyFlow()
                }
            }

    suspend fun setSelectedOption(model: GridOptionItemModel) {
        model.onSelected.invoke()
    }

    suspend fun getSelectedOption(): GridOptionItemModel? {
        return (repository.getOptions() as? GridOptionItemsModel.Loaded)?.options?.firstOrNull {
            optionItem ->
            optionItem.isSelected.value
        }
    }

    fun getSelectOptionNonSuspend(): GridOption? = repository.getSelectedOption()

    fun clearSelectedOption() = repository.clearSelectedOption()

    fun isSelectedOptionApplied() = repository.isSelectedOptionApplied()

    fun applySelectedOption(callback: CustomizationManager.Callback) {
        repository.applySelectedOption(callback)
    }

    private suspend fun reload(): GridOptionItemsModel {
        val model = repository.getOptions()
        return if (model is GridOptionItemsModel.Loaded) {
            GridOptionItemsModel.Loaded(
                options =
                    model.options.map { option ->
                        GridOptionItemModel(
                            name = option.name,
                            cols = option.cols,
                            rows = option.rows,
                            isSelected = option.isSelected,
                            onSelected = {
                                option.onSelected()
                                snapshotRestorer.get().store(option)
                            },
                        )
                    }
            )
        } else {
            model
        }
    }
}
