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
import android.permission.cts.PermissionUtils.install
import android.permission.cts.PermissionUtils.uninstallApp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import com.android.compatibility.common.util.SystemUtil.eventually
import com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity
import com.android.compatibility.common.util.UiAutomatorUtils2.waitFindObject
import com.android.compatibility.common.util.UiAutomatorUtils2.waitFindObjectOrNull
import com.android.permissioncontroller.permissionui.wakeUpScreen
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

private const val MORE_OPTIONS = "More options"
private const val ALL_PERMISSIONS = "All permissions"

/**
 * Simple tests for {@link AllAppPermissionsFragment} Currently, does NOT run on TV.
 *
 * TODO(b/178576541): Adapt and run on TV. Run with: atest AllAppPermissionsFragmentTest
 */
@RunWith(AndroidJUnit4::class)
class AllAppPermissionsFragmentTest : BasePermissionUiTest() {
    private val ONE_PERMISSION_DEFINER_APK =
        "/data/local/tmp/pc-permissionui/" + "PermissionUiDefineAdditionalPermissionApp.apk"
    private val PERMISSION_USER_APK =
        "/data/local/tmp/pc-permissionui/" + "PermissionUiUseAdditionalPermissionApp.apk"
    private val TWO_PERMISSION_USER_APK =
        "/data/local/tmp/pc-permissionui/" + "PermissionUiUseTwoAdditionalPermissionsApp.apk"
    private val DEFINER_PKG = "com.android.permissioncontroller.tests.appthatdefinespermission"
    private val USER_PKG = "com.android.permissioncontroller.tests.appthatrequestpermission"

    private val PERM_LABEL = "Permission B"
    private val SECOND_PERM_LABEL = "Permission C"

    private val TIMEOUT_SHORT = 500L

    @Before fun assumeNotTelevision() = assumeFalse(isTelevision)

    @Before
    fun wakeScreenUp() {
        wakeUpScreen()
    }

    @Before
    fun startManageAppPermissionsActivity() {
        install(ONE_PERMISSION_DEFINER_APK)
        install(PERMISSION_USER_APK)

        runWithShellPermissionIdentity {
            instrumentationContext.startActivity(
                Intent(Intent.ACTION_MANAGE_APP_PERMISSIONS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    putExtra(Intent.EXTRA_PACKAGE_NAME, USER_PKG)
                }
            )
        }

        waitFindObject(By.descContains(MORE_OPTIONS)).click()
        waitFindObject(By.text(ALL_PERMISSIONS)).click()
    }

    @Test
    fun usedPermissionsAreListed() {
        waitFindObject(By.text(PERM_LABEL))
    }

    @Test
    fun permissionsAreAddedWhenAppIsUpdated() {
        waitFindObject(By.text(PERM_LABEL))

        install(TWO_PERMISSION_USER_APK)
        eventually { waitFindObject(By.text(SECOND_PERM_LABEL)) }
    }

    @Test
    fun permissionsAreRemovedWhenAppIsUpdated() {
        install(TWO_PERMISSION_USER_APK)
        eventually { waitFindObject(By.text(SECOND_PERM_LABEL)) }

        install(PERMISSION_USER_APK)
        eventually { assertNull(waitFindObjectOrNull(By.text(SECOND_PERM_LABEL), TIMEOUT_SHORT)) }
    }

    @Test
    fun activityIsClosedWhenUserIsUninstalled() {
        uninstallApp(USER_PKG)
        eventually { assertNull(waitFindObjectOrNull(By.text(ALL_PERMISSIONS), TIMEOUT_SHORT)) }
    }

    @After
    fun uninstallTestApp() {
        uninstallApp(DEFINER_PKG)
        uninstallApp(USER_PKG)
    }
}
