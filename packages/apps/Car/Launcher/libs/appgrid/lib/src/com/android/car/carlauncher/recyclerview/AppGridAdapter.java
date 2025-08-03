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

import static com.android.car.carlauncher.AppGridConstants.AppItemBoundDirection;
import static com.android.car.carlauncher.AppGridConstants.PageOrientation;

import android.content.Context;
import android.graphics.Rect;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.android.car.carlauncher.AppGridActivity.Mode;
import com.android.car.carlauncher.AppGridPageSnapper;
import com.android.car.carlauncher.AppItem;
import com.android.car.carlauncher.LauncherItem;
import com.android.car.carlauncher.LauncherItemDiffCallback;
import com.android.car.carlauncher.LauncherViewModel;
import com.android.car.carlauncher.R;
import com.android.car.carlauncher.RecentAppsRowViewHolder;
import com.android.car.carlauncher.pagination.PageIndexingHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * The adapter that populates the grid view with apps.
 */
public class AppGridAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    public static final int RECENT_APPS_TYPE = 1;
    public static final int APP_ITEM_TYPE = 2;

    private static final String TAG = "AppGridAdapter";
    private final Context mContext;
    private final LayoutInflater mInflater;
    private final PageIndexingHelper mIndexingHelper;
    private final AppItemViewHolder.AppItemDragCallback mDragCallback;
    private final AppGridPageSnapper.AppGridPageSnapCallback mSnapCallback;
    private final int mNumOfCols;
    private final int mNumOfRows;
    private int mAppItemWidth;
    private int mAppItemHeight;
    private final LauncherViewModel mLauncherViewModel;
    // grid order of the mLauncherItems used by DiffUtils in dispatchUpdates to animate UI updates
    private final List<LauncherItem> mGridOrderedLauncherItems;

    private List<LauncherItem> mLauncherItems;
    private boolean mIsDistractionOptimizationRequired;
    private int mPageScrollDestination;
    // the global bounding rect of the app grid including margins (excluding page indicator bar)
    private Rect mPageBound;
    private Mode mAppGridMode;

    public AppGridAdapter(Context context, int numOfCols, int numOfRows,
            LauncherViewModel launcherViewModel, AppItemViewHolder.AppItemDragCallback dragCallback,
            AppGridPageSnapper.AppGridPageSnapCallback snapCallback) {
        this(context, numOfCols, numOfRows,
                context.getResources().getBoolean(R.bool.use_vertical_app_grid)
                        ? PageOrientation.VERTICAL : PageOrientation.HORIZONTAL,
                LayoutInflater.from(context), launcherViewModel, dragCallback, snapCallback);
    }

    public AppGridAdapter(Context context, int numOfCols, int numOfRows,
            @PageOrientation int pageOrientation,
            LayoutInflater layoutInflater, LauncherViewModel launcherViewModel,
            AppItemViewHolder.AppItemDragCallback dragCallback,
            AppGridPageSnapper.AppGridPageSnapCallback snapCallback) {
        this(context, numOfCols, numOfRows, pageOrientation, layoutInflater,
                launcherViewModel, dragCallback, snapCallback, Mode.ALL_APPS);
    }

    public AppGridAdapter(Context context, int numOfCols, int numOfRows,
            @PageOrientation int pageOrientation,
            LayoutInflater layoutInflater, LauncherViewModel launcherViewModel,
            AppItemViewHolder.AppItemDragCallback dragCallback,
            AppGridPageSnapper.AppGridPageSnapCallback snapCallback, Mode mode) {
        mContext = context;
        mInflater = layoutInflater;
        mNumOfCols = numOfCols;
        mNumOfRows = numOfRows;
        mDragCallback = dragCallback;
        mSnapCallback = snapCallback;

        mIndexingHelper = new PageIndexingHelper(numOfCols, numOfRows, pageOrientation);
        mGridOrderedLauncherItems = new ArrayList<>();
        mLauncherViewModel = launcherViewModel;
        mAppGridMode = mode;
    }

    /**
     * Updates the dimension measurements of the app items and app grid bounds.
     *
     * To dispatch the UI changes, the recyclerview needs to call {@link RecyclerView#setAdapter}
     * after calling this method to recreate the view holders.
     */
    public void updateViewHolderDimensions(Rect pageBound, int appItemWidth, int appItemHeight) {
        mPageBound = pageBound;
        mAppItemWidth = appItemWidth;
        mAppItemHeight = appItemHeight;
    }

    /**
     * Updates the current driving restriction to {@code isDistractionOptimizationRequired}, then
     * rebind the view holders.
     */
    public void setIsDistractionOptimizationRequired(boolean isDistractionOptimizationRequired) {
        mIsDistractionOptimizationRequired = isDistractionOptimizationRequired;
        // notifyDataSetChanged will rebind distraction optimization to all app items
        notifyDataSetChanged();
    }

    /**
     * Updates the current app grid mode to {@code mode}, then
     * rebind the view holders.
     */
    public void setMode(Mode mode) {
        mAppGridMode = mode;
        notifyDataSetChanged();
    }

    /**
     * Sets a new list of launcher items to be displayed in the app grid.
     * This should only be called by onChanged() in the observer as a response to data change in the
     * adapter's LauncherViewModel.
     */
    public void setLauncherItems(List<LauncherItem> launcherItems) {
        mLauncherItems = launcherItems;
        int newSnapPosition = mSnapCallback.getSnapPosition();
        if (newSnapPosition != 0 && newSnapPosition >= getItemCount()) {
            // in case user deletes the only app item on the last page, the page should snap to the
            // last icon on the second last page.
            mSnapCallback.notifySnapToPosition(getItemCount() - 1);
        }
        dispatchUpdates();
    }

    @Override
    public int getItemViewType(int position) {
        if (position == 0 && hasRecentlyUsedApps()) {
            return RECENT_APPS_TYPE;
        }
        return APP_ITEM_TYPE;
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        if (viewType == RECENT_APPS_TYPE) {
            View view =
                    mInflater.inflate(R.layout.recent_apps_row, parent, /* attachToRoot= */ false);
            return new RecentAppsRowViewHolder(view, mContext);
        } else {
            View view = mInflater.inflate(R.layout.app_item, parent, /* attachToRoot= */ false);
            LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
                    mAppItemWidth, mAppItemHeight);
            view.setLayoutParams(layoutParams);
            return new AppItemViewHolder(view, mContext, mDragCallback, mSnapCallback);
        }
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        AppItemViewHolder viewHolder = (AppItemViewHolder) holder;
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
                mAppItemWidth, mAppItemHeight);
        holder.itemView.setLayoutParams(layoutParams);

        AppItemViewHolder.BindInfo bindInfo = new AppItemViewHolder.BindInfo(
                mIsDistractionOptimizationRequired, mPageBound, mAppGridMode);
        int adapterIndex = mIndexingHelper.gridPositionToAdaptorIndex(position);
        if (adapterIndex >= mLauncherItems.size()) {
            // the current view holder is an empty item used to pad the last page.
            viewHolder.bind(null, bindInfo);
            return;
        }
        AppItem item = (AppItem) mLauncherItems.get(adapterIndex);
        viewHolder.bind(item.getAppMetaData(), bindInfo);
    }

    /**
     * Sets the layout direction of the indexing helper.
     */
    public void setLayoutDirection(int layoutDirection) {
        mIndexingHelper.setLayoutDirection(layoutDirection);
    }

    @Override
    public int getItemCount() {
        return getItemCountInternal(getLauncherItemsCount());
    }

    /** Returns the item count including padded spaces on the last page */
    private int getItemCountInternal(int unpaddedItemCount) {
        // item count should always be a multiple of block size to ensure pagination
        // is done properly. Extra spaces will have empty ViewHolders binded.
        float pageFraction = (float) unpaddedItemCount / (mNumOfCols * mNumOfRows);
        int pageCount = (int) Math.ceil(pageFraction);
        return pageCount * mNumOfCols * mNumOfRows;
    }

    public int getLauncherItemsCount() {
        return mLauncherItems == null ? 0 : mLauncherItems.size();
    }

    /**
     * Calculates the number of pages required to fit the all app items in the recycler view, with
     * minimum of 1 page when no items have been added to data model.
     */
    public int getPageCount() {
        return getPageCount(/* unpaddedItemCount */ getItemCount());
    }

    /**
     * Calculates the number of pages required to fit {@code unpaddedItemCount} number of app items.
     */
    public int getPageCount(int unpaddedItemCount) {
        int pageCount = getItemCountInternal(unpaddedItemCount) / (mNumOfRows * mNumOfCols);
        return Math.max(pageCount, 1);
    }

    /**
     * Return the offset bound direction of the given gridPosition.
     */
    @AppItemBoundDirection
    public int getOffsetBoundDirection(int gridPosition) {
        return mIndexingHelper.getOffsetBoundDirection(gridPosition);
    }


    private boolean hasRecentlyUsedApps() {
        // TODO (b/266988404): deprecate ui logic associated with recently used apps
        return false;
    }

    /**
     * Sets the cached drag start position to {@code gridPosition}.
     */
    public void setDragStartPoint(int gridPosition) {
        mPageScrollDestination = mIndexingHelper.roundToFirstIndexOnPage(gridPosition);
        mSnapCallback.notifySnapToPosition(mPageScrollDestination);
    }

    /**
     * The magical function that writes the new order to proto datastore.
     *
     * There should not be any calls to update RecyclerView, such as via notifyDatasetChanged in
     * this method since UI changes relating to data model should be handled by data observer.
     */
    public void moveAppItem(int gridPositionFrom, int gridPositionTo) {
        int adaptorIndexFrom = mIndexingHelper.gridPositionToAdaptorIndex(gridPositionFrom);
        int adaptorIndexTo = mIndexingHelper.gridPositionToAdaptorIndex(gridPositionTo);
        mPageScrollDestination = mIndexingHelper.roundToFirstIndexOnPage(gridPositionTo);
        mSnapCallback.notifySnapToPosition(mPageScrollDestination);

        // we need to move package to target index even if the from and to index are the same to
        // ensure dispatchLayout gets called to re-anchor the recyclerview to current page.
        AppItem selectedApp = (AppItem) mLauncherItems.get(adaptorIndexFrom);
        mLauncherViewModel.setAppPosition(adaptorIndexTo, selectedApp.getAppMetaData());
    }


    /**
     * Updates page scroll destination after user has held the app item at the end of page for
     * longer than the scroll dispatch threshold.
     */
    public void updatePageScrollDestination(boolean scrollToNextPage) {
        int newDestination;
        int blockSize = mNumOfCols * mNumOfRows;
        if (scrollToNextPage) {
            newDestination = mPageScrollDestination + blockSize;
            mPageScrollDestination = (newDestination >= getItemCount()) ? mPageScrollDestination :
                    mIndexingHelper.roundToLastIndexOnPage(newDestination);
        } else {
            newDestination = mPageScrollDestination - blockSize;
            mPageScrollDestination = (newDestination < 0) ? mPageScrollDestination :
                    mIndexingHelper.roundToFirstIndexOnPage(newDestination);
        }
        mSnapCallback.notifySnapToPosition(mPageScrollDestination);
    }

    /**
     * Returns the last cached page scroll destination.
     */
    public int getPageScrollDestination() {
        return mPageScrollDestination;
    }

    /**
     * Dispatches the paged reordering animation using async list differ, based on
     * the current adapter order when the method is called.
     */
    private void dispatchUpdates() {
        List<LauncherItem> newAppsList = new ArrayList<>();
        // we first need to pad the empty items on the last page
        for (int i = 0; i < getItemCount(); i++) {
            newAppsList.add(getEmptyLauncherItem());
        }

        for (int i = 0; i < mLauncherItems.size(); i++) {
            newAppsList.set(mIndexingHelper.adaptorIndexToGridPosition(i), mLauncherItems.get(i));
        }
        LauncherItemDiffCallback callback = new LauncherItemDiffCallback(
                /* oldList */ mGridOrderedLauncherItems, /* newList */ newAppsList);
        DiffUtil.DiffResult result = DiffUtil.calculateDiff(callback);

        mGridOrderedLauncherItems.clear();
        mGridOrderedLauncherItems.addAll(newAppsList);
        result.dispatchUpdatesTo(this);
    }

    private LauncherItem getEmptyLauncherItem() {
        return new AppItem(/* packageName*/ "", /* className */ "", /* displayName */ "",
                /* appMetaData */ null);
    }

    /**
     * Returns the grid position of the next intended rotary focus view. This should follow the
     * same logical order as the adapter indexes.
     */
    public int getNextRotaryFocus(int focusedGridPosition, int direction) {
        int targetAdapterIndex = mIndexingHelper.gridPositionToAdaptorIndex(focusedGridPosition)
                + (direction == View.FOCUS_FORWARD ? 1 : -1);
        if (targetAdapterIndex < 0 || targetAdapterIndex >= getLauncherItemsCount()) {
            return focusedGridPosition;
        }
        return mIndexingHelper.adaptorIndexToGridPosition(targetAdapterIndex);
    }
}
