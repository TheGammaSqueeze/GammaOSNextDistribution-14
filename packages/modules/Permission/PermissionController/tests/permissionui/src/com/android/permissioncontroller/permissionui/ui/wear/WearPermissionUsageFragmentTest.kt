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
import android.Manifest.permission.READ_CALENDAR
import android.app.AppOpsManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
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
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Tests for [WearPermissionUsageFragment] */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
class WearPermissionUsageFragmentTest : PermissionHub2Test() {
    private val CAMERA_APK =
        "/data/local/tmp/pc-permissionui/PermissionUiUseCameraPermissionApp.apk"
    private val CAMERA_APP_LABEL = "CameraRequestApp"
    private val CAMERA_PREF_LABEL = "Camera"

    private val CALENDAR_APK =
        "/data/local/tmp/pc-permissionui/PermissionUiReadCalendarPermissionApp.apk"
    private val CALENDAR_APP_LABEL = "CalendarRequestApp"
    private val CALENDAR_PREF_LABEL = "Calendar"

    private val TEST_PACKAGE_NAME =
        "com.android.permissioncontroller.tests.appthatrequestpermission"

    private val SHOW_SYSTEM_LABEL = "Show system"
    private val HIDE_SYSTEM_LABEL = "Hide system"
    private val TIMEOUT = 30_000L

    @Before
    fun setup() {
        assumeTrue(isWear())
        wakeUpScreen()
    }

    @Test
    fun testShowSystem() {
        runWithShellPermissionIdentity {
            context.startActivity(
                Intent(Intent.ACTION_REVIEW_PERMISSION_USAGE).apply {
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }

        clickObject(SHOW_SYSTEM_LABEL)
        waitFindObject(By.textContains(HIDE_SYSTEM_LABEL), TIMEOUT)

        clickObject(HIDE_SYSTEM_LABEL)
        waitFindObject(By.textContains(SHOW_SYSTEM_LABEL), TIMEOUT)
    }

    @Test
    fun testTransitToPermissionUsageDetails() {
        install(CAMERA_APK)
        grantPermission(TEST_PACKAGE_NAME, CAMERA)
        accessCamera()

        runWithShellPermissionIdentity {
            context.startActivity(
                Intent(Intent.ACTION_REVIEW_PERMISSION_USAGE).apply {
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }

        clickObject(CAMERA_PREF_LABEL)

        waitFindObject(By.textContains(CAMERA_APP_LABEL), TIMEOUT)
    }

    @Test
    fun testTransitToPermissionApps() {
        install(CALENDAR_APK)
        grantPermission(TEST_PACKAGE_NAME, READ_CALENDAR)

        accessCalendar()

        runWithShellPermissionIdentity {
            context.startActivity(
                Intent(Intent.ACTION_REVIEW_PERMISSION_USAGE).apply {
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }

        clickObject(CALENDAR_PREF_LABEL)

        waitFindObject(By.textContains(CALENDAR_PREF_LABEL), TIMEOUT)
        waitFindObject(By.textContains(CALENDAR_APP_LABEL), TIMEOUT)
    }

    private fun accessCalendar() {
        runWithShellPermissionIdentity {
            eventually {
                assertThat(
                        context.packageManager.getPermissionFlags(
                            READ_CALENDAR,
                            TEST_PACKAGE_NAME,
                            Process.myUserHandle()
                        ) and PackageManager.FLAG_PERMISSION_USER_SENSITIVE_WHEN_GRANTED
                    )
                    .isNotEqualTo(0)
            }

            eventually {
                assertThat(
                        context
                            .getSystemService(AppOpsManager::class.java)
                            .startOp(
                                AppOpsManager.OPSTR_READ_CALENDAR,
                                context.packageManager.getPackageUid(TEST_PACKAGE_NAME, 0),
                                TEST_PACKAGE_NAME,
                                null,
                                null
                            )
                    )
                    .isEqualTo(AppOpsManager.MODE_ALLOWED)
            }
        }
    }

    @After
    fun tearDown() {
        uninstallApp(TEST_PACKAGE_NAME)
        pressHome()
    }
}
