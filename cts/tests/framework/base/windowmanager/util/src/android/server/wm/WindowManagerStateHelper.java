/*
 * Copyright (C) 2016 The Android Open Source Project
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
 * limitations under the License
 */

package android.server.wm;

import static android.app.ActivityTaskManager.INVALID_STACK_ID;
import static android.app.WindowConfiguration.ACTIVITY_TYPE_HOME;
import static android.app.WindowConfiguration.ACTIVITY_TYPE_UNDEFINED;
import static android.app.WindowConfiguration.WINDOWING_MODE_UNDEFINED;
import static android.server.wm.ComponentNameUtils.getActivityName;
import static android.server.wm.ComponentNameUtils.getWindowName;
import static android.server.wm.StateLogger.logAlways;
import static android.server.wm.StateLogger.logE;
import static android.view.Display.DEFAULT_DISPLAY;
import static android.view.WindowManager.LayoutParams.TYPE_INPUT_METHOD_DIALOG;
import static android.view.WindowManager.LayoutParams.TYPE_KEYGUARD_DIALOG;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.app.Instrumentation;
import android.content.ComponentName;
import android.graphics.Rect;
import android.server.wm.WindowManagerTestBase.FocusableActivity;
import android.util.SparseArray;
import android.view.InputEvent;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

/** Window Manager State helper class with assert and wait functions. */
public class WindowManagerStateHelper extends WindowManagerState {

    /**
     * Compute AM and WM state of device, check validity and bounds.
     * WM state will include only visible windows, stack and task bounds will be compared.
     *
     * @param componentNames array of activity names to wait for.
     */
    public void computeState(ComponentName... componentNames) {
        waitForValidState(Arrays.stream(componentNames)
                .map(WaitForValidActivityState::new)
                .toArray(WaitForValidActivityState[]::new));
    }

    /**
     * Compute AM and WM state of device, check validity and bounds.
     * WM state will include only visible windows, stack and task bounds will be compared.
     *
     * @param waitForActivitiesVisible array of activity names to wait for.
     */
    public void computeState(WaitForValidActivityState... waitForActivitiesVisible) {
        waitForValidState(waitForActivitiesVisible);
    }

    /**
     * Wait for the activities to appear and for valid state in AM and WM.
     *
     * @param activityNames name list of activities to wait for.
     */
    public void waitForValidState(ComponentName... activityNames) {
        waitForValidState(Arrays.stream(activityNames)
                .map(WaitForValidActivityState::new)
                .toArray(WaitForValidActivityState[]::new));

    }

    /**
     * Wait for the activities to appear in proper stacks and for valid state in AM and WM.
     * @param waitForActivitiesVisible  array of activity states to wait for.
     */
    public void waitForValidState(WaitForValidActivityState... waitForActivitiesVisible) {
        if (!Condition.waitFor("valid stacks and activities states", () -> {
            // TODO: Get state of AM and WM at the same time to avoid mismatches caused by
            // requesting dump in some intermediate state.
            computeState();
            return !(shouldWaitForValidityCheck()
                    || shouldWaitForValidStacks()
                    || shouldWaitForActivities(waitForActivitiesVisible)
                    || shouldWaitForWindows());
        })) {
            logE("***Waiting for states failed: " + Arrays.toString(waitForActivitiesVisible));
        }
    }

    public void waitForAllStoppedActivities() {
        if (!Condition.waitFor("all started activities have been removed", () -> {
            computeState();
            return !containsStartedActivities();
        })) {
            fail("All started activities have been removed");
        }
    }

    public void waitForAllNonHomeActivitiesToDestroyed() {
        Condition.waitFor("all non-home activities to be destroyed", () -> {
            computeState();
            for (Task rootTask : getRootTasks()) {
                final Activity activity = rootTask.getActivity(
                        (a) -> !a.state.equals(STATE_DESTROYED)
                                && a.getActivityType() != ACTIVITY_TYPE_HOME);
                if (activity != null) return false;
            }
            return true;
        });
    }

    /**
     * Compute AM and WM state of device, wait for the activity records to be added, and
     * wait for debugger window to show up.
     *
     * This should only be used when starting with -D (debugger) option, where we pop up the
     * waiting-for-debugger window, but real activity window won't show up since we're waiting
     * for debugger.
     */
    public void waitForDebuggerWindowVisible(ComponentName activityName) {
        Condition.waitFor("debugger window", () -> {
            computeState();
            return !shouldWaitForDebuggerWindow(activityName)
                    && !shouldWaitForActivityRecords(activityName);
        });
    }

    public void waitForHomeActivityVisible() {
        ComponentName homeActivity = getHomeActivityName();
        // Sometimes this function is called before we know what Home Activity is
        if (homeActivity == null) {
            logAlways("Computing state to determine Home Activity");
            computeState();
            homeActivity = getHomeActivityName();
        }
        assertNotNull("homeActivity should not be null", homeActivity);
        waitForValidState(homeActivity);
    }

    /** @return {@code true} if the recents is visible; {@code false} if timeout occurs. */
    public boolean waitForRecentsActivityVisible() {
        if (isHomeRecentsComponent()) {
            waitForHomeActivityVisible();
            return true;
        } else {
            return waitForWithAmState(WindowManagerState::isRecentsActivityVisible,
                    "recents activity to be visible");
        }
    }

    public void waitForDreamGone() {
        assertTrue("Dream must be gone",
                waitForWithAmState(state -> state.getDreamTask() == null, "DreamActivity gone"));
    }

    public static boolean isKeyguardShowingAndNotOccluded(WindowManagerState state) {
        return state.getKeyguardControllerState().keyguardShowing
                && state.getKeyguardServiceDelegateState().isKeyguardAwake()
                && !state.getKeyguardControllerState().mKeyguardGoingAway
                && !state.getKeyguardControllerState().aodShowing
                && !state.getKeyguardControllerState().isKeyguardOccluded(DEFAULT_DISPLAY);
    }

    public void waitForKeyguardShowingAndNotOccluded() {
        waitForWithAmState(WindowManagerStateHelper::isKeyguardShowingAndNotOccluded,
                "Keyguard showing");
    }

    public void waitForKeyguardShowingAndOccluded() {
        waitForWithAmState(state -> state.getKeyguardControllerState().keyguardShowing
                        && state.getKeyguardControllerState().isKeyguardOccluded(DEFAULT_DISPLAY),
                "Keyguard showing and occluded");
    }

    public void waitAndAssertWindowShown(int windowType, boolean show) {
        assertTrue(waitFor(state -> {
            Stream<WindowState> windows = getMatchingWindows(
                    ws -> ws.isSurfaceShown() == show && ws.getType() == windowType);
            return windows.findAny().isPresent();
        }, "wait for window surface " + (show ? "show" : "hide")));
    }

    public void waitForAodShowing() {
        waitForWithAmState(state -> state.getKeyguardControllerState().aodShowing, "AOD showing");
    }

    public void waitForKeyguardGone() {
        waitForWithAmState(state -> !state.getKeyguardControllerState().keyguardShowing,
                "Keyguard gone");
    }

    public void waitAndAssertKeyguardGone() {
        assertTrue("Keyguard must be gone",
                waitForWithAmState(
                        state -> !state.getKeyguardControllerState().keyguardShowing,
                        "Keyguard gone"));
    }

    /**
     * Wait for specific rotation for the default display.
     * @param rotation Surface#Rotation
     */
    public boolean waitForRotation(int rotation) {
        return waitForWithAmState(state -> state.getRotation() == rotation,
                "Rotation: " + rotation);
    }

    /**
     * Wait for specific orientation for the default display.
     * @param screenOrientation ActivityInfo#ScreenOrientation
     */
    public void waitForLastOrientation(int screenOrientation) {
        waitForWithAmState(state -> state.getLastOrientation() == screenOrientation,
                "LastOrientation: " + screenOrientation);
    }

    /**
     * @param message log message
     * @param screenOrientation ActivityInfo#ScreenOrientation
     */
    public void waitAndAssertLastOrientation(String message, int screenOrientation) {
        if (screenOrientation != getLastOrientation()) {
            waitForLastOrientation(screenOrientation);
        }
        assertEquals(message, screenOrientation, getLastOrientation());
    }

    /** Waits for the configuration orientation (landscape or portrait) of the default display.
     * @param configOrientation Configuration#Orientation
     */
    public void waitForDisplayOrientation(int configOrientation) {
        waitForWithAmState(state -> state.getDisplay(DEFAULT_DISPLAY)
                        .mFullConfiguration.orientation == configOrientation,
                "orientation of default display to be " + configOrientation);
    }

    /**
     * Wait for the configuration orientation of the Activity.
     * @param activityName activity
     * @param configOrientation Configuration#Orientation
     */
    public boolean waitForActivityOrientation(ComponentName activityName, int configOrientation) {
        return waitForWithAmState(amState -> {
            final Activity activity = amState.getActivity(activityName);
            return activity != null && activity.mFullConfiguration.orientation == configOrientation;
        }, "orientation of " + getActivityName(activityName) + " to be " + configOrientation);
    }

    public void waitForDisplayUnfrozen() {
        waitForWithAmState(state -> !state.isDisplayFrozen(), "Display unfrozen");
    }

    public boolean waitForActivityState(ComponentName activityName, String activityState) {
        return waitForWithAmState(state -> state.hasActivityState(activityName, activityState),
                "state of " + getActivityName(activityName) + " to be " + activityState);
    }

    public void waitAndAssertActivityState(ComponentName activityName, String activityState) {
        assertTrue(waitForActivityState(activityName, activityState));
    }

    public void waitForActivityRemoved(ComponentName activityName) {
        waitFor((amState) -> !amState.containsActivity(activityName)
                && !amState.containsWindow(getWindowName(activityName)),
                getActivityName(activityName) + " to be removed");
    }

    public void waitAndAssertActivityRemoved(ComponentName activityName) {
        waitForActivityRemoved(activityName);
        assertNotExist(activityName);
    }

    public void waitForFocusedStack(int windowingMode, int activityType) {
        waitForWithAmState(state ->
                        (activityType == ACTIVITY_TYPE_UNDEFINED
                                || state.getFocusedRootTaskActivityType() == activityType)
                        && (windowingMode == WINDOWING_MODE_UNDEFINED
                                || state.getFocusedRootTaskWindowingMode() == windowingMode),
                "focused stack");
    }

    public void waitForPendingActivityContain(ComponentName activity) {
        waitForWithAmState(state -> state.pendingActivityContain(activity),
                getActivityName(activity) + " in pending list");
    }

    public boolean waitForAppTransitionRunningOnDisplay(int displayId) {
        return waitForWithAmState(
                state -> WindowManagerState.APP_STATE_RUNNING.equals(
                        state.getDisplay(displayId).getAppTransitionState()),
                "app transition running on Display " + displayId);
    }

    public boolean waitForAppTransitionIdleOnDisplay(int displayId) {
        return waitForWithAmState(
                state -> WindowManagerState.APP_STATE_IDLE.equals(
                        state.getDisplay(displayId).getAppTransitionState()),
                "app transition idle on Display " + displayId);
    }

    public void waitAndAssertNavBarShownOnDisplay(int displayId) {
        waitAndAssertNavBarShownOnDisplay(displayId, 1 /* expectedNavBarCount */);
    }

    public void waitAndAssertNavBarShownOnDisplay(int displayId, int expectedNavBarCount) {
        assertTrue(waitForWithAmState(state -> {
            List<WindowState> navWindows = state
                    .getAndAssertNavBarWindowsOnDisplay(displayId, expectedNavBarCount);

            return navWindows != null;
        }, "navigation bar #" + displayId + " show"));
    }

    public void waitAndAssertKeyguardShownOnSecondaryDisplay(int displayId) {
        assertTrue("KeyguardDialog must be shown on secondary display " + displayId,
                waitForWithAmState(
                        state -> isKeyguardOnSecondaryDisplay(state, displayId),
                        "keyguard window to show"));
    }

    public void waitAndAssertKeyguardGoneOnSecondaryDisplay(int displayId) {
        assertTrue("KeyguardDialog must be gone on secondary display " + displayId,
                waitForWithAmState(
                        state -> !isKeyguardOnSecondaryDisplay(state, displayId),
                        "keyguard window to dismiss"));
    }

    public boolean waitForWindowSurfaceShown(String windowName, boolean shown) {
        final String message = windowName + "'s isWindowSurfaceShown to return " + shown;
        return Condition.waitFor(new Condition<>(message, () -> {
            computeState();
            return isWindowSurfaceShown(windowName) == shown;
        }).setRetryIntervalMs(200).setRetryLimit(20));
    }

    void waitForWindowSurfaceDisappeared(String windowName) {
        waitForWindowSurfaceShown(windowName, false);
    }

    public void waitAndAssertWindowSurfaceShown(String windowName, boolean shown) {
        assertTrue(waitForWindowSurfaceShown(windowName, shown));
    }

    /** A variant of waitForWithAmState with different parameter order for better Kotlin interop. */
    public boolean waitForWithAmState(String message, Predicate<WindowManagerState> waitCondition) {
        return waitForWithAmState(waitCondition, message);
    }

    public boolean waitForWithAmState(Predicate<WindowManagerState> waitCondition,
            String message) {
        return waitFor(waitCondition, message);
    }

    public void waitWindowingModeTopFocus(int windowingMode, boolean topFocus, String message) {
        waitForWithAmState(amState -> {
            final Task rootTask = amState.getStandardRootTaskByWindowingMode(windowingMode);
            return rootTask != null
                    && topFocus == (amState.getFocusedTaskId() == rootTask.getRootTaskId());
        }, message);
    }

    public boolean waitForFocusedActivity(final String msg, final ComponentName activityName) {
        final String activityComponentName = getActivityName(activityName);
        return waitFor(msg, wmState ->
                Objects.equals(activityComponentName, wmState.getFocusedActivity())
                && Objects.equals(activityComponentName, wmState.getFocusedApp()));
    }

    /** A variant of waitFor with different parameter order for better Kotlin interop. */
    public boolean waitFor(String message, Predicate<WindowManagerState> waitCondition) {
        return waitFor(waitCondition, message);
    }

    /** @return {@code true} if the wait is successful; {@code false} if timeout occurs. */
    public boolean waitFor(Predicate<WindowManagerState> waitCondition, String message) {
        return Condition.waitFor(message, () -> {
            computeState();
            return waitCondition.test(this);
        });
    }

    /** Waits for non-null result from {@code function} and returns it. */
    public <T> T waitForResult(String message, Function<WindowManagerState, T> function) {
        return waitForResult(message, function, Objects::nonNull);
    }

    public <T> T waitForResult(String message, Function<WindowManagerState, T> function,
            Predicate<T> validator) {
        return Condition.waitForResult(new Condition<T>(message)
                .setResultSupplier(() -> {
                    computeState();
                    return function.apply(this);
                })
                .setResultValidator(validator));
    }

    /**
     * @return true if should wait for valid stacks state.
     */
    private boolean shouldWaitForValidStacks() {
        final int stackCount = getRootTaskCount();
        if (stackCount == 0) {
            logAlways("***stackCount=" + stackCount);
            return true;
        }
        final int resumedActivitiesCount = getResumedActivitiesCount();
        if (!getKeyguardControllerState().keyguardShowing && resumedActivitiesCount < 1) {
            logAlways("***resumedActivitiesCount=" + resumedActivitiesCount);
            return true;
        }
        if (getFocusedActivity() == null) {
            logAlways("***focusedActivity=null");
            return true;
        }
        return false;
    }

    public void waitAndAssertAppFocus(String appPackageName, long waitTime) {
        final Condition<String> condition = new Condition<>(appPackageName + " to be focused");
        Condition.waitFor(condition.setResultSupplier(() -> {
            computeState();
            return getFocusedApp();
        }).setResultValidator(focusedAppName -> {
            return focusedAppName != null && appPackageName.equals(
                    ComponentName.unflattenFromString(focusedAppName).getPackageName());
        }).setOnFailure(focusedAppName -> {
            fail("Timed out waiting for focus on app "
                    + appPackageName + ", last was " + focusedAppName);
        }).setRetryIntervalMs(100).setRetryLimit((int) waitTime / 100));
    }

    /**
     * Waits until the given activity is ready for input, this is only needed when directly
     * injecting input on screen via
     * {@link android.hardware.input.InputManager#injectInputEvent(InputEvent, int)}.
     */
    public <T extends android.app.Activity> void waitUntilActivityReadyForInputInjection(T activity,
            String tag, String windowDumpErrMsg) throws InterruptedException {
        // If we requested an orientation change, just waiting for the window to be visible is not
        // sufficient. We should first wait for the transitions to stop, and the for app's UI thread
        // to process them before making sure the window is visible.
        CtsWindowInfoUtils.waitForStableWindowGeometry(5, TimeUnit.SECONDS);
        if (activity.getWindow() != null
                && !CtsWindowInfoUtils.waitForWindowOnTop(activity.getWindow())) {
            CtsWindowInfoUtils.dumpWindowsOnScreen(tag, windowDumpErrMsg);
            fail("Activity window did not become visible: " + activity);
        }
    }

    /**
     * @return true if should wait for some activities to become visible.
     */
    private boolean shouldWaitForActivities(WaitForValidActivityState... waitForActivitiesVisible) {
        if (waitForActivitiesVisible == null || waitForActivitiesVisible.length == 0) {
            return false;
        }
        // If the caller is interested in us waiting for some particular activity windows to be
        // visible before compute the state. Check for the visibility of those activity windows
        // and for placing them in correct stacks (if requested).
        boolean allActivityWindowsVisible = true;
        boolean tasksInCorrectStacks = true;
        for (final WaitForValidActivityState state : waitForActivitiesVisible) {
            final ComponentName activityName = state.activityName;
            final String windowName = state.windowName;
            final int stackId = state.stackId;
            final int windowingMode = state.windowingMode;
            final int activityType = state.activityType;

            final List<WindowState> matchingWindowStates =
                    getMatchingVisibleWindowState(windowName);
            boolean activityWindowVisible = !matchingWindowStates.isEmpty();
            if (!activityWindowVisible) {
                logAlways("Activity window not visible: " + windowName);
                allActivityWindowsVisible = false;
            } else if (activityName != null
                    && !isActivityVisible(activityName)) {
                logAlways("Activity not visible: " + getActivityName(activityName));
                allActivityWindowsVisible = false;
            } else {
                // Check if window is already the correct state requested by test.
                boolean windowInCorrectState = false;
                for (WindowState ws : matchingWindowStates) {
                    if (stackId != INVALID_STACK_ID && ws.getStackId() != stackId) {
                        continue;
                    }
                    if (!ws.isWindowingModeCompatible(windowingMode)) {
                        continue;
                    }
                    if (activityType != ACTIVITY_TYPE_UNDEFINED
                            && ws.getActivityType() != activityType) {
                        continue;
                    }
                    windowInCorrectState = true;
                    break;
                }

                if (!windowInCorrectState) {
                    logAlways("Window in incorrect stack: " + state);
                    tasksInCorrectStacks = false;
                }
            }
        }
        return !allActivityWindowsVisible || !tasksInCorrectStacks;
    }

    /**
     * @return true if should wait valid windows state.
     */
    private boolean shouldWaitForWindows() {
        if (getFrontWindow() == null) {
            logAlways("***frontWindow=null");
            return true;
        }
        if (getFocusedWindow() == null) {
            logAlways("***focusedWindow=null");
            return true;
        }
        if (getFocusedApp() == null) {
            logAlways("***focusedApp=null");
            return true;
        }

        return false;
    }

    private boolean shouldWaitForDebuggerWindow(ComponentName activityName) {
        List<WindowState> matchingWindowStates =
                getMatchingVisibleWindowState(activityName.getPackageName());
        for (WindowState ws : matchingWindowStates) {
            if (ws.isDebuggerWindow()) {
                return false;
            }
        }
        logAlways("Debugger window not available yet");
        return true;
    }

    private boolean shouldWaitForActivityRecords(ComponentName... activityNames) {
        // Check if the activity records we're looking for is already added.
        for (final ComponentName activityName : activityNames) {
            if (!isActivityVisible(activityName)) {
                logAlways("ActivityRecord " + getActivityName(activityName) + " not visible yet");
                return true;
            }
        }
        return false;
    }

    private boolean shouldWaitForValidityCheck() {
        try {
            assertValidity();
        } catch (Throwable t) {
            logAlways("Waiting for validity check: " + t.toString());
            return true;
        }
        return false;
    }

    public void assertValidity() {
        assertThat("Must have root task", getRootTaskCount(), greaterThan(0));
        // TODO: Update when keyguard will be shown on multiple displays
        if (!getKeyguardControllerState().keyguardShowing) {
            assertThat("There should be at least one resumed activity in the system.",
                    getResumedActivitiesCount(), greaterThanOrEqualTo(1));
        }
        assertNotNull("Must have focus activity.", getFocusedActivity());

        for (Task rootTask : getRootTasks()) {
            final int taskId = rootTask.mRootTaskId;
            for (Task task : rootTask.getTasks()) {
                assertEquals("Root task can only contain its own tasks", taskId,
                        task.mRootTaskId);
            }
        }

        assertNotNull("Must have front window.", getFrontWindow());
        assertNotNull("Must have focused window.", getFocusedWindow());
        assertNotNull("Must have app.", getFocusedApp());
    }

    public void assertContainsStack(String msg, int windowingMode, int activityType) {
        assertTrue(msg, containsRootTasks(windowingMode, activityType));
    }

    public void assertDoesNotContainStack(String msg, int windowingMode, int activityType) {
        assertFalse(msg, containsRootTasks(windowingMode, activityType));
    }

    public void assertFrontStack(String msg, int windowingMode, int activityType) {
        assertFrontStackOnDisplay(msg, windowingMode, activityType, DEFAULT_DISPLAY);
    }

    public void assertFrontStackOnDisplay(String msg, int windowingMode, int activityType,
            int displayId) {
        if (windowingMode != WINDOWING_MODE_UNDEFINED) {
            assertEquals(msg, windowingMode, getFrontRootTaskWindowingMode(displayId));
        }
        if (activityType != ACTIVITY_TYPE_UNDEFINED) {
            assertEquals(msg, activityType, getFrontRootTaskActivityType(displayId));
        }
    }

    public void assertFrontStackActivityType(String msg, int activityType) {
        assertEquals(msg, activityType, getFrontRootTaskActivityType(DEFAULT_DISPLAY));
    }

    public void assertFocusedRootTask(String msg, int taskId) {
        assertEquals(msg, taskId, getFocusedTaskId());
    }

    public void assertFocusedRootTask(String msg, int windowingMode, int activityType) {
        if (windowingMode != WINDOWING_MODE_UNDEFINED) {
            assertEquals(msg, windowingMode, getFocusedRootTaskWindowingMode());
        }
        if (activityType != ACTIVITY_TYPE_UNDEFINED) {
            assertEquals(msg, activityType, getFocusedRootTaskActivityType());
        }
    }

    public void assertFocusedActivity(final String msg, final ComponentName activityName) {
        final String activityComponentName = getActivityName(activityName);
        assertEquals(msg, activityComponentName, getFocusedActivity());
        assertEquals(msg, activityComponentName, getFocusedApp());
    }

    public boolean waitForFocusedActivity(final ComponentName activityName) {
        final String activityComponentName = getActivityName(activityName);
        final String message = activityComponentName + " to be focused";
        return Condition.waitFor(new Condition<>(message, () -> {
            boolean focusedActivityMatching = activityComponentName.equals(getFocusedActivity());
            boolean focusedAppMatching = activityComponentName.equals(getFocusedApp());
            return focusedActivityMatching && focusedAppMatching;
        }).setRetryIntervalMs(200).setRetryLimit(20));
    }

    public void waitAndAssertFocusedActivity(final String msg, final ComponentName activityName) {
        assertTrue(msg, waitForFocusedActivity(activityName));
    }

    public void assertFocusedAppOnDisplay(final String msg, final ComponentName activityName,
            final int displayId) {
        final String activityComponentName = getActivityName(activityName);
        assertEquals(msg, activityComponentName, getDisplay(displayId).getFocusedApp());
    }

    public void assertNotFocusedActivity(String msg, ComponentName activityName) {
        assertNotEquals(msg, getFocusedActivity(), getActivityName(activityName));
        assertNotEquals(msg, getFocusedApp(), getActivityName(activityName));
    }

    public void assertResumedActivity(final String msg, final ComponentName activityName) {
        assertEquals(msg, getActivityName(activityName),
                getFocusedActivity());
    }

    /** Asserts that each display has correct resumed activity. */
    public void assertResumedActivities(final String msg,
            Consumer<SparseArray<ComponentName>> resumedActivitiesMapping) {
        final SparseArray<ComponentName> resumedActivities = new SparseArray<>();
        resumedActivitiesMapping.accept(resumedActivities);
        for (int i = 0; i < resumedActivities.size(); i++) {
            final int displayId = resumedActivities.keyAt(i);
            final ComponentName activityComponent = resumedActivities.valueAt(i);
            assertEquals("Error asserting resumed activity on display " + displayId + ": " + msg,
                    activityComponent != null ? getActivityName(activityComponent) : null,
                    getResumedActivityOnDisplay(displayId));
        }
    }

    public void assertNotResumedActivity(String msg, ComponentName activityName) {
        assertNotEquals(msg, getFocusedActivity(), getActivityName(activityName));
    }

    public void assertFocusedWindow(String msg, String windowName) {
        assertEquals(msg, windowName, getFocusedWindow());
    }

    public void assertNotFocusedWindow(String msg, String windowName) {
        assertNotEquals(msg, getFocusedWindow(), windowName);
    }

    public void assertNotExist(final ComponentName activityName) {
        final String windowName = getWindowName(activityName);
        assertFalse("Activity=" + getActivityName(activityName) + " must NOT exist.",
                containsActivity(activityName));
        assertFalse("Window=" + windowName + " must NOT exits.",
                containsWindow(windowName));
    }

    public void waitAndAssertVisibilityGone(final ComponentName activityName) {
        // Sometimes the surface can be shown due to the late animation.
        // Wait for the animation is done.
        waitForWindowSurfaceDisappeared(getWindowName(activityName));
        assertVisibility(activityName, false);
    }

    public void assertVisibility(final ComponentName activityName, final boolean visible) {
        final String windowName = getWindowName(activityName);
        // Check existence of activity and window.
        assertTrue("Activity=" + getActivityName(activityName) + " must exist.",
                containsActivity(activityName));
        assertTrue("Window=" + windowName + " must exist.", containsWindow(windowName));

        // Check visibility of activity and window.
        assertEquals("Activity=" + getActivityName(activityName) + " must" + (visible ? "" : " NOT")
                + " be visible.", visible, isActivityVisible(activityName));
        assertEquals("Window=" + windowName + " must" + (visible ? "" : " NOT")
                        + " have shown surface.",
                visible, isWindowSurfaceShown(windowName));
    }

    public void assertHomeActivityVisible(boolean visible) {
        final ComponentName homeActivity = getHomeActivityName();
        assertNotNull(homeActivity);
        assertVisibility(homeActivity, visible);
    }

    /**
     * Asserts that the device default display minimum width is larger than the minimum task width.
     */
    public void assertDeviceDefaultDisplaySizeForMultiWindow(String errorMessage) {
        computeState();
        final int minTaskSizePx = defaultMinimalTaskSize(DEFAULT_DISPLAY);
        final WindowManagerState.DisplayContent display = getDisplay(DEFAULT_DISPLAY);
        final Rect displayRect = display.getDisplayRect();
        if (Math.min(displayRect.width(), displayRect.height()) < minTaskSizePx) {
            fail(errorMessage);
        }
    }

    /**
     * Asserts that the device default display minimum width is not smaller than the minimum width
     * for split-screen required by CDD.
     */
    public void assertDeviceDefaultDisplaySizeForSplitScreen(String errorMessage) {
        computeState();
        final int minDisplaySizePx = defaultMinimalDisplaySizeForSplitScreen(DEFAULT_DISPLAY);
        final WindowManagerState.DisplayContent display = getDisplay(DEFAULT_DISPLAY);
        final Rect displayRect = display.getDisplayRect();
        if (Math.max(displayRect.width(), displayRect.height()) < minDisplaySizePx) {
            fail(errorMessage);
        }
    }

    public void assertKeyguardShowingAndOccluded() {
        assertTrue("Keyguard must be showing",
                getKeyguardControllerState().keyguardShowing);
        assertFalse("keyguard must not be going away",
                getKeyguardControllerState().mKeyguardGoingAway);
        assertTrue("Keyguard must be occluded",
                getKeyguardControllerState().isKeyguardOccluded(DEFAULT_DISPLAY));
    }

    public void assertKeyguardShowingAndNotOccluded() {
        assertTrue("Keyguard must be showing",
                getKeyguardControllerState().keyguardShowing);
        assertFalse("Keyguard must not be going away",
                getKeyguardControllerState().mKeyguardGoingAway);
        assertFalse("Keyguard must not be occluded",
                getKeyguardControllerState().isKeyguardOccluded(DEFAULT_DISPLAY));
    }

    public void assertKeyguardGone() {
        assertFalse("Keyguard is not shown",
                getKeyguardControllerState().keyguardShowing);
        assertFalse("Keyguard must not be going away",
                getKeyguardControllerState().mKeyguardGoingAway);
    }

    public void assertKeyguardShownOnSecondaryDisplay(int displayId) {
        assertTrue("KeyguardDialog must be shown on display " + displayId,
                isKeyguardOnSecondaryDisplay(this, displayId));
    }

    public void assertKeyguardGoneOnSecondaryDisplay(int displayId) {
        assertFalse("KeyguardDialog must be gone on display " + displayId,
                isKeyguardOnSecondaryDisplay(this, displayId));
    }

    public void assertAodShowing() {
        assertTrue("AOD is showing",
                getKeyguardControllerState().aodShowing);
    }

    public void assertAodNotShowing() {
        assertFalse("AOD is not showing",
                getKeyguardControllerState().aodShowing);
    }

    public void assertIllegalTaskState() {
        computeState();
        final List<Task> tasks = getRootTasks();
        for (Task task : tasks) {
            task.forAllTasks((t) -> assertWithMessage("Empty task was found, id = " + t.mTaskId)
                    .that(t.mTasks.size() + t.mTaskFragments.size() + t.mActivities.size())
                    .isGreaterThan(0));
            if (task.isLeafTask()) {
                continue;
            }
            assertWithMessage("Non-leaf task cannot have affinity set, id = " + task.mTaskId)
                    .that(task.mAffinity).isEmpty();
        }
    }

    public void assumePendingActivityContain(ComponentName activity) {
        assumeTrue(pendingActivityContain(activity));
    }

    public void assertActivityDisplayed(final ComponentName activityName) {
        assertWindowDisplayed(getWindowName(activityName));
    }

    public void assertWindowDisplayed(final String windowName) {
        waitForValidState(WaitForValidActivityState.forWindow(windowName));
        assertTrue(windowName + " is visible", isWindowSurfaceShown(windowName));
    }

    public void waitAndAssertImePickerShownOnDisplay(int displayId, String message) {
        if (!Condition.waitFor(message, () -> {
            computeState();
            return getMatchingWindowType(TYPE_INPUT_METHOD_DIALOG).stream().anyMatch(
                    w -> w.getDisplayId() == displayId && w.isSurfaceShown());
        })) {
            fail(message);
        }
    }

    public void waitAndAssertImeWindowShownOnDisplay(int displayId) {
        final WindowState imeWinState = Condition.waitForResult("IME window",
                condition -> condition
                        .setResultSupplier(this::getImeWindowState)
                        .setResultValidator(
                                w -> w != null && w.isSurfaceShown()
                                        && w.getDisplayId() == displayId));

        assertNotNull("IME window must exist", imeWinState);
        assertTrue("IME window must be shown", imeWinState.isSurfaceShown());
        assertEquals("IME window must be on the given display", displayId,
                imeWinState.getDisplayId());
    }

    public void waitAndAssertImeWindowHiddenOnDisplay(int displayId) {
        final WindowState imeWinState = Condition.waitForResult("IME window",
                condition -> condition
                        .setResultSupplier(this::getImeWindowState)
                        .setResultValidator(w -> w == null
                                || (!w.isSurfaceShown() && w.getDisplayId() == displayId)));

        if (imeWinState == null) {
            return;
        }
        assertFalse("IME window must be hidden", imeWinState.isSurfaceShown());
        assertEquals("IME window must be on the given display", displayId,
                imeWinState.getDisplayId());
    }

    public WindowState getImeWindowState() {
        computeState();
        return getInputMethodWindowState();
    }

    /**
     * @return the window state for the given {@param activityName}'s window.
     */
    public WindowState getWindowState(ComponentName activityName) {
        String windowName = getWindowName(activityName);
        computeState(activityName);
        final List<WindowManagerState.WindowState> tempWindowList =
                getMatchingVisibleWindowState(windowName);
        return tempWindowList.get(0);
    }

    boolean isScreenPortrait(int displayId) {
        final Rect displayRect = getDisplay(displayId).getDisplayRect();
        return displayRect.height() > displayRect.width();
    }

    private static boolean isKeyguardOnSecondaryDisplay(
            WindowManagerState windowManagerState, int displayId) {
        final List<WindowManagerState.WindowState> states =
                windowManagerState.getMatchingWindowType(TYPE_KEYGUARD_DIALOG);
        for (WindowManagerState.WindowState ws : states) {
            if (ws.getDisplayId() == displayId) return true;
        }
        return false;
    }
}
