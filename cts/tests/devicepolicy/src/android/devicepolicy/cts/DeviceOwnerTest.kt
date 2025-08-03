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
package android.devicepolicy.cts

import android.app.admin.DevicePolicyManager
import android.content.pm.PackageManager
import com.android.bedstead.harrier.BedsteadJUnit4
import com.android.bedstead.harrier.DeviceState
import com.android.bedstead.harrier.UserType
import com.android.bedstead.harrier.annotations.EnsureCanAddUser
import com.android.bedstead.harrier.annotations.EnsureHasAccount
import com.android.bedstead.harrier.annotations.EnsureHasAdditionalUser
import com.android.bedstead.harrier.annotations.EnsureHasNoAccounts
import com.android.bedstead.harrier.annotations.EnsureHasPermission
import com.android.bedstead.harrier.annotations.FailureMode
import com.android.bedstead.harrier.annotations.Postsubmit
import com.android.bedstead.harrier.annotations.RequireFeature
import com.android.bedstead.harrier.annotations.RequireNotHeadlessSystemUserMode
import com.android.bedstead.harrier.annotations.RequireRunOnAdditionalUser
import com.android.bedstead.harrier.annotations.RequireRunOnSystemUser
import com.android.bedstead.harrier.annotations.UserTest
import com.android.bedstead.harrier.annotations.enterprise.EnsureHasDeviceOwner
import com.android.bedstead.harrier.annotations.enterprise.EnsureHasNoDpc
import com.android.bedstead.nene.TestApis
import com.android.bedstead.nene.exceptions.AdbException
import com.android.bedstead.nene.exceptions.NeneException
import com.android.bedstead.nene.packages.ComponentReference
import com.android.bedstead.nene.packages.Package
import com.android.bedstead.nene.permissions.CommonPermissions
import com.android.bedstead.nene.utils.Poll
import com.android.bedstead.nene.utils.ShellCommand
import com.android.bedstead.nene.utils.ShellCommandUtils
import com.android.bedstead.remotedpc.RemoteDpc
import com.android.compatibility.common.util.ApiTest
import com.android.eventlib.truth.EventLogsSubject.assertThat
import com.google.common.truth.Truth.assertThat
import org.junit.Assert
import org.junit.Assume.assumeFalse
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(BedsteadJUnit4::class)
class DeviceOwnerTest {

    @Test
    @Postsubmit(reason = "new test")
    @EnsureHasDeviceOwner
    @RequireRunOnSystemUser
    fun isDeviceOwnerApp_is_returnsTrue() {
        assertThat(devicePolicyManager.isDeviceOwnerApp(deviceState.dpc().packageName()))
            .isTrue()
    }

    @Test
    @Postsubmit(reason = "new test")
    @EnsureHasDeviceOwner
    @RequireRunOnSystemUser
    fun isDeviceOwnerApp_isNot_returnsFalse() {
        assertThat(devicePolicyManager.isDeviceOwnerApp("fake.package.name"))
            .isFalse()
    }

    @Test
    @Postsubmit(reason = "new test")
    @EnsureHasDeviceOwner
    @RequireRunOnAdditionalUser
    fun isDeviceOwnerApp_isButOnDifferentUser_returnsFalse() {
        assertThat(devicePolicyManager.isDeviceOwnerApp(deviceState.dpc().packageName()))
            .isFalse()
    }

    @Test
    @Postsubmit(reason = "new test")
    @EnsureHasDeviceOwner
    @RequireRunOnSystemUser
    fun setDeviceOwner_setsDeviceOwner() {
        assertThat(devicePolicyManager.isAdminActive(deviceState.dpc().componentName()!!))
            .isTrue()
        assertThat(devicePolicyManager.isDeviceOwnerApp(deviceState.dpc().packageName()))
            .isTrue()
        assertThat(devicePolicyManager.deviceOwner).isEqualTo(deviceState.dpc().packageName())
    }

    @Postsubmit(reason = "new test")
    @EnsureHasPermission(CommonPermissions.MANAGE_PROFILE_AND_DEVICE_OWNERS)
    @EnsureHasDeviceOwner
    @UserTest(
        UserType.SYSTEM_USER,
        UserType.SECONDARY_USER
    )
    fun getDeviceOwnerNameOnAnyUser_returnsDeviceOwnerName() {
            assertThat(devicePolicyManager.deviceOwnerNameOnAnyUser)
                .isEqualTo(deviceState.dpc().testApp().label())
        }

    @Postsubmit(reason = "new test")
    @EnsureHasPermission(CommonPermissions.MANAGE_PROFILE_AND_DEVICE_OWNERS)
    @EnsureHasDeviceOwner
    @UserTest(
        UserType.SYSTEM_USER,
        UserType.SECONDARY_USER
    )
    fun getDeviceOwnerComponentOnAnyUser_returnsDeviceOwnerComponent() {
            assertThat(devicePolicyManager.deviceOwnerComponentOnAnyUser)
                .isEqualTo(deviceState.dpc().componentName())
        }

    // All via adb methods use an additional user as we assume if it works cross user it'll work
    // same user
    // We use ensureHasNoAccounts and then create the account during the test because we want
    // to ensure there is only one account (and bedstead currently doesn't support this)
    @EnsureHasAdditionalUser
    @EnsureHasNoDpc
    @EnsureHasNoAccounts(onUser = UserType.ANY)
    @Test
    fun setDeviceOwnerViaAdb_noAccounts_testOnly_sets() {
        TEST_ONLY_DPC.install(TestApis.users().system()).use {
            try {
                ShellCommand
                    .builder(SET_DEVICE_OWNER_COMMAND)
                    .addOperand(TEST_ONLY_DPC_COMPONENT.flattenToString())
                    .validate { ShellCommandUtils.startsWithSuccess(it) }
                    .execute()
                assertThat(TestApis.devicePolicy().getDeviceOwner()).isNotNull()
            } finally {
                val deviceOwner = TestApis.devicePolicy().getDeviceOwner()
                deviceOwner?.remove()
            }
        }
    }

    @EnsureHasAdditionalUser
    @EnsureHasNoDpc
    // This explicitly requires no pre-created accounts so we must skip if there are any
    @EnsureHasNoAccounts(
        onUser = UserType.ANY,
        allowPreCreatedAccounts = false,
        failureMode = FailureMode.SKIP
    )
    @RequireNotHeadlessSystemUserMode(reason = "No non-testonly dpc which supports headless")
    @Test
    fun setDeviceOwnerViaAdb_noAccounts_notTestOnly_sets() {
        NOT_TEST_ONLY_DPC.install(TestApis.users().system()).use {
            try {
                ShellCommand
                    .builder(SET_DEVICE_OWNER_COMMAND)
                    .addOperand(NOT_TEST_ONLY_DPC_COMPONENT.flattenToString())
                    .validate { ShellCommandUtils.startsWithSuccess(it) }
                    .execute()
                assertThat(TestApis.devicePolicy().getDeviceOwner()).isNotNull()
            } finally {
                val deviceOwner = TestApis.devicePolicy().getDeviceOwner()
                deviceOwner?.remove()
            }
        }
    }

    @EnsureHasAdditionalUser
    @EnsureHasNoDpc
    @EnsureHasNoAccounts(onUser = UserType.ANY)
    @EnsureHasAccount(features = [], onUser = UserType.ADDITIONAL_USER)
    @Test
    fun setDeviceOwnerViaAdb_accountExistsWithNoFeatures_doesNotSet() {
        try {
            TEST_ONLY_DPC.install(TestApis.users().system()).use {
                try {
                    Assert.assertThrows(AdbException::class.java) {
                        ShellCommand
                            .builder(SET_DEVICE_OWNER_COMMAND)
                            .addOperand(TEST_ONLY_DPC_COMPONENT.flattenToString())
                            .execute()
                    }
                    assertThat(TestApis.devicePolicy().getDeviceOwner()).isNull()
                } finally {
                    val deviceOwner = TestApis.devicePolicy().getDeviceOwner()
                    deviceOwner?.remove()
                }
            }
        } finally {
            // After attempting and failing to set the device owner, it will remain as an active
            // admin for a short while
            Poll.forValue(
                "Active admins"
            ) {
                TestApis.devicePolicy().getActiveAdmins(TestApis.users().system())
            }
                .toMeet { !it.contains(TEST_ONLY_DPC_COMPONENT) }
                .errorOnFail("Expected active admins to not contain DPC")
                .await()
        }
    }

    @EnsureHasAdditionalUser
    @EnsureHasNoDpc
    @EnsureHasNoAccounts(onUser = UserType.ANY)
    @EnsureHasAccount(features = [FEATURE_DISALLOW], onUser = UserType.ADDITIONAL_USER)
    @Test
    fun setDeviceOwnerViaAdb_accountExistsWithDisallowFeature_doesNotSet() {
        try {
            TEST_ONLY_DPC.install(TestApis.users().system()).use {
                try {
                    Assert.assertThrows(AdbException::class.java) {
                        ShellCommand
                            .builder(SET_DEVICE_OWNER_COMMAND)
                            .addOperand(TEST_ONLY_DPC_COMPONENT.flattenToString())
                            .execute()
                    }
                    assertThat(TestApis.devicePolicy().getDeviceOwner()).isNull()
                } finally {
                    TestApis.devicePolicy().getDeviceOwner()?.remove()
                }
            }
        } finally {
            TestApis.devicePolicy().getDeviceOwner()?.remove()
            // After attempting and failing to set the device owner, it will remain as an active
            // admin for a short while
            Poll.forValue(
                "Active admins"
            ) { TestApis.devicePolicy().getActiveAdmins(TestApis.users().system()) }
                .toMeet { !it.contains(TEST_ONLY_DPC_COMPONENT) }
                .errorOnFail("Expected active admins to not contain DPC")
                .await()
        }
    }

    @EnsureHasAdditionalUser
    @EnsureHasNoDpc
    @EnsureHasNoAccounts(onUser = UserType.ANY)
    @EnsureHasAccount(
        features = [FEATURE_ALLOW, FEATURE_DISALLOW],
        onUser = UserType.ADDITIONAL_USER
    )
    @Test
    fun setDeviceOwnerViaAdb_accountExistsWithDisallowAndAllowFeatures_doesNotSet() {
        try {
            TEST_ONLY_DPC.install(TestApis.users().system()).use {
                try {
                    Assert.assertThrows(AdbException::class.java) {
                        ShellCommand
                            .builder(SET_DEVICE_OWNER_COMMAND)
                            .addOperand(TEST_ONLY_DPC_COMPONENT.flattenToString())
                            .execute()
                    }
                    assertThat(TestApis.devicePolicy().getDeviceOwner()).isNull()
                } finally {
                    val deviceOwner = TestApis.devicePolicy().getDeviceOwner()
                    deviceOwner?.remove()
                }
            }
        } finally {
            // After attempting and failing to set the device owner, it will remain as an active
            // admin for a short while
            Poll.forValue(
                "Active admins"
            ) { TestApis.devicePolicy().getActiveAdmins(TestApis.users().system()) }
                .toMeet { !it.contains(TEST_ONLY_DPC_COMPONENT) }
                .errorOnFail("Expected active admins to not contain DPC")
                .await()
        }
    }

    @EnsureHasAdditionalUser
    @EnsureHasNoDpc
    @EnsureHasNoAccounts(onUser = UserType.ANY)
    @EnsureHasAccount(features = [FEATURE_ALLOW], onUser = UserType.ADDITIONAL_USER)
    @Test
    fun setDeviceOwnerViaAdb_accountExistsWithAllowFeature_testOnly_sets() {
        TEST_ONLY_DPC.install(TestApis.users().system()).use {
            try {
                ShellCommand
                    .builder(SET_DEVICE_OWNER_COMMAND)
                    .addOperand(TEST_ONLY_DPC_COMPONENT.flattenToString())
                    .validate { ShellCommandUtils.startsWithSuccess(it) }
                    .execute()
                assertThat(TestApis.devicePolicy().getDeviceOwner()).isNotNull()
            } finally {
                val deviceOwner = TestApis.devicePolicy().getDeviceOwner()
                deviceOwner?.remove()
            }
        }
    }

    @EnsureHasAdditionalUser
    @EnsureHasNoDpc
    @EnsureHasNoAccounts(onUser = UserType.ANY)
    @EnsureHasAccount(features = [FEATURE_ALLOW], onUser = UserType.ADDITIONAL_USER)
    @Test
    fun setDeviceOwnerViaAdb_accountExistsWithAllowFeature_notTestOnly_doesNotSet() {
        try {
            NOT_TEST_ONLY_DPC.install(TestApis.users().system()).use {
                try {
                    Assert.assertThrows(AdbException::class.java) {
                        ShellCommand
                            .builder(SET_DEVICE_OWNER_COMMAND)
                            .addOperand(NOT_TEST_ONLY_DPC_COMPONENT.flattenToString())
                            .validate { ShellCommandUtils.startsWithSuccess(it) }
                            .execute()
                    }
                    assertThat(TestApis.devicePolicy().getDeviceOwner()).isNull()
                } finally {
                    val deviceOwner = TestApis.devicePolicy().getDeviceOwner()
                    deviceOwner?.remove()
                }
            }
        } finally {
            // After attempting and failing to set the device owner, it will remain as an active
            // admin for a short while
            Poll.forValue(
                "Active admins"
            ) { TestApis.devicePolicy().getActiveAdmins(TestApis.users().system()) }
                .toMeet { i: Set<ComponentReference> -> !i.contains(NOT_TEST_ONLY_DPC_COMPONENT) }
                .errorOnFail("Expected active admins to not contain DPC")
                .await()
        }
    }

    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#ACTION_DEVICE_OWNER_CHANGED"])
    @EnsureHasNoDpc
    @Postsubmit(reason = "new test")
    @Test
    fun setDeviceOwner_receivesOwnerChangedBroadcast() {
        try {
            deviceState.registerBroadcastReceiver(
                DevicePolicyManager.ACTION_DEVICE_OWNER_CHANGED
            ).use { broadcastReceiver ->
                RemoteDpc.setAsDeviceOwner()
                broadcastReceiver.awaitForBroadcastOrFail()
            }
        } finally {
            val deviceOwner = TestApis.devicePolicy().getDeviceOwner()
            deviceOwner?.remove()
        }
    }

    @EnsureHasNoDpc
    @EnsureCanAddUser
    @Postsubmit(reason = "new test")
    @Test
    fun setDeviceOwner_hasSecondaryUser_throwsException() {
        TestApis.users().createUser().forTesting(false).create().use {
            try {
                Assert.assertThrows(
                    "Error setting device owner.",
                    NeneException::class.java
                ) {
                    RemoteDpc.setAsDeviceOwner()
                }
            } finally {
                val deviceOwner = TestApis.devicePolicy().getDeviceOwner()
                deviceOwner?.remove()
            }
        }
    }

    @Test
    @Postsubmit(reason = "new test")
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#isProvisioningAllowed"])
    fun getIsProvisioningAllowed_forManagedDevice_setupWizardIsComplete_returnsFalse() {
            TestApis.users().instrumented().setupComplete = true
            remoteDpcTestApp.install().use {
                assertThat(
                    devicePolicyManager.isProvisioningAllowed(
                        DevicePolicyManager.ACTION_PROVISION_MANAGED_DEVICE
                    )
                ).isFalse()
            }
        }

    @RequireFeature(PackageManager.FEATURE_SECURE_LOCK_SCREEN)
    @EnsureHasDeviceOwner
    @Test
    fun clearDeviceOwnerApp_escrowTokenExists_success() {
        skipTestIfEscrowDisabled {
            val dpm = deviceState.dpc().devicePolicyManager()

            assertThat(
                dpm.setResetPasswordToken(
                    deviceState.dpc().componentName(),
                    ByteArray(32)
                )
            ).isTrue()

            // clearDeviceOwnerApp should not throw, and isDeviceOwnerApp should return false afterwards
            dpm.clearDeviceOwnerApp(deviceState.dpc().packageName())

            assertThat(dpm.isDeviceOwnerApp(deviceState.dpc().packageName())).isFalse()
        }
    }

    @EnsureHasDeviceOwner
    @Test
    @Postsubmit(reason = "new test")
    fun disableComponentInDeviceOwnerViaAdb_throwsException() {
        val component = deviceState.dpc().packageName() + "/" +
                deviceState.dpc().testApp().activities().query().get().className()
        try {
            Package.of(component).disable()
            Assert.fail("AdbException should be thrown here.")
        } catch (e: NeneException) {
            assertThat(e.cause is AdbException).isTrue()
            assertThat((e.cause as AdbException?)!!.error()).contains(
                "Cannot disable a protected package: ${deviceState.dpc().packageName()}"
            )
        }
    }

    @EnsureHasDeviceOwner
    @Test
    @Postsubmit(reason = "new test")
    fun disableDeviceOwnerViaAdb_throwsException() {
        try {
            deviceState.dpc().pkg().disable()
            Assert.fail("AdbException should be thrown here.")
        } catch (e: NeneException) {
            assertThat(e.cause is AdbException).isTrue()
            assertThat((e.cause as AdbException?)!!.error()).contains(
                "Cannot disable a protected package: ${deviceState.dpc().packageName()}"
            )
        }
    }

    @Test
    @EnsureHasNoDpc
    fun setAsDeviceOwner_onEnabledIsCalled() {
        RemoteDpc.setAsDeviceOwner().use {
            assertThat(it.events().deviceAdminEnabled()).eventOccurred()
        }
    }

    private fun skipTestIfEscrowDisabled(r: Runnable) {
        try {
            r.run()
        } catch (e: SecurityException) {
            assumeFalse(
                "Escrow token disabled",
                e.message?.contains("Escrow token is disabled on the current user") ?: false
            )
            throw e
        }
    }

    companion object {
        @JvmField
        @ClassRule
        @Rule
        val deviceState = DeviceState()

        private val context = TestApis.context().instrumentedContext()
        private val TEST_ONLY_DPC = deviceState.testApps()
            .query().whereIsDeviceAdmin().isTrue()
            .whereTestOnly().isTrue()
            .get()

        // TODO: We should be able to query for the receiver rather than hardcoding
        private val TEST_ONLY_DPC_COMPONENT = TEST_ONLY_DPC.pkg().component(
            TEST_ONLY_DPC.packageName() + ".DeviceAdminReceiver"
        )
        private val NOT_TEST_ONLY_DPC = deviceState.testApps()
            .query().whereIsDeviceAdmin().isTrue()
            .whereTestOnly().isFalse()
            .get()
        private val remoteDpcTestApp = deviceState.testApps().query()
            .wherePackageName().isEqualTo(RemoteDpc.REMOTE_DPC_APP_PACKAGE_NAME_OR_PREFIX)
            .get()
        private val NOT_TEST_ONLY_DPC_COMPONENT = NOT_TEST_ONLY_DPC.pkg().component(
            NOT_TEST_ONLY_DPC.packageName() + ".DeviceAdminReceiver"
        )
        private const val SET_DEVICE_OWNER_COMMAND = "dpm set-device-owner"
        private val devicePolicyManager = context.getSystemService(
            DevicePolicyManager::class.java
        )!!
        private const val FEATURE_ALLOW = "android.account.DEVICE_OR_PROFILE_OWNER_ALLOWED"
        private const val FEATURE_DISALLOW = "android.account.DEVICE_OR_PROFILE_OWNER_DISALLOWED"
    }
}
