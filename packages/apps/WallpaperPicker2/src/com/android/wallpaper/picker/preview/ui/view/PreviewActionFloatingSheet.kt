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
package com.android.wallpaper.picker.preview.ui.view

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.view.isVisible
import com.android.wallpaper.R
import com.android.wallpaper.effects.EffectsController.EffectEnumInterface
import com.android.wallpaper.util.SizeCalculator
import com.android.wallpaper.widget.floatingsheetcontent.WallpaperEffectsView2
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetBehavior.BottomSheetCallback

/**
 * UI that hosts the content of the floating sheet dialog sliding from the bottom when a
 * correspondent preview action is toggled on.
 */
class PreviewActionFloatingSheet(context: Context, attrs: AttributeSet?) :
    FrameLayout(context, attrs) {

    private val floatingSheetView: ViewGroup
    private val floatingSheetContainer: ViewGroup
    private val floatingSheetBehavior: BottomSheetBehavior<ViewGroup>

    init {
        LayoutInflater.from(context).inflate(R.layout.floating_sheet2, this, true)
        floatingSheetView = requireViewById(R.id.floating_sheet_content)
        SizeCalculator.adjustBackgroundCornerRadius(floatingSheetView)
        floatingSheetContainer = requireViewById(R.id.floating_sheet_container)
        floatingSheetBehavior = BottomSheetBehavior.from(floatingSheetContainer)
        floatingSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
    }

    fun setEffectContent(
        effect: EffectEnumInterface,
        myPhotosClickListener: OnClickListener,
        collapseFloatingSheetListener: OnClickListener,
        effectSwitchListener: WallpaperEffectsView2.EffectSwitchListener,
        effectDownloadClickListener: WallpaperEffectsView2.EffectDownloadClickListener,
        status: WallpaperEffectsView2.Status,
        resultCode: Int?,
        errorMessage: String?,
        title: String,
        effectTextRes: WallpaperEffectsView2.EffectTextRes,
    ) {
        val view =
            LayoutInflater.from(context).inflate(R.layout.wallpaper_effects_view2, this, false)
                as WallpaperEffectsView2
        view.setEffectResources(effectTextRes)
        view.setMyPhotosClickListener(myPhotosClickListener)
        view.setCollapseFloatingSheetListener(collapseFloatingSheetListener)
        view.addEffectSwitchListener(effectSwitchListener)
        view.setEffectDownloadClickListener(effectDownloadClickListener)
        view.updateEffectStatus(
            effect,
            status,
            resultCode,
            errorMessage,
        )
        view.updateEffectTitle(title)
        floatingSheetView.removeAllViews()
        floatingSheetView.addView(view)
    }

    fun setInformationContent(
        attributions: List<String?>?,
        onExploreButtonClicked: (() -> Unit)?,
    ) {
        val view = LayoutInflater.from(context).inflate(R.layout.wallpaper_info_view2, this, false)
        val title: TextView = view.requireViewById(R.id.wallpaper_info_title)
        val subtitle1: TextView = view.requireViewById(R.id.wallpaper_info_subtitle1)
        val subtitle2: TextView = view.requireViewById(R.id.wallpaper_info_subtitle2)
        val exploreButton: Button = view.requireViewById(R.id.wallpaper_info_explore_button)
        attributions?.forEachIndexed { index, text ->
            when (index) {
                0 -> {
                    if (!text.isNullOrEmpty()) {
                        title.text = text
                        title.isVisible = true
                    }
                }
                1 -> {
                    if (!text.isNullOrEmpty()) {
                        subtitle1.text = text
                        subtitle1.isVisible = true
                    }
                }
                2 -> {
                    if (!text.isNullOrEmpty()) {
                        subtitle2.text = text
                        subtitle2.isVisible = true
                    }
                }
            }
            if (onExploreButtonClicked != null) {
                exploreButton.isVisible = true
                exploreButton.setOnClickListener { onExploreButtonClicked.invoke() }
            }
        }
        floatingSheetView.removeAllViews()
        floatingSheetView.addView(view)
    }

    fun expand() {
        floatingSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
    }

    fun collapse() {
        floatingSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
    }

    /**
     * Adds Floating Sheet Callback to connected [BottomSheetBehavior].
     *
     * @param callback the callback for floating sheet state changes, has to be in the type of
     *   [BottomSheetBehavior.BottomSheetCallback] since the floating sheet behavior is currently
     *   based on [BottomSheetBehavior]
     */
    fun addFloatingSheetCallback(callback: BottomSheetCallback) {
        floatingSheetBehavior.addBottomSheetCallback(callback)
    }

    fun removeFloatingSheetCallback(callback: BottomSheetCallback) {
        floatingSheetBehavior.removeBottomSheetCallback(callback)
    }
}
