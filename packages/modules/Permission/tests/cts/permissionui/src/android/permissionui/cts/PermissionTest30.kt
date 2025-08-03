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

import android.Manifest.permission.ACCESS_BACKGROUND_LOCATION
import android.Manifest.permission.ACCESS_COARSE_LOCATION
import android.Manifest.permission.ACCESS_FINE_LOCATION
import androidx.test.filters.FlakyTest
import androidx.test.uiautomator.By
import org.junit.Assert.assertNull
import org.junit.Test

/** Runtime permission behavior apps targeting API 30 */
@FlakyTest
class PermissionTest30 : BaseUsePermissionTest() {

    @Test
    fun testCantRequestFgAndBgAtOnce() {
        // TODO(b/280542662): This delay is a temporary mitigation for an intermittent failure
        Thread.sleep(500)
        installPackage(APP_APK_PATH_30_WITH_BACKGROUND)
        assertAppHasPermission(ACCESS_FINE_LOCATION, false)
        assertAppHasPermission(ACCESS_BACKGROUND_LOCATION, false)

        requestAppPermissionsAndAssertResult(
            ACCESS_FINE_LOCATION to false,
            ACCESS_BACKGROUND_LOCATION to false,
            waitForWindowTransition = false
        ) {
            // Do nothing, should be automatically denied
        }
    }

    @Test
    fun testRequestBothInSequence() {
        installPackage(APP_APK_PATH_30_WITH_BACKGROUND)
        assertAppHasPermission(ACCESS_FINE_LOCATION, false)
        assertAppHasPermission(ACCESS_BACKGROUND_LOCATION, false)

        requestAppPermissionsAndAssertResult(ACCESS_FINE_LOCATION to true) {
            clickPermissionRequestAllowForegroundButton()
        }

        requestAppPermissionsAndAssertResult(
            ACCESS_BACKGROUND_LOCATION to true,
            waitForWindowTransition = false
        ) {
            clickAllowAlwaysInSettings()
            pressBack()
        }
    }

    @Test
    fun testRequestFgLocationAndNoAccuracyOptions() {
        installPackage(APP_APK_PATH_30)
        assertAppHasPermission(ACCESS_FINE_LOCATION, false)
        assertAppHasPermission(ACCESS_COARSE_LOCATION, false)

        requestAppPermissionsAndAssertResult(
            ACCESS_FINE_LOCATION to false,
            ACCESS_COARSE_LOCATION to false
        ) {
            // Verify there's no location accuracy options
            val locationAccuracyOptions =
                waitFindObjectOrNull(
                    By.res("com.android.permissioncontroller:id/permission_location_accuracy"),
                    1000L
                )
            assertNull(
                "For apps targetSDK < 31, location permission dialog shouldn't show " +
                    "accuracy options. Please update the system with " +
                    "the latest (at least Oct, 2021) mainline modules.",
                locationAccuracyOptions
            )
            // Close dialog
            clickPermissionRequestDenyButton()
        }
    }
}
