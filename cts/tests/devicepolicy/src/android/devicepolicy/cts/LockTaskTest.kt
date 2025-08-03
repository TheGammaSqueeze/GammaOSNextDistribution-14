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

import android.app.ActivityManager
import android.app.ActivityManager.LOCK_TASK_MODE_LOCKED
import android.app.ActivityOptions
import android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN
import android.app.admin.DevicePolicyIdentifiers.LOCK_TASK_POLICY
import android.app.admin.DevicePolicyManager
import android.app.admin.DevicePolicyManager.LOCK_TASK_FEATURE_BLOCK_ACTIVITY_START_IN_TASK
import android.app.admin.DevicePolicyManager.LOCK_TASK_FEATURE_GLOBAL_ACTIONS
import android.app.admin.DevicePolicyManager.LOCK_TASK_FEATURE_HOME
import android.app.admin.DevicePolicyManager.LOCK_TASK_FEATURE_KEYGUARD
import android.app.admin.DevicePolicyManager.LOCK_TASK_FEATURE_NONE
import android.app.admin.DevicePolicyManager.LOCK_TASK_FEATURE_OVERVIEW
import android.app.admin.DpcAuthority.DPC_AUTHORITY
import android.app.admin.NoArgsPolicyKey
import android.app.admin.PolicyUpdateResult.RESULT_POLICY_SET
import android.app.admin.RoleAuthority
import android.app.admin.TargetUser
import android.content.ComponentName
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.content.pm.PackageManager
import android.content.pm.PackageManager.FEATURE_TELEPHONY
import android.content.pm.PackageManager.FEATURE_TELEPHONY_CALLING
import android.devicepolicy.cts.utils.PolicyEngineUtils
import android.devicepolicy.cts.utils.PolicySetResultUtils
import android.os.Bundle
import android.os.SystemClock
import android.stats.devicepolicy.EventId
import android.telecom.TelecomManager
import com.android.bedstead.harrier.BedsteadJUnit4
import com.android.bedstead.harrier.DeviceState
import com.android.bedstead.harrier.annotations.IntTestParameter
import com.android.bedstead.harrier.annotations.Postsubmit
import com.android.bedstead.harrier.annotations.RequireAdbRoot
import com.android.bedstead.harrier.annotations.RequireFeature
import com.android.bedstead.harrier.annotations.enterprise.CanSetPolicyTest
import com.android.bedstead.harrier.annotations.enterprise.CannotSetPolicyTest
import com.android.bedstead.harrier.annotations.enterprise.EnsureHasDeviceOwner
import com.android.bedstead.harrier.annotations.enterprise.MostImportantCoexistenceTest
import com.android.bedstead.harrier.annotations.enterprise.MostImportantCoexistenceTest.LESS_IMPORTANT
import com.android.bedstead.harrier.annotations.enterprise.MostImportantCoexistenceTest.MORE_IMPORTANT
import com.android.bedstead.harrier.annotations.enterprise.PolicyAppliesTest
import com.android.bedstead.harrier.annotations.enterprise.PolicyDoesNotApplyTest
import com.android.bedstead.harrier.annotations.enterprise.RequireHasPolicyExemptApps
import com.android.bedstead.harrier.policies.LockTask
import com.android.bedstead.harrier.policies.LockTaskFinance
import com.android.bedstead.metricsrecorder.EnterpriseMetricsRecorder
import com.android.bedstead.metricsrecorder.truth.MetricQueryBuilderSubject.assertThat
import com.android.bedstead.nene.TestApis
import com.android.bedstead.nene.activities.Activity
import com.android.bedstead.nene.utils.Poll
import com.android.bedstead.testapp.TestAppActivity
import com.android.compatibility.common.util.ApiTest
import com.android.eventlib.truth.EventLogsSubject.assertThat
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.testng.Assert.assertThrows

@RunWith(BedsteadJUnit4::class)
class LockTaskTest {
    @IntTestParameter(
        DevicePolicyManager.LOCK_TASK_FEATURE_SYSTEM_INFO,
        LOCK_TASK_FEATURE_HOME,
        LOCK_TASK_FEATURE_GLOBAL_ACTIONS,
        LOCK_TASK_FEATURE_KEYGUARD
    )
    @Retention(AnnotationRetention.RUNTIME)
    private annotation class IndividuallySettableFlagTestParameter

    @IntTestParameter(
        DevicePolicyManager.LOCK_TASK_FEATURE_SYSTEM_INFO,
        LOCK_TASK_FEATURE_OVERVIEW,
        DevicePolicyManager.LOCK_TASK_FEATURE_NOTIFICATIONS,
        LOCK_TASK_FEATURE_GLOBAL_ACTIONS,
        LOCK_TASK_FEATURE_KEYGUARD
    )
    @Retention(AnnotationRetention.RUNTIME)
    private annotation class SettableWithHomeFlagTestParameter

    @PolicyAppliesTest(policy = [LockTaskFinance::class])
    @Postsubmit(reason = "b/181993922 automatically marked flaky")
    fun setLockTaskPackages_lockTaskPackagesIsSet() {
        deviceState.dpc().devicePolicyManager().setLockTaskPackages(
            deviceState.dpc().componentName(), arrayOf(PACKAGE_NAME)
        )
        try {
            assertThat(
                deviceState.dpc().devicePolicyManager()
                    .getLockTaskPackages(deviceState.dpc().componentName())
            ).asList().containsExactly(PACKAGE_NAME)
        } finally {
            deviceState.dpc().devicePolicyManager()
                .setLockTaskPackages(deviceState.dpc().componentName(), arrayOf())
            deviceState.dpc().devicePolicyManager().setLockTaskFeatures(
                deviceState.dpc().componentName(), LOCK_TASK_FEATURE_NONE
            )
        }
    }

    // b/278061827 This currently fails for permission based access
    @PolicyAppliesTest(policy = [LockTaskFinance::class])
    @Postsubmit(reason = "b/181993922 automatically marked flaky")
    fun startLockTask_recordsMetric() {
        assumeTrue("Test requires showing activities",
            TestApis.users().instrumented().canShowActivities()
        )
        try {
            EnterpriseMetricsRecorder.create().use { metrics ->
                testApp.install().use { testApp ->
                    deviceState.dpc().devicePolicyManager().setLockTaskPackages(
                        deviceState.dpc().componentName(), arrayOf(testApp.packageName())
                    )
                    val activity = testApp.activities().any().start()
                    try {
                        activity.startLockTask()
                        assertThat(
                            metrics.query()
                                .whereType().isEqualTo(EventId.SET_LOCKTASK_MODE_ENABLED_VALUE)
                                .whereAdminPackageName().isEqualTo(deviceState.dpc().packageName())
                                .whereBoolean().isTrue()
                                .whereStrings().contains(testApp.packageName())
                        ).wasLogged()
                    } finally {
                        activity.stopLockTask()
                    }
                }
            }
        } finally {
            deviceState.dpc().devicePolicyManager()
                .setLockTaskPackages(deviceState.dpc().componentName(), arrayOf())
            deviceState.dpc().devicePolicyManager().setLockTaskFeatures(
                deviceState.dpc().componentName(), LOCK_TASK_FEATURE_NONE
            )
        }
    }

    @CannotSetPolicyTest(policy = [LockTaskFinance::class])
    fun getLockTaskPackages_policyIsNotAllowedToBeFetched_throwsException() {
            assertThrows(
                SecurityException::class.java
            ) {
                deviceState.dpc().devicePolicyManager()
                    .getLockTaskPackages(deviceState.dpc().componentName())
            }
        }

    @PolicyAppliesTest(policy = [LockTaskFinance::class])
    @Postsubmit(reason = "b/181993922 automatically marked flaky")
    fun setLockTaskPackages_empty_lockTaskPackagesIsSet() {
        deviceState.dpc().devicePolicyManager()
            .setLockTaskPackages(deviceState.dpc().componentName(), arrayOf())
        try {
            assertThat(
                deviceState.dpc().devicePolicyManager()
                    .getLockTaskPackages(deviceState.dpc().componentName())
            ).asList().isEmpty()
        } finally {
            deviceState.dpc().devicePolicyManager()
                .setLockTaskPackages(deviceState.dpc().componentName(), arrayOf())
            deviceState.dpc().devicePolicyManager().setLockTaskFeatures(
                deviceState.dpc().componentName(), LOCK_TASK_FEATURE_NONE
            )
        }
    }

    @PolicyAppliesTest(policy = [LockTaskFinance::class])
    @Postsubmit(reason = "b/181993922 automatically marked flaky")
    @RequireHasPolicyExemptApps
    fun setLockTaskPackages_includesPolicyExemptApp_lockTaskPackagesIsSet() {
        val policyExemptApp = TestApis.devicePolicy().getPolicyExemptApps().iterator().next()
        deviceState.dpc().devicePolicyManager().setLockTaskPackages(
            deviceState.dpc().componentName(), arrayOf(policyExemptApp)
        )
        try {
            assertThat(
                deviceState.dpc().devicePolicyManager()
                    .getLockTaskPackages(deviceState.dpc().componentName())
            ).asList().containsExactly(policyExemptApp)
        } finally {
            deviceState.dpc().devicePolicyManager()
                .setLockTaskPackages(deviceState.dpc().componentName(), arrayOf())
            deviceState.dpc().devicePolicyManager().setLockTaskFeatures(
                deviceState.dpc().componentName(), LOCK_TASK_FEATURE_NONE
            )
        }
    }

    @CannotSetPolicyTest(policy = [LockTaskFinance::class])
    fun setLockTaskPackages_policyIsNotAllowedToBeSet_throwsException() {
        assertThrows(
            SecurityException::class.java
        ) {
            deviceState.dpc().devicePolicyManager()
                .setLockTaskPackages(deviceState.dpc().componentName(), arrayOf())
        }
    }

    @PolicyAppliesTest(policy = [LockTaskFinance::class])
    fun isLockTaskPermitted_lockTaskPackageIsSet_returnsTrue() {
            deviceState.dpc().devicePolicyManager().setLockTaskPackages(
                deviceState.dpc().componentName(), arrayOf(PACKAGE_NAME)
            )
            try {
                assertThat(localDevicePolicyManager.isLockTaskPermitted(PACKAGE_NAME))
                    .isTrue()
            } finally {
                deviceState.dpc().devicePolicyManager()
                    .setLockTaskPackages(deviceState.dpc().componentName(), arrayOf())
                deviceState.dpc().devicePolicyManager().setLockTaskFeatures(
                    deviceState.dpc().componentName(), LOCK_TASK_FEATURE_NONE
                )
            }
        }

    @PolicyDoesNotApplyTest(policy = [LockTaskFinance::class])
    fun isLockTaskPermitted_lockTaskPackageIsSet_policyDoesntApply_returnsFalse() {
            deviceState.dpc().devicePolicyManager().setLockTaskPackages(
                deviceState.dpc().componentName(), arrayOf(PACKAGE_NAME)
            )
            try {
                assertThat(localDevicePolicyManager.isLockTaskPermitted(PACKAGE_NAME)).isFalse()
            } finally {
                deviceState.dpc().devicePolicyManager()
                    .setLockTaskPackages(deviceState.dpc().componentName(), arrayOf())
                deviceState.dpc().devicePolicyManager().setLockTaskFeatures(
                    deviceState.dpc().componentName(), LOCK_TASK_FEATURE_NONE
                )
            }
        }

    @Postsubmit(reason = "b/181993922 automatically marked flaky")
    @PolicyAppliesTest(policy = [LockTaskFinance::class])
    fun isLockTaskPermitted_lockTaskPackageIsNotSet_returnsFalse() {
            deviceState.dpc().devicePolicyManager()
                .setLockTaskPackages(deviceState.dpc().componentName(), arrayOf())
            try {
                assertThat(localDevicePolicyManager.isLockTaskPermitted(PACKAGE_NAME)).isFalse()
            } finally {
                deviceState.dpc().devicePolicyManager()
                    .setLockTaskPackages(deviceState.dpc().componentName(), arrayOf())
                deviceState.dpc().devicePolicyManager().setLockTaskFeatures(
                    deviceState.dpc().componentName(), LOCK_TASK_FEATURE_NONE
                )
            }
        }

    @RequireHasPolicyExemptApps
    @Postsubmit(reason = "b/181993922 automatically marked flaky")
    @PolicyAppliesTest(policy = [LockTaskFinance::class])
    fun isLockTaskPermitted_includesPolicyExemptApps() {
            try {
                deviceState.dpc().devicePolicyManager()
                    .setLockTaskPackages(deviceState.dpc().componentName(), arrayOf())
                for (app in TestApis.devicePolicy().getPolicyExemptApps()) {
                    assertWithMessage("isLockTaskPermitted(%s)", app)
                        .that(localDevicePolicyManager.isLockTaskPermitted(app)).isTrue()
                }
            } finally {
                deviceState.dpc().devicePolicyManager()
                    .setLockTaskPackages(deviceState.dpc().componentName(), arrayOf())
                deviceState.dpc().devicePolicyManager().setLockTaskFeatures(
                    deviceState.dpc().componentName(), LOCK_TASK_FEATURE_NONE
                )
            }
        }

    @CanSetPolicyTest(policy = [LockTask::class]) // TODO(b/188893663): Support additional parameterization for cases like this
    @Postsubmit(reason = "b/181993922 automatically marked flaky")
    fun setLockTaskFeatures_individuallySettableFlag_setsFeature(
        @IndividuallySettableFlagTestParameter flag: Int
    ) {
        try {
            deviceState.dpc().devicePolicyManager().setLockTaskPackages(
                deviceState.dpc().componentName(), arrayOf(testApp.packageName())
            )
            deviceState.dpc().devicePolicyManager()
                .setLockTaskFeatures(deviceState.dpc().componentName(), flag)
            assertThat(
                deviceState.dpc().devicePolicyManager()
                    .getLockTaskFeatures(deviceState.dpc().componentName())
            ).isEqualTo(flag)
        } finally {
            deviceState.dpc().devicePolicyManager().setLockTaskFeatures(
                deviceState.dpc().componentName(), LOCK_TASK_FEATURE_NONE
            )
            deviceState.dpc().devicePolicyManager()
                .setLockTaskPackages(deviceState.dpc().componentName(), arrayOf())
        }
    }

    @CanSetPolicyTest(policy = [LockTask::class])
    @Postsubmit(reason = "b/181993922 automatically marked flaky")
    fun setLockTaskFeatures_overviewFeature_throwsException() {
        // Overview can only be used in combination with home
        try {
            deviceState.dpc().devicePolicyManager().setLockTaskPackages(
                deviceState.dpc().componentName(), arrayOf(testApp.packageName())
            )
            assertThrows(IllegalArgumentException::class.java) {
                deviceState.dpc().devicePolicyManager().setLockTaskFeatures(
                    deviceState.dpc().componentName(),
                    LOCK_TASK_FEATURE_OVERVIEW
                )
            }
        } finally {
            deviceState.dpc().devicePolicyManager().setLockTaskFeatures(
                deviceState.dpc().componentName(), LOCK_TASK_FEATURE_NONE
            )
            deviceState.dpc().devicePolicyManager().setLockTaskPackages(
                deviceState.dpc().componentName(), arrayOf()
            )
        }
    }

    @CanSetPolicyTest(policy = [LockTask::class])
    @Postsubmit(reason = "b/181993922 automatically marked flaky")
    fun setLockTaskFeatures_notificationsFeature_throwsException() {
        // Notifications can only be used in combination with home
        try {
            deviceState.dpc().devicePolicyManager().setLockTaskPackages(
                deviceState.dpc().componentName(), arrayOf(testApp.packageName())
            )
            assertThrows(IllegalArgumentException::class.java) {
                deviceState.dpc().devicePolicyManager().setLockTaskFeatures(
                    deviceState.dpc().componentName(),
                    DevicePolicyManager.LOCK_TASK_FEATURE_NOTIFICATIONS
                )
            }
        } finally {
            deviceState.dpc().devicePolicyManager().setLockTaskFeatures(
                deviceState.dpc().componentName(), LOCK_TASK_FEATURE_NONE
            )
            deviceState.dpc().devicePolicyManager()
                .setLockTaskPackages(deviceState.dpc().componentName(), arrayOf())
        }
    }

    @CanSetPolicyTest(policy = [LockTask::class]) // TODO(b/188893663): Support additional parameterization for cases like this
    @Postsubmit(reason = "b/181993922 automatically marked flaky")
    fun setLockTaskFeatures_multipleFeatures_setsFeatures(
        @SettableWithHomeFlagTestParameter flag: Int
    ) {
        try {
            deviceState.dpc().devicePolicyManager().setLockTaskPackages(
                deviceState.dpc().componentName(), arrayOf(testApp.packageName())
            )
            deviceState.dpc().devicePolicyManager().setLockTaskFeatures(
                deviceState.dpc().componentName(),
                LOCK_TASK_FEATURE_HOME or flag
            )
            assertThat(
                deviceState.dpc().devicePolicyManager()
                    .getLockTaskFeatures(deviceState.dpc().componentName())
            ).isEqualTo(LOCK_TASK_FEATURE_HOME or flag)
        } finally {
            deviceState.dpc().devicePolicyManager().setLockTaskFeatures(
                deviceState.dpc().componentName(), LOCK_TASK_FEATURE_NONE
            )
            deviceState.dpc().devicePolicyManager().setLockTaskPackages(
                deviceState.dpc().componentName(), arrayOf()
            )
        }
    }

    @CannotSetPolicyTest(policy = [LockTask::class])
    fun setLockTaskFeatures_policyIsNotAllowedToBeSet_throwsException() {
        assertThrows(SecurityException::class.java) {
            deviceState.dpc().devicePolicyManager().setLockTaskFeatures(
                deviceState.dpc().componentName(),
                LOCK_TASK_FEATURE_OVERVIEW or LOCK_TASK_FEATURE_HOME
            )
        }
    }

    @CannotSetPolicyTest(policy = [LockTaskFinance::class])
    fun getLockTaskFeatures_policyIsNotAllowedToBeFetched_throwsException() {
            assertThrows(SecurityException::class.java) {
                deviceState.dpc().devicePolicyManager()
                    .getLockTaskFeatures(deviceState.dpc().componentName())
            }
        }

    @PolicyAppliesTest(policy = [LockTaskFinance::class])
    @Postsubmit(reason = "b/181993922 automatically marked flaky")
    fun startLockTask_includedInLockTaskPackages_taskIsLocked() {
        assumeTrue(
            "Test requires showing activities",
            TestApis.users().instrumented().canShowActivities()
        )
        deviceState.dpc().devicePolicyManager().setLockTaskPackages(
            deviceState.dpc().componentName(), arrayOf(testApp.packageName())
        )
        try {
            testApp.install().use { testApp ->
                val activity = testApp.activities().any().start()
                activity.startLockTask()
                try {
                    assertThat(TestApis.activities().foregroundActivity()).isEqualTo(
                        activity.activity().component()
                    )
                    assertThat(TestApis.activities().lockTaskModeState).isEqualTo(
                        LOCK_TASK_MODE_LOCKED
                    )
                } finally {
                    activity.stopLockTask()
                }
            }
        } finally {
            deviceState.dpc().devicePolicyManager()
                .setLockTaskPackages(deviceState.dpc().componentName(), arrayOf())
            deviceState.dpc().devicePolicyManager().setLockTaskFeatures(
                deviceState.dpc().componentName(), LOCK_TASK_FEATURE_NONE
            )
        }
    }

    @PolicyAppliesTest(policy = [LockTaskFinance::class])
    @Postsubmit(reason = "b/181993922 automatically marked flaky")
    fun startLockTask_notIncludedInLockTaskPackages_taskIsNotLocked() {
        assumeTrue(
            "Test requires showing activities",
            TestApis.users().instrumented().canShowActivities()
        )
        deviceState.dpc().devicePolicyManager()
            .setLockTaskPackages(deviceState.dpc().componentName(), arrayOf())
        try {
            testApp.install().use { testApp ->
                val activity: Activity<TestAppActivity> = testApp.activities().any().start()
                activity.activity().startLockTask()
                try {
                    assertThat(TestApis.activities().foregroundActivity()).isEqualTo(
                        activity.activity().component()
                    )
                    assertThat(TestApis.activities().lockTaskModeState).isNotEqualTo(
                        LOCK_TASK_MODE_LOCKED
                    )
                } finally {
                    activity.stopLockTask()
                }
            }
        } finally {
            deviceState.dpc().devicePolicyManager()
                .setLockTaskPackages(deviceState.dpc().componentName(), arrayOf())
            deviceState.dpc().devicePolicyManager().setLockTaskFeatures(
                deviceState.dpc().componentName(), LOCK_TASK_FEATURE_NONE
            )
        }
    }

    @PolicyDoesNotApplyTest(policy = [LockTaskFinance::class])
    fun startLockTask_includedInLockTaskPackages_policyShouldNotApply_taskIsNotLocked() {
        assumeTrue(
            "Test requires showing activities",
            TestApis.users().instrumented().canShowActivities()
        )
        deviceState.dpc().devicePolicyManager().setLockTaskPackages(
            deviceState.dpc().componentName(), arrayOf(testApp.packageName())
        )
        try {
            testApp.install().use { testApp ->
                val activity = testApp.activities().any().start()
                activity.activity().startLockTask()
                try {
                    assertThat(TestApis.activities().foregroundActivity()).isEqualTo(
                        activity.activity().component()
                    )
                    assertThat(TestApis.activities().lockTaskModeState).isNotEqualTo(
                        LOCK_TASK_MODE_LOCKED
                    )
                } finally {
                    activity.stopLockTask()
                }
            }
        } finally {
            deviceState.dpc().devicePolicyManager()
                .setLockTaskPackages(deviceState.dpc().componentName(), arrayOf())
            deviceState.dpc().devicePolicyManager().setLockTaskFeatures(
                deviceState.dpc().componentName(), LOCK_TASK_FEATURE_NONE
            )
        }
    }

    @PolicyAppliesTest(policy = [LockTaskFinance::class])
    @Postsubmit(reason = "b/181993922 automatically marked flaky")
    fun finish_isLocked_doesNotFinish() {
        assumeTrue(
            "Test requires showing activities",
            TestApis.users().instrumented().canShowActivities()
        )
        deviceState.dpc().devicePolicyManager().setLockTaskPackages(
            deviceState.dpc().componentName(), arrayOf(testApp.packageName())
        )
        try {
            testApp.install().use { testApp ->
                val activity = testApp.activities().any().start()
                activity.startLockTask()
                activity.activity().finish()
                try {
                    // We don't actually watch for the Destroyed event because that'd be waiting for a
                    // non occurrence of an event which is slow
                    assertThat(TestApis.activities().foregroundActivity()).isEqualTo(
                        activity.activity().component()
                    )
                    assertThat(TestApis.activities().lockTaskModeState).isEqualTo(
                        LOCK_TASK_MODE_LOCKED
                    )
                } finally {
                    activity.stopLockTask()
                }
            }
        } finally {
            deviceState.dpc().devicePolicyManager()
                .setLockTaskPackages(deviceState.dpc().componentName(), arrayOf())
            deviceState.dpc().devicePolicyManager().setLockTaskFeatures(
                deviceState.dpc().componentName(), LOCK_TASK_FEATURE_NONE
            )
        }
    }

    @PolicyAppliesTest(policy = [LockTaskFinance::class])
    @Postsubmit(reason = "b/181993922 automatically marked flaky")
    fun finish_hasStoppedLockTask_doesFinish() {
        assumeTrue(
            "Test requires showing activities",
            TestApis.users().instrumented().canShowActivities()
        )
        deviceState.dpc().devicePolicyManager().setLockTaskPackages(
            deviceState.dpc().componentName(), arrayOf(testApp.packageName())
        )
        try {
            testApp.install().use { testApp ->
                val activity = testApp.activities().any().start()
                activity.startLockTask()
                activity.stopLockTask()
                activity.activity().finish()
                assertThat(activity.activity().events().activityDestroyed()).eventOccurred()
                assertThat(TestApis.activities().foregroundActivity()).isNotEqualTo(
                    activity.activity().component()
                )
            }
        } finally {
            deviceState.dpc().devicePolicyManager()
                .setLockTaskPackages(deviceState.dpc().componentName(), arrayOf())
            deviceState.dpc().devicePolicyManager().setLockTaskFeatures(
                deviceState.dpc().componentName(), LOCK_TASK_FEATURE_NONE
            )
        }
    }

    @PolicyAppliesTest(policy = [LockTaskFinance::class])
    @Postsubmit(reason = "b/181993922 automatically marked flaky")
    fun setLockTaskPackages_removingCurrentlyLockedTask_taskFinishes() {
        assumeTrue(
            "Test requires showing activities",
            TestApis.users().instrumented().canShowActivities()
        )
        deviceState.dpc().devicePolicyManager().setLockTaskPackages(
            deviceState.dpc().componentName(), arrayOf(testApp.packageName())
        )
        try {
            testApp.install().use { testApp ->
                val activity = testApp.activities().any().start()
                activity.startLockTask()
                deviceState.dpc().devicePolicyManager()
                    .setLockTaskPackages(deviceState.dpc().componentName(), arrayOf())
                assertThat(activity.activity().events().activityDestroyed()).eventOccurred()
                assertThat(TestApis.activities().foregroundActivity()).isNotEqualTo(
                    activity.activity().component()
                )
            }
        } finally {
            deviceState.dpc().devicePolicyManager()
                .setLockTaskPackages(deviceState.dpc().componentName(), arrayOf())
            deviceState.dpc().devicePolicyManager().setLockTaskFeatures(
                deviceState.dpc().componentName(), LOCK_TASK_FEATURE_NONE
            )
        }
    }

    @PolicyAppliesTest(policy = [LockTaskFinance::class])
    @Postsubmit(reason = "b/181993922 automatically marked flaky")
    fun setLockTaskPackages_removingCurrentlyLockedTask_otherLockedTasksRemainLocked() {
        assumeTrue(
            "Test requires showing activities",
            TestApis.users().instrumented().canShowActivities()
        )
        deviceState.dpc().devicePolicyManager()
            .setLockTaskPackages(
                deviceState.dpc().componentName(),
                arrayOf(testApp.packageName(), secondTestApp.packageName())
            )
        try {
            testApp.install().use { testApp ->
                secondTestApp.install().use { testApp2 ->
                    val activity = testApp.activities().any().start(
                        LAUNCH_FULLSCREEN_OPTIONS.toBundle()
                    )
                    activity.startLockTask()
                    val activity2 = testApp2.activities().any().start(
                        LAUNCH_FULLSCREEN_OPTIONS.toBundle()
                    )
                    activity2.startLockTask()
                    try {
                        deviceState.dpc().devicePolicyManager()
                            .setLockTaskPackages(
                                deviceState.dpc().componentName(),
                                arrayOf(testApp.packageName())
                            )
                        assertThat(activity2.activity().events().activityDestroyed()).eventOccurred()
                        assertThat(TestApis.activities().lockTaskModeState).isEqualTo(
                            LOCK_TASK_MODE_LOCKED
                        )
                        assertThat(TestApis.activities().foregroundActivity()).isEqualTo(
                            activity.activity().component()
                        )
                    } finally {
                        activity.stopLockTask()
                    }
                }
            }
        } finally {
            deviceState.dpc().devicePolicyManager()
                .setLockTaskPackages(deviceState.dpc().componentName(), arrayOf())
            deviceState.dpc().devicePolicyManager().setLockTaskFeatures(
                deviceState.dpc().componentName(), LOCK_TASK_FEATURE_NONE
            )
        }
    }

    @PolicyAppliesTest(policy = [LockTaskFinance::class])
    @Postsubmit(reason = "b/181993922 automatically marked flaky")
    fun startActivity_withinSameTask_startsActivity() {
        assumeTrue(
            "Test requires showing activities",
            TestApis.users().instrumented().canShowActivities()
        )
        try {
            testApp.install().use { testApp ->
                secondTestApp.install().use { testApp2 ->
                    deviceState.dpc().devicePolicyManager().setLockTaskPackages(
                        deviceState.dpc().componentName(), arrayOf(testApp.packageName())
                    )
                    val firstActivity: Activity<TestAppActivity> =
                        testApp.activities().any().start()
                    val secondActivity = testApp2.activities().any()
                    val secondActivityIntent = Intent()
                    // TODO(scottjonathan): Add filter to ensure no taskAffinity or launchMode which would
                    //  stop launching in same task
                    secondActivityIntent.setComponent(secondActivity.component().componentName())
                    firstActivity.startActivity(secondActivityIntent)
                    assertThat(secondActivity.events().activityStarted()).eventOccurred()
                    assertThat(TestApis.activities().foregroundActivity())
                        .isEqualTo(secondActivity.component())
                }
            }
        } finally {
            deviceState.dpc().devicePolicyManager()
                .setLockTaskPackages(deviceState.dpc().componentName(), arrayOf())
            deviceState.dpc().devicePolicyManager().setLockTaskFeatures(
                deviceState.dpc().componentName(), LOCK_TASK_FEATURE_NONE
            )
        }
    }

    @PolicyAppliesTest(policy = [LockTask::class])
    @Postsubmit(reason = "b/181993922 automatically marked flaky")
    fun startActivity_withinSameTask_blockStartInTask_doesNotStart() {
        assumeTrue(
            "Test requires showing activities",
            TestApis.users().instrumented().canShowActivities()
        )
        testApp.install().use { testApp ->
            secondTestApp.install().use { testApp2 ->
                try {
                    deviceState.dpc().devicePolicyManager()
                        .setLockTaskPackages(
                            deviceState.dpc().componentName(),
                            arrayOf(testApp.packageName())
                        )
                    deviceState.dpc().devicePolicyManager()
                        .setLockTaskFeatures(
                            deviceState.dpc().componentName(),
                            LOCK_TASK_FEATURE_BLOCK_ACTIVITY_START_IN_TASK
                        )
                    val firstActivity: Activity<TestAppActivity> =
                        testApp.activities().any().start()
                    firstActivity.startLockTask()
                    val secondActivity = testApp2.activities().any()
                    val secondActivityIntent = Intent()
                    secondActivityIntent.setComponent(secondActivity.component().componentName())
                    firstActivity.activity().startActivity(secondActivityIntent)
                    Poll.forValue("Foreground activity") { TestApis.activities().foregroundActivity() }
                        .toBeEqualTo(BLOCKED_ACTIVITY_COMPONENT)
                        .errorOnFail()
                        .await()
                } finally {
                    deviceState.dpc().devicePolicyManager().setLockTaskFeatures(
                        deviceState.dpc().componentName(),
                        LOCK_TASK_FEATURE_NONE
                    )
                    deviceState.dpc().devicePolicyManager()
                        .setLockTaskPackages(deviceState.dpc().componentName(), arrayOf())
                }
            }
        }
    }

    @PolicyAppliesTest(policy = [LockTask::class])
    @Postsubmit(reason = "b/181993922 automatically marked flaky")
    fun startActivity_inNewTask_blockStartInTask_doesNotStart() {
        assumeTrue(
            "Test requires showing activities",
            TestApis.users().instrumented().canShowActivities()
        )
        testApp.install().use { testApp ->
            secondTestApp.install().use { testApp2 ->
                try {
                    deviceState.dpc().devicePolicyManager()
                        .setLockTaskPackages(
                            deviceState.dpc().componentName(),
                            arrayOf(testApp.packageName())
                        )
                    deviceState.dpc().devicePolicyManager()
                        .setLockTaskFeatures(
                            deviceState.dpc().componentName(),
                            LOCK_TASK_FEATURE_BLOCK_ACTIVITY_START_IN_TASK
                        )
                    val firstActivity: Activity<TestAppActivity> =
                        testApp.activities().any().start()
                    firstActivity.startLockTask()
                    val secondActivity = testApp2.activities().any()
                    val secondActivityIntent = Intent()
                    secondActivityIntent.setComponent(secondActivity.component().componentName())
                    secondActivityIntent.addFlags(FLAG_ACTIVITY_NEW_TASK)
                    firstActivity.activity().startActivity(secondActivityIntent)
                    Poll.forValue("Foreground activity") { TestApis.activities().foregroundActivity() }
                        .toBeEqualTo(BLOCKED_ACTIVITY_COMPONENT)
                        .errorOnFail()
                        .await()
                } finally {
                    deviceState.dpc().devicePolicyManager().setLockTaskFeatures(
                        deviceState.dpc().componentName(),
                        LOCK_TASK_FEATURE_NONE
                    )
                    deviceState.dpc().devicePolicyManager().setLockTaskPackages(
                        deviceState.dpc().componentName(), arrayOf()
                    )
                }
            }
        }
    }

    @PolicyAppliesTest(policy = [LockTaskFinance::class])
    @Postsubmit(reason = "b/181993922 automatically marked flaky")
    fun startActivity_fromPermittedPackage_newTask_starts() {
        assumeTrue(
            "Test requires showing activities",
            TestApis.users().instrumented().canShowActivities()
        )
        testApp.install().use { testApp ->
            secondTestApp.install().use { testApp2 ->
                try {
                    deviceState.dpc().devicePolicyManager().setLockTaskPackages(
                        deviceState.dpc().componentName(),
                        arrayOf(testApp.packageName(), secondTestApp.packageName())
                    )
                    val firstActivity = testApp.activities().any().start()
                    firstActivity.startLockTask()
                    val secondActivity = testApp2.activities().any()
                    val secondActivityIntent = Intent()
                    secondActivityIntent.setComponent(secondActivity.component().componentName())
                    secondActivityIntent.addFlags(FLAG_ACTIVITY_NEW_TASK)
                    firstActivity.startActivity(secondActivityIntent)
                    assertThat(TestApis.activities().foregroundActivity())
                        .isEqualTo(secondActivity.component())
                } finally {
                    deviceState.dpc().devicePolicyManager().setLockTaskPackages(
                        deviceState.dpc().componentName(), arrayOf()
                    )
                    deviceState.dpc().devicePolicyManager().setLockTaskFeatures(
                        deviceState.dpc().componentName(),
                        LOCK_TASK_FEATURE_NONE
                    )
                }
            }
        }
    }

    @PolicyAppliesTest(policy = [LockTaskFinance::class])
    @Postsubmit(reason = "b/181993922 automatically marked flaky")
    fun startActivity_fromNonPermittedPackage_newTask_doesNotStart() {
        assumeTrue(
            "Test requires showing activities",
            TestApis.users().instrumented().canShowActivities()
        )
        testApp.install().use { testApp ->
            secondTestApp.install().use { testApp2 ->
                try {
                    deviceState.dpc().devicePolicyManager().setLockTaskPackages(
                        deviceState.dpc().componentName(), arrayOf(testApp.packageName())
                    )
                    val firstActivity = testApp.activities().any().start()
                    firstActivity.startLockTask()
                    val secondActivity = testApp2.activities().any()
                    val secondActivityIntent = Intent()
                    secondActivityIntent.setComponent(secondActivity.component().componentName())
                    secondActivityIntent.addFlags(FLAG_ACTIVITY_NEW_TASK)
                    firstActivity.activity().startActivity(secondActivityIntent)
                    assertThat(TestApis.activities().foregroundActivity())
                        .isEqualTo(firstActivity.activity().component())
                } finally {
                    deviceState.dpc().devicePolicyManager().setLockTaskPackages(
                        deviceState.dpc().componentName(), arrayOf()
                    )
                    deviceState.dpc().devicePolicyManager().setLockTaskFeatures(
                        deviceState.dpc().componentName(),
                        LOCK_TASK_FEATURE_NONE
                    )
                }
            }
        }
    }

    @PolicyAppliesTest(policy = [LockTaskFinance::class])
    @Postsubmit(reason = "b/181993922 automatically marked flaky")
    fun startActivity_lockTaskEnabledOption_startsInLockTaskMode() {
        assumeTrue(
            "Test requires showing activities",
            TestApis.users().instrumented().canShowActivities()
        )
        testApp.install().use { testApp ->
            try {
                deviceState.dpc().devicePolicyManager().setLockTaskPackages(
                    deviceState.dpc().componentName(), arrayOf(testApp.packageName())
                )
                val options = ActivityOptions.makeBasic().setLockTaskEnabled(true).toBundle()
                val activity = testApp.activities().any().start(options)
                try {
                    assertThat(TestApis.activities().foregroundActivity()).isEqualTo(
                        activity.activity().component()
                    )
                    assertThat(TestApis.activities().lockTaskModeState).isEqualTo(
                        LOCK_TASK_MODE_LOCKED
                    )
                } finally {
                    activity.stopLockTask()
                }
            } finally {
                deviceState.dpc().devicePolicyManager().setLockTaskPackages(
                    deviceState.dpc().componentName(), arrayOf()
                )
                deviceState.dpc().devicePolicyManager().setLockTaskFeatures(
                    deviceState.dpc().componentName(), LOCK_TASK_FEATURE_NONE
                )
            }
        }
    }

    @PolicyAppliesTest(policy = [LockTaskFinance::class])
    @Postsubmit(reason = "b/181993922 automatically marked flaky")
    fun startActivity_lockTaskEnabledOption_notAllowedPackage_throwsException() {
        testApp.install().use { testApp ->
            try {
                deviceState.dpc().devicePolicyManager().setLockTaskPackages(
                    deviceState.dpc().componentName(), arrayOf()
                )
                val options = ActivityOptions.makeBasic().setLockTaskEnabled(true).toBundle()
                assertThrows(SecurityException::class.java) {
                    testApp.activities().any().start(options)
                }
            } finally {
                deviceState.dpc().devicePolicyManager().setLockTaskPackages(
                    deviceState.dpc().componentName(), arrayOf()
                )
                deviceState.dpc().devicePolicyManager().setLockTaskFeatures(
                    deviceState.dpc().componentName(), LOCK_TASK_FEATURE_NONE
                )
            }
        }
    }

    @PolicyAppliesTest(policy = [LockTaskFinance::class])
    @Postsubmit(reason = "b/181993922 automatically marked flaky")
    fun startActivity_ifWhitelistedActivity_startsInLockTaskMode() {
        assumeTrue(
            "Test requires showing activities",
            TestApis.users().instrumented().canShowActivities()
        )
        try {
            lockTaskTestApp.install().use { testApp ->
                deviceState.dpc().devicePolicyManager().setLockTaskPackages(
                    deviceState.dpc().componentName(),
                    arrayOf(lockTaskTestApp.packageName())
                )
                val activity = testApp.activities().query()
                    .whereActivity().activityClass().simpleName()
                    .isEqualTo("ifwhitelistedactivity") // TODO(scottjonathan): filter for lock task mode - currently we can't check
                    //  this so we just get a fixed package which contains a fixed activity
                    .get().start()
                try {
                    assertThat(TestApis.activities().foregroundActivity()).isEqualTo(
                        activity.activity().component()
                    )
                    assertThat(TestApis.activities().lockTaskModeState).isEqualTo(
                        LOCK_TASK_MODE_LOCKED
                    )
                } finally {
                    activity.stopLockTask()
                }
            }
        } finally {
            deviceState.dpc().devicePolicyManager().setLockTaskPackages(
                deviceState.dpc().componentName(), arrayOf()
            )
            deviceState.dpc().devicePolicyManager().setLockTaskFeatures(
                deviceState.dpc().componentName(), LOCK_TASK_FEATURE_NONE
            )
        }
    }

    @PolicyAppliesTest(policy = [LockTaskFinance::class])
    @Postsubmit(reason = "b/181993922 automatically marked flaky")
    fun startActivity_ifWhitelistedActivity_notWhitelisted_startsNotInLockTaskMode() {
        assumeTrue(
            "Test requires showing activities",
            TestApis.users().instrumented().canShowActivities()
        )
        try {
            lockTaskTestApp.install().use { testApp ->
                deviceState.dpc().devicePolicyManager().setLockTaskPackages(
                    deviceState.dpc().componentName(), arrayOf()
                )
                val activity = testApp.activities().query()
                    .whereActivity().activityClass().simpleName()
                    .isEqualTo("ifwhitelistedactivity") // TODO(scottjonathan): filter for lock task mode - currently we can't check
                    //  this so we just get a fixed package which contains a fixed activity
                    .get().start()
                try {
                    assertThat(TestApis.activities().foregroundActivity()).isEqualTo(
                        activity.activity().component()
                    )
                    assertThat(TestApis.activities().lockTaskModeState).isEqualTo(
                        ActivityManager.LOCK_TASK_MODE_NONE
                    )
                } finally {
                    activity.stopLockTask()
                }
            }
        } finally {
            deviceState.dpc().devicePolicyManager().setLockTaskPackages(
                deviceState.dpc().componentName(), arrayOf()
            )
            deviceState.dpc().devicePolicyManager().setLockTaskFeatures(
                deviceState.dpc().componentName(), LOCK_TASK_FEATURE_NONE
            )
        }
    }

    @PolicyAppliesTest(policy = [LockTaskFinance::class])
    @Postsubmit(reason = "b/181993922 automatically marked flaky")
    fun finish_ifWhitelistedActivity_doesNotFinish() {
        assumeTrue(
            "Test requires showing activities",
            TestApis.users().instrumented().canShowActivities()
        )
        try {
            lockTaskTestApp.install().use { testApp ->
                deviceState.dpc().devicePolicyManager().setLockTaskPackages(
                    deviceState.dpc().componentName(),
                    arrayOf(lockTaskTestApp.packageName())
                )
                val activity = testApp.activities().query()
                    .whereActivity().activityClass().simpleName()
                    .isEqualTo("ifwhitelistedactivity") // TODO(scottjonathan): filter for lock task mode - currently we can't check
                    //  this so we just get a fixed package which contains a fixed activity
                    .get().start()
                activity.activity().finish()
                try {
                    // We don't actually watch for the Destroyed event because that'd be waiting for a
                    // non occurrence of an event which is slow
                    assertThat(TestApis.activities().foregroundActivity()).isEqualTo(
                        activity.activity().component()
                    )
                    assertThat(TestApis.activities().lockTaskModeState).isEqualTo(
                        LOCK_TASK_MODE_LOCKED
                    )
                } finally {
                    activity.stopLockTask()
                }
            }
        } finally {
            deviceState.dpc().devicePolicyManager().setLockTaskPackages(
                deviceState.dpc().componentName(), arrayOf()
            )
            deviceState.dpc().devicePolicyManager().setLockTaskFeatures(
                deviceState.dpc().componentName(), LOCK_TASK_FEATURE_NONE
            )
        }
    }

    @PolicyAppliesTest(policy = [LockTaskFinance::class])
    @Postsubmit(reason = "b/181993922 automatically marked flaky")
    fun setLockTaskPackages_removingExistingIfWhitelistedActivity_stopsTask() {
        assumeTrue(
            "Test requires showing activities",
            TestApis.users().instrumented().canShowActivities()
        )
        try {
            lockTaskTestApp.install().use { testApp ->
                deviceState.dpc().devicePolicyManager().setLockTaskPackages(
                    deviceState.dpc().componentName(),
                    arrayOf(lockTaskTestApp.packageName())
                )
                val activity = testApp.activities().query()
                    .whereActivity().activityClass().simpleName()
                    .isEqualTo("ifwhitelistedactivity") // TODO(scottjonathan): filter for lock task mode - currently we can't check
                    //  this so we just get a fixed package which contains a fixed activity
                    .get().start()
                deviceState.dpc().devicePolicyManager().setLockTaskPackages(
                    deviceState.dpc().componentName(), arrayOf()
                )
                assertThat(
                    activity.activity().events().activityDestroyed()
                ).eventOccurred()
                assertThat(TestApis.activities().foregroundActivity()).isNotEqualTo(
                    activity.activity().component()
                )
            }
        } finally {
            deviceState.dpc().devicePolicyManager().setLockTaskPackages(
                deviceState.dpc().componentName(), arrayOf()
            )
            deviceState.dpc().devicePolicyManager().setLockTaskFeatures(
                deviceState.dpc().componentName(), LOCK_TASK_FEATURE_NONE
            )
        }
    }

    @PolicyAppliesTest(policy = [LockTaskFinance::class])
    @RequireFeature(FEATURE_TELEPHONY_CALLING)
    @Postsubmit(reason = "b/181993922 automatically marked flaky") // Tests that the default dialer doesn't crash or otherwise misbehave in lock task mode
    fun launchDefaultDialerInLockTaskMode_launches() {
        val telecomManager =
            TestApis.context().instrumentedContext().getSystemService(TelecomManager::class.java)!!
        val dialerPackage = telecomManager.systemDialerPackage
        try {
            deviceState.dpc().devicePolicyManager().setLockTaskPackages(
                deviceState.dpc().componentName(), arrayOf(dialerPackage)
            )
            val options = ActivityOptions.makeBasic().setLockTaskEnabled(true).toBundle()
            val intent = Intent(Intent.ACTION_DIAL)
            intent.setPackage(dialerPackage)
            intent.setFlags(FLAG_ACTIVITY_NEW_TASK or FLAG_ACTIVITY_CLEAR_TASK)
            TestApis.context().instrumentedContext().startActivity(intent, options)
            Poll.forValue("Foreground package") { TestApis.activities().foregroundActivity()?.pkg() }
                .toMeet { it?.packageName() == dialerPackage }
                .errorOnFail()
                .await()
            Poll.forValue("Lock task mode state") { TestApis.activities().lockTaskModeState }
                .toBeEqualTo(LOCK_TASK_MODE_LOCKED)
                .errorOnFail()
                .await()
        } finally {
            deviceState.dpc().devicePolicyManager().setLockTaskPackages(
                deviceState.dpc().componentName(), arrayOf()
            )
            deviceState.dpc().devicePolicyManager().setLockTaskFeatures(
                deviceState.dpc().componentName(), LOCK_TASK_FEATURE_NONE
            )
        }
    }

    @PolicyAppliesTest(policy = [LockTask::class])
    @RequireFeature(FEATURE_TELEPHONY)
    @Postsubmit(reason = "b/181993922 automatically marked flaky")
    fun launchEmergencyDialerInLockTaskMode_notWhitelisted_noKeyguardFeature_doesNotLaunch() {
        assumeTrue(
            "Test requires showing activities",
            TestApis.users().instrumented().canShowActivities()
        )
        val emergencyDialerPackageName = calculateEmergencyDialerPackageName()
        assumeFalse(emergencyDialerPackageName == null)
        try {
            lockTaskTestApp.install().use { testApp ->
                deviceState.dpc().devicePolicyManager().setLockTaskPackages(
                    deviceState.dpc().componentName(),
                    arrayOf(lockTaskTestApp.packageName())
                )
                deviceState.dpc().devicePolicyManager()
                    .setLockTaskFeatures(deviceState.dpc().componentName(), 0)
                val activity = testApp.activities().any().start()
                try {
                    activity.startLockTask()
                    val intent = Intent(ACTION_EMERGENCY_DIAL)
                    intent.setFlags(FLAG_ACTIVITY_NEW_TASK or FLAG_ACTIVITY_CLEAR_TASK)
                    activity.activity().startActivity(intent)
                    assertThat(
                        TestApis.activities().foregroundActivity()?.pkg()?.packageName()
                    ).isNotEqualTo(emergencyDialerPackageName)
                } finally {
                    activity.stopLockTask()
                }
            }
        } finally {
            deviceState.dpc().devicePolicyManager().setLockTaskFeatures(
                deviceState.dpc().componentName(), LOCK_TASK_FEATURE_NONE
            )
            deviceState.dpc().devicePolicyManager().setLockTaskPackages(
                deviceState.dpc().componentName(), arrayOf()
            )
        }
    }

    @PolicyAppliesTest(policy = [LockTask::class])
    @RequireFeature(FEATURE_TELEPHONY)
    @Postsubmit(reason = "b/181993922 automatically marked flaky")
    fun launchEmergencyDialerInLockTaskMode_notWhitelisted_keyguardFeature_launches() {
        assumeTrue(
            "Test requires showing activities",
            TestApis.users().instrumented().canShowActivities()
        )
        val emergencyDialerPackageName = calculateEmergencyDialerPackageName()
        assumeFalse(emergencyDialerPackageName == null)
        try {
            lockTaskTestApp.install().use { testApp ->
                deviceState.dpc().devicePolicyManager().setLockTaskPackages(
                    deviceState.dpc().componentName(),
                    arrayOf(lockTaskTestApp.packageName())
                )
                deviceState.dpc().devicePolicyManager().setLockTaskFeatures(
                    deviceState.dpc().componentName(),
                    LOCK_TASK_FEATURE_KEYGUARD
                )
                val activity = testApp.activities().any().start()
                try {
                    activity.startLockTask()
                    val intent = Intent(ACTION_EMERGENCY_DIAL)
                    intent.setFlags(FLAG_ACTIVITY_NEW_TASK or FLAG_ACTIVITY_CLEAR_TASK)
                    activity.startActivity(intent)
                    assertThat(TestApis.activities().foregroundActivity()?.pkg())
                        .isEqualTo(TestApis.packages().find(emergencyDialerPackageName))
                    assertThat(TestApis.activities().lockTaskModeState).isEqualTo(
                        LOCK_TASK_MODE_LOCKED
                    )
                } finally {
                    activity.stopLockTask()
                }
            }
        } finally {
            deviceState.dpc().devicePolicyManager().setLockTaskFeatures(
                deviceState.dpc().componentName(), LOCK_TASK_FEATURE_NONE
            )
            deviceState.dpc().devicePolicyManager().setLockTaskPackages(
                deviceState.dpc().componentName(), arrayOf()
            )
        }
    }

    @PolicyAppliesTest(policy = [LockTask::class])
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#setLockTaskPackages", "android.app.admin.DevicePolicyManager#getDevicePolicyState"])
    @Postsubmit(reason = "new test")
    fun getDevicePolicyState_setLockTaskPackagesAndFeatures_returnsPolicy() {
            try {
                deviceState.dpc().devicePolicyManager().setLockTaskPackages(
                    deviceState.dpc().componentName(), arrayOf(PACKAGE_NAME)
                )
                deviceState.dpc().devicePolicyManager().setLockTaskFeatures(
                    deviceState.dpc().componentName(), LOCK_TASK_FEATURE
                )
                val policyState =
                    PolicyEngineUtils.getLockTaskPolicyState(
                        NoArgsPolicyKey(LOCK_TASK_POLICY),
                        TestApis.users().instrumented().userHandle()
                    )
                assertThat(policyState.currentResolvedPolicy?.packages).containsExactly(PACKAGE_NAME)
                assertThat(policyState.currentResolvedPolicy?.flags).isEqualTo(LOCK_TASK_FEATURE)
            } finally {
                deviceState.dpc().devicePolicyManager()
                    .setLockTaskPackages(deviceState.dpc().componentName(), arrayOf())
                deviceState.dpc().devicePolicyManager().setLockTaskFeatures(
                    deviceState.dpc().componentName(), LOCK_TASK_FEATURE_NONE
                )
            }
        }

    @Test
    @Postsubmit(reason = "new test")
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#setLockTaskPackages"]) // TODO: enable after adding the broadcast receiver to relevant test apps.
    //    @PolicyAppliesTest(policy = LockTask.class)
    @EnsureHasDeviceOwner(isPrimary = true)
    fun policyUpdateReceiver_setLockTaskPackages_receivedPolicySetBroadcast() {
        try {
            deviceState.dpc().devicePolicyManager().setLockTaskPackages(
                deviceState.dpc().componentName(), arrayOf(PACKAGE_NAME)
            )
            PolicySetResultUtils.assertPolicySetResultReceived(
                deviceState,
                LOCK_TASK_POLICY,
                RESULT_POLICY_SET,
                TargetUser.LOCAL_USER_ID,
                Bundle()
            )
        } finally {
            deviceState.dpc().devicePolicyManager()
                .setLockTaskPackages(deviceState.dpc().componentName(), arrayOf())
            deviceState.dpc().devicePolicyManager().setLockTaskFeatures(
                deviceState.dpc().componentName(), LOCK_TASK_FEATURE_NONE
            )
        }
    }

    @CanSetPolicyTest(policy = [LockTask::class], singleTestOnly = true)
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#setLockTaskPackages", "android.app.admin.DevicePolicyManager#getDevicePolicyState"])
    @Postsubmit(reason = "new test")
    fun getDevicePolicyState_setLockTaskPackages_returnsCorrectResolutionMechanism() {
            try {
                deviceState.dpc().devicePolicyManager().setLockTaskPackages(
                    deviceState.dpc().componentName(), arrayOf(PACKAGE_NAME)
                )
                val policyState =
                    PolicyEngineUtils.getLockTaskPolicyState(
                        NoArgsPolicyKey(LOCK_TASK_POLICY),
                        deviceState.dpc().user().userHandle()
                    )
                assertThat(
                    PolicyEngineUtils.getTopPriorityMechanism(policyState)
                        .highestToLowestPriorityAuthorities
                ).isEqualTo(
                    listOf(
                        RoleAuthority(setOf(PolicyEngineUtils.FINANCED_DEVICE_CONTROLLER_ROLE)),
                        DPC_AUTHORITY
                    )
                )
            } finally {
                deviceState.dpc().devicePolicyManager()
                    .setLockTaskPackages(deviceState.dpc().componentName(), arrayOf())
                deviceState.dpc().devicePolicyManager().setLockTaskFeatures(
                    deviceState.dpc().componentName(), LOCK_TASK_FEATURE_NONE
                )
            }
        }

    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#setLockTaskPackages", "android.app.admin.DevicePolicyManager#isLockTaskPermitted"])
    @RequireAdbRoot
    @MostImportantCoexistenceTest(policy = LockTask::class)
    fun setLockTaskPackages_sameValues_applied() {
        try {
            deviceState.testApp(MORE_IMPORTANT).devicePolicyManager()
                .setLockTaskPackages( /* admin= */
                    null, arrayOf(testApp.packageName())
                )
            deviceState.testApp(LESS_IMPORTANT).devicePolicyManager()
                .setLockTaskPackages( /* admin= */
                    null, arrayOf(testApp.packageName())
                )
            val policyState = PolicyEngineUtils.getLockTaskPolicyState(
                NoArgsPolicyKey(LOCK_TASK_POLICY),
                TestApis.users().instrumented().userHandle()
            )
            assertThat(policyState.currentResolvedPolicy?.packages)
                .containsExactly(testApp.packageName())
            assertThat(localDevicePolicyManager.isLockTaskPermitted(testApp.packageName()))
                .isTrue()
        } finally {
            deviceState.testApp(MORE_IMPORTANT).devicePolicyManager()
                .setLockTaskPackages( /* admin= */
                    null, arrayOf()
                )
            deviceState.testApp(LESS_IMPORTANT).devicePolicyManager()
                .setLockTaskPackages( /* admin= */
                    null, arrayOf()
                )
            deviceState.testApp(MORE_IMPORTANT).devicePolicyManager()
                .setLockTaskFeatures( /* admin= */
                    null,
                    LOCK_TASK_FEATURE_NONE
                )
            deviceState.testApp(LESS_IMPORTANT).devicePolicyManager()
                .setLockTaskFeatures( /* admin= */
                    null,
                    LOCK_TASK_FEATURE_NONE
                )
        }
    }

    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#setLockTaskPackages", "android.app.admin.DevicePolicyManager#isLockTaskPermitted"])
    @MostImportantCoexistenceTest(policy = LockTask::class)
    fun setLockTaskPackages_differentValues_moreImportantApplied() {
        try {
            deviceState.testApp(MORE_IMPORTANT).devicePolicyManager()
                .setLockTaskPackages( /* admin= */
                    null, arrayOf(testApp.packageName())
                )
            deviceState.testApp(LESS_IMPORTANT).devicePolicyManager()
                .setLockTaskPackages( /* admin= */
                    null, arrayOf(secondTestApp.packageName())
                )
            val policyState = PolicyEngineUtils.getLockTaskPolicyState(
                NoArgsPolicyKey(LOCK_TASK_POLICY),
                TestApis.users().instrumented().userHandle()
            )
            assertThat(policyState.currentResolvedPolicy?.packages)
                .containsExactly(testApp.packageName())
            assertThat(localDevicePolicyManager.isLockTaskPermitted(testApp.packageName()))
                .isTrue()
            assertThat(localDevicePolicyManager.isLockTaskPermitted(secondTestApp.packageName()))
                .isFalse()
        } finally {
            deviceState.testApp(MORE_IMPORTANT).devicePolicyManager()
                .setLockTaskPackages( /* admin= */
                    null, arrayOf()
                )
            deviceState.testApp(LESS_IMPORTANT).devicePolicyManager()
                .setLockTaskPackages( /* admin= */
                    null, arrayOf()
                )
            deviceState.testApp(MORE_IMPORTANT).devicePolicyManager()
                .setLockTaskFeatures( /* admin= */
                    null,
                    LOCK_TASK_FEATURE_NONE
                )
            deviceState.testApp(LESS_IMPORTANT).devicePolicyManager()
                .setLockTaskFeatures( /* admin= */
                    null,
                    LOCK_TASK_FEATURE_NONE
                )
        }
    }

    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#setLockTaskPackages", "android.app.admin.DevicePolicyManager#isLockTaskPermitted"])
    @MostImportantCoexistenceTest(policy = LockTask::class)
    fun setLockTaskPackages_differentValuesReverseOrder_moreImportantApplied() {
        try {
            deviceState.testApp(LESS_IMPORTANT).devicePolicyManager()
                .setLockTaskPackages( /* admin= */
                    null, arrayOf(secondTestApp.packageName())
                )
            deviceState.testApp(MORE_IMPORTANT).devicePolicyManager()
                .setLockTaskPackages( /* admin= */
                    null, arrayOf(testApp.packageName())
                )
            val policyState = PolicyEngineUtils.getLockTaskPolicyState(
                NoArgsPolicyKey(LOCK_TASK_POLICY),
                TestApis.users().instrumented().userHandle()
            )
            assertThat(policyState.currentResolvedPolicy?.packages)
                .containsExactly(testApp.packageName())
            assertThat(localDevicePolicyManager.isLockTaskPermitted(testApp.packageName()))
                .isTrue()
            assertThat(localDevicePolicyManager.isLockTaskPermitted(secondTestApp.packageName()))
                .isFalse()
        } finally {
            deviceState.testApp(MORE_IMPORTANT).devicePolicyManager()
                .setLockTaskPackages( /* admin= */
                    null, arrayOf()
                )
            deviceState.testApp(LESS_IMPORTANT).devicePolicyManager()
                .setLockTaskPackages( /* admin= */
                    null, arrayOf()
                )
            deviceState.testApp(MORE_IMPORTANT).devicePolicyManager()
                .setLockTaskFeatures( /* admin= */
                    null,
                    LOCK_TASK_FEATURE_NONE
                )
            deviceState.testApp(LESS_IMPORTANT).devicePolicyManager()
                .setLockTaskFeatures( /* admin= */
                    null,
                    LOCK_TASK_FEATURE_NONE
                )
        }
    }

    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#setLockTaskPackages", "android.app.admin.DevicePolicyManager#isLockTaskPermitted"])
    @MostImportantCoexistenceTest(policy = LockTask::class)
    fun setLockTaskPackages_bothSetThenBothReset_nothingApplied() {
        try {
            deviceState.testApp(MORE_IMPORTANT).devicePolicyManager()
                .setLockTaskPackages( /* admin= */
                    null, arrayOf(testApp.packageName())
                )
            deviceState.testApp(LESS_IMPORTANT).devicePolicyManager()
                .setLockTaskPackages( /* admin= */
                    null, arrayOf(secondTestApp.packageName())
                )
            deviceState.testApp(MORE_IMPORTANT).devicePolicyManager()
                .setLockTaskPackages( /* admin= */
                    null, arrayOf()
                )
            deviceState.testApp(LESS_IMPORTANT).devicePolicyManager()
                .setLockTaskPackages( /* admin= */
                    null, arrayOf()
                )
            deviceState.testApp(MORE_IMPORTANT).devicePolicyManager()
                .setLockTaskFeatures( /* admin= */
                    null,
                    LOCK_TASK_FEATURE_NONE
                )
            deviceState.testApp(LESS_IMPORTANT).devicePolicyManager()
                .setLockTaskFeatures( /* admin= */
                    null,
                    LOCK_TASK_FEATURE_NONE
                )
            val policyState = PolicyEngineUtils.getLockTaskPolicyState(
                NoArgsPolicyKey(LOCK_TASK_POLICY),
                TestApis.users().instrumented().userHandle()
            )
            assertThat(policyState).isNull()
            assertThat(localDevicePolicyManager.isLockTaskPermitted(testApp.packageName()))
                .isFalse()
            assertThat(localDevicePolicyManager.isLockTaskPermitted(secondTestApp.packageName()))
                .isFalse()
        } finally {
            deviceState.testApp(MORE_IMPORTANT).devicePolicyManager()
                .setLockTaskPackages( /* admin= */
                    null, arrayOf()
                )
            deviceState.testApp(LESS_IMPORTANT).devicePolicyManager()
                .setLockTaskPackages( /* admin= */
                    null, arrayOf()
                )
            deviceState.testApp(MORE_IMPORTANT).devicePolicyManager()
                .setLockTaskFeatures( /* admin= */
                    null,
                    LOCK_TASK_FEATURE_NONE
                )
            deviceState.testApp(LESS_IMPORTANT).devicePolicyManager()
                .setLockTaskFeatures( /* admin= */
                    null,
                    LOCK_TASK_FEATURE_NONE
                )
        }
    }

    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#setLockTaskPackages", "android.app.admin.DevicePolicyManager#isLockTaskPermitted"])
    @MostImportantCoexistenceTest(policy = LockTask::class)
    fun setLockTaskPackages_bothSetThenMoreImportantResets_lessImportantApplied() {
        try {
            deviceState.testApp(MORE_IMPORTANT).devicePolicyManager()
                .setLockTaskPackages( /* admin= */
                    null, arrayOf(testApp.packageName())
                )
            deviceState.testApp(LESS_IMPORTANT).devicePolicyManager()
                .setLockTaskPackages( /* admin= */
                    null, arrayOf(secondTestApp.packageName())
                )
            deviceState.testApp(MORE_IMPORTANT).devicePolicyManager()
                .setLockTaskPackages( /* admin= */
                    null, arrayOf()
                )
            deviceState.testApp(MORE_IMPORTANT).devicePolicyManager()
                .setLockTaskFeatures( /* admin= */
                    null,
                    LOCK_TASK_FEATURE_NONE
                )
            val policyState = PolicyEngineUtils.getLockTaskPolicyState(
                NoArgsPolicyKey(LOCK_TASK_POLICY),
                TestApis.users().instrumented().userHandle()
            )
            assertThat(policyState.currentResolvedPolicy?.packages)
                .containsExactly(secondTestApp.packageName())
            assertThat(localDevicePolicyManager.isLockTaskPermitted(testApp.packageName()))
                .isFalse()
            assertThat(localDevicePolicyManager.isLockTaskPermitted(secondTestApp.packageName()))
                .isTrue()
        } finally {
            deviceState.testApp(MORE_IMPORTANT).devicePolicyManager()
                .setLockTaskPackages( /* admin= */
                    null, arrayOf()
                )
            deviceState.testApp(LESS_IMPORTANT).devicePolicyManager()
                .setLockTaskPackages( /* admin= */
                    null, arrayOf()
                )
            deviceState.testApp(MORE_IMPORTANT).devicePolicyManager()
                .setLockTaskFeatures( /* admin= */
                    null,
                    LOCK_TASK_FEATURE_NONE
                )
            deviceState.testApp(LESS_IMPORTANT).devicePolicyManager()
                .setLockTaskFeatures( /* admin= */
                    null,
                    LOCK_TASK_FEATURE_NONE
                )
        }
    }

    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#setLockTaskPackages", "android.app.admin.DevicePolicyManager#isLockTaskPermitted"])
    @MostImportantCoexistenceTest(policy = LockTask::class)
    fun setLockTaskPackages_bothSetSameValueThenMoreImportantResets_lessImportantApplied() {
        try {
            deviceState.testApp(MORE_IMPORTANT).devicePolicyManager()
                .setLockTaskPackages( /* admin= */
                    null, arrayOf(testApp.packageName())
                )
            deviceState.testApp(LESS_IMPORTANT).devicePolicyManager()
                .setLockTaskPackages( /* admin= */
                    null, arrayOf(testApp.packageName())
                )
            deviceState.testApp(MORE_IMPORTANT).devicePolicyManager()
                .setLockTaskPackages( /* admin= */
                    null, arrayOf()
                )
            deviceState.testApp(MORE_IMPORTANT).devicePolicyManager()
                .setLockTaskFeatures( /* admin= */
                    null,
                    LOCK_TASK_FEATURE_NONE
                )
            val policyState = PolicyEngineUtils.getLockTaskPolicyState(
                NoArgsPolicyKey(LOCK_TASK_POLICY),
                TestApis.users().instrumented().userHandle()
            )
            assertThat(policyState.currentResolvedPolicy?.packages)
                .containsExactly(testApp.packageName())
            assertThat(localDevicePolicyManager.isLockTaskPermitted(testApp.packageName()))
                .isTrue()
        } finally {
            deviceState.testApp(MORE_IMPORTANT).devicePolicyManager()
                .setLockTaskPackages( /* admin= */
                    null, arrayOf()
                )
            deviceState.testApp(LESS_IMPORTANT).devicePolicyManager()
                .setLockTaskPackages( /* admin= */
                    null, arrayOf()
                )
            deviceState.testApp(MORE_IMPORTANT).devicePolicyManager()
                .setLockTaskFeatures( /* admin= */
                    null,
                    LOCK_TASK_FEATURE_NONE
                )
            deviceState.testApp(LESS_IMPORTANT).devicePolicyManager()
                .setLockTaskFeatures( /* admin= */
                    null,
                    LOCK_TASK_FEATURE_NONE
                )
        }
    }

    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#setLockTaskPackages", "android.app.admin.DevicePolicyManager#isLockTaskPermitted"])
    @MostImportantCoexistenceTest(policy = LockTask::class)
    fun setLockTaskPackages_moreImportantSetsFeaturesOnly_moreImportantApplies() {
        try {
            deviceState.testApp(MORE_IMPORTANT).devicePolicyManager()
                .setLockTaskFeatures( /* admin= */
                    null,
                    LOCK_TASK_FEATURE
                )
            deviceState.testApp(LESS_IMPORTANT).devicePolicyManager()
                .setLockTaskPackages( /* admin= */
                    null, arrayOf(secondTestApp.packageName())
                )
            deviceState.testApp(LESS_IMPORTANT).devicePolicyManager()
                .setLockTaskFeatures( /* admin= */
                    null,
                    SECOND_LOCK_TASK_FEATURE
                )
            val policyState = PolicyEngineUtils.getLockTaskPolicyState(
                NoArgsPolicyKey(LOCK_TASK_POLICY),
                TestApis.users().instrumented().userHandle()
            )
            assertThat(policyState.currentResolvedPolicy?.packages)
                .isEmpty()
            assertThat(policyState.currentResolvedPolicy?.flags)
                .isEqualTo(LOCK_TASK_FEATURE)
            assertThat(localDevicePolicyManager.isLockTaskPermitted(secondTestApp.packageName()))
                .isFalse()
        } finally {
            deviceState.testApp(MORE_IMPORTANT).devicePolicyManager()
                .setLockTaskPackages( /* admin= */
                    null, arrayOf()
                )
            deviceState.testApp(LESS_IMPORTANT).devicePolicyManager()
                .setLockTaskPackages( /* admin= */
                    null, arrayOf()
                )
            deviceState.testApp(MORE_IMPORTANT).devicePolicyManager()
                .setLockTaskFeatures( /* admin= */
                    null,
                    LOCK_TASK_FEATURE_NONE
                )
            deviceState.testApp(LESS_IMPORTANT).devicePolicyManager()
                .setLockTaskFeatures( /* admin= */
                    null,
                    LOCK_TASK_FEATURE_NONE
                )
        }
    }

    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#setLockTaskPackages", "android.app.admin.DevicePolicyManager#isLockTaskPermitted"])
    @MostImportantCoexistenceTest(policy = LockTask::class)
    fun setLockTaskPackages_setByDPCAndPermission_DPCRemoved_stillEnforced() {
        try {
            deviceState.testApp(MORE_IMPORTANT).devicePolicyManager()
                .setLockTaskPackages( /* admin= */
                    null, arrayOf(testApp.packageName())
                )
            deviceState.testApp(LESS_IMPORTANT).devicePolicyManager()
                .setLockTaskPackages( /* admin= */
                    null, arrayOf(secondTestApp.packageName())
                )

            // Remove DPC
            deviceState.dpc().devicePolicyManager().clearDeviceOwnerApp(
                deviceState.dpc().packageName()
            )
            val policyState = PolicyEngineUtils.getLockTaskPolicyState(
                NoArgsPolicyKey(LOCK_TASK_POLICY),
                TestApis.users().instrumented().userHandle()
            )
            assertThat(policyState.currentResolvedPolicy?.packages)
                .containsExactly(secondTestApp.packageName())
            assertThat(localDevicePolicyManager.isLockTaskPermitted(testApp.packageName()))
                .isFalse()
            assertThat(localDevicePolicyManager.isLockTaskPermitted(secondTestApp.packageName()))
                .isTrue()
        } finally {
            try {
                deviceState.testApp(MORE_IMPORTANT)
                    .devicePolicyManager().setLockTaskPackages( /* admin= */
                    null, arrayOf()
                )
                deviceState.testApp(MORE_IMPORTANT)
                    .devicePolicyManager().setLockTaskFeatures( /* admin= */
                    null,
                    LOCK_TASK_FEATURE_NONE
                )
            } catch (e: Exception) {
                // expected if app was uninstalled
            }
            try {
                deviceState.testApp(LESS_IMPORTANT)
                    .devicePolicyManager().setLockTaskPackages( /* admin= */
                    null, arrayOf()
                )
                deviceState.testApp(LESS_IMPORTANT)
                    .devicePolicyManager().setLockTaskFeatures( /* admin= */
                    null,
                    LOCK_TASK_FEATURE_NONE
                )
            } catch (e: Exception) {
                // expected if app was uninstalled
            }
        }
    }

    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#setLockTaskPackages", "android.app.admin.DevicePolicyManager#isLockTaskPermitted"])
    @MostImportantCoexistenceTest(policy = LockTask::class)
    fun setLockTaskPackages_setByPermission_appRemoved_notEnforced() {
        try {
            deviceState.testApp(LESS_IMPORTANT).devicePolicyManager()
                .setLockTaskPackages( /* admin= */ null,
                    arrayOf(testApp.packageName())
                )

            // uninstall app
            deviceState.testApp(LESS_IMPORTANT).uninstall()
            SystemClock.sleep(500)
            val policyState = PolicyEngineUtils.getLockTaskPolicyState(
                NoArgsPolicyKey(LOCK_TASK_POLICY),
                TestApis.users().instrumented().userHandle()
            )
            assertThat(policyState).isNull()
            assertThat(localDevicePolicyManager.isLockTaskPermitted(testApp.packageName()))
                .isFalse()
        } finally {
            try {
                deviceState.testApp(MORE_IMPORTANT)
                    .devicePolicyManager().setLockTaskPackages( /* admin= */
                    null, arrayOf()
                )
                deviceState.testApp(MORE_IMPORTANT)
                    .devicePolicyManager().setLockTaskFeatures( /* admin= */
                    null,
                    LOCK_TASK_FEATURE_NONE
                )
            } catch (e: Exception) {
                // expected if app was uninstalled
            }
            try {
                deviceState.testApp(LESS_IMPORTANT)
                    .devicePolicyManager().setLockTaskPackages( /* admin= */
                    null, arrayOf()
                )
                deviceState.testApp(LESS_IMPORTANT)
                    .devicePolicyManager().setLockTaskFeatures( /* admin= */
                    null,
                    LOCK_TASK_FEATURE_NONE
                )
            } catch (e: Exception) {
                // expected if app was uninstalled
            }
        }
    }

    // TODO: Add tests that the DeviceAdminReceiver methods are called correctly (onLockTaskModeEntering, onLockTaskModeExiting)

    companion object {
        @JvmField
        @ClassRule
        @Rule
        val deviceState = DeviceState()
        
        private const val PACKAGE_NAME = "com.android.package.test"
        
        private val localDevicePolicyManager =
            TestApis.context().instrumentedContext().getSystemService(
                DevicePolicyManager::class.java
            )!!

        /**
         * Option to launch activities in fullscreen. This is needed to properly use lock task mode on
         * freeform windowing devices. See b/273644378 for more context.
         */
        private val LAUNCH_FULLSCREEN_OPTIONS = ActivityOptions.makeBasic().apply {
            launchWindowingMode = WINDOWING_MODE_FULLSCREEN
        }

        private val lockTaskTestApp = deviceState.testApps().query()
            .wherePackageName().isEqualTo("com.android.bedstead.testapp.LockTaskApp")
            .get() // TODO(scottjonathan): filter by containing activity not by package name
        private val testApp = deviceState.testApps().query().whereActivities().isNotEmpty().get()
        private val secondTestApp =
            deviceState.testApps().query().whereActivities().isNotEmpty().get()
        private const val LOCK_TASK_FEATURE = LOCK_TASK_FEATURE_HOME
        private const val SECOND_LOCK_TASK_FEATURE = LOCK_TASK_FEATURE_GLOBAL_ACTIONS
        private val BLOCKED_ACTIVITY_COMPONENT = TestApis.packages().component(
            ComponentName(
                "android", "com.android.internal.app.BlockedAppActivity"
            )
        )
        private const val ACTION_EMERGENCY_DIAL = "com.android.phone.EmergencyDialer.DIAL"

        private fun calculateEmergencyDialerPackageName() =
            TestApis.context().instrumentedContext().packageManager?.resolveActivity(
                Intent(ACTION_EMERGENCY_DIAL).addFlags(FLAG_ACTIVITY_NEW_TASK),
                PackageManager.MATCH_DEFAULT_ONLY
            )?.activityInfo?.packageName
    }
}
