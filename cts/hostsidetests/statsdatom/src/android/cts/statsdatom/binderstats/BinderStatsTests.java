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

import com.android.internal.os.StatsdConfigProto.StatsdConfig;
import com.android.os.AtomsProto.Atom;
import com.android.os.AtomsProto.BinderCalls;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.testtype.DeviceTestCase;
import com.android.tradefed.testtype.IBuildReceiver;

import com.google.common.collect.Range;

import java.util.List;

public final class BinderStatsTests extends DeviceTestCase implements IBuildReceiver {
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

    public void testBinderStats() throws Exception {
        try {
            DeviceUtils.unplugDevice(getDevice());
            RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_SHORT);

            enableBinderStats();
            binderStatsNoSampling();
            resetBinderStats();

            StatsdConfig.Builder config =
                    ConfigUtils.createConfigBuilder(DeviceUtils.STATSD_ATOM_TEST_PKG);
            ConfigUtils.addGaugeMetricForUidAtom(config, Atom.BINDER_CALLS_FIELD_NUMBER,
                    /*uidInAttributionChain=*/false, DeviceUtils.STATSD_ATOM_TEST_PKG);
            ConfigUtils.uploadConfig(getDevice(), config);

            DeviceUtils.runActivity(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                    "StatsdCtsForegroundActivity", "action", "action.show_notification", 3_000);
            AtomTestUtils.sendAppBreadcrumbReportedAtom(getDevice());
            RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_SHORT);

            boolean found = false;
            int appUid = DeviceUtils.getAppUid(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG);
            for (Atom atom : ReportUtils.getGaugeMetricAtoms(getDevice())) {
                BinderCalls calls = atom.getBinderCalls();
                assertThat(calls.getUid()).isEqualTo(appUid);
                boolean classMatches = calls.getServiceClassName().contains(
                        "com.android.server.notification.NotificationManagerService");
                boolean methodMatches = calls.getServiceMethodName()
                        .equals("createNotificationChannels");
                if (classMatches && methodMatches) {
                    found = true;
                    assertThat(calls.getRecordedCallCount()).isGreaterThan(0L);
                    assertThat(calls.getCallCount()).isGreaterThan(0L);
                    assertThat(calls.getRecordedTotalLatencyMicros())
                        .isIn(Range.open(0L, 1000000L));
                    assertThat(calls.getRecordedTotalCpuMicros()).isIn(Range.open(0L, 1000000L));
                }
            }

            assertWithMessage(String.format("Did not find a matching atom for uid %d", appUid))
                .that(found).isTrue();
          } finally {
            disableBinderStats();
            plugInAc();
          }
    }

    private void enableBinderStats() throws Exception {
        getDevice().executeShellCommand("dumpsys binder_calls_stats --enable");
    }

    private void resetBinderStats() throws Exception {
        getDevice().executeShellCommand("dumpsys binder_calls_stats --reset");
    }

    private void disableBinderStats() throws Exception {
        getDevice().executeShellCommand("dumpsys binder_calls_stats --disable");
    }

    private void binderStatsNoSampling() throws Exception {
        getDevice().executeShellCommand("dumpsys binder_calls_stats --no-sampling");
    }

    private void plugInAc() throws Exception {
        getDevice().executeShellCommand("cmd battery set ac 1");
    }
}
