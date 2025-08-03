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

package com.android.permissioncontroller.permissionui.ui.handheld.v31

import android.Manifest.permission.CAMERA
import android.content.Intent
import android.os.Build
import android.permission.cts.PermissionUtils.grantPermission
import android.permission.cts.PermissionUtils.install
import android.permission.cts.PermissionUtils.uninstallApp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.uiautomator.By
import com.android.compatibility.common.util.SystemUtil.eventually
import com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity
import com.android.compatibility.common.util.UiAutomatorUtils2.waitFindObject
import com.android.permissioncontroller.permissionui.PermissionHub2Test
import com.android.permissioncontroller.permissionui.pressHome
import com.android.permissioncontroller.permissionui.wakeUpScreen
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Simple tests for {@link PermissionUsageFragment} */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
class PermissionUsageFragmentTest : PermissionHub2Test() {
    private val APK = "/data/local/tmp/pc-permissionui" + "/PermissionUiUseCameraPermissionApp.apk"
    private val APP = "com.android.permissioncontroller.tests.appthatrequestpermission"
    private val APP_LABEL = "CameraRequestApp"
    private val CAMERA_PREF_LABEL = "Camera"
    private val REFRESH = "Refresh"

    @Before
    fun setup() {
        wakeUpScreen()
        install(APK)
        grantPermission(APP, CAMERA)
    }

    @Test
    fun cameraAccessShouldBeListed() {
        accessCamera()

        runWithShellPermissionIdentity {
            context.startActivity(
                Intent(Intent.ACTION_REVIEW_PERMISSION_USAGE).apply {
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }

        eventually {
            try {
                waitFindObject(By.res("android:id/title").textContains(CAMERA_PREF_LABEL)).click()
            } catch (e: Exception) {
                waitFindObject(By.textContains(REFRESH)).click()
                throw e
            }
        }

        waitFindObject(By.textContains(APP_LABEL))
    }

    @After
    fun uninstallTestApp() {
        uninstallApp(APP)
        pressHome()
    }
}
