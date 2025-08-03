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

import static com.android.bedstead.nene.userrestrictions.CommonUserRestrictions.DISALLOW_SAFE_BOOT;

import static com.google.common.truth.Truth.assertThat;

import static org.testng.Assert.assertThrows;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.EnsureDoesNotHaveUserRestriction;
import com.android.bedstead.harrier.annotations.EnsureHasUserRestriction;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.harrier.annotations.enterprise.CannotSetPolicyTest;
import com.android.bedstead.harrier.annotations.enterprise.PolicyAppliesTest;
import com.android.bedstead.harrier.annotations.enterprise.PolicyDoesNotApplyTest;
import com.android.bedstead.harrier.policies.DisallowSafeBoot;
import com.android.bedstead.nene.TestApis;
import com.android.compatibility.common.util.ApiTest;
import com.android.interactive.Step;
import com.android.interactive.annotations.Interactive;
import com.android.interactive.annotations.NotFullyAutomated;
import com.android.interactive.steps.PauseStep;
import com.android.interactive.steps.sysui.OpenPowerMenuStep;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(BedsteadJUnit4.class)
public final class SafeBootTest {

    @ClassRule @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    @CannotSetPolicyTest(policy = DisallowSafeBoot.class, includeNonDeviceAdminStates = false)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_SAFE_BOOT")
    public void setUserRestriction_disallowSafeBoot_cannotSet_throwsException() {
        assertThrows(SecurityException.class,
                () -> sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                        sDeviceState.dpc().componentName(), DISALLOW_SAFE_BOOT));
    }

    @PolicyAppliesTest(policy = DisallowSafeBoot.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_SAFE_BOOT")
    public void setUserRestriction_disallowSafeBoot_isSet() {
        try {
            sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_SAFE_BOOT);

            assertThat(TestApis.devicePolicy().userRestrictions().isSet(DISALLOW_SAFE_BOOT))
                    .isTrue();
        } finally {
            sDeviceState.dpc().devicePolicyManager().clearUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_SAFE_BOOT);
        }
    }

    @PolicyDoesNotApplyTest(policy = DisallowSafeBoot.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_SAFE_BOOT")
    public void setUserRestriction_disallowSafeBoot_isNotSet() {
        try {
            sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_SAFE_BOOT);

            assertThat(TestApis.devicePolicy().userRestrictions().isSet(DISALLOW_SAFE_BOOT))
                    .isFalse();
        } finally {

            sDeviceState.dpc().devicePolicyManager().clearUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_SAFE_BOOT);
        }
    }

    @EnsureDoesNotHaveUserRestriction(DISALLOW_SAFE_BOOT)
    @Test
    @Postsubmit(reason = "new test")
    @Interactive
    @ApiTest(apis = "android.os.UserManager#DISALLOW_SAFE_BOOT")
    @NotFullyAutomated(reason = "All steps") // TODO: Automate
    public void disallowSafeBootIsNotSet_canRebootIntoSafeMode() throws Exception {
        Step.execute(OpenPowerMenuStep.class);

        Step.execute(PauseStep.class);

//        Step.execute(HoldRebootButtonFor5Seconds.class);
//
//        assertThat(Step.execute(CanYouRebootIntoSafeMode.class)).isTrue();
    }

    @EnsureHasUserRestriction(DISALLOW_SAFE_BOOT)
    @Test
    @Postsubmit(reason = "new test")
    @Interactive
    @ApiTest(apis = "android.os.UserManager#DISALLOW_SAFE_BOOT")
    @NotFullyAutomated(reason = "All steps") // TODO: Automate
    public void disallowSafeBootIsSet_canNotRebootIntoSafeMode() throws Exception {
        Step.execute(OpenPowerMenuStep.class);

        Step.execute(PauseStep.class);

//        Step.execute(HoldRebootButtonFor5Seconds.class);
//
//        assertThat(Step.execute(CanYouRebootIntoSafeMode.class)).isFalse();
    }

}
