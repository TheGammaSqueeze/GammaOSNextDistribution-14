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

import static android.hdmicec.cts.HdmiCecConstants.TIMEOUT_SAFETY_MS;

import static com.google.common.truth.Truth.assertThat;

import android.hdmicec.cts.BaseHdmiCecCtsTest;
import android.hdmicec.cts.CecMessage;
import android.hdmicec.cts.CecOperand;
import android.hdmicec.cts.HdmiCecConstants;
import android.hdmicec.cts.LogicalAddress;

import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.util.RunUtil;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.List;

/** HDMI CEC system information tests (Section 11.2.6) */
@RunWith(DeviceJUnit4ClassRunner.class)
public final class HdmiCecSystemInformationTest extends BaseHdmiCecCtsTest {

    @Rule
    public RuleChain ruleChain =
            RuleChain
                    .outerRule(CecRules.requiresCec(this))
                    .around(CecRules.requiresLeanback(this))
                    .around(CecRules.requiresPhysicalDevice(this))
                    .around(hdmiCecClient);

    /**
     * Tests 11.2.6-2, 10.1.1.1-1
     *
     * <p>Tests that the device sends a {@code <Report Physical Address>} in response to a {@code
     * <Give Physical Address>}
     */
    @Test
    public void cect_11_2_6_2_GivePhysicalAddress() throws Exception {
        List<LogicalAddress> testDevices =
                Arrays.asList(
                        LogicalAddress.TV,
                        LogicalAddress.RECORDER_1,
                        LogicalAddress.TUNER_1,
                        LogicalAddress.PLAYBACK_1,
                        LogicalAddress.AUDIO_SYSTEM,
                        LogicalAddress.BROADCAST);
        for (LogicalAddress testDevice : testDevices) {
            if (hasLogicalAddress(testDevice)) {
                /* Skip the DUT logical address */
                continue;
            }
            hdmiCecClient.sendCecMessage(testDevice, CecOperand.GIVE_PHYSICAL_ADDRESS);
            String message = hdmiCecClient.checkExpectedOutput(CecOperand.REPORT_PHYSICAL_ADDRESS);
            /* Check that the physical address taken is valid. */
            CecMessage.assertPhysicalAddressValid(message, getDumpsysPhysicalAddress());
            int receivedParams = CecMessage.getParams(message);
            assertThat(hasDeviceType(receivedParams & 0xFF)).isTrue();
        }
    }

    /**
     * Tests {@code <Report Features>}
     *
     * <p>Tests that the device reports the correct information in {@code <Report Features>} in
     * response to a {@code <Give Features>} message.
     */
    @Test
    public void cect_reportFeatures_deviceTypeContainedInAllDeviceTypes() throws Exception {
        setCec20();
        List<LogicalAddress> testDevices =
                Arrays.asList(
                        LogicalAddress.TV,
                        LogicalAddress.RECORDER_1,
                        LogicalAddress.TUNER_1,
                        LogicalAddress.PLAYBACK_1,
                        LogicalAddress.AUDIO_SYSTEM,
                        LogicalAddress.BROADCAST);
        for (LogicalAddress testDevice : testDevices) {
            if (hasLogicalAddress(testDevice)) {
                /* Skip the DUT logical address */
                continue;
            }
            hdmiCecClient.sendCecMessage(testDevice, CecOperand.GIVE_FEATURES);
            String message = hdmiCecClient.checkExpectedOutput(CecOperand.REPORT_FEATURES);
            int receivedParams = CecMessage.getParams(message, 2, 4);

            int deviceType = 0;
            for (LogicalAddress address : mDutLogicalAddresses) {
                switch (address.getDeviceType()) {
                    case HdmiCecConstants.CEC_DEVICE_TYPE_PLAYBACK_DEVICE:
                        deviceType |= 1 << 4;
                        break;
                    case HdmiCecConstants.CEC_DEVICE_TYPE_TV:
                        deviceType |= 1 << 7;
                        break;
                    case HdmiCecConstants.CEC_DEVICE_TYPE_AUDIO_SYSTEM:
                        deviceType |= 1 << 3;
                        break;
                    case HdmiCecConstants.CEC_DEVICE_TYPE_RECORDER:
                        deviceType |= 1 << 6;
                        break;
                    case HdmiCecConstants.CEC_DEVICE_TYPE_TUNER:
                        deviceType |= 1 << 5;
                        break;
                    case HdmiCecConstants.CEC_DEVICE_TYPE_RESERVED:
                        break;
                }
            }

            assertThat(receivedParams & deviceType).isNotEqualTo(1);
        }
    }

    /**
     * Test 11.2.6-6
     * Tests that the device sends a {@code <CEC Version>} in response to a {@code <Get CEC
     * Version>}
     */
    @Test
    public void cect_11_2_6_6_GiveCecVersion() throws Exception {
        int cecVersion = HdmiCecConstants.CEC_VERSION_1_4;

        hdmiCecClient.sendCecMessage(hdmiCecClient.getSelfDevice(), CecOperand.GET_CEC_VERSION);
        String message =
                hdmiCecClient.checkExpectedOutput(
                        hdmiCecClient.getSelfDevice(), CecOperand.CEC_VERSION);
        assertThat(CecMessage.getParams(message)).isEqualTo(cecVersion);
    }

    /**
     * Test HF4-2-12
     * Tests that the device sends a {@code <CEC Version>} with correct version argument in
     * response to a {@code <Get CEC Version>} message.
     *
     * Also verifies that the CEC version reported in {@code <Report Features>} matches the CEC
     * version reported in {@code <CEC Version>}.
     */
    @Test
    public void cect_hf4_2_12_GiveCecVersion() throws Exception {
        int cecVersion = HdmiCecConstants.CEC_VERSION_2_0;
        setCec20();

        hdmiCecClient.sendCecMessage(hdmiCecClient.getSelfDevice(), CecOperand.GET_CEC_VERSION);
        String reportCecVersion = hdmiCecClient.checkExpectedOutput(hdmiCecClient.getSelfDevice(),
                CecOperand.CEC_VERSION);
        assertThat(CecMessage.getParams(reportCecVersion)).isEqualTo(cecVersion);

        RunUtil.getDefault().sleep(TIMEOUT_SAFETY_MS);

        hdmiCecClient.sendCecMessage(hdmiCecClient.getSelfDevice(), CecOperand.GIVE_FEATURES);
        String reportFeatures = hdmiCecClient.checkExpectedOutput(LogicalAddress.BROADCAST,
                CecOperand.REPORT_FEATURES);
        assertThat(CecMessage.getParams(reportFeatures, 2)).isEqualTo(cecVersion);
    }

    /**
     * Tests that the device sends a {@code <CEC Version>} in response to a {@code <Get CEC
     * Version>} in standby
     */
    @Test
    public void cectGiveCecVersionInStandby() throws Exception {
        ITestDevice device = getDevice();
        try {
            sendDeviceToSleepAndValidate();
            hdmiCecClient.sendCecMessage(hdmiCecClient.getSelfDevice(), CecOperand.GET_CEC_VERSION);
            String message =
                    hdmiCecClient.checkExpectedOutputOrFeatureAbort(
                            hdmiCecClient.getSelfDevice(),
                            CecOperand.CEC_VERSION,
                            CecOperand.GET_CEC_VERSION,
                            HdmiCecConstants.ABORT_NOT_IN_CORRECT_MODE);
            assertThat(CecMessage.getParams(message))
                    .isIn(
                            Arrays.asList(
                                    HdmiCecConstants.CEC_VERSION_2_0,
                                    HdmiCecConstants.CEC_VERSION_1_4));
        } finally {
            wakeUpDevice();
        }
    }

    /**
     * Test HF4-2-16 (CEC 2.0)
     *
     * <p>Tests that the DUT responds to a {@code <Give Device Vendor Id>} with a {@code <Device
     * Vendor ID>} message or a {@code <Feature Abort>[Unrecognized Opcode]}
     */
    @Test
    public void cect_hf_4_2_16_GiveDeviceVendorId() throws Exception {
        ITestDevice device = getDevice();
        setCec20();
        hdmiCecClient.sendCecMessage(
                hdmiCecClient.getSelfDevice(), CecOperand.GIVE_DEVICE_VENDOR_ID);
        String message =
                hdmiCecClient.checkExpectedOutputOrFeatureAbort(
                        LogicalAddress.BROADCAST,
                        CecOperand.DEVICE_VENDOR_ID,
                        CecOperand.GIVE_DEVICE_VENDOR_ID,
                        HdmiCecConstants.ABORT_UNRECOGNIZED_MODE);
        if (CecMessage.getOperand(message) == CecOperand.GIVE_DEVICE_VENDOR_ID) {
            assertThat(CecMessage.getParams(message))
                    .isNotEqualTo(HdmiCecConstants.INVALID_VENDOR_ID);
        }
    }

    /**
     * Test HF4-2-17 (CEC 2.0)
     *
     * <p>Tests that the DUT responds to a {@code <Vendor Command with Id>} that has an incorrect or
     * unrecognised Vendor ID with a {@code <Feature Abort>} message with an appropriate reason.
     */
    @Test
    public void cect_hf_4_2_17_VendorCommandWithIncorrectId() throws Exception {
        ITestDevice device = getDevice();
        setCec20();
        long vendorId = 0xBADDAD;
        String vendorCommandParams =
                CecMessage.formatParams(vendorId, 6) + CecMessage.formatParams("01DBF7E498");
        String featureAbortRefused =
                CecOperand.VENDOR_COMMAND_WITH_ID.toString()
                        + String.format("%02d", HdmiCecConstants.ABORT_REFUSED);
        String featureAbortUnrecognised =
                CecOperand.VENDOR_COMMAND_WITH_ID.toString()
                        + String.format("%02d", HdmiCecConstants.ABORT_UNRECOGNIZED_MODE);

        hdmiCecClient.sendCecMessage(
                hdmiCecClient.getSelfDevice(), CecOperand.GIVE_DEVICE_VENDOR_ID);

        String message =
                hdmiCecClient.checkExpectedOutput(
                        LogicalAddress.BROADCAST, CecOperand.DEVICE_VENDOR_ID);
        if (CecMessage.getParams(message) == vendorId) {
            // Device has the same vendor ID used in test, change it.
            vendorId += 1;
        }
        LogicalAddress cecClientDevice = hdmiCecClient.getSelfDevice();
        hdmiCecClient.sendCecMessage(
                cecClientDevice,
                LogicalAddress.BROADCAST,
                CecOperand.DEVICE_VENDOR_ID,
                CecMessage.formatParams(vendorId, 6));
        hdmiCecClient.sendCecMessage(
                cecClientDevice, CecOperand.VENDOR_COMMAND_WITH_ID, vendorCommandParams);
        message = hdmiCecClient.checkExpectedOutput(cecClientDevice, CecOperand.FEATURE_ABORT);
        if (!CecMessage.getParamsAsString(message).equals(featureAbortRefused)
                && !CecMessage.getParamsAsString(message).equals(featureAbortUnrecognised)) {
            throw new Exception("Feature Abort reason is not REFUSED(0x04) or UNRECOGNIZED(0x00)");
        }
    }
}
