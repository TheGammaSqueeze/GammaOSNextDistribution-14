/*
 * Copyright (C) 2021 The Android Open Source Project
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

import static android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN;
import static android.app.WindowConfiguration.WINDOWING_MODE_MULTI_WINDOW;
import static android.app.WindowConfiguration.WINDOWING_MODE_UNDEFINED;
import static android.server.wm.WindowManagerState.STATE_STARTED;
import static android.server.wm.WindowManagerState.STATE_STOPPED;
import static android.server.wm.taskfragment.SplitActivityLifecycleTest.SplitTestActivity.EXTRA_SET_RESULT_AND_FINISH;
import static android.server.wm.taskfragment.SplitActivityLifecycleTest.SplitTestActivity.EXTRA_SHOW_WHEN_LOCKED;
import static android.view.Display.DEFAULT_DISPLAY;
import static android.window.TaskFragmentOrganizer.TASK_FRAGMENT_TRANSIT_CHANGE;
import static android.window.TaskFragmentOrganizer.TASK_FRAGMENT_TRANSIT_OPEN;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assume.assumeTrue;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.IBinder;
import android.platform.test.annotations.Presubmit;
import android.server.wm.LockScreenSession;
import android.server.wm.WindowManagerState.TaskFragment;
import android.window.TaskFragmentCreationParams;
import android.window.TaskFragmentInfo;
import android.window.WindowContainerToken;
import android.window.WindowContainerTransaction;

import androidx.annotation.NonNull;

import org.junit.Test;

/**
 * Tests that verify the behavior of split Activity.
 * <p>
 * At the beginning of test, two Activities are launched side-by-side in two adjacent TaskFragments.
 * Then another Activity will be launched with different scenarios. The purpose of this test is to
 * verify the CUJ of split Activity.
 * </p>
 *
 * Build/Install/Run:
 *     atest CtsWindowManagerDeviceTaskFragment:SplitActivityLifecycleTest
 */
@Presubmit
@android.server.wm.annotation.Group2
public class SplitActivityLifecycleTest extends TaskFragmentOrganizerTestBase {
    /** The bounds should only be updated through {@link #updateSplitBounds(Rect)}. */
    private final Rect mPrimaryBounds = new Rect();
    private final Rect mPrimaryRelativeBounds = new Rect();
    private final Rect mSideBounds = new Rect();
    private final Rect mSideRelativeBounds = new Rect();

    private TaskFragmentRecord mTaskFragA;
    private TaskFragmentRecord mTaskFragB;
    private final ComponentName mActivityA = new ComponentName(mContext, ActivityA.class);
    private final ComponentName mActivityB = new ComponentName(mContext, ActivityB.class);
    private final ComponentName mActivityC = new ComponentName(mContext, ActivityC.class);
    private final Intent mIntent = new Intent().setComponent(mActivityC);

    @Override
    public void setUp() throws Exception {
        super.setUp();
    }

    @Override
    Activity setUpOwnerActivity() {
        // Launch activities in fullscreen, otherwise, some tests fail on devices which use freeform
        // as the default windowing mode, because tests' prerequisite are that activity A, B, and C
        // need to overlay completely, but they can be partially overlay as freeform windows.
        return startActivityInWindowingModeFullScreen(ActivityA.class);
    }

    /** Launch two Activities in two adjacent TaskFragments side-by-side. */
    private void initializeSplitActivities() {
        initializeSplitActivities(false /* showWhenLocked */);
    }

    /**
     * Launch two Activities in two adjacent TaskFragments side-by-side and support to set the
     * showWhenLocked attribute to Activity B.
     */
    private void initializeSplitActivities(boolean showWhenLocked) {
        final Rect activityBounds = mOwnerActivity.getWindowManager().getCurrentWindowMetrics()
                .getBounds();
        updateSplitBounds(activityBounds);

        final TaskFragmentCreationParams paramsA = generatePrimaryTaskFragParams();
        final TaskFragmentCreationParams paramsB = generateSideTaskFragParams();
        IBinder taskFragTokenA = paramsA.getFragmentToken();
        IBinder taskFragTokenB = paramsB.getFragmentToken();

        final WindowContainerTransaction wct = new WindowContainerTransaction()
                .createTaskFragment(paramsA)
                .reparentActivityToTaskFragment(taskFragTokenA, mOwnerToken)
                .createTaskFragment(paramsB)
                .setAdjacentTaskFragments(taskFragTokenA, taskFragTokenB, null /* params */);

        final Intent intent = new Intent().setComponent(mActivityB);
        if (showWhenLocked) {
            intent.putExtra(EXTRA_SHOW_WHEN_LOCKED, true);
        }
        wct.startActivityInTaskFragment(taskFragTokenB, mOwnerToken, intent,
                null /* activityOptions */);

        mTaskFragmentOrganizer.setAppearedCount(2);
        mTaskFragmentOrganizer.applyTransaction(wct, TASK_FRAGMENT_TRANSIT_CHANGE,
                false /* shouldApplyIndependently */);
        mTaskFragmentOrganizer.waitForTaskFragmentCreated();

        final TaskFragmentInfo infoA = mTaskFragmentOrganizer.getTaskFragmentInfo(
                taskFragTokenA);
        final TaskFragmentInfo infoB = mTaskFragmentOrganizer.getTaskFragmentInfo(
                taskFragTokenB);

        assertNotEmptyTaskFragment(infoA, taskFragTokenA, mOwnerToken);
        assertNotEmptyTaskFragment(infoB, taskFragTokenB);

        mTaskFragA = new TaskFragmentRecord(infoA);
        mTaskFragB = new TaskFragmentRecord(infoB);

        waitAndAssertResumedActivity(mActivityA, "Activity A must still be resumed.");
        waitAndAssertResumedActivity(mActivityB, "Activity B must still be resumed.");

        mTaskFragmentOrganizer.resetLatch();
    }

    /**
     * Splits the {@code parentBounds} vertically to {@link #mPrimaryBounds} and
     * {@link #mSideBounds}. {@link #mPrimaryRelativeBounds} and {@link #mSideRelativeBounds} will
     * also be updated to the corresponding relative bounds in parent coordinate.
     */
    private void updateSplitBounds(@NonNull Rect parentBounds) {
        parentBounds.splitVertically(mPrimaryBounds, mSideBounds);
        mPrimaryRelativeBounds.set(mPrimaryBounds);
        mPrimaryRelativeBounds.offsetTo(0, 0);
        mSideRelativeBounds.set(mSideBounds);
        mSideRelativeBounds.offsetTo(mSideBounds.left - mPrimaryBounds.left,
                mSideBounds.top - mPrimaryBounds.top);
    }

    /**
     * Verifies the behavior to launch Activity in the same TaskFragment as the owner Activity.
     * <p>
     * For example, given that Activity A and B are showed side-by-side, this test verifies
     * the behavior to launch Activity C in the same TaskFragment as Activity A:
     * <pre class="prettyprint">
     * |A|B| -> |C|B|
     * </pre></p>
     */
    @Test
    public void testActivityLaunchInSameSplitTaskFragment() {
        // Initialize test environment by launching Activity A and B side-by-side.
        initializeSplitActivities();

        final IBinder taskFragTokenA = mTaskFragA.getTaskFragToken();
        final WindowContainerTransaction wct = new WindowContainerTransaction()
                .startActivityInTaskFragment(taskFragTokenA, mOwnerToken, mIntent,
                        null /* activityOptions */);

        mTaskFragmentOrganizer.applyTransaction(wct, TASK_FRAGMENT_TRANSIT_OPEN,
                false /* shouldApplyIndependently */);

        final TaskFragmentInfo infoA = mTaskFragmentOrganizer.waitForAndGetTaskFragmentInfo(
                taskFragTokenA, info -> info.getActivities().size() == 2,
                "getActivities from TaskFragment A must contain 2 activities");

        assertNotEmptyTaskFragment(infoA, taskFragTokenA, mOwnerToken);

        waitAndAssertResumedActivity(mActivityC, "Activity C must be resumed.");
        waitAndAssertActivityState(mActivityA, STATE_STOPPED,
                "Activity A is occluded by Activity C, so it must be stopped.");
        waitAndAssertResumedActivity(mActivityB, "Activity B must be resumed.");

        final TaskFragment taskFragmentA = mWmState.getTaskFragmentByActivity(mActivityA);
        assertWithMessage("TaskFragmentA must contain Activity A and C")
                .that(taskFragmentA.getActivities())
                .containsExactly(
                        mWmState.getActivity(mActivityA), mWmState.getActivity(mActivityC));
    }

    /**
     * Verifies the behavior to launch Activity in the adjacent TaskFragment.
     * <p>
     * For example, given that Activity A and B are showed side-by-side, this test verifies
     * the behavior to launch Activity C in the same TaskFragment as Activity B:
     * <pre class="prettyprint">
     * |A|B| -> |A|C|
     * </pre></p>
     */
    @Test
    public void testActivityLaunchInAdjacentSplitTaskFragment() {
        // Initialize test environment by launching Activity A and B side-by-side.
        initializeSplitActivities();

        final IBinder taskFragTokenB = mTaskFragB.getTaskFragToken();
        final WindowContainerTransaction wct = new WindowContainerTransaction()
                .startActivityInTaskFragment(taskFragTokenB, mOwnerToken, mIntent,
                        null /* activityOptions */);

        mTaskFragmentOrganizer.applyTransaction(wct, TASK_FRAGMENT_TRANSIT_OPEN,
                false /* shouldApplyIndependently */);

        final TaskFragmentInfo infoB = mTaskFragmentOrganizer.waitForAndGetTaskFragmentInfo(
                taskFragTokenB, info -> info.getActivities().size() == 2,
                "getActivities from TaskFragment A must contain 2 activities");

        assertNotEmptyTaskFragment(infoB, taskFragTokenB);

        waitAndAssertResumedActivity(mActivityC, "Activity C must be resumed.");
        waitAndAssertResumedActivity(mActivityA, "Activity A must be resumed.");
        waitAndAssertActivityState(mActivityB, STATE_STOPPED,
                "Activity B is occluded by Activity C, so it must be stopped.");

        final TaskFragment taskFragmentB = mWmState.getTaskFragmentByActivity(mActivityB);
        assertWithMessage("TaskFragmentB must contain Activity B and C")
                .that(taskFragmentB.getActivities())
                .containsExactly(
                        mWmState.getActivity(mActivityB), mWmState.getActivity(mActivityC));
    }

    /**
     * Verifies the behavior that the Activity instance in bottom TaskFragment calls
     * {@link Context#startActivity(Intent)} to launch another Activity.
     * <p>
     * For example, given that Activity A and B are showed side-by-side, Activity A calls
     * {@link Context#startActivity(Intent)} to launch Activity C. The expected behavior is that
     * Activity C will be launch on top of Activity B as below:
     * <pre class="prettyprint">
     * |A|B| -> |A|C|
     * </pre>
     * The reason is that TaskFragment B has higher z-order than TaskFragment A because we create
     * TaskFragment B later than TaskFragment A.
     * </p>
     */
    @Test
    public void testActivityLaunchFromBottomTaskFragment() {
        // Initialize test environment by launching Activity A and B side-by-side.
        initializeSplitActivities();

        mOwnerActivity.startActivity(mIntent);

        final IBinder taskFragTokenB = mTaskFragB.getTaskFragToken();
        final TaskFragmentInfo infoB = mTaskFragmentOrganizer.waitForAndGetTaskFragmentInfo(
                taskFragTokenB, info -> info.getActivities().size() == 2,
                "getActivities from TaskFragment A must contain 2 activities");

        assertNotEmptyTaskFragment(infoB, taskFragTokenB);

        waitAndAssertResumedActivity(mActivityC, "Activity C must be resumed.");
        waitAndAssertResumedActivity(mActivityA, "Activity A must be resumed.");
        waitAndAssertActivityState(mActivityB, STATE_STOPPED,
                "Activity B is occluded by Activity C, so it must be stopped.");

        final TaskFragment taskFragmentB = mWmState.getTaskFragmentByActivity(mActivityB);
        assertWithMessage("TaskFragmentB must contain Activity B and C")
                .that(taskFragmentB.getActivities())
                .containsExactly(
                        mWmState.getActivity(mActivityB), mWmState.getActivity(mActivityC));
    }

    /**
     * Verifies the behavior of the activities in a TaskFragment that is sandwiched in adjacent
     * TaskFragments.
     */
    @Test
    public void testSandwichTaskFragmentInAdjacent() {
        // Initialize test environment by launching Activity A and B side-by-side.
        initializeSplitActivities();

        final IBinder taskFragTokenA = mTaskFragA.getTaskFragToken();
        final TaskFragmentCreationParams paramsC = generateSideTaskFragParams();
        final IBinder taskFragTokenC = paramsC.getFragmentToken();
        final WindowContainerTransaction wct = new WindowContainerTransaction()
                // Create the side TaskFragment for C and launch
                .createTaskFragment(paramsC)
                .startActivityInTaskFragment(taskFragTokenC, mOwnerToken, mIntent,
                        null /* activityOptions */)
                .setAdjacentTaskFragments(taskFragTokenA, taskFragTokenC, null /* options */);

        mTaskFragmentOrganizer.applyTransaction(wct, TASK_FRAGMENT_TRANSIT_OPEN,
                false /* shouldApplyIndependently */);
        // Wait for the TaskFragment of Activity C to be created.
        mTaskFragmentOrganizer.waitForTaskFragmentCreated();

        waitAndAssertResumedActivity(mActivityC, "Activity C must be resumed.");
        waitAndAssertActivityState(mActivityB, STATE_STOPPED,
                "Activity B is occluded by Activity C, so it must be stopped.");
        waitAndAssertResumedActivity(mActivityA, "Activity A must be resumed.");
    }

    /**
     * Verifies the behavior of the activities in a TaskFragment that is sandwiched in adjacent
     * TaskFragments. It should be hidden even if part of it is not cover by the adjacent
     * TaskFragment above.
     */
    @Test
    public void testSandwichTaskFragmentInAdjacent_partialOccluding() {
        // Initialize test environment by launching Activity A and B side-by-side.
        initializeSplitActivities();

        final IBinder taskFragTokenA = mTaskFragA.getTaskFragToken();
        // TaskFragment C is not fully occluding TaskFragment B.
        final Rect partialOccludingRelativeSideBounds = new Rect(mSideRelativeBounds);
        partialOccludingRelativeSideBounds.left += 50;
        final TaskFragmentCreationParams paramsC = mTaskFragmentOrganizer.generateTaskFragParams(
                mOwnerToken, partialOccludingRelativeSideBounds, WINDOWING_MODE_MULTI_WINDOW);
        final IBinder taskFragTokenC = paramsC.getFragmentToken();
        final WindowContainerTransaction wct = new WindowContainerTransaction()
                // Create the side TaskFragment for C and launch
                .createTaskFragment(paramsC)
                .startActivityInTaskFragment(taskFragTokenC, mOwnerToken, mIntent,
                        null /* activityOptions */)
                .setAdjacentTaskFragments(taskFragTokenA, taskFragTokenC, null /* options */);

        mTaskFragmentOrganizer.applyTransaction(wct, TASK_FRAGMENT_TRANSIT_OPEN,
                false /* shouldApplyIndependently */);
        // Wait for the TaskFragment of Activity C to be created.
        mTaskFragmentOrganizer.waitForTaskFragmentCreated();

        waitAndAssertResumedActivity(mActivityC, "Activity C must be resumed.");
        waitAndAssertActivityState(mActivityB, STATE_STOPPED,
                "Activity B is occluded by Activity C, so it must be stopped.");
        waitAndAssertResumedActivity(mActivityA, "Activity A must be resumed.");
    }

    /**
     * Verifies the behavior to launch adjacent Activity to the adjacent TaskFragment.
     * <p>
     * For example, given that Activity A and B are showed side-by-side, this test verifies
     * the behavior to launch the Activity C to the adjacent TaskFragment of the secondary
     * TaskFragment, which Activity B is attached to. Then the secondary TaskFragment is shifted to
     * occlude the primary TaskFragment, which Activity A is attached to, and the adjacent
     * TaskFragment, which Activity C is attached to, is occupied the region where the secondary
     * TaskFragment is located. This test is to verify the "shopping mode" scenario.
     * <pre class="prettyprint">
     * |A|B| -> |B|C|
     * </pre></p>
     */
    @Test
    public void testAdjacentActivityLaunchFromSecondarySplitTaskFragment() {
        // Initialize test environment by launching Activity A and B side-by-side.
        initializeSplitActivities();

        final IBinder taskFragTokenB = mTaskFragB.getTaskFragToken();
        final TaskFragmentCreationParams paramsC = generateSideTaskFragParams();
        final IBinder taskFragTokenC = paramsC.getFragmentToken();
        final WindowContainerTransaction wct = new WindowContainerTransaction()
                // Move TaskFragment B to the primaryBounds
                .setRelativeBounds(mTaskFragB.getToken(), mPrimaryRelativeBounds)
                // Create the side TaskFragment for C and launch
                .createTaskFragment(paramsC)
                .startActivityInTaskFragment(taskFragTokenC, mOwnerToken, mIntent,
                        null /* activityOptions */)
                .setAdjacentTaskFragments(taskFragTokenB, taskFragTokenC, null /* options */);

        mTaskFragmentOrganizer.applyTransaction(wct, TASK_FRAGMENT_TRANSIT_CHANGE,
                false /* shouldApplyIndependently */);
        // Wait for the TaskFragment of Activity C to be created.
        mTaskFragmentOrganizer.waitForTaskFragmentCreated();
        // Wait for the TaskFragment of Activity B to be changed.
        mTaskFragmentOrganizer.waitForTaskFragmentInfoChanged();

        final TaskFragmentInfo infoB = mTaskFragmentOrganizer.getTaskFragmentInfo(taskFragTokenB);
        final TaskFragmentInfo infoC = mTaskFragmentOrganizer.getTaskFragmentInfo(taskFragTokenC);

        assertNotEmptyTaskFragment(infoB, taskFragTokenB);
        assertNotEmptyTaskFragment(infoC, taskFragTokenC);

        mTaskFragB = new TaskFragmentRecord(infoB);
        final TaskFragmentRecord taskFragC = new TaskFragmentRecord(infoC);

        assertThat(mTaskFragB.getBounds()).isEqualTo(mPrimaryBounds);
        assertThat(taskFragC.getBounds()).isEqualTo(mSideBounds);

        waitAndAssertResumedActivity(mActivityC, "Activity C must be resumed.");
        waitAndAssertActivityState(mActivityA, STATE_STOPPED,
                "Activity A is occluded by Activity C, so it must be stopped.");
        waitAndAssertResumedActivity(mActivityB, "Activity B must be resumed.");
    }

    /**
     * Verifies the behavior to launch Activity in expanded TaskFragment.
     * <p>
     * For example, given that Activity A and B are showed side-by-side, this test verifies
     * the behavior to launch Activity C in the TaskFragment which fills the Task bounds of owner
     * Activity:
     * <pre class="prettyprint">
     * |A|B| -> |C|
     * </pre></p>
     */
    @Test
    public void testActivityLaunchInExpandedTaskFragment() {
        // Initialize test environment by launching Activity A and B side-by-side.
        initializeSplitActivities();

        testActivityLaunchInExpandedTaskFragmentInternal();
    }

    private void testActivityLaunchInExpandedTaskFragmentInternal() {

        final TaskFragmentCreationParams fullScreenParamsC = mTaskFragmentOrganizer
                .generateTaskFragParams(mOwnerToken, new Rect(), WINDOWING_MODE_FULLSCREEN);
        final IBinder taskFragTokenC = fullScreenParamsC.getFragmentToken();
        final WindowContainerTransaction wct = new WindowContainerTransaction()
                .createTaskFragment(fullScreenParamsC)
                .startActivityInTaskFragment(taskFragTokenC, mOwnerToken, mIntent,
                        null /* activityOptions */);

        mTaskFragmentOrganizer.applyTransaction(wct, TASK_FRAGMENT_TRANSIT_OPEN,
                false /* shouldApplyIndependently */);

        mTaskFragmentOrganizer.waitForTaskFragmentCreated();

        assertNotEmptyTaskFragment(mTaskFragmentOrganizer.getTaskFragmentInfo(taskFragTokenC),
                taskFragTokenC);

        waitAndAssertResumedActivity(mActivityC, "Activity C must be resumed.");
        waitAndAssertActivityState(mActivityA, STATE_STOPPED,
                "Activity A is occluded by Activity C, so it must be stopped.");
        waitAndAssertActivityState(mActivityB, STATE_STOPPED,
                "Activity B is occluded by Activity C, so it must be stopped.");
    }

    /**
     * Verifies the show-when-locked behavior while launch embedded activities. Don't show the
     * embedded activities even if one of Activity has showWhenLocked flag.
     */
    @Test
    public void testLaunchEmbeddedActivityWithShowWhenLocked() {
        assumeTrue(supportsLockScreen());

        final LockScreenSession lockScreenSession = createManagedLockScreenSession();
        // Initialize test environment by launching Activity A and B (with showWhenLocked)
        // side-by-side.
        initializeSplitActivities(true /* showWhenLocked */);

        lockScreenSession.sleepDevice();
        lockScreenSession.wakeUpDevice();

        waitAndAssertActivityState(mActivityA, STATE_STOPPED,"Activity A must be stopped");
        waitAndAssertActivityState(mActivityB, STATE_STOPPED,"Activity B must be stopped");
    }

    /**
     * Verifies the show-when-locked behavior while launch embedded activities. Don't show the
     * embedded activities if the activities don't have showWhenLocked flag.
     */
    @Test
    public void testLaunchEmbeddedActivitiesWithoutShowWhenLocked() {
        assumeTrue(supportsLockScreen());

        final LockScreenSession lockScreenSession = createManagedLockScreenSession();
        // Initialize test environment by launching Activity A and B side-by-side.
        initializeSplitActivities();

        lockScreenSession.sleepDevice();
        lockScreenSession.wakeUpDevice();

        waitAndAssertActivityState(mActivityA, STATE_STOPPED,"Activity A must be stopped");
        waitAndAssertActivityState(mActivityB, STATE_STOPPED,"Activity B must be stopped");
    }

    /**
     * Verifies the show-when-locked behavior while launch embedded activities. The embedded
     * activities should be shown on top of the lock screen since they have the showWhenLocked flag.
     * Don't show the embedded activities even if one of Activity has showWhenLocked flag.
     */
    @Test
    public void testLaunchEmbeddedActivitiesWithShowWhenLocked() {
        assumeTrue(supportsLockScreen());

        final LockScreenSession lockScreenSession = createManagedLockScreenSession();
        // Initialize test environment by launching Activity A and B side-by-side.
        mOwnerActivity.setShowWhenLocked(true);
        initializeSplitActivities(true /* showWhenLocked */);

        lockScreenSession.sleepDevice();
        lockScreenSession.wakeUpDevice();

        waitAndAssertResumedActivity(mActivityA, "Activity A must be resumed.");
        waitAndAssertResumedActivity(mActivityB, "Activity B must be resumed.");

        // Launch Activity C without show-when-lock and verifies that both activities are stopped.
        mOwnerActivity.startActivity(mIntent);
        waitAndAssertActivityState(mActivityA, STATE_STOPPED, "Activity A must be stopped");
        waitAndAssertActivityState(mActivityC, STATE_STOPPED, "Activity C must be stopped");
    }

    /**
     * Verifies the Activity in primary TaskFragment is no longer focused after clear adjacent
     * TaskFragments.
     */
    @Test
    public void testResetFocusedAppAfterClearAdjacentTaskFragment() {
        // Initialize test environment by launching Activity A and B side-by-side.
        initializeSplitActivities();

        // Request the focus on the primary TaskFragment
        WindowContainerTransaction wct = new WindowContainerTransaction()
                .requestFocusOnTaskFragment(mTaskFragA.getTaskFragToken());
        mTaskFragmentOrganizer.applyTransaction(wct, TASK_FRAGMENT_TRANSIT_CHANGE,
                false /* shouldApplyIndependently */);
        waitForActivityFocused(5000, mActivityA);
        assertThat(mWmState.getFocusedApp()).isEqualTo(mActivityA.flattenToShortString());

        // Expand top TaskFragment and clear the adjacent TaskFragments to have the two
        // TaskFragment stacked.
        wct = new WindowContainerTransaction()
                .setRelativeBounds(mTaskFragB.getToken(), new Rect())
                .setWindowingMode(mTaskFragB.getToken(), WINDOWING_MODE_UNDEFINED)
                .clearAdjacentTaskFragments(mTaskFragA.getTaskFragToken());
        mTaskFragmentOrganizer.applyTransaction(wct, TASK_FRAGMENT_TRANSIT_CHANGE,
                false /* shouldApplyIndependently */);

        // Ensure the Activity on primary TaskFragment is stopped and no longer focused.
        waitAndAssertActivityState(mActivityA, STATE_STOPPED, "Activity A must be stopped");
        assertThat(mWmState.getFocusedApp()).isNotEqualTo(mActivityA.flattenToShortString());
        assertThat(mWmState.getFocusedWindow()).isEqualTo(mActivityB.flattenToShortString());
    }

    /**
     * Verifies an Activity below adjacent translucent TaskFragments is visible.
     */
    @Test
    public void testTranslucentAdjacentTaskFragment() {
        // Create ActivityB on top of ActivityA.
        // Make sure ActivityB is launched into the same task as ActivityA so that we can reparent
        // it to TaskFragment in the same task later.
        Activity activityB = startActivity(ActivityB.class, DEFAULT_DISPLAY, true /* hasFocus */,
                WINDOWING_MODE_FULLSCREEN, mOwnerActivity.getTaskId());
        waitAndAssertResumedActivity(mActivityB, "Activity B must be resumed.");
        waitAndAssertActivityState(mActivityA, STATE_STOPPED,
                "Activity A is occluded by Activity B, so it must be stopped.");

        // Create two adjacent TaskFragments, making ActivityB and TranslucentActivity
        // displayed side-by-side (ActivityB|TranslucentActivity).
        updateSplitBounds(mOwnerActivity.getWindowManager().getCurrentWindowMetrics().getBounds());
        final TaskFragmentCreationParams primaryParams = generatePrimaryTaskFragParams();
        final TaskFragmentCreationParams secondaryParams = generateSideTaskFragParams();
        IBinder primaryToken = primaryParams.getFragmentToken();
        IBinder secondaryToken = secondaryParams.getFragmentToken();

        final ComponentName translucentActivity = new ComponentName(mContext,
                TranslucentActivity.class);
        final Intent intent = new Intent().setComponent(translucentActivity);
        WindowContainerTransaction wct = new WindowContainerTransaction()
                .createTaskFragment(primaryParams)
                .reparentActivityToTaskFragment(primaryToken, getActivityToken(activityB))
                .createTaskFragment(secondaryParams)
                .setAdjacentTaskFragments(primaryToken, secondaryToken, null /* params */)
                .startActivityInTaskFragment(secondaryToken, mOwnerToken, intent,
                        null /* activityOptions */);
        mTaskFragmentOrganizer.applyTransaction(wct, TASK_FRAGMENT_TRANSIT_CHANGE,
                false /* shouldApplyIndependently */);

        waitAndAssertResumedActivity(translucentActivity, "TranslucentActivity must be resumed.");
        waitAndAssertResumedActivity(mActivityB, "Activity B must be resumed.");
        waitAndAssertActivityState(mActivityA, STATE_STARTED,
                "Activity A is not fully occluded and must be visible and started");
    }

    /**
     * Verifies starting an Activity on the adjacent TaskFragment and able to get the result.
     */
    @Test
    public void testStartActivityForResultInAdjacentTaskFragment() {
        // Initialize test environment by launching Activity A and B side-by-side.
        initializeSplitActivities();

        // Start an Activity on the adjacent TaskFragment for result.
        final Intent intent = new Intent();
        intent.setComponent(mActivityC);
        intent.putExtra(EXTRA_SET_RESULT_AND_FINISH, true);
        mOwnerActivity.startActivityForResult(intent, 1 /* requestCode */);

        // Waits for the result
        waitForOrFail("Wait for the result",
                () -> ((SplitTestActivity) mOwnerActivity).getResultCode() == 100);
    }

    private TaskFragmentCreationParams generatePrimaryTaskFragParams() {
        return mTaskFragmentOrganizer.generateTaskFragParams(mOwnerToken, mPrimaryRelativeBounds,
                WINDOWING_MODE_MULTI_WINDOW);
    }

    private TaskFragmentCreationParams generateSideTaskFragParams() {
        return mTaskFragmentOrganizer.generateTaskFragParams(mOwnerToken, mSideRelativeBounds,
                WINDOWING_MODE_MULTI_WINDOW);
    }

    private static class TaskFragmentRecord {
        private final IBinder mTaskFragToken;
        private final Rect mBounds = new Rect();
        private final WindowContainerToken mContainerToken;

        private TaskFragmentRecord(TaskFragmentInfo info) {
            mTaskFragToken = info.getFragmentToken();
            mBounds.set(info.getConfiguration().windowConfiguration.getBounds());
            mContainerToken = info.getToken();
        }

        private IBinder getTaskFragToken() {
            return mTaskFragToken;
        }

        private Rect getBounds() {
            return mBounds;
        }

        private WindowContainerToken getToken() {
            return mContainerToken;
        }
    }

    public static class ActivityA extends SplitTestActivity {}
    public static class ActivityB extends SplitTestActivity {}
    public static class ActivityC extends SplitTestActivity {}
    public static class TranslucentActivity extends SplitTestActivity {}
    public static class SplitTestActivity extends FocusableActivity {
        public static final String EXTRA_SHOW_WHEN_LOCKED = "showWhenLocked";
        public static final String EXTRA_SET_RESULT_AND_FINISH = "setResultAndFinish";

        private int mResultCode = -1;
        @Override
        protected void onCreate(Bundle icicle) {
            super.onCreate(icicle);
            if (getIntent().getBooleanExtra(EXTRA_SHOW_WHEN_LOCKED, false)) {
                setShowWhenLocked(true);
            }
        }

        @Override
        protected void onResume() {
            super.onResume();
            if (getIntent().getBooleanExtra(EXTRA_SET_RESULT_AND_FINISH, false)) {
                setResult(100);
                finish();
            }
        }

        @Override
        protected void onActivityResult(int requestCode, int resultCode, Intent data) {
            super.onActivityResult(requestCode, resultCode, data);
            mResultCode = resultCode;
        }

        public int getResultCode() {
            return mResultCode;
        }
    }
}
