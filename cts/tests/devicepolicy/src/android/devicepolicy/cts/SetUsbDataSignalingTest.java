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

import static android.app.admin.DevicePolicyIdentifiers.USB_DATA_SIGNALING_POLICY;
import static android.app.admin.TargetUser.GLOBAL_USER_ID;
import static android.devicepolicy.cts.utils.PolicyEngineUtils.FALSE_MORE_RESTRICTIVE;

import static com.google.common.truth.Truth.assertThat;

import static org.testng.Assert.assertThrows;

import android.app.admin.NoArgsPolicyKey;
import android.app.admin.PolicyState;
import android.app.admin.PolicyUpdateResult;
import android.app.admin.flags.Flags;
import android.devicepolicy.cts.utils.PolicyEngineUtils;
import android.devicepolicy.cts.utils.PolicySetResultUtils;
import android.os.Bundle;
import android.os.UserHandle;

import com.android.bedstead.flags.annotations.RequireFlagsEnabled;
import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.enterprise.CanSetPolicyTest;
import com.android.bedstead.harrier.annotations.enterprise.CannotSetPolicyTest;
import com.android.bedstead.harrier.annotations.enterprise.PolicyAppliesTest;
import com.android.bedstead.harrier.policies.SetUsbDataSignaling;

import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.runner.RunWith;

@RunWith(BedsteadJUnit4.class)
public final class SetUsbDataSignalingTest {

    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    @Ignore("b/287191149")
    @PolicyAppliesTest(policy = SetUsbDataSignaling.class)
    public void setUsbDataSignalingEnabled_setFalse_loseConnection() {
        sDeviceState.dpc().devicePolicyManager().setUsbDataSignalingEnabled(false);

        // Expect usb connection to be killed - Factory reset to re-enable
    }

    @CannotSetPolicyTest(policy = SetUsbDataSignaling.class)
    public void setUsbDataSignalingEnabled_notPermitted_throwsException() {
        assertThrows(SecurityException.class,
                () -> sDeviceState.dpc().devicePolicyManager().setUsbDataSignalingEnabled(false));
    }

    @RequireFlagsEnabled(Flags.FLAG_POLICY_ENGINE_MIGRATION_V2_ENABLED)
    @CanSetPolicyTest(policy = SetUsbDataSignaling.class)
    public void getDevicePolicyState_setUsbDataSignalingEnabled_returnsCorrectResolutionMechanism() {
        sDeviceState.dpc().devicePolicyManager().setUsbDataSignalingEnabled(true);

        PolicyState<Boolean> policyState = PolicyEngineUtils.getBooleanPolicyState(
                new NoArgsPolicyKey(USB_DATA_SIGNALING_POLICY),
                UserHandle.ALL);

        assertThat(PolicyEngineUtils.getMostRestrictiveBooleanMechanism(policyState)
                .getMostToLeastRestrictiveValues()).isEqualTo(FALSE_MORE_RESTRICTIVE);
    }

    @RequireFlagsEnabled(Flags.FLAG_POLICY_ENGINE_MIGRATION_V2_ENABLED)
    @PolicyAppliesTest(policy = SetUsbDataSignaling.class)
    public void getDevicePolicyState_setUsbDataSignalingEnabled_returnsPolicy() {
        sDeviceState.dpc().devicePolicyManager().setUsbDataSignalingEnabled(true);

        PolicyState<Boolean> policyState = PolicyEngineUtils.getBooleanPolicyState(
                new NoArgsPolicyKey(USB_DATA_SIGNALING_POLICY),
                UserHandle.ALL);

        assertThat(policyState.getCurrentResolvedPolicy()).isTrue();
    }

    @PolicyAppliesTest(policy = SetUsbDataSignaling.class)
    @RequireFlagsEnabled(Flags.FLAG_POLICY_ENGINE_MIGRATION_V2_ENABLED)
    public void policyUpdateReceiver_setUsbDataSignaling_receivedPolicySetBroadcast() {
        sDeviceState.dpc().devicePolicyManager().setUsbDataSignalingEnabled(true);

        PolicySetResultUtils.assertPolicySetResultReceived(
                sDeviceState,
                USB_DATA_SIGNALING_POLICY,
                PolicyUpdateResult.RESULT_POLICY_SET, GLOBAL_USER_ID, new Bundle());
    }
    @Ignore("b/277071699")
    @PolicyAppliesTest(policy = SetUsbDataSignaling.class)
    @RequireFlagsEnabled(Flags.FLAG_POLICY_ENGINE_MIGRATION_V2_ENABLED)
    public void usbDataSignaling_serialisation_loadsPolicy() {
        sDeviceState.dpc().devicePolicyManager().setUsbDataSignalingEnabled(true);

        // TODO(b/277071699): Add test API to trigger reloading from disk. Currently I've tested
        //  this locally by triggering the loading in DPM#getDevicePolicyState in my local
        //  build.

        PolicyState<Boolean> policyState = PolicyEngineUtils.getBooleanPolicyState(
                new NoArgsPolicyKey(USB_DATA_SIGNALING_POLICY),
                UserHandle.ALL);

        assertThat(policyState.getCurrentResolvedPolicy()).isTrue();
    }

    @PolicyAppliesTest(policy = SetUsbDataSignaling.class)
    public void setUsbDataSignalingEnabled_setTrue_testGetter() {
        sDeviceState.dpc().devicePolicyManager().setUsbDataSignalingEnabled(true);

        assertThat(sDeviceState.dpc().devicePolicyManager().isUsbDataSignalingEnabled()).isTrue();
    }

    @Ignore("b/287191149")
    @PolicyAppliesTest(policy = SetUsbDataSignaling.class)
    public void setUsbDataSignalingEnabled_setFalse_testGetter() {
        sDeviceState.dpc().devicePolicyManager().setUsbDataSignalingEnabled(false);

        assertThat(sDeviceState.dpc().devicePolicyManager().isUsbDataSignalingEnabled()).isFalse();
    }
}
