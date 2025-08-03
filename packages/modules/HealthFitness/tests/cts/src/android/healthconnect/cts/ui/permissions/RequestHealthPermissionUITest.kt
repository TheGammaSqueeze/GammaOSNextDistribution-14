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
import android.health.connect.HealthPermissions
import android.healthconnect.cts.lib.ActivityLauncher.launchRequestPermissionActivity
import android.healthconnect.cts.lib.UiTestUtils.TEST_APP_PACKAGE_NAME
import android.healthconnect.cts.lib.UiTestUtils.clickOnText
import android.healthconnect.cts.lib.UiTestUtils.grantPermissionViaPackageManager
import android.healthconnect.cts.lib.UiTestUtils.revokePermissionViaPackageManager
import android.healthconnect.cts.lib.UiTestUtils.waitDisplayed
import android.healthconnect.cts.lib.UiTestUtils.waitNotDisplayed
import android.healthconnect.cts.ui.HealthConnectBaseTest
import androidx.test.uiautomator.By
import com.google.common.truth.Truth
import java.lang.Exception
import org.junit.After
import org.junit.Ignore
import org.junit.Test

class RequestHealthPermissionUITest : HealthConnectBaseTest() {

    @Test
    @Ignore(
        "TODO(b/265789268): Fix flaky cannot find 'Allow “Health Connect cts test app” to read' view")
    fun showsAppName_showsRequestedPermissions() {
        revokePermissionViaPackageManager(
            context, TEST_APP_PACKAGE_NAME, HealthPermissions.READ_HEIGHT)
        revokePermissionViaPackageManager(
            context, TEST_APP_PACKAGE_NAME, HealthPermissions.WRITE_BODY_FAT)
        context.launchRequestPermissionActivity(
            packageName = TEST_APP_PACKAGE_NAME,
            permissions = listOf(HealthPermissions.READ_HEIGHT, HealthPermissions.WRITE_BODY_FAT)) {
                waitDisplayed(By.text("Allow “Health Connect cts test app” to read"))
                waitDisplayed(By.text("Height"))

                waitDisplayed(By.text("Allow “Health Connect cts test app” to write"))
                waitDisplayed(By.text("Body fat"))
            }
    }

    @Test
    fun requestGrantedPermissions_doesNotShowGrantedPermissions() {
        revokePermissionViaPackageManager(
            context, TEST_APP_PACKAGE_NAME, HealthPermissions.WRITE_BODY_FAT)
        grantPermissionViaPackageManager(
            context, TEST_APP_PACKAGE_NAME, HealthPermissions.READ_HEIGHT)

        context.launchRequestPermissionActivity(
            packageName = TEST_APP_PACKAGE_NAME,
            permissions = listOf(HealthPermissions.READ_HEIGHT, HealthPermissions.WRITE_BODY_FAT)) {
                waitNotDisplayed(By.text("Height"))

                waitDisplayed(By.text("Body fat"))

                revokePermissionViaPackageManager(
                    context, TEST_APP_PACKAGE_NAME, HealthPermissions.READ_HEIGHT)
            }
    }

    @Test
    @Ignore(
        "TODO(b/265789268): Fix flaky assertPermGrantedForApp(READ_HEIGHT)=false because Height is not actually clicked")
    fun grantPermission_grantsOnlyRequestedPermission() {
        revokePermissionViaPackageManager(
            context, TEST_APP_PACKAGE_NAME, HealthPermissions.READ_HEIGHT)
        revokePermissionViaPackageManager(
            context, TEST_APP_PACKAGE_NAME, HealthPermissions.WRITE_BODY_FAT)
        context.launchRequestPermissionActivity(
            packageName = TEST_APP_PACKAGE_NAME,
            permissions = listOf(HealthPermissions.READ_HEIGHT, HealthPermissions.WRITE_BODY_FAT)) {
                clickOnText("Height")
                clickOnText("Allow")

                assertPermGrantedForApp(TEST_APP_PACKAGE_NAME, HealthPermissions.READ_HEIGHT)
                assertPermNotGrantedForApp(TEST_APP_PACKAGE_NAME, HealthPermissions.WRITE_BODY_FAT)

                revokePermissionViaPackageManager(
                    context, TEST_APP_PACKAGE_NAME, HealthPermissions.READ_HEIGHT)
            }
    }

    @Test
    @Ignore(
        "TODO(b/265789268): Fix assertPermGrantedForApp(...)=false because Allow all is not actually clicked")
    fun grantAllPermissions_grantsAllPermissions() {
        revokePermissionViaPackageManager(
            context, TEST_APP_PACKAGE_NAME, HealthPermissions.READ_HEIGHT)
        revokePermissionViaPackageManager(
            context, TEST_APP_PACKAGE_NAME, HealthPermissions.WRITE_HEIGHT)
        context.launchRequestPermissionActivity(
            packageName = TEST_APP_PACKAGE_NAME,
            permissions = listOf(HealthPermissions.READ_HEIGHT, HealthPermissions.WRITE_HEIGHT)) {
                clickOnText("Allow all")
                clickOnText("Allow")

                assertPermGrantedForApp(TEST_APP_PACKAGE_NAME, HealthPermissions.READ_HEIGHT)
                assertPermGrantedForApp(TEST_APP_PACKAGE_NAME, HealthPermissions.WRITE_HEIGHT)

                revokePermissionViaPackageManager(
                    context, TEST_APP_PACKAGE_NAME, HealthPermissions.READ_HEIGHT)
                revokePermissionViaPackageManager(
                    context, TEST_APP_PACKAGE_NAME, HealthPermissions.WRITE_HEIGHT)
            }
    }

    @After
    fun tearDown() {
        revokePermissionViaPackageManager(
            context, TEST_APP_PACKAGE_NAME, HealthPermissions.READ_HEIGHT)
        revokePermissionViaPackageManager(
            context, TEST_APP_PACKAGE_NAME, HealthPermissions.WRITE_HEIGHT)
        revokePermissionViaPackageManager(
            context, TEST_APP_PACKAGE_NAME, HealthPermissions.WRITE_BODY_FAT)
    }

    @Throws(Exception::class)
    private fun assertPermGrantedForApp(packageName: String, permName: String) {
        Truth.assertThat(context.packageManager.checkPermission(permName, packageName))
            .isEqualTo(PackageManager.PERMISSION_GRANTED)
    }

    @Throws(Exception::class)
    private fun assertPermNotGrantedForApp(packageName: String, permName: String) {
        Truth.assertThat(context.packageManager.checkPermission(permName, packageName))
            .isEqualTo(PackageManager.PERMISSION_DENIED)
    }
}
