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
 * limitations under the License.
 */

package android.server.wm.activity;

import static android.server.wm.WindowManagerState.STATE_PAUSED;
import static android.server.wm.WindowManagerState.STATE_RESUMED;
import static android.server.wm.overlay.Components.TranslucentFloatingActivity.ACTION_FINISH;
import static android.server.wm.overlay.Components.TranslucentFloatingActivity.EXTRA_FADE_EXIT;

import static com.google.common.truth.Truth.assertThat;

import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Bundle;
import android.platform.test.annotations.Presubmit;
import android.server.wm.ActivityManagerTestBase;
import android.server.wm.WindowManagerState;
import android.server.wm.overlay.Components;

import androidx.annotation.Nullable;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Build/Install/Run:
 * atest CtsWindowManagerDeviceWindow:ActivityRecordInputSinkTests
 */
@Presubmit
public class ActivityRecordInputSinkTests extends ActivityManagerTestBase {

    private static final String APP_SELF = "android.server.wm.cts";
    private static final String APP_A =
            android.server.wm.second.Components.class.getPackage().getName();

    private static final ComponentName TEST_ACTIVITY =
            new ComponentName(APP_SELF, "android.server.wm.activity.ActivityRecordInputSinkTestsActivity");

    private static final ComponentName OVERLAY_IN_SAME_UID =
            Components.TranslucentFloatingActivity.getComponent(APP_SELF);
    private static final ComponentName OVERLAY_IN_DIFFERENT_UID =
            Components.TranslucentFloatingActivity.getComponent(APP_A);
    private static final ComponentName TRAMPOLINE_DIFFERENT_UID =
            Components.TrampolineActivity.getComponent(APP_A);

    private int mTouchCount;

    @Before
    public void setUp() {
        ActivityRecordInputSinkTestsActivity.sButtonClickCount.set(0);
    }

    @After
    public void tearDown() {
        stopTestPackage(APP_A);
    }

    @Test
    public void testOverlappingActivityInNewTask_BlocksTouches() {
        launchActivity(TEST_ACTIVITY);
        touchButtonsAndAssert(true /*expectTouchesToReachActivity*/);

        launchActivityInNewTask(OVERLAY_IN_SAME_UID);
        mWmState.waitAndAssertActivityState(OVERLAY_IN_SAME_UID, STATE_RESUMED);
        touchButtonsAndAssert(false /*expectTouchesToReachActivity*/);

        mContext.sendBroadcast(new Intent(Components.TranslucentFloatingActivity.ACTION_FINISH));
        mWmState.waitAndAssertActivityRemoved(OVERLAY_IN_SAME_UID);
        touchButtonsAndAssert(true /*expectTouchesToReachActivity*/);
    }

    @Test
    public void testOverlappingActivityInSameTaskSameUid_DoesNotBlocksTouches() {
        launchActivity(TEST_ACTIVITY);
        touchButtonsAndAssert(true /*expectTouchesToReachActivity*/);

        launchActivityInSameTask(OVERLAY_IN_SAME_UID);
        mWmState.waitAndAssertActivityState(OVERLAY_IN_SAME_UID, STATE_RESUMED);
        touchButtonsAndAssert(true /*expectTouchesToReachActivity*/);
    }

    @Test
    public void testOverlappingActivityInSameTaskDifferentUid_DoesNotBlocksTouches() {
        launchActivity(TEST_ACTIVITY);
        touchButtonsAndAssert(true /*expectTouchesToReachActivity*/);

        launchActivityInSameTask(OVERLAY_IN_DIFFERENT_UID);
        mWmState.waitAndAssertActivityState(OVERLAY_IN_DIFFERENT_UID, STATE_RESUMED);
        mWmState.assertActivityDisplayed(OVERLAY_IN_DIFFERENT_UID);
        touchButtonsAndAssert(true /*expectTouchesToReachActivity*/);
    }

    @Test
    public void testOverlappingActivityInSameTaskTrampolineDifferentUid_DoesNotBlockTouches() {
        launchActivity(TEST_ACTIVITY);
        touchButtonsAndAssert(true /*expectTouchesToReachActivity*/);

        launchActivityInSameTask(TRAMPOLINE_DIFFERENT_UID,
                Components.TrampolineActivity.buildTrampolineExtra(OVERLAY_IN_DIFFERENT_UID));
        mWmState.waitAndAssertActivityState(OVERLAY_IN_DIFFERENT_UID, STATE_RESUMED);
        touchButtonsAndAssert(true /*expectTouchesToReachActivity*/);
    }

    @Test
    public void testOverlappingActivitySandwich_BlocksTouches() {
        Intent intent = new Intent();
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setComponent(TRAMPOLINE_DIFFERENT_UID);
        intent.replaceExtras(Components.TrampolineActivity.buildTrampolineExtra(TEST_ACTIVITY,
                OVERLAY_IN_DIFFERENT_UID));
        mContext.startActivity(intent);

        mWmState.waitAndAssertActivityState(TEST_ACTIVITY, STATE_PAUSED);
        mWmState.waitAndAssertActivityState(OVERLAY_IN_DIFFERENT_UID, STATE_RESUMED);
        touchButtonsAndAssert(false /*expectTouchesToReachActivity*/);

        mContext.sendBroadcast(new Intent(Components.TranslucentFloatingActivity.ACTION_FINISH));
        mWmState.waitAndAssertActivityRemoved(OVERLAY_IN_DIFFERENT_UID);
        touchButtonsAndAssert(true /*expectTouchesToReachActivity*/);
    }

    @Test
    public void testOverlappingActivitySandwichDuringAnimation_DoesNotBlockTouches() {
        Intent intent = new Intent();
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setComponent(TRAMPOLINE_DIFFERENT_UID);
        intent.replaceExtras(Components.TrampolineActivity.buildTrampolineExtra(TEST_ACTIVITY,
                OVERLAY_IN_DIFFERENT_UID));
        mContext.startActivity(intent);

        mWmState.waitAndAssertActivityState(TEST_ACTIVITY, STATE_PAUSED);
        mWmState.waitAndAssertActivityState(OVERLAY_IN_DIFFERENT_UID, STATE_RESUMED);
        touchButtonsAndAssert(false);

        int displayId = mWmState.getTaskByActivity(OVERLAY_IN_DIFFERENT_UID).mDisplayId;
        mContext.sendBroadcast(new Intent(ACTION_FINISH).putExtra(EXTRA_FADE_EXIT, true));
        assertThat(mWmState.waitForAppTransitionRunningOnDisplay(displayId)).isTrue();
        touchButtonsAndAssert(true /*expectTouchesToReachActivity*/, false /*waitForAnimation*/);
    }

    private void launchActivityInSameTask(ComponentName componentName) {
        launchActivityInSameTask(componentName, null);
    }

    private void launchActivityInSameTask(ComponentName componentName, @Nullable Bundle extras) {
        Intent intent = new Intent(ActivityRecordInputSinkTestsActivity.LAUNCH_ACTIVITY_ACTION);
        intent.setPackage(APP_SELF);
        intent.putExtra(ActivityRecordInputSinkTestsActivity.COMPONENT_EXTRA, componentName);
        intent.putExtra(ActivityRecordInputSinkTestsActivity.EXTRA_EXTRA, extras);
        mContext.sendBroadcast(intent);
    }


    private void touchButtonsAndAssert(boolean expectTouchesToReachActivity) {
        touchButtonsAndAssert(expectTouchesToReachActivity, true /* waitForAnimation */);
    }

    private void touchButtonsAndAssert(
            boolean expectTouchesToReachActivity, boolean waitForAnimation) {
        WindowManagerState.Activity activity = mWmState.getActivity(TEST_ACTIVITY);
        int displayId = activity.getTask().mDisplayId;
        Rect bounds = activity.getBounds();
        bounds.offset(0, -bounds.height() / 3);
        mTouchHelper.tapOnCenter(bounds, displayId, waitForAnimation);
        mTouchCount += (expectTouchesToReachActivity ? 1 : 0);
        mInstrumentation.waitForIdleSync();
        assertThat(ActivityRecordInputSinkTestsActivity.sButtonClickCount.get())
                .isEqualTo(mTouchCount);

        bounds.offset(0, 2 * bounds.height() / 3);
        mTouchHelper.tapOnCenter(bounds, displayId, waitForAnimation);
        mTouchCount += (expectTouchesToReachActivity ? 1 : 0);
        mInstrumentation.waitForIdleSync();
        assertThat(ActivityRecordInputSinkTestsActivity.sButtonClickCount.get())
                .isEqualTo(mTouchCount);
    }

}
