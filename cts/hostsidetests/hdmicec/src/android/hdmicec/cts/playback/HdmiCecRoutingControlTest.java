/*
 * Copyright (C) 2019 The Android Open Source Project
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

import android.hdmicec.cts.BaseHdmiCecCtsTest;
import android.hdmicec.cts.CecMessage;
import android.hdmicec.cts.CecOperand;
import android.hdmicec.cts.HdmiCecConstants;
import android.hdmicec.cts.LogicalAddress;
import android.hdmicec.cts.WakeLockHelper;

import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeUnit;

/** HDMI CEC test to test routing control (Section 11.2.2) */
@RunWith(DeviceJUnit4ClassRunner.class)
public final class HdmiCecRoutingControlTest extends BaseHdmiCecCtsTest {

    private static final int PHYSICAL_ADDRESS = 0x1000;

    public HdmiCecRoutingControlTest() {
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

    /**
     * Test 11.1.2-2, HF4-7-2
     *
     * Tests that the device does not respond to a {@code <Request Active Source>} message
     * when it is not the current active source.
     */
    @Test
    public void cect_11_1_2_2_RequestActiveSource() throws Exception {
        ITestDevice device = getDevice();

        hdmiCecClient.sendCecMessage(
                LogicalAddress.TV,
                LogicalAddress.BROADCAST,
                CecOperand.ACTIVE_SOURCE,
                CecMessage.formatParams(HdmiCecConstants.TV_PHYSICAL_ADDRESS,
                        HdmiCecConstants.PHYSICAL_ADDRESS_LENGTH));

        TimeUnit.SECONDS.sleep(HdmiCecConstants.DEVICE_WAIT_TIME_SECONDS);

        String isActiveSource = device.executeShellCommand(
                "dumpsys hdmi_control | grep \"isActiveSource()\"");
        assertThat(isActiveSource.trim()).isEqualTo("isActiveSource(): false");

        hdmiCecClient.sendCecMessage(LogicalAddress.TV, LogicalAddress.BROADCAST,
                CecOperand.REQUEST_ACTIVE_SOURCE);

        hdmiCecClient.checkOutputDoesNotContainMessage(
                LogicalAddress.BROADCAST, CecOperand.ACTIVE_SOURCE);
    }

    /**
     * Test 11.2.2-1
     * Tests that the device broadcasts a <ACTIVE_SOURCE> in response to a <SET_STREAM_PATH>, when
     * the TV has switched to a different input.
     */
    @Test
    public void cect_11_2_2_1_SetStreamPathToDut() throws Exception {
        final long alternateAddress;
        int dumpsysPhysicalAddress = getDumpsysPhysicalAddress();
        if (dumpsysPhysicalAddress == 0x1000) {
            alternateAddress = 0x2000;
        } else {
            alternateAddress = 0x1000;
        }
        /*
         * Switch to HDMI port whose physical address is alternateAddress. DUT is connected to HDMI
         * port whose physical address is dumpsysPhysicalAddress.
         */
        hdmiCecClient.sendCecMessage(LogicalAddress.PLAYBACK_2, LogicalAddress.BROADCAST,
                CecOperand.ACTIVE_SOURCE, CecMessage.formatParams(alternateAddress));
        TimeUnit.SECONDS.sleep(3);
        hdmiCecClient.sendCecMessage(LogicalAddress.TV, LogicalAddress.BROADCAST,
                CecOperand.SET_STREAM_PATH,
                CecMessage.formatParams(dumpsysPhysicalAddress));
        String message = hdmiCecClient.checkExpectedOutput(CecOperand.ACTIVE_SOURCE);
        CecMessage.assertPhysicalAddressValid(message, dumpsysPhysicalAddress);
    }

    /**
     * Test 11.2.2-2
     * Tests that the device broadcasts a <ACTIVE_SOURCE> in response to a <REQUEST_ACTIVE_SOURCE>.
     * This test depends on One Touch Play, and will pass only if One Touch Play passes.
     */
    @Test
    public void cect_11_2_2_2_RequestActiveSource() throws Exception {
        ITestDevice device = getDevice();
        device.executeShellCommand("input keyevent KEYCODE_HOME");
        hdmiCecClient.sendCecMessage(LogicalAddress.TV, LogicalAddress.BROADCAST,
            CecOperand.REQUEST_ACTIVE_SOURCE);
        String message = hdmiCecClient.checkExpectedOutput(CecOperand.ACTIVE_SOURCE);
        CecMessage.assertPhysicalAddressValid(message, getDumpsysPhysicalAddress());
    }

    /**
     * Test 11.2.2-4
     * Tests that the device sends a <INACTIVE_SOURCE> message when put on standby.
     */
    @Test
    public void cect_11_2_2_4_InactiveSourceOnStandby() throws Exception {
        String previousPowerControlMode =
                setPowerControlMode(HdmiCecConstants.POWER_CONTROL_MODE_NONE);
        try {
            int dumpsysPhysicalAddress = getDumpsysPhysicalAddress();
            hdmiCecClient.sendCecMessage(
                    LogicalAddress.TV,
                    LogicalAddress.BROADCAST,
                    CecOperand.SET_STREAM_PATH,
                    CecMessage.formatParams(dumpsysPhysicalAddress));
            TimeUnit.SECONDS.sleep(5);
            sendDeviceToSleepWithoutWait();
            String message = hdmiCecClient.checkExpectedOutput(LogicalAddress.TV,
                    CecOperand.INACTIVE_SOURCE);
            CecMessage.assertPhysicalAddressValid(message, dumpsysPhysicalAddress);
        } finally {
            /* Wake up the device */
            wakeUpDevice();
            setPowerControlMode(previousPowerControlMode);
        }
    }

    /**
     * Tests that if the device is configured to go to sleep when losing the active source status,
     * it behaves as expected.
     */
    @Test
    public void testPowerStateChangeOnActiveSourceLost_standby() throws Exception {
        String previousActionOnActiveSourceLost = setPowerStateChangeOnActiveSourceLost(
                HdmiCecConstants.POWER_STATE_CHANGE_ON_ACTIVE_SOURCE_LOST_STANDBY_NOW);
        try {
            ITestDevice device = getDevice();
            // Make sure that the DUT is the active source
            device.executeShellCommand("input keyevent KEYCODE_HOME");
            TimeUnit.SECONDS.sleep(HdmiCecConstants.DEVICE_WAIT_TIME_SECONDS);
            WakeLockHelper.acquirePartialWakeLock(device);
            // Now make the TV the active source
            hdmiCecClient.sendCecMessage(LogicalAddress.TV, LogicalAddress.BROADCAST,
                    CecOperand.ACTIVE_SOURCE, CecMessage.formatParams("0000"));
            assertDeviceWakefulness(HdmiCecConstants.WAKEFULNESS_ASLEEP);
        } finally {
            /* Wake up the device */
            wakeUpDevice();
            setPowerStateChangeOnActiveSourceLost(previousActionOnActiveSourceLost);
        }
    }

    /**
     * Tests that if the device is configured not to go to sleep when losing the active source
     * status, it behaves as expected.
     */
    @Test
    public void testPowerStateChangeOnActiveSourceLost_none() throws Exception {
        String previousActionOnActiveSourceLost = setPowerStateChangeOnActiveSourceLost(
                HdmiCecConstants.POWER_STATE_CHANGE_ON_ACTIVE_SOURCE_LOST_NONE);
        try {
            ITestDevice device = getDevice();
            // Make sure that the DUT is the active source
            device.executeShellCommand("input keyevent KEYCODE_HOME");
            TimeUnit.SECONDS.sleep(HdmiCecConstants.DEVICE_WAIT_TIME_SECONDS);
            WakeLockHelper.acquirePartialWakeLock(device);
            // Now make the TV the active source
            hdmiCecClient.sendCecMessage(LogicalAddress.TV, LogicalAddress.BROADCAST,
                    CecOperand.ACTIVE_SOURCE, CecMessage.formatParams("0000"));
            assertDeviceWakefulness(HdmiCecConstants.WAKEFULNESS_AWAKE);
        } finally {
            /* Wake up the device */
            wakeUpDevice();
            setPowerStateChangeOnActiveSourceLost(previousActionOnActiveSourceLost);
        }
    }
}
