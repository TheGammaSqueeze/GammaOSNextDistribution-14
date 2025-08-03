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

import static com.android.bedstead.nene.userrestrictions.CommonUserRestrictions.DISALLOW_CONFIG_LOCATION;
import static com.android.bedstead.nene.userrestrictions.CommonUserRestrictions.DISALLOW_SHARE_LOCATION;

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
import com.android.bedstead.harrier.policies.DisallowConfigLocation;
import com.android.bedstead.harrier.policies.DisallowShareLocation;
import com.android.bedstead.nene.TestApis;
import com.android.compatibility.common.util.ApiTest;
import com.android.interactive.Step;
import com.android.interactive.annotations.Interactive;
import com.android.interactive.steps.enterprise.settings.NavigateToPersonalLocationSettingsStep;
import com.android.interactive.steps.settings.CanYouEnableLocationSharingStep;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(BedsteadJUnit4.class)
public final class LocationTest {

    @ClassRule @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    @CannotSetPolicyTest(policy = DisallowShareLocation.class, includeNonDeviceAdminStates = false)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_SHARE_LOCATION")
    public void setUserRestriction_disallowShareLocation_cannotSet_throwsException() {
        assertThrows(SecurityException.class,
                () -> sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                        sDeviceState.dpc().componentName(), DISALLOW_SHARE_LOCATION));
    }

    @PolicyAppliesTest(policy = DisallowShareLocation.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_SHARE_LOCATION")
    public void setUserRestriction_disallowShareLocation_isSet() {
        try {
            sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_SHARE_LOCATION);

            assertThat(TestApis.devicePolicy().userRestrictions().isSet(DISALLOW_SHARE_LOCATION))
                    .isTrue();
        } finally {
            sDeviceState.dpc().devicePolicyManager().clearUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_SHARE_LOCATION);
        }
    }

    @PolicyDoesNotApplyTest(policy = DisallowShareLocation.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_SHARE_LOCATION")
    public void setUserRestriction_disallowShareLocation_isNotSet() {
        try {
            sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_SHARE_LOCATION);

            assertThat(TestApis.devicePolicy().userRestrictions().isSet(DISALLOW_SHARE_LOCATION))
                    .isFalse();
        } finally {

            sDeviceState.dpc().devicePolicyManager().clearUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_SHARE_LOCATION);
        }
    }

    @EnsureDoesNotHaveUserRestriction(DISALLOW_CONFIG_LOCATION)
    @EnsureDoesNotHaveUserRestriction(DISALLOW_SHARE_LOCATION)
    @Test
    @Postsubmit(reason = "new test")
    @Interactive
    @ApiTest(apis = "android.os.UserManager#DISALLOW_SHARE_LOCATION")
    public void disallowShareLocationIsNotSet_todo() throws Exception {
        Step.execute(NavigateToPersonalLocationSettingsStep.class);

        assertThat(Step.execute(CanYouEnableLocationSharingStep.class)).isTrue();
    }

    @EnsureDoesNotHaveUserRestriction(DISALLOW_CONFIG_LOCATION)
    @EnsureHasUserRestriction(DISALLOW_SHARE_LOCATION)
    @Test
    @Postsubmit(reason = "new test")
    @Interactive
    @ApiTest(apis = "android.os.UserManager#DISALLOW_SHARE_LOCATION")
    public void disallowShareLocationIsSet_todo() throws Exception {
        Step.execute(NavigateToPersonalLocationSettingsStep.class);

        assertThat(Step.execute(CanYouEnableLocationSharingStep.class)).isFalse();
    }

    @CannotSetPolicyTest(policy = DisallowConfigLocation.class, includeNonDeviceAdminStates = false)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_CONFIG_LOCATION")
    public void setUserRestriction_disallowConfigLocation_cannotSet_throwsException() {
        assertThrows(SecurityException.class,
                () -> sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                        sDeviceState.dpc().componentName(), DISALLOW_CONFIG_LOCATION));
    }

    @PolicyAppliesTest(policy = DisallowConfigLocation.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_CONFIG_LOCATION")
    public void setUserRestriction_disallowConfigLocation_isSet() {
        try {
            sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_CONFIG_LOCATION);

            assertThat(TestApis.devicePolicy().userRestrictions().isSet(DISALLOW_CONFIG_LOCATION))
                    .isTrue();
        } finally {
            sDeviceState.dpc().devicePolicyManager().clearUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_CONFIG_LOCATION);
        }
    }

    @PolicyDoesNotApplyTest(policy = DisallowConfigLocation.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_CONFIG_LOCATION")
    public void setUserRestriction_disallowConfigLocation_isNotSet() {
        try {
            sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_CONFIG_LOCATION);

            assertThat(TestApis.devicePolicy().userRestrictions().isSet(DISALLOW_CONFIG_LOCATION))
                    .isFalse();
        } finally {

            sDeviceState.dpc().devicePolicyManager().clearUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_CONFIG_LOCATION);
        }
    }

    @EnsureDoesNotHaveUserRestriction(DISALLOW_SHARE_LOCATION)
    @EnsureDoesNotHaveUserRestriction(DISALLOW_CONFIG_LOCATION)
    @Test
    @Postsubmit(reason = "new test")
    @Interactive
    @ApiTest(apis = "android.os.UserManager#DISALLOW_CONFIG_LOCATION")
    public void disallowConfigLocationIsNotSet_todo() throws Exception {
        Step.execute(NavigateToPersonalLocationSettingsStep.class);

        assertThat(Step.execute(CanYouEnableLocationSharingStep.class)).isTrue();
    }

    @EnsureDoesNotHaveUserRestriction(DISALLOW_SHARE_LOCATION)
    @EnsureHasUserRestriction(DISALLOW_CONFIG_LOCATION)
    @Test
    @Postsubmit(reason = "new test")
    @Interactive
    @ApiTest(apis = "android.os.UserManager#DISALLOW_CONFIG_LOCATION")
    public void disallowConfigLocationIsSet_todo() throws Exception {
        Step.execute(NavigateToPersonalLocationSettingsStep.class);

        assertThat(Step.execute(CanYouEnableLocationSharingStep.class)).isFalse();
    }
}
