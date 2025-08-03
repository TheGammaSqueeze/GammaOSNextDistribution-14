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
import android.hdmicec.cts.CecOperand;
import android.hdmicec.cts.HdmiCecConstants;
import android.hdmicec.cts.LogicalAddress;

import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

/** Tests that check Wakeup behaviour of playback devices */
@RunWith(DeviceJUnit4ClassRunner.class)
public final class HdmiCecWakeupTest extends BaseHdmiCecCtsTest {

    public HdmiCecWakeupTest() {
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
     * Tests that the DUT does send an OTP to the TV when it is being woken up and
     * {@code POWER_CONTROL_MODE} is set to {@code TO_TV}.
     */
    @Test
    public void cectOtpOnWakeupWhenPowerControlModeToTv() throws Exception {
        String prevMode = setPowerControlMode(HdmiCecConstants.POWER_CONTROL_MODE_TV);
        try {
            sendDeviceToSleep();
            wakeUpDeviceWithoutWait();
            hdmiCecClient.checkExpectedOutput(LogicalAddress.TV, CecOperand.TEXT_VIEW_ON);
            hdmiCecClient.checkExpectedOutput(LogicalAddress.BROADCAST, CecOperand.ACTIVE_SOURCE);
        } finally {
            setPowerControlMode(prevMode);
        }
    }

    /**
     * Tests that the DUT does send an OTP to the TV when it is being woken up and
     * {@code POWER_CONTROL_MODE} is set to {@code TO_TV_AND_AUDIO_SYSTEM}.
     */
    @Test
    public void cectOtpOnWakeupWhenPowerControlModeToTvAndAudioSystem() throws Exception {
        String prevMode = setPowerControlMode(
                HdmiCecConstants.POWER_CONTROL_MODE_TV_AND_AUDIO_SYSTEM);
        try {
            sendDeviceToSleep();
            wakeUpDeviceWithoutWait();
            hdmiCecClient.checkExpectedOutput(LogicalAddress.TV, CecOperand.TEXT_VIEW_ON);
            hdmiCecClient.checkExpectedOutput(LogicalAddress.BROADCAST, CecOperand.ACTIVE_SOURCE);
        } finally {
            setPowerControlMode(prevMode);
        }
    }

    /**
     * Tests that the DUT does send an OTP to the TV when it is being woken up and
     * {@code POWER_CONTROL_MODE} is set to {@code BROADCAST}.
     */
    @Test
    public void cectOtpOnWakeupWhenPowerControlModeToBroadcast() throws Exception {
        String prevMode = setPowerControlMode(HdmiCecConstants.POWER_CONTROL_MODE_BROADCAST);
        try {
            sendDeviceToSleep();
            wakeUpDeviceWithoutWait();
            hdmiCecClient.checkExpectedOutput(LogicalAddress.TV, CecOperand.TEXT_VIEW_ON);
            hdmiCecClient.checkExpectedOutput(LogicalAddress.BROADCAST, CecOperand.ACTIVE_SOURCE);
        } finally {
            setPowerControlMode(prevMode);
        }
    }

    /**
     * Tests that the DUT does not send an OTP to the TV when it is being woken up and
     * {@code POWER_CONTROL_MODE} is set to {@code NONE}.
     */
    @Test
    public void cectNoOtpOnWakeupWhenPowerControlModeNone() throws Exception {
        String prevMode = setPowerControlMode(HdmiCecConstants.POWER_CONTROL_MODE_NONE);
        try {
            sendDeviceToSleep();
            wakeUpDeviceWithoutWait();
            hdmiCecClient.checkOutputDoesNotContainMessage(LogicalAddress.TV,
                    CecOperand.TEXT_VIEW_ON);
            hdmiCecClient.checkOutputDoesNotContainMessage(LogicalAddress.BROADCAST,
                    CecOperand.ACTIVE_SOURCE);
        } finally {
            setPowerControlMode(prevMode);
        }
    }
}
