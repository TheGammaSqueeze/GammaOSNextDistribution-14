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

package android.tools.traces.wm

enum class WindowingMode(val value: Int) {
    /** Windowing mode is currently not defined. */
    WINDOWING_MODE_UNDEFINED(0),

    /** Occupies the full area of the screen or the parent container. */
    WINDOWING_MODE_FULLSCREEN(1),

    /** Always on-top (always visible). of other siblings in its parent container. */
    WINDOWING_MODE_PINNED(2),

    /** Can be freely resized within its parent container. */
    WINDOWING_MODE_FREEFORM(5),

    /** Generic multi-window with no presentation attribution from the window manager. */
    WINDOWING_MODE_MULTI_WINDOW(6);

    companion object {
        fun fromInt(value: Int) =
            values().firstOrNull { it.value == value }
                ?: error("No valid windowing mode for id $value")
    }
}
