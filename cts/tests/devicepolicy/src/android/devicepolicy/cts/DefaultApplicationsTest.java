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

import static com.android.bedstead.nene.userrestrictions.CommonUserRestrictions.DISALLOW_CONFIG_DEFAULT_APPS;

import static com.google.common.truth.Truth.assertThat;

import static org.testng.Assert.assertThrows;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.harrier.annotations.enterprise.CanSetPolicyTest;
import com.android.bedstead.harrier.annotations.enterprise.CannotSetPolicyTest;
import com.android.bedstead.harrier.annotations.enterprise.PolicyAppliesTest;
import com.android.bedstead.harrier.policies.DisallowConfigDefaultApps;
import com.android.bedstead.nene.TestApis;
import com.android.compatibility.common.util.ApiTest;

import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.runner.RunWith;

@RunWith(BedsteadJUnit4.class)
public final class DefaultApplicationsTest {

    @ClassRule @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    @CannotSetPolicyTest(policy = DisallowConfigDefaultApps.class,
            includeNonDeviceAdminStates = false)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_CONFIG_DEFAULT_APPS")
    public void addUserRestriction_disallowConfigDefaultApps_cannotSet_throwsException() {
        assertThrows(SecurityException.class,
                () -> sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                        sDeviceState.dpc().componentName(), DISALLOW_CONFIG_DEFAULT_APPS));
    }

    @PolicyAppliesTest(policy = DisallowConfigDefaultApps.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_CONFIG_DEFAULT_APPS")
    public void addUserRestriction_disallowConfigDefaultApps_isSet() {
        try {
            sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_CONFIG_DEFAULT_APPS);

            assertThat(TestApis.devicePolicy().userRestrictions()
                    .isSet(DISALLOW_CONFIG_DEFAULT_APPS))
                    .isTrue();
        } finally {
            sDeviceState.dpc().devicePolicyManager().clearUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_CONFIG_DEFAULT_APPS);
        }
    }

    @CanSetPolicyTest(policy = DisallowConfigDefaultApps.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_CONFIG_DEFAULT_APPS")
    public void clearUserRestriction_disallowConfigDefaultApps_isNotSet() {
        try {
            sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                    sDeviceState.dpc().componentName(),
                    DISALLOW_CONFIG_DEFAULT_APPS);

            sDeviceState.dpc().devicePolicyManager().clearUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_CONFIG_DEFAULT_APPS);

            assertThat(TestApis.devicePolicy().userRestrictions().isSet(DISALLOW_CONFIG_DEFAULT_APPS))
                    .isFalse();
        } finally {
            sDeviceState.dpc().devicePolicyManager().clearUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_CONFIG_DEFAULT_APPS);
        }
    }

    @CanSetPolicyTest(policy = DisallowConfigDefaultApps.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_CONFIG_DEFAULT_APPS")
    // TODO: Add restriction for permission based targeting U+
    // TODO: Test that this is actually global
    // TODO(b/278869421): Re-enable this test when we can test with cross-user permissions.
    @Ignore
    public void addUserRestrictionGlobally_disallowConfigDefaultApps_isSet() {
        try {
            sDeviceState.dpc().devicePolicyManager().addUserRestrictionGlobally(
                    DISALLOW_CONFIG_DEFAULT_APPS);

            assertThat(TestApis.devicePolicy().userRestrictions()
                    .isSet(DISALLOW_CONFIG_DEFAULT_APPS))
                    .isTrue();
        } finally {
            sDeviceState.dpc().devicePolicyManager().clearUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_CONFIG_DEFAULT_APPS);
        }
    }

    @CanSetPolicyTest(policy = DisallowConfigDefaultApps.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_CONFIG_DEFAULT_APPS")
    // TODO: Add restriction for permission based targeting U+
    // TODO: Test that this is actually global
    // TODO(b/278869421): Re-enable this test when we can test with cross-user permissions.
    @Ignore
    public void clearGlobalUserRestriction_disallowConfigDefaultApps_isNotSet() {
        try {
            sDeviceState.dpc().devicePolicyManager().addUserRestrictionGlobally(
                    DISALLOW_CONFIG_DEFAULT_APPS);

            sDeviceState.dpc().devicePolicyManager().clearUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_CONFIG_DEFAULT_APPS);

            assertThat(TestApis.devicePolicy().userRestrictions()
                    .isSet(DISALLOW_CONFIG_DEFAULT_APPS))
                    .isFalse();
        } finally {
            sDeviceState.dpc().devicePolicyManager().clearUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_CONFIG_DEFAULT_APPS);
        }
    }

    //TODO(b/279416312): Add interactive tests
}
