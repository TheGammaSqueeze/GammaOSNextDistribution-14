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

package android.cts.statsdatom.devicestate;

import com.android.tradefed.util.RunUtil;
import static com.google.common.truth.Truth.assertThat;

import android.cts.statsdatom.lib.AtomTestUtils;
import android.cts.statsdatom.lib.ConfigUtils;
import android.cts.statsdatom.lib.DeviceUtils;
import android.cts.statsdatom.lib.ReportUtils;

import com.android.os.AtomsProto;
import com.android.os.StatsLog;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.device.DeviceFoldableState;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.testtype.DeviceTestCase;
import com.android.tradefed.testtype.IBuildReceiver;
import com.android.compatibility.common.util.PollingCheck;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * This test is for making sure that device state changes send the desired metrics.
 */
public class DeviceStateStatsTests extends DeviceTestCase implements IBuildReceiver {

    private IBuildInfo mCtsBuild;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        assertThat(mCtsBuild).isNotNull();
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);
    }

    @Override
    protected void tearDown() throws Exception {
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
        super.tearDown();
    }

    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mCtsBuild = buildInfo;
    }

    /**
     * Queries the device using an adb command for all the possible device states, then switches
     * to all the possible states one after another.
     */
    public void testDeviceStateChanges() throws Exception {
        ConfigUtils.uploadConfigForPushedAtom(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                AtomsProto.Atom.DEVICE_STATE_CHANGED_FIELD_NUMBER);

        List<Integer> availableStates = Arrays.stream(getDevice()
                .executeShellCommand("cmd device_state print-states-simple")
                .split(","))
                .map(Integer::valueOf)
                .collect(Collectors.toList());
        if (availableStates.size() == 1) {
            CLog.i("Device doesn't have more than one available state.");
            return;
        }

        int currentState = Integer.parseInt(getDevice()
                .executeShellCommand("cmd device_state print-state").trim());
        for (int i = 0; i < availableStates.size(); i++) {
            int currentStateIndex = availableStates.indexOf(currentState);
            int nextStateIndex = currentStateIndex + 1;
            if (nextStateIndex >= availableStates.size()) {
                nextStateIndex = 0;
            }
            int nextState = availableStates.get(nextStateIndex);
            CLog.d("testDeviceStateChange: from " + currentState + " to " + nextState);
            testDeviceStateChange(nextState);

            currentState = nextState;
        }
    }

    private void testDeviceStateChange(int nextState) throws Exception {
        getDevice().executeShellCommand("cmd device_state state " + nextState);

        // wait device state change complete.
        PollingCheck.waitFor(2000, () -> nextState == getDevice()
                .getCurrentFoldableState().getIdentifier());

        List<StatsLog.EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());
        assertThat(data.size()).isEqualTo(1);

        AtomsProto.DeviceStateChanged deviceStateChanged =
                data.get(0).getAtom().getDeviceStateChanged();
        assertThat(deviceStateChanged.getNewDeviceState()).isEqualTo(nextState);

        ReportUtils.clearReports(getDevice());
    }
}
