/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.content.pm.cts

import android.Manifest
import android.app.UiAutomation
import android.content.Context
import android.content.Context.RECEIVER_EXPORTED
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.Flags.FLAG_NULLABLE_DATA_DIR
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PackageManager.MATCH_ANY_USER
import android.content.pm.PackageManager.MATCH_ARCHIVED_PACKAGES
import android.content.pm.PackageManager.MATCH_DISABLED_COMPONENTS
import android.content.pm.PackageManager.MATCH_DISABLED_UNTIL_USED_COMPONENTS
import android.content.pm.PackageManager.MATCH_HIDDEN_UNTIL_INSTALLED_COMPONENTS
import android.content.pm.PackageManager.MATCH_KNOWN_PACKAGES
import android.content.pm.PackageManager.MATCH_SYSTEM_ONLY
import android.content.pm.PackageManager.MATCH_UNINSTALLED_PACKAGES
import android.content.pm.PackageManager.PackageInfoFlags
import android.content.pm.cts.PackageManagerShellCommandInstallTest.PackageBroadcastReceiver
import android.content.pm.cts.PackageManagerTest.CTS_SHIM_PACKAGE_NAME
import android.content.pm.cts.PackageManagerTest.getInstalledState
import android.content.pm.cts.util.AbandonAllPackageSessionsRule
import android.os.Handler
import android.os.HandlerThread
import android.os.UserManager
import android.platform.test.annotations.AppModeFull
import android.platform.test.annotations.AppModeNonSdkSandbox
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.CheckFlagsRule
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.android.bedstead.harrier.BedsteadJUnit4
import com.android.bedstead.harrier.DeviceState
import com.android.bedstead.harrier.annotations.EnsureHasSecondaryUser
import com.android.bedstead.nene.users.UserReference
import com.android.compatibility.common.util.SystemUtil
import com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.util.regex.Pattern
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runners.model.Statement

@EnsureHasSecondaryUser
@RunWith(BedsteadJUnit4::class)
@AppModeFull(reason = "Cannot query other apps if instant")
@AppModeNonSdkSandbox(reason = "SDK sandboxes cannot query other apps")
class PackageManagerShellCommandMultiUserTest {

    @JvmField
    @Rule
    val mCheckFlagsRule: CheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    companion object {

        private const val TEST_APP_PACKAGE = PackageManagerShellCommandInstallTest.TEST_APP_PACKAGE
        private const val TEST_HW5 = PackageManagerShellCommandInstallTest.TEST_HW5

        private const val TEST_HW_SYSTEM_USER_ONLY =
                PackageManagerShellCommandInstallTest.TEST_HW_SYSTEM_USER_ONLY

        private const val TEST_PROVIDER = "TestProvider"
        private const val TEST_SERVICE = "TestService"

        private const val SYSTEM_USER_ONLY_PROVIDER = "SystemUserOnlyProvider"
        private const val SYSTEM_USER_ONLY_SERVICE = "SystemUserOnlyService"

        @JvmField
        @ClassRule(order = 0)
        @Rule
        val deviceState = DeviceState()

        @JvmField
        @ClassRule(order = 1)
        var mAbandonSessionsRule = AbandonAllPackageSessionsRule()

        @JvmField
        @ClassRule(order = 2)
        var mBroadcastBarrierRule = object : TestRule {
            override fun apply(base: Statement, description: Description): Statement {
                return object : Statement() {
                    override fun evaluate() {
                        if (description.isSuite()) {
                            // Run "wait-for-broadcast-idle" to clear any
                            // pending broadcasts in the System so that they do not have any
                            // impact on the tests.
                            SystemUtil.runShellCommand("am wait-for-broadcast-idle")
                        }
                        base.evaluate()
                    }
                }
            }
        }

        private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
        private val uiAutomation: UiAutomation =
            InstrumentationRegistry.getInstrumentation().uiAutomation

        private var backgroundThread = HandlerThread("PackageManagerShellCommandMultiUserTest")

        fun skipTheInstallType(installTypeString: String): Boolean {
            if (installTypeString == "install-incremental" &&
                !context.packageManager.hasSystemFeature(
                    PackageManager.FEATURE_INCREMENTAL_DELIVERY)) {
                return true
            }
            return false
        }
    }

    private lateinit var primaryUser: UserReference
    private lateinit var secondaryUser: UserReference

    @Before
    fun cacheUsers() {
        primaryUser = deviceState.primaryUser()
        secondaryUser = deviceState.secondaryUser()
    }

    private var mPackageVerifier: String? = null
    private var mStreamingVerificationTimeoutMs =
        PackageManagerShellCommandInstallTest.DEFAULT_STREAMING_VERIFICATION_TIMEOUT_MS

    @Before
    fun setup() {
        assumeTrue(UserManager.supportsMultipleUsers())
        assumeFalse(getUserManager().hasUserRestriction(UserManager.DISALLOW_REMOVE_USER))
        uninstallPackageSilently(TEST_APP_PACKAGE)
        assertFalse(PackageManagerShellCommandInstallTest.isAppInstalled(TEST_APP_PACKAGE))
        mPackageVerifier =
            SystemUtil.runShellCommand("settings get global verifier_verify_adb_installs")
        // Disable the package verifier for non-incremental installations to avoid the dialog
        // when installing an app.
        SystemUtil.runShellCommand("settings put global verifier_verify_adb_installs 0")
        mStreamingVerificationTimeoutMs = SystemUtil.runShellCommand(
            "settings get global streaming_verifier_timeout"
        )
            .toLongOrNull()
            ?: PackageManagerShellCommandInstallTest.DEFAULT_STREAMING_VERIFICATION_TIMEOUT_MS
    }

    @After
    fun reset() {
        uninstallPackageSilently(TEST_APP_PACKAGE)
        assertFalse(PackageManagerShellCommandInstallTest.isAppInstalled(TEST_APP_PACKAGE))
        assertEquals(null, PackageManagerShellCommandInstallTest.getSplits(TEST_APP_PACKAGE))

        // Reset the global settings to their original values.
        SystemUtil.runShellCommand(
            "settings put global verifier_verify_adb_installs $mPackageVerifier"
        )

        // Set the test override to invalid.
        setSystemProperty("debug.pm.uses_sdk_library_default_cert_digest", "invalid")
        setSystemProperty("debug.pm.prune_unused_shared_libraries_delay", "invalid")
        setSystemProperty("debug.pm.adb_verifier_override_packages", "invalid")
    }

    @Test
    fun testGetFirstInstallTime() {
        val startTimeMillisForPrimaryUser = System.currentTimeMillis()
        installPackageAsUser(TEST_HW5, primaryUser)
        assertTrue(isAppInstalledForUser(TEST_APP_PACKAGE, primaryUser))
        val origFirstInstallTimeForPrimaryUser =
            getFirstInstallTimeAsUser(TEST_APP_PACKAGE, primaryUser)
        // Validate the timestamp
        assertTrue(origFirstInstallTimeForPrimaryUser > 0)
        assertTrue(startTimeMillisForPrimaryUser < origFirstInstallTimeForPrimaryUser)
        assertTrue(System.currentTimeMillis() > origFirstInstallTimeForPrimaryUser)

        // Install again with replace and the firstInstallTime should remain the same
        installPackage(TEST_HW5)
        var firstInstallTimeForPrimaryUser =
            getFirstInstallTimeAsUser(TEST_APP_PACKAGE, primaryUser)
        assertEquals(origFirstInstallTimeForPrimaryUser, firstInstallTimeForPrimaryUser)

        // Start another user and install this test itself for that user
        var startTimeMillisForSecondaryUser = System.currentTimeMillis()
        installExistingPackageAsUser(context.packageName, secondaryUser)
        assertTrue(isAppInstalledForUser(context.packageName, secondaryUser))
        // Install test package with replace
        installPackageAsUser(TEST_HW5, secondaryUser)
        assertTrue(isAppInstalledForUser(TEST_APP_PACKAGE, secondaryUser))
        firstInstallTimeForPrimaryUser = getFirstInstallTimeAsUser(TEST_APP_PACKAGE, primaryUser)
        // firstInstallTime should remain unchanged for the current user
        assertEquals(origFirstInstallTimeForPrimaryUser, firstInstallTimeForPrimaryUser)
        var firstInstallTimeForSecondaryUser =
            getFirstInstallTimeAsUser(TEST_APP_PACKAGE, secondaryUser)
        // firstInstallTime for the other user should be different
        assertNotEquals(firstInstallTimeForPrimaryUser, firstInstallTimeForSecondaryUser)
        assertTrue(startTimeMillisForSecondaryUser < firstInstallTimeForSecondaryUser)
        assertTrue(System.currentTimeMillis() > firstInstallTimeForSecondaryUser)

        // Uninstall for the other user
        uninstallPackageAsUser(TEST_APP_PACKAGE, secondaryUser)
        assertFalse(isAppInstalledForUser(TEST_APP_PACKAGE, secondaryUser))
        // Install test package as an existing package
        startTimeMillisForSecondaryUser = System.currentTimeMillis()
        installExistingPackageAsUser(TEST_APP_PACKAGE, secondaryUser)
        assertTrue(isAppInstalledForUser(TEST_APP_PACKAGE, secondaryUser))
        firstInstallTimeForPrimaryUser = getFirstInstallTimeAsUser(TEST_APP_PACKAGE, primaryUser)
        // firstInstallTime still remains unchanged for the current user
        assertEquals(origFirstInstallTimeForPrimaryUser, firstInstallTimeForPrimaryUser)
        firstInstallTimeForSecondaryUser =
            getFirstInstallTimeAsUser(TEST_APP_PACKAGE, secondaryUser)
        // firstInstallTime for the other user should be different
        assertNotEquals(firstInstallTimeForPrimaryUser, firstInstallTimeForSecondaryUser)
        assertTrue(startTimeMillisForSecondaryUser < firstInstallTimeForSecondaryUser)
        assertTrue(System.currentTimeMillis() > firstInstallTimeForSecondaryUser)

        // Uninstall for all users
        uninstallPackageSilently(TEST_APP_PACKAGE)
        assertFalse(isAppInstalledForUser(TEST_APP_PACKAGE, primaryUser))
        assertFalse(isAppInstalledForUser(TEST_APP_PACKAGE, secondaryUser))
        // Reinstall for all users
        installPackage(TEST_HW5)
        assertTrue(isAppInstalledForUser(TEST_APP_PACKAGE, primaryUser))
        assertTrue(isAppInstalledForUser(TEST_APP_PACKAGE, secondaryUser))
        firstInstallTimeForPrimaryUser = getFirstInstallTimeAsUser(TEST_APP_PACKAGE, primaryUser)
        // First install time is now different because the package was fully uninstalled
        assertNotEquals(origFirstInstallTimeForPrimaryUser, firstInstallTimeForPrimaryUser)
        firstInstallTimeForSecondaryUser =
            getFirstInstallTimeAsUser(TEST_APP_PACKAGE, secondaryUser)
        // Same firstInstallTime because package was installed for both users at the same time
        assertEquals(firstInstallTimeForPrimaryUser, firstInstallTimeForSecondaryUser)
    }

    @Test
    @RequiresFlagsEnabled(android.multiuser
            .Flags.FLAG_ENABLE_SYSTEM_USER_ONLY_FOR_SERVICES_AND_PROVIDERS)
    fun testInstallAppHavingSystemUserOnlyComponents() {
        runWithShellPermissionIdentity(
                uiAutomation,
                {
                    installPackageAsUser(TEST_HW_SYSTEM_USER_ONLY, primaryUser)
                    assertTrue(isAppInstalledForUser(TEST_APP_PACKAGE, primaryUser))

                    installExistingPackageAsUser(context.packageName, secondaryUser)
                    assertTrue(isAppInstalledForUser(context.packageName, secondaryUser))
                    installPackageAsUser(TEST_HW_SYSTEM_USER_ONLY, secondaryUser)
                    assertTrue(isAppInstalledForUser(TEST_APP_PACKAGE, secondaryUser))

                    val contextSystemUser = context.createPackageContextAsUser(
                        TEST_APP_PACKAGE,
                            0,
                        primaryUser.userHandle()
                    )
                    val contextSecondaryUser = context.createPackageContextAsUser(
                        TEST_APP_PACKAGE,
                            0,
                        secondaryUser.userHandle()
                    )

                    // Normal Providers and Services are available on both
                    // SYSTEM and SECONDARY users.
                    assertTrue(isServiceAvailable(contextSystemUser, TEST_SERVICE))
                    assertTrue(isProviderAvailable(contextSystemUser, TEST_PROVIDER))
                    assertTrue(isServiceAvailable(contextSecondaryUser, TEST_SERVICE))
                    assertTrue(isProviderAvailable(contextSecondaryUser, TEST_PROVIDER))

                    // systemUserOnly Providers and Services are available on SYSTEM user
                    assertTrue(isServiceAvailable(contextSystemUser, SYSTEM_USER_ONLY_SERVICE))
                    assertTrue(isProviderAvailable(contextSystemUser, SYSTEM_USER_ONLY_PROVIDER))
                    // systemUserOnly Providers and Services are unavailable on SECONDARY user
                    assertFalse(isServiceAvailable(contextSecondaryUser, SYSTEM_USER_ONLY_SERVICE))
                    assertFalse(
                        isProviderAvailable(contextSecondaryUser, SYSTEM_USER_ONLY_PROVIDER)
                    )
                },
                Manifest.permission.INTERACT_ACROSS_USERS,
                Manifest.permission.INTERACT_ACROSS_USERS_FULL
        )
    }

    private fun isProviderAvailable(context: Context, providerName: String): Boolean {
        return try {
            context.contentResolver.acquireUnstableContentProviderClient(
                    "$TEST_APP_PACKAGE.$providerName") != null
        } catch (ex: Exception) {
            false
        }
    }

    private fun isServiceAvailable(context: Context, serviceName: String): Boolean {
        val systemUserOnlyServiceIntent = Intent()
        systemUserOnlyServiceIntent.setClassName(
                TEST_APP_PACKAGE,
                "$TEST_APP_PACKAGE.$serviceName"
        )

        return try {
            context.startForegroundService(systemUserOnlyServiceIntent) != null
        } catch (ex: Exception) {
            false
        }
    }

    @Test
    fun testPackageRemovedBroadcastsMultiUser() {
        if (!backgroundThread.isAlive) {
            backgroundThread.start()
        }
        val backgroundHandler = Handler(backgroundThread.looper)
        installExistingPackageAsUser(context.packageName, secondaryUser)
        installPackage(TEST_HW5)
        assertTrue(isAppInstalledForUser(context.packageName, primaryUser))
        assertTrue(isAppInstalledForUser(context.packageName, secondaryUser))
        assertTrue(isAppInstalledForUser(TEST_APP_PACKAGE, primaryUser))
        assertTrue(isAppInstalledForUser(TEST_APP_PACKAGE, secondaryUser))
        val removedBroadcastReceiverForPrimaryUser = PackageBroadcastReceiver(
            TEST_APP_PACKAGE,
            primaryUser.id(),
            Intent.ACTION_PACKAGE_REMOVED
        )
        val removedBroadcastReceiverForSecondaryUser = PackageBroadcastReceiver(
            TEST_APP_PACKAGE,
            secondaryUser.id(),
            Intent.ACTION_PACKAGE_REMOVED
        )
        val fullyRemovedBroadcastReceiverForPrimaryUser = PackageBroadcastReceiver(
            TEST_APP_PACKAGE,
            primaryUser.id(),
            Intent.ACTION_PACKAGE_FULLY_REMOVED
        )
        val fullyRemovedBroadcastReceiverForSecondaryUser = PackageBroadcastReceiver(
            TEST_APP_PACKAGE,
            secondaryUser.id(),
            Intent.ACTION_PACKAGE_FULLY_REMOVED
        )
        val uidRemovedBroadcastReceiverForPrimaryUser = PackageBroadcastReceiver(
                TEST_APP_PACKAGE,
                primaryUser.id(),
                Intent.ACTION_UID_REMOVED
        )
        val uidRemovedBroadcastReceiverForSecondaryUser = PackageBroadcastReceiver(
                TEST_APP_PACKAGE,
                secondaryUser.id(),
                Intent.ACTION_UID_REMOVED
        )
        val intentFilter = IntentFilter()
        intentFilter.addAction(Intent.ACTION_PACKAGE_REMOVED)
        intentFilter.addAction(Intent.ACTION_PACKAGE_FULLY_REMOVED)
        intentFilter.addDataScheme("package")
        val intentFilterForUidRemoved = IntentFilter(Intent.ACTION_UID_REMOVED)
        runWithShellPermissionIdentity(
            uiAutomation,
            {
                val contextPrimaryUser = context.createContextAsUser(primaryUser.userHandle(), 0)
                val contextSecondaryUser = context.createContextAsUser(
                    secondaryUser.userHandle(),
                    0
                )
                contextPrimaryUser.registerReceiver(
                    removedBroadcastReceiverForPrimaryUser,
                    intentFilter,
                    null,
                    backgroundHandler,
                    RECEIVER_EXPORTED
                )
                contextPrimaryUser.registerReceiver(
                    fullyRemovedBroadcastReceiverForPrimaryUser,
                    intentFilter,
                    null,
                    backgroundHandler,
                    RECEIVER_EXPORTED
                )
                contextSecondaryUser.registerReceiver(
                    removedBroadcastReceiverForSecondaryUser,
                    intentFilter,
                    null,
                    backgroundHandler,
                    RECEIVER_EXPORTED
                )
                contextSecondaryUser.registerReceiver(
                    fullyRemovedBroadcastReceiverForSecondaryUser,
                    intentFilter,
                    null,
                    backgroundHandler,
                    RECEIVER_EXPORTED
                )
                contextPrimaryUser.registerReceiver(
                        uidRemovedBroadcastReceiverForPrimaryUser,
                        intentFilterForUidRemoved,
                        null,
                        backgroundHandler,
                        RECEIVER_EXPORTED
                )
                contextSecondaryUser.registerReceiver(
                        uidRemovedBroadcastReceiverForSecondaryUser,
                        intentFilterForUidRemoved,
                        null,
                        backgroundHandler,
                        RECEIVER_EXPORTED
                )

                var uidPrimaryUser =
                        contextPrimaryUser.packageManager.getPackageUid(TEST_APP_PACKAGE, 0)
                var uidSecondaryUser =
                        contextSecondaryUser.packageManager.getPackageUid(TEST_APP_PACKAGE, 0)
                assertNotEquals(uidPrimaryUser, uidSecondaryUser)
                // Uninstall w/o DELETE_KEEP_DATA sends PACKAGE_REMOVED, PACKAGE_FULLY_REMOVED, and
                // UID_REMOVED broadcasts to the targeted user
                uninstallPackageAsUser(TEST_APP_PACKAGE, secondaryUser)
                removedBroadcastReceiverForPrimaryUser.assertBroadcastNotReceived()
                fullyRemovedBroadcastReceiverForPrimaryUser.assertBroadcastNotReceived()
                uidRemovedBroadcastReceiverForPrimaryUser.assertBroadcastNotReceived()
                removedBroadcastReceiverForSecondaryUser.assertBroadcastReceived()
                var result = removedBroadcastReceiverForSecondaryUser.broadcastResult
                assertEquals(result.extras!!.getInt(Intent.EXTRA_UID, 0), uidSecondaryUser)
                fullyRemovedBroadcastReceiverForSecondaryUser.assertBroadcastReceived()
                result = fullyRemovedBroadcastReceiverForSecondaryUser.broadcastResult
                assertEquals(result.extras!!.getInt(Intent.EXTRA_UID, 0), uidSecondaryUser)
                uidRemovedBroadcastReceiverForSecondaryUser.assertBroadcastReceived()
                result = uidRemovedBroadcastReceiverForSecondaryUser.broadcastResult
                assertEquals(result.extras!!.getInt(Intent.EXTRA_UID, 0), uidSecondaryUser)
                assertNull(result.extras!!.getString(Intent.EXTRA_PACKAGE_NAME, null))
                removedBroadcastReceiverForSecondaryUser.reset()
                fullyRemovedBroadcastReceiverForSecondaryUser.reset()
                uidRemovedBroadcastReceiverForSecondaryUser.reset()

                installExistingPackageAsUser(TEST_APP_PACKAGE, secondaryUser)
                // Uninstall on both users sends the broadcasts to both users
                uninstallPackageSilently(TEST_APP_PACKAGE)
                removedBroadcastReceiverForPrimaryUser.assertBroadcastReceived()
                result = removedBroadcastReceiverForPrimaryUser.broadcastResult
                assertEquals(result.extras!!.getInt(Intent.EXTRA_UID, 0), uidPrimaryUser)
                fullyRemovedBroadcastReceiverForPrimaryUser.assertBroadcastReceived()
                result = fullyRemovedBroadcastReceiverForPrimaryUser.broadcastResult
                assertEquals(result.extras!!.getInt(Intent.EXTRA_UID, 0), uidPrimaryUser)
                uidRemovedBroadcastReceiverForPrimaryUser.assertBroadcastReceived()
                result = uidRemovedBroadcastReceiverForPrimaryUser.broadcastResult
                assertEquals(result.extras!!.getInt(Intent.EXTRA_UID, 0), uidPrimaryUser)
                assertNull(result.extras!!.getString(Intent.EXTRA_PACKAGE_NAME, null))
                removedBroadcastReceiverForSecondaryUser.assertBroadcastReceived()
                result = removedBroadcastReceiverForSecondaryUser.broadcastResult
                assertEquals(result.extras!!.getInt(Intent.EXTRA_UID, 0), uidSecondaryUser)
                fullyRemovedBroadcastReceiverForSecondaryUser.assertBroadcastReceived()
                result = fullyRemovedBroadcastReceiverForSecondaryUser.broadcastResult
                assertEquals(result.extras!!.getInt(Intent.EXTRA_UID, 0), uidSecondaryUser)
                uidRemovedBroadcastReceiverForSecondaryUser.assertBroadcastReceived()
                result = uidRemovedBroadcastReceiverForSecondaryUser.broadcastResult
                assertEquals(result.extras!!.getInt(Intent.EXTRA_UID, 0), uidSecondaryUser)
                assertNull(result.extras!!.getString(Intent.EXTRA_PACKAGE_NAME, null))
                removedBroadcastReceiverForPrimaryUser.reset()
                fullyRemovedBroadcastReceiverForPrimaryUser.reset()
                removedBroadcastReceiverForSecondaryUser.reset()
                fullyRemovedBroadcastReceiverForSecondaryUser.reset()

                // Uninstall with "keep data" sends the REMOVED broadcast but not the FULLY_REMOVED
                // Only the targeted user will get the broadcast
                installPackage(TEST_HW5)
                // The UIDs have been changed after uninstall + reinstall
                uidPrimaryUser =
                        contextPrimaryUser.packageManager.getPackageUid(TEST_APP_PACKAGE, 0)
                uidSecondaryUser =
                        contextSecondaryUser.packageManager.getPackageUid(TEST_APP_PACKAGE, 0)
                assertNotEquals(uidPrimaryUser, uidSecondaryUser)
                uninstallPackageWithKeepData(TEST_APP_PACKAGE, secondaryUser)
                removedBroadcastReceiverForPrimaryUser.assertBroadcastNotReceived()
                fullyRemovedBroadcastReceiverForPrimaryUser.assertBroadcastNotReceived()
                removedBroadcastReceiverForSecondaryUser.assertBroadcastReceived()
                result = removedBroadcastReceiverForSecondaryUser.broadcastResult
                assertEquals(result.extras!!.getInt(Intent.EXTRA_UID, 0), uidSecondaryUser)
                fullyRemovedBroadcastReceiverForSecondaryUser.assertBroadcastNotReceived()
                removedBroadcastReceiverForSecondaryUser.reset()
                uninstallPackageWithKeepData(TEST_APP_PACKAGE, primaryUser)
                removedBroadcastReceiverForPrimaryUser.assertBroadcastReceived()
                result = removedBroadcastReceiverForPrimaryUser.broadcastResult
                assertEquals(result.extras!!.getInt(Intent.EXTRA_UID, 0), uidPrimaryUser)
                fullyRemovedBroadcastReceiverForPrimaryUser.assertBroadcastNotReceived()
                removedBroadcastReceiverForSecondaryUser.assertBroadcastNotReceived()
                fullyRemovedBroadcastReceiverForSecondaryUser.assertBroadcastNotReceived()
                removedBroadcastReceiverForPrimaryUser.reset()

                // Clean up
                contextPrimaryUser.unregisterReceiver(removedBroadcastReceiverForPrimaryUser)
                contextPrimaryUser.unregisterReceiver(fullyRemovedBroadcastReceiverForPrimaryUser)
                contextPrimaryUser.unregisterReceiver(uidRemovedBroadcastReceiverForPrimaryUser)
                contextSecondaryUser.unregisterReceiver(removedBroadcastReceiverForSecondaryUser)
                contextSecondaryUser.unregisterReceiver(
                    fullyRemovedBroadcastReceiverForSecondaryUser
                )
                contextSecondaryUser.unregisterReceiver(uidRemovedBroadcastReceiverForSecondaryUser)
                backgroundThread.interrupt()
            },
            Manifest.permission.INTERACT_ACROSS_USERS,
            Manifest.permission.INTERACT_ACROSS_USERS_FULL
        )
    }

    @Test
    fun testListPackageDefaultAllUsers() {
        installPackageAsUser(TEST_HW5, primaryUser)
        assertTrue(isAppInstalledForUser(TEST_APP_PACKAGE, primaryUser))
        assertFalse(isAppInstalledForUser(TEST_APP_PACKAGE, secondaryUser))
        var out = SystemUtil.runShellCommand(
            "pm list packages -U --user ${primaryUser.id()} $TEST_APP_PACKAGE"
        ).replace("\n", "")
        assertTrue(out.split(":").last().split(",").size == 1)
        var primaryUid = out.split(":").last().split(",").last()
        out = SystemUtil.runShellCommand(
            "pm list packages -U --user ${secondaryUser.id()} $TEST_APP_PACKAGE"
        ).replace("\n", "")
        assertEquals("", out)
        out = SystemUtil.runShellCommand("pm list packages -U $TEST_APP_PACKAGE")
            .replace("\n", "")
        var listedUids = out.split(":").last().split(",")
        assertTrue(out, listedUids.contains(primaryUid))
        // Install app on another user and verify the new UID is included in the list
        installExistingPackageAsUser(TEST_APP_PACKAGE, secondaryUser)
        assertTrue(isAppInstalledForUser(TEST_APP_PACKAGE, primaryUser))
        assertTrue(isAppInstalledForUser(TEST_APP_PACKAGE, secondaryUser))
        out = SystemUtil.runShellCommand(
                "pm list packages -U --user ${primaryUser.id()} $TEST_APP_PACKAGE"
        ).replace("\n", "")
        assertTrue(out, out.split(":").last().split(",").size == 1)
        assertEquals(primaryUid, out.split(":").last().split(",").last())
        out = SystemUtil.runShellCommand(
                "pm list packages -U --user ${secondaryUser.id()} $TEST_APP_PACKAGE"
        ).replace("\n", "")
        assertTrue(out, out.split(":").last().split(",").size == 1)
        var secondaryUid = out.split(":").last().split(",").last()
        out = SystemUtil.runShellCommand("pm list packages -U $TEST_APP_PACKAGE")
            .replace("\n", "")
        listedUids = out.split(":").last().split(",")
        assertTrue(out, listedUids.contains(primaryUid))
        assertTrue(out, listedUids.contains(secondaryUid))
    }

    @Test
    fun testCreateUserCurAsType() {
        val oldPropertyValue = getSystemProperty(UserManager.DEV_CREATE_OVERRIDE_PROPERTY)
        setSystemProperty(UserManager.DEV_CREATE_OVERRIDE_PROPERTY, "1")
        try {
            val pattern = Pattern.compile("Success: created user id (\\d+)\\R*")
            var commandResult = SystemUtil.runShellCommand(
                "pm create-user --profileOf cur " +
                    "--user-type android.os.usertype.profile.CLONE test"
            )
            var matcher = pattern.matcher(commandResult)
            assertTrue(commandResult, matcher.find())
            commandResult = SystemUtil.runShellCommand("pm remove-user " + matcher.group(1))
            assertEquals("Success: removed user\n", commandResult)
            commandResult = SystemUtil.runShellCommand(
                "pm create-user --profileOf current " +
                    "--user-type android.os.usertype.profile.CLONE test"
            )
            matcher = pattern.matcher(commandResult)
            assertTrue(commandResult, matcher.find())
            commandResult = SystemUtil.runShellCommand("pm remove-user " + matcher.group(1))
            assertEquals("Success: removed user\n", commandResult)
        } finally {
            setSystemProperty(
                UserManager.DEV_CREATE_OVERRIDE_PROPERTY,
                if (oldPropertyValue.isEmpty()) "invalid" else oldPropertyValue
            )
        }
    }

    @Test
    fun testGrantPermissionToSecondaryUser() {
        installPackageAsUser(TEST_HW5, secondaryUser)
        assertFalse(isAppInstalledForUser(TEST_APP_PACKAGE, primaryUser))
        assertTrue(isAppInstalledForUser(TEST_APP_PACKAGE, secondaryUser))
        var commandResult = PackageManagerShellCommandInstallTest.executeShellCommand(
                "pm grant --all-permissions --user ${secondaryUser.id()} $TEST_APP_PACKAGE"
        ).replace("\n", "")
        assertTrue(commandResult.isEmpty())
        commandResult = PackageManagerShellCommandInstallTest.executeShellCommand(
                "pm grant --all-permissions --user ${primaryUser.id()} $TEST_APP_PACKAGE"
        ).replace("\n", "")
        assertEquals(commandResult, "Failure [package not found]")
    }

    private fun testUninstallSetup() {
        // Install this test itself for the secondary user
        installExistingPackageAsUser(context.packageName, secondaryUser)
        assertTrue(isAppInstalledForUser(context.packageName, secondaryUser))
        // Install the test package on both users
        installPackageAsUser(TEST_HW5, primaryUser)
        installPackageAsUser(TEST_HW5, secondaryUser)
        assertThat(isAppInstalledForUser(TEST_APP_PACKAGE, primaryUser)).isTrue()
        assertThat(isAppInstalledForUser(TEST_APP_PACKAGE, secondaryUser)).isTrue()
        assertThat(getInstalledState(TEST_APP_PACKAGE, primaryUser.id()))
            .isEqualTo("true")
        assertThat(getInstalledState(TEST_APP_PACKAGE, secondaryUser.id()))
            .isEqualTo("true")
    }

    private fun matchFlag(packageName: String, userContext: Context, flag: Long) {
        // Expect not to throw
        userContext.packageManager.getPackageInfo(
                packageName,
                PackageInfoFlags.of(flag)
        )
    }

    private fun matchFlag(packageName: String, userContext: Context, flag: Int) {
        // Expect not to throw
        userContext.packageManager.getPackageInfo(
            packageName,
            PackageInfoFlags.of(flag.toLong())
        )
    }

    private fun notMatchFlag(packageName: String, userContext: Context, flag: Long) {
        assertThrows(PackageManager.NameNotFoundException::class.java) {
            userContext.packageManager.getPackageInfo(
                    packageName,
                    PackageInfoFlags.of(flag)
            )
        }
    }

    private fun notMatchFlag(packageName: String, userContext: Context, flag: Int) {
        assertThrows(PackageManager.NameNotFoundException::class.java) {
            userContext.packageManager.getPackageInfo(
                packageName,
                PackageInfoFlags.of(flag.toLong())
            )
        }
    }

    @Test
    @RequiresFlagsEnabled(FLAG_NULLABLE_DATA_DIR)
    fun testNullableDataDir() {
        testUninstallSetup()
        // Delete data on second user only
        uninstallPackageAsUser(TEST_APP_PACKAGE, secondaryUser)
        assertThat(getInstalledState(TEST_APP_PACKAGE, primaryUser.id())).isEqualTo("true")
        assertThat(getInstalledState(TEST_APP_PACKAGE, secondaryUser.id())).isEqualTo("false")
        assertThat(isAppInstalledForUser(TEST_APP_PACKAGE, primaryUser)).isTrue()
        assertThat(isAppInstalledForUser(TEST_APP_PACKAGE, secondaryUser)).isFalse()
        runWithShellPermissionIdentity(
                uiAutomation,
                {
                    val cxtPrimaryUser = context.createContextAsUser(primaryUser.userHandle(), 0)
                    val cxtSecondaryUser = context.createContextAsUser(
                            secondaryUser.userHandle(),
                            0
                    )
                    assertThat(hasDataDir(TEST_APP_PACKAGE, cxtPrimaryUser)).isTrue()
                    assertThat(hasDataDir(TEST_APP_PACKAGE, cxtSecondaryUser)).isFalse()
                },
                Manifest.permission.INTERACT_ACROSS_USERS,
                Manifest.permission.INTERACT_ACROSS_USERS_FULL
        )

        testUninstallSetup()
        // Delete data on secondary user but keep data on primary user
        uninstallPackageWithKeepData(TEST_APP_PACKAGE, primaryUser)
        uninstallPackageAsUser(TEST_APP_PACKAGE, secondaryUser)

        assertThat(getInstalledState(TEST_APP_PACKAGE, primaryUser.id())).isEqualTo("false")
        assertThat(getInstalledState(TEST_APP_PACKAGE, secondaryUser.id())).isEqualTo("false")
        assertThat(isAppInstalledForUser(TEST_APP_PACKAGE, primaryUser)).isFalse()
        assertThat(isAppInstalledForUser(TEST_APP_PACKAGE, secondaryUser)).isFalse()
        runWithShellPermissionIdentity(
                uiAutomation,
                {
                    val cxtPrimaryUser = context.createContextAsUser(primaryUser.userHandle(), 0)
                    val cxtSecondaryUser = context.createContextAsUser(
                            secondaryUser.userHandle(),
                            0
                    )
                    assertThat(hasDataDir(TEST_APP_PACKAGE, cxtPrimaryUser)).isTrue()
                    assertThat(hasDataDir(TEST_APP_PACKAGE, cxtSecondaryUser)).isFalse()
                },
                Manifest.permission.INTERACT_ACROSS_USERS,
                Manifest.permission.INTERACT_ACROSS_USERS_FULL
        )
    }

    private fun hasDataDir(packageName: String, userContext: Context): Boolean {
        return userContext.packageManager.getPackageInfo(
                packageName,
                PackageInfoFlags.of(MATCH_KNOWN_PACKAGES.toLong())
        ).applicationInfo!!.dataDir != null
    }

    @Test
    fun testUninstallMultiUser() {
        testUninstallSetup()
        // Delete app on both users
        uninstallPackageAsUser(TEST_APP_PACKAGE, primaryUser)
        uninstallPackageAsUser(TEST_APP_PACKAGE, secondaryUser)
        assertThat(getInstalledState(TEST_APP_PACKAGE, primaryUser.id())).isNull()
        assertThat(getInstalledState(TEST_APP_PACKAGE, secondaryUser.id())).isNull()
        assertThat(isAppInstalledForUser(TEST_APP_PACKAGE, primaryUser)).isFalse()
        assertThat(isAppInstalledForUser(TEST_APP_PACKAGE, secondaryUser)).isFalse()
        // Not queryable with any flag
        runWithShellPermissionIdentity(
            uiAutomation,
            {
                val cxtPrimaryUser = context.createContextAsUser(primaryUser.userHandle(), 0)
                val cxtSecondaryUser = context.createContextAsUser(secondaryUser.userHandle(), 0)
                notMatchFlag(TEST_APP_PACKAGE, cxtPrimaryUser, 0)
                notMatchFlag(TEST_APP_PACKAGE, cxtSecondaryUser, 0)
                notMatchFlag(TEST_APP_PACKAGE, cxtPrimaryUser, MATCH_ANY_USER)
                notMatchFlag(TEST_APP_PACKAGE, cxtSecondaryUser, MATCH_ANY_USER)
                notMatchFlag(TEST_APP_PACKAGE, cxtPrimaryUser, MATCH_UNINSTALLED_PACKAGES)
                notMatchFlag(TEST_APP_PACKAGE, cxtSecondaryUser, MATCH_UNINSTALLED_PACKAGES)
                notMatchFlag(TEST_APP_PACKAGE, cxtPrimaryUser, MATCH_KNOWN_PACKAGES)
                notMatchFlag(TEST_APP_PACKAGE, cxtSecondaryUser, MATCH_KNOWN_PACKAGES)
                notMatchFlag(TEST_APP_PACKAGE, cxtPrimaryUser, MATCH_ARCHIVED_PACKAGES)
                notMatchFlag(TEST_APP_PACKAGE, cxtSecondaryUser, MATCH_ARCHIVED_PACKAGES)
            },
            Manifest.permission.INTERACT_ACROSS_USERS,
            Manifest.permission.INTERACT_ACROSS_USERS_FULL
        )
    }

    @Test
    fun testUninstallWithoutKeepDataOnOneUser() {
        val preInstallTime = System.currentTimeMillis()
        testUninstallSetup()
        // Delete app on secondary user
        uninstallPackageAsUser(TEST_APP_PACKAGE, secondaryUser)
        assertThat(getInstalledState(TEST_APP_PACKAGE, primaryUser.id())).isEqualTo("true")
        assertThat(getInstalledState(TEST_APP_PACKAGE, secondaryUser.id())).isEqualTo("false")
        assertThat(isAppInstalledForUser(TEST_APP_PACKAGE, primaryUser)).isTrue()
        assertThat(isAppInstalledForUser(TEST_APP_PACKAGE, secondaryUser)).isFalse()
        assertThat(getFirstInstallTimeAsUser(TEST_APP_PACKAGE, primaryUser))
                .isGreaterThan(preInstallTime)
        assertThat(getFirstInstallTimeAsUser(TEST_APP_PACKAGE, secondaryUser)).isEqualTo(0)
        runWithShellPermissionIdentity(
            uiAutomation,
            {
                val cxtPrimaryUser = context.createContextAsUser(primaryUser.userHandle(), 0)
                val cxtSecondaryUser = context.createContextAsUser(secondaryUser.userHandle(), 0)
                // Queryable without any match flag on primary user
                matchFlag(TEST_APP_PACKAGE, cxtPrimaryUser, 0)
                matchFlag(TEST_APP_PACKAGE, cxtPrimaryUser, MATCH_ANY_USER)
                matchFlag(TEST_APP_PACKAGE, cxtPrimaryUser, MATCH_UNINSTALLED_PACKAGES)
                matchFlag(TEST_APP_PACKAGE, cxtPrimaryUser, MATCH_KNOWN_PACKAGES)
                matchFlag(TEST_APP_PACKAGE, cxtPrimaryUser, MATCH_ARCHIVED_PACKAGES)
                // Queryable with MATCH_ANY_USER/MATCH_KNOWN_PACKAGES on secondary user
                notMatchFlag(TEST_APP_PACKAGE, cxtSecondaryUser, 0)
                matchFlag(TEST_APP_PACKAGE, cxtSecondaryUser, MATCH_ANY_USER)
                notMatchFlag(TEST_APP_PACKAGE, cxtSecondaryUser, MATCH_UNINSTALLED_PACKAGES)
                matchFlag(TEST_APP_PACKAGE, cxtSecondaryUser, MATCH_KNOWN_PACKAGES)
                notMatchFlag(TEST_APP_PACKAGE, cxtSecondaryUser, MATCH_ARCHIVED_PACKAGES)
            },
            Manifest.permission.INTERACT_ACROSS_USERS,
            Manifest.permission.INTERACT_ACROSS_USERS_FULL
        )
    }

    @Test
    fun testUninstallWithKeepDataOnOneUser() {
        val preInstallTime = System.currentTimeMillis()
        testUninstallSetup()
        // Delete app on secondary user with DELETE_KEEP_DATA
        uninstallPackageWithKeepData(TEST_APP_PACKAGE, secondaryUser)
        assertThat(getInstalledState(TEST_APP_PACKAGE, primaryUser.id())).isEqualTo("true")
        assertThat(getInstalledState(TEST_APP_PACKAGE, secondaryUser.id())).isEqualTo("false")
        assertThat(isAppInstalledForUser(TEST_APP_PACKAGE, primaryUser)).isTrue()
        assertThat(isAppInstalledForUser(TEST_APP_PACKAGE, secondaryUser)).isFalse()
        assertThat(getFirstInstallTimeAsUser(TEST_APP_PACKAGE, primaryUser))
                .isGreaterThan(preInstallTime)
        assertThat(getFirstInstallTimeAsUser(TEST_APP_PACKAGE, secondaryUser))
                .isGreaterThan(preInstallTime)
        runWithShellPermissionIdentity(
            uiAutomation,
            {
                val cxtPrimaryUser = context.createContextAsUser(primaryUser.userHandle(), 0)
                val cxtSecondaryUser = context.createContextAsUser(secondaryUser.userHandle(), 0)
                // Queryable without any match flag on primary user
                matchFlag(TEST_APP_PACKAGE, cxtPrimaryUser, 0)
                matchFlag(TEST_APP_PACKAGE, cxtPrimaryUser, MATCH_ANY_USER)
                matchFlag(TEST_APP_PACKAGE, cxtPrimaryUser, MATCH_UNINSTALLED_PACKAGES)
                matchFlag(TEST_APP_PACKAGE, cxtPrimaryUser, MATCH_KNOWN_PACKAGES)
                matchFlag(TEST_APP_PACKAGE, cxtPrimaryUser, MATCH_ARCHIVED_PACKAGES)
                // Queryable with match flags on secondary user
                notMatchFlag(TEST_APP_PACKAGE, cxtSecondaryUser, 0)
                matchFlag(TEST_APP_PACKAGE, cxtSecondaryUser, MATCH_ANY_USER)
                matchFlag(TEST_APP_PACKAGE, cxtSecondaryUser, MATCH_UNINSTALLED_PACKAGES)
                matchFlag(TEST_APP_PACKAGE, cxtSecondaryUser, MATCH_KNOWN_PACKAGES)
                matchFlag(TEST_APP_PACKAGE, cxtSecondaryUser, MATCH_ARCHIVED_PACKAGES)
            },
            Manifest.permission.INTERACT_ACROSS_USERS,
            Manifest.permission.INTERACT_ACROSS_USERS_FULL
        )
    }

    @Test
    fun testUninstallWithKeepDataOnBothUsers() {
        val preInstallTime = System.currentTimeMillis()
        testUninstallSetup()
        // Delete app on both users with DELETE_KEEP_DATA
        uninstallPackageWithKeepData(TEST_APP_PACKAGE, primaryUser)
        uninstallPackageWithKeepData(TEST_APP_PACKAGE, secondaryUser)
        assertThat(getInstalledState(TEST_APP_PACKAGE, primaryUser.id())).isEqualTo("false")
        assertThat(getInstalledState(TEST_APP_PACKAGE, secondaryUser.id())).isEqualTo("false")
        assertThat(isAppInstalledForUser(TEST_APP_PACKAGE, primaryUser)).isFalse()
        assertThat(isAppInstalledForUser(TEST_APP_PACKAGE, secondaryUser)).isFalse()
        assertThat(getFirstInstallTimeAsUser(TEST_APP_PACKAGE, primaryUser))
                .isGreaterThan(preInstallTime)
        assertThat(getFirstInstallTimeAsUser(TEST_APP_PACKAGE, secondaryUser))
                .isGreaterThan(preInstallTime)
        runWithShellPermissionIdentity(
            uiAutomation,
            {
                val cxtPrimaryUser = context.createContextAsUser(primaryUser.userHandle(), 0)
                val cxtSecondaryUser = context.createContextAsUser(secondaryUser.userHandle(), 0)
                // Not queryable with MATCH_ANY_USER flag as the AndroidPackage object was removed
                notMatchFlag(TEST_APP_PACKAGE, cxtPrimaryUser, 0)
                notMatchFlag(TEST_APP_PACKAGE, cxtPrimaryUser, MATCH_ANY_USER)
                matchFlag(TEST_APP_PACKAGE, cxtPrimaryUser, MATCH_UNINSTALLED_PACKAGES)
                matchFlag(TEST_APP_PACKAGE, cxtPrimaryUser, MATCH_KNOWN_PACKAGES)
                matchFlag(TEST_APP_PACKAGE, cxtPrimaryUser, MATCH_ARCHIVED_PACKAGES)
                notMatchFlag(TEST_APP_PACKAGE, cxtSecondaryUser, 0)
                notMatchFlag(TEST_APP_PACKAGE, cxtSecondaryUser, MATCH_ANY_USER)
                matchFlag(TEST_APP_PACKAGE, cxtSecondaryUser, MATCH_UNINSTALLED_PACKAGES)
                matchFlag(TEST_APP_PACKAGE, cxtSecondaryUser, MATCH_KNOWN_PACKAGES)
                matchFlag(TEST_APP_PACKAGE, cxtSecondaryUser, MATCH_ARCHIVED_PACKAGES)
            },
            Manifest.permission.INTERACT_ACROSS_USERS,
            Manifest.permission.INTERACT_ACROSS_USERS_FULL
        )
    }

    @Test
    fun testUninstallWithKeepDataOnOneUsersAndWithoutKeepDataOnAnotherUser() {
        val preInstallTime = System.currentTimeMillis()
        testUninstallSetup()
        // Delete app on primary users without DELETE_KEEP_DATA
        uninstallPackageAsUser(TEST_APP_PACKAGE, primaryUser)
        // Delete app on secondary users with DELETE_KEEP_DATA
        uninstallPackageWithKeepData(TEST_APP_PACKAGE, secondaryUser)
        assertThat(getInstalledState(TEST_APP_PACKAGE, primaryUser.id())).isEqualTo("false")
        assertThat(getInstalledState(TEST_APP_PACKAGE, secondaryUser.id())).isEqualTo("false")
        assertThat(isAppInstalledForUser(TEST_APP_PACKAGE, primaryUser)).isFalse()
        assertThat(isAppInstalledForUser(TEST_APP_PACKAGE, secondaryUser)).isFalse()
        assertThat(getFirstInstallTimeAsUser(TEST_APP_PACKAGE, primaryUser)).isEqualTo(0)
        assertThat(getFirstInstallTimeAsUser(TEST_APP_PACKAGE, secondaryUser))
                .isGreaterThan(preInstallTime)
        runWithShellPermissionIdentity(
            uiAutomation,
            {
                val cxtPrimaryUser = context.createContextAsUser(primaryUser.userHandle(), 0)
                val cxtSecondaryUser = context.createContextAsUser(secondaryUser.userHandle(), 0)
                // Only queryable with MATCH_KNOWN_PACKAGES flag on primary user
                notMatchFlag(TEST_APP_PACKAGE, cxtPrimaryUser, 0)
                notMatchFlag(TEST_APP_PACKAGE, cxtPrimaryUser, MATCH_ANY_USER)
                notMatchFlag(TEST_APP_PACKAGE, cxtPrimaryUser, MATCH_UNINSTALLED_PACKAGES)
                matchFlag(TEST_APP_PACKAGE, cxtPrimaryUser, MATCH_KNOWN_PACKAGES)
                notMatchFlag(TEST_APP_PACKAGE, cxtPrimaryUser, MATCH_ARCHIVED_PACKAGES)
                // Queryable on secondary user with MATCH_UNINSTALLED_PACKAGES/MATCH_KNOWN_PACKAGES
                notMatchFlag(TEST_APP_PACKAGE, cxtSecondaryUser, 0)
                notMatchFlag(TEST_APP_PACKAGE, cxtSecondaryUser, MATCH_ANY_USER)
                matchFlag(TEST_APP_PACKAGE, cxtSecondaryUser, MATCH_UNINSTALLED_PACKAGES)
                matchFlag(TEST_APP_PACKAGE, cxtSecondaryUser, MATCH_KNOWN_PACKAGES)
                matchFlag(TEST_APP_PACKAGE, cxtSecondaryUser, MATCH_ARCHIVED_PACKAGES)
            },
            Manifest.permission.INTERACT_ACROSS_USERS,
            Manifest.permission.INTERACT_ACROSS_USERS_FULL
        )
    }

    @Test
    fun testDeleteSystemAppMultiuser() {
        var ctsShimPackageInfo: PackageInfo? = null
        try {
            ctsShimPackageInfo = context.packageManager.getPackageInfo(
                    CTS_SHIM_PACKAGE_NAME,
                    PackageInfoFlags.of((MATCH_SYSTEM_ONLY or MATCH_KNOWN_PACKAGES).toLong()))
        } catch (e: PackageManager.NameNotFoundException) {
        }
        // Skip the test if CtsShim cannot be found
        assumeTrue(ctsShimPackageInfo != null)
        try {
            runWithShellPermissionIdentity(
                    uiAutomation,
                    {
                        // Delete the system package with DELETE_SYSTEM_APP on primary user
                        uninstallPackageAsUser(CTS_SHIM_PACKAGE_NAME, primaryUser)
                        assertThat(PackageManagerTest.matchesInstalled(
                                context.packageManager,
                                CTS_SHIM_PACKAGE_NAME,
                                primaryUser.id(),
                                0
                        )).isFalse()
                        assertThat(PackageManagerTest.matchesInstalled(
                                context.packageManager,
                                CTS_SHIM_PACKAGE_NAME,
                                primaryUser.id(),
                                MATCH_DISABLED_COMPONENTS.toLong()
                        )).isFalse()
                        assertThat(PackageManagerTest.matchesInstalled(
                                context.packageManager,
                                CTS_SHIM_PACKAGE_NAME,
                                primaryUser.id(),
                                MATCH_DISABLED_UNTIL_USED_COMPONENTS.toLong()
                        )).isFalse()
                        assertThat(PackageManagerTest.matchesInstalled(
                                context.packageManager,
                                CTS_SHIM_PACKAGE_NAME,
                                primaryUser.id(),
                                MATCH_HIDDEN_UNTIL_INSTALLED_COMPONENTS.toLong()
                        )).isTrue()
                        assertThat(PackageManagerTest.matchesInstalled(
                                context.packageManager,
                                CTS_SHIM_PACKAGE_NAME,
                                primaryUser.id(),
                                MATCH_KNOWN_PACKAGES.toLong()
                        )).isTrue()
                        // Delete the system package with DELETE_SYSTEM_APP on secondary user
                        uninstallPackageAsUser(CTS_SHIM_PACKAGE_NAME, secondaryUser)
                        assertThat(PackageManagerTest.matchesInstalled(
                                context.packageManager,
                                CTS_SHIM_PACKAGE_NAME,
                                secondaryUser.id(),
                                0
                        )).isFalse()
                        assertThat(PackageManagerTest.matchesInstalled(
                                context.packageManager,
                                CTS_SHIM_PACKAGE_NAME,
                                secondaryUser.id(),
                                MATCH_DISABLED_COMPONENTS.toLong()
                        )).isFalse()
                        assertThat(PackageManagerTest.matchesInstalled(
                                context.packageManager,
                                CTS_SHIM_PACKAGE_NAME,
                                secondaryUser.id(),
                                MATCH_DISABLED_UNTIL_USED_COMPONENTS.toLong()
                        )).isFalse()
                        assertThat(PackageManagerTest.matchesInstalled(
                                context.packageManager,
                                CTS_SHIM_PACKAGE_NAME,
                                secondaryUser.id(),
                                MATCH_HIDDEN_UNTIL_INSTALLED_COMPONENTS.toLong()
                        )).isTrue()
                        assertThat(PackageManagerTest.matchesInstalled(
                                context.packageManager,
                                CTS_SHIM_PACKAGE_NAME,
                                secondaryUser.id(),
                                MATCH_KNOWN_PACKAGES.toLong()
                        )).isTrue()
                    },
                    Manifest.permission.INTERACT_ACROSS_USERS,
                    Manifest.permission.INTERACT_ACROSS_USERS_FULL
            )
        } finally {
            installExistingPackageAsUser(CTS_SHIM_PACKAGE_NAME, primaryUser)
            installExistingPackageAsUser(CTS_SHIM_PACKAGE_NAME, secondaryUser)
        }
    }

    private fun getUserManager(): UserManager {
        return context.getSystemService(UserManager::class.java)!!
    }

    private fun getFirstInstallTimeAsUser(packageName: String, user: UserReference) =
        context.createContextAsUser(user.userHandle(), 0)
            .packageManager
            .getPackageInfo(packageName, PackageInfoFlags.of(MATCH_KNOWN_PACKAGES.toLong()))
            .firstInstallTime

    private fun installPackage(baseName: String) {
        val file = File(PackageManagerShellCommandInstallTest.createApkPath(baseName))
        assertThat(SystemUtil.runShellCommand("pm install -t -g ${file.path}"))
            .isEqualTo("Success\n")
    }

    private fun installExistingPackageAsUser(packageName: String, user: UserReference) {
        val userId = user.id()
        assertThat(SystemUtil.runShellCommand("pm install-existing --user $userId $packageName"))
            .isEqualTo("Package $packageName installed for user: $userId\n")
    }

    private fun installPackageAsUser(
        baseName: String,
        user: UserReference
    ) {
        val file = File(PackageManagerShellCommandInstallTest.createApkPath(baseName))
        assertThat(
            SystemUtil.runShellCommand(
                "pm install -t -g --user ${user.id()} ${file.path}"
            )
        )
            .isEqualTo("Success\n")
    }

    private fun uninstallPackageAsUser(packageName: String, user: UserReference) =
        assertThat(SystemUtil.runShellCommand("pm uninstall --user ${user.id()} $packageName"))
            .isEqualTo("Success\n")

    private fun uninstallPackageWithKeepData(packageName: String, user: UserReference) =
        SystemUtil.runShellCommand("pm uninstall -k --user ${user.id()} $packageName")

    private fun uninstallPackageSilently(packageName: String) =
        SystemUtil.runShellCommand("pm uninstall $packageName")

    private fun isAppInstalledForUser(packageName: String, user: UserReference) =
        SystemUtil.runShellCommand("pm list packages --user ${user.id()} $packageName")
            .split("\\r?\\n".toRegex())
            .any { it == "package:$packageName" }

    private fun setSystemProperty(name: String, value: String) =
        assertThat(SystemUtil.runShellCommand("setprop $name $value"))
            .isEmpty()

    fun getSystemProperty(prop: String): String {
        return SystemUtil.runShellCommand("getprop $prop").replace("\n", "")
    }
}
