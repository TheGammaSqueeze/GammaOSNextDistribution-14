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

package android.tools

import android.app.Instrumentation
import android.tools.flicker.datastore.CachedResultWriter
import android.tools.flicker.legacy.AbstractFlickerTestData
import android.tools.flicker.legacy.FlickerBuilder
import android.tools.flicker.legacy.FlickerTestData
import android.tools.io.PERFETTO_EXT
import android.tools.io.Reader
import android.tools.io.WINSCOPE_EXT
import android.tools.parsers.events.EventLogParser
import android.tools.rules.DataStoreCleanupRule
import android.tools.traces.monitors.ITransitionMonitor
import android.tools.traces.monitors.PerfettoTraceMonitor
import android.tools.traces.monitors.wm.WindowManagerTraceMonitor
import android.tools.traces.parsers.WindowManagerStateHelper
import android.tools.traces.parsers.perfetto.LayersTraceParser
import android.tools.traces.parsers.perfetto.TraceProcessorSession
import android.tools.traces.parsers.perfetto.TransactionsTraceParser
import android.tools.traces.parsers.wm.LegacyTransitionTraceParser
import android.tools.traces.parsers.wm.WindowManagerTraceParser
import android.tools.utils.CleanFlickerEnvironmentRule
import android.tools.utils.ParsedTracesReader
import android.tools.utils.TEST_SCENARIO
import android.tools.utils.TestArtifact
import android.tools.utils.readAsset
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import java.io.File
import kotlin.io.path.createTempDirectory
import org.junit.rules.RuleChain
import org.mockito.Mockito

fun CleanFlickerEnvironmentRuleWithDataStore(): RuleChain =
    CleanFlickerEnvironmentRule().around(DataStoreCleanupRule())

internal fun getTraceReaderFromScenario(scenario: String): Reader {
    val scenarioTraces = getScenarioTraces(scenario)

    val (layersTrace, transactionsTrace) =
        TraceProcessorSession.loadPerfettoTrace(scenarioTraces.perfetto.readBytes()) { session ->
            val layersTrace = LayersTraceParser().parse(session)
            val transactionsTrace = TransactionsTraceParser().parse(session)
            Pair(layersTrace, transactionsTrace)
        }

    return ParsedTracesReader(
        artifact = TestArtifact(scenario),
        wmTrace = WindowManagerTraceParser().parse(scenarioTraces.wmTrace.readBytes()),
        layersTrace = layersTrace,
        transitionsTrace =
            LegacyTransitionTraceParser()
                .parse(
                    scenarioTraces.wmTransitions.readBytes(),
                    scenarioTraces.shellTransitions.readBytes()
                ),
        transactionsTrace = transactionsTrace,
        eventLog = EventLogParser().parse(scenarioTraces.eventLog.readBytes()),
    )
}

fun getScenarioTraces(scenario: String): FlickerBuilder.TraceFiles {
    lateinit var wmTrace: File
    lateinit var perfettoTrace: File
    lateinit var wmTransitionTrace: File
    lateinit var shellTransitionTrace: File
    lateinit var eventLog: File
    val traces =
        mapOf<String, (File) -> Unit>(
            "wm_trace$WINSCOPE_EXT" to { wmTrace = it },
            "layers_and_transactions_trace$PERFETTO_EXT" to { perfettoTrace = it },
            "wm_transition_trace$WINSCOPE_EXT" to { wmTransitionTrace = it },
            "shell_transition_trace$WINSCOPE_EXT" to { shellTransitionTrace = it },
            "eventlog$WINSCOPE_EXT" to { eventLog = it }
        )
    for ((traceFileName, resultSetter) in traces.entries) {
        val traceBytes = readAsset("scenarios/$scenario/$traceFileName")
        val traceFile = File.createTempFile(traceFileName, "")
        traceFile.writeBytes(traceBytes)
        resultSetter.invoke(traceFile)
    }

    return FlickerBuilder.TraceFiles(
        wmTrace,
        perfettoTrace,
        wmTransitionTrace,
        shellTransitionTrace,
        eventLog,
    )
}

fun createMockedFlicker(
    setup: List<FlickerTestData.() -> Unit> = emptyList(),
    teardown: List<FlickerTestData.() -> Unit> = emptyList(),
    transitions: List<FlickerTestData.() -> Unit> = emptyList(),
    extraMonitor: ITransitionMonitor? = null
): FlickerTestData {
    val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()
    val uiDevice: UiDevice = UiDevice.getInstance(instrumentation)
    val mockedFlicker = Mockito.mock(AbstractFlickerTestData::class.java)
    val monitors: MutableList<ITransitionMonitor> =
        mutableListOf(
            WindowManagerTraceMonitor(),
            PerfettoTraceMonitor.newBuilder().enableLayersTrace().build()
        )
    extraMonitor?.let { monitors.add(it) }
    Mockito.`when`(mockedFlicker.wmHelper).thenReturn(WindowManagerStateHelper())
    Mockito.`when`(mockedFlicker.device).thenReturn(uiDevice)
    Mockito.`when`(mockedFlicker.outputDir).thenReturn(createTempDirectory().toFile())
    Mockito.`when`(mockedFlicker.traceMonitors).thenReturn(monitors)
    Mockito.`when`(mockedFlicker.transitionSetup).thenReturn(setup)
    Mockito.`when`(mockedFlicker.transitionTeardown).thenReturn(teardown)
    Mockito.`when`(mockedFlicker.transitions).thenReturn(transitions)
    return mockedFlicker
}

internal fun newTestCachedResultWriter(scenario: Scenario = TEST_SCENARIO) =
    CachedResultWriter()
        .forScenario(scenario)
        .withOutputDir(createTempDirectory().toFile())
        .setRunComplete()
