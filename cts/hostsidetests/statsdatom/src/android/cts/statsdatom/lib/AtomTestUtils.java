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

package android.cts.statsdatom.lib;

import static com.google.common.truth.Truth.assertWithMessage;

import com.android.os.AtomsProto;
import com.android.os.AtomsProto.AppBreadcrumbReported;
import com.android.os.StatsLog;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil;
import com.android.utils.SparseIntArray;

import com.google.common.collect.Range;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * Contains miscellaneous helper functions that are used in statsd atom tests
 */
public final class AtomTestUtils {

    public static final int WAIT_TIME_SHORT = 500;
    public static final int WAIT_TIME_LONG = 1000;

    public static final long NS_PER_SEC = (long) 1E+9;

    /**
     * Sends an AppBreadcrumbReported atom to statsd. For GaugeMetrics that are added using
     * ConfigUtils, pulls are triggered when statsd receives an AppBreadcrumbReported atom, so
     * calling this function is necessary for gauge data to be acquired.
     *
     * @param device test device can be retrieved using getDevice()
     */
    public static void sendAppBreadcrumbReportedAtom(ITestDevice device)
            throws DeviceNotAvailableException {
        String cmd = String.format("cmd stats log-app-breadcrumb %d %d", /*label=*/1,
                AppBreadcrumbReported.State.START.ordinal());
        device.executeShellCommand(cmd);
    }

    /**
     * Asserts that each set of states in {@code stateSets} occurs in {@code data} without assuming
     * the order of occurrence.
     *
     * @param stateSets        A list of set of states, where each set represents an equivalent
     *                         state of the device for the purpose of CTS.
     * @param data             list of EventMetricData from statsd, produced by
     *                         getReportMetricListData()
     * @param getStateFromAtom expression that takes in an Atom and returns the state it contains
     */
    public static void assertStatesOccurred(List<Set<Integer>> stateSets,
            List<StatsLog.EventMetricData> data,
            Function<AtomsProto.Atom, Integer> getStateFromAtom) {
        // Sometimes, there are more events than there are states.
        // Eg: When the screen turns off, it may go into OFF and then DOZE immediately.
        final SparseIntArray dataStateCount = new SparseIntArray();
        final List<Integer> dataStates = new ArrayList<>(data.size());
        for (StatsLog.EventMetricData emd : data) {
            final int state = getStateFromAtom.apply(emd.getAtom());
            dataStates.add(state);
            dataStateCount.put(state, dataStateCount.get(state, 0) + 1);
        }

        assertWithMessage("Number of recorded states must be >= expected states"
                + " (dataStates=%s, stateSets=%s)", dataStates, stateSets)
                .that(data.size()).isAtLeast(stateSets.size());

        for (Set<Integer> states : stateSets) {
            for (int state : states) {
                final int count = dataStateCount.get(state);
                assertWithMessage("Remaining count of result state (%s)", state)
                        .that(count).isGreaterThan(0);
                dataStateCount.put(state, count - 1);
            }
        }
    }

    /**
     * Asserts that each set of states in stateSets occurs at least once in data.
     * Asserts that the states in data occur in the same order as the sets in stateSets.
     *
     * @param stateSets        A list of set of states, where each set represents an equivalent
     *                         state of the device for the purpose of CTS.
     * @param data             list of EventMetricData from statsd, produced by
     *                         getReportMetricListData()
     * @param wait             expected duration (in ms) between state changes; asserts that the
     *                         actual wait
     *                         time was wait/2 <= actual_wait <= 5*wait. Use 0 to ignore this
     *                         assertion.
     * @param getStateFromAtom expression that takes in an Atom and returns the state it contains
     */
    public static void assertStatesOccurredInOrder(List<Set<Integer>> stateSets,
            List<StatsLog.EventMetricData> data,
            int wait, Function<AtomsProto.Atom, Integer> getStateFromAtom) {
        // Sometimes, there are more events than there are states.
        // Eg: When the screen turns off, it may go into OFF and then DOZE immediately.
        assertWithMessage("Number of result states").that(data.size()).isAtLeast(stateSets.size());
        int stateSetIndex = 0; // Tracks which state set we expect the data to be in.
        for (int dataIndex = 0; dataIndex < data.size(); dataIndex++) {
            AtomsProto.Atom atom = data.get(dataIndex).getAtom();
            int state = getStateFromAtom.apply(atom);
            // If state is in the current state set, we do not assert anything.
            // If it is not, we expect to have transitioned to the next state set.
            if (stateSets.get(stateSetIndex).contains(state)) {
                // No need to assert anything. Just log it.
                LogUtil.CLog.i("The following atom at dataIndex=" + dataIndex + " is "
                        + "in stateSetIndex " + stateSetIndex + ":\n"
                        + data.get(dataIndex).getAtom().toString());
            } else {
                stateSetIndex += 1;
                LogUtil.CLog.i("Assert that the following atom at dataIndex=" + dataIndex + " is"
                        + " in stateSetIndex " + stateSetIndex + ":\n"
                        + data.get(dataIndex).getAtom().toString());
                assertWithMessage("Missed first state").that(dataIndex).isNotEqualTo(0);
                assertWithMessage("Too many states").that(stateSetIndex)
                        .isLessThan(stateSets.size());
                assertWithMessage(String.format("Is in wrong state (%d)", state))
                        .that(stateSets.get(stateSetIndex)).contains(state);
                if (wait > 0) {
                    assertTimeDiffBetween(data.get(dataIndex - 1), data.get(dataIndex),
                            wait / 2, wait * 5);
                }
            }
        }
        assertWithMessage("Too few states").that(stateSetIndex).isEqualTo(stateSets.size() - 1);
    }

    /**
     * Asserts that the two events are within the specified range of each other.
     *
     * @param d0        the event that should occur first
     * @param d1        the event that should occur second
     * @param minDiffMs d0 should precede d1 by at least this amount
     * @param maxDiffMs d0 should precede d1 by at most this amount
     */
    public static void assertTimeDiffBetween(
            StatsLog.EventMetricData d0, StatsLog.EventMetricData d1,
            int minDiffMs, int maxDiffMs) {
        long diffMs = (d1.getElapsedTimestampNanos() - d0.getElapsedTimestampNanos()) / 1_000_000;
        assertWithMessage("Illegal time difference")
                .that(diffMs).isIn(Range.closed((long) minDiffMs, (long) maxDiffMs));
    }

    // Checks that a timestamp has been truncated to be a multiple of 5 min
    public static void assertTimestampIsTruncated(long timestampNs) {
        long fiveMinutesInNs = NS_PER_SEC * 5 * 60;
        assertWithMessage("Timestamp is not truncated")
                .that(timestampNs % fiveMinutesInNs).isEqualTo(0);
    }

    /**
     * Removes all elements from data prior to the first occurrence of an element of state. After
     * this method is called, the first element of data (if non-empty) is guaranteed to be an
     * element in state.
     *
     * @param getStateFromAtom expression that takes in an Atom and returns the state it contains
     */
    public static void popUntilFind(List<StatsLog.EventMetricData> data, Set<Integer> state,
            Function<AtomsProto.Atom, Integer> getStateFromAtom) {
        int firstStateIdx;
        for (firstStateIdx = 0; firstStateIdx < data.size(); firstStateIdx++) {
            AtomsProto.Atom atom = data.get(firstStateIdx).getAtom();
            if (state.contains(getStateFromAtom.apply(atom))) {
                break;
            }
        }
        if (firstStateIdx == 0) {
            // First first element already is in state, so there's nothing to do.
            return;
        }
        data.subList(0, firstStateIdx).clear();
    }

    /**
     * Removes all elements from data after the last occurrence of an element of state. After this
     * method is called, the last element of data (if non-empty) is guaranteed to be an element in
     * state.
     *
     * @param getStateFromAtom expression that takes in an Atom and returns the state it contains
     */
    public static void popUntilFindFromEnd(List<StatsLog.EventMetricData> data, Set<Integer> state,
            Function<AtomsProto.Atom, Integer> getStateFromAtom) {
        int lastStateIdx;
        for (lastStateIdx = data.size() - 1; lastStateIdx >= 0; lastStateIdx--) {
            AtomsProto.Atom atom = data.get(lastStateIdx).getAtom();
            if (state.contains(getStateFromAtom.apply(atom))) {
                break;
            }
        }
        if (lastStateIdx == data.size() - 1) {
            // Last element already is in state, so there's nothing to do.
            return;
        }
        data.subList(lastStateIdx + 1, data.size()).clear();
    }

    private AtomTestUtils() {}
}
