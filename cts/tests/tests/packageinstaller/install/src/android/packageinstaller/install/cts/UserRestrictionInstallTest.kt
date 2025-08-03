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

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageInstaller.EXTRA_STATUS
import android.content.pm.PackageInstaller.STATUS_FAILURE_INVALID
import android.content.pm.PackageInstaller.STATUS_PENDING_USER_ACTION
import android.content.pm.PackageInstaller.Session
import android.content.pm.PackageInstaller.SessionParams
import android.platform.test.annotations.AppModeFull
import androidx.core.content.FileProvider
import androidx.test.uiautomator.UiObject
import androidx.test.uiautomator.UiSelector
import com.android.bedstead.harrier.BedsteadJUnit4
import com.android.bedstead.harrier.DeviceState
import com.android.bedstead.harrier.UserType
import com.android.bedstead.harrier.annotations.EnsureDoesNotHaveUserRestriction
import com.android.bedstead.harrier.annotations.EnsureHasAppOp
import com.android.bedstead.harrier.annotations.EnsureHasPermission
import com.android.bedstead.harrier.annotations.EnsureHasUserRestriction
import com.android.bedstead.harrier.annotations.EnsureHasWorkProfile
import com.android.bedstead.harrier.annotations.RequireRunOnWorkProfile
import com.android.bedstead.harrier.annotations.enterprise.DevicePolicyRelevant
import com.android.bedstead.nene.TestApis
import com.android.bedstead.nene.appops.CommonAppOps.OPSTR_REQUEST_INSTALL_PACKAGES
import com.android.bedstead.nene.exceptions.AdbException
import com.android.bedstead.nene.permissions.CommonPermissions.INTERACT_ACROSS_USERS_FULL
import com.android.bedstead.nene.userrestrictions.CommonUserRestrictions.DISALLOW_DEBUGGING_FEATURES
import com.android.bedstead.nene.userrestrictions.CommonUserRestrictions.DISALLOW_INSTALL_APPS
import com.android.bedstead.nene.users.UserReference
import com.android.bedstead.nene.utils.ShellCommand
import com.android.compatibility.common.util.ApiTest
import com.android.compatibility.common.util.BlockingBroadcastReceiver
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.test.assertFailsWith
import org.junit.Assert.fail
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(BedsteadJUnit4::class)
@AppModeFull(reason = "DEVICE_POLICY_SERVICE is null in instant mode")
class UserRestrictionInstallTest : PackageInstallerTestBase() {
    private val APP_INSTALL_ACTION =
            "android.packageinstaller.install.cts.UserRestrictionInstallTest.action"

    companion object {
        @JvmField
        @ClassRule
        @Rule
        val sDeviceState = DeviceState()
    }

    @Before
    fun uninstallTestApp() {
        val cmd = ShellCommand.builder("pm uninstall")
        cmd.addOperand(TEST_APK_PACKAGE_NAME)
        try {
            cmd.execute()
        } catch (_: AdbException) {
            fail("Could not uninstall $TEST_APK_PACKAGE_NAME")
        }
    }

    @Test
    @ApiTest(apis = ["android.os.UserManager#DISALLOW_DEBUGGING_FEATURES"])
    @DevicePolicyRelevant
    @EnsureHasWorkProfile
    @EnsureHasUserRestriction(value = DISALLOW_DEBUGGING_FEATURES, onUser = UserType.WORK_PROFILE)
    @EnsureDoesNotHaveUserRestriction(value = DISALLOW_INSTALL_APPS, onUser = UserType.WORK_PROFILE)
    fun disallowDebuggingFeatures_adbInstallOnAllUsers_installedOnUnrestrictedUser() {
        val initialUser = sDeviceState.initialUser()
        val workProfile = sDeviceState.workProfile()

        installPackageViaAdb(apkPath = "$TEST_APK_LOCATION/$TEST_APK_NAME")

        assertWithMessage("Test app should be installed in initial user")
                .that(TestApis.packages().find(TEST_APK_PACKAGE_NAME).installedOnUser(initialUser))
                .isTrue()

        assertWithMessage("Test app shouldn't be installed in a work profile with " +
                "$DISALLOW_DEBUGGING_FEATURES set")
                .that(TestApis.packages().find(TEST_APK_PACKAGE_NAME).installedOnUser(workProfile))
                .isFalse()
    }

    @Test
    @ApiTest(apis = ["android.os.UserManager#DISALLOW_DEBUGGING_FEATURES"])
    @DevicePolicyRelevant
    @EnsureHasWorkProfile
    @EnsureHasUserRestriction(value = DISALLOW_DEBUGGING_FEATURES, onUser = UserType.WORK_PROFILE)
    @EnsureDoesNotHaveUserRestriction(value = DISALLOW_INSTALL_APPS, onUser = UserType.WORK_PROFILE)
    fun disallowDebuggingFeatures_adbInstallOnWorkProfile_fails() {
        val workProfile = sDeviceState.workProfile()

        installPackageViaAdb(apkPath = "$TEST_APK_LOCATION/$TEST_APK_NAME", user = workProfile)

        assertWithMessage("Test app shouldn't be installed in a work profile with " +
                "$DISALLOW_DEBUGGING_FEATURES set")
                .that(TestApis.packages().find(TEST_APK_PACKAGE_NAME).installedOnUser(workProfile))
                .isFalse()
    }

    @Test
    @ApiTest(apis = ["android.os.UserManager#DISALLOW_DEBUGGING_FEATURES"])
    @DevicePolicyRelevant
    @EnsureHasWorkProfile
    @EnsureHasUserRestriction(value = DISALLOW_DEBUGGING_FEATURES, onUser = UserType.WORK_PROFILE)
    @EnsureDoesNotHaveUserRestriction(value = DISALLOW_INSTALL_APPS, onUser = UserType.WORK_PROFILE)
    @EnsureHasPermission(INTERACT_ACROSS_USERS_FULL)
    fun disallowDebuggingFeatures_sessionInstallOnWorkProfile_getInstallRequest() {
        val workProfile = sDeviceState.workProfile()
        val (_, session) = createSessionForUser(workProfile)
        try {
            writeSessionAsUser(workProfile, session)
            val result: Intent? = commitSessionAsUser(workProfile, session)
            assertThat(result).isNotNull()
            assertThat(result!!.getIntExtra(EXTRA_STATUS, STATUS_FAILURE_INVALID))
                    .isEqualTo(STATUS_PENDING_USER_ACTION)
        } finally {
            session.abandon()
        }
    }

    @Test
    @ApiTest(apis = ["android.os.UserManager#DISALLOW_DEBUGGING_FEATURES"])
    @DevicePolicyRelevant
    @EnsureHasUserRestriction(value = DISALLOW_DEBUGGING_FEATURES, onUser = UserType.WORK_PROFILE)
    @EnsureDoesNotHaveUserRestriction(value = DISALLOW_INSTALL_APPS, onUser = UserType.WORK_PROFILE)
    @EnsureHasAppOp(OPSTR_REQUEST_INSTALL_PACKAGES)
    @RequireRunOnWorkProfile
    fun disallowDebuggingFeatures_intentInstallOnWorkProfile_installationSucceeds() {
        val context = TestApis.context().instrumentedContext()
        val apkFile = File(context.filesDir, TEST_APK_NAME)
        val appInstallIntent = getAppInstallationIntent(apkFile)

        val installation = startInstallationViaIntent(appInstallIntent)
        clickInstallerUIButton(INSTALL_BUTTON_ID)

        // Install should have succeeded
        assertThat(installation.get(TIMEOUT, TimeUnit.MILLISECONDS)).isEqualTo(Activity.RESULT_OK)
    }

    @Test
    @ApiTest(apis = ["android.os.UserManager#DISALLOW_INSTALL_APPS"])
    @DevicePolicyRelevant
    @EnsureHasWorkProfile
    @EnsureHasUserRestriction(value = DISALLOW_INSTALL_APPS, onUser = UserType.WORK_PROFILE)
    @EnsureDoesNotHaveUserRestriction(value = DISALLOW_DEBUGGING_FEATURES,
            onUser = UserType.WORK_PROFILE)
    fun disallowInstallApps_adbInstallOnAllUsers_installedOnUnrestrictedUser() {
        val initialUser = sDeviceState.initialUser()
        val workProfile = sDeviceState.workProfile()

        installPackageViaAdb(apkPath = "$TEST_APK_LOCATION/$TEST_APK_NAME")

        var targetPackage = TestApis.packages().installedForUser(initialUser).filter {
            it.packageName().equals(TEST_APK_PACKAGE_NAME)
        }
        assertWithMessage("Test app should be installed in initial user")
                .that(targetPackage.size).isNotEqualTo(0)

        targetPackage = TestApis.packages().installedForUser(workProfile).filter {
            it.packageName().equals(TEST_APK_PACKAGE_NAME)
        }
        assertWithMessage("Test app shouldn't be installed in a work profile with " +
                "$DISALLOW_INSTALL_APPS set")
                .that(targetPackage.size).isEqualTo(0)
    }

    @Test
    @ApiTest(apis = ["android.os.UserManager#DISALLOW_INSTALL_APPS"])
    @DevicePolicyRelevant
    @EnsureHasWorkProfile
    @EnsureHasUserRestriction(value = DISALLOW_INSTALL_APPS, onUser = UserType.WORK_PROFILE)
    @EnsureDoesNotHaveUserRestriction(value = DISALLOW_DEBUGGING_FEATURES,
            onUser = UserType.WORK_PROFILE)
    fun disallowInstallApps_adbInstallOnWorkProfile_fails() {
        val workProfile = sDeviceState.workProfile()
        assertThat(TestApis.devicePolicy().userRestrictions(workProfile)
                .isSet(DISALLOW_INSTALL_APPS)).isTrue()

        installPackageViaAdb(apkPath = "$TEST_APK_LOCATION/$TEST_APK_NAME", user = workProfile)

        val targetPackage = TestApis.packages().installedForUser(workProfile).filter {
            it.packageName().equals(TEST_APK_PACKAGE_NAME)
        }
        assertWithMessage("Test app shouldn't be installed in a work profile with " +
                "$DISALLOW_DEBUGGING_FEATURES set")
                .that(targetPackage.size).isEqualTo(0)
    }

    @Test
    @ApiTest(apis = ["android.os.UserManager#DISALLOW_INSTALL_APPS"])
    @DevicePolicyRelevant
    @EnsureHasWorkProfile
    @EnsureHasUserRestriction(value = DISALLOW_INSTALL_APPS, onUser = UserType.WORK_PROFILE)
    @EnsureDoesNotHaveUserRestriction(value = DISALLOW_DEBUGGING_FEATURES,
            onUser = UserType.WORK_PROFILE)
    @EnsureHasPermission(INTERACT_ACROSS_USERS_FULL)
    fun disallowInstallApps_sessionInstallOnWorkProfile_throwsException() {
        val workProfile = sDeviceState.workProfile()
        assertFailsWith(SecurityException::class) {
            createSessionForUser(workProfile)
        }
    }

    @Test
    @ApiTest(apis = ["android.os.UserManager#DISALLOW_INSTALL_APPS"])
    @DevicePolicyRelevant
    @EnsureHasUserRestriction(value = DISALLOW_INSTALL_APPS, onUser = UserType.WORK_PROFILE)
    @EnsureDoesNotHaveUserRestriction(value = DISALLOW_DEBUGGING_FEATURES,
            onUser = UserType.WORK_PROFILE)
    @EnsureHasAppOp(OPSTR_REQUEST_INSTALL_PACKAGES)
    @RequireRunOnWorkProfile
    fun disallowInstallApps_intentInstallOnWorkProfile_installationFails() {
        val context = TestApis.context().instrumentedContext()
        val apkFile = File(context.filesDir, TEST_APK_NAME)
        val appInstallIntent = getAppInstallationIntent(apkFile)

        val installation = startInstallationViaIntent(appInstallIntent)
        // Dismiss the device policy dialog
        val closeBtn: UiObject = TestApis.ui().device().findObject(
                UiSelector().resourceId("android:id/button1")
        )
        closeBtn.click()

        // Install should have failed
        assertThat(installation.get(TIMEOUT, TimeUnit.MILLISECONDS))
                .isEqualTo(Activity.RESULT_CANCELED)
    }

    @Test
    @ApiTest(apis = ["android.os.UserManager#DISALLOW_DEBUGGING_FEATURES",
        "android.os.UserManager#DISALLOW_INSTALL_APPS"])
    @DevicePolicyRelevant
    @EnsureHasWorkProfile
    @EnsureDoesNotHaveUserRestriction(value = DISALLOW_DEBUGGING_FEATURES,
            onUser = UserType.WORK_PROFILE)
    @EnsureDoesNotHaveUserRestriction(value = DISALLOW_INSTALL_APPS, onUser = UserType.WORK_PROFILE)
    fun unrestrictedWorkProfile_adbInstallOnAllUsers_installedOnAllUsers() {
        val initialUser = sDeviceState.initialUser()
        val workProfile = sDeviceState.workProfile()
        assertThat(TestApis.devicePolicy().userRestrictions(workProfile)
                .isSet(DISALLOW_DEBUGGING_FEATURES)).isFalse()
        assertThat(TestApis.devicePolicy().userRestrictions(workProfile)
                .isSet(DISALLOW_INSTALL_APPS)).isFalse()

        installPackageViaAdb(apkPath = "$TEST_APK_LOCATION/$TEST_APK_NAME")

        var targetPackage = TestApis.packages().installedForUser(initialUser).filter {
            it.packageName().equals(TEST_APK_PACKAGE_NAME)
        }
        assertWithMessage("Test app should be installed in initial user")
                .that(targetPackage.size).isNotEqualTo(0)

        targetPackage = TestApis.packages().installedForUser(workProfile).filter {
            it.packageName().equals(TEST_APK_PACKAGE_NAME)
        }
        assertWithMessage("Test app should be installed in work profile")
                .that(targetPackage.size).isNotEqualTo(0)
    }

    private fun installPackageViaAdb(apkPath: String, user: UserReference? = null): String? {
        val cmd = ShellCommand.builderForUser(user, "pm install")
        cmd.addOperand(apkPath)
        return try {
            cmd.execute()
        } catch (e: AdbException) {
            null
        }
    }

    @Throws(SecurityException::class)
    private fun createSessionForUser(user: UserReference = sDeviceState.initialUser()):
            Pair<Int, Session> {
        val context = TestApis.context().androidContextAsUser(user)
        val pm = context.packageManager
        val pi = pm.packageInstaller

        val params = SessionParams(SessionParams.MODE_FULL_INSTALL)
        params.setRequireUserAction(SessionParams.USER_ACTION_REQUIRED)

        val sessionId = pi.createSession(params)
        val session = pi.openSession(sessionId)

        return Pair(sessionId, session)
    }

    private fun writeSessionAsUser(
            user: UserReference = sDeviceState.initialUser(),
            session: Session
    ) {
        val context = TestApis.context().androidContextAsUser(user)
        val apkFile = File(context.filesDir, TEST_APK_NAME)
        // Write data to session
        apkFile.inputStream().use { fileOnDisk ->
            session.openWrite(TEST_APK_NAME, 0, -1).use { sessionFile ->
                fileOnDisk.copyTo(sessionFile)
            }
        }
    }

    private fun commitSessionAsUser(
            user: UserReference = sDeviceState.initialUser(),
            session: Session
    ): Intent? {
        val context = TestApis.context().androidContextAsUser(user)
        val receiver: BlockingBroadcastReceiver =
                sDeviceState.registerBroadcastReceiverForUser(user, APP_INSTALL_ACTION)
        receiver.register()

        val intent = Intent(APP_INSTALL_ACTION).setPackage(context.packageName)
                .addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        val pendingIntent = PendingIntent.getBroadcast(
                context, 0 /* requestCode */, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)

        session.commit(pendingIntent.intentSender)

        // The system should have asked us to launch the installer
        return receiver.awaitForBroadcast()
    }

    private fun getAppInstallationIntent(apkFile: File): Intent {
        val intent = Intent(Intent.ACTION_INSTALL_PACKAGE)
        intent.data = FileProvider.getUriForFile(context, CONTENT_AUTHORITY, apkFile)
        intent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        intent.putExtra(Intent.EXTRA_RETURN_RESULT, true)
        return intent
    }
}
