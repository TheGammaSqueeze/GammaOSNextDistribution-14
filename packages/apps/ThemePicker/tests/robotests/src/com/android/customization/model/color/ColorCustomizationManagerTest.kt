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
 */
package com.android.customization.model.color

import android.app.WallpaperColors
import android.content.Context
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.android.customization.model.CustomizationManager
import com.android.customization.model.ResourceConstants.OVERLAY_CATEGORY_COLOR
import com.android.customization.model.ResourceConstants.OVERLAY_CATEGORY_SYSTEM_PALETTE
import com.android.customization.model.color.ColorOptionsProvider.COLOR_SOURCE_HOME
import com.android.customization.model.color.ColorOptionsProvider.COLOR_SOURCE_PRESET
import com.android.customization.model.color.ColorOptionsProvider.OVERLAY_COLOR_BOTH
import com.android.customization.model.color.ColorOptionsProvider.OVERLAY_COLOR_INDEX
import com.android.customization.model.color.ColorOptionsProvider.OVERLAY_COLOR_SOURCE
import com.android.customization.model.color.ColorOptionsProvider.OVERLAY_THEME_STYLE
import com.android.customization.model.theme.OverlayManagerCompat
import com.android.customization.picker.color.shared.model.ColorType
import com.android.systemui.monet.Style
import com.google.common.truth.Truth.assertThat
import org.json.JSONObject
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import org.robolectric.RobolectricTestRunner

/** Tests of {@link ColorCustomizationManager}. */
@RunWith(RobolectricTestRunner::class)
class ColorCustomizationManagerTest {

    @get:Rule val rule: MockitoRule = MockitoJUnit.rule()

    @Mock private lateinit var provider: ColorOptionsProvider
    @Mock private lateinit var mockOM: OverlayManagerCompat

    private lateinit var manager: ColorCustomizationManager

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)
        val application = ApplicationProvider.getApplicationContext<Context>()
        manager = ColorCustomizationManager(provider, application.contentResolver, mockOM)
    }

    @Test
    fun testParseSettings() {
        val source = COLOR_SOURCE_HOME
        val style = Style.SPRITZ
        val someColor = "aabbcc"
        val someOtherColor = "bbccdd"
        val settings =
            mapOf(
                OVERLAY_CATEGORY_SYSTEM_PALETTE to someColor,
                OVERLAY_CATEGORY_COLOR to someOtherColor,
                OVERLAY_COLOR_SOURCE to source,
                OVERLAY_THEME_STYLE to style.toString(),
                ColorOption.TIMESTAMP_FIELD to "12345"
            )
        val json = JSONObject(settings).toString()

        manager.parseSettings(json)

        assertThat(manager.currentColorSource).isEqualTo(source)
        assertThat(manager.currentStyle).isEqualTo(style.toString())
        assertThat(manager.currentOverlays.size).isEqualTo(2)
        assertThat(manager.currentOverlays[OVERLAY_CATEGORY_COLOR]).isEqualTo(someOtherColor)
        assertThat(manager.currentOverlays[OVERLAY_CATEGORY_SYSTEM_PALETTE]).isEqualTo(someColor)
    }

    @Test
    fun apply_PresetColorOption_index() {
        testApplyPresetColorOption(1, "1")
        testApplyPresetColorOption(2, "2")
        testApplyPresetColorOption(3, "3")
        testApplyPresetColorOption(4, "4")
    }

    private fun testApplyPresetColorOption(index: Int, value: String) {
        manager.apply(
            getPresetColorOption(index),
            object : CustomizationManager.Callback {
                override fun onSuccess() {}
                override fun onError(throwable: Throwable?) {}
            }
        )

        Thread.sleep(100)

        val overlaysJson = JSONObject(manager.storedOverlays)

        assertThat(overlaysJson.getString(OVERLAY_COLOR_INDEX)).isEqualTo(value)
    }
    @Test
    fun apply_WallpaperColorOption_index() {
        testApplyWallpaperColorOption(1, "1")
        testApplyWallpaperColorOption(2, "2")
        testApplyWallpaperColorOption(3, "3")
        testApplyWallpaperColorOption(4, "4")
    }

    private fun testApplyWallpaperColorOption(index: Int, value: String) {
        manager.apply(
            getWallpaperColorOption(index),
            object : CustomizationManager.Callback {
                override fun onSuccess() {}
                override fun onError(throwable: Throwable?) {}
            }
        )

        Thread.sleep(100)

        val overlaysJson = JSONObject(manager.storedOverlays)
        assertThat(overlaysJson.getString(OVERLAY_COLOR_INDEX)).isEqualTo(value)
    }

    private fun getPresetColorOption(index: Int): ColorOptionImpl {
        return ColorOptionImpl(
            "fake color",
            mapOf("fake_package" to "fake_color"),
            /* isDefault= */ false,
            COLOR_SOURCE_PRESET,
            Style.TONAL_SPOT,
            index,
            ColorOptionImpl.PreviewInfo(intArrayOf(0), intArrayOf(0)),
            ColorType.PRESET_COLOR
        )
    }

    private fun getWallpaperColorOption(index: Int): ColorOptionImpl {
        return ColorOptionImpl(
            "fake color",
            mapOf("fake_package" to "fake_color"),
            /* isDefault= */ false,
            COLOR_SOURCE_HOME,
            Style.TONAL_SPOT,
            index,
            ColorOptionImpl.PreviewInfo(intArrayOf(0), intArrayOf(0)),
            ColorType.WALLPAPER_COLOR
        )
    }

    @Test
    fun testApply_colorSeedFromWallpaperBoth_shouldReturnBothValue() {
        val wallpaperColor = WallpaperColors(Color.valueOf(Color.RED), null, null)
        manager.setWallpaperColors(wallpaperColor, wallpaperColor)

        manager.apply(
            getWallpaperColorOption(0),
            object : CustomizationManager.Callback {
                override fun onSuccess() {}
                override fun onError(throwable: Throwable?) {}
            }
        )

        Thread.sleep(100)

        val overlaysJson = JSONObject(manager.storedOverlays)
        assertThat(overlaysJson.getString(OVERLAY_COLOR_BOTH)).isEqualTo("1")
    }

    @Test
    fun testApply_colorSeedFromWallpaperDifferent_shouldReturnNonBothValue() {
        val wallpaperColor1 = WallpaperColors(Color.valueOf(Color.RED), null, null)
        val wallpaperColor2 = WallpaperColors(Color.valueOf(Color.BLUE), null, null)
        manager.setWallpaperColors(wallpaperColor1, wallpaperColor2)

        manager.apply(
            getWallpaperColorOption(0),
            object : CustomizationManager.Callback {
                override fun onSuccess() {}
                override fun onError(throwable: Throwable?) {}
            }
        )

        Thread.sleep(100)

        val overlaysJson = JSONObject(manager.storedOverlays)
        assertThat(overlaysJson.getString(OVERLAY_COLOR_BOTH)).isEqualTo("0")
    }
}
