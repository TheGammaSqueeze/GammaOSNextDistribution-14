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

import static com.android.queryable.queries.ActivityQuery.activity;
import static com.android.queryable.queries.IntentFilterQuery.intentFilter;

import android.content.ComponentName;
import android.content.Intent;
import android.content.IntentFilter;
import android.stats.devicepolicy.EventId;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.Postsubmit;
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
import org.junit.Rule;
import org.junit.runner.RunWith;

@RunWith(BedsteadJUnit4.class)
public final class PersistentIntentResolvingTest {
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
                                () -> checkRecentActivitiesContainActivity(
                                        PREFERRED_ACTIVITY.className()))
                        .toBeEqualTo(true)
                        .errorOnFail()
                        .await();
                Poll.forValue("Recent activities does not contain unpreferred activity",
                                () -> checkRecentActivitiesContainActivity(
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
                                        sDeviceState.dpc().componentName().getPackageName())
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

    private boolean checkRecentActivitiesContainResolver() {
        return TestApis.activities().recentActivities()
                .stream().anyMatch(ComponentReference::isResolver);
    }

    private boolean checkRecentActivitiesContainActivity(String activityName) {
        return TestApis.activities().recentActivities()
                .stream().anyMatch(a -> a.componentName().getClassName()
                        .equals(activityName));
    }

}
