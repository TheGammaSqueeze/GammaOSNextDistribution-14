/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.server.wm.taskfragment;

import static android.app.WindowConfiguration.WINDOWING_MODE_MULTI_WINDOW;
import static android.server.wm.WindowManagerState.STATE_RESUMED;
import static android.server.wm.jetpack.second.Components.SECOND_UNTRUSTED_EMBEDDING_ACTIVITY;
import static android.server.wm.jetpack.utils.ActivityEmbeddingUtil.assumeActivityEmbeddingSupportedDevice;
import static android.window.TaskFragmentOrganizer.TASK_FRAGMENT_TRANSIT_CHANGE;
import static android.window.TaskFragmentOrganizer.TASK_FRAGMENT_TRANSIT_OPEN;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Binder;
import android.os.IBinder;
import android.platform.test.annotations.Presubmit;
import android.server.wm.WindowManagerState;
import android.server.wm.WindowManagerState.Task;
import android.window.TaskFragmentInfo;
import android.window.WindowContainerTransaction;

import androidx.annotation.NonNull;

import org.junit.Before;
import org.junit.Test;

/**
 * Tests that verifies the behaviors of embedding activities in different trusted modes.
 *
 * Build/Install/Run:
 *     atest CtsWindowManagerDeviceTaskFragment:TaskFragmentTrustedModeTest
 */
@Presubmit
@android.server.wm.annotation.Group2
public class TaskFragmentTrustedModeTest extends TaskFragmentOrganizerTestBase {

    private final ComponentName mTranslucentActivity = new ComponentName(mContext,
            TranslucentActivity.class);

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
        assumeActivityEmbeddingSupportedDevice();
    }

    /**
     * Verifies the visibility of a task fragment that has overlays on top of activities embedded
     * in untrusted mode when there is an overlay over the task fragment.
     */
    @Test
    public void testUntrustedModeTaskFragmentVisibility_overlayTaskFragment() {
        // Create a task fragment with activity in untrusted mode.
        final Rect baseActivityBounds =
                mOwnerActivity.getResources().getConfiguration().windowConfiguration.getBounds();
        final TaskFragmentInfo tf = createTaskFragment(SECOND_UNTRUSTED_EMBEDDING_ACTIVITY,
                partialOverlayRelativeBounds(baseActivityBounds));

        // Start a translucent activity over the TaskFragment.
        createTaskFragment(mTranslucentActivity, partialOverlayRelativeBounds(
                tf.getConfiguration().windowConfiguration.getBounds()));
        waitAndAssertResumedActivity(mTranslucentActivity, "Translucent activity must be resumed.");

        // The task fragment must be made invisible when there is an overlay activity in it.
        final String overlayMessage = "Activities embedded in untrusted mode should be made "
                + "invisible in a task fragment with overlay";
        waitAndAssertStoppedActivity(SECOND_UNTRUSTED_EMBEDDING_ACTIVITY, overlayMessage);
        assertFalse(overlayMessage, mWmState.getTaskFragmentByActivity(
                SECOND_UNTRUSTED_EMBEDDING_ACTIVITY).isVisible());

        // The activity that appeared on top would stay resumed
        assertTrue(overlayMessage, mWmState.hasActivityState(mTranslucentActivity, STATE_RESUMED));
        assertTrue(overlayMessage, mWmState.isActivityVisible(mTranslucentActivity));
        assertTrue(overlayMessage, mWmState.getTaskFragmentByActivity(
                mTranslucentActivity).isVisible());
    }

    /**
     * Verifies the visibility of a task fragment that has overlays on top of activities embedded
     * in untrusted mode when an activity from another process is started on top.
     */
    @Test
    public void testUntrustedModeTaskFragmentVisibility_startActivityInTaskFragment() {
        // Create a task fragment with activity in untrusted mode.
        final Rect baseActivityBounds =
                mOwnerActivity.getResources().getConfiguration().windowConfiguration.getBounds();
        final TaskFragmentInfo taskFragmentInfo = createTaskFragment(
                SECOND_UNTRUSTED_EMBEDDING_ACTIVITY,
                partialOverlayRelativeBounds(baseActivityBounds));

        // Start an activity with a different UID in the TaskFragment.
        final WindowContainerTransaction wct = new WindowContainerTransaction()
                .startActivityInTaskFragment(taskFragmentInfo.getFragmentToken(), mOwnerToken,
                        new Intent().setComponent(mTranslucentActivity),
                        null /* activityOptions */);
        mTaskFragmentOrganizer.applyTransaction(wct, TASK_FRAGMENT_TRANSIT_OPEN,
                false /* shouldApplyIndependently */);
        waitAndAssertResumedActivity(mTranslucentActivity, "Translucent activity must be resumed.");

        // Some activities in the task fragment must be made invisible when there is an overlay.
        final String overlayMessage = "Activities embedded in untrusted mode should be made "
                + "invisible in a task fragment with overlay";
        waitAndAssertStoppedActivity(SECOND_UNTRUSTED_EMBEDDING_ACTIVITY, overlayMessage);

        // The activity that appeared on top would stay resumed, and the task fragment is still
        // visible.
        assertTrue(overlayMessage, mWmState.hasActivityState(mTranslucentActivity, STATE_RESUMED));
        assertTrue(overlayMessage, mWmState.isActivityVisible(mTranslucentActivity));
        assertTrue(overlayMessage, mWmState.getTaskFragmentByActivity(
                SECOND_UNTRUSTED_EMBEDDING_ACTIVITY).isVisible());
    }

    /**
     * Verifies the visibility of a task fragment that has overlays on top of activities embedded
     * in untrusted mode when an activity from another process is reparented on top.
     */
    @Test
    public void testUntrustedModeTaskFragmentVisibility_reparentActivityInTaskFragment() {
        final Activity translucentActivity = startActivity(TranslucentActivity.class);

        // Create a task fragment with activity in untrusted mode.
        final TaskFragmentInfo taskFragmentInfo = createTaskFragment(
                SECOND_UNTRUSTED_EMBEDDING_ACTIVITY);

        // Reparent a translucent activity with a different UID to the TaskFragment.
        final IBinder embeddedActivityToken = getActivityToken(translucentActivity);
        final WindowContainerTransaction wct = new WindowContainerTransaction()
                .reparentActivityToTaskFragment(taskFragmentInfo.getFragmentToken(),
                        embeddedActivityToken);
        mTaskFragmentOrganizer.applyTransaction(wct, TASK_FRAGMENT_TRANSIT_CHANGE,
                false /* shouldApplyIndependently */);
        waitAndAssertResumedActivity(mTranslucentActivity, "Translucent activity must be resumed.");

        // Some activities in the task fragment must be made invisible when there is an overlay.
        final String overlayMessage = "Activities embedded in untrusted mode should be made "
                + "invisible in a task fragment with overlay";
        waitAndAssertStoppedActivity(SECOND_UNTRUSTED_EMBEDDING_ACTIVITY, overlayMessage);

        // The activity that appeared on top would stay resumed, and the task fragment is still
        // visible
        assertTrue(overlayMessage, mWmState.hasActivityState(mTranslucentActivity, STATE_RESUMED));
        assertTrue(overlayMessage, mWmState.isActivityVisible(mTranslucentActivity));
        assertTrue(overlayMessage, mWmState.getTaskFragmentByActivity(
                SECOND_UNTRUSTED_EMBEDDING_ACTIVITY).isVisible());

        // Finishing the overlay activity must make TaskFragment visible again.
        translucentActivity.finish();
        waitAndAssertResumedActivity(SECOND_UNTRUSTED_EMBEDDING_ACTIVITY,
                "Activity must be resumed without overlays");
        assertTrue("Activity must be visible without overlays",
                mWmState.isActivityVisible(SECOND_UNTRUSTED_EMBEDDING_ACTIVITY));
    }

    /**
     * Verifies that when the TaskFragment has embedded activities in untrusted mode, set relative
     * bounds outside of its parent bounds will still set the TaskFragment bounds within its parent.
     */
    @Test
    public void testUntrustedModeTaskFragment_setRelativeBoundsOutsideOfParentBounds() {
        final Task parentTask = mWmState.getRootTask(mOwnerTaskId);
        final Rect parentBounds = new Rect(parentTask.getBounds());
        // Create a TaskFragment with activity embedded in untrusted mode.
        final TaskFragmentInfo info = createTaskFragment(SECOND_UNTRUSTED_EMBEDDING_ACTIVITY);

        // Try to set relative bounds that is larger than its parent bounds.
        mTaskFragmentOrganizer.resetLatch();
        final Rect taskFragBounds = new Rect(parentBounds);
        taskFragBounds.right++;
        final WindowContainerTransaction wct = new WindowContainerTransaction()
                .setRelativeBounds(info.getToken(), taskFragBounds)
                .setWindowingMode(info.getToken(), WINDOWING_MODE_MULTI_WINDOW);

        // It is allowed to set TaskFragment bounds to outside of its parent bounds.
        mTaskFragmentOrganizer.applyTransaction(wct, TASK_FRAGMENT_TRANSIT_CHANGE,
                false /* shouldApplyIndependently */);

        // Update the windowing mode to make sure the WindowContainerTransaction has been applied.
        mWmState.waitForWithAmState(amState -> {
            final WindowManagerState.TaskFragment tf = amState.getTaskFragmentByActivity(
                    SECOND_UNTRUSTED_EMBEDDING_ACTIVITY);
            return tf != null && tf.getWindowingMode() == WINDOWING_MODE_MULTI_WINDOW;
        }, "TaskFragment should have WINDOWING_MODE_MULTI_WINDOW");

        // The TaskFragment bounds should remain in its parent bounds.
        final WindowManagerState.TaskFragment tf = mWmState.getTaskFragmentByActivity(
                SECOND_UNTRUSTED_EMBEDDING_ACTIVITY);
        assertNotNull(tf);
        assertEquals(WINDOWING_MODE_MULTI_WINDOW, tf.getWindowingMode());
        assertEquals(parentBounds, tf.getFullConfiguration().windowConfiguration.getBounds());
    }

    /**
     * Verifies that when the TaskFragment bounds is outside of its parent bounds, it is disallowed
     * to start activity in untrusted mode.
     */
    @Test
    public void testUntrustedModeTaskFragment_startActivityInTaskFragmentOutsideOfParentBounds() {
        final Task parentTask = mWmState.getRootTask(mOwnerTaskId);
        final Rect parentBounds = new Rect(parentTask.getBounds());
        final IBinder errorCallbackToken = new Binder();
        final WindowContainerTransaction wct = new WindowContainerTransaction()
                .setErrorCallbackToken(errorCallbackToken);

        // We check if the TaskFragment bounds is in its parent bounds before launching activity in
        // untrusted mode.
        final Rect taskFragRelativeBounds = new Rect(parentBounds);
        taskFragRelativeBounds.offsetTo(0, 0);
        taskFragRelativeBounds.right++;
        createTaskFragment(SECOND_UNTRUSTED_EMBEDDING_ACTIVITY, taskFragRelativeBounds, wct);

        // It is disallowed to start activity to TaskFragment with bounds outside of its parent
        // in untrusted mode.
        assertTaskFragmentError(errorCallbackToken, SecurityException.class);
    }

    /**
     * Creates relative bounds for a container that would appear on top and partially occlude the
     * provided one.
     */
    @NonNull
    private Rect partialOverlayRelativeBounds(@NonNull Rect baseBounds) {
        final Rect result = new Rect(baseBounds);
        result.offsetTo(0, 0);
        result.inset(50 /* left */, 50 /* top */, 50 /* right */, 50 /* bottom */);
        return result;
    }

    /** Asserts that the organizer received an error callback. */
    private void assertTaskFragmentError(@NonNull IBinder errorCallbackToken,
            @NonNull Class<? extends Throwable> exceptionClass) {
        mTaskFragmentOrganizer.waitForTaskFragmentError();
        assertThat(mTaskFragmentOrganizer.getThrowable()).isInstanceOf(exceptionClass);
        assertThat(mTaskFragmentOrganizer.getErrorCallbackToken()).isEqualTo(errorCallbackToken);
    }

    public static class TranslucentActivity extends FocusableActivity {}
}
