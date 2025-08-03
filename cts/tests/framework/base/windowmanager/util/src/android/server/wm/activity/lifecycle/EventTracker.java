/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.server.wm.activity.lifecycle;

import static android.server.wm.BuildUtils.HW_TIMEOUT_MULTIPLIER;
import static android.server.wm.StateLogger.logAlways;

import static org.junit.Assert.fail;

import android.app.Activity;
import android.util.Pair;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Gets notified about activity lifecycle updates and provides blocking mechanism to wait until
 * expected activity states are reached.
 */
public class EventTracker implements EventLog.EventTrackerCallback {
    private static final long TIMEOUT = 5 * 1000L * HW_TIMEOUT_MULTIPLIER;
    private EventLog mEventLog;

    public EventTracker(EventLog eventLog) {
        mEventLog = eventLog;
        mEventLog.setEventTracker(this);
    }

    void waitAndAssertActivityStates(
            Pair<Class<? extends Activity>, String>[] activityCallbacks) {
        final boolean waitResult = waitForConditionWithTimeout(
                () -> pendingCallbacks(activityCallbacks).isEmpty());

        if (!waitResult) {
            fail("Expected lifecycle states not achieved: " + pendingCallbacks(activityCallbacks));
        }
    }

    /**
     * Blocking call that will wait and verify that the activity transition settles with the
     * expected state.
     */
    public void waitAndAssertActivityCurrentState(Class<? extends Activity> activityClass,
            String expectedState) {
        final boolean waitResult = waitForActivityCurrentState(activityClass, expectedState);
        if (!waitResult) {
            fail("Lifecycle state did not settle with the expected current state of "
                    + expectedState + " : " + mEventLog.getActivityLog(activityClass));
        }
    }

    /**
     * Blocking call that will wait for the activity transition settles with the
     * expected state.
     */
    public boolean waitForActivityCurrentState(Class<? extends Activity> activityClass,
            String expectedState) {
        return waitForConditionWithTimeout(() -> {
            List<String> activityLog = mEventLog.getActivityLog(activityClass);
            if (activityLog.isEmpty()) return false;
            String currentState = activityLog.get(activityLog.size() - 1);
            return expectedState.equals(currentState);
        });
    }

    /**
     * Waits for a specific sequence of events to happen.
     * When there is a possibility of some lifecycle state happening more than once in a sequence,
     * it is better to use this method instead of {@link #waitAndAssertActivityStates(Pair[])}.
     * Otherwise we might stop tracking too early.
     */
    public void waitForActivityTransitions(Class<? extends Activity> activityClass,
            List<String> expectedTransitions) {
        waitForConditionWithTimeout(
                () -> mEventLog.getActivityLog(activityClass).equals(expectedTransitions));
    }

    @Override
    public synchronized void onEventObserved() {
        notify();
    }

    /** Get a list of activity states that were not reached yet. */
    private List<Pair<Class<? extends Activity>, String>> pendingCallbacks(
            Pair<Class<? extends Activity>, String>[] activityCallbacks) {
        final List<Pair<Class<? extends Activity>, String>> notReachedActivityCallbacks =
                new ArrayList<>();

        for (Pair<Class<? extends Activity>, String> callbackPair : activityCallbacks) {
            final Class<? extends Activity> activityClass = callbackPair.first;
            final List<String> transitionList =
                    mEventLog.getActivityLog(activityClass);
            if (transitionList.isEmpty()
                    || !transitionList.get(transitionList.size() - 1).equals(callbackPair.second)) {
                // The activity either hasn't got any state transitions yet or the current state is
                // not the one we expect.
                notReachedActivityCallbacks.add(callbackPair);
            }
        }
        return notReachedActivityCallbacks;
    }

    public boolean waitForConditionWithTimeout(BooleanSupplier waitCondition) {
        return waitForConditionWithTimeout(waitCondition, TIMEOUT);
    }

    /** Blocking call to wait for a condition to become true with max timeout. */
    private synchronized boolean waitForConditionWithTimeout(BooleanSupplier waitCondition,
            long timeoutMs) {
        final long timeout = System.currentTimeMillis() + timeoutMs;
        while (!waitCondition.getAsBoolean()) {
            final long waitMs = timeout - System.currentTimeMillis();
            if (waitMs <= 0) {
                // Timeout expired.
                logAlways("waitForConditionWithTimeout expired.");
                return false;
            }
            try {
                wait(timeoutMs);
            } catch (InterruptedException e) {
                // Weird, let's retry.
            }
        }
        return true;
    }
}
