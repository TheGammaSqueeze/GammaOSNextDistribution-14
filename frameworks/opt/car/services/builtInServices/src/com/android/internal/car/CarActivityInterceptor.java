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
package com.android.internal.car;

import android.annotation.Nullable;
import android.app.TaskInfo;
import android.car.builtin.util.Slogf;
import android.content.pm.ActivityInfo;

import com.android.server.LocalServices;
import com.android.server.pm.UserManagerInternal;
import com.android.server.wm.ActivityInterceptResultWrapper;
import com.android.server.wm.ActivityInterceptorCallback;
import com.android.server.wm.ActivityInterceptorInfoWrapper;
import com.android.server.wm.CarActivityInterceptorInterface;
import com.android.server.wm.CarActivityInterceptorUpdatable;

/**
 * See {@link ActivityInterceptorCallback}.
 *
 * @hide
 */
public final class CarActivityInterceptor implements ActivityInterceptorCallback {
    private static final String TAG  = CarActivityInterceptor.class.getSimpleName();
    private CarActivityInterceptorUpdatable mCarActivityInterceptorUpdatable;

    public CarActivityInterceptor() {
        mCarActivityInterceptorUpdatable = null;
    }

    /**
     * Sets the given {@link CarActivityInterceptorUpdatable} which this internal class will
     * communicate with.
     */
    public void setUpdatable(CarActivityInterceptorUpdatable carActivityInterceptorUpdatable) {
        mCarActivityInterceptorUpdatable = carActivityInterceptorUpdatable;
    }

    @Nullable
    @Override
    public ActivityInterceptResult onInterceptActivityLaunch(ActivityInterceptorInfo info) {
        if (mCarActivityInterceptorUpdatable == null) {
            Slogf.w(TAG, "mCarActivityInterceptorUpdatable not set");
            return null;
        }
        ActivityInterceptResultWrapper interceptResultWrapper = mCarActivityInterceptorUpdatable
                .onInterceptActivityLaunch(ActivityInterceptorInfoWrapper.create(info));
        if (interceptResultWrapper == null) {
            return null;
        }
        return interceptResultWrapper.getInterceptResult();
    }

    @Override
    public void onActivityLaunched(TaskInfo taskInfo, ActivityInfo activityInfo,
            ActivityInterceptorInfo info) {
        // do nothing
    }

    CarActivityInterceptorInterface getBuiltinInterface() {
        return new CarActivityInterceptorInterface() {
            @Override
            public int getUserAssignedToDisplay(int displayId) {
                UserManagerInternal umi = LocalServices.getService(UserManagerInternal.class);
                int userId = umi.getUserAssignedToDisplay(displayId);
                return userId;
            }

            @Override
            public int getMainDisplayAssignedToUser(int userId) {
                UserManagerInternal umi = LocalServices.getService(UserManagerInternal.class);
                int displayId = umi.getMainDisplayAssignedToUser(userId);
                return displayId;
            }
        };
    }
}
