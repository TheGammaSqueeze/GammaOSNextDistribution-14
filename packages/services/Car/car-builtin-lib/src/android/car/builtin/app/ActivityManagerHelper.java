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

package android.car.builtin.app;

import android.Manifest;
import android.annotation.NonNull;
import android.annotation.RequiresPermission;
import android.annotation.SystemApi;
import android.annotation.UserIdInt;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.app.ActivityTaskManager;
import android.app.ActivityTaskManager.RootTaskInfo;
import android.app.IActivityManager;
import android.app.IProcessObserver;
import android.car.builtin.util.Slogf;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;

import java.util.List;
import java.util.concurrent.Callable;

/**
 * Provide access to {@code android.app.IActivityManager} calls.
 * @hide
 */
@SystemApi(client = SystemApi.Client.MODULE_LIBRARIES)
public final class ActivityManagerHelper {

    /** Invalid task ID. */
    public static final int INVALID_TASK_ID = ActivityTaskManager.INVALID_TASK_ID;

    private static final String TAG = "CAR.AM";  // CarLog.TAG_AM

    // Lazy initialization holder class idiom for static fields; See go/ej3e-83 for the detail.
    private static class ActivityManagerHolder {
        static final IActivityManager sAm = ActivityManager.getService();
    }

    private static IActivityManager getActivityManager() {
        return ActivityManagerHolder.sAm;
    }

    private ActivityManagerHelper() {
        throw new UnsupportedOperationException("contains only static members");
    }

    /**
     * See {@code android.app.IActivityManager.startUserInBackground}.
     *
     * @throws IllegalStateException if ActivityManager binder throws RemoteException
     */
    public static boolean startUserInBackground(@UserIdInt int userId) {
        return runRemotely(() -> getActivityManager().startUserInBackground(userId),
                "error while startUserInBackground %d", userId);
    }

    /**
     * See {@code android.app.IActivityManager.startUserInBackgroundVisibleOnDisplay}.
     *
     * @throws IllegalStateException if ActivityManager binder throws RemoteException
     */
    public static boolean startUserInBackgroundVisibleOnDisplay(@UserIdInt int userId,
            int displayId) {
        return runRemotely(() -> getActivityManager().startUserInBackgroundVisibleOnDisplay(
                        userId, displayId, /* unlockProgressListener= */ null),
                "error while startUserInBackgroundVisibleOnDisplay userId:%d displayId:%d",
                userId, displayId);
    }

    /**
     * See {@code android.app.IActivityManager.startUserInForegroundWithListener}.
     *
     * @throws IllegalStateException if ActivityManager binder throws RemoteException
     */
    public static boolean startUserInForeground(@UserIdInt int userId) {
        return runRemotely(
                () -> getActivityManager().startUserInForegroundWithListener(
                        userId, /* listener= */ null),
                "error while startUserInForeground %d", userId);
    }

    /**
     * See {@code android.app.IActivityManager.stopUser}.
     *
     * @throws IllegalStateException if ActivityManager binder throws RemoteException
     */
    public static int stopUser(@UserIdInt int userId, boolean force) {
        return runRemotely(
                () -> getActivityManager().stopUser(userId, force, /* callback= */ null),
                "error while stopUser userId:%d force:%b", userId, force);
    }

    /**
     * See {@code android.app.IActivityManager.stopUserWithDelayedLocking}.
     *
     * @throws IllegalStateException if ActivityManager binder throws RemoteException
     */
    public static int stopUserWithDelayedLocking(@UserIdInt int userId, boolean force) {
        return runRemotely(
                () -> getActivityManager().stopUserWithDelayedLocking(
                        userId, force, /* callback= */ null),
                "error while stopUserWithDelayedLocking userId:%d force:%b", userId, force);
    }

    /**
     * Check {@code android.app.IActivityManager.unlockUser}.
     *
     * @throws IllegalStateException if ActivityManager binder throws RemoteException
     */
    public static boolean unlockUser(@UserIdInt int userId) {
        return runRemotely(() -> getActivityManager().unlockUser2(userId, /* listener= */ null),
                "error while unlocking user %d", userId);
    }

    /**
     * Stops all task for the user.
     *
     * @throws IllegalStateException if ActivityManager binder throws RemoteException
     */
    public static void stopAllTasksForUser(@UserIdInt int userId) {
        try {
            IActivityManager am = getActivityManager();
            for (RootTaskInfo info : am.getAllRootTaskInfos()) {
                for (int i = 0; i < info.childTaskIds.length; i++) {
                    if (info.childTaskUserIds[i] == userId) {
                        int taskId = info.childTaskIds[i];
                        if (!am.removeTask(taskId)) {
                            Slogf.w(TAG, "could not remove task " + taskId);
                        }
                    }
                }
            }
        } catch (RemoteException e) {
            throw logAndReThrow(e, "could not get stack info for user %d", userId);
        }
    }

    /**
     * Creates an ActivityOptions from the Bundle generated from ActivityOptions.
     */
    @NonNull
    public static ActivityOptions createActivityOptions(@NonNull Bundle bOptions) {
        return new ActivityOptions(bOptions);
    }

    private static <T> T runRemotely(Callable<T> callable, String format, Object...args) {
        try {
            return callable.call();
        } catch (Exception e) {
            throw logAndReThrow(e, format, args);
        }
    }

    @SuppressWarnings("AnnotateFormatMethod")
    private static RuntimeException logAndReThrow(Exception e, String format, Object...args) {
        String msg = String.format(format, args);
        Slogf.e(TAG, msg, e);
        return new IllegalStateException(msg, e);
    }

    /**
     * Makes the root task of the given taskId focused.
     */
    public static void setFocusedRootTask(int taskId) {
        try {
            getActivityManager().setFocusedRootTask(taskId);
        } catch (RemoteException e) {
            Slogf.e(TAG, "Failed to setFocusedRootTask", e);
        }
    }

    /**
     * Makes the task of the given taskId focused.
     */
    public static void setFocusedTask(int taskId) {
        try {
            ActivityTaskManager.getService().setFocusedTask(taskId);
        } catch (RemoteException e) {
            Slogf.e(TAG, "Failed to setFocusedTask", e);
        }
    }

    /**
     * Removes the given task.
     */
    public static boolean removeTask(int taskId) {
        try {
            return getActivityManager().removeTask(taskId);
        } catch (RemoteException e) {
            Slogf.e(TAG, "Failed to removeTask", e);
        }
        return false;
    }

    /**
     * Callback to monitor Processes in the system
     */
    public abstract static class ProcessObserverCallback {
        /** Called when the foreground Activities are changed. */
        public void onForegroundActivitiesChanged(int pid, int uid, boolean foregroundActivities) {
        }
        /** Called when the Process is died. */
        public void onProcessDied(int pid, int uid) {}

        final IProcessObserver.Stub mIProcessObserver = new IProcessObserver.Stub() {
            @Override
            public void onForegroundActivitiesChanged(
                    int pid, int uid, boolean foregroundActivities) throws RemoteException {
                ProcessObserverCallback.this.onForegroundActivitiesChanged(
                        pid, uid, foregroundActivities);
            }

            @Override
            public void onForegroundServicesChanged(int pid, int uid, int fgServiceTypes)
                    throws RemoteException {
                // Not used
            }

            @Override
            public void onProcessStarted(int pid, int processUid, int packageUid,
                    String packageName, String processName) {
                // Not used
            }

            @Override
            public void onProcessDied(int pid, int uid) throws RemoteException {
                ProcessObserverCallback.this.onProcessDied(pid, uid);
            }
        };
    }

    /**
     * Registers a callback to be invoked when the process states are changed.
     * @param callback a callback to register
     */
    public static void registerProcessObserverCallback(ProcessObserverCallback callback) {
        try {
            getActivityManager().registerProcessObserver(callback.mIProcessObserver);
        } catch (RemoteException e) {
            Slogf.e(TAG, "Failed to register ProcessObserver", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Unregisters the given callback.
     * @param callback a callback to unregister
     */
    public static void unregisterProcessObserverCallback(ProcessObserverCallback callback) {
        try {
            getActivityManager().unregisterProcessObserver(callback.mIProcessObserver);
        } catch (RemoteException e) {
            Slogf.e(TAG, "Failed to unregister listener", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Same as {@link ActivityManager#checkComponentPermission(String, int, int, boolean).
     */
    public static int checkComponentPermission(@NonNull String permission, int uid, int owningUid,
            boolean exported) {
        return ActivityManager.checkComponentPermission(permission, uid, owningUid, exported);
    }

    /** See {@link android.app.ActivityTaskManager#getTasks(int, boolean, boolean, int)} */
    public static List<ActivityManager.RunningTaskInfo> getTasks(int maxNum,
            boolean filterOnlyVisibleRecents, boolean keepIntentExtra, int displayId) {
        return ActivityTaskManager.getInstance().getTasks(maxNum, filterOnlyVisibleRecents,
                keepIntentExtra, displayId);
    }

    /**
     * Same as {@link ActivityManager#killAllBackgroundProcesses()}
     */
    public static void killAllBackgroundProcesses() {
        try {
            getActivityManager().killAllBackgroundProcesses();
        } catch (RemoteException e) {
            Slogf.e(TAG, "Failed to kill background apps", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Same as {@link ActivityManager#killUid()}
     */
    public static void killUid(int appId, int userId, String reason) {
        try {
            getActivityManager().killUid(appId, userId, reason);
        } catch (RemoteException e) {
            Slogf.e(TAG, "Failed to call app : %d , userId: %d, kill reason: %s", appId, userId,
                    reason);
            throw new RuntimeException(e);
        }
    }

    /** See {@link Activity#getActivityToken()} */
    public static IBinder getActivityToken(Activity activity) {
        return activity.getActivityToken();
    }

    /** See {@link Activity#isVisibleForAutofill()} */
    public static boolean isVisible(Activity activity) {
        return activity.isVisibleForAutofill();
    }

    /**
     * Moves the given {@code RootTask} to the specified {@code Display}.
     *
     * @param taskId    the id of the target {@code RootTask} to move
     * @param displayId the displayId to move the {@code RootTask} to
     */
    @RequiresPermission(Manifest.permission.INTERNAL_SYSTEM_WINDOW)
    public static void moveRootTaskToDisplay(int taskId, int displayId) {
        try {
            ActivityTaskManager.getService().moveRootTaskToDisplay(taskId, displayId);
        } catch (RemoteException e) {
            Slogf.e(TAG, "Error moving task %d to display %d", e, taskId, displayId);
            throw new RuntimeException(e);
        }
    }
}
