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

import android.app.Instrumentation
import android.tools.traces.io.ResultWriter
import android.tools.traces.monitors.ITransitionMonitor
import android.tools.traces.parsers.WindowManagerStateHelper
import android.tools.utils.CleanFlickerEnvironmentRule
import android.tools.utils.TEST_SCENARIO
import android.tools.utils.assertThrows
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth
import org.junit.Before
import org.junit.ClassRule
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/** Tests for [TraceMonitorRule] */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class TraceMonitorRuleTest {
    private var startExecutionCount = 0
    private var setResultExecutionCount = 0

    private val monitorWithExceptionStart =
        createMonitor({ error(Consts.FAILURE) }, { setResultExecutionCount++ })
    private val monitorWithExceptionStop =
        createMonitor(
            { startExecutionCount++ },
            { error(Consts.FAILURE) },
        )
    private val monitorWithoutException =
        createMonitor({ startExecutionCount++ }, { setResultExecutionCount++ })

    @Before
    fun setup() {
        startExecutionCount = 0
        setResultExecutionCount = 0
    }

    @Test
    fun executesSuccessfully() {
        val rule = createRule(listOf(monitorWithoutException))
        rule.apply(base = null, description = Consts.description(this)).evaluate()
        Truth.assertWithMessage("Start executed").that(startExecutionCount).isEqualTo(1)
        Truth.assertWithMessage("Set result executed").that(setResultExecutionCount).isEqualTo(1)
    }

    @Test
    fun executesSuccessfullyMonitor2() {
        val rule = createRule(listOf(monitorWithoutException, monitorWithoutException))
        rule.apply(base = null, description = Consts.description(this)).evaluate()
        Truth.assertWithMessage("Start executed").that(startExecutionCount).isEqualTo(2)
        Truth.assertWithMessage("Set result executed").that(setResultExecutionCount).isEqualTo(2)
    }

    @Test
    fun executesWithStartFailure() {
        val failure =
            assertThrows<IllegalStateException> {
                val rule = createRule(listOf(monitorWithExceptionStart))
                rule.apply(base = null, description = Consts.description(this)).evaluate()
            }
        Truth.assertWithMessage("Failure").that(failure).hasMessageThat().contains(Consts.FAILURE)
        Truth.assertWithMessage("Start executed").that(startExecutionCount).isEqualTo(0)
        Truth.assertWithMessage("Set result executed").that(setResultExecutionCount).isEqualTo(1)
    }

    @Test
    fun executesStartFailureMonitor2() {
        val failure =
            assertThrows<IllegalStateException> {
                val rule = createRule(listOf(monitorWithExceptionStart, monitorWithoutException))
                rule.apply(base = null, description = Consts.description(this)).evaluate()
            }
        Truth.assertWithMessage("Failure").that(failure).hasMessageThat().contains(Consts.FAILURE)
        Truth.assertWithMessage("Start executed").that(startExecutionCount).isEqualTo(0)
        Truth.assertWithMessage("Set result executed").that(setResultExecutionCount).isEqualTo(2)
    }

    @Test
    fun executesWithStopFailure() {
        val failure =
            assertThrows<IllegalStateException> {
                val rule = createRule(listOf(monitorWithExceptionStop))
                rule.apply(base = null, description = Consts.description(this)).evaluate()
            }
        Truth.assertWithMessage("Failure").that(failure).hasMessageThat().contains(Consts.FAILURE)
        Truth.assertWithMessage("Start executed").that(startExecutionCount).isEqualTo(1)
        Truth.assertWithMessage("Set result executed").that(setResultExecutionCount).isEqualTo(0)
    }

    @Test
    fun executesStopFailureMonitor2() {
        val failure =
            assertThrows<IllegalStateException> {
                val rule = createRule(listOf(monitorWithExceptionStop, monitorWithoutException))
                rule.apply(base = null, description = Consts.description(this)).evaluate()
            }
        Truth.assertWithMessage("Failure").that(failure).hasMessageThat().contains(Consts.FAILURE)
        Truth.assertWithMessage("Start executed").that(startExecutionCount).isEqualTo(2)
        Truth.assertWithMessage("Set result executed").that(setResultExecutionCount).isEqualTo(1)
    }

    companion object {
        private fun createRule(traceMonitors: List<ITransitionMonitor>): TraceMonitorRule {
            val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()
            return TraceMonitorRule(
                traceMonitors,
                TEST_SCENARIO,
                WindowManagerStateHelper(),
                ResultWriter(),
                instrumentation
            )
        }

        private fun createMonitor(
            onStart: () -> Unit,
            onSetResult: (ResultWriter) -> Unit
        ): ITransitionMonitor =
            object : ITransitionMonitor {
                override fun start() {
                    onStart()
                }

                override fun stop(writer: ResultWriter) {
                    onSetResult(writer)
                }
            }

        @ClassRule @JvmField val ENV_CLEANUP = CleanFlickerEnvironmentRule()
    }
}
