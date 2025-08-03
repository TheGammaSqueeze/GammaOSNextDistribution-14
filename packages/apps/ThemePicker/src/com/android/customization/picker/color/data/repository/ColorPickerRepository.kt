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
package com.android.customization.picker.color.data.repository

import com.android.customization.picker.color.shared.model.ColorOptionModel
import com.android.customization.picker.color.shared.model.ColorType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Abstracts access to application state related to functionality for selecting, picking, or setting
 * system color.
 */
interface ColorPickerRepository {
    /** Whether the system color is in the process of being updated */
    val isApplyingSystemColor: StateFlow<Boolean>

    /** List of wallpaper and preset color options on the device, categorized by Color Type */
    val colorOptions: Flow<Map<ColorType, List<ColorOptionModel>>>

    /** Selects a color option with optimistic update */
    suspend fun select(colorOptionModel: ColorOptionModel)

    /** Returns the current selected color option based on system settings */
    fun getCurrentColorOption(): ColorOptionModel

    /** Returns the current selected color source based on system settings */
    fun getCurrentColorSource(): String?
}
