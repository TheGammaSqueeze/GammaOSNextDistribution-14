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
package com.android.wallpaper.widget.floatingsheetcontent

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout
import android.widget.TextView
import com.android.internal.widget.LinearLayoutManager
import com.android.internal.widget.RecyclerView
import com.android.wallpaper.R

/** This class is the view which displays the bottom sheet for wallpaper effects */
class WallpaperActionSelectionBottomSheet(context: Context?, attrs: AttributeSet?) :
    LinearLayout(context, attrs) {
    private lateinit var wallpaperActionsTitle: TextView
    private lateinit var wallpaperActionsSubtitle: TextView
    private lateinit var wallpaperActionsToggles: RecyclerView

    override fun onFinishInflate() {
        super.onFinishInflate()
        wallpaperActionsTitle = requireViewById(R.id.wallpaper_effects_title)
        wallpaperActionsSubtitle = requireViewById(R.id.wallpaper_effects_subtitle)
        wallpaperActionsToggles = requireViewById(R.id.wallpaper_action_toggles)
    }

    // This function takes in a WallpaperToggleSwitchAdapter and completes the initialization of the
    // recycler view to present the list of toggle options.
    fun setUpActionToggleOptions(adapter: WallpaperActionsToggleAdapter) {
        // Set CustomAdapter as the adapter for RecyclerView.
        wallpaperActionsToggles.setAdapter(adapter)
        wallpaperActionsToggles.setLayoutManager(
            LinearLayoutManager(context, LinearLayoutManager.VERTICAL, /* reverseLayout= */ false)
        )
    }

    fun setBottomSheetTitle(title: String) {
        wallpaperActionsTitle.text = title
    }

    fun setBottomSheetSubtitle(subtitle: String) {
        wallpaperActionsSubtitle.text = subtitle
    }
}
