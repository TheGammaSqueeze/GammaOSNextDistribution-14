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
 *
 */

package com.android.wallpaper.picker.customization.ui.binder

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.DimenRes
import androidx.core.view.doOnLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.android.wallpaper.R
import com.android.wallpaper.picker.common.text.ui.viewbinder.TextViewBinder
import com.android.wallpaper.picker.customization.ui.viewmodel.WallpaperQuickSwitchViewModel
import kotlinx.coroutines.launch

/** Binds between the view and view-model for the wallpaper quick switch section. */
object WallpaperQuickSwitchSectionBinder {
    fun bind(
        view: View,
        viewModel: WallpaperQuickSwitchViewModel,
        lifecycleOwner: LifecycleOwner,
        isThumbnailFadeAnimationEnabled: Boolean,
        onNavigateToFullWallpaperSelector: () -> Unit,
    ) {
        val moreWallpapers: TextView = view.requireViewById(R.id.more_wallpapers)
        moreWallpapers.setOnClickListener { onNavigateToFullWallpaperSelector() }

        TextViewBinder.bind(moreWallpapers, viewModel.actionText)

        val optionContainer: ViewGroup = view.requireViewById(R.id.options)

        if (!viewModel.areRecentsAvailable) {
            optionContainer.visibility = View.GONE
        } else {
            optionContainer.visibility = View.VISIBLE
            // We have to wait for the container to be laid out before we can bind it because we
            // need
            // its size to calculate the sizes of the option items.
            optionContainer.doOnLayout {
                lifecycleOwner.lifecycleScope.launch {
                    lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                        launch {
                            bindOptions(
                                parent = optionContainer,
                                viewModel = viewModel,
                                lifecycleOwner = lifecycleOwner,
                                isThumbnailFadeAnimationEnabled = isThumbnailFadeAnimationEnabled,
                            )
                        }
                    }
                }
            }
        }
    }

    /** Binds the option items to the given parent. */
    private suspend fun bindOptions(
        parent: ViewGroup,
        viewModel: WallpaperQuickSwitchViewModel,
        lifecycleOwner: LifecycleOwner,
        isThumbnailFadeAnimationEnabled: Boolean,
    ) {
        viewModel.options.collect { options ->
            // Remove all views from a previous update.
            parent.removeAllViews()

            // Calculate the sizes that views should have.
            val (largeOptionWidth, smallOptionWidth) = calculateSizes(parent, options.size)

            val titleMap = mutableMapOf<String, Int>()

            // Create, add, and bind a view for each option.
            options.forEachIndexed { index, option ->
                val optionView =
                    createOptionView(
                        parent = parent,
                    )
                parent.addView(optionView)
                WallpaperQuickSwitchOptionBinder.bind(
                    view = optionView,
                    viewModel = option,
                    lifecycleOwner = lifecycleOwner,
                    smallOptionWidthPx = smallOptionWidth,
                    largeOptionWidthPx = largeOptionWidth,
                    isThumbnailFadeAnimationEnabled = isThumbnailFadeAnimationEnabled,
                    position = index,
                    titleMap = titleMap,
                )
            }
        }
    }

    /**
     * Returns a pair where the first value is the width that we should use for the large/selected
     * option and the second value is the width for the small/non-selected options.
     */
    private fun calculateSizes(
        parent: View,
        optionCount: Int,
    ): Pair<Int, Int> {
        // The large/selected option is a square. Its size should be equal to the height of its
        // container (with padding removed).
        val largeOptionWidth = parent.height - parent.paddingTop - parent.paddingBottom
        // We'll use the total (non-padded) width of the container to figure out the widths of the
        // small/non-selected options.
        val optionContainerWidthWithoutPadding =
            parent.width - parent.paddingStart - parent.paddingEnd
        // First, we will need the total of the widths of all the spacings between the options.
        val spacingWidth = parent.dimensionResource(R.dimen.spacing_8dp)
        val totalMarginWidths = (optionCount - 1) * spacingWidth

        val remainingSpaceForSmallOptions =
            optionContainerWidthWithoutPadding - largeOptionWidth - totalMarginWidths
        // One option is always large, the rest are small.
        val numberOfSmallOptions = optionCount - 1
        val smallOptionWidth =
            if (numberOfSmallOptions != 0) {
                (remainingSpaceForSmallOptions / numberOfSmallOptions).coerceAtMost(
                    parent.dimensionResource(R.dimen.wallpaper_quick_switch_max_option_width)
                )
            } else {
                0
            }

        return Pair(largeOptionWidth, smallOptionWidth)
    }

    /** Returns a new [View] for an option, without attaching it to the view-tree. */
    private fun createOptionView(
        parent: ViewGroup,
    ): View {
        return LayoutInflater.from(parent.context)
            .inflate(
                R.layout.wallpaper_quick_switch_option,
                parent,
                false,
            )
    }

    /** Compose-inspired convenience alias for getting a dimension in pixels. */
    private fun View.dimensionResource(
        @DimenRes res: Int,
    ): Int {
        return context.resources.getDimensionPixelSize(res)
    }
}
