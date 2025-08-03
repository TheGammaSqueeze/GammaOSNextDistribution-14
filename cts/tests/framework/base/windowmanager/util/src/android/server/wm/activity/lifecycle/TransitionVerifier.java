/*
 * Copyright (C) 2023 The Android Open Source Project
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

import static android.server.wm.StateLogger.log;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.app.Activity;
import android.util.Pair;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Util class that verifies correct event and state transition sequences. */
public class TransitionVerifier {

    private static final Class CALLBACK_TRACKING_CLASS = CallbackTrackingActivity.class;
    private static final Class CONFIG_CHANGE_HANDLING_CLASS =
            LifecycleConfigChangeHandlingActivity.class;

    static void assertLaunchSequence(
            Class<? extends Activity> activityClass,
            EventLog eventLog,
            String... expectedSubsequentEvents) {
        final List<String> observedTransitions = eventLog.getActivityLog(activityClass);
        log("Observed sequence: " + observedTransitions);
        final String errorMessage = errorDuringTransition(activityClass, "launch");

        final List<String> launchSequence = getLaunchSequence(activityClass);
        final List<String> expectedTransitions;
        expectedTransitions =
                new ArrayList<>(launchSequence.size() + expectedSubsequentEvents.length);
        expectedTransitions.addAll(launchSequence);
        expectedTransitions.addAll(Arrays.asList(expectedSubsequentEvents));
        assertEquals(errorMessage, expectedTransitions, observedTransitions);
    }

    public static List<String> getLaunchSequence(Class<? extends Activity> activityClass) {
        return CALLBACK_TRACKING_CLASS.isAssignableFrom(activityClass)
                ? Arrays.asList(
                        LifecycleConstants.ON_CREATE,
                        LifecycleConstants.ON_START,
                        LifecycleConstants.ON_POST_CREATE,
                        LifecycleConstants.ON_RESUME,
                        LifecycleConstants.ON_TOP_POSITION_GAINED)
                : Arrays.asList(
                        LifecycleConstants.ON_CREATE,
                        LifecycleConstants.ON_START,
                        LifecycleConstants.ON_RESUME);
    }

    static List<String> getLaunchAndDestroySequence(Class<? extends Activity> activityClass) {
        final List<String> expectedTransitions = new ArrayList<>();
        expectedTransitions.addAll(getLaunchSequence(activityClass));
        expectedTransitions.addAll(getResumeToDestroySequence(activityClass));
        return expectedTransitions;
    }

    static void assertLaunchSequence(
            Class<? extends Activity> launchingActivity,
            Class<? extends Activity> existingActivity,
            EventLog eventLog,
            boolean launchingIsTranslucent) {
        final boolean includingCallbacks;
        if (CALLBACK_TRACKING_CLASS.isAssignableFrom(launchingActivity)
                && CALLBACK_TRACKING_CLASS.isAssignableFrom(existingActivity)) {
            includingCallbacks = true;
        } else if (!CALLBACK_TRACKING_CLASS.isAssignableFrom(launchingActivity)
                && !CALLBACK_TRACKING_CLASS.isAssignableFrom(existingActivity)) {
            includingCallbacks = false;
        } else {
            throw new IllegalArgumentException(
                    "Mixed types of callback tracking not supported. "
                            + "Both activities must support or not support callback tracking "
                            + "simultaneously");
        }

        final List<Pair<String, String>> observedTransitions = eventLog.getLog();
        log("Observed sequence: " + observedTransitions);
        final String errorMessage = errorDuringTransition(launchingActivity, "launch");

        final List<Pair<String, String>> expectedTransitions = new ArrayList<>();
        // First top position will be lost
        if (includingCallbacks) {
            expectedTransitions.add(
                    transition(existingActivity, LifecycleConstants.ON_TOP_POSITION_LOST));
        }
        // Next the existing activity is paused and the next one is launched
        expectedTransitions.add(transition(existingActivity, LifecycleConstants.ON_PAUSE));
        expectedTransitions.add(transition(launchingActivity, LifecycleConstants.ON_CREATE));
        expectedTransitions.add(transition(launchingActivity, LifecycleConstants.ON_START));
        if (includingCallbacks) {
            expectedTransitions.add(
                    transition(launchingActivity, LifecycleConstants.ON_POST_CREATE));
        }
        expectedTransitions.add(transition(launchingActivity, LifecycleConstants.ON_RESUME));
        if (includingCallbacks) {
            expectedTransitions.add(
                    transition(launchingActivity, LifecycleConstants.ON_TOP_POSITION_GAINED));
        }
        if (!launchingIsTranslucent) {
            expectedTransitions.add(transition(existingActivity, LifecycleConstants.ON_STOP));
        }

        assertEquals(errorMessage, expectedTransitions, observedTransitions);
    }

    static void assertLaunchAndStopSequence(
            Class<? extends Activity> activityClass, EventLog eventLog) {
        assertLaunchAndStopSequence(activityClass, eventLog, false /* onTop */);
    }

    static void assertLaunchAndStopSequence(
            Class<? extends Activity> activityClass, EventLog eventLog, boolean onTop) {
        final List<String> observedTransitions = eventLog.getActivityLog(activityClass);
        log("Observed sequence: " + observedTransitions);
        final String errorMessage = errorDuringTransition(activityClass, "launch and stop");

        final boolean includeCallbacks = CALLBACK_TRACKING_CLASS.isAssignableFrom(activityClass);

        final List<String> expectedTransitions =
                new ArrayList<>(
                        Arrays.asList(LifecycleConstants.ON_CREATE, LifecycleConstants.ON_START));
        if (includeCallbacks) {
            expectedTransitions.add(LifecycleConstants.ON_POST_CREATE);
        }
        expectedTransitions.add(LifecycleConstants.ON_RESUME);
        if (includeCallbacks && onTop) {
            expectedTransitions.addAll(
                    Arrays.asList(
                            LifecycleConstants.ON_TOP_POSITION_GAINED,
                            LifecycleConstants.ON_TOP_POSITION_LOST));
        }
        expectedTransitions.addAll(
                Arrays.asList(LifecycleConstants.ON_PAUSE, LifecycleConstants.ON_STOP));
        assertEquals(errorMessage, expectedTransitions, observedTransitions);
    }

    static void assertLaunchAndPauseSequence(
            Class<? extends Activity> activityClass, EventLog eventLog) {
        final List<String> observedTransitions = eventLog.getActivityLog(activityClass);
        log("Observed sequence: " + observedTransitions);
        final String errorMessage = errorDuringTransition(activityClass, "launch and pause");

        final List<String> expectedTransitions =
                Arrays.asList(
                        LifecycleConstants.ON_CREATE,
                        LifecycleConstants.ON_START,
                        LifecycleConstants.ON_RESUME,
                        LifecycleConstants.ON_PAUSE);
        assertEquals(errorMessage, expectedTransitions, observedTransitions);
    }

    static void assertRestartSequence(Class<? extends Activity> activityClass, EventLog eventLog) {
        final List<String> observedTransitions = eventLog.getActivityLog(activityClass);
        log("Observed sequence: " + observedTransitions);
        final String errorMessage = errorDuringTransition(activityClass, "restart");

        final List<String> expectedTransitions =
                Arrays.asList(LifecycleConstants.ON_RESTART, LifecycleConstants.ON_START);
        assertEquals(errorMessage, expectedTransitions, observedTransitions);
    }

    static void assertRestartAndResumeSequence(
            Class<? extends Activity> activityClass, EventLog eventLog) {
        final List<String> observedTransitions = eventLog.getActivityLog(activityClass);
        log("Observed sequence: " + observedTransitions);
        final String errorMessage = errorDuringTransition(activityClass, "restart and pause");

        final List<String> expectedTransitions =
                Arrays.asList(
                        LifecycleConstants.ON_RESTART,
                        LifecycleConstants.ON_START,
                        LifecycleConstants.ON_RESUME);
        assertEquals(errorMessage, expectedTransitions, observedTransitions);
    }

    /**
     * TODO(b/192274045): In Automotive, we tolerate superfluous lifecycle events between the first
     * lifecycle events and the last one until any discrepancy between ActivityManager and Keyguard
     * state is resolved.
     */
    static void assertRestartAndResumeSubSequence(
            Class<? extends Activity> activityClass, EventLog eventLog) {
        final List<String> observedTransitions = eventLog.getActivityLog(activityClass);
        log("Observed sequence: " + observedTransitions);

        final List<Pair<String, String>> expectedTransitions =
                Arrays.asList(
                        transition(activityClass, LifecycleConstants.ON_RESTART),
                        transition(activityClass, LifecycleConstants.ON_START),
                        transition(activityClass, LifecycleConstants.ON_RESUME));

        assertOrder(eventLog, expectedTransitions, "restart and resume");
    }

    static void assertRecreateAndResumeSequence(
            Class<? extends Activity> activityClass, EventLog eventLog) {
        final List<String> observedTransitions = eventLog.getActivityLog(activityClass);
        log("Observed sequence: " + observedTransitions);
        final String errorMessage = errorDuringTransition(activityClass, "recreateA  and pause");

        final List<String> expectedTransitions =
                Arrays.asList(
                        LifecycleConstants.ON_DESTROY,
                        LifecycleConstants.ON_CREATE,
                        LifecycleConstants.ON_START,
                        LifecycleConstants.ON_RESUME);
        assertEquals(errorMessage, expectedTransitions, observedTransitions);
    }

    static void assertLaunchAndDestroySequence(
            Class<? extends Activity> activityClass, EventLog eventLog) {
        final List<String> observedTransitions = eventLog.getActivityLog(activityClass);
        log("Observed sequence: " + observedTransitions);
        final String errorMessage = errorDuringTransition(activityClass, "launch and destroy");

        final List<String> expectedTransitions =
                Arrays.asList(
                        LifecycleConstants.ON_CREATE,
                        LifecycleConstants.ON_START,
                        LifecycleConstants.ON_RESUME,
                        LifecycleConstants.ON_PAUSE,
                        LifecycleConstants.ON_STOP,
                        LifecycleConstants.ON_DESTROY);
        assertEquals(errorMessage, expectedTransitions, observedTransitions);
    }

    static void assertResumeToDestroySequence(
            Class<? extends Activity> activityClass, EventLog eventLog) {
        final List<String> observedTransitions = eventLog.getActivityLog(activityClass);
        log("Observed sequence: " + observedTransitions);
        final String errorMessage = errorDuringTransition(activityClass, "launch and destroy");

        final List<String> expectedTransitions = getResumeToDestroySequence(activityClass);
        assertEquals(errorMessage, expectedTransitions, observedTransitions);
    }

    static List<String> getResumeToDestroySequence(Class<? extends Activity> activityClass) {
        return CALLBACK_TRACKING_CLASS.isAssignableFrom(activityClass)
                ? Arrays.asList(
                        LifecycleConstants.ON_TOP_POSITION_LOST,
                        LifecycleConstants.ON_PAUSE,
                        LifecycleConstants.ON_STOP,
                        LifecycleConstants.ON_DESTROY)
                : Arrays.asList(
                        LifecycleConstants.ON_PAUSE,
                        LifecycleConstants.ON_STOP,
                        LifecycleConstants.ON_DESTROY);
    }

    static void assertResumeToStopSequence(
            Class<? extends Activity> activityClass, EventLog eventLog) {
        final List<String> observedTransitions = eventLog.getActivityLog(activityClass);
        log("Observed sequence: " + observedTransitions);
        final String errorMessage = errorDuringTransition(activityClass, "resumed to stopped");
        final boolean includeCallbacks = CALLBACK_TRACKING_CLASS.isAssignableFrom(activityClass);

        final List<String> expectedTransitions = new ArrayList<>();
        if (includeCallbacks) {
            expectedTransitions.add(LifecycleConstants.ON_TOP_POSITION_LOST);
        }
        expectedTransitions.add(LifecycleConstants.ON_PAUSE);
        expectedTransitions.add(LifecycleConstants.ON_STOP);

        assertEquals(errorMessage, expectedTransitions, observedTransitions);
    }

    static void assertStopToResumeSequence(
            Class<? extends Activity> activityClass, EventLog eventLog) {
        final List<String> observedTransitions = eventLog.getActivityLog(activityClass);
        log("Observed sequence: " + observedTransitions);
        final String errorMessage = errorDuringTransition(activityClass, "stopped to resumed");
        final boolean includeCallbacks = CALLBACK_TRACKING_CLASS.isAssignableFrom(activityClass);

        final List<String> expectedTransitions =
                new ArrayList<>(
                        Arrays.asList(
                                LifecycleConstants.ON_RESTART,
                                LifecycleConstants.ON_START,
                                LifecycleConstants.ON_RESUME));
        if (includeCallbacks) {
            expectedTransitions.add(LifecycleConstants.ON_TOP_POSITION_GAINED);
        }

        assertEquals(errorMessage, expectedTransitions, observedTransitions);
    }

    /**
     * TODO(b/192274045): In Automotive, we tolerate superfluous lifecycle events between the first
     * lifecycle events and the last one until any discrepancy between ActivityManager and Keyguard
     * state is resolved.
     */
    static void assertStopToResumeSubSequence(
            Class<? extends Activity> activityClass, EventLog eventLog) {
        final List<String> observedTransitions = eventLog.getActivityLog(activityClass);
        log("Observed sequence: " + observedTransitions);
        final boolean includeCallbacks = CALLBACK_TRACKING_CLASS.isAssignableFrom(activityClass);

        final List<Pair<String, String>> expectedTransitions =
                new ArrayList<>(
                        Arrays.asList(
                                transition(activityClass, LifecycleConstants.ON_RESTART),
                                transition(activityClass, LifecycleConstants.ON_START),
                                transition(activityClass, LifecycleConstants.ON_RESUME)));
        if (includeCallbacks) {
            expectedTransitions.add(
                    transition(activityClass, LifecycleConstants.ON_TOP_POSITION_GAINED));
        }

        assertOrder(eventLog, expectedTransitions, "stop and resume");
    }

    static void assertRelaunchSequence(
            Class<? extends Activity> activityClass, EventLog eventLog, String startState) {
        final List<String> expectedTransitions = getRelaunchSequence(startState);
        assertSequence(activityClass, eventLog, expectedTransitions, "relaunch");
    }

    static List<String> getRelaunchSequence(String startState) {
        final List<String> expectedTransitions;
        if (startState.equals(LifecycleConstants.ON_PAUSE)) {
            expectedTransitions =
                    Arrays.asList(
                            LifecycleConstants.ON_STOP,
                            LifecycleConstants.ON_DESTROY,
                            LifecycleConstants.ON_CREATE,
                            LifecycleConstants.ON_START,
                            LifecycleConstants.ON_RESUME,
                            LifecycleConstants.ON_PAUSE);
        } else if (startState.equals(LifecycleConstants.ON_STOP)) {
            expectedTransitions =
                    Arrays.asList(
                            LifecycleConstants.ON_DESTROY,
                            LifecycleConstants.ON_CREATE,
                            LifecycleConstants.ON_START,
                            LifecycleConstants.ON_RESUME,
                            LifecycleConstants.ON_PAUSE,
                            LifecycleConstants.ON_STOP);
        } else if (startState.equals(LifecycleConstants.ON_RESUME)) {
            expectedTransitions =
                    Arrays.asList(
                            LifecycleConstants.ON_PAUSE,
                            LifecycleConstants.ON_STOP,
                            LifecycleConstants.ON_DESTROY,
                            LifecycleConstants.ON_CREATE,
                            LifecycleConstants.ON_START,
                            LifecycleConstants.ON_RESUME);
        } else if (startState.equals(LifecycleConstants.ON_TOP_POSITION_GAINED)) {
            // Looks like we're tracking the callbacks here
            expectedTransitions =
                    Arrays.asList(
                            LifecycleConstants.ON_TOP_POSITION_LOST,
                            LifecycleConstants.ON_PAUSE,
                            LifecycleConstants.ON_STOP,
                            LifecycleConstants.ON_DESTROY,
                            LifecycleConstants.ON_CREATE,
                            LifecycleConstants.ON_START,
                            LifecycleConstants.ON_POST_CREATE,
                            LifecycleConstants.ON_RESUME,
                            LifecycleConstants.ON_TOP_POSITION_GAINED);
        } else {
            throw new IllegalArgumentException("Start state not supported: " + startState);
        }
        return expectedTransitions;
    }

    static List<String> getSplitScreenTransitionSequence(Class<? extends Activity> activityClass) {
        // Minimized-dock is not a policy requirement and but SysUI-specific concept, so we here
        // don't expect a trailing ON_PAUSE.
        return CALLBACK_TRACKING_CLASS.isAssignableFrom(activityClass)
                ? CONFIG_CHANGE_HANDLING_CLASS.isAssignableFrom(activityClass)
                        ? Arrays.asList(
                                LifecycleConstants.ON_MULTI_WINDOW_MODE_CHANGED,
                                LifecycleConstants.ON_TOP_POSITION_LOST)
                        : Arrays.asList(
                                LifecycleConstants.ON_TOP_POSITION_LOST,
                                LifecycleConstants.ON_PAUSE,
                                LifecycleConstants.ON_STOP,
                                LifecycleConstants.ON_DESTROY,
                                LifecycleConstants.ON_CREATE,
                                LifecycleConstants.ON_START,
                                LifecycleConstants.ON_POST_CREATE,
                                LifecycleConstants.ON_RESUME,
                                LifecycleConstants.ON_TOP_POSITION_GAINED,
                                LifecycleConstants.ON_TOP_POSITION_LOST)
                : Arrays.asList(
                        LifecycleConstants.ON_PAUSE,
                        LifecycleConstants.ON_STOP,
                        LifecycleConstants.ON_DESTROY,
                        LifecycleConstants.ON_CREATE,
                        LifecycleConstants.ON_START,
                        LifecycleConstants.ON_RESUME);
    }

    // TODO(b/149338177): Remove this workaround once test passes with TestTaskOrganizer not to
    // depend on minimized dock feature which is not policy requirement, but SysUI-specific.
    /**
     * Returns the result of appending "leave from minimized dock" transitions to given transitions
     * to "consume" these activity callbacks.
     */
    static List<String> appendMinimizedDockTransitionTrail(List<String> transitions) {
        final List<String> newTransitions = new ArrayList<>(transitions);
        newTransitions.addAll(
                Arrays.asList(LifecycleConstants.ON_PAUSE, LifecycleConstants.ON_RESUME));

        return newTransitions;
    }

    static void assertSequence(
            Class<? extends Activity> activityClass,
            EventLog eventLog,
            List<String> expectedTransitions,
            String transition) {
        final List<String> observedTransitions = eventLog.getActivityLog(activityClass);
        log("Observed sequence: " + observedTransitions);
        final String errorMessage = errorDuringTransition(activityClass, transition);

        assertEquals(errorMessage, expectedTransitions, observedTransitions);
    }

    /**
     * Assert that the observed transitions of a particular activity happened in expected order.
     * There may be more observed transitions than in the expected array, only their order matters.
     *
     * <p>Use this method when there is no need to verify the entire sequence, only that some
     * transitions happened after another.
     */
    static void assertOrder(
            EventLog eventLog,
            Class<? extends Activity> activityClass,
            List<String> expectedTransitionsOrder,
            String transition) {
        List<Pair<String, String>> expectedTransitions = new ArrayList<>();
        for (String callback : expectedTransitionsOrder) {
            expectedTransitions.add(transition(activityClass, callback));
        }
        assertOrder(eventLog, expectedTransitions, transition);
    }

    /**
     * Assert that the observed transitions happened in expected order. There may be more observed
     * transitions than in the expected array, only their order matters.
     *
     * <p>Use this method when there is no need to verify the entire sequence, only that some
     * transitions happened after another.
     */
    public static void assertOrder(
            EventLog eventLog,
            List<Pair<String, String>> expectedTransitionsOrder,
            String transition) {
        String result = checkOrderAndReturnError(eventLog, expectedTransitionsOrder, transition);
        if (result != null) {
            fail(result);
        }
    }

    /**
     * Same as {@link #assertOrder(EventLog, List, String)}, but returns the String with error if it
     * occurs. Otherwise returns {@code null}
     */
    public static String checkOrderAndReturnError(
            EventLog eventLog,
            List<Pair<String, String>> expectedTransitionsOrder,
            String transition) {
        final List<Pair<String, String>> observedTransitions = eventLog.getLog();
        int nextObservedPosition = 0;
        for (Pair<String, String> expectedTransition : expectedTransitionsOrder) {
            while (nextObservedPosition < observedTransitions.size()
                    && !observedTransitions.get(nextObservedPosition).equals(expectedTransition)) {
                nextObservedPosition++;
            }
            if (nextObservedPosition == observedTransitions.size()) {
                return "Transition wasn't observed in the expected position: "
                        + expectedTransition
                        + " during transition: "
                        + transition;
            }
        }
        return null;
    }

    /** Assert that a transition was observer, no particular order. */
    public static void assertTransitionObserved(
            EventLog eventLog, Pair<String, String> expectedTransition, String transition) {
        assertTrue(
                "Transition " + expectedTransition + " must be observed during " + transition,
                eventLog.getLog().contains(expectedTransition));
    }

    /**
     * Same as {@link #checkOrderAndReturnError(EventLog, List, String)}, but returns {@code false}
     * if the order does not match. Otherwise returns {@code true}
     */
    public static boolean checkOrder(
            EventLog eventLog, List<Pair<String, String>> expectedTransitionsOrder) {
        String result = checkOrderAndReturnError(eventLog, expectedTransitionsOrder, null);
        return result == null;
    }

    /** Assert that a transition was not observer, no particular order. */
    static void assertTransitionNotObserved(
            EventLog eventLog, Pair<String, String> expectedTransition, String transition) {
        assertFalse(
                "Transition " + expectedTransition + " must not be observed during " + transition,
                eventLog.getLog().contains(expectedTransition));
    }

    static void assertEmptySequence(
            Class<? extends Activity> activityClass, EventLog eventLog, String transition) {
        assertSequence(activityClass, eventLog, new ArrayList<>(), transition);
    }

    /** Assert that a lifecycle sequence matches one of the possible variants. */
    static void assertSequenceMatchesOneOf(
            Class<? extends Activity> activityClass,
            EventLog eventLog,
            List<List<String>> expectedTransitions,
            String transition) {
        final List<String> observedTransitions = eventLog.getActivityLog(activityClass);
        log("Observed sequence: " + observedTransitions);
        final String errorMessage = errorDuringTransition(activityClass, transition);

        boolean oneOfExpectedSequencesObserved = false;
        for (List<String> transitionVariant : expectedTransitions) {
            if (transitionVariant.equals(observedTransitions)) {
                oneOfExpectedSequencesObserved = true;
                break;
            }
        }
        assertTrue(
                errorMessage
                        + "\nObserved transitions: "
                        + observedTransitions
                        + "\nExpected one of: "
                        + expectedTransitions,
                oneOfExpectedSequencesObserved);
    }

    /** Assert the entire sequence for all involved activities. */
    static void assertEntireSequence(
            List<Pair<String, String>> expectedTransitions, EventLog eventLog, String message) {
        final List<Pair<String, String>> observedTransitions = eventLog.getLog();
        assertEquals(message, expectedTransitions, observedTransitions);
    }

    public static Pair<String, String> transition(
            Class<? extends Activity> activityClass, String state) {
        return new Pair<>(activityClass.getCanonicalName(), state);
    }

    public static Pair<String, String> transition(String owner, String state) {
        return new Pair<>(owner, state);
    }

    private static String errorDuringTransition(
            Class<? extends Activity> activityClass, String transition) {
        return "Failed verification during moving activity: "
                + activityClass.getCanonicalName()
                + " through transition: "
                + transition;
    }
}
