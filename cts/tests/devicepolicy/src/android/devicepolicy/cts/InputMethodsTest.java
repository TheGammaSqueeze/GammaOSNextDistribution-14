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

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.harrier.annotations.enterprise.PolicyAppliesTest;
import com.android.bedstead.harrier.policies.SetDefaultInputMethod;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.utils.Poll;
import com.android.compatibility.common.util.ApiTest;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(BedsteadJUnit4.class)
public final class InputMethodsTest {

    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private static final String DEFAULT_INPUT_METHOD = "default_input_method";
    private static final String SETTING_VALUE = "com.test.1";
    private static final String SETTING_VALUE_TWO = "com.test.2";

    @ApiTest(apis = "android.app.admin.DevicePolicyManager#isCurrentInputMethodSetByOwner")
    @Postsubmit(reason = "new test")
    @PolicyAppliesTest(policy = SetDefaultInputMethod.class)
    public void isCurrentInputMethodSetByOwner_isSetByOwner_returnsTrue() {
        try {
            TestApis.settings().secure().putString(
                    TestApis.context().instrumentedContext().getContentResolver(),
                    DEFAULT_INPUT_METHOD, SETTING_VALUE);

            sDeviceState.dpc().devicePolicyManager().setSecureSetting(
                    sDeviceState.dpc().componentName(), DEFAULT_INPUT_METHOD, SETTING_VALUE_TWO);

            Poll.forValue("isCurrentInputMethodSetByOwner", () -> TestApis.devicePolicy().isCurrentInputMethodSetByOwner())
                    .toBeEqualTo(true)
                    .errorOnFail().
                    await();
        } finally {
            TestApis.settings().secure().reset(sDeviceState.dpc().user());
        }
    }

    @ApiTest(apis = "android.app.admin.DevicePolicyManager#isCurrentInputMethodSetByOwner")
    @Postsubmit(reason = "new test")
    @Test
    @PolicyAppliesTest(policy = SetDefaultInputMethod.class)
    public void isCurrentInputMethodSetByOwner_isNotSetByOwner_returnsFalse() {
        try {
            sDeviceState.dpc().devicePolicyManager().setSecureSetting(
                    sDeviceState.dpc().componentName(), DEFAULT_INPUT_METHOD, SETTING_VALUE);

            TestApis.settings().secure().putString(
                    TestApis.context().instrumentedContext().getContentResolver(),
                    DEFAULT_INPUT_METHOD, SETTING_VALUE_TWO);

            Poll.forValue("isCurrentInputMethodSetByOwner", () -> TestApis.devicePolicy().isCurrentInputMethodSetByOwner())
                    .toBeEqualTo(false)
                    .errorOnFail().
                    await();
        } finally {
            TestApis.settings().secure().reset();
        }
    }
}
