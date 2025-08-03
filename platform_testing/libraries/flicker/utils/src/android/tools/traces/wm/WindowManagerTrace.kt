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

import android.tools.Rotation
import android.tools.Timestamp
import android.tools.Trace

/**
 * Contains a collection of parsed WindowManager trace entries and assertions to apply over a single
 * entry.
 *
 * Each entry is parsed into a list of [WindowManagerState] objects.
 *
 * This is a generic object that is reused by both Flicker and Winscope and cannot access internal
 * Java/Android functionality
 */
data class WindowManagerTrace(override val entries: Collection<WindowManagerState>) :
    Trace<WindowManagerState> {

    val isTablet: Boolean
        get() = entries.any { it.isTablet }

    override fun toString(): String {
        return "WindowManagerTrace(Start: ${entries.firstOrNull()}, " +
            "End: ${entries.lastOrNull()})"
    }

    /** Get the initial rotation */
    fun getInitialRotation(): Rotation {
        if (entries.isEmpty()) {
            throw RuntimeException("WindowManager Trace has no entries")
        }
        val firstWmState = entries.first()
        return firstWmState.policy?.rotation
            ?: run { throw RuntimeException("Wm state has no policy") }
    }

    /** Get the final rotation */
    fun getFinalRotation(): Rotation {
        if (entries.isEmpty()) {
            throw RuntimeException("WindowManager Trace has no entries")
        }
        val lastWmState = entries.last()
        return lastWmState.policy?.rotation
            ?: run { throw RuntimeException("Wm state has no policy") }
    }

    override fun slice(startTimestamp: Timestamp, endTimestamp: Timestamp): WindowManagerTrace {
        return WindowManagerTrace(
            entries
                .dropWhile { it.timestamp < startTimestamp }
                .dropLastWhile { it.timestamp > endTimestamp }
        )
    }

    fun getWindowDescriptorById(id: Int): WindowDescriptor? {
        for (entry in this.entries) {
            for (window in entry.windowContainers) {
                if (window.id == id) {
                    return WindowDescriptor(window)
                }
            }
        }
        return null
    }
}
