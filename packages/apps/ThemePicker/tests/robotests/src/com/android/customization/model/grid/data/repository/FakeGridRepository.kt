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

package com.android.customization.model.grid.data.repository

import com.android.customization.model.CustomizationManager
import com.android.customization.model.grid.GridOption
import com.android.customization.picker.grid.data.repository.GridRepository
import com.android.customization.picker.grid.shared.model.GridOptionItemModel
import com.android.customization.picker.grid.shared.model.GridOptionItemsModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class FakeGridRepository(
    private val scope: CoroutineScope,
    initialOptionCount: Int,
    var available: Boolean = true
) : GridRepository {
    private val _optionChanges =
        MutableSharedFlow<Unit>(
            replay = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )

    override suspend fun isAvailable(): Boolean = available

    override fun getOptionChanges(): Flow<Unit> = _optionChanges.asSharedFlow()

    private val selectedOptionIndex = MutableStateFlow(0)
    private var options: GridOptionItemsModel = createOptions(count = initialOptionCount)

    override suspend fun getOptions(): GridOptionItemsModel {
        return options
    }

    override fun getSelectedOption(): GridOption? = null

    override fun applySelectedOption(callback: CustomizationManager.Callback) {}

    override fun clearSelectedOption() {}

    override fun isSelectedOptionApplied() = false

    fun setOptions(
        count: Int,
        selectedIndex: Int = 0,
    ) {
        options = createOptions(count, selectedIndex)
        _optionChanges.tryEmit(Unit)
    }

    private fun createOptions(
        count: Int,
        selectedIndex: Int = 0,
    ): GridOptionItemsModel {
        selectedOptionIndex.value = selectedIndex
        return GridOptionItemsModel.Loaded(
            options =
                buildList {
                    repeat(times = count) { index ->
                        add(
                            GridOptionItemModel(
                                name = "option_$index",
                                cols = 4,
                                rows = index * 2,
                                isSelected =
                                    selectedOptionIndex
                                        .map { it == index }
                                        .stateIn(
                                            scope = scope,
                                            started = SharingStarted.Eagerly,
                                            initialValue = false,
                                        ),
                                onSelected = { selectedOptionIndex.value = index },
                            )
                        )
                    }
                }
        )
    }
}
