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

package com.android.car.carlauncher.recents;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;


/**
 * Utility methods that are used by Recents classes.
 */
public class RecentsUtils {
    /**
     * Checks if the items in the RecyclerView are arranged from right to left.
     *
     * @return true if the layout is right to left.
     */
    public static boolean areItemsRightToLeft(@NonNull RecyclerView recyclerView) {
        boolean isLayoutReversed = false;
        if (recyclerView.getLayoutManager() instanceof LinearLayoutManager) {
            isLayoutReversed = ((LinearLayoutManager) recyclerView.getLayoutManager())
                    .getReverseLayout();
        }
        return recyclerView.isLayoutRtl() ^ isLayoutReversed;
    }
}
