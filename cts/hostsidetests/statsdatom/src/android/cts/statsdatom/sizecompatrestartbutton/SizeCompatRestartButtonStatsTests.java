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

package android.cts.statsdatom.sizecompatrestartbutton;

import com.android.tradefed.util.RunUtil;
import static com.google.common.truth.Truth.assertThat;

import android.cts.statsdatom.lib.AtomTestUtils;
import android.cts.statsdatom.lib.ConfigUtils;
import android.cts.statsdatom.lib.DeviceUtils;
import android.cts.statsdatom.lib.ReportUtils;

import com.android.os.AtomsProto;
import com.android.os.AtomsProto.SizeCompatRestartButtonEventReported;
import com.android.os.AtomsProto.SizeCompatRestartButtonEventReported.Event;
import com.android.os.StatsLog;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.testtype.DeviceTestCase;
import com.android.tradefed.testtype.IBuildReceiver;
import com.android.tradefed.util.Pair;

import java.util.List;

/**
 * This test is for making sure that Size Compat Restart Button appearances and clicks log the
 * desired atoms.
 *
 *  <p>Build/Install/Run:
 *  atest CtsStatsdAtomHostTestCases:SizeCompatRestartButtonStatsTests
 */
// TODO(b/197223993): add test for clicked event
public class SizeCompatRestartButtonStatsTests extends DeviceTestCase implements IBuildReceiver {

    private static final String NON_RESIZEABLE_PORTRAIT_ACTIVITY =
            "StatsdCtsNonResizeablePortraitActivity";
    private static final String CMD_GET_STAY_ON = "settings get global stay_on_while_plugged_in";
    private static final String CMD_PUT_STAY_ON_TEMPLATE =
            "settings put global stay_on_while_plugged_in %d";
    private static final int ENABLE_STAY_ON_CODE = 7;
    private static final String CMD_GET_AVAILABLE_DEVICE_STATES =
            "cmd device_state print-states-simple";
    private static final String CMD_RESET_DEVICE_STATE = "cmd device_state state reset";
    private static final String CMD_PUT_DEVICE_STATE_TEMPLATE = "cmd device_state state %d";
    private static final int DEVICE_STATE_CLOSED = 0;
    private static final int DEVICE_STATE_OPENED = 2;

    private IBuildInfo mCtsBuild;
    private long mOriginalStayOnSetting;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        assertThat(mCtsBuild).isNotNull();
        mOriginalStayOnSetting = Long.parseLong(
                getDevice().executeShellCommand(CMD_GET_STAY_ON).trim());
        getDevice().executeShellCommand(
                String.format(CMD_PUT_STAY_ON_TEMPLATE, ENABLE_STAY_ON_CODE));
        getDevice().executeShellCommand(
                String.format(CMD_PUT_DEVICE_STATE_TEMPLATE, DEVICE_STATE_CLOSED));
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
        DeviceUtils.installStatsdTestApp(getDevice(), mCtsBuild);
        DeviceUtils.turnScreenOn(getDevice());
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);
        ConfigUtils.uploadConfigForPushedAtomWithUid(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                AtomsProto.Atom.SIZE_COMPAT_RESTART_BUTTON_EVENT_REPORTED_FIELD_NUMBER,
                /*uidInAttributionChain=*/ false);
    }

    @Override
    protected void tearDown() throws Exception {
        getDevice().executeShellCommand(
                String.format(CMD_PUT_STAY_ON_TEMPLATE, mOriginalStayOnSetting));
        getDevice().executeShellCommand(CMD_RESET_DEVICE_STATE);
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
        DeviceUtils.uninstallStatsdTestApp(getDevice());
        super.tearDown();
    }

    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mCtsBuild = buildInfo;
    }

    public void testSizeCompatRestartButtonAppearedButNotClicked() throws Exception {
        if (!isFoldableStateAvailable(DEVICE_STATE_OPENED)
                || !isFoldableStateAvailable(DEVICE_STATE_CLOSED)) {
            CLog.i("Device doesn't support OPENED or CLOSED device states.");
            return;
        }

        Pair<Integer, Integer> displaySizeClosed = getDisplayRealSize(getDevice());
        if (displaySizeClosed == null) {
            CLog.i("Could not determine display size while CLOSED.");
            return;
        }
        try (AutoCloseable a = DeviceUtils.withActivity(getDevice(),
                DeviceUtils.STATSD_ATOM_TEST_PKG, NON_RESIZEABLE_PORTRAIT_ACTIVITY, "action",
                "action.sleep_top")) {
            getDevice().executeShellCommand(
                    String.format(CMD_PUT_DEVICE_STATE_TEMPLATE, DEVICE_STATE_OPENED));
            RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);
        }

        Pair<Integer, Integer> displaySizeOpened = getDisplayRealSize(getDevice());
        if (displaySizeOpened == null) {
            CLog.i("Could not determine display size while OPENED.");
            return;
        }
        if (displaySizeClosed.equals(displaySizeOpened)) {
            CLog.i("Display size has not changed.");
            return;
        }
        List<StatsLog.EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());
        assertThat(data.size()).isEqualTo(1);

        SizeCompatRestartButtonEventReported atom =
                data.get(0).getAtom().getSizeCompatRestartButtonEventReported();
        assertThat(atom.getUid()).isEqualTo(DeviceUtils.getStatsdTestAppUid(getDevice()));
        assertThat(atom.getEvent()).isEqualTo(Event.APPEARED);
    }

    private boolean isFoldableStateAvailable(int state) throws Exception {
        return getDevice().getFoldableStates().stream().anyMatch(
                foldableState -> foldableState.getIdentifier() == state);
    }

    /**
     * Returns the physical size of the current display used.
     */
    private Pair<Integer, Integer> getDisplayRealSize(ITestDevice device) throws Exception {
        final String physicalSize = "Physical size: ";
        String str = device.executeShellCommand("wm size");
        if (!str.isEmpty()) {
            String[] lines = str.split(System.getProperty("line.separator"));
            for (String s : lines) {
                if (s.contains(physicalSize)) {
                    String substring = s.substring(physicalSize.length());
                    if (!substring.isEmpty()) {
                        return Pair.create(Integer.parseInt(substring.split("x")[0]),
                                Integer.parseInt(substring.split("x")[1]));
                    }
                }
            }
        }
        return null;
    }
}
