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

package android.cts.statsdatom.alarm;

import com.android.tradefed.util.RunUtil;
import static com.google.common.truth.Truth.assertThat;

import android.app.ProcessStateEnum;
import android.cts.statsdatom.lib.AtomTestUtils;
import android.cts.statsdatom.lib.ConfigUtils;
import android.cts.statsdatom.lib.DeviceUtils;
import android.cts.statsdatom.lib.ReportUtils;

import com.android.os.AtomsProto;
import com.android.os.StatsLog;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.testtype.DeviceTestCase;
import com.android.tradefed.testtype.IBuildReceiver;

import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AlarmStatsTests extends DeviceTestCase implements IBuildReceiver {
    private static final String FEATURE_AUTOMOTIVE = "android.hardware.type.automotive";
    private static final String ALARM_ATOM_TEST_APK = "CtsStatsdAlarmHelper.apk";
    private static final String ALARM_ATOM_TEST_APK_2 = "CtsStatsdAlarmHelper2.apk";
    private static final String ALARM_ATOM_TEST_PACKAGE =
            "com.android.server.cts.device.statsdalarmhelper";
    private static final String ALARM_ATOM_TEST_PACKAGE_2 =
            "com.android.server.cts.device.statsdalarmhelper2";
    private static final String DEVICE_TEST_CLASS = ALARM_ATOM_TEST_PACKAGE + ".AlarmAtomTests";

    private IBuildInfo mCtsBuild;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        assertThat(mCtsBuild).isNotNull();
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
        DeviceUtils.installTestApp(getDevice(), ALARM_ATOM_TEST_APK, ALARM_ATOM_TEST_PACKAGE,
                mCtsBuild);
        DeviceUtils.installTestApp(getDevice(), ALARM_ATOM_TEST_APK_2, ALARM_ATOM_TEST_PACKAGE_2,
                mCtsBuild);
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);

        enableCompatChange("ENABLE_USE_EXACT_ALARM", ALARM_ATOM_TEST_PACKAGE);
    }

    @Override
    protected void tearDown() throws Exception {
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());

        resetCompatChanges(ALARM_ATOM_TEST_PACKAGE);

        DeviceUtils.uninstallTestApp(getDevice(), ALARM_ATOM_TEST_PACKAGE);
        super.tearDown();
    }

    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mCtsBuild = buildInfo;
    }

    private void grantAppOpSEA(String packageName) throws DeviceNotAvailableException {
        StringBuilder command = new StringBuilder("appops set --uid ")
                .append(packageName).append(" SCHEDULE_EXACT_ALARM allow");
        getDevice().executeShellCommand(command.toString());
    }

    private void enableCompatChange(String changeId, String packageName)
            throws DeviceNotAvailableException {
        StringBuilder command = new StringBuilder("am compat enable ")
                .append(changeId).append(" ").append(packageName);
        getDevice().executeShellCommand(command.toString());
    }

    private void resetCompatChanges(String packageName)
            throws DeviceNotAvailableException {
        StringBuilder command = new StringBuilder("am compat reset-all ").append(packageName);
        getDevice().executeShellCommand(command.toString());
    }

    private int getUid(String packageName) throws DeviceNotAvailableException {
        final int user = getDevice().getCurrentUser();
        final String output = getDevice().executeShellCommand(
                "pm list packages -U --user " + user + " " + packageName);

        final Pattern pattern = Pattern.compile("uid\\:(\\d+)");
        final Matcher matcher = pattern.matcher(output);
        assertThat(matcher.find()).isTrue();
        return Integer.parseInt(matcher.group(1));
    }

    public void testWakeupAlarmOccurred() throws Exception {
        // For automotive, all wakeup alarm becomes normal alarm. So this
        // test does not work.
        if (DeviceUtils.hasFeature(getDevice(), FEATURE_AUTOMOTIVE)) return;

        final int atomTag = AtomsProto.Atom.WAKEUP_ALARM_OCCURRED_FIELD_NUMBER;

        ConfigUtils.uploadConfigForPushedAtomWithUid(getDevice(), ALARM_ATOM_TEST_PACKAGE,
                atomTag, /*useUidAttributionChain=*/true);
        DeviceUtils.runDeviceTests(getDevice(), ALARM_ATOM_TEST_PACKAGE, DEVICE_TEST_CLASS,
                "testWakeupAlarm");

        List<StatsLog.EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());
        assertThat(data.size()).isAtLeast(1);
        for (int i = 0; i < data.size(); i++) {
            AtomsProto.WakeupAlarmOccurred wao = data.get(i).getAtom().getWakeupAlarmOccurred();
            assertThat(wao.getTag()).isEqualTo("*walarm*:android.cts.statsdatom.testWakeupAlarm");
            assertThat(wao.getPackageName()).isEqualTo(ALARM_ATOM_TEST_PACKAGE);
        }
    }

    public void testAlarmScheduled() throws Exception {
        if (DeviceUtils.hasFeature(getDevice(), FEATURE_AUTOMOTIVE)) return;

        final int atomId = AtomsProto.Atom.ALARM_SCHEDULED_FIELD_NUMBER;

        ConfigUtils.uploadConfigForPushedAtom(getDevice(), ALARM_ATOM_TEST_PACKAGE, atomId);
        DeviceUtils.runDeviceTests(getDevice(), ALARM_ATOM_TEST_PACKAGE, DEVICE_TEST_CLASS,
                "testAlarmScheduled");

        List<StatsLog.EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());
        assertThat(data.size()).isAtLeast(2);
        final int uid = getUid(ALARM_ATOM_TEST_PACKAGE);

        int count = 0;
        Predicate<AtomsProto.AlarmScheduled> alarm1 =
                as -> (!as.getIsRtc() && !as.getIsExact() && as.getIsWakeup() && as.getIsRepeating()
                        && !as.getIsAlarmClock() && !as.getIsAllowWhileIdle());
        Predicate<AtomsProto.AlarmScheduled> alarm2 =
                as -> (as.getIsRtc() && !as.getIsExact() && !as.getIsWakeup()
                        && !as.getIsRepeating() && !as.getIsAlarmClock()
                        && !as.getIsAllowWhileIdle());
        for (int i = 0; i < data.size(); i++) {
            AtomsProto.AlarmScheduled as = data.get(i).getAtom().getAlarmScheduled();
            if (as.getCallingUid() != uid) {
                continue;
            }
            assertThat(alarm1.test(as) != alarm2.test(as)).isTrue();
            assertThat(as.getCallingProcessState()).isNoneOf(ProcessStateEnum.PROCESS_STATE_UNKNOWN,
                    ProcessStateEnum.PROCESS_STATE_UNKNOWN_TO_PROTO);
            assertThat(as.getExactAlarmAllowedReason()).isEqualTo(
                    AtomsProto.AlarmScheduled.ReasonCode.NOT_APPLICABLE);

            count++;
        }
        assertThat(count).isEqualTo(2);
    }

    public void testAlarmScheduled_exactWithUEA() throws Exception {
        if (DeviceUtils.hasFeature(getDevice(), FEATURE_AUTOMOTIVE)) return;

        final int atomId = AtomsProto.Atom.ALARM_SCHEDULED_FIELD_NUMBER;

        ConfigUtils.uploadConfigForPushedAtom(getDevice(), ALARM_ATOM_TEST_PACKAGE, atomId);
        DeviceUtils.runDeviceTests(getDevice(), ALARM_ATOM_TEST_PACKAGE, DEVICE_TEST_CLASS,
                "testExactAlarmScheduled");

        List<StatsLog.EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());
        assertThat(data.size()).isAtLeast(1);
        final int uid = getUid(ALARM_ATOM_TEST_PACKAGE);

        int count = 0;

        for (int i = 0; i < data.size(); i++) {
            AtomsProto.AlarmScheduled as = data.get(i).getAtom().getAlarmScheduled();
            if (as.getCallingUid() != uid) {
                continue;
            }
            assertThat(as.getIsExact()).isTrue();
            assertThat(as.getExactAlarmAllowedReason()).isEqualTo(
                    AtomsProto.AlarmScheduled.ReasonCode.POLICY_PERMISSION);
            count++;
        }
        assertThat(count).isEqualTo(1);
    }

    public void testAlarmScheduled_exactWithSEA() throws Exception {
        if (DeviceUtils.hasFeature(getDevice(), FEATURE_AUTOMOTIVE)) return;

        grantAppOpSEA(ALARM_ATOM_TEST_PACKAGE_2);

        final int atomId = AtomsProto.Atom.ALARM_SCHEDULED_FIELD_NUMBER;

        ConfigUtils.uploadConfigForPushedAtom(getDevice(), ALARM_ATOM_TEST_PACKAGE_2, atomId);
        DeviceUtils.runDeviceTests(getDevice(), ALARM_ATOM_TEST_PACKAGE_2, DEVICE_TEST_CLASS,
                "testExactAlarmScheduled");

        List<StatsLog.EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());
        assertThat(data.size()).isAtLeast(1);
        final int uid = getUid(ALARM_ATOM_TEST_PACKAGE_2);

        int count = 0;

        for (int i = 0; i < data.size(); i++) {
            AtomsProto.AlarmScheduled as = data.get(i).getAtom().getAlarmScheduled();
            if (as.getCallingUid() != uid) {
                continue;
            }
            assertThat(as.getIsExact()).isTrue();
            assertThat(as.getExactAlarmAllowedReason()).isEqualTo(
                    AtomsProto.AlarmScheduled.ReasonCode.PERMISSION);
            count++;
        }
        assertThat(count).isEqualTo(1);
    }

    public void testAlarmBatchDelivered() throws Exception {
        if (DeviceUtils.hasFeature(getDevice(), FEATURE_AUTOMOTIVE)) return;

        final int atomId = AtomsProto.Atom.ALARM_BATCH_DELIVERED_FIELD_NUMBER;

        ConfigUtils.uploadConfigForPushedAtom(getDevice(), ALARM_ATOM_TEST_PACKAGE, atomId);
        DeviceUtils.runDeviceTests(getDevice(), ALARM_ATOM_TEST_PACKAGE, DEVICE_TEST_CLASS,
                "testWakeupAlarm");

        List<StatsLog.EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());
        assertThat(data.size()).isAtLeast(1);
        boolean found = false;
        final int expectedUid = getUid(ALARM_ATOM_TEST_PACKAGE);
        for (int i = 0; i < data.size(); i++) {
            AtomsProto.AlarmBatchDelivered abd = data.get(i).getAtom().getAlarmBatchDelivered();
            int expectedNumAlarms = 0;
            int expectedWakeups = 0;
            for (int j = 0; j < abd.getUidsCount(); j++) {
                expectedNumAlarms += abd.getNumAlarmsPerUid(j);
                expectedWakeups += abd.getNumWakeupsPerUid(j);
                if (abd.getUids(j) == expectedUid) {
                    assertThat(abd.getNumAlarmsPerUid(j)).isEqualTo(1);
                    assertThat(abd.getNumWakeupsPerUid(j)).isEqualTo(1);
                    found = true;
                }
            }
            assertThat(abd.getNumAlarms()).isEqualTo(expectedNumAlarms);
            assertThat(abd.getWakeups()).isEqualTo(expectedWakeups);
        }
        assertThat(found).isTrue();
    }

    public void testPendingAlarmInfo() throws Exception {
        if (DeviceUtils.hasFeature(getDevice(), FEATURE_AUTOMOTIVE)) return;

        ConfigUtils.uploadConfigForPulledAtom(getDevice(), ALARM_ATOM_TEST_PACKAGE,
                AtomsProto.Atom.PENDING_ALARM_INFO_FIELD_NUMBER);

        // Schedule some alarms
        DeviceUtils.runDeviceTests(getDevice(), ALARM_ATOM_TEST_PACKAGE, DEVICE_TEST_CLASS,
                "testPendingAlarmInfo");
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_SHORT);
        // Trigger atom pull
        AtomTestUtils.sendAppBreadcrumbReportedAtom(getDevice());
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);

        // The list of atoms will be empty if the atom is not supported.
        List<AtomsProto.Atom> atoms = ReportUtils.getGaugeMetricAtoms(getDevice());

        for (AtomsProto.Atom atom : atoms) {
            assertThat(atom.getPendingAlarmInfo().getNumTotal()).isAtLeast(5);
            assertThat(atom.getPendingAlarmInfo().getNumExact()).isAtLeast(2);
            assertThat(atom.getPendingAlarmInfo().getNumWakeup()).isAtLeast(3);
            assertThat(atom.getPendingAlarmInfo().getNumAllowWhileIdle()).isAtLeast(1);
            assertThat(atom.getPendingAlarmInfo().getNumForegroundService()).isAtLeast(2);
            assertThat(atom.getPendingAlarmInfo().getNumActivity()).isAtLeast(1);
            assertThat(atom.getPendingAlarmInfo().getNumService()).isAtLeast(1);
            assertThat(atom.getPendingAlarmInfo().getNumListener()).isAtLeast(1);
            assertThat(atom.getPendingAlarmInfo().getNumIndefiniteFuture()).isAtLeast(1);
            assertThat(atom.getPendingAlarmInfo().getNumRepeating()).isAtLeast(1);
            assertThat(atom.getPendingAlarmInfo().getNumAlarmClock()).isAtLeast(1);
            assertThat(atom.getPendingAlarmInfo().getNumRtc()).isAtLeast(2);
        }
    }
}
