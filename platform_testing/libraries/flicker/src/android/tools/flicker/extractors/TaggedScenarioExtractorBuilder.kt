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
import android.tools.traces.events.Cuj
import android.tools.traces.events.CujType

class TaggedScenarioExtractorBuilder {
    private var targetTag: CujType? = null
    private var transitionMatcher: TransitionMatcher? = null
    private var adjustCuj: CujAdjust =
        object : CujAdjust {
            override fun adjustCuj(cujEntry: Cuj, reader: Reader): Cuj = cujEntry
        }
    private var ignoreIfNoMatchingTransition: Boolean = false

    fun setTargetTag(value: CujType): TaggedScenarioExtractorBuilder = apply { targetTag = value }

    fun setTransitionMatcher(value: TransitionMatcher): TaggedScenarioExtractorBuilder = apply {
        transitionMatcher = value
    }

    fun setAdjustCuj(value: CujAdjust): TaggedScenarioExtractorBuilder = apply { adjustCuj = value }

    fun setIgnoreIfNoMatchingTransition(value: Boolean) = apply {
        ignoreIfNoMatchingTransition = value
    }

    fun build(): ScenarioExtractor {
        val targetTag = targetTag ?: error("Missing targetTag")
        return TaggedScenarioExtractor(
            targetTag,
            transitionMatcher,
            adjustCuj,
            ignoreIfNoMatchingTransition
        )
    }
}
