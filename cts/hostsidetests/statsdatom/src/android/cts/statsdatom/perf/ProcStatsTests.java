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

package android.cts.statsdatom.perf;

import static com.google.common.truth.Truth.assertThat;

import android.cts.statsdatom.lib.AtomTestUtils;
import android.cts.statsdatom.lib.ConfigUtils;
import android.cts.statsdatom.lib.DeviceUtils;
import android.cts.statsdatom.lib.ReportUtils;

import com.android.os.AtomsProto;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.testtype.DeviceTestCase;
import com.android.tradefed.testtype.IBuildReceiver;
import com.android.tradefed.util.RunUtil;

/**
 * Statsd atom tests for ProcessState and ProcessAssociation.
 */
public class ProcStatsTests extends DeviceTestCase implements IBuildReceiver {
    private static final String ACTION_END_IMMEDIATELY = "action.end_immediately";
    private static final String ACTION_SHOW_APPLICATION_OVERLAY = "action.show_application_overlay";

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

    public void testProcessState() throws Exception {
        try (AutoCloseable c = DeviceUtils.withActivity(
                getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                "StatsdCtsForegroundActivity", "action", ACTION_SHOW_APPLICATION_OVERLAY)) {
            RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG * 2);
            getDevice().executeShellCommand("dumpsys procstats --commit");
            ConfigUtils.uploadConfigForPulledAtom(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                    AtomsProto.Atom.PROCESS_STATE_FIELD_NUMBER);
            AtomTestUtils.sendAppBreadcrumbReportedAtom(getDevice());
            RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);
            AtomsProto.Atom atom = ReportUtils.getGaugeMetricAtoms(getDevice()).stream()
                    .filter(a -> a.getProcessState().getProcessName().equals(
                            DeviceUtils.STATSD_ATOM_TEST_PKG))
                    .filter(a -> a.getProcessState().getTopSeconds() > 0)
                    .findFirst()
                    .orElse(null);
            assertThat(atom).isNotNull();
        }
    }

    public void testCachedState() throws Exception {
        ConfigUtils.uploadConfigForPulledAtom(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                AtomsProto.Atom.PROCESS_STATE_FIELD_NUMBER);
        // Start extremely short-lived activity, so app goes into cache state after the background
        // work completes.
        DeviceUtils.executeBackgroundService(getDevice(), ACTION_END_IMMEDIATELY);
        // Leave some time for the app to transition into cached state before grabbing the stats.
        final int waitTimeMs = 2_000;
        RunUtil.getDefault().sleep(waitTimeMs);
        getDevice().executeShellCommand("dumpsys procstats --commit");
        AtomTestUtils.sendAppBreadcrumbReportedAtom(getDevice());
        AtomsProto.Atom atom = ReportUtils.getGaugeMetricAtoms(getDevice()).stream()
                .filter(a -> a.getProcessState().getProcessName().equals(
                        DeviceUtils.STATSD_ATOM_TEST_PKG))
                .filter(a -> a.getProcessState().getCachedSeconds() > 0)
                .findFirst()
                .orElse(null);
        assertThat(atom).isNotNull();
    }
}
