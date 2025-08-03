/*
 * Copyright (C) 2024 The Android Open Source Project
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

import android.app.admin.flags.Flags;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.harrier.annotations.enterprise.CanSetPolicyTest;
import com.android.bedstead.harrier.policies.TheftDetection;
import com.android.compatibility.common.util.ApiTest;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;

@RunWith(BedsteadJUnit4.class)
public final class TheftDetectionTest {

    @ClassRule
    public static final DeviceState sDeviceState = new DeviceState();

    @Rule
    public final TestRule mCheckFlagsRule = RuleChain
            .outerRule(DeviceFlagsValueProvider.createCheckFlagsRule())
            .around(sDeviceState);

    /**
     * Test the default value for theft detection state
     */
    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = TheftDetection.class)
    @RequiresFlagsEnabled(Flags.FLAG_DEVICE_THEFT_IMPL_ENABLED)
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#isTheftDetectionTriggered")
    public void isTheftDetectionTriggered_defaultToFalse() {
        assertThat(sDeviceState.dpc().devicePolicyManager().isTheftDetectionTriggered()).isFalse();
    }
}
