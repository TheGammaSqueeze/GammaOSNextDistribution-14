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

package android.cts.statsdatom.binderstats;

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

import com.google.common.collect.Range;

import java.util.List;

public class LooperStatsTests extends DeviceTestCase implements IBuildReceiver {
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

    public void testLooperStats() throws Exception {
        try {
            DeviceUtils.unplugDevice(getDevice());
            setUpLooperStats();

            ConfigUtils.uploadConfigForPulledAtom(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                    AtomsProto.Atom.LOOPER_STATS_FIELD_NUMBER);

            DeviceUtils.runActivity(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                    "StatsdCtsForegroundActivity", "action", "action.show_notification", 3_000);

            AtomTestUtils.sendAppBreadcrumbReportedAtom(getDevice());
            RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_SHORT);

            List<AtomsProto.Atom> atomList = ReportUtils.getGaugeMetricAtoms(getDevice());

            boolean found = false;
            int uid = DeviceUtils.getStatsdTestAppUid(getDevice());
            for (AtomsProto.Atom atom : atomList) {
                AtomsProto.LooperStats stats = atom.getLooperStats();
                String notificationServiceFullName =
                        "com.android.server.notification.NotificationManagerService";
                boolean handlerMatches =
                        stats.getHandlerClassName().equals(
                                notificationServiceFullName + "$WorkerHandler");
                boolean messageMatches =
                        stats.getMessageName().equals(
                                notificationServiceFullName + "$EnqueueNotificationRunnable");
                if (atom.getLooperStats().getUid() == uid && handlerMatches && messageMatches) {
                    found = true;
                    assertThat(stats.getMessageCount()).isGreaterThan(0L);
                    assertThat(stats.getRecordedMessageCount()).isGreaterThan(0L);
                    assertThat(stats.getRecordedTotalLatencyMicros())
                            .isIn(Range.open(0L, 1000000L));
                    assertThat(stats.getRecordedTotalCpuMicros()).isIn(Range.open(0L, 1000000L));
                    assertThat(stats.getRecordedMaxLatencyMicros()).isIn(Range.open(0L, 1000000L));
                    assertThat(stats.getRecordedMaxCpuMicros()).isIn(Range.open(0L, 1000000L));
                    assertThat(stats.getRecordedDelayMessageCount()).isGreaterThan(0L);
                    assertThat(stats.getRecordedTotalDelayMillis())
                            .isIn(Range.closedOpen(0L, 5000L));
                    assertThat(stats.getRecordedMaxDelayMillis()).isIn(Range.closedOpen(0L, 5000L));
                }
            }
            assertWithMessage(String.format("Did not find a matching atom for uid %d", uid))
                    .that(found).isTrue();
        } finally {
            cleanUpLooperStats();
            DeviceUtils.plugInAc(getDevice());
        }
    }

    private void setUpLooperStats() throws Exception {
        getDevice().executeShellCommand("cmd looper_stats enable");
        getDevice().executeShellCommand("cmd looper_stats sampling_interval 1");
        getDevice().executeShellCommand("cmd looper_stats reset");
    }

    private void cleanUpLooperStats() throws Exception {
        getDevice().executeShellCommand("cmd looper_stats disable");
    }
}
