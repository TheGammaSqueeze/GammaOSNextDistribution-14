/*
 * Copyright 2020 The Android Open Source Project
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

import android.app.Activity
import android.content.Intent
import android.content.Intent.ACTION_INSTALL_PACKAGE
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager.MATCH_DEFAULT_ONLY
import android.net.Uri
import android.platform.test.annotations.AppModeFull
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.util.concurrent.TimeUnit
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@AppModeFull(reason = "Instant apps cannot install packages")
class InstallSourceInfoTest : PackageInstallerTestBase() {
    companion object {
        const val SHELL_PACKAGE_NAME = "com.android.shell"
    }

    private val ourPackageName = context.packageName

    @Before
    fun prepareDevice() {
        // Unlock screen.
        uiDevice.executeShellCommand("input keyevent KEYCODE_WAKEUP")
        // Dismiss keyguard, in case it's set as "Swipe to unlock".
        uiDevice.executeShellCommand("wm dismiss-keyguard")
        // Collapse notifications.
        uiDevice.executeShellCommand("cmd statusbar collapse")
        // Dismiss all system dialogs before launch test.
        uiDevice.executeShellCommand("am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS")
        // Disable AiAi.
        uiDevice.executeShellCommand("settings put secure odi_captions_enabled 0")
    }

    @Test
    fun installViaIntent() {
        val packageInstallerPackageName = getPackageInstallerPackageName()

        val installation = startInstallationViaIntent()
        clickInstallerUIButton(INSTALL_BUTTON_ID)

        // Install should have succeeded
        assertThat(installation.get(TIMEOUT, TimeUnit.MILLISECONDS)).isEqualTo(Activity.RESULT_OK)

        val info = pm.getInstallSourceInfo(TEST_APK_PACKAGE_NAME)
        assertThat(info.installingPackageName).isEqualTo(packageInstallerPackageName)
        assertThat(info.initiatingPackageName).isEqualTo(packageInstallerPackageName)
        assertThat(info.originatingPackageName).isNull()
        assertThat(info.updateOwnerPackageName).isNull()
    }

    @Test
    fun installViaAdb() {
        uiDevice.executeShellCommand("pm install $TEST_APK_LOCATION/$TEST_APK_NAME")

        val info = pm.getInstallSourceInfo(TEST_APK_PACKAGE_NAME)
        assertThat(info.installingPackageName).isNull()
        assertThat(info.initiatingPackageName).isEqualTo(SHELL_PACKAGE_NAME)
        assertThat(info.originatingPackageName).isNull()
        assertThat(info.updateOwnerPackageName).isNull()
        assertThat(info.packageSource).isEqualTo(PackageInstaller.PACKAGE_SOURCE_OTHER)
    }

    @Test
    fun installViaAdbValidInstallerName() {
        val packageInstallerPackageName = getPackageInstallerPackageName()
        uiDevice.executeShellCommand(
                "pm install -i $packageInstallerPackageName $TEST_APK_LOCATION/$TEST_APK_NAME")

        val info = pm.getInstallSourceInfo(TEST_APK_PACKAGE_NAME)
        assertThat(info.installingPackageName).isEqualTo(packageInstallerPackageName)
        assertThat(info.initiatingPackageName).isEqualTo(SHELL_PACKAGE_NAME)
        assertThat(info.originatingPackageName).isNull()
        assertThat(info.updateOwnerPackageName).isNull()
    }

    @Test
    fun installViaAdbInvalidInstallerName() {
        val invalidInstallerPackageName = "invalid"
        uiDevice.executeShellCommand(
                "pm install -i $invalidInstallerPackageName $TEST_APK_LOCATION/$TEST_APK_NAME")

        val info = pm.getInstallSourceInfo(TEST_APK_PACKAGE_NAME)
        // Invalid installerPackageName should have been cleared
        assertThat(info.installingPackageName).isNull()
        assertThat(info.initiatingPackageName).isEqualTo(SHELL_PACKAGE_NAME)
        assertThat(info.originatingPackageName).isNull()
        assertThat(info.updateOwnerPackageName).isNull()
    }

    @Test
    fun installViaSessionByStore() {
        installViaSession(PackageInstaller.PACKAGE_SOURCE_STORE)
    }

    @Test
    fun installViaSessionByLocalFile() {
        installViaSession(PackageInstaller.PACKAGE_SOURCE_LOCAL_FILE)
    }

    @Test
    fun installViaSession() {
        installViaSession(null)
    }

    private fun installViaSession(packageSource: Int?) {
        startInstallationViaSessionWithPackageSource(packageSource)
        clickInstallerUIButton(INSTALL_BUTTON_ID)

        // Install should have succeeded
        val result = getInstallSessionResult()
        assertThat(result.status).isEqualTo(PackageInstaller.STATUS_SUCCESS)
        assertThat(result.preapproval).isFalse()

        val info = pm.getInstallSourceInfo(TEST_APK_PACKAGE_NAME)
        assertThat(info.installingPackageName).isEqualTo(ourPackageName)
        assertThat(info.initiatingPackageName).isEqualTo(ourPackageName)
        assertThat(info.originatingPackageName).isNull()
        assertThat(info.updateOwnerPackageName).isNull()
        if (packageSource != null) {
            assertThat(info.packageSource).isEqualTo(packageSource)
        } else {
            assertThat(info.packageSource).isEqualTo(
                    PackageInstaller.PACKAGE_SOURCE_UNSPECIFIED)
        }
    }

    private fun getPackageInstallerPackageName(): String {
        val installerIntent = Intent(ACTION_INSTALL_PACKAGE)
        installerIntent.setDataAndType(Uri.parse("content://com.example/"),
                "application/vnd.android.package-archive")
        return installerIntent.resolveActivityInfo(pm, MATCH_DEFAULT_ONLY).packageName
    }
}
