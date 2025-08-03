/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.intentresolver.grid;

import android.view.View;
import android.view.View.MeasureSpec;
import android.view.ViewGroup;

/**
 * Used to bind types for group of items including:
 * {@link ChooserGridAdapter#VIEW_TYPE_DIRECT_SHARE},
 * and {@link ChooserGridAdapter#VIEW_TYPE_CALLER_AND_RANK}.
 */
public abstract class ItemGroupViewHolder extends ViewHolderBase {
    protected int mMeasuredRowHeight;
    private int[] mItemIndices;
    protected final View[] mCells;
    private final int mColumnCount;

    public ItemGroupViewHolder(int cellCount, View itemView, int viewType) {
        super(itemView, viewType);
        this.mCells = new View[cellCount];
        this.mItemIndices = new int[cellCount];
        this.mColumnCount = cellCount;
    }

    public abstract ViewGroup addView(int index, View v);

    public abstract ViewGroup getViewGroup();

    public abstract ViewGroup getRowByIndex(int index);

    public abstract ViewGroup getRow(int rowNumber);

    public abstract void setViewVisibility(int i, int visibility);

    public int getColumnCount() {
        return mColumnCount;
    }

    public void measure() {
        final int spec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
        getViewGroup().measure(spec, spec);
        mMeasuredRowHeight = getViewGroup().getMeasuredHeight();
    }

    public int getMeasuredRowHeight() {
        return mMeasuredRowHeight;
    }

    public void setItemIndex(int itemIndex, int listIndex) {
        mItemIndices[itemIndex] = listIndex;
    }

    public int getItemIndex(int itemIndex) {
        return mItemIndices[itemIndex];
    }

    public View getView(int index) {
        return mCells[index];
    }
}
