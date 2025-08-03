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

package android.tools.traces.wm

import android.tools.withCache

/** {@inheritDoc} */
class ConfigurationContainerImpl
private constructor(
    override val overrideConfiguration: Configuration?,
    override val fullConfiguration: Configuration?,
    override val mergedOverrideConfiguration: Configuration?
) : ConfigurationContainer {
    override val windowingMode: Int
        get() = fullConfiguration?.windowConfiguration?.windowingMode ?: 0

    override val activityType: Int
        get() = fullConfiguration?.windowConfiguration?.activityType ?: 0

    override val isEmpty: Boolean
        get() =
            (overrideConfiguration?.isEmpty
                ?: true) &&
                (fullConfiguration?.isEmpty ?: true) &&
                (mergedOverrideConfiguration?.isEmpty ?: true)

    override fun isWindowingModeCompatible(requestedWindowingMode: Int): Boolean {
        return when (requestedWindowingMode) {
            WindowingMode.WINDOWING_MODE_UNDEFINED.value -> true
            else -> windowingMode == requestedWindowingMode
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ConfigurationContainerImpl) return false

        if (overrideConfiguration != other.overrideConfiguration) return false
        if (fullConfiguration != other.fullConfiguration) return false
        if (mergedOverrideConfiguration != other.mergedOverrideConfiguration) return false

        return true
    }

    override fun hashCode(): Int {
        var result = overrideConfiguration?.hashCode() ?: 0
        result = 31 * result + (fullConfiguration?.hashCode() ?: 0)
        result = 31 * result + (mergedOverrideConfiguration?.hashCode() ?: 0)
        return result
    }

    companion object {
        val EMPTY: ConfigurationContainerImpl
            get() = withCache { ConfigurationContainerImpl(null, null, null) }

        fun from(
            overrideConfiguration: Configuration?,
            fullConfiguration: Configuration?,
            mergedOverrideConfiguration: Configuration?
        ): ConfigurationContainerImpl = withCache {
            ConfigurationContainerImpl(
                overrideConfiguration,
                fullConfiguration,
                mergedOverrideConfiguration
            )
        }
    }
}
