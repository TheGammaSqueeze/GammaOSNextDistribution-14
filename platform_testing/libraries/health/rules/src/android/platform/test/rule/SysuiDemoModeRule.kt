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

import android.content.Intent
import android.provider.Settings
import org.junit.runner.Description

/** This rule will modify SysUI demo mode flag and revert it after the test. */
class SysuiDemoModeRule() : TestWatcher() {
    private val contentResolver = context.contentResolver
    private val DEMO_MODE_FLAG = "sysui_demo_allowed"
    private val DEMO_MODE_TUNER_FLAG = "sysui_tuner_demo_on"
    private val ENTER_COMMAND = "enter"
    private val DISABLE_COMMAND = "exit"
    private val ENABLE_VALUE = 1
    private var DISABLE_VALUE = 0

    override fun starting(description: Description) {
        if (!Settings.Global.putInt(contentResolver, DEMO_MODE_FLAG, ENABLE_VALUE)) {
            throw RuntimeException("Could not set SysUI demo mode to $ENABLE_VALUE")
        }
        sendDemoModeBroadcast(ENTER_COMMAND)

        if (!Settings.Global.putInt(contentResolver, DEMO_MODE_TUNER_FLAG, ENABLE_VALUE)) {
            throw RuntimeException("Could not turn SysUI demo mode on.")
        }
    }

    override fun finished(description: Description) {
        sendDemoModeBroadcast(DISABLE_COMMAND)

        if (!Settings.Global.putInt(contentResolver, DEMO_MODE_TUNER_FLAG, DISABLE_VALUE)) {
            throw RuntimeException("Could not turn SysUI demo mode off.")
        }

        if (!Settings.Global.putInt(contentResolver, DEMO_MODE_FLAG, DISABLE_VALUE)) {
            throw RuntimeException("Could not disable SysUI demo mode.")
        }
    }

    private fun sendDemoModeBroadcast(command: String) {
        val intent = Intent("com.android.systemui.demo")
        intent.putExtra("command", command)
        context.sendBroadcast(intent)
    }
}
