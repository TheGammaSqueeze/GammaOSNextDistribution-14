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

package android.tools.monitors.wm

import android.tools.io.TraceType
import android.tools.monitors.TraceMonitorTest
import android.tools.traces.TRACE_CONFIG_REQUIRE_CHANGES
import android.tools.traces.io.ResultReader
import android.tools.traces.monitors.wm.WindowManagerTraceMonitor
import android.tools.utils.CleanFlickerEnvironmentRule
import android.tools.utils.newTestResultWriter
import com.android.server.wm.nano.WindowManagerTraceFileProto
import com.google.common.truth.Truth
import org.junit.ClassRule
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/** Contains [WindowManagerTraceMonitor] tests. */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class WindowManagerTraceMonitorTest : TraceMonitorTest<WindowManagerTraceMonitor>() {
    override val traceType = TraceType.WM
    override fun getMonitor() = WindowManagerTraceMonitor()

    override fun assertTrace(traceData: ByteArray) {
        val trace = WindowManagerTraceFileProto.parseFrom(traceData)
        Truth.assertThat(trace.magicNumber)
            .isEqualTo(
                WindowManagerTraceFileProto.MAGIC_NUMBER_H.toLong() shl
                    32 or
                    WindowManagerTraceFileProto.MAGIC_NUMBER_L.toLong()
            )
    }

    @Test
    fun includesProtologTrace() {
        val monitor = getMonitor()
        monitor.start()
        val writer = newTestResultWriter()
        monitor.stop(writer)
        val result = writer.write()
        val reader = ResultReader(result, TRACE_CONFIG_REQUIRE_CHANGES)
        Truth.assertWithMessage("Trace file exists ${TraceType.PROTOLOG.fileName}")
            .that(reader.hasTraceFile(TraceType.PROTOLOG))
            .isTrue()
    }

    companion object {
        @ClassRule @JvmField val ENV_CLEANUP = CleanFlickerEnvironmentRule()
    }
}
