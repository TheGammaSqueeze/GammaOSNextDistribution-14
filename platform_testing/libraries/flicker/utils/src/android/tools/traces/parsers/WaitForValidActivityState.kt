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

package android.tools.traces.parsers

import android.app.ActivityTaskManager
import android.app.WindowConfiguration
import android.tools.traces.component.IComponentMatcher

data class WaitForValidActivityState(
    @JvmField val activityMatcher: IComponentMatcher?,
    @JvmField val windowIdentifier: String?,
    @JvmField val stackId: Int,
    @JvmField val windowingMode: Int,
    @JvmField val activityType: Int
) {
    constructor(
        activityName: IComponentMatcher
    ) : this(
        activityName,
        windowIdentifier = activityName.toWindowIdentifier(),
        stackId = ActivityTaskManager.INVALID_STACK_ID,
        windowingMode = WindowConfiguration.WINDOWING_MODE_UNDEFINED,
        activityType = WindowConfiguration.ACTIVITY_TYPE_UNDEFINED
    )

    private constructor(
        builder: Builder
    ) : this(
        activityMatcher = builder.activityName,
        windowIdentifier = builder.windowIdentifier,
        stackId = builder.stackId,
        windowingMode = builder.windowingMode,
        activityType = builder.activityType
    )

    override fun toString(): String {
        val sb = StringBuilder("wait:")
        if (activityMatcher != null) {
            sb.append(" activity=").append(activityMatcher.toActivityIdentifier())
        }
        if (activityType != WindowConfiguration.ACTIVITY_TYPE_UNDEFINED) {
            sb.append(" type=").append(activityTypeName(activityType))
        }
        if (windowIdentifier != null) {
            sb.append(" window=").append(windowIdentifier)
        }
        if (windowingMode != WindowConfiguration.WINDOWING_MODE_UNDEFINED) {
            sb.append(" mode=").append(windowingModeName(windowingMode))
        }
        if (stackId != ActivityTaskManager.INVALID_STACK_ID) {
            sb.append(" stack=").append(stackId)
        }
        return sb.toString()
    }

    class Builder constructor(internal var activityName: IComponentMatcher? = null) {
        internal var windowIdentifier: String? = activityName?.toWindowIdentifier()
        internal var stackId = ActivityTaskManager.INVALID_STACK_ID
        internal var windowingMode = WindowConfiguration.WINDOWING_MODE_UNDEFINED
        internal var activityType = WindowConfiguration.ACTIVITY_TYPE_UNDEFINED

        fun setWindowIdentifier(windowIdentifier: String): Builder {
            this.windowIdentifier = windowIdentifier
            return this
        }

        fun setStackId(stackId: Int): Builder {
            this.stackId = stackId
            return this
        }

        fun setWindowingMode(windowingMode: Int): Builder {
            this.windowingMode = windowingMode
            return this
        }

        fun setActivityType(activityType: Int): Builder {
            this.activityType = activityType
            return this
        }

        fun build(): WaitForValidActivityState {
            return WaitForValidActivityState(this)
        }
    }

    companion object {
        @JvmStatic
        fun forWindow(windowName: String): WaitForValidActivityState {
            return Builder().setWindowIdentifier(windowName).build()
        }

        private fun windowingModeName(windowingMode: Int): String {
            return when (windowingMode) {
                WindowConfiguration.WINDOWING_MODE_UNDEFINED -> "UNDEFINED"
                WindowConfiguration.WINDOWING_MODE_FULLSCREEN -> "FULLSCREEN"
                WindowConfiguration.WINDOWING_MODE_PINNED -> "PINNED"
                WindowConfiguration.WINDOWING_MODE_FREEFORM -> "FREEFORM"
                else -> throw IllegalArgumentException("Unknown WINDOWING_MODE_: $windowingMode")
            }
        }

        private fun activityTypeName(activityType: Int): String {
            return when (activityType) {
                WindowConfiguration.ACTIVITY_TYPE_UNDEFINED -> "UNDEFINED"
                WindowConfiguration.ACTIVITY_TYPE_STANDARD -> "STANDARD"
                WindowConfiguration.ACTIVITY_TYPE_HOME -> "HOME"
                WindowConfiguration.ACTIVITY_TYPE_RECENTS -> "RECENTS"
                WindowConfiguration.ACTIVITY_TYPE_ASSISTANT -> "ASSISTANT"
                else -> throw IllegalArgumentException("Unknown ACTIVITY_TYPE_: $activityType")
            }
        }
    }
}
