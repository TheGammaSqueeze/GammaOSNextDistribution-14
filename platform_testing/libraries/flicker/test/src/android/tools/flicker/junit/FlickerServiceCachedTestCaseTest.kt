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

package android.tools.flicker.junit

import android.app.Instrumentation
import android.device.collectors.util.SendToInstrumentation
import android.tools.flicker.AssertionInvocationGroup
import android.tools.flicker.FlickerService
import android.tools.flicker.FlickerServiceResultsCollector
import android.tools.flicker.ScenarioInstance
import android.tools.flicker.assertions.AssertionData
import android.tools.flicker.assertions.AssertionResult
import android.tools.flicker.assertions.ScenarioAssertion
import android.tools.flicker.assertions.SubjectsParser
import android.tools.flicker.subject.exceptions.FlickerAssertionError
import android.tools.flicker.subject.exceptions.SimpleFlickerAssertionError
import android.tools.io.Reader
import android.tools.utils.KotlinMockito
import android.tools.utils.assertThrows
import com.google.common.truth.Truth
import org.junit.AssumptionViolatedException
import org.junit.Test
import org.junit.runner.Description
import org.mockito.Mockito

class FlickerServiceCachedTestCaseTest {
    @Test
    fun reportsPassingResultMetric() {
        val decorator = Mockito.mock(IFlickerJUnitDecorator::class.java)
        val mockInstrumentation = Mockito.mock(Instrumentation::class.java)
        val mockFlickerService = Mockito.mock(FlickerService::class.java)
        val mockScenarioInstance = Mockito.mock(ScenarioInstance::class.java)
        val mockScenarioAssertion = Mockito.mock(ScenarioAssertion::class.java)
        val assertionResult =
            object : AssertionResult {
                override val name: String = "MOCK_SCENARIO#mockAssertion"
                override val assertionData =
                    listOf<AssertionData>(
                        object : AssertionData {
                            override fun checkAssertion(run: SubjectsParser) {
                                error("Unimplemented - shouldn't be called")
                            }
                        }
                    )
                override val assertionErrors = emptyList<FlickerAssertionError>()
                override val stabilityGroup = AssertionInvocationGroup.BLOCKING
                override val passed = true
            }
        Mockito.`when`(mockScenarioAssertion.execute()).thenReturn(assertionResult)
        Mockito.`when`(mockScenarioInstance.generateAssertions())
            .thenReturn(listOf(mockScenarioAssertion))
        Mockito.`when`(mockFlickerService.detectScenarios(KotlinMockito.any(Reader::class.java)))
            .thenReturn(listOf(mockScenarioInstance))

        val mockDescription = Mockito.mock(Description::class.java)
        val test =
            FlickerServiceCachedTestCase(
                assertion = mockScenarioAssertion,
                method = InjectedTestCase::class.java.getMethod("execute", Description::class.java),
                skipNonBlocking = false,
                isLast = false,
                injectedBy = decorator,
                instrumentation = mockInstrumentation,
                paramString = "",
            )
        test.execute(mockDescription)

        Mockito.verify(mockInstrumentation)
            .sendStatus(
                Mockito.eq(SendToInstrumentation.INST_STATUS_IN_PROGRESS),
                KotlinMockito.argThat {
                    this.getString(
                        "${FlickerServiceResultsCollector.FAAS_METRICS_PREFIX}::" +
                            assertionResult.name
                    ) == "0"
                }
            )
    }

    @Test
    fun reportsFailingResultMetric() {
        val decorator = Mockito.mock(IFlickerJUnitDecorator::class.java)
        val mockInstrumentation = Mockito.mock(Instrumentation::class.java)
        val mockFlickerService = Mockito.mock(FlickerService::class.java)
        val mockScenarioInstance = Mockito.mock(ScenarioInstance::class.java)
        val mockScenarioAssertion = Mockito.mock(ScenarioAssertion::class.java)
        val assertionResult =
            object : AssertionResult {
                override val name: String = "MY_CUSTOM_SCENARIO#myAssertion"
                override val assertionData =
                    listOf<AssertionData>(
                        object : AssertionData {
                            override fun checkAssertion(run: SubjectsParser) {
                                error("Unimplemented - shouldn't be called")
                            }
                        }
                    )
                override val assertionErrors =
                    listOf<FlickerAssertionError>(SimpleFlickerAssertionError("EXPECTED"))
                override val stabilityGroup = AssertionInvocationGroup.BLOCKING
                override val passed = false
            }
        Mockito.`when`(mockScenarioAssertion.execute()).thenReturn(assertionResult)
        Mockito.`when`(mockScenarioInstance.generateAssertions())
            .thenReturn(listOf(mockScenarioAssertion))
        Mockito.`when`(mockFlickerService.detectScenarios(KotlinMockito.any(Reader::class.java)))
            .thenReturn(listOf(mockScenarioInstance))

        val mockDescription = Mockito.mock(Description::class.java)
        val test =
            FlickerServiceCachedTestCase(
                assertion = mockScenarioAssertion,
                method = InjectedTestCase::class.java.getMethod("execute", Description::class.java),
                skipNonBlocking = false,
                isLast = false,
                injectedBy = decorator,
                instrumentation = mockInstrumentation,
                paramString = "",
            )

        val failure = assertThrows<Throwable> { test.execute(mockDescription) }
        Truth.assertThat(failure).hasMessageThat().startsWith("EXPECTED")

        Mockito.verify(mockInstrumentation)
            .sendStatus(
                Mockito.eq(SendToInstrumentation.INST_STATUS_IN_PROGRESS),
                KotlinMockito.argThat {
                    this.getString(
                        "${FlickerServiceResultsCollector.FAAS_METRICS_PREFIX}::" +
                            assertionResult.name
                    ) == "1"
                }
            )
    }

    @Test
    fun skippedIfNonBlocking() {
        val mockScenarioAssertion = Mockito.mock(ScenarioAssertion::class.java)
        val decorator = Mockito.mock(IFlickerJUnitDecorator::class.java)
        val mockInstrumentation = Mockito.mock(Instrumentation::class.java)
        val mockFlickerService = Mockito.mock(FlickerService::class.java)
        val mockScenarioInstance = Mockito.mock(ScenarioInstance::class.java)
        val assertionResult =
            object : AssertionResult {
                override val name: String = "MY_CUSTOM_SCENARIO#myAssertion"
                override val assertionData =
                    listOf<AssertionData>(
                        object : AssertionData {
                            override fun checkAssertion(run: SubjectsParser) {
                                error("Unimplemented - shouldn't be called")
                            }
                        }
                    )
                override val assertionErrors =
                    listOf<FlickerAssertionError>(SimpleFlickerAssertionError("EXPECTED"))
                override val stabilityGroup = AssertionInvocationGroup.NON_BLOCKING
                override val passed = false
            }
        Mockito.`when`(mockScenarioAssertion.execute()).thenReturn(assertionResult)
        Mockito.`when`(mockScenarioInstance.generateAssertions())
            .thenReturn(listOf(mockScenarioAssertion))
        Mockito.`when`(mockFlickerService.detectScenarios(KotlinMockito.any(Reader::class.java)))
            .thenReturn(listOf(mockScenarioInstance))

        val testCase =
            FlickerServiceCachedTestCase(
                assertion = mockScenarioAssertion,
                method = InjectedTestCase::class.java.getMethod("execute", Description::class.java),
                skipNonBlocking = true,
                isLast = false,
                injectedBy = decorator,
                instrumentation = mockInstrumentation,
                paramString = "",
            )

        val mockDescription = Mockito.mock(Description::class.java)
        val failure =
            assertThrows<AssumptionViolatedException> { testCase.execute(mockDescription) }
        Truth.assertThat(failure).hasMessageThat().isEqualTo("FaaS Test was non blocking - skipped")
    }
}
