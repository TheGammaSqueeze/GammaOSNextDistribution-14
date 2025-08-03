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

import android.tools.FLICKER_TAG
import android.tools.Logger
import android.tools.Scenario
import android.tools.flicker.Utils.ALL_MONITORS
import android.tools.io.Reader
import android.tools.io.TraceType
import android.tools.traces.SERVICE_TRACE_CONFIG
import android.tools.traces.io.ResultReaderWithLru
import android.tools.traces.io.ResultWriter
import java.io.File
import kotlin.io.path.createTempDirectory

class FlickerServiceTracesCollector(
    private val outputDir: File = createTempDirectory().toFile(),
) : TracesCollector {
    private var scenario: Scenario? = null

    private val traceMonitors = ALL_MONITORS.filter { it.traceType != TraceType.SCREEN_RECORDING }

    override fun start(scenario: Scenario) {
        reportErrorsBlock("Failed to start traces") {
            require(this.scenario == null) { "Trace still running" }
            traceMonitors.forEach { it.start() }
            this.scenario = scenario
        }
    }

    override fun stop(): Reader {
        return reportErrorsBlock("Failed to stop traces") {
            val scenario = this.scenario
            require(scenario != null) { "Scenario not set - make sure trace was started properly" }

            Logger.v(LOG_TAG, "Creating output directory for trace files")
            outputDir.mkdirs()

            Logger.v(LOG_TAG, "Stopping trace monitors")
            val writer = ResultWriter().forScenario(scenario).withOutputDir(outputDir)
            traceMonitors.forEach { it.stop(writer) }
            this.scenario = null
            val result = writer.write()

            ResultReaderWithLru(result, SERVICE_TRACE_CONFIG)
        }
    }

    override fun cleanup() {
        if (outputDir.exists()) {
            outputDir.deleteRecursively()
        }
    }

    private fun <T : Any> reportErrorsBlock(msg: String, block: () -> T): T {
        try {
            return block()
        } catch (e: Throwable) {
            Logger.e(LOG_TAG, msg, e)
            throw e
        }
    }

    companion object {
        private const val LOG_TAG = "$FLICKER_TAG-Collector"
    }
}
