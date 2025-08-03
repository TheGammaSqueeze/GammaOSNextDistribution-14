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

import android.annotation.SuppressLint
import android.device.collectors.DataRecord
import android.tools.flicker.FlickerServiceResultsCollector.Companion.EXECUTION_ERROR_STATUS_CODE
import android.tools.flicker.FlickerServiceResultsCollector.Companion.FLICKER_ASSERTIONS_COUNT_KEY
import android.tools.flicker.FlickerServiceResultsCollector.Companion.OK_STATUS_CODE
import android.tools.flicker.FlickerServiceResultsCollector.Companion.WINSCOPE_FILE_PATH_KEY
import android.tools.flicker.FlickerServiceResultsCollector.Companion.getKeyForAssertionResult
import android.tools.flicker.assertions.AssertionData
import android.tools.flicker.assertions.AssertionResult
import android.tools.flicker.assertions.ScenarioAssertion
import android.tools.flicker.assertions.SubjectsParser
import android.tools.flicker.subject.exceptions.FlickerAssertionError
import android.tools.flicker.subject.exceptions.SimpleFlickerAssertionError
import android.tools.io.Reader
import android.tools.traces.wm.TransitionsTrace
import android.tools.utils.CleanFlickerEnvironmentRule
import android.tools.utils.KotlinMockito
import android.tools.utils.MockLayersTraceBuilder
import android.tools.utils.MockWindowManagerTraceBuilder
import android.tools.utils.ParsedTracesReader
import android.tools.utils.TestArtifact
import com.google.common.truth.Truth
import org.junit.ClassRule
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.Description
import org.junit.runner.notification.Failure
import org.junit.runners.MethodSorters
import org.mockito.Mockito

/**
 * Contains [FlickerServiceResultsCollector] tests. To run this test: `atest
 * FlickerLibTest:FlickerServiceResultsCollectorTest`
 */
@SuppressLint("VisibleForTests")
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class FlickerServiceResultsCollectorTest {
    @Test
    fun reportsMetricsOnlyForPassingTestsIfRequested() {
        val collector = createCollector(reportOnlyForPassingTests = true)
        val runData = DataRecord()
        val runDescription = Description.createSuiteDescription("TestSuite")
        val testData = Mockito.mock(DataRecord::class.java)
        val testDescription = Description.createTestDescription(this::class.java, "TestName")

        collector.onTestRunStart(runData, runDescription)
        collector.onTestStart(testData, testDescription)
        collector.onTestFail(testData, testDescription, Mockito.mock(Failure::class.java))
        collector.onTestEnd(testData, testDescription)
        collector.onTestRunEnd(runData, Mockito.mock(org.junit.runner.Result::class.java))

        Truth.assertThat(collector.executionErrors).isEmpty()
        Truth.assertThat(collector.assertionResultsByTest[testDescription]).isNull()
        Truth.assertThat(runData.hasMetrics()).isFalse()

        // Reports only FaaS status & FaaS result file path
        Mockito.verify(testData).addStringMetric("FAAS_STATUS", OK_STATUS_CODE.toString())
        Mockito.verify(testData)
            .addStringMetric(
                Mockito.eq(FlickerServiceResultsCollector.FAAS_RESULTS_FILE_PATH_KEY),
                Mockito.anyString()
            )
        // No other calls to addStringMetric
        Mockito.verify(testData, Mockito.times(2))
            .addStringMetric(Mockito.anyString(), Mockito.anyString())
    }

    @Test
    fun reportsMetricsForFailingTestsIfRequested() {
        val collector =
            createCollector(reportOnlyForPassingTests = false, collectMetricsPerTest = true)
        val runData = DataRecord()
        val runDescription = Description.createSuiteDescription("TestSuite")
        val testData = DataRecord()
        val testDescription = Description.createTestDescription(this::class.java, "TestName")

        collector.onTestRunStart(runData, runDescription)
        collector.onTestStart(testData, testDescription)
        collector.onTestFail(testData, testDescription, Mockito.mock(Failure::class.java))
        collector.onTestEnd(testData, testDescription)
        collector.onTestRunEnd(runData, Mockito.mock(org.junit.runner.Result::class.java))

        Truth.assertThat(collector.executionErrors).isEmpty()
        Truth.assertThat(collector.resultsForTest(testDescription)).isNotEmpty()
        Truth.assertThat(testData.hasMetrics()).isTrue()
        Truth.assertThat(runData.hasMetrics()).isFalse()
    }

    @Test
    fun collectsMetricsForEachTestIfRequested() {
        val collector = createCollector(collectMetricsPerTest = true)
        val runData = DataRecord()
        val runDescription = Description.createSuiteDescription("TestSuite")
        val testData = DataRecord()
        val testDescription = Description.createTestDescription(this::class.java, "TestName")

        collector.onTestRunStart(runData, runDescription)
        collector.onTestStart(testData, testDescription)
        collector.onTestEnd(testData, testDescription)
        collector.onTestRunEnd(runData, Mockito.mock(org.junit.runner.Result::class.java))

        Truth.assertThat(collector.executionErrors).isEmpty()
        Truth.assertThat(collector.resultsForTest(testDescription)).isNotEmpty()
        Truth.assertThat(testData.hasMetrics()).isTrue()
        Truth.assertThat(runData.hasMetrics()).isFalse()
    }

    @Test
    fun collectsMetricsForEntireTestRunIfRequested() {
        val collector = createCollector(collectMetricsPerTest = false)
        val runData = DataRecord()
        val runDescription = Description.createSuiteDescription(this::class.java)
        val testData = DataRecord()
        val testDescription = Description.createTestDescription(this::class.java, "TestName")

        collector.onTestRunStart(runData, runDescription)
        collector.onTestStart(testData, testDescription)
        collector.onTestEnd(testData, testDescription)
        collector.onTestRunEnd(runData, Mockito.mock(org.junit.runner.Result::class.java))

        Truth.assertThat(collector.executionErrors).isEmpty()
        Truth.assertThat(collector.assertionResults).isNotEmpty()
        Truth.assertThat(testData.hasMetrics()).isFalse()
        Truth.assertThat(runData.hasMetrics()).isTrue()
    }

    @Test
    fun reportsAssertionCountMetric() {
        val assertionResults = listOf(mockSuccessfulAssertionResult, mockFailedAssertionResult)
        val collector = createCollector(assertionResults = assertionResults)
        val runData = DataRecord()
        val runDescription = Description.createSuiteDescription(this::class.java)
        val testData = SpyDataRecord()
        val testDescription = Description.createTestDescription(this::class.java, "TestName")

        collector.onTestRunStart(runData, runDescription)
        collector.onTestStart(testData, testDescription)
        collector.onTestEnd(testData, testDescription)
        collector.onTestRunEnd(runData, Mockito.mock(org.junit.runner.Result::class.java))

        Truth.assertThat(collector.executionErrors).isEmpty()
        Truth.assertThat(collector.assertionResults).isNotEmpty()

        Truth.assertThat(testData.hasMetrics()).isTrue()
        Truth.assertThat(testData.stringMetrics).containsKey(FLICKER_ASSERTIONS_COUNT_KEY)
        Truth.assertThat(testData.stringMetrics[FLICKER_ASSERTIONS_COUNT_KEY])
            .isEqualTo("${assertionResults.size}")

        Truth.assertThat(runData.hasMetrics()).isFalse()
    }

    @Test
    fun reportsMetricForTraceFile() {
        val assertionResults = listOf(mockSuccessfulAssertionResult, mockFailedAssertionResult)
        val collector = createCollector(assertionResults = assertionResults)
        val runData = DataRecord()
        val runDescription = Description.createSuiteDescription(this::class.java)
        val testData = SpyDataRecord()
        val testDescription = Description.createTestDescription(this::class.java, "TestName")

        collector.onTestRunStart(runData, runDescription)
        collector.onTestStart(testData, testDescription)
        collector.onTestEnd(testData, testDescription)
        collector.onTestRunEnd(runData, Mockito.mock(org.junit.runner.Result::class.java))

        Truth.assertThat(collector.executionErrors).isEmpty()
        Truth.assertThat(collector.assertionResults).isNotEmpty()

        Truth.assertThat(testData.hasMetrics()).isTrue()
        Truth.assertThat(testData.stringMetrics).containsKey(WINSCOPE_FILE_PATH_KEY)
        Truth.assertThat(testData.stringMetrics[WINSCOPE_FILE_PATH_KEY])
            .isEqualTo("IN_MEMORY/Empty")

        Truth.assertThat(runData.hasMetrics()).isFalse()
    }

    @Test
    fun reportsMetricForTraceFileOnServiceFailure() {
        val assertionResults = listOf(mockSuccessfulAssertionResult, mockFailedAssertionResult)
        val collector =
            createCollector(assertionResults = assertionResults, serviceProcessingError = true)
        val runData = DataRecord()
        val runDescription = Description.createSuiteDescription(this::class.java)
        val testData = SpyDataRecord()
        val testDescription = Description.createTestDescription(this::class.java, "TestName")

        collector.onTestRunStart(runData, runDescription)
        collector.onTestStart(testData, testDescription)
        collector.onTestEnd(testData, testDescription)
        collector.onTestRunEnd(runData, Mockito.mock(org.junit.runner.Result::class.java))

        Truth.assertThat(collector.executionErrors).isNotEmpty()
        Truth.assertThat(collector.assertionResults).isEmpty()

        Truth.assertThat(testData.hasMetrics()).isTrue()
        Truth.assertThat(testData.stringMetrics).containsKey(WINSCOPE_FILE_PATH_KEY)
        Truth.assertThat(testData.stringMetrics[WINSCOPE_FILE_PATH_KEY])
            .isEqualTo("IN_MEMORY/Empty")

        Truth.assertThat(runData.hasMetrics()).isFalse()
    }

    @Test
    fun reportsDuplicateAssertionsWithIndex() {
        val assertionResults =
            listOf(
                mockSuccessfulAssertionResult,
                mockSuccessfulAssertionResult,
                mockFailedAssertionResult
            )
        val collector = createCollector(assertionResults = assertionResults)
        val runData = DataRecord()
        val runDescription = Description.createSuiteDescription(this::class.java)
        val testData = SpyDataRecord()
        val testDescription = Description.createTestDescription(this::class.java, "TestName")

        collector.onTestRunStart(runData, runDescription)
        collector.onTestStart(testData, testDescription)
        collector.onTestEnd(testData, testDescription)
        collector.onTestRunEnd(runData, Mockito.mock(org.junit.runner.Result::class.java))

        Truth.assertThat(collector.executionErrors).isEmpty()
        Truth.assertThat(collector.assertionResults).isNotEmpty()

        Truth.assertThat(testData.hasMetrics()).isTrue()
        Truth.assertThat(testData.stringMetrics).containsKey(WINSCOPE_FILE_PATH_KEY)
        Truth.assertThat(testData.stringMetrics[WINSCOPE_FILE_PATH_KEY])
            .isEqualTo("IN_MEMORY/Empty")

        Truth.assertThat(testData.stringMetrics).containsKey(FLICKER_ASSERTIONS_COUNT_KEY)
        Truth.assertThat(testData.stringMetrics[FLICKER_ASSERTIONS_COUNT_KEY])
            .isEqualTo("${assertionResults.size}")

        val key0 = "${getKeyForAssertionResult(mockSuccessfulAssertionResult)}_0"
        val key1 = "${getKeyForAssertionResult(mockSuccessfulAssertionResult)}_1"
        val key2 = "${getKeyForAssertionResult(mockFailedAssertionResult)}_0"
        Truth.assertThat(testData.stringMetrics).containsKey(key0)
        Truth.assertThat(testData.stringMetrics[key0]).isEqualTo("0")
        Truth.assertThat(testData.stringMetrics[key1]).isEqualTo("0")
        Truth.assertThat(testData.stringMetrics[key2]).isEqualTo("1")

        Truth.assertThat(runData.hasMetrics()).isFalse()
    }

    @Test
    fun reportOkFlickerServiceStatus() {
        val collector = createCollector()
        val runData = DataRecord()
        val runDescription = Description.createSuiteDescription("TestSuite")
        val testData = Mockito.mock(DataRecord::class.java)
        val testDescription = Description.createTestDescription(this::class.java, "TestName")

        collector.onTestRunStart(runData, runDescription)
        collector.onTestStart(testData, testDescription)
        collector.onTestFail(testData, testDescription, Mockito.mock(Failure::class.java))
        collector.onTestEnd(testData, testDescription)
        collector.onTestRunEnd(runData, Mockito.mock(org.junit.runner.Result::class.java))

        Truth.assertThat(collector.executionErrors).isEmpty()

        // Reports only FaaS status
        Mockito.verify(testData).addStringMetric("FAAS_STATUS", OK_STATUS_CODE.toString())
    }

    @Test
    fun reportExecutionErrorFlickerServiceStatus() {
        val assertionResults = listOf(mockSuccessfulAssertionResult, mockFailedAssertionResult)
        val collector =
            createCollector(assertionResults = assertionResults, serviceProcessingError = true)
        val runData = DataRecord()
        val runDescription = Description.createSuiteDescription(this::class.java)
        val testData = SpyDataRecord()
        val testDescription = Description.createTestDescription(this::class.java, "TestName")

        collector.onTestRunStart(runData, runDescription)
        collector.onTestStart(testData, testDescription)
        collector.onTestEnd(testData, testDescription)
        collector.onTestRunEnd(runData, Mockito.mock(org.junit.runner.Result::class.java))

        Truth.assertThat(collector.executionErrors).isNotEmpty()

        Truth.assertThat(testData.stringMetrics["FAAS_STATUS"])
            .isEqualTo(EXECUTION_ERROR_STATUS_CODE.toString())
    }

    private fun createCollector(
        assertionResults: Collection<AssertionResult> = listOf(mockSuccessfulAssertionResult),
        reportOnlyForPassingTests: Boolean = true,
        collectMetricsPerTest: Boolean = true,
        serviceProcessingError: Boolean = false,
    ): FlickerServiceResultsCollector {
        val mockTraceCollector = Mockito.mock(TracesCollector::class.java)
        Mockito.`when`(mockTraceCollector.stop())
            .thenReturn(
                ParsedTracesReader(
                    artifact = TestArtifact.EMPTY,
                    wmTrace = MockWindowManagerTraceBuilder().build(),
                    layersTrace = MockLayersTraceBuilder().build(),
                    transitionsTrace = TransitionsTrace(emptyList()),
                    transactionsTrace = null
                )
            )
        val mockFlickerService = Mockito.mock(FlickerService::class.java)
        if (serviceProcessingError) {
            Mockito.`when`(
                    mockFlickerService.detectScenarios(KotlinMockito.any(Reader::class.java))
                )
                .thenThrow(RuntimeException("Flicker Service Processing Error"))
        } else {
            val mockScenarioInstance = Mockito.mock(ScenarioInstance::class.java)
            val mockedAssertions =
                assertionResults.map { assertion ->
                    val mockScenarioAssertion = Mockito.mock(ScenarioAssertion::class.java)
                    Mockito.`when`(mockScenarioAssertion.execute()).thenReturn(assertion)
                    mockScenarioAssertion
                }
            Mockito.`when`(mockScenarioInstance.generateAssertions()).thenReturn(mockedAssertions)

            Mockito.`when`(
                    mockFlickerService.detectScenarios(KotlinMockito.any(Reader::class.java))
                )
                .thenReturn(listOf(mockScenarioInstance))
        }

        return FlickerServiceResultsCollector(
            tracesCollector = mockTraceCollector,
            flickerService = mockFlickerService,
            reportOnlyForPassingTests = reportOnlyForPassingTests,
            collectMetricsPerTest = collectMetricsPerTest,
        )
    }

    companion object {
        val mockSuccessfulAssertionResult =
            object : AssertionResult {
                override val name: String = "MOCK_SCENARIO#mockSuccessfulAssertion"
                override val assertionData =
                    listOf<AssertionData>(
                        object : AssertionData {
                            override fun checkAssertion(run: SubjectsParser) {
                                error("Unimplemented - shouldn't be called")
                            }
                        }
                    )
                override val assertionErrors = listOf<FlickerAssertionError>()
                override val stabilityGroup = AssertionInvocationGroup.BLOCKING
                override val passed = true
            }

        val mockFailedAssertionResult =
            object : AssertionResult {
                override val name: String = "MOCK_SCENARIO#mockFailedAssertion"
                override val assertionData =
                    listOf<AssertionData>(
                        object : AssertionData {
                            override fun checkAssertion(run: SubjectsParser) {
                                error("Unimplemented - shouldn't be called")
                            }
                        }
                    )
                override val assertionErrors =
                    listOf<FlickerAssertionError>(SimpleFlickerAssertionError("Assertion failed"))
                override val stabilityGroup = AssertionInvocationGroup.BLOCKING
                override val passed = false
                override val failed: Boolean = true
            }

        private class SpyDataRecord : DataRecord() {
            val stringMetrics = mutableMapOf<String, String>()
            override fun addStringMetric(key: String, value: String) {
                super.addStringMetric(key, value)
                stringMetrics[key] = value
            }
        }

        @ClassRule @JvmField val ENV_CLEANUP = CleanFlickerEnvironmentRule()
    }
}
