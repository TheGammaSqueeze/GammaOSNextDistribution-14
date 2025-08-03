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

import android.app.Activity;
import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.WindowMetrics;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.Px;
import androidx.recyclerview.widget.RecyclerView;

import com.android.car.carlauncher.R;
import com.android.car.carlauncher.recents.RecentTasksViewModel;
import com.android.car.carlauncher.recents.RecentsUtils;
import com.android.internal.annotations.VisibleForTesting;

import org.jetbrains.annotations.NotNull;

/**
 * RecyclerView that centers the first and last elements of the Recent task list by adding
 * appropriate padding.
 */
public class RecentsRecyclerView extends RecyclerView {
    private final int mFirstItemWidth;
    private final int mColSpacing;
    private final int mItemWidth;
    private RecentTasksViewModel mRecentTasksViewModel;
    private WindowMetrics mWindowMetrics;

    public RecentsRecyclerView(@NonNull Context context) {
        this(context, /* attrs= */ null);
    }

    public RecentsRecyclerView(@NonNull Context context,
            @Nullable AttributeSet attrs) {
        this(context, attrs, androidx.recyclerview.R.attr.recyclerViewStyle);
    }


    public RecentsRecyclerView(@NonNull Context context, @Nullable AttributeSet attrs,
            int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mRecentTasksViewModel = RecentTasksViewModel.getInstance();
        if (context instanceof Activity) {
            // needed for testing when using a mock context
            mWindowMetrics = ((Activity) context).getWindowManager().getCurrentWindowMetrics();
        }
        mFirstItemWidth = getResources().getDimensionPixelSize(R.dimen.recent_task_width_first);
        mItemWidth = getResources().getDimensionPixelSize(R.dimen.recent_task_width);
        mColSpacing = getResources().getDimensionPixelSize(R.dimen.recent_task_col_space);
    }

    @VisibleForTesting
    public RecentsRecyclerView(@NonNull Context context, RecentTasksViewModel recentTasksViewModel,
            WindowMetrics windowMetrics) {
        this(context);
        mRecentTasksViewModel = recentTasksViewModel;
        mWindowMetrics = windowMetrics;
    }

    /**
     * Handles {@link View.FOCUS_FORWARD} and {@link View.FOCUS_BACKWARD} events.
     * For the 2 elements, A and B, the focus should go forward from A's dismiss button to
     * A's thumbnail to B's dismiss button to B's thumbnail and backward in the inverted order.
     */
    @Override
    public View focusSearch(View focused, int direction) {
        if (direction != View.FOCUS_FORWARD && direction != View.FOCUS_BACKWARD) {
            return super.focusSearch(focused, direction);
        }
        boolean shouldGoToNextView = shouldGoToNextView(direction);
        ViewHolder focusedViewHolder = findContainingViewHolder(focused);
        if (focusedViewHolder == null) {
            return null;
        }
        if (focusedViewHolder instanceof BaseTaskViewHolder) {
            View taskDismissButton = focusedViewHolder.itemView.findViewById(
                    R.id.task_dismiss_button);
            View taskThumbnail = focusedViewHolder.itemView.findViewById(R.id.task_thumbnail);
            if (focused == taskDismissButton && shouldGoToNextView) {
                return taskThumbnail;
            }
            if (focused == taskThumbnail && !shouldGoToNextView) {
                return taskDismissButton;
            }
        }
        int position = focusedViewHolder.getAbsoluteAdapterPosition();
        if (position == NO_POSITION) {
            return null;
        }

        return getNextFocusView(direction,
                shouldGoToNextView ? ++position : --position, /* tryScrolling= */ true);
    }

    /**
     * Should be called to find the view in the next viewHolder to take focus.
     *
     * @param nextPosition next view holder position to be focused.
     * @param tryScrolling should try to scroll to find the next view holder.
     *                     This would only happen if view holder at {@code nextPosition} is null.
     * @return the next view to be focused.
     */
    @Nullable
    private View getNextFocusView(int direction, int nextPosition, boolean tryScrolling) {
        ViewHolder nextFocusViewHolder = findViewHolderForAdapterPosition(nextPosition);

        if (nextFocusViewHolder == null) {
            if (tryScrolling) {
                this.smoothScrollBy(direction == View.FOCUS_FORWARD ? mItemWidth : -mItemWidth, 0);
                this.addOnScrollListener(new OnScrollListener() {
                    @Override
                    public void onScrollStateChanged(@NotNull RecyclerView recyclerView,
                            int newState) {
                        super.onScrollStateChanged(recyclerView, newState);
                        if (newState == SCROLL_STATE_IDLE) {
                            RecentsRecyclerView.this.removeOnScrollListener(this);
                            View nextFocusedView = getNextFocusView(direction, nextPosition,
                                    /* tryScrolling= */ false);
                            if (nextFocusedView != null) {
                                nextFocusedView.requestFocus();
                            }
                        }
                    }
                });
            }
            return null;
        }
        if (nextFocusViewHolder instanceof BaseTaskViewHolder) {
            return shouldGoToNextView(direction)
                    ? nextFocusViewHolder.itemView.findViewById(R.id.task_dismiss_button)
                    : nextFocusViewHolder.itemView.findViewById(R.id.task_thumbnail);
        }
        if (nextFocusViewHolder instanceof ClearAllViewHolder) {
            return nextFocusViewHolder.itemView.findViewById(R.id.recents_clear_all_button);
        }
        return nextFocusViewHolder.itemView;

    }

    /**
     * @return {@code true} if the {@code direction} is meant to move the focus to next
     * view {@code false} for previous view.
     */
    private boolean shouldGoToNextView(int direction) {
        return (direction == View.FOCUS_FORWARD)
                == !RecentsUtils.areItemsRightToLeft(this);
    }

    /**
     * Resets the RecyclerView's start and end padding based on the Task list size,
     * recent task view width and window width where Recents activity is drawn.
     */
    public void resetPadding() {
        if (mRecentTasksViewModel.getRecentTasksSize() == 0) {
            setPadding(/* firstItemPadding= */ 0, /* lastItemPadding= */ 0);
            return;
        }
        setPadding(/* firstItemPadding= */ calculateFirstItemPadding(
                        mWindowMetrics.getBounds().width()),
                /* lastItemPadding= */ calculateLastItemPadding());
    }

    @Px
    @VisibleForTesting
    int calculateFirstItemPadding(@Px int windowWidth) {
        // This assumes that RecyclerView's width is same as the windowWidth. This is to add padding
        // before RecyclerView or its children is drawn.
        return Math.max(0, (windowWidth - (mFirstItemWidth + mColSpacing)) / 2);
    }

    @Px
    @VisibleForTesting
    int calculateLastItemPadding() {
        // no-op
        return 0;
    }

    /**
     * @param firstItemPadding padding set to recyclerView to fit the first item.
     * @param lastItemPadding  padding set to recyclerView to fit the last item.
     */
    private void setPadding(@Px int firstItemPadding, @Px int lastItemPadding) {
        boolean shouldBeReversed = RecentsUtils.areItemsRightToLeft(this);
        setPaddingRelative(
                /* start= */ shouldBeReversed ? lastItemPadding : firstItemPadding,
                getPaddingTop(),
                /* end= */ shouldBeReversed ? firstItemPadding : lastItemPadding,
                getPaddingBottom());
    }
}
