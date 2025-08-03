/*
 * Copyright (C) 2018 The Android Open Source Project
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

import android.app.Activity.RESULT_CANCELED
import android.app.Activity.RESULT_OK
import android.content.Intent
import android.content.pm.InstallSourceInfo
import android.net.Uri
import android.platform.test.annotations.AppModeFull
import androidx.test.runner.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import com.android.bedstead.harrier.annotations.RequireAdbRoot
import com.android.bedstead.nene.TestApis
import com.android.bedstead.nene.userrestrictions.CommonUserRestrictions.DISALLOW_INSTALL_APPS
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@AppModeFull(reason = "Instant apps cannot install packages")
class IntentTest : PackageInstallerTestBase() {

    companion object {
        // An invalid package name that exceeds the maximum file name length.
        const val LONG_PACKAGE_NAME = "android.packageinstaller.install.cts.invalidname." +
                "27jEBRNRG3ozwBsGr1sVIM9U0bVTI2TdyIyeRkZgW4JrJefwNIBAmCg4AzqXiCvG6JjqA0u" +
                "TCWSFu2YqAVxVdiRKAay19k5VFlSaM7QW9uhvlrLQqsTW01ofFzxNDbp2QfIFHZR6rebKzK" +
                "Bz6byQFM0DYQnYMwFWXjWkMPNdqkRLykoFLyBup53G68k2n8wl27jEBRNRG3ozwBsGr"
        const val NO_INSTALL_APPS_RESTRICTION_TEXT = "This user is not allowed to install apps"
        const val TIMEOUT = 60000L
    }

    @After
    fun disableSecureFrp() {
        setSecureFrp(false)
    }

    /**
     * Check that we can install an app via a package-installer intent
     */
    @Test
    fun confirmInstallation() {
        val installation = startInstallationViaIntent()
        clickInstallerUIButton(INSTALL_BUTTON_ID)

        // Install should have succeeded
        assertEquals(RESULT_OK, installation.get(TIMEOUT, TimeUnit.MILLISECONDS))
        assertInstalled()
    }

    /**
     * Install an app via a package-installer intent, but then cancel it when the package installer
     * pops open.
     */
    @Test
    fun cancelInstallation() {
        val installation = startInstallationViaIntent()
        clickInstallerUIButton(CANCEL_BUTTON_ID)

        // Install should have been aborted
        assertEquals(RESULT_CANCELED, installation.get(TIMEOUT, TimeUnit.MILLISECONDS))
        assertNotInstalled()
    }

    /**
     * Install an app via a package-installer intent, and assign itself as the installer.
     */
    @Test
    fun installWithCallingInstallerPackageName() {
        val intent = getInstallationIntent()
        intent.putExtra(Intent.EXTRA_INSTALLER_PACKAGE_NAME, context.opPackageName)
        val installation = startInstallationViaIntent(intent)
        clickInstallerUIButton(INSTALL_BUTTON_ID)

        // Install should have succeeded, and system will use the given installer package name
        // in EXTRA_INSTALLER_PACKAGE_NAME as the installer.
        assertEquals(RESULT_OK, installation.get(TIMEOUT, TimeUnit.MILLISECONDS))
        assertEquals(context.opPackageName, getInstallSourceInfo().installingPackageName)
    }

    /**
     * Install an app via a package-installer intent, but assign another package as installer
     * package name.
     */
    @Ignore("b/317736655")
    @Test
    fun installWithAnotherInstallerPackageName() {
        val intent = getInstallationIntent()
        intent.putExtra(Intent.EXTRA_INSTALLER_PACKAGE_NAME, context.opPackageName + ".another")
        val installation = startInstallationViaIntent(intent)
        clickInstallerUIButton(INSTALL_BUTTON_ID)

        // Install should have succeeded, but system won't use the given installer package name
        // in EXTRA_INSTALLER_PACKAGE_NAME as the installer.
        assertEquals(RESULT_OK, installation.get(TIMEOUT, TimeUnit.MILLISECONDS))
        assertEquals(getInstallSourceInfo().initiatingPackageName,
                getInstallSourceInfo().installingPackageName)
    }

    /**
     * Install an app via a package-installer intent, but assign an invalid installer
     * package name which exceeds the maximum file name length.
     */
    @Test
    fun installWithLongInstallerPackageName() {
        val intent = getInstallationIntent()
        intent.putExtra(Intent.EXTRA_INSTALLER_PACKAGE_NAME, LONG_PACKAGE_NAME)
        val installation = startInstallationViaIntent(intent)
        clickInstallerUIButton(INSTALL_BUTTON_ID)

        // Install should have succeeded, but system won't use the given installer package name
        // in EXTRA_INSTALLER_PACKAGE_NAME as the installer.
        assertEquals(RESULT_OK, installation.get(TIMEOUT, TimeUnit.MILLISECONDS))
        assertEquals(getInstallSourceInfo().initiatingPackageName,
                getInstallSourceInfo().installingPackageName)
    }

    /**
     * Make sure that an already installed app can be reinstalled via a "package" uri
     */
    @Test
    fun reinstallViaPackageUri() {
        // Regular install
        confirmInstallation()

        // Reinstall
        val intent = Intent(Intent.ACTION_INSTALL_PACKAGE)
        intent.data = Uri.fromParts("package", TEST_APK_PACKAGE_NAME, null)
        intent.putExtra(Intent.EXTRA_RETURN_RESULT, true)
        intent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION

        val reinstall = installDialogStarter.activity.startActivityForResult(intent)

        clickInstallerUIButton(INSTALL_BUTTON_ID)

        // Install should have succeeded
        assertEquals(RESULT_OK, reinstall.get(TIMEOUT, TimeUnit.MILLISECONDS))
        assertInstalled()
    }

    /**
     * Check that we can't install an app via a package-installer intent if Secure FRP is enabled
     */
    @Test
    fun packageNotInstalledSecureFrp() {
        setSecureFrp(true)
        try {
            val installation = startInstallationViaIntent()
            clickInstallerUIButton(INSTALL_BUTTON_ID)

            // Install should not have succeeded
            assertNotInstalled()
        } finally {
            setSecureFrp(false)
        }
    }

    @Test
    @RequireAdbRoot
    @Ignore("b/322830652: Test suite runs on non-rooted devices only.")
    fun disallowInstallApps_installFails() {
        try {
            TestApis.devicePolicy().userRestrictions().set(DISALLOW_INSTALL_APPS, true)

            val installation = startInstallationViaIntent()

            assertNotNull(
                "Error dialog not shown", uiDevice.wait(
                    Until.findObject(By.text(NO_INSTALL_APPS_RESTRICTION_TEXT)), TIMEOUT
                )
            )
            clickInstallerUIButton(INSTALL_BUTTON_ID)

            assertEquals(RESULT_CANCELED, installation.get(TIMEOUT, TimeUnit.MILLISECONDS))

        } finally {
            TestApis.devicePolicy().userRestrictions().set(DISALLOW_INSTALL_APPS, false)
        }
    }

    private fun getInstallSourceInfo(): InstallSourceInfo {
        return pm.getInstallSourceInfo(TEST_APK_PACKAGE_NAME)
    }
}
