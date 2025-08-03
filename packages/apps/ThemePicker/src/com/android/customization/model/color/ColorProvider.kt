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
import android.app.WallpaperManager
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Resources
import androidx.annotation.ColorInt
import androidx.core.graphics.ColorUtils.setAlphaComponent
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.android.customization.model.CustomizationManager.OptionsFetchedListener
import com.android.customization.model.ResourceConstants.COLOR_BUNDLES_ARRAY_NAME
import com.android.customization.model.ResourceConstants.COLOR_BUNDLE_MAIN_COLOR_PREFIX
import com.android.customization.model.ResourceConstants.COLOR_BUNDLE_NAME_PREFIX
import com.android.customization.model.ResourceConstants.COLOR_BUNDLE_STYLE_PREFIX
import com.android.customization.model.ResourceConstants.OVERLAY_CATEGORY_COLOR
import com.android.customization.model.ResourceConstants.OVERLAY_CATEGORY_SYSTEM_PALETTE
import com.android.customization.model.ResourcesApkProvider
import com.android.customization.model.color.ColorOptionsProvider.COLOR_SOURCE_HOME
import com.android.customization.model.color.ColorOptionsProvider.COLOR_SOURCE_LOCK
import com.android.customization.model.color.ColorUtils.toColorString
import com.android.customization.picker.color.shared.model.ColorType
import com.android.systemui.monet.ColorScheme
import com.android.systemui.monet.Style
import com.android.themepicker.R
import com.android.wallpaper.module.InjectorProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Default implementation of {@link ColorOptionsProvider} that reads preset colors from a stub APK.
 * TODO (b/311212666): Make [ColorProvider] and [ColorCustomizationManager] injectable
 */
class ColorProvider(private val context: Context, stubPackageName: String) :
    ResourcesApkProvider(context, stubPackageName), ColorOptionsProvider {

    companion object {
        const val themeStyleEnabled = true
        val styleSize = if (themeStyleEnabled) Style.values().size else 1
        private const val TAG = "ColorProvider"
        private const val MAX_SEED_COLORS = 4
        private const val MAX_PRESET_COLORS = 4
        private const val ALPHA_MASK = 0xFF
    }

    private val monetEnabled = ColorUtils.isMonetEnabled(context)
    // TODO(b/202145216): Use style method to fetch the list of style.
    private var styleList =
        if (themeStyleEnabled)
            arrayOf(Style.TONAL_SPOT, Style.SPRITZ, Style.VIBRANT, Style.EXPRESSIVE)
        else arrayOf(Style.TONAL_SPOT)

    private var monochromeBundleName: String? = null

    private val scope =
        if (mContext is LifecycleOwner) {
            mContext.lifecycleScope
        } else {
            CoroutineScope(Dispatchers.Default + SupervisorJob())
        }

    private var colorsAvailable = true
    private var presetColorBundles: List<ColorOption>? = null
    private var wallpaperColorBundles: List<ColorOption>? = null
    private var homeWallpaperColors: WallpaperColors? = null
    private var lockWallpaperColors: WallpaperColors? = null

    override fun isAvailable(): Boolean {
        return monetEnabled && super.isAvailable() && colorsAvailable
    }

    override fun fetch(
        callback: OptionsFetchedListener<ColorOption>?,
        reload: Boolean,
        homeWallpaperColors: WallpaperColors?,
        lockWallpaperColors: WallpaperColors?,
    ) {
        val wallpaperColorsChanged =
            this.homeWallpaperColors != homeWallpaperColors ||
                this.lockWallpaperColors != lockWallpaperColors
        if (wallpaperColorsChanged || reload) {
            loadSeedColors(
                homeWallpaperColors,
                lockWallpaperColors,
            )
            this.homeWallpaperColors = homeWallpaperColors
            this.lockWallpaperColors = lockWallpaperColors
        }
        if (presetColorBundles == null || reload) {
            scope.launch {
                try {
                    loadPreset()
                } catch (e: Throwable) {
                    colorsAvailable = false
                    callback?.onError(e)
                    return@launch
                }
                callback?.onOptionsLoaded(buildFinalList())
            }
        } else {
            callback?.onOptionsLoaded(buildFinalList())
        }
    }

    private fun isLockScreenWallpaperLastApplied(): Boolean {
        // The WallpaperId increases every time a new wallpaper is set, so the larger wallpaper id
        // is the most recently set wallpaper
        val manager = WallpaperManager.getInstance(mContext)
        return manager.getWallpaperId(WallpaperManager.FLAG_LOCK) >
            manager.getWallpaperId(WallpaperManager.FLAG_SYSTEM)
    }

    private fun loadSeedColors(
        homeWallpaperColors: WallpaperColors?,
        lockWallpaperColors: WallpaperColors?,
    ) {
        if (homeWallpaperColors == null) return

        val bundles: MutableList<ColorOption> = ArrayList()
        val colorsPerSource =
            if (lockWallpaperColors == null) {
                MAX_SEED_COLORS
            } else {
                MAX_SEED_COLORS / 2
            }

        if (lockWallpaperColors != null) {
            val shouldLockColorsGoFirst = isLockScreenWallpaperLastApplied()
            // First half of the colors
            buildColorSeeds(
                if (shouldLockColorsGoFirst) lockWallpaperColors else homeWallpaperColors,
                colorsPerSource,
                if (shouldLockColorsGoFirst) COLOR_SOURCE_LOCK else COLOR_SOURCE_HOME,
                true,
                bundles,
            )
            // Second half of the colors
            buildColorSeeds(
                if (shouldLockColorsGoFirst) homeWallpaperColors else lockWallpaperColors,
                MAX_SEED_COLORS - bundles.size / styleSize,
                if (shouldLockColorsGoFirst) COLOR_SOURCE_HOME else COLOR_SOURCE_LOCK,
                false,
                bundles,
            )
        } else {
            buildColorSeeds(
                homeWallpaperColors,
                colorsPerSource,
                COLOR_SOURCE_HOME,
                true,
                bundles,
            )
        }
        wallpaperColorBundles = bundles
    }

    private fun buildColorSeeds(
        wallpaperColors: WallpaperColors,
        maxColors: Int,
        source: String,
        containsDefault: Boolean,
        bundles: MutableList<ColorOption>,
    ) {
        val seedColors = ColorScheme.getSeedColors(wallpaperColors)
        val defaultSeed = seedColors.first()
        buildBundle(defaultSeed, 0, containsDefault, source, bundles)
        for ((i, colorInt) in seedColors.drop(1).take(maxColors - 1).withIndex()) {
            buildBundle(colorInt, i + 1, false, source, bundles)
        }
    }

    private fun buildBundle(
        colorInt: Int,
        i: Int,
        isDefault: Boolean,
        source: String,
        bundles: MutableList<ColorOption>,
    ) {
        // TODO(b/202145216): Measure time cost in the loop.
        for (style in styleList) {
            val lightColorScheme = ColorScheme(colorInt, /* darkTheme= */ false, style)
            val darkColorScheme = ColorScheme(colorInt, /* darkTheme= */ true, style)
            val builder = ColorOptionImpl.Builder()
            builder.lightColors = getLightColorPreview(lightColorScheme)
            builder.darkColors = getDarkColorPreview(darkColorScheme)
            builder.addOverlayPackage(
                OVERLAY_CATEGORY_SYSTEM_PALETTE,
                if (isDefault) "" else toColorString(colorInt)
            )
            builder.title =
                when (style) {
                    Style.TONAL_SPOT ->
                        context.getString(R.string.content_description_dynamic_color_option)
                    Style.SPRITZ ->
                        context.getString(R.string.content_description_neutral_color_option)
                    Style.VIBRANT ->
                        context.getString(R.string.content_description_vibrant_color_option)
                    Style.EXPRESSIVE ->
                        context.getString(R.string.content_description_expressive_color_option)
                    else -> context.getString(R.string.content_description_dynamic_color_option)
                }
            builder.source = source
            builder.style = style
            // Color option index value starts from 1.
            builder.index = i + 1
            builder.isDefault = isDefault
            builder.type = ColorType.WALLPAPER_COLOR
            bundles.add(builder.build())
        }
    }

    /**
     * Returns the light theme version of the Revamped UI preview of a ColorScheme based on this
     * order: top left, top right, bottom left, bottom right
     *
     * This color mapping corresponds to GM3 colors: Primary (light), Primary (light), Secondary
     * LStar 85, and Tertiary LStar 70
     */
    @ColorInt
    private fun getLightColorPreview(colorScheme: ColorScheme): IntArray {
        return intArrayOf(
            setAlphaComponent(colorScheme.accent1.s600, ALPHA_MASK),
            setAlphaComponent(colorScheme.accent1.s600, ALPHA_MASK),
            ColorStateList.valueOf(colorScheme.accent2.s500).withLStar(85f).colors[0],
            setAlphaComponent(colorScheme.accent3.s300, ALPHA_MASK),
        )
    }

    /**
     * Returns the dark theme version of the Revamped UI preview of a ColorScheme based on this
     * order: top left, top right, bottom left, bottom right
     *
     * This color mapping corresponds to GM3 colors: Primary (dark), Primary (dark), Secondary LStar
     * 35, and Tertiary LStar 70
     */
    @ColorInt
    private fun getDarkColorPreview(colorScheme: ColorScheme): IntArray {
        return intArrayOf(
            setAlphaComponent(colorScheme.accent1.s200, ALPHA_MASK),
            setAlphaComponent(colorScheme.accent1.s200, ALPHA_MASK),
            ColorStateList.valueOf(colorScheme.accent2.s500).withLStar(35f).colors[0],
            setAlphaComponent(colorScheme.accent3.s300, ALPHA_MASK),
        )
    }

    /**
     * Returns the light theme version of the Revamped UI preview of a ColorScheme based on this
     * order: top left, top right, bottom left, bottom right
     *
     * This color mapping corresponds to GM3 colors: Primary LStar 0, Primary LStar 0, Secondary
     * LStar 85, and Tertiary LStar 70
     */
    @ColorInt
    private fun getLightMonochromePreview(colorScheme: ColorScheme): IntArray {
        return intArrayOf(
            setAlphaComponent(colorScheme.accent1.s1000, ALPHA_MASK),
            setAlphaComponent(colorScheme.accent1.s1000, ALPHA_MASK),
            ColorStateList.valueOf(colorScheme.accent2.s500).withLStar(85f).colors[0],
            setAlphaComponent(colorScheme.accent3.s300, ALPHA_MASK),
        )
    }

    /**
     * Returns the dark theme version of the Revamped UI preview of a ColorScheme based on this
     * order: top left, top right, bottom left, bottom right
     *
     * This color mapping corresponds to GM3 colors: Primary LStar 99, Primary LStar 99, Secondary
     * LStar 35, and Tertiary LStar 70
     */
    @ColorInt
    private fun getDarkMonochromePreview(colorScheme: ColorScheme): IntArray {
        return intArrayOf(
            setAlphaComponent(colorScheme.accent1.s10, ALPHA_MASK),
            setAlphaComponent(colorScheme.accent1.s10, ALPHA_MASK),
            ColorStateList.valueOf(colorScheme.accent2.s500).withLStar(35f).colors[0],
            setAlphaComponent(colorScheme.accent3.s300, ALPHA_MASK),
        )
    }

    /**
     * Returns the Revamped UI preview of a preset ColorScheme based on this order: top left, top
     * right, bottom left, bottom right
     */
    private fun getPresetColorPreview(colorScheme: ColorScheme, seed: Int): IntArray {
        val colors =
            when (colorScheme.style) {
                Style.FRUIT_SALAD -> intArrayOf(seed, colorScheme.accent1.s200)
                Style.TONAL_SPOT -> intArrayOf(colorScheme.accentColor, colorScheme.accentColor)
                Style.RAINBOW -> intArrayOf(colorScheme.accent1.s200, colorScheme.accent1.s200)
                else -> intArrayOf(colorScheme.accent1.s100, colorScheme.accent1.s100)
            }
        return intArrayOf(
            colors[0],
            colors[1],
            colors[0],
            colors[1],
        )
    }

    private suspend fun loadPreset() =
        withContext(Dispatchers.IO) {
            val bundles: MutableList<ColorOption> = ArrayList()

            val bundleNames =
                if (isAvailable) getItemsFromStub(COLOR_BUNDLES_ARRAY_NAME) else emptyArray()
            // Color option index value starts from 1.
            var index = 1
            val maxPresetColors = if (themeStyleEnabled) bundleNames.size else MAX_PRESET_COLORS

            // keep track of whether monochrome is included in preset colors to determine
            // inclusion in wallpaper colors
            var hasMonochrome = false
            for (bundleName in bundleNames.take(maxPresetColors)) {
                if (themeStyleEnabled) {
                    val styleName =
                        try {
                            getItemStringFromStub(COLOR_BUNDLE_STYLE_PREFIX, bundleName)
                        } catch (e: Resources.NotFoundException) {
                            null
                        }
                    val style =
                        try {
                            if (styleName != null) Style.valueOf(styleName) else Style.TONAL_SPOT
                        } catch (e: IllegalArgumentException) {
                            Style.TONAL_SPOT
                        }

                    if (style == Style.MONOCHROMATIC) {
                        if (
                            !InjectorProvider.getInjector()
                                .getFlags()
                                .isMonochromaticThemeEnabled(mContext)
                        ) {
                            continue
                        }
                        hasMonochrome = true
                        monochromeBundleName = bundleName
                    }
                    bundles.add(buildPreset(bundleName, index, style))
                } else {
                    bundles.add(buildPreset(bundleName, index, null))
                }

                index++
            }
            if (!hasMonochrome) {
                monochromeBundleName = null
            }

            presetColorBundles = bundles
        }

    private fun buildPreset(
        bundleName: String,
        index: Int,
        style: Style? = null,
        type: ColorType = ColorType.PRESET_COLOR,
    ): ColorOptionImpl {
        val builder = ColorOptionImpl.Builder()
        builder.title = getItemStringFromStub(COLOR_BUNDLE_NAME_PREFIX, bundleName)
        builder.index = index
        builder.source = ColorOptionsProvider.COLOR_SOURCE_PRESET
        builder.type = type
        val colorFromStub = getItemColorFromStub(COLOR_BUNDLE_MAIN_COLOR_PREFIX, bundleName)
        var darkColorScheme = ColorScheme(colorFromStub, /* darkTheme= */ true)
        var lightColorScheme = ColorScheme(colorFromStub, /* darkTheme= */ false)
        val lightColor = lightColorScheme.accentColor
        val darkColor = darkColorScheme.accentColor
        var lightColors = intArrayOf(lightColor, lightColor, lightColor, lightColor)
        var darkColors = intArrayOf(darkColor, darkColor, darkColor, darkColor)
        builder.addOverlayPackage(OVERLAY_CATEGORY_COLOR, toColorString(colorFromStub))
        builder.addOverlayPackage(OVERLAY_CATEGORY_SYSTEM_PALETTE, toColorString(colorFromStub))
        if (style != null) {
            builder.style = style

            lightColorScheme = ColorScheme(colorFromStub, /* darkTheme= */ false, style)
            darkColorScheme = ColorScheme(colorFromStub, /* darkTheme= */ true, style)

            when (style) {
                Style.MONOCHROMATIC -> {
                    darkColors = getDarkMonochromePreview(darkColorScheme)
                    lightColors = getLightMonochromePreview(lightColorScheme)
                }
                else -> {
                    darkColors = getPresetColorPreview(darkColorScheme, colorFromStub)
                    lightColors = getPresetColorPreview(lightColorScheme, colorFromStub)
                }
            }
        }
        builder.lightColors = lightColors
        builder.darkColors = darkColors
        return builder.build()
    }

    private fun buildFinalList(): List<ColorOption> {
        val presetColors = presetColorBundles ?: emptyList()
        val wallpaperColors = wallpaperColorBundles?.toMutableList() ?: mutableListOf()
        // Insert monochrome in the second position if it is enabled and included in preset
        // colors
        if (InjectorProvider.getInjector().getFlags().isMonochromaticThemeEnabled(mContext)) {
            monochromeBundleName?.let {
                if (wallpaperColors.isNotEmpty()) {
                    wallpaperColors.add(
                        1,
                        buildPreset(it, -1, Style.MONOCHROMATIC, ColorType.WALLPAPER_COLOR)
                    )
                }
            }
        }
        return wallpaperColors + presetColors
    }
}
