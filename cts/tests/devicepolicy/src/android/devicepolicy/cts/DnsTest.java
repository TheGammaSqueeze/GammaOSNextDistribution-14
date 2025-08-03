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

import static com.android.bedstead.nene.userrestrictions.CommonUserRestrictions.DISALLOW_CONFIG_PRIVATE_DNS;

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
import com.android.bedstead.harrier.policies.DisallowConfigPrivateDns;
import com.android.bedstead.nene.TestApis;
import com.android.compatibility.common.util.ApiTest;
import com.android.interactive.Step;
import com.android.interactive.annotations.Interactive;
import com.android.interactive.steps.enterprise.settings.NavigateToPersonalNetworkSettingsStep;
import com.android.interactive.steps.settings.CanYouChangePrivateDNSConfigStep;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(BedsteadJUnit4.class)
public final class DnsTest {

    @ClassRule @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    @CannotSetPolicyTest(policy = DisallowConfigPrivateDns.class, includeNonDeviceAdminStates = false)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_CONFIG_PRIVATE_DNS")
    public void setUserRestriction_disallowConfigPrivateDns_cannotSet_throwsException() {
        assertThrows(SecurityException.class,
                () -> sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                        sDeviceState.dpc().componentName(), DISALLOW_CONFIG_PRIVATE_DNS));
    }

    @PolicyAppliesTest(policy = DisallowConfigPrivateDns.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_CONFIG_PRIVATE_DNS")
    public void setUserRestriction_disallowConfigPrivateDns_isSet() {
        try {
            sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_CONFIG_PRIVATE_DNS);

            assertThat(TestApis.devicePolicy().userRestrictions().isSet(DISALLOW_CONFIG_PRIVATE_DNS))
                    .isTrue();
        } finally {
            sDeviceState.dpc().devicePolicyManager().clearUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_CONFIG_PRIVATE_DNS);
        }
    }

    @PolicyDoesNotApplyTest(policy = DisallowConfigPrivateDns.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_CONFIG_PRIVATE_DNS")
    public void setUserRestriction_disallowConfigPrivateDns_isNotSet() {
        try {
            sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_CONFIG_PRIVATE_DNS);

            assertThat(TestApis.devicePolicy().userRestrictions().isSet(DISALLOW_CONFIG_PRIVATE_DNS))
                    .isFalse();
        } finally {

            sDeviceState.dpc().devicePolicyManager().clearUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_CONFIG_PRIVATE_DNS);
        }
    }

    @EnsureDoesNotHaveUserRestriction(DISALLOW_CONFIG_PRIVATE_DNS)
    @Test
    @Postsubmit(reason = "new test")
    @Interactive
    @ApiTest(apis = "android.os.UserManager#DISALLOW_CONFIG_PRIVATE_DNS")
    public void disallowConfigPrivateDnsIsNotSet_canChangePrivateDnsConfig() throws Exception {
        Step.execute(NavigateToPersonalNetworkSettingsStep.class);

        assertThat(Step.execute(CanYouChangePrivateDNSConfigStep.class)).isTrue();
    }

    @EnsureHasUserRestriction(DISALLOW_CONFIG_PRIVATE_DNS)
    @Test
    @Postsubmit(reason = "new test")
    @Interactive
    @ApiTest(apis = "android.os.UserManager#DISALLOW_CONFIG_PRIVATE_DNS")
    public void disallowConfigPrivateDnsIsSet_canNotChangePrivateDnsConfig() throws Exception {
        Step.execute(NavigateToPersonalNetworkSettingsStep.class);

        assertThat(Step.execute(CanYouChangePrivateDNSConfigStep.class)).isFalse();

        // TODO: Figure out expectations around policy transparency - this doesn't show the label
    }
}
