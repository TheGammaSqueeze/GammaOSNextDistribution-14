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

package android.cts.statsdatom.display;

import static com.google.common.truth.Truth.assertThat;

import android.cts.statsdatom.lib.AtomTestUtils;
import android.cts.statsdatom.lib.ConfigUtils;
import android.cts.statsdatom.lib.DeviceUtils;
import android.cts.statsdatom.lib.ReportUtils;

import com.android.compatibility.common.util.NonApiTest;
import com.android.os.AtomsProto;
import com.android.os.StatsLog;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.testtype.DeviceTestCase;
import com.android.tradefed.testtype.IBuildReceiver;
import com.android.tradefed.util.RunUtil;

import java.util.List;

@NonApiTest(exemptionReasons = {}, justification = "METRIC")
public class DisplayBrightnessChangedStatsTests extends DeviceTestCase implements IBuildReceiver {
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

    public void testBrightnessEventReported() throws Exception {
        // Only run if we have a valid ambient light sensor.
        if (!DeviceUtils.checkDeviceFor(getDevice(), "checkValidLightSensor")) {
            return;
        }

        // Don't run if there is no app that has permission to access slider usage.
        if (!DeviceUtils.checkDeviceFor(getDevice(), "checkBrightnessSliderPermission")) {
            return;
        }

        // Upload config to collect DisplayBrightnessChanged event.
        ConfigUtils.uploadConfigForPushedAtom(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                AtomsProto.Atom.DISPLAY_BRIGHTNESS_CHANGED_FIELD_NUMBER);

        // Generate slider event.
        int brightnessLevelBeforeTest = getCurrentBrightnessLevel();
        int brightnessModeBeforeTest = getCurrentBrightnessMode();

        // Make sure we don't go out of the [0 - 255] range
        int newBrightness = (brightnessLevelBeforeTest < 100
                ? brightnessLevelBeforeTest + 10 : brightnessLevelBeforeTest - 10);
        // Enable autobrightness.
        setAutoBrightnessMode(1);
        // Set brightness to new value.
        setScreenBrightnessLevel(newBrightness);

        // Assert one DisplayBrightnessChanged event has been collected.
        List<StatsLog.EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());
        assertThat(data.size()).isEqualTo(1);
        AtomsProto.DisplayBrightnessChanged displayBrightnessChanged =
                data.get(0).getAtom().getDisplayBrightnessChanged();
        assertThat(displayBrightnessChanged.getReason())
            .isEqualTo(AtomsProto.DisplayBrightnessChanged.Reason.REASON_MANUAL);

        // Reset brightness to initial level and mode
        setScreenBrightnessLevel(brightnessLevelBeforeTest);
        setAutoBrightnessMode(brightnessModeBeforeTest);
    }

    private int getCurrentBrightnessLevel() throws Exception {
        getDevice().executeShellCommand("input keyevent 82");
        return Integer.parseInt(
                getDevice()
                    .executeShellCommand("settings get system screen_brightness")
                    .replaceAll("\\s", ""));
    }

    private int getCurrentBrightnessMode() throws Exception {
        return Integer.parseInt(
            getDevice()
                .executeShellCommand("settings get system screen_brightness_mode")
                .replaceAll("\\s", ""));
    }

    private void setScreenBrightnessLevel(int newBrightness) throws Exception {
        String command = "settings put system screen_brightness " + String.valueOf(newBrightness);
        getDevice().executeShellCommand(command);
    }

    private void setAutoBrightnessMode(int mode) throws Exception {
        // Ensure adaptive brightness is enabled.
        getDevice().executeShellCommand("settings set system screen_brightness_mode "
                + String.valueOf(mode));
    }
}
