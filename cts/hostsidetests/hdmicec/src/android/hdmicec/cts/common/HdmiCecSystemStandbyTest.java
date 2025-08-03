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

package android.hdmicec.cts.common;

import android.hdmicec.cts.BaseHdmiCecCtsTest;
import android.hdmicec.cts.CecOperand;
import android.hdmicec.cts.HdmiCecConstants;
import android.hdmicec.cts.LogicalAddress;
import android.hdmicec.cts.WakeLockHelper;

import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * HDMI CEC test to verify the device handles standby correctly (Section 11.1.3, 11.2.3)
 */
@RunWith(DeviceJUnit4ClassRunner.class)
public final class HdmiCecSystemStandbyTest extends BaseHdmiCecCtsTest {

    private static final String TV_SEND_STANDBY_ON_SLEEP = "tv_send_standby_on_sleep";
    private static final String TV_SEND_STANDBY_ON_SLEEP_ENABLED = "1";
    private static final String TV_SEND_STANDBY_ON_SLEEP_DISABLED = "0";

    public List<LogicalAddress> mLogicalAddresses = new ArrayList<>();
    public boolean previousDeviceAutoOff;
    public String previousPowerControlMode;

    @Rule
    public RuleChain ruleChain =
            RuleChain
                    .outerRule(CecRules.requiresCec(this))
                    .around(CecRules.requiresLeanback(this))
                    .around(CecRules.requiresPhysicalDevice(this))
                    .around(hdmiCecClient);

    @Before
    public void initialTestSetup() throws Exception {
        defineLogicalAddressList();
        previousDeviceAutoOff = setHdmiControlDeviceAutoOff(false);
        previousPowerControlMode = setPowerControlMode(HdmiCecConstants.POWER_CONTROL_MODE_NONE);
    }

    @After
    public void resetDutState() throws Exception {
        /* Wake up the device */
        wakeUpDevice();
        setHdmiControlDeviceAutoOff(previousDeviceAutoOff);
        setPowerControlMode(previousPowerControlMode);
    }

    /**
     * Test 11.1.3-2, 11.2.3-2<br>
     * Tests that the device goes into standby when a {@code <STANDBY>} message is broadcast.
     */
    @Test
    public void cect_HandleBroadcastStandby() throws Exception {
        ITestDevice device = getDevice();
        device.reboot();
        TimeUnit.SECONDS.sleep(5);
        for (LogicalAddress source : mLogicalAddresses) {
            if (!hasLogicalAddress(source)) {
                WakeLockHelper.acquirePartialWakeLock(device);
                hdmiCecClient.sendCecMessage(source, LogicalAddress.BROADCAST, CecOperand.STANDBY);
                checkStandbyAndWakeUp();
            }
        }
    }

    /**
     * Test 11.1.3-3, 11.2.3-3<br>
     * Tests that the device goes into standby when a {@code <STANDBY>} message is sent to it.
     */
    @Test
    public void cect_HandleAddressedStandby() throws Exception {
        ITestDevice device = getDevice();
        device.reboot();
        for (LogicalAddress source : mLogicalAddresses) {
            if (!hasLogicalAddress(source)) {
                WakeLockHelper.acquirePartialWakeLock(device);
                hdmiCecClient.sendCecMessage(source, CecOperand.STANDBY);
                checkStandbyAndWakeUp();
            }
        }
    }

    /**
     * Test 11.2.3-4<br>
     * Tests that the device does not broadcast a {@code <STANDBY>} when going into standby mode.
     */
    @Test
    public void cect_11_2_3_4_NoBroadcastStandby() throws Exception {
        /*
         * CEC CTS does not specify for TV a no broadcast on standby test. On Android TVs, there is
         * a feature to turn off this standby broadcast and this test tests the same.
         */
        sendDeviceToSleep();
        try {
            hdmiCecClient.checkOutputDoesNotContainMessage(
                    LogicalAddress.BROADCAST, CecOperand.STANDBY);
        } finally {
            wakeUpDevice();
        }
    }

    private void defineLogicalAddressList() throws Exception {
        /* TODO: b/174279917 Add LogicalAddress.BROADCAST to this list as well. */
        mLogicalAddresses.add(LogicalAddress.TV);
        mLogicalAddresses.add(LogicalAddress.RECORDER_1);
        mLogicalAddresses.add(LogicalAddress.TUNER_1);
        mLogicalAddresses.add(LogicalAddress.PLAYBACK_1);
        mLogicalAddresses.add(LogicalAddress.AUDIO_SYSTEM);

        if (hasDeviceType(HdmiCecConstants.CEC_DEVICE_TYPE_TV)) {
            //Add logical addresses 13, 14 only for TV panel tests.
            mLogicalAddresses.add(LogicalAddress.RESERVED_2);
            mLogicalAddresses.add(LogicalAddress.SPECIFIC_USE);
        }
    }

    private boolean setHdmiControlDeviceAutoOff(boolean turnOn) throws Exception {
        String val = getSettingsValue(TV_SEND_STANDBY_ON_SLEEP);
        setSettingsValue(TV_SEND_STANDBY_ON_SLEEP, turnOn ? TV_SEND_STANDBY_ON_SLEEP_ENABLED
                                                          : TV_SEND_STANDBY_ON_SLEEP_DISABLED);
        return val == TV_SEND_STANDBY_ON_SLEEP_ENABLED;
    }
}
