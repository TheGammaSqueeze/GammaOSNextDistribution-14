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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import android.hdmicec.cts.BaseHdmiCecCtsTest;
import android.hdmicec.cts.CecMessage;
import android.hdmicec.cts.CecOperand;
import android.hdmicec.cts.HdmiCecConstants;
import android.hdmicec.cts.HdmiControlManagerUtility;
import android.hdmicec.cts.LogicalAddress;
import android.hdmicec.cts.RemoteControlPassthrough;

import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

/**
 * HDMI CEC tests to ensure that the remote control passthrough to TV works as expected (Section
 * 11.2.13)
 */
@RunWith(DeviceJUnit4ClassRunner.class)
public final class HdmiCecRemoteControlPassThroughTest extends BaseHdmiCecCtsTest {

    private static int DUT_DEVICE_TYPE = HdmiCecConstants.CEC_DEVICE_TYPE_PLAYBACK_DEVICE;

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
                                    this, HdmiCecConstants.CEC_DEVICE_TYPE_PLAYBACK_DEVICE))
                    .around(hdmiCecClient);

    /**
     * Test 11.2.13-1
     *
     * <p>Tests that the device responds correctly to a {@code <USER_CONTROL_PRESSED>} message
     * followed immediately by a {@code <USER_CONTROL_RELEASED>} message.
     */
    @Test
    public void cect_11_2_13_1_UserControlPressAndRelease() throws Exception {
        LogicalAddress dutLogicalAddress = getTargetLogicalAddress(getDevice(), DUT_DEVICE_TYPE);
        RemoteControlPassthrough.checkUserControlPressAndRelease(
                hdmiCecClient, getDevice(), LogicalAddress.TV, dutLogicalAddress);
    }

    /**
     * Test 11.2.13-2
     * Tests that the device responds correctly to a <USER_CONTROL_PRESSED> message for press and
     * hold operations.
     */
    @Test
    public void cect_11_2_13_2_UserControlPressAndHold() throws Exception {
        LogicalAddress dutLogicalAddress = getTargetLogicalAddress(getDevice(), DUT_DEVICE_TYPE);
        RemoteControlPassthrough.checkUserControlPressAndHold(
                hdmiCecClient, getDevice(), LogicalAddress.TV, dutLogicalAddress);
    }

    /**
     * HF 4-8-4
     *
     * <p>Verify that the device that support cec version 2.0 accepts {@code <USER_CONTROL_PRESSED>}
     * messages and maps to appropriate internal action.
     *
     * No Android keycode defined for {@code <CEC_KEYCODE_FAVORITE_MENU>},
     * {@code <CEC_KEYCODE_STOP_RECORD>} and {@code <CEC_KEYCODE_PAUSE_RECORD>}
     *
     * The UI commands Audio Description, internet and 3D mode are introduced in CEC 2.0 devices but
     * they haven't been implemented yet.
     * TODO: Add these UI commands once they are implemented.
     */
    @Test
    public void cect_4_8_4_UserControlPressAndRelease_20() throws Exception {
        setCec20();
        LogicalAddress dutLogicalAddress = getTargetLogicalAddress(getDevice(), DUT_DEVICE_TYPE);
        RemoteControlPassthrough.checkUserControlPressAndRelease_20(
                hdmiCecClient, getDevice(), LogicalAddress.TV, dutLogicalAddress);
    }

    /**
     * Test HF4-8-12
     *
     * <p>Tests that device sends the UCP Commands related to menus (Device Root Menu, Device Setup
     * Menu, Contents Menu, Media Top Menu, Media Context-Sensitive Menu) in the operand [RC Profile
     * Source] that is sent in the <Report Features> message and verifies that device reacts to sent
     * UCP commands.
     */
    @Test
    public void cect_hf4_8_12_UCPForRcProfileSearchOperand() throws Exception {
        setCec20();
        hdmiCecClient.sendCecMessage(LogicalAddress.TV, CecOperand.GIVE_FEATURES);
        String message = hdmiCecClient.checkExpectedOutput(CecOperand.REPORT_FEATURES);
        int remoteControlProfileSource = CecMessage.getParams(message, 4, 6);
        if ((remoteControlProfileSource & 0x01) == 0x01) {
            sendUcpMenuCommand(
                    HdmiCecConstants.CEC_KEYCODE_MEDIA_CONTEXT_SENSITIVE_MENU,
                    "TV_MEDIA_CONTEXT_MENU");
        }
        if ((remoteControlProfileSource & 0x02) == 0x02) {
            sendUcpMenuCommand(HdmiCecConstants.CEC_KEYCODE_MEDIA_TOP_MENU, "MEDIA_TOP_MENU");
        }
        if ((remoteControlProfileSource & 0x04) == 0x04) {
            sendUcpMenuCommand(HdmiCecConstants.CEC_KEYCODE_CONTENTS_MENU, "TV_CONTENTS_MENU");
        }
        if ((remoteControlProfileSource & 0x08) == 0x08) {
            sendUcpMenuCommand(HdmiCecConstants.CEC_KEYCODE_SETUP_MENU, "SETTINGS");
        }
        if ((remoteControlProfileSource & 0x10) == 0x10) {
            sendUcpMenuCommand(HdmiCecConstants.CEC_KEYCODE_ROOT_MENU, "HOME");
        }
    }

    private void sendUcpMenuCommand(int cecKeycode, String androidKeycode) throws Exception {
        LogicalAddress dutLogicalAddress = getTargetLogicalAddress(getDevice(), DUT_DEVICE_TYPE);
        RemoteControlPassthrough.checkUserControlPressAndRelease(
                hdmiCecClient,
                getDevice(),
                LogicalAddress.TV,
                dutLogicalAddress,
                cecKeycode,
                androidKeycode);
    }

    /**
     * Test HF4-8-9 (CEC 2.0)
     *
     * <p>Tests that the DUT sends multiple {@code <USER_CONTROL_PRESSED>[KEYCODE]} when there is a
     * long press keyevent.
     */
    @Test
    public void cect_hf4_8_9_SendLongPress() throws Exception {
        setCec20();
        String message;
        int i;

        HdmiControlManagerUtility.sendLongPressKeyevent(this);
        // The above command should send 5 <UCP>[KEYCODE_UP] messages followed by 1 <UCR> message
        // and finally, a <UCP>[KEYCODE_DOWN].
        for (i = 0; i < 5; i++) {
            message =
                    hdmiCecClient.checkExpectedOutput(
                            LogicalAddress.TV, CecOperand.USER_CONTROL_PRESSED);
            assertThat(CecMessage.getParams(message)).isEqualTo(HdmiCecConstants.CEC_KEYCODE_UP);
        }
        message =
                hdmiCecClient.checkExpectedOutput(
                        LogicalAddress.TV, CecOperand.USER_CONTROL_RELEASED);
        message =
                hdmiCecClient.checkExpectedOutput(
                        LogicalAddress.TV, CecOperand.USER_CONTROL_PRESSED);
        assertThat(CecMessage.getParams(message)).isEqualTo(HdmiCecConstants.CEC_KEYCODE_DOWN);
    }

    /**
     * Test HF4-8-13 (CEC 2.0)
     *
     * <p>Tests that the device responds with a {@code <FEATURE_ABORT>[Not in correct mode]} when it
     * is not in a mode to action the message.
     */
    @Test
    public void cect_hf4_8_13_AbortIncorrectMode() throws Exception {
        setCec20();
        try {
            sendDeviceToSleep();
            hdmiCecClient.sendUserControlPressAndRelease(
                    LogicalAddress.TV, HdmiCecConstants.CEC_KEYCODE_ROOT_MENU, false);
            String message =
                    hdmiCecClient.checkExpectedOutput(LogicalAddress.TV, CecOperand.FEATURE_ABORT);
            int reason = CecMessage.getParams(message) & 0xFF;
            assertThat(reason).isEqualTo(HdmiCecConstants.ABORT_NOT_IN_CORRECT_MODE);
        } finally {
            wakeUpDevice();
        }
    }

    /*
     * Test to check that the DUT sends volume key press events to the TV when system audio mode is
     * not turned on.
     */
    @Test
    public void cect_sendVolumeKeyPressToTv() throws Exception {
        assumeFalse("Skip for audio system devices (b/323469502)",
                hasDeviceType(HdmiCecConstants.CEC_DEVICE_TYPE_AUDIO_SYSTEM));

        // The DUT won't send <User Control Pressed> messages if this condition is not met.
        assumeTrue(isPlayingStreamMusicOnHdmiOut());

        ITestDevice device = getDevice();
        String ucpMessage;
        String command = "cmd hdmi_control setsam ";

        simulateCecSinkConnected(device, getTargetLogicalAddress());
        String volumeControlEnabled =
                getSettingsValue(device, HdmiCecConstants.SETTING_VOLUME_CONTROL_ENABLED);
        setSettingsValue(
                device,
                HdmiCecConstants.SETTING_VOLUME_CONTROL_ENABLED,
                HdmiCecConstants.VOLUME_CONTROL_ENABLED);

        boolean wasSystemAudioModeOn = isSystemAudioModeOn(device);
        if (wasSystemAudioModeOn) {
            device.executeShellCommand(command + "off");
            assertWithMessage("System audio mode is not off")
                    .that(isSystemAudioModeOn(device))
                    .isFalse();
        }
        try {
            device.executeShellCommand("input keyevent KEYCODE_VOLUME_UP");
            ucpMessage =
                    hdmiCecClient.checkExpectedOutput(
                            LogicalAddress.TV, CecOperand.USER_CONTROL_PRESSED);
            assertThat(CecMessage.getParams(ucpMessage))
                    .isEqualTo(HdmiCecConstants.CEC_KEYCODE_VOLUME_UP);
            device.executeShellCommand("input keyevent KEYCODE_VOLUME_DOWN");
            ucpMessage =
                    hdmiCecClient.checkExpectedOutput(
                            LogicalAddress.TV, CecOperand.USER_CONTROL_PRESSED);
            assertThat(CecMessage.getParams(ucpMessage))
                    .isEqualTo(HdmiCecConstants.CEC_KEYCODE_VOLUME_DOWN);
            device.executeShellCommand("input keyevent KEYCODE_VOLUME_MUTE");
            ucpMessage =
                    hdmiCecClient.checkExpectedOutput(
                            LogicalAddress.TV, CecOperand.USER_CONTROL_PRESSED);
            assertThat(CecMessage.getParams(ucpMessage))
                    .isEqualTo(HdmiCecConstants.CEC_KEYCODE_MUTE);
        } finally {
            if (wasSystemAudioModeOn) {
                device.executeShellCommand(command + "on");
            }
            setSettingsValue(
                    device, HdmiCecConstants.SETTING_VOLUME_CONTROL_ENABLED, volumeControlEnabled);
        }
    }
}
