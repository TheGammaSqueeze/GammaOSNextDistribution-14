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

package com.android.devicelockcontroller.services;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;

import androidx.annotation.Nullable;

import com.android.devicelockcontroller.util.LogUtil;

/**
 * Used to determine if the controller crashed while in lock task mode.
 * Device lock system service component will restart the controller as needed.
 */
public final class DeviceLockKeepAliveService extends Service {
    private static final String TAG = "DeviceLockKeepAliveService";
    private final IBinder mBinder = new Binder();

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        LogUtil.i(TAG, "onBind called for DLC keep alive service");
        return mBinder;
    }
}
