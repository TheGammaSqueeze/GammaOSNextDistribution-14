/*
 * Copyright 2024 The Android Open Source Project
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

package com.android.cts.input

import android.content.Context
import android.hardware.input.InputManager
import android.hardware.input.InputSettings
import android.provider.Settings
import androidx.test.platform.app.InstrumentationRegistry
import com.android.compatibility.common.util.PollingCheck
import com.android.compatibility.common.util.SettingsStateManager
import com.android.compatibility.common.util.StateChangerRule
import com.android.compatibility.common.util.UserSettings

class DefaultPointerSpeedRule : StateChangerRule<String?>(
    PointerSpeedSettingsStateManager(
    InstrumentationRegistry
            .getInstrumentation()
            .targetContext
    ),
    InputSettings.DEFAULT_POINTER_SPEED.toString()
) {
    private class PointerSpeedSettingsStateManager(context: Context) :
        SettingsStateManager(
            context,
            UserSettings.Namespace.SYSTEM,
            Settings.System.POINTER_SPEED
        ) {
        private val mInputManager: InputManager

        init {
            mInputManager = context.getSystemService(InputManager::class.java)!!
        }

        override fun set(value: String?) {
            super.set(value)
            val speed = value!!.toInt()
            // This doesn't ensure that the input reader is configured yet with this mouse
            // pointer speed. So, this rule should be used only in tests which add input device(s),
            // as that would ensure that this configuration change is complete by the time a device
            // gets added.
            PollingCheck.waitFor { mInputManager.mousePointerSpeed == speed }
        }
    }
}
