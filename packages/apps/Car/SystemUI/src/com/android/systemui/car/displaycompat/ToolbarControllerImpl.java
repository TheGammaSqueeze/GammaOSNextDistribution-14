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
package com.android.systemui.car.displaycompat;

import static android.car.Car.PERMISSION_QUERY_DISPLAY_COMPATIBILITY;
import static android.view.Display.DEFAULT_DISPLAY;
import static android.view.View.GONE;
import static android.view.View.VISIBLE;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.ActivityManager.RunningTaskInfo;
import android.car.content.pm.CarPackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.hardware.input.InputManager;
import android.hardware.input.InputManagerGlobal;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;

import androidx.annotation.MainThread;
import androidx.annotation.RequiresPermission;

import com.android.systemui.R;
import com.android.systemui.car.CarServiceProvider;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.dagger.qualifiers.UiBackground;

import javax.inject.Inject;

/**
 * Implementation of {@link ToolbarController} for showing/hiding the display compatibility toolbar.
 */
public class ToolbarControllerImpl implements ToolbarController {

    private static final String TAG = ToolbarControllerImpl.class.getSimpleName();

    private ViewGroup mToolbarParent;

    @NonNull
    private final Handler mMainHandler;

    @NonNull
    private CarServiceProvider mCarServiceProvider;
    @Nullable
    private CarPackageManager mCarPackageManager;
    @NonNull
    @UiBackground
    private final CarServiceProvider.CarServiceOnConnectedListener mCarServiceLifecycleListener =
            car -> {
                mCarPackageManager = car.getCarManager(CarPackageManager.class);
            };
    @Nullable
    private ImageButton mBackButton;

    @Inject
    public ToolbarControllerImpl(@NonNull @Main Handler mainHandler,
            CarServiceProvider carServiceProvider) {
        mMainHandler = mainHandler;
        mCarServiceProvider = carServiceProvider;
    }

    /**
     * Needs to be called before calling any other method.
     */
    @Override
    public void init(@NonNull ViewGroup parent) {
        mToolbarParent = parent;
        mCarServiceProvider.addListener(mCarServiceLifecycleListener);
    }

    @MainThread
    @Override
    public void show() {
        if (mToolbarParent == null) {
            Log.w(TAG, "init was not called");
            return;
        }
        if (mBackButton == null) {
            // Can't do this in init method because that's called when the window is available way
            // before the views are inflated.
            mBackButton = mToolbarParent.findViewById(R.id.back_btn);
            if (mBackButton != null) {
                mBackButton.setOnClickListener(v -> {
                    sendVirtualBackPress();
                });
            }
        }
        mToolbarParent.setVisibility(VISIBLE);
        View actionBar = mToolbarParent.findViewById(R.id.action_bar);
        if (actionBar != null) {
            actionBar.setVisibility(VISIBLE);
        }
    }

    @MainThread
    @Override
    public void hide() {
        if (mToolbarParent == null) {
            Log.w(TAG, "init was not called");
            return;
        }
        mToolbarParent.setVisibility(GONE);
        View actionBar = mToolbarParent.findViewById(R.id.action_bar);
        if (actionBar != null) {
            actionBar.setVisibility(GONE);
        }
    }

    @RequiresPermission(allOf = {PERMISSION_QUERY_DISPLAY_COMPATIBILITY,
            android.Manifest.permission.QUERY_ALL_PACKAGES})
    @Override
    public void update(@NonNull RunningTaskInfo taskInfo) {
        if (mToolbarParent == null) {
            Log.w(TAG, "init was not called");
            return;
        }
        if (requiresDisplayCompat(getPackageName(taskInfo))
                && taskInfo.displayId == DEFAULT_DISPLAY) {
            mMainHandler.post(() -> show());
            return;
        }
        mMainHandler.post(() -> hide());
    }

    private String getPackageName(RunningTaskInfo taskInfo) {
        if (taskInfo.topActivity != null) {
            return taskInfo.topActivity.getPackageName();
        }
        return taskInfo.baseIntent.getComponent().getPackageName();
    }

    @RequiresPermission(allOf = {PERMISSION_QUERY_DISPLAY_COMPATIBILITY,
            android.Manifest.permission.QUERY_ALL_PACKAGES})
    private boolean requiresDisplayCompat(String packageName) {
        boolean result = false;
        if (mCarPackageManager != null) {
            try {
                result = mCarPackageManager.requiresDisplayCompat(packageName);
            } catch (NameNotFoundException e) {
            }
        } else {
            Log.w(TAG, "CarPackageManager is not set.");
        }
        return result;
    }

    /**
     * Send both action down and up to be qualified as a back press. Set time for key events, so
     * they are not staled.
     */
    public static void sendVirtualBackPress() {
        long downEventTime = SystemClock.uptimeMillis();
        long upEventTime = downEventTime + 1;

        final KeyEvent keydown = new KeyEvent(downEventTime, downEventTime, KeyEvent.ACTION_DOWN,
                KeyEvent.KEYCODE_BACK, /* repeat= */ 0, /* metaState= */ 0,
                KeyCharacterMap.VIRTUAL_KEYBOARD, /* scancode= */ 0, KeyEvent.FLAG_FROM_SYSTEM);
        final KeyEvent keyup = new KeyEvent(upEventTime, upEventTime, KeyEvent.ACTION_UP,
                KeyEvent.KEYCODE_BACK, /* repeat= */ 0, /* metaState= */ 0,
                KeyCharacterMap.VIRTUAL_KEYBOARD, /* scancode= */ 0, KeyEvent.FLAG_FROM_SYSTEM);

        InputManagerGlobal inputManagerGlobal = InputManagerGlobal.getInstance();
        inputManagerGlobal.injectInputEvent(keydown, InputManager.INJECT_INPUT_EVENT_MODE_ASYNC);
        inputManagerGlobal.injectInputEvent(keyup, InputManager.INJECT_INPUT_EVENT_MODE_ASYNC);
    }
}
