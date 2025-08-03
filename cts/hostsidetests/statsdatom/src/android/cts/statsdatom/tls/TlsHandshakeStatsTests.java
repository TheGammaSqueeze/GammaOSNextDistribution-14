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

package android.cts.statsdatom.tls;

import com.android.tradefed.util.RunUtil;
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

public class TlsHandshakeStatsTests extends DeviceTestCase implements IBuildReceiver {

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

    public void testTlsHandshake()
            throws Exception {
        final int atomTag = AtomsProto.Atom.TLS_HANDSHAKE_REPORTED_FIELD_NUMBER;
        ConfigUtils.uploadConfigForPushedAtom(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                atomTag);

        DeviceUtils.runDeviceTestsOnStatsdApp(getDevice(), ".AtomTests", "testTlsHandshake");

        // Sorted list of events in order in which they occurred.
        List<StatsLog.EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());

        assertThat(data.size()).isAtLeast(2);
        AtomsProto.TlsHandshakeReported atom = data.get(0).getAtom().getTlsHandshakeReported();
        AtomsProto.TlsHandshakeReported atom2 = data.get(1).getAtom().getTlsHandshakeReported();
        assertThat(atom.getProtocol().toString()).contains("TLS_V1_3");
        assertThat(atom2.getProtocol().toString()).contains("TLS_V1_3");
    }
}