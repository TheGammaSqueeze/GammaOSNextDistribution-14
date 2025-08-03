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

enum class TransitionType(val value: Int) {
    UNDEFINED(-1),
    NONE(0),
    OPEN(1),
    CLOSE(2),
    TO_FRONT(3),
    TO_BACK(4),
    RELAUNCH(5),
    CHANGE(6),
    KEYGUARD_GOING_AWAY(7),
    KEYGUARD_OCCLUDE(8),
    KEYGUARD_UNOCCLUDE(9),
    PIP(10),
    WAKE(11),
    SLEEP(12),

    // START OF CUSTOM TYPES
    FIRST_CUSTOM(1000),
    EXIT_PIP(FIRST_CUSTOM.value + 1),
    EXIT_PIP_TO_SPLIT(FIRST_CUSTOM.value + 2),
    REMOVE_PIP(FIRST_CUSTOM.value + 3),
    SPLIT_SCREEN_PAIR_OPEN(FIRST_CUSTOM.value + 4),
    SPLIT_SCREEN_OPEN_TO_SIDE(FIRST_CUSTOM.value + 5),
    SPLIT_DISMISS_SNAP(FIRST_CUSTOM.value + 6),
    SPLIT_DISMISS(FIRST_CUSTOM.value + 7),
    MAXIMIZE(FIRST_CUSTOM.value + 8),
    RESTORE_FROM_MAXIMIZE(FIRST_CUSTOM.value + 9),
    ENTER_FREEFORM(FIRST_CUSTOM.value + 10),
    ENTER_DESKTOP_MODE(FIRST_CUSTOM.value + 11),
    EXIT_DESKTOP_MODE(FIRST_CUSTOM.value + 12);

    companion object {
        fun fromInt(value: Int) = values().first { it.value == value }
    }
}
