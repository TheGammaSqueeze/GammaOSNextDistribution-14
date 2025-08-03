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
 */
package com.android.customization.picker.clock.data.repository

import android.graphics.Color
import androidx.annotation.ColorInt
import androidx.annotation.IntRange
import com.android.customization.picker.clock.data.repository.FakeClockPickerRepository.Companion.fakeClocks
import com.android.customization.picker.clock.shared.ClockSize
import com.android.customization.picker.clock.shared.model.ClockMetadataModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine

/** By default [FakeClockPickerRepository] uses [fakeClocks]. */
open class FakeClockPickerRepository(clocks: List<ClockMetadataModel> = fakeClocks) :
    ClockPickerRepository {
    override val allClocks: Flow<List<ClockMetadataModel>> = MutableStateFlow(clocks).asStateFlow()

    private val selectedClockId = MutableStateFlow(fakeClocks[0].clockId)
    @ColorInt private val selectedColorId = MutableStateFlow<String?>(null)
    private val colorTone = MutableStateFlow(ClockMetadataModel.DEFAULT_COLOR_TONE_PROGRESS)
    @ColorInt private val seedColor = MutableStateFlow<Int?>(null)
    override val selectedClock: Flow<ClockMetadataModel> =
        combine(
            selectedClockId,
            selectedColorId,
            colorTone,
            seedColor,
        ) { selectedClockId, selectedColor, colorTone, seedColor ->
            val selectedClock = fakeClocks.find { clock -> clock.clockId == selectedClockId }
            checkNotNull(selectedClock)
            ClockMetadataModel(
                clockId = selectedClock.clockId,
                isSelected = true,
                selectedColorId = selectedColor,
                colorToneProgress = colorTone,
                seedColor = seedColor,
            )
        }

    private val _selectedClockSize = MutableStateFlow(ClockSize.SMALL)
    override val selectedClockSize: Flow<ClockSize> = _selectedClockSize.asStateFlow()

    override suspend fun setSelectedClock(clockId: String) {
        selectedClockId.value = clockId
    }

    override suspend fun setClockColor(
        selectedColorId: String?,
        @IntRange(from = 0, to = 100) colorToneProgress: Int,
        @ColorInt seedColor: Int?,
    ) {
        this.selectedColorId.value = selectedColorId
        this.colorTone.value = colorToneProgress
        this.seedColor.value = seedColor
    }

    override suspend fun setClockSize(size: ClockSize) {
        _selectedClockSize.value = size
    }

    companion object {
        const val CLOCK_ID_0 = "clock0"
        const val CLOCK_ID_1 = "clock1"
        const val CLOCK_ID_2 = "clock2"
        const val CLOCK_ID_3 = "clock3"
        val fakeClocks =
            listOf(
                ClockMetadataModel(CLOCK_ID_0, true, null, 50, null),
                ClockMetadataModel(CLOCK_ID_1, false, null, 50, null),
                ClockMetadataModel(CLOCK_ID_2, false, null, 50, null),
                ClockMetadataModel(CLOCK_ID_3, false, null, 50, null),
            )
        const val CLOCK_COLOR_ID = "RED"
        const val CLOCK_COLOR_TONE_PROGRESS = 87
        const val SEED_COLOR = Color.RED
    }
}
