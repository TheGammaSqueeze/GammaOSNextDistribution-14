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

package com.android.intentresolver.v2.profiles

import android.os.UserHandle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ListView
import androidx.test.platform.app.InstrumentationRegistry
import com.android.intentresolver.MultiProfilePagerAdapter.PROFILE_PERSONAL
import com.android.intentresolver.MultiProfilePagerAdapter.PROFILE_WORK
import com.android.intentresolver.R
import com.android.intentresolver.ResolverListAdapter
import com.android.intentresolver.emptystate.EmptyStateProvider
import com.android.intentresolver.mock
import com.android.intentresolver.whenever
import com.google.common.collect.ImmutableList
import com.google.common.truth.Truth.assertThat
import java.util.Optional
import java.util.function.Supplier
import org.junit.Test

class MultiProfilePagerAdapterTest {
    private val PERSONAL_USER_HANDLE = UserHandle.of(10)
    private val WORK_USER_HANDLE = UserHandle.of(20)

    private val context = InstrumentationRegistry.getInstrumentation().getContext()
    private val inflater = Supplier {
        LayoutInflater.from(context).inflate(R.layout.resolver_list_per_profile, null, false)
            as ViewGroup
    }

    @Test
    fun testSinglePageProfileAdapter() {
        val personalListAdapter =
            mock<ResolverListAdapter> { whenever(getUserHandle()).thenReturn(PERSONAL_USER_HANDLE) }
        val pagerAdapter =
            MultiProfilePagerAdapter(
                { listAdapter: ResolverListAdapter -> listAdapter },
                { listView: ListView, bindAdapter: ResolverListAdapter ->
                    listView.setAdapter(bindAdapter)
                },
                ImmutableList.of(
                    TabConfig(
                        PROFILE_PERSONAL,
                        "personal",
                        "personal_a11y",
                        "TAG_PERSONAL",
                        personalListAdapter
                    )
                ),
                object : EmptyStateProvider {},
                { false },
                PROFILE_PERSONAL,
                null,
                null,
                inflater,
                { Optional.empty() }
            )
        assertThat(pagerAdapter.count).isEqualTo(1)
        assertThat(pagerAdapter.currentPage).isEqualTo(PROFILE_PERSONAL)
        assertThat(pagerAdapter.currentUserHandle).isEqualTo(PERSONAL_USER_HANDLE)
        assertThat(pagerAdapter.getPageAdapterForIndex(0)).isSameInstanceAs(personalListAdapter)
        assertThat(pagerAdapter.activeListAdapter).isSameInstanceAs(personalListAdapter)
        assertThat(pagerAdapter.personalListAdapter).isSameInstanceAs(personalListAdapter)
        assertThat(pagerAdapter.workListAdapter).isNull()
        assertThat(pagerAdapter.itemCount).isEqualTo(1)
        // TODO: consider covering some of the package-private methods (and making them public?).
        // TODO: consider exercising responsibilities as an implementation of a ViewPager adapter.
    }

    @Test
    fun testTwoProfilePagerAdapter() {
        val personalListAdapter =
            mock<ResolverListAdapter> { whenever(getUserHandle()).thenReturn(PERSONAL_USER_HANDLE) }
        val workListAdapter =
            mock<ResolverListAdapter> { whenever(getUserHandle()).thenReturn(WORK_USER_HANDLE) }
        val pagerAdapter =
            MultiProfilePagerAdapter(
                { listAdapter: ResolverListAdapter -> listAdapter },
                { listView: ListView, bindAdapter: ResolverListAdapter ->
                    listView.setAdapter(bindAdapter)
                },
                ImmutableList.of(
                    TabConfig(
                        PROFILE_PERSONAL,
                        "personal",
                        "personal_a11y",
                        "TAG_PERSONAL",
                        personalListAdapter
                    ),
                    TabConfig(PROFILE_WORK, "work", "work_a11y", "TAG_WORK", workListAdapter)
                ),
                object : EmptyStateProvider {},
                { false },
                PROFILE_PERSONAL,
                WORK_USER_HANDLE, // TODO: why does this test pass even if this is null?
                null,
                inflater,
                { Optional.empty() }
            )
        assertThat(pagerAdapter.count).isEqualTo(2)
        assertThat(pagerAdapter.currentPage).isEqualTo(PROFILE_PERSONAL)
        assertThat(pagerAdapter.currentUserHandle).isEqualTo(PERSONAL_USER_HANDLE)
        assertThat(pagerAdapter.getPageAdapterForIndex(0)).isSameInstanceAs(personalListAdapter)
        assertThat(pagerAdapter.getPageAdapterForIndex(1)).isSameInstanceAs(workListAdapter)
        assertThat(pagerAdapter.activeListAdapter).isSameInstanceAs(personalListAdapter)
        assertThat(pagerAdapter.personalListAdapter).isSameInstanceAs(personalListAdapter)
        assertThat(pagerAdapter.workListAdapter).isSameInstanceAs(workListAdapter)
        assertThat(pagerAdapter.itemCount).isEqualTo(2)
        // TODO: consider covering some of the package-private methods (and making them public?).
        // TODO: consider exercising responsibilities as an implementation of a ViewPager adapter;
        // especially matching profiles to ListViews?
        // TODO: test ProfileSelectedListener (and getters for "current" state) as the selected
        // page changes. Currently there's no API to change the selected page directly; that's
        // only possible through manipulation of the bound ViewPager.
    }

    @Test
    fun testTwoProfilePagerAdapter_workIsDefault() {
        val personalListAdapter =
            mock<ResolverListAdapter> { whenever(getUserHandle()).thenReturn(PERSONAL_USER_HANDLE) }
        val workListAdapter =
            mock<ResolverListAdapter> { whenever(getUserHandle()).thenReturn(WORK_USER_HANDLE) }
        val pagerAdapter =
            MultiProfilePagerAdapter(
                { listAdapter: ResolverListAdapter -> listAdapter },
                { listView: ListView, bindAdapter: ResolverListAdapter ->
                    listView.setAdapter(bindAdapter)
                },
                ImmutableList.of(
                    TabConfig(
                        PROFILE_PERSONAL,
                        "personal",
                        "personal_a11y",
                        "TAG_PERSONAL",
                        personalListAdapter
                    ),
                    TabConfig(PROFILE_WORK, "work", "work_a11y", "TAG_WORK", workListAdapter)
                ),
                object : EmptyStateProvider {},
                { false },
                PROFILE_WORK, // <-- This test specifically requests we start on work profile.
                WORK_USER_HANDLE, // TODO: why does this test pass even if this is null?
                null,
                inflater,
                { Optional.empty() }
            )
        assertThat(pagerAdapter.count).isEqualTo(2)
        assertThat(pagerAdapter.currentPage).isEqualTo(PROFILE_WORK)
        assertThat(pagerAdapter.currentUserHandle).isEqualTo(WORK_USER_HANDLE)
        assertThat(pagerAdapter.getPageAdapterForIndex(0)).isSameInstanceAs(personalListAdapter)
        assertThat(pagerAdapter.getPageAdapterForIndex(1)).isSameInstanceAs(workListAdapter)
        assertThat(pagerAdapter.activeListAdapter).isSameInstanceAs(workListAdapter)
        assertThat(pagerAdapter.personalListAdapter).isSameInstanceAs(personalListAdapter)
        assertThat(pagerAdapter.workListAdapter).isSameInstanceAs(workListAdapter)
        assertThat(pagerAdapter.itemCount).isEqualTo(2)
        // TODO: consider covering some of the package-private methods (and making them public?).
        // TODO: test ProfileSelectedListener (and getters for "current" state) as the selected
        // page changes. Currently there's no API to change the selected page directly; that's
        // only possible through manipulation of the bound ViewPager.
    }

    @Test
    fun testBottomPaddingDelegate_default() {
        val personalListAdapter =
            mock<ResolverListAdapter> { whenever(getUserHandle()).thenReturn(PERSONAL_USER_HANDLE) }
        val pagerAdapter =
            MultiProfilePagerAdapter(
                { listAdapter: ResolverListAdapter -> listAdapter },
                { listView: ListView, bindAdapter: ResolverListAdapter ->
                    listView.setAdapter(bindAdapter)
                },
                ImmutableList.of(
                    TabConfig(
                        PROFILE_PERSONAL,
                        "personal",
                        "personal_a11y",
                        "TAG_PERSONAL",
                        personalListAdapter
                    )
                ),
                object : EmptyStateProvider {},
                { false },
                PROFILE_PERSONAL,
                null,
                null,
                inflater,
                { Optional.empty() }
            )
        val container =
            pagerAdapter
                .getActiveEmptyStateView()
                .requireViewById<View>(com.android.internal.R.id.resolver_empty_state_container)
        container.setPadding(1, 2, 3, 4)
        pagerAdapter.setupContainerPadding()
        assertThat(container.paddingLeft).isEqualTo(1)
        assertThat(container.paddingTop).isEqualTo(2)
        assertThat(container.paddingRight).isEqualTo(3)
        assertThat(container.paddingBottom).isEqualTo(4)
    }

    @Test
    fun testBottomPaddingDelegate_override() {
        val personalListAdapter =
            mock<ResolverListAdapter> { whenever(getUserHandle()).thenReturn(PERSONAL_USER_HANDLE) }
        val pagerAdapter =
            MultiProfilePagerAdapter(
                { listAdapter: ResolverListAdapter -> listAdapter },
                { listView: ListView, bindAdapter: ResolverListAdapter ->
                    listView.setAdapter(bindAdapter)
                },
                ImmutableList.of(
                    TabConfig(
                        PROFILE_PERSONAL,
                        "personal",
                        "personal_a11y",
                        "TAG_PERSONAL",
                        personalListAdapter
                    )
                ),
                object : EmptyStateProvider {},
                { false },
                PROFILE_PERSONAL,
                null,
                null,
                inflater,
                { Optional.of(42) }
            )
        val container =
            pagerAdapter
                .getActiveEmptyStateView()
                .requireViewById<View>(com.android.internal.R.id.resolver_empty_state_container)
        container.setPadding(1, 2, 3, 4)
        pagerAdapter.setupContainerPadding()
        assertThat(container.paddingLeft).isEqualTo(1)
        assertThat(container.paddingTop).isEqualTo(2)
        assertThat(container.paddingRight).isEqualTo(3)
        assertThat(container.paddingBottom).isEqualTo(42)
    }

    @Test
    fun testPresumedQuietModeEmptyStateForWorkProfile_whenQuiet() {
        // TODO: this is "presumed" because the conditions to determine whether we "should" show an
        // empty state aren't enforced to align with the conditions when we actually *would* -- I
        // believe `shouldShowEmptyStateScreen` should be implemented in terms of the provider?
        val personalListAdapter =
            mock<ResolverListAdapter> {
                whenever(getUserHandle()).thenReturn(PERSONAL_USER_HANDLE)
                whenever(getUnfilteredCount()).thenReturn(1)
            }
        val workListAdapter =
            mock<ResolverListAdapter> {
                whenever(getUserHandle()).thenReturn(WORK_USER_HANDLE)
                whenever(getUnfilteredCount()).thenReturn(1)
            }
        val pagerAdapter =
            MultiProfilePagerAdapter(
                { listAdapter: ResolverListAdapter -> listAdapter },
                { listView: ListView, bindAdapter: ResolverListAdapter ->
                    listView.setAdapter(bindAdapter)
                },
                ImmutableList.of(
                    TabConfig(
                        PROFILE_PERSONAL,
                        "personal",
                        "personal_a11y",
                        "TAG_PERSONAL",
                        personalListAdapter
                    ),
                    TabConfig(PROFILE_WORK, "work", "work_a11y", "TAG_WORK", workListAdapter)
                ),
                object : EmptyStateProvider {},
                { true }, // <-- Work mode is quiet.
                PROFILE_WORK,
                WORK_USER_HANDLE,
                null,
                inflater,
                { Optional.empty() }
            )
        assertThat(pagerAdapter.shouldShowEmptyStateScreen(workListAdapter)).isTrue()
        assertThat(pagerAdapter.shouldShowEmptyStateScreen(personalListAdapter)).isFalse()
    }

    @Test
    fun testPresumedQuietModeEmptyStateForWorkProfile_notWhenNotQuiet() {
        // TODO: this is "presumed" because the conditions to determine whether we "should" show an
        // empty state aren't enforced to align with the conditions when we actually *would* -- I
        // believe `shouldShowEmptyStateScreen` should be implemented in terms of the provider?
        val personalListAdapter =
            mock<ResolverListAdapter> {
                whenever(getUserHandle()).thenReturn(PERSONAL_USER_HANDLE)
                whenever(getUnfilteredCount()).thenReturn(1)
            }
        val workListAdapter =
            mock<ResolverListAdapter> {
                whenever(getUserHandle()).thenReturn(WORK_USER_HANDLE)
                whenever(getUnfilteredCount()).thenReturn(1)
            }
        val pagerAdapter =
            MultiProfilePagerAdapter(
                { listAdapter: ResolverListAdapter -> listAdapter },
                { listView: ListView, bindAdapter: ResolverListAdapter ->
                    listView.setAdapter(bindAdapter)
                },
                ImmutableList.of(
                    TabConfig(
                        PROFILE_PERSONAL,
                        "personal",
                        "personal_a11y",
                        "TAG_PERSONAL",
                        personalListAdapter
                    ),
                    TabConfig(PROFILE_WORK, "work", "work_a11y", "TAG_WORK", workListAdapter)
                ),
                object : EmptyStateProvider {},
                { false }, // <-- Work mode is not quiet.
                PROFILE_WORK,
                WORK_USER_HANDLE,
                null,
                inflater,
                { Optional.empty() }
            )
        assertThat(pagerAdapter.shouldShowEmptyStateScreen(workListAdapter)).isFalse()
        assertThat(pagerAdapter.shouldShowEmptyStateScreen(personalListAdapter)).isFalse()
    }
}
