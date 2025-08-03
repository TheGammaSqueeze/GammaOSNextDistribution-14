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

import static android.content.Intent.FLAG_ACTIVITY_MULTIPLE_TASK;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static android.server.wm.WindowManagerState.STATE_STOPPED;
import static android.server.wm.activity.lifecycle.LifecycleConstants.ON_DESTROY;
import static android.server.wm.activity.lifecycle.LifecycleConstants.ON_PAUSE;
import static android.server.wm.activity.lifecycle.LifecycleConstants.ON_RESTART;
import static android.server.wm.activity.lifecycle.LifecycleConstants.ON_RESUME;
import static android.server.wm.activity.lifecycle.LifecycleConstants.ON_START;
import static android.server.wm.activity.lifecycle.LifecycleConstants.ON_STOP;
import static android.server.wm.activity.lifecycle.LifecycleConstants.ON_TOP_POSITION_GAINED;
import static android.server.wm.activity.lifecycle.LifecycleConstants.ON_TOP_POSITION_LOST;
import static android.server.wm.activity.lifecycle.TransitionVerifier.assertEmptySequence;
import static android.server.wm.activity.lifecycle.TransitionVerifier.assertEntireSequence;
import static android.server.wm.activity.lifecycle.TransitionVerifier.assertLaunchAndDestroySequence;
import static android.server.wm.activity.lifecycle.TransitionVerifier.assertSequence;
import static android.server.wm.activity.lifecycle.TransitionVerifier.transition;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.ActivityOptions;
import android.platform.test.annotations.Presubmit;

import androidx.test.filters.MediumTest;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

/**
 * Tests for {@link Activity} class APIs.
 *
 * Build/Install/Run:
 *      atest CtsWindowManagerDeviceActivity:ActivityTests
 */
@Presubmit
@MediumTest
@android.server.wm.annotation.Group3
public class ActivityTests extends ActivityLifecycleClientTestBase {
    @Test
    public void testReleaseActivityInstance_visible() throws Exception {
        final Activity activity = launchActivityAndWait(FirstActivity.class);
        waitAndAssertActivityStates(state(activity, ON_RESUME));

        getTransitionLog().clear();
        assertFalse("Launched and visible activity must be released", activity.releaseInstance());
        assertEmptySequence(FirstActivity.class, getTransitionLog(), "tryReleaseInstance");
    }

    @Test
    public void testReleaseActivityInstance_invisible() throws Exception {
        // Launch two activities - second one to cover the first one and make it invisible.
        final Activity firstActivity = launchActivityAndWait(FirstActivity.class);
        final Activity secondActivity = launchActivityAndWait(SecondActivity.class);
        // Wait for activity to report saved state to the server.
        mWmState.waitForActivityState(firstActivity.getComponentName(), STATE_STOPPED);

        // Release the instance of the non-visible activity below.
        getTransitionLog().clear();
        assertTrue("It must be possible to release an instance of an invisible activity",
                firstActivity.releaseInstance());
        waitAndAssertActivityStates(state(firstActivity, ON_DESTROY));
        assertEmptySequence(SecondActivity.class, getTransitionLog(),
                "releaseInstance");

        // Finish the top activity to navigate back to the first one and re-create it.
        getTransitionLog().clear();
        secondActivity.finish();
        waitAndAssertActivityStates(state(secondActivity, ON_DESTROY));
        TransitionVerifier.assertLaunchSequence(FirstActivity.class, getTransitionLog());
    }

    /**
     * Verify that {@link Activity#finishAndRemoveTask()} removes all activities in task if called
     * for root of task.
     */
    @Test
    public void testFinishTask_FromRoot() throws Exception {
        final Class<? extends Activity> rootActivityClass = CallbackTrackingActivity.class;
        final Activity rootActivity = launchActivityAndWait(rootActivityClass);
        final Class<? extends Activity> topActivityClass = SecondCallbackTrackingActivity.class;
        final Activity topActivity = launchActivityAndWait(topActivityClass);
        waitAndAssertActivityStates(state(rootActivity, ON_STOP),
                state(topActivity, ON_TOP_POSITION_GAINED));

        getTransitionLog().clear();
        rootActivity.finishAndRemoveTask();

        waitAndAssertActivityStates(state(rootActivity, ON_DESTROY),
                state(topActivity, ON_DESTROY));
        // Cannot guarantee exact sequence among top and bottom activities, so verifying
        // independently
        assertSequence(rootActivityClass, getTransitionLog(),
                Collections.singletonList(ON_DESTROY), "finishAndRemoveTask");
        assertSequence(topActivityClass, getTransitionLog(),
                Arrays.asList(ON_TOP_POSITION_LOST, ON_PAUSE, ON_STOP, ON_DESTROY),
                "finishAndRemoveTask");
    }

    /**
     * Verify that {@link Activity#finishAndRemoveTask()} removes all activities in task if called
     * for root of task. This version verifies lifecycle when top activity is translucent
     */
    @Test
    public void testFinishTask_FromRoot_TranslucentOnTop() throws Exception {
        final Class<? extends Activity> rootActivityClass = CallbackTrackingActivity.class;
        final Activity rootActivity = launchActivityAndWait(rootActivityClass);
        final Class<? extends Activity> topActivityClass =
                TranslucentCallbackTrackingActivity.class;
        final Activity topActivity = launchActivityAndWait(topActivityClass);
        waitAndAssertActivityStates(state(rootActivity, ON_PAUSE),
                state(topActivity, ON_TOP_POSITION_GAINED));

        getTransitionLog().clear();
        rootActivity.finishAndRemoveTask();

        waitAndAssertActivityStates(state(rootActivity, ON_DESTROY),
                state(topActivity, ON_DESTROY));
        // Cannot guarantee exact sequence among top and bottom activities, so verifying
        // independently
        assertSequence(rootActivityClass, getTransitionLog(),
                Arrays.asList(ON_STOP, ON_DESTROY), "finishAndRemoveTask");
        assertSequence(topActivityClass, getTransitionLog(),
                Arrays.asList(ON_TOP_POSITION_LOST, ON_PAUSE, ON_STOP, ON_DESTROY),
                "finishAndRemoveTask");
    }

    /**
     * Verify that {@link Activity#finishAndRemoveTask()} only removes one activity in task if
     * called not for root of task.
     */
    @Test
    public void testFinishTask_NotFromRoot() throws Exception {
        final Class<? extends Activity> rootActivityClass = CallbackTrackingActivity.class;
        final Activity rootActivity = launchActivityAndWait(rootActivityClass);
        final Class<? extends Activity> midActivityClass = SecondActivity.class;
        final Activity midActivity = launchActivityAndWait(midActivityClass);
        final Class<? extends Activity> topActivityClass = SecondCallbackTrackingActivity.class;
        final Activity topActivity = launchActivityAndWait(topActivityClass);
        waitAndAssertActivityStates(state(rootActivity, ON_STOP), state(midActivity, ON_STOP),
                state(topActivity, ON_TOP_POSITION_GAINED));

        getTransitionLog().clear();
        midActivity.finishAndRemoveTask();

        waitAndAssertActivityStates(state(midActivity, ON_DESTROY));
        assertEntireSequence(Collections.singletonList(transition(midActivityClass, ON_DESTROY)),
                getTransitionLog(), "finishAndRemoveTask");
    }

    /**
     * Verify the lifecycle of {@link Activity#finishAfterTransition()} for activity that has a
     * transition set.
     */
    @Test
    public void testFinishAfterTransition() throws Exception {
        final TransitionSourceActivity rootActivity =
                launchActivityAndWait(TransitionSourceActivity.class);
        waitAndAssertActivityStates(state(rootActivity, ON_RESUME));

        // Launch activity with configured shared element transition. It will call
        // finishAfterTransition() on its own after transition completes.
        rootActivity.runOnUiThread(rootActivity::launchActivityWithTransition);
        waitAndAssertActivityStates(state(TransitionDestinationActivity.class, ON_DESTROY),
                state(rootActivity, ON_RESUME));
        assertLaunchAndDestroySequence(TransitionDestinationActivity.class, getTransitionLog());
    }

    /**
     * Verify the lifecycle of {@link Activity#finishAfterTransition()} for activity with no
     * transition set (root of task).
     */
    @Test
    public void testFinishAfterTransition_noTransition_rootOfTask() throws Exception {
        final Activity activity = launchActivityAndWait(FirstActivity.class);
        waitAndAssertActivityStates(state(activity, ON_RESUME));

        getTransitionLog().clear();
        activity.finishAfterTransition();
        waitAndAssertActivityStates(state(FirstActivity.class, ON_DESTROY));
        assertSequence(FirstActivity.class, getTransitionLog(),
                Arrays.asList(ON_PAUSE, ON_STOP, ON_DESTROY), "finishAfterTransition");
    }

    /**
     * Verify the lifecycle of {@link Activity#finishAfterTransition()} for activity with no
     * transition set.
     */
    @Test
    public void testFinishAfterTransition_noTransition() throws Exception {
        final Activity rootActivity = launchActivityAndWait(FirstActivity.class);
        final Activity topActivity = launchActivityAndWait(SecondActivity.class);
        waitAndAssertActivityStates(state(topActivity, ON_RESUME), state(rootActivity, ON_STOP));

        getTransitionLog().clear();
        topActivity.finishAfterTransition();
        waitAndAssertActivityStates(state(SecondActivity.class, ON_DESTROY));
        assertSequence(SecondActivity.class, getTransitionLog(),
                Arrays.asList(ON_PAUSE, ON_STOP, ON_DESTROY), "finishAfterTransition");
    }

    /**
     * Verify that {@link Activity#finishAffinity()} will finish all activities with the same
     * affinity below the target activity.
     */
    @Test
    public void testFinishAffinity() throws Exception {
        final Activity firstActivity = launchActivityAndWait(FirstActivity.class);
        final Activity secondActivity = launchActivityAndWait(SecondActivity.class);
        final Activity thirdActivity = launchActivityAndWait(ThirdActivity.class);
        waitAndAssertActivityStates(state(thirdActivity, ON_RESUME), state(secondActivity, ON_STOP),
                state(firstActivity, ON_STOP));

        getTransitionLog().clear();
        secondActivity.finishAffinity();
        waitAndAssertActivityStates(state(FirstActivity.class, ON_DESTROY),
                state(SecondActivity.class, ON_DESTROY));
        assertEmptySequence(ThirdActivity.class, getTransitionLog(), "finishAffinityBelow");
    }

    /**
     * Verify that {@link Activity#finishAffinity()} will not finish activities with different
     * affinities in the same task.
     */
    @Test
    public void testFinishAffinity_differentAffinity() throws Exception {
        final Activity firstActivity = launchActivityAndWait(FirstActivity.class);
        final int firstActivityTDAFeatureId = mWmState.getTaskDisplayAreaFeatureId(firstActivity
                .getComponentName());
        ActivityOptions activityOptions = getLaunchOptionsForFullscreen();
        activityOptions.setLaunchTaskDisplayAreaFeatureId(firstActivityTDAFeatureId);
        final Activity differentAffinityActivity = new Launcher(DifferentAffinityActivity.class)
                .setOptions(activityOptions)
                .launch();
        waitAndAssertActivityStates(state(differentAffinityActivity, ON_RESUME),
                state(firstActivity, ON_STOP));

        getTransitionLog().clear();
        differentAffinityActivity.finishAffinity();
        waitAndAssertActivityStates(state(DifferentAffinityActivity.class, ON_DESTROY));
        assertSequence(FirstActivity.class, getTransitionLog(),
                Arrays.asList(ON_RESTART, ON_START, ON_RESUME), "finishAffinity");
    }

    /**
     * Verify that {@link Activity#finishAffinity()} will not finish activities with the same
     * affinity in different tasks.
     */
    @Test
    public void testFinishAffinity_multiTask() throws Exception {
        final Activity firstActivity = launchActivityAndWait(FirstActivity.class);
        final int firstActivityTDAFeatureId = mWmState.getTaskDisplayAreaFeatureId(firstActivity
                .getComponentName());
        ActivityOptions activityOptions = getLaunchOptionsForFullscreen();
        activityOptions.setLaunchTaskDisplayAreaFeatureId(firstActivityTDAFeatureId);
        // Launch fullscreen activity in a new task to stop first activity
        final Activity secondActivity = new Launcher(SecondActivity.class)
                .setFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_MULTIPLE_TASK)
                .setOptions(activityOptions)
                .launch();
        waitAndAssertActivityStates(state(secondActivity, ON_RESUME),
                state(firstActivity, ON_STOP));

        getTransitionLog().clear();
        secondActivity.finishAffinity();
        waitAndAssertActivityStates(state(SecondActivity.class, ON_DESTROY),
                state(firstActivity, ON_RESUME));
    }
}