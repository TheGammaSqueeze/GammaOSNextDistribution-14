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

import android.content.Context;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.recyclerview.widget.LinearSnapHelper;
import androidx.recyclerview.widget.OrientationHelper;
import androidx.recyclerview.widget.RecyclerView;


/**
 * <p>Extension of a {@link LinearSnapHelper} that will snap to the next/previous page.
 * for a horizontal's recycler view.
 */
public class AppGridPageSnapper extends LinearSnapHelper {
    private final float mPageSnapThreshold;
    private final float mFlingThreshold;

    @NonNull
    private final Context mContext;
    @Nullable
    private RecyclerView mRecyclerView;
    private int mBlockSize = 0;
    private int mPrevFirstVisiblePos = 0;
    private AppGridPageSnapCallback mSnapCallback;

    public AppGridPageSnapper(
            @NonNull Context context,
            int numOfCol,
            int numOfRow,
            AppGridPageSnapCallback snapCallback) {
        mSnapCallback = snapCallback;
        mContext = context;
        mPageSnapThreshold = context.getResources().getFloat(R.dimen.page_snap_threshold);
        mFlingThreshold = context.getResources().getFloat(R.dimen.fling_threshold);
        mBlockSize = numOfCol * numOfRow;
    }

    // Orientation helpers are lazily created per LayoutManager.
    @Nullable
    private OrientationHelper mHorizontalHelper;
    @Nullable
    private OrientationHelper mVerticalHelper;

    @VisibleForTesting
    RecyclerView.OnFlingListener mOnFlingListener;

    /**
     * Finds the view to snap to. The view to snap can be either the current, next or previous page.
     * Start is defined as the left if the orientation is horizontal and top if the orientation is
     * vertical
     */
    @Override
    @Nullable
    public View findSnapView(@Nullable RecyclerView.LayoutManager layoutManager) {
        if (layoutManager == null || layoutManager.getChildCount() == 0) {
            return null;
        }

        OrientationHelper orientationHelper = getOrientationHelper(layoutManager);

        if (mRecyclerView == null) {
            return null;
        }

        View currentPosView = getFirstMostVisibleChild(orientationHelper);
        int adapterPos = findAdapterPosition(currentPosView);
        int posToReturn;

        // In the case of swiping left, the current adapter position is smaller than the previous
        // first visible position. In the case of swiping right, the current adapter position is
        // greater than the previous first visible position. In this case, if the swipe is
        // by only 1 column, the page should remain the same since we want to demonstrate some
        // stickiness
        if (adapterPos <= mPrevFirstVisiblePos
                || (float) adapterPos % mBlockSize / mBlockSize < mPageSnapThreshold) {
            posToReturn = adapterPos - adapterPos % mBlockSize;
        } else {
            // Snap to next page
            posToReturn = (adapterPos / mBlockSize + 1) * mBlockSize + mBlockSize - 1;
        }
        handleScrollToPos(posToReturn, orientationHelper);
        return null;
    }

    private int findAdapterPosition(View view) {
        RecyclerView.ViewHolder holder = mRecyclerView.findContainingViewHolder(view);
        return holder.getAbsoluteAdapterPosition();
    }

    @VisibleForTesting
    int findFirstItemOnNextPage(int adapterPos) {
        return (adapterPos / mBlockSize + 1) * mBlockSize + mBlockSize - 1;
    }

    @VisibleForTesting
    int findFirstItemOnPrevPage(int adapterPos) {
        return adapterPos - (adapterPos - 1) % mBlockSize - 1;
    }

    private void handleScrollToPos(int posToReturn, OrientationHelper orientationHelper) {
        mPrevFirstVisiblePos = posToReturn / mBlockSize * mBlockSize;
        mRecyclerView.smoothScrollToPosition(posToReturn);
        mSnapCallback.notifySnapToPosition(posToReturn);

        // If there is a gap between the start of the first fully visible child and the start of
        // the recycler view (this can happen after the swipe or when the swipe offset is too small
        // such that the first fully visible item doesn't change), smooth scroll to make sure the
        // gap no longer exists.
        RecyclerView.ViewHolder childToReturn = mRecyclerView.findViewHolderForAdapterPosition(
                posToReturn);
        if (childToReturn != null) {
            int start = orientationHelper.getStartAfterPadding();
            int viewStart = orientationHelper.getDecoratedStart(childToReturn.itemView);
            if (viewStart - start > 0) {
                if (mHorizontalHelper != null) {
                    mRecyclerView.smoothScrollBy(viewStart - start, 0);
                } else {
                    mRecyclerView.smoothScrollBy(0, viewStart - start);
                }
            }
        }
    }

    @NonNull
    private OrientationHelper getOrientationHelper(
            @NonNull RecyclerView.LayoutManager layoutManager) {
        return layoutManager.canScrollVertically() ? getVerticalHelper(layoutManager)
                : getHorizontalHelper(layoutManager);
    }

    @NonNull
    private OrientationHelper getVerticalHelper(
            @NonNull RecyclerView.LayoutManager layoutManager) {
        if (mVerticalHelper == null || mVerticalHelper.getLayoutManager() != layoutManager) {
            mVerticalHelper = OrientationHelper.createVerticalHelper(layoutManager);
        }
        return mVerticalHelper;
    }

    @NonNull
    private OrientationHelper getHorizontalHelper(
            @NonNull RecyclerView.LayoutManager layoutManager) {
        if (mHorizontalHelper == null || mHorizontalHelper.getLayoutManager() != layoutManager) {
            mHorizontalHelper = OrientationHelper.createHorizontalHelper(layoutManager);
        }
        return mHorizontalHelper;
    }

    /**
     * Returns the percentage of the given view that is visible, relative to its containing
     * RecyclerView.
     *
     * @param view   The View to get the percentage visible of.
     * @param helper An {@link OrientationHelper} to aid with calculation.
     * @return A float indicating the percentage of the given view that is visible.
     */
    static float getPercentageVisible(@Nullable View view, @NonNull OrientationHelper helper) {
        if (view == null) {
            return 0;
        }
        int start = helper.getStartAfterPadding();
        int end = helper.getEndAfterPadding();

        int viewStart = helper.getDecoratedStart(view);
        int viewEnd = helper.getDecoratedEnd(view);

        if (viewStart >= start && viewEnd <= end) {
            // The view is within the bounds of the RecyclerView, so it's fully visible.
            return 1.f;
        } else if (viewEnd <= start) {
            // The view is above the visible area of the RecyclerView.
            return 0;
        } else if (viewStart >= end) {
            // The view is below the visible area of the RecyclerView.
            return 0;
        } else if (viewStart <= start && viewEnd >= end) {
            // The view is larger than the height of the RecyclerView.
            return ((float) end - start) / helper.getDecoratedMeasurement(view);
        } else if (viewStart < start) {
            // The view is above the start of the RecyclerView.
            return ((float) viewEnd - start) / helper.getDecoratedMeasurement(view);
        } else {
            // The view is below the end of the RecyclerView.
            return ((float) end - viewStart) / helper.getDecoratedMeasurement(view);
        }
    }

    @Nullable
    private View getFirstMostVisibleChild(@NonNull OrientationHelper helper) {
        float mostVisiblePercent = 0;
        View mostVisibleView = null;
        for (int i = 0; i < mRecyclerView.getLayoutManager().getChildCount(); i++) {
            View child = mRecyclerView.getLayoutManager().getChildAt(i);
            float visiblePercentage = getPercentageVisible(child, helper);
            if (visiblePercentage == 1f) {
                mostVisibleView = child;
                break;
            } else if (visiblePercentage > mostVisiblePercent) {
                mostVisiblePercent = visiblePercentage;
                mostVisibleView = child;
            }
        }
        return mostVisibleView;
    }

    @Override
    public void attachToRecyclerView(@Nullable RecyclerView recyclerView) {
        super.attachToRecyclerView(recyclerView);
        mRecyclerView = recyclerView;
        if (mRecyclerView == null) {
            return;
        }

        // When a fling happens, try to find the target snap view and go there.
        mOnFlingListener = new RecyclerView.OnFlingListener() {
            @Override
            public boolean onFling(int velocityX, int velocityY) {
                RecyclerView.LayoutManager layoutManager = mRecyclerView.getLayoutManager();
                OrientationHelper orientationHelper = getOrientationHelper(layoutManager);
                View currentPosView = getFirstMostVisibleChild(orientationHelper);
                int adapterPos = findAdapterPosition(currentPosView);
                int posToReturn = mPrevFirstVisiblePos;
                if (velocityX > mFlingThreshold || velocityY > mFlingThreshold) {
                    posToReturn = findFirstItemOnNextPage(adapterPos);
                } else if (velocityX < -mFlingThreshold || velocityY < -mFlingThreshold) {
                    posToReturn = findFirstItemOnPrevPage(adapterPos);
                }
                handleScrollToPos(posToReturn, orientationHelper);
                return true;
            }
        };
        mRecyclerView.setOnFlingListener(mOnFlingListener);
    }

    @VisibleForTesting
    void setOnFlingListener(RecyclerView.OnFlingListener onFlingListener) {
        mRecyclerView.setOnFlingListener(onFlingListener);
    }

    /**
     * A Callback contract between all app grid components that causes triggers a scroll or snap
     * behavior and its listener.
     *
     * Scrolling by user touch or by recyclerview during off page scroll should always cause a
     * page snap, and it is up to the AppGridPageSnapCallback to notify the listener to cache that
     * snapped index to allow user to return to that location when they trigger onResume.
     */
    public static class AppGridPageSnapCallback {
        private final PageSnapListener mSnapListener;
        private int mSnapPosition;
        private int mScrollState;

        public AppGridPageSnapCallback(PageSnapListener snapListener) {
            mSnapListener = snapListener;
        }

        /** caches the most recent snap position and notifies the listener */
        public void notifySnapToPosition(int gridPosition) {
            mSnapPosition = gridPosition;
            mSnapListener.onSnapToPosition(gridPosition);
        }

        /** return the most recent cached snap position */
        public int getSnapPosition() {
            return mSnapPosition;
        }

        /** caches the current recent scroll state */
        public void setScrollState(int newState) {
            mScrollState = newState;
        }

        /** return the most recent scroll state */
        public int getScrollState() {
            return mScrollState;
        }
    }

    /**
     * Listener class that should be implemented by AppGridActivity.
     */
    public interface PageSnapListener {
        /** Listener method called during AppGridPageSnapCallback.notifySnapToPosition */
        void onSnapToPosition(int gridPosition);
    }
}
