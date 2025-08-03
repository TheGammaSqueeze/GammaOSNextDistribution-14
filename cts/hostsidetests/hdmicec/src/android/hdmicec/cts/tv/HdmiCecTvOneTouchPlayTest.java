/*
 * Copyright 2020 The Android Open Source Project
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

import android.hdmicec.cts.BaseHdmiCecCtsTest;
import android.hdmicec.cts.CecMessage;
import android.hdmicec.cts.CecOperand;
import android.hdmicec.cts.HdmiCecConstants;
import android.hdmicec.cts.HdmiControlManagerUtility;
import android.hdmicec.cts.LogicalAddress;

import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** HDMI CEC test to test One Touch Play features (Section 11.1.1) */
@RunWith(DeviceJUnit4ClassRunner.class)
public class HdmiCecTvOneTouchPlayTest extends BaseHdmiCecCtsTest {

    private static final int WAIT_TIME_MS = 1000;

    private static final int SLEEP_TIMESTEP_SECONDS = 1;
    private static final int POWER_TRANSITION_WAIT_TIME = 10;
    private static final int MAX_POWER_TRANSITION_WAIT_TIME = 15;

    List<LogicalAddress> testDevices = new ArrayList<>();

    public HdmiCecTvOneTouchPlayTest() {
        /* Start the client as recorder, tuner and playback devices */
        super(HdmiCecConstants.CEC_DEVICE_TYPE_TV, "-t", "r", "-t", "t", "-t", "p");
        testDevices.add(LogicalAddress.RECORDER_1);
        testDevices.add(LogicalAddress.TUNER_1);
        testDevices.add(LogicalAddress.PLAYBACK_1);
    }

    @Rule
    public RuleChain ruleChain =
            RuleChain.outerRule(CecRules.requiresCec(this))
                    .around(CecRules.requiresLeanback(this))
                    .around(CecRules.requiresPhysicalDevice(this))
                    .around(CecRules.requiresDeviceType(this, HdmiCecConstants.CEC_DEVICE_TYPE_TV))
                    .around(hdmiCecClient);

    /**
     * Test 11.1.1-1
     *
     * <p>Tests that the DUT responds to {@code <Image View On>} message correctly when the message
     * is sent from logical addresses 0x1, 0x3 and 0x4
     */
    @Test
    public void cect_11_1_1_1_RespondToImageViewOn() throws Exception {
        for (LogicalAddress testDevice : testDevices) {
            hdmiCecClient.sendCecMessage(testDevice, LogicalAddress.TV, CecOperand.IMAGE_VIEW_ON);
            TimeUnit.MILLISECONDS.sleep(WAIT_TIME_MS);
            hdmiCecClient.broadcastActiveSource(testDevice, hdmiCecClient.getPhysicalAddress());
            hdmiCecClient.checkOutputDoesNotContainMessage(testDevice, CecOperand.FEATURE_ABORT);
            assertWithMessage(
                            "Device has not registered expected logical address as active source.")
                    .that(getDumpsysActiveSourceLogicalAddress())
                    .isEqualTo(testDevice);
        }
    }

    /**
     * Test 11.1.1-2
     *
     * <p>Tests that the DUT responds to {@code <Text View On>} message correctly when the message
     * is sent from logical addresses 0x1, 0x3 and 0x4
     */
    @Test
    public void cect_11_1_1_2_RespondToTextViewOn() throws Exception {
        for (LogicalAddress testDevice : testDevices) {
            hdmiCecClient.sendCecMessage(testDevice, LogicalAddress.TV, CecOperand.TEXT_VIEW_ON);
            TimeUnit.MILLISECONDS.sleep(WAIT_TIME_MS);
            hdmiCecClient.broadcastActiveSource(testDevice, hdmiCecClient.getPhysicalAddress());
            hdmiCecClient.checkOutputDoesNotContainMessage(testDevice, CecOperand.FEATURE_ABORT);
            assertWithMessage(
                            "Device has not registered expected logical address as active source.")
                    .that(getDumpsysActiveSourceLogicalAddress())
                    .isEqualTo(testDevice);
        }
    }

    /**
     * Test 11.1.1-5
     *
     * <p>Tests that the DUT broadcasts an {@code <Active Source>} message when changing to an
     * internal source from previously displaying an external source.
     */
    @Test
    public void cect_11_1_1_5_DutBroadcastsActiveSourceWhenChangingToInternal() throws Exception {
        // Ensure that an external source is the active source.
        try {
            /*
             * Check for the broadcasted <ACTIVE_SOURCE> message from Recorder_1, which was sent as
             * a response to <SET_STREAM_PATH> message from the TV.
             */
            String message =
                    hdmiCecClient.checkExpectedMessageFromClient(
                            LogicalAddress.RECORDER_1, CecOperand.ACTIVE_SOURCE);
        } catch (Exception e) {
            /*
             * In case the TV does not send <Set Stream Path> to CEC adapter, or the client does
             * not make recorder active source, broadcast an <Active Source> message from the
             * adapter.
             */
            hdmiCecClient.broadcastActiveSource(
                    LogicalAddress.RECORDER_1, hdmiCecClient.getPhysicalAddress());
            TimeUnit.MILLISECONDS.sleep(WAIT_TIME_MS);
        }
        // Make the TV device the active source.
        HdmiControlManagerUtility.selectDevice(this, getDevice(), LogicalAddress.TV.toString());
        hdmiCecClient.checkExpectedOutput(LogicalAddress.BROADCAST, CecOperand.ACTIVE_SOURCE);
    }

    /**
     * Test 11.1.1-3
     *
     * <p>Tests that the DUT powers on in response to an {@code <Image View On>} message when in
     * standby
     */
    @Test
    public void cect_11_1_1_3_ImageViewOnWhenInStandby() throws Exception {
        try {
            getDevice().reboot();
            sendDeviceToSleep();
            assertDevicePowerStatus(HdmiCecConstants.CEC_POWER_STATUS_STANDBY);
            /* Get the first device the client has started as */
            LogicalAddress testDevice = testDevices.get(0);
            hdmiCecClient.sendCecMessage(testDevice, LogicalAddress.TV, CecOperand.IMAGE_VIEW_ON);
            assertDevicePowerStatus(HdmiCecConstants.CEC_POWER_STATUS_ON);
        } finally {
            wakeUpDevice();
        }
    }

    /**
     * Test 11.1.1-4
     *
     * <p>Tests that the DUT powers on in response to an {@code <Text View On>} message when in
     * standby
     */
    @Test
    public void cect_11_1_1_4_TextViewOnWhenInStandby() throws Exception {
        try {
            getDevice().reboot();
            sendDeviceToSleep();
            assertDevicePowerStatus(HdmiCecConstants.CEC_POWER_STATUS_STANDBY);
            /* Get the first device the client has started as */
            LogicalAddress testDevice = testDevices.get(0);
            hdmiCecClient.sendCecMessage(testDevice, LogicalAddress.TV, CecOperand.TEXT_VIEW_ON);
            assertDevicePowerStatus(HdmiCecConstants.CEC_POWER_STATUS_ON);
        } finally {
            wakeUpDevice();
        }
    }

    private void assertDevicePowerStatus(int powerStatus) throws Exception {
        String[] powerStatusNames = {"ON", "OFF", "IN_TRANSITION_TO_ON", "IN_TRANSITION_TO_OFF"};
        LogicalAddress cecClientDevice = hdmiCecClient.getSelfDevice();
        int actualPowerStatus;
        int waitTimeSeconds = POWER_TRANSITION_WAIT_TIME;

        /* Wait for the device to transition */
        TimeUnit.SECONDS.sleep(waitTimeSeconds);

        do {
            TimeUnit.SECONDS.sleep(SLEEP_TIMESTEP_SECONDS);
            waitTimeSeconds += SLEEP_TIMESTEP_SECONDS;
            hdmiCecClient.sendCecMessage(cecClientDevice, CecOperand.GIVE_POWER_STATUS);
            actualPowerStatus =
                    CecMessage.getParams(
                            hdmiCecClient.checkExpectedOutput(
                                    cecClientDevice, CecOperand.REPORT_POWER_STATUS));
            /* Compare with (powerStatus + 2) to check if it is transitioning to the expected power
             * status.
             */
        } while (actualPowerStatus == (powerStatus + 2)
                && waitTimeSeconds <= MAX_POWER_TRANSITION_WAIT_TIME);
        assertWithMessage(
                        "Device power status is "
                                + powerStatusNames[actualPowerStatus]
                                + " but expected to be "
                                + powerStatusNames[powerStatus])
                .that(actualPowerStatus)
                .isEqualTo(powerStatus);
    }
}
