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

import android.tools.Timestamps
import android.tools.traces.TRACE_CONFIG_REQUIRE_CHANGES
import android.tools.traces.deleteIfExists
import android.tools.traces.io.ResultReader
import android.tools.utils.CleanFlickerEnvironmentRule
import android.tools.utils.assertThrows
import android.tools.utils.newTestResultWriter
import android.tools.utils.outputFileName
import com.google.common.truth.Truth
import java.io.FileNotFoundException
import org.junit.Before
import org.junit.ClassRule
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/** Tests for [ResultReader] */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class ResultReaderTest {
    @Before
    fun setup() {
        outputFileName(RunStatus.RUN_EXECUTED).deleteIfExists()
        outputFileName(RunStatus.ASSERTION_SUCCESS).deleteIfExists()
    }

    @Test
    fun failFileNotFound() {
        val data = newTestResultWriter().write()
        data.artifact.deleteIfExists()
        val reader = ResultReader(data, TRACE_CONFIG_REQUIRE_CHANGES)
        assertThrows<FileNotFoundException> {
            reader.readTransitionsTrace() ?: error("Should have failed")
        }
    }

    @Test
    fun slicedResultKeepsStatusInSync() {
        val data = newTestResultWriter().setRunComplete().write()
        val reader = ResultReader(data, TRACE_CONFIG_REQUIRE_CHANGES)
        val slicedReader = reader.slice(Timestamps.min(), Timestamps.max())
        reader.result.updateStatus(RunStatus.ASSERTION_SUCCESS)

        Truth.assertThat(reader.runStatus).isEqualTo(RunStatus.ASSERTION_SUCCESS)
        Truth.assertThat(reader.runStatus).isEqualTo(slicedReader.runStatus)

        Truth.assertThat(reader.artifactPath).contains(RunStatus.ASSERTION_SUCCESS.prefix)
        Truth.assertThat(reader.artifactPath).isEqualTo(slicedReader.artifactPath)
    }

    companion object {
        @ClassRule @JvmField val ENV_CLEANUP = CleanFlickerEnvironmentRule()
    }
}
