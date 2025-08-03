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

import static org.junit.Assume.assumeFalse;

import android.hdmicec.cts.AudioManagerHelper;
import android.hdmicec.cts.BaseHdmiCecCtsTest;
import android.hdmicec.cts.CecMessage;
import android.hdmicec.cts.CecOperand;
import android.hdmicec.cts.HdmiCecConstants;
import android.hdmicec.cts.LogicalAddress;

import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeUnit;

/** HDMI CEC test to verify system audio control commands (Section 11.1.15, 11.2.15) */
@RunWith(DeviceJUnit4ClassRunner.class)
public final class HdmiCecSystemAudioControlTest extends BaseHdmiCecCtsTest {

    public boolean isDutTv;

    public HdmiCecSystemAudioControlTest() {
        super("-t", "a");
    }

    @Rule
    public RuleChain ruleChain =
            RuleChain.outerRule(CecRules.requiresCec(this))
                    .around(CecRules.requiresLeanback(this))
                    .around(CecRules.requiresPhysicalDevice(this))
                    .around(
                            CecRules.skipDeviceType(
                                    this, HdmiCecConstants.CEC_DEVICE_TYPE_AUDIO_SYSTEM))
                    .around(hdmiCecClient);

    @Before
    public void initialTestSetup() throws Exception {
        /*
         * Set the device volume level to 20, because if the volume were 100% or 0%, it would not
         * be possible to detect if there was an incorrect increase or decrease in level.
         */
        AudioManagerHelper.setDeviceVolume(getDevice(), 20);
        TimeUnit.SECONDS.sleep(5);
        isDutTv = hasDeviceType(HdmiCecConstants.CEC_DEVICE_TYPE_TV);
        if (isDutTv) {
            int initialDutVolume = AudioManagerHelper.getDutAudioVolume(getDevice());
        }
    }

    /**
     * Test 11.1.15-2, 11.2.15-11
     *
     * <p>Tests that when System Audio Control is On, the device sends {@code
     * <USER_CONTROL_PRESSED>} and {@code <USER_CONTROL_RELEASED>} messages when the volume up and
     * down keys are pressed on the DUT. Test also verifies that the {@code <USER_CONTROL_PRESSED>}
     * message has the right control param.
     */
    @Test
    public void cect_VolumeUpDownUserControlPressedWhenSystemAudioControlOn() throws Exception {
        /*
         * TODO: Remove the assumeFalse below and let test run for playback devices when b/172539380
         * is fixed.
         */
        assumeFalse("Skip for playback devices (b/172539380)",
                hasDeviceType(HdmiCecConstants.CEC_DEVICE_TYPE_PLAYBACK_DEVICE));
        ITestDevice device = getDevice();
        hdmiCecClient.sendCecMessage(
                hdmiCecClient.getSelfDevice(),
                LogicalAddress.BROADCAST,
                CecOperand.SET_SYSTEM_AUDIO_MODE,
                CecMessage.formatParams(1));
        device.executeShellCommand("input keyevent KEYCODE_VOLUME_UP");
        String message =
                hdmiCecClient.checkExpectedOutput(
                        hdmiCecClient.getSelfDevice(), CecOperand.USER_CONTROL_PRESSED);
        assertThat(CecMessage.getParams(message)).isEqualTo(HdmiCecConstants.CEC_KEYCODE_VOLUME_UP);
        hdmiCecClient.checkExpectedOutput(
                hdmiCecClient.getSelfDevice(), CecOperand.USER_CONTROL_RELEASED);
        /* TODO: b/174733146  For TV devices, assert that the volume level has not changed. */

        device.executeShellCommand("input keyevent KEYCODE_VOLUME_DOWN");
        message =
                hdmiCecClient.checkExpectedOutput(
                        hdmiCecClient.getSelfDevice(), CecOperand.USER_CONTROL_PRESSED);
        assertThat(CecMessage.getParams(message))
                .isEqualTo(HdmiCecConstants.CEC_KEYCODE_VOLUME_DOWN);
        hdmiCecClient.checkExpectedOutput(
                hdmiCecClient.getSelfDevice(), CecOperand.USER_CONTROL_RELEASED);
        /* TODO: b/174733146  For TV devices, assert that the volume level has not changed. */
    }

    /**
     * Test 11.1.15-3, 11.2.15-12
     *
     * <p>Tests that the device sends {@code <USER_CONTROL_PRESSED>} and {@code
     * <USER_CONTROL_RELEASED>} messages when the mute key is pressed on the DUT. Test also verifies
     * that the{@code <USER_CONTROL_PRESSED>} message has the right control param.
     */
    @Test
    public void cect_MuteUserControlPressedWhenSystemAudioControlOn() throws Exception {
        /*
         * TODO: Remove the assumeFalse below and let test run for playback devices when b/172539380
         * is fixed.
         */
        assumeFalse("Skip for playback devices (b/172539380)",
                hasDeviceType(HdmiCecConstants.CEC_DEVICE_TYPE_PLAYBACK_DEVICE));
        ITestDevice device = getDevice();
        hdmiCecClient.sendCecMessage(
                hdmiCecClient.getSelfDevice(),
                LogicalAddress.BROADCAST,
                CecOperand.SET_SYSTEM_AUDIO_MODE,
                CecMessage.formatParams(1));
        device.executeShellCommand("input keyevent KEYCODE_VOLUME_MUTE");
        String message =
                hdmiCecClient.checkExpectedOutput(
                        hdmiCecClient.getSelfDevice(), CecOperand.USER_CONTROL_PRESSED);
        assertThat(CecMessage.getParams(message)).isEqualTo(HdmiCecConstants.CEC_KEYCODE_MUTE);
        hdmiCecClient.checkExpectedOutput(
                hdmiCecClient.getSelfDevice(), CecOperand.USER_CONTROL_RELEASED);
        /* TODO: b/174733146  For TV devices, assert that the volume level has not changed. */
    }

    /**
     * Test 11.1.15-4, 11.2.15-10
     *
     * <p>Tests that the device sends a {@code <GIVE_SYSTEM_AUDIO_STATUS>} message when brought out
     * of standby
     */
    @Test
    public void cect_GiveSystemAudioModeStatus() throws Exception {
        ITestDevice device = getDevice();
        sendDeviceToSleep();
        wakeUpDeviceWithoutWait();
        hdmiCecClient.checkExpectedOutput(
                hdmiCecClient.getSelfDevice(), CecOperand.GIVE_SYSTEM_AUDIO_MODE_STATUS);
    }
}
