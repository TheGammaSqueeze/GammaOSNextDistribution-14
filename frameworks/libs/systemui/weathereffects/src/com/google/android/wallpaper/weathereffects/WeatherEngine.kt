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

package com.google.android.wallpaper.weathereffects

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import android.util.Size
import android.util.SizeF
import android.view.SurfaceHolder
import com.google.android.torus.canvas.engine.CanvasWallpaperEngine
import com.google.android.wallpaper.weathereffects.shared.model.WallpaperImageModel
import com.google.android.wallpaper.weathereffects.domain.WeatherEffectsInteractor
import com.google.android.wallpaper.weathereffects.graphics.WeatherEffect
import com.google.android.wallpaper.weathereffects.graphics.fog.FogEffect
import com.google.android.wallpaper.weathereffects.graphics.fog.FogEffectConfig
import com.google.android.wallpaper.weathereffects.graphics.none.NoEffect
import com.google.android.wallpaper.weathereffects.graphics.rain.RainEffect
import com.google.android.wallpaper.weathereffects.graphics.rain.RainEffectConfig
import com.google.android.wallpaper.weathereffects.graphics.snow.SnowEffect
import com.google.android.wallpaper.weathereffects.graphics.snow.SnowEffectConfig
import com.google.android.wallpaper.weathereffects.provider.WallpaperInfoContract
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class WeatherEngine(
    defaultHolder: SurfaceHolder,
    private val context: Context,
    hardwareAccelerated: Boolean = true
) : CanvasWallpaperEngine(defaultHolder, hardwareAccelerated) {

    private val currentAssets: WallpaperImageModel? = null
    private var activeEffect: WeatherEffect? = null
        private set(value) {
            field = value
            if (shouldTriggerUpdate()) {
                startUpdateLoop()
            } else {
                stopUpdateLoop()
            }
        }

    private var collectWallpaperImageJob: Job? = null
    private lateinit var interactor: WeatherEffectsInteractor
    private lateinit var applicationScope: CoroutineScope

    override fun onCreate(isFirstActiveInstance: Boolean) {
        Log.d(TAG, "Engine created.")
    }

    override fun onResize(size: Size) {
        activeEffect?.resize(size.toSizeF())
        if (activeEffect is NoEffect) {
            render { canvas -> activeEffect!!.draw(canvas) }
        }
    }

    fun initialize(
        applicationScope: CoroutineScope,
        interactor: WeatherEffectsInteractor,
    ) {
        this.interactor = interactor
        this.applicationScope = applicationScope

        if (interactor.wallpaperImageModel.value == null) {
            applicationScope.launch {
                interactor.loadWallpaper()
            }
        }
    }

    override fun onResume() {
        if (shouldTriggerUpdate()) {
            startUpdateLoop()
        }
        collectWallpaperImageJob = applicationScope.launch {
            interactor.wallpaperImageModel.collect { asset ->
                if (asset == null || asset == currentAssets) return@collect

                createWeatherEffect(asset.foreground, asset.background, asset.weatherEffect)
            }
        }
    }

    override fun onPause() {
        stopUpdateLoop()
        activeEffect?.reset()
        collectWallpaperImageJob?.cancel()
    }


    override fun onDestroy(isLastActiveInstance: Boolean) {
        activeEffect?.release()
        activeEffect = null
    }

    override fun onUpdate(deltaMillis: Long, frameTimeNanos: Long) {
        super.onUpdate(deltaMillis, frameTimeNanos)
        activeEffect?.update(deltaMillis, frameTimeNanos)

        renderWithFpsLimit(frameTimeNanos) { canvas -> activeEffect?.draw(canvas) }
    }

    private fun createWeatherEffect(
        foreground: Bitmap,
        background: Bitmap,
        weatherEffect: WallpaperInfoContract.WeatherEffect? = null
    ) {
        activeEffect?.release()
        activeEffect = null

        when (weatherEffect) {
            WallpaperInfoContract.WeatherEffect.RAIN -> {
                val rainConfig = RainEffectConfig(context, foreground, background)
                activeEffect = RainEffect(rainConfig, screenSize.toSizeF())
            }

            WallpaperInfoContract.WeatherEffect.FOG -> {
                val fogConfig = FogEffectConfig(
                    context.assets, foreground, background, context.resources.displayMetrics.density
                )
                activeEffect = FogEffect(fogConfig, screenSize.toSizeF())
            }

            WallpaperInfoContract.WeatherEffect.SNOW -> {
                val snowConfig = SnowEffectConfig(context, foreground, background)
                activeEffect = SnowEffect(snowConfig, screenSize.toSizeF(), context.mainExecutor)
            }

            else -> {
                activeEffect = NoEffect(foreground, background, screenSize.toSizeF())
            }
        }

        render { canvas -> activeEffect?.draw(canvas) }
    }

    private fun shouldTriggerUpdate(): Boolean {
        return activeEffect != null && activeEffect !is NoEffect
    }

    private fun Size.toSizeF(): SizeF = SizeF(width.toFloat(), height.toFloat())

    private companion object {

        private val TAG = WeatherEngine::class.java.simpleName
    }
}
