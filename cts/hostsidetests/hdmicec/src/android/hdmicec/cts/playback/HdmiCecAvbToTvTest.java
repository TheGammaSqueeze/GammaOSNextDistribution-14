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

package android.hdmicec.cts.playback;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeFalse;

import android.hdmicec.cts.AudioManagerHelper;
import android.hdmicec.cts.BaseHdmiCecAbsoluteVolumeBehaviorTest;
import android.hdmicec.cts.BaseHdmiCecCtsTest;
import android.hdmicec.cts.CecMessage;
import android.hdmicec.cts.CecOperand;
import android.hdmicec.cts.HdmiCecConstants;

import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

/**
 * Tests for absolute volume behavior where the DUT is a Playback device and the
 * System Audio device is a TV.
 */
@RunWith(DeviceJUnit4ClassRunner.class)
public class HdmiCecAvbToTvTest extends BaseHdmiCecAbsoluteVolumeBehaviorTest {

    /**
     * No need to pass in client parameters because the client is started as TV as long as the
     * DUT is not a TV.
     */
    public HdmiCecAvbToTvTest() {
        super(HdmiCecConstants.CEC_DEVICE_TYPE_PLAYBACK_DEVICE);
    }

    @Rule
    public RuleChain ruleChain =
            RuleChain.outerRule(BaseHdmiCecCtsTest.CecRules.requiresCec(this))
                    .around(BaseHdmiCecCtsTest.CecRules.requiresLeanback(this))
                    .around(BaseHdmiCecCtsTest.CecRules.requiresPhysicalDevice(this))
                    .around(
                            BaseHdmiCecCtsTest.CecRules.requiresDeviceType(
                                    this, HdmiCecConstants.CEC_DEVICE_TYPE_PLAYBACK_DEVICE))
                    .around(hdmiCecClient);

    /**
     * Requires the device to be able to adopt CEC 2.0 so that it sends <Give Features>.
     *
     * Tests that the DUT enables and disables AVB in response to changes in the System Audio
     * device's support for <Set Audio Volume Level>. In this test, this support status is
     * communicated through <Report Features> messages.
     */
    @Test
    public void testEnableDisableAvb_cec20_triggeredByReportFeatures() throws Exception {
        // Enable CEC 2.0
        setCec20();

        // Enable CEC volume
        setSettingsValue(HdmiCecConstants.SETTING_VOLUME_CONTROL_ENABLED,
                HdmiCecConstants.VOLUME_CONTROL_ENABLED);

        // Enable System Audio Mode if the System Audio device is an Audio System
        enableSystemAudioModeIfApplicable();

        // Since CEC 2.0 is enabled, DUT should also use <Give Features> to query AVB support
        hdmiCecClient.checkExpectedOutput(hdmiCecClient.getSelfDevice(),
                CecOperand.GIVE_FEATURES);
        hdmiCecClient.checkExpectedOutput(hdmiCecClient.getSelfDevice(),
                CecOperand.SET_AUDIO_VOLUME_LEVEL);

        // System Audio device reports support for <Set Audio Volume Level> via <Report Features>
        sendReportFeatures(true);

        // DUT queries audio status
        hdmiCecClient.checkExpectedOutput(hdmiCecClient.getSelfDevice(),
                CecOperand.GIVE_AUDIO_STATUS);
        assertFullVolumeBehavior();

        // DUT receives audio status
        hdmiCecClient.sendCecMessage(hdmiCecClient.getSelfDevice(), CecOperand.REPORT_AUDIO_STATUS,
                CecMessage.formatParams(50));
        assertAbsoluteVolumeBehavior();

        // System Audio device reports no support for <Set Audio Volume Level>
        sendReportFeatures(false);
        assertFullVolumeBehavior();
    }

    /**
     * Tests that the DUT enables and disables AVB in response to changes in the System Audio
     * device's support for <Set Audio Volume Level>. In this test, this support status is
     * communicated through (the lack of) <Feature Abort> responses to <Set Audio Volume Level>.
     */
    @Test
    public void testEnableDisableAvb_triggeredByAvbSupportChanged() throws Exception {
        assumeFalse("Skip for audio system devices (b/323469502)",
                hasDeviceType(HdmiCecConstants.CEC_DEVICE_TYPE_AUDIO_SYSTEM));

        setSettingsValue(HdmiCecConstants.SETTING_VOLUME_CONTROL_ENABLED,
                HdmiCecConstants.VOLUME_CONTROL_ENABLED);

        enableSystemAudioModeIfApplicable();

        // DUT queries AVB support by sending <Set Audio Volume Level>
        hdmiCecClient.checkExpectedOutput(hdmiCecClient.getSelfDevice(),
                CecOperand.SET_AUDIO_VOLUME_LEVEL);

        // System Audio device does not respond with <Feature Abort>. DUT queries audio status
        hdmiCecClient.checkExpectedOutput(hdmiCecClient.getSelfDevice(),
                CecOperand.GIVE_AUDIO_STATUS);
        assertFullVolumeBehavior();

        // DUT receives audio status
        hdmiCecClient.sendCecMessage(hdmiCecClient.getSelfDevice(), CecOperand.REPORT_AUDIO_STATUS,
                CecMessage.formatParams(50));
        assertAbsoluteVolumeBehavior();

        // System Audio device responds to <Set Audio Volume Level> with
        // <Feature Abort>[Unrecognized opcode]
        hdmiCecClient.sendCecMessage(hdmiCecClient.getSelfDevice(), CecOperand.FEATURE_ABORT,
                CecMessage.formatParams(CecOperand.SET_AUDIO_VOLUME_LEVEL + "00"));
        assertFullVolumeBehavior();
    }

    /**
     * Tests that the DUT enables and disables AVB in response to CEC volume control being
     * enabled or disabled.
     */
    @Test
    public void testEnableAndDisableAvb_triggeredByVolumeControlSettingChange() throws Exception {
        assumeFalse("Skip for audio system devices (b/323469502)",
                hasDeviceType(HdmiCecConstants.CEC_DEVICE_TYPE_AUDIO_SYSTEM));

        enableSystemAudioModeIfApplicable();

        // System audio device reports support for <Set Audio Volume Level>
        sendReportFeatures(true);

        // Enable CEC volume
        setSettingsValue(HdmiCecConstants.SETTING_VOLUME_CONTROL_ENABLED,
                HdmiCecConstants.VOLUME_CONTROL_ENABLED);

        // DUT queries audio status
        hdmiCecClient.checkExpectedOutput(hdmiCecClient.getSelfDevice(),
                CecOperand.GIVE_AUDIO_STATUS);
        assertFullVolumeBehavior();

        // DUT receives audio status
        hdmiCecClient.sendCecMessage(hdmiCecClient.getSelfDevice(), CecOperand.REPORT_AUDIO_STATUS,
                CecMessage.formatParams(50));
        assertAbsoluteVolumeBehavior();

        // CEC volume control is disabled on the DUT
        setSettingsValue(HdmiCecConstants.SETTING_VOLUME_CONTROL_ENABLED,
                HdmiCecConstants.VOLUME_CONTROL_DISABLED);
        assertFullVolumeBehavior();
    }

    /**
     * Tests that the DUT sends the correct CEC messages when AVB is enabled and Android
     * initiates volume changes.
     */
    @Test
    public void testOutgoingVolumeUpdates() throws Exception {
        assumeFalse("Skip for audio system devices (b/323469502)",
                hasDeviceType(HdmiCecConstants.CEC_DEVICE_TYPE_AUDIO_SYSTEM));

        // Enable AVB
        setSettingsValue(HdmiCecConstants.SETTING_VOLUME_CONTROL_ENABLED,
                HdmiCecConstants.VOLUME_CONTROL_ENABLED);
        enableSystemAudioModeIfApplicable();
        sendReportFeatures(true);
        hdmiCecClient.checkExpectedOutput(hdmiCecClient.getSelfDevice(),
                CecOperand.GIVE_AUDIO_STATUS);
        hdmiCecClient.sendCecMessage(hdmiCecClient.getSelfDevice(), CecOperand.REPORT_AUDIO_STATUS,
                CecMessage.formatParams(50));
        assertAbsoluteVolumeBehavior();

        // Calling AudioManager#setStreamVolume should cause the DUT to send
        // <Set Audio Volume Level> with the new volume level as a parameter
        AudioManagerHelper.setDeviceVolume(getDevice(), 80);
        String setAudioVolumeLevelMessage = hdmiCecClient.checkExpectedOutput(
                hdmiCecClient.getSelfDevice(), CecOperand.SET_AUDIO_VOLUME_LEVEL);
        assertThat(CecMessage.getParams(setAudioVolumeLevelMessage)).isEqualTo(80);

        // Calling AudioManager#adjustStreamVolume should cause the DUT to send
        // <User Control Pressed>, <User Control Released>, and <Give Audio Status>
        AudioManagerHelper.raiseVolume(getDevice());
        String userControlPressedMessage = hdmiCecClient.checkExpectedOutput(
                hdmiCecClient.getSelfDevice(), CecOperand.USER_CONTROL_PRESSED);
        assertThat(CecMessage.getParams(userControlPressedMessage))
                .isEqualTo(HdmiCecConstants.CEC_KEYCODE_VOLUME_UP);
        hdmiCecClient.checkExpectedOutput(hdmiCecClient.getSelfDevice(),
                CecOperand.USER_CONTROL_RELEASED);
        hdmiCecClient.checkExpectedOutput(hdmiCecClient.getSelfDevice(),
                CecOperand.GIVE_AUDIO_STATUS);
    }

    /**
     * Tests that the DUT notifies AudioManager when it receives <Report Audio Status> from the
     * System Audio device.
     */
    @Test
    public void testIncomingVolumeUpdates() throws Exception {
        assumeFalse("Skip for audio system devices (b/323469502)",
                hasDeviceType(HdmiCecConstants.CEC_DEVICE_TYPE_AUDIO_SYSTEM));

        // Enable AVB
        setSettingsValue(HdmiCecConstants.SETTING_VOLUME_CONTROL_ENABLED,
                HdmiCecConstants.VOLUME_CONTROL_ENABLED);
        enableSystemAudioModeIfApplicable();
        sendReportFeatures(true);
        hdmiCecClient.checkExpectedOutput(hdmiCecClient.getSelfDevice(),
                CecOperand.GIVE_AUDIO_STATUS);
        hdmiCecClient.sendCecMessage(hdmiCecClient.getSelfDevice(), CecOperand.REPORT_AUDIO_STATUS,
                CecMessage.formatParams(50)); // Volume 50, mute off
        assertAbsoluteVolumeBehavior();

        // Volume and mute status should match the initial <Report Audio Status>
        assertApproximateDeviceVolumeAndMute(50, false);

        // Test an incoming <Report Audio Status> that does not mute the device
        hdmiCecClient.sendCecMessage(hdmiCecClient.getSelfDevice(), CecOperand.REPORT_AUDIO_STATUS,
                CecMessage.formatParams(90)); // Volume 90, mute off
        assertApproximateDeviceVolumeAndMute(90, false);

        // Test an incoming <Report Audio Status> that mutes the device
        hdmiCecClient.sendCecMessage(hdmiCecClient.getSelfDevice(), CecOperand.REPORT_AUDIO_STATUS,
                CecMessage.formatParams(70 + 0b1000_0000)); // Volume 70, mute on
        assertApproximateDeviceVolumeAndMute(0, true);
    }
}
