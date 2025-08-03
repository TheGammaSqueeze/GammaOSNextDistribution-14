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

package android.tools.monitors

import android.app.Instrumentation
import android.tools.Tag
import android.tools.io.RunStatus
import android.tools.io.TraceType
import android.tools.traces.TRACE_CONFIG_REQUIRE_CHANGES
import android.tools.traces.deleteIfExists
import android.tools.traces.io.ResultReader
import android.tools.traces.monitors.TraceMonitor
import android.tools.utils.CleanFlickerEnvironmentRule
import android.tools.utils.newTestResultWriter
import android.tools.utils.outputFileName
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.google.common.truth.Truth
import org.junit.After
import org.junit.Before
import org.junit.ClassRule
import org.junit.Test

abstract class TraceMonitorTest<T : TraceMonitor> {
    abstract fun getMonitor(): T
    abstract fun assertTrace(traceData: ByteArray)
    abstract val traceType: TraceType

    protected open val tag = Tag.ALL
    protected val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()
    protected val device: UiDevice = UiDevice.getInstance(instrumentation)
    private val traceMonitor by lazy { getMonitor() }

    @Before
    fun before() {
        Truth.assertWithMessage("Trace already enabled before starting test")
            .that(traceMonitor.isEnabled)
            .isFalse()
    }

    @After
    fun teardown() {
        device.pressHome()
        if (traceMonitor.isEnabled) {
            traceMonitor.stop(newTestResultWriter())
        }
        outputFileName(RunStatus.RUN_EXECUTED).deleteIfExists()
        Truth.assertWithMessage("Failed to disable trace at end of test")
            .that(traceMonitor.isEnabled)
            .isFalse()
    }

    @Test
    @Throws(Exception::class)
    fun canStartTrace() {
        traceMonitor.start()
        Truth.assertThat(traceMonitor.isEnabled).isTrue()
    }

    @Test
    @Throws(Exception::class)
    fun canStopTrace() {
        traceMonitor.start()
        Truth.assertThat(traceMonitor.isEnabled).isTrue()
        traceMonitor.stop(newTestResultWriter())
        Truth.assertThat(traceMonitor.isEnabled).isFalse()
    }

    @Test
    @Throws(Exception::class)
    fun captureTrace() {
        traceMonitor.start()
        device.pressHome()
        device.pressRecentApps()
        val writer = newTestResultWriter()
        traceMonitor.stop(writer)
        val result = writer.write()
        val reader = ResultReader(result, TRACE_CONFIG_REQUIRE_CHANGES)
        Truth.assertWithMessage("Trace file exists ${traceMonitor.traceType}")
            .that(reader.hasTraceFile(traceMonitor.traceType, tag))
            .isTrue()

        val trace =
            reader.readBytes(traceMonitor.traceType, tag)
                ?: error("Missing trace file ${traceMonitor.traceType}")
        Truth.assertWithMessage("Trace file size").that(trace.size).isGreaterThan(0)
        assertTrace(trace)
    }

    @Test
    fun withTracing() {
        val trace =
            traceMonitor.withTracing(tag) {
                device.pressHome()
                device.pressRecentApps()
            }

        assertTrace(trace)
    }

    companion object {
        @ClassRule @JvmField val ENV_CLEANUP = CleanFlickerEnvironmentRule()
    }
}
