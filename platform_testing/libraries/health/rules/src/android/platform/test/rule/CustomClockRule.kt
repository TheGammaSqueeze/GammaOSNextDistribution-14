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

package android.platform.test.rule

import android.platform.uiautomator_helpers.DeviceHelpers.context
import android.provider.Settings

class CustomClockRule(private val customClock: CustomClock) :
    SecureSettingRule<String>(
        Settings.Secure.LOCK_SCREEN_CUSTOM_CLOCK_FACE,
        initialValue = """{"clockId":"${customClock.name}"}"""
    ) {

    fun isLockscreenCustomClockSet(): Boolean {
        val contentResolver = context.contentResolver
        val customClockFace =
            Settings.Secure.getString(
                contentResolver,
                Settings.Secure.LOCK_SCREEN_CUSTOM_CLOCK_FACE
            )
        return customClockFace == """{"clockId":"${customClock.name}"}"""
    }

    /** Custom clock faces on the lockscreen */
    enum class CustomClock {
        ANALOG_CLOCK_BIGNUM,
        DIGITAL_CLOCK_WEATHER,
        DIGITAL_CLOCK_METRO,
        DIGITAL_CLOCK_HANDWRITTEN,
        DIGITAL_CLOCK_GROWTH,
        DIGITAL_CLOCK_NUMBEROVERLAP,
        DIGITAL_CLOCK_CALLIGRAPHY,
        DIGITAL_CLOCK_INFLATE,
        DIGITAL_CLOCK_FLEX,
    }
}
