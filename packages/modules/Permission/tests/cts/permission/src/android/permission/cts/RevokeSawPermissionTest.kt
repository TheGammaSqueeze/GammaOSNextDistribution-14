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

package android.permission.cts

import android.content.pm.PackageManager
import android.platform.test.annotations.AppModeFull
import android.platform.test.annotations.AsbSecurityTest
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert
import org.junit.Test

private val APP_PKG_NAME = "android.permission.cts.usesystemalertwindowpermission"
private val APK_22 = "/data/local/tmp/cts-permission/" + "CtsAppThatRequestsSystemAlertWindow22.apk"
private val APK_23 = "/data/local/tmp/cts-permission/" + "CtsAppThatRequestsSystemAlertWindow23.apk"

@AppModeFull
class RevokeSawPermissionTest {

    fun installApp(apk: String) {
        PermissionUtils.install(apk)
    }

    @After
    fun uninstallApp() {
        PermissionUtils.uninstallApp(APP_PKG_NAME)
    }

    @AsbSecurityTest(cveBugId = [221040577L])
    @Test
    fun testPre23AppsWithSystemAlertWindowGetDeniedOnUpgrade() {
        installApp(APK_22)
        assertAppHasPermission(android.Manifest.permission.SYSTEM_ALERT_WINDOW, true)
        installApp(APK_23)
        assertAppHasPermission(android.Manifest.permission.SYSTEM_ALERT_WINDOW, false)
    }

    private fun assertAppHasPermission(permissionName: String, expectPermission: Boolean) {
        Assert.assertEquals(
            if (expectPermission) {
                PackageManager.PERMISSION_GRANTED
            } else {
                PackageManager.PERMISSION_DENIED
            },
            InstrumentationRegistry.getInstrumentation()
                .getTargetContext()
                .packageManager
                .checkPermission(permissionName, APP_PKG_NAME)
        )
    }
}
