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

import android.annotation.SuppressLint
import android.tools.ScenarioBuilder
import android.tools.Timestamps
import android.tools.traces.TRACE_CONFIG_REQUIRE_CHANGES
import android.tools.traces.deleteIfExists
import android.tools.traces.io.ResultReader
import android.tools.traces.io.ResultWriter
import android.tools.utils.CleanFlickerEnvironmentRule
import android.tools.utils.TEST_SCENARIO
import android.tools.utils.TestTraces
import android.tools.utils.assertExceptionMessage
import android.tools.utils.assertThrows
import android.tools.utils.newTestResultWriter
import android.tools.utils.outputFileName
import com.google.common.truth.Truth
import java.io.File
import kotlin.io.path.createTempDirectory
import org.junit.ClassRule
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/** Tests for [ResultWriter] */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@SuppressLint("VisibleForTests")
class ResultWriterTest {

    @Test
    fun cannotWriteFileWithoutScenario() {
        val exception =
            assertThrows<IllegalArgumentException> {
                val writer =
                    newTestResultWriter().forScenario(ScenarioBuilder().createEmptyScenario())
                writer.write()
            }

        assertExceptionMessage(exception, "Scenario shouldn't be empty")
    }

    @Test
    fun writesEmptyFile() {
        outputFileName(RunStatus.RUN_EXECUTED).deleteIfExists()
        val writer = newTestResultWriter()
        val result = writer.write()
        val path = File(result.artifact.absolutePath)
        Truth.assertWithMessage("File exists").that(path.exists()).isTrue()
        Truth.assertWithMessage("Transition start time")
            .that(result.transitionTimeRange.start)
            .isEqualTo(Timestamps.min())
        Truth.assertWithMessage("Transition end time")
            .that(result.transitionTimeRange.end)
            .isEqualTo(Timestamps.max())
        val reader = ResultReader(result, TRACE_CONFIG_REQUIRE_CHANGES)
        Truth.assertWithMessage("File count").that(reader.countFiles()).isEqualTo(0)
    }

    @Test
    fun writesUndefinedFile() {
        outputFileName(RunStatus.RUN_EXECUTED).deleteIfExists()
        val writer =
            ResultWriter().forScenario(TEST_SCENARIO).withOutputDir(createTempDirectory().toFile())
        val result = writer.write()
        val path = File(result.artifact.absolutePath)
        validateFileName(path, RunStatus.UNDEFINED)
    }

    @Test
    fun writesRunCompleteFile() {
        outputFileName(RunStatus.RUN_EXECUTED).deleteIfExists()
        val writer = newTestResultWriter().setRunComplete()
        val result = writer.write()
        val path = File(result.artifact.absolutePath)
        validateFileName(path, RunStatus.RUN_EXECUTED)
    }

    @Test
    fun writesRunFailureFile() {
        outputFileName(RunStatus.RUN_FAILED).deleteIfExists()
        val writer = newTestResultWriter().setRunFailed(EXPECTED_FAILURE)
        val result = writer.write()
        val path = File(result.artifact.absolutePath)
        validateFileName(path, RunStatus.RUN_FAILED)
        Truth.assertWithMessage("Expected assertion")
            .that(result.executionError)
            .isEqualTo(EXPECTED_FAILURE)
    }

    @Test
    fun writesTransitionTime() {
        val writer =
            newTestResultWriter()
                .setTransitionStartTime(TestTraces.TIME_5)
                .setTransitionEndTime(TestTraces.TIME_10)

        val result = writer.write()
        Truth.assertWithMessage("Transition start time")
            .that(result.transitionTimeRange.start)
            .isEqualTo(TestTraces.TIME_5)
        Truth.assertWithMessage("Transition end time")
            .that(result.transitionTimeRange.end)
            .isEqualTo(TestTraces.TIME_10)
    }

    @Test
    fun writeWMTrace() {
        val writer = newTestResultWriter().addTraceResult(TraceType.WM, TestTraces.WMTrace.FILE)
        val result = writer.write()
        val reader = ResultReader(result, TRACE_CONFIG_REQUIRE_CHANGES)
        Truth.assertWithMessage("File count").that(reader.countFiles()).isEqualTo(1)
        Truth.assertWithMessage("Has file with type")
            .that(reader.hasTraceFile(TraceType.WM))
            .isTrue()
    }

    @Test
    fun writeLayersTrace() {
        val writer = newTestResultWriter().addTraceResult(TraceType.SF, TestTraces.LayerTrace.FILE)
        val result = writer.write()
        val reader = ResultReader(result, TRACE_CONFIG_REQUIRE_CHANGES)
        Truth.assertWithMessage("File count").that(reader.countFiles()).isEqualTo(1)
        Truth.assertWithMessage("Has file with type")
            .that(reader.hasTraceFile(TraceType.SF))
            .isTrue()
    }

    @Test
    fun writeTransactionTrace() {
        val writer =
            newTestResultWriter()
                .addTraceResult(TraceType.TRANSACTION, TestTraces.TransactionTrace.FILE)
        val result = writer.write()
        val reader = ResultReader(result, TRACE_CONFIG_REQUIRE_CHANGES)
        Truth.assertWithMessage("File count").that(reader.countFiles()).isEqualTo(1)
        Truth.assertWithMessage("Has file with type")
            .that(reader.hasTraceFile(TraceType.TRANSACTION))
            .isTrue()
    }

    @Test
    fun writeTransitionTrace() {
        val writer =
            newTestResultWriter()
                .addTraceResult(
                    TraceType.LEGACY_WM_TRANSITION,
                    TestTraces.LegacyTransitionTrace.WM_FILE
                )
                .addTraceResult(
                    TraceType.LEGACY_SHELL_TRANSITION,
                    TestTraces.LegacyTransitionTrace.SHELL_FILE
                )
        val result = writer.write()
        val reader = ResultReader(result, TRACE_CONFIG_REQUIRE_CHANGES)
        Truth.assertWithMessage("File count").that(reader.countFiles()).isEqualTo(2)
        Truth.assertWithMessage("Has file with type")
            .that(reader.hasTraceFile(TraceType.LEGACY_WM_TRANSITION))
            .isTrue()
        Truth.assertWithMessage("Has file with type")
            .that(reader.hasTraceFile(TraceType.LEGACY_SHELL_TRANSITION))
            .isTrue()
    }

    @Test
    fun writeAllTraces() {
        val writer =
            newTestResultWriter()
                .addTraceResult(TraceType.WM, TestTraces.WMTrace.FILE)
                .addTraceResult(TraceType.SF, TestTraces.LayerTrace.FILE)
                .addTraceResult(TraceType.TRANSACTION, TestTraces.TransactionTrace.FILE)
                .addTraceResult(
                    TraceType.LEGACY_WM_TRANSITION,
                    TestTraces.LegacyTransitionTrace.WM_FILE
                )
                .addTraceResult(
                    TraceType.LEGACY_SHELL_TRANSITION,
                    TestTraces.LegacyTransitionTrace.SHELL_FILE
                )
        val result = writer.write()
        val reader = ResultReader(result, TRACE_CONFIG_REQUIRE_CHANGES)
        Truth.assertWithMessage("File count").that(reader.countFiles()).isEqualTo(4)
        Truth.assertWithMessage("Has file with type")
            .that(reader.hasTraceFile(TraceType.WM))
            .isTrue()
        Truth.assertWithMessage("Has file with type")
            .that(reader.hasTraceFile(TraceType.SF))
            .isTrue()
        Truth.assertWithMessage("Has file with type")
            .that(reader.hasTraceFile(TraceType.LEGACY_WM_TRANSITION))
            .isTrue()
        Truth.assertWithMessage("Has file with type")
            .that(reader.hasTraceFile(TraceType.LEGACY_SHELL_TRANSITION))
            .isTrue()
        Truth.assertWithMessage("Has file with type")
            .that(reader.hasTraceFile(TraceType.TRANSACTION))
            .isTrue()
    }

    companion object {
        private val EXPECTED_FAILURE = IllegalArgumentException("Expected test exception")

        private fun validateFileName(filePath: File, status: RunStatus) {
            Truth.assertWithMessage("File name contains run status")
                .that(filePath.name)
                .contains(status.prefix)
        }

        @ClassRule @JvmField val ENV_CLEANUP = CleanFlickerEnvironmentRule()
    }
}
