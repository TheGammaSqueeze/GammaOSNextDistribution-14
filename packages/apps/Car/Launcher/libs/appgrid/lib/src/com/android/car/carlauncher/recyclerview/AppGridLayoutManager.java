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

import static com.android.car.carlauncher.AppGridConstants.PageOrientation;
import static com.android.car.carlauncher.AppGridConstants.isHorizontal;

import android.content.Context;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

/**
 * Grid style layout manager for AppGridRecyclerView.
 */
public class AppGridLayoutManager extends GridLayoutManager {
    boolean mShouldLayoutChildren = true;

    public AppGridLayoutManager(Context context, int numOfCols, int numOfRows,
            @PageOrientation int pageOrientation) {
        super(context, isHorizontal(pageOrientation) ? numOfRows : numOfCols,
                isHorizontal(pageOrientation)
                        ? GridLayoutManager.HORIZONTAL : GridLayoutManager.VERTICAL, false);
    }

    /**
     * By default, RecyclerView and GridLayoutManager performs many predictive animations and
     * predictive scrolling in response to MotionEvents, such as events emitted by DragEvent.
     *
     * During drag and drop operations, we don't expect any predictive scrolling or margin
     * adjustment. By calling #setShouldLayoutChildren(false), we let the animation be exclusively
     * handled by ViewHolders themselves, rather than the parent ViewGroup.
     */
    public void setShouldLayoutChildren(boolean shouldLayoutChildren) {
        mShouldLayoutChildren = shouldLayoutChildren;
    }

    @Override
    public void onLayoutChildren(RecyclerView.Recycler recycler, RecyclerView.State state) {
        if (mShouldLayoutChildren) {
            super.onLayoutChildren(recycler, state);
        }
    }
}
