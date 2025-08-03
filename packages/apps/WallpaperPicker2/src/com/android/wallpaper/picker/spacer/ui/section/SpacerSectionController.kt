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

package com.android.wallpaper.picker.spacer.ui.section

import android.annotation.DimenRes
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout.LayoutParams
import com.android.wallpaper.R
import com.android.wallpaper.model.CustomizationSectionController
import com.android.wallpaper.picker.spacer.ui.view.SpacerSectionView

/** This class provides a vertical margin section */
class SpacerSectionController(private @DimenRes val heightResourceId: Int) :
    CustomizationSectionController<SpacerSectionView> {

    override fun isAvailable(context: Context): Boolean {
        return true
    }

    override fun createView(context: Context): SpacerSectionView {
        val view =
            LayoutInflater.from(context)
                .inflate(
                    R.layout.spacer_section_view,
                    null,
                ) as SpacerSectionView
        val spacer = View(context)
        val height = context.resources.getDimension(heightResourceId).toInt()
        val lp = LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, height)
        spacer.layoutParams = lp
        view.addView(spacer)
        return view
    }
}
