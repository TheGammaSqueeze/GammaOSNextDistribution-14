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
package com.android.customization.model.picker.color.ui.viewmodel

import android.content.Context
import android.graphics.Color
import android.stats.style.StyleEnums
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.android.customization.model.color.ColorOptionsProvider
import com.android.customization.module.logging.TestThemesUserEventLogger
import com.android.customization.picker.color.data.repository.FakeColorPickerRepository
import com.android.customization.picker.color.domain.interactor.ColorPickerInteractor
import com.android.customization.picker.color.domain.interactor.ColorPickerSnapshotRestorer
import com.android.customization.picker.color.shared.model.ColorType
import com.android.customization.picker.color.ui.viewmodel.ColorOptionIconViewModel
import com.android.customization.picker.color.ui.viewmodel.ColorPickerViewModel
import com.android.customization.picker.color.ui.viewmodel.ColorTypeTabViewModel
import com.android.systemui.monet.Style
import com.android.wallpaper.picker.option.ui.viewmodel.OptionItemViewModel
import com.android.wallpaper.testing.FakeSnapshotStore
import com.android.wallpaper.testing.collectLastValue
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@SmallTest
@RunWith(RobolectricTestRunner::class)
class ColorPickerViewModelTest {
    private val logger = TestThemesUserEventLogger()
    private lateinit var underTest: ColorPickerViewModel
    private lateinit var repository: FakeColorPickerRepository
    private lateinit var interactor: ColorPickerInteractor
    private lateinit var store: FakeSnapshotStore

    private lateinit var context: Context
    private lateinit var testScope: TestScope

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        val testDispatcher = StandardTestDispatcher()
        Dispatchers.setMain(testDispatcher)
        testScope = TestScope(testDispatcher)
        repository = FakeColorPickerRepository(context = context)
        store = FakeSnapshotStore()

        interactor =
            ColorPickerInteractor(
                repository = repository,
                snapshotRestorer = {
                    ColorPickerSnapshotRestorer(interactor = interactor).apply {
                        runBlocking { setUpSnapshotRestorer(store = store) }
                    }
                },
            )

        underTest =
            ColorPickerViewModel.Factory(
                    context = context,
                    interactor = interactor,
                    logger = logger
                )
                .create(ColorPickerViewModel::class.java)

        repository.setOptions(4, 4, ColorType.WALLPAPER_COLOR, 0)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `Select a color section color`() =
        testScope.runTest {
            val colorSectionOptions = collectLastValue(underTest.colorSectionOptions)

            assertColorOptionUiState(
                colorOptions = colorSectionOptions(),
                selectedColorOptionIndex = 0
            )

            selectColorOption(colorSectionOptions, 2)
            assertColorOptionUiState(
                colorOptions = colorSectionOptions(),
                selectedColorOptionIndex = 2
            )

            selectColorOption(colorSectionOptions, 4)
            assertColorOptionUiState(
                colorOptions = colorSectionOptions(),
                selectedColorOptionIndex = 4
            )
        }

    @Test
    fun `Log selected wallpaper color`() =
        testScope.runTest {
            repository.setOptions(
                listOf(
                    repository.buildWallpaperOption(
                        ColorOptionsProvider.COLOR_SOURCE_LOCK,
                        Style.EXPRESSIVE,
                        "121212"
                    )
                ),
                listOf(repository.buildPresetOption(Style.FRUIT_SALAD, "#ABCDEF")),
                ColorType.PRESET_COLOR,
                0
            )

            val colorTypes = collectLastValue(underTest.colorTypeTabs)
            val colorOptions = collectLastValue(underTest.colorOptions)

            // Select "Wallpaper colors" tab
            colorTypes()?.get(ColorType.WALLPAPER_COLOR)?.onClick?.invoke()
            // Select a color option
            selectColorOption(colorOptions, 0)
            advanceUntilIdle()

            assertThat(logger.themeColorSource)
                .isEqualTo(StyleEnums.COLOR_SOURCE_LOCK_SCREEN_WALLPAPER)
            assertThat(logger.themeColorStyle).isEqualTo(Style.EXPRESSIVE.toString().hashCode())
            assertThat(logger.themeSeedColor).isEqualTo(Color.parseColor("#121212"))
        }

    @Test
    fun `Log selected preset color`() =
        testScope.runTest {
            repository.setOptions(
                listOf(
                    repository.buildWallpaperOption(
                        ColorOptionsProvider.COLOR_SOURCE_LOCK,
                        Style.EXPRESSIVE,
                        "121212"
                    )
                ),
                listOf(repository.buildPresetOption(Style.FRUIT_SALAD, "#ABCDEF")),
                ColorType.WALLPAPER_COLOR,
                0
            )

            val colorTypes = collectLastValue(underTest.colorTypeTabs)
            val colorOptions = collectLastValue(underTest.colorOptions)

            // Select "Wallpaper colors" tab
            colorTypes()?.get(ColorType.PRESET_COLOR)?.onClick?.invoke()
            // Select a color option
            selectColorOption(colorOptions, 0)
            advanceUntilIdle()

            assertThat(logger.themeColorSource).isEqualTo(StyleEnums.COLOR_SOURCE_PRESET_COLOR)
            assertThat(logger.themeColorStyle).isEqualTo(Style.FRUIT_SALAD.toString().hashCode())
            assertThat(logger.themeSeedColor).isEqualTo(Color.parseColor("#ABCDEF"))
        }

    @Test
    fun `Select a preset color`() =
        testScope.runTest {
            val colorTypes = collectLastValue(underTest.colorTypeTabs)
            val colorOptions = collectLastValue(underTest.colorOptions)

            // Initially, the wallpaper color tab should be selected
            assertPickerUiState(
                colorTypes = colorTypes(),
                colorOptions = colorOptions(),
                selectedColorTypeText = "Wallpaper colors",
                selectedColorOptionIndex = 0
            )

            // Select "Basic colors" tab
            colorTypes()?.get(ColorType.PRESET_COLOR)?.onClick?.invoke()
            assertPickerUiState(
                colorTypes = colorTypes(),
                colorOptions = colorOptions(),
                selectedColorTypeText = "Basic colors",
                selectedColorOptionIndex = -1
            )

            // Select a color option
            selectColorOption(colorOptions, 2)

            // Check original option is no longer selected
            colorTypes()?.get(ColorType.WALLPAPER_COLOR)?.onClick?.invoke()
            assertPickerUiState(
                colorTypes = colorTypes(),
                colorOptions = colorOptions(),
                selectedColorTypeText = "Wallpaper colors",
                selectedColorOptionIndex = -1
            )

            // Check new option is selected
            colorTypes()?.get(ColorType.PRESET_COLOR)?.onClick?.invoke()
            assertPickerUiState(
                colorTypes = colorTypes(),
                colorOptions = colorOptions(),
                selectedColorTypeText = "Basic colors",
                selectedColorOptionIndex = 2
            )
        }

    /** Simulates a user selecting the affordance at the given index, if that is clickable. */
    private fun TestScope.selectColorOption(
        colorOptions: () -> List<OptionItemViewModel<ColorOptionIconViewModel>>?,
        index: Int,
    ) {
        val onClickedFlow = colorOptions()?.get(index)?.onClicked
        val onClickedLastValueOrNull: (() -> (() -> Unit)?)? =
            onClickedFlow?.let { collectLastValue(it) }
        onClickedLastValueOrNull?.let { onClickedLastValue ->
            val onClickedOrNull: (() -> Unit)? = onClickedLastValue()
            onClickedOrNull?.let { onClicked -> onClicked() }
        }
    }

    /**
     * Asserts the entire picker UI state is what is expected. This includes the color type tabs and
     * the color options list.
     *
     * @param colorTypes The observed color type view-models, keyed by ColorType
     * @param colorOptions The observed color options
     * @param selectedColorTypeText The text of the color type that's expected to be selected
     * @param selectedColorOptionIndex The index of the color option that's expected to be selected,
     *   -1 stands for no color option should be selected
     */
    private fun TestScope.assertPickerUiState(
        colorTypes: Map<ColorType, ColorTypeTabViewModel>?,
        colorOptions: List<OptionItemViewModel<ColorOptionIconViewModel>>?,
        selectedColorTypeText: String,
        selectedColorOptionIndex: Int,
    ) {
        assertColorTypeTabUiState(
            colorTypes = colorTypes,
            colorTypeId = ColorType.WALLPAPER_COLOR,
            isSelected = "Wallpaper colors" == selectedColorTypeText,
        )
        assertColorTypeTabUiState(
            colorTypes = colorTypes,
            colorTypeId = ColorType.PRESET_COLOR,
            isSelected = "Basic colors" == selectedColorTypeText,
        )
        assertColorOptionUiState(colorOptions, selectedColorOptionIndex)
    }

    /**
     * Asserts the picker section UI state is what is expected.
     *
     * @param colorOptions The observed color options
     * @param selectedColorOptionIndex The index of the color option that's expected to be selected,
     *   -1 stands for no color option should be selected
     */
    private fun TestScope.assertColorOptionUiState(
        colorOptions: List<OptionItemViewModel<ColorOptionIconViewModel>>?,
        selectedColorOptionIndex: Int,
    ) {
        var foundSelectedColorOption = false
        assertThat(colorOptions).isNotNull()
        if (colorOptions != null) {
            for (i in colorOptions.indices) {
                val colorOptionHasSelectedIndex = i == selectedColorOptionIndex
                val isSelected: Boolean? = collectLastValue(colorOptions[i].isSelected).invoke()
                assertWithMessage(
                        "Expected color option with index \"${i}\" to have" +
                            " isSelected=$colorOptionHasSelectedIndex but it was" +
                            " ${isSelected}, num options: ${colorOptions.size}"
                    )
                    .that(isSelected)
                    .isEqualTo(colorOptionHasSelectedIndex)
                foundSelectedColorOption = foundSelectedColorOption || colorOptionHasSelectedIndex
            }
            if (selectedColorOptionIndex == -1) {
                assertWithMessage(
                        "Expected no color options to be selected, but a color option is" +
                            " selected"
                    )
                    .that(foundSelectedColorOption)
                    .isFalse()
            } else {
                assertWithMessage(
                        "Expected a color option to be selected, but no color option is" +
                            " selected"
                    )
                    .that(foundSelectedColorOption)
                    .isTrue()
            }
        }
    }

    /**
     * Asserts that a color type tab has the correct UI state.
     *
     * @param colorTypes The observed color type view-models, keyed by ColorType enum
     * @param colorTypeId the ID of the color type to assert
     * @param isSelected Whether that color type should be selected
     */
    private fun assertColorTypeTabUiState(
        colorTypes: Map<ColorType, ColorTypeTabViewModel>?,
        colorTypeId: ColorType,
        isSelected: Boolean,
    ) {
        val viewModel =
            colorTypes?.get(colorTypeId) ?: error("No color type with ID \"$colorTypeId\"!")
        assertThat(viewModel.isSelected).isEqualTo(isSelected)
    }
}
