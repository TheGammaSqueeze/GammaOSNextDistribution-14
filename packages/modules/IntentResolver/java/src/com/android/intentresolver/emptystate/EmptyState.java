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

/**
 * Model for the "empty state"/"blocker" UI to display instead of a profile tab's normal contents.
 */
public interface EmptyState {
    /**
     * Get the title to show on the empty state.
     */
    @Nullable
    default String getTitle() {
        return null;
    }

    /**
     * Get the subtitle string to show underneath the title on the empty state.
     */
    @Nullable
    default String getSubtitle()  {
        return null;
    }

    /**
     * Get the handler for an optional button associated with this empty state. If the result is
     * non-null, the empty-state UI will be built with a button that dispatches this handler.
     */
    @Nullable
    default ClickListener getButtonClickListener()  {
        return null;
    }

    /**
     * Get whether to show the default UI for the empty state. If true, the UI will show the default
     * blocker text ('No apps can perform this action') and style; title and subtitle are ignored.
     */
    default boolean useDefaultEmptyView() {
        return false;
    }

    /**
     * Returns true if for this empty state we should skip rebuilding of the apps list
     * for this tab.
     */
    default boolean shouldSkipDataRebuild() {
        return false;
    }

    /**
     * Called when empty state is shown, could be used e.g. to track analytics events.
     */
    default void onEmptyStateShown() {}

    interface ClickListener {
        void onClick(TabControl currentTab);
    }

    interface TabControl {
        void showSpinner();
    }
}
