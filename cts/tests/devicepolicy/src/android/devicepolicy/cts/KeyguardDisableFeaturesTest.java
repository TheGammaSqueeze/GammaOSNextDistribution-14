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

import static com.google.common.truth.Truth.assertThat;

import android.app.admin.DevicePolicyManager;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.PolicyArgument;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.harrier.annotations.enterprise.PolicyAppliesTest;
import com.android.bedstead.harrier.annotations.enterprise.PolicyDoesNotApplyTest;
import com.android.bedstead.harrier.policies.KeyguardDisableFeatures;
import com.android.bedstead.harrier.policies.KeyguardDisableFeaturesForOrgOwnedParentProfileOwner;
import com.android.bedstead.nene.TestApis;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.runner.RunWith;

@RunWith(BedsteadJUnit4.class)
public final class KeyguardDisableFeaturesTest {
    @ClassRule @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private static final DevicePolicyManager sLocalDevicePolicyManager =
            TestApis.context().instrumentedContext().getSystemService(DevicePolicyManager.class);

    @PolicyAppliesTest(policy = {
            KeyguardDisableFeatures.class,
            KeyguardDisableFeaturesForOrgOwnedParentProfileOwner.class
    })
    @Postsubmit(reason = "new test")
    public void setKeyguardDisabledFeature_isSet(@PolicyArgument int flag) {
        try {
            sDeviceState.dpc().devicePolicyManager().setKeyguardDisabledFeatures(
                    sDeviceState.dpc().componentName(), flag);

            assertThat(TestApis.devicePolicy().getKeyguardDisabledFeatures()).isEqualTo(flag);
        } finally {
            sDeviceState.dpc().devicePolicyManager().setKeyguardDisabledFeatures(
                    sDeviceState.dpc().componentName(),
                    DevicePolicyManager.KEYGUARD_DISABLE_FEATURES_NONE
            );
        }
    }

    @PolicyDoesNotApplyTest(policy = {
            KeyguardDisableFeatures.class,
            KeyguardDisableFeaturesForOrgOwnedParentProfileOwner.class
    })
    @Postsubmit(reason = "new test")
    public void setKeyguardDisabledFeature_cannotSet_isNotSet(@PolicyArgument int flag) {
        try {
            sDeviceState.dpc().devicePolicyManager().setKeyguardDisabledFeatures(
                    sDeviceState.dpc().componentName(), flag);

            assertThat(TestApis.devicePolicy().getKeyguardDisabledFeatures()).isNotEqualTo(flag);
        } finally {
            sDeviceState.dpc().devicePolicyManager().setKeyguardDisabledFeatures(
                    sDeviceState.dpc().componentName(),
                    DevicePolicyManager.KEYGUARD_DISABLE_FEATURES_NONE
            );
        }
    }
}
