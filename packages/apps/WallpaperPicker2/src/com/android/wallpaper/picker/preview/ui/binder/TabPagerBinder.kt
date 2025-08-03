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

import android.view.View.OVER_SCROLL_NEVER
import androidx.viewpager.widget.ViewPager
import com.android.wallpaper.picker.preview.ui.fragment.smallpreview.adapters.TabTextPagerAdapter
import com.android.wallpaper.picker.preview.ui.fragment.smallpreview.pagetransformers.PreviewSelectedTabTransformer

/** Binds single preview home screen and lock screen tabs. */
object TabPagerBinder {

    fun bind(
        tabsViewPager: ViewPager,
    ) {
        tabsViewPager.apply {
            adapter = TabTextPagerAdapter()
            offscreenPageLimit = 2
            clipChildren = false
            clipToPadding = false
            overScrollMode = OVER_SCROLL_NEVER
            setPageTransformer(false, PreviewSelectedTabTransformer())
        }
    }
}
