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
import android.tools.Timestamps
import android.tools.Trace
import android.tools.traces.TRACE_CONFIG_REQUIRE_CHANGES
import android.tools.traces.deleteIfExists
import android.tools.traces.io.ResultReader
import android.tools.traces.io.ResultWriter
import android.tools.utils.CleanFlickerEnvironmentRule
import android.tools.utils.TestTraces
import android.tools.utils.assertExceptionMessage
import android.tools.utils.assertThrows
import android.tools.utils.newTestResultWriter
import android.tools.utils.outputFileName
import com.google.common.truth.Truth
import java.io.File
import org.junit.Before
import org.junit.ClassRule
import org.junit.Test

/** Base class for [ResultReader] tests parsing traces */
abstract class BaseResultReaderTestParseTrace {
    protected abstract val assetFiles: Map<TraceType, File>
    protected abstract val traceName: String
    protected abstract val startTimeTrace: Timestamp
    protected abstract val endTimeTrace: Timestamp
    protected abstract val validSliceTime: Timestamp
    protected abstract val invalidSliceTime: Timestamp
    protected abstract val expectedSlicedTraceSize: Int
    protected open val invalidSizeMessage: String
        get() = "$traceName contained 0 entries, expected at least 2"

    protected abstract fun doParse(reader: ResultReader): Trace<*>?
    protected abstract fun getTime(traceTime: Timestamp): Long

    protected open fun setupWriter(writer: ResultWriter): ResultWriter {
        assetFiles.forEach { (traceType, assetFile) -> writer.addTraceResult(traceType, assetFile) }
        return writer
    }

    @Before
    fun setup() {
        outputFileName(RunStatus.RUN_EXECUTED).deleteIfExists()
    }

    @Test
    fun readTrace() {
        val writer = setupWriter(newTestResultWriter())
        val result = writer.write()

        val reader = ResultReader(result, TRACE_CONFIG_REQUIRE_CHANGES)
        val trace = doParse(reader) ?: error("$traceName not built")

        Truth.assertWithMessage(traceName).that(trace.entries).isNotEmpty()
        Truth.assertWithMessage("$traceName start")
            .that(getTime(trace.entries.first().timestamp))
            .isEqualTo(getTime(startTimeTrace))
        Truth.assertWithMessage("$traceName end")
            .that(getTime(trace.entries.last().timestamp))
            .isEqualTo(getTime(endTimeTrace))
    }

    @Test
    fun readTraceNullWhenDoesNotExist() {
        val writer = newTestResultWriter()
        val result = writer.write()
        val reader = ResultReader(result, TRACE_CONFIG_REQUIRE_CHANGES)
        val trace = doParse(reader)

        Truth.assertWithMessage(traceName).that(trace).isNull()
    }

    @Test
    fun readTraceAndSliceTraceByTimestamp() {
        val result =
            setupWriter(newTestResultWriter())
                .setTransitionStartTime(startTimeTrace)
                .setTransitionEndTime(validSliceTime)
                .write()
        val reader = ResultReader(result, TestTraces.TEST_TRACE_CONFIG)
        val trace = doParse(reader) ?: error("$traceName not built")

        Truth.assertWithMessage(traceName).that(trace.entries).hasSize(expectedSlicedTraceSize)
        Truth.assertWithMessage("$traceName start")
            .that(getTime(trace.entries.first().timestamp))
            .isEqualTo(getTime(startTimeTrace))
    }

    @Test
    fun readTraceAndSliceTraceByTimestampAndFailInvalidSize() {
        val result =
            setupWriter(newTestResultWriter()).setTransitionEndTime(Timestamps.min()).write()
        val reader = ResultReader(result, TRACE_CONFIG_REQUIRE_CHANGES)
        val exception =
            assertThrows<IllegalArgumentException> {
                doParse(reader) ?: error("$traceName not built")
            }
        assertExceptionMessage(exception, invalidSizeMessage)
    }

    companion object {
        @ClassRule @JvmField val ENV_CLEANUP = CleanFlickerEnvironmentRule()
    }
}
