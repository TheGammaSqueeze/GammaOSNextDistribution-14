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

package android.hdmicec.cts;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assume.assumeTrue;

import org.junit.Before;

/**
 * <pre>
 * Abstract base class for tests for absolute volume behavior (AVB). Subclasses must call
 * this class's constructor to specify the device type of the DUT and the System Audio device.
 *
 * The three valid pairs of (DUT type, System Audio device type) for AVB are as follows:
 * (Playback, TV); (Playback, Audio System); (TV, Audio System).
 *
 * Unfortunately, it is infeasible to test cases where the System Audio device is an Audio System,
 * because the CEC adapter responds <Feature Abort> to <Set Audio Volume Level> when it is started
 * as an Audio System.
 *
 * However, there is a special case for the (TV, Audio System) pair that can be tested:
 * When the DUT is a TV, it may adopt *adjust-only* absolute volume behavior if all of the
 * conditions for absolute volume behavior are met, except the Audio System does not support
 * incoming <Set Audio Volume Level> messages.
 *
 * To summarize, these are all of the valid the (DUT, System Audio device, volume behavior) triplets
 * and how/whether they can be tested:
 *
 * - (Playback, TV,           absolute volume behavior):
 *   Tested in {@link android.hdmicec.cts.playback.HdmiCecAvbToTvTest}
 *
 * - (Playback, Audio System, absolute volume behavior):
 *   Infeasible to test because CEC adapter feature aborts <Set Audio Volume Level>
 *
 * - (TV,       Audio System, absolute volume behavior):
 *   Infeasible to test because CEC adapter feature aborts <Set Audio Volume Level>
 *
 * - (TV,       Audio System, adjust-only absolute volume behavior):
 *   Tested in {@link android.hdmicec.cts.tv.HdmiCecAvbToAudioSystemTest}
 * </pre>
 */
public abstract class BaseHdmiCecAbsoluteVolumeBehaviorTest extends BaseHdmiCecCtsTest {

    /**
     * Constructor. The test device type and client params (determining the client's device type)
     * passed in here determine the behavior of the tests.
     */
    public BaseHdmiCecAbsoluteVolumeBehaviorTest(@HdmiCecConstants.CecDeviceType int testDeviceType,
            String... clientParams) {
        super(testDeviceType, clientParams);
    }

    /**
     * Returns the audio output device being used.
     */
    public int getAudioOutputDevice() {
        if (mTestDeviceType == HdmiCecConstants.CEC_DEVICE_TYPE_TV) {
            return HdmiCecConstants.DEVICE_OUT_HDMI_ARC;
        } else {
            return HdmiCecConstants.DEVICE_OUT_HDMI;
        }
    }

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();

        // This setting must be enabled to use AVB. Start with it disabled to ensure that we can
        // control when the AVB initiation process starts.
        setSettingsValue(HdmiCecConstants.SETTING_VOLUME_CONTROL_ENABLED,
                HdmiCecConstants.VOLUME_CONTROL_DISABLED);

        // Disable and enable CEC on the DUT to clear its knowledge of device feature support.
        // If the DUT isn't a TV, simulate a connected sink as well.
        if (mTestDeviceType == HdmiCecConstants.CEC_DEVICE_TYPE_TV) {
            getDevice().executeShellCommand("cmd hdmi_control cec_setting set hdmi_cec_enabled 0");
            waitForCondition(() -> !isCecEnabled(getDevice()), "Could not disable CEC");
            getDevice().executeShellCommand("cmd hdmi_control cec_setting set hdmi_cec_enabled 1");
            waitForCondition(() -> isCecEnabled(getDevice()), "Could not enable CEC");
        } else {
            simulateCecSinkConnected(getDevice(), getTargetLogicalAddress());
        }

        // Full volume behavior is a prerequisite for AVB. However, we cannot control this
        // condition from CTS tests or shell due to missing permissions. Therefore, we run these
        // tests only if it is already true.
        assumeTrue(isFullVolumeDevice(getAudioOutputDevice()));
    }

    /**
     * Enables System Audio Mode if the System Audio device is an Audio System.
     */
    protected void enableSystemAudioModeIfApplicable() throws Exception {
        if (hdmiCecClient.getSelfDevice() == LogicalAddress.AUDIO_SYSTEM) {
            broadcastSystemAudioModeMessage(true);
        }
    }

    /**
     * Has the CEC client broadcast a message enabling or disabling System Audio Mode.
     */
    protected void broadcastSystemAudioModeMessage(boolean val) throws Exception {
        hdmiCecClient.sendCecMessage(
                hdmiCecClient.getSelfDevice(),
                LogicalAddress.BROADCAST,
                CecOperand.SET_SYSTEM_AUDIO_MODE,
                CecMessage.formatParams(val ? 1 : 0));
    }

    /**
     * Has the CEC client send a <Report Features> message expressing support or lack of support for
     * <Set Audio Volume Level>.
     */
    protected void sendReportFeatures(boolean setAudioVolumeLevelSupport) throws Exception {
        String deviceTypeNibble = hdmiCecClient.getSelfDevice() == LogicalAddress.TV
                ? "80" : "08";
        String featureSupportNibble = setAudioVolumeLevelSupport ? "01" : "00";
        hdmiCecClient.sendCecMessage(
                hdmiCecClient.getSelfDevice(),
                LogicalAddress.BROADCAST,
                CecOperand.REPORT_FEATURES,
                CecMessage.formatParams("06" + deviceTypeNibble + "00" + featureSupportNibble));
    }

    protected void assertAbsoluteVolumeBehavior() throws Exception {
        waitForCondition(() -> isAbsoluteVolumeDevice(getAudioOutputDevice()),
                "Not using absolute volume behavior");
    }

    protected void assertAdjustOnlyAbsoluteVolumeBehavior() throws Exception {
        waitForCondition(() -> isAdjustOnlyAbsoluteVolumeDevice(getAudioOutputDevice()),
                "Not using adjust-only absolute volume behavior");
    }

    protected void assertFullVolumeBehavior() throws Exception {
        waitForCondition(() -> isFullVolumeDevice(getAudioOutputDevice()),
                "Not using full absolute volume behavior");
    }

    /**
     * Asserts that the DUT's volume (scale: [0, 100]) is within 5 points of an expected volume.
     * This accounts for small differences due to rounding when converting between volume scales.
     * Also asserts that the DUT's mute status is equal to {@code expectedMute}.
     *
     * Asserting both volume and mute at the same time saves a shell command because both are
     * conveyed in a single log message.
     */
    protected void assertApproximateDeviceVolumeAndMute(int expectedVolume, boolean expectedMute)
            throws Exception {
        // Raw output is equal to volume out of 100, plus 128 if muted
        // In practice, if the stream is muted, volume equals 0, so this will be at most 128
        int rawOutput = AudioManagerHelper.getDutAudioVolume(getDevice());

        int actualVolume = rawOutput % 128;
        assertWithMessage("Expected DUT to have volume " + expectedVolume
                + " but was actually " + actualVolume)
                .that(Math.abs(expectedVolume - actualVolume) <= 5)
                .isTrue();

        boolean actualMute = rawOutput >= 128;
        String expectedMuteString = expectedMute ? "muted" : "unmuted";
        String actualMuteString = actualMute ? "muted" : "unmuted";
        assertWithMessage("Expected DUT to be " + expectedMuteString
                + "but was actually " + actualMuteString)
                .that(expectedMute)
                .isEqualTo(actualMute);
    }
}
