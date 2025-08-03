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

import android.tools.flicker.datastore.CachedResultReader
import android.tools.flicker.legacy.LegacyFlickerTest
import android.tools.io.RunStatus
import android.tools.traces.TRACE_CONFIG_REQUIRE_CHANGES
import android.tools.utils.CleanFlickerEnvironmentRule
import android.tools.utils.TEST_SCENARIO
import com.google.common.truth.Truth
import java.io.File
import org.junit.Before
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test

class TransitionErrorTest {
    private var assertionExecuted = false
    private val testParam = LegacyFlickerTest().also { it.initialize(TEST_SCENARIO.testClass) }

    @Before
    fun setup() {
        assertionExecuted = false
    }

    @Test
    fun failsToExecuteTransition() {
        val reader =
            android.tools.flicker.datastore.CachedResultReader(
                TEST_SCENARIO,
                TRACE_CONFIG_REQUIRE_CHANGES
            )
        Truth.assertWithMessage("Run status").that(reader.runStatus).isEqualTo(RunStatus.RUN_FAILED)
        assertArtifactExists()
    }

    @Test
    fun assertThrowsTransitionError() {
        val results =
            (0..10).map {
                runCatching {
                    testParam.assertLayers {
                        assertionExecuted = true
                        error(WRONG_EXCEPTION)
                    }
                }
            }

        Truth.assertWithMessage("Executed").that(assertionExecuted).isFalse()
        results.forEach { result ->
            Truth.assertWithMessage("Expected exception").that(result.isFailure).isTrue()
            Truth.assertWithMessage("Expected exception")
                .that(result.exceptionOrNull())
                .hasMessageThat()
                .contains(Utils.FAILURE)
        }
        val reader =
            android.tools.flicker.datastore.CachedResultReader(
                TEST_SCENARIO,
                TRACE_CONFIG_REQUIRE_CHANGES
            )
        Truth.assertWithMessage("Run status").that(reader.runStatus).isEqualTo(RunStatus.RUN_FAILED)
        assertArtifactExists()
    }

    private fun assertArtifactExists() {
        val reader =
            android.tools.flicker.datastore.CachedResultReader(
                TEST_SCENARIO,
                TRACE_CONFIG_REQUIRE_CHANGES
            )
        val file = File(reader.artifactPath)
        Truth.assertWithMessage("Files exist").that(file.exists()).isTrue()
    }

    companion object {
        private const val WRONG_EXCEPTION = "Wrong exception"

        @BeforeClass @JvmStatic fun runTransition() = Utils.runTransition { error(Utils.FAILURE) }

        @ClassRule @JvmField val ENV_CLEANUP = CleanFlickerEnvironmentRule()
    }
}
