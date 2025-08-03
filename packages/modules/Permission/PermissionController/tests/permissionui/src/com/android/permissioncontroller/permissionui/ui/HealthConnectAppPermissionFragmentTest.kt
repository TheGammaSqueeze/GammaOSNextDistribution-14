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

package com.android.permissioncontroller.permissionui.ui

import android.content.Intent
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.uiautomator.By
import com.android.compatibility.common.util.SystemUtil.eventually
import com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity
import com.android.compatibility.common.util.UiAutomatorUtils2.waitFindObject
import com.android.compatibility.common.util.UiAutomatorUtils2.waitUntilObjectGone
import com.android.permissioncontroller.permissionui.wakeUpScreen
import org.junit.After
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Simple tests for {@link AppPermissionsFragment} for Health Connect behaviors Currently, does NOT
 * run on TV.
 *
 * TODO(b/178576541): Adapt and run on TV. Run with: atest HealthConnectAppPermissionFragmentTest
 */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE, codeName = "UpsideDownCake")
class HealthConnectAppPermissionFragmentTest : BasePermissionUiTest() {
    @Before fun assumeNotTelevision() = assumeFalse(isTelevision)

    @Before
    fun wakeScreenUp() {
        wakeUpScreen()
    }

    @After
    fun uninstallTestApp() {
        uninstallTestApps()
    }
    @Test
    fun usedHealthConnectPermissionsAreListed() {
        installTestAppThatUsesHealthConnectPermission()

        startManageAppPermissionsActivity()

        eventually { waitFindObject(By.text(HEALTH_CONNECT_LABEL)) }
    }

    @Test
    fun invalidUngrantedUsedHealthConnectPermissionsAreNotListed() {
        installInvalidTestAppThatUsesHealthConnectPermission()

        startManageAppPermissionsActivity()

        waitUntilObjectGone(By.text(HEALTH_CONNECT_LABEL), TIMEOUT_SHORT)
    }

    @Test
    fun invalidGrantedUsedHealthConnectPermissionsAreListed() {
        installInvalidTestAppThatUsesHealthConnectPermission()
        grantTestAppPermission(HEALTH_CONNECT_PERMISSION_READ_FLOORS_CLIMBED)

        startManageAppPermissionsActivity()

        eventually { waitFindObject(By.text(HEALTH_CONNECT_LABEL)) }
    }

    private fun startManageAppPermissionsActivity() {
        runWithShellPermissionIdentity {
            instrumentationContext.startActivity(
                Intent(Intent.ACTION_MANAGE_APP_PERMISSIONS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    putExtra(Intent.EXTRA_PACKAGE_NAME, PERM_USER_PACKAGE)
                }
            )
        }
    }

    companion object {
        // Health connect label uses a non breaking space
        private const val HEALTH_CONNECT_LABEL = "Health\u00A0Connect"
        private const val HEALTH_CONNECT_PERMISSION_READ_FLOORS_CLIMBED =
            "android.permission.health.READ_FLOORS_CLIMBED"

        private val TIMEOUT_SHORT = 500L
    }
}
