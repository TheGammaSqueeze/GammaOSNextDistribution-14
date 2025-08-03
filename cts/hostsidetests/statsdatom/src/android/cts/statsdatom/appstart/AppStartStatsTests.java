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

package android.cts.statsdatom.appstart;

import com.android.tradefed.util.RunUtil;
import static com.google.common.truth.Truth.assertThat;

import android.cts.statsdatom.lib.AtomTestUtils;
import android.cts.statsdatom.lib.ConfigUtils;
import android.cts.statsdatom.lib.DeviceUtils;
import android.cts.statsdatom.lib.ReportUtils;

import com.android.annotations.Nullable;
import com.android.internal.os.StatsdConfigProto.StatsdConfig;
import com.android.os.AtomsProto;
import com.android.os.StatsLog;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.testtype.DeviceTestCase;
import com.android.tradefed.testtype.IBuildReceiver;

import java.util.List;

public class AppStartStatsTests extends DeviceTestCase implements IBuildReceiver {
    public static final String CMD_APP_HIBERNATION_SET_STATE_GLOBAL =
            "cmd app_hibernation set-state --global ";
    public static final String STATSD_CTS_FOREGROUND_ACTIVITY = "StatsdCtsForegroundActivity";
    public static final int WAIT_TIME_MS = 3_500;
    public static final String COMMAND_ENABLE_APP_HIBERNATION =
            "device_config put app_hibernation app_hibernation_enabled true";
    private static final String CMD_GET_STAY_ON = "settings get global stay_on_while_plugged_in";
    private static final String CMD_PUT_STAY_ON_TEMPLATE =
            "settings put global stay_on_while_plugged_in %d";
    private static final String CMD_ENABLE_STAY_ON =
            "settings put global stay_on_while_plugged_in 7";
    private IBuildInfo mCtsBuild;
    private long mOriginalStayOnSetting;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        assertThat(mCtsBuild).isNotNull();
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
        DeviceUtils.installStatsdTestApp(getDevice(), mCtsBuild);
        mOriginalStayOnSetting = Long.parseLong(
                getDevice().executeShellCommand(CMD_GET_STAY_ON).trim());
        getDevice().executeShellCommand(CMD_ENABLE_STAY_ON);
        DeviceUtils.turnScreenOn(getDevice());
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);
    }

    @Override
    protected void tearDown() throws Exception {
        getDevice().executeShellCommand(
                String.format(CMD_PUT_STAY_ON_TEMPLATE, mOriginalStayOnSetting));
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
        DeviceUtils.uninstallStatsdTestApp(getDevice());
        super.tearDown();
    }

    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mCtsBuild = buildInfo;
    }

    public void testAppStartOccurred() throws Exception {
        runTestAppStartOccurredCommon(null, atom -> {
            assertThat(atom.getType()).isEqualTo(
                    AtomsProto.AppStartOccurred.TransitionType.COLD);
            assertThat(atom.getProcessState()).isEqualTo(
                    AtomsProto.AppStartOccurred.AppProcessState.PROCESS_STATE_NONEXISTENT);
        });
    }

    public void testAppStartOccurredWarm() throws Exception {
        runTestAppStartOccurredCommon(() -> {
            DeviceUtils.executeBackgroundService(getDevice(), "action.end_immediately");
            RunUtil.getDefault().sleep(WAIT_TIME_MS);
        }, atom -> {
            assertThat(atom.getType()).isEqualTo(
                    AtomsProto.AppStartOccurred.TransitionType.WARM);
            assertThat(atom.getProcessState()).isEqualTo(
                    AtomsProto.AppStartOccurred.AppProcessState.PROCESS_STATE_CACHED_EMPTY);
        });
    }

    private interface RunnableWithException {
        void run() throws Exception;
    }

    private interface ConsumerWithException<T> {
        void accept(T t) throws Exception;
    }

    private void runTestAppStartOccurredCommon(@Nullable RunnableWithException prolog,
            @Nullable ConsumerWithException<AtomsProto.AppStartOccurred> epilog) throws Exception {
        StatsdConfig.Builder config =
                ConfigUtils.createConfigBuilder(DeviceUtils.STATSD_ATOM_TEST_PKG);
        ConfigUtils.addEventMetricForUidAtom(
                config,
                AtomsProto.Atom.APP_START_OCCURRED_FIELD_NUMBER,
                /*uidInAttributionChain=*/false,
                DeviceUtils.STATSD_ATOM_TEST_PKG);
        ConfigUtils.addEventMetricForUidAtom(
                config,
                AtomsProto.Atom.APP_START_FULLY_DRAWN_FIELD_NUMBER,
                /*uidInAttributionChain=*/false,
                DeviceUtils.STATSD_ATOM_TEST_PKG);
        ConfigUtils.uploadConfig(getDevice(), config);

        if (prolog != null) {
            prolog.run();
        }

        DeviceUtils.runActivity(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                STATSD_CTS_FOREGROUND_ACTIVITY, "action", "action.sleep_top", WAIT_TIME_MS);

        // Sorted list of events in order in which they occurred.
        List<StatsLog.EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());

        assertThat(data).hasSize(2);
        AtomsProto.AppStartOccurred atom = data.get(0).getAtom().getAppStartOccurred();
        assertThat(atom.getPkgName()).isEqualTo(DeviceUtils.STATSD_ATOM_TEST_PKG);
        assertThat(atom.getActivityName())
                .isEqualTo("com.android.server.cts.device.statsdatom.StatsdCtsForegroundActivity");
        assertThat(atom.getIsInstantApp()).isFalse();
        assertThat(atom.getActivityStartTimestampMillis()).isGreaterThan(0L);
        assertThat(atom.getTransitionDelayMillis()).isGreaterThan(0);
        assertThat(atom.getIsHibernating()).isFalse();

        AtomsProto.AppStartFullyDrawn fullyDrawnAtom =
                data.get(1).getAtom().getAppStartFullyDrawn();
        assertThat(fullyDrawnAtom.getPkgName()).isEqualTo(DeviceUtils.STATSD_ATOM_TEST_PKG);
        assertThat(fullyDrawnAtom.getActivityName())
                .isEqualTo("com.android.server.cts.device.statsdatom.StatsdCtsForegroundActivity");
        // Both events should have the same trigger timestamp so they can be joined.
        assertThat(fullyDrawnAtom.getActivityStartTimestampMillis())
                .isEqualTo(atom.getActivityStartTimestampMillis());

        if (epilog != null) {
            epilog.accept(atom);
        }
    }

    public void testHibernatingAppStartOccurred() throws Exception {
        final int atomTag = AtomsProto.Atom.APP_START_OCCURRED_FIELD_NUMBER;
        ConfigUtils.uploadConfigForPushedAtomWithUid(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                atomTag,  /*uidInAttributionChain=*/false);
        getDevice().executeShellCommand(COMMAND_ENABLE_APP_HIBERNATION);
        getDevice().executeShellCommand(getGlobalHibernationCommand(
                DeviceUtils.STATSD_ATOM_TEST_PKG, true));

        DeviceUtils.runActivity(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                STATSD_CTS_FOREGROUND_ACTIVITY, "action", "action.sleep_top", WAIT_TIME_MS);

        // Sorted list of events in order in which they occurred.
        List<StatsLog.EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());
        assertThat(data).hasSize(1);
        AtomsProto.AppStartOccurred atom = data.get(0).getAtom().getAppStartOccurred();
        assertThat(atom.getIsHibernating()).isTrue();
    }

    public void testHibernatingAppStartOccurredTwice_isHibernatingShouldBeFalseSecondTime()
            throws Exception {
        final int atomTag = AtomsProto.Atom.APP_START_OCCURRED_FIELD_NUMBER;
        ConfigUtils.uploadConfigForPushedAtomWithUid(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                atomTag,  /*uidInAttributionChain=*/false);
        getDevice().executeShellCommand(COMMAND_ENABLE_APP_HIBERNATION);
        getDevice().executeShellCommand(getGlobalHibernationCommand(
                DeviceUtils.STATSD_ATOM_TEST_PKG, true));

        DeviceUtils.runActivity(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                STATSD_CTS_FOREGROUND_ACTIVITY, "action", "action.sleep_top", WAIT_TIME_MS);
        DeviceUtils.runActivity(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                STATSD_CTS_FOREGROUND_ACTIVITY, "action", "action.sleep_top", WAIT_TIME_MS);

        // Sorted list of events in order in which they occurred.
        List<StatsLog.EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());

        assertThat(data).hasSize(2);
        AtomsProto.AppStartOccurred atom = data.get(1).getAtom().getAppStartOccurred();
        assertThat(atom.getIsHibernating()).isFalse();
    }

    private static String getGlobalHibernationCommand(String packageName, boolean isHibernating) {
        return CMD_APP_HIBERNATION_SET_STATE_GLOBAL + packageName + " " + isHibernating;
    }
}
