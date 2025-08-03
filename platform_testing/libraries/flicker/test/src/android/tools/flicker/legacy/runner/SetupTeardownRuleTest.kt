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
import android.tools.flicker.legacy.AbstractFlickerTestData
import android.tools.flicker.legacy.FlickerTestData
import android.tools.traces.io.ResultWriter
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
import org.mockito.Mockito

/** Tests for [SetupTeardownRule] */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class SetupTeardownRuleTest {
    private var setupExecuted = false
    private var teardownExecuted = false

    private val runSetup: FlickerTestData.() -> Unit = { setupExecuted = true }
    private val runTeardown: FlickerTestData.() -> Unit = { teardownExecuted = true }
    private val throwError: FlickerTestData.() -> Unit = { error(Consts.FAILURE) }

    @Before
    fun setup() {
        setupExecuted = false
        teardownExecuted = false
    }

    @Test
    fun executesSetupTeardown() {
        val rule = createRule(listOf(runSetup), listOf(runTeardown))
        rule.apply(base = null, description = Consts.description(this)).evaluate()
        Truth.assertWithMessage("Setup executed").that(setupExecuted).isTrue()
        Truth.assertWithMessage("Teardown executed").that(teardownExecuted).isTrue()
    }

    @Test
    fun throwsSetupFailureAndExecutesTeardown() {
        val failure =
            assertThrows<IllegalStateException> {
                val rule = createRule(listOf(throwError, runSetup), listOf(runTeardown))
                rule.apply(base = null, description = Consts.description(this)).evaluate()
            }
        Truth.assertWithMessage("Failure").that(failure).hasMessageThat().contains(Consts.FAILURE)
        Truth.assertWithMessage("Setup executed").that(setupExecuted).isFalse()
        Truth.assertWithMessage("Teardown executed").that(teardownExecuted).isTrue()
    }

    @Test
    fun throwsTeardownFailure() {
        val failure =
            assertThrows<IllegalStateException> {
                val rule = createRule(listOf(runSetup), listOf(throwError, runTeardown))
                rule.apply(base = null, description = Consts.description(this)).evaluate()
            }
        Truth.assertWithMessage("Failure").that(failure).hasMessageThat().contains(Consts.FAILURE)
        Truth.assertWithMessage("Setup executed").that(setupExecuted).isTrue()
        Truth.assertWithMessage("Teardown executed").that(teardownExecuted).isFalse()
    }

    companion object {
        private fun createRule(
            setupCommands: List<FlickerTestData.() -> Unit>,
            teardownCommands: List<FlickerTestData.() -> Unit>
        ): SetupTeardownRule {
            val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()
            val mockedFlicker = Mockito.mock(AbstractFlickerTestData::class.java)
            return SetupTeardownRule(
                mockedFlicker,
                ResultWriter(),
                TEST_SCENARIO,
                instrumentation,
                setupCommands,
                teardownCommands,
                WindowManagerStateHelper()
            )
        }

        @ClassRule @JvmField val ENV_CLEANUP = CleanFlickerEnvironmentRule()
    }
}
