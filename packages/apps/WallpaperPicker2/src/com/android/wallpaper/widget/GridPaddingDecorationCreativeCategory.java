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

package com.android.wallpaper.widget;

import static com.android.wallpaper.picker.individual.IndividualPickerFragment2.IndividualAdapter.ITEM_VIEW_TYPE_HEADER;
import static com.android.wallpaper.picker.individual.IndividualPickerFragment2.IndividualAdapter.ITEM_VIEW_TYPE_HEADER_TOP;
import static com.android.wallpaper.picker.individual.IndividualPickerFragment2.IndividualAdapter.ITEM_VIEW_TYPE_INDIVIDUAL_WALLPAPER;

import android.graphics.Rect;
import android.os.Build;
import android.view.View;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

/**
 * RecyclerView ItemDecorator that adds a horizontal space and bottom space of the given size
 * between items of type creative category
 */
public class GridPaddingDecorationCreativeCategory extends RecyclerView.ItemDecoration {
    private final int mPaddingHorizontal;
    private final int mPaddingBottom;
    private int mEdgePadding;

    public GridPaddingDecorationCreativeCategory(int paddingHorizontal, int paddingBottom,
            int edgePadding) {
        mPaddingHorizontal = paddingHorizontal;
        mPaddingBottom = paddingBottom;
        mEdgePadding = edgePadding;
    }

    @Override
    public void getItemOffsets(Rect outRect, View view, RecyclerView parent,
            RecyclerView.State state) {
        int position = parent.getChildAdapterPosition(view);
        int spanCount = getSpanCount(parent);
        boolean isRtl = isRtlLayout(parent);

        if (position >= 0) {
            if (parent.getAdapter().getItemViewType(position)
                    == ITEM_VIEW_TYPE_INDIVIDUAL_WALLPAPER) {
                outRect.bottom = mPaddingBottom;
                // Calculate the total horizontal space available for items in a row
                int totalHorizontalSpace = parent.getWidth()
                        - parent.getPaddingLeft()
                        - parent.getPaddingRight()
                        - (mEdgePadding * 2); // Account for the gaps on both sides

                // Calculate the width of each item in the row
                int itemWidth = totalHorizontalSpace / spanCount;

                // Calculate the left and right padding for all items
                int padding = (totalHorizontalSpace - (itemWidth * spanCount)) / (spanCount - 1);

                boolean isFirstItem = position % spanCount == 0;
                // This needs to be done since the emoji wallpapers creation tile is at position 1
                // which is the first element in the row
                if (position == 1) {
                    isFirstItem = true;
                }
                if (isFirstItem) {
                    if (isRtl) {
                        outRect.right = mEdgePadding;
                        outRect.left = padding;
                    } else {
                        outRect.left = mEdgePadding;
                        outRect.right = padding;
                    }
                }
                boolean isLastItem = (position + 1) % spanCount == 0;
                if (isLastItem) {
                    if (isRtl) {
                        outRect.right = padding;
                        outRect.left = mEdgePadding;
                    } else {
                        outRect.left = padding;
                        outRect.right = mEdgePadding;
                    }
                }
                if (!isFirstItem && !isLastItem) {
                    // Middle items
                    outRect.left = outRect.left + mEdgePadding / 2;
                    outRect.right = outRect.right + mEdgePadding / 2;
                }
            } else if ((parent.getAdapter().getItemViewType(position) == ITEM_VIEW_TYPE_HEADER)
                    || (parent.getAdapter().getItemViewType(position)
                    == ITEM_VIEW_TYPE_HEADER_TOP)) {
                // Header items
                outRect.left = mPaddingHorizontal;
                outRect.right = mPaddingHorizontal;
                outRect.bottom = mPaddingBottom;
            }
        }
    }

    private int getSpanCount(RecyclerView parent) {
        RecyclerView.LayoutManager layoutManager = parent.getLayoutManager();
        if (layoutManager instanceof GridLayoutManager) {
            GridLayoutManager gridLayoutManager = (GridLayoutManager) layoutManager;
            return gridLayoutManager.getSpanCount();
        }
        return 1;
    }

    private boolean isRtlLayout(RecyclerView parent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            int layoutDirection = parent.getLayoutDirection();
            return layoutDirection == View.LAYOUT_DIRECTION_RTL;
        }
        return false;
    }

}
