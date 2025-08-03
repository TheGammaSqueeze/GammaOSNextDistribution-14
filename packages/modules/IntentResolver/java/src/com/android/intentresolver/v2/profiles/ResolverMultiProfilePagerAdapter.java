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

import android.content.Context;
import android.os.UserHandle;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.ListView;

import androidx.viewpager.widget.PagerAdapter;

import com.android.intentresolver.R;
import com.android.intentresolver.ResolverListAdapter;
import com.android.intentresolver.emptystate.EmptyStateProvider;

import com.google.common.collect.ImmutableList;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * A {@link PagerAdapter} which describes the work and personal profile intent resolver screens.
 */
public class ResolverMultiProfilePagerAdapter extends
        MultiProfilePagerAdapter<ListView, ResolverListAdapter, ResolverListAdapter> {
    private final BottomPaddingOverrideSupplier mBottomPaddingOverrideSupplier;

    public ResolverMultiProfilePagerAdapter(Context context,
                                            ImmutableList<TabConfig<ResolverListAdapter>> tabs,
                                            EmptyStateProvider emptyStateProvider,
                                            Supplier<Boolean> workProfileQuietModeChecker,
                                            @ProfileType int defaultProfile,
                                            UserHandle workProfileUserHandle,
                                            UserHandle cloneProfileUserHandle) {
        this(
                context,
                tabs,
                emptyStateProvider,
                workProfileQuietModeChecker,
                defaultProfile,
                workProfileUserHandle,
                cloneProfileUserHandle,
                new BottomPaddingOverrideSupplier());
    }

    private ResolverMultiProfilePagerAdapter(
            Context context,
            ImmutableList<TabConfig<ResolverListAdapter>> tabs,
            EmptyStateProvider emptyStateProvider,
            Supplier<Boolean> workProfileQuietModeChecker,
            @ProfileType int defaultProfile,
            UserHandle workProfileUserHandle,
            UserHandle cloneProfileUserHandle,
            BottomPaddingOverrideSupplier bottomPaddingOverrideSupplier) {
        super(
                        listAdapter -> listAdapter,
                        (listView, bindAdapter) -> listView.setAdapter(bindAdapter),
                tabs,
                emptyStateProvider,
                workProfileQuietModeChecker,
                defaultProfile,
                workProfileUserHandle,
                cloneProfileUserHandle,
                        () -> (ViewGroup) LayoutInflater.from(context).inflate(
                                R.layout.resolver_list_per_profile, null, false),
                bottomPaddingOverrideSupplier);
        mBottomPaddingOverrideSupplier = bottomPaddingOverrideSupplier;
    }

    public void setUseLayoutWithDefault(boolean useLayoutWithDefault) {
        mBottomPaddingOverrideSupplier.setUseLayoutWithDefault(useLayoutWithDefault);
    }

    /** Un-check any item(s) that may be checked in any of our inactive adapter(s). */
    public void clearCheckedItemsInInactiveProfiles() {
        // TODO: The "inactive" condition is legacy logic. Could we simplify and clear-all?
        forEachInactivePage(pageNumber -> {
            ListView inactiveListView = getListViewForIndex(pageNumber);
            if (inactiveListView.getCheckedItemCount() > 0) {
                inactiveListView.setItemChecked(inactiveListView.getCheckedItemPosition(), false);
            }
        });
    }

    private static class BottomPaddingOverrideSupplier implements Supplier<Optional<Integer>> {
        private boolean mUseLayoutWithDefault;

        public void setUseLayoutWithDefault(boolean useLayoutWithDefault) {
            mUseLayoutWithDefault = useLayoutWithDefault;
        }

        @Override
        public Optional<Integer> get() {
            return mUseLayoutWithDefault ? Optional.empty() : Optional.of(0);
        }
    }
}
