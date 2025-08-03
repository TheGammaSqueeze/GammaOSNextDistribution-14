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

package android.tools.integration

import android.app.Instrumentation
import android.tools.Timestamps
import android.tools.datatypes.Region
import android.tools.device.apphelpers.MessagingAppHelper
import android.tools.flicker.AssertionInvocationGroup
import android.tools.flicker.FlickerConfig
import android.tools.flicker.ScenarioInstance
import android.tools.flicker.annotation.ExpectedScenarios
import android.tools.flicker.annotation.FlickerConfigProvider
import android.tools.flicker.assertions.FlickerTest
import android.tools.flicker.assertors.AssertionTemplate
import android.tools.flicker.config.FlickerConfig
import android.tools.flicker.config.FlickerConfigEntry
import android.tools.flicker.config.FlickerServiceConfig
import android.tools.flicker.config.ScenarioId
import android.tools.flicker.extractors.ScenarioExtractor
import android.tools.flicker.extractors.TraceSlice
import android.tools.flicker.junit.FlickerServiceJUnit4ClassRunner
import android.tools.flicker.subject.FlickerSubject
import android.tools.flicker.subject.layers.LayersTraceSubject
import android.tools.flicker.subject.region.RegionSubject
import android.tools.flicker.subject.wm.WindowManagerTraceSubject
import android.tools.io.Reader
import android.tools.traces.parsers.WindowManagerStateHelper
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(FlickerServiceJUnit4ClassRunner::class)
class FullTestRun {
    private val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()
    private val wmHelper = WindowManagerStateHelper(instrumentation)
    private val testApp = MessagingAppHelper(instrumentation)

    @Before
    fun setup() {
        // Nothing to do
    }

    @ExpectedScenarios(["ENTIRE_TRACE"])
    @Test
    fun openApp() {
        testApp.launchViaIntent(wmHelper)
    }

    @After
    fun teardown() {
        testApp.exit(wmHelper)
    }

    companion object {
        @JvmStatic
        @FlickerConfigProvider
        fun flickerConfigProvider(): FlickerConfig =
            FlickerConfig().use(FlickerServiceConfig.DEFAULT).use(CUSTOM_CONFIG)

        val CUSTOM_CONFIG =
            FlickerConfigEntry(
                scenarioId = ScenarioId("MY_CUSTOM_SCENARIO"),
                extractor =
                    object : ScenarioExtractor {
                        override fun extract(reader: Reader): List<TraceSlice> {
                            return listOf(TraceSlice(Timestamps.min(), Timestamps.max()))
                        }
                    },
                assertions =
                    mapOf(
                        object : AssertionTemplate("internalWmCheck") {
                            override fun doEvaluate(
                                scenarioInstance: ScenarioInstance,
                                flicker: FlickerTest
                            ) {
                                var trace: WindowManagerTraceSubject? = null
                                var executionCount = 0
                                flicker.assertWm {
                                    executionCount++
                                    trace = this
                                    this.isNotEmpty()

                                    Truth.assertWithMessage("Execution count")
                                        .that(executionCount)
                                        .isEqualTo(1)
                                }
                                flicker.assertWm {
                                    executionCount++
                                    val failure: Result<Any> = runCatching { this.isEmpty() }
                                    if (failure.isSuccess) {
                                        error("Should have thrown failure")
                                    }

                                    Truth.assertWithMessage("Execution count")
                                        .that(executionCount)
                                        .isEqualTo(2)
                                }
                                flicker.assertWmStart {
                                    executionCount++
                                    validateState(this, trace?.first())
                                    validateVisibleRegion(
                                        this.visibleRegion(),
                                        trace?.first()?.visibleRegion()
                                    )

                                    Truth.assertWithMessage("Execution count")
                                        .that(executionCount)
                                        .isEqualTo(3)
                                }
                                flicker.assertWmEnd {
                                    executionCount++
                                    validateState(this, trace?.last())
                                    validateVisibleRegion(
                                        this.visibleRegion(),
                                        trace?.last()?.visibleRegion()
                                    )

                                    Truth.assertWithMessage("Execution count")
                                        .that(executionCount)
                                        .isEqualTo(4)
                                }
                            }
                        } to AssertionInvocationGroup.BLOCKING,
                        object : AssertionTemplate("internalLayersCheck") {
                            override fun doEvaluate(
                                scenarioInstance: ScenarioInstance,
                                flicker: FlickerTest
                            ) {
                                var trace: LayersTraceSubject? = null
                                var executionCount = 0
                                flicker.assertLayers {
                                    executionCount++
                                    trace = this
                                    this.isNotEmpty()

                                    Truth.assertWithMessage("Execution count")
                                        .that(executionCount)
                                        .isEqualTo(1)
                                }
                                flicker.assertLayers {
                                    executionCount++
                                    val failure: Result<Any> = runCatching { this.isEmpty() }
                                    if (failure.isSuccess) {
                                        error("Should have thrown failure")
                                    }

                                    Truth.assertWithMessage("Execution count")
                                        .that(executionCount)
                                        .isEqualTo(2)
                                }
                                flicker.assertLayersStart {
                                    executionCount++
                                    validateState(this, trace?.first())
                                    validateVisibleRegion(
                                        this.visibleRegion(),
                                        trace?.first()?.visibleRegion()
                                    )

                                    Truth.assertWithMessage("Execution count")
                                        .that(executionCount)
                                        .isEqualTo(3)
                                }
                                flicker.assertLayersEnd {
                                    executionCount++
                                    validateState(this, trace?.last())
                                    validateVisibleRegion(
                                        this.visibleRegion(),
                                        trace?.last()?.visibleRegion()
                                    )

                                    Truth.assertWithMessage("Execution count")
                                        .that(executionCount)
                                        .isEqualTo(4)
                                }
                            }
                        } to AssertionInvocationGroup.BLOCKING
                    ),
                enabled = true
            )

        private fun validateState(actual: FlickerSubject?, expected: FlickerSubject?) {
            Truth.assertWithMessage("Actual state").that(actual).isNotNull()
            Truth.assertWithMessage("Expected state").that(expected).isNotNull()
        }

        private fun validateVisibleRegion(
            actual: RegionSubject?,
            expected: RegionSubject?,
        ) {
            Truth.assertWithMessage("Actual visible region").that(actual).isNotNull()
            Truth.assertWithMessage("Expected visible region").that(expected).isNotNull()
            actual?.coversExactly(expected?.region ?: Region.EMPTY)

            val failure: Result<Any?> = runCatching {
                actual?.isHigher(expected?.region ?: Region.EMPTY)
            }
            if (failure.isSuccess) {
                error("Should have thrown failure")
            }
        }
    }
}
