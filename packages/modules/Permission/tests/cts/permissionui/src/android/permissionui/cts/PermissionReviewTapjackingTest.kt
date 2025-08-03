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

package android.permissionui.cts

import android.content.ComponentName
import android.content.Intent
import androidx.test.filters.FlakyTest
import androidx.test.uiautomator.By
import com.android.compatibility.common.util.SystemUtil
import java.lang.Exception
import org.junit.After
import org.junit.Assert
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Test

/** Tests permission review screen can't be tapjacked */
@FlakyTest
class PermissionReviewTapjackingTest : BaseUsePermissionTest() {

    companion object {
        const val HELPER_APP_OVERLAY = "$APK_DIRECTORY/CtsHelperAppOverlay.apk"
        private const val HELPER_PACKAGE_NAME = "android.permissionui.cts.helper.overlay"
    }

    @Before
    fun installApp22AndApprovePermissionReview() {
        assumeFalse(packageManager.arePermissionsIndividuallyControlled())

        installPackage(APP_APK_PATH_22)
        installPackage(HELPER_APP_OVERLAY)

        SystemUtil.runShellCommandOrThrow(
            "appops set $HELPER_PACKAGE_NAME android:system_alert_window allow"
        )
    }

    @After
    fun uninstallPackages() {
        SystemUtil.runShellCommandOrThrow("pm uninstall $APP_PACKAGE_NAME")
        SystemUtil.runShellCommandOrThrow("pm uninstall $HELPER_PACKAGE_NAME")
    }

    @Test
    fun testOverlaysAreHidden() {
        context.startActivity(
            Intent()
                .setComponent(
                    ComponentName(HELPER_PACKAGE_NAME, "$HELPER_PACKAGE_NAME.OverlayActivity")
                )
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        findOverlay()

        context.startActivity(
            Intent()
                .setComponent(
                    ComponentName(APP_PACKAGE_NAME, "$APP_PACKAGE_NAME.FinishOnCreateActivity")
                )
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )

        if (isWatch) {
            waitFindObject(
                By.text(getPermissionControllerString("review_button_cancel")),
                TIMEOUT_MILLIS * 2
            )
        } else {
            waitFindObject(By.res("com.android.permissioncontroller:id/permissions_message"))
        }

        try {
            findOverlay()
            Assert.fail("Overlay was displayed")
        } catch (e: Exception) {
            // expected
        }

        pressHome()
        findOverlay()
    }

    private fun findOverlay() = waitFindObject(By.text("Find me!"))
}
