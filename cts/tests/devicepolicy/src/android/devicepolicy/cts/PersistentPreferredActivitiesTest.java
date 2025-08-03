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

package android.devicepolicy.cts;

import static android.app.admin.DevicePolicyIdentifiers.PERSISTENT_PREFERRED_ACTIVITY_POLICY;
import static android.app.admin.TargetUser.LOCAL_USER_ID;
import static android.devicepolicy.cts.utils.PolicyEngineUtils.FINANCED_DEVICE_CONTROLLER_ROLE;

import static com.android.bedstead.harrier.annotations.enterprise.MostImportantCoexistenceTest.LESS_IMPORTANT;
import static com.android.bedstead.harrier.annotations.enterprise.MostImportantCoexistenceTest.MORE_IMPORTANT;
import static com.android.queryable.queries.ActivityQuery.activity;
import static com.android.queryable.queries.IntentFilterQuery.intentFilter;

import static com.google.common.truth.Truth.assertThat;

import android.app.admin.DpcAuthority;
import android.app.admin.IntentFilterPolicyKey;
import android.app.admin.PolicyState;
import android.app.admin.PolicyUpdateReceiver;
import android.app.admin.PolicyUpdateResult;
import android.app.admin.RoleAuthority;
import android.content.ComponentName;
import android.content.Intent;
import android.content.IntentFilter;
import android.devicepolicy.cts.utils.PolicyEngineUtils;
import android.devicepolicy.cts.utils.PolicySetResultUtils;
import android.os.Bundle;
import android.os.SystemClock;
import android.stats.devicepolicy.EventId;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.harrier.annotations.enterprise.CanSetPolicyTest;
import com.android.bedstead.harrier.annotations.enterprise.CannotSetPolicyTest;
import com.android.bedstead.harrier.annotations.enterprise.EnsureHasDeviceOwner;
import com.android.bedstead.harrier.annotations.enterprise.MostImportantCoexistenceTest;
import com.android.bedstead.harrier.annotations.enterprise.PolicyAppliesTest;
import com.android.bedstead.harrier.policies.PersistentPreferredActivities;
import com.android.bedstead.metricsrecorder.EnterpriseMetricsRecorder;
import com.android.bedstead.metricsrecorder.truth.MetricQueryBuilderSubject;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.packages.ComponentReference;
import com.android.bedstead.nene.utils.Poll;
import com.android.bedstead.testapp.TestApp;
import com.android.bedstead.testapp.TestAppInstance;
import com.android.compatibility.common.util.ApiTest;
import com.android.queryable.info.ActivityInfo;

import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.testng.Assert;

import java.util.List;
import java.util.Set;

@RunWith(BedsteadJUnit4.class)
public final class PersistentPreferredActivitiesTest {
    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private static final String TEST_ACTION = "com.android.cts.deviceandprofileowner.CONFIRM";

    private static final TestApp sTestAppWithMultipleActivities =
            sDeviceState.testApps().query()
                    .whereActivities().contains(
                            activity().where().intentFilters().contains(
                                    intentFilter()
                                            .where().categories().contains(
                                                    Intent.CATEGORY_DEFAULT)
                                            .where().actions().contains(TEST_ACTION)))
                    .get();

    private static final ActivityInfo PREFERRED_ACTIVITY =
            sTestAppWithMultipleActivities.activities().query()
                    .where().intentFilters().contains(
                            intentFilter().where().actions().contains(TEST_ACTION))
                    .get();
    private static final ActivityInfo UNPREFERRED_ACTIVITY =
            sTestAppWithMultipleActivities.activities().query()
                    .where().intentFilters().contains(
                            intentFilter().where().actions().contains(TEST_ACTION))
                    .where().activityClass().className()
                    .isNotEqualTo(PREFERRED_ACTIVITY.className())
                    .get();

    @CannotSetPolicyTest(policy = PersistentPreferredActivities.class)
    @ApiTest(apis="android.admin.app.DevicePolicyManager#addPersistentPreferredActivity")
    @Postsubmit(reason = "new test")
    public void addPersistentPreferredActivity_notPermitted_throwsException() {
        try (TestAppInstance testAppInstance = sTestAppWithMultipleActivities.install()) {
            IntentFilter intentFilter = new IntentFilter(TEST_ACTION);
            intentFilter.addCategory(Intent.CATEGORY_DEFAULT);
            Assert.assertThrows(SecurityException.class, () -> {
                sDeviceState.dpc().devicePolicyManager()
                        .addPersistentPreferredActivity(
                                sDeviceState.dpc().componentName(),
                                intentFilter,
                                new ComponentName(testAppInstance.packageName(),
                                        PREFERRED_ACTIVITY.className()));
                });
        }
    }

    @CannotSetPolicyTest(policy = PersistentPreferredActivities.class)
    @ApiTest(apis="android.admin.app.DevicePolicyManager#clearPackagePersistentPreferredActivities")
    @Postsubmit(reason = "new test")
    public void clearPackagePersistentPreferredActivities_notPermitted_throwsException() {
        try (TestAppInstance testAppInstance = sTestAppWithMultipleActivities.install()) {
            IntentFilter intentFilter = new IntentFilter(TEST_ACTION);
            intentFilter.addCategory(Intent.CATEGORY_DEFAULT);
            Assert.assertThrows(SecurityException.class, () -> {
                sDeviceState.dpc().devicePolicyManager()
                        .clearPackagePersistentPreferredActivities(
                                sDeviceState.dpc().componentName(),
                                testAppInstance.packageName());
            });
        }
    }

    @PolicyAppliesTest(policy = PersistentPreferredActivities.class)
    @Postsubmit(reason = "new test")
    public void sendIntent_hasMultipleDefaultReceivers_launchesResolverActivity() {
        try (TestAppInstance testAppInstance = sTestAppWithMultipleActivities.install()) {
            TestApis.context().instrumentedContext().startActivity(
                    new Intent(TEST_ACTION).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));

            Poll.forValue("Recent Activities contain resolver",
                            this::checkRecentActivitiesContainResolver)
                    .toBeEqualTo(true)
                    .errorOnFail()
                    .await();
        }
    }

    @PolicyAppliesTest(policy = PersistentPreferredActivities.class)
    @ApiTest(apis="android.admin.app.DevicePolicyManager#addPersistentPreferredActivity")
    @Postsubmit(reason = "new test")
    public void sendIntent_hasPreferredReceiver_launchesPreferredActivity() {
        try (TestAppInstance testAppInstance = sTestAppWithMultipleActivities.install()) {
            try {
                IntentFilter intentFilter = new IntentFilter(TEST_ACTION);
                intentFilter.addCategory(Intent.CATEGORY_DEFAULT);
                sDeviceState.dpc().devicePolicyManager()
                        .addPersistentPreferredActivity(
                                sDeviceState.dpc().componentName(),
                                intentFilter,
                                new ComponentName(testAppInstance.packageName(),
                                        PREFERRED_ACTIVITY.className()));

                TestApis.context().instrumentedContext().startActivity(
                        new Intent(TEST_ACTION).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));

                Poll.forValue("Recent activities contain preferred activity",
                                () -> recentActivitiesContainsActivity(
                                        PREFERRED_ACTIVITY.className()))
                        .toBeEqualTo(true)
                        .errorOnFail()
                        .await();
                Poll.forValue("Recent activities does not contain unpreferred activity",
                                () -> recentActivitiesContainsActivity(
                                        UNPREFERRED_ACTIVITY.className()))
                        .toBeEqualTo(false)
                        .errorOnFail()
                        .await();
            } finally {
                sDeviceState.dpc().devicePolicyManager()
                        .clearPackagePersistentPreferredActivities(
                                sDeviceState.dpc().componentName(),
                                testAppInstance.packageName());
            }
        }
    }

    @PolicyAppliesTest(policy = PersistentPreferredActivities.class)
    @ApiTest(apis="android.admin.app.DevicePolicyManager#addPersistentPreferredActivity")
    @Postsubmit(reason = "new test")
    public void addPersistentPreferredActivity_metricLogged() {
        try (TestAppInstance testAppInstance = sTestAppWithMultipleActivities.install();
             EnterpriseMetricsRecorder metrics = EnterpriseMetricsRecorder.create()) {
            try {
                IntentFilter intentFilter = new IntentFilter(TEST_ACTION);
                intentFilter.addCategory(Intent.CATEGORY_DEFAULT);
                sDeviceState.dpc().devicePolicyManager()
                        .addPersistentPreferredActivity(
                                sDeviceState.dpc().componentName(),
                                intentFilter,
                                new ComponentName(testAppInstance.packageName(),
                                        PREFERRED_ACTIVITY.className()));

                MetricQueryBuilderSubject.assertThat(metrics.query()
                                .whereType().isEqualTo(
                                        EventId.ADD_PERSISTENT_PREFERRED_ACTIVITY_VALUE)
                                .whereAdminPackageName().isEqualTo(
                                        sDeviceState.dpc().packageName())
                                .whereStrings().contains(testAppInstance.packageName()))
                        .wasLogged();
            } finally {
                sDeviceState.dpc().devicePolicyManager()
                        .clearPackagePersistentPreferredActivities(
                                sDeviceState.dpc().componentName(),
                                testAppInstance.packageName());
            }
        }
    }

    @PolicyAppliesTest(policy = PersistentPreferredActivities.class)
    @ApiTest(apis="android.admin.app.DevicePolicyManager#clearPackagePersistentPreferredActivities")
    @Postsubmit(reason = "new test")
    public void sendIntent_clearPreferredActivity_launchesResolverActivity() {
        try (TestAppInstance testAppInstance = sTestAppWithMultipleActivities.install()) {
            IntentFilter intentFilter = new IntentFilter(TEST_ACTION);
            intentFilter.addCategory(Intent.CATEGORY_DEFAULT);
            sDeviceState.dpc().devicePolicyManager()
                    .addPersistentPreferredActivity(
                            sDeviceState.dpc().componentName(),
                            intentFilter,
                            new ComponentName(
                                    testAppInstance.packageName(), PREFERRED_ACTIVITY.className()));

            sDeviceState.dpc().devicePolicyManager()
                    .clearPackagePersistentPreferredActivities(
                            sDeviceState.dpc().componentName(),
                            testAppInstance.packageName());
            TestApis.context().instrumentedContext().startActivity(
                    new Intent(TEST_ACTION).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));

            Poll.forValue("Recent Activities contain resolver",
                            this::checkRecentActivitiesContainResolver)
                    .toBeEqualTo(true)
                    .errorOnFail()
                    .await();
        }
    }

    @Test
    @Postsubmit(reason = "new test")
    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#addPersistentPreferredActivity",
            "android.app.admin.DevicePolicyManager#clearPackagePersistentPreferredActivities",
            "android.app.admin.DevicePolicyManager#getDevicePolicyState"})
    @PolicyAppliesTest(policy = PersistentPreferredActivities.class)
    public void getDevicePolicyState_addPersistentPreferredActivity_returnsPolicy() {
        try (TestAppInstance testAppInstance = sTestAppWithMultipleActivities.install()) {
            try {
                ComponentName componentName = new ComponentName(
                        testAppInstance.packageName(), PREFERRED_ACTIVITY.className());
                IntentFilter intentFilter = new IntentFilter(TEST_ACTION);
                intentFilter.addCategory(Intent.CATEGORY_DEFAULT);
                sDeviceState.dpc().devicePolicyManager()
                        .addPersistentPreferredActivity(
                                sDeviceState.dpc().componentName(),
                                intentFilter,
                                componentName);

                PolicyState<ComponentName> policyState =
                        PolicyEngineUtils.getComponentNamePolicyState(
                                new IntentFilterPolicyKey(
                                        PERSISTENT_PREFERRED_ACTIVITY_POLICY,
                                        intentFilter),
                                TestApis.users().instrumented().userHandle());

                assertThat(policyState.getCurrentResolvedPolicy()).isEqualTo(componentName);
            } finally {
                sDeviceState.dpc().devicePolicyManager()
                        .clearPackagePersistentPreferredActivities(
                                sDeviceState.dpc().componentName(),
                                testAppInstance.packageName());
            }
        }
    }

    @Ignore("b/283078332 use polling instead of sleep for uninstalling")
    @Test
    @Postsubmit(reason = "new test")
    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#addPersistentPreferredActivity",
            "android.app.admin.DevicePolicyManager#clearPackagePersistentPreferredActivities",
            "android.app.admin.DevicePolicyManager#getDevicePolicyState"})
    @PolicyAppliesTest(policy = PersistentPreferredActivities.class)
    public void getDevicePolicyState_addPersistentPreferredActivity_activityUninstalled_returnsNull() {
        try (TestAppInstance testAppInstance = sTestAppWithMultipleActivities.install()) {
            try {
                ComponentName componentName = new ComponentName(
                        testAppInstance.packageName(), PREFERRED_ACTIVITY.className());
                IntentFilter intentFilter = new IntentFilter(TEST_ACTION);
                intentFilter.addCategory(Intent.CATEGORY_DEFAULT);
                sDeviceState.dpc().devicePolicyManager()
                        .addPersistentPreferredActivity(
                                sDeviceState.dpc().componentName(),
                                intentFilter,
                                componentName);

                // Remove app
                sTestAppWithMultipleActivities.uninstall();
                SystemClock.sleep(500);

                PolicyState<ComponentName> policyState =
                        PolicyEngineUtils.getComponentNamePolicyState(
                                new IntentFilterPolicyKey(
                                        PERSISTENT_PREFERRED_ACTIVITY_POLICY,
                                        intentFilter),
                                TestApis.users().instrumented().userHandle());
                assertThat(policyState).isNull();
            } finally {
                sDeviceState.dpc().devicePolicyManager()
                        .clearPackagePersistentPreferredActivities(
                                sDeviceState.dpc().componentName(),
                                testAppInstance.packageName());
            }
        }
    }

    @Test
    @Postsubmit(reason = "new test")
    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#addPersistentPreferredActivity",
            "android.app.admin.DevicePolicyManager#clearPackagePersistentPreferredActivities",
            "android.app.admin.DevicePolicyManager#getDevicePolicyState"})
    @PolicyAppliesTest(policy = PersistentPreferredActivities.class)
    public void getDevicePolicyState_addPersistentPreferredActivity_nonExistingActivity_returnsNull() {
        ComponentName componentName = new ComponentName(
                "dummy package", "dummy class");
        try {
            IntentFilter intentFilter = new IntentFilter(TEST_ACTION);
            intentFilter.addCategory(Intent.CATEGORY_DEFAULT);
            sDeviceState.dpc().devicePolicyManager()
                    .addPersistentPreferredActivity(
                            sDeviceState.dpc().componentName(),
                            intentFilter,
                            componentName);

            PolicyState<ComponentName> policyState =
                    PolicyEngineUtils.getComponentNamePolicyState(
                            new IntentFilterPolicyKey(
                                    PERSISTENT_PREFERRED_ACTIVITY_POLICY,
                                    intentFilter),
                            TestApis.users().instrumented().userHandle());
            assertThat(policyState).isNull();
        } finally {
            sDeviceState.dpc().devicePolicyManager()
                    .clearPackagePersistentPreferredActivities(
                            sDeviceState.dpc().componentName(),
                            componentName.getPackageName());
        }
    }

    @Test
    @Postsubmit(reason = "new test")
    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#addPersistentPreferredActivity"})
    // TODO: enable after adding the broadcast receiver to relevant test apps.
//    @PolicyAppliesTest(policy = PersistentPreferredActivities.class)
    @EnsureHasDeviceOwner(isPrimary = true)
    @Ignore("figure out why it's failing")
    public void policyUpdateReceiver_addPersistentPreferredActivity_receivedPolicySetBroadcast() {
        try (TestAppInstance testAppInstance = sTestAppWithMultipleActivities.install()) {
            try {
                IntentFilter intentFilter = new IntentFilter(TEST_ACTION);
                intentFilter.addCategory(Intent.CATEGORY_DEFAULT);
                Bundle bundle = new Bundle();
                bundle.putParcelable(PolicyUpdateReceiver.EXTRA_INTENT_FILTER, intentFilter);
                sDeviceState.dpc().devicePolicyManager()
                        .addPersistentPreferredActivity(
                                sDeviceState.dpc().componentName(),
                                intentFilter,
                                new ComponentName(
                                        testAppInstance.packageName(),
                                        PREFERRED_ACTIVITY.className()));

                PolicySetResultUtils.assertPolicySetResultReceived(sDeviceState,
                        PERSISTENT_PREFERRED_ACTIVITY_POLICY,
                        PolicyUpdateResult.RESULT_POLICY_SET, LOCAL_USER_ID, bundle);

            } finally {
                sDeviceState.dpc().devicePolicyManager()
                        .clearPackagePersistentPreferredActivities(
                                sDeviceState.dpc().componentName(),
                                testAppInstance.packageName());
            }
        }
    }

    @Test
    @Postsubmit(reason = "new test")
    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#addPersistentPreferredActivity",
            "android.app.admin.DevicePolicyManager#clearPackagePersistentPreferredActivities",
            "android.app.admin.DevicePolicyManager#getDevicePolicyState"})
    @CanSetPolicyTest(policy = PersistentPreferredActivities.class, singleTestOnly = true)
    public void getDevicePolicyState_addPersistentPreferredActivity_returnsCorrectResolutionMechanism() {
        try (TestAppInstance testAppInstance = sTestAppWithMultipleActivities.install()) {
            try {
                IntentFilter intentFilter = new IntentFilter(TEST_ACTION);
                intentFilter.addCategory(Intent.CATEGORY_DEFAULT);
                sDeviceState.dpc().devicePolicyManager()
                        .addPersistentPreferredActivity(
                                sDeviceState.dpc().componentName(),
                                intentFilter,
                                new ComponentName(
                                        testAppInstance.packageName(),
                                        PREFERRED_ACTIVITY.className()));

                PolicyState<ComponentName> policyState =
                        PolicyEngineUtils.getComponentNamePolicyState(
                                new IntentFilterPolicyKey(
                                        PERSISTENT_PREFERRED_ACTIVITY_POLICY,
                                        intentFilter),
                                TestApis.users().instrumented().userHandle());

                assertThat(PolicyEngineUtils.getTopPriorityMechanism(policyState)
                        .getHighestToLowestPriorityAuthorities()).isEqualTo(
                        List.of(
                                new RoleAuthority(Set.of(FINANCED_DEVICE_CONTROLLER_ROLE)),
                                DpcAuthority.DPC_AUTHORITY));

            } finally {
                sDeviceState.dpc().devicePolicyManager()
                        .clearPackagePersistentPreferredActivities(
                                sDeviceState.dpc().componentName(),
                                testAppInstance.packageName());
            }
        }
    }

    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#addPersistentPreferredActivity",
            "android.app.admin.DevicePolicyManager#clearPackagePersistentPreferredActivities"})
    @MostImportantCoexistenceTest(policy = PersistentPreferredActivities.class)
    public void addPersistentPreferredActivity_sameValues_applied() {
        try (TestAppInstance testAppInstance = sTestAppWithMultipleActivities.install()) {
            try {
                ComponentName componentName = new ComponentName(
                        testAppInstance.packageName(), PREFERRED_ACTIVITY.className());
                IntentFilter intentFilter = new IntentFilter(TEST_ACTION);
                intentFilter.addCategory(Intent.CATEGORY_DEFAULT);
                sDeviceState.testApp(MORE_IMPORTANT).devicePolicyManager()
                        .addPersistentPreferredActivity(
                                /* admin= */ null,
                                intentFilter,
                                componentName);
                sDeviceState.testApp(LESS_IMPORTANT).devicePolicyManager()
                        .addPersistentPreferredActivity(
                                /* admin= */ null,
                                intentFilter,
                                componentName);

                PolicyState<ComponentName> policyState =
                        PolicyEngineUtils.getComponentNamePolicyState(
                                new IntentFilterPolicyKey(
                                        PERSISTENT_PREFERRED_ACTIVITY_POLICY,
                                        intentFilter),
                                TestApis.users().instrumented().userHandle());
                assertThat(policyState.getCurrentResolvedPolicy()).isEqualTo(componentName);

            } finally {
                sDeviceState.testApp(MORE_IMPORTANT).devicePolicyManager()
                        .clearPackagePersistentPreferredActivities(
                                /* admin= */ null,
                                testAppInstance.packageName());
                sDeviceState.testApp(LESS_IMPORTANT).devicePolicyManager()
                        .clearPackagePersistentPreferredActivities(
                                /* admin= */ null,
                                testAppInstance.packageName());
            }
        }
    }

    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#addPersistentPreferredActivity",
            "android.app.admin.DevicePolicyManager#clearPackagePersistentPreferredActivities"})
    @MostImportantCoexistenceTest(policy = PersistentPreferredActivities.class)
    public void addPersistentPreferredActivity_differentValues_moreImportantApplied() {
        try (TestAppInstance testAppInstance = sTestAppWithMultipleActivities.install()) {
            try {
                ComponentName firstComponentName = new ComponentName(
                        testAppInstance.packageName(), PREFERRED_ACTIVITY.className());
                ComponentName secondComponentName = new ComponentName(
                        testAppInstance.packageName(), UNPREFERRED_ACTIVITY.className());
                IntentFilter intentFilter = new IntentFilter(TEST_ACTION);
                intentFilter.addCategory(Intent.CATEGORY_DEFAULT);
                sDeviceState.testApp(MORE_IMPORTANT).devicePolicyManager()
                        .addPersistentPreferredActivity(
                                /* admin= */ null,
                                intentFilter,
                                firstComponentName);
                sDeviceState.testApp(LESS_IMPORTANT).devicePolicyManager()
                        .addPersistentPreferredActivity(
                                /* admin= */ null,
                                intentFilter,
                                secondComponentName);

                PolicyState<ComponentName> policyState =
                        PolicyEngineUtils.getComponentNamePolicyState(
                                new IntentFilterPolicyKey(
                                        PERSISTENT_PREFERRED_ACTIVITY_POLICY,
                                        intentFilter),
                                TestApis.users().instrumented().userHandle());
                assertThat(policyState.getCurrentResolvedPolicy()).isEqualTo(firstComponentName);

            } finally {
                sDeviceState.testApp(MORE_IMPORTANT).devicePolicyManager()
                        .clearPackagePersistentPreferredActivities(
                                /* admin= */ null,
                                testAppInstance.packageName());
                sDeviceState.testApp(LESS_IMPORTANT).devicePolicyManager()
                        .clearPackagePersistentPreferredActivities(
                                /* admin= */ null,
                                testAppInstance.packageName());
            }
        }
    }

    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#addPersistentPreferredActivity",
            "android.app.admin.DevicePolicyManager#clearPackagePersistentPreferredActivities"})
    @MostImportantCoexistenceTest(policy = PersistentPreferredActivities.class)
    public void addPersistentPreferredActivity_differentValuesReverseOrder_moreImportantApplied() {
        try (TestAppInstance testAppInstance = sTestAppWithMultipleActivities.install()) {
            try {
                ComponentName firstComponentName = new ComponentName(
                        testAppInstance.packageName(), PREFERRED_ACTIVITY.className());
                ComponentName secondComponentName = new ComponentName(
                        testAppInstance.packageName(), UNPREFERRED_ACTIVITY.className());
                IntentFilter intentFilter = new IntentFilter(TEST_ACTION);
                intentFilter.addCategory(Intent.CATEGORY_DEFAULT);
                sDeviceState.testApp(LESS_IMPORTANT).devicePolicyManager()
                        .addPersistentPreferredActivity(
                                /* admin= */ null,
                                intentFilter,
                                secondComponentName);
                sDeviceState.testApp(MORE_IMPORTANT).devicePolicyManager()
                        .addPersistentPreferredActivity(
                                /* admin= */ null,
                                intentFilter,
                                firstComponentName);

                PolicyState<ComponentName> policyState =
                        PolicyEngineUtils.getComponentNamePolicyState(
                                new IntentFilterPolicyKey(
                                        PERSISTENT_PREFERRED_ACTIVITY_POLICY,
                                        intentFilter),
                                TestApis.users().instrumented().userHandle());
                assertThat(policyState.getCurrentResolvedPolicy()).isEqualTo(firstComponentName);

            } finally {
                sDeviceState.testApp(MORE_IMPORTANT).devicePolicyManager()
                        .clearPackagePersistentPreferredActivities(
                                /* admin= */ null,
                                testAppInstance.packageName());
                sDeviceState.testApp(LESS_IMPORTANT).devicePolicyManager()
                        .clearPackagePersistentPreferredActivities(
                                /* admin= */ null,
                                testAppInstance.packageName());
            }
        }
    }

    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#addPersistentPreferredActivity",
            "android.app.admin.DevicePolicyManager#clearPackagePersistentPreferredActivities"})
    @MostImportantCoexistenceTest(policy = PersistentPreferredActivities.class)
    public void addPersistentPreferredActivity_bothSetThenBothReset_nothingApplied() {
        try (TestAppInstance testAppInstance = sTestAppWithMultipleActivities.install()) {
            try {
                ComponentName firstComponentName = new ComponentName(
                        testAppInstance.packageName(), PREFERRED_ACTIVITY.className());
                ComponentName secondComponentName = new ComponentName(
                        testAppInstance.packageName(), UNPREFERRED_ACTIVITY.className());
                IntentFilter intentFilter = new IntentFilter(TEST_ACTION);
                intentFilter.addCategory(Intent.CATEGORY_DEFAULT);
                sDeviceState.testApp(LESS_IMPORTANT).devicePolicyManager()
                        .addPersistentPreferredActivity(
                                /* admin= */ null,
                                intentFilter,
                                secondComponentName);
                sDeviceState.testApp(MORE_IMPORTANT).devicePolicyManager()
                        .addPersistentPreferredActivity(
                                /* admin= */ null,
                                intentFilter,
                                firstComponentName);
                sDeviceState.testApp(MORE_IMPORTANT).devicePolicyManager()
                        .clearPackagePersistentPreferredActivities(
                                /* admin= */ null,
                                testAppInstance.packageName());
                sDeviceState.testApp(LESS_IMPORTANT).devicePolicyManager()
                        .clearPackagePersistentPreferredActivities(
                                /* admin= */ null,
                                testAppInstance.packageName());

                PolicyState<ComponentName> policyState =
                        PolicyEngineUtils.getComponentNamePolicyState(
                                new IntentFilterPolicyKey(
                                        PERSISTENT_PREFERRED_ACTIVITY_POLICY,
                                        intentFilter),
                                TestApis.users().instrumented().userHandle());
                assertThat(policyState).isNull();

            } finally {
                sDeviceState.testApp(MORE_IMPORTANT).devicePolicyManager()
                        .clearPackagePersistentPreferredActivities(
                                /* admin= */ null,
                                testAppInstance.packageName());
                sDeviceState.testApp(LESS_IMPORTANT).devicePolicyManager()
                        .clearPackagePersistentPreferredActivities(
                                /* admin= */ null,
                                testAppInstance.packageName());
            }
        }
    }

    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#addPersistentPreferredActivity",
            "android.app.admin.DevicePolicyManager#clearPackagePersistentPreferredActivities"})
    @MostImportantCoexistenceTest(policy = PersistentPreferredActivities.class)
    public void addPersistentPreferredActivity_bothSetThenMoreImportantResets_lessImportantApplied() {
        try (TestAppInstance testAppInstance = sTestAppWithMultipleActivities.install()) {
            try {
                ComponentName firstComponentName = new ComponentName(
                        testAppInstance.packageName(), PREFERRED_ACTIVITY.className());
                ComponentName secondComponentName = new ComponentName(
                        testAppInstance.packageName(), UNPREFERRED_ACTIVITY.className());
                IntentFilter intentFilter = new IntentFilter(TEST_ACTION);
                intentFilter.addCategory(Intent.CATEGORY_DEFAULT);
                sDeviceState.testApp(MORE_IMPORTANT).devicePolicyManager()
                        .addPersistentPreferredActivity(
                                /* admin= */ null,
                                intentFilter,
                                firstComponentName);
                sDeviceState.testApp(LESS_IMPORTANT).devicePolicyManager()
                        .addPersistentPreferredActivity(
                                /* admin= */ null,
                                intentFilter,
                                secondComponentName);
                sDeviceState.testApp(MORE_IMPORTANT).devicePolicyManager()
                        .clearPackagePersistentPreferredActivities(
                                /* admin= */ null,
                                testAppInstance.packageName());

                PolicyState<ComponentName> policyState =
                        PolicyEngineUtils.getComponentNamePolicyState(
                                new IntentFilterPolicyKey(
                                        PERSISTENT_PREFERRED_ACTIVITY_POLICY,
                                        intentFilter),
                                TestApis.users().instrumented().userHandle());
                assertThat(policyState.getCurrentResolvedPolicy()).isEqualTo(secondComponentName);

            } finally {
                sDeviceState.testApp(MORE_IMPORTANT).devicePolicyManager()
                        .clearPackagePersistentPreferredActivities(
                                /* admin= */ null,
                                testAppInstance.packageName());
                sDeviceState.testApp(LESS_IMPORTANT).devicePolicyManager()
                        .clearPackagePersistentPreferredActivities(
                                /* admin= */ null,
                                testAppInstance.packageName());
            }
        }
    }

    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#addPersistentPreferredActivity",
            "android.app.admin.DevicePolicyManager#clearPackagePersistentPreferredActivities"})
    @MostImportantCoexistenceTest(policy = PersistentPreferredActivities.class)
    public void addPersistentPreferredActivity_bothSetSameValueThenMoreImportantResets_lessImportantApplied() {
        try (TestAppInstance testAppInstance = sTestAppWithMultipleActivities.install()) {
            try {
                ComponentName componentName = new ComponentName(
                        testAppInstance.packageName(), PREFERRED_ACTIVITY.className());
                IntentFilter intentFilter = new IntentFilter(TEST_ACTION);
                intentFilter.addCategory(Intent.CATEGORY_DEFAULT);
                sDeviceState.testApp(MORE_IMPORTANT).devicePolicyManager()
                        .addPersistentPreferredActivity(
                                /* admin= */ null,
                                intentFilter,
                                componentName);
                sDeviceState.testApp(LESS_IMPORTANT).devicePolicyManager()
                        .addPersistentPreferredActivity(
                                /* admin= */ null,
                                intentFilter,
                                componentName);
                sDeviceState.testApp(MORE_IMPORTANT).devicePolicyManager()
                        .clearPackagePersistentPreferredActivities(
                                /* admin= */ null,
                                testAppInstance.packageName());

                PolicyState<ComponentName> policyState =
                        PolicyEngineUtils.getComponentNamePolicyState(
                                new IntentFilterPolicyKey(
                                        PERSISTENT_PREFERRED_ACTIVITY_POLICY,
                                        intentFilter),
                                TestApis.users().instrumented().userHandle());
                assertThat(policyState.getCurrentResolvedPolicy()).isEqualTo(componentName);

            } finally {
                sDeviceState.testApp(MORE_IMPORTANT).devicePolicyManager()
                        .clearPackagePersistentPreferredActivities(
                                /* admin= */ null,
                                testAppInstance.packageName());
                sDeviceState.testApp(LESS_IMPORTANT).devicePolicyManager()
                        .clearPackagePersistentPreferredActivities(
                                /* admin= */ null,
                                testAppInstance.packageName());
            }
        }
    }

    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#addPersistentPreferredActivity",
            "android.app.admin.DevicePolicyManager#clearPackagePersistentPreferredActivities"})
    @MostImportantCoexistenceTest(policy = PersistentPreferredActivities.class)
    public void addPersistentPreferredActivity_setByDPCAndPermission_DPCRemoved_stillEnforced() {
        try (TestAppInstance testAppInstance = sTestAppWithMultipleActivities.install()) {
            try {
                ComponentName firstComponentName = new ComponentName(
                        testAppInstance.packageName(), UNPREFERRED_ACTIVITY.className());
                ComponentName secondComponentName = new ComponentName(
                        testAppInstance.packageName(), PREFERRED_ACTIVITY.className());
                IntentFilter intentFilter = new IntentFilter(TEST_ACTION);
                intentFilter.addCategory(Intent.CATEGORY_DEFAULT);
                sDeviceState.testApp(MORE_IMPORTANT).devicePolicyManager()
                        .addPersistentPreferredActivity(
                                /* admin= */ null,
                                intentFilter,
                                firstComponentName);
                sDeviceState.testApp(LESS_IMPORTANT).devicePolicyManager()
                        .addPersistentPreferredActivity(
                                /* admin= */ null,
                                intentFilter,
                                secondComponentName);

                // Remove DPC
                sDeviceState.dpc().devicePolicyManager().clearDeviceOwnerApp(
                        sDeviceState.dpc().packageName());

                PolicyState<ComponentName> policyState =
                        PolicyEngineUtils.getComponentNamePolicyState(
                                new IntentFilterPolicyKey(
                                        PERSISTENT_PREFERRED_ACTIVITY_POLICY,
                                        intentFilter),
                                TestApis.users().instrumented().userHandle());
                assertThat(policyState.getCurrentResolvedPolicy()).isEqualTo(secondComponentName);

                TestApis.context().instrumentedContext().startActivity(
                        new Intent(TEST_ACTION).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));

                Poll.forValue("Recent activities contain preferred activity",
                                () -> recentActivitiesContainsActivity(
                                        PREFERRED_ACTIVITY.className()))
                        .toBeEqualTo(true)
                        .errorOnFail()
                        .await();
                Poll.forValue("Recent activities does not contain unpreferred activity",
                                () -> recentActivitiesContainsActivity(
                                        UNPREFERRED_ACTIVITY.className()))
                        .toBeEqualTo(false)
                        .errorOnFail()
                        .await();

            } finally {
                try {
                    sDeviceState.testApp(MORE_IMPORTANT).devicePolicyManager()
                            .clearPackagePersistentPreferredActivities(
                                    /* admin= */ null,
                                    testAppInstance.packageName());
                } catch (Exception e) {
                    // expected if app was uninstalled
                }
                try {
                    sDeviceState.testApp(LESS_IMPORTANT).devicePolicyManager()
                            .clearPackagePersistentPreferredActivities(
                                    /* admin= */ null,
                                    testAppInstance.packageName());
                } catch (Exception e) {
                    // expected if app was uninstalled
                }
            }
        }
    }

    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#addPersistentPreferredActivity",
            "android.app.admin.DevicePolicyManager#clearPackagePersistentPreferredActivities"})
    @MostImportantCoexistenceTest(policy = PersistentPreferredActivities.class)
    public void addPersistentPreferredActivity_setByPermission_appRemoved_notEnforced() {
        try (TestAppInstance testAppInstance = sTestAppWithMultipleActivities.install()) {
            try {
                ComponentName componentName = new ComponentName(
                        testAppInstance.packageName(), UNPREFERRED_ACTIVITY.className());
                IntentFilter intentFilter = new IntentFilter(TEST_ACTION);
                intentFilter.addCategory(Intent.CATEGORY_DEFAULT);
                sDeviceState.testApp(LESS_IMPORTANT).devicePolicyManager()
                        .addPersistentPreferredActivity(
                                /* admin= */ null,
                                intentFilter,
                                componentName);

                // uninstall app
                sDeviceState.testApp(LESS_IMPORTANT).uninstall();
                SystemClock.sleep(1000);

                PolicyState<ComponentName> policyState =
                        PolicyEngineUtils.getComponentNamePolicyState(
                                new IntentFilterPolicyKey(
                                        PERSISTENT_PREFERRED_ACTIVITY_POLICY,
                                        intentFilter),
                                TestApis.users().instrumented().userHandle());
                assertThat(policyState).isNull();

                TestApis.context().instrumentedContext().startActivity(
                        new Intent(TEST_ACTION).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));

                Poll.forValue("Recent activities does not contain unpreferred activity",
                                () -> recentActivitiesContainsActivity(
                                        UNPREFERRED_ACTIVITY.className()))
                        .toBeEqualTo(false)
                        .errorOnFail()
                        .await();

            } finally {
                try {
                    sDeviceState.testApp(MORE_IMPORTANT).devicePolicyManager()
                            .clearPackagePersistentPreferredActivities(
                                    /* admin= */ null,
                                    testAppInstance.packageName());
                } catch (Exception e) {
                    // expected if app was uninstalled
                }
                try {
                    sDeviceState.testApp(LESS_IMPORTANT).devicePolicyManager()
                            .clearPackagePersistentPreferredActivities(
                                    /* admin= */ null,
                                    testAppInstance.packageName());
                } catch (Exception e) {
                    // expected if app was uninstalled
                }
            }
        }
    }

    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#addPersistentPreferredActivity",
            "android.app.admin.DevicePolicyManager#clearPackagePersistentPreferredActivities"})
    @MostImportantCoexistenceTest(policy = PersistentPreferredActivities.class)
    public void addPersistentPreferredActivity_setByDPCAndPermission_appWithActivitiesRemoved_removedFromPolicyEngine() {
        try (TestAppInstance testAppInstance = sTestAppWithMultipleActivities.install()) {
            try {
                ComponentName firstComponentName = new ComponentName(
                        testAppInstance.packageName(), UNPREFERRED_ACTIVITY.className());
                ComponentName secondComponentName = new ComponentName(
                        testAppInstance.packageName(), PREFERRED_ACTIVITY.className());
                IntentFilter intentFilter = new IntentFilter(TEST_ACTION);
                intentFilter.addCategory(Intent.CATEGORY_DEFAULT);
                sDeviceState.testApp(MORE_IMPORTANT).devicePolicyManager()
                        .addPersistentPreferredActivity(
                                /* admin= */ null,
                                intentFilter,
                                firstComponentName);
                sDeviceState.testApp(LESS_IMPORTANT).devicePolicyManager()
                        .addPersistentPreferredActivity(
                                /* admin= */ null,
                                intentFilter,
                                secondComponentName);

                // Remove app
                sTestAppWithMultipleActivities.uninstall();
                SystemClock.sleep(1000);

                PolicyState<ComponentName> policyState =
                        PolicyEngineUtils.getComponentNamePolicyState(
                                new IntentFilterPolicyKey(
                                        PERSISTENT_PREFERRED_ACTIVITY_POLICY,
                                        intentFilter),
                                TestApis.users().instrumented().userHandle());
                assertThat(policyState).isNull();
            } finally {
                try {
                    sDeviceState.testApp(MORE_IMPORTANT).devicePolicyManager()
                            .clearPackagePersistentPreferredActivities(
                                    /* admin= */ null,
                                    testAppInstance.packageName());
                } catch (Exception e) {
                    // expected if app was uninstalled
                }
                try {
                    sDeviceState.testApp(LESS_IMPORTANT).devicePolicyManager()
                            .clearPackagePersistentPreferredActivities(
                                    /* admin= */ null,
                                    testAppInstance.packageName());
                } catch (Exception e) {
                    // expected if app was uninstalled
                }
            }
        }
    }

    private boolean checkRecentActivitiesContainResolver() {
        return TestApis.activities().recentActivities()
                .stream().anyMatch(ComponentReference::isResolver);
    }

    private boolean recentActivitiesContainsActivity(String activityName) {
        return TestApis.activities().recentActivities()
                .stream().anyMatch(a -> a.className().equals(activityName));
    }

}