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

package android.tools.flicker.legacy

import android.tools.Rotation
import android.tools.utils.CleanFlickerEnvironmentRule
import com.google.common.truth.Truth
import org.junit.ClassRule
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/**
 * Contains [LegacyFlickerTestFactory] tests.
 *
 * To run this test: `atest FlickerLibTest:FlickerTestFactoryRunnerTest`
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class LegacyFlickerTestFactoryTest {
    @Test
    fun checkBuildTest() {
        val actual = LegacyFlickerTestFactory.nonRotationTests()
        Truth.assertWithMessage("Flicker should create tests for 0 and 90 degrees")
            .that(actual)
            .hasSize(4)
    }

    @Test
    fun checkBuildRotationTest() {
        val actual = LegacyFlickerTestFactory.rotationTests()
        Truth.assertWithMessage("Flicker should create tests for 0 and 90 degrees")
            .that(actual)
            .hasSize(4)
    }

    @Test
    fun checkBuildCustomRotationsTest() {
        val rotations =
            listOf(
                Rotation.ROTATION_0,
                Rotation.ROTATION_90,
                Rotation.ROTATION_180,
                Rotation.ROTATION_270
            )
        val actual = LegacyFlickerTestFactory.rotationTests(supportedRotations = rotations)
        // Should have config for each rotation pair
        Truth.assertWithMessage("Flicker should create tests for 0/90/180/270 degrees")
            .that(actual)
            .hasSize(24)
    }

    companion object {
        @ClassRule @JvmField val ENV_CLEANUP = CleanFlickerEnvironmentRule()
    }
}
