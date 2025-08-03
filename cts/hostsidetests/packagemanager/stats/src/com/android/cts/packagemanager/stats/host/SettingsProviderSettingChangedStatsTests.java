/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.cts.packagemanager.stats.host;

import android.cts.statsdatom.lib.AtomTestUtils;
import android.cts.statsdatom.lib.ConfigUtils;
import android.cts.statsdatom.lib.DeviceUtils;
import android.cts.statsdatom.lib.ReportUtils;

import com.android.os.AtomsProto;
import com.android.os.StatsLog;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.util.RunUtil;

import java.util.ArrayList;
import java.util.List;

public class SettingsProviderSettingChangedStatsTests extends PackageManagerStatsTestsBase {

    public void testSettingsChanged() throws Throwable {
        ConfigUtils.uploadConfigForPushedAtom(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                AtomsProto.Atom.SETTINGS_PROVIDER_SETTING_CHANGED_FIELD_NUMBER);
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_SHORT);
        putSetting(getDevice(), "secure", "test_setting1", "100");
        putSetting(getDevice(), "system", "test_setting2", "200");
        putSetting(getDevice(), "global", "test_setting3", "300");
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_SHORT);
        deleteSetting(getDevice(), "secure", "test_setting1");
        deleteSetting(getDevice(), "system", "test_setting2");
        deleteSetting(getDevice(), "global", "test_setting3");
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_SHORT);

        List<AtomsProto.SettingsProviderSettingChanged> reports = new ArrayList<>();
        for (StatsLog.EventMetricData data : ReportUtils.getEventMetricDataList(getDevice())) {
            if (data.getAtom().hasSettingsProviderSettingChanged()) {
                AtomsProto.SettingsProviderSettingChanged setting =
                        data.getAtom().getSettingsProviderSettingChanged();
                String name = setting.getName();
                if (name.equals("test_setting1") || name.equals("test_setting2")
                        || name.equals("test_setting3")) {
                    reports.add(setting);
                }
            }
        }
        assertEquals(6, reports.size());
        assertEquals("test_setting1", reports.get(0).getName());
        assertEquals(2, reports.get(0).getType());
        assertEquals(0, reports.get(0).getChangeType());
        assertEquals("test_setting2", reports.get(1).getName());
        assertEquals(1, reports.get(1).getType());
        assertEquals(0, reports.get(1).getChangeType());
        assertEquals("test_setting3", reports.get(2).getName());
        assertEquals(0, reports.get(2).getType());
        assertEquals(0, reports.get(2).getChangeType());
        assertEquals("test_setting1", reports.get(3).getName());
        assertEquals(2, reports.get(3).getType());
        assertEquals(1, reports.get(3).getChangeType());
        assertEquals("test_setting2", reports.get(4).getName());
        assertEquals(1, reports.get(4).getType());
        assertEquals(1, reports.get(4).getChangeType());
        assertEquals("test_setting3", reports.get(5).getName());
        assertEquals(0, reports.get(5).getType());
        assertEquals(1, reports.get(5).getChangeType());
    }

    private static void putSetting(ITestDevice device, String type, String name, String value)
            throws DeviceNotAvailableException {
        device.executeShellCommand(String.format("settings put %s %s %s", type, name, value));
    }

    private static void deleteSetting(ITestDevice device, String type, String name)
            throws DeviceNotAvailableException {
        device.executeShellCommand(String.format("settings delete %s %s", type, name));
    }
}
