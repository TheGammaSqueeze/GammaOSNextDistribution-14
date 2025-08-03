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

package com.google.android.wallpaper.weathereffects.graphics.snow

import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RenderEffect
import android.graphics.Shader
import android.util.SizeF
import com.google.android.wallpaper.weathereffects.graphics.FrameBuffer
import com.google.android.wallpaper.weathereffects.graphics.WeatherEffect
import com.google.android.wallpaper.weathereffects.graphics.utils.GraphicsUtils
import com.google.android.wallpaper.weathereffects.graphics.utils.ImageCrop
import java.util.concurrent.Executor
import kotlin.random.Random

/** Defines and generates the rain weather effect animation. */
class SnowEffect(
    /** The config of the snow effect. */
    private val snowConfig: SnowEffectConfig,
    /** The initial size of the surface where the effect will be shown. */
    surfaceSize: SizeF,
    /** App main executor. */
    private val mainExecutor: Executor
) : WeatherEffect {

    private val snowPaint = Paint().also { it.shader = snowConfig.colorGradingShader }
    private var elapsedTime: Float = 0f

    private val frameBuffer = FrameBuffer(snowConfig.background.width, snowConfig.background.height)
    private val frameBufferPaint = Paint().also { it.shader = snowConfig.accumulatedSnowShader }

    init {
        frameBuffer.setRenderEffect(RenderEffect.createBlurEffect(4f, 4f, Shader.TileMode.CLAMP))
        updateTextureUniforms()
        adjustCropping(surfaceSize)
        prepareColorGrading()
        setIntensity(snowConfig.intensity)

        // Generate accumulated snow at the end after we updated all the uniforms.
        generateAccumulatedSnow()
    }

    override fun resize(newSurfaceSize: SizeF) = adjustCropping(newSurfaceSize)

    override fun update(deltaMillis: Long, frameTimeNanos: Long) {
        elapsedTime += deltaMillis * MILLIS_TO_SECONDS
        snowConfig.shader.setFloatUniform("time", elapsedTime)
        snowConfig.colorGradingShader.setInputShader("texture", snowConfig.shader)
    }

    override fun draw(canvas: Canvas) {
        canvas.drawPaint(snowPaint)
    }

    override fun reset() {
        elapsedTime = Random.nextFloat() * 90f
    }

    override fun release() {
        snowConfig.lut?.recycle()
        snowConfig.blurredBackground.recycle()
        frameBuffer.close()
    }

    override fun setIntensity(intensity: Float) {
        snowConfig.shader.setFloatUniform("intensity", intensity)
        snowConfig.colorGradingShader.setFloatUniform(
            "intensity",
            snowConfig.colorGradingIntensity * intensity
        )
        snowConfig.accumulatedSnowShader.setFloatUniform("intensity", intensity)

        // Regenerate accumulated snow since the uniform changed.
        generateAccumulatedSnow()
    }

    private fun adjustCropping(surfaceSize: SizeF) {
        val imageCropFgd =
            ImageCrop.centerCoverCrop(
                surfaceSize.width,
                surfaceSize.height,
                snowConfig.foreground.width.toFloat(),
                snowConfig.foreground.height.toFloat()
            )
        snowConfig.shader.setFloatUniform(
            "uvOffsetFgd",
            imageCropFgd.leftOffset,
            imageCropFgd.topOffset
        )
        snowConfig.shader.setFloatUniform(
            "uvScaleFgd",
            imageCropFgd.horizontalScale,
            imageCropFgd.verticalScale
        )
        val imageCropBgd =
            ImageCrop.centerCoverCrop(
                surfaceSize.width,
                surfaceSize.height,
                snowConfig.background.width.toFloat(),
                snowConfig.background.height.toFloat()
            )
        snowConfig.shader.setFloatUniform(
            "uvOffsetBgd",
            imageCropBgd.leftOffset,
            imageCropBgd.topOffset
        )
        snowConfig.shader.setFloatUniform(
            "uvScaleBgd",
            imageCropBgd.horizontalScale,
            imageCropBgd.verticalScale
        )
        snowConfig.shader.setFloatUniform("screenSize", surfaceSize.width, surfaceSize.height)
        snowConfig.shader.setFloatUniform(
            "screenAspectRatio",
            GraphicsUtils.getAspectRatio(surfaceSize)
        )
    }

    private fun updateTextureUniforms() {
        snowConfig.shader.setInputBuffer(
            "foreground",
            BitmapShader(snowConfig.foreground, Shader.TileMode.MIRROR, Shader.TileMode.MIRROR)
        )

        snowConfig.shader.setInputBuffer(
            "background",
            BitmapShader(snowConfig.background, Shader.TileMode.MIRROR, Shader.TileMode.MIRROR)
        )

        snowConfig.shader.setInputBuffer(
            "blurredBackground",
            BitmapShader(
                snowConfig.blurredBackground,
                Shader.TileMode.MIRROR,
                Shader.TileMode.MIRROR
            )
        )
    }

    private fun prepareColorGrading() {
        snowConfig.colorGradingShader.setInputShader("texture", snowConfig.shader)
        snowConfig.lut?.let {
            snowConfig.colorGradingShader.setInputShader(
                "lut",
                BitmapShader(it, Shader.TileMode.MIRROR, Shader.TileMode.MIRROR)
            )
        }
    }

    private fun generateAccumulatedSnow() {
        val renderingCanvas = frameBuffer.beginDrawing()
        snowConfig.accumulatedSnowShader.setFloatUniform(
            "imageSize",
            renderingCanvas.width.toFloat(),
            renderingCanvas.height.toFloat()
        )
        snowConfig.accumulatedSnowShader.setInputBuffer(
            "foreground",
            BitmapShader(snowConfig.foreground, Shader.TileMode.MIRROR, Shader.TileMode.MIRROR)
        )
        renderingCanvas.drawPaint(frameBufferPaint)
        frameBuffer.endDrawing()

        frameBuffer.tryObtainingImage(
            { image ->
                snowConfig.shader.setInputBuffer(
                    "accumulatedSnow",
                    BitmapShader(image, Shader.TileMode.MIRROR, Shader.TileMode.MIRROR)
                )
            },
            mainExecutor
        )
    }

    private companion object {
        private const val MILLIS_TO_SECONDS = 1 / 1000f
    }
}
