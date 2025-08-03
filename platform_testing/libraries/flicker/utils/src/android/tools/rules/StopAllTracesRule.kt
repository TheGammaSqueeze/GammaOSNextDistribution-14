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

package android.tools.rules

import android.tools.ScenarioBuilder
import android.tools.traces.io.ResultWriter
import android.tools.traces.monitors.PerfettoTraceMonitor
import android.tools.traces.monitors.TraceMonitor
import android.tools.traces.monitors.view.ViewTraceMonitor
import android.tools.traces.monitors.wm.LegacyShellTransitionTraceMonitor
import android.tools.traces.monitors.wm.LegacyWmTransitionTraceMonitor
import android.tools.traces.monitors.wm.WindowManagerTraceMonitor
import kotlin.io.path.createTempDirectory
import kotlin.io.path.name
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

class StopAllTracesRule : TestRule {
    override fun apply(base: Statement?, description: Description?): Statement {
        return object : Statement() {
            override fun evaluate() {
                PerfettoTraceMonitor.stopAllSessions()
                LegacyShellTransitionTraceMonitor().stopIfEnabled()
                LegacyWmTransitionTraceMonitor().stopIfEnabled()
                WindowManagerTraceMonitor().stopIfEnabled()
                ViewTraceMonitor().stopIfEnabled()

                base?.evaluate()
            }
        }
    }

    companion object {
        private fun TraceMonitor.stopIfEnabled() {
            if (!isEnabled) {
                return
            }
            val resultWriter =
                ResultWriter()
                    .forScenario(
                        ScenarioBuilder().forClass(kotlin.io.path.createTempFile().name).build()
                    )
                    .withOutputDir(createTempDirectory().toFile())
                    .setRunComplete()
            stop(resultWriter)
        }
    }
}
