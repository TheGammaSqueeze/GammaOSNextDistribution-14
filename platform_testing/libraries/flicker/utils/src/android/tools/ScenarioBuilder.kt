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

package android.tools

/** Helper class to create [Scenario]s */
class ScenarioBuilder {
    private var testClass: String = ""
    private var startRotation = DEFAULT_ROTATION
    private var endRotation = DEFAULT_ROTATION
    private var navBarMode = DEFAULT_NAVBAR_MODE
    private var extraConfig = mutableMapOf<String, Any?>()
    private var description = ""

    fun forClass(cls: String) = apply { testClass = cls }

    fun withStartRotation(rotation: Rotation) = apply { startRotation = rotation }

    fun withEndRotation(rotation: Rotation) = apply { endRotation = rotation }

    fun withNavBarMode(mode: NavBar) = apply { navBarMode = mode }

    fun withExtraConfig(key: String, value: Any?) = apply { extraConfig[key] = value }

    fun withExtraConfigs(cfg: Map<String, Any?>) = apply { extraConfig.putAll(cfg) }

    fun withDescriptionOverride(description: String) = apply { this.description = description }

    fun build(): Scenario {
        require(testClass.isNotEmpty()) { "Test class missing" }
        val description =
            description.ifEmpty {
                buildString {
                    append(startRotation.description)
                    if (endRotation != startRotation) {
                        append("_${endRotation.description}")
                    }
                    append("_${navBarMode.description}")
                }
            }
        return ScenarioImpl(
            testClass,
            startRotation,
            endRotation,
            navBarMode,
            extraConfig,
            description
        )
    }

    fun createEmptyScenario(): Scenario =
        ScenarioImpl(
            testClass = "",
            startRotation = DEFAULT_ROTATION,
            endRotation = DEFAULT_ROTATION,
            navBarMode = DEFAULT_NAVBAR_MODE,
            config = emptyMap(),
            description =
                defaultDescription(DEFAULT_ROTATION, DEFAULT_ROTATION, DEFAULT_NAVBAR_MODE)
        )

    companion object {
        val DEFAULT_ROTATION = Rotation.ROTATION_0
        val DEFAULT_NAVBAR_MODE = NavBar.MODE_GESTURAL

        private fun defaultDescription(
            startOrientation: Rotation,
            endOrientation: Rotation,
            navBarMode: NavBar
        ): String = buildString {
            append(startOrientation.description)
            if (endOrientation != startOrientation) {
                append("_${endOrientation.description}")
            }
            append("_${navBarMode.description}")
        }
    }
}
