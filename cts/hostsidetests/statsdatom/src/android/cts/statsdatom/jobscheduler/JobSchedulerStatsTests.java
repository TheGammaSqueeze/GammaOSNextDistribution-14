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

package android.cts.statsdatom.jobscheduler;

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

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class JobSchedulerStatsTests extends DeviceTestCase implements IBuildReceiver {
    private static final Set<Integer> STATE_SCHEDULE = new HashSet<>(
            List.of(AtomsProto.ScheduledJobStateChanged.State.SCHEDULED_VALUE));
    private static final Set<Integer> STATE_START = new HashSet<>(
            List.of(AtomsProto.ScheduledJobStateChanged.State.STARTED_VALUE));
    private static final Set<Integer> STATE_FINISH = new HashSet<>(
            List.of(AtomsProto.ScheduledJobStateChanged.State.FINISHED_VALUE));
    private static final Set<Integer> STATE_CANCEL = new HashSet<>(
            List.of(AtomsProto.ScheduledJobStateChanged.State.CANCELLED_VALUE));

    private static final String JOB_NAME =
            "com.android.server.cts.device.statsdatom/.StatsdJobService";

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

    public void testScheduledJobState() throws Exception {
        final int atomTag = AtomsProto.Atom.SCHEDULED_JOB_STATE_CHANGED_FIELD_NUMBER;

        // Add state sets to the list in order.
        List<Set<Integer>> stateSet = List.of(STATE_SCHEDULE, STATE_START, STATE_FINISH);

        ConfigUtils.uploadConfigForPushedAtomWithUid(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                atomTag, /*useUidAttributionChain=*/true);
        DeviceUtils.allowImmediateSyncs(getDevice());
        DeviceUtils.runDeviceTestsOnStatsdApp(getDevice(), ".AtomTests", "testScheduledJob");

        // Sorted list of events in order in which they occurred.
        List<StatsLog.EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());

        AtomTestUtils.assertStatesOccurredInOrder(stateSet, data, 0,
                atom -> atom.getScheduledJobStateChanged().getState().getNumber());

        for (StatsLog.EventMetricData e : data) {
            assertThat(e.getAtom().getScheduledJobStateChanged().getJobName())
                    .isEqualTo(JOB_NAME);
        }
    }

    public void testScheduledJobState_CancelledJob() throws Exception {
        final int atomTag = AtomsProto.Atom.SCHEDULED_JOB_STATE_CHANGED_FIELD_NUMBER;

        // Add state sets to the list in order.
        List<Set<Integer>> stateSet = List.of(STATE_SCHEDULE, STATE_CANCEL);

        ConfigUtils.uploadConfigForPushedAtomWithUid(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                atomTag, /*useUidAttributionChain=*/true);
        DeviceUtils.allowImmediateSyncs(getDevice());
        DeviceUtils.runDeviceTestsOnStatsdApp(getDevice(),
                ".AtomTests", "testScheduledJob_CancelledJob");

        // Sorted list of events in order in which they occurred.
        List<StatsLog.EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());

        AtomTestUtils.assertStatesOccurredInOrder(stateSet, data, 0,
                atom -> atom.getScheduledJobStateChanged().getState().getNumber());

        for (StatsLog.EventMetricData e : data) {
            assertThat(e.getAtom().getScheduledJobStateChanged().getJobName())
                    .isEqualTo(JOB_NAME);
        }
    }

    public void testScheduledJobStatePriority() throws Exception {
        final int atomTag = AtomsProto.Atom.SCHEDULED_JOB_STATE_CHANGED_FIELD_NUMBER;

        // Add state sets to the list in order.
        List<Set<Integer>> stateSet = List.of(
                STATE_SCHEDULE, STATE_START, STATE_FINISH,
                STATE_SCHEDULE, STATE_START, STATE_FINISH,
                STATE_SCHEDULE, STATE_START, STATE_FINISH,
                STATE_SCHEDULE, STATE_START, STATE_FINISH
        );

        ConfigUtils.uploadConfigForPushedAtomWithUid(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                atomTag, /*useUidAttributionChain=*/true);
        DeviceUtils.allowImmediateSyncs(getDevice());
        DeviceUtils.runDeviceTestsOnStatsdApp(getDevice(), ".AtomTests",
                "testScheduledJobPriority");

        // Sorted list of events in order in which they occurred.
        List<StatsLog.EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());

        AtomTestUtils.assertStatesOccurred(stateSet, data,
                atom -> atom.getScheduledJobStateChanged().getState().getNumber());

        for (StatsLog.EventMetricData e : data) {
            assertThat(e.getAtom().getScheduledJobStateChanged().getJobName())
                    .isEqualTo(JOB_NAME);
            assertThat(e.getAtom().getScheduledJobStateChanged().getRequestedPriority())
                    .isEqualTo(e.getAtom().getScheduledJobStateChanged().getJobId());
        }
    }
}
