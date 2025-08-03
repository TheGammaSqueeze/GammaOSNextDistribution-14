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

package android.tools.utils

import android.tools.traces.surfaceflinger.LayersTrace

class MockLayersTraceBuilder(
    private var entries: MutableList<MockLayerTraceEntryBuilder> = mutableListOf()
) {
    fun addEntry(entry: MockLayerTraceEntryBuilder) {
        entries.add(entry)
    }

    fun sortEntriesBasedOfCurrentTimestamps() {
        entries.sortBy { it.timestamp }
    }

    fun build(): LayersTrace {
        require(entries.zipWithNext { prev, cur -> prev.timestamp < cur.timestamp }.all { it }) {
            "Timestamps not strictly increasing between entries."
        }

        val entries = entries.map { it.build() }
        return LayersTrace(entries)
    }
}
