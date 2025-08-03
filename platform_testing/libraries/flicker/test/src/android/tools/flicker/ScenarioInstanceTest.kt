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

import android.tools.Rotation
import android.tools.Timestamps
import android.tools.flicker.assertions.FlickerTest
import android.tools.flicker.assertors.AssertionTemplate
import android.tools.flicker.config.FlickerConfigEntry
import android.tools.flicker.config.ScenarioId
import android.tools.flicker.extractors.ScenarioExtractor
import android.tools.flicker.extractors.TraceSlice
import android.tools.flicker.subject.exceptions.SimpleFlickerAssertionError
import android.tools.getTraceReaderFromScenario
import android.tools.io.Reader
import com.google.common.truth.Truth
import org.junit.Test

class ScenarioInstanceTest {
    @Test
    fun willReportFlickerAssertions() {
        val errorMessage = "My Error"
        val scenarioInstance =
            ScenarioInstanceImpl(
                config =
                    FlickerConfigEntry(
                        scenarioId = ScenarioId("MY_CUSTOM_SCENARIO"),
                        extractor =
                            object : ScenarioExtractor {
                                override fun extract(reader: Reader): List<TraceSlice> {
                                    return listOf(TraceSlice(Timestamps.min(), Timestamps.max()))
                                }
                            },
                        assertions =
                            mapOf(
                                object : AssertionTemplate("myAssertionSingle") {
                                    override fun doEvaluate(
                                        scenarioInstance: ScenarioInstance,
                                        flicker: FlickerTest
                                    ) {
                                        flicker.assertLayers {
                                            throw SimpleFlickerAssertionError(errorMessage)
                                        }
                                    }
                                } to AssertionInvocationGroup.BLOCKING,
                                object : AssertionTemplate("myAssertionMultiple") {
                                    override fun doEvaluate(
                                        scenarioInstance: ScenarioInstance,
                                        flicker: FlickerTest
                                    ) {
                                        flicker.assertLayers {
                                            // No errors
                                        }
                                        flicker.assertLayers {
                                            throw SimpleFlickerAssertionError(errorMessage)
                                        }
                                        flicker.assertWmStart {
                                            throw SimpleFlickerAssertionError(errorMessage)
                                        }
                                        flicker.assertWm {
                                            // No errors
                                        }
                                    }
                                } to AssertionInvocationGroup.BLOCKING
                            ),
                        enabled = true
                    ),
                startRotation = Rotation.ROTATION_0,
                endRotation = Rotation.ROTATION_90,
                startTimestamp = Timestamps.min(),
                endTimestamp = Timestamps.max(),
                reader = getTraceReaderFromScenario("AppLaunch"),
            )

        val assertions = scenarioInstance.generateAssertions()
        Truth.assertThat(assertions).hasSize(2)

        val results = assertions.map { it.execute() }
        Truth.assertThat(results.map { it.name }).contains("MY_CUSTOM_SCENARIO::myAssertionSingle")
        Truth.assertThat(results.map { it.name })
            .contains("MY_CUSTOM_SCENARIO::myAssertionMultiple")

        val singleAssertionResult =
            results.first { it.name == "MY_CUSTOM_SCENARIO::myAssertionSingle" }
        val multipleAssertionResult =
            results.first { it.name == "MY_CUSTOM_SCENARIO::myAssertionMultiple" }

        Truth.assertThat(singleAssertionResult.failed).isTrue()
        Truth.assertThat(singleAssertionResult.assertionErrors).hasSize(1)
        Truth.assertThat(singleAssertionResult.assertionErrors.first())
            .hasMessageThat()
            .startsWith(errorMessage)

        Truth.assertThat(multipleAssertionResult.failed).isTrue()
        Truth.assertThat(multipleAssertionResult.assertionErrors).hasSize(2)
        Truth.assertThat(multipleAssertionResult.assertionErrors.first())
            .hasMessageThat()
            .startsWith(errorMessage)
        Truth.assertThat(multipleAssertionResult.assertionErrors.last())
            .hasMessageThat()
            .startsWith(errorMessage)
    }

    @Test
    fun willReportMainBlockAssertions() {
        val errorMessage = "My Error"
        val scenarioInstance =
            ScenarioInstanceImpl(
                config =
                    FlickerConfigEntry(
                        scenarioId = ScenarioId("MY_CUSTOM_SCENARIO"),
                        extractor =
                            object : ScenarioExtractor {
                                override fun extract(reader: Reader): List<TraceSlice> {
                                    return listOf(TraceSlice(Timestamps.min(), Timestamps.max()))
                                }
                            },
                        assertions =
                            mapOf(
                                object : AssertionTemplate("myAssertion1") {
                                    override fun doEvaluate(
                                        scenarioInstance: ScenarioInstance,
                                        flicker: FlickerTest
                                    ) {
                                        throw SimpleFlickerAssertionError(errorMessage)
                                    }
                                } to AssertionInvocationGroup.BLOCKING,
                                object : AssertionTemplate("myAssertion2") {
                                    override fun doEvaluate(
                                        scenarioInstance: ScenarioInstance,
                                        flicker: FlickerTest
                                    ) {
                                        flicker.assertLayers {
                                            throw SimpleFlickerAssertionError("Some flicker error")
                                        }
                                        throw SimpleFlickerAssertionError(errorMessage)
                                    }
                                } to AssertionInvocationGroup.BLOCKING
                            ),
                        enabled = true
                    ),
                startRotation = Rotation.ROTATION_0,
                endRotation = Rotation.ROTATION_90,
                startTimestamp = Timestamps.min(),
                endTimestamp = Timestamps.max(),
                reader = getTraceReaderFromScenario("AppLaunch"),
            )

        val assertions = scenarioInstance.generateAssertions()
        Truth.assertThat(assertions).hasSize(2)

        val results = assertions.map { it.execute() }
        Truth.assertThat(results.map { it.name }).contains("MY_CUSTOM_SCENARIO::myAssertion1")
        Truth.assertThat(results.map { it.name }).contains("MY_CUSTOM_SCENARIO::myAssertion2")

        val assertion1Result = results.first { it.name == "MY_CUSTOM_SCENARIO::myAssertion1" }
        Truth.assertThat(assertion1Result.failed).isTrue()
        Truth.assertThat(assertion1Result.assertionErrors).hasSize(1)
        Truth.assertThat(assertion1Result.assertionErrors.first())
            .hasMessageThat()
            .startsWith(errorMessage)

        val assertion2Result = results.first { it.name == "MY_CUSTOM_SCENARIO::myAssertion2" }
        Truth.assertThat(assertion2Result.failed).isTrue()
        Truth.assertThat(assertion2Result.assertionErrors).hasSize(2)
        Truth.assertThat(assertion2Result.assertionErrors.first().message)
            .contains("Some flicker error")
        Truth.assertThat(assertion2Result.assertionErrors.drop(1).first().message)
            .contains(errorMessage)
    }
}
