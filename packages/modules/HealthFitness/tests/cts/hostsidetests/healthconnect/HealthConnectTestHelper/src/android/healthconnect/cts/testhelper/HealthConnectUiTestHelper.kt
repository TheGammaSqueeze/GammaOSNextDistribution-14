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

package android.healthconnect.cts.testhelper

import android.content.Context
import android.content.pm.PackageManager
import android.health.connect.HealthConnectManager
import android.healthconnect.cts.lib.ActivityLauncher.launchMainActivity
import android.healthconnect.cts.lib.UiTestUtils
import android.healthconnect.cts.lib.UiTestUtils.clickOnText
import androidx.test.core.app.ApplicationProvider
import androidx.test.uiautomator.By
import com.android.compatibility.common.util.NonApiTest
import com.android.compatibility.common.util.SystemUtil
import org.junit.After
import org.junit.Assume
import org.junit.Before
import org.junit.Test

/**
 * These tests are run by statsdatom/healthconnect to log atoms by triggering Health Connect APIs.
 *
 * <p>They only trigger the APIs, but don't test anything themselves.
 */
@NonApiTest(exemptionReasons = [], justification = "METRIC")
class HealthConnectUiTestHelper {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val mHealthConnectManager: HealthConnectManager? =
        context.getSystemService<HealthConnectManager>(HealthConnectManager::class.java)
    private val TEST_APP_PACKAGE_NAME = "android.healthconnect.cts.testhelper"

    @Before
    fun setUpClass() {
        Assume.assumeTrue(isHardwareSupported())
        // Collapse notifications
        SystemUtil.runShellCommandOrThrow("cmd statusbar collapse")

        unlockDevice()
    }

    @Before
    fun before() {
        TestHelperUtils.deleteAllRecordsAddedByTestApp(mHealthConnectManager)
    }

    @After
    fun after() {
        TestHelperUtils.deleteAllRecordsAddedByTestApp(mHealthConnectManager)
    }

    private fun unlockDevice() {
        SystemUtil.runShellCommandOrThrow("input keyevent KEYCODE_WAKEUP")
        if ("false".equals(SystemUtil.runShellCommandOrThrow("cmd lock_settings get-disabled"))) {
            // Unlock screen only when it's lock settings enabled to prevent showing "wallpaper
            // picker" which may cover another UI elements on freeform window configuration.
            SystemUtil.runShellCommandOrThrow("input keyevent 82")
        }
        SystemUtil.runShellCommandOrThrow("wm dismiss-keyguard")
    }

    private fun isHardwareSupported(): Boolean {
        // These UI tests are not optimised for Watches, TVs, Auto;
        // IoT devices do not have a UI to run these UI tests
        val pm: PackageManager = context.packageManager
        return (!pm.hasSystemFeature(PackageManager.FEATURE_EMBEDDED) &&
            !pm.hasSystemFeature(PackageManager.FEATURE_WATCH) &&
            !pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK) &&
            !pm.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE))
    }

    @Test
    fun openHomeFragment() {
        TestHelperUtils.insertRecords(
            listOf(
                TestHelperUtils.getBloodPressureRecord(),
                TestHelperUtils.getHeartRateRecord(),
                TestHelperUtils.getStepsRecord()),
            mHealthConnectManager)
        context.launchMainActivity {
            UiTestUtils.skipOnboardingIfAppears()
            UiTestUtils.waitDisplayed(By.text("Data and access"))
            UiTestUtils.clickOnText("Data and access")

            UiTestUtils.waitDisplayed(By.text("Browse data"))
            UiTestUtils.waitDisplayed(By.text("Manage data"))

            UiTestUtils.waitDisplayed(By.text("Delete all data"))
        }
    }
}
