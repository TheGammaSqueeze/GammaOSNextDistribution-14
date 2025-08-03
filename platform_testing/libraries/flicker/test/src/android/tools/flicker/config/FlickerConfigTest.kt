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

package android.tools.flicker.config

import android.tools.flicker.AssertionInvocationGroup
import android.tools.flicker.FlickerConfig
import android.tools.flicker.ScenarioInstance
import android.tools.flicker.ScenarioInstanceImpl
import android.tools.flicker.assertions.FlickerTest
import android.tools.flicker.assertors.AssertionTemplate
import android.tools.flicker.extractors.EntireTraceExtractor
import android.tools.flicker.extractors.ScenarioExtractor
import android.tools.flicker.extractors.TraceSlice
import android.tools.getTraceReaderFromScenario
import android.tools.io.Reader
import android.tools.utils.assertThrows
import com.google.common.truth.Truth
import org.junit.Test

class FlickerConfigTest {
    @Test
    fun canRegisterScenario() {
        val registry = FlickerConfig()

        registry.registerScenario(SOME_SCENARIO, EXTRACTOR_FOR_SOME_SCENARIO)

        val entries = registry.getEntries()
        Truth.assertThat(entries).hasSize(1)
        Truth.assertThat(entries.first().scenarioId).isEqualTo(SOME_SCENARIO)
    }

    @Test
    fun canRegisterScenarioWithAssertions() {
        val registry = FlickerConfig()

        var executed = false
        val assertion =
            object : AssertionTemplate("Mock Assertion") {
                override fun doEvaluate(scenarioInstance: ScenarioInstance, flicker: FlickerTest) {
                    flicker.assertLayers { executed = true }
                }
            }

        registry.registerScenario(
            SOME_SCENARIO,
            EXTRACTOR_FOR_SOME_SCENARIO,
            mapOf(assertion to AssertionInvocationGroup.BLOCKING)
        )

        val entries = registry.getEntries()
        Truth.assertThat(entries).hasSize(1)
        Truth.assertThat(entries.first().scenarioId).isEqualTo(SOME_SCENARIO)

        val reader = getTraceReaderFromScenario("AppLaunch")
        val scenarioSlices = entries.first().extractor.extract(reader)
        Truth.assertThat(scenarioSlices).hasSize(1)
        val scenarioInstance =
            ScenarioInstanceImpl.fromSlice(scenarioSlices.first(), reader, entries.first())
        val assertions = scenarioInstance.generateAssertions()

        Truth.assertThat(assertions).hasSize(1)
        assertions.first().execute()
        Truth.assertThat(executed).isTrue()

        // TODO: Check invocation group is respected
    }

    @Test
    fun canUnregisterScenario() {
        val registry = FlickerConfig()

        registry.registerScenario(SOME_SCENARIO, EXTRACTOR_FOR_SOME_SCENARIO)

        var entries = registry.getEntries()
        Truth.assertThat(entries).hasSize(1)

        registry.unregisterScenario(SOME_SCENARIO)
        entries = registry.getEntries()
        Truth.assertThat(entries).hasSize(0)
    }

    // TODO: Require anonymous assertions to have a proper name

    @Test
    fun canUnregisterAssertion() {
        val registry = FlickerConfig()

        val assertion =
            object : AssertionTemplate("Mock Assertion") {
                override fun doEvaluate(scenarioInstance: ScenarioInstance, flicker: FlickerTest) {
                    flicker.assertLayers { error("Should never be called") }
                }
            }

        registry.registerScenario(
            SOME_SCENARIO,
            EXTRACTOR_FOR_SOME_SCENARIO,
            mapOf(assertion to AssertionInvocationGroup.BLOCKING)
        )
        registry.unregisterAssertion(SOME_SCENARIO, assertion.id)

        val entries = registry.getEntries()
        Truth.assertThat(entries).hasSize(1)
        Truth.assertThat(entries.first().scenarioId).isEqualTo(SOME_SCENARIO)

        val reader = getTraceReaderFromScenario("AppLaunch")
        val scenarioSlices = entries.first().extractor.extract(reader)
        Truth.assertThat(scenarioSlices).hasSize(1)
        val scenarioInstance =
            ScenarioInstanceImpl.fromSlice(scenarioSlices.first(), reader, entries.first())
        val assertions = scenarioInstance.generateAssertions()

        Truth.assertThat(assertions).hasSize(0)
    }

    @Test
    fun canOverrideStabilityGroup() {
        val registry = FlickerConfig()

        val assertion =
            object : AssertionTemplate("Mock Assertion") {
                override fun doEvaluate(scenarioInstance: ScenarioInstance, flicker: FlickerTest) {
                    flicker.assertLayers { error("Should never be called") }
                }
            }

        registry.registerScenario(
            SOME_SCENARIO,
            EXTRACTOR_FOR_SOME_SCENARIO,
            mapOf(assertion to AssertionInvocationGroup.BLOCKING)
        )
        registry.overrideAssertionStabilityGroup(
            SOME_SCENARIO,
            assertion.id,
            AssertionInvocationGroup.NON_BLOCKING
        )

        val entries = registry.getEntries()
        Truth.assertThat(entries).hasSize(1)
        Truth.assertThat(entries.first().scenarioId).isEqualTo(SOME_SCENARIO)

        val reader = getTraceReaderFromScenario("AppLaunch")
        val scenarioSlices = entries.first().extractor.extract(reader)
        Truth.assertThat(scenarioSlices).hasSize(1)
        val scenarioInstance =
            ScenarioInstanceImpl.fromSlice(scenarioSlices.first(), reader, entries.first())
        val assertions = scenarioInstance.generateAssertions()

        Truth.assertThat(assertions).hasSize(1)
        Truth.assertThat(assertions.first().stabilityGroup)
            .isEqualTo(AssertionInvocationGroup.NON_BLOCKING)
    }

    @Test
    fun registerAssertionToScenario() {
        val registry = FlickerConfig()

        var executed1 = false
        val assertion1 =
            object : AssertionTemplate("Mock Assertion 1") {
                override fun doEvaluate(scenarioInstance: ScenarioInstance, flicker: FlickerTest) {
                    flicker.assertLayers { executed1 = true }
                }
            }

        var executed2 = false
        val assertion2 =
            object : AssertionTemplate("Mock Assertion 2") {
                override fun doEvaluate(scenarioInstance: ScenarioInstance, flicker: FlickerTest) {
                    flicker.assertLayers { executed2 = true }
                }
            }

        registry.registerScenario(
            SOME_SCENARIO,
            EXTRACTOR_FOR_SOME_SCENARIO,
            mapOf(assertion1 to AssertionInvocationGroup.BLOCKING)
        )
        registry.registerAssertions(
            SOME_SCENARIO,
            assertion2,
            stabilityGroup = AssertionInvocationGroup.NON_BLOCKING
        )

        val entries = registry.getEntries()
        Truth.assertThat(entries).hasSize(1)
        Truth.assertThat(entries.first().scenarioId).isEqualTo(SOME_SCENARIO)

        val reader = getTraceReaderFromScenario("AppLaunch")
        val scenarioSlices = entries.first().extractor.extract(reader)
        Truth.assertThat(scenarioSlices).hasSize(1)
        val scenarioInstance =
            ScenarioInstanceImpl.fromSlice(scenarioSlices.first(), reader, entries.first())
        val assertions = scenarioInstance.generateAssertions()

        Truth.assertThat(assertions).hasSize(2)
        Truth.assertThat(assertions.first().stabilityGroup)
            .isEqualTo(AssertionInvocationGroup.BLOCKING)
        Truth.assertThat(assertions.last().stabilityGroup)
            .isEqualTo(AssertionInvocationGroup.NON_BLOCKING)

        assertions.forEach { it.execute() }
        Truth.assertThat(executed1).isTrue()
        Truth.assertThat(executed2).isTrue()
    }

    @Test
    fun throwsOnRegisteringAssertionToNotRegisteredScenario() {
        val registry = FlickerConfig()

        val assertion =
            object : AssertionTemplate("Mock Assertion") {
                override fun doEvaluate(scenarioInstance: ScenarioInstance, flicker: FlickerTest) {
                    flicker.assertLayers { error("Should never be called") }
                }
            }

        val error =
            assertThrows<Throwable> { registry.registerAssertions(SOME_SCENARIO, assertion) }
        Truth.assertThat(error)
            .hasMessageThat()
            .contains("No scenario named 'SOME_SCENARIO' registered")
    }

    @Test
    fun throwsOnRegisteringTheSameScenario() {
        val registry = FlickerConfig()

        val assertion =
            object : AssertionTemplate("Mock Assertion") {
                override fun doEvaluate(scenarioInstance: ScenarioInstance, flicker: FlickerTest) {
                    flicker.assertLayers { error("Should never be called") }
                }
            }

        registry.registerScenario(
            SOME_SCENARIO,
            EXTRACTOR_FOR_SOME_SCENARIO,
            mapOf(assertion to AssertionInvocationGroup.BLOCKING)
        )

        val error =
            assertThrows<Throwable> {
                registry.registerScenario(
                    SOME_SCENARIO,
                    EXTRACTOR_FOR_SOME_SCENARIO,
                    mapOf(assertion to AssertionInvocationGroup.BLOCKING)
                )
            }
        Truth.assertThat(error)
            .hasMessageThat()
            .contains("already has a registered scenario with name 'SOME_SCENARIO'")
    }

    @Test
    fun throwsOnRegisteringTheSameAssertion() {
        val registry = FlickerConfig()

        val assertion =
            object : AssertionTemplate("Mock Assertion") {
                override fun doEvaluate(scenarioInstance: ScenarioInstance, flicker: FlickerTest) {
                    flicker.assertLayers { error("Should never be called") }
                }
            }

        registry.registerScenario(
            SOME_SCENARIO,
            EXTRACTOR_FOR_SOME_SCENARIO,
            mapOf(assertion to AssertionInvocationGroup.BLOCKING)
        )

        val error =
            assertThrows<Throwable> { registry.registerAssertions(SOME_SCENARIO, assertion) }
        Truth.assertThat(error)
            .hasMessageThat()
            .contains(
                "Assertion with id 'Mock Assertion' already present for scenario 'SOME_SCENARIO'"
            )
    }

    @Test
    fun canUseConfigs() {
        val registry = FlickerConfig()
        val config = createConfig(SOME_SCENARIO)
        registry.use(config)

        // TODO: Validate
    }

    @Test
    fun canUseMultipleConfigs() {
        val registry = FlickerConfig()

        val config1 = createConfig(ScenarioId("SCENARIO_1"))
        val config2 = createConfig(ScenarioId("SCENARIO_2"))
        val config3 = createConfig(ScenarioId("SCENARIO_3"))

        registry.use(config1, config2).use(config3)

        // TODO: Validate
    }

    @Test
    fun canRegisterSameAssertionForDifferentScenarios() {
        val registry = FlickerConfig()

        val assertion =
            object : AssertionTemplate("Mock Assertion") {
                override fun doEvaluate(scenarioInstance: ScenarioInstance, flicker: FlickerTest) {
                    flicker.assertLayers { error("Should never be called") }
                }
            }

        registry.registerScenario(
            SOME_SCENARIO,
            EXTRACTOR_FOR_SOME_SCENARIO,
            mapOf(assertion to AssertionInvocationGroup.BLOCKING)
        )
        registry.registerScenario(
            SOME_OTHER_SCENARIO,
            EXTRACTOR_FOR_SOME_OTHER_SCENARIO,
            mapOf(assertion to AssertionInvocationGroup.BLOCKING)
        )
    }

    private fun createConfig(scenarioId: ScenarioId): FlickerConfigEntry {
        return FlickerConfigEntry(
            scenarioId = scenarioId,
            extractor =
                object : ScenarioExtractor {
                    override fun extract(reader: Reader): List<TraceSlice> {
                        return EntireTraceExtractor().extract(reader)
                    }
                },
            assertions = mapOf(),
            enabled = true
        )
    }

    companion object {
        private val SOME_SCENARIO = ScenarioId("SOME_SCENARIO")
        private val SOME_OTHER_SCENARIO = ScenarioId("SOME_OTHER_SCENARIO")

        private val EXTRACTOR_FOR_SOME_SCENARIO =
            object : ScenarioExtractor {
                override fun extract(reader: Reader): List<TraceSlice> {
                    return EntireTraceExtractor().extract(reader)
                }
            }
        private val EXTRACTOR_FOR_SOME_OTHER_SCENARIO =
            object : ScenarioExtractor {
                override fun extract(reader: Reader): List<TraceSlice> {
                    error("Should never be called...")
                }
            }
    }
}
