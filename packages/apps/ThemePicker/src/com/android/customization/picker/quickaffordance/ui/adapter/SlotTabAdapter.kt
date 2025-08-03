/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.customization.picker.quickaffordance.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.android.customization.picker.quickaffordance.ui.viewmodel.KeyguardQuickAffordanceSlotViewModel
import com.android.themepicker.R

/** Adapts between lock screen quick affordance slot items and views. */
class SlotTabAdapter : RecyclerView.Adapter<SlotTabAdapter.ViewHolder>() {

    private val items = mutableListOf<KeyguardQuickAffordanceSlotViewModel>()

    fun setItems(items: List<KeyguardQuickAffordanceSlotViewModel>) {
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
            if (item.onClicked != null) {
                View.OnClickListener { item.onClicked.invoke() }
            } else {
                null
            }
        )
        val stateDescription =
            item.selectedQuickAffordances
                .find { it.isSelected.value }
                ?.text
                ?.asString(holder.itemView.context)
        holder.itemView.stateDescription =
            stateDescription
                ?: holder.itemView.resources.getString(R.string.keyguard_affordance_none)
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val textView: TextView = itemView.requireViewById(R.id.text)
    }
}
