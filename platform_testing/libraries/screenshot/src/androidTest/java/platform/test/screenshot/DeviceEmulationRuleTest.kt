/*
 * Copyright 2022 The Android Open Source Project
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

package platform.test.screenshot

import android.app.Activity
import android.content.Context
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeviceEmulationRuleTest {
    private val isSystemUsingDarkTheme =
        InstrumentationRegistry.getInstrumentation()
            .targetContext
            .resources
            .configuration
            .isNightModeActive
    private val spec =
        DeviceEmulationSpec(
            display =
                DisplaySpec(
                    name = "test_display",
                    width = 400,
                    height = 500,
                    densityDpi = 90,
                ),

            // Toggle the theme.
            isDarkTheme = !isSystemUsingDarkTheme,
            isLandscape = true,
        )

    @get:Rule val emulationRule = DeviceEmulationRule(spec)

    // We start an activity because that's the only way to reliably check that the *application*
    // theme was changed.
    @get:Rule val activityRule = ActivityScenarioRule(Activity::class.java)

    @Test
    fun testDisplayIsEmulated() {
        activityRule.scenario.onActivity { activity ->
            val wm = activity.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val metrics = DisplayMetrics()
            wm.defaultDisplay.getRealMetrics(metrics)

            // We are in landscape, so the width and height are inverted.
            assertThat(metrics.densityDpi).isEqualTo(90)
            assertThat(metrics.widthPixels).isEqualTo(500)
            assertThat(metrics.heightPixels).isEqualTo(400)

            // Check that the dark/light mode was toggled.
            assertThat(activity.resources.configuration.isNightModeActive)
                .isEqualTo(!isSystemUsingDarkTheme)
        }
    }
}
