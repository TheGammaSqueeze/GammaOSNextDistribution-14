/*
 * Copyright (C) 2022 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.hdmicec.cts.AudioManagerHelper;
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

/**
 * Tests TV behavior when it receives <Set Audio Volume Level>.
 */
@RunWith(DeviceJUnit4ClassRunner.class)
public class HdmiCecAbsoluteVolumeControlFollowerTest extends BaseHdmiCecCtsTest {
    public HdmiCecAbsoluteVolumeControlFollowerTest() {
        super(HdmiCecConstants.CEC_DEVICE_TYPE_TV, "-t", "p", "-t", "a");
    }

    @Rule
    public RuleChain ruleChain =
            RuleChain.outerRule(CecRules.requiresCec(this))
                    .around(CecRules.requiresLeanback(this))
                    .around(CecRules.requiresPhysicalDevice(this))
                    .around(CecRules.requiresDeviceType(this, HdmiCecConstants.CEC_DEVICE_TYPE_TV))
                    .around(hdmiCecClient);

    /**
     * Tests that if System Audio Mode is enabled, the TV responds to <Set Audio Volume Level>
     * with <Feature Abort>[Not in correct mode to respond]
     */
    @Test
    public void testSystemAudioModeOn_respondsFeatureAbort() throws Exception {
        AudioManagerHelper.unmuteDevice(getDevice());

        int initialDeviceVolume = AudioManagerHelper.getDutAudioVolume(getDevice());

        getDevice().executeShellCommand("cmd hdmi_control setsam on");

        hdmiCecClient.sendCecMessage(LogicalAddress.PLAYBACK_1,
                CecOperand.SET_AUDIO_VOLUME_LEVEL,
                CecMessage.formatParams((initialDeviceVolume + 50) % 101));

        // Check that the DUT sent
        // <Feature Abort>[Set Audio Volume Level, Not in correct mode to respond]
        String featureAbort = hdmiCecClient.checkExpectedOutput(
                LogicalAddress.PLAYBACK_1, CecOperand.FEATURE_ABORT);
        assertThat(CecOperand.getOperand(CecMessage.getParams(featureAbort, 0, 2)))
                .isEqualTo(CecOperand.SET_AUDIO_VOLUME_LEVEL);
        assertThat(CecMessage.getParams(featureAbort, 2, 4)).isEqualTo(1);

        // Check that volume did not change
        assertThat(AudioManagerHelper.getDutAudioVolume(getDevice()))
                .isEqualTo(initialDeviceVolume);
    }

    /**
     * Tests that if System Audio Mode is disabled, the TV updates its volume after receiving
     * <Set Audio Volume Level>
     */
    @Test
    public void testSystemAudioModeOff_updatesVolume() throws Exception {
        // Wait for CEC adapter to enable System Audio Mode before turning it off
        hdmiCecClient.checkExpectedMessageFromClient(LogicalAddress.AUDIO_SYSTEM,
                LogicalAddress.TV, CecOperand.SYSTEM_AUDIO_MODE_STATUS);

        getDevice().executeShellCommand("cmd hdmi_control setsam off");

        AudioManagerHelper.unmuteDevice(getDevice());

        int initialDeviceVolume = AudioManagerHelper.getDutAudioVolume(getDevice());
        try {
            int volumeToSet = (initialDeviceVolume + 50) % 101;
            hdmiCecClient.sendCecMessage(LogicalAddress.PLAYBACK_1,
                    CecOperand.SET_AUDIO_VOLUME_LEVEL,
                    CecMessage.formatParams(volumeToSet));

            // Check that no <Feature Abort> was sent
            hdmiCecClient.checkOutputDoesNotContainMessage(LogicalAddress.PLAYBACK_1,
                    CecOperand.FEATURE_ABORT);

            // Check that device volume is within 5 points of the expected volume.
            // This accounts for rounding errors due to volume scale conversions.
            int deviceVolume = AudioManagerHelper.getDutAudioVolume(getDevice());
            assertWithMessage("Expected DUT to have volume " + volumeToSet
                    + " but was actually " + deviceVolume)
                    .that(Math.abs(volumeToSet - deviceVolume) <= 5)
                    .isTrue();
        } finally {
            AudioManagerHelper.setDeviceVolume(getDevice(), initialDeviceVolume);
        }
    }
}
