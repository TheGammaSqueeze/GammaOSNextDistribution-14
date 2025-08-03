/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.security.cts

import android.app.role.RoleManager
import android.content.pm.PackageManager
import android.os.Process
import android.platform.test.annotations.AsbSecurityTest
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.compatibility.common.util.SystemUtil.callWithShellPermissionIdentity
import com.android.compatibility.common.util.SystemUtil.runShellCommand
import com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity
import com.android.compatibility.common.util.mainline.MainlineModule
import com.android.compatibility.common.util.mainline.ModuleDetector
import com.android.sts.common.util.StsExtraBusinessLogicTestCase
import com.google.common.truth.Truth.assertThat
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.function.Consumer
import org.junit.After
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RolePermissionOverrideTest : StsExtraBusinessLogicTestCase {
    private val context = getInstrumentation().targetContext
    private val packageManager = context.packageManager
    private val roleManager = context.getSystemService(RoleManager::class.java)!!
    private val user = Process.myUserHandle()

    constructor() : super()

    @Before
    fun setUp() {
        installPackage(TEST_APP_APK_PATH)
    }

    @After
    fun tearDown() {
        uninstallPackage(TEST_APP_PACKAGE_NAME)
    }

    @AsbSecurityTest(cveBugId = [202312327])
    @Test
    fun systemRoleDoesNotOverrideUserRevokedPermission() {
        assumeFalse(
            ModuleDetector.moduleIsPlayManaged(
                packageManager, MainlineModule.PERMISSION_CONTROLLER_APEX
            )
        )
        assumeTrue(roleManager.isRoleAvailable(ROLE_SYSTEM_SPEECH_RECOGNIZER))
        val systemSpeechRecognizerPackageName = getRoleHolder(ROLE_SYSTEM_SPEECH_RECOGNIZER)
        if (systemSpeechRecognizerPackageName != null) {
            assertPermissionState(
                systemSpeechRecognizerPackageName, android.Manifest.permission.RECORD_AUDIO, true
            )
        }
        assertPermissionState(
            TEST_APP_PACKAGE_NAME, android.Manifest.permission.RECORD_AUDIO, false
        )

        runWithShellPermissionIdentity {
            packageManager.updatePermissionFlags(
                android.Manifest.permission.RECORD_AUDIO, TEST_APP_PACKAGE_NAME,
                PackageManager.FLAG_PERMISSION_USER_SET, PackageManager.FLAG_PERMISSION_USER_SET,
                user
            )
        }
        setBypassingRoleQualification(true)
        try {
            addRoleHolder(ROLE_SYSTEM_SPEECH_RECOGNIZER, TEST_APP_PACKAGE_NAME)

            assertPermissionState(
                TEST_APP_PACKAGE_NAME, android.Manifest.permission.RECORD_AUDIO, false
            )
        } finally {
            setBypassingRoleQualification(false)
        }
    }

    private fun installPackage(apkPath: String) {
        runShellCommand("pm install -r --user ${user.identifier} $apkPath")
    }

    private fun uninstallPackage(packageName: String) {
        runShellCommand("pm uninstall -r --user ${user.identifier} $packageName")
    }

    private fun getRoleHolders(roleName: String): List<String> =
        callWithShellPermissionIdentity { roleManager.getRoleHolders(roleName) }

    private fun getRoleHolder(roleName: String): String? = getRoleHolders(roleName).firstOrNull()

    private fun setBypassingRoleQualification(bypassRoleQualification: Boolean) {
        runWithShellPermissionIdentity {
            roleManager.setBypassingRoleQualification(bypassRoleQualification)
        }
    }

    private fun addRoleHolder(roleName: String, packageName: String) {
        val future = CallbackFuture()
        runWithShellPermissionIdentity {
            roleManager.addRoleHolderAsUser(
                roleName, packageName, 0, user, context.mainExecutor, future
            )
        }
        assertThat(future.get(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)).isEqualTo(true)
    }

    private fun assertPermissionState(
        packageName: String,
        permissionName: String,
        isGranted: Boolean
    ) {
        assertThat(packageManager.checkPermission(permissionName, packageName)).isEqualTo(
            if (isGranted) PackageManager.PERMISSION_GRANTED else PackageManager.PERMISSION_DENIED
        )
    }

    companion object {
        private const val TEST_APP_APK_PATH =
            "/data/local/tmp/cts/security/RolePermissionOverrideTestApp.apk"
        private const val TEST_APP_PACKAGE_NAME =
            "android.security.cts.rolepermissionoverridetestapp"

        private const val ROLE_SYSTEM_SPEECH_RECOGNIZER =
            "android.app.role.SYSTEM_SPEECH_RECOGNIZER"

        private const val TIMEOUT_MILLIS = 15_000L
    }

    private class CallbackFuture : CompletableFuture<Boolean>(), Consumer<Boolean> {
        override fun accept(successful: Boolean) {
            complete(successful)
        }
    }
}
