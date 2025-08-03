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
import android.tools.flicker.FlickerTag
import android.tools.flicker.junit.Utils
import android.tools.flicker.legacy.FlickerTestData
import android.tools.io.TraceType
import android.tools.traces.getCurrentState
import android.tools.traces.io.ResultWriter
import android.tools.traces.monitors.NoTraceMonitor
import android.tools.traces.now
import android.tools.traces.parsers.WindowManagerStateHelper
import android.util.EventLog
import java.io.File
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * Test rule to execute the transition and update [resultWriter]
 *
 * @param flicker test definition
 * @param resultWriter to write
 * @param scenario to run the transition
 * @param instrumentation to interact with the device
 * @param commands to run during the transition
 * @param wmHelper to stabilize the UI before/after transitions
 */
class TransitionExecutionRule(
    private val flicker: FlickerTestData,
    private val resultWriter: ResultWriter,
    private val scenario: Scenario,
    private val instrumentation: Instrumentation = flicker.instrumentation,
    private val commands: List<FlickerTestData.() -> Any> = flicker.transitions,
    private val wmHelper: WindowManagerStateHelper = flicker.wmHelper
) : TestRule {
    private var tags = mutableSetOf<String>()

    override fun apply(base: Statement?, description: Description?): Statement {
        return object : Statement() {
            override fun evaluate() {
                Logger.withTracing("transition") {
                    try {
                        Utils.notifyRunnerProgress(scenario, "Running transition $description")
                        doRunBeforeTransition()
                        commands.forEach { it.invoke(flicker) }
                        base?.evaluate()
                    } finally {
                        doRunAfterTransition()
                    }
                }
            }
        }
    }

    private fun doRunBeforeTransition() {
        Utils.doWaitForUiStabilize(wmHelper)
        Logger.withTracing("doRunBeforeTransition") {
            Utils.notifyRunnerProgress(scenario, "Running doRunBeforeTransition")
            Logger.d(FLICKER_RUNNER_TAG, "doRunBeforeTransition")
            val now = now()
            resultWriter.setTransitionStartTime(now)
            EventLog.writeEvent(
                FlickerTag.TRANSITION_START,
                now.unixNanos,
                now.elapsedNanos,
                now.systemUptimeNanos
            )
            flicker.setCreateTagListener { doCreateTag(it) }
            doValidate()
        }
    }

    private fun doRunAfterTransition() {
        Utils.doWaitForUiStabilize(wmHelper)
        Logger.withTracing("doRunAfterTransition") {
            Utils.notifyRunnerProgress(scenario, "Running doRunAfterTransition")
            Logger.d(FLICKER_RUNNER_TAG, "doRunAfterTransition")
            val now = now()
            resultWriter.setTransitionEndTime(now)
            EventLog.writeEvent(
                FlickerTag.TRANSITION_END,
                now.unixNanos,
                now.elapsedNanos,
                now.systemUptimeNanos
            )
            flicker.clearTagListener()
        }
    }

    private fun doValidate() {
        require(flicker.traceMonitors.isNotEmpty()) { NO_MONITORS_ERROR }
        require(commands.isNotEmpty() || flicker.traceMonitors.all { it is NoTraceMonitor }) {
            EMPTY_TRANSITIONS_ERROR
        }
    }

    private fun doValidateTag(tag: String) {
        require(!tags.contains(tag)) { "Tag `$tag` already used" }
        require(!tag.contains(" ")) { "Tag can't contain spaces, instead it was `$tag`" }
        require(!tag.contains("__")) { "Tag can't `__``, instead it was `$tag`" }
    }

    private fun doCreateTag(tag: String) {
        Logger.withTracing("doRunAfterTransition") {
            Utils.notifyRunnerProgress(scenario, "Creating tag $tag")
            doValidateTag(tag)
            tags.add(tag)

            val deviceStateBytes = getCurrentState()
            val wmDumpFile = File.createTempFile(TraceType.WM_DUMP.fileName, tag)
            val layersDumpFile = File.createTempFile(TraceType.SF_DUMP.fileName, tag)

            wmDumpFile.writeBytes(deviceStateBytes.first)
            layersDumpFile.writeBytes(deviceStateBytes.second)

            resultWriter.addTraceResult(TraceType.WM_DUMP, wmDumpFile, tag)
            resultWriter.addTraceResult(TraceType.SF_DUMP, layersDumpFile, tag)
        }
    }
}
