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

import static com.android.bedstead.nene.userrestrictions.CommonUserRestrictions.DISALLOW_INSTALL_UNKNOWN_SOURCES;
import static com.android.bedstead.nene.userrestrictions.CommonUserRestrictions.DISALLOW_INSTALL_UNKNOWN_SOURCES_GLOBALLY;

import static com.google.common.truth.Truth.assertThat;

import static org.testng.Assert.assertThrows;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.EnsureDoesNotHaveUserRestriction;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.harrier.annotations.enterprise.CannotSetPolicyTest;
import com.android.bedstead.harrier.annotations.enterprise.PolicyAppliesTest;
import com.android.bedstead.harrier.annotations.enterprise.PolicyDoesNotApplyTest;
import com.android.bedstead.harrier.policies.DisallowInstallUnknownSources;
import com.android.bedstead.harrier.policies.DisallowInstallUnknownSourcesGlobally;
import com.android.bedstead.nene.TestApis;
import com.android.compatibility.common.util.ApiTest;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.runner.RunWith;


@RunWith(BedsteadJUnit4.class)
public class InstallUnknownSourcesTest {

    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    @CannotSetPolicyTest(policy = DisallowInstallUnknownSources.class,
            includeNonDeviceAdminStates = false)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_INSTALL_UNKNOWN_SOURCES")
    public void addUserRestriction_disallowInstallUnknownSources_cannotSet_throwsException() {
        assertThrows(SecurityException.class,
                () -> sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                        sDeviceState.dpc().componentName(), DISALLOW_INSTALL_UNKNOWN_SOURCES));
    }

    @PolicyAppliesTest(policy = DisallowInstallUnknownSources.class)
    @EnsureDoesNotHaveUserRestriction(DISALLOW_INSTALL_UNKNOWN_SOURCES)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_INSTALL_UNKNOWN_SOURCES")
    public void addUserRestriction_disallowInstallUnknownSources_isSet() {
        try {
            sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_INSTALL_UNKNOWN_SOURCES);

            assertThat(TestApis.devicePolicy().userRestrictions()
                    .isSet(DISALLOW_INSTALL_UNKNOWN_SOURCES))
                    .isTrue();
        } finally {
            sDeviceState.dpc().devicePolicyManager().clearUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_INSTALL_UNKNOWN_SOURCES);
        }
    }

    @PolicyDoesNotApplyTest(policy = DisallowInstallUnknownSources.class)
    @EnsureDoesNotHaveUserRestriction(DISALLOW_INSTALL_UNKNOWN_SOURCES)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_INSTALL_UNKNOWN_SOURCES")
    public void addUserRestriction_disallowInstallUnknownSources_isNotSet() {
        try {
            sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_INSTALL_UNKNOWN_SOURCES);

            assertThat(TestApis.devicePolicy().userRestrictions()
                    .isSet(DISALLOW_INSTALL_UNKNOWN_SOURCES))
                    .isFalse();
        } finally {

            sDeviceState.dpc().devicePolicyManager().clearUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_INSTALL_UNKNOWN_SOURCES);
        }
    }

    @PolicyAppliesTest(policy = DisallowInstallUnknownSourcesGlobally.class)
    @EnsureDoesNotHaveUserRestriction(DISALLOW_INSTALL_UNKNOWN_SOURCES_GLOBALLY)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_INSTALL_UNKNOWN_SOURCES_GLOBALLY")
    public void addUserRestriction_disallowInstallUnknownSourcesGlobally_isSet() {
        try {
            sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_INSTALL_UNKNOWN_SOURCES_GLOBALLY);

            assertThat(TestApis.devicePolicy().userRestrictions().isSet(
                    DISALLOW_INSTALL_UNKNOWN_SOURCES_GLOBALLY))
                    .isTrue();
        } finally {
            sDeviceState.dpc().devicePolicyManager().clearUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_INSTALL_UNKNOWN_SOURCES_GLOBALLY);
        }
    }
     // TODO(b/277701935): Add tests for addUserRestrictionGlobally
}
