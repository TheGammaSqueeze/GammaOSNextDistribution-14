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
import android.graphics.Point
import android.view.SurfaceView
import android.view.View
import androidx.cardview.widget.CardView
import androidx.lifecycle.LifecycleOwner
import com.android.wallpaper.R
import com.android.wallpaper.model.wallpaper.FoldableDisplay
import com.android.wallpaper.module.CustomizationSections.Screen
import com.android.wallpaper.picker.di.modules.MainDispatcher
import com.android.wallpaper.picker.preview.ui.viewmodel.WallpaperPreviewViewModel
import kotlinx.coroutines.CoroutineScope

object SmallPreviewBinder {
    /** @param foldableDisplay Only used for foldable devices; otherwise, set to null. */
    fun bind(
        applicationContext: Context,
        view: View,
        viewModel: WallpaperPreviewViewModel,
        screen: Screen,
        displaySize: Point,
        foldableDisplay: FoldableDisplay?,
        @MainDispatcher mainScope: CoroutineScope,
        viewLifecycleOwner: LifecycleOwner,
        currentNavDestId: Int,
        navigate: ((View) -> Unit)? = null,
    ) {
        val previewCard: CardView = view.requireViewById(R.id.preview_card)
        val wallpaperSurface: SurfaceView = view.requireViewById(R.id.wallpaper_surface)
        val workspaceSurface: SurfaceView = view.requireViewById(R.id.workspace_surface)

        if (R.id.smallPreviewFragment == currentNavDestId) {
            view.setOnClickListener {
                viewModel.onSmallPreviewClicked(screen, foldableDisplay)
                navigate?.invoke(previewCard)
            }
        } else if (R.id.setWallpaperDialog == currentNavDestId) {
            previewCard.radius =
                previewCard.resources.getDimension(
                    R.dimen.set_wallpaper_dialog_preview_corner_radius
                )
        }

        val config = viewModel.getWorkspacePreviewConfig(screen, foldableDisplay)
        WorkspacePreviewBinder.bind(
            workspaceSurface,
            config,
            viewModel,
            viewLifecycleOwner,
        )

        SmallWallpaperPreviewBinder.bind(
            surface = wallpaperSurface,
            viewModel = viewModel,
            screen = screen,
            displaySize = displaySize,
            applicationContext = applicationContext,
            mainScope = mainScope,
            viewLifecycleOwner = viewLifecycleOwner,
        )
    }
}
