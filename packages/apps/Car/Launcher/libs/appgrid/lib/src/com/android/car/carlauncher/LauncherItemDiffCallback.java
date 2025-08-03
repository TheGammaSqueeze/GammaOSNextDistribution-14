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

import androidx.recyclerview.widget.DiffUtil;

import java.util.List;

/**
 * A DiffUtil callback
 */
public class LauncherItemDiffCallback extends DiffUtil.Callback {

    private final List<LauncherItem> mOldList;
    private final List<LauncherItem> mNewList;

    public LauncherItemDiffCallback(List<LauncherItem> oldList, List<LauncherItem> newList) {
        mOldList = oldList;
        mNewList = newList;
    }

    @Override
    public int getOldListSize() {
        return mOldList.size();
    }

    @Override
    public int getNewListSize() {
        return mNewList.size();
    }

    @Override
    public boolean areItemsTheSame(int oldListPosition, int newListPosition) {
        return mOldList.get(oldListPosition).areContentsTheSame(mNewList.get(newListPosition));
    }

    @Override
    public boolean areContentsTheSame(int oldListPosition, int newListPosition) {
        return mOldList.get(oldListPosition).areContentsTheSame(mNewList.get(newListPosition));
    }
}
