/*
 * Copyright (C) 2019 The Android Open Source Project
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

package android.hdmicec.cts.common;

import static com.google.common.truth.Truth.assertThat;

import android.hdmicec.cts.BaseHdmiCecCtsTest;
import android.hdmicec.cts.CecMessage;
import android.hdmicec.cts.CecOperand;
import android.hdmicec.cts.LogicalAddress;

import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

/** HDMI CEC test to verify physical address after device reboot (Section 10.2.3) */
@RunWith(DeviceJUnit4ClassRunner.class)
public final class HdmiCecLogicalAddressTest extends BaseHdmiCecCtsTest {

    private static final LogicalAddress PLAYBACK_DEVICE = LogicalAddress.PLAYBACK_1;

    @Rule
    public RuleChain ruleChain =
        RuleChain
            .outerRule(CecRules.requiresCec(this))
            .around(CecRules.requiresLeanback(this))
            .around(CecRules.requiresPhysicalDevice(this))
            .around(hdmiCecClient);

    /**
     * Test 10.2.1-1, 10.2.3-1, 10.2.5-1
     * <p>Tests that the device broadcasts a {@code <REPORT_PHYSICAL_ADDRESS>} after a reboot and
     * that the device has taken the correct logical address.
     */
    @Test
    public void cect_RebootLogicalAddress() throws Exception {
        ITestDevice device = getDevice();
        device.reboot();
        String message = hdmiCecClient.checkExpectedOutput(CecOperand.REPORT_PHYSICAL_ADDRESS);
        int deviceType = CecMessage.getParams(message, 4, 6);
        assertThat(CecMessage.getSource(message).getDeviceType()).isEqualTo(deviceType);
    }
}
