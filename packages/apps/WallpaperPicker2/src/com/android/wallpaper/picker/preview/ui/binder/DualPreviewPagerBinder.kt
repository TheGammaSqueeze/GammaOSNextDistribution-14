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
import android.view.View
import androidx.lifecycle.LifecycleOwner
import com.android.wallpaper.R
import com.android.wallpaper.model.wallpaper.FoldableDisplay
import com.android.wallpaper.model.wallpaper.PreviewPagerPage
import com.android.wallpaper.picker.preview.ui.fragment.smallpreview.DualPreviewViewPager
import com.android.wallpaper.picker.preview.ui.fragment.smallpreview.adapters.DualPreviewPagerAdapter
import com.android.wallpaper.picker.preview.ui.view.DualDisplayAspectRatioLayout
import com.android.wallpaper.picker.preview.ui.view.DualDisplayAspectRatioLayout.Companion.getViewId
import com.android.wallpaper.picker.preview.ui.viewmodel.WallpaperPreviewViewModel
import kotlinx.coroutines.CoroutineScope

/** Binds dual preview home screen and lock screen view pager. */
object DualPreviewPagerBinder {

    fun bind(
        dualPreviewView: DualPreviewViewPager,
        wallpaperPreviewViewModel: WallpaperPreviewViewModel,
        applicationContext: Context,
        viewLifecycleOwner: LifecycleOwner,
        mainScope: CoroutineScope,
        currentNavDestId: Int,
        navigate: (View) -> Unit,
    ) {
        // implement adapter for the dual preview pager
        dualPreviewView.adapter = DualPreviewPagerAdapter { view, position ->
            PreviewTooltipBinder.bind(
                tooltipStub = view.requireViewById(R.id.tooltip_stub),
                enableClickToDismiss = false,
                viewModel = wallpaperPreviewViewModel,
                lifecycleOwner = viewLifecycleOwner,
            )

            val dualDisplayAspectRatioLayout: DualDisplayAspectRatioLayout =
                view.requireViewById(R.id.dual_preview)

            val displaySizes =
                mapOf(
                    FoldableDisplay.FOLDED to wallpaperPreviewViewModel.smallerDisplaySize,
                    FoldableDisplay.UNFOLDED to wallpaperPreviewViewModel.wallpaperDisplaySize,
                )
            dualDisplayAspectRatioLayout.setDisplaySizes(displaySizes)
            dualPreviewView.setDisplaySizes(displaySizes)

            FoldableDisplay.entries.forEach { display ->
                val previewDisplaySize = dualDisplayAspectRatioLayout.getPreviewDisplaySize(display)
                previewDisplaySize?.let {
                    SmallPreviewBinder.bind(
                        applicationContext = applicationContext,
                        view = dualDisplayAspectRatioLayout.requireViewById(display.getViewId()),
                        viewModel = wallpaperPreviewViewModel,
                        mainScope = mainScope,
                        viewLifecycleOwner = viewLifecycleOwner,
                        screen = PreviewPagerPage.entries[position].screen,
                        displaySize = it,
                        foldableDisplay = display,
                        currentNavDestId = currentNavDestId,
                        navigate = navigate,
                    )
                }
            }
        }
    }
}
