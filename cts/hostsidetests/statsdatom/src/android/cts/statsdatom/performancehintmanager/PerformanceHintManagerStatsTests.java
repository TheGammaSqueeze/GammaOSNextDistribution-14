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

package android.cts.statsdatom.performancehintmanager;

import static com.google.common.truth.Truth.assertThat;

import android.cts.statsdatom.lib.AtomTestUtils;
import android.cts.statsdatom.lib.ConfigUtils;
import android.cts.statsdatom.lib.DeviceUtils;
import android.cts.statsdatom.lib.ReportUtils;

import com.android.ddmlib.testrunner.TestResult.TestStatus;
import com.android.os.AtomsProto;
import com.android.os.StatsLog;
import com.android.os.adpf.ADPFSystemComponentInfo;
import com.android.os.adpf.PerformanceHintSessionReported;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.result.TestDescription;
import com.android.tradefed.result.TestRunResult;
import com.android.tradefed.testtype.DeviceTestCase;
import com.android.tradefed.testtype.IBuildReceiver;
import com.android.tradefed.util.RunUtil;

import java.util.List;

/**
 * Test for Performance Hint Manager stats.
 * This test is mainly to test ADPF data collection
 *
 *  <p>Build/Install/Run:
 *  atest CtsStatsdAtomHostTestCases:PerformanceHintManagerStatsTests
 */
public class PerformanceHintManagerStatsTests extends DeviceTestCase implements IBuildReceiver {
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

    public void testCreateHintSessionStatsd() throws Exception {
        final int androidSApiLevel = 31; // android.os.Build.VERSION_CODES.S
        final int firstApiLevel = Integer.parseInt(
                DeviceUtils.getProperty(getDevice(), "ro.product.first_api_level"));
        final TestDescription testCreateHintSessionDescription = TestDescription.fromString(
                "com.android.server.cts.device.statsdatom.AtomTests#testCreateHintSession");
        ConfigUtils.uploadConfigForPushedAtom(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                AtomsProto.Atom.PERFORMANCE_HINT_SESSION_REPORTED_FIELD_NUMBER);
        TestRunResult testRunResult = DeviceUtils.runDeviceTestsOnStatsdApp(getDevice(),
                ".AtomTests", "testCreateHintSession");

        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);

        TestStatus testCreateHintSessionStatus =
                testRunResult.getTestResults().get(testCreateHintSessionDescription).getStatus();
        assertThat(testCreateHintSessionStatus).isNotEqualTo(TestStatus.FAILURE);
        if (testCreateHintSessionStatus == TestStatus.ASSUMPTION_FAILURE) {
            // test requirement does not meet, the device does not support
            // ADPF hint session, skipping the test
            return;
        }

        List<StatsLog.EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());

        if (firstApiLevel < androidSApiLevel && data.size() == 0) {
            // test requirement does not meet, the device does not support
            // ADPF hint session, skipping the test
            return;
        }

        assertThat(data.size()).isAtLeast(1);
        PerformanceHintSessionReported a0 =
                data.get(0).getAtom().getPerformanceHintSessionReported();
        assertThat(a0.getPackageUid()).isGreaterThan(10000);  // Not a system service UID.
        assertThat(a0.getSessionId()).isNotEqualTo(0);
        assertThat(a0.getTargetDurationNs()).isEqualTo(16666666L);
        assertThat(a0.getTidCount()).isEqualTo(1);
    }

    public void testAdpfSystemComponentStatsd() throws Exception {
        final boolean isSurfaceFlingerCpuHintEnabled = Boolean.parseBoolean(
                DeviceUtils.getProperty(getDevice(), "debug.sf.enable_adpf_cpu_hint"));
        final boolean isHwuiHintEnabled = Boolean.parseBoolean(
                DeviceUtils.getProperty(getDevice(), "debug.hwui.use_hint_manager"));
        ConfigUtils.uploadConfigForPulledAtom(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                AtomsProto.Atom.ADPF_SYSTEM_COMPONENT_INFO_FIELD_NUMBER);
        AtomTestUtils.sendAppBreadcrumbReportedAtom(getDevice());
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);

        List<AtomsProto.Atom> data = ReportUtils.getGaugeMetricAtoms(getDevice());
        assertThat(data.size()).isAtLeast(1);
        ADPFSystemComponentInfo a0 = data.get(0).getAdpfSystemComponentInfo();
        assertThat(a0.getSurfaceflingerCpuHintEnabled()).isEqualTo(isSurfaceFlingerCpuHintEnabled);
        assertThat(a0.getHwuiHintEnabled()).isEqualTo(isHwuiHintEnabled);
    }
}
