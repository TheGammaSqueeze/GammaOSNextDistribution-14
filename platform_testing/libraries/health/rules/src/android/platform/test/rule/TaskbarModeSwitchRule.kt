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
package android.platform.test.rule

import com.android.launcher3.tapl.LauncherInstrumentation
import org.junit.runner.Description

/**
 * Makes each test of the class that uses this rule execute in one of [TaskbarMode.TRANSIENT] or
 * [TaskbarMode.PERSISTENT].
 */
class TaskbarModeSwitchRule(private val taskbarMode: TaskbarMode) : TestWatcher() {

    private val launcher = LauncherInstrumentation()
    private val wasTransientTaskbar = launcher.isTransientTaskbar

    override fun starting(description: Description?) {
        launcher.enableDebugTracing()
        setAndAssertTaskbarMode(isTransient = taskbarMode == TaskbarMode.TRANSIENT)
    }

    override fun finished(description: Description?) {
        launcher.disableDebugTracing()
        setAndAssertTaskbarMode(isTransient = wasTransientTaskbar)
    }

    private fun setAndAssertTaskbarMode(isTransient: Boolean) {
        launcher.enableTransientTaskbar(isTransient)
        launcher.recreateTaskbar()
        val actualIsTransient = launcher.isTransientTaskbar
        assert(actualIsTransient == isTransient) {
            "Taskbar mode did not get set properly." +
                "\n\tExpected isTransient= $isTransient" +
                "\n\tActual isTransient= $actualIsTransient"
        }
    }

    /** Which version to set Taskbar in for the tests. */
    enum class TaskbarMode {
        TRANSIENT,
        PERSISTENT,
    }
}
