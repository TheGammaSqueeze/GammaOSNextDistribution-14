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

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Switch
import android.widget.TextView
import com.android.internal.widget.RecyclerView
import com.android.wallpaper.R
import com.android.wallpaper.model.WallpaperAction

/**
 * This class adapts the [WallpaperActionToggle] model to the WallpaperActionSelectionBottomSheet's
 * recycler view.
 */
class WallpaperActionsToggleAdapter(
    private val actionToggles: List<WallpaperAction>,
    private val clearToggle: Uri,
    private val wallpaperEffectSwitchListener: WallpaperEffectSwitchListener
) : RecyclerView.Adapter<WallpaperActionsToggleAdapter.ViewHolder>() {

    class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
        val textView: TextView
        val switchView: Switch
        init {
            textView = v.requireViewById(R.id.wallpaper_action_switch_label)
            switchView = v.requireViewById(R.id.wallpaper_action_switch)
        }
    }

    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
        val v =
            LayoutInflater.from(viewGroup.context)
                .inflate(R.layout.wallpaper_action_toggle, viewGroup, false)

        return ViewHolder(v)
    }

    // Replace the contents of a view (invoked by the layout manager).
    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
        viewHolder.textView.text = actionToggles[position].label
        viewHolder.switchView.setOnCheckedChangeListener(null)
        viewHolder.switchView.isChecked = actionToggles[position].toggled

        viewHolder.switchView.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                // Notify all observers that a switch has been flipped
                wallpaperEffectSwitchListener.onEffectSwitchChanged(position)
            } else {
                // Initiate query to disable all effects on this wallpaper
                wallpaperEffectSwitchListener.onEffectSwitchChanged(-1)
            }
            notifyDataSetChanged()
        }
    }

    override fun getItemCount() = actionToggles.size

    companion object {
        private val TAG = "WallpaperActionsToggleAdapter"
    }

    interface WallpaperEffectSwitchListener {
        /**
         * This is called when a wallpaper effect toggle switch has flipped
         *
         * @param checkedItem is the index of the item that is checked and -1 if nothing is checked
         */
        fun onEffectSwitchChanged(checkedItem: Int)
    }
}
