/*
 * Copyright (C) 2018 The Android Open Source Project
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

import static org.junit.Assert.*;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.suite.checker.StatusCheckerResult.CheckStatus;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.Map;

/** Unit tests for {@link DeviceSettingChecker} */
@RunWith(JUnit4.class)
public class DeviceSettingCheckerTest {

    private DeviceSettingChecker mChecker;
    private ITestDevice mMockDevice;
    private OptionSetter mOptionSetter;

    @Before
    public void setup() throws Exception {
        mMockDevice = mock(ITestDevice.class);
        mChecker = new DeviceSettingChecker();
        mOptionSetter = new OptionSetter(mChecker);
    }

    /** Test that device checker passes if the settings does not change. */
    @Test
    public void testSettingsUnchanged() throws Exception {
        Map<String, String> mapGlobal = new HashMap<>();
        mapGlobal.put("wifi_on", "1");
        mapGlobal.put("adb_enabled", "1");
        Map<String, String> mapSecure = new HashMap<>();
        mapSecure.put("bluetooth_on", "1");
        Map<String, String> mapSystem = new HashMap<>();
        mapSystem.put("alarm_alert", "1");
        when(mMockDevice.getAllSettings(Mockito.eq("global"))).thenReturn(mapGlobal);
        when(mMockDevice.getAllSettings(Mockito.eq("secure"))).thenReturn(mapSecure);
        when(mMockDevice.getAllSettings(Mockito.eq("system"))).thenReturn(mapSystem);

        assertEquals(CheckStatus.SUCCESS, mChecker.preExecutionCheck(mMockDevice).getStatus());
        assertEquals(CheckStatus.SUCCESS, mChecker.postExecutionCheck(mMockDevice).getStatus());
        verify(mMockDevice, times(2)).getAllSettings(Mockito.eq("global"));
        verify(mMockDevice, times(2)).getAllSettings(Mockito.eq("secure"));
        verify(mMockDevice, times(2)).getAllSettings(Mockito.eq("system"));
    }

    /** Test that device checker fails if the settings does change. */
    @Test
    public void testSettingsChanged() throws Exception {
        Map<String, String> mapPreGlobal = new HashMap<>();
        mapPreGlobal.put("wifi_on", "1");
        mapPreGlobal.put("adb_enabled", "1");
        Map<String, String> mapPreSecure = new HashMap<>();
        mapPreSecure.put("bluetooth_on", "1");
        Map<String, String> mapPreSystem = new HashMap<>();
        mapPreSystem.put("alarm_alert", "1");
        when(mMockDevice.getAllSettings(Mockito.eq("secure"))).thenReturn(mapPreSecure);
        when(mMockDevice.getAllSettings(Mockito.eq("system"))).thenReturn(mapPreSystem);
        Map<String, String> mapPostGlobal = new HashMap<>();
        mapPostGlobal.put("wifi_on", "0");
        mapPostGlobal.put("adb_enabled", "1");
        when(mMockDevice.getAllSettings(Mockito.eq("global")))
                .thenReturn(mapPreGlobal)
                .thenReturn(mapPostGlobal);

        assertEquals(CheckStatus.SUCCESS, mChecker.preExecutionCheck(mMockDevice).getStatus());
        assertEquals(CheckStatus.FAILED, mChecker.postExecutionCheck(mMockDevice).getStatus());
        verify(mMockDevice, times(2)).getAllSettings(Mockito.eq("secure"));
        verify(mMockDevice, times(2)).getAllSettings(Mockito.eq("system"));
    }

    /** Test that ignored setting won't be checked. */
    @Test
    public void testIgnoredSettings() throws Exception {
        mOptionSetter.setOptionValue("ignore-global-setting", "wifi_on");
        Map<String, String> mapPreGlobal = new HashMap<>();
        mapPreGlobal.put("wifi_on", "1");
        mapPreGlobal.put("adb_enabled", "1");
        Map<String, String> mapPreSecure = new HashMap<>();
        mapPreSecure.put("bluetooth_on", "1");
        Map<String, String> mapPreSystem = new HashMap<>();
        mapPreSystem.put("alarm_alert", "1");
        when(mMockDevice.getAllSettings(Mockito.eq("global"))).thenReturn(mapPreGlobal);
        when(mMockDevice.getAllSettings(Mockito.eq("secure"))).thenReturn(mapPreSecure);
        when(mMockDevice.getAllSettings(Mockito.eq("system"))).thenReturn(mapPreSystem);
        Map<String, String> mapPostGlobal = new HashMap<>();
        mapPostGlobal.put("wifi_on", "0");
        mapPostGlobal.put("adb_enabled", "1");
        when(mMockDevice.getAllSettings(Mockito.eq("global"))).thenReturn(mapPostGlobal);

        assertEquals(CheckStatus.SUCCESS, mChecker.preExecutionCheck(mMockDevice).getStatus());
        assertEquals(CheckStatus.SUCCESS, mChecker.postExecutionCheck(mMockDevice).getStatus());
        verify(mMockDevice, times(2)).getAllSettings(Mockito.eq("secure"));
        verify(mMockDevice, times(2)).getAllSettings(Mockito.eq("system"));
    }

    /** Test that device checker fails if some settings can't be detected in post execution. */
    @Test
    public void testSettingsMissedPostExecution() throws Exception {
        Map<String, String> mapPreGlobal = new HashMap<>();
        mapPreGlobal.put("wifi_on", "1");
        mapPreGlobal.put("adb_enabled", "1");
        Map<String, String> mapPreSecure = new HashMap<>();
        mapPreSecure.put("bluetooth_on", "1");
        Map<String, String> mapPreSystem = new HashMap<>();
        mapPreSystem.put("alarm_alert", "1");
        when(mMockDevice.getAllSettings(Mockito.eq("secure"))).thenReturn(mapPreSecure);
        when(mMockDevice.getAllSettings(Mockito.eq("system"))).thenReturn(mapPreSystem);
        Map<String, String> mapPostGlobal = new HashMap<>();
        when(mMockDevice.getAllSettings(Mockito.eq("global")))
                .thenReturn(mapPreGlobal)
                .thenReturn(mapPostGlobal);

        assertEquals(CheckStatus.SUCCESS, mChecker.preExecutionCheck(mMockDevice).getStatus());
        assertEquals(CheckStatus.FAILED, mChecker.postExecutionCheck(mMockDevice).getStatus());
        verify(mMockDevice, times(2)).getAllSettings(Mockito.eq("secure"));
        verify(mMockDevice, times(2)).getAllSettings(Mockito.eq("system"));
    }

    /** Test that device checker fails if new settings are detected in post execution. */
    @Test
    public void testSettingsNewPostExecution() throws Exception {
        Map<String, String> mapPreGlobal = new HashMap<>();
        mapPreGlobal.put("wifi_on", "1");
        mapPreGlobal.put("adb_enabled", "1");
        Map<String, String> mapPreSecure = new HashMap<>();
        mapPreSecure.put("bluetooth_on", "1");
        Map<String, String> mapPreSystem = new HashMap<>();
        mapPreSystem.put("alarm_alert", "1");
        when(mMockDevice.getAllSettings(Mockito.eq("secure"))).thenReturn(mapPreSecure);
        when(mMockDevice.getAllSettings(Mockito.eq("system"))).thenReturn(mapPreSystem);
        Map<String, String> mapPostGlobal = new HashMap<>();
        mapPostGlobal.put("wifi_on", "1");
        mapPostGlobal.put("adb_enabled", "1");
        mapPostGlobal.put("boot_count", "1");
        when(mMockDevice.getAllSettings(Mockito.eq("global")))
                .thenReturn(mapPreGlobal)
                .thenReturn(mapPostGlobal);

        assertEquals(CheckStatus.SUCCESS, mChecker.preExecutionCheck(mMockDevice).getStatus());
        assertEquals(CheckStatus.FAILED, mChecker.postExecutionCheck(mMockDevice).getStatus());
        verify(mMockDevice, times(2)).getAllSettings(Mockito.eq("secure"));
        verify(mMockDevice, times(2)).getAllSettings(Mockito.eq("system"));
    }

    /** Test that device checker fails if no settings can be detected in pre execution. */
    @Test
    public void testSettingsNonePreExecution() throws Exception {
        Map<String, String> mapPreGlobal = new HashMap<>();
        mapPreGlobal.put("wifi_on", "1");
        mapPreGlobal.put("adb_enabled", "1");
        Map<String, String> mapPreSecure = new HashMap<>();
        mapPreSecure.put("bluetooth_on", "1");
        Map<String, String> mapPreSystem = new HashMap<>();
        when(mMockDevice.getAllSettings(Mockito.eq("global"))).thenReturn(mapPreGlobal);
        when(mMockDevice.getAllSettings(Mockito.eq("secure"))).thenReturn(mapPreSecure);
        when(mMockDevice.getAllSettings(Mockito.eq("system"))).thenReturn(mapPreSystem);

        assertEquals(CheckStatus.FAILED, mChecker.preExecutionCheck(mMockDevice).getStatus());
        assertEquals(CheckStatus.SUCCESS, mChecker.postExecutionCheck(mMockDevice).getStatus());
        verify(mMockDevice, times(2)).getAllSettings(Mockito.eq("global"));
        verify(mMockDevice, times(2)).getAllSettings(Mockito.eq("secure"));
        verify(mMockDevice, times(2)).getAllSettings(Mockito.eq("system"));
    }
}
