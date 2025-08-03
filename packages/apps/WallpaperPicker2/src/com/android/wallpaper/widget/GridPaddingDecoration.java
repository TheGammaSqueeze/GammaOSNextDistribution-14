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
 */

package com.android.wallpaper.widget;

import android.graphics.Rect;
import android.view.View;

import androidx.recyclerview.widget.RecyclerView;

/**
 * RecyclerView ItemDecorator that adds a horizontal space and bottom space of the given size
 * between items
 */
public class GridPaddingDecoration extends RecyclerView.ItemDecoration {

    private final int mPaddingHorizontal;
    private final int mPaddingBottom;

    public GridPaddingDecoration(int paddingHorizontal, int paddingBottom) {
        mPaddingHorizontal = paddingHorizontal;
        mPaddingBottom = paddingBottom;
    }

    @Override
    public void getItemOffsets(Rect outRect, View view, RecyclerView parent,
            RecyclerView.State state) {
        int position = parent.getChildAdapterPosition(view);
        if (position >= 0) {
            outRect.left = mPaddingHorizontal;
            outRect.right = mPaddingHorizontal;
            outRect.bottom = mPaddingBottom;
        }
    }
}
