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

package com.android.server.wm;

import android.annotation.SystemApi;
import android.annotation.UserIdInt;


/**
 * Interface implemented by {@link com.android.internal.car.CarActivityInterceptor} and used by
 * {@link CarActivityInterceptorUpdatable}.
 *
 * Because {@code CarActivityInterceptorUpdatable} calls {@code CarActivityInterceptorInterface}
 * with {@code mLock} acquired, {@code CarActivityInterceptorInterface} shouldn't call
 * {@code CarActivityInterceptorUpdatable} again during its execution.
 * @hide
 */
@SystemApi(client = SystemApi.Client.MODULE_LIBRARIES)
public interface CarActivityInterceptorInterface {
    /**
     * Returns the main user (i.e., not a profile) that is assigned to the display, or the
     * {@link android.app.ActivityManager#getCurrentUser() current foreground user} if no user is
     * associated with the display.
     * See {@link com.android.server.pm.UserManagerInternal#getUserAssignedToDisplay(int)} for
     * the detail.
     */
    @UserIdInt int getUserAssignedToDisplay(int displayId);

    /**
     * Returns the main display id assigned to the user, or {@code Display.INVALID_DISPLAY} if the
     * user is not assigned to any main display.
     * See {@link com.android.server.pm.UserManagerInternal#getMainDisplayAssignedToUser(int)} for
     * the detail.
     */
    int getMainDisplayAssignedToUser(@UserIdInt int userId);
}
