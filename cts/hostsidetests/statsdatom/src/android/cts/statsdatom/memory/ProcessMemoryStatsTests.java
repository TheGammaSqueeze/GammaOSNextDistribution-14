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

package android.cts.statsdatom.memory;

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

public class ProcessMemoryStatsTests extends DeviceTestCase implements IBuildReceiver {
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

    public void testProcessMemoryState() throws Exception {
        // Get ProcessMemoryState as a simple gauge metric.
        ConfigUtils.uploadConfigForPulledAtom(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                AtomsProto.Atom.PROCESS_MEMORY_STATE_FIELD_NUMBER);

        // Start test app.
        try (AutoCloseable a = DeviceUtils.withActivity(getDevice(),
                DeviceUtils.STATSD_ATOM_TEST_PKG, "StatsdCtsForegroundActivity", "action",
                "action.show_notification")) {
            RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);
            // Trigger a pull and wait for new pull before killing the process.
            AtomTestUtils.sendAppBreadcrumbReportedAtom(getDevice());
            RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);
        }

        // Assert about ProcessMemoryState for the test app.
        List<AtomsProto.Atom> atoms = ReportUtils.getGaugeMetricAtoms(getDevice());
        int uid = DeviceUtils.getStatsdTestAppUid(getDevice());
        boolean found = false;
        for (AtomsProto.Atom atom : atoms) {
            AtomsProto.ProcessMemoryState state = atom.getProcessMemoryState();
            if (state.getUid() != uid) {
                continue;
            }
            found = true;
            assertThat(state.getProcessName()).isEqualTo(DeviceUtils.STATSD_ATOM_TEST_PKG);
            assertThat(state.getOomAdjScore()).isAtLeast(0);
            assertThat(state.getPageFault()).isAtLeast(0L);
            assertThat(state.getPageMajorFault()).isAtLeast(0L);
            assertThat(state.getRssInBytes()).isAtLeast(0L);
            assertThat(state.getCacheInBytes()).isAtLeast(0L);
            assertThat(state.getSwapInBytes()).isAtLeast(0L);
        }
        assertWithMessage(String.format("Did not find a matching atom for uid %d", uid))
                .that(found).isTrue();
    }

    public void testProcessMemoryHighWaterMark() throws Exception {
        // Get ProcessMemoryHighWaterMark as a simple gauge metric.
        ConfigUtils.uploadConfigForPulledAtom(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                AtomsProto.Atom.PROCESS_MEMORY_HIGH_WATER_MARK_FIELD_NUMBER);

        // Start test app and trigger a pull while it is running.
        try (AutoCloseable a = DeviceUtils.withActivity(getDevice(),
                DeviceUtils.STATSD_ATOM_TEST_PKG, "StatsdCtsForegroundActivity", "action",
                "action.show_notification")) {
            RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_SHORT);
            // Trigger a pull and wait for new pull before killing the process.
            AtomTestUtils.sendAppBreadcrumbReportedAtom(getDevice());
            RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);
        }

        // Assert about ProcessMemoryHighWaterMark for the test app, statsd and system server.
        List<AtomsProto.Atom> atoms = ReportUtils.getGaugeMetricAtoms(getDevice());
        int uid = DeviceUtils.getStatsdTestAppUid(getDevice());
        boolean foundTestApp = false;
        boolean foundStatsd = false;
        boolean foundSystemServer = false;
        for (AtomsProto.Atom atom : atoms) {
            AtomsProto.ProcessMemoryHighWaterMark state = atom.getProcessMemoryHighWaterMark();
            if (state.getUid() == uid) {
                foundTestApp = true;
                assertThat(state.getProcessName()).isEqualTo(DeviceUtils.STATSD_ATOM_TEST_PKG);
                assertThat(state.getRssHighWaterMarkInBytes()).isGreaterThan(0L);
            } else if (state.getProcessName().contains("/statsd")) {
                foundStatsd = true;
                assertThat(state.getRssHighWaterMarkInBytes()).isGreaterThan(0L);
            } else if (state.getProcessName().equals("system")) {
                foundSystemServer = true;
                assertThat(state.getRssHighWaterMarkInBytes()).isGreaterThan(0L);
            }
        }
        assertWithMessage(String.format("Did not find a matching atom for test app uid=%d", uid))
                .that(foundTestApp).isTrue();
        assertWithMessage("Did not find a matching atom for statsd").that(foundStatsd).isTrue();
        assertWithMessage("Did not find a matching atom for system server")
                .that(foundSystemServer).isTrue();
    }

    public void testProcessMemorySnapshot() throws Exception {
        // Get ProcessMemorySnapshot as a simple gauge metric.
        ConfigUtils.uploadConfigForPulledAtom(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                AtomsProto.Atom.PROCESS_MEMORY_SNAPSHOT_FIELD_NUMBER);

        // Start test app and trigger a pull while it is running.
        try (AutoCloseable a = DeviceUtils.withActivity(getDevice(),
                DeviceUtils.STATSD_ATOM_TEST_PKG, "StatsdCtsForegroundActivity", "action",
                "action.show_notification")) {
            RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);
            AtomTestUtils.sendAppBreadcrumbReportedAtom(getDevice());
        }

        // Assert about ProcessMemorySnapshot for the test app, statsd and system server.
        List<AtomsProto.Atom> atoms = ReportUtils.getGaugeMetricAtoms(getDevice());
        int uid = DeviceUtils.getStatsdTestAppUid(getDevice());
        boolean foundTestApp = false;
        boolean foundStatsd = false;
        boolean foundSystemServer = false;
        for (AtomsProto.Atom atom : atoms) {
            AtomsProto.ProcessMemorySnapshot snapshot = atom.getProcessMemorySnapshot();
            if (snapshot.getUid() == uid) {
                foundTestApp = true;
                assertThat(snapshot.getProcessName()).isEqualTo(DeviceUtils.STATSD_ATOM_TEST_PKG);
            } else if (snapshot.getProcessName().contains("/statsd")) {
                foundStatsd = true;
            } else if (snapshot.getProcessName().equals("system")) {
                foundSystemServer = true;
            }

            assertThat(snapshot.getPid()).isGreaterThan(0);
            assertThat(snapshot.getAnonRssAndSwapInKilobytes()).isAtLeast(0);
            assertThat(snapshot.getAnonRssAndSwapInKilobytes()).isEqualTo(
                    snapshot.getAnonRssInKilobytes() + snapshot.getSwapInKilobytes());
            assertThat(snapshot.getRssInKilobytes()).isAtLeast(0);
            assertThat(snapshot.getAnonRssInKilobytes()).isAtLeast(0);
            assertThat(snapshot.getSwapInKilobytes()).isAtLeast(0);
        }
        assertWithMessage(String.format("Did not find a matching atom for test app uid=%d", uid))
                .that(foundTestApp).isTrue();
        assertWithMessage("Did not find a matching atom for statsd").that(foundStatsd).isTrue();
        assertWithMessage("Did not find a matching atom for system server")
                .that(foundSystemServer).isTrue();
    }

}
