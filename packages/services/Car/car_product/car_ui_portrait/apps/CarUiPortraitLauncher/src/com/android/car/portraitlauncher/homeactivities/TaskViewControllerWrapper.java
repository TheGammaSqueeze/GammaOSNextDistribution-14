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

package com.android.car.portraitlauncher.homeactivities;

import android.app.ActivityManager;
import android.car.Car;
import android.car.app.RemoteCarRootTaskView;
import android.car.app.RemoteCarRootTaskViewCallback;
import android.car.app.RemoteCarTaskView;
import android.content.ComponentName;
import android.graphics.Rect;
import android.graphics.Region;
import android.os.Build;
import android.util.SparseArray;
import android.view.SurfaceView;

import androidx.annotation.NonNull;

import java.util.List;
import java.util.concurrent.Executor;

/**
 * An abstract class for organizing the Taskviews used in {@link CarUiPortraitHomeScreen}.
 * TODO(b/321807191) Remove this abstract class as only
 *  {@link RemoteCarTaskViewControllerWrapperImpl} implements it now.
 */
abstract class TaskViewControllerWrapper {
    protected static final boolean DBG = Build.IS_DEBUGGABLE;
    private static final String TAG = TaskViewControllerWrapper.class.getSimpleName();
    protected final SparseArray<SurfaceView> mTaskViewsMap;

    TaskViewControllerWrapper() {
        mTaskViewsMap = new SparseArray<>();
    }

    /** Destroy the current {@code TaskViewControllerWrapper}. */
    abstract void onDestroy();

    /**
     * Sets insets with type {@code WindowInsets.Type.systemOverlays()} for the taskview with given
     * {@code taskViewId}.
     */
    abstract void setSystemOverlayInsets(Rect bottomInsets, Rect topInsets,
            int taskViewId);

    /** Sets ObscuredTouchRegion for the taskview with given {@code taskViewId}. */
    abstract void setObscuredTouchRegion(Region obscuredTouchRegion, int taskViewId);

    /** Associates a {@code taskView} with given {@code taskViewId}. */
    void setTaskView(SurfaceView taskView, int taskViewId) {
        mTaskViewsMap.put(taskViewId, taskView);
    }

    /**
     * Brings the embedded task to the front for all the taskviews. Does nothing if there is no
     * task.
     *
     * @param taskViewIds the Ids of the task views which the embedded tasks should be shown for.
     */
    abstract void showEmbeddedTasks(int[] taskViewIds);

    /**
     * Updates the WM bounds for the underlying task as per the current view bounds for all the
     * taskviews. Does nothing if there is no task.
     */
    abstract void updateWindowBounds();

    /**
     * Creates a new taskview with root task.
     *
     * @param executor  the executor to get the  on.
     * @param callback  the callback to monitor the taskview related events.
     * @param displayId the display Id of the display which the root task will be created for.
     * @param list      the list of all the allow listed activities which will be persisted on the
     *                  root task that is embedded inside the task view.
     */
    abstract void createCarRootTaskView(TaskViewCallback callback, int displayId,
            Executor executor, List<ComponentName> list);

    /**
     * Creates a new default taskview with root task.
     *
     * @param displayId the display Id of the display which the root task will be created for.
     * @param executor  the executor to get the {@link RemoteCarRootTaskViewCallback} on.
     * @param callback  the callback to monitor the {@link RemoteCarRootTaskView} related events.
     */
    abstract void createCarDefaultRootTaskView(TaskViewCallback callback, int displayId,
            Executor executor);

    /**
     * Updates the list of activities that appear inside the taskview with given {@code
     * taskViewId}. This only works for certain types of taskviews.
     */
    abstract void updateAllowListedActivities(int taskViewId, List<ComponentName> list);

    /** Returns the task info of the top task running in the default taskview with root task. */
    abstract ActivityManager.RunningTaskInfo getRootTaskInfo();

    /** Moves the taskview with given {@code taskViewId} to the back. */
    abstract void moveToBack(int taskViewId);

    /**
     * Triggers the change in the WM bounds as per the {@code newBounds} received on the taskview
     * with given {@code taskViewId}
     */
    abstract void setWindowBounds(Rect taskViewBounds, int taskViewId);

    /**
     * Updates the visibility of the task in the taskview with given {@code taskViewId}.
     * @param visibility {true} if window needs to be displayed {false} otherwise.
     * @param taskViewId Identifier associate with the taskview.
     */
    abstract void updateTaskVisibility(boolean visibility, int taskViewId);

    /** Returns the taskview with given {@code taskViewId}. */
    abstract SurfaceView getTaskView(int taskViewId);

    /** Returns the task id of the top child task in with given {@code taskViewId}. */
    abstract int getTaskId(int taskViewId);

    /**
     * A callback interface for {@link TaskViewControllerWrapper}.
     */
    interface Callback {

        /**
         * Called when the {@link Car} is connected.
         */
        void onCarConnected();
    }

    /**
     * A callback interface for the host activity that uses {@link RemoteCarTaskView}
     * and their derivatives.
     */
    interface TaskViewCallback {

        /**
         * Called when the underlying {@link RemoteCarTaskView} instance is created.
         *
         * @param surfaceView the new newly created {@link RemoteCarTaskView}
         *                    instance.
         */
        void onTaskViewCreated(@NonNull SurfaceView surfaceView);

        /**
         * Called when a task with this {@code taskInfo} appears in the task view.
         */
        default void onTaskAppeared(ActivityManager.RunningTaskInfo taskInfo) {

        }

        /**
         * Called when a task's information has changed in this task view.
         *
         * @param taskInfo the new task info for the task
         */
        default void onTaskInfoChanged(ActivityManager.RunningTaskInfo taskInfo) {

        }
        /**
         * Called when the underlying {@link RemoteCarTaskView} is initialized.
         */
        void onTaskViewInitialized();

        /**
         * Called when the underlying {@link RemoteCarTaskView} is released.
         */
        default void onTaskViewReleased() {};
    }
}
