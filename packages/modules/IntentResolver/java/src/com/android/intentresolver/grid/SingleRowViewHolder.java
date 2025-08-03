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
import android.view.ViewGroup;

/** Holder for a group of items displayed in a single row of the {@link ChooserGridAdapter}. */
public final class SingleRowViewHolder extends ItemGroupViewHolder {
    private final ViewGroup mRow;

    public SingleRowViewHolder(ViewGroup row, int cellCount, int viewType) {
        super(cellCount, row, viewType);

        this.mRow = row;
    }

    /** Get the group of all views in this holder. */
    public ViewGroup getViewGroup() {
        return mRow;
    }

    /**
     * Get the group of views for the row containing the specified cell index.
     * TODO: unclear if that's what this `index` meant. It doesn't matter for our "single row"
     * holders, and it doesn't look like this is an override from some other interface; maybe we can
     * just remove?
     */
    public ViewGroup getRowByIndex(int index) {
        return mRow;
    }

    /** Get the group of views for the specified {@code rowNumber}, if any. */
    public ViewGroup getRow(int rowNumber) {
        if (rowNumber == 0) {
            return mRow;
        }
        return null;
    }

    /**
     * @param index the index of the cell to add the view into.
     * @param v the view to add into the cell.
     */
    public ViewGroup addView(int index, View v) {
        mRow.addView(v);
        mCells[index] = v;

        return mRow;
    }

    /**
     * @param i the index of the cell containing the view to modify.
     * @param visibility the new visibility to set on the view with the specified index.
     */
    public void setViewVisibility(int i, int visibility) {
        getView(i).setVisibility(visibility);
    }
}
