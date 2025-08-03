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
package android.cts.statsdatom.memorysafety;

import static com.google.common.truth.Truth.assertThat;

import android.cts.statsdatom.lib.AtomTestUtils;
import android.cts.statsdatom.lib.ConfigUtils;
import android.cts.statsdatom.lib.DeviceUtils;
import android.cts.statsdatom.lib.ReportUtils;

import com.android.internal.os.StatsdConfigProto.StatsdConfig;
import com.android.os.AtomsProto.Atom;
import com.android.os.memorysafety.MemorysafetyExtensionAtoms;
import com.android.os.memorysafety.MteState;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.testtype.DeviceTestCase;
import com.android.tradefed.testtype.IBuildReceiver;
import com.android.tradefed.util.RunUtil;

import com.google.protobuf.ExtensionRegistry;

import java.io.File;
import java.io.FileInputStream;
import java.util.List;
import java.util.Scanner;

public class MteStateTest extends DeviceTestCase implements IBuildReceiver {

    private IBuildInfo mCtsBuild;

    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mCtsBuild = buildInfo;
    }

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

    public void testMteState() throws Exception {
        final StatsdConfig.Builder config =
                ConfigUtils.createConfigBuilder(DeviceUtils.STATSD_ATOM_TEST_PKG);
        ConfigUtils.addGaugeMetric(
                config, MemorysafetyExtensionAtoms.MTE_STATE_FIELD_NUMBER);
        ConfigUtils.uploadConfig(getDevice(), config);

        AtomTestUtils.sendAppBreadcrumbReportedAtom(getDevice());
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_SHORT);

        ExtensionRegistry registry = ExtensionRegistry.newInstance();
        MemorysafetyExtensionAtoms.registerAllExtensions(registry);
        boolean hasMte = false;
        File cpuInfoFile = getDevice().pullFile("/proc/cpuinfo");
        try (FileInputStream istr = new FileInputStream(cpuInfoFile);
                Scanner sc = new Scanner(istr)) {
            while (sc.hasNextLine()) {
                if (sc.nextLine().contains("mte")) {
                    hasMte = true;
                    break;
                }
            }
        }

        final List<Atom> atoms = ReportUtils.getGaugeMetricAtoms(getDevice(), registry, false);
        assertThat(atoms.size()).isAtLeast(1);
        for (Atom atom : atoms) {
            assertThat(atom.hasExtension(MemorysafetyExtensionAtoms.mteState)).isTrue();
            MteState data = atom.getExtension(MemorysafetyExtensionAtoms.mteState);
            assertThat(data.getState()).isEqualTo(hasMte ? MteState.State.ON : MteState.State.OFF);
        }
    }
}
