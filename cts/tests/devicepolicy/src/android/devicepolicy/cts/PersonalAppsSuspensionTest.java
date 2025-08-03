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

import static android.Manifest.permission.INTERACT_ACROSS_USERS_FULL;
import static android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;

import static com.android.queryable.queries.ActivityQuery.activity;
import static com.android.queryable.queries.IntentFilterQuery.intentFilter;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.content.ComponentName;
import android.content.Intent;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.harrier.annotations.RequireNotHeadlessSystemUserMode;
import com.android.bedstead.harrier.annotations.enterprise.CannotSetPolicyTest;
import com.android.bedstead.harrier.annotations.enterprise.PolicyAppliesTest;
import com.android.bedstead.harrier.annotations.enterprise.PolicyDoesNotApplyTest;
import com.android.bedstead.harrier.policies.SuspendPersonalApps;
import com.android.bedstead.harrier.policies.SuspendPersonalAppsWithCloneProfileDisabled;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.permissions.PermissionContext;
import com.android.bedstead.nene.roles.RoleContext;
import com.android.bedstead.testapp.TestApp;
import com.android.bedstead.testapp.TestAppActivityReference;
import com.android.bedstead.testapp.TestAppInstance;
import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.BlockingBroadcastReceiver;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.runner.RunWith;

@RunWith(BedsteadJUnit4.class)
public final class PersonalAppsSuspensionTest {

    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private static final String SMS_ROLE = "android.app.role.SMS";
    private static final String ACTION_MY_PACKAGE_SUSPENDED =
            "android.intent.action.MY_PACKAGE_SUSPENDED";
    private static final TestApp sSmsTestApp =
            sDeviceState.testApps().query().whereActivities().contains(
                    activity().where().intentFilters().contains(
                            intentFilter().where().actions().contains("android.intent.action.SEND")
                    )).get();
    private static final TestApp sTestApp = sDeviceState.testApps().any();

    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#setPersonalAppsSuspended")
    @PolicyAppliesTest(policy = SuspendPersonalApps.class)
    public void setPersonalAppsSuspended_sendsPackageSuspendedBroadcast() {
        try (BlockingBroadcastReceiver broadcastReceiver =
                     sDeviceState.registerBroadcastReceiver(ACTION_MY_PACKAGE_SUSPENDED)) {
            sDeviceState.dpc().devicePolicyManager().setPersonalAppsSuspended(
                    sDeviceState.dpc().componentName(), /* suspended= */ true);

            broadcastReceiver.awaitForBroadcastOrFail();
        } finally {
            sDeviceState.dpc().devicePolicyManager().setPersonalAppsSuspended(
                    sDeviceState.dpc().componentName(), /* suspended= */ false);
        }
    }

    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#setPersonalAppsSuspended")
    @PolicyAppliesTest(policy = SuspendPersonalApps.class)
    public void setPersonalAppsSuspended_cannotStartActivity() {
        try (TestAppInstance testApp = sTestApp.install()) {
            sDeviceState.dpc().devicePolicyManager().setPersonalAppsSuspended(
                    sDeviceState.dpc().componentName(), /* suspended= */ true);
            TestAppActivityReference activity = testApp.activities().any();

            startActivity(activity.component().componentName());

            assertThat(TestApis.activities().recentActivities().stream().noneMatch(
                    a -> a.componentName().equals(activity.component().componentName())))
                    .isTrue();
        } finally {
            sDeviceState.dpc().devicePolicyManager().setPersonalAppsSuspended(
                    sDeviceState.dpc().componentName(), /* suspended= */ false);
        }
    }

    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#setPersonalAppsSuspended")
    @PolicyDoesNotApplyTest(policy = SuspendPersonalApps.class)
    public void setPersonalAppsSuspended_policyDoesNotApply_canStartActivity() {
        try (TestAppInstance testApp = sTestApp.install()) {
            sDeviceState.dpc().devicePolicyManager().setPersonalAppsSuspended(
                    sDeviceState.dpc().componentName(), /* suspended= */ true);
            TestAppActivityReference activity = testApp.activities().any();

            startActivity(activity.component().componentName());

            assertThat(TestApis.activities().recentActivities().stream().anyMatch(
                    a -> a.componentName().equals(activity.component().componentName())))
                    .isTrue();
        } finally {
            sDeviceState.dpc().devicePolicyManager().setPersonalAppsSuspended(
                    sDeviceState.dpc().componentName(), /* suspended= */ false);
        }
    }

    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#setPersonalAppsSuspended")
    @RequireNotHeadlessSystemUserMode(
            reason = "b/319449037 - Headless bug fix needs to be fully ramped")
    @PolicyAppliesTest(policy = SuspendPersonalAppsWithCloneProfileDisabled.class)
    public void setPersonalAppsSuspended_smsApp_canStartActivity() {
        try (TestAppInstance testApp = sSmsTestApp.install();
             RoleContext r = TestApis.packages().find(testApp.packageName()).setAsRoleHolder(
                     SMS_ROLE)) {
            sDeviceState.dpc().devicePolicyManager().setPersonalAppsSuspended(
                    sDeviceState.dpc().componentName(), /* suspended= */ true);
            TestAppActivityReference smsActivity =
                    testApp.activities().query().whereActivity().activityClass().simpleName()
                            .isEqualTo("SmsSenderActivity").get();

            startActivity(smsActivity.component().componentName());

            assertThat(TestApis.activities().recentActivities().stream().anyMatch(
                    a -> a.componentName().equals(smsActivity.component().componentName())))
                    .isTrue();
        } finally {
            sDeviceState.dpc().devicePolicyManager().setPersonalAppsSuspended(
                    sDeviceState.dpc().componentName(), /* suspended= */ false);
        }
    }

    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#setPersonalAppsSuspended")
    @CannotSetPolicyTest(policy = SuspendPersonalApps.class, includeNonDeviceAdminStates = false)
    public void setPersonalAppsSuspended_cannotSetPolicy_throwsException() {
        assertThrows(SecurityException.class, () ->
                sDeviceState.dpc().devicePolicyManager().setPersonalAppsSuspended(
                        sDeviceState.dpc().componentName(), /* suspended= */ false)
        );
    }

    private void startActivity(ComponentName componentName) {
        try (PermissionContext p =
                     TestApis.permissions().withPermission(INTERACT_ACROSS_USERS_FULL)) {
            TestApis.context().instrumentedContext().startActivityAsUser(
                    new Intent().setFlags(
                                    FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TASK)
                            .setComponent(componentName),
                    TestApis.users().instrumented().userHandle());
        }
    }
}
