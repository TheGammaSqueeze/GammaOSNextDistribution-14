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

package android.tools.flicker.extractors

import android.tools.io.Reader

class ShellTransitionScenarioExtractor(
    private val transitionMatcher: ITransitionMatcher,
) : ScenarioExtractor {
    override fun extract(reader: Reader): List<TraceSlice> {
        val transitionsTrace = reader.readTransitionsTrace() ?: error("Missing transitions trace")
        val completeTransitions = transitionsTrace.entries.filter { !it.isIncomplete }

        val transitions = transitionMatcher.findAll(completeTransitions)

        return transitions.map {
            val startTimestamp = Utils.interpolateStartTimestampFromTransition(it, reader)
            val endTimestamp = Utils.interpolateFinishTimestampFromTransition(it, reader)

            TraceSlice(startTimestamp, endTimestamp, associatedTransition = it)
        }
    }
}
