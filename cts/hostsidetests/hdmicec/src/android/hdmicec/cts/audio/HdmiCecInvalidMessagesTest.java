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

package android.hdmicec.cts.audio;


import android.hdmicec.cts.BaseHdmiCecCtsTest;
import android.hdmicec.cts.CecMessage;
import android.hdmicec.cts.CecOperand;
import android.hdmicec.cts.HdmiCecConstants;
import android.hdmicec.cts.LogicalAddress;
import android.hdmicec.cts.error.CecClientWrapperException;
import android.hdmicec.cts.error.ErrorCodes;

import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

/** HDMI CEC test to verify that device ignores invalid messages (Section 12) */
@Ignore("b/162820841")
@RunWith(DeviceJUnit4ClassRunner.class)
public final class HdmiCecInvalidMessagesTest extends BaseHdmiCecCtsTest {

    private static final LogicalAddress AUDIO_DEVICE = LogicalAddress.AUDIO_SYSTEM;

    /** The package name of the APK. */
    private static final String PACKAGE = "android.hdmicec.app";

    /** The class name of the main activity in the APK. */
    private static final String CLASS = "HdmiCecKeyEventCapture";

    /** The command to launch the main activity. */
    private static final String START_COMMAND = String.format(
            "am start -W -a android.intent.action.MAIN -n %s/%s.%s", PACKAGE, PACKAGE, CLASS);

    /** The command to clear the main activity. */
    private static final String CLEAR_COMMAND = String.format("pm clear %s", PACKAGE);

    public HdmiCecInvalidMessagesTest() {
        super(HdmiCecConstants.CEC_DEVICE_TYPE_AUDIO_SYSTEM);
    }

    @Rule
    public RuleChain ruleChain =
            RuleChain.outerRule(CecRules.requiresCec(this))
                    .around(CecRules.requiresLeanback(this))
                    .around(CecRules.requiresPhysicalDevice(this))
                    .around(
                            CecRules.requiresDeviceType(
                                    this, HdmiCecConstants.CEC_DEVICE_TYPE_AUDIO_SYSTEM))
                    .around(hdmiCecClient);

    private void checkArcIsInitiated() throws CecClientWrapperException {
        try {
            hdmiCecClient.sendCecMessage(LogicalAddress.TV, AUDIO_DEVICE,
                CecOperand.REQUEST_ARC_INITIATION);
            hdmiCecClient.checkExpectedOutput(LogicalAddress.TV, CecOperand.INITIATE_ARC);
        } catch (CecClientWrapperException e) {
            if (e.getErrorCode() != ErrorCodes.CecMessageNotFound) {
                throw e;
            }
        }
    }

    /**
     * Test 12-2
     * Tests that the device ignores directly addressed message <GIVE_AUDIO_STATUS> if received as
     * a broadcast message
     */
    @Test
    public void cect_12_2_DirectlyAddressedReceivedAsBroadcast_giveAudioStatus() throws Exception {
        hdmiCecClient.sendCecMessage(
                LogicalAddress.TV,
                LogicalAddress.BROADCAST,
                CecOperand.GIVE_AUDIO_STATUS);
        hdmiCecClient.checkOutputDoesNotContainMessage(
                LogicalAddress.TV,
                CecOperand.REPORT_AUDIO_STATUS);
    }

    /**
     * Test 12-2
     * Tests that the device ignores directly addressed message <GIVE_SYSTEM_AUDIO_MODE_STATUS> if
     * received as a broadcast message
     */
    @Test
    public void cect_12_2_DirectlyAddressedReceivedAsBroadcast_giveSystemAudioModeStatus()
        throws Exception {
        hdmiCecClient.sendCecMessage(
                LogicalAddress.TV,
                LogicalAddress.BROADCAST,
                CecOperand.GIVE_SYSTEM_AUDIO_MODE_STATUS);
        hdmiCecClient.checkOutputDoesNotContainMessage(
                LogicalAddress.TV,
                CecOperand.SYSTEM_AUDIO_MODE_STATUS);
    }

    /**
     * Test 12-2
     * Tests that the device ignores directly addressed message <REQUEST_SHORT_AUDIO_DESCRIPTOR> if
     * received as a broadcast message
     */
    @Test
    public void cect_12_2_DirectlyAddressedReceivedAsBroadcast_requestShortAudioDescriptor()
        throws Exception {
        hdmiCecClient.sendCecMessage(
                LogicalAddress.TV,
                LogicalAddress.BROADCAST,
                CecOperand.REQUEST_SHORT_AUDIO_DESCRIPTOR,
                CecMessage.formatParams("01020304"));
        hdmiCecClient.checkOutputDoesNotContainMessage(
                LogicalAddress.TV,
                CecOperand.REPORT_SHORT_AUDIO_DESCRIPTOR);
    }

    /**
     * Test 12-2
     * Tests that the device ignores directly addressed message <SYSTEM_AUDIO_MODE_REQUEST> if
     * received as a broadcast message
     */
    @Test
    public void cect_12_2_DirectlyAddressedReceivedAsBroadcast_systemAudioModeRequest()
        throws Exception {
        hdmiCecClient.sendCecMessage(
                LogicalAddress.TV,
                LogicalAddress.BROADCAST,
                CecOperand.SYSTEM_AUDIO_MODE_REQUEST);
        hdmiCecClient.checkOutputDoesNotContainMessage(
                LogicalAddress.BROADCAST,
                CecOperand.SET_SYSTEM_AUDIO_MODE);
    }

    /**
     * Test 12-2
     * Tests that the device ignores directly addressed message <REQUEST_ARC_INITIATION> if
     * received as a broadcast message
     */
    @Test
    public void cect_12_2_DirectlyAddressedReceivedAsBroadcast_requestArcInitiation()
        throws Exception {
        hdmiCecClient.sendCecMessage(
                LogicalAddress.TV,
                LogicalAddress.BROADCAST,
                CecOperand.REQUEST_ARC_INITIATION);
        hdmiCecClient.checkOutputDoesNotContainMessage(
                LogicalAddress.BROADCAST,
                CecOperand.INITIATE_ARC);
    }

    /**
     * Test 12-2
     * Tests that the device ignores directly addressed message <REQUEST_ARC_TERMINATION> if
     * received as a broadcast message
     */
    @Test
    public void cect_12_2_DirectlyAddressedReceivedAsBroadcast_requestArcTermination()
        throws Exception {
        checkArcIsInitiated();
        hdmiCecClient.sendCecMessage(
                LogicalAddress.TV,
                LogicalAddress.BROADCAST,
                CecOperand.REQUEST_ARC_TERMINATION);
        hdmiCecClient.checkOutputDoesNotContainMessage(
                LogicalAddress.BROADCAST,
                CecOperand.TERMINATE_ARC);
    }
}
