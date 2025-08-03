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

package com.android.customization.model.picker.color.domain.interactor

import android.content.Context
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.android.customization.picker.color.data.repository.FakeColorPickerRepository
import com.android.customization.picker.color.domain.interactor.ColorPickerInteractor
import com.android.customization.picker.color.domain.interactor.ColorPickerSnapshotRestorer
import com.android.customization.picker.color.shared.model.ColorOptionModel
import com.android.customization.picker.color.shared.model.ColorType
import com.android.wallpaper.testing.FakeSnapshotStore
import com.android.wallpaper.testing.collectLastValue
import com.google.common.truth.Truth
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@SmallTest
@RunWith(RobolectricTestRunner::class)
class ColorPickerSnapshotRestorerTest {

    private lateinit var underTest: ColorPickerSnapshotRestorer
    private lateinit var repository: FakeColorPickerRepository
    private lateinit var store: FakeSnapshotStore

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        repository = FakeColorPickerRepository(context = context)
        underTest =
            ColorPickerSnapshotRestorer(
                interactor =
                    ColorPickerInteractor(
                        repository = repository,
                        snapshotRestorer = { underTest },
                    )
            )
        store = FakeSnapshotStore()
    }

    @Test
    fun restoreToSnapshot_noCallsToStore_restoresToInitialSnapshot() = runTest {
        val colorOptions = collectLastValue(repository.colorOptions)

        repository.setOptions(4, 4, ColorType.WALLPAPER_COLOR, 2)
        val initialSnapshot = underTest.setUpSnapshotRestorer(store = store)
        assertThat(initialSnapshot.args).isNotEmpty()

        val colorOptionToSelect = colorOptions()?.get(ColorType.PRESET_COLOR)?.get(3)
        colorOptionToSelect?.let { repository.select(it) }
        assertState(colorOptions(), ColorType.PRESET_COLOR, 3)

        underTest.restoreToSnapshot(initialSnapshot)
        assertState(colorOptions(), ColorType.WALLPAPER_COLOR, 2)
    }

    @Test
    fun restoreToSnapshot_withCallToStore_restoresToInitialSnapshot() = runTest {
        val colorOptions = collectLastValue(repository.colorOptions)

        repository.setOptions(4, 4, ColorType.WALLPAPER_COLOR, 2)
        val initialSnapshot = underTest.setUpSnapshotRestorer(store = store)
        assertThat(initialSnapshot.args).isNotEmpty()

        val colorOptionToSelect = colorOptions()?.get(ColorType.PRESET_COLOR)?.get(3)
        colorOptionToSelect?.let { repository.select(it) }
        assertState(colorOptions(), ColorType.PRESET_COLOR, 3)

        val colorOptionToStore = colorOptions()?.get(ColorType.PRESET_COLOR)?.get(1)
        colorOptionToStore?.let { underTest.storeSnapshot(colorOptionToStore) }

        underTest.restoreToSnapshot(initialSnapshot)
        assertState(colorOptions(), ColorType.WALLPAPER_COLOR, 2)
    }

    private fun assertState(
        colorOptions: Map<ColorType, List<ColorOptionModel>>?,
        selectedColorType: ColorType,
        selectedColorIndex: Int
    ) {
        var foundSelectedColorOption = false
        assertThat(colorOptions).isNotNull()
        val optionsOfSelectedColorType = colorOptions?.get(selectedColorType)
        assertThat(optionsOfSelectedColorType).isNotNull()
        if (optionsOfSelectedColorType != null) {
            for (i in optionsOfSelectedColorType.indices) {
                val colorOptionHasSelectedIndex = i == selectedColorIndex
                Truth.assertWithMessage(
                        "Expected color option with index \"${i}\" to have" +
                            " isSelected=$colorOptionHasSelectedIndex but it was" +
                            " ${optionsOfSelectedColorType[i].isSelected}, num options: ${colorOptions.size}"
                    )
                    .that(optionsOfSelectedColorType[i].isSelected)
                    .isEqualTo(colorOptionHasSelectedIndex)
                foundSelectedColorOption = foundSelectedColorOption || colorOptionHasSelectedIndex
            }
            if (selectedColorIndex == -1) {
                Truth.assertWithMessage(
                        "Expected no color options to be selected, but a color option is" +
                            " selected"
                    )
                    .that(foundSelectedColorOption)
                    .isFalse()
            } else {
                Truth.assertWithMessage(
                        "Expected a color option to be selected, but no color option is" +
                            " selected"
                    )
                    .that(foundSelectedColorOption)
                    .isTrue()
            }
        }
    }
}
