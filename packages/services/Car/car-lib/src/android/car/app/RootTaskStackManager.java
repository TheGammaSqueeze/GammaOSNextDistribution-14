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

package android.car.app;

import android.annotation.Nullable;
import android.app.ActivityManager;
import android.view.SurfaceControl;

import java.util.Iterator;
import java.util.LinkedHashMap;

final class RootTaskStackManager {
    private ActivityManager.RunningTaskInfo mRootTask;
    private final LinkedHashMap<Integer, ActivityManager.RunningTaskInfo> mChildrenTaskStack =
            new LinkedHashMap<>();

    void taskAppeared(ActivityManager.RunningTaskInfo taskInfo, SurfaceControl leash) {
        // The first call to onTaskAppeared() is always for the root-task.
        if (mRootTask == null) {
            mRootTask = taskInfo;
            return;
        }

        mChildrenTaskStack.put(taskInfo.taskId, taskInfo);
    }

    void taskInfoChanged(ActivityManager.RunningTaskInfo taskInfo) {
        if (mRootTask == null || mRootTask.taskId == taskInfo.taskId) {
            return;
        }
        if (taskInfo.isVisible() && mChildrenTaskStack.containsKey(taskInfo.taskId)) {
            // Remove the task and insert again so that it jumps to the end of
            // the queue.
            mChildrenTaskStack.remove(taskInfo.taskId);
            mChildrenTaskStack.put(taskInfo.taskId, taskInfo);
        }
    }

    void taskVanished(ActivityManager.RunningTaskInfo taskInfo) {
        if (mRootTask == null || mRootTask.taskId == taskInfo.taskId) {
            return;
        }

        if (mChildrenTaskStack.containsKey(taskInfo.taskId)) {
            mChildrenTaskStack.remove(taskInfo.taskId);
        }
    }

    @Nullable
    ActivityManager.RunningTaskInfo getTopTask() {
        if (mChildrenTaskStack.isEmpty()) {
            return null;
        }
        ActivityManager.RunningTaskInfo topTask = null;
        Iterator<ActivityManager.RunningTaskInfo> iterator = mChildrenTaskStack.values().iterator();
        while (iterator.hasNext()) {
            topTask = iterator.next();
        }
        return topTask;
    }
}
