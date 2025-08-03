/*
 * Copyright (C) 2022 The Android Open Source Project
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

import static android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN;

import android.app.ActivityManager;
import android.car.Car;
import android.car.app.CarActivityManager;
import android.content.Context;
import android.hardware.display.DisplayManager;
import android.util.Log;
import android.util.Slog;
import android.view.Display;
import android.view.SurfaceControl;

import com.android.systemui.car.CarServiceProvider;
import com.android.wm.shell.ShellTaskOrganizer;
import com.android.wm.shell.common.SyncTransactionQueue;
import com.android.wm.shell.fullscreen.FullscreenTaskListener;
import com.android.wm.shell.recents.RecentTasksController;
import com.android.wm.shell.sysui.ShellInit;
import com.android.wm.shell.windowdecor.WindowDecorViewModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The Car version of {@link FullscreenTaskListener}, which reports Task lifecycle to CarService
 * only when the {@link CarSystemUIProxyImpl} should be registered.
 *
 * <p>When {@link CarSystemUIProxyImpl#shouldRegisterCarSystemUIProxy(Context)} returns true, the
 * task organizer is registered by the system ui alone and hence SystemUI is responsible to act as
 * a task monitor for the car service.
 *
 * <p>On legacy system where a task organizer is registered by system ui and car launcher both,
 * this listener will not forward task lifecycle to car service as this would end up sending
 * multiple task events to the car service.
 */
public class CarFullscreenTaskMonitorListener extends FullscreenTaskListener {

    private static final String TAG = "CarFullscrTaskMonitor";
    private static final boolean DBG = Log.isLoggable(TAG, Log.DEBUG);
    private final AtomicReference<CarActivityManager> mCarActivityManagerRef =
            new AtomicReference<>();
    private final ShellTaskOrganizer mShellTaskOrganizer;
    private final DisplayManager mDisplayManager;
    private final boolean mShouldConnectToCarActivityService;

    public CarFullscreenTaskMonitorListener(
            Context context,
            CarServiceProvider carServiceProvider,
            ShellInit shellInit,
            ShellTaskOrganizer shellTaskOrganizer,
            SyncTransactionQueue syncQueue,
            Optional<RecentTasksController> recentTasksOptional,
            Optional<WindowDecorViewModel> windowDecorViewModelOptional) {
        super(shellInit, shellTaskOrganizer, syncQueue, recentTasksOptional,
                windowDecorViewModelOptional);

        mShellTaskOrganizer = shellTaskOrganizer;
        mDisplayManager = context.getSystemService(DisplayManager.class);
        // Rely on whether or not CarSystemUIProxy should be registered to account for these cases:
        // 1. Legacy system where System UI + launcher both register a TaskOrganizer.
        //    CarFullScreenTaskMonitorListener will not forward the task lifecycle to the car
        //    service, as launcher has its own FullScreenTaskMonitorListener.
        // 2. MUMD system where only System UI registers a TaskOrganizer but the user associated
        //    with the current display is not a system user. CarSystemUIProxy will be registered
        //    for system user alone and hence CarFullScreenTaskMonitorListener should be registered
        //    only then.
        mShouldConnectToCarActivityService =
                CarSystemUIProxyImpl.shouldRegisterCarSystemUIProxy(context);

        if (mShouldConnectToCarActivityService) {
            carServiceProvider.addListener(this::onCarConnected);
        }
    }

    @Override
    public void onTaskAppeared(ActivityManager.RunningTaskInfo taskInfo,
            SurfaceControl leash) {
        super.onTaskAppeared(taskInfo, leash);

        if (!mShouldConnectToCarActivityService) {
            if (DBG) {
                Slog.w(TAG, "onTaskAppeared() handled in SystemUI as conditions not met for "
                        + "connecting to car service.");
            }
            return;
        }

        CarActivityManager carAM = mCarActivityManagerRef.get();
        if (carAM != null) {
            carAM.onTaskAppeared(taskInfo, leash);
        } else {
            Slog.w(TAG, "CarActivityManager is null, skip onTaskAppeared: taskInfo=" + taskInfo);
        }
    }

    @Override
    public void onTaskInfoChanged(ActivityManager.RunningTaskInfo taskInfo) {
        super.onTaskInfoChanged(taskInfo);

        if (!mShouldConnectToCarActivityService) {
            if (DBG) {
                Slog.w(TAG, "onTaskInfoChanged() handled in SystemUI as conditions not met for "
                        + "connecting to car service.");
            }
            return;
        }

        CarActivityManager carAM = mCarActivityManagerRef.get();
        if (carAM != null) {
            carAM.onTaskInfoChanged(taskInfo);
        } else {
            Slog.w(TAG, "CarActivityManager is null, skip onTaskInfoChanged: taskInfo=" + taskInfo);
        }
    }

    @Override
    public void onTaskVanished(ActivityManager.RunningTaskInfo taskInfo) {
        super.onTaskVanished(taskInfo);

        if (!mShouldConnectToCarActivityService) {
            if (DBG) {
                Slog.w(TAG, "onTaskVanished() handled in SystemUI as conditions not met for "
                        + "connecting to car service.");
            }
            return;
        }

        CarActivityManager carAM = mCarActivityManagerRef.get();
        if (carAM != null) {
            carAM.onTaskVanished(taskInfo);
        } else {
            Slog.w(TAG, "CarActivityManager is null, skip onTaskVanished: taskInfo=" + taskInfo);
        }
    }

    private void onCarConnected(Car car) {
        mCarActivityManagerRef.set(car.getCarManager(CarActivityManager.class));
        // The tasks that have already appeared need to be reported to the CarActivityManager.
        // The code uses null as the leash because there is no way to get the leash at the moment.
        // And the leash is only required for mirroring cases. Those tasks will anyway appear after
        // the car service is connected and hence will go via the {@link #onTaskAppeared} flow.
        List<ActivityManager.RunningTaskInfo> runningFullscreenTaskInfos =
                getRunningFullscreenTasks();
        for (ActivityManager.RunningTaskInfo runningTaskInfo : runningFullscreenTaskInfos) {
            Slog.d(TAG, "Sending onTaskAppeared for an already existing fullscreen task: "
                    + runningTaskInfo.taskId);
            mCarActivityManagerRef.get().onTaskAppeared(runningTaskInfo, null);
        }
    }

    private List<ActivityManager.RunningTaskInfo> getRunningFullscreenTasks() {
        Display[] displays = mDisplayManager.getDisplays();
        List<ActivityManager.RunningTaskInfo> fullScreenTaskInfos = new ArrayList<>();
        for (int i = 0; i < displays.length; i++) {
            List<ActivityManager.RunningTaskInfo> taskInfos =
                    mShellTaskOrganizer.getRunningTasks(displays[i].getDisplayId());
            for (ActivityManager.RunningTaskInfo taskInfo : taskInfos) {
                // In Auto, only TaskView tasks have WINDOWING_MODE_MULTI_WINDOW as of now.
                if (taskInfo.getWindowingMode() == WINDOWING_MODE_FULLSCREEN) {
                    fullScreenTaskInfos.add(taskInfo);
                }
            }
        }
        return fullScreenTaskInfos;
    }
}
