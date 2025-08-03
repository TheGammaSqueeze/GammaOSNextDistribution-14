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

package android.packageinstaller.install.cts

import android.content.pm.Flags
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageInstaller.SessionParams.PERMISSION_STATE_DEFAULT
import android.content.pm.PackageInstaller.SessionParams.PERMISSION_STATE_DENIED
import android.content.pm.PackageInstaller.SessionParams.PERMISSION_STATE_GRANTED
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.Manifest
import android.content.AttributionSource
import android.permission.PermissionManager
import android.platform.test.annotations.AppModeFull
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.CheckFlagsRule
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import com.android.compatibility.common.util.SystemUtil
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import kotlin.test.assertFailsWith
import org.junit.BeforeClass
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
@AppModeFull(reason = "Instant apps cannot create installer sessions")
class SessionParamsPermissionStateTest : PackageInstallerTestBase() {

    @JvmField
    @Rule
    val mCheckFlagsRule: CheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    companion object {
        private const val FULL_SCREEN_INTENT_APK = "CtsEmptyTestApp_FullScreenIntent.apk"
        private const val NON_EXISTENT_PERMISSION = "android.cts.NON_EXISTENT_PERMISSION"
        private val GET_PERMISSIONS_FLAGS =
            PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong())

        private val permissionManager = context.getSystemService(PermissionManager::class.java)!!

        private val isFsiDefaultGranted by lazy {
            context.packageManager
                .getPermissionInfo(Manifest.permission.USE_FULL_SCREEN_INTENT, 0)
                .protection == PermissionInfo.PROTECTION_NORMAL
        }

        @JvmStatic
        @BeforeClass
        fun copySubclassTestApk() {
            File(TEST_APK_LOCATION, FULL_SCREEN_INTENT_APK).copyTo(
                target = File(context.filesDir, FULL_SCREEN_INTENT_APK),
                overwrite = true
            )
        }

        @JvmStatic
        @BeforeClass
        fun verifyNoGrantRuntimePermission() {
            // Ensure the test doesn't have the grant runtime permission
            assertThat(
                context.checkSelfPermission(
                    Manifest.permission.INSTALL_GRANT_RUNTIME_PERMISSIONS
                )
            ).isEqualTo(PackageManager.PERMISSION_DENIED)
        }

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun parameters() = listOf(
            // Check that installer is allowed to explicitly grant FSI
            Params(
                name = "fullScreenIntentGranted",
                finalPermissionState = mapOf(Manifest.permission.USE_FULL_SCREEN_INTENT to true)
            ) {
                setFinalState(
                    Manifest.permission.USE_FULL_SCREEN_INTENT,
                    PERMISSION_STATE_GRANTED
                )
            },

            // Check that installer is allowed to explicitly deny FSI
            Params(
                name = "fullScreenIntentDenied",
                finalPermissionState = mapOf(Manifest.permission.USE_FULL_SCREEN_INTENT to false)
            ) {
                setFinalState(
                    Manifest.permission.USE_FULL_SCREEN_INTENT,
                    PERMISSION_STATE_DENIED
                )
            },

            // Check that a vanilla session automatically grants/denies FSI to an app declaring it
            Params(
                name = "fullScreenIntentDefault",
                finalPermissionState = mapOf(
                    Manifest.permission.USE_FULL_SCREEN_INTENT to isFsiDefaultGranted,
                ),
            ) {
                setFinalState(
                    Manifest.permission.USE_FULL_SCREEN_INTENT,
                    PERMISSION_STATE_DEFAULT
                )
            },

            // Check that the installer doesn't affect an app that doesn't declare FSI
            listOf(
                PERMISSION_STATE_GRANTED,
                PERMISSION_STATE_DENIED,
                PERMISSION_STATE_DEFAULT,
            ).map {
                Params(
                    name = "fullScreenIntentWithoutAppDeclaration${stateToName(it)}",
                    success = true,
                    testApkName = TEST_APK_NAME,
                    finalPermissionState = mapOf(Manifest.permission.USE_FULL_SCREEN_INTENT to null)
                ) { setFinalState(Manifest.permission.USE_FULL_SCREEN_INTENT, it) }
            },

            // Check that granting/denying a real runtime permission isn't allowed
            listOf(
                PERMISSION_STATE_GRANTED,
                PERMISSION_STATE_DENIED,
            ).map {
                Params(
                    name = "runtimePermission${stateToName(it)}",
                    success = false,
                ) { setFinalState(Manifest.permission.READ_CALENDAR, it) }
            },

            // Check that setting a runtime permission to default is ignored (and thus succeeds)
            Params(
                name = "runtimePermissionDefault",
                finalPermissionState = mapOf(
                    Manifest.permission.USE_FULL_SCREEN_INTENT to isFsiDefaultGranted,
                    Manifest.permission.READ_CALENDAR to false,
                ),
            ) { setFinalState(Manifest.permission.READ_CALENDAR, PERMISSION_STATE_DEFAULT) },

            // Check that setting a permission not known to the system isn't allowed
            listOf(
                PERMISSION_STATE_GRANTED,
                PERMISSION_STATE_DENIED,
            ).map {
                Params(
                    name = "unknownPermission${stateToName(it)}",
                    success = false,
                ) { setFinalState(NON_EXISTENT_PERMISSION, it) }
            },

            // Check that setting an unknown permission to default is ignored (and thus succeeds)
            Params(
                name = "unknownPermissionDefault",
                finalPermissionState = mapOf(
                    Manifest.permission.USE_FULL_SCREEN_INTENT to isFsiDefaultGranted,
                ),
            ) { setFinalState(NON_EXISTENT_PERMISSION, PERMISSION_STATE_DEFAULT) },

            // Check that setting a runtime/unknown permission with the right permission is allowed
            Params(
                name = "runtimePermissionGranted",
                withInstallGrantRuntimePermissions = true,
                finalPermissionState = mapOf(
                    Manifest.permission.USE_FULL_SCREEN_INTENT to isFsiDefaultGranted,
                    Manifest.permission.READ_CALENDAR to true,
                    NON_EXISTENT_PERMISSION to null,
                ),
            ) {
                setFinalState(Manifest.permission.READ_CALENDAR, PERMISSION_STATE_GRANTED)
                    .setFinalState(NON_EXISTENT_PERMISSION, PERMISSION_STATE_GRANTED)
            },
        ).flatMap { if (it is Collection<*>) it else listOf(it) }

        data class Params(
            val name: String,
            var success: Boolean = true,
            val testApkName: String = FULL_SCREEN_INTENT_APK,
            val withInstallGrantRuntimePermissions: Boolean = false,
            val finalPermissionState: Map<String, Boolean?> = emptyMap(),
            val paramsBlock: PackageInstaller.SessionParams.() -> Unit = {},
        ) {
            override fun toString() = "${name}_${if (success) "Success" else "Failure"}"
        }

        private fun stateToName(state: Int) = when (state) {
            PERMISSION_STATE_GRANTED -> "Granted"
            PERMISSION_STATE_DENIED -> "Denied"
            PERMISSION_STATE_DEFAULT -> "Default"
            else -> throw IllegalArgumentException("Unknown state: $state")
        }

        /** Cycles through all of the states to make sure only latest is kept */
        private fun PackageInstaller.SessionParams.setFinalState(
            permissionName: String,
            state: Int
        ) = setPermissionState(permissionName, PERMISSION_STATE_GRANTED)
            .setPermissionState(permissionName, PERMISSION_STATE_DENIED)
            .setPermissionState(permissionName, PERMISSION_STATE_DEFAULT)
            .setPermissionState(permissionName, state)
    }

    @Parameterized.Parameter(0)
    lateinit var params: Params

    @Before
    fun validateParams() {
        if (!params.success) {
            // Ensure that a test case expecting failure has no permission state to assert
            assertThat(params.finalPermissionState).isEmpty()
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_READ_INSTALL_INFO, Flags.FLAG_GET_RESOLVED_APK_PATH)
    fun checkInstall() {
        val block = {
            startInstallationViaSession(
                apkName = params.testApkName,
                paramsBlock = params.paramsBlock,
            )
        }

        if (!params.success) {
            assertFailsWith(SecurityException::class) { block() }
            return
        } else if (params.withInstallGrantRuntimePermissions) {
            SystemUtil.callWithShellPermissionIdentity(
                { block() },
                Manifest.permission.INSTALL_GRANT_RUNTIME_PERMISSIONS
            )
        } else {
            block()
        }

        clickInstallerUIButton(INSTALL_BUTTON_ID)

        val result = getInstallSessionResult()
        assertWithMessage(result.message)
            .that(result.status)
            .isEqualTo(PackageInstaller.STATUS_SUCCESS)

        val packageInfo = assertInstalled(GET_PERMISSIONS_FLAGS)
        params.finalPermissionState.forEach { (permission, granted) ->
            assertPermission(packageInfo, permission, granted)
        }
    }

    private fun assertPermission(packageInfo: PackageInfo, name: String, granted: Boolean?) {
        val permissionIndex = packageInfo.requestedPermissions!!.indexOfFirst { it == name }

        if (granted == null) {
            assertThat(permissionIndex).isEqualTo(-1)
        } else {
            val appInfo = pm.getApplicationInfo(
                TEST_APK_PACKAGE_NAME,
                PackageManager.ApplicationInfoFlags.of(0),
            )

            permissionManager.checkPermissionForPreflight(
                name,
                AttributionSource.Builder(appInfo.uid)
                    .setPackageName(TEST_APK_PACKAGE_NAME)
                    .build(),
            ).let(::assertThat)
                .run {
                    if (granted) {
                        isEqualTo(PermissionManager.PERMISSION_GRANTED)
                    } else {
                        isNotEqualTo(PermissionManager.PERMISSION_GRANTED)
                    }
                }
        }
    }
}
