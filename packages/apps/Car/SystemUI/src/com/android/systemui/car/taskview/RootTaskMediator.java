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

package com.android.systemui.car.taskview;

import static android.app.WindowConfiguration.ACTIVITY_TYPE_ASSISTANT;
import static android.app.WindowConfiguration.ACTIVITY_TYPE_HOME;
import static android.app.WindowConfiguration.ACTIVITY_TYPE_RECENTS;
import static android.app.WindowConfiguration.ACTIVITY_TYPE_STANDARD;
import static android.app.WindowConfiguration.WINDOWING_MODE_MULTI_WINDOW;
import static android.app.WindowConfiguration.WINDOWING_MODE_UNDEFINED;

import android.app.ActivityManager;
import android.car.app.CarActivityManager;
import android.util.Log;
import android.view.SurfaceControl;
import android.window.WindowContainerTransaction;

import com.android.internal.annotations.VisibleForTesting;
import com.android.wm.shell.ShellTaskOrganizer;
import com.android.wm.shell.common.SyncTransactionQueue;
import com.android.wm.shell.taskview.TaskViewBase;
import com.android.wm.shell.taskview.TaskViewTaskController;

import java.util.Iterator;
import java.util.LinkedHashMap;

/**
 * A mediator to {@link RemoteCarTaskViewServerImpl} that encapsulates the root task related logic.
 */
public final class RootTaskMediator implements ShellTaskOrganizer.TaskListener {
    private static final String TAG = RootTaskMediator.class.getSimpleName();

    private final int mDisplayId;
    private final boolean mIsLaunchRoot;
    private final int[] mActivityTypes;
    private final ShellTaskOrganizer mShellTaskOrganizer;
    private final SyncTransactionQueue mSyncQueue;
    private final TaskViewTaskController mTaskViewTaskShellPart;
    private final TaskViewBase mTaskViewClientPart;
    private final CarActivityManager mCarActivityManager;
    private final LinkedHashMap<Integer, ActivityManager.RunningTaskInfo> mTaskStack =
            new LinkedHashMap<>();

    private ActivityManager.RunningTaskInfo mRootTask;

    public RootTaskMediator(int displayId, boolean isLaunchRoot,
            boolean embedHomeTask,
            boolean embedRecentsTask,
            boolean embedAssistantTask,
            ShellTaskOrganizer shellTaskOrganizer,
            TaskViewTaskController taskViewTaskShellPart,
            TaskViewBase taskViewClientPart,
            SyncTransactionQueue syncQueue,
            CarActivityManager carActivityManager) {
        mDisplayId = displayId;
        mIsLaunchRoot = isLaunchRoot;
        mActivityTypes = createActivityArray(embedHomeTask, embedRecentsTask, embedAssistantTask);
        mShellTaskOrganizer = shellTaskOrganizer;
        mTaskViewTaskShellPart = taskViewTaskShellPart;
        mTaskViewClientPart = taskViewClientPart;
        mSyncQueue = syncQueue;
        mCarActivityManager = carActivityManager;

        mShellTaskOrganizer.createRootTask(displayId,
                WINDOWING_MODE_MULTI_WINDOW,
                this, /* removeWithTaskOrganizer= */ true);
    }

    @VisibleForTesting
    static int[] createActivityArray(boolean embedHomeTask, boolean embedRecentsTask,
            boolean embedAssistantTask) {
        int size = 1; // 1 for ACTIVITY_TYPE_STANDARD
        if (embedHomeTask) {
            size++;
        }
        if (embedRecentsTask) {
            size++;
        }
        if (embedAssistantTask) {
            size++;
        }
        int[] activityTypeArray = new int[size];
        int index = 0;
        activityTypeArray[index] = ACTIVITY_TYPE_STANDARD;
        index++;
        if (embedHomeTask) {
            activityTypeArray[index] = ACTIVITY_TYPE_HOME;
            index++;
        }
        if (embedRecentsTask) {
            activityTypeArray[index] = ACTIVITY_TYPE_RECENTS;
            index++;
        }
        if (embedAssistantTask) {
            activityTypeArray[index] = ACTIVITY_TYPE_ASSISTANT;
        }
        return activityTypeArray;
    }

    int getDisplayId() {
        return mDisplayId;
    }

    void release() {
        clearRootTask();
    }

    @Override
    public void onTaskAppeared(ActivityManager.RunningTaskInfo taskInfo, SurfaceControl leash) {
        ShellTaskOrganizer.TaskListener.super.onTaskAppeared(taskInfo, leash);

        // The first call to onTaskAppeared() is always for the root-task.
        if (mRootTask == null && !taskInfo.hasParentTask()) {
            mRootTask = taskInfo;
            if (mIsLaunchRoot) {
                setRootTaskAsLaunchRoot(taskInfo);
            }
            // Shell part will eventually trigger onTaskAppeared on the client as well.
            mTaskViewTaskShellPart.onTaskAppeared(taskInfo, leash);
            return;
        }

        // For all the children tasks, just update the client part and no notification/update is
        // sent to the shell part
        mTaskViewClientPart.onTaskAppeared(taskInfo, leash);
        mTaskStack.put(taskInfo.taskId, taskInfo);

        if (mIsLaunchRoot) {
            mCarActivityManager.onTaskAppeared(taskInfo, leash);
        }
    }

    @Override
    public void onTaskInfoChanged(ActivityManager.RunningTaskInfo taskInfo) {
        ShellTaskOrganizer.TaskListener.super.onTaskInfoChanged(taskInfo);
        if (mRootTask != null
                && mRootTask.taskId == taskInfo.taskId) {
            mTaskViewTaskShellPart.onTaskInfoChanged(taskInfo);
            return;
        }
        // For all the children tasks, just update the client part and no notification/update is
        // sent to the shell part
        mTaskViewClientPart.onTaskInfoChanged(taskInfo);
        if (mIsLaunchRoot) {
            mCarActivityManager.onTaskInfoChanged(taskInfo);
        }
        if (taskInfo.isVisible && mTaskStack.containsKey(taskInfo.taskId)) {
            // Remove the task and insert again so that it jumps to the end of
            // the queue.
            mTaskStack.remove(taskInfo.taskId);
            mTaskStack.put(taskInfo.taskId, taskInfo);
        }
    }

    @Override
    public void onTaskVanished(ActivityManager.RunningTaskInfo taskInfo) {
        ShellTaskOrganizer.TaskListener.super.onTaskVanished(taskInfo);
        if (mRootTask != null
                && mRootTask.taskId == taskInfo.taskId) {
            mTaskViewTaskShellPart.onTaskVanished(taskInfo);
            return;
        }
        // For all the children tasks, just update the client part and no notification/update is
        // sent to the shell part
        mTaskViewClientPart.onTaskVanished(taskInfo);
        if (mIsLaunchRoot) {
            mCarActivityManager.onTaskVanished(taskInfo);
        }
        if (mTaskStack.containsKey(taskInfo.taskId)) {
            mTaskStack.remove(taskInfo.taskId);
        }
    }

    @Override
    public void onBackPressedOnTaskRoot(ActivityManager.RunningTaskInfo taskInfo) {
        ShellTaskOrganizer.TaskListener.super.onBackPressedOnTaskRoot(taskInfo);
        if (mTaskStack.size() == 1) {
            Log.i(TAG, "Cannot remove last task from root task, display=" + mDisplayId);
            return;
        }
        if (mTaskStack.size() == 0) {
            Log.i(TAG, "Root task is empty, do nothing, display=" + mDisplayId);
            return;
        }

        ActivityManager.RunningTaskInfo topTask = getTopTaskInLaunchRootTask();
        WindowContainerTransaction wct = new WindowContainerTransaction();
        // removeTask() will trigger onTaskVanished which will remove the task locally
        // from mLaunchRootStack
        wct.removeTask(topTask.token);
        mSyncQueue.queue(wct);
    }

    @Override
    public void attachChildSurfaceToTask(int taskId, SurfaceControl.Builder b) {
        if (mRootTask != null
                && mRootTask.taskId == taskId) {
            mTaskViewTaskShellPart.attachChildSurfaceToTask(taskId, b);
            return;
        }
        ShellTaskOrganizer.TaskListener.super.attachChildSurfaceToTask(taskId, b);
    }

    @Override
    public void reparentChildSurfaceToTask(int taskId, SurfaceControl sc,
                                           SurfaceControl.Transaction t) {
        if (mRootTask != null
                && mRootTask.taskId == taskId) {
            mTaskViewTaskShellPart.reparentChildSurfaceToTask(taskId, sc, t);
            return;
        }
        ShellTaskOrganizer.TaskListener.super.reparentChildSurfaceToTask(taskId, sc, t);
    }

    private void setRootTaskAsLaunchRoot(ActivityManager.RunningTaskInfo taskInfo) {
        WindowContainerTransaction wct = new WindowContainerTransaction();
        wct.setLaunchRoot(taskInfo.token,
                        new int[]{WINDOWING_MODE_UNDEFINED},
                        mActivityTypes)
                .reorder(taskInfo.token, true);
        mSyncQueue.queue(wct);
    }

    private void clearRootTask() {
        if (mRootTask == null) {
            Log.w(TAG, "Unable to clear launch root task because it is not created.");
            return;
        }
        if (mIsLaunchRoot) {
            WindowContainerTransaction wct = new WindowContainerTransaction();
            wct.setLaunchRoot(mRootTask.token, null, null);
            mSyncQueue.queue(wct);
        }
        // Should run on shell's executor
        mShellTaskOrganizer.deleteRootTask(mRootTask.token);
        mShellTaskOrganizer.removeListener(this);
        mTaskStack.clear();
        mRootTask = null;
    }

    private ActivityManager.RunningTaskInfo getTopTaskInLaunchRootTask() {
        if (mTaskStack.isEmpty()) {
            return null;
        }
        ActivityManager.RunningTaskInfo topTask = null;
        Iterator<ActivityManager.RunningTaskInfo> iterator = mTaskStack.values().iterator();
        while (iterator.hasNext()) {
            topTask = iterator.next();
        }
        return topTask;
    }

    public boolean isLaunchRoot() {
        return mIsLaunchRoot;
    }

    @VisibleForTesting
    ActivityManager.RunningTaskInfo getRootTask() {
        return mRootTask;
    }

    @VisibleForTesting
    LinkedHashMap<Integer, ActivityManager.RunningTaskInfo> getTaskStack() {
        return mTaskStack;
    }

    @Override
    public String toString() {
        Iterator<ActivityManager.RunningTaskInfo> iterator = mTaskStack.values().iterator();
        StringBuilder stringBuilder = new StringBuilder("{" + "displayId=" + mDisplayId + " ,");
        stringBuilder.append("taskStack=[\n");
        ActivityManager.RunningTaskInfo topTask;
        while (iterator.hasNext()) {
            topTask = iterator.next();
            stringBuilder.append(" {" + " taskId=").append(topTask.taskId).append(",").append(
                    " baseActivity=").append(topTask.baseActivity).append("}\n");
        }
        stringBuilder.append("]\n}\n");
        return stringBuilder.toString();
    }
}
