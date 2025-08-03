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

package android.tools.flicker.datastore

import android.annotation.SuppressLint
import android.tools.Tag
import android.tools.flicker.assertions.AssertionDataImpl
import android.tools.flicker.subject.FlickerSubject
import android.tools.flicker.subject.events.EventLogSubject
import android.tools.flicker.subject.exceptions.SimpleFlickerAssertionError
import android.tools.io.RunStatus
import android.tools.traces.monitors.events.EventLogMonitor
import android.tools.utils.CleanFlickerEnvironmentRule
import android.tools.utils.TEST_SCENARIO
import android.tools.utils.assertExceptionMessage
import android.tools.utils.newTestResultWriter
import com.google.common.truth.Truth
import org.junit.Before
import org.junit.ClassRule
import org.junit.Test

/** Tests for [CachedAssertionRunner] */
@SuppressLint("VisibleForTests")
class CachedAssertionRunnerTest {
    private var executionCount = 0

    private val assertionSuccess =
        android.tools.flicker.datastore.CachedAssertionRunnerTest.Companion.newAssertionData {
            executionCount++
        }
    private val assertionFailure =
        android.tools.flicker.datastore.CachedAssertionRunnerTest.Companion.newAssertionData {
            executionCount++
            throw SimpleFlickerAssertionError(android.tools.flicker.datastore.Consts.FAILURE)
        }

    @Before
    fun setup() {
        android.tools.flicker.datastore.DataStore.clear()
        executionCount = 0
        val writer = newTestResultWriter(TEST_SCENARIO)
        val monitor = EventLogMonitor()
        monitor.start()
        monitor.stop(writer)
        val result = writer.write()
        android.tools.flicker.datastore.DataStore.addResult(TEST_SCENARIO, result)
    }

    @Test
    fun executes() {
        val runner = android.tools.flicker.datastore.CachedAssertionRunner(TEST_SCENARIO)
        val firstAssertionResult = runner.runAssertion(assertionSuccess)
        val lastAssertionResult = runner.runAssertion(assertionSuccess)
        val result = android.tools.flicker.datastore.DataStore.getResult(TEST_SCENARIO)

        Truth.assertWithMessage("Executed").that(executionCount).isEqualTo(2)
        Truth.assertWithMessage("Run status")
            .that(result.runStatus)
            .isEqualTo(RunStatus.ASSERTION_SUCCESS)
        Truth.assertWithMessage("Expected exception").that(firstAssertionResult).isNull()
        Truth.assertWithMessage("Expected exception").that(lastAssertionResult).isNull()
    }

    @Test
    fun executesFailure() {
        val runner = android.tools.flicker.datastore.CachedAssertionRunner(TEST_SCENARIO)
        val firstAssertionResult = runner.runAssertion(assertionFailure)
        val lastAssertionResult = runner.runAssertion(assertionFailure)
        val result = android.tools.flicker.datastore.DataStore.getResult(TEST_SCENARIO)

        Truth.assertWithMessage("Executed").that(executionCount).isEqualTo(2)
        Truth.assertWithMessage("Run status")
            .that(result.runStatus)
            .isEqualTo(RunStatus.ASSERTION_FAILED)

        assertExceptionMessage(firstAssertionResult, android.tools.flicker.datastore.Consts.FAILURE)
        assertExceptionMessage(lastAssertionResult, android.tools.flicker.datastore.Consts.FAILURE)
        Truth.assertWithMessage("Same exception")
            .that(firstAssertionResult)
            .hasMessageThat()
            .isEqualTo(lastAssertionResult?.message)
    }

    @Test
    fun updatesRunStatusFailureFirst() {
        val runner = android.tools.flicker.datastore.CachedAssertionRunner(TEST_SCENARIO)
        val firstAssertionResult = runner.runAssertion(assertionFailure)
        val lastAssertionResult = runner.runAssertion(assertionSuccess)
        val result = android.tools.flicker.datastore.DataStore.getResult(TEST_SCENARIO)

        Truth.assertWithMessage("Executed").that(executionCount).isEqualTo(2)
        assertExceptionMessage(firstAssertionResult, android.tools.flicker.datastore.Consts.FAILURE)
        Truth.assertWithMessage("Expected exception").that(lastAssertionResult).isNull()
        Truth.assertWithMessage("Run status")
            .that(result.runStatus)
            .isEqualTo(RunStatus.ASSERTION_FAILED)
    }

    @Test
    fun updatesRunStatusFailureLast() {
        val runner = android.tools.flicker.datastore.CachedAssertionRunner(TEST_SCENARIO)
        val firstAssertionResult = runner.runAssertion(assertionSuccess)
        val lastAssertionResult = runner.runAssertion(assertionFailure)
        val result = android.tools.flicker.datastore.DataStore.getResult(TEST_SCENARIO)

        Truth.assertWithMessage("Executed").that(executionCount).isEqualTo(2)
        Truth.assertWithMessage("Expected exception").that(firstAssertionResult).isNull()
        assertExceptionMessage(lastAssertionResult, android.tools.flicker.datastore.Consts.FAILURE)
        Truth.assertWithMessage("Run status")
            .that(result.runStatus)
            .isEqualTo(RunStatus.ASSERTION_FAILED)
    }

    companion object {
        private fun newAssertionData(assertion: (FlickerSubject) -> Unit) =
            AssertionDataImpl(Tag.ALL, EventLogSubject::class, assertion)

        @ClassRule @JvmField val ENV_CLEANUP = CleanFlickerEnvironmentRule()
    }
}
