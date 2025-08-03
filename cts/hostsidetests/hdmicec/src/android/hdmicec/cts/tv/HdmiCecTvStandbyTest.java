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

import android.hdmicec.cts.BaseHdmiCecCtsTest;
import android.hdmicec.cts.CecOperand;
import android.hdmicec.cts.HdmiCecConstants;
import android.hdmicec.cts.LogicalAddress;

import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;


/** HDMI CEC tests for system standby features (Section 11.1.3) */
@RunWith(DeviceJUnit4ClassRunner.class)
public class HdmiCecTvStandbyTest extends BaseHdmiCecCtsTest {

    private static final String TV_SEND_STANDBY_ON_SLEEP = "tv_send_standby_on_sleep";
    private static final String TV_SEND_STANDBY_ON_SLEEP_ENABLED = "1";

    public HdmiCecTvStandbyTest() {
        super(HdmiCecConstants.CEC_DEVICE_TYPE_TV);
    }

    @Rule
    public RuleChain ruleChain =
            RuleChain.outerRule(CecRules.requiresCec(this))
                    .around(CecRules.requiresLeanback(this))
                    .around(CecRules.requiresPhysicalDevice(this))
                    .around(CecRules.requiresDeviceType(this, HdmiCecConstants.CEC_DEVICE_TYPE_TV))
                    .around(hdmiCecClient);

    /**
     * Test 11.1.3-1
     *
     * <p>Tests that the DUT broadcasts a {@code <Standby>} message correctly and goes into standby
     * when standby is initiated.
     */
    @Test
    public void cect_11_1_3_1_BroadcastStandby() throws Exception {
        ITestDevice device = getDevice();
        device.waitForBootComplete(HdmiCecConstants.REBOOT_TIMEOUT);
        String value = getSettingsValue(TV_SEND_STANDBY_ON_SLEEP);
        setSettingsValue(TV_SEND_STANDBY_ON_SLEEP, TV_SEND_STANDBY_ON_SLEEP_ENABLED);
        try {
            sendDeviceToSleepWithoutWait();
            hdmiCecClient.checkExpectedOutput(LogicalAddress.BROADCAST, CecOperand.STANDBY);
        } finally {
            wakeUpDevice();
            setSettingsValue(TV_SEND_STANDBY_ON_SLEEP, value);
        }
    }
}
