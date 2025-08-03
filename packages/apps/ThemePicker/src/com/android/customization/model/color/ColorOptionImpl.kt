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
package com.android.customization.model.color

import android.content.Context
import android.stats.style.StyleEnums
import android.view.View
import androidx.annotation.ColorInt
import com.android.customization.model.color.ColorOptionsProvider.ColorSource
import com.android.customization.picker.color.shared.model.ColorType
import com.android.systemui.monet.Style
import com.android.themepicker.R

/**
 * Represents a color option in the revamped UI, it can be used for both wallpaper and preset colors
 */
class ColorOptionImpl(
    title: String?,
    overlayPackages: Map<String, String?>,
    isDefault: Boolean,
    private val source: String?,
    style: Style,
    index: Int,
    private val previewInfo: PreviewInfo,
    val type: ColorType,
) : ColorOption(title, overlayPackages, isDefault, style, index) {

    class PreviewInfo(
        @ColorInt val lightColors: IntArray,
        @ColorInt val darkColors: IntArray,
    ) : ColorOption.PreviewInfo {
        @ColorInt
        fun resolveColors(darkTheme: Boolean): IntArray {
            return if (darkTheme) darkColors else lightColors
        }
    }

    override fun bindThumbnailTile(view: View?) {
        // Do nothing. This function will no longer be used in the Revamped UI
    }

    override fun getLayoutResId(): Int {
        return R.layout.color_option
    }

    override fun getPreviewInfo(): PreviewInfo {
        return previewInfo
    }

    override fun getContentDescription(context: Context): CharSequence? {
        return title
    }

    override fun getSource(): String? {
        return source
    }

    override fun getSourceForLogging(): Int {
        return when (getSource()) {
            ColorOptionsProvider.COLOR_SOURCE_PRESET -> StyleEnums.COLOR_SOURCE_PRESET_COLOR
            ColorOptionsProvider.COLOR_SOURCE_HOME -> StyleEnums.COLOR_SOURCE_HOME_SCREEN_WALLPAPER
            ColorOptionsProvider.COLOR_SOURCE_LOCK -> StyleEnums.COLOR_SOURCE_LOCK_SCREEN_WALLPAPER
            else -> StyleEnums.COLOR_SOURCE_UNSPECIFIED
        }
    }

    override fun getStyleForLogging(): Int = style.toString().hashCode()

    class Builder {
        var title: String? = null

        @ColorInt var lightColors: IntArray = intArrayOf()

        @ColorInt var darkColors: IntArray = intArrayOf()

        @ColorSource var source: String? = null
        var isDefault = false
        var style = Style.TONAL_SPOT
        var index = 0
        var packages: MutableMap<String, String?> = HashMap()
        var type = ColorType.WALLPAPER_COLOR

        fun build(): ColorOptionImpl {
            return ColorOptionImpl(
                title,
                packages,
                isDefault,
                source,
                style,
                index,
                createPreviewInfo(),
                type
            )
        }

        private fun createPreviewInfo(): PreviewInfo {
            return PreviewInfo(lightColors, darkColors)
        }

        fun addOverlayPackage(category: String?, packageName: String?): ColorOptionImpl.Builder {
            category?.let { packages[category] = packageName }
            return this
        }
    }
}
