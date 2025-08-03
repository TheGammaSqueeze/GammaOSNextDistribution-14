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
import android.annotation.Nullable;
import android.annotation.SystemApi;
import android.annotation.UserIdInt;
import android.content.res.CompatScaleWrapper;

/**
 * Updatable interface of {@link CarDisplayCompatScaleProvider}.
 *
 * @hide
 */
@SystemApi(client = SystemApi.Client.MODULE_LIBRARIES)
public interface CarDisplayCompatScaleProviderUpdatable {

    /**
     * This method is used to scale the height/width/density of a given package.
     * This is called before initialization of the application context therefore the app will have
     * no idea about the real width/height/density of the device.
     *
     * @param packageName package name of the running application
     * @param userId user id that is running the application
     * @return scaling factor for the given package name. return null if package was not handled.
     */
    @Nullable
    CompatScaleWrapper getCompatScale(@NonNull String packageName, @UserIdInt int userId);

    /**
     * @param packageName package name of the running application
     * @param userId user id that the package is installed for
     * @return true if package requires launching in automotive compatibility mode
     */
    boolean requiresDisplayCompat(@NonNull String packageName, @UserIdInt int userId);
}
