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


import static android.content.pm.PackageManager.PERMISSION_GRANTED;


import android.Manifest;
import android.annotation.MainThread;
import android.annotation.NonNull;
import android.annotation.RequiresPermission;
import android.annotation.SystemApi;
import android.annotation.UiContext;
import android.app.Activity;
import android.car.Car;
import android.car.builtin.util.Slogf;
import android.content.Context;
import android.os.RemoteException;
import android.os.UserManager;
import android.util.Log;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * This class is used for creating task views & is created on a per activity basis.
 * @hide
 */
@SystemApi
public final class CarTaskViewController {
    private static final String TAG = CarTaskViewController.class.getSimpleName();
    static final boolean DBG = Slogf.isLoggable(TAG, Log.DEBUG);

    private final ICarSystemUIProxy mService;
    private final Context mHostContext;
    private final CarTaskViewControllerHostLifecycle mLifecycle;
    private final List<RemoteCarTaskView> mRemoteCarTaskViews =
            new ArrayList<>();
    private final CarTaskViewInputInterceptor mTaskViewInputInterceptor;
    private final ICarActivityService mCarActivityService;

    private boolean mReleased = false;

    /**
     * @param service the binder interface to communicate with the car system UI.
     * @hide
     */
    CarTaskViewController(@UiContext Context hostContext,
            @NonNull CarTaskViewControllerHostLifecycle lifecycle,
            @NonNull ICarSystemUIProxy service,
            ICarActivityService carActivityService) {
        mHostContext = hostContext;
        mService = service;
        mLifecycle = lifecycle;
        mCarActivityService = carActivityService;
        mTaskViewInputInterceptor = new CarTaskViewInputInterceptor(hostContext, lifecycle, this);
    }

    /**
     * Creates a new {@link ControlledRemoteCarTaskView}.
     *
     * @param callbackExecutor the executor to get the {@link ControlledRemoteCarTaskViewCallback}
     *                         on.
     * @param controlledRemoteCarTaskViewCallback the callback to monitor the
     *                                            {@link ControlledRemoteCarTaskView} related
     *                                            events.
     */
    @RequiresPermission(allOf = {Manifest.permission.INJECT_EVENTS,
            Manifest.permission.INTERNAL_SYSTEM_WINDOW}, conditional = true)
    @MainThread
    public void createControlledRemoteCarTaskView(
            @NonNull ControlledRemoteCarTaskViewConfig controlledRemoteCarTaskViewConfig,
            @NonNull Executor callbackExecutor,
            @NonNull ControlledRemoteCarTaskViewCallback controlledRemoteCarTaskViewCallback) {
        if (mReleased) {
            throw new IllegalStateException("CarTaskViewController is already released");
        }
        ControlledRemoteCarTaskView taskViewClient =
                new ControlledRemoteCarTaskView(
                        mHostContext,
                        controlledRemoteCarTaskViewConfig,
                        callbackExecutor,
                        controlledRemoteCarTaskViewCallback,
                        /* carTaskViewController= */ this,
                        mHostContext.getSystemService(UserManager.class));

        try {
            ICarTaskViewHost host = mService.createControlledCarTaskView(
                    taskViewClient.mICarTaskViewClient);
            taskViewClient.setRemoteHost(host);
            mRemoteCarTaskViews.add(taskViewClient);

            if (controlledRemoteCarTaskViewConfig.mShouldCaptureGestures
                    || controlledRemoteCarTaskViewConfig.mShouldCaptureLongPress) {
                assertPermission(Manifest.permission.INJECT_EVENTS);
                assertPermission(Manifest.permission.INTERNAL_SYSTEM_WINDOW);
                mTaskViewInputInterceptor.init();
            }
        } catch (RemoteException e) {
            Slogf.e(TAG, "Unable to create task view.", e);
        }
    }

    /**
     * Creates a new {@link RemoteCarRootTaskView}.
     *
     * @param callbackExecutor the executor to get the {@link RemoteCarRootTaskViewCallback} on.
     * @param remoteCarRootTaskViewCallback the callback to monitor the
     *                                      {@link RemoteCarRootTaskView} related events.
     * @hide
     */
    @RequiresPermission(Car.PERMISSION_CONTROL_CAR_APP_LAUNCH)
    @MainThread
    public void createRemoteCarRootTaskView(
            @NonNull RemoteCarRootTaskViewConfig remoteCarRootTaskViewConfig,
            @NonNull Executor callbackExecutor,
            @NonNull RemoteCarRootTaskViewCallback remoteCarRootTaskViewCallback) {
        assertPermission(Car.PERMISSION_CONTROL_CAR_APP_LAUNCH);
        if (mReleased) {
            throw new IllegalStateException("CarTaskViewController is already released");
        }
        RemoteCarRootTaskView taskViewClient =
                new RemoteCarRootTaskView(
                        mHostContext,
                        remoteCarRootTaskViewConfig,
                        callbackExecutor,
                        remoteCarRootTaskViewCallback,
                        /* carTaskViewController= */ this,
                        mCarActivityService
                );

        try {
            ICarTaskViewHost host = mService.createCarTaskView(taskViewClient.mICarTaskViewClient);
            taskViewClient.setRemoteHost(host);
            mRemoteCarTaskViews.add(taskViewClient);
        } catch (RemoteException e) {
            Slogf.e(TAG, "Unable to create root task view.", e);
        }
    }

    /**
     * Creates a new {@link RemoteCarDefaultRootTaskView}.
     *
     * @param callbackExecutor the executor to get the {@link RemoteCarDefaultRootTaskViewCallback}
     *                         on.
     * @param remoteCarDefaultRootTaskViewCallback the callback to monitor the
     *                                             {@link RemoteCarDefaultRootTaskView} related
     *                                             events.
     * @hide
     */
    @MainThread
    public void createRemoteCarDefaultRootTaskView(
            @NonNull RemoteCarDefaultRootTaskViewConfig remoteCarDefaultRootTaskViewConfig,
            @NonNull Executor callbackExecutor,
            @NonNull RemoteCarDefaultRootTaskViewCallback remoteCarDefaultRootTaskViewCallback) {
        if (mReleased) {
            throw new IllegalStateException("CarTaskViewController is already released");
        }
        RemoteCarDefaultRootTaskView taskViewClient =
                new RemoteCarDefaultRootTaskView(
                        mHostContext,
                        remoteCarDefaultRootTaskViewConfig,
                        callbackExecutor,
                        remoteCarDefaultRootTaskViewCallback,
                        /* carTaskViewController= */ this
                );

        try {
            ICarTaskViewHost host = mService.createCarTaskView(
                    taskViewClient.mICarTaskViewClient);
            taskViewClient.setRemoteHost(host);
            mRemoteCarTaskViews.add(taskViewClient);
        } catch (RemoteException e) {
            Slogf.e(TAG, "Unable to create default root task view.", e);
        }
    }

    void onRemoteCarTaskViewReleased(@NonNull RemoteCarTaskView taskView) {
        if (mReleased) {
            Log.w(TAG, "Failed to remove the taskView as the "
                    + "CarTaskViewController is already released");
            return;
        }
        if (!mRemoteCarTaskViews.contains(taskView)) {
            Log.w(TAG, "This taskView has already been removed");
            return;
        }
        mRemoteCarTaskViews.remove(taskView);
    }

    private void assertPermission(String permission) {
        if (mHostContext.checkCallingOrSelfPermission(permission)
                != PERMISSION_GRANTED) {
            throw new SecurityException("requires " + permission);
        }
    }

    /**
     * Releases all the resources held by the taskviews associated with this controller.
     *
     * <p> Once {@link #release()} is called, the current instance of {@link CarTaskViewController}
     * cannot be used further. A new instance should be requested using
     * {@link CarActivityManager#getCarTaskViewController(Activity, Executor,
     * CarTaskViewControllerCallback)}.
     */
    @MainThread
    public void release() {
        if (mReleased) {
            Slogf.w(TAG, "CarTaskViewController is already released");
            return;
        }
        releaseTaskViews();
        mTaskViewInputInterceptor.release();
        mReleased = true;
    }

    @MainThread
    void releaseTaskViews() {
        Iterator<RemoteCarTaskView> iterator = mRemoteCarTaskViews.iterator();
        while (iterator.hasNext()) {
            RemoteCarTaskView taskView = iterator.next();
            // Remove the task view here itself because release triggers removal again which can
            // result in concurrent modification exception.
            iterator.remove();
            taskView.release();
        }
    }

    /**
     * Brings all the embedded tasks to the front.
     */
    @MainThread
    public void showEmbeddedTasks() {
        if (mReleased) {
            throw new IllegalStateException("CarTaskViewController is already released");
        }
        for (int i = 0, length = mRemoteCarTaskViews.size(); i < length; i++) {
            RemoteCarTaskView remoteCarTaskView = mRemoteCarTaskViews.get(i);
            // TODO(b/267314188): Add a new method in ICarSystemUI to call
            // showEmbeddedTask in a single WCT for multiple tasks.
            remoteCarTaskView.showEmbeddedTask();
        }
    }

    /**
     * Brings all the embedded controlled tasks to the front.
     */
    @MainThread
    void showEmbeddedControlledTasks() {
        if (mReleased) {
            throw new IllegalStateException("CarTaskViewController is already released");
        }
        for (int i = 0, length = mRemoteCarTaskViews.size(); i < length; i++) {
            RemoteCarTaskView carTaskView = mRemoteCarTaskViews.get(i);
            // TODO(b/267314188): Add a new method in ICarSystemUI to call
            // showEmbeddedTask in a single WCT for multiple tasks.
            if (carTaskView instanceof ControlledRemoteCarTaskView) {
                carTaskView.showEmbeddedTask();
            }
        }
    }

    boolean isHostVisible() {
        return mLifecycle.isVisible();
    }

    List<RemoteCarTaskView> getRemoteCarTaskViews() {
        return mRemoteCarTaskViews;
    }
}
