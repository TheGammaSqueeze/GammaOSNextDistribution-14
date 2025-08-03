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

package android.server.wm.activity;

import static android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM;
import static android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN;
import static android.server.wm.ShellCommandHelper.executeShellCommand;
import static android.server.wm.WindowManagerState.TRANSIT_ACTIVITY_CLOSE;
import static android.server.wm.WindowManagerState.TRANSIT_ACTIVITY_OPEN;
import static android.server.wm.WindowManagerState.TRANSIT_TASK_CLOSE;
import static android.server.wm.WindowManagerState.TRANSIT_TASK_OPEN;
import static android.server.wm.WindowManagerState.TRANSIT_TRANSLUCENT_ACTIVITY_CLOSE;
import static android.server.wm.WindowManagerState.TRANSIT_WALLPAPER_CLOSE;
import static android.server.wm.WindowManagerState.TRANSIT_WALLPAPER_INTRA_CLOSE;
import static android.server.wm.WindowManagerState.TRANSIT_WALLPAPER_INTRA_OPEN;
import static android.server.wm.WindowManagerState.TRANSIT_WALLPAPER_OPEN;
import static android.server.wm.app.Components.BOTTOM_ACTIVITY;
import static android.server.wm.app.Components.BOTTOM_NON_RESIZABLE_ACTIVITY;
import static android.server.wm.app.Components.BottomActivity.EXTRA_BOTTOM_WALLPAPER;
import static android.server.wm.app.Components.BottomActivity.EXTRA_STOP_DELAY;
import static android.server.wm.app.Components.TOP_ACTIVITY;
import static android.server.wm.app.Components.TOP_NON_RESIZABLE_ACTIVITY;
import static android.server.wm.app.Components.TOP_NON_RESIZABLE_WALLPAPER_ACTIVITY;
import static android.server.wm.app.Components.TOP_WALLPAPER_ACTIVITY;
import static android.server.wm.app.Components.TRANSLUCENT_TOP_ACTIVITY;
import static android.server.wm.app.Components.TRANSLUCENT_TOP_NON_RESIZABLE_ACTIVITY;
import static android.server.wm.app.Components.TRANSLUCENT_TOP_WALLPAPER_ACTIVITY;
import static android.server.wm.app.Components.TopActivity.EXTRA_FINISH_DELAY;

import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.platform.test.annotations.Presubmit;
import android.server.wm.ActivityManagerTestBase;
import android.server.wm.Condition;

import org.junit.Before;
import org.junit.Test;

/**
 * This test tests the transition type selection logic in ActivityManager/WindowManager.
 * BottomActivity is started first, then TopActivity, and we check the transition type that the
 * system selects when TopActivity enters or exits under various setups.
 *
 * <p>Note that we only require the correct transition type to be reported (eg.
 * TRANSIT_ACTIVITY_OPEN, TRANSIT_TASK_CLOSE, TRANSIT_WALLPAPER_OPEN, etc.). The exact animation is
 * unspecified and can be overridden.
 *
 * <p>Build/Install/Run: atest CtsWindowManagerDeviceActivity:TransitionSelectionTests
 */
@Presubmit
public class TransitionSelectionTests extends ActivityManagerTestBase {

    @Before
    public void setup() {
        assumeFalse(ENABLE_SHELL_TRANSITIONS);
    }

    // Test activity open/close under normal timing
    @Test
    public void testOpenActivity_NeitherWallpaper() {
        testOpenActivity(
                false /*bottomWallpaper*/,
                false /*topWallpaper*/,
                false /*slowStop*/,
                TRANSIT_ACTIVITY_OPEN);
    }

    @Test
    public void testCloseActivity_NeitherWallpaper() {
        testCloseActivity(
                false /*bottomWallpaper*/,
                false /*topWallpaper*/,
                false /*slowStop*/,
                TRANSIT_ACTIVITY_CLOSE);
    }

    @Test
    public void testOpenActivity_BottomWallpaper() {
        testOpenActivity(
                true /*bottomWallpaper*/,
                false /*topWallpaper*/,
                false /*slowStop*/,
                TRANSIT_WALLPAPER_CLOSE);
    }

    @Test
    public void testCloseActivity_BottomWallpaper() {
        testCloseActivity(
                true /*bottomWallpaper*/,
                false /*topWallpaper*/,
                false /*slowStop*/,
                TRANSIT_WALLPAPER_OPEN);
    }

    @Test
    public void testOpenActivity_BothWallpaper() {
        testOpenActivity(
                true /*bottomWallpaper*/,
                true /*topWallpaper*/,
                false /*slowStop*/,
                TRANSIT_WALLPAPER_INTRA_OPEN);
    }

    @Test
    public void testCloseActivity_BothWallpaper() {
        testCloseActivity(
                true /*bottomWallpaper*/,
                true /*topWallpaper*/,
                false /*slowStop*/,
                TRANSIT_WALLPAPER_INTRA_CLOSE);
    }

    // ------------------------------------------------------------------------//

    // Test task open/close under normal timing
    @Test
    public void testOpenTask_NeitherWallpaper() {
        assumeFalse(isAutomotive() && hasSplitscreenMultitaskingFeature());
        testOpenTask(
                false /*bottomWallpaper*/,
                false /*topWallpaper*/,
                true /* topResizable */,
                false /*slowStop*/,
                TRANSIT_TASK_OPEN,
                WINDOWING_MODE_FULLSCREEN);
    }

    @Test
    public void testOpenFreeformTask_NeitherWallpaper() {
        assumeTrue(supportsFreeform());
        testOpenTask(
                false /*bottomWallpaper*/,
                false /*topWallpaper*/,
                true /* topResizable */,
                false /*slowStop*/,
                TRANSIT_TASK_OPEN,
                WINDOWING_MODE_FREEFORM);
    }

    @Test
    public void testCloseTask_NeitherWallpaper() {
        assumeFalse(isAutomotive() && hasSplitscreenMultitaskingFeature());
        testCloseTask(
                false /*bottomWallpaper*/,
                false /*topWallpaper*/,
                true /* topResizable */,
                false /*slowStop*/,
                TRANSIT_TASK_CLOSE,
                WINDOWING_MODE_FULLSCREEN);
    }

    @Test
    public void testOpenTask_BottomWallpaper_TopNonResizable() {
        testOpenTask(
                true /*bottomWallpaper*/,
                false /*topWallpaper*/,
                false /* topResizable */,
                false /*slowStop*/,
                TRANSIT_WALLPAPER_CLOSE,
                WINDOWING_MODE_FULLSCREEN);
    }

    @Test
    public void testOpenFreeformTask_BottomWallpaper_TopResizable() {
        assumeTrue(supportsFreeform());
        testOpenTask(
                true /*bottomWallpaper*/,
                false /*topWallpaper*/,
                true /* topResizable */,
                false /*slowStop*/,
                TRANSIT_TASK_OPEN,
                WINDOWING_MODE_FREEFORM);
    }

    @Test
    public void testCloseTask_BottomWallpaper_TopNonResizable() {
        testCloseTask(
                true /*bottomWallpaper*/,
                false /*topWallpaper*/,
                false /* topResizable */,
                false /*slowStop*/,
                TRANSIT_WALLPAPER_OPEN,
                WINDOWING_MODE_FULLSCREEN);
    }

    @Test
    public void testCloseFreeformTask_BottomWallpaper_TopResizable() {
        assumeTrue(supportsFreeform());
        testCloseTask(
                true /*bottomWallpaper*/,
                false /*topWallpaper*/,
                true /* topResizable */,
                false /*slowStop*/,
                TRANSIT_TASK_CLOSE,
                WINDOWING_MODE_FREEFORM);
    }

    @Test
    public void testOpenTask_BothWallpaper() {
        testOpenTask(
                true /*bottomWallpaper*/,
                true /*topWallpaper*/,
                false /* topResizable */,
                false /*slowStop*/,
                TRANSIT_WALLPAPER_INTRA_OPEN,
                WINDOWING_MODE_FULLSCREEN);
    }

    @Test
    public void testCloseTask_BothWallpaper() {
        testCloseTask(
                true /*bottomWallpaper*/,
                true /*topWallpaper*/,
                false /* topResizable */,
                false /*slowStop*/,
                TRANSIT_WALLPAPER_INTRA_CLOSE,
                WINDOWING_MODE_FULLSCREEN);
    }

    // ------------------------------------------------------------------------//

    // Test activity close -- bottom activity slow in stopping
    // These simulate the case where the bottom activity is resumed
    // before AM receives its activitiyStopped
    @Test
    public void testCloseActivity_NeitherWallpaper_SlowStop() {
        testCloseActivity(
                false /*bottomWallpaper*/,
                false /*topWallpaper*/,
                true /*slowStop*/,
                TRANSIT_ACTIVITY_CLOSE);
    }

    @Test
    public void testCloseActivity_BottomWallpaper_SlowStop() {
        testCloseActivity(
                true /*bottomWallpaper*/,
                false /*topWallpaper*/,
                true /*slowStop*/,
                TRANSIT_WALLPAPER_OPEN);
    }

    @Test
    public void testCloseActivity_BothWallpaper_SlowStop() {
        testCloseActivity(
                true /*bottomWallpaper*/,
                true /*topWallpaper*/,
                true /*slowStop*/,
                TRANSIT_WALLPAPER_INTRA_CLOSE);
    }

    // ------------------------------------------------------------------------//

    // Test task close -- bottom task top activity slow in stopping
    // These simulate the case where the bottom activity is resumed
    // before AM receives its activitiyStopped
    @Test
    public void testCloseTask_NeitherWallpaper_SlowStop() {
        assumeFalse(isAutomotive() && hasSplitscreenMultitaskingFeature());
        testCloseTask(
                false /*bottomWallpaper*/,
                false /*topWallpaper*/,
                true /* topResizable */,
                true /*slowStop*/,
                TRANSIT_TASK_CLOSE,
                WINDOWING_MODE_FULLSCREEN);
    }

    @Test
    public void testCloseTask_BottomWallpaper_TopNonResizable_SlowStop() {
        assumeFalse(isAutomotive() && hasSplitscreenMultitaskingFeature());
        testCloseTask(
                true /*bottomWallpaper*/,
                false /*topWallpaper*/,
                false /* topResizable */,
                true /*slowStop*/,
                TRANSIT_WALLPAPER_OPEN,
                WINDOWING_MODE_FULLSCREEN);
    }

    @Test
    public void testCloseFreeformTask_BottomWallpaper_TopResizable_SlowStop() {
        assumeTrue(supportsFreeform());
        testCloseTask(
                true /*bottomWallpaper*/,
                false /*topWallpaper*/,
                true /* topResizable */,
                true /*slowStop*/,
                TRANSIT_TASK_CLOSE,
                WINDOWING_MODE_FREEFORM);
    }

    @Test
    public void testCloseTask_BothWallpaper_SlowStop() {
        testCloseTask(
                true /*bottomWallpaper*/,
                true /*topWallpaper*/,
                false /* topResizable */,
                true /*slowStop*/,
                TRANSIT_WALLPAPER_INTRA_CLOSE,
                WINDOWING_MODE_FULLSCREEN);
    }

    // ------------------------------------------------------------------------//

    /// Test closing of translucent activity/task
    @Test
    public void testCloseActivity_NeitherWallpaper_Translucent() {
        testCloseActivityTranslucent(
                false /*bottomWallpaper*/,
                false /*topWallpaper*/,
                TRANSIT_TRANSLUCENT_ACTIVITY_CLOSE);
    }

    @Test
    public void testCloseActivity_BottomWallpaper_Translucent() {
        testCloseActivityTranslucent(
                true /*bottomWallpaper*/,
                false /*topWallpaper*/,
                TRANSIT_TRANSLUCENT_ACTIVITY_CLOSE);
    }

    @Test
    public void testCloseActivity_BothWallpaper_Translucent() {
        testCloseActivityTranslucent(
                true /*bottomWallpaper*/, true /*topWallpaper*/, TRANSIT_WALLPAPER_INTRA_CLOSE);
    }

    @Test
    public void testCloseTask_NeitherWallpaper_Translucent() {
        testCloseTaskTranslucent(
                false /*bottomWallpaper*/,
                false /*topWallpaper*/,
                TRANSIT_TRANSLUCENT_ACTIVITY_CLOSE);
    }

    @Test
    public void testCloseTask_BottomWallpaper_Translucent() {
        testCloseTaskTranslucent(
                true /*bottomWallpaper*/,
                false /*topWallpaper*/,
                TRANSIT_TRANSLUCENT_ACTIVITY_CLOSE);
    }

    @Test
    public void testCloseTask_BothWallpaper_Translucent() {
        testCloseTaskTranslucent(
                true /*bottomWallpaper*/, true /*topWallpaper*/, TRANSIT_WALLPAPER_INTRA_CLOSE);
    }

    // ------------------------------------------------------------------------//

    private void testOpenActivity(
            boolean bottomWallpaper,
            boolean topWallpaper,
            boolean slowStop,
            String expectedTransit) {
        testTransitionSelection(
                true /*testOpen*/,
                false /*testNewTask*/,
                bottomWallpaper,
                topWallpaper,
                false /*topTranslucent*/,
                true /* topResizable */,
                slowStop,
                expectedTransit,
                WINDOWING_MODE_FULLSCREEN);
    }

    private void testCloseActivity(
            boolean bottomWallpaper,
            boolean topWallpaper,
            boolean slowStop,
            String expectedTransit) {
        testTransitionSelection(
                false /*testOpen*/,
                false /*testNewTask*/,
                bottomWallpaper,
                topWallpaper,
                false /*topTranslucent*/,
                true /* topResizable */,
                slowStop,
                expectedTransit,
                WINDOWING_MODE_FULLSCREEN);
    }

    private void testOpenTask(
            boolean bottomWallpaper,
            boolean topWallpaper,
            boolean topResizable,
            boolean slowStop,
            String expectedTransit,
            int windowingMode) {
        testTransitionSelection(
                true /*testOpen*/,
                true /*testNewTask*/,
                bottomWallpaper,
                topWallpaper,
                false /*topTranslucent*/,
                topResizable,
                slowStop,
                expectedTransit,
                windowingMode);
    }

    private void testCloseTask(
            boolean bottomWallpaper,
            boolean topWallpaper,
            boolean topResizable,
            boolean slowStop,
            String expectedTransit,
            int windowingMode) {
        testTransitionSelection(
                false /*testOpen*/,
                true /*testNewTask*/,
                bottomWallpaper,
                topWallpaper,
                false /*topTranslucent*/,
                topResizable /* topResizable */,
                slowStop,
                expectedTransit,
                windowingMode);
    }

    private void testCloseActivityTranslucent(
            boolean bottomWallpaper, boolean topWallpaper, String expectedTransit) {
        testTransitionSelection(
                false /*testOpen*/,
                false /*testNewTask*/,
                bottomWallpaper,
                topWallpaper,
                true /*topTranslucent*/,
                true /* topResizable */,
                false /*slowStop*/,
                expectedTransit,
                WINDOWING_MODE_FULLSCREEN);
    }

    private void testCloseTaskTranslucent(
            boolean bottomWallpaper, boolean topWallpaper, String expectedTransit) {
        testTransitionSelection(
                false /*testOpen*/,
                true /*testNewTask*/,
                bottomWallpaper,
                topWallpaper,
                true /*topTranslucent*/,
                true /* topResizable */,
                false /*slowStop*/,
                expectedTransit,
                WINDOWING_MODE_FULLSCREEN);
    }

    // ------------------------------------------------------------------------//

    private void testTransitionSelection(
            boolean testOpen,
            boolean testNewTask,
            boolean bottomWallpaper,
            boolean topWallpaper,
            boolean topTranslucent,
            boolean topResizable,
            boolean testSlowStop,
            String expectedTransit,
            int windowingMode) {
        final ComponentName bottomComponent =
                bottomWallpaper ? BOTTOM_NON_RESIZABLE_ACTIVITY : BOTTOM_ACTIVITY;
        String bottomStartCmd = getAmStartCmd(bottomComponent);
        if (bottomWallpaper) {
            bottomStartCmd += " --ez " + EXTRA_BOTTOM_WALLPAPER + " true";
        }
        if (testSlowStop) {
            bottomStartCmd += " --ei " + EXTRA_STOP_DELAY + " 3000";
        }
        executeShellCommand(bottomStartCmd + " --windowingMode " + windowingMode);

        mWmState.computeState(bottomComponent);

        final ComponentName topActivity;
        if (topTranslucent && !topResizable) {
            topActivity = TRANSLUCENT_TOP_NON_RESIZABLE_ACTIVITY;
        } else if (topTranslucent && topWallpaper) {
            topActivity = TRANSLUCENT_TOP_WALLPAPER_ACTIVITY;
        } else if (topTranslucent) {
            topActivity = TRANSLUCENT_TOP_ACTIVITY;
        } else if (!topResizable && topWallpaper) {
            topActivity = TOP_NON_RESIZABLE_WALLPAPER_ACTIVITY;
        } else if (!topResizable) {
            topActivity = TOP_NON_RESIZABLE_ACTIVITY;
        } else if (topWallpaper) {
            topActivity = TOP_WALLPAPER_ACTIVITY;
        } else {
            topActivity = TOP_ACTIVITY;
        }
        String topStartCmd = getAmStartCmd(topActivity);
        if (testNewTask) {
            topStartCmd += " -f 0x18000000";
        }
        if (!testOpen) {
            topStartCmd += " --ei " + EXTRA_FINISH_DELAY + " 1000";
        }
        topStartCmd += " --windowingMode " + windowingMode;
        // Launch top task in the same display area as the bottom task. CTS tests using multiple
        // tasks assume they will be started in the same task display area.
        int bottomComponentDisplayAreaFeatureId =
                mWmState.getTaskDisplayAreaFeatureId(bottomComponent);
        topStartCmd += " --task-display-area-feature-id " + bottomComponentDisplayAreaFeatureId;
        executeShellCommand(topStartCmd);

        Condition.waitFor(
                "Retrieving correct transition",
                () -> {
                    if (testOpen) {
                        mWmState.computeState(topActivity);
                    } else {
                        mWmState.computeState(bottomComponent);
                    }
                    return expectedTransit.equals(mWmState.getDefaultDisplayLastTransition());
                });
        assertEquals(
                "Picked wrong transition",
                expectedTransit,
                mWmState.getDefaultDisplayLastTransition());
    }

    /**
     * Checks whether the device is automotive
     */
    private boolean isAutomotive() {
        PackageManager pm =  mContext.getPackageManager();
        return pm.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE);
    }

    /**
     * Checks whether the device has automotive splitscreen multitasking feature enabled
     */
    private boolean hasSplitscreenMultitaskingFeature() {
        PackageManager pm = mContext.getPackageManager();
        return pm.hasSystemFeature(/* PackageManager.FEATURE_CAR_SPLITSCREEN_MULTITASKING */
                "android.software.car.splitscreen_multitasking");
    }
}
