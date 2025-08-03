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

import android.tools.Scenario
import android.tools.io.Reader
import android.tools.traces.TRACE_CONFIG_REQUIRE_CHANGES
import android.tools.traces.io.ResultReaderWithLru
import android.tools.traces.io.ResultWriter
import android.tools.traces.monitors.PerfettoTraceMonitor
import android.tools.traces.monitors.ScreenRecorder
import android.tools.traces.monitors.TraceMonitor
import android.tools.traces.monitors.events.EventLogMonitor
import android.tools.traces.monitors.view.ViewTraceMonitor
import android.tools.traces.monitors.wm.LegacyShellTransitionTraceMonitor
import android.tools.traces.monitors.wm.LegacyWmTransitionTraceMonitor
import android.tools.traces.monitors.wm.WindowManagerTraceMonitor
import android.tools.traces.surfaceflinger.LayersTrace
import android.tools.traces.wm.TransitionChange
import android.tools.traces.wm.WindowManagerTrace
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlin.io.path.createTempDirectory

object Utils {
    val ALL_MONITORS: List<TraceMonitor> =
        mutableListOf(
                WindowManagerTraceMonitor(),
                EventLogMonitor(),
                ViewTraceMonitor(),
                ScreenRecorder(InstrumentationRegistry.getInstrumentation().targetContext)
            )
            .apply {
                val perfettoMonitorBuilder = PerfettoTraceMonitor.newBuilder()
                perfettoMonitorBuilder.enableLayersTrace().enableTransactionsTrace()

                if (android.tracing.Flags.perfettoTransitionTracing()) {
                    perfettoMonitorBuilder.enableTransitionsTrace()
                } else {
                    this.add(LegacyWmTransitionTraceMonitor())
                    this.add(LegacyShellTransitionTraceMonitor())
                }

                if (android.tracing.Flags.perfettoProtologTracing()) {
                    perfettoMonitorBuilder.enableProtoLog()
                }

                this.add(perfettoMonitorBuilder.build())
            }

    fun captureTrace(
        scenario: Scenario,
        outputDir: File = createTempDirectory().toFile(),
        monitors: List<TraceMonitor> = ALL_MONITORS,
        actions: (writer: ResultWriter) -> Unit
    ): Reader {
        val writer = ResultWriter().forScenario(scenario).withOutputDir(outputDir).setRunComplete()
        monitors.fold({ actions.invoke(writer) }) { action, monitor ->
            { monitor.withTracing(writer) { action() } }
        }()
        val result = writer.write()

        return ResultReaderWithLru(result, TRACE_CONFIG_REQUIRE_CHANGES)
    }
}

fun String.camelToSnakeCase(): String {
    return this.fold(StringBuilder()) { acc, c ->
            acc.let {
                val lowerC = c.lowercase()
                acc.append(if (acc.isNotEmpty() && c.isUpperCase()) "_$lowerC" else lowerC)
            }
        }
        .toString()
}

fun isAppTransitionChange(
    transitionChange: TransitionChange,
    layersTrace: LayersTrace?,
    wmTrace: WindowManagerTrace?
): Boolean {
    require(layersTrace != null || wmTrace != null) {
        "Requires at least one of wm of layers trace to not be null"
    }

    val layerDescriptors =
        layersTrace?.let {
            it.getLayerDescriptorById(transitionChange.layerId)
                ?: error("Failed to find layer with id ${transitionChange.layerId}")
        }
    val windowDescriptor =
        wmTrace?.let {
            it.getWindowDescriptorById(transitionChange.windowId)
                ?: error("Failed to find layer with id ${transitionChange.windowId}")
        }
    return (layerDescriptors?.isAppLayer ?: true) && (windowDescriptor?.isAppWindow ?: true)
}
