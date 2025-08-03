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

package android.tools.flicker.legacy.runner

import android.app.Instrumentation
import android.tools.Logger
import android.tools.Scenario
import android.tools.flicker.junit.Utils
import android.tools.flicker.legacy.FlickerTestData
import android.tools.traces.io.ResultWriter
import android.tools.traces.parsers.WindowManagerStateHelper
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * Test rule to run transition setup and teardown
 *
 * @param flicker test definition
 * @param resultWriter to write
 * @param scenario to run the transition
 * @param instrumentation to interact with the device
 * @param setupCommands to run before the transition
 * @param teardownCommands to run after the transition
 * @param wmHelper to stabilize the UI before/after transitions
 */
class SetupTeardownRule(
    private val flicker: FlickerTestData,
    private val resultWriter: ResultWriter,
    private val scenario: Scenario,
    private val instrumentation: Instrumentation,
    private val setupCommands: List<FlickerTestData.() -> Any> = flicker.transitionSetup,
    private val teardownCommands: List<FlickerTestData.() -> Any> = flicker.transitionTeardown,
    private val wmHelper: WindowManagerStateHelper = flicker.wmHelper
) : TestRule {
    override fun apply(base: Statement?, description: Description?): Statement {
        return object : Statement() {
            override fun evaluate() {
                try {
                    doRunTransitionSetup(description)
                    base?.evaluate()
                } finally {
                    doRunTransitionTeardown(description)
                }
            }
        }
    }

    private fun doRunTransitionSetup(description: Description?) {
        Logger.withTracing("doRunTransitionSetup") {
            Utils.notifyRunnerProgress(scenario, "Running transition setup for $description")
            setupCommands.forEach { it.invoke(flicker) }
            Utils.doWaitForUiStabilize(wmHelper)
        }
    }

    private fun doRunTransitionTeardown(description: Description?) {
        Logger.withTracing("doRunTransitionTeardown") {
            Utils.notifyRunnerProgress(scenario, "Running transition teardown for $description")
            teardownCommands.forEach { it.invoke(flicker) }
            Utils.doWaitForUiStabilize(wmHelper)
        }
    }
}
