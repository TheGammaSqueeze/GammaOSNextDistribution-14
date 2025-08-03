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
package android.devicepolicy.cts

import android.app.admin.DevicePolicyManager
import android.app.admin.SecurityLog
import android.app.admin.flags.Flags
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import com.android.bedstead.harrier.BedsteadJUnit4
import com.android.bedstead.harrier.DeviceState
import com.android.bedstead.harrier.UserType
import com.android.bedstead.harrier.annotations.EnsureDoesNotHavePermission
import com.android.bedstead.harrier.annotations.EnsureHasAdditionalUser
import com.android.bedstead.harrier.annotations.EnsureHasNoAdditionalUser
import com.android.bedstead.harrier.annotations.EnsureHasPermission
import com.android.bedstead.harrier.annotations.Postsubmit
import com.android.bedstead.harrier.annotations.enterprise.CanSetPolicyTest
import com.android.bedstead.harrier.annotations.enterprise.CannotSetPolicyTest
import com.android.bedstead.harrier.annotations.enterprise.EnsureHasProfileOwner
import com.android.bedstead.harrier.policies.GlobalSecurityLogging
import com.android.bedstead.harrier.policies.SecurityLogging
import com.android.bedstead.nene.TestApis
import com.android.bedstead.nene.exceptions.NeneException
import com.android.bedstead.nene.permissions.CommonPermissions
import com.android.bedstead.nene.users.UserReference
import com.android.compatibility.common.util.ApiTest
import com.android.compatibility.common.util.BlockingCallback
import com.android.eventlib.truth.EventLogsSubject
import com.google.common.truth.Truth
import java.util.concurrent.Executors
import java.util.function.Consumer
import org.junit.ClassRule
import org.junit.Rule
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.testng.Assert.assertThrows

@RunWith(BedsteadJUnit4::class)
class SecurityLoggingTest {
    @CannotSetPolicyTest(policy = [GlobalSecurityLogging::class, SecurityLogging::class])
    @Postsubmit(reason = "new test")
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#setSecurityLoggingEnabled"])
    fun setSecurityLoggingEnabled_notPermitted_throwsException() {
        assertThrows(SecurityException::class.java) {
            deviceState.dpc().devicePolicyManager().setSecurityLoggingEnabled(
                deviceState.dpc().componentName(),
                true
            )
        }
    }

    @CanSetPolicyTest(policy = [GlobalSecurityLogging::class, SecurityLogging::class])
    @Postsubmit(reason = "new test")
    @ApiTest(
        apis = ["android.app.admin.DevicePolicyManager#setSecurityLoggingEnabled",
            "android.app.admin.DevicePolicyManager#isSecurityLoggingEnabled"]
    )
    fun setSecurityLoggingEnabled_true_securityLoggingIsEnabled() {
        val originalSecurityLoggingEnabled = deviceState.dpc()
            .devicePolicyManager().isSecurityLoggingEnabled(deviceState.dpc().componentName())
        try {
            deviceState.dpc().devicePolicyManager().setSecurityLoggingEnabled(
                deviceState.dpc().componentName(),
                true
            )
            Truth.assertThat(
                deviceState.dpc().devicePolicyManager().isSecurityLoggingEnabled(
                    deviceState.dpc().componentName()
                )
            ).isTrue()
        } finally {
            deviceState.dpc().devicePolicyManager().setSecurityLoggingEnabled(
                deviceState.dpc().componentName(),
                originalSecurityLoggingEnabled
            )
        }
    }

    @CanSetPolicyTest(policy = [GlobalSecurityLogging::class, SecurityLogging::class])
    @Postsubmit(reason = "new test")
    @ApiTest(
        apis = ["android.app.admin.DevicePolicyManager#setSecurityLoggingEnabled",
            "android.app.admin.DevicePolicyManager#isSecurityLoggingEnabled"]
    )
    fun setSecurityLoggingEnabled_false_securityLoggingIsNotEnabled() {
        val originalSecurityLoggingEnabled = deviceState.dpc()
            .devicePolicyManager().isSecurityLoggingEnabled(deviceState.dpc().componentName())
        try {
            deviceState.dpc().devicePolicyManager().setSecurityLoggingEnabled(
                deviceState.dpc().componentName(),
                false
            )
            Truth.assertThat(
                deviceState.dpc().devicePolicyManager().isSecurityLoggingEnabled(
                    deviceState.dpc().componentName()
                )
            ).isFalse()
        } finally {
            deviceState.dpc().devicePolicyManager().setSecurityLoggingEnabled(
                deviceState.dpc().componentName(),
                originalSecurityLoggingEnabled
            )
        }
    }

    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#isSecurityLoggingEnabled"])
    @Postsubmit(reason = "new test")
    @CannotSetPolicyTest(policy = [GlobalSecurityLogging::class, SecurityLogging::class])
    fun isSecurityLoggingEnabled_notPermitted_throwsException() {
        assertThrows(SecurityException::class.java) {
            deviceState.dpc().devicePolicyManager().isSecurityLoggingEnabled(
                deviceState.dpc().componentName()
            )
        }
    }

    @CannotSetPolicyTest(policy = [GlobalSecurityLogging::class, SecurityLogging::class])
    @Postsubmit(reason = "new test")
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#retrieveSecurityLogs"])
    fun retrieveSecurityLogs_notPermitted_throwsException() {
        ensureNoAdditionalFullUsers()
        assertThrows(SecurityException::class.java) {
            deviceState.dpc().devicePolicyManager().retrieveSecurityLogs(
                deviceState.dpc().componentName()
            )
        }
    }

    @CanSetPolicyTest(policy = [GlobalSecurityLogging::class, SecurityLogging::class])
    @Postsubmit(reason = "new test")
    @ApiTest(
        apis = ["android.app.admin.DeviceAdminReceiver#onSecurityLogsAvailable",
        "android.app.admin.DevicePolicyManager#retrieveSecurityLogs"]
    )
    fun retrieveSecurityLogs_logsCanBeFetchedAfterOnSecurityLogsAvailableCallback() {
        ensureNoAdditionalFullUsers()

        val dpc = deviceState.dpc()

        try {
            dpc.devicePolicyManager().setSecurityLoggingEnabled(dpc.componentName(), true)

            TestApis.devicePolicy().forceSecurityLogs()
            EventLogsSubject.assertThat(dpc.events().securityLogsAvailable()).eventOccurred()

            val logs = dpc.devicePolicyManager().retrieveSecurityLogs(dpc.componentName())
            Truth.assertThat(logs).isNotNull()
            Truth.assertThat(logs.stream().filter { e -> e.tag == SecurityLog.TAG_LOGGING_STARTED })
                    .isNotNull()
        } finally {
            dpc.devicePolicyManager().setSecurityLoggingEnabled(dpc.componentName(), false)
        }
    }

    // TODO: Add test that logs are filtered for non-device-owner
    // TODO: Add test for rate limiting

    @CannotSetPolicyTest(policy = [GlobalSecurityLogging::class, SecurityLogging::class])
    @Postsubmit(reason = "new test")
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#retrievePreRebootSecurityLogs"])
    fun retrievePreRebootSecurityLogs_notPermitted_throwsException() {
        ensureNoAdditionalFullUsers()

        assertThrows(SecurityException::class.java) {
            deviceState.dpc().devicePolicyManager().retrievePreRebootSecurityLogs(
                deviceState.dpc().componentName()
            )
        }
    }

    @CanSetPolicyTest(policy = [GlobalSecurityLogging::class, SecurityLogging::class])
    @Postsubmit(reason = "new test")
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#retrievePreRebootSecurityLogs"])
    fun retrievePreRebootSecurityLogs_doesNotThrow() {
        ensureNoAdditionalFullUsers()
        val originalSecurityLoggingEnabled = deviceState.dpc().devicePolicyManager()
            .isSecurityLoggingEnabled(deviceState.dpc().componentName())
        try {
            deviceState.dpc().devicePolicyManager().setSecurityLoggingEnabled(
                deviceState.dpc().componentName(),
                true
            )

            // Nothing to assert as this can be null on some devices
            deviceState.dpc().devicePolicyManager().retrievePreRebootSecurityLogs(
                deviceState.dpc().componentName()
            )
        } finally {
            deviceState.dpc().devicePolicyManager().setSecurityLoggingEnabled(
                deviceState.dpc().componentName(),
                originalSecurityLoggingEnabled
            )
        }
    }

    @CanSetPolicyTest(policy = [GlobalSecurityLogging::class], singleTestOnly = true)
    @EnsureHasAdditionalUser
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#retrieveSecurityLogs"])
    fun retrieveSecurityLogs_unaffiliatedAdditionalUser_throwsException() {
        try {
            deviceState.dpc().devicePolicyManager().setSecurityLoggingEnabled(
                deviceState.dpc().componentName(),
                true
            )
            assertThrows(SecurityException::class.java) {
                deviceState.dpc().devicePolicyManager().retrieveSecurityLogs(
                    deviceState.dpc().componentName()
                )
            }
        } finally {
            deviceState.dpc().devicePolicyManager().setSecurityLoggingEnabled(
                deviceState.dpc().componentName(),
                false
            )
        }
    }

    @CanSetPolicyTest(policy = [GlobalSecurityLogging::class, SecurityLogging::class])
    @EnsureHasAdditionalUser
    @EnsureHasProfileOwner(onUser = UserType.ADDITIONAL_USER, affiliationIds = ["affiliated"])
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#retrieveSecurityLogs"])
    fun retrieveSecurityLogs_affiliatedAdditionalUser_doesNotThrowException() {
        // TODO(273474964): Move into infra
        TestApis.users().all().stream()
            .filter { u: UserReference ->
                (u != TestApis.users().instrumented() &&
                        u != TestApis.users().system() &&
                        u != deviceState.additionalUser() &&
                        u != TestApis.users().current() &&
                        !u.isMain)
            }
            .forEach { obj: UserReference -> obj.remove() }
        val affiliationIds: MutableSet<String> = HashSet(
            deviceState.dpcOnly().devicePolicyManager()
                .getAffiliationIds(deviceState.dpcOnly().componentName())
        )
        affiliationIds.add("affiliated")
        deviceState.dpcOnly().devicePolicyManager().setAffiliationIds(
            deviceState.dpc().componentName(),
            affiliationIds
        )

        try {
            deviceState.dpc().devicePolicyManager().setSecurityLoggingEnabled(
                deviceState.dpc().componentName(),
                true
            )
            deviceState.dpc().devicePolicyManager().retrieveSecurityLogs(
                deviceState.dpc().componentName()
            )
        } finally {
            deviceState.dpc().devicePolicyManager().setSecurityLoggingEnabled(
                deviceState.dpc().componentName(),
                false
            )
        }
    }

    @CanSetPolicyTest(policy = [GlobalSecurityLogging::class, SecurityLogging::class])
    @EnsureHasNoAdditionalUser
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#retrieveSecurityLogs"])
    fun retrieveSecurityLogs_noAdditionalUser_doesNotThrowException() {
        ensureNoAdditionalFullUsers()

        try {
            deviceState.dpc().devicePolicyManager().setSecurityLoggingEnabled(
                deviceState.dpc().componentName(),
                true
            )
            deviceState.dpc().devicePolicyManager().retrieveSecurityLogs(
                deviceState.dpc().componentName()
            )
        } finally {
            deviceState.dpc().devicePolicyManager().setSecurityLoggingEnabled(
                deviceState.dpc().componentName(),
                false
            )
        }
    }

    @CanSetPolicyTest(policy = [GlobalSecurityLogging::class])
    @EnsureHasAdditionalUser
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#retrievePreRebootSecurityLogs"])
    fun retrievePreRebootSecurityLogs_unaffiliatedAdditionalUser_throwsException() {
        try {
            deviceState.dpc().devicePolicyManager().setSecurityLoggingEnabled(
                deviceState.dpc().componentName(),
                true
            )
            assertThrows(SecurityException::class.java) {
                deviceState.dpc().devicePolicyManager().retrievePreRebootSecurityLogs(
                    deviceState.dpc().componentName()
                )
            }
        } finally {
            deviceState.dpc().devicePolicyManager().setSecurityLoggingEnabled(
                deviceState.dpc().componentName(),
                false
            )
        }
    }

    @CanSetPolicyTest(policy = [GlobalSecurityLogging::class, SecurityLogging::class])
    @EnsureHasAdditionalUser
    @EnsureHasProfileOwner(onUser = UserType.ADDITIONAL_USER, affiliationIds = ["affiliated"])
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#retrievePreRebootSecurityLogs"])
    fun retrievePreRebootSecurityLogs_affiliatedAdditionalUser_doesNotThrowException() {
        // TODO(273474964): Move into infra
        TestApis.users().all().stream()
            .filter { u: UserReference ->
                (u != TestApis.users().instrumented() &&
                        u != TestApis.users().system() &&
                        u != deviceState.additionalUser() &&
                        u != TestApis.users().current())
            }
            .forEach { obj: UserReference -> obj.remove() }
        val affiliationIds: MutableSet<String> = HashSet(
            deviceState.dpcOnly().devicePolicyManager()
                .getAffiliationIds(deviceState.dpcOnly().componentName())
        )
        affiliationIds.add("affiliated")
        deviceState.dpcOnly().devicePolicyManager().setAffiliationIds(
            deviceState.dpc().componentName(),
            affiliationIds
        )

        try {
            deviceState.dpc().devicePolicyManager().setSecurityLoggingEnabled(
                deviceState.dpc().componentName(),
                true
            )
            deviceState.dpc().devicePolicyManager().retrievePreRebootSecurityLogs(
                deviceState.dpc().componentName()
            )
        } finally {
            deviceState.dpc().devicePolicyManager().setSecurityLoggingEnabled(
                deviceState.dpc().componentName(),
                false
            )
        }
    }

    @CanSetPolicyTest(policy = [GlobalSecurityLogging::class, SecurityLogging::class])
    @EnsureHasNoAdditionalUser
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#retrievePreRebootSecurityLogs"])
    fun retrievePreRebootSecurityLogs_noAdditionalUser_doesNotThrowException() {
        ensureNoAdditionalFullUsers()

        try {
            deviceState.dpc().devicePolicyManager().setSecurityLoggingEnabled(
                deviceState.dpc().componentName(),
                true
            )
            deviceState.dpc().devicePolicyManager().retrievePreRebootSecurityLogs(
                deviceState.dpc().componentName()
            )
        } finally {
            deviceState.dpc().devicePolicyManager().setSecurityLoggingEnabled(
                deviceState.dpc().componentName(),
                false
            )
        }
    }

    /**
     * Here and below - audit logging is only available on corporate owned devices, i.e. in the same
     * configurations when security logging is enabled, so this is a PolicyTest.
     */
    @CanSetPolicyTest(policy = [GlobalSecurityLogging::class, SecurityLogging::class])
    @Postsubmit(reason = "new test")
    @ApiTest(
            apis = ["android.app.admin.DevicePolicyManager#setAuditLogEnabled",
                "android.app.admin.DevicePolicyManager#isAuditLogEnabled"]
    )
    @EnsureHasPermission(CommonPermissions.MANAGE_DEVICE_POLICY_AUDIT_LOGGING)
    @RequiresFlagsEnabled(Flags.FLAG_SECURITY_LOG_V2_ENABLED)
    fun setAuditLogEnabled_withPermission_works() {
        ensureNoAdditionalFullUsers()

        localDpm.setAuditLogEnabled(true)
        try {
            Truth.assertThat(localDpm.isAuditLogEnabled()).isTrue()
        } finally {
            localDpm.setAuditLogEnabled(false)
        }
    }

    @CanSetPolicyTest(policy = [GlobalSecurityLogging::class, SecurityLogging::class])
    @Postsubmit(reason = "new test")
    @ApiTest(
            apis = ["android.app.admin.DevicePolicyManager#setAuditLogEnabled"]
    )
    @EnsureDoesNotHavePermission(CommonPermissions.MANAGE_DEVICE_POLICY_AUDIT_LOGGING)
    @RequiresFlagsEnabled(Flags.FLAG_SECURITY_LOG_V2_ENABLED)
    fun setAuditLogEnabled_withoutPermission_throws() {
        ensureNoAdditionalFullUsers()

        try {
            assertThrows(SecurityException::class.java) {
                localDpm.setAuditLogEnabled(true)
            }
        } finally {
            try {
                localDpm.setAuditLogEnabled(false)
            } catch (e: Exception) {
                // ignored - the policy shouldn't be set in the first place.
            }
        }
    }

    @CanSetPolicyTest(policy = [GlobalSecurityLogging::class, SecurityLogging::class])
    @Postsubmit(reason = "new test")
    @ApiTest(
            apis = ["android.app.admin.DevicePolicyManager#setAuditLogEnabled",
                "android.app.admin.DevicePolicyManager#setAuditLogEventCallback"]
    )
    @EnsureHasPermission(CommonPermissions.MANAGE_DEVICE_POLICY_AUDIT_LOGGING)
    @RequiresFlagsEnabled(Flags.FLAG_SECURITY_LOG_V2_ENABLED)
    fun setAuditLogEventCallback_callbackInvoked() {
        ensureNoAdditionalFullUsers()

        localDpm.setAuditLogEnabled(true)
        try {
            val callback = SecurityEventCallback()

            localDpm.setAuditLogEventCallback(executor, callback)

            Truth.assertThat(callback.await().stream().filter { e ->
                e.tag == SecurityLog.TAG_LOGGING_STARTED
            }).isNotNull()
        } finally {
            localDpm.setAuditLogEnabled(false)
        }
    }

    private fun ensureNoAdditionalFullUsers() {
        // TODO(273474964): Move into infra
        try {
            TestApis.users().all().stream()
                .filter { u: UserReference ->
                    (u != TestApis.users().instrumented() &&
                            u != TestApis.users().system() &&
                            u != TestApis.users()
                        .current() && // We can't remove the profile of the instrumented user for
                            // the run on parent profile tests. But the profiles of other users
                            // will be removed when the full-user is removed anyway.
                            !u.isProfile)
                }
                .forEach { obj: UserReference -> obj.remove() }
        } catch (e: NeneException) {
            // Happens when we can't remove a user
            throw NeneException(
                "Error when removing user. Instrumented user is " +
                        TestApis.users().instrumented() + ", current user is " +
                        TestApis.users().current() + ", system user is " +
                        TestApis.users().system(),
                e
            )
        }
    }

    class SecurityEventCallback : BlockingCallback<List<SecurityLog.SecurityEvent>>(),
            Consumer<List<SecurityLog.SecurityEvent>> {
        override fun accept(events: List<SecurityLog.SecurityEvent>) {
            callbackTriggered(events)
        }
    }

    companion object {
        @JvmField
        @ClassRule
        val deviceState = DeviceState()

        val context = TestApis.context().instrumentedContext()
        val executor = Executors.newSingleThreadExecutor()
        var localDpm = context.getSystemService(DevicePolicyManager::class.java)!!
    }

    @JvmField
    @Rule
    val mCheckFlagsRule = RuleChain
            .outerRule(DeviceFlagsValueProvider.createCheckFlagsRule())
            .around(deviceState)
}
