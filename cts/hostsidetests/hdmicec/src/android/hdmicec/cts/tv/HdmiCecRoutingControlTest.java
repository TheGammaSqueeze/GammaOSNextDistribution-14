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

import android.hdmicec.cts.BaseHdmiCecCtsTest;
import android.hdmicec.cts.CecMessage;
import android.hdmicec.cts.CecOperand;
import android.hdmicec.cts.HdmiCecConstants;
import android.hdmicec.cts.HdmiControlManagerUtility;
import android.hdmicec.cts.LogicalAddress;
import android.hdmicec.cts.error.CecClientWrapperException;
import android.hdmicec.cts.error.ErrorCodes;

import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeUnit;

/** HDMI CEC Routing control tests (Section 11.1.2) */
@RunWith(DeviceJUnit4ClassRunner.class)
public final class HdmiCecRoutingControlTest extends BaseHdmiCecCtsTest {

    private static final int WAIT_TIME_MS = 1000;

    @Rule
    public RuleChain ruleChain =
            RuleChain.outerRule(CecRules.requiresCec(this))
                    .around(CecRules.requiresLeanback(this))
                    .around(CecRules.requiresPhysicalDevice(this))
                    .around(CecRules.requiresDeviceType(this, HdmiCecConstants.CEC_DEVICE_TYPE_TV))
                    .around(hdmiCecClient);

    public HdmiCecRoutingControlTest() {
        super(HdmiCecConstants.CEC_DEVICE_TYPE_TV, "-t", "r", "-t", "t", "-t", "p");
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
                        hdmiCecClient.getSelfDevice(), hdmiCecClient.getPhysicalAddress());
                try {
                    TimeUnit.MILLISECONDS.sleep(WAIT_TIME_MS);
                } catch (InterruptedException ex) {
                    // Do nothing
                }
            }
        }
    }

    /**
     * Test 11.1.2-1
     *
     * <p>Tests that the device sends a {@code <Set Stream Path>} message if the user selects
     * another source device.
     */
    @Test
    public void cect_11_1_2_1_DutSendsSetStreamPathMessage() throws Exception {
        // Broadcast a <Report Physical Address> [2.1.0.0] message from Logical Address 3.
        hdmiCecClient.broadcastReportPhysicalAddress(LogicalAddress.TUNER_1, 0x2100);
        // Broadcast a <Report Physical Address> [2.2.0.0] message from Logical Address 4.
        hdmiCecClient.broadcastReportPhysicalAddress(LogicalAddress.PLAYBACK_1, 0x2200);
        TimeUnit.SECONDS.sleep(2);
        // Make the device with LA 4 as the active source.
        HdmiControlManagerUtility.selectDevice(
                this, getDevice(), LogicalAddress.PLAYBACK_1.toString());
        String message = hdmiCecClient.checkExpectedOutput(CecOperand.SET_STREAM_PATH);
        assertWithMessage("Device has not sent a Set Stream Path message to the selected device")
                .that(CecMessage.getParams(message))
                .isEqualTo(0x2200);
    }

    /**
     * Test 11.1.2-2
     *
     * <p>Tests that the device doesn't respond to a {@code <Request Active Source>} message when it
     * is not the current active source.
     */
    @Test
    public void cect_11_1_2_2_DutDoesNotRespondToRequestActiveSourceMessage() throws Exception {
        // Ensure that DUT is the active source.
        HdmiControlManagerUtility.selectDevice(this, getDevice(), LogicalAddress.TV.toString());
        hdmiCecClient.checkExpectedOutput(CecOperand.ACTIVE_SOURCE);
        // Broadcast an active source from the client device.
        hdmiCecClient.broadcastActiveSource(hdmiCecClient.getSelfDevice());
        hdmiCecClient.sendCecMessage(
                hdmiCecClient.getSelfDevice(),
                LogicalAddress.BROADCAST,
                CecOperand.REQUEST_ACTIVE_SOURCE);
        hdmiCecClient.checkOutputDoesNotContainMessage(
                LogicalAddress.BROADCAST, CecOperand.ACTIVE_SOURCE);
    }

    /**
     * Test 11.1.2-3
     *
     * <p>Tests that the device responds correctly to a {@code <Request Active Source>} message when
     * it is the current active source.
     */
    @Test
    public void cect_11_1_2_3_DutDoesRespondToRequestActiveSourceMessage() throws Exception {
        // Make the TV device the active source.
        HdmiControlManagerUtility.selectDevice(this, getDevice(), LogicalAddress.TV.toString());
        hdmiCecClient.sendCecMessage(
                hdmiCecClient.getSelfDevice(),
                LogicalAddress.BROADCAST,
                CecOperand.REQUEST_ACTIVE_SOURCE);
        hdmiCecClient.checkExpectedOutput(CecOperand.ACTIVE_SOURCE);
    }

    /**
     * Test 11.1.2-4
     *
     * <p>Tests that the device accepts {@code <Inactive Source>} message.
     */
    @Test
    public void cect_11_1_2_4_DutAcceptsInactiveSourceMessage() throws Exception {
        hdmiCecClient.sendCecMessage(
                hdmiCecClient.getSelfDevice(),
                LogicalAddress.TV,
                CecOperand.INACTIVE_SOURCE,
                CecMessage.formatParams(hdmiCecClient.getPhysicalAddress()));
        hdmiCecClient.checkOutputDoesNotContainMessage(
                hdmiCecClient.getSelfDevice(),
                CecOperand.FEATURE_ABORT,
                CecMessage.formatParams(CecOperand.INACTIVE_SOURCE.toString()));
    }
}
