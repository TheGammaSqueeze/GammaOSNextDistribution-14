/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.tools.flicker

import android.tools.Logger
import android.tools.Rotation
import android.tools.Timestamp
import android.tools.flicker.assertions.ScenarioAssertion
import android.tools.flicker.assertions.ScenarioAssertionImpl
import android.tools.flicker.config.FlickerConfigEntry
import android.tools.flicker.extractors.TraceSlice
import android.tools.flicker.extractors.Utils
import android.tools.io.Reader
import android.tools.traces.events.CujType
import android.tools.traces.wm.Transition

data class ScenarioInstanceImpl(
    override val config: FlickerConfigEntry,
    override val startRotation: Rotation,
    override val endRotation: Rotation,
    val startTimestamp: Timestamp,
    val endTimestamp: Timestamp,
    override val reader: Reader,
    val associatedCuj: CujType? = null,
    override val associatedTransition: Transition? = null,
) : ScenarioInstance {
    // b/227752705
    override val navBarMode
        get() = error("Unsupported")

    override val key = "${config.scenarioId.name}_${startRotation}_$endRotation"

    override val description = key

    override val isEmpty = false

    override fun <T> getConfigValue(key: String): T? = null

    override fun generateAssertions(): Collection<ScenarioAssertion> =
        Logger.withTracing("generateAssertions") {
            val assertionExtraData =
                mutableMapOf<String, String>().apply {
                    this["Scenario Start"] = startTimestamp.toString()
                    this["Scenario End"] = endTimestamp.toString()
                    this["Associated CUJ"] = associatedCuj.toString()
                    this["Associated Transition"] = associatedTransition.toString()
                }

            config.assertions.map { (template, stabilityGroup) ->
                ScenarioAssertionImpl(
                    template.qualifiedAssertionName(this),
                    reader,
                    template.createAssertions(this),
                    stabilityGroup,
                    assertionExtraData
                )
            }
        }

    override fun toString() = key

    companion object {
        fun fromSlice(
            traceSlice: TraceSlice,
            reader: Reader,
            config: FlickerConfigEntry
        ): ScenarioInstanceImpl {
            val layersTrace = reader.readLayersTrace() ?: error("Missing layers trace")
            val startTimestamp = traceSlice.startTimestamp
            val endTimestamp = traceSlice.endTimestamp

            val displayAtStart =
                Utils.getOnDisplayFor(layersTrace.getFirstEntryWithOnDisplayAfter(startTimestamp))
            val displayAtEnd =
                Utils.getOnDisplayFor(layersTrace.getLastEntryWithOnDisplayBefore(endTimestamp))

            return ScenarioInstanceImpl(
                config,
                startRotation = displayAtStart.transform.getRotation(),
                endRotation = displayAtEnd.transform.getRotation(),
                startTimestamp = startTimestamp,
                endTimestamp = endTimestamp,
                associatedCuj = traceSlice.associatedCuj,
                associatedTransition = traceSlice.associatedTransition,
                reader = reader.slice(startTimestamp, endTimestamp)
            )
        }
    }
}
