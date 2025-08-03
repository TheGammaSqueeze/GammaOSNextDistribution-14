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

package com.android.customization.picker.color.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.android.customization.picker.color.ui.viewmodel.ColorTypeTabViewModel
import com.android.themepicker.R

/** Adapts between color type items and views. */
class ColorTypeTabAdapter : RecyclerView.Adapter<ColorTypeTabAdapter.ViewHolder>() {

    private val items = mutableListOf<ColorTypeTabViewModel>()

    fun setItems(items: List<ColorTypeTabViewModel>) {
        this.items.clear()
        this.items.addAll(items)
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int {
        return items.size
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            LayoutInflater.from(parent.context)
                .inflate(
                    R.layout.picker_fragment_tab,
                    parent,
                    false,
                )
        )
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.itemView.isSelected = item.isSelected
        holder.textView.text = item.name
        holder.itemView.setOnClickListener(
            if (item.onClick != null) {
                View.OnClickListener { item.onClick.invoke() }
            } else {
                null
            }
        )
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val textView: TextView = itemView.requireViewById(R.id.text)
    }
}
