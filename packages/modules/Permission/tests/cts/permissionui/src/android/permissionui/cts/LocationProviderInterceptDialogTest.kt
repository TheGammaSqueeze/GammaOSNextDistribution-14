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
// * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.permissionui.cts

import android.app.Activity
import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Intent
import android.location.LocationManager
import android.os.Build
import android.permission.cts.MtsIgnore
import android.permission.cts.PermissionUtils
import androidx.test.filters.FlakyTest
import androidx.test.filters.SdkSuppress
import androidx.test.uiautomator.By
import com.android.compatibility.common.util.AppOpsUtils
import com.android.compatibility.common.util.CddTest
import com.android.compatibility.common.util.SystemUtil
import java.util.concurrent.TimeUnit
import org.junit.Assert
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Ignore
import org.junit.Test

private const val EXTRA_PACKAGE_NAME = "android.intent.extra.PACKAGE_NAME"
private const val ACTION_MANAGE_APP_PERMISSIONS = "android.intent.action.MANAGE_APP_PERMISSIONS"

/**
 * Tests that LocationProviderInterceptDialog (a warning dialog) shows when attempting to view the
 * location permission for location a service provider app (e.g., usually GMS, but we use a custom
 * app in this test).
 */
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
@FlakyTest
@CddTest(requirement = "9.1/C-0-1")
class LocationProviderInterceptDialogTest : BaseUsePermissionTest() {
    @Before
    fun setup() {
        assumeFalse(isAutomotive)
        assumeFalse(isTv)
        assumeFalse(isWatch)
        installPackage(MIC_LOCATION_PROVIDER_APP_APK_PATH, grantRuntimePermissions = true)
        AppOpsUtils.setOpMode(
            MIC_LOCATION_PROVIDER_APP_PACKAGE_NAME,
            AppOpsManager.OPSTR_MOCK_LOCATION,
            AppOpsManager.MODE_ALLOWED
        )
        enableMicrophoneAppAsLocationProvider()
    }

    @Test
    @Ignore("b/288471744")
    @MtsIgnore(bugId = 288471744)
    fun clickLocationPermission_showDialog_clickOk() {
        openPermissionScreenForApp()
        clickAndWaitForWindowTransition(By.text("Location"))
        findView(By.textContains("Location access can be modified from location settings"), true)
        click(By.res(OK_BUTTON_RES))
    }

    @Test
    @Ignore("b/288471744")
    @MtsIgnore(bugId = 288471744)
    fun clickLocationPermission_showDialog_clickLocationAccess() {
        openPermissionScreenForApp()
        clickAndWaitForWindowTransition(By.text("Location"))
        findView(By.textContains("Location access can be modified from location settings"), true)
        clickAndWaitForWindowTransition(By.res(LOCATION_ACCESS_BUTTON_RES))
        findView(By.res(USE_LOCATION_LABEL_ID), true)
    }

    @Test
    @Ignore("b/288471744")
    @MtsIgnore(bugId = 288471744)
    fun checkRestrictedPermissions() {
        context.sendBroadcast(
            Intent(PermissionTapjackingTest.ACTION_SHOW_OVERLAY)
                .putExtra("package", MIC_LOCATION_PROVIDER_APP_PACKAGE_NAME)
                .putExtra("permission", "android.permission.BACKGROUND_CAMERA")
        )
    }

    private fun openPermissionScreenForApp() {
        restartPermissionController()
        doAndWaitForWindowTransition {
            SystemUtil.runWithShellPermissionIdentity {
                context.startActivity(
                    Intent(ACTION_MANAGE_APP_PERMISSIONS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        putExtra(EXTRA_PACKAGE_NAME, MIC_LOCATION_PROVIDER_APP_PACKAGE_NAME)
                    }
                )
            }
        }
    }

    private fun restartPermissionController() {
        PermissionUtils.clearAppState(permissionControllerPackageName)
    }

    private fun enableMicrophoneAppAsLocationProvider() {
        val locationManager = context.getSystemService(LocationManager::class.java)!!
        val future =
            startActivityForFuture(
                Intent().apply {
                    component =
                        ComponentName(
                            MIC_LOCATION_PROVIDER_APP_PACKAGE_NAME,
                            "$MIC_LOCATION_PROVIDER_APP_PACKAGE_NAME.AddLocationProviderActivity"
                        )
                }
            )
        val result = future.get(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
        Assert.assertEquals(Activity.RESULT_OK, result.resultCode)
        Assert.assertTrue(
            SystemUtil.callWithShellPermissionIdentity {
                locationManager.isProviderPackage(MIC_LOCATION_PROVIDER_APP_PACKAGE_NAME)
            }
        )
    }

    companion object {
        private const val USE_LOCATION_LABEL_ID = "com.android.settings:id/switch_text"
        private const val MIC_LOCATION_PROVIDER_APP_APK_PATH =
            "$APK_DIRECTORY/CtsAccessMicrophoneAppLocationProvider.apk"
        private const val MIC_LOCATION_PROVIDER_APP_PACKAGE_NAME =
            "android.permissionui.cts.accessmicrophoneapplocationprovider"
        private const val OK_BUTTON_RES = "android:id/button2"
        private const val LOCATION_ACCESS_BUTTON_RES = "android:id/button1"
        private val permissionControllerPackageName =
            context.packageManager.permissionControllerPackageName
    }
}
