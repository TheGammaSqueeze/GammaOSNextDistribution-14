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

package android.healthconnect.cts.ui

import android.healthconnect.cts.lib.ActivityLauncher.launchMainActivity
import android.healthconnect.cts.lib.UiTestUtils
import android.healthconnect.cts.lib.UiTestUtils.clickOnText
import android.healthconnect.cts.utils.TestUtils
import androidx.test.uiautomator.By
import org.junit.Test

/** CTS test for HealthConnect Manage data screen. */
class ManageDataFragmentTest : HealthConnectBaseTest() {

    companion object {
        private const val THREE_MONTHS = 3 * 30
    }

    @Test
    fun manageDataFragment_never_showsAutoDeleteOption() {
        TestUtils.setAutoDeletePeriod(0)

        context.launchMainActivity {
            clickOnText("Manage data")
            UiTestUtils.waitDisplayed(By.text("Auto-delete"))
            UiTestUtils.waitDisplayed(By.text("Off"))
        }
    }

    @Test
    fun manageDataFragment_3months_showsAutoDeleteOption() {
        TestUtils.setAutoDeletePeriod(THREE_MONTHS)

        context.launchMainActivity {
            clickOnText("Manage data")
            UiTestUtils.waitDisplayed(By.text("Auto-delete"))
            UiTestUtils.waitDisplayed(By.text("After 3 months"))
        }
    }
}
