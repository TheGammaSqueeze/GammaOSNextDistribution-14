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

import android.view.MotionEvent;
import android.view.View;

import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;


public class TaskTouchListener implements View.OnTouchListener {
    private final ItemTouchHelper mTaskItemTouchHelper;
    private final RecyclerView.ViewHolder mViewHolder;
    private final float mStartSwipeThreshold;
    private float mStartY;

    public TaskTouchListener(float startSwipeThreshold, ItemTouchHelper itemTouchHelper,
            RecyclerView.ViewHolder viewHolder) {
        mTaskItemTouchHelper = itemTouchHelper;
        mViewHolder = viewHolder;
        mStartSwipeThreshold = startSwipeThreshold;
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                mStartY = event.getY();
                break;
            case MotionEvent.ACTION_MOVE:
                if (mStartSwipeThreshold < Math.abs(mStartY - event.getY())) {
                    mTaskItemTouchHelper.startSwipe(mViewHolder);
                    return true;
                }
                break;
        }
        return false;
    }
}
