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

import static com.android.bedstead.nene.userrestrictions.CommonUserRestrictions.DISALLOW_FACTORY_RESET;

import static com.google.common.truth.Truth.assertThat;

import static org.testng.Assert.assertThrows;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.harrier.annotations.enterprise.CanSetPolicyTest;
import com.android.bedstead.harrier.annotations.enterprise.CannotSetPolicyTest;
import com.android.bedstead.harrier.annotations.enterprise.PolicyAppliesTest;
import com.android.bedstead.harrier.policies.DisallowFactoryReset;
import com.android.bedstead.nene.TestApis;
import com.android.compatibility.common.util.ApiTest;

import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.runner.RunWith;

@RunWith(BedsteadJUnit4.class)
public final class FactoryResetTest {

    @ClassRule @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    @CannotSetPolicyTest(policy = DisallowFactoryReset.class, includeNonDeviceAdminStates = false)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_FACTORY_RESET")
    public void addUserRestriction_disallowFactoryReset_cannotSet_throwsException() {
        assertThrows(SecurityException.class,
                () -> sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                        sDeviceState.dpc().componentName(), DISALLOW_FACTORY_RESET));
    }

    @PolicyAppliesTest(policy = DisallowFactoryReset.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_FACTORY_RESET")
    public void addUserRestriction_disallowFactoryReset_isSet() {
        try {
            sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_FACTORY_RESET);

            assertThat(TestApis.devicePolicy().userRestrictions().isSet(DISALLOW_FACTORY_RESET))
                    .isTrue();
        } finally {
            sDeviceState.dpc().devicePolicyManager().clearUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_FACTORY_RESET);
        }
    }

    @CanSetPolicyTest(policy = DisallowFactoryReset.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_FACTORY_RESET")
    public void clearUserRestriction_disallowFactoryReset_isNotSet() {
        try {
            sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                    sDeviceState.dpc().componentName(),
                    DISALLOW_FACTORY_RESET);

            sDeviceState.dpc().devicePolicyManager().clearUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_FACTORY_RESET);

            assertThat(TestApis.devicePolicy().userRestrictions().isSet(DISALLOW_FACTORY_RESET))
                    .isFalse();
        } finally {
            sDeviceState.dpc().devicePolicyManager().clearUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_FACTORY_RESET);
        }
    }

    @CanSetPolicyTest(policy = DisallowFactoryReset.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_FACTORY_RESET")
    // TODO: Add restriction for permission based targeting U+
    // TODO: Test that this is actually global
    // TODO(b/278869421): Re-enable this test when we can test with cross-user permissions.
    @Ignore
    public void addUserRestrictionGlobally_disallowFactoryReset_isSet() {
        try {
            sDeviceState.dpc().devicePolicyManager().addUserRestrictionGlobally(
                    DISALLOW_FACTORY_RESET);

            assertThat(TestApis.devicePolicy().userRestrictions().isSet(DISALLOW_FACTORY_RESET))
                    .isTrue();
        } finally {
            sDeviceState.dpc().devicePolicyManager().clearUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_FACTORY_RESET);
        }
    }

    @CanSetPolicyTest(policy = DisallowFactoryReset.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_FACTORY_RESET")
    // TODO: Add restriction for permission based targeting U+
    // TODO: Test that this is actually global
    // TODO(b/278869421): Re-enable this test when we can test with cross-user permissions.
    @Ignore
    public void clearGlobalUserRestriction_disallowFactoryReset_isNotSet() {
        try {
            sDeviceState.dpc().devicePolicyManager().addUserRestrictionGlobally(
                    DISALLOW_FACTORY_RESET);

            sDeviceState.dpc().devicePolicyManager().clearUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_FACTORY_RESET);

            assertThat(TestApis.devicePolicy().userRestrictions().isSet(DISALLOW_FACTORY_RESET))
                    .isFalse();
        } finally {
            sDeviceState.dpc().devicePolicyManager().clearUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_FACTORY_RESET);
        }
    }

// TODO(b/279417218): Add interactive tests.

}
