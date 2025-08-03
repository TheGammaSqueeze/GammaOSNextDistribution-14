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

package android.cts.statsdatom.wifi;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.cts.statsdatom.lib.AtomTestUtils;
import android.cts.statsdatom.lib.ConfigUtils;
import android.cts.statsdatom.lib.DeviceUtils;
import android.cts.statsdatom.lib.ReportUtils;
import android.net.wifi.WifiModeEnum;

import com.android.compatibility.common.util.ApiLevelUtil;
import com.android.os.AtomsProto;
import com.android.os.StatsLog;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.testtype.DeviceTestCase;
import com.android.tradefed.testtype.IBuildReceiver;
import com.android.tradefed.util.RunUtil;

import com.google.common.collect.Range;
import com.google.protobuf.AbstractMessage;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class WifiStatsTests extends DeviceTestCase implements IBuildReceiver {
    private IBuildInfo mCtsBuild;

    private static final int WIFI_CONNECT_TIMEOUT_MILLIS = 30_000;
    private static final String FEATURE_PC = "android.hardware.type.pc";
    private static final String FEATURE_WIFI = "android.hardware.wifi";
    private static final String FEATURE_NAMESPACE = "wifi";
    private String mDefaultValue;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        assertThat(mCtsBuild).isNotNull();
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
        DeviceUtils.installStatsdTestApp(getDevice(), mCtsBuild);
        saveFeature("high_perf_lock_deprecated");
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);
    }

    @Override
    protected void tearDown() throws Exception {
        restoreFeature("high_perf_lock_deprecated");
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
        DeviceUtils.uninstallStatsdTestApp(getDevice());
        super.tearDown();
    }

    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mCtsBuild = buildInfo;
    }

    private void saveFeature(final String feature) throws Exception {
        mDefaultValue = DeviceUtils.getDeviceConfigFeature(getDevice(), FEATURE_NAMESPACE,
                feature);
    }

    private void setFeature(final String feature, final String enable) throws Exception {
        DeviceUtils.putDeviceConfigFeature(getDevice(), FEATURE_NAMESPACE, feature, enable);
    }

    private void restoreFeature(String feature) throws Exception {
        if (mDefaultValue == null || mDefaultValue.equals("null")) {
            DeviceUtils.deleteDeviceConfigFeature(getDevice(), FEATURE_NAMESPACE, feature);
        } else {
            DeviceUtils.putDeviceConfigFeature(getDevice(), FEATURE_NAMESPACE, feature,
                    mDefaultValue);
        }
    }
    /**
     * Test High Perf lock with the wifi:high_perf_lock_deprecated = false.
     */
    public void testWifiLockHighPerf() throws Exception {
        if (!DeviceUtils.hasFeature(getDevice(), FEATURE_WIFI)) return;
        if (DeviceUtils.hasFeature(getDevice(), FEATURE_PC)) return;

        setFeature("high_perf_lock_deprecated", "false");
        ConfigUtils.uploadConfigForPushedAtomWithUid(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                AtomsProto.Atom.WIFI_LOCK_STATE_CHANGED_FIELD_NUMBER, true);
        DeviceUtils.runDeviceTestsOnStatsdApp(getDevice(), ".AtomTests", "testWifiLockHighPerf");
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);

        // Sorted list of events in order in which they occurred.
        List<StatsLog.EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());

        Set<Integer> lockOn = new HashSet<>(
                Collections.singletonList(AtomsProto.WifiLockStateChanged.State.ON_VALUE));
        Set<Integer> lockOff = new HashSet<>(
                Collections.singletonList(AtomsProto.WifiLockStateChanged.State.OFF_VALUE));

        // Add state sets to the list in order.
        List<Set<Integer>> stateSet = Arrays.asList(lockOn, lockOff);

        // Assert that the events happened in the expected order.
        AtomTestUtils.assertStatesOccurredInOrder(stateSet, data, AtomTestUtils.WAIT_TIME_SHORT,
                atom -> atom.getWifiLockStateChanged().getState().getNumber());

        for (StatsLog.EventMetricData event : data) {
            assertThat(event.getAtom().getWifiLockStateChanged().getMode())
                    .isEqualTo(WifiModeEnum.WIFI_MODE_FULL_HIGH_PERF);
        }
    }

    /**
     * Test High Perf lock with the wifi:high_perf_lock_deprecated = true.
     */
    public void testWifiLockHighPerfDeprecated() throws Exception {
        if (!DeviceUtils.hasFeature(getDevice(), FEATURE_WIFI)) return;
        if (DeviceUtils.hasFeature(getDevice(), FEATURE_PC)) return;

        setFeature("high_perf_lock_deprecated", "true");
        ConfigUtils.uploadConfigForPushedAtomWithUid(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                AtomsProto.Atom.WIFI_LOCK_STATE_CHANGED_FIELD_NUMBER, true);

        // High perf lock is deprecated from Android U onwards. Acquisition of  High perf lock will
        // be treated as a call to Low Latency Lock.
        //
        // For low latency lock to be active following conditions need to be met,
        //    - Wi-Fi is connected
        //    - Screen On
        //    - Application is foreground.

        // Check Wi-Fi is connected.
        DeviceUtils.runDeviceTestsOnStatsdApp(getDevice(), ".AtomTests", "testWifiConnected");

        // Turn screen on.
        DeviceUtils.turnScreenOn(getDevice());

        // Acquire and release highperf lock in foreground activity.
        DeviceUtils.runActivity(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                "StatsdCtsForegroundActivity", "action", "action.acquire_release_wifi_hiperf_lock");
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);

        // Sorted list of events in order in which they occurred.
        List<StatsLog.EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());

        Set<Integer> lockOn = new HashSet<>(
                Collections.singletonList(AtomsProto.WifiLockStateChanged.State.ON_VALUE));
        Set<Integer> lockOff = new HashSet<>(
                Collections.singletonList(AtomsProto.WifiLockStateChanged.State.OFF_VALUE));

        // Add state sets to the list in order.
        List<Set<Integer>> stateSet = Arrays.asList(lockOn, lockOff);

        // Assert that the events happened in the expected order.
        AtomTestUtils.assertStatesOccurredInOrder(stateSet, data, AtomTestUtils.WAIT_TIME_SHORT,
                atom -> atom.getWifiLockStateChanged().getState().getNumber());

        for (StatsLog.EventMetricData event : data) {
            // High perf lock is deprecated from Android U onwards. Acquisition of high perf lock is
            // treated as low latency lock.
            assertThat(event.getAtom().getWifiLockStateChanged().getMode())
                    .isEqualTo(
                            (ApiLevelUtil.isAfter(getDevice(), "TIRAMISU")
                                    || ApiLevelUtil.codenameStartsWith(getDevice(), "U")
                                    || ApiLevelUtil.codenameStartsWith(getDevice(), "V"))
                                    ? WifiModeEnum.WIFI_MODE_FULL_LOW_LATENCY
                                    : WifiModeEnum.WIFI_MODE_FULL_HIGH_PERF);
        }
    }

    public void testWifiLockLowLatency() throws Exception {
        if (!DeviceUtils.hasFeature(getDevice(), FEATURE_WIFI)) return;
        if (DeviceUtils.hasFeature(getDevice(), FEATURE_PC)) return;

        ConfigUtils.uploadConfigForPushedAtomWithUid(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                AtomsProto.Atom.WIFI_LOCK_STATE_CHANGED_FIELD_NUMBER, true);

        // For low latency lock to be active following conditions need to be met,
        //    - Wi-Fi is connected
        //    - Screen On
        //    - Application is foreground.

        // Check Wi-Fi is connected.
        DeviceUtils.runDeviceTestsOnStatsdApp(getDevice(), ".AtomTests", "testWifiConnected");

        // Turn screen on.
        DeviceUtils.turnScreenOn(getDevice());

        // Acquire and release low latency lock in foreground activity.
        DeviceUtils.runActivity(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                "StatsdCtsForegroundActivity", "action", "action.acquire_release_wifi_ll_lock");
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);

        // Sorted list of events in order in which they occurred.
        List<StatsLog.EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());

        Set<Integer> lockOn = new HashSet<>(
                Collections.singletonList(AtomsProto.WifiLockStateChanged.State.ON_VALUE));
        Set<Integer> lockOff = new HashSet<>(
                Collections.singletonList(AtomsProto.WifiLockStateChanged.State.OFF_VALUE));

        // Add state sets to the list in order.
        List<Set<Integer>> stateSet = Arrays.asList(lockOn, lockOff);

        // Assert that the events happened in the expected order.
        AtomTestUtils.assertStatesOccurredInOrder(stateSet, data, AtomTestUtils.WAIT_TIME_SHORT,
                atom -> atom.getWifiLockStateChanged().getState().getNumber());

        for (StatsLog.EventMetricData event : data) {
            assertThat(event.getAtom().getWifiLockStateChanged().getMode())
                    .isEqualTo(WifiModeEnum.WIFI_MODE_FULL_LOW_LATENCY);
        }
    }

    public void testWifiMulticastLock() throws Exception {
        if (!DeviceUtils.hasFeature(getDevice(), FEATURE_WIFI)) return;
        if (DeviceUtils.hasFeature(getDevice(), FEATURE_PC)) return;

        ConfigUtils.uploadConfigForPushedAtomWithUid(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                AtomsProto.Atom.WIFI_MULTICAST_LOCK_STATE_CHANGED_FIELD_NUMBER, true);
        DeviceUtils.runDeviceTestsOnStatsdApp(getDevice(), ".AtomTests", "testWifiMulticastLock");
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);

        // Sorted list of events in order in which they occurred.
        List<StatsLog.EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());

        Set<Integer> lockOn = new HashSet<>(
                Collections.singletonList(AtomsProto.WifiMulticastLockStateChanged.State.ON_VALUE));
        Set<Integer> lockOff = new HashSet<>(
                Collections.singletonList(
                        AtomsProto.WifiMulticastLockStateChanged.State.OFF_VALUE));

        final String EXPECTED_TAG = "StatsdCTSMulticastLock";

        // Add state sets to the list in order.
        List<Set<Integer>> stateSet = Arrays.asList(lockOn, lockOff);

        // Assert that the events happened in the expected order.
        AtomTestUtils.assertStatesOccurredInOrder(stateSet, data, AtomTestUtils.WAIT_TIME_SHORT,
                atom -> atom.getWifiMulticastLockStateChanged().getState().getNumber());

        for (StatsLog.EventMetricData event : data) {
            String tag = event.getAtom().getWifiMulticastLockStateChanged().getTag();
            assertThat(tag).isEqualTo(EXPECTED_TAG);
        }
    }

    public void testWifiReconnect() throws Exception {
        if (!DeviceUtils.hasFeature(getDevice(), FEATURE_WIFI)) return;

        ConfigUtils.uploadConfigForPushedAtoms(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                new int[] {
                        AtomsProto.Atom.WIFI_CONNECTION_RESULT_REPORTED_FIELD_NUMBER,
                        AtomsProto.Atom.WIFI_DISCONNECT_REPORTED_FIELD_NUMBER
                });

        // This test on device checks if device is connected, and connects it if it is not;
        // Afterwards, it disconnects from that network and connects back to it.
        DeviceUtils.runDeviceTestsOnStatsdApp(getDevice(), ".AtomTests", "testWifiReconnect");
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);

        List<StatsLog.EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());

        // If device had Wifi connected, we'll see two atoms: disconnect, connect.
        // If it was not connected, we'll see three: connect, disconnect, connect.
        // We're only interested in the disconnect-connect pair.
        assertWithMessage(
                "Expected disconnected and connected atoms, got: \n" +
                        data.stream().map(AbstractMessage::toString).reduce((acc, i) -> acc + i)
        ).that(data.size()).isIn(Range.closed(2, 3));

        AtomsProto.WifiDisconnectReported a0 =
                data.get(data.size() - 2).getAtom().getWifiDisconnectReported();
        AtomsProto.WifiConnectionResultReported a1 =
                data.get(data.size() - 1).getAtom().getWifiConnectionResultReported();

        assertThat(a0).isNotNull();
        assertThat(a1).isNotNull();

        assertThat(a0.getConnectedDurationSeconds()).isGreaterThan(0);
        int maxLinkSpeedMbps = 1_000_000; /* 640K ought to be enough for anybody. */
        assertThat(a0.getLastLinkSpeed()).isIn(Range.open(0, maxLinkSpeedMbps));
        assertThat(a0.getLastRssi()).isIn(Range.closed(-127, 0));

        assertThat(a1.getConnectionResult()).isTrue();
        assertThat(a1.getRssi()).isIn(Range.closed(-127, 0));
        assertThat(a1.getConnectionAttemptDurationMillis()).isIn(
                Range.open(0, WIFI_CONNECT_TIMEOUT_MILLIS));
        assertThat(a1.getTrigger()).isEqualTo(
                AtomsProto.WifiConnectionResultReported.Trigger.RECONNECT_SAME_NETWORK);
        assertThat(a1.getNetworkUsed()).isTrue();
    }

    public void testWifiScanLogsScanAtoms() throws Exception {
        if (!DeviceUtils.hasFeature(getDevice(), FEATURE_WIFI)) return;

        ConfigUtils.uploadConfigForPushedAtom(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                AtomsProto.Atom.WIFI_SCAN_REPORTED_FIELD_NUMBER);
        DeviceUtils.runDeviceTestsOnStatsdApp(getDevice(), ".AtomTests", "testWifiScan");
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);

        List<StatsLog.EventMetricData> metricData = ReportUtils.getEventMetricDataList(getDevice());
        List<AtomsProto.WifiScanReported> wifiScanAtoms = metricData.stream()
                .map(eventLog -> eventLog.getAtom().getWifiScanReported())
                // Disregard interfering scans from other sources.
                // If this test is run on a device that has a Settings app open that
                // continuously performs frequent scans, quite often our scans requests
                // are bundled together and get attributed to the Settings app.
                .filter(scan -> List.of(
                                AtomsProto.WifiScanReported.Source.SOURCE_OTHER_APP,
                                AtomsProto.WifiScanReported.Source.SOURCE_SETTINGS_APP)
                        .contains(scan.getSource()))
                .filter(Predicate.not(scan -> scan.getImportance().equals(
                        AtomsProto.WifiScanReported.Importance.IMPORTANCE_UNKNOWN)))
                .collect(Collectors.toList());
        assertThat(wifiScanAtoms).isNotEmpty();

        for (AtomsProto.WifiScanReported scan : wifiScanAtoms) {
            assertThat(scan.getResult()).isEqualTo(
                    AtomsProto.WifiScanReported.Result.RESULT_SUCCESS);
            assertThat(scan.getType()).isEqualTo(AtomsProto.WifiScanReported.Type.TYPE_SINGLE);
            List<AtomsProto.WifiScanReported.Importance> expectedResult =
                    Arrays.asList(
                            AtomsProto.WifiScanReported.Importance.IMPORTANCE_FOREGROUND_SERVICE,
                            AtomsProto.WifiScanReported.Importance.IMPORTANCE_FOREGROUND);
            assertThat(scan.getImportance()).isIn(expectedResult);
            assertThat(scan.getScanDurationMillis()).isGreaterThan(0);
        }
    }

    public void testWifiScanLogsStateChangedAtoms() throws Exception {
        if (!DeviceUtils.hasFeature(getDevice(), FEATURE_WIFI)) return;


        ConfigUtils.uploadConfigForPushedAtomWithUid(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                AtomsProto.Atom.WIFI_SCAN_STATE_CHANGED_FIELD_NUMBER, true);
        DeviceUtils.runDeviceTestsOnStatsdApp(getDevice(), ".AtomTests", "testWifiScan");
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);

        final int stateOn = AtomsProto.WifiScanStateChanged.State.ON_VALUE;
        final int stateOff = AtomsProto.WifiScanStateChanged.State.OFF_VALUE;
        final int minTimeDiffMillis = 250;
        final int maxTimeDiffMillis = 60_000;

        List<StatsLog.EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());
        assertThat(data.size()).isIn(Range.closed(2, 4));
        AtomTestUtils.assertTimeDiffBetween(data.get(0), data.get(1), minTimeDiffMillis,
                maxTimeDiffMillis);
        AtomsProto.WifiScanStateChanged a0 = data.get(0).getAtom().getWifiScanStateChanged();
        AtomsProto.WifiScanStateChanged a1 = data.get(1).getAtom().getWifiScanStateChanged();
        assertThat(a0.getState().getNumber()).isEqualTo(stateOn);
        assertThat(a1.getState().getNumber()).isEqualTo(stateOff);
    }
}
