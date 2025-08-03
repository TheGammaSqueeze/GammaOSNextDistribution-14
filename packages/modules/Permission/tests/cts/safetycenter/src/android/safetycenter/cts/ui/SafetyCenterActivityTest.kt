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

package android.safetycenter.cts.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import com.android.compatibility.common.util.DisableAnimationRule
import com.android.compatibility.common.util.FreezeRotationRule
import com.android.compatibility.common.util.UiAutomatorUtils2.getUiDevice
import com.android.safetycenter.testing.SafetyCenterActivityLauncher.launchSafetyCenterActivity
import com.android.safetycenter.testing.SafetyCenterTestHelper
import com.android.safetycenter.testing.SafetyCenterTestRule
import com.android.safetycenter.testing.SettingsPackage.getSettingsPackageName
import com.android.safetycenter.testing.SupportsSafetyCenterRule
import com.android.safetycenter.testing.UiTestHelper.resetRotation
import com.android.safetycenter.testing.UiTestHelper.waitDisplayed
import com.android.safetycenter.testing.UiTestHelper.waitPageTitleDisplayed
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** CTS tests for the Safety Center Activity. */
@RunWith(AndroidJUnit4::class)
class SafetyCenterActivityTest {

    private val context: Context = getApplicationContext()
    private val safetyCenterTestHelper = SafetyCenterTestHelper(context)

    @get:Rule(order = 1) val supportsSafetyCenterRule = SupportsSafetyCenterRule(context)
    @get:Rule(order = 2) val safetyCenterTestRule = SafetyCenterTestRule(safetyCenterTestHelper)
    @get:Rule(order = 3) val disableAnimationRule = DisableAnimationRule()
    @get:Rule(order = 4) val freezeRotationRule = FreezeRotationRule()

    @After
    fun clearDataAfterTest() {
        getUiDevice().resetRotation()
    }

    @Test
    fun launchActivity_withFlagEnabled_showsSecurityAndPrivacyTitle() {
        context.launchSafetyCenterActivity { waitPageTitleDisplayed("Security & privacy") }
    }

    @Test
    fun launchActivity_withFlagDisabled_opensSettings() {
        safetyCenterTestHelper.setEnabled(false)

        context.launchSafetyCenterActivity {
            waitDisplayed(By.pkg(context.getSettingsPackageName()))
        }
    }
}
