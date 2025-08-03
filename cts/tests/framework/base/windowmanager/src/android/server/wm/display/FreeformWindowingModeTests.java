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

package android.server.wm.display;

import static android.app.WindowConfiguration.ACTIVITY_TYPE_STANDARD;
import static android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM;
import static android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN;
import static android.content.pm.PackageManager.FEATURE_PC;
import static android.server.wm.WindowManagerState.dpToPx;
import static android.server.wm.app.Components.FREEFORM_ACTIVITY;
import static android.server.wm.app.Components.MULTI_WINDOW_FULLSCREEN_ACTIVITY;
import static android.server.wm.app.Components.MultiWindowFullscreenActivity.ACTION_REQUEST_FULLSCREEN;
import static android.server.wm.app.Components.MultiWindowFullscreenActivity.ACTION_RESTORE_FREEFORM;
import static android.server.wm.app.Components.NON_RESIZEABLE_ACTIVITY;
import static android.server.wm.app.Components.NO_RELAUNCH_ACTIVITY;
import static android.server.wm.app.Components.TEST_ACTIVITY;
import static android.server.wm.app.Components.TestActivity.TEST_ACTIVITY_ACTION_FINISH_SELF;
import static android.view.WindowManager.DISPLAY_IME_POLICY_LOCAL;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.content.ComponentName;
import android.graphics.Rect;
import android.platform.test.annotations.Presubmit;
import android.server.wm.MultiDisplayTestBase;
import android.server.wm.WaitForValidActivityState;
import android.server.wm.WindowManagerState.Task;
import android.view.Display;

import org.junit.Ignore;
import org.junit.Test;

/**
 * Build/Install/Run:
 *     atest CtsWindowManagerDeviceDisplay:FreeformWindowingModeTests
 */
@Presubmit
@android.server.wm.annotation.Group3
public class FreeformWindowingModeTests extends MultiDisplayTestBase {

    private static final int TEST_TASK_OFFSET = 20;
    private static final int TEST_TASK_OFFSET_2 = 100;
    private static final int TEST_TASK_SIZE_1 = 900;
    private static final int TEST_TASK_SIZE_2 = TEST_TASK_SIZE_1 * 2;
    private static final int TEST_TASK_SIZE_DP_1 = 220;
    private static final int TEST_TASK_SIZE_DP_2 = TEST_TASK_SIZE_DP_1 * 2;

    // NOTE: Launching the FreeformActivity will automatically launch the TestActivity
    // with bounds (0, 0, 900, 900)

    @Test
    public void testFreeformWindowManagementSupport() {
        int displayId = Display.DEFAULT_DISPLAY;
        if (supportsMultiDisplay()) {
            displayId = createManagedVirtualDisplaySession()
                    .setSimulateDisplay(true)
                    .setSimulationDisplaySize(1920 /* width */, 1080 /* height */)
                    .setDisplayImePolicy(DISPLAY_IME_POLICY_LOCAL)
                    .createDisplay().mId;
        }
        launchActivityOnDisplay(FREEFORM_ACTIVITY, WINDOWING_MODE_FREEFORM, displayId);

        mWmState.computeState(FREEFORM_ACTIVITY, TEST_ACTIVITY);

        if (!supportsFreeform()) {
            mWmState.assertDoesNotContainStack("Must not contain freeform stack.",
                    WINDOWING_MODE_FREEFORM, ACTIVITY_TYPE_STANDARD);
            return;
        }

        mWmState.assertFrontStackOnDisplay("Freeform stack must be the front stack.",
                WINDOWING_MODE_FREEFORM, ACTIVITY_TYPE_STANDARD, displayId);
        mWmState.assertVisibility(FREEFORM_ACTIVITY, true);
        mWmState.assertVisibility(TEST_ACTIVITY, true);
        mWmState.assertFocusedActivity(
                TEST_ACTIVITY + " must be focused Activity", TEST_ACTIVITY);
        assertEquals(new Rect(0, 0, TEST_TASK_SIZE_1, TEST_TASK_SIZE_1),
                mWmState.getTaskByActivity(TEST_ACTIVITY).getBounds());
    }

    @Test
    @Ignore("b/303085283 This is related to new app compat policy")
    public void testNonResizeableActivityHasFullDisplayBounds() throws Exception {
        createManagedDevEnableNonResizableMultiWindowSession().set(0);
        launchActivity(TEST_ACTIVITY);

        mWmState.computeState(TEST_ACTIVITY);

        final Task testTask = mWmState.getTaskByActivity(TEST_ACTIVITY);
        Rect expectedBounds = testTask.getBounds();
        mBroadcastActionTrigger.doAction(TEST_ACTIVITY_ACTION_FINISH_SELF);
        mWmState.waitFor((wmState) ->
                        !wmState.containsActivity(TEST_ACTIVITY),
                "Waiting for test activity to finish...");

        launchActivity(NON_RESIZEABLE_ACTIVITY, WINDOWING_MODE_FREEFORM);

        mWmState.computeState(NON_RESIZEABLE_ACTIVITY);

        final Task nonResizeableTask = mWmState.getTaskByActivity(NON_RESIZEABLE_ACTIVITY);

        if (nonResizeableTask.isFullscreen()) {
            // If the task is on the fullscreen stack, then we know that it will have bounds that
            // fill the entire display.
            return;
        }

        // If the task is not on the fullscreen stack, then compare the task bounds to the display
        // bounds.
        assertEquals(expectedBounds, nonResizeableTask.getBounds());
    }

    @Test
    public void testActivityLifeCycleOnResizeFreeformTask() throws Exception {
        launchActivity(TEST_ACTIVITY, WINDOWING_MODE_FREEFORM);
        launchActivity(NO_RELAUNCH_ACTIVITY, WINDOWING_MODE_FREEFORM);

        mWmState.computeState(TEST_ACTIVITY, NO_RELAUNCH_ACTIVITY);

        if (!supportsFreeform()) {
            mWmState.assertDoesNotContainStack("Must not contain freeform stack.",
                    WINDOWING_MODE_FREEFORM, ACTIVITY_TYPE_STANDARD);
            return;
        }

        final int displayId = mWmState.getStandardRootTaskByWindowingMode(WINDOWING_MODE_FREEFORM)
                .mDisplayId;
        final int densityDpi =
                mWmState.getDisplay(displayId).getDpi();
        final int testTaskSize1 = dpToPx(TEST_TASK_SIZE_DP_1, densityDpi);
        final int testTaskSize2 = dpToPx(TEST_TASK_SIZE_DP_2, densityDpi);

        resizeActivityTask(TEST_ACTIVITY,
                TEST_TASK_OFFSET, TEST_TASK_OFFSET,
                TEST_TASK_OFFSET + testTaskSize1, TEST_TASK_OFFSET + testTaskSize2);
        resizeActivityTask(NO_RELAUNCH_ACTIVITY,
                TEST_TASK_OFFSET_2, TEST_TASK_OFFSET_2,
                TEST_TASK_OFFSET_2 + testTaskSize1, TEST_TASK_OFFSET_2 + testTaskSize2);

        mWmState.computeState(new WaitForValidActivityState.Builder(TEST_ACTIVITY).build(),
                new WaitForValidActivityState.Builder(NO_RELAUNCH_ACTIVITY).build());

        separateTestJournal();
        resizeActivityTask(TEST_ACTIVITY,
                TEST_TASK_OFFSET, TEST_TASK_OFFSET,
                TEST_TASK_OFFSET + testTaskSize2, TEST_TASK_OFFSET + testTaskSize1);
        resizeActivityTask(NO_RELAUNCH_ACTIVITY,
                TEST_TASK_OFFSET_2, TEST_TASK_OFFSET_2,
                TEST_TASK_OFFSET_2 + testTaskSize2, TEST_TASK_OFFSET_2 + testTaskSize1);
        mWmState.computeState(TEST_ACTIVITY, NO_RELAUNCH_ACTIVITY);

        assertActivityLifecycle(TEST_ACTIVITY, true /* relaunched */);
        assertActivityLifecycle(NO_RELAUNCH_ACTIVITY, false /* relaunched */);
    }

    @Test
    public void testMultiWindowFullscreenRequest() throws Exception {
        assumeTrue("Only test on device guaranteed with a freeform display",
                supportsFreeform() && hasDeviceFeature(FEATURE_PC));
        int displayId = Display.DEFAULT_DISPLAY;
        launchActivityOnDisplay(MULTI_WINDOW_FULLSCREEN_ACTIVITY, displayId);
        mWmState.computeState(MULTI_WINDOW_FULLSCREEN_ACTIVITY);

        mWmState.assertContainsStack("Must has a freeform stack.",
                WINDOWING_MODE_FREEFORM, ACTIVITY_TYPE_STANDARD);

        mBroadcastActionTrigger.doAction(ACTION_REQUEST_FULLSCREEN);
        mWmState.waitForAppTransitionIdleOnDisplay(displayId);
        assertTrue(waitForEnterFullscreen(MULTI_WINDOW_FULLSCREEN_ACTIVITY));

        mBroadcastActionTrigger.doAction(ACTION_RESTORE_FREEFORM);
        mWmState.waitForAppTransitionIdleOnDisplay(displayId);
        assertTrue(waitForExitFullscreen(MULTI_WINDOW_FULLSCREEN_ACTIVITY));
    }

    @Test
    public void testMultiWindowFullscreenRequestRejection() throws Exception {
        assumeTrue("Only test on device guaranteed with a freeform display",
                supportsFreeform() && hasDeviceFeature(FEATURE_PC));
        int displayId = Display.DEFAULT_DISPLAY;
        launchActivityOnDisplay(
                MULTI_WINDOW_FULLSCREEN_ACTIVITY, WINDOWING_MODE_FULLSCREEN, displayId);
        mWmState.computeState(MULTI_WINDOW_FULLSCREEN_ACTIVITY);
        mWmState.assertDoesNotContainStack("Must has no freeform stack.",
                WINDOWING_MODE_FREEFORM, ACTIVITY_TYPE_STANDARD);

        mBroadcastActionTrigger.doAction(ACTION_RESTORE_FREEFORM);
        mWmState.assertDoesNotContainStack("Must has no freeform stack.",
                WINDOWING_MODE_FREEFORM, ACTIVITY_TYPE_STANDARD);
    }

    @Test
    public void testMultiWindowFullscreenOnNonPcDevice() throws Exception {
        assumeTrue("Only test on non-PC device",
                !supportsFreeform() || !hasDeviceFeature(FEATURE_PC));
        int displayId = Display.DEFAULT_DISPLAY;
        launchActivityOnDisplay(MULTI_WINDOW_FULLSCREEN_ACTIVITY, displayId);
        mWmState.computeState(MULTI_WINDOW_FULLSCREEN_ACTIVITY);
        mWmState.assertDoesNotContainStack("Must has no freeform stack.",
                WINDOWING_MODE_FREEFORM, ACTIVITY_TYPE_STANDARD);

        mBroadcastActionTrigger.doAction(ACTION_RESTORE_FREEFORM);
        mWmState.assertDoesNotContainStack("Must has no freeform stack.",
                WINDOWING_MODE_FREEFORM, ACTIVITY_TYPE_STANDARD);

        if (supportsFreeform()) {
            removeRootTasksWithActivityTypes(ACTIVITY_TYPE_STANDARD);
            mWmState.waitAndAssertActivityRemoved(MULTI_WINDOW_FULLSCREEN_ACTIVITY);
            launchActivityOnDisplay(MULTI_WINDOW_FULLSCREEN_ACTIVITY, WINDOWING_MODE_FREEFORM,
                    displayId);
            mWmState.assertContainsStack("Must has a freeform stack.",
                    WINDOWING_MODE_FREEFORM, ACTIVITY_TYPE_STANDARD);
            mBroadcastActionTrigger.doAction(ACTION_REQUEST_FULLSCREEN);
            mWmState.assertContainsStack("Must has a freeform stack.",
                    WINDOWING_MODE_FREEFORM, ACTIVITY_TYPE_STANDARD);
        }
    }

    private boolean waitForEnterFullscreen(ComponentName activityName) {
        return mWmState.waitForWithAmState(wmState -> {
            Task task = wmState.getTaskByActivity(activityName);
            return task != null && task.getWindowingMode() == WINDOWING_MODE_FULLSCREEN;
        }, "checking task windowing mode");
    }

    private boolean waitForExitFullscreen(ComponentName activityName) {
        return mWmState.waitForWithAmState(wmState -> {
            Task task = wmState.getTaskByActivity(activityName);
            return task != null && task.getWindowingMode() != WINDOWING_MODE_FULLSCREEN;
        }, "checking task windowing mode");
    }
}
