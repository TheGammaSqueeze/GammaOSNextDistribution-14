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

import android.tools.device.apphelpers.BrowserAppHelper
import android.tools.io.TraceType
import android.tools.traces.monitors.PerfettoTraceMonitor
import android.tools.traces.monitors.withSFTracing
import android.tools.traces.monitors.withTransactionsTracing
import android.tools.traces.parsers.perfetto.LayersTraceParser
import android.tools.traces.parsers.perfetto.TraceProcessorSession
import android.tools.traces.parsers.perfetto.TransitionsTraceParser
import android.tools.utils.CleanFlickerEnvironmentRule
import com.google.common.truth.Truth
import org.junit.Assume.assumeTrue
import org.junit.ClassRule
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/**
 * Contains [PerfettoTraceMonitor] tests. To run this test: `atest
 * FlickerLibTest:PerfettoTraceMonitorTest`
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class PerfettoTraceMonitorTest : TraceMonitorTest<PerfettoTraceMonitor>() {
    override val traceType = TraceType.PERFETTO
    override fun getMonitor() =
        PerfettoTraceMonitor.newBuilder().enableLayersTrace().enableTransactionsTrace().build()

    override fun assertTrace(traceData: ByteArray) {
        Truth.assertThat(traceData.size).isGreaterThan(0)
    }

    @Test
    fun withSFTracingTest() {
        val trace = withSFTracing {
            device.pressHome()
            device.pressRecentApps()
        }

        Truth.assertWithMessage("Could not obtain layers trace").that(trace.entries).isNotEmpty()
    }

    @Test
    fun withTransactionsTracingTest() {
        val trace = withTransactionsTracing {
            device.pressHome()
            device.pressRecentApps()
        }

        Truth.assertWithMessage("Could not obtain transactions trace")
            .that(trace.entries)
            .isNotEmpty()
    }

    @Test
    fun layersDump() {
        val traceData = PerfettoTraceMonitor.newBuilder().enableLayersDump().build().withTracing {}
        assertTrace(traceData)

        val trace =
            TraceProcessorSession.loadPerfettoTrace(traceData) { session ->
                LayersTraceParser().parse(session)
            }
        Truth.assertWithMessage("Could not obtain layers dump")
            .that(trace.entries.size)
            .isEqualTo(1)
    }

    @Test
    fun withTransitionTracingTest() {
        assumeTrue(
            "PerfettoTransitionTracing flag should be enabled",
            android.tracing.Flags.perfettoTransitionTracing()
        )

        val traceMonitor = PerfettoTraceMonitor.newBuilder().enableTransitionsTrace().build()
        val traceData =
            traceMonitor.withTracing {
                BrowserAppHelper().launchViaIntent()
                device.pressHome()
                device.pressRecentApps()
            }
        assertTrace(traceData)

        val trace =
            TraceProcessorSession.loadPerfettoTrace(traceData) { session ->
                TransitionsTraceParser().parse(session)
            }
        Truth.assertWithMessage("Could not obtain transition trace")
            .that(trace.entries)
            .isNotEmpty()
    }

    companion object {
        @ClassRule @JvmField val ENV_CLEANUP = CleanFlickerEnvironmentRule()
    }
}
