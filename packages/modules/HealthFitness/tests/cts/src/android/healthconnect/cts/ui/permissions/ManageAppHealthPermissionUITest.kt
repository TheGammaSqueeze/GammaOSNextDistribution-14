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

package android.healthconnect.cts.ui.permissions

import android.content.pm.PackageManager
import android.health.connect.HealthPermissions.READ_HEIGHT
import android.health.connect.HealthPermissions.WRITE_BODY_FAT
import android.health.connect.HealthPermissions.WRITE_HEIGHT
import android.healthconnect.cts.lib.ActivityLauncher.launchMainActivity
import android.healthconnect.cts.lib.UiTestUtils.TEST_APP_PACKAGE_NAME
import android.healthconnect.cts.lib.UiTestUtils.clickOnContentDescription
import android.healthconnect.cts.lib.UiTestUtils.clickOnText
import android.healthconnect.cts.lib.UiTestUtils.grantPermissionViaPackageManager
import android.healthconnect.cts.lib.UiTestUtils.navigateBackToHomeScreen
import android.healthconnect.cts.lib.UiTestUtils.revokePermissionViaPackageManager
import android.healthconnect.cts.lib.UiTestUtils.waitDisplayed
import android.healthconnect.cts.ui.HealthConnectBaseTest
import androidx.test.uiautomator.By
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Test

class ManageAppHealthPermissionUITest : HealthConnectBaseTest() {

    @Test
    fun showDeclaredPermissions() {
        context.launchMainActivity {
            navigateToManageAppPermissions()

            waitDisplayed(By.text("Height"))
        }
    }

    @Test
    fun grantPermission_updatesAppPermissions() {
        revokePermissionViaPackageManager(context, TEST_APP_PACKAGE_NAME, WRITE_BODY_FAT)
        context.launchMainActivity {
            navigateToManageAppPermissions()

            clickOnText("Body fat")
            clickOnContentDescription("Navigate up")

            assertPermGrantedForApp(TEST_APP_PACKAGE_NAME, WRITE_BODY_FAT)
        }
    }

    @Test
    fun revokePermission_updatesAppPermissions() {
        grantPermissionViaPackageManager(context, TEST_APP_PACKAGE_NAME, WRITE_BODY_FAT)
        context.launchMainActivity {
            navigateToManageAppPermissions()
            assertPermGrantedForApp(TEST_APP_PACKAGE_NAME, WRITE_BODY_FAT)

            clickOnText("Body fat")
            clickOnContentDescription("Navigate up")

            assertPermNotGrantedForApp(TEST_APP_PACKAGE_NAME, WRITE_BODY_FAT)
        }
    }

    @Test
    fun revokeAllPermissions_revokesAllAppPermissions() {
        grantPermissionViaPackageManager(context, TEST_APP_PACKAGE_NAME, READ_HEIGHT)
        grantPermissionViaPackageManager(context, TEST_APP_PACKAGE_NAME, WRITE_HEIGHT)
        grantPermissionViaPackageManager(context, TEST_APP_PACKAGE_NAME, WRITE_BODY_FAT)

        context.launchMainActivity {
            navigateToManageAppPermissions()
            clickOnText("Allow all")
            waitDisplayed(By.text("Remove all permissions?"))
            clickOnText("Remove all")
            clickOnContentDescription("Navigate up")

            assertPermNotGrantedForApp(TEST_APP_PACKAGE_NAME, READ_HEIGHT)
            assertPermNotGrantedForApp(TEST_APP_PACKAGE_NAME, WRITE_HEIGHT)
        }
    }

    @Test
    fun revokeAllPermissions_allowsUserToDeleteAppData() {
        grantPermissionViaPackageManager(context, TEST_APP_PACKAGE_NAME, READ_HEIGHT)
        grantPermissionViaPackageManager(context, TEST_APP_PACKAGE_NAME, WRITE_HEIGHT)
        grantPermissionViaPackageManager(context, TEST_APP_PACKAGE_NAME, WRITE_BODY_FAT)
        context.launchMainActivity {
            navigateToManageAppPermissions()
            clickOnText("Allow all")
            waitDisplayed(By.text("Remove all permissions?"))
            waitDisplayed(
                By.text("Also delete Health Connect cts test app data from Health Connect"))
        }
    }

    @Throws(Exception::class)
    private fun assertPermNotGrantedForApp(packageName: String, permName: String) {
        assertThat(context.packageManager.checkPermission(permName, packageName))
            .isEqualTo(PackageManager.PERMISSION_DENIED)
    }

    @Throws(Exception::class)
    private fun assertPermGrantedForApp(packageName: String, permName: String) {
        assertThat(context.packageManager.checkPermission(permName, packageName))
            .isEqualTo(PackageManager.PERMISSION_GRANTED)
    }

    private fun navigateToManageAppPermissions() {
        clickOnText("App permissions")
        clickOnText("Health Connect cts test app")
        waitDisplayed(By.text("Health Connect cts test app"))
        waitDisplayed(By.text("Allowed to read"))
    }

    @After
    fun tearDown() {
        grantPermissionViaPackageManager(context, TEST_APP_PACKAGE_NAME, READ_HEIGHT)
        grantPermissionViaPackageManager(context, TEST_APP_PACKAGE_NAME, WRITE_HEIGHT)
        grantPermissionViaPackageManager(context, TEST_APP_PACKAGE_NAME, WRITE_BODY_FAT)
        navigateBackToHomeScreen()
    }
}
