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
package com.android.wallpaper.picker.preview.ui.binder

import android.app.WallpaperColors
import android.content.Context
import android.graphics.Point
import android.view.LayoutInflater
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.android.wallpaper.R
import com.android.wallpaper.module.CustomizationSections.Screen
import com.android.wallpaper.picker.customization.shared.model.WallpaperColorsModel
import com.android.wallpaper.picker.data.WallpaperModel
import com.android.wallpaper.picker.di.modules.MainDispatcher
import com.android.wallpaper.picker.preview.ui.util.SurfaceViewUtil
import com.android.wallpaper.picker.preview.ui.util.SurfaceViewUtil.attachView
import com.android.wallpaper.picker.preview.ui.viewmodel.WallpaperPreviewViewModel
import com.android.wallpaper.util.wallpaperconnection.WallpaperConnectionUtils
import com.android.wallpaper.util.wallpaperconnection.WallpaperEngineConnection.WallpaperEngineConnectionListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Bind the [SurfaceView] with [WallpaperPreviewViewModel] for rendering static or live wallpaper
 * preview, with regard to its underlying [WallpaperModel].
 */
object SmallWallpaperPreviewBinder {
    /**
     * @param onFullResImageViewCreated This callback is only used when the wallpaperModel is a
     *   [WallpaperModel.StaticWallpaperModel]. [FullWallpaperPreviewBinder] needs the callback to
     *   further delegate the touch events and set the state change listener.
     */
    fun bind(
        surface: SurfaceView,
        viewModel: WallpaperPreviewViewModel,
        screen: Screen,
        displaySize: Point,
        applicationContext: Context,
        @MainDispatcher mainScope: CoroutineScope,
        viewLifecycleOwner: LifecycleOwner,
    ) {
        var job: Job? = null
        surface.setZOrderMediaOverlay(true)
        surface.holder.addCallback(
            object : SurfaceViewUtil.SurfaceCallback {
                override fun surfaceCreated(holder: SurfaceHolder) {
                    job =
                        viewLifecycleOwner.lifecycleScope.launch {
                            viewModel.smallWallpaper.collect { (wallpaper, whichPreview) ->
                                if (wallpaper is WallpaperModel.LiveWallpaperModel) {
                                    WallpaperConnectionUtils.connect(
                                        applicationContext,
                                        mainScope,
                                        wallpaper,
                                        whichPreview,
                                        screen.toFlag(),
                                        surface,
                                        object : WallpaperEngineConnectionListener {
                                            override fun onWallpaperColorsChanged(
                                                colors: WallpaperColors?,
                                                displayId: Int
                                            ) {
                                                viewModel.setWallpaperConnectionColors(
                                                    WallpaperColorsModel.Loaded(colors)
                                                )
                                            }
                                        }
                                    )
                                } else if (wallpaper is WallpaperModel.StaticWallpaperModel) {
                                    val staticPreviewView =
                                        LayoutInflater.from(applicationContext)
                                            .inflate(R.layout.fullscreen_wallpaper_preview, null)
                                    surface.attachView(staticPreviewView)
                                    // Bind static wallpaper
                                    StaticWallpaperPreviewBinder.bind(
                                        lowResImageView =
                                            staticPreviewView.requireViewById(R.id.low_res_image),
                                        fullResImageView =
                                            staticPreviewView.requireViewById(R.id.full_res_image),
                                        viewModel = viewModel.staticWallpaperPreviewViewModel,
                                        displaySize = displaySize,
                                        viewLifecycleOwner = viewLifecycleOwner,
                                        shouldCalibrateWithSystemScale = true,
                                    )
                                    // This is to possibly shut down all live wallpaper services
                                    // if they exist; otherwise static wallpaper can not show up.
                                    WallpaperConnectionUtils.disconnectAllServices(
                                        applicationContext
                                    )
                                }
                            }
                        }
                }

                override fun surfaceDestroyed(holder: SurfaceHolder) {
                    job?.cancel()
                    // Note that we disconnect wallpaper connection for live wallpapers in
                    // WallpaperPreviewActivity's onDestroy().
                    // This is to reduce multiple times of connecting and disconnecting live
                    // wallpaper services, when going back and forth small and full preview.
                }
            }
        )
        // TODO (b/300979155): Clean up surface when no longer needed, e.g. onDestroyed
    }
}
