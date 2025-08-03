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

package android.cts.statsdatom.display;

import static com.google.common.truth.Truth.assertThat;

import android.cts.statsdatom.lib.AtomTestUtils;
import android.cts.statsdatom.lib.ConfigUtils;
import android.cts.statsdatom.lib.DeviceUtils;
import android.cts.statsdatom.lib.ReportUtils;

import com.android.compatibility.common.util.NonApiTest;
import com.android.os.AtomsProto;
import com.android.os.StatsLog;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.testtype.DeviceTestCase;
import com.android.tradefed.testtype.IBuildReceiver;
import com.android.tradefed.util.RunUtil;

import java.util.List;

@NonApiTest(exemptionReasons = {}, justification = "METRIC")
public class DisplayWakeReportedStatsTests extends DeviceTestCase implements IBuildReceiver {
    private static final int WAKE_REASON_UNKNOWN = 0;
    private static final int WAKE_REASON_APPLICATION = 2;
    private static final int WAKE_REASON_WAKE_KEY = 6;
    private static final int SYSTEM_UID = 1000;

    private IBuildInfo mCtsBuild;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        assertThat(mCtsBuild).isNotNull();
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
        DeviceUtils.installStatsdTestApp(getDevice(), mCtsBuild);
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);

        // Upload config to collect DisplayWakeReported event.
        ConfigUtils.uploadConfigForPushedAtom(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                AtomsProto.Atom.DISPLAY_WAKE_REPORTED_FIELD_NUMBER);
    }

    @Override
    protected void tearDown() throws Exception {
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
        DeviceUtils.uninstallStatsdTestApp(getDevice());
        DeviceUtils.turnScreenOn(getDevice());
        super.tearDown();
    }

    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mCtsBuild = buildInfo;
    }

    public void testDisplayWakeReportedFromWakeKey() throws Exception {
        DeviceUtils.runDeviceTestsOnStatsdApp(getDevice(), ".DisplayWakeReportedTests",
                "testWakeWithWakeKey");

        assertWakeup(WAKE_REASON_WAKE_KEY, SYSTEM_UID);
    }

    public void testDisplayWakeReportedFromWakeLock() throws Exception {
        DeviceUtils.runDeviceTestsOnStatsdApp(getDevice(), ".DisplayWakeReportedTests",
                "testWakeWithWakeLock");

        assertWakeup(WAKE_REASON_APPLICATION, DeviceUtils.getStatsdTestAppUid(getDevice()));
    }

    public void testDisplayWakeReportedFromWakeUpApi() throws Exception {
        DeviceUtils.runDeviceTestsOnStatsdApp(getDevice(), ".DisplayWakeReportedTests",
                "testWakeWithWakeUpApi");

        assertWakeup(WAKE_REASON_UNKNOWN, DeviceUtils.getStatsdTestAppUid(getDevice()));
    }

    public void testDisplayWakeReportedFromTurnScreenOnActivity() throws Exception {
        DeviceUtils.runDeviceTestsOnStatsdApp(getDevice(), ".DisplayWakeReportedTests",
                "testWakeWithTurnScreenOnActivity");

        assertWakeup(WAKE_REASON_APPLICATION, SYSTEM_UID);
    }

    private void assertWakeup(int reason, int uid) throws Exception {
        // Assert one DisplayWakeReported event has been collected.
        List<StatsLog.EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());
        assertThat(data.size()).isEqualTo(1);
        AtomsProto.DisplayWakeReported displayWakeReported =
                data.get(0).getAtom().getDisplayWakeReported();
        assertThat(displayWakeReported.getWakeUpReason()).isEqualTo(reason);
        assertThat(displayWakeReported.getUid()).isEqualTo(uid);
    }
}
