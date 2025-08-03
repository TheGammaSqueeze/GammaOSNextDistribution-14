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

import android.graphics.Canvas;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.android.car.carlauncher.recents.RecentTasksViewModel;

public class TaskTouchHelperCallback extends ItemTouchHelper.SimpleCallback {
    private final RecentTasksViewModel mRecentTasksViewModel;
    private final float mSwipeThreshold;

    public TaskTouchHelperCallback(int dragDirs, int swipeDirs, float swipeThreshold) {
        super(dragDirs, swipeDirs);
        mSwipeThreshold = swipeThreshold;
        mRecentTasksViewModel = RecentTasksViewModel.getInstance();
    }

    @Override
    public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder,
            int direction) {
        mRecentTasksViewModel.removeTaskFromRecents(viewHolder.getAbsoluteAdapterPosition());
    }

    @Override
    public boolean onMove(@NonNull RecyclerView recyclerView,
            @NonNull RecyclerView.ViewHolder viewHolder,
            @NonNull RecyclerView.ViewHolder viewHolder1) {
        // no op
        return false;
    }

    @Override
    public boolean isItemViewSwipeEnabled() {
        // To avoid clicking on empty space in the ViewHolder and initiating a swipe,
        // call startSwipe() when the ViewHolder's child views individually receive touch events.
        return false;
    }

    @Override
    public float getSwipeThreshold(@NonNull RecyclerView.ViewHolder viewHolder) {
        return mSwipeThreshold;
    }

    @Override
    public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView recyclerView,
            @NonNull RecyclerView.ViewHolder viewHolder,
            float dX, float dY, int actionState, boolean isCurrentlyActive) {
        super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState,
                isCurrentlyActive);
        // Change the alpha of the task depending on how far it has be moved from
        // its original position.
        float threshold = recyclerView.getHeight() * getSwipeThreshold(viewHolder);
        // Scale the change in position dY to alpha value(0-1)
        float newVal = 1 - (Math.abs(dY) / threshold);
        viewHolder.itemView.setAlpha(newVal);
    }

    @Override
    public void clearView(@NonNull RecyclerView recyclerView,
            @NonNull RecyclerView.ViewHolder viewHolder) {
        super.clearView(recyclerView, viewHolder);
        // Reset the alpha because the viewHolder would be reused for another task
        viewHolder.itemView.setAlpha(1);
    }
}
