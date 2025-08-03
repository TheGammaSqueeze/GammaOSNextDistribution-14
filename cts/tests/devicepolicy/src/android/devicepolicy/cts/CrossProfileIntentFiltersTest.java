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

package android.devicepolicy.cts;

import static android.app.admin.DevicePolicyManager.FLAG_MANAGED_CAN_ACCESS_PARENT;
import static android.app.admin.DevicePolicyManager.FLAG_PARENT_CAN_ACCESS_MANAGED;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;

import static com.android.bedstead.metricsrecorder.truth.MetricQueryBuilderSubject.assertThat;
import static com.android.bedstead.nene.permissions.CommonPermissions.INTERACT_ACROSS_USERS;
import static com.android.eventlib.truth.EventLogsSubject.assertThat;
import static com.android.queryable.queries.ActivityQuery.activity;
import static com.android.queryable.queries.IntentFilterQuery.intentFilter;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.stats.devicepolicy.EventId;

import com.android.activitycontext.ActivityContext;
import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.UserType;
import com.android.bedstead.harrier.annotations.CrossUserTest;
import com.android.bedstead.harrier.annotations.EnsureHasPermission;
import com.android.bedstead.harrier.annotations.EnsureHasWorkProfile;
import com.android.bedstead.harrier.annotations.RequireRunOnWorkProfile;
import com.android.bedstead.harrier.annotations.UserPair;
import com.android.bedstead.metricsrecorder.EnterpriseMetricsRecorder;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.testapp.TestApp;
import com.android.bedstead.testapp.TestAppInstance;
import com.android.compatibility.common.util.ApiTest;
import com.android.interactive.Step;
import com.android.interactive.annotations.Interactive;
import com.android.interactive.steps.sysui.IsThereAScreenVisibleToSelectBetweenPersonalAndWorkApps;
import com.android.internal.app.IntentForwarderActivity;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.runner.RunWith;

import java.util.List;

@RunWith(BedsteadJUnit4.class)
public final class CrossProfileIntentFiltersTest {

    @ClassRule @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    // We expect that this action is only included in a single testapp
    private static final String ACTION = "com.android.testapp.UNIQUE_ACTIVITY_ACTION";

    private static final TestApp sTestApp = sDeviceState.testApps().query()
            .whereActivities().contains(
                    activity()
                            .where().exported().isTrue()
                            .where().intentFilters().contains(
                                    intentFilter().where().actions().contains(ACTION)
                            )
            ).get();

    private static final Context sContext = TestApis.context().instrumentedContext();
    private static final PackageManager sPackageManager = sContext.getPackageManager();

    @CrossUserTest({
            @UserPair(from = UserType.INITIAL_USER, to = UserType.WORK_PROFILE),
            @UserPair(from = UserType.WORK_PROFILE, to = UserType.INITIAL_USER)})
    @EnsureHasWorkProfile(dpcIsPrimary = true)
    @ApiTest(apis = "android.content.pm.PackageManager#queryIntentActivities")
    public void queryIntentActivities_doesntIncludeAppInOtherUser() {
        sTestApp.uninstallFromAllUsers();
        try (TestAppInstance testApp = sTestApp.install(sDeviceState.otherUser())) {

            assertThat(sPackageManager.queryIntentActivities(
                    new Intent(ACTION), /* flags = */ 0).size()).isEqualTo(0);
        }
    }

    @CrossUserTest({
            @UserPair(from = UserType.INITIAL_USER, to = UserType.WORK_PROFILE),
            @UserPair(from = UserType.WORK_PROFILE, to = UserType.INITIAL_USER)})
    @EnsureHasWorkProfile(dpcIsPrimary = true)
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#addCrossProfileIntentFilter")
    public void queryIntentActivities_intentFilterIsSet_includesAppInOtherUser() {
        sTestApp.uninstallFromAllUsers();
        try (TestAppInstance testApp = sTestApp.install(sDeviceState.otherUser())) {

            int flag = TestApis.users().instrumented().equals(sDeviceState.workProfile())
                    ? FLAG_PARENT_CAN_ACCESS_MANAGED : FLAG_MANAGED_CAN_ACCESS_PARENT;
            IntentFilter testIntentFilter = new IntentFilter();
            testIntentFilter.addAction(ACTION);
            sDeviceState.dpc().devicePolicyManager().addCrossProfileIntentFilter(
                    sDeviceState.dpc().componentName(),
                    testIntentFilter, flag);

            List<ResolveInfo> activities =
                    sPackageManager.queryIntentActivities(
                            new Intent(ACTION), /* flags = */ 0);
            assertThat(activities).hasSize(1);
            assertThat(activities.get(0).isCrossProfileIntentForwarderActivity()).isTrue();
        } finally {
            sDeviceState.dpc().devicePolicyManager()
                    .clearCrossProfileIntentFilters(sDeviceState.dpc().componentName());
        }
    }

    @CrossUserTest({
            @UserPair(from = UserType.INITIAL_USER, to = UserType.WORK_PROFILE),
            @UserPair(from = UserType.WORK_PROFILE, to = UserType.INITIAL_USER)})
    @EnsureHasWorkProfile(dpcIsPrimary = true)
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#addCrossProfileIntentFilter")
    public void queryIntentActivities_intentFilterIsSet_includesAppInOwnUser() {
        sTestApp.uninstallFromAllUsers();
        try (TestAppInstance testApp = sTestApp.install()) {
            int flag = TestApis.users().instrumented().equals(sDeviceState.workProfile())
                    ? FLAG_PARENT_CAN_ACCESS_MANAGED : FLAG_MANAGED_CAN_ACCESS_PARENT;
            IntentFilter testIntentFilter = new IntentFilter();
            testIntentFilter.addAction(ACTION);
            sDeviceState.dpc().devicePolicyManager().addCrossProfileIntentFilter(
                    sDeviceState.dpc().componentName(),
                    testIntentFilter, flag);

            List<ResolveInfo> activities =
                    sPackageManager.queryIntentActivities(
                            new Intent(ACTION), /* flags = */ 0);
            assertThat(activities).hasSize(1);
            assertThat(activities.get(0).isCrossProfileIntentForwarderActivity()).isFalse();
        } finally {
            sDeviceState.dpc().devicePolicyManager()
                    .clearCrossProfileIntentFilters(sDeviceState.dpc().componentName());
        }
    }

    @CrossUserTest({
            @UserPair(from = UserType.INITIAL_USER, to = UserType.WORK_PROFILE),
            @UserPair(from = UserType.WORK_PROFILE, to = UserType.INITIAL_USER)})
    @EnsureHasWorkProfile(dpcIsPrimary = true)
    @EnsureHasPermission(INTERACT_ACROSS_USERS)
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#addCrossProfileIntentFilter")
    public void startActivity_intentFilterIsSet_startsAppInOtherUser() throws InterruptedException {
        sTestApp.uninstallFromAllUsers();
        try (TestAppInstance testApp = sTestApp.install(sDeviceState.otherUser())) {
            int flag = TestApis.users().instrumented().equals(sDeviceState.workProfile())
                    ? FLAG_PARENT_CAN_ACCESS_MANAGED : FLAG_MANAGED_CAN_ACCESS_PARENT;
            IntentFilter testIntentFilter = new IntentFilter();
            testIntentFilter.addAction(ACTION);
            sDeviceState.dpc().devicePolicyManager().addCrossProfileIntentFilter(
                    sDeviceState.dpc().componentName(),
                    testIntentFilter, flag);

            ActivityContext.runWithContext(activity -> {
                Intent intent = new Intent(ACTION)
                        .putExtra(IntentForwarderActivity.EXTRA_SKIP_USER_CONFIRMATION, true);
                activity.startActivityForResult(intent, 123);
            });

            assertThat(testApp.activities().query()
                            .whereActivity().intentFilters().contains(
                                    intentFilter().where().actions().contains(ACTION)
                    ).get().events().activityStarted()).eventOccurred();
        } finally {
            sDeviceState.dpc().devicePolicyManager()
                    .clearCrossProfileIntentFilters(sDeviceState.dpc().componentName());
        }
    }

    @CrossUserTest({
            @UserPair(from = UserType.INITIAL_USER, to = UserType.WORK_PROFILE),
            @UserPair(from = UserType.WORK_PROFILE, to = UserType.INITIAL_USER)})
    @EnsureHasWorkProfile(dpcIsPrimary = true)
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#addCrossProfileIntentFilter")
    public void startActivity_intentFilterIsSet_startsAppInOwnUser() {
        sTestApp.uninstallFromAllUsers();
        try (TestAppInstance testApp = sTestApp.install()) {
            int flag = TestApis.users().instrumented().equals(sDeviceState.workProfile())
                    ? FLAG_PARENT_CAN_ACCESS_MANAGED : FLAG_MANAGED_CAN_ACCESS_PARENT;
            IntentFilter testIntentFilter = new IntentFilter();
            testIntentFilter.addAction(ACTION);
            sDeviceState.dpc().devicePolicyManager().addCrossProfileIntentFilter(
                    sDeviceState.dpc().componentName(),
                    testIntentFilter, flag);

            TestApis.context().instrumentedContext().startActivity(new Intent(ACTION).setFlags(
                    FLAG_ACTIVITY_NEW_TASK));

            assertThat(testApp.activities().query()
                    .whereActivity().intentFilters().contains(
                            intentFilter().where().actions().contains(ACTION)
                    ).get().events().activityStarted()).eventOccurred();
        } finally {
            sDeviceState.dpc().devicePolicyManager()
                    .clearCrossProfileIntentFilters(sDeviceState.dpc().componentName());
        }
    }

    @CrossUserTest({
            @UserPair(from = UserType.INITIAL_USER, to = UserType.WORK_PROFILE),
            @UserPair(from = UserType.WORK_PROFILE, to = UserType.INITIAL_USER)})
    @EnsureHasWorkProfile(dpcIsPrimary = true)
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#addCrossProfileIntentFilter")
    public void addCrossProfileIntentFilter_logsMetric() {
        try (EnterpriseMetricsRecorder metrics = EnterpriseMetricsRecorder.create();
             TestAppInstance testApp = sTestApp.install(sDeviceState.otherUser())) {
            boolean runningOnWorkProfile = TestApis.users().instrumented().equals(
                    sDeviceState.workProfile());
            int flag = runningOnWorkProfile
                    ? FLAG_PARENT_CAN_ACCESS_MANAGED : FLAG_MANAGED_CAN_ACCESS_PARENT;
            IntentFilter testIntentFilter = new IntentFilter();
            testIntentFilter.addAction(ACTION);
            sDeviceState.dpc().devicePolicyManager().addCrossProfileIntentFilter(
                    sDeviceState.dpc().componentName(),
                    testIntentFilter, flag);

            assertThat(metrics.query()
                    .whereType().isEqualTo(EventId.ADD_CROSS_PROFILE_INTENT_FILTER_VALUE)
                    .whereAdminPackageName().isEqualTo(sDeviceState.dpc().packageName())
                    .whereInteger().isEqualTo(flag)
                    .whereStrings().contains(ACTION)).wasLogged();
        } finally {
            sDeviceState.dpc().devicePolicyManager()
                    .clearCrossProfileIntentFilters(sDeviceState.dpc().componentName());
        }
    }

    @RequireRunOnWorkProfile(dpcIsPrimary = true)
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#clearCrossProfileIntentFilters")
    public void clearCrossProfileIntentFilters_clears() {
        sTestApp.uninstallFromAllUsers();
        try (TestAppInstance testApp = sTestApp.install(sDeviceState.otherUser())) {
            IntentFilter testIntentFilter = new IntentFilter();
            testIntentFilter.addAction(ACTION);
            sDeviceState.dpc().devicePolicyManager().addCrossProfileIntentFilter(
                    sDeviceState.dpc().componentName(),
                    testIntentFilter, FLAG_PARENT_CAN_ACCESS_MANAGED);

            sDeviceState.dpc().devicePolicyManager()
                    .clearCrossProfileIntentFilters(sDeviceState.dpc().componentName());

            assertThat(sPackageManager.queryIntentActivities(
                    new Intent(ACTION), /* flags = */ 0).size()).isEqualTo(0);
        } finally {
            sDeviceState.dpc().devicePolicyManager()
                    .clearCrossProfileIntentFilters(sDeviceState.dpc().componentName());
        }
    }

    @CrossUserTest({
            @UserPair(from = UserType.INITIAL_USER, to = UserType.WORK_PROFILE),
            @UserPair(from = UserType.WORK_PROFILE, to = UserType.INITIAL_USER)})
    @EnsureHasWorkProfile(dpcIsPrimary = true)
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#addCrossProfileIntentFilter")
    public void queryIntentActivities_intentFilterIsSet_includesAppInBothUsers() {
        sTestApp.uninstallFromAllUsers();
        try (TestAppInstance testApp = sTestApp.install();
             TestAppInstance otherTestApp = sTestApp.install(sDeviceState.otherUser())) {

            int flag = TestApis.users().instrumented().equals(sDeviceState.workProfile())
                    ? FLAG_PARENT_CAN_ACCESS_MANAGED : FLAG_MANAGED_CAN_ACCESS_PARENT;
            IntentFilter testIntentFilter = new IntentFilter();
            testIntentFilter.addAction(ACTION);
            sDeviceState.dpc().devicePolicyManager().addCrossProfileIntentFilter(
                    sDeviceState.dpc().componentName(),
                    testIntentFilter, flag);

            List<ResolveInfo> activities =
                    sPackageManager.queryIntentActivities(
                            new Intent(ACTION), /* flags = */ 0);
            assertThat(activities).hasSize(2);
            assertThat(activities.stream().filter(
                    ResolveInfo::isCrossProfileIntentForwarderActivity).count()).isEqualTo(1);
        } finally {
            sDeviceState.dpc().devicePolicyManager()
                    .clearCrossProfileIntentFilters(sDeviceState.dpc().componentName());
        }
    }

    @CrossUserTest({
            @UserPair(from = UserType.INITIAL_USER, to = UserType.WORK_PROFILE),
            @UserPair(from = UserType.WORK_PROFILE, to = UserType.INITIAL_USER)})
    @EnsureHasWorkProfile(dpcIsPrimary = true)
    @Interactive
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#addCrossProfileIntentFilter")
    public void startActivity_intentFilterIsSet_appIsInBothUsers_requiresDisambiguation()
            throws Exception {
        sTestApp.uninstallFromAllUsers();
        try (TestAppInstance testApp = sTestApp.install();
             TestAppInstance otherTestApp = sTestApp.install(sDeviceState.otherUser())) {

            int flag = TestApis.users().instrumented().equals(sDeviceState.workProfile())
                    ? FLAG_PARENT_CAN_ACCESS_MANAGED : FLAG_MANAGED_CAN_ACCESS_PARENT;
            IntentFilter testIntentFilter = new IntentFilter();
            testIntentFilter.addAction(ACTION);
            sDeviceState.dpc().devicePolicyManager().addCrossProfileIntentFilter(
                    sDeviceState.dpc().componentName(),
                    testIntentFilter, flag);

            TestApis.context().instrumentedContext().startActivity(new Intent(ACTION).setFlags(
                    FLAG_ACTIVITY_NEW_TASK));

            assertThat(
                    Step.execute(
                            IsThereAScreenVisibleToSelectBetweenPersonalAndWorkApps.class))
                    .isTrue();
        } finally {
            sDeviceState.dpc().devicePolicyManager()
                    .clearCrossProfileIntentFilters(sDeviceState.dpc().componentName());
        }
    }
}
