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

import android.tools.datatypes.Region
import android.tools.flicker.ScenarioInstance
import android.tools.flicker.assertions.FlickerTest
import android.tools.flicker.assertors.ComponentTemplate
import android.tools.flicker.config.splitscreen.Components.SPLIT_SCREEN_DIVIDER
import android.tools.flicker.subject.layers.LayerTraceEntrySubject
import android.tools.flicker.subject.layers.LayersTraceSubject
import android.tools.traces.component.IComponentMatcher

class SplitAppLayerBoundsBecomesVisible(
    private val component: ComponentTemplate,
    val isPrimaryApp: Boolean
) : AssertionTemplateWithComponent(component) {
    override fun doEvaluate(scenarioInstance: ScenarioInstance, flicker: FlickerTest) {
        val splitScreenDivider = SPLIT_SCREEN_DIVIDER.build(scenarioInstance)
        val app = component.build(scenarioInstance)

        val landscapePosLeft: Boolean
        val portraitPosTop: Boolean
        if (isPrimaryApp) {
            landscapePosLeft = false
            portraitPosTop = false
        } else {
            // secondaryApp
            landscapePosLeft = true
            portraitPosTop = true
        }

        flicker.assertLayers {
            notContains(splitScreenDivider.or(app), isOptional = true)
                .then()
                .isInvisible(splitScreenDivider.or(app))
                .then()
                .splitAppLayerBoundsSnapToDivider(
                    app,
                    splitScreenDivider,
                    landscapePosLeft,
                    portraitPosTop
                )
        }
    }

    companion object {
        fun LayersTraceSubject.splitAppLayerBoundsSnapToDivider(
            component: IComponentMatcher,
            splitScreenDivider: IComponentMatcher,
            landscapePosLeft: Boolean,
            portraitPosTop: Boolean
        ): LayersTraceSubject {
            return invoke("splitAppLayerBoundsSnapToDivider") {
                it.splitAppLayerBoundsSnapToDivider(
                    component,
                    splitScreenDivider,
                    landscapePosLeft,
                    portraitPosTop
                )
            }
        }

        private fun LayerTraceEntrySubject.splitAppLayerBoundsSnapToDivider(
            component: IComponentMatcher,
            splitScreenDivider: IComponentMatcher,
            landscapePosLeft: Boolean,
            portraitPosTop: Boolean
        ): LayerTraceEntrySubject {
            val activeDisplay =
                this.entry.displays.firstOrNull { it.isOn && !it.isVirtual }
                    ?: error("No non-virtual and on display found")

            return invoke {
                val dividerRegion =
                    layer(splitScreenDivider)?.visibleRegion?.region
                        ?: error("$splitScreenDivider component not found")
                visibleRegion(component)
                    .coversAtMost(
                        if (
                            activeDisplay.layerStackSpace.width >
                                activeDisplay.layerStackSpace.height
                        ) {
                            if (landscapePosLeft) {
                                Region.from(
                                    0,
                                    0,
                                    (dividerRegion.bounds.left + dividerRegion.bounds.right) / 2,
                                    activeDisplay.layerStackSpace.bottom
                                )
                            } else {
                                Region.from(
                                    (dividerRegion.bounds.left + dividerRegion.bounds.right) / 2,
                                    0,
                                    activeDisplay.layerStackSpace.right,
                                    activeDisplay.layerStackSpace.bottom
                                )
                            }
                        } else {
                            if (portraitPosTop) {
                                Region.from(
                                    0,
                                    0,
                                    activeDisplay.layerStackSpace.right,
                                    (dividerRegion.bounds.top + dividerRegion.bounds.bottom) / 2
                                )
                            } else {
                                Region.from(
                                    0,
                                    (dividerRegion.bounds.top + dividerRegion.bounds.bottom) / 2,
                                    activeDisplay.layerStackSpace.right,
                                    activeDisplay.layerStackSpace.bottom
                                )
                            }
                        }
                    )
            }
        }
    }
}
