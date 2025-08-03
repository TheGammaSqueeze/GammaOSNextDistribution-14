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

package com.android.car.carlauncher;

import static android.app.ActivityTaskManager.INVALID_TASK_ID;
import static android.view.WindowManager.LayoutParams.TYPE_APPLICATION_STARTING;

import static java.util.Objects.requireNonNull;

import android.annotation.NonNull;
import android.annotation.SuppressLint;
import android.annotation.UiContext;
import android.app.ActivityManager;
import android.car.Car;
import android.car.app.CarActivityManager;
import android.car.app.CarTaskViewController;
import android.car.app.CarTaskViewControllerCallback;
import android.car.app.CarTaskViewControllerHostLifecycle;
import android.car.app.ControlledRemoteCarTaskView;
import android.car.app.ControlledRemoteCarTaskViewCallback;
import android.car.app.ControlledRemoteCarTaskViewConfig;
import android.car.app.RemoteCarTaskView;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.util.Log;

import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

/**
 * A car launcher view model to manage the lifecycle of {@link RemoteCarTaskView}.
 */
public final class CarLauncherViewModel extends ViewModel implements DefaultLifecycleObserver {
    private static final String TAG = CarLauncher.TAG;
    private static final boolean DEBUG = CarLauncher.DEBUG;
    private static final boolean sAutoRestartOnCrash = Build.IS_USER;

    private final CarActivityManager mCarActivityManager;
    private final Car mCar;
    private final CarTaskViewControllerHostLifecycle mHostLifecycle;
    @SuppressLint("StaticFieldLeak") // We're not leaking this context as it is the window context.
    private final Context mWindowContext;
    private final Intent mMapsIntent;
    private final MutableLiveData<RemoteCarTaskView> mRemoteCarTaskView;

    public CarLauncherViewModel(@UiContext Context context, @NonNull Intent mapsIntent) {
        mWindowContext = context.createWindowContext(TYPE_APPLICATION_STARTING, /* options */ null);
        mMapsIntent = mapsIntent;
        mCar = Car.createCar(mWindowContext);
        mCarActivityManager = mCar.getCarManager(CarActivityManager.class);
        mHostLifecycle = new CarTaskViewControllerHostLifecycle();
        mRemoteCarTaskView = new MutableLiveData<>(null);
        ControlledRemoteCarTaskViewCallback controlledRemoteCarTaskViewCallback =
                new ControlledRemoteCarTaskViewCallbackImpl(mRemoteCarTaskView);

        CarTaskViewControllerCallback carTaskViewControllerCallback =
                new CarTaskViewControllerCallbackImpl(controlledRemoteCarTaskViewCallback);

        mCarActivityManager.getCarTaskViewController(mWindowContext, mHostLifecycle,
                mWindowContext.getMainExecutor(), carTaskViewControllerCallback);
    }

    LiveData<RemoteCarTaskView> getRemoteCarTaskView() {
        return mRemoteCarTaskView;
    }

    /**
     * Returns remote car task view task Id.
     */
    public int getRemoteCarTaskViewTaskId() {
        if (mRemoteCarTaskView != null && mRemoteCarTaskView.getValue() != null
                && mRemoteCarTaskView.getValue().getTaskInfo() != null) {
            return mRemoteCarTaskView.getValue().getTaskInfo().taskId;
        }
        return INVALID_TASK_ID;
    }

    /**
     * Shows remote car task view when activity is resumed.
     */
    @Override
    public void onResume(@NonNull LifecycleOwner owner) {
        DefaultLifecycleObserver.super.onResume(owner);
        mHostLifecycle.hostAppeared();
    }

    @Override
    public void onStop(@NonNull LifecycleOwner owner) {
        DefaultLifecycleObserver.super.onStop(owner);
        mHostLifecycle.hostDisappeared();
    }

    @Override
    protected void onCleared() {
        if (mRemoteCarTaskView != null) {
            mRemoteCarTaskView.setValue(null);
        }
        if (mCar != null) {
            mCar.disconnect();
        }
        mHostLifecycle.hostDestroyed();
        super.onCleared();
    }

    private static final class ControlledRemoteCarTaskViewCallbackImpl implements
            ControlledRemoteCarTaskViewCallback {
        private final MutableLiveData<RemoteCarTaskView> mRemoteCarTaskView;

        private ControlledRemoteCarTaskViewCallbackImpl(
                MutableLiveData<RemoteCarTaskView> remoteCarTaskView) {
            mRemoteCarTaskView = remoteCarTaskView;
        }

        @Override
        public void onTaskViewCreated(@NonNull ControlledRemoteCarTaskView taskView) {
            mRemoteCarTaskView.setValue(taskView);
        }

        @Override
        public void onTaskViewInitialized() {
            if (DEBUG) {
                Log.d(TAG, "MapsTaskView: onTaskViewInitialized");
            }
        }

        @Override
        public void onTaskAppeared(@NonNull ActivityManager.RunningTaskInfo taskInfo) {
            if (DEBUG) {
                Log.d(TAG, "MapsTaskView: onTaskAppeared: taskId=" + taskInfo.taskId);
            }
            if (!sAutoRestartOnCrash) {
                mRemoteCarTaskView.getValue().setBackgroundColor(Color.TRANSPARENT);
            }
        }

        @Override
        public void onTaskVanished(@NonNull ActivityManager.RunningTaskInfo taskInfo) {
            if (DEBUG) {
                Log.d(TAG, "MapsTaskView: onTaskVanished: taskId=" + taskInfo.taskId);
            }
            if (!sAutoRestartOnCrash) {
                // RemoteCarTaskView color is set to red to indicate
                // that nothing is wrong with the task view but maps
                // in the task view has crashed. More details in
                // b/247156851.
                mRemoteCarTaskView.getValue().setBackgroundColor(Color.RED);
            }
        }
    }

    private final class CarTaskViewControllerCallbackImpl implements CarTaskViewControllerCallback {
        private final ControlledRemoteCarTaskViewCallback mControlledRemoteCarTaskViewCallback;

        private CarTaskViewControllerCallbackImpl(
                ControlledRemoteCarTaskViewCallback controlledRemoteCarTaskViewCallback) {
            mControlledRemoteCarTaskViewCallback = controlledRemoteCarTaskViewCallback;
        }

        @Override
        public void onConnected(@NonNull CarTaskViewController carTaskViewController) {
            carTaskViewController.createControlledRemoteCarTaskView(
                    new ControlledRemoteCarTaskViewConfig.Builder()
                            .setActivityIntent(mMapsIntent)
                            .setShouldAutoRestartOnTaskRemoval(sAutoRestartOnCrash)
                            .build(),
                    mWindowContext.getMainExecutor(),
                    mControlledRemoteCarTaskViewCallback);
        }

        @Override
        public void onDisconnected(@NonNull CarTaskViewController carTaskViewController) {
            if (DEBUG) {
                Log.d(TAG, "onDisconnected");
            }
            mRemoteCarTaskView.setValue(null);
        }
    }

    static final class CarLauncherViewModelFactory implements ViewModelProvider.Factory {
        private final Context mContext;
        private final Intent mMapsIntent;

        CarLauncherViewModelFactory(@UiContext Context context, @NonNull Intent mapsIntent) {
            mMapsIntent = requireNonNull(mapsIntent);
            mContext = requireNonNull(context);
        }

        @NonNull
        @Override
        public <T extends ViewModel> T create(Class<T> modelClass) {
            return modelClass.cast(new CarLauncherViewModel(mContext, mMapsIntent));
        }
    }
}
