/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.permissioncontroller.permissionui.ui.wear

import android.Manifest.permission.CAMERA
import android.content.Intent
import android.os.Build
import android.permission.cts.PermissionUtils.grantPermission
import android.permission.cts.PermissionUtils.install
import android.permission.cts.PermissionUtils.uninstallApp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.uiautomator.By
import com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity
import com.android.compatibility.common.util.UiAutomatorUtils2.waitFindObject
import com.android.permissioncontroller.permissionui.PermissionHub2Test
import com.android.permissioncontroller.permissionui.pressHome
import com.android.permissioncontroller.permissionui.wakeUpScreen
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Tests for [WearPermissionUsageDetailsFragment] */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
class WearPermissionUsageDetailsFragmentTest : PermissionHub2Test() {
    private val CAMERA_APK =
        "/data/local/tmp/pc-permissionui/PermissionUiUseCameraPermissionApp.apk"
    private val CAMERA_APP = "com.android.permissioncontroller.tests.appthatrequestpermission"
    private val CAMERA_APP_LABEL = "CameraRequestApp"
    private val CAMERA_PREF_LABEL = "Camera"
    private val MANAGE_PERMISSION_LABEL = "Manage permission"
    private val APP_PERMISSIONS_TITLE = "App permissions"
    private val TIMEOUT = 30_000L

    @Before
    fun setup() {
        assumeTrue(isWear())
        wakeUpScreen()
        install(CAMERA_APK)
        grantPermission(CAMERA_APP, CAMERA)
        accessCamera()
        goToPermissionUsageDetails()
    }

    private fun goToPermissionUsageDetails() {
        runWithShellPermissionIdentity {
            context.startActivity(
                Intent(Intent.ACTION_REVIEW_PERMISSION_USAGE).apply {
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }

        clickObject(CAMERA_PREF_LABEL)
    }

    @Test
    fun testClickAppThenTransitToAppPermissionGroups() {
        clickObject(CAMERA_APP_LABEL)

        // Find the title of AppPermissionGroups.
        waitFindObject(By.textContains(APP_PERMISSIONS_TITLE), TIMEOUT)
        // Find the permission in the list.
        waitFindObject(By.textContains(CAMERA_PREF_LABEL), TIMEOUT)
    }

    @Test
    fun testClickManagePermissionThenTransitToPermissionApps() {
        clickObject(MANAGE_PERMISSION_LABEL)

        // Find the title of PermissionApps.
        waitFindObject(By.textContains(CAMERA_PREF_LABEL), TIMEOUT)
        // Find the test app is in the app list.
        waitFindObject(By.textContains(CAMERA_APP_LABEL), TIMEOUT)
    }

    @After
    fun tearDown() {
        uninstallApp(CAMERA_APP)
        pressHome()
    }
}
