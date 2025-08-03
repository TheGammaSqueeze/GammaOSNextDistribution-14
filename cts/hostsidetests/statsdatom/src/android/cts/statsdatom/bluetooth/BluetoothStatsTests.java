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

package android.cts.statsdatom.bluetooth;

import com.android.tradefed.util.RunUtil;
import static com.google.common.truth.Truth.assertThat;

import android.cts.statsdatom.lib.AtomTestUtils;
import android.cts.statsdatom.lib.ConfigUtils;
import android.cts.statsdatom.lib.DeviceUtils;
import android.cts.statsdatom.lib.ReportUtils;

import com.android.internal.os.StatsdConfigProto;
import com.android.os.AtomsProto;
import com.android.os.StatsLog;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.testtype.DeviceTestCase;
import com.android.tradefed.testtype.IBuildReceiver;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class BluetoothStatsTests extends DeviceTestCase implements IBuildReceiver {
    private static final String FEATURE_BLUETOOTH_LE = "android.hardware.bluetooth_le";

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

    public void testBleScan() throws Exception {
        if (!DeviceUtils.hasFeature(getDevice(), FEATURE_BLUETOOTH_LE)) return;

        final int atomTag = AtomsProto.Atom.BLE_SCAN_STATE_CHANGED_FIELD_NUMBER;
        Set<Integer> onState = new HashSet<>(
                Collections.singletonList(AtomsProto.BleScanStateChanged.State.ON_VALUE));
        Set<Integer> offState = new HashSet<>(
                Collections.singletonList(AtomsProto.BleScanStateChanged.State.OFF_VALUE));
        final int expectedWait = 3_000;
        // Add state sets to the list in order.
        List<Set<Integer>> stateSet = Arrays.asList(onState, offState);
        ConfigUtils.uploadConfigForPushedAtomWithUid(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                atomTag, /*useAttributionChain=*/ true);

        DeviceUtils.runDeviceTestsOnStatsdApp(getDevice(), ".AtomTests", "testBleScanUnoptimized");
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_SHORT);

        List<StatsLog.EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());
        AtomTestUtils.assertStatesOccurredInOrder(stateSet, data, expectedWait,
                atom -> atom.getBleScanStateChanged().getState().getNumber());
    }

    public void testBleUnoptimizedScan() throws Exception {
        if (!DeviceUtils.hasFeature(getDevice(), FEATURE_BLUETOOTH_LE)) return;

        final int atomTag = AtomsProto.Atom.BLE_SCAN_STATE_CHANGED_FIELD_NUMBER;
        Set<Integer> onState = new HashSet<>(
                Collections.singletonList(AtomsProto.BleScanStateChanged.State.ON_VALUE));
        Set<Integer> offState = new HashSet<>(
                Collections.singletonList(AtomsProto.BleScanStateChanged.State.OFF_VALUE));
        final int minTimeDiffMillis = 1_500;
        final int maxTimeDiffMillis = 3_000;
        // Add state sets to the list in order.
        List<Set<Integer>> stateSet = Arrays.asList(onState, offState);
        ConfigUtils.uploadConfigForPushedAtomWithUid(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                atomTag, /*useAttributionChain=*/ true);

        DeviceUtils.runDeviceTestsOnStatsdApp(getDevice(), ".AtomTests", "testBleScanUnoptimized");
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);

        List<StatsLog.EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());
        AtomTestUtils.assertTimeDiffBetween(data.get(0), data.get(1), minTimeDiffMillis,
                maxTimeDiffMillis);
        AtomsProto.BleScanStateChanged a0 = data.get(0).getAtom().getBleScanStateChanged();
        assertThat(a0.getState().getNumber()).isEqualTo(
                AtomsProto.BleScanStateChanged.State.ON_VALUE);
        assertThat(a0.getIsFiltered()).isFalse();
        assertThat(a0.getIsFirstMatch()).isFalse();
        assertThat(a0.getIsOpportunistic()).isFalse();
        AtomsProto.BleScanStateChanged a1 = data.get(1).getAtom().getBleScanStateChanged();
        assertThat(a1.getState().getNumber()).isEqualTo(
                AtomsProto.BleScanStateChanged.State.OFF_VALUE);
        assertThat(a1.getIsFiltered()).isFalse();
        assertThat(a1.getIsFirstMatch()).isFalse();
        assertThat(a1.getIsOpportunistic()).isFalse();
    }

    public void testBleOpportunisticScan() throws Exception {
        if (!DeviceUtils.hasFeature(getDevice(), FEATURE_BLUETOOTH_LE)) return;

        final int atomTag = AtomsProto.Atom.BLE_SCAN_STATE_CHANGED_FIELD_NUMBER;
        Set<Integer> onState = new HashSet<>(
                Collections.singletonList(AtomsProto.BleScanStateChanged.State.ON_VALUE));
        Set<Integer> offState = new HashSet<>(
                Collections.singletonList(AtomsProto.BleScanStateChanged.State.OFF_VALUE));
        final int minTimeDiffMillis = 1_500;
        final int maxTimeDiffMillis = 3_000;
        // Add state sets to the list in order.
        List<Set<Integer>> stateSet = Arrays.asList(onState, offState);
        ConfigUtils.uploadConfigForPushedAtomWithUid(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                atomTag, /*useAttributionChain=*/ true);

        DeviceUtils.runDeviceTestsOnStatsdApp(getDevice(), ".AtomTests",
                "testBleScanOpportunistic");

        List<StatsLog.EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());
        AtomTestUtils.assertTimeDiffBetween(data.get(0), data.get(1), minTimeDiffMillis,
                maxTimeDiffMillis);
        AtomsProto.BleScanStateChanged a0 = data.get(0).getAtom().getBleScanStateChanged();
        assertThat(a0.getState().getNumber()).isEqualTo(
                AtomsProto.BleScanStateChanged.State.ON_VALUE);
        assertThat(a0.getIsFiltered()).isFalse();
        assertThat(a0.getIsFirstMatch()).isFalse();
        assertThat(a0.getIsOpportunistic()).isTrue();  // This scan is opportunistic.
        AtomsProto.BleScanStateChanged a1 = data.get(1).getAtom().getBleScanStateChanged();
        assertThat(a1.getState().getNumber()).isEqualTo(
                AtomsProto.BleScanStateChanged.State.OFF_VALUE);
        assertThat(a1.getIsFiltered()).isFalse();
        assertThat(a1.getIsFirstMatch()).isFalse();
        assertThat(a1.getIsOpportunistic()).isTrue();
    }
}
