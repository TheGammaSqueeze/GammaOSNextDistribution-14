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

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ItemDecoration
import com.android.car.docklib.R

class DockView
@JvmOverloads
constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0
) : FrameLayout(context, attrs, defStyleAttr, defStyleRes) {

    private val recyclerView: RecyclerView

    init {
        inflate(context, R.layout.dock_view, this)
        recyclerView = requireViewById(R.id.recycler_view)
        recyclerView.addItemDecoration(
            object : ItemDecoration() {
                override fun getItemOffsets(
                    outRect: Rect,
                    view: View,
                    parent: RecyclerView,
                    state: RecyclerView.State
                ) {
                    with(outRect) {
                        if (parent.getChildAdapterPosition(view) != 0) {
                            // TODO: b/301484526 set margins in case of RTL and vertical
                            left = resources.getDimensionPixelSize(R.dimen.dock_item_spacing)
                        }
                    }
                }
            }
        )
    }

    fun getAdapter() = recyclerView.adapter as DockAdapter

    fun setAdapter(adapter: DockAdapter) {
        recyclerView.adapter = adapter
    }
}
