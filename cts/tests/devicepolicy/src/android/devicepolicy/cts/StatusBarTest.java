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

import static com.android.queryable.queries.ActivityQuery.activity;

import static com.google.common.truth.Truth.assertThat;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.EnsureHasWorkProfile;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.harrier.annotations.RequireRunOnPrimaryUser;
import com.android.bedstead.harrier.annotations.enterprise.CanSetPolicyTest;
import com.android.bedstead.harrier.annotations.enterprise.CannotSetPolicyTest;
import com.android.bedstead.harrier.policies.StatusBarDisabled;
import com.android.bedstead.testapp.TestApp;
import com.android.bedstead.testapp.TestAppActivityReference;
import com.android.bedstead.testapp.TestAppInstance;
import com.android.compatibility.common.util.CddTest;
import com.android.interactive.Step;
import com.android.interactive.annotations.Interactive;
import com.android.interactive.annotations.NotFullyAutomated;
import com.android.interactive.steps.sysui.DoesTheStatusBarContainWorkIconStep;

import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(BedsteadJUnit4.class)
public final class StatusBarTest {

    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private static final TestApp sTestApp = sDeviceState.testApps().query()
            .whereActivities().contains(
                    activity()
                            .where().exported().isTrue()
                            .where().intentFilters().isEmpty()
            )
            .get();

    @Interactive
    @Test
    @RequireRunOnPrimaryUser
    @EnsureHasWorkProfile
    @CddTest(requirements = "3.9.2/C-1-4")
    @NotFullyAutomated(reason = "DoesTheStatusBarContainWorkIconStep")
    public void statusBar_personalActivityIsInForeground_doesNotIncludeWorkBadge()
            throws Exception {
        try (TestAppInstance testApp = sTestApp.install()) {
            testApp.activities().query()
                    .whereActivity().exported().isTrue()
                    .whereActivity().intentFilters().isEmpty()
                    .get().start();

            assertThat(Step.execute(DoesTheStatusBarContainWorkIconStep.class))
                    .isFalse();
        }
    }

    @Interactive
    @Test
    @RequireRunOnPrimaryUser
    @EnsureHasWorkProfile
    @CddTest(requirements = "3.9.2/C-1-4")
    @NotFullyAutomated(reason = "DoesTheStatusBarContainWorkIconStep")
    public void statusBar_workActivityIsInForeground_includesWorkBadge() throws Exception {
        try (TestAppInstance testApp = sTestApp.install(sDeviceState.workProfile())) {
            TestAppActivityReference t = testApp.activities().query()
                    .whereActivity().exported().isTrue()
                    .whereActivity().intentFilters().isEmpty()
                    .get();

            // TODO: For some reason on a freshly reset device it doesn't reliably show the work
            //  icon first time. I've tried with various delays before and after creating work
            //  profile/installing app. Triggering the activity start multiple times makes it work
            for (int i = 0; i < 10; i++) {
                t.start();
            }

            assertThat(Step.execute(DoesTheStatusBarContainWorkIconStep.class))
                    .isTrue();
        }
    }

    @Postsubmit(reason = "new test")
    @CannotSetPolicyTest(policy = StatusBarDisabled.class, includeNonDeviceAdminStates = false)
    public void setStatusBarDisabled_notAllowed_throwsException() {
        Assert.assertThrows(SecurityException.class, () -> {
            sDeviceState.dpc().devicePolicyManager().setStatusBarDisabled(
                    sDeviceState.dpc().componentName(), /* disabled= */ true);
        });
    }

    @Postsubmit(reason = "new test")
    @CannotSetPolicyTest(policy = StatusBarDisabled.class, includeNonDeviceAdminStates = false)
    public void isStatusBarDisabled_notAllowed_throwsException() {
        Assert.assertThrows(SecurityException.class, () -> {
            sDeviceState.dpc().devicePolicyManager().isStatusBarDisabled();
        });
    }

    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = StatusBarDisabled.class)
    public void setStatusBarDisabled_true_isStatusBarDisabledIsTrue() {
        try {
            sDeviceState.dpc().devicePolicyManager().setStatusBarDisabled(
                    sDeviceState.dpc().componentName(), /* disabled= */ true);

            assertThat(sDeviceState.dpc().devicePolicyManager().isStatusBarDisabled()).isTrue();
        } finally {
            sDeviceState.dpc().devicePolicyManager().setStatusBarDisabled(
                    sDeviceState.dpc().componentName(), /* disabled= */ false);
        }
    }

    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = StatusBarDisabled.class)
    public void setStatusBarDisabled_false_isStatusBarDisabledIsFalse() {
        sDeviceState.dpc().devicePolicyManager().setStatusBarDisabled(
                sDeviceState.dpc().componentName(), /* disabled= */ false);

        assertThat(sDeviceState.dpc().devicePolicyManager().isStatusBarDisabled()).isFalse();
    }
}
