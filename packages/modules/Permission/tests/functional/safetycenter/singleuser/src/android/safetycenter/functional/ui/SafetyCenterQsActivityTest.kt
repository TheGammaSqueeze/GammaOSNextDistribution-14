/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.safetycenter.functional.ui

import android.content.Context
import android.hardware.SensorPrivacyManager.Sensors.CAMERA
import android.hardware.SensorPrivacyManager.Sensors.MICROPHONE
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import com.android.compatibility.common.util.DisableAnimationRule
import com.android.compatibility.common.util.FreezeRotationRule
import com.android.safetycenter.testing.EnableSensorRule
import com.android.safetycenter.testing.SafetyCenterActivityLauncher.launchSafetyCenterQsActivity
import com.android.safetycenter.testing.SafetyCenterTestConfigs
import com.android.safetycenter.testing.SafetyCenterTestHelper
import com.android.safetycenter.testing.SafetyCenterTestRule
import com.android.safetycenter.testing.SupportsSafetyCenterRule
import com.android.safetycenter.testing.UiTestHelper.waitAllTextDisplayed
import com.android.safetycenter.testing.UiTestHelper.waitDisplayed
import com.android.safetycenter.testing.UiTestHelper.waitPageTitleDisplayed
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Functional tests for the Safety Center Quick Settings Activity. */
@RunWith(AndroidJUnit4::class)
class SafetyCenterQsActivityTest {

    private val context: Context = getApplicationContext()
    private val safetyCenterTestHelper = SafetyCenterTestHelper(context)
    private val safetyCenterTestConfigs = SafetyCenterTestConfigs(context)

    @get:Rule(order = 1) val supportsSafetyCenterRule = SupportsSafetyCenterRule(context)
    @get:Rule(order = 2) val enableCameraRule = EnableSensorRule(context, CAMERA)
    @get:Rule(order = 3) val enableMicrophoneRule = EnableSensorRule(context, MICROPHONE)
    @get:Rule(order = 4) val safetyCenterTestRule = SafetyCenterTestRule(safetyCenterTestHelper)
    @get:Rule(order = 5) val disableAnimationRule = DisableAnimationRule()
    @get:Rule(order = 6) val freezeRotationRule = FreezeRotationRule()

    @Before
    fun setTestConfigBeforeTest() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
    }

    @Test
    fun launchActivity_fromQuickSettings_hasContentDescriptions() {
        context.launchSafetyCenterQsActivity {
            waitPageTitleDisplayed("Security and privacy quick settings")
            waitAllTextDisplayed("Your privacy controls")
            waitDisplayed(By.desc("Close"))

            // Verify privacy controls descriptions
            waitDisplayed(By.desc("Switch. Camera access. Available"))
            waitDisplayed(By.desc("Switch. Mic access. Available"))
        }
    }

    @Test
    fun launchActivity_togglePrivacyControls_hasUpdatedDescriptions() {
        context.launchSafetyCenterQsActivity {
            // Toggle privacy controls
            waitDisplayed(By.desc("Switch. Camera access. Available")) { it.click() }
            waitDisplayed(By.desc("Switch. Mic access. Available")) { it.click() }

            // Verify updated state of privacy controls
            waitDisplayed(By.desc("Switch. Camera access. Blocked"))
            waitDisplayed(By.desc("Switch. Mic access. Blocked"))
        }
    }
}
