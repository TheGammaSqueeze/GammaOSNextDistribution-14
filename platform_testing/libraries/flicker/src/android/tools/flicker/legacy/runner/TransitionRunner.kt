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
import android.platform.test.rule.NavigationModeRule
import android.platform.test.rule.PressHomeRule
import android.platform.test.rule.UnlockScreenRule
import android.tools.Logger
import android.tools.Scenario
import android.tools.device.apphelpers.MessagingAppHelper
import android.tools.flicker.datastore.CachedResultWriter
import android.tools.flicker.legacy.FlickerTestData
import android.tools.flicker.rules.ArtifactSaverRule
import android.tools.flicker.rules.ChangeDisplayOrientationRule
import android.tools.flicker.rules.LaunchAppRule
import android.tools.flicker.rules.RemoveAllTasksButHomeRule
import android.tools.rules.StopAllTracesRule
import android.tools.traces.io.IResultData
import android.tools.traces.io.ResultWriter
import org.junit.rules.RuleChain
import org.junit.runner.Description

/**
 * Transition runner that executes a default device setup (based on [scenario]) as well as the
 * flicker setup/transition/teardown
 */
class TransitionRunner(
    private val scenario: Scenario,
    private val instrumentation: Instrumentation,
    private val resultWriter: ResultWriter = android.tools.flicker.datastore.CachedResultWriter()
) {
    /** Executes [flicker] transition and returns the result */
    fun execute(flicker: FlickerTestData, description: Description?): IResultData {
        return Logger.withTracing("TransitionRunner:execute") {
            resultWriter.forScenario(scenario).withOutputDir(flicker.outputDir)

            val ruleChain = buildTestRuleChain(flicker)
            try {
                ruleChain.apply(null, description).evaluate()
                resultWriter.setRunComplete()
            } catch (e: Throwable) {
                resultWriter.setRunFailed(e)
            }
            resultWriter.write()
        }
    }

    /**
     * Create the default flicker test setup rules. In order:
     * - unlock device
     * - change orientation
     * - change navigation mode
     * - launch an app
     * - remove all apps
     * - go home
     *
     * (b/186740751) An app should be launched because, after changing the navigation mode, the
     * first app launch is handled as a screen size change (similar to a rotation), this causes
     * different problems during testing (e.g. IME now shown on app launch)
     */
    private fun buildTestRuleChain(flicker: FlickerTestData): RuleChain {
        val errorRule = ArtifactSaverRule()
        return RuleChain.outerRule(errorRule)
            .around(StopAllTracesRule())
            .around(UnlockScreenRule())
            .around(NavigationModeRule(scenario.navBarMode.value, false))
            .around(
                LaunchAppRule(MessagingAppHelper(instrumentation), clearCacheAfterParsing = false)
            )
            .around(RemoveAllTasksButHomeRule())
            .around(
                ChangeDisplayOrientationRule(
                    scenario.startRotation,
                    resetOrientationAfterTest = false,
                    clearCacheAfterParsing = false
                )
            )
            .around(PressHomeRule())
            .around(
                TraceMonitorRule(
                    flicker.traceMonitors,
                    scenario,
                    flicker.wmHelper,
                    resultWriter,
                    instrumentation
                )
            )
            .around(errorRule)
            .around(SetupTeardownRule(flicker, resultWriter, scenario, instrumentation))
            .around(errorRule)
            .around(TransitionExecutionRule(flicker, resultWriter, scenario, instrumentation))
    }
}
