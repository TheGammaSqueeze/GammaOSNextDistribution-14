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

package android.server.wm.keyguard;

import static android.server.wm.WindowManagerState.STATE_STOPPED;
import static android.server.wm.WindowManagerState.TRANSIT_ACTIVITY_OPEN;
import static android.server.wm.WindowManagerState.TRANSIT_KEYGUARD_GOING_AWAY;
import static android.server.wm.WindowManagerState.TRANSIT_KEYGUARD_GOING_AWAY_ON_WALLPAPER;
import static android.server.wm.WindowManagerState.TRANSIT_KEYGUARD_OCCLUDE;
import static android.server.wm.WindowManagerState.TRANSIT_KEYGUARD_UNOCCLUDE;
import static android.server.wm.app.Components.DISABLE_PREVIEW_ACTIVITY;
import static android.server.wm.app.Components.SHOW_WHEN_LOCKED_ATTR_NO_PREVIEW_ACTIVITY;
import static android.server.wm.app.Components.SHOW_WHEN_LOCKED_ATTR_REMOVE_ATTR_NO_PREVIEW_ACTIVITY;
import static android.server.wm.app.Components.SHOW_WHEN_LOCKED_NO_PREVIEW_ACTIVITY;
import static android.server.wm.app.Components.SHOW_WHEN_LOCKED_WITH_DIALOG_NO_PREVIEW_ACTIVITY;
import static android.server.wm.app.Components.WALLPAPAER_ACTIVITY;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import android.platform.test.annotations.Presubmit;
import android.server.wm.ActivityManagerTestBase;
import android.server.wm.LockScreenSession;

import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

/**
 * Build/Install/Run:
 *     atest CtsWindowManagerDeviceKeyguard:KeyguardTransitionTests
 */
@Presubmit
@android.server.wm.annotation.Group2
public class KeyguardTransitionTests extends ActivityManagerTestBase {

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
        assumeFalse(ENABLE_SHELL_TRANSITIONS);
        assumeTrue(supportsInsecureLock());
        assumeFalse(isUiModeLockedToVrHeadset());
    }

    @Test
    public void testUnlock() {
        List<String> expectedTransitionList = Arrays.asList(TRANSIT_KEYGUARD_GOING_AWAY,
                TRANSIT_KEYGUARD_GOING_AWAY_ON_WALLPAPER);
        final LockScreenSession lockScreenSession = createManagedLockScreenSession();
        launchActivity(DISABLE_PREVIEW_ACTIVITY);
        lockScreenSession.gotoKeyguard().unlockDevice();
        mWmState.computeState(DISABLE_PREVIEW_ACTIVITY);
        // The AOSP flow is checking if the current transit is TRANSIT_KEYGUARD_GOING_AWAY and
        // if the visible apps have FLAG_SHOW_WALLPAPER and if both conditions are true the transit
        // will be changed to TRANSIT_KEYGUARD_GOING_AWAY_ON_WALLPAPER. For multiple screen devices,
        // both conditions are true, because the launcher is visible and has this flag.
        assertTrue("Picked wrong transition",
                expectedTransitionList.contains(mWmState.getDefaultDisplayLastTransition()));
    }

    @Test
    public void testUnlockWallpaper() {
        final LockScreenSession lockScreenSession = createManagedLockScreenSession();
        launchActivity(WALLPAPAER_ACTIVITY);
        lockScreenSession.gotoKeyguard().unlockDevice();
        mWmState.computeState(WALLPAPAER_ACTIVITY);
        assertEquals("Picked wrong transition", TRANSIT_KEYGUARD_GOING_AWAY_ON_WALLPAPER,
                mWmState.getDefaultDisplayLastTransition());
    }

    @Test
    public void testOcclude() {
        createManagedLockScreenSession().gotoKeyguard();
        launchActivity(SHOW_WHEN_LOCKED_NO_PREVIEW_ACTIVITY);
        mWmState.computeState(SHOW_WHEN_LOCKED_NO_PREVIEW_ACTIVITY);
        assertEquals("Picked wrong transition", TRANSIT_KEYGUARD_OCCLUDE,
                mWmState.getDefaultDisplayLastTransition());
    }

    @Test
    public void testUnocclude() {
        createManagedLockScreenSession().gotoKeyguard();
        launchActivity(SHOW_WHEN_LOCKED_NO_PREVIEW_ACTIVITY);
        launchActivity(DISABLE_PREVIEW_ACTIVITY);
        mWmState.waitForKeyguardShowingAndNotOccluded();
        mWmState.computeState();
        assertEquals("Picked wrong transition", TRANSIT_KEYGUARD_UNOCCLUDE,
                mWmState.getDefaultDisplayLastTransition());
    }

    @Test
    public void testDismissKeyguardIfInsecure() {
        createManagedLockScreenSession().gotoKeyguard();
        launchActivityWithDismissKeyguardIfInsecure(SHOW_WHEN_LOCKED_NO_PREVIEW_ACTIVITY);
        mWmState.computeState(SHOW_WHEN_LOCKED_NO_PREVIEW_ACTIVITY);
        assertEquals("Picked wrong transition", TRANSIT_KEYGUARD_GOING_AWAY,
                mWmState.getDefaultDisplayLastTransition());
    }

    @Test
    public void testNewActivityDuringOccluded() {
        final LockScreenSession lockScreenSession = createManagedLockScreenSession();
        launchActivity(SHOW_WHEN_LOCKED_NO_PREVIEW_ACTIVITY);
        lockScreenSession.gotoKeyguard(SHOW_WHEN_LOCKED_NO_PREVIEW_ACTIVITY);
        launchActivity(SHOW_WHEN_LOCKED_WITH_DIALOG_NO_PREVIEW_ACTIVITY);
        mWmState.computeState(SHOW_WHEN_LOCKED_WITH_DIALOG_NO_PREVIEW_ACTIVITY);
        assertEquals("Picked wrong transition", TRANSIT_ACTIVITY_OPEN,
                mWmState.getDefaultDisplayLastTransition());
    }

    @Test
    public void testNewDismissKeyguardIfInsecureActivityDuringOccluded() {
        final LockScreenSession lockScreenSession = createManagedLockScreenSession();
        launchActivity(SHOW_WHEN_LOCKED_NO_PREVIEW_ACTIVITY);
        lockScreenSession.gotoKeyguard(SHOW_WHEN_LOCKED_NO_PREVIEW_ACTIVITY);
        launchActivityWithDismissKeyguardIfInsecure(
                SHOW_WHEN_LOCKED_WITH_DIALOG_NO_PREVIEW_ACTIVITY);
        mWmState.computeState(SHOW_WHEN_LOCKED_WITH_DIALOG_NO_PREVIEW_ACTIVITY);
        assertEquals("Picked wrong transition", TRANSIT_ACTIVITY_OPEN,
                mWmState.getDefaultDisplayLastTransition());
    }

    @Test
    public void testOccludeManifestAttr() {
        final LockScreenSession lockScreenSession = createManagedLockScreenSession();
        lockScreenSession.gotoKeyguard();
        separateTestJournal();
        launchActivity(SHOW_WHEN_LOCKED_ATTR_NO_PREVIEW_ACTIVITY);
        mWmState.computeState(SHOW_WHEN_LOCKED_ATTR_NO_PREVIEW_ACTIVITY);
        assertEquals("Picked wrong transition", TRANSIT_KEYGUARD_OCCLUDE,
                mWmState.getDefaultDisplayLastTransition());
        assertSingleLaunch(SHOW_WHEN_LOCKED_ATTR_NO_PREVIEW_ACTIVITY);
    }

    @Test
    public void testOccludeAttrRemove() {
        final LockScreenSession lockScreenSession = createManagedLockScreenSession();
        lockScreenSession.gotoKeyguard();
        separateTestJournal();
        launchActivity(SHOW_WHEN_LOCKED_ATTR_REMOVE_ATTR_NO_PREVIEW_ACTIVITY);
        mWmState.computeState(SHOW_WHEN_LOCKED_ATTR_REMOVE_ATTR_NO_PREVIEW_ACTIVITY);
        assertEquals("Picked wrong transition", TRANSIT_KEYGUARD_OCCLUDE,
                mWmState.getDefaultDisplayLastTransition());
        assertSingleLaunch(SHOW_WHEN_LOCKED_ATTR_REMOVE_ATTR_NO_PREVIEW_ACTIVITY);

        // Waiting for the standard keyguard since
        // {@link SHOW_WHEN_LOCKED_ATTR_REMOVE_ATTR_NO_PREVIEW_ACTIVITY} called
        // {@link Activity#showWhenLocked(boolean)} and removed the attribute.
        lockScreenSession.gotoKeyguard();
        separateTestJournal();
        // Waiting for {@link SHOW_WHEN_LOCKED_ATTR_REMOVE_ATTR_NO_PREVIEW_ACTIVITY} stopped since it
        // already lost show-when-locked attribute.
        launchActivityNoWait(SHOW_WHEN_LOCKED_ATTR_REMOVE_ATTR_NO_PREVIEW_ACTIVITY);
        mWmState.waitForActivityState(SHOW_WHEN_LOCKED_ATTR_REMOVE_ATTR_NO_PREVIEW_ACTIVITY,
                STATE_STOPPED);
        assertSingleStartAndStop(SHOW_WHEN_LOCKED_ATTR_REMOVE_ATTR_NO_PREVIEW_ACTIVITY);
    }

    @Test
    public void testNewActivityDuringOccludedWithAttr() {
        final LockScreenSession lockScreenSession = createManagedLockScreenSession();
        launchActivityInFullscreen(SHOW_WHEN_LOCKED_ATTR_NO_PREVIEW_ACTIVITY);
        lockScreenSession.gotoKeyguard(SHOW_WHEN_LOCKED_ATTR_NO_PREVIEW_ACTIVITY);
        launchActivity(SHOW_WHEN_LOCKED_WITH_DIALOG_NO_PREVIEW_ACTIVITY);
        mWmState.computeState(SHOW_WHEN_LOCKED_WITH_DIALOG_NO_PREVIEW_ACTIVITY);
        assertEquals("Picked wrong transition", TRANSIT_ACTIVITY_OPEN,
                mWmState.getDefaultDisplayLastTransition());
    }
}
