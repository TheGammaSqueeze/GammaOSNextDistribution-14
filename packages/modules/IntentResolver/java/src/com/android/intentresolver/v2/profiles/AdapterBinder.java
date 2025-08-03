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

/**
 * Delegate to set up a given adapter and page view to be used together.
 *
 * @param <PageViewT>          (as in {@link MultiProfilePagerAdapter}).
 * @param <SinglePageAdapterT> (as in {@link MultiProfilePagerAdapter}).
 */
public interface AdapterBinder<PageViewT, SinglePageAdapterT> {
    /**
     * The given {@code view} will be associated with the given {@code adapter}. Do any work
     * necessary to configure them compatibly, introduce them to each other, etc.
     */
    void bind(PageViewT view, SinglePageAdapterT adapter);
}
