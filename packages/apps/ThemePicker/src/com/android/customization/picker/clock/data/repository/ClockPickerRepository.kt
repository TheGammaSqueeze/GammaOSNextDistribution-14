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
package com.android.customization.picker.clock.data.repository

import androidx.annotation.ColorInt
import androidx.annotation.IntRange
import com.android.customization.picker.clock.shared.ClockSize
import com.android.customization.picker.clock.shared.model.ClockMetadataModel
import kotlinx.coroutines.flow.Flow

/**
 * Repository for accessing application clock settings, as well as selecting and configuring custom
 * clocks.
 */
interface ClockPickerRepository {
    val allClocks: Flow<List<ClockMetadataModel>>

    val selectedClock: Flow<ClockMetadataModel>

    val selectedClockSize: Flow<ClockSize>

    suspend fun setSelectedClock(clockId: String)

    /**
     * Set clock color to the settings.
     *
     * @param selectedColor selected color in the color option list.
     * @param colorToneProgress color tone from 0 to 100 to apply to the selected color
     * @param seedColor the actual clock color after blending the selected color and color tone
     */
    suspend fun setClockColor(
        selectedColorId: String?,
        @IntRange(from = 0, to = 100) colorToneProgress: Int,
        @ColorInt seedColor: Int?,
    )

    suspend fun setClockSize(size: ClockSize)
}
