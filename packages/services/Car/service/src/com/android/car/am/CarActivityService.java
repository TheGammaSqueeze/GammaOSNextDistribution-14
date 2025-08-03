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

package com.android.car.am;

import static android.Manifest.permission.INTERACT_ACROSS_USERS;
import static android.Manifest.permission.MANAGE_ACTIVITY_TASKS;
import static android.car.Car.PERMISSION_MANAGE_CAR_SYSTEM_UI;
import static android.car.Car.PERMISSION_REGISTER_CAR_SYSTEM_UI_PROXY;
import static android.car.content.pm.CarPackageManager.BLOCKING_INTENT_EXTRA_DISPLAY_ID;

import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.DUMP_INFO;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.app.TaskInfo;
import android.car.Car;
import android.car.app.CarActivityManager;
import android.car.app.ICarActivityService;
import android.car.app.ICarSystemUIProxy;
import android.car.app.ICarSystemUIProxyCallback;
import android.car.builtin.app.ActivityManagerHelper;
import android.car.builtin.app.TaskInfoHelper;
import android.car.builtin.os.UserManagerHelper;
import android.car.builtin.util.Slogf;
import android.car.builtin.view.SurfaceControlHelper;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Point;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.os.UserHandle;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Log;
import android.util.SparseArray;
import android.util.proto.ProtoOutputStream;
import android.view.Display;
import android.view.SurfaceControl;

import com.android.car.CarLog;
import com.android.car.CarServiceBase;
import com.android.car.CarServiceHelperWrapper;
import com.android.car.CarServiceUtils;
import com.android.car.R;
import com.android.car.SystemActivityMonitoringService;
import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;
import com.android.car.internal.util.IndentingPrintWriter;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.Preconditions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

/**
 * Service responsible for Activities in Car.
 */
public final class CarActivityService extends ICarActivityService.Stub
        implements CarServiceBase {

    private static final String TAG = CarLog.TAG_AM;
    private static final boolean DBG = Slogf.isLoggable(TAG, Log.DEBUG);

    private static final int MAX_RUNNING_TASKS_TO_GET = 100;
    private static final long MIRRORING_TOKEN_TIMEOUT_MS = 10 * 60 * 1000;  // 10 mins

    private final Context mContext;
    private final DisplayManager mDisplayManager;
    private final long mMirroringTokenTimeoutMs;

    private final Object mLock = new Object();

    // LinkedHashMap is used instead of SparseXXX because a predictable iteration order is needed.
    // The tasks here need be ordered as per their stack order. The stack order is maintained
    // using a combination of onTaskAppeared and onTaskInfoChanged callbacks.
    @GuardedBy("mLock")
    private final LinkedHashMap<Integer, ActivityManager.RunningTaskInfo> mTasks =
            new LinkedHashMap<>();
    @GuardedBy("mLock")
    private final SparseArray<SurfaceControl> mTaskToSurfaceMap = new SparseArray<>();

    @GuardedBy("mLock")
    private final ArrayMap<IBinder, IBinder.DeathRecipient> mMonitorTokens = new ArrayMap<>();

    @GuardedBy("mLock")
    private final ArraySet<MirroringToken> mMirroringTokens = new ArraySet<>();

    @GuardedBy("mLock")
    private ICarSystemUIProxy mCarSystemUIProxy;
    @GuardedBy("mLock")
    private final RemoteCallbackList<ICarSystemUIProxyCallback> mCarSystemUIProxyCallbacks =
            new RemoteCallbackList<ICarSystemUIProxyCallback>();

    private IBinder mCurrentMonitor;

    public interface ActivityLaunchListener {
        /**
         * Notify launch of activity.
         *
         * @param topTask Task information for what is currently launched.
         */
        void onActivityLaunch(TaskInfo topTask);
    }
    @GuardedBy("mLock")
    private final ArrayList<ActivityLaunchListener> mActivityLaunchListeners = new ArrayList<>();

    private final HandlerThread mMonitorHandlerThread = CarServiceUtils.getHandlerThread(
            SystemActivityMonitoringService.class.getSimpleName());
    private final Handler mHandler = new Handler(mMonitorHandlerThread.getLooper());

    public CarActivityService(Context context) {
        this(context, MIRRORING_TOKEN_TIMEOUT_MS);
    }

    @VisibleForTesting
    CarActivityService(Context context, long mirroringTokenTimeout) {
        mContext = context;
        mDisplayManager = context.getSystemService(DisplayManager.class);
        mMirroringTokenTimeoutMs = mirroringTokenTimeout;
    }

    @Override
    public void init() {}

    @Override
    public void release() {
        synchronized (mLock) {
            mActivityLaunchListeners.clear();
        }
    }

    @Override
    public int setPersistentActivity(ComponentName activity, int displayId, int featureId) throws
            RemoteException {
        ensurePermission(Car.PERMISSION_CONTROL_CAR_APP_LAUNCH);
        int caller = getCaller();
        if (caller != UserManagerHelper.USER_SYSTEM && caller != ActivityManager.getCurrentUser()) {
            return CarActivityManager.RESULT_INVALID_USER;
        }

        return CarServiceHelperWrapper.getInstance().setPersistentActivity(activity, displayId,
                featureId);
    }

    @Override
    public void setPersistentActivitiesOnRootTask(@NonNull List<ComponentName> activities,
            IBinder rootTaskToken) {
        ensurePermission(Car.PERMISSION_CONTROL_CAR_APP_LAUNCH);
        CarServiceHelperWrapper.getInstance().setPersistentActivitiesOnRootTask(activities,
                rootTaskToken);
    }

    @VisibleForTesting
    int getCaller() {  // Non static for mocking.
        return UserManagerHelper.getUserId(Binder.getCallingUid());
    }

    public void registerActivityLaunchListener(@NonNull ActivityLaunchListener listener) {
        synchronized (mLock) {
            mActivityLaunchListeners.add(listener);
        }
    }

    public void unregisterActivityLaunchListener(@NonNull ActivityLaunchListener listener) {
        synchronized (mLock) {
            mActivityLaunchListeners.remove(listener);
        }
    }

    @Override
    public void registerTaskMonitor(IBinder token) {
        if (DBG) Slogf.d(TAG, "registerTaskMonitor: %s", token);
        ensureManageActivityTasksPermission();

        IBinder.DeathRecipient deathRecipient = new IBinder.DeathRecipient() {
            @Override
            public void binderDied() {
                cleanUpMonitorToken(token);
            }
        };
        synchronized (mLock) {
            try {
                token.linkToDeath(deathRecipient, /* flags= */ 0);
            } catch (RemoteException e) {
                // 'token' owner might be dead already.
                Slogf.e(TAG, "failed to linkToDeath: %s", token);
                return;
            }
            mMonitorTokens.put(token, deathRecipient);
            mCurrentMonitor = token;
            // When new TaskOrganizer takes the control, it'll get the status of the whole tasks
            // in the system again. So drops the old status.
            mTasks.clear();
        }
    }

    private void ensurePermission(String permission) {
        if (mContext.checkCallingOrSelfPermission(permission)
                != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("requires permission " + permission);
        }
    }

    private void ensureManageActivityTasksPermission() {
        ensurePermission(MANAGE_ACTIVITY_TASKS);
    }

    private void cleanUpMonitorToken(IBinder token) {
        synchronized (mLock) {
            if (mCurrentMonitor == token) {
                mCurrentMonitor = null;
            }
            IBinder.DeathRecipient deathRecipient = mMonitorTokens.remove(token);
            if (deathRecipient != null) {
                token.unlinkToDeath(deathRecipient, /* flags= */ 0);
            }
        }
    }

    @Override
    public void onTaskAppeared(IBinder token,
            ActivityManager.RunningTaskInfo taskInfo, SurfaceControl leash) {
        if (DBG) {
            Slogf.d(TAG, "onTaskAppeared: %s, %s", token, TaskInfoHelper.toString(taskInfo));
        }
        ensureManageActivityTasksPermission();
        synchronized (mLock) {
            if (!isAllowedToUpdateLocked(token)) {
                return;
            }
            mTasks.put(taskInfo.taskId, taskInfo);
            if (leash != null) {
                mTaskToSurfaceMap.put(taskInfo.taskId, leash);
            }
        }
        if (TaskInfoHelper.isVisible(taskInfo)) {
            mHandler.post(() -> notifyActivityLaunch(taskInfo));
        }
    }

    private void notifyActivityLaunch(TaskInfo taskInfo) {
        synchronized (mLock) {
            for (int i = 0, size = mActivityLaunchListeners.size(); i < size; ++i) {
                mActivityLaunchListeners.get(i).onActivityLaunch(taskInfo);
            }
        }
    }

    @GuardedBy("mLock")
    private boolean isAllowedToUpdateLocked(IBinder token) {
        if (mCurrentMonitor != null && mCurrentMonitor == token) {
            return true;
        }
        // Fallback during no current Monitor exists.
        boolean allowed = (mCurrentMonitor == null && mMonitorTokens.containsKey(token));
        if (!allowed) {
            Slogf.w(TAG, "Report with the invalid token: %s", token);
        }
        return allowed;
    }

    @Override
    public void onTaskVanished(IBinder token, ActivityManager.RunningTaskInfo taskInfo) {
        if (DBG) {
            Slogf.d(TAG, "onTaskVanished: %s, %s", token, TaskInfoHelper.toString(taskInfo));
        }
        ensureManageActivityTasksPermission();
        synchronized (mLock) {
            if (!isAllowedToUpdateLocked(token)) {
                return;
            }
            mTasks.remove(taskInfo.taskId);
            mTaskToSurfaceMap.remove(taskInfo.taskId);
        }
    }

    @Override
    public void onTaskInfoChanged(IBinder token, ActivityManager.RunningTaskInfo taskInfo) {
        if (DBG) {
            Slogf.d(TAG, "onTaskInfoChanged: %s, %s", token, TaskInfoHelper.toString(taskInfo));
        }
        ensureManageActivityTasksPermission();
        synchronized (mLock) {
            if (!isAllowedToUpdateLocked(token)) {
                return;
            }
            // The key should be removed and added again so that it jumps to the front of the
            // LinkedHashMap.
            TaskInfo oldTaskInfo = mTasks.remove(taskInfo.taskId);
            mTasks.put(taskInfo.taskId, taskInfo);
            if ((oldTaskInfo == null || !TaskInfoHelper.isVisible(oldTaskInfo)
                    || !Objects.equals(oldTaskInfo.topActivity, taskInfo.topActivity))
                    && TaskInfoHelper.isVisible(taskInfo)) {
                mHandler.post(() -> notifyActivityLaunch(taskInfo));
            }
        }
    }

    @Override
    public void unregisterTaskMonitor(IBinder token) {
        if (DBG) Slogf.d(TAG, "unregisterTaskMonitor: %s", token);
        ensureManageActivityTasksPermission();
        cleanUpMonitorToken(token);
    }

    /**
     * Returns all the visible tasks in the given display. The order is not guaranteed.
     */
    @Override
    public List<ActivityManager.RunningTaskInfo> getVisibleTasks(int displayId) {
        ensureManageActivityTasksPermission();
        return getVisibleTasksInternal(displayId);
    }

    public List<ActivityManager.RunningTaskInfo> getVisibleTasksInternal() {
        return getVisibleTasksInternal(Display.INVALID_DISPLAY);
    }

    /** Car service internal version without the permission enforcement. */
    public List<ActivityManager.RunningTaskInfo> getVisibleTasksInternal(int displayId) {
        ArrayList<ActivityManager.RunningTaskInfo> tasksToReturn = new ArrayList<>();
        synchronized (mLock) {
            for (ActivityManager.RunningTaskInfo taskInfo : mTasks.values()) {
                // Activities launched in the private display or non-focusable display can't be
                // focusable. So we just monitor all visible Activities/Tasks.
                if (TaskInfoHelper.isVisible(taskInfo)
                        && (displayId == Display.INVALID_DISPLAY
                                || displayId == TaskInfoHelper.getDisplayId(taskInfo))) {
                    tasksToReturn.add(taskInfo);
                }
            }
        }
        // Reverse the order so that the resultant order is top to bottom.
        Collections.reverse(tasksToReturn);
        return tasksToReturn;
    }

    @Override
    public void startUserPickerOnDisplay(int displayId) {
        CarServiceUtils.assertAnyPermission(mContext, INTERACT_ACROSS_USERS);
        Preconditions.checkArgument(displayId != Display.INVALID_DISPLAY, "Invalid display");
        String userPickerName = mContext.getResources().getString(
                R.string.config_userPickerActivity);
        if (userPickerName.isEmpty()) {
            Slogf.w(TAG, "Cannot launch user picker to display %d, component not specified",
                    displayId);
            return;
        }
        CarServiceUtils.startUserPickerOnDisplay(mContext, displayId, userPickerName);
    }

    private abstract class MirroringToken extends Binder {
        private MirroringToken() {
            CarActivityService.this.registerMirroringToken(this);
        }

        protected abstract SurfaceControl getMirroredSurface(Rect outBounds);
    };

    private void registerMirroringToken(MirroringToken token) {
        synchronized (mLock) {
            mMirroringTokens.add(token);
        }
        mHandler.postDelayed(() -> cleanUpMirroringToken(token), mMirroringTokenTimeoutMs);
    }

    private void cleanUpMirroringToken(MirroringToken token) {
        synchronized (mLock) {
            mMirroringTokens.remove(token);
        }
    }

    @GuardedBy("mLock")
    private void assertMirroringTokenIsValidLocked(MirroringToken token) {
        if (!mMirroringTokens.contains(token)) {
            throw new IllegalArgumentException("Invalid token: " + token);
        }
    }

    private final class TaskMirroringToken extends MirroringToken {
        private final int mTaskId;
        private TaskMirroringToken(int taskId) {
            super();
            mTaskId = taskId;
        }

        @Override
        @GuardedBy("CarActivityService.this.mLock")
        protected SurfaceControl getMirroredSurface(Rect outBounds) {
            TaskInfo taskInfo = mTasks.get(mTaskId);
            SurfaceControl taskSurface = mTaskToSurfaceMap.get(mTaskId);
            if (taskInfo == null || taskSurface == null || !taskInfo.isVisible()) {
                Slogf.e(TAG, "TaskMirroringToken#getMirroredSurface: no task=%s", taskInfo);
                return null;
            }
            outBounds.set(TaskInfoHelper.getBounds(taskInfo));
            return SurfaceControlHelper.mirrorSurface(taskSurface);
        }

        @Override
        public String toString() {
            return TaskMirroringToken.class.getSimpleName() + "[taskid=" + mTaskId + "]";
        }
    };

    private final class DisplayMirroringToken extends MirroringToken {
        private final int mDisplayId;
        private DisplayMirroringToken(int displayId) {
            super();
            mDisplayId = displayId;
        }

        @Override
        protected SurfaceControl getMirroredSurface(Rect outBounds) {
            Display display = mDisplayManager.getDisplay(mDisplayId);
            Point point = new Point();
            display.getRealSize(point);
            outBounds.set(0, 0, point.x, point.y);
            return SurfaceControlHelper.mirrorDisplay(mDisplayId);
        }

        @Override
        public String toString() {
            return DisplayMirroringToken.class.getSimpleName() + "[displayId=" + mDisplayId + "]";
        }
    };

    @Override
    public IBinder createTaskMirroringToken(int taskId) {
        ensureManageActivityTasksPermission();
        synchronized (mLock) {
            if (!mTaskToSurfaceMap.contains(taskId)) {
                throw new IllegalArgumentException("Non-existent Task#" + taskId);
            }
        }
        return new TaskMirroringToken(taskId);
    }

    @Override
    public IBinder createDisplayMirroringToken(int displayId) {
        ensurePermission(Car.PERMISSION_MIRROR_DISPLAY);
        return new DisplayMirroringToken(displayId);
    }

    @Override
    @Nullable
    public SurfaceControl getMirroredSurface(IBinder token, Rect outBounds) {
        ensurePermission(Car.PERMISSION_ACCESS_MIRRORRED_SURFACE);
        MirroringToken mirroringToken;
        try {
            mirroringToken = (MirroringToken) token;
        } catch (ClassCastException e) {
            throw new IllegalArgumentException("Bad token");
        }
        synchronized (mLock) {
            assertMirroringTokenIsValidLocked(mirroringToken);
            return mirroringToken.getMirroredSurface(outBounds);
        }
    }

    @Override
    public void registerCarSystemUIProxy(ICarSystemUIProxy carSystemUIProxy) {
        if (DBG) {
            Slogf.d(TAG, "registerCarSystemUIProxy %s", carSystemUIProxy.toString());
        }
        ensurePermission(PERMISSION_REGISTER_CAR_SYSTEM_UI_PROXY);
        synchronized (mLock) {
            if (mCarSystemUIProxy != null) {
                throw new UnsupportedOperationException("Car system UI proxy is already "
                        + "registered");
            }

            mCarSystemUIProxy = carSystemUIProxy;
            try {
                mCarSystemUIProxy.asBinder().linkToDeath(new IBinder.DeathRecipient(){
                    @Override
                    public void binderDied() {
                        synchronized (mLock) {
                            Slogf.d(TAG, "CarSystemUIProxy died %s",
                                        mCarSystemUIProxy.toString());
                            mCarSystemUIProxy.asBinder().unlinkToDeath(this, /* flags= */ 0);
                            mCarSystemUIProxy = null;
                        }
                    }
                }, /* flags= */0);
            } catch (RemoteException remoteException) {
                mCarSystemUIProxy = null;
                throw new IllegalStateException("Linking to binder death failed for "
                        + "ICarSystemUIProxy, the System UI might already died", remoteException);
            }

            if (DBG) {
                Slogf.d(TAG, "CarSystemUIProxy registered.");
            }

            int numCallbacks = mCarSystemUIProxyCallbacks.beginBroadcast();
            if (DBG) {
                Slogf.d(TAG, "Broadcasting CarSystemUIProxy connected to %d callbacks",
                        numCallbacks);
            }
            for (int i = 0; i < numCallbacks; i++) {
                try {
                    mCarSystemUIProxyCallbacks.getBroadcastItem(i).onConnected(
                            mCarSystemUIProxy);
                } catch (RemoteException remoteException) {
                    Slogf.e(TAG, "Error dispatching onConnected", remoteException);
                }
            }
            mCarSystemUIProxyCallbacks.finishBroadcast();
        }
    }

    @Override
    public boolean isCarSystemUIProxyRegistered() {
        synchronized (mLock) {
            return mCarSystemUIProxy != null;
        }
    }

    @Override
    public void addCarSystemUIProxyCallback(ICarSystemUIProxyCallback callback) {
        if (DBG) {
            Slogf.d(TAG, "addCarSystemUIProxyCallback %s", callback.toString());
        }
        ensurePermission(PERMISSION_MANAGE_CAR_SYSTEM_UI);
        synchronized (mLock) {
            boolean alreadyExists = mCarSystemUIProxyCallbacks.unregister(callback);
            mCarSystemUIProxyCallbacks.register(callback);

            if (alreadyExists) {
                // Do not trigger onConnected() if the callback already exists because it is either
                // already called or will be called when the mCarSystemUIProxy is registered.
                Slogf.d(TAG, "Callback exists already, skip calling onConnected()");
                return;
            }

            // Trigger onConnected() on the callback.
            if (mCarSystemUIProxy == null) {
                if (DBG) {
                    Slogf.d(TAG, "Callback stored locally, car system ui proxy not "
                            + "registered.");
                }
                return;
            }
            try {
                callback.onConnected(mCarSystemUIProxy);
            } catch (RemoteException remoteException) {
                Slogf.e(TAG, "Error when dispatching onConnected", remoteException);
            }
        }
    }

    @Override
    public void removeCarSystemUIProxyCallback(ICarSystemUIProxyCallback callback) {
        if (DBG) {
            Slogf.d(TAG, "removeCarSystemUIProxyCallback %s", callback.toString());
        }
        ensurePermission(PERMISSION_MANAGE_CAR_SYSTEM_UI);
        synchronized (mLock) {
            mCarSystemUIProxyCallbacks.unregister(callback);
        }
    }

    /**
     * Attempts to restart a task.
     *
     * <p>Restarts a task by removing the task and sending an empty intent with flag
     * {@link Intent#FLAG_ACTIVITY_NEW_TASK} to its root activity. If the task does not exist, do
     * nothing.
     *
     * @param taskId id of task to be restarted.
     */
    public void restartTask(int taskId) {
        TaskInfo task;
        synchronized (mLock) {
            task = mTasks.get(taskId);
        }
        if (task == null) {
            Slogf.e(CarLog.TAG_AM, "Could not find root activity with task id " + taskId);
            return;
        }

        Intent intent = (Intent) task.baseIntent.clone();
        // Remove the task the root activity is running in and start it in a new task.
        // This effectively leads to restarting of the root activity and removal all the other
        // activities in the task.
        // FLAG_ACTIVITY_CLEAR_TASK was being used earlier, but it has the side effect where the
        // last activity in the existing task is visible for a moment until the root activity is
        // started again.
        ActivityManagerHelper.removeTask(taskId);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        int userId = TaskInfoHelper.getUserId(task);
        if (Slogf.isLoggable(CarLog.TAG_AM, Log.INFO)) {
            Slogf.i(CarLog.TAG_AM, "restarting root activity with user id " + userId);
        }
        mContext.startActivityAsUser(intent, UserHandle.of(userId));
    }

    public TaskInfo getTaskInfoForTopActivity(ComponentName activity) {
        synchronized (mLock) {
            for (ActivityManager.RunningTaskInfo info : mTasks.values()) {
                if (activity.equals(info.topActivity)) {
                    return info;
                }
            }
        }
        return null;
    }

    /**
     * Block the current task: Launch new activity with given Intent and finish the current task.
     *
     * @param currentTask task to finish
     * @param newActivityIntent Intent for new Activity
     */
    public void blockActivity(TaskInfo currentTask, Intent newActivityIntent) {
        mHandler.post(() -> handleBlockActivity(currentTask, newActivityIntent));
    }

    /**
     * block the current task with the provided new activity.
     */
    private void handleBlockActivity(TaskInfo currentTask, Intent newActivityIntent) {
        int displayId = newActivityIntent.getIntExtra(BLOCKING_INTENT_EXTRA_DISPLAY_ID,
                Display.DEFAULT_DISPLAY);
        if (Slogf.isLoggable(CarLog.TAG_AM, Log.DEBUG)) {
            Slogf.d(CarLog.TAG_AM, "Launching blocking activity on display: " + displayId);
        }

        ActivityOptions options = ActivityOptions.makeBasic();
        options.setLaunchDisplayId(displayId);
        // Starts ABA as User 0 consistenly since the target apps can be any users (User 0 -
        // UserPicker, Driver/Passegners - general NDO apps) and launching ABA as passengers
        // have some issue (b/294447050).
        mContext.startActivity(newActivityIntent, options.toBundle());
        // Now make stack with new activity focused.
        findTaskAndGrantFocus(newActivityIntent.getComponent());
    }

    private void findTaskAndGrantFocus(ComponentName activity) {
        TaskInfo taskInfo = getTaskInfoForTopActivity(activity);
        if (taskInfo != null) {
            ActivityManagerHelper.setFocusedTask(taskInfo.taskId);
            return;
        }
        Slogf.i(CarLog.TAG_AM, "cannot give focus, cannot find Activity:" + activity);
    }

    @Override
    public void moveRootTaskToDisplay(int taskId, int displayId) {
        ensurePermission(Car.PERMISSION_CONTROL_CAR_APP_LAUNCH);
        // Calls moveRootTaskToDisplay() with the system uid.
        long identity = Binder.clearCallingIdentity();
        try {
            ActivityManagerHelper.moveRootTaskToDisplay(taskId, displayId);
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    @Override
    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    public void dump(IndentingPrintWriter writer) {
        writer.println("*CarActivityService*");
        synchronized (mLock) {
            writer.println(" CarSystemUIProxy registered:");
            writer.println(" " + (mCarSystemUIProxy != null));
            writer.println(" Tasks:");
            for (ActivityManager.RunningTaskInfo taskInfo : mTasks.values()) {
                writer.println("  " + TaskInfoHelper.toString(taskInfo));
            }
            writer.println(" Surfaces: " + mTaskToSurfaceMap.toString());
            writer.println(" ActivityLaunchListeners: " + mActivityLaunchListeners.toString());
        }
    }

    @Override
    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    public void dumpProto(ProtoOutputStream proto) {}
}
