/*
 * Copyright 2023 The Android Open Source Project
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

import android.Manifest
import android.content.Context
import android.content.pm.PackageInstaller
import android.platform.test.annotations.AppModeFull
import androidx.test.InstrumentationRegistry
import com.android.bedstead.harrier.BedsteadJUnit4
import com.android.bedstead.harrier.DeviceState
import com.android.bedstead.harrier.annotations.EnsureHasSecondaryUser
import com.android.bedstead.nene.users.UserReference
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@EnsureHasSecondaryUser
@RunWith(BedsteadJUnit4::class)
@AppModeFull(reason = "Instant apps cannot install packages")
class InstallSourceInfoMultiUserTest : PackageInstallerTestBase() {
    companion object {
        @JvmField
        @ClassRule
        @Rule
        val deviceState = DeviceState()
    }

    private lateinit var primaryUser: UserReference
    private lateinit var secondaryUser: UserReference

    @Before
    fun setUp() {
        primaryUser = deviceState.primaryUser()
        secondaryUser = deviceState.secondaryUser()
        val ownPackageName = context.packageName
        uiDevice.executeShellCommand(
                "pm install-existing --user ${primaryUser.id()} $ownPackageName")
        uiDevice.executeShellCommand(
                "pm install-existing --user ${secondaryUser.id()} $ownPackageName")
    }

    @After
    fun tearDown() {
        uiDevice.executeShellCommand("pm uninstall $TEST_APK_PACKAGE_NAME")
    }

    @Test
    fun installOnPrimaryUser() {
        installOnUserAndCheckInfo(primaryUser)
    }

    @Test
    fun installOnSecondaryUser() {
        installOnUserAndCheckInfo(secondaryUser)
    }

    private fun installOnUserAndCheckInfo(user: UserReference) {
        uiDevice.executeShellCommand(
                "pm install --user ${user.id()} $TEST_APK_LOCATION/$TEST_APK_NAME")
        val uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation()
        uiAutomation.adoptShellPermissionIdentity(Manifest.permission.INTERACT_ACROSS_USERS_FULL)
        val userContext: Context = context.createContextAsUser(user.userHandle(), 0)
        val info = userContext.packageManager.getInstallSourceInfo(TEST_APK_PACKAGE_NAME)
        uiAutomation.dropShellPermissionIdentity()
        // Target InstallSourceInfo should not be null regardless of the current running user
        assertThat(info).isNotNull()
        assertThat(info.installingPackageName).isNull()
        assertThat(info.initiatingPackageName).isEqualTo("com.android.shell")
        assertThat(info.originatingPackageName).isNull()
        assertThat(info.updateOwnerPackageName).isNull()
        assertThat(info.packageSource).isEqualTo(PackageInstaller.PACKAGE_SOURCE_OTHER)
    }
}
