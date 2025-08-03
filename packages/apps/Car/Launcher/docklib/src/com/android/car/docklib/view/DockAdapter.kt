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

package com.android.car.docklib.view

import android.car.media.CarMediaManager
import android.content.Context
import android.os.Build
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.android.car.docklib.DockInterface
import com.android.car.docklib.R
import com.android.car.docklib.data.DockAppItem

/** [RecyclerView.Adapter] used to bind Dock items */
class DockAdapter(private val dockController: DockInterface, private val userContext: Context) :
        ListAdapter<DockAppItem, DockItemViewHolder>(DIFF_CALLBACK) {
    companion object {
        private val DEBUG = Build.isDebuggable()
        private const val TAG = "DockAdapter"
    }

    private var carMediaManager: CarMediaManager? = null

    enum class PayloadType {
        CHANGE_ITEM_TYPE,
    }

    private val positionToCallbackMap = HashMap<Int, Runnable>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DockItemViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(
                R.layout.dock_app_item_view, // resource
                parent,
                false // attachToRoot
        )
        return DockItemViewHolder(dockController, view, userContext, carMediaManager)
    }

    override fun onBindViewHolder(
            viewHolder: DockItemViewHolder,
            position: Int,
            payloads: MutableList<Any>
    ) {
        if (payloads.isEmpty()) {
            return super.onBindViewHolder(viewHolder, position, payloads)
        }
        if (DEBUG) Log.d(TAG, "Binding at position $position with payloads")

        payloads.forEach { payload ->
            when (payload) {
                PayloadType.CHANGE_ITEM_TYPE -> {
                    if (DEBUG) Log.d(TAG, "Type changed for position $position")
                    viewHolder.itemTypeChanged(currentList[position])
                }
            }
        }
    }

    override fun onBindViewHolder(viewHolder: DockItemViewHolder, position: Int) {
        if (DEBUG) Log.d(TAG, "Binding at position $position without payloads")
        val cleanupCallback = positionToCallbackMap.getOrDefault(
                position,
                null // defaultValue
        )
        if (DEBUG) Log.d(TAG, "Is callback set for $position: ${cleanupCallback != null}")
        positionToCallbackMap.remove(position)
        viewHolder.bind(currentList[position], cleanupCallback)
    }

    /** Used to set a callback for the [position] to be passed to the ViewHolder on the next bind. */
    fun setCallback(position: Int, callback: Runnable?) {
        callback?.let { positionToCallbackMap[position] = it }
    }

    /**
     * Setter for carMediaManager
     */
    fun setCarMediaManager(carMediaManager: CarMediaManager) {
        this.carMediaManager = carMediaManager
    }
}

private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<DockAppItem>() {
    override fun areItemsTheSame(p0: DockAppItem, p1: DockAppItem): Boolean {
        return p0.id == p1.id
    }

    override fun areContentsTheSame(p0: DockAppItem, p1: DockAppItem): Boolean {
        return p0 == p1
    }

    override fun getChangePayload(
            oldItem: DockAppItem,
            newItem: DockAppItem
    ): Any? {
        if (oldItem.type != newItem.type) {
            return DockAdapter.PayloadType.CHANGE_ITEM_TYPE
        }
        return super.getChangePayload(oldItem, newItem)
    }
}
