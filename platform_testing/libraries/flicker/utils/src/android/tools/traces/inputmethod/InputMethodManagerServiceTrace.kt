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

package android.tools.traces.inputmethod

import android.tools.Timestamp
import android.tools.Trace

/**
 * Contains a collection of parsed InputMethodManagerService trace entries and assertions to apply
 * over a single entry
 *
 * Each entry is parsed into a list of [InputMethodManagerServiceEntry] objects.
 *
 * This is a generic object that is reused by both Flicker and Winscope and cannot access internal
 * Java/Android functionality
 */
data class InputMethodManagerServiceTrace(
    override val entries: Collection<InputMethodManagerServiceEntry>
) : Trace<InputMethodManagerServiceEntry> {
    override fun toString(): String {
        return "InputMethodManagerServiceTrace(Start: ${entries.firstOrNull()}, " +
            "End: ${entries.lastOrNull()})"
    }

    /**
     * Split the trace by the start and end timestamp.
     *
     * @param startTimestamp the start timestamp
     * @param endTimestamp the end timestamp
     * @return the sub-trace trace(from, to)
     */
    override fun slice(
        startTimestamp: Timestamp,
        endTimestamp: Timestamp
    ): InputMethodManagerServiceTrace {
        return InputMethodManagerServiceTrace(
            this.entries
                .dropWhile { it.timestamp < startTimestamp }
                .dropLastWhile { it.timestamp > endTimestamp }
        )
    }
}
