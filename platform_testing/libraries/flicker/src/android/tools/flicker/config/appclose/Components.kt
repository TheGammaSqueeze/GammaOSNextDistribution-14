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

package android.tools.flicker.config.appclose

import android.tools.flicker.ScenarioInstance
import android.tools.flicker.assertors.ComponentTemplate
import android.tools.flicker.isAppTransitionChange
import android.tools.traces.component.FullComponentIdMatcher
import android.tools.traces.component.IComponentMatcher
import android.tools.traces.surfaceflinger.LayersTrace
import android.tools.traces.wm.Transition
import android.tools.traces.wm.TransitionType
import android.tools.traces.wm.WindowManagerTrace

object Components {
    val CLOSING_APPS =
        ComponentTemplate("CLOSING_APP(S)") { scenarioInstance: ScenarioInstance ->
            closingAppFrom(
                scenarioInstance.associatedTransition ?: error("Missing associated transition"),
                scenarioInstance.reader.readLayersTrace(),
                scenarioInstance.reader.readWmTrace()
            )
        }

    private fun closingAppFrom(
        transition: Transition,
        layersTrace: LayersTrace?,
        wmTrace: WindowManagerTrace?
    ): IComponentMatcher {
        val targetChanges =
            transition.changes.filter {
                (it.transitMode == TransitionType.CLOSE ||
                    it.transitMode == TransitionType.TO_BACK) &&
                    isAppTransitionChange(it, layersTrace, wmTrace)
            }

        val closingAppMatchers =
            targetChanges.map { FullComponentIdMatcher(it.windowId, it.layerId) }

        return closingAppMatchers.reduce<IComponentMatcher, IComponentMatcher> { acc, matcher ->
            acc.or(matcher)
        }
    }
}
