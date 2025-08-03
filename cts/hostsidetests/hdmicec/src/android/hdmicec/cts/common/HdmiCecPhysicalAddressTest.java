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

import android.hdmicec.cts.BaseHdmiCecCtsTest;
import android.hdmicec.cts.CecMessage;
import android.hdmicec.cts.CecOperand;

import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

/** HDMI CEC test to verify physical address after device reboot (Section 10.1.2) */
@RunWith(DeviceJUnit4ClassRunner.class)
public final class HdmiCecPhysicalAddressTest extends BaseHdmiCecCtsTest {

    @Rule
    public RuleChain ruleChain =
        RuleChain
            .outerRule(CecRules.requiresCec(this))
            .around(CecRules.requiresLeanback(this))
            .around(CecRules.requiresPhysicalDevice(this))
            .around(hdmiCecClient);
    /**
     * Test 10.1.2-1
     * <p>Tests that the device broadcasts a {@code <REPORT_PHYSICAL_ADDRESS>} after a reboot and
     * that the device has taken the correct physical address.
     */
    @Test
    public void cect_10_1_2_1_RebootPhysicalAddress() throws Exception {
        ITestDevice device = getDevice();
        int physicalAddress = getDumpsysPhysicalAddress();
        device.reboot();
        String message = hdmiCecClient.checkExpectedOutput(CecOperand.REPORT_PHYSICAL_ADDRESS);
        CecMessage.assertPhysicalAddressValid(message, physicalAddress);
    }
}
