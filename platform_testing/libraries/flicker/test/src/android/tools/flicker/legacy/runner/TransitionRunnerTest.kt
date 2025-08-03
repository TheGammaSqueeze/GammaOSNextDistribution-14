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

package android.tools.flicker.legacy.runner

import android.annotation.SuppressLint
import android.app.Instrumentation
import android.os.SystemClock
import android.tools.createMockedFlicker
import android.tools.flicker.legacy.FlickerTestData
import android.tools.io.RunStatus
import android.tools.traces.TRACE_CONFIG_REQUIRE_CHANGES
import android.tools.traces.io.ResultReader
import android.tools.traces.io.ResultWriter
import android.tools.traces.monitors.ITransitionMonitor
import android.tools.utils.CleanFlickerEnvironmentRule
import android.tools.utils.TEST_SCENARIO
import android.tools.utils.assertExceptionMessage
import android.view.WindowManagerGlobal
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth
import org.junit.After
import org.junit.ClassRule
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/** Tests for [TransitionRunner] */
@SuppressLint("VisibleForTests")
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class TransitionRunnerTest {
    private val executionOrder = mutableListOf<String>()

    private val runSetup: FlickerTestData.() -> Unit = {
        executionOrder.add(Consts.SETUP)
        SystemClock.sleep(100)
    }
    private val runTeardown: FlickerTestData.() -> Unit = {
        executionOrder.add(Consts.TEARDOWN)
        SystemClock.sleep(100)
    }
    private val runTransition: FlickerTestData.() -> Unit = {
        executionOrder.add(Consts.TRANSITION)
        SystemClock.sleep(100)
    }
    private val throwError: FlickerTestData.() -> Unit = { error(Consts.FAILURE) }

    @After
    fun assertTracingStopped() {
        val windowManager =
            WindowManagerGlobal.getWindowManagerService()
                ?: error("Unable to acquire WindowManager")
        Truth.assertWithMessage("WM Trace running")
            .that(windowManager.isWindowTraceEnabled)
            .isFalse()
    }

    @Test
    fun runsTransition() {
        val runner = TransitionRunner(TEST_SCENARIO, instrumentation, ResultWriter())
        val dummyMonitor = dummyMonitor()
        val mockedFlicker =
            createMockedFlicker(
                setup = listOf(runSetup),
                teardown = listOf(runTeardown),
                transitions = listOf(runTransition),
                extraMonitor = dummyMonitor
            )
        val result = runner.execute(mockedFlicker, Consts.description(this))
        val reader = ResultReader(result, TRACE_CONFIG_REQUIRE_CHANGES)

        validateExecutionOrder(hasTransition = true)
        dummyMonitor.validate()
        TestUtils.validateTransitionTime(result)
        Truth.assertWithMessage("Run status")
            .that(reader.runStatus)
            .isEqualTo(RunStatus.RUN_EXECUTED)
    }

    @Test
    fun failsWithNoTransitions() {
        val runner = TransitionRunner(TEST_SCENARIO, instrumentation, ResultWriter())
        val dummyMonitor = dummyMonitor()
        val mockedFlicker =
            createMockedFlicker(
                setup = listOf(runSetup),
                teardown = listOf(runTeardown),
                extraMonitor = dummyMonitor
            )
        val result = runner.execute(mockedFlicker, Consts.description(this))
        val reader = ResultReader(result, TRACE_CONFIG_REQUIRE_CHANGES)

        validateExecutionOrder(hasTransition = false)
        dummyMonitor.validate()
        TestUtils.validateTransitionTime(result)
        Truth.assertWithMessage("Run status").that(reader.runStatus).isEqualTo(RunStatus.RUN_FAILED)
        assertExceptionMessage(result.executionError, EMPTY_TRANSITIONS_ERROR)
    }

    @Test
    fun failsWithTransitionError() {
        val runner = TransitionRunner(TEST_SCENARIO, instrumentation, ResultWriter())
        val dummyMonitor = dummyMonitor()
        val mockedFlicker =
            createMockedFlicker(
                setup = listOf(runSetup),
                teardown = listOf(runTeardown),
                transitions = listOf(throwError),
                extraMonitor = dummyMonitor
            )
        val result = runner.execute(mockedFlicker, Consts.description(this))
        val reader = ResultReader(result, TRACE_CONFIG_REQUIRE_CHANGES)

        validateExecutionOrder(hasTransition = false)
        dummyMonitor.validate()
        TestUtils.validateTransitionTime(result)
        Truth.assertWithMessage("Run status").that(reader.runStatus).isEqualTo(RunStatus.RUN_FAILED)
        assertExceptionMessage(result.executionError, Consts.FAILURE)
    }

    @Test
    fun failsWithSetupErrorAndHasTraces() {
        val runner = TransitionRunner(TEST_SCENARIO, instrumentation, ResultWriter())
        val dummyMonitor = dummyMonitor()
        val mockedFlicker =
            createMockedFlicker(
                setup = listOf(runSetup, throwError),
                teardown = listOf(runTeardown),
                transitions = listOf(runTransition),
                extraMonitor = dummyMonitor
            )
        val result = runner.execute(mockedFlicker, Consts.description(this))
        val reader = ResultReader(result, TRACE_CONFIG_REQUIRE_CHANGES)

        validateExecutionOrder(hasTransition = false)
        dummyMonitor.validate()
        TestUtils.validateTransitionTimeIsEmpty(result)
        Truth.assertWithMessage("Run status").that(reader.runStatus).isEqualTo(RunStatus.RUN_FAILED)
        assertExceptionMessage(result.executionError, Consts.FAILURE)
    }

    @Test
    fun failsWithTeardownErrorAndHasTraces() {
        val runner = TransitionRunner(TEST_SCENARIO, instrumentation, ResultWriter())
        val dummyMonitor = dummyMonitor()
        val mockedFlicker =
            createMockedFlicker(
                setup = listOf(runSetup),
                teardown = listOf(runTeardown, throwError),
                transitions = listOf(runTransition),
                extraMonitor = dummyMonitor
            )
        val result = runner.execute(mockedFlicker, Consts.description(this))
        val reader = ResultReader(result, TRACE_CONFIG_REQUIRE_CHANGES)

        validateExecutionOrder(hasTransition = true)
        dummyMonitor.validate()
        TestUtils.validateTransitionTime(result)
        Truth.assertWithMessage("Run status").that(reader.runStatus).isEqualTo(RunStatus.RUN_FAILED)
        assertExceptionMessage(result.executionError, Consts.FAILURE)
    }

    private fun assertContainsOrNot(value: String, hasValue: Boolean): String? {
        return if (hasValue) {
            Truth.assertWithMessage("$value executed").that(executionOrder).contains(value)
            value
        } else {
            Truth.assertWithMessage("$value skipped").that(executionOrder).doesNotContain(value)
            null
        }
    }

    private fun validateExecutionOrder(hasTransition: Boolean) {
        val expected = mutableListOf<String>()
        assertContainsOrNot(Consts.SETUP, hasValue = true)?.also { expected.add(it) }
        assertContainsOrNot(Consts.TRANSITION, hasTransition)?.also { expected.add(it) }
        assertContainsOrNot(Consts.TEARDOWN, hasValue = true)?.also { expected.add(it) }

        Truth.assertWithMessage("Execution order")
            .that(executionOrder)
            .containsExactlyElementsIn(expected)
            .inOrder()
    }

    private fun dummyMonitor() =
        object : ITransitionMonitor {
            private var startExecuted = false
            private var setResultExecuted = false

            override fun start() {
                startExecuted = true
            }

            override fun stop(writer: ResultWriter) {
                setResultExecuted = true
            }

            fun validate() {
                Truth.assertWithMessage("Start executed").that(startExecuted).isTrue()
                Truth.assertWithMessage("Set result executed").that(setResultExecuted).isTrue()
            }
        }

    companion object {
        private val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()

        @ClassRule @JvmField val ENV_CLEANUP = CleanFlickerEnvironmentRule()
    }
}
