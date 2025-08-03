/*
 * Copyright (C) 2015 The Android Open Source Project
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

import androidx.test.filters.FlakyTest
import org.junit.Test

/** Runtime permission behavior tests for permission groups. */
@FlakyTest
class PermissionGroupTest : BaseUsePermissionTest() {
    @Test
    fun testRuntimeGroupGrantExpansion23() {
        installPackage(APP_APK_PATH_23)
        testRuntimeGroupGrantExpansion(true)
    }

    @Test
    fun testRuntimeGroupGrantExpansion25() {
        installPackage(APP_APK_PATH_25)
        testRuntimeGroupGrantExpansion(true)
    }

    @Test
    fun testRuntimeGroupGrantExpansion26() {
        installPackage(APP_APK_PATH_26)
        testRuntimeGroupGrantExpansion(false)
    }

    @Test
    fun testRuntimeGroupGrantExpansion30() {
        installPackage(APP_APK_PATH_30)
        testRuntimeGroupGrantExpansion(false)
    }

    @Test
    fun testPartiallyGrantedGroupExpansion() {
        installPackage(APP_APK_PATH_30)

        // Start out without permission
        assertAppHasPermission(android.Manifest.permission.RECEIVE_SMS, false)
        assertAppHasPermission(android.Manifest.permission.SEND_SMS, false)

        // Grant only RECEIVE_SMS
        uiAutomation.grantRuntimePermission(
            APP_PACKAGE_NAME,
            android.Manifest.permission.RECEIVE_SMS
        )
        assertAppHasPermission(android.Manifest.permission.RECEIVE_SMS, true)

        // Request both permissions, and expect that SEND_SMS is granted
        requestAppPermissionsAndAssertResult(
            android.Manifest.permission.RECEIVE_SMS to true,
            android.Manifest.permission.SEND_SMS to true,
            waitForWindowTransition = false
        ) {}

        assertAppHasPermission(android.Manifest.permission.SEND_SMS, true)
    }

    private fun testRuntimeGroupGrantExpansion(expectExpansion: Boolean) {
        // Start out without permission
        assertAppHasPermission(android.Manifest.permission.RECEIVE_SMS, false)
        assertAppHasPermission(android.Manifest.permission.SEND_SMS, false)

        // Request only one permission from the 'SMS' permission group at runtime,
        // but two from this group are <uses-permission> in the manifest
        requestAppPermissionsAndAssertResult(
            android.Manifest.permission.RECEIVE_SMS to true,
            waitForWindowTransition = !isWatch
        ) {
            clickPermissionRequestAllowButton()
        }

        assertAppHasPermission(android.Manifest.permission.SEND_SMS, expectExpansion)
    }
}
