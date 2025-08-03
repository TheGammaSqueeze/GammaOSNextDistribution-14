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

import android.content.pm.PackageManager
import android.content.pm.PermissionGroupInfo
import android.content.pm.PermissionInfo
import android.os.Build
import android.platform.test.annotations.AppModeFull
import android.platform.test.annotations.AsbSecurityTest
import androidx.test.InstrumentationRegistry
import androidx.test.filters.SdkSuppress
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner
import com.android.compatibility.common.util.ShellUtils.runShellCommand
import com.android.compatibility.common.util.SystemUtil.runShellCommandOrThrow
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

private const val APK_PATH = "/data/local/tmp/cts-permission/"

private const val APK_DEFINING_PERM_A = "${APK_PATH}CtsAppThatDefinesPermissionA.apk"
private const val APK_ALSO_DEFINING_PERM_A = "${APK_PATH}CtsAppThatAlsoDefinesPermissionA.apk"
private const val APK_ALSO_DEFINING_PERM_A_DIFFERENT_CERT =
    "${APK_PATH}CtsAppThatAlsoDefinesPermissionADifferentCert.apk"
private const val APK_ALSO_DEFINING_PERM_GROUP_A_DIFFERENT_CERT =
    "${APK_PATH}CtsAppThatAlsoDefinesPermissionGroupADifferentCert.apk"
private const val APK_ALSO_DEFINING_PERM_GROUP_A_DIFFERENT_CERT_SDK_30 =
    "${APK_PATH}CtsAppThatAlsoDefinesPermissionGroupADifferentCert30.apk"
private const val APK_DEFINING_PERM_WITH_INVALID_GROUP =
    "${APK_PATH}CtsAppThatDefinesPermissionWithInvalidGroup.apk"
private const val APK_DEFINING_PERM_WITH_INVALID_GROUP_SDK_30 =
    "${APK_PATH}CtsAppThatDefinesPermissionWithInvalidGroup30.apk"
private const val APK_DEFINING_PERM_IN_PLATFORM_GROUP =
    "${APK_PATH}CtsAppThatDefinesPermissionInPlatformGroup.apk"

private const val APP_DEFINING_PERM_A = "android.permission.cts.appthatdefinespermissiona"
private const val APP_ALSO_DEFINING_PERM_A = "android.permission.cts.appthatalsodefinespermissiona"
private const val APP_ALSO_DEFINING_PERM_A_DIFFERENT_CERT =
    "android.permission.cts.appthatdefinespermissiona.differentcert"
private const val APP_ALSO_DEFINING_PERM_GROUP_A_DIFFERENT_CERT =
    "android.permission.cts.appthatdefinespermissiongroupa.differentcert"
private const val APP_ALSO_DEFINING_PERM_GROUP_A_DIFFERENT_CERT_SDK_30 =
    "android.permission.cts.appthatdefinespermissiongroupa.differentcert30"
private const val APP_DEFINING_PERM_IN_PLATFORM_GROUP =
    "android.permission.cts.appthatdefinespermissioninplatformgroup"
private const val APP_DEFINING_PERM_WITH_INVALID_GROUP =
    "android.permission.cts.appthatdefinespermissionwithinvalidgroup"
private const val APP_DEFINING_PERM_WITH_INVALID_GROUP_SDK_30 =
    "android.permission.cts.appthatdefinespermissionwithinvalidgroup30"

private const val PERM_A = "com.android.cts.duplicatepermission.permA"
private const val GROUP_A = "com.android.cts.duplicatepermission.groupA"
private const val INVALID_GROUP = "com.android.cts.duplicatepermission.invalid"

/**
 * Test cases where packages
 * - define the same permission or
 * - define the same permission group
 * - define permissions in a group defined by another package
 */
@AppModeFull(reason = "Tests properties of other app. Instant apps cannot interact with other apps")
@RunWith(AndroidJUnit4ClassRunner::class)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE, codeName = "UpsideDownCake")
class DuplicatePermissionDefinitionsTest {
    private val pm = InstrumentationRegistry.getTargetContext().packageManager

    private fun install(apk: String) {
        runShellCommandOrThrow("pm install $apk")
    }

    private fun uninstall(app: String) {
        runShellCommand("pm uninstall $app")
    }

    private val allPackages: List<String>
        get() = pm.getInstalledPackages(0).map { it.packageName }

    private val permAInfo: PermissionInfo
        get() = pm.getPermissionInfo(PERM_A, 0)!!

    private val groupAInfo: PermissionGroupInfo
        get() = pm.getPermissionGroupInfo(GROUP_A, 0)!!

    @Test
    fun canInstallAppsDefiningSamePermissionWhenSameCert() {
        install(APK_DEFINING_PERM_A)
        install(APK_ALSO_DEFINING_PERM_A)

        assertThat(allPackages).containsAtLeast(APP_DEFINING_PERM_A, APP_ALSO_DEFINING_PERM_A)

        assertThat(permAInfo.packageName).isEqualTo(APP_DEFINING_PERM_A)
    }

    @Test
    fun cannotInstallAppsDefiningSamePermissionWhenDifferentCert() {
        install(APK_DEFINING_PERM_A)
        install(APK_ALSO_DEFINING_PERM_A_DIFFERENT_CERT)

        assertThat(allPackages).contains(APP_DEFINING_PERM_A)
        assertThat(allPackages).doesNotContain(APP_ALSO_DEFINING_PERM_A_DIFFERENT_CERT)

        assertThat(permAInfo.packageName).isEqualTo(APP_DEFINING_PERM_A)
    }

    @Test
    fun canInstallAppsDefiningSamePermissionGroupWhenDifferentCertIfSdk30() {
        install(APK_DEFINING_PERM_A)
        install(APK_ALSO_DEFINING_PERM_GROUP_A_DIFFERENT_CERT_SDK_30)

        assertThat(allPackages)
            .containsAtLeast(
                APP_DEFINING_PERM_A,
                APP_ALSO_DEFINING_PERM_GROUP_A_DIFFERENT_CERT_SDK_30
            )

        assertThat(groupAInfo.packageName).isEqualTo(APP_DEFINING_PERM_A)
    }

    @Test
    @AsbSecurityTest(cveBugId = [146211400])
    fun cannotInstallAppsDefiningSamePermissionGroupWhenDifferentCert() {
        install(APK_DEFINING_PERM_A)
        install(APK_ALSO_DEFINING_PERM_GROUP_A_DIFFERENT_CERT)

        assertThat(allPackages).contains(APP_DEFINING_PERM_A)
        assertThat(allPackages).doesNotContain(APP_ALSO_DEFINING_PERM_GROUP_A_DIFFERENT_CERT)

        assertThat(groupAInfo.packageName).isEqualTo(APP_DEFINING_PERM_A)
    }

    // This is the same as cannotInstallAppsDefiningSamePermissionGroupWhenDifferentCert but this
    // case is allowed as the package that originally defined the group is a platform.
    @Test
    fun canInstallAppsDefiningPermissionInPlatformGroup() {
        install(APK_DEFINING_PERM_IN_PLATFORM_GROUP)

        assertThat(allPackages).contains(APP_DEFINING_PERM_IN_PLATFORM_GROUP)

        assertThat(permAInfo.packageName).isEqualTo(APP_DEFINING_PERM_IN_PLATFORM_GROUP)
        assertThat(permAInfo.group).isEqualTo(android.Manifest.permission_group.CAMERA)
        assertThat(
                pm.getPermissionGroupInfo(android.Manifest.permission_group.CAMERA, 0)!!.packageName
            )
            .isEqualTo("android")
    }

    @Test
    fun canInstallAppsDefiningPermissionWithInvalidGroupSdk30() {
        install(APK_DEFINING_PERM_WITH_INVALID_GROUP_SDK_30)

        assertThat(allPackages).contains(APP_DEFINING_PERM_WITH_INVALID_GROUP_SDK_30)

        assertThat(permAInfo.packageName).isEqualTo(APP_DEFINING_PERM_WITH_INVALID_GROUP_SDK_30)
        assertThat(permAInfo.group).isEqualTo(INVALID_GROUP)
    }

    @Test(expected = PackageManager.NameNotFoundException::class)
    @AsbSecurityTest(cveBugId = [146211400])
    fun cannotInstallAppsDefiningPermissionWithInvalidGroup() {
        install(APK_DEFINING_PERM_WITH_INVALID_GROUP)

        assertThat(allPackages).doesNotContain(APP_DEFINING_PERM_WITH_INVALID_GROUP)

        // throws a NameNotFoundException as perm info does not exist
        permAInfo
    }

    @After
    fun uninstallTestApps() {
        uninstall(APP_DEFINING_PERM_A)
        uninstall(APP_ALSO_DEFINING_PERM_A)
        uninstall(APP_ALSO_DEFINING_PERM_A_DIFFERENT_CERT)
        uninstall(APP_ALSO_DEFINING_PERM_GROUP_A_DIFFERENT_CERT)
        uninstall(APP_ALSO_DEFINING_PERM_GROUP_A_DIFFERENT_CERT_SDK_30)
        uninstall(APP_DEFINING_PERM_IN_PLATFORM_GROUP)
        uninstall(APP_DEFINING_PERM_WITH_INVALID_GROUP)
        uninstall(APP_DEFINING_PERM_WITH_INVALID_GROUP_SDK_30)
    }
}
