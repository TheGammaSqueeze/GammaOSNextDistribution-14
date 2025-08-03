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

package com.android.car.carlauncher.recents;

import static android.app.ActivityManager.RECENT_IGNORE_UNAVAILABLE;

import static com.android.wm.shell.util.GroupedRecentTaskInfo.TYPE_FREEFORM;
import static com.android.wm.shell.util.GroupedRecentTaskInfo.TYPE_SINGLE;
import static com.android.wm.shell.util.GroupedRecentTaskInfo.TYPE_SPLIT;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.RemoteException;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.systemui.shared.recents.model.Task;
import com.android.systemui.shared.recents.model.ThumbnailData;
import com.android.systemui.shared.system.ActivityManagerWrapper;
import com.android.systemui.shared.system.PackageManagerWrapper;
import com.android.systemui.shared.system.TaskStackChangeListener;
import com.android.systemui.shared.system.TaskStackChangeListeners;
import com.android.wm.shell.recents.IRecentTasks;
import com.android.wm.shell.util.GroupedRecentTaskInfo;

import com.google.common.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class RecentTasksProvider implements RecentTasksProviderInterface {
    private static final String TAG = "RecentTasksProviderInterface";
    private static final boolean DEBUG = Build.IS_DEBUGGABLE;
    private static Executor sRecentsModelExecutor = Executors.newSingleThreadExecutor();
    private static Handler sMainHandler = new Handler(Looper.getMainLooper());
    private static RecentTasksProvider sInstance;
    private Context mContext;
    private IRecentTasks mRecentTasksProxy;
    private ActivityManagerWrapper mActivityManagerWrapper;
    private PackageManagerWrapper mPackageManagerWrapper;
    private Drawable mDefaultIcon;
    private List<Integer> mRecentTaskIds;
    @VisibleForTesting
    Map<Integer, Task> mRecentTaskIdToTaskMap;
    private RecentsDataChangeListener mRecentsDataChangeListener;
    private boolean mIsInitialised;
    private final TaskStackChangeListener mTaskStackChangeListener = new TaskStackChangeListener() {
        @Override
        public boolean onTaskSnapshotChanged(int taskId, ThumbnailData snapshot) {
            if (!mRecentTaskIdToTaskMap.containsKey(taskId)) {
                return false;
            }
            mRecentTaskIdToTaskMap.get(taskId).thumbnail = snapshot;
            if (mRecentsDataChangeListener != null) {
                sMainHandler.post(
                        () -> mRecentsDataChangeListener.recentTaskThumbnailChange(taskId));
            }
            return true;
        }

        @Override
        public void onTaskDescriptionChanged(ActivityManager.RunningTaskInfo taskInfo) {
            if (!mRecentTaskIdToTaskMap.containsKey(taskInfo.taskId)) {
                return;
            }
            Drawable icon = getIconFromTaskDescription(taskInfo.taskDescription);
            if (icon == null) {
                return;
            }
            mRecentTaskIdToTaskMap.get(taskInfo.taskId).icon = icon;
            if (mRecentsDataChangeListener != null) {
                sMainHandler.post(
                        () -> mRecentsDataChangeListener.recentTaskIconChange(taskInfo.taskId));
            }
        }
    };

    private RecentTasksProvider() {
        mActivityManagerWrapper = ActivityManagerWrapper.getInstance();
        mPackageManagerWrapper = PackageManagerWrapper.getInstance();
        mRecentTaskIds = new ArrayList<>();
        mRecentTaskIdToTaskMap = new HashMap<>();
        mDefaultIcon = Objects.requireNonNull(Resources.getSystem().getDrawable(
                android.R.drawable.sym_def_app_icon, /* theme= */ null));
    }

    public static RecentTasksProvider getInstance() {
        if (sInstance == null) {
            sInstance = new RecentTasksProvider();
        }
        return sInstance;
    }

    public void init(Context context, IRecentTasks recentTasksProxy) {
        if (mIsInitialised) {
            return;
        }
        mIsInitialised = true;
        mContext = context;
        mRecentTasksProxy = recentTasksProxy;
        TaskStackChangeListeners.getInstance().registerTaskStackListener(mTaskStackChangeListener);
    }

    /**
     * Terminates connections and sets shared service variables to {@code null}.
     */
    public void terminate() {
        mIsInitialised = false;
        mContext = null;
        mRecentTasksProxy = null;
        TaskStackChangeListeners.getInstance().unregisterTaskStackListener(
                mTaskStackChangeListener);
        clearCache();
    }

    @Override
    public void getRecentTasksAsync() {
        if (mRecentTasksProxy == null) {
            return;
        }
        sRecentsModelExecutor.execute(() -> {
            GroupedRecentTaskInfo[] groupedRecentTasks;
            try {
                // todo: b/271498799 use ActivityManagerWrapper.getInstance().getCurrentUserId()
                //  or equivalent instead of hidden API mContext.getUserId()
                groupedRecentTasks =
                        mRecentTasksProxy.getRecentTasks(Integer.MAX_VALUE,
                                RECENT_IGNORE_UNAVAILABLE, mContext.getUserId());
            } catch (RemoteException e) {
                if (DEBUG) {
                    Log.e(TAG, e.toString());
                }
                return;
            }
            if (groupedRecentTasks == null) {
                return;
            }
            mRecentTaskIds = new ArrayList<>(groupedRecentTasks.length);
            mRecentTaskIdToTaskMap = new HashMap<>(groupedRecentTasks.length);
            boolean areSplitOrFreeformTypeTasksPresent = false;
            for (GroupedRecentTaskInfo groupedRecentTask : groupedRecentTasks) {
                switch (groupedRecentTask.getType()) {
                    case TYPE_SINGLE:
                        // Automotive doesn't have split screen functionality, only process tasks
                        // of TYPE_SINGLE.
                        ActivityManager.RecentTaskInfo taskInfo = groupedRecentTask.getTaskInfo1();
                        Task.TaskKey taskKey = new Task.TaskKey(taskInfo);

                        // isLocked is always set to false since this value is not required in
                        // automotive. Usually set as Keyguard lock state for the user associated
                        // with the task.
                        // ag/1705623 introduced this to support multiple profiles under same user
                        // where this value is necessary to check if profile user associated with
                        // the task is unlocked.
                        Task task = Task.from(taskKey, taskInfo, /* isLocked= */ false);
                        task.setLastSnapshotData(taskInfo);
                        mRecentTaskIds.add(task.key.id);
                        mRecentTaskIdToTaskMap.put(task.key.id, task);
                        getRecentTaskThumbnailAsync(task.key.id);
                        getRecentTaskIconAsync(task.key.id);
                        break;
                    case TYPE_SPLIT:
                    case TYPE_FREEFORM:
                        areSplitOrFreeformTypeTasksPresent = true;
                }
            }
            if (areSplitOrFreeformTypeTasksPresent && DEBUG) {
                Log.d(TAG, "Automotive doesn't support TYPE_SPLIT and TYPE_FREEFORM tasks");
            }
            if (mRecentsDataChangeListener != null) {
                sMainHandler.post(() -> mRecentsDataChangeListener.recentTasksFetched());
            }
        });
    }

    @NonNull
    @Override
    public List<Integer> getRecentTaskIds() {
        return new ArrayList<>(mRecentTaskIds);
    }

    @Nullable
    @Override
    public ComponentName getRecentTaskComponentName(int taskId) {
        return mRecentTaskIdToTaskMap.containsKey(taskId)
                ? mRecentTaskIdToTaskMap.get(taskId).getTopComponent() : null;
    }

    @Nullable
    @Override
    public Intent getRecentTaskBaseIntent(int taskId) {
        return mRecentTaskIdToTaskMap.containsKey(taskId)
                ? mRecentTaskIdToTaskMap.get(taskId).getKey().baseIntent : null;
    }

    @Nullable
    @Override
    public Drawable getRecentTaskIcon(int taskId) {
        return mRecentTaskIdToTaskMap.containsKey(taskId)
                ? mRecentTaskIdToTaskMap.get(taskId).icon : null;
    }

    @Nullable
    @Override
    public Bitmap getRecentTaskThumbnail(int taskId) {
        ThumbnailData thumbnailData = getRecentTaskThumbnailData(taskId);
        return thumbnailData != null ? thumbnailData.thumbnail : null;
    }

    @NonNull
    @Override
    public Rect getRecentTaskInsets(int taskId) {
        ThumbnailData thumbnailData = getRecentTaskThumbnailData(taskId);
        return thumbnailData != null ? thumbnailData.insets : new Rect();

    }

    @Nullable
    private ThumbnailData getRecentTaskThumbnailData(int taskId) {
        if (!mRecentTaskIdToTaskMap.containsKey(taskId)) {
            return null;
        }
        return mRecentTaskIdToTaskMap.get(taskId).thumbnail;
    }

    @Override
    public boolean openRecentTask(int taskId) {
        if (!mRecentTaskIdToTaskMap.containsKey(taskId)) {
            return false;
        }
        return ActivityManagerWrapper.getInstance().startActivityFromRecents(
                mRecentTaskIdToTaskMap.get(taskId).key, /* options= */ null);
    }

    @Override
    public boolean openTopRunningTask(@NonNull Class<? extends Activity> recentsActivity,
            int displayId) {
        ActivityManager.RunningTaskInfo[] runningTasks = mActivityManagerWrapper.getRunningTasks(
                /* filterOnlyVisibleRecents= */ false, displayId);
        boolean foundRecentsTask = false;
        for (ActivityManager.RunningTaskInfo runningTask : runningTasks) {
            if (runningTask == null) {
                return false;
            }
            if (foundRecentsTask) {
                // this is the running task after recents task, attempt to open
                return mActivityManagerWrapper.startActivityFromRecents(
                        runningTask.taskId, /* options= */ null);
            }
            String topComponent = runningTask.topActivity != null
                    ? runningTask.topActivity.getClassName()
                    : runningTask.baseIntent.getComponent().getClassName();
            if (recentsActivity.getName().equals(topComponent)) {
                foundRecentsTask = true;
            }
        }
        // Recents task not found or no task present after recents task,
        // not attempting to open any running task
        return false;
    }

    @Override
    public void removeTaskFromRecents(int taskId) {
        if (!mRecentTaskIdToTaskMap.containsKey(taskId)) {
            return;
        }
        mActivityManagerWrapper.removeTask(mRecentTaskIdToTaskMap.get(taskId).key.id);
        mRecentTaskIds.remove((Integer) taskId);
    }

    @Override
    public void removeAllRecentTasks() {
        mActivityManagerWrapper.removeAllRecentTasks();
        clearCache();
    }

    @Override
    public void clearCache() {
        mRecentTaskIds.clear();
        mRecentTaskIdToTaskMap.clear();
    }

    @Override
    public void setRecentsDataChangeListener(@Nullable RecentsDataChangeListener listener) {
        mRecentsDataChangeListener = listener;
    }

    private void getRecentTaskThumbnailAsync(int taskId) {
        sRecentsModelExecutor.execute(() -> {
            ThumbnailData thumbnailData = mActivityManagerWrapper.getTaskThumbnail(
                    taskId, /* isLowResolution= */ false);
            if (!mRecentTaskIdToTaskMap.containsKey(taskId)) {
                return;
            }
            mRecentTaskIdToTaskMap.get(taskId).thumbnail = thumbnailData;
            if (mRecentsDataChangeListener != null) {
                sMainHandler.post(
                        () -> mRecentsDataChangeListener.recentTaskThumbnailChange(taskId));
            }
        });
    }

    @VisibleForTesting
    void getRecentTaskIconAsync(int taskId) {
        sRecentsModelExecutor.execute(() -> {
            Task task = mRecentTaskIdToTaskMap.get(taskId);
            Task.TaskKey key = task.key;
            Drawable drawableIcon = getIconFromTaskDescription(task.taskDescription);

            if (drawableIcon == null) {
                ActivityInfo activityInfo = mPackageManagerWrapper.getActivityInfo(
                        key.getComponent(), key.userId);
                if (activityInfo != null) {
                    drawableIcon = activityInfo.loadIcon(mContext.getPackageManager());
                } else {
                    // set it a default icon
                    drawableIcon = mDefaultIcon;
                }
            }
            if (!mRecentTaskIdToTaskMap.containsKey(taskId)) {
                return;
            }
            mRecentTaskIdToTaskMap.get(taskId).icon = drawableIcon;
            if (mRecentsDataChangeListener != null) {
                sMainHandler.post(
                        () -> mRecentsDataChangeListener.recentTaskIconChange(taskId));
            }
        });
    }

    @Nullable
    private Drawable getIconFromTaskDescription(
            ActivityManager.TaskDescription taskDescription) {
        Bitmap icon;
        // todo: b/271498799 access through ActivityManagerWrapper instead of using
        //  hidden api getInMemoryIcon(), loadTaskDescriptionIcon() and mContext.getUserId()
        if (taskDescription.getInMemoryIcon() != null) {
            icon = taskDescription.getInMemoryIcon();
        } else {
            icon = ActivityManager.TaskDescription.loadTaskDescriptionIcon(
                    taskDescription.getIconFilename(), mContext.getUserId());
        }
        return icon != null ? new BitmapDrawable(mContext.getResources(), icon) : null;
    }

    @VisibleForTesting
    static void setExecutor(Executor executor) {
        sRecentsModelExecutor = executor;
    }

    @VisibleForTesting
    static void setHandler(Handler handler) {
        sMainHandler = handler;
    }

    @VisibleForTesting
    void setActivityManagerWrapper(ActivityManagerWrapper activityManagerWrapper) {
        mActivityManagerWrapper = activityManagerWrapper;
    }

    @VisibleForTesting
    void setPackageManagerWrapper(PackageManagerWrapper packageManagerWrapper) {
        mPackageManagerWrapper = packageManagerWrapper;
    }

    @VisibleForTesting
    void setDefaultIcon(Drawable icon) {
        mDefaultIcon = icon;
    }
}
