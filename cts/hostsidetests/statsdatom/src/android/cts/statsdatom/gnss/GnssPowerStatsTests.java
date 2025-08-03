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

package android.cts.statsdatom.gnss;

import com.android.tradefed.util.RunUtil;
import static com.google.common.truth.Truth.assertThat;

import android.cts.statsdatom.lib.AtomTestUtils;
import android.cts.statsdatom.lib.ConfigUtils;
import android.cts.statsdatom.lib.DeviceUtils;
import android.cts.statsdatom.lib.ReportUtils;

import com.android.os.AtomsProto;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.testtype.DeviceTestCase;
import com.android.tradefed.testtype.IBuildReceiver;

import java.util.List;

public class GnssPowerStatsTests extends DeviceTestCase implements IBuildReceiver {
    private static final boolean OPTIONAL_TESTS_ENABLED = true;

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

    public void testGnssPowerStats() throws Exception {
        if (!OPTIONAL_TESTS_ENABLED) return;

        ConfigUtils.uploadConfigForPulledAtom(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                AtomsProto.Atom.GNSS_POWER_STATS_FIELD_NUMBER);

        AtomTestUtils.sendAppBreadcrumbReportedAtom(getDevice());
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);

        List<AtomsProto.Atom> dataList = ReportUtils.getGaugeMetricAtoms(getDevice());

        for (AtomsProto.Atom atom : dataList) {
            assertThat(atom.getGnssPowerStats().getElapsedRealtimeUncertaintyNanos())
                    .isAtLeast(0L);
            assertThat(atom.getGnssPowerStats().getTotalEnergyMicroJoule()).isAtLeast(0L);
            assertThat(atom.getGnssPowerStats().getSinglebandTrackingModeEnergyMicroJoule())
                    .isAtLeast(0L);
            assertThat(atom.getGnssPowerStats().getMultibandTrackingModeEnergyMicroJoule())
                    .isAtLeast(0L);
            assertThat(atom.getGnssPowerStats().getSinglebandAcquisitionModeEnergyMicroJoule())
                    .isAtLeast(0L);
            assertThat(atom.getGnssPowerStats().getMultibandAcquisitionModeEnergyMicroJoule())
                    .isAtLeast(0L);
            assertThat(atom.getGnssPowerStats().getVendorSpecificPowerModesEnergyMicroJoule0())
                    .isAtLeast(0L);
            assertThat(atom.getGnssPowerStats().getVendorSpecificPowerModesEnergyMicroJoule1())
                    .isAtLeast(0L);
            assertThat(atom.getGnssPowerStats().getVendorSpecificPowerModesEnergyMicroJoule2())
                    .isAtLeast(0L);
            assertThat(atom.getGnssPowerStats().getVendorSpecificPowerModesEnergyMicroJoule3())
                    .isAtLeast(0L);
            assertThat(atom.getGnssPowerStats().getVendorSpecificPowerModesEnergyMicroJoule4())
                    .isAtLeast(0L);
            assertThat(atom.getGnssPowerStats().getVendorSpecificPowerModesEnergyMicroJoule5())
                    .isAtLeast(0L);
            assertThat(atom.getGnssPowerStats().getVendorSpecificPowerModesEnergyMicroJoule6())
                    .isAtLeast(0L);
            assertThat(atom.getGnssPowerStats().getVendorSpecificPowerModesEnergyMicroJoule7())
                    .isAtLeast(0L);
            assertThat(atom.getGnssPowerStats().getVendorSpecificPowerModesEnergyMicroJoule8())
                    .isAtLeast(0L);
            assertThat(atom.getGnssPowerStats().getVendorSpecificPowerModesEnergyMicroJoule9())
                    .isAtLeast(0L);
        }
    }
}
