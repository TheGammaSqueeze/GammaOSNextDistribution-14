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

package com.android.car.carlauncher;

import androidx.annotation.IntDef;

/**
 * Defines the constants used for custom state attributes (e.g. scrolling state, drag state) and
 * app grid configurations (e.g. horizontal vertical paging).
 */
public interface AppGridConstants {
    /**
     * Page orientation for app grid. {@code PageOrientation.HORIZONTAL} will should support left
     * and right paging. {@code PageOrientation.VERTICAL} will support up and down paging.
     *
     * The constant is defined separately here to differentiate this variable from device
     * orientation, layout manager orientation, and layout param orientation.
     */
    @IntDef({
            PageOrientation.HORIZONTAL,
            PageOrientation.VERTICAL,
    })
    @interface PageOrientation {
        int HORIZONTAL = 0;
        int VERTICAL = 1;
    }

    /**
     * Integer denoting the direction of an app item. Use for adding offset to create margins
     * between pages and tracking off page drag intent.
     *
     * Only {@code TOP} and {@code BOTTOM} should be processed for configuration {@code
     * PageOrientation.VERTICAL}, and {@code LEFT} and {@code RIGHT} for
     * {@code PageOrientation.HORIZONTAL}.
     */
    @IntDef({
            AppItemBoundDirection.NONE,
            AppItemBoundDirection.TOP,
            AppItemBoundDirection.BOTTOM,
            AppItemBoundDirection.LEFT,
            AppItemBoundDirection.RIGHT,
    })
    @interface AppItemBoundDirection {
        int NONE = 0;
        int TOP = 1;
        int BOTTOM = 2;
        int LEFT = 3;
        int RIGHT = 4;
    }

    /**
     * Static method for checking if the app grid orientation value represents horizontal.
     *
     * @param pageOrientation an integer value defined in {@code PageOrientation}
     * @return true if the input {@code PageOrientation.HORIZONTAL}, false otherwise
     */
    static boolean isHorizontal(int pageOrientation) {
        return pageOrientation == PageOrientation.HORIZONTAL;
    }
}
