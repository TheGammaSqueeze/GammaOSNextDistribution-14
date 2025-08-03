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

import static com.android.car.carlauncher.AppGridConstants.AppItemBoundDirection;
import static com.android.car.carlauncher.AppGridConstants.PageOrientation;
import static com.android.car.carlauncher.AppGridConstants.isHorizontal;

import android.view.View;

/**
 * Helper class that handles all page rounding logic.
 */
public class PageIndexingHelper {
    private final int mNumOfCols;
    private final int mNumOfRows;
    @PageOrientation
    private final int mPageOrientation;

    private int mLayoutDirection;

    public PageIndexingHelper(int numOfCols, int numOfRows, @PageOrientation int orientation) {
        mNumOfCols = numOfCols;
        mNumOfRows = numOfRows;
        mPageOrientation = orientation;
    }

    /**
     * Layout direction needs to be reset onResume as to not crash when user switches to another
     * language with different layout direction.
     */
    public void setLayoutDirection(int layoutDirection) {
        mLayoutDirection = layoutDirection;
    }

    /**
     * Returns the direction of the offset to add to the app item at the given grid position.
     *
     * For example, when there are 5 app items per column when using horizontal paging, the
     * 1st column app item should have padding to the left and 4th column app item should have
     * padding to the right.
     */
    @AppItemBoundDirection
    public int getOffsetBoundDirection(int gridPosition) {
        // TODO (b/271628061): rename gridPosition and adapterIndex
        if (isHorizontal(mPageOrientation)) {
            int cid = (gridPosition / mNumOfRows) % mNumOfCols;
            if (cid == 0) {
                return AppItemBoundDirection.LEFT;
            } else if (cid == mNumOfCols - 1) {
                return AppItemBoundDirection.RIGHT;
            }
        } else {
            int rid = (gridPosition / mNumOfCols) % mNumOfRows;
            if (rid == 0) {
                return AppItemBoundDirection.TOP;
            } else if (rid == mNumOfRows - 1) {
                return AppItemBoundDirection.BOTTOM;
            }
        }
        return AppItemBoundDirection.NONE;
    }

    /**
     * Grid position refers to the default position in a RecyclerView used to draw out a horizontal
     * grid layout, shown below.
     *
     * This is the value returned by the default ViewHolder.getAbsoluteAdapterPosition().
     */
    public int gridPositionToAdaptorIndex(int position) {
        if (!isHorizontal(mPageOrientation)) {
            if (mLayoutDirection == View.LAYOUT_DIRECTION_RTL) {
                int cid = position % mNumOfCols;
                // column order swap
                position = (position - cid) + (mNumOfCols - cid - 1);
            }
            return position;
        }
        int positionOnPage = position % (mNumOfCols * mNumOfRows);
        // page the item resides on
        int pid = position / (mNumOfCols * mNumOfRows);
        // row of the item, in matrix order
        int rid = positionOnPage % mNumOfRows;
        // column of the item, in matrix order / LTR order
        int cid = positionOnPage / mNumOfRows;

        if (mLayoutDirection == View.LAYOUT_DIRECTION_RTL) {
            cid = mNumOfCols - cid - 1;
        }
        return (pid * mNumOfRows * mNumOfCols) + (rid * mNumOfCols) + cid;
    }

    /**
     * Adapter index refers to the "business logic" index, which is the order which the users will
     * read the app in their language (either LTR or RTL on each page)
     */
    public int adaptorIndexToGridPosition(int index) {
        if (!isHorizontal(mPageOrientation)) {
            if (mLayoutDirection == View.LAYOUT_DIRECTION_RTL) {
                int cid = index % mNumOfCols;
                // column order swap
                index = (index - cid) + (mNumOfCols - cid - 1);
            }
            return index;
        }
        int indexOnPage = index % (mNumOfCols * mNumOfRows);
        // page the item resides on
        int pid = index / (mNumOfCols * mNumOfRows);
        // row of the item, in matrix order
        int rid = indexOnPage / mNumOfCols;
        // column of the item, in matrix order / LTR order
        int cid = indexOnPage % mNumOfCols;

        if (mLayoutDirection == View.LAYOUT_DIRECTION_RTL) {
            cid =  mNumOfCols - cid - 1;
        }
        return (pid * mNumOfRows * mNumOfCols) + (cid * mNumOfRows) + rid;
    }

    /**
     * Returns the grid position of the FIRST item on the page. The result is always the same in RTL
     * and LTR since the first and last index of every page is always mirrored.
     *
     *          * Grid Position example - Horizontal
     *          *  |[0] 3  6  9  12 |[15] 18 21 24 27 |
     *          *  | 1  4  7  10 13 | 16  19 22 25 28 |
     *          *  | 2  5  8  11 14 | 17  20 23 26 29 |
     *
     *          * Grid Position example - Vertical
     *          *  |[1]  2   3   4   5  |
     *          *  | 6   7   8   9   10 |
     *          *  | 11  12  13  14  15 |
     *          *  __________________
     *          *  |[16] 17  18  19  20 |
     *          *  | 21  22  23  24  25 |
     *          *  | 26  27  28  29  30 |
     */
    public int roundToFirstIndexOnPage(int gridPosition) {
        int pidRoundedDown = gridPosition / (mNumOfCols * mNumOfRows);
        return pidRoundedDown * (mNumOfCols * mNumOfRows);
    }

    /**
     * Returns the grid position of the LAST item on the page. The result is always the same in RTL
     * and LTR since the first and last index of every page is always mirrored.
     *
     *          * Grid position example - horizontal
     *          *  | 0  3  6  9   12  | 15  18  21  24  27  |
     *          *  | 1  4  7  10  13  | 16  19  22  25  28  |
     *          *  | 2  5  8  11 [14] | 17  20  23  26 [29] |
     *
     *          * Grid position example - Vertical
     *          *  | 1   2   3   4   5   |
     *          *  | 6   7   8   9   10  |
     *          *  | 11  12  13  14 [15] |
     *          *
     *          *  | 16  17  18  19  20  |
     *          *  | 21  22  23  24  25  |
     *          *  | 26  27  28  29 [30] |
     */
    public int roundToLastIndexOnPage(int gridPosition) {
        int pidRoundedUp = gridPosition / (mNumOfCols * mNumOfRows) + 1;
        return pidRoundedUp * (mNumOfCols * mNumOfRows) - 1;
    }
}
