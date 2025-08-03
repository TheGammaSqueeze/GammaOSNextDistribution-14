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
package com.android.wallpaper.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.wallpaper.R;
import com.android.wallpaper.util.SystemColors;

/**
 * Custom layout for duo tabs.
 */
public final class DuoTabs extends FrameLayout {

    public static final int TAB_PRIMARY = 0;
    public static final int TAB_SECONDARY = 1;

    @IntDef({TAB_PRIMARY, TAB_SECONDARY})
    public @interface Tab {
    }

    public interface OnTabSelectedListener {
        void onTabSelected(@Tab int tab);
    }

    private OnTabSelectedListener mOnTabSelectedListener;
    @Nullable private final FrameLayout mPrimaryTabContainer;
    @Nullable private final FrameLayout mSecondaryTabContainer;
    private final TextView mPrimaryTabText;
    private final TextView mSecondaryTabText;

    @Tab private int mCurrentOverlayTab;

    private final int mSelectedTabDrawable;

    private final int mNonSelectedTabDrawable;

    private final int mSelectedTabTextColor;

    private final int mNonSelectedTabTextColor;

    private final boolean mShouldUseShortTabs;

    public DuoTabs(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);

        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.DuoTabs, 0, 0);
        mShouldUseShortTabs = a.getBoolean(R.styleable.DuoTabs_should_use_short_tabs, false);
        mSelectedTabDrawable = a.getResourceId(R.styleable.DuoTabs_selected_tab_drawable,
                R.drawable.duo_tabs_preview_button_indicator_background);
        mNonSelectedTabDrawable = a.getResourceId(R.styleable.DuoTabs_non_selected_tab_drawable,
                R.drawable.duo_tabs_preview_button_background);
        mSelectedTabTextColor = a.getColor(R.styleable.DuoTabs_selected_tab_text_color,
                getResources().getColor(R.color.text_color_on_accent));
        mNonSelectedTabTextColor = a.getColor(R.styleable.DuoTabs_non_selected_tab_text_color,
                SystemColors.getColor(getContext(), android.R.attr.textColorPrimary));
        a.recycle();

        LayoutInflater.from(context).inflate(
                mShouldUseShortTabs ? R.layout.duo_tabs_short : R.layout.duo_tabs,
                this,
                true);

        mPrimaryTabText = findViewById(R.id.tab_primary);
        mSecondaryTabText = findViewById(R.id.tab_secondary);
        mPrimaryTabContainer = mShouldUseShortTabs ? findViewById(R.id.tab_primary_container)
                : null;
        mSecondaryTabContainer = mShouldUseShortTabs ? findViewById(R.id.tab_secondary_container)
                : null;
        if (mShouldUseShortTabs && mPrimaryTabContainer != null && mSecondaryTabContainer != null) {
            mPrimaryTabContainer.setOnClickListener(v -> selectTab(TAB_PRIMARY));
            mSecondaryTabContainer.setOnClickListener(v -> selectTab(TAB_SECONDARY));
        } else {
            mPrimaryTabText.setOnClickListener(v -> selectTab(TAB_PRIMARY));
            mSecondaryTabText.setOnClickListener(v -> selectTab(TAB_SECONDARY));
        }
    }

    public void setTabText(String primaryTabText, String secondaryTabText) {
        mPrimaryTabText.setText(primaryTabText);
        mSecondaryTabText.setText(secondaryTabText);
    }

    public void selectTab(@Tab int tab) {
        updateTabIndicator(tab);
        if (mOnTabSelectedListener != null) {
            mOnTabSelectedListener.onTabSelected(tab);
        }
        mCurrentOverlayTab = tab;
    }

    public void setOnTabSelectedListener(
            OnTabSelectedListener onTabSelectedListener) {
        mOnTabSelectedListener = onTabSelectedListener;
    }

    /**
     * Update the background color in case the context theme changes.
     */
    public void updateBackgroundColor() {
        mPrimaryTabText.setBackground(null);
        mSecondaryTabText.setBackground(null);
        updateTabIndicator(mCurrentOverlayTab);
    }

    private void updateTabIndicator(@Tab int tab) {
        mPrimaryTabText.setBackgroundResource(
                tab == TAB_PRIMARY
                        ? mSelectedTabDrawable
                        : mNonSelectedTabDrawable);
        mPrimaryTabText.setTextColor(
                tab == TAB_PRIMARY
                        ? mSelectedTabTextColor
                        : mNonSelectedTabTextColor);
        // Set selected for talkback
        if (mShouldUseShortTabs && mPrimaryTabContainer != null) {
            mPrimaryTabContainer.setSelected(tab == TAB_PRIMARY);
        } else {
            mPrimaryTabText.setSelected(tab == TAB_PRIMARY);
        }
        mSecondaryTabText.setBackgroundResource(
                tab == TAB_SECONDARY
                        ? mSelectedTabDrawable
                        : mNonSelectedTabDrawable);
        mSecondaryTabText.setTextColor(
                tab == TAB_SECONDARY
                        ? mSelectedTabTextColor
                        : mNonSelectedTabTextColor);
        // Set selected for talkback
        if (mShouldUseShortTabs && mSecondaryTabContainer != null) {
            mSecondaryTabContainer.setSelected(tab == TAB_SECONDARY);
        } else {
            mSecondaryTabText.setSelected(tab == TAB_SECONDARY);
        }
    }

    public @Tab int getSelectedTab() {
        return mCurrentOverlayTab;
    }
}
