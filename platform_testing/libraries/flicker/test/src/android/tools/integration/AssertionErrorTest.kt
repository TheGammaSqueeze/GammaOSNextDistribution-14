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
import android.tools.flicker.subject.exceptions.SimpleFlickerAssertionError
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

/**
 * Integration tests to ensure assertions fail correctly
 *
 * To run this test: `atest FlickerLibTest:AssertionErrorTest`
 */
class AssertionErrorTest {
    private var assertionExecuted = false
    private val testParam = LegacyFlickerTest().also { it.initialize(TEST_SCENARIO.testClass) }

    @Before
    fun setup() {
        assertionExecuted = false
    }

    @Test
    fun executesTransition() {
        Truth.assertWithMessage("Transition executed").that(transitionExecuted).isTrue()
        assertArtifactExists()
    }

    @Test
    fun assertThrowsAssertionError() {
        val result = runCatching {
            testParam.assertLayers {
                assertionExecuted = true
                throw SimpleFlickerAssertionError(Utils.FAILURE)
            }
        }

        Truth.assertWithMessage("Executed").that(assertionExecuted).isTrue()
        Truth.assertWithMessage("Expected exception").that(result.isSuccess).isFalse()
        Truth.assertWithMessage("Expected exception")
            .that(result.exceptionOrNull())
            .hasMessageThat()
            .contains(Utils.FAILURE)
        val reader =
            android.tools.flicker.datastore.CachedResultReader(
                TEST_SCENARIO,
                TRACE_CONFIG_REQUIRE_CHANGES
            )
        Truth.assertWithMessage("Run status")
            .that(reader.runStatus)
            .isEqualTo(RunStatus.ASSERTION_FAILED)
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
        private var transitionExecuted = false
        @BeforeClass
        @JvmStatic
        fun runTransition() = Utils.runTransition { transitionExecuted = true }

        @ClassRule @JvmField val ENV_CLEANUP = CleanFlickerEnvironmentRule()
    }
}
