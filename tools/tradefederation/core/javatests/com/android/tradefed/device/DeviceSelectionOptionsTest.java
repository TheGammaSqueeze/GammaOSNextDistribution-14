/*
 * Copyright (C) 2011 The Android Open Source Project
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
package com.android.tradefed.device;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.android.ddmlib.IDevice;
import com.android.ddmlib.IShellOutputReceiver;
import com.android.tradefed.config.ArgsOptionParser;
import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.device.DeviceManager.FastbootDevice;
import com.android.tradefed.device.DeviceSelectionOptions.DeviceRequestedType;

import com.google.common.util.concurrent.SettableFuture;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

/** Unit tests for {@link DeviceSelectionOptions}. */
@RunWith(JUnit4.class)
public class DeviceSelectionOptionsTest {

    // DEVICE_SERIAL and DEVICE_ENV_SERIAL need to be different.
    private static final String DEVICE_SERIAL = "12345";
    private static final String DEVICE_ENV_SERIAL = "6789";

    @Mock IDevice mMockDevice;
    @Mock IDevice mMockEmulatorDevice;
    private DeviceSelectionOptions mDeviceSelection;

    // DEVICE_TYPE and OTHER_DEVICE_TYPE should be different
    private static final String DEVICE_TYPE = "charm";
    private static final String OTHER_DEVICE_TYPE = "strange";

    // For mockBatteryTemperatureCheck
    private static final String DUMPSYS_BATTERY_OUTPUT_TEMPLATE =
            "Current Battery Service state:\n"
                    + "  AC powered: true\n"
                    + "  USB powered: false\n"
                    + "  Wireless powered: false\n"
                    + "  Max charging current: 1500000\n"
                    + "  Max charging voltage: 5000000\n"
                    + "  Charge counter: 6418283\n"
                    + "  status: 5\n"
                    + "  health: 2\n"
                    + "  present: true\n"
                    + "  level: 100\n"
                    + "  scale: 100\n"
                    + "  voltage: 4279\n"
                    + "  temperature: %s\n"
                    + "  technology: Li-ion\n";

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        // Avoid any issue related to env. variable.
        mDeviceSelection =
                new DeviceSelectionOptions() {
                    @Override
                    public String fetchEnvironmentVariable(String name) {
                        return null;
                    }
                };

        when(mMockDevice.getSerialNumber()).thenReturn(DEVICE_SERIAL);
        when(mMockDevice.isEmulator()).thenReturn(Boolean.FALSE);

        when(mMockEmulatorDevice.getSerialNumber()).thenReturn("emulator");
        when(mMockEmulatorDevice.isEmulator()).thenReturn(Boolean.TRUE);
    }

    /** Test for {@link DeviceSelectionOptions#getSerials(IDevice)} */
    @Test
    public void testGetSerials() {
        DeviceSelectionOptions options = getDeviceSelectionOptionsWithEnvVar(DEVICE_ENV_SERIAL);
        // If no serial is available, the environment variable will be used instead.
        assertEquals(1, options.getSerials(mMockDevice).size());
        assertTrue(options.getSerials(mMockDevice).contains(DEVICE_ENV_SERIAL));
        assertFalse(options.getSerials(mMockDevice).contains(DEVICE_SERIAL));
    }

    /** Test matching a stub device when ANDROID_SERIAL is set. */
    @Test
    public void testGetSerials_envVariable_nullDevice() {
        DeviceSelectionOptions options = getDeviceSelectionOptionsWithEnvVar(DEVICE_ENV_SERIAL);
        options.setNullDeviceRequested(true);
        // If no serial is available, the environment variable will be used instead.
        IDevice device = new NullDevice("serial");
        assertEquals(0, options.getSerials(device).size());
        assertTrue(options.matches(device));
    }

    /** Test matching a FastbootDevice when ANDROID_SERIAL is set. */
    @Test
    public void testGetSerials_envVariable_FastbootDevice() {
        DeviceSelectionOptions options = getDeviceSelectionOptionsWithEnvVar(DEVICE_ENV_SERIAL);
        // If no serial is available, the environment variable will be used instead.
        IDevice device = new FastbootDevice(DEVICE_ENV_SERIAL);
        assertEquals(1, options.getSerials(device).size());
        assertTrue(options.matches(device));
    }

    /** Test not matching a FastbootDevice when ANDROID_SERIAL is set. */
    @Test
    public void testGetSerials_envVariable_FastbootDevice_noMatch() {
        DeviceSelectionOptions options = getDeviceSelectionOptionsWithEnvVar(DEVICE_ENV_SERIAL);
        // If no serial is available, the environment variable will be used instead.
        IDevice device = new FastbootDevice("serial");
        assertEquals(1, options.getSerials(device).size());
        assertFalse(options.matches(device));
    }

    /**
     * Test that {@link DeviceSelectionOptions#getSerials(IDevice)} does not override the values.
     */
    @Test
    public void testGetSerialsDoesNotOverride() {
        DeviceSelectionOptions options = getDeviceSelectionOptionsWithEnvVar(DEVICE_ENV_SERIAL);
        options.addSerial(DEVICE_SERIAL);

        // Check that now we do not override the serial with the environment variable.
        assertEquals(1, options.getSerials(mMockDevice).size());
        assertFalse(options.getSerials(mMockDevice).contains(DEVICE_ENV_SERIAL));
        assertTrue(options.getSerials(mMockDevice).contains(DEVICE_SERIAL));
    }

    /**
     * Test for {@link DeviceSelectionOptions#getSerials(IDevice)} without the environment variable
     * set.
     */
    @Test
    public void testGetSerialsWithNoEnvValue() {
        DeviceSelectionOptions options = getDeviceSelectionOptionsWithEnvVar(null);
        // An empty list will cause it to fetch the
        assertTrue(options.getSerials(mMockDevice).isEmpty());
        // If no serial is available and the environment variable is not set, nothing happens.
        assertEquals(0, options.getSerials(mMockDevice).size());

        options.addSerial(DEVICE_SERIAL);
        // Check that now we do not override the serial.
        assertEquals(1, options.getSerials(mMockDevice).size());
        assertFalse(options.getSerials(mMockDevice).contains(DEVICE_ENV_SERIAL));
        assertTrue(options.getSerials(mMockDevice).contains(DEVICE_SERIAL));
    }

    /**
     * Helper method to return an anonymous subclass of DeviceSelectionOptions with a given
     * environmental variable.
     *
     * @param value {@link String} of the environment variable ANDROID_SERIAL
     * @return {@link DeviceSelectionOptions} subclass with a given environmental variable.
     */
    private DeviceSelectionOptions getDeviceSelectionOptionsWithEnvVar(final String value) {
        return new DeviceSelectionOptions() {
            // We don't have the environment variable set, return null.
            @Override
            public String fetchEnvironmentVariable(String name) {
                return value;
            }
        };
    }

    @Test
    public void testGetProductVariant_legacy() throws Exception {
        when(mMockDevice.getProperty(DeviceProperties.VARIANT)).thenReturn(null);
        when(mMockDevice.getProperty(DeviceProperties.VARIANT_LEGACY_O_MR1))
                .thenReturn(null);
        when(mMockDevice.getProperty(DeviceProperties.VARIANT_LEGACY_LESS_EQUAL_O))
                .thenReturn("legacy");

        assertEquals("legacy", mDeviceSelection.getDeviceProductVariant(mMockDevice));
    }

    @Test
    public void testGetProductVariant_legacyOmr1() throws Exception {
        when(mMockDevice.getProperty(DeviceProperties.VARIANT)).thenReturn(null);
        when(mMockDevice.getProperty(DeviceProperties.VARIANT_LEGACY_O_MR1))
                .thenReturn("legacy_omr1");
        when(mMockDevice.getProperty(DeviceProperties.VARIANT_LEGACY_LESS_EQUAL_O))
                .thenReturn("legacy");

        assertEquals("legacy_omr1", mDeviceSelection.getDeviceProductVariant(mMockDevice));
    }

    @Test
    public void testGetProductVariant_vendor() throws Exception {
        when(mMockDevice.getProperty(DeviceProperties.VARIANT)).thenReturn("variant");
        when(mMockDevice.getProperty(DeviceProperties.VARIANT_LEGACY_O_MR1))
                .thenReturn("legacy_mr1");
        when(mMockDevice.getProperty(DeviceProperties.VARIANT_LEGACY_LESS_EQUAL_O))
                .thenReturn("legacy");

        assertEquals("variant", mDeviceSelection.getDeviceProductVariant(mMockDevice));
    }

    @Test
    public void testGetProductType_mismatch() throws Exception {
        mDeviceSelection.addProductType(OTHER_DEVICE_TYPE);

        when(mMockDevice.getProperty(DeviceProperties.BOARD)).thenReturn(DEVICE_TYPE);
        when(mMockDevice.getProperty(DeviceProperties.VARIANT)).thenReturn(null);

        assertFalse(mDeviceSelection.matches(mMockDevice));
    }

    @Test
    public void testGetProductType_match() throws Exception {
        mDeviceSelection.addProductType(DEVICE_TYPE);

        when(mMockDevice.getProperty(DeviceProperties.BOARD)).thenReturn(DEVICE_TYPE);
        when(mMockDevice.getProperty(DeviceProperties.VARIANT)).thenReturn(null);
        when(mMockDevice.getProperty(DeviceProperties.VARIANT_LEGACY_O_MR1))
                .thenReturn(null);
        when(mMockDevice.getProperty(DeviceProperties.VARIANT_LEGACY_LESS_EQUAL_O))
                .thenReturn(null);

        assertTrue(mDeviceSelection.matches(mMockDevice));
    }

    /**
     * Test scenario where device does not return a valid product type. For now, this will result in
     * device not being matched.
     */
    @Test
    public void testGetProductType_missingProduct() throws Exception {
        mDeviceSelection.addProductType(DEVICE_TYPE);

        when(mMockDevice.getProperty(DeviceProperties.BOARD)).thenReturn(DEVICE_TYPE);
        when(mMockDevice.getProperty(DeviceProperties.VARIANT)).thenReturn(null);
        when(mMockDevice.getProperty(DeviceProperties.VARIANT_LEGACY_O_MR1))
                .thenReturn(null);
        when(mMockDevice.getProperty(DeviceProperties.VARIANT_LEGACY_LESS_EQUAL_O))
                .thenReturn(null);

        assertTrue(mDeviceSelection.matches(mMockDevice));
    }

    /** Test matching by property */
    @Test
    public void testMatches_property() {
        mDeviceSelection.addProperty("prop1", "propvalue");

        when(mMockDevice.getProperty("prop1")).thenReturn("propvalue");

        assertTrue(mDeviceSelection.matches(mMockDevice));
    }

    /** Test negative case for matching by property */
    @Test
    public void testMatches_propertyNotMatch() {
        mDeviceSelection.addProperty("prop1", "propvalue");

        when(mMockDevice.getProperty("prop1")).thenReturn("wrongvalue");

        assertFalse(mDeviceSelection.matches(mMockDevice));
    }

    /** Test for matching by multiple properties */
    @Test
    public void testMatches_multipleProperty() {
        mDeviceSelection.addProperty("prop1", "propvalue");
        mDeviceSelection.addProperty("prop2", "propvalue2");

        when(mMockDevice.getProperty("prop1")).thenReturn("propvalue");
        when(mMockDevice.getProperty("prop2")).thenReturn("propvalue2");

        assertTrue(mDeviceSelection.matches(mMockDevice));
    }

    /** Test for matching by multiple properties, when one property does not match */
    @Test
    public void testMatches_notMultipleProperty() {
        mDeviceSelection.addProperty("prop1", "propvalue");
        mDeviceSelection.addProperty("prop2", "propvalue2");

        when(mMockDevice.getProperty("prop1")).thenReturn("propvalue");
        when(mMockDevice.getProperty("prop2")).thenReturn("wrongpropvalue");

        assertFalse(mDeviceSelection.matches(mMockDevice));
    }

    /** Test for matching with an srtub emulator */
    @Test
    public void testMatches_stubEmulator() {
        mDeviceSelection.setStubEmulatorRequested(true);
        IDevice emulatorDevice = new StubDevice("emulator", true);
        assertTrue(mDeviceSelection.matches(emulatorDevice));
    }

    /** Test that an stub emulator is not matched by default */
    @Test
    public void testMatches_stubEmulatorNotDefault() {
        IDevice emulatorDevice = new StubDevice("emulator", true);
        assertFalse(mDeviceSelection.matches(emulatorDevice));
    }

    /** Test for matching with null device requested flag */
    @Test
    public void testMatches_nullDevice() {
        mDeviceSelection.setNullDeviceRequested(true);
        IDevice stubDevice = new NullDevice("null device");
        assertTrue(mDeviceSelection.matches(stubDevice));
    }

    /** Test for matching with tcp device requested flag */
    @Test
    public void testMatches_tcpDevice() {
        mDeviceSelection.setTcpDeviceRequested(true);
        IDevice stubDevice = new TcpDevice("tcp device");
        assertTrue(mDeviceSelection.matches(stubDevice));
    }

    /** Test that a real device is not matched if the 'null device requested' flag is set */
    @Test
    public void testMatches_notNullDevice() {
        mDeviceSelection.setNullDeviceRequested(true);

        assertFalse(mDeviceSelection.matches(mMockDevice));
    }

    /** Test that a real device is matched when requested */
    @Test
    public void testMatches_device() {
        IDevice mockIpDevice = mock(IDevice.class);
        when(mockIpDevice.getSerialNumber()).thenReturn("127.0.0.1:5555");
        when(mockIpDevice.isEmulator()).thenReturn(Boolean.FALSE);

        mDeviceSelection.setDeviceRequested(true);

        assertTrue(mDeviceSelection.matches(mMockDevice));
        assertFalse(mDeviceSelection.matches(mMockEmulatorDevice));
        assertFalse(mDeviceSelection.matches(mockIpDevice));
    }

    /** Test that a emulator is matched when requested */
    @Test
    public void testMatches_emulator() {
        mDeviceSelection.setEmulatorRequested(true);

        assertFalse(mDeviceSelection.matches(mMockDevice));
        assertTrue(mDeviceSelection.matches(mMockEmulatorDevice));
    }

    /** Test that battery checking works */
    @Test
    public void testMatches_minBatteryPass() throws Exception {
        mDeviceSelection.setMinBatteryLevel(25);
        mockBatteryCheck(50);

        assertTrue(mDeviceSelection.matches(mMockDevice));
    }

    /** Test that battery checking works */
    @Test
    public void testMatches_minBatteryFail() throws Exception {
        mDeviceSelection.setMinBatteryLevel(75);
        mockBatteryCheck(50);

        assertFalse(mDeviceSelection.matches(mMockDevice));
    }

    /** Test that battery checking works */
    @Test
    public void testMatches_maxBatteryPass() throws Exception {
        mDeviceSelection.setMaxBatteryLevel(75);
        mockBatteryCheck(50);

        assertTrue(mDeviceSelection.matches(mMockDevice));
    }

    /** Test that battery checking works */
    @Test
    public void testMatches_maxBatteryFail() throws Exception {
        mDeviceSelection.setMaxBatteryLevel(25);
        mockBatteryCheck(50);

        assertFalse(mDeviceSelection.matches(mMockDevice));
    }

    /** Test that battery checking works */
    @Test
    public void testMatches_forceBatteryCheckTrue() throws Exception {
        mDeviceSelection.setRequireBatteryCheck(true);
        mockBatteryCheck(null);

        assertTrue(mDeviceSelection.matches(mMockDevice));
        mDeviceSelection.setMinBatteryLevel(25);
        assertFalse(mDeviceSelection.matches(mMockDevice));
    }

    /**
     * Test that when battery checking is disabled, if a min-battery is requested, no battery check
     * occurs.
     */
    @Test
    public void testMatches_forceBatteryCheckFalse() throws Exception {
        mDeviceSelection.setRequireBatteryCheck(false);
        mockBatteryCheck(12);

        assertTrue(mDeviceSelection.matches(mMockDevice));
        mDeviceSelection.setMinBatteryLevel(25);
        assertTrue(mDeviceSelection.matches(mMockDevice));
    }

    /** Test that battery temperature checking works */
    @Test
    public void testMatches_maxBatteryTempPass() throws Exception {
        // 50 < 100, test should pass
        DeviceSelectionOptions options = mockBatteryTemperatureCheck(50, 100, true);

        assertTrue(options.matches(mMockDevice));
    }

    /** Test that battery temperature checking works */
    @Test
    public void testMatches_maxBatteryTempFail() throws Exception {
        // 150 > 100, test should fail
        DeviceSelectionOptions options = mockBatteryTemperatureCheck(150, 100, true);

        assertFalse(options.matches(mMockDevice));
    }

    /** Test that battery temperature checking works */
    @Test
    public void testMatches_forceBatteryTempCheckTrue() throws Exception {
        // temperature unavailable, should fail
        DeviceSelectionOptions options = mockBatteryTemperatureCheck(0, 100, true);

        assertFalse(options.matches(mMockDevice));
    }

    /** Test that battery temperature checking works */
    @Test
    public void testMatches_forceBatteryTempCheckFalse() throws Exception {
        // temperature unavailable, should pass
        DeviceSelectionOptions options = mockBatteryTemperatureCheck(0, 100, false);

        assertTrue(options.matches(mMockDevice));
    }

    /** Test that min sdk checking works for negative case */
    @Test
    public void testMatches_minSdkFail() throws Exception {
        ArgsOptionParser p = new ArgsOptionParser(mDeviceSelection);
        p.parse("--min-sdk-level", "15");
        when(
                mMockDevice.getProperty(DeviceProperties.SDK_VERSION))
                .thenReturn("10");

        assertFalse(mDeviceSelection.matches(mMockDevice));
    }

    /** Test that min sdk checking works for positive case */
    @Test
    public void testMatches_minSdkPass() throws Exception {
        ArgsOptionParser p = new ArgsOptionParser(mDeviceSelection);
        p.parse("--min-sdk-level", "10");
        when(
                mMockDevice.getProperty(DeviceProperties.SDK_VERSION))
                .thenReturn("10");

        assertTrue(mDeviceSelection.matches(mMockDevice));
    }

    /** Test that device is not matched if device api cannot be determined */
    @Test
    public void testMatches_minSdkNull() throws Exception {
        ArgsOptionParser p = new ArgsOptionParser(mDeviceSelection);
        p.parse("--min-sdk-level", "10");
        when(
                mMockDevice.getProperty(DeviceProperties.SDK_VERSION))
                .thenReturn("blargh");

        assertFalse(mDeviceSelection.matches(mMockDevice));
    }

    /** Test that max sdk checking works for negative case */
    @Test
    public void testMatches_maxSdkFail() throws Exception {
        ArgsOptionParser p = new ArgsOptionParser(mDeviceSelection);
        p.parse("--max-sdk-level", "15");
        when(
                mMockDevice.getProperty(DeviceProperties.SDK_VERSION))
                .thenReturn("25");

        assertFalse(mDeviceSelection.matches(mMockDevice));
    }

    /** Test that max sdk checking works for positive case */
    @Test
    public void testMatches_maxSdkPass() throws Exception {
        ArgsOptionParser p = new ArgsOptionParser(mDeviceSelection);
        p.parse("--max-sdk-level", "15");
        when(
                mMockDevice.getProperty(DeviceProperties.SDK_VERSION))
                .thenReturn("10");

        assertTrue(mDeviceSelection.matches(mMockDevice));
    }

    /** Test that device is not matched if device api cannot be determined */
    @Test
    public void testMatches_maxSdkNull() throws Exception {
        ArgsOptionParser p = new ArgsOptionParser(mDeviceSelection);
        p.parse("--max-sdk-level", "15");
        when(
                mMockDevice.getProperty(DeviceProperties.SDK_VERSION))
                .thenReturn("blargh");

        assertFalse(mDeviceSelection.matches(mMockDevice));
    }

    /** Test that if min-battery is used for a StubDevice it will not match. */
    @Test
    public void testStubDevice_minBattery() throws Exception {
        OptionSetter setter = new OptionSetter(mDeviceSelection);
        setter.setOptionValue("require-battery-check", "true");
        setter.setOptionValue("min-battery", "20");
        setter.setOptionValue("null-device", "true");
        assertFalse(mDeviceSelection.matches(new NullDevice("test")));
    }

    /** Test that if we require a battery check but no minimal or max, StubDevice can be matched. */
    @Test
    public void testStubDevice_requireBatteryCheck() throws Exception {
        OptionSetter setter = new OptionSetter(mDeviceSelection);
        setter.setOptionValue("require-battery-check", "true");
        setter.setOptionValue("null-device", "true");
        assertTrue(mDeviceSelection.matches(new NullDevice("test")));
    }

    /**
     * A FastbootDevice does not expose a battery level so if a battery is specified we cannot match
     * it.
     */
    @Test
    public void testFastbootDevice_minBattery() throws Exception {
        OptionSetter setter = new OptionSetter(mDeviceSelection);
        setter.setOptionValue("min-battery", "20");
        assertFalse(mDeviceSelection.matches(new FastbootDevice("serial")));
    }

    /**
     * Ensure that a fastboot device without any special condition can be matched for allocation.
     */
    @Test
    public void testFastbootDevice() throws Exception {
        assertTrue(mDeviceSelection.matches(new FastbootDevice("serial")));
    }

    /** When a gce-device is requested, it can be matched. */
    @Test
    public void testAllocateGceDevice() throws ConfigurationException {
        IDevice gceDevice = new RemoteAvdIDevice("gce-device:0");
        OptionSetter setter = new OptionSetter(mDeviceSelection);
        setter.setOptionValue("gce-device", "true");
        assertTrue(mDeviceSelection.matches(gceDevice));
    }

    /** When a gce-device is presented but no --gce-device flag, it can't be matched. */
    @Test
    public void testAllocateGceDevice_default() {
        IDevice gceDevice = new RemoteAvdIDevice("gce-device:0");
        assertFalse(mDeviceSelection.matches(gceDevice));
    }

    /** When a gce-device is requested by serial. other gce-device serial should be rejected. */
    @Test
    public void testAllocateGceDevice_bySerial() throws ConfigurationException {
        IDevice gceDevice = new RemoteAvdIDevice("gce-device:1");
        OptionSetter setter = new OptionSetter(mDeviceSelection);
        setter.setOptionValue("gce-device", "true");
        setter.setOptionValue("serial", "gce-device:0");
        assertFalse(mDeviceSelection.matches(gceDevice));
    }

    /** When a tcp-device is requested, a gce-device cannot be allocated for it. */
    @Test
    public void testAllocateGceDevice_whenTcpRequested() throws ConfigurationException {
        IDevice gceDevice = new RemoteAvdIDevice("gce-device:0");
        OptionSetter setter = new OptionSetter(mDeviceSelection);
        setter.setOptionValue("tcp-device", "true");
        assertFalse(mDeviceSelection.matches(gceDevice));
    }

    /** When a tcp-device is requested, a tcp-device can be allocated for it. */
    @Test
    public void testAllocateTcpDevice_whenTcpRequested() throws ConfigurationException {
        IDevice gceDevice = new TcpDevice("tcp-device:0");
        OptionSetter setter = new OptionSetter(mDeviceSelection);
        setter.setOptionValue("tcp-device", "true");
        assertTrue(mDeviceSelection.matches(gceDevice));
    }

    /** When a tcp-device is requested, and device-type is also requested, it has precedence */
    @Test
    public void testAllocateTcpDevice_whenDeviceRequestIsSet() throws ConfigurationException {
        IDevice gceDevice = new TcpDevice("tcp-device:0");
        OptionSetter setter = new OptionSetter(mDeviceSelection);
        setter.setOptionValue("tcp-device", "true");
        // Device type takes precedence.
        setter.setOptionValue("device-type", DeviceRequestedType.GCE_DEVICE.toString());
        assertFalse(mDeviceSelection.matches(gceDevice));
    }

    /** When a gce-device is requested, a serial is provided that match a non gce-device. */
    @Test
    public void testAllocateDeviceMatch_gceRequested() throws ConfigurationException {
        IDevice gceDevice = new StubDevice("stub-device:0");
        OptionSetter setter = new OptionSetter(mDeviceSelection);
        // mis-match of expectation: request gce-device with a serial of a device that is not one.
        setter.setOptionValue("gce-device", "true");
        setter.setOptionValue("serial", "stub-device:0");
        assertFalse(mDeviceSelection.matches(gceDevice));
    }

    /** When a gce-device is requested, a serial is provided that doens't match a non gce-device. */
    @Test
    public void testAllocateDevice_NoMatch_gceRequested() throws ConfigurationException {
        IDevice gceDevice = new StubDevice("stub-device:0");
        OptionSetter setter = new OptionSetter(mDeviceSelection);
        // mis-match of expectation: request gce-device with a serial of a device that is not one.
        setter.setOptionValue("gce-device", "true");
        setter.setOptionValue("serial", "stub-device:1");
        assertFalse(mDeviceSelection.matches(gceDevice));
    }

    private void mockBatteryCheck(Integer battery) {
        SettableFuture<Integer> batteryFuture = SettableFuture.create();
        batteryFuture.set(battery);
        when(mMockDevice.getBattery()).thenReturn(batteryFuture);
    }

    private DeviceSelectionOptions mockBatteryTemperatureCheck(
            Integer batteryTemp, Integer maxBatteryTemp, Boolean required) throws Exception {
        // Mock out the execution of executeShellCommand
        String dumpsysOutput = "";

        if (batteryTemp != 0) {
            dumpsysOutput = String.format(DUMPSYS_BATTERY_OUTPUT_TEMPLATE, batteryTemp * 10);
        }

        final String dumpsysMock = dumpsysOutput;
        doAnswer(invocation -> {
            IShellOutputReceiver receiver =
                    (IShellOutputReceiver) invocation.getArgument(1);
            byte[] inputData = dumpsysMock.getBytes();
            receiver.addOutput(inputData, 0, inputData.length);
            receiver.flush();
            return null;
        }).when(mMockDevice).executeShellCommand(Mockito.contains("dumpsys battery"), Mockito.any());

        mDeviceSelection.setMaxBatteryTemperature(maxBatteryTemp);
        mDeviceSelection.setRequireBatteryTemperatureCheck(required);
        return mDeviceSelection;
    }
}
