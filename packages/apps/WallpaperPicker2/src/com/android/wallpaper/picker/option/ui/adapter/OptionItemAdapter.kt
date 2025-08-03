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

package com.android.wallpaper.picker.option.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.LayoutRes
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.android.wallpaper.R
import com.android.wallpaper.picker.option.ui.binder.OptionItemBinder
import com.android.wallpaper.picker.option.ui.viewmodel.OptionItemViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.DisposableHandle
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Adapts between option items and their views. */
class OptionItemAdapter<T>(
    @LayoutRes private val layoutResourceId: Int,
    private val lifecycleOwner: LifecycleOwner,
    private val backgroundDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val foregroundTintSpec: OptionItemBinder.TintSpec? = null,
    private val bindIcon: (View, T) -> Unit,
) : RecyclerView.Adapter<OptionItemAdapter.ViewHolder>() {

    private val items = mutableListOf<OptionItemViewModel<T>>()

    fun setItems(items: List<OptionItemViewModel<T>>, callback: (() -> Unit)? = null) {
        lifecycleOwner.lifecycleScope.launch {
            val oldItems = this@OptionItemAdapter.items
            val newItems = items
            val diffResult =
                withContext(backgroundDispatcher) {
                    DiffUtil.calculateDiff(
                        object : DiffUtil.Callback() {
                            override fun getOldListSize(): Int {
                                return oldItems.size
                            }

                            override fun getNewListSize(): Int {
                                return newItems.size
                            }

                            override fun areItemsTheSame(
                                oldItemPosition: Int,
                                newItemPosition: Int
                            ): Boolean {
                                val oldItem = oldItems[oldItemPosition]
                                val newItem = newItems[newItemPosition]
                                return oldItem.key.value == newItem.key.value
                            }

                            override fun areContentsTheSame(
                                oldItemPosition: Int,
                                newItemPosition: Int
                            ): Boolean {
                                val oldItem = oldItems[oldItemPosition]
                                val newItem = newItems[newItemPosition]
                                return oldItem == newItem
                            }
                        },
                        /* detectMoves= */ false,
                    )
                }

            oldItems.clear()
            oldItems.addAll(items)
            diffResult.dispatchUpdatesTo(this@OptionItemAdapter)
            if (callback != null) {
                callback()
            }
        }
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var disposableHandle: DisposableHandle? = null
    }

    override fun getItemCount(): Int {
        return items.size
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            LayoutInflater.from(parent.context)
                .inflate(
                    layoutResourceId,
                    parent,
                    false,
                )
        )
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.disposableHandle?.dispose()
        val item = items[position]
        item.payload?.let {
            bindIcon(holder.itemView.requireViewById(R.id.foreground), item.payload)
        }
        holder.disposableHandle =
            OptionItemBinder.bind(
                view = holder.itemView,
                viewModel = item,
                lifecycleOwner = lifecycleOwner,
                foregroundTintSpec = foregroundTintSpec,
            )
    }
}
