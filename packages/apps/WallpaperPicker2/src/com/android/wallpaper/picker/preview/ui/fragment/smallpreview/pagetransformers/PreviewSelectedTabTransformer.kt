/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.wallpaper.picker.preview.ui.fragment.smallpreview.pagetransformers

import android.view.View
import android.widget.TextView
import androidx.viewpager.widget.ViewPager
import com.android.wallpaper.R
import kotlin.math.abs

/**
 * Selector api for view pager pages is not correctly setting the state for a page. As a workaround
 * this transformer manually controls the enable/disable state of the preview tab buttons
 */
class PreviewSelectedTabTransformer : ViewPager.PageTransformer {
    override fun transformPage(page: View, position: Float) {
        val textView = page.requireViewById<TextView>(R.id.preview_tab_text)
        val textViewDisabled =
            page.requireViewById<TextView>(R.id.preview_tab_text_overlay_disabled)

        textViewDisabled.alpha = abs(position)
        textView.alpha = 1 - abs(position)
    }
}
