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

import static com.google.common.truth.Truth.assertWithMessage;

import android.hdmicec.cts.BaseHdmiCecCtsTest;
import android.hdmicec.cts.CecOperand;
import android.hdmicec.cts.HdmiCecConstants;
import android.hdmicec.cts.LogicalAddress;

import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeUnit;

/** Tests that check Standby behaviour of playback devices */
@RunWith(DeviceJUnit4ClassRunner.class)
public final class HdmiCecStandbyTest extends BaseHdmiCecCtsTest {

    @Rule
    public RuleChain ruleChain =
            RuleChain.outerRule(CecRules.requiresCec(this))
                    .around(CecRules.requiresLeanback(this))
                    .around(CecRules.requiresPhysicalDevice(this))
                    .around(
                            CecRules.requiresDeviceType(
                                    this, HdmiCecConstants.CEC_DEVICE_TYPE_PLAYBACK_DEVICE))
                    .around(hdmiCecClient);

    private void sendStandbyAndCheckNoStandbySent(LogicalAddress destAddress) throws Exception {
        hdmiCecClient.broadcastActiveSource(LogicalAddress.TV);
        TimeUnit.SECONDS.sleep(HdmiCecConstants.DEVICE_WAIT_TIME_SECONDS);
        assertWithMessage("Device should not have been active source!")
                .that(isDeviceActiveSource(getDevice()))
                .isFalse();

        try {
            sendDeviceToSleep();
            hdmiCecClient.checkOutputDoesNotContainMessage(destAddress, CecOperand.STANDBY);
        } finally {
            wakeUpDevice();
        }
    }

    /**
     * Tests that the DUT does not send a {@code <STANDBY>} to the TV when it is turned off, and is
     * not the active source.
     */
    @Test
    public void cectNoTvStandbyWhenNotActiveSource() throws Exception {
        String prevMode = setPowerControlMode(HdmiCecConstants.POWER_CONTROL_MODE_TV);
        sendStandbyAndCheckNoStandbySent(LogicalAddress.TV);
        setPowerControlMode(prevMode);
    }

    /**
     * Tests that the DUT does not broadcast a {@code <STANDBY>} when it is turned off, and is not
     * the active source.
     */
    @Test
    public void cectNoBroadcastStandbyWhenNotActiveSource() throws Exception {
        String prevMode = setPowerControlMode(HdmiCecConstants.POWER_CONTROL_MODE_BROADCAST);
        sendStandbyAndCheckNoStandbySent(LogicalAddress.BROADCAST);
        setPowerControlMode(prevMode);
    }
}
