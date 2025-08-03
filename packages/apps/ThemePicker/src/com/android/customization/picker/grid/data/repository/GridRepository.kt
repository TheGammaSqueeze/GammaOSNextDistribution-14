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

package com.android.customization.picker.grid.data.repository

import androidx.lifecycle.asFlow
import com.android.customization.model.CustomizationManager
import com.android.customization.model.CustomizationManager.Callback
import com.android.customization.model.grid.GridOption
import com.android.customization.model.grid.GridOptionsManager
import com.android.customization.picker.grid.shared.model.GridOptionItemModel
import com.android.customization.picker.grid.shared.model.GridOptionItemsModel
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

interface GridRepository {
    suspend fun isAvailable(): Boolean
    fun getOptionChanges(): Flow<Unit>
    suspend fun getOptions(): GridOptionItemsModel
    fun getSelectedOption(): GridOption?
    fun applySelectedOption(callback: Callback)
    fun clearSelectedOption()
    fun isSelectedOptionApplied(): Boolean
}

class GridRepositoryImpl(
    private val applicationScope: CoroutineScope,
    private val manager: GridOptionsManager,
    private val backgroundDispatcher: CoroutineDispatcher,
    private val isGridApplyButtonEnabled: Boolean,
) : GridRepository {

    override suspend fun isAvailable(): Boolean {
        return withContext(backgroundDispatcher) { manager.isAvailable }
    }

    override fun getOptionChanges(): Flow<Unit> =
        manager.getOptionChangeObservable(/* handler= */ null).asFlow().map {}

    private val selectedOption = MutableStateFlow<GridOption?>(null)

    private var appliedOption: GridOption? = null

    override fun getSelectedOption() = selectedOption.value

    override suspend fun getOptions(): GridOptionItemsModel {
        return withContext(backgroundDispatcher) {
            suspendCancellableCoroutine { continuation ->
                manager.fetchOptions(
                    object : CustomizationManager.OptionsFetchedListener<GridOption> {
                        override fun onOptionsLoaded(options: MutableList<GridOption>?) {
                            val optionsOrEmpty = options ?: emptyList()
                            // After Apply Button is added, we will rely on onSelected() method
                            // to update selectedOption.
                            if (!isGridApplyButtonEnabled || selectedOption.value == null) {
                                selectedOption.value = optionsOrEmpty.find { it.isActive(manager) }
                            }
                            if (isGridApplyButtonEnabled && appliedOption == null) {
                                appliedOption = selectedOption.value
                            }
                            continuation.resume(
                                GridOptionItemsModel.Loaded(
                                    optionsOrEmpty.map { option -> toModel(option) }
                                )
                            )
                        }

                        override fun onError(throwable: Throwable?) {
                            continuation.resume(
                                GridOptionItemsModel.Error(
                                    throwable ?: Exception("Failed to load grid options!")
                                ),
                            )
                        }
                    },
                    /* reload= */ true,
                )
            }
        }
    }

    private fun toModel(option: GridOption): GridOptionItemModel {
        return GridOptionItemModel(
            name = option.title,
            rows = option.rows,
            cols = option.cols,
            isSelected =
                selectedOption
                    .map { it.key() }
                    .map { selectedOptionKey -> option.key() == selectedOptionKey }
                    .stateIn(
                        scope = applicationScope,
                        started = SharingStarted.Eagerly,
                        initialValue = false,
                    ),
            onSelected = { onSelected(option) },
        )
    }

    private suspend fun onSelected(option: GridOption) {
        withContext(backgroundDispatcher) {
            suspendCancellableCoroutine { continuation ->
                if (isGridApplyButtonEnabled) {
                    selectedOption.value?.setIsCurrent(false)
                    selectedOption.value = option
                    selectedOption.value?.setIsCurrent(true)
                    manager.preview(option)
                    continuation.resume(true)
                } else {
                    manager.apply(
                        option,
                        object : CustomizationManager.Callback {
                            override fun onSuccess() {
                                continuation.resume(true)
                            }

                            override fun onError(throwable: Throwable?) {
                                continuation.resume(false)
                            }
                        },
                    )
                }
            }
        }
    }

    override fun applySelectedOption(callback: Callback) {
        val option = getSelectedOption()
        manager.apply(
            option,
            if (isGridApplyButtonEnabled) {
                object : Callback {
                    override fun onSuccess() {
                        callback.onSuccess()
                        appliedOption = option
                    }

                    override fun onError(throwable: Throwable?) {
                        callback.onError(throwable)
                    }
                }
            } else callback
        )
    }

    override fun clearSelectedOption() {
        if (!isGridApplyButtonEnabled) {
            return
        }
        selectedOption.value?.setIsCurrent(false)
        selectedOption.value = null
    }

    override fun isSelectedOptionApplied() = selectedOption.value?.name == appliedOption?.name

    private fun GridOption?.key(): String? {
        return if (this != null) "${cols}x${rows}" else null
    }
}
