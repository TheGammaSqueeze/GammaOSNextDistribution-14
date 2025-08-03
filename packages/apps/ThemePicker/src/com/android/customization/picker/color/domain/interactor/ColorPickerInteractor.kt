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
package com.android.customization.picker.color.domain.interactor

import com.android.customization.picker.color.data.repository.ColorPickerRepository
import com.android.customization.picker.color.shared.model.ColorOptionModel
import javax.inject.Provider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onEach

/** Single entry-point for all application state and business logic related to system color. */
class ColorPickerInteractor(
    private val repository: ColorPickerRepository,
    private val snapshotRestorer: Provider<ColorPickerSnapshotRestorer>,
) {
    val isApplyingSystemColor = repository.isApplyingSystemColor

    /**
     * The newly selected color option for overwriting the current active option during an
     * optimistic update, the value is set to null when update completes
     */
    private val _selectingColorOption = MutableStateFlow<ColorOptionModel?>(null)
    val selectingColorOption = _selectingColorOption.asStateFlow()

    /** List of wallpaper and preset color options on the device, categorized by Color Type */
    val colorOptions =
        repository.colorOptions.onEach {
            // Reset optimistic update value when colorOptions updates
            _selectingColorOption.value = null
        }

    suspend fun select(colorOptionModel: ColorOptionModel) {
        _selectingColorOption.value = colorOptionModel
        try {
            // Do not reset optimistic update selection on selection success because UI color is not
            // actually updated until the picker restarts. Wait to do so when updated color options
            // become available
            repository.select(colorOptionModel)
            snapshotRestorer.get().storeSnapshot(colorOptionModel)
        } catch (e: Exception) {
            _selectingColorOption.value = null
        }
    }

    fun getCurrentColorOption(): ColorOptionModel = repository.getCurrentColorOption()
}
