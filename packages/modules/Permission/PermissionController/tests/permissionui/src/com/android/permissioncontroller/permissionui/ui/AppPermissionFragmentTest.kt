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

package com.android.permissioncontroller.permissionui.ui

import android.content.Intent
import android.os.UserHandle
import android.os.UserHandle.myUserId
import android.permission.cts.PermissionUtils.install
import android.permission.cts.PermissionUtils.uninstallApp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import com.android.compatibility.common.util.SystemUtil.eventually
import com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity
import com.android.compatibility.common.util.UiAutomatorUtils2.waitFindObjectOrNull
import com.android.permissioncontroller.permissionui.wakeUpScreen
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith

private const val APP_PERMISSIONS = "App permissions"

/**
 * Simple tests for {@link AppPermissionFragment} Currently, does NOT run on TV.
 *
 * TODO(b/178576541): Adapt and run on TV. Run with: atest AppPermissionFragmentTest
 */
@RunWith(AndroidJUnit4::class)
class AppPermissionFragmentTest : BasePermissionUiTest() {
    private val ONE_PERMISSION_DEFINER_APK =
        "/data/local/tmp/permissioncontroller/tests/permisssionui/" +
            "PermissionUiDefineAdditionalPermissionApp.apk"
    private val PERMISSION_USER_APK =
        "/data/local/tmp/permissioncontroller/tests/permisssionui/" +
            "PermissionUiUseAdditionalPermissionApp.apk"
    private val DEFINER_PKG = "com.android.permissioncontroller.tests.appthatdefinespermission"
    private val USER_PKG = "com.android.permissioncontroller.tests.appthatrequestpermission"

    private val PERM = "com.android.permissioncontroller.tests.A"

    @Before fun assumeNotTelevision() = assumeFalse(isTelevision)

    @Before
    fun wakeScreenUp() {
        wakeUpScreen()
    }

    @Before
    fun installApps() {
        install(ONE_PERMISSION_DEFINER_APK)
        install(PERMISSION_USER_APK)
    }

    @Before
    fun startManagePermissionAppsActivity() {
        runWithShellPermissionIdentity {
            instrumentationContext.startActivity(
                Intent(Intent.ACTION_MANAGE_APP_PERMISSION).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    putExtra(Intent.EXTRA_PACKAGE_NAME, USER_PKG)
                    putExtra(Intent.EXTRA_PERMISSION_NAME, PERM)
                    putExtra(Intent.EXTRA_PERMISSION_GROUP_NAME, PERM)
                    putExtra(Intent.EXTRA_USER, UserHandle.of(myUserId()))
                    putExtra("com.android.permissioncontroller.extra.CALLER_NAME", "")
                }
            )
        }
    }

    @Test
    @Ignore("b/301001789")
    fun activityIsClosedWhenUserIsUninstalled() {
        uninstallApp(USER_PKG)
        eventually { assertNull(waitFindObjectOrNull(By.text(APP_PERMISSIONS))) }
    }

    @Test
    @Ignore("b/301001789")
    fun activityIsClosedWhenDefinerIsUninstalled() {
        uninstallApp(DEFINER_PKG)
        eventually { assertNull(waitFindObjectOrNull(By.text(APP_PERMISSIONS))) }
    }

    @After
    fun uninstallTestApp() {
        uninstallApp(DEFINER_PKG)
        uninstallApp(USER_PKG)
    }
}
