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

package android.security.cts

import android.platform.test.annotations.AsbSecurityTest
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.compatibility.common.util.mainline.MainlineModule
import com.android.compatibility.common.util.mainline.ModuleDetector
import org.junit.After
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SplitPermissionAutoGrantTest : BasePermissionUiTest() {

    @Before
    @After
    fun uninstallApp() {
        uninstallPackage(APP_PACKAGE_NAME)
    }

    @Test
    @AsbSecurityTest(cveBugId = [223907044])
    fun testAutoGrant() {
        assumeFalse(ModuleDetector.moduleIsPlayManaged(
                mContext.getPackageManager(), MainlineModule.PERMISSION_CONTROLLER))
        installPackage(SPLIT_PERMISSION_APK_PATH)
        assertAppHasPermission(android.Manifest.permission.BLUETOOTH, true)
        assertAppHasPermission(android.Manifest.permission.BLUETOOTH_CONNECT, true)
        assertAppHasPermission(android.Manifest.permission.BLUETOOTH_ADVERTISE, true)
        assertAppHasPermission(android.Manifest.permission.BLUETOOTH_SCAN, false)

        requestAppPermissionsAndAssertResult(
            android.Manifest.permission.BLUETOOTH_SCAN to false) {
            clickPermissionRequestDenyButton()
        }

        requestAppPermissionsAndAssertResult(
            android.Manifest.permission.BLUETOOTH_SCAN to true) {
            clickPermissionRequestAllowButton()
        }
    }
}
