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

package android.hdmicec.cts;

import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.util.Arrays;
import java.util.List;

/**
 * Rule to check if the device contains the required device type as property or as a local device.
 */
public class RequiredDeviceTypeRule implements TestRule {

    private RequiredDeviceTypeRule(){}

    @Override
    public Statement apply(final Statement base, Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                base.evaluate();
            }
        };
    }

    public static RequiredDeviceTypeRule requiredDeviceType(
            final BaseHostJUnit4Test test, int deviceType) {
        return new RequiredDeviceTypeRule() {
            @Override
            public Statement apply(final Statement base, final Description description) {
                return new Statement() {
                    @Override
                    public void evaluate() throws Throwable {
                        LogicalAddress logicalAddressForType =
                                LogicalAddress.getLogicalAddress(deviceType);
                        ITestDevice testDevice = test.getDevice();
                        List<LogicalAddress> logicalAddresses =
                                BaseHdmiCecCtsTest.getDumpsysLogicalAddresses(testDevice);

                        List<String> deviceProperties =
                                Arrays.asList(RequiredPropertyRule.getDevicePropertyValue(test,
                                                HdmiCecConstants.HDMI_DEVICE_TYPE_PROPERTY)
                                        .replaceAll("\\s+", "")
                                        .split(","));

                        assumeTrue(
                                "Required device type " + deviceType + " is not found in device "
                                        + "property or in local devices list of device "
                                        + testDevice.getSerialNumber(),
                                logicalAddresses.contains(logicalAddressForType)
                                        || deviceProperties.contains(Integer.toString(deviceType)));
                        base.evaluate();
                    }
                };
            }
        };
    }

    public static RequiredDeviceTypeRule invalidDeviceType(
            final BaseHostJUnit4Test test, int deviceType) {
        return new RequiredDeviceTypeRule() {
            @Override
            public Statement apply(final Statement base, final Description description) {
                return new Statement() {
                    @Override
                    public void evaluate() throws Throwable {
                        LogicalAddress logicalAddressForType =
                                LogicalAddress.getLogicalAddress(deviceType);
                        ITestDevice testDevice = test.getDevice();
                        List<LogicalAddress> logicalAddresses =
                                BaseHdmiCecCtsTest.getDumpsysLogicalAddresses(testDevice);

                        List<String> deviceProperties =
                                Arrays.asList(RequiredPropertyRule.getDevicePropertyValue(test,
                                                HdmiCecConstants.HDMI_DEVICE_TYPE_PROPERTY)
                                        .replaceAll("\\s+", "")
                                        .split(","));

                        assumeFalse(
                                "Invalid device type " + deviceType + " is found in device "
                                        + "property or in local devices list of device "
                                        + testDevice.getSerialNumber(),
                                logicalAddresses.contains(logicalAddressForType)
                                        || deviceProperties.contains(Integer.toString(deviceType)));
                        base.evaluate();
                    }
                };
            }
        };
    }

    /**
     * Rule to check that the DUT is not an emulator.
     * @param test The test using this rule.
     * @return The rule to be used in the test.
     */
    public static RequiredDeviceTypeRule requiredPhysicalDevice(final BaseHostJUnit4Test test) {
        return new RequiredDeviceTypeRule() {
            @Override
            public Statement apply(final Statement base, final Description description) {
                return new Statement() {
                    @Override
                    public void evaluate() throws Throwable {
                        ITestDevice testDevice = test.getDevice();
                        String buildFingerPrint = RequiredPropertyRule.getDevicePropertyValue(test,
                                                HdmiCecConstants.PROPERTY_BUILD_FINGERPRINT);
                        // Currently only Cuttlefish is supported in automation testing. We will add
                        // more targets in the future if necessary.
                        assumeFalse(
                                "Invalid device " +  testDevice.getSerialNumber() + " for "
                                        + "running HDMI Control CTS. Physical device required, "
                                        + "emulator found instead.",
                                buildFingerPrint.contains("cf_x86_"));
                        base.evaluate();
                    }
                };
            }
        };
    }
}

