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
package com.android.systemui.car.activity.window;

import static android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.ActivityManager.RunningTaskInfo;
import android.car.app.CarActivityManager;
import android.car.app.CarTaskViewController;
import android.car.app.CarTaskViewControllerCallback;
import android.car.app.CarTaskViewControllerHostLifecycle;
import android.car.app.RemoteCarDefaultRootTaskView;
import android.car.app.RemoteCarDefaultRootTaskViewCallback;
import android.car.app.RemoteCarDefaultRootTaskViewConfig;
import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Binder;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowManager;

import androidx.annotation.MainThread;

import com.android.systemui.R;
import com.android.systemui.car.CarServiceProvider;
import com.android.systemui.car.displaycompat.ToolbarController;
import com.android.systemui.dagger.qualifiers.UiBackground;
import com.android.systemui.shared.system.TaskStackChangeListener;
import com.android.systemui.shared.system.TaskStackChangeListeners;

import javax.inject.Inject;

/**
 * Handles adding the {@link RemoteCarDefaultRootTaskView}
 */
public class ActivityWindowControllerImpl implements ActivityWindowController {
    public static final String TAG = ActivityWindowController.class.getSimpleName();

    @NonNull
    private final Context mContext;
    @NonNull
    private final WindowManager mWindowManager;
    @NonNull
    private final CarServiceProvider mCarServiceProvider;
    @NonNull
    private ViewGroup mLayout;
    @NonNull
    private WindowManager.LayoutParams mWmLayoutParams;

    @NonNull
    private CarTaskViewController mCarTaskViewController;
    @NonNull
    private CarTaskViewControllerHostLifecycle mCarTaskViewControllerHostLifecycle;
    @NonNull
    private CarActivityManager mCarActivityManager;

    @NonNull
    @UiBackground
    private final CarServiceProvider.CarServiceOnConnectedListener mCarServiceLifecycleListener =
            car -> {
                mCarActivityManager = car.getCarManager(CarActivityManager.class);

                inflate();
                setupRemoteCarTaskView();
            };

    @Nullable
    private ToolbarController mToolbarController;

    @Inject
    public ActivityWindowControllerImpl(Context context, WindowManager windowManager,
            CarServiceProvider carServiceProvider,
            CarTaskViewControllerHostLifecycle carTaskViewControllerHostLifecycle,
            @Nullable ToolbarController toolbarController) {
        mContext = context;
        mWindowManager = windowManager;
        mCarServiceProvider = carServiceProvider;
        mCarTaskViewControllerHostLifecycle = carTaskViewControllerHostLifecycle;
        mToolbarController = toolbarController;
    }

    /**
     * called for initialization
     */
    @MainThread
    @Override
    public void init() {
        mCarServiceProvider.addListener(mCarServiceLifecycleListener);
    }

    @MainThread
    protected void inflate() {
        mLayout = (ViewGroup) LayoutInflater.from(mContext)
                .inflate(R.layout.car_activity_window, /* root= */ null);

        mWmLayoutParams = new WindowManager.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                    | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT);
        mWmLayoutParams.setTrustedOverlay();
        mWmLayoutParams.setFitInsetsTypes(0);
        mWmLayoutParams.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;
        mWmLayoutParams.token = new Binder();
        mWmLayoutParams.setTitle("ActivityWindow!");
        mWmLayoutParams.packageName = mContext.getPackageName();
        mWmLayoutParams.layoutInDisplayCutoutMode = LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        mWmLayoutParams.privateFlags |= WindowManager.LayoutParams.SYSTEM_FLAG_SHOW_FOR_ALL_USERS;

        mWindowManager.addView(mLayout, mWmLayoutParams);
    }

    private void setupRemoteCarTaskView() {
        mCarActivityManager.getCarTaskViewController(
                mContext,
                mCarTaskViewControllerHostLifecycle,
                mContext.getMainExecutor(),
                new CarTaskViewControllerCallback() {
                    @Override
                    public void onConnected(
                            CarTaskViewController carTaskViewController) {
                        mCarTaskViewController = carTaskViewController;
                        taskViewControllerReady();
                    }

                    @Override
                    public void onDisconnected(
                            CarTaskViewController carTaskViewController) {
                    }
                });
    }

    private void taskViewControllerReady() {
        mCarTaskViewController.createRemoteCarDefaultRootTaskView(
                new RemoteCarDefaultRootTaskViewConfig.Builder()
                        .setDisplayId(mContext.getDisplayId())
                        .embedHomeTask(true)
                        .embedRecentsTask(true)
                        .build(),
                mContext.getMainExecutor(),
                new RemoteCarDefaultRootTaskViewCallback() {
                    @Override
                    public void onTaskViewCreated(@NonNull RemoteCarDefaultRootTaskView taskView) {
                        Log.d(TAG, "Root Task View is created");
                        taskView.setZOrderMediaOverlay(true);

                        mLayout.setOnApplyWindowInsetsListener(
                                new View.OnApplyWindowInsetsListener() {
                                @Override
                                public WindowInsets onApplyWindowInsets(View view,
                                        WindowInsets insets) {
                                    mLayout.setPadding(
                                            insets.getSystemWindowInsetLeft(),
                                            insets.getSystemWindowInsetTop(),
                                            insets.getSystemWindowInsetRight(),
                                            insets.getSystemWindowInsetBottom());
                                    return insets.replaceSystemWindowInsets(
                                        /* left */ 0, /* top */ 0, /* right */ 0, /* bottom */ 0);
                                }
                            });

                        if (mToolbarController != null) {
                            mToolbarController.init(mLayout);
                        }

                        TaskStackChangeListeners.getInstance().registerTaskStackListener(
                                new TaskStackChangeListener() {
                                @Override
                                public void onTaskMovedToFront(RunningTaskInfo taskInfo) {
                                    if (mToolbarController != null) {
                                        mToolbarController.update(taskInfo);
                                    }
                                }
                            });

                        ViewGroup layout = (ViewGroup) mLayout.findViewById(R.id.activity_area);
                        layout.addView(taskView);
                    }

                    @Override
                    public void onTaskViewInitialized() {
                        Log.d(TAG, "Root Task View is ready");
                    }
                }
        );
    }
}
