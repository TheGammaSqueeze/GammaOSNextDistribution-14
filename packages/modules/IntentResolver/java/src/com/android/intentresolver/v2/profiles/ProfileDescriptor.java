/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.intentresolver.v2.profiles;

import android.view.ViewGroup;

import com.android.intentresolver.v2.emptystate.EmptyStateUiHelper;

import java.util.Optional;
import java.util.function.Supplier;

// TODO: `ChooserActivity` also has a per-profile record type. Maybe the "multi-profile pager"
// should be the owner of all per-profile data (especially now that the API is generic)?
class ProfileDescriptor<PageViewT, SinglePageAdapterT> {
    final @MultiProfilePagerAdapter.ProfileType int mProfile;
    final String mTabLabel;
    final String mTabAccessibilityLabel;
    final String mTabTag;

    final ViewGroup mRootView;
    final EmptyStateUiHelper mEmptyStateUi;

    // TODO: post-refactoring, we may not need to retain these ivars directly (since they may
    // be encapsulated within the `EmptyStateUiHelper`?).
    private final ViewGroup mEmptyStateView;

    private final SinglePageAdapterT mAdapter;

    public SinglePageAdapterT getAdapter() {
        return mAdapter;
    }

    public PageViewT getView() {
        return mView;
    }

    private final PageViewT mView;

    ProfileDescriptor(
            @MultiProfilePagerAdapter.ProfileType int forProfile,
            String tabLabel,
            String tabAccessibilityLabel,
            String tabTag,
            ViewGroup rootView,
            SinglePageAdapterT adapter,
            Supplier<Optional<Integer>> containerBottomPaddingOverrideSupplier) {
        mProfile = forProfile;
        mTabLabel = tabLabel;
        mTabAccessibilityLabel = tabAccessibilityLabel;
        mTabTag = tabTag;
        mRootView = rootView;
        mAdapter = adapter;
        mEmptyStateView = rootView.findViewById(com.android.internal.R.id.resolver_empty_state);
        mView = (PageViewT) rootView.findViewById(com.android.internal.R.id.resolver_list);
        mEmptyStateUi = new EmptyStateUiHelper(
                rootView,
                com.android.internal.R.id.resolver_list,
                containerBottomPaddingOverrideSupplier);
    }

    protected ViewGroup getEmptyStateView() {
        return mEmptyStateView;
    }

    public void setupContainerPadding() {
        mEmptyStateUi.setupContainerPadding();
    }
}
