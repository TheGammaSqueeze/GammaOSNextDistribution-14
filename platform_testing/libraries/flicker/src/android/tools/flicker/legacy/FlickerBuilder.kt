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

import android.app.Instrumentation
import android.tools.flicker.Utils.ALL_MONITORS
import android.tools.io.TraceType
import android.tools.traces.getDefaultFlickerOutputDir
import android.tools.traces.monitors.ITransitionMonitor
import android.tools.traces.monitors.NoTraceMonitor
import android.tools.traces.monitors.ScreenRecorder
import android.tools.traces.parsers.WindowManagerStateHelper
import androidx.test.uiautomator.UiDevice
import java.io.File

/** Build Flicker tests using Flicker DSL */
@FlickerDslMarker
class FlickerBuilder(
    private val instrumentation: Instrumentation,
    private val outputDir: File = getDefaultFlickerOutputDir(),
    private val wmHelper: WindowManagerStateHelper =
        WindowManagerStateHelper(instrumentation, clearCacheAfterParsing = false),
    private val setupCommands: MutableList<FlickerTestData.() -> Any> = mutableListOf(),
    private val transitionCommands: MutableList<FlickerTestData.() -> Any> = mutableListOf(),
    private val teardownCommands: MutableList<FlickerTestData.() -> Any> = mutableListOf(),
    val device: UiDevice = UiDevice.getInstance(instrumentation),
    private val traceMonitors: MutableList<ITransitionMonitor> = ALL_MONITORS.toMutableList()
) {
    private var usingExistingTraces = false

    /**
     * Configure a [ScreenRecorder].
     *
     * By default, the tracing is always active. To disable tracing return null
     */
    fun withScreenRecorder(screenRecorder: () -> ScreenRecorder?): FlickerBuilder = apply {
        traceMonitors.removeIf { it is ScreenRecorder }
        addMonitor(screenRecorder())
    }

    fun withoutScreenRecorder(): FlickerBuilder = apply {
        traceMonitors.removeIf { it is ScreenRecorder }
    }

    /** Defines the setup commands executed before the [transitions] to test */
    fun setup(commands: FlickerTestData.() -> Unit): FlickerBuilder = apply {
        setupCommands.add(commands)
    }

    /** Defines the teardown commands executed after the [transitions] to test */
    fun teardown(commands: FlickerTestData.() -> Unit): FlickerBuilder = apply {
        teardownCommands.add(commands)
    }

    /** Defines the commands that trigger the behavior to test */
    fun transitions(command: FlickerTestData.() -> Unit): FlickerBuilder = apply {
        require(!usingExistingTraces) {
            "Can't update transition after calling usingExistingTraces"
        }
        transitionCommands.add(command)
    }

    data class TraceFiles(
        val wmTrace: File,
        val perfetto: File,
        val wmTransitions: File,
        val shellTransitions: File,
        val eventLog: File
    )

    /** Use pre-executed results instead of running transitions to get the traces */
    fun usingExistingTraces(_traceFiles: () -> TraceFiles): FlickerBuilder = apply {
        val traceFiles = _traceFiles()
        // Remove all trace monitor and use only monitor that read from existing trace file
        this.traceMonitors.clear()
        addMonitor(NoTraceMonitor { it.addTraceResult(TraceType.WM, traceFiles.wmTrace) })
        addMonitor(NoTraceMonitor { it.addTraceResult(TraceType.SF, traceFiles.perfetto) })
        addMonitor(NoTraceMonitor { it.addTraceResult(TraceType.TRANSACTION, traceFiles.perfetto) })
        addMonitor(
            NoTraceMonitor {
                it.addTraceResult(TraceType.LEGACY_WM_TRANSITION, traceFiles.wmTransitions)
            }
        )
        addMonitor(
            NoTraceMonitor {
                it.addTraceResult(TraceType.LEGACY_SHELL_TRANSITION, traceFiles.shellTransitions)
            }
        )
        addMonitor(NoTraceMonitor { it.addTraceResult(TraceType.EVENT_LOG, traceFiles.eventLog) })

        // Remove all transitions execution
        this.transitionCommands.clear()
        this.usingExistingTraces = true
    }

    /** Creates a new Flicker runner based on the current builder configuration */
    fun build(): FlickerTestData {
        return FlickerTestDataImpl(
            instrumentation,
            device,
            outputDir,
            traceMonitors,
            setupCommands,
            transitionCommands,
            teardownCommands,
            wmHelper
        )
    }

    /** Returns a copy of the current builder with the changes of [block] applied */
    fun copy(block: FlickerBuilder.() -> Unit) =
        FlickerBuilder(
                instrumentation,
                outputDir.absoluteFile,
                wmHelper,
                setupCommands.toMutableList(),
                transitionCommands.toMutableList(),
                teardownCommands.toMutableList(),
                device,
                traceMonitors.toMutableList(),
            )
            .apply(block)

    private fun addMonitor(newMonitor: ITransitionMonitor?) {
        require(!usingExistingTraces) { "Can't add monitors after calling usingExistingTraces" }

        if (newMonitor != null) {
            traceMonitors.add(newMonitor)
        }
    }
}
