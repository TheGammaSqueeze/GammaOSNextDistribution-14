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

package com.android.car.carlauncher.recyclerview;

import android.graphics.Rect;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.android.car.carlauncher.AppGridConstants.AppItemBoundDirection;
import com.android.car.carlauncher.pagination.PageIndexingHelper;

/**
 * ItemDecoration that adds margins to the corner items in the recycler view to create margins
 * between each page of the app grid.
 */
public class PageMarginDecoration extends RecyclerView.ItemDecoration {
    private final PageIndexingHelper mIndexingHelper;
    private final int mMarginHorizontalPx;
    private final int mMarginVerticalPx;

    public PageMarginDecoration(int marginHorizontal, int marginVertical,
            PageIndexingHelper pageIndexingHelper) {
        mMarginHorizontalPx = marginHorizontal;
        mMarginVerticalPx = marginVertical;
        mIndexingHelper = pageIndexingHelper;
    }

    @Override
    public void getItemOffsets(@NonNull Rect outRect, @NonNull View view,
            @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        outRect.set(0, 0, 0, 0);
        switch (mIndexingHelper.getOffsetBoundDirection(parent.getChildAdapterPosition(view))) {
            case AppItemBoundDirection.LEFT:
                outRect.left = mMarginHorizontalPx;
                break;
            case AppItemBoundDirection.RIGHT:
                outRect.right = mMarginHorizontalPx;
                break;
            case AppItemBoundDirection.TOP:
                outRect.top = mMarginVerticalPx;
                break;
            case AppItemBoundDirection.BOTTOM:
                outRect.bottom = mMarginVerticalPx;
        }
    }
}
