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

package com.android.customization.picker.clock.domain.interactor

import androidx.annotation.ColorInt
import androidx.annotation.IntRange
import com.android.customization.picker.clock.data.repository.ClockPickerRepository
import com.android.customization.picker.clock.shared.ClockSize
import com.android.customization.picker.clock.shared.model.ClockMetadataModel
import com.android.customization.picker.clock.shared.model.ClockSnapshotModel
import javax.inject.Provider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map

/**
 * Interactor for accessing application clock settings, as well as selecting and configuring custom
 * clocks.
 */
class ClockPickerInteractor(
    private val repository: ClockPickerRepository,
    private val snapshotRestorer: Provider<ClockPickerSnapshotRestorer>,
) {

    val allClocks: Flow<List<ClockMetadataModel>> = repository.allClocks

    val selectedClockId: Flow<String> =
        repository.selectedClock.map { clock -> clock.clockId }.distinctUntilChanged()

    val selectedColorId: Flow<String?> =
        repository.selectedClock.map { clock -> clock.selectedColorId }.distinctUntilChanged()

    val colorToneProgress: Flow<Int> =
        repository.selectedClock.map { clock -> clock.colorToneProgress }

    val seedColor: Flow<Int?> = repository.selectedClock.map { clock -> clock.seedColor }

    val selectedClockSize: Flow<ClockSize> = repository.selectedClockSize

    suspend fun setSelectedClock(clockId: String) {
        // Use the [clockId] to override saved clock id, since it might not be updated in time
        setClockOption(ClockSnapshotModel(clockId = clockId))
    }

    suspend fun setClockColor(
        selectedColorId: String?,
        @IntRange(from = 0, to = 100) colorToneProgress: Int,
        @ColorInt seedColor: Int?,
    ) {
        // Use the color to override saved color, since it might not be updated in time
        setClockOption(
            ClockSnapshotModel(
                selectedColorId = selectedColorId,
                colorToneProgress = colorToneProgress,
                seedColor = seedColor,
            )
        )
    }

    suspend fun setClockSize(size: ClockSize) {
        // Use the [ClockSize] to override saved clock size, since it might not be updated in time
        setClockOption(ClockSnapshotModel(clockSize = size))
    }

    suspend fun setClockOption(clockSnapshotModel: ClockSnapshotModel) {
        // [ClockCarouselViewModel] is monitoring the [ClockPickerInteractor.setSelectedClock] job,
        // so it needs to finish last.
        storeCurrentClockOption(clockSnapshotModel)

        clockSnapshotModel.clockSize?.let { repository.setClockSize(it) }
        clockSnapshotModel.colorToneProgress?.let {
            repository.setClockColor(
                selectedColorId = clockSnapshotModel.selectedColorId,
                colorToneProgress = clockSnapshotModel.colorToneProgress,
                seedColor = clockSnapshotModel.seedColor
            )
        }
        clockSnapshotModel.clockId?.let { repository.setSelectedClock(it) }
    }

    /**
     * Gets the [ClockSnapshotModel] from the storage and override with [latestOption].
     *
     * The storage might be in the middle of a write, and not reflecting the user's options, always
     * pass in a [ClockSnapshotModel] if we know it's the latest option from a user's point of view.
     *
     * [selectedColorId] and [seedColor] have null state collide with nullable type, but we know
     * they are presented whenever there's a [colorToneProgress].
     */
    suspend fun getCurrentClockToRestore(latestOption: ClockSnapshotModel? = null) =
        ClockSnapshotModel(
            clockId = latestOption?.clockId ?: selectedClockId.firstOrNull(),
            clockSize = latestOption?.clockSize ?: selectedClockSize.firstOrNull(),
            colorToneProgress = latestOption?.colorToneProgress ?: colorToneProgress.firstOrNull(),
            selectedColorId = latestOption?.colorToneProgress?.let { latestOption.selectedColorId }
                    ?: selectedColorId.firstOrNull(),
            seedColor = latestOption?.colorToneProgress?.let { latestOption.seedColor }
                    ?: seedColor.firstOrNull(),
        )

    private suspend fun storeCurrentClockOption(clockSnapshotModel: ClockSnapshotModel) {
        val option = getCurrentClockToRestore(clockSnapshotModel)
        snapshotRestorer.get().storeSnapshot(option)
    }
}
