/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.car.settings.common;

import android.content.Context;
import android.util.TypedValue;
import android.view.View;

import androidx.annotation.DrawableRes;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceGroupAdapter;
import androidx.preference.PreferenceViewHolder;
import androidx.recyclerview.widget.RecyclerView;

import com.android.car.settings.R;

/** RecyclerView adapter that supports single-preference highlighting. */
public class HighlightablePreferenceGroupAdapter extends PreferenceGroupAdapter {

    @DrawableRes
    private final int mNormalBackgroundRes;
    @DrawableRes
    private final int mHighlightBackgroundRes;
    private int mHighlightPosition = RecyclerView.NO_POSITION;

    public HighlightablePreferenceGroupAdapter(PreferenceGroup preferenceGroup) {
        super(preferenceGroup);
        Context context = preferenceGroup.getContext();
        TypedValue outValue = new TypedValue();
        context.getTheme().resolveAttribute(android.R.attr.selectableItemBackground,
                outValue, /* resolveRefs= */ true);
        mNormalBackgroundRes = outValue.resourceId;
        mHighlightBackgroundRes = R.drawable.preference_highlight_default;
    }

    public HighlightablePreferenceGroupAdapter(PreferenceGroup preferenceGroup,
            @DrawableRes int normalBackgroundRes, @DrawableRes int highlightBackgroundRes) {
        super(preferenceGroup);
        mNormalBackgroundRes = normalBackgroundRes;
        mHighlightBackgroundRes = highlightBackgroundRes;
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder, int position) {
        super.onBindViewHolder(holder, position);
        updateBackground(holder, position);
    }

    private void updateBackground(PreferenceViewHolder holder, int position) {
        View v = holder.itemView;
        if (position == mHighlightPosition) {
            addHighlightBackground(v);
        } else if (hasHighlightBackground(v)) {
            removeHighlightBackground(v);
        }
    }

    /**
     * Requests that a particular preference be highlighted. This will remove the highlight from
     * the previously highlighted preference.
     */
    public void requestHighlight(View root, RecyclerView recyclerView, String key) {
        if (root == null || recyclerView == null) {
            return;
        }
        int position = getPreferenceAdapterPosition(key);
        if (position < 0) {
            // Item is not in the list - clearing the previous highlight without setting a new one.
            clearHighlight(root);
            return;
        }
        root.post(() -> {
            recyclerView.scrollToPosition(position);
            int oldPosition = mHighlightPosition;
            mHighlightPosition = position;
            notifyItemChanged(oldPosition);
            notifyItemChanged(position);
        });
    }

    /**
     * Removes the highlight from the currently highlighted preference.
     */
    public void clearHighlight(View root) {
        if (root == null) {
            return;
        }
        root.post(() -> {
            if (mHighlightPosition < 0) {
                return;
            }
            int oldPosition = mHighlightPosition;
            mHighlightPosition = RecyclerView.NO_POSITION;
            notifyItemChanged(oldPosition);
        });
    }

    private void addHighlightBackground(View v) {
        v.setTag(R.id.preference_highlighted, true);
        v.setBackgroundResource(mHighlightBackgroundRes);
    }

    private void removeHighlightBackground(View v) {
        v.setTag(R.id.preference_highlighted, false);
        v.setBackgroundResource(mNormalBackgroundRes);
    }

    private boolean hasHighlightBackground(View v) {
        return Boolean.TRUE.equals(v.getTag(R.id.preference_highlighted));
    }
}
