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

import com.android.customization.model.ResourceConstants.OVERLAY_CATEGORY_SYSTEM_PALETTE
import com.android.customization.model.color.ColorOptionsProvider.COLOR_SOURCE_HOME
import com.android.customization.model.color.ColorOptionsProvider.COLOR_SOURCE_LOCK
import com.android.customization.model.color.ColorOptionsProvider.COLOR_SOURCE_PRESET
import com.android.customization.picker.color.shared.model.ColorType
import com.android.systemui.monet.Style
import com.google.common.truth.Truth.assertThat
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import org.robolectric.RobolectricTestRunner

/** Tests of {@link ColorOption}. */
@RunWith(RobolectricTestRunner::class)
class ColorOptionTest {

    @get:Rule val rule: MockitoRule = MockitoJUnit.rule()

    @Mock private lateinit var manager: ColorCustomizationManager

    @Test
    fun colorOption_Source() {
        testColorOptionSource(COLOR_SOURCE_HOME)
        testColorOptionSource(COLOR_SOURCE_LOCK)
        testColorOptionSource(COLOR_SOURCE_PRESET)
    }

    private fun testColorOptionSource(source: String) {
        val colorOption: ColorOption =
            ColorOptionImpl(
                "fake color",
                mapOf("fake_package" to "fake_color"),
                false,
                source,
                Style.TONAL_SPOT,
                /* index= */ 0,
                ColorOptionImpl.PreviewInfo(intArrayOf(0), intArrayOf(0)),
                ColorType.WALLPAPER_COLOR
            )
        assertThat(colorOption.source).isEqualTo(source)
    }

    @Test
    fun colorOption_style() {
        testColorOptionStyle(Style.TONAL_SPOT)
        testColorOptionStyle(Style.SPRITZ)
        testColorOptionStyle(Style.VIBRANT)
        testColorOptionStyle(Style.EXPRESSIVE)
    }

    private fun testColorOptionStyle(style: Style) {
        val colorOption: ColorOption =
            ColorOptionImpl(
                "fake color",
                mapOf("fake_package" to "fake_color"),
                /* isDefault= */ false,
                "fake_source",
                style,
                0,
                ColorOptionImpl.PreviewInfo(intArrayOf(0), intArrayOf(0)),
                ColorType.WALLPAPER_COLOR
            )
        assertThat(colorOption.style).isEqualTo(style)
    }

    @Test
    fun colorOption_index() {
        testColorOptionIndex(1)
        testColorOptionIndex(2)
        testColorOptionIndex(3)
        testColorOptionIndex(4)
    }

    private fun testColorOptionIndex(index: Int) {
        val colorOption: ColorOption =
            ColorOptionImpl(
                "fake color",
                mapOf("fake_package" to "fake_color"),
                /* isDefault= */ false,
                "fake_source",
                Style.TONAL_SPOT,
                index,
                ColorOptionImpl.PreviewInfo(intArrayOf(0), intArrayOf(0)),
                ColorType.WALLPAPER_COLOR
            )
        assertThat(colorOption.index).isEqualTo(index)
    }

    private fun setUpWallpaperColorOption(
        isDefault: Boolean,
        source: String = "some_source"
    ): ColorOptionImpl {
        val overlays =
            if (isDefault) {
                HashMap()
            } else {
                mapOf("package" to "value", "otherPackage" to "otherValue")
            }
        `when`(manager.currentOverlays).thenReturn(overlays)
        return ColorOptionImpl(
            "seed",
            overlays,
            isDefault,
            source,
            Style.TONAL_SPOT,
            /* index= */ 0,
            ColorOptionImpl.PreviewInfo(intArrayOf(0), intArrayOf(0)),
            ColorType.WALLPAPER_COLOR
        )
    }

    @Test
    fun wallpaperColorOption_isActive_notDefault_SourceSet() {
        val source = "some_source"
        val colorOption = setUpWallpaperColorOption(false, source)
        `when`(manager.currentColorSource).thenReturn(source)

        assertThat(colorOption.isActive(manager)).isTrue()
    }

    @Test
    fun wallpaperColorOption_isActive_notDefault_NoSource() {
        val colorOption = setUpWallpaperColorOption(false)
        `when`(manager.currentColorSource).thenReturn(null)

        assertThat(colorOption.isActive(manager)).isTrue()
    }

    @Test
    fun wallpaperColorOption_isActive_notDefault_differentSource() {
        val colorOption = setUpWallpaperColorOption(false)
        `when`(manager.currentColorSource).thenReturn("some_other_source")

        assertThat(colorOption.isActive(manager)).isFalse()
    }

    @Test
    fun wallpaperColorOption_isActive_default_emptyJson() {
        val colorOption = setUpWallpaperColorOption(true)
        `when`(manager.storedOverlays).thenReturn("")

        assertThat(colorOption.isActive(manager)).isTrue()
    }

    @Test
    fun wallpaperColorOption_isActive_default_nonEmptyJson() {
        val colorOption = setUpWallpaperColorOption(true)

        `when`(manager.storedOverlays).thenReturn("{non-empty-json}")

        // Should still be Active because overlays is empty
        assertThat(colorOption.isActive(manager)).isTrue()
    }

    @Test
    fun wallpaperColorOption_isActive_default_nonEmptyOverlays() {
        val colorOption = setUpWallpaperColorOption(true)

        val settings = mapOf(OVERLAY_CATEGORY_SYSTEM_PALETTE to "fake_color")
        val json = JSONObject(settings).toString()
        `when`(manager.storedOverlays).thenReturn(json)
        `when`(manager.currentOverlays).thenReturn(settings)
        assertThat(colorOption.isActive(manager)).isFalse()
    }
}
