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
import android.tools.utils.TestTraces
import org.junit.FixMethodOrder
import org.junit.runners.MethodSorters

/** Tests for [ResultReader] parsing event log */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class ResultReaderTestParseEventLog : BaseResultReaderTestParseTrace() {
    override val assetFiles = mapOf(TraceType.EVENT_LOG to TestTraces.EventLog.FILE)
    override val traceName = "Event Log"
    override val startTimeTrace = TestTraces.EventLog.START_TIME
    override val endTimeTrace = TestTraces.EventLog.END_TIME
    override val validSliceTime = TestTraces.EventLog.SLICE_TIME
    override val invalidSliceTime = startTimeTrace
    override val expectedSlicedTraceSize = 125
    override val invalidSizeMessage: String = "'to' needs to be greater than 'from'"

    override fun doParse(reader: ResultReader) = reader.readEventLogTrace()
    override fun getTime(traceTime: Timestamp) = traceTime.unixNanos
}
