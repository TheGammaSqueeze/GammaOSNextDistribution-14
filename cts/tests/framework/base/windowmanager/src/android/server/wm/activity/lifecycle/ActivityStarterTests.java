/*
 * Copyright (C) 2018 The Android Open Source Project
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

package android.server.wm.activity.lifecycle;

import static android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN;
import static android.app.WindowConfiguration.WINDOWING_MODE_MULTI_WINDOW;
import static android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK;
import static android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP;
import static android.content.Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT;
import static android.content.Intent.FLAG_ACTIVITY_MULTIPLE_TASK;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static android.content.Intent.FLAG_ACTIVITY_PREVIOUS_IS_TOP;
import static android.content.Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED;
import static android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP;
import static android.server.wm.ComponentNameUtils.getActivityName;
import static android.server.wm.UiDeviceUtils.pressWakeupButton;
import static android.server.wm.WindowManagerState.STATE_DESTROYED;
import static android.server.wm.WindowManagerState.STATE_RESUMED;
import static android.server.wm.activity.lifecycle.LifecycleConstants.ON_STOP;
import static android.server.wm.activity.lifecycle.LifecycleConstants.getComponentName;
import static android.server.wm.app.Components.ALIAS_TEST_ACTIVITY;
import static android.server.wm.app.Components.NO_HISTORY_ACTIVITY;
import static android.server.wm.app.Components.SHOW_WHEN_LOCKED_TRANSLUCENT_ACTIVITY;
import static android.server.wm.app.Components.TEST_ACTIVITY;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;
import static org.junit.Assume.assumeFalse;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.platform.test.annotations.Presubmit;
import android.server.wm.ActivityLauncher;
import android.server.wm.HelperActivities;
import android.server.wm.LockScreenSession;
import android.server.wm.WaitForValidActivityState;
import android.server.wm.app.Components;

import org.junit.Test;

/**
 * Build/Install/Run:
 *     atest CtsWindowManagerDeviceActivity:ActivityStarterTests
 */
@Presubmit
@android.server.wm.annotation.Group3
public class ActivityStarterTests extends ActivityLifecycleClientTestBase {

    private static final ComponentName STANDARD_ACTIVITY
            = getComponentName(HelperActivities.StandardActivity.class);
    private static final ComponentName SECOND_STANDARD_ACTIVITY
            = getComponentName(HelperActivities.SecondStandardActivity.class);
    private static final ComponentName SINGLE_TOP_ACTIVITY
            = getComponentName(SingleTopActivity.class);
    private static final ComponentName SINGLE_INSTANCE_ACTIVITY
            = getComponentName(SingleInstanceActivity.class);
    private static final ComponentName SINGLE_TASK_ACTIVITY
            = getComponentName(SingleTaskActivity.class);
    private static final ComponentName STANDARD_SINGLE_TOP_ACTIVITY
            = getComponentName(StandardWithSingleTopActivity.class);
    private static final ComponentName TEST_LAUNCHING_ACTIVITY
            = getComponentName(TestLaunchingActivity.class);
    private static final ComponentName LAUNCHING_AND_FINISH_ACTIVITY
            = getComponentName(LaunchingAndFinishActivity.class);
    private static final ComponentName CLEAR_TASK_ON_LAUNCH_ACTIVITY
            = getComponentName(ClearTaskOnLaunchActivity.class);
    private static final ComponentName FINISH_ON_TASK_LAUNCH_ACTIVITY
            = getComponentName(FinishOnTaskLaunchActivity.class);
    private static final ComponentName DOCUMENT_INTO_EXISTING_ACTIVITY
            = getComponentName(DocumentIntoExistingActivity.class);
    private static final ComponentName RELINQUISHTASKIDENTITY_ACTIVITY
            = getComponentName(RelinquishTaskIdentityActivity.class);


    /**
     * Ensures that the following launch flag combination works when starting an activity which is
     * already running: - {@code FLAG_ACTIVITY_CLEAR_TOP} - {@code
     * FLAG_ACTIVITY_RESET_TASK_IF_NEEDED} - {@code FLAG_ACTIVITY_NEW_TASK}
     */
    @Test
    public void testClearTopNewTaskResetTask() throws Exception {
        // Start activity normally
        launchActivityAndWait(FirstActivity.class);

        // Navigate home
        launchHomeActivity();
        waitAndAssertActivityStates(state(FirstActivity.class, ON_STOP));
        getTransitionLog().clear();

        // Start activity again with flags in question. Verify activity is resumed.
        // A new instance of activity will be created, and the old one destroyed.
        final Activity secondLaunchActivity = new Launcher(FirstActivity.class)
                .setFlags(FLAG_ACTIVITY_CLEAR_TOP | FLAG_ACTIVITY_NEW_TASK
                        | FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                .launch();
        mWmState.waitForActivityState(secondLaunchActivity.getComponentName(), STATE_RESUMED);
        assertEquals("The activity should be started and be resumed",
                getActivityName(secondLaunchActivity.getComponentName()),
                mWmState.getTopActivityName(0));
    }

    /**
     * This test case tests "standard" activity behavior.
     * A first launched standard activity and a second launched standard activity
     * must be in same task.
     */
    @Test
    public void testLaunchStandardActivity() {
        // Launch a standard activity.
        launchActivity(STANDARD_ACTIVITY);

        final int taskId = mWmState.getTaskByActivity(STANDARD_ACTIVITY).getTaskId();
        final int instances = mWmState.getActivityCountInTask(taskId, null);

        // Launch a second standard activity.
        launchActivity(SECOND_STANDARD_ACTIVITY);

        // Make sure instances in task are increased.
        assertEquals("instances of activity in task must be increased.", instances + 1,
                mWmState.getActivityCountInTask(taskId, null));

        // Make sure the stack for the second standard activity is front.
        assertEquals("The stack for the second standard activity must be front.",
                getActivityName(SECOND_STANDARD_ACTIVITY),
                mWmState.getTopActivityName(0));

        // Make sure the standard activity and the second standard activity are in same task.
        assertEquals("Activity must be in same task.", taskId,
                mWmState.getTaskByActivity(SECOND_STANDARD_ACTIVITY).getTaskId());
    }

    /**
     * This test case tests show-when-locked behavior for a "no-history" activity.
     * The no-history activity should be resumed over lockscreen.
     */
    @Test
    public void testLaunchNoHistoryActivityShowWhenLocked() {
        // Allow TV devices to skip this test.
        assumeFalse(isLeanBack());
        final LockScreenSession lockScreenSession = createManagedLockScreenSession();
        lockScreenSession.sleepDevice();

        getLaunchActivityBuilder().setTargetActivity(NO_HISTORY_ACTIVITY)
                .setIntentExtra(extra -> extra.putBoolean(
                        Components.NoHistoryActivity.EXTRA_SHOW_WHEN_LOCKED, true))
                .setUseInstrumentation().execute();
        waitAndAssertActivityState(NO_HISTORY_ACTIVITY, STATE_RESUMED,
            "Activity should be resumed");
    }

    /**
     * This test case tests the behavior for a "no-history" activity after turning the screen off.
     * The no-history activity must be resumed over lockscreen when launched again.
     */
    @Test
    public void testNoHistoryActivityNotFinished() {
        assumeTrue(supportsLockScreen());

        final LockScreenSession lockScreenSession = createManagedLockScreenSession();
        // Launch a no-history activity
        getLaunchActivityBuilder().setTargetActivity(NO_HISTORY_ACTIVITY)
                .setIntentExtra(extra -> extra.putBoolean(
                        Components.NoHistoryActivity.EXTRA_SHOW_WHEN_LOCKED, true))
                .setUseInstrumentation().execute();

        // Wait for the activity resumed.
        mWmState.waitForActivityState(NO_HISTORY_ACTIVITY, STATE_RESUMED);

        lockScreenSession.sleepDevice();

        // Launch a no-history activity
        launchActivity(NO_HISTORY_ACTIVITY);

        // Wait for the activity resumed
        waitAndAssertActivityState(NO_HISTORY_ACTIVITY, STATE_RESUMED,
                "Activity must be resumed");
    }

    /**
     * This test case tests the behavior that a fullscreen activity was started on top of the
     * no-history activity within different tasks during sleeping. The no-history activity must be
     * finished.
     */
    @Test
    public void testNoHistoryActivityWithDifferentTask() {
        assumeTrue(supportsLockScreen());

        final LockScreenSession lockScreenSession = createManagedLockScreenSession();
        // Launch a no-history activity
        getLaunchActivityBuilder().setTargetActivity(NO_HISTORY_ACTIVITY)
                .setIntentExtra(extra -> extra.putBoolean(
                        Components.NoHistoryActivity.EXTRA_SHOW_WHEN_LOCKED, true))
                .setWaitForLaunched(false)
                .setUseInstrumentation()
                .execute();

        // Wait for the activity resumed.
        waitAndAssertActivityState(NO_HISTORY_ACTIVITY, STATE_RESUMED,
                "Activity must be resumed");
        final int taskId = mWmState.getTaskByActivity(NO_HISTORY_ACTIVITY).getTaskId();
        lockScreenSession.sleepDevice();

        // Launch a single instance activity
        getLaunchActivityBuilder().setTargetActivity(SINGLE_INSTANCE_ACTIVITY)
                .setIntentExtra(extra -> extra.putBoolean(
                        SingleInstanceActivity.EXTRA_SHOW_WHEN_LOCKED, true))
                .setWaitForLaunched(false)
                .setUseInstrumentation()
                .execute();

        // Make sure the activity is finished.
        final String waitFinishMsg = "Instance of no-history activity must not exist";
        assertTrue(waitFinishMsg, mWmState.waitForWithAmState(
                amState -> 0 == amState.getActivityCountInTask(taskId, NO_HISTORY_ACTIVITY),
                waitFinishMsg));

        // Turn the screen on after the test is completed to prevent keyDispatchingTimedOut during
        // the lockScreenSession close.
        pressWakeupButton();
    }

    /**
     * This test case tests the behavior that a translucent activity was started on top of the
     * no-history activity during sleeping. The no-history activity must not be finished.
     */
    @Test
    public void testNoHistoryActivityWithTranslucentActivity() {
        assumeTrue(supportsLockScreen());

        final LockScreenSession lockScreenSession = createManagedLockScreenSession();
        // Launch a no-history activity
        getLaunchActivityBuilder().setTargetActivity(NO_HISTORY_ACTIVITY)
                .setIntentExtra(extra -> extra.putBoolean(
                        Components.NoHistoryActivity.EXTRA_SHOW_WHEN_LOCKED, true))
                .setWaitForLaunched(false)
                .setUseInstrumentation()
                .execute();

        // Wait for the activity resumed.
        waitAndAssertActivityState(NO_HISTORY_ACTIVITY, STATE_RESUMED,
                "Activity must be resumed");

        final int taskId = mWmState.getTaskByActivity(NO_HISTORY_ACTIVITY).getTaskId();
        lockScreenSession.sleepDevice();
        launchActivityNoWait(SHOW_WHEN_LOCKED_TRANSLUCENT_ACTIVITY);

        final String waitFinishMsg = "Instance of no-history activity must exist";
        assertTrue(waitFinishMsg, mWmState.waitForWithAmState(
                amState -> 1 == amState.getActivityCountInTask(taskId, NO_HISTORY_ACTIVITY),
                waitFinishMsg));

        // Turn the screen on after the test is completed to prevent keyDispatchingTimedOut during
        // the lockScreenSession close.
        pressWakeupButton();
    }

    /**
     * This test case tests "single top" activity behavior.
     * - A first launched standard activity and a second launched single top
     * activity are in same task.
     * - A new instance of single top activity is not created if task
     * already has a its activity at the top of its task.
     */
    @Test
    public void testLaunchSingleTopActivity() {
        // Launch a standard activity.
        launchActivity(STANDARD_ACTIVITY);

        final int taskId = mWmState.getTaskByActivity(STANDARD_ACTIVITY).getTaskId();

        // Launch a single top activity.
        launchActivity(SINGLE_TOP_ACTIVITY);

        final int instances = mWmState.getActivityCountInTask(taskId, null);

        // Make sure the single top activity is in focus.
        mWmState.assertFocusedActivity(SINGLE_TOP_ACTIVITY + "must be focused Activity",
                SINGLE_TOP_ACTIVITY);

        // Make sure the stack for the single top activity is front.
        assertEquals("The stack for the single top activity must be front.",
                getActivityName(SINGLE_TOP_ACTIVITY),
                mWmState.getTopActivityName(0));

        // Make sure the standard activity and the single top activity are in same task.
        assertEquals("Two activities must be in same task.", taskId,
                mWmState.getTaskByActivity(SINGLE_TOP_ACTIVITY).getTaskId());

        // Launch a single top activity.
        launchActivity(SINGLE_TOP_ACTIVITY);

        // Make sure that instances of activity are not increased.
        assertEquals("instances of activity must not be increased.", instances,
                mWmState.getActivityCountInTask(taskId, null));
    }

    /**
     * This test case tests "single instance" activity behavior.
     * - A first launched standard activity and a second launched single instance
     * activity are not in same task.
     * - A single instance activity is always the single and only member of its task.
     */
    @Test
    public void testLaunchSingleInstanceActivity() {
        // Launch a standard activity.
        launchActivity(STANDARD_ACTIVITY);

        final int firstTaskId = mWmState
                .getTaskByActivity(STANDARD_ACTIVITY).getTaskId();

        // Launch a single instance activity
        launchActivity(SINGLE_INSTANCE_ACTIVITY);

        final int secondTaskId = mWmState
                .getTaskByActivity(SINGLE_INSTANCE_ACTIVITY).getTaskId();

        // Make sure the single instance activity is in focus.
        mWmState.assertFocusedActivity(SINGLE_INSTANCE_ACTIVITY + "must be focused Activity",
                SINGLE_INSTANCE_ACTIVITY);
        // Make sure the single instance activity is front.
        assertEquals("The stack for the single instance activity must be front.",
                getActivityName(SINGLE_INSTANCE_ACTIVITY),
                mWmState.getTopActivityName(0));

        // Make sure the standard activity and the test activity are not in same task.
        assertNotEquals("Activity must be in different task.", firstTaskId, secondTaskId);

        // Make sure the single instance activity is only member of its task.
        assertEquals("Single instance activity is only member of its task", 1,
                mWmState.getActivityCountInTask(secondTaskId, null));
    }

    /**
     * This test case tests "single task" activity behavior.
     * - A first launched standard activity and a second launched single task activity
     * are in same task.
     * - Instance of single task activity is only one in its task.
     */
    @Test
    public void testLaunchSingleTaskActivity() {
        // Launch a standard activity.
        launchActivity(STANDARD_ACTIVITY);

        final int taskId = mWmState.getTaskByActivity(STANDARD_ACTIVITY).getTaskId();

        // Launch a single task activity
        launchActivity(SINGLE_TASK_ACTIVITY);

        // Make sure the single task activity is in focus.
        mWmState.assertFocusedActivity(SINGLE_TASK_ACTIVITY + "must be focused Activity",
                SINGLE_TASK_ACTIVITY);

        // Make sure the stack for the single task activity is front.
        assertEquals("The stack for the single task activity must be front.",
                getActivityName(SINGLE_TASK_ACTIVITY),
                mWmState.getTopActivityName(0));

        // Make sure the test activity is in same task.
        assertEquals("Activity must be in same task.", taskId,
                mWmState.getTaskByActivity(SINGLE_TASK_ACTIVITY).getTaskId());

        // Launch a second standard activity
        launchActivity(SECOND_STANDARD_ACTIVITY);

        // Launch a single task activity again.
        launchActivity(SINGLE_TASK_ACTIVITY);
        mWmState.waitForActivityRemoved(SECOND_STANDARD_ACTIVITY);

        // Make sure the number of instances for single task activity is only one.
        assertEquals("Instance of single task activity in its task must be only one", 1,
                mWmState.getActivityCountInTask(taskId, SINGLE_TASK_ACTIVITY));
        // Make sure that instance of standard activity does not exists.
        assertEquals("Instance of second standard activity must not exist.", 0,
                mWmState.getActivityCountInTask(taskId, SECOND_STANDARD_ACTIVITY));

    }

    /**
     * Tests that the existing task would be brought to top while launching alias activity or
     * real activity without creating new activity instances, tasks, or stacks.
     */
    @Test
    public void testLaunchAliasActivity() {
        // Launch alias activity.
        getLaunchActivityBuilder().setUseInstrumentation().setTargetActivity(ALIAS_TEST_ACTIVITY)
                .setIntentFlags(FLAG_ACTIVITY_NEW_TASK).execute();

        final int testStacks = countTestRootTasks();
        final int taskId = mWmState.getTaskByActivity(ALIAS_TEST_ACTIVITY).getTaskId();

        // Return to home and launch the alias activity again.
        launchHomeActivity();
        getLaunchActivityBuilder().setUseInstrumentation().setTargetActivity(ALIAS_TEST_ACTIVITY)
                .setIntentFlags(FLAG_ACTIVITY_NEW_TASK).execute();
        assertEquals("Instance of the activity in its task must be only one", 1,
                mWmState.getActivityCountInTask(taskId, ALIAS_TEST_ACTIVITY));
        assertEquals("Test stack count should not be increased.", testStacks,
                countTestRootTasks());

        // Return to home and launch the real activity.
        launchHomeActivity();
        getLaunchActivityBuilder().setUseInstrumentation().setTargetActivity(TEST_ACTIVITY)
                .setIntentFlags(FLAG_ACTIVITY_NEW_TASK).execute();
        assertEquals("Instance of the activity in its task must be only one", 1,
                mWmState.getActivityCountInTask(taskId, ALIAS_TEST_ACTIVITY));
        assertEquals("Test stack count should not be increased.", testStacks,
                countTestRootTasks());
    }

    private int countTestRootTasks() {
        return mWmState.getRootTasksCount(
                t -> ALIAS_TEST_ACTIVITY.getPackageName().equals(t.getPackageName()));
    }

    /**
     * This test case tests behavior of activities launched with FLAG_ACTIVITY_NEW_TASK
     * and FLAG_ACTIVITY_CLEAR_TASK.
     * A first launched activity is finished, then a second activity is created if the
     * second activity is launched with FLAG_ACTIVITY_NEW_TASK and FLAG_ACTIVITY_CLEAR_TASK.
     */
    @Test
    public void testLaunchActivityWithFlagNewTaskAndClearTask() {
        // Launch a standard activity with FLAG_ACTIVITY_NEW_TASK.
        getLaunchActivityBuilder()
                .setTargetActivity(STANDARD_ACTIVITY)
                .setIntentFlags(FLAG_ACTIVITY_NEW_TASK)
                .execute();

        final int taskId = mWmState.getTaskByActivity(STANDARD_ACTIVITY).getTaskId();

        // Launch Activity with FLAG_ACTIVITY_NEW_TASK|FLAG_ACTIVITY_CLEAR_TASK.
        getLaunchActivityBuilder()
                .setTargetActivity(STANDARD_ACTIVITY)
                .setLaunchingActivity(TEST_LAUNCHING_ACTIVITY)
                .setIntentFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TASK)
                .execute();

        mWmState.waitForActivityState(STANDARD_ACTIVITY, STATE_DESTROYED);

        // Make sure the number of instances for standard activity is one
        // because previous standard activity to be finished due to FLAG_ACTIVITY_CLEAR_TASK.
        assertEquals("Instance of activity must be one", 1,
                mWmState.getActivityCountInTask(taskId, STANDARD_ACTIVITY));

        // Make sure the stack for the standard activity is front.
        assertEquals("The stack for the standard activity must be front.",
                getActivityName(STANDARD_ACTIVITY),
                mWmState.getTopActivityName(0));
    }

    /**
     * This test case tests behavior of activity launched with FLAG_ACTIVITY_CLEAR_TOP.
     * A top activity is finished when an activity is launched with FLAG_ACTIVITY_CLEAR_TOP.
     */
    @Test
    public void testLaunchActivityWithFlagClearTop() {
        // Launch a standard activity
        getLaunchActivityBuilder()
                .setTargetActivity(STANDARD_ACTIVITY)
                .setUseInstrumentation()
                .execute();

        final int taskId = mWmState.getTaskByActivity(STANDARD_ACTIVITY).getTaskId();

        // Launch a second standard activity
        getLaunchActivityBuilder()
                .setTargetActivity(SECOND_STANDARD_ACTIVITY)
                .setUseInstrumentation()
                .execute();

        // Launch a standard activity again with CLEAR_TOP_FLAG
        getLaunchActivityBuilder()
                .setTargetActivity(STANDARD_ACTIVITY)
                .setUseInstrumentation()
                .setIntentFlags(FLAG_ACTIVITY_CLEAR_TOP)
                .execute();

        mWmState.waitForActivityState(STANDARD_ACTIVITY, STATE_RESUMED);

        // Make sure that the standard activity is in focus.
        mWmState.assertFocusedActivity(STANDARD_ACTIVITY + "must be focused Activity",
                STANDARD_ACTIVITY);

        // Make sure the stack for the standard activity is front.
        assertEquals("The stack for the standard activity must be front.",
                getActivityName(STANDARD_ACTIVITY),
                mWmState.getTopActivityName(0));

        // Make sure the activity is not in same task.
        assertEquals("Activity must be in same task.", taskId,
                mWmState.getTaskByActivity(STANDARD_ACTIVITY).getTaskId());
        // Make sure the second standard activity is finished.
        final String waitFinishMsg = "Instance of second standard activity must not exist";
        assertTrue(waitFinishMsg, mWmState.waitForWithAmState(
                amState -> 0 == amState.getActivityCountInTask(taskId, SECOND_STANDARD_ACTIVITY),
                waitFinishMsg));
    }

    @Test
    public void testLaunchActivityWithFlagPreviousIsTop() {
        // Launch a standard activity
        getLaunchActivityBuilder()
                .setTargetActivity(SINGLE_TOP_ACTIVITY)
                .execute();

        final int taskId = mWmState.getTaskByActivity(
                SINGLE_TOP_ACTIVITY).getTaskId();

        // Launch a standard activity again with PREVIOUS_IS_TOP
        getLaunchActivityBuilder()
                .setTargetActivity(SINGLE_TOP_ACTIVITY)
                .setLaunchingActivity(LAUNCHING_AND_FINISH_ACTIVITY)
                .setIntentFlags(FLAG_ACTIVITY_PREVIOUS_IS_TOP)
                .execute();

        assertEquals("Instance of activity must be one", 1,
                mWmState.getActivityCountInTask(taskId, SINGLE_TOP_ACTIVITY));
    }

    /**
     * This test case tests behavior of activity launched with FLAG_ACTIVITY_SINGLE_TOP.
     * A single top activity must not be launched if it is already running at the top
     * of the history stack.
     */
    @Test
    public void testLaunchActivityWithFlagSingleTop() {
        // Launch a standard activity
        getLaunchActivityBuilder()
                .setUseInstrumentation()
                .setTargetActivity(STANDARD_ACTIVITY)
                .execute();

        final int taskId = mWmState.getTaskByActivity(STANDARD_ACTIVITY).getTaskId();

        // Launch a standard activity with SINGLE_TOP flag.
        // This standard activity launches a standard activity with single top flag.
        getLaunchActivityBuilder()
                .setTargetActivity(STANDARD_SINGLE_TOP_ACTIVITY)
                .setUseInstrumentation()
                .setIntentFlags(FLAG_ACTIVITY_SINGLE_TOP)
                .execute();

        mWmState.waitForActivityState(STANDARD_SINGLE_TOP_ACTIVITY, STATE_RESUMED);

        // Make sure that a new instance is not created if it is already running at the top
        // of the history stack.
        assertEquals("Multiple single top activities must not be created.", 1,
                mWmState
                        .getActivityCountInTask(taskId, STANDARD_SINGLE_TOP_ACTIVITY));


        // Make sure that activity is in focus.
        mWmState.assertFocusedActivity(STANDARD_SINGLE_TOP_ACTIVITY + "must be focused Activity",
                STANDARD_SINGLE_TOP_ACTIVITY);
        // Make sure the stack for the single top activity is front.
        assertEquals("The stack for the single top activity must be front.",
                getActivityName(STANDARD_SINGLE_TOP_ACTIVITY),
                mWmState.getTopActivityName(0));

        // Make sure the standard activity and the single top activity are in same task.
        assertEquals("Activity must be in same task.", taskId,
                mWmState.getTaskByActivity(STANDARD_SINGLE_TOP_ACTIVITY)
                        .getTaskId());
    }

    /**
     * This test case tests behavior of activity launched with FLAG_ACTIVITY_MULTIPLE_TASK
     * and FLAG_ACTIVITY_NEW_TASK.
     * Second launched activity which is launched with FLAG_ACTIVITY_MULTIPLE_TASK and
     * FLAG_ACTIVITY_NEW_TASK is created in new task/stack. So, a first launched activity
     * and a second launched activity are in different task/stack.
     */
    @Test
    public void testActivityWithFlagMultipleTaskAndNewTask() {
        // Launch a standard activity
        getLaunchActivityBuilder()
                .setTargetActivity(STANDARD_ACTIVITY)
                .setIntentFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_MULTIPLE_TASK)
                .execute();

        final int taskId = mWmState.getTaskByActivity(STANDARD_ACTIVITY).getTaskId();

        // Launch a standard activity with FLAG_ACTIVITY_MULTIPLE_TASK|FLAG_ACTIVITY_NEW_TASK
        getLaunchActivityBuilder()
                .setTargetActivity(STANDARD_ACTIVITY)
                .setLaunchingActivity(TEST_LAUNCHING_ACTIVITY)
                .setIntentFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_MULTIPLE_TASK)
                .execute();

        // Make sure the stack for the standard activity is front.
        assertEquals("The stack for the standard activity must be front.",
                getActivityName(STANDARD_ACTIVITY),
                mWmState.getTopActivityName(0));
        // Make sure the first standard activity and second standard activity are not in same task.
        assertNotEquals("Activity must not be in same task.", taskId,
                mWmState.getTaskByActivity(STANDARD_ACTIVITY).getTaskId());
    }

    /**
     * This test case tests behavior of activity launched with ClearTaskOnLaunch attribute and
     * FLAG_ACTIVITY_RESET_TASK_IF_NEEDED. The activities above will be removed from the task when
     * the clearTaskonlaunch activity is re-launched again.
     */
    @Test
    public void testActivityWithClearTaskOnLaunch() {
        // Launch a clearTaskonlaunch activity
        getLaunchActivityBuilder()
                .setUseInstrumentation()
                .setTargetActivity(CLEAR_TASK_ON_LAUNCH_ACTIVITY)
                .setIntentFlags(FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                .execute();
        mWmState.waitForActivityState(CLEAR_TASK_ON_LAUNCH_ACTIVITY, STATE_RESUMED);
        final int taskId = mWmState.getTaskByActivity(CLEAR_TASK_ON_LAUNCH_ACTIVITY).getTaskId();

        // Launch a standard activity
        getLaunchActivityBuilder()
                .setUseInstrumentation()
                .setTargetActivity(STANDARD_ACTIVITY)
                .execute();
        mWmState.waitForActivityState(STANDARD_ACTIVITY, STATE_RESUMED);

        // Return to home
        launchHomeActivity();

        // Launch the clearTaskonlaunch activity again
        getLaunchActivityBuilder()
                .setUseInstrumentation()
                .setTargetActivity(CLEAR_TASK_ON_LAUNCH_ACTIVITY)
                .setIntentFlags(FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                .execute();
        mWmState.waitForActivityState(CLEAR_TASK_ON_LAUNCH_ACTIVITY, STATE_RESUMED);
        mWmState.waitForActivityState(STANDARD_ACTIVITY, STATE_DESTROYED);

        // Make sure the task for the clearTaskonlaunch activity is front.
        assertEquals("The task for the clearTaskonlaunch activity must be front.",
                getActivityName(CLEAR_TASK_ON_LAUNCH_ACTIVITY),
                mWmState.getTopActivityName(0));

        assertEquals("Instance of the activity in its task must be cleared", 0,
                mWmState.getActivityCountInTask(taskId, STANDARD_ACTIVITY));
    }

    /**
     * This test case tests behavior of activity with finishOnTaskLaunch attribute when the
     * activity's task is relaunched from home, this activity should be finished.
     */
    @Test
    public void testActivityWithFinishOnTaskLaunch() {
        // Launch a standard activity.
        launchActivity(STANDARD_ACTIVITY);

        final int taskId = mWmState.getTaskByActivity(STANDARD_ACTIVITY).getTaskId();
        final int instances = mWmState.getActivityCountInTask(taskId, null);

        // Launch a activity with finishOnTaskLaunch
        launchActivity(FINISH_ON_TASK_LAUNCH_ACTIVITY);

        // Make sure instances in task are increased.
        assertEquals("instances of activity in task must be increased.", instances + 1,
                mWmState.getActivityCountInTask(taskId, null));

        // Navigate home
        launchHomeActivity();

        // Simulate to launch the activity from home again
        getLaunchActivityBuilder()
                .setUseInstrumentation()
                .setTargetActivity(STANDARD_ACTIVITY)
                .setIntentFlags(FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                .execute();
        mWmState.waitForActivityState(STANDARD_ACTIVITY, STATE_RESUMED);

        // Make sure the activity is removed.
        mWmState.waitAndAssertActivityRemoved(FINISH_ON_TASK_LAUNCH_ACTIVITY);
    }

    @Test
    public void testActivityWithDocumentIntoExisting() {
        // Launch a documentLaunchMode="intoExisting" activity
        launchActivityWithData(DOCUMENT_INTO_EXISTING_ACTIVITY, "test");
        waitAndAssertActivityState(DOCUMENT_INTO_EXISTING_ACTIVITY, STATE_RESUMED,
                "Activity should be resumed");
        final int taskId = mWmState.getTaskByActivity(DOCUMENT_INTO_EXISTING_ACTIVITY).getTaskId();

        // Navigate home
        launchHomeActivity();

        // Launch the alias activity.
        final ComponentName componentName = new ComponentName(mContext.getPackageName(),
                DocumentIntoExistingActivity.class.getPackageName()
                        + ".ActivityStarterTests$DocumentIntoExistingAliasActivity");
        launchActivityWithData(componentName, "test");

        waitAndAssertActivityState(DOCUMENT_INTO_EXISTING_ACTIVITY, STATE_RESUMED,
                "Activity should be resumed");
        final int taskId2 = mWmState.getTaskByActivity(DOCUMENT_INTO_EXISTING_ACTIVITY).getTaskId();
        assertEquals("Activity must be in the same task.", taskId, taskId2);
        assertEquals("Activity is the only member of its task", 1,
                mWmState.getActivityCountInTask(taskId2, null));
    }

    /**
     * This test case tests behavior of activity with relinquishTaskIdentify attribute. Ensure the
     * relinquishTaskIdentity work if the activities are in the same app.
     */
    @Test
    public void testActivityWithRelinquishTaskIdentity() {
        // Launch a relinquishTaskIdentity activity and test activity with different affinity into
        // a same task.
        getLaunchActivityBuilder().setTargetActivity(RELINQUISHTASKIDENTITY_ACTIVITY)
                .setIntentExtra(extra -> extra.putBoolean(
                        RelinquishTaskIdentityActivity.EXTRA_LAUNCHED, true))
                .setUseInstrumentation()
                .setWaitForLaunched(true)
                .execute();
        // get the task affinity(e.g. 10825:android.server.wm.cts) without the uid information
        String affinity = mWmState.getTaskByActivity(RELINQUISHTASKIDENTITY_ACTIVITY).getAffinity();
        affinity = affinity.substring(affinity.indexOf(":") + 1);

        final int taskId = mWmState.getTaskByActivity(RELINQUISHTASKIDENTITY_ACTIVITY).getTaskId();
        final int taskId2 = mWmState.getTaskByActivity(TEST_LAUNCHING_ACTIVITY).getTaskId();

        // verify the activities are in the same task
        assertEquals("Activity must be in the same task.", taskId, taskId2);
        // verify the relinquishTaskIdentify function should work since the activities are in the
        // same app
        assertNotEquals("Affinity should not be same with the package name.",
                RELINQUISHTASKIDENTITY_ACTIVITY.getPackageName(), affinity);
    }

    /**
     * This test case tests behavior of activity with launch_adjacent and new_task. Ensure the flags
     * make activity in multi-window mode.
     */
    @Test
    public void testLaunchActivityWithLaunchAdjacentAndNewTask() {
        assumeTrue("Skipping test: no split multi-window support",
                supportsSplitScreenMultiWindow());

        mTaskOrganizer.registerOrganizerIfNeeded();

        // Launch activity with FLAG_ACTIVITY_NEW_TASK and FLAG_ACTIVITY_LAUNCH_ADJACENT.
        launchAndAssertActivityWindowingMode(
                FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_LAUNCH_ADJACENT,
                WINDOWING_MODE_MULTI_WINDOW);
    }

    /**
     * This test case tests behavior of activity with launch_adjacent. Ensure the flag is ignored
     * if without new_task together.
     */
    @Test
    public void testLaunchActivityWithLaunchAdjacent() {
        assumeTrue("Skipping test: no split multi-window support",
                supportsSplitScreenMultiWindow());

        // Launch activity with FLAG_ACTIVITY_LAUNCH_ADJACENT only.
        launchAndAssertActivityWindowingMode(FLAG_ACTIVITY_LAUNCH_ADJACENT,
                WINDOWING_MODE_FULLSCREEN);
    }

    private void launchAndAssertActivityWindowingMode(int flags, int expectWindowingMode) {
        getLaunchActivityBuilder()
                .setTargetActivity(STANDARD_ACTIVITY)
                .setIntentFlags(flags)
                .setWindowingMode(WINDOWING_MODE_FULLSCREEN)
                .execute();

        // wait for the expected windowing mode
        mWmState.waitForValidState(new WaitForValidActivityState.Builder(STANDARD_ACTIVITY)
                .setWindowingMode(expectWindowingMode)
                .build());

        assertEquals(expectWindowingMode,
                mWmState.getActivity(STANDARD_ACTIVITY).getWindowingMode());
    }

    // Test activity
    public static class RelinquishTaskIdentityActivity extends Activity {
        public static final String EXTRA_LAUNCHED = "extraLaunched";
        @Override
        protected void onCreate(Bundle icicle) {
            super.onCreate(icicle);
            if (getIntent().getBooleanExtra(EXTRA_LAUNCHED, false)) {
                startActivityForResult(
                        new Intent(this, TestLaunchingActivity.class), 1 /* requestCode */);
            }
        }
    }

    // Test activity
    public static class StandardWithSingleTopActivity extends Activity {

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            final Intent intent = new Intent().setComponent(
                    new ComponentName(this /* context */, getClass()));
            intent.addFlags(FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
        }
    }

    // Test activity
    public static class ClearTaskOnLaunchActivity extends Activity {
    }

    // Test activity
    public static class FinishOnTaskLaunchActivity extends Activity {
    }

    // Test activity
    public static class SingleTopActivity extends Activity {
    }

    // Test activity
    public static class SingleTaskActivity extends Activity {
    }

    // Test activity
    public static class SingleInstanceActivity extends Activity {
        public static final String EXTRA_SHOW_WHEN_LOCKED = "showWhenLocked";
        @Override
        protected void onCreate(Bundle icicle) {
            super.onCreate(icicle);
            if (getIntent().getBooleanExtra(EXTRA_SHOW_WHEN_LOCKED, false)) {
                setShowWhenLocked(true);
            }
        }
    }

    // Test activity
    public static class DocumentIntoExistingActivity extends Activity {
    }

    // Launching activity
    public static class TestLaunchingActivity extends Activity {
        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            final Intent intent = getIntent();
            if (intent != null) {
                ActivityLauncher.launchActivityFromExtras(this, intent.getExtras());
            }
        }

        @Override
        protected void onNewIntent(Intent intent) {
            super.onNewIntent(intent);
            ActivityLauncher.launchActivityFromExtras(this, intent.getExtras());
        }
    }

    public static class LaunchingAndFinishActivity extends TestLaunchingActivity {
        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            finish();
        }
    }
}
