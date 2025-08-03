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
package com.android.wallpaper.picker.preview.ui.fragment

import androidx.test.filters.MediumTest
import androidx.test.runner.AndroidJUnit4
import com.android.wallpaper.model.WallpaperInfo
import com.android.wallpaper.module.InjectorProvider
import com.android.wallpaper.module.logging.TestUserEventLogger
import com.android.wallpaper.testing.TestInjector
import com.android.wallpaper.testing.TestStaticWallpaperInfo
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith

@MediumTest
@RunWith(AndroidJUnit4::class)
class SmallPreviewFragmentTest {
    private val testStaticWallpaper =
        TestStaticWallpaperInfo(TestStaticWallpaperInfo.COLOR_DEFAULT).setWallpaperAttributions()
    private val testUserEventLogger = TestUserEventLogger()

    @Before
    fun setUp() {
        InjectorProvider.setInjector(TestInjector(testUserEventLogger))
    }

    @Test @Ignore("b/295958495") fun testWallpaperInfoIsNotNull() {}

    private fun TestStaticWallpaperInfo.setWallpaperAttributions(): WallpaperInfo {
        setAttributions(listOf("Title", "Subtitle 1", "Subtitle 2"))
        setCollectionId("collectionStatic")
        setWallpaperId("wallpaperStatic")
        setActionUrl("http://google.com")
        return this
    }
}
