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

package com.android.tradefed.suite.checker;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.suite.checker.StatusCheckerResult.CheckStatus;
import com.android.tradefed.suite.checker.baseline.DeviceBaselineSetter;
import com.android.tradefed.suite.checker.baseline.SettingsBaselineSetter;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.ArrayList;
import java.util.List;

/** Unit tests for {@link DeviceBaselineChecker}. */
@RunWith(JUnit4.class)
public class DeviceBaselineCheckerTest {

    private DeviceBaselineChecker mChecker;
    private ITestDevice mMockDevice;
    private OptionSetter mOptionSetter;

    @Before
    public void setup() throws Exception {
        mMockDevice = mock(ITestDevice.class);
        mChecker = new DeviceBaselineChecker();
        mOptionSetter = new OptionSetter(mChecker);
    }

    /** Test that the baseline setting is set when it is included in the experiment list. */
    @Test
    public void testSetBaselineSettings_inExperimentList() throws Exception {
        mOptionSetter.setOptionValue("enable-device-baseline-settings", "true");
        mOptionSetter.setOptionValue("enable-experimental-device-baseline-setters", "test1");

        DeviceBaselineSetter mockSetterExp = mock(SettingsBaselineSetter.class);
        doReturn("test1").when(mockSetterExp).getName();
        doReturn(30).when(mockSetterExp).getMinimalApiLevel();
        doReturn(true).when(mockSetterExp).isExperimental();
        doReturn(true).when(mockSetterExp).setBaseline(mMockDevice);

        DeviceBaselineSetter mockSetterNotExp = mock(SettingsBaselineSetter.class);
        doReturn("test2").when(mockSetterNotExp).getName();
        doReturn(30).when(mockSetterNotExp).getMinimalApiLevel();
        doReturn(false).when(mockSetterNotExp).isExperimental();
        doReturn(true).when(mockSetterNotExp).setBaseline(mMockDevice);

        doReturn(true).when(mMockDevice).checkApiLevelAgainstNextRelease(30);

        List<DeviceBaselineSetter> deviceBaselineSetters = new ArrayList<>();
        deviceBaselineSetters.add(mockSetterExp);
        deviceBaselineSetters.add(mockSetterNotExp);
        mChecker.setDeviceBaselineSetters(deviceBaselineSetters);

        StatusCheckerResult result = mChecker.preExecutionCheck(mMockDevice);

        assertEquals(CheckStatus.SUCCESS, result.getStatus());
        assertEquals(
                result.getModuleProperties()
                        .getOrDefault("system_checker_enabled_device_baseline", ""),
                "test1,test2");
        verify(mockSetterExp, times(1)).setBaseline(mMockDevice);
        verify(mockSetterNotExp, times(1)).setBaseline(mMockDevice);
    }

    /** Test that the baseline setting is skipped when it is not included in the experiment list. */
    @Test
    public void testSetBaselineSettings_notInExperimentList() throws Exception {
        mOptionSetter.setOptionValue("enable-device-baseline-settings", "true");

        DeviceBaselineSetter mockSetter = mock(SettingsBaselineSetter.class);
        doReturn("test").when(mockSetter).getName();
        doReturn(30).when(mockSetter).getMinimalApiLevel();
        doReturn(true).when(mockSetter).isExperimental();
        doReturn(true).when(mockSetter).setBaseline(mMockDevice);

        doReturn(true).when(mMockDevice).checkApiLevelAgainstNextRelease(30);

        List<DeviceBaselineSetter> deviceBaselineSetters = new ArrayList<>();
        deviceBaselineSetters.add(mockSetter);
        mChecker.setDeviceBaselineSetters(deviceBaselineSetters);

        StatusCheckerResult result = mChecker.preExecutionCheck(mMockDevice);

        assertEquals(CheckStatus.SUCCESS, result.getStatus());
        assertEquals(
                result.getModuleProperties()
                        .getOrDefault("system_checker_enabled_device_baseline", ""),
                "");
        verify(mockSetter, times(0)).setBaseline(mMockDevice);
    }

    /** Test that the status is set to failed when an exception occurs. */
    @Test
    public void testFailToSetBaselineSettings() throws Exception {
        mOptionSetter.setOptionValue("enable-device-baseline-settings", "true");

        DeviceBaselineSetter mockSetter = mock(SettingsBaselineSetter.class);
        doReturn("test").when(mockSetter).getName();
        doReturn(30).when(mockSetter).getMinimalApiLevel();
        doReturn(false).when(mockSetter).isExperimental();
        doReturn(false).when(mockSetter).setBaseline(mMockDevice);

        doReturn(true).when(mMockDevice).checkApiLevelAgainstNextRelease(30);

        List<DeviceBaselineSetter> deviceBaselineSetters = new ArrayList<>();
        deviceBaselineSetters.add(mockSetter);
        mChecker.setDeviceBaselineSetters(deviceBaselineSetters);

        StatusCheckerResult result = mChecker.preExecutionCheck(mMockDevice);

        assertEquals(CheckStatus.FAILED, result.getStatus());
        assertEquals("Failed to set baseline test. ", result.getErrorMessage());
        assertEquals(
                result.getModuleProperties()
                        .getOrDefault("system_checker_enabled_device_baseline", ""),
                "");
    }

    /** Test that the baseline setting is skipped when the device api level is too old. */
    @Test
    public void testSetBaselineSettings_oldDeviceApiLevel() throws Exception {
        mOptionSetter.setOptionValue("enable-device-baseline-settings", "true");

        DeviceBaselineSetter mockSetter = mock(SettingsBaselineSetter.class);
        doReturn("test").when(mockSetter).getName();
        doReturn(30).when(mockSetter).getMinimalApiLevel();
        doReturn(false).when(mockSetter).isExperimental();
        doReturn(true).when(mockSetter).setBaseline(mMockDevice);

        doReturn(false).when(mMockDevice).checkApiLevelAgainstNextRelease(30);

        List<DeviceBaselineSetter> deviceBaselineSetters = new ArrayList<>();
        deviceBaselineSetters.add(mockSetter);
        mChecker.setDeviceBaselineSetters(deviceBaselineSetters);

        StatusCheckerResult result = mChecker.preExecutionCheck(mMockDevice);

        assertEquals(CheckStatus.SUCCESS, result.getStatus());
        assertEquals(
                result.getModuleProperties()
                        .getOrDefault("system_checker_enabled_device_baseline", ""),
                "");
        verify(mockSetter, times(0)).setBaseline(mMockDevice);
    }
}
