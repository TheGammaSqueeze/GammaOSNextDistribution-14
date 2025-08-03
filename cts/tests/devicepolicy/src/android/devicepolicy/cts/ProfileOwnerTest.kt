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
package android.devicepolicy.cts

import com.android.bedstead.harrier.BedsteadJUnit4
import com.android.bedstead.harrier.DeviceState
import com.android.bedstead.harrier.UserType
import com.android.bedstead.harrier.annotations.EnsureHasAccount
import com.android.bedstead.harrier.annotations.EnsureHasNoAccounts
import com.android.bedstead.harrier.annotations.FailureMode
import com.android.bedstead.harrier.annotations.Postsubmit
import com.android.bedstead.harrier.annotations.enterprise.EnsureHasNoDpc
import com.android.bedstead.harrier.annotations.enterprise.EnsureHasProfileOwner
import com.android.bedstead.nene.TestApis
import com.android.bedstead.nene.exceptions.AdbException
import com.android.bedstead.nene.exceptions.NeneException
import com.android.bedstead.nene.packages.ComponentReference
import com.android.bedstead.nene.packages.Package
import com.android.bedstead.nene.utils.Poll
import com.android.bedstead.nene.utils.ShellCommand
import com.android.bedstead.nene.utils.ShellCommandUtils
import com.android.bedstead.remotedpc.RemoteDpc
import com.android.eventlib.truth.EventLogsSubject
import com.google.common.truth.Truth
import com.google.common.truth.Truth.assertThat
import org.junit.Assert
import org.junit.Assert.assertThrows
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(BedsteadJUnit4::class)
class ProfileOwnerTest {
    @EnsureHasNoDpc
    @EnsureHasNoAccounts(onUser = UserType.ANY)
    @Test
    fun setProfileOwnerViaAdb_noAccounts_testOnly_sets() {
        TEST_ONLY_DPC.install().use {
            try {
                ShellCommand
                    .builderForUser(TestApis.users().instrumented(), SET_PROFILE_OWNER_COMMAND)
                    .addOperand(TEST_ONLY_DPC_COMPONENT.flattenToString())
                    .validate { ShellCommandUtils.startsWithSuccess(it) }
                    .execute()
                assertThat(TestApis.devicePolicy().getProfileOwner()).isNotNull()
            } finally {
                val profileOwner = TestApis.devicePolicy().getProfileOwner()
                profileOwner?.remove()
            }
        }
    }

    @EnsureHasNoDpc
    // This explicitly requires no pre-created accounts so we must skip if there are any
    @EnsureHasNoAccounts(
        onUser = UserType.ANY, allowPreCreatedAccounts = false, failureMode = FailureMode.SKIP)
    @Test
    fun setProfileOwnerViaAdb_noAccounts_notTestOnly_sets() {
        NOT_TEST_ONLY_DPC.install().use {
            try {
                ShellCommand
                    .builderForUser(TestApis.users().instrumented(), SET_PROFILE_OWNER_COMMAND)
                    .addOperand(NOT_TEST_ONLY_DPC_COMPONENT.flattenToString())
                    .validate { ShellCommandUtils.startsWithSuccess(it) }
                    .execute()
                assertThat(TestApis.devicePolicy().getProfileOwner()).isNotNull()
            } finally {
                val profileOwner = TestApis.devicePolicy().getProfileOwner()
                profileOwner?.remove()
            }
        }
    }

    @EnsureHasNoDpc
    @EnsureHasNoAccounts(onUser = UserType.ANY)
    @EnsureHasAccount(features = [FEATURE_DISALLOW], onUser = UserType.INITIAL_USER)
    @Test
    fun setProfileOwnerViaAdb_invalidAccountOnParent_sets() {
        TestApis.users().createUser()
            .parent(TestApis.users().instrumented())
            .type(com.android.bedstead.nene.users.UserType.MANAGED_PROFILE_TYPE_NAME)
            .createAndStart().use { profile ->
                NOT_TEST_ONLY_DPC.install(profile)
                ShellCommand
                    .builderForUser(profile, SET_PROFILE_OWNER_COMMAND)
                    .addOperand(NOT_TEST_ONLY_DPC_COMPONENT.flattenToString())
                    .validate { ShellCommandUtils.startsWithSuccess(it) }
                    .execute()
                assertThat(TestApis.devicePolicy().getProfileOwner(profile)).isNotNull()
            }
    }

    @EnsureHasNoDpc
    @EnsureHasNoAccounts(onUser = UserType.ANY)
    @EnsureHasAccount(features = [], onUser = UserType.INITIAL_USER)
    @Test
    fun setProfileOwnerViaAdb_accountExistsWithNoFeatures_doesNotSet() {
        try {
            TEST_ONLY_DPC.install().use {
                try {
                    assertThrows(AdbException::class.java) {
                        ShellCommand
                            .builderForUser(
                                TestApis.users().instrumented(),
                                SET_PROFILE_OWNER_COMMAND
                            )
                            .addOperand(TEST_ONLY_DPC_COMPONENT.flattenToString())
                            .execute()
                    }
                    assertThat(TestApis.devicePolicy().getDeviceOwner()).isNull()
                } finally {
                    val profileOwner = TestApis.devicePolicy().getProfileOwner()
                    profileOwner?.remove()
                }
            }
        } finally {
            // After attempting and failing to set the profiel owner, it will remain as an active
            // admin for a short while
            Poll.forValue(
                "Active admins"
            ) { TestApis.devicePolicy().getActiveAdmins() }
                .toMeet { !it.contains(TEST_ONLY_DPC_COMPONENT) }
                .errorOnFail("Expected active admins to not contain DPC")
                .await()
        }
    }

    @EnsureHasNoDpc
    @EnsureHasNoAccounts(onUser = UserType.ANY)
    @EnsureHasAccount(features = [FEATURE_DISALLOW], onUser = UserType.INITIAL_USER)
    @Test
    fun setProfileOwnerViaAdb_accountExistsWithDisallowFeature_doesNotSet() {
        try {
            TEST_ONLY_DPC.install().use {
                try {
                    assertThrows(AdbException::class.java) {
                        ShellCommand
                            .builderForUser(
                                TestApis.users().instrumented(),
                                SET_PROFILE_OWNER_COMMAND
                            )
                            .addOperand(TEST_ONLY_DPC_COMPONENT.flattenToString())
                            .execute()
                    }
                    assertThat(TestApis.devicePolicy().getProfileOwner()).isNull()
                } finally {
                    val profileOwner = TestApis.devicePolicy().getProfileOwner()
                    profileOwner?.remove()
                }
            }
        } finally {
            // After attempting and failing to set the profile owner, it will remain as an active
            // admin for a short while
            Poll.forValue(
                "Active admins"
            ) { TestApis.devicePolicy().getActiveAdmins() }
                .toMeet { !it.contains(TEST_ONLY_DPC_COMPONENT) }
                .errorOnFail("Expected active admins to not contain DPC")
                .await()
        }
    }

    @EnsureHasNoDpc
    @EnsureHasNoAccounts(onUser = UserType.ANY)
    @EnsureHasAccount(features = [FEATURE_ALLOW, FEATURE_DISALLOW], onUser = UserType.INITIAL_USER)
    @Test
    fun setProfileOwnerViaAdb_accountExistsWithDisallowAndAllowFeatures_doesNotSet() {
        try {
            TEST_ONLY_DPC.install().use {
                try {
                    assertThrows(AdbException::class.java) {
                        ShellCommand
                            .builderForUser(
                                TestApis.users().instrumented(),
                                SET_PROFILE_OWNER_COMMAND
                            )
                            .addOperand(TEST_ONLY_DPC_COMPONENT.flattenToString())
                            .execute()
                    }
                    assertThat(TestApis.devicePolicy().getProfileOwner()).isNull()
                } finally {
                    val profileOwner = TestApis.devicePolicy().getProfileOwner()
                    profileOwner?.remove()
                }
            }
        } finally {
            // After attempting and failing to set the profile owner, it will remain as an active
            // admin for a short while
            Poll.forValue(
                "Active admins"
            ) { TestApis.devicePolicy().getActiveAdmins() }
                .toMeet { !it.contains(TEST_ONLY_DPC_COMPONENT) }
                .errorOnFail("Expected active admins to not contain DPC")
                .await()
        }
    }

    @EnsureHasNoDpc
    @EnsureHasNoAccounts(onUser = UserType.ANY)
    @EnsureHasAccount(features = [FEATURE_ALLOW], onUser = UserType.INITIAL_USER)
    @Test
    fun setProfileOwnerViaAdb_accountExistsWithAllowFeature_testOnly_sets() {
        TEST_ONLY_DPC.install().use {
            try {
                ShellCommand
                    .builderForUser(TestApis.users().instrumented(), SET_PROFILE_OWNER_COMMAND)
                    .addOperand(TEST_ONLY_DPC_COMPONENT.flattenToString())
                    .validate { ShellCommandUtils.startsWithSuccess(it) }
                    .execute()
                assertThat(TestApis.devicePolicy().getProfileOwner()).isNotNull()
            } finally {
                val profileOwner = TestApis.devicePolicy().getProfileOwner()
                profileOwner?.remove()
            }
        }
    }

    @EnsureHasNoDpc
    @EnsureHasNoAccounts(onUser = UserType.ANY)
    @EnsureHasAccount(features = [FEATURE_ALLOW], onUser = UserType.INITIAL_USER)
    @Test
    fun setProfileOwnerViaAdb_accountExistsWithAllowFeature_notTestOnly_doesNotSet() {
        try {
            NOT_TEST_ONLY_DPC.install().use {
                try {
                    assertThrows(AdbException::class.java) {
                        ShellCommand
                            .builderForUser(
                                TestApis.users().instrumented(),
                                SET_PROFILE_OWNER_COMMAND
                            )
                            .addOperand(NOT_TEST_ONLY_DPC_COMPONENT.flattenToString())
                            .execute()
                    }
                    assertThat(TestApis.devicePolicy().getProfileOwner()).isNull()
                } finally {
                    val profileOwner = TestApis.devicePolicy().getProfileOwner()
                    profileOwner?.remove()
                }
            }
        } finally {
            // After attempting and failing to set the profile owner, it will remain as an active
            // admin for a short while
            Poll.forValue(
                "Active admins"
            ) { TestApis.devicePolicy().getActiveAdmins() }
                .toMeet { !it.contains(NOT_TEST_ONLY_DPC_COMPONENT) }
                .errorOnFail("Expected active admins to not contain DPC")
                .await()
        }
    }

    @Test
    @Postsubmit(reason = "new test")
    @EnsureHasProfileOwner
    fun disableComponentInProfileOwnerViaAdb_throwsException() {
        val component = deviceState.dpc().packageName() + "/" +
                deviceState.dpc().testApp().activities().query().get().className()
        try {
            Package.of(component).disable()
            Assert.fail("AdbException should be thrown here")
        } catch (e: NeneException) {
            assertThat(e.cause is AdbException).isTrue()
            assertThat((e.cause as AdbException?)!!.error()).contains(
                "Cannot disable a protected package: ${deviceState.dpc().packageName()}"
            )
        }
    }

    @Test
    @Postsubmit(reason = "new test")
    @EnsureHasProfileOwner
    fun disableProfileOwnerViaAdb_throwsException() {
        try {
            deviceState.dpc().pkg().disable()
            Assert.fail("AdbException should be thrown here")
        } catch (e: NeneException) {
            assertThat(e.cause is AdbException).isTrue()
            assertThat((e.cause as AdbException?)!!.error()).contains(
                "Cannot disable a protected package: ${deviceState.dpc().packageName()}"
            )
        }
    }

    @Test
    @EnsureHasNoDpc
    fun setAsProfileOwner_onEnabledIsCalled() {
        RemoteDpc.setAsProfileOwner().use {
            EventLogsSubject.assertThat(it.events().deviceAdminEnabled()).eventOccurred()
        }
    }

    companion object {
        
        @JvmField
        @ClassRule
        @Rule
        val deviceState = DeviceState()
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
        private val NOT_TEST_ONLY_DPC_COMPONENT = NOT_TEST_ONLY_DPC.pkg().component(
            NOT_TEST_ONLY_DPC.packageName() + ".DeviceAdminReceiver"
        )
        private const val SET_PROFILE_OWNER_COMMAND = "dpm set-profile-owner"
        private const val FEATURE_ALLOW = "android.account.DEVICE_OR_PROFILE_OWNER_ALLOWED"
        private const val FEATURE_DISALLOW = "android.account.DEVICE_OR_PROFILE_OWNER_DISALLOWED"
    }
}
