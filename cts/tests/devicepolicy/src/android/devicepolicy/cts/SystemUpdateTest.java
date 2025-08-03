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

import static com.google.common.truth.Truth.assertThat;

import static org.testng.Assert.assertThrows;

import android.app.admin.DevicePolicyManager;
import android.app.admin.SystemUpdatePolicy;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.harrier.annotations.enterprise.CanSetPolicyTest;
import com.android.bedstead.harrier.annotations.enterprise.CannotSetPolicyTest;
import com.android.bedstead.harrier.annotations.enterprise.PolicyAppliesTest;
import com.android.bedstead.harrier.annotations.enterprise.PolicyDoesNotApplyTest;
import com.android.bedstead.harrier.policies.SystemUpdate;
import com.android.bedstead.nene.TestApis;
import com.android.compatibility.common.util.ApiTest;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.runner.RunWith;

@RunWith(BedsteadJUnit4.class)
public final class SystemUpdateTest {

    @ClassRule @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private static final SystemUpdatePolicy SYSTEM_UPDATE_POLICY =
            SystemUpdatePolicy.createWindowedInstallPolicy(100, 200);

    private static final DevicePolicyManager sLocalDevicePolicyManager =
            TestApis.context().instrumentedContext().getSystemService(DevicePolicyManager.class);

    @CannotSetPolicyTest(policy = SystemUpdate.class, includeNonDeviceAdminStates = false)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#setSystemUpdatePolicy"})
    public void setSystemUpdatePolicy_notPermitted_throwsException() {
        assertThrows(SecurityException.class,
                () -> sDeviceState.dpc().devicePolicyManager()
                        .setSystemUpdatePolicy(
                                sDeviceState.dpc().componentName(), SYSTEM_UPDATE_POLICY)
        );
    }

    @PolicyAppliesTest(policy = SystemUpdate.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#setSystemUpdatePolicy",
            "android.app.admin.DevicePolicyManager#getSystemUpdatePolicy"})
    public void setSystemUpdatePolicy_policyIsSet() {
        SystemUpdatePolicy originalPolicy = sDeviceState.dpc()
                .devicePolicyManager().getSystemUpdatePolicy();
        try {
            sDeviceState.dpc().devicePolicyManager()
                    .setSystemUpdatePolicy(
                            sDeviceState.dpc().componentName(), SYSTEM_UPDATE_POLICY);

            // String comparison as SystemUpdatePolicy doesn't have a good equals method
            assertThat(sLocalDevicePolicyManager.getSystemUpdatePolicy().toString())
                    .isEqualTo(SYSTEM_UPDATE_POLICY.toString());
        } finally {
            sDeviceState.dpc().devicePolicyManager().setSystemUpdatePolicy(
                    sDeviceState.dpc().componentName(), originalPolicy);
        }
    }

    @PolicyDoesNotApplyTest(policy = SystemUpdate.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#setSystemUpdatePolicy",
            "android.app.admin.DevicePolicyManager#getSystemUpdatePolicy"})
    public void setSystemUpdatePolicy_doesNotApply_policyIsNotSet() {
        SystemUpdatePolicy originalPolicy =
                sDeviceState.dpc().devicePolicyManager().getSystemUpdatePolicy();
        try {
            sDeviceState.dpc().devicePolicyManager().setSystemUpdatePolicy(
                    sDeviceState.dpc().componentName(), SYSTEM_UPDATE_POLICY);

            // String comparison as SystemUpdatePolicy doesn't have a good equals method
            assertThat(sLocalDevicePolicyManager.getSystemUpdatePolicy().toString())
                    .isNotEqualTo(SYSTEM_UPDATE_POLICY.toString());
        } finally {
            sDeviceState.dpc().devicePolicyManager().setSystemUpdatePolicy(
                    sDeviceState.dpc().componentName(), originalPolicy);
        }
    }

    // TODO: Add test of installSystemUpdate
}
