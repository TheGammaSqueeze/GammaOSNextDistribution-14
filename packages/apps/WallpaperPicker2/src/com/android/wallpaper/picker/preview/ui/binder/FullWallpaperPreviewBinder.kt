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

import android.content.Context
import android.graphics.Rect
import android.view.LayoutInflater
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.widget.ImageView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.android.wallpaper.R
import com.android.wallpaper.picker.TouchForwardingLayout
import com.android.wallpaper.picker.data.WallpaperModel
import com.android.wallpaper.picker.di.modules.MainDispatcher
import com.android.wallpaper.picker.preview.shared.model.CropSizeModel
import com.android.wallpaper.picker.preview.shared.model.FullPreviewCropModel
import com.android.wallpaper.picker.preview.ui.util.SubsamplingScaleImageViewUtil.setOnNewCropListener
import com.android.wallpaper.picker.preview.ui.util.SurfaceViewUtil
import com.android.wallpaper.picker.preview.ui.util.SurfaceViewUtil.attachView
import com.android.wallpaper.picker.preview.ui.view.FullPreviewFrameLayout
import com.android.wallpaper.picker.preview.ui.viewmodel.WallpaperPreviewViewModel
import com.android.wallpaper.util.DisplayUtils
import com.android.wallpaper.util.WallpaperCropUtils
import com.android.wallpaper.util.wallpaperconnection.WallpaperConnectionUtils
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import java.lang.Integer.min
import kotlin.math.max
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Binds wallpaper preview surface view and its view models. */
object FullWallpaperPreviewBinder {

    fun bind(
        applicationContext: Context,
        view: View,
        viewModel: WallpaperPreviewViewModel,
        displayUtils: DisplayUtils,
        lifecycleOwner: LifecycleOwner,
        @MainDispatcher mainScope: CoroutineScope,
    ) {
        val wallpaperPreviewCrop: FullPreviewFrameLayout =
            view.requireViewById(R.id.wallpaper_preview_crop)
        lifecycleOwner.lifecycleScope.launch {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.fullWallpaper.collect { (_, config, _) ->
                    wallpaperPreviewCrop.setCurrentAndTargetDisplaySize(
                        displayUtils.getRealSize(checkNotNull(view.context.display)),
                        config.displaySize,
                    )
                }
            }
        }
        val surfaceView: SurfaceView = view.requireViewById(R.id.wallpaper_surface)
        val surfaceTouchForwardingLayout: TouchForwardingLayout =
            view.requireViewById(R.id.touch_forwarding_layout)
        var job: Job? = null
        surfaceView.setZOrderMediaOverlay(true)
        surfaceView.holder.addCallback(
            object : SurfaceViewUtil.SurfaceCallback {
                override fun surfaceCreated(holder: SurfaceHolder) {
                    val surfaceSize = holder.surface.defaultSize
                    val cropSurfaceSize =
                        WallpaperCropUtils.calculateCropSurfaceSize(
                            view.resources,
                            max(surfaceSize.x, surfaceSize.y),
                            min(surfaceSize.x, surfaceSize.y),
                            surfaceSize.x,
                            surfaceSize.y
                        )
                    job =
                        lifecycleOwner.lifecycleScope.launch {
                            viewModel.fullWallpaper.collect {
                                (wallpaper, config, allowUserCropping, whichPreview) ->
                                if (wallpaper is WallpaperModel.LiveWallpaperModel) {
                                    WallpaperConnectionUtils.connect(
                                        applicationContext,
                                        mainScope,
                                        wallpaper,
                                        whichPreview,
                                        config.screen.toFlag(),
                                        surfaceView,
                                    )
                                } else if (wallpaper is WallpaperModel.StaticWallpaperModel) {
                                    val (lowResImageView, fullResImageView) =
                                        initStaticPreviewSurface(
                                            applicationContext,
                                            surfaceView,
                                        ) { crop, zoom ->
                                            viewModel.staticWallpaperPreviewViewModel
                                                .fullPreviewCropModels[config.displaySize] =
                                                FullPreviewCropModel(
                                                    cropHint = crop,
                                                    cropSizeModel =
                                                        CropSizeModel(
                                                            wallpaperZoom = zoom,
                                                            hostViewSize = surfaceSize,
                                                            cropSurfaceSize = cropSurfaceSize,
                                                        ),
                                                )
                                        }

                                    // We do not allow users to pinch to crop if it is a
                                    // downloadable wallpaper.
                                    if (allowUserCropping) {
                                        surfaceTouchForwardingLayout.initTouchForwarding(
                                            fullResImageView
                                        )
                                    }

                                    // Bind static wallpaper
                                    StaticWallpaperPreviewBinder.bind(
                                        lowResImageView,
                                        fullResImageView,
                                        viewModel.staticWallpaperPreviewViewModel,
                                        config.displaySize,
                                        lifecycleOwner,
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

    private fun initStaticPreviewSurface(
        applicationContext: Context,
        surfaceView: SurfaceView,
        onNewCrop: (crop: Rect, zoom: Float) -> Unit
    ): Pair<ImageView, SubsamplingScaleImageView> {
        val preview =
            LayoutInflater.from(applicationContext)
                .inflate(R.layout.fullscreen_wallpaper_preview, null)
        surfaceView.attachView(preview)
        val fullResImageView =
            preview.requireViewById<SubsamplingScaleImageView>(R.id.full_res_image)
        fullResImageView.setOnNewCropListener { crop, zoom -> onNewCrop.invoke(crop, zoom) }
        return Pair(preview.requireViewById(R.id.low_res_image), fullResImageView)
    }

    private fun TouchForwardingLayout.initTouchForwarding(targetView: View) {
        setForwardingEnabled(true)
        setTargetView(targetView)
    }
}
