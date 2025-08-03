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

package android.tools.traces.region

import android.tools.Timestamp
import android.tools.Trace
import android.tools.traces.component.IComponentMatcher

/**
 * Contains a collection of parsed Region trace entries.
 *
 * Each entry is parsed into a list of [RegionEntry] objects.
 *
 * This is a generic object that is reused by both Flicker and Winscope and cannot access internal
 * Java/Android functionality
 */
data class RegionTrace(
    val components: IComponentMatcher?,
    override val entries: Collection<RegionEntry>
) : Trace<RegionEntry> {

    override fun slice(startTimestamp: Timestamp, endTimestamp: Timestamp): Trace<RegionEntry> {
        return RegionTrace(
            components,
            entries
                .dropWhile { it.timestamp < startTimestamp }
                .dropLastWhile { it.timestamp > endTimestamp }
        )
    }
}
