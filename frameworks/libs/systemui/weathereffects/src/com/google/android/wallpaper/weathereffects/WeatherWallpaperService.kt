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
import android.view.SurfaceHolder
import com.google.android.torus.core.engine.TorusEngine
import com.google.android.torus.core.wallpaper.LiveWallpaper
import com.google.android.wallpaper.weathereffects.dagger.MainScope
import com.google.android.wallpaper.weathereffects.domain.WeatherEffectsInteractor
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope

class WeatherWallpaperService @Inject constructor(): LiveWallpaper() {

    @Inject lateinit var interactor: WeatherEffectsInteractor
    @Inject @MainScope lateinit var applicationScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        WallpaperEffectsDebugApplication.graph.inject(this)
    }

    override fun getWallpaperEngine(context: Context, surfaceHolder: SurfaceHolder): TorusEngine {
        val engine = WeatherEngine(surfaceHolder, context)
        engine.initialize(applicationScope, interactor)
        return engine
    }
}
