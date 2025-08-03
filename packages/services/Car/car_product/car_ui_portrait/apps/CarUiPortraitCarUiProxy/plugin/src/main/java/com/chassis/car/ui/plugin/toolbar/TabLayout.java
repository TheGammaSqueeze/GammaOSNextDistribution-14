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
package com.chassis.car.ui.plugin.toolbar;

import static com.android.car.ui.utils.CarUiUtils.requireViewByRefId;

import android.content.Context;
import android.content.res.Resources;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.chassis.car.ui.plugin.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * Custom tab layout which supports adding tabs dynamically
 *
 * <p>It supports two layout modes:
 * <ul><li>Flexible layout which will fill the width
 * <li>Non-flexible layout which wraps content with a minimum tab width. By setting tab gravity,
 * it can left aligned, right aligned or center aligned.
 *
 * <p>Scrolling function is not supported. If a tab item runs out of the tab layout bound, there
 * is no way to access it. It's better to set the layout mode to flexible in this case.
 *
 * <p>Default tab item inflates from R.layout.car_ui_tab_item, but it also supports custom layout
 * id, by overlaying R.layout.car_ui_tab_item_layout. By doing this, appearance of tab item view
 * can be customized.
 *
 * <p>Touch feedback is using @android:attr/selectableItemBackground.
 */
public class TabLayout extends LinearLayout {
    @LayoutRes
    private final int mTabLayoutRes;
    @NonNull
    private List<com.android.car.ui.toolbar.Tab> mTabs = Collections.emptyList();
    private int mSelectedTab = -1;

    public TabLayout(@NonNull Context context) {
        this(context, null);
    }

    public TabLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TabLayout(@NonNull Context context, @Nullable AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        Resources resources = context.getResources();

        mTabLayoutRes = R.layout.car_ui_portrait_toolbar_tab_item;
    }

    /** Sets the tabs to show */
    public void setTabs(List<com.android.car.ui.toolbar.Tab> tabs, int selectedTab) {
        if (tabs == null) {
            mTabs = Collections.emptyList();
        } else {
            mTabs = Collections.unmodifiableList(new ArrayList<>(tabs));
        }
        mSelectedTab = selectedTab;
        recreateViews();
    }

    public List<com.android.car.ui.toolbar.Tab> getTabs() {
        return mTabs;
    }

    /** Returns the currently selected tab, or -1 if no tabs exist */
    public int getSelectedTab() {
        if (mTabs.isEmpty() && mSelectedTab != -1) {
            throw new IllegalStateException("Selected tab is invalid: " + mSelectedTab);
        }
        return mSelectedTab;
    }

    /**
     * Returns if this TabLayout has tabs. That is, if the most recent call to
     * {@link #setTabs(List, int)} contained a non-empty list.
     */
    public boolean hasTabs() {
        return !mTabs.isEmpty();
    }

    /** Set the tab at given position as the current selected tab. */
    public void selectTab(int position) {
        if (position < 0 || position >= mTabs.size()) {
            throw new IllegalArgumentException("Tab position is invalid: " + position);
        }
        if (position == mSelectedTab) {
            return;
        }

        int oldPosition = mSelectedTab;
        mSelectedTab = position;
        presentTabView(oldPosition);
        presentTabView(position);

        com.android.car.ui.toolbar.Tab tab = mTabs.get(position);
        Consumer<com.android.car.ui.toolbar.Tab> listener = tab.getSelectedListener();
        if (listener != null) {
            listener.accept(tab);
        }
    }

    private void recreateViews() {
        removeAllViews();
        for (int i = 0; i < mTabs.size(); i++) {
            View tabView = LayoutInflater.from(getContext())
                    .inflate(mTabLayoutRes, this, false);
            addView(tabView);
            presentTabView(i);
        }
    }

    private void presentTabView(int position) {
        if (position < 0 || position >= mTabs.size()) {
            throw new IllegalArgumentException("Tab position is invalid: " + position);
        }
        View tabView = getChildAt(position);
        com.android.car.ui.toolbar.Tab tab = mTabs.get(position);
        ImageView iconView = requireViewByRefId(tabView,
                R.id.car_ui_portrait_toolbar_tab_item_icon);
        TextView textView = requireViewByRefId(tabView, R.id.car_ui_portrait_toolbar_tab_item_text);

        tabView.setOnClickListener(view -> selectTab(position));
        tabView.setActivated(position == mSelectedTab);

        iconView.setImageDrawable(tab.getIcon());

        textView.setText(tab.getText());
    }
}
