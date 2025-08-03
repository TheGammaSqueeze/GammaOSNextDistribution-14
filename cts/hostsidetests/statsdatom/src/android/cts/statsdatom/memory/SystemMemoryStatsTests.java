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

package android.cts.statsdatom.memory;

import com.android.tradefed.util.RunUtil;
import static com.google.common.truth.Truth.assertThat;

import android.cts.statsdatom.lib.AtomTestUtils;
import android.cts.statsdatom.lib.ConfigUtils;
import android.cts.statsdatom.lib.DeviceUtils;
import android.cts.statsdatom.lib.ReportUtils;

import com.android.compatibility.common.util.PropertyUtil;
import com.android.os.AtomsProto;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.testtype.DeviceTestCase;
import com.android.tradefed.testtype.IBuildReceiver;

import java.util.List;

public class SystemMemoryStatsTests extends DeviceTestCase implements IBuildReceiver {
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

    public void testSystemMemoryAtom() throws Exception {
        List<AtomsProto.Atom> atoms = pullSystemMemoryAsGaugeMetric();
        assertThat(atoms).hasSize(1);
        AtomsProto.SystemMemory systemMemory = atoms.get(0).getSystemMemory();
        assertThat(systemMemory.getUnreclaimableSlabKb()).isAtLeast(0);
        assertThat(systemMemory.getVmallocUsedKb()).isAtLeast(0);
        assertThat(systemMemory.getPageTablesKb()).isAtLeast(0);
        assertThat(systemMemory.getKernelStackKb()).isAtLeast(0);
        if (PropertyUtil.getFirstApiLevel(getDevice()) >= 30) {
            assertThat(systemMemory.getTotalIonKb()).isAtLeast(0);
        }
    }

    /** Returns SystemMemory atoms pulled as a simple gauge metric while test app is running. */
    private List<AtomsProto.Atom> pullSystemMemoryAsGaugeMetric() throws Exception {
        // Get SystemMemory as a simple gauge metric.
        ConfigUtils.uploadConfigForPulledAtom(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                AtomsProto.Atom.SYSTEM_MEMORY_FIELD_NUMBER);
        AtomTestUtils.sendAppBreadcrumbReportedAtom(getDevice());
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);
        return ReportUtils.getGaugeMetricAtoms(getDevice());
    }
}
