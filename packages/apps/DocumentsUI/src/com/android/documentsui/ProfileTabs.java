/*
 * Copyright (C) 2020 The Android Open Source Project
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

import static androidx.core.util.Preconditions.checkNotNull;

import static com.android.documentsui.DevicePolicyResources.Strings.PERSONAL_TAB;
import static com.android.documentsui.DevicePolicyResources.Strings.WORK_TAB;

import android.app.admin.DevicePolicyManager;
import android.os.Build;
import android.os.UserManager;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.android.documentsui.base.RootInfo;
import com.android.documentsui.base.State;
import com.android.documentsui.base.UserId;
import com.android.modules.utils.build.SdkLevel;

import com.google.android.material.tabs.TabLayout;
import com.google.common.base.Objects;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * A manager class to control UI on a {@link TabLayout} for cross-profile purpose.
 */
public class ProfileTabs implements ProfileTabsAddons {
    private static final float DISABLED_TAB_OPACITY = 0.38f;

    private final View mTabsContainer;
    private final TabLayout mTabs;
    private final State mState;
    private final NavigationViewManager.Environment mEnv;
    private final AbstractActionHandler.CommonAddons mCommonAddons;
    @Nullable
    private final UserIdManager mUserIdManager;
    @Nullable
    private final UserManagerState mUserManagerState;
    private final ConfigStore mConfigStore;
    private List<UserId> mUserIds;
    @Nullable
    private Listener mListener;
    private TabLayout.OnTabSelectedListener mOnTabSelectedListener;
    private View mTabSeparator;

    public ProfileTabs(View tabLayoutContainer, State state, UserIdManager userIdManager,
            NavigationViewManager.Environment env,
            AbstractActionHandler.CommonAddons commonAddons, ConfigStore configStore) {
        this(tabLayoutContainer, state, userIdManager, null, env, commonAddons, configStore);
    }

    public ProfileTabs(View tabLayoutContainer, State state, UserManagerState userManagerState,
            NavigationViewManager.Environment env,
            AbstractActionHandler.CommonAddons commonAddons, ConfigStore configStore) {
        this(tabLayoutContainer, state, null, userManagerState, env, commonAddons, configStore);
    }

    public ProfileTabs(View tabLayoutContainer, State state, @Nullable UserIdManager userIdManager,
            @Nullable UserManagerState userManagerState, NavigationViewManager.Environment env,
            AbstractActionHandler.CommonAddons commonAddons, ConfigStore configStore) {
        mTabsContainer = checkNotNull(tabLayoutContainer);
        mTabs = tabLayoutContainer.findViewById(R.id.tabs);
        mState = checkNotNull(state);
        mEnv = checkNotNull(env);
        mCommonAddons = checkNotNull(commonAddons);
        mConfigStore = configStore;
        if (mConfigStore.isPrivateSpaceInDocsUIEnabled()) {
            mUserIdManager = userIdManager;
            mUserManagerState = checkNotNull(userManagerState);
        } else {
            mUserIdManager = checkNotNull(userIdManager);
            mUserManagerState = userManagerState;
        }
        mTabs.removeAllTabs();
        mUserIds = Collections.singletonList(UserId.CURRENT_USER);
        mTabSeparator = tabLayoutContainer.findViewById(R.id.tab_separator);

        mOnTabSelectedListener = new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                if (mListener != null) {
                    // find a way to identify user iteraction
                    mListener.onUserSelected((UserId) tab.getTag());
                }
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        };
        mTabs.addOnTabSelectedListener(mOnTabSelectedListener);
    }

    /**
     * Update the tab layout based on status of availability of the hidden profile.
     */
    public void updateView() {
        updateTabsIfNeeded();
        RootInfo currentRoot = mCommonAddons.getCurrentRoot();
        if (mTabs.getSelectedTabPosition() == -1
                || !Objects.equal(currentRoot.userId, getSelectedUser())) {
            // Update the layout according to the current root if necessary.
            // Make sure we do not invoke callback. Otherwise, it is likely to cause infinite loop.
            mTabs.removeOnTabSelectedListener(mOnTabSelectedListener);
            mTabs.selectTab(mTabs.getTabAt(mUserIds.indexOf(currentRoot.userId)));
            mTabs.addOnTabSelectedListener(mOnTabSelectedListener);
        }
        mTabsContainer.setVisibility(shouldShow() ? View.VISIBLE : View.GONE);

        // Material next changes apply only for version S or greater
        if (SdkLevel.isAtLeastS()) {
            mTabSeparator.setVisibility(View.GONE);
            int tabContainerHeightInDp = (int) mTabsContainer.getContext().getResources()
                    .getDimension(R.dimen.tab_container_height);
            mTabsContainer.getLayoutParams().height = tabContainerHeightInDp;
            ViewGroup.MarginLayoutParams tabContainerMarginLayoutParams =
                    (ViewGroup.MarginLayoutParams) mTabsContainer.getLayoutParams();
            int tabContainerMarginTop = (int) mTabsContainer.getContext().getResources()
                    .getDimension(R.dimen.profile_tab_margin_top);
            tabContainerMarginLayoutParams.setMargins(0, tabContainerMarginTop, 0, 0);
            mTabsContainer.requestLayout();
            for (int i = 0; i < mTabs.getTabCount(); i++) {

                // Tablayout holds a view that contains the individual tab
                View tab = ((ViewGroup) mTabs.getChildAt(0)).getChildAt(i);

                // Get individual tab to set the style
                ViewGroup.MarginLayoutParams marginLayoutParams =
                        (ViewGroup.MarginLayoutParams) tab.getLayoutParams();
                int tabMarginSide = (int) mTabsContainer.getContext().getResources()
                        .getDimension(R.dimen.profile_tab_margin_side);
                marginLayoutParams.setMargins(tabMarginSide, 0, tabMarginSide, 0);
                int tabHeightInDp = (int) mTabsContainer.getContext().getResources()
                        .getDimension(R.dimen.tab_height);
                tab.getLayoutParams().height = tabHeightInDp;
                tab.requestLayout();
                tab.setBackgroundResource(R.drawable.tab_border_rounded);
            }

        }
    }

    public void setListener(@Nullable Listener listener) {
        mListener = listener;
    }

    private void updateTabsIfNeeded() {
        List<UserId> userIds = getUserIds();
        // Add tabs if the userIds is not equals to cached mUserIds.
        // Given that mUserIds was initialized with only the current user, if getUserIds()
        // returns just the current user, we don't need to do anything on the tab layout.
        if (!userIds.equals(mUserIds)) {
            mUserIds = new ArrayList<>();
            mUserIds.addAll(userIds);
            mTabs.removeAllTabs();
            if (mUserIds.size() > 1) {
                if (mConfigStore.isPrivateSpaceInDocsUIEnabled()) {
                    addTabsPrivateSpaceEnabled();
                } else {
                    addTabsPrivateSpaceDisabled();
                }
            }
        }
    }

    private List<UserId> getUserIds() {
        if (mConfigStore.isPrivateSpaceInDocsUIEnabled()) {
            assert mUserManagerState != null;
            return mUserManagerState.getUserIds();
        }
        assert mUserIdManager != null;
        return mUserIdManager.getUserIds();
    }

    private void addTabsPrivateSpaceEnabled() {
        // set setSelected to false otherwise it will trigger callback.
        assert mUserManagerState != null;
        Map<UserId, String> userIdToLabelMap = mUserManagerState.getUserIdToLabelMap();
        UserManager userManager = mTabsContainer.getContext().getSystemService(UserManager.class);
        assert userManager != null;
        for (UserId userId : mUserIds) {
            mTabs.addTab(createTab(userIdToLabelMap.get(userId), userId), /* setSelected= */false);
        }
    }

    private void addTabsPrivateSpaceDisabled() {
        // set setSelected to false otherwise it will trigger callback.
        assert mUserIdManager != null;
        mTabs.addTab(createTab(
                getEnterpriseString(PERSONAL_TAB, R.string.personal_tab),
                mUserIdManager.getSystemUser()), /* setSelected= */false);
        mTabs.addTab(createTab(
                getEnterpriseString(WORK_TAB, R.string.work_tab),
                mUserIdManager.getManagedUser()), /* setSelected= */false);
    }

    private String getEnterpriseString(String updatableStringId, int defaultStringId) {
        if (SdkLevel.isAtLeastT()) {
            return getUpdatableEnterpriseString(updatableStringId, defaultStringId);
        } else {
            return mTabsContainer.getContext().getString(defaultStringId);
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private String getUpdatableEnterpriseString(String updatableStringId, int defaultStringId) {
        DevicePolicyManager dpm = mTabsContainer.getContext().getSystemService(
                DevicePolicyManager.class);
        return dpm.getResources().getString(
                updatableStringId,
                () -> mTabsContainer.getContext().getString(defaultStringId));
    }

    /**
     * Returns the user represented by the selected tab. If there is no tab, return the
     * current user.
     */
    public UserId getSelectedUser() {
        if (mTabs.getTabCount() > 1 && mTabs.getSelectedTabPosition() >= 0) {
            return (UserId) mTabs.getTabAt(mTabs.getSelectedTabPosition()).getTag();
        }
        return UserId.CURRENT_USER;
    }

    private boolean shouldShow() {
        // Only show tabs when:
        // 1. state supports cross profile, and
        // 2. more than one tab, and
        // 3. not in search mode, and
        // 4. not in sub-folder, and
        // 5. the root supports cross profile.
        return mState.supportsCrossProfile()
                && mTabs.getTabCount() > 1
                && !mEnv.isSearchExpanded()
                && mState.stack.size() <= 1
                && mState.stack.getRoot() != null && mState.stack.getRoot().supportsCrossProfile();
    }

    private TabLayout.Tab createTab(String text, UserId userId) {
        return mTabs.newTab().setText(text).setTag(userId);
    }

    @Override
    public void setEnabled(boolean enabled) {
        if (mTabs.getChildCount() > 0) {
            View view = mTabs.getChildAt(0);
            if (view instanceof ViewGroup) {
                ViewGroup tabs = (ViewGroup) view;
                for (int i = 0; i < tabs.getChildCount(); i++) {
                    View tabView = tabs.getChildAt(i);
                    tabView.setEnabled(enabled);
                    tabView.setAlpha((enabled || mTabs.getSelectedTabPosition() == i) ? 1f
                            : DISABLED_TAB_OPACITY);
                }
            }
        }
    }

    /**
     * Interface definition for a callback to be invoked.
     */
    interface Listener {
        /**
         * Called when a user tab has been selected.
         */
        void onUserSelected(UserId userId);
    }
}
