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

import android.annotation.NonNull;
import android.annotation.SystemApi;
import android.annotation.UserIdInt;
import android.content.pm.ApplicationInfo;

/**
 * Interface implemented by {@link com.android.server.wm.CarDisplayCompatScaleProvider} and
 * used by {@link CarDisplayCompatScaleProviderUpdatable}.
 *
 * @hide
 */
@SystemApi(client = SystemApi.Client.MODULE_LIBRARIES)
public interface CarDisplayCompatScaleProviderInterface {
    /**
     * Returns the main display id assigned to the user, or {@code Display.INVALID_DISPLAY} if the
     * user is not assigned to any main display.
     * See {@link com.android.server.pm.UserManagerInternal#getMainDisplayAssignedToUser(int)} for
     * the detail.
     */
    int getMainDisplayAssignedToUser(@UserIdInt int userId);

    /**
     * returns true if the given application has {@code PackageManager#PRIVATE_FLAG_PRIVILEGED}
     * false otherwise.
     */
    boolean isPrivileged(@NonNull ApplicationInfo applicationInfo);
}
