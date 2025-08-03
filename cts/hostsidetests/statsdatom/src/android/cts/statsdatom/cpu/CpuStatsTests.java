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

package android.cts.statsdatom.cpu;

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

public class CpuStatsTests extends DeviceTestCase implements IBuildReceiver {
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

    public void testCpuTimePerUid() throws Exception {
        if (DeviceUtils.hasFeature(getDevice(), DeviceUtils.FEATURE_WATCH)) return;

        ConfigUtils.uploadConfigForPulledAtomWithUid(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                AtomsProto.Atom.CPU_TIME_PER_UID_FIELD_NUMBER,  /*uidInAttributionChain=*/false);

        // Do some trivial work on the app
        DeviceUtils.runDeviceTestsOnStatsdApp(getDevice(), ".AtomTests", "testSimpleCpu");
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_SHORT);
        // Trigger atom pull
        AtomTestUtils.sendAppBreadcrumbReportedAtom(getDevice());
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);

        // Verify correctness of data
        List<AtomsProto.Atom> atoms = ReportUtils.getGaugeMetricAtoms(getDevice());
        boolean found = false;
        int appUid = DeviceUtils.getStatsdTestAppUid(getDevice());
        for (AtomsProto.Atom atom : atoms) {
            assertThat(atom.getCpuTimePerUid().getUid()).isEqualTo(appUid);
            assertThat(atom.getCpuTimePerUid().getUserTimeMicros()).isGreaterThan(0L);
            assertThat(atom.getCpuTimePerUid().getSysTimeMicros()).isGreaterThan(0L);
            found = true;
        }
        assertWithMessage("Found no CpuTimePerUid atoms from uid " + appUid).that(found).isTrue();
    }

    public void testCpuTimePerClusterFreq() throws Exception {
        ConfigUtils.uploadConfigForPulledAtom(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                AtomsProto.Atom.CPU_TIME_PER_CLUSTER_FREQ_FIELD_NUMBER);

        // Do some trivial work on the app
        DeviceUtils.runDeviceTestsOnStatsdApp(getDevice(), ".AtomTests", "testSimpleCpu");
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_SHORT);
        // Trigger atom pull
        AtomTestUtils.sendAppBreadcrumbReportedAtom(getDevice());
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);

        // The list of atoms will be empty if the atom is not supported.
        List<AtomsProto.Atom> atoms = ReportUtils.getGaugeMetricAtoms(getDevice());

        for (AtomsProto.Atom atom : atoms) {
            assertThat(atom.getCpuTimePerClusterFreq().getCluster()).isAtLeast(0);
            assertThat(atom.getCpuTimePerClusterFreq().getFreqKhz()).isAtLeast(0);
            assertThat(atom.getCpuTimePerClusterFreq().getTimeMillis()).isAtLeast(0);
        }
    }

    public void testCpuCyclesPerUidCluster() throws Exception {
        ConfigUtils.uploadConfigForPulledAtom(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                AtomsProto.Atom.CPU_CYCLES_PER_UID_CLUSTER_FIELD_NUMBER);

        // Do some trivial work on the app
        DeviceUtils.runDeviceTestsOnStatsdApp(getDevice(), ".AtomTests", "testSimpleCpu");
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_SHORT);
        // Trigger atom pull
        AtomTestUtils.sendAppBreadcrumbReportedAtom(getDevice());
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);

        // The list of atoms will be empty if the atom is not supported.
        List<AtomsProto.Atom> atoms = ReportUtils.getGaugeMetricAtoms(getDevice());

        for (AtomsProto.Atom atom : atoms) {
            assertThat(atom.getCpuCyclesPerUidCluster().getUid()).isAtLeast(0);
            assertThat(atom.getCpuCyclesPerUidCluster().getCluster()).isAtLeast(0);
            assertThat(atom.getCpuCyclesPerUidCluster().getMcycles()).isAtLeast(0);
            assertThat(atom.getCpuCyclesPerUidCluster().getTimeMillis()).isAtLeast(0);
            assertThat(atom.getCpuCyclesPerUidCluster().getPowerProfileEstimate()).isAtLeast(0);
        }
    }

    public void testCpuCyclesPerThreadGroupCluster() throws Exception {
        ConfigUtils.uploadConfigForPulledAtom(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                AtomsProto.Atom.CPU_CYCLES_PER_THREAD_GROUP_CLUSTER_FIELD_NUMBER);

        // Do some trivial work on the app
        DeviceUtils.runDeviceTestsOnStatsdApp(getDevice(), ".AtomTests", "testSimpleCpu");
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_SHORT);
        // Trigger atom pull
        AtomTestUtils.sendAppBreadcrumbReportedAtom(getDevice());
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);

        // The list of atoms will be empty if the atom is not supported.
        List<AtomsProto.Atom> atoms = ReportUtils.getGaugeMetricAtoms(getDevice());

        for (AtomsProto.Atom atom : atoms) {
            assertThat(atom.getCpuCyclesPerThreadGroupCluster().getThreadGroup()).isNotEqualTo(
              AtomsProto.CpuCyclesPerThreadGroupCluster.ThreadGroup.UNKNOWN_THREAD_GROUP);
            assertThat(atom.getCpuCyclesPerThreadGroupCluster().getCluster()).isAtLeast(0);
            assertThat(atom.getCpuCyclesPerThreadGroupCluster().getMcycles()).isAtLeast(0);
            assertThat(atom.getCpuCyclesPerThreadGroupCluster().getTimeMillis()).isAtLeast(0);
        }
    }
}
