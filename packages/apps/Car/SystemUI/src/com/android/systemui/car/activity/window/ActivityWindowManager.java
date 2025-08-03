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

import android.annotation.Nullable;
import android.content.Context;
import android.util.Log;

import com.android.systemui.CoreStartable;
import com.android.systemui.R;
import com.android.systemui.dagger.SysUISingleton;

import dagger.Lazy;

import javax.inject.Inject;

/**
 * {@link CoreStartable} for {@link ActivityWindowController}
 */
@SysUISingleton
public class ActivityWindowManager implements CoreStartable {

    private static final String TAG =  ActivityWindowManager.class.getSimpleName();

    @Nullable
    private final ActivityWindowController mActivityWindowController;

    @Inject
    public ActivityWindowManager(Context context,
            Lazy<ActivityWindowController> activityWindowController) {
        if (context.getResources().getBoolean(R.bool.config_useRemoteLaunchTaskView)) {
            Log.i(TAG, "Will use RemoteLaunchTaskView for showing Activities.");
            mActivityWindowController = activityWindowController.get();
        } else {
            Log.i(TAG, "Will use DefaultTaskDisplayArea for showing Activities.");
            mActivityWindowController = null;
        }
    }

    @Override
    public void start() {
        Log.i(TAG, "ActivityWindowController not set.");
        if (mActivityWindowController != null) {
            mActivityWindowController.init();
        }
    }
}
