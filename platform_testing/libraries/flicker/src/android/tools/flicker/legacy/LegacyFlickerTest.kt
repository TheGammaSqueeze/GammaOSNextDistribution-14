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

package android.tools.flicker.legacy

import android.tools.Scenario
import android.tools.ScenarioBuilder
import android.tools.ScenarioImpl
import android.tools.flicker.assertions.AssertionData
import android.tools.flicker.assertions.AssertionRunner
import android.tools.flicker.assertions.BaseFlickerTest
import android.tools.flicker.assertions.SubjectsParser
import android.tools.flicker.datastore.CachedAssertionRunner
import android.tools.flicker.datastore.CachedResultReader
import android.tools.io.Reader
import android.tools.traces.TRACE_CONFIG_REQUIRE_CHANGES

/** Specification of a flicker test for JUnit ParameterizedRunner class */
data class LegacyFlickerTest(
    private val scenarioBuilder: ScenarioBuilder = ScenarioBuilder(),
    private val resultReaderProvider: (Scenario) -> Reader = {
        android.tools.flicker.datastore.CachedResultReader(it, TRACE_CONFIG_REQUIRE_CHANGES)
    },
    private val subjectsParserProvider: (Reader) -> SubjectsParser = { SubjectsParser(it) },
    private val runnerProvider: (Scenario) -> AssertionRunner = {
        val reader = resultReaderProvider(it)
        android.tools.flicker.datastore.CachedAssertionRunner(it, reader)
    }
) : BaseFlickerTest() {
    var scenario: ScenarioImpl = ScenarioBuilder().createEmptyScenario() as ScenarioImpl
        private set

    override fun toString(): String = scenario.toString()

    fun initialize(testClass: String): Scenario {
        scenario = scenarioBuilder.forClass(testClass).build() as ScenarioImpl
        return scenario
    }

    /** Obtains a reader for the flicker result artifact */
    val reader: Reader
        get() = resultReaderProvider(scenario)

    override fun doProcess(assertion: AssertionData) {
        require(!scenario.isEmpty) { "Scenario shouldn't be empty" }
        runnerProvider.invoke(scenario).runAssertion(assertion)?.let { throw it }
    }
}
