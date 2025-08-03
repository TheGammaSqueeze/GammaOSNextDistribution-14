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

package com.android.car.carlauncher.recents.view;

import android.annotation.IntDef;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.android.car.carlauncher.R;
import com.android.car.carlauncher.recents.RecentTasksViewModel;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;

/**
 * Adapter that is used to display the list of Recent tasks.
 * ViewTypes in this adapter:
 * - FIRST_TASK_ITEM_VIEW_TYPE:  First task has special handling since it is takes up more area.
 * - DEFAULT_TASK_ITEM_VIEW_TYPE: all other view holders that hold a recent task.
 * - CLEAR_ALL_VIEW_TYPE: represents the view that contains the clear all button.
 */
public class RecentTasksAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> implements
        RecentTasksViewModel.RecentTasksChangeListener {
    @IntDef({RecentsItemViewType.DEFAULT_TASK_ITEM_VIEW_TYPE,
            RecentsItemViewType.FIRST_TASK_ITEM_VIEW_TYPE,
            RecentsItemViewType.CLEAR_ALL_VIEW_TYPE})
    @Retention(RetentionPolicy.SOURCE)
    @interface RecentsItemViewType {
        int DEFAULT_TASK_ITEM_VIEW_TYPE = 0;
        int FIRST_TASK_ITEM_VIEW_TYPE = 1;
        int CLEAR_ALL_VIEW_TYPE = 2;
    }

    private static final byte THUMBNAIL_UPDATED = 0x1; // 00000001
    private static final byte ICON_UPDATED = 0x2; // 00000010
    private final RecentTasksViewModel mRecentTasksViewModel;
    private final LayoutInflater mLayoutInflater;
    private final ItemTouchHelper mItemTouchHelper;
    private final View.OnClickListener mClearAllOnClickListener;
    private final float mStartSwipeThreshold;

    public RecentTasksAdapter(Context context, LayoutInflater layoutInflater,
            ItemTouchHelper itemTouchHelper, View.OnClickListener clearAllOnClickListener) {
        this(context, layoutInflater, itemTouchHelper, RecentTasksViewModel.getInstance(),
                clearAllOnClickListener);
    }

    @VisibleForTesting
    public RecentTasksAdapter(Context context, LayoutInflater layoutInflater,
            ItemTouchHelper itemTouchHelper, RecentTasksViewModel recentTasksViewModel,
            View.OnClickListener clearAllOnClickListener) {
        mRecentTasksViewModel = recentTasksViewModel;
        mRecentTasksViewModel.addRecentTasksChangeListener(this);
        mLayoutInflater = layoutInflater;
        mItemTouchHelper = itemTouchHelper;
        mStartSwipeThreshold = context.getResources().getFloat(
                R.dimen.recent_task_start_swipe_threshold);
        mClearAllOnClickListener = clearAllOnClickListener;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent,
            @RecentsItemViewType int viewType) {
        switch (viewType) {
            case RecentsItemViewType.FIRST_TASK_ITEM_VIEW_TYPE:
                return new TaskViewHolder(mLayoutInflater.inflate(R.layout.recent_task_view_first,
                        parent, /* attachToRoot= */ false));
            case RecentsItemViewType.CLEAR_ALL_VIEW_TYPE:
                return new ClearAllViewHolder(
                        mLayoutInflater.inflate(R.layout.recent_clear_all_view, parent,
                                /* attachToRoot= */ false), mClearAllOnClickListener);
            default:
                return new TaskViewHolder(mLayoutInflater.inflate(R.layout.recent_task_view,
                        parent, /* attachToRoot= */ false));
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof TaskViewHolder) {
            TaskViewHolder taskViewHolder = (TaskViewHolder) holder;
            Drawable taskIcon = mRecentTasksViewModel.getRecentTaskIconAt(position);
            Bitmap taskThumbnail = mRecentTasksViewModel.getRecentTaskThumbnailAt(position);
            boolean isDisabled = mRecentTasksViewModel.isRecentTaskDisabled(position);
            View.OnClickListener openTaskClickListener =
                    isDisabled ? mRecentTasksViewModel.getDisabledTaskClickListener(position)
                            : new TaskClickListener(position);
            taskViewHolder.bind(taskIcon, taskThumbnail, isDisabled, openTaskClickListener,
                    /* dismissTaskClickListener= */ new DismissTaskClickListener(position),
                    /* taskTouchListener= */
                    new TaskTouchListener(mStartSwipeThreshold, mItemTouchHelper, holder));
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position,
            @NonNull List<Object> payloads) {
        if (payloads.isEmpty() || !(holder instanceof BaseTaskViewHolder)) {
            super.onBindViewHolder(holder, position, payloads);
            return;
        }
        BaseTaskViewHolder baseTaskViewHolder = (BaseTaskViewHolder) holder;
        payloads.forEach(payload -> {
            if (payload instanceof Byte) {
                byte updateType = (Byte) payload;
                if ((updateType & THUMBNAIL_UPDATED) > 0) {
                    Bitmap taskThumbnail = mRecentTasksViewModel.getRecentTaskThumbnailAt(position);
                    baseTaskViewHolder.updateThumbnail(taskThumbnail);
                }
                if ((updateType & ICON_UPDATED) > 0) {
                    Drawable taskIcon = mRecentTasksViewModel.getRecentTaskIconAt(position);
                    baseTaskViewHolder.updateIcon(taskIcon);
                }
            }
        });
    }

    @Override
    public void onViewAttachedToWindow(@NonNull RecyclerView.ViewHolder holder) {
        super.onViewAttachedToWindow(holder);
        if (holder instanceof TaskViewHolder) {
            ((TaskViewHolder) holder).attachedToWindow();
        }
    }

    @Override
    public void onViewDetachedFromWindow(@NonNull RecyclerView.ViewHolder holder) {
        super.onViewDetachedFromWindow(holder);
        if (holder instanceof TaskViewHolder) {
            ((TaskViewHolder) holder).detachedFromWindow();
        }
    }

    @Override
    public int getItemCount() {
        // +1 to account for clear-all button at the end of the list
        return mRecentTasksViewModel.getRecentTasksSize() + 1;
    }

    @Override
    public int getItemViewType(int position) {
        if (position == 0) {
            return RecentsItemViewType.FIRST_TASK_ITEM_VIEW_TYPE;
        }
        if (position == getItemCount() - 1) {
            // last item is always clear all view
            return RecentsItemViewType.CLEAR_ALL_VIEW_TYPE;
        }
        return RecentsItemViewType.DEFAULT_TASK_ITEM_VIEW_TYPE;
    }

    @Override
    public void onRecentTasksFetched() {
        int tasksCount = mRecentTasksViewModel.getRecentTasksSize();
        if (tasksCount <= 0) {
            return;
        }
        notifyDataSetChanged();
    }

    @Override
    public void onTaskThumbnailChange(int position) {
        this.notifyItemChanged(position, THUMBNAIL_UPDATED);
    }

    @Override
    public void onTaskIconChange(int position) {
        this.notifyItemChanged(position, ICON_UPDATED);
    }

    @Override
    public void onAllRecentTasksRemoved(int countRemoved) {
        this.notifyItemRangeRemoved(0, countRemoved);
    }

    @Override
    public void onRecentTaskRemoved(int position) {
        notifyItemRemoved(position);
        notifyItemRangeChanged(position, mRecentTasksViewModel.getRecentTasksSize() - position);
    }
}

