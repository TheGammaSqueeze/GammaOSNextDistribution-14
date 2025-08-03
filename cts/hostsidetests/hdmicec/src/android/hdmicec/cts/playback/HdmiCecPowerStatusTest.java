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

package android.hdmicec.cts.playback;

import android.hdmicec.cts.BaseHdmiCecCtsTest;
import android.hdmicec.cts.CecMessage;
import android.hdmicec.cts.CecOperand;
import android.hdmicec.cts.HdmiCecConstants;
import android.hdmicec.cts.LogicalAddress;

import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * HDMI CEC tests verifying power status related messages of the device (CEC 2.0 CTS Section 7.6)
 */
@RunWith(DeviceJUnit4ClassRunner.class)
public final class HdmiCecPowerStatusTest extends BaseHdmiCecCtsTest {

    public HdmiCecPowerStatusTest() {
        super(HdmiCecConstants.CEC_DEVICE_TYPE_PLAYBACK_DEVICE);
    }

    private static final int OFF = 0x1;

    private static final List<String> UCP_POWER_MSGS = new ArrayList<>(Arrays.asList(
            CecMessage.buildCecMessage(LogicalAddress.PLAYBACK_1, LogicalAddress.TV,
                    CecOperand.USER_CONTROL_PRESSED, HdmiCecConstants.CEC_KEYCODE_POWER),
            CecMessage.buildCecMessage(LogicalAddress.PLAYBACK_1, LogicalAddress.TV,
                    CecOperand.USER_CONTROL_PRESSED,
                    HdmiCecConstants.CEC_KEYCODE_POWER_TOGGLE_FUNCTION),
            CecMessage.buildCecMessage(LogicalAddress.PLAYBACK_1, LogicalAddress.TV,
                    CecOperand.USER_CONTROL_PRESSED,
                    HdmiCecConstants.CEC_KEYCODE_POWER_OFF_FUNCTION),
            CecMessage.buildCecMessage(LogicalAddress.PLAYBACK_1, LogicalAddress.TV,
                    CecOperand.USER_CONTROL_PRESSED,
                    HdmiCecConstants.CEC_KEYCODE_POWER_ON_FUNCTION)));

    private static final List<CecOperand> VIEW_ON_MSGS =
            new ArrayList<>(Arrays.asList(CecOperand.TEXT_VIEW_ON, CecOperand.IMAGE_VIEW_ON));

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
     * Test HF4-6-1
     *
     * <p>Verify that the DUT initially tries sending either {@code <Image View On>} or
     * {@code <Text View On>} to wake up the TV when the DUT wants to become the active source,
     * before sending any {@code <User Control Pressed>} with power-related operands.
     */
    @Test
    public void cect_hf4_6_1_otp_viewOnBeforeUcp_20() throws Exception {
        ITestDevice device = getDevice();
        /* Make sure the device is not booting up/in standby */
        device.waitForBootComplete(HdmiCecConstants.REBOOT_TIMEOUT);

        setCec20();

        /* simulate a TV that is in the Standby state. */
        hdmiCecClient.sendCecMessage(LogicalAddress.TV, LogicalAddress.BROADCAST,
                CecOperand.REPORT_POWER_STATUS, CecMessage.formatParams(OFF));
        TimeUnit.SECONDS.sleep(HdmiCecConstants.DEVICE_WAIT_TIME_SECONDS);

        sendOtp();

        hdmiCecClient.checkMessagesInOrder(LogicalAddress.TV, VIEW_ON_MSGS, UCP_POWER_MSGS);
        String message = hdmiCecClient.checkExpectedOutput(CecOperand.ACTIVE_SOURCE);
        CecMessage.assertPhysicalAddressValid(message, getDumpsysPhysicalAddress());
    }

    /**
     * Test HF4-6-7
     * Same as Test HF4-6-9
     *
     * Tests that a device comes out of the Standby state when it receives a {@code <Set Stream
     * Path>} message with its Physical Address as operand.
     *
     * Only applies if the DUT has Primary Device "Playback Device", "Recording Device", or "Tuner".
     */
    @Test
    public void cect_hf4_6_7_setStreamPath_powerOn() throws Exception {
        try {
            sendDeviceToSleep();
            hdmiCecClient.sendCecMessage(
                    LogicalAddress.TV,
                    LogicalAddress.BROADCAST,
                    CecOperand.SET_STREAM_PATH,
                    CecMessage.formatParams(getDumpsysPhysicalAddress(),
                            HdmiCecConstants.PHYSICAL_ADDRESS_LENGTH));
            assertDeviceWakefulness(HdmiCecConstants.WAKEFULNESS_AWAKE);
        } finally {
            wakeUpDevice();
        }
    }

    /**
     * Test HF4-6-16
     *
     * <p>Verify that the DUT initially sends a {@code <Standby>} message to the TV when system
     * standby feature is enabled, before sending any {@code <User Control Pressed>} with
     * power-related operands. (Ref section 11.5.1 in CEC 2.1 specification)
     */
    @Test
    public void cect_hf4_6_16_standby_tvBeforeUcp_20() throws Exception {
        setCec20();
        String previousPowerControlMode =
                setPowerControlMode(HdmiCecConstants.POWER_CONTROL_MODE_TV);

        try {
            sendDeviceToSleepWithoutWait();
            hdmiCecClient.checkMessagesInOrder(
                    LogicalAddress.TV,
                    new ArrayList<>(Arrays.asList(CecOperand.STANDBY)),
                    UCP_POWER_MSGS);
        } finally {
            wakeUpDevice();
            setPowerControlMode(previousPowerControlMode);
        }
    }

    /**
     * Test HF4-6-19
     *
     * <p>Verify that the DUT initially broadcasts a {@code <Standby>} message when the system
     * standby feature is enabled, before sending any {@code <User Control Pressed>} with
     * power-related operands.
     */
    @Test
    public void cect_hf4_6_19_standby_broadcastBeforeUcp_20() throws Exception {
        setCec20();
        String previousPowerControlMode =
                setPowerControlMode(HdmiCecConstants.POWER_CONTROL_MODE_BROADCAST);
        try {
            sendDeviceToSleepWithoutWait();
            hdmiCecClient.checkMessagesInOrder(
                    LogicalAddress.BROADCAST,
                    new ArrayList<>(Arrays.asList(CecOperand.STANDBY)),
                    UCP_POWER_MSGS);
        } finally {
            wakeUpDevice();
            setPowerControlMode(previousPowerControlMode);
        }
    }
}
