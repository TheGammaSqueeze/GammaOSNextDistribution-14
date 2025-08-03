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

package com.android.wallpaper.picker.customization.data.repository

import android.stats.style.StyleEnums.SET_WALLPAPER_ENTRY_POINT_WALLPAPER_PREVIEW
import androidx.test.filters.SmallTest
import com.android.wallpaper.picker.customization.shared.model.WallpaperDestination
import com.android.wallpaper.testing.FakeWallpaperClient
import com.android.wallpaper.testing.TestWallpaperPreferences
import com.android.wallpaper.testing.collectLastValue
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.map
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
class WallpaperRepositoryTest {

    private lateinit var underTest: WallpaperRepository

    private lateinit var client: FakeWallpaperClient
    private lateinit var testScope: TestScope
    private var prefs = TestWallpaperPreferences()

    @Before
    fun setUp() {
        client = FakeWallpaperClient()

        val testDispatcher = StandardTestDispatcher()
        testScope = TestScope(testDispatcher)
        underTest =
            WallpaperRepository(
                scope = testScope.backgroundScope,
                client = client,
                wallpaperPreferences = prefs,
                backgroundDispatcher = testDispatcher,
            )
    }

    @Test
    fun setWallpaper() =
        testScope.runTest {
            val recentHomeWallpapers =
                collectLastValue(
                    underTest.recentWallpapers(destination = WallpaperDestination.HOME, limit = 5)
                )
            val recentLockWallpapers =
                collectLastValue(
                    underTest.recentWallpapers(destination = WallpaperDestination.LOCK, limit = 5)
                )
            val selectedHomeWallpaperId =
                collectLastValue(underTest.selectedWallpaperId(WallpaperDestination.HOME))
            val selectedLockWallpaperId =
                collectLastValue(underTest.selectedWallpaperId(WallpaperDestination.LOCK))
            val selectingHomeWallpaperId =
                collectLastValue(
                    underTest.selectingWallpaperId.map { it[WallpaperDestination.HOME] }
                )
            val selectingLockWallpaperId =
                collectLastValue(
                    underTest.selectingWallpaperId.map { it[WallpaperDestination.LOCK] }
                )
            assertThat(recentHomeWallpapers())
                .isEqualTo(FakeWallpaperClient.INITIAL_RECENT_WALLPAPERS)
            assertThat(recentLockWallpapers())
                .isEqualTo(FakeWallpaperClient.INITIAL_RECENT_WALLPAPERS)
            assertThat(selectedHomeWallpaperId())
                .isEqualTo(FakeWallpaperClient.INITIAL_RECENT_WALLPAPERS.first().wallpaperId)
            assertThat(selectedLockWallpaperId())
                .isEqualTo(FakeWallpaperClient.INITIAL_RECENT_WALLPAPERS.first().wallpaperId)
            assertThat(selectingHomeWallpaperId()).isNull()
            assertThat(selectingLockWallpaperId()).isNull()

            // Pause the client so we can examine the interim state.
            client.pause()
            underTest.setRecentWallpaper(
                SET_WALLPAPER_ENTRY_POINT_WALLPAPER_PREVIEW,
                WallpaperDestination.HOME,
                FakeWallpaperClient.INITIAL_RECENT_WALLPAPERS[1].wallpaperId,
            )
            prefs.setHomeWallpaperRecentsKey(
                FakeWallpaperClient.INITIAL_RECENT_WALLPAPERS[1].wallpaperId
            )
            underTest.setRecentWallpaper(
                SET_WALLPAPER_ENTRY_POINT_WALLPAPER_PREVIEW,
                WallpaperDestination.LOCK,
                FakeWallpaperClient.INITIAL_RECENT_WALLPAPERS[2].wallpaperId,
            )
            prefs.setLockWallpaperRecentsKey(
                FakeWallpaperClient.INITIAL_RECENT_WALLPAPERS[2].wallpaperId
            )
            assertThat(recentHomeWallpapers())
                .isEqualTo(FakeWallpaperClient.INITIAL_RECENT_WALLPAPERS)
            assertThat(recentLockWallpapers())
                .isEqualTo(FakeWallpaperClient.INITIAL_RECENT_WALLPAPERS)
            assertThat(selectedHomeWallpaperId())
                .isEqualTo(FakeWallpaperClient.INITIAL_RECENT_WALLPAPERS.first().wallpaperId)
            assertThat(selectedLockWallpaperId())
                .isEqualTo(FakeWallpaperClient.INITIAL_RECENT_WALLPAPERS.first().wallpaperId)
            assertThat(selectingHomeWallpaperId())
                .isEqualTo(FakeWallpaperClient.INITIAL_RECENT_WALLPAPERS[1].wallpaperId)
            assertThat(selectingLockWallpaperId())
                .isEqualTo(FakeWallpaperClient.INITIAL_RECENT_WALLPAPERS[2].wallpaperId)

            // Unpause the client so we can examine the final state.
            client.unpause()
            assertThat(recentHomeWallpapers())
                .isEqualTo(
                    listOf(
                        FakeWallpaperClient.INITIAL_RECENT_WALLPAPERS[1],
                        FakeWallpaperClient.INITIAL_RECENT_WALLPAPERS[0],
                        FakeWallpaperClient.INITIAL_RECENT_WALLPAPERS[2],
                    )
                )
            assertThat(recentLockWallpapers())
                .isEqualTo(
                    listOf(
                        FakeWallpaperClient.INITIAL_RECENT_WALLPAPERS[2],
                        FakeWallpaperClient.INITIAL_RECENT_WALLPAPERS[0],
                        FakeWallpaperClient.INITIAL_RECENT_WALLPAPERS[1],
                    )
                )
            assertThat(selectedHomeWallpaperId())
                .isEqualTo(FakeWallpaperClient.INITIAL_RECENT_WALLPAPERS[1].wallpaperId)
            assertThat(selectedLockWallpaperId())
                .isEqualTo(FakeWallpaperClient.INITIAL_RECENT_WALLPAPERS[2].wallpaperId)
            assertThat(selectingHomeWallpaperId()).isNull()
            assertThat(selectingLockWallpaperId()).isNull()
        }

    /**
     * Tests the fallback behavior of WallpaperRepository#selectedWallpaperId when then recents key
     * is not set in metadata.
     */
    @Test
    fun setWallpaper_recentsKeyFallback() =
        testScope.runTest {
            val recentHomeWallpapers =
                collectLastValue(
                    underTest.recentWallpapers(destination = WallpaperDestination.HOME, limit = 5)
                )
            val recentLockWallpapers =
                collectLastValue(
                    underTest.recentWallpapers(destination = WallpaperDestination.LOCK, limit = 5)
                )
            val selectedHomeWallpaperId =
                collectLastValue(underTest.selectedWallpaperId(WallpaperDestination.HOME))
            val selectedLockWallpaperId =
                collectLastValue(underTest.selectedWallpaperId(WallpaperDestination.LOCK))
            assertThat(recentHomeWallpapers())
                .isEqualTo(FakeWallpaperClient.INITIAL_RECENT_WALLPAPERS)
            assertThat(recentLockWallpapers())
                .isEqualTo(FakeWallpaperClient.INITIAL_RECENT_WALLPAPERS)
            assertThat(selectedHomeWallpaperId())
                .isEqualTo(FakeWallpaperClient.INITIAL_RECENT_WALLPAPERS.first().wallpaperId)
            assertThat(selectedLockWallpaperId())
                .isEqualTo(FakeWallpaperClient.INITIAL_RECENT_WALLPAPERS.first().wallpaperId)

            underTest.setRecentWallpaper(
                SET_WALLPAPER_ENTRY_POINT_WALLPAPER_PREVIEW,
                WallpaperDestination.HOME,
                FakeWallpaperClient.INITIAL_RECENT_WALLPAPERS[1].wallpaperId,
            )
            underTest.setRecentWallpaper(
                SET_WALLPAPER_ENTRY_POINT_WALLPAPER_PREVIEW,
                WallpaperDestination.LOCK,
                FakeWallpaperClient.INITIAL_RECENT_WALLPAPERS[2].wallpaperId,
            )

            assertThat(recentHomeWallpapers())
                .isEqualTo(
                    listOf(
                        FakeWallpaperClient.INITIAL_RECENT_WALLPAPERS[1],
                        FakeWallpaperClient.INITIAL_RECENT_WALLPAPERS[0],
                        FakeWallpaperClient.INITIAL_RECENT_WALLPAPERS[2],
                    )
                )
            assertThat(recentLockWallpapers())
                .isEqualTo(
                    listOf(
                        FakeWallpaperClient.INITIAL_RECENT_WALLPAPERS[2],
                        FakeWallpaperClient.INITIAL_RECENT_WALLPAPERS[0],
                        FakeWallpaperClient.INITIAL_RECENT_WALLPAPERS[1],
                    )
                )
            assertThat(selectedHomeWallpaperId())
                .isEqualTo(FakeWallpaperClient.INITIAL_RECENT_WALLPAPERS[1].wallpaperId)
            assertThat(selectedLockWallpaperId())
                .isEqualTo(FakeWallpaperClient.INITIAL_RECENT_WALLPAPERS[2].wallpaperId)
        }
}
