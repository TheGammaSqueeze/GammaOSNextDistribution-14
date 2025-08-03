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

import androidx.annotation.Nullable;

import com.android.intentresolver.ChooserListAdapter;
import com.android.intentresolver.ResolverListAdapter;

import java.util.function.Consumer;

/**
 * Used to bind types of individual item including
 * {@link ChooserGridAdapter#VIEW_TYPE_NORMAL},
 * {@link ChooserGridAdapter#VIEW_TYPE_CONTENT_PREVIEW},
 * {@link ChooserGridAdapter#VIEW_TYPE_PROFILE},
 * and {@link ChooserGridAdapter#VIEW_TYPE_AZ_LABEL}.
 */
public final class ItemViewHolder extends ViewHolderBase {
    private final ResolverListAdapter.ViewHolder mWrappedViewHolder;

    private int mListPosition = ChooserListAdapter.NO_POSITION;

    public ItemViewHolder(
            View itemView,
            int viewType,
            @Nullable Consumer<Integer> onClick,
            @Nullable Consumer<Integer> onLongClick) {
        super(itemView, viewType);
        mWrappedViewHolder = new ResolverListAdapter.ViewHolder(itemView);

        if (onClick != null) {
            itemView.setOnClickListener(v -> onClick.accept(mListPosition));
        }

        if (onLongClick != null) {
            itemView.setOnLongClickListener(v -> {
                onLongClick.accept(mListPosition);
                return true;
            });
        }
    }

    public void setListPosition(int listPosition) {
        mListPosition = listPosition;
    }
}
