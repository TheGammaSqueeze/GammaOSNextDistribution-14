/*
 * Copyright (C) 2022 The Android Open Source Project
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

import androidx.lifecycle.SavedStateHandle
import androidx.test.filters.SmallTest
import com.android.wallpaper.module.logging.TestUserEventLogger
import com.android.wallpaper.picker.customization.data.repository.WallpaperRepository
import com.android.wallpaper.picker.customization.domain.interactor.WallpaperInteractor
import com.android.wallpaper.picker.undo.data.repository.UndoRepository
import com.android.wallpaper.picker.undo.domain.interactor.UndoInteractor
import com.android.wallpaper.testing.FAKE_RESTORERS
import com.android.wallpaper.testing.FakeWallpaperClient
import com.android.wallpaper.testing.TestWallpaperPreferences
import com.android.wallpaper.testing.collectLastValue
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@SmallTest
@RunWith(RobolectricTestRunner::class)
class CustomizationPickerViewModelTest {

    private lateinit var underTest: CustomizationPickerViewModel

    private lateinit var savedStateHandle: SavedStateHandle
    private lateinit var testScope: TestScope
    private lateinit var undoInteractor: UndoInteractor
    private lateinit var wallpaperInteractor: WallpaperInteractor
    private val logger = TestUserEventLogger()

    @Before
    fun setUp() {
        val testDispatcher = StandardTestDispatcher()
        testScope = TestScope(testDispatcher)
        savedStateHandle = SavedStateHandle()
        undoInteractor =
            UndoInteractor(
                scope = testScope.backgroundScope,
                repository = UndoRepository(),
                restorerByOwnerId = FAKE_RESTORERS,
            )
        wallpaperInteractor =
            WallpaperInteractor(
                repository =
                    WallpaperRepository(
                        scope = testScope.backgroundScope,
                        client = FakeWallpaperClient(),
                        wallpaperPreferences = TestWallpaperPreferences(),
                        backgroundDispatcher = testDispatcher,
                    ),
            )

        underTest =
            CustomizationPickerViewModel(
                undoInteractor = undoInteractor,
                wallpaperInteractor = wallpaperInteractor,
                savedStateHandle = savedStateHandle,
                logger = logger,
            )
    }

    @Test
    fun `initial tab is lock screen`() =
        testScope.runTest {
            val homeScreenTab = collectLastValue(underTest.homeScreenTab)
            val lockScreenTab = collectLastValue(underTest.lockScreenTab)
            val isOnLockScreen = collectLastValue(underTest.isOnLockScreen)

            assertThat(homeScreenTab()?.isSelected).isFalse()
            assertThat(lockScreenTab()?.isSelected).isTrue()
            assertThat(isOnLockScreen()).isTrue()
        }

    @Test
    fun `setInitialScreen - home screen`() =
        testScope.runTest {
            underTest.setInitialScreen(onLockScreen = false)

            val homeScreenTab = collectLastValue(underTest.homeScreenTab)
            val lockScreenTab = collectLastValue(underTest.lockScreenTab)
            val isOnLockScreen = collectLastValue(underTest.isOnLockScreen)

            assertThat(homeScreenTab()?.isSelected).isTrue()
            assertThat(lockScreenTab()?.isSelected).isFalse()
            assertThat(isOnLockScreen()).isFalse()
        }

    @Test
    fun `setInitialScreen - home screen - but lock screen already selected from before`() =
        testScope.runTest {
            // First, we start on the Home screen.
            underTest.setInitialScreen(onLockScreen = false)
            // Then, we switch to the lock screen.
            collectLastValue(underTest.lockScreenTab)()?.onClicked?.invoke()
            // Instantiate a new view-model with the same saved state.
            val newUnderTest =
                CustomizationPickerViewModel(
                    undoInteractor = undoInteractor,
                    wallpaperInteractor = wallpaperInteractor,
                    savedStateHandle = savedStateHandle,
                    logger = logger,
                )
            val newIsOnLockScreen = collectLastValue(newUnderTest.isOnLockScreen)

            newUnderTest.setInitialScreen(onLockScreen = false)

            assertThat(newIsOnLockScreen()).isTrue()
        }

    @Test
    fun `switching to the home screen`() =
        testScope.runTest {
            val homeScreenTab = collectLastValue(underTest.homeScreenTab)
            val lockScreenTab = collectLastValue(underTest.lockScreenTab)
            val isOnLockScreen = collectLastValue(underTest.isOnLockScreen)

            homeScreenTab()?.onClicked?.invoke()

            assertThat(homeScreenTab()?.isSelected).isTrue()
            assertThat(lockScreenTab()?.isSelected).isFalse()
            assertThat(isOnLockScreen()).isFalse()
        }

    @Test
    fun `switching to the home screen and back to the lock screen`() =
        testScope.runTest {
            val homeScreenTab = collectLastValue(underTest.homeScreenTab)
            val lockScreenTab = collectLastValue(underTest.lockScreenTab)
            val isOnLockScreen = collectLastValue(underTest.isOnLockScreen)

            homeScreenTab()?.onClicked?.invoke()
            lockScreenTab()?.onClicked?.invoke()

            assertThat(homeScreenTab()?.isSelected).isFalse()
            assertThat(lockScreenTab()?.isSelected).isTrue()
            assertThat(isOnLockScreen()).isTrue()
        }

    @Test
    fun `restores saved state`() =
        testScope.runTest {
            val oldHomeScreenTab = collectLastValue(underTest.homeScreenTab)

            // Switch to the home screen, which is **not** the default.
            oldHomeScreenTab()?.onClicked?.invoke()

            // Instantiate a new view-model with the same saved state
            val newUnderTest =
                CustomizationPickerViewModel(
                    undoInteractor = undoInteractor,
                    wallpaperInteractor = wallpaperInteractor,
                    savedStateHandle = savedStateHandle,
                    logger = logger,
                )
            val newHomeScreenTab = collectLastValue(newUnderTest.homeScreenTab)

            assertThat(newHomeScreenTab()?.isSelected).isTrue()
        }
}
