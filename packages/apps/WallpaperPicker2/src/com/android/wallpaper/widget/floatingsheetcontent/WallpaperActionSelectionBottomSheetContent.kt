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
import android.net.Uri
import com.android.wallpaper.R
import com.android.wallpaper.model.WallpaperAction
import java.util.Arrays

/** This class provides the bottom sheet content for the effects/ sparkle button. */
class WallpaperActionSelectionBottomSheetContent(
    private val context: Context,
    private val sectionTitle: String?,
    private val sectionSubtitle: String?,
    private val clearActionsUri: Uri?,
    private val wallpaperActions: List<WallpaperAction>?,
    private var currentlyAppliedEffectId: String?,
    private var wallpaperEffectSwitchListener:
        WallpaperActionsToggleAdapter.WallpaperEffectSwitchListener
) : FloatingSheetContent<WallpaperActionSelectionBottomSheet>(context) {
    private lateinit var wallpaperActionSelectionBottomSheetView:
        WallpaperActionSelectionBottomSheet
    override val viewId: Int
        get() = R.layout.wallpaper_action_selection_bottom_sheet

    override fun onViewCreated(view: WallpaperActionSelectionBottomSheet) {
        wallpaperActionSelectionBottomSheetView = view
        if (
            wallpaperActions == null ||
                clearActionsUri == null ||
                sectionSubtitle == null ||
                sectionTitle == null
        ) {
            return
        }

        // Initialize the selected toggle.
        wallpaperActions.forEach {
            if (currentlyAppliedEffectId == it.effectId) {
                it.toggled = true
            }
        }
        wallpaperActionSelectionBottomSheetView.setUpActionToggleOptions(
            WallpaperActionsToggleAdapter(
                // TODO(b/270729418): enable multiple effect options once final design is
                //  agreed upon.
                // Forcing only one effect item for now
                Arrays.asList(wallpaperActions.get(0)),
                clearActionsUri,
                wallpaperEffectSwitchListener
            )
        )
        wallpaperActionSelectionBottomSheetView.setBottomSheetTitle(sectionTitle)
        wallpaperActionSelectionBottomSheetView.setBottomSheetSubtitle(sectionSubtitle)
    }

    fun setCurrentlyAppliedEffect(selectedCredential: String?) {
        currentlyAppliedEffectId = selectedCredential
    }
}
