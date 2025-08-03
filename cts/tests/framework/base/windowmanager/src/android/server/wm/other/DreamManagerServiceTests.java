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

package android.server.wm.other;

import static android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN;
import static android.server.wm.ComponentNameUtils.getWindowName;
import static android.server.wm.UiDeviceUtils.pressUnlockButton;
import static android.server.wm.WindowManagerState.STATE_STOPPED;
import static android.server.wm.app.Components.SHOW_WHEN_LOCKED_ACTIVITY;
import static android.server.wm.app.Components.TEST_ACTIVITY;
import static android.server.wm.app.Components.TEST_DREAM_SERVICE;
import static android.server.wm.app.Components.TEST_STUBBORN_DREAM_SERVICE;
import static android.view.Display.DEFAULT_DISPLAY;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import android.content.ComponentName;
import android.platform.test.annotations.Presubmit;
import android.server.wm.ActivityManagerTestBase;
import android.server.wm.Condition;
import android.server.wm.DreamCoordinator;
import android.server.wm.LockScreenSession;
import android.server.wm.RotationSession;
import android.server.wm.app.Components;
import android.view.Surface;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

@Presubmit
public class DreamManagerServiceTests extends ActivityManagerTestBase {

    // Maximum timeout in seconds after which the dream should have finished willingly
    private static final int ACTIVITY_STOP_MAX_TIMEOUT = 3;

    // Maximum timeout in seconds after which the dream should have been forcefully stopped
    private static final int DREAM_FORCE_STOP_MAX_TIMEOUT = 15;

    private ComponentName mDreamActivityName;

    private DreamCoordinator mDreamCoordinator = new DreamCoordinator(mContext);

    @Before
    public void setup() {
        mDreamCoordinator.setup();
    }

    @After
    public void reset() {
        mDreamCoordinator.restoreDefaults();
        mDreamCoordinator.stopDream();
    }

    private void waitAndAssertDreamActivityGone(int maxTimeOutInSeconds) {
        String msg = "Dream Activity must be gone";
        Condition.waitFor(new Condition<>(msg, this::isDreamActivityGone)
                .setRetryLimit(maxTimeOutInSeconds)
                .setOnFailure(o -> fail(msg))
        );
    }

    private boolean isDreamActivityGone() {
        mWmState.computeState();
        return !mWmState.containsWindow(getWindowName(mDreamActivityName))
                && !mWmState.containsActivity(mDreamActivityName);
    }

    private void startFullscreenTestActivity() {
        launchActivity(TEST_ACTIVITY, WINDOWING_MODE_FULLSCREEN);
        waitAndAssertTopResumedActivity(TEST_ACTIVITY, DEFAULT_DISPLAY,
                "Test activity should be the top resumed activity");
        mWmState.assertVisibility(TEST_ACTIVITY, true);
    }

    private void startFullscreenTestActivityWithShowWhenLocked() {
        launchActivity(SHOW_WHEN_LOCKED_ACTIVITY, WINDOWING_MODE_FULLSCREEN);
        waitAndAssertTopResumedActivity(SHOW_WHEN_LOCKED_ACTIVITY, DEFAULT_DISPLAY,
                "Test activity with android:showWhenLocked should be the top resumed activity");
        mWmState.assertVisibility(SHOW_WHEN_LOCKED_ACTIVITY, true);
    }


    @Test
    public void testStartAndStopDream() throws Exception {
        startFullscreenTestActivity();
        mDreamActivityName = mDreamCoordinator.setActiveDream(TEST_DREAM_SERVICE);

        mDreamCoordinator.startDream();
        waitAndAssertTopResumedActivity(mDreamActivityName, DEFAULT_DISPLAY,
                "Dream activity should be the top resumed activity");
        mWmState.waitForValidState(mWmState.getHomeActivityName());
        mWmState.assertVisibility(mWmState.getHomeActivityName(), false);
        mWmState.waitForValidState(TEST_ACTIVITY);
        mWmState.assertVisibility(TEST_ACTIVITY, false);

        assertTrue(mDreamCoordinator.isDreaming());

        mDreamCoordinator.stopDream();
        mWmState.waitAndAssertActivityRemoved(mDreamActivityName);

        //To reduce flakiness, dismiss keyguard in case the device locked while test was running
        pressUnlockButton();

        waitAndAssertTopResumedActivity(TEST_ACTIVITY, DEFAULT_DISPLAY,
                "Previous top activity should show when dream is stopped");
    }

    @Test
    public void testShowWhenLockedActivityUnderDream() throws Exception {
        startFullscreenTestActivityWithShowWhenLocked();
        mDreamActivityName = mDreamCoordinator.setActiveDream(TEST_DREAM_SERVICE);

        mDreamCoordinator.startDream();
        waitAndAssertTopResumedActivity(mDreamActivityName, DEFAULT_DISPLAY,
                "Dream activity should be the top resumed activity");
        mWmState.waitForValidState(mWmState.getHomeActivityName());
        mWmState.assertVisibility(mWmState.getHomeActivityName(), false);
        mWmState.waitForValidState(SHOW_WHEN_LOCKED_ACTIVITY);
        mWmState.assertVisibility(SHOW_WHEN_LOCKED_ACTIVITY, false);

        assertTrue(mDreamCoordinator.isDreaming());

        mDreamCoordinator.stopDream();
        mWmState.waitAndAssertActivityRemoved(mDreamActivityName);

        waitAndAssertTopResumedActivity(SHOW_WHEN_LOCKED_ACTIVITY, DEFAULT_DISPLAY,
                "ShowWhenLocked activity should be resumed when dream is stopped");
        mWmState.assertVisibility(SHOW_WHEN_LOCKED_ACTIVITY, true);
    }


    @Test
    public void testDreamServiceStopsTimely() throws Exception {
        try (DreamingState state = new DreamingState(TEST_DREAM_SERVICE)) {
            mDreamCoordinator.stopDream();
            waitAndAssertDreamActivityGone(ACTIVITY_STOP_MAX_TIMEOUT);
            assertFalse(mDreamCoordinator.isDreaming());
        }
    }

    @Test
    public void testForceStopStubbornDream() throws Exception {
        startFullscreenTestActivity();
        mDreamActivityName = mDreamCoordinator.setActiveDream(TEST_STUBBORN_DREAM_SERVICE);

        mDreamCoordinator.startDream();
        waitAndAssertTopResumedActivity(mDreamActivityName, DEFAULT_DISPLAY,
                "Dream activity should be the top resumed activity");
        mWmState.waitForValidState(mWmState.getHomeActivityName());
        mWmState.assertVisibility(mWmState.getHomeActivityName(), false);
        mWmState.waitForValidState(TEST_ACTIVITY);
        mWmState.assertVisibility(TEST_ACTIVITY, false);

        mDreamCoordinator.stopDream();

        waitAndAssertDreamActivityGone(DREAM_FORCE_STOP_MAX_TIMEOUT);

        assertFalse(mDreamCoordinator.isDreaming());
        pressUnlockButton();
        waitAndAssertTopResumedActivity(TEST_ACTIVITY, DEFAULT_DISPLAY,
                "Previous top activity should show when dream is stopped");
    }

    @Test
    public void testDreamNotFinishAfterRotation() {
        assumeTrue("Skipping test: no rotation support", supportsRotation());

        final RotationSession rotationSession = createManagedRotationSession();
        rotationSession.set(Surface.ROTATION_0);
        mDreamActivityName = mDreamCoordinator.setActiveDream(TEST_DREAM_SERVICE);
        mDreamCoordinator.startDream();
        rotationSession.set(Surface.ROTATION_90);

        waitAndAssertTopResumedActivity(mDreamActivityName, DEFAULT_DISPLAY,
                "Dream activity should be the top resumed activity");
    }

    @Test
    public void testStartActivityDoesNotWakeAndIsNotResumed() {
        assumeFalse(dismissDreamOnActivityStart());

        try (DreamingState state = new DreamingState(TEST_DREAM_SERVICE)) {
            launchActivity(Components.TEST_ACTIVITY);
            mWmState.waitForActivityState(Components.TEST_ACTIVITY, STATE_STOPPED);
            assertTrue(mDreamCoordinator.isDreaming());
        }
    }

    @Test
    public void testStartActivityWakesDevice() {
        assumeTrue(dismissDreamOnActivityStart());

        try (DreamingState state = new DreamingState(TEST_DREAM_SERVICE)) {
            launchActivity(TEST_ACTIVITY);
            state.waitForDreamGone();
            assertFalse(mDreamCoordinator.isDreaming());
            waitAndAssertTopResumedActivity(TEST_ACTIVITY, DEFAULT_DISPLAY,
                    "Test activity should be the top resumed activity");
            mWmState.assertVisibility(TEST_ACTIVITY, true);
        }
    }

    @Test
    public void testStartTurnScreenOnActivityDoesWake() {
        try (DreamingState state = new DreamingState(TEST_DREAM_SERVICE)) {
            launchActivity(Components.TURN_SCREEN_ON_ACTIVITY);

            state.waitForDreamGone();
            waitAndAssertTopResumedActivity(Components.TURN_SCREEN_ON_ACTIVITY,
                    DEFAULT_DISPLAY, "TurnScreenOnActivity should resume through dream");
        }
    }

    @Test
    public void testStartTurnScreenOnAttrActivityDoesWake() {
        try (DreamingState state = new DreamingState(TEST_DREAM_SERVICE)) {
            launchActivity(Components.TURN_SCREEN_ON_ATTR_ACTIVITY);
            pressUnlockButton();
            state.waitForDreamGone();
            waitAndAssertTopResumedActivity(Components.TURN_SCREEN_ON_ATTR_ACTIVITY,
                    DEFAULT_DISPLAY, "TurnScreenOnAttrActivity should resume through dream");
        }
    }

    @Test
    public void testStartActivityOnKeyguardLocked() {
        assumeTrue(supportsLockScreen());
        assumeFalse(dismissDreamOnActivityStart());

        final LockScreenSession lockScreenSession = createManagedLockScreenSession();
        lockScreenSession.setLockCredential();
        try (DreamingState state = new DreamingState(TEST_DREAM_SERVICE)) {
            launchActivityNoWait(Components.TEST_ACTIVITY);
            waitAndAssertActivityState(Components.TEST_ACTIVITY, STATE_STOPPED,
                "Activity must be started and stopped");
            assertTrue(mDreamCoordinator.isDreaming());

            launchActivity(Components.TURN_SCREEN_ON_SHOW_ON_LOCK_ACTIVITY);
            state.waitForDreamGone();
            waitAndAssertTopResumedActivity(Components.TURN_SCREEN_ON_SHOW_ON_LOCK_ACTIVITY,
                    DEFAULT_DISPLAY, "TurnScreenOnShowOnLockActivity should resume through dream");
            assertFalse(mDreamCoordinator.isDreaming());
        }
    }

    @Test
    public void testStartActivityDismissesDreamOnKeyguardLocked() {
        assumeTrue(supportsLockScreen());
        assumeTrue(dismissDreamOnActivityStart());

        final LockScreenSession lockScreenSession = createManagedLockScreenSession();
        lockScreenSession.setLockCredential();
        try (DreamingState state = new DreamingState(TEST_DREAM_SERVICE)) {
            launchActivity(SHOW_WHEN_LOCKED_ACTIVITY);
            state.waitForDreamGone();
            waitAndAssertTopResumedActivity(SHOW_WHEN_LOCKED_ACTIVITY,
                    DEFAULT_DISPLAY, "Activity should dismiss dream");
            assertFalse(mDreamCoordinator.isDreaming());
        }
    }

    private class DreamingState implements AutoCloseable {
        public DreamingState(ComponentName dream) {
            mDreamActivityName = mDreamCoordinator.setActiveDream(dream);
            mDreamCoordinator.startDream();
            waitAndAssertDreaming();
        }

        @Override
        public void close() {
            mDreamCoordinator.stopDream();
        }

        public void waitAndAssertDreaming() {
            waitAndAssertTopResumedActivity(mDreamActivityName, DEFAULT_DISPLAY,
                    "Dream activity should be the top resumed activity");
            mWmState.waitForActivityState(mWmState.getHomeActivityName(), STATE_STOPPED);
            mWmState.waitAndAssertVisibilityGone(mWmState.getHomeActivityName());
            assertTrue(mDreamCoordinator.isDreaming());
        }

        public void waitForDreamGone() {
            mWmState.waitForDreamGone();
            assertFalse(mDreamCoordinator.isDreaming());
        }
    }
}
