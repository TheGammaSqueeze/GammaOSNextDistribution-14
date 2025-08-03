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

@file:JvmName("MonitorUtils")
@file:OptIn(
    androidx.benchmark.perfetto.ExperimentalPerfettoCaptureApi::class,
    androidx.benchmark.perfetto.ExperimentalPerfettoTraceProcessorApi::class
)

package android.tools.traces.monitors

import android.tools.ScenarioBuilder
import android.tools.Tag
import android.tools.io.Reader
import android.tools.traces.SERVICE_TRACE_CONFIG
import android.tools.traces.io.ResultReaderWithLru
import android.tools.traces.io.ResultWriter
import android.tools.traces.monitors.wm.WindowManagerTraceMonitor
import android.tools.traces.parsers.perfetto.LayersTraceParser
import android.tools.traces.parsers.perfetto.TraceProcessorSession
import android.tools.traces.parsers.perfetto.TransactionsTraceParser
import android.tools.traces.parsers.wm.WindowManagerTraceParser
import android.tools.traces.surfaceflinger.LayersTrace
import android.tools.traces.surfaceflinger.TransactionsTrace
import android.tools.traces.wm.WindowManagerTrace
import java.io.File
import perfetto.protos.PerfettoConfig.SurfaceFlingerLayersConfig

/**
 * Acquire the [WindowManagerTrace] with the device state changes that happen when executing the
 * commands defined in the [predicate].
 *
 * @param predicate Commands to execute
 * @throws UnsupportedOperationException If tracing is already activated
 */
fun withWMTracing(predicate: () -> Unit): WindowManagerTrace {
    return WindowManagerTraceParser()
        .parse(WindowManagerTraceMonitor().withTracing(Tag.ALL, predicate))
}

/**
 * Acquire the [LayersTrace] with the device state changes that happen when executing the commands
 * defined in the [predicate].
 *
 * @param flags Flags to indicate tracing level
 * @param predicate Commands to execute
 * @throws UnsupportedOperationException If tracing is already activated
 */
@JvmOverloads
fun withSFTracing(
    flags: List<SurfaceFlingerLayersConfig.TraceFlag>? = null,
    predicate: () -> Unit
): LayersTrace {
    val trace =
        PerfettoTraceMonitor.newBuilder()
            .enableLayersTrace(flags)
            .build()
            .withTracing(Tag.ALL, predicate)
    return TraceProcessorSession.loadPerfettoTrace(trace) { session ->
        LayersTraceParser().parse(session)
    }
}

/**
 * Acquire the [TransactionsTrace] with the device state changes that happen when executing the
 * commands defined in the [predicate].
 *
 * @param predicate Commands to execute
 * @throws UnsupportedOperationException If tracing is already activated
 */
@JvmOverloads
fun withTransactionsTracing(predicate: () -> Unit): TransactionsTrace {
    val trace =
        PerfettoTraceMonitor.newBuilder()
            .enableTransactionsTrace()
            .build()
            .withTracing(Tag.ALL, predicate)
    return TraceProcessorSession.loadPerfettoTrace(trace) { session ->
        TransactionsTraceParser().parse(session)
    }
}

/**
 * Acquire the [WindowManagerTrace] and [LayersTrace] with the device state changes that happen when
 * executing the commands defined in the [predicate].
 *
 * @param predicate Commands to execute
 * @throws UnsupportedOperationException If tracing is already activated
 */
fun withTracing(
    traceMonitors: List<TraceMonitor> =
        listOf(
            WindowManagerTraceMonitor(),
            PerfettoTraceMonitor.newBuilder().enableLayersTrace().enableTransactionsTrace().build(),
        ),
    predicate: () -> Unit
): Reader {
    val tmpFile = File.createTempFile("recordTraces", "")
    val writer =
        ResultWriter()
            .forScenario(ScenarioBuilder().forClass(tmpFile.name).build())
            .withOutputDir(tmpFile.parentFile)

    try {
        traceMonitors.forEach { it.start() }
        predicate()
    } finally {
        traceMonitors.forEach { it.stop(writer) }
    }
    return ResultReaderWithLru(writer.write(), SERVICE_TRACE_CONFIG)
}

/**
 * Acquire the [WindowManagerTrace] and [LayersTrace] with the device state changes that happen when
 * executing the commands defined in the [predicate].
 *
 * @param predicate Commands to execute
 * @return a pair containing the WM and SF traces
 * @throws UnsupportedOperationException If tracing is already activated
 */
fun recordTraces(predicate: () -> Unit): Pair<ByteArray, ByteArray> {
    var wmTraceData = ByteArray(0)
    val layersTraceData =
        PerfettoTraceMonitor.newBuilder().enableLayersTrace().build().withTracing {
            wmTraceData = WindowManagerTraceMonitor().withTracing(Tag.ALL, predicate)
        }

    return Pair(wmTraceData, layersTraceData)
}
