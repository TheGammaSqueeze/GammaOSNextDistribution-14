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

package com.google.android.wallpaper.weathereffects.graphics.rain

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RuntimeShader
import androidx.annotation.FloatRange
import com.google.android.wallpaper.weathereffects.graphics.utils.GraphicsUtils

/** Configuration for a rain effect. */
data class RainEffectConfig(
    /** The first layer of the shader, rain showering in the environment. */
    val rainShowerShader: RuntimeShader,
    /** The second layer of the shader, rain running on the glass window. */
    val glassRainShader: RuntimeShader,
    /** The final layer of the shader, which adds color grading. */
    val colorGradingShader: RuntimeShader,
    /** The main lut (color grading) for the effect. */
    val lut: Bitmap?,
    /** A bitmap containing the foreground of the image. */
    val foreground: Bitmap,
    /** A bitmap containing the background of the image. */
    val background: Bitmap,
    /** The amount of the rain. This contributes to the color grading as well. */
    @FloatRange(from = 0.0, to = 1.0) val intensity: Float,
    /** The intensity of the color grading. 0: no color grading, 1: color grading in full effect. */
    @FloatRange(from = 0.0, to = 1.0) val colorGradingIntensity: Float,
) {
    /**
     * Constructor for [RainEffectConfig].
     *
     * @param context the application context.
     * @param foreground a bitmap containing the foreground of the image.
     * @param background a bitmap containing the background of the image.
     * @param intensity initial intensity that affects the amount of rain and color grading.
     *   Expected range is [0, 1]. You can always change the intensity dynamically. Defaults to 1.
     */
    constructor(
        context: Context,
        foreground: Bitmap,
        background: Bitmap,
        intensity: Float = DEFAULT_INTENSITY,
    ) : this(
        rainShowerShader = GraphicsUtils.loadShader(context.assets, RAIN_SHOWER_LAYER_SHADER_PATH),
        glassRainShader = GraphicsUtils.loadShader(context.assets, GLASS_RAIN_LAYER_SHADER_PATH),
        colorGradingShader = GraphicsUtils.loadShader(context.assets, COLOR_GRADING_SHADER_PATH),
        lut = GraphicsUtils.loadTexture(context.assets, LOOKUP_TABLE_TEXTURE_PATH),
        foreground,
        background,
        intensity,
        COLOR_GRADING_INTENSITY
    )

    private companion object {
        private const val RAIN_SHOWER_LAYER_SHADER_PATH = "shaders/rain_shower_layer.agsl"
        private const val GLASS_RAIN_LAYER_SHADER_PATH = "shaders/rain_glass_layer.agsl"
        private const val COLOR_GRADING_SHADER_PATH = "shaders/color_grading_lut.agsl"
        private const val LOOKUP_TABLE_TEXTURE_PATH = "textures/lut_rain_and_fog.png"
        private const val DEFAULT_INTENSITY = 1f
        private const val COLOR_GRADING_INTENSITY = 0.7f
    }
}
