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
import android.platform.test.annotations.Presubmit
import android.tools.Scenario
import android.tools.datatypes.Region
import android.tools.device.apphelpers.MessagingAppHelper
import android.tools.flicker.annotation.FlickerServiceCompatible
import android.tools.flicker.junit.FlickerBuilderProvider
import android.tools.flicker.junit.FlickerParametersRunnerFactory
import android.tools.flicker.legacy.FlickerBuilder
import android.tools.flicker.legacy.LegacyFlickerTest
import android.tools.flicker.legacy.LegacyFlickerTestFactory
import android.tools.flicker.subject.FlickerSubject
import android.tools.flicker.subject.layers.LayersTraceSubject
import android.tools.flicker.subject.region.RegionSubject
import android.tools.flicker.subject.wm.WindowManagerTraceSubject
import android.tools.io.RunStatus
import android.tools.traces.parsers.WindowManagerStateHelper
import androidx.test.platform.app.InstrumentationRegistry
import com.android.launcher3.tapl.LauncherInstrumentation
import com.google.common.truth.Truth
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@FlickerServiceCompatible(expectedCujs = ["ENTIRE_TRACE"])
@RunWith(Parameterized::class)
@Parameterized.UseParametersRunnerFactory(FlickerParametersRunnerFactory::class)
class FullLegacyTestRun(private val flicker: LegacyFlickerTest) {
    private val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()
    private val testApp = MessagingAppHelper(instrumentation)
    private val tapl: LauncherInstrumentation = LauncherInstrumentation()

    init {
        flicker.scenario.setIsTablet(
            WindowManagerStateHelper(instrumentation, clearCacheAfterParsing = false)
                .currentState
                .wmState
                .isTablet
        )
        tapl.setExpectedRotationCheckEnabled(true)
    }

    /**
     * Entry point for the test runner. It will use this method to initialize and cache flicker
     * executions
     */
    @FlickerBuilderProvider
    fun buildFlicker(): FlickerBuilder {
        return FlickerBuilder(instrumentation).apply {
            setup { flicker.scenario.setIsTablet(wmHelper.currentState.wmState.isTablet) }
            teardown { testApp.exit(wmHelper) }
            transitions { testApp.launchViaIntent(wmHelper) }
        }
    }

    /**
     * This is a shel test from the flicker infra to ensure the WM tracing pipeline executed
     * entirely executed correctly
     */
    @Presubmit
    @Test
    fun internalWmCheck() {
        var trace: WindowManagerTraceSubject? = null
        var executionCount = 0
        flicker.assertWm {
            executionCount++
            trace = this
            this.isNotEmpty()
        }
        flicker.assertWm {
            executionCount++
            val failure: Result<Any> = runCatching { this.isEmpty() }
            if (failure.isSuccess) {
                error("Should have thrown failure")
            }
        }
        flicker.assertWmStart {
            executionCount++
            validateState(this, trace?.first())
            validateVisibleRegion(this.visibleRegion(), trace?.first()?.visibleRegion())
        }
        flicker.assertWmEnd {
            executionCount++
            validateState(this, trace?.last())
            validateVisibleRegion(this.visibleRegion(), trace?.last()?.visibleRegion())
        }
        Truth.assertWithMessage("Execution count").that(executionCount).isEqualTo(4)
    }

    /**
     * This is a shel test from the flicker infra to ensure the Layers tracing pipeline executed
     * entirely executed correctly
     */
    @Presubmit
    @Test
    fun internalLayersCheck() {
        var trace: LayersTraceSubject? = null
        var executionCount = 0
        flicker.assertLayers {
            executionCount++
            trace = this
            this.isNotEmpty()
        }
        flicker.assertLayers {
            executionCount++
            val failure: Result<Any> = runCatching { this.isEmpty() }
            if (failure.isSuccess) {
                error("Should have thrown failure")
            }
        }
        flicker.assertLayersStart {
            executionCount++
            validateState(this, trace?.first())
            validateVisibleRegion(this.visibleRegion(), trace?.first()?.visibleRegion())
        }
        flicker.assertLayersEnd {
            executionCount++
            validateState(this, trace?.last())
            validateVisibleRegion(this.visibleRegion(), trace?.last()?.visibleRegion())
        }
        Truth.assertWithMessage("Execution count").that(executionCount).isEqualTo(4)
    }

    @Presubmit
    @Test
    fun exceptionMessageCheck() {
        val failure: Result<Any> = runCatching { flicker.assertLayers { this.isEmpty() } }
        val exception = failure.exceptionOrNull() ?: error("Should have thrown failure")
        Truth.assertWithMessage("Artifact path on exception")
            .that(exception)
            .hasMessageThat()
            .contains(RunStatus.ASSERTION_FAILED.prefix)
    }

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

    companion object {
        /**
         * Creates the test configurations.
         *
         * See [LegacyFlickerTestFactory.nonRotationTests] for configuring screen orientation and
         * navigation modes.
         */
        @Parameterized.Parameters(name = "{0}")
        @JvmStatic
        fun getParams() =
            LegacyFlickerTestFactory.nonRotationTests(
                extraArgs = mapOf(Scenario.FAAS_BLOCKING to true)
            )
    }
}
