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

import static com.google.common.truth.Truth.assertThat;

import android.hdmicec.cts.AudioManagerHelper;
import android.hdmicec.cts.BaseHdmiCecAbsoluteVolumeBehaviorTest;
import android.hdmicec.cts.BaseHdmiCecCtsTest;
import android.hdmicec.cts.CecMessage;
import android.hdmicec.cts.CecOperand;
import android.hdmicec.cts.HdmiCecConstants;
import android.hdmicec.cts.LogicalAddress;

import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

/**
 * <pre>
 * Tests for absolute volume behavior where the DUT is a TV and the System Audio device is an
 * Audio System.
 *
 * When the CEC adapter is an Audio System, it always responds <Feature Abort> to
 * <Set Audio Volume Level>. This makes it impossible to enable "regular" absolute volume behavior.
 * As a result, this class only tests adjust-only absolute volume behavior.
 * </pre>
 */
@RunWith(DeviceJUnit4ClassRunner.class)
public class HdmiCecAvbToAudioSystemTest extends BaseHdmiCecAbsoluteVolumeBehaviorTest {
    public HdmiCecAvbToAudioSystemTest() {
        super(HdmiCecConstants.CEC_DEVICE_TYPE_TV, "-t", "a");
    }

    @Rule
    public RuleChain ruleChain =
            RuleChain.outerRule(BaseHdmiCecCtsTest.CecRules.requiresCec(this))
                    .around(BaseHdmiCecCtsTest.CecRules.requiresLeanback(this))
                    .around(CecRules.requiresPhysicalDevice(this))
                    .around(
                            BaseHdmiCecCtsTest.CecRules.requiresDeviceType(
                                    this, HdmiCecConstants.CEC_DEVICE_TYPE_TV))
                    .around(hdmiCecClient);

    /**
     * Tests that the DUT enables and disables adjust-only AVB in response to System Audio mode
     * being enabled or disabled.
     */
    @Test
    @Ignore("b/281806793")
    public void testEnableDisableAdjustOnlyAvb_triggeredBySystemAudioModeChange() throws Exception {
        // CEC volume control is enabled on the DUT
        setSettingsValue(HdmiCecConstants.SETTING_VOLUME_CONTROL_ENABLED,
                HdmiCecConstants.VOLUME_CONTROL_ENABLED);

        // Enable System Audio Mode
        broadcastSystemAudioModeMessage(true);

        // DUT queries AVB support by sending <Set Audio Volume Level>
        // CEC adapter responds <Feature Abort>
        hdmiCecClient.checkExpectedOutput(hdmiCecClient.getSelfDevice(),
                CecOperand.SET_AUDIO_VOLUME_LEVEL);
        hdmiCecClient.checkExpectedMessageFromClient(LogicalAddress.AUDIO_SYSTEM, LogicalAddress.TV,
                CecOperand.FEATURE_ABORT);

        // DUT queries audio status
        hdmiCecClient.checkExpectedOutput(hdmiCecClient.getSelfDevice(),
                CecOperand.GIVE_AUDIO_STATUS);
        assertFullVolumeBehavior();

        // DUT receives audio status
        hdmiCecClient.sendCecMessage(hdmiCecClient.getSelfDevice(), CecOperand.REPORT_AUDIO_STATUS,
                CecMessage.formatParams(50));
        assertAdjustOnlyAbsoluteVolumeBehavior();

        // System Audio Mode is disabled
        broadcastSystemAudioModeMessage(false);
        assertFullVolumeBehavior();
    }

    /**
     * Tests that the DUT enables and disables adjust-only AVB in response to CEC volume control
     * being enabled or disabled.
     */
    @Test
    @Ignore("b/281806793")
    public void testEnableDisableAdjustOnlyAvb_triggeredByVolumeControlSettingChange()
            throws Exception {
        // Enable System Audio Mode
        broadcastSystemAudioModeMessage(true);

        // Enable CEC volume
        setSettingsValue(HdmiCecConstants.SETTING_VOLUME_CONTROL_ENABLED,
                HdmiCecConstants.VOLUME_CONTROL_ENABLED);

        // DUT queries AVB support by sending <Set Audio Volume Level>
        // CEC adapter responds <Feature Abort>
        hdmiCecClient.checkExpectedOutput(hdmiCecClient.getSelfDevice(),
                CecOperand.SET_AUDIO_VOLUME_LEVEL);
        hdmiCecClient.checkExpectedMessageFromClient(LogicalAddress.AUDIO_SYSTEM, LogicalAddress.TV,
                CecOperand.FEATURE_ABORT);

        // DUT queries audio status
        hdmiCecClient.checkExpectedOutput(hdmiCecClient.getSelfDevice(),
                CecOperand.GIVE_AUDIO_STATUS);
        assertFullVolumeBehavior();

        // DUT receives audio status
        hdmiCecClient.sendCecMessage(hdmiCecClient.getSelfDevice(), CecOperand.REPORT_AUDIO_STATUS,
                CecMessage.formatParams(50));
        assertAdjustOnlyAbsoluteVolumeBehavior();


        // CEC volume control is disabled on the DUT
        setSettingsValue(HdmiCecConstants.SETTING_VOLUME_CONTROL_ENABLED,
                HdmiCecConstants.VOLUME_CONTROL_DISABLED);
        assertFullVolumeBehavior();
    }

    /**
     * Tests that the DUT sends the correct CEC messages when adjust-only AVB is enabled and Android
     * initiates volume changes.
     *
     * With adjust-only AVB, AudioManager#setStreamVolume should not send <Set Audio Volume Level>,
     * but AudioManager#adjustStreamVolume should send <User Control Pressed>.
     */
    @Test
    @Ignore("b/281806793")
    public void adjustOnlyAvb_testOutgoingVolumeUpdates() throws Exception {
        // Enable adjust-only AVB
        setSettingsValue(HdmiCecConstants.SETTING_VOLUME_CONTROL_ENABLED,
                HdmiCecConstants.VOLUME_CONTROL_ENABLED);
        broadcastSystemAudioModeMessage(true);
        hdmiCecClient.checkExpectedOutput(hdmiCecClient.getSelfDevice(),
                CecOperand.SET_AUDIO_VOLUME_LEVEL);
        hdmiCecClient.checkExpectedMessageFromClient(LogicalAddress.AUDIO_SYSTEM, LogicalAddress.TV,
                CecOperand.FEATURE_ABORT);
        hdmiCecClient.checkExpectedOutput(hdmiCecClient.getSelfDevice(),
                CecOperand.GIVE_AUDIO_STATUS);
        hdmiCecClient.sendCecMessage(hdmiCecClient.getSelfDevice(), CecOperand.REPORT_AUDIO_STATUS,
                CecMessage.formatParams(50));
        assertAdjustOnlyAbsoluteVolumeBehavior();

        // Calling AudioManager#setStreamVolume should NOT cause the DUT to send
        // <Set Audio Volume Level>
        hdmiCecClient.clearClientOutput();

        AudioManagerHelper.setDeviceVolume(getDevice(), 80);
        hdmiCecClient.checkOutputDoesNotContainMessage(hdmiCecClient.getSelfDevice(),
                CecOperand.SET_AUDIO_VOLUME_LEVEL);

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
     * System Audio device, while using adjust-only AVB.
     */
    @Test
    @Ignore("b/281806793")
    public void adjustOnlyAvb_testIncomingVolumeUpdates() throws Exception {
        // Enable adjust-only AVB
        setSettingsValue(HdmiCecConstants.SETTING_VOLUME_CONTROL_ENABLED,
                HdmiCecConstants.VOLUME_CONTROL_ENABLED);
        broadcastSystemAudioModeMessage(true);
        hdmiCecClient.checkExpectedOutput(hdmiCecClient.getSelfDevice(),
                CecOperand.SET_AUDIO_VOLUME_LEVEL);
        hdmiCecClient.checkExpectedMessageFromClient(LogicalAddress.AUDIO_SYSTEM, LogicalAddress.TV,
                CecOperand.FEATURE_ABORT);
        hdmiCecClient.checkExpectedOutput(hdmiCecClient.getSelfDevice(),
                CecOperand.GIVE_AUDIO_STATUS);
        hdmiCecClient.sendCecMessage(hdmiCecClient.getSelfDevice(), CecOperand.REPORT_AUDIO_STATUS,
                CecMessage.formatParams(50)); // Volume 50, mute off
        assertAdjustOnlyAbsoluteVolumeBehavior();

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
