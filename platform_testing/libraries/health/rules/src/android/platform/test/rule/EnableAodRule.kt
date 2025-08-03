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

import android.content.ContentResolver
import android.hardware.display.AmbientDisplayConfiguration
import android.provider.Settings
import com.google.common.truth.Truth.assertThat
import org.junit.Assume
import org.junit.runner.Description

/** This rule enables/disables always-on display. */
class EnableAodRule : TestWatcher() {
    private var wasAodEnabled = false

    override fun starting(description: Description) {
        super.starting(description)
        Assume.assumeTrue(
            "This test requires AoD, but the current device doesn't support it.",
            isAodSupported()
        )
        val contentResolver: ContentResolver = context.contentResolver
        wasAodEnabled =
            Settings.Secure.getInt(contentResolver, Settings.Secure.DOZE_ALWAYS_ON, 0) == 1

        if (!wasAodEnabled) {
            assertThat(Settings.Secure.putInt(contentResolver, Settings.Secure.DOZE_ALWAYS_ON, 1))
                .isTrue()
        }
    }

    override fun finished(description: Description) {
        super.finished(description)
        if (wasAodEnabled) {
            val contentResolver: ContentResolver = context.contentResolver
            assertThat(Settings.Secure.putInt(contentResolver, Settings.Secure.DOZE_ALWAYS_ON, 1))
                .isTrue()
        }
    }

    private fun isAodSupported(): Boolean {
        val config = AmbientDisplayConfiguration(context)
        return config.alwaysOnAvailable() || uiDevice.productName.startsWith("cf_x86")
    }
}
