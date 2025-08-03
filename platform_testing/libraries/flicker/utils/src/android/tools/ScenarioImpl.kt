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

/**
 * Legacy flicker test scenario
 *
 * @param testClass
 * @param startRotation Initial screen rotation
 * @param endRotation Final screen rotation
 * @param navBarMode Navigation mode, such as 3 button or gestural.
 * @param config Additional configurations
 *
 * Defaults to [startRotation]
 */
class ScenarioImpl
internal constructor(
    val testClass: String,
    override val startRotation: Rotation,
    override val endRotation: Rotation,
    override val navBarMode: NavBar,
    config: Map<String, Any?>,
    override val description: String
) : Scenario {
    private val _extraConfig = config.toMutableMap()

    val extraConfig: Map<String, Any?>
        get() = _extraConfig

    override val isEmpty = testClass.isEmpty()

    override val key = if (isEmpty) "empty" else "${testClass}_$description"

    /** If the initial screen rotation is 90 (landscape) or 180 (seascape) degrees */
    val isLandscapeOrSeascapeAtStart: Boolean =
        startRotation == Rotation.ROTATION_90 || startRotation == Rotation.ROTATION_270

    val isGesturalNavigation = navBarMode == NavBar.MODE_GESTURAL

    val isTablet: Boolean
        get() =
            extraConfig[IS_TABLET] as Boolean?
                ?: error("$IS_TABLET property not initialized. Use [setIsTablet] to initialize ")

    fun setIsTablet(isTablet: Boolean) {
        _extraConfig[IS_TABLET] = isTablet
    }

    override fun <T> getConfigValue(key: String): T? = extraConfig[key] as T?

    override fun toString(): String = key

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ScenarioImpl) return false

        if (testClass != other.testClass) return false
        if (startRotation != other.startRotation) return false
        if (endRotation != other.endRotation) return false
        if (navBarMode != other.navBarMode) return false
        if (description != other.description) return false
        if (extraConfig != other.extraConfig) return false

        return true
    }

    override fun hashCode(): Int {
        var result = testClass.hashCode()
        result = 31 * result + startRotation.hashCode()
        result = 31 * result + endRotation.hashCode()
        result = 31 * result + navBarMode.hashCode()
        result = 31 * result + description.hashCode()
        result = 31 * result + extraConfig.hashCode()
        return result
    }

    companion object {
        internal const val IS_TABLET = "isTablet"
    }
}
