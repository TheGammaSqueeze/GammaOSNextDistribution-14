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

package android.tools.flicker.config

import android.app.Instrumentation
import android.platform.test.rule.NavigationModeRule
import android.tools.NavBar
import android.tools.ScenarioBuilder
import android.tools.device.apphelpers.BrowserAppHelper
import android.tools.device.apphelpers.CameraAppHelper
import android.tools.flicker.Utils
import android.tools.flicker.config.gesturenav.Quickswitch
import android.tools.traces.getDefaultFlickerOutputDir
import android.tools.traces.parsers.WindowManagerStateHelper
import android.tools.utils.CleanFlickerEnvironmentRule
import android.view.Display
import androidx.test.platform.app.InstrumentationRegistry
import com.android.launcher3.tapl.LauncherInstrumentation
import com.android.launcher3.tapl.LauncherInstrumentation.NavigationModel
import com.google.common.truth.Truth
import org.junit.Assume
import org.junit.ClassRule
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class QuickswitchTest {
    val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()
    val tapl = LauncherInstrumentation()
    val wmHelper = WindowManagerStateHelper(instrumentation)
    val browser = BrowserAppHelper(instrumentation)
    val camera = CameraAppHelper(instrumentation)

    @Test
    fun canExtractQuickswitch() {
        Assume.assumeTrue(tapl.getNavigationModel() == NavigationModel.ZERO_BUTTON)

        browser.launchViaIntent(wmHelper)
        camera.launchViaIntent(wmHelper)

        val scenario = ScenarioBuilder().forClass("QuickSwitch").build()
        val reader =
            Utils.captureTrace(scenario, getDefaultFlickerOutputDir()) {
                tapl.launchedAppState.quickSwitchToPreviousApp()

                wmHelper
                    .StateSyncBuilder()
                    .withAppTransitionIdle(Display.DEFAULT_DISPLAY)
                    .withNavOrTaskBarVisible()
                    .withStatusBarVisible()
                    .waitForAndVerify()
            }

        browser.exit()
        camera.exit()

        val quickSwitchExtractor = Quickswitch.extractor
        val slices = quickSwitchExtractor.extract(reader)

        Truth.assertThat(slices).hasSize(1)
    }

    companion object {
        @ClassRule @JvmField val NAV_MODE_RULE = NavigationModeRule(NavBar.MODE_GESTURAL.value)
        @ClassRule @JvmField val ENV_CLEANUP = CleanFlickerEnvironmentRule()
    }
}
