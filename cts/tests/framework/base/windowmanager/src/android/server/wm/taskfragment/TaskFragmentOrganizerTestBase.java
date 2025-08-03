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

import static android.app.ActivityTaskManager.INVALID_STACK_ID;
import static android.app.WindowConfiguration.WINDOWING_MODE_UNDEFINED;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static android.server.wm.WindowManagerState.STATE_RESUMED;
import static android.window.TaskFragmentOrganizer.TASK_FRAGMENT_TRANSIT_OPEN;
import static android.window.TaskFragmentTransaction.TYPE_ACTIVITY_REPARENTED_TO_TASK;
import static android.window.TaskFragmentTransaction.TYPE_TASK_FRAGMENT_APPEARED;
import static android.window.TaskFragmentTransaction.TYPE_TASK_FRAGMENT_ERROR;
import static android.window.TaskFragmentTransaction.TYPE_TASK_FRAGMENT_INFO_CHANGED;
import static android.window.TaskFragmentTransaction.TYPE_TASK_FRAGMENT_PARENT_INFO_CHANGED;
import static android.window.TaskFragmentTransaction.TYPE_TASK_FRAGMENT_VANISHED;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.server.wm.WindowContextTestActivity;
import android.server.wm.WindowManagerState.WindowContainer;
import android.server.wm.WindowManagerTestBase;
import android.util.ArrayMap;
import android.util.Log;
import android.window.TaskFragmentCreationParams;
import android.window.TaskFragmentInfo;
import android.window.TaskFragmentOrganizer;
import android.window.TaskFragmentTransaction;
import android.window.WindowContainerTransaction;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import javax.annotation.concurrent.GuardedBy;

public class TaskFragmentOrganizerTestBase extends WindowManagerTestBase {
    private static final String TAG = "TaskFragmentOrganizerTestBase";

    public BasicTaskFragmentOrganizer mTaskFragmentOrganizer;
    Activity mOwnerActivity;
    IBinder mOwnerToken;
    ComponentName mOwnerActivityName;
    int mOwnerTaskId;

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
        assumeTrue(supportsMultiWindow());
        mTaskFragmentOrganizer = new BasicTaskFragmentOrganizer();
        mTaskFragmentOrganizer.registerOrganizer();
        mOwnerActivity = setUpOwnerActivity();
        mOwnerToken = getActivityToken(mOwnerActivity);
        mOwnerActivityName = mOwnerActivity.getComponentName();
        mOwnerTaskId = mOwnerActivity.getTaskId();
        // Make sure the activity is launched and resumed, otherwise the window state may not be
        // stable.
        waitAndAssertResumedActivity(mOwnerActivity.getComponentName(),
                "The owner activity must be resumed.");
    }

    /** Setups the owner activity of the organized TaskFragment. */
    Activity setUpOwnerActivity() {
        // Launch activities in fullscreen in case the device may use freeform as the default
        // windowing mode.
        return startActivityInWindowingModeFullScreen(WindowContextTestActivity.class);
    }

    @After
    public void tearDown() {
        if (mTaskFragmentOrganizer != null) {
            mTaskFragmentOrganizer.unregisterOrganizer();
        }
    }

    public static IBinder getActivityToken(@NonNull Activity activity) {
        return activity.getWindow().getAttributes().token;
    }

    public static void assertEmptyTaskFragment(TaskFragmentInfo info,
            IBinder expectedTaskFragToken) {
        assertTaskFragmentInfoValidity(info, expectedTaskFragToken);
        assertWithMessage("TaskFragment must be empty").that(info.isEmpty()).isTrue();
        assertWithMessage("TaskFragmentInfo#getActivities must be empty")
                .that(info.getActivities()).isEmpty();
        assertWithMessage("TaskFragment must not contain any running Activity")
                .that(info.hasRunningActivity()).isFalse();
        assertWithMessage("TaskFragment must not be visible").that(info.isVisible()).isFalse();
    }

    public static void assertNotEmptyTaskFragment(TaskFragmentInfo info,
            IBinder expectedTaskFragToken, @Nullable IBinder ... expectedActivityTokens) {
        assertTaskFragmentInfoValidity(info, expectedTaskFragToken);
        assertWithMessage("TaskFragment must not be empty").that(info.isEmpty()).isFalse();
        assertWithMessage("TaskFragment must contain running Activity")
                .that(info.hasRunningActivity()).isTrue();
        if (expectedActivityTokens != null) {
            assertWithMessage("TaskFragmentInfo#getActivities must be empty")
                    .that(info.getActivities()).containsAtLeastElementsIn(expectedActivityTokens);
        }
    }

    private static void assertTaskFragmentInfoValidity(TaskFragmentInfo info,
            IBinder expectedTaskFragToken) {
        assertWithMessage("TaskFragmentToken must match the token from "
                + "TaskFragmentCreationParams#getFragmentToken")
                .that(info.getFragmentToken()).isEqualTo(expectedTaskFragToken);
        assertWithMessage("WindowContainerToken must not be null")
                .that(info.getToken()).isNotNull();
        assertWithMessage("TaskFragmentInfo#getPositionInParent must not be null")
                .that(info.getPositionInParent()).isNotNull();
        assertWithMessage("Configuration must not be empty")
                .that(info.getConfiguration()).isNotEqualTo(new Configuration());
    }

    /**
     * Verifies whether the window hierarchy is as expected or not.
     * <p>
     * The sample usage is as follows:
     * <pre class="prettyprint">
     * assertWindowHierarchy(rootTask, leafTask, taskFragment, activity);
     * </pre></p>
     *
     * @param containers The containers to be verified. It should be put from top to down
     */
    public static void assertWindowHierarchy(WindowContainer... containers) {
        for (int i = 0; i < containers.length - 2; i++) {
            final WindowContainer parent = containers[i];
            final WindowContainer child = containers[i + 1];
            assertWithMessage(parent + " must contains " + child)
                    .that(parent.getChildren())
                    .contains(child);
        }
    }

    /**
     * Builds, runs and waits for completion of task fragment creation transaction.
     * @param componentName name of the activity to launch in the TF, or {@code null} if none.
     * @return token of the created task fragment.
     */
    TaskFragmentInfo createTaskFragment(@Nullable ComponentName componentName) {
        return createTaskFragment(componentName, new Rect());
    }

    /**
     * Same as {@link #createTaskFragment(ComponentName)}, but allows to specify the bounds for the
     * new task fragment.
     */
    TaskFragmentInfo createTaskFragment(@Nullable ComponentName componentName,
            @NonNull Rect relativeBounds) {
        return createTaskFragment(componentName, relativeBounds, new WindowContainerTransaction());
    }

    /**
     * Same as {@link #createTaskFragment(ComponentName, Rect)}, but allows to specify the
     * {@link WindowContainerTransaction} to use.
     */
    TaskFragmentInfo createTaskFragment(@Nullable ComponentName componentName,
            @NonNull Rect relativeBounds, @NonNull WindowContainerTransaction wct) {
        final TaskFragmentCreationParams params = generateTaskFragCreationParams(relativeBounds);
        final IBinder taskFragToken = params.getFragmentToken();
        wct.createTaskFragment(params);
        if (componentName != null) {
            wct.startActivityInTaskFragment(taskFragToken, mOwnerToken,
                    new Intent().setComponent(componentName), null /* activityOptions */);
        }
        mTaskFragmentOrganizer.applyTransaction(wct, TASK_FRAGMENT_TRANSIT_OPEN,
                false /* shouldApplyIndependently */);
        mTaskFragmentOrganizer.waitForTaskFragmentCreated();

        if (componentName != null) {
            mWmState.waitForActivityState(componentName, STATE_RESUMED);
        }

        return mTaskFragmentOrganizer.getTaskFragmentInfo(taskFragToken);
    }

    @NonNull
    TaskFragmentCreationParams generateTaskFragCreationParams() {
        return mTaskFragmentOrganizer.generateTaskFragParams(mOwnerToken);
    }

    @NonNull
    TaskFragmentCreationParams generateTaskFragCreationParams(@NonNull Rect relativeBounds) {
        return mTaskFragmentOrganizer.generateTaskFragParams(mOwnerToken, relativeBounds,
                WINDOWING_MODE_UNDEFINED);
    }

    static Activity startNewActivity() {
        return startNewActivity(WindowContextTestActivity.class);
    }

    static Activity startNewActivity(Class<?> className) {
        final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        final Intent intent = new Intent(instrumentation.getTargetContext(), className)
                .addFlags(FLAG_ACTIVITY_NEW_TASK);
        return instrumentation.startActivitySync(intent);
    }

    public static class BasicTaskFragmentOrganizer extends TaskFragmentOrganizer {
        private final static int WAIT_TIMEOUT_IN_SECOND = 10;

        private final Object mLock = new Object();

        @GuardedBy("mLock")
        private final Map<IBinder, TaskFragmentInfo> mInfos = new ArrayMap<>();
        @GuardedBy("mLock")
        private final Map<IBinder, TaskFragmentInfo> mRemovedInfos = new ArrayMap<>();
        @GuardedBy("mLock")
        private int mParentTaskId = INVALID_STACK_ID;
        @Nullable
        @GuardedBy("mLock")
        private Configuration mParentConfig;
        @Nullable
        @GuardedBy("mLock")
        private IBinder mErrorToken;
        @Nullable
        @GuardedBy("mLock")
        private Throwable mThrowable;
        @GuardedBy("mLock")
        private boolean mIsRegistered;

        private CountDownLatch mAppearedLatch = new CountDownLatch(1);
        private CountDownLatch mChangedLatch = new CountDownLatch(1);
        private CountDownLatch mVanishedLatch = new CountDownLatch(1);
        private CountDownLatch mParentChangedLatch = new CountDownLatch(1);
        private CountDownLatch mErrorLatch = new CountDownLatch(1);

        BasicTaskFragmentOrganizer() {
            super(Runnable::run);
        }

        public TaskFragmentInfo getTaskFragmentInfo(IBinder taskFragToken) {
            synchronized (mLock) {
                return mInfos.get(taskFragToken);
            }
        }

        public TaskFragmentInfo getRemovedTaskFragmentInfo(IBinder taskFragToken) {
            synchronized (mLock) {
                return mRemovedInfos.get(taskFragToken);
            }
        }

        public Throwable getThrowable() {
            synchronized (mLock) {
                return mThrowable;
            }
        }

        public IBinder getErrorCallbackToken() {
            synchronized (mLock) {
                return mErrorToken;
            }
        }

        public void resetLatch() {
            mAppearedLatch = new CountDownLatch(1);
            mChangedLatch = new CountDownLatch(1);
            mVanishedLatch = new CountDownLatch(1);
            mParentChangedLatch = new CountDownLatch(1);
            mErrorLatch = new CountDownLatch(1);
        }

        /**
         * Generates a {@link TaskFragmentCreationParams} with {@code ownerToken} specified.
         *
         * @param ownerToken The token of {@link Activity} to create a TaskFragment under its parent
         *                   Task
         * @return the generated {@link TaskFragmentCreationParams}
         */
        @NonNull
        public TaskFragmentCreationParams generateTaskFragParams(@NonNull IBinder ownerToken) {
            return generateTaskFragParams(ownerToken, new Rect(), WINDOWING_MODE_UNDEFINED);
        }

        @NonNull
        public TaskFragmentCreationParams generateTaskFragParams(@NonNull IBinder ownerToken,
                @NonNull Rect relativeBounds, int windowingMode) {
            return generateTaskFragParams(new Binder(), ownerToken, relativeBounds, windowingMode);
        }

        @NonNull
        public TaskFragmentCreationParams generateTaskFragParams(@NonNull IBinder fragmentToken,
                @NonNull IBinder ownerToken, @NonNull Rect relativeBounds, int windowingMode) {
            return new TaskFragmentCreationParams.Builder(getOrganizerToken(), fragmentToken,
                    ownerToken)
                    .setInitialRelativeBounds(relativeBounds)
                    .setWindowingMode(windowingMode)
                    .build();
        }

        public void setAppearedCount(int count) {
            mAppearedLatch = new CountDownLatch(count);
        }

        public TaskFragmentInfo waitForAndGetTaskFragmentInfo(IBinder taskFragToken,
                Predicate<TaskFragmentInfo> condition, String message) {
            final TaskFragmentInfo[] info = new TaskFragmentInfo[1];
            waitForOrFail(message, () -> {
                info[0] = getTaskFragmentInfo(taskFragToken);
                return condition.test(info[0]);
            });
            return info[0];
        }

        public void waitForTaskFragmentCreated() {
            try {
                assertThat(mAppearedLatch.await(WAIT_TIMEOUT_IN_SECOND, TimeUnit.SECONDS)).isTrue();
            } catch (InterruptedException e) {
                fail("Assertion failed because of" + e);
            }
        }

        public void waitForTaskFragmentInfoChanged() {
            try {
                assertThat(mChangedLatch.await(WAIT_TIMEOUT_IN_SECOND, TimeUnit.SECONDS)).isTrue();
            } catch (InterruptedException e) {
                fail("Assertion failed because of" + e);
            }
        }

        public void waitForTaskFragmentRemoved() {
            try {
                assertThat(mVanishedLatch.await(WAIT_TIMEOUT_IN_SECOND, TimeUnit.SECONDS)).isTrue();
            } catch (InterruptedException e) {
                fail("Assertion failed because of" + e);
            }
        }

        public void waitForParentConfigChanged() {
            try {
                assertThat(mParentChangedLatch.await(WAIT_TIMEOUT_IN_SECOND, TimeUnit.SECONDS))
                        .isTrue();
            } catch (InterruptedException e) {
                fail("Assertion failed because of" + e);
            }
        }

        public void waitForTaskFragmentError() {
            try {
                assertThat(mErrorLatch.await(WAIT_TIMEOUT_IN_SECOND, TimeUnit.SECONDS)).isTrue();
            } catch (InterruptedException e) {
                fail("Assertion failed because of" + e);
            }
        }

        @GuardedBy("mLock")
        private void removeAllTaskFragments() {
            final WindowContainerTransaction wct = new WindowContainerTransaction();
            for (TaskFragmentInfo info : mInfos.values()) {
                wct.deleteTaskFragment(info.getFragmentToken());
            }
            applyTransaction(wct, TASK_FRAGMENT_TRANSIT_CLOSE,
                    false /* shouldApplyIndependently */);
        }

        @Override
        public void registerOrganizer() {
            synchronized (mLock) {
                mIsRegistered = true;
            }
            super.registerOrganizer();
        }

        @Override
        public void unregisterOrganizer() {
            synchronized (mLock) {
                mIsRegistered = false;
                removeAllTaskFragments();
                mRemovedInfos.clear();
                mInfos.clear();
                mParentTaskId = INVALID_STACK_ID;
                mParentConfig = null;
                mErrorToken = null;
                mThrowable = null;
            }
            super.unregisterOrganizer();
        }

        @Override
        public void onTransactionReady(@NonNull TaskFragmentTransaction transaction) {
            synchronized (mLock) {
                if (!mIsRegistered) {
                    // Ignore callback that is invoked after unregister. This can be a racing
                    // condition before the unregister reaches the server side.
                    return;
                }
                final List<TaskFragmentTransaction.Change> changes = transaction.getChanges();
                for (TaskFragmentTransaction.Change change : changes) {
                    final int taskId = change.getTaskId();
                    final TaskFragmentInfo info = change.getTaskFragmentInfo();
                    switch (change.getType()) {
                        case TYPE_TASK_FRAGMENT_APPEARED:
                            onTaskFragmentAppeared(info);
                            break;
                        case TYPE_TASK_FRAGMENT_INFO_CHANGED:
                            onTaskFragmentInfoChanged(info);
                            break;
                        case TYPE_TASK_FRAGMENT_VANISHED:
                            onTaskFragmentVanished(info);
                            break;
                        case TYPE_TASK_FRAGMENT_PARENT_INFO_CHANGED:
                            onTaskFragmentParentInfoChanged(taskId, change.getTaskConfiguration());
                            break;
                        case TYPE_TASK_FRAGMENT_ERROR:
                            final Bundle errorBundle = change.getErrorBundle();
                            final IBinder errorToken = change.getErrorCallbackToken();
                            final TaskFragmentInfo errorTaskFragmentInfo =
                                    errorBundle.getParcelable(
                                            KEY_ERROR_CALLBACK_TASK_FRAGMENT_INFO,
                                            TaskFragmentInfo.class);
                            final int opType = errorBundle.getInt(KEY_ERROR_CALLBACK_OP_TYPE);
                            final Throwable exception = errorBundle.getSerializable(
                                    KEY_ERROR_CALLBACK_THROWABLE, Throwable.class);
                            onTaskFragmentError(errorToken, errorTaskFragmentInfo, opType,
                                    exception);
                            break;
                        case TYPE_ACTIVITY_REPARENTED_TO_TASK:
                            onActivityReparentedToTask(
                                    taskId,
                                    change.getActivityIntent(),
                                    change.getActivityToken());
                            break;
                        default:
                            // Log instead of throwing exception in case we will add more types
                            // between releases.
                            Log.w(TAG, "Unknown TaskFragmentEvent=" + change.getType());
                    }
                }
                onTransactionHandled(transaction.getTransactionToken(),
                        new WindowContainerTransaction(), TASK_FRAGMENT_TRANSIT_NONE,
                        false /* shouldApplyIndependently */);
            }
        }

        @GuardedBy("mLock")
        private void onTaskFragmentAppeared(@NonNull TaskFragmentInfo taskFragmentInfo) {
            mInfos.put(taskFragmentInfo.getFragmentToken(), taskFragmentInfo);
            mAppearedLatch.countDown();
        }

        @GuardedBy("mLock")
        private void onTaskFragmentInfoChanged(@NonNull TaskFragmentInfo taskFragmentInfo) {
            mInfos.put(taskFragmentInfo.getFragmentToken(), taskFragmentInfo);
            mChangedLatch.countDown();
        }

        @GuardedBy("mLock")
        private void onTaskFragmentVanished(@NonNull TaskFragmentInfo taskFragmentInfo) {
            mInfos.remove(taskFragmentInfo.getFragmentToken());
            mRemovedInfos.put(taskFragmentInfo.getFragmentToken(), taskFragmentInfo);
            mVanishedLatch.countDown();
        }

        @GuardedBy("mLock")
        private void onTaskFragmentParentInfoChanged(int taskId,
                @NonNull Configuration parentConfig) {
            mParentTaskId = taskId;
            mParentConfig = parentConfig;
            mParentChangedLatch.countDown();
        }

        @GuardedBy("mLock")
        private void onTaskFragmentError(@NonNull IBinder errorCallbackToken,
                @Nullable TaskFragmentInfo taskFragmentInfo, int opType,
                @NonNull Throwable exception) {
            mErrorToken = errorCallbackToken;
            if (taskFragmentInfo != null) {
                mInfos.put(taskFragmentInfo.getFragmentToken(), taskFragmentInfo);
            }
            mThrowable = exception;
            mErrorLatch.countDown();
        }

        private void onActivityReparentedToTask(int taskId, @NonNull Intent activityIntent,
                @NonNull IBinder activityToken) {
            // TODO(b/232476698) Add CTS to verify PIP behavior with ActivityEmbedding
        }
    }
}
