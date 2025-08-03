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

package android.tools.flicker.config.splitscreen

import android.tools.flicker.ScenarioInstance
import android.tools.flicker.assertors.ComponentTemplate
import android.tools.flicker.config.ScenarioId
import android.tools.traces.component.ComponentNameMatcher
import android.tools.traces.component.FullComponentIdMatcher
import android.tools.traces.component.IComponentMatcher
import android.tools.traces.surfaceflinger.LayersTrace
import android.tools.traces.wm.Transition

object Components {
    val SPLIT_SCREEN_DIVIDER =
        ComponentTemplate("SplitScreenDivider") {
            ComponentNameMatcher("", "StageCoordinatorSplitDivider#")
        }
    val SPLIT_SCREEN_PRIMARY_APP =
        ComponentTemplate("SPLIT_SCREEN_PRIMARY_APP") { scenarioInstance: ScenarioInstance ->
            val associatedTransition =
                scenarioInstance.associatedTransition
                    ?: error(
                        "Can only extract SPLIT_SCREEN_PRIMARY_APP from scenario with transition"
                    )
            val layersTrace =
                scenarioInstance.reader.readLayersTrace() ?: error("Missing layers trace")

            when (scenarioInstance.type) {
                ScenarioId("SPLIT_SCREEN_ENTER") -> {
                    getSplitscreenOpeningComponentMatchers(associatedTransition, layersTrace)
                        .first()
                }
                ScenarioId("SPLIT_SCREEN_EXIT") -> {
                    TODO(
                        "Not implemented :: ${scenarioInstance.type} :: " +
                            "${scenarioInstance.associatedTransition}"
                    )
                }
                ScenarioId("SPLIT_SCREEN_RESIZE") -> {
                    val change = associatedTransition.changes.first()
                    FullComponentIdMatcher(change.windowId, change.layerId)
                }
                else -> error("Unsupported transition type")
            }
        }
    val SPLIT_SCREEN_SECONDARY_APP =
        ComponentTemplate("SPLIT_SCREEN_SECONDARY_APP") { scenarioInstance: ScenarioInstance ->
            val associatedTransition =
                scenarioInstance.associatedTransition
                    ?: error(
                        "Can only extract SPLIT_SCREEN_SECONDARY_APP from scenario with transition"
                    )
            val layersTrace =
                scenarioInstance.reader.readLayersTrace() ?: error("Missing layers trace")

            when (scenarioInstance.type) {
                ScenarioId("SPLIT_SCREEN_ENTER") -> {
                    getSplitscreenOpeningComponentMatchers(associatedTransition, layersTrace)[1]
                }
                ScenarioId("SPLIT_SCREEN_EXIT") -> {
                    TODO(
                        "Not implemented :: ${scenarioInstance.type} :: " +
                            "${scenarioInstance.associatedTransition}"
                    )
                }
                ScenarioId("SPLIT_SCREEN_RESIZE") -> {
                    val change = associatedTransition.changes.last()
                    FullComponentIdMatcher(change.windowId, change.layerId)
                }
                else -> error("Unsupported transition type")
            }
        }

    private fun getSplitscreenOpeningComponentMatchers(
        associatedTransition: Transition,
        layersTrace: LayersTrace
    ): List<IComponentMatcher> {
        // Task (part of changes)
        // - Task (part of changes)
        //   - SplitDecorManager
        //   - Task for app (part of changes)
        // - Task (part of changes)
        //   - SplitDecorManager
        //   - Task for app (part of changes)
        // - SplitWindowManager
        //   - StageCoordinatorSplitDividerLeash#1378

        val layerIds = associatedTransition.changes.map { it.layerId }
        val layers =
            associatedTransition.changes.map { change ->
                layersTrace.entries.last().flattenedLayers.first { it.id == change.layerId }
            }

        val appIds = layers.filter { layerIds.contains(it.parent?.parent?.id) }.map { it.id }

        val componentMatchers =
            associatedTransition.changes
                .filter { appIds.contains(it.layerId) }
                .map { FullComponentIdMatcher(it.windowId, it.layerId) }

        require(componentMatchers.size == 2) {
            "Expected to get 2 splitscreen apps but got ${componentMatchers.size}"
        }

        return componentMatchers
    }
}
