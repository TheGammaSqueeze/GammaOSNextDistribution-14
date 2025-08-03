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

import static android.app.Instrumentation.ActivityMonitor;
import static android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN;
import static android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP;
import static android.content.Intent.FLAG_ACTIVITY_MULTIPLE_TASK;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static android.content.Intent.FLAG_ACTIVITY_NO_USER_ACTION;
import static android.server.wm.UiDeviceUtils.pressBackButton;
import static android.server.wm.WindowManagerState.STATE_PAUSED;
import static android.server.wm.WindowManagerState.STATE_STOPPED;
import static android.server.wm.activity.lifecycle.ActivityLifecycleClientTestBase.LaunchForResultActivity.EXTRA_LAUNCH_ON_RESULT;
import static android.server.wm.activity.lifecycle.ActivityLifecycleClientTestBase.LaunchForResultActivity.EXTRA_LAUNCH_ON_RESUME_AFTER_RESULT;
import static android.server.wm.activity.lifecycle.ActivityLifecycleClientTestBase.NoDisplayActivity.EXTRA_LAUNCH_ACTIVITY;
import static android.server.wm.activity.lifecycle.ActivityLifecycleClientTestBase.NoDisplayActivity.EXTRA_NEW_TASK;
import static android.server.wm.activity.lifecycle.LifecycleConstants.EXTRA_ACTIVITY_ON_USER_LEAVE_HINT;
import static android.server.wm.activity.lifecycle.LifecycleConstants.EXTRA_FINISH_IN_ON_CREATE;
import static android.server.wm.activity.lifecycle.LifecycleConstants.EXTRA_FINISH_IN_ON_PAUSE;
import static android.server.wm.activity.lifecycle.LifecycleConstants.EXTRA_FINISH_IN_ON_RESUME;
import static android.server.wm.activity.lifecycle.LifecycleConstants.EXTRA_FINISH_IN_ON_START;
import static android.server.wm.activity.lifecycle.LifecycleConstants.EXTRA_FINISH_IN_ON_STOP;
import static android.server.wm.activity.lifecycle.LifecycleConstants.EXTRA_RECREATE;
import static android.server.wm.activity.lifecycle.LifecycleConstants.EXTRA_SKIP_TOP_RESUMED_STATE;
import static android.server.wm.activity.lifecycle.LifecycleConstants.EXTRA_START_ACTIVITY_IN_ON_CREATE;
import static android.server.wm.activity.lifecycle.LifecycleConstants.EXTRA_START_ACTIVITY_WHEN_IDLE;
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
import static android.server.wm.activity.lifecycle.LifecycleConstants.ON_USER_LEAVE_HINT;
import static android.server.wm.activity.lifecycle.LifecycleConstants.getComponentName;
import static android.server.wm.activity.lifecycle.TransitionVerifier.assertEmptySequence;
import static android.server.wm.activity.lifecycle.TransitionVerifier.assertEntireSequence;
import static android.server.wm.activity.lifecycle.TransitionVerifier.assertLaunchAndDestroySequence;
import static android.server.wm.activity.lifecycle.TransitionVerifier.assertLaunchSequence;
import static android.server.wm.activity.lifecycle.TransitionVerifier.assertOrder;
import static android.server.wm.activity.lifecycle.TransitionVerifier.assertRelaunchSequence;
import static android.server.wm.activity.lifecycle.TransitionVerifier.assertRestartAndResumeSequence;
import static android.server.wm.activity.lifecycle.TransitionVerifier.assertRestartSequence;
import static android.server.wm.activity.lifecycle.TransitionVerifier.assertResumeToDestroySequence;
import static android.server.wm.activity.lifecycle.TransitionVerifier.assertSequence;
import static android.server.wm.activity.lifecycle.TransitionVerifier.assertSequenceMatchesOneOf;
import static android.server.wm.activity.lifecycle.TransitionVerifier.assertTransitionNotObserved;
import static android.server.wm.activity.lifecycle.TransitionVerifier.assertTransitionObserved;
import static android.server.wm.activity.lifecycle.TransitionVerifier.getLaunchAndDestroySequence;
import static android.server.wm.activity.lifecycle.TransitionVerifier.transition;
import static android.view.Surface.ROTATION_0;
import static android.view.Surface.ROTATION_180;
import static android.view.Surface.ROTATION_270;
import static android.view.Surface.ROTATION_90;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static org.junit.Assert.fail;

import android.app.Activity;
import android.app.ActivityOptions;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.platform.test.annotations.Presubmit;
import android.server.wm.RotationSession;

import androidx.test.filters.MediumTest;

import com.android.compatibility.common.util.AmUtils;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Build/Install/Run:
 *     atest CtsWindowManagerDeviceActivity:ActivityLifecycleTests
 */
@MediumTest
@Presubmit
@android.server.wm.annotation.Group3
public class ActivityLifecycleTests extends ActivityLifecycleClientTestBase {

    @Test
    public void testSingleLaunch() throws Exception {
        launchActivityAndWait(FirstActivity.class);

        TransitionVerifier.assertLaunchSequence(FirstActivity.class, getTransitionLog());
    }

    @Test
    public void testLaunchOnTop() throws Exception {
        final Activity firstActivity = launchActivityAndWait(FirstActivity.class);

        getTransitionLog().clear();
        launchActivityAndWait(SecondActivity.class);
        waitAndAssertActivityStates(state(firstActivity, ON_STOP));

        assertLaunchSequence(SecondActivity.class, FirstActivity.class,
                getTransitionLog(), false /* launchIsTranslucent */);
    }

    @Test
    public void testLaunchTranslucentOnTop() throws Exception {
        // Launch fullscreen activity
        final Activity firstActivity = launchActivityAndWait(FirstActivity.class);

        // Launch translucent activity on top
        getTransitionLog().clear();
        final Activity translucentActivity = launchActivityAndWait(TranslucentActivity.class);
        waitAndAssertActivityStates(occludedActivityState(firstActivity, translucentActivity));

        assertLaunchSequence(TranslucentActivity.class, FirstActivity.class,
                getTransitionLog(), true /* launchIsTranslucent */);
    }

    @Test
    public void testLaunchDoubleTranslucentOnTop() throws Exception {
        final Activity firstActivity = launchActivityAndWait(FirstActivity.class);

        // Launch translucent activity on top
        getTransitionLog().clear();
        final Activity translucentActivity = launchActivityAndWait(TranslucentActivity.class);
        waitAndAssertActivityStates(occludedActivityState(firstActivity, translucentActivity));

        assertLaunchSequence(TranslucentActivity.class, FirstActivity.class,
                getTransitionLog(), true /* launchIsTranslucent */);

        // Launch another translucent activity on top
        getTransitionLog().clear();
        final Activity secondTranslucentActivity =
                launchActivityAndWait(SecondTranslucentActivity.class);
        waitAndAssertActivityStates(
                occludedActivityState(translucentActivity, secondTranslucentActivity));
        assertSequence(TranslucentActivity.class, getTransitionLog(),
                Collections.singletonList(ON_PAUSE), "launch");
        assertEmptySequence(FirstActivity.class, getTransitionLog(), "launch");

        // Finish top translucent activity
        getTransitionLog().clear();
        secondTranslucentActivity.finish();

        waitAndAssertActivityStates(state(translucentActivity, ON_RESUME));
        waitAndAssertActivityStates(state(secondTranslucentActivity, ON_DESTROY));
        assertResumeToDestroySequence(SecondTranslucentActivity.class, getTransitionLog());
        assertSequence(TranslucentActivity.class, getTransitionLog(),
                Collections.singletonList(ON_RESUME), "launch");
        assertEmptySequence(FirstActivity.class, getTransitionLog(), "launch");
    }

    @Test
    public void testTranslucentMovedIntoStack() throws Exception {
        // Launch a translucent activity and a regular activity in separate stacks
        final Activity translucentActivity = new Launcher(TranslucentActivity.class)
                .setOptions(getLaunchOptionsForFullscreen())
                .launch();

        // We need to get the translucentActivity task display area feature Id so we can launch the
        // firstActivity on the same task display area as the translucentActivity.
        final int translucentActivityTDAFeatureId = mWmState.getTaskDisplayAreaFeatureId(
                translucentActivity.getComponentName());
        ActivityOptions activityOptions = getLaunchOptionsForFullscreen();
        activityOptions.setLaunchTaskDisplayAreaFeatureId(translucentActivityTDAFeatureId);
        final Activity firstActivity = new Launcher(FirstActivity.class)
                .setFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_MULTIPLE_TASK)
                .setOptions(activityOptions)
                .launch();
        waitAndAssertActivityStates(state(translucentActivity, ON_STOP));

        final ComponentName firstActivityName = getComponentName(FirstActivity.class);
        mWmState.computeState(firstActivityName);
        int firstActivityStack = mWmState.getRootTaskIdByActivity(firstActivityName);

        // We need to get the firstActivity task display area feature Id so we can move the
        // translucentActivity on top of the same task display area as the firstActivity.
        int firstActivityTDAFeatureId = mWmState.getTaskDisplayAreaFeatureId(firstActivityName);
        // Move translucent activity into the stack with the first activity
        getTransitionLog().clear();
        moveActivityToRootTaskOrOnTop(getComponentName(TranslucentActivity.class),
                firstActivityStack, firstActivityTDAFeatureId);

        // Wait for translucent activity to resume and first activity to pause
        waitAndAssertActivityStates(state(translucentActivity, ON_RESUME),
                state(firstActivity, ON_PAUSE));
        assertSequence(FirstActivity.class, getTransitionLog(), Collections.singletonList(ON_PAUSE),
                "launchOnTop");
        assertRestartAndResumeSequence(TranslucentActivity.class, getTransitionLog());
    }

    @Test
    public void testDestroyTopTranslucent() throws Exception {
        // Launch a regular activity and a a translucent activity in the same stack
        final Activity firstActivity = launchActivityAndWait(FirstActivity.class);
        final Activity translucentActivity = launchActivityAndWait(TranslucentActivity.class);
        waitAndAssertActivityStates(occludedActivityState(firstActivity, translucentActivity));

        // Finish translucent activity
        getTransitionLog().clear();
        translucentActivity.finish();

        waitAndAssertActivityStates(state(firstActivity, ON_RESUME),
                state(translucentActivity, ON_DESTROY));

        // Verify destruction lifecycle
        assertResumeToDestroySequence(TranslucentActivity.class,
                getTransitionLog());
        assertSequence(FirstActivity.class, getTransitionLog(),
                Arrays.asList(ON_RESUME), "resumeAfterTopDestroyed");
    }

    @Test
    public void testDestroyOnTopOfTranslucent() throws Exception {
        // Launch fullscreen activity
        final Activity firstActivity = launchActivityAndWait(FirstActivity.class);
        // Launch translucent activity
        final Activity translucentActivity = launchActivityAndWait(TranslucentActivity.class);
        // Launch another fullscreen activity
        final Activity secondActivity = launchActivityAndWait(SecondActivity.class);

        // Wait for top activity to resume
        waitAndAssertActivityStates(state(translucentActivity, ON_STOP),
                state(firstActivity, ON_STOP));

        getTransitionLog().clear();

        final boolean secondActivityIsTranslucent = ActivityInfo.isTranslucentOrFloating(
                secondActivity.getWindow().getWindowStyle());

        // Finish top activity
        secondActivity.finish();

        waitAndAssertActivityStates(state(secondActivity, ON_DESTROY));
        assertResumeToDestroySequence(SecondActivity.class, getTransitionLog());
        if (secondActivityIsTranslucent) {
            // In this case we don't expect the state of the firstActivity to change since it is
            // already in the visible paused state. So, we just verify that translucentActivity
            // transitions to resumed state.
            waitAndAssertActivityStates(state(translucentActivity, ON_RESUME));
        } else {
            // Wait for translucent activity to resume
            waitAndAssertActivityStates(state(translucentActivity, ON_RESUME),
                    state(firstActivity, ON_START));

            // Verify that the first activity was restarted
            assertRestartSequence(FirstActivity.class, getTransitionLog());
        }
    }

    @Test
    public void testDestroyDoubleTranslucentOnTop() throws Exception {
        final Activity firstActivity = launchActivityAndWait(FirstActivity.class);
        final Activity translucentActivity = launchActivityAndWait(TranslucentActivity.class);
        final Activity secondTranslucentActivity =
                launchActivityAndWait(SecondTranslucentActivity.class);
        waitAndAssertActivityStates(occludedActivityState(firstActivity, secondTranslucentActivity),
                occludedActivityState(translucentActivity, secondTranslucentActivity));

        // Finish top translucent activity
        getTransitionLog().clear();
        secondTranslucentActivity.finish();

        waitAndAssertActivityStates(state(translucentActivity, ON_RESUME));
        waitAndAssertActivityStates(state(secondTranslucentActivity, ON_DESTROY));
        assertResumeToDestroySequence(SecondTranslucentActivity.class,
                getTransitionLog());
        assertSequence(TranslucentActivity.class, getTransitionLog(),
                Collections.singletonList(ON_RESUME), "destroy");
        assertEmptySequence(FirstActivity.class, getTransitionLog(), "destroy");

        // Finish first translucent activity
        getTransitionLog().clear();
        translucentActivity.finish();

        waitAndAssertActivityStates(state(firstActivity, ON_RESUME));
        waitAndAssertActivityStates(state(translucentActivity, ON_DESTROY));
        assertResumeToDestroySequence(TranslucentActivity.class, getTransitionLog());
        assertSequence(FirstActivity.class, getTransitionLog(),
                Collections.singletonList(ON_RESUME), "secondDestroy");
    }

    @Test
    public void testFinishBottom() throws Exception {
        final Activity bottomActivity = launchActivityAndWait(FirstActivity.class);
        final Activity topActivity = launchActivityAndWait(SecondActivity.class);
        waitAndAssertActivityStates(state(bottomActivity, ON_STOP));

        // Finish the activity on the bottom
        getTransitionLog().clear();
        bottomActivity.finish();

        // Assert that activity on the bottom went directly to destroyed state, and activity on top
        // did not get any lifecycle changes.
        waitAndAssertActivityStates(state(bottomActivity, ON_DESTROY));
        assertSequence(FirstActivity.class, getTransitionLog(),
                Collections.singletonList(ON_DESTROY), "destroyOnBottom");
        assertEmptySequence(SecondActivity.class, getTransitionLog(),
                "destroyOnBottom");
    }

    @Test
    public void testFinishAndLaunchOnResult() throws Exception {
        testLaunchForResultAndLaunchAfterResultSequence(EXTRA_LAUNCH_ON_RESULT);
    }

    @Test
    public void testFinishAndLaunchAfterOnResultInOnResume() throws Exception {
        testLaunchForResultAndLaunchAfterResultSequence(EXTRA_LAUNCH_ON_RESUME_AFTER_RESULT);
    }

    /**
     * This triggers launch of an activity for result, which immediately finishes. After receiving
     * result new activity launch is triggered automatically.
     * @see android.server.wm.activity.lifecycle.ActivityLifecycleClientTestBase.LaunchForResultActivity
     */
    private void testLaunchForResultAndLaunchAfterResultSequence(String flag) throws Exception {
        new Launcher(LaunchForResultActivity.class)
                .customizeIntent(LaunchForResultActivity.forwardFlag(EXTRA_FINISH_IN_ON_RESUME))
                .setExtraFlags(flag)
                .setExpectedState(ON_STOP)
                .setNoInstance()
                .launch();

        waitAndAssertActivityStates(state(CallbackTrackingActivity.class, ON_TOP_POSITION_GAINED),
                state(ResultActivity.class, ON_DESTROY));
        assertOrder(getTransitionLog(), Arrays.asList(
                // Base launching activity starting.
                transition(LaunchForResultActivity.class, ON_CREATE),
                transition(LaunchForResultActivity.class, ON_START),
                transition(LaunchForResultActivity.class, ON_POST_CREATE),
                transition(LaunchForResultActivity.class, ON_RESUME),
                // An activity is automatically launched for result.
                transition(LaunchForResultActivity.class, ON_PAUSE),
                transition(ResultActivity.class, ON_CREATE),
                transition(ResultActivity.class, ON_START),
                transition(ResultActivity.class, ON_RESUME),
                // Activity that was launched for result is finished automatically - the base
                // launching activity is brought to front.
                transition(LaunchForResultActivity.class, ON_ACTIVITY_RESULT),
                transition(LaunchForResultActivity.class, ON_RESUME),
                // New activity is launched after receiving result in base activity.
                transition(LaunchForResultActivity.class, ON_PAUSE),
                transition(CallbackTrackingActivity.class, ON_CREATE),
                transition(CallbackTrackingActivity.class, ON_START),
                transition(CallbackTrackingActivity.class, ON_RESUME),
                transition(CallbackTrackingActivity.class, ON_TOP_POSITION_GAINED)),
                "launchForResultAndLaunchAfterOnResult");
    }

    @Test
    public void testLaunchAndDestroy() throws Exception {
        final Activity activity = launchActivityAndWait(FirstActivity.class);

        activity.finish();
        waitAndAssertActivityStates(state(activity, ON_DESTROY));

        assertLaunchAndDestroySequence(FirstActivity.class, getTransitionLog());
    }

    @Test
    public void testTrampoline() throws Exception {
        testTrampolineLifecycle(false /* newTask */);
    }

    @Test
    public void testTrampolineNewTask() throws Exception {
        testTrampolineLifecycle(true /* newTask */);
    }

    /**
     * Verifies that activity start from a trampoline will have the correct lifecycle and complete
     * in time. The expected lifecycle is that the trampoline will skip ON_START - ON_STOP part of
     * the usual sequence, and will go straight to ON_DESTROY after creation.
     */
    private void testTrampolineLifecycle(boolean newTask) throws Exception {
        // Run activity start manually (without using instrumentation) to make it async and measure
        // time from the request correctly.
        // TODO verify
        final Launcher launcher = new Launcher(NoDisplayActivity.class)
                .setFlags(FLAG_ACTIVITY_NEW_TASK)
                .setExtraFlags(EXTRA_LAUNCH_ACTIVITY, EXTRA_FINISH_IN_ON_CREATE)
                .setExpectedState(ON_DESTROY)
                .setNoInstance();
        if (newTask) {
            launcher.setExtraFlags(EXTRA_NEW_TASK);
        }
        launcher.launch();
        waitAndAssertActivityStates(state(CallbackTrackingActivity.class, ON_TOP_POSITION_GAINED));

        assertEntireSequence(Arrays.asList(
                transition(NoDisplayActivity.class, ON_CREATE),
                transition(CallbackTrackingActivity.class, ON_CREATE),
                transition(CallbackTrackingActivity.class, ON_START),
                transition(CallbackTrackingActivity.class, ON_POST_CREATE),
                transition(CallbackTrackingActivity.class, ON_RESUME),
                transition(CallbackTrackingActivity.class, ON_TOP_POSITION_GAINED),
                transition(NoDisplayActivity.class, ON_DESTROY)),
                getTransitionLog(), "trampolineLaunch");
    }

    /** @see #testTrampolineWithAnotherProcess */
    @Test
    public void testTrampolineAnotherProcessNewTask() {
        testTrampolineWithAnotherProcess();
    }

    /**
     * Same as {@link #testTrampolineAnotherProcessNewTask()}, but with a living second process.
     */
    @Test
    public void testTrampolineAnotherExistingProcessNewTask() {
        // Start the second process before running the test. It is to make a specific path that the
        // the activity may be started when checking visibility instead of attaching its process.
        mContext.startActivity(new Intent(mContext, SecondProcessCallbackTrackingActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        waitAndAssertActivityStates(
                state(SecondProcessCallbackTrackingActivity.class, ON_TOP_POSITION_GAINED));
        getTransitionLog().clear();

        testTrampolineWithAnotherProcess();
    }

    /**
     * Simulates X starts Y in the same task, and Y starts Z in another task then finishes itself:
     * <pre>
     * Top Task B: SecondProcessCallbackTrackingActivity (Z)
     *     Task A: SecondProcessCallbackTrackingActivity (Y) (finishing)
     *             FirstActivity (X)
     * </pre>
     * Expect Y to become invisible and then destroyed when the transition is done.
     */
    private void testTrampolineWithAnotherProcess() {
        // Use another process so its lifecycle won't be affected by the caller activity.
        final Intent intent2 = new Intent(mContext, SecondProcessCallbackTrackingActivity.class)
                .addFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_MULTIPLE_TASK);
        final Intent intent1 = new Intent(mContext, SecondProcessCallbackTrackingActivity.class)
                .putExtra(EXTRA_START_ACTIVITY_IN_ON_CREATE, intent2)
                .putExtra(EXTRA_FINISH_IN_ON_CREATE, true);
        mContext.startActivity(new Intent(mContext, FirstActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(EXTRA_START_ACTIVITY_WHEN_IDLE, intent1));
        waitAndAssertActivityStates(state(SecondProcessCallbackTrackingActivity.class, ON_DESTROY));
    }

    @Test
    public void testRelaunchResumed() throws Exception {
        final Activity activity = launchActivityAndWait(FirstActivity.class);

        getTransitionLog().clear();
        getInstrumentation().runOnMainSync(activity::recreate);
        waitAndAssertActivityStates(state(activity, ON_RESUME));

        assertRelaunchSequence(FirstActivity.class, getTransitionLog(), ON_RESUME);
    }

    @Test
    public void testRelaunchPaused() throws Exception {
        final Activity pausedActivity = launchActivityAndWait(FirstActivity.class);
        final Activity translucentActivity = launchActivityAndWait(TranslucentActivity.class);

        waitAndAssertActivityStates(occludedActivityState(pausedActivity, translucentActivity));

        getTransitionLog().clear();
        getInstrumentation().runOnMainSync(pausedActivity::recreate);
        waitAndAssertActivityStates(state(pausedActivity, ON_PAUSE));

        assertRelaunchSequence(FirstActivity.class, getTransitionLog(), ON_PAUSE);
    }

    @Test
    public void testRelaunchStopped() throws Exception {
        final Activity stoppedActivity = launchActivityAndWait(FirstActivity.class);
        launchActivityAndWait(SecondActivity.class);

        waitAndAssertActivityStates(state(stoppedActivity, ON_STOP));

        getTransitionLog().clear();
        getInstrumentation().runOnMainSync(stoppedActivity::recreate);
        waitAndAssertActivityStates(state(stoppedActivity, ON_STOP));

        assertRelaunchSequence(FirstActivity.class, getTransitionLog(), ON_STOP);
    }

    @Test
    public void testRelaunchConfigurationChangedWhileBecomingVisible() throws Exception {
        if (!supportsRotation()) {
            // Skip rotation test if device doesn't support it.
            return;
        }

        final ActivityOptions options = ActivityOptions.makeBasic();
        options.setLaunchWindowingMode(WINDOWING_MODE_FULLSCREEN);

        final Activity becomingVisibleActivity =
                new Launcher(FirstActivity.class).setOptions(options).launch();
        final Activity translucentActivity =
                new Launcher(TranslucentActivity.class).setOptions(options).launch();
        final Activity topOpaqueActivity =
                new Launcher(SecondActivity.class).setOptions(options).launch();

        waitAndAssertActivityStates(
                state(becomingVisibleActivity, ON_STOP),
                state(translucentActivity, ON_STOP));

        final RotationSession rotationSession = createManagedRotationSession();
        if (!supportsLockedUserRotation(
                rotationSession, translucentActivity.getDisplay().getDisplayId())) {
            return;
        }

        getTransitionLog().clear();

        final int current = rotationSession.get();
        // Set new rotation to cause a configuration change.
        switch (current) {
            case ROTATION_0:
            case ROTATION_180:
                rotationSession.set(ROTATION_90);
                break;
            case ROTATION_90:
            case ROTATION_270:
                rotationSession.set(ROTATION_0);
                break;
            default:
                fail("Unknown rotation:" + current);
        }

        // Assert that the top activity was relaunched.
        waitAndAssertActivityStates(state(topOpaqueActivity, ON_RESUME));
        assertRelaunchSequence(SecondActivity.class, getTransitionLog(), ON_RESUME);

        // Finish the top activity
        getTransitionLog().clear();
        topOpaqueActivity.finish();

        // Assert that the translucent activity and the activity visible behind it were
        // relaunched.
        waitAndAssertActivityStates(state(becomingVisibleActivity, ON_PAUSE),
                state(translucentActivity, ON_RESUME));

        assertSequence(FirstActivity.class, getTransitionLog(),
                Arrays.asList(ON_DESTROY, ON_CREATE, ON_START, ON_RESUME, ON_PAUSE),
                "becomingVisiblePaused");
        final List<String> expectedSequence =
                Arrays.asList(ON_DESTROY, ON_CREATE, ON_START, ON_RESUME);
        assertSequence(TranslucentActivity.class, getTransitionLog(), expectedSequence,
                "becomingVisibleResumed");
    }

    @Test
    public void testLaunchActivityWithFlagForwardResult() throws Exception {
        final ActivityMonitor resultMonitor = getInstrumentation().addMonitor(
                ResultActivity.class.getName(), null /* result */, false /* block */);

        new Launcher(LaunchForwardResultActivity.class)
                .setExpectedState(ON_STOP)
                .setNoInstance()
                .launch();

        final Activity resultActivity = getInstrumentation()
                .waitForMonitorWithTimeout(resultMonitor, 5000);
        getInstrumentation().runOnMainSync(resultActivity::finish);
        waitAndAssertActivityStates(state(LaunchForwardResultActivity.class,
                ON_TOP_POSITION_GAINED));

        // verify the result have sent back to original activity
        final List<String> expectedSequence =
                Arrays.asList(ON_CREATE, ON_START, ON_POST_CREATE, ON_RESUME,
                        ON_TOP_POSITION_GAINED, ON_TOP_POSITION_LOST, ON_PAUSE, ON_STOP,
                        ON_RESTART, ON_START, ON_ACTIVITY_RESULT, ON_RESUME,
                        ON_TOP_POSITION_GAINED);
        assertSequence(LaunchForwardResultActivity.class, getTransitionLog(),
                expectedSequence, "becomingVisibleResumed");
    }

    @Test
    public void testOnActivityResult() throws Exception {
        new Launcher(LaunchForResultActivity.class)
                .customizeIntent(LaunchForResultActivity.forwardFlag(
                        EXTRA_FINISH_IN_ON_RESUME,
                        EXTRA_SKIP_TOP_RESUMED_STATE))
                .setSkipTopResumedStateCheck()
                .launch();

        final List<String> expectedSequence =
                Arrays.asList(ON_CREATE, ON_START, ON_POST_CREATE, ON_RESUME,
                        ON_PAUSE, ON_ACTIVITY_RESULT, ON_RESUME);
        waitForActivityTransitions(LaunchForResultActivity.class, expectedSequence);

        // TODO(b/79218023): First activity might also be stopped before getting result.
        final List<String> sequenceWithStop =
                Arrays.asList(ON_CREATE, ON_START, ON_POST_CREATE, ON_RESUME,
                        ON_PAUSE, ON_STOP, ON_RESTART, ON_START, ON_ACTIVITY_RESULT, ON_RESUME);
        assertSequenceMatchesOneOf(LaunchForResultActivity.class, getTransitionLog(),
                Arrays.asList(expectedSequence, sequenceWithStop), "activityResult");
    }

    @Test
    public void testOnActivityResultAfterStop() throws Exception {
        final ActivityMonitor resultMonitor = getInstrumentation().addMonitor(
                ResultActivity.class.getName(), null /* result */, false /* block */);
        final ActivityMonitor launchMonitor = getInstrumentation().addMonitor(
                LaunchForResultActivity.class.getName(), null/* result */, false /* block */);
        new Launcher(LaunchForResultActivity.class)
                // TODO (b/127741025) temporarily use setNoInstance, because startActivitySync will
                // cause launch timeout when more than 2 activities start consecutively.
                .setNoInstance()
                .setSkipTopResumedStateCheck()
                .setExpectedState(ON_STOP)
                .launch();
        final Activity activity = getInstrumentation()
                .waitForMonitorWithTimeout(launchMonitor, 5000);
        waitAndAssertActivityStates(state(activity, ON_STOP));
        final Activity resultActivity = getInstrumentation()
                .waitForMonitorWithTimeout(resultMonitor, 5000);
        waitAndAssertActivityStates(state(resultActivity, ON_TOP_POSITION_GAINED));
        getInstrumentation().runOnMainSync(resultActivity::finish);

        final boolean isTranslucent = isTranslucent(activity);

        final List<String> expectedSequences;
        if (isTranslucent) {
            expectedSequences = Arrays.asList(ON_CREATE, ON_START, ON_POST_CREATE, ON_RESUME,
                    ON_PAUSE, ON_ACTIVITY_RESULT, ON_RESUME);
        } else {
            expectedSequences = Arrays.asList(ON_CREATE, ON_START, ON_POST_CREATE, ON_RESUME,
                    ON_PAUSE, ON_STOP, ON_RESTART, ON_START, ON_ACTIVITY_RESULT, ON_RESUME);
        }
        waitForActivityTransitions(LaunchForResultActivity.class, expectedSequences);

        assertSequence(LaunchForResultActivity.class, getTransitionLog(), expectedSequences,
                "activityResult");
    }

    @Test
    public void testOnPostCreateAfterRecreateInOnResume() throws Exception {
        final Activity trackingActivity = launchActivityAndWait(CallbackTrackingActivity.class);

        // Call "recreate" and assert sequence
        getTransitionLog().clear();
        getInstrumentation().runOnMainSync(trackingActivity::recreate);
        waitAndAssertActivityStates(state(trackingActivity, ON_TOP_POSITION_GAINED));

        assertSequence(CallbackTrackingActivity.class, getTransitionLog(),
                Arrays.asList(ON_TOP_POSITION_LOST, ON_PAUSE, ON_STOP, ON_DESTROY,
                        ON_CREATE, ON_START, ON_POST_CREATE, ON_RESUME, ON_TOP_POSITION_GAINED),
                "recreate");
    }

    @Test
    public void testOnPostCreateAfterRecreateInOnPause() throws Exception {
        final Activity trackingActivity = launchActivityAndWait(CallbackTrackingActivity.class);

        // Launch translucent activity, which will make the first one paused.
        final Activity translucentActivity = launchActivityAndWait(TranslucentActivity.class);

        // Wait for first activity to become paused
        waitAndAssertActivityStates(occludedActivityState(trackingActivity, translucentActivity));

        // Call "recreate" and assert sequence
        getTransitionLog().clear();
        getInstrumentation().runOnMainSync(trackingActivity::recreate);
        waitAndAssertActivityStates(occludedActivityState(trackingActivity, translucentActivity));

        assertSequence(CallbackTrackingActivity.class, getTransitionLog(),
                Arrays.asList(ON_STOP, ON_DESTROY, ON_CREATE, ON_START,
                        ON_POST_CREATE, ON_RESUME, ON_PAUSE),
                "recreate");
    }

    @Test
    public void testOnPostCreateAfterRecreateInOnStop() throws Exception {
        // Launch first activity
        final Activity trackingActivity = launchActivityAndWait(CallbackTrackingActivity.class);
        // Launch second activity to cover and stop first
        final Activity secondActivity = launchActivityAndWait(SecondActivity.class);
        // Wait for first activity to become stopped
        waitAndAssertActivityStates(state(trackingActivity, ON_STOP));

        // Call "recreate" and assert sequence
        getTransitionLog().clear();
        getInstrumentation().runOnMainSync(trackingActivity::recreate);
        waitAndAssertActivityStates(state(trackingActivity, ON_STOP));

        final List<String> callbacks;
        if (isTranslucent(secondActivity)) {
            callbacks = Arrays.asList(ON_STOP, ON_DESTROY, ON_CREATE, ON_START,
                    ON_POST_CREATE, ON_RESUME, ON_PAUSE);
        } else {
            callbacks = Arrays.asList(ON_DESTROY, ON_CREATE, ON_START,
                    ON_POST_CREATE, ON_RESUME, ON_PAUSE, ON_STOP);
        }

        assertSequence(
                CallbackTrackingActivity.class, getTransitionLog(), callbacks, "recreate");
    }

    /**
     * The following test ensures an activity is brought back if its process is ended in the
     * background.
     */
    @Test
    public void testRestoreFromKill() throws Exception {
        final LaunchActivityBuilder builder = getLaunchActivityBuilder();
        final ComponentName targetActivity = builder.getTargetActivity();

        // Launch activity whose process will be killed
        builder.execute();

        // Get the TaskDisplayArea feature id for the targetActivity so we could launch the
        // testActivity into the same TDA.
        final int targetActivityTDAFeatureId = mWmState.getTaskDisplayAreaFeatureId(targetActivity);
        ActivityOptions activityOptions = getLaunchOptionsForFullscreen();
        activityOptions.setLaunchTaskDisplayAreaFeatureId(targetActivityTDAFeatureId);

        // Start fullscreen activity in another process to put original activity in background.
        final Activity testActivity = new Launcher(FirstActivity.class)
                .setOptions(activityOptions)
                .launch();
        final boolean isTranslucent = isTranslucent(testActivity);
        mWmState.waitForActivityState(
                targetActivity, isTranslucent ? STATE_PAUSED : STATE_STOPPED);

        // Only try to kill targetActivity if the top activity isn't translucent. If the top
        // activity is translucent then targetActivity will be visible, so the process will be
        // started again really quickly.
        if (!isTranslucent) {
            // Kill first activity
            AmUtils.runKill(targetActivity.getPackageName(), true /* wait */);
        }

        // Return back to first activity
        pressBackButton();

        // Verify activity is resumed
        mWmState.waitForValidState(targetActivity);
        mWmState.assertResumedActivity("Originally launched activity should be resumed",
                targetActivity);
    }

    /**
     * Tests that recreate request from an activity is executed immediately.
     */
    @Test
    public void testLocalRecreate() throws Exception {
        // Launch the activity that will recreate itself
        final Activity recreatingActivity = new Launcher(SingleTopActivity.class)
                .launch();

        // Retrieve the activity Task Display Area.
        int recreatingActivityTDAFeatureId = mWmState.getTaskDisplayAreaFeatureId(recreatingActivity
                .getComponentName());
        ActivityOptions activityOptions = getLaunchOptionsForFullscreen();
        activityOptions.setLaunchTaskDisplayAreaFeatureId(recreatingActivityTDAFeatureId);
        // Launch second activity to cover and stop first
        final Activity secondActivity = new Launcher(SecondActivity.class)
                .setFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_MULTIPLE_TASK)
                .setOptions(activityOptions)
                .launch();

        // Wait for first activity to become occluded
        waitAndAssertActivityStates(state(recreatingActivity, ON_STOP));

        // Launch the activity again to recreate
        getTransitionLog().clear();
        new Launcher(SingleTopActivity.class)
                .setFlags(FLAG_ACTIVITY_NEW_TASK)
                .setExtraFlags(EXTRA_RECREATE)
                // There is no guarantee that the activity will be relaunched after on top resume
                // state received. Skip recording the top resume state to simplify the verification.
                .setSkipTopResumedStateCheck()
                .launch();

        // Wait for activity to relaunch and resume
        final List<String> expectedRelaunchSequence;
        if (isTranslucent(secondActivity)) {
            expectedRelaunchSequence = Arrays.asList(ON_NEW_INTENT, ON_RESUME,
                    ON_PAUSE, ON_STOP, ON_DESTROY, ON_CREATE, ON_START, ON_POST_CREATE, ON_RESUME);
        } else {
            expectedRelaunchSequence = Arrays.asList(ON_RESTART, ON_START, ON_NEW_INTENT, ON_RESUME,
                    ON_PAUSE, ON_STOP, ON_DESTROY, ON_CREATE, ON_START, ON_POST_CREATE, ON_RESUME);
        }

        waitForActivityTransitions(SingleTopActivity.class, expectedRelaunchSequence);
        assertSequence(SingleTopActivity.class, getTransitionLog(), expectedRelaunchSequence,
                "recreate");
    }

    @Test
    public void testOnNewIntent() throws Exception {
        // Launch a singleTop activity
        launchActivityAndWait(SingleTopActivity.class);

        TransitionVerifier.assertLaunchSequence(SingleTopActivity.class, getTransitionLog());

        // Try to launch again
        getTransitionLog().clear();
        new Launcher(SingleTopActivity.class)
                .setFlags(FLAG_ACTIVITY_NEW_TASK)
                .setNoInstance()
                .launch();

        // Verify that the first activity was paused, new intent was delivered and resumed again
        assertSequence(SingleTopActivity.class, getTransitionLog(),
                Arrays.asList(ON_TOP_POSITION_LOST, ON_PAUSE, ON_NEW_INTENT, ON_RESUME,
                        ON_TOP_POSITION_GAINED), "newIntent");
    }

    @Test
    public void testOnNewIntentFromHidden() throws Exception {
        // Launch a singleTop activity
        final Activity singleTopActivity = launchActivityAndWait(SingleTopActivity.class);
        TransitionVerifier.assertLaunchSequence(SingleTopActivity.class, getTransitionLog());

        int singleTopActivityTDAFeatureId = mWmState.getTaskDisplayAreaFeatureId(singleTopActivity
                .getComponentName());
        ActivityOptions activityOptions = getLaunchOptionsForFullscreen();
        activityOptions.setLaunchTaskDisplayAreaFeatureId(singleTopActivityTDAFeatureId);

        // Launch something on top
        final Activity secondActivity = new Launcher(SecondActivity.class)
                .setFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_MULTIPLE_TASK)
                .setOptions(activityOptions)
                .launch();

        waitAndAssertActivityStates(state(singleTopActivity, ON_STOP));

        // Try to launch again
        getTransitionLog().clear();
        new Launcher(SingleTopActivity.class)
                .setFlags(FLAG_ACTIVITY_NEW_TASK)
                .setNoInstance()
                .launch();

        // Verify that the first activity was restarted, new intent was delivered and resumed again
        final List<String> expectedSequence;
        if (isTranslucent(singleTopActivity)) {
            expectedSequence = Arrays.asList(ON_NEW_INTENT, ON_RESUME, ON_TOP_POSITION_GAINED);
        } else {
            expectedSequence = Arrays.asList(ON_RESTART, ON_START, ON_NEW_INTENT, ON_RESUME,
                    ON_TOP_POSITION_GAINED);
        }
        assertSequence(SingleTopActivity.class, getTransitionLog(), expectedSequence, "newIntent");
    }

    @Test
    public void testOnNewIntentFromPaused() throws Exception {
        // Launch a singleTop activity
        final Activity singleTopActivity = launchActivityAndWait(SingleTopActivity.class);
        TransitionVerifier.assertLaunchSequence(SingleTopActivity.class, getTransitionLog());

        // Launch translucent activity, which will make the first one paused.
        launchActivityAndWait(TranslucentActivity.class);

        // Wait for the activity below to pause
        waitAndAssertActivityStates(state(singleTopActivity, ON_PAUSE));

        // Try to launch again
        getTransitionLog().clear();
        new Launcher(SingleTopActivity.class)
                .setFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TOP)
                .setNoInstance()
                .launch();

        // Wait for the activity to resume again
        // Verify that the new intent was delivered and resumed again
        final List<String> expectedSequence =
                Arrays.asList(ON_NEW_INTENT, ON_RESUME, ON_TOP_POSITION_GAINED);
        waitForActivityTransitions(SingleTopActivity.class, expectedSequence);
        assertSequence(SingleTopActivity.class, getTransitionLog(), expectedSequence, "newIntent");
    }

    @Test
    public void testFinishInOnCreate() throws Exception {
        verifyFinishAtStage(ResultActivity.class, EXTRA_FINISH_IN_ON_CREATE,
                Arrays.asList(ON_CREATE, ON_DESTROY), "onCreate");
    }

    @Test
    public void testFinishInOnCreateNoDisplay() throws Exception {
        verifyFinishAtStage(NoDisplayActivity.class, EXTRA_FINISH_IN_ON_CREATE,
                Arrays.asList(ON_CREATE, ON_DESTROY), "onCreate");
    }

    @Test
    public void testFinishInOnStart() throws Exception {
        verifyFinishAtStage(ResultActivity.class, EXTRA_FINISH_IN_ON_START,
                Arrays.asList(ON_CREATE, ON_START, ON_POST_CREATE, ON_STOP,
                        ON_DESTROY), "onStart");
    }

    @Test
    public void testFinishInOnStartNoDisplay() throws Exception {
        verifyFinishAtStage(NoDisplayActivity.class, EXTRA_FINISH_IN_ON_START,
                Arrays.asList(ON_CREATE, ON_START, ON_POST_CREATE, ON_STOP,
                        ON_DESTROY), "onStart");
    }

    @Test
    public void testFinishInOnResume() throws Exception {
        verifyFinishAtStage(ResultActivity.class, EXTRA_FINISH_IN_ON_RESUME,
                true /* skipTopResumedState */,
                Arrays.asList(ON_CREATE, ON_START, ON_POST_CREATE, ON_RESUME, ON_PAUSE, ON_STOP,
                        ON_DESTROY), "onResume");
    }

    @Test
    public void testFinishInOnResumeNoDisplay() throws Exception {
        verifyFinishAtStage(NoDisplayActivity.class, EXTRA_FINISH_IN_ON_RESUME,
                true /* skipTopResumedState */,
                Arrays.asList(ON_CREATE, ON_START, ON_POST_CREATE, ON_RESUME, ON_PAUSE, ON_STOP,
                        ON_DESTROY), "onResume");
    }

    private void verifyFinishAtStage(Class<? extends Activity> activityClass,
            String finishStageExtra, List<String> expectedSequence,
            String stageName) throws Exception {
        verifyFinishAtStage(activityClass, finishStageExtra, false /* skipTopResumedState */,
                expectedSequence, stageName);
    }

    private void verifyFinishAtStage(Class<? extends Activity> activityClass,
            String finishStageExtra, boolean skipTopResumedState,
            List<String> expectedSequence,
            String stageName) throws Exception {
        final Launcher launcher = new Launcher(activityClass)
                .setExpectedState(ON_DESTROY)
                .setExtraFlags(finishStageExtra)
                .setNoInstance();
        if (skipTopResumedState) {
            launcher.setSkipTopResumedStateCheck();
        }
        launcher.launch();

        waitAndAssertActivityTransitions(activityClass, expectedSequence, "finish in " + stageName);
    }

    @Test
    public void testFinishInOnPause() throws Exception {
        verifyFinishAtStage(ResultActivity.class, EXTRA_FINISH_IN_ON_PAUSE, "onPause",
                TranslucentActivity.class);
    }

    @Test
    public void testFinishInOnStop() throws Exception {
        verifyFinishAtStage(ResultActivity.class, EXTRA_FINISH_IN_ON_STOP, "onStop",
                FirstActivity.class);
    }

    @Test
    public void testFinishBelowDialogActivity() throws Exception {
        verifyFinishAtStage(ResultActivity.class, EXTRA_FINISH_IN_ON_PAUSE, "onPause",
                TranslucentCallbackTrackingActivity.class);
    }

    private void verifyFinishAtStage(Class<? extends Activity> activityClass,
            String finishStageExtra, String stageName, Class<? extends Activity> launchOnTopClass)
            throws Exception {

        // Activity will finish itself after onResume, so need to launch an extra activity on
        // top to get it there.
        new Launcher(activityClass)
                .setExtraFlags(finishStageExtra)
                .launch();

        // Launch an activity on top, which will make the first one paused or stopped.
        launchActivityAndWait(launchOnTopClass);

        final List<String> expectedSequence = getLaunchAndDestroySequence(activityClass);
        waitAndAssertActivityTransitions(activityClass, expectedSequence, "finish in " + stageName);
    }

    @Test
    public void testFinishBelowTranslucentActivityAfterDelay() throws Exception {
        final Activity bottomActivity = launchActivityAndWait(CallbackTrackingActivity.class);

        launchActivityAndWait(TranslucentCallbackTrackingActivity.class);
        waitAndAssertActivityStates(state(bottomActivity, ON_PAUSE));
        getTransitionLog().clear();

        waitForIdle();
        bottomActivity.finish();
        waitAndAssertActivityStates(state(bottomActivity, ON_DESTROY));
        assertEmptySequence(TranslucentCallbackTrackingActivity.class,
                getTransitionLog(), "finishBelow");
    }

    @Test
    public void testFinishBelowFullscreenActivityAfterDelay() throws Exception {
        final Activity bottomActivity = launchActivityAndWait(CallbackTrackingActivity.class);

        launchActivityAndWait(FirstActivity.class);
        waitAndAssertActivityStates(state(bottomActivity, ON_STOP));
        getTransitionLog().clear();

        waitForIdle();
        bottomActivity.finish();
        waitAndAssertActivityStates(state(bottomActivity, ON_DESTROY));
        assertEmptySequence(FirstActivity.class, getTransitionLog(), "finishBelow");
    }

    @Test
    public void testSingleTopActivityOnActivityResultNewTask() throws Exception {
        testSingleTopActivityForResult(true /* newTask */);
    }

    @Test
    public void testSingleTopActivityOnActivityResult() throws Exception {
        testSingleTopActivityForResult(false /* newTask */);
    }

    private void testSingleTopActivityForResult(boolean newTask) throws Exception {
        // Launch a singleTop activity
        final Launcher launcher = new Launcher(SingleTopActivity.class)
                .setExtraFlags(EXTRA_LAUNCH_ACTIVITY);

        if (newTask) {
            launcher.setExtraFlags(EXTRA_NEW_TASK);
        }
        final Activity activity = launcher.launch();
        waitAndAssertActivityStates(state(activity, ON_TOP_POSITION_GAINED));

        // Verify the result have been sent back to original activity
        assertTransitionObserved(getTransitionLog(),
                transition(SingleTopActivity.class, ON_ACTIVITY_RESULT),"activityResult");
    }

    @Test
    public void testLaunchOnUserLeaveHint() throws Exception {
        new Launcher(FirstActivity.class)
                .setExtraFlags(EXTRA_ACTIVITY_ON_USER_LEAVE_HINT)
                .launch();

        getTransitionLog().clear();
        launchActivityAndWait(SecondActivity.class);
        waitAndAssertActivityStates(state(FirstActivity.class, ON_STOP));

        assertTransitionObserved(getTransitionLog(),
                transition(FirstActivity.class, ON_USER_LEAVE_HINT),"userLeaveHint");
    }

    @Test
    public void testLaunchOnUserLeaveHintWithNoUserAction() throws Exception {
        new Launcher(FirstActivity.class)
                .setExtraFlags(EXTRA_ACTIVITY_ON_USER_LEAVE_HINT)
                .launch();

        getTransitionLog().clear();
        new Launcher(SecondActivity.class)
                .setFlags(FLAG_ACTIVITY_NO_USER_ACTION | FLAG_ACTIVITY_NEW_TASK)
                .launch();
        waitAndAssertActivityStates(state(FirstActivity.class, ON_STOP));

        assertTransitionNotObserved(getTransitionLog(),
                transition(FirstActivity.class, ON_USER_LEAVE_HINT),"userLeaveHint");
    }
}
