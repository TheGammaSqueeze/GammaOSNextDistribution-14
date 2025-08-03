/*
 * Copyright 2023 The Android Open Source Project
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

import android.app.Activity
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.android.wallpaper.R
import com.android.wallpaper.model.WallpaperInfo
import com.android.wallpaper.picker.individual.CreativeCategoryAdapter
import com.android.wallpaper.picker.individual.MarginItemDecoration

/**
 * CreativeCategoryHolder subclass for a creative category wallpaper tile in the individual
 * wallpaper picker grid. This helps us create a different view for the creative category tiles in
 * the individual picker.
 */
class CreativeCategoryHolder(private val mActivity: Activity, itemView: View) :
    RecyclerView.ViewHolder(itemView) {
    private var recyclerViewCreativeCategory: RecyclerView
    private var adapter: CreativeCategoryAdapter? = null

    init {
        recyclerViewCreativeCategory = itemView.requireViewById(R.id.recyclerview_container)
        recyclerViewCreativeCategory.addItemDecoration(
            MarginItemDecoration(
                mActivity.resources.getInteger(R.integer.creative_category_individual_item_padding)
            )
        )
    }

    fun bind(items: List<WallpaperInfo>, height: Int) {
        if (adapter == null) {
            adapter = CreativeCategoryAdapter(items, mActivity, height)
            recyclerViewCreativeCategory.adapter = adapter
        } else {
            adapter?.items = items
            adapter?.notifyDataSetChanged()
        }
    }
}
