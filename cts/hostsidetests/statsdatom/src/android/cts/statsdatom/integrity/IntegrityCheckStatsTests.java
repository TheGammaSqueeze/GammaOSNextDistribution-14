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

package android.cts.statsdatom.integrity;

import com.android.tradefed.util.RunUtil;
import static com.android.os.AtomsProto.IntegrityCheckResultReported.Response.ALLOWED;

import static com.google.common.truth.Truth.assertThat;

import android.cts.statsdatom.lib.AtomTestUtils;
import android.cts.statsdatom.lib.ConfigUtils;
import android.cts.statsdatom.lib.DeviceUtils;
import android.cts.statsdatom.lib.ReportUtils;

import com.android.os.AtomsProto;
import com.android.os.StatsLog;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.testtype.DeviceTestCase;
import com.android.tradefed.testtype.IBuildReceiver;

import java.util.List;

public class IntegrityCheckStatsTests extends DeviceTestCase implements IBuildReceiver {
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


    public void testIntegrityCheckAtomReportedDuringInstall() throws Exception {
        ConfigUtils.uploadConfigForPushedAtom(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                AtomsProto.Atom.INTEGRITY_CHECK_RESULT_REPORTED_FIELD_NUMBER);

        DeviceUtils.uninstallStatsdTestApp(getDevice());
        DeviceUtils.installStatsdTestApp(getDevice(), mCtsBuild);

        List<StatsLog.EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());

        assertThat(data.size()).isEqualTo(1);
        assertThat(data.get(0).getAtom().hasIntegrityCheckResultReported()).isTrue();
        AtomsProto.IntegrityCheckResultReported result = data.get(0)
                .getAtom().getIntegrityCheckResultReported();
        assertThat(result.getPackageName()).isEqualTo(DeviceUtils.STATSD_ATOM_TEST_PKG);
        // we do not assert on certificates since it seem to differ by device.
        assertThat(result.getInstallerPackageName()).isEqualTo("adb");
        long testPackageVersion = 10;
        assertThat(result.getVersionCode()).isEqualTo(testPackageVersion);
        assertThat(result.getResponse()).isEqualTo(ALLOWED);
        assertThat(result.getCausedByAppCertRule()).isFalse();
        assertThat(result.getCausedByInstallerRule()).isFalse();
    }
}
