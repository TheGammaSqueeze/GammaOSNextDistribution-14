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
import android.hdmicec.cts.CecMessage;
import android.hdmicec.cts.CecOperand;
import android.hdmicec.cts.HdmiCecConstants;
import android.hdmicec.cts.LogicalAddress;
import android.hdmicec.cts.error.CecClientWrapperException;
import android.hdmicec.cts.error.ErrorCodes;

import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

/** HDMI CEC test to test audio return channel control (Section 11.2.17) */
@Ignore("b/162820841")
@RunWith(DeviceJUnit4ClassRunner.class)
public final class HdmiCecAudioReturnChannelControlTest extends BaseHdmiCecCtsTest {

    private static final LogicalAddress AUDIO_DEVICE = LogicalAddress.AUDIO_SYSTEM;

    public HdmiCecAudioReturnChannelControlTest() {
        super(HdmiCecConstants.CEC_DEVICE_TYPE_AUDIO_SYSTEM);
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

    private void checkArcIsInitiated() throws CecClientWrapperException {
        try {
            hdmiCecClient.sendCecMessage(LogicalAddress.TV, AUDIO_DEVICE,
                    CecOperand.REQUEST_ARC_INITIATION);
            hdmiCecClient.checkExpectedOutput(LogicalAddress.TV, CecOperand.INITIATE_ARC);
        } catch (CecClientWrapperException e) {
            if (e.getErrorCode() != ErrorCodes.CecMessageNotFound) {
                throw e;
            }
        }
    }

    /**
     * Test 11.2.17-1
     * Tests that the device sends a directly addressed <Initiate ARC> message
     * when it wants to initiate ARC.
     */
    @Test
    public void cect_11_2_17_1_InitiateArc() throws Exception {
        hdmiCecClient.sendCecMessage(LogicalAddress.TV, LogicalAddress.BROADCAST,
                CecOperand.REPORT_PHYSICAL_ADDRESS,
                CecMessage.formatParams(HdmiCecConstants.TV_PHYSICAL_ADDRESS,
                HdmiCecConstants.PHYSICAL_ADDRESS_LENGTH));
        getDevice().executeShellCommand("reboot");
        getDevice().waitForBootComplete(HdmiCecConstants.REBOOT_TIMEOUT);
        hdmiCecClient.checkExpectedOutput(LogicalAddress.TV, CecOperand.INITIATE_ARC);
    }

    /**
     * Test 11.2.17-2
     * Tests that the device sends a directly addressed <Terminate ARC> message
     * when it wants to terminate ARC.
     */
    @Test
    public void cect_11_2_17_2_TerminateArc() throws Exception {
        checkArcIsInitiated();
        hdmiCecClient.sendCecMessage(LogicalAddress.TV, LogicalAddress.BROADCAST,
                CecOperand.REPORT_PHYSICAL_ADDRESS,
                CecMessage.formatParams(HdmiCecConstants.TV_PHYSICAL_ADDRESS,
                        HdmiCecConstants.PHYSICAL_ADDRESS_LENGTH));
        sendDeviceToSleep();
        try {
            hdmiCecClient.checkExpectedOutput(LogicalAddress.TV, CecOperand.TERMINATE_ARC);
        } finally {
            wakeUpDevice();
        }
    }

    /**
     * Test 11.2.17-3
     * Tests that the device sends a directly addressed <Initiate ARC>
     * message when it is requested to initiate ARC.
     */
    @Test
    public void cect_11_2_17_3_RequestToInitiateArc() throws Exception {
        hdmiCecClient.sendCecMessage(LogicalAddress.TV, LogicalAddress.BROADCAST,
                CecOperand.REPORT_PHYSICAL_ADDRESS,
                CecMessage.formatParams(HdmiCecConstants.TV_PHYSICAL_ADDRESS,
                HdmiCecConstants.PHYSICAL_ADDRESS_LENGTH));
        hdmiCecClient.sendCecMessage(LogicalAddress.TV, AUDIO_DEVICE,
                CecOperand.REQUEST_ARC_INITIATION);
        hdmiCecClient.checkExpectedOutput(LogicalAddress.TV, CecOperand.INITIATE_ARC);
    }

    /**
     * Test 11.2.17-4
     * Tests that the device sends a directly addressed <Terminate ARC> message
     * when it is requested to terminate ARC.
     */
    @Test
    public void cect_11_2_17_4_RequestToTerminateArc() throws Exception {
        checkArcIsInitiated();
        hdmiCecClient.sendCecMessage(LogicalAddress.TV, LogicalAddress.BROADCAST,
                CecOperand.REPORT_PHYSICAL_ADDRESS,
                CecMessage.formatParams(HdmiCecConstants.TV_PHYSICAL_ADDRESS,
                        HdmiCecConstants.PHYSICAL_ADDRESS_LENGTH));
        hdmiCecClient.sendCecMessage(LogicalAddress.TV, AUDIO_DEVICE,
                CecOperand.REQUEST_ARC_TERMINATION);
        hdmiCecClient.checkExpectedOutput(LogicalAddress.TV, CecOperand.TERMINATE_ARC);
    }
}
