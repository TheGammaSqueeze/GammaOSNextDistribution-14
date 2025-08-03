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
import android.tools.flicker.assertors.AssertionId
import android.tools.flicker.assertors.AssertionTemplate
import android.tools.flicker.extractors.ScenarioExtractor

internal class FlickerConfigImpl : FlickerConfig {
    private val registry = mutableMapOf<ScenarioId, FlickerConfigEntry>()

    override fun use(flickerConfigEntries: Collection<FlickerConfigEntry>): FlickerConfig = apply {
        for (config in flickerConfigEntries) {
            if (!config.enabled) {
                continue
            }
            registerScenario(config.scenarioId, config.extractor, config.assertions)
        }
    }

    override fun registerScenario(
        scenario: ScenarioId,
        extractor: ScenarioExtractor,
        assertions: Map<AssertionTemplate, AssertionInvocationGroup>?
    ) = apply {
        require(!registry.containsKey(scenario)) {
            "${this::class.simpleName} already has a registered scenario with name '$scenario'."
        }

        registry[scenario] = FlickerConfigEntry(scenario, extractor, assertions ?: emptyMap())
    }

    override fun unregisterScenario(scenario: ScenarioId) = apply {
        val entry = registry[scenario] ?: error("No scenario named '$scenario' registered.")
        registry.remove(scenario)
    }

    override fun registerAssertions(
        scenario: ScenarioId,
        vararg assertions: AssertionTemplate,
        stabilityGroup: AssertionInvocationGroup
    ) = apply {
        val entry = registry[scenario]
        require(entry != null) { "No scenario named '$scenario' registered." }

        assertions.forEach { assertion ->
            require(entry.assertions.keys.none { it.id == assertion.id }) {
                "Assertion with id '${assertion.id.name}' already present for scenario " +
                    "'${scenario.name}'."
            }

            registry[scenario] =
                FlickerConfigEntry(
                    entry.scenarioId,
                    entry.extractor,
                    entry.assertions.toMutableMap().apply { this[assertion] = stabilityGroup },
                    entry.enabled
                )
        }
    }

    override fun getEntries(): Collection<FlickerConfigEntry> {
        return registry.values
    }

    override fun overrideAssertionStabilityGroup(
        scenario: ScenarioId,
        assertionId: AssertionId,
        stabilityGroup: AssertionInvocationGroup
    ) = apply {
        val entry = registry[scenario]
        require(entry != null) { "No scenario named '$scenario' registered." }

        val targetAssertion =
            entry.assertions.keys.firstOrNull { it.id == assertionId }
                ?: error(
                    "No assertion with id $assertionId present in registry for scenario $scenario."
                )

        registry[scenario] =
            FlickerConfigEntry(
                entry.scenarioId,
                entry.extractor,
                entry.assertions.toMutableMap().apply { this[targetAssertion] = stabilityGroup },
                entry.enabled
            )
    }

    override fun unregisterAssertion(scenario: ScenarioId, assertionId: AssertionId) = apply {
        val entry = registry[scenario]
        require(entry != null) { "No scenario named '$scenario' registered." }

        val targetAssertion =
            entry.assertions.keys.firstOrNull { it.id == assertionId }
                ?: error(
                    "No assertion with id $assertionId present in registry for scenario $scenario."
                )

        registry[scenario] =
            FlickerConfigEntry(
                entry.scenarioId,
                entry.extractor,
                entry.assertions.toMutableMap().apply { this.remove(targetAssertion) },
                entry.enabled
            )
    }
}
