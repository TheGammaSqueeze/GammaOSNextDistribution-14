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

package android.car.app;

import android.app.ActivityManager.RunningTaskInfo;
import android.car.app.ICarSystemUIProxy;
import android.car.app.ICarSystemUIProxyCallback;
import android.content.ComponentName;
import android.graphics.Rect;
import android.view.SurfaceControl;
import java.util.List;


/** @hide */
interface ICarActivityService {
    /**
     * Designates the given {@code activity} to be launched in {@code TaskDisplayArea} of
     * {@code featureId} in the display of {@code displayId}.
     */
    int setPersistentActivity(in ComponentName activity, int displayId, int featureId) = 0;

    /**
     * Registers the caller as TaskMonitor, which can provide Task lifecycle events to CarService.
     * The caller should provide a binder token, which is used to check if the given TaskMonitor is
     * live and the reported events are from the legitimate TaskMonitor.
     */
    void registerTaskMonitor(in IBinder token) = 1;

    /**
     * Reports that a Task is created.
     */
    void onTaskAppeared(in IBinder token, in RunningTaskInfo taskInfo, in SurfaceControl leash) = 8;

    /**
     * Reports that a Task is vanished.
     */
    void onTaskVanished(in IBinder token, in RunningTaskInfo taskInfo) = 3;

    /**
     * Reports that some Task's states are changed.
     */
    void onTaskInfoChanged(in IBinder token, in RunningTaskInfo taskInfo) = 4;

    /**
     * Unregisters the caller from TaskMonitor.
     */
    void unregisterTaskMonitor(in IBinder token) = 5;

    /** See {@link CarActivityManager#getVisibleTasks(int)} */
    List<RunningTaskInfo> getVisibleTasks(int displayId) = 6;

    /** See {@link CarActivityManager#startUserPickerOnDisplay(int)} */
    void startUserPickerOnDisplay(int displayId) = 7;

    /** See {@link CarActivityManager#createTaskMirroringToken(int)} */
    IBinder createTaskMirroringToken(int taskId) = 9;

    /** See {@link CarActivityManager#createDisplayMirroringToken(int)} */
    IBinder createDisplayMirroringToken(int displayId) = 10;

    /** See {@link CarActivityManager#getMirroredSurface(IBinder, Rect)} */
    SurfaceControl getMirroredSurface(in IBinder mirroringToken, out Rect bounds) = 11;

    /**
     * Registers a System UI proxy which is meant to host all the system ui interaction that is
     * required by other apps.
     */
    void registerCarSystemUIProxy(in ICarSystemUIProxy carSystemUIProxy) = 12;

    /**
     * Adds a callback to monitor the lifecycle of System UI proxy. Calling this for an already
     * registered callback will result in a no-op.
     */
    void addCarSystemUIProxyCallback(in ICarSystemUIProxyCallback callback) = 13;

    /**
     * Removes the callback to monitor the lifecycle of System UI proxy.
     * Calling this for an already unregistered callback will result in a no-op
     */
    void removeCarSystemUIProxyCallback(in ICarSystemUIProxyCallback callback) = 14;

    /** See {@link CarActivityManager#moveRootTaskToDisplay(int, int)} */
    void moveRootTaskToDisplay(int taskId, int displayId) = 15;

    /**
     * Returns true if the {@link CarSystemUIProxy} is registered, false otherwise.
     */
    boolean isCarSystemUIProxyRegistered() = 16;

    void setPersistentActivitiesOnRootTask(in List<ComponentName> activities,
        in IBinder launchCookie) = 17;
}

