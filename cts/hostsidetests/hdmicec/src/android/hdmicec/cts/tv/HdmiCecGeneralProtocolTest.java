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

package android.hdmicec.cts.tv;

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
                    .around(CecRules.requiresDeviceType(this, HdmiCecConstants.CEC_DEVICE_TYPE_TV))
                    .around(hdmiCecClient);

    /**
     * Test HF4-2-5 (CEC 2.0)
     *
     * <p>Tests that the device ignores any additional trailing parameters in an otherwise correct
     * CEC message.
     *
     * <p>e.g. If {@code 4F:82:20:00:80:20:00:00:00 (<Active Source>)} is sent to the DUT, the DUT
     * should ignore the last byte of the parameter and treat it as a {@code <Active Source>}
     * message.
     */
    @Test
    public void cect_hf_ignoreAdditionalParams() throws Exception {
        setCec20();

        int clientPhysicalAddress = hdmiCecClient.getPhysicalAddress();
        int playbackPhysicalAddress = 0x2000;
        if (playbackPhysicalAddress == clientPhysicalAddress) {
            playbackPhysicalAddress = 0x1000;
        }

        String parameterPlaybackPhysicalAddress = CecMessage.formatParams(playbackPhysicalAddress,
                HdmiCecConstants.PHYSICAL_ADDRESS_LENGTH);
        String routingChange = CecMessage.formatParams(String.valueOf(CecOperand.ROUTING_CHANGE));

        hdmiCecClient.broadcastReportPhysicalAddress(
                LogicalAddress.RECORDER_1, clientPhysicalAddress);
        hdmiCecClient.broadcastActiveSource(LogicalAddress.RECORDER_1, clientPhysicalAddress);
        waitForCondition(() ->
                        getDumpsysActiveSourceLogicalAddress().equals(LogicalAddress.RECORDER_1),
                "Device has not registered expected logical address as active source.");
        hdmiCecClient.broadcastReportPhysicalAddress(LogicalAddress.PLAYBACK_1,
                playbackPhysicalAddress);
        hdmiCecClient.sendCecMessage(LogicalAddress.PLAYBACK_1, LogicalAddress.BROADCAST,
                CecOperand.ACTIVE_SOURCE, parameterPlaybackPhysicalAddress + routingChange);
        waitForCondition(() ->
                        getDumpsysActiveSourceLogicalAddress().equals(LogicalAddress.PLAYBACK_1),
                "Device has not registered expected logical address as active source.");
    }

    /**
     * <p>Tests that the device ignores any additional trailing parameters in an otherwise correct
     * CEC message.
     *
     * <p>e.g. If {@code 4F:82:20:00:80:20:00:00:00 (<Active Source>)} is sent to the DUT, the DUT
     * should ignore the bytes after the first physical address, the additional <Routing Change>
     * operand and physical addresses, and treat it as a {@code <Active Source>} message.
     *
     * <p>This is not an HDMI Forum 2.0 CTS test.
     */
    @Test
    public void cectIgnoreAdditionalParamsAsMessage() throws Exception {
        setCec20();

        int clientPhysicalAddress = hdmiCecClient.getPhysicalAddress();
        int playbackPhysicalAddress = 0x2000;
        if (playbackPhysicalAddress == clientPhysicalAddress) {
            playbackPhysicalAddress = 0x1000;
        }
        String parameterClientPhysicalAddress = CecMessage.formatParams(clientPhysicalAddress,
                HdmiCecConstants.PHYSICAL_ADDRESS_LENGTH);
        String parameterPlaybackPhysicalAddress = CecMessage.formatParams(playbackPhysicalAddress,
                HdmiCecConstants.PHYSICAL_ADDRESS_LENGTH);
        String routingChange = CecMessage.formatParams(String.valueOf(CecOperand.ROUTING_CHANGE));

        hdmiCecClient.broadcastReportPhysicalAddress(
                LogicalAddress.RECORDER_1, clientPhysicalAddress);
        hdmiCecClient.broadcastActiveSource(LogicalAddress.RECORDER_1, clientPhysicalAddress);
        waitForCondition(() ->
                        getDumpsysActiveSourceLogicalAddress().equals(LogicalAddress.RECORDER_1),
                "Device has not registered expected logical address as active source.");
        hdmiCecClient.broadcastReportPhysicalAddress(LogicalAddress.PLAYBACK_1,
                playbackPhysicalAddress);
        hdmiCecClient.sendCecMessage(LogicalAddress.PLAYBACK_1, LogicalAddress.BROADCAST,
                CecOperand.ACTIVE_SOURCE, parameterPlaybackPhysicalAddress
                        + routingChange + parameterPlaybackPhysicalAddress
                        + parameterClientPhysicalAddress);
        waitForCondition(() ->
                        getDumpsysActiveSourceLogicalAddress().equals(LogicalAddress.PLAYBACK_1),
                "Device has not registered expected logical address as active source.");
    }
}
