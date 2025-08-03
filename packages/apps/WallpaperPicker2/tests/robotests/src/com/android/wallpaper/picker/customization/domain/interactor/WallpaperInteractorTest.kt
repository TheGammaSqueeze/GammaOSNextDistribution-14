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

package com.android.wallpaper.picker.customization.domain.interactor

import android.stats.style.StyleEnums.SET_WALLPAPER_ENTRY_POINT_WALLPAPER_PREVIEW
import androidx.test.filters.SmallTest
import com.android.wallpaper.module.CustomizationSections
import com.android.wallpaper.picker.customization.data.repository.WallpaperRepository
import com.android.wallpaper.picker.customization.shared.model.WallpaperDestination
import com.android.wallpaper.picker.customization.shared.model.WallpaperModel
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
import org.junit.runners.JUnit4

@OptIn(ExperimentalCoroutinesApi::class)
@SmallTest
@RunWith(JUnit4::class)
class WallpaperInteractorTest {

    private lateinit var underTest: WallpaperInteractor

    private lateinit var client: FakeWallpaperClient
    private lateinit var testScope: TestScope

    @Before
    fun setUp() {
        client = FakeWallpaperClient()

        val testDispatcher = StandardTestDispatcher()
        testScope = TestScope(testDispatcher)
        underTest =
            WallpaperInteractor(
                repository =
                    WallpaperRepository(
                        scope = testScope.backgroundScope,
                        client = client,
                        wallpaperPreferences = TestWallpaperPreferences(),
                        backgroundDispatcher = testDispatcher,
                    ),
            )
    }

    @Test
    fun `previews - limits to maximum results`() =
        testScope.runTest {
            val limited =
                collectLastValue(
                    underTest.previews(
                        destination = WallpaperDestination.HOME,
                        maxResults = FakeWallpaperClient.INITIAL_RECENT_WALLPAPERS.size - 1
                    )
                )

            assertThat(limited())
                .isEqualTo(
                    FakeWallpaperClient.INITIAL_RECENT_WALLPAPERS.subList(
                        0,
                        FakeWallpaperClient.INITIAL_RECENT_WALLPAPERS.size - 1,
                    )
                )
        }

    @Test
    fun `previews - handles empty recents list`() =
        testScope.runTest {
            client.setRecentWallpapers(
                buildMap { put(WallpaperDestination.HOME, listOf<WallpaperModel>()) }
            )
            val homeWallpaperUpdateEvents =
                collectLastValue(
                    underTest.wallpaperUpdateEvents(CustomizationSections.Screen.HOME_SCREEN)
                )

            assertThat(homeWallpaperUpdateEvents()).isNull()
        }

    @Test
    fun wallpaperUpdateEvents() =
        testScope.runTest {
            val homeWallpaperUpdateEvents =
                collectLastValue(
                    underTest.wallpaperUpdateEvents(CustomizationSections.Screen.HOME_SCREEN)
                )
            val lockWallpaperUpdateEvents =
                collectLastValue(
                    underTest.wallpaperUpdateEvents(CustomizationSections.Screen.LOCK_SCREEN)
                )
            val homeWallpaperUpdateOutput1 = homeWallpaperUpdateEvents()
            val lockWallpaperUpdateOutput1 = lockWallpaperUpdateEvents()

            val homeWallpaperId1 = FakeWallpaperClient.INITIAL_RECENT_WALLPAPERS[1].wallpaperId
            val lockWallpaperId1 = FakeWallpaperClient.INITIAL_RECENT_WALLPAPERS[2].wallpaperId
            underTest.setRecentWallpaper(
                SET_WALLPAPER_ENTRY_POINT_WALLPAPER_PREVIEW,
                WallpaperDestination.HOME,
                homeWallpaperId1
            )
            underTest.setRecentWallpaper(
                SET_WALLPAPER_ENTRY_POINT_WALLPAPER_PREVIEW,
                WallpaperDestination.LOCK,
                lockWallpaperId1
            )
            assertThat(homeWallpaperUpdateEvents()).isNotEqualTo(homeWallpaperUpdateOutput1)
            assertThat(lockWallpaperUpdateEvents()).isNotEqualTo(lockWallpaperUpdateOutput1)
            val homeWallpaperUpdateOutput2 = homeWallpaperUpdateEvents()
            val lockWallpaperUpdateOutput2 = lockWallpaperUpdateEvents()

            val homeWallpaperId2 = FakeWallpaperClient.INITIAL_RECENT_WALLPAPERS[2].wallpaperId
            val lockWallpaperId2 = FakeWallpaperClient.INITIAL_RECENT_WALLPAPERS[2].wallpaperId
            underTest.setRecentWallpaper(
                SET_WALLPAPER_ENTRY_POINT_WALLPAPER_PREVIEW,
                WallpaperDestination.HOME,
                homeWallpaperId2
            )
            underTest.setRecentWallpaper(
                SET_WALLPAPER_ENTRY_POINT_WALLPAPER_PREVIEW,
                WallpaperDestination.LOCK,
                lockWallpaperId2
            )
            assertThat(homeWallpaperUpdateEvents()).isNotEqualTo(homeWallpaperUpdateOutput2)
            assertThat(lockWallpaperUpdateEvents()).isEqualTo(lockWallpaperUpdateOutput2)
        }

    @Test
    fun setWallpaper() =
        testScope.runTest {
            val homePreviews =
                collectLastValue(
                    underTest.previews(
                        destination = WallpaperDestination.HOME,
                        maxResults = FakeWallpaperClient.INITIAL_RECENT_WALLPAPERS.size
                    )
                )
            val lockPreviews =
                collectLastValue(
                    underTest.previews(
                        destination = WallpaperDestination.LOCK,
                        maxResults = FakeWallpaperClient.INITIAL_RECENT_WALLPAPERS.size
                    )
                )
            val selectedHomeWallpaperId =
                collectLastValue(underTest.selectedWallpaperId(WallpaperDestination.HOME))
            val selectedLockWallpaperId =
                collectLastValue(underTest.selectedWallpaperId(WallpaperDestination.LOCK))
            val selectingHomeWallpaperId =
                collectLastValue(underTest.selectingWallpaperId(WallpaperDestination.HOME))
            val selectingLockWallpaperId =
                collectLastValue(underTest.selectingWallpaperId(WallpaperDestination.LOCK))
            assertThat(homePreviews()).isEqualTo(FakeWallpaperClient.INITIAL_RECENT_WALLPAPERS)
            assertThat(lockPreviews()).isEqualTo(FakeWallpaperClient.INITIAL_RECENT_WALLPAPERS)
            assertThat(selectedLockWallpaperId())
                .isEqualTo(FakeWallpaperClient.INITIAL_RECENT_WALLPAPERS[0].wallpaperId)
            assertThat(selectedLockWallpaperId())
                .isEqualTo(FakeWallpaperClient.INITIAL_RECENT_WALLPAPERS[0].wallpaperId)
            assertThat(selectingHomeWallpaperId()).isNull()
            assertThat(selectingLockWallpaperId()).isNull()
            val homeWallpaperId = FakeWallpaperClient.INITIAL_RECENT_WALLPAPERS[1].wallpaperId
            val lockWallpaperId = FakeWallpaperClient.INITIAL_RECENT_WALLPAPERS[2].wallpaperId

            // Pause the client so we can examine the interim state.
            client.pause()
            underTest.setRecentWallpaper(
                SET_WALLPAPER_ENTRY_POINT_WALLPAPER_PREVIEW,
                WallpaperDestination.HOME,
                homeWallpaperId
            )
            underTest.setRecentWallpaper(
                SET_WALLPAPER_ENTRY_POINT_WALLPAPER_PREVIEW,
                WallpaperDestination.LOCK,
                lockWallpaperId
            )
            assertThat(homePreviews()).isEqualTo(FakeWallpaperClient.INITIAL_RECENT_WALLPAPERS)
            assertThat(lockPreviews()).isEqualTo(FakeWallpaperClient.INITIAL_RECENT_WALLPAPERS)
            assertThat(selectedHomeWallpaperId())
                .isEqualTo(FakeWallpaperClient.INITIAL_RECENT_WALLPAPERS[0].wallpaperId)
            assertThat(selectedLockWallpaperId())
                .isEqualTo(FakeWallpaperClient.INITIAL_RECENT_WALLPAPERS[0].wallpaperId)
            assertThat(selectingHomeWallpaperId())
                .isEqualTo(FakeWallpaperClient.INITIAL_RECENT_WALLPAPERS[1].wallpaperId)
            assertThat(selectingLockWallpaperId())
                .isEqualTo(FakeWallpaperClient.INITIAL_RECENT_WALLPAPERS[2].wallpaperId)

            // Unpause the client so we can examine the final state.
            client.unpause()
            assertThat(homePreviews())
                .isEqualTo(
                    listOf(
                        FakeWallpaperClient.INITIAL_RECENT_WALLPAPERS[1],
                        FakeWallpaperClient.INITIAL_RECENT_WALLPAPERS[0],
                        FakeWallpaperClient.INITIAL_RECENT_WALLPAPERS[2],
                    )
                )
            assertThat(lockPreviews())
                .isEqualTo(
                    listOf(
                        FakeWallpaperClient.INITIAL_RECENT_WALLPAPERS[2],
                        FakeWallpaperClient.INITIAL_RECENT_WALLPAPERS[0],
                        FakeWallpaperClient.INITIAL_RECENT_WALLPAPERS[1],
                    )
                )
            assertThat(selectedHomeWallpaperId()).isEqualTo(homeWallpaperId)
            assertThat(selectedLockWallpaperId()).isEqualTo(lockWallpaperId)
            assertThat(selectingHomeWallpaperId()).isNull()
            assertThat(selectingLockWallpaperId()).isNull()
        }
}
