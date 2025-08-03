/*
 * Copyright (C) 2020 The Android Open Source Project
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

import static android.app.ActivityTaskManager.INVALID_TASK_ID;

import static com.android.systemui.car.taskview.CarSystemUIProxyImpl.ensureManageSystemUIPermission;

import android.annotation.NonNull;
import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.app.ActivityTaskManager;
import android.app.PendingIntent;
import android.car.app.CarActivityManager;
import android.car.app.CarTaskViewClient;
import android.car.app.CarTaskViewHost;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Binder;
import android.os.Bundle;
import android.os.DeadSystemRuntimeException;
import android.util.Slog;
import android.util.SparseArray;
import android.view.InsetsSource;
import android.view.SurfaceControl;
import android.window.WindowContainerTransaction;

import com.android.internal.annotations.Keep;
import com.android.wm.shell.ShellTaskOrganizer;
import com.android.wm.shell.common.SyncTransactionQueue;
import com.android.wm.shell.taskview.TaskViewBase;
import com.android.wm.shell.taskview.TaskViewTaskController;
import com.android.wm.shell.taskview.TaskViewTransitions;

/** Server side implementation for {@code RemoteCarTaskView}. */
public class RemoteCarTaskViewServerImpl implements TaskViewBase {
    private static final String TAG = RemoteCarTaskViewServerImpl.class.getSimpleName();

    private final Context mContext;
    private final SyncTransactionQueue mSyncQueue;
    private final CarTaskViewClient mCarTaskViewClient;
    private final TaskViewTaskController mTaskViewTaskController;
    private final CarSystemUIProxyImpl mCarSystemUIProxy;
    private final Binder mInsetsOwner = new Binder();
    private final SparseArray<Rect> mInsets = new SparseArray<>();
    private final ShellTaskOrganizer mShellTaskOrganizer;
    private final CarActivityManager mCarActivityManager;

    private RootTaskMediator mRootTaskMediator;
    private boolean mReleased;

    private final CarTaskViewHost mHostImpl = new CarTaskViewHost() {
        @Override
        public void release() {
            ensureManageSystemUIPermission(mContext);
            if (mReleased) {
                Slog.w(TAG, "TaskView server part already released");
                return;
            }
            mInsets.clear();
            int taskIdToRemove = INVALID_TASK_ID;
            if (mTaskViewTaskController.getTaskInfo() != null) {
                taskIdToRemove = mTaskViewTaskController.getTaskInfo().taskId;
            }
            mTaskViewTaskController.release();

            if (mRootTaskMediator != null) {
                mRootTaskMediator.release();
            } else if (taskIdToRemove != INVALID_TASK_ID) {
                Slog.w(TAG, "Removing embedded task: " + taskIdToRemove);
                ActivityTaskManager.getInstance().removeTask(taskIdToRemove);
            }
            mCarSystemUIProxy.onCarTaskViewReleased(RemoteCarTaskViewServerImpl.this);
            mReleased = true;
        }

        @Override
        public void notifySurfaceCreated(SurfaceControl control) {
            ensureManageSystemUIPermission(mContext);
            mTaskViewTaskController.surfaceCreated(control);
        }

        @Override
        public void setWindowBounds(Rect bounds) {
            ensureManageSystemUIPermission(mContext);
            mTaskViewTaskController.setWindowBounds(bounds);
        }

        @Override
        public void notifySurfaceDestroyed() {
            ensureManageSystemUIPermission(mContext);
            mTaskViewTaskController.surfaceDestroyed();
        }

        @Override
        public void startActivity(
                PendingIntent pendingIntent,
                Intent fillInIntent,
                Bundle options,
                Rect launchBounds) {
            ensureManageSystemUIPermission(mContext);
            mTaskViewTaskController.startActivity(
                    pendingIntent,
                    fillInIntent,
                    ActivityOptions.fromBundle(options),
                    launchBounds);
        }

        /**
         * Creates the root task in this task view. Should be called only once for the lifetime of
         * this task view.
         */
        @Override
        // TODO(b/24087642): Remove @Keep once this method is promoted to SystemApi.
        // @Keep is used to prevent the removal of this method by the compiler as it is a hidden api
        // in the base class.
        @Keep
        public void createRootTask(int displayId) {
            ensureManageSystemUIPermission(mContext);
            if (mRootTaskMediator != null) {
                throw new IllegalStateException("Root task is already created for this task view.");
            }
            mRootTaskMediator = new RootTaskMediator(displayId, /* isLaunchRoot= */ false,
                    false, false, false, mShellTaskOrganizer,
                    mTaskViewTaskController, RemoteCarTaskViewServerImpl.this, mSyncQueue,
                    mCarActivityManager);
        }

        /**
         * Creates the launch root task in this task view. Should be called only once for the
         * lifetime of this task view.
         */
        @Override
        // TODO(b/24087642): Remove @Keep once this method is promoted to SystemApi.
        // @Keep is used to prevent the removal of this method by the compiler as it is a hidden api
        // in the base class.
        @Keep
        public void createLaunchRootTask(int displayId, boolean embedHomeTask,
                boolean embedRecentsTask, boolean embedAssistantTask) {
            ensureManageSystemUIPermission(mContext);
            if (mCarSystemUIProxy.isLaunchRootTaskPresent(displayId)) {
                throw new IllegalArgumentException("Cannot create more than 1 root task on the"
                        + " display=" + displayId);
            }

            // TODO(b/299535374): Remove setHideTaskWithSurface once the taskviews with launch root
            //  tasks are moved to an always visible window (surface) in SystemUI.
            mTaskViewTaskController.setHideTaskWithSurface(false);

            mRootTaskMediator = new RootTaskMediator(displayId, /* isLaunchRoot= */ true,
                    embedHomeTask, embedRecentsTask, embedAssistantTask, mShellTaskOrganizer,
                    mTaskViewTaskController, RemoteCarTaskViewServerImpl.this, mSyncQueue,
                    mCarActivityManager);
        }

        @Override
        public void showEmbeddedTask() {
            ensureManageSystemUIPermission(mContext);
            ActivityManager.RunningTaskInfo taskInfo = mTaskViewTaskController.getTaskInfo();
            if (taskInfo == null) {
                return;
            }
            WindowContainerTransaction wct = new WindowContainerTransaction();
            // Clears the hidden flag to make it TopFocusedRootTask: b/228092608
            wct.setHidden(taskInfo.token, /* hidden= */ false);
            // Moves the embedded task to the top to make it resumed: b/225388469
            wct.reorder(taskInfo.token, /* onTop= */ true);
            mSyncQueue.queue(wct);
        }

        @Override
        // TODO(b/24087642): Remove @Keep once this method is promoted to SystemApi.
        // @Keep is used to prevent the removal of this method by the compiler as it is a hidden api
        // in the base class.
        @Keep
        public void setTaskVisibility(boolean visibility) {
            ensureManageSystemUIPermission(mContext);
            ActivityManager.RunningTaskInfo taskInfo = mTaskViewTaskController.getTaskInfo();
            if (taskInfo == null) {
                return;
            }
            WindowContainerTransaction wct = new WindowContainerTransaction();
            wct.setHidden(taskInfo.token, !visibility);
            mSyncQueue.queue(wct);
        }

        @Override
        // TODO(b/24087642): Remove @Keep once this method is promoted to SystemApi.
        // @Keep is used to prevent the removal of this method by the compiler as it is a hidden api
        // in the base class.
        @Keep
        public void reorderTask(boolean onTop) {
            ensureManageSystemUIPermission(mContext);
            ActivityManager.RunningTaskInfo taskInfo = mTaskViewTaskController.getTaskInfo();
            if (taskInfo == null) {
                return;
            }
            WindowContainerTransaction wct = new WindowContainerTransaction();
            wct.reorder(taskInfo.token, onTop);
            mSyncQueue.queue(wct);
        }

        @Override
        public void addInsets(int index, int type, @NonNull Rect frame) {
            ensureManageSystemUIPermission(mContext);
            mInsets.append(InsetsSource.createId(mInsetsOwner, index, type), frame);

            if (mTaskViewTaskController.getTaskInfo() == null) {
                // The insets will be applied later as part of onTaskAppeared.
                Slog.w(TAG, "Cannot apply insets as the task token is not present.");
                return;
            }
            WindowContainerTransaction wct = new WindowContainerTransaction();
            wct.addInsetsSource(mTaskViewTaskController.getTaskInfo().token,
                    mInsetsOwner, index, type, frame, /* boundingRects = */ null);
            mSyncQueue.queue(wct);
        }

        @Override
        public void removeInsets(int index, int type) {
            ensureManageSystemUIPermission(mContext);
            if (mInsets.size() == 0) {
                Slog.w(TAG, "No insets set.");
                return;
            }
            int id = InsetsSource.createId(mInsetsOwner, index, type);
            if (!mInsets.contains(id)) {
                Slog.w(TAG, "Insets type: " + type + " can't be removed as it was not "
                        + "applied as part of the last addInsets()");
                return;
            }
            mInsets.remove(id);

            if (mTaskViewTaskController.getTaskInfo() == null) {
                Slog.w(TAG, "Cannot remove insets as the task token is not present.");
                return;
            }
            WindowContainerTransaction wct = new WindowContainerTransaction();
            wct.removeInsetsSource(mTaskViewTaskController.getTaskInfo().token,
                    mInsetsOwner, index, type);
            mSyncQueue.queue(wct);
        }
    };

    public RemoteCarTaskViewServerImpl(
            Context context,
            ShellTaskOrganizer organizer,
            SyncTransactionQueue syncQueue,
            CarTaskViewClient carTaskViewClient,
            CarSystemUIProxyImpl carSystemUIProxy,
            TaskViewTransitions taskViewTransitions,
            CarActivityManager carActivityManager) {
        mContext = context;
        mSyncQueue = syncQueue;
        mCarTaskViewClient = carTaskViewClient;
        mCarSystemUIProxy = carSystemUIProxy;
        mShellTaskOrganizer = organizer;
        mCarActivityManager = carActivityManager;

        mTaskViewTaskController =
                new TaskViewTaskController(context, organizer, taskViewTransitions, syncQueue);
        mTaskViewTaskController.setTaskViewBase(this);
    }

    public CarTaskViewHost getHostImpl() {
        return mHostImpl;
    }

    boolean hasLaunchRootTaskOnDisplay(int display) {
        return mRootTaskMediator != null && mRootTaskMediator.isLaunchRoot()
                && mRootTaskMediator.getDisplayId() == display;
    }

    @Override
    public Rect getCurrentBoundsOnScreen() {
        try {
            return mCarTaskViewClient.getCurrentBoundsOnScreen();
        } catch (DeadSystemRuntimeException ex) {
            Slog.w(TAG, "Failed to call getCurrentBoundsOnScreen() as TaskView client has "
                    + "already died. Host part will be released shortly.");
        }
        return new Rect(0, 0, 0, 0); // If it reaches here, it means that
        // the host side is already being released so it doesn't matter what is returned from here.
    }

    @Override
    public String toString() {
        ActivityManager.RunningTaskInfo taskInfo = mTaskViewTaskController.getTaskInfo();
        return "RemoteCarTaskViewServerImpl {"
                + "insets=" + mInsets
                + ", taskId=" + (taskInfo == null ? "null" : taskInfo.taskId)
                + ", taskInfo=" + (taskInfo == null ? "null" : taskInfo)
                + ", rootTaskMediator=" + (mRootTaskMediator == null ? "null"
                : mRootTaskMediator.toString())
                + "}";
    }

    @Override
    public void setResizeBgColor(SurfaceControl.Transaction transaction, int color) {
        try {
            mCarTaskViewClient.setResizeBackgroundColor(transaction, color);
        } catch (DeadSystemRuntimeException e) {
            Slog.w(TAG, "Failed to call setResizeBackgroundColor() as TaskView client has "
                    + "already died. Host part will be released shortly.");
        }
    }

    @Override
    public void onTaskAppeared(ActivityManager.RunningTaskInfo taskInfo, SurfaceControl leash) {
        applyAllInsets();
        try {
            mCarTaskViewClient.onTaskAppeared(taskInfo, leash);
        } catch (DeadSystemRuntimeException e) {
            Slog.w(TAG, "Failed to call onTaskAppeared() as TaskView client has already died, "
                    + "already died. Host part will be released shortly.");
        }
    }

    @Override
    public void onTaskInfoChanged(ActivityManager.RunningTaskInfo taskInfo) {
        try {
            mCarTaskViewClient.onTaskInfoChanged(taskInfo);
        } catch (DeadSystemRuntimeException e) {
            Slog.w(TAG, "Failed to call onTaskInfoChanged() as TaskView client has already died, "
                    + "already died. Host part will be released shortly.");
        }
    }

    @Override
    public void onTaskVanished(ActivityManager.RunningTaskInfo taskInfo) {
        try {
            mCarTaskViewClient.onTaskVanished(taskInfo);
        } catch (DeadSystemRuntimeException e) {
            Slog.w(TAG, "Failed to call onTaskVanished() as TaskView client has already died, "
                    + "already died. Host part will be released shortly.");
        }
    }

    private void applyAllInsets() {
        if (mInsets.size() == 0) {
            Slog.w(TAG, "Cannot apply null or empty insets");
            return;
        }
        if (mTaskViewTaskController.getTaskInfo() == null) {
            Slog.w(TAG, "Cannot apply insets as the task token is not present.");
            return;
        }
        WindowContainerTransaction wct = new WindowContainerTransaction();
        for (int i = 0; i < mInsets.size(); i++) {
            final int id = mInsets.keyAt(i);
            final Rect frame = mInsets.valueAt(i);
            wct.addInsetsSource(mTaskViewTaskController.getTaskInfo().token,
                    mInsetsOwner, InsetsSource.getIndex(id), InsetsSource.getType(id), frame,
                    null /* boundingRects */);
        }
        mSyncQueue.queue(wct);
    }
}
