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

package android.tools.flicker.assertions

import android.tools.flicker.subject.exceptions.SimpleFlickerAssertionError
import android.tools.io.RunStatus
import android.tools.traces.deleteIfExists
import android.tools.traces.io.IResultData
import android.tools.traces.monitors.events.EventLogMonitor
import android.tools.utils.CleanFlickerEnvironmentRule
import android.tools.utils.assertExceptionMessage
import android.tools.utils.newTestResultWriter
import android.tools.utils.outputFileName
import com.google.common.truth.Truth
import org.junit.Before
import org.junit.ClassRule
import org.junit.Test

/**
 * Tests for [ArtifactAssertionRunner]
 *
 * run with `atest FlickerLibTest:ArtifactAssertionRunnerTest`
 */
class ArtifactAssertionRunnerTest {
    private var executionCount = 0

    private val assertionSuccess = newAssertionData { executionCount++ }
    private val assertionFailure = newAssertionData {
        executionCount++
        throw SimpleFlickerAssertionError(Consts.FAILURE)
    }

    @Before
    fun setup() {
        executionCount = 0
        outputFileName(RunStatus.RUN_EXECUTED).deleteIfExists()
        outputFileName(RunStatus.ASSERTION_FAILED).deleteIfExists()
        outputFileName(RunStatus.ASSERTION_SUCCESS).deleteIfExists()
    }

    @Test
    fun executes() {
        val result = newResultReaderWithEmptySubject()
        val runner = ArtifactAssertionRunner(result)
        val firstAssertionResult = runner.runAssertion(assertionSuccess)
        val lastAssertionResult = runner.runAssertion(assertionSuccess)

        Truth.assertWithMessage("Executed").that(executionCount).isEqualTo(2)
        Truth.assertWithMessage("Run status")
            .that(result.runStatus)
            .isEqualTo(RunStatus.ASSERTION_SUCCESS)
        verifyExceptionMessage(firstAssertionResult, expectSuccess = true)
        verifyExceptionMessage(lastAssertionResult, expectSuccess = true)
    }

    @Test
    fun executesFailure() {
        val result = newResultReaderWithEmptySubject()
        val runner = ArtifactAssertionRunner(result)
        val firstAssertionResult = runner.runAssertion(assertionFailure)
        val lastAssertionResult = runner.runAssertion(assertionFailure)

        Truth.assertWithMessage("Executed").that(executionCount).isEqualTo(2)
        Truth.assertWithMessage("Run status")
            .that(result.runStatus)
            .isEqualTo(RunStatus.ASSERTION_FAILED)

        verifyExceptionMessage(firstAssertionResult, expectSuccess = false)
        verifyExceptionMessage(lastAssertionResult, expectSuccess = false)
        Truth.assertWithMessage("Same exception")
            .that(firstAssertionResult)
            .hasMessageThat()
            .isEqualTo(lastAssertionResult?.message)
    }

    @Test
    fun updatesRunStatusFailureFirst() {
        val result = newResultReaderWithEmptySubject()
        val runner = ArtifactAssertionRunner(result)
        val firstAssertionResult = runner.runAssertion(assertionFailure)
        val lastAssertionResult = runner.runAssertion(assertionSuccess)
        Truth.assertWithMessage("Executed").that(executionCount).isEqualTo(2)
        verifyExceptionMessage(firstAssertionResult, expectSuccess = false)
        verifyExceptionMessage(lastAssertionResult, expectSuccess = true)
        Truth.assertWithMessage("Run status")
            .that(result.runStatus)
            .isEqualTo(RunStatus.ASSERTION_FAILED)
    }

    @Test
    fun updatesRunStatusFailureLast() {
        val result = newResultReaderWithEmptySubject()
        val runner = ArtifactAssertionRunner(result)
        val firstAssertionResult = runner.runAssertion(assertionSuccess)
        val lastAssertionResult = runner.runAssertion(assertionFailure)
        Truth.assertWithMessage("Executed").that(executionCount).isEqualTo(2)
        verifyExceptionMessage(firstAssertionResult, expectSuccess = true)
        verifyExceptionMessage(lastAssertionResult, expectSuccess = false)
        Truth.assertWithMessage("Run status")
            .that(result.runStatus)
            .isEqualTo(RunStatus.ASSERTION_FAILED)
    }

    private fun verifyExceptionMessage(actual: Throwable?, expectSuccess: Boolean) {
        if (expectSuccess) {
            Truth.assertWithMessage("Expected exception").that(actual).isNull()
        } else {
            assertExceptionMessage(actual, Consts.FAILURE)
        }
    }

    companion object {
        private fun newAssertionData(assertion: () -> Unit) =
            object : AssertionData {
                override fun checkAssertion(run: SubjectsParser) {
                    assertion.invoke()
                }
            }

        private fun newResultReaderWithEmptySubject(): IResultData {
            val writer = newTestResultWriter()
            val monitor = EventLogMonitor()
            monitor.start()
            monitor.stop(writer)
            return writer.write()
        }

        @ClassRule @JvmField val ENV_CLEANUP = CleanFlickerEnvironmentRule()
    }
}
