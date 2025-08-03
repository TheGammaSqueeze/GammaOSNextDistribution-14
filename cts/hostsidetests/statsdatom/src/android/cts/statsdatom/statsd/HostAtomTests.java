/*
 * Copyright (C) 2017 The Android Open Source Project
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
package android.cts.statsdatom.statsd;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.cts.statsdatom.lib.AtomTestUtils;
import android.cts.statsdatom.lib.ConfigUtils;
import android.cts.statsdatom.lib.DeviceUtils;
import android.cts.statsdatom.lib.ReportUtils;
import android.os.BatteryPluggedStateEnum;
import android.os.BatteryStatusEnum;
import android.os.StatsDataDumpProto;
import android.platform.test.annotations.RestrictedBuildTest;
import android.server.DeviceIdleModeEnum;
import android.view.DisplayStateEnum;

import com.android.os.AtomsProto.AppBreadcrumbReported;
import com.android.os.AtomsProto.Atom;
import com.android.os.AtomsProto.BatterySaverModeStateChanged;
import com.android.os.AtomsProto.BuildInformation;
import com.android.os.AtomsProto.ConnectivityStateChanged;
import com.android.os.StatsLog.ConfigMetricsReportList;
import com.android.os.StatsLog.EventMetricData;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.log.LogUtil;
import com.android.tradefed.testtype.DeviceTestCase;
import com.android.tradefed.testtype.IBuildReceiver;
import com.android.tradefed.util.RunUtil;

import com.google.common.collect.Range;
import com.google.protobuf.ByteString;
import com.google.protobuf.ExtensionRegistry;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Statsd atom tests that are done via adb (hostside).
 */
public class HostAtomTests extends DeviceTestCase implements IBuildReceiver {

    private static final String TAG = "Statsd.HostAtomTests";

    private static final String DUMPSYS_STATS_CMD = "dumpsys stats";

    // Either file must exist to read kernel wake lock stats.
    private static final String WAKE_LOCK_FILE = "/proc/wakelocks";
    private static final String WAKE_SOURCES_FILE = "/d/wakeup_sources";

    private static final String FEATURE_AUTOMOTIVE = "android.hardware.type.automotive";
    private static final String FEATURE_WATCH = "android.hardware.type.watch";
    private static final String FEATURE_TWM = "com.google.clockwork.hardware.traditional_watch_mode";
    private static final String FEATURE_WIFI = "android.hardware.wifi";
    private static final String FEATURE_LEANBACK_ONLY = "android.software.leanback_only";

    private IBuildInfo mCtsBuild;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        assertThat(mCtsBuild).isNotNull();
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
        DeviceUtils.installStatsdTestApp(getDevice(), mCtsBuild);
        DeviceUtils.turnBatteryStatsAutoResetOff(getDevice());
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);
    }

    @Override
    protected void tearDown() throws Exception {
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
        DeviceUtils.uninstallStatsdTestApp(getDevice());
        DeviceUtils.turnBatteryStatsAutoResetOn(getDevice());
        super.tearDown();
    }

    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mCtsBuild = buildInfo;
    }

    public void testScreenStateChangedAtom() throws Exception {
        // Setup, make sure the screen is off and turn off AoD if it is on.
        // AoD needs to be turned off because the screen should go into an off state. But, if AoD is
        // on and the device doesn't support STATE_DOZE, the screen sadly goes back to STATE_ON.
        String aodState = getAodState();
        setAodState("0");
        DeviceUtils.turnScreenOn(getDevice());
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_SHORT);
        DeviceUtils.turnScreenOff(getDevice());
        // Ensure that the screen on/off atoms are pushed before the config is uploaded.
        RunUtil.getDefault().sleep(5_000);

        final int atomTag = Atom.SCREEN_STATE_CHANGED_FIELD_NUMBER;

        Set<Integer> screenOnStates = new HashSet<>(
                Arrays.asList(DisplayStateEnum.DISPLAY_STATE_ON_VALUE,
                        DisplayStateEnum.DISPLAY_STATE_ON_SUSPEND_VALUE,
                        DisplayStateEnum.DISPLAY_STATE_VR_VALUE));
        Set<Integer> screenOffStates = new HashSet<>(
                Arrays.asList(DisplayStateEnum.DISPLAY_STATE_OFF_VALUE,
                        DisplayStateEnum.DISPLAY_STATE_DOZE_VALUE,
                        DisplayStateEnum.DISPLAY_STATE_DOZE_SUSPEND_VALUE,
                        DisplayStateEnum.DISPLAY_STATE_UNKNOWN_VALUE));

        // Add state sets to the list in order.
        List<Set<Integer>> stateSet = Arrays.asList(screenOnStates, screenOffStates);

        ConfigUtils.uploadConfigForPushedAtom(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                atomTag);

        // Trigger events in same order.
        DeviceUtils.turnScreenOn(getDevice());
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);
        DeviceUtils.turnScreenOff(getDevice());
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);

        // Sorted list of events in order in which they occurred.
        List<EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());
        // reset screen to on
        DeviceUtils.turnScreenOn(getDevice());
        // Restores AoD to initial state.
        setAodState(aodState);
        // Assert that the events happened in the expected order.
        AtomTestUtils.assertStatesOccurredInOrder(stateSet, data, AtomTestUtils.WAIT_TIME_LONG,
                atom -> atom.getScreenStateChanged().getState().getNumber());
    }

    public void testChargingStateChangedAtom() throws Exception {
        if (DeviceUtils.hasFeature(getDevice(), FEATURE_AUTOMOTIVE)) return;
        // Setup, set charging state to full.
        DeviceUtils.setChargingState(getDevice(), 5);
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_SHORT);

        final int atomTag = Atom.CHARGING_STATE_CHANGED_FIELD_NUMBER;

        Set<Integer> batteryUnknownStates = new HashSet<>(
                Arrays.asList(BatteryStatusEnum.BATTERY_STATUS_UNKNOWN_VALUE));
        Set<Integer> batteryChargingStates = new HashSet<>(
                Arrays.asList(BatteryStatusEnum.BATTERY_STATUS_CHARGING_VALUE));
        Set<Integer> batteryDischargingStates = new HashSet<>(
                Arrays.asList(BatteryStatusEnum.BATTERY_STATUS_DISCHARGING_VALUE));
        Set<Integer> batteryNotChargingStates = new HashSet<>(
                Arrays.asList(BatteryStatusEnum.BATTERY_STATUS_NOT_CHARGING_VALUE));
        Set<Integer> batteryFullStates = new HashSet<>(
                Arrays.asList(BatteryStatusEnum.BATTERY_STATUS_FULL_VALUE));

        // Add state sets to the list in order.
        List<Set<Integer>> stateSet = Arrays.asList(batteryUnknownStates, batteryChargingStates,
                batteryDischargingStates, batteryNotChargingStates, batteryFullStates);

        ConfigUtils.uploadConfigForPushedAtom(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                atomTag);

        // Trigger events in same order.
        DeviceUtils.setChargingState(getDevice(), 1);
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_SHORT);
        DeviceUtils.setChargingState(getDevice(), 2);
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_SHORT);
        DeviceUtils.setChargingState(getDevice(), 3);
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_SHORT);
        DeviceUtils.setChargingState(getDevice(), 4);
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_SHORT);
        DeviceUtils.setChargingState(getDevice(), 5);
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_SHORT);

        // Sorted list of events in order in which they occurred.
        List<EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());

        // Unfreeze battery state after test
        DeviceUtils.resetBatteryStatus(getDevice());
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_SHORT);

        // Assert that the events happened in the expected order.
        AtomTestUtils.assertStatesOccurredInOrder(stateSet, data, AtomTestUtils.WAIT_TIME_SHORT,
                atom -> atom.getChargingStateChanged().getState().getNumber());
    }

    public void testPluggedStateChangedAtom() throws Exception {
        if (DeviceUtils.hasFeature(getDevice(), FEATURE_AUTOMOTIVE)) return;
        // Setup, unplug device.
        DeviceUtils.unplugDevice(getDevice());
        DeviceUtils.flushBatteryStatsHandlers(getDevice());
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_SHORT);

        final int atomTag = Atom.PLUGGED_STATE_CHANGED_FIELD_NUMBER;

        Set<Integer> unpluggedStates = new HashSet<>(
                Arrays.asList(BatteryPluggedStateEnum.BATTERY_PLUGGED_NONE_VALUE));
        Set<Integer> acStates = new HashSet<>(
                Arrays.asList(BatteryPluggedStateEnum.BATTERY_PLUGGED_AC_VALUE));
        Set<Integer> usbStates = new HashSet<>(
                Arrays.asList(BatteryPluggedStateEnum.BATTERY_PLUGGED_USB_VALUE));
        Set<Integer> wirelessStates = new HashSet<>(
                Arrays.asList(BatteryPluggedStateEnum.BATTERY_PLUGGED_WIRELESS_VALUE));

        // Add state sets to the list in order.
        List<Set<Integer>> stateSet = Arrays.asList(acStates, unpluggedStates, usbStates,
                unpluggedStates, wirelessStates, unpluggedStates);

        ConfigUtils.uploadConfigForPushedAtom(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                atomTag);

        // Trigger events in same order.
        DeviceUtils.plugInAc(getDevice());
        DeviceUtils.flushBatteryStatsHandlers(getDevice());
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);
        DeviceUtils.unplugDevice(getDevice());
        DeviceUtils.flushBatteryStatsHandlers(getDevice());
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);
        plugInUsb();
        DeviceUtils.flushBatteryStatsHandlers(getDevice());
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);
        DeviceUtils.unplugDevice(getDevice());
        DeviceUtils.flushBatteryStatsHandlers(getDevice());
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);
        plugInWireless();
        DeviceUtils.flushBatteryStatsHandlers(getDevice());
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);
        DeviceUtils.unplugDevice(getDevice());
        DeviceUtils.flushBatteryStatsHandlers(getDevice());

        // Sorted list of events in order in which they occurred.
        List<EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());

        // Unfreeze battery state after test
        DeviceUtils.resetBatteryStatus(getDevice());
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_SHORT);

        // Assert that the events happened in the expected order.
        AtomTestUtils.assertStatesOccurredInOrder(stateSet, data, AtomTestUtils.WAIT_TIME_LONG,
                atom -> atom.getPluggedStateChanged().getState().getNumber());
    }

    public void testBatteryLevelChangedAtom() throws Exception {
        if (DeviceUtils.hasFeature(getDevice(), FEATURE_AUTOMOTIVE)) return;
        // Setup, set battery level to full.
        setBatteryLevel(100);
        DeviceUtils.flushBatteryStatsHandlers(getDevice());
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_SHORT);

        final int atomTag = Atom.BATTERY_LEVEL_CHANGED_FIELD_NUMBER;

        Set<Integer> batteryLow = new HashSet<>(Arrays.asList(2));
        Set<Integer> battery25p = new HashSet<>(Arrays.asList(25));
        Set<Integer> battery50p = new HashSet<>(Arrays.asList(50));
        Set<Integer> battery75p = new HashSet<>(Arrays.asList(75));
        Set<Integer> batteryFull = new HashSet<>(Arrays.asList(100));

        // Add state sets to the list in order.
        List<Set<Integer>> stateSet = Arrays.asList(batteryLow, battery25p, battery50p,
                battery75p, batteryFull);

        ConfigUtils.uploadConfigForPushedAtom(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                atomTag);

        // Trigger events in same order.
        setBatteryLevel(2);
        DeviceUtils.flushBatteryStatsHandlers(getDevice());
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);
        setBatteryLevel(25);
        DeviceUtils.flushBatteryStatsHandlers(getDevice());
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);
        setBatteryLevel(50);
        DeviceUtils.flushBatteryStatsHandlers(getDevice());
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);
        setBatteryLevel(75);
        DeviceUtils.flushBatteryStatsHandlers(getDevice());
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);
        setBatteryLevel(100);
        DeviceUtils.flushBatteryStatsHandlers(getDevice());
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);

        // Sorted list of events in order in which they occurred.
        List<EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());

        // Unfreeze battery state after test
        DeviceUtils.resetBatteryStatus(getDevice());
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_SHORT);

        // Assert that the events happened in the expected order.
        AtomTestUtils.assertStatesOccurredInOrder(stateSet, data, AtomTestUtils.WAIT_TIME_LONG,
                atom -> atom.getBatteryLevelChanged().getBatteryLevel());
    }

    public void testDeviceIdleModeStateChangedAtom() throws Exception {
        // Setup, leave doze mode.
        leaveDozeMode();
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_SHORT);

        final int atomTag = Atom.DEVICE_IDLE_MODE_STATE_CHANGED_FIELD_NUMBER;

        Set<Integer> dozeOff = new HashSet<>(
                Arrays.asList(DeviceIdleModeEnum.DEVICE_IDLE_MODE_OFF_VALUE));
        Set<Integer> dozeLight = new HashSet<>(
                Arrays.asList(DeviceIdleModeEnum.DEVICE_IDLE_MODE_LIGHT_VALUE));
        Set<Integer> dozeDeep = new HashSet<>(
                Arrays.asList(DeviceIdleModeEnum.DEVICE_IDLE_MODE_DEEP_VALUE));

        // Add state sets to the list in order.
        List<Set<Integer>> stateSet = Arrays.asList(dozeLight, dozeDeep, dozeOff);

        ConfigUtils.uploadConfigForPushedAtom(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                atomTag);

        // Trigger events in same order.
        enterDozeModeLight();
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_SHORT);
        enterDozeModeDeep();
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_SHORT);
        leaveDozeMode();
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_SHORT);

        // Sorted list of events in order in which they occurred.
        List<EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());

        // Assert that the events happened in the expected order.
        AtomTestUtils.assertStatesOccurredInOrder(stateSet, data, AtomTestUtils.WAIT_TIME_SHORT,
                atom -> atom.getDeviceIdleModeStateChanged().getState().getNumber());
    }

    public void testBatterySaverModeStateChangedAtom() throws Exception {
        if (DeviceUtils.hasFeature(getDevice(), FEATURE_TWM)) return;
        if (DeviceUtils.hasFeature(getDevice(), FEATURE_AUTOMOTIVE)) return;
        if (DeviceUtils.hasFeature(getDevice(), FEATURE_WATCH)) return;
        // Setup, turn off battery saver.
        turnBatterySaverOff();
        DeviceUtils.flushBatteryStatsHandlers(getDevice());

        final int atomTag = Atom.BATTERY_SAVER_MODE_STATE_CHANGED_FIELD_NUMBER;

        Set<Integer> batterySaverOn = new HashSet<>(
                Arrays.asList(BatterySaverModeStateChanged.State.ON_VALUE));
        Set<Integer> batterySaverOff = new HashSet<>(
                Arrays.asList(BatterySaverModeStateChanged.State.OFF_VALUE));

        // Add state sets to the list in order.
        List<Set<Integer>> stateSet = Arrays.asList(batterySaverOn, batterySaverOff);

        ConfigUtils.uploadConfigForPushedAtom(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                atomTag);

        // Trigger events in same order.
        turnBatterySaverOn();
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);
        turnBatterySaverOff();
        DeviceUtils.flushBatteryStatsHandlers(getDevice());

        // Sorted list of events in order in which they occurred.
        List<EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());

        // Assert that the events happened in the expected order.
        AtomTestUtils.assertStatesOccurredInOrder(stateSet, data, AtomTestUtils.WAIT_TIME_LONG,
                atom -> atom.getBatterySaverModeStateChanged().getState().getNumber());
    }

    @RestrictedBuildTest
    public void testRemainingBatteryCapacity() throws Exception {
        if (DeviceUtils.hasFeature(getDevice(), FEATURE_WATCH)) return;
        if (DeviceUtils.hasFeature(getDevice(), FEATURE_AUTOMOTIVE)) return;

        ConfigUtils.uploadConfigForPulledAtom(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                Atom.REMAINING_BATTERY_CAPACITY_FIELD_NUMBER);

        AtomTestUtils.sendAppBreadcrumbReportedAtom(getDevice());
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);

        List<Atom> data = ReportUtils.getGaugeMetricAtoms(getDevice());

        assertThat(data).isNotEmpty();
        Atom atom = data.get(0);
        assertThat(atom.getRemainingBatteryCapacity().hasChargeMicroAmpereHour()).isTrue();
        if (DeviceUtils.hasBattery(getDevice())) {
            assertThat(atom.getRemainingBatteryCapacity().getChargeMicroAmpereHour())
                    .isGreaterThan(0);
        }
    }

    @RestrictedBuildTest
    public void testFullBatteryCapacity() throws Exception {
        if (DeviceUtils.hasFeature(getDevice(), FEATURE_WATCH)) return;
        if (DeviceUtils.hasFeature(getDevice(), FEATURE_AUTOMOTIVE)) return;

        ConfigUtils.uploadConfigForPulledAtom(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                Atom.FULL_BATTERY_CAPACITY_FIELD_NUMBER);

        AtomTestUtils.sendAppBreadcrumbReportedAtom(getDevice());
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);

        List<Atom> data = ReportUtils.getGaugeMetricAtoms(getDevice());

        assertThat(data).isNotEmpty();
        Atom atom = data.get(0);
        assertThat(atom.getFullBatteryCapacity().hasCapacityMicroAmpereHour()).isTrue();
        if (DeviceUtils.hasBattery(getDevice())) {
            assertThat(atom.getFullBatteryCapacity().getCapacityMicroAmpereHour()).isGreaterThan(0);
        }
    }

    public void testBatteryVoltage() throws Exception {
        if (DeviceUtils.hasFeature(getDevice(), FEATURE_WATCH)) return;

        ConfigUtils.uploadConfigForPulledAtom(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                Atom.BATTERY_VOLTAGE_FIELD_NUMBER);

        AtomTestUtils.sendAppBreadcrumbReportedAtom(getDevice());
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);

        List<Atom> data = ReportUtils.getGaugeMetricAtoms(getDevice());

        assertThat(data).isNotEmpty();
        Atom atom = data.get(0);
        assertThat(atom.getBatteryVoltage().hasVoltageMillivolt()).isTrue();
        if (DeviceUtils.hasBattery(getDevice())) {
            assertThat(atom.getBatteryVoltage().getVoltageMillivolt()).isGreaterThan(0);
        }
    }

    // This test is for the pulled battery level atom.
    public void testBatteryLevel() throws Exception {
        if (DeviceUtils.hasFeature(getDevice(), FEATURE_WATCH)) return;

        ConfigUtils.uploadConfigForPulledAtom(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                Atom.BATTERY_LEVEL_FIELD_NUMBER);

        AtomTestUtils.sendAppBreadcrumbReportedAtom(getDevice());
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);

        List<Atom> data = ReportUtils.getGaugeMetricAtoms(getDevice());

        assertThat(data).isNotEmpty();
        Atom atom = data.get(0);
        assertThat(atom.getBatteryLevel().hasBatteryLevel()).isTrue();
        if (DeviceUtils.hasBattery(getDevice())) {
            assertThat(atom.getBatteryLevel().getBatteryLevel()).isIn(Range.openClosed(0, 100));
        }
    }

    public void testKernelWakelock() throws Exception {
        if (!kernelWakelockStatsExist()) {
            return;
        }

        ConfigUtils.uploadConfigForPulledAtom(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                Atom.KERNEL_WAKELOCK_FIELD_NUMBER);

        AtomTestUtils.sendAppBreadcrumbReportedAtom(getDevice());
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);

        List<Atom> data = ReportUtils.getGaugeMetricAtoms(getDevice());

        assertThat(data).isNotEmpty();
        for (Atom atom : data) {
            assertThat(atom.getKernelWakelock().hasName()).isTrue();
            assertThat(atom.getKernelWakelock().hasCount()).isTrue();
            assertThat(atom.getKernelWakelock().hasVersion()).isTrue();
            assertThat(atom.getKernelWakelock().getVersion()).isGreaterThan(0);
            assertThat(atom.getKernelWakelock().hasTimeMicros()).isTrue();
        }
    }

    // Returns true iff either |WAKE_LOCK_FILE| or |WAKE_SOURCES_FILE| exists.
    private boolean kernelWakelockStatsExist() {
      try {
        return doesFileExist(WAKE_LOCK_FILE) || doesFileExist(WAKE_SOURCES_FILE);
      } catch(Exception e) {
        return false;
      }
    }

    public void testWifiActivityInfo() throws Exception {
        if (!DeviceUtils.hasFeature(getDevice(), FEATURE_WIFI)) return;
        if (DeviceUtils.hasFeature(getDevice(), FEATURE_WATCH)) return;
        if (!DeviceUtils.checkDeviceFor(getDevice(), "checkWifiEnhancedPowerReportingSupported")) {
            return;
        }

        ConfigUtils.uploadConfigForPulledAtom(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                Atom.WIFI_ACTIVITY_INFO_FIELD_NUMBER);

        AtomTestUtils.sendAppBreadcrumbReportedAtom(getDevice());
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);

        List<Atom> dataList = ReportUtils.getGaugeMetricAtoms(getDevice());

        for (Atom atom : dataList) {
            assertThat(atom.getWifiActivityInfo().getTimestampMillis()).isGreaterThan(0L);
            assertThat(atom.getWifiActivityInfo().getStackState()).isAtLeast(0);
            assertThat(atom.getWifiActivityInfo().getControllerIdleTimeMillis()).isGreaterThan(0L);
            assertThat(atom.getWifiActivityInfo().getControllerTxTimeMillis()).isAtLeast(0L);
            assertThat(atom.getWifiActivityInfo().getControllerRxTimeMillis()).isAtLeast(0L);
            assertThat(atom.getWifiActivityInfo().getControllerEnergyUsed()).isAtLeast(0L);
        }
    }

    public void testBuildInformation() throws Exception {
        ConfigUtils.uploadConfigForPulledAtom(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                Atom.BUILD_INFORMATION_FIELD_NUMBER);

        AtomTestUtils.sendAppBreadcrumbReportedAtom(getDevice());
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);

        List<Atom> data = ReportUtils.getGaugeMetricAtoms(getDevice());

        assertThat(data).isNotEmpty();
        BuildInformation atom = data.get(0).getBuildInformation();
        assertThat(DeviceUtils.getProperty(getDevice(), "ro.product.brand")).isEqualTo(
                atom.getBrand());
        assertThat(DeviceUtils.getProperty(getDevice(), "ro.product.name")).isEqualTo(
                atom.getProduct());
        assertThat(DeviceUtils.getProperty(getDevice(), "ro.product.device")).isEqualTo(
                atom.getDevice());
        assertThat(DeviceUtils.getProperty(getDevice(),
                "ro.build.version.release_or_codename")).isEqualTo(
                atom.getVersionRelease());
        assertThat(DeviceUtils.getProperty(getDevice(), "ro.build.id")).isEqualTo(atom.getId());
        assertThat(DeviceUtils.getProperty(getDevice(), "ro.build.version.incremental"))
                .isEqualTo(atom.getVersionIncremental());
        assertThat(DeviceUtils.getProperty(getDevice(), "ro.build.type")).isEqualTo(atom.getType());
        assertThat(DeviceUtils.getProperty(getDevice(), "ro.build.tags")).isEqualTo(atom.getTags());
    }

    // Explicitly tests if the adb command to log a breadcrumb is working.
    public void testBreadcrumbAdb() throws Exception {
        final int atomTag = Atom.APP_BREADCRUMB_REPORTED_FIELD_NUMBER;
        ConfigUtils.uploadConfigForPushedAtom(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                atomTag);

        AtomTestUtils.sendAppBreadcrumbReportedAtom(getDevice());
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_SHORT);

        List<EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());
        AppBreadcrumbReported atom = data.get(0).getAtom().getAppBreadcrumbReported();
        assertThat(atom.getLabel()).isEqualTo(1);
        assertThat(atom.getState().getNumber()).isEqualTo(AppBreadcrumbReported.State.START_VALUE);
    }

    // Test dumpsys stats --proto.
    public void testDumpsysStats() throws Exception {
        final int atomTag = Atom.APP_BREADCRUMB_REPORTED_FIELD_NUMBER;
        ConfigUtils.uploadConfigForPushedAtom(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                atomTag);

        AtomTestUtils.sendAppBreadcrumbReportedAtom(getDevice());
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_SHORT);

        // Get the stats incident section.
        List<ConfigMetricsReportList> listList = getReportsFromStatsDataDumpProto();
        assertThat(listList).isNotEmpty();

        // Extract the relevant report from the incident section.
        ConfigMetricsReportList ourList = null;
        int hostUid = 2000; // Shell UID is always used in ConfigUtils.uploadConfig
        for (ConfigMetricsReportList list : listList) {
            ConfigMetricsReportList.ConfigKey configKey = list.getConfigKey();
            if (configKey.getUid() == hostUid && configKey.getId() == ConfigUtils.CONFIG_ID) {
                ourList = list;
                break;
            }
        }
        assertWithMessage(String.format("Could not find list for uid=%d id=%d", hostUid,
                ConfigUtils.CONFIG_ID))
                .that(ourList).isNotNull();

        // Make sure that the report is correct.
        List<EventMetricData> data = ReportUtils.getEventMetricDataList(ourList);
        AppBreadcrumbReported atom = data.get(0).getAtom().getAppBreadcrumbReported();
        assertThat(atom.getLabel()).isEqualTo(1);
        assertThat(atom.getState().getNumber()).isEqualTo(AppBreadcrumbReported.State.START_VALUE);
    }

    public void testConnectivityStateChange() throws Exception {
        if (!DeviceUtils.hasFeature(getDevice(), FEATURE_WIFI)) return;
        if (DeviceUtils.hasFeature(getDevice(), FEATURE_WATCH)) return;
        if (DeviceUtils.hasFeature(getDevice(), FEATURE_LEANBACK_ONLY)) return;

        final int atomTag = Atom.CONNECTIVITY_STATE_CHANGED_FIELD_NUMBER;
        ConfigUtils.uploadConfigForPushedAtom(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                atomTag);

        turnOnAirplaneMode();
        // wait long enough for airplane mode events to propagate.
        RunUtil.getDefault().sleep(1_200);
        turnOffAirplaneMode();
        // wait long enough for the device to restore connection
        RunUtil.getDefault().sleep(13_000);

        List<EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());
        // at least 1 disconnect and 1 connect
        assertThat(data.size()).isAtLeast(2);
        boolean foundDisconnectEvent = false;
        boolean foundConnectEvent = false;
        for (EventMetricData d : data) {
            ConnectivityStateChanged atom = d.getAtom().getConnectivityStateChanged();
            if (atom.getState().getNumber()
                    == ConnectivityStateChanged.State.DISCONNECTED_VALUE) {
                foundDisconnectEvent = true;
            }
            if (atom.getState().getNumber()
                    == ConnectivityStateChanged.State.CONNECTED_VALUE) {
                foundConnectEvent = true;
            }
        }
        assertThat(foundConnectEvent).isTrue();
        assertThat(foundDisconnectEvent).isTrue();
    }

    public void testAtomsLoggedOnBoot() throws Exception {
        ConfigUtils.uploadConfigForPushedAtoms(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                new int[] {
                    Atom.DEVICE_IDLE_MODE_STATE_CHANGED_FIELD_NUMBER,
                    Atom.SCREEN_STATE_CHANGED_FIELD_NUMBER,
                    Atom.BATTERY_LEVEL_CHANGED_FIELD_NUMBER,
                    Atom.CHARGING_STATE_CHANGED_FIELD_NUMBER,
                    Atom.PLUGGED_STATE_CHANGED_FIELD_NUMBER
                }
        );

        DeviceUtils.rebootDeviceAndWaitUntilReady(getDevice());
        RunUtil.getDefault().sleep(10_000);

        // Get events from the report after boot.
        List<Atom> atoms = ReportUtils
                .getEventMetricDataList(
                        getDevice(), ExtensionRegistry.getEmptyRegistry(), /*reportIndex*/ 1)
                .stream()
                .map(EventMetricData::getAtom)
                .collect(Collectors.toList());

        assertThat(atoms.stream().anyMatch(Atom::hasDeviceIdleModeStateChanged)).isTrue();
        assertThat(atoms.stream().anyMatch(Atom::hasScreenStateChanged)).isTrue();
        if (!DeviceUtils.hasFeature(getDevice(), FEATURE_AUTOMOTIVE)) {
            assertThat(atoms.stream().anyMatch(Atom::hasBatteryLevelChanged)).isTrue();
            assertThat(atoms.stream().anyMatch(Atom::hasChargingStateChanged)).isTrue();
            assertThat(atoms.stream().anyMatch(Atom::hasPluggedStateChanged)).isTrue();
        }
    }

    // Gets whether "Always on Display" setting is enabled.
    // In rare cases, this is different from whether the device can enter SCREEN_STATE_DOZE.
    private String getAodState() throws Exception {
        return getDevice().executeShellCommand("settings get secure doze_always_on");
    }

    private void setAodState(String state) throws Exception {
        getDevice().executeShellCommand("settings put secure doze_always_on " + state);
    }

    private void plugInUsb() throws Exception {
        getDevice().executeShellCommand("cmd battery set usb 1");
    }

    private void plugInWireless() throws Exception {
        getDevice().executeShellCommand("cmd battery set wireless 1");
    }

    /**
     * Determines if the device has |file|.
     */
    private boolean doesFileExist(String file) throws Exception {
        return getDevice().doesFileExist(file);
    }

    private void setBatteryLevel(int level) throws Exception {
        getDevice().executeShellCommand("cmd battery set level " + level);
    }

    private void leaveDozeMode() throws Exception {
        getDevice().executeShellCommand("dumpsys deviceidle unforce");
        getDevice().executeShellCommand("dumpsys deviceidle disable");
        getDevice().executeShellCommand("dumpsys deviceidle enable");
    }

    private void enterDozeModeLight() throws Exception {
        getDevice().executeShellCommand("dumpsys deviceidle force-idle light");
    }

    private void enterDozeModeDeep() throws Exception {
        getDevice().executeShellCommand("dumpsys deviceidle force-idle deep");
    }

    private void turnBatterySaverOff() throws Exception {
        getDevice().executeShellCommand("settings put global low_power 0");
        getDevice().executeShellCommand("cmd battery reset");
    }

    private void turnBatterySaverOn() throws Exception {
        DeviceUtils.unplugDevice(getDevice());
        getDevice().executeShellCommand("settings put global low_power 1");
    }

    private void turnOnAirplaneMode() throws Exception {
        getDevice().executeShellCommand("cmd connectivity airplane-mode enable");
    }

    private void turnOffAirplaneMode() throws Exception {
        getDevice().executeShellCommand("cmd connectivity airplane-mode disable");
    }

    /** Gets reports from the statsd data incident section from the stats dumpsys. */
    private List<ConfigMetricsReportList> getReportsFromStatsDataDumpProto() throws Exception {
        try {
            StatsDataDumpProto statsProto = DeviceUtils.getShellCommandOutput(
                    getDevice(),
                    StatsDataDumpProto.parser(),
                    String.join(" ", DUMPSYS_STATS_CMD, "--proto"));
            // statsProto holds repeated bytes, which we must parse into ConfigMetricsReportLists.
            List<ConfigMetricsReportList> reports
                    = new ArrayList<>(statsProto.getConfigMetricsReportListCount());
            for (ByteString reportListBytes : statsProto.getConfigMetricsReportListList()) {
                reports.add(ConfigMetricsReportList.parseFrom(reportListBytes));
            }
            LogUtil.CLog.d("Got dumpsys stats output:\n " + reports.toString());
            return reports;
        } catch (com.google.protobuf.InvalidProtocolBufferException e) {
            LogUtil.CLog.e("Failed to dumpsys stats proto");
            throw (e);
        }
    }
}
