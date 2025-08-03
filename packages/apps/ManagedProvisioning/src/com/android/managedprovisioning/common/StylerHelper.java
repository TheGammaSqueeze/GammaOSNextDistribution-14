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

package com.android.managedprovisioning.common;

import static java.util.Objects.requireNonNull;

import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.android.managedprovisioning.R;

import com.google.android.setupdesign.util.HeaderAreaStyler;
import com.google.android.setupdesign.util.ItemStyler;

/**
 * Utils for handling styling provided by the setupdesign library.
 */
public class StylerHelper {

    /**
     * Applies styling to a list item.
     *
     * <p>The list item must contain two {@link TextView}s, {@code R.id.sud_items_title} and {@code
     * R.id.sud_items_summary}.
     *
     * <p>Since the styles are applied using the setupdesign library, which additionally applies
     * padding and margin, this method also maintains the original {@code listItem} and two {@link
     * TextView} padding and margin.
     *
     * @see ItemStyler#applyPartnerCustomizationItemStyle(View)
     */
    public void applyListItemStyling(View listItem, ViewGroup.LayoutParams listItemViewParamsCopy) {
        requireNonNull(listItem);
        View title = listItem.findViewById(R.id.sud_items_title);
        View summary = listItem.findViewById(R.id.sud_items_summary);
        SpacingInfo titleSpacing = new SpacingInfo(title, title.getLayoutParams());
        SpacingInfo summarySpacing = new SpacingInfo(summary, summary.getLayoutParams());
        SpacingInfo listItemSpacing = new SpacingInfo(listItem, listItemViewParamsCopy);
        ItemStyler.applyPartnerCustomizationItemStyle(listItem);
        titleSpacing.applySpacingToView(title);
        summarySpacing.applySpacingToView(summary);
        listItemSpacing.applySpacingToView(listItem);
        listItem.setMinimumHeight(0);
    }

    /**
     *  Applies styling to a header {@link TextView}.
     *
     *  <p>Since the style is applied using the setupdesign library, which additionally applies
     *  padding and margin, this method also maintains the original {@code header} padding and
     *  margin.
     *
     * @see HeaderAreaStyler#applyPartnerCustomizationHeaderHeavyStyle(TextView)
     */
    public void applyHeaderStyling(TextView header, ViewGroup.LayoutParams headerViewParamsCopy) {
        requireNonNull(header);
        SpacingInfo headerSpacing = new SpacingInfo(header, headerViewParamsCopy);
        HeaderAreaStyler.applyPartnerCustomizationHeaderStyle(header);
        headerSpacing.applySpacingToView(header);
    }

    /**
     * Helper which caches the paddings and {@link android.view.ViewGroup.LayoutParams} of a
     * {@link View} and applies them back via {@link #applySpacingToView(View)}.
     */
    private static class SpacingInfo {
        private final int mPaddingLeft;
        private final int mPaddingTop;
        private final int mPaddingRight;
        private final int mPaddingBottom;
        private final ViewGroup.LayoutParams mLayoutParams;

        /**
         * Constructs a {@link SpacingInfo} which caches the {@code view}'s paddings and {@link
         * android.view.ViewGroup.LayoutParams}.
         */
        SpacingInfo(View view, ViewGroup.LayoutParams layoutParamsCopy) {
            requireNonNull(view);
            mPaddingLeft = view.getPaddingLeft();
            mPaddingTop = view.getPaddingTop();
            mPaddingRight = view.getPaddingRight();
            mPaddingBottom = view.getPaddingBottom();
            mLayoutParams = layoutParamsCopy;
        }

        void applySpacingToView(View view) {
            requireNonNull(view);
            applyPaddingToView(view);
            applyLayoutParamsToView(view);
        }

        private void applyPaddingToView(View view) {
            view.setPadding(mPaddingLeft, mPaddingTop, mPaddingRight, mPaddingBottom);
        }

        private void applyLayoutParamsToView(View view) {
            view.setLayoutParams(mLayoutParams);
        }
    }
}
