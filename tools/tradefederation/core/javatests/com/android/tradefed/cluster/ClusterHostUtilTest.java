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
package com.android.tradefed.cluster;

import static org.mockito.Mockito.when;

import com.android.ddmlib.IDevice;
import com.android.ddmlib.IDevice.DeviceState;
import com.android.tradefed.command.remote.DeviceDescriptor;
import com.android.tradefed.device.DeviceAllocationState;
import com.android.tradefed.device.DeviceManager;
import com.android.tradefed.device.DeviceManager.FastbootDevice;
import java.security.InvalidParameterException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

/** Unit tests for {@link ClusterHostUtil}. */
@RunWith(JUnit4.class)
public class ClusterHostUtilTest {

    private static final String DEVICE_SERIAL = "serial";
    private static final String EMULATOR_SERIAL = "emulator-5554";

    @Test
    public void testIsLocalhostIpPort() {
        Assert.assertTrue(ClusterHostUtil.isLocalhostIpPort("127.0.0.1:101"));
        Assert.assertTrue(ClusterHostUtil.isLocalhostIpPort("127.0.0.1"));
        Assert.assertFalse(ClusterHostUtil.isLocalhostIpPort(DEVICE_SERIAL));
        Assert.assertFalse(ClusterHostUtil.isLocalhostIpPort("127.0.0.1:notaport"));
        Assert.assertFalse(ClusterHostUtil.isLocalhostIpPort("192.168.0.1:22434"));
    }

    @Test
    public void testIsLocalhostIpPort_hostname() {
        Assert.assertTrue(ClusterHostUtil.isLocalhostIpPort("localhost:101"));
        Assert.assertTrue(ClusterHostUtil.isLocalhostIpPort("localhost"));
        Assert.assertFalse(ClusterHostUtil.isLocalhostIpPort(DEVICE_SERIAL));
        Assert.assertFalse(ClusterHostUtil.isLocalhostIpPort("localhost:notaport"));
        Assert.assertFalse(ClusterHostUtil.isLocalhostIpPort("google.com:22434"));
    }

    // Test a valid TF version
    @Test
    public void testToValidTfVersion() {
        String version = "12345";
        String actual = ClusterHostUtil.toValidTfVersion(version);
        Assert.assertEquals(version, actual);
    }

    // Test an empty TF version
    @Test
    public void testToValidTfVersionWithEmptyVersion() {
        String version = "";
        String actual = ClusterHostUtil.toValidTfVersion(version);
        Assert.assertEquals(ClusterHostUtil.DEFAULT_TF_VERSION, actual);
    }

    // Test a null TF version
    @Test
    public void testToValidTfVersionWithNullVersion() {
        String version = null;
        String actual = ClusterHostUtil.toValidTfVersion(version);
        Assert.assertEquals(ClusterHostUtil.DEFAULT_TF_VERSION, actual);
    }

    // Test an invalid TF version
    @Test
    public void testToValidTfVersionWithInvalidVersion() {
        String version = "1abcd2efg";
        String actual = ClusterHostUtil.toValidTfVersion(version);
        Assert.assertEquals(ClusterHostUtil.DEFAULT_TF_VERSION, actual);
    }

    // Test default run target if nothing is specified.
    @Test
    public void testGetDefaultRunTarget() {
        DeviceDescriptor device =
                new DeviceDescriptor(
                        DEVICE_SERIAL,
                        false,
                        DeviceAllocationState.Available,
                        "product",
                        "productVariant",
                        "sdkVersion",
                        "buildId",
                        "batteryLevel");
        Assert.assertEquals(
                "product:productVariant", ClusterHostUtil.getRunTarget(device, null, null));
    }

    // Test default run target if nothing is specified, and product == product variant.
    @Test
    public void testGetDefaultRunTargetWithSameProductAndProductVariant() {
        DeviceDescriptor device =
                new DeviceDescriptor(
                        DEVICE_SERIAL,
                        false,
                        DeviceAllocationState.Available,
                        "product",
                        "product",
                        "sdkVersion",
                        "buildId",
                        "batteryLevel");
        Assert.assertEquals("product", ClusterHostUtil.getRunTarget(device, null, null));
    }

    // If a constant string run target pattern is set, always return said pattern.
    @Test
    public void testSimpleConstantRunTargetMatchPattern() {
        String format = "foo";
        DeviceDescriptor device =
                new DeviceDescriptor(
                        DEVICE_SERIAL,
                        false,
                        DeviceAllocationState.Available,
                        "product",
                        "productVariant",
                        "sdkVersion",
                        "buildId",
                        "batteryLevel");
        Assert.assertEquals("foo", ClusterHostUtil.getRunTarget(device, format, null));
    }

    // Test run target pattern with a device tag map
    @Test
    public void testDeviceTagRunTargetMatchPattern_simple() {
        String format = "{TAG}";
        DeviceDescriptor device =
                new DeviceDescriptor(
                        DEVICE_SERIAL,
                        false,
                        DeviceAllocationState.Available,
                        "product",
                        "productVariant",
                        "sdkVersion",
                        "buildId",
                        "batteryLevel");
        Map<String, String> deviceTag = new HashMap<>();
        deviceTag.put(DEVICE_SERIAL, "foo");
        Assert.assertEquals("foo", ClusterHostUtil.getRunTarget(device, format, deviceTag));
    }

    // Test run target pattern with a device tag map, but the device serial is not in map
    @Test
    public void testDeviceTagRunTargetMatchPattern_missingSerial() {
        String format = "foo{TAG}bar";
        DeviceDescriptor device =
                new DeviceDescriptor(
                        DEVICE_SERIAL,
                        false,
                        DeviceAllocationState.Available,
                        "product",
                        "productVariant",
                        "sdkVersion",
                        "buildId",
                        "batteryLevel");
        Map<String, String> deviceTag = Collections.emptyMap();
        Assert.assertEquals("foobar", ClusterHostUtil.getRunTarget(device, format, deviceTag));
    }

    // Ensure that invalid run target pattern throws an exception.
    @Test
    public void testInvalidRunTargetMetachPattern() {
        String format = "foo-{INVALID PATTERN}";
        DeviceDescriptor device =
                new DeviceDescriptor(
                        DEVICE_SERIAL,
                        false,
                        DeviceAllocationState.Available,
                        "product",
                        "productVariant",
                        "sdkVersion",
                        "buildId",
                        "batteryLevel");
        try {
            ClusterHostUtil.getRunTarget(device, format, null);
            Assert.fail("Should have thrown an InvalidParameter exception.");
        } catch (InvalidParameterException e) {
            // expected.
        }
    }

    // Test all supported run target match patterns.
    @Test
    public void testSupportedRunTargetMatchPattern() {
        String format = "foo-{PRODUCT}-{PRODUCT_VARIANT}-{API_LEVEL}-{DEVICE_PROP:bar}";
        IDevice mockIDevice = Mockito.mock(IDevice.class);
        when(mockIDevice.getProperty("bar")).thenReturn("zzz");
        DeviceDescriptor device =
                new DeviceDescriptor(
                        DEVICE_SERIAL,
                        false,
                        DeviceState.ONLINE,
                        DeviceAllocationState.Available,
                        "product",
                        "productVariant",
                        "sdkVersion",
                        "buildId",
                        "batteryLevel",
                        "",
                        "",
                        "",
                        "",
                        mockIDevice);

        Assert.assertEquals(
                "foo-product-productVariant-sdkVersion-zzz",
                ClusterHostUtil.getRunTarget(device, format, null));
    }

    // Test all supported run target match patterns with unknown property.
    @Test
    public void testSupportedRunTargetMatchPattern_unknownProperty() {
        String format = "foo-{PRODUCT}-{PRODUCT_VARIANT}-{API_LEVEL}";
        DeviceDescriptor device =
                new DeviceDescriptor(
                        DEVICE_SERIAL,
                        false,
                        DeviceAllocationState.Available,
                        DeviceManager.UNKNOWN_DISPLAY_STRING,
                        "productVariant",
                        "sdkVersion",
                        "buildId",
                        "batteryLevel");
        Assert.assertEquals(
                DeviceManager.UNKNOWN_DISPLAY_STRING,
                ClusterHostUtil.getRunTarget(device, format, null));
    }

    /**
     * Test PRODUCT_OR_DEVICE_CLASS that can return both product type and the stub device class.
     * This allows an host to report both physical and stub devices.
     */
    @Test
    public void testSupportedRunTargetMatchPattern_productAndStub() {
        String format = "{PRODUCT_OR_DEVICE_CLASS}";
        // with non-stub device we use the requested product
        DeviceDescriptor device =
                new DeviceDescriptor(
                        DEVICE_SERIAL,
                        false,
                        DeviceAllocationState.Available,
                        "product",
                        "productVariant",
                        "sdkVersion",
                        "buildId",
                        "batteryLevel");
        Assert.assertEquals("product", ClusterHostUtil.getRunTarget(device, format, null));
        // with a stub device we use the device class
        device =
                new DeviceDescriptor(
                        DEVICE_SERIAL,
                        true,
                        DeviceAllocationState.Available,
                        "product",
                        "productVariant",
                        "sdkVersion",
                        "buildId",
                        "batteryLevel",
                        "deviceClass",
                        "macAddress",
                        "simState",
                        "simOperator");
        Assert.assertEquals("deviceClass", ClusterHostUtil.getRunTarget(device, format, null));
        // with a fastboot device we use the product
        device =
                new DeviceDescriptor(
                        DEVICE_SERIAL,
                        true,
                        DeviceAllocationState.Available,
                        "product",
                        "productVariant",
                        "sdkVersion",
                        "buildId",
                        "batteryLevel",
                        FastbootDevice.class.getSimpleName(),
                        "macAddress",
                        "simState",
                        "simOperator");
        Assert.assertEquals("product", ClusterHostUtil.getRunTarget(device, format, null));
    }

    @Test
    public void testGetRunTarget_withStubDevice() {
        final String hostname = ClusterHostUtil.getHostName();
        DeviceDescriptor device =
                new DeviceDescriptor(
                        DEVICE_SERIAL,
                        true, // Stub device.
                        DeviceAllocationState.Available,
                        "product",
                        "productVariant",
                        "sdkVersion",
                        "buildId",
                        "batteryLevel");
        Assert.assertEquals(
                hostname + ":" + DEVICE_SERIAL,
                ClusterHostUtil.getRunTarget(device, "{SERIAL}", null));
    }

    @Test
    public void testGetRunTarget_withFastbootDevice() {
        DeviceDescriptor device =
                new DeviceDescriptor(
                        DEVICE_SERIAL,
                        true, // Stub device.
                        DeviceAllocationState.Available,
                        "product",
                        "productVariant",
                        "sdkVersion",
                        "buildId",
                        "batteryLevel",
                        FastbootDevice.class.getSimpleName(), // Fastboot device.
                        "macAddress",
                        "simState",
                        "simOperator");
        Assert.assertEquals(DEVICE_SERIAL, ClusterHostUtil.getRunTarget(device, "{SERIAL}", null));
    }

    @Test
    public void testGetRunTarget_withEmulator() {
        final String hostname = ClusterHostUtil.getHostName();
        DeviceDescriptor device =
                new DeviceDescriptor(
                        EMULATOR_SERIAL, // Emulator.
                        false,
                        DeviceAllocationState.Available,
                        "product",
                        "productVariant",
                        "sdkVersion",
                        "buildId",
                        "batteryLevel");
        Assert.assertEquals(
                hostname + ":" + EMULATOR_SERIAL,
                ClusterHostUtil.getRunTarget(device, "{SERIAL}", null));
    }

    @Test
    public void testGetRunTarget_withEmptyDeviceSerial() {
        final String hostname = ClusterHostUtil.getHostName();
        DeviceDescriptor device =
                new DeviceDescriptor(
                        "", // Empty serial number.
                        false,
                        DeviceAllocationState.Available,
                        "product",
                        "productVariant",
                        "sdkVersion",
                        "buildId",
                        "batteryLevel");
        Assert.assertEquals(
                hostname + ":" + ClusterHostUtil.NULL_DEVICE_SERIAL_PLACEHOLDER,
                ClusterHostUtil.getRunTarget(device, "{SERIAL}", null));
    }

    @Test
    public void testGetHostIpAddress() {
        final String hostIp = ClusterHostUtil.getHostIpAddress();
        Assert.assertNotEquals(hostIp, "127.0.0.1");
        Pattern pattern =
                Pattern.compile("[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}" + "|UNKNOWN");
        Matcher matcher = pattern.matcher(hostIp);
        Assert.assertTrue("host ip format not match: " + hostIp, matcher.matches());
    }
}
