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

package com.android.systemui.car.userpicker;

import android.graphics.Rect;
import android.view.View;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

// TODO(b/281729191) unused until RecyclerView.ItemDecoration is supported by CarUiRecyclerView
final class UserPickerItemSpacingDecoration extends RecyclerView.ItemDecoration {
    private final int mVerticalSpacing;
    private final int mHorizontalSpacing;
    private final int mNumCols;

    UserPickerItemSpacingDecoration(int verticalSpacing, int horizontalSpacing, int numCols) {
        mVerticalSpacing = verticalSpacing;
        mHorizontalSpacing = horizontalSpacing;
        mNumCols = numCols;
    }

    @Override
    public void getItemOffsets(Rect outRect, View view, RecyclerView parent,
            RecyclerView.State state) {
        super.getItemOffsets(outRect, view, parent, state);
        int position = parent.getChildAdapterPosition(view);

        // Skip offset for last item except for {@link GridLayoutManager}.
        if (position == state.getItemCount() - 1
                && !(parent.getLayoutManager() instanceof GridLayoutManager)) {
            return;
        }

        outRect.bottom = mVerticalSpacing;

        // The space between the columns should be split evenly among the elements such that all
        // elements give up the same space. Each element should give up a total of
        // (mNumCols - 1) / mNumCols of the set spacing amount across both sides. The first and last
        // element should only add space to the inside area of the view.
        int splitHorizontalSpacing = mHorizontalSpacing / mNumCols;
        int col = position % mNumCols;
        outRect.left = col * splitHorizontalSpacing;
        outRect.right = (mNumCols - (col + 1)) * splitHorizontalSpacing;
    }
}
