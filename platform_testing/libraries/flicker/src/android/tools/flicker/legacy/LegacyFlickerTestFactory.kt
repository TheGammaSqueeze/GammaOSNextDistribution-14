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

package android.tools.flicker.legacy

import android.tools.NavBar
import android.tools.Rotation
import android.tools.ScenarioBuilder
import android.tools.flicker.assertions.FlickerTest

/**
 * Factory for creating JUnit4 compatible tests based on the flicker DSL
 *
 * This class recreates behavior from JUnit5 TestFactory that is not available on JUnit4
 */
object LegacyFlickerTestFactory {
    /**
     * Gets a list of test configurations.
     *
     * Each configuration has only a start orientation.
     */
    @JvmOverloads
    @JvmStatic
    fun nonRotationTests(
        supportedRotations: List<Rotation> = listOf(Rotation.ROTATION_0, Rotation.ROTATION_90),
        supportedNavigationModes: List<NavBar> = listOf(NavBar.MODE_3BUTTON, NavBar.MODE_GESTURAL),
        extraArgs: Map<String, Any> = emptyMap()
    ): List<FlickerTest> {
        return supportedNavigationModes.flatMap { navBarMode ->
            supportedRotations.map { rotation ->
                createFlickerTest(navBarMode, rotation, rotation, extraArgs)
            }
        }
    }

    /**
     * Gets a list of test configurations.
     *
     * Each configuration has a start and end orientation.
     */
    @JvmOverloads
    @JvmStatic
    fun rotationTests(
        supportedRotations: List<Rotation> = listOf(Rotation.ROTATION_0, Rotation.ROTATION_90),
        supportedNavigationModes: List<NavBar> = listOf(NavBar.MODE_3BUTTON, NavBar.MODE_GESTURAL),
        extraArgs: Map<String, Any> = emptyMap()
    ): List<FlickerTest> {
        return supportedNavigationModes.flatMap { navBarMode ->
            supportedRotations
                .flatMap { start -> supportedRotations.map { end -> start to end } }
                .filter { (start, end) -> start != end }
                .map { (start, end) -> createFlickerTest(navBarMode, start, end, extraArgs) }
        }
    }

    private fun createFlickerTest(
        navBarMode: NavBar,
        startRotation: Rotation,
        endRotation: Rotation,
        extraArgs: Map<String, Any>
    ) =
        LegacyFlickerTest(
            ScenarioBuilder()
                .withStartRotation(startRotation)
                .withEndRotation(endRotation)
                .withNavBarMode(navBarMode)
                .withExtraConfigs(extraArgs)
        )
}
