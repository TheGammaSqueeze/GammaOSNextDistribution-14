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

import androidx.recyclerview.widget.RecyclerView;

/** Base class for all {@link RecyclerView.ViewHolder} types in the {@link ChooserGridAdapter}. */
public abstract class ViewHolderBase extends RecyclerView.ViewHolder {
    private int mViewType;

    ViewHolderBase(View itemView, int viewType) {
        super(itemView);
        this.mViewType = viewType;
    }

    public int getViewType() {
        return mViewType;
    }
}
