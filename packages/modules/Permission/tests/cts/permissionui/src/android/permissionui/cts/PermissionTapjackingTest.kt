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

import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Point
import android.os.Build
import androidx.test.filters.FlakyTest
import androidx.test.filters.SdkSuppress
import androidx.test.uiautomator.By
import com.android.compatibility.common.util.SystemUtil
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Test

/** Tests permissions can't be tapjacked */
@FlakyTest
class PermissionTapjackingTest : BaseUsePermissionTest() {

    @Before
    fun installAppLatest() {
        installPackage(APP_APK_PATH_WITH_OVERLAY)
    }

    @Test
    fun testTapjackGrantDialog_fullOverlay() {
        // PermissionController for television uses a floating window.
        assumeFalse(isTv)

        // Automotive split-screen multitasking uses multi-window mode
        assumeFalse(isAutomotiveSplitscreen)

        assertAppHasPermission(ACCESS_FINE_LOCATION, false)
        requestAppPermissionsForNoResult(ACCESS_FINE_LOCATION) {}

        val buttonCenter =
            waitFindObject(By.text(getPermissionControllerString(ALLOW_FOREGROUND_BUTTON_TEXT)))
                .visibleCenter

        // Wait for overlay to hide the dialog
        context.sendBroadcast(Intent(ACTION_SHOW_OVERLAY).putExtra(EXTRA_FULL_OVERLAY, true))
        waitFindObject(By.res("android.permissionui.cts.usepermission:id/overlay"))

        tryClicking(buttonCenter)
    }

    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
    @Test
    fun testTapjackGrantDialog_partialOverlay() {
        // PermissionController for television uses a floating window.
        assumeFalse(isTv)

        // Automotive split-screen multitasking uses multi-window mode
        assumeFalse(isAutomotiveSplitscreen)

        assertAppHasPermission(ACCESS_FINE_LOCATION, false)
        requestAppPermissionsForNoResult(ACCESS_FINE_LOCATION) {}

        val foregroundButtonCenter =
            waitFindObject(By.text(getPermissionControllerString(ALLOW_FOREGROUND_BUTTON_TEXT)))
                .visibleCenter
        val oneTimeButton =
            waitFindObjectOrNull(By.text(getPermissionControllerString(ALLOW_ONE_TIME_BUTTON_TEXT)))
        // If one-time button is not available, fallback to deny button
        val overlayButtonBounds =
            oneTimeButton?.visibleBounds
                ?: waitFindObject(By.text(getPermissionControllerString(DENY_BUTTON_TEXT)))
                    .visibleBounds

        // Wait for overlay to hide the dialog
        context.sendBroadcast(
            Intent(ACTION_SHOW_OVERLAY)
                .putExtra(EXTRA_FULL_OVERLAY, false)
                .putExtra(OVERLAY_LEFT, overlayButtonBounds.left)
                .putExtra(OVERLAY_TOP, overlayButtonBounds.top)
                .putExtra(OVERLAY_RIGHT, overlayButtonBounds.right)
                .putExtra(OVERLAY_BOTTOM, overlayButtonBounds.bottom)
        )
        waitFindObject(By.res("android.permissionui.cts.usepermission:id/overlay"))

        tryClicking(foregroundButtonCenter)
    }

    private fun tryClicking(buttonCenter: Point) {
        try {
            // Try to grant the permission, this should fail
            SystemUtil.eventually(
                {
                    if (
                        packageManager.checkPermission(ACCESS_FINE_LOCATION, APP_PACKAGE_NAME) ==
                            PackageManager.PERMISSION_DENIED
                    ) {
                        uiDevice.click(buttonCenter.x, buttonCenter.y)
                        Thread.sleep(100)
                    }
                    assertAppHasPermission(ACCESS_FINE_LOCATION, true)
                },
                10000
            )
        } catch (e: RuntimeException) {
            // expected
        }
        // Permission should not be granted
        assertAppHasPermission(ACCESS_FINE_LOCATION, false)

        // Verify that clicking the dialog without the overlay still works
        context.sendBroadcast(Intent(ACTION_HIDE_OVERLAY))
        SystemUtil.eventually(
            {
                if (
                    packageManager.checkPermission(ACCESS_FINE_LOCATION, APP_PACKAGE_NAME) ==
                        PackageManager.PERMISSION_DENIED
                ) {
                    uiDevice.click(buttonCenter.x, buttonCenter.y)
                    Thread.sleep(100)
                }
                assertAppHasPermission(ACCESS_FINE_LOCATION, true)
            },
            10000
        )
    }

    companion object {
        const val ACTION_SHOW_OVERLAY = "android.permissionui.cts.usepermission.ACTION_SHOW_OVERLAY"
        const val ACTION_HIDE_OVERLAY = "android.permissionui.cts.usepermission.ACTION_HIDE_OVERLAY"

        const val EXTRA_FULL_OVERLAY = "android.permissionui.cts.usepermission.extra.FULL_OVERLAY"

        const val OVERLAY_LEFT = "android.permissionui.cts.usepermission.extra.OVERLAY_LEFT"
        const val OVERLAY_TOP = "android.permissionui.cts.usepermission.extra.OVERLAY_TOP"
        const val OVERLAY_RIGHT = "android.permissionui.cts.usepermission.extra.OVERLAY_RIGHT"
        const val OVERLAY_BOTTOM = "android.permissionui.cts.usepermission.extra.OVERLAY_BOTTOM"
    }
}
