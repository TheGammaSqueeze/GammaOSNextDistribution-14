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

import android.app.Notification
import android.stats.devicepolicy.EventId
import com.android.bedstead.harrier.BedsteadJUnit4
import com.android.bedstead.harrier.DeviceState
import com.android.bedstead.harrier.UserType
import com.android.bedstead.harrier.annotations.EnsureHasAdditionalUser
import com.android.bedstead.harrier.annotations.EnsureHasNoAdditionalUser
import com.android.bedstead.harrier.annotations.EnsureWillTakeQuickBugReports
import com.android.bedstead.harrier.annotations.NotificationsTest
import com.android.bedstead.harrier.annotations.enterprise.CanSetPolicyTest
import com.android.bedstead.harrier.annotations.enterprise.CannotSetPolicyTest
import com.android.bedstead.harrier.annotations.enterprise.EnsureHasProfileOwner
import com.android.bedstead.harrier.policies.RequestBugReport
import com.android.bedstead.metricsrecorder.EnterpriseMetricsRecorder
import com.android.bedstead.metricsrecorder.truth.MetricQueryBuilderSubject
import com.android.bedstead.metricsrecorder.truth.MetricQueryBuilderSubject.assertThat
import com.android.bedstead.nene.TestApis
import com.android.bedstead.nene.types.OptionalBoolean
import com.android.bedstead.nene.types.OptionalBoolean.TRUE
import com.android.bedstead.nene.users.UserReference
import com.android.bedstead.remotedpc.RemoteDpc
import com.android.compatibility.common.util.ApiTest
import com.android.eventlib.truth.EventLogsSubject
import com.android.interactive.Step
import com.android.interactive.annotations.Interactive
import com.android.interactive.steps.enterprise.bugreport.BugReportNotificationDeclineToShareBugReportStep
import com.android.interactive.steps.enterprise.bugreport.BugReportNotificationShareBugReportStep
import android.util.Log
import com.android.bedstead.harrier.annotations.SlowApiTest
import com.android.bedstead.nene.notifications.NotificationListener
import com.android.bedstead.nene.utils.Poll
import com.android.eventlib.truth.EventLogsSubject.assertThat
import com.google.common.truth.Truth
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.ClassRule
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.testng.Assert.assertThrows
import java.util.stream.Collectors

@RunWith(BedsteadJUnit4::class)
@EnsureWillTakeQuickBugReports
@NotificationsTest
@SlowApiTest("Bug reports are slow - even when skipping most of dumpsys")
class BugReportTest {
    // TODO: Figure out how to test onBugreportFailed callback

    // TODO: Add (interactive?) tests that proper transparency is given during bug reports

    @Before
    @After
    fun cleanupBugreportNotifications() {
        TestApis.notifications().createListener().use { notifications ->
            cleanupBugreportNotification(notifications)
        }
    }

    @CanSetPolicyTest(policy = [RequestBugReport::class], singleTestOnly = true)
    @Test
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#requestBugReport",
        "android.app.admin.DeviceAdminReceiver#onBugReportShared"])
    @NotificationsTest
    fun requestBugReport_userAgrees_bugreportIsGenerated() {
        removeOtherUsers()

        TestApis.notifications().createListener().use { notifications ->
            deviceState.dpc().devicePolicyManager()
                .requestBugreport(deviceState.dpc().componentName())

            val n = notifications.query().whereNotification().extras().key(Notification.EXTRA_TITLE)
                .stringValue().startsWith("Share bug report").poll()!!
            // The second action is to share
            n.notification.actions[1].actionIntent.send()

            assertThat(deviceState.dpc().events().bugReportShared()).eventOccurred()
        }
    }

    @CanSetPolicyTest(policy = [RequestBugReport::class], singleTestOnly = true)
    @Test
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#requestBugReport",
        "android.app.admin.DeviceAdminReceiver#onBugReportSharingDeclined"])
    @NotificationsTest
    fun requestBugReport_userDoesNotAgree_callbackIsTriggered() {
        removeOtherUsers()

        TestApis.notifications().createListener().use { notifications ->
            deviceState.dpc().devicePolicyManager()
                .requestBugreport(deviceState.dpc().componentName())

            val n = notifications.query().whereNotification().extras().key(Notification.EXTRA_TITLE)
                .stringValue().startsWith("Share bug report").poll()!!
            // The first action is to decline
            n.notification.actions[0].actionIntent.send()

            assertThat(deviceState.dpc().events().bugReportSharingDeclined()).eventOccurred()
        }
    }

    @CannotSetPolicyTest(policy = [RequestBugReport::class], includeNonDeviceAdminStates = false)
    @EnsureHasNoAdditionalUser
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#requestBugreport"])
    fun requestBugreport_notAllowed_throwsException() {
        assertThrows(SecurityException::class.java) {
            deviceState.dpc().devicePolicyManager().requestBugreport(
                deviceState.dpc().componentName()
            )
        }
    }

    @CanSetPolicyTest(policy = [RequestBugReport::class], singleTestOnly = true)
    @EnsureHasAdditionalUser
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#requestBugreport"])
    fun requestBugReport_unaffiliatedAdditionalUser_throwsException() {
        assertThrows(SecurityException::class.java) {
            deviceState.dpc().devicePolicyManager().requestBugreport(
                deviceState.dpc().componentName()
            )
        }
    }

    @CanSetPolicyTest(policy = [RequestBugReport::class])
    @EnsureHasAdditionalUser(switchedToUser = TRUE)
    @EnsureHasProfileOwner(onUser = UserType.ADDITIONAL_USER, affiliationIds = ["affiliated"])
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#requestBugreport"])
    @NotificationsTest
    fun requestBugReport_affiliatedAdditionalUser_doesNotThrowException() {
        // TODO(273474964): Move into infra
        TestApis.users().all().stream()
            .filter { u: UserReference ->
                (u != TestApis.users().instrumented()
                        && u != deviceState.additionalUser()
                        && u != TestApis.users().current())
            }
            .forEach { obj: UserReference -> obj.remove() }
        val affiliationIds = HashSet(
            deviceState.dpcOnly().devicePolicyManager()
                .getAffiliationIds(deviceState.dpcOnly().componentName())
        )
        affiliationIds.add("affiliated")
        deviceState.dpcOnly().devicePolicyManager().setAffiliationIds(
            deviceState.dpc().componentName(),
            affiliationIds
        )

        TestApis.notifications().createListener().use { notifications ->
            val result = deviceState.dpc().devicePolicyManager()
                .requestBugreport(deviceState.dpc().componentName())

            assertThat(result).isTrue()

            cleanupBugreportNotification(notifications)
        }
    }

    @CanSetPolicyTest(policy = [RequestBugReport::class])
    @EnsureHasNoAdditionalUser
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#requestBugreport"])
    @NotificationsTest
    fun requestBugReport_noAdditionalUser_doesNotThrowException() {
        removeOtherUsers()

        TestApis.notifications().createListener().use { notifications ->
            val result = deviceState.dpc().devicePolicyManager()
                .requestBugreport(deviceState.dpc().componentName())

            assertThat(result).isTrue()

            cleanupBugreportNotification(notifications)
        }
    }

    @CanSetPolicyTest(policy = [RequestBugReport::class])
    @EnsureHasNoAdditionalUser
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#getLastBugReportRequestTime"])
    @NotificationsTest
    @Ignore // We need to put getLastBugReportRequestTime behind QUERY_ADMIN_POLICY
    fun getLastBugReportRequestTime_returnsCorrectValue() {
        removeOtherUsers()
        TestApis.notifications().createListener().use { notifications ->
            val previousValue = deviceState.dpc().devicePolicyManager().lastBugReportRequestTime

            deviceState.dpc().devicePolicyManager().requestBugreport(deviceState.dpc().componentName())

            assertThat(deviceState.dpc().devicePolicyManager().lastBugReportRequestTime)
                .isNotEqualTo(previousValue)

            cleanupBugreportNotification(notifications)
        }
    }

    @CannotSetPolicyTest(policy = [RequestBugReport::class])
    @EnsureHasNoAdditionalUser
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#getLastBugReportRequestTime"])
    @Ignore // Testapps can't currently access @TestApis as not debuggable
    fun getLastBugReportRequestTime_notAllowed_throwsException() {
        assertThrows<SecurityException>(SecurityException::class.java) {
            deviceState.dpc().devicePolicyManager().lastBugReportRequestTime
        }
    }

    @CanSetPolicyTest(policy = [RequestBugReport::class])
    @Test
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#requestBugReport",
        "android.app.admin.DeviceAdminReceiver#onBugReportShared"])
    @NotificationsTest
    fun requestBugReport_logsEvent() {
        removeOtherUsers()

        EnterpriseMetricsRecorder.create().use { metrics ->
            TestApis.notifications().createListener().use { notifications ->

                deviceState.dpc().devicePolicyManager()
                    .requestBugreport(deviceState.dpc().componentName())

                assertThat(
                    metrics.query()
                        .whereType().isEqualTo(EventId.REQUEST_BUGREPORT_VALUE)
                        .whereAdminPackageName().isEqualTo(
                            deviceState.dpc().packageName()
                        )
                ).wasLogged()

                cleanupBugreportNotification(notifications)
            }
        }
    }

    private fun removeOtherUsers() {
        // TODO(273474964): Move into infra
        TestApis.users().all().stream()
            .filter { u: UserReference ->
                (u != TestApis.users().instrumented()
                        && u != TestApis.users().current())
            }
            .forEach { obj: UserReference -> obj.remove() }
    }

    fun cleanupBugreportNotification(notifications: NotificationListener) {
        // Wait for the bug report notification
        val n = notifications.query()
            .whereNotification().tag().isEqualTo("DevicePolicyManager")
            .whereNotification().extras().key(Notification.EXTRA_TITLE).stringValue().startsWith("Share bug report")
            .poll()
        // The first action is to decline
        n?.notification?.actions?.get(0)?.actionIntent?.send();
    }

    companion object {
        @JvmField
        @ClassRule
        @Rule
        val deviceState = DeviceState()
    }
}
