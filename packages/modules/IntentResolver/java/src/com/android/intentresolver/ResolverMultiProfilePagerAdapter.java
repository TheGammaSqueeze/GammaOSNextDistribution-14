/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.intentresolver;

import android.content.Context;
import android.os.UserHandle;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.ListView;

import androidx.viewpager.widget.PagerAdapter;

import com.android.intentresolver.emptystate.EmptyStateProvider;
import com.android.internal.annotations.VisibleForTesting;

import com.google.common.collect.ImmutableList;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * A {@link PagerAdapter} which describes the work and personal profile intent resolver screens.
 */
@VisibleForTesting
public class ResolverMultiProfilePagerAdapter extends
        MultiProfilePagerAdapter<ListView, ResolverListAdapter, ResolverListAdapter> {
    private final BottomPaddingOverrideSupplier mBottomPaddingOverrideSupplier;

    public ResolverMultiProfilePagerAdapter(
            Context context,
            ResolverListAdapter adapter,
            EmptyStateProvider emptyStateProvider,
            Supplier<Boolean> workProfileQuietModeChecker,
            UserHandle workProfileUserHandle,
            UserHandle cloneProfileUserHandle) {
        this(
                context,
                ImmutableList.of(adapter),
                emptyStateProvider,
                workProfileQuietModeChecker,
                /* defaultProfile= */ 0,
                workProfileUserHandle,
                cloneProfileUserHandle,
                new BottomPaddingOverrideSupplier());
    }

    public ResolverMultiProfilePagerAdapter(Context context,
                                            ResolverListAdapter personalAdapter,
                                            ResolverListAdapter workAdapter,
                                            EmptyStateProvider emptyStateProvider,
                                            Supplier<Boolean> workProfileQuietModeChecker,
                                            @Profile int defaultProfile,
                                            UserHandle workProfileUserHandle,
                                            UserHandle cloneProfileUserHandle) {
        this(
                context,
                ImmutableList.of(personalAdapter, workAdapter),
                emptyStateProvider,
                workProfileQuietModeChecker,
                defaultProfile,
                workProfileUserHandle,
                cloneProfileUserHandle,
                new BottomPaddingOverrideSupplier());
    }

    private ResolverMultiProfilePagerAdapter(
            Context context,
            ImmutableList<ResolverListAdapter> listAdapters,
            EmptyStateProvider emptyStateProvider,
            Supplier<Boolean> workProfileQuietModeChecker,
            @Profile int defaultProfile,
            UserHandle workProfileUserHandle,
            UserHandle cloneProfileUserHandle,
            BottomPaddingOverrideSupplier bottomPaddingOverrideSupplier) {
        super(
                        listAdapter -> listAdapter,
                        (listView, bindAdapter) -> listView.setAdapter(bindAdapter),
                listAdapters,
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
