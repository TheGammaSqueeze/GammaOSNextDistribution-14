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
package com.android.intentresolver.emptystate;

import android.view.View;
import android.view.ViewGroup;

/**
 * Helper for building `MultiProfilePagerAdapter` tab UIs for profile tabs that are "blocked" by
 * some empty-state status.
 */
public class EmptyStateUiHelper {
    private final View mEmptyStateView;

    public EmptyStateUiHelper(ViewGroup rootView) {
        mEmptyStateView =
                rootView.requireViewById(com.android.internal.R.id.resolver_empty_state);
    }

    public void resetViewVisibilities() {
        mEmptyStateView.requireViewById(com.android.internal.R.id.resolver_empty_state_title)
                .setVisibility(View.VISIBLE);
        mEmptyStateView.requireViewById(com.android.internal.R.id.resolver_empty_state_subtitle)
                .setVisibility(View.VISIBLE);
        mEmptyStateView.requireViewById(com.android.internal.R.id.resolver_empty_state_button)
                .setVisibility(View.INVISIBLE);
        mEmptyStateView.requireViewById(com.android.internal.R.id.resolver_empty_state_progress)
                .setVisibility(View.GONE);
        mEmptyStateView.requireViewById(com.android.internal.R.id.empty)
                .setVisibility(View.GONE);
        mEmptyStateView.setVisibility(View.VISIBLE);
    }

    public void showSpinner() {
        mEmptyStateView.requireViewById(com.android.internal.R.id.resolver_empty_state_title)
                .setVisibility(View.INVISIBLE);
        // TODO: subtitle?
        mEmptyStateView.requireViewById(com.android.internal.R.id.resolver_empty_state_button)
                .setVisibility(View.INVISIBLE);
        mEmptyStateView.requireViewById(com.android.internal.R.id.resolver_empty_state_progress)
                .setVisibility(View.VISIBLE);
        mEmptyStateView.requireViewById(com.android.internal.R.id.empty)
                .setVisibility(View.GONE);
    }

    public void hide() {
        mEmptyStateView.setVisibility(View.GONE);
    }
}

