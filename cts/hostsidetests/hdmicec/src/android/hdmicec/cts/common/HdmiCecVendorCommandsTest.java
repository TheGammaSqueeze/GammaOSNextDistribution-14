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

import static com.google.common.truth.Truth.assertThat;

import android.hdmicec.cts.BaseHdmiCecCtsTest;
import android.hdmicec.cts.CecMessage;
import android.hdmicec.cts.CecOperand;
import android.hdmicec.cts.HdmiControlManagerUtility;
import android.hdmicec.cts.LogHelper;
import android.hdmicec.cts.LogicalAddress;

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

/** HDMI CEC test to verify device vendor specific commands (Section 11.2.9) */
@RunWith(DeviceJUnit4ClassRunner.class)
public final class HdmiCecVendorCommandsTest extends BaseHdmiCecCtsTest {

    private static final int INCORRECT_VENDOR_ID = 0x0;

    private static final String VENDOR_LISTENER_WITH_ID =
            "-a android.hdmicec.app.VENDOR_LISTENER_WITH_ID";
    private static final String VENDOR_LISTENER_WITHOUT_ID =
            "-a android.hdmicec.app.VENDOR_LISTENER_WITHOUT_ID";

    /** Log confirmation message after listener registration. */
    private static final String REGISTERED_LISTENER = "Registered vendor command listener";

    /** The TAG that the test class will use. */
    private static final String TEST_LOG_TAG = "HdmiControlManagerHelper";

    /**
     * This has to be the same as the vendor ID used in the instrumentation test {@link
     * HdmiControlManagerHelper#VENDOR_ID}
     */
    private static final int VENDOR_ID = 0xBADDAD;

    @Rule
    public RuleChain ruleChain =
        RuleChain
            .outerRule(CecRules.requiresCec(this))
            .around(CecRules.requiresLeanback(this))
            .around(CecRules.requiresPhysicalDevice(this))
            .around(hdmiCecClient);

    /**
     * Test 11.2.9-1
     * <p>Tests that the device responds to a {@code <GIVE_DEVICE_VENDOR_ID>} from various source
     * devices with a {@code <DEVICE_VENDOR_ID>}.
     */
    @Test
    public void cect_11_2_9_1_GiveDeviceVendorId() throws Exception {
        for (LogicalAddress logicalAddress : LogicalAddress.values()) {
            // Skip the logical address of this device
            if (hasLogicalAddress(logicalAddress)) {
                continue;
            }
            hdmiCecClient.sendCecMessage(logicalAddress, CecOperand.GIVE_DEVICE_VENDOR_ID);
            String message = hdmiCecClient.checkExpectedOutput(CecOperand.DEVICE_VENDOR_ID);
            assertThat(CecMessage.getParams(message)).isNotEqualTo(INCORRECT_VENDOR_ID);
        }
    }

    /**
     * Tests that the device responds to a {@code <GIVE_DEVICE_VENDOR_ID>} when in standby.
     */
    @Test
    public void cectGiveDeviceVendorIdDuringStandby() throws Exception {
        ITestDevice device = getDevice();
        try {
            sendDeviceToSleepAndValidate();
            cect_11_2_9_1_GiveDeviceVendorId();
        } finally {
            wakeUpDevice();
        }
    }

    /**
     * Test 11.2.9-2
     * <p>Tests that the device broadcasts a {@code <DEVICE_VENDOR_ID>} message after successful
     * initialisation and address allocation.
     */
    @Test
    public void cect_11_2_9_2_DeviceVendorIdOnInit() throws Exception {
        ITestDevice device = getDevice();
        device.reboot();
        String message = hdmiCecClient.checkExpectedOutput(CecOperand.DEVICE_VENDOR_ID);
        assertThat(CecMessage.getParams(message)).isNotEqualTo(INCORRECT_VENDOR_ID);
    }

    /* The four following tests test the registration of a callback, and if the callback is received
     * when the DUT receives a <Vendor Command> message.
     * When there are no listeners registered, the DUT should respond with <Feature Abort>[Refused].
     * Since the number of listeners registered is not queryable, the case where there are no
     * listeners registered is not tested.
     */

    private Thread registerVendorCmdListenerWithId() {
        return new Thread(
                new Runnable() {
                    public void run() {
                        try {
                            HdmiControlManagerUtility.registerVendorCmdListenerWithId(
                                    HdmiCecVendorCommandsTest.this);
                        } catch (DeviceNotAvailableException dnae) {
                            CLog.w("HdmiCecVendorcommandstest", "Device not available exception");
                        }
                    }
                });
    }

    private Thread registerVendorCmdListenerWithoutId() {
        return new Thread(
                new Runnable() {
                    public void run() {
                        try {
                            HdmiControlManagerUtility.registerVendorCmdListenerWithoutId(
                                    HdmiCecVendorCommandsTest.this);
                        } catch (DeviceNotAvailableException dnae) {
                            CLog.w("HdmiCecVendorcommandstest", "Device not available exception");
                        }
                    }
                });
    }

    @Test
    public void cecVendorCommandListenerWithVendorIdTest() throws Exception {
        ITestDevice device = getDevice();
        Thread test = registerVendorCmdListenerWithId();

        test.start();

        try {
            LogHelper.waitForLog(getDevice(), TEST_LOG_TAG, 10, REGISTERED_LISTENER);
            String params = CecMessage.formatParams(VENDOR_ID);
            params += CecMessage.formatParams("010203");
            hdmiCecClient.sendCecMessage(
                    hdmiCecClient.getSelfDevice(), CecOperand.VENDOR_COMMAND_WITH_ID, params);

            LogHelper.assertLog(
                    device, TEST_LOG_TAG, "Received vendor command with correct vendor ID");
        } finally {
            test.join();
        }
    }

    @Test
    public void cecVendorCommandListenerReceivesVendorCommandWithoutId() throws Exception {
        ITestDevice device = getDevice();
        Thread test = registerVendorCmdListenerWithId();
        test.start();

        try {
            LogHelper.waitForLog(getDevice(), TEST_LOG_TAG, 10, REGISTERED_LISTENER);

            String params = CecMessage.formatParams("010203");
            hdmiCecClient.sendCecMessage(
                    hdmiCecClient.getSelfDevice(), CecOperand.VENDOR_COMMAND, params);

            LogHelper.assertLog(device, TEST_LOG_TAG, "Received vendor command without vendor ID");
        } finally {
            test.join();
        }
    }

    @Test
    public void cecVendorCommandListenerWithoutVendorIdTest() throws Exception {
        ITestDevice device = getDevice();
        Thread test = registerVendorCmdListenerWithoutId();
        test.start();

        try {
            LogHelper.waitForLog(getDevice(), TEST_LOG_TAG, 10, REGISTERED_LISTENER);

            String params = CecMessage.formatParams("010203");
            hdmiCecClient.sendCecMessage(
                    hdmiCecClient.getSelfDevice(), CecOperand.VENDOR_COMMAND, params);

            LogHelper.assertLog(device, TEST_LOG_TAG, "Received vendor command without vendor ID");
        } finally {
            test.join();
        }
    }

    @Test
    public void cecVendorCommandListenerWithoutVendorIdDoesNotReceiveTest() throws Exception {
        ITestDevice device = getDevice();
        Thread test = registerVendorCmdListenerWithoutId();
        test.start();

        try {
            LogHelper.waitForLog(getDevice(), TEST_LOG_TAG, 10, REGISTERED_LISTENER);

            String params = CecMessage.formatParams(VENDOR_ID);
            params += CecMessage.formatParams("010203");
            hdmiCecClient.sendCecMessage(
                    hdmiCecClient.getSelfDevice(), CecOperand.VENDOR_COMMAND_WITH_ID, params);

            LogHelper.assertLogDoesNotContain(
                    device, TEST_LOG_TAG, "Received vendor command with correct vendor ID");
        } finally {
            test.join();
        }
    }
}
