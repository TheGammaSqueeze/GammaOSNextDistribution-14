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

package android.tools.io

import android.tools.Timestamp
import android.tools.traces.io.ResultReader
import android.tools.utils.TestTraces
import org.junit.Assume.assumeTrue
import org.junit.Before

/** Tests for [ResultReader] parsing [TraceType.PROTOLOG] */
class ResultReaderTestParseProtoLog : BaseResultReaderTestParseTrace() {
    override val assetFiles = mapOf(TraceType.PERFETTO to TestTraces.ProtoLogTrace.FILE)
    override val traceName = "ProtoLog trace"
    override val startTimeTrace = TestTraces.ProtoLogTrace.START_TIME
    override val endTimeTrace = TestTraces.ProtoLogTrace.END_TIME
    override val validSliceTime = TestTraces.ProtoLogTrace.VALID_SLICE_TIME
    override val invalidSliceTime = TestTraces.ProtoLogTrace.INVALID_SLICE_TIME
    override val invalidSizeMessage = "ProtoLog trace cannot be empty"
    override val expectedSlicedTraceSize = 1

    @Before
    fun before() {
        assumeTrue(android.tracing.Flags.perfettoProtologTracing())
    }

    override fun doParse(reader: ResultReader) = reader.readProtoLogTrace()
    override fun getTime(traceTime: Timestamp) = traceTime.elapsedNanos
}
