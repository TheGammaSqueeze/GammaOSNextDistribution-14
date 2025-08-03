/*
 * Copyright (C) 2021 The Android Open Source Project
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

import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

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

/** HDMI CEC test verifying system audio control commands (CEC 2.0 CTS Section 7.6) */
@RunWith(DeviceJUnit4ClassRunner.class)
public final class HdmiCecSystemAudioControlTest extends BaseHdmiCecCtsTest {

    public HdmiCecSystemAudioControlTest() {
        super("-t", "a", "-t", "x");
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
     * Test HF4-10-5
     *
     * <p>Tests that a device forwards all remote control commands to the device that is providing
     * the audio rendering.
     */
    @Test
    public void cect_hf4_10_5_RemoteControlCommandsWithSystemAudioControlProperty()
            throws Exception {
        setCec20();
        assumeFalse("Skip for audio system devices (b/323469502)",
                hasDeviceType(HdmiCecConstants.CEC_DEVICE_TYPE_AUDIO_SYSTEM));
        // The DUT won't send <User Control Pressed> messages if this condition is not met.
        assumeTrue(isPlayingStreamMusicOnHdmiOut());

        ITestDevice device = getDevice();
        String volumeControlEnabled =
                getSettingsValue(device, HdmiCecConstants.SETTING_VOLUME_CONTROL_ENABLED);

        try {
            simulateCecSinkConnected(device, getTargetLogicalAddress());
            setSettingsValue(
                    device,
                    HdmiCecConstants.SETTING_VOLUME_CONTROL_ENABLED,
                    HdmiCecConstants.VOLUME_CONTROL_ENABLED);
            waitForCondition(() -> isHdmiCecVolumeControlEnabled(device),
                        "Could not enable hdmi cec volume control");

            // Broadcast <Set System Audio Mode> ["off"].
            broadcastSystemAudioModeMessage(false);
            waitForCondition(() -> !isSystemAudioActivated(device),
                        "Could not <Set System Audio Mode> [off]");
            // All remote control commands should forward to the TV.
            sendVolumeUpCommandAndCheckForUcp(LogicalAddress.TV);

            // Broadcast <Set System Audio Mode> ["on"].
            broadcastSystemAudioModeMessage(true);
            waitForCondition(() -> isSystemAudioActivated(device),
                        "Could not <Set System Audio Mode> [on]");
            // All remote control commands should forward to the audio rendering device.
            sendVolumeUpCommandAndCheckForUcp(LogicalAddress.AUDIO_SYSTEM);
        } finally {
            setSettingsValue(
                    device, HdmiCecConstants.SETTING_VOLUME_CONTROL_ENABLED, volumeControlEnabled);
        }
    }

    private void broadcastSystemAudioModeMessage(boolean val) throws Exception {
        hdmiCecClient.sendCecMessage(
                hdmiCecClient.getSelfDevice(),
                LogicalAddress.BROADCAST,
                CecOperand.SET_SYSTEM_AUDIO_MODE,
                CecMessage.formatParams(val ? 1 : 0));
    }

    private void sendVolumeUpCommandAndCheckForUcp(LogicalAddress toDevice) throws Exception {
        getDevice().executeShellCommand("input keyevent KEYCODE_VOLUME_UP");
        String message =
                hdmiCecClient.checkExpectedOutput(toDevice, CecOperand.USER_CONTROL_PRESSED);
        assertThat(CecMessage.getParams(message)).isEqualTo(HdmiCecConstants.CEC_KEYCODE_VOLUME_UP);
        hdmiCecClient.checkExpectedOutput(toDevice, CecOperand.USER_CONTROL_RELEASED);
    }

    private boolean isSystemAudioActivated(ITestDevice device) throws Exception {
        return device.executeShellCommand(
                "dumpsys hdmi_control | grep mSystemAudioActivated:")
                .replace("mSystemAudioActivated:", "").trim().equals("true");
    }

    private boolean isHdmiCecVolumeControlEnabled(ITestDevice device) throws Exception {
        return device.executeShellCommand(
                "dumpsys hdmi_control | grep mHdmiCecVolumeControlEnabled:")
                .replace("mHdmiCecVolumeControlEnabled:", "").trim().equals("1");
    }
}
