/*
 * Copyright (C) 2020 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertWithMessage;

import android.hdmicec.cts.BaseHdmiCecCtsTest;
import android.hdmicec.cts.CecOperand;
import android.hdmicec.cts.HdmiCecConstants;

import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * HDMI CEC tests verifying CEC messages sent after startup
 */
@RunWith(DeviceJUnit4ClassRunner.class)
public final class HdmiCecStartupTest extends BaseHdmiCecCtsTest {

    @Rule
    public RuleChain ruleChain =
            RuleChain
                    .outerRule(CecRules.requiresCec(this))
                    .around(CecRules.requiresLeanback(this))
                    .around(CecRules.requiresPhysicalDevice(this))
                    .around(hdmiCecClient);

    /**
     * CEC 1.4
     *
     * Tests that the device sends all the messages that should be sent on startup. It also ensures
     * that only the device only sends messages which are allowed by the spec.
     */
    @Test
    public void cectVerifyStartupMessages_Cec14b() throws Exception {
        ITestDevice device = getDevice();

        List<CecOperand> expectedMessages = Collections.singletonList(
                CecOperand.REPORT_PHYSICAL_ADDRESS);
        List<CecOperand> allowedMessages = new ArrayList<>(
                Arrays.asList(CecOperand.VENDOR_COMMAND, CecOperand.GIVE_DEVICE_VENDOR_ID,
                        CecOperand.VENDOR_COMMAND_WITH_ID,
                        CecOperand.SET_OSD_NAME, CecOperand.GIVE_OSD_NAME, CecOperand.CEC_VERSION,
                        CecOperand.DEVICE_VENDOR_ID, CecOperand.GIVE_POWER_STATUS,
                        CecOperand.GET_MENU_LANGUAGE, CecOperand.ACTIVE_SOURCE,
                        CecOperand.REQUEST_ACTIVE_SOURCE, CecOperand.GIVE_PHYSICAL_ADDRESS,
                        CecOperand.REPORT_POWER_STATUS, CecOperand.GIVE_SYSTEM_AUDIO_MODE_STATUS));
        allowedMessages.addAll(expectedMessages);

        String deviceType = device.getProperty(HdmiCecConstants.HDMI_DEVICE_TYPE_PROPERTY);
        boolean isAudioSystem = deviceType.contains(
                Integer.toString(HdmiCecConstants.CEC_DEVICE_TYPE_AUDIO_SYSTEM));
        if (isAudioSystem) {
            allowedMessages.addAll(new ArrayList<>(
                Arrays.asList(CecOperand.SET_SYSTEM_AUDIO_MODE, CecOperand.INITIATE_ARC)));
        }

        device.reboot();
        /* Monitor CEC messages for 20s after reboot */
        final List<CecOperand> messagesReceived =
                hdmiCecClient.getAllMessages(mDutLogicalAddresses, 20);

        List<CecOperand> notPermittedMessages = messagesReceived.stream()
                .filter(message -> !allowedMessages.contains(message))
                .filter(message -> !expectedMessages.contains(message))
                .collect(Collectors.toList());

        List<CecOperand> requiredMessages = messagesReceived.stream()
                .filter(expectedMessages::contains)
                .collect(Collectors.toList());

        assertWithMessage("Unexpected messages sent by the device").that(
                notPermittedMessages).isEmpty();
        assertWithMessage("Some necessary messages are missing").that(requiredMessages).hasSize(
                expectedMessages.size());
        assertWithMessage("Expected <Report Physical Address>").that(
                requiredMessages.get(0)).isEqualTo(CecOperand.REPORT_PHYSICAL_ADDRESS);
    }

    /**
     * CEC 2.0 CTS 7.5.
     *
     * Verifies that {@code <Report Features>} and {@code <Report Physical Address>} are sent at
     * device startup.
     * Verifies that both messages are sent in the given order.
     */
    @Test
    public void hf_7_5_verifyStartupMessages() throws Exception {
        ITestDevice device = getDevice();
        setCec20();

        List<CecOperand> expectedMessages = Arrays.asList(CecOperand.REPORT_PHYSICAL_ADDRESS,
                CecOperand.REPORT_FEATURES);

        device.reboot();
        /* Monitor CEC messages for 20s after reboot */
        final List<CecOperand> messagesReceived =
                hdmiCecClient.getAllMessages(mDutLogicalAddresses, 20);

        List<CecOperand> requiredMessages = messagesReceived.stream()
                .filter(expectedMessages::contains)
                .collect(Collectors.toList());

        assertWithMessage("Some necessary messages are missing").that(requiredMessages).hasSize(
                expectedMessages.size());
        assertWithMessage("Expected <Report Features> first").that(
                requiredMessages.get(0)).isEqualTo(CecOperand.REPORT_FEATURES);
        assertWithMessage("Expected <Report Physical Address> last").that(
                requiredMessages.get(1)).isEqualTo(CecOperand.REPORT_PHYSICAL_ADDRESS);
    }

}
