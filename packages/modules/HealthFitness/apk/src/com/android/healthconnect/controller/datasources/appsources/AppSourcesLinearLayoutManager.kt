/**
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.android.healthconnect.controller.datasources.appsources

import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.healthconnect.controller.R

class AppSourcesLinearLayoutManager(context: Context?, private val adapter: AppSourcesAdapter) :
        LinearLayoutManager(context) {
    override fun onInitializeAccessibilityNodeInfoForItem(
            recycler: RecyclerView.Recycler,
            state: RecyclerView.State,
            host: View,
            info: AccessibilityNodeInfoCompat
    ) {
        super.onInitializeAccessibilityNodeInfoForItem(recycler, state, host, info)
        val position = getPosition(host)
        if (position > 0) {
            info.addAction(
                    AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                            R.id.action_drag_move_up,
                            host.context.getString(R.string.action_drag_label_move_up)))
        }
        if (position < itemCount - 1) {
            info.addAction(
                    AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                            R.id.action_drag_move_down,
                            host.context.getString(R.string.action_drag_label_move_down)))
        }
    }

    override fun performAccessibilityActionForItem(
            recycler: RecyclerView.Recycler,
            state: RecyclerView.State,
            host: View,
            action: Int,
            args: Bundle?
    ): Boolean {
        val position = getPosition(host)
        if (action == R.id.action_drag_move_up) {
            adapter.onItemMove(position, position - 1)
            return true
        }
        if (action == R.id.action_drag_move_down) {
            adapter.onItemMove(position, position + 1)
            return true
        }
        return false
    }
}
