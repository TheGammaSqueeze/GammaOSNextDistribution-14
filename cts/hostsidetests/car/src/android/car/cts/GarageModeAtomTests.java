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
package android.car.cts;

import com.android.tradefed.util.RunUtil;
import static com.google.common.truth.Truth.assertThat;

import android.cts.statsdatom.lib.AtomTestUtils;
import android.cts.statsdatom.lib.ConfigUtils;
import android.cts.statsdatom.lib.DeviceUtils;
import android.cts.statsdatom.lib.ReportUtils;

import com.android.os.AtomsProto.Atom;
import com.android.os.StatsLog.EventMetricData;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.testtype.DeviceTestCase;
import com.android.tradefed.testtype.IBuildReceiver;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Verifies that Automotive's Garage Mode reports its status.
 *
 * <p> {@code statsd} atom tests are done via adb (hostside).
 */
public class GarageModeAtomTests extends DeviceTestCase implements IBuildReceiver {

    private static final String TAG = "Statsd.GarageModeAtomTests";
    private static final String FEATURE_AUTOMOTIVE = "android.hardware.type.automotive";

    private IBuildInfo mCtsBuild;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        assertThat(mCtsBuild).isNotNull();
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());

        // Give enough time to remove/clear reports in statsd because that happens
        // asynchronously
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_SHORT);

        DeviceUtils.installStatsdTestApp(getDevice(), mCtsBuild);
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

    public void testGarageModeOnOff() throws Exception {
        if (!DeviceUtils.hasFeature(getDevice(), FEATURE_AUTOMOTIVE)) {
            return;
        }

        ConfigUtils.uploadConfigForPushedAtom(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                Atom.GARAGE_MODE_INFO_FIELD_NUMBER);

        // Garage mode ON
        Set<Integer> garageModeOn = new HashSet<>(Arrays.asList(1));
        // Garage mode OFF
        Set<Integer> garageModeOff = new HashSet<>(Arrays.asList(0));
        List<Set<Integer>> stateSet = Arrays.asList(garageModeOn, garageModeOff);

        turnOnGarageMode();
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_SHORT);
        turnOffGarageMode();
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_SHORT);

        List<EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());
        AtomTestUtils.assertStatesOccurredInOrder(stateSet, data, AtomTestUtils.WAIT_TIME_SHORT,
                atom -> atom.getGarageModeInfo().getIsGarageMode() ?  1 : 0);

    }

    private void turnOnGarageMode() throws Exception {
        getDevice().executeShellCommand("cmd car_service garage-mode on");
    }

    private void turnOffGarageMode() throws Exception {
        getDevice().executeShellCommand("cmd car_service garage-mode off");
    }
}
