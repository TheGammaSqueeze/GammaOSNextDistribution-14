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

package com.android.permissioncontroller.permissionui.ui.television

import android.content.Intent
import com.android.compatibility.common.util.SystemUtil
import com.android.permissioncontroller.permissionui.ui.BasePermissionUiTest
import com.android.permissioncontroller.permissionui.ui.uninstallTestApps
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before

abstract class TelevisionUiBaseTest : BasePermissionUiTest() {
    val bodySensorsPermissionLabel = "Body sensors"
    val cameraPermissionLabel = "Camera"
    val otherPermissionsLabel = "Other permissions"
    val additionalPermissionsLabel = "Additional permissions"

    @Before fun assumeTelevision() = assumeTrue(isTelevision)

    @Before
    fun wakeUpAndGoToHomeScreen() {
        uiDevice.wakeUp()
        uiDevice.pressHome()
    }

    @After fun cleanUp() = uninstallTestApps()

    protected fun launchPermissionController() {
        SystemUtil.runWithShellPermissionIdentity {
            instrumentationContext.startActivity(
                Intent(Intent.ACTION_MANAGE_PERMISSIONS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }

        uiDevice.waitForIdle()
    }
}
