/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.tv.settings.library.instrumentation;

import static com.android.internal.jank.InteractionJankMonitor.CUJ_SETTINGS_PAGE_SCROLL;
import static com.android.internal.jank.InteractionJankMonitor.Configuration;

import androidx.leanback.preference.LeanbackPreferenceFragmentCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.android.internal.jank.InteractionJankMonitor;

/**
 * Instrumented fragment that logs jank.
 */
public abstract class InstrumentedPreferenceFragment extends LeanbackPreferenceFragmentCompat {

    private RecyclerView.OnScrollListener mOnScrollListener;

    @Override
    public void onResume() {
        // Add scroll listener to trace interaction jank.
        final RecyclerView recyclerView = getListView();
        if (recyclerView != null) {
            mOnScrollListener = new OnScrollListener(getClass().getName());
            recyclerView.addOnScrollListener(mOnScrollListener);
        }
        super.onResume();
    }

    @Override
    public void onPause() {
        final RecyclerView recyclerView = getListView();
        if (recyclerView != null && mOnScrollListener != null) {
            recyclerView.removeOnScrollListener(mOnScrollListener);
            mOnScrollListener = null;
        }
        super.onPause();
    }

    private static final class OnScrollListener extends RecyclerView.OnScrollListener {
        private final InteractionJankMonitor mMonitor = InteractionJankMonitor.getInstance();
        private final String mClassName;

        private OnScrollListener(String className) {
            mClassName = className;
        }

        @Override
        public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
            switch (newState) {
                case RecyclerView.SCROLL_STATE_SETTLING -> {
                    final Configuration.Builder builder =
                            Configuration.Builder.withView(CUJ_SETTINGS_PAGE_SCROLL, recyclerView)
                                    .setTag(mClassName);
                    mMonitor.begin(builder);
                }
                case RecyclerView.SCROLL_STATE_IDLE -> mMonitor.end(CUJ_SETTINGS_PAGE_SCROLL);
                default -> {
                }
            }
        }
    }
}