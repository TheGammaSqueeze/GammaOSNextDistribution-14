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

import static android.server.wm.activity.lifecycle.LifecycleConstants.ON_PAUSE;
import static android.server.wm.activity.lifecycle.LifecycleConstants.ON_RESTART;
import static android.server.wm.activity.lifecycle.LifecycleConstants.ON_RESUME;
import static android.server.wm.activity.lifecycle.LifecycleConstants.ON_START;
import static android.server.wm.activity.lifecycle.LifecycleConstants.ON_STOP;
import static android.server.wm.activity.lifecycle.TransitionVerifier.assertRestartAndResumeSequence;
import static android.server.wm.activity.lifecycle.TransitionVerifier.assertRestartAndResumeSubSequence;
import static android.server.wm.activity.lifecycle.TransitionVerifier.assertResumeToStopSequence;
import static android.server.wm.activity.lifecycle.TransitionVerifier.assertSequence;
import static android.server.wm.app.Components.PipActivity.EXTRA_ENTER_PIP;

import static org.junit.Assume.assumeTrue;

import android.app.Activity;
import android.platform.test.annotations.Presubmit;
import android.server.wm.LockScreenSession;

import androidx.test.filters.MediumTest;

import com.android.compatibility.common.util.UiAutomatorUtils2;

import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

/**
 * Build/Install/Run:
 *     atest CtsWindowManagerDeviceActivity:ActivityLifecycleKeyguardTests
 */
@MediumTest
@Presubmit
@android.server.wm.annotation.Group3
public class ActivityLifecycleKeyguardTests extends ActivityLifecycleClientTestBase {
    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
        UiAutomatorUtils2.getUiDevice().waitForIdle();
    }

    @Test
    public void testSingleLaunch() throws Exception {
        assumeTrue(supportsSecureLock());
        try (LockScreenSession lockScreenSession =
                    new LockScreenSession(mInstrumentation, mWmState)) {
            lockScreenSession.setLockCredential().gotoKeyguard();

            new Launcher(FirstActivity.class)
                    .setExpectedState(ON_STOP)
                    .setNoInstance()
                    .launch();
            TransitionVerifier.assertLaunchAndStopSequence(FirstActivity.class, getTransitionLog());
        }
    }

    @Test
    public void testKeyguardShowHide() throws Exception {
        assumeTrue(supportsSecureLock());

        // Launch first activity and wait for resume
        final Activity activity = launchActivityAndWait(FirstActivity.class);

        // Show and hide lock screen
        try (LockScreenSession lockScreenSession =
                  new LockScreenSession(mInstrumentation, mWmState)) {
            lockScreenSession.setLockCredential().gotoKeyguard();
            waitAndAssertActivityStates(state(activity, ON_STOP));

            TransitionVerifier.assertLaunchAndStopSequence(FirstActivity.class, getTransitionLog());
            getTransitionLog().clear();
        } // keyguard hidden

        // Verify that activity was resumed
        if (isCar()) {
            assertRestartAndResumeSubSequence(FirstActivity.class, getTransitionLog());
            waitAndAssertActivityCurrentState(activity.getClass(), ON_RESUME);
        } else {
            waitAndAssertActivityStates(state(activity, ON_RESUME));
            assertRestartAndResumeSequence(FirstActivity.class, getTransitionLog());
        }
    }

    @Test
    public void testKeyguardShowHideOverSplitScreen() throws Exception {
        assumeTrue(supportsSecureLock());
        assumeTrue(supportsSplitScreenMultiWindow());

        final Activity secondaryActivity = launchActivityAndWait(SideActivity.class);
        final Activity firstActivity = launchActivityAndWait(FirstActivity.class);

        // Enter split screen
        moveTaskToPrimarySplitScreenAndVerify(firstActivity, secondaryActivity);

        // Show and hide lock screen
        getTransitionLog().clear();
        try (LockScreenSession lockScreenSession =
                    new LockScreenSession(mInstrumentation, mWmState)) {
            lockScreenSession.setLockCredential().gotoKeyguard();
            waitAndAssertActivityStates(state(firstActivity, ON_STOP));
            waitAndAssertActivityStates(state(secondaryActivity, ON_STOP));

            assertResumeToStopSequence(FirstActivity.class, getTransitionLog());
            assertResumeToStopSequence(SideActivity.class, getTransitionLog());
            getTransitionLog().clear();
        } // keyguard hidden

        waitAndAssertActivityStates(state(firstActivity, ON_RESUME),
                state(secondaryActivity, ON_RESUME));
        assertRestartAndResumeSequence(FirstActivity.class, getTransitionLog());
        assertRestartAndResumeSequence(SideActivity.class, getTransitionLog());
    }

    @Test
    public void testKeyguardShowHideOverPip() throws Exception {
        assumeTrue(supportsSecureLock());
        if (!supportsPip()) {
            // Skipping test: no Picture-In-Picture support
            return;
        }

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

        // Show and hide lock screen
        getTransitionLog().clear();
        try (LockScreenSession lockScreenSession =
                    new LockScreenSession(mInstrumentation, mWmState)) {
            lockScreenSession.setLockCredential().gotoKeyguard();
            waitAndAssertActivityStates(state(firstActivity, ON_STOP));
            waitAndAssertActivityStates(state(PipActivity.class, ON_STOP));

            assertResumeToStopSequence(FirstActivity.class, getTransitionLog());
            assertSequence(PipActivity.class, getTransitionLog(),
                    Collections.singletonList(ON_STOP), "keyguardShown");
            getTransitionLog().clear();
        } // keyguard hidden

        // Wait and assert lifecycle
        waitAndAssertActivityStates(state(firstActivity, ON_RESUME),
                state(PipActivity.class, ON_PAUSE));
        assertRestartAndResumeSequence(FirstActivity.class, getTransitionLog());
        assertSequence(PipActivity.class, getTransitionLog(),
                Arrays.asList(ON_RESTART, ON_START, ON_RESUME, ON_PAUSE), "keyguardGone");
    }
}
