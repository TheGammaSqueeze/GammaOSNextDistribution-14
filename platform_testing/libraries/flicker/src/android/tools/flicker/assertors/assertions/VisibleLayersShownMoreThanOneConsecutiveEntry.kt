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

package android.tools.flicker.assertors.assertions

import android.tools.flicker.ScenarioInstance
import android.tools.flicker.assertions.FlickerTest
import android.tools.flicker.assertors.AssertionTemplate
import android.tools.flicker.subject.layers.LayersTraceSubject.Companion.VISIBLE_FOR_MORE_THAN_ONE_ENTRY_IGNORE_LAYERS
import android.tools.traces.component.IComponentMatcher

/**
 * Checks that all layers that are visible on the trace, are visible for at least 2 consecutive
 * entries.
 */
class VisibleLayersShownMoreThanOneConsecutiveEntry(
    private val ignore: List<IComponentMatcher> = listOf()
) : AssertionTemplate() {
    /** {@inheritDoc} */
    override fun doEvaluate(scenarioInstance: ScenarioInstance, flicker: FlickerTest) {
        flicker.assertLayers {
            visibleLayersShownMoreThanOneConsecutiveEntry(
                ignoreLayers = VISIBLE_FOR_MORE_THAN_ONE_ENTRY_IGNORE_LAYERS + ignore
            )
        }
    }
}
