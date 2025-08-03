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

import android.Manifest
import android.content.pm.PackageManager
import android.platform.test.annotations.AppModeFull
import androidx.test.InstrumentationRegistry
import com.android.bedstead.harrier.BedsteadJUnit4
import com.android.bedstead.harrier.annotations.EnsureHasSecondaryUser
import com.android.bedstead.nene.users.UserReference
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@EnsureHasSecondaryUser
@RunWith(BedsteadJUnit4::class)
@AppModeFull(reason = "Instant apps cannot create installer sessions")
class UpdateOwnershipEnforcementMultiUserTest : UpdateOwnershipEnforcementTestBase() {

    private lateinit var currentUser: UserReference
    private lateinit var otherUser: UserReference

	// TODO: should not use primary user or current user, but rather the user running the test and
	// other user. Otherwise, it won't support well devices that support headless system user mode
	// and / or visible background users (the test will pass, but it woudn't be reflecting the
	// reality of these devices)
    /**
     * Create another user for testing, and install the test installer package on both of them.
     */
    @Before
    fun cacheUsers() {
        var primaryUser = deviceState.primaryUser()
        if (primaryUser.id() == context.userId) {
            currentUser = primaryUser
            otherUser = deviceState.secondaryUser()
        } else {
            currentUser = UserReference.of(context.user)
            otherUser = primaryUser
        }
        installPackage(TEST_INSTALLER_APK_NAME)
    }

    /**
     * Checks that we clear the update owner if the installer doesn't request the update ownership
     * on the initial installation of current user while the app is already installed on another
     * user and regardless the existing update owner.
     */
    @Test
    fun installWithoutRequestingUpdateOwnership_hasNoUpdateOwner() {
        installTestPackage(
                "--user ${currentUser.id()} --update-ownership -i $TEST_INSTALLER_APK_PACKAGE_NAME"
        )
        var sourceInfo = pm.getInstallSourceInfo(TEST_APK_PACKAGE_NAME)
        assertEquals(TEST_INSTALLER_APK_PACKAGE_NAME, sourceInfo.updateOwnerPackageName)

        installTestPackage("--user ${otherUser.id()} -i $TEST_INSTALLER_APK_PACKAGE_NAME")
        sourceInfo = pm.getInstallSourceInfo(TEST_APK_PACKAGE_NAME)
        assertEquals(null, sourceInfo.updateOwnerPackageName)
    }

    /**
     * Checks that we keep the update owner if the installer request the update ownership on
     * the initial installation of current user while the app is already installed on another
     * user and the update owner is the same.
     */
    @Test
    fun installWithRequestingUpdateOwnership_updateOwnerIsTheSame_ownerRetained() {
        installTestPackage(
                "--user ${currentUser.id()} --update-ownership -i $TEST_INSTALLER_APK_PACKAGE_NAME"
        )
        var sourceInfo = pm.getInstallSourceInfo(TEST_APK_PACKAGE_NAME)
        assertEquals(TEST_INSTALLER_APK_PACKAGE_NAME, sourceInfo.updateOwnerPackageName)

        installTestPackage(
                "--user ${otherUser.id()} --update-ownership -i $TEST_INSTALLER_APK_PACKAGE_NAME"
        )
        sourceInfo = pm.getInstallSourceInfo(TEST_APK_PACKAGE_NAME)
        assertEquals(TEST_INSTALLER_APK_PACKAGE_NAME, sourceInfo.updateOwnerPackageName)
    }

    /**
     * Checks that we clear the update owner if the installer request the update ownership on
     * the initial installation of current user while the app is already installed on another
     * user and the update owner isn't the same.
     */
    @Test
    fun installWithRequestingUpdateOwnership_updateOwnerIsNotTheSame_hasNoUpdateOwner() {
        installTestPackage(
                "--user ${currentUser.id()} --update-ownership -i $TEST_INSTALLER_APK_PACKAGE_NAME"
        )
        var sourceInfo = pm.getInstallSourceInfo(TEST_APK_PACKAGE_NAME)
        assertEquals(TEST_INSTALLER_APK_PACKAGE_NAME, sourceInfo.updateOwnerPackageName)

        installTestPackage(
                "--user ${otherUser.id()} --update-ownership -i " + context.opPackageName
        )
        sourceInfo = pm.getInstallSourceInfo(TEST_APK_PACKAGE_NAME)
        assertEquals(null, sourceInfo.updateOwnerPackageName)
    }

    /**
     * Checks that we keep no update owner if the installer request the update ownership on
     * the initial installation of current user while the app is already installed on another
     * user and doesn't have the update owner.
     */
    @Test
    fun installWithRequestingUpdateOwnership_noUpdateOwnerIsSet_hasNoUpdateOwner() {
        installTestPackage("--user ${currentUser.id()} -i $TEST_INSTALLER_APK_PACKAGE_NAME")
        var sourceInfo = pm.getInstallSourceInfo(TEST_APK_PACKAGE_NAME)
        assertEquals(null, sourceInfo.updateOwnerPackageName)

        installTestPackage(
                "--user ${otherUser.id()} --update-ownership -i $TEST_INSTALLER_APK_PACKAGE_NAME"
        )
        sourceInfo = pm.getInstallSourceInfo(TEST_APK_PACKAGE_NAME)
        assertEquals(null, sourceInfo.updateOwnerPackageName)
    }

    /**
     * Checks that we clear the update owner when install existing package and the existing
     * update owner isn't the same.
     */
    @Test
    fun installExistingPackage_updateOwnerIsNotTheSame_hasNoUpdateOwner() {
        installTestPackage(
                "--user ${otherUser.id()} --update-ownership -i $TEST_INSTALLER_APK_PACKAGE_NAME"
        )

        try {
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .adoptShellPermissionIdentity(Manifest.permission.INSTALL_PACKAGES)
            installExistingTestPackage()
            var sourceInfo = pm.getInstallSourceInfo(TEST_APK_PACKAGE_NAME)
            assertEquals(null, sourceInfo.updateOwnerPackageName)
        } finally {
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .dropShellPermissionIdentity()
        }
    }

    /**
     * Checks that we keep the update owner when install existing package and the existing
     * update owner is the same.
     */
    @Test
    fun installExistingPackage_updateOwnerIsTheSame_ownerRetained() {
        // Since installer/update owner are only assigned with existing package,
        // install this package to test user as well.
        uiDevice.executeShellCommand(
                "pm install-existing --wait --user ${otherUser.id()} " + context.opPackageName
        )
        // The command would only hang if the package was installed for the user before.
        // Second call will guarantee the buggy codepath to be triggered.
        uiDevice.executeShellCommand(
                "pm install-existing --wait --user ${otherUser.id()} " + context.opPackageName
        )
        installTestPackage(
                "--user ${otherUser.id()} --update-ownership -i " + context.opPackageName
        )

        try {
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .adoptShellPermissionIdentity(Manifest.permission.INSTALL_PACKAGES)
            installExistingTestPackage()
            var sourceInfo = pm.getInstallSourceInfo(TEST_APK_PACKAGE_NAME)
            assertEquals(context.opPackageName, sourceInfo.updateOwnerPackageName)
        } finally {
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .dropShellPermissionIdentity()
        }
    }

    /**
     * Checks that we keep no update owner when install existing package and it doesn't
     * have existing update owner.
     */
    @Test
    fun installExistingPackage_noUpdateOwnerIsSet_hasNoUpdateOwner() {
        installTestPackage("--user ${otherUser.id()} -i $TEST_INSTALLER_APK_PACKAGE_NAME")

        try {
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .adoptShellPermissionIdentity(Manifest.permission.INSTALL_PACKAGES)
            installExistingTestPackage()
            var sourceInfo = pm.getInstallSourceInfo(TEST_APK_PACKAGE_NAME)
            assertEquals(null, sourceInfo.updateOwnerPackageName)
        } finally {
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .dropShellPermissionIdentity()
        }
    }

    private fun installExistingTestPackage() {
        pi.installExistingPackage(
                TEST_APK_PACKAGE_NAME,
                PackageManager.INSTALL_REASON_UNKNOWN,
                null /* intentSender */
        )
    }
}
