/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.cts.statsdatom.powermanager;

import static com.google.common.truth.Truth.assertThat;

import android.cts.statsdatom.lib.AtomTestUtils;
import android.cts.statsdatom.lib.ConfigUtils;
import android.cts.statsdatom.lib.DeviceUtils;
import android.cts.statsdatom.lib.ReportUtils;
import android.os.Flags;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.host.HostFlagsValueProvider;

import com.android.ddmlib.testrunner.TestResult.TestStatus;
import com.android.os.AtomsProto;
import com.android.os.StatsLog;
import com.android.os.adpf.AdpfExtensionAtoms;
import com.android.os.adpf.ThermalApiStatus;
import com.android.os.adpf.ThermalHeadroomCalled;
import com.android.os.adpf.ThermalHeadroomThresholds;
import com.android.os.adpf.ThermalHeadroomThresholdsCalled;
import com.android.os.adpf.ThermalStatusCalled;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.result.TestDescription;
import com.android.tradefed.result.TestRunResult;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.IBuildReceiver;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;
import com.android.tradefed.util.RunUtil;

import com.google.protobuf.ExtensionRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

/**
 * Test for Power Manager stats.
 *
 * <p>Build/Install/Run:
 * atest CtsStatsdAtomHostTestCases:PowerManagerStatsTests
 */
@RunWith(DeviceJUnit4ClassRunner.class)
public class PowerManagerStatsTests extends BaseHostJUnit4Test implements IBuildReceiver {
    private static final String DEVICE_TEST_PKG = "com.android.server.cts.device.statsdatom";
    private static final String DEVICE_TEST_CLASS = ".PowerManagerTests";

    private IBuildInfo mCtsBuild;

    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            HostFlagsValueProvider.createCheckFlagsRule(this::getDevice);

    @Before
    public void setUp() throws Exception {
        assertThat(mCtsBuild).isNotNull();
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
        DeviceUtils.installStatsdTestApp(getDevice(), mCtsBuild);
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);
    }

    @After
    public void tearDown() throws Exception {
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
        DeviceUtils.uninstallStatsdTestApp(getDevice());
    }

    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mCtsBuild = buildInfo;
    }

    @Test
    public void testThermalHeadroomCalledIsPushed() throws Exception {
        final String testMethod = "testGetThermalHeadroom";
        final TestDescription desc = TestDescription.fromString(
                DEVICE_TEST_PKG + DEVICE_TEST_CLASS + "#" + testMethod);
        ConfigUtils.uploadConfigForPushedAtom(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                AdpfExtensionAtoms.THERMAL_HEADROOM_CALLED_FIELD_NUMBER);
        TestRunResult testRunResult = DeviceUtils.runDeviceTestsOnStatsdApp(getDevice(),
                DEVICE_TEST_CLASS, testMethod);

        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);

        TestStatus status = testRunResult.getTestResults().get(desc).getStatus();
        assertThat(status).isEqualTo(TestStatus.PASSED);
        ExtensionRegistry registry = ExtensionRegistry.newInstance();
        AdpfExtensionAtoms.registerAllExtensions(registry);
        List<StatsLog.EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice(),
                registry);
        assertThat(data.size()).isAtLeast(1);

        ThermalHeadroomCalled a0 =
                data.get(0).getAtom().getExtension(AdpfExtensionAtoms.thermalHeadroomCalled);
        assertThat(a0.getApiStatus()).isNotEqualTo(
                ThermalApiStatus.UNSPECIFIED_THERMAL_API_FAILURE);
        if (a0.getApiStatus().equals(ThermalApiStatus.SUCCESS)) {
            assertThat(a0.getHeadroom()).isNotNaN();
        }
    }

    @Test
    public void testThermalStatusCalledIsPushed() throws Exception {
        final String testMethod = "testGetCurrentThermalStatus";
        final TestDescription desc = TestDescription.fromString(
                DEVICE_TEST_PKG + DEVICE_TEST_CLASS + "#" + testMethod);
        ConfigUtils.uploadConfigForPushedAtom(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                AdpfExtensionAtoms.THERMAL_STATUS_CALLED_FIELD_NUMBER);
        TestRunResult testRunResult = DeviceUtils.runDeviceTestsOnStatsdApp(getDevice(),
                DEVICE_TEST_CLASS, testMethod);

        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);

        TestStatus status = testRunResult.getTestResults().get(desc).getStatus();
        assertThat(status).isEqualTo(TestStatus.PASSED);
        ExtensionRegistry registry = ExtensionRegistry.newInstance();
        AdpfExtensionAtoms.registerAllExtensions(registry);
        List<StatsLog.EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice(),
                registry);
        assertThat(data.size()).isAtLeast(1);
        ThermalStatusCalled a0 =
                data.get(0).getAtom().getExtension(AdpfExtensionAtoms.thermalStatusCalled);
        assertThat(a0.getApiStatus()).isNotEqualTo(
                ThermalApiStatus.UNSPECIFIED_THERMAL_API_FAILURE);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ALLOW_THERMAL_HEADROOM_THRESHOLDS)
    public void testThermalHeadroomThresholdsCalledIsPushed() throws Exception {
        final String testMethod = "testGetThermalHeadroomThresholds";
        final TestDescription desc = TestDescription.fromString(
                DEVICE_TEST_PKG + DEVICE_TEST_CLASS + "#" + testMethod);
        ConfigUtils.uploadConfigForPushedAtom(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                AdpfExtensionAtoms.THERMAL_HEADROOM_THRESHOLDS_CALLED_FIELD_NUMBER);
        TestRunResult testRunResult = DeviceUtils.runDeviceTestsOnStatsdApp(getDevice(),
                DEVICE_TEST_CLASS, testMethod);

        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);

        TestStatus status = testRunResult.getTestResults().get(desc).getStatus();
        assertThat(status).isEqualTo(TestStatus.PASSED);
        ExtensionRegistry registry = ExtensionRegistry.newInstance();
        AdpfExtensionAtoms.registerAllExtensions(registry);
        List<StatsLog.EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice(),
                registry);
        assertThat(data.size()).isAtLeast(1);
        ThermalHeadroomThresholdsCalled a0 =
                data.get(0).getAtom().getExtension(
                        AdpfExtensionAtoms.thermalHeadroomThresholdsCalled);
        assertThat(a0.getApiStatus()).isNotEqualTo(
                ThermalApiStatus.UNSPECIFIED_THERMAL_API_FAILURE);
    }

    @Test
    public void testThermalHeadroomThresholdsIsPulled() throws Exception {
        ConfigUtils.uploadConfigForPulledAtom(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                AdpfExtensionAtoms.THERMAL_HEADROOM_THRESHOLDS_FIELD_NUMBER);
        AtomTestUtils.sendAppBreadcrumbReportedAtom(getDevice());
        RunUtil.getDefault().sleep(5 * AtomTestUtils.WAIT_TIME_LONG);

        ExtensionRegistry registry = ExtensionRegistry.newInstance();
        AdpfExtensionAtoms.registerAllExtensions(registry);
        List<AtomsProto.Atom> data = ReportUtils.getGaugeMetricAtoms(getDevice(), registry, false);
        assertThat(data.size()).isAtLeast(1);
        assertThat(data.get(0).hasExtension(AdpfExtensionAtoms.thermalHeadroomThresholds)).isTrue();
        ThermalHeadroomThresholds a0 = data.get(0).getExtension(
                AdpfExtensionAtoms.thermalHeadroomThresholds);
        assertThat(a0.getHeadroomCount()).isGreaterThan(0);
    }
}
