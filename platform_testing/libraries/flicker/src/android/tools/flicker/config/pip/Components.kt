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

package android.tools.flicker.config.pip

import android.tools.flicker.ScenarioInstance
import android.tools.flicker.assertors.ComponentTemplate
import android.tools.flicker.config.ScenarioId
import android.tools.traces.component.ComponentNameMatcher
import android.tools.traces.component.FullComponentIdMatcher
import android.tools.traces.wm.TransitionType

object Components {
    val PIP_DISMISS_OVERLAY =
        ComponentTemplate("PipDismissOverlay") { ComponentNameMatcher("", "pip-dismiss-overlay") }
    val PIP_CONTENT_OVERLAY =
        ComponentTemplate("PipContentOverlay") { ComponentNameMatcher.PIP_CONTENT_OVERLAY }
    val PIP_APP =
        ComponentTemplate("PIP") { scenarioInstance: ScenarioInstance ->
            if (scenarioInstance.type == ScenarioId("LAUNCHER_APP_CLOSE_TO_PIP")) {
                val associatedTransition =
                    scenarioInstance.associatedTransition ?: error("Missing associated transition")
                val change =
                    associatedTransition.changes.firstOrNull {
                        it.transitMode == TransitionType.TO_BACK
                    }
                        ?: error("Missing to back change")
                FullComponentIdMatcher(change.windowId, change.layerId)
            } else {
                error("Unhandled case - can't get PiP app for this case")
            }
        }
}
