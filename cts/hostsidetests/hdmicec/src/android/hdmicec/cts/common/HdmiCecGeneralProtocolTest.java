/*
 * Copyright 2021 The Android Open Source Project
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
import android.hdmicec.cts.HdmiCecConstants;

import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

/** HDMI CEC 2.0 general protocol tests */
@RunWith(DeviceJUnit4ClassRunner.class)
public final class HdmiCecGeneralProtocolTest extends BaseHdmiCecCtsTest {

    @Rule
    public RuleChain ruleChain =
            RuleChain.outerRule(CecRules.requiresCec(this))
                    .around(CecRules.requiresLeanback(this))
                    .around(CecRules.requiresPhysicalDevice(this))
                    .around(hdmiCecClient);

    /**
     * Test HF4-2-2
     *
     * <p> Verify that device ignores applicable messages sent from logical address F to DUT. (one
     * by one, with a 3 sec spacing).
     */
    @Test
    public void cect_4_2_2_ignoreMessagesFromAddressF() throws Exception {
        setCec20();
        // get cec reinit messages
        hdmiCecClient.getAllMessages(mDutLogicalAddresses,
                HdmiCecConstants.TIMEOUT_CEC_REINIT_SECONDS);

        sendMessageAndVerifyNoMessageSentFromDevice(CecOperand.GIVE_TUNER_DEVICE_STATUS, ":01");
        sendMessageAndVerifyNoMessageSentFromDevice(CecOperand.RECORD_ON);
        sendMessageAndVerifyNoMessageSentFromDevice(CecOperand.RECORD_OFF);
        sendMessageAndVerifyNoMessageSentFromDevice(CecOperand.RECORD_TV_SCREEN);
        sendMessageAndVerifyNoMessageSentFromDevice(CecOperand.GIVE_DECK_STATUS, ":01");
        sendMessageAndVerifyNoMessageSentFromDevice(
                CecOperand.CLEAR_ANALOG_TIMER, ":02:02:02:02:02:02:00:00:00:02:00");
        sendMessageAndVerifyNoMessageSentFromDevice(
                CecOperand.SET_ANALOG_TIMER, ":02:02:02:02:02:02:00:00:00:02:00");
        sendMessageAndVerifyNoMessageSentFromDevice(CecOperand.PLAY, ":05");
        sendMessageAndVerifyNoMessageSentFromDevice(CecOperand.DECK_CONTROL, ":01");
        sendMessageAndVerifyNoMessageSentFromDevice(CecOperand.GIVE_OSD_NAME);
        sendMessageAndVerifyNoMessageSentFromDevice(CecOperand.GIVE_AUDIO_STATUS);
        sendMessageAndVerifyNoMessageSentFromDevice(CecOperand.GIVE_SYSTEM_AUDIO_MODE_STATUS);
        sendMessageAndVerifyNoMessageSentFromDevice(CecOperand.VENDOR_COMMAND, ":00:01");
        sendMessageAndVerifyNoMessageSentFromDevice(CecOperand.MENU_REQUEST, ":00");
        sendMessageAndVerifyNoMessageSentFromDevice(CecOperand.GIVE_POWER_STATUS);
        sendMessageAndVerifyNoMessageSentFromDevice(
                CecOperand.SET_DIGITAL_TIMER, ":02:02:02:02:02:02:00:00:00:02:00");
        sendMessageAndVerifyNoMessageSentFromDevice(
                CecOperand.CLEAR_DIGITAL_TIMER, ":02:02:02:02:02:02:00:00:00:02:00");
        sendMessageAndVerifyNoMessageSentFromDevice(CecOperand.GET_CEC_VERSION);
        sendMessageAndVerifyNoMessageSentFromDevice(
                CecOperand.CLEAR_EXTERNAL_TIMER, ":02:02:02:02:02:02:00:10:02");
        sendMessageAndVerifyNoMessageSentFromDevice(CecOperand.REQUEST_SHORT_AUDIO_DESCRIPTOR);
        sendMessageAndVerifyNoMessageSentFromDevice(CecOperand.INITIATE_ARC);
        sendMessageAndVerifyNoMessageSentFromDevice(CecOperand.REQUEST_ARC_INITIATION);
        sendMessageAndVerifyNoMessageSentFromDevice(CecOperand.REQUEST_ARC_TERMINATION);
        sendMessageAndVerifyNoMessageSentFromDevice(CecOperand.TERMINATE_ARC);
        sendMessageAndVerifyNoMessageSentFromDevice(CecOperand.ABORT);
    }

    public void sendMessageAndVerifyNoMessageSentFromDevice(CecOperand message, String params)
            throws Exception {
        // DeviceDiscoveryAction will send GIVE_OSD_NAME and GIVE_DEVICE_VENDOR_ID
        // HotplugDetectionAction will send GIVE_PHYSICAL_ADDRESS
        // PowerStatusMonitorAction will send GIVE_POWER_STATUS
        // AbsoluteVolumeAudioStatusAction will send GIVE_AUDIO_STATUS
        List<CecOperand> excludeOperands = new ArrayList<>();
        excludeOperands.add(CecOperand.GIVE_PHYSICAL_ADDRESS);
        excludeOperands.add(CecOperand.GIVE_DEVICE_VENDOR_ID);
        excludeOperands.add(CecOperand.GIVE_OSD_NAME);
        excludeOperands.add(CecOperand.GIVE_POWER_STATUS);
        excludeOperands.add(CecOperand.GIVE_AUDIO_STATUS);

        hdmiCecClient.sendCecMessage(message, params);
        // Default timeout for the incoming command to arrive in response to a request is 2 secs
        // Thus test ensures no messages are sent from DUT for a spacing of 3 secs
        hdmiCecClient.checkNoMessagesSentFromDevice(3000, excludeOperands);
    }

    public void sendMessageAndVerifyNoMessageSentFromDevice(CecOperand message) throws Exception {
        sendMessageAndVerifyNoMessageSentFromDevice(message, "");
    }

    /**
     * Test HF4-2-15 (CEC 2.0)
     *
     * <p>Tests that the DUT responds to mandatory messages in both on and standby states
     */
    @Test
    public void cect_hf_4_2_15() throws Exception {
        setCec20();
        int cecVersion = HdmiCecConstants.CEC_VERSION_2_0;
        int physicalAddressParams;
        int features = 0;
        String osdName;

        sendDeviceToSleep();
        try {
            // Check POLL message response
            hdmiCecClient.sendPoll();
            if (!hdmiCecClient.checkConsoleOutput(HdmiCecConstants.POLL_SUCCESS)) {
                throw new Exception("Device did not respond to Poll");
            }

            // Check CEC version
            hdmiCecClient.sendCecMessage(hdmiCecClient.getSelfDevice(), CecOperand.GET_CEC_VERSION);
            String message =
                    hdmiCecClient.checkExpectedOutput(
                            hdmiCecClient.getSelfDevice(), CecOperand.CEC_VERSION);
            assertThat(CecMessage.getParams(message)).isEqualTo(cecVersion);

            // Give physical address
            hdmiCecClient.sendCecMessage(
                    hdmiCecClient.getSelfDevice(), CecOperand.GIVE_PHYSICAL_ADDRESS);
            message = hdmiCecClient.checkExpectedOutput(CecOperand.REPORT_PHYSICAL_ADDRESS);
            physicalAddressParams = CecMessage.getParams(message);

            // Give features
            hdmiCecClient.sendCecMessage(hdmiCecClient.getSelfDevice(), CecOperand.GIVE_FEATURES);
            message = hdmiCecClient.checkExpectedOutput(CecOperand.REPORT_FEATURES);
            features = CecMessage.getParams(message);

            // Give power status
            hdmiCecClient.sendCecMessage(
                    hdmiCecClient.getSelfDevice(), CecOperand.GIVE_POWER_STATUS);
            message =
                    hdmiCecClient.checkExpectedOutput(
                            hdmiCecClient.getSelfDevice(), CecOperand.REPORT_POWER_STATUS);
            assertThat(CecMessage.getParams(message))
                    .isEqualTo(HdmiCecConstants.CEC_POWER_STATUS_STANDBY);

            // Give OSD name
            hdmiCecClient.sendCecMessage(hdmiCecClient.getSelfDevice(), CecOperand.GIVE_OSD_NAME);
            message =
                    hdmiCecClient.checkExpectedOutput(
                            hdmiCecClient.getSelfDevice(), CecOperand.SET_OSD_NAME);
            osdName = CecMessage.getParamsAsString(message);
        } finally {
            wakeUpDevice();
        }

        // Repeat the above with DUT not in standby, and verify that the responses are the same,
        // except the <Report Power Status> message
        // Check POLL message response
        hdmiCecClient.sendPoll();
        if (!hdmiCecClient.checkConsoleOutput(HdmiCecConstants.POLL_SUCCESS)) {
            throw new Exception("Device did not respond to Poll");
        }

        // Check CEC version
        hdmiCecClient.sendCecMessage(hdmiCecClient.getSelfDevice(), CecOperand.GET_CEC_VERSION);
        String message =
                hdmiCecClient.checkExpectedOutput(
                        hdmiCecClient.getSelfDevice(), CecOperand.CEC_VERSION);
        assertThat(CecMessage.getParams(message)).isEqualTo(cecVersion);

        // Give physical address
        hdmiCecClient.sendCecMessage(
                hdmiCecClient.getSelfDevice(), CecOperand.GIVE_PHYSICAL_ADDRESS);
        message = hdmiCecClient.checkExpectedOutput(CecOperand.REPORT_PHYSICAL_ADDRESS);
        assertThat(CecMessage.getParams(message)).isEqualTo(physicalAddressParams);

        // Give features
        hdmiCecClient.sendCecMessage(hdmiCecClient.getSelfDevice(), CecOperand.GIVE_FEATURES);
        message = hdmiCecClient.checkExpectedOutput(CecOperand.REPORT_FEATURES);
        assertThat(CecMessage.getParams(message)).isEqualTo(features);

        // Give power status
        hdmiCecClient.sendCecMessage(hdmiCecClient.getSelfDevice(), CecOperand.GIVE_POWER_STATUS);
        message =
                hdmiCecClient.checkExpectedOutput(
                        hdmiCecClient.getSelfDevice(), CecOperand.REPORT_POWER_STATUS);
        assertThat(CecMessage.getParams(message)).isEqualTo(HdmiCecConstants.CEC_POWER_STATUS_ON);

        // Give OSD name
        hdmiCecClient.sendCecMessage(hdmiCecClient.getSelfDevice(), CecOperand.GIVE_OSD_NAME);
        message =
                hdmiCecClient.checkExpectedOutput(
                        hdmiCecClient.getSelfDevice(), CecOperand.SET_OSD_NAME);
        assertThat(CecMessage.getParamsAsString(message)).isEqualTo(osdName);
    }
}
