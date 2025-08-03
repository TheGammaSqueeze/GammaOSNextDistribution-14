/*
 * Copyright (C) 2018 The Android Open Source Project
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

import android.os.Build
import androidx.test.filters.FlakyTest
import androidx.test.filters.SdkSuppress
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Test

/** Runtime permission behavior tests for permission splits. */
@FlakyTest
class PermissionSplitTest : BaseUsePermissionTest() {
    @Before
    fun assumeNotTv() {
        assumeFalse(isTv)
    }

    @Test
    fun testPermissionSplit28() {
        installPackage(APP_APK_PATH_28)
        testLocationPermissionSplit(true)
    }

    @Test
    fun testPermissionNotSplit29() {
        installPackage(APP_APK_PATH_29)
        testLocationPermissionSplit(false)
    }

    @Test
    fun testPermissionNotSplit30() {
        installPackage(APP_APK_PATH_30)
        testLocationPermissionSplit(false)
    }

    @Test
    fun testPermissionNotSplitLatest() {
        installPackage(APP_APK_PATH_LATEST)
        testLocationPermissionSplit(false)
    }

    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.TIRAMISU, codeName = "Tiramisu")
    @Test
    fun testBodySensorSplit() {
        installPackage(APP_APK_PATH_31)
        testBodySensorPermissionSplit(true)
    }

    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.TIRAMISU, codeName = "Tiramisu")
    @Test
    fun testBodySensorSplit32() {
        installPackage(APP_APK_PATH_32)
        testBodySensorPermissionSplit(true)
    }

    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.TIRAMISU, codeName = "Tiramisu")
    @Test
    fun testBodySensorNonSplit() {
        installPackage(APP_APK_PATH_LATEST)
        testBodySensorPermissionSplit(false)
    }

    private fun testLocationPermissionSplit(expectSplit: Boolean) {
        assertAppHasPermission(android.Manifest.permission.ACCESS_FINE_LOCATION, false)
        assertAppHasPermission(android.Manifest.permission.ACCESS_BACKGROUND_LOCATION, false)

        requestAppPermissionsAndAssertResult(
            android.Manifest.permission.ACCESS_FINE_LOCATION to true,
            waitForWindowTransition = false
        ) {
            if (expectSplit) {
                clickPermissionRequestSettingsLinkAndAllowAlways()
            } else {
                doAndWaitForWindowTransition { clickPermissionRequestAllowForegroundButton() }
            }
        }

        assertAppHasPermission(android.Manifest.permission.ACCESS_BACKGROUND_LOCATION, expectSplit)
    }

    private fun testBodySensorPermissionSplit(expectSplit: Boolean) {
        assertAppHasPermission(android.Manifest.permission.BODY_SENSORS, false)
        assertAppHasPermission(android.Manifest.permission.BODY_SENSORS_BACKGROUND, false)

        requestAppPermissionsAndAssertResult(
            android.Manifest.permission.BODY_SENSORS to true,
            waitForWindowTransition = false
        ) {
            if (expectSplit) {
                clickPermissionRequestSettingsLinkAndAllowAlways()
            } else {
                doAndWaitForWindowTransition { clickPermissionRequestAllowForegroundButton() }
            }
        }

        assertAppHasPermission(android.Manifest.permission.BODY_SENSORS_BACKGROUND, expectSplit)
    }
}
