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
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager.widget.PagerAdapter
import com.android.wallpaper.R

/** This class provides the dual preview views for the small preview screen on foldable devices */
class DualPreviewPagerAdapter(
    val onBindViewHolder: (View, Int) -> Unit,
) : PagerAdapter() {

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)

    override fun isViewFromObject(item: View, `object`: Any): Boolean {
        return item == `object`
    }

    override fun instantiateItem(container: ViewGroup, position: Int): Any {
        val view =
            LayoutInflater.from(container.context)
                .inflate(R.layout.small_preview_foldable_card_view, container, false)

        onBindViewHolder.invoke(view, position)
        container.addView(view)
        return view
    }

    override fun destroyItem(container: ViewGroup, position: Int, `object`: Any) {
        container.removeView(`object` as View)
    }

    override fun getCount(): Int {
        return PREVIEW_PAGER_ITEM_COUNT
    }

    companion object {
        const val PREVIEW_PAGER_ITEM_COUNT = 2
    }
}
