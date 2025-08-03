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

package com.android.permissioncontroller.permissionui.ui.handheld

import android.Manifest.permission.CAMERA
import android.content.Intent
import androidx.test.uiautomator.By
import com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity
import com.android.compatibility.common.util.UiAutomatorUtils2.waitFindObject
import com.android.permissioncontroller.permissionui.PermissionHub2Test
import com.android.permissioncontroller.permissionui.ui.CAMERA_TEST_APP_LABEL
import com.android.permissioncontroller.permissionui.ui.grantTestAppPermission
import com.android.permissioncontroller.permissionui.ui.installTestAppThatUsesCameraPermission
import com.android.permissioncontroller.permissionui.ui.uninstallTestApps
import org.junit.After
import org.junit.Before
import org.junit.Test

/** Simple tests for {@link ReviewOngoingUsageFragment} */
class ReviewOngoingUsageFragmentTest : PermissionHub2Test() {

    @Before
    fun setup() {
        installTestAppThatUsesCameraPermission()
        grantTestAppPermission(CAMERA)

        accessCamera()
    }

    @Test
    fun cameraAccessShouldBeShown() {
        runWithShellPermissionIdentity {
            context.startActivity(
                Intent(Intent.ACTION_REVIEW_ONGOING_PERMISSION_USAGE).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }

        waitFindObject(By.textContains(CAMERA_TEST_APP_LABEL)).click()
    }

    @After fun cleanUp() = uninstallTestApps()
}
