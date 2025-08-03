/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.documentsui;

import static com.android.documentsui.base.SharedMinimal.VERBOSE;

import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Outline;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;

import androidx.annotation.ColorRes;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;

import com.android.documentsui.base.RootInfo;
import com.android.documentsui.base.State;
import com.android.documentsui.base.UserId;
import com.android.documentsui.dirlist.AnimationView;
import com.android.documentsui.util.VersionUtils;
import com.android.modules.utils.build.SdkLevel;

import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.appbar.CollapsingToolbarLayout;

import java.util.function.IntConsumer;

/**
 * A facade over the portions of the app and drawer toolbars.
 */
public class NavigationViewManager implements AppBarLayout.OnOffsetChangedListener {

    private static final String TAG = "NavigationViewManager";

    private final DrawerController mDrawer;
    private final Toolbar mToolbar;
    private final BaseActivity mActivity;
    private final View mHeader;
    private final State mState;
    private final NavigationViewManager.Environment mEnv;
    private final Breadcrumb mBreadcrumb;
    private final ProfileTabs mProfileTabs;
    private final View mSearchBarView;
    private final CollapsingToolbarLayout mCollapsingBarLayout;
    private final Drawable mDefaultActionBarBackground;
    private final ViewOutlineProvider mDefaultOutlineProvider;
    private final ViewOutlineProvider mSearchBarOutlineProvider;
    private final boolean mShowSearchBar;
    private final ConfigStore mConfigStore;

    private boolean mIsActionModeActivated = false;
    @ColorRes
    private int mDefaultStatusBarColorResId;

    public NavigationViewManager(
            BaseActivity activity,
            DrawerController drawer,
            State state,
            NavigationViewManager.Environment env,
            Breadcrumb breadcrumb,
            View tabLayoutContainer,
            UserIdManager userIdManager,
            ConfigStore configStore) {
        this(activity, drawer, state, env, breadcrumb, tabLayoutContainer, userIdManager, null,
                configStore);
    }

    public NavigationViewManager(
            BaseActivity activity,
            DrawerController drawer,
            State state,
            NavigationViewManager.Environment env,
            Breadcrumb breadcrumb,
            View tabLayoutContainer,
            UserManagerState userManagerState,
            ConfigStore configStore) {
        this(activity, drawer, state, env, breadcrumb, tabLayoutContainer, null, userManagerState,
                configStore);
    }

    public NavigationViewManager(
            BaseActivity activity,
            DrawerController drawer,
            State state,
            NavigationViewManager.Environment env,
            Breadcrumb breadcrumb,
            View tabLayoutContainer,
            UserIdManager userIdManager,
            UserManagerState userManagerState,
            ConfigStore configStore) {

        mActivity = activity;
        mToolbar = activity.findViewById(R.id.toolbar);
        mHeader = activity.findViewById(R.id.directory_header);
        mDrawer = drawer;
        mState = state;
        mEnv = env;
        mBreadcrumb = breadcrumb;
        mBreadcrumb.setup(env, state, this::onNavigationItemSelected);
        mConfigStore = configStore;
        mProfileTabs =
                getProfileTabs(tabLayoutContainer, userIdManager, userManagerState, activity);

        mToolbar.setNavigationOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        onNavigationIconClicked();
                    }
                });
        mSearchBarView = activity.findViewById(R.id.searchbar_title);
        mCollapsingBarLayout = activity.findViewById(R.id.collapsing_toolbar);
        mDefaultActionBarBackground = mToolbar.getBackground();
        mDefaultOutlineProvider = mToolbar.getOutlineProvider();
        mShowSearchBar = activity.getResources().getBoolean(R.bool.show_search_bar);

        final int[] styledAttrs = {android.R.attr.statusBarColor};
        TypedArray a = mActivity.obtainStyledAttributes(styledAttrs);
        mDefaultStatusBarColorResId = a.getResourceId(0, -1);
        if (mDefaultStatusBarColorResId == -1) {
            Log.w(TAG, "Retrieve statusBarColorResId from theme failed, assigned default");
            mDefaultStatusBarColorResId = R.color.app_background_color;
        }
        a.recycle();

        final Resources resources = mToolbar.getResources();
        final int radius = resources.getDimensionPixelSize(R.dimen.search_bar_radius);
        final int marginStart =
                resources.getDimensionPixelSize(R.dimen.search_bar_background_margin_start);
        final int marginEnd =
                resources.getDimensionPixelSize(R.dimen.search_bar_background_margin_end);
        mSearchBarOutlineProvider = new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(marginStart, 0,
                        view.getWidth() - marginEnd, view.getHeight(), radius);
            }
        };
    }

    private ProfileTabs getProfileTabs(View tabLayoutContainer, UserIdManager userIdManager,
            UserManagerState userManagerState, BaseActivity activity) {
        return mConfigStore.isPrivateSpaceInDocsUIEnabled()
                ? new ProfileTabs(tabLayoutContainer, mState, userManagerState, mEnv, activity,
                mConfigStore)
                : new ProfileTabs(tabLayoutContainer, mState, userIdManager, mEnv, activity,
                        mConfigStore);
    }

    @Override
    public void onOffsetChanged(AppBarLayout appBarLayout, int offset) {
        if (!VersionUtils.isAtLeastS()) {
            return;
        }

        // For S+ Only. Change toolbar color dynamically based on scroll offset.
        // Usually this can be done in xml using app:contentScrim and app:statusBarScrim, however
        // in our case since we also put directory_header.xml inside the CollapsingToolbarLayout,
        // the scrim will also cover the directory header. Long term need to think about how to
        // move directory_header out of the AppBarLayout.

        Window window = mActivity.getWindow();
        View actionBar =
                window.getDecorView().findViewById(androidx.appcompat.R.id.action_mode_bar);
        int dynamicHeaderColor = ContextCompat.getColor(mActivity,
                offset == 0 ? mDefaultStatusBarColorResId : R.color.color_surface_header);
        if (actionBar != null) {
            // Action bar needs to be updated separately for selection mode.
            actionBar.setBackgroundColor(dynamicHeaderColor);
        }

        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.setStatusBarColor(dynamicHeaderColor);
        if (shouldShowSearchBar()) {
            // Do not change search bar background.
        } else {
            mToolbar.setBackground(new ColorDrawable(dynamicHeaderColor));
        }
    }

    public void setSearchBarClickListener(View.OnClickListener listener) {
        mSearchBarView.setOnClickListener(listener);
        if (SdkLevel.isAtLeastU()) {
            try {
                mSearchBarView.setHandwritingDelegatorCallback(
                        () -> listener.onClick(mSearchBarView));
            } catch (LinkageError e) {
                // Running on a device with an older build of Android U
                // TODO(b/274154553): Remove try/catch block after Android U Beta 1 is released
            }
        }
    }

    public ProfileTabsAddons getProfileTabsAddons() {
        return mProfileTabs;
    }

    /**
     * Sets a listener to the profile tabs.
     */
    public void setProfileTabsListener(ProfileTabs.Listener listener) {
        mProfileTabs.setListener(listener);
    }

    private void onNavigationIconClicked() {
        if (mDrawer.isPresent()) {
            mDrawer.setOpen(true);
        }
    }

    void onNavigationItemSelected(int position) {
        boolean changed = false;
        while (mState.stack.size() > position + 1) {
            changed = true;
            mState.stack.pop();
        }
        if (changed) {
            mEnv.refreshCurrentRootAndDirectory(AnimationView.ANIM_LEAVE);
        }
    }

    public UserId getSelectedUser() {
        return mProfileTabs.getSelectedUser();
    }

    public void setActionModeActivated(boolean actionModeActivated) {
        mIsActionModeActivated = actionModeActivated;
        update();
    }

    public void update() {
        updateScrollFlag();
        updateToolbar();
        mProfileTabs.updateView();

        // TODO: Looks to me like this block is never getting hit.
        if (mEnv.isSearchExpanded()) {
            mToolbar.setTitle(null);
            mBreadcrumb.show(false);
            return;
        }

        mDrawer.setTitle(mEnv.getDrawerTitle());

        mToolbar.setNavigationIcon(getActionBarIcon());
        mToolbar.setNavigationContentDescription(R.string.drawer_open);

        if (shouldShowSearchBar()) {
            mBreadcrumb.show(false);
            mToolbar.setTitle(null);
            mSearchBarView.setVisibility(View.VISIBLE);
        } else {
            mSearchBarView.setVisibility(View.GONE);
            String title = mState.stack.size() <= 1
                    ? mEnv.getCurrentRoot().title : mState.stack.getTitle();
            if (VERBOSE) Log.v(TAG, "New toolbar title is: " + title);
            mToolbar.setTitle(title);
            mBreadcrumb.show(true);
            mBreadcrumb.postUpdate();
        }
    }

    private void updateScrollFlag() {
        if (mCollapsingBarLayout == null) {
            return;
        }

        AppBarLayout.LayoutParams lp =
                (AppBarLayout.LayoutParams) mCollapsingBarLayout.getLayoutParams();
        lp.setScrollFlags(AppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL
                | AppBarLayout.LayoutParams.SCROLL_FLAG_EXIT_UNTIL_COLLAPSED);
        mCollapsingBarLayout.setLayoutParams(lp);
    }

    private void updateToolbar() {
        if (mCollapsingBarLayout == null) {
            // Tablet mode does not use CollapsingBarLayout
            // (res/layout-sw720dp/directory_app_bar.xml or res/layout/fixed_layout.xml)
            if (shouldShowSearchBar()) {
                mToolbar.setBackgroundResource(R.drawable.search_bar_background);
                mToolbar.setOutlineProvider(mSearchBarOutlineProvider);
            } else {
                mToolbar.setBackground(mDefaultActionBarBackground);
                mToolbar.setOutlineProvider(null);
            }
            return;
        }

        CollapsingToolbarLayout.LayoutParams toolbarLayoutParams =
                (CollapsingToolbarLayout.LayoutParams) mToolbar.getLayoutParams();

        int headerTopOffset = 0;
        if (shouldShowSearchBar() && !mIsActionModeActivated) {
            mToolbar.setBackgroundResource(R.drawable.search_bar_background);
            mToolbar.setOutlineProvider(mSearchBarOutlineProvider);
            int searchBarMargin = mToolbar.getResources().getDimensionPixelSize(
                    R.dimen.search_bar_margin);
            toolbarLayoutParams.setMargins(searchBarMargin, searchBarMargin, searchBarMargin,
                    searchBarMargin);
            mToolbar.setLayoutParams(toolbarLayoutParams);
            mToolbar.setElevation(
                    mToolbar.getResources().getDimensionPixelSize(R.dimen.search_bar_elevation));
            headerTopOffset = toolbarLayoutParams.height + searchBarMargin * 2;
        } else {
            mToolbar.setBackground(mDefaultActionBarBackground);
            mToolbar.setOutlineProvider(mDefaultOutlineProvider);
            int actionBarMargin = mToolbar.getResources().getDimensionPixelSize(
                    R.dimen.action_bar_margin);
            toolbarLayoutParams.setMargins(0, 0, 0, /* bottom= */ actionBarMargin);
            mToolbar.setLayoutParams(toolbarLayoutParams);
            mToolbar.setElevation(
                    mToolbar.getResources().getDimensionPixelSize(R.dimen.action_bar_elevation));
            headerTopOffset = toolbarLayoutParams.height + actionBarMargin;
        }

        if (!mIsActionModeActivated) {
            FrameLayout.LayoutParams headerLayoutParams =
                    (FrameLayout.LayoutParams) mHeader.getLayoutParams();
            headerLayoutParams.setMargins(0, /* top= */ headerTopOffset, 0, 0);
            mHeader.setLayoutParams(headerLayoutParams);
        }
    }

    private boolean shouldShowSearchBar() {
        return mState.stack.isRecents() && !mEnv.isSearchExpanded() && mShowSearchBar;
    }

    // Hamburger if drawer is present, else sad nullness.
    private @Nullable
    Drawable getActionBarIcon() {
        if (mDrawer.isPresent()) {
            return mToolbar.getContext().getDrawable(R.drawable.ic_hamburger);
        } else {
            return null;
        }
    }

    void revealRootsDrawer(boolean open) {
        mDrawer.setOpen(open);
    }

    interface Breadcrumb {
        void setup(Environment env, State state, IntConsumer listener);

        void show(boolean visibility);

        void postUpdate();
    }

    interface Environment {
        @Deprecated
            // Use CommonAddones#getCurrentRoot
        RootInfo getCurrentRoot();

        String getDrawerTitle();

        @Deprecated
            // Use CommonAddones#refreshCurrentRootAndDirectory
        void refreshCurrentRootAndDirectory(int animation);

        boolean isSearchExpanded();
    }
}
