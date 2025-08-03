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
package com.android.customization.picker.common.ui.view

import android.graphics.Rect
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.RecyclerView

/** Item spacing used by the RecyclerView. */
class ItemSpacing(
    private val itemSpacingDp: Int,
) : RecyclerView.ItemDecoration() {
    override fun getItemOffsets(outRect: Rect, itemPosition: Int, parent: RecyclerView) {
        val addSpacingToStart = itemPosition > 0
        val addSpacingToEnd = itemPosition < (parent.adapter?.itemCount ?: 0) - 1
        val isRtl = parent.layoutManager?.layoutDirection == ViewCompat.LAYOUT_DIRECTION_RTL
        val density = parent.context.resources.displayMetrics.density
        val halfItemSpacingPx = itemSpacingDp.toPx(density) / 2
        if (!isRtl) {
            outRect.left = if (addSpacingToStart) halfItemSpacingPx else 0
            outRect.right = if (addSpacingToEnd) halfItemSpacingPx else 0
        } else {
            outRect.left = if (addSpacingToEnd) halfItemSpacingPx else 0
            outRect.right = if (addSpacingToStart) halfItemSpacingPx else 0
        }
    }

    private fun Int.toPx(density: Float): Int {
        return (this * density).toInt()
    }

    companion object {
        const val TAB_ITEM_SPACING_DP = 12
        const val ITEM_SPACING_DP = 8
    }
}
