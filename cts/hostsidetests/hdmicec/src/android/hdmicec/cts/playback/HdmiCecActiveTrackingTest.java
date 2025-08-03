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

import static com.google.common.truth.Truth.assertThat;

import android.hdmicec.cts.BaseHdmiCecCtsTest;
import android.hdmicec.cts.CecMessage;
import android.hdmicec.cts.CecOperand;
import android.hdmicec.cts.HdmiCecConstants;
import android.hdmicec.cts.LogicalAddress;

import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeUnit;

/**
 * HDMI CEC tests verifying the active tracking mechanism of the CEC network for Playback devices
 */
@RunWith(DeviceJUnit4ClassRunner.class)
public final class HdmiCecActiveTrackingTest extends BaseHdmiCecCtsTest {
    // Delay to allow the DUT to poll all the non-local logical addresses (seconds)
    private static final int POLLING_WAIT_TIME = 5;
    // Delay to wait for the HotplugDetectionAction to start (milliseconds)
    private static final int HOTPLUG_WAIT_TIME = 60000;

    public HdmiCecActiveTrackingTest() {
        super(HdmiCecConstants.CEC_DEVICE_TYPE_PLAYBACK_DEVICE);
    }

    @Rule
    public RuleChain ruleChain =
            RuleChain
                    .outerRule(CecRules.requiresCec(this))
                    .around(CecRules.requiresLeanback(this))
                    .around(CecRules.requiresPhysicalDevice(this))
                    .around(CecRules.requiresDeviceType(
                            this, HdmiCecConstants.CEC_DEVICE_TYPE_PLAYBACK_DEVICE))
                    .around(hdmiCecClient);

    private int createUnusedPhysicalAddress(int usedPhysicalAddress) {
        if (usedPhysicalAddress == 0x2000) {
            return 0x2200;
        }
        return 0x2000;
    }

    /**
     * Tests that the DUT removes a device from the network, when it doesn't answer to the polling
     * message sent by HotplugDetectionAction.
     */
    @Test
    public void cect_RemoveDeviceFromNetwork() throws Exception {
        // Wait for the device discovery action to pass.
        TimeUnit.SECONDS.sleep(POLLING_WAIT_TIME);
        // Add an external playback device to the network.
        int playback2PhysicalAddress = createUnusedPhysicalAddress(getDumpsysPhysicalAddress());
        String formattedPhysicalAddress = CecMessage.formatParams(playback2PhysicalAddress,
                HdmiCecConstants.PHYSICAL_ADDRESS_LENGTH);
        String formattedDeviceType = CecMessage.formatParams(
                HdmiCecConstants.CEC_DEVICE_TYPE_PLAYBACK_DEVICE);
        hdmiCecClient.sendCecMessage(
                LogicalAddress.PLAYBACK_2,
                LogicalAddress.BROADCAST,
                CecOperand.REPORT_PHYSICAL_ADDRESS,
                formattedPhysicalAddress + formattedDeviceType
        );
        String deviceName = "Playback_2";
        hdmiCecClient.sendCecMessage(
                LogicalAddress.PLAYBACK_2,
                LogicalAddress.PLAYBACK_1,
                CecOperand.SET_OSD_NAME,
                CecMessage.convertStringToHexParams(deviceName)
        );
        // Wait for the first HotplugDetectionAction to start and leave enough time to poll all
        // devices once.
        hdmiCecClient.checkExpectedOutput(LogicalAddress.SPECIFIC_USE, CecOperand.POLL,
                HOTPLUG_WAIT_TIME + HdmiCecConstants.DEVICE_WAIT_TIME_MS);
        TimeUnit.SECONDS.sleep(HdmiCecConstants.DEVICE_WAIT_TIME_SECONDS);
        String deviceList = getDeviceList();
        assertThat(deviceList).doesNotContain(deviceName);
    }
}
