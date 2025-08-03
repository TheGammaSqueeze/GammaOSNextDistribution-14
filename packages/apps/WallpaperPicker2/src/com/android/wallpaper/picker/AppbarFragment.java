/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.wallpaper.picker;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

import android.annotation.MenuRes;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toolbar;
import android.widget.Toolbar.OnMenuItemClickListener;

import androidx.annotation.Nullable;

import com.android.wallpaper.R;
import com.android.wallpaper.config.BaseFlags;
import com.android.wallpaper.util.ResourceUtils;
import com.android.wallpaper.widget.BottomActionBar;

import com.google.android.material.transition.MaterialSharedAxis;

/**
 * Base class for Fragments that own a {@link Toolbar} widget and a {@link BottomActionBar}.
 *
 * A Fragment extending this class is expected to have a {@link Toolbar} in its root view, with id
 * {@link R.id#toolbar}, which can optionally have a TextView with id custom_toolbar_title for
 * the title.
 * If the Bundle returned by {@link #createArguments(CharSequence)} is used as Arguments for this
 * Fragment, the title provided to that method will be used as the Fragment's toolbar title,
 * otherwise, the value returned by {@link #getDefaultTitle()} (default being {@code null}) will be
 * used as title.
 *
 * @see #setArguments(Bundle)
 * @see BottomActionBarFragment
 */
public abstract class AppbarFragment extends BottomActionBarFragment
        implements OnMenuItemClickListener {

    private static final String ARG_TITLE = "ToolbarFragment.title";
    private AppbarFragmentHost mHost;
    private boolean mUpArrowEnabled;

    /**
     * Interface to be implemented by an Activity hosting a {@link AppbarFragment}.
     */
    public interface AppbarFragmentHost {
        /**
         * Called when a up arrow had been pressed.
         */
        void onUpArrowPressed();

        /**
         * Check if it supports up arrow.
         */
        boolean isUpArrowSupported();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (BaseFlags.get().isPageTransitionsFeatureEnabled(requireContext())) {
            setEnterTransition(new MaterialSharedAxis(MaterialSharedAxis.X, /* forward */ true));
            setReturnTransition(new MaterialSharedAxis(MaterialSharedAxis.X, /* forward */ false));
            setExitTransition(new MaterialSharedAxis(MaterialSharedAxis.X, /* forward */ true));
            setReenterTransition(new MaterialSharedAxis(MaterialSharedAxis.X, /* forward */ false));
        }
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        mHost = (AppbarFragmentHost) context;
    }

    /**
     * Returns a newly created {@link Bundle} containing the given title as an argument.
     * If set as a ToolbarFragment's arguments bundle, this will be used to set up the title of
     * the Toolbar in {@link #setUpToolbar(View)}
     */
    protected static Bundle createArguments(CharSequence title) {
        Bundle args = new Bundle();
        args.putCharSequence(ARG_TITLE, title);
        return args;
    }

    protected Toolbar mToolbar;
    private TextView mTitleView;

    /**
     * Configures a toolbar in the given rootView, with id {@code toolbar} and sets its title to
     * the value in Arguments or {@link #getDefaultTitle()}.
     * Default upArrow value is true.
     */
    public void setUpToolbar(View rootView) {
        setUpToolbar(rootView, /* upArrow= */ true);
    }

    /**
     * Configures a toolbar in the given rootView, inflating the menu corresponding to the given id
     * for the toolbar menu.
     *
     * @param rootView given root view.
     * @param upArrow  true to enable up arrow feature.
     */
    protected void setUpToolbar(View rootView, boolean upArrow) {
        mUpArrowEnabled = upArrow;
        mToolbar = rootView.findViewById(getToolbarId());

        mTitleView = mToolbar.findViewById(R.id.custom_toolbar_title);

        // Update toolbar and status bar color.
        setToolbarColor(getToolbarColorId());

        CharSequence title;
        if (getArguments() != null) {
            title = getArguments().getCharSequence(ARG_TITLE, getDefaultTitle());
        } else {
            title = getDefaultTitle();
        }
        if (!TextUtils.isEmpty(title)) {
            setTitle(title);
        }

        if (upArrow && mHost.isUpArrowSupported()) {
            setUpUpArrow();
        }
    }

    /**
     * Configures the menu in the toolbar.
     *
     * @param menuResId the resource id of the menu
     */
    public void setUpToolbarMenu(@MenuRes int menuResId) {
        mToolbar.inflateMenu(menuResId);
        mToolbar.setOnMenuItemClickListener(this);
    }

    protected void setUpToolbarMenuClickListener(int menuItemResId, View.OnClickListener listener) {
        MenuItem menuItem = mToolbar.getMenu().findItem(menuItemResId);
        menuItem.getActionView().setOnClickListener(listener);
    }

    protected int getToolbarId() {
        return R.id.toolbar;
    }

    protected int getToolbarColorId() {
        return R.color.toolbar_color;
    }

    protected int getToolbarTextColor() {
        return ResourceUtils.getColorAttr(getActivity(), android.R.attr.textColorPrimary);
    }

    /**
     * Set up arrow feature status to enabled or not. Enable it for updating
     * onBottomActionBarReady() while initializing without toolbar setup.
     *
     * @param upArrow true to enable up arrow feature.
     */
    public void setUpArrowEnabled(boolean upArrow) {
        mUpArrowEnabled = upArrow;
    }

    private void setUpUpArrow() {
        Drawable backIcon = getResources().getDrawable(R.drawable.material_ic_arrow_back_black_24,
                null).mutate();
        backIcon.setAutoMirrored(true);
        backIcon.setTint(getToolbarTextColor());
        mToolbar.setNavigationIcon(backIcon);
        mToolbar.setNavigationContentDescription(R.string.bottom_action_bar_back);
        mToolbar.setNavigationOnClickListener(v -> mHost.onUpArrowPressed());
    }

    /**
     * Configures a toolbar in the given rootView, inflating the menu corresponding to the given id
     * for the toolbar menu.
     * Override {@link #onMenuItemClick(MenuItem)} to listen to item click events.
     * @see #setUpToolbar(View)
     */
    public void setUpToolbar(View rootView, @MenuRes int menuResId) {
        setUpToolbar(rootView);
        setUpToolbarMenu(menuResId);
    }

    protected void setToolbarColor(int colorId) {
        mToolbar.setBackgroundResource(colorId);
        getActivity().getWindow().setStatusBarColor(
                getActivity().getResources().getColor(colorId));
    }

    /**
     * Provides a title for this Fragment's toolbar to be used if none is found in
     * {@link #getArguments()}.
     * Default implementation returns {@code null}.
     */
    public CharSequence getDefaultTitle() {
        return null;
    }

    protected String getAccessibilityTitle() {
        return null;
    }

    protected void setTitle(CharSequence title) {
        if (mToolbar == null) {
            return;
        }
        if (mTitleView != null) {
            mToolbar.setTitle(null);
            mTitleView.setText(title);
            mTitleView.setTextColor(getToolbarTextColor());
        } else {
            mToolbar.setTitle(title);
            mToolbar.setTitleTextColor(getToolbarTextColor());
        }

        // Set Activity title to make TalkBack announce title after updating toolbar title.
        if (getActivity() != null) {
            String accessibilityTitle = getAccessibilityTitle();
            getActivity().setTitle(TextUtils.isEmpty(accessibilityTitle) ? title
                    : accessibilityTitle);
        }
    }

    @Override
    protected void onBottomActionBarReady(BottomActionBar bottomActionBar) {
        bottomActionBar.setBackButtonVisibility(
                mUpArrowEnabled && mHost.isUpArrowSupported() ? GONE : VISIBLE);
        super.onBottomActionBarReady(bottomActionBar);
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        return false;
    }
}
