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

import android.tools.flicker.config.AssertionTemplates
import android.tools.flicker.config.FlickerConfigEntry
import android.tools.flicker.config.ScenarioId
import android.tools.flicker.config.TransitionFilters
import android.tools.flicker.extractors.CujAdjust
import android.tools.flicker.extractors.TaggedCujTransitionMatcher
import android.tools.flicker.extractors.TaggedScenarioExtractorBuilder
import android.tools.io.Reader
import android.tools.traces.events.Cuj
import android.tools.traces.events.CujType

val AppCloseToPip =
    FlickerConfigEntry(
        enabled = false,
        scenarioId = ScenarioId("APP_CLOSE_TO_PIP"),
        assertions = AssertionTemplates.APP_CLOSE_TO_PIP_ASSERTIONS,
        extractor =
            TaggedScenarioExtractorBuilder()
                .setTargetTag(CujType.CUJ_LAUNCHER_APP_CLOSE_TO_PIP)
                .setTransitionMatcher(
                    TaggedCujTransitionMatcher(TransitionFilters.APP_CLOSE_TO_PIP_TRANSITION_FILTER)
                )
                .setAdjustCuj(
                    object : CujAdjust {
                        override fun adjustCuj(cujEntry: Cuj, reader: Reader): Cuj {
                            val cujTrace = reader.readCujTrace() ?: error("Missing CUJ trace")
                            val closeToHomeCuj =
                                cujTrace.entries.firstOrNull {
                                    it.cuj == CujType.CUJ_LAUNCHER_APP_CLOSE_TO_HOME &&
                                        it.startTimestamp <= cujEntry.startTimestamp &&
                                        cujEntry.startTimestamp <= it.endTimestamp
                                }

                            return if (closeToHomeCuj == null) {
                                cujEntry
                            } else {
                                Cuj(
                                    cujEntry.cuj,
                                    closeToHomeCuj.startTimestamp,
                                    cujEntry.endTimestamp,
                                    cujEntry.canceled
                                )
                            }
                        }
                    }
                )
                .build()
    )
