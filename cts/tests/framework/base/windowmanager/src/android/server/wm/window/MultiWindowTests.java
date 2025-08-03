/*
 * Copyright (C) 2015 The Android Open Source Project
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

package android.server.wm.window;

import static android.app.ActivityManager.LOCK_TASK_MODE_NONE;
import static android.app.WindowConfiguration.ACTIVITY_TYPE_STANDARD;
import static android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN;
import static android.app.WindowConfiguration.WINDOWING_MODE_MULTI_WINDOW;
import static android.server.wm.TestTaskOrganizer.INVALID_TASK_ID;
import static android.server.wm.WindowManagerState.STATE_RESUMED;
import static android.server.wm.WindowManagerState.STATE_STOPPED;
import static android.server.wm.app.Components.LAUNCHING_ACTIVITY;
import static android.server.wm.app.Components.NON_RESIZEABLE_ACTIVITY;
import static android.server.wm.app.Components.NO_RELAUNCH_ACTIVITY;
import static android.server.wm.app.Components.SINGLE_INSTANCE_ACTIVITY;
import static android.server.wm.app.Components.SINGLE_TASK_ACTIVITY;
import static android.server.wm.app.Components.TEST_ACTIVITY;
import static android.server.wm.app.Components.TEST_ACTIVITY_WITH_SAME_AFFINITY;
import static android.server.wm.app.Components.TRANSLUCENT_TEST_ACTIVITY;
import static android.server.wm.app.Components.TestActivity.TEST_ACTIVITY_ACTION_FINISH_SELF;
import static android.server.wm.app27.Components.SDK_27_LAUNCHING_ACTIVITY;
import static android.server.wm.app27.Components.SDK_27_SEPARATE_PROCESS_ACTIVITY;
import static android.server.wm.app27.Components.SDK_27_TEST_ACTIVITY;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.content.ComponentName;
import android.content.res.Resources;
import android.platform.test.annotations.Presubmit;
import android.server.wm.ActivityManagerTestBase;
import android.server.wm.CommandSession.ActivityCallback;
import android.server.wm.WaitForValidActivityState;
import android.server.wm.WindowManagerState;
import android.view.WindowManager;
import android.window.WindowContainerToken;
import android.window.WindowContainerTransaction;

import org.junit.Before;
import org.junit.Test;

/**
 * Build/Install/Run:
 *     atest CtsWindowManagerDeviceWindow:MultiWindowTests
 */
@Presubmit
@android.server.wm.annotation.Group2
public class MultiWindowTests extends ActivityManagerTestBase {

    private boolean mIsHomeRecentsComponent;

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();

        mIsHomeRecentsComponent = mWmState.isHomeRecentsComponent();

        assumeTrue("Skipping test: no split multi-window support",
                supportsSplitScreenMultiWindow());
    }

    @Test
    public void testMinimumDeviceSize() {
        mWmState.assertDeviceDefaultDisplaySizeForMultiWindow(
                "Devices supporting multi-window must be larger than the default minimum"
                        + " task size");
        mWmState.assertDeviceDefaultDisplaySizeForSplitScreen(
                "Devices supporting split-screen multi-window must be larger than the"
                        + " default minimum display size.");
    }

    /** Resizeable activity should be able to enter multi-window mode.*/
    @Test
    public void testResizeableActivity() {
        assertActivitySupportedInSplitScreen(TEST_ACTIVITY);
    }

    /**
     * Depending on the value of
     * {@link com.android.internal.R.integer.config_supportsNonResizableMultiWindow},
     * non-resizeable activity may or may not be able to enter multi-window mode.
     *
     * Based on the flag value:
     * -1: not support non-resizable in multi window.
     *  0: check the screen smallest width, if it is a large screen, support non-resizable in multi
     *     window. Otherwise, not support.
     *  1: always support non-resizable in multi window.
     */
    @Test
    public void testNonResizeableActivity() {
        createManagedDevEnableNonResizableMultiWindowSession().set(0);
        final Resources resources = mContext.getResources();
        final int configSupportsNonResizableMultiWindow;
        try {
            configSupportsNonResizableMultiWindow = resources.getInteger(resources.getIdentifier(
                    "config_supportsNonResizableMultiWindow", "integer", "android"));
        } catch (Resources.NotFoundException e) {
            fail("Device must define config_supportsNonResizableMultiWindow");
            return;
        }
        switch (configSupportsNonResizableMultiWindow) {
            case -1:
                assertActivityNotSupportedInSplitScreen(NON_RESIZEABLE_ACTIVITY);
                break;
            case 1:
                assertActivitySupportedInSplitScreen(NON_RESIZEABLE_ACTIVITY);
                break;
            case 0:
                final int smallestScreenWidthDp = mWmState.getHomeTask().getFullConfiguration().smallestScreenWidthDp;
                if (smallestScreenWidthDp >= WindowManager.LARGE_SCREEN_SMALLEST_SCREEN_WIDTH_DP) {
                    assertActivitySupportedInSplitScreen(NON_RESIZEABLE_ACTIVITY);
                } else {
                    assertActivityNotSupportedInSplitScreen(NON_RESIZEABLE_ACTIVITY);
                }
                break;
            default:
                fail("config_supportsNonResizableMultiWindow must be -1, 0, or 1.");
        }
    }

    /**
     * Non-resizeable activity can enter split-screen if
     * {@link android.provider.Settings.Global#DEVELOPMENT_ENABLE_NON_RESIZABLE_MULTI_WINDOW} is
     * set.
     */
    @Test
    public void testDevEnableNonResizeableMultiWindow_splitScreenPrimary() {
        createManagedDevEnableNonResizableMultiWindowSession().set(1);

        assertActivitySupportedInSplitScreen(NON_RESIZEABLE_ACTIVITY);
    }

    /**
     * Non-resizeable activity can enter split-screen if
     * {@link android.provider.Settings.Global#DEVELOPMENT_ENABLE_NON_RESIZABLE_MULTI_WINDOW} is
     * set.
     */
    @Test
    public void testDevEnableNonResizeableMultiWindow_splitScreenSecondary() {
        createManagedDevEnableNonResizableMultiWindowSession().set(1);

        launchActivitiesInSplitScreen(
                getLaunchActivityBuilder().setTargetActivity(TEST_ACTIVITY),
                getLaunchActivityBuilder().setTargetActivity(NON_RESIZEABLE_ACTIVITY));

        mWmState.waitForActivityState(NON_RESIZEABLE_ACTIVITY, STATE_RESUMED);
        mWmState.assertVisibility(NON_RESIZEABLE_ACTIVITY, true);
        assertTrue(mWmState.containsActivityInWindowingMode(
                NON_RESIZEABLE_ACTIVITY, WINDOWING_MODE_MULTI_WINDOW));
    }

    /** Asserts that the give activity can be shown in split screen. */
    private void assertActivitySupportedInSplitScreen(ComponentName activity) {
        launchActivityInPrimarySplit(activity);
        mWmState.waitForActivityState(activity, STATE_RESUMED);
        mWmState.assertVisibility(activity, true);
        assertTrue(mWmState.containsActivityInWindowingMode(activity, WINDOWING_MODE_MULTI_WINDOW));
    }

    /** Asserts that the give activity can NOT be shown in split screen. */
    private void assertActivityNotSupportedInSplitScreen(ComponentName activity) {
        boolean gotAssertionError = false;
        try {
            launchActivityInPrimarySplit(activity);
        } catch (AssertionError e) {
            gotAssertionError = true;
        }
        assertTrue("Trying to put non-resizeable activity in split should throw error.",
                gotAssertionError);
        mWmState.waitForActivityState(activity, STATE_RESUMED);
        mWmState.assertVisibility(activity, true);
        assertTrue(mWmState.containsActivityInWindowingMode(activity, WINDOWING_MODE_FULLSCREEN));
    }

    @Test
    public void testLaunchToSideMultiWindowCallbacks() {
        // Launch two activities in split-screen mode.
        launchActivitiesInSplitScreen(
                getLaunchActivityBuilder().setTargetActivity(NO_RELAUNCH_ACTIVITY),
                getLaunchActivityBuilder().setTargetActivity(TEST_ACTIVITY));

        int displayAreaWindowingMode =
                mWmState.getTaskDisplayArea(TEST_ACTIVITY).getWindowingMode();
        separateTestJournal();
        mTaskOrganizer.dismissSplitScreen(true /* primaryOnTop */);
        if (displayAreaWindowingMode == WINDOWING_MODE_FULLSCREEN) {
            // Exit split-screen mode and ensure we only get 1 multi-window mode changed callback.
            final ActivityLifecycleCounts lifecycleCounts = waitForOnMultiWindowModeChanged(
                    NO_RELAUNCH_ACTIVITY);
            assertEquals(1,
                    lifecycleCounts.getCount(ActivityCallback.ON_MULTI_WINDOW_MODE_CHANGED));
        } else {
            // Display is not a fullscreen display, so there won't be a multi-window callback.
            // Instead just verify that windows are not in split-screen anymore.
            waitForIdle();
            mWmState.computeState();
            mWmState.assertDoesNotContainStack("Must have exited split-screen",
                    WINDOWING_MODE_MULTI_WINDOW, ACTIVITY_TYPE_STANDARD);
        }
    }

    @Test
    public void testNoUserLeaveHintOnMultiWindowModeChanged() {
        launchActivity(NO_RELAUNCH_ACTIVITY, WINDOWING_MODE_FULLSCREEN);

        // Move to primary split.
        separateTestJournal();
        putActivityInPrimarySplit(NO_RELAUNCH_ACTIVITY);

        ActivityLifecycleCounts lifecycleCounts =
                waitForOnMultiWindowModeChanged(NO_RELAUNCH_ACTIVITY);
        assertEquals("mMultiWindowModeChangedCount",
                1, lifecycleCounts.getCount(ActivityCallback.ON_MULTI_WINDOW_MODE_CHANGED));
        assertEquals("mUserLeaveHintCount",
                0, lifecycleCounts.getCount(ActivityCallback.ON_USER_LEAVE_HINT));

        // Make sure primary split is focused. This way when we dismiss it later fullscreen stack
        // will come up.
        launchActivity(LAUNCHING_ACTIVITY, WINDOWING_MODE_FULLSCREEN);
        putActivityInSecondarySplit(LAUNCHING_ACTIVITY);

        launchActivity(NO_RELAUNCH_ACTIVITY);

        separateTestJournal();

        // Move activities back to fullscreen screen.
        // TestTaskOrganizer sets windowing modes of tasks to unspecific when putting them to split
        // screens so we need to explicitly set their windowing modes back to fullscreen to avoid
        // inheriting freeform windowing mode from the display on freeform first devices.
        int noRelaunchTaskId = mWmState.getTaskByActivity(NO_RELAUNCH_ACTIVITY).getTaskId();
        WindowContainerToken noRelaunchTaskToken =
                mTaskOrganizer.getTaskInfo(noRelaunchTaskId).getToken();
        WindowContainerTransaction t = new WindowContainerTransaction()
                .setWindowingMode(noRelaunchTaskToken, WINDOWING_MODE_FULLSCREEN);
        mTaskOrganizer.dismissSplitScreen(t, true /* primaryOnTop */);

        lifecycleCounts = waitForOnMultiWindowModeChanged(NO_RELAUNCH_ACTIVITY);
        assertEquals("mMultiWindowModeChangedCount",
                1, lifecycleCounts.getCount(ActivityCallback.ON_MULTI_WINDOW_MODE_CHANGED));
        assertEquals("mUserLeaveHintCount",
                0, lifecycleCounts.getCount(ActivityCallback.ON_USER_LEAVE_HINT));
    }

    @Test
    public void testLaunchToSideAndBringToFront() {
        launchActivitiesInSplitScreen(
                getLaunchActivityBuilder().setTargetActivity(LAUNCHING_ACTIVITY),
                getLaunchActivityBuilder().setTargetActivity(TEST_ACTIVITY));

        mWmState.waitAndAssertFocusedActivity("Launched to side activity must be in front.",
                TEST_ACTIVITY);

        // Set secondary split as launch root
        mTaskOrganizer.setLaunchRoot(mTaskOrganizer.getSecondarySplitTaskId());

        // Launch another activity to side to cover first one.
        launchActivityInSecondarySplit(NO_RELAUNCH_ACTIVITY);
        mWmState.waitAndAssertFocusedActivity(
                "Launched to side covering activity must be in front.", NO_RELAUNCH_ACTIVITY);

        // Launch activity that was first launched to side. It should be brought to front.
        launchActivity(TEST_ACTIVITY);
        mWmState.waitAndAssertFocusedActivity(
                "Launched to side covering activity must be in front.", TEST_ACTIVITY);
    }

    @Test
    public void testLaunchToSideMultiple() {
        launchActivitiesInSplitScreen(
                getLaunchActivityBuilder().setTargetActivity(LAUNCHING_ACTIVITY),
                getLaunchActivityBuilder().setTargetActivity(TEST_ACTIVITY));

        final int taskNumberInitial = mTaskOrganizer.getSecondarySplitTaskCount();

        // Try to launch to side same activity again.
        launchActivity(TEST_ACTIVITY);
        mWmState.computeState(TEST_ACTIVITY, LAUNCHING_ACTIVITY);
        final int taskNumberFinal = mTaskOrganizer.getSecondarySplitTaskCount();
        assertEquals("Task number mustn't change.", taskNumberInitial, taskNumberFinal);
        mWmState.assertFocusedActivity("Launched to side activity must remain in front.",
                TEST_ACTIVITY);
    }

    @Test
    public void testLaunchToSideSingleInstance() {
        launchTargetToSide(SINGLE_INSTANCE_ACTIVITY, false);
    }

    @Test
    public void testLaunchToSideSingleTask() {
        launchTargetToSide(SINGLE_TASK_ACTIVITY, false);
    }

    @Test
    public void testLaunchToSideMultipleWithDifferentIntent() {
        launchTargetToSide(TEST_ACTIVITY, true);
    }

    private void launchTargetToSide(ComponentName targetActivityName,
            boolean taskCountMustIncrement) {
        launchActivityInPrimarySplit(LAUNCHING_ACTIVITY);

        // Launch target to side
        final LaunchActivityBuilder targetActivityLauncher = getLaunchActivityBuilder()
                .setTargetActivity(targetActivityName)
                .setToSide(true)
                .setRandomData(true)
                .setMultipleTask(false);
        targetActivityLauncher.execute();
        final int secondaryTaskId = mWmState.getTaskByActivity(targetActivityName).getTaskId();
        mTaskOrganizer.putTaskInSplitSecondary(secondaryTaskId);

        mWmState.computeState(targetActivityName, LAUNCHING_ACTIVITY);
        final int taskNumberInitial = mTaskOrganizer.getSecondarySplitTaskCount();

        // Try to launch to side same activity again with different data.
        targetActivityLauncher.execute();
        mWmState.computeState(targetActivityName, LAUNCHING_ACTIVITY);

        final int[] excludeTaskIds = new int[] { secondaryTaskId, INVALID_TASK_ID };
        if (taskCountMustIncrement) {
            mWmState.waitFor("Waiting for new activity to come up.",
                    state -> state.getTaskByActivity(targetActivityName, excludeTaskIds) != null);
        }
        WindowManagerState.Task task = mWmState.getTaskByActivity(targetActivityName,
                excludeTaskIds);
        final int secondaryTaskId2;
        if (task != null) {
            secondaryTaskId2 = task.getTaskId();
            // The task is a new child Task in split, so wait until it appear instead of trying
            // to find a root task with this id.
            mTaskOrganizer.waitForAndAssertTaskAppeared(secondaryTaskId2);
            mTaskOrganizer.putTaskInSplitSecondary(secondaryTaskId2);
        } else {
            secondaryTaskId2 = INVALID_TASK_ID;
        }
        final int taskNumberSecondLaunch = mTaskOrganizer.getSecondarySplitTaskCount();

        if (taskCountMustIncrement) {
            assertEquals(
                    "Task number must be incremented.",
                    taskNumberInitial + 1,
                    taskNumberSecondLaunch);
        } else {
            assertEquals("Task number must not change.", taskNumberInitial,
                    taskNumberSecondLaunch);
        }
        mWmState.waitForFocusedActivity("Wait for launched to side activity to be in front.",
                targetActivityName);
        mWmState.assertFocusedActivity("Launched to side activity must be in front.",
                targetActivityName);

        // Try to launch to side same activity again with different random data. Note that null
        // cannot be used here, since the first instance of TestActivity is launched with no data
        // in order to launch into split screen.
        targetActivityLauncher.execute();
        mWmState.computeState(targetActivityName, LAUNCHING_ACTIVITY);

        excludeTaskIds[1] = secondaryTaskId2;
        if (taskCountMustIncrement) {
            mWmState.waitFor("Waiting for the second new activity to come up.",
                    state -> state.getTaskByActivity(targetActivityName, excludeTaskIds) != null);
        }
        WindowManagerState.Task taskFinal =
                mWmState.getTaskByActivity(targetActivityName, excludeTaskIds);
        if (taskFinal != null) {
            int secondaryTaskId3 = taskFinal.getTaskId();
            // The task is a new child Task in split, so wait until it appear instead of trying
            // to find a root task with this id.
            mTaskOrganizer.waitForAndAssertTaskAppeared(secondaryTaskId3);
            mTaskOrganizer.putTaskInSplitSecondary(secondaryTaskId3);
        }
        final int taskNumberFinal = mTaskOrganizer.getSecondarySplitTaskCount();

        if (taskCountMustIncrement) {
            assertEquals("Task number must be incremented.", taskNumberSecondLaunch + 1,
                    taskNumberFinal);
        } else {
            assertEquals("Task number must not change.", taskNumberSecondLaunch,
                    taskNumberFinal);
        }
        mWmState.waitForFocusedActivity("Wait for launched to side activity to be in front.",
                targetActivityName);
        mWmState.assertFocusedActivity("Launched to side activity must be in front.",
                targetActivityName);
    }

    @Test
    public void testLaunchToSideMultipleWithFlag() {
        launchActivitiesInSplitScreen(
                getLaunchActivityBuilder()
                        .setTargetActivity(TEST_ACTIVITY),
                getLaunchActivityBuilder()
                        // Try to launch to side same activity again,
                        // but with Intent#FLAG_ACTIVITY_MULTIPLE_TASK.
                        .setMultipleTask(true)
                        .setTargetActivity(TEST_ACTIVITY));
        assertTrue("Primary split must contain TEST_ACTIVITY",
                mWmState.getRootTask(mTaskOrganizer.getPrimarySplitTaskId())
                        .containsActivity(TEST_ACTIVITY)
        );

        assertTrue("Secondary split must contain TEST_ACTIVITY",
                mWmState.getRootTask(mTaskOrganizer.getSecondarySplitTaskId())
                        .containsActivity(TEST_ACTIVITY)
                );
        mWmState.assertFocusedActivity("Launched to side activity must be in front.",
                TEST_ACTIVITY);
    }

    @Test
    public void testSameProcessActivityResumedPreQ() {
        launchActivitiesInSplitScreen(
                getLaunchActivityBuilder().setTargetActivity(SDK_27_TEST_ACTIVITY),
                getLaunchActivityBuilder().setTargetActivity(SDK_27_LAUNCHING_ACTIVITY));

        assertEquals("There must be only one resumed activity in the package.", 1,
                mWmState.getResumedActivitiesCountInPackage(
                        SDK_27_TEST_ACTIVITY.getPackageName()));
    }

    @Test
    public void testDifferentProcessActivityResumedPreQ() {
        launchActivitiesInSplitScreen(
                getLaunchActivityBuilder().setTargetActivity(SDK_27_TEST_ACTIVITY),
                getLaunchActivityBuilder().setTargetActivity(SDK_27_SEPARATE_PROCESS_ACTIVITY));

        assertEquals("There must be only two resumed activities in the package.", 2,
                mWmState.getResumedActivitiesCountInPackage(
                        SDK_27_TEST_ACTIVITY.getPackageName()));
    }

    @Test
    public void testDisallowUpdateWindowingModeWhenInLockedTask() {
        launchActivity(TEST_ACTIVITY, WINDOWING_MODE_FULLSCREEN);
        final WindowManagerState.Task task =
                mWmState.getStandardRootTaskByWindowingMode(
                        WINDOWING_MODE_FULLSCREEN).getTopTask();

        try {
            // Lock the task
            runWithShellPermission(() -> mAtm.startSystemLockTaskMode(task.getTaskId()));
            waitForOrFail("Fail to enter locked task mode", () ->
                    mAm.getLockTaskModeState() != LOCK_TASK_MODE_NONE);

            // Verify specifying non-fullscreen windowing mode will fail.
            runWithShellPermission(() -> {
                final WindowContainerTransaction wct = new WindowContainerTransaction()
                        .setWindowingMode(
                                mTaskOrganizer.getTaskInfo(task.getTaskId()).getToken(),
                                WINDOWING_MODE_MULTI_WINDOW);
                mTaskOrganizer.applyTransaction(wct);
            });
            mWmState.computeState(TEST_ACTIVITY);
            assertEquals(WINDOWING_MODE_FULLSCREEN,
                    mWmState.getWindowState(TEST_ACTIVITY).getWindowingMode());
        } finally {
            runWithShellPermission(() -> {
                mAtm.stopSystemLockTaskMode();
            });
        }
    }

    @Test
    public void testDisallowReparentOperationWhenInLockedTask() {
        launchActivity(TEST_ACTIVITY, WINDOWING_MODE_FULLSCREEN);
        launchActivity(LAUNCHING_ACTIVITY, WINDOWING_MODE_MULTI_WINDOW);
        final WindowManagerState.Task task = mWmState
                .getStandardRootTaskByWindowingMode(WINDOWING_MODE_FULLSCREEN).getTopTask();
        final WindowManagerState.Task root = mWmState
                .getStandardRootTaskByWindowingMode(WINDOWING_MODE_MULTI_WINDOW).getTopTask();

        try {
            // Lock the task
            runWithShellPermission(
                    () -> {
                        mAtm.startSystemLockTaskMode(task.getTaskId());
                    });
            waitForOrFail("Fail to enter locked task mode", () ->
                    mAm.getLockTaskModeState() != LOCK_TASK_MODE_NONE);

            boolean gotAssertionError = false;
            try {
                runWithShellPermission(
                        () -> {
                            // Fetch tokens of testing task and multi-window root.
                            final WindowContainerToken multiWindowRoot =
                                    mTaskOrganizer.getTaskInfo(root.getTaskId()).getToken();
                            final WindowContainerToken testChild =
                                    mTaskOrganizer.getTaskInfo(task.getTaskId()).getToken();

                            // Verify performing reparent operation is no operation.
                            final WindowContainerTransaction wct =
                                    new WindowContainerTransaction()
                                            .reparent(testChild, multiWindowRoot, true /* onTop */);
                            mTaskOrganizer.applyTransaction(wct);
                            waitForOrFail(
                                    "Fail to reparent",
                                    () ->
                                            mTaskOrganizer
                                                            .getTaskInfo(task.getTaskId())
                                                            .getParentTaskId()
                                                    == root.getTaskId());
                        });
            } catch (AssertionError e) {
                gotAssertionError = true;
            }
            assertTrue("Not allowed to perform hierarchy operation while in locked task mode.",
                    gotAssertionError);
        } finally {
            runWithShellPermission(() -> {
                mAtm.stopSystemLockTaskMode();
            });
        }
    }

    /**
     * Asserts that the activity is visible when the top opaque activity finishes and with another
     * translucent activity on top while in split-screen-secondary task.
     */
    @Test
    public void testVisibilityWithTranslucentAndTopFinishingActivity() {
        // Launch two activities in split-screen mode.
        launchActivitiesInSplitScreen(
                getLaunchActivityBuilder().setTargetActivity(LAUNCHING_ACTIVITY),
                getLaunchActivityBuilder().setTargetActivity(TEST_ACTIVITY_WITH_SAME_AFFINITY));

        mTaskOrganizer.setLaunchRoot(mTaskOrganizer.getSecondarySplitTaskId());

        // Launch two more activities on a different task on top of split-screen-secondary and
        // only the top opaque activity should be visible.
        // Explicitly launch them into fullscreen mode because the control windowing mode of the
        // launch root doesn't include freeform mode. Freeform first devices launch apps in freeform
        // mode by default, which won't trigger the launch root.
        getLaunchActivityBuilder().setTargetActivity(TRANSLUCENT_TEST_ACTIVITY)
                .setUseInstrumentation()
                .setWaitForLaunched(true)
                .setWindowingMode(WINDOWING_MODE_FULLSCREEN)
                .execute();
        getLaunchActivityBuilder().setTargetActivity(TEST_ACTIVITY)
                .setUseInstrumentation()
                .setWaitForLaunched(true)
                .setWindowingMode(WINDOWING_MODE_FULLSCREEN)
                .execute();
        mWmState.assertVisibility(TEST_ACTIVITY, true);
        mWmState.waitForActivityState(TRANSLUCENT_TEST_ACTIVITY, STATE_STOPPED);
        mWmState.assertVisibility(TRANSLUCENT_TEST_ACTIVITY, false);
        mWmState.assertVisibility(TEST_ACTIVITY_WITH_SAME_AFFINITY, false);

        // Finish the top opaque activity and both the two activities should be visible.
        mBroadcastActionTrigger.doAction(TEST_ACTIVITY_ACTION_FINISH_SELF);
        mWmState.computeState(new WaitForValidActivityState(TRANSLUCENT_TEST_ACTIVITY));
        mWmState.assertVisibility(TRANSLUCENT_TEST_ACTIVITY, true);
        mWmState.assertVisibility(TEST_ACTIVITY_WITH_SAME_AFFINITY, true);
    }

    /**
     * Ensure that the hierarchy operation : reorder is not allowed if the target task is violated
     * the lock-task policy.
     */
    @Test
    public void testDisallowReOrderOperationWhenInLockedTask() {
        // Start LaunchingActivity and testActivity in two separate tasks.
        launchActivity(LAUNCHING_ACTIVITY, WINDOWING_MODE_FULLSCREEN);
        launchActivity(TEST_ACTIVITY, WINDOWING_MODE_FULLSCREEN);
        waitAndAssertResumedActivity(TEST_ACTIVITY, "Activity must be resumed");

        final int taskId = mWmState.getTaskByActivity(TEST_ACTIVITY).getTaskId();
        final int taskId2 = mWmState.getTaskByActivity(LAUNCHING_ACTIVITY).getTaskId();
        // Make sure the launching activity and the test activity are not in the same task.
        assertNotEquals("Activity must be in different task.", taskId, taskId2);

        try {
            runWithShellPermission(() -> {
                mAtm.startSystemLockTaskMode(taskId);
            });
            waitForOrFail("Fail to enter locked task mode", () ->
                    mAm.getLockTaskModeState() != LOCK_TASK_MODE_NONE);

            boolean gotAssertionError = false;
            try {
                runWithShellPermission(() -> {
                    final WindowContainerToken token =
                            mTaskOrganizer.getTaskInfo(taskId2).getToken();

                    // Verify performing reorder operation is no operation.
                    final WindowContainerTransaction wct = new WindowContainerTransaction()
                            .reorder(token, true /* onTop */);
                    mTaskOrganizer.applyTransaction(wct);

                    final WindowManagerState.Task topTask = mWmState
                            .getStandardRootTaskByWindowingMode(WINDOWING_MODE_FULLSCREEN)
                            .getTopTask();
                    waitForOrFail("Fail to reorder", () -> (topTask.getTaskId() == taskId2));
                });
            } catch (AssertionError e) {
                gotAssertionError = true;
            }
            assertTrue("Not allowed to perform reorder operation while in locked task mode.",
                    gotAssertionError);
        } finally {
            runWithShellPermission(() -> {
                mAtm.stopSystemLockTaskMode();
            });
        }
    }
}
