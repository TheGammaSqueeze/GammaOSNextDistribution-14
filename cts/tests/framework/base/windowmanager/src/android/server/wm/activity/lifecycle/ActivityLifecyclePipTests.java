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

import static android.content.Intent.FLAG_ACTIVITY_MULTIPLE_TASK;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static android.server.wm.activity.lifecycle.LifecycleConstants.ON_CREATE;
import static android.server.wm.activity.lifecycle.LifecycleConstants.ON_DESTROY;
import static android.server.wm.activity.lifecycle.LifecycleConstants.ON_PAUSE;
import static android.server.wm.activity.lifecycle.LifecycleConstants.ON_RESTART;
import static android.server.wm.activity.lifecycle.LifecycleConstants.ON_RESUME;
import static android.server.wm.activity.lifecycle.LifecycleConstants.ON_START;
import static android.server.wm.activity.lifecycle.LifecycleConstants.ON_STOP;
import static android.server.wm.activity.lifecycle.TransitionVerifier.assertEmptySequence;
import static android.server.wm.activity.lifecycle.TransitionVerifier.assertRestartAndResumeSequence;
import static android.server.wm.activity.lifecycle.TransitionVerifier.assertResumeToDestroySequence;
import static android.server.wm.activity.lifecycle.TransitionVerifier.assertSequence;
import static android.server.wm.activity.lifecycle.TransitionVerifier.assertSequenceMatchesOneOf;
import static android.server.wm.app.Components.PipActivity.EXTRA_ENTER_PIP;

import static org.junit.Assume.assumeTrue;

import android.app.Activity;
import android.platform.test.annotations.Presubmit;

import androidx.test.filters.MediumTest;

import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Build/Install/Run:
 *     atest CtsWindowManagerDeviceActivity:ActivityLifecyclePipTests
 */
@MediumTest
@Presubmit
@android.server.wm.annotation.Group3
public class ActivityLifecyclePipTests extends ActivityLifecycleClientTestBase {

    @Before
    public void setUp() throws Exception {
        super.setUp();
        assumeTrue(supportsPip());
    }

    @Test
    public void testGoToPip() throws Exception {
        // Launch first activity
        final Activity firstActivity = launchActivityAndWait(FirstActivity.class);

        // Launch Pip-capable activity
        final PipActivity pipActivity = launchActivityAndWait(PipActivity.class);

        waitAndAssertActivityStates(state(firstActivity, ON_STOP));

        // Move activity to Picture-In-Picture
        getTransitionLog().clear();
        pipActivity.enterPip();

        // Wait and assert lifecycle
        waitAndAssertActivityStates(state(firstActivity, ON_RESUME), state(pipActivity, ON_PAUSE));
        assertRestartAndResumeSequence(FirstActivity.class, getTransitionLog());
        assertSequence(PipActivity.class, getTransitionLog(), Collections.singletonList(ON_PAUSE),
                "enterPip");
    }

    @Test
    public void testPipOnLaunch() throws Exception {
        // Launch first activity
        final Activity firstActivity = launchActivityAndWait(FirstActivity.class);

        // Clear the log before launching to Pip
        getTransitionLog().clear();

        // Launch Pip-capable activity and enter Pip immediately
        new Launcher(PipActivity.class)
                .setExpectedState(ON_PAUSE)
                .setExtraFlags(EXTRA_ENTER_PIP)
                .launch();

        // Wait and assert lifecycle
        waitAndAssertActivityStates(state(firstActivity, ON_RESUME));

        final List<String> expectedSequence =
                Arrays.asList(ON_PAUSE, ON_RESUME);
        final List<String> extraCycleSequence =
                Arrays.asList(ON_PAUSE, ON_STOP, ON_RESTART, ON_START, ON_RESUME);
        assertSequenceMatchesOneOf(FirstActivity.class, getTransitionLog(),
                Arrays.asList(expectedSequence, extraCycleSequence), "activityEnteringPipOnTop");
        assertSequence(PipActivity.class, getTransitionLog(),
                Arrays.asList(ON_CREATE, ON_START, ON_RESUME, ON_PAUSE), "launchAndEnterPip");
    }

    @Test
    public void testDestroyPip() throws Exception {
        // Launch first activity
        final Activity firstActivity = launchActivityAndWait(FirstActivity.class);

        // Clear the log before launching to Pip
        getTransitionLog().clear();

        // Launch Pip-capable activity and enter Pip immediately
        final Activity pipActivity = new Launcher(PipActivity.class)
                .setExpectedState(ON_PAUSE)
                .setExtraFlags(EXTRA_ENTER_PIP)
                .launch();

        // Wait and assert lifecycle
        waitAndAssertActivityStates(state(firstActivity, ON_RESUME));

        // Exit PiP
        getTransitionLog().clear();
        pipActivity.finish();

        waitAndAssertActivityStates(state(pipActivity, ON_DESTROY));
        assertEmptySequence(FirstActivity.class, getTransitionLog(), "finishPip");
        assertSequence(PipActivity.class, getTransitionLog(),
                Arrays.asList(ON_STOP, ON_DESTROY), "finishPip");
    }

    @Test
    public void testLaunchBelowPip() throws Exception {
        // Launch Pip-capable activity and enter Pip immediately
        new Launcher(PipActivity.class)
                .setExpectedState(ON_PAUSE)
                .setExtraFlags(EXTRA_ENTER_PIP)
                .launch();

        // Launch a regular activity below
        getTransitionLog().clear();
        new Launcher(FirstActivity.class)
                .setFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_MULTIPLE_TASK)
                .launch();

        // Wait and verify the sequence
        TransitionVerifier.assertLaunchSequence(FirstActivity.class, getTransitionLog());
        assertEmptySequence(PipActivity.class, getTransitionLog(),
                "launchBelowPip");
    }

    @Test
    public void testIntoPipSameTask() throws Exception {
        // Launch Pip-capable activity and enter Pip immediately
        new Launcher(PipActivity.class)
                .setExpectedState(ON_PAUSE)
                .setExtraFlags(EXTRA_ENTER_PIP)
                .launch();

        // Launch a regular activity into same task
        getTransitionLog().clear();
        new Launcher(FirstActivity.class)
                .setExpectedState(ON_PAUSE)
                // Skip launch time verification - it can be affected by PiP menu activity
                .setSkipLaunchTimeCheck()
                .launch();

        // Wait and verify the sequence
        waitAndAssertActivityStates(state(PipActivity.class, ON_STOP));

        // TODO(b/123013403): sometimes extra one or even more relaunches happen
        //final List<String> extraDestroySequence =
        //        Arrays.asList(PRE_ON_CREATE, ON_CREATE, ON_START, ON_RESUME, ON_PAUSE, ON_STOP,
        //                ON_DESTROY, PRE_ON_CREATE, ON_CREATE, ON_START, ON_RESUME, ON_PAUSE);
        //waitForActivityTransitions(FirstActivity.class, extraDestroySequence);
        //final List<String> expectedSequence =
        //        Arrays.asList(PRE_ON_CREATE, ON_CREATE, ON_START, ON_RESUME, ON_PAUSE);
        //TransitionVerifier.assertSequenceMatchesOneOf(FirstActivity.class, getLifecycleLog(),
        //        Arrays.asList(extraDestroySequence, expectedSequence),
        //        "launchIntoPip");

        assertSequence(PipActivity.class, getTransitionLog(),
                Arrays.asList(ON_STOP), "launchIntoPip");
    }

    @Test
    public void testDestroyBelowPip() throws Exception {
        // Launch a regular activity
        final Activity regularActivity = launchActivityAndWait(SideActivity.class);

        // Launch Pip-capable activity (which is on a separate Task) and enter Pip immediately
        new Launcher(PipActivity.class)
                .setExpectedState(ON_PAUSE)
                .setExtraFlags(EXTRA_ENTER_PIP)
                .launch();

        waitAndAssertActivityStates(state(regularActivity, ON_RESUME));

        // Destroy the activity below
        getTransitionLog().clear();
        regularActivity.finish();
        waitAndAssertActivityStates(state(regularActivity, ON_DESTROY));
        assertResumeToDestroySequence(SideActivity.class, getTransitionLog());
        assertEmptySequence(PipActivity.class, getTransitionLog(),
                "destroyBelowPip");
    }

    @Test
    public void testSplitScreenBelowPip() throws Exception {
        assumeTrue(supportsSplitScreenMultiWindow());

        // Launch Pip-capable activity and enter Pip immediately
        new Launcher(PipActivity.class)
                .setExpectedState(ON_PAUSE)
                .setExtraFlags(EXTRA_ENTER_PIP)
                .launch();

        // Launch an activity that will be moved to split-screen secondary
        final Activity sideActivity = new Launcher(ThirdActivity.class)
                .setFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_MULTIPLE_TASK)
                .launch();

        // Launch first activity
        getTransitionLog().clear();
        final Activity firstActivity = new Launcher(FirstActivity.class)
                .setFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_MULTIPLE_TASK)
                .launch();
        TransitionVerifier.assertLaunchSequence(FirstActivity.class, getTransitionLog());

        // Enter split screen
        moveTaskToPrimarySplitScreenAndVerify(firstActivity, sideActivity);
        assertEmptySequence(PipActivity.class, getTransitionLog(),
                "launchBelow");

        // Set secondary split as launch root
        mTaskOrganizer.setLaunchRoot(mTaskOrganizer.getSecondarySplitTaskId());

        // Launch second activity to side
        getTransitionLog().clear();
        new Launcher(SecondActivity.class)
                .setFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_MULTIPLE_TASK)
                .launch();

        TransitionVerifier.assertLaunchSequence(SecondActivity.class, getTransitionLog());
        assertEmptySequence(PipActivity.class, getTransitionLog(),
                "launchBelow");
    }

    @Test
    public void testPipAboveSplitScreen() throws Exception {
        assumeTrue(supportsSplitScreenMultiWindow());

        // Launch an activity that will be moved to split-screen secondary
        final Activity sideActivity = launchActivityAndWait(SideActivity.class);

        // Launch first activity
        final Activity firstActivity = launchActivityAndWait(FirstActivity.class);

        // Enter split screen
        moveTaskToPrimarySplitScreenAndVerify(firstActivity, sideActivity);

        // Set secondary split as launch root
        mTaskOrganizer.setLaunchRoot(mTaskOrganizer.getSecondarySplitTaskId());

        // Launch second activity to side
        final Activity secondActivity = new Launcher(SecondActivity.class)
                .setFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_MULTIPLE_TASK)
                .launch();

        // Launch Pip-capable activity and enter Pip immediately
        getTransitionLog().clear();
        new Launcher(PipActivity.class)
                .setExpectedState(ON_PAUSE)
                .setExtraFlags(EXTRA_ENTER_PIP)
                .launch();

        // Wait for it to launch and pause. Other activities should not be affected.
        waitAndAssertActivityStates(state(secondActivity, ON_RESUME));
        assertSequence(PipActivity.class, getTransitionLog(),
                Arrays.asList(ON_CREATE, ON_START, ON_RESUME, ON_PAUSE),
                "launchAndEnterPip");
        assertEmptySequence(FirstActivity.class, getTransitionLog(),
                "launchPipOnTop");
        final List<String> expectedSequence =
                Arrays.asList(ON_PAUSE, ON_RESUME);
        final List<String> extraCycleSequence =
                Arrays.asList(ON_PAUSE, ON_STOP, ON_RESTART, ON_START, ON_RESUME);
        // TODO(b/123013403): sometimes extra destroy is observed
        assertSequenceMatchesOneOf(SecondActivity.class,
                getTransitionLog(), Arrays.asList(expectedSequence, extraCycleSequence),
                "activityEnteringPipOnTop");
    }
}
