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
import static com.android.car.carlauncher.AppGridConstants.isHorizontal;

import android.content.Context;
import android.util.AttributeSet;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.core.content.ContextCompat;

import com.android.car.carlauncher.pagination.PageMeasurementHelper.GridDimensions;
import com.android.car.carlauncher.pagination.PageMeasurementHelper.PageDimensions;
import com.android.car.carlauncher.pagination.PaginationController.DimensionUpdateListener;

/**
 * A scrollbar like view that dynamically adjusts its offset and size to indicate to users which
 * page they are on when scrolling through the app grid.
 */
public class PageIndicator extends FrameLayout implements DimensionUpdateListener {
    private final long mAppearAnimationDurationMs;
    private final long mAppearAnimationDelayMs;
    private final long mFadeAnimationDurationMs;
    private final long mFadeAnimationDelayMs;
    @PageOrientation
    private final int mPageOrientation;

    private FrameLayout mContainer;
    private int mAppGridWidth;
    private int mAppGridHeight;
    private int mAppGridOffset;
    private int mPageCount = 1;

    private float mOffsetScaleFactor = 1.f;

    public PageIndicator(Context context, AttributeSet attrs) {
        super(context, attrs);
        mFadeAnimationDurationMs = getResources().getInteger(
                R.integer.ms_scrollbar_fade_animation_duration);
        mFadeAnimationDelayMs = getResources().getInteger(
                R.integer.ms_scrollbar_fade_animation_delay);
        mAppearAnimationDurationMs = getResources().getInteger(
                R.integer.ms_scrollbar_appear_animation_duration);
        mAppearAnimationDelayMs = getResources().getInteger(
                R.integer.ms_scrollbar_appear_animation_delay);
        mPageOrientation = getResources().getBoolean(R.bool.use_vertical_app_grid)
                ? PageOrientation.VERTICAL : PageOrientation.HORIZONTAL;
        setBackground(ContextCompat.getDrawable(context, R.drawable.page_indicator_bar));
        setLayoutParams(new LayoutParams(/* width */ mAppGridWidth,
                /* height */ LayoutParams.MATCH_PARENT));
        setAlpha(0.f);
    }

    public void setContainer(FrameLayout container) {
        // TODO (b/271637411): along with adding scroll controller, create an a layout xml where
        // the container is inflated along with the scroll bar in this class so its accessible.
        mContainer = container;
    }

    @Override
    public void onDimensionsUpdated(PageDimensions pageDimens, GridDimensions gridDimens) {
        ViewGroup.LayoutParams indicatorContainerParams = mContainer.getLayoutParams();
        indicatorContainerParams.width = pageDimens.pageIndicatorWidthPx;
        indicatorContainerParams.height = pageDimens.pageIndicatorHeightPx;
        mOffsetScaleFactor = isHorizontal(mPageOrientation)
                ? (float) gridDimens.gridWidthPx / pageDimens.windowWidthPx
                : (float) gridDimens.gridHeightPx / pageDimens.windowHeightPx;
        mAppGridWidth = gridDimens.gridWidthPx;
        mAppGridHeight = gridDimens.gridHeightPx;
        updatePageCount(mPageCount);
    }

    /**
     * Updates the dimensions of the scroll bar when number of pages in app grid changes.
     */
    void updatePageCount(int pageCount) {
        mPageCount = pageCount;
        LayoutParams params = (LayoutParams) getLayoutParams();
        if (isHorizontal(mPageOrientation)) {
            params.width = mAppGridWidth / mPageCount;
        } else {
            params.height = mAppGridHeight / mPageCount;
        }
        setLayoutParams(params);
        updateOffset(mAppGridOffset);
    }

    /**
     * Updates the offset when recyclerview has been scrolled to xOffset.
     */
    void updateOffset(int appGridOffset) {
        mAppGridOffset = appGridOffset;
        LayoutParams params = (LayoutParams) getLayoutParams();
        int offset = (int) (mAppGridOffset * mOffsetScaleFactor / mPageCount);
        if (isHorizontal(mPageOrientation)) {
            params.leftMargin = offset;
        } else {
            params.topMargin = offset;
        }
        setLayoutParams(params);
    }

    void animateAppearance() {
        // TODO (b/273771594) allow custom animations for fade transitions
        animate().alpha(1.f)
                 .setDuration(mAppearAnimationDurationMs)
                 .setStartDelay(mAppearAnimationDelayMs);
    }

    void animateFading() {
        animate().alpha(0.f)
                 .setDuration(mFadeAnimationDelayMs)
                 .setStartDelay(mFadeAnimationDelayMs);
    }
}
