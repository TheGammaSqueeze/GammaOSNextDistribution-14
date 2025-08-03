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

package android.permission.cts

import android.Manifest.permission.ACCESS_MEDIA_LOCATION
import android.Manifest.permission.READ_EXTERNAL_STORAGE
import android.Manifest.permission.WRITE_EXTERNAL_STORAGE
import android.app.Instrumentation
import android.app.UiAutomation
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.os.UserHandle
import android.platform.test.annotations.AppModeFull
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.android.compatibility.common.util.SystemUtil
import org.junit.After
import org.junit.Assert
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeNoException
import org.junit.Before
import org.junit.Test

@AppModeFull
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE, codeName = "UpsideDownCake")
class StorageEscalationTest {
    companion object {
        private const val APK_DIRECTORY = "/data/local/tmp/cts-permission"
        const val APP_APK_PATH_28 = "$APK_DIRECTORY/CtsStorageEscalationApp28.apk"
        const val APP_APK_PATH_29_SCOPED = "$APK_DIRECTORY/CtsStorageEscalationApp29Scoped.apk"
        const val APP_APK_PATH_29_FULL = "$APK_DIRECTORY/CtsStorageEscalationApp29Full.apk"
        const val APP_PACKAGE_NAME = "android.permission.cts.storageescalation"
        const val DELAY_TIME_MS: Long = 200
        val permissions =
            listOf<String>(READ_EXTERNAL_STORAGE, WRITE_EXTERNAL_STORAGE, ACCESS_MEDIA_LOCATION)
    }

    private val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.context
    private val uiAutomation: UiAutomation = instrumentation.uiAutomation
    private var secondaryUserId: Int? = null

    @Before
    @After
    fun uninstallApp() {
        SystemUtil.runShellCommand("pm uninstall $APP_PACKAGE_NAME --user ALL")
    }

    private fun installPackage(apk: String) {
        var userString = ""
        secondaryUserId?.let { userId -> userString = " --user $userId" }
        val result = SystemUtil.runShellCommandOrThrow("pm install -r$userString $apk")
        assertTrue(
            "Expected output to contain \"Success\", but was \"$result\"",
            result.contains("Success")
        )
    }

    private fun createSecondaryUser() {
        val createUserOutput: String = SystemUtil.runShellCommand("pm create-user secondary")
        var formatException: Exception? = null
        val userId =
            try {
                createUserOutput.split(" id ".toRegex())[1].trim { it <= ' ' }.toInt()
            } catch (e: Exception) {
                formatException = e
                -1
            }
        assumeNoException("Failed to parse userId from $createUserOutput", formatException)
        SystemUtil.runShellCommand("am start-user -w $userId")
        secondaryUserId = userId
    }

    @After
    fun removeSecondaryUser() {
        secondaryUserId?.let { userId ->
            SystemUtil.runShellCommand("pm remove-user $userId")
            secondaryUserId = null
        }
    }

    private fun grantStoragePermissions() {
        for (permName in permissions) {
            var user = Process.myUserHandle()
            secondaryUserId?.let { user = UserHandle.of(it) }
            uiAutomation.grantRuntimePermissionAsUser(APP_PACKAGE_NAME, permName, user)
        }
    }

    private fun assertStoragePermissionState(granted: Boolean) {
        for (permName in permissions) {
            var userContext = context
            secondaryUserId?.let { userId ->
                SystemUtil.runWithShellPermissionIdentity {
                    userContext =
                        context.createPackageContextAsUser(
                            APP_PACKAGE_NAME,
                            0,
                            UserHandle.of(userId)
                        )
                }
            }
            Assert.assertEquals(
                granted,
                userContext.packageManager.checkPermission(permName, APP_PACKAGE_NAME) ==
                    PackageManager.PERMISSION_GRANTED
            )
        }
    }

    @Test
    fun testCannotEscalateWithSdkDowngrade() {
        runStorageEscalationTest(APP_APK_PATH_29_SCOPED, APP_APK_PATH_28)
    }

    @Test
    fun testCannotEscalateWithNewManifestLegacyRequest() {
        runStorageEscalationTest(APP_APK_PATH_29_SCOPED, APP_APK_PATH_29_FULL)
    }

    @Test
    fun testCannotEscalateWithSdkDowngradeSecondary() {
        createSecondaryUser()
        runStorageEscalationTest(APP_APK_PATH_29_SCOPED, APP_APK_PATH_28)
    }

    @Test
    fun testCannotEscalateWithNewManifestLegacyRequestSecondary() {
        createSecondaryUser()
        runStorageEscalationTest(APP_APK_PATH_29_SCOPED, APP_APK_PATH_29_FULL)
    }

    private fun runStorageEscalationTest(startPackageApk: String, finishPackageApk: String) {
        installPackage(startPackageApk)
        grantStoragePermissions()
        assertStoragePermissionState(granted = true)
        installPackage(finishPackageApk)
        // permission revoke is async, so wait a short period
        Thread.sleep(DELAY_TIME_MS)
        assertStoragePermissionState(granted = false)
    }
}
