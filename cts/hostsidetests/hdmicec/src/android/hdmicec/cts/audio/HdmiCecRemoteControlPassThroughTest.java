/*
 * Copyright 2020 The Android Open Source Project
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

package android.hdmicec.cts.audio;

import android.hdmicec.cts.BaseHdmiCecCtsTest;
import android.hdmicec.cts.HdmiCecConstants;
import android.hdmicec.cts.LogicalAddress;
import android.hdmicec.cts.RemoteControlPassthrough;

import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

@Ignore("b/162820841")
@RunWith(DeviceJUnit4ClassRunner.class)
public final class HdmiCecRemoteControlPassThroughTest extends BaseHdmiCecCtsTest {

    private static final int DUT_DEVICE_TYPE = HdmiCecConstants.CEC_DEVICE_TYPE_AUDIO_SYSTEM;

    public HdmiCecRemoteControlPassThroughTest() {
        super(DUT_DEVICE_TYPE);
    }

    @Rule
    public RuleChain ruleChain =
            RuleChain.outerRule(CecRules.requiresCec(this))
                    .around(CecRules.requiresLeanback(this))
                    .around(CecRules.requiresPhysicalDevice(this))
                    .around(
                            CecRules.requiresDeviceType(
                                    this, HdmiCecConstants.CEC_DEVICE_TYPE_AUDIO_SYSTEM))
                    .around(hdmiCecClient);

    /**
     * Test 11.2.13-1
     *
     * <p>Tests that the device responds correctly to a {@code <User Control Pressed>} message
     * followed immediately by a {@code <User Control Released>} message.
     */
    @Test
    public void cect_11_2_13_1_UserControlPressAndRelease() throws Exception {
        LogicalAddress dutLogicalAddress = getTargetLogicalAddress(getDevice(), DUT_DEVICE_TYPE);
        RemoteControlPassthrough.checkUserControlPressAndRelease(
                hdmiCecClient, getDevice(), LogicalAddress.TV, dutLogicalAddress);
    }

    /**
     * Test 11.2.13-2
     *
     * <p>Tests that the device responds correctly to a {@code <User Control Pressed>} message for
     * press and hold operations.
     */
    @Test
    public void cect_11_2_13_2_UserControlPressAndHold() throws Exception {
        LogicalAddress dutLogicalAddress = getTargetLogicalAddress(getDevice(), DUT_DEVICE_TYPE);
        RemoteControlPassthrough.checkUserControlPressAndHold(
                hdmiCecClient, getDevice(), LogicalAddress.TV, dutLogicalAddress);
    }

    /**
     * Test 11.2.13-3
     *
     * <p>Tests that the device responds correctly to a {@code <User Control Pressed>} message for
     * press and hold operations when no {@code <User Control Released>} is sent.
     */
    @Test
    public void cect_11_2_13_3_UserControlPressAndHoldWithNoRelease() throws Exception {
        LogicalAddress dutLogicalAddress = getTargetLogicalAddress(getDevice(), DUT_DEVICE_TYPE);
        RemoteControlPassthrough.checkUserControlPressAndHoldWithNoRelease(
                hdmiCecClient, getDevice(), LogicalAddress.TV, dutLogicalAddress);
    }

    /**
     * Test 11.2.13-4
     *
     * <p>Tests that the device responds correctly to a {@code <User Control Pressed>
     * [firstKeycode]} press and hold operation when interrupted by a {@code <User Control Pressed>
     * [secondKeycode]} before a {@code <User Control Released> [firstKeycode]} is sent.
     */
    @Test
    public void cect_11_2_13_4_UserControlInterruptedPressAndHoldWithNoRelease() throws Exception {
        LogicalAddress dutLogicalAddress = getTargetLogicalAddress(getDevice(), DUT_DEVICE_TYPE);
        RemoteControlPassthrough.checkUserControlInterruptedPressAndHoldWithNoRelease(
                hdmiCecClient, getDevice(), LogicalAddress.TV, dutLogicalAddress);
    }
}
