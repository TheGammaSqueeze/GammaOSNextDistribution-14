/*
 * Copyright (C) 2021 The Android Open Source Project
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * HDMI CEC test to verify the device selection API for playback devices
 */
@RunWith(DeviceJUnit4ClassRunner.class)
public class HdmiCecDeviceSelectForPlaybackTest extends BaseHdmiCecCtsTest {

    private List<LogicalAddress> mPlaybackAddresses = new ArrayList<>();
    private LogicalAddress mDutLogicalAddress;
    private LogicalAddress mSecondPlayback;
    private LogicalAddress mThirdPlayback;

    public HdmiCecDeviceSelectForPlaybackTest() {
        super(HdmiCecConstants.CEC_DEVICE_TYPE_PLAYBACK_DEVICE);
    }

    @Rule
    public RuleChain ruleChain =
            RuleChain.outerRule(CecRules.requiresCec(this))
                    .around(CecRules.requiresLeanback(this))
                    .around(CecRules.requiresPhysicalDevice(this))
                    .around(
                            CecRules.requiresDeviceType(
                                    this, HdmiCecConstants.CEC_DEVICE_TYPE_PLAYBACK_DEVICE))
                    .around(hdmiCecClient);

    @Before
    public void setupLogicalAddresses() throws Exception {
        mPlaybackAddresses.addAll(
                Arrays.asList(
                        LogicalAddress.PLAYBACK_1,
                        LogicalAddress.PLAYBACK_2,
                        LogicalAddress.PLAYBACK_3));
        mDutLogicalAddress = getTargetLogicalAddress();
        mPlaybackAddresses.remove(mDutLogicalAddress);
        mSecondPlayback = mPlaybackAddresses.get(0);
        mThirdPlayback = mPlaybackAddresses.get(1);
    }

    private int getUnusedPhysicalAddress(int initialValue, int usedValue) {
        int address;
        if (initialValue == usedValue) {
            address = usedValue + 0x1000;
            return address < 0xFFFF ? address : 0xFFFF;
        }
        return initialValue;
    }

    private void reportPhysicalAddress(LogicalAddress logicalAddress, int physicalAddress,
            int deviceType) throws Exception {
        String formattedPhysicalAddress = CecMessage.formatParams(physicalAddress,
                HdmiCecConstants.PHYSICAL_ADDRESS_LENGTH);
        String formattedDeviceType = CecMessage.formatParams(deviceType);
        hdmiCecClient.sendCecMessage(
                logicalAddress,
                LogicalAddress.BROADCAST,
                CecOperand.REPORT_PHYSICAL_ADDRESS,
                formattedPhysicalAddress + formattedDeviceType
        );
    }

    /**
     * Tests that the DUT sends a {@code <Routing Change>} when a different device
     * from the network is selected.
     */
    @Test
    public void cectDeviceSelectDifferentSource() throws Exception {
        // Store previous power state change on active source lost.
        // Set the power state change to none, such that the device won't go to sleep when the
        // active source is changed.
        String previousPowerStateChange = setPowerStateChangeOnActiveSourceLost(
                HdmiCecConstants.POWER_STATE_CHANGE_ON_ACTIVE_SOURCE_LOST_NONE);
        try {
            int dumpsysPhysicalAddress = getDumpsysPhysicalAddress();
            // Add a second playback device in the network.
            int playback2PhysicalAddress = getUnusedPhysicalAddress(0x2200, dumpsysPhysicalAddress);
            reportPhysicalAddress(
                    mSecondPlayback,
                    playback2PhysicalAddress,
                    HdmiCecConstants.CEC_DEVICE_TYPE_PLAYBACK_DEVICE);
            // Make a third playback device the active source.
            int playback3PhysicalAddress = getUnusedPhysicalAddress(0x2300, dumpsysPhysicalAddress);
            reportPhysicalAddress(
                    mThirdPlayback,
                    playback3PhysicalAddress,
                    HdmiCecConstants.CEC_DEVICE_TYPE_PLAYBACK_DEVICE);
            hdmiCecClient.broadcastActiveSource(mThirdPlayback, playback3PhysicalAddress);
            // Wait for the <Active Source> message to be processed by the DUT.
            TimeUnit.SECONDS.sleep(HdmiCecConstants.DEVICE_WAIT_TIME_SECONDS);
            // Select second playback device and check if the expected message with the source and
            // the target physical addresses is sent.
            ITestDevice device = getDevice();
            device.executeShellCommand("cmd hdmi_control deviceselect " + mSecondPlayback);
            String message = hdmiCecClient.checkExpectedOutput(CecOperand.ROUTING_CHANGE);
            CecMessage.assertPhysicalAddressValid(message, playback3PhysicalAddress);
            CecMessage.assertTargetPhysicalAddressValid(message,
                    playback2PhysicalAddress);
        } finally {
            // Restore the previous power state change.
            setPowerStateChangeOnActiveSourceLost(previousPowerStateChange);
        }
    }

    /**
     * Tests that the DUT sends {@code <Text View On>} and {@code <Active Source>} messages
     * when it selects itself. The message is the result of an One Touch Play action.
     */
    @Test
    public void cectDeviceSelectSameSource() throws Exception {
        // If the DUT has a OneTouchPlayAction waiting for <Report Power Status>, finish it.
        // This test triggers its own OneTouchPlayAction, which fails if one is already in progress.
        hdmiCecClient.sendCecMessage(
                LogicalAddress.TV,
                mDutLogicalAddress,
                CecOperand.REPORT_POWER_STATUS,
                CecMessage.formatParams(HdmiCecConstants.CEC_POWER_STATUS_ON));

        int dumpsysPhysicalAddress = getDumpsysPhysicalAddress();
        int playback2PhysicalAddress = getUnusedPhysicalAddress(
                HdmiCecConstants.DEFAULT_PHYSICAL_ADDRESS, dumpsysPhysicalAddress);
        // Store previous power state change on active source lost.
        // Set the power state change to none, such that the device won't go to sleep when the
        // active source is changed.
        String previousPowerStateChange = setPowerStateChangeOnActiveSourceLost("none");
        try {
            // Make another playback device the active source.
            hdmiCecClient.broadcastActiveSource(mSecondPlayback, playback2PhysicalAddress);
            // Wait for the <Active Source> message to be processed by the DUT.
            TimeUnit.SECONDS.sleep(HdmiCecConstants.DEVICE_WAIT_TIME_SECONDS);
            // Select DUT and check if the expected <Text View On> and <Active Source>
            // messages are sent.
            ITestDevice device = getDevice();
            device.executeShellCommand("cmd hdmi_control deviceselect " + mDutLogicalAddress);
            hdmiCecClient.checkExpectedOutput(LogicalAddress.TV, CecOperand.TEXT_VIEW_ON);
            String message = hdmiCecClient.checkExpectedOutput(CecOperand.ACTIVE_SOURCE);
            CecMessage.assertPhysicalAddressValid(message, dumpsysPhysicalAddress);
        } finally {
            // Restore the previous power state change.
            setPowerStateChangeOnActiveSourceLost(previousPowerStateChange);
        }
    }
}
