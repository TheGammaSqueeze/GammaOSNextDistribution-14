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

package com.android.car.carlauncher;

import static com.android.car.carlauncher.AppGridConstants.PageOrientation;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

import androidx.recyclerview.widget.RecyclerView;

import com.android.car.carlauncher.pagination.PageIndexingHelper;
import com.android.car.carlauncher.pagination.PageMeasurementHelper.GridDimensions;
import com.android.car.carlauncher.pagination.PageMeasurementHelper.PageDimensions;
import com.android.car.carlauncher.pagination.PaginationController.DimensionUpdateListener;
import com.android.car.carlauncher.recyclerview.AppGridAdapter;
import com.android.car.carlauncher.recyclerview.PageMarginDecoration;

/**
 * The RecyclerView that holds all the apps as children in the main app grid.
 */
public class AppGridRecyclerView extends RecyclerView implements DimensionUpdateListener {
    // the previous rotary focus direction
    private int mPrevRotaryPageScrollDirection = View.FOCUS_FORWARD;
    private final int mNumOfCols;
    private final int mNumOfRows;
    @PageOrientation
    private final int mPageOrientation;
    private AppGridAdapter mAdapter;
    private PageMarginDecoration mPageMarginDecoration;

    public AppGridRecyclerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mNumOfCols = getResources().getInteger(R.integer.car_app_selector_column_number);
        mNumOfRows = getResources().getInteger(R.integer.car_app_selector_row_number);
        mPageOrientation = getResources().getBoolean(R.bool.use_vertical_app_grid)
                ? PageOrientation.VERTICAL : PageOrientation.HORIZONTAL;
    }

    @Override
    public void setAdapter(RecyclerView.Adapter adapter) {
        if (!(adapter instanceof AppGridAdapter)) {
            throw new IllegalStateException("Expected Adapter of type AppGridAdapter");
        }
        mAdapter = (AppGridAdapter) adapter;
        super.setAdapter(mAdapter);
    }

    /**
     * Finds the next focusable descendant given rotary input of either View.FOCUS_FORWARD or
     * View.FOCUS_BACKWARD.
     *
     * This method could be called during a scroll event, or to initiate a scroll event when the
     * intended viewHolder item is not on the screen.
     */
    @Override
    public View focusSearch(View focused, int direction) {
        ViewHolder viewHolder = findContainingViewHolder(focused);
        AppGridAdapter adapter = (AppGridAdapter) getAdapter();

        if (viewHolder == null || getScrollState() != RecyclerView.SCROLL_STATE_IDLE) {
            // user may input additional rotary rotations during a page sling, so we return the
            // currently focused view.
            return focused;
        }

        int currentPosition = viewHolder.getAbsoluteAdapterPosition();
        int nextPosition = adapter.getNextRotaryFocus(currentPosition, direction);

        int blockSize = mNumOfCols * mNumOfRows;
        if ((currentPosition / blockSize) == (nextPosition / blockSize)) {
            // if the views are on the same page, then RecyclerView#getChildAt will be able to find
            // the child on screen.
            return getChildAt(nextPosition % blockSize);
        }

        // since the view is not on the screen and focusSearch cannot target a view that has not
        // been recycled yet, we need to dispatch a scroll event and postpone focusing.
        if (AppGridConstants.isHorizontal(mPageOrientation)) {
            // TODO: fix rounding issue on last page with rotary
            int pageWidth = getMeasuredWidth();
            int dx = (direction == View.FOCUS_FORWARD) ? pageWidth : -pageWidth;
            smoothScrollBy(dx, 0);
        } else {
            int pageHeight = getMeasuredHeight();
            int dy = (direction == View.FOCUS_FORWARD) ? pageHeight : -pageHeight;
            smoothScrollBy(0, dy);
        }
        mPrevRotaryPageScrollDirection = direction;

        // the focus should remain on current focused view until maybeHandleRotaryFocus is called
        return focused;
    }

    /**
     * Handles the delayed rotary focus request. This method should only be called after rotary page
     * scroll completed.
     */
    public void maybeHandleRotaryFocus() {
        if (!isInTouchMode()) {
            // if the recyclerview just settled, and it is using remote inputs, it must have been
            // scrolled by focusSearch
            if (mPrevRotaryPageScrollDirection == View.FOCUS_FORWARD) {
                getChildAt(0).requestFocus();
                return;
            }
            getChildAt(mNumOfCols * mNumOfRows - 1).requestFocus();
        }
    }

    @Override
    public void onDimensionsUpdated(PageDimensions pageDimens, GridDimensions gridDimens) {
        ViewGroup.LayoutParams layoutParams = getLayoutParams();
        layoutParams.width = pageDimens.recyclerViewWidthPx;
        layoutParams.height = pageDimens.recyclerViewHeightPx;

        Rect pageBounds = new Rect();
        getGlobalVisibleRect(pageBounds);
        mAdapter.updateViewHolderDimensions(pageBounds, gridDimens.cellWidthPx,
                gridDimens.cellHeightPx);
        mAdapter.notifyDataSetChanged();

        if (mPageMarginDecoration != null) {
            removeItemDecoration(mPageMarginDecoration);
        }
        mPageMarginDecoration = new PageMarginDecoration(pageDimens.marginHorizontalPx,
                pageDimens.marginVerticalPx, new PageIndexingHelper(mNumOfCols, mNumOfRows,
                mPageOrientation));
        addItemDecoration(mPageMarginDecoration);
    }
}
