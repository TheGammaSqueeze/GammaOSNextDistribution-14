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

import android.annotation.Nullable;

import com.android.intentresolver.ResolverListAdapter;

/**
 * Empty state provider that combines multiple providers. Providers earlier in the list have
 * priority, that is if there is a provider that returns non-null empty state then all further
 * providers will be ignored.
 */
public class CompositeEmptyStateProvider implements EmptyStateProvider {

    private final EmptyStateProvider[] mProviders;

    public CompositeEmptyStateProvider(EmptyStateProvider... providers) {
        mProviders = providers;
    }

    @Nullable
    @Override
    public EmptyState getEmptyState(ResolverListAdapter resolverListAdapter) {
        for (EmptyStateProvider provider : mProviders) {
            EmptyState emptyState = provider.getEmptyState(resolverListAdapter);
            if (emptyState != null) {
                return emptyState;
            }
        }
        return null;
    }
}
