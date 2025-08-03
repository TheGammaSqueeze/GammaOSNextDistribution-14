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

import android.Manifest
import android.app.Activity.RESULT_CANCELED
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.app.PendingIntent.FLAG_UPDATE_CURRENT
import android.app.UiAutomation
import android.content.Intent
import android.content.pm.ApplicationInfo.CATEGORY_MAPS
import android.content.pm.ApplicationInfo.CATEGORY_UNDEFINED
import android.content.pm.Flags
import android.content.pm.PackageInstaller
import android.content.pm.PackageInstaller.STATUS_FAILURE_ABORTED
import android.content.pm.PackageInstaller.STATUS_SUCCESS
import android.content.pm.PackageInstaller.SessionParams.MODE_FULL_INSTALL
import android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
import android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED
import android.os.Build
import android.platform.test.annotations.AppModeFull
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.CheckFlagsRule
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import androidx.test.InstrumentationRegistry
import androidx.test.filters.SdkSuppress
import androidx.test.runner.AndroidJUnit4
import com.android.compatibility.common.util.AppOpsUtils
import com.android.compatibility.common.util.SystemUtil
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * This class tests creation of a package installer session with different params.
 */
@AppModeFull(reason = "Instant apps cannot create installer sessions")
@RunWith(AndroidJUnit4::class)
class SessionTest : PackageInstallerTestBase() {
    @JvmField
    @Rule
    val mCheckFlagsRule: CheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    private val uiAutomation: UiAutomation =
            InstrumentationRegistry.getInstrumentation().getUiAutomation()

    /**
     * Check the session should not pass the status receiver from an immutable PendingIntent
     */
    @Test(expected = IllegalArgumentException::class)
    @SdkSuppress(
        minSdkVersion = Build.VERSION_CODES.VANILLA_ICE_CREAM,
        codeName = "VanillaIceCream"
    )
    fun sessionWithImmutablePendingIntent() {
        val sessionParam = PackageInstaller.SessionParams(MODE_FULL_INSTALL)
        val sessionId = pi.createSession(sessionParam)
        try {
            val session = pi.openSession(sessionId)
            commitSessionWithImmutablePendingIntent(session)
        } finally {
            pi.abandonSession(sessionId)
        }
    }

    /**
     * Check that we can install an app via a package-installer session
     */
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_READ_INSTALL_INFO, Flags.FLAG_GET_RESOLVED_APK_PATH)
    fun confirmInstallation() {
        val installation = startInstallationViaSession(needFuture = true)!!
        clickInstallerUIButton(INSTALL_BUTTON_ID)

        // Install should have succeeded
        val result = getInstallSessionResult()
        assertEquals(STATUS_SUCCESS, result.status)
        assertEquals(false, result.preapproval)
        assertInstalled()

        // Even when the install succeeds the install confirm dialog returns 'canceled'
        assertEquals(RESULT_CANCELED, installation.get(TIMEOUT, TimeUnit.MILLISECONDS))

        assertTrue(AppOpsUtils.allowedOperationLogged(context.packageName, APP_OP_STR))
    }

    /**
     * Check that we can install an app via a package-installer session
     */
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_READ_INSTALL_INFO, Flags.FLAG_GET_RESOLVED_APK_PATH)
    fun confirmMultiPackageInstallation() {
        val installation = startInstallationViaMultiPackageSession(
                installFlags = 0,
                TEST_APK_NAME,
                needFuture = true
        )!!
        clickInstallerUIButton(INSTALL_BUTTON_ID)

        // Install should have succeeded
        val result = getInstallSessionResult()
        assertEquals(STATUS_SUCCESS, result.status)
        assertEquals(false, result.preapproval)
        assertInstalled()

        // Even when the install succeeds the install confirm dialog returns 'canceled'
        assertEquals(RESULT_CANCELED, installation.get(TIMEOUT, TimeUnit.MILLISECONDS))

        assertTrue(AppOpsUtils.allowedOperationLogged(context.packageName, APP_OP_STR))
    }

    /**
     * Check that we can set an app category for an app we installed
     */
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_READ_INSTALL_INFO, Flags.FLAG_GET_RESOLVED_APK_PATH)
    fun setAppCategory() {
        val installation = startInstallationViaSession()
        clickInstallerUIButton(INSTALL_BUTTON_ID)

        // Wait for installation to finish
        getInstallSessionResult()

        assertEquals(CATEGORY_UNDEFINED, pm.getApplicationInfo(TEST_APK_PACKAGE_NAME, 0).category)

        // This app installed the app, hence we can set the category
        pm.setApplicationCategoryHint(TEST_APK_PACKAGE_NAME, CATEGORY_MAPS)

        assertEquals(CATEGORY_MAPS, pm.getApplicationInfo(TEST_APK_PACKAGE_NAME, 0).category)
    }

    /**
     * Check that we can set an app category for an app we installed
     */
    @Test
    fun setApplicationEnabledSettingPersistent() {
        installWithApplicationEnabledSetting()
        assertEquals(COMPONENT_ENABLED_STATE_DEFAULT,
                pm.getApplicationEnabledSetting(TEST_APK_PACKAGE_NAME))

        disablePackage()
        assertEquals(COMPONENT_ENABLED_STATE_DISABLED,
                pm.getApplicationEnabledSetting(TEST_APK_PACKAGE_NAME))

        // enabled setting should be reset to default after reinstall
        installWithApplicationEnabledSetting()
        assertEquals(COMPONENT_ENABLED_STATE_DEFAULT,
                pm.getApplicationEnabledSetting(TEST_APK_PACKAGE_NAME))

        disablePackage()
        assertEquals(COMPONENT_ENABLED_STATE_DISABLED,
            pm.getApplicationEnabledSetting(TEST_APK_PACKAGE_NAME))

        // enabled setting should now be persisted after reinstall
        installWithApplicationEnabledSetting(true)
        assertEquals(COMPONENT_ENABLED_STATE_DISABLED,
            pm.getApplicationEnabledSetting(TEST_APK_PACKAGE_NAME))
    }

    /**
     * Install an app via a package-installer session, but then cancel it when the package installer
     * pops open.
     */
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_READ_INSTALL_INFO, Flags.FLAG_GET_RESOLVED_APK_PATH)
    fun cancelInstallation() {
        val installation = startInstallationViaSession(needFuture = true)!!
        clickInstallerUIButton(CANCEL_BUTTON_ID)

        // Install should have been aborted
        val result = getInstallSessionResult()
        assertEquals(STATUS_FAILURE_ABORTED, result.status)
        assertEquals(false, result.preapproval)
        assertEquals(RESULT_CANCELED, installation.get(TIMEOUT, TimeUnit.MILLISECONDS))
        assertNotInstalled()
    }

    /**
     * Check that can't install when FRP mode is enabled.
     */
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_READ_INSTALL_INFO, Flags.FLAG_GET_RESOLVED_APK_PATH)
    fun confirmFrpInstallationFails() {
        try {
            setSecureFrp(true)

            try {
                val installation = startInstallationViaSession()
                clickInstallerUIButton(CANCEL_BUTTON_ID)

                fail("Package should not be installed")
            } catch (expected: SecurityException) {
            }

            // Install should never have started
            assertNotInstalled()
        } finally {
            setSecureFrp(false)
        }
    }

    /**
     * Check that can't install Instant App when installer don't have proper permission.
     */
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_READ_INSTALL_INFO, Flags.FLAG_GET_RESOLVED_APK_PATH)
    fun confirmInstantInstallationFails() {
        try {
            val installation = startInstallationViaSession(INSTALL_INSTANT_APP)
            clickInstallerUIButton(CANCEL_BUTTON_ID)

            fail("Expected security exception on instant install from non-system app")
        } catch (expected: SecurityException) {
            // Expected
        }

        // Install should never have started
        assertNotInstalled()
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_READ_INSTALL_INFO, Flags.FLAG_GET_RESOLVED_APK_PATH)
    fun withPrivilegedPermissions_canAccessResolvedPath() {
        val sessionParam = PackageInstaller.SessionParams(MODE_FULL_INSTALL)
        val sessionId = pi.createSession(sessionParam)
        val session = pi.openSession(sessionId)

        // resolvedBaseCodePath is populated after at least one file is written.
        var sessionInfo: PackageInstaller.SessionInfo? = null
        SystemUtil.runWithShellPermissionIdentity({
            sessionInfo = pi.getSessionInfo(sessionId)
        }, Manifest.permission.READ_INSTALLED_SESSION_PATHS)
        assertNull(sessionInfo!!.resolvedBaseApkPath)

        writeSession(session, TEST_APK_NAME)
        commitSession(session)

        SystemUtil.runWithShellPermissionIdentity({
            sessionInfo = pi.getSessionInfo(sessionId)
        }, Manifest.permission.READ_INSTALLED_SESSION_PATHS)
        assertNotNull(sessionInfo!!.resolvedBaseApkPath)
        clickInstallerUIButton(CANCEL_BUTTON_ID)
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_READ_INSTALL_INFO, Flags.FLAG_GET_RESOLVED_APK_PATH)
    fun withoutPrivilegedPermissions_cannotAccessResolvedPath() {
        val sessionParam = PackageInstaller.SessionParams(MODE_FULL_INSTALL)
        val sessionId = pi.createSession(sessionParam)
        val session = pi.openSession(sessionId)
        writeSession(session, TEST_APK_NAME)
        commitSession(session)
        val sessionInfo = pi.getSessionInfo(sessionId)
        assertNull(sessionInfo!!.resolvedBaseApkPath)
        clickInstallerUIButton(CANCEL_BUTTON_ID)
    }

    private fun installWithApplicationEnabledSetting(setEnabledSettingPersistent: Boolean = false) {
        val sessionParam = PackageInstaller.SessionParams(MODE_FULL_INSTALL)
        if (setEnabledSettingPersistent) {
            sessionParam.setApplicationEnabledSettingPersistent()
        }
        val sessionId = pi.createSession(sessionParam)
        val session = pi.openSession(sessionId)
        assertEquals(setEnabledSettingPersistent, session.isApplicationEnabledSettingPersistent())
        writeSession(session, TEST_APK_NAME)
        commitSession(session)
        clickInstallerUIButton(INSTALL_BUTTON_ID)

        // Wait for installation to finish
        getInstallSessionResult()
    }

    private fun disablePackage() {
        uiAutomation.adoptShellPermissionIdentity()
        try {
            pm.setApplicationEnabledSetting(TEST_APK_PACKAGE_NAME,
                COMPONENT_ENABLED_STATE_DISABLED, 0)
        } finally {
            uiAutomation.dropShellPermissionIdentity()
        }
    }

    private fun commitSessionWithImmutablePendingIntent(session: PackageInstaller.Session) {
        var intent = Intent(INSTALL_ACTION_CB).setPackage(context.getPackageName())
        val pendingIntent = PendingIntent.getBroadcast(
                        context, 0 /* requestCode */, intent, FLAG_UPDATE_CURRENT or FLAG_IMMUTABLE)
        session.commit(pendingIntent.intentSender)
    }
}
