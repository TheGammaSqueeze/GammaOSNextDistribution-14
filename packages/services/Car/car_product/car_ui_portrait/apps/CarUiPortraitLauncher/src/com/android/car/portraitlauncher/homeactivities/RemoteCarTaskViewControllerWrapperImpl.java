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

import android.app.Activity;
import android.app.ActivityManager;
import android.car.Car;
import android.car.app.CarActivityManager;
import android.car.app.CarTaskViewController;
import android.car.app.CarTaskViewControllerCallback;
import android.car.app.RemoteCarDefaultRootTaskView;
import android.car.app.RemoteCarDefaultRootTaskViewCallback;
import android.car.app.RemoteCarDefaultRootTaskViewConfig;
import android.car.app.RemoteCarRootTaskView;
import android.car.app.RemoteCarRootTaskViewCallback;
import android.car.app.RemoteCarRootTaskViewConfig;
import android.car.app.RemoteCarTaskView;
import android.content.ComponentName;
import android.graphics.Rect;
import android.graphics.Region;
import android.util.Log;
import android.view.SurfaceView;
import android.view.WindowInsets;

import androidx.annotation.NonNull;

import java.util.List;
import java.util.concurrent.Executor;

/** A {@link TaskViewControllerWrapper} that organizing the {@link RemoteCarTaskView}. */
final class RemoteCarTaskViewControllerWrapperImpl extends TaskViewControllerWrapper {
    private static final String TAG = RemoteCarTaskViewControllerWrapperImpl.class.getSimpleName();
    private final Car mCar;
    private CarTaskViewController mCarTaskViewController;

    RemoteCarTaskViewControllerWrapperImpl(Activity activity, Callback callback) {
        super();
        mCar = Car.createCar(/* context= */ activity, /* handler= */ null,
                Car.CAR_WAIT_TIMEOUT_WAIT_FOREVER, (car, ready) -> {
                    if (!ready) {
                        Log.w(TAG, "CarService is not ready.");
                        return;
                    }
                    CarActivityManager carAM = (CarActivityManager) car.getCarManager(
                            Car.CAR_ACTIVITY_SERVICE);

                    carAM.getCarTaskViewController(activity, activity.getMainExecutor(),
                            new CarTaskViewControllerCallback() {
                                @Override
                                public void onConnected(
                                        CarTaskViewController carTaskViewController) {
                                    mCarTaskViewController = carTaskViewController;
                                    callback.onCarConnected();
                                }

                                @Override
                                public void onDisconnected(
                                        CarTaskViewController carTaskViewController) {}
                            });
                });
    }

    private static void logIfDebuggable(String message) {
        if (DBG) {
            Log.d(TAG, message);
        }
    }

    @Override
    void updateAllowListedActivities(int taskViewId, List<ComponentName> list) {
        SurfaceView targetTaskView = getTaskView(taskViewId);
        if (targetTaskView instanceof RemoteCarRootTaskView remoteCarRootTaskView) {
            remoteCarRootTaskView.updateAllowListedActivities(list);
        }
    }

    @Override
    ActivityManager.RunningTaskInfo getRootTaskInfo() {
        for (int keyIndex = 0; keyIndex < mTaskViewsMap.size(); keyIndex++) {
            int key = mTaskViewsMap.keyAt(keyIndex);
            SurfaceView surfaceView = mTaskViewsMap.get(key);
            if (surfaceView instanceof RemoteCarDefaultRootTaskView remoteCarDefaultRootTaskView) {
                return remoteCarDefaultRootTaskView.getTopTaskInfo();
            }
        }
        logIfDebuggable("No RemoteCarDefaultRootTaskView found");
        return null;
    }

    @Override
    void moveToBack(int taskViewId) {
        logIfDebuggable("Move taskview " + taskViewId + " to back");
        RemoteCarTaskView targetTaskView = (RemoteCarTaskView) getTaskView(taskViewId);
        if (targetTaskView == null) {
            return;
        }
        // Stop the app by moving it to back
        targetTaskView.reorderTask(/* onTop= */ false);
    }

    @Override
    public void setWindowBounds(Rect taskViewBounds, int taskViewId) {
        RemoteCarTaskView targetTaskView = (RemoteCarTaskView) getTaskView(taskViewId);
        if (targetTaskView == null) {
            return;
        }
        targetTaskView.setWindowBounds(taskViewBounds);
    }

    @Override
    void updateTaskVisibility(boolean visibility, int taskViewId) {
        logIfDebuggable("updateTaskViewVisibility, visibility=" + visibility + ", taskViewId="
                + taskViewId);
        RemoteCarTaskView targetTaskView = (RemoteCarTaskView) getTaskView(taskViewId);
        targetTaskView.setTaskVisibility(visibility);
    }

    @Override
    void setObscuredTouchRegion(Region obscuredTouchRegion, int taskViewId) {
        RemoteCarTaskView targetTaskView = (RemoteCarTaskView) getTaskView(taskViewId);
        if (targetTaskView == null) {
            return;
        }
        targetTaskView.setObscuredTouchRegion(obscuredTouchRegion);
    }

    @Override
    void showEmbeddedTasks(int[] taskViewIds) {
        for (int key: taskViewIds) {
            SurfaceView surfaceView = mTaskViewsMap.get(key);
            if (surfaceView instanceof RemoteCarTaskView remoteCarTaskView) {
                logIfDebuggable("showEmbeddedTasks, TaskView key=" + key);
                remoteCarTaskView.showEmbeddedTask();
            }
        }
    }

    @Override
    void onDestroy() {
        for (int keyIndex = 0; keyIndex < mTaskViewsMap.size(); keyIndex++) {
            int key = mTaskViewsMap.keyAt(keyIndex);
            SurfaceView surfaceView = mTaskViewsMap.get(key);
            if (surfaceView instanceof RemoteCarTaskView remoteCarTaskView) {
                logIfDebuggable("onDestroy, TaskView key=" + key);
                remoteCarTaskView.release();
            }
        }
        mCar.disconnect();
    }

    @Override
    void updateWindowBounds() {
        for (int keyIndex = 0; keyIndex < mTaskViewsMap.size(); keyIndex++) {
            int key = mTaskViewsMap.keyAt(keyIndex);
            SurfaceView surfaceView = mTaskViewsMap.get(key);
            if (surfaceView instanceof RemoteCarTaskView remoteCarTaskView) {
                logIfDebuggable("updateWindowBounds, TaskView key=" + key);
                remoteCarTaskView.post(remoteCarTaskView::updateWindowBounds);
            }
        }
    }

    @Override
    void createCarRootTaskView(TaskViewCallback taskViewCallback, int displayId, Executor executor,
            List<ComponentName> list) {
        RemoteCarRootTaskViewConfig config = new RemoteCarRootTaskViewConfig.Builder().setDisplayId(
                displayId).setAllowListedActivities(list).build();
        RemoteCarRootTaskViewCallback callback = new RemoteCarRootTaskViewCallback() {
            @Override
            public void onTaskViewCreated(@NonNull RemoteCarRootTaskView taskView) {
                RemoteCarRootTaskViewCallback.super.onTaskViewCreated(taskView);
                taskViewCallback.onTaskViewCreated(taskView);
            }

            @Override
            public void onTaskViewInitialized() {
                RemoteCarRootTaskViewCallback.super.onTaskViewInitialized();
                taskViewCallback.onTaskViewInitialized();
            }

            @Override
            public void onTaskViewReleased() {
                RemoteCarRootTaskViewCallback.super.onTaskViewReleased();
                taskViewCallback.onTaskViewReleased();
            }
        };

        mCarTaskViewController.createRemoteCarRootTaskView(config, executor, callback);
    }

    @Override
    void createCarDefaultRootTaskView(TaskViewCallback taskViewCallback, int displayId,
            Executor executor) {
        RemoteCarDefaultRootTaskViewConfig config =
                new RemoteCarDefaultRootTaskViewConfig.Builder().setDisplayId(
                        displayId).embedRecentsTask(true).build();
        RemoteCarDefaultRootTaskViewCallback callback = new RemoteCarDefaultRootTaskViewCallback() {
            @Override
            public void onTaskViewCreated(@NonNull RemoteCarDefaultRootTaskView taskView) {
                RemoteCarDefaultRootTaskViewCallback.super.onTaskViewCreated(taskView);
                taskViewCallback.onTaskViewCreated(taskView);

            }

            @Override
            public void onTaskAppeared(@NonNull ActivityManager.RunningTaskInfo taskInfo) {
                taskViewCallback.onTaskAppeared(taskInfo);
            }

            @Override
            public void onTaskInfoChanged(@NonNull ActivityManager.RunningTaskInfo taskInfo) {
                taskViewCallback.onTaskInfoChanged(taskInfo);
            }

            @Override
            public void onTaskViewInitialized() {
                RemoteCarDefaultRootTaskViewCallback.super.onTaskViewInitialized();
                taskViewCallback.onTaskViewInitialized();
            }

            @Override
            public void onTaskViewReleased() {
                RemoteCarDefaultRootTaskViewCallback.super.onTaskViewReleased();
                taskViewCallback.onTaskViewReleased();
            }
        };

        mCarTaskViewController.createRemoteCarDefaultRootTaskView(config, executor, callback);
    }

    @Override
    void setSystemOverlayInsets(Rect bottomInsets, Rect topInsets, int taskViewId) {
        RemoteCarTaskView targetTaskView = (RemoteCarTaskView) getTaskView(taskViewId);
        if (targetTaskView == null) {
            return;
        }

        targetTaskView.addInsets(
                /* index= */ 0, WindowInsets.Type.systemOverlays(), bottomInsets);
        targetTaskView.addInsets(
                /* index= */ 1, WindowInsets.Type.systemOverlays(), topInsets);
    }

    @Override
    SurfaceView getTaskView(int taskViewId) {
        return mTaskViewsMap.get(taskViewId);
    }

    @Override
    int getTaskId(int taskViewId) {
        RemoteCarTaskView targetTaskView = (RemoteCarTaskView) getTaskView(taskViewId);

        if (targetTaskView == null) {
            return -1;
        }
        return targetTaskView.getTaskInfo() == null ? -1 : targetTaskView.getTaskInfo().taskId;
    }
}
