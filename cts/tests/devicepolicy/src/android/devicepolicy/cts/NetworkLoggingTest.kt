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

import android.stats.devicepolicy.EventId
import com.android.bedstead.harrier.BedsteadJUnit4
import com.android.bedstead.harrier.DeviceState
import com.android.bedstead.harrier.UserType
import com.android.bedstead.harrier.UserType.ADDITIONAL_USER
import com.android.bedstead.harrier.annotations.EnsureHasAdditionalUser
import com.android.bedstead.harrier.annotations.EnsureHasNoAdditionalUser
import com.android.bedstead.harrier.annotations.Postsubmit
import com.android.bedstead.harrier.annotations.enterprise.CanSetPolicyTest
import com.android.bedstead.harrier.annotations.enterprise.CannotSetPolicyTest
import com.android.bedstead.harrier.annotations.enterprise.EnsureHasProfileOwner
import com.android.bedstead.harrier.annotations.enterprise.PolicyAppliesTest
import com.android.bedstead.harrier.policies.GlobalNetworkLogging
import com.android.bedstead.harrier.policies.NetworkLogging
import com.android.bedstead.metricsrecorder.EnterpriseMetricsRecorder
import com.android.bedstead.metricsrecorder.truth.MetricQueryBuilderSubject
import com.android.bedstead.nene.TestApis
import com.android.bedstead.nene.permissions.CommonPermissions
import com.android.bedstead.nene.users.UserReference
import com.android.compatibility.common.util.ApiTest
import com.google.common.truth.Truth
import org.junit.Assume
import org.junit.AssumptionViolatedException
import org.junit.ClassRule
import org.junit.Rule
import org.junit.runner.RunWith
import org.testng.Assert
import java.net.HttpURLConnection
import java.net.URL
import java.net.UnknownHostException

// These tests currently only cover checking that the appropriate methods are callable. They should
// be replaced with more complete tests once the other network logging tests are ready to be
// migrated to the new infrastructure
@RunWith(BedsteadJUnit4::class)
class NetworkLoggingTest {
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#isNetworkLoggingEnabled"])
    @CannotSetPolicyTest(policy = [GlobalNetworkLogging::class, NetworkLogging::class])
    @Postsubmit(reason = "new test")
    fun isNetworkLoggingEnabled_notAllowed_throwsException() {
        ensureNoUnaffiliatedAdditionalUsers()

        Assert.assertThrows(SecurityException::class.java) {
            deviceState.dpc().devicePolicyManager()
                .isNetworkLoggingEnabled(deviceState.dpc().componentName())
        }
    }

    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#isNetworkLoggingEnabled"])
    @EnsureHasNoAdditionalUser
    @CanSetPolicyTest(policy = [GlobalNetworkLogging::class, NetworkLogging::class])
    @Postsubmit(reason = "new test")
    fun isNetworkLoggingEnabled_networkLoggingIsEnabled_returnsTrue() {
        ensureNoUnaffiliatedAdditionalUsers()

        try {
            deviceState.dpc().devicePolicyManager().setNetworkLoggingEnabled(
                deviceState.dpc().componentName(), true
            )
            Truth.assertThat(
                deviceState.dpc().devicePolicyManager().isNetworkLoggingEnabled(
                    deviceState.dpc().componentName()
                )
            ).isTrue()
        } finally {
            deviceState.dpc().devicePolicyManager().setNetworkLoggingEnabled(
                deviceState.dpc().componentName(), false
            )
        }
    }

    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#isNetworkLoggingEnabled"])
    @CanSetPolicyTest(policy = [GlobalNetworkLogging::class, NetworkLogging::class])
    @Postsubmit(reason = "new test")
    fun isNetworkLoggingEnabled_networkLoggingIsNotEnabled_returnsFalse() {
        ensureNoUnaffiliatedAdditionalUsers()

        deviceState.dpc().devicePolicyManager().setNetworkLoggingEnabled(
            deviceState.dpc().componentName(), false
        )
        Truth.assertThat(
            deviceState.dpc().devicePolicyManager().isNetworkLoggingEnabled(
                deviceState.dpc().componentName()
            )
        ).isFalse()
    }

    @Postsubmit(reason = "new test")
    @PolicyAppliesTest(policy = [GlobalNetworkLogging::class, NetworkLogging::class])
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#setNetworkLoggingEnabled",
        "android.app.admin.DeviceAdminReceiver#onNetworkLogsAvailable",
        "android.app.admin.DelegatedAdminReceiver#onNetworkLogsAvailable"])
    fun setNetworkLoggingEnabled_networkLoggingIsEnabled() {
        ensureNoUnaffiliatedAdditionalUsers()

        try {
            deviceState.dpc().devicePolicyManager().setNetworkLoggingEnabled(
                deviceState.dpc().componentName(), true
            )

            for (url in URL_LIST) {
                connectToWebsite(url)
            }
            TestApis.devicePolicy().forceNetworkLogs()
            val batchToken = waitForBatchToken()

            Truth.assertThat(
                deviceState.dpc().devicePolicyManager().retrieveNetworkLogs(
                    deviceState.dpc().componentName(), batchToken
                )
            ).isNotEmpty()
        } finally {
            deviceState.dpc().devicePolicyManager().setNetworkLoggingEnabled(
                deviceState.dpc().componentName(), false
            )
        }
    }

    private fun waitForBatchToken(): Long {
        return try {
            if (deviceState.dpc().isDelegate) {
                deviceState.dpc().events().delegateNetworkLogsAvailable()
                    .waitForEvent().batchToken()
            } else {
                deviceState.dpc().events().networkLogsAvailable().waitForEvent().batchToken()
            }
        } catch (e: AssertionError) {
            // Collect relevant logs
            throw AssertionError(
                "Error receiving batch token. Relevant logs: "
                        + TestApis.logcat().dump { l: String ->
                    l.contains("NetworkLoggingHandler")
                            || l.contains("sendDeviceOwnerOrProfileOwnerCommand")
                }, e
            )
        }
    }

    private fun connectToWebsite(server: String) {
        TestApis.permissions().withPermission(CommonPermissions.INTERNET).use { p ->
            val url = URL("http://$server")
            val urlConnection = url.openConnection() as HttpURLConnection
            try {
                urlConnection.connectTimeout = 2000
                urlConnection.readTimeout = 2000
                urlConnection.responseCode
            } catch (e: UnknownHostException) {
                throw AssumptionViolatedException("Could not resolve host $server")
            } finally {
                urlConnection.disconnect()
            }
        }
    }

    @Postsubmit(reason = "new test")
    @CannotSetPolicyTest(policy = [GlobalNetworkLogging::class, NetworkLogging::class])
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#setNetworkLoggingEnabled"])
    fun setNetworkLoggingEnabled_notAllowed_throwsException() {
        ensureNoUnaffiliatedAdditionalUsers()

        Assert.assertThrows(SecurityException::class.java) {
            deviceState.dpc().devicePolicyManager()
                .setNetworkLoggingEnabled(deviceState.dpc().componentName(), true)
        }
    }

    @Postsubmit(reason = "new test")
    @CannotSetPolicyTest(policy = [GlobalNetworkLogging::class, NetworkLogging::class])
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#retrieveNetworkLogs"])
    fun retrieveNetworkLogs_notAllowed_throwsException() {
        ensureNoUnaffiliatedAdditionalUsers()

        Assert.assertThrows(SecurityException::class.java) {
            deviceState.dpc().devicePolicyManager()
                .retrieveNetworkLogs(deviceState.dpc().componentName(),  /*batchToken= */0)
        }
    }

    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = [GlobalNetworkLogging::class, NetworkLogging::class])
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#setNetworkLoggingEnabled"])
    fun setNetworkLoggingEnabled_true_logsEvent() {
        try {
            EnterpriseMetricsRecorder.create().use { metrics ->
                deviceState.dpc().devicePolicyManager().setNetworkLoggingEnabled(
                    deviceState.dpc().componentName(), true
                )

                MetricQueryBuilderSubject.assertThat(
                    metrics.query()
                        .whereType().isEqualTo(EventId.SET_NETWORK_LOGGING_ENABLED_VALUE)
                        .whereAdminPackageName().isEqualTo(
                            deviceState.dpc().packageName()
                        )
                        .whereBoolean().isEqualTo(deviceState.dpc().isDelegate)
                        .whereInteger().isEqualTo(1) // Enabled
                ).wasLogged()
            }
        } finally {
            deviceState.dpc().devicePolicyManager().setNetworkLoggingEnabled(
                deviceState.dpc().componentName(), false
            )
        }
    }

    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = [GlobalNetworkLogging::class, NetworkLogging::class])
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#setNetworkLoggingEnabled"])
    fun setNetworkLoggingEnabled_false_logsEvent() {
        deviceState.dpc().devicePolicyManager().setNetworkLoggingEnabled(
            deviceState.dpc().componentName(), true
        )

        EnterpriseMetricsRecorder.create().use { metrics ->
            deviceState.dpc().devicePolicyManager().setNetworkLoggingEnabled(
                deviceState.dpc().componentName(), false
            )

            MetricQueryBuilderSubject.assertThat(
                metrics.query()
                    .whereType().isEqualTo(EventId.SET_NETWORK_LOGGING_ENABLED_VALUE)
                    .whereAdminPackageName().isEqualTo(
                        deviceState.dpc().packageName()
                    )
                    .whereBoolean().isEqualTo(deviceState.dpc().isDelegate)
                    .whereInteger().isEqualTo(0) // Disabled
            ).wasLogged()
        }
    }

    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = [GlobalNetworkLogging::class, NetworkLogging::class])
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#retrieveNetworkLogs",
        "android.app.admin.DeviceAdminReceiver#onNetworkLogsAvailable",
        "android.app.admin.DelegatedAdminReceiver#onNetworkLogsAvailable"])
    fun retrieveNetworkLogs_logsEvent() {
        ensureNoUnaffiliatedAdditionalUsers()

        try {
            EnterpriseMetricsRecorder.create().use { metrics ->
                deviceState.dpc().devicePolicyManager().setNetworkLoggingEnabled(
                    deviceState.dpc().componentName(), true
                )

                for (url in URL_LIST) {
                    connectToWebsite(url)
                }
                TestApis.devicePolicy().forceNetworkLogs()
                val batchToken = waitForBatchToken()
                deviceState.dpc().devicePolicyManager().retrieveNetworkLogs(
                    deviceState.dpc().componentName(), batchToken
                )

                MetricQueryBuilderSubject.assertThat(
                    metrics.query()
                        .whereType().isEqualTo(EventId.RETRIEVE_NETWORK_LOGS_VALUE)
                        .whereAdminPackageName().isEqualTo(
                            deviceState.dpc().packageName()
                        )
                        .whereBoolean().isEqualTo(deviceState.dpc().isDelegate)
                ).wasLogged()
            }
        } finally {
            deviceState.dpc().devicePolicyManager().setNetworkLoggingEnabled(
                deviceState.dpc().componentName(), false
            )
        }
    }

    @CanSetPolicyTest(policy = [GlobalNetworkLogging::class])
    @EnsureHasAdditionalUser
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#retrieveNetworkLogs"])
    fun retrieveNetworkLogs_unaffiliatedAdditionalUser_throwsException() {
        try {
            deviceState.dpc().devicePolicyManager().setNetworkLoggingEnabled(
                deviceState.dpc().componentName(), true
            )

            Assert.assertThrows(SecurityException::class.java) {
                deviceState.dpc().devicePolicyManager().retrieveNetworkLogs(
                    deviceState.dpc().componentName(),  /* batchToken= */0
                )
            }
        } finally {
            deviceState.dpc().devicePolicyManager().setNetworkLoggingEnabled(
                deviceState.dpc().componentName(), false
            )
        }
    }

    @CanSetPolicyTest(policy = [GlobalNetworkLogging::class, NetworkLogging::class])
    @EnsureHasAdditionalUser
    @EnsureHasProfileOwner(onUser = ADDITIONAL_USER, affiliationIds = ["affiliated"])
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#retrieveNetworkLogs"])
    fun retrieveNetworkLogs_affiliatedAdditionalUser_doesNotThrowException() {
        // TODO(273474964): Move into infra
        TestApis.users().all().stream()
            .filter {
                (it != TestApis.users().instrumented()
                        && it != TestApis.users().system()
                        && it != deviceState.additionalUser()
                        && it != TestApis.users().current())
            }
            .forEach { it.remove() }

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
            deviceState.dpc().devicePolicyManager().setNetworkLoggingEnabled(
                deviceState.dpc().componentName(), true
            )
            deviceState.dpc().devicePolicyManager().retrieveNetworkLogs(
                deviceState.dpc().componentName(),  /* batchToken= */0
            )
        } finally {
            deviceState.dpc().devicePolicyManager().setNetworkLoggingEnabled(
                deviceState.dpc().componentName(), false
            )
        }
    }

    @CanSetPolicyTest(policy = [GlobalNetworkLogging::class, NetworkLogging::class])
    @EnsureHasNoAdditionalUser
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#retrieveNetworkLogs"])
    fun retrieveNetworkLogs_noAdditionalUser_doesNotThrowException() {
        ensureNoUnaffiliatedAdditionalUsers()

        try {
            deviceState.dpc().devicePolicyManager().setNetworkLoggingEnabled(
                deviceState.dpc().componentName(), true
            )
            deviceState.dpc().devicePolicyManager().retrieveNetworkLogs(
                deviceState.dpc().componentName(),  /* batchToken= */0
            )
        } finally {
            deviceState.dpc().devicePolicyManager().setNetworkLoggingEnabled(
                deviceState.dpc().componentName(), false
            )
        }
    }

    private fun ensureNoUnaffiliatedAdditionalUsers() {
        // TODO(273474964): Move into infra

        // We need to skip tests on an unaffiliated user - this should be expressible in
        // annotation so it doesn't generate the incorrect test
        Assume.assumeTrue(
            "Cannot run on an unaffiliate user", TestApis.devicePolicy().isAffiliated()
        )

        TestApis.users().all().stream()
            .filter { u: UserReference ->
                (u != TestApis.users().instrumented()
                        && u != TestApis.users().system()
                        && u != TestApis.users().current())
            }
            .forEach { obj: UserReference -> obj.remove() }
    }

    companion object {
        @JvmField
        @ClassRule
        @Rule
        val deviceState = DeviceState()

        private val URL_LIST = arrayOf(
            "example.edu",
            "google.co.jp",
            "google.fr",
            "google.com.br",
            "google.com.tr",
            "google.co.uk",
            "google.de"
        )
    }
}