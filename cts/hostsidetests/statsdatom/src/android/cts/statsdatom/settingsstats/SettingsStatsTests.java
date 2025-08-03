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

package android.cts.statsdatom.settingsstats;

import com.android.tradefed.util.RunUtil;
import static com.google.common.truth.Truth.assertThat;

import android.cts.statsdatom.lib.AtomTestUtils;
import android.cts.statsdatom.lib.ConfigUtils;
import android.cts.statsdatom.lib.DeviceUtils;
import android.cts.statsdatom.lib.ReportUtils;

import com.android.os.AtomsProto;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.testtype.DeviceTestCase;
import com.android.tradefed.testtype.IBuildReceiver;

import java.util.List;

public class SettingsStatsTests extends DeviceTestCase implements IBuildReceiver {
    private IBuildInfo mCtsBuild;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        assertThat(mCtsBuild).isNotNull();
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
        DeviceUtils.installStatsdTestApp(getDevice(), mCtsBuild);
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);
    }

    @Override
    protected void tearDown() throws Exception {
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
        DeviceUtils.uninstallStatsdTestApp(getDevice());
        super.tearDown();
    }

    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mCtsBuild = buildInfo;
    }

    public void testSettingsStatsReported() throws Exception {
        // Base64 encoded proto com.android.service.nano.StringListParamProto,
        // which contains five strings 'low_power_trigger_level', 'preferred_network_mode1',
        // 'preferred_network_mode1_int', 'wfc_ims_mode','zen_mode'
        final String encoded =
                "Chdsb3dfcG93ZXJfdHJpZ2dlcl9sZXZlbAoQd2ZjX2ltc19tb2RlID0gMgoXcHJlZmVycmVkX25ldHdvcmtfbW9kZTEKG3ByZWZlcnJlZF9uZXR3b3JrX21vZGUxX2ludAoIemVuX21vZGU";
        final String network_mode1 = "preferred_network_mode1";

        int originalNetworkMode;
        try {
            originalNetworkMode = Integer.parseInt(
                    getDevice().executeShellCommand("settings get global " + network_mode1));
        } catch (NumberFormatException e) {
            // The default value, zen mode is not enabled
            originalNetworkMode = 0;
        }
        // Clear settings_stats device config.
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_SHORT);
        getDevice().executeShellCommand(
                "device_config reset untrusted_clear settings_stats");
        // Set allow list through device config.
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_SHORT);
        getDevice().executeShellCommand(
                "device_config put settings_stats GlobalFeature__integer_whitelist " + encoded);
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_SHORT);
        // Set network_mode1 value
        getDevice().executeShellCommand("settings put global " + network_mode1 + " 15");

        // Get SettingSnapshot as a simple gauge metric.
        ConfigUtils.uploadConfigForPulledAtom(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                AtomsProto.Atom.SETTING_SNAPSHOT_FIELD_NUMBER);

        // Start test app and trigger a pull while it is running.
        try (AutoCloseable a = DeviceUtils.withActivity(getDevice(),
                DeviceUtils.STATSD_ATOM_TEST_PKG, "StatsdCtsForegroundActivity", "action",
                "action.show_notification")) {
            RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_SHORT);
            // Trigger a pull and wait for new pull before killing the process.
            AtomTestUtils.sendAppBreadcrumbReportedAtom(getDevice());
            RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);
        }

        // Test the size of atoms. It should contain 5 atoms.
        List<AtomsProto.Atom> atoms = ReportUtils.getGaugeMetricAtoms(getDevice());
        assertThat(atoms.size()).isAtLeast(5);
        AtomsProto.SettingSnapshot snapshot = null;
        for (AtomsProto.Atom atom : atoms) {
            AtomsProto.SettingSnapshot settingSnapshot = atom.getSettingSnapshot();
            if (network_mode1.equals(settingSnapshot.getName())) {
                snapshot = settingSnapshot;
                break;
            }
        }

        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_SHORT);
        // Test the data of atom.
        assertNotNull(snapshot);
        // Get setting value and test value type.
        final int newNetworkMode = Integer.parseInt(
                getDevice().executeShellCommand("settings get global " + network_mode1).trim());
        assertThat(snapshot.getType()).isEqualTo(
                AtomsProto.SettingSnapshot.SettingsValueType.ASSIGNED_INT_TYPE);
        assertThat(snapshot.getBoolValue()).isEqualTo(false);
        assertThat(snapshot.getIntValue()).isEqualTo(newNetworkMode);
        assertThat(snapshot.getFloatValue()).isEqualTo(0f);
        assertThat(snapshot.getStrValue()).isEqualTo("");
        assertThat(snapshot.getUserId()).isEqualTo(0);

        // Restore the setting value.
        getDevice().executeShellCommand(
                "settings put global " + network_mode1 + " " + originalNetworkMode);
    }
}