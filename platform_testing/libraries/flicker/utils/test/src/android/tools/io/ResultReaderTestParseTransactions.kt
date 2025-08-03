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

/** Tests for [ResultReader] parsing [TraceType.TRANSACTION] */
class ResultReaderTestParseTransactions : BaseResultReaderTestParseTrace() {
    override val assetFiles = mapOf(TraceType.TRANSACTION to TestTraces.TransactionTrace.FILE)
    override val traceName = "Transactions trace"
    override val startTimeTrace = TestTraces.TransactionTrace.START_TIME
    override val endTimeTrace = TestTraces.TransactionTrace.END_TIME
    override val validSliceTime = TestTraces.TransactionTrace.VALID_SLICE_TIME
    override val invalidSliceTime = TestTraces.TransactionTrace.INVALID_SLICE_TIME
    override val invalidSizeMessage = "Transactions trace cannot be empty"
    override val expectedSlicedTraceSize = 2

    override fun doParse(reader: ResultReader) = reader.readTransactionsTrace()
    override fun getTime(traceTime: Timestamp) = traceTime.elapsedNanos
}
