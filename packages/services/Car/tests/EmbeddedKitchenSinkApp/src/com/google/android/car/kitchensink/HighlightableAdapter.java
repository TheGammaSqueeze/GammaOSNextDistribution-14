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

package com.google.android.car.kitchensink;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.View;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.android.car.ui.recyclerview.CarUiListItemAdapter;
import com.android.car.ui.recyclerview.CarUiRecyclerView;
import com.android.car.ui.widget.CarUiTextView;

import java.util.List;

/** RecyclerView adapter that supports single-preference highlighting. */
public class HighlightableAdapter extends CarUiListItemAdapter {

    private static final int INVALID_BACKGROUND_RES = -1;
    private static final String EMPTY_STRING = "";
    @DrawableRes
    private final int mDefaultBackgroundRes;
    @DrawableRes
    private final int mHighlightBackgroundRes;
    private final int mItemHeight;
    private final Drawable mPinIconDrawable;
    private final int mPinIconPadding;
    private String mHighlightTitle = EMPTY_STRING;
    private final List<FragmentListItem> mListItems;
    private final CarUiRecyclerView mRecyclerView;
    private int mHighlightPosition;

    public HighlightableAdapter(Context context, List<FragmentListItem> data,
            CarUiRecyclerView recyclerView) {
        this(context, data, recyclerView, INVALID_BACKGROUND_RES,
                R.drawable.preference_highlight_default);
    }

    public HighlightableAdapter(Context context, List<FragmentListItem> data,
            CarUiRecyclerView recyclerView,
            @DrawableRes int defaultBackgroundRes, @DrawableRes int highlightBackgroundRes) {
        super(data);
        mListItems = data;
        mRecyclerView = recyclerView;
        if (defaultBackgroundRes == INVALID_BACKGROUND_RES) {
            TypedValue outValue = new TypedValue();
            context.getTheme().resolveAttribute(android.R.attr.selectableItemBackground,
                    outValue, /* resolveRefs= */ true);
            mDefaultBackgroundRes = outValue.resourceId;
        } else {
            mDefaultBackgroundRes = defaultBackgroundRes;
        }
        mHighlightBackgroundRes = highlightBackgroundRes;

        mItemHeight = context.getResources().getDimensionPixelSize(
                R.dimen.top_level_preference_height);
        mPinIconDrawable = context.getDrawable(R.drawable.ic_item_pin);
        mPinIconPadding = context.getResources().getDimensionPixelSize(
                R.dimen.top_level_pin_icon_padding);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        super.onBindViewHolder(holder, position);
        holder.itemView.setSelected(mHighlightPosition == position);
        holder.itemView.getLayoutParams().height = mItemHeight;

        //itemView is inflated by CarUiListItemAdapter
        CarUiTextView titleView = holder.itemView.findViewById(R.id.car_ui_list_item_title);
        if (titleView != null) {
            titleView.setEllipsize(TextUtils.TruncateAt.END);
            titleView.setSingleLine(true);
            if (mListItems.get(position).isFavourite()) {
                titleView.setCompoundDrawablesWithIntrinsicBounds(null, null, mPinIconDrawable,
                        null);
                titleView.setPaddingRelative(0, 0, mPinIconPadding, 0);
            } else {
                titleView.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
            }
        }
        updateBackground(holder, position);
    }

    private void updateBackground(RecyclerView.ViewHolder holder, int position) {
        View v = holder.itemView;
        if (mHighlightPosition == position) {
            addHighlightBackground(v);
        } else if (hasHighlightBackground(v)) {
            removeHighlightBackground(v);
        }
    }

    /**
     * Requests that a particular list item be highlighted. This will remove the highlight from
     * the previously highlighted item.
     */
    public void requestHighlight(String fragmentTitle, int newPosition) {
        if (mRecyclerView == null) {
            return;
        }

        if (mHighlightPosition < 0) {
            // Item is not in the list - clearing the previous highlight without setting a new one.
            mHighlightTitle = EMPTY_STRING;
            mRecyclerView.getView().post(() -> notifyItemChanged(mHighlightPosition));
        }

        if (newPosition >= 0) {
            mHighlightTitle = fragmentTitle;
            mRecyclerView.getView().post(() -> {
                notifyItemChanged(mHighlightPosition);
                mHighlightPosition = newPosition;
                mRecyclerView.scrollToPosition(newPosition);
                notifyItemChanged(newPosition);
            });
        }
    }

    private int getPositionFromTitle(String fragmentTitle) {
        for (int i = 0; i < mListItems.size(); i++) {
            String targetText = mListItems.get(i).getTitle().getPreferredText().toString();
            if (targetText.equalsIgnoreCase(fragmentTitle)) {
                return i;
            }
        }
        return RecyclerView.NO_POSITION;
    }

    private void addHighlightBackground(View v) {
        v.setTag(R.id.preference_highlighted, true);
        v.setBackgroundResource(mHighlightBackgroundRes);
    }

    private void removeHighlightBackground(View v) {
        v.setTag(R.id.preference_highlighted, false);
        v.setBackgroundResource(mDefaultBackgroundRes);
    }

    private boolean hasHighlightBackground(View v) {
        return Boolean.TRUE.equals(v.getTag(R.id.preference_highlighted));
    }

    /**
     * Will be called each time the search query is changed.
     */
    public void afterTextChanged() {
        mHighlightPosition = getPositionFromTitle(mHighlightTitle);
        notifyDataSetChanged();
    }

    /**
     * Scrolls to the highlighted item after search ends.
     */
    public void onSearchEnded() {
        mRecyclerView.getView().post(() -> mRecyclerView.scrollToPosition(mHighlightPosition));
    }

    public void afterFavClicked(int from, int to) {
        mHighlightPosition = to;
        notifyItemMoved(from, to);
        notifyItemChanged(to);
        mRecyclerView.scrollToPosition(to);
    }
}
