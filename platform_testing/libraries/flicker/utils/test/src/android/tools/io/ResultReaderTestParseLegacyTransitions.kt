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

package android.tools.io

import android.tools.Timestamp
import android.tools.traces.io.ResultReader
import android.tools.traces.io.ResultWriter
import android.tools.utils.TestTraces
import android.tools.utils.readAssetAsFile
import org.junit.Assume.assumeFalse
import org.junit.Before

/** Tests for [ResultReader] parsing [TraceType.TRANSITION] */
class ResultReaderTestParseLegacyTransitions : BaseResultReaderTestParseTrace() {
    override val assetFiles =
        mapOf(
            TraceType.LEGACY_WM_TRANSITION to TestTraces.LegacyTransitionTrace.WM_FILE,
            TraceType.LEGACY_SHELL_TRANSITION to TestTraces.LegacyTransitionTrace.SHELL_FILE
        )
    override val traceName = "Transitions trace"
    override val startTimeTrace = TestTraces.LegacyTransitionTrace.START_TIME
    override val endTimeTrace = TestTraces.LegacyTransitionTrace.END_TIME
    override val validSliceTime = TestTraces.LegacyTransitionTrace.VALID_SLICE_TIME
    override val invalidSliceTime = TestTraces.LegacyTransitionTrace.INVALID_SLICE_TIME
    override val invalidSizeMessage = "Transitions trace cannot be empty"
    override val expectedSlicedTraceSize = 10

    @Before
    fun before() {
        assumeFalse(android.tracing.Flags.perfettoTransitionTracing())
    }

    override fun doParse(reader: ResultReader) = reader.readTransitionsTrace()
    override fun getTime(traceTime: Timestamp) = traceTime.elapsedNanos
    override fun setupWriter(writer: ResultWriter): ResultWriter {
        return super.setupWriter(writer).also {
            val wmTransitionTrace = readAssetAsFile("wm_transition_trace.winscope")
            val shellTransitionTrace = readAssetAsFile("shell_transition_trace.winscope")
            it.addTraceResult(TraceType.LEGACY_WM_TRANSITION, wmTransitionTrace)
            it.addTraceResult(TraceType.LEGACY_SHELL_TRANSITION, shellTransitionTrace)
        }
    }
}
