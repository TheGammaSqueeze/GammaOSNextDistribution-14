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

package android.tools.parsers.perfetto

import android.tools.Cache
import android.tools.traces.parsers.perfetto.LayersTraceParser
import android.tools.traces.parsers.perfetto.TraceProcessorSession
import android.tools.utils.CleanFlickerEnvironmentRule
import android.tools.utils.readAsset
import com.google.common.truth.Truth
import org.junit.Before
import org.junit.ClassRule
import org.junit.Test

class LayerTraceParserTest {
    @Before
    fun before() {
        Cache.clear()
    }

    @Test
    fun canParseFromTrace() {
        val trace =
            TraceProcessorSession.loadPerfettoTrace(
                readAsset("layers_trace_occluded.perfetto-trace")
            ) { session ->
                LayersTraceParser().parse(session)
            }
        Truth.assertWithMessage("Trace").that(trace.entries).isNotEmpty()
        Truth.assertWithMessage("Trace contains entry")
            .that(trace.entries.map { it.elapsedTimestamp })
            .contains(1700382131522L)
    }

    @Test
    fun canParseFromDumpWithDisplay() {
        val trace =
            TraceProcessorSession.loadPerfettoTrace(
                readAsset("layers_dump_with_display.perfetto-trace")
            ) { session ->
                LayersTraceParser().parse(session)
            }
        Truth.assertWithMessage("Dump").that(trace.entries.size).isEqualTo(1)
        Truth.assertWithMessage("Dump contains display")
            .that(trace.entries.first().displays)
            .isNotEmpty()
    }

    companion object {
        @ClassRule @JvmField val ENV_CLEANUP = CleanFlickerEnvironmentRule()
    }
}
