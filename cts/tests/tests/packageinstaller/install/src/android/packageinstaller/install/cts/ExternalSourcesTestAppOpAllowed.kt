/*
 * Copyright (C) 2020 The Android Open Source Project
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
package android.packageinstaller.install.cts

import android.app.AppOpsManager.MODE_ALLOWED
import android.content.Intent
import android.content.pm.Flags
import android.platform.test.annotations.AppModeFull
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.CheckFlagsRule
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import android.provider.Settings
import androidx.test.filters.MediumTest
import androidx.test.runner.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Until
import com.android.compatibility.common.util.AppOpsUtils
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val INSTALL_CONFIRM_TEXT_ID = "install_confirm_question"
private const val ALERT_DIALOG_TITLE_ID = "android:id/alertTitle"

@RunWith(AndroidJUnit4::class)
@MediumTest
@AppModeFull
class ExternalSourcesTestAppOpAllowed : PackageInstallerTestBase() {
    @JvmField
    @Rule
    val mCheckFlagsRule: CheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    private val packageName = context.packageName

    private fun assertUiObject(errorMessage: String, selector: BySelector) {
        assertNotNull(errorMessage, uiDevice.wait(Until.findObject(selector), TIMEOUT))
    }

    private fun assertInstallAllowed(errorMessage: String) {
        assertUiObject(errorMessage, By.res(PACKAGE_INSTALLER_PACKAGE_NAME,
                INSTALL_CONFIRM_TEXT_ID))
        uiDevice.pressBack()
    }

    private fun allowedSourceTest(startInstallation: () -> Unit) {
        assertTrue("Package $packageName blocked from installing packages after setting app op " +
                "to allowed", pm.canRequestPackageInstalls())

        startInstallation()
        assertInstallAllowed("Install confirmation not shown when app op set to allowed")

        assertTrue("Operation not logged", AppOpsUtils.allowedOperationLogged(packageName,
                APP_OP_STR))
    }

    @Before
    fun verifyAppOpAllowed() {
        assertThat(AppOpsUtils.getOpMode(packageName, APP_OP_STR))
                .isEqualTo(MODE_ALLOWED)
    }

    @Test
    fun allowedSourceTestViaIntent() {
        allowedSourceTest { startInstallationViaIntent() }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_READ_INSTALL_INFO, Flags.FLAG_GET_RESOLVED_APK_PATH)
    fun allowedSourceTestViaSession() {
        allowedSourceTest { startInstallationViaSession() }
    }

    @Test
    fun allowedSourceTest() {
        assertTrue("Package $packageName blocked from installing packages after setting app op " +
                "to allowed", pm.canRequestPackageInstalls())
    }

    @Test
    fun testManageUnknownSourcesExists() {
        val manageUnknownSources = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
        assertNotNull("No activity found for ${manageUnknownSources.action}",
                pm.resolveActivity(manageUnknownSources, 0))
    }
}
