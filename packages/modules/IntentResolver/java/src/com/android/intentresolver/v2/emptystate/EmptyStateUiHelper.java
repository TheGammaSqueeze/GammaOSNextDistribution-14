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
package com.android.intentresolver.v2.emptystate;

import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import com.android.intentresolver.emptystate.EmptyState;
import com.android.internal.annotations.VisibleForTesting;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Helper for building `MultiProfilePagerAdapter` tab UIs for profile tabs that are "blocked" by
 * some empty-state status.
 */
public class EmptyStateUiHelper {
    private final Supplier<Optional<Integer>> mContainerBottomPaddingOverrideSupplier;
    private final View mEmptyStateView;
    private final View mListView;
    private final View mEmptyStateContainerView;
    private final TextView mEmptyStateTitleView;
    private final TextView mEmptyStateSubtitleView;
    private final Button mEmptyStateButtonView;
    private final View mEmptyStateProgressView;
    private final View mEmptyStateEmptyView;

    public EmptyStateUiHelper(
            ViewGroup rootView,
            int listViewResourceId,
            Supplier<Optional<Integer>> containerBottomPaddingOverrideSupplier) {
        mContainerBottomPaddingOverrideSupplier = containerBottomPaddingOverrideSupplier;
        mEmptyStateView =
                rootView.requireViewById(com.android.internal.R.id.resolver_empty_state);
        mListView = rootView.requireViewById(listViewResourceId);
        mEmptyStateContainerView = mEmptyStateView.requireViewById(
                com.android.internal.R.id.resolver_empty_state_container);
        mEmptyStateTitleView = mEmptyStateView.requireViewById(
                com.android.internal.R.id.resolver_empty_state_title);
        mEmptyStateSubtitleView = mEmptyStateView.requireViewById(
                com.android.internal.R.id.resolver_empty_state_subtitle);
        mEmptyStateButtonView = mEmptyStateView.requireViewById(
                com.android.internal.R.id.resolver_empty_state_button);
        mEmptyStateProgressView = mEmptyStateView.requireViewById(
                com.android.internal.R.id.resolver_empty_state_progress);
        mEmptyStateEmptyView = mEmptyStateView.requireViewById(com.android.internal.R.id.empty);
    }

    /**
     * Display the described empty state.
     * @param emptyState the data describing the cause of this empty-state condition.
     * @param buttonOnClick handler for a button that the user might be able to use to circumvent
     * the empty-state condition. If null, no button will be displayed.
     */
    public void showEmptyState(EmptyState emptyState, View.OnClickListener buttonOnClick) {
        resetViewVisibilities();
        setupContainerPadding();

        String title = emptyState.getTitle();
        if (title != null) {
            mEmptyStateTitleView.setVisibility(View.VISIBLE);
            mEmptyStateTitleView.setText(title);
        } else {
            mEmptyStateTitleView.setVisibility(View.GONE);
        }

        String subtitle = emptyState.getSubtitle();
        if (subtitle != null) {
            mEmptyStateSubtitleView.setVisibility(View.VISIBLE);
            mEmptyStateSubtitleView.setText(subtitle);
        } else {
            mEmptyStateSubtitleView.setVisibility(View.GONE);
        }

        mEmptyStateEmptyView.setVisibility(
                emptyState.useDefaultEmptyView() ? View.VISIBLE : View.GONE);
        // TODO: The EmptyState API says that if `useDefaultEmptyView()` is true, we'll ignore the
        // state's specified title/subtitle; where (if anywhere) is that implemented?

        mEmptyStateButtonView.setVisibility(buttonOnClick != null ? View.VISIBLE : View.GONE);
        mEmptyStateButtonView.setOnClickListener(buttonOnClick);

        // Don't show the main list view when we're showing an empty state.
        mListView.setVisibility(View.GONE);
    }

    /** Sets up the padding of the view containing the empty state screens. */
    public void setupContainerPadding() {
        Optional<Integer> bottomPaddingOverride = mContainerBottomPaddingOverrideSupplier.get();
        bottomPaddingOverride.ifPresent(paddingBottom ->
                mEmptyStateContainerView.setPadding(
                    mEmptyStateContainerView.getPaddingLeft(),
                    mEmptyStateContainerView.getPaddingTop(),
                    mEmptyStateContainerView.getPaddingRight(),
                    paddingBottom));
    }

    public void showSpinner() {
        mEmptyStateTitleView.setVisibility(View.INVISIBLE);
        // TODO: subtitle?
        mEmptyStateButtonView.setVisibility(View.INVISIBLE);
        mEmptyStateProgressView.setVisibility(View.VISIBLE);
        mEmptyStateEmptyView.setVisibility(View.GONE);
    }

    public void hide() {
        mEmptyStateView.setVisibility(View.GONE);
        mListView.setVisibility(View.VISIBLE);
    }

    // TODO: this is exposed for testing so we can thoroughly prepare initial conditions that let us
    // observe the resulting change. In reality it's only invoked as part of `showEmptyState()` and
    // we could consider setting up narrower "realistic" preconditions to make assertions about the
    // higher-level operation.
    @VisibleForTesting
    void resetViewVisibilities() {
        mEmptyStateTitleView.setVisibility(View.VISIBLE);
        mEmptyStateSubtitleView.setVisibility(View.VISIBLE);
        mEmptyStateButtonView.setVisibility(View.INVISIBLE);
        mEmptyStateProgressView.setVisibility(View.GONE);
        mEmptyStateEmptyView.setVisibility(View.GONE);
        mEmptyStateView.setVisibility(View.VISIBLE);
    }
}

