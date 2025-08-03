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

import static android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM;
import static android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN;
import static android.content.Intent.FLAG_ACTIVITY_MULTIPLE_TASK;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static android.server.wm.ComponentNameUtils.getWindowName;
import static android.server.wm.WindowManagerState.STATE_PAUSED;
import static android.server.wm.WindowManagerState.STATE_RESUMED;
import static android.server.wm.WindowManagerState.STATE_STOPPED;
import static android.server.wm.activity.lifecycle.LifecycleConstants.ON_RESUME;
import static android.server.wm.activity.lifecycle.LifecycleConstants.ON_TOP_POSITION_LOST;
import static android.server.wm.activity.lifecycle.LifecycleConstants.getComponentName;
import static android.server.wm.activity.lifecycle.TransitionVerifier.assertEmptySequence;
import static android.server.wm.activity.lifecycle.TransitionVerifier.assertLaunchAndPauseSequence;
import static android.server.wm.activity.lifecycle.TransitionVerifier.assertRestartAndResumeSequence;
import static android.server.wm.activity.lifecycle.TransitionVerifier.assertResumeToDestroySequence;
import static android.server.wm.activity.lifecycle.TransitionVerifier.assertSequence;
import static android.server.wm.app27.Components.SDK_27_LAUNCHING_ACTIVITY;
import static android.server.wm.app27.Components.SDK_27_TEST_ACTIVITY;

import static org.junit.Assert.assertFalse;
import static org.junit.Assume.assumeTrue;

import android.app.Activity;
import android.app.ActivityOptions;
import android.content.Intent;
import android.os.Bundle;
import android.platform.test.annotations.Presubmit;

import androidx.test.filters.MediumTest;

import org.junit.Before;
import org.junit.Test;

import java.util.Collections;

/**
 * Build/Install/Run:
 *     atest CtsWindowManagerDeviceActivity:ActivityLifecycleFreeformTests
 */
@MediumTest
@Presubmit
@android.server.wm.annotation.Group3
public class ActivityLifecycleFreeformTests extends ActivityLifecycleClientTestBase {

    @Before
    public void setUp() throws Exception {
        super.setUp();
        assumeTrue(supportsFreeform());
    }

    @Test
    public void testLaunchInFreeform() throws Exception {
        // Launch a fullscreen activity, mainly to prevent setting pending due to task switching.
        launchActivityInFullscreenAndWait(CallbackTrackingActivity.class);

        // Launch an activity in freeform
        final ActivityOptions launchOptions = ActivityOptions.makeBasic();
        launchOptions.setLaunchWindowingMode(WINDOWING_MODE_FREEFORM);
        new Launcher(FirstActivity.class)
                .setFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_MULTIPLE_TASK)
                .setOptions(launchOptions)
                .launch();

        // Wait and assert resume
        waitAndAssertActivityState(getComponentName(FirstActivity.class), STATE_RESUMED,
                "Activity should be resumed after launch");
        TransitionVerifier.assertLaunchSequence(FirstActivity.class, getTransitionLog());
        TransitionVerifier.assertLaunchSequence(
                CallbackTrackingActivity.class, getTransitionLog(), ON_TOP_POSITION_LOST);
    }

    @Test
    public void testMultiLaunchInFreeform() throws Exception {
        // Launch a fullscreen activity, mainly to prevent setting pending due to task switching.
        launchActivityInFullscreenAndWait(CallbackTrackingActivity.class);

        final ActivityOptions launchOptions = ActivityOptions.makeBasic();
        launchOptions.setLaunchWindowingMode(WINDOWING_MODE_FREEFORM);

        // Launch three activities in freeform
        new Launcher(FirstActivity.class)
                .setFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_MULTIPLE_TASK)
                .setOptions(launchOptions)
                .launch();

        new Launcher(SecondActivity.class)
                .setFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_MULTIPLE_TASK)
                .setOptions(launchOptions)
                .launch();

        new Launcher(ThirdActivity.class)
                .setFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_MULTIPLE_TASK)
                .setOptions(launchOptions)
                .launch();

        // Wait for resume
        final String message = "Activity should be resumed after launch";
        waitAndAssertActivityState(getComponentName(FirstActivity.class), STATE_RESUMED, message);
        waitAndAssertActivityState(getComponentName(SecondActivity.class), STATE_RESUMED, message);
        waitAndAssertActivityState(getComponentName(ThirdActivity.class), STATE_RESUMED, message);

        // Assert lifecycle
        TransitionVerifier.assertLaunchSequence(FirstActivity.class, getTransitionLog());
        TransitionVerifier.assertLaunchSequence(SecondActivity.class, getTransitionLog());
        TransitionVerifier.assertLaunchSequence(ThirdActivity.class, getTransitionLog());
        TransitionVerifier.assertLaunchSequence(
                CallbackTrackingActivity.class, getTransitionLog(), ON_TOP_POSITION_LOST);
    }

    @Test
    public void testLaunchOccludingInFreeform() throws Exception {
        // Launch a fullscreen activity, mainly to prevent setting pending due to task switching.
        launchActivityInFullscreenAndWait(CallbackTrackingActivity.class);

        final ActivityOptions launchOptions = ActivityOptions.makeBasic();
        launchOptions.setLaunchWindowingMode(WINDOWING_MODE_FREEFORM);

        // Launch two activities in freeform in the same task
        new Launcher(FirstActivity.class)
                .setFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_MULTIPLE_TASK)
                .setOptions(launchOptions)
                .launch();

        final Activity secondActivity = launchActivityAndWait(SecondActivity.class);

        new Launcher(ThirdActivity.class)
                .setFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_MULTIPLE_TASK)
                .setOptions(launchOptions)
                .launch();

        // Wait for valid states
        final String stopMessage = "Activity should be stopped after being covered above";
        waitAndAssertActivityState(getComponentName(FirstActivity.class), STATE_STOPPED,
                stopMessage);
        final String message = "Activity should be resumed after launch";
        waitAndAssertActivityState(getComponentName(SecondActivity.class), STATE_RESUMED, message);
        waitAndAssertActivityState(getComponentName(ThirdActivity.class), STATE_RESUMED, message);

        // Assert lifecycle
        TransitionVerifier.assertLaunchAndStopSequence(FirstActivity.class, getTransitionLog());
        TransitionVerifier.assertLaunchSequence(SecondActivity.class, getTransitionLog());
        TransitionVerifier.assertLaunchSequence(ThirdActivity.class, getTransitionLog());
        TransitionVerifier.assertLaunchSequence(
                CallbackTrackingActivity.class, getTransitionLog(), ON_TOP_POSITION_LOST);

        // Finish the activity that was occluding the first one
        getTransitionLog().clear();
        secondActivity.finish();

        // Wait and assert the lifecycle
        mWmState.waitForActivityRemoved(getComponentName(SecondActivity.class));
        waitAndAssertActivityState(getComponentName(FirstActivity.class), STATE_RESUMED,
                "Activity must be resumed after occluding finished");

        assertFalse("Activity must be destroyed",
                mWmState.containsActivity(getComponentName(SecondActivity.class)));
        assertFalse("Activity must be destroyed",
                mWmState.containsWindow(
                        getWindowName(getComponentName(SecondActivity.class))));
        assertRestartAndResumeSequence(FirstActivity.class, getTransitionLog());
        assertResumeToDestroySequence(SecondActivity.class, getTransitionLog());
        assertEmptySequence(ThirdActivity.class, getTransitionLog(), "finishInOtherStack");
        assertEmptySequence(CallbackTrackingActivity.class, getTransitionLog(),
                "finishInOtherStack");
    }

    @Test
    public void testLaunchTranslucentInFreeform() throws Exception {
        // Launch a fullscreen activity, mainly to prevent setting pending due to task switching.
        launchActivityInFullscreenAndWait(CallbackTrackingActivity.class);

        final ActivityOptions launchOptions = ActivityOptions.makeBasic();
        launchOptions.setLaunchWindowingMode(WINDOWING_MODE_FREEFORM);

        // Launch two activities in freeform in the same task
        new Launcher(FirstActivity.class)
                .setFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_MULTIPLE_TASK)
                .setOptions(launchOptions)
                .launch();

        final Activity transparentActivity = launchActivityAndWait(TranslucentActivity.class);

        new Launcher(ThirdActivity.class)
                .setFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_MULTIPLE_TASK)
                .setOptions(launchOptions)
                .launch();

        // Wait for valid states
        final String pauseMessage = "Activity should be stopped after transparent launch above";
        waitAndAssertActivityState(getComponentName(FirstActivity.class), STATE_PAUSED,
                pauseMessage);
        final String message = "Activity should be resumed after launch";
        waitAndAssertActivityState(getComponentName(TranslucentActivity.class), STATE_RESUMED,
                message);
        waitAndAssertActivityState(getComponentName(ThirdActivity.class), STATE_RESUMED, message);

        // Assert lifecycle
        assertLaunchAndPauseSequence(FirstActivity.class, getTransitionLog());
        TransitionVerifier.assertLaunchSequence(TranslucentActivity.class, getTransitionLog());
        TransitionVerifier.assertLaunchSequence(ThirdActivity.class, getTransitionLog());
        TransitionVerifier.assertLaunchSequence(
                CallbackTrackingActivity.class, getTransitionLog(), ON_TOP_POSITION_LOST);

        // Finish the activity that was occluding the first one
        getTransitionLog().clear();
        transparentActivity.finish();

        // Wait and assert the lifecycle
        mWmState.waitForActivityRemoved(getComponentName(TranslucentActivity.class));
        waitAndAssertActivityState(getComponentName(FirstActivity.class), STATE_RESUMED,
                "Activity must be resumed after occluding finished");


        assertFalse("Activity must be destroyed",
                mWmState.containsActivity(
                        getComponentName(TranslucentActivity.class)));
        assertFalse("Activity must be destroyed",
                mWmState.containsWindow(
                        getWindowName(getComponentName(TranslucentActivity.class))));
        assertSequence(FirstActivity.class, getTransitionLog(),
                Collections.singletonList(ON_RESUME), "finishTranslucentOnTop");
        assertResumeToDestroySequence(TranslucentActivity.class,
                getTransitionLog());
        assertEmptySequence(ThirdActivity.class, getTransitionLog(),
                "finishInOtherStack");
        assertEmptySequence(CallbackTrackingActivity.class, getTransitionLog(),
                "finishInOtherStack");
    }

    @Test
    public void testPreQTopProcessResumedActivityInFreeform() throws Exception {
        // Resume app switches, so the activities that we are going to launch won't be deferred
        // since Home activity was started in #setUp().
        resumeAppSwitches();

        final ActivityOptions launchOptions = ActivityOptions.makeBasic();
        launchOptions.setLaunchWindowingMode(WINDOWING_MODE_FREEFORM);
        final Bundle bundle = launchOptions.toBundle();

        // Launch an activity that targeted SDK 27.
        Intent intent = new Intent().setComponent(SDK_27_TEST_ACTIVITY)
                .setFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_MULTIPLE_TASK);
        mTargetContext.startActivity(intent, bundle);
        waitAndAssertActivityState(SDK_27_TEST_ACTIVITY, STATE_RESUMED,
                "Activity must be resumed.");

        // Launch another activity in the same process.
        intent = new Intent().setComponent(SDK_27_LAUNCHING_ACTIVITY)
                .setFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_MULTIPLE_TASK);
        mTargetContext.startActivity(intent, bundle);
        waitAndAssertActivityState(SDK_27_TEST_ACTIVITY, STATE_PAUSED,
                "Activity must be paused since another activity started.");
        waitAndAssertActivityState(SDK_27_LAUNCHING_ACTIVITY, STATE_RESUMED,
                "Activity must be resumed.");
    }

    private Activity launchActivityInFullscreenAndWait(Class<? extends Activity> activityClass)
        throws Exception {
        final ActivityOptions launchOptions = ActivityOptions.makeBasic();
        launchOptions.setLaunchWindowingMode(WINDOWING_MODE_FULLSCREEN);
        return new Launcher(activityClass)
            .setOptions(launchOptions)
            .launch();
    }
}
