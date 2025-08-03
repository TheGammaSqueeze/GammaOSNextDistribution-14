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

import android.app.ProcessStateEnum; // From enums.proto for atoms.proto's UidProcessStateChanged.
import android.cts.statsdatom.lib.AtomTestUtils;
import android.cts.statsdatom.lib.ConfigUtils;
import android.cts.statsdatom.lib.DeviceUtils;
import android.cts.statsdatom.lib.ReportUtils;

import com.android.os.AtomsProto.Atom;
import com.android.os.StatsLog.EventMetricData;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.testtype.DeviceTestCase;
import com.android.tradefed.testtype.IBuildReceiver;
import com.android.tradefed.util.RunUtil;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Statsd atom tests that are done via app, for atoms that report a uid.
 */
public class ProcStateAtomTests extends DeviceTestCase implements IBuildReceiver {

    private static final String TAG = "Statsd.ProcStateAtomTests";

    private static final String DEVICE_SIDE_FG_ACTIVITY_COMPONENT
            = "com.android.server.cts.device.statsdatom/.StatsdCtsForegroundActivity";
    private static final String DEVICE_SIDE_FG_SERVICE_COMPONENT
            = "com.android.server.cts.device.statsdatom/.StatsdCtsForegroundService";

    // Constants from the device-side tests (not directly accessible here).
    private static final String ACTION_END_IMMEDIATELY = "action.end_immediately";
    private static final String ACTION_BACKGROUND_SLEEP = "action.background_sleep";
    private static final String ACTION_SLEEP_WHILE_TOP = "action.sleep_top";
    private static final String ACTION_LONG_SLEEP_WHILE_TOP = "action.long_sleep_top";
    private static final String ACTION_SHOW_APPLICATION_OVERLAY = "action.show_application_overlay";

    // Sleep times (ms) that actions invoke device-side.
    private static final int SLEEP_OF_ACTION_SLEEP_WHILE_TOP = 2_000;
    private static final int SLEEP_OF_ACTION_LONG_SLEEP_WHILE_TOP = 60_000;
    private static final int SLEEP_OF_ACTION_BACKGROUND_SLEEP = 2_000;
    private static final int SLEEP_OF_FOREGROUND_SERVICE = 2_000;

    private static final int WAIT_TIME_FOR_CONFIG_UPDATE_MS = 200;
    // ActivityManager can take a while to register screen state changes, mandating an extra delay.
    private static final int WAIT_TIME_FOR_SCREEN_MS = 1_000;
    private static final int EXTRA_WAIT_TIME_MS = 5_000; // as buffer when proc state changing.
    private static final int STATSD_REPORT_WAIT_TIME_MS = 500; // make sure statsd finishes log.

    private static final String FEATURE_WATCH = "android.hardware.type.watch";

    // The tests here are using the BatteryStats definition of 'background'.
    private static final Set<Integer> BG_STATES = new HashSet<>(
            Arrays.asList(
                    ProcessStateEnum.PROCESS_STATE_IMPORTANT_BACKGROUND_VALUE,
                    ProcessStateEnum.PROCESS_STATE_TRANSIENT_BACKGROUND_VALUE,
                    ProcessStateEnum.PROCESS_STATE_BACKUP_VALUE,
                    ProcessStateEnum.PROCESS_STATE_SERVICE_VALUE,
                    ProcessStateEnum.PROCESS_STATE_RECEIVER_VALUE,
                    ProcessStateEnum.PROCESS_STATE_HEAVY_WEIGHT_VALUE
            ));

    // Using the BatteryStats definition of 'cached', which is why HOME (etc) are considered cached.
    private static final Set<Integer> CACHED_STATES = new HashSet<>(
            Arrays.asList(
                    ProcessStateEnum.PROCESS_STATE_HOME_VALUE,
                    ProcessStateEnum.PROCESS_STATE_LAST_ACTIVITY_VALUE,
                    ProcessStateEnum.PROCESS_STATE_CACHED_ACTIVITY_VALUE,
                    ProcessStateEnum.PROCESS_STATE_CACHED_ACTIVITY_CLIENT_VALUE,
                    ProcessStateEnum.PROCESS_STATE_CACHED_RECENT_VALUE,
                    ProcessStateEnum.PROCESS_STATE_CACHED_EMPTY_VALUE
            ));

    private static final Set<Integer> MISC_STATES = new HashSet<>(
            Arrays.asList(
                    ProcessStateEnum.PROCESS_STATE_PERSISTENT_VALUE, // TODO: untested
                    ProcessStateEnum.PROCESS_STATE_PERSISTENT_UI_VALUE, // TODO: untested
                    ProcessStateEnum.PROCESS_STATE_TOP_VALUE,
                    ProcessStateEnum.PROCESS_STATE_BOUND_TOP_VALUE, // TODO: untested
                    ProcessStateEnum.PROCESS_STATE_BOUND_FOREGROUND_SERVICE_VALUE, // TODO: untested
                    ProcessStateEnum.PROCESS_STATE_FOREGROUND_SERVICE_VALUE,
                    ProcessStateEnum.PROCESS_STATE_IMPORTANT_FOREGROUND_VALUE,
                    ProcessStateEnum.PROCESS_STATE_TOP_SLEEPING_VALUE,

                    ProcessStateEnum.PROCESS_STATE_UNKNOWN_VALUE,
                    ProcessStateEnum.PROCESS_STATE_NONEXISTENT_VALUE
            ));

    private static final Set<Integer> ALL_STATES = Stream.of(MISC_STATES, CACHED_STATES, BG_STATES)
            .flatMap(s -> s.stream()).collect(Collectors.toSet());

    private static final Function<Atom, Integer> PROC_STATE_FUNCTION =
            atom -> atom.getUidProcessStateChanged().getState().getNumber();

    private static final int PROC_STATE_ATOM_TAG = Atom.UID_PROCESS_STATE_CHANGED_FIELD_NUMBER;

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

    public void testForegroundService() throws Exception {
        Set<Integer> onStates = new HashSet<>(Arrays.asList(
                ProcessStateEnum.PROCESS_STATE_FOREGROUND_SERVICE_VALUE));
        Set<Integer> offStates = complement(onStates);

        List<Set<Integer>> stateSet = Arrays.asList(onStates, offStates); // state sets, in order
        ConfigUtils.uploadConfigForPushedAtomWithUid(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                PROC_STATE_ATOM_TAG, /*useUidAttributionChain=*/false);

        executeForegroundService(getDevice());
        final int waitTime = SLEEP_OF_FOREGROUND_SERVICE;
        RunUtil.getDefault().sleep(waitTime + STATSD_REPORT_WAIT_TIME_MS + EXTRA_WAIT_TIME_MS);

        List<EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());
        AtomTestUtils.popUntilFind(data, onStates,
                PROC_STATE_FUNCTION); // clear out initial proc states.
        AtomTestUtils.assertStatesOccurredInOrder(stateSet, data, waitTime, PROC_STATE_FUNCTION);
    }

    public void testForeground() throws Exception {
        Set<Integer> onStates = new HashSet<>(Arrays.asList(
                ProcessStateEnum.PROCESS_STATE_IMPORTANT_FOREGROUND_VALUE));
        // There are no offStates, since the app remains in foreground until killed.

        List<Set<Integer>> stateSet = Arrays.asList(onStates); // state sets, in order
        ConfigUtils.uploadConfigForPushedAtomWithUid(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                PROC_STATE_ATOM_TAG, /*useUidAttributionChain=*/false);

        executeForegroundActivity(getDevice(), ACTION_SHOW_APPLICATION_OVERLAY);
        final int waitTime = EXTRA_WAIT_TIME_MS + 5_000; // Overlay may need to sit there a while.
        RunUtil.getDefault().sleep(waitTime + STATSD_REPORT_WAIT_TIME_MS);

        List<EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());
        AtomTestUtils.popUntilFind(data, onStates,
                PROC_STATE_FUNCTION); // clear out initial proc states.
        AtomTestUtils.assertStatesOccurredInOrder(stateSet, data, 0, PROC_STATE_FUNCTION);
    }

    public void testBackground() throws Exception {
        Set<Integer> onStates = BG_STATES;
        Set<Integer> offStates = complement(onStates);

        List<Set<Integer>> stateSet = Arrays.asList(onStates, offStates); // state sets, in order
        ConfigUtils.uploadConfigForPushedAtomWithUid(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                PROC_STATE_ATOM_TAG, /*useUidAttributionChain=*/false);

        DeviceUtils.executeBackgroundService(getDevice(), ACTION_BACKGROUND_SLEEP);
        final int waitTime = SLEEP_OF_ACTION_BACKGROUND_SLEEP;
        RunUtil.getDefault().sleep(waitTime + STATSD_REPORT_WAIT_TIME_MS + EXTRA_WAIT_TIME_MS);

        List<EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());
        AtomTestUtils.popUntilFind(data, onStates,
                PROC_STATE_FUNCTION); // clear out initial proc states.
        AtomTestUtils.assertStatesOccurredInOrder(stateSet, data, waitTime, PROC_STATE_FUNCTION);
    }

    public void testTop() throws Exception {
        Set<Integer> onStates = new HashSet<>(Arrays.asList(
                ProcessStateEnum.PROCESS_STATE_TOP_VALUE));
        Set<Integer> offStates = complement(onStates);

        List<Set<Integer>> stateSet = Arrays.asList(onStates, offStates); // state sets, in order
        ConfigUtils.uploadConfigForPushedAtomWithUid(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                PROC_STATE_ATOM_TAG, /*useUidAttributionChain=*/false);

        executeForegroundActivity(getDevice(), ACTION_SLEEP_WHILE_TOP);
        final int waitTime = SLEEP_OF_ACTION_SLEEP_WHILE_TOP;
        RunUtil.getDefault().sleep(waitTime + STATSD_REPORT_WAIT_TIME_MS + EXTRA_WAIT_TIME_MS);

        List<EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());
        AtomTestUtils.popUntilFind(data, onStates,
                PROC_STATE_FUNCTION); // clear out initial proc states.
        AtomTestUtils.assertStatesOccurredInOrder(stateSet, data, waitTime, PROC_STATE_FUNCTION);
    }

    public void testTopSleeping() throws Exception {
        if (DeviceUtils.hasFeature(getDevice(), FEATURE_WATCH)) return;
        Set<Integer> onStates = new HashSet<>(Arrays.asList(
                ProcessStateEnum.PROCESS_STATE_TOP_SLEEPING_VALUE));
        Set<Integer> offStates = complement(onStates);

        List<Set<Integer>> stateSet = Arrays.asList(onStates, offStates); // state sets, in order
        ConfigUtils.uploadConfigForPushedAtomWithUid(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                PROC_STATE_ATOM_TAG, /*useUidAttributionChain=*/false);

        DeviceUtils.turnScreenOn(getDevice());
        RunUtil.getDefault().sleep(WAIT_TIME_FOR_SCREEN_MS);

        executeForegroundActivity(getDevice(), ACTION_SLEEP_WHILE_TOP);
        RunUtil.getDefault().sleep(WAIT_TIME_FOR_SCREEN_MS);
        DeviceUtils.turnScreenOff(getDevice());
        final int waitTime = SLEEP_OF_ACTION_SLEEP_WHILE_TOP + EXTRA_WAIT_TIME_MS;
        RunUtil.getDefault().sleep(waitTime + STATSD_REPORT_WAIT_TIME_MS);

        List<EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());
        AtomTestUtils.popUntilFind(data,
                new HashSet<>(Arrays.asList(ProcessStateEnum.PROCESS_STATE_TOP_VALUE)),
                PROC_STATE_FUNCTION); // clear out anything prior to it entering TOP.
        AtomTestUtils.popUntilFind(data, onStates, PROC_STATE_FUNCTION); // clear out TOP itself.
        // reset screen back on
        DeviceUtils.turnScreenOn(getDevice());
        // Don't check the wait time, since it's up to the system how long top sleeping persists.
        AtomTestUtils.assertStatesOccurredInOrder(stateSet, data, 0, PROC_STATE_FUNCTION);
    }

    public void testCached() throws Exception {
        Set<Integer> onStates = CACHED_STATES;
        Set<Integer> offStates = complement(onStates);

        ConfigUtils.uploadConfigForPushedAtomWithUid(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                PROC_STATE_ATOM_TAG, /*useUidAttributionChain=*/false);

        // The schedule is as follows
        // #1. The system may do anything it wants, such as moving the app into a cache state.
        // #2. We move the app into the background.
        // #3. The background process ends, so the app definitely moves to a cache state
        //          (this is the ultimate goal of the test).
        // #4. We start a foreground activity, moving the app out of cache.

        // Start extremely short-lived activity, so app goes into cache state (#1 - #3 above).
        DeviceUtils.executeBackgroundService(getDevice(), ACTION_END_IMMEDIATELY);
        final int cacheTime = 2_000; // process should be in cached state for up to this long
        RunUtil.getDefault().sleep(cacheTime);
        // Now forcibly bring the app out of cache (#4 above).
        executeForegroundActivity(getDevice(), ACTION_SHOW_APPLICATION_OVERLAY);
        // Now check the data *before* the app enters cache again (to avoid another cache event).
        RunUtil.getDefault().sleep(cacheTime);  //the app out of cache need some time
        List<EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());
        // First, clear out any incidental cached states of step #1, prior to step #2.
        AtomTestUtils.popUntilFind(data, BG_STATES, PROC_STATE_FUNCTION);
        CLog.d("After clearing cached states:\n");
        ReportUtils.printEventMetricDataList(data);
        // Now clear out the bg state from step #2 (since we are interested in the cache after it).
        AtomTestUtils.popUntilFind(data, onStates, PROC_STATE_FUNCTION);
        CLog.d("After clearing bg states:\n");
        ReportUtils.printEventMetricDataList(data);

        // The first element in data is a cached state (unless empty) and there should be
        // at least one other element with non-cached state.
        assertWithMessage("Cached state is not present").that(data).isNotEmpty();

        List<Integer> states = data.stream()
                                       .map(EventMetricData::getAtom)
                                       .map(PROC_STATE_FUNCTION)
                                       .collect(Collectors.toList());
        assertWithMessage("Non-cached state is not present").that(states).containsAnyIn(offStates);
    }

    public void testValidityOfStates() throws Exception {
        assertWithMessage("UNKNOWN_TO_PROTO should not be a valid state")
                .that(ALL_STATES).doesNotContain(
                ProcessStateEnum.PROCESS_STATE_UNKNOWN_TO_PROTO_VALUE);
    }

    /** Returns the a set containing elements of a that are not elements of b. */
    private Set<Integer> difference(Set<Integer> a, Set<Integer> b) {
        Set<Integer> result = new HashSet<Integer>(a);
        result.removeAll(b);
        return result;
    }

    /** Returns the set of all states that are not in set. */
    private Set<Integer> complement(Set<Integer> set) {
        return difference(ALL_STATES, set);
    }

    /**
     * Runs an activity (in the foreground) to perform the given action.
     *
     * @param actionValue the action code constants indicating the desired action to perform.
     */
    private static void executeForegroundActivity(ITestDevice device, String actionValue)
            throws Exception {
        device.executeShellCommand(String.format(
                "am start -n '%s' -e %s %s",
                DEVICE_SIDE_FG_ACTIVITY_COMPONENT,
                "action", actionValue));
    }

    /**
     * Runs a simple foreground service.
     */
    private static void executeForegroundService(ITestDevice device) throws Exception {
        executeForegroundActivity(device, ACTION_END_IMMEDIATELY);
        device.executeShellCommand(String.format(
                "am startservice -n '%s'", DEVICE_SIDE_FG_SERVICE_COMPONENT));
    }
}
