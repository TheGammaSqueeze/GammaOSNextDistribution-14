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
package com.android.wallpaper.picker.preview.ui

import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import androidx.navigation.fragment.NavHostFragment
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import com.android.wallpaper.Flags.FLAG_MULTI_CROP_PREVIEW_UI_FLAG
import com.android.wallpaper.model.WallpaperInfo
import com.android.wallpaper.module.InjectorProvider
import com.android.wallpaper.testing.TestInjector
import com.android.wallpaper.testing.TestStaticWallpaperInfo
import com.android.window.flags.Flags.FLAG_MULTI_CROP
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@MediumTest
@RunWith(AndroidJUnit4::class)
class WallpaperPreviewActivityTest {
    @get:Rule var hiltRule = HiltAndroidRule(this)

    @get:Rule val setFlagsRule = SetFlagsRule()

    @Inject lateinit var testInjector: TestInjector

    private val testStaticWallpaper =
        TestStaticWallpaperInfo(TestStaticWallpaperInfo.COLOR_DEFAULT).setWallpaperAttributions()
    private val activityStartIntent =
        WallpaperPreviewActivity.newIntent(
            context = ApplicationProvider.getApplicationContext(),
            wallpaperInfo = testStaticWallpaper,
            isAssetIdPresent = false,
            isViewAsHome = false,
            isNewTask = false,
        )

    @Before
    fun setUp() {
        hiltRule.inject()
        InjectorProvider.setInjector(testInjector)
    }

    @Test
    @Ignore("b/327241549")
    @EnableFlags(FLAG_MULTI_CROP_PREVIEW_UI_FLAG, FLAG_MULTI_CROP)
    fun showsNavHostFragment() {
        val scenario: ActivityScenario<WallpaperPreviewActivity> =
            ActivityScenario.launch(activityStartIntent)

        scenario.onActivity { activity ->
            val previews =
                activity.supportFragmentManager.fragments.filterIsInstance<NavHostFragment>()
            assertThat(previews).hasSize(1)
        }
    }

    private fun TestStaticWallpaperInfo.setWallpaperAttributions(): WallpaperInfo {
        setAttributions(listOf("Title", "Subtitle 1", "Subtitle 2"))
        setCollectionId("collectionStatic")
        setWallpaperId("wallpaperStatic")
        setActionUrl("http://google.com")
        return this
    }
}
