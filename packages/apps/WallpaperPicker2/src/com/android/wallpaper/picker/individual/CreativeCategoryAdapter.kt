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

package com.android.wallpaper.picker.individual

import android.app.Activity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.android.wallpaper.R
import com.android.wallpaper.model.LiveWallpaperInfo
import com.android.wallpaper.model.WallpaperInfo

/**
 * CreativeCategoryAdapter subclass for a creative category wallpaper tile in the RecyclerView which
 * internally creates the CreativeCategoryIndividualHolder instance for the tile .
 */
class CreativeCategoryAdapter(
    var items: List<WallpaperInfo>,
    private val activity: Activity,
    private val tileSizePx: Int,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return createIndividualHolder(parent)
    }

    override fun getItemCount(): Int {
        return items.size
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val wallpaperInfo = items[position] as LiveWallpaperInfo
        wallpaperInfo.setVisibleTitle(true)
        (holder as CreativeCategoryIndividualHolder?)?.bindWallpaper(wallpaperInfo)
    }

    private fun createIndividualHolder(parent: ViewGroup): RecyclerView.ViewHolder {
        val layoutInflater = LayoutInflater.from(activity)
        val view: View = layoutInflater.inflate(R.layout.labeled_grid_item_image, parent, false)

        return CreativeCategoryIndividualHolder(activity, tileSizePx, view)
    }
}
