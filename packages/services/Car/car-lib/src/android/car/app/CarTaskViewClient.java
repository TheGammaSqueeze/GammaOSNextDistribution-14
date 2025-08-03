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


import android.annotation.NonNull;
import android.annotation.SystemApi;
import android.app.ActivityManager;
import android.graphics.Rect;
import android.os.RemoteException;
import android.view.SurfaceControl;

/**
 * Represents the client part of the task view as seen by the server. This wraps the AIDL based
 * communication with the client apps.
 *
 * @hide
 */
@SystemApi
public final class CarTaskViewClient {
    private final ICarTaskViewClient mICarTaskViewClient;

    CarTaskViewClient(ICarTaskViewClient iCarCarTaskViewClient) {
        mICarTaskViewClient = iCarCarTaskViewClient;
    }

    /** Returns the current bounds (in pixels) on screen for the task view's view part. */
    @NonNull
    public Rect getCurrentBoundsOnScreen() {
        try {
            return mICarTaskViewClient.getCurrentBoundsOnScreen();
        } catch (RemoteException ex) {
            ex.rethrowFromSystemServer();
        }
        return null; // cannot reach here. This is just to satisfy compiler.
    }

    /**
     * Sets the resize background color on the task view's view part.
     *
     * <p>See {@link android.view.SurfaceView#setResizeBackgroundColor(SurfaceControl.Transaction,
     * int)}
     */
    public void setResizeBackgroundColor(@NonNull SurfaceControl.Transaction transaction,
            int color) {
        try {
            mICarTaskViewClient.setResizeBackgroundColor(transaction, color);
        } catch (RemoteException ex) {
            ex.rethrowFromSystemServer();
        }
    }

    /** Called when a task has appeared on the TaskView. */
    public void onTaskAppeared(@NonNull ActivityManager.RunningTaskInfo taskInfo,
            @NonNull SurfaceControl leash) {
        try {
            mICarTaskViewClient.onTaskAppeared(taskInfo, leash);
        } catch (RemoteException ex) {
            ex.rethrowFromSystemServer();
        }
    }

    /** Called when a task has vanished from the TaskView. */
    public void onTaskVanished(@NonNull ActivityManager.RunningTaskInfo taskInfo) {
        try {
            mICarTaskViewClient.onTaskVanished(taskInfo);
        } catch (RemoteException ex) {
            ex.rethrowFromSystemServer();
        }
    }

    /** Called when the task in the TaskView is changed. */
    public void onTaskInfoChanged(@NonNull ActivityManager.RunningTaskInfo taskInfo) {
        try {
            mICarTaskViewClient.onTaskInfoChanged(taskInfo);
        } catch (RemoteException ex) {
            ex.rethrowFromSystemServer();
        }
    }
}
