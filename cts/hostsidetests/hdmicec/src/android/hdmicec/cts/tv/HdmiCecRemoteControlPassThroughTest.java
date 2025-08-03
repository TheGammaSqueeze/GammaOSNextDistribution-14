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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.hdmicec.cts.BaseHdmiCecCtsTest;
import android.hdmicec.cts.CecMessage;
import android.hdmicec.cts.CecOperand;
import android.hdmicec.cts.HdmiCecConstants;
import android.hdmicec.cts.LogicalAddress;
import android.hdmicec.cts.error.CecClientWrapperException;
import android.hdmicec.cts.error.ErrorCodes;

import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** HDMI CEC test to check Remote Control Pass Through behaviour (Sections 11.1.13) */
@RunWith(DeviceJUnit4ClassRunner.class)
public final class HdmiCecRemoteControlPassThroughTest extends BaseHdmiCecCtsTest {

    private static final int WAIT_TIME_MS = 1000;

    private HashMap<String, Integer> remoteControlKeys = new HashMap<String, Integer>();
    private HashMap<String, Integer> remoteControlAudioKeys = new HashMap<String, Integer>();

    @Rule
    public RuleChain ruleChain =
            RuleChain.outerRule(CecRules.requiresCec(this))
                    .around(CecRules.requiresLeanback(this))
                    .around(CecRules.requiresPhysicalDevice(this))
                    .around(CecRules.requiresDeviceType(this, HdmiCecConstants.CEC_DEVICE_TYPE_TV))
                    .around(hdmiCecClient);

    public HdmiCecRemoteControlPassThroughTest() {
        super(HdmiCecConstants.CEC_DEVICE_TYPE_TV, "-t", "r", "-t", "p", "-t", "t", "-t", "a");
        mapRemoteControlKeys();
    }

    @Before
    public void checkForInitialActiveSourceMessage() throws CecClientWrapperException {
        try {
            /*
             * Check for the broadcasted <ACTIVE_SOURCE> message from Recorder_1, which was sent as
             * a response to <SET_STREAM_PATH> message from the TV.
             */
            String message =
                    hdmiCecClient.checkExpectedMessageFromClient(
                            LogicalAddress.RECORDER_1, CecOperand.ACTIVE_SOURCE);
        } catch (CecClientWrapperException e) {
            if (e.getErrorCode() != ErrorCodes.CecMessageNotFound) {
                throw e;
            } else {
                /*
                 * In case the TV does not send <Set Stream Path> to CEC adapter, or the client does
                 * not make recorder active source, broadcast an <Active Source> message from the
                 * adapter.
                 */
                hdmiCecClient.broadcastActiveSource(
                        LogicalAddress.RECORDER_1, hdmiCecClient.getPhysicalAddress());
                try {
                    TimeUnit.MILLISECONDS.sleep(WAIT_TIME_MS);
                } catch (InterruptedException ex) {
                    // Do nothing
                }
            }
        }
    }

    /**
     * Test 11.1.13-1
     *
     * <p>Tests that the DUT sends the appropriate messages for remote control pass through to a
     * Recording Device.
     */
    @Test
    public void cect_11_1_13_1_RemoteControlMessagesToRecorder() throws Exception {
        hdmiCecClient.broadcastActiveSource(
                LogicalAddress.RECORDER_1, hdmiCecClient.getPhysicalAddress());
        TimeUnit.MILLISECONDS.sleep(WAIT_TIME_MS);
        validateKeyeventToUserControlPress(LogicalAddress.RECORDER_1, remoteControlKeys);
    }

    /**
     * Test 11.1.13-2
     *
     * <p>Tests that the DUT sends the appropriate messages for remote control pass through to a
     * Playback Device.
     */
    @Test
    public void cect_11_1_13_2_RemoteControlMessagesToPlayback() throws Exception {
        hdmiCecClient.broadcastActiveSource(
                LogicalAddress.PLAYBACK_1, hdmiCecClient.getPhysicalAddress());
        TimeUnit.MILLISECONDS.sleep(WAIT_TIME_MS);
        validateKeyeventToUserControlPress(LogicalAddress.PLAYBACK_1, remoteControlKeys);
    }

    /**
     * Test 11.1.13-3
     *
     * <p>Tests that the DUT sends the appropriate messages for remote control pass through to a
     * Tuner Device.
     */
    @Test
    public void cect_11_1_13_3_RemoteControlMessagesToTuner() throws Exception {
        hdmiCecClient.broadcastActiveSource(
                LogicalAddress.TUNER_1, hdmiCecClient.getPhysicalAddress());
        TimeUnit.MILLISECONDS.sleep(WAIT_TIME_MS);
        validateKeyeventToUserControlPress(LogicalAddress.TUNER_1, remoteControlKeys);
    }

    /**
     * Test 11.1.13-4
     *
     * <p>Tests that the DUT sends the appropriate messages for remote control pass through to an
     * Audio System.
     */
    @Test
    public void cect_11_1_13_4_RemoteControlMessagesToAudioSystem() throws Exception {
        hdmiCecClient.broadcastActiveSource(
                LogicalAddress.AUDIO_SYSTEM, hdmiCecClient.getPhysicalAddress());
        TimeUnit.MILLISECONDS.sleep(WAIT_TIME_MS);
        validateKeyeventToUserControlPress(LogicalAddress.AUDIO_SYSTEM, remoteControlAudioKeys);
    }

    /**
     * Test 11.1.13-5
     *
     * <p>Tests that the DUT behaves sensibly when the remote control pass through feature is
     * invoked in a system with multiple devices of the same type.
     */
    @Test
    public void cect_11_1_13_5_RemoteControlPassthroughWithMultipleDevices() throws Exception {
        hdmiCecClient.broadcastReportPhysicalAddress(LogicalAddress.RECORDER_1);
        hdmiCecClient.broadcastReportPhysicalAddress(LogicalAddress.RECORDER_2, 0x2100);
        validateMultipleKeyeventToUserControlPress(
                LogicalAddress.RECORDER_1, LogicalAddress.RECORDER_2);
    }

    private void mapRemoteControlKeys() {
        remoteControlKeys.put("DPAD_UP", HdmiCecConstants.CEC_KEYCODE_UP);
        remoteControlKeys.put("DPAD_DOWN", HdmiCecConstants.CEC_KEYCODE_DOWN);
        remoteControlKeys.put("DPAD_LEFT", HdmiCecConstants.CEC_KEYCODE_LEFT);
        remoteControlKeys.put("DPAD_RIGHT", HdmiCecConstants.CEC_KEYCODE_RIGHT);
        remoteControlAudioKeys.put("VOLUME_UP", HdmiCecConstants.CEC_KEYCODE_VOLUME_UP);
        remoteControlAudioKeys.put("VOLUME_DOWN", HdmiCecConstants.CEC_KEYCODE_VOLUME_DOWN);
        remoteControlAudioKeys.put("VOLUME_MUTE", HdmiCecConstants.CEC_KEYCODE_MUTE);
    }

    private void validateKeyeventToUserControlPress(LogicalAddress toDevice
            , HashMap<String, Integer> keyMaps) throws Exception {
        ITestDevice device = getDevice();
        for (String remoteKey : keyMaps.keySet()) {
            device.executeShellCommand("input keyevent KEYCODE_" + remoteKey);
            String message =
                    hdmiCecClient.checkExpectedOutput(toDevice, CecOperand.USER_CONTROL_PRESSED);
            assertThat(CecMessage.getParams(message)).isEqualTo(keyMaps.get(remoteKey));
            hdmiCecClient.checkExpectedOutput(toDevice, CecOperand.USER_CONTROL_RELEASED);
        }
    }

    private void validateMultipleKeyeventToUserControlPress(
            LogicalAddress device1, LogicalAddress device2) throws Exception {
        ITestDevice device = getDevice();
        for (String remoteKey : remoteControlKeys.keySet()) {
            List<LogicalAddress> destinationAddresses = new ArrayList<>();
            device.executeShellCommand("input keyevent KEYCODE_" + remoteKey);
            destinationAddresses =
                    hdmiCecClient.getAllDestLogicalAddresses(
                            CecOperand.USER_CONTROL_PRESSED,
                            CecMessage.formatParams(remoteControlKeys.get(remoteKey)),
                            4);
            assertWithMessage("UCP message forwarded to more than one device.")
                    .that(destinationAddresses.containsAll(Arrays.asList(device1, device2)))
                    .isFalse();
            assertWithMessage("UCP message was not forwarded to any of the device.")
                    .that(destinationAddresses)
                    .containsAnyIn(Arrays.asList(device1, device2));
        }
    }
}
