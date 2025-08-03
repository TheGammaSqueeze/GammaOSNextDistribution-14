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
package com.android.car.docklib.task;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.os.Build;
import android.util.Log;
import android.view.Display;

import androidx.annotation.NonNull;

import com.android.car.docklib.DockInterface;
import com.android.systemui.shared.system.TaskStackChangeListener;

public class DockTaskStackChangeListener implements TaskStackChangeListener {
    private static final String TAG = "DockTaskStackChangeListener";
    private static final boolean DEBUG = Build.isDebuggable();

    private final DockInterface mDockController;
    private final int mCurrentUserId;

    public DockTaskStackChangeListener(int currentUserId, @NonNull DockInterface dockController) {
        mDockController = dockController;
        mCurrentUserId = currentUserId;
    }

    @Override
    public void onTaskMovedToFront(ActivityManager.RunningTaskInfo taskInfo) {
        if (taskInfo.displayId != Display.DEFAULT_DISPLAY || taskInfo.userId != mCurrentUserId) {
            if (DEBUG) {
                Log.d(TAG, "New task on display " + taskInfo.displayId
                        + " and for user " + taskInfo.userId + " is not added to the dock");
            }
            return;
        }

        ComponentName component = TaskUtils.Companion.getComponentName(taskInfo);
        if (component != null) {
            mDockController.appLaunched(component);
        }
    }
}
