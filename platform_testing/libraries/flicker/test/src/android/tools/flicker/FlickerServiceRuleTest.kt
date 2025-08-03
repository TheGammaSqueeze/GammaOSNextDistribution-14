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

package android.tools.flicker

import android.tools.flicker.assertions.AssertionData
import android.tools.flicker.assertions.AssertionResult
import android.tools.flicker.assertions.SubjectsParser
import android.tools.flicker.legacy.runner.Consts
import android.tools.flicker.rules.FlickerServiceRule
import android.tools.flicker.subject.exceptions.FlickerAssertionError
import android.tools.flicker.subject.exceptions.SimpleFlickerAssertionError
import android.tools.utils.CleanFlickerEnvironmentRule
import android.tools.utils.KotlinMockito
import com.google.common.truth.Truth
import org.junit.Assume
import org.junit.AssumptionViolatedException
import org.junit.Before
import org.junit.ClassRule
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.Description
import org.junit.runners.MethodSorters
import org.mockito.Mockito
import org.mockito.Mockito.`when`

/**
 * Contains [FlickerServiceRule] tests. To run this test: `atest
 * FlickerLibTest:FlickerServiceRuleTest`
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class FlickerServiceRuleTest {
    @Before
    fun before() {
        Assume.assumeTrue(isShellTransitionsEnabled)
    }

    @Test
    fun startsTraceCollectionOnTestStarting() {
        val mockFlickerServiceResultsCollector =
            Mockito.mock(IFlickerServiceResultsCollector::class.java)
        val testRule = FlickerServiceRule(metricsCollector = mockFlickerServiceResultsCollector)
        val mockDescription = Description.createTestDescription(this::class.java, "mockTest")

        testRule.starting(mockDescription)
        Mockito.verify(mockFlickerServiceResultsCollector).testStarted(mockDescription)
    }

    @Test
    fun stopsTraceCollectionOnTestFinished() {
        val mockFlickerServiceResultsCollector =
            Mockito.mock(IFlickerServiceResultsCollector::class.java)
        val testRule = FlickerServiceRule(metricsCollector = mockFlickerServiceResultsCollector)
        val mockDescription = Description.createTestDescription(this::class.java, "mockTest")

        testRule.finished(mockDescription)
        Mockito.verify(mockFlickerServiceResultsCollector).testFinished(mockDescription)
    }

    @Test
    fun reportsFailuresToMetricsCollector() {
        val mockFlickerServiceResultsCollector =
            Mockito.mock(IFlickerServiceResultsCollector::class.java)
        val testRule = FlickerServiceRule(metricsCollector = mockFlickerServiceResultsCollector)
        val mockDescription = Description.createTestDescription(this::class.java, "mockTest")
        val mockError = Throwable("Mock error")

        testRule.failed(mockError, mockDescription)
        Mockito.verify(mockFlickerServiceResultsCollector)
            .testFailure(
                KotlinMockito.argThat {
                    this.description == mockDescription && this.exception == mockError
                }
            )
    }

    @Test
    fun reportsSkippedToMetricsCollector() {
        val mockFlickerServiceResultsCollector =
            Mockito.mock(IFlickerServiceResultsCollector::class.java)
        val testRule = FlickerServiceRule(metricsCollector = mockFlickerServiceResultsCollector)
        val mockDescription = Description.createTestDescription(this::class.java, "mockTest")
        val mockAssumptionFailure = AssumptionViolatedException("Mock error")

        testRule.skipped(mockAssumptionFailure, mockDescription)
        Mockito.verify(mockFlickerServiceResultsCollector).testSkipped(mockDescription)
    }

    @Test
    fun doesNotThrowExceptionForFlickerTestFailureIfRequested() {
        val mockFlickerServiceResultsCollector =
            Mockito.mock(IFlickerServiceResultsCollector::class.java)
        val testRule =
            FlickerServiceRule(
                metricsCollector = mockFlickerServiceResultsCollector,
                failTestOnFlicker = false
            )
        val mockDescription = Description.createTestDescription(this::class.java, "mockTest")

        val assertionError = SimpleFlickerAssertionError("Some assertion error")
        `when`(mockFlickerServiceResultsCollector.resultsForTest(mockDescription))
            .thenReturn(listOf(mockFailureAssertionResult(assertionError)))

        testRule.starting(mockDescription)
        testRule.succeeded(mockDescription)
        testRule.finished(mockDescription)
    }

    @Test
    fun throwsExceptionForFlickerTestFailureIfRequested() {
        val mockFlickerServiceResultsCollector =
            Mockito.mock(IFlickerServiceResultsCollector::class.java)
        val testRule =
            FlickerServiceRule(
                metricsCollector = mockFlickerServiceResultsCollector,
                failTestOnFlicker = true
            )
        val mockDescription = Description.createTestDescription(this::class.java, "mockTest")

        val assertionError = SimpleFlickerAssertionError("Some assertion error")
        `when`(mockFlickerServiceResultsCollector.resultsForTest(mockDescription))
            .thenReturn(listOf(mockFailureAssertionResult(assertionError)))

        testRule.starting(mockDescription)
        testRule.succeeded(mockDescription)
        try {
            testRule.finished(mockDescription)
            error("Exception was not thrown")
        } catch (e: Throwable) {
            Truth.assertThat(e).isEqualTo(assertionError)
        }
    }

    @Test
    fun neverThrowsExceptionForExecutionErrors() {
        val mockFlickerServiceResultsCollector =
            Mockito.mock(IFlickerServiceResultsCollector::class.java)
        val testRule =
            FlickerServiceRule(
                metricsCollector = mockFlickerServiceResultsCollector,
                failTestOnFlicker = true
            )
        val mockDescription = Description.createTestDescription(this::class.java, "mockTest")

        val executionError = Throwable(Consts.FAILURE)
        `when`(mockFlickerServiceResultsCollector.executionErrors)
            .thenReturn(listOf(executionError))

        testRule.starting(mockDescription)
        testRule.succeeded(mockDescription)
        testRule.finished(mockDescription)
    }

    @Test
    fun canBeDisabled() {
        val mockFlickerServiceResultsCollector =
            Mockito.mock(IFlickerServiceResultsCollector::class.java)
        val testRule =
            FlickerServiceRule(
                enabled = false,
                metricsCollector = mockFlickerServiceResultsCollector,
                failTestOnFlicker = true
            )

        val mockDescription = Description.createTestDescription(this::class.java, "mockTest")

        val executionError = Throwable(Consts.FAILURE)
        `when`(mockFlickerServiceResultsCollector.executionErrors)
            .thenReturn(listOf(executionError))

        testRule.starting(mockDescription)
        testRule.succeeded(mockDescription)
        testRule.finished(mockDescription)
        testRule.failed(Throwable(), mockDescription)
        testRule.skipped(Mockito.mock(AssumptionViolatedException::class.java), mockDescription)

        Mockito.verifyZeroInteractions(mockFlickerServiceResultsCollector)
    }

    @Test
    fun assertionFailuresNotReportedIfUnderlyingTestFailed() {
        val mockFlickerServiceResultsCollector =
            Mockito.mock(IFlickerServiceResultsCollector::class.java)
        val testRule =
            FlickerServiceRule(
                metricsCollector = mockFlickerServiceResultsCollector,
                failTestOnFlicker = true
            )
        val mockDescription = Description.createTestDescription(this::class.java, "mockTest")

        val assertionError = SimpleFlickerAssertionError("Some assertion error")
        `when`(mockFlickerServiceResultsCollector.resultsForTest(mockDescription))
            .thenReturn(listOf(mockFailureAssertionResult(assertionError)))

        testRule.starting(mockDescription)
        testRule.failed(Throwable("Mock failure"), mockDescription)

        // Should not throw any assertion errors
        testRule.finished(mockDescription)
    }

    companion object {
        fun mockFailureAssertionResult(error: FlickerAssertionError) =
            object : AssertionResult {
                override val name = "MOCK_SCENARIO#mockAssertion"
                override val assertionData =
                    listOf<AssertionData>(
                        object : AssertionData {
                            override fun checkAssertion(run: SubjectsParser) {
                                error("Unimplemented - shouldn't be called")
                            }
                        }
                    )
                override val assertionErrors = listOf(error)
                override val stabilityGroup = AssertionInvocationGroup.BLOCKING
                override val passed = false
            }

        @ClassRule @JvmField val ENV_CLEANUP = CleanFlickerEnvironmentRule()
    }
}
