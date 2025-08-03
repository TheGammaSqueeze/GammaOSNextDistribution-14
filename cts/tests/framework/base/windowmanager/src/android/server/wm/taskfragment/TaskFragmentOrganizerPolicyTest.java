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

package android.server.wm.taskfragment;

import static android.app.ActivityManager.LOCK_TASK_MODE_PINNED;
import static android.app.WindowConfiguration.WINDOWING_MODE_MULTI_WINDOW;
import static android.app.WindowConfiguration.WINDOWING_MODE_UNDEFINED;
import static android.content.Intent.FLAG_ACTIVITY_MULTIPLE_TASK;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static android.server.wm.WindowManagerState.STATE_RESUMED;
import static android.server.wm.app.Components.LAUNCHING_ACTIVITY;
import static android.server.wm.app30.Components.SDK_30_TEST_ACTIVITY;
import static android.server.wm.taskfragment.TaskFragmentOrganizerTestBase.assertEmptyTaskFragment;
import static android.server.wm.taskfragment.TaskFragmentOrganizerTestBase.getActivityToken;
import static android.server.wm.taskfragment.TaskFragmentOrganizerTestBase.startNewActivity;
import static android.window.TaskFragmentOrganizer.TASK_FRAGMENT_TRANSIT_CHANGE;
import static android.window.TaskFragmentOrganizer.TASK_FRAGMENT_TRANSIT_CLOSE;
import static android.window.TaskFragmentOrganizer.TASK_FRAGMENT_TRANSIT_OPEN;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.fail;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Binder;
import android.os.IBinder;
import android.platform.test.annotations.Presubmit;
import android.server.wm.ActivityManagerTestBase;
import android.server.wm.HelperActivities;
import android.server.wm.MetricsActivity;
import android.server.wm.NestedShellPermission;
import android.server.wm.WindowContextTestActivity;
import android.server.wm.WindowManagerState.Task;
import android.server.wm.taskfragment.TaskFragmentOrganizerTestBase.BasicTaskFragmentOrganizer;
import android.view.SurfaceControl;
import android.window.TaskAppearedInfo;
import android.window.TaskFragmentCreationParams;
import android.window.TaskFragmentInfo;
import android.window.TaskFragmentOrganizer;
import android.window.TaskOrganizer;
import android.window.WindowContainerToken;
import android.window.WindowContainerTransaction;
import android.window.WindowContainerTransactionCallback;

import androidx.annotation.NonNull;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.ApiTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

/**
 * Tests that verify the behavior of {@link TaskFragmentOrganizer} policy.
 *
 * <p>Build/Install/Run: atest CtsWindowManagerDeviceTaskFragment:TaskFragmentOrganizerPolicyTest
 */
@RunWith(AndroidJUnit4.class)
@Presubmit
@android.server.wm.annotation.Group2
public class TaskFragmentOrganizerPolicyTest extends ActivityManagerTestBase {

    private TaskOrganizer mTaskOrganizer;
    private BasicTaskFragmentOrganizer mTaskFragmentOrganizer;
    private final ArrayList<BasicTaskFragmentOrganizer> mOrganizers = new ArrayList<>();

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
        mTaskFragmentOrganizer = new BasicTaskFragmentOrganizer();
        mTaskFragmentOrganizer.registerOrganizer();
        mOrganizers.add(mTaskFragmentOrganizer);
    }

    @After
    public void tearDown() {
        for (TaskFragmentOrganizer organizer : mOrganizers) {
            organizer.unregisterOrganizer();
        }
        mOrganizers.clear();
        if (mTaskOrganizer != null) {
            NestedShellPermission.run(() -> mTaskOrganizer.unregisterOrganizer());
        }
    }

    /**
     * Verifies that performing {@link WindowContainerTransaction#createTaskFragment} will fail if
     * the fragment token is not unique.
     */
    @Test
    @ApiTest(
            apis = {
                "android.window.TaskFragmentOrganizer#applyTransaction",
                "android.window.WindowContainerTransaction#createTaskFragment"
            })
    public void testCreateTaskFragment_duplicatedFragmentToken_reportError() {
        final Activity activity = startNewActivity();
        final TaskFragmentInfo taskFragmentInfo =
                createOrganizedTaskFragment(mTaskFragmentOrganizer, activity);
        final IBinder existingFragmentToken = taskFragmentInfo.getFragmentToken();
        final IBinder errorCallbackToken = new Binder();

        // Request to create another TaskFragment using the existing fragment token.
        final TaskFragmentCreationParams params =
                mTaskFragmentOrganizer.generateTaskFragParams(
                        existingFragmentToken,
                        getActivityToken(activity),
                        new Rect(),
                        WINDOWING_MODE_UNDEFINED);
        final WindowContainerTransaction wct =
                new WindowContainerTransaction()
                        .setErrorCallbackToken(errorCallbackToken)
                        .createTaskFragment(params);

        mTaskFragmentOrganizer.applyTransaction(
                wct, TASK_FRAGMENT_TRANSIT_OPEN, false /* shouldApplyIndependently */);
        mTaskFragmentOrganizer.waitForTaskFragmentError();

        assertThat(mTaskFragmentOrganizer.getThrowable())
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(mTaskFragmentOrganizer.getErrorCallbackToken()).isEqualTo(errorCallbackToken);
    }

    /**
     * Verifies that performing {@link WindowContainerTransaction#deleteTaskFragment} on
     * non-organized TaskFragment will throw {@link SecurityException}.
     */
    @Test
    @ApiTest(
            apis = {
                "android.window.TaskFragmentOrganizer#applyTransaction",
                "android.window.WindowContainerTransaction#deleteTaskFragment"
            })
    public void testDeleteTaskFragment_nonOrganizedTaskFragment_throwException() {
        final Activity activity = startNewActivity();
        final TaskFragmentInfo taskFragmentInfo =
                createOrganizedTaskFragment(mTaskFragmentOrganizer, activity);

        // Create another TaskFragmentOrganizer to request operation.
        final TaskFragmentOrganizer anotherOrganizer = registerNewOrganizer();
        final WindowContainerTransaction wct =
                new WindowContainerTransaction()
                        .deleteTaskFragment(taskFragmentInfo.getFragmentToken());

        assertThrows(
                SecurityException.class,
                () ->
                        anotherOrganizer.applyTransaction(
                                wct,
                                TASK_FRAGMENT_TRANSIT_CLOSE,
                                false /* shouldApplyIndependently */));
    }

    /**
     * Verifies that performing {@link WindowContainerTransaction#deleteTaskFragment} on organized
     * TaskFragment is allowed.
     */
    @Test
    @ApiTest(
            apis = {
                "android.window.TaskFragmentOrganizer#applyTransaction",
                "android.window.WindowContainerTransaction#deleteTaskFragment"
            })
    public void testDeleteTaskFragment_organizedTaskFragment() {
        final Activity activity = startNewActivity();
        final TaskFragmentInfo taskFragmentInfo =
                createOrganizedTaskFragment(mTaskFragmentOrganizer, activity);

        final WindowContainerTransaction wct =
                new WindowContainerTransaction()
                        .deleteTaskFragment(taskFragmentInfo.getFragmentToken());

        mTaskFragmentOrganizer.applyTransaction(
                wct, TASK_FRAGMENT_TRANSIT_CLOSE, false /* shouldApplyIndependently */);
    }

    /**
     * Verifies that performing {@link WindowContainerTransaction#startActivityInTaskFragment} on
     * non-organized TaskFragment will throw {@link SecurityException}.
     */
    @Test
    @ApiTest(
            apis = {
                "android.window.TaskFragmentOrganizer#applyTransaction",
                "android.window.WindowContainerTransaction#startActivityInTaskFragment"
            })
    public void testStartActivityInTaskFragment_nonOrganizedTaskFragment_throwException() {
        final Activity activity = startNewActivity();
        final TaskFragmentInfo taskFragmentInfo =
                createOrganizedTaskFragment(mTaskFragmentOrganizer, activity);
        final IBinder fragmentToken = taskFragmentInfo.getFragmentToken();
        final IBinder callerToken = getActivityToken(activity);
        final Intent intent = new Intent(mContext, WindowContextTestActivity.class);

        // Create another TaskFragmentOrganizer to request operation.
        final TaskFragmentOrganizer anotherOrganizer = registerNewOrganizer();
        final WindowContainerTransaction wct =
                new WindowContainerTransaction()
                        .startActivityInTaskFragment(
                                fragmentToken, callerToken, intent, null /* activityOptions */);

        assertThrows(
                SecurityException.class,
                () ->
                        anotherOrganizer.applyTransaction(
                                wct,
                                TASK_FRAGMENT_TRANSIT_OPEN,
                                false /* shouldApplyIndependently */));
    }

    /**
     * Verifies that performing {@link WindowContainerTransaction#startActivityInTaskFragment} on
     * organized TaskFragment is allowed.
     */
    @Test
    @ApiTest(
            apis = {
                "android.window.TaskFragmentOrganizer#applyTransaction",
                "android.window.WindowContainerTransaction#startActivityInTaskFragment"
            })
    public void testStartActivityInTaskFragment_organizedTaskFragment() {
        final Activity activity = startNewActivity();
        final TaskFragmentInfo taskFragmentInfo =
                createOrganizedTaskFragment(mTaskFragmentOrganizer, activity);
        final IBinder fragmentToken = taskFragmentInfo.getFragmentToken();
        final IBinder callerToken = getActivityToken(activity);
        final Intent intent = new Intent(mContext, MetricsActivity.class);

        final WindowContainerTransaction wct =
                new WindowContainerTransaction()
                        .startActivityInTaskFragment(
                                fragmentToken, callerToken, intent, null /* activityOptions */);

        mTaskFragmentOrganizer.applyTransaction(
                wct, TASK_FRAGMENT_TRANSIT_OPEN, false /* shouldApplyIndependently */);

        waitAndAssertResumedActivity(intent.getComponent());
        mTaskFragmentOrganizer.waitForAndGetTaskFragmentInfo(
                fragmentToken, info -> info.getActivities().size() == 1,
                "getActivities from TaskFragment must contain 1 activities");
    }

    /**
     * Verifies that performing {@link WindowContainerTransaction#requestFocusOnTaskFragment} on
     * non-organized TaskFragment will throw {@link SecurityException}.
     */
    @Test
    @ApiTest(
            apis = {
                "android.window.TaskFragmentOrganizer#applyTransaction",
                "android.window.WindowContainerTransaction#requestFocusOnTaskFragment"
            })
    public void testRequestFocusOnTaskFragment_nonOrganizedTaskFragment_throwException() {
        final Activity activity = startNewActivity();
        final TaskFragmentInfo taskFragmentInfo =
                createOrganizedTaskFragment(mTaskFragmentOrganizer, activity);
        final IBinder fragmentToken = taskFragmentInfo.getFragmentToken();

        // Create another TaskFragmentOrganizer to request operation.
        final TaskFragmentOrganizer anotherOrganizer = registerNewOrganizer();
        final WindowContainerTransaction wct =
                new WindowContainerTransaction().requestFocusOnTaskFragment(fragmentToken);

        assertThrows(
                SecurityException.class,
                () ->
                        anotherOrganizer.applyTransaction(
                                wct,
                                TASK_FRAGMENT_TRANSIT_CHANGE,
                                false /* shouldApplyIndependently */));
    }

    /**
     * Verifies that performing {@link WindowContainerTransaction#requestFocusOnTaskFragment} on
     * organized TaskFragment is allowed.
     */
    @Test
    @ApiTest(
            apis = {
                "android.window.TaskFragmentOrganizer#applyTransaction",
                "android.window.WindowContainerTransaction#requestFocusOnTaskFragment"
            })
    public void testRequestFocusOnTaskFragment_organizedTaskFragment() {
        final Activity activity = startNewActivity();
        final TaskFragmentInfo taskFragmentInfo =
                createOrganizedTaskFragment(mTaskFragmentOrganizer, activity);
        final IBinder fragmentToken = taskFragmentInfo.getFragmentToken();

        final WindowContainerTransaction wct =
                new WindowContainerTransaction().requestFocusOnTaskFragment(fragmentToken);

        mTaskFragmentOrganizer.applyTransaction(
                wct, TASK_FRAGMENT_TRANSIT_CHANGE, false /* shouldApplyIndependently */);
    }

    /**
     * Verifies that performing {@link WindowContainerTransaction#reparentActivityToTaskFragment} on
     * non-organized TaskFragment will throw {@link SecurityException}.
     */
    @Test
    @ApiTest(
            apis = {
                "android.window.TaskFragmentOrganizer#applyTransaction",
                "android.window.WindowContainerTransaction#reparentActivityToTaskFragment"
            })
    public void testReparentActivityToTaskFragment_nonOrganizedTaskFragment_throwException() {
        final Activity activity = startNewActivity();
        final TaskFragmentInfo taskFragmentInfo =
                createOrganizedTaskFragment(mTaskFragmentOrganizer, activity);
        final IBinder fragmentToken = taskFragmentInfo.getFragmentToken();
        final IBinder activityToken = getActivityToken(activity);

        // Create another TaskFragmentOrganizer to request operation.
        final TaskFragmentOrganizer anotherOrganizer = registerNewOrganizer();
        final WindowContainerTransaction wct =
                new WindowContainerTransaction()
                        .reparentActivityToTaskFragment(fragmentToken, activityToken);

        assertThrows(
                SecurityException.class,
                () ->
                        anotherOrganizer.applyTransaction(
                                wct,
                                TASK_FRAGMENT_TRANSIT_CHANGE,
                                false /* shouldApplyIndependently */));
    }

    /**
     * Verifies that performing {@link WindowContainerTransaction#reparentActivityToTaskFragment} on
     * organized TaskFragment is allowed.
     */
    @Test
    @ApiTest(
            apis = {
                "android.window.TaskFragmentOrganizer#applyTransaction",
                "android.window.WindowContainerTransaction#startActivityInTaskFragment"
            })
    public void testReparentActivityToTaskFragment_organizedTaskFragment() {
        final Activity activity = startNewActivity();
        final TaskFragmentInfo taskFragmentInfo =
                createOrganizedTaskFragment(mTaskFragmentOrganizer, activity);
        final IBinder fragmentToken = taskFragmentInfo.getFragmentToken();
        final IBinder activityToken = getActivityToken(activity);

        final WindowContainerTransaction wct =
                new WindowContainerTransaction()
                        .reparentActivityToTaskFragment(fragmentToken, activityToken);

        mTaskFragmentOrganizer.applyTransaction(
                wct, TASK_FRAGMENT_TRANSIT_CHANGE, false /* shouldApplyIndependently */);
    }

    /**
     * Verifies that performing {@link WindowContainerTransaction#setAdjacentTaskFragments} on
     * non-organized TaskFragment will throw {@link SecurityException}.
     */
    @Test
    @ApiTest(
            apis = {
                "android.window.TaskFragmentOrganizer#applyTransaction",
                "android.window.WindowContainerTransaction#setAdjacentTaskFragments"
            })
    public void testSetAdjacentTaskFragments_nonOrganizedTaskFragment_throwException() {
        final Activity activity = startNewActivity();
        final TaskFragmentInfo taskFragmentInfo0 =
                createOrganizedTaskFragment(mTaskFragmentOrganizer, activity);
        final TaskFragmentInfo taskFragmentInfo1 =
                createOrganizedTaskFragment(mTaskFragmentOrganizer, activity);
        final IBinder fragmentToken0 = taskFragmentInfo0.getFragmentToken();
        final IBinder fragmentToken1 = taskFragmentInfo1.getFragmentToken();

        // Create another TaskFragmentOrganizer to request operation.
        final TaskFragmentOrganizer anotherOrganizer = registerNewOrganizer();
        final WindowContainerTransaction wct =
                new WindowContainerTransaction()
                        .setAdjacentTaskFragments(
                                fragmentToken0, fragmentToken1, null /* params */);

        assertThrows(
                SecurityException.class,
                () ->
                        anotherOrganizer.applyTransaction(
                                wct,
                                TASK_FRAGMENT_TRANSIT_CHANGE,
                                false /* shouldApplyIndependently */));
    }

    /**
     * Verifies that performing {@link WindowContainerTransaction#setAdjacentTaskFragments} on
     * organized TaskFragment is allowed.
     */
    @Test
    @ApiTest(
            apis = {
                "android.window.TaskFragmentOrganizer#applyTransaction",
                "android.window.WindowContainerTransaction#setAdjacentTaskFragments"
            })
    public void testSetAdjacentTaskFragments_organizedTaskFragment() {
        final Activity activity = startNewActivity();
        final TaskFragmentInfo taskFragmentInfo0 =
                createOrganizedTaskFragment(mTaskFragmentOrganizer, activity);
        final TaskFragmentInfo taskFragmentInfo1 =
                createOrganizedTaskFragment(mTaskFragmentOrganizer, activity);
        final IBinder fragmentToken0 = taskFragmentInfo0.getFragmentToken();
        final IBinder fragmentToken1 = taskFragmentInfo1.getFragmentToken();

        final WindowContainerTransaction wct =
                new WindowContainerTransaction()
                        .setAdjacentTaskFragments(
                                fragmentToken0, fragmentToken1, null /* params */);

        mTaskFragmentOrganizer.applyTransaction(
                wct, TASK_FRAGMENT_TRANSIT_CHANGE, false /* shouldApplyIndependently */);
    }

    /**
     * Verifies that changing property on non-TaskFragment window will throw {@link
     * SecurityException}.
     */
    @Test
    @ApiTest(
            apis = {
                "android.window.TaskFragmentOrganizer#applyTransaction",
                "android.window.WindowContainerTransaction#setRelativeBounds",
                "android.window.WindowContainerTransaction#setWindowingMode",
            })
    public void testSetProperty_nonTaskFragmentWindow_throwException() {
        final WindowContainerToken taskToken = getFirstTaskToken();
        final WindowContainerTransaction wct0 =
                new WindowContainerTransaction().setRelativeBounds(taskToken, new Rect());

        assertThrows(
                SecurityException.class,
                () ->
                        mTaskFragmentOrganizer.applyTransaction(
                                wct0,
                                TASK_FRAGMENT_TRANSIT_CHANGE,
                                false /* shouldApplyIndependently */));

        final WindowContainerTransaction wct1 =
                new WindowContainerTransaction()
                        .setWindowingMode(taskToken, WINDOWING_MODE_MULTI_WINDOW);

        assertThrows(
                SecurityException.class,
                () ->
                        mTaskFragmentOrganizer.applyTransaction(
                                wct1,
                                TASK_FRAGMENT_TRANSIT_CHANGE,
                                false /* shouldApplyIndependently */));
    }

    /**
     * Verifies that changing property on non-organized TaskFragment will throw {@link
     * SecurityException}.
     */
    @Test
    @ApiTest(
            apis = {
                "android.window.TaskFragmentOrganizer#applyTransaction",
                "android.window.WindowContainerTransaction#setRelativeBounds",
                "android.window.WindowContainerTransaction#setWindowingMode",
            })
    public void testSetProperty_nonOrganizedTaskFragment_throwException() {
        final Activity activity = startNewActivity();
        final TaskFragmentInfo taskFragmentInfo =
                createOrganizedTaskFragment(mTaskFragmentOrganizer, activity);
        final WindowContainerToken taskFragmentToken = taskFragmentInfo.getToken();

        // Create another TaskFragmentOrganizer to request operation.
        final TaskFragmentOrganizer anotherOrganizer = registerNewOrganizer();
        final WindowContainerTransaction wct0 =
                new WindowContainerTransaction().setRelativeBounds(taskFragmentToken, new Rect());

        assertThrows(
                SecurityException.class,
                () ->
                        anotherOrganizer.applyTransaction(
                                wct0,
                                TASK_FRAGMENT_TRANSIT_CHANGE,
                                false /* shouldApplyIndependently */));

        final WindowContainerTransaction wct1 =
                new WindowContainerTransaction()
                        .setWindowingMode(taskFragmentToken, WINDOWING_MODE_MULTI_WINDOW);

        assertThrows(
                SecurityException.class,
                () ->
                        anotherOrganizer.applyTransaction(
                                wct1,
                                TASK_FRAGMENT_TRANSIT_CHANGE,
                                false /* shouldApplyIndependently */));
    }

    /** Verifies that changing property on organized TaskFragment is allowed. */
    @Test
    @ApiTest(
            apis = {
                "android.window.TaskFragmentOrganizer#applyTransaction",
                "android.window.WindowContainerTransaction#setRelativeBounds",
                "android.window.WindowContainerTransaction#setWindowingMode",
            })
    public void testSetProperty_organizedTaskFragment() {
        final Activity activity = startNewActivity();
        final TaskFragmentInfo taskFragmentInfo =
                createOrganizedTaskFragment(mTaskFragmentOrganizer, activity);
        final WindowContainerToken taskFragmentToken = taskFragmentInfo.getToken();

        final WindowContainerTransaction wct0 =
                new WindowContainerTransaction().setRelativeBounds(taskFragmentToken, new Rect());

        mTaskFragmentOrganizer.applyTransaction(
                wct0, TASK_FRAGMENT_TRANSIT_CHANGE, false /* shouldApplyIndependently */);

        final WindowContainerTransaction wct1 =
                new WindowContainerTransaction()
                        .setWindowingMode(taskFragmentToken, WINDOWING_MODE_MULTI_WINDOW);

        mTaskFragmentOrganizer.applyTransaction(
                wct1, TASK_FRAGMENT_TRANSIT_CHANGE, false /* shouldApplyIndependently */);
    }

    /**
     * Verifies that the following {@link WindowContainerTransaction} operations are not allowed on
     * organized TaskFragment.
     */
    @Test
    @ApiTest(
            apis = {
                "android.window.TaskFragmentOrganizer#applyTransaction",
                "android.window.WindowContainerTransaction#setBounds",
                "android.window.WindowContainerTransaction#setAppBounds",
                "android.window.WindowContainerTransaction#setScreenSizeDp",
                "android.window.WindowContainerTransaction#setSmallestScreenWidthDp",
            })
    public void testSetProperty_unsupportedChange_throwException() {
        final Activity activity = startNewActivity();
        final TaskFragmentInfo taskFragmentInfo =
                createOrganizedTaskFragment(mTaskFragmentOrganizer, activity);
        final WindowContainerToken taskFragmentToken = taskFragmentInfo.getToken();

        final WindowContainerTransaction wct0 =
                new WindowContainerTransaction().setBounds(taskFragmentToken, new Rect());

        assertThrows(
                SecurityException.class,
                () ->
                        mTaskFragmentOrganizer.applyTransaction(
                                wct0,
                                TASK_FRAGMENT_TRANSIT_CHANGE,
                                false /* shouldApplyIndependently */));

        final WindowContainerTransaction wct1 =
                new WindowContainerTransaction().setAppBounds(taskFragmentToken, new Rect());

        assertThrows(
                SecurityException.class,
                () ->
                        mTaskFragmentOrganizer.applyTransaction(
                                wct1,
                                TASK_FRAGMENT_TRANSIT_CHANGE,
                                false /* shouldApplyIndependently */));

        final WindowContainerTransaction wct2 =
                new WindowContainerTransaction().setScreenSizeDp(taskFragmentToken, 100, 200);

        assertThrows(
                SecurityException.class,
                () ->
                        mTaskFragmentOrganizer.applyTransaction(
                                wct2,
                                TASK_FRAGMENT_TRANSIT_CHANGE,
                                false /* shouldApplyIndependently */));

        final WindowContainerTransaction wct3 =
                new WindowContainerTransaction().setSmallestScreenWidthDp(taskFragmentToken, 100);

        assertThrows(
                SecurityException.class,
                () ->
                        mTaskFragmentOrganizer.applyTransaction(
                                wct3,
                                TASK_FRAGMENT_TRANSIT_CHANGE,
                                false /* shouldApplyIndependently */));
    }

    /**
     * Verifies that config changes with the following {@link
     * WindowContainerTransaction.Change#getChangeMask()} are disallowed on organized TaskFragment.
     */
    @Test
    @ApiTest(
            apis = {
                "android.window.TaskFragmentOrganizer#applyTransaction",
                "android.window.WindowContainerTransaction#scheduleFinishEnterPip",
                "android.window.WindowContainerTransaction#setBoundsChangeTransaction",
                "android.window.WindowContainerTransaction#setFocusable",
                "android.window.WindowContainerTransaction#setHidden",
            })
    public void testApplyChange_unsupportedChangeMask_throwException() {
        final Activity activity = startNewActivity();
        final TaskFragmentInfo taskFragmentInfo =
                createOrganizedTaskFragment(mTaskFragmentOrganizer, activity);
        final WindowContainerToken token = taskFragmentInfo.getToken();

        final WindowContainerTransaction wct0 =
                new WindowContainerTransaction()
                        .scheduleFinishEnterPip(token, new Rect(0, 0, 100, 100));
        assertThrows(
                SecurityException.class,
                () ->
                        mTaskFragmentOrganizer.applyTransaction(
                                wct0,
                                TASK_FRAGMENT_TRANSIT_CHANGE,
                                false /* shouldApplyIndependently */));

        final WindowContainerTransaction wct1 =
                new WindowContainerTransaction()
                        .setBoundsChangeTransaction(token, new SurfaceControl.Transaction());
        assertThrows(
                SecurityException.class,
                () ->
                        mTaskFragmentOrganizer.applyTransaction(
                                wct1,
                                TASK_FRAGMENT_TRANSIT_CHANGE,
                                false /* shouldApplyIndependently */));

        final WindowContainerTransaction wct3 =
                new WindowContainerTransaction().setFocusable(token, false /* focusable */);
        assertThrows(
                SecurityException.class,
                () ->
                        mTaskFragmentOrganizer.applyTransaction(
                                wct3,
                                TASK_FRAGMENT_TRANSIT_CHANGE,
                                false /* shouldApplyIndependently */));

        final WindowContainerTransaction wct4 =
                new WindowContainerTransaction().setHidden(token, false /* hidden */);
        assertThrows(
                SecurityException.class,
                () ->
                        mTaskFragmentOrganizer.applyTransaction(
                                wct4,
                                TASK_FRAGMENT_TRANSIT_CHANGE,
                                false /* shouldApplyIndependently */));
    }

    /**
     * Verifies that performing {@link WindowContainerTransaction#reparent} from
     * TaskFragmentOrganizer will throw {@link SecurityException}.
     */
    @Test
    @ApiTest(
            apis = {
                "android.window.TaskFragmentOrganizer#applyTransaction",
                "android.window.WindowContainerTransaction#reparent"
            })
    public void testDisallowOperation_reparent() {
        final Activity activity = startNewActivity();
        final TaskFragmentInfo taskFragmentInfo0 =
                createOrganizedTaskFragment(mTaskFragmentOrganizer, activity);
        final TaskFragmentInfo taskFragmentInfo1 =
                createOrganizedTaskFragment(mTaskFragmentOrganizer, activity);
        final WindowContainerToken taskFragmentToken0 = taskFragmentInfo0.getToken();
        final WindowContainerToken taskFragmentToken1 = taskFragmentInfo1.getToken();

        final WindowContainerTransaction wct =
                new WindowContainerTransaction()
                        .reparent(taskFragmentToken0, taskFragmentToken1, true /* onTop */);

        assertThrows(
                SecurityException.class,
                () ->
                        mTaskFragmentOrganizer.applyTransaction(
                                wct,
                                TASK_FRAGMENT_TRANSIT_CHANGE,
                                false /* shouldApplyIndependently */));
    }

    /**
     * Verifies that performing {@link WindowContainerTransaction#reorder} from
     * TaskFragmentOrganizer will throw {@link SecurityException}.
     */
    @Test
    @ApiTest(
            apis = {
                "android.window.TaskFragmentOrganizer#applyTransaction",
                "android.window.WindowContainerTransaction#reorder"
            })
    public void testDisallowOperation_reorder() {
        final Activity activity = startNewActivity();
        final TaskFragmentInfo taskFragmentInfo =
                createOrganizedTaskFragment(mTaskFragmentOrganizer, activity);
        final WindowContainerToken taskFragmentToken = taskFragmentInfo.getToken();

        final WindowContainerTransaction wct =
                new WindowContainerTransaction().reorder(taskFragmentToken, true /* onTop */);

        assertThrows(
                SecurityException.class,
                () ->
                        mTaskFragmentOrganizer.applyTransaction(
                                wct,
                                TASK_FRAGMENT_TRANSIT_CHANGE,
                                false /* shouldApplyIndependently */));
    }

    /**
     * Verifies that performing {@link WindowContainerTransaction#setLaunchRoot} from
     * TaskFragmentOrganizer will throw {@link SecurityException}.
     */
    @Test
    @ApiTest(
            apis = {
                "android.window.TaskFragmentOrganizer#applyTransaction",
                "android.window.WindowContainerTransaction#setLaunchRoot"
            })
    public void testDisallowOperation_setLaunchRoot() {
        final Activity activity = startNewActivity();
        final TaskFragmentInfo taskFragmentInfo =
                createOrganizedTaskFragment(mTaskFragmentOrganizer, activity);
        final WindowContainerToken taskFragmentToken = taskFragmentInfo.getToken();

        final WindowContainerTransaction wct =
                new WindowContainerTransaction()
                        .setLaunchRoot(
                                taskFragmentToken,
                                null /* windowingModes */,
                                null /* activityTypes */);

        assertThrows(
                SecurityException.class,
                () ->
                        mTaskFragmentOrganizer.applyTransaction(
                                wct,
                                TASK_FRAGMENT_TRANSIT_CHANGE,
                                false /* shouldApplyIndependently */));
    }

    /**
     * Verifies that performing {@link WindowContainerTransaction#setLaunchAdjacentFlagRoot} from
     * TaskFragmentOrganizer will throw {@link SecurityException}.
     */
    @Test
    @ApiTest(
            apis = {
                "android.window.TaskFragmentOrganizer#applyTransaction",
                "android.window.WindowContainerTransaction#setLaunchAdjacentFlagRoot"
            })
    public void testDisallowOperation_setLaunchAdjacentFlagRoot() {
        final Activity activity = startNewActivity();
        final TaskFragmentInfo taskFragmentInfo =
                createOrganizedTaskFragment(mTaskFragmentOrganizer, activity);
        final WindowContainerToken taskFragmentToken = taskFragmentInfo.getToken();

        final WindowContainerTransaction wct =
                new WindowContainerTransaction().setLaunchAdjacentFlagRoot(taskFragmentToken);

        assertThrows(
                SecurityException.class,
                () ->
                        mTaskFragmentOrganizer.applyTransaction(
                                wct,
                                TASK_FRAGMENT_TRANSIT_CHANGE,
                                false /* shouldApplyIndependently */));
    }

    /**
     * Verifies that performing {@link WindowContainerTransaction#clearLaunchAdjacentFlagRoot} from
     * TaskFragmentOrganizer will throw {@link SecurityException}.
     */
    @Test
    @ApiTest(
            apis = {
                "android.window.TaskFragmentOrganizer#applyTransaction",
                "android.window.WindowContainerTransaction#clearLaunchAdjacentFlagRoot"
            })
    public void testDisallowOperation_clearLaunchAdjacentFlagRoot() {
        final Activity activity = startNewActivity();
        final TaskFragmentInfo taskFragmentInfo =
                createOrganizedTaskFragment(mTaskFragmentOrganizer, activity);
        final WindowContainerToken taskFragmentToken = taskFragmentInfo.getToken();

        final WindowContainerTransaction wct =
                new WindowContainerTransaction().clearLaunchAdjacentFlagRoot(taskFragmentToken);

        assertThrows(
                SecurityException.class,
                () ->
                        mTaskFragmentOrganizer.applyTransaction(
                                wct,
                                TASK_FRAGMENT_TRANSIT_CHANGE,
                                false /* shouldApplyIndependently */));
    }

    /**
     * Verifies the behavior to start Activity in a new created Task in TaskFragment is forbidden.
     */
    @Test
    public void testStartActivityFromAnotherProcessInNewTask_ThrowException() {
        final Activity activity = startNewActivity();
        final IBinder ownerToken = getActivityToken(activity);
        final TaskFragmentCreationParams params =
                mTaskFragmentOrganizer.generateTaskFragParams(ownerToken);
        final IBinder taskFragToken = params.getFragmentToken();
        final Intent intent =
                new Intent()
                        .setComponent(LAUNCHING_ACTIVITY)
                        .addFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_MULTIPLE_TASK);
        final IBinder errorCallbackToken = new Binder();

        WindowContainerTransaction wct =
                new WindowContainerTransaction()
                        .setErrorCallbackToken(errorCallbackToken)
                        .createTaskFragment(params)
                        .startActivityInTaskFragment(
                                taskFragToken, ownerToken, intent, null /* activityOptions */);

        mTaskFragmentOrganizer.applyTransaction(
                wct, TASK_FRAGMENT_TRANSIT_OPEN, false /* shouldApplyIndependently */);
        mTaskFragmentOrganizer.waitForTaskFragmentError();

        assertThat(mTaskFragmentOrganizer.getThrowable()).isInstanceOf(SecurityException.class);
        assertThat(mTaskFragmentOrganizer.getErrorCallbackToken()).isEqualTo(errorCallbackToken);

        // Activity must be launched on a new task instead.
        waitAndAssertActivityLaunchOnTask(LAUNCHING_ACTIVITY);
    }

    /**
     * Verifies the behavior of starting an Activity of another app in TaskFragment is not allowed
     * without permissions.
     */
    @Test
    public void testStartAnotherAppActivityInTaskFragment() {
        final Activity activity = startNewActivity();
        final IBinder ownerToken = getActivityToken(activity);
        final TaskFragmentCreationParams params =
                mTaskFragmentOrganizer.generateTaskFragParams(ownerToken);
        final IBinder taskFragToken = params.getFragmentToken();
        final WindowContainerTransaction wct =
                new WindowContainerTransaction()
                        .createTaskFragment(params)
                        .startActivityInTaskFragment(
                                taskFragToken,
                                ownerToken,
                                new Intent().setComponent(SDK_30_TEST_ACTIVITY),
                                null /* activityOptions */);
        mTaskFragmentOrganizer.applyTransaction(
                wct, TASK_FRAGMENT_TRANSIT_OPEN, false /* shouldApplyIndependently */);
        mTaskFragmentOrganizer.waitForTaskFragmentCreated();

        // Launching an activity of another app in TaskFragment should report error.
        mTaskFragmentOrganizer.waitForTaskFragmentError();
        assertThat(mTaskFragmentOrganizer.getThrowable()).isInstanceOf(SecurityException.class);

        // Making sure activity is not launched on the TaskFragment
        TaskFragmentInfo info = mTaskFragmentOrganizer.getTaskFragmentInfo(taskFragToken);
        assertEmptyTaskFragment(info, taskFragToken);

        // Activity must be launched on a new task instead.
        waitAndAssertActivityLaunchOnTask(SDK_30_TEST_ACTIVITY);
    }

    private void waitAndAssertActivityLaunchOnTask(ComponentName activityName) {
        waitAndAssertResumedActivity(activityName, "Activity must be resumed.");

        Task task = mWmState.getTaskByActivity(activityName);
        assertWithMessage("Launching activity must be started on Task")
                .that(task.getActivities())
                .contains(mWmState.getActivity(activityName));
    }

    /**
     * Verifies the behavior of starting an Activity of another app while activities of the host app
     * are already embedded in TaskFragment.
     */
    @Test
    public void testStartAnotherAppActivityWithEmbeddedTaskFragments() {
        final Activity activity = startNewActivity();
        final IBinder ownerToken = getActivityToken(activity);
        final TaskFragmentCreationParams params =
                mTaskFragmentOrganizer.generateTaskFragParams(ownerToken);
        final IBinder taskFragToken = params.getFragmentToken();
        final Intent intent = new Intent(mContext, MetricsActivity.class);
        final WindowContainerTransaction wct =
                new WindowContainerTransaction()
                        .createTaskFragment(params)
                        .startActivityInTaskFragment(
                                taskFragToken,
                                ownerToken,
                                intent,
                                null /* activityOptions */);
        mTaskFragmentOrganizer.applyTransaction(
                wct, TASK_FRAGMENT_TRANSIT_OPEN, false /* shouldApplyIndependently */);
        mTaskFragmentOrganizer.waitForTaskFragmentCreated();
        mTaskFragmentOrganizer.waitForAndGetTaskFragmentInfo(
                taskFragToken,
                info -> info.getActivities().size() == 1,
                "getActivities from TaskFragment must contain 1 activities");
        waitAndAssertResumedActivity(intent.getComponent());

        activity.startActivity(new Intent().setComponent(SDK_30_TEST_ACTIVITY));

        waitAndAssertActivityState(
                SDK_30_TEST_ACTIVITY, STATE_RESUMED, "Activity should be resumed.");
        TaskFragmentInfo info = mTaskFragmentOrganizer.getTaskFragmentInfo(taskFragToken);
        assertEquals(1, info.getActivities().size());
    }

    /**
     * Verifies whether creating TaskFragment with non-resizeable {@link Activity} leads to {@link
     * IllegalArgumentException} returned by {@link
     * TaskFragmentOrganizer#onTaskFragmentError(IBinder, Throwable)}.
     */
    @Test
    public void testCreateTaskFragmentWithNonResizeableActivity_ThrowException() {
        // Pass non-resizeable Activity's token to TaskFragmentCreationParams and tries to
        // create a TaskFragment with the params.
        final Activity activity =
                startNewActivity(HelperActivities.NonResizeablePortraitActivity.class);
        final IBinder ownerToken = getActivityToken(activity);
        final TaskFragmentCreationParams params =
                mTaskFragmentOrganizer.generateTaskFragParams(ownerToken);
        final WindowContainerTransaction wct =
                new WindowContainerTransaction().createTaskFragment(params);

        mTaskFragmentOrganizer.applyTransaction(
                wct, TASK_FRAGMENT_TRANSIT_OPEN, false /* shouldApplyIndependently */);

        mTaskFragmentOrganizer.waitForTaskFragmentError();

        assertThat(mTaskFragmentOrganizer.getThrowable())
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** Verifies that the TaskFragment hierarchy ops should still work while in lock task mode. */
    @Test
    public void testApplyHierarchyOpsInLockTaskMode() {
        // Start an activity
        final Activity activity = startNewActivity();

        try {
            // Lock the task
            runWithShellPermission(
                    () -> {
                        mAtm.startSystemLockTaskMode(activity.getTaskId());
                    });
            waitForOrFail(
                    "Task in app pinning mode",
                    () -> {
                        return mAm.getLockTaskModeState() == LOCK_TASK_MODE_PINNED;
                    });

            // Create TaskFragment and reparent the activity
            final IBinder ownerToken = getActivityToken(activity);
            final TaskFragmentCreationParams params =
                    mTaskFragmentOrganizer.generateTaskFragParams(ownerToken);
            final IBinder taskFragToken = params.getFragmentToken();
            WindowContainerTransaction wct =
                    new WindowContainerTransaction()
                            .createTaskFragment(params)
                            .reparentActivityToTaskFragment(taskFragToken, ownerToken);
            mTaskFragmentOrganizer.applyTransaction(
                    wct, TASK_FRAGMENT_TRANSIT_CHANGE, false /* shouldApplyIndependently */);

            // Verifies it works
            mTaskFragmentOrganizer.waitForTaskFragmentCreated();
            TaskFragmentInfo info = mTaskFragmentOrganizer.getTaskFragmentInfo(taskFragToken);
            assertEquals(1, info.getActivities().size());

            // Delete the TaskFragment
            wct = new WindowContainerTransaction().deleteTaskFragment(taskFragToken);
            mTaskFragmentOrganizer.applyTransaction(
                    wct, TASK_FRAGMENT_TRANSIT_CLOSE, false /* shouldApplyIndependently */);

            // Verifies the TaskFragment NOT removed because the removal would also empty the task.
            mTaskFragmentOrganizer.waitForTaskFragmentError();
            assertThat(mTaskFragmentOrganizer.getThrowable())
                    .isInstanceOf(IllegalStateException.class);
            info = mTaskFragmentOrganizer.getTaskFragmentInfo(taskFragToken);
            assertEquals(1, info.getActivities().size());
        } finally {
            runWithShellPermission(
                    () -> {
                        mAtm.stopSystemLockTaskMode();
                    });
        }
    }

    /** Verifies that {@link TaskFragmentOrganizer#applySyncTransaction} is not allowed. */
    @Test
    @ApiTest(apis = {"android.window.TaskFragmentOrganizer#applySyncTransaction"})
    public void testApplySyncTransaction_disallowed() {
        final Activity activity = startNewActivity();
        final TaskFragmentInfo taskFragmentInfo =
                createOrganizedTaskFragment(mTaskFragmentOrganizer, activity);

        final WindowContainerTransaction wct =
                new WindowContainerTransaction()
                        .deleteTaskFragment(taskFragmentInfo.getFragmentToken());
        final WindowContainerTransactionCallback callback =
                new WindowContainerTransactionCallback() {
                    @Override
                    public void onTransactionReady(int id, @NonNull SurfaceControl.Transaction t) {
                        fail("Transaction shouldn't be executed");
                    }
                };

        assertThrows(
                SecurityException.class,
                () -> mTaskFragmentOrganizer.applySyncTransaction(wct, callback));
    }

    /**
     * Creates and registers a {@link TaskFragmentOrganizer} that will be unregistered in {@link
     * #tearDown()}.
     */
    private BasicTaskFragmentOrganizer registerNewOrganizer() {
        final BasicTaskFragmentOrganizer organizer = new BasicTaskFragmentOrganizer();
        organizer.registerOrganizer();
        mOrganizers.add(organizer);
        return organizer;
    }

    /**
     * Registers a {@link TaskOrganizer} to get the {@link WindowContainerToken} of a Task. The
     * organizer will be unregistered in {@link #tearDown()}.
     */
    private WindowContainerToken getFirstTaskToken() {
        final List<TaskAppearedInfo> taskInfos = new ArrayList<>();
        // Register TaskOrganizer to obtain Task information.
        NestedShellPermission.run(
                () -> {
                    mTaskOrganizer = new TaskOrganizer();
                    taskInfos.addAll(mTaskOrganizer.registerOrganizer());
                });
        return taskInfos.get(0).getTaskInfo().getToken();
    }

    /**
     * Creates a TaskFragment organized by the given organizer. The TaskFragment will be removed
     * when the organizer is unregistered.
     */
    private static TaskFragmentInfo createOrganizedTaskFragment(
            BasicTaskFragmentOrganizer organizer, Activity ownerActivity) {
        // Create a TaskFragment with a TaskFragmentOrganizer.
        final TaskFragmentCreationParams params =
                organizer.generateTaskFragParams(getActivityToken(ownerActivity));
        final WindowContainerTransaction wct =
                new WindowContainerTransaction().createTaskFragment(params);
        organizer.applyTransaction(
                wct, TASK_FRAGMENT_TRANSIT_OPEN, false /* shouldApplyIndependently */);

        // Wait for TaskFragment's creation to obtain its info.
        organizer.waitForTaskFragmentCreated();
        organizer.resetLatch();
        return organizer.getTaskFragmentInfo(params.getFragmentToken());
    }
}
