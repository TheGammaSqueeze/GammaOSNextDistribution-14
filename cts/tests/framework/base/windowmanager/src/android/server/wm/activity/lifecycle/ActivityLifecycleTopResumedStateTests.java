/*
 * Copyright (C) 2019 The Android Open Source Project
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
import static android.app.WindowConfiguration.WINDOWING_MODE_PINNED;
import static android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP;
import static android.content.Intent.FLAG_ACTIVITY_MULTIPLE_TASK;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT;
import static android.server.wm.UiDeviceUtils.pressHomeButton;
import static android.server.wm.activity.lifecycle.LifecycleConstants.EXTRA_FINISH_IN_ON_RESUME;
import static android.server.wm.activity.lifecycle.LifecycleConstants.EXTRA_SKIP_TOP_RESUMED_STATE;
import static android.server.wm.activity.lifecycle.LifecycleConstants.ON_ACTIVITY_RESULT;
import static android.server.wm.activity.lifecycle.LifecycleConstants.ON_CREATE;
import static android.server.wm.activity.lifecycle.LifecycleConstants.ON_DESTROY;
import static android.server.wm.activity.lifecycle.LifecycleConstants.ON_NEW_INTENT;
import static android.server.wm.activity.lifecycle.LifecycleConstants.ON_PAUSE;
import static android.server.wm.activity.lifecycle.LifecycleConstants.ON_POST_CREATE;
import static android.server.wm.activity.lifecycle.LifecycleConstants.ON_RESTART;
import static android.server.wm.activity.lifecycle.LifecycleConstants.ON_RESUME;
import static android.server.wm.activity.lifecycle.LifecycleConstants.ON_START;
import static android.server.wm.activity.lifecycle.LifecycleConstants.ON_STOP;
import static android.server.wm.activity.lifecycle.LifecycleConstants.ON_TOP_POSITION_GAINED;
import static android.server.wm.activity.lifecycle.LifecycleConstants.ON_TOP_POSITION_LOST;
import static android.server.wm.activity.lifecycle.LifecycleConstants.getComponentName;
import static android.server.wm.activity.lifecycle.TransitionVerifier.assertEmptySequence;
import static android.server.wm.activity.lifecycle.TransitionVerifier.assertEntireSequence;
import static android.server.wm.activity.lifecycle.TransitionVerifier.assertLaunchSequence;
import static android.server.wm.activity.lifecycle.TransitionVerifier.assertOrder;
import static android.server.wm.activity.lifecycle.TransitionVerifier.assertRelaunchSequence;
import static android.server.wm.activity.lifecycle.TransitionVerifier.assertResumeToDestroySequence;
import static android.server.wm.activity.lifecycle.TransitionVerifier.assertResumeToStopSequence;
import static android.server.wm.activity.lifecycle.TransitionVerifier.assertSequence;
import static android.server.wm.activity.lifecycle.TransitionVerifier.assertSequenceMatchesOneOf;
import static android.server.wm.activity.lifecycle.TransitionVerifier.assertStopToResumeSequence;
import static android.server.wm.activity.lifecycle.TransitionVerifier.assertStopToResumeSubSequence;
import static android.server.wm.activity.lifecycle.TransitionVerifier.getLaunchSequence;
import static android.server.wm.activity.lifecycle.TransitionVerifier.getRelaunchSequence;
import static android.server.wm.activity.lifecycle.TransitionVerifier.transition;
import static android.server.wm.app.Components.PipActivity.EXTRA_ENTER_PIP;
import static android.view.Display.DEFAULT_DISPLAY;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

import android.app.Activity;
import android.app.ActivityOptions;
import android.content.ComponentName;
import android.content.Intent;
import android.os.SystemClock;
import android.platform.test.annotations.Presubmit;
import android.server.wm.LockScreenSession;
import android.server.wm.WindowManagerState;
import android.server.wm.WindowManagerState.Task;
import android.util.Pair;

import androidx.test.filters.MediumTest;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Tests for {@link Activity#onTopResumedActivityChanged}.
 *
 * Build/Install/Run:
 *     atest CtsWindowManagerDeviceActivity:ActivityLifecycleTopResumedStateTests
 */
@MediumTest
@Presubmit
@android.server.wm.annotation.Group3
public class ActivityLifecycleTopResumedStateTests extends ActivityLifecycleClientTestBase {

    @Before
    public void setUp() throws Exception {
        super.setUp();
    }

    @Test
    public void testTopPositionAfterLaunch() throws Exception {
        launchActivityAndWait(CallbackTrackingActivity.class);

        TransitionVerifier.assertLaunchSequence(CallbackTrackingActivity.class, getTransitionLog());
    }

    @Test
    public void testTopPositionLostOnFinish() throws Exception {
        final Activity activity = launchActivityAndWait(CallbackTrackingActivity.class);

        getTransitionLog().clear();
        activity.finish();
        mWmState.waitForActivityRemoved(getComponentName(CallbackTrackingActivity.class));

        assertResumeToDestroySequence(CallbackTrackingActivity.class, getTransitionLog());
    }

    @Test
    public void testTopPositionSwitchToActivityOnTop() throws Exception {
        final Activity activity = launchActivityAndWait(CallbackTrackingActivity.class);

        getTransitionLog().clear();
        launchActivityAndWait(SingleTopActivity.class);
        waitAndAssertActivityStates(state(activity, ON_STOP));

        assertLaunchSequence(SingleTopActivity.class,
                CallbackTrackingActivity.class, getTransitionLog(),
                false /* launchingIsTranslucent */);
    }

    @Test
    public void testTopPositionSwitchToActivityOnTopSlowDifferentProcess() throws Exception {
        // Launch first activity, which will be slow to release top position
        final Intent slowTopReleaseIntent = new Intent();
        slowTopReleaseIntent.putExtra(SlowActivity.EXTRA_CONTROL_FLAGS,
                SlowActivity.FLAG_SLOW_TOP_RESUME_RELEASE);

        final Activity firstActivity =
                mSlowActivityTestRule.launchActivity(slowTopReleaseIntent);
        waitAndAssertActivityStates(state(firstActivity, ON_TOP_POSITION_GAINED));

        // Launch second activity on top
        getTransitionLog().clear();
        final Class<? extends Activity> secondActivityClass =
                SecondProcessCallbackTrackingActivity.class;
        launchActivity(new ComponentName(firstActivity, secondActivityClass));

        // Wait and assert top position switch
        waitAndAssertActivityStates(state(secondActivityClass, ON_TOP_POSITION_GAINED),
                state(firstActivity, ON_STOP));
        assertOrder(getTransitionLog(), Arrays.asList(
                transition(firstActivity.getClass(), ON_TOP_POSITION_LOST),
                transition(secondActivityClass, ON_TOP_POSITION_GAINED)),
                "launchOnTop");
    }

    @Test
    public void testTopPositionSwitchToActivityOnTopTimeoutDifferentProcess() throws Exception {
        // Launch first activity, which will be slow to release top position
        final Intent slowTopReleaseIntent = new Intent();
        slowTopReleaseIntent.putExtra(SlowActivity.EXTRA_CONTROL_FLAGS,
                SlowActivity.FLAG_TIMEOUT_TOP_RESUME_RELEASE);

        final Activity firstActivity =
                mSlowActivityTestRule.launchActivity(slowTopReleaseIntent);
        waitAndAssertActivityStates(state(firstActivity, ON_TOP_POSITION_GAINED));
        final Class<? extends Activity> firstActivityClass = firstActivity.getClass();

        // Launch second activity on top
        getTransitionLog().clear();
        final Class<? extends Activity> secondActivityClass =
                SecondProcessCallbackTrackingActivity.class;
        launchActivity(new ComponentName(firstActivity, secondActivityClass));

        // Wait and assert top position switch,
        waitAndAssertActivityStates(state(secondActivityClass, ON_TOP_POSITION_GAINED),
                state(firstActivity, ON_STOP));
        assertOrder(getTransitionLog(), Arrays.asList(
                transition(secondActivityClass, ON_TOP_POSITION_GAINED),
                transition(firstActivityClass, ON_TOP_POSITION_LOST)),
                "launchOnTop");

        // Wait 5 seconds more to make sure that no new messages received after top resumed state
        // released by the slow activity
        getTransitionLog().clear();
        Thread.sleep(5000);
        assertEmptySequence(firstActivityClass, getTransitionLog(),
                "topStateLossTimeout");
        assertEmptySequence(secondActivityClass, getTransitionLog(),
                "topStateLossTimeout");
    }

    @Test
    public void testTopPositionSwitchToTranslucentActivityOnTop() throws Exception {
        final Activity activity = launchActivityAndWait(CallbackTrackingActivity.class);

        getTransitionLog().clear();
        launchActivityAndWait(TranslucentCallbackTrackingActivity.class);
        waitAndAssertActivityStates(state(activity, ON_PAUSE));

        assertLaunchSequence(TranslucentCallbackTrackingActivity.class,
                CallbackTrackingActivity.class, getTransitionLog(),
                true /* launchingIsTranslucent */);
    }

    @Test
    public void testTopPositionSwitchOnDoubleLaunch() throws Exception {
        final Activity baseActivity = launchActivityAndWait(CallbackTrackingActivity.class);

        getTransitionLog().clear();
        new Launcher(LaunchForResultActivity.class)
                .setExpectedState(ON_STOP)
                .setNoInstance()
                .launch();

        waitAndAssertActivityStates(state(baseActivity, ON_STOP));

        final List<String> expectedTopActivitySequence = Arrays.asList(
                ON_CREATE, ON_START, ON_POST_CREATE, ON_RESUME, ON_TOP_POSITION_GAINED);
        waitForActivityTransitions(ResultActivity.class, expectedTopActivitySequence);

        final List<Pair<String, String>> observedTransitions =
                getTransitionLog().getLog();
        final List<Pair<String, String>> expectedTransitions = new ArrayList<>();
        Collections.addAll(expectedTransitions,
                transition(CallbackTrackingActivity.class, ON_TOP_POSITION_LOST),
                transition(CallbackTrackingActivity.class, ON_PAUSE),
                transition(LaunchForResultActivity.class, ON_CREATE),
                transition(LaunchForResultActivity.class, ON_START),
                transition(LaunchForResultActivity.class, ON_POST_CREATE),
                transition(LaunchForResultActivity.class, ON_RESUME),
                transition(LaunchForResultActivity.class, ON_TOP_POSITION_GAINED),
                transition(LaunchForResultActivity.class, ON_TOP_POSITION_LOST),
                transition(LaunchForResultActivity.class, ON_PAUSE),
                transition(ResultActivity.class, ON_CREATE),
                transition(ResultActivity.class, ON_START),
                transition(ResultActivity.class, ON_POST_CREATE),
                transition(ResultActivity.class, ON_RESUME),
                transition(ResultActivity.class, ON_TOP_POSITION_GAINED));
        if (ENABLE_SHELL_TRANSITIONS) {
            // With shell-transition, all changes are collected and being part of the transition,
            // so the trampoline activity (LaunchForResultActivity) will be in animating state and
            // be stopped after the transition completed. Therefore, it won't be stopped
            // prior than the CallbackTrackingActivity.
            expectedTransitions.add(transition(CallbackTrackingActivity.class, ON_STOP));
            expectedTransitions.add(transition(LaunchForResultActivity.class, ON_STOP));
        } else {
            expectedTransitions.add(transition(LaunchForResultActivity.class, ON_STOP));
            expectedTransitions.add(transition(CallbackTrackingActivity.class, ON_STOP));
        }
        assertEquals("Double launch sequence must match", expectedTransitions, observedTransitions);
    }

    @Test
    public void testTopPositionSwitchOnDoubleLaunchAndTopFinish() throws Exception {
        final Activity baseActivity = launchActivityAndWait(CallbackTrackingActivity.class);

        getTransitionLog().clear();
        new Launcher(LaunchForResultActivity.class)
                .customizeIntent(LaunchForResultActivity.forwardFlag(EXTRA_FINISH_IN_ON_RESUME,
                        EXTRA_SKIP_TOP_RESUMED_STATE))
                // Start the TranslucentResultActivity to avoid activity below stopped sometimes
                // and resulted in different lifecycle events.
                .setExtraFlags(LaunchForResultActivity.EXTRA_USE_TRANSLUCENT_RESULT)
                .launch();

        waitAndAssertActivityStates(state(baseActivity, ON_STOP));
        final List<String> expectedLaunchingSequence =
                Arrays.asList(ON_CREATE, ON_START, ON_POST_CREATE, ON_RESUME,
                        ON_TOP_POSITION_GAINED, ON_TOP_POSITION_LOST, ON_PAUSE,
                        ON_ACTIVITY_RESULT, ON_RESUME, ON_TOP_POSITION_GAINED);
        waitForActivityTransitions(LaunchForResultActivity.class, expectedLaunchingSequence);

        final List<String> expectedTopActivitySequence = Arrays.asList(ON_CREATE,
                ON_START, ON_POST_CREATE, ON_RESUME, ON_TOP_POSITION_GAINED);
        waitForActivityTransitions(TranslucentResultActivity.class, expectedTopActivitySequence);

        final List<Pair<String, String>> expectedTransitions = new ArrayList<>();
        Collections.addAll(expectedTransitions,
                transition(CallbackTrackingActivity.class, ON_TOP_POSITION_LOST),
                transition(CallbackTrackingActivity.class, ON_PAUSE),
                transition(LaunchForResultActivity.class, ON_CREATE),
                transition(LaunchForResultActivity.class, ON_START),
                transition(LaunchForResultActivity.class, ON_POST_CREATE),
                transition(LaunchForResultActivity.class, ON_RESUME),
                transition(LaunchForResultActivity.class, ON_TOP_POSITION_GAINED),
                transition(LaunchForResultActivity.class, ON_TOP_POSITION_LOST),
                transition(LaunchForResultActivity.class, ON_PAUSE),
                transition(TranslucentResultActivity.class, ON_CREATE),
                transition(TranslucentResultActivity.class, ON_START),
                transition(TranslucentResultActivity.class, ON_POST_CREATE),
                transition(TranslucentResultActivity.class, ON_RESUME),
                transition(TranslucentResultActivity.class, ON_PAUSE),
                transition(LaunchForResultActivity.class, ON_ACTIVITY_RESULT),
                transition(LaunchForResultActivity.class, ON_RESUME),
                transition(LaunchForResultActivity.class, ON_TOP_POSITION_GAINED));
        if (ENABLE_SHELL_TRANSITIONS) {
            // With shell-transition, all changes are collected and being part of the transition,
            // so the TranslucentResultActivity will be in animating state and be stopped after
            // the transition completed. Therefore, it won't be stopped prior than
            // the CallbackTrackingActivity.
            expectedTransitions.add(transition(CallbackTrackingActivity.class, ON_STOP));
            expectedTransitions.add(transition(TranslucentResultActivity.class, ON_STOP));
            expectedTransitions.add(transition(TranslucentResultActivity.class, ON_DESTROY));
        } else {
            expectedTransitions.add(transition(TranslucentResultActivity.class, ON_STOP));
            expectedTransitions.add(transition(TranslucentResultActivity.class, ON_DESTROY));
            expectedTransitions.add(transition(CallbackTrackingActivity.class, ON_STOP));
        }
        assertEntireSequence(expectedTransitions, getTransitionLog(),
                "Double launch sequence must match");
    }

    @Test
    public void testTopPositionLostWhenDocked() throws Exception {
        assumeTrue(supportsSplitScreenMultiWindow());

        // Launch an activity that will be moved to split-screen secondary
        final Activity sideActivity = launchActivityAndWait(SideActivity.class);

        // Launch first activity
        final Activity firstActivity = launchActivityAndWait(CallbackTrackingActivity.class);

        // Enter split screen
        moveTaskToPrimarySplitScreenAndVerify(firstActivity, sideActivity);
    }

    @Test
    public void testTopPositionSwitchToAnotherVisibleActivity() throws Exception {
        assumeTrue(supportsSplitScreenMultiWindow());

        // Launch side activity
        final Activity sideActivity = launchActivityAndWait(SideActivity.class);

        // Launch first activity
        final Activity firstActivity = launchActivityAndWait(CallbackTrackingActivity.class);

        // Enter split screen
        moveTaskToPrimarySplitScreenAndVerify(firstActivity, sideActivity);

        // Launch second activity to side
        getTransitionLog().clear();
        new Launcher(SingleTopActivity.class)
                .setFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_MULTIPLE_TASK)
                .launch();

        // Second activity must be on top now
        TransitionVerifier.assertLaunchSequence(SingleTopActivity.class, getTransitionLog());
    }

    @Test
    public void testTopPositionSwitchBetweenVisibleActivities() throws Exception {
        assumeTrue(supportsSplitScreenMultiWindow());

        // Launch side activity
        final Activity sideActivity = launchActivityAndWait(SideActivity.class);

        // Launch first activity
        final Activity firstActivity = launchActivityAndWait(CallbackTrackingActivity.class);

        // Enter split screen
        moveTaskToPrimarySplitScreenAndVerify(firstActivity, sideActivity);

        // Launch second activity to side
        getTransitionLog().clear();
        mTaskOrganizer.setLaunchRoot(mTaskOrganizer.getSecondarySplitTaskId());
        final Activity secondActivity = new Launcher(SingleTopActivity.class)
                .setFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_MULTIPLE_TASK)
                .launch();

        // Switch top between two activities
        getTransitionLog().clear();
        mTaskOrganizer.setLaunchRoot(mTaskOrganizer.getPrimarySplitTaskId());
        new Launcher(CallbackTrackingActivity.class)
                .setFlags(FLAG_ACTIVITY_NEW_TASK)
                .setNoInstance()
                .launch();

        waitAndAssertActivityStates(state(firstActivity, ON_TOP_POSITION_GAINED),
                state(secondActivity, ON_TOP_POSITION_LOST));
        assertSequence(CallbackTrackingActivity.class, getTransitionLog(),
                Collections.singletonList(ON_TOP_POSITION_GAINED), "switchTop");
        assertSequence(SingleTopActivity.class, getTransitionLog(),
                Collections.singletonList(ON_TOP_POSITION_LOST), "switchTop");

        // Switch top again
        getTransitionLog().clear();
        mTaskOrganizer.setLaunchRoot(mTaskOrganizer.getSecondarySplitTaskId());
        new Launcher(SingleTopActivity.class)
                .setFlags(FLAG_ACTIVITY_NEW_TASK)
                .setNoInstance()
                .launch();

        waitAndAssertActivityStates(state(firstActivity, ON_TOP_POSITION_LOST),
                state(secondActivity, ON_TOP_POSITION_GAINED));
        assertSequence(CallbackTrackingActivity.class, getTransitionLog(),
                Collections.singletonList(ON_TOP_POSITION_LOST), "switchTop");
        assertSequence(SingleTopActivity.class, getTransitionLog(),
                Arrays.asList(ON_PAUSE, ON_NEW_INTENT, ON_RESUME, ON_TOP_POSITION_GAINED),
                "switchTop");
    }

    @Test
    public void testTopPositionNewIntent() throws Exception {
        // Launch single top activity
        launchActivityAndWait(SingleTopActivity.class);

        // Launch the activity again to observe new intent
        getTransitionLog().clear();
        new Launcher(SingleTopActivity.class)
                .setFlags(FLAG_ACTIVITY_NEW_TASK)
                .setNoInstance()
                .launch();

        waitAndAssertActivityTransitions(SingleTopActivity.class,
                Arrays.asList(
                        ON_TOP_POSITION_LOST, ON_PAUSE, ON_NEW_INTENT, ON_RESUME,
                        ON_TOP_POSITION_GAINED), "newIntent");
    }

    @Test
    public void testTopPositionNewIntentForStopped() throws Exception {
        // Launch single top activity
        final Activity singleTopActivity = launchActivityAndWait(SingleTopActivity.class);

        // Launch another activity on top
        final Activity topActivity = launchActivityAndWait(CallbackTrackingActivity.class);
        waitAndAssertActivityStates(state(singleTopActivity, ON_STOP));

        // Launch the single top activity again to observe new intent
        getTransitionLog().clear();
        new Launcher(SingleTopActivity.class)
                .setFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TOP)
                .setNoInstance()
                .launch();

        waitAndAssertActivityStates(state(topActivity, ON_DESTROY));

        assertEntireSequence(Arrays.asList(
                transition(CallbackTrackingActivity.class, ON_TOP_POSITION_LOST),
                transition(CallbackTrackingActivity.class, ON_PAUSE),
                transition(SingleTopActivity.class, ON_RESTART),
                transition(SingleTopActivity.class, ON_START),
                transition(SingleTopActivity.class, ON_NEW_INTENT),
                transition(SingleTopActivity.class, ON_RESUME),
                transition(SingleTopActivity.class, ON_TOP_POSITION_GAINED),
                transition(CallbackTrackingActivity.class, ON_STOP),
                transition(CallbackTrackingActivity.class, ON_DESTROY)),
                getTransitionLog(), "Single top resolution sequence must match");
    }

    @Test
    public void testTopPositionNewIntentForPaused() throws Exception {
        // Launch single top activity
        final Activity singleTopActivity = launchActivityAndWait(SingleTopActivity.class);

        // Launch a translucent activity on top
        final Activity topActivity =
                launchActivityAndWait(TranslucentCallbackTrackingActivity.class);
        waitAndAssertActivityStates(state(singleTopActivity, ON_PAUSE));

        // Launch the single top activity again to observe new intent
        getTransitionLog().clear();
        new Launcher(SingleTopActivity.class)
                .setFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TOP)
                .setNoInstance()
                .launch();

        waitAndAssertActivityStates(state(topActivity, ON_DESTROY));

        assertOrder(getTransitionLog(), Arrays.asList(
                transition(TranslucentCallbackTrackingActivity.class, ON_TOP_POSITION_LOST),
                transition(TranslucentCallbackTrackingActivity.class, ON_PAUSE),
                transition(SingleTopActivity.class, ON_NEW_INTENT),
                transition(SingleTopActivity.class, ON_RESUME),
                transition(SingleTopActivity.class, ON_TOP_POSITION_GAINED)),
                "Single top resolution sequence must match");
    }

    @Test
    public void testTopPositionSwitchWhenGoingHome() throws Exception {
        final Activity topActivity = launchActivityAndWait(CallbackTrackingActivity.class);

        // Press HOME and verify the lifecycle
        getTransitionLog().clear();
        pressHomeButton();
        waitAndAssertActivityStates(state(topActivity, ON_STOP));

        assertResumeToStopSequence(CallbackTrackingActivity.class, getTransitionLog());
    }

    @Test
    public void testTopPositionSwitchOnTap() throws Exception {
        assumeTrue(supportsSplitScreenMultiWindow());

        // Launch side activity
        final Activity sideActivity = launchActivityAndWait(SideActivity.class);

        // Launch first activity
        final Activity firstActivity = launchActivityAndWait(CallbackTrackingActivity.class);

        // Enter split screen
        moveTaskToPrimarySplitScreenAndVerify(firstActivity, sideActivity);

        // Launch second activity to side
        getTransitionLog().clear();
        mTaskOrganizer.setLaunchRoot(mTaskOrganizer.getSecondarySplitTaskId());
        final Activity secondActivity = new Launcher(SingleTopActivity.class)
                .setFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_MULTIPLE_TASK)
                .launch();
        waitAndAssertActivityStates(state(sideActivity, ON_STOP));

        // Tap on first activity to switch the focus
        getTransitionLog().clear();
        final Task dockedTask = getRootTaskForLeafTaskId(firstActivity.getTaskId());
        tapOnTaskCenter(dockedTask);

        // Wait and assert focus switch
        waitAndAssertActivityStates(state(firstActivity, ON_TOP_POSITION_GAINED),
                state(secondActivity, ON_TOP_POSITION_LOST));
        assertEntireSequence(Arrays.asList(
                transition(SingleTopActivity.class, ON_TOP_POSITION_LOST),
                transition(CallbackTrackingActivity.class, ON_TOP_POSITION_GAINED)),
                getTransitionLog(), "Single top resolution sequence must match");

        // Tap on second activity to switch the focus again
        getTransitionLog().clear();
        final Task sideTask = getRootTaskForLeafTaskId(secondActivity.getTaskId());
        tapOnTaskCenter(sideTask);

        // Wait and assert focus switch
        waitAndAssertActivityStates(state(firstActivity, ON_TOP_POSITION_LOST),
                state(secondActivity, ON_TOP_POSITION_GAINED));
        assertEntireSequence(Arrays.asList(
                transition(CallbackTrackingActivity.class, ON_TOP_POSITION_LOST),
                transition(SingleTopActivity.class, ON_TOP_POSITION_GAINED)),
                getTransitionLog(), "Single top resolution sequence must match");
    }

    @Test
    public void testTopPositionSwitchOnTapSlowDifferentProcess() throws Exception {
        assumeTrue(supportsSplitScreenMultiWindow());

        // Launch side activity
        final Activity sideActivity = launchActivityAndWait(SideActivity.class);

        // Launch first activity
        final Intent slowTopReleaseIntent = new Intent();
        slowTopReleaseIntent.putExtra(SlowActivity.EXTRA_CONTROL_FLAGS,
                SlowActivity.FLAG_SLOW_TOP_RESUME_RELEASE);
        final Activity firstActivity =
                mSlowActivityTestRule.launchActivity(slowTopReleaseIntent);
        waitAndAssertActivityStates(state(firstActivity, ON_TOP_POSITION_GAINED));
        final Class<? extends Activity> firstActivityClass = firstActivity.getClass();

        // Enter split screen
        moveTaskToPrimarySplitScreenAndVerify(firstActivity, sideActivity);

        // Launch second activity to side
        getTransitionLog().clear();
        mTaskOrganizer.setLaunchRoot(mTaskOrganizer.getSecondarySplitTaskId());
        final Class<? extends Activity> secondActivityClass =
                SecondProcessCallbackTrackingActivity.class;
        final ComponentName secondActivityComponent =
                new ComponentName(firstActivity, secondActivityClass);
        getLaunchActivityBuilder()
                .setTargetActivity(secondActivityComponent)
                .setUseInstrumentation()
                .setNewTask(true)
                .setMultipleTask(true)
                .execute();

        // Wait and assert top position switch
        waitAndAssertActivityStates(state(secondActivityClass, ON_TOP_POSITION_GAINED));

        // Tap on first activity to switch the top resumed one
        getTransitionLog().clear();
        final Task dockedTask = getRootTaskForLeafTaskId(firstActivity.getTaskId());
        tapOnTaskCenter(dockedTask);

        // Wait and assert top resumed position switch
        waitAndAssertActivityStates(state(secondActivityClass, ON_TOP_POSITION_LOST),
                state(firstActivityClass, ON_TOP_POSITION_GAINED));
        assertOrder(getTransitionLog(), Arrays.asList(
                transition(secondActivityClass, ON_TOP_POSITION_LOST),
                transition(firstActivityClass, ON_TOP_POSITION_GAINED)),
                "tapOnTask");

        // Tap on second activity to switch the top resumed activity again
        getTransitionLog().clear();
        final Task sideTask = mWmState.getTaskByActivity(secondActivityComponent);
        tapOnTaskCenter(sideTask);

        // Wait and assert top resumed position switch
        waitAndAssertActivityStates(state(secondActivityClass, ON_TOP_POSITION_GAINED),
                state(firstActivityClass, ON_TOP_POSITION_LOST));
        assertOrder(getTransitionLog(), Arrays.asList(
                transition(firstActivityClass, ON_TOP_POSITION_LOST),
                transition(secondActivityClass, ON_TOP_POSITION_GAINED)),
                "tapOnTask");
    }

    @Test
    public void testTopPositionSwitchOnTapTimeoutDifferentProcess() throws Exception {
        assumeTrue(supportsSplitScreenMultiWindow());

        // Launch side activity
        final Activity sideActivity = launchActivityAndWait(SideActivity.class);

        // Launch first activity
        final Intent slowTopReleaseIntent = new Intent();
        slowTopReleaseIntent.putExtra(SlowActivity.EXTRA_CONTROL_FLAGS,
                SlowActivity.FLAG_TIMEOUT_TOP_RESUME_RELEASE);
        final Activity slowActivity =
                mSlowActivityTestRule.launchActivity(slowTopReleaseIntent);
        waitAndAssertActivityStates(state(slowActivity, ON_TOP_POSITION_GAINED));
        final Class<? extends Activity> slowActivityClass = slowActivity.getClass();

        // Enter split screen
        moveTaskToPrimarySplitScreenAndVerify(slowActivity, sideActivity);

        // Launch second activity to side
        getTransitionLog().clear();
        mTaskOrganizer.setLaunchRoot(mTaskOrganizer.getSecondarySplitTaskId());
        final Class<? extends Activity> secondActivityClass =
                SecondProcessCallbackTrackingActivity.class;
        final ComponentName secondActivityComponent =
                new ComponentName(slowActivity, secondActivityClass);
        getLaunchActivityBuilder()
                .setTargetActivity(secondActivityComponent)
                .setUseInstrumentation()
                .setNewTask(true)
                .setMultipleTask(true)
                .execute();

        // Wait and assert top position switch
        waitAndAssertActivityStates(state(secondActivityClass, ON_TOP_POSITION_GAINED));

        // Tap on first activity to switch the top resumed one
        getTransitionLog().clear();
        final Task dockedTask = getRootTaskForLeafTaskId(slowActivity.getTaskId());
        tapOnTaskCenter(dockedTask);

        // Wait and assert top resumed position switch.
        waitAndAssertActivityStates(state(secondActivityClass, ON_TOP_POSITION_LOST),
                state(slowActivityClass, ON_TOP_POSITION_GAINED));
        assertOrder(getTransitionLog(), Arrays.asList(
                transition(secondActivityClass, ON_TOP_POSITION_LOST),
                transition(slowActivityClass, ON_TOP_POSITION_GAINED)),
                "tapOnTask");

        // Tap on second activity to switch the top resumed activity again
        getTransitionLog().clear();
        final Task sideTask = mWmState
                .getTaskByActivity(secondActivityComponent);
        tapOnTaskCenter(sideTask);

        // Wait and assert top resumed position switch. Because of timeout the new top position will
        // be reported to the first activity before second will finish handling it.
        waitAndAssertActivityStates(state(secondActivityClass, ON_TOP_POSITION_GAINED),
                state(slowActivityClass, ON_TOP_POSITION_LOST));
        assertOrder(getTransitionLog(), Arrays.asList(
                transition(secondActivityClass, ON_TOP_POSITION_GAINED),
                transition(slowActivityClass, ON_TOP_POSITION_LOST)),
                "tapOnTask");

        // Wait 5 seconds more to make sure that no new messages received after top resumed state
        // released by the slow activity
        getTransitionLog().clear();
        Thread.sleep(5000);
        assertEmptySequence(slowActivityClass, getTransitionLog(),
                "topStateLossTimeout");
        assertEmptySequence(secondActivityClass, getTransitionLog(),
                "topStateLossTimeout");
    }

    @Test
    public void testTopPositionPreservedOnLocalRelaunch() throws Exception {
        final Activity activity = launchActivityAndWait(CallbackTrackingActivity.class);

        getTransitionLog().clear();
        getInstrumentation().runOnMainSync(activity::recreate);
        waitAndAssertActivityStates(state(activity, ON_TOP_POSITION_GAINED));

        assertRelaunchSequence(CallbackTrackingActivity.class, getTransitionLog(),
                ON_TOP_POSITION_GAINED);
    }

    @Test
    public void testTopPositionLaunchedBehindLockScreen() throws Exception {
        assumeTrue(supportsSecureLock());

        try (LockScreenSession lockScreenSession =
                    new LockScreenSession(mInstrumentation, mWmState)) {
            lockScreenSession.setLockCredential().gotoKeyguard();

            new Launcher(CallbackTrackingActivity.class)
                    .setExpectedState(ON_STOP)
                    .setNoInstance()
                    .launch();
            TransitionVerifier.assertLaunchAndStopSequence(
                    CallbackTrackingActivity.class, getTransitionLog(), true /* onTop */);

            getTransitionLog().clear();
        }

        // Lock screen removed - activity should be on top now
        if (isCar()) {
            assertStopToResumeSubSequence(CallbackTrackingActivity.class, getTransitionLog());
            waitAndAssertActivityCurrentState(CallbackTrackingActivity.class,
                    ON_TOP_POSITION_GAINED);
        } else {
            waitAndAssertActivityStates(
                    state(CallbackTrackingActivity.class, ON_TOP_POSITION_GAINED));
            assertStopToResumeSequence(CallbackTrackingActivity.class, getTransitionLog());
        }
    }

    @Test
    public void testTopPositionRemovedBehindLockScreen() throws Exception {
        assumeTrue(supportsSecureLock());

        final Activity activity = launchActivityAndWait(CallbackTrackingActivity.class);

        getTransitionLog().clear();
        try (LockScreenSession lockScreenSession =
                    new LockScreenSession(mInstrumentation, mWmState)) {
            lockScreenSession.setLockCredential().gotoKeyguard();

            waitAndAssertActivityStates(state(activity, ON_STOP));
            assertResumeToStopSequence(CallbackTrackingActivity.class,
                    getTransitionLog());

            getTransitionLog().clear();
        }

        // Lock screen removed - activity should be on top now
        if (isCar()) {
            assertStopToResumeSubSequence(CallbackTrackingActivity.class,
                    getTransitionLog());
            waitAndAssertActivityCurrentState(activity.getClass(), ON_TOP_POSITION_GAINED);
        } else {
            waitAndAssertActivityStates(state(activity, ON_TOP_POSITION_GAINED));
            assertStopToResumeSequence(CallbackTrackingActivity.class,
                    getTransitionLog());
        }
    }

    @Test
    public void testTopPositionLaunchedOnTopOfLockScreen() throws Exception {
        assumeTrue(supportsSecureLock());

        final Activity showWhenLockedActivity;
        try (LockScreenSession lockScreenSession =
                    new LockScreenSession(mInstrumentation, mWmState)) {
            lockScreenSession.setLockCredential().gotoKeyguard();

            ActivityOptions options = ActivityOptions.makeBasic();
            options.setLaunchWindowingMode(WINDOWING_MODE_FULLSCREEN);
            showWhenLockedActivity = new Launcher(ShowWhenLockedCallbackTrackingActivity.class)
                                .setOptions(options)
                                .launch();

            final List<String> expectedSequence =
                    Arrays.asList(ON_CREATE, ON_START, ON_POST_CREATE, ON_RESUME,
                            ON_TOP_POSITION_GAINED, ON_TOP_POSITION_LOST, ON_PAUSE, ON_RESUME,
                            ON_TOP_POSITION_GAINED);
            final List<String> extraSequence =
                    Arrays.asList(ON_CREATE, ON_START, ON_POST_CREATE, ON_RESUME,
                            ON_TOP_POSITION_GAINED, ON_TOP_POSITION_LOST, ON_PAUSE, ON_STOP,
                            ON_RESTART, ON_START, ON_RESUME, ON_TOP_POSITION_GAINED);
            assertSequenceMatchesOneOf(ShowWhenLockedCallbackTrackingActivity.class,
                    getTransitionLog(),
                    Arrays.asList(expectedSequence, extraSequence),
                    "launchAboveKeyguard");

            getTransitionLog().clear();
        }

        // When the lock screen is removed, the ShowWhenLocked activity will be dismissed using the
        // back button, which should finish the activity.
        waitAndAssertActivityStates(state(showWhenLockedActivity, ON_DESTROY));
        assertResumeToDestroySequence(
                ShowWhenLockedCallbackTrackingActivity.class, getTransitionLog());
    }

    @Test
    public void testTopPositionSwitchAcrossDisplays() throws Exception {
        assumeTrue(supportsMultiDisplay());

        // Launch activity on default display.
        final ActivityOptions launchOptions = ActivityOptions.makeBasic();
        launchOptions.setLaunchDisplayId(DEFAULT_DISPLAY);
        new Launcher(CallbackTrackingActivity.class)
                .setFlags(FLAG_ACTIVITY_NEW_TASK)
                .setOptions(launchOptions)
                .launch();

        waitAndAssertTopResumedActivity(getComponentName(CallbackTrackingActivity.class),
                DEFAULT_DISPLAY, "Activity launched on default display must be focused");
        waitAndAssertActivityTransitions(CallbackTrackingActivity.class,
                getLaunchSequence(CallbackTrackingActivity.class), "launch");

        try (final VirtualDisplaySession virtualDisplaySession = new VirtualDisplaySession()) {
            // Create new simulated display
            final WindowManagerState.DisplayContent newDisplay
                    = virtualDisplaySession.setSimulateDisplay(true).createDisplay();

            // Launch another activity on new secondary display.
            getTransitionLog().clear();
            launchOptions.setLaunchDisplayId(newDisplay.mId);
            new Launcher(SingleTopActivity.class)
                    .setFlags(FLAG_ACTIVITY_NEW_TASK)
                    .setOptions(launchOptions)
                    .launch();
            waitAndAssertTopResumedActivity(getComponentName(SingleTopActivity.class),
                    newDisplay.mId, "Activity launched on secondary display must be focused");

            waitAndAssertActivityTransitions(SingleTopActivity.class,
                    getLaunchSequence(SingleTopActivity.class), "launch");
            assertOrder(getTransitionLog(), Arrays.asList(
                    transition(CallbackTrackingActivity.class, ON_TOP_POSITION_LOST),
                    transition(SingleTopActivity.class, ON_TOP_POSITION_GAINED)),
                    "launchOnOtherDisplay");

            getTransitionLog().clear();
        }

        // Secondary display was removed - activity will be moved to the default display
        waitForActivityTransitions(SingleTopActivity.class,
                getRelaunchSequence(ON_TOP_POSITION_GAINED));
        assertOrder(getTransitionLog(), Arrays.asList(
                transition(SingleTopActivity.class, ON_TOP_POSITION_LOST),
                transition(SingleTopActivity.class, ON_TOP_POSITION_GAINED)),
                "hostingDisplayRemoved");
    }

    @Test
    public void testTopPositionSwitchAcrossDisplaysOnTap() throws Exception {
        assumeTrue(supportsMultiDisplay());

        // Launch activity on default display.
        final ActivityOptions launchOptions = ActivityOptions.makeBasic();
        launchOptions.setLaunchDisplayId(DEFAULT_DISPLAY);
        new Launcher(CallbackTrackingActivity.class)
                .setFlags(FLAG_ACTIVITY_NEW_TASK)
                .setOptions(launchOptions)
                .launch();

        waitAndAssertTopResumedActivity(getComponentName(CallbackTrackingActivity.class),
                DEFAULT_DISPLAY, "Activity launched on default display must be focused");

        // Create new simulated display
        final WindowManagerState.DisplayContent newDisplay = createManagedVirtualDisplaySession()
                .setSimulateDisplay(true)
                .createDisplay();

        // Launch another activity on new secondary display.
        getTransitionLog().clear();
        launchOptions.setLaunchDisplayId(newDisplay.mId);
        new Launcher(SingleTopActivity.class)
                .setFlags(FLAG_ACTIVITY_NEW_TASK)
                .setOptions(launchOptions)
                .launch();
        waitAndAssertTopResumedActivity(getComponentName(SingleTopActivity.class),
                newDisplay.mId, "Activity launched on secondary display must be focused");

        getTransitionLog().clear();

        // Tap on task center to switch the top activity.
        final Task callbackTrackingTask = mWmState
                .getTaskByActivity(getComponentName(CallbackTrackingActivity.class));
        tapOnTaskCenter(callbackTrackingTask);

        // Wait and assert focus switch
        waitAndAssertActivityTransitions(SingleTopActivity.class,
                Arrays.asList(ON_TOP_POSITION_LOST), "tapOnFocusSwitch");
        waitAndAssertActivityTransitions(CallbackTrackingActivity.class,
                Arrays.asList(ON_TOP_POSITION_GAINED), "tapOnFocusSwitch");
        assertEntireSequence(Arrays.asList(
                transition(SingleTopActivity.class, ON_TOP_POSITION_LOST),
                transition(CallbackTrackingActivity.class, ON_TOP_POSITION_GAINED)),
                getTransitionLog(), "Top activity must be switched on tap");

        getTransitionLog().clear();

        // Tap on task center to switch the top activity.
        final Task singleTopActivityTask = mWmState
                .getTaskByActivity(getComponentName(SingleTopActivity.class));
        tapOnTaskCenter(singleTopActivityTask);


        // Wait and assert focus switch
        waitAndAssertActivityTransitions(CallbackTrackingActivity.class,
                Arrays.asList(ON_TOP_POSITION_LOST), "tapOnFocusSwitch");
        waitAndAssertActivityTransitions(SingleTopActivity.class,
                Arrays.asList(ON_TOP_POSITION_GAINED), "tapOnFocusSwitch");
        assertEntireSequence(Arrays.asList(
                transition(CallbackTrackingActivity.class, ON_TOP_POSITION_LOST),
                transition(SingleTopActivity.class, ON_TOP_POSITION_GAINED)),
                getTransitionLog(), "Top activity must be switched on tap");
    }

    @Test
    public void testTopPositionSwitchAcrossDisplaysOnTapSlowDifferentProcess() {
        assumeTrue(supportsMultiDisplay());

        // Create new simulated display.
        final WindowManagerState.DisplayContent newDisplay = createManagedVirtualDisplaySession()
                .setSimulateDisplay(true)
                .createDisplay();

        // Launch an activity on new secondary display.
        final Class<? extends Activity> secondActivityClass =
                SecondProcessCallbackTrackingActivity.class;
        final ComponentName secondActivityComponent =
                new ComponentName(mTargetContext, secondActivityClass);

        getLaunchActivityBuilder()
                .setTargetActivity(secondActivityComponent)
                .setUseInstrumentation()
                .setDisplayId(newDisplay.mId)
                .execute();
        waitAndAssertActivityStates(state(secondActivityClass, ON_TOP_POSITION_GAINED));

        // Launch activity on default display, which will be slow to release top position.
        getTransitionLog().clear();
        final ActivityOptions launchOptions = ActivityOptions.makeBasic();
        launchOptions.setLaunchDisplayId(DEFAULT_DISPLAY);
        final Class<? extends Activity> defaultActivityClass = SlowActivity.class;
        final Intent defaultDisplaySlowIntent = new Intent(mContext, defaultActivityClass);
        defaultDisplaySlowIntent.setFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_MULTIPLE_TASK);
        defaultDisplaySlowIntent.putExtra(SlowActivity.EXTRA_CONTROL_FLAGS,
                SlowActivity.FLAG_SLOW_TOP_RESUME_RELEASE);
        mTargetContext.startActivity(defaultDisplaySlowIntent, launchOptions.toBundle());

        waitAndAssertTopResumedActivity(getComponentName(SlowActivity.class),
                DEFAULT_DISPLAY, "Activity launched on default display must be focused");

        // Wait and assert focus switch
        waitAndAssertActivityStates(state(secondActivityClass, ON_TOP_POSITION_LOST),
                state(defaultActivityClass, ON_TOP_POSITION_GAINED));
        assertOrder(getTransitionLog(), Arrays.asList(
                transition(secondActivityClass, ON_TOP_POSITION_LOST),
                transition(defaultActivityClass, ON_TOP_POSITION_GAINED)),
                "launchOnDifferentDisplay");

        // Tap on task center to switch the top activity.
        getTransitionLog().clear();
        final Task secondActivityTask = mWmState
                .getTaskByActivity(getComponentName(SecondProcessCallbackTrackingActivity.class));
        tapOnTaskCenter(secondActivityTask);

        // Wait and assert top resumed position switch.
        waitAndAssertActivityStates(state(secondActivityClass, ON_TOP_POSITION_GAINED),
                state(defaultActivityClass, ON_TOP_POSITION_LOST));
        assertOrder(getTransitionLog(), Arrays.asList(
                transition(defaultActivityClass, ON_TOP_POSITION_LOST),
                transition(secondActivityClass, ON_TOP_POSITION_GAINED)),
                "tapOnDifferentDisplay");

        getTransitionLog().clear();
        // Tap on task center to switch the top activity.
        final Task defaultActivityTask = mWmState
                .getTaskByActivity(getComponentName(defaultActivityClass));
        tapOnTaskCenter(defaultActivityTask);

        // Wait and assert top resumed position switch.
        waitAndAssertActivityStates(state(secondActivityClass, ON_TOP_POSITION_LOST),
                state(defaultActivityClass, ON_TOP_POSITION_GAINED));
        assertOrder(getTransitionLog(), Arrays.asList(
                transition(secondActivityClass, ON_TOP_POSITION_LOST),
                transition(defaultActivityClass, ON_TOP_POSITION_GAINED)),
                "tapOnDifferentDisplay");
    }

    @Test
    public void testTopPositionSwitchAcrossDisplaysOnTapTimeoutDifferentProcess() {
        assumeTrue(supportsMultiDisplay());

        // Create new simulated display.
        final WindowManagerState.DisplayContent newDisplay = createManagedVirtualDisplaySession()
                .setSimulateDisplay(true)
                .createDisplay();

        // Launch an activity on new secondary display.
        final Class<? extends Activity> secondActivityClass =
                SecondProcessCallbackTrackingActivity.class;
        final ComponentName secondActivityComponent =
                new ComponentName(mTargetContext, secondActivityClass);

        getLaunchActivityBuilder()
                .setTargetActivity(secondActivityComponent)
                .setUseInstrumentation()
                .setDisplayId(newDisplay.mId)
                .execute();
        waitAndAssertActivityStates(state(secondActivityClass, ON_TOP_POSITION_GAINED));

        // Launch activity on default display, which will be slow to release top position.
        getTransitionLog().clear();
        final ActivityOptions launchOptions = ActivityOptions.makeBasic();
        launchOptions.setLaunchDisplayId(DEFAULT_DISPLAY);
        final Class<? extends Activity> defaultActivityClass = SlowActivity.class;
        final Intent defaultDisplaySlowIntent = new Intent(mContext, defaultActivityClass);
        defaultDisplaySlowIntent.setFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_MULTIPLE_TASK);
        defaultDisplaySlowIntent.putExtra(SlowActivity.EXTRA_CONTROL_FLAGS,
                SlowActivity.FLAG_TIMEOUT_TOP_RESUME_RELEASE);
        mTargetContext.startActivity(defaultDisplaySlowIntent, launchOptions.toBundle());

        waitAndAssertTopResumedActivity(getComponentName(SlowActivity.class),
                DEFAULT_DISPLAY, "Activity launched on default display must be focused");

        // Wait and assert focus switch.
        waitAndAssertActivityStates(state(secondActivityClass, ON_TOP_POSITION_LOST),
                state(defaultActivityClass, ON_TOP_POSITION_GAINED));
        assertOrder(getTransitionLog(), Arrays.asList(
                transition(secondActivityClass, ON_TOP_POSITION_LOST),
                transition(defaultActivityClass, ON_TOP_POSITION_GAINED)),
                "launchOnDifferentDisplay");

        // Tap on secondary display to switch the top activity.
        getTransitionLog().clear();
        tapOnDisplayCenter(newDisplay.mId);

        // Wait and assert top resumed position switch. Because of timeout top position gain
        // will appear before top position loss handling is finished.
        waitAndAssertActivityStates(state(secondActivityClass, ON_TOP_POSITION_GAINED),
                state(defaultActivityClass, ON_TOP_POSITION_LOST));
        assertOrder(getTransitionLog(), Arrays.asList(
                transition(secondActivityClass, ON_TOP_POSITION_GAINED),
                transition(defaultActivityClass, ON_TOP_POSITION_LOST)),
                "tapOnDifferentDisplay");

        // Wait 5 seconds more to make sure that no new messages received after top resumed state
        // released by the slow activity
        getTransitionLog().clear();
        SystemClock.sleep(5000);
        assertEmptySequence(defaultActivityClass, getTransitionLog(),
                "topStateLossTimeout");
        assertEmptySequence(secondActivityClass, getTransitionLog(),
                "topStateLossTimeout");
    }

    @Test
    public void testFinishOnDifferentDisplay_nonFocused() throws Exception {
        assumeTrue(supportsMultiDisplay());

        // In some platforms, Launcher can invoke another embedded Activity and it can affect
        // the test. We'll place a bottom Activity to eliminate the side effects by that Launcher.
        final Activity bottomActivity = launchActivityAndWait(SecondActivity.class);

        // Launch activity on some display.
        final Activity callbackTrackingActivity =
                launchActivityAndWait(CallbackTrackingActivity.class);

        waitAndAssertTopResumedActivity(getComponentName(CallbackTrackingActivity.class),
                DEFAULT_DISPLAY, "Activity launched on default display must be focused");

        // Create new simulated display.
        final WindowManagerState.DisplayContent newDisplay = createManagedVirtualDisplaySession()
                .setSimulateDisplay(true)
                .createDisplay();

        // Launch another activity on new secondary display.
        getTransitionLog().clear();
        final ActivityOptions launchOptions = ActivityOptions.makeBasic();
        launchOptions.setLaunchDisplayId(newDisplay.mId);
        new Launcher(SingleTopActivity.class)
                .setFlags(FLAG_ACTIVITY_NEW_TASK)
                .setOptions(launchOptions)
                .launch();
        waitAndAssertTopResumedActivity(getComponentName(SingleTopActivity.class),
                newDisplay.mId, "Activity launched on secondary display must be focused");
        // An activity is launched on the new display, so the activity on default display should
        // lose the top state.
        assertSequence(CallbackTrackingActivity.class, getTransitionLog(),
                Arrays.asList(ON_TOP_POSITION_LOST), "launchFocusSwitch");

        // Finish the activity on the default display.
        getTransitionLog().clear();
        callbackTrackingActivity.finish();

        // Verify that activity was actually destroyed.
        waitAndAssertActivityStates(state(CallbackTrackingActivity.class, ON_DESTROY));
        // Verify that the original focused display is not affected by the finished activity on
        // non-focused display.
        assertEmptySequence(SingleTopActivity.class, getTransitionLog(),
                "destructionOnDifferentDisplay");
    }

    @Test
    public void testFinishOnDifferentDisplay_focused() throws Exception {
        assumeTrue(supportsMultiDisplay());

        // Launch activity on some display.
        final Activity bottomActivity = launchActivityAndWait(SecondActivity.class);
        final Activity callbackTrackingActivity =
                launchActivityAndWait(CallbackTrackingActivity.class);

        waitAndAssertTopResumedActivity(getComponentName(CallbackTrackingActivity.class),
                DEFAULT_DISPLAY, "Activity launched on default display must be focused");

        // Create new simulated display.
        final WindowManagerState.DisplayContent newDisplay = createManagedVirtualDisplaySession()
                .setSimulateDisplay(true)
                .createDisplay();

        // Launch another activity on new secondary display.
        getTransitionLog().clear();
        final ActivityOptions launchOptions = ActivityOptions.makeBasic();
        launchOptions.setLaunchDisplayId(newDisplay.mId);
        new Launcher(SingleTopActivity.class)
                .setFlags(FLAG_ACTIVITY_NEW_TASK)
                .setOptions(launchOptions)
                .launch();
        waitAndAssertTopResumedActivity(getComponentName(SingleTopActivity.class),
                newDisplay.mId, "Activity launched on secondary display must be focused");

        // Bring the focus back.
        final Intent sameInstanceIntent = new Intent(mContext, CallbackTrackingActivity.class);
        sameInstanceIntent.setFlags(FLAG_ACTIVITY_REORDER_TO_FRONT);
        bottomActivity.startActivity(sameInstanceIntent, null);
        waitAndAssertActivityStates(state(CallbackTrackingActivity.class, ON_TOP_POSITION_GAINED));

        // Finish the focused activity
        getTransitionLog().clear();
        callbackTrackingActivity.finish();

        // Verify that lifecycle of the activity on a different display did not change.
        // Top resumed state will be given to home activity on that display.
        waitAndAssertActivityStates(state(CallbackTrackingActivity.class, ON_DESTROY),
                state(SecondActivity.class, ON_RESUME));
        assertEmptySequence(SingleTopActivity.class, getTransitionLog(),
                "destructionOnDifferentDisplay");
    }

    @Test
    public void testTopPositionNotSwitchedToPip() throws Exception {
        assumeTrue(supportsPip());

        // Launch first activity
        final Activity activity = launchActivityAndWait(CallbackTrackingActivity.class);

        // Clear the log before launching to Pip
        getTransitionLog().clear();

        // Launch Pip-capable activity and enter Pip immediately
        final Activity pipActivity = new Launcher(PipActivity.class)
                .setExtraFlags(EXTRA_ENTER_PIP)
                .setExpectedState(ON_PAUSE)
                .launch();

        // The PipMenuActivity could start anytime after moving pipActivity to pinned stack,
        // however, we cannot control when would it start or finish, so this test could fail when
        // PipMenuActivity just start and pipActivity call finish almost at the same time.
        // So the strategy here is to wait the PipMenuActivity start and finish after pipActivity
        // moved to pinned stack and paused, because pipActivity is not focusable but the
        // PipMenuActivity is focusable, when the pinned stack gain top focus means the
        // PipMenuActivity is launched and resumed, then when pinned stack lost top focus means the
        // PipMenuActivity is finished.
        mWmState.waitWindowingModeTopFocus(WINDOWING_MODE_PINNED, true /* topFocus */
                , "wait PipMenuActivity get top focus");
        mWmState.waitWindowingModeTopFocus(WINDOWING_MODE_PINNED, false /* topFocus */
                , "wait PipMenuActivity lost top focus");
        waitAndAssertActivityStates(state(activity, ON_TOP_POSITION_GAINED));

        assertOrder(getTransitionLog(), Arrays.asList(
                transition(CallbackTrackingActivity.class, ON_TOP_POSITION_LOST),
                transition(CallbackTrackingActivity.class, ON_PAUSE),
                transition(CallbackTrackingActivity.class, ON_RESUME),
                transition(CallbackTrackingActivity.class, ON_TOP_POSITION_GAINED)), "startPIP");

        // Exit PiP
        getTransitionLog().clear();
        pipActivity.finish();

        waitAndAssertActivityStates(state(pipActivity, ON_DESTROY));
        assertSequence(PipActivity.class, getTransitionLog(),
                Arrays.asList(
                        ON_STOP, ON_DESTROY), "finishPip");
        assertEmptySequence(CallbackTrackingActivity.class, getTransitionLog(),
                "finishPip");
    }

    @Test
    public void testTopPositionForAlwaysFocusableActivityInPip() throws Exception {
        assumeTrue(supportsPip());

        // Launch first activity
        final Activity activity = launchActivityAndWait(CallbackTrackingActivity.class);

        // Clear the log before launching to Pip
        getTransitionLog().clear();

        // Launch Pip-capable activity and enter Pip immediately
        final Activity pipActivity = new Launcher(PipActivity.class)
                .setExtraFlags(EXTRA_ENTER_PIP)
                .setExpectedState(ON_PAUSE)
                .launch();

        // Launch always focusable activity into PiP

        // Notice that do not clear the lifecycle log here, because it may clear the event
        // ON_TOP_POSITION_LOST of CallbackTrackingActivity if PipMenuActivity is started earlier.
        final Activity alwaysFocusableActivity =
                launchActivityAndWait(AlwaysFocusablePipActivity.class);
        waitAndAssertActivityStates(state(pipActivity, ON_STOP),
                state(activity, ON_TOP_POSITION_LOST));
        assertOrder(getTransitionLog(), Arrays.asList(
                transition(CallbackTrackingActivity.class, ON_TOP_POSITION_LOST),
                transition(AlwaysFocusablePipActivity.class, ON_TOP_POSITION_GAINED)),
                "launchAlwaysFocusablePip");

        // Finish always focusable activity - top position should go back to fullscreen activity
        getTransitionLog().clear();
        alwaysFocusableActivity.finish();

        waitAndAssertActivityStates(state(alwaysFocusableActivity, ON_DESTROY),
                state(activity, ON_TOP_POSITION_GAINED), state(pipActivity, ON_PAUSE));
        assertResumeToDestroySequence(AlwaysFocusablePipActivity.class,
                getTransitionLog());
        assertOrder(getTransitionLog(), Arrays.asList(
                transition(AlwaysFocusablePipActivity.class, ON_TOP_POSITION_LOST),
                transition(CallbackTrackingActivity.class, ON_TOP_POSITION_GAINED)),
                "finishAlwaysFocusablePip");
    }
}
