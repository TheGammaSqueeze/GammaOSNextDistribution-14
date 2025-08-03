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
import android.tools.flicker.legacy.AbstractFlickerTestData
import android.tools.flicker.legacy.FlickerTestData
import android.tools.traces.TRACE_CONFIG_REQUIRE_CHANGES
import android.tools.traces.io.ResultReader
import android.tools.traces.io.ResultWriter
import android.tools.traces.monitors.TraceMonitor
import android.tools.traces.parsers.WindowManagerStateHelper
import android.tools.utils.CleanFlickerEnvironmentRule
import android.tools.utils.TEST_SCENARIO
import android.tools.utils.assertExceptionMessage
import android.tools.utils.assertThrows
import android.tools.utils.newTestResultWriter
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth
import org.junit.Before
import org.junit.ClassRule
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import org.mockito.Mockito

/** Tests for [TransitionExecutionRule] */
@SuppressLint("VisibleForTests")
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class TransitionExecutionRuleTest {
    private var executed = false

    private val runTransition: FlickerTestData.() -> Unit = {
        executed = true
        SystemClock.sleep(100)
    }
    private val runCreateValidTags: FlickerTestData.() -> Unit = {
        createTag(VALID_TAG_1)
        createTag(VALID_TAG_2)
    }
    private val runInvalidTagSpace: FlickerTestData.() -> Unit = { createTag(INVALID_TAG_SPACE) }
    private val runInvalidTagUnderscore: FlickerTestData.() -> Unit = {
        createTag(INVALID_TAG_UNDERSCORE)
    }
    private val throwTransitionError: FlickerTestData.() -> Unit = { error(Consts.FAILURE) }
    private val throwAssertionError: FlickerTestData.() -> Unit = {
        throw AssertionError(Consts.FAILURE)
    }

    @Before
    fun setup() {
        executed = false
    }

    @Test
    fun runSuccessfully() {
        val rule = createRule(listOf(runTransition))
        rule.apply(base = null, description = Consts.description(this)).evaluate()
        Truth.assertWithMessage("Transition executed").that(executed).isTrue()
    }

    @Test
    fun setTransitionStartAndEndTime() {
        val writer = newTestResultWriter()
        val rule = createRule(listOf(runTransition), writer)
        rule.apply(base = null, description = Consts.description(this)).evaluate()
        val result = writer.write()
        TestUtils.validateTransitionTime(result)
    }

    @Test
    fun throwsTransitionFailure() {
        val failure =
            assertThrows<IllegalStateException> {
                val rule = createRule(listOf(throwTransitionError))
                rule.apply(base = null, description = Consts.description(this)).evaluate()
            }
        assertExceptionMessage(failure, Consts.FAILURE)
        Truth.assertWithMessage("Transition executed").that(executed).isFalse()
    }

    @Test
    fun throwsTransitionFailureEmptyTransitions() {
        val failure =
            assertThrows<IllegalArgumentException> {
                val rule = createRule(listOf())
                rule.apply(base = null, description = Consts.description(this)).evaluate()
            }
        assertExceptionMessage(failure, EMPTY_TRANSITIONS_ERROR)
        Truth.assertWithMessage("Transition executed").that(executed).isFalse()
    }

    @Test
    fun throwsAssertionFailure() {
        val failure =
            assertThrows<AssertionError> {
                val rule = createRule(listOf(throwAssertionError))
                rule.apply(base = null, description = Consts.description(this)).evaluate()
            }
        Truth.assertWithMessage("Failure").that(failure).hasMessageThat().contains(Consts.FAILURE)
        Truth.assertWithMessage("Transition executed").that(executed).isFalse()
    }

    @Test
    fun createsValidTags() {
        val writer = newTestResultWriter()
        val rule = createRule(listOf(runCreateValidTags), writer)
        rule.apply(base = null, description = Consts.description(this)).evaluate()
        val result = writer.write()
        val reader = ResultReader(result, TRACE_CONFIG_REQUIRE_CHANGES)
        val wmStateValidTag1 =
            reader.readWmState(VALID_TAG_1) ?: error("Couldn't parse WM state for $VALID_TAG_1")
        val wmStateValidTag2 =
            reader.readWmState(VALID_TAG_2) ?: error("Couldn't parse WM state for $VALID_TAG_2")
        val layerStateValidTag1 =
            reader.readLayersDump(VALID_TAG_1) ?: error("Couldn't parse SF state for $VALID_TAG_1")
        val layerStateValidTag2 =
            reader.readLayersDump(VALID_TAG_2) ?: error("Couldn't parse SF state for $VALID_TAG_2")

        Truth.assertWithMessage("File count").that(reader.countFiles()).isEqualTo(4)
        Truth.assertWithMessage("WM State - $VALID_TAG_1")
            .that(wmStateValidTag1.entries)
            .isNotEmpty()
        Truth.assertWithMessage("WM State - $VALID_TAG_2")
            .that(wmStateValidTag2.entries)
            .isNotEmpty()
        Truth.assertWithMessage("SF State - $VALID_TAG_1")
            .that(layerStateValidTag1.entries)
            .isNotEmpty()
        Truth.assertWithMessage("SF State - $VALID_TAG_2")
            .that(layerStateValidTag2.entries)
            .isNotEmpty()
    }

    @Test
    fun throwErrorCreateInvalidTagWithSpace() {
        val writer = newTestResultWriter()
        val failure =
            assertThrows<IllegalArgumentException> {
                val rule = createRule(listOf(runInvalidTagSpace), writer)
                rule.apply(base = null, description = Consts.description(this)).evaluate()
            }
        assertExceptionMessage(failure, INVALID_TAG_SPACE)
    }

    @Test
    fun throwErrorCreateInvalidTagDuplicate() {
        val writer = newTestResultWriter()
        val failure =
            assertThrows<IllegalArgumentException> {
                val rule = createRule(listOf(runCreateValidTags, runCreateValidTags), writer)
                rule.apply(base = null, description = Consts.description(this)).evaluate()
            }
        assertExceptionMessage(failure, VALID_TAG_1)
    }

    @Test
    fun throwErrorCreateInvalidTagWithUnderscore() {
        val writer = newTestResultWriter()
        val failure =
            assertThrows<IllegalArgumentException> {
                val rule = createRule(listOf(runInvalidTagUnderscore), writer)
                rule.apply(base = null, description = Consts.description(this)).evaluate()
            }
        assertExceptionMessage(failure, INVALID_TAG_UNDERSCORE)
    }

    companion object {
        private const val VALID_TAG_1 = "ValidTag1"
        private const val VALID_TAG_2 = "Valid_Tag2"
        private const val INVALID_TAG_SPACE = "Invalid Tag"
        private const val INVALID_TAG_UNDERSCORE = "Invalid__Tag"

        private fun createRule(
            commands: List<FlickerTestData.() -> Unit>,
            writer: ResultWriter = newTestResultWriter()
        ): TransitionExecutionRule {
            val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()
            val mockedFlicker = Mockito.mock(AbstractFlickerTestData::class.java)
            val mockedMonitor = Mockito.mock(TraceMonitor::class.java)
            Mockito.`when`(mockedFlicker.traceMonitors).thenReturn(listOf(mockedMonitor))
            return TransitionExecutionRule(
                mockedFlicker,
                writer,
                TEST_SCENARIO,
                instrumentation,
                commands,
                WindowManagerStateHelper()
            )
        }

        @ClassRule @JvmField val ENV_CLEANUP = CleanFlickerEnvironmentRule()
    }
}
