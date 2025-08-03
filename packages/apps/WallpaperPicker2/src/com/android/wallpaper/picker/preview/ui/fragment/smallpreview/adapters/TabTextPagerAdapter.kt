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
package com.android.wallpaper.picker.preview.ui.fragment.smallpreview.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.viewpager.widget.PagerAdapter
import androidx.viewpager.widget.ViewPager
import com.android.wallpaper.R

/** This class provides the tab views for the tabs in small preview page */
class TabTextPagerAdapter : PagerAdapter() {

    // TODO: move to view model when ready
    private val textPages = listOf(R.string.lock_screen_message, R.string.home_screen_message)

    override fun getCount(): Int {
        return textPages.size
    }

    override fun isViewFromObject(view: View, `object`: Any): Boolean {
        return view == `object`
    }

    override fun instantiateItem(container: ViewGroup, position: Int): Any {
        val itemView =
            LayoutInflater.from(container.context).inflate(R.layout.item_text, container, false)

        val tabText = container.resources.getString(textPages[position])

        val textView = itemView.requireViewById<TextView>(R.id.preview_tab_text)
        textView.text = tabText

        val textViewDisabled =
            itemView.requireViewById<TextView>(R.id.preview_tab_text_overlay_disabled)
        textViewDisabled.text = tabText

        itemView.setOnClickListener { view ->
            (container as ViewPager).setCurrentItem(position, true)
        }

        container.addView(itemView)
        return itemView
    }

    override fun destroyItem(container: ViewGroup, position: Int, `object`: Any) {
        container.removeView(`object` as View)
    }
}
