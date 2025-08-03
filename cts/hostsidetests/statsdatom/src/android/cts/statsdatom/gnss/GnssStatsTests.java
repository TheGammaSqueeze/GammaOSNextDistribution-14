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

package android.cts.statsdatom.gnss;

import com.android.tradefed.util.RunUtil;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.cts.statsdatom.lib.AtomTestUtils;
import android.cts.statsdatom.lib.ConfigUtils;
import android.cts.statsdatom.lib.DeviceUtils;
import android.cts.statsdatom.lib.ReportUtils;

import com.android.os.AtomsProto;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.testtype.DeviceTestCase;
import com.android.tradefed.testtype.IBuildReceiver;

import java.util.List;

public class GnssStatsTests extends DeviceTestCase implements IBuildReceiver {
    private static final String FEATURE_LOCATION_GPS = "android.hardware.location.gps";

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

    public void testGnssStats() throws Exception {
        // Get GnssMetrics as a simple gauge metric.
        ConfigUtils.uploadConfigForPulledAtom(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                AtomsProto.Atom.GNSS_STATS_FIELD_NUMBER);

        if (!DeviceUtils.hasFeature(getDevice(), FEATURE_LOCATION_GPS)) return;
        // Allowlist this app against background location request throttling
        String origWhitelist = getDevice().executeShellCommand(
                "settings get global location_background_throttle_package_whitelist").trim();
        getDevice().executeShellCommand(String.format(
                "settings put global location_background_throttle_package_whitelist %s",
                DeviceUtils.STATSD_ATOM_TEST_PKG));

        try {
            DeviceUtils.runDeviceTestsOnStatsdApp(getDevice(), ".AtomTests", "testGpsStatus");

            RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);
            // Trigger a pull and wait for new pull before killing the process.
            AtomTestUtils.sendAppBreadcrumbReportedAtom(getDevice());
            RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);

            // Assert about GnssMetrics for the test app.
            List<AtomsProto.Atom> atoms = ReportUtils.getGaugeMetricAtoms(getDevice());

            boolean found = false;
            for (AtomsProto.Atom atom : atoms) {
                AtomsProto.GnssStats state = atom.getGnssStats();
                found = true;
                if ((state.getSvStatusReports() > 0 || state.getL5SvStatusReports() > 0)
                        && state.getLocationReports() == 0) {
                    // Device is detected to be indoors and not able to acquire location.
                    // flaky test device
                    break;
                }
                assertThat(state.getLocationReports()).isGreaterThan((long) 0);
                assertThat(state.getLocationFailureReports()).isAtLeast((long) 0);
                assertThat(state.getTimeToFirstFixReports()).isGreaterThan((long) 0);
                assertThat(state.getTimeToFirstFixMillis()).isGreaterThan((long) 0);
                assertThat(state.getPositionAccuracyReports()).isGreaterThan((long) 0);
                assertThat(state.getPositionAccuracyMeters()).isGreaterThan((long) 0);
                assertThat(state.getTopFourAverageCn0Reports()).isGreaterThan((long) 0);
                assertThat(state.getTopFourAverageCn0DbMhz()).isGreaterThan((long) 0);
                assertThat(state.getL5TopFourAverageCn0Reports()).isAtLeast((long) 0);
                assertThat(state.getL5TopFourAverageCn0DbMhz()).isAtLeast((long) 0);
                assertThat(state.getSvStatusReports()).isAtLeast((long) 0);
                assertThat(state.getSvStatusReportsUsedInFix()).isAtLeast((long) 0);
                assertThat(state.getL5SvStatusReports()).isAtLeast((long) 0);
                assertThat(state.getL5SvStatusReportsUsedInFix()).isAtLeast((long) 0);
            }
            assertWithMessage(String.format("Did not find a matching atom"))
                    .that(found).isTrue();
        } finally {
            if ("null".equals(origWhitelist) || "".equals(origWhitelist)) {
                getDevice().executeShellCommand(
                        "settings delete global location_background_throttle_package_whitelist");
            } else {
                getDevice().executeShellCommand(String.format(
                        "settings put global location_background_throttle_package_whitelist %s",
                        origWhitelist));
            }
        }
    }
}
