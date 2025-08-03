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

import android.annotation.SuppressLint
import android.tools.CleanFlickerEnvironmentRuleWithDataStore
import android.tools.ScenarioBuilder
import android.tools.flicker.assertions.FlickerTest
import android.tools.flicker.datastore.CachedResultReader
import android.tools.flicker.datastore.DataStore
import android.tools.io.TraceType
import android.tools.newTestCachedResultWriter
import android.tools.traces.TRACE_CONFIG_REQUIRE_CHANGES
import android.tools.traces.io.ResultReader
import android.tools.utils.TEST_SCENARIO
import android.tools.utils.TestTraces
import android.tools.utils.assertExceptionMessage
import android.tools.utils.assertThrows
import com.google.common.truth.Truth
import java.io.File
import kotlin.io.path.name
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Tests for [FlickerTest] */
@SuppressLint("VisibleForTests")
class LegacyFlickerTestTest {
    private var executionCount = 0
    @Rule @JvmField val envCleanup = CleanFlickerEnvironmentRuleWithDataStore()

    @Before
    fun setup() {
        executionCount = 0
    }

    @Test
    fun failsWithoutScenario() {
        val actual = LegacyFlickerTest()
        val failure =
            assertThrows<IllegalArgumentException> { actual.assertLayers { executionCount++ } }
        assertExceptionMessage(failure, "Scenario shouldn't be empty")
        Truth.assertWithMessage("Executed").that(executionCount).isEqualTo(0)
    }

    @Test
    fun executesLayers() {
        val predicate: (FlickerTest) -> Unit = { it.assertLayers { executionCount++ } }
        doWriteTraceExecuteAssertionAndVerify(
            TraceType.SF,
            predicate,
            TestTraces.LayerTrace.FILE,
            expectedExecutionCount = 2
        )
    }

    @Test
    fun executesLayerStart() {
        val predicate: (FlickerTest) -> Unit = { it.assertLayersStart { executionCount++ } }
        doWriteTraceExecuteAssertionAndVerify(
            TraceType.SF,
            predicate,
            TestTraces.LayerTrace.FILE,
            expectedExecutionCount = 2
        )
    }

    @Test
    fun executesLayerEnd() {
        val predicate: (FlickerTest) -> Unit = { it.assertLayersEnd { executionCount++ } }
        doWriteTraceExecuteAssertionAndVerify(
            TraceType.SF,
            predicate,
            TestTraces.LayerTrace.FILE,
            expectedExecutionCount = 2
        )
    }

    @Test
    fun doesNotExecuteLayersWithoutTrace() {
        val predicate: (FlickerTest) -> Unit = { it.assertLayers { executionCount++ } }
        doExecuteAssertionWithoutTraceAndVerifyNotExecuted(TraceType.SF, predicate)
    }

    @Test
    fun doesNotExecuteLayersStartWithoutTrace() {
        val predicate: (FlickerTest) -> Unit = { it.assertLayersStart { executionCount++ } }
        doExecuteAssertionWithoutTraceAndVerifyNotExecuted(TraceType.SF, predicate)
    }

    @Test
    fun doesNotExecuteLayersEndWithoutTrace() {
        val predicate: (FlickerTest) -> Unit = { it.assertLayersEnd { executionCount++ } }
        doExecuteAssertionWithoutTraceAndVerifyNotExecuted(TraceType.SF, predicate)
    }

    @Test
    fun doesNotExecuteLayerTagWithoutTag() {
        val predicate: (FlickerTest) -> Unit = { it.assertLayersTag("tag") { executionCount++ } }
        doExecuteAssertionWithoutTraceAndVerifyNotExecuted(TraceType.SF, predicate)
    }

    @Test
    fun executesWm() {
        val predicate: (FlickerTest) -> Unit = { it.assertWm { executionCount++ } }
        doWriteTraceExecuteAssertionAndVerify(
            TraceType.WM,
            predicate,
            TestTraces.WMTrace.FILE,
            expectedExecutionCount = 2
        )
    }

    @Test
    fun executesWmStart() {
        val predicate: (FlickerTest) -> Unit = { it.assertWmStart { executionCount++ } }
        doWriteTraceExecuteAssertionAndVerify(
            TraceType.WM,
            predicate,
            TestTraces.WMTrace.FILE,
            expectedExecutionCount = 2
        )
    }

    @Test
    fun executesWmEnd() {
        val predicate: (FlickerTest) -> Unit = { it.assertWmEnd { executionCount++ } }
        doWriteTraceExecuteAssertionAndVerify(
            TraceType.WM,
            predicate,
            TestTraces.WMTrace.FILE,
            expectedExecutionCount = 2
        )
    }

    @Test
    fun doesNotExecuteWmWithoutTrace() {
        val predicate: (FlickerTest) -> Unit = { it.assertWm { executionCount++ } }
        doExecuteAssertionWithoutTraceAndVerifyNotExecuted(TraceType.WM, predicate)
    }

    @Test
    fun doesNotExecuteWmStartWithoutTrace() {
        val predicate: (FlickerTest) -> Unit = { it.assertWmStart { executionCount++ } }
        doExecuteAssertionWithoutTraceAndVerifyNotExecuted(TraceType.WM, predicate)
    }

    @Test
    fun doesNotExecuteWmEndWithoutTrace() {
        val predicate: (FlickerTest) -> Unit = { it.assertWmEnd { executionCount++ } }
        doExecuteAssertionWithoutTraceAndVerifyNotExecuted(TraceType.WM, predicate)
    }

    @Test
    fun doesNotExecuteWmTagWithoutTag() {
        val predicate: (FlickerTest) -> Unit = { it.assertWmTag("tag") { executionCount++ } }
        doWriteTraceExecuteAssertionAndVerify(
            TraceType.WM,
            predicate,
            TestTraces.WMTrace.FILE,
            expectedExecutionCount = 0
        )
    }

    @Test
    fun executesEventLog() {
        val predicate: (FlickerTest) -> Unit = { it.assertEventLog { executionCount++ } }
        doWriteTraceExecuteAssertionAndVerify(
            TraceType.EVENT_LOG,
            predicate,
            TestTraces.EventLog.FILE,
            expectedExecutionCount = 2
        )
    }

    @Test
    fun doesNotExecuteEventLogWithoutEventLog() {
        val predicate: (FlickerTest) -> Unit = { it.assertEventLog { executionCount++ } }
        val scenarioName = kotlin.io.path.createTempFile().name
        val scenario = ScenarioBuilder().forClass(scenarioName).build()
        newTestCachedResultWriter(scenario).write()
        val flickerWrapper = LegacyFlickerTest()
        flickerWrapper.initialize(scenarioName)
        // Each assertion is executed independently and not cached, only Flicker as a Service
        // assertions are cached
        predicate.invoke(flickerWrapper)
        predicate.invoke(flickerWrapper)

        Truth.assertWithMessage("Executed").that(executionCount).isEqualTo(0)
    }

    private fun doExecuteAssertionWithoutTraceAndVerifyNotExecuted(
        traceType: TraceType,
        predicate: (FlickerTest) -> Unit
    ) =
        doWriteTraceExecuteAssertionAndVerify(
            traceType,
            predicate,
            file = null,
            expectedExecutionCount = 0
        )

    private fun doWriteTraceExecuteAssertionAndVerify(
        traceType: TraceType,
        predicate: (FlickerTest) -> Unit,
        file: File?,
        expectedExecutionCount: Int
    ) {
        val writer = newTestCachedResultWriter()
        if (file != null) {
            writer.addTraceResult(traceType, file)
        }
        writer.write()
        val flickerWrapper =
            LegacyFlickerTest(
                resultReaderProvider = {
                    android.tools.flicker.datastore.CachedResultReader(
                        it,
                        TRACE_CONFIG_REQUIRE_CHANGES,
                        reader =
                            ResultReader(
                                android.tools.flicker.datastore.DataStore.getResult(it),
                                TRACE_CONFIG_REQUIRE_CHANGES
                            )
                    )
                }
            )
        flickerWrapper.initialize(TEST_SCENARIO.testClass)
        // Each assertion is executed independently and not cached, only Flicker as a Service
        // assertions are cached
        predicate.invoke(flickerWrapper)
        predicate.invoke(flickerWrapper)

        Truth.assertWithMessage("Executed").that(executionCount).isEqualTo(expectedExecutionCount)
    }
}
