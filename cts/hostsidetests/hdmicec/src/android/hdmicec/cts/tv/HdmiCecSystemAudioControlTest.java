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

package android.hdmicec.cts.tv;

import static com.google.common.truth.Truth.assertWithMessage;

import android.hdmicec.cts.AudioManagerHelper;
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

/** HDMI CEC test to verify system audio control commands tests (Section 11.1.15) */
@RunWith(DeviceJUnit4ClassRunner.class)
public final class HdmiCecSystemAudioControlTest extends BaseHdmiCecCtsTest {

    private static final int ON = 0x1;
    private static final int OFF = 0x0;

    @Rule
    public RuleChain ruleChain =
            RuleChain.outerRule(CecRules.requiresCec(this))
                    .around(CecRules.requiresLeanback(this))
                    .around(CecRules.requiresPhysicalDevice(this))
                    .around(CecRules.requiresDeviceType(this, HdmiCecConstants.CEC_DEVICE_TYPE_TV))
                    .around(hdmiCecClient);

    public HdmiCecSystemAudioControlTest() {
        super(HdmiCecConstants.CEC_DEVICE_TYPE_TV, "-t", "a", "-t", "r");
    }

    /**
     * Test 11.1.15-1
     *
     * <p>Tests that the DUT sends a correctly formatted {@code <System Audio Mode Request>}
     * message.
     */
    @Test
    public void cect_11_1_15_1_DutSendsSystemAudioModeRequest() throws Exception {
        // Ensure that system audio mode is off before testing 11.1.15-5.
        setSystemAudioMode(false);

        hdmiCecClient.broadcastReportPhysicalAddress(LogicalAddress.AUDIO_SYSTEM);
        hdmiCecClient.broadcastReportPhysicalAddress(
                LogicalAddress.RECORDER_1, hdmiCecClient.getPhysicalAddress());
        hdmiCecClient.sendCecMessage(LogicalAddress.RECORDER_1, CecOperand.IMAGE_VIEW_ON);
        hdmiCecClient.broadcastActiveSource(
                LogicalAddress.RECORDER_1, hdmiCecClient.getPhysicalAddress());
        /*
         * Invoke the DUT to turn on the system audio mode and check for system audio mode request
         * message with params.
         */
        assertWithMessage("Device did not send a <System Audio Mode Request> message with params")
                .that(setSystemAudioMode(true))
                .isTrue();
    }

    /**
     * Test 11.1.15-5
     *
     * <p>Tests that the DUT sends a correctly formatted {@code <System Audio Mode Request>} message
     * when the DUT invokes the System Audio Mode to be Off.
     */
    @Test
    public void cect_11_1_15_5_DutResponseWhenSystemAudioModeToOff() throws Exception {
        // Ensure that system audio mode is on.
        setSystemAudioMode(true);
        /*
         * Invoke the DUT to turn off the system audio mode and check for system audio mode request
         * message with no params.
         */
        assertWithMessage(
                        "Device did not send a <System Audio Mode Request> message with no params")
                .that(setSystemAudioMode(false))
                .isTrue();
    }

    /**
     * Test 11.1.15-7
     *
     * <p>Tests that the DUT mutes its volume when the DUT receives a broadcast {@code <Set System
     * Audio Mode>} ["On"] message
     */
    @Ignore("b/174733146")
    @Test
    public void cect_11_1_15_7_DutMutesForSetSystemAudioModeOn() throws Exception {
        /*
         * TODO: Call HdmiCecLocalDeviceTv.setSystemAudioMode(false) instead to turn off system
         * audio mode after permission issue is sorted.
         */
        hdmiCecClient.sendCecMessage(
                hdmiCecClient.getSelfDevice(),
                LogicalAddress.BROADCAST,
                CecOperand.SET_SYSTEM_AUDIO_MODE,
                CecMessage.formatParams(OFF));
        hdmiCecClient.sendCecMessage(
                hdmiCecClient.getSelfDevice(),
                LogicalAddress.BROADCAST,
                CecOperand.SET_SYSTEM_AUDIO_MODE,
                CecMessage.formatParams(ON));
        assertWithMessage("Device is not muted")
                .that(AudioManagerHelper.isDeviceMuted(getDevice()))
                .isTrue();
    }

    /**
     * Test 11.1.15-8
     *
     * <p>Tests that the DUT unmutes its volume when the DUT receives a broadcast {@code <Set System
     * Audio Mode>} ["Off"] message
     */
    @Ignore("b/174733146")
    @Test
    public void cect_11_1_15_8_DutUnmutesForSetSystemAudioModeOff() throws Exception {
        /*
         * TODO: Call HdmiCecLocalDeviceTv.setSystemAudioMode(true) instead to turn off system
         * audio mode after permission issue is sorted.
         */
        hdmiCecClient.sendCecMessage(
                hdmiCecClient.getSelfDevice(),
                LogicalAddress.BROADCAST,
                CecOperand.SET_SYSTEM_AUDIO_MODE,
                CecMessage.formatParams(ON));
        hdmiCecClient.sendCecMessage(
                hdmiCecClient.getSelfDevice(),
                LogicalAddress.BROADCAST,
                CecOperand.SET_SYSTEM_AUDIO_MODE,
                CecMessage.formatParams(OFF));
        assertWithMessage("Device is muted")
                .that(AudioManagerHelper.isDeviceMuted(getDevice()))
                .isFalse();
    }

    private boolean setSystemAudioMode(boolean enabled) throws Exception {
        getDevice().executeShellCommand("cmd hdmi_control setsam " + (enabled ? "on" : "off"));
        try {
            String message =
                    hdmiCecClient.checkExpectedOutput(
                            LogicalAddress.AUDIO_SYSTEM, CecOperand.SYSTEM_AUDIO_MODE_REQUEST);
            /*
             * When system audio mode is turned off. DUT should send <System Audio Mode Request>
             * message with no params. And when it is turned on, DUT should send the same message
             * with params.
             */
            return enabled ^ CecMessage.getParamsAsString(message).equals("");
        } catch (Exception e) {
            return false;
        }
    }
}
