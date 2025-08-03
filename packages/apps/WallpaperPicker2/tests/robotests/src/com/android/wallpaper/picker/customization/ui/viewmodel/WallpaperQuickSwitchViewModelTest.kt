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

package com.android.wallpaper.picker.customization.ui.viewmodel

import androidx.test.filters.SmallTest
import com.android.wallpaper.picker.customization.data.repository.WallpaperRepository
import com.android.wallpaper.picker.customization.domain.interactor.WallpaperInteractor
import com.android.wallpaper.picker.customization.shared.model.WallpaperDestination
import com.android.wallpaper.picker.customization.shared.model.WallpaperModel
import com.android.wallpaper.testing.FakeWallpaperClient
import com.android.wallpaper.testing.TestWallpaperPreferences
import com.android.wallpaper.testing.collectLastValue
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
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
class WallpaperQuickSwitchViewModelTest {

    private lateinit var underTest: WallpaperQuickSwitchViewModel

    private lateinit var client: FakeWallpaperClient
    private lateinit var testScope: TestScope

    @Before
    fun setUp() {
        client = FakeWallpaperClient()

        val testDispatcher = StandardTestDispatcher()
        Dispatchers.setMain(testDispatcher)
        testScope = TestScope(testDispatcher)
        val interactor =
            WallpaperInteractor(
                repository =
                    WallpaperRepository(
                        scope = testScope.backgroundScope,
                        client = client,
                        wallpaperPreferences = TestWallpaperPreferences(),
                        backgroundDispatcher = testDispatcher,
                    ),
            )
        underTest =
            WallpaperQuickSwitchViewModel(
                interactor = interactor,
                destination = WallpaperDestination.HOME,
                coroutineScope = testScope.backgroundScope,
                maxOptions = FakeWallpaperClient.INITIAL_RECENT_WALLPAPERS.size,
            )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial options`() =
        testScope.runTest {
            val options = collectLastValue(underTest.options)
            assertOptions(
                observed = options(),
                expected = expectations(),
            )
        }

    @Test
    fun `updates options`() =
        testScope.runTest {
            val options = collectLastValue(underTest.options)

            val models =
                listOf(
                    WallpaperModel(
                        wallpaperId = "aaa",
                        placeholderColor = 1200,
                        title = "title1",
                    ),
                    WallpaperModel(
                        wallpaperId = "bbb",
                        placeholderColor = 1300,
                        title = "title2",
                    ),
                    WallpaperModel(
                        wallpaperId = "ccc",
                        placeholderColor = 1400,
                        title = "title3",
                    ),
                )
            client.setRecentWallpapers(buildMap { put(WallpaperDestination.HOME, models) })

            assertOptions(
                observed = options(),
                expected =
                    expectations(
                        models = models,
                    ),
            )
        }

    @Test
    fun `recentOptions_lastUpdatedChange_updatesOptions`() =
        testScope.runTest {
            val options = collectLastValue(underTest.options)

            val models =
                FakeWallpaperClient.INITIAL_RECENT_WALLPAPERS.mapIndexed { idx, wp ->
                    WallpaperModel(
                        wallpaperId = wp.wallpaperId,
                        placeholderColor = wp.placeholderColor,
                        lastUpdated = if (idx == 0) 100 else wp.lastUpdated,
                        title = "title1"
                    )
                }
            client.setRecentWallpapers(buildMap { put(WallpaperDestination.HOME, models) })

            assertOptions(
                observed = options(),
                expected =
                    expectations(
                        models = models,
                    ),
            )
        }

    @Test
    fun `switches to third option`() =
        testScope.runTest {
            val options = collectLastValue(underTest.options)

            // Pause the client so we can examine the interim state.
            client.pause()
            val selectedIndex = 2
            val optionToSelect = checkNotNull(options()?.get(selectedIndex))
            val onSelected = collectLastValue(optionToSelect.onSelected)
            onSelected()?.invoke()

            assertOptions(
                observed = options(),
                expected =
                    expectations(
                        selectingIndex = selectedIndex,
                    ),
            )

            // Unpause the client so we can examine the final state.
            client.unpause()
            runCurrent()
            assertOptions(
                observed = options(),
                expected =
                    expectations(
                        selectedIndex = selectedIndex,
                    ),
            )
        }

    private fun expectations(
        models: List<WallpaperModel> = FakeWallpaperClient.INITIAL_RECENT_WALLPAPERS,
        selectedIndex: Int = 0,
        selectingIndex: Int? = null,
    ): List<ExpectedOption> {
        return models.mapIndexed { index, model ->
            val nothingBeingSelected = selectingIndex == null
            val isBeingSelected = selectingIndex == index
            val isSelected = selectedIndex == index
            ExpectedOption(
                wallpaperId = model.wallpaperId,
                placeholderColor = model.placeholderColor,
                isLarge = isBeingSelected || (nothingBeingSelected && isSelected),
                isSelectionIndicatorVisible =
                    isBeingSelected || (nothingBeingSelected && isSelected),
                isSelectable = nothingBeingSelected && !isSelected,
            )
        }
    }

    private fun TestScope.assertOptions(
        observed: List<WallpaperQuickSwitchOptionViewModel>?,
        expected: List<ExpectedOption>,
    ) {
        checkNotNull(observed)
        assertThat(observed).hasSize(expected.size)
        observed.forEachIndexed { index, option ->
            assertWithMessage("mismatching wallpaperId for index $index.")
                .that(option.wallpaperId)
                .isEqualTo(expected[index].wallpaperId)
            assertWithMessage("mismatching isLarge for index $index.")
                .that(collectLastValue(option.isLarge)())
                .isEqualTo(expected[index].isLarge)
            assertWithMessage("mismatching placeholderColor for index $index.")
                .that(option.placeholderColor)
                .isEqualTo(expected[index].placeholderColor)
            assertWithMessage("mismatching isSelectionBorderVisible for index $index.")
                .that(collectLastValue(option.isSelectionIndicatorVisible)())
                .isEqualTo(expected[index].isSelectionIndicatorVisible)
            assertWithMessage("mismatching isSelectable for index $index.")
                .that(collectLastValue(option.onSelected)() != null)
                .isEqualTo(expected[index].isSelectable)
        }
    }

    private data class ExpectedOption(
        val wallpaperId: String,
        val placeholderColor: Int,
        val isLarge: Boolean = false,
        val isSelectionIndicatorVisible: Boolean = false,
        val isSelectable: Boolean = true,
    )
}
