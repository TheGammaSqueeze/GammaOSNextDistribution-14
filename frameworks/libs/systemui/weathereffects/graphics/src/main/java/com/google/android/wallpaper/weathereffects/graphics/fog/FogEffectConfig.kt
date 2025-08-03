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

package com.google.android.wallpaper.weathereffects.graphics.fog

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.RuntimeShader
import androidx.annotation.FloatRange
import com.google.android.wallpaper.weathereffects.graphics.utils.GraphicsUtils

/** Configuration for a fog effect. */
data class FogEffectConfig(
    /** The main shader of the effect. */
    val shader: RuntimeShader,
    /** The color grading shader. */
    val colorGradingShader: RuntimeShader,
    /** The main lut (color grading) for the effect. */
    val lut: Bitmap?,
    /** A bitmap containing the foreground of the image. */
    val foreground: Bitmap,
    /** A bitmap containing the background of the image. */
    val background: Bitmap,
    /** Pixel density of the display. Used for dithering. */
    val pixelDensity: Float,
    /** The amount of the fog. This contributes to the color grading as well. */
    @FloatRange(from = 0.0, to = 1.0) val intensity: Float,
    /** The intensity of the color grading. 0: no color grading, 1: color grading in full effect. */
    @FloatRange(from = 0.0, to = 1.0) val colorGradingIntensity: Float,
) {
    /**
     * Constructor for [FogEffectConfig].
     *
     * @param assets the application [AssetManager].
     * @param foreground a bitmap containing the foreground of the image.
     * @param background a bitmap containing the background of the image.
     * @param pixelDensity pixel density of the display.
     * @param intensity initial intensity that affects the amount of fog and color grading. Expected
     *   range is [0, 1]. You can always change the intensity dynamically. Defaults to 1.
     */
    constructor(
        assets: AssetManager,
        foreground: Bitmap,
        background: Bitmap,
        pixelDensity: Float,
        intensity: Float = DEFAULT_INTENSITY,
    ) : this(
        shader = GraphicsUtils.loadShader(assets, SHADER_PATH),
        colorGradingShader = GraphicsUtils.loadShader(assets, COLOR_GRADING_SHADER_PATH),
        lut = GraphicsUtils.loadTexture(assets, LOOKUP_TABLE_TEXTURE_PATH),
        foreground,
        background,
        pixelDensity,
        intensity,
        COLOR_GRADING_INTENSITY
    )

    private companion object {
        private const val SHADER_PATH = "shaders/fog_effect.agsl"
        private const val COLOR_GRADING_SHADER_PATH = "shaders/color_grading_lut.agsl"
        private const val LOOKUP_TABLE_TEXTURE_PATH = "textures/lut_rain_and_fog.png"
        private const val DEFAULT_INTENSITY = 1f
        private const val COLOR_GRADING_INTENSITY = 0.7f
    }
}
