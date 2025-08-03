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

package android.hdmicec.cts.common;

import android.hdmicec.cts.BaseHdmiCecCtsTest;

import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

/** HDMI CEC tests related to polling */
@RunWith(DeviceJUnit4ClassRunner.class)
public final class HdmiCecPollingTest extends BaseHdmiCecCtsTest {

    @Rule
    public RuleChain ruleChain =
            RuleChain
                    .outerRule(CecRules.requiresCec(this))
                    .around(CecRules.requiresLeanback(this))
                    .around(CecRules.requiresPhysicalDevice(this))
                    .around(hdmiCecClient);

    /**
     * Test 11.2.6-1
     * Tests for Ack {@code <Polling Message>} message.
     */
    @Test
    public void cect_11_2_6_1_Ack() throws Exception {
        String expectedOutput = "POLL message sent";
        hdmiCecClient.sendPoll();
        if (!hdmiCecClient.checkConsoleOutput(expectedOutput)) {
            throw new Exception("Could not find " + expectedOutput);
        }
    }

    /**
     * Test HF4-2-10
     * Verify {@code <Polling Message>} message is acknowledged in all states.
     *
     * Explicitly changes that polling messages are handled in standby power states.
     */
    @Test
    public void cect_hf4_2_10_Ack() throws Exception {
        setCec20();

        ITestDevice device = getDevice();
        try {
            sendDeviceToSleep();
            String expectedOutput = "POLL message sent";
            hdmiCecClient.sendPoll();
            if (!hdmiCecClient.checkConsoleOutput(expectedOutput)) {
                throw new Exception("Could not find " + expectedOutput);
            }
        } finally {
            wakeUpDevice();
        }
    }
}
