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

package com.android.car.carlauncher.pagination;

import android.view.View;

import com.android.car.carlauncher.AppGridConstants;
import com.android.car.carlauncher.AppGridConstants.PageOrientation;
import com.android.car.carlauncher.R;
import com.android.car.carlauncher.recyclerview.PageMarginDecoration;

/**
 * Helper class for PaginationController that computes the measurements of app grid and app items.
 */
public class PageMeasurementHelper {
    private final int mNumOfCols;
    private final int mNumOfRows;
    @PageOrientation
    private final int mPageOrientation;
    private final boolean mUseDefinedDimensions;
    private final int mDefinedWidth;
    private final int mDefinedHeight;
    private final int mDefinedMarginHorizontal;
    private final int mDefinedMarginVertical;
    private final int mDefinedPageIndicatorSize;

    private int mWindowWidth;
    private int mWindowHeight;
    private GridDimensions mGridDimensions;
    private PageDimensions mPageDimensions;

    public PageMeasurementHelper(View windowBackground) {
        mNumOfCols = windowBackground.getResources().getInteger(
                R.integer.car_app_selector_column_number);
        mNumOfRows = windowBackground.getResources().getInteger(
                R.integer.car_app_selector_row_number);
        mPageOrientation = windowBackground.getResources().getBoolean(R.bool.use_vertical_app_grid)
                ? PageOrientation.VERTICAL : PageOrientation.HORIZONTAL;
        mUseDefinedDimensions = windowBackground.getResources().getBoolean(
                R.bool.use_defined_app_grid_dimensions);
        mDefinedWidth = windowBackground.getResources().getDimensionPixelSize(
                R.dimen.app_grid_width);
        mDefinedHeight = windowBackground.getResources().getDimensionPixelSize(
                R.dimen.app_grid_height);
        mDefinedMarginHorizontal = windowBackground.getResources().getDimensionPixelSize(
                R.dimen.app_grid_margin_horizontal);
        mDefinedMarginVertical = windowBackground.getResources().getDimensionPixelSize(
                R.dimen.app_grid_margin_vertical);
        mDefinedPageIndicatorSize = windowBackground.getResources().getDimensionPixelSize(
                R.dimen.page_indicator_height);
    }

    /**
     * @return the most recently updated app grid dimension, or {@code null} if
     * {@link PageMeasurementHelper#handleWindowSizeChange} was never called.
     */
    public GridDimensions getGridDimensions() {
        return mGridDimensions;
    }

    /**
     * @return the most recently updated page margin decoration or {@code null} if
     * {@link PageMeasurementHelper#handleWindowSizeChange} was never called.
     */
    public PageDimensions getPageDimensions() {
        return mPageDimensions;
    }

    /**
     * Handles window dimension change by calculating spaces available for the app grid. Returns
     * {@code true} if the new measurements is different, and {@code false} otherwise.
     *
     * If dimensions has changed, it is the caller's responsibility to retrieve the page and grid
     * dimensions and update their respective layout params. {@link PageMarginDecoration} should
     * also be recreated and reattached to redraw the page margins.
     *
     * @param windowWidth width available for app grid in px.
     * @param windowHeight height available for app grid to fill in px.
     * @return true if the
     */
    public boolean handleWindowSizeChange(int windowWidth, int windowHeight) {
        if (mUseDefinedDimensions) {
            windowWidth = mDefinedWidth;
            windowHeight = mDefinedHeight;
        }
        boolean consumed = windowWidth != mWindowWidth || windowHeight != mWindowHeight;
        if (consumed) {
            mWindowWidth = windowWidth;
            mWindowHeight = windowHeight;
            // Step 1: calculate the width and height available to for the grid layout by accounting
            // for spaces required to place page indicator and page margins.
            int gridWidth = windowWidth - mDefinedMarginHorizontal * 2
                    - (isHorizontal() ? 0 : mDefinedPageIndicatorSize);
            int gridHeight = windowHeight - mDefinedMarginVertical * 2
                    - (isHorizontal() ? mDefinedPageIndicatorSize : 0);

            // Step 2: Round the measurements to ensure child view holder cells have an exact fit.
            gridWidth = roundDownToModuloMultiple(gridWidth, mNumOfCols);
            gridHeight = roundDownToModuloMultiple(gridHeight, mNumOfRows);
            int cellWidth = gridWidth / mNumOfCols;
            int cellHeight = gridHeight / mNumOfRows;
            mGridDimensions = new GridDimensions(gridWidth, gridHeight, cellWidth, cellHeight);

            // Step 3: Since the grid dimens are rounded, we need to recalculate the margins.
            int marginHorizontal = (windowWidth - gridWidth) / 2;
            int marginVertical = (windowHeight - gridHeight) / 2;

            // Step 4: Calculate RecyclerView and PageIndicator dimens for layout params.
            int recyclerViewWidth, recyclerViewHeight;
            int pageIndicatorWidth, pageIndicatorHeight;
            if (isHorizontal()) {
                // horizontal app grid should have HORIZONTAL page indicator bar and the
                // recyclerview width should span the entire window to not clip off the page margin
                recyclerViewWidth = windowWidth;
                recyclerViewHeight = gridHeight;
                pageIndicatorWidth = gridWidth;
                pageIndicatorHeight = mDefinedPageIndicatorSize;
            } else {
                // vertical app grid should have VERTICAL page indicator bar and the
                // recyclerview height should span the entire window to not clip off the page margin
                recyclerViewWidth = gridWidth;
                recyclerViewHeight = windowHeight;
                pageIndicatorWidth = mDefinedPageIndicatorSize;
                pageIndicatorHeight = gridHeight;
            }
            mPageDimensions = new PageDimensions(recyclerViewWidth, recyclerViewHeight,
                    marginHorizontal, marginVertical, pageIndicatorWidth, pageIndicatorHeight,
                    windowWidth, windowHeight);
        }
        return consumed;
    }

    private boolean isHorizontal() {
        return AppGridConstants.isHorizontal(mPageOrientation);
    }

    /**
     * Rounds down to the nearest modulo multiple. For example, when {@code input} is 1024 and
     * {@code modulo} is 5, we want to round down to 1020, since 1020 is the largest number
     * such that {1020 % 5 = 0}.
     */
    private int roundDownToModuloMultiple(int input, int modulo) {
        return input / modulo * modulo;
    }

    /**
     * Data structure representing dimensions of the app grid.
     *
     * {@link GridDimensions#cellWidthPx} and {@link GridDimensions#cellHeightPx}:
     * The width and height of each app item cell (view holder layout).
     *
     * {@link GridDimensions#gridWidthPx} and {@link GridDimensions#gridWidthPx}:
     * The width and height of the app grid. These values should be equal to or less than the
     * RecyclerView dimensions, with equal case being page margin size being 0 px.
     */
    public static class GridDimensions {
        public int gridWidthPx;
        public int gridHeightPx;
        public int cellWidthPx;
        public int cellHeightPx;

        public GridDimensions(int gridWidth, int gridHeight, int cellWidth, int cellHeight) {
            gridWidthPx = gridWidth;
            gridHeightPx = gridHeight;
            cellWidthPx = cellWidth;
            cellHeightPx = cellHeight;
        }

        @Override
        public String toString() {
            return "%s {".formatted(super.toString())
                    + " gridWidthPx: %d".formatted(gridWidthPx)
                    + " gridHeightPx: %d".formatted(gridHeightPx)
                    + " cellWidthPx: %d".formatted(cellWidthPx)
                    + " cellHeightPx: %d".formatted(cellHeightPx)
                    + "}";
        }
    }

    /**
     * Data structure representing dimensions of the app grid.
     *
     * {@link PageDimensions#recyclerViewWidthPx} and {@link PageDimensions#recyclerViewHeightPx}
     * The width and height of recycler view layout params.
     *
     * {@link PageDimensions#marginHorizontalPx} and {@link PageDimensions#marginVerticalPx}
     * The margins on the left/right and top/bottom of the recycler view, respectively.
     *
     * {@link PageDimensions#pageIndicatorWidthPx} and {@link PageDimensions#pageIndicatorHeightPx}
     * The width and height of the page indicator prior to resizing and adjusting offsets.
     */
    public static class PageDimensions {
        public int recyclerViewWidthPx;
        public int recyclerViewHeightPx;
        public int marginHorizontalPx;
        public int marginVerticalPx;
        public int pageIndicatorWidthPx;
        public int pageIndicatorHeightPx;
        public int windowWidthPx;
        public int windowHeightPx;

        public PageDimensions(int recyclerViewWidth, int recyclerViewHeight, int marginHorizontal,
                int marginVertical, int pageIndicatorWidth, int pageIndicatorHeight,
                int windowWidth, int windowHeight) {
            recyclerViewWidthPx = recyclerViewWidth;
            recyclerViewHeightPx = recyclerViewHeight;
            marginHorizontalPx = marginHorizontal;
            marginVerticalPx = marginVertical;
            pageIndicatorWidthPx = pageIndicatorWidth;
            pageIndicatorHeightPx = pageIndicatorHeight;
            windowWidthPx = windowWidth;
            windowHeightPx = windowHeight;
        }

        @Override
        public String toString() {
            return "%s {".formatted(super.toString())
                    + " recyclerViewWidthPx: %d".formatted(recyclerViewWidthPx)
                    + " recyclerViewHeightPx: %d".formatted(recyclerViewHeightPx)
                    + " marginHorizontalPx: %d".formatted(marginHorizontalPx)
                    + " marginVerticalPx: %d".formatted(marginVerticalPx)
                    + " pageIndicatorWidthPx: %d".formatted(pageIndicatorWidthPx)
                    + " pageIndicatorHeightPx: %d".formatted(pageIndicatorHeightPx)
                    + " windowWidthPx: %d".formatted(windowWidthPx)
                    + " windowHeightPx: %d".formatted(windowHeightPx)
                    + "}";
        }
    }
}
