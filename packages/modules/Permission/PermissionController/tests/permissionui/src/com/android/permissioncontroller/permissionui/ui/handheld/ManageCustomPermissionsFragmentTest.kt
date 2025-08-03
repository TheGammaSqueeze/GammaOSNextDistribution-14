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

package com.android.permissioncontroller.permissionui.ui.handheld

import android.content.Intent
import android.permission.cts.PermissionUtils.grantPermission
import android.permission.cts.PermissionUtils.install
import android.permission.cts.PermissionUtils.revokePermission
import android.permission.cts.PermissionUtils.uninstallApp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import com.android.compatibility.common.util.SystemUtil.eventually
import com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity
import com.android.compatibility.common.util.UiAutomatorUtils2.waitFindObject
import com.android.permissioncontroller.permissionui.getUsageCountsFromUi
import com.android.permissioncontroller.permissionui.wakeUpScreen
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Simple tests for {@link ManageCustomPermissionsFragment} */
@RunWith(AndroidJUnit4::class)
class ManageCustomPermissionsFragmentTest : BaseHandheldPermissionUiTest() {
    private val ONE_PERMISSION_DEFINER_APK =
        "/data/local/tmp/pc-permissionui/" + "PermissionUiDefineAdditionalPermissionApp.apk"
    private val PERMISSION_USER_APK =
        "/data/local/tmp/pc-permissionui/" + "PermissionUiUseAdditionalPermissionApp.apk"
    private val DEFINER_PKG = "com.android.permissioncontroller.tests.appthatdefinespermission"
    private val USER_PKG = "com.android.permissioncontroller.tests.appthatrequestpermission"

    private val PERM_LABEL = "Permission A"
    private val PERM = "com.android.permissioncontroller.tests.A"
    private val ADDITIONAL_PERMISSIONS_LABEL = "Additional permissions"

    @Before
    fun setup() {
        wakeUpScreen()

        runWithShellPermissionIdentity {
            instrumentationContext.startActivity(
                Intent(Intent.ACTION_MANAGE_PERMISSIONS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }
    }

    @Test
    fun groupSummaryGetsUpdatedWhenPermissionGetsGranted() {
        install(ONE_PERMISSION_DEFINER_APK)
        install(PERMISSION_USER_APK)
        waitFindObject(By.textContains(ADDITIONAL_PERMISSIONS_LABEL)).click()
        waitFindObject(By.textContains(PERM_LABEL))

        val original = getUsageCountsFromUi(PERM_LABEL)

        grantPermission(USER_PKG, PERM)
        eventually {
            assertThat(getUsageCountsFromUi(PERM_LABEL).granted).isEqualTo(original.granted + 1)
        }
    }

    @Test
    fun groupSummaryGetsUpdatedWhenPermissionGetsRevoked() {
        install(ONE_PERMISSION_DEFINER_APK)
        install(PERMISSION_USER_APK)
        waitFindObject(By.textContains(ADDITIONAL_PERMISSIONS_LABEL)).click()
        waitFindObject(By.textContains(PERM_LABEL))

        val original = getUsageCountsFromUi(PERM_LABEL)

        grantPermission(USER_PKG, PERM)
        eventually { assertThat(getUsageCountsFromUi(PERM_LABEL)).isNotEqualTo(original) }

        revokePermission(USER_PKG, PERM)
        eventually { assertThat(getUsageCountsFromUi(PERM_LABEL)).isEqualTo(original) }
    }

    @After
    fun tearDown() {
        uninstallApp(DEFINER_PKG)
        uninstallApp(USER_PKG)

        uiDevice.pressBack()
    }
}
