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
package com.android.systemui.car.distantdisplay.common;

import static android.car.drivingstate.CarUxRestrictions.UX_RESTRICTIONS_NO_VIDEO;

import static com.android.systemui.car.distantdisplay.common.DistantDisplayForegroundTaskMap.TaskData;

import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.app.ActivityTaskManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.Log;

import androidx.annotation.Nullable;

import com.android.car.ui.utils.CarUxRestrictionsUtil;
import com.android.systemui.R;
import com.android.systemui.broadcast.BroadcastDispatcher;
import com.android.systemui.car.distantdisplay.activity.MoveTaskReceiver;
import com.android.systemui.car.distantdisplay.activity.RootTaskViewWallpaperActivity;
import com.android.systemui.car.distantdisplay.util.AppCategoryDetector;
import com.android.systemui.dagger.SysUISingleton;
import com.android.systemui.settings.UserTracker;
import com.android.systemui.shared.system.TaskStackChangeListener;
import com.android.systemui.shared.system.TaskStackChangeListeners;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.inject.Inject;

/**
 * TaskView Controller that manages the implementation details for task views on a distant display.
 * <p>
 * This is also a common class used between two different technical implementation where in one
 * TaskViews are hosted in a window created by systemUI Vs in another where TaskView are created
 * via an activity.
 */
@SysUISingleton
public class TaskViewController {
    public static final String TAG = TaskViewController.class.getSimpleName();
    private static final boolean DEBUG = Build.IS_ENG || Build.IS_USERDEBUG;
    private static final int DEFAULT_DISPLAY_ID = 0;
    private static final int INVALID_TASK_ID = -1;

    private final Context mContext;
    private final BroadcastDispatcher mBroadcastDispatcher;
    private final UserTracker mUserTracker;
    private final UserManager mUserManager;
    private final DisplayManager mDisplayManager;
    private final List<Callback> mCallbacks = new ArrayList<>();
    private boolean mInitialized;
    private int mDistantDisplayId;
    private final DistantDisplayForegroundTaskMap mForegroundTasks =
            new DistantDisplayForegroundTaskMap();
    private int mDistantDisplayRootWallpaperTaskId = INVALID_TASK_ID;
    private MoveTaskReceiver mMoveTaskReceiver;

    private final CarUxRestrictionsUtil.OnUxRestrictionsChangedListener
            mOnUxRestrictionsChangedListener = carUxRestrictions -> {
        int uxr = carUxRestrictions.getActiveRestrictions();
        if (isVideoRestricted(uxr)) {
            // pull back the video from DD
            changeDisplayForTask(MoveTaskReceiver.MOVE_FROM_DISTANT_DISPLAY);
        }
    };

    private final BroadcastReceiver mUserEventReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Objects.equals(intent.getAction(), Intent.ACTION_USER_UNLOCKED)) {
                launchActivity(mDistantDisplayId,
                        RootTaskViewWallpaperActivity.createIntent(mContext));
            }
        }
    };

    private final TaskStackChangeListener mTaskStackChangeLister = new TaskStackChangeListener() {
        @Override
        public void onTaskMovedToFront(ActivityManager.RunningTaskInfo taskInfo) {
            logIfDebuggable("onTaskMovedToFront: displayId: " + taskInfo.displayId + ", " + taskInfo
                    + " token: " + taskInfo.token);
            mForegroundTasks.put(taskInfo.taskId, taskInfo.displayId, taskInfo.baseIntent);
            if (taskInfo.displayId == DEFAULT_DISPLAY_ID) {
                notifyListeners(DEFAULT_DISPLAY_ID);
            } else if (taskInfo.displayId == mDistantDisplayId) {
                if (getPackageNameFromBaseIntent(taskInfo.baseIntent).equals(
                        mContext.getPackageName())) {
                    mDistantDisplayRootWallpaperTaskId = taskInfo.taskId;
                }
                notifyListeners(mDistantDisplayId);
            }
        }

        @Override
        public void onTaskDisplayChanged(int taskId, int newDisplayId) {
            logIfDebuggable(
                    "onTaskDisplayChanged: taskId: " + taskId + " newDisplayId: " + newDisplayId);
            TaskData oldData = mForegroundTasks.get(taskId);
            if (oldData != null) {
                // If a task has not changed displays, do nothing. If it has truly been moved to
                // the top of the same display, it should be handled by onTaskMovedToFront
                if (oldData.mDisplayId == newDisplayId) return;
                mForegroundTasks.remove(taskId);
                mForegroundTasks.put(taskId, newDisplayId, oldData.mBaseIntent);
            } else {
                mForegroundTasks.put(taskId, newDisplayId, null);
            }

            if (newDisplayId == DEFAULT_DISPLAY_ID || (oldData != null
                    && oldData.mDisplayId == DEFAULT_DISPLAY_ID)) {
                // Task on the default display has changed (by either a new task being added or an
                // old task being moved away) - notify listeners
                notifyListeners(DEFAULT_DISPLAY_ID);
            }
            if (newDisplayId == mDistantDisplayId || (oldData != null
                    && oldData.mDisplayId == mDistantDisplayId)) {
                // Task on the distant display has changed  (by either a new task being added or an
                // old task being moved away) - notify listeners
                notifyListeners(mDistantDisplayId);
            }
        }
    };

    @Inject
    public TaskViewController(Context context, BroadcastDispatcher broadcastDispatcher,
            UserTracker userTracker) {
        mContext = context;
        mBroadcastDispatcher = broadcastDispatcher;
        mUserTracker = userTracker;
        mUserManager = context.getSystemService(UserManager.class);
        mDisplayManager = context.getSystemService(DisplayManager.class);
        mDistantDisplayId = mContext.getResources().getInteger(R.integer.config_distantDisplayId);
    }

    /**
     * Initializes the listeners and initial wallpaper task for a particular distant display id.
     * Can only be called once - if reinitialization is required, {@link #unregister} must be called
     * before initialize is called again.
     */
    public void initialize(int distantDisplayId) {
        if (mInitialized) return;

        mInitialized = true;
        mDistantDisplayId = distantDisplayId;

        TaskStackChangeListeners.getInstance().registerTaskStackListener(mTaskStackChangeLister);
        CarUxRestrictionsUtil carUxRestrictionsUtil = CarUxRestrictionsUtil.getInstance(mContext);
        carUxRestrictionsUtil.register(mOnUxRestrictionsChangedListener);

        if (DEBUG) {
            Log.i(TAG, "Setup adb debugging : ");
            setupDebuggingThroughAdb();

            if (mUserManager.isUserUnlocked()) {
                // User is already unlocked - immediately launch wallpaper activity
                launchActivity(mDistantDisplayId,
                        RootTaskViewWallpaperActivity.createIntent(mContext));
            }
            // Register user unlock receiver for future user switches and unlocks
            mBroadcastDispatcher.registerReceiver(mUserEventReceiver,
                    new IntentFilter(Intent.ACTION_USER_UNLOCKED),
                    /* executor= */ null, UserHandle.ALL);
        }
    }

    /** Unregister listeners - call before re-initialization. */
    public void unregister() {
        if (!mInitialized) return;

        TaskStackChangeListeners.getInstance().unregisterTaskStackListener(mTaskStackChangeLister);
        CarUxRestrictionsUtil carUxRestrictionsUtil = CarUxRestrictionsUtil.getInstance(mContext);
        carUxRestrictionsUtil.unregister(mOnUxRestrictionsChangedListener);
        clearListeners();

        if (DEBUG) {
            mContext.unregisterReceiver(mMoveTaskReceiver);
            mMoveTaskReceiver = null;
            mBroadcastDispatcher.unregisterReceiver(mUserEventReceiver);
        }
    }

    /** Move task from default display to distant display. */
    public void moveTaskToDistantDisplay() {
        if (!mInitialized) return;
        changeDisplayForTask(MoveTaskReceiver.MOVE_TO_DISTANT_DISPLAY);
    }

    /** Move task from distant display to default display. */
    public void moveTaskFromDistantDisplay() {
        if (!mInitialized) return;
        changeDisplayForTask(MoveTaskReceiver.MOVE_FROM_DISTANT_DISPLAY);
    }

    /** Add a callback to listen to task changes on the default and distant displays. */
    public void addCallback(Callback callback) {
        synchronized (mCallbacks) {
            mCallbacks.add(callback);
        }
    }

    /** Remove callback for task changes on the default and distant displays. */
    public void removeCallback(Callback callback) {
        synchronized (mCallbacks) {
            mCallbacks.remove(callback);
        }
    }

    private void setupDebuggingThroughAdb() {
        IntentFilter filter = new IntentFilter(MoveTaskReceiver.MOVE_ACTION);
        mMoveTaskReceiver = new MoveTaskReceiver();
        mMoveTaskReceiver.registerOnChangeDisplayForTask(this::changeDisplayForTask);
        mContext.registerReceiverAsUser(mMoveTaskReceiver,
                mUserTracker.getUserHandle(),
                filter, null, null,
                Context.RECEIVER_EXPORTED);
    }

    private boolean isVideoRestricted(int uxr) {
        return ((uxr & UX_RESTRICTIONS_NO_VIDEO) == UX_RESTRICTIONS_NO_VIDEO);
    }

    private void changeDisplayForTask(String movement) {
        Log.i(TAG, "Handling movement command : " + movement);
        if (movement.equals(MoveTaskReceiver.MOVE_FROM_DISTANT_DISPLAY)
                && !mForegroundTasks.isEmpty()) {
            TaskData data = mForegroundTasks.getTopTaskOnDisplay(
                    mDistantDisplayId);
            if (data == null || data.mTaskId == mDistantDisplayRootWallpaperTaskId) return;
            moveTaskToDisplay(data.mTaskId, DEFAULT_DISPLAY_ID);
        } else if (movement.equals(MoveTaskReceiver.MOVE_TO_DISTANT_DISPLAY)
                && !mForegroundTasks.isEmpty()) {
            TaskData data = mForegroundTasks.getTopTaskOnDisplay(
                    DEFAULT_DISPLAY_ID);
            if (data == null) return;
            String pkgName = getPackageNameFromBaseIntent(data.mBaseIntent);
            if (pkgName != null && isVideoApp(pkgName)) {
                moveTaskToDisplay(data.mTaskId, mDistantDisplayId);
            }
        }
    }

    private void moveTaskToDisplay(int taskId, int displayId) {
        try {
            ActivityTaskManager.getService().moveRootTaskToDisplay(taskId, displayId);
        } catch (Exception e) {
            Log.e(TAG, "Error moving task " + taskId + " to display " + displayId, e);
        }
    }

    private boolean isVideoApp(@Nullable String packageName) {
        if (packageName == null) {
            Log.w(TAG, "package name is null");
            return false;
        }

        Context userContext = mContext.createContextAsUser(
                mUserTracker.getUserHandle(), /* flags= */ 0);
        return AppCategoryDetector.isVideoApp(userContext.getPackageManager(),
                packageName);
    }

    /**
     * Provides a DisplayManager.
     * This method will be called from the SurfaceHolderCallback to create a virtual display.
     */
    public DisplayManager getDisplayManager() {
        return mDisplayManager;
    }

    private void launchActivity(int displayId, Intent intent) {
        ActivityOptions options = ActivityOptions.makeCustomAnimation(mContext, 0, 0);
        options.setLaunchDisplayId(displayId);
        mContext.startActivityAsUser(intent, options.toBundle(), mUserTracker.getUserHandle());
    }

    @Nullable
    private String getPackageNameFromBaseIntent(Intent intent) {
        if (intent == null || intent.getComponent() == null) {
            return null;
        }
        return intent.getComponent().getPackageName();
    }

    private static void logIfDebuggable(String message) {
        if (DEBUG) {
            Log.d(TAG, message);
        }
    }

    /**
     * Called when unregistered - since the top packages can no longer be guaranteed known, notify
     * listeners for both displays that the top package is null.
     */
    private void clearListeners() {
        notifyListeners(DEFAULT_DISPLAY_ID, null);
        notifyListeners(mDistantDisplayId, null);
    }

    private void notifyListeners(int displayId) {
        if (displayId != DEFAULT_DISPLAY_ID && displayId != mDistantDisplayId) return;
        String pkg;
        TaskData data = mForegroundTasks.getTopTaskOnDisplay(displayId);
        if (data != null) {
            pkg = getPackageNameFromBaseIntent(data.mBaseIntent);
        } else {
            pkg = null;
        }

        notifyListeners(displayId, pkg);
    }

    private void notifyListeners(int displayId, String pkg) {
        synchronized (mCallbacks) {
            for (Callback callback : mCallbacks) {
                callback.topAppOnDisplayChanged(displayId, pkg);
            }
        }
    }

    /** Callback to listen to task changes on the default and distant displays. */
    public interface Callback {
        /**
         * Called when the top app on a particular display changes, including the relevant
         * display id and package name. Note that this will only be called for the default and the
         * configured distant display ids.
         */
        void topAppOnDisplayChanged(int displayId, @Nullable String packageName);
    }
}
