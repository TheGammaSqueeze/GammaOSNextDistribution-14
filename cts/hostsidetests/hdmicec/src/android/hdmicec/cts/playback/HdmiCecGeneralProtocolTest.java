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

package android.hdmicec.cts.playback;

import android.hdmicec.cts.BaseHdmiCecCtsTest;
import android.hdmicec.cts.CecMessage;
import android.hdmicec.cts.CecOperand;
import android.hdmicec.cts.HdmiCecConstants;
import android.hdmicec.cts.LogicalAddress;

import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

/** HDMI CEC 2.0 general protocol tests */
@RunWith(DeviceJUnit4ClassRunner.class)
public final class HdmiCecGeneralProtocolTest extends BaseHdmiCecCtsTest {

    @Rule
    public RuleChain ruleChain =
            RuleChain.outerRule(CecRules.requiresCec(this))
                    .around(CecRules.requiresLeanback(this))
                    .around(CecRules.requiresPhysicalDevice(this))
                    .around(
                            CecRules.requiresDeviceType(
                                    this, HdmiCecConstants.CEC_DEVICE_TYPE_PLAYBACK_DEVICE))
                    .around(hdmiCecClient);

    /**
     * Test HF4-2-6 (CEC 2.0)
     *
     * <p>Tests that the device ignores any additional trailing parameters in an otherwise correct
     * CEC message.
     *
     * <p>e.g. If {@code 0F:86:30:00:80(<Set Stream Path>)} is sent to the DUT, the DUT should
     * ignore the last byte of the parameter and treat it as a {@code <Set Stream Path>} message.
     */
    @Test
    public void cect_hf_ignoreAdditionalParams() throws Exception {
        setCec20();
        String dutPhysicalAddress = CecMessage.formatParams(getDumpsysPhysicalAddress(),
                HdmiCecConstants.PHYSICAL_ADDRESS_LENGTH);
        String routingChange = CecMessage.formatParams(String.valueOf(CecOperand.ROUTING_CHANGE));

        hdmiCecClient.broadcastActiveSource(LogicalAddress.TV, 0x0000);
        hdmiCecClient.sendCecMessage(LogicalAddress.TV, LogicalAddress.BROADCAST,
                CecOperand.SET_STREAM_PATH, dutPhysicalAddress + routingChange);

        String message = hdmiCecClient.checkExpectedOutput(CecOperand.ACTIVE_SOURCE);
        CecMessage.assertPhysicalAddressValid(message, getDumpsysPhysicalAddress());
    }


    /**
     * <p>Tests that the device ignores any additional trailing parameters in an otherwise correct
     * CEC message.
     *
     * <p>e.g. If {@code 0F:86:30:00:80:30:00:00:00 (<Set Stream Path>)} is sent to the DUT, the DUT
     * should ignore the bytes after the first physical address, the additional <Routing Change>
     * operand and physical addresses, and treat it as a {@code <Set Stream Path>} message.
     *
     * <p>This is not an HDMI Forum 2.0 CTS test.
     */
    @Test
    public void cectIgnoreAdditionalParamsAsMessage() throws Exception {
        setCec20();
        String tvPhysicalAddress = CecMessage.formatParams(0x0000,
                HdmiCecConstants.PHYSICAL_ADDRESS_LENGTH);
        String dutPhysicalAddress = CecMessage.formatParams(getDumpsysPhysicalAddress(),
                HdmiCecConstants.PHYSICAL_ADDRESS_LENGTH);
        String routingChange = CecMessage.formatParams(String.valueOf(CecOperand.ROUTING_CHANGE));

        hdmiCecClient.broadcastActiveSource(LogicalAddress.TV, 0x0000);
        hdmiCecClient.sendCecMessage(LogicalAddress.TV, LogicalAddress.BROADCAST,
                CecOperand.SET_STREAM_PATH, dutPhysicalAddress
                        + routingChange + dutPhysicalAddress + tvPhysicalAddress);

        String message = hdmiCecClient.checkExpectedOutput(CecOperand.ACTIVE_SOURCE);
        CecMessage.assertPhysicalAddressValid(message, getDumpsysPhysicalAddress());
    }
}
