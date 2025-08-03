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

package android.tools.flicker.rules

import android.app.ActivityTaskManager
import android.app.WindowConfiguration
import android.tools.FLICKER_TAG
import android.tools.Logger
import android.tools.traces.parsers.WindowManagerStateHelper
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/** Test rule to ensure no tasks as running before executing the test */
class RemoveAllTasksButHomeRule() : TestWatcher() {
    override fun starting(description: Description?) {
        Logger.withTracing("$RemoveAllTasksButHomeRule:starting") {
            Logger.v(FLICKER_TAG, "Removing all tasks (except home)")
            removeAllTasksButHome()
            WindowManagerStateHelper()
                .StateSyncBuilder()
                .withAppTransitionIdle()
                .withHomeActivityVisible()
                .waitForAndVerify()
        }
    }

    companion object {
        @JvmStatic
        fun removeAllTasksButHome() {
            val atm = ActivityTaskManager.getService()
            atm.removeRootTasksWithActivityTypes(ALL_ACTIVITY_TYPE_BUT_HOME)
        }

        private val ALL_ACTIVITY_TYPE_BUT_HOME =
            intArrayOf(
                WindowConfiguration.ACTIVITY_TYPE_STANDARD,
                WindowConfiguration.ACTIVITY_TYPE_ASSISTANT,
                WindowConfiguration.ACTIVITY_TYPE_RECENTS,
                WindowConfiguration.ACTIVITY_TYPE_UNDEFINED
            )
    }
}
