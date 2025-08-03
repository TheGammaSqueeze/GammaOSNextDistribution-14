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
import android.os.Bundle
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.core.os.bundleOf
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.android.wallpaper.picker.customization.shared.model.WallpaperColorsModel
import com.android.wallpaper.picker.preview.ui.util.SurfaceViewUtil
import com.android.wallpaper.picker.preview.ui.viewmodel.WallpaperPreviewViewModel
import com.android.wallpaper.picker.preview.ui.viewmodel.WorkspacePreviewConfigViewModel
import com.android.wallpaper.util.PreviewUtils
import com.android.wallpaper.util.SurfaceViewUtils
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

object WorkspacePreviewBinder {
    fun bind(
        surface: SurfaceView,
        config: WorkspacePreviewConfigViewModel,
        viewModel: WallpaperPreviewViewModel,
        lifecycleOwner: LifecycleOwner,
    ) {
        surface.setZOrderMediaOverlay(true)
        surface.holder.addCallback(
            object : SurfaceViewUtil.SurfaceCallback {
                override fun surfaceCreated(holder: SurfaceHolder) {
                    lifecycleOwner.lifecycleScope.launch {
                        viewModel.wallpaperColorsModel.collect {
                            if (it is WallpaperColorsModel.Loaded) {
                                renderWorkspacePreview(
                                    surface = surface,
                                    previewUtils = config.previewUtils,
                                    displayId = config.displayId,
                                    wallpaperColors = it.colors
                                )
                            }
                        }
                    }
                }
            }
        )
    }

    /**
     * Binds the workspace preview in the full screen, where we need to listen to the changes of the
     * [WorkspacePreviewConfigViewModel] according to which small preview the user clicks on.
     */
    fun bindFullWorkspacePreview(
        surface: SurfaceView,
        viewModel: WallpaperPreviewViewModel,
        lifecycleOwner: LifecycleOwner,
    ) {
        surface.setZOrderMediaOverlay(true)
        surface.holder.addCallback(
            object : SurfaceViewUtil.SurfaceCallback {
                override fun surfaceCreated(holder: SurfaceHolder) {
                    lifecycleOwner.lifecycleScope.launch {
                        combine(
                                viewModel.fullWorkspacePreviewConfigViewModel,
                                viewModel.wallpaperColorsModel
                            ) { config, colorsModel ->
                                config to colorsModel
                            }
                            .collect { (config, colorsModel) ->
                                if (colorsModel is WallpaperColorsModel.Loaded) {
                                    renderWorkspacePreview(
                                        surface = surface,
                                        previewUtils = config.previewUtils,
                                        displayId = config.displayId,
                                        wallpaperColors = colorsModel.colors
                                    )
                                }
                            }
                    }
                }
            }
        )
    }

    private fun renderWorkspacePreview(
        surface: SurfaceView,
        previewUtils: PreviewUtils,
        displayId: Int,
        wallpaperColors: WallpaperColors? = null,
    ) {
        if (previewUtils.supportsPreview()) {
            val extras = bundleOf(Pair(SurfaceViewUtils.KEY_DISPLAY_ID, displayId))
            wallpaperColors?.let {
                extras.putParcelable(SurfaceViewUtils.KEY_WALLPAPER_COLORS, wallpaperColors)
            }
            val request =
                SurfaceViewUtils.createSurfaceViewRequest(
                    surface,
                    extras,
                )
            previewUtils.renderPreview(
                request,
                object : PreviewUtils.WorkspacePreviewCallback {
                    override fun onPreviewRendered(resultBundle: Bundle?) {
                        if (resultBundle != null) {
                            SurfaceViewUtils.getSurfacePackage(resultBundle).apply {
                                if (this != null) {
                                    surface.setChildSurfacePackage(this)
                                } else {
                                    Log.w(
                                        TAG,
                                        "Result bundle from rendering preview does not contain " +
                                            "a child surface package."
                                    )
                                }
                            }
                        } else {
                            Log.w(TAG, "Result bundle from rendering preview is null.")
                        }
                    }
                }
            )
        }
    }

    const val TAG = "WorkspacePreviewBinder"
}
