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

package android.permissionui.cts

import android.Manifest
import android.content.Intent
import android.os.Build
import android.permission.PermissionManager
import androidx.test.filters.FlakyTest
import androidx.test.filters.SdkSuppress
import androidx.test.uiautomator.By
import com.android.compatibility.common.util.SystemUtil
import org.junit.Assert.assertNull
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

@SdkSuppress(minSdkVersion = Build.VERSION_CODES.TIRAMISU, codeName = "Tiramisu")
@FlakyTest
class PermissionDecisionsTest : BaseUsePermissionTest() {

    companion object {
        const val ASSERT_ABSENT_SELECTOR_TIMEOUT_MS = 500L
    }

    // Permission decisions has only been implemented on Auto
    @Before
    fun assumeAuto() {
        assumeTrue(isAutomotive)
    }

    @Test
    fun testAcceptPermissionDialogShowsDecisionWithGrantedAccess() {
        installPackage(APP_APK_PATH_30_WITH_BACKGROUND)
        requestAppPermissionsAndAssertResult(Manifest.permission.ACCESS_FINE_LOCATION to true) {
            clickPermissionRequestAllowForegroundButton()
        }

        openPermissionDecisions()
        waitFindObject(
            By.hasChild(By.text("You gave $APP_PACKAGE_NAME access to location"))
                .hasChild(By.text("Today"))
        )
    }

    @Test
    fun testDenyPermissionDialogShowsDecisionWithDeniedAccess() {
        installPackage(APP_APK_PATH_30_WITH_BACKGROUND)
        requestAppPermissionsAndAssertResult(Manifest.permission.ACCESS_FINE_LOCATION to false) {
            clickPermissionRequestDenyButton()
        }

        openPermissionDecisions()
        waitFindObject(
            By.hasChild(By.text("You denied $APP_PACKAGE_NAME access to location"))
                .hasChild(By.text("Today"))
        )
    }

    @Test
    fun testAppUninstallRemovesDecision() {
        installPackage(APP_APK_PATH_30_WITH_BACKGROUND)
        requestAppPermissionsAndAssertResult(Manifest.permission.ACCESS_FINE_LOCATION to false) {
            clickPermissionRequestDenyButton()
        }
        uninstallApp()

        openPermissionDecisions()
        assertNull(
            waitFindObjectOrNull(
                By.hasChild(By.text("You denied $APP_PACKAGE_NAME access to location"))
                    .hasChild(By.text("Today")),
                ASSERT_ABSENT_SELECTOR_TIMEOUT_MS
            )
        )
    }

    @Test
    fun testClickOnDecisionAndChangeAccessUpdatesDecision() {
        installPackage(APP_APK_PATH_30_WITH_BACKGROUND)
        requestAppPermissionsAndAssertResult(Manifest.permission.ACCESS_FINE_LOCATION to true) {
            clickPermissionRequestAllowForegroundButton()
        }

        openPermissionDecisions()

        waitFindObject(
                By.hasChild(By.text("You gave $APP_PACKAGE_NAME access to location"))
                    .hasChild(By.text("Today"))
            )
            .click()

        waitFindObject(By.text(APP_PACKAGE_NAME))
        waitFindObject(By.text("Location access for this app"))

        // change the permission on the app permission screen and verify that updates the decision
        // page
        waitFindObject(By.text("Don’t allow")).click()
        pressBack()
        waitFindObject(
            By.hasChild(By.text("You denied $APP_PACKAGE_NAME access to location"))
                .hasChild(By.text("Today"))
        )
    }

    private fun openPermissionDecisions() {
        doAndWaitForWindowTransition {
            SystemUtil.runWithShellPermissionIdentity {
                context.startActivity(
                    Intent(PermissionManager.ACTION_REVIEW_PERMISSION_DECISIONS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                )
            }
        }
    }
}
