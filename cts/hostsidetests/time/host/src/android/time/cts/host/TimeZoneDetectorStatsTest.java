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

package android.time.cts.host;

import com.android.tradefed.util.RunUtil;
import static android.app.time.cts.shell.DeviceConfigKeys.NAMESPACE_SYSTEM_TIME;
import static android.app.time.cts.shell.DeviceConfigShellHelper.SYNC_DISABLED_MODE_UNTIL_REBOOT;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.app.time.cts.shell.DeviceConfigShellHelper;
import android.app.time.cts.shell.DeviceShellCommandExecutor;
import android.app.time.cts.shell.LocationShellHelper;
import android.app.time.cts.shell.TimeZoneDetectorShellHelper;
import android.app.time.cts.shell.host.HostShellCommandExecutor;
import android.cts.statsdatom.lib.AtomTestUtils;
import android.cts.statsdatom.lib.ConfigUtils;
import android.cts.statsdatom.lib.DeviceUtils;
import android.cts.statsdatom.lib.ReportUtils;

import com.android.os.AtomsProto;
import com.android.os.AtomsProto.TimeZoneDetectorState.DetectionMode;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

/** Host-side CTS tests for the time zone detector service stats logging. */
@RunWith(DeviceJUnit4ClassRunner.class)
public class TimeZoneDetectorStatsTest extends BaseHostJUnit4Test {

    private TimeZoneDetectorShellHelper mTimeZoneDetectorShellHelper;
    private LocationShellHelper mLocationShellHelper;
    private DeviceConfigShellHelper mDeviceConfigShellHelper;
    private DeviceConfigShellHelper.PreTestState mDeviceConfigPreTestState;

    @Before
    public void setUp() throws Exception {
        DeviceShellCommandExecutor shellCommandExecutor = new HostShellCommandExecutor(getDevice());
        mTimeZoneDetectorShellHelper = new TimeZoneDetectorShellHelper(shellCommandExecutor);
        mLocationShellHelper = new LocationShellHelper(shellCommandExecutor);
        mDeviceConfigShellHelper = new DeviceConfigShellHelper(shellCommandExecutor);
        mDeviceConfigPreTestState = mDeviceConfigShellHelper.setSyncModeForTest(
                SYNC_DISABLED_MODE_UNTIL_REBOOT, NAMESPACE_SYSTEM_TIME);

        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
    }

    @After
    public void tearDown() throws Exception {
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
        mDeviceConfigShellHelper.restoreDeviceConfigStateForTest(mDeviceConfigPreTestState);
    }

    @Test
    public void testAtom_TimeZoneDetectorState() throws Exception {
        // Enable the atom.
        ConfigUtils.uploadConfigForPulledAtom(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                AtomsProto.Atom.TIME_ZONE_DETECTOR_STATE_FIELD_NUMBER);
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);

        // This should trigger a pull.
        AtomTestUtils.sendAppBreadcrumbReportedAtom(getDevice());
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);

        // Extract and assert about TimeZoneDetectorState.
        List<AtomsProto.Atom> atoms = ReportUtils.getGaugeMetricAtoms(getDevice());

        boolean found = false;
        for (AtomsProto.Atom atom : atoms) {
            if (atom.hasTimeZoneDetectorState()) {
                AtomsProto.TimeZoneDetectorState state = atom.getTimeZoneDetectorState();

                // There are a few parts of the pull metric we can check easily via the command
                // line. Checking more would require adding more commands or something that dumps a
                // proto. This test provides at least some coverage that the atom is working /
                // matches actual state.

                // The shell reports the same info the atom does for geo detection supported.
                boolean geoDetectionSupportedFromShell =
                        mTimeZoneDetectorShellHelper.isGeoDetectionSupported();
                assertThat(state.getGeoSupported()).isEqualTo(geoDetectionSupportedFromShell);

                // The shell reports the same info the atom does for location enabled.
                boolean locationEnabledForCurrentUserFromShell =
                        mLocationShellHelper.isLocationEnabledForCurrentUser();
                assertThat(state.getLocationEnabled())
                        .isEqualTo(locationEnabledForCurrentUserFromShell);

                // The shell reports the user's setting for auto detection.
                boolean autoDetectionEnabledFromShell =
                        mTimeZoneDetectorShellHelper.isAutoDetectionEnabled();
                assertThat(state.getAutoDetectionSetting())
                        .isEqualTo(autoDetectionEnabledFromShell);

                boolean telephonyDetectionSupportedFromShell =
                        mTimeZoneDetectorShellHelper.isTelephonyDetectionSupported();
                boolean noAutoDetectionSupported =
                        !(telephonyDetectionSupportedFromShell || geoDetectionSupportedFromShell);
                // The atom reports the functional state for "detection mode", which is derived from
                // device config and settings. This test logic basically repeats the logic we expect
                // to be used on the device.
                DetectionMode expectedDetectionMode;
                if (autoDetectionEnabledFromShell) {
                    if (telephonyDetectionSupportedFromShell && geoDetectionSupportedFromShell) {
                        boolean geoDetectionSettingEnabledFromShell =
                                mTimeZoneDetectorShellHelper.isGeoDetectionEnabled();
                        if (locationEnabledForCurrentUserFromShell
                                && geoDetectionSettingEnabledFromShell) {
                            expectedDetectionMode = DetectionMode.GEO;
                        } else {
                            expectedDetectionMode = DetectionMode.TELEPHONY;
                        }
                    } else if (telephonyDetectionSupportedFromShell) {
                        expectedDetectionMode = DetectionMode.TELEPHONY;
                    } else if (geoDetectionSupportedFromShell) {
                        if (locationEnabledForCurrentUserFromShell) {
                            expectedDetectionMode = DetectionMode.GEO;
                        } else {
                            expectedDetectionMode = DetectionMode.UNKNOWN;
                        }
                    } else {
                        expectedDetectionMode = DetectionMode.MANUAL;
                    }
                } else {
                    expectedDetectionMode = DetectionMode.MANUAL;
                }
                assertThat(state.getDetectionMode()).isEqualTo(expectedDetectionMode);

                found = true;
                break;
            }
        }
        assertWithMessage("Did not find a matching atom TimeZoneDetectorState")
                .that(found).isTrue();
    }
}
