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
package android.healthconnect.cts.lib

import android.Manifest.permission.GRANT_RUNTIME_PERMISSIONS
import android.content.Context
import android.content.Intent
import android.content.Intent.EXTRA_PACKAGE_NAME
import android.content.pm.PackageManager.EXTRA_REQUEST_PERMISSIONS_NAMES
import android.health.connect.HealthConnectManager
import android.healthconnect.cts.lib.UiTestUtils.TEST_APP_PACKAGE_NAME
import android.healthconnect.cts.lib.UiTestUtils.skipOnboardingIfAppears
import com.android.compatibility.common.util.SystemUtil
import com.android.compatibility.common.util.UiAutomatorUtils2.getUiDevice

/** A class that provides a way to launch the Health Connect [MainActivity] in tests. */
object ActivityLauncher {
    /** Launches the Main activity and exits it once [block] completes. */
    fun Context.launchMainActivity(block: () -> Unit) {
        val intent =
            Intent("android.health.connect.action.HEALTH_HOME_SETTINGS")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
        executeBlockAndExit(block) {
            startActivity(intent)
            skipOnboardingIfAppears()
        }
    }

    fun Context.launchDataActivity(block: () -> Unit) {
        val intent =
            Intent("android.health.connect.action.MANAGE_HEALTH_DATA")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
        executeBlockAndExit(block) {
            startActivity(intent)
            skipOnboardingIfAppears()
        }
    }

    fun Context.launchRequestPermissionActivity(
        packageName: String = TEST_APP_PACKAGE_NAME,
        permissions: List<String>,
        block: () -> Unit
    ) {
        val intent =
            Intent(HealthConnectManager.ACTION_REQUEST_HEALTH_PERMISSIONS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                putExtra(EXTRA_REQUEST_PERMISSIONS_NAMES, permissions.toTypedArray())
                putExtra(EXTRA_PACKAGE_NAME, packageName)
            }
        executeBlockAndExit(block) {
            SystemUtil.runWithShellPermissionIdentity(
                {
                    startActivity(intent)
                    skipOnboardingIfAppears()
                },
                GRANT_RUNTIME_PERMISSIONS)
        }
    }

    private fun executeBlockAndExit(block: () -> Unit, launchActivity: () -> Unit) {
        val uiDevice = getUiDevice()
        uiDevice.waitForIdle()
        launchActivity()
        uiDevice.waitForIdle()
        block()
        uiDevice.pressBack()
        uiDevice.waitForIdle()
    }
}
